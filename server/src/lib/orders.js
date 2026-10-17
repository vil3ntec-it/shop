'use strict';
/**
 * سرویسِ سفارشِ آنلاین — پایهٔ سرورِ فاز ۴ (بخشِ ۳.۹ بندِ ۲).
 *
 *   پرچم ── `shop.orders` در «تنظیماتِ زنده» (scope = شناسهٔ دکان، یا `all`)
 *           + کلیدِ اشتراکِ `online_orders` (بخشِ ۳.۱۷). ⛔ پیش‌فرض خاموش.
 *   ساخت ── با کلیدِ یکتایی (CHK-19)، زیرِ قفلِ دکان، در یک تراکنش:
 *           سفارش + ردیف‌ها + رویدادِ «ثبت». مبلغ را سرور می‌سازد.
 *   گذار ── فقط آن‌چه `order-states` اجازه می‌دهد؛ ردیفِ سفارش قفل می‌شود،
 *           رویداد (زمان، کاربر، دلیل) در همان تراکنش نوشته می‌شود.
 *   خواندن ─ کارتِ سبکِ هر برگه (ORD-11/12) با صفحه‌بندیِ کلیدی، شمارش‌ها،
 *           و جزئیات با خطِ زمانی (ORD-16).
 *
 * ⛔ هنوز نیست (و عمداً): بررسیِ دوبارهٔ قیمت و موجودی با موتورِ قیمت
 * (CHK-17)، رزروِ موجودی (CHK-18) و سندِ فروشِ سریِ W (ORD-20) — این‌ها
 * لایهٔ دادهٔ کالا و موجودیِ سرور را لازم دارند. سفارشِ دستیِ کارمند قیمت
 * را خودش می‌گوید، درست مثلِ صندوق.
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now, tx } = db;
const S = require('./order-states');
const { ulid } = require('./ulid');
const { stable } = require('./idempotency');
const { can } = require('./permissions');
const { badRequest, forbidden, notFound, conflict, ApiError } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders';
const FEATURE_KEY = 'online_orders';
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;

const MAX_LINES = 200;
const MAX_QTY_MILLI = 10_000_000;            // ۱۰٬۰۰۰ واحد
const MAX_PRICE_MINOR = 100_000_000_000;     // یک میلیارد افغانی
const MAX_FEE_MINOR = 100_000_000;           // یک میلیون افغانی
const MAX_LIST = 100;
const STAFF_SOURCES = ['phone', 'telegram', 'whatsapp', 'manual'];
const SUBSTITUTIONS = ['', 'best_match', 'specific', 'none', 'refund'];

// ════════════════════════════════════════════════════════════════════
//  پرچم و اشتراک
// ════════════════════════════════════════════════════════════════════

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** پرچمِ دکان: مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است. */
async function flagOf(shopId) {
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG_KEY, 'all', String(shopId)]
  );
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

async function entitledOf(shopId) {
  const { entitlementOf } = require('./entitlement');
  const ent = await entitlementOf(shopId, now());
  return { entitled: ent.features.includes(FEATURE_KEY), ent };
}

async function stateOf(shopId) {
  const flag = await flagOf(shopId);
  const { entitled } = await entitledOf(shopId);
  return { flag, entitled, enabled: flag && entitled };
}

// ════════════════════════════════════════════════════════════════════
//  ورودی
// ════════════════════════════════════════════════════════════════════

const str = (v) => (typeof v === 'string' ? v.trim() : '');

function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

function int(v, field, { min = 0, max = Number.MAX_SAFE_INTEGER, def } = {}) {
  if ((v === undefined || v === null || v === '') && def !== undefined) return def;
  if (typeof v !== 'number' || !Number.isSafeInteger(v)) throw badRequest(`${field} باید عددِ صحیح باشد`, 'bad_number');
  if (v < min || v > max) throw badRequest(`${field} در بازهٔ مجاز نیست`, 'bad_number');
  return v;
}

function coord(v, min, max) {
  if (v === undefined || v === null || v === '') return null;
  const n = Number(v);
  if (!Number.isFinite(n) || n < min || n > max) throw badRequest('مختصاتِ پین معتبر نیست', 'bad_address');
  return Math.round(n * 1e6) / 1e6;
}

