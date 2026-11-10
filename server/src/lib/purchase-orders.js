'use strict';
/**
 * سفارشِ خرید از تأمین‌کننده — PUR-02 (پیش‌نویس، فرستاده، بخشی، کامل، لغو)، PUR-04 (رسیدِ
 * دریافتِ کامل/جزئی با خراب و باقی‌ماندهٔ باز) و PUR-14 (فاکتورِ جدای تأمین‌کننده برای تطبیقِ
 * سه‌طرفه، مهاجرتِ ۲۰۸)، §۶.۶ و سناریوی S19.
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.purchase_orders` در «تنظیماتِ زنده» فقط `GET /config` جواب
 *    می‌دهد. ⛔ shop_id فقط از عضویتِ کاربرِ توکن (INV-11).
 * ⛔ هر قاعده از `purchase-order-core.js` (همتای Kotlinِ برنامه، بردارهای مشترک) می‌آید؛ این
 *    فایل فقط ورودیِ HTTP را می‌خواند، قفل می‌گیرد و می‌نویسد.
 * ⛔ این سرور هیچ ردیفی در دفترِ دکان نمی‌نویسد: برنامهٔ دکان با رسیدِ ثبت‌شده همان فاکتورِ خریدِ
 *    امروز (`PurchaseBook.record`) را می‌سازد، پس موجودی، بها و ماندهٔ تأمین‌کننده همان راهِ دفتر را
 *    می‌روند و با آن می‌خوانند.
 *
 * هم‌زمانی: ساختن زیرِ قفلِ دکان (`pg_advisory_xact_lock`، شماره و کلیدِ یکتایی)؛ هر تغییرِ سفارش
 * و هر رسید زیرِ `SELECT … FOR UPDATE` ِ همان سفارش، پس دو رسیدِ هم‌زمان باقی‌مانده را دو بار
 * نمی‌خورند و رسیدِ تکراری همان پاسخِ اول را می‌گیرد.
 */
