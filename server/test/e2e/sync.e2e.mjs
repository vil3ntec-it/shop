/**
 * سناریوی سرتاسریِ همگام‌سازی — E2E-SYNC-12، S26 (کوچک‌شده)، S27، SYN-10 و جداییِ دکان‌ها.
 *
 * دستگاه‌ها «برنامهٔ دکان» در سطحِ پروتکل‌اند (`lib/native.mjs`): A گوشیِ صاحب، B کامپیوترِ
 * شاگرد (هر دو v1)، D گوشیِ سوم روی Sync v3 (`license/sync-v3-engine.js`، قرینهٔ
 * `SyncV3Engine.kt`)، و C دستگاهِ دکانِ دیگر. سرورِ واقعی روی PGlite.
 *
 *   ۱) ورود با کدِ ایمیل و نخستین همگام‌سازی (Snapshot ِ خالی)
 *   ۲) کالا و موجودی از A به B
 *   ۳) A بی اینترنت دو فروش ⇒ صف پُر، سرور خالی، و «خروج» بسته (LogoutGuard)
 *   ۴) B آنلاین می‌فروشد
 *   ۵) اینترنتِ A برمی‌گردد ⇒ هر دو دستگاه و سرور یک عدد؛ خروج باز
 *   ۶) جوابِ push گم می‌شود (سرور گرفته، دستگاه نشنیده) ⇒ ارسالِ دوباره فقط «تکراری»
 *   ۷) S26 کوچک‌شده: صدها op ِ آفلاین (فروش، نسیه، دریافت، مرجوعی) ⇒ بی تکرار و بی گم‌شدن؛
 *      مانده‌ها و موجودی‌ها روی هر دو دستگاه و سرور برابر
 *   ۸) دکانِ دیگر هیچ چیز نمی‌بیند و نمی‌تواند بنویسد
 *   ۹) S27: هر دو دستگاه آفلاین آخرین واحد ⇒ هر دو پذیرفته، −۱، و وظیفهٔ بررسی (پرچمِ shop.catalog)
 *  ۱۰) دستگاهِ v3 کنارِ v1 (پرچمِ shop.sync_v3_to_v1): فروشِ هر کدام به دیگری می‌رسد
 *
 * اجرا:  node test/e2e/sync.e2e.mjs      (همه: npm run test:e2e)
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { boot, assert } from './lib/harness.mjs';
import { core, MODELS, row, emptyLedger } from './lib/native.mjs';

const require = createRequire(import.meta.url);
const E = await boot({ name: 'sync', env: { SYNC_V3_ENABLED: '1' } });
const { db } = E;

const owner = await E.owner('owner-sync@e2e.local', 'صاحبِ یک', 'دکانِ یک');
const staff = await E.staff(owner, 'staff-sync@e2e.local', 'شاگردِ یک');
const other = await E.owner('owner-two@e2e.local', 'صاحبِ دو', 'دکانِ دو');

const rows = (shopId, table) => db.many(
  `SELECT row_id, data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND deleted_at IS NULL`,
  [shopId, table],
);
/** موجودی همان‌طور که `ShopStore.stock` می‌شمارد، ولی از ردیف‌های سرور */
async function serverStock(shopId, productId) {
  const ledger = { warehouseEntries: (await rows(shopId, 'warehouseEntries')).map((r) => r.data), sales: (await rows(shopId, 'sales')).map((r) => ({ id: r.row_id, ...r.data })), saleItems: (await rows(shopId, 'saleItems')).map((r) => r.data) };
  const entered = ledger.warehouseEntries.filter((e) => e.productId === productId).reduce((s, e) => s + e.units, 0);
  const cancelled = new Set(ledger.sales.filter((s) => s.status === 'cancelled').map((s) => s.id));
  return entered - ledger.saleItems.filter((i) => i.productId === productId && !cancelled.has(i.saleId)).reduce((s, i) => s + i.quantity - i.returnedQty, 0);
}
async function serverDebt(shopId, debtorId) {
  return (await rows(shopId, 'transactions')).map((r) => r.data).filter((t) => t.debtorId === debtorId).reduce((s, t) => s + (t.type === 'give' ? t.amount : -t.amount), 0);
}
const ids = (dev, t) => dev.ledger[t].map((x) => x.id).sort();

