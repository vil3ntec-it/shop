/**
 * سناریوی سرتاسریِ هم‌خوانیِ موجودی — E2E-STK-05، INV-06/INV-19، CHK-18 و §۳۲.۲ (بندهای ۱ و ۴)،
 * با **تصمیمِ صاحبِ مخزن (۱۴۰۵/۰۷/۲۷)**: «خریدِ اینترنتی موجودیِ سرور را نه کم می‌کند نه رزرو؛
 * دکان کالا را از برنامهٔ خودش تحویل می‌دهد و همان‌جا فروشِ عادی ثبت می‌شود؛ موجودی با
 * همگام‌سازی به سرور، سایت و دستگاه‌های دیگر می‌رسد».
 *
 *   • سفارشِ اینترنتی (حتی دو مشتریِ هم‌زمانِ آخرین واحد) ⇒ موجودیِ سرور، برنامه و سایت دست
 *     نمی‌خورد؛ هیچ رزروی نیست.
 *   • فروشنده همان واحد را در دکان می‌فروشد ⇒ «تحویل» در برنامه بیش از موجودی نمی‌فروشد (پیامِ
 *     روشن، موجودی منفی نمی‌شود)؛ «قبول» را سرور برای موجودی رد نمی‌کند؛ دکان با دلیل رد می‌کند.
 *   • «مشتری گرفت» در برنامه ⇒ فروشِ `o-<سفارش>` ⇒ پس از همگام‌سازی −N همه‌جا (سرور، سایت در همان
 *     خواندنِ بعدی، دستگاهِ دیگر)؛ تلاشِ دوباره و دستگاهِ دوم دو بار کم نمی‌کنند.
 *   • تحویلی که دستگاه نزده (پیک) از `GET /shop/orders/handover` به برنامه می‌رسد.
 *   • گزارشِ تطبیقِ سرور (دفترِ گردش = بازپخش) سبز است.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.catalog.
 * اجرا:  node test/e2e/stock.e2e.mjs
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'stock' });
const { db } = E;
const catalog = createRequire(import.meta.url)('../../src/lib/catalog');
const owner = await E.owner('owner-stk@e2e.local', 'صاحبِ موجودی', 'دکانِ موجودی');
for (const k of ['shop.orders', 'shop.storefront', 'shop.catalog']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-stock-store';

async function customer(phone) {
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone }), 'کدِ تأیید');
  const v = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone, code: s.devCode }), 'تأییدِ شماره');
  return (lines, extra = {}) => E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: `مشتری ${phone.slice(-2)}`, phone }, lines, ...extra,
  }, null, { 'X-Store-Ticket': v.ticket, 'Idempotency-Key': E.key('web') });
}
const stockOf = async (dev, id) => E.ok(await dev.api('GET', `/shop/catalog/products/${id}`), 'کاتالوگ').product.stock;
/** همان دری که ویترین و بازار (#301، #324) می‌خوانند */
const site = async (id) => (await catalog.publicStock(owner.shopId, [id])).get(id);
const orderId = async (no) => (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, no])).id;
const detail = async (dev, id) => E.ok(await dev.api('GET', `/shop/orders/${id}`), 'جزئیاتِ سفارش').order;
async function walk(dev, id, steps) {
  for (const [to, from, x] of steps) E.ok(await dev.api('POST', `/shop/orders/${id}/transition`, { to, from, ...(x || {}) }), `${from} ⇐ ${to}`);
}
const TO_READY = [['accepted', 'new', { readyInMinutes: 10 }], ['picking', 'accepted'], ['packed', 'picking'], ['ready_for_pickup', 'packed']];

let A; let last; let rice; let lastOrders;

await E.step('E2E-STK.0', 'کالاها از برنامهٔ دکان (۱ «روغنِ آخر»، ۳ «برنج») و ویترین', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  last = A.stockIn({ name: 'روغنِ آخر', price: 300, cost: 250, units: 1 });
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 3 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ موجودی', contactPhone: '0700000004', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  for (const id of [last, rice]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), 'انتشار');
  E.ok(await A.api('PUT', '/shop/catalog/settings', { showQty: 'exact' }), 'نمایشِ عدد در سایت');
  const st = await stockOf(A, last);
  assert.equal(st.levels.shop_floor, 1000, JSON.stringify(st));
  const pub = await site(last);
  assert.equal(pub.sellableMilli, 1000, JSON.stringify(pub));
});

