/**
 * سناریوی سرتاسریِ تحویل و پیک — E2E-DLV-07، E2E-DLV-08 (S39)، E2E-CASH-09 (S40).
 *
 *   مشتری از ویترین (API ِ همان صفحه) سفارشِ «ارسال» با پرداختِ نقد هنگامِ تحویل می‌دهد؛
 *   A (گوشیِ صاحب) قبول، جمع‌آوری و بسته‌بندی می‌کند و به پیکِ دکان می‌سپارد؛ K (گوشیِ پیک)
 *   مأموریت را می‌بیند (نشانه، شمارهٔ پوشیده، مبلغِ موردِ انتظار)، می‌گیرد، می‌رسد و با کد و
 *   نقد تحویل می‌دهد؛ مشتری «تحویل شد» را می‌بیند و چیزی از پیک و کد نمی‌بیند؛ تسویهٔ پیک
 *   نقد را از کرایه جدا نگه می‌دارد، کسری را با دلیل ثبت می‌کند و تسویهٔ دوباره را رد.
 *   سفارشِ دوم: ناموفق ⇐ تلاشِ دوباره ⇐ ناموفق ⇐ برگشت به دکان ⇐ بازگشت به قفسه؛ پولی گرفته
 *   نشده، و موجودیِ رزروشده (پرچمِ shop.catalog) آزاد می‌شود.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.delivery، shop.catalog.
 * اجرا:  node test/e2e/delivery.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'delivery' });
const { db } = E;
const owner = await E.owner('owner-dlv@e2e.local', 'صاحبِ ارسال', 'دکانِ ارسال');
const courierAcct = await E.staff(owner, 'courier-dlv@e2e.local', 'پیکِ دکان');
const clerkAcct = await E.staff(owner, 'clerk-dlv@e2e.local', 'شاگردِ انبار');
for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.catalog']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-delivery-store';
const order = (id) => db.one('SELECT status, cod_status, total_minor FROM online_orders WHERE id=$1', [id]);

/** مشتری از درِ همان API که صفحهٔ ویترین می‌زند */
async function customer(phone) {
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone }), 'کدِ تأیید');
  const v = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone, code: s.devCode }), 'تأییدِ شماره');
  const h = { 'X-Store-Ticket': v.ticket };
  return {
    checkout: (body, key = E.key('web')) => E.post(`/api/store/${SLUG}/checkout`, body, null, { ...h, 'Idempotency-Key': key }),
    track: (token) => E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(token)}`, null, h),
  };
}

let A; let K; let clerk; let rice; let courierId; let cust;
const placed = {};
let ev = 0;
const event = (assignmentId, type, extra = {}) => ({ eventId: `e2e-ev-${Date.now()}-${++ev}`, assignmentId, type, deviceTime: Date.now(), deviceSeq: ev, ...extra });
async function courierSend(events) {
  const r = await K.api('POST', '/courier/events', { deviceId: K.deviceId, events });
  E.ok(r, 'رویدادِ پیک');
  return r.body.results;
}
async function courierOk(events) {
  for (const x of await courierSend(events)) assert.equal(x.status, 'applied', JSON.stringify(x));
}
async function deliveryOrder(qty, landmark) {
  const body = {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ ارسال', phone: '0791234567' },
    lines: [{ productId: rice, qtyMilli: qty * 1000 }],
    address: { text: 'کارتهٔ چهار، کوچهٔ سوم', landmark }, delivery: { callBeforeArrival: true },
  };
  const r = E.ok(await cust.checkout(body), 'ثبتِ سفارشِ ارسال', 201);
  //  پاسخِ ویترین شناسهٔ درونی ندارد (فقط شماره و توکنِ پیگیری) — دکان همان سفارش را با شماره‌اش می‌شناسد
  const row = await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, r.order.orderNo]);
  return { ...r.order, id: row.id };
}
async function prepare(id) {
  E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to: 'accepted', from: 'new', readyInMinutes: 15 }), 'قبول');
  E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to: 'picking', from: 'accepted' }), 'جمع‌آوری');
  E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to: 'packed', from: 'picking' }), 'بسته‌بندی');
}

await E.step('E2E-DLV.0', 'کالا از برنامه، ویترین روشن، پیکِ دکان تعریف می‌شود', async () => {
  A = await E.device('A-owner-phone', owner.email);
  K = await E.device('K-courier-phone', courierAcct.email);
  clerk = await E.device('W-clerk-phone', clerkAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 5 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ ارسال', contactPhone: '0700000003', deliveryFeeMinor: 4000, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  const c = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: courierAcct.user.id, cashCapMinor: 5_000_000 }), 'پیک', 201);
  courierId = c.courier.id;
  cust = await customer('0791234567');
});

await E.step('E2E-DLV-07', 'سپردن ⇐ پیک می‌بیند (نشانه، شمارهٔ پوشیده، مبلغ) ⇐ می‌گیرد ⇐ می‌رسد ⇐ با کد و نقد تحویل', async () => {
  const o = await deliveryOrder(2, 'نزدیکِ مسجدِ جامع');
  placed.ok = o;
  assert.equal(Number(o.totalMinor), 2 * 60000 + 4000);
  await prepare(o.id);
  const asg = E.ok(await A.api('POST', `/shop/delivery/orders/${o.id}/assign`, { mode: 'shop_courier', courierId, fareMinor: 5000, requireCode: true }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201);
  placed.okAsg = asg.assignment.id;
  placed.code = asg.code;
  assert.match(asg.code, /^\d{6}$/);
  const ms = E.ok(await K.api('GET', '/courier/missions'), 'مأموریت‌های پیک');
  assert.equal(ms.items.length, 1);
  const card = ms.items[0];
  assert.equal(card.address.landmark, 'نزدیکِ مسجدِ جامع');
  assert.match(card.customer.phoneMasked, /•/);
  assert.equal(JSON.stringify(card).includes('791234567'), false, 'شمارهٔ کامل به پیک رسید');
  assert.equal(card.expectedCollectMinor, 124000);
  assert.equal(card.fareMinor, 5000);
  //  شاگردِ دیگر پیک نیست ⇒ درِ پیک بسته
  assert.equal((await clerk.api('GET', '/courier/missions')).status, 403);
  await courierOk([event(placed.okAsg, 'assigned'), event(placed.okAsg, 'picked_up')]);
  assert.equal((await order(o.id)).status, 'out_for_delivery');
  await courierOk([event(placed.okAsg, 'moving'), event(placed.okAsg, 'arrived')]);
  const wrong = await courierSend([event(placed.okAsg, 'delivered', { cashCollectedMinor: 124000, code: placed.code === '000000' ? '111111' : '000000' })]);
  assert.equal(wrong[0].code, 'bad_code');
  await courierOk([event(placed.okAsg, 'delivered', { cashCollectedMinor: 124000, code: placed.code })]);
  const row = await order(o.id);
  assert.deepEqual([row.status, row.cod_status], ['delivered', 'collected_by_courier'], 'تحویل ≠ تسویه');
});

await E.step('E2E-DLV-07.views', 'مشتری «تحویل شد» را می‌بیند ولی نه کد، نه پیک؛ صاحب خطِ زمانیِ کامل را دارد', async () => {
  const t = E.ok(await cust.track(placed.ok.token), 'پیگیری').order;
  assert.equal(t.status, 'delivered');
  const text = JSON.stringify(t);
  assert.equal(text.includes(placed.code), false, 'کدِ تحویل در صفحهٔ مشتری');
  assert.equal(/courier|پیکِ دکان|courierId/.test(text), false, `اطلاعاتِ پیک به مشتری رسید: ${text.slice(0, 300)}`);
  const det = E.ok(await A.api('GET', `/shop/delivery/orders/${placed.ok.id}`), 'جزئیاتِ تحویل');
  const steps = det.assignments[0].events.map((e) => e.toStatus).filter((s, i, a) => a.indexOf(s) === i);
  assert.deepEqual(steps, ['requested', 'assigned', 'picked_up', 'moving', 'arrived', 'delivered']);
  assert.equal(det.assignments[0].events.find((e) => e.toStatus === 'delivered').detail.codeVerified, true);
}, { needs: ['E2E-DLV-07'] });

await E.step('E2E-CASH-09', 'S40: تسویهٔ پیک — نقد و کرایه جدا؛ کسری با دلیل؛ تسویهٔ دوباره رد؛ دفترِ نقدِ پیک صفر', async () => {
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${courierId}/settlement-preview`), 'پیش‌نمایشِ تسویه');
  assert.equal(pv.totals.expectedCashMinor, 124000);
  const url = `/shop/delivery/couriers/${courierId}/settlements`;
  const body = { assignmentIds: [placed.okAsg], handedOverMinor: 123600, receivedMinor: 123600 };
  const noWhy = await A.api('POST', url, body, { headers: { 'Idempotency-Key': E.key('s') } });
  assert.equal(noWhy.status, 400, JSON.stringify(noWhy.body));
  assert.equal(noWhy.body.error.code, 'reason_required');
  const byClerk = await clerk.api('POST', url, { ...body, reason: 'x' }, { headers: { 'Idempotency-Key': E.key('s') } });
  assert.equal(byClerk.status, 403, 'شاگردِ بی‌اجازه تسویه زد');
  const key = E.key('s');
  const st = E.ok(await A.api('POST', url, { ...body, reason: 'شمارش در حضورِ پیک' }, { headers: { 'Idempotency-Key': key } }), 'تسویه', 201).settlement;
  assert.equal(st.shortageMinor, 400, JSON.stringify(st));
  assert.equal(st.fareTotalMinor, 5000, 'کرایه جدا از نقد');
  const again = await A.api('POST', url, { ...body, reason: 'شمارش در حضورِ پیک' }, { headers: { 'Idempotency-Key': key } });
  assert.equal(again.status, 200);
  assert.equal(again.body.settlement.id, st.id, 'تکرارِ همان کلید سندِ دوم ساخت');
  const twice = await A.api('POST', url, { ...body, reason: 'دوباره' }, { headers: { 'Idempotency-Key': E.key('s') } });
  assert.equal(twice.status, 409, JSON.stringify(twice.body));
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM courier_settlements WHERE shop_id=$1', [owner.shopId])).n, 1);
  assert.equal((await order(placed.ok.id)).cod_status, 'settled_to_shop');
  const cs = E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها').items.find((c) => c.id === courierId);
  assert.equal(cs.cashHeldMinor, 0);
  const rep = E.ok(await A.api('GET', '/shop/delivery/settlements'), 'گزارشِ تسویه');
  assert.equal(rep.totals.settlements, 1);
}, { needs: ['E2E-DLV-07'] });