const crypto = require('crypto');
const { query, many, now, tx } = require('../db');
const { ulid } = require('./ulid');
const { stable } = require('./idempotency');
const { flagOf } = require('./orders');
const { can } = require('./permissions');
const core = require('./purchase-order-core');
const { badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.purchase_orders';
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

/** مرزهای کسب‌وکار (سخت‌تر از سقفِ امنِ هسته؛ همان اندازه‌های سفارشِ آنلاین) */
const MAX_QTY_MILLI = 1_000_000_000;         // یک میلیون واحد
const MAX_PRICE_MINOR = 100_000_000_000;     // یک میلیارد افغانی
const MAX_TEXT = { supplierName: 120, name: 200, unit: 30, note: 500, reason: 300, billNo: 60 };
const MAX_LIST = 100;
/** PUR-14: جمعِ یک ردیفِ فاکتورِ تأمین‌کننده (همان سقفِ فی × یک میلیون واحد نه — سقفِ امنِ یک فاکتور) */
const MAX_BILL_MINOR = 1_000_000_000_000_000;

/** پیامِ فارسیِ هر کدِ هسته */
const MESSAGES = {
  bad_state: 'وضعیتِ سفارش درست نیست',
  supplier_required: 'تأمین‌کننده را انتخاب کنید',
  lines_required: 'دست‌کم یک ردیف کالا لازم است',
  too_many_lines: `بیش از ${core.MAX_LINES} ردیف در یک سفارش نمی‌شود`,
  line_id_required: 'شناسهٔ ردیف درست نیست',
  duplicate_line: 'ردیفِ تکراری',
  product_required: 'کالا را انتخاب کنید',
  duplicate_product: 'یک کالا دو بار در سفارش آمده است',
  bad_qty: 'مقدار درست نیست',
  bad_price: 'قیمت درست نیست',
  bad_factor: 'ضریبِ واحدِ خریدِ ردیف درست نیست',
  overflow: 'مبلغ بیش از اندازه است',
  not_draft: 'فقط پیش‌نویس ویرایش می‌شود',
  not_sent: 'سفارش هنوز فرستاده نشده است',
  cancelled: 'سفارش لغو شده است',
  complete: 'سفارش کامل دریافت شده است',
  reason_required: 'دلیلِ لغو را بنویسید',
  receipt_id_required: 'کلیدِ رسید لازم است',
  id_conflict: 'این کلید پیش‌تر برای رسیدِ دیگری به کار رفته است',
  unknown_line: 'این ردیف در سفارش نیست',
  damaged_over_received: 'خراب بیش از رسیده است',
  nothing_received: 'هیچ مقداری وارد نشده است',
  over_ordered: 'بیش از باقی‌ماندهٔ سفارش است',
  //  PUR-14 — فاکتورِ تأمین‌کننده
  bill_no_required: 'شمارهٔ فاکتورِ تأمین‌کننده را بنویسید',
  bill_no_taken: 'این شمارهٔ فاکتور پیش‌تر برای همین سفارش ثبت شده است',
  bill_lines_required: 'دست‌کم یک ردیف در فاکتور لازم است',
  product_mismatch: 'کالای این ردیف با ردیفِ سفارش یکی نیست',
  bad_total: 'مبلغِ ردیفِ فاکتور درست نیست',
  bill_not_found: 'فاکتورِ تأمین‌کننده پیدا نشد',
  bill_voided: 'این فاکتور پیش‌تر باطل شده است',
};

/** کدِ هسته ⇒ خطای HTTP (۴۰۹ برای گذارِ نامجاز و تعارضِ کلید، ۴۰۰ برای ورودی) */
const CONFLICTS = new Set(['not_draft', 'not_sent', 'cancelled', 'complete', 'id_conflict', 'bill_no_taken', 'bill_voided']);
function refuse(f) {
  const msg = MESSAGES[f.code] || f.code;
  const err = CONFLICTS.has(f.code) ? conflict(msg, f.code) : badRequest(msg, f.code);
  if (f.lineId != null || f.index >= 0) err.data = { lineId: f.lineId, index: f.index };
  return err;
}

async function enabled(shopId) {
  return flagOf(shopId, FLAG_KEY);
}

async function requireEnabled(shopId) {
  if (!(await enabled(shopId))) throw forbidden('سفارشِ خرید برای این دکان روشن نیست', 'purchase_orders_disabled');
}

/* ── ورودی ───────────────────────────────────────────────────────── */

function text(v, max, field) {
  if (v === undefined || v === null) return '';
  if (typeof v !== 'string') throw badRequest(`${field} باید متن باشد`, 'bad_text');
  const t = v.trim();
  if (t.length > max) throw badRequest(`${field} بیش از ${max} نویسه است`, 'text_too_long');
  return t;
}

function dateOrNull(v) {
  if (v === undefined || v === null || v === '') return null;
  if (typeof v !== 'string' || !DATE_RE.test(v)) throw badRequest('تاریخِ رسیدنِ کالا درست نیست', 'bad_date');
  const d = new Date(`${v}T00:00:00Z`);
  if (Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== v) throw badRequest('تاریخِ رسیدنِ کالا درست نیست', 'bad_date');
  return v;
}

/**
 * بدنهٔ پیش‌نویس ⇒ سفارشِ درست یا ۴۰۰. ردیف: `{id?, productId, name, unit, qtyMilli, priceMinor}`؛
 * شناسهٔ ردیف نیامده ⇒ `l1`، `l2`… به ترتیب.
 */
function parseDraft(body) {
  const b = body || {};
  const raw = Array.isArray(b.lines) ? b.lines : [];
  const lines = raw.map((l, i) => {
    const x = l && typeof l === 'object' ? l : {};
    return {
      id: x.id === undefined || x.id === null || x.id === '' ? `l${i + 1}` : x.id,
      productId: x.productId,
      name: text(x.name, MAX_TEXT.name, 'نامِ کالا'),
      unit: text(x.unit, MAX_TEXT.unit, 'واحد'),
      qtyMilli: x.qtyMilli,
      priceMinor: x.priceMinor,
      //  PRD-07: ردیف به واحدِ خرید (`unit` نامِ همان، مقدار و فی به همان واحد)؛ ضریب به واحدِ پایهٔ کالا. نبود ⇒ کلید نیست (همان پیشین)
      ...(x.factorMilli === undefined || x.factorMilli === null ? {} : { factorMilli: x.factorMilli }),
    };
  });
  const draft = {
    supplierId: b.supplierId,
    supplierName: text(b.supplierName, MAX_TEXT.supplierName, 'نامِ تأمین‌کننده'),
    expectedDate: dateOrNull(b.expectedDate),
    note: text(b.note, MAX_TEXT.note, 'یادداشت'),
    lines,
  };
  const bad = core.checkOrder({ supplierId: draft.supplierId, state: core.STATE.DRAFT, lines });
  if (bad) throw refuse(bad);
  lines.forEach((l, i) => {
    if (l.factorMilli !== undefined && (!Number.isSafeInteger(l.factorMilli) || l.factorMilli <= 0
      || l.factorMilli > require('./purchase-units').MAX_FACTOR_MILLI)) throw refuse({ code: 'bad_factor', lineId: l.id, index: i });
    if (l.qtyMilli > MAX_QTY_MILLI) throw refuse({ code: 'bad_qty', lineId: l.id, index: i });
    if (l.priceMinor > MAX_PRICE_MINOR) throw refuse({ code: 'bad_price', lineId: l.id, index: i });
  });
  return draft;
}

/** کلیدِ یکتاییِ ساختن: سرآیندِ `Idempotency-Key` یا `clientKey` در بدنه */
function clientKeyOf(req) {
  const raw = (typeof req.get === 'function' && req.get('idempotency-key')) || req.body?.clientKey;
  const key = typeof raw === 'string' ? raw.trim() : '';
  if (!key) throw badRequest('کلیدِ یکتاییِ سفارش لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  return key;
}

const hashOf = (draft) => crypto.createHash('sha256').update(stable(draft)).digest('hex');

/* ── نما ─────────────────────────────────────────────────────────── */

const num = (v) => (v === null || v === undefined ? null : Number(v));

function orderOf(row) {
  return {
    id: row.id,
    no: Number(row.po_no),
    supplierId: row.supplier_id,
    supplierName: row.supplier_name,
    state: row.state,
    lines: row.lines,
    expectedDate: row.expected_date,
    note: row.note,
    version: Number(row.version),
    createdBy: row.created_by,
    createdAt: num(row.created_at),
    updatedAt: num(row.updated_at),
    sentAt: num(row.sent_at),
    cancelledAt: num(row.cancelled_at),
    cancelReason: row.cancel_reason,
  };
}

function receiptOf(row) {
  return {
    id: row.id,
    poId: row.po_id,
    lines: row.lines,
    posted: row.posted,
    totalMinor: Number(row.total_minor),
    //  PUR-06: کرایهٔ همین رسید (به حمل‌کننده)؛ رسیدِ پیشین ۰
    freightMinor: Number(row.freight_minor || 0),
    note: row.note,
    createdBy: row.created_by,
    createdAt: num(row.created_at),
  };
}

function billOf(row) {
  return {
    id: row.id,
    poId: row.po_id,
    billNo: row.bill_no,
    billDate: row.bill_date,
    lines: row.lines,
    totalMinor: Number(row.total_minor),
    note: row.note,
    createdBy: row.created_by,
    createdAt: num(row.created_at),
    voidedAt: num(row.voided_at),
    voidedBy: row.voided_by || null,
    voidReason: row.void_reason || null,
  };
}

/**
 * سفارش + وضعیتِ نمایشی، پیشرفتِ هر ردیف، جمع‌ها و کارهای مجاز. `bills` (PUR-14) همهٔ فاکتورهای
 * تأمین‌کننده با باطل‌شده‌ها؛ `billCount` فقط فعال‌ها. برنامهٔ پیشین این دو کلید را نمی‌شناسد و کنار می‌گذارد.
 */
function view(order, receipts, { withReceipts = true, bills = [] } = {}) {
  const status = core.status(order, receipts);
  const out = {
    ...order,
    status,
    allowed: core.allowed(status),
    progress: core.progress(order, receipts),
    totals: core.totals(order, receipts),
    receiptCount: receipts.length,
    //  زمانِ تأمین (PUR-10): «فرستادن ⇐ نخستین رسید» را برنامه از همین دو زمان می‌سنجد (ReorderCore)
    firstReceiptAt: receipts.length ? Math.min(...receipts.map((r) => r.createdAt)) : null,
    billCount: bills.filter((b) => b.voidedAt === null).length,
  };
  if (withReceipts) { out.receipts = receipts; out.bills = bills; }
  return out;
}

async function billsOf(run, shopId, poId) {
  return (await run(
    'SELECT * FROM purchase_bills WHERE shop_id=$1 AND po_id=$2 ORDER BY created_at, id', [shopId, poId]
  )).rows.map(billOf);
}

async function receiptsOf(run, shopId, poId) {
  return (await run(
    'SELECT * FROM purchase_receipts WHERE shop_id=$1 AND po_id=$2 ORDER BY created_at, id', [shopId, poId]
  )).rows.map(receiptOf);
}

async function lockOrder(c, shopId, id) {
  const row = (await c.query(
    'SELECT * FROM purchase_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(id || '')]
  )).rows[0];
  if (!row) throw notFound('سفارشِ خرید پیدا نشد', 'purchase_order_not_found');
  return orderOf(row);
}

/* ── کارها ───────────────────────────────────────────────────────── */

/** ساختنِ پیش‌نویس؛ همان کلید + همان بدنه ⇒ همان سفارش (`replayed`)، بدنهٔ دیگر ⇒ ۴۰۹ */
async function create(shopId, { key, body, userId }) {
  const draft = parseDraft(body);
  const hash = hashOf(draft);
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`purchase_orders:${shopId}`]);
    const prev = (await c.query(
      'SELECT * FROM purchase_orders WHERE shop_id=$1 AND client_key=$2', [shopId, key]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== hash) {
        throw conflict('این کلیدِ یکتایی پیش‌تر برای سفارشِ دیگری به کار رفته است', 'idempotency_key_reused');
      }
      const o = orderOf(prev);
      return { order: view(o, await receiptsOf(run, shopId, o.id), { bills: await billsOf(run, shopId, o.id) }), replayed: true };
    }
    const no = Number((await c.query(
      'SELECT COALESCE(MAX(po_no), 0) + 1 AS n FROM purchase_orders WHERE shop_id=$1', [shopId]
    )).rows[0].n);
    const at = now();
    const id = ulid(at);
    const row = (await c.query(
      `INSERT INTO purchase_orders (shop_id, id, po_no, client_key, request_hash, supplier_id, supplier_name, state,
         lines, expected_date, note, version, created_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,'draft',$8::jsonb,$9,$10,1,$11,$12,$12) RETURNING *`,
      [shopId, id, no, key, hash, draft.supplierId, draft.supplierName, JSON.stringify(draft.lines),
        draft.expectedDate, draft.note, userId || '', at]
    )).rows[0];
    return { order: view(orderOf(row), []), replayed: false };
  });
}

