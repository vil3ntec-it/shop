'use strict';
/**
 * تمرینِ واقعیِ بازیابی با تطبیق — §۱۲، DATA-07، DATA-10، SEC-DATA-04/05 (`docs/security/data-fa.md`).
 *
 * دو دکانِ ساختگی (A و B) با کالا، فاکتور، قلم، برگشت و ورودِ انبار ⇒ پشتیبانِ واقعی (`backup.run`) ⇒
 * بازگرداندن در دیتابیسی **جدا** (PGlite ِ تازه در حافظه، یا دیتابیسِ `shop_drill_*` روی PostgreSQL) ⇒
 * شمارِ هر جدول و جمعِ پول و مقدار باید دقیقاً همان manifest ِ لحظهٔ پشتیبان باشد. روی هر دو راه‌انداز
 * می‌دود (`npm test` روی PostgreSQL، `npm run test:pglite`).
 *
 * و: پشتیبانی که فروشِ آخر را ندارد «برابر» گزارش نمی‌شود؛ مهاجرت روی کپی تطبیق می‌خورد؛ رمزِ اشتباه
 * بازنمی‌گرداند؛ شکستِ پشتیبانِ خودکار سابقه و «سلامتِ» قرمز می‌سازد و نشانیِ دیتابیس را لو نمی‌دهد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const h = require('./helpers');

const BACKUP_DIR = path.join(os.tmpdir(), `shop-drill-test-${process.pid}`);
const config = require('../src/config');
const backup = require('../src/lib/backup');
const drill = require('../src/lib/restore-drill');
const { query } = require('../src/db');

const live = () => drill.fingerprint((t, p) => query(t, p));
/** اثرِ انگشتِ زنده پس از آرام شدنِ نوشتن‌های پس‌زمینه (سابقه‌های بی await ِ درخواست‌های پیشین) */
async function stableLive() {
  let a = await live();
  for (let i = 0; i < 40; i++) {
    await new Promise((r) => setTimeout(r, 25));
    const b = await live();
    if (b.digest === a.digest) return b;
    a = b;
  }
  throw new Error('دیتابیس آرام نشد');
}
const PASS = 'drill-passphrase-only-for-this-test';
let seq = 0;
const opId = () => `01J${String(Date.now()).slice(-9)}${String(++seq).padStart(5, '0')}`;

/** دکانِ ساختگی با دفترِ پولی، از راهِ همان API ِ برنامه */
async function seedShop(name, device, { sale, items, ret, entry }) {
  const owner = await h.newUser(`صاحبِ ${name}`, 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name } });
  assert.ok(shop.status < 300, JSON.stringify(shop.body));
  const login = (await h.api('POST', '/api/auth/login', {
    body: { identifier: owner.email, password: owner.password, app: 'shop', device: { deviceId: device, name, platform: 'test' } },
  })).body;
  const token = login.accessToken;
  await h.api('POST', '/api/products', { token, body: { id: `p-${device}`, data: { name: 'چای', salePrice: 120.5, purchasePrice: 90 } } });
  const op = (table, row_id, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id, type: 'insert', fields });
  const ops = [
    op('sales', `s-${device}`, sale),
    ...items.map((it, i) => op('saleItems', `i-${device}-${i}`, { saleId: `s-${device}`, ...it })),
    op('returns', `r-${device}`, { saleId: `s-${device}`, ...ret }),
    op('warehouseEntries', `w-${device}`, entry),
  ];
  const r = await h.api('POST', '/api/sync/v1/push', {
    token, body: { device_id: device, schema_version: 2, ops }, headers: { 'X-App': 'shop', 'X-Device': device },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results));
  return { token, device };
}

let shopA;

test.before(async () => {
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  config.backup.dir = BACKUP_DIR;
  config.backup.passphrase = '';
  shopA = await seedShop('دکانِ A', 'drill-a', {
    sale: { invoiceNumber: 1, total: 1000, discount: 100, finalTotal: 900, paidAmount: 900 },
    items: [{ quantity: 2, unitPrice: 250, totalPrice: 500, returnedQty: 1 }, { quantity: 1.25, unitPrice: 400, totalPrice: 500, returnedQty: 0 }],
    ret: { quantity: 1, amount: 225 },
    entry: { units: 40, cost: 12.75 },
  });
  await seedShop('دکانِ B', 'drill-b', {
    sale: { invoiceNumber: 1, total: 7, discount: 0, finalTotal: 7, paidAmount: 7 },
    items: [{ quantity: 2, unitPrice: 3.5, totalPrice: 7, returnedQty: 0 }],
    ret: { quantity: 0, amount: 0 },
    entry: { units: 3.5, cost: 1 },
  });
});

test.after(async () => {
  config.backup.passphrase = '';
  await h.stop();
  await fs.rm(BACKUP_DIR, { recursive: true, force: true });
});

