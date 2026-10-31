/**
 * سناریوی سرتاسریِ برنامهٔ پیک — از صفِ گوشیِ پیک (`lib/courier.mjs`، سطر به سطرِ CourierQueue.kt و
 * CourierSync.flush)، نه از درِ خامِ API:
 *
 *   DEL-01        پیک مأموریت را با دلیل رد می‌کند ⇐ سفارش در وضعیتِ معتبر («بسته‌بندی‌شده») می‌ماند،
 *                 کارت از گوشیِ پیکِ اول می‌رود و دکان به پیکِ دوم می‌سپارد.
 *   E2E-SYNC-12   (پیک) پیک بی اینترنت می‌گیرد، می‌رود، می‌رسد و با کد و نقد تحویل می‌دهد ⇐ همه در صفِ
 *                 گوشی، کارت جلو می‌رود؛ وصل ⇐ جوابِ نخستین فرستادن گم می‌شود ⇐ دوباره ⇐ «تکراری»؛
 *                 یک تحویل، یک نقد در دستِ پیک، یک فروش در برنامهٔ دکان.
 *   E2E-DLV-07    همان مسیر از برنامهٔ پیک؛ پیک شمارهٔ کامل و پس از پایان نشانی را ندارد؛ مشتری نه کد، نه پیک.
 *   DEL-02        مشتری کمتر از مبلغ می‌دهد ⇐ بی دلیل سرور رد می‌کند و ردشده در گوشی پیداست (پنهان نه)؛
 *                 با دلیل ثبت ⇐ پیش‌نمایشِ تسویه «اختلافِ مشتری» را جدا از کسریِ پیک نشان می‌دهد؛ تسویه
 *                 بی دلیل رد، با دلیل ثبت و اختلاف در سندِ تسویه و گزارش می‌ماند.
 *   E2E-DLV-08    ناموفق از گوشی ⇐ دوباره ⇐ ناموفق ⇐ دکان بسته را پس می‌گیرد ⇐ قفسه؛ فروشِ تحویلِ برنامه
 *                 یک بار لغو، پولی گرفته نشده، نقدِ پیک و تسویه دست‌نخورده.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.delivery.
 * اجرا:  node test/e2e/courier.e2e.mjs
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';
import { CourierApp, KT, canSend, enqueue } from './lib/courier.mjs';

const require = createRequire(import.meta.url);
const D = require('../../src/lib/delivery-states');
const E = await boot({ name: 'courier' });
const { db } = E;
const owner = await E.owner('owner-cur@e2e.local', 'صاحبِ پیک', 'دکانِ پیک');
const k1Acct = await E.staff(owner, 'courier1-cur@e2e.local', 'پیکِ اول');
const k2Acct = await E.staff(owner, 'courier2-cur@e2e.local', 'پیکِ دوم');
for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-courier-store';
const PHONE = '0791234568';
const order = (id) => db.one('SELECT status, cod_status, total_minor FROM online_orders WHERE id=$1', [id]);

let A; let K1; let K2; let rice; let c1; let c2; let ticket;
const placed = {};

async function deliveryOrder(qty, landmark) {
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ پیک', phone: PHONE },
    lines: [{ productId: rice, qtyMilli: qty * 1000 }],
    address: { text: 'خیرخانه، سرکِ اول', landmark }, delivery: { callBeforeArrival: true },
  }, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': E.key('web') }), 'ثبتِ سفارشِ ارسال', 201);
  const row = await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, r.order.orderNo]);
  return { ...r.order, id: row.id };
}
async function packed(id) {
  for (const [to, from, x] of [['accepted', 'new', { readyInMinutes: 15 }], ['picking', 'accepted'], ['packed', 'picking']]) {
    E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to, from, ...(x || {}) }), `${from} ⇐ ${to}`);
  }
}
async function assign(orderId, courierId, extra = {}) {
  return E.ok(await A.api('POST', `/shop/delivery/orders/${orderId}/assign`, { mode: 'shop_courier', courierId, fareMinor: 5000, ...extra },
    { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201);
}
const track = async (token) => E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(token)}`, null, { 'X-Store-Ticket': ticket }), 'پیگیری').order;
/** «ارسالِ» برنامهٔ پیک باید موفق باشد و صف خالی */
async function flushed(app) {
  const r = await app.flush();
  assert.equal(r.ok, true, `${app.dev.label}: ${app.lastFailure}`);
  assert.equal(app.queue.pending.length, 0, JSON.stringify(app.queue.pending));
  return r;
}