/** کلیدِ یکتایی: سرآیندِ `Idempotency-Key` یا `clientKey` در بدنه. */
function clientKeyOf(req) {
  const raw = (typeof req.get === 'function' && req.get('idempotency-key')) || req.body?.clientKey;
  const key = str(raw);
  if (!key) throw badRequest('کلیدِ یکتاییِ سفارش لازم است', 'idempotency_key_required');
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  return key;
}

/** اثرِ انگشتِ درخواست — بی خودِ کلید، تا سرآیند یا بدنه فرقی نکند. */
function hashOf(body) {
  const { clientKey, ...rest } = body || {};
  return crypto.createHash('sha256').update(stable(rest)).digest('hex');
}

/**
 * بدنهٔ «سفارشِ دستیِ کارمند» (تلفنی/تلگرام/واتس‌اپ/دستی، ORD-21) — بررسی
 * و ساختِ مبلغ. هر عدد صحیح است: `qtyMilli` (هزارم) و `*Minor` (یک‌صدم).
 */
function parseCreate(body, at) {
  const b = body && typeof body === 'object' ? body : {};
  const source = str(b.source) || 'manual';
  if (!STAFF_SOURCES.includes(source)) throw badRequest('منبعِ سفارش معتبر نیست', 'bad_source');
  const fulfillment = str(b.fulfillment);
  if (!['delivery', 'pickup'].includes(fulfillment)) throw badRequest('ارسال یا دریافت از دکان؟', 'bad_fulfillment');
  const paymentMethod = str(b.paymentMethod) || 'cod';
  //  پرداختِ آنلاین منتظرِ D-08 است (🔌)؛ امروز فقط نقد هنگامِ تحویل
  if (paymentMethod !== 'cod') throw badRequest('فعلاً فقط نقد هنگامِ تحویل', 'bad_payment_method');

  const c = b.customer && typeof b.customer === 'object' ? b.customer : {};
  const customerName = text(c.name, 80);
  if (!customerName) throw badRequest('نامِ مشتری لازم است', 'bad_customer');
  const v = require('./validate');
  const customerPhone = v.phone(c.phone, { required: fulfillment === 'delivery' }) || '';

  let address = {};
  if (fulfillment === 'delivery') {
    const a = b.address && typeof b.address === 'object' ? b.address : {};
    const lat = coord(a.lat, -90, 90);
    const lng = coord(a.lng, -180, 180);
    if ((lat === null) !== (lng === null)) throw badRequest('پین ناقص است', 'bad_address');
    const landmark = text(a.landmark, 200);
    const addrText = text(a.text, 300);
    //  پین منبعِ اصلی است (CHK-08)؛ سفارشِ تلفنی بی پین با نشانه پذیرفته است
    if (lat === null && !landmark && !addrText) throw badRequest('نشانی یا پینِ تحویل لازم است', 'bad_address');
    address = { ...(lat !== null ? { lat, lng } : {}), ...(landmark ? { landmark } : {}), ...(addrText ? { text: addrText } : {}) };
  }

  const d = b.delivery && typeof b.delivery === 'object' ? b.delivery : {};
  const delivery = {};
  if (d.callBeforeArrival === true) delivery.callBeforeArrival = true;
  const leaveWith = text(d.leaveWith, 80);
  if (leaveWith) delivery.leaveWith = leaveWith;

  if (!Array.isArray(b.lines) || b.lines.length === 0) throw badRequest('سفارش بی قلم است', 'bad_lines');
  if (b.lines.length > MAX_LINES) throw badRequest(`حداکثر ${MAX_LINES} قلم`, 'bad_lines');
  const lines = b.lines.map((l, i) => {
    const x = l && typeof l === 'object' ? l : {};
    const name = text(x.name, 200);
    if (!name) throw badRequest(`نامِ قلمِ ${i + 1} لازم است`, 'bad_lines');
    const qtyMilli = int(x.qtyMilli, `مقدارِ قلمِ ${i + 1}`, { min: 1, max: MAX_QTY_MILLI });
    const unitPriceMinor = int(x.unitPriceMinor, `قیمتِ قلمِ ${i + 1}`, { min: 0, max: MAX_PRICE_MINOR });
    const substitution = str(x.substitution);
    if (!SUBSTITUTIONS.includes(substitution)) throw badRequest('ترجیحِ جایگزینی معتبر نیست', 'bad_lines');
    const productId = str(x.productId);
    if (productId && !/^[A-Za-z0-9_.:-]{1,80}$/.test(productId)) throw badRequest('شناسهٔ کالا معتبر نیست', 'bad_lines');
    return {
      lineNo: i + 1, productId, name, unit: text(x.unit, 30), qtyMilli, unitPriceMinor,
      lineTotalMinor: S.lineTotalMinor(qtyMilli, unitPriceMinor), note: text(x.note, 200), substitution,
    };
  });

  const subtotalMinor = lines.reduce((s, l) => s + l.lineTotalMinor, 0);
  const discountMinor = int(b.discountMinor, 'تخفیف', { min: 0, max: MAX_PRICE_MINOR, def: 0 });
  if (discountMinor > subtotalMinor) throw badRequest('تخفیف از جمعِ اقلام بیشتر است', 'bad_discount');
  const deliveryFeeMinor = int(b.deliveryFeeMinor, 'هزینهٔ ارسال', { min: 0, max: MAX_FEE_MINOR, def: 0 });
  if (fulfillment === 'pickup' && deliveryFeeMinor) throw badRequest('دریافت از دکان هزینهٔ ارسال ندارد', 'bad_fee');
  const totalMinor = subtotalMinor - discountMinor + deliveryFeeMinor;
  if (!Number.isSafeInteger(totalMinor)) throw badRequest('جمعِ سفارش بیش از حد بزرگ است', 'bad_lines');

  let scheduledFor = null;
  if (b.scheduledFor !== undefined && b.scheduledFor !== null && b.scheduledFor !== '') {
    scheduledFor = int(b.scheduledFor, 'زمانِ سفارش', { min: 0, max: 4102444800000 });
    if (scheduledFor <= at) throw badRequest('زمانِ سفارشِ زمان‌بندی‌شده گذشته است', 'bad_schedule');
  }

  return {
    source, fulfillment, paymentMethod, customerName, customerPhone, address, delivery,
    note: text(b.note, 500), isTest: b.test === true, lines, subtotalMinor, discountMinor,
    deliveryFeeMinor, totalMinor, scheduledFor,
    itemsCount: lines.length,
  };
}

