/**
 * سناریوی S31 (§۷.۴) — سفارشِ مهمان با پرداختِ نقد هنگامِ تحویل، از اول تا آخر
 * (CHK-01، CHK-08، ORD-01، ORD-02، ORD-10، PCK-02، DLV-08، DLV-11).
 *
 * بازیگرها:
 *   • مشتریِ مهمان — درِ همان API که صفحهٔ ویترین می‌زند (شمارهٔ تأییدشده، ۴ قلم، نام، شماره، پین،
 *     نشانهٔ «پشت مسجد جامع، دروازه سبز» و «پیش از رسیدن زنگ بزنید»)؛
 *   • A — گوشیِ صاحب (برنامهٔ دکان در سطحِ پروتکل): قبول، جمع‌آوری با اسکن، بسته‌بندی، سپردن به پیک،
 *     تسویهٔ پیک و ثبتِ فروشِ تحویل (`fulfillOrder` ِ همان `OrderFulfill` ِ کاتلین)؛
 *   • K — گوشیِ پیکِ دکان: گرفتن با اسکنِ برچسبِ کیسه‌ها، راه، رسیدن، کدِ تحویلِ مشتری و «نقد گرفته شد».
 *
 * هر سطرِ «نتیجه» ِ سند یک assert است؛ سطری که امروز ساخته نشده فقط در `PENDING` (پایین) نوشته شده،
 * نه assert ِ قرمز.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.orders.picking،
 * shop.delivery، shop.delivery_bag_scan، shop.customer_notify.
 * اجرا:  node test/e2e/run.mjs scenario-s31
 */
import { createRequire } from 'node:module';
import path from 'node:path';
import { boot, assert } from './lib/harness.mjs';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const E = await boot({ name: 'scenario-s31' });
//  ⚠️ پس از boot: کدِ سرور پیکربندی را همان لحظهٔ نخستین require می‌خواند
const require = createRequire(import.meta.url);
const N = require('../../src/lib/customer-notify');
const RC = require(path.join(import.meta.dirname, '..', '..', '..', 'license', 'report-core.js'));
const { db } = E;

/** سطرهای «نتیجه» که امروز ساخته نشده‌اند یا بیرون از این سرورند — شاهد نیستند، فقط سند */
export const PENDING = [
  'گام ۴ «قبول با F9 ⇒ چاپِ خودکارِ برگه» در برنامه است (OrderAutoPrint، OrderSlipTest)؛ این‌جا فقط گذارِ «قبول» ِ همان بدنه',
  'پیامِ مشتری به‌راستی نمی‌رسد: هیچ سرویس‌دهندهٔ پیامک/واتس‌اپ/تلگرام وصل نیست (D-09)؛ این‌جا یک پیام برای هر رویداد در صفِ پایدار با سرویس‌دهندهٔ ساختگی',
  'هشدارِ سفارش در پس‌زمینه (ORD-02) FCM می‌خواهد (BLOCKED-EXTERNAL)',
  '«گوشیِ ارزان با اینترنتِ ضعیف» سنجیده نشده',
];

const owner = await E.owner('owner-s31@e2e.local', 'صاحبِ S31', 'دکانِ S31');
const courierAcct = await E.staff(owner, 'courier-s31@e2e.local', 'پیکِ S31');
for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.picking', 'shop.delivery', 'shop.delivery_bag_scan', 'shop.customer_notify']) {
  await E.flag(owner.shopId, k, true);
}
const SLUG = 'e2e-s31-store';
const PHONE = '0791230031';
const LANDMARK = 'پشت مسجد جامع، دروازه سبز';
const PIN = { lat: 34.531, lng: 69.172 };
const ITEMS = [
  { name: 'برنج', price: 600, barcode: '6260000031011' },
  { name: 'روغن', price: 300, barcode: '6260000031028' },
  { name: 'چای', price: 150, barcode: '6260000031035' },
  { name: 'صابون', price: 40, barcode: '6260000031042' },
];
const FEE = 4000;

