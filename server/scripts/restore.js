#!/usr/bin/env node
'use strict';
/**
 * بازگرداندن پشتیبان:
 *   node scripts/restore.js <فایل> [DATABASE_URL مقصد]
 *
 * دیتابیس مقصد باید خالی باشد (یا جدول‌های هم‌نام نداشته باشد).
 * برای انتقال سرور: روی سرور تازه یک دیتابیس خالی بسازید و همین را بزنید.
 *
 * §۹.۵: پس از بازگرداندن، دورانِ بازیابیِ همگام‌سازی در **همان** دیتابیس تازه می‌شود
 * (`lib/sync-epoch.js`) تا برنامه‌ها opهای پس از پشتیبان را دوباره بفرستند — پس نشانیِ مقصد
 * پیش از بارِ پیکربندی و دیتابیس در DATABASE_URL می‌نشیند.
 */
const [file, target] = process.argv.slice(2);
if (!file) {
  console.error('کاربرد: node scripts/restore.js <فایل پشتیبان> [DATABASE_URL مقصد]');
  process.exit(1);
}
if (target) process.env.DATABASE_URL = target;

const backup = require('../src/lib/backup');
const { closeDb } = require('../src/db');

backup.restore(file)
  .then(async (out) => {
    console.log(`بازیابی انجام شد (${Math.round(out.bytes / 1024)} کیلوبایت SQL).`);
    //  F0-28: پشتیبانِ مهرشده — فقط نام، شمار و kid؛ هرگز مقدار
    const sec = out.secrets;
    if (sec && sec.unsealed && !sec.rejected) console.log(`${sec.unsealed} رازِ مهرشده با BACKUP_SECRETS_KEY باز شد${sec.conflicts?.length ? ` (روی‌نویسی‌نشده: ${sec.conflicts.join('، ')})` : ''}.`);
    if (sec && (sec.missingKey || sec.rejected || sec.error)) console.error(`⛔ ${sec.message || `پاکت‌های راز سنجیده نشدند (${sec.error})`}`);
    if (out.epochError) {
      //  دادهٔ پشتیبان نشسته است؛ فقط «دورانِ بازیابی» تازه نشد — جدا و روشن، با کدِ خروجِ جدا
      console.error(`⚠️ داده برگشت ولی دورانِ بازیابیِ همگام‌سازی تازه نشد: ${out.epochError}\n`
        + '   سرور را یک بار دوباره بالا بیاورید (نشانِ پشتیبان در دیتابیس هست)، یا با همین DATABASE_URL:\n'
        + '   node -e "require(\'./src/lib/sync-epoch\').rotate(\'restore\').then(() => process.exit(0))"\n'
        + '   تا آن وقت فروش‌های پس از پشتیبان روی برنامه‌ها می‌مانند و دوباره فرستاده نمی‌شوند.');
      await closeDb();
      process.exit(2);
    }
    await closeDb();
    //  داده برگشت ولی پاکتِ راز رد شد (کلیدِ دیگر یا دست‌کاری) ⇒ کدِ خروجِ جدا؛ بی کلید = ۰ (بازیابی موفق، پیامِ بالا)
    if (sec && (sec.rejected || sec.error)) process.exit(3);
  })
  .catch(async (e) => { console.error('✖', e.message); await closeDb(); process.exit(1); });