await E.step('E2E-DLV.app.0', 'صفِ برنامهٔ پیک همان دلیل‌ها و گذارهای سرور را دارد؛ دکان، ویترین و دو پیک آماده', async () => {
  assert.deepEqual(KT.REJECT_REASONS, D.REASONS.rejected);
  assert.deepEqual(KT.FAIL_REASONS, D.REASONS.failed);
  assert.deepEqual(KT.CASH_REASONS, D.REASONS.cash);
  assert.deepEqual([...KT.COURIER_TARGETS].sort(), [...D.COURIER_TARGETS].sort());
  assert.deepEqual([...KT.STATUSES].sort(), [...D.STATUSES].sort());
  //  هر گذاری که گوشی می‌فرستد سرور برای پیک می‌پذیرد و برعکس (بی «تحویل» بی گرفتنِ بسته)
  for (const from of D.STATUSES) {
    for (const to of D.COURIER_TARGETS) {
      assert.equal(canSend(from, to), D.canTransition(from, to) && !(from === 'assigned' && to === 'delivered'), `${from} ⇐ ${to}`);
    }
  }
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  assert.ok(E.ok(await A.ordersConfig(), 'config ِ سفارش‌ها').handoverSince > 0);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 8 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ پیک', contactPhone: '0700000005', deliveryFeeMinor: 4000, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  c1 = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: k1Acct.user.id, cashCapMinor: 5_000_000 }), 'پیکِ اول', 201).courier.id;
  c2 = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: k2Acct.user.id, cashCapMinor: 5_000_000 }), 'پیکِ دوم', 201).courier.id;
  K1 = new CourierApp(await E.device('K1-courier-phone', k1Acct.email));
  K2 = new CourierApp(await E.device('K2-courier-phone', k2Acct.email));
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone: PHONE }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone: PHONE, code: s.devCode }), 'تأییدِ شماره').ticket;
});

await E.step('DEL-01', 'پیکِ اول از برنامه با دلیل رد می‌کند ⇐ سفارش «بسته‌بندی‌شده» و معتبر؛ کارت از گوشیِ او می‌رود؛ دکان به پیکِ دوم می‌سپارد', async () => {
  const o = await deliveryOrder(2, 'نزدیکِ مکتب');
  placed.one = o;
  await packed(o.id);
  const a1 = (await assign(o.id, c1)).assignment.id;
  await flushed(K1);
  assert.equal(K1.view(a1)?.status, 'requested', 'مأموریت در گوشیِ پیکِ اول نیست');
  //  رد بی دلیل در خودِ گوشی بسته است (پیش از رفتن به صف)
  assert.throws(() => K1.record(a1, 'rejected'), (e) => e.code === 'reason_required');
  K1.record(a1, 'rejected', { reason: 'busy' });
  await flushed(K1);
  assert.equal(K1.queue.rejected.length, 0, JSON.stringify(K1.queue.rejected));
  assert.equal(K1.cards.active.some((c) => c.id === a1), false, 'کارتِ ردشده در «فعال» ماند');
  const row = await order(o.id);
  assert.equal(row.status, 'packed', 'سفارش پس از ردِ پیک در وضعیتِ معتبر نماند');
  const a = await db.one('SELECT status, status_reason FROM delivery_assignments WHERE id=$1', [a1]);
  assert.deepEqual([a.status, a.status_reason], ['rejected', 'busy']);
  assert.equal((await track(o.token)).status, 'packed', 'مشتری وضعیتِ دیگری دید');
  //  پیکِ اول دیگر روی همان مأموریت کاری نمی‌تواند (حتی با کارتِ کهنه در صف)
  [K1.queue] = enqueue(K1.queue, `cev-stale-${Date.now()}`, a1, 'assigned', Date.now(), { from: 'requested' });
  await K1.flush();
  assert.equal(K1.queue.rejected.length, 1, 'رویدادِ مأموریتِ ردشده بی‌صدا پذیرفته یا گم شد');
  assert.equal((await order(o.id)).status, 'packed');
  //  سپردنِ دوباره
  const res = await assign(o.id, c2, { requireCode: true });
  const a2 = res.assignment.id;
  placed.oneAsg = a2;
  placed.code = res.code;   //  کدِ تحویل فقط پیشِ مشتری است (پیامِ دکان)
  await flushed(K2);
  assert.equal(K2.view(a2)?.status, 'requested');
  K2.record(a2, 'assigned');
  await flushed(K2);
  assert.equal((await db.one('SELECT status FROM delivery_assignments WHERE id=$1', [a2])).status, 'assigned');
});