test('پشتیبانِ واقعی (رمزدار) در دیتابیسِ جدا برمی‌گردد و شمار، پول و مقدار دقیقاً همان manifest است', async () => {
  //  رمزدار: پشتیبانِ بی رمز سابقهٔ «backup.unencrypted» می‌نویسد و دیتابیس را پس از اثرِ انگشتِ آزمون عوض می‌کند
  config.backup.passphrase = PASS;
  const before = await stableLive();
  //  دفترِ دو دکان در اثرِ انگشت هست: جمعِ مبلغِ نهاییِ فاکتورها و مقدارِ ورودِ انبار
  assert.equal(before.ledger['sync_rows/sales.finalTotal'], '2:907');
  assert.equal(before.ledger['sync_rows/warehouseEntries.units'], '2:43.5');
  assert.equal(before.ledger['sync_rows/returns.amount'], '2:225');
  assert.equal(before.ledger['sync_rows/saleItems.totalPrice'], '3:1007');
  assert.equal(before.ledger['products.salePrice'], '2:241');

  const out = await backup.run({ kind: 'manual' });
  const meta = JSON.parse(await fs.readFile(`${out.file}.meta.json`, 'utf8'));
  assert.equal(meta.manifestConsistent, true, 'manifest در همان snapshot ِ پشتیبان');
  assert.equal(meta.manifest.digest, before.digest, 'manifest همان دیتابیسِ لحظهٔ پشتیبان است');
  assert.equal(out.manifest, before.digest);
  //  manifest فقط عدد است: نه نامِ دکان، نه ایمیل
  const text = JSON.stringify(meta);
  assert.ok(!text.includes('دکانِ A') && !text.includes('@'), 'هیچ دادهٔ شخصی در meta.json نیست');

  const r = await drill.drill({ file: out.file });
  assert.equal(r.ok, true, JSON.stringify(r.restore?.diffs?.slice(0, 5)));
  assert.equal(r.manifest, 'meta');
  assert.equal(r.restore.equal, true);
  assert.equal(r.digest, before.digest);
  assert.ok(r.where === 'pglite:memory' || r.where.startsWith('postgres:shop_drill_'), r.where);
  assert.ok(Number(r.summary.rows) > 0 && r.summary.tables > 50, JSON.stringify(r.summary));
  assert.equal(typeof r.restoredMs, 'number');
  assert.ok(r.verifiedMs >= r.restoredMs);
  assert.ok(r.backupAgeMs >= 0 && r.backupAgeMs < 10 * 60 * 1000);
  //  دیتابیسِ زنده دست نخورد
  assert.equal((await live()).digest, before.digest);
  //  عبارتِ اشتباه ⇒ بازنمی‌گردد (AES-GCM)
  config.backup.passphrase = 'wrong-passphrase-for-this-test';
  try { await assert.rejects(() => drill.drill({ file: out.file })); } finally { config.backup.passphrase = ''; }
});

test('پشتیبانی که فروشِ تازه را ندارد «برابر» گزارش نمی‌شود — تفاوت همان جدول و همان مبلغ است', async () => {
  const out = await backup.run({ kind: 'manual' });
  //  پس از پشتیبان: یک فروشِ تازهٔ ۵۰۰ افغانیِ دکانِ A
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: shopA.token,
    body: { device_id: shopA.device, schema_version: 2, ops: [{ op_id: opId(), ts: Date.now(), table: 'sales', row_id: 's-late', type: 'insert', fields: { total: 500, finalTotal: 500 } }] },
    headers: { 'X-App': 'shop', 'X-Device': shopA.device },
  });
  assert.equal(r.status, 200);
  const now = await live();
  const d = await drill.drill({ file: out.file, expected: now });
  assert.equal(d.ok, false);
  assert.equal(d.manifest, 'given');
  const keys = d.restore.diffs.map((x) => `${x.part}:${x.key}`);
  assert.ok(keys.includes('ledger:sync_rows/sales.finalTotal'), keys.join(' '));
  const fin = d.restore.diffs.find((x) => x.key === 'sync_rows/sales.finalTotal');
  assert.deepEqual([fin.expected, fin.actual], ['3:1407', '2:907']);
  assert.ok(keys.includes('tables:sync_rows') && keys.includes('tables:oplog'), keys.join(' '));
});

test('مهاجرت روی کپی: مهاجرت‌های این نسخه روی دیتابیسِ بازگردانده می‌دود و چیزی گم یا عوض نمی‌شود', async () => {
  const out = await backup.run({ kind: 'manual' });
  const d = await drill.drill({ file: out.file, migrate: true });
  assert.equal(d.ok, true, JSON.stringify(d.migration?.diffs?.slice(0, 5)));
  assert.deepEqual(d.migration.applied, [], 'پشتیبانِ همین نسخه مهاجرتِ تازه‌ای ندارد');
  assert.equal(d.migration.equal, true);
});

