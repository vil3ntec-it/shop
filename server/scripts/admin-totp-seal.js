#!/usr/bin/env node
'use strict';
/**
 * C-01 — رازِ TOTP ِ مدیرها در دیتابیس: رمز کردن یا (فقط برای برگشت به نسخهٔ پیشین) باز کردن.
 *   node scripts/admin-totp-seal.js seal     ⇒ هر رازِ خامِ مانده رمز می‌شود (همان کارِ هر راه‌اندازی)
 *   node scripts/admin-totp-seal.js unseal   ⇒ پیش از برگشت به نسخه‌ای که پاکت را نمی‌شناسد
 * همان `API_SECRET` ِ سرور لازم است. ⛔ هیچ راز یا پاکتی چاپ نمی‌شود — فقط شمار.
 */
const { closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const seal = require('../src/lib/admin-totp-seal');

async function main() {
  const cmd = process.argv[2];
  if (cmd !== 'seal' && cmd !== 'unseal') {
    console.error('کاربرد: node scripts/admin-totp-seal.js seal|unseal');
    process.exit(1);
  }
  await migrate.run();
  const n = cmd === 'seal' ? await seal.sealLegacy() : await seal.unsealAll();
  console.log(cmd === 'seal' ? `رازِ ${n} مدیر رمز شد` : `رازِ ${n} مدیر برای نسخهٔ پیشین باز شد`);
}

main().then(() => closeDb()).catch(async (err) => {
  console.error(err.message);
  await closeDb().catch(() => {});
  process.exit(1);
});
