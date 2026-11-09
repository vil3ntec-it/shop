'use strict';
/**
 * S89 + نیمهٔ سرورِ SEC-03 — فاکتورِ ثبت‌شده در سرور «سرِ جا» ویرایش و پاک نمی‌شود (§۶.۱۳، §۱۹.۱۲).
 *
 * پشتِ همان پرچمِ خاموشِ `shop.doc_chain` (SEC-02): خاموش ⇒ هیچ پرسش و هیچ تغییری؛ رفتارِ امروز.
 * روشن ⇒ هر نوشتنی که محتوای مالیِ فاکتوری را که سرور دارد عوض کند (`sales` یا قلمی از `saleItems`) سنجیده می‌شود:
 *
 *   enforce  دستگاهی که زنجیره دارد (ردیفی در `device_chain_heads` ِ همین دکان) و درِ مستقیمِ API (`routes/data.js`)
 *            ⇒ رد با `sale_finalized_edit_forbidden` (v1: همان op «rejected»؛ API: ۴۰۹ و هیچ نوشتنی) + رویداد.
 *   log      دستگاهِ بی زنجیره (برنامهٔ کهنه، وب) و راهِ قدیمِ `/api/sync` ⇒ همان امروز می‌نشیند، فقط رویداد —
 *            «قاعدهٔ سازگاری» (F0-02: راهِ قدیم برای برنامهٔ کهنه باز می‌ماند) و «زنجیره هرگز فروش را نمی‌بندد».
 * رویداد: `security.doc_chain` با `problem = finalized_edit` (یک ردیف برای هر فاکتور در هر نوشتن).
 *
 * ── محتوای مالی (عوض نمی‌شود؛ همان مقدار دوباره = «همان») ─────────────────────────────────────
 *   فاکتور  SALE_FIXED؛ شناسهٔ سند (SALE_ONCE) فقط از «خالی» پر می‌شود؛ مشتریِ فاکتورِ نسیه (`debtorId`) جز با
 *           ادغامِ مشتری (CRM-10: ردیفِ قدیمی `mergedInto` ِ همان مقصد را دارد، در سرور یا همین دسته)؛
 *           وضعیت فقط رو به `cancelled` (لغو = سندِ معکوسِ SEC-03؛ برگشت از لغو نه)؛ حذف هرگز.
 *   قلم     ITEM_FIXED؛ حذف هرگز؛ قلمِ تازه برای فاکتورِ موجود فقط در همان ثبت (درجِ فاکتور از همین دستگاه با
 *           همان `ts` — بُرشِ دسته در `OpQueue.cut` همان `ts` را دارد).
 * ── آزاد (فهرستِ صریح، از RULES) ───────────────────────────────────────────────────────────
 *   لغو: `status → cancelled` و `cancelReason`؛ شمارنده‌های سندِ معکوس: `debtSettled` (لغو، برگشت) و
 *   `returnedQty` ِ قلم (برگشت)؛ برچسب‌ها: `seller`، `syncStatus`، نامِ قلمِ آزاد (`name`)؛ و هر فیلدِ دیگری
 *   که مالی نیست. ⛔ فیلدِ مالیِ تازهٔ فاکتور یا قلم باید به SALE_FIXED/ITEM_FIXED برسد.
 * ── تکرار و بازپخش (هرگز رد نمی‌شود) ──────────────────────────────────────────────────────
 *   همان `op_id` ⇐ «duplicate» ِ خودِ v1 (پیش از این نگهبان). درجِ دوبارهٔ همان فاکتور یا قلم با `op_id` ِ تازه
 *   (بازپخش پس از بازیابی، دستگاهِ تازه با پشتیبانِ دستگاهِ دیگر) با محتوای مالیِ یکسان ⇐ در enforce پذیرفته و
 *   **بی بازنویسی** (همان قاعدهٔ «فقط‌افزودنی» ِ Sync v3: درجِ یکسان بی اثر) — تا نسخهٔ کهنهٔ پیش از لغو، وضعیت و
 *   شمارنده‌های سندِ معکوس را برنگرداند؛ در log همان امروز. ویرایشی که همان مقدار را دوباره می‌نویسد هم «همان» است.
 *
 * ── لغو = حلقهٔ زنجیرهٔ سرور (SEC-03، همان پرچم) ────────────────────────────────────────────
 *   هر لغوی که این نگهبان آزاد می‌گذارد (`status` ِ نالغو ⇒ `cancelled`، یا درجِ فاکتورِ تازه که بی اینترنت ثبت و لغو
 *   شد) **در همان تراکنشِ نوشتن** یک حلقه در `hash_chain` ِ `void/<دکان>` می‌شود — برای هر دستگاه (زنجیره‌دار یا نه،
 *   وب هم) و هر راه (`v1`، `legacy`، `api`): دستهٔ برگشته حلقه نمی‌گذارد. یک فاکتور ⇐ یک حلقه (زیرِ قفلِ زنجیره، با
 *   نمایهٔ `sale_void_index` ِ مهاجرتِ ۳۲۶؛ تکرار، بازپخش، لغوِ دوباره و برگشت‌ولغوِ دوباره ⇒ هیچ). بدنه: شناسه، شماره و سری، مبلغ با متنِ عددِ جاوااسکریپت
 *   (همان `JsNumber` ِ کاتلین؛ زنجیره فقط عددِ صحیح می‌پذیرد)، درهمِ محتوای مالی و درهمِ دلیل — **نه متنِ دلیل**
 *   (شاید دادهٔ شخصی؛ زنجیره پاک‌شدنی نیست). ⛔ زنجیره هرگز فروش را نمی‌بندد: شکستِ حلقه با SAVEPOINT برمی‌گردد،
 *   لغو و بقیهٔ دسته می‌نشینند و رویدادِ `void_unrecorded` می‌ماند.
 *   ⚠️ درجِ دوبارهٔ فاکتورِ موجود از دستگاهِ زنجیره‌دار (`replay`) نمی‌نشیند، حتی اگر وضعیتش «لغو» باشد: لغو با ویرایشِ
 *   خودِ دستگاه می‌رسد (قاعدهٔ S89 دست نخورد).
 *
 * ⛔ بازگرداندنِ تعارض (`restoreConflict` ِ مدیرِ ارشد، `routes/admin-sync.js`) از همین نگهبان با `enforce` و راهِ `admin`
 *    می‌گذرد: مقدارِ بازندهٔ فیلدِ مالیِ فاکتورِ ثبت‌شده ۴۰۹. `restoreDeleted` (بی فیلد) و پلِ v3 نگهبان ندارند؛ Sync v3
 *    خودش سند را فقط‌افزودنی می‌داند (`append_only_edit`).
 */
