#!/usr/bin/env node
'use strict';
/**
 * بازگرداندن پشتیبان:
 *   node scripts/restore.js <فایل> [DATABASE_URL مقصد]
 *
 * دیتابیس مقصد باید خالی باشد (یا جدول‌های هم‌نام نداشته باشد).
 * برای انتقال سرور: روی سرور تازه یک دیتابیس خالی بسازید و همین را بزنید.
 */
const backup = require('../src/lib/backup');
const { closeDb } = require('../src/db');

const [file, target] = process.argv.slice(2);
if (!file) {
  console.error('کاربرد: node scripts/restore.js <فایل پشتیبان> [DATABASE_URL مقصد]');
  process.exit(1);
}

backup.restore(file, target ? { databaseUrl: target } : {})
  .then(async (out) => {
    console.log(`بازیابی انجام شد (${Math.round(out.bytes / 1024)} کیلوبایت SQL).`);
    await closeDb();
  })
  .catch(async (e) => { console.error('✖', e.message); await closeDb(); process.exit(1); });
