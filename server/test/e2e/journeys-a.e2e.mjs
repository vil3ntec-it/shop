/**
 * سفرهای واقعیِ دکان (بخشِ نخست) — هر سفر از چند دستگاه و چند حساب می‌گذرد و در پایان **روی هر دستگاه و سرور**
 * می‌سنجد که هیچ چیزی گم نشده، هیچ چیزی دو بار نیست و جمع‌ها درست است (`lib/journey.mjs`).
 *
 *   J1  دکانِ تازه: ثبت‌نام، ساختنِ دکان، ۲۰ کالا با موجودی، فروشِ نقد و نسیه ⇐ خروج ⇐ ورود روی دستگاهِ دوم ⇐ همه یکی.
 *   J2  شاگرد می‌پیوندد: کدِ شاگرد ⇐ ورود روی دستگاهِ B با همان کد ⇐ فروشِ آنلاین و آفلاین ⇐ صاحب روی A همان فروش‌ها و
 *       حرکتِ موجودی را می‌بیند؛ مرزِ نقش (کد ساختن، نقش عوض کردن، لغوِ فاکتور با برنامهٔ دست‌کاری‌شده).
 *   J2b همان گوشی (گزارشِ صاحب): صاحب خارج می‌شود و شاگرد با کدِ شاگرد روی **همان گوشی** وارد می‌شود و می‌فروشد؛ صاحب
 *       برمی‌گردد و می‌فروشد ⇐ هیچ فاکتوری شمارهٔ تکراری ندارد، هیچ چیزی گم نشده.
 *   J3  چرخهٔ قرض: فروشِ نسیه ⇐ پرداختِ جزئی روی دو دستگاه هم‌زمان (آفلاین) ⇐ تسویهٔ کامل ⇐ مانده همه‌جا صفر.
 *   J4  جابه‌جاییِ دستگاه: صاحب برنامه را پاک و دوباره نصب می‌کند (دفترِ محلی هیچ) ⇐ ورود ⇐ همه برمی‌گردد و فروشِ
 *       تازه شمارهٔ تکراری نمی‌سازد.
 *   J6  INV-10: فروشِ آفلاینِ A پیش از شمارش ⇐ B قفسه را ۱۷ می‌شمارد و ثبت می‌شود ⇐ A دیر می‌فرستد ⇐ A، B و سرور
 *       (دفترِ همگام‌سازی و موجودیِ کاتالوگ) همه ۱۷، نه ۱۴؛ فروش در دفتر می‌ماند و جذب در سابقه دیده می‌شود.
 *   J5  دو دستگاه قیمتِ یک کالا را آفلاین عوض می‌کنند ⇐ یکی برنده، بازنده در صندوقِ تعارضِ سرور و در سابقهٔ هر دو
 *       دستگاه؛ هیچ چیزی بی‌صدا گم نمی‌شود.
 *
 * دستگاه‌ها همان `NativeDevice` (سطحِ پروتکل، سطر به سطرِ موتورهای کاتلین). سرور واقعی روی PGlite.
 * اجرا:  node test/e2e/run.mjs journeys-a
 */
import { boot, assert } from './lib/harness.mjs';
import { NativeDevice, KOTLIN } from './lib/native.mjs';
import { assertSameEverywhere, serverLedger, assertNoDuplicates } from './lib/journey.mjs';

const E = await boot({ name: 'journeys-a' });
const { db } = E;

let owner; let A; let B; let S; let P; let A2;
let products = []; let ahmad;
const sold = new Map();            //  کالا ⇒ فروخته (مستقل از موتور، برای جمعِ مورد انتظار)
const UNITS = 50;
const sell = (dev, lines, extra = {}) => {
  const r = dev.sell({ lines, ...extra });
  assert.equal(r.ok, true, `${dev.label}: ${r.message}`);
  for (const l of lines) sold.set(l.id, (sold.get(l.id) || 0) + l.qty);
  return r;
};
const expectStock = () => Object.fromEntries(products.map((id) => [id, UNITS - (sold.get(id) || 0)]));
const staffCode = async (dev, role = 'staff') => {
  const r = await dev.api('POST', '/shop/staff-code', { role, maxUses: 1 });
  assert.ok([200, 201].includes(r.status), `کدِ شاگرد: ${r.status} ${JSON.stringify(r.body)}`);
  return r.body.code;
};