await E.step('E2E-DLV-08', 'S39: ناموفق ⇐ تلاشِ دوباره ⇐ ناموفق ⇐ برگشت به دکان ⇐ قفسه؛ پولی گرفته نشده و رزرو آزاد', async () => {
  const before = E.ok(await A.api('GET', `/shop/catalog/products/${rice}`), 'کاتالوگ').product.stock;
  assert.equal(before.levels.shop_floor, 3000, `تحویلِ سفارشِ اول (۲ از ۵) از قفسه کم نشد: ${JSON.stringify(before)}`);
  const o = await deliveryOrder(1, 'پشتِ نانوایی');
  const held = E.ok(await A.api('GET', `/shop/catalog/products/${rice}`), 'کاتالوگ').product.stock;
  assert.ok(JSON.stringify(held) !== JSON.stringify(before), `سفارش رزرو نساخت: ${JSON.stringify(held)}`);
  await prepare(o.id);
  const asg = E.ok(await A.api('POST', `/shop/delivery/orders/${o.id}/assign`, { mode: 'shop_courier', courierId, fareMinor: 5000 }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201).assignment.id;
  await courierOk([event(asg, 'assigned'), event(asg, 'picked_up')]);
  const noReason = await courierSend([event(asg, 'failed')]);
  assert.equal(noReason[0].code, 'reason_required');
  await courierOk([event(asg, 'failed', { reason: 'no_answer' })]);
  assert.equal((await order(o.id)).status, 'delivery_failed');
  await courierOk([event(asg, 'moving')]);
  assert.equal((await order(o.id)).status, 'out_for_delivery');
  await courierOk([event(asg, 'failed', { reason: 'refused' })]);
  const self = await courierSend([event(asg, 'returned')]);
  assert.equal(self[0].code, 'staff_only', 'برگشت را دکان تأیید می‌کند، نه پیک');
  E.ok(await clerk.api('POST', `/shop/delivery/assignments/${asg}/status`, { to: 'returned', from: 'failed' }, { headers: { 'Idempotency-Key': E.key('st') } }), 'دریافتِ بسته در دکان');
  let row = await order(o.id);
  assert.deepEqual([row.status, row.cod_status], ['returned_to_store', 'to_collect'], 'پولی گرفته نشد');
  E.ok(await A.api('POST', `/shop/orders/${o.id}/transition`, { to: 'restocked', from: 'returned_to_store' }), 'بازگشت به قفسه');
  row = await order(o.id);
  assert.equal(row.status, 'restocked');
  const after = E.ok(await A.api('GET', `/shop/catalog/products/${rice}`), 'کاتالوگ').product.stock;
  assert.deepEqual(after, before, 'پس از برگشت به قفسه، موجودی و رزرو باید همان پیش از سفارش باشند');
  const cs = E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها').items.find((c) => c.id === courierId);
  assert.equal(cs.cashHeldMinor, 0);
  const t = E.ok(await cust.track(o.token), 'پیگیری').order;
  assert.match(t.status, /restocked|returned_to_store/);
  const rep = E.ok(await A.api('GET', `/shop/catalog/report?productId=${rice}`), 'تطبیقِ موجودی');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['E2E-DLV-07'] });

await E.finish();
