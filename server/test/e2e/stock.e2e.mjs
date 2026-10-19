/**
 * سناریوی سرتاسریِ هم‌خوانیِ موجودی — E2E-STK-05، INV-06/INV-19، CHK-18 و §۳۲.۲ (بندهای ۱ و ۴).
 *
 *   • دو مشتری هم‌زمان آخرین واحد را از ویترین می‌خواهند ⇐ فقط یکی؛ موجودی منفی نمی‌شود.
 *   • همان واحدِ رزروشده را فروشنده در دکان (برنامه، بی خبر از رزرو) می‌فروشد ⇐ فروشِ ثبت‌شده
 *     رد نمی‌شود، رزروِ بی‌پشتوانه «وظیفه» می‌سازد و قبولِ سفارش ۴۰۹ می‌گیرد.
 *   • سفارشِ اینترنتیِ تحویل‌شده از دفترِ سرور کم می‌شود و دستگاهِ دکان پس از همگام‌سازی همان
 *     عدد را می‌بیند (POS و ویترین «یک موجودی»).
 *   • گزارشِ تطبیقِ سرور (دفترِ گردش = بازپخش) سبز است.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.catalog.
 * اجرا:  node test/e2e/stock.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'stock' });
const { db } = E;
const owner = await E.owner('owner-stk@e2e.local', 'صاحبِ موجودی', 'دکانِ موجودی');
for (const k of ['shop.orders', 'shop.storefront', 'shop.catalog']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-stock-store';

async function customer(phone) {
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone }), 'کدِ تأیید');
  const v = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone, code: s.devCode }), 'تأییدِ شماره');
  return (lines) => E.post(`/api/store/${SLUG}/checkout`, { fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: `مشتری ${phone.slice(-2)}`, phone }, lines }, null, { 'X-Store-Ticket': v.ticket, 'Idempotency-Key': E.key('web') });
}
const stockOf = async (dev, id) => E.ok(await dev.api('GET', `/shop/catalog/products/${id}`), 'کاتالوگ').product.stock;
const orderId = async (no) => (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, no])).id;

let A; let last; let rice; let won;

await E.step('E2E-STK.0', 'کالاها از برنامهٔ دکان (۱ «روغنِ آخر»، ۳ «برنج») و ویترین', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  last = A.stockIn({ name: 'روغنِ آخر', price: 300, cost: 250, units: 1 });
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 3 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ موجودی', contactPhone: '0700000004', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  for (const id of [last, rice]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), 'انتشار');
  const st = await stockOf(A, last);
  assert.equal(st.levels.shop_floor, 1000, JSON.stringify(st));
});

await E.step('E2E-STK-05', 'دو مشتریِ هم‌زمان، آخرین واحد ⇐ فقط یکی؛ دیگری «ناموجود»؛ موجودی منفی نمی‌شود', async () => {
  const [c1, c2] = await Promise.all([customer('0791000001'), customer('0791000002')]);
  const two = await Promise.all([c1([{ productId: last, qtyMilli: 1000 }]), c2([{ productId: last, qtyMilli: 1000 }])]);
  assert.deepEqual(two.map((r) => r.status).sort(), [201, 409], JSON.stringify(two.map((r) => r.body)));
  const lost = two.find((r) => r.status === 409);
  assert.equal(lost.body.error.code, 'out_of_stock');
  won = two.find((r) => r.status === 201).body.order;
  const n = (await db.one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [owner.shopId])).n;
  assert.equal(n, 1, 'سفارشِ ردشده ردیف ساخت');
  const st = await stockOf(A, last);
  assert.ok(st.levels.shop_floor >= 0 && st.sellableMilli === 0, JSON.stringify(st));
  assert.equal(st.reservedMilli, 1000);
}, { needs: ['E2E-STK.0'] });

await E.step('INV-06', 'فروشنده همان واحدِ رزروشده را در دکان می‌فروشد ⇐ فروش رد نمی‌شود، وظیفهٔ «رزروِ بی‌پشتوانه» و قبولِ سفارش ۴۰۹', async () => {
  //  برنامهٔ دکان از رزرو خبر ندارد: موجودیِ محلی ۱ است و فروش ثبت می‌شود
  assert.equal(A.stock(last), 1);
  const s = A.sell({ lines: [{ id: last, qty: 1 }] });
  assert.equal(s.ok, true, s.message);
  await A.drain();
  assert.equal(A.dropped.length, 0, `opِ فروشِ دکان رد شد: ${JSON.stringify(A.dropped)}`);
  const st = await stockOf(A, last);
  assert.equal(st.levels.shop_floor, 0, JSON.stringify(st));
  const tasks = E.ok(await A.api('GET', '/shop/catalog/tasks?status=open'), 'وظیفه‌ها').tasks;
  assert.ok(tasks.some((t) => t.kind === 'reservation_short' && t.productId === last), JSON.stringify(tasks));
}, { needs: ['E2E-STK-05'] });

await E.step('CHK-18.accept', 'CHK-18: قبولِ سفارشی که رزروش دیگر پشتوانه ندارد ⇐ ۴۰۹ «ناموجود» و سفارش «جدید» می‌ماند؛ رد با دلیل ⇐ رزرو آزاد', async () => {
  const id = await orderId(won.orderNo);
  const acc = await A.api('POST', `/shop/orders/${id}/transition`, { to: 'accepted', from: 'new' });
  assert.equal(acc.status, 409, `قبولِ سفارشِ بی‌موجودی پذیرفته شد: ${acc.status} ${acc.body?.order?.status || JSON.stringify(acc.body).slice(0, 120)}`);
  assert.equal(acc.body.error.code, 'out_of_stock');
  //  دکان سفارش را با دلیل رد می‌کند ⇐ رزرو آزاد
  assert.equal((await db.one('SELECT status FROM online_orders WHERE id=$1', [id])).status, 'new');
  E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to: 'rejected', from: 'new', reason: 'out_of_stock' }), 'رد با دلیل');
  assert.equal((await stockOf(A, last)).reservedMilli, 0);
}, { needs: ['INV-06'] });

let pickupId;
await E.step('E2E-STK.online', 'سفارشِ اینترنتیِ ۲ «برنج» تا «تحویل گرفته شد» ⇐ از دفترِ سرور کم می‌شود؛ تطبیق سبز', async () => {
  const c = await customer('0791000003');
  const o = E.ok(await c([{ productId: rice, qtyMilli: 2000 }]), 'سفارش', 201).order;
  pickupId = await orderId(o.orderNo);
  for (const [to, from, x] of [['accepted', 'new', { readyInMinutes: 10 }], ['picking', 'accepted'], ['packed', 'picking'], ['ready_for_pickup', 'packed'], ['picked_up', 'ready_for_pickup']]) {
    E.ok(await A.api('POST', `/shop/orders/${pickupId}/transition`, { to, from, ...(x || {}) }), `${from} ⇐ ${to}`);
  }
  const st = await stockOf(A, rice);
  assert.equal(st.levels.shop_floor, 1000, `۳ − ۲ = ۱: ${JSON.stringify(st)}`);
  assert.equal(st.reservedMilli, 0);
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['E2E-STK.0'] });

await E.step('E2E-STK.pos-view', '§۳۲.۲: پس از همگام‌سازی، برنامهٔ دکان همان موجودیِ سرور را دارد (فروشِ اینترنتی هم کم شده)', async () => {
  await A.drain();
  const server = (await stockOf(A, rice)).levels.shop_floor / 1000;
  assert.equal(A.stock(rice), server, `برنامهٔ دکان ${A.stock(rice)} نشان می‌دهد، سرور ${server} — فروشِ اینترنتی به دفترِ دستگاه برنمی‌گردد`);
}, { needs: ['E2E-STK.online'] });

await E.finish();