/* ═══════════════════════════════ J1 ═══════════════════════════════ */

await E.step('J1.signup', 'صاحب ثبت‌نام می‌کند و دکان می‌سازد؛ برنامه روی گوشیِ A وارد و هم‌تراز می‌شود', async () => {
  owner = await E.owner('owner-journey@e2e.local', 'صاحبِ سفر', 'دکانِ سفر');
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  assert.match(await A.ensureSeries(), /^[A-Z][1-9]/);
});

await E.step('J1', 'دکانِ تازه: ۲۰ کالا با موجودی، فروشِ نقد/تخفیف‌دار/نسیه ⇐ خروج ⇐ ورود روی دستگاهِ دوم ⇐ همه یکی', async () => {
  for (let i = 0; i < 20; i++) {
    products.push(A.stockIn({ name: `کالای ${i + 1}`, price: 100 + i * 10, cost: 70 + i * 5, units: UNITS, category: i % 2 ? 'خوراکی' : 'شوینده' }));
  }
  ahmad = A.addDebtor('احمد', '0791110001');
  sell(A, [{ id: products[0], qty: 2 }, { id: products[1], qty: 1 }]);
  sell(A, [{ id: products[2], qty: 5 }]);
  sell(A, [{ id: products[3], qty: 1 }, { id: products[4], qty: 3 }], { discountValue: 20 });
  sell(A, [{ id: products[5], qty: 2 }], { discountType: 'percent', discountValue: 10 });
  const credit = sell(A, [{ id: products[6], qty: 4 }, { id: products[7], qty: 1 }], { payment: 'credit', debtorId: ahmad, paid: 200 });
  await A.drain();
  const total = (4 * 160) + 170 - 200;
  assert.equal(credit.finalTotal - 200, total);
  await assertSameEverywhere('J1 · پیش از خروج', [A], { expect: { sales: 5, stock: expectStock(), debt: { [ahmad]: total } } });

  //  خروج: `LogoutGuard` صفِ خالی می‌خواهد (همه رسیده) ⇒ باز
  assert.deepEqual(A.logoutVerdict(), { block: false });
  assert.equal((await A.logout()).blocked, false);
  //  توکنِ باطل‌شده دیگر کار نمی‌کند
  A.token = '';

  //  ورود روی دستگاهِ دوم (کامپیوتر، دفترِ خالی ⇒ Snapshot)
  B = await E.device('B-owner-desktop', owner.email, { appVersion: '1.0.9999' });
  assert.equal((await B.firstSync()).ok, true);
  await assertSameEverywhere('J1 · دستگاهِ دوم', [B, A], { expect: { sales: 5, stock: expectStock(), debt: { [ahmad]: total } } });
  assert.equal(B.ledger.products.length, 20);

  //  صاحب دوباره روی A وارد می‌شود (همان دفتر، همان صف) و می‌فروشد ⇐ B می‌گیرد
  await A.login(owner.email);
  sell(A, [{ id: products[8], qty: 1 }]);
  await A.drain();
  await B.drain();
  await assertSameEverywhere('J1 · ورودِ دوباره', [A, B], { expect: { sales: 6, stock: expectStock() } });
}, { needs: ['J1.signup'] });

/* ═══════════════════════════════ J2 ═══════════════════════════════ */

