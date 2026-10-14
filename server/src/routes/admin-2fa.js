'use strict';
/**
 * تأییدِ دومرحله‌ایِ مدیرِ سرور (F0-15) — `/api/admin/2fa/*`
 *
 * ⚠️ **اختیاری است.** هر مدیر خودش روشنش می‌کند؛ اجباری شدن فقط با
 * تصمیمِ صاحب محصول. تا روشن نشده، ورود دقیقاً همان است که بود.
 *
 *   GET    /2fa                 حال: روشن؟ چند کدِ بازیابی مانده؟
 *   POST   /2fa/setup           { password }           ⇒ راز + نشانیِ otpauth
 *   POST   /2fa/enable          { code }               ⇒ روشن + ده کدِ بازیابی (فقط همین یک بار)
 *   POST   /2fa/recovery-codes  { password, code }     ⇒ ده کدِ تازه، کهنه‌ها باطل
 *   POST   /2fa/disable         { password, code | recovery_code }
 *
 * ⛔ رمزِ نادرست این‌جا ۴۰۳ است نه ۴۰۱ — ۴۰۱ نشستِ پنل را می‌بندد.
 * ⛔ راهِ نجات: `ADMIN_BOOTSTRAP_PASSWORD`ِ تازه + راه‌اندازیِ دوباره ⇒
 *    رمز به محیط برمی‌گردد و تأییدِ دومرحله‌ای خاموش می‌شود
 *    (‎lib/admin-bootstrap.js‎).
 */
const express = require('express');
const config = require('../config');
const { one, now } = require('../db');
const pw = require('../lib/password');
const audit = require('../lib/audit');
const totp = require('../lib/totp');
const { checkSecondFactor, recoveryLeft } = require('../lib/admin-2fa');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, conflict } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.authMax, keyPrefix: 'admin-2fa' }));

async function fresh(id) { return one('SELECT * FROM admins WHERE id=$1', [id]); }

async function needPassword(admin, password) {
  const ok = typeof password === 'string' && password && await pw.verifyPassword(password, admin.password_hash);
  if (!ok) throw forbidden('رمزِ کنونی درست نیست', 'bad_password');
}

function state(admin) {
  return {
    enabled: !!admin.totp_enabled_at,
    enabled_at: admin.totp_enabled_at ? Number(admin.totp_enabled_at) : null,
    recovery_left: recoveryLeft(admin),
    //  اجباری نیست (تصمیمِ صاحب محصول) — پنل فقط پیشنهاد می‌دهد
    enforced: false,
  };
}

router.get('/', async (req, res) => { res.json(state(await fresh(req.admin.id))); });

router.post('/setup', async (req, res, next) => {
  try {
    const admin = await fresh(req.admin.id);
    await needPassword(admin, req.body?.password);
    if (admin.totp_enabled_at) throw conflict('تأییدِ دومرحله‌ای همین حالا روشن است', 'totp_already_enabled');
    const secret = totp.newSecret();
    await one('UPDATE admins SET totp_pending=$2 WHERE id=$1 RETURNING id', [admin.id, secret]);
    res.set('Cache-Control', 'no-store');
    res.json({ secret, otpauth_url: totp.otpauthUrl(secret, admin.username) });
  } catch (err) { next(err); }
});

router.post('/enable', async (req, res, next) => {
  try {
    const admin = await fresh(req.admin.id);
    if (admin.totp_enabled_at) throw conflict('تأییدِ دومرحله‌ای همین حالا روشن است', 'totp_already_enabled');
    if (!admin.totp_pending) throw badRequest('اول «روشن کردن» را بزنید', 'totp_not_started');
    const step = totp.verify(admin.totp_pending, req.body?.code);
    if (step === null) throw badRequest('کدِ برنامهٔ تأیید درست نیست؛ ساعتِ گوشی را هم ببینید', 'totp_invalid');
    const codes = totp.newRecoveryCodes();
    const row = await one(
      `UPDATE admins SET totp_secret=totp_pending, totp_pending='', totp_enabled_at=$2,
         totp_last_step=$3, totp_recovery=$4
       WHERE id=$1 AND totp_pending=$5 AND totp_enabled_at IS NULL RETURNING id`,
      [admin.id, now(), step, JSON.stringify(codes.map(totp.hashRecovery)), admin.totp_pending]
    );
    if (!row) throw conflict('هم‌زمان عوض شد؛ دوباره امتحان کنید', 'totp_race');
    await audit.log({ actorType: 'admin', userId: admin.id, action: 'admin.2fa_enabled', ip: clientIp(req) });
    res.set('Cache-Control', 'no-store');
    res.json({ ...state(await fresh(admin.id)), recovery_codes: codes });
  } catch (err) { next(err); }
});

router.post('/recovery-codes', async (req, res, next) => {
  try {
    const admin = await fresh(req.admin.id);
    if (!admin.totp_enabled_at) throw badRequest('تأییدِ دومرحله‌ای روشن نیست', 'totp_not_enabled');
    await needPassword(admin, req.body?.password);
    if (!await checkSecondFactor(admin, { code: req.body?.code })) throw forbidden('کدِ برنامهٔ تأیید درست نیست', 'totp_invalid');
    const codes = totp.newRecoveryCodes();
    await one('UPDATE admins SET totp_recovery=$2 WHERE id=$1 RETURNING id', [admin.id, JSON.stringify(codes.map(totp.hashRecovery))]);
    await audit.log({ actorType: 'admin', userId: admin.id, action: 'admin.2fa_recovery_renewed', ip: clientIp(req) });
    res.set('Cache-Control', 'no-store');
    res.json({ ...state(await fresh(admin.id)), recovery_codes: codes });
  } catch (err) { next(err); }
});

router.post('/disable', async (req, res, next) => {
  try {
    const admin = await fresh(req.admin.id);
    if (!admin.totp_enabled_at) return res.json(state(admin));
    await needPassword(admin, req.body?.password);
    const how = await checkSecondFactor(admin, { code: req.body?.code, recovery: req.body?.recovery_code });
    if (!how) throw forbidden('کدِ تأیید یا کدِ بازیابی درست نیست', 'totp_invalid');
    await one(
      `UPDATE admins SET totp_secret='', totp_pending='', totp_enabled_at=NULL, totp_last_step=0, totp_recovery='[]'
       WHERE id=$1 RETURNING id`, [admin.id]
    );
    await audit.log({ actorType: 'admin', userId: admin.id, action: 'admin.2fa_disabled', ip: clientIp(req), detail: { via: how } });
    res.json(state(await fresh(admin.id)));
  } catch (err) { next(err); }
});

module.exports = router;