let A; let B; let C; let chai;
const offline = [];

await E.step('E2E-SYNC.shapes', 'شکلِ ردیف‌های دستگاهِ آزمون از خودِ Models.kt برنامه خوانده می‌شود (بی جابه‌جایی)', async () => {
  for (const cls of ['Product', 'WarehouseEntry', 'Sale', 'SaleItem', 'SaleReturn', 'Debtor', 'DebtTransaction', 'StockMovement', 'AuditEntry']) {
    assert.ok(MODELS[cls] && MODELS[cls].length >= 5, `${cls} در Models.kt خوانده نشد`);
  }
  const sale = row('Sale', { id: 'x' });
  for (const k of ['invoiceNumber', 'finalTotal', 'paidAmount', 'debtGiven', 'debtSettled', 'syncStatus', 'seller']) assert.ok(k in sale, `Sale.${k}`);
  assert.equal('series' in sale, false, '@EncodeDefault(NEVER) با null نوشته نمی‌شود');
  assert.throws(() => row('Sale', { id: 'x', notInModel: 1 }), /در Models.kt نیست/);
  const empty = emptyLedger();
  assert.deepEqual(Object.keys(empty).filter((k) => Array.isArray(empty[k]) && core.COLLECTIONS.includes(k)).sort(), [...core.COLLECTIONS].sort());
  assert.ok(empty.productUnits.length > 0, 'واحدهای پیش‌فرضِ ShopData');
  const kt = fs.readFileSync(path.join(import.meta.dirname, '..', '..', '..', 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'sync', 'v1', 'LedgerDiff.kt'), 'utf8');
  assert.equal(core.SCHEMA_VERSION, Number(/SCHEMA_VERSION = (\d+)/.exec(kt)[1]), 'همان LedgerDiff.SCHEMA_VERSION ِ برنامه');
  assert.deepEqual([...core.COLLECTIONS].sort(), [...kt.match(/val COLLECTIONS = listOf\(([\s\S]*?)\)/)[1].matchAll(/"(\w+)"/g)].map((m) => m[1]).sort(), 'همان LedgerDiff.COLLECTIONS');
});

await E.step('E2E-SYNC-12.1', 'A، B و C با کدِ ایمیل وارد می‌شوند و نخستین همگام‌سازی (دفترِ خالی ⇐ Snapshot)', async () => {
  A = await E.device('A-owner-phone', owner.email);
  B = await E.device('B-staff-desktop', staff.email);
  C = await E.device('C-other-shop', other.email);
  for (const d of [A, B, C]) {
    const r = await d.firstSync();
    assert.equal(r.ok, true, `${d.label}: ${JSON.stringify(r)}`);
    assert.ok(d.snapshotAt > 0, `${d.label} از Snapshot شروع نکرد`);
  }
});

await E.step('E2E-SYNC-12.2', 'کالا و موجودی از A به B می‌رسد (همان opها و همان هشِ برنامه)', async () => {
  chai = A.stockIn({ name: 'چای سبز', price: 50, cost: 30, units: 10 });
  assert.ok(A.queue.length >= 3, 'کالا + ورود + حرکت باید op شده باشند');
  for (const op of A.queue) assert.equal(op.hash, core.hashOf(op), 'هشِ op همان OpHash.kt');
  await A.drain();
  await B.drain();
  assert.ok(B.ledger.products.some((p) => p.id === chai), 'کالای A روی B نیست');
  assert.equal(B.stock(chai), 10);
  assert.equal(await serverStock(owner.shopId, chai), 10);
}, { needs: ['E2E-SYNC-12.1'] });

