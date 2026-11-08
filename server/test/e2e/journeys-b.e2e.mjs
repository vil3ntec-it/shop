/**
 * سفرهای واقعیِ دکان (بخشِ دوم) — همان سنجه‌های `lib/journey.mjs` پس از هر سفر، روی هر دستگاه و سرور:
 * هیچ چیزی گم نشده، هیچ چیزی دو بار نیست، جمع‌ها درست است.
 *
 *   JB1 برگشت و لغو با سقف و تأیید: شاگرد زیرِ سقف برمی‌گرداند؛ بالای سقف فقط با «درخواستِ تأیید» که صاحب روی
 *       دستگاهِ خودش می‌پذیرد؛ برگشتِ بی تأیید از برنامهٔ دست‌کاری‌شده با همهٔ همراه‌هایش رد؛ برگشتِ بیش از فاکتور
 *       از دو دستگاهِ هم‌زمان (پرچمِ `shop.returns.sale_cap`) رد؛ صاحب نقد و نسیه را لغو می‌کند.
 *   JB2 پایانِ روز: صندوق روی A باز، فروش روی سه دستگاه (یکی آفلاین)، X ِ هر سه یکی، Z و شمارشِ دخل روی B، فردا
 *       بازکردن روی B و بستن روی A ⇐ همان اعداد روی هر سه و همان خلاصهٔ روزِ سرور.
 *   JB3 خرید و رسیدِ کالا: خرید با پیش‌پرداخت ⇐ موجودی بالا، بدهیِ تأمین‌کننده؛ پرداخت روی B و خریدِ دوم روی A
 *       هم‌زمان آفلاین ⇐ همان بدهی همه‌جا.
 *   JB4 سفارشِ اینترنتی: ویترین ⇐ قبول ⇐ پیک ⇐ تحویل ⇐ فروش و کسرِ موجودی روی دستگاهِ دکان؛ دستگاهِ دوم دو بار
 *       کم نمی‌کند؛ موجودیِ سرور و سایت همان.
 *
 * پرچم‌ها فقط برای همین دکان: shop.approval_requests، shop.returns.sale_cap، shop.orders، shop.storefront،
 * shop.catalog، shop.delivery، shop.owner_report.
 * اجرا:  node test/e2e/run.mjs journeys-b
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';
import { NativeDevice } from './lib/native.mjs';
import { assertSameEverywhere, serverLedger } from './lib/journey.mjs';

const E = await boot({ name: 'journeys-b' });
const { db } = E;
const require = createRequire(import.meta.url);
const report = require('../../src/lib/owner-report');

const owner = await E.owner('owner-journey-b@e2e.local', 'صاحبِ سفرِ دوم', 'دکانِ سفرِ دوم');
for (const k of ['shop.approval_requests', 'shop.returns.sale_cap', 'shop.orders', 'shop.storefront', 'shop.catalog', 'shop.delivery', 'shop.owner_report']) {
  await E.flag(owner.shopId, k, true);
}
let A; let B; let S; let rice; let tea; let oil; let ahmad;
const syncAll = async () => { for (const d of [A, B, S]) await d.drain(); for (const d of [A, B, S]) await d.drain(); };
const sale = (dev, r) => { assert.equal(r.ok, true, `${dev.label}: ${r.message || r.code}`); return r; };
const itemOf = (dev, saleId, productId) => dev.ledger.saleItems.find((i) => i.saleId === saleId && i.productId === productId);

await E.step('JB.0', 'سه دستگاه: گوشیِ صاحب (A)، کامپیوترِ صاحب (B)، گوشیِ شاگرد با کدِ شاگرد (S)؛ سه کالا و یک قرض‌دار', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  await A.ensureSeries();
  rice = A.stockIn({ name: 'برنج', price: 300, cost: 220, units: 40 });
  tea = A.stockIn({ name: 'چای', price: 150, cost: 100, units: 40 });
  oil = A.stockIn({ name: 'روغن', price: 500, cost: 420, units: 10 });
  ahmad = A.addDebtor('احمد', '0793330003');
  await A.drain();
  B = await E.device('B-owner-desktop', owner.email, { appVersion: '1.0.9999' });
  assert.equal((await B.firstSync()).ok, true);
  await B.ensureSeries();
  const code = E.ok(await A.api('POST', '/shop/staff-code', { role: 'staff', maxUses: 1 }), 'کدِ شاگرد', [200, 201]).code;
  S = new NativeDevice(E, 'S-staff-phone', { seller: 'علی' });
  await S.loginStaff(code, 'علی');
  assert.equal((await S.firstSync()).ok, true);
  await S.ensureSeries();
  await assertSameEverywhere('JB.0', [A, B, S], { expect: { stock: { [rice]: 40, [tea]: 40, [oil]: 10 } } });
});

/* ═══════════════════════════════ JB1 ═══════════════════════════════ */