/** ویرایشِ پیش‌نویس با نسخه (قفلِ خوش‌بینانه): نسخهٔ کهنه ⇒ ۴۰۹ `version_conflict` */
async function update(shopId, id, { body }) {
  const version = body?.version;
  if (!Number.isSafeInteger(version) || version < 1) throw badRequest('نسخهٔ سفارش لازم است', 'version_required');
  const draft = parseDraft(body);
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const current = await lockOrder(c, shopId, id);
    const receipts = await receiptsOf(run, shopId, current.id);
    if (current.version !== version) {
      throw conflict('سفارش در این میان عوض شده است؛ دوباره باز کنید', 'version_conflict');
    }
    const r = core.edit(current, receipts, { supplierId: draft.supplierId, state: core.STATE.DRAFT, lines: draft.lines });
    if (r.failure) throw refuse(r.failure);
    const row = (await c.query(
      `UPDATE purchase_orders SET supplier_id=$3, supplier_name=$4, lines=$5::jsonb, expected_date=$6, note=$7,
         version=version+1, updated_at=$8
       WHERE shop_id=$1 AND id=$2 RETURNING *`,
      [shopId, current.id, draft.supplierId, draft.supplierName, JSON.stringify(draft.lines), draft.expectedDate,
        draft.note, now()]
    )).rows[0];
    return view(orderOf(row), receipts, { bills: await billsOf(run, shopId, current.id) });
  });
}

