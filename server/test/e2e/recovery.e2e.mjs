/**
 * سناریوی سرتاسریِ بازیابی و به‌روزرسانیِ نیمه‌کاره — REC-01 و REL-01 (§۳۸.۱۱).
 *
 *   REC-01 ── پشتیبانِ رمزدارِ سرور در **محیطِ جدا** برمی‌گردد (پوشهٔ تازه، فرایندِ جدا، سرورِ
 *             واقعیِ `src/index.js`) و «شمارش و جمعِ کنترل» با مبدأ یکی است: هر جدول (شمار، هشِ
 *             محتوا، جمعِ ستون‌های `*_minor`)، دفترِ همگام‌شدهٔ دکان (شمار و جمعِ هر فیلدِ عددی ِ
 *             هر مجموعه)، verify-data، و از درِ برنامه: دستگاهِ نو همان جمعِ فروش، قرض و موجودی.
 *   REL-01 ── «نسخهٔ تازهٔ سرور» (کپیِ همین سرور + یک مهاجرتِ تازه) روی همان پایگاه:
 *               • مهاجرت وسطِ کار خطا می‌دهد ⇒ سرور بالا نمی‌آید (کدِ ۱، نامِ فایل در پیام) و
 *                 پایگاه بایت‌به‌بایتِ محتوا همان پیش است (نه ستونِ نیمه، نه جدولِ نیمه، نه ردیفِ
 *                 schema_migrations)؛
 *               • فرایند وسطِ مهاجرت کشته می‌شود (SIGKILL، «برق رفت») ⇒ همان؛
 *               • طرحِ برگشت: نسخهٔ پیش روی همان پایگاه بالا می‌آید، فروشِ صف‌شدهٔ دستگاه در زمانِ
 *                 خاموشی یک بار می‌رسد؛
 *               • نسخهٔ اصلاح‌شده (همان نامِ فایل) اعمال می‌شود؛ داده و جمع‌ها حفظ و برنامه ادامه
 *                 می‌دهد.
 *
 * ⚠️ PGlite (همان پایگاهِ سرورِ خانگی، D-01). روی PostgreSQL همان `migrate.run` با BEGIN/ROLLBACK است.
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.orders.refunds.
 * اجرا:  node test/e2e/recovery.e2e.mjs
 */
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import zlib from 'node:zlib';
import { createHash } from 'node:crypto';
import { boot, assert, sleep } from './lib/harness.mjs';
import { NativeDevice } from './lib/native.mjs';
import { fingerprint, diff } from './lib/control-sums.mjs';

const require = createRequire(import.meta.url);
const SERVER = path.join(import.meta.dirname, '..', '..');
const SUMS = path.join(import.meta.dirname, 'lib', 'control-sums.mjs');
const PASS = 'e2e-backup-passphrase-rec-01';
const MIG = '999_e2e_release_rehearsal.sql';

const E = await boot({ name: 'recovery', env: { BACKUP_PASSPHRASE: PASS } });
const { db } = E;
const owner = await E.owner('owner-rec@e2e.local', 'صاحبِ بازیابی', 'دکانِ بازیابی');
const staff = await E.staff(owner, 'staff-rec@e2e.local', 'شاگردِ بازیابی');
for (const k of ['shop.orders', 'shop.orders.refunds']) await E.flag(owner.shopId, k, true);

/* ════════════════════════ ابزار ════════════════════════ */

/** یک فرایندِ جدا؛ ⇒ {code, signal, out} */
function run(args, env = {}, ms = 120_000) {
  return new Promise((resolve, reject) => {
    const c = spawn(process.execPath, args, { env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'pipe'] });
    let out = '';
    c.stdout.on('data', (d) => { out += d; });
    c.stderr.on('data', (d) => { out += d; });
    const t = setTimeout(() => { c.kill('SIGKILL'); reject(new Error(`بیش از ${ms / 1000} ثانیه: ${args.join(' ')}\n${out.slice(-600)}`)); }, ms);
    c.on('exit', (code, signal) => { clearTimeout(t); resolve({ code, signal, out }); });
  });
}
/** fingerprint ِ پوشه در فرایندِ جدا (سرور خاموش) */
async function sumsOf(dir) {
  const r = await run([SUMS, 'sums', dir]);
  assert.equal(r.code, 0, `شمارش و جمعِ کنترل: ${r.out.slice(-600)}`);
  return JSON.parse(r.out.trim().split('\n').at(-1));
}
const freePort = () => new Promise((resolve) => {
  const s = net.createServer();
  s.listen(0, '127.0.0.1', () => { const { port } = s.address(); s.close(() => resolve(port)); });
});