await E.step('JB1', 'برگشت و لغو: شاگرد زیرِ سقف؛ بالای سقف با تأییدِ صاحب از دستگاهِ خودش؛ بی تأیید یا بیش از فاکتور رد با همه‌ی همراه‌ها؛ لغوِ نقد و نسیه', async () => {
  //  سقفِ برگشتِ شاگرد ۵۰۰ افغانی (بالاتر ⇒ «نیازمندِ تأیید»)
  E.ok(await A.api('PUT', '/shop/permissions/roles/staff', { perms: { 'returns.create': { caps: { maxAmount: 50000 } } } }), 'سقفِ برگشت');
  const s1 = sale(A, A.sell({ lines: [{ id: rice, qty: 2 }] }));                                               // ۶۰۰ نقد
  const s2 = sale(A, A.sell({ lines: [{ id: rice, qty: 3 }], payment: 'credit', debtorId: ahmad, paid: 0 })); // ۹۰۰ قرض
  const s3 = sale(A, A.sell({ lines: [{ id: tea, qty: 2 }] }));                                                // ۳۰۰ نقد ⇐ لغو
  const s4 = sale(A, A.sell({ lines: [{ id: oil, qty: 1 }], payment: 'credit', debtorId: ahmad, paid: 100 })); // ۵۰۰، ۴۰۰ قرض ⇐ لغو
  const s5 = sale(A, A.sell({ lines: [{ id: tea, qty: 2 }] }));                                                // ۳۰۰ ⇐ برگشتِ هم‌زمان
  await syncAll();

  //  ۱) شاگرد زیرِ سقف: ۱ برنج (۳۰۰) از فاکتورِ نقد
  const r1 = S.returnFlow(s1.saleId, { [itemOf(S, s1.saleId, rice).id]: { qty: 1 } }, { reason: 'خراب', by: 'علی' });
  assert.equal(r1.ok, true, r1.code);
  assert.deepEqual([r1.amount, r1.cash], [300, 300]);
  await syncAll();

  //  ۲) بالای سقف (۶۰۰ از فاکتورِ نسیه) — برنامه پیش از ثبت می‌پرسد؛ صاحب روی A تأیید می‌کند
  const doc = `01JBRET${Date.now()}`;
  const ask = await S.api('POST', '/shop/approval-requests', { key: `k-${doc}`, perm: 'returns.create', doc: `returns/${doc}`, amount: 60000, reason: 'مشتری کالا را نخواست' });
  assert.equal(ask.status, 201, JSON.stringify(ask.body));
  const pending = E.ok(await A.api('GET', '/shop/approval-requests?status=pending'), 'درخواست‌ها').requests || [];
  assert.ok(pending.some((x) => x.id === ask.body.request.id), JSON.stringify(pending).slice(0, 300));
  E.ok(await A.api('POST', `/shop/approval-requests/${ask.body.request.id}/approve`, {}), 'تأییدِ صاحب');
  const r2 = S.returnFlow(s2.saleId, { [itemOf(S, s2.saleId, rice).id]: { qty: 2 } }, { reason: 'مشتری کالا را نخواست', docId: doc, by: 'علی', approval: { v: 1, request: ask.body.request.id } });
  assert.equal(r2.ok, true, r2.code);
  assert.deepEqual([r2.amount, r2.account, r2.cash], [600, 600, 0], 'برگشتِ فاکتورِ نسیه اول از بدهیِ همان فاکتور');
  await syncAll();
  assert.equal(A.debt(ahmad), 900 + 400 - 600, 'ماندهٔ احمد');

  //  ۳) برنامهٔ دست‌کاری‌شده: ۳۰۰ ِ دیگر از همان فاکتور بی تأیید (سقف تجمعیِ فاکتور است: ۶۰۰ + ۳۰۰ > ۵۰۰) ⇒ رد،
  //     با رسید، حرکت و شمارندهٔ قلم
  const T = new NativeDevice(E, 'S-tampered', { deviceId: S.deviceId, seller: 'علی' });
  T.token = S.token; T.refreshToken = S.refreshToken; T.user = S.user;
  assert.equal((await T.firstSync()).ok, true);
  const bad = T.returnFlow(s2.saleId, { [itemOf(T, s2.saleId, rice).id]: { qty: 1 } }, { reason: 'بی تأیید', by: 'علی' });
  assert.equal(bad.ok, true);
  await T.sync();
  const why = T.dropped.map((x) => `${x.table}:${x.why}`);
  assert.ok(why.includes('returns:approval_required'), JSON.stringify(why));
  assert.ok(why.some((w) => w.endsWith(':tx_rejected')), `همراه‌ها: ${JSON.stringify(why)}`);
  assert.equal(T.queue.length, 0);

  //  ۴) برگشتِ هم‌زمانِ بیش از فاکتور: صاحب (A) و شاگرد (S) هر دو بی اینترنت ۲ چایِ همان فاکتورِ ۲ تایی را
  A.offline = true; S.offline = true;
  sale(A, A.returnFlow(s5.saleId, { [itemOf(A, s5.saleId, tea).id]: { qty: 2 } }, { reason: 'تاریخ گذشته' }));
  sale(S, S.returnFlow(s5.saleId, { [itemOf(S, s5.saleId, tea).id]: { qty: 2 } }, { reason: 'تاریخ گذشته', by: 'علی' }));
  A.offline = false; await A.drain();
  S.offline = false; await S.sync();
  assert.deepEqual(S.dropped.filter((x) => x.table === 'returns').map((x) => x.why), ['return_exceeds_sale'], JSON.stringify(S.dropped.map((x) => `${x.table}:${x.why}`)));
  const srv = await serverLedger(A);
  assert.equal(srv.saleItems.find((i) => i.saleId === s5.saleId).returnedQty, 2, 'سرور: فقط یک بار برگشت');
  assert.equal(srv.returns.filter((r) => r.saleId === s5.saleId).reduce((n, r) => n + r.amount, 0), 300);

  //  ۵) لغو: شاگرد نمی‌تواند (پیش‌فرضِ «ممنوع» ⇒ درخواستِ تأیید هم ساخته نمی‌شود)؛ صاحب روی B لغو می‌کند
  const noAsk = await S.api('POST', '/shop/approval-requests', { key: `k-c-${doc}`, perm: 'sales.cancel', doc: `sales/${s3.saleId}`, reason: 'اشتباه' });
  assert.equal(noAsk.status, 403, JSON.stringify(noAsk.body));
  assert.equal(noAsk.body.error.code, 'cancel_not_allowed', 'دلیلِ روشن، نه پنجرهٔ تأییدِ بی‌ثمر');
  await B.drain();
  sale(B, B.cancelSale(s3.saleId, 'فاکتورِ اشتباه'));
  sale(B, B.cancelSale(s4.saleId, 'مشتری پس داد'));
  assert.equal(B.cancelSale(s4.saleId, 'دوباره').ok, false, 'لغوِ دوباره نه');
  await syncAll();

  //  S ِ واقعی (دفترِ برگشتِ هم‌زمان را دارد که سرور نپذیرفت) همان نیست ⇒ از سرور دوباره ساخته (نصبِ دوباره)
  S = new NativeDevice(E, 'S-staff-phone', { deviceId: S.deviceId, seller: 'علی' });
  const code2 = E.ok(await A.api('POST', '/shop/staff-code', { role: 'staff', maxUses: 1 }), 'کدِ شاگرد', [200, 201]).code;
  await S.loginStaff(code2, 'علی');
  assert.equal((await S.firstSync()).ok, true);
  await S.ensureSeries();
  const t = await assertSameEverywhere('JB1', [A, B, S], {
    expect: {
      sales: 3, cancelled: 2,
      stock: { [rice]: 40 - 5 + 1 + 2, [tea]: 40 - 2 - 2 + 2 + 2, [oil]: 10 - 1 + 1 },
      debt: { [ahmad]: 900 - 600 },
      returnsTotal: 300 + 600 + 300,
    },
  });
  assert.equal(t.cancelled, 2);
  //  لغوِ نسیه: ماندهٔ ۴۰۰ ِ فاکتورِ ۴ با رسیدِ سیستمی بسته شد (پولی به دخل نیامد)
  const sys = A.ledger.transactions.filter((x) => x.refId === s4.saleId && x.kind === 'sale_cancel');
  assert.deepEqual(sys.map((x) => x.amount), [400]);
  //  شاگرد هیچ چیزی را به نامِ تأییدکننده ثبت نکرد: سابقهٔ تأیید درخواست‌کننده و تأییدکننده را دارد
  const ap = E.ok(await A.api('GET', '/shop/approvals'), 'سابقهٔ تأییدها').approvals.find((x) => x.doc === `returns/${doc}`);
  assert.ok(ap, 'سابقهٔ تأیید');
  assert.equal(ap.approverId, owner.user.id);
}, { needs: ['JB.0'] });

