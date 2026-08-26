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
  const exists = await one('SELECT id FROM admins WHERE username=$1', [uname]);
  if (exists) {
    await one('UPDATE admins SET password_hash=$2, role=$3 WHERE id=$1 RETURNING id',
      [exists.id, await pw.hashPassword(password), role]);
    console.log(`رمز مدیر «${uname}» عوض شد.`);
  } else {
    await one(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
       VALUES ($1,$2,$3,$4,$5,'active',$6) RETURNING id`,
      [newId('adm'), uname, name || uname, await pw.hashPassword(password), role, now()]
    );
    console.log(`مدیر «${uname}» ساخته شد (${role}).`);
  }
  if (!passwordArg) console.log(`رمز: ${password}`);
  await closeDb();
}

main().catch(async (e) => { console.error(e.message); await closeDb(); process.exit(1); });