const db = require('../db');
const hc = require('./hash-chain');
const { conflict } = require('../middleware/errors');

const REASON = 'sale_finalized_edit_forbidden';
const PROBLEM = 'finalized_edit';
const ACTION = 'security.doc_chain';
const FLAG = 'shop.doc_chain';
const TEXT = 'ویرایش یا حذفِ سرِ جای فاکتورِ ثبت‌شده؛ اصلاح فقط با سندِ معکوس';

/** محتوای مالیِ فاکتور — همان بخش‌های سندِ زنجیره (`DocChain.saleDoc`) و پرداخت‌ها */
const SALE_FIXED = Object.freeze([
  'total', 'discountType', 'discountValue', 'discount', 'finalTotal',
  'paymentMethod', 'paidAmount', 'remaining', 'debtGiven', 'payments', 'tenderedMinor', 'changeMinor',
  'promoDiscountMinor', 'coupons', 'approval', 'createdAt', 'date', 'roundingMinor',
]);
/** شناسهٔ سند: فقط یک بار از «خالی» پر می‌شود (فاکتورِ کهنهٔ بی شماره، F0-03/DATA-06) */
const SALE_ONCE = Object.freeze(['invoiceNumber', 'series', 'receiptToken', 'channel', 'orderId', 'orderNo']);
/** محتوای مالیِ قلم */
const ITEM_FIXED = Object.freeze([
  'saleId', 'productId', 'quantity', 'unitPrice', 'totalPrice', 'purchasePrice', 'listPriceMinor', 'promo',
]);
const SALE_TABLES = new Set(['sales', 'saleItems']);
const MERGE_HOPS = 8;
const MAX_EVENTS = 20;
const VOID_PROBLEM = 'void_unrecorded';
const VOID_TEXT = 'لغوِ فاکتور نشست ولی حلقهٔ زنجیرهٔ لغو نوشته نشد';
const SHOP_PART = /^[A-Za-z0-9_.:-]{1,180}$/;
const TEXT_MAX = 80;

