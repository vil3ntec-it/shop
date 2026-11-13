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
  await h.seed(token, 'products', { id: `p-${device}`, name: 'چای', salePrice: 120.5, purchasePrice: 90 });
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
  const empty = await require('../src/lib/pglite-create').createPglite({ dataDir: 'memory://' });
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

test('D-24 (گرفته‌شده ۱۴۰۵/۰۸/۱۳، الف): هدفِ RPO یک روز و RTO نصفِ روز سنجیده می‌شود؛ بیرون از هدف «برابر» را نمی‌پوشاند', async () => {
  const H = 3600 * 1000;
  assert.equal(config.backup.rpoTargetMs, 24 * H);
  assert.equal(config.backup.rtoTargetMs, 12 * H);
  config.backup.passphrase = PASS;
  try {
    const out = await backup.run({ kind: 'manual' });
    const r = await drill.drill({ file: out.file });
    assert.equal(r.ok, true);
    assert.deepEqual(r.targets, { rpoMs: 24 * H, rtoMs: 12 * H, rpoMet: true, rtoMet: true });
    assert.equal(r.withinTargets, true);

    //  همان پشتیبان ۲۵ ساعت بعد: هنوز «برابر»، ولی بیرون از هدفِ RPO
    const late = await drill.drill({ file: out.file, now: () => Date.now() + 25 * H });
    assert.equal(late.ok, true);
    assert.equal(late.targets.rpoMet, false);
    assert.equal(late.targets.rtoMet, true);
    assert.equal(late.withinTargets, false);

    //  بازگرداندنی که (به ساعتِ ساختگی) ۱۴ ساعت تا پایانِ تطبیق می‌کشد ⇒ RTO ✖
    let t = Date.now();
    const slow = await drill.drill({ file: out.file, now: () => (t += 7 * H) });
    assert.equal(slow.verifiedMs, 14 * H);
    assert.equal(slow.targets.rtoMet, false);
    assert.equal(slow.withinTargets, false);

    //  پشتیبانِ بی meta.json: سنش نامعلوم ⇒ RPO نامعلوم (null)، نه «سبز»
    await fs.rm(`${out.file}.meta.json`);
    const bare = await drill.drill({ file: out.file });
    assert.equal(bare.targets.rpoMet, null);
    assert.equal(bare.withinTargets, false);
  } finally {
    config.backup.passphrase = '';
  }
});
/* ── OPS-02@29: «restore drill و زمانِ واقعیِ RPO/RTO ثبت شود» ─────────────────────────────── */

