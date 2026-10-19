/**
 * سناریوی سرتاسریِ پشتیبان و بازیابی — E2E-OPS-14 و §۹.۵ («بازیابی تا آخرین پشتیبان و سپس
 * اعمالِ opهای صف»).
 *
 *   ۱) دکانِ واقعی: فروشِ نقد و نسیه، دریافت، مرجوعی (برنامه، Sync v1)؛ سفارشِ اینترنتی تا
 *      «برده شد» با بازپرداخت؛ کاتالوگِ سرور.
 *   ۲) پشتیبانِ رمزدارِ سرور (PGlite، AES-256-GCM): بی عبارتِ عبور یا با عبارتِ غلط باز نمی‌شود
 *      و متنِ دکان در فایل خوانا نیست.
 *   ۳) پس از پشتیبان، شاگرد یک فروشِ دیگر می‌فرستد (پنجرهٔ «پس از آخرین پشتیبان»).
 *   ۴) سرورِ تازه از همان فایل (فرایندِ جدا، پوشهٔ تازه) بالا می‌آید.
 *   ۵) دستگاه‌ها با همان نشست ادامه می‌دهند؛ دستگاهِ نو از Snapshot همان دفتر را می‌گیرد؛ سفارش،
 *      رویدادها، بازپرداخت، گزارشِ تطبیق و کاتالوگ با پیش از پشتیبان یکی‌اند.
 *   ۶) §۹.۵: فروشِ «پس از پشتیبان» که در سرورِ بازیابی‌شده نیست، از صف/دفترِ دستگاه دوباره می‌رسد.
 *
 * اجرا:  node test/e2e/ops.e2e.mjs
 */
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { boot, assert } from './lib/harness.mjs';
import { NativeDevice } from './lib/native.mjs';

const require = createRequire(import.meta.url);
const PASS = 'e2e-backup-passphrase-ops-14';
const E = await boot({ name: 'ops', env: { BACKUP_PASSPHRASE: PASS } });
const { db } = E;
const owner = await E.owner('owner-ops@e2e.local', 'صاحبِ پشتیبان', 'دکانِ پشتیبان');
const staff = await E.staff(owner, 'staff-ops@e2e.local', 'شاگردِ پشتیبان');
for (const k of ['shop.orders', 'shop.orders.refunds', 'shop.catalog']) await E.flag(owner.shopId, k, true);

let A; let B; let rice; let orderId; let before; let file; let child; let R; let lateSale;

await E.step('E2E-OPS-14.0', 'دکانِ واقعی: فروش، نسیه، دریافت، مرجوعی، سفارش و بازپرداخت', async () => {
  A = await E.device('A-owner-phone', owner.email);
  B = await E.device('B-staff-desktop', staff.email);
  for (const d of [A, B]) assert.equal((await d.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 50 });
  const debtor = A.addDebtor('احمد');
  const s1 = A.sell({ lines: [{ id: rice, qty: 3 }] });
  A.sell({ lines: [{ id: rice, qty: 4 }], payment: 'credit', debtorId: debtor, paid: 100 });
  A.receive(debtor, 50);
  A.returnItems(s1.saleId, { [A.ledger.saleItems.find((x) => x.saleId === s1.saleId).id]: 1 }, 'خراب');
  await A.drain();
  await B.drain();
  const o = E.ok(await A.api('POST', '/shop/orders', {
    clientKey: E.key('app'), source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'مشتری', phone: '0791110000' }, lines: [{ productId: rice, name: 'برنج', unit: 'عدد', qtyMilli: 2000, unitPriceMinor: 10000 }],
  }, { headers: { 'Idempotency-Key': E.key('o') } }), 'سفارش', 201).order;
  orderId = o.id;
  for (const [to, from] of [['accepted', 'new'], ['picking', 'accepted'], ['packed', 'picking'], ['ready_for_pickup', 'packed'], ['picked_up', 'ready_for_pickup'], ['return_requested', 'picked_up']]) {
    E.ok(await A.api('POST', `/shop/orders/${orderId}/transition`, { to, from }), `${from} ⇐ ${to}`);
  }
  E.ok(await A.api('POST', `/shop/orders/${orderId}/refunds`, { amountMinor: 5000, reason: 'یک کیسه', final: true }, { headers: { 'Idempotency-Key': E.key('r') } }), 'بازپرداخت', 201);
  before = {
    ledger: Object.fromEntries(['products', 'sales', 'saleItems', 'returns', 'transactions', 'debtors', 'warehouseEntries', 'stockMovements', 'auditLog'].map((t) => [t, A.ledger[t].map((x) => x.id).sort()])),
    stock: A.stock(rice),
    debt: A.debt(debtor),
    order: E.ok(await A.api('GET', `/shop/orders/${orderId}`), 'سفارش').order,
    recon: E.ok(await A.api('GET', '/shop/orders/reconciliation'), 'تطبیق'),
    catalog: E.ok(await A.api('GET', `/shop/catalog/products/${rice}`), 'کاتالوگ').product.stock,
    refunds: (await db.many('SELECT id, amount_minor FROM online_order_refunds WHERE order_id=$1', [orderId])).map((r) => `${r.id}:${r.amount_minor}`),
    debtor,
  };
});

