'use strict';
/**
 * پشتیبان‌گیری و انتقال سرور.
 *
 * سناریوی واقعی: سرور خانگی → پشتیبان → سرور تازه → بازیابی.
 * همه‌چیز باید سر جایش برگردد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs/promises');
const path = require('path');
const os = require('os');
const { Client } = require('pg');
const h = require('./helpers');

const BACKUP_DIR = path.join(os.tmpdir(), `shop-backup-test-${process.pid}`);
process.env.BACKUP_PATH = BACKUP_DIR;

const config = require('../src/config');
config.backup.dir = BACKUP_DIR;

const backup = require('../src/lib/backup');

const RESTORE_DB = 'shop_restore_test';
const adminUrl = process.env.DATABASE_URL.replace(/\/[^/]+$/, '/postgres');
const restoreUrl = process.env.DATABASE_URL.replace(/\/[^/]+$/, `/${RESTORE_DB}`);

async function sql(url, text) {
  const c = new Client({ connectionString: url });
  await c.connect();
  try { return await c.query(text); } finally { await c.end(); }
}

test.before(async () => { await h.start(); });
test.after(async () => {
  await h.stop();
  await fs.rm(BACKUP_DIR, { recursive: true, force: true });
  await sql(adminUrl, `DROP DATABASE IF EXISTS ${RESTORE_DB}`).catch(() => {});
});

test('پشتیبان بدون رمز ساخته می‌شود و روی سرور تازه بازمی‌گردد', async () => {
  config.backup.passphrase = '';

  // چند رکورد واقعی می‌سازیم
  const owner = await h.newUser('صاحب-پشتیبان');
  const shop = (await h.post('/api/shop', { name: 'دکان پشتیبان' }, { token: owner.accessToken })).body.shop;
  await h.post('/api/products', { id: 'p-backup', data: { name: 'چای', price: 120 } }, { token: owner.accessToken });

  const out = await backup.run({ kind: 'manual' });
  assert.ok(out.bytes > 0);
  const meta = JSON.parse(await fs.readFile(`${out.file}.meta.json`, 'utf8'));
  assert.equal(meta.encrypted, false);
  assert.equal(meta.format, 'pg_dump-plain-gzip');

  const sqlText = (await backup.decode(out.file)).toString('utf8');
  assert.match(sqlText, /CREATE TABLE public\.shops/);
  assert.match(sqlText, /دکان پشتیبان/);

  // سرور تازه: دیتابیس خالی، بازیابی، بررسی
  await sql(adminUrl, `DROP DATABASE IF EXISTS ${RESTORE_DB}`);
  await sql(adminUrl, `CREATE DATABASE ${RESTORE_DB}`);
  await backup.restore(out.file, { databaseUrl: restoreUrl });

  const shops = await sql(restoreUrl, `SELECT name FROM shops WHERE id = '${shop.id}'`);
  assert.equal(shops.rows[0].name, 'دکان پشتیبان');
  const products = await sql(restoreUrl, `SELECT data FROM products WHERE id = 'p-backup'`);
  assert.equal(products.rows[0].data.name, 'چای');
  const migrations = await sql(restoreUrl, 'SELECT version FROM schema_migrations ORDER BY version');
  assert.ok(migrations.rows.length >= 2, 'تاریخچه‌ی Migration هم باید منتقل شود');
});

test('پشتیبان رمزشده بدون عبارت عبور باز نمی‌شود و با آن باز می‌شود', async () => {
  config.backup.passphrase = 'یک-عبارت-عبور-طولانی-برای-تست';
  const out = await backup.run({ kind: 'manual' });
  assert.equal(out.encrypted, true);

  const raw = await fs.readFile(out.file);
  assert.equal(raw.subarray(0, 7).toString(), 'SHOPBK1');
  assert.ok(!raw.includes(Buffer.from('CREATE TABLE')), 'محتوا نباید خوانا باشد');

  const text = (await backup.decode(out.file)).toString('utf8');
  assert.match(text, /CREATE TABLE public\.users/);

  config.backup.passphrase = 'عبارت-اشتباه';
  await assert.rejects(() => backup.decode(out.file));
  config.backup.passphrase = '';
});

test('نگه‌داری نسخه‌ها: قدیمی‌ها پاک می‌شوند و تازه‌ها می‌مانند', async () => {
  config.backup.passphrase = '';
  const dir = backup.dirFor('daily');
  await fs.mkdir(dir, { recursive: true });
  for (let i = 1; i <= 6; i++) {
    await fs.writeFile(path.join(dir, `shop-2024010${i}-000000.sql.gz`), 'x');
  }
  config.backup.keepDaily = 3;
  const removed = await backup.prune('daily');
  assert.equal(removed, 3);
  const left = (await fs.readdir(dir)).filter(f => f.endsWith('.sql.gz')).sort();
  assert.deepEqual(left, ['shop-20240104-000000.sql.gz', 'shop-20240105-000000.sql.gz', 'shop-20240106-000000.sql.gz']);
});

/**
 * فایلی که وسط راه دست بخورد، نباید باز شود.
 *
 * رمزگذاری با AES-GCM برچسب احراز اصالت دارد؛ فایده‌اش همین است. اگر
 * فقط رمز می‌شد و سنجیده نمی‌شد، فایلِ خرابِ نیمه‌باز روی دیتابیس اجرا
 * می‌شد — بدتر از نبودنِ پشتیبان.
 */
test('پشتیبان دست‌کاری‌شده باز نمی‌شود', async () => {
  config.backup.passphrase = 'test-passphrase-1234';
  const out = await backup.run({ kind: 'manual' });

  const buf = await fs.readFile(out.file);
  buf[buf.length - 40] ^= 0xff;              // یک بایت وسط بدنه
  const broken = `${out.file}.broken`;
  await fs.writeFile(broken, buf);

  await assert.rejects(() => backup.decode(broken), 'فایل دست‌خورده باید رد شود');

  // خودِ فایل سالم هنوز باز می‌شود — پس رد شدن به‌خاطر دست‌کاری بود، نه رمز
  const good = await backup.decode(out.file);
  assert.match(good.toString('utf8'), /CREATE TABLE/);
  config.backup.passphrase = '';
});

/**
 * pg_dump که با کد ۰ تمام شود ولی چیزی ندهد، نباید «پشتیبان» حساب شود.
 *
 * این حالت واقعاً پیش آمد: در مسیر رمزشده یک `await` بین اجرای pg_dump
 * و وصل شدن به خروجی‌اش بود، و اگر dump در همان فاصله تمام می‌شد
 * خروجی‌اش از دست می‌رفت. فایل خالی ساخته می‌شد، کد خروج ۰ بود و
 * هیچ خطایی نمی‌آمد — یعنی روز خرابی تازه معلوم می‌شد که پشتیبانی نیست.
 *
 * `true` یک برنامه‌ی سیستمی است که بی‌درنگ با کد ۰ تمام می‌شود و چیزی
 * نمی‌نویسد؛ دقیقاً همان حالت.
 */
test('پشتیبانِ خالی، پشتیبان حساب نمی‌شود', async () => {
  const original = config.backup.pgDump;
  config.backup.pgDump = 'true';
  try {
    await assert.rejects(() => backup.run({ kind: 'manual' }), /چیزی نداد/);
  } finally {
    config.backup.pgDump = original;
  }

  // فایل نیمه‌کاره هم نباید جا مانده باشد
  const left = (await fs.readdir(backup.dirFor('manual')).catch(() => []))
    .filter(f => f.endsWith('.part'));
  assert.deepEqual(left, [], 'فایل نیمه‌کاره نباید بماند');
});
