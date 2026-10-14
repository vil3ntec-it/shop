'use strict';
/**
 * مبدلِ v2 ⇒ v3 (SYN-11، بخشِ ۳.۸.۱ بندِ ۵).
 *
 * «op v2» یعنی opِ پروتکلِ Sync v1 (schema ۱ تا ۳ی امروز). دو جا به کار
 * می‌رود و هر دو **فقط می‌خوانند**؛ هیچ چیزی در v1 عوض نمی‌شود:
 *   ۱. برنامه‌ای که به v3 رفته ولی صفِ opهای قدیمش هنوز نرفته، همان‌ها را
 *      با `format: "v2"` به `/api/sync/v3/push` می‌فرستد ⇒ هیچ opی گم نمی‌شود.
 *   ۲. پلِ v1 ⇒ v3 (`sync-v3.js`): opهای دستگاه‌های هنوز-v1ِ همان دکان.
 *
 * ── چه می‌کند ────────────────────────────────────────────────────
 *   • سنجش با همان `validateOp`ِ v1 و بالا آوردن با همان مهاجرت‌های v1
 *     (`sync-v1-migrations.js`) — یک قاعده، نه دو.
 *   • مبلغِ `Double` (افغانی) ⇒ `<نام>Minor` (یک‌صدم، Long)؛ مقدار ⇒ `<نام>Milli`.
 *   • `{"$inc": n}` ⇒ op «دلتا» (فقط شمارنده‌ها).
 *   • لغوِ فاکتور (`status: "cancelled"`) ⇒ op «لغو» با دلیل.
 *   • حذفِ سندِ مالی ⇒ «لغو» (سندِ مالی نابود نمی‌شود).
 *   • ویرایشِ سندِ فقط‌افزودنی ⇒ op با `legacy` که سرور به صندوقِ تعارض
 *     می‌برد — دیده می‌شود، گم نمی‌شود، ولی سندِ مالی را هم عوض نمی‌کند.
 *   • مانده‌ها و نشان‌های محلی نمی‌روند (`dropped` در خروجی).
 *   • مقدارِ نامفهوم (مثلاً مبلغی که عدد نیست) با **نامِ قدیم** می‌ماند.
 *
 * تابع خالص است: یک op می‌گیرد و همیشه همان خروجی را می‌دهد؛ پس تبدیلِ
 * دوبارهٔ همان op همان `op_id`ها را می‌سازد و تکرار بی‌اثر می‌ماند.
 */
const { validateOp } = require('./sync-v1');
const v1migrations = require('./sync-v1-migrations');
const schema = require('./sync-v3-schema');
const hlc = require('./hlc');

const DEFAULT_CANCEL_REASON = 'لغو از نسخهٔ قدیمیِ برنامه';
const DELETE_CANCEL_REASON = 'حذف از نسخهٔ قدیمیِ برنامه';

function isPlainObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
function isInc(v) { return isPlainObject(v) && Object.keys(v).length === 1 && Number.isFinite(v.$inc); }

/** عدد × مقیاس، گردِ دهدهی (نیمه به سمتِ +∞، مثلِ Math.round)؛ نامفهوم ⇒ undefined. */
function scaled(x, scale) {
  if (typeof x === 'string' && x.trim() !== '') x = Number(x);
  if (typeof x !== 'number' || !Number.isFinite(x)) return undefined;
  //  toPrecision(15) خطای دودوییِ ضرب را می‌گیرد: 1.005×100 = 100.49999… ⇒ 100.5
  const v = Math.round(Number((x * scale).toPrecision(15)));
  if (!Number.isSafeInteger(v)) return undefined;
  return v === 0 ? 0 : v;   //  −۰ نه
}
const toMinor = (x) => scaled(x, 100);
const toMilli = (x) => scaled(x, 1000);

/**
 * فیلدهای op v2 ⇒ {set, inc, dropped}
 * `inc` فقط برای `$inc` است؛ `set` همه چیزِ دیگر.
 */
