'use strict';
/**
 * سخت‌کردنِ ورود و نشست (SEC-AUTH) — بخش‌هایی که از `routes/auth.js` و `routes/admin.js` جدا شد تا آن دو
 * زیرِ سقفِ اندازه بمانند (`tools/quality`)؛ رفتار همان. مسیرها همان‌جا ثبت می‌شوند، این‌جا فقط بدنه.
 */
const { query, one, now } = require('../db');
const pw = require('./password');
const reauth = require('./reauth');
const audit = require('./audit');
const { checkSecondFactor } = require('./admin-2fa');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

/**
 *  رمزِ ورودِ کاربر — حساب نبود؟ باز هم همان‌قدر وقت می‌گیرد (شرحش سرِ `pw.burnTime`).
 *  SEC-AUTH-01: هشِ کهنه ارزان‌تر است؛ رمزِ غلط روی آن کمتر از «حساب نیست» طول نکشد.
 */
async function checkPassword(user, password) {
  let ok = false;
  if (user && user.password_hash) ok = await pw.verifyPassword(password, user.password_hash);
  else await pw.burnTime(password);
  if (!ok && user && pw.needsRehash(user.password_hash)) await pw.burnTime(password);
  return ok;
}

/**
 *  SEC-AUTH-01: هشِ رمزی که از هزینهٔ امروز ارزان‌تر است، پس از ورودِ **درست** بازنویسی
 *  می‌شود — شرطی، تا عوض شدنِ هم‌زمانِ رمز را پاک نکند. شکستش ورود را نمی‌خواباند.
 *  `table` فقط یکی از دو نامِ ثابتِ پایین است (هرگز از درخواست).
 */
async function upgradeHash(table, row, plain) {
  try {
    const fresh = await pw.upgrade(plain, row.password_hash);
    if (!fresh) return false;
    const sql = table === 'admins'
      ? 'UPDATE admins SET password_hash=$2 WHERE id=$1 AND password_hash=$3'
      : 'UPDATE users SET password_hash=$2 WHERE id=$1 AND password_hash=$3';
    const r = await query(sql, [row.id, fresh, row.password_hash]);
    return (r.rowCount || 0) > 0;
  } catch (err) {
    console.warn(`[auth] بازنویسیِ هشِ رمز نشد: ${err.message}`);
    return false;
  }
}

/**
 *  SEC-AUTH-13 — `POST /api/admin/reauth`، تأییدِ دوبارهٔ نشستِ مدیر: رمز (+ کدِ TOTP یا کدِ بازیابی
 *  اگر روشن است). همان قفلِ «نام + IP»ِ ورود؛ شکست ۴۰۳ است نه ۴۰۱ (۴۰۱ نشستِ پنل را می‌بندد).
 *  پشتِ `requireAdmin` و `adminLimit` ِ `routes/admin.js` سوار است.
 */
async function adminReauth(req, res, next) {
  const admin = await one('SELECT * FROM admins WHERE id=$1', [req.admin.id]);
  const ip = clientIp(req);
  //  دیر خوانده می‌شود: `routes/auth.js` خودش همین پرونده را می‌خواند
  await require('../routes/auth').assertNotLocked('admin', admin.username, ip);
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  const passOk = !!password && await pw.verifyPassword(password, admin.password_hash);
  const totpCode = typeof req.body?.totp === 'string' ? req.body.totp.trim() : '';
  const recoveryCode = typeof req.body?.recovery_code === 'string' ? req.body.recovery_code.trim() : '';
  const twoStep = !!admin.totp_enabled_at;
  if (passOk && twoStep && !totpCode && !recoveryCode) {
    return next(forbidden('کدِ برنامهٔ تأیید (یا یک کدِ بازیابی) را بنویسید', 'totp_required'));
  }
  const second = passOk && twoStep ? await checkSecondFactor(admin, { code: totpCode, recovery: recoveryCode }) : null;
  const ok = passOk && (!twoStep || !!second);
  await query(
    `INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ('admin',$1,$2,$3,$4)`,
    [admin.username, ip, ok, now()]
  );
  if (!passOk) return next(forbidden('رمزِ کنونی درست نیست', 'bad_password'));
  if (!ok) return next(forbidden('کدِ تأیید درست نیست', 'totp_invalid'));
  await reauth.markFresh({ tokenHash: req.tokenRow.token_hash, ip });
  await audit.log({ actorType: 'admin', userId: admin.id, action: 'admin.reauth', ip, ...(second ? { detail: { second_factor: second } } : {}) });
  res.json({ ok: true, freshUntil: await reauth.freshUntil(req.tokenRow.token_hash) });
}

module.exports = { checkPassword, upgradeHash, adminReauth };
