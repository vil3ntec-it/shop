'use strict';
/**
 * PRD-07 — واحدهای خریدِ چندگانهٔ کالا (`products.purchaseUnits`): سنجشِ شکل در سرور.
 * همتای `validate` ِ `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/product/PurchaseUnits.kt` با **همان کدها**
 * (`pu_*`). سرور کالاهای دیگر را نمی‌شناسد، پس فقط شکل را می‌سنجد؛ یکتاییِ بارکد بین کالاها و «اعشار برای واحدِ
 * شمارشی» کارِ برنامه است (`WarehouseEngine` ⇐ `PurchaseUnits.check`).
 *
 *   • `null` (پاک کردن) و نبودنِ کلید همیشه درست‌اند — کالای بی واحدِ خرید همان پیشین است.
 *   • فهرست، حداکثر ۱۰ واحد؛ هر واحد شیء با `name` (۱ تا ۳۰ نویسه، یکتا با همان کلیدِ `SimilarNames.exactKey`)،
 *     `factorMilli` (عددِ صحیحِ ۱ تا ۱٬۰۰۰٬۰۰۰٬۰۰۰ = یک میلیون واحدِ پایه)، و به اختیار `supplierId` (۱ تا ۱۲۸، نه فقط
 *     فاصله) و `barcode` (۱ تا ۶۴، بی فاصله، یکتا میانِ واحدهای همین کالا).
 *
 * سه راهِ همگام‌سازیِ دکان: v3 (`sync-v3-merge.validate`)، v1 (میان‌افزارِ فقط‌دکانِ `newer-fields.v1NewerFields`، چون
 * ‎routes/sync-v1.js‎ و ‎lib/sync-v1.js‎ ِ مشترک با پمپ دست نمی‌خورند) و راهِ قدیم (`sync.js`). opی که شکلِ نادرست دارد
 * با `bad_purchase_units` رد می‌شود؛ پمپ و حسابِ غیرِ دکان هرگز به این‌جا نمی‌رسند.
 */

const MAX_UNITS = 10;
const MAX_NAME = 30;
const MAX_FACTOR_MILLI = 1000000000;
const MAX_REF = 128;
const MAX_CODE = 64;
const REASON = 'bad_purchase_units';

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const has = (o, k) => isObj(o) && Object.prototype.hasOwnProperty.call(o, k);
/** همان «خالی» ِ `PurchaseOrderCore.blank`: فقط فاصله، تب و خطِ تازه */
const blank = (s) => /^[ \t\n\r]*$/.test(s);
/** همان `SimilarNames.exactKey` */
const key = (name) => name.trim().replace(/\s+/g, ' ').replace(/ي/g, 'ی').replace(/ك/g, 'ک').toLowerCase();

/** @returns {null | {code: string, index: number}} */
function check(value) {
  if (value === null || value === undefined) return null;
  if (!Array.isArray(value)) return { code: 'pu_not_list', index: -1 };
  if (value.length > MAX_UNITS) return { code: 'pu_too_many', index: -1 };
  const names = new Set();
  const codes = new Set();
  for (let i = 0; i < value.length; i++) {
    const u = value[i];
    if (!isObj(u) || typeof u.name !== 'string') return { code: 'pu_name_required', index: i };
    const name = u.name.trim();
    if (!name) return { code: 'pu_name_required', index: i };
    if (name.length > MAX_NAME) return { code: 'pu_name_too_long', index: i };
    const k = key(name);
    if (names.has(k)) return { code: 'pu_duplicate_name', index: i };
    names.add(k);
    const f = u.factorMilli;
    if (!Number.isSafeInteger(f) || f <= 0) return { code: 'pu_bad_factor', index: i };
    if (f > MAX_FACTOR_MILLI) return { code: 'pu_factor_too_big', index: i };
    if (u.supplierId !== undefined && u.supplierId !== null
      && (typeof u.supplierId !== 'string' || u.supplierId.length > MAX_REF || blank(u.supplierId))) {
      return { code: 'pu_bad_supplier', index: i };
    }
    if (u.barcode !== undefined && u.barcode !== null) {
      if (typeof u.barcode !== 'string' || !u.barcode || u.barcode.length > MAX_CODE || /\s/.test(u.barcode)) {
        return { code: 'pu_bad_barcode', index: i };
      }
      if (codes.has(u.barcode)) return { code: 'pu_duplicate_barcode', index: i };
      codes.add(u.barcode);
    }
  }
  return null;
}

/** فیلدهای ردیفِ کالا (درج یا ویرایش) ⇒ دلیلِ رد یا '' */
function reasonOf(fields) {
  if (!has(fields, 'purchaseUnits')) return '';
  return check(fields.purchaseUnits) ? REASON : '';
}

const isProducts = (table) => table === 'products';

/**
 * v1: opِ `products` با `purchaseUnits` ِ نادرست رد می‌شود (فقط همان op)، بقیهٔ دسته به v1 می‌رسد. پس از پالایشِ
 * null ِ کهنه (`v1NewerFields`) صدا زده می‌شود، پس همان opی را می‌بیند که v1 می‌نشاند. هیچ op ِ دیگری دست نمی‌خورد.
 */
function v1Guard(req, res) {
  const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
  if (!ops || !ops.length) return;
  //  ⛔ همان خوانشِ v1 (`sync-v1.normalize`/`tableOf`): «Products»، «pro_ducts» و `op_type`/`opType` هم همان opِ کالا‌اند —
  //  پیش‌تر فقط `table === 'products'` و `type` سنجیده می‌شد و شکلِ دیگرِ همان op بی سنجش می‌نشست
  const { tableOf } = require('./sync-v1');
  const rejected = new Map();
  ops.forEach((o, i) => {
    if (!isObj(o) || !isProducts(tableOf('shop', o.table ?? o.table_name ?? o.tableName))) return;
    const kind = o.type ?? o.op_type ?? o.opType;
    if (kind !== 'insert' && kind !== 'update') return;
    if (reasonOf(o.fields)) rejected.set(i, { op_id: String(o.op_id ?? o.opId ?? '').slice(0, 80), status: 'rejected', reason: REASON });
  });
  if (rejected.size) require('./v1-reject').rejectOps(req, res, ops, rejected);
}

/** راهِ قدیم (`changes`): ردیفِ کالا با `purchaseUnits` ِ نادرست ⇒ دلیل (کلِ دسته ۴۰۰ می‌گیرد) */
function legacyReason(changes) {
  for (const ch of Array.isArray(changes) ? changes : []) {
    if (!isObj(ch) || ch.collection !== 'products' || ch.deleted === true) continue;
    if (reasonOf(ch.data)) return REASON;
  }
  return '';
}

module.exports = { MAX_UNITS, MAX_NAME, MAX_FACTOR_MILLI, REASON, key, check, reasonOf, v1Guard, legacyReason };