/* ═══════════════════════════════ JB2 ═══════════════════════════════ */

let day1;
await E.step('JB2', 'پایانِ روز: صندوق روی A باز، فروش روی سه دستگاه (یکی آفلاین)، X یکی، Z و شمارشِ دخل روی B ⇐ همه و سرور یک عدد', async () => {
  await syncAll();
  const t0 = Date.now();
  sale(A, A.openShift(1000));
  await syncAll();
  assert.ok(B.currentShift() && S.currentShift(), 'صندوقِ باز روی B و S هم');
  sale(A, A.sell({ lines: [{ id: rice, qty: 2 }] }));                                              // +۶۰۰
  sale(S, S.sell({ lines: [{ id: tea, qty: 2 }] }));                                               // +۳۰۰
  await S.drain();
  S.offline = true;
  sale(S, S.sell({ lines: [{ id: tea, qty: 1 }] }));                                               // +۱۵۰ (آفلاین)
  sale(B, B.sell({ lines: [{ id: rice, qty: 1 }], payment: 'credit', debtorId: ahmad, paid: 100 })); // +۱۰۰ پیش‌پرداخت
  sale(A, A.receive(ahmad, 50));                                                                   // +۵۰ رسیدِ قرض
  await A.drain(); await B.drain(); await A.drain();
  //  X روی A پیش از رسیدنِ فروشِ آفلاینِ S: ۱۰۰۰ + ۶۰۰ + ۳۰۰ + ۱۰۰ + ۵۰
  assert.equal(A.shiftTotals(A.currentShift()).expected, 2050);
  S.offline = false;
  await syncAll();
  const xs = [A, B, S].map((d) => d.shiftTotals(d.currentShift()).expected);
  assert.deepEqual(xs, [2200, 2200, 2200], 'X ِ هر سه پس از همگام‌سازی');
  //  Z روی B: شمرده ۲۱۹۰ ⇐ «کسری ۱۰»؛ چیزی خودکار درست نمی‌شود
  const z = B.closeShift(2190, 'شمارشِ شب');
  sale(B, z);
  assert.deepEqual([z.totals.expected, z.diff], [2200, -10]);
  await syncAll();
  const closed = [A, B, S].map((d) => d.shifts()[0]);
  assert.ok(closed.every((s) => s.close && s.close.amount === 2190), 'همان بستن روی هر سه');
  assert.ok(closed.every((s) => s.close.notes.includes('کسری 10')), closed[0].close.notes);
  const zs = [A, B, S].map((d) => d.shiftTotals(d.shifts()[0]));
  assert.deepEqual(zs[1], zs[0]);
  assert.deepEqual(zs[2], zs[0]);
  assert.equal(zs[0].expected, 2200);
  //  فردا: B باز می‌کند، A می‌بندد — دو صندوقِ جدا، هیچ کدام باز نمی‌ماند
  sale(B, B.openShift(500));
  await syncAll();
  sale(A, A.sell({ lines: [{ id: tea, qty: 1 }] }));                                               // +۱۵۰
  await syncAll();
  const z2 = A.closeShift(650);
  sale(A, z2);
  assert.deepEqual([z2.totals.expected, z2.diff], [650, 0]);
  await syncAll();
  for (const d of [A, B, S]) {
    assert.equal(d.shifts().length, 2, d.label);
    assert.equal(d.currentShift(), null, `${d.label}: صندوقِ باز نمانده`);
  }
  day1 = { from: t0, to: Date.now() + 1 };
  const t = await assertSameEverywhere('JB2', [A, B, S]);

  //  خلاصهٔ روزِ سرور (همان گزارشِ صاحب) با جمعِ اسنادِ دفترِ برنامه برابر است
  const kabul = (t) => new Date(t + 4.5 * 3_600_000).toISOString().slice(0, 10);
  const w = { frequency: 'daily', fromAt: day1.from, until: day1.to, from: kabul(day1.from), to: kabul(day1.to) };
  const sum = await report.buildSummary(owner.shopId, w, 'Asia/Kabul');
  const inW = A.ledger.sales.filter((s) => s.createdAt >= day1.from && s.createdAt <= day1.to && s.status !== 'cancelled');
  const minor = (x) => Math.round(x * 100);
  assert.equal(sum.invoices, inW.length);
  assert.equal(sum.sales, inW.reduce((n, s) => n + minor(s.finalTotal), 0));
  assert.equal(sum.returns, A.ledger.returns.filter((r) => r.createdAt >= day1.from && r.createdAt <= day1.to).reduce((n, r) => n + minor(r.amount), 0));
  assert.equal(inW.length, 5, 'پنج فاکتورِ این دو صندوق');
  assert.equal(t.sales, 3 + 5);
}, { needs: ['JB1'] });

