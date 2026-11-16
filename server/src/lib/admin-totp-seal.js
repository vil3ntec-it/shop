'use strict';
/**
 * C-01 (ممیزیِ رازهای در حالِ سکون، ۱۴۰۵/۰۸/۲۶) — رازِ TOTP ِ مدیرِ سرور در دیتابیس **رمزشده** است.
 *
 * تا پیش از این `admins.totp_secret` و `admins.totp_pending` همان base32 ِ خام بودند: هر کس فقط دیتابیس (یا پشتیبانش) را
 * داشت، کدِ شش‌رقمیِ هر مدیر را می‌ساخت و مرحلهٔ دوم عملاً حذف می‌شد. راز برای سنجیدنِ کد لازم است (هش‌شدنی نیست)، پس رمز
 * می‌شود: AES-256-GCM، کلید با HKDF از رازِ سرور (`API_SECRET`، همان ترتیبِ `pepper` ِ کدهای دیگر) — کلید هرگز در دیتابیس یا
 * پشتیبان نیست. AAD = نسخه + شناسهٔ مدیر ⇒ پاکتِ یک مدیر روی ردیفِ مدیرِ دیگر باز نمی‌شود.
 *
 *   پاکت: `ats1.<iv>.<بدنه>.<برچسب>` (base64url)
 *
 * ── پنجرهٔ مهاجرت ─────────────────────────────────────────────────────────────────────
 * کلید فقط در محیط است، پس مهاجرتِ SQL نمی‌تواند ردیف‌های کهنه را رمز کند. `sealLegacy` در هر راه‌اندازی (پیش از نخستین
 * درخواست، `app.js`) هر رازِ خامِ مانده را با CAS رمز می‌کند؛ تا آن لحظه `openSecret` خامِ کهنه را می‌پذیرد (فقط شکلِ base32،
 * نه هر رشته) تا هیچ مدیری بیرون نماند. پاکتِ خراب/کلیدِ دیگر ⇒ `null` ⇒ هیچ کدی پذیرفته نیست (بسته، نه باز) — راهِ نجات
 * همان کدِ بازیابی یا `ADMIN_BOOTSTRAP_PASSWORD` است.
 *
 * ── برگشت ──────────────────────────────────────────────────────────────────────────────
 * نسخهٔ پیشین پاکت را نمی‌خواند؛ پیش از برگشت به آن: `node scripts/admin-totp-seal.js unseal` (همان `unsealAll`).
 *
 * ⛔ راز، پاکت یا کلید هرگز در لاگ، خطا یا سابقه نیست.
 */
const crypto = require('crypto');
const { serverSecret } = require('./server-secret');

const PREFIX = 'ats1.';
const BASE32 = /^[A-Z2-7]+=*$/;

function key() {
  const secret = serverSecret(['api', 'otp', 'jwt'], 'shop-admin-totp');
  return Buffer.from(crypto.hkdfSync('sha256', Buffer.from(secret, 'utf8'), Buffer.from('shop-admin-totp:salt:v1'),
    Buffer.from('admin-totp-seal:v1'), 32));
}
const aad = (adminId) => Buffer.from(`admin-totp:v1:${String(adminId)}`, 'utf8');

const isSealed = (stored) => typeof stored === 'string' && stored.startsWith(PREFIX);

/** رازِ base32 ⇒ پاکت (خالی ⇒ خالی) */
function sealSecret(adminId, plain) {
  if (!plain) return '';
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', key(), iv, { authTagLength: 16 });
  c.setAAD(aad(adminId));
  const body = Buffer.concat([c.update(String(plain), 'utf8'), c.final()]);
  return `${PREFIX}${iv.toString('base64url')}.${body.toString('base64url')}.${c.getAuthTag().toString('base64url')}`;
}

/**
 * پاکت ⇒ رازِ base32. خالی ⇒ ''؛ خامِ کهنهٔ base32 (پنجرهٔ مهاجرت) ⇒ همان؛ پاکتِ خراب، مدیرِ دیگر یا کلیدِ دیگر ⇒ null.
 */
function openSecret(adminId, stored) {
  if (!stored) return '';
  if (!isSealed(stored)) return BASE32.test(stored) ? stored : null;
  const parts = stored.slice(PREFIX.length).split('.');
  if (parts.length !== 3) return null;
  try {
    const d = crypto.createDecipheriv('aes-256-gcm', key(), Buffer.from(parts[0], 'base64url'), { authTagLength: 16 });
    d.setAAD(aad(adminId));
    d.setAuthTag(Buffer.from(parts[2], 'base64url'));
    const out = Buffer.concat([d.update(Buffer.from(parts[1], 'base64url')), d.final()]).toString('utf8');
    return BASE32.test(out) ? out : null;
  } catch { return null; }
}

/** هر ستونِ TOTP ِ خامِ مانده ⇒ پاکت (CAS روی مقدارِ خوانده‌شده). @returns شمارِ ردیف‌های رمزشده */
async function sealLegacy() {
  const { query } = require('../db');
  const rows = (await query(
    `SELECT id, totp_secret, totp_pending FROM admins
      WHERE (totp_secret <> '' AND totp_secret NOT LIKE 'ats1.%') OR (totp_pending <> '' AND totp_pending NOT LIKE 'ats1.%')`
  )).rows;
  let n = 0;
  for (const r of rows) {
    const next = (v) => (v && !isSealed(v) ? sealSecret(r.id, v) : v);
    const res = await query(
      'UPDATE admins SET totp_secret=$2, totp_pending=$3 WHERE id=$1 AND totp_secret=$4 AND totp_pending=$5',
      [r.id, next(r.totp_secret), next(r.totp_pending), r.totp_secret, r.totp_pending]
    );
    n += res.rowCount || 0;
  }
  return n;
}

/** برگشت به نسخهٔ پیشین: هر پاکت ⇒ خام. پاکتی که باز نشد دست نمی‌خورد. @returns شمارِ ردیف‌های بازشده */
async function unsealAll() {
  const { query } = require('../db');
  const rows = (await query(
    `SELECT id, totp_secret, totp_pending FROM admins WHERE totp_secret LIKE 'ats1.%' OR totp_pending LIKE 'ats1.%'`
  )).rows;
  let n = 0;
  for (const r of rows) {
    const back = (v) => (isSealed(v) ? openSecret(r.id, v) : v);
    const s = back(r.totp_secret);
    const p = back(r.totp_pending);
    if (s === null || p === null) continue;
    const res = await query(
      'UPDATE admins SET totp_secret=$2, totp_pending=$3 WHERE id=$1 AND totp_secret=$4 AND totp_pending=$5',
      [r.id, s, p, r.totp_secret, r.totp_pending]
    );
    n += res.rowCount || 0;
  }
  return n;
}

module.exports = { PREFIX, isSealed, sealSecret, openSecret, sealLegacy, unsealAll };
