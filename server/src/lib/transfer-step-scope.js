'use strict';
/**
 * STF-06 برای گامِ بی‌اینترنتِ انتقال (INV-11، مهاجرتِ ۲۸۰).
 *
 * چه چیز سنجیده‌شدنی است: ردیفِ گام (`auditLog` با `type = stock_transfer`) فیلدِ `by` دارد، ولی آن را خودِ دستگاه
 * می‌نویسد (نامِ حساب، بی امضا) — برنامهٔ دست‌کاری‌شده هر نامی می‌گذارد، پس سرور به آن تکیه نمی‌کند. امضای هر گام
 * با کلیدِ کارمند هم نیست (`shop_approver_keys` فقط مالک و مدیر). تنها هویتِ سنجیده **توکنِ عضوی است که ردیف را
 * push می‌کند** (`req.sync.userId`/`role`، همان که `doc-roles` نقشش را می‌سنجد).
 *
 * قاعده:
 *   ⛔ پرچمِ `shop.staff_scope` خاموش، مالک یا عضوِ بی‌محدودیت ⇒ هیچ کاری (هیچ ردیف، هیچ خواندنِ `shop_member_scopes`
 *      جز همان پرچم)؛ پاسخ و رفتار همان امروز.
 *   ⛔ عضوِ محدود: هنگامِ push (v3: `authorize` ِ `sync-v3.js`؛ v1: میان‌افزارِ فقط‌دکانِ `routes/sync-v1.js`) مکان‌هایش
 *      کنارِ کلیدِ گام می‌نشیند — فقط وقتی ردیف هنوز روی سرور نیست (نخستین push)، و یک بار (`ON CONFLICT DO NOTHING`).
 *   ⛔ هنگامِ خواندنِ دفتر (`stock-transfers.ingestDevice`): ارسال باید از مبدأِ محدوده باشد و دریافت/رد در مقصدِ
 *      محدوده (همان قاعدهٔ REST ِ `routes/catalog.js`)؛ وگرنه گام با `location_out_of_scope` «نپذیرفته» ثبت می‌شود
 *      (نتیجه با `GET /transfers?keys=` و وظیفهٔ `transfer_refused`)، بی هیچ حرکت.
 *
 * مانده (نوشته در STATUS): صفِ دستگاهی که پس از «خروج» با حسابِ عضوِ دیگری push شود با محدودهٔ همان عضو سنجیده
 * می‌شود (نه نویسندهٔ اصلی) — همان فرضِ `doc-roles`.
 */
const { now, query, many } = require('../db');
const { flagOf } = require('./orders');
const { SHOP } = require('./tenancy');

const FLAG = 'shop.staff_scope';
const PLACES = Object.freeze(['shop_floor', 'warehouse']);
const OUT = 'location_out_of_scope';

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const isStep = (table, f) => table === 'auditLog' && isObj(f) && f.type === 'stock_transfer' && isObj(f.xfer);

function placesOf(v) {
  let a = v;
  if (typeof a === 'string') { try { a = JSON.parse(a); } catch { a = null; } }
  return Array.isArray(a) ? PLACES.filter((p) => a.includes(p)) : [];
}

/** مکان‌های مجازِ عضوِ push، یا null (بی‌محدودیت) — همان `staff-scope.scopeOf`، از اتصالِ داده‌شده */
async function scopeOf(run, shopId, userId, role) {
  if (!shopId || !userId || role === 'owner') return null;
  if (!(await flagOf(shopId, FLAG, run))) return null;
  const r = (await run('SELECT places FROM shop_member_scopes WHERE shop_id=$1 AND user_id=$2', [shopId, String(userId)])).rows[0];
  const places = placesOf(r && r.places);
  return places.length && places.length < PLACES.length ? places : null;
}

function mark(run, shopId, key, userId, places, via) {
  return run(
    `INSERT INTO stock_transfer_step_scopes (shop_id, step_key, user_id, places, via, created_at)
     VALUES ($1,$2,$3,$4::jsonb,$5,$6) ON CONFLICT (shop_id, step_key) DO NOTHING`,
    [shopId, String(key).slice(0, 128), String(userId).slice(0, 80), JSON.stringify(places), via, now()]
  );
}

//  محدوده یک بار برای هر push (`ctx` ِ همان درخواست)
const memo = new WeakMap();

/**
 * v3/v2 (`authorize` ِ `sync-v3.js`، درونِ savepoint ِ همان op، پس از همهٔ داوری‌ها): فقط درجِ ردیفی که هنوز ننشسته و
 * [verdict] ِ تهی (پذیرفته). چیزی رد نمی‌کند — داوری هنگامِ خواندنِ دفتر است تا گامِ نپذیرفته مثلِ هر ردِ دیگر با کد و
 * وظیفه دیده شود. @returns همان [verdict]
 */
async function markV3(c, ctx, op, row, verdict = null) {
  if (verdict || op.kind !== 'insert' || (row && row.inserted) || !isStep(op.table, op.fields)) return verdict;
  if (!ctx || ctx.role === 'owner') return verdict;
  const run = (sql, params) => c.query(sql, params);
  //  شعبه: محدودهٔ مکان سطحِ دکان است (`lib/branch-key.js`)
  const shopId = require('./branch-key').shopOf(ctx);
  if (!memo.has(ctx)) memo.set(ctx, scopeOf(run, shopId, ctx.userId, ctx.role));
  const places = await memo.get(ctx);
  if (places) await mark(run, shopId, op.row_id, ctx.userId, places, 'v3');
  return verdict;
}

/** میان‌افزارِ فقط‌دکانِ push ِ v1 — پیش از `sync-v1.js` (مشترک با پمپ، دست‌نخورده)؛ هیچ opی را برنمی‌دارد */
async function v1Guard(req, res, next) {
  const ctx = req.sync;
  if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId || ctx.role === 'owner') return next();
  try {
    const ops = Array.isArray(req.body?.ops) ? req.body.ops : [];
    const steps = ops.filter((o) => isObj(o) && o.type === 'insert' && typeof o.row_id === 'string' && o.row_id && isStep(o.table, o.fields));
    if (!steps.length) return next();
    const run = (sql, params) => query(sql, params);
    const places = await scopeOf(run, ctx.accountId, ctx.userId, ctx.role);
    if (!places) return next();
    const ids = [...new Set(steps.map((o) => o.row_id.slice(0, 128)))];
    const seen = new Set((await many(
      `SELECT row_id FROM sync_rows WHERE app=$1 AND account_kind=$1 AND account_id=$2 AND table_name='auditLog' AND row_id = ANY($3::text[])`,
      [SHOP.app, ctx.accountId, ids]
    )).map((r) => r.row_id));
    for (const id of ids) if (!seen.has(id)) await mark(run, ctx.accountId, id, ctx.userId, places, 'v1');
    return next();
  } catch (err) { return next(err); }
}

/** هنگامِ خواندنِ دفتر: مکان‌های مجازِ فرستندهٔ همین گام، یا null (بی سنجش) */
async function placesFor(c, shopId, key) {
  const r = (await c.query('SELECT places FROM stock_transfer_step_scopes WHERE shop_id=$1 AND step_key=$2', [shopId, key])).rows[0];
  if (!r) return null;
  const places = placesOf(r.places);
  return places.length ? places : null;
}

module.exports = { FLAG, OUT, isStep, scopeOf, markV3, v1Guard, placesFor };
