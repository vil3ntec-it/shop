/**
 * سرتاسریِ پیگیریِ مشتری با پیک — DLV-06 (§۶.۱۸): «لینکِ پیگیری برای مشتری: وضعیت، زمانِ تقریبی، نامِ پیک».
 *
 *   مشتری از ویترین سفارشِ «ارسال» می‌دهد و صفحهٔ پیگیری را در کرومیومِ واقعی (عرضِ گوشی) باز می‌کند؛
 *   A (گوشیِ صاحب) می‌سپارد و K (گوشیِ پیک) می‌پذیرد، راه می‌افتد، زمانِ تقریبی می‌گذارد، می‌رسد و با
 *   همان کدی که مشتری روی صفحه دید تحویل می‌دهد. در هر گام صفحه همان را نشان می‌دهد که سرور می‌گوید:
 *   ⛔ بی پرچمِ `shop.delivery_customer_view` هیچ بخشِ پیکی نیست؛ با پرچم فقط نامِ کوچک (نه نامِ خانوادگی،
 *   نه شماره)، «رسیده است»، ساعتِ تقریبی و کدِ تحویل؛ پس از تحویل هیچ — و هیچ خطای اسکریپتی در صفحه.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.delivery، shop.catalog.
 * اجرا:  node test/e2e/track.e2e.mjs
 */
import { boot, assert, fa } from './lib/harness.mjs';

const E = await boot({ name: 'track' });
const { db } = E;
const owner = await E.owner('owner-track@e2e.local', 'صاحبِ پیگیری', 'دکانِ پیگیری');
const courierAcct = await E.staff(owner, 'courier-track@e2e.local', 'پیکِ پیگیری');
for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.catalog']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-track-store';
const PHONE = '0791237788';
const COURIER_PHONE = '0799990011';
const VIEW_FLAG = 'shop.delivery_customer_view';

let A; let K; let rice; let courierId; let ticket; let pg;
let ev = 0;
const event = (assignmentId, type, extra = {}) => ({ eventId: `e2e-tr-${Date.now()}-${++ev}`, assignmentId, type, deviceTime: Date.now(), deviceSeq: ev, ...extra });
async function courierOk(events) {
  const r = E.ok(await K.api('POST', '/courier/events', { deviceId: K.deviceId, events }), 'رویدادِ پیک');
  for (const x of r.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

/** سفارشِ ارسالِ نقدی از درِ همان API که ویترین می‌زند، آمادهٔ سپردن */
async function packedOrder() {
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ پیگیری', phone: PHONE },
    lines: [{ productId: rice, qtyMilli: 1000 }], address: { text: 'کارتهٔ سه', landmark: 'روبه‌روی نانوایی' },
  }, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': E.key('web') }), 'ثبتِ سفارش', 201);
  const row = await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, r.order.orderNo]);
  for (const [to, from] of [['accepted', 'new'], ['picking', 'accepted'], ['packed', 'picking']]) {
    E.ok(await A.api('POST', `/shop/orders/${row.id}/transition`, { to, from, ...(to === 'accepted' ? { readyInMinutes: 15 } : {}) }), to);
  }
  return { id: row.id, token: r.order.token };
}
const assign = async (o) => E.ok(await A.api('POST', `/shop/delivery/orders/${o.id}/assign`,
  { mode: 'shop_courier', courierId, requireCode: true }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201);

/** صفحهٔ پیگیری را از نو باز می‌کند (همان کاری که مشتری با «تازه کردن» می‌کند) و حالِ بخشِ پیک را می‌خواند */
async function viewOf(token) {
  const page = pg.page;
  //  همان نشانی فقط با `#` ⇒ مرورگر بار نمی‌کند؛ پس اول از صفحه بیرون
  await page.goto('about:blank');
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(token)}`);
  await page.waitForFunction(() => {
    const s = document.getElementById('o-status');
    return !document.getElementById('view-order').hidden && s && s.textContent.trim() !== '';
  }, null, { timeout: 15_000 });
  return page.evaluate(() => {
    const vis = (id) => { const e = document.getElementById(id); return Boolean(e) && !e.hidden && e.offsetParent !== null; };
    const txt = (id) => (vis(id) ? document.getElementById(id).textContent.trim() : '');
    return {
      card: vis('o-dlv'), courier: txt('o-courier'), eta: txt('o-eta'), code: vis('o-code') ? txt('o-code-n') : '',
      status: document.getElementById('o-status').textContent.trim(),
      body: document.getElementById('view-order').textContent,
    };
  });
}
const latin = (s) => String(s).replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0));

await E.step('DLV-06.0', 'کالا از برنامه، ویترین روشن، پیکِ دکان با نام و شماره', async () => {
  A = await E.device('A-owner-phone', owner.email);
  K = await E.device('K-courier-phone', courierAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 5 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ پیگیری', contactPhone: '0700000009', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  const c = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: courierAcct.user.id, name: 'کریم احمدی', phone: COURIER_PHONE, cashCapMinor: 5_000_000 }), 'پیک', 201);
  courierId = c.courier.id;
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone: PHONE }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone: PHONE, code: s.devCode }), 'تأییدِ شماره').ticket;
  pg = await E.page('customer', { width: 360, height: 780 });
});

await E.step('DLV-06.off', 'بی پرچمِ نمای پیک: سپرده و پذیرفته، ولی صفحه هیچ بخشِ پیکی ندارد', async () => {
  const o = await packedOrder();
  const a = await assign(o);
  await courierOk([event(a.assignment.id, 'assigned')]);
  const api = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(o.token)}`), 'پیگیری').order;
  assert.equal('delivery' in api, false, 'سرور بی پرچم کلیدِ delivery نمی‌دهد');
  const v = await viewOf(o.token);
  assert.equal(v.card, false, JSON.stringify(v));
  assert.equal(v.body.includes('کریم'), false, 'نامِ پیک بی پرچم پیدا شد');
}, { needs: ['DLV-06.0'] });

