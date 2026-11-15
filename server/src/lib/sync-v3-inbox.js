'use strict';
/**
 * SYN-07 / S28 — صندوقِ تعارضِ v3 «در برنامه»، سمتِ سرور: فقط آنچه صفحهٔ برنامه بیشتر از امروز لازم دارد، روی
 * همان دو درِ امروز (`GET /api/sync/v3/conflicts`، `POST /api/sync/v3/conflicts/:id/resolve`) — درِ تازه‌ای نیست.
 *
 * ⛔ پرچمِ دکانِ `shop.sync_v3_inbox` (`live_config`، پیش‌فرض خاموش). خاموش ⇒ هر دو در بایت‌به‌بایت همان امروز:
 *    فهرست بی کلیدِ `inbox`، کارِ `merge` همان ۴۰۰ ِ `bad_action`، بستنِ دوباره همان ۴۰۹ ِ `already_resolved`.
 * روشن ⇒ سه چیز:
 *   • `inbox: { v: 1, merge: true }` در پاسخِ فهرست — برنامه فقط با همین کلید صندوق را نشان می‌دهد؛
 *   • `merge` (+ `fields`): از ردیفِ چندفیلدیِ بازنده (`field = '*'` با هر دو مقدار، مثلِ `clock_skew`) فقط همین
 *     فیلدها برمی‌گردند و بقیه همان برنده می‌مانند — همان opِ برگرداندنِ امروز (`srv.rv.<شناسه>`، HLCِ تازهٔ سرور)
 *     با فیلدهای کم‌تر؛ حال `reverted` (همان CHECK ِ مهاجرتِ ۰۶۰) و فهرستِ فیلدها در `detail.merged`؛
 *   • تکرارِ **همان** تصمیم به دستِ **همان** کاربر (پاسخِ گم‌شده، صفِ بی‌اینترنتِ برنامه) ⇒ ۲۰۰ با `duplicate: true` و
 *     همان ردیف، نه ۴۰۹ — هیچ opِ دومی ساخته نمی‌شود. تصمیمِ دیگر یا کاربرِ دیگر همان ۴۰۹.
 * اجازه همان `data.delete.any` ِ امروز (`RESOLVE_PERMISSION` ِ `sync-v3.js`)؛ شاگرد ۴۰۳.
 */
const { badRequest } = require('../middleware/errors');
const { SHOP } = require('./tenancy');

const FLAG = 'shop.sync_v3_inbox';
const MAX_FIELDS = 64;
const FIELD_RE = /^[A-Za-z0-9_]{1,64}$/;
//  همان چهار کارِ `resolveConflict`؛ `merge` فقط با پرچم
const ACTIONS = Object.freeze(['keep', 'revert', 'dismiss']);
const ACTIONS_ON = Object.freeze([...ACTIONS, 'merge']);

const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** پرچمِ همین دکان (شعبه ⇒ همان دکان، `orders.flagOf`)؛ فقط `true` روشن است */
async function enabled(ctx) {
  if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId) return false;
  return require('./orders').flagOf(String(ctx.accountId), FLAG);
}

/** نشانِ فهرست برای برنامه */
function hint() { return { v: 1, merge: true }; }

/** کارهای پذیرفته */
const actions = (on) => (on ? ACTIONS_ON : ACTIONS);

/** ادغام فقط روی ردیفِ چندفیلدی که هر دو سو مقدار دارند */
function mergeable(cf) {
  return !!cf && cf.field === '*' && isPlainObject(cf.loser_value) && isPlainObject(cf.winner_value)
    && Object.keys(cf.loser_value).length > 0;
}

/** فیلدهای برگشتی ⇒ فهرستِ پاک (یکتا، به ترتیبِ بازنده)؛ نادرست ⇒ ۴۰۰ */
function mergeFields(cf, fields) {
  if (!mergeable(cf)) throw badRequest('این مورد ادغام‌شدنی نیست', 'not_mergeable');
  if (!Array.isArray(fields) || fields.length === 0 || fields.length > MAX_FIELDS
    || !fields.every((f) => typeof f === 'string' && FIELD_RE.test(f))) {
    throw badRequest('فهرستِ فیلدها درست نیست', 'bad_fields');
  }
  const want = new Set(fields);
  const keys = Object.keys(cf.loser_value);
  if ([...want].some((f) => !keys.includes(f))) throw badRequest('فیلدی که در این مورد نیست', 'bad_fields');
  return keys.filter((k) => want.has(k));
}

/** opِ ادغام: همان opِ برگرداندن (`revertOp`) فقط با فیلدهای برگزیده */
function mergeOp(cf, picked, stamp) {
  const fields = {};
  for (const k of picked) fields[k] = cf.loser_value[k];
  return { op_id: `srv.rv.${cf.id}`.slice(0, 80), table: cf.table_name, row_id: cf.row_id, hlc: stamp, kind: 'update', fields };
}

/** حالِ پایانیِ این کار روی این ردیف — همان قاعدهٔ `resolveConflict` */
function finalOf(action, task) {
  if (action === 'revert' || action === 'merge') return 'reverted';
  return task || action === 'dismiss' ? 'dismissed' : 'kept';
}

/** ردیفِ بسته همان تصمیمِ همین کاربر است؟ (تکرارِ بی‌اثر) */
function sameDecision(cf, action, task, userId) {
  const who = String(userId || '');
  return !!who && cf.status !== 'open' && cf.resolved_by === who && cf.status === finalOf(action, task);
}

module.exports = { FLAG, MAX_FIELDS, enabled, hint, actions, mergeable, mergeFields, mergeOp, finalOf, sameDecision };