let A; let K; let courierId; let ticket = '';
const P = [];
const S = {};
const sent = [];
const fake = { send: async (msg) => { sent.push(msg); return { status: 'accepted', ref: `s31-${sent.length}` }; } };
N.setProviders({ push: fake, whatsapp: fake, telegram: fake, sms: fake });
/** کارگرِ اعلانِ مشتری یک دور — پس از هر رویداد، تا پیامِ پیشین با رویدادِ تازه «کهنه» نشود */
const deliver = async () => { await N.tick(Date.now() + 1000); };
const orderRow = () => db.one('SELECT * FROM online_orders WHERE id=$1', [S.id]);
let ev = 0;
const cev = (type, extra = {}) => ({ eventId: `s31-ev-${Date.now()}-${++ev}`, assignmentId: S.asg, type, deviceTime: Date.now(), deviceSeq: ev, ...extra });
async function courierOk(events) {
  const r = E.ok(await K.api('POST', '/courier/events', { deviceId: K.deviceId, events }), 'رویدادِ پیک');
  for (const x of r.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

await E.step('S31.0', 'دکان: چهار کالا با بارکد از برنامه، ویترین روشن، پیکِ دکان تعریف', async () => {
  A = await E.device('A-owner-phone', owner.email);
  K = await E.device('K-courier-phone', courierAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  //  برنامهٔ «فروشِ تحویل» دکان را می‌بیند ⇒ کفِ تحویلِ همین دکان (پیش از هر سفارش)
  assert.ok(E.ok(await A.ordersConfig(), 'config ِ سفارش‌ها').handoverSince > 0);
  for (const it of ITEMS) P.push(A.stockIn({ name: it.name, price: it.price, cost: Math.round(it.price * 0.8), units: 10, category: 'خوراکی', barcode: it.barcode }));
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ S31', contactPhone: '0700000031', deliveryFeeMinor: FEE, minOrderMinor: 0 }), 'ویترین');
  for (const id of P) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), 'انتشار');
  courierId = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: courierAcct.user.id, cashCapMinor: 5_000_000 }), 'پیک', 201).courier.id;
});

await E.step('S31.1', 'مهمان: ۴ قلم، نام، شماره، پین، نشانه و «پیش از رسیدن زنگ بزنید» ⇒ ثبت با پرداختِ نقد هنگامِ تحویل', async () => {
  const st = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { email: mailOf(PHONE) }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { email: mailOf(PHONE), code: st.devCode }), 'تأییدِ شماره').ticket;
  const lines = P.map((productId) => ({ productId, qtyMilli: 1000 }));
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مهمانِ S31', phone: PHONE }, lines,
    address: { ...PIN, landmark: LANDMARK }, delivery: { callBeforeArrival: true },
  }, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket }), 'ثبتِ سفارش', 201);
  S.token = r.order.token;
  S.no = r.order.orderNo;
  S.id = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, S.no])).id;
  S.cart = ITEMS.reduce((n, it) => n + it.price * 100, 0);
  assert.equal(Number(r.order.totalMinor), S.cart + FEE, 'مبلغِ سبد + کرایه');
  const o = await orderRow();
  assert.equal(o.status, 'new');
  const addr = typeof o.address === 'string' ? JSON.parse(o.address) : o.address;
  assert.equal(addr.landmark, LANDMARK);
  await deliver();
}, { needs: ['S31.0'] });

await E.step('S31.2', 'دکان: هشدار در صفِ سفارش‌ها ⇒ قبول ⇒ جمع‌آوری با اسکن ⇒ بسته‌بندی (دو کیسه)', async () => {
  const list = E.ok(await A.api('GET', '/shop/orders?status=new'), 'صفِ تازه');
  assert.ok((list.items || list.orders || []).some((x) => x.id === S.id), 'سفارش در صفِ «تازه» ِ برنامه');
  E.ok(await A.api('POST', `/shop/orders/${S.id}/transition`, { to: 'accepted', from: 'new', readyInMinutes: 20 }), 'قبول');
  await deliver();
  const sheet = E.ok(await A.api('POST', `/shop/orders/${S.id}/pick/start`, {}), 'شروعِ جمع‌آوری').pick;
  //  اسکنِ کالای اشتباه رد می‌شود (PCK-02)
  const wrong = await A.api('POST', `/shop/orders/${S.id}/pick/events`, { events: [{ eventId: E.key('ev'), lineNo: sheet.lines[0].lineNo, type: 'scan', barcode: ITEMS[1].barcode }] });
  assert.notEqual(wrong.body?.results?.[0]?.status, 'accepted', 'بارکدِ کالای دیگر پذیرفته شد');
  const evs = sheet.lines.map((l) => ({ eventId: E.key('ev'), lineNo: l.lineNo, type: 'scan', barcode: ITEMS[P.indexOf(l.productId)].barcode }));
  const r = E.ok(await A.api('POST', `/shop/orders/${S.id}/pick/events`, { events: evs }), 'اسکنِ قلم‌ها');
  for (const x of r.results) assert.equal(x.status, 'accepted', JSON.stringify(x));
  const pk = E.ok(await A.api('POST', `/shop/orders/${S.id}/pick/pack`, { bags: 2 }, { headers: { 'Idempotency-Key': E.key('pack') } }), 'بسته‌بندی');
  assert.equal(pk.pick.totals.deductedMinor, 0, 'هیچ قلمی کم نشد');
  S.labels = pk.pick.labels.map((l) => l.qr);   //  همان QR ِ چاپ‌شده روی هر کیسه
  assert.equal(S.labels.length, 2);
  assert.equal((await orderRow()).status, 'packed');
}, { needs: ['S31.1'] });

