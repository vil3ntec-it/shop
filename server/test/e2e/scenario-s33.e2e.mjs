/**
 * سناریوی S33 (§۷.۴) — نشانیِ بدونِ کدِ پستی (CHK-08، CHK-10، LOC-10).
 *
 *   نتیجه: هیچ فیلدی جز نام، شماره و پین اجباری نیست؛ Plus Code از روی پین ساخته می‌شود؛
 *   پیک کارتِ «اول نشانه» را می‌بیند.
 *
 * بازیگرها: مشتریِ مهمان از درِ API ِ ویترین (شمارهٔ تأییدشده)؛ A — گوشیِ صاحب (برنامه در سطحِ پروتکل)؛
 * K — گوشیِ پیکِ دکان. پرچم‌ها فقط برای همین دکان: shop.orders، shop.storefront، shop.delivery.
 * اجرا:  node test/e2e/run.mjs scenario-s33
 */
import { boot, assert } from './lib/harness.mjs';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const E = await boot({ name: 'scenario-s33' });
const { db } = E;

/** سطرهایی که امروز ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  '«Plus Code از روی پین ساخته می‌شود»: ساخته نمی‌شود — `orders.parseAddress` فقط Plus Code ِ تایپ‌شده را می‌سنجد و نگه می‌دارد (CHK-08)؛ هیچ رمزگذارِ Open Location Code در سرور، برنامه و core نیست',
  'چیدنِ کارت روی گوشیِ پیک (CourierHome.kt، نشانه با قلمِ درشت) فقط در برنامه است؛ این‌جا ترتیبِ کلیدهای کارتِ سرور',
];

const owner = await E.owner('owner-s33@e2e.local', 'صاحبِ S33', 'دکانِ S33');
const courierAcct = await E.staff(owner, 'courier-s33@e2e.local', 'پیکِ S33');
for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-s33-store';
const PHONE = '0791230033';
const PIN = { lat: 34.5281, lng: 69.1723 };
let A; let K; let rice; let ticket = ''; let courierId; let placed;
const checkout = (body) => E.post(`/api/store/${SLUG}/checkout`, {
  fulfillment: 'delivery', paymentMethod: 'cod', lines: [{ productId: rice, qtyMilli: 1000 }], ...body,
}, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket });

await E.step('S33.0', 'دکان: کالا از برنامه، ویترینِ ارسال، پیکِ دکان؛ شمارهٔ مشتری تأیید', async () => {
  A = await E.device('A-owner-phone', owner.email);
  K = await E.device('K-courier-phone', courierAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 10 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ S33', contactPhone: '0700000033', deliveryFeeMinor: 3000, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  courierId = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: courierAcct.user.id, cashCapMinor: 5_000_000 }), 'پیک', 201).courier.id;
  const st = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { email: mailOf(PHONE) }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { email: mailOf(PHONE), code: st.devCode }), 'تأییدِ شماره').ticket;
});

await E.step('S33.required', 'فقط نام، شماره و پین ⇒ سفارش ثبت می‌شود؛ بی نام یا بی هیچ نشانی ⇒ رد با خطای روشن', async () => {
  const ok = E.ok(await checkout({ customer: { name: 'مهمانِ S33', phone: PHONE }, address: { ...PIN } }), 'فقط نام، شماره و پین', 201);
  const o = await db.one('SELECT address FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, ok.order.orderNo]);
  const addr = typeof o.address === 'string' ? JSON.parse(o.address) : o.address;
  assert.deepEqual({ lat: addr.lat, lng: addr.lng }, PIN);
  //  کدِ پستی، شهر، کوچه و … هیچ‌کدام خواسته نشد
  const noName = await checkout({ customer: { name: '', phone: PHONE }, address: { ...PIN } });
  assert.equal(noName.status, 400);
  assert.equal(noName.body.error.code, 'bad_customer');
  const noWhere = await checkout({ customer: { name: 'مهمانِ S33', phone: PHONE }, address: { postalCode: '1001' } });
  assert.equal(noWhere.status, 400);
  assert.equal(noWhere.body.error.code, 'bad_address', 'کدِ پستی جای پین/نشانه را نمی‌گیرد');
}, { needs: ['S33.0'] });

await E.step('S33.courier', 'پیک کارتِ «اول نشانه» را می‌بیند: نشانه، ناحیه و رنگِ دروازه پیش از متنِ نشانی', async () => {
  placed = E.ok(await checkout({
    customer: { name: 'مهمانِ S33', phone: PHONE },
    address: { ...PIN, landmark: 'روبه‌روی نانوایی', area: 'کارته‌سه', gateColor: 'سبز', text: 'سرکِ دوم' },
  }), 'سفارشِ با نشانه', 201);
  const id = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, placed.order.orderNo])).id;
  for (const [to, from] of [['accepted', 'new'], ['picking', 'accepted'], ['packed', 'picking']]) {
    E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to, from, ...(to === 'accepted' ? { readyInMinutes: 10 } : {}) }), to);
  }
  E.ok(await A.api('POST', `/shop/delivery/orders/${id}/assign`, { mode: 'shop_courier', courierId, fareMinor: 3000 }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201);
  const card = E.ok(await K.api('GET', '/courier/missions'), 'مأموریت').items.find((x) => x.orderNo === placed.order.orderNo);
  assert.ok(card, 'کارت نرسید');
  assert.deepEqual(Object.keys(card.address).slice(0, 4), ['landmark', 'area', 'gateColor', 'text'], 'اول نشانه');
  assert.deepEqual([card.address.landmark, card.address.area, card.address.gateColor], ['روبه‌روی نانوایی', 'کارته‌سه', 'سبز']);
  assert.deepEqual({ lat: card.address.lat, lng: card.address.lng }, PIN, 'پین برای نقشه');
}, { needs: ['S33.0'] });

await E.finish();