// ════════════════════════════════════════════════════════════════════
//  مدلِ خواندنی
// ════════════════════════════════════════════════════════════════════

function maskPhone(p) {
  const s = String(p || '');
  if (!s) return '';
  if (s.length <= 7) return '•'.repeat(s.length);
  return s.slice(0, 4) + '•'.repeat(s.length - 7) + s.slice(-3);
}

const n = (x) => (x === null || x === undefined ? null : Number(x));
const asJson = (x) => (x && typeof x === 'object' ? x : decode(x) || {});

/** کارتِ سبک (ORD-11) — شمارهٔ کامل هرگز در فهرست نیست. */
function card(row, viewer, at) {
  return {
    id: row.id,
    orderNo: Number(row.order_no),
    status: row.status,
    tab: S.tabOf(row, at),
    next: S.nextFor(row, { type: 'user', role: viewer.role }),
    source: row.source,
    isTest: row.is_test === true,
    fulfillment: row.fulfillment,
    paymentMethod: row.payment_method,
    paymentStatus: row.payment_status,
    codStatus: row.cod_status,
    customer: { name: row.customer_name, phoneMasked: maskPhone(row.customer_phone) },
    itemsCount: Number(row.items_count),
    currency: row.currency,
    totalMinor: Number(row.total_minor),
    scheduledFor: n(row.scheduled_for),
    readyAt: n(row.ready_at),
    statusReason: row.status_reason,
    version: Number(row.version),
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
  };
}

