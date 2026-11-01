'use strict';
/**
 * «مالکِ اصلی» ِ سرورِ حساب — مدیرِ ارشدی که هیچ مدیرِ دیگری (حتی ارشد) نمی‌تواند غیرفعال یا تنزلش
 * دهد (یافتهٔ S12 ِ ممیزیِ پنلِ مرکزی؛ قاعدهٔ «مالک هرگز بیرون نمی‌ماند»).
 *
 * کیست: ستونِ `admins.is_owner` (مهاجرتِ ۱۶۸). اگر هنوز کسی نشان نخورده (پایگاهِ تازه)، پیشین‌ترین
 * مدیرِ ارشد — همان حسابِ آغازینِ سرور (`ADMIN_BOOTSTRAP_*` یا `scripts/create-admin.js`) — نشان
 * می‌خورد. در هر لحظه فقط یکی (`ux_admins_owner`).
 *
 * راهِ بازیابیِ مالک (رمز یا TOTP گم شده) فقط از پوستهٔ همان سرور است، نه از هیچ API ای:
 *   • `ADMIN_BOOTSTRAP_PASSWORD` ِ تازه در محیط ⇒ رمز برمی‌گردد، حساب فعال و TOTP خاموش می‌شود؛
 *   • یا `node scripts/create-admin.js <نامِ مالک> <رمزِ تازه>` ⇒ همان، و نقشِ مالک ارشد می‌ماند.
 */
const { one } = require('../db');

/** شناسهٔ مالک (و اگر نیست، پیشین‌ترین ارشد را مالک کن). */
async function ownerId() {
  const r = await one('SELECT id FROM admins WHERE is_owner LIMIT 1');
  if (r) return r.id;
  try {
    const made = await one(
      `UPDATE admins SET is_owner = true
        WHERE id = (SELECT id FROM admins WHERE role='superadmin' ORDER BY created_at ASC, id ASC LIMIT 1)
          AND NOT EXISTS (SELECT 1 FROM admins WHERE is_owner)
        RETURNING id`
    );
    if (made) return made.id;
  } catch { /* دو نوشتنِ هم‌زمان: ایندکسِ یکتا یکی را نگه داشت */ }
  const again = await one('SELECT id FROM admins WHERE is_owner LIMIT 1');
  return again ? again.id : null;
}

module.exports = { ownerId };
