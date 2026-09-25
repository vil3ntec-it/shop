'use strict';
/**
 * اثرِ انگشتِ Migration بی‌اعتنا به پایانِ خط — و نگهبانش ضعیف نشد.
 *
 * ⛔ گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳، عکس): پس از به‌روز کردنِ سرورِ حساب از
 * مرکز فرمان به ۲.۱۰.۱، سرور با «فایل Migration «001_core.sql» بعد از اجرا
 * تغییر کرده است» هرگز بالا نمی‌آمد. دیتابیس را نسخهٔ داخلِ نصابِ ویندوز
 * ساخته بود — کلون‌شده روی رانرِ ویندوز، یعنی CRLF — و بستهٔ تازه روی لینوکس
 * ساخته می‌شود، یعنی LF. همان SQL، اثرِ انگشتِ دیگر.
 *
 * این‌جا همان حال واقعاً ساخته می‌شود: اثرِ انگشت‌های ثبت‌شده به شکلِ CRLF
 * بازنویسی و Migrationها دوباره اجرا می‌شوند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./helpers');
const db = require('../src/db');
const migrate = require('../src/migrate');

const sha = (s) => createHash('sha256').update(s).digest('hex').slice(0, 32);
const DIR = path.join(__dirname, '..', 'migrations');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('۱) خالص: LF، CRLF و خام یک Migrationاند؛ تغییرِ واقعی نه', () => {
  const lf = 'CREATE TABLE a (x int);\nCREATE TABLE b (y int);\n';
  const crlf = lf.replace(/\n/g, '\r\n');
  assert.ok(migrate.sameMigration(sha(lf), crlf), 'دیتابیسِ LF، فایلِ CRLF');
  assert.ok(migrate.sameMigration(sha(crlf), lf), 'دیتابیسِ CRLF (نصابِ ویندوز)، فایلِ LF (بستهٔ لینوکسی)');
  assert.ok(migrate.sameMigration(sha(lf), lf));
  assert.equal(migrate.checksums(crlf).canonical, sha(lf), 'اثرِ انگشتِ تازه همیشه روی LF است');
  assert.ok(!migrate.sameMigration(sha(lf), lf.replace('int', 'bigint')), '⛔ تغییرِ واقعیِ متن همچنان رد می‌شود');
});

test('۲) دیتابیسی که نصابِ ویندوز ساخته (اثرِ انگشتِ CRLF) با بستهٔ LF بالا می‌آید', async () => {
  //  همان حالِ کامپیوترِ صاحب سامانه
  for (const f of migrate.files()) {
    const lf = fs.readFileSync(path.join(DIR, f), 'utf8').replace(/\r\n/g, '\n');
    await db.query('UPDATE schema_migrations SET checksum=$1 WHERE version=$2', [sha(lf.replace(/\n/g, '\r\n')), f]);
  }
  const done = await migrate.run();
  assert.deepEqual(done, [], 'هیچ Migrationی دوباره اجرا نشد');
});

test('۳) ⛔ ولی اثرِ انگشتِ واقعاً دیگر همچنان سرور را نگه می‌دارد', async () => {
  const first = migrate.files()[0];
  const { checksum } = await db.one('SELECT checksum FROM schema_migrations WHERE version=$1', [first]);
  await db.query('UPDATE schema_migrations SET checksum=$1 WHERE version=$2', ['0'.repeat(32), first]);
  try {
    await assert.rejects(() => migrate.run(), /بعد از اجرا تغییر کرده است/);
  } finally {
    await db.query('UPDATE schema_migrations SET checksum=$1 WHERE version=$2', [checksum, first]);
  }
});
