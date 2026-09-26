'use strict';
/**
 * ══ کدِ دسترسیِ پمپ — کلیدِ اپِ کارمندان ══════════════════════════════════
 *
 * خواستهٔ صاحب مخزن: «برای هر پمپ یک کد باشد که هر کسی برنامه را نصب
 * می‌کند همان را بزند و فقط حساب‌های همان پمپ را ببیند.»
 *
 *     برنامهٔ کامپیوتر ──GET  /pump/device/access-code──▶  {code: '4829-1736'}
 *     گوشیِ کارمند    ──POST /pump/public/join {code}──▶  {station, home:{url, readKey}}
 *
 * ── قاعده‌ها ─────────────────────────────────────────────────────────────
 *   • یک پمپ، یک کد. تا صاحبش عوض نکند همان می‌ماند — کارمندِ تازه هم با
 *     همان کد می‌آید. عوض کردن، همان لحظه کدِ قبلی را بی‌اثر می‌کند.
 *   • کد فقط به **همان یک پمپ** می‌رسد. جوابِ ‎join‎ چیزی جز نشانی و رمزِ
 *     فقط‌خواندنیِ آن پمپ ندارد؛ نه فهرستِ پمپ‌ها، نه شناسهٔ داخلی.
 *   • ⛔ از ۱۴۰۵/۰۷/۱۴ (۲.۱۱.۶) کد **هشت رقم** است — خواستهٔ صاحب سامانه:
 *     روی صفحه‌کلیدِ عددیِ گوشی زده می‌شود و خواندنش پای تلفن آسان است.
 *     رقمِ فارسی و عربی هم پذیرفته می‌شود (‎۴۸۲۹۱۷۳۶‎).
 *   • ⛔ کدِ حرفیِ پیشین (‎K7PM-3XQ2‎) به `access_code_old` می‌رود و **هنوز**
 *     در را باز می‌کند؛ فقط «عوض کردنِ کد» هر دو را باطل می‌کند.
 *   • هر پمپ کدش را همان لحظهٔ ساختن می‌گیرد (`stations.js`)، و `sweep()`
 *     سرِ بالا آمدنِ سرور و هر شش ساعت هر پمپی را که هنوز کدِ هشت‌رقمی
 *     ندارد درست می‌کند. پس «هر حساب یک کد» منتظرِ باز شدنِ هیچ صفحه‌ای نیست.
 */
