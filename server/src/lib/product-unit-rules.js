'use strict';
/**
 * PRD-07 — قاعدهٔ واحدِ کالا در سرور؛ همتای `data/ProductDraftRules` ِ برنامه (`unitLocked` و ضریبِ تازهٔ دقیق)، به همان ترتیب:
 *   unit_locked          واحدِ پایهٔ کالایی که ورود، فروش، خرید یا حرکت دارد عوض می‌شود. ورود و فروش به همان واحد نوشته
 *                        شده‌اند و با واحدِ تازه بی‌صدا دوباره خوانده می‌شدند (۴۸ کیلو ⇒ «۴۸ گرام»). همان واحد با فاصله یا
 *                        ي/ك (`purchase-units.key` = `SimilarNames.exactKey`)، واحدِ کهنهٔ خالی، واحدِ خالیِ op و کالای تازه آزاد.
 *   unit_factor_inexact  ضریبِ واحدِ فروشِ دومِ **تازه** (≠ ضریبِ ذخیره) که دقیقاً به هزارمِ واحدِ پایه نیست (`exactMilli` =
 *                        `UnitSale.exactMilli`: ۱/۲۴، ۰٫۰۰۰۵، نامثبت، بیرون از سقف) — فقط وقتی کالا واحدِ فروشِ دوم دارد.
 *                        ضریبِ کهنهٔ دست‌نخورده (همان عدد) هرگز رد نمی‌شود؛ `null` یعنی همان ضریبِ ذخیره.
 * برای همه، صاحب دکان هم: قاعدهٔ داده است نه مجوز (برنامه هم برای همه می‌سنجد).
 *
 * پرچمِ `shop.product_unit_rules` (`live_config`، دکان یا `all`، فقط `true`؛ همان `orders.flagOf`). ⛔ پیش‌فرض خاموش ⇒ همان
 * امروز؛ op ِ کالایی که نه واحد دارد نه ضریب (`touches`) پرچم را هم نمی‌پرسد.
 *
 * دو راهِ دستگاه:
 *   v3  `authorize` ِ `sync-v3-apply.js` (`judgeV3`) درونِ تراکنش و قفلِ push: ردیفِ ذخیره و گردش از `sync_v3_rows`
 *       (opهای پیشینِ همان push پیش‌تر نشسته‌اند). op ِ پلِ v1 (`origin` = v1) در v1 سنجیده شده بود.
 *   v1  میان‌افزارِ فقط‌دکانِ `newer-fields.v1NewerFields` (`v1Guard`) پس از پالایش، بیرونِ تراکنش: ردیفِ ذخیره و گردش از
 *       `sync_rows` **به‌علاوهٔ opهای پیشینِ همان دسته** (حالِ کالا و ورود/فروشِ تازه) ⇒ یک دسته دورِ قاعده نمی‌زند؛ op ِ ردشده
 *       حالِ کالا را عوض نمی‌کند. ⛔ ‎lib/sync-v1.js‎ و ‎routes/sync-v1.js‎ ِ مشترک با پمپ دست نمی‌خورند.
 *   راهِ قدیم (`/api/sync`، برنامهٔ کهنه، F0-02) این‌جا نیست.
 */
const { key } = require('./purchase-units');

const FLAG = 'shop.product_unit_rules';
const LOCKED = 'unit_locked';
const INEXACT = 'unit_factor_inexact';
/** همان `PackBarcodes.MAX_SIZE_MILLI` */
const MAX_MILLI = 1_000_000_000;
/** گردشِ کالا = همان چهار فهرستِ `unitLocked` */
const HISTORY = Object.freeze(['warehouseEntries', 'saleItems', 'stockMovements', 'purchases']);

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const has = (o, k) => isObj(o) && Object.prototype.hasOwnProperty.call(o, k);
const filled = (v) => typeof v === 'string' && v.trim() !== '';
/** عدد یا متنِ عددی (همان خوانشِ `toMilli` ِ کاتالوگ)؛ وگرنه NaN */
function numOf(v) {
  if (typeof v === 'string' && v.trim() !== '') return Number(v);
  return typeof v === 'number' ? v : NaN;
}

/** همان `UnitSale.factorMilli`: ضریب ⇒ هزارمِ واحدِ پایه (گردِ نیمه به بالا)؛ نامثبت، NaN یا بیرون از سقف ⇒ null */
function factorMilli(f) {
  if (typeof f !== 'number' || !Number.isFinite(f) || f <= 0) return null;
  const m = Math.round(f * 1000);
  return m >= 1 && m <= MAX_MILLI ? m : null;
}

/** همان `UnitSale.exactMilli`: فقط ضریبی که دقیقاً به هزارم نوشته می‌شود */
function exactMilli(f) {
  const m = factorMilli(f);
  return m !== null && Math.abs(f * 1000 - m) <= 1e-9 * m ? m : null;
}

/** op ِ کالا که واحد یا ضریب می‌نویسد؟ (بی آن هیچ پرسشی نیست) */
const touches = (fields) => isObj(fields) && (filled(fields.unit) || (has(fields, 'unitFactor') && fields.unitFactor !== null));