const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const hasOwn = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
const UNSAFE_KEY = new Set(['__proto__', 'constructor', 'prototype']);
const isInc = (v) => isPlainObject(v) && Object.keys(v).length === 1 && typeof v.$inc === 'number';
const isEmpty = (v) => v === undefined || v === null || v === '' || v === 0
  || (Array.isArray(v) && !v.length) || (isPlainObject(v) && !Object.keys(v).length);

function canonical(v) {
  if (Array.isArray(v)) return `[${v.map(canonical).join(',')}]`;
  if (isPlainObject(v)) return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${canonical(v[k])}`).join(',')}}`;
  return JSON.stringify(v === undefined ? null : v);
}

/** «همان مقدار»؟ نبودن، null، صفر و رشته/فهرستِ خالی یکی‌اند (پیش‌فرضِ مدل)؛ عدد با مقدار، نه شکلِ نوشتن */
function same(a, b) {
  if (isEmpty(a) && isEmpty(b)) return true;
  if (typeof a === 'number' && typeof b === 'number') return a === b;
  return canonical(a) === canonical(b);
}

/** مقدارِ پس از op: دلتا روی مقدارِ فعلی، وگرنه خودِ مقدار */
const after = (cur, v) => (isInc(v) ? (Number(cur) || 0) + v.$inc : v);
const statusOf = (s) => (isEmpty(s) ? 'completed' : String(s));
const isCredit = (s) => Number(s.debtGiven) > 0 || Number(s.remaining) > 0;

/* ------------------------------------------------------------- زنجیرهٔ لغو */

/** نامِ زنجیرهٔ لغوِ دکان ⇐ رشته یا '' (شناسهٔ ناجور ⇒ بی زنجیره) */
const voidChainOf = (shopId) => (SHOP_PART.test(String(shopId || '')) ? `void/${shopId}` : '');