/* ═══════════════════════════════ JB3 ═══════════════════════════════ */

await E.step('JB3', 'خرید و رسیدِ کالا: موجودی بالا، بدهیِ تأمین‌کننده؛ پرداخت روی B و خریدِ دوم روی A هم‌زمان آفلاین ⇐ همان بدهی همه‌جا', async () => {
  await syncAll();
  const before = A.stock(rice);
  const sup = A.addSupplier('عمده‌فروشیِ کریمی', '0794440004');
  const p1 = A.purchase({ supplierId: sup, productId: rice, quantity: 100, price: 250, paid: 10000 });
  sale(A, p1);
  assert.deepEqual([p1.totalAmount, p1.debt], [25000, 15000]);
  await syncAll();
  assert.equal(B.supplierDebt(sup), 15000);
  assert.equal(S.stock(rice), before + 100);
  A.offline = true; B.offline = true;
  sale(B, B.paySupplier(sup, 5000, `pay-${Date.now()}`));
  sale(A, A.purchase({ supplierId: sup, productId: rice, quantity: 50, price: 260, paid: 0 }));
  //  دو لمسِ همان پرداخت (همان کلید) یک پرداخت است
  const key = `pay2-${Date.now()}`;
  sale(B, B.paySupplier(sup, 1000, key));
  assert.equal(B.paySupplier(sup, 1000, key).ok, false);
  A.offline = false; B.offline = false;
  await syncAll();
  await assertSameEverywhere('JB3', [A, B, S], {
    expect: { stock: { [rice]: before + 150 }, supplier: { [sup]: 25000 - 10000 - 5000 + 13000 - 1000 } },
  });
  //  قیمتِ خریدِ تازه روی کالا و تاریخچه‌اش روی هر سه
  for (const d of [A, B, S]) {
    assert.equal(d.ledger.products.find((p) => p.id === rice).purchasePrice, 260, d.label);
    assert.deepEqual(d.ledger.priceHistory.filter((h) => h.productId === rice).map((h) => [h.oldPrice, h.newPrice]).sort(), [[220, 250], [250, 260]]);
  }
}, { needs: ['JB.0'] });

