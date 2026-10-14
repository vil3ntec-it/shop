'use strict';
/**
 * مدیرِ سامانه از محیط — برای نصبی که هیچ‌کس پشتِ ترمینال نیست.
 *
 * تا امروز تنها راهِ ساختنِ مدیر `node scripts/create-admin.js` بود. روی
 * کامپیوترِ خانگی، پنلِ سرورِ خانگی این سرور را خودش بالا می‌آورد و کسی
 * ترمینال باز نمی‌کند؛ پس نام و رمزِ مدیر را با محیط می‌دهد و این‌جا سرِ
 * هر راه‌اندازی سنجیده می‌شود:
 *
 *   ADMIN_BOOTSTRAP_USER      نامِ کاربری
 *   ADMIN_BOOTSTRAP_PASSWORD  رمز (دستِ‌کم ۸ نویسه، نه همه‌رقم)
 *
 * ⚠️ نبود ⇒ همان مدیرِ همیشگی ساخته می‌شود؛ بود ولی رمز فرق داشت ⇒ رمز به
 * همان که در محیط است برمی‌گردد. یعنی محیط مرجع است: کسی که آن دو مقدار را
 * در دست دارد، صاحبِ همین کامپیوتر است. رمز هیچ‌جا لاگ نمی‌شود.
 *
 * ⛔ F0-15 — راهِ نجات: رمزِ تازه از محیط ⇒ تأییدِ دومرحله‌ایِ همان مدیر هم
 * خاموش می‌شود (گوشی و کدهای بازیابی گم شده‌اند). همان رمز ⇒ دست نمی‌خورد.
 */
const { one, newId, now } = require('../db');
const pw = require('./password');

async function ensureAdmin({ username, password, log = () => {} } = {}) {
  const uname = String(username || '').trim().toLowerCase();
  if (!uname || !password) return null;
  const weak = pw.checkStrength(password);
  if (weak) throw new Error(`ADMIN_BOOTSTRAP_PASSWORD: ${weak}`);

  const found = await one('SELECT id, password_hash, status, totp_enabled_at FROM admins WHERE username=$1', [uname]);
  if (!found) {
    const row = await one(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
       VALUES ($1,$2,$3,$4,'superadmin','active',$5) RETURNING id`,
      [newId('adm'), uname, uname, await pw.hashPassword(password), now()]
    );
    log(`مدیرِ «${uname}» از محیط ساخته شد`);
    return { id: row.id, created: true };
  }
  const same = await pw.verifyPassword(password, found.password_hash);
  if (!same || found.status !== 'active') {
    await one(
      `UPDATE admins SET password_hash=$2, status='active' WHERE id=$1 RETURNING id`,
      [found.id, await pw.hashPassword(password)]
    );
    //  رمز عوض شد ⇒ نشست‌هایی که با رمزِ پیشین باز شده‌اند هم بسته شوند
    if (!same) await require('./tokens').revokeAllForSubject(found.id);
    if (!same && found.totp_enabled_at) {
      await one(
        `UPDATE admins SET totp_secret='', totp_pending='', totp_enabled_at=NULL, totp_last_step=0, totp_recovery='[]'
         WHERE id=$1 RETURNING id`, [found.id]
      );
      await require('./audit').log({ actorType: 'system', userId: found.id, action: 'admin.2fa_reset_by_bootstrap' }).catch(() => {});
      log(`تأییدِ دومرحله‌ایِ «${uname}» با رمزِ تازهٔ محیط خاموش شد (راهِ نجات)`);
    }
    log(`رمزِ مدیرِ «${uname}» با محیط یکی شد`);
    return { id: found.id, created: false, updated: true };
  }
  return { id: found.id, created: false, updated: false };
}

module.exports = { ensureAdmin };