function convertFields(spec, fields) {
  const set = {};
  const inc = {};
  const dropped = [];
  for (const [k, v] of Object.entries(fields || {})) {
    if (k === 'id') continue;
    if (spec.derived.includes(k) || spec.localOnly.includes(k)) { dropped.push(k); continue; }
    const unit = spec.money.includes(k) ? ['Minor', toMinor] : spec.qty.includes(k) ? ['Milli', toMilli] : null;
    if (isInc(v)) {
      if (unit) {
        const n = unit[1](v.$inc);
        if (n !== undefined) { inc[k + unit[0]] = n; continue; }
      } else { inc[k] = v.$inc; continue; }
      set[k] = v;                          //  دلتای نامفهوم: همان‌طور، به صندوق می‌رود
      continue;
    }
    if (unit) {
      if (v === null) { set[k + unit[0]] = null; continue; }
      const n = unit[1](v);
      if (n !== undefined) { set[k + unit[0]] = n; continue; }
    }
    set[k] = v;
  }
  return { set, inc, dropped };
}

/**
 * @param raw  opِ v2 همان‌طور که برنامه فرستاده (یا از `oplog`ِ v1 خوانده شده)
 * @param opts {schemaVersion, deviceId, index, hlc?}
 * @returns {{ok:true, ops:Array, dropped:string[]} | {ok:false, reason:string, op_id:string}}
 */
function fromV2(raw, { schemaVersion, deviceId, index = 0, hlc: given = '' } = {}) {
  const val = validateOp('shop', raw);
  if (!val.ok) return { ok: false, reason: val.reason, op_id: val.op_id };
  let op = val.op;
  const from = Number(schemaVersion) || 1;
  if (from < v1migrations.currentVersion('shop')) op = v1migrations.upgrade('shop', from, op);

  const spec = schema.specOf(op.table);
  if (!spec) return { ok: false, reason: 'unknown_table', op_id: op.op_id };

  const stamp = (given && hlc.canonical(given)) || hlc.fromTs(op.ts, index, String(deviceId || 'v2'));
  const base = { tx: op.op_id, hlc: stamp, table: op.table, row_id: op.row_id, legacy: true };
  const { set, inc, dropped } = op.type === 'delete' ? { set: {}, inc: {}, dropped: [] } : convertFields(spec, op.fields);
  const parts = [];            //  [پسوند، op]

  const counterInc = {};
  const strayInc = {};
  for (const [k, n] of Object.entries(inc)) (spec.counters.includes(k) ? counterInc : strayInc)[k] = n;
  //  دلتای فیلدی که شمارنده نیست: همان `$inc` برای ویرایشِ قدیمی می‌ماند
  for (const [k, n] of Object.entries(strayInc)) set[k] = { $inc: n };

  if (op.type === 'insert') {
    parts.push(['i', { kind: 'insert', fields: set }]);
  } else if (op.type === 'delete') {
    if (spec.cancel) parts.push(['c', { kind: 'cancel', reason: DELETE_CANCEL_REASON, fromDelete: true }]);
    else parts.push(['x', { kind: 'delete' }]);
  } else if (spec.policy === 'master') {
    if (Object.keys(set).length) parts.push(['u', { kind: 'update', fields: set }]);
  } else {
    //  سندِ فقط‌افزودنی: لغو جداست، شمارنده دلتاست، بقیه ویرایشِ قدیمی
    if (spec.cancel && set.status === 'cancelled') {
      const reason = typeof set.cancelReason === 'string' && set.cancelReason.trim()
        ? set.cancelReason.trim().slice(0, 200) : DEFAULT_CANCEL_REASON;
      delete set.status;
      delete set.cancelReason;
      parts.push(['c', { kind: 'cancel', reason }]);
    }
  }
  if (Object.keys(counterInc).length) parts.push(['d', { kind: 'delta', fields: counterInc }]);
  if (op.type === 'update' && spec.policy !== 'master' && Object.keys(set).length) {
    parts.push(['e', { kind: 'update', fields: set }]);
  }

  //  یک op ⇒ همان شناسه؛ چند op ⇒ پسوندِ نقطه‌دار (ULID هرگز نقطه ندارد)
  const ops = parts.map(([suffix, p]) => ({
    ...base, ...p, op_id: parts.length === 1 ? op.op_id : `${op.op_id}.${suffix}`,
  }));
  return { ok: true, ops, dropped };
}

module.exports = { fromV2, convertFields, toMinor, toMilli, DEFAULT_CANCEL_REASON, DELETE_CANCEL_REASON };