await E.step('E2E-SYNC-12.3', 'A بی اینترنت دو فروش می‌کند ⇐ صف پُر، سرور خالی، «خروج» بسته (SYN-10)', async () => {
  A.offline = true;
  for (const q of [3, 1]) {
    const r = A.sell({ lines: [{ id: chai, qty: q }] });
    assert.equal(r.ok, true, r.message);
    offline.push(r.saleId);
  }
  const r = await A.sync();
  assert.equal(r.ok, false);
  assert.ok(A.queue.length >= 2 * 4, `صف: ${A.queue.length}`);
  assert.equal(A.stock(chai), 6);
  assert.equal((await rows(owner.shopId, 'sales')).length, 0, 'پیش از وصل شدن فروشی به سرور رسید');
  const v = A.logoutVerdict();
  assert.equal(v.block, true, 'با صفِ پُر «خروج» باید بسته باشد');
  const out = await A.logout();
  assert.equal(out.blocked, true);
  assert.equal(out.pending, A.queue.length);
}, { needs: ['E2E-SYNC-12.2'] });

let bSale;
await E.step('E2E-SYNC-12.4', 'B در همان حال آنلاین می‌فروشد', async () => {
  const r = B.sell({ lines: [{ id: chai, qty: 2 }] });
  assert.equal(r.ok, true, r.message);
  bSale = r.saleId;
  await B.drain();
  assert.deepEqual((await rows(owner.shopId, 'sales')).map((x) => x.row_id), [bSale]);
}, { needs: ['E2E-SYNC-12.2'] });

await E.step('E2E-SYNC-12.5', 'اینترنتِ A برمی‌گردد ⇐ A، B و سرور یک عدد؛ «خروج» باز', async () => {
  A.offline = false;
  await A.drain();
  await B.drain();
  const all = [...offline, bSale].sort();
  assert.deepEqual(ids(A, 'sales'), all);
  assert.deepEqual(ids(B, 'sales'), all);
  assert.deepEqual((await rows(owner.shopId, 'sales')).map((x) => x.row_id).sort(), all);
  assert.equal(A.stock(chai), 4);
  assert.equal(B.stock(chai), 4);
  assert.equal(await serverStock(owner.shopId, chai), 4);
  assert.equal(A.logoutVerdict().block, false, 'صفِ خالی ⇐ خروج باز');
}, { needs: ['E2E-SYNC-12.3', 'E2E-SYNC-12.4'] });

await E.step('E2E-SYNC-12.6', 'جوابِ push گم می‌شود (سرور گرفته، دستگاه نشنیده) ⇐ ارسالِ دوباره «تکراری»؛ یک فروش، یک اثر', async () => {
  const r = A.sell({ lines: [{ id: chai, qty: 1 }] });
  const sent = A.queue.slice();
  //  همان بدنهٔ `SyncV1Client.push` به سرور می‌رسد، ولی پاسخش به دستگاه نمی‌رسد ⇒ صف دست‌نخورده
  const lost = await A.api('POST', '/sync/v1/push', { device_id: A.deviceId, schema_version: core.SCHEMA_VERSION, queued: sent.length, ops: sent });
  assert.equal(lost.status, 200);
  assert.ok(lost.body.results.every((x) => x.status === 'applied'), JSON.stringify(lost.body.results));
  assert.equal(A.queue.length, sent.length, 'صف نباید پاک شده باشد');
  const again = await A.api('POST', '/sync/v1/push', { device_id: A.deviceId, schema_version: core.SCHEMA_VERSION, queued: sent.length, ops: sent });
  assert.ok(again.body.results.every((x) => x.status === 'duplicate'), JSON.stringify(again.body.results));
  await A.drain();
  const ops = await db.many(`SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND table_name='sales' AND row_id=$2`, [owner.shopId, r.saleId]);
  assert.equal(ops[0].n, 1, `یک op برای این فروش، نه ${ops[0].n}`);
  await B.drain();
  assert.equal(B.ledger.sales.filter((s) => s.id === r.saleId).length, 1);
  assert.equal(B.stock(chai), 3);
  assert.equal(await serverStock(owner.shopId, chai), 3);
}, { needs: ['E2E-SYNC-12.5'] });

