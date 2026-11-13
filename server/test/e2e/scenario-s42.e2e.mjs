/**
 * سناریوی S42 (§۷.۴) — شلوغی (ORD-08، ORD-09).
 *
 *   • «شلوغم» برای ۳۰ دقیقه: زمانِ تحویلِ ویترین ۲۰ دقیقه بیشتر می‌شود و پس از ۳۰ دقیقه خودکار برمی‌گردد.
 *   • بیش از ۱۰ سفارشِ باز یا قدیمی‌ترین بیش از ۳۰ دقیقه ⇒ توقفِ خودکار (اگر روشن باشد) با پیام به مدیر.
 *
 * بازیگرها: A — گوشیِ صاحب و M — گوشیِ مدیر (برنامه در سطحِ پروتکل، هر دو با توکنِ پوش)؛ مشتری از درِ API ِ
 * ویترین. «گذشتِ زمان» بی خواب: مهلتِ ثبت‌شده به گذشته، یا زمانِ دور به کارگرِ هر دقیقه (`order-desk.sweep`).
 * پوش با `push.setDeliver` (FCM ِ واقعی نه).
 *
 * پرچم‌ها فقط برای همین دکان: shop.orders، shop.storefront، shop.orders.desk، shop.storefront.eta، shop.order_notify.
 * اجرا:  node test/e2e/run.mjs scenario-s42
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

//  کشِ ۳۰ ثانیه‌ایِ پاسخِ عمومیِ «زمانِ تحویل» خاموش: هر خواندن همان لحظه را نشان دهد (بی خواب)
const E = await boot({ name: 'scenario-s42', env: { STORE_ETA_CACHE_MS: '0' } });
const require = createRequire(import.meta.url);
const push = require('../../src/lib/push');
const desk = require('../../src/lib/order-desk');
const eta = require('../../src/lib/storefront-eta');
const { db } = E;
const pushes = [];
push.setDeliver((row, message) => { pushes.push({ ...message, to: row.user_id }); });

/** سطرهایی که امروز ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  'کلیدِ Ctrl+B ِ برنامه (`orders.busy`، زنده) همان `repo.busy(d, 30)` ِ دکمه را می‌زند — سنجه‌اش JVM است (ScenarioS42KeyTest، OrderDeskTest)؛ این‌جا همان درِ `POST /api/shop/orders/desk/busy` با ۳۰ دقیقه',
  'رسیدنِ پیامِ مدیر روی اندروید در پس‌زمینه FCM می‌خواهد (NTF-05 — BLOCKED-EXTERNAL)؛ این‌جا پوشِ سرور',
];

const owner = await E.owner('owner-s42@e2e.local', 'صاحبِ S42', 'دکانِ S42');
const mgrAcct = await E.staff(owner, 'manager-s42@e2e.local', 'مدیرِ S42', 'manager');
for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.desk', 'shop.storefront.eta', 'shop.order_notify']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-s42-store';
const PHONE = '0791230042';
const MIN = 60_000;
let A; let M; let rice; let ticket = '';
const span = (e) => e && [e.minMinutes, e.maxMinutes];
const pickupEta = async () => span(E.ok(await E.get(`/api/store/${SLUG}/eta`), 'زمانِ تحویل').eta.pickup);
const pub = async () => E.ok(await E.get(`/api/store/${SLUG}`), 'ویترین').store.status;
const deskOf = async () => E.ok(await A.api('GET', '/shop/orders/desk'), 'میزِ سفارش');
const ev = () => E.key('desk-ev');
const checkout = () => E.post(`/api/store/${SLUG}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتریِ S42', phone: PHONE }, lines: [{ productId: rice, qtyMilli: 1000 }],
}, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket });
/** سفارشِ تلفنیِ کارمند که همان دم قبول می‌شود: «باز» است ولی «تازهٔ بی‌پاسخ» نه */
async function openOrder(label) {
  const r = E.ok(await A.api('POST', '/shop/orders', {
    source: 'phone', fulfillment: 'pickup', customer: { name: 'تلفنی' }, lines: [{ name: 'چای', qtyMilli: 1000, unitPriceMinor: 5000 }],
  }, { headers: { 'Idempotency-Key': E.key('st') } }), label, 201);
  const id = r.order?.id || r.id;
  E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to: 'accepted', from: 'new', readyInMinutes: 30 }), `قبولِ ${label}`);
}

await E.step('S42.0', 'دکان روشن: کالا، ویترین، صاحب و مدیر با توکنِ پوش؛ زمانِ تحویلِ پایه', async () => {
  A = await E.device('A-owner-phone', owner.email);
  M = await E.device('M-manager-phone', mgrAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 50 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ S42', contactPhone: '0700000042', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  E.ok(await A.api('POST', '/me/push', { token: 's42-push-owner' }), 'پوشِ صاحب', 201);
  E.ok(await M.api('POST', '/me/push', { token: 's42-push-manager' }), 'پوشِ مدیر', 201);
  const st = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { email: mailOf(PHONE) }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { email: mailOf(PHONE), code: st.devCode }), 'تأییدِ شماره').ticket;
  assert.deepEqual(await pickupEta(), span(eta.range(15)), 'بی سابقه: پیش‌فرضِ ۱۵ دقیقه');
});