/** فرستادن (پیش‌نویس ⇒ فرستاده) و لغو؛ تکرارِ همان کار بی اثر است */
async function transition(shopId, id, action, { reason } = {}) {
  const why = action === 'cancel' ? text(reason, MAX_TEXT.reason, 'دلیل') : '';
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const current = await lockOrder(c, shopId, id);
    const receipts = await receiptsOf(run, shopId, current.id);
    const r = action === 'send' ? core.send(current, receipts) : core.cancel(current, receipts, why);
    if (r.failure) throw refuse(r.failure);
    const bills = await billsOf(run, shopId, current.id);
    if (r.unchanged) return { order: view(current, receipts, { bills }), changed: false };
    const at = now();
    const row = (await c.query(
      action === 'send'
        ? `UPDATE purchase_orders SET state='sent', sent_at=$3, version=version+1, updated_at=$3
           WHERE shop_id=$1 AND id=$2 RETURNING *`
        : `UPDATE purchase_orders SET state='cancelled', cancelled_at=$3, cancel_reason=$4, version=version+1, updated_at=$3
           WHERE shop_id=$1 AND id=$2 RETURNING *`,
      action === 'send' ? [shopId, current.id, at] : [shopId, current.id, at, why || null]
    )).rows[0];
    return { order: view(orderOf(row), receipts, { bills }), changed: true };
  });
}

/**
 * رسیدِ دریافت. بدنه: `{id, lines:[{lineId, receivedMilli, damagedMilli?, priceMinor?}], allowOver?, note?, freightMinor?}`؛
 * `id` همان کلیدِ یکتای رسید است (سرآیندِ `Idempotency-Key` هم پذیرفته). فیِ نیامده = فیِ سفارش.
 * `freightMinor` (PUR-06) کرایهٔ همین رسید به حمل‌کننده است: عددِ صحیحِ ۰ تا سقفِ فی، وگرنه ۴۰۰ `bad_freight`.
 * ⛔ کسی که `stock.adjust` ندارد (شاگرد) فی را عوض نمی‌کند، بیش از سفارش نمی‌پذیرد و کرایه نمی‌نویسد.
 */
