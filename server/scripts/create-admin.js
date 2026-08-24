#!/usr/bin/env node
'use strict';
/**
 * ساخت حساب مدیر.
 *
 * رمز عبور هرگز در کد یا آرگومان دیده نمی‌شود؛ یا از متغیر محیطی
 * ADMIN_PASSWORD خوانده می‌شود یا به صورت تعاملی پرسیده می‌شود.
 *
 *   node scripts/create-admin.js <username> [--name "نام"] [--role superadmin]
 *   ADMIN_PASSWORD='...' node scripts/create-admin.js admin
 */
const readline = require('readline');
const { getDb, newId, now } = require('../src/db');
const pw = require('../src/lib/password');

function arg(flag, dflt) {
  const i = process.argv.indexOf(flag);
  return i > -1 && process.argv[i + 1] ? process.argv[i + 1] : dflt;
}

function ask(question) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(question, (a) => { rl.close(); resolve(a); });
  });
}

(async () => {
  const username = (process.argv[2] || '').trim().toLowerCase();
  if (!username || username.startsWith('--')) {
    console.error('استفاده: node scripts/create-admin.js <username> [--name "نام"] [--role superadmin]');
    process.exit(1);
  }
  if (!/^[a-z0-9._-]{3,40}$/.test(username)) {
    console.error('نام کاربری فقط حروف کوچک انگلیسی، عدد، نقطه، خط تیره و زیرخط (۳ تا ۴۰ کاراکتر)');
    process.exit(1);
  }

  const db = getDb();
  if (db.prepare('SELECT id FROM admins WHERE username=?').get(username)) {
    console.error('این نام کاربری قبلاً ثبت شده است.');
    process.exit(1);
  }

  const password = process.env.ADMIN_PASSWORD || await ask('رمز عبور مدیر: ');
  const weak = pw.checkStrength(password);
  if (weak) { console.error(weak); process.exit(1); }

  const role = arg('--role', 'admin');
  if (!['admin', 'superadmin'].includes(role)) {
    console.error('نقش نامعتبر است (admin یا superadmin)');
    process.exit(1);
  }

  db.prepare(`INSERT INTO admins (id,username,name,password_hash,role,status,created_at)
              VALUES (?,?,?,?,?,'active',?)`)
    .run(newId('adm'), username, arg('--name', username), await pw.hashPassword(password), role, now());

  console.log(`مدیر «${username}» ساخته شد (نقش: ${role}).`);
  if (process.env.ADMIN_PASSWORD) {
    console.log('یادآوری: ADMIN_PASSWORD را از تاریخچه‌ی شل پاک کنید.');
  }
})();