/* ═══════════════════════════════ JB4 ═══════════════════════════════ */

await E.step('JB4', 'سفارشِ اینترنتی: ویترین ⇐ قبول ⇐ پیک ⇐ تحویل ⇐ فروش و کسرِ موجودی روی دستگاهِ دکان؛ دستگاهِ دوم دو بار کم نمی‌کند', async () => {
  await syncAll();
  const SLUG = 'e2e-journeys-store';
  const PHONE = '0795550005';
  assert.ok(E.ok(await A.ordersConfig(), 'config').handoverSince > 0);
  assert.ok(E.ok(await B.ordersConfig(), 'config').handoverSince > 0);
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ سفر', contactPhone: '0700000099', deliveryFeeMinor: 4000, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${tea}`, { published: true }), 'انتشار');
  const kAcct = await E.staff(owner, 'courier-journey@e2e.local', 'پیکِ سفر');
  const K = await E.device('K-courier', kAcct.email);
  const cid = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: kAcct.user.id, cashCapMinor: 5_000_000 }), 'پیک', 201).courier.id;
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone: PHONE }), 'کدِ تأیید');
  const ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone: PHONE, code: s.devCode }), 'تأییدِ شماره').ticket;
  const stockBefore = A.stock(tea);
  const placed = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ سفر', phone: PHONE },
    lines: [{ productId: tea, qtyMilli: 3000 }], address: { text: 'کارتهٔ چهار', landmark: 'کنارِ نانوایی' },
  }, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': E.key('web') }), 'سفارش', 201).order;
  const id = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, placed.orderNo])).id;
  //  ثبتِ سفارش موجودی را نه کم می‌کند نه رزرو (تصمیمِ صاحبِ مخزن)
  assert.equal(A.stock(tea), stockBefore);
  for (const [to, from, x] of [['accepted', 'new', { readyInMinutes: 15 }], ['picking', 'accepted'], ['packed', 'picking']]) {
    E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to, from, ...(x || {}) }), `${from} ⇐ ${to}`);
  }
  const asg = E.ok(await A.api('POST', `/shop/delivery/orders/${id}/assign`, { mode: 'shop_courier', courierId: cid, fareMinor: 5000 }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن به پیک', 201).assignment.id;
  const due = 3 * 15000 + 4000;
  let n = 0;
  const ev = (type, extra = {}) => ({ eventId: `${K.deviceId}-j-${++n}`, assignmentId: asg, type, deviceTime: Date.now() - 60_000 + n * 1000, deviceSeq: n, ...extra });
  const res = E.ok(await K.api('POST', '/courier/events', { deviceId: K.deviceId, events: [ev('assigned'), ev('picked_up'), ev('moving'), ev('arrived'), ev('delivered', { cashCollectedMinor: due })] }), 'صفِ پیک').results;
  assert.ok(res.every((x) => x.status === 'applied'), JSON.stringify(res));
  assert.equal((await db.one('SELECT status FROM online_orders WHERE id=$1', [id])).status, 'delivered');

  //  دستگاهِ دکان (A) تحویل را می‌بیند و فروش را ثبت می‌کند؛ B هم‌زمان همان را می‌بیند ⇒ یک فروش
  const itemsA = E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌ها').items.filter((x) => x.id === id);
  const itemsB = E.ok(await B.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌ها').items.filter((x) => x.id === id);
  assert.equal(itemsA.length, 1);
  sale(A, A.fulfillOrder(itemsA[0]));
  await A.drain(); await B.drain();
  const again = B.fulfillOrder(itemsB[0]);
  assert.deepEqual([again.ok, again.already], [true, true], 'دستگاهِ دوم دوباره ثبت نمی‌کند');
  await syncAll();
  assert.deepEqual(E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌ها').items.filter((x) => x.id === id), []);
  const t = await assertSameEverywhere('JB4', [A, B, S], { expect: { stock: { [tea]: stockBefore - 3 } } });
  assert.ok(t.sales > 0);
  const saleRow = A.ledger.sales.find((x) => x.orderId === id);
  assert.equal(saleRow.finalTotal * 100, due);
  assert.equal(saleRow.paymentMethod, 'cod');
  assert.deepEqual(saleRow.payments.map((p) => [p.kind, p.custody]), [['cash', 'courier']]);
  //  موجودیِ سرور (کاتالوگ) و تطبیق
  const st = E.ok(await A.api('GET', `/shop/catalog/products/${tea}`), 'کاتالوگ').product.stock;
  assert.equal(st.levels.shop_floor, (stockBefore - 3) * 1000);
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['JB.0'] });

await E.finish();
