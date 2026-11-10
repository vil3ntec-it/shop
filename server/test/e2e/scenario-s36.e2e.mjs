/**
 * سناریوی S36 (§۷.۴) — سفارش وقتی اپِ فروشگاه در پس‌زمینه است (ORD-02، NTF-05).
 *
 *   گوشیِ فروشگاه: صفحه خاموش، اپ در پس‌زمینه، صرفه‌جوییِ باتری. کامپیوتر: برنامه کوچک‌شده در سینی.
 *
 * ⛔ سهمِ دستگاه بیرون از این سرور است: اندروید وابستگی و سرویسِ FCM ندارد و توکن ثبت نمی‌کند (ORD-02 و
 * NTF-05 هر دو BLOCKED-EXTERNAL)؛ برنامهٔ کامپیوتر فقط وقتی جلوی چشم است هر ۲۰ ثانیه می‌پرسد (ORD-01).
 * آنچه این‌جا سنجیده می‌شود سهمِ سرور است: پوشِ تکراری تا «دیدم» (یادآوری‌های محدود)، و سفارشی که
 * بی‌پاسخ ماند فقط یک بار از کانالِ پشتیبان به صاحب خبر داده می‌شود. پوش با `push.setDeliver` و کانالِ
 * پشتیبان با `order-notify.setFallback` گرفته می‌شوند (FCM و پیامکِ واقعی نه).
 *
 * پرچم‌ها فقط برای همین دکان: shop.orders، shop.storefront، shop.order_notify.
 * اجرا:  node test/e2e/run.mjs scenario-s36
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'scenario-s36' });
const require = createRequire(import.meta.url);
const push = require('../../src/lib/push');
const ON = require('../../src/lib/order-notify');
const { db } = E;
const pushes = [];
push.setDeliver((row, message) => { pushes.push(message); });
const fallbacks = [];

/** سطرهایی که امروز ساخته نشده‌اند یا بیرونی‌اند — سند، نه assert */
export const PENDING = [
  'اندروید: هشدارِ صوتیِ تکراری ظرفِ ≤ ۱۰ ثانیه با FCM یا سرویسِ پیش‌زمینه — FCM و سرویسِ پیش‌زمینه در برنامه نیست (BLOCKED-EXTERNAL: ORD-02، NTF-05)',
  'اندروید بی هر دو: «حداکثر ۱۵ دقیقه» و پیامِ نوارِ وضعیت «سفارش‌ها ممکن است دیر برسند» آزموده نشده',
  'کامپیوتر: هشدار ظرفِ ≤ ۱۰ ثانیه حتی در سینی — برنامه فقط وقتی جلوی چشم است هر ۲۰ ثانیه می‌پرسد',
  'کانالِ پشتیبانِ واقعی (تلگرام/پیامک) وصل نیست (D-09)؛ در تولید حالِ صادقانهٔ `not_configured`',
  'وب (آیفون): «استثنای ثبت‌شده» (بخشِ ۱.۳ بندِ ۷)',
];

const owner = await E.owner('owner-s36@e2e.local', 'صاحبِ S36', 'دکانِ S36');
for (const k of ['shop.orders', 'shop.storefront', 'shop.order_notify']) await E.flag(owner.shopId, k, true);
//  E2E_FLAGS_ON: RSK-05/RSK-02 (`shop.order_risk`) همان سبد از همان شمارهٔ تأییدشده را تا ۱۰ دقیقه و مشتریِ تازه را بیش از دو سفارشِ باز رد
//  می‌کند؛ این سناریو عمداً همان سبد را چند بار می‌گذارد تا به وضعیت‌های دیگر برسد ⇒ «بی پرچمِ ریسک» صریح (خودِ قاعده: order-risk.test.js)
await E.flag(owner.shopId, 'shop.order_risk', false);
const SLUG = 'e2e-s36-store';
const PHONE = '0791230036';
let A; let rice; let ticket = '';
const row = (orderNo) => db.one('SELECT * FROM order_notify_outbox WHERE shop_id=$1 AND order_no=$2', [owner.shopId, orderNo]);
async function place(name) {
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', customer: { name, phone: PHONE }, lines: [{ productId: rice, qtyMilli: 1000 }],
  }, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket }), 'سفارش', 201);
  return Number(r.order.orderNo);
}
/** کارگرِ سرور تا `n` دورِ «پنجرهٔ دیدن» جلوتر (بی خواب: زمانِ دور به کارگر داده می‌شود) */
async function rounds(n) {
  let t = Date.now();
  for (let i = 0; i < n; i++) { t += ON.ACK_WINDOW_MS + 1000; await ON.tick(t); }
}

