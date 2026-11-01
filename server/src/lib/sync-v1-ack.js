'use strict';
/**
 * cursor ِ دستگاه از تأییدِ خودِ دستگاه — پرچمِ دکانِ `sync.cursor_from_since` (پیش‌فرض خاموش؛ #481).
 *
 * ── چه چیزی نبود ─────────────────────────────────────────────────────
 * `pull()` ِ `lib/sync-v1.js` cursor ِ دستگاه در `sync_devices` را برابرِ **آنچه فرستاد** می‌گذارد
 * (GREATEST)، و همین cursor تنها ملاکِ «این دستگاه نوشتهٔ دیگری را دیده بود؟» ِ تعارض در push است.
 * پاسخِ pull که در راه گم شود ⇒ سرور خیال می‌کند دستگاه دیده و ویرایشِ بعدیِ همان فیلد بی ردیفِ
 * `sync_conflicts` رویش می‌نشیند (یافتهٔ PERF-05، «۳-ب» ِ #449).
 * ──────────────────────────────────────────────────────────────────────
 *
 * با پرچم، cursor ِ ذخیره‌شده = آنچه دستگاه **تأیید** کرده:
 *   • pull ⇐ `since` ِ همان درخواست («تا این‌جا را دارم»)، بریده به سرِ دفتر، **دقیق** (نه GREATEST)؛
 *     پاسخِ pull دست نمی‌خورد ([aroundPull]).
 *   • push ⇐ `since` ِ اختیاریِ بدنه (cursor ِ واقعیِ دستگاه)، بریده به سرِ دفتر، پیش از push نوشته
 *     می‌شود تا ملاکِ تعارضِ همان دسته باشد ([aroundPush]). بی `since` ⇒ همان تأییدِ pull ِ پیشین؛
 *     برنامه‌ها push را پیش از pull می‌زنند، پس ممکن است تعارضِ اضافه ثبت شود، هرگز کم.
 *
 * ⛔ `lib/sync-v1.js` مشترک با پمپ است و دست نمی‌خورد: این لایه فقط در `routes/sync-v1.js` و فقط برای
 * حسابِ دکان کار می‌کند. پمپ (یا بی ctx) حتی پرچم را نمی‌خواند و همان مسیرِ main را می‌رود.
 *
 * فرقِ کوچک با نوشتن درونِ تراکنشِ push: سرِ دفتر برای بریدن پیش از قفلِ push خوانده می‌شود (اگر
 * دستگاهِ دیگری هم‌زمان نوشت، cursor کوچک‌تر ⇒ محتاط‌تر)؛ و دستگاهی که هنوز ردیفِ `sync_devices`
 * ندارد، در نخستین push همان ۰ ِ امروز را دارد (محتاط) و پس از آن cursor ِ تأییدشده می‌گیرد.
 * خطای push (۴۰۰، ۴۲۶، …) cursor ِ پیشین را برمی‌گرداند — «هیچ چیزی اعمال نشد».
 */
const { query } = require('../db');
const { SHOP } = require('./tenancy');

const FLAG = 'sync.cursor_from_since';

const ACC = 'app=$1 AND account_kind=$2 AND account_id=$3';
const accArgs = (ctx) => [ctx.app, ctx.accountKind, ctx.accountId];

/** حسابِ دکان (حسابِ پمپ `station` است) */
const isShop = (ctx) => Boolean(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && ctx.accountId);

/** پرچمِ همین دکان (یا `all`) — همان `orders.flagOf`؛ پمپ هرگز نمی‌پرسد */
async function on(ctx) {
  if (!isShop(ctx)) return false;
  return require('./orders').flagOf(ctx.accountId, FLAG);
}

/** `since` ِ بدنهٔ push ⇒ عددِ صحیحِ نامنفی، یا null (نیست یا نادرست ⇒ نادیده، یعنی محتاط) */
function ackOf(raw) {
  if (typeof raw === 'number') return Number.isSafeInteger(raw) && raw >= 0 ? raw : null;
  if (typeof raw === 'string' && /^\d{1,16}$/.test(raw)) {
    const n = Number(raw);
    return Number.isSafeInteger(n) ? n : null;
  }
  return null;
}

async function headOf(ctx) {
  const r = (await query(`SELECT COALESCE(MAX(server_seq),0)::bigint AS head FROM oplog WHERE ${ACC}`, accArgs(ctx))).rows[0];
  return Number(r.head);
}

async function cursorOf(ctx, deviceId) {
  const r = (await query(`SELECT cursor FROM sync_devices WHERE ${ACC} AND device_id=$4`, [...accArgs(ctx), deviceId])).rows[0];
  return r ? Number(r.cursor) : null;
}

/** cursor ِ تأییدشده، دقیق؛ ردیفی که هنوز نیست ساخته نمی‌شود (آن را `sync-v1` می‌سازد) */
async function setCursor(ctx, deviceId, cursor) {
  await query(`UPDATE sync_devices SET cursor=$5 WHERE ${ACC} AND device_id=$4`, [...accArgs(ctx), deviceId, cursor]);
}

/**
 * دورِ push: بی پرچم یا بی `since` ِ درست ⇒ همان [run] (main). با هر دو ⇒ cursor = `since` (بریده به سرِ
 * دفتر) پیش از push، و دوباره پس از آن (نخستین push ِ دستگاه ردیف را ساخته است).
 */
async function aroundPush(ctx, deviceId, since, run) {
  const claimed = ackOf(since);
  if (claimed === null || !(await on(ctx))) return run();
  const prev = await cursorOf(ctx, deviceId);
  const acked = Math.min(claimed, await headOf(ctx));
  if (prev !== null) await setCursor(ctx, deviceId, acked);
  let out;
  try {
    out = await run();
  } catch (err) {
    if (prev !== null) await setCursor(ctx, deviceId, prev);
    throw err;
  }
  await setCursor(ctx, deviceId, acked);
  return out;
}

/**
 * دورِ pull: بی پرچم ⇒ همان [run] با همان `since` (main). با پرچم ⇒ پس از pull، cursor = `since` ِ همین
 * درخواست، بریده به سرِ دفترِ همان پاسخ. پاسخ برای `since` ≤ سرِ دفتر مو‌به‌مو همان است؛ `since` ِ
 * بزرگ‌تر (ادعای بیش از دفتر) پیش از pull به سرِ دفتر بریده می‌شود — همان بریدنِ تأیید، و `sync-v1` آن
 * عددِ بی‌معنا را در `sync_devices` نمی‌نویسد (پارامترِ `COALESCE($5,0)` ِ آن integer است ⇒ ۵۰۰ از 2³¹).
 */
async function aroundPull(ctx, deviceId, since, run) {
  if (!(await on(ctx))) return run(since);
  const from = Math.max(0, Number(since) || 0);
  const head = await headOf(ctx);
  const out = await run(from > head ? head : since);
  await setCursor(ctx, deviceId, Math.min(Math.floor(from), Number(out.head) || 0));
  return out;
}

module.exports = { FLAG, isShop, on, ackOf, aroundPush, aroundPull };