await E.step('J2', 'شاگرد با کد روی B می‌پیوندد؛ فروشِ آنلاین و آفلاین ⇐ صاحب روی A فروش و حرکتِ موجودی را می‌بیند', async () => {
  const code = await staffCode(A);
  S = new NativeDevice(E, 'S-staff-phone', { seller: 'علی' });
  await S.loginStaff(code, 'علی');
  assert.equal(S.shop?.id, owner.shopId, 'کدِ شاگرد مستقیم به دکانِ کارفرما');
  assert.equal(S.shop?.role, 'staff');
  assert.equal((await S.firstSync()).ok, true);
  await S.ensureSeries();
  assert.notEqual(S.series, A.series, 'هر دستگاه سریِ خودش');
  await assertSameEverywhere('J2 · شاگرد همان دفترِ دکان', [A, S, B]);

  sell(S, [{ id: products[9], qty: 2 }]);
  sell(S, [{ id: products[10], qty: 1 }]);
  await S.drain();
  //  شاگرد بی اینترنت: سه فروش در صف؛ صاحب هم‌زمان روی A می‌فروشد
  S.offline = true;
  sell(S, [{ id: products[0], qty: 1 }]);
  sell(S, [{ id: products[11], qty: 3 }]);
  sell(S, [{ id: products[12], qty: 1 }], { payment: 'credit', debtorId: ahmad, paid: 0 });
  assert.equal(S.logoutVerdict().block, true, 'خروج با صفِ پُر بسته');
  sell(A, [{ id: products[0], qty: 1 }]);
  await A.drain();
  assert.equal((await S.sync()).offline, true);
  assert.ok(S.queue.length > 0);
  S.offline = false;
  await S.drain();
  await A.drain();
  await B.drain();
  const t = await assertSameEverywhere('J2 · پس از برگشتِ اینترنت', [A, S, B], { expect: { sales: 12, stock: expectStock() } });
  //  صاحب فروش‌های شاگرد را با نام و سریِ شاگرد می‌بیند
  const staffSales = A.ledger.sales.filter((s) => s.seller === 'علی');
  assert.equal(staffSales.length, 5);
  assert.ok(staffSales.every((s) => s.series === S.series));
  assert.equal(A.stock(products[0]), UNITS - 2 - 1 - 1);
  assert.equal(t.debt[ahmad], 610 + 220);
  //  حرکتِ موجودیِ فروش‌های شاگرد روی A
  const moves = A.ledger.stockMovements.filter((m) => staffSales.some((s) => s.id === m.refId));
  assert.equal(moves.length, 5);
}, { needs: ['J1'] });

await E.step('J2.roles', 'مرزِ نقشِ شاگرد: کد و نقش نمی‌سازد؛ لغو و تخفیف از برنامهٔ دست‌کاری‌شده رد و همراه‌هایش هم؛ سرور و صاحب دست‌نخورده', async () => {
  assert.equal((await S.api('POST', '/shop/staff-code', { role: 'staff', maxUses: 1 })).status, 403);
  assert.equal((await S.api('PUT', '/shop/permissions/members/x', { perms: { 'sales.cancel': { mode: 'allow' } } })).status, 403);
  //  برنامهٔ دست‌کاری‌شده (رابطِ برنامه دکمهٔ لغو را به شاگرد نشان نمی‌دهد): همان دستگاه، دفترِ جدا
  const T = new NativeDevice(E, 'S-tampered', { deviceId: S.deviceId, seller: 'علی' });
  T.token = S.token;
  T.refreshToken = S.refreshToken;
  assert.equal((await T.firstSync()).ok, true);
  const victim = A.ledger.sales.find((s) => s.seller !== 'علی' && s.status === 'completed');
  assert.equal(T.cancelSale(victim.id, 'دست‌کاری').ok, true);
  const disc = T.sell({ lines: [{ id: products[13], qty: 1 }], discountValue: 30 });
  assert.equal(disc.ok, true);
  const before = await serverLedger(A);
  await T.sync();
  const why = new Map(T.dropped.map((x) => [`${x.table}/${x.row_id}`, x.why]));
  assert.equal(why.get(`sales/${victim.id}`), 'cancel_not_allowed', JSON.stringify([...why]));
  assert.equal(why.get(`sales/${disc.saleId}`), 'discount_not_allowed', JSON.stringify([...why]));
  //  همراه‌های فاکتورِ ردشده (قلم، حرکت، سابقه) هم ننشستند — هیچ ردیفِ یتیمی روی سرور نیست
  const after = await serverLedger(A);
  assert.equal(after.sales.find((s) => s.id === victim.id).status, 'completed');
  assert.equal(after.saleItems.filter((i) => i.saleId === disc.saleId).length, 0, 'قلمِ یتیم');
  assert.equal(after.stockMovements.filter((m) => m.refId === victim.id || m.refId === disc.saleId).length,
    before.stockMovements.filter((m) => m.refId === victim.id || m.refId === disc.saleId).length, 'حرکتِ یتیم');
  await A.drain();
  await S.drain();
  await assertSameEverywhere('J2.roles · صاحب و شاگرد دست‌نخورده', [A, S, B], { expect: { sales: 12, stock: expectStock() } });
}, { needs: ['J2'] });