const DRILL_LOG = () => path.join(config.backup.dir, drill.LOG_FILE);
async function asRoot() {
  const { newId, now } = require('../src/db');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'drill-root','ارشد',$2,'superadmin','active',$3) ON CONFLICT DO NOTHING`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  return (await h.api('POST', '/api/admin/login', { body: { username: 'drill-root', password: 'Root!12345' } })).body.token;
}

test('OPS-02@29: هر تمرین با RTO/RPO ِ اندازه‌گرفته ثبت می‌شود، شکست هم؛ «سلامت» و پنل آخرینش را می‌بینند', async () => {
  const health = require('../src/lib/system-health');
  await fs.rm(DRILL_LOG(), { force: true });
  const enabled = config.backup.enabled;
  config.backup.enabled = true;               //  تا «وضع» فقط از پشتیبان و تمرین بیاید، نه از خاموشیِ آزمون
  try {
    //  هیچ تمرینی ثبت نشده ⇒ بی تمرین و همان وضعِ امروز (هیچ سرورِ بی‌تمرینی زرد نمی‌شود)
    config.backup.passphrase = PASS;
    const out = await backup.run({ kind: 'manual' });
    const none = (await health.snapshot()).backups;
    assert.equal(none.drill, null);
    assert.equal(none.status, 'ok');

    const r = await drill.drillAndRecord({ file: out.file });
    assert.equal(r.ok, true);
    const ok = (await health.snapshot()).backups;
    assert.equal(ok.status, 'ok');
    assert.equal(ok.drill.last.ok, true);
    assert.equal(ok.drill.last.restoredMs, r.restoredMs);
    assert.equal(ok.drill.last.verifiedMs, r.verifiedMs);
    assert.equal(ok.drill.last.backupAgeMs, r.backupAgeMs);
    assert.equal(ok.drill.lastOkAt, ok.drill.last.at);
    assert.equal(ok.drill.failing, false);

    //  عبارتِ اشتباه ⇒ پشتیبان برنمی‌گردد ⇒ ثبت با «ناموفق» و «سلامت» زرد (پشتیبانی که برنمی‌گردد پشتیبان نیست)
    config.backup.passphrase = 'wrong-passphrase-for-this-test';
    await assert.rejects(() => drill.drillAndRecord({ file: out.file }));
    config.backup.passphrase = PASS;
    const bad = (await health.snapshot()).backups;
    assert.equal(bad.drill.last.ok, false);
    assert.equal(bad.drill.failing, true);
    assert.equal(bad.drill.last.error, 'passphrase', 'فقط گونهٔ شکست، نه متنِ خطا');
    assert.equal(bad.drill.lastOkAt, ok.drill.last.at, 'آخرین تمرینِ موفق گم نمی‌شود');
    assert.equal(bad.status, 'warn');

    //  پنل همان را می‌بیند (فقط خواندن؛ بی هیچ مسیرِ تازه)
    const root = await asRoot();
    const panel = await h.api('GET', '/api/admin/backups', { token: root });
    assert.equal(panel.status, 200);
    assert.equal(panel.body.drill.last.ok, false);
    assert.equal(panel.body.drill.lastOkAt, ok.drill.last.at);
    assert.ok(Array.isArray(panel.body.drill.recent) && panel.body.drill.recent.length === 2);

    //  تمرینِ موفقِ بعدی ⇒ دوباره سبز
    await drill.drillAndRecord({ file: out.file });
    const again = (await health.snapshot()).backups;
    assert.equal(again.status, 'ok');
    assert.equal(again.drill.failing, false);

    //  دفترِ تمرین فقط عدد است: نه نامِ دکان، نه ایمیل، نه عبارتِ عبور، نه نشانیِ دیتابیس
    const text = await fs.readFile(DRILL_LOG(), 'utf8');
    for (const bad of ['دکانِ A', '@', PASS, 'wrong-passphrase', 'postgres://', 'shoppass']) {
      assert.ok(!text.includes(bad), `دفترِ تمرین «${bad}» دارد`);
    }
  } finally {
    config.backup.enabled = enabled;
    config.backup.passphrase = '';
  }
});

test('OPS-02@29: تمرینِ موفقِ کهنه‌تر از یک ماه (D-24: «تمرینِ ماهانه») ⇒ زرد؛ BACKUP_DRILL_MAX_DAYS=0 خاموشش می‌کند', async () => {
  const health = require('../src/lib/system-health');
  const enabled = config.backup.enabled;
  const maxDays = config.backup.drillMaxDays;
  config.backup.enabled = true;
  try {
    assert.equal(config.backup.drillMaxDays, 31, 'پیش‌فرض: ماهانه (D-24، الف)');
    await fs.rm(DRILL_LOG(), { force: true });
    await backup.run({ kind: 'manual' });
    const DAY = 24 * 3600e3;
    await drill.record({ ok: true, restoredMs: 1200, verifiedMs: 1300, backupAgeMs: 5000, summary: { tables: 9, rows: 99 } },
      { at: Date.now() - 40 * DAY });
    const stale = (await health.snapshot()).backups;
    assert.equal(stale.drill.stale, true);
    assert.equal(stale.drill.maxDays, 31);
    assert.equal(stale.status, 'warn');
    config.backup.drillMaxDays = 0;
    assert.equal((await health.snapshot()).backups.status, 'ok', '۰ = خاموش');
    config.backup.drillMaxDays = maxDays;
    await drill.record({ ok: true, restoredMs: 1100, verifiedMs: 1200, backupAgeMs: 4000, summary: { tables: 9, rows: 99 } });
    assert.equal((await health.snapshot()).backups.status, 'ok', 'تمرینِ تازه ⇒ دوباره سبز');
  } finally {
    config.backup.enabled = enabled;
    config.backup.drillMaxDays = maxDays;
  }
});

test('OPS-02@29: دفترِ تمرین کران‌دار است و سطرِ خراب «سلامت» را نمی‌اندازد', async () => {
  await fs.rm(DRILL_LOG(), { force: true });
  await fs.mkdir(config.backup.dir, { recursive: true });
  await fs.writeFile(DRILL_LOG(), '{نیمه‌کاره\n');
  for (let i = 0; i < drill.LOG_MAX + 15; i++) {
    await drill.record({ ok: i % 2 === 0, restoredMs: i, verifiedMs: i, backupAgeMs: i, summary: { tables: 1, rows: i } }, { at: 1_700_000_000_000 + i });
  }
  const lines = (await fs.readFile(DRILL_LOG(), 'utf8')).trim().split('\n');
  assert.equal(lines.length, drill.LOG_MAX);
  const hist = await drill.history();
  assert.equal(hist.last.restoredMs, drill.LOG_MAX + 14);
  assert.equal(hist.last.ok, true);
  assert.equal(hist.recent.length, 10);
  assert.equal(hist.last.withinTargets, null, 'تمرینی که هدف نسنجیده «نامعلوم» است، نه «سبز»');
  await drill.record({ ok: true, withinTargets: false, restoredMs: 1, verifiedMs: 1, backupAgeMs: 1, summary: { tables: 1, rows: 1 } });
  assert.equal((await drill.history()).last.withinTargets, false);
});