async function receive(shopId, id, { body, headerKey, userId, role }) {
  const b = body || {};
  const rid = typeof b.id === 'string' && b.id ? b.id : headerKey;
  const allowOver = b.allowOver === true;
  const note = text(b.note, MAX_TEXT.note, 'یادداشت');
  const manager = can(role, 'stock.adjust');
  if (allowOver && !manager) throw forbidden('پذیرفتنِ بیش از سفارش فقط با مدیر است', 'over_not_allowed');
  const freight = b.freightMinor === undefined || b.freightMinor === null ? 0 : b.freightMinor;
  if (!Number.isSafeInteger(freight) || freight < 0 || freight > MAX_PRICE_MINOR) {
    throw badRequest('کرایه درست نیست', 'bad_freight');
  }
  //  کرایه بهای کالا را بالا می‌برد ⇒ همان مرزِ «تغییرِ فی»
  if (freight > 0 && !manager) throw forbidden('کرایهٔ رسید فقط با مدیر است', 'freight_not_allowed');
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const current = await lockOrder(c, shopId, id);
    const receipts = await receiptsOf(run, shopId, current.id);
    const price = new Map(current.lines.map((l) => [l.id, l.priceMinor]));
    const lines = (Array.isArray(b.lines) ? b.lines : []).map((l) => {
      const x = l && typeof l === 'object' ? l : {};
      const p = x.priceMinor === undefined || x.priceMinor === null ? price.get(x.lineId) : x.priceMinor;
      return { lineId: x.lineId, receivedMilli: x.receivedMilli, damagedMilli: x.damagedMilli ?? 0, priceMinor: p };
    });
    //  همین کلید روی سفارشِ دیگری؟ ⇒ تعارض (کلیدِ رسید در کلِ دکان یکتاست)
    if (typeof rid === 'string' && rid) {
      const elsewhere = (await c.query(
        'SELECT po_id FROM purchase_receipts WHERE shop_id=$1 AND id=$2', [shopId, rid]
      )).rows[0];
      if (elsewhere && elsewhere.po_id !== current.id) throw refuse({ code: 'id_conflict', lineId: null, index: -1 });
    }
    const r = core.receive(current, receipts, { id: rid, lines }, allowOver);
    if (r.failure) throw refuse(r.failure);
    if (r.duplicate) {
      return {
        order: view(current, receipts, { bills: await billsOf(run, shopId, current.id) }),
        receipt: receipts.find((x) => x.id === r.duplicate.id), replayed: true,
      };
    }
    if (!manager) {
      lines.forEach((l, i) => {
        if (price.has(l.lineId) && l.priceMinor !== price.get(l.lineId)) {
          const err = forbidden('تغییرِ قیمت در رسید فقط با مدیر است', 'price_change_not_allowed');
          err.data = { lineId: l.lineId, index: i };
          throw err;
        }
      });
    }
    lines.forEach((l, i) => {
      if (l.receivedMilli > MAX_QTY_MILLI) throw refuse({ code: 'bad_qty', lineId: l.lineId, index: i });
      if (l.priceMinor > MAX_PRICE_MINOR) throw refuse({ code: 'bad_price', lineId: l.lineId, index: i });
    });
    const at = now();
    let row;
    try {
      row = (await c.query(
        `INSERT INTO purchase_receipts (shop_id, id, po_id, lines, posted, total_minor, freight_minor, note, created_by, created_at)
         VALUES ($1,$2,$3,$4::jsonb,$5::jsonb,$6,$7,$8,$9,$10) RETURNING *`,
        [shopId, rid, current.id, JSON.stringify(r.ok.receipt.lines), JSON.stringify(r.ok.lines), r.ok.totalMinor,
          freight, note, userId || '', at]
      )).rows[0];
    } catch (e) {
      //  همان کلید هم‌زمان روی سفارشِ دیگری نشست (قفلِ هر سفارش جداست) ⇒ تعارض، نه ۵۰۰
      if (e && e.code === '23505') throw refuse({ code: 'id_conflict', lineId: null, index: -1 });
      throw e;
    }
    //  رسید وضعیتِ نمایشی را عوض می‌کند ⇒ نسخه و زمان بالا می‌روند (فهرست و ویرایشِ هم‌زمان)
    const updated = (await c.query(
      'UPDATE purchase_orders SET version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2 RETURNING *',
      [shopId, current.id, at]
    )).rows[0];
    const receipt = receiptOf(row);
    const bills = await billsOf(run, shopId, current.id);
    return { order: view(orderOf(updated), receipts.concat([receipt]), { bills }), receipt, replayed: false };
  });
}

/* ── فاکتورِ جدای تأمین‌کننده (PUR-14) ─────────────────────────────── */

const blankText = (v) => v === undefined || v === null || v === '';

/**
 * بدنهٔ فاکتور ⇐ فاکتورِ درست یا ۴۰۰ (کدِ پایدار و ردیف). ردیف: `{lineId?, productId?, qtyMilli, totalMinor}`؛
 * ردیفِ سفارش (`lineId`) کالایش را از سفارش می‌گیرد؛ کالای بیرون از سفارش فقط با `productId` (همان «بیرون از
 * سفارش» ِ هسته). یک کالا دو بار نمی‌آید. جمعِ ردیف همان مبلغی است که تأمین‌کننده نوشته (نه مقدار × فی).
 */