/* ═══════════════════════════════ J3 ═══════════════════════════════ */

await E.step('J3', 'چرخهٔ قرض: نسیه ⇐ پرداختِ جزئیِ هم‌زمان روی A و B (آفلاین) و شاگرد ⇐ تسویهٔ کامل ⇐ مانده همه‌جا صفر', async () => {
  await A.drain(); await B.drain(); await S.drain();
  const karim = A.addDebtor('کریم', '0792220002');
  const s = sell(A, [{ id: products[18], qty: 5 }], { payment: 'credit', debtorId: karim, paid: 0 });
  assert.equal(s.finalTotal, 5 * 280);
  await A.drain(); await B.drain(); await S.drain();
  assert.equal(B.debt(karim), 1400);
  A.offline = true; B.offline = true;
  assert.equal(A.receive(karim, 300).ok, true);
  assert.equal(B.receive(karim, 200).ok, true);
  assert.equal(A.debt(karim), 1100, 'A فقط پرداختِ خودش را می‌بیند');
  A.offline = false; B.offline = false;
  await A.drain(); await B.drain(); await A.drain();
  assert.equal(S.receive(karim, 100).ok, true, 'شاگرد هم رسید می‌گیرد');
  await S.drain(); await A.drain(); await B.drain();
  await assertSameEverywhere('J3 · پس از سه پرداختِ جزئی', [A, B, S], { expect: { debt: { [karim]: 800 } } });
  assert.equal(B.receive(karim, 800).ok, true);
  await B.drain(); await A.drain(); await S.drain();
  const t = await assertSameEverywhere('J3 · تسویه', [A, B, S], { expect: { debt: { [karim]: 0 } } });
  assert.equal(t.debt[karim], 0);
  const tx = A.ledger.transactions.filter((x) => x.debtorId === karim);
  assert.deepEqual(tx.map((x) => [x.type, x.amount]).sort(), [['give', 1400], ['receive', 100], ['receive', 200], ['receive', 300], ['receive', 800]].sort());
}, { needs: ['J2'] });

/* ═══════════════════════════════ J4 ═══════════════════════════════ */

await E.step('J4', 'جابه‌جاییِ دستگاه: صاحب برنامه را پاک و دوباره نصب می‌کند ⇐ ورود ⇐ همه برمی‌گردد؛ فروشِ تازه بی شمارهٔ تکراری', async () => {
  await A.drain(); await B.drain(); await S.drain();
  const before = await serverLedger(B);
  //  نصبِ تازه: دفتر، صف، سایه و شناسهٔ دستگاه همه نو (`tohid-sync.xml` با پشتیبانِ اندروید برنمی‌گردد)
  A2 = new NativeDevice(E, 'A-owner-phone-reinstalled');
  await A2.login(owner.email);
  assert.equal((await A2.firstSync()).ok, true);
  await A2.ensureSeries();
  await assertSameEverywhere('J4 · پس از نصبِ دوباره', [A2, B, S]);
  for (const c of ['products', 'sales', 'saleItems', 'debtors', 'transactions', 'stockMovements', 'warehouseEntries']) {
    assert.equal(A2.ledger[c].length, before[c].length, `J4: ${c}`);
  }
  sell(A2, [{ id: products[19], qty: 1 }]);
  sell(A2, [{ id: products[0], qty: 1 }]);
  await A2.drain(); await B.drain(); await S.drain();
  await assertSameEverywhere('J4 · فروش پس از نصب', [A2, B, S], { expect: { stock: expectStock() } });
}, { needs: ['J2'] });