await E.step('E2E-SYNC-12.courier', 'پیک بی اینترنت می‌گیرد، می‌رود، می‌رسد و با کد و نقد تحویل می‌دهد ⇐ صف؛ وصل ⇐ جوابِ گم‌شده ⇐ دوباره «تکراری»؛ یک اثرِ مالی', async () => {
  const a2 = placed.oneAsg;
  assert.match(placed.code, /^\d{6}$/);
  //  مشتری کد را با رقمِ فارسی می‌گوید؛ گوشی لاتین می‌کند (`latinDigits`)
  const spoken = placed.code.replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
  const expected = Number((await order(placed.one.id)).total_minor);
  K2.dev.offline = true;
  K2.record(a2, 'picked_up').record(a2, 'moving').record(a2, 'arrived');
  K2.record(a2, 'delivered', { cash: expected, code: spoken });
  assert.equal(K2.view(a2).status, 'delivered', 'کارت بی اینترنت جلو نرفت');
  assert.equal(K2.view(a2).address, null, 'پس از پایان نشانی روی گوشی ماند');
  assert.equal((await K2.flush()).ok, false);
  assert.equal(K2.queue.pending.length, 4, 'بی اینترنت چیزی از صف رفت');
  assert.equal((await db.one('SELECT status FROM delivery_assignments WHERE id=$1', [a2])).status, 'assigned', 'سرورِ بی‌خبر جلو رفت');
  K2.dev.offline = false;
  //  سرور می‌گیرد و اعمال می‌کند، ولی جواب به گوشی نمی‌رسد
  assert.equal((await K2.flush({ loseResponse: true })).ok, false);
  assert.equal(K2.queue.pending.length, 4, 'بی جواب صف خالی شد');
  assert.equal((await db.one('SELECT status FROM delivery_assignments WHERE id=$1', [a2])).status, 'delivered');
  const ids = K2.queue.pending.map((e) => e.eventId);
  await flushed(K2);
  assert.deepEqual(K2.lastResults.map((x) => x.status), ['duplicate', 'duplicate', 'duplicate', 'duplicate'], JSON.stringify(K2.lastResults));
  assert.equal(K2.queue.rejected.length, 0, JSON.stringify(K2.queue.rejected));
  const evs = await db.many("SELECT to_status FROM delivery_events WHERE assignment_id=$1 AND kind='status' AND event_key = ANY($2::text[])", [a2, ids]);
  assert.deepEqual(evs.map((e) => e.to_status).sort(), ['arrived', 'delivered', 'moving', 'picked_up'], 'هر رویداد یک بار');
  const row = await order(placed.one.id);
  assert.deepEqual([row.status, row.cod_status], ['delivered', 'collected_by_courier']);
  const cs = E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها').items.find((c) => c.id === c2);
  assert.equal(cs.cashHeldMinor, expected, 'نقدِ در دستِ پیک دو بار شمرده شد');
  //  برنامهٔ دکان: تحویلِ پیک ⇐ یک فروش؛ دوباره ⇐ همان
  const pend = E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌های بی‌فروش').items;
  assert.deepEqual(pend.map((x) => x.id), [placed.one.id]);
  assert.equal(A.fulfillOrder(pend[0]).ok, true);
  assert.equal(A.fulfillOrder(pend[0]).already, true);
  await A.drain();
  assert.equal(A.ledger.sales.filter((s) => s.orderId === placed.one.id).length, 1);
  assert.equal(A.stock(rice), 6);
  assert.deepEqual(E.ok(await A.api('GET', '/shop/orders/handover?since=0'), 'تحویل‌ها').items, []);
}, { needs: ['DEL-01'] });