function parseBill(order, body) {
  const b = body || {};
  const billNo = text(b.billNo, MAX_TEXT.billNo, 'شمارهٔ فاکتور');
  if (!billNo) throw refuse({ code: 'bill_no_required' });
  let billDate = null;
  if (!blankText(b.billDate)) {
    const v = b.billDate;
    const d = typeof v === 'string' && DATE_RE.test(v) ? new Date(`${v}T00:00:00Z`) : null;
    if (!d || Number.isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== v) throw badRequest('تاریخِ فاکتور درست نیست', 'bad_date');
    billDate = v;
  }
  const note = text(b.note, MAX_TEXT.note, 'یادداشت');
  const raw = Array.isArray(b.lines) ? b.lines : [];
  if (!raw.length) throw refuse({ code: 'bill_lines_required' });
  if (raw.length > core.MAX_LINES) throw refuse({ code: 'too_many_lines' });
  const byId = new Map(order.lines.map((l) => [l.id, l]));
  const seen = new Set();
  let total = 0;
  const lines = raw.map((l, i) => {
    const x = l && typeof l === 'object' ? l : {};
    const lineId = blankText(x.lineId) ? null : x.lineId;
    let productId = blankText(x.productId) ? null : x.productId;
    if (lineId !== null) {
      const ol = typeof lineId === 'string' ? byId.get(lineId) : null;
      if (!ol) throw refuse({ code: 'unknown_line', lineId, index: i });
      if (productId !== null && productId !== ol.productId) throw refuse({ code: 'product_mismatch', lineId, index: i });
      productId = ol.productId;
    } else if (!core.refOk(productId)) {
      throw refuse({ code: 'product_required', lineId: null, index: i });
    }
    if (seen.has(productId)) throw refuse({ code: 'duplicate_product', lineId, index: i });
    seen.add(productId);
    if (!Number.isSafeInteger(x.qtyMilli) || x.qtyMilli <= 0 || x.qtyMilli > MAX_QTY_MILLI) throw refuse({ code: 'bad_qty', lineId, index: i });
    if (!Number.isSafeInteger(x.totalMinor) || x.totalMinor < 0 || x.totalMinor > MAX_BILL_MINOR) throw refuse({ code: 'bad_total', lineId, index: i });
    total += x.totalMinor;
    if (!Number.isSafeInteger(total)) throw refuse({ code: 'overflow' });
    return { lineId, productId, qtyMilli: x.qtyMilli, totalMinor: x.totalMinor };
  });
  return { billNo, billDate, note, lines, totalMinor: total };
}

/**
 * ثبتِ فاکتورِ تأمین‌کننده برای سفارشِ فرستاده (بخشی، کامل یا لغوشده با رسید هم). `id` ِ بدنه (یا سرآیندِ
 * `Idempotency-Key`) کلیدِ یکتاست: همان کلید و همان بدنه ⇒ همان فاکتور (`replayed`)، بدنهٔ دیگر ⇒ ۴۰۹
 * `idempotency_key_reused`، کلیدِ سفارشِ دیگر ⇒ ۴۰۹ `id_conflict`. همان شمارهٔ فعال در همان سفارش ⇒ ۴۰۹ `bill_no_taken`.
 * هیچ ردیفی در دفترِ دکان نوشته نمی‌شود (بدهیِ تأمین‌کننده همان راهِ رسید ⇐ فاکتورِ خریدِ دفتر را دارد).
 */
async function bill(shopId, id, { body, headerKey, userId }) {
  const b = body || {};
  const bid = typeof b.id === 'string' && b.id ? b.id : headerKey;
  if (!core.idOk(bid)) throw badRequest('کلیدِ یکتای فاکتور لازم است', 'bill_id_required');
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const current = await lockOrder(c, shopId, id);
    if (current.state === core.STATE.DRAFT) throw refuse({ code: 'not_sent' });
    const parsed = parseBill(current, b);
    const hash = hashOf(parsed);
    const prev = (await c.query('SELECT * FROM purchase_bills WHERE shop_id=$1 AND id=$2', [shopId, bid])).rows[0];
    if (prev) {
      if (prev.po_id !== current.id) throw refuse({ code: 'id_conflict' });
      if (prev.request_hash !== hash) {
        throw conflict('این کلیدِ یکتایی پیش‌تر برای فاکتورِ دیگری به کار رفته است', 'idempotency_key_reused');
      }
      return {
        order: view(current, await receiptsOf(run, shopId, current.id), { bills: await billsOf(run, shopId, current.id) }),
        bill: billOf(prev), replayed: true,
      };
    }
    const taken = (await c.query(
      'SELECT 1 FROM purchase_bills WHERE shop_id=$1 AND po_id=$2 AND bill_no=$3 AND voided_at IS NULL', [shopId, current.id, parsed.billNo]
    )).rows[0];
    if (taken) throw refuse({ code: 'bill_no_taken' });
    const at = now();
    let row;
    try {
      row = (await c.query(
        `INSERT INTO purchase_bills (shop_id, id, po_id, bill_no, bill_date, lines, total_minor, request_hash, note, created_by, created_at)
         VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7,$8,$9,$10,$11) RETURNING *`,
        [shopId, bid, current.id, parsed.billNo, parsed.billDate, JSON.stringify(parsed.lines), parsed.totalMinor, hash,
          parsed.note, userId || '', at]
      )).rows[0];
    } catch (e) {
      //  همان کلید هم‌زمان روی سفارشِ دیگری (قفلِ هر سفارش جداست) ⇒ تعارض، نه ۵۰۰
      if (e && e.code === '23505') throw refuse({ code: 'id_conflict' });
      throw e;
    }
    const updated = (await c.query(
      'UPDATE purchase_orders SET version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2 RETURNING *', [shopId, current.id, at]
    )).rows[0];
    return {
      order: view(orderOf(updated), await receiptsOf(run, shopId, current.id), { bills: await billsOf(run, shopId, current.id) }),
      bill: billOf(row), replayed: false,
    };
  });
}