/* ═══════════════════════════════ J5 ═══════════════════════════════ */

await E.step('J5.default', 'پیکربندیِ پیش‌فرض (`sync.pull_own_newer` از ۱۴۰۵/۰۸/۱۸ روشن، بی هیچ ردیفِ پرچم): دو دستگاه یک قیمت را آفلاین عوض می‌کنند ⇐ یک قیمت روی هر دو و سرور', async () => {
  //  دو دستگاهِ جدا (تبلت و کامپیوترِ دوم) تا دفترِ دستگاه‌های اصلیِ سفر از این شکاف آلوده نشود
  const X = await E.device('X-owner-tablet', owner.email);
  const Y = await E.device('Y-owner-laptop', owner.email);
  assert.equal((await X.firstSync()).ok, true);
  assert.equal((await Y.firstSync()).ok, true);
  const pid = products[5];
  X.offline = true; Y.offline = true;
  X.setSalePrice(pid, 160);
  Y.setSalePrice(pid, 165);
  X.offline = false; await X.drain();
  Y.offline = false; await Y.drain(); await X.drain();
  try {
    await assertSameEverywhere('J5.default', [X, Y]);
  } finally {
    //  دستگاه‌های اصلی همان قیمتِ سرور را می‌گیرند (ویرایشِ هر دو بیرون از آن‌ها)
    for (const d of [A2, B, S]) await d.drain();
  }
}, { needs: ['J4'] });

await E.step('J5', 'با `sync.pull_own_newer`: دو دستگاه قیمتِ یک کالا را آفلاین عوض می‌کنند ⇐ یک قیمت همه‌جا؛ بازنده در صندوقِ تعارض و سابقهٔ هر دو', async () => {
  await E.flag(owner.shopId, 'sync.pull_own_newer', true);
  const dev1 = A2;
  await dev1.drain(); await B.drain(); await S.drain();
  const pid = products[3];
  const was = dev1.ledger.products.find((p) => p.id === pid).salePrice;
  dev1.offline = true; B.offline = true;
  dev1.setSalePrice(pid, 150);
  B.setSalePrice(pid, 175);
  dev1.offline = false;
  await dev1.drain();
  B.offline = false;
  await B.drain(); await dev1.drain(); await S.drain();
  await assertSameEverywhere('J5 · یک قیمت همه‌جا', [dev1, B, S]);
  assert.equal(dev1.ledger.products.find((p) => p.id === pid).salePrice, 175, 'رسیدنِ دیرتر برنده');
  //  هیچ چیزی بی‌صدا گم نشد: مقدارِ بازنده در صندوقِ تعارضِ همان دکان (API ِ دکان و پنلِ مدیر، با بازگردانی)
  const cf = E.ok(await B.api('GET', '/sync/v1/conflicts'), 'تعارض‌ها').conflicts
    .filter((c) => c.table === 'products' && c.row_id === pid && c.field === 'salePrice');
  assert.equal(cf.length, 1, JSON.stringify(cf));
  assert.deepEqual([cf[0].loser_value, cf[0].winner_value, cf[0].loser_device, cf[0].winner_device], [150, 175, dev1.replica(), B.replica()]);
  //  و در سابقهٔ هر دستگاه (همگام‌شده) هر دو ویرایش با مقدارِ پیشین
  for (const d of [dev1, B, S]) {
    const edits = d.ledger.auditLog.filter((a) => a.type === 'product_edit' && a.refId === pid).map((a) => a.notes);
    assert.equal(edits.length, 2, `${d.label}: ${JSON.stringify(edits)}`);
    assert.ok(edits.some((n) => n.includes('150')) && edits.some((n) => n.includes('175')), `${d.label}: ${JSON.stringify(edits)}`);
    assert.ok(edits.every((n) => n.includes(String(was))));
  }
}, { needs: ['J4'] });