await E.step('S26', 'S26 (کوچک‌شده): صدها op ِ آفلاین روی دو دستگاه ⇐ همه بی تکرار؛ مانده و موجودی همه‌جا برابر', async () => {
  const rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 1000 });
  const debtor = A.addDebtor('احمد کریمی', '0791234567');
  await A.drain();
  await B.drain();
  A.offline = true;
  B.offline = true;
  const credit = [];
  for (let i = 0; i < 40; i++) {
    const a = A.sell({ lines: [{ id: rice, qty: 1 + (i % 3) }] });
    assert.ok(a.ok, a.message);
    const b = B.sell({ lines: [{ id: rice, qty: 2 }], payment: i % 4 === 0 ? 'credit' : 'cash', debtorId: i % 4 === 0 ? debtor : null, paid: i % 4 === 0 ? 50 : null });
    assert.ok(b.ok, b.message);
    if (i % 4 === 0) credit.push(b.saleId);
  }
  for (let i = 0; i < 5; i++) assert.ok(A.receive(debtor, 20).ok);
  const sid = credit[0];
  const item = B.ledger.saleItems.find((x) => x.saleId === sid);
  assert.ok(B.returnItems(sid, { [item.id]: 1 }, 'خراب').ok);
  const queued = A.queue.length + B.queue.length;
  A.offline = false;
  B.offline = false;
  assert.ok(queued > 300, `opهای آفلاین: ${queued}`);
  await A.drain();
  await B.drain();
  await A.drain();
  const opCount = (await db.many(`SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1`, [owner.shopId]))[0].n;
  const distinct = (await db.many(`SELECT COUNT(DISTINCT op_id)::int AS n FROM oplog WHERE account_id=$1`, [owner.shopId]))[0].n;
  assert.equal(opCount, distinct, 'op ِ تکراری در دفترِ سرور');
  assert.deepEqual(ids(A, 'sales'), ids(B, 'sales'));
  assert.deepEqual(ids(A, 'transactions'), ids(B, 'transactions'));
  assert.equal(A.stock(rice), B.stock(rice));
  assert.equal(A.stock(rice), await serverStock(owner.shopId, rice));
  assert.equal(A.debt(debtor), B.debt(debtor));
  assert.equal(A.debt(debtor), await serverDebt(owner.shopId, debtor));
  //  ۱۰ نسیه × ۱۵۰ = ۱٬۵۰۰ − ۵ × ۲۰ − مرجوعیِ ۱ × ۱۰۰ (از ماندهٔ همان فاکتور) = ۱٬۳۰۰
  assert.equal(A.debt(debtor), 1300);
}, { needs: ['E2E-SYNC-12.5'] });

await E.step('E2E-SEC-13.sync', 'دکانِ دیگر (C) هیچ چیز از این دکان نمی‌بیند و با همان row_id هم در آن نمی‌نویسد', async () => {
  await C.drain();
  assert.equal(C.ledger.products.length, 0);
  assert.equal(C.ledger.sales.length, 0);
  const snap = await C.api('GET', '/sync/v1/snapshot');
  assert.equal(JSON.stringify(snap.body).includes(chai), false, 'Snapshot ِ دکانِ دو چیزی از دکانِ یک داد');
  //  دستگاهِ دکانِ دو با همان شناسهٔ کالا op می‌فرستد ⇒ فقط در دفترِ خودش می‌نشیند
  const d = JSON.parse(JSON.stringify(C.ledger));
  d.products.push({ ...A.ledger.products.find((p) => p.id === chai), salePrice: 1 });
  C.save(d);
  await C.drain();
  const mine = (await rows(owner.shopId, 'products')).find((r) => r.row_id === chai);
  assert.equal(mine.data.salePrice, 50, 'دکانِ دو قیمتِ کالای دکانِ یک را عوض کرد');
  await A.drain();
  assert.equal(A.ledger.products.find((p) => p.id === chai).salePrice, 50);
}, { needs: ['E2E-SYNC-12.2'] });