/** متنِ کوتاه و امن برای بدنهٔ زنجیره: نیم‌جفتِ تنها (که canonicalJson نمی‌پذیرد) ⇐ U+FFFD */
function text(v) {
  if (isEmpty(v) && v !== 0) return '';
  return String(v).slice(0, TEXT_MAX).replace(/[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/g, '\uFFFD');
}

/** مبلغ ⇐ متنِ `Number#toString` ِ جاوااسکریپت (همان `JsNumber.of` ِ کاتلین)؛ نبود ⇐ '' */
const numText = (v) => (typeof v === 'number' && Number.isFinite(v) ? String(v) : text(v));

/** فاکتور پس از op: کلِ ردیف (راهِ قدیم، درجِ تازه) یا ویرایش روی ردیفِ فعلی (دلتا جمع) */
function nextSale(cur, fields, full) {
  if (full || !cur) return { ...fields };
  const out = { ...cur };
  for (const [k, v] of Object.entries(fields)) {
    //  کلیدِ «__proto__» ِ JSON ِ دستگاه نمونهٔ out را عوض می‌کرد و فیلدِ ساختگی ارث می‌رسید (CodeQL ۲۹۰)
    if (UNSAFE_KEY.has(k)) continue;
    out[k] = after(cur[k], v);
  }
  return out;
}

/** درهمِ محتوای مالیِ فاکتور (همان فیلدهایی که عوض نمی‌شوند) — خالی‌ها یکی، عدد با متنِ جاوااسکریپت */
function saleHash(sale) {
  const pick = {};
  for (const k of [...SALE_FIXED, ...SALE_ONCE, 'debtorId']) pick[k] = isEmpty(sale[k]) ? null : sale[k];
  return hc.sha256Hex(canonical(pick));
}

/** این نوشتن فاکتور را لغو می‌کند؟ (نالغو ⇒ لغو، یا ثبتِ تازهٔ لغوشده) */
function isVoid(cur, fields, { type, full }) {
  if (type === 'delete' || !(full || hasOwn(fields, 'status')) || statusOf(fields.status) !== 'cancelled') return false;
  return cur ? statusOf(cur.status) !== 'cancelled' : (full || type === 'insert');
}

/**
 * فیلدهای مالیِ فاکتورِ [cur] که [fields] عوض می‌کند (بی سنجشِ مشتری، که ادغام می‌خواهد) ⇐ نام‌ها.
 * `full`: نوشتنِ کلِ ردیف (راهِ قدیم) — کلیدِ نبوده یعنی «خالی».
 * `insert`: درجِ دوبارهٔ ردیفِ موجود — وضعیت و شمارنده‌های سندِ معکوس سنجیده نمی‌شوند (نسخهٔ کهنهٔ همان سند).
 */
function saleChanges(cur, fields, { full = false, insert = false } = {}) {
  const has = (k) => full || hasOwn(fields, k);
  const out = [];
  for (const k of SALE_FIXED) if (has(k) && !same(cur[k], after(cur[k], fields[k]))) out.push(k);
  for (const k of SALE_ONCE) if (has(k) && !isEmpty(cur[k]) && !same(cur[k], fields[k])) out.push(k);
  if (!insert && has('status') && statusOf(fields.status) !== statusOf(cur.status) && statusOf(fields.status) !== 'cancelled') {
    out.push('status');
  }
  return out;
}

/** مشتریِ فاکتورِ نسیه عوض می‌شود؟ ⇐ [از، به] یا null */
function customerMove(cur, fields, { full = false } = {}) {
  if (!(full || hasOwn(fields, 'debtorId')) || same(cur.debtorId, fields.debtorId) || !isCredit(cur)) return null;
  return [isEmpty(cur.debtorId) ? '' : String(cur.debtorId), isEmpty(fields.debtorId) ? '' : String(fields.debtorId)];
}

function itemChanges(cur, fields, { full = false } = {}) {
  const out = [];
  for (const k of ITEM_FIXED) if ((full || hasOwn(fields, k)) && !same(cur[k], after(cur[k], fields[k]))) out.push(k);
  return out;
}

/** پرچمِ دکان — همان `orders.flagOf` (دکان بر `all` جلو؛ فقط `true`) */
const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG);

async function chainDevice(shopId, deviceId) {
  if (!deviceId) return false;
  return !!(await db.one('SELECT 1 AS ok FROM device_chain_heads WHERE shop_id=$1 AND device_id=$2 LIMIT 1', [shopId, deviceId]));
}

/**
 * نگهبانِ یک نوشتن. `rowOf(c, table, id)` ⇐ دادهٔ ردیفِ زنده یا null، `firstInsert(c, saleId)` ⇐ {device, ts} یا null.
 * `merges`: ادغام‌های همین دسته (شناسهٔ مشتری ⇐ `mergedInto` یا null).
 */
class Guard {
  constructor({ ctx, deviceId, mode, path, merges, rowOf, firstInsert, ip = '', voids = true }) {
    Object.assign(this, { ctx, deviceId, mode, path, merges, rowOf, firstInsert, ip, voids });
    this.notes = new Map();
    this.newSales = new Set();
    this.voidFails = new Map();
  }

  get enforce() { return this.mode === 'enforce'; }

  /** آیا مشتریِ [from] (با زنجیرهٔ `mergedInto`) در [to] ادغام شده؟ */
  async merged(c, from, to) {
    let id = from;
    for (let i = 0; i < MERGE_HOPS && id; i++) {
      let next;
      if (this.merges.has(id)) next = this.merges.get(id);
      else { const d = await this.rowOf(c, 'debtors', id); next = d ? d.mergedInto : null; }
      if (isEmpty(next)) return false;
      if (String(next) === to) return true;
      id = String(next);
    }
    return false;
  }