function detail(row, lines, events, viewer, at) {
  const contact = can(viewer.role, 'orders.contact');
  const c = card(row, viewer, at);
  return {
    ...c,
    customer: { ...c.customer, ...(contact ? { phone: row.customer_phone } : {}) },
    canContact: contact,
    address: asJson(row.address),
    delivery: asJson(row.delivery),
    note: row.note,
    subtotalMinor: Number(row.subtotal_minor),
    discountMinor: Number(row.discount_minor),
    deliveryFeeMinor: Number(row.delivery_fee_minor),
    createdBy: row.created_by,
    lines: lines.map((l) => ({
      lineNo: Number(l.line_no), productId: l.product_id, name: l.name, unit: l.unit,
      qtyMilli: Number(l.qty_milli), unitPriceMinor: Number(l.unit_price_minor),
      lineTotalMinor: Number(l.line_total_minor), note: l.note, substitution: l.substitution,
    })),
    events: events.map((e) => ({
      seq: Number(e.seq), kind: e.kind, fromStatus: e.from_status, toStatus: e.to_status,
      actorType: e.actor_type, actorId: e.actor_id, reason: e.reason, note: e.note,
      detail: asJson(e.detail), at: Number(e.created_at),
    })),
  };
}

async function loadDetail(run, shopId, id, viewer) {
  const row = (await run('SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2', [shopId, id])).rows[0];
  if (!row) return null;
  const lines = (await run('SELECT * FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [id])).rows;
  const events = (await run('SELECT * FROM online_order_events WHERE order_id=$1 ORDER BY seq', [id])).rows;
  return detail(row, lines, events, viewer, now());
}

const viaDb = (text, params) => db.query(text, params);

async function get(shopId, id, viewer) {
  const out = await loadDetail(viaDb, shopId, String(id || ''), viewer);
  if (!out) throw notFound('سفارش پیدا نشد', 'order_not_found');
  return out;
}

// ════════════════════════════════════════════════════════════════════
//  ساخت
// ════════════════════════════════════════════════════════════════════

/**
 * @returns {{order:object, replayed:boolean}}
 */
async function create(shopId, { key, body, actor }) {
  const at = now();
  const input = parseCreate(body, at);
  const hash = hashOf(body);

  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    //  قفلِ دکان: کلیدِ یکتایی و شمارهٔ سفارش هر دو پشتِ همین می‌مانند
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`orders:${shopId}`]);

    const prev = (await c.query(
      'SELECT id, request_hash FROM online_orders WHERE shop_id=$1 AND client_key=$2', [shopId, key]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== hash) {
        throw conflict('این کلیدِ یکتایی پیش‌تر برای سفارشِ دیگری به کار رفته است', 'idempotency_key_reused');
      }
      return { order: await loadDetail(run, shopId, prev.id, actor), replayed: true };
    }

    const no = Number((await c.query(
      'SELECT COALESCE(MAX(order_no), 0) + 1 AS n FROM online_orders WHERE shop_id=$1', [shopId]
    )).rows[0].n);
    const id = ulid(at);
    await c.query(
      `INSERT INTO online_orders (
         id, shop_id, order_no, client_key, request_hash, source, is_test, status, fulfillment,
         payment_method, payment_status, cod_status, customer_name, customer_phone, address, delivery,
         note, items_count, subtotal_minor, discount_minor, delivery_fee_minor, total_minor,
         scheduled_for, created_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,'new',$8,$9,'unpaid',$10,$11,$12,$13::jsonb,$14::jsonb,
               $15,$16,$17,$18,$19,$20,$21,$22,$23,$23)`,
      [id, shopId, no, key, hash, input.source, input.isTest, input.fulfillment,
        input.paymentMethod, input.paymentMethod === 'cod' ? 'to_collect' : '',
        input.customerName, input.customerPhone, JSON.stringify(input.address), JSON.stringify(input.delivery),
        input.note, input.itemsCount, input.subtotalMinor, input.discountMinor, input.deliveryFeeMinor,
        input.totalMinor, input.scheduledFor, actor.id || '', at]
    );
    for (const l of input.lines) {
      await c.query(
        `INSERT INTO online_order_lines (id, order_id, shop_id, line_no, product_id, name, unit, qty_milli,
           unit_price_minor, line_total_minor, note, substitution)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)`,
        [ulid(at), id, shopId, l.lineNo, l.productId, l.name, l.unit, l.qtyMilli, l.unitPriceMinor,
          l.lineTotalMinor, l.note, l.substitution]
      );
    }
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,1,'created','','new',$4,$5,'',$6,$7::jsonb,$8)`,
      [ulid(at), id, shopId, actor.type, actor.id || '', '',
        JSON.stringify({ source: input.source, isTest: input.isTest, totalMinor: input.totalMinor }), at]
    );
    return { order: await loadDetail(run, shopId, id, actor), replayed: false };
  });
}

// ════════════════════════════════════════════════════════════════════
//  گذار
// ════════════════════════════════════════════════════════════════════

const CHECK_STATUS = {
  invalid_transition: 409, fulfillment_mismatch: 409,
  reason_required: 400, bad_reason: 400, note_required: 400,
  system_only: 403, permission_denied: 403,
};
const CHECK_MESSAGE = {
  invalid_transition: 'این تغییرِ وضعیت مجاز نیست',
  fulfillment_mismatch: 'این وضعیت با نوعِ تحویلِ سفارش جور نیست',
  reason_required: 'دلیل لازم است',
  bad_reason: 'دلیل معتبر نیست',
  note_required: 'برای «دلیلِ دیگر» توضیح لازم است',
  system_only: 'این وضعیت را فقط سیستم می‌گذارد',
  permission_denied: 'این کار در حدِ دسترسیِ شما نیست',
};

/**
 * گذارِ وضعیت.
 * @param {{to:string, from?:string, reason?:string, note?:string, readyInMinutes?:number}} input
 * @param {{type:'user'|'system'|'customer', id?:string, role?:string}} actor
 * @returns {{order:object, unchanged:boolean, from:string, to:string}}
 */
async function transition(shopId, id, input, actor) {
  const to = str(input?.to);
  if (!S.ORDER_STATUSES.includes(to)) throw badRequest('وضعیتِ مقصد معتبر نیست', 'bad_status');
  const expected = str(input?.from);
  const reason = str(input?.reason);
  //  دلیل کد است، نه متن (متن در `note`)
  if (reason && !/^[a-z_]{1,40}$/.test(reason)) throw badRequest('دلیل معتبر نیست', 'bad_reason');
  const note = text(input?.note, 300);
  let readyAt;
  if (input?.readyInMinutes !== undefined && input?.readyInMinutes !== null && input?.readyInMinutes !== '') {
    if (to !== 'accepted') throw badRequest('زمانِ آماده فقط هنگامِ قبول', 'bad_ready_time');
    readyAt = now() + int(input.readyInMinutes, 'زمانِ آماده', { min: 1, max: 7 * 24 * 60 }) * 60_000;
  }

  return tx(async (c) => {
    const run = (t, p) => c.query(t, p);
    const row = (await c.query(
      'SELECT * FROM online_orders WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, String(id || '')]
    )).rows[0];
    if (!row) throw notFound('سفارش پیدا نشد', 'order_not_found');

    //  تکرارِ همان گذار (دو بار کلیک، قطعِ شبکه) ⇒ بی رویدادِ دوم
    if (row.status === to) {
      return { order: await loadDetail(run, shopId, row.id, actor), unchanged: true, from: to, to };
    }
    if (expected && expected !== row.status) {
      const err = conflict('وضعیتِ سفارش همین حالا عوض شده است', 'status_changed');
      err.current = row.status;
      throw err;
    }
    const verdict = S.check(row, to, { actor, reason, note });
    if (!verdict.ok) {
      throw new ApiError(CHECK_STATUS[verdict.code] || 409, verdict.code, CHECK_MESSAGE[verdict.code] || 'مجاز نیست');
    }

    const at = now();
    const seq = Number((await c.query(
      'SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM online_order_events WHERE order_id=$1', [row.id]
    )).rows[0].n);
    await c.query(
      `UPDATE online_orders
          SET status=$3, status_reason=$4, ready_at=COALESCE($5, ready_at),
              version=version+1, updated_at=$6
        WHERE shop_id=$1 AND id=$2`,
      [shopId, row.id, to, reason, readyAt ?? null, at]
    );
    await c.query(
      `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status,
         actor_type, actor_id, reason, note, detail, created_at)
       VALUES ($1,$2,$3,$4,'status',$5,$6,$7,$8,$9,$10,$11::jsonb,$12)`,
      [ulid(at), row.id, shopId, seq, row.status, to, actor.type, actor.id || '', reason, note,
        JSON.stringify(readyAt ? { readyAt } : {}), at]
    );
    return { order: await loadDetail(run, shopId, row.id, actor), unchanged: false, from: row.status, to };
  });
}

// ════════════════════════════════════════════════════════════════════
//  فهرست و شمارش
// ════════════════════════════════════════════════════════════════════

/** ترتیبِ هر برگه: صف از قدیمی به جدید؛ زمان‌بندی‌شده به زمانش؛ تمام‌شده از تازه. */
const ORDER_BY = {
  new: { col: 'created_at', dir: 'ASC' },
  preparing: { col: 'created_at', dir: 'ASC' },
  ready: { col: 'created_at', dir: 'ASC' },
  on_the_way: { col: 'created_at', dir: 'ASC' },
  scheduled: { col: 'scheduled_for', dir: 'ASC' },
  done: { col: 'updated_at', dir: 'DESC' },
};

/** شرطِ برگه؛ «اکنون» فقط وقتی پارامتر می‌شود که شرط به آن نیاز دارد. */
function tabCond(tab, params, at) {
  const ph = `$${params.length + 1}`;
  const cond = S.tabWhere(tab, ph);
  if (cond.includes(ph)) params.push(at);
  return cond;
}

function encodeCursor(v, id) {
  return Buffer.from(JSON.stringify([v, id])).toString('base64url');
}

function decodeCursor(raw) {
  try {
    const [v, id] = JSON.parse(Buffer.from(String(raw), 'base64url').toString('utf8'));
    if (!Number.isSafeInteger(v) || typeof id !== 'string' || !/^[0-9A-Z]{26}$/.test(id)) throw new Error('x');
    return { v, id };
  } catch {
    throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
  }
}

async function list(shopId, { tab = 'new', limit, cursor } = {}, viewer) {
  if (!S.TABS.includes(tab)) throw badRequest('برگه معتبر نیست', 'bad_tab');
  const lim = Math.min(MAX_LIST, Math.max(1, Number.parseInt(limit, 10) || 50));
  const at = now();
  const { col, dir } = ORDER_BY[tab];
  const params = [shopId];
  let where = `shop_id=$1 AND ${tabCond(tab, params, at)}`;
  if (cursor) {
    const cur = decodeCursor(cursor);
    params.push(cur.v, cur.id);
    const op = dir === 'ASC' ? '>' : '<';
    where += ` AND (${col}, id) ${op} ($${params.length - 1}, $${params.length})`;
  }
  params.push(lim + 1);
  const rows = await many(
    `SELECT * FROM online_orders WHERE ${where} ORDER BY ${col} ${dir}, id ${dir} LIMIT $${params.length}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  const last = page[page.length - 1];
  return {
    tab,
    items: page.map((r) => card(r, viewer, at)),
    nextCursor: more ? encodeCursor(Number(last[col]), last.id) : null,
  };
}

/** شمارشِ برگه‌های باز + قدیمی‌ترین سفارشِ تازه (هشدارِ ORD-02) + نشانگرِ تغییر — یک پرس‌وجو. */
async function summary(shopId) {
  const at = now();
  const cols = S.OPEN_TABS.map((tab) => `COUNT(*) FILTER (WHERE ${S.tabWhere(tab, '$2')})::int AS "${tab}"`);
  const r = await one(
    `SELECT ${cols.join(', ')},
            MIN(created_at) FILTER (WHERE status = 'new') AS oldest,
            MAX(updated_at) AS rev
       FROM online_orders WHERE shop_id = $1`,
    [shopId, at]
  );
  const counts = Object.fromEntries(S.OPEN_TABS.map((tab) => [tab, Number(r?.[tab] || 0)]));
  return { counts, oldestNewAt: n(r?.oldest), rev: n(r?.rev) || 0, serverTime: at };
}

module.exports = {
  FLAG_KEY, FEATURE_KEY, KEY_RE, MAX_LINES, STAFF_SOURCES,
  flagOf, entitledOf, stateOf, clientKeyOf, hashOf, parseCreate, maskPhone,
  create, transition, get, list, summary,
};
