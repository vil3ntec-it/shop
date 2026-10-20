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
    await closeDb();
  })
  .catch(async (e) => { console.error('✖', e.message); await closeDb(); process.exit(1); });