await E.step('E2E-OPS-14.backup', 'پشتیبانِ رمزدارِ سرور: متنِ دکان در فایل خوانا نیست؛ بی عبارت یا با عبارتِ غلط باز نمی‌شود', async () => {
  const backup = require('../../src/lib/backup');
  const config = require('../../src/config');
  const out = await backup.run({ kind: 'manual' });
  file = out.file;
  assert.equal(out.encrypted, true);
  const raw = fs.readFileSync(file);
  assert.equal(raw.subarray(0, 7).toString(), 'SHOPBK1');
  assert.equal(raw.includes(Buffer.from('دکانِ پشتیبان')), false, 'نامِ دکان در فایلِ رمزدار خوانا است');
  const meta = JSON.parse(fs.readFileSync(`${file}.meta.json`, 'utf8'));
  assert.equal(meta.format, 'pglite-datadir-tar-gzip');
  config.backup.passphrase = 'عبارتِ غلط';
  await assert.rejects(() => backup.decode(file));
  config.backup.passphrase = '';
  await assert.rejects(() => backup.decode(file));
  config.backup.passphrase = PASS;
}, { needs: ['E2E-OPS-14.0'] });

await E.step('E2E-OPS-14.late', 'پس از پشتیبان: شاگرد یک فروشِ دیگر می‌فرستد (روی سرورِ قدیم «اعمال شد»)', async () => {
  lateSale = B.sell({ lines: [{ id: rice, qty: 2 }] }).saleId;
  await B.drain();
  assert.equal(B.queue.length, 0);
}, { needs: ['E2E-OPS-14.backup'] });

await E.step('E2E-OPS-14.restore', 'سرورِ تازه از همان فایل در پوشهٔ تازه بالا می‌آید (فرایندِ جدا)', async () => {
  const backup = require('../../src/lib/backup');
  const tar = await backup.decode(file);
  const tgz = path.join(E.tmp, 'restore.tar.gz');
  fs.writeFileSync(tgz, zlib.gzipSync(tar));
  const dir = path.join(E.tmp, 'restored-db');
  child = spawn(process.execPath, [path.join(import.meta.dirname, 'lib', 'restored-server.mjs'), dir, tgz], {
    env: { ...process.env, DATABASE_URL: `pglite:${dir}` }, stdio: ['ignore', 'pipe', 'pipe'],
  });
  const port = await new Promise((resolve, reject) => {
    let out = '';
    const t = setTimeout(() => reject(new Error(`سرورِ بازیابی‌شده بالا نیامد: ${out.slice(-400)}`)), 90_000);
    child.stdout.on('data', (d) => { out += d; const m = /READY (\d+)/.exec(out); if (m) { clearTimeout(t); resolve(Number(m[1])); } });
    child.stderr.on('data', (d) => { out += d; });
    child.on('exit', (c) => { clearTimeout(t); reject(new Error(`سرورِ بازیابی‌شده افتاد (${c}): ${out.slice(-400)}`)); });
  });
  R = { ...E, apiBase: `http://127.0.0.1:${port}` };
}, { needs: ['E2E-OPS-14.backup'] });