await E.step('S31.3', 'سپردن به پیک ⇒ پیک با اسکنِ هر دو کیسه می‌گیرد ⇒ می‌رسد ⇒ کدِ مشتری ⇒ «نقد گرفته شد»', async () => {
  const asg = E.ok(await A.api('POST', `/shop/delivery/orders/${S.id}/assign`, { mode: 'shop_courier', courierId, fareMinor: 5000, requireCode: true }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201);
  S.asg = asg.assignment.id;
  const card = E.ok(await K.api('GET', '/courier/missions'), 'مأموریت').items.find((x) => x.assignmentId === S.asg || x.id === S.asg) || E.ok(await K.api('GET', '/courier/missions'), 'مأموریت').items[0];
  assert.equal(card.address.landmark, LANDMARK, 'پیک نشانه را می‌بیند');
  assert.equal(card.expectedCollectMinor, S.cart + FEE);
  await courierOk([cev('assigned')]);
  //  یک کیسه کم ⇒ گرفتن رد
  const half = E.ok(await K.api('POST', '/courier/events', { deviceId: K.deviceId, events: [cev('picked_up', { bags: [S.labels[0]] })] }), 'نیمه');
  assert.equal(half.results[0].code, 'bags_incomplete', JSON.stringify(half.results[0]));
  await courierOk([cev('picked_up', { bags: S.labels })]);
  await deliver();
  await courierOk([cev('moving'), cev('arrived')]);
  //  DLV-17 (`shop.delivery_near_pin`، مثلاً `E2E_FLAGS_ON`): کارتِ پین‌دار `nearPin` دارد ⇒ برنامهٔ پیک موقعیتِ همان لحظه را هم می‌فرستد
  await courierOk([cev('delivered', { cashCollectedMinor: S.cart + FEE, code: asg.code, ...(card.nearPin ? { position: PIN } : {}) })]);
  await deliver();
  const o = await orderRow();
  assert.deepEqual([o.status, o.cod_status], ['delivered', 'collected_by_courier']);
}, { needs: ['S31.2'] });

await E.step('S31.timeline', 'هر مرحله در خطِ زمانی ثبت شده است', async () => {
  const st = (await db.many("SELECT kind, to_status FROM online_order_events WHERE order_id=$1 ORDER BY seq", [S.id]));
  assert.equal(st[0].kind, 'created');
  assert.deepEqual(st.filter((e) => e.kind === 'status').map((e) => e.to_status).filter((s, i, a) => a.indexOf(s) === i),
    ['accepted', 'picking', 'packed', 'out_for_delivery', 'delivered']);
  const picks = await db.many('SELECT kind, outcome FROM order_pick_events WHERE order_id=$1', [S.id]);
  assert.ok(picks.filter((p) => p.kind === 'scan').length >= ITEMS.length, JSON.stringify(picks));
  const dl = E.ok(await A.api('GET', `/shop/delivery/orders/${S.id}`), 'خطِ زمانیِ تحویل').assignments[0].events.map((e) => e.toStatus);
  for (const s of ['requested', 'assigned', 'picked_up', 'moving', 'arrived', 'delivered']) assert.ok(dl.includes(s), `${s} در خطِ زمانیِ تحویل نیست: ${dl}`);
}, { needs: ['S31.3'] });

await E.step('S31.notify', 'مشتری برای هر رویداد یک پیام گرفته است — نه کمتر، نه دوتا', async () => {
  await deliver();
  const rows = await db.many('SELECT event, status FROM customer_notify_outbox WHERE order_id=$1 ORDER BY created_at, id', [S.id]);
  assert.deepEqual(rows.map((r) => r.event), ['order_received', 'order_accepted', 'order_on_the_way', 'order_completed']);
  assert.deepEqual(rows.map((r) => r.status), ['sent', 'sent', 'sent', 'sent'], JSON.stringify(rows));
  assert.equal(sent.length, 4, 'هر رویداد دقیقاً یک پیام');
  //  کارگر دوباره ⇒ هیچ پیامِ تازه
  await deliver();
  assert.equal(sent.length, 4);
}, { needs: ['S31.3'] });

await E.step('S31.sale', 'برنامهٔ دکان: فاکتورِ فروش با همان قیمتِ سبد، موجودی یک بار کم، کانالِ «اینترنتی» در گزارش', async () => {
  const before = P.map((id) => A.stock(id));
  const h = E.ok(await A.api('GET', `/shop/orders/handover?since=0&orderId=${S.id}`), 'تحویل‌های بی‌فروش').items.find((x) => x.id === S.id);
  assert.ok(h, 'تحویلِ پیک به برنامهٔ دکان نرسید');
  const f = A.fulfillOrder(h);
  assert.equal(f.ok, true, JSON.stringify(f));
  assert.equal(A.fulfillOrder(h).already, true, 'دوبار ⇒ همان فروش');
  await A.drain();
  const sales = A.ledger.sales.filter((s) => s.orderId === S.id);
  assert.equal(sales.length, 1);
  const sale = sales[0];
  assert.equal(Math.round(sale.finalTotal * 100), S.cart + FEE);
  assert.equal(sale.channel, 'online');
  const items = A.ledger.saleItems.filter((i) => i.saleId === sale.id && i.productId);
  for (const it of ITEMS) {
    const line = items.find((i) => i.productId === P[ITEMS.indexOf(it)]);
    assert.equal(line.unitPrice, it.price, `${it.name}: قیمتِ فاکتور = قیمتِ سبد`);
  }
  assert.deepEqual(P.map((id) => A.stock(id)), before.map((n) => n - 1), 'موجودی یک بار کم شد');
  //  دستگاهِ دیگرِ همان دکان همان یک کسر را می‌بیند
  const B = await E.device('B-owner-desk', owner.email);
  assert.equal((await B.firstSync()).ok, true);
  assert.deepEqual(P.map((id) => B.stock(id)), P.map((id) => A.stock(id)));
  //  گزارشِ «فروش به تفکیکِ کانال» (همان report-core ِ برنامه)
  const rep = RC.sales(A.ledger, {});
  const online = rep.channel.find((r) => r.key === 'online');
  assert.ok(online, JSON.stringify(rep.channel));
  assert.deepEqual([online.invoices, online.sales], [1, S.cart + FEE], 'سفارش در «فروش به تفکیکِ کانال» زیرِ «اینترنتی»');
}, { needs: ['S31.3'] });

await E.step('S31.cash', 'پایانِ روز: نقدِ پیک تا تسویه «در دستِ پیک» است؛ پس از تسویه صفر و «تحویلِ دکان»', async () => {
  const held = async () => E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها');
  const before = (await held()).items.find((c) => c.id === courierId);
  assert.equal(before.cashHeldMinor, S.cart + FEE, 'نقد در دستِ پیک');
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${courierId}/settlement-preview`), 'پیش‌نمایشِ تسویه');
  assert.equal(pv.totals.expectedCashMinor, S.cart + FEE);
  const st = E.ok(await A.api('POST', `/shop/delivery/couriers/${courierId}/settlements`,
    { assignmentIds: [S.asg], handedOverMinor: S.cart + FEE, receivedMinor: S.cart + FEE },
    { headers: { 'Idempotency-Key': E.key('settle') } }), 'تسویه', 201).settlement;
  assert.equal(st.shortageMinor, 0);
  assert.equal((await held()).items.find((c) => c.id === courierId).cashHeldMinor, 0);
  assert.equal((await orderRow()).cod_status, 'settled_to_shop');
}, { needs: ['S31.3'] });

await E.finish();