await E.step('DLV-06.page', 'با پرچم: کد ⇐ نامِ کوچک ⇐ ساعتِ تقریبی ⇐ «رسیده است» ⇐ تحویل با همان کدِ صفحه ⇐ هیچ', async () => {
  await E.flag(owner.shopId, VIEW_FLAG, true);
  const o = await packedOrder();
  const a = await assign(o);
  const id = a.assignment.id;

  //  هنوز نپذیرفته: فقط کد (رقمِ فارسی)، بی نام و بی زمان
  let v = await viewOf(o.token);
  assert.equal(v.card, true, JSON.stringify(v));
  assert.equal(v.code, fa(a.code), 'کدِ صفحه همان کدِ کارمند است');
  assert.equal(v.courier, '');
  assert.equal(v.eta, '');

  //  پذیرفت: نامِ کوچک، نه نامِ خانوادگی، نه شماره
  await courierOk([event(id, 'assigned')]);
  v = await viewOf(o.token);
  assert.match(v.courier, /کریم/);
  for (const secret of ['احمدی', COURIER_PHONE, fa(COURIER_PHONE), courierId]) assert.equal(v.body.includes(secret), false, `نباید باشد: ${secret}`);

  //  در راه با زمانِ تقریبیِ خودِ پیک ⇒ ساعت به زبانِ صفحه
  await courierOk([event(id, 'picked_up'), event(id, 'moving')]);
  E.ok(await K.api('POST', `/courier/missions/${id}/eta`, { minutes: 20 }), 'زمانِ تقریبی');
  const etaAt = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(o.token)}`), 'پیگیری').order.delivery.etaAt;
  assert.ok(etaAt > Date.now(), 'زمانِ تقریبی در آینده');
  v = await viewOf(o.token);
  const clock = await pg.page.evaluate((at) => window.K.clock(at), etaAt);
  assert.ok(clock && v.eta.includes(clock), `ساعتِ تقریبی: «${v.eta}» ⊅ «${clock}»`);
  assert.equal(v.code, fa(a.code));

  //  پیگیری پس از نمایش خطا داد ⇒ کد و نامِ کهنه روی صفحه نمی‌ماند
  const ORDER_API = '**/api/store/*/orders/*';
  await pg.page.route(ORDER_API, (r) => r.fulfill({ status: 503, contentType: 'application/json', body: '{"error":{"code":"unavailable","message":"-"}}' }));
  await pg.page.evaluate(() => { location.hash = '#/'; });
  await pg.page.evaluate((tk) => { location.hash = '#/o/' + encodeURIComponent(tk); }, o.token);
  await pg.page.waitForFunction(() => document.getElementById('o-status').classList.contains('err'), null, { timeout: 15_000 });
  assert.equal(await pg.page.isHidden('#o-dlv'), true, 'بخشِ پیک پس از خطا ماند');
  await pg.page.unroute(ORDER_API);

  //  رسید: «رسیده است»، زمانِ تقریبی دیگر نیست
  await courierOk([event(id, 'arrived')]);
  v = await viewOf(o.token);
  assert.match(v.courier, /کریم.*رسیده است/);
  assert.equal(v.eta, '');

  //  پیک کدی را می‌پرسد که مشتری روی صفحه دید ⇒ تحویل؛ صفحه دیگر نه کد دارد نه پیک
  await courierOk([event(id, 'delivered', { cashCollectedMinor: 60000, code: latin(v.code) })]);
  v = await viewOf(o.token);
  assert.equal(v.status, 'تحویل شد');
  assert.equal(v.card, false, JSON.stringify(v));
  assert.equal(v.body.includes(fa(a.code)), false, 'کدِ مصرف‌شده هنوز روی صفحه است');
}, { needs: ['DLV-06.0'] });

await E.step('DLV-06.clean', 'هیچ خطای اسکریپتی در صفحهٔ مشتری', async () => {
  assert.deepEqual(E.pageErrors, []);
}, { needs: ['DLV-06.0'] });

await E.finish();