let last;
await E.step('S27', 'S27: هر دو دستگاه آفلاین آخرین واحد را می‌فروشند ⇐ هر دو پذیرفته، موجودی −۱ روی هر دو و سرور', async () => {
  await E.flag(owner.shopId, 'shop.orders', true);
  await E.flag(owner.shopId, 'shop.catalog', true);
  last = A.stockIn({ name: 'روغن آخر', price: 300, cost: 250, units: 1 });
  await A.drain();
  await B.drain();
  A.offline = true;
  B.offline = true;
  const sa = A.sell({ lines: [{ id: last, qty: 1 }] });
  const sb = B.sell({ lines: [{ id: last, qty: 1 }] });
  assert.ok(sa.ok && sb.ok);
  A.offline = false;
  B.offline = false;
  await A.drain();
  await B.drain();
  await A.drain();
  const srv = (await rows(owner.shopId, 'sales')).map((r) => r.row_id);
  assert.ok(srv.includes(sa.saleId) && srv.includes(sb.saleId), 'هیچ فروشی نباید رد یا حذف شود');
  assert.equal(A.stock(last), -1);
  assert.equal(B.stock(last), -1);
  assert.equal(await serverStock(owner.shopId, last), -1);
}, { needs: ['E2E-SYNC-12.5'] });

await E.step('S27.task', 'S27: «وظیفهٔ بررسیِ موجودی» برای مدیر ساخته می‌شود و کاتالوگِ سرور همان −۱ را دارد', async () => {
  const st = E.ok(await A.api('GET', `/shop/catalog/products/${last}`), 'کالا در کاتالوگ');
  assert.equal(st.product.stock.levels.shop_floor, -1000, JSON.stringify(st.product.stock));
  const tasks = E.ok(await A.api('GET', '/shop/catalog/tasks?status=open'), 'وظیفه‌ها').tasks;
  assert.ok(tasks.some((t) => t.kind === 'negative_stock' && t.productId === last), JSON.stringify(tasks));
  const rep = E.ok(await A.api('GET', `/shop/catalog/report?productId=${last}`), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['S27'] });

await E.step('E2E-SYNC-12.v3', 'دستگاهِ v3 کنارِ v1 (پرچمِ shop.sync_v3_to_v1): فروشِ هر کدام به دیگری و به سرور می‌رسد', async () => {
  await E.flag(owner.shopId, 'shop.sync_v3_to_v1', true);
  const V3 = require(path.join(import.meta.dirname, '..', '..', '..', 'license', 'sync-v3-engine.js'));
  const D = await E.device('D-v3-phone', owner.email);
  let state = core.cloneLedger({});
  const ledger = { read: () => state, write: (x) => { state = core.cloneLedger(x); } };
  const engine = new V3.Engine({ transport: V3.FetchTransport(E.apiBase, D.token), store: new V3.MemoryStore(), ledger, deviceId: D.deviceId });
  await engine.load();
  let r = await engine.sync();
  assert.equal(r.ok, true, JSON.stringify(r));
  assert.ok((state.products || []).some((p) => p.id === chai), 'Snapshot ِ v3 کالای v1 را ندارد');
  //  فروش روی دستگاهِ v3 — همان شکلِ ردیفِ برنامه
  const N = await import('./lib/native.mjs');
  const tmp = new N.NativeDevice(E, 'D-shape');
  tmp.ledger = { ...N.emptyLedger(), ...core.cloneLedger(state) };
  tmp.shadow = core.shadowOf(tmp.ledger);
  const s = tmp.sell({ lines: [{ id: chai, qty: 1 }] });
  assert.ok(s.ok, s.message);
  state = core.cloneLedger(tmp.ledger);
  r = await engine.sync();
  assert.equal(r.ok, true, JSON.stringify(r));
  assert.equal(engine.queued(), 0, JSON.stringify(engine.rejected()));
  await A.drain();
  assert.ok(A.ledger.sales.some((x) => x.id === s.saleId), 'فروشِ دستگاهِ v3 به دستگاهِ v1 نرسید');
  //  و برعکس: فروشِ v1 به v3
  const a = A.sell({ lines: [{ id: chai, qty: 1 }] });
  await A.drain();
  r = await engine.sync();
  assert.equal(r.ok, true, JSON.stringify(r));
  assert.ok((state.sales || []).some((x) => x.id === a.saleId), 'فروشِ v1 به دستگاهِ v3 نرسید');
  const saleV3 = (state.sales || []).find((x) => x.id === a.saleId);
  assert.equal(saleV3.finalTotal, 50, 'مبلغ در گذر از پل عوض شد');
}, { needs: ['E2E-SYNC-12.5'] });

await E.finish();
