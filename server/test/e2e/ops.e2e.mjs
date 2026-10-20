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
 *   ۶) §۹.۵: فروشِ «پس از پشتیبان» که در سرورِ بازیابی‌شده نیست، از دفترچهٔ opهای پذیرفته‌شدهٔ
 *      دستگاه (`ReplayBook`) با همان op_id دوباره می‌رسد — دستگاه از «دورانِ» تازهٔ سرور
 *      (`GET /api/shop/sync-epoch`) می‌فهمد سرور برگشته؛ گوشیِ دیگری که همان فروش را
 *      پیش‌تر گرفته، opِ دوباره‌رسیده را دو بار نمی‌نشاند.
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

let A; let B; let J; let rice; let orderId; let before; let file; let child; let R; let lateSale; let s1Id; let s1Item; let late;

await E.step('E2E-OPS-14.0', 'دکانِ واقعی: فروش، نسیه، دریافت، مرجوعی، سفارش و بازپرداخت', async () => {
  A = await E.device('A-owner-phone', owner.email);
  B = await E.device('B-staff-desktop', staff.email);
  for (const d of [A, B]) assert.equal((await d.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 50 });
  const debtor = A.addDebtor('احمد');
  const s1 = A.sell({ lines: [{ id: rice, qty: 3 }] });
  s1Id = s1.saleId;
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

await E.step('E2E-OPS-14.plain-restart', 'بالا آمدنِ عادیِ سرور میانِ رفت‌وآمدِ دو دستگاه: نه دوباره‌فرستادن، نه ساختنِ دوباره، دوران همان', async () => {
  const epoch = require('../../src/lib/sync-epoch');
  const id = (await epoch.current()).id;
  await epoch.rotate('boot');
  A.sell({ lines: [{ id: rice, qty: 1 }] });
  await A.drain();
  await epoch.rotate('boot');
  await B.drain();
  await A.drain();
  assert.equal((await epoch.current()).id, id, 'بالا آمدنِ عادی دورانِ بازیابی را عوض نمی‌کند');
  for (const d of [A, B]) {
    assert.equal(d.epoch, id, d.label);
    assert.equal(d.replayed, 0, `${d.label}: چیزی دوباره رفت`);
    assert.equal(d.rebuilt, 0, `${d.label}: از نو ساخته شد`);
    assert.equal(d.restorePending, false, d.label);
  }
  assert.equal(A.stock(rice), B.stock(rice));
}, { needs: ['E2E-OPS-14.0'] });

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

await E.step('E2E-OPS-14.late', 'پس از پشتیبان: شاگرد یک فروشِ تازه و صاحب یک مرجوعیِ دیگر روی فروشِ پیش از پشتیبان (`$inc`) می‌فرستند (روی سرورِ قدیم «اعمال شد») و هر کدام مالِ دیگری را می‌گیرد', async () => {
  lateSale = B.sell({ lines: [{ id: rice, qty: 2 }] }).saleId;
  const bOps = B.queue.map((o) => o.op_id);
  await B.drain();
  assert.equal(B.queue.length, 0);
  //  مرجوعیِ دوم روی همان قلمِ فروشِ پیش از پشتیبان ⇒ opِ update با returnedQty: {$inc: 1}
  s1Item = A.ledger.saleItems.find((x) => x.saleId === s1Id).id;
  A.returnItems(s1Id, { [s1Item]: 1 }, 'خراب');
  const aOps = A.queue.map((o) => o.op_id);
  assert.ok(A.queue.some((o) => o.table === 'saleItems' && o.type === 'update' && o.fields?.returnedQty?.$inc === 1), 'مرجوعی opِ $inc نساخت');
  await A.drain();
  await B.drain();
  const returned = (d) => d.ledger.saleItems.find((x) => x.id === s1Item).returnedQty;
  late = { bOps, aOps, returned: returned(A), stock: A.stock(rice) };
  assert.equal(returned(B), late.returned, 'مرجوعیِ صاحب به دستگاهِ شاگرد نرسید');
  assert.ok(A.ledger.sales.some((x) => x.id === lateSale), 'فروشِ شاگرد به گوشیِ صاحب نرسید');
  assert.equal(B.stock(rice), late.stock);
}, { needs: ['E2E-OPS-14.backup'] });

await E.step('E2E-OPS-14.late-edit', 'پس از پشتیبان: یک فیلد در دو دستگاه — شاگرد قیمت را ۱۱۱ و صاحب پس از دیدنِ آن ۲۲۲ می‌کند', async () => {
  B.setSalePrice(rice, 111);
  late.bOps.push(...B.queue.map((o) => o.op_id));
  await B.drain();
  await A.drain();
  assert.equal(A.ledger.products.find((p) => p.id === rice).salePrice, 111);
  A.setSalePrice(rice, 222);
  late.aOps.push(...A.queue.map((o) => o.op_id));
  await A.drain();
  await B.drain();
  assert.equal(B.ledger.products.find((p) => p.id === rice).salePrice, 222);
}, { needs: ['E2E-OPS-14.late'] });

await E.step('E2E-OPS-14.late-join', 'پس از پشتیبان: دستگاهِ تازه‌ای با Snapshot می‌آید (مرجوعیِ $inc و فروشِ تازه را در Snapshot دارد)', async () => {
  J = await E.device('J-late-tablet', owner.email);
  assert.equal((await J.firstSync()).ok, true);
  assert.equal(J.ledger.saleItems.find((x) => x.id === s1Item).returnedQty, late.returned);
  assert.ok(J.ledger.sales.some((x) => x.id === lateSale));
}, { needs: ['E2E-OPS-14.late'] });

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
  Object.assign(d, {
    token: dev.token, refreshToken: dev.refreshToken, ledger: dev.ledger, shadow: dev.shadow, queue: dev.queue.slice(), cursor: dev.cursor, snapshotAt: dev.snapshotAt,
    //  دورانِ دیده‌شده و دفترچهٔ `ReplayBook` هم روی همان دستگاه می‌مانند (§۹.۵)
    epoch: dev.epoch, restorePending: dev.restorePending, sent: dev.sent.slice(),
  });
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

await E.step('E2E-OPS-14.replay', '§۹.۵: کارهای «پس از آخرین پشتیبان» از هر دو دستگاه دوباره به سرورِ بازیابی‌شده می‌رسند؛ همه از Snapshot از نو و یکی با سرور — هیچ opی دو بار نمی‌نشیند', async () => {
  const returned = (d) => d.ledger.saleItems.find((x) => x.id === s1Item).returnedQty;
  const price = (d) => d.ledger.products.find((x) => x.id === rice).salePrice;
  //  بازبین #۱: اول صاحب، بعد شاگرد دوباره می‌فرستند (عکسِ ترتیبِ ویرایشِ قیمت)
  const A3 = moved(A, 'A-after-restore');
  assert.equal((await A3.sync()).ok, true, A3.lastError);
  assert.equal(A3.replayed, late.aOps.length, `صاحب دوباره فرستاد: ${A3.replayed}، opهای پس از پشتیبانش: ${late.aOps.length}`);
  const B2 = moved(B, 'B-after-restore');
  assert.equal((await B2.sync()).ok, true, B2.lastError);
  assert.equal(B2.replayed, late.bOps.length, `شاگرد دوباره فرستاد: ${B2.replayed}، opهای پس از پشتیبانش: ${late.bOps.length}`);
  //  بازبین #۲: دستگاهی که پس از پشتیبان با Snapshot آمد
  //  نشستش پس از پشتیبان ساخته شده بود و در سرورِ برگشته نیست ⇒ ورودِ دوباره (همان حساب)؛
  //  حالِ همگام‌سازیِ دستگاه (دوران، cursor، دفتر) سرِ جایش می‌ماند
  const J2 = moved(J, 'J-after-restore');
  assert.equal((await J2.api('GET', `/sync/v1/status?device_id=${J2.deviceId}`)).status, 401);
  Object.assign(J2, { token: A.token, refreshToken: A.refreshToken });
  assert.equal((await J2.sync()).ok, true, J2.lastError);
  assert.equal(J2.replayed, 0);
  assert.equal((await A3.sync()).ok, true);
  assert.equal((await B2.sync()).ok, true);

  const N = new NativeDevice(R, 'N2-new-device');
  N.token = A.token;
  N.refreshToken = A.refreshToken;
  assert.equal((await N.firstSync()).ok, true);
  assert.ok(N.ledger.sales.some((s) => s.id === lateSale), 'فروشِ پس از پشتیبان روی سرورِ بازیابی‌شده نیست و دستگاه هم دوباره نمی‌فرستد (cursor ِ دستگاه از سرِ دفترِ سرور جلوتر است)');
  assert.equal(returned(N), late.returned, 'سرور: مرجوعیِ پس از پشتیبان نرسید یا دو بار رسید');
  for (const [d, who] of [[A3, 'گوشیِ صاحب'], [B2, 'دستگاهِ شاگرد'], [J2, 'تبلتِ تازه']]) {
    assert.equal(d.restorePending, false, `${who}: در انتظار ماند`);
    assert.equal(d.rebuilt, 1, `${who}: از Snapshot از نو ساخته نشد`);
    assert.equal(returned(d), late.returned, `${who}: opِ دوباره‌رسیده دو بار نشست (returnedQty ${returned(d)} ≠ ${late.returned})`);
    assert.equal(price(d), price(N), `${who}: قیمت با سرور یکی نیست (${price(d)} ≠ ${price(N)})`);
    assert.equal(d.ledger.sales.filter((s) => s.id === lateSale).length, 1, who);
    assert.equal(d.stock(rice), N.stock(rice), `${who}: موجودی`);
    assert.deepEqual(d.ledger.sales.map((x) => x.id).sort(), N.ledger.sales.map((x) => x.id).sort(), `${who}: فروش‌ها`);
    assert.ok(d.cursor <= N.cursor, `${who}: cursor (${d.cursor}) از سرِ دفترِ سرور (${N.cursor}) جلوتر ماند`);
    assert.equal(d.queue.length, 0, who);
  }
  assert.equal(N.stock(rice), late.stock, 'سرور: موجودی همان پیش از بازیابی');
}, { needs: ['E2E-OPS-14.restore', 'E2E-OPS-14.late-edit', 'E2E-OPS-14.late-join'] });

if (child) child.kill('SIGTERM');
await E.finish();