/** باطل کردنِ فاکتورِ تأمین‌کننده با دلیل؛ تکرار بی اثر (`changed: false`)؛ شماره‌اش برای فاکتورِ درست آزاد می‌شود */
async function voidBill(shopId, id, billId, { reason, userId }) {
  const why = text(reason, MAX_TEXT.reason, 'دلیل');
  if (!why) throw refuse({ code: 'reason_required' });
  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const current = await lockOrder(c, shopId, id);
    const row = (await c.query(
      'SELECT * FROM purchase_bills WHERE shop_id=$1 AND po_id=$2 AND id=$3', [shopId, current.id, String(billId || '')]
    )).rows[0];
    if (!row) throw notFound(MESSAGES.bill_not_found, 'bill_not_found');
    if (row.voided_at !== null) {
      return {
        order: view(current, await receiptsOf(run, shopId, current.id), { bills: await billsOf(run, shopId, current.id) }),
        bill: billOf(row), changed: false,
      };
    }
    const at = now();
    const done = (await c.query(
      `UPDATE purchase_bills SET voided_at=$4, voided_by=$5, void_reason=$6 WHERE shop_id=$1 AND po_id=$2 AND id=$3 RETURNING *`,
      [shopId, current.id, row.id, at, userId || '', why]
    )).rows[0];
    const updated = (await c.query(
      'UPDATE purchase_orders SET version=version+1, updated_at=$3 WHERE shop_id=$1 AND id=$2 RETURNING *', [shopId, current.id, at]
    )).rows[0];
    return {
      order: view(orderOf(updated), await receiptsOf(run, shopId, current.id), { bills: await billsOf(run, shopId, current.id) }),
      bill: billOf(done), changed: true,
    };
  });
}

async function get(shopId, id) {
  const rows = await many('SELECT * FROM purchase_orders WHERE shop_id=$1 AND id=$2', [shopId, String(id || '')]);
  if (!rows.length) throw notFound('سفارشِ خرید پیدا نشد', 'purchase_order_not_found');
  const o = orderOf(rows[0]);
  return view(o, await receiptsOf(query, shopId, o.id), { bills: await billsOf(query, shopId, o.id) });
}

/**
 * PUR-02/03: پالایهٔ «وضعیتِ نمایشی» (`status`) ⇒ وضعیتِ ذخیره‌شدهٔ لازم. draft و cancelled همان ستون‌اند؛
 * sent/partial/complete و `open` (= sent یا partial، «در راه» ِ پیشنهادِ سفارش) از رسیدها با همان `core.status`.
 */
const LIST_STATUS = Object.freeze({
  draft: core.STATE.DRAFT, cancelled: core.STATE.CANCELLED,
  sent: core.STATE.SENT, partial: core.STATE.SENT, complete: core.STATE.SENT, open: core.STATE.SENT,
});
const statusMatches = (want, s) => (want === 'open' ? s === core.STATUS.SENT || s === core.STATUS.PARTIAL : s === want);
/** سقفِ ردیف‌های پیموده در یک درخواستِ پالایهٔ رسیدی (دکانِ با هزاران سفارشِ کامل) و اندازهٔ هر دور */
const SCAN_MAX = 500;
const SCAN_BATCH = 100;

const cursorOf = (o) => `${o.updatedAt}:${o.id}`;

async function receiptsByPo(shopId, ids) {
  const byPo = new Map(ids.map((x) => [x, []]));
  if (ids.length) {
    const rs = await many(
      'SELECT * FROM purchase_receipts WHERE shop_id=$1 AND po_id = ANY($2::text[]) ORDER BY created_at, id', [shopId, ids]
    );
    for (const r of rs) byPo.get(r.po_id).push(receiptOf(r));
  }
  return byPo;
}

/**
 * فهرست، تازه‌ترین تغییر بالا. `state` (draft|sent|cancelled) و `supplierId` پالایش در SQL؛
 * `cursor` = «updatedAt:id» ِ آخرین ردیفِ صفحهٔ پیش. وضعیتِ نمایشی برای هر ردیف حساب می‌شود.
 * `status` (draft|sent|partial|complete|cancelled|open) وضعیتِ نمایشی را می‌پالاید؛ `state` کنارش فقط هم‌خوان
 * (`state=sent&status=open`)، وگرنه ۴۰۰ `bad_status`. برای پالایهٔ رسیدی نشانگر آخرین ردیفِ **پیموده‌شده** است:
 * صفحه‌ای کم‌تر از `limit` (حتی تهی) با `next` یعنی «هنوز ردیف هست»؛ هر درخواست حداکثر `SCAN_MAX` ردیف.
 */
