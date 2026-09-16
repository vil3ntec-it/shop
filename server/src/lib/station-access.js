'use strict';
/**
 * ══ کدِ دسترسیِ پمپ — کلیدِ اپِ کارمندان ══════════════════════════════════
 *
 * خواستهٔ صاحب مخزن: «برای هر پمپ یک کد باشد که هر کسی برنامه را نصب
 * می‌کند همان را بزند و فقط حساب‌های همان پمپ را ببیند.»
 *
 *     برنامهٔ کامپیوتر ──GET  /pump/device/access-code──▶  {code: 'K7PM-3XQ2'}
 *     گوشیِ کارمند    ──POST /pump/public/join {code}──▶  {station, home:{url, readKey}}
 *
 * ── قاعده‌ها ─────────────────────────────────────────────────────────────
 *   • یک پمپ، یک کد. تا صاحبش عوض نکند همان می‌ماند — کارمندِ تازه هم با
 *     همان کد می‌آید. عوض کردن، همان لحظه کدِ قبلی را بی‌اثر می‌کند.
 *   • کد فقط به **همان یک پمپ** می‌رسد. جوابِ ‎join‎ چیزی جز نشانی و رمزِ
 *     فقط‌خواندنیِ آن پمپ ندارد؛ نه فهرستِ پمپ‌ها، نه شناسهٔ داخلی.
 *   • حروفِ شبیه به هم (‎0/O‎، ‎1/I‎) در الفبا نیستند تا کدِ دست‌نویس روی
 *     کاغذ اشتباه خوانده نشود. ورودی بی‌توجه به بزرگی/کوچکی و خطِ تیره
 *     پذیرفته می‌شود.
 */
const { randomInt } = require('crypto');
const { one, query, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const LENGTH = 8;

/** ورودیِ کاربر ⇒ شکلِ ذخیره‌شده. ‎'k7pm-3xq2'‎ ⇒ ‎'K7PM3XQ2'‎. */
function normalize(raw) {
  //  فقط بزرگ کردن و برداشتنِ خطِ تیره/فاصله. ‎0/1/O/I‎ در الفبا نیستند،
  //  پس اگر کسی زد، ‎isValid‎ همان‌جا رد می‌کند — کدِ درست چنین حرفی ندارد.
  return String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

function isValid(code) {
  if (code.length !== LENGTH) return false;
  for (const ch of code) if (!ALPHABET.includes(ch)) return false;
  return true;
}

/** برای نمایش: ‎K7PM-3XQ2‎. */
function format(code) {
  const c = String(code || '');
  return c.length === LENGTH ? c.slice(0, 4) + '-' + c.slice(4) : c;
}

function random() {
  let s = '';
  for (let i = 0; i < LENGTH; i++) s += ALPHABET[randomInt(ALPHABET.length)];
  return s;
}

/** کدِ این پمپ؛ اگر هنوز ندارد، همین حالا ساخته می‌شود. */
async function ensure(stationId) {
  const st = await one('SELECT access_code FROM stations WHERE id=$1', [stationId]);
  if (st && st.access_code) return st.access_code;
  return rotate(stationId);
}

/** کدِ تازه — کدِ قبلی همان لحظه از کار می‌افتد. */
async function rotate(stationId) {
  for (let attempt = 0; attempt < 12; attempt++) {
    const code = random();
    if (await one('SELECT 1 FROM stations WHERE access_code=$1', [code])) continue;
    const r = await one(
      `UPDATE stations SET access_code=$2, updated_at=$3 WHERE id=$1 RETURNING access_code`,
      [stationId, code, now()]
    );
    if (r) return r.access_code;
    break;
  }
  throw conflict('ساختِ کد ممکن نشد، دوباره تلاش کنید', 'access_code_failed');
}

/**
 * پمپی که این کد مالِ اوست — یا ‎null‎.
 *
 * ⚠️ پمپِ غیرفعال هم ‎null‎ است: کدِ پمپی که بسته شده نباید دری باز کند.
 */
async function byCode(raw) {
  const code = normalize(raw);
  if (!isValid(code)) return null;
  const st = await one(`SELECT * FROM stations WHERE access_code=$1 AND status='active'`, [code]);
  return st || null;
}

/** همان ‎normalize‎ ولی با خطای فارسی برای ورودیِ بدشکل. */
function parse(raw) {
  const code = normalize(raw);
  if (!isValid(code)) throw badRequest('کدِ پمپ هشت حرف و رقم است، مثلِ K7PM-3XQ2', 'bad_access_code');
  return code;
}

module.exports = { ensure, rotate, byCode, parse, normalize, format, isValid, ALPHABET, LENGTH, _query: query };