await E.step('E2E-STK-05', 'دو مشتریِ هم‌زمان، آخرین واحد ⇐ هر دو سفارش ثبت (بی رزرو)؛ موجودیِ سرور، برنامه و سایت دست نمی‌خورد', async () => {
  const [c1, c2] = await Promise.all([customer('0791000001'), customer('0791000002')]);
  const two = await Promise.all([c1([{ productId: last, qtyMilli: 1000 }]), c2([{ productId: last, qtyMilli: 1000 }])]);
  assert.deepEqual(two.map((r) => r.status), [201, 201], JSON.stringify(two.map((r) => r.body)));
  lastOrders = await Promise.all(two.map((r) => orderId(r.body.order.orderNo)));
  const st = await stockOf(A, last);
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli, st.sellableMilli], [1000, 0, 1000], JSON.stringify(st));
  await A.drain();
  assert.equal(A.stock(last), 1, 'برنامهٔ دکان');
  assert.equal((await site(last)).sellableMilli, 1000, 'سایت');
  assert.equal(Number((await db.one('SELECT COUNT(*)::int AS n FROM stock_reservations WHERE shop_id=$1', [owner.shopId])).n), 0);
}, { needs: ['E2E-STK.0'] });

await E.step('INV-06', 'فروشنده همان واحد را در دکان می‌فروشد ⇐ فروش رد نمی‌شود؛ «تحویل» در برنامه بیش از موجودی نمی‌فروشد و منفی نمی‌شود', async () => {
  const s = A.sell({ lines: [{ id: last, qty: 1 }] });
  assert.equal(s.ok, true, s.message);
  await A.drain();
  assert.equal(A.dropped.length, 0, `opِ فروشِ دکان رد شد: ${JSON.stringify(A.dropped)}`);
  assert.equal((await stockOf(A, last)).levels.shop_floor, 0);
  assert.equal((await site(last)).status, 'out_of_stock', 'سایت همان خواندنِ بعدی');
  //  تحویلِ سفارشِ اول: برنامه کمبود را با پیامِ روشن می‌گوید و چیزی ثبت نمی‌کند
  const f = A.fulfillOrder(await detail(A, lastOrders[0]));
  assert.equal(f.ok, false);
  assert.match(f.message, /روغنِ آخر/);
  assert.equal(A.stock(last), 0, 'موجودی منفی نشد');
  const tasks = E.ok(await A.api('GET', '/shop/catalog/tasks?status=open'), 'وظیفه‌ها').tasks;
  assert.equal(tasks.filter((t) => t.kind === 'reservation_short').length, 0, 'رزروی نیست که بی‌پشتوانه شود');
}, { needs: ['E2E-STK-05'] });

await E.step('CHK-18.accept', 'CHK-18: «قبول» را سرور برای موجودی رد نمی‌کند (۲۰۰)؛ دکان با دلیلِ «ناموجود» رد می‌کند ⇐ هیچ اثری بر موجودی', async () => {
  const acc = await A.api('POST', `/shop/orders/${lastOrders[0]}/transition`, { to: 'accepted', from: 'new' });
  assert.equal(acc.status, 200, JSON.stringify(acc.body).slice(0, 200));
  E.ok(await A.api('POST', `/shop/orders/${lastOrders[0]}/transition`, { to: 'cancelled', from: 'accepted', reason: 'out_of_stock' }), 'لغو با دلیل');
  E.ok(await A.api('POST', `/shop/orders/${lastOrders[1]}/transition`, { to: 'rejected', from: 'new', reason: 'out_of_stock' }), 'رد با دلیل');
  const st = await stockOf(A, last);
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli], [0, 0]);
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['INV-06'] });