const { randomInt } = require('crypto');
const { one, query, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');

//  الفبای کدهای حرفیِ پیشین — فقط برای شناختنشان، دیگر ساخته نمی‌شوند
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const DIGITS = '0123456789';
const LENGTH = 8;

/** رقمِ فارسی (۰-۹) و عربی (٠-٩) ⇒ لاتین. */
function asciiDigits(s) {
  return String(s || '').replace(/[\u06F0-\u06F9]/g, d => String(d.charCodeAt(0) - 0x06F0))
    .replace(/[\u0660-\u0669]/g, d => String(d.charCodeAt(0) - 0x0660));
}

/** ورودیِ کاربر ⇒ شکلِ ذخیره‌شده. ‎'۴۸۲۹-۱۷۳۶'‎ ⇒ ‎'48291736'‎، ‎'k7pm-3xq2'‎ ⇒ ‎'K7PM3XQ2'‎. */
function normalize(raw) {
  return asciiDigits(raw).toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** کدِ امروزی: هشت رقم. */
function isDigits(code) {
  return code.length === LENGTH && /^[0-9]+$/.test(code);
}

/** کدِ امروزی یا کدِ حرفیِ پیشین. */
function isValid(code) {
  if (code.length !== LENGTH) return false;
  if (isDigits(code)) return true;
  for (const ch of code) if (!ALPHABET.includes(ch)) return false;
  return true;
}

/** برای نمایش: ‎4829-1736‎ (و کدِ پیشین ‎K7PM-3XQ2‎). */
function format(code) {
  const c = String(code || '');
  return c.length === LENGTH ? c.slice(0, 4) + '-' + c.slice(4) : c;
}

/** هشت رقمِ تصادفی — رقمِ اول صفر نیست تا در هیچ جدولی «۰» ی سرش نیفتد. */
function random() {
  let s = String(1 + randomInt(9));
  for (let i = 1; i < LENGTH; i++) s += DIGITS[randomInt(DIGITS.length)];
  return s;
}

async function taken(code) {
  return !!(await one('SELECT 1 FROM stations WHERE access_code=$1 OR access_code_old=$1', [code]));
}

/**
 * کدِ هشت‌رقمیِ تازه برای پمپ.
 * `keepOld` ⇒ کدِ حرفیِ کنونی به `access_code_old` می‌رود و هنوز پذیرفته است
 * (فقط برای جابه‌جاییِ خودکار)؛ وگرنه هر کدِ پیشین همان لحظه باطل می‌شود.
 */
async function assign(stationId, { keepOld = false } = {}) {
  for (let attempt = 0; attempt < 12; attempt++) {
    const code = random();
    if (await taken(code)) continue;
    const r = keepOld
      ? await one(
        `UPDATE stations
            SET access_code_old = CASE WHEN access_code <> '' AND access_code_old = '' THEN access_code ELSE access_code_old END,
                access_code = $2, updated_at = $3
          WHERE id = $1 RETURNING access_code`, [stationId, code, now()])
      : await one(
        `UPDATE stations SET access_code=$2, access_code_old='', updated_at=$3 WHERE id=$1 RETURNING access_code`,
        [stationId, code, now()]);
    if (r) return r.access_code;
    break;
  }
  throw conflict('ساختِ کد ممکن نشد، دوباره تلاش کنید', 'access_code_failed');
}

/**
 * کدِ این پمپ؛ اگر هنوز ندارد — یا هنوز کدِ حرفیِ پیشین است — همین حالا
 * کدِ هشت‌رقمی می‌گیرد (کدِ حرفی پذیرفته می‌ماند).
 */
async function ensure(stationId) {
  const st = await one('SELECT access_code FROM stations WHERE id=$1', [stationId]);
  if (st && isDigits(st.access_code || '')) return st.access_code;
  return assign(stationId, { keepOld: true });
}

/** کدِ تازه به دستِ صاحبِ پمپ — کدِ قبلی (و کدِ حرفیِ پیشین) همان لحظه از کار می‌افتد. */
async function rotate(stationId) {
  return assign(stationId, { keepOld: false });
}

/**
 * رباتِ کدها: هر پمپی که هنوز کدِ هشت‌رقمی ندارد (بی‌کد، یا کدِ حرفیِ
 * پیشین) همین حالا می‌گیرد. سرِ بالا آمدنِ سرور و هر شش ساعت (`app.js`).
 * @returns {Promise<number>} شمارِ پمپ‌هایی که کد گرفتند
 */
async function sweep() {
  const r = await query(
    `SELECT id FROM stations WHERE access_code = '' OR access_code !~ '^[0-9]{8}$'`);
  let n = 0;
  for (const row of (r.rows || [])) {
    try { await assign(row.id, { keepOld: true }); n++; }
    catch (err) { console.error('[access-sweep]', row.id, err.message); }
  }
  if (n > 0) console.log(`[access-sweep] ${n} پمپ کدِ هشت‌رقمی گرفت`);
  return n;
}

/**
 * پمپی که این کد مالِ اوست — یا ‎null‎.
 *
 * ⚠️ پمپِ غیرفعال هم ‎null‎ است: کدِ پمپی که بسته شده نباید دری باز کند.
 */
async function byCode(raw) {
  const code = normalize(raw);
  if (!isValid(code)) return null;
  //  ⛔ کدِ حرفیِ پیشین هم در را باز می‌کند — گوشیِ وصل‌شده بیرون نمی‌افتد
  const st = await one(
    `SELECT * FROM stations WHERE (access_code=$1 OR access_code_old=$1) AND status='active'`, [code]);
  return st || null;
}

/** همان ‎normalize‎ ولی با خطای فارسی برای ورودیِ بدشکل. */
function parse(raw) {
  const code = normalize(raw);
  if (!isValid(code)) throw badRequest('کدِ پمپ هشت رقم است، مثلِ 4829-1736', 'bad_access_code');
  return code;
}

module.exports = { ensure, rotate, sweep, byCode, parse, normalize, format, isValid, isDigits, ALPHABET, LENGTH, _query: query };