await E.step('S42.busy', '«شلوغم» ۳۰ دقیقه ⇒ زمانِ تحویلِ ویترین ۲۰ دقیقه بیشتر؛ پس از ۳۰ دقیقه خودکار برمی‌گردد', async () => {
  const t0 = Date.now();
  const r = E.ok(await A.api('POST', '/shop/orders/desk/busy', { eventId: ev(), minutes: 30 }), 'شلوغم');
  assert.equal(r.state.busy.extraMinutes, 20, 'پیش‌فرضِ ۲۰ دقیقهٔ افزوده');
  assert.ok(r.state.busy.until >= t0 + 30 * MIN && r.state.busy.until <= Date.now() + 30 * MIN);
  assert.deepEqual(await pickupEta(), span(eta.range(15 + 20)));
  assert.equal((await pub()).busy.extraMinutes, 20);
  //  «۳۰ دقیقه بعد»: مهلتِ ثبت‌شده گذشت ⇒ بی هیچ کاری برمی‌گردد
  await db.query('UPDATE order_desk SET busy_until=$2 WHERE shop_id=$1', [owner.shopId, Date.now() - 1000]);
  assert.equal((await deskOf()).busy, null);
  assert.equal((await pub()).busy, undefined);
  assert.deepEqual(await pickupEta(), span(eta.range(15)));
}, { needs: ['S42.0'] });

await E.step('S42.oldest', 'توقفِ خودکار (روشن، پیش‌فرض‌های سند ۱۰ / ۳۰ / ۳۰): سفارشِ تازهٔ بی‌پاسخِ قدیمی‌تر از ۳۰ دقیقه ⇒ توقف و پیام به صاحب و مدیر', async () => {
  E.ok(await A.api('PUT', '/shop/orders/desk/settings', { eventId: ev(), autoPause: { enabled: true } }), 'روشن کردنِ توقفِ خودکار');
  const g = await deskOf();
  assert.deepEqual([g.autoPause.enabled, g.autoPause.maxOpen, g.autoPause.oldestMinutes, g.autoPause.minutes], [true, 10, 30, 30]);
  const r = E.ok(await checkout(), 'سفارشِ ویترین', 201);
  await desk.sweep();
  assert.equal((await deskOf()).pause, null, 'تازه است ⇒ نه');
  await db.query('UPDATE online_orders SET created_at=$2 WHERE shop_id=$1 AND order_no=$3', [owner.shopId, Date.now() - 31 * MIN, r.order.orderNo]);
  pushes.length = 0;
  await desk.sweep();
  const p = (await deskOf()).pause;
  assert.deepEqual([p && p.source, p && p.reason], ['auto', 'overloaded']);
  assert.equal((await checkout()).body.error.code, 'store_paused', 'ویترین سفارشِ تازه نمی‌گیرد');
  assert.ok(pushes.some((m) => /خودکار متوقف شد/.test(m.title) && /۳۱|31/.test(m.body)), JSON.stringify(pushes));
}, { needs: ['S42.0'] });

await E.step('S42', 'بیش از ۱۰ سفارشِ باز ⇒ توقفِ خودکار، با پیام به مدیر و صاحب', async () => {
  //  آن سفارشِ قدیمی را دکان قبول می‌کند و «ادامه» می‌زند؛ پس از مهلتِ «ادامه» فقط شمار می‌سنجد
  const oldId = (await db.one(`SELECT id FROM online_orders WHERE shop_id=$1 AND status='new'`, [owner.shopId])).id;
  E.ok(await A.api('POST', `/shop/orders/${oldId}/transition`, { to: 'accepted', from: 'new', readyInMinutes: 10 }), 'قبول');
  E.ok(await A.api('POST', '/shop/orders/desk/resume', { eventId: ev() }), 'ادامه');
  for (let i = 0; i < 9; i++) await openOrder(`سفارشِ ${i + 2}`);
  const later = Date.now() + 31 * MIN;   // پس از مهلتِ یک دورِ توقف
  await desk.sweep(later);
  assert.equal((await deskOf()).queue.open, 10);
  assert.equal((await db.one(`SELECT COUNT(*)::int AS n FROM order_desk_events WHERE shop_id=$1 AND kind='auto_pause'`, [owner.shopId])).n, 1, '۱۰ = سقف، نه بیشتر');
  await openOrder('سفارشِ ۱۱');
  pushes.length = 0;
  await desk.sweep(later);
  const g = await deskOf();
  assert.equal(g.queue.open, 11);
  assert.deepEqual([g.pause.source, g.pause.reason], ['auto', 'overloaded']);
  assert.equal((await db.one(`SELECT COUNT(*)::int AS n FROM order_desk_events WHERE shop_id=$1 AND kind='auto_pause'`, [owner.shopId])).n, 2);
  const auto = pushes.filter((m) => /خودکار متوقف شد/.test(m.title));
  assert.ok(auto.length >= 1 && auto.every((m) => /۱۱|11/.test(m.body)), JSON.stringify(pushes));
  //  گیرنده‌ها: صاحب و مدیر
  assert.deepEqual([...new Set(auto.map((m) => m.to))].sort(), [owner.user.id, mgrAcct.user.id].sort(), 'پیام به صاحب و مدیر');
}, { needs: ['S42.oldest'] });

await E.finish();