await E.step('E2E-DLV-07.app', 'پیک شمارهٔ کامل را هرگز و نشانی را پس از پایان ندارد؛ مشتری «تحویل شد» را بی کد و بی پیک می‌بیند', async () => {
  const all = JSON.stringify([...K1.cards.active, ...K1.cards.done, ...K2.cards.active, ...K2.cards.done]);
  assert.equal(all.includes(PHONE.slice(1)), false, 'شمارهٔ کامل به گوشیِ پیک رسید');
  const done = K2.cards.done.find((c) => c.id === placed.oneAsg);
  assert.ok(done, 'مأموریتِ پایان‌یافته در «انجام‌شده» نیست');
  assert.equal(JSON.stringify(done).includes('خیرخانه'), false, 'نشانی پس از پایان در کارتِ پیک ماند');
  const t = await track(placed.one.token);
  assert.equal(t.status, 'delivered');
  assert.equal(/courier|پیکِ دوم|courierId/.test(JSON.stringify(t)), false, 'اطلاعاتِ پیک به مشتری رسید');
}, { needs: ['E2E-SYNC-12.courier'] });

await E.step('DEL-02', 'مشتری کمتر می‌دهد ⇐ بی دلیل رد و پیدا در گوشی؛ با دلیل ثبت ⇐ اختلافِ مشتری جدا در پیش‌نمایش، سندِ تسویه و گزارش', async () => {
  const o = await deliveryOrder(1, 'پشتِ نانوایی');
  await packed(o.id);
  const asg = await assign(o.id, c2, { requireCode: false });
  const a = asg.assignment.id;
  await flushed(K2);
  K2.record(a, 'assigned').record(a, 'picked_up').record(a, 'arrived');
  await flushed(K2);
  const expected = Number((await order(o.id)).total_minor);
  const paid = expected - 500;
  //  برنامه‌ای که دلیل را نفرستد: سرور رد می‌کند و ردشده در گوشی می‌ماند تا پیک ببیند
  K2.record(a, 'delivered', { cash: paid });
  await K2.flush();
  assert.equal(K2.queue.pending.length, 0);
  const rej = K2.queue.rejected.find((x) => x.event.assignmentId === a);
  assert.equal(rej?.code, 'cash_reason_required', JSON.stringify(K2.queue.rejected));
  assert.equal((await db.one('SELECT status FROM delivery_assignments WHERE id=$1', [a])).status, 'arrived', 'بی دلیل تحویل ثبت شد');
  K2.record(a, 'delivered', { cash: paid, cashReason: 'partial_payment', note: 'مشتری ۵ افغانی کم داشت' });
  await flushed(K2);
  assert.equal((await db.one('SELECT status, cash_collected_minor FROM delivery_assignments WHERE id=$1', [a])).cash_collected_minor, String(paid));
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${c2}/settlement-preview`), 'پیش‌نمایشِ تسویه');
  const r = pv.rows.find((x) => x.assignmentId === a);
  assert.deepEqual([r.expectedCollectMinor, r.cashCollectedMinor, r.collectionDiffMinor], [expected, paid, -500], JSON.stringify(r));
  const first = pv.rows.find((x) => x.assignmentId === placed.oneAsg);
  assert.equal(first.collectionDiffMinor, 0);
  assert.equal(pv.totals.collectionDiffMinor, -500, 'اختلاف در جمعِ پیش‌نمایش پنهان شد');
  const held = pv.totals.collectedCashMinor;
  const url = `/shop/delivery/couriers/${c2}/settlements`;
  const body = { assignmentIds: pv.rows.map((x) => x.assignmentId), handedOverMinor: held, receivedMinor: held };
  const noWhy = await A.api('POST', url, body, { headers: { 'Idempotency-Key': E.key('s') } });
  assert.equal(noWhy.status, 400, JSON.stringify(noWhy.body));
  assert.equal(noWhy.body.error.code, 'reason_required', 'اختلافِ مشتری بی دلیل تسویه شد');
  const st = E.ok(await A.api('POST', url, { ...body, reason: 'کم‌پرداختِ مشتری، پیگیری می‌شود' }, { headers: { 'Idempotency-Key': E.key('s') } }), 'تسویه', 201).settlement;
  assert.deepEqual([st.collectionDiffMinor, st.shortageMinor, st.collectedCashMinor], [-500, 0, held], JSON.stringify(st));
  const rep = E.ok(await A.api('GET', '/shop/delivery/settlements'), 'گزارشِ تسویه');
  assert.ok(JSON.stringify(rep).includes('-500'), `اختلاف در گزارشِ تسویه نیست: ${JSON.stringify(rep).slice(0, 400)}`);
  const cs = E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها').items.find((c) => c.id === c2);
  assert.equal(cs.cashHeldMinor, 0);
}, { needs: ['E2E-SYNC-12.courier'] });

await E.step('E2E-DLV-08.app', 'ناموفق از برنامهٔ پیک (بی دلیل نه) ⇐ دوباره ⇐ ناموفق ⇐ دکان بسته را پس می‌گیرد ⇐ قفسه؛ فروشِ تحویل یک بار لغو، پولی گرفته نشده، نقدِ پیک دست‌نخورده', async () => {
  const o = await deliveryOrder(1, 'کنارِ داروخانه');
  await packed(o.id);
  const a = (await assign(o.id, c2, { requireCode: false })).assignment.id;
  await flushed(K2);
  K2.record(a, 'assigned').record(a, 'picked_up');
  await flushed(K2);
  //  کالا با پیک رفت ⇐ برنامهٔ دکان فروشِ تحویل (نقد در دستِ پیک) را ثبت می‌کند
  const ask = async () => E.ok(await A.api('GET', `/shop/orders/handover?since=0&orderId=${o.id}`), 'تحویل؟');
  const sent = (await ask()).items.find((x) => x.id === o.id);
  assert.equal(sent?.action, 'sale', JSON.stringify(sent));
  const stock0 = A.stock(rice);
  assert.equal(A.fulfillOrder(sent).ok, true);
  await A.drain();
  assert.equal(A.stock(rice), stock0 - 1);
  assert.throws(() => K2.record(a, 'failed'), (e) => e.code === 'reason_required');
  K2.record(a, 'failed', { reason: 'no_answer' });
  await flushed(K2);
  assert.equal((await order(o.id)).status, 'delivery_failed');
  K2.record(a, 'moving');
  await flushed(K2);
  assert.equal((await order(o.id)).status, 'out_for_delivery');
  K2.record(a, 'failed', { reason: 'refused' });
  //  «برگشت» کارِ دکان است، نه پیک: گوشی آن را اصلاً نمی‌سازد
  assert.throws(() => K2.record(a, 'returned'), (e) => e.code === 'bad_event_type');
  await flushed(K2);
  E.ok(await A.api('POST', `/shop/delivery/assignments/${a}/status`, { to: 'returned', from: 'failed' }, { headers: { 'Idempotency-Key': E.key('st') } }), 'دریافتِ بسته در دکان');
  let row = await order(o.id);
  assert.deepEqual([row.status, row.cod_status], ['returned_to_store', 'to_collect'], 'پولی گرفته نشد');
  E.ok(await A.api('POST', `/shop/orders/${o.id}/transition`, { to: 'restocked', from: 'returned_to_store' }), 'بازگشت به قفسه');
  row = await order(o.id);
  assert.equal(row.status, 'restocked');
  //  برنامه همان فروش را لغو می‌کند؛ دستگاهِ دوم و تلاشِ دوباره اثرِ دوم ندارند
  const back = (await ask()).reversals.find((x) => x.id === o.id);
  assert.equal(back?.action, 'reverse', JSON.stringify(back));
  assert.equal(A.reverseOrder(back).ok, true);
  assert.equal(A.reverseOrder(back).already, true);
  await A.drain();
  const B = await E.device('B-owner-desktop', owner.email);
  assert.equal((await B.firstSync()).ok, true);
  assert.equal(B.reverseOrder(back).already, true);
  assert.deepEqual([A.stock(rice), B.stock(rice)], [stock0, stock0], 'موجودی دقیقاً برگشت');
  assert.equal(B.ledger.sales.find((s) => s.orderId === o.id)?.status, 'cancelled');
  assert.deepEqual((await ask()).reversals.filter((x) => x.id === o.id), [], 'پس از لغو هنوز «برگشتِ بی‌لغو» است');
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${c2}/settlement-preview`), 'پیش‌نمایشِ تسویه');
  assert.equal(pv.rows.some((x) => x.assignmentId === a), false, 'مأموریتِ برگشتی در تسویهٔ پیک آمد');
  const cs = E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها').items.find((c) => c.id === c2);
  assert.equal(cs.cashHeldMinor, 0, 'نقدِ پیک برای سفارشِ برگشتی');
  assert.match((await track(o.token)).status, /restocked|returned_to_store/);
}, { needs: ['DEL-01'] });

await E.finish();