test('compare: جدول/ستونِ تازه پس از مهاجرت پذیرفته و گزارش می‌شود؛ ازدست‌رفته یا عوض‌شده هرگز', () => {
  const a = { tables: { t: '2' }, columns: { 't.amount_minor': '500' }, ledger: { 'x/sales.total': '1:5' } };
  const added = { tables: { t: '2', n: '0' }, columns: { 't.amount_minor': '500' }, ledger: { 'x/sales.total': '1:5' } };
  assert.equal(drill.compare(a, added, { allowAdded: true }).equal, true);
  assert.equal(drill.compare(a, added, { allowAdded: true }).added.length, 1);
  assert.equal(drill.compare(a, added).equal, false, 'بی allowAdded جدولِ تازه هم تفاوت است');
  const changed = { ...a, columns: { 't.amount_minor': '499' } };
  assert.deepEqual(drill.compare(a, changed, { allowAdded: true }).diffs, [{ part: 'columns', key: 't.amount_minor', expected: '500', actual: '499' }]);
  const lost = { ...a, tables: {} };
  assert.equal(drill.compare(a, lost, { allowAdded: true }).equal, false);
  assert.equal(drill.normNum('3.500'), '3.5');
  assert.equal(drill.normNum('10.000'), '10');
  assert.equal(drill.normNum('-0'), '0');
});

test('پشتیبانِ بی رمز هم برمی‌گردد و با manifest ِ خودش تطبیق می‌خورد', async () => {
  config.backup.passphrase = '';
  const out = await backup.run({ kind: 'manual' });
  assert.equal(out.encrypted, false);
  const d = await drill.drill({ file: out.file });
  assert.equal(d.ok, true, JSON.stringify(d.restore?.diffs?.slice(0, 5)));
});

test('پشتیبانِ بی manifest «برابر» نیست', async () => {
  const out = await backup.run({ kind: 'manual' });
  const metaFile = `${out.file}.meta.json`;
  const meta = JSON.parse(await fs.readFile(metaFile, 'utf8'));
  delete meta.manifest;
  await fs.writeFile(metaFile, JSON.stringify(meta));
  const d = await drill.drill({ file: out.file });
  assert.equal(d.manifest, 'none');
  assert.equal(d.ok, false);
  assert.equal(d.restore, null);
});

test('پشتیبانِ خالی (بی هیچ جدول) هرگز «سالم» نیست، حتی برابر با manifest ِ خودش', async () => {
  const { PGlite } = require('@electric-sql/pglite');
  const empty = await PGlite.create({ dataDir: 'memory://' });
  const tar = Buffer.from(await (await empty.dumpDataDir('gzip')).arrayBuffer());
  const manifest = await drill.fingerprint((t, p) => (p && p.length ? empty.query(t, p) : empty.exec(t).then((r) => r[r.length - 1])));
  await empty.close();
  await fs.mkdir(BACKUP_DIR, { recursive: true });
  const file = path.join(BACKUP_DIR, 'shop-empty.pglite.tar.gz');
  await fs.writeFile(file, tar);
  await fs.writeFile(`${file}.meta.json`, JSON.stringify({ createdAt: Date.now(), manifest }));
  const d = await drill.drill({ file });
  assert.equal(d.restore.equal, true);
  assert.equal(d.empty, true);
  assert.equal(d.ok, false);
});

test('SEC-DATA-05: شکستِ پشتیبانِ خودکار ⇒ سابقهٔ backup.failed و «سلامت» قرمز، تا موفقِ بعدی', async () => {
  const blocker = path.join(BACKUP_DIR, 'not-a-dir');
  await fs.mkdir(BACKUP_DIR, { recursive: true });
  await fs.writeFile(blocker, 'x');
  const realDir = config.backup.dir;
  config.backup.dir = path.join(blocker, 'sub');            //  mkdir زیرِ یک فایل ⇒ ENOTDIR
  const startedAt = Date.now();
  try {
    assert.equal(await backup.tick(new Date('2026-10-14T03:00:00Z')), null, 'tick هرگز throw نمی‌کند');
  } finally {
    config.backup.dir = realDir;
  }
  const st = backup.status();
  assert.ok(st.lastFailure && st.lastFailure.at >= startedAt);
  assert.equal(st.lastFailure.kind, 'daily');
  let row = null;
  for (let i = 0; i < 50 && !row; i++) {
    row = (await query(`SELECT detail FROM audit_logs WHERE action = 'backup.failed' AND created_at >= $1`, [startedAt])).rows[0];
    if (!row) await new Promise((r) => setTimeout(r, 20));
  }
  assert.ok(row, 'سابقهٔ backup.failed');
  const health = require('../src/lib/system-health');
  const sick = await health.snapshot();
  assert.equal(sick.backups.status, 'down');
  assert.equal(sick.backups.lastFailure.kind, 'daily');
  assert.equal(sick.status, 'down');

  //  پشتیبانِ موفقِ بعدی ⇒ دیگر قرمز نیست
  await new Promise((r) => setTimeout(r, 5));
  await backup.run({ kind: 'manual' });
  const well = await health.snapshot();
  assert.notEqual(well.backups.status, 'down');
  assert.equal(well.backups.lastFailure, null);
});

test('SEC-DATA-05: پیامِ شکست نشانیِ دیتابیس و رمزش را لو نمی‌دهد', () => {
  const e = backup.noteFailure('manual', new Error('pg_dump: connection to postgres://shop:hunter2-secret@10.0.0.5:5432/shop failed'));
  assert.ok(!e.message.includes('hunter2-secret') && !e.message.includes('10.0.0.5'), e.message);
  assert.match(e.message, /<db>/);
});
