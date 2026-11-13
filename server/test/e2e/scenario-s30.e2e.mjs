/**
 * سناریوی S30 (§۷.۳) — قطعِ سراسریِ اینترنت حینِ فروشِ اینترنتی (WEB-25، SYN-12).
 *
 *   «همهٔ دستگاه‌های فروشگاه ۲۰ دقیقه آفلاین‌اند و ویترین روی سرورِ بیرونی بالاست.»
 *
 * بازیگرها: A (گوشیِ صاحب) و D (کامپیوترِ دکان) — برنامه در سطحِ پروتکل، هر دو با توکنِ پوشِ ثبت‌شده؛
 * مشتری از درِ API ِ ویترین. «۲۰ دقیقه آفلاین» = آخرین تماسِ دستگاه‌ها (`sync_devices.last_seen_at`)
 * ۲۰ دقیقه پیش و هیچ درخواستی از دستگاه‌ها (حالتِ هواپیما) — بی خواب و بی دست‌کاریِ ساعت.
 * پوش با `push.setDeliver` گرفته می‌شود (همان راهِ آزمونِ `notify.e2e.mjs`؛ FCM ِ واقعی نه).
 *
 * پرچم‌ها فقط برای همین دکان: shop.orders، shop.storefront، shop.order_notify.
 * اجرا:  node test/e2e/run.mjs scenario-s30
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { boot, assert } from './lib/harness.mjs';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const E = await boot({ name: 'scenario-s30' });
const require = createRequire(import.meta.url);
const push = require('../../src/lib/push');
const ON = require('../../src/lib/order-notify');
const { db } = E;
const sent = [];
push.setDeliver((row, message) => { sent.push({ token: row && (row.token || row.id), message }); });

/** سطرهایی که امروز ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  '«ویترین سفارشِ فوری را متوقف می‌کند … طبقِ تنظیم»: این سناریو پیش‌فرض («ثبت با پیامِ تأخیر»، §۳۱.۲ بندِ ۶) را می‌سنجد؛ «توقف» تنظیمِ `offlinePause` ِ میزِ سفارش است (مهاجرتِ ۸۲۰، پشتِ پرچمِ خاموشِ `shop.orders.desk`) و در `order-desk.test.js` «S30» آزموده می‌شود',
  'رسیدنِ هشدار روی گوشیِ اندروید در پس‌زمینه FCM می‌خواهد (ORD-02، NTF-05 — BLOCKED-EXTERNAL)؛ این‌جا پوشِ سرور و صفِ سفارش‌های برنامه',
  'تشخیصِ «قطعِ سراسری» (SYN-12) در برنامه نیست؛ فقط backoff',
];

const owner = await E.owner('owner-s30@e2e.local', 'صاحبِ S30', 'دکانِ S30');
for (const k of ['shop.orders', 'shop.storefront', 'shop.order_notify']) await E.flag(owner.shopId, k, true);
//  E2E_FLAGS_ON: RSK-05/RSK-02 (`shop.order_risk`) همان سبد از همان شمارهٔ تأییدشده را تا ۱۰ دقیقه و مشتریِ تازه را بیش از دو سفارشِ باز رد
//  می‌کند؛ این سناریو عمداً همان سبد را چند بار می‌گذارد تا به وضعیت‌های دیگر برسد ⇒ «بی پرچمِ ریسک» صریح (خودِ قاعده: order-risk.test.js)
await E.flag(owner.shopId, 'shop.order_risk', false);
const SLUG = 'e2e-s30-store';
const PHONE = '0791230030';
const FA = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '..', '..', 'public', 'kit', 'i18n', 'fa.json'), 'utf8'));
let A; let D; let rice; let ticket = '';
const placed = [];
const status = async () => E.ok(await E.get(`/api/store/${SLUG}`), 'ویترین').store.status;

await E.step('S30.0', 'دکان روشن: کالا از برنامه، ویترین، دو دستگاه با توکنِ پوش؛ ویترین «همین حالا به دکان می‌رسد»', async () => {
  A = await E.device('A-owner-phone', owner.email);
  D = await E.device('D-shop-desk', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  assert.equal((await D.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 20 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ S30', contactPhone: '0700000030', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  E.ok(await A.api('POST', '/me/push', { token: 's30-push-phone' }), 'پوشِ گوشی', 201);
  E.ok(await D.api('POST', '/me/push', { token: 's30-push-desk' }), 'پوشِ کامپیوتر', 201);
  assert.equal((await status()).orderAcceptance, 'accepting');
  const st = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { email: mailOf(PHONE) }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { email: mailOf(PHONE), code: st.devCode }), 'تأییدِ شماره').ticket;
});

await E.step('S30.outage', '۲۰ دقیقه همهٔ دستگاه‌ها آفلاین ⇒ ویترین پیامِ تأخیر نشان می‌دهد؛ سفارش‌ها ثبت و در صف می‌مانند', async () => {
  A.offline = true; D.offline = true;
  await db.query(`UPDATE sync_devices SET last_seen_at = $2 WHERE app='shop' AND account_id = $1`, [owner.shopId, Date.now() - 20 * 60_000]);
  const st = await status();
  assert.equal(st.orderAcceptance, 'delayed', JSON.stringify(st).slice(0, 300));
  assert.notEqual(st.connection, 'confirmed');
  assert.match(FA['accept.delayed'], /دیرتر بررسی/, 'متنِ همان کلید در صفحهٔ ویترین');
  for (let i = 0; i < 2; i++) {
    const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
      fulfillment: 'pickup', customer: { name: `مشتریِ S30 ${i + 1}`, phone: PHONE }, lines: [{ productId: rice, qtyMilli: 1000 }],
    }, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket }), `سفارشِ ${i + 1}`, 201);
    placed.push(r.order.orderNo);
  }
  const rows = await db.many('SELECT status FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [owner.shopId]);
  assert.deepEqual(rows.map((r) => r.status), ['new', 'new'], 'هر دو در صفِ «تازه»');
}, { needs: ['S30.0'] });

await E.step('S30.alerts', 'سرور برای هر سفارش یک هشدار دارد و هیچ هشدارِ جدای «آفلاین» برای هیچ دستگاهی نمی‌فرستد', async () => {
  const box = await db.many('SELECT order_no, event FROM order_notify_outbox WHERE shop_id=$1 ORDER BY order_no', [owner.shopId]);
  assert.deepEqual(box.map((b) => [Number(b.order_no), b.event]), placed.map((n) => [Number(n), 'order_created']), 'یک ردیف برای هر سفارش، نه برای هر دستگاه');
  await ON.tick(Date.now() + 1000);
  assert.ok(sent.length >= 2, `پوش: ${sent.length}`);
  for (const s of sent) {
    const text = JSON.stringify(s.message);
    assert.equal(/آفلاین|offline|قطع/i.test(text), false, `پیامِ آفلاین: ${text}`);
    assert.ok(placed.some((n) => text.includes(String(n)) || text.includes(String(n).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[+d]))), `پیامِ بی سفارش: ${text}`);
  }
}, { needs: ['S30.outage'] });

await E.step('S30.back', 'پس از وصل شدن: ویترین «همین حالا»، و هر دو سفارش روی دستگاه‌ها در صفِ «تازه»', async () => {
  A.offline = false; D.offline = false;
  assert.equal((await A.sync()).ok, true);
  assert.equal((await status()).orderAcceptance, 'accepting');
  for (const dev of [A, D]) {
    const list = E.ok(await dev.api('GET', '/shop/orders?status=new'), `صفِ ${dev.label}`);
    const nos = (list.items || list.orders || []).map((o) => Number(o.orderNo));
    for (const n of placed) assert.ok(nos.includes(Number(n)), `${dev.label}: سفارشِ ${n} نیست (${nos})`);
  }
}, { needs: ['S30.outage'] });

await E.finish();