/**
 * @param fields  فیلدهای op (درج یا ویرایش)
 * @param before  دادهٔ ذخیرهٔ همان کالا یا null (کالای تازه)
 * @param moved   `async () => boolean` — گردش دارد؟ فقط وقتی واحد واقعاً عوض شود پرسیده می‌شود
 * @returns {Promise<string>} دلیلِ رد یا ''
 */
async function reasonOf(fields, before, moved) {
  if (!isObj(fields)) return '';
  const b = isObj(before) ? before : null;
  if (filled(fields.unit) && b && filled(b.unit) && key(fields.unit) !== key(b.unit) && await moved()) return LOCKED;
  if (!has(fields, 'unitFactor') || fields.unitFactor === null) return '';
  const saleUnit = has(fields, 'saleUnit') && fields.saleUnit !== null ? fields.saleUnit : b?.saleUnit;
  if (!filled(saleUnit)) return '';
  const f = numOf(fields.unitFactor);
  if (b && f === numOf(b.unitFactor)) return '';
  return exactMilli(f) === null ? INEXACT : '';
}

const on = (shopId, run = null) => require('./orders').flagOf(shopId, FLAG, run);

/** گردشِ کالا در دفترِ دکان (`sync_rows` یا `sync_v3_rows`)؛ ردیفِ حذف‌شده نه */
function movedIn(run, table, shopId, productIds) {
  return run(
    `SELECT DISTINCT data->>'productId' AS pid FROM ${table === 'v3' ? 'sync_v3_rows' : 'sync_rows'}
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name = ANY($2::text[])
        AND deleted_at IS NULL AND data->>'productId' = ANY($3::text[])`,
    [shopId, HISTORY, productIds]
  ).then((r) => new Set(r.rows.map((x) => x.pid)));
}

/** v3 (`authorize`): دلیلِ رد یا null */
async function judgeV3(c, ctx, op, row) {
  if (op.table !== 'products' || (op.kind !== 'insert' && op.kind !== 'update') || !touches(op.fields)) return null;
  const run = (sql, params) => c.query(sql, params);
  if (!(await on(ctx.accountId, run))) return null;
  const before = row && isObj(row.data) ? row.data : null;
  const why = await reasonOf(op.fields, before, async () => (await movedIn(run, 'v3', ctx.accountId, [op.row_id])).has(op.row_id));
  return why || null;
}

/** v1: opهای کالا که واحد/ضریب دارند — همگام، تا دستهٔ بی آن‌ها بی هیچ پرسشی بگذرد */
function v1Hits(ops) {
  if (!Array.isArray(ops) || !ops.length) return [];
  const { validateOp } = require('./sync-v1');
  const out = [];
  ops.forEach((raw, i) => {
    if (!isObj(raw) || !touches(raw.fields)) return;
    const v = validateOp('shop', raw);
    if (v.ok && v.op.table === 'products' && (v.op.type === 'insert' || v.op.type === 'update')) out.push(i);
  });
  return out;
}

/** v1 (پس از پالایشِ `v1NewerFields`): opهای ردشده از دسته بیرون و پاسخشان سرِ جایشان (`v1-reject`) */
async function v1Guard(req, res) {
  const ctx = req.sync;
  const ops = req.body.ops;
  if (!(await on(ctx.accountId))) return;
  const { validateOp } = require('./sync-v1');
  const all = ops.map((raw) => { const v = validateOp('shop', raw); return v.ok ? v.op : null; });
  const ids = [...new Set(all.filter((o) => o && o.table === 'products').map((o) => o.row_id))];
  const { query } = require('../db');
  const stored = new Map((await query(
    `SELECT row_id, data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' AND row_id = ANY($2::text[])`, [ctx.accountId, ids]
  )).rows.map((r) => [r.row_id, isObj(r.data) ? { ...r.data } : null]));
  let dbMoved = null;
  const movedBefore = new Set();   //  ورود/فروش/خرید/حرکتِ همین دسته، پیش از op
  const rejected = new Map();
  for (let i = 0; i < all.length; i++) {
    const o = all[i];
    if (!o || o.type === 'delete' || !isObj(o.fields)) continue;
    if (HISTORY.includes(o.table) && typeof o.fields.productId === 'string') { movedBefore.add(o.fields.productId); continue; }
    if (o.table !== 'products') continue;
    const before = stored.get(o.row_id) || null;
    const why = await reasonOf(o.fields, before, async () => {
      if (movedBefore.has(o.row_id)) return true;
      if (!dbMoved) dbMoved = await movedIn(query, 'v1', ctx.accountId, ids);
      return dbMoved.has(o.row_id);
    });
    if (why) { rejected.set(i, { op_id: o.op_id, status: 'rejected', reason: why }); continue; }
    stored.set(o.row_id, { ...(before || {}), ...o.fields });
  }
  require('./v1-reject').rejectOps(req, res, ops, rejected);
}

module.exports = { FLAG, LOCKED, INEXACT, HISTORY, factorMilli, exactMilli, touches, reasonOf, judgeV3, v1Hits, v1Guard };