await E.step('S36.0', 'دکان: کالا، ویترین، گوشیِ صاحب با توکنِ پوش (اپ بسته/پس‌زمینه = فقط پوش)', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 20 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ S36', contactPhone: '0700000036', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  E.ok(await A.api('POST', '/me/push', { token: 's36-push-phone' }), 'پوشِ گوشی', 201);
  const st = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone: PHONE }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone: PHONE, code: st.devCode }), 'تأییدِ شماره').ticket;
});

await E.step('S36.repeat', 'پوشِ تکراری تا «دیدم»: یک پوش و حداکثر دو یادآوری؛ «دیدم» زنجیره را می‌بندد', async () => {
  const no = await place('مشتریِ S36 دیده‌شده');
  await ON.tick(Date.now() + 1000);
  assert.equal(pushes.length, 1, 'پوشِ نخست');
  assert.equal((await row(no)).status, 'provider_accepted');
  await rounds(1);
  assert.equal(pushes.length, 2, 'یادآوریِ نخست');
  //  صاحب اعلان را باز می‌کند ⇒ زنگ می‌ایستد
  const id = (await row(no)).id;
  E.ok(await A.api('POST', `/shop/order-notify/${id}/ack`, { kind: 'opened' }), 'دیدم');
  await rounds(4);
  assert.equal(pushes.length, 2, 'پس از «دیدم» یادآوری نیامد');
  assert.equal((await row(no)).status, 'acked');
  assert.equal((await row(no)).fallback_at, null, 'دیده‌شده به کانالِ پشتیبان نرفت');
}, { needs: ['S36.0'] });

await E.step('S36', 'سفارشی که بی‌پاسخ ماند ⇒ پس از یادآوری‌ها فقط یک بار از کانالِ پشتیبان به صاحب خبر داده می‌شود', async () => {
  ON.setFallback({ channel: 'telegram', send: async (m) => { fallbacks.push(m); return { ok: true }; } });
  pushes.length = 0;
  const no = await place('مشتریِ S36 بی‌پاسخ');
  await ON.tick(Date.now() + 1000);
  await rounds(ON.MAX_REMINDERS + 3);
  assert.equal(pushes.length, 1 + ON.MAX_REMINDERS, 'پوش + یادآوری‌های محدود، نه بی‌پایان');
  assert.equal(fallbacks.length, 1, 'کانالِ پشتیبان یک بار');
  assert.equal(Number(fallbacks[0].orderNo), no);
  assert.equal(fallbacks[0].reason, 'unacked');
  const r = await row(no);
  assert.deepEqual([r.fallback_channel, r.fallback_state], ['telegram', 'provider_accepted']);
  assert.ok(r.fallback_at > 0);
  //  بی سرویس‌دهنده (تولیدِ امروز): حالِ صادقانه، هیچ پیامِ دروغین
  ON.setFallback(null);
  const no2 = await place('مشتریِ S36 بی کانال');
  await ON.tick(Date.now() + 1000);
  await rounds(ON.MAX_REMINDERS + 3);
  assert.equal((await row(no2)).fallback_state, 'not_configured');
  assert.equal(fallbacks.length, 1);
}, { needs: ['S36.0'] });

await E.finish();