/* ═══════════════════════════════ J2b ═══════════════════════════════ */

await E.step('J2b', 'همان گوشی: صاحب خارج، شاگرد با کدِ شاگرد روی همان گوشی می‌فروشد، صاحب برمی‌گردد و می‌فروشد ⇐ بی شمارهٔ تکراری، بی گم شدن', async () => {
  const O = A2;   //  گوشیِ صاحب (پس از نصبِ دوبارهٔ J4)
  await O.drain();
  assert.equal((await O.logout()).blocked, false);
  const code = await staffCode(B);
  //  همان گوشی (همان `deviceUid`)، حسابِ شاگرد: دفتر و صفِ جدا (`AccountBind.signedIn` ⇒ SWITCHED)، نخستین دور Snapshot
  P = new NativeDevice(E, 'O-same-phone-staff', { deviceId: O.deviceId, phone: O.phone, seller: 'حسن' });
  await P.loginStaff(code, 'حسن');
  assert.equal((await P.firstSync()).ok, true);
  assert.notEqual(P.replica(), O.replica(), 'حسابِ دوم روی همان گوشی شناسهٔ همگام‌سازیِ خودش (`SyncReplica`)');
  assert.notEqual(await P.ensureSeries(), O.series, 'و سریِ سندِ خودش');
  sell(P, [{ id: products[14], qty: 1 }]);
  sell(P, [{ id: products[15], qty: 2 }]);
  await P.drain();
  assertNoDuplicates('J2b · سرور پس از فروشِ شاگرد روی گوشیِ صاحب', await serverLedger(B));
  //  «حذفِ ردیفِ خودش»: ردیفی که صاحب از همین گوشی ساخت مالِ شاگرد نیست (پیش‌تر همان شناسه ⇒ پاک می‌شد)
  const ownerMove = P.ledger.stockMovements.find((m) => O.ledger.sales.some((x) => x.id === m.refId && x.series === O.series));
  assert.ok(ownerMove, 'حرکتِ فروشِ صاحب از همین گوشی');
  P.save({ ...P.ledger, stockMovements: P.ledger.stockMovements.filter((m) => m.id !== ownerMove.id) });
  await P.sync();
  assert.deepEqual(P.dropped.map((x) => [x.row_id, x.why]), [[ownerMove.id, 'delete_not_allowed']]);
  assert.ok((await serverLedger(B)).stockMovements.some((m) => m.id === ownerMove.id), 'روی سرور مانده');
  assert.equal((await P.logout()).blocked, false);

  //  صاحب برمی‌گردد: همان دفترِ خودش (پیش از خروج) + فروش‌های شاگرد از همگام‌سازی
  await O.login(owner.email);
  await O.drain();
  sell(O, [{ id: products[16], qty: 1 }]);
  sell(O, [{ id: products[17], qty: 1 }]);
  await O.drain();
  await S.drain();
  await B.drain();
  await assertSameEverywhere('J2b · همه پس از برگشتِ صاحب', [O, S, B], { expect: { stock: expectStock() } });
  const nums = (series) => O.ledger.sales.filter((x) => x.series === series).map((x) => x.invoiceNumber).sort((x, y) => x - y);
  assert.deepEqual(nums(O.series), [1, 2, 3, 4], 'سریِ صاحب پشتِ سرِ هم، بی تکرار (۲ فروشِ J4، ۲ پس از برگشت)');
  assert.deepEqual(nums(P.series), [1, 2], 'سریِ شاگرد روی همان گوشی');
  //  فروش‌های شاگرد روی همان گوشی به دفترِ صاحب رسید (پیش‌تر pull «همین دستگاه» را نمی‌داد)
  assert.equal(O.ledger.sales.filter((x) => x.seller === 'حسن').length, 2);
  if (!KOTLIN.invoiceBySeries) throw new Error('InvoiceNumber.next در SalesEngine.record ِ کاتلین نیست');
}, { needs: ['J2', 'J4'] });

