'use strict';
/**
 * مرحلهٔ دومِ ورودِ مدیر (F0-15) — کدِ TOTP یا یک کدِ بازیابیِ یک‌بارمصرف.
 *
 * ⛔ هر دو «مصرف» اتمی‌اند: گامِ TOTP فقط اگر از آخرین گامِ پذیرفته جلوتر
 *    باشد می‌نشیند و کدِ بازیابی فقط اگر فهرست هم‌زمان عوض نشده باشد پاک
 *    می‌شود. پس دو درخواستِ هم‌زمان با یک کد، یکی‌شان رد می‌شود.
 */
const { one } = require('../db');
const totp = require('./totp');

function recoveryList(admin) {
  try {
    const list = JSON.parse(admin.totp_recovery || '[]');
    return Array.isArray(list) ? list : [];
  } catch { return []; }
}

function recoveryLeft(admin) { return admin.totp_enabled_at ? recoveryList(admin).length : 0; }

/**
 * @returns {Promise<'totp'|'recovery'|null>}
 */
async function checkSecondFactor(admin, { code, recovery } = {}) {
  if (!admin || !admin.totp_enabled_at || !admin.totp_secret) return null;
  const c = String(code || '').trim();
  if (c) {
    const step = totp.verify(admin.totp_secret, c, { afterStep: Number(admin.totp_last_step || 0) });
    if (step === null) return null;
    const row = await one(
      'UPDATE admins SET totp_last_step=$2 WHERE id=$1 AND totp_last_step < $2 RETURNING id',
      [admin.id, step]
    );
    return row ? 'totp' : null;
  }
  const r = totp.normalizeRecovery(recovery);
  if (r.length !== 10) return null;
  const list = recoveryList(admin);
  const hash = totp.hashRecovery(r);
  if (!list.includes(hash)) return null;
  const row = await one(
    'UPDATE admins SET totp_recovery=$2 WHERE id=$1 AND totp_recovery=$3 RETURNING id',
    [admin.id, JSON.stringify(list.filter((x) => x !== hash)), admin.totp_recovery]
  );
  return row ? 'recovery' : null;
}

module.exports = { checkSecondFactor, recoveryLeft };