/**
 * یک «نسخه»ی سرور: کپیِ src، migrations و package.json (+ پیوندِ node_modules و public).
 * `sql` ⇒ همان نسخه با یک مهاجرتِ تازه (`MIG`).
 */
function release(name, sql = null) {
  const root = path.join(E.tmp, `release-${name}`, 'server');
  fs.mkdirSync(root, { recursive: true });
  for (const d of ['src', 'migrations']) fs.cpSync(path.join(SERVER, d), path.join(root, d), { recursive: true });
  fs.copyFileSync(path.join(SERVER, 'package.json'), path.join(root, 'package.json'));
  for (const d of ['node_modules', 'public']) fs.symlinkSync(path.join(SERVER, d), path.join(root, d), 'dir');
  if (sql !== null) fs.writeFileSync(path.join(root, 'migrations', MIG), sql);
  return root;
}

/**
 * `src/index.js` ِ یک نسخه روی پوشهٔ PGlite — همان دستورِ راه‌اندازیِ سرورِ واقعی.
 * ⇒ { child, port, log(), exited: Promise<{code, signal}>, stop() }
 */
async function serve(root, dir) {
  const port = await freePort();
  const child = spawn(process.execPath, [path.join(root, 'src', 'index.js')], {
    cwd: root,
    env: { ...process.env, DATABASE_URL: `pglite:${dir}`, PORT: String(port), HOST: '127.0.0.1', BACKUP_ENABLED: 'false' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let out = '';
  child.stdout.on('data', (d) => { out += d; });
  child.stderr.on('data', (d) => { out += d; });
  const exited = new Promise((r) => child.on('exit', (code, signal) => r({ code, signal })));
  return {
    child, port, exited, log: () => out,
    base: `http://127.0.0.1:${port}`,
    /** تا /api/health ِ ۲۰۰ — یا افتادنِ فرایند */
    async ready(ms = 90_000) {
      const until = Date.now() + ms;
      let dead = null;
      exited.then((x) => { dead = x; });
      for (;;) {
        if (dead) throw new Error(`سرور افتاد (${dead.code ?? dead.signal}): ${out.slice(-600)}`);
        try { if ((await fetch(`http://127.0.0.1:${port}/api/health`)).status === 200) return; } catch { /* هنوز نه */ }
        if (Date.now() > until) throw new Error(`سرور بالا نیامد: ${out.slice(-600)}`);
        await sleep(150);
      }
    },
    /** خاموشیِ عادی (SIGTERM) */
    async stop() {
      child.kill('SIGTERM');
      const x = await exited;
      assert.equal(x.code, 0, `خاموشی: ${x.code ?? x.signal} ${out.slice(-400)}`);
    },
  };
}
const at = (s) => ({ ...E, apiBase: s.base });

/** جمعِ کنترل از درِ برنامه: فروشِ قطعی، قرضِ هر قرض‌دار، موجودیِ هر کالا */
function totals(dev) {
  const L = dev.ledger;
  const done = L.sales.filter((s) => s.status === 'completed');
  return {
    sales: L.sales.length,
    completed: done.length,
    salesTotal: Math.round(done.reduce((s, x) => s + x.finalTotal, 0) * 100),
    returns: (L.returns || []).length,
    debt: Object.fromEntries(L.debtors.map((d) => [d.id, dev.debt(d.id)])),
    stock: Object.fromEntries(L.products.map((p) => [p.id, dev.stock(p.id)])),
  };
}
/** دو fingerprint یکی‌اند — وگرنه خودِ تفاوت‌ها در پیام */
function same(a, b, what, o) {
  const d = diff(a, b, o);
  assert.ok(!d.length, `${what}: ${d.slice(0, 12).join('؛ ')}`);
}
const summary = (fp) => `${Object.keys(fp.tables).length} جدول، ${Object.values(fp.tables).reduce((s, t) => s + t.rows, 0)} ردیف، `
  + `${Object.keys(fp.ledger).length} مجموعهٔ دفتر`;

let A; let B; let rice; let oil; let debtor; let orderId; let file; let origin; let originTotals; let originOrder; let originRecon;
const DRILL = path.join(E.tmp, 'drill');          //  «محیطِ جدا»: پوشه‌ای که سرورِ مبدأ هرگز ندیده
const DIR = path.join(DRILL, 'db');
let fpServed;

/* ════════════════════════ REC-01 ════════════════════════ */

await E.step('REC-01.0', 'دکانِ واقعی: فروشِ نقد و نسیه، دریافت، مرجوعی، سفارشِ اینترنتی با بازپرداختِ جزئی', async () => {
  A = await E.device('A-owner-phone', owner.email);
  B = await E.device('B-staff-desktop', staff.email);
  for (const d of [A, B]) assert.equal((await d.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 50 });
  oil = A.stockIn({ name: 'روغن', price: 350, cost: 300, units: 12 });
  debtor = A.addDebtor('احمد', '0791234567');
  const s1 = A.sell({ lines: [{ id: rice, qty: 3 }, { id: oil, qty: 1 }] });
  A.sell({ lines: [{ id: rice, qty: 4 }], payment: 'credit', debtorId: debtor, paid: 100 });
  A.receive(debtor, 50);
  A.returnItems(s1.saleId, { [A.ledger.saleItems.find((x) => x.saleId === s1.saleId && x.productId === rice).id]: 1 }, 'خراب');
  await A.drain();
  await B.drain();
  B.sell({ lines: [{ id: oil, qty: 2 }] });
  await B.drain();
  await A.drain();
  const o = E.ok(await A.api('POST', '/shop/orders', {
    clientKey: E.key('app'), source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'مشتری', phone: '0791110000' }, lines: [{ productId: rice, name: 'برنج', unit: 'عدد', qtyMilli: 2000, unitPriceMinor: 10000 }],
  }, { headers: { 'Idempotency-Key': E.key('o') } }), 'سفارش', 201).order;
  orderId = o.id;
  for (const [to, from] of [['accepted', 'new'], ['picking', 'accepted'], ['packed', 'picking'], ['ready_for_pickup', 'packed'], ['picked_up', 'ready_for_pickup'], ['return_requested', 'picked_up']]) {
    E.ok(await A.api('POST', `/shop/orders/${orderId}/transition`, { to, from }), `${from} ⇐ ${to}`);
  }
  E.ok(await A.api('POST', `/shop/orders/${orderId}/refunds`, { amountMinor: 5000, reason: 'یک کیسه', final: true }, { headers: { 'Idempotency-Key': E.key('r') } }), 'بازپرداخت', 201);
  assert.equal(B.queue.length + A.queue.length, 0);
});

await E.step('REC-01.backup', 'پشتیبانِ رمزدار؛ شمارش و جمعِ کنترلِ مبدأ همان لحظه (پیش و پس از پشتیبان یکسان)', async () => {
  const q = (sql, params) => db.many(sql, params);
  origin = await fingerprint(q);
  const backup = require('../../src/lib/backup');
  const out = await backup.run({ kind: 'manual' });
  file = out.file;
  assert.equal(out.encrypted, true);
  same(origin, await fingerprint(q), 'مبدأ وسطِ پشتیبان عوض شد');
  //  درستیِ فایل: sha256 ِ meta همان بایت‌های روی دیسک
  const meta = JSON.parse(fs.readFileSync(`${file}.meta.json`, 'utf8'));
  assert.equal(createHash('sha256').update(fs.readFileSync(file)).digest('hex'), meta.sha256, 'sha256 ِ پشتیبان');
  originTotals = totals(A);
  originOrder = E.ok(await A.api('GET', `/shop/orders/${orderId}`), 'سفارش').order;
  originRecon = E.ok(await A.api('GET', '/shop/orders/reconciliation'), 'تطبیق');
  assert.ok(origin.tables.oplog.rows > 0 && Object.keys(origin.ledger).length > 5, summary(origin));
  console.log(`     مبدأ: ${summary(origin)}`);
}, { needs: ['REC-01.0'] });

await E.step('REC-01', 'بازیابی در محیطِ جدا (پوشهٔ تازه، فرایندِ جدا): شمار، هش و جمعِ هر جدول و هر مجموعهٔ دفتر = مبدأ؛ verify-data سالم', async () => {
  const backup = require('../../src/lib/backup');
  fs.mkdirSync(DRILL, { recursive: true });
  const tgz = path.join(DRILL, 'restore.tar.gz');
  fs.writeFileSync(tgz, zlib.gzipSync(await backup.decode(file)));
  const l = await run([SUMS, 'load', DIR, tgz]);
  assert.equal(l.code, 0, l.out.slice(-600));
  const restored = await sumsOf(DIR);
  same(origin, restored, 'بازیابی با مبدأ نمی‌خواند');
  //  جمعِ کنترلِ پولی صفر نیست (سنجش تهی نیست)
  assert.ok(Number(restored.tables.online_orders.sums.total_minor) > 0, JSON.stringify(restored.tables.online_orders));
  assert.ok(Number(restored.tables.online_order_refunds.sums.amount_minor) === 5000, JSON.stringify(restored.tables.online_order_refunds));
  const sales = Object.entries(restored.ledger).find(([k]) => k.endsWith('/sales'));
  assert.ok(sales && Number(sales[1].sums.finalTotal) > 0, JSON.stringify(sales));
  const v = await run([path.join(SERVER, 'scripts', 'verify-data.js')], { DATABASE_URL: `pglite:${DIR}` });
  assert.equal(v.code, 0, `verify-data روی بازیابی‌شده: ${v.out.slice(-800)}`);
  console.log(`     بازیابی‌شده: ${summary(restored)}`);
}, { needs: ['REC-01.backup'] });

await E.step('REC-01.app', 'سرورِ واقعی روی پوشهٔ بازیابی‌شده: دستگاهِ نو همان جمعِ فروش، قرض و موجودی؛ سفارش، رویدادها و تطبیق همان', async () => {
  const S = await serve(SERVER, DIR);
  try {
    await S.ready();
    const N = new NativeDevice(at(S), 'N-new-device');
    N.token = A.token;
    N.refreshToken = A.refreshToken;
    assert.equal((await N.firstSync()).ok, true);
    assert.deepEqual(totals(N), originTotals, 'جمعِ کنترل از درِ برنامه');
    const o = E.ok(await N.api('GET', `/shop/orders/${orderId}`), 'سفارش').order;
    assert.equal(o.status, originOrder.status);
    assert.equal(o.events.length, originOrder.events.length, 'رویدادهای سفارش');
    const rec = E.ok(await N.api('GET', '/shop/orders/reconciliation'), 'تطبیق');
    assert.deepEqual([rec.collected.totalMinor, rec.refunds.totalMinor, rec.netMinor], [originRecon.collected.totalMinor, originRecon.refunds.totalMinor, originRecon.netMinor]);
    //  کارِ تازه روی سرورِ بازیابی‌شده: A (همان نشست) می‌فروشد
    A.E = at(S);
    A.sell({ lines: [{ id: rice, qty: 1 }] });
    await A.drain();
  } finally {
    await S.stop();
  }
  fpServed = await sumsOf(DIR);
}, { needs: ['REC-01'] });

/* ════════════════════════ REL-01 ════════════════════════ */

let broken;
await E.step('REL-01.failed', 'نسخهٔ تازه با مهاجرتی که وسطِ کار خطا می‌دهد ⇐ سرور بالا نمی‌آید و پایگاه دست‌نخورده (نه ستون، نه جدول، نه ردیفِ نیمه)', async () => {
  broken = release('broken', [
    'ALTER TABLE online_orders ADD COLUMN rel01_note text;',
    "UPDATE online_orders SET rel01_note = 'نیمه';",
    'CREATE TABLE rel01_probe (id int PRIMARY KEY);',
    'INSERT INTO rel01_probe SELECT generate_series(1, 100);',
    'UPDATE oplog SET server_seq = server_seq;',
    'SELECT 1 / 0;',
  ].join('\n'));
  const S = await serve(broken, DIR);
  const x = await Promise.race([S.exited, sleep(90_000).then(() => null)]);
  if (!x) { S.child.kill('SIGKILL'); throw new Error(`نسخهٔ خراب بالا ماند: ${S.log().slice(-400)}`); }
  assert.equal(x.code, 1, `کدِ خروج ${x.code ?? x.signal}: ${S.log().slice(-400)}`);
  assert.match(S.log(), new RegExp(`${MIG}.*شکست خورد`), 'پیام نامِ مهاجرت را نمی‌گوید');
  const after = await sumsOf(DIR);
  assert.equal(after.tables.rel01_probe, undefined, 'جدولِ نیمه ماند');
  same(fpServed, after, 'مهاجرتِ شکسته چیزی را عوض کرد');
}, { needs: ['REC-01.app'] });

await E.step('REL-01.killed', 'فرایندِ نسخهٔ تازه وسطِ مهاجرت کشته می‌شود (SIGKILL) ⇐ پس از بالا آمدن هیچ نیمه‌کاری نمی‌ماند', async () => {
  const slow = release('slow', [
    'ALTER TABLE online_orders ADD COLUMN rel01_note text;',
    "UPDATE online_orders SET rel01_note = 'نیمه';",
    'CREATE TABLE rel01_probe (id int PRIMARY KEY);',
    'INSERT INTO rel01_probe SELECT generate_series(1, 100);',
    //  کارِ طولانی (فقط پردازنده، بی دیسک) تا کشتن وسطِ همین تراکنش بیفتد
    'SELECT count(*) FROM generate_series(1, 3000000000);',
  ].join('\n'));
  const S = await serve(slow, DIR);
  const until = Date.now() + 90_000;
  while (!S.log().includes(`↑ ${MIG}`)) {
    if (S.child.exitCode !== null || Date.now() > until) throw new Error(`مهاجرت شروع نشد: ${S.log().slice(-400)}`);
    await sleep(50);
  }
  await sleep(1500);
  assert.equal(S.child.exitCode, null, `پیش از کشتن تمام شد: ${S.log().slice(-300)}`);
  S.child.kill('SIGKILL');
  assert.equal((await S.exited).signal, 'SIGKILL');
  const after = await sumsOf(DIR);
  assert.equal(after.tables.rel01_probe, undefined, 'جدولِ نیمه ماند');
  same(fpServed, after, 'کشتنِ وسطِ مهاجرت چیزی را عوض کرد');
}, { needs: ['REC-01.app'] });

let back;
await E.step('REL-01.rollback', 'طرحِ برگشت: نسخهٔ پیش روی همان پایگاه بالا می‌آید؛ فروشِ صف‌شده در زمانِ خاموشی یک بار می‌رسد', async () => {
  //  در زمانِ به‌روزرسانیِ ناکام، دکان کار کرده است (بی اینترنتِ سرور)
  const sold = A.sell({ lines: [{ id: oil, qty: 1 }] });
  assert.equal((await A.sync()).ok, false, 'سرورِ خاموش «موفق» گفت');
  assert.ok(A.queue.length > 0);
  const S = await serve(SERVER, DIR);
  try {
    await S.ready();
    A.E = at(S);
    B.E = at(S);
    await A.drain();
    await A.drain();      //  دوباره: «تکراری» نه اثرِ دوم
    await B.drain();
    assert.equal(B.ledger.sales.filter((s) => s.id === sold.saleId).length, 1);
    assert.deepEqual(totals(B), totals(A), 'دو دستگاه پس از برگشت');
    back = totals(A);
  } finally {
    await S.stop();
  }
  const fp = await sumsOf(DIR);
  assert.equal(fp.tables.rel01_probe, undefined);
  assert.equal(fp.ledger[Object.keys(fp.ledger).find((k) => k.endsWith('/sales'))].rows, back.sales, 'فروش‌های دفترِ سرور');
}, { needs: ['REL-01.failed', 'REL-01.killed'] });

await E.step('REL-01.fixed', 'نسخهٔ اصلاح‌شده (همان نامِ فایل) اعمال می‌شود؛ شمار و جمعِ همهٔ جدول‌ها و دفتر حفظ؛ برنامه ادامه می‌دهد', async () => {
  const before = await sumsOf(DIR);
  const fixed = release('fixed', [
    "ALTER TABLE online_orders ADD COLUMN rel01_note text NOT NULL DEFAULT '';",
    'CREATE TABLE rel01_probe (id int PRIMARY KEY);',
    'INSERT INTO rel01_probe SELECT generate_series(1, 100);',
  ].join('\n'));
  let S = await serve(fixed, DIR);
  await S.ready();
  assert.match(S.log(), new RegExp(`↑ ${MIG}`));
  await S.stop();
  const after = await sumsOf(DIR);
  assert.equal(after.tables.rel01_probe.rows, 100);
  assert.equal(after.tables.schema_migrations.rows, before.tables.schema_migrations.rows + 1);
  same(before, after, 'داده پس از مهاجرت', { ignore: ['rel01_probe', 'schema_migrations'], countsOnly: ['online_orders'] });
  S = await serve(fixed, DIR);
  try {
    await S.ready();
    A.E = at(S);
    const s = A.sell({ lines: [{ id: rice, qty: 2 }] });
    await A.drain();
    const N = new NativeDevice(at(S), 'N2-after-update');
    N.token = A.token;
    N.refreshToken = A.refreshToken;
    assert.equal((await N.firstSync()).ok, true);
    assert.deepEqual(totals(N), totals(A));
    assert.equal(N.ledger.sales.filter((x) => x.id === s.saleId).length, 1);
    assert.equal(totals(N).sales, back.sales + 1);
    const o = E.ok(await N.api('GET', `/shop/orders/${orderId}`), 'سفارش پس از به‌روزرسانی').order;
    assert.equal(o.status, originOrder.status);
  } finally {
    await S.stop();
  }
  const v = await run([path.join(SERVER, 'scripts', 'verify-data.js')], { DATABASE_URL: `pglite:${DIR}` });
  assert.equal(v.code, 0, `verify-data پس از به‌روزرسانی: ${v.out.slice(-800)}`);
}, { needs: ['REL-01.rollback'] });

await E.finish();
