#!/usr/bin/env node
'use strict';
/**
 * ساخت مدیر سامانه.
 *   node scripts/create-admin.js <username> <password> [نام] [admin|superadmin]
 * اگر رمز داده نشود، یک رمز قوی ساخته و چاپ می‌شود.
 */
const { randomBytes } = require('crypto');
const { one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

async function main() {
  const [username, passwordArg, name, roleArg] = process.argv.slice(2);
  if (!username) {
    console.error('کاربرد: node scripts/create-admin.js <username> [password] [name] [admin|superadmin]');
    process.exit(1);
  }
  await migrate.run();

  const password = passwordArg || randomBytes(12).toString('base64url');
  const weak = pw.checkStrength(password);
  if (weak) { console.error(weak); process.exit(1); }

  const role = roleArg === 'superadmin' ? 'superadmin' : 'admin';
  const uname = String(username).toLowerCase();
  const exists = await one('SELECT id, is_owner FROM admins WHERE username=$1', [uname]);
  if (exists) {
    //  F0-15: راهِ نجات از ترمینال ⇒ تأییدِ دومرحله‌ای هم خاموش (گوشی گم شده)
    //  ⛔ فاز ۳: این راهِ بازیابیِ **مالکِ اصلی** هم هست — مالک همیشه ارشد و فعال می‌ماند (بی آرگومانِ
    //  نقش هم تنزل نمی‌خورد) و نشست‌های پیشینش بسته می‌شوند.
    const keep = exists.is_owner === true ? 'superadmin' : role;
    await one(
      `UPDATE admins SET password_hash=$2, role=$3, status = CASE WHEN $4 THEN 'active' ELSE status END,
         totp_secret='', totp_pending='', totp_enabled_at=NULL, totp_last_step=0, totp_recovery='[]'
       WHERE id=$1 RETURNING id`,
      [exists.id, await pw.hashPassword(password), keep, exists.is_owner === true]);
    await require('../src/lib/tokens').revokeAllForSubject(exists.id);
    console.log(`رمز مدیر «${uname}» عوض شد (تأییدِ دومرحله‌ای‌اش، اگر روشن بود، خاموش شد؛ نقش: ${keep}).`);
  } else {
    await one(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
       VALUES ($1,$2,$3,$4,$5,'active',$6) RETURNING id`,
      [newId('adm'), uname, name || uname, await pw.hashPassword(password), role, now()]
    );
    console.log(`مدیر «${uname}» ساخته شد (${role}).`);
  }
  await require('../src/lib/admin-owner').ownerId();
  if (!passwordArg) console.log(`رمز: ${password}`);
  await closeDb();
}

main().catch(async (e) => { console.error(e.message); await closeDb(); process.exit(1); });