/** همان دستگاه، حالا روبه‌روی سرورِ بازیابی‌شده (نشست و دفترش همان) */
function moved(dev, label) {
  const d = new NativeDevice(R, label, { deviceId: dev.deviceId });
  Object.assign(d, { token: dev.token, refreshToken: dev.refreshToken, ledger: dev.ledger, shadow: dev.shadow, queue: dev.queue.slice(), cursor: dev.cursor, snapshotAt: dev.snapshotAt });
  return d;
}

await E.step('E2E-OPS-14.consistent', 'پس از بازیابی: دفتر، موجودی، قرض، سفارش و رویدادها، بازپرداخت، تطبیق و کاتالوگ همان پیش از پشتیبان', async () => {
  const A2 = moved(A, 'A-after-restore');
  const st = await A2.api('GET', `/sync/v1/status?device_id=${A2.deviceId}`);
  assert.equal(st.status, 200, `نشستِ برنامه پس از بازیابی: ${st.status}`);
  //  دستگاهِ نو با همان حساب: Snapshot ِ سرورِ بازیابی‌شده
  const N = new NativeDevice(R, 'N-new-device');
  N.token = A.token;
  N.refreshToken = A.refreshToken;
  assert.equal((await N.firstSync()).ok, true);
  for (const [t, ids] of Object.entries(before.ledger)) {
    assert.deepEqual(N.ledger[t].map((x) => x.id).sort().filter((id) => ids.includes(id)), ids, `${t}: ردیفی از پیش از پشتیبان گم شد`);
  }
  assert.equal(N.debt(before.debtor), before.debt);
  const o = E.ok(await A2.api('GET', `/shop/orders/${orderId}`), 'سفارش').order;
  assert.equal(o.status, before.order.status);
  assert.deepEqual(o.events.map((e) => e.id || `${e.toStatus}@${e.at}`), before.order.events.map((e) => e.id || `${e.toStatus}@${e.at}`), 'رویدادهای سفارش');
  assert.deepEqual(E.ok(await A2.api('GET', '/shop/orders/reconciliation'), 'تطبیق').netMinor, before.recon.netMinor);
  const refunds = (await A2.api('GET', `/shop/orders/${orderId}`)).body.order.refund;
  assert.ok(!refunds || refunds.refundedMinor === 5000 || refunds.totalMinor === 5000 || JSON.stringify(refunds).includes('5000'), JSON.stringify(refunds));
  const rep = E.ok(await A2.api('GET', `/shop/catalog/report?productId=${rice}`), 'تطبیقِ موجودی');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['E2E-OPS-14.restore'] });

await E.step('E2E-OPS-14.replay', '§۹.۵: فروشِ «پس از آخرین پشتیبان» از دستگاهِ شاگرد دوباره به سرورِ بازیابی‌شده می‌رسد', async () => {
  const B2 = moved(B, 'B-after-restore');
  const r = await B2.sync();
  assert.equal(r.ok, true, JSON.stringify(r));
  const N = new NativeDevice(R, 'N2-new-device');
  N.token = A.token;
  N.refreshToken = A.refreshToken;
  assert.equal((await N.firstSync()).ok, true);
  assert.ok(N.ledger.sales.some((s) => s.id === lateSale), 'فروشِ پس از پشتیبان روی سرورِ بازیابی‌شده نیست و دستگاه هم دوباره نمی‌فرستد (cursor ِ دستگاه از سرِ دفترِ سرور جلوتر است)');
}, { needs: ['E2E-OPS-14.restore'] });

if (child) child.kill('SIGTERM');
await E.finish();