  /** فاکتورِ زنده با شناسهٔ [id] ⇐ داده یا null */
  sale(c, id) { return isEmpty(id) ? null : this.rowOf(c, 'sales', String(id)); }

  /**
   * یک نوشتن روی ردیفِ [cur] (دادهٔ زنده یا null) ⇐ null (آزاد) | {replay:true} | {reject: REASON}.
   * `type`: insert | update | delete؛ `full`: کلِ ردیف (راهِ قدیم).
   */
  async check(c, { table, rowId, type, fields = {}, ts = 0, full = false }, cur) {
    if (!SALE_TABLES.has(table)) return null;
    let changed = [];
    let sale = '';
    if (table === 'sales') {
      if (!cur) {                                             //  ثبت، نه ویرایش (ردیفِ حذف‌شده هم «نیست»)
        this.newSales.add(rowId);
        if (this.voids && isVoid(null, fields, { type, full })) await this.recordVoid(c, rowId, null, fields, { full, atInsert: true });
        return null;
      }
      sale = rowId;
      if (type === 'delete') changed = ['*delete*'];
      else {
        const insert = type === 'insert';
        changed = saleChanges(cur, fields, { full, insert });
        const move = customerMove(cur, fields, { full });
        if (move && !(await this.merged(c, move[0], move[1])) && !(insert && (await this.merged(c, move[1], move[0])))) {
          changed.push('debtorId');
        }
        if (insert && !changed.length && this.enforce) return { replay: true };
      }
      //  نوشتن می‌نشیند (آزاد، یا `log`) و فاکتور را لغو می‌کند ⇒ حلقهٔ لغو در همین تراکنش
      if (this.voids && (!changed.length || !this.enforce) && isVoid(cur, fields, { type, full })) {
        await this.recordVoid(c, rowId, cur, fields, { full, atInsert: false });
      }
    } else if (cur) {
      sale = String(cur.saleId || '');
      const parent = await this.sale(c, cur.saleId);
      if (parent) {
        if (type === 'delete') changed = ['*delete*'];
        else {
          changed = itemChanges(cur, fields, { full });
          if (type === 'insert' && !changed.length) return this.enforce ? { replay: true } : null;
        }
      } else if (type !== 'delete' && hasOwn(fields, 'saleId') && !same(cur.saleId, fields.saleId)
                 && (await this.sale(c, fields.saleId))) {
        sale = String(fields.saleId);
        changed = ['saleId'];                                 //  قلمِ بی‌فاکتور به فاکتورِ ثبت‌شده چسبانده می‌شود
      }
    } else if (type !== 'delete' && !isEmpty(fields.saleId) && (await this.sale(c, fields.saleId))) {
      sale = String(fields.saleId);
      if (!(await this.sameSave(c, sale, ts))) changed = ['+line'];
    }
    if (!changed.length) return null;
    this.note(sale, { table, row: rowId, type, fields: changed });
    return this.enforce ? { reject: REASON } : null;
  }

