/**
 * سناریوی S38 (§۷.۴) — افزودنِ قلم پس از ثبت (CHK-21).
 *
 *   نتیجه: تا پیش از «شروعِ جمع‌آوری» قلم به همان سفارش اضافه می‌شود و زمانِ تحویل حفظ می‌شود؛
 *   بعد از آن پیامِ «سفارش در حالِ آماده‌سازی است» با گزینهٔ سفارشِ جدید.
 *
 * بازیگرها: مشتری از درِ API ِ صفحهٔ پیگیریِ ویترین (توکنِ پیگیری + شمارهٔ تأییدشده)؛ A — گوشیِ صاحب
 * (برنامه در سطحِ پروتکل): قبول با زمانِ آماده و «شروعِ جمع‌آوری».
 * پرچم‌ها فقط برای همین دکان: shop.orders، shop.storefront، shop.orders.picking، shop.orders.add_items.
 * اجرا:  node test/e2e/run.mjs scenario-s38
 */
import { boot, assert } from './lib/harness.mjs';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const E = await boot({ name: 'scenario-s38' });
const { db } = E;

/** سطرهایی که امروز به کاربر نمی‌رسند — سند، نه assert */
export const PENDING = [
  'پرچمِ `shop.orders.add_items` پیش‌فرض خاموش است (CHK-21)؛ دکمه و پیام در صفحهٔ پیگیری با `web-store-add.mjs` (کرومیوم) سنجیده می‌شود، نه این‌جا',
];

const owner = await E.owner('owner-s38@e2e.local', 'صاحبِ S38', 'دکانِ S38');
for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.picking', 'shop.orders.add_items']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-s38-store';
const PHONE = '0791230038';
let A; let rice; let oil; let ticket = ''; let o;
const H = () => ({ 'X-Store-Ticket': ticket });
const row = () => db.one('SELECT status, ready_at, delivery, total_minor, items_count FROM online_orders WHERE id=$1', [o.id]);
const add = (body) => E.post(`/api/store/${SLUG}/orders/${encodeURIComponent(o.token)}/items`, body, '', { ...H(), 'Idempotency-Key': E.key('add') });

await E.step('S38.0', 'سفارشِ ویترین ⇒ دکان با «آماده تا ۲۰ دقیقه» قبول می‌کند', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 10 });
  oil = A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 10 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ S38', contactPhone: '0700000038', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  for (const id of [rice, oil]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), 'انتشار');
  const st = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { email: mailOf(PHONE) }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { email: mailOf(PHONE), code: st.devCode }), 'تأییدِ شماره').ticket;
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ S38', phone: PHONE }, lines: [{ productId: rice, qtyMilli: 1000 }],
  }, '', { ...H(), 'Idempotency-Key': E.key('ck') }), 'ثبت', 201);
  o = { token: r.order.token, no: r.order.orderNo };
  o.id = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, o.no])).id;
  E.ok(await A.api('POST', `/shop/orders/${o.id}/transition`, { to: 'accepted', from: 'new', readyInMinutes: 20 }), 'قبول');
  assert.ok(Number((await row()).ready_at) > Date.now());
});

let before;
await E.step('S38.before', 'پیش از «شروعِ جمع‌آوری»: قلم به همان سفارش اضافه می‌شود و زمانِ تحویل همان می‌ماند', async () => {
  before = await row();
  const t = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(o.token)}`, '', H()), 'پیگیری').order;
  assert.deepEqual(t.addItems, { allowed: true, preparing: false });
  const r = E.ok(await add({ lines: [{ productId: oil, qtyMilli: 2000 }] }), 'افزودن', 201);
  assert.equal(r.replayed, false);
  const after = await row();
  assert.equal(after.status, 'accepted', 'همان سفارش، همان وضعیت');
  assert.equal(Number(after.items_count), 2);
  assert.equal(Number(after.total_minor), Number(before.total_minor) + 2 * 30000);
  assert.equal(Number(after.ready_at), Number(before.ready_at), 'زمانِ آماده/تحویل حفظ شد');
  assert.deepEqual(after.delivery, before.delivery);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [owner.shopId])).n, 1, 'سفارشِ دوم ساخته نشد');
}, { needs: ['S38.0'] });

await E.step('S38', 'پس از «شروعِ جمع‌آوری»: «سفارش در حالِ آماده‌سازی است» با گزینهٔ سفارشِ جدید؛ هیچ تغییری؛ سفارشِ جدید ثبت می‌شود', async () => {
  E.ok(await A.api('POST', `/shop/orders/${o.id}/pick/start`, {}), 'شروعِ جمع‌آوری');
  const snap = await row();
  const late = await add({ lines: [{ productId: rice, qtyMilli: 1000 }] });
  assert.equal(late.status, 409, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'order_preparing');
  assert.match(late.body.error.message, /در حال آماده‌سازی است/);
  assert.equal(late.body.error.newOrder, true, 'گزینهٔ سفارشِ جدید');
  assert.deepEqual(await row(), snap, 'هیچ تغییری');
  const t = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(o.token)}`, '', H()), 'پیگیری').order;
  assert.deepEqual(t.addItems, { allowed: false, preparing: true });
  //  گزینهٔ سفارشِ جدید: همان قلم با سفارشِ تازه
  const fresh = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ S38', phone: PHONE }, lines: [{ productId: rice, qtyMilli: 1000 }],
  }, '', { ...H(), 'Idempotency-Key': E.key('ck') }), 'سفارشِ جدید', 201);
  assert.notEqual(fresh.order.orderNo, o.no);
}, { needs: ['S38.before'] });

await E.finish();