await E.step('J6', 'INV-10: فروشِ آفلاینِ A پیش از شمارش ⇐ شمارشِ ۱۷ روی B ⇐ A دیر می‌فرستد ⇐ A، B و سرور همه ۱۷', async () => {
  const boss = await E.owner('owner-count@e2e.local', 'صاحبِ شمارش', 'دکانِ شمارش');
  await E.flag(boss.shopId, 'shop.catalog');
  await E.flag(boss.shopId, 'shop.count_absorb');
  const X = await E.device('X-count-phone', boss.email);
  assert.equal((await X.firstSync()).ok, true);
  const Y = await E.device('Y-count-desktop', boss.email, { appVersion: '1.0.9999' });
  assert.equal((await Y.firstSync()).ok, true);
  assert.equal(await X.stockFlags(), true);
  assert.equal(await Y.stockFlags(), true);
  const oil = X.stockIn({ name: 'روغن', price: 100, cost: 80, units: 20 });
  await X.drain(); await Y.drain();
  const floor = async (dev) => {
    const r = await dev.api('GET', `/shop/catalog/products/${oil}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.product.stock.levels.shop_floor;
  };
  assert.equal(await floor(Y), 20_000);

  //  ۱۰:۰۰ — A آفلاین ۳ می‌فروشد (هنوز نفرستاده)
  X.offline = true;
  assert.equal(X.sell({ lines: [{ id: oil, qty: 3 }] }).ok, true);
  assert.equal(X.stock(oil), 17);
  //  ۱۰:۳۰ — B قفسه را می‌شمارد: ۱۷ (عددِ درست پس از فروشِ A) و ثبت می‌شود
  await new Promise((r) => setTimeout(r, 5));
  assert.equal(Y.count({ [oil]: 17 }, { [oil]: Date.now() }), 1);
  await Y.drain();
  assert.equal(Y.stock(oil), 17);
  assert.equal(await floor(Y), 17_000);
  //  ۱۱:۰۰ — A برمی‌گردد و فروشِ ۱۰:۰۰ را می‌فرستد ⇒ هیچ‌جا ۱۴ نیست
  X.offline = false;
  await X.drain(); await Y.drain(); await X.drain();
  assert.equal(X.stock(oil), 17, 'A');
  assert.equal(Y.stock(oil), 17, 'B');
  assert.equal(await floor(X), 17_000, 'موجودیِ کاتالوگِ سرور');
  await assertSameEverywhere('J6 · پس از فروشِ دیررس', [X, Y], { expect: { sales: 1, stock: { [oil]: 17 } } });
  //  فروش در دفتر و گزارش می‌ماند؛ جذب با یادداشت در سابقهٔ سرور
  const mv = await X.api('GET', `/shop/catalog/moves?productId=${oil}&limit=100`);
  assert.equal(mv.status, 200);
  assert.deepEqual(mv.body.items.filter((m) => m.type === 'sale').map((m) => m.qtyMilli), [3000]);
  assert.deepEqual(mv.body.items.filter((m) => m.reason === 'count_absorb').map((m) => [m.deltaOnHandMilli, m.note]),
    [[3000, 'پیش از شمارش ثبت شده بود — در شمارش آمده است']]);
  //  پرچم خاموش روی همان دستگاه ⇒ همان ۱۴ ِ امروز (شکاف بسته شد، نه پنهان)
  X.countAbsorb = false;
  assert.equal(X.stock(oil), 14);
  X.countAbsorb = true;
});

await E.step('J.db', 'سرور: هیچ op ِ تکراری در دفترِ دکان و هیچ حسابِ شبح از کدهای شاگرد', async () => {
  const dup = await db.many(`SELECT op_id, COUNT(*)::int AS n FROM oplog GROUP BY op_id HAVING COUNT(*) > 1`);
  assert.deepEqual(dup, []);
  const ghosts = await db.many(`SELECT u.id FROM users u WHERE u.kind='staff' AND NOT EXISTS (SELECT 1 FROM shop_members m WHERE m.user_id=u.id)`);
  assert.deepEqual(ghosts, []);
});

await E.finish();