  /**
   * حلقهٔ لغوِ فاکتورِ [saleId] در `void/<دکان>` — درونِ تراکنشِ نوشتن ([c])، زیرِ قفلِ همان زنجیره، یک بار برای هر
   * فاکتور. ⛔ هرگز خطا نمی‌دهد: شکست با SAVEPOINT برمی‌گردد و رویدادِ `void_unrecorded` پس از تراکنش ثبت می‌شود.
   */
  async recordVoid(c, saleId, cur, fields, { full, atInsert }) {
    const shopId = this.ctx.accountId;
    const chain = voidChainOf(shopId);
    if (!chain || isEmpty(saleId)) return;
    const next = nextSale(cur, fields, full);
    const reason = isEmpty(next.cancelReason) ? '' : String(next.cancelReason);
    const doc = {
      kind: 'doc', type: 'void', v: 1,
      saleId: text(saleId), invoiceNumber: text(next.invoiceNumber), series: text(next.series),
      total: numText(isEmpty(next.finalTotal) ? next.total : next.finalTotal),
      saleHash: saleHash(next), reasonHash: reason ? hc.sha256Hex(reason) : '',
      deviceId: text(this.deviceId), by: text(this.ctx.userId), path: this.path, enforced: this.enforce,
      //  انبارِ ردیف: v1 (`sync_rows`) یا راهِ قدیم/API (`sales`) — همان شناسه در انبارِ دیگر فاکتورِ دیگری است
      store: this.path === 'v1' ? 'v1' : 'rest',
      atInsert: !!atInsert, at: db.now(),
    };
    await c.query('SAVEPOINT sale_void_chain');
    try {
      await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sale-void:${shopId}`]);
      //  یک فاکتور ⇐ یک حلقه: نمایهٔ ۳۲۶ (کلیدِ اصلی)، نه گشتنِ متنِ حلقه‌ها
      const seen = await c.query(
        'SELECT seq FROM sale_void_index WHERE shop_id=$1 AND store=$2 AND sale_id=$3', [shopId, doc.store, String(saleId)]
      );
      if (!seen.rows.length) {
        const head = (await c.query('SELECT seq, hash FROM hash_chain WHERE chain=$1 ORDER BY seq DESC LIMIT 1', [chain])).rows[0];
        const seq = head ? Number(head.seq) + 1 : 1;
        const prev = head ? head.hash : hc.GENESIS;
        const body = hc.canonicalJson({ ...doc, chain, seq });
        const r = await c.query(
          `INSERT INTO hash_chain (chain, seq, prev_hash, hash, body, created_at)
           VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (chain, seq) DO NOTHING RETURNING seq`,
          [chain, seq, prev, hc.sha256Hex(prev + body), body, doc.at]
        );
        if (!r.rows.length) throw new Error('void chain head moved');
        await c.query(
          'INSERT INTO sale_void_index (shop_id, store, sale_id, seq, created_at) VALUES ($1,$2,$3,$4,$5)',
          [shopId, doc.store, String(saleId), seq, doc.at]
        );
      }
      await c.query('RELEASE SAVEPOINT sale_void_chain');
    } catch (err) {
      await c.query('ROLLBACK TO SAVEPOINT sale_void_chain');
      await c.query('RELEASE SAVEPOINT sale_void_chain');
      if (this.voidFails.size < MAX_EVENTS) this.voidFails.set(String(saleId), String(err && err.message || err).slice(0, 200));
    }
  }

  /** قلمِ تازه از همان ثبتِ فاکتور است؟ */
  async sameSave(c, saleId, ts) {
    if (this.newSales.has(saleId)) return true;
    const first = await this.firstInsert(c, saleId);
    return !!first && first.device === this.deviceId && !!ts && Number(first.ts) === Number(ts);
  }

  note(sale, entry) {
    if (!this.notes.has(sale)) this.notes.set(sale, []);
    const list = this.notes.get(sale);
    if (list.length < 50) list.push(entry);
  }

  /** رویدادها — پس از تراکنش؛ شکستِ ثبت هیچ چیزی را نمی‌خواباند */
  async finish() {
    let n = 0;
    for (const [sale, ops] of this.notes) {
      if (++n > MAX_EVENTS) break;
      try {
        await require('./audit').log({
          shopId: this.ctx.accountId, actorType: 'user', userId: this.ctx.userId || '', ip: this.ip,
          action: ACTION, targetType: 'sale', targetId: sale,
          detail: {
            //  متن، نه فهرست: `audit.scrub` فهرست را شیء می‌کند
            problem: PROBLEM, enforced: this.enforce, path: this.path, deviceId: this.deviceId || '',
            edits: ops.map((o) => `${o.table}/${o.row} ${o.type}: ${o.fields.join(',')}`).join('; '),
            more: this.notes.size > MAX_EVENTS ? this.notes.size - MAX_EVENTS : undefined, text: TEXT,
          },
        });
      } catch (err) {
        console.error('[finalized-sales] رویداد ثبت نشد:', err.message);
      }
    }
    this.notes.clear();
    for (const [sale, error] of this.voidFails) {
      try {
        await require('./audit').log({
          shopId: this.ctx.accountId, actorType: 'user', userId: this.ctx.userId || '', ip: this.ip,
          action: ACTION, targetType: 'sale', targetId: sale,
          detail: { problem: VOID_PROBLEM, enforced: this.enforce, path: this.path, deviceId: this.deviceId || '', error, text: VOID_TEXT },
        });
      } catch (err) {
        console.error('[finalized-sales] رویدادِ لغو ثبت نشد:', err.message);
      }
    }
    this.voidFails.clear();
  }
}

/* ------------------------------------------------------------------ Sync v1 */

const V1_ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
//  هویتِ حسابِ دکان در v1 — یک جا، نه مقایسهٔ درجای رشته (سنجهٔ product-literals)
const SHOP_V1 = { app: 'shop', accountKind: 'shop' };

/**
 * نگهبانِ یک push ِ v1 ⇐ Guard یا null (پمپ، دسته‌ای بی فاکتور و قلم، پرچمِ خاموش ⇒ null و هیچ پرسشی).
 * `lib/sync-v1.js` آن را برای هر op پس از قفلِ ردیف صدا می‌زند (همان تراکنش، زیرِ قفلِ حساب).
 */
/** ردیفِ زندهٔ v1 ِ دکان ⇐ داده یا null (`c`: تراکنش یا خودِ `db`) */
const v1RowOf = (shopId) => async (c, table, id) => {
  const r = (await c.query(
    `SELECT data FROM sync_rows WHERE ${V1_ACC} AND table_name=$2 AND row_id=$3 AND deleted_at IS NULL`, [shopId, table, id]
  )).rows[0];
  return r ? r.data : null;
};

async function v1Guard(ctx, deviceId, ops, { ip = '' } = {}) {
  if (!ctx || ctx.app !== SHOP_V1.app || ctx.accountKind !== SHOP_V1.accountKind || !ctx.accountId || !Array.isArray(ops)) return null;
  const { tableOf } = require('./sync-v1');
  const tableOfRaw = (r) => (isPlainObject(r) ? tableOf(SHOP_V1.app, r.table ?? r.table_name ?? r.tableName) : null);
  if (!ops.some((r) => SALE_TABLES.has(tableOfRaw(r)))) return null;
  if (!(await flagOn(ctx.accountId))) return null;
  const merges = new Map();
  for (const r of ops) {
    const f = isPlainObject(r) && isPlainObject(r.fields) ? r.fields : null;
    if (f && tableOfRaw(r) === 'debtors' && hasOwn(f, 'mergedInto')) merges.set(String(r.row_id ?? r.rowId ?? r.id ?? ''), f.mergedInto);
  }
  const shopId = ctx.accountId;
  return new Guard({
    ctx, deviceId, path: 'v1', merges, ip,
    mode: (await chainDevice(shopId, deviceId)) ? 'enforce' : 'log',
    rowOf: v1RowOf(shopId),
    firstInsert: async (c, saleId) => {
      const r = (await c.query(
        `SELECT device_id, client_ts FROM oplog WHERE ${V1_ACC} AND table_name='sales' AND row_id=$2 AND op_type='insert'
          ORDER BY server_seq ASC LIMIT 1`, [shopId, saleId]
      )).rows[0];
      return r ? { device: r.device_id, ts: Number(r.client_ts) } : null;
    },
  });
}

/**
 * پیش از `restoreConflict` ِ مدیرِ ارشد (`routes/admin-sync.js`): همان op (مقدارِ بازنده روی ردیفِ امروز) از نگهبانِ
 * `enforce` با راهِ `admin` ⇐ ۴۰۹ (`sale_finalized_edit_forbidden`، پرتاب) + رویداد، یا هیچ (آزاد).
 * پمپ، جدولِ دیگر، پرچمِ خاموش و تعارضِ نبوده (خودِ restore ۴۰۴ می‌دهد) ⇒ هیچ پرسشِ دیگری.
 * وضعیتِ فاکتور هم با بازگرداندن عوض نمی‌شود: برگشت از لغو ویرایش است و لغو کارِ خودِ دکان با دلیل (حلقهٔ لغو).
 */
async function adminRestoreCheck(conflictId, { adminId = '', ip = '' } = {}) {
  const cf = await db.one(
    'SELECT app, account_kind, account_id, table_name, row_id, field, loser_value FROM sync_conflicts WHERE id=$1',
    [Number(conflictId) || 0]
  );
  if (!cf || cf.app !== SHOP_V1.app || cf.account_kind !== SHOP_V1.accountKind || !SALE_TABLES.has(cf.table_name)) return;
  const shopId = cf.account_id;
  if (!(await flagOn(shopId))) return;
  const rowOf = v1RowOf(shopId);
  const g = new Guard({
    ctx: { accountId: shopId, userId: adminId }, deviceId: require('./sync-v1').SERVER_DEVICE, mode: 'enforce', path: 'admin',
    merges: new Map(), rowOf, firstInsert: async () => null, ip, voids: false,
  });
  const fields = { [cf.field]: cf.loser_value };
  const cur = await rowOf(db, cf.table_name, cf.row_id);
  let res = await g.check(db, { table: cf.table_name, rowId: cf.row_id, type: 'update', fields }, cur);
  if (!(res && res.reject) && cur && cf.table_name === 'sales' && cf.field === 'status'
      && statusOf(cf.loser_value) !== statusOf(cur.status)) {
    g.note(cf.row_id, { table: cf.table_name, row: cf.row_id, type: 'update', fields: ['status'] });
    res = { reject: REASON };
  }
  await g.finish();
  if (res && res.reject) throw forbiddenError();
}

/* ------------------------------------------------------- راهِ قدیم و API ِ مستقیم */

/** نامِ مجموعه در `lib/sync.js` ⇐ نامِ جدولِ v1 (همان نام‌ها) */
const LEGACY = Object.freeze({ sales: 'sales', saleItems: 'sale_items', debtors: 'debtors' });

/**
 * نگهبانِ `sync.pushChanges` ⇐ Guard یا null. `mode`: `log` برای `/api/sync` (برنامهٔ کهنه، F0-02)،
 * `enforce` برای `routes/data.js` (درِ مستقیمِ API — هیچ برنامه‌ای از آن نمی‌نویسد).
 */
async function legacyGuard({ shopId, userId = '', deviceId = '', ip = '' }, mode, changes) {
  if (!shopId || !mode || !Array.isArray(changes)) return null;
  if (!changes.some((r) => r && SALE_TABLES.has(String(r.collection || '')))) return null;
  if (!(await flagOn(shopId))) return null;
  const merges = new Map();
  for (const r of changes) {
    if (r && r.collection === 'debtors' && isPlainObject(r.data) && hasOwn(r.data, 'mergedInto')) merges.set(String(r.id || ''), r.data.mergedInto);
  }
  return new Guard({
    ctx: { accountId: shopId, userId }, deviceId, mode, path: mode === 'enforce' ? 'api' : 'legacy', merges, ip,
    rowOf: async (c, collection, id) => {
      const r = (await c.query(
        `SELECT data FROM ${LEGACY[collection]} WHERE shop_id=$1 AND id=$2 AND deleted = false`, [shopId, id]
      )).rows[0];
      return r ? r.data : null;
    },
    firstInsert: async () => null,                            //  راهِ قدیم `ts` ندارد: قلمِ تازه فقط با فاکتورِ همین دسته
  });
}

/** ردِ درِ مستقیم: ۴۰۹ و تراکنش برمی‌گردد (هیچ بخشی از نوشتن نمی‌نشیند) */
const forbiddenError = () => conflict('فاکتورِ ثبت‌شده ویرایش یا حذف نمی‌شود؛ اصلاح فقط با لغو یا برگشت (سندِ معکوس)', REASON);

module.exports = {
  REASON, PROBLEM, VOID_PROBLEM, SALE_FIXED, SALE_ONCE, ITEM_FIXED, voidChainOf,
  v1Guard, legacyGuard, adminRestoreCheck, forbiddenError, nextSale,
};