async function list(shopId, { state, status, supplierId, cursor, limit } = {}) {
  const n = Math.min(MAX_LIST, Math.max(1, Number.parseInt(limit, 10) || 50));
  const want = status === undefined || status === '' ? null : status;
  if (want !== null && (typeof want !== 'string' || !Object.hasOwn(LIST_STATUS, want))) {
    throw badRequest('وضعیتِ پالایش درست نیست', 'bad_status');
  }
  if (state !== undefined && state !== '' && !core.STATES.includes(state)) throw badRequest('وضعیتِ پالایش درست نیست', 'bad_state');
  const stored = want === null ? (state || null) : LIST_STATUS[want];
  if (want !== null && state !== undefined && state !== '' && state !== stored) {
    throw badRequest('وضعیتِ ذخیره‌شده با وضعیتِ نمایشی نمی‌خواند', 'bad_status');
  }
  const where = ['shop_id=$1'];
  const args = [shopId];
  if (stored) { args.push(stored); where.push(`state=$${args.length}`); }
  if (typeof supplierId === 'string' && supplierId) { args.push(supplierId); where.push(`supplier_id=$${args.length}`); }
  let after = null;
  if (typeof cursor === 'string' && cursor) {
    const m = /^(\d{1,16}):([A-Za-z0-9]{1,64})$/.exec(cursor);
    if (!m) throw badRequest('نشانگرِ صفحه درست نیست', 'bad_cursor');
    after = [Number(m[1]), m[2]];
  }
  const fetch = async (from, take) => {
    const a = args.slice();
    const w = where.slice();
    if (from) { a.push(from[0], from[1]); w.push(`(updated_at, id) < ($${a.length - 1}, $${a.length})`); }
    a.push(take);
    return (await many(
      `SELECT * FROM purchase_orders WHERE ${w.join(' AND ')} ORDER BY updated_at DESC, id DESC LIMIT $${a.length}`, a
    )).map(orderOf);
  };
  let page;
  let receipts;
  let next = null;
  if (want === null || stored !== core.STATE.SENT) {
    const rows = await fetch(after, n + 1);
    page = rows.slice(0, n);
    receipts = await receiptsByPo(shopId, page.map((o) => o.id));
    const last = page[page.length - 1];
    next = rows.length > n && last ? cursorOf(last) : null;
  } else {
    //  پالایهٔ رسیدی: دور به دور تا n ردیفِ هم‌خوان، پایانِ ردیف‌ها یا سقفِ پیمایش
    page = [];
    receipts = new Map();
    let scanned = 0;
    for (;;) {
      const take = Math.min(SCAN_BATCH, SCAN_MAX - scanned);
      const rows = await fetch(after, take + 1);
      const batch = rows.slice(0, take);
      const got = await receiptsByPo(shopId, batch.map((o) => o.id));
      let stop = -1;
      for (let i = 0; i < batch.length; i++) {
        const o = batch[i];
        if (statusMatches(want, core.status(o, got.get(o.id)))) { page.push(o); receipts.set(o.id, got.get(o.id)); }
        if (page.length === n) { stop = i; break; }
      }
      scanned += stop >= 0 ? stop + 1 : batch.length;
      const more = stop >= 0 ? stop < batch.length - 1 || rows.length > take : rows.length > take;
      const lastSeen = stop >= 0 ? batch[stop] : batch[batch.length - 1];
      if (!more) { next = null; break; }
      if (stop >= 0 || scanned >= SCAN_MAX) { next = cursorOf(lastSeen); break; }
      after = [lastSeen.updatedAt, lastSeen.id];
    }
  }
  const ids = page.map((o) => o.id);
  const billsBy = new Map(ids.map((x) => [x, []]));
  if (ids.length) {
    const bs = await many(
      'SELECT * FROM purchase_bills WHERE shop_id=$1 AND po_id = ANY($2::text[]) ORDER BY created_at, id', [shopId, ids]
    );
    for (const x of bs) billsBy.get(x.po_id).push(billOf(x));
  }
  return {
    orders: page.map((o) => view(o, receipts.get(o.id), { withReceipts: false, bills: billsBy.get(o.id) })),
    next,
  };
}

module.exports = {
  FLAG_KEY, MAX_QTY_MILLI, MAX_PRICE_MINOR, MAX_LIST, MAX_BILL_MINOR, SCAN_MAX,
  enabled, requireEnabled, parseDraft, clientKeyOf, create, update, transition, receive, bill, voidBill, get, list,
};