let pickupId; let C;
await E.step('E2E-STK.online', 'سفارشِ اینترنتیِ ۲ «برنج» ⇐ تا «تحویل» هیچ کسری نیست؛ «مشتری گرفت» در برنامه ⇐ فروشِ عادی ⇐ −۲ همه‌جا', async () => {
  //  دستگاهِ C پیش از تحویل همگام شده (برای سنجشِ «دستگاهِ دوم» در گامِ بعد)
  C = await E.device('C-counter-desktop', owner.email);
  assert.equal((await C.firstSync()).ok, true);
  const c = await customer('0791000003');
  const o = E.ok(await c([{ productId: rice, qtyMilli: 2000 }]), 'سفارش', 201).order;
  pickupId = await orderId(o.orderNo);
  await walk(A, pickupId, TO_READY);
  assert.equal((await stockOf(A, rice)).levels.shop_floor, 3000, 'پذیرش و آماده‌سازی چیزی کم نمی‌کند');
  await walk(A, pickupId, [['picked_up', 'ready_for_pickup']]);
  //  گذارِ سرور به‌تنهایی هیچ کم نمی‌کند (کسر فقط در برنامه)
  assert.equal((await stockOf(A, rice)).levels.shop_floor, 3000);
  assert.equal((await site(rice)).sellableMilli, 3000);
  const pending = E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌های بی‌فروش').items;
  assert.deepEqual(pending.map((x) => x.id), [pickupId]);

  //  برنامه: همان لحظهٔ «مشتری گرفت» فروش را ثبت و همگام می‌کند (`OrdersLive.post` ⇒ `AutoSync.now`)
  const t0 = performance.now();
  const f = A.fulfillOrder(await detail(A, pickupId));
  assert.equal(f.ok, true, f.message);
  assert.equal(f.saleId, `o-${pickupId}`);
  assert.equal(A.stock(rice), 1, 'برنامهٔ دکان همان لحظه');
  await A.drain();
  const pushed = performance.now();
  const pub = await site(rice);
  const seen = performance.now();
  assert.equal(pub.sellableMilli, 1000, `سایت همان خواندنِ بعدی: ${JSON.stringify(pub)}`);
  console.log(`     ⏱ تأخیر: ثبتِ فروش ⇐ push ${Math.round(pushed - t0)}ms · ⇐ خواندنِ سایت ${Math.round(seen - pushed)}ms · کل ${Math.round(seen - t0)}ms`);
  const st = await stockOf(A, rice);
  assert.deepEqual([st.levels.shop_floor, st.reservedMilli], [1000, 0]);
  const sale = A.ledger.sales.find((s) => s.id === `o-${pickupId}`);
  assert.equal(sale.channel, 'online');
  assert.equal(sale.orderId, pickupId);
  assert.equal(sale.paymentMethod, 'cash', 'تحویل در دکان، نقد سرِ پیشخوان');
  assert.deepEqual(E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌ها').items, []);
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['E2E-STK.0'] });

await E.step('E2E-STK.pos-view', '§۳۲.۲: دستگاهِ دیگر پس از همگام‌سازی همان موجودیِ سرور را دارد؛ تلاشِ دوباره و تحویلِ هم‌زمانِ دستگاهِ دوم دو بار کم نمی‌کنند', async () => {
  //  C هنوز pull نکرده و همان سفارش را (هم‌زمان با A) تحویل می‌زند ⇒ همان شناسه‌ها
  const fc = C.fulfillOrder(await detail(C, pickupId));
  assert.equal(fc.ok, true, fc.message);
  assert.equal(fc.saleId, `o-${pickupId}`);
  await C.drain();
  await A.drain();
  const server = (await stockOf(A, rice)).levels.shop_floor / 1000;
  assert.equal(server, 1, 'سرور یک بار کم کرد');
  assert.equal(A.stock(rice), server, `برنامهٔ A ${A.stock(rice)}، سرور ${server}`);
  assert.equal(C.stock(rice), server, `برنامهٔ C ${C.stock(rice)}، سرور ${server}`);
  assert.equal(A.ledger.sales.filter((s) => s.orderId === pickupId).length, 1);
  assert.equal(C.ledger.sales.filter((s) => s.orderId === pickupId).length, 1);
  //  تلاشِ دوباره روی A ⇒ «پیش‌تر ثبت شده»
  const again = A.fulfillOrder(await detail(A, pickupId));
  assert.equal(again.already, true);
  //  دستگاهِ تازه با Snapshot
  const B = await E.device('B-new-phone', owner.email);
  assert.equal((await B.firstSync()).ok, true);
  assert.equal(B.stock(rice), server);
  assert.equal(B.fulfillOrder(await detail(B, pickupId)).already, true);
  assert.equal((await site(rice)).sellableMilli, 1000);
}, { needs: ['E2E-STK.online'] });

await E.step('E2E-STK.courier', 'تحویلی که دستگاه نزده (پیک «ارسال شد»/«رسید») ⇐ handover ⇐ همان فروش در برنامه؛ نقدِ پیک بیرونِ دخل', async () => {
  const c = await customer('0791000004');
  const o = E.ok(await c([{ productId: rice, qtyMilli: 1000 }], { fulfillment: 'delivery', address: { landmark: 'کنارِ مسجد' } }), 'سفارشِ ارسالی', 201).order;
  const id = await orderId(o.orderNo);
  //  همان گذارهایی که برنامهٔ پیک از سرور می‌زند — دستگاهِ دکان هیچ کاری نکرده
  await walk(A, id, [...TO_READY.slice(0, 3), ['out_for_delivery', 'packed'], ['delivered', 'out_for_delivery']]);
  assert.equal((await stockOf(A, rice)).levels.shop_floor, 1000, 'سرور کم نکرد');
  const items = E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌های بی‌فروش').items;
  assert.deepEqual(items.map((x) => x.id), [id]);
  //  `OrdersLive.reconcile`: هر ردیف ⇒ همان `post`
  for (const it of items) assert.equal(A.fulfillOrder(it).ok, true);
  await A.drain();
  assert.equal((await stockOf(A, rice)).levels.shop_floor, 0);
  assert.equal(A.stock(rice), 0);
  assert.equal((await site(rice)).status, 'out_of_stock');
  const sale = A.ledger.sales.find((s) => s.orderId === id);
  assert.equal(sale.paymentMethod, 'cod');
  assert.deepEqual(sale.payments.map((p) => [p.kind, p.custody]), [['cash', 'courier']]);
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['E2E-STK.online'] });

await E.finish();
