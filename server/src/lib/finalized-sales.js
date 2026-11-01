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
 * ⛔ درهای سرور که op نمی‌سازند (`restoreConflict`/`restoreDeleted` ِ مدیر، پلِ v3) نگهبان ندارند؛ Sync v3 خودش
 *    سند را فقط‌افزودنی می‌داند (`append_only_edit`).
 */
const db = require('../db');
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
  'promoDiscountMinor', 'coupons', 'approval', 'createdAt', 'date',
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

const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const hasOwn = (o, k) => Object.prototype.hasOwnProperty.call(o, k);
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
  constructor({ ctx, deviceId, mode, path, merges, rowOf, firstInsert, ip = '' }) {
    Object.assign(this, { ctx, deviceId, mode, path, merges, rowOf, firstInsert, ip });
    this.notes = new Map();
    this.newSales = new Set();
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
      if (!cur) { this.newSales.add(rowId); return null; }   //  ثبت، نه ویرایش (ردیفِ حذف‌شده هم «نیست»)
      sale = rowId;
      if (type === 'delete') changed = ['*delete*'];
      else {
        const insert = type === 'insert';
        changed = saleChanges(cur, fields, { full, insert });
        const move = customerMove(cur, fields, { full });
        if (move && !(await this.merged(c, move[0], move[1])) && !(insert && (await this.merged(c, move[1], move[0])))) {
          changed.push('debtorId');
        }
        if (insert && !changed.length) return this.enforce ? { replay: true } : null;
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
    rowOf: async (c, table, id) => {
      const r = (await c.query(
        `SELECT data FROM sync_rows WHERE ${V1_ACC} AND table_name=$2 AND row_id=$3 AND deleted_at IS NULL`, [shopId, table, id]
      )).rows[0];
      return r ? r.data : null;
    },
    firstInsert: async (c, saleId) => {
      const r = (await c.query(
        `SELECT device_id, client_ts FROM oplog WHERE ${V1_ACC} AND table_name='sales' AND row_id=$2 AND op_type='insert'
          ORDER BY server_seq ASC LIMIT 1`, [shopId, saleId]
      )).rows[0];
      return r ? { device: r.device_id, ts: Number(r.client_ts) } : null;
    },
  });
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
  REASON, PROBLEM, SALE_FIXED, SALE_ONCE, ITEM_FIXED, v1Guard, legacyGuard, forbiddenError,
};
