/**
 * سناریوهای سرتاسریِ پیک و تسویه — S57، S80، S84، S98، S99، S100، S101، S120 (§۱۶، §۱۹.۱۲، §۲۰.۱۲، §۲۱.۱۹).
 *
 * بازیگرها:
 *   • A — گوشیِ صاحب (برنامهٔ دکان در سطحِ پروتکل): کالا و قیمت از همگام‌سازی، سفارش و سپردن با همان بدنه‌ها؛
 *   • K1 و K2 — گوشیِ دو پیکِ دکان: صفِ رویدادها با همان بدنهٔ `POST /api/courier/events`
 *     (`eventId`، `deviceTime`، `deviceSeq`) و «حالتِ هواپیما» ِ `NativeDevice` (هیچ درخواستی بیرون نمی‌رود)؛
 *   • W — گوشیِ شاگردِ انبار (پیک نیست؛ دریافتِ بستهٔ برگشتی در دکان)؛
 *   • مشتری — API ِ همان صفحهٔ ویترین برای ثبت، و صفحهٔ پیگیری در کرومیوم (عرضِ گوشی).
 *
 * ⛔ زمان: هیچ خوابی برای هماهنگی نیست. زمانِ دستگاهِ پیک (`deviceTime`) از یک مبدأِ ثابت و ترتیبِ
 * رویداد (`deviceSeq`) ساخته می‌شود؛ صفحهٔ مشتری با بارگذاریِ دوباره و `waitForFunction` خوانده می‌شود.
 * ⛔ موجودی (تصمیمِ صاحبِ مخزن، همان `stock.e2e.mjs`): سرور برای سفارش رزرو و کسری ندارد.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.delivery، shop.catalog.
 * اجرا:  node test/e2e/run.mjs courier-scenarios
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'courier-scenarios' });
const { db } = E;
const owner = await E.owner('owner-cour@e2e.local', 'صاحبِ پیک‌ها', 'دکانِ پیک‌ها');
const k1Acct = await E.staff(owner, 'k1-cour@e2e.local', 'پیکِ یک');
const k2Acct = await E.staff(owner, 'k2-cour@e2e.local', 'پیکِ دو');
const clerkAcct = await E.staff(owner, 'clerk-cour@e2e.local', 'شاگردِ انبار');
for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.catalog']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-courier-store';
const PHONE = '0791230001';
const RICE_MINOR = 60000;
const FEE_MINOR = 4000;
const FARE_MINOR = 5000;

let A; let K1; let K2; let W; let rice; let c1; let c2; let ticket; let C;
const o = {};        //  سفارش‌ها به نامِ سناریو
const T0 = Date.UTC(2026, 9, 30, 6, 0, 0);   //  مبدأِ ثابتِ ساعتِ گوشیِ پیک (۸ عقرب ۱۴۰۵، ۱۰:۳۰ کابل)
const seq = new Map();

const order = (id) => db.one('SELECT status, cod_status, total_minor, delivery_fee_minor FROM online_orders WHERE id=$1', [id]);
const asg = (id) => db.one('SELECT status, cash_review, code_used_at, code_attempts, settlement_id, recall_outcome, cash_collected_minor FROM delivery_assignments WHERE id=$1', [id]);
const eventsOf = (id) => db.many('SELECT event_key, to_status, device_time, device_seq FROM delivery_events WHERE assignment_id=$1 ORDER BY seq', [id]);
const liveOf = (orderId) => db.many("SELECT id, courier_id FROM delivery_assignments WHERE order_id=$1 AND status IN ('requested','assigned','picked_up','moving','arrived','failed')", [orderId]);
const courierRow = async (id) => E.ok(await A.api('GET', '/shop/delivery/couriers'), 'پیک‌ها').items.find((c) => c.id === id);

/** رویدادِ صفِ گوشیِ پیک: شناسهٔ یکتا، زمان و ترتیبِ همان گوشی */
function ev(dev, assignmentId, type, extra = {}) {
  const n = (seq.get(dev) || 0) + 1;
  seq.set(dev, n);
  return { eventId: `${dev.deviceId}-ev-${n}`, assignmentId, type, deviceTime: T0 + n * 60_000, deviceSeq: n, ...extra };
}
async function send(dev, events) {
  return E.ok(await dev.api('POST', '/courier/events', { deviceId: dev.deviceId, events }), `صفِ ${dev.label}`).results;
}
async function applied(dev, events) {
  const r = await send(dev, events);
  for (const x of r) assert.equal(x.status, 'applied', JSON.stringify(x));
  return r;
}
async function placeOrder(qty, landmark) {
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ پیک', phone: PHONE },
    lines: [{ productId: rice, qtyMilli: qty * 1000 }], address: { text: 'کارتهٔ سه، سرکِ اول', landmark },
  }, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': E.key('web') }), 'ثبتِ سفارشِ ارسال', 201).order;
  const row = await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, r.orderNo]);
  return { ...r, id: row.id };
}
async function prepare(id) {
  for (const [to, from, x] of [['accepted', 'new', { readyInMinutes: 15 }], ['picking', 'accepted'], ['packed', 'picking']]) {
    E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to, from, ...(x || {}) }), `${from} ⇐ ${to}`);
  }
}
function assign(orderId, courierId, extra = {}, key = E.key('asg')) {
  return A.api('POST', `/shop/delivery/orders/${orderId}/assign`, { mode: 'shop_courier', courierId, fareMinor: FARE_MINOR, ...extra }, { headers: { 'Idempotency-Key': key } });
}
/** صفحهٔ پیگیریِ مشتری (همان پیوندِ رسید) تا وضعیتِ خواسته‌شده */
async function customerSees(token, re) {
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(token)}`);
  await page.reload();
  await page.waitForFunction((src) => new RegExp(src).test(document.getElementById('o-status')?.textContent || ''), re.source, { timeout: 10_000 });
  return { status: await page.textContent('#o-status'), cancel: await page.isVisible('#o-cancel'), body: await page.textContent('#view-order') };
}

await E.step('S.courier.0', 'کالا از برنامه، ویترین روشن، دو پیکِ دکان، مشتریِ تأییدشده', async () => {
  A = await E.device('A-owner-phone', owner.email);
  K1 = await E.device('K1', k1Acct.email);
  K2 = await E.device('K2', k2Acct.email);
  W = await E.device('W-clerk', clerkAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  assert.ok(E.ok(await A.ordersConfig(), 'config ِ سفارش‌ها').handoverSince > 0);
  rice = A.stockIn({ name: 'برنج', price: RICE_MINOR / 100, cost: 450, units: 30 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ پیک‌ها', contactPhone: '0700000011', deliveryFeeMinor: FEE_MINOR, minOrderMinor: 0 }), 'ویترین');
  E.ok(await A.api('PUT', `/shop/storefront/products/${rice}`, { published: true }), 'انتشار');
  c1 = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: k1Acct.user.id, cashCapMinor: 5_000_000 }), 'پیکِ یک', 201).courier.id;
  c2 = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: k2Acct.user.id, cashCapMinor: 5_000_000 }), 'پیکِ دو', 201).courier.id;
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone: PHONE }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone: PHONE, code: s.devCode }), 'تأییدِ شماره').ticket;
  C = await E.page('customer');
});

await E.step('S98', 'پیکِ آفلاین: صفِ رویدادها بی شبکه می‌ماند، پس از وصل بی‌ترتیب و یک‌جا می‌رسد ⇐ به ترتیبِ گوشی اعمال؛ ارسالِ دوباره بی‌اثر؛ زمانِ گوشی نگه داشته', async () => {
  o.s98 = await placeOrder(2, 'نزدیکِ مسجدِ جامع');
  await prepare(o.s98.id);
  const a = E.ok(await assign(o.s98.id, c1, { requireCode: true }), 'سپردن به پیکِ یک', 201);
  o.s98.asg = a.assignment.id;
  o.s98.code = a.code;
  //  کارت پیش از قطعِ شبکه گرفته می‌شود (همان که گوشی بی شبکه نشان می‌دهد)
  const card = E.ok(await K1.api('GET', '/courier/missions'), 'مأموریت‌ها').items.find((m) => m.id === o.s98.asg);
  assert.deepEqual([card.address.landmark, card.expectedCollectMinor, card.requiresCode], ['نزدیکِ مسجدِ جامع', 2 * RICE_MINOR + FEE_MINOR, true]);
  K1.offline = true;
  const q = ['assigned', 'picked_up', 'moving', 'arrived'].map((t) => ev(K1, o.s98.asg, t));
  await assert.rejects(K1.api('POST', '/courier/events', { deviceId: K1.deviceId, events: q }), /هواپیما/);
  assert.equal((await asg(o.s98.asg)).status, 'requested', 'رویدادِ آفلاین به سرور رسید');
  assert.equal((await order(o.s98.id)).status, 'packed');
  K1.offline = false;
  const batch = [q[3], q[1], q[0], q[2]];                     //  صف بی‌ترتیب می‌رسد
  const r1 = await applied(K1, batch);
  assert.deepEqual(r1.map((x) => x.eventId), batch.map((x) => x.eventId), 'پاسخ به جایِ هر رویداد');
  assert.equal((await asg(o.s98.asg)).status, 'arrived');
  assert.equal((await order(o.s98.id)).status, 'out_for_delivery');
  const evs = (await eventsOf(o.s98.asg)).filter((e) => q.some((x) => x.eventId === e.event_key));
  assert.deepEqual(evs.map((e) => e.to_status), ['assigned', 'picked_up', 'moving', 'arrived'], 'به ترتیبِ deviceSeq');
  assert.deepEqual(evs.map((e) => Number(e.device_time)), q.map((x) => x.deviceTime), 'زمانِ گوشی نگه داشته شد');
  //  جوابِ بار اول گم شد ⇐ گوشی همان صف را دوباره می‌فرستد
  const n0 = (await eventsOf(o.s98.asg)).length;
  const r2 = await send(K1, batch);
  assert.deepEqual(r2.map((x) => x.status), ['duplicate', 'duplicate', 'duplicate', 'duplicate']);
  assert.equal((await eventsOf(o.s98.asg)).length, n0, 'ارسالِ دوباره رویدادِ دوم ساخت');
  //  همان شناسهٔ رویداد با بدنهٔ دیگر (گوشیِ خراب یا دست‌کاری) ⇐ رد، نه اعمال
  const forged = await send(K1, [{ ...q[3], type: 'delivered', cashCollectedMinor: 1, cashReason: 'partial_payment', code: o.s98.code }]);
  assert.deepEqual([forged[0].status, forged[0].code], ['rejected', 'event_key_reused']);
  assert.equal((await asg(o.s98.asg)).status, 'arrived');
}, { needs: ['S.courier.0'] });

await E.step('S98.withdrawn', 'S98: مأموریتِ پس‌گرفته و سپرده‌شده به پیکِ دیگر در صفِ آفلاینِ پیکِ اول پذیرفته نمی‌شود و اثری ندارد', async () => {
  o.wd = await placeOrder(1, 'کنارِ دواخانه');
  await prepare(o.wd.id);
  const first = E.ok(await assign(o.wd.id, c1), 'سپردن به پیکِ یک', 201).assignment.id;
  K1.offline = true;
  const stale = [ev(K1, first, 'assigned'), ev(K1, first, 'picked_up')];
  //  دکان پیش از رسیدنِ صف پس می‌گیرد و به پیکِ دو می‌سپارد
  const noWhy = await A.api('POST', `/shop/delivery/assignments/${first}/status`, { to: 'withdrawn' }, { headers: { 'Idempotency-Key': E.key('st') } });
  assert.equal(noWhy.status, 400, JSON.stringify(noWhy.body));
  E.ok(await A.api('POST', `/shop/delivery/assignments/${first}/status`, { to: 'withdrawn', reason: 'reassign' }, { headers: { 'Idempotency-Key': E.key('st') } }), 'پس‌گرفتن');
  const second = E.ok(await assign(o.wd.id, c2), 'سپردن به پیکِ دو', 201).assignment;
  assert.equal(second.attemptNo, 2);
  K1.offline = false;
  const r = await send(K1, stale);
  assert.deepEqual(r.map((x) => [x.status, x.code]), [['rejected', 'invalid_transition'], ['rejected', 'invalid_transition']], JSON.stringify(r));
  assert.equal((await asg(first)).status, 'withdrawn');
  assert.equal((await order(o.wd.id)).status, 'packed', 'صفِ کهنه سفارش را «در راه» کرد');
  assert.equal(E.ok(await K1.api('GET', '/courier/missions'), 'مأموریت‌های پیکِ یک').items.some((m) => m.orderId === o.wd.id), false);
  //  رویدادِ پیکِ یک روی مأموریتِ تازه (مالِ پیکِ دو) ⇐ «پیدا نشد»
  const cross = await send(K1, [ev(K1, second.id, 'assigned')]);
  assert.deepEqual([cross[0].status, cross[0].code], ['rejected', 'assignment_not_found']);
  await applied(K2, [ev(K2, second.id, 'assigned')]);
  o.wd.asg = second.id;
}, { needs: ['S98'] });

await E.step('S84', 'اثباتِ تحویل: پیکِ دیگر و شاگرد نمی‌توانند؛ کدِ غلط شمرده و پس از پنج بار قفل؛ کدِ کهنه پس از کدِ تازه باطل؛ کدِ مصرف‌شده اثرِ دوم ندارد؛ وضعیت و دفترِ نقد جدا', async () => {
  const id = o.s98.asg;
  const wrong = o.s98.code === '000000' ? '111111' : '000000';
  const due = 2 * RICE_MINOR + FEE_MINOR;
  //  پیکِ دیگر، با کدِ درست ⇐ «پیدا نشد» (مالِ تو نیست را هم لو نمی‌دهد)
  const other = await send(K2, [ev(K2, id, 'delivered', { code: o.s98.code, cashCollectedMinor: due })]);
  assert.deepEqual([other[0].status, other[0].code], ['rejected', 'assignment_not_found']);
  //  شاگردی که پیک نیست ⇐ درِ پیک بسته؛ از درِ دکان هم «تحویل شد» جای پیک فقط با مجوزِ سپردن
  assert.equal((await W.api('POST', '/courier/events', { events: [ev(W, id, 'delivered', { code: o.s98.code, cashCollectedMinor: due })] })).status, 403);
  const byClerk = await W.api('POST', `/shop/delivery/assignments/${id}/status`, { to: 'delivered', note: 'تحویل دادم', cashCollectedMinor: due }, { headers: { 'Idempotency-Key': E.key('st') } });
  assert.equal(byClerk.status, 403, JSON.stringify(byClerk.body));
  //  کدِ غلط: پنج بار شمرده ⇐ قفل، حتی کدِ درست
  for (let i = 0; i < 5; i++) {
    const r = await send(K1, [ev(K1, id, 'delivered', { code: wrong, cashCollectedMinor: due })]);
    assert.equal(r[0].code, 'bad_code', JSON.stringify(r[0]));
  }
  assert.equal(Number((await asg(id)).code_attempts), 5);
  const locked = await send(K1, [ev(K1, id, 'delivered', { code: o.s98.code, cashCollectedMinor: due })]);
  assert.equal(locked[0].code, 'code_locked');
  //  مدیر کدِ تازه می‌دهد ⇐ کدِ پیشین باطل (منقضی)
  const fresh = E.ok(await A.api('POST', `/shop/delivery/assignments/${id}/code`, {}), 'کدِ تازه').code;
  assert.match(fresh, /^\d{6}$/);
  if (fresh !== o.s98.code) {
    const old = await send(K1, [ev(K1, id, 'delivered', { code: o.s98.code, cashCollectedMinor: due })]);
    assert.equal(old[0].code, 'bad_code', 'کدِ کهنه پس از کدِ تازه هنوز کار می‌کند');
  }
  //  بی مبلغ ⇐ رد؛ کدِ تازه با ارقامِ فارسی (همان که مشتری می‌گوید) و مبلغِ کامل ⇐ تحویل
  const noCash = await send(K1, [ev(K1, id, 'delivered', { code: fresh })]);
  assert.equal(noCash[0].code, 'cash_required');
  const done = ev(K1, id, 'delivered', { code: fresh.replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]), cashCollectedMinor: due });
  await applied(K1, [done]);
  o.s98.used = fresh;
  const row = await asg(id);
  assert.equal(row.status, 'delivered');
  assert.ok(row.code_used_at != null, 'کد مصرف‌شده علامت نخورد');
  //  کدِ مصرف‌شده دوباره (رویدادِ تازه) ⇐ بی‌اثر؛ همان رویداد ⇐ تکراری
  const again = await send(K1, [ev(K1, id, 'delivered', { code: fresh, cashCollectedMinor: due })]);
  assert.equal(again[0].status, 'unchanged');
  assert.equal((await send(K1, [done]))[0].status, 'duplicate');
  assert.equal((await eventsOf(id)).filter((e) => e.to_status === 'delivered').length, 1, 'تحویلِ دوم ثبت شد');
  //  وضعیت ≠ پول: «تحویل شد» ولی نقد هنوز در دستِ پیک تا تسویه
  const ord = await order(o.s98.id);
  assert.deepEqual([ord.status, ord.cod_status], ['delivered', 'collected_by_courier']);
  assert.equal((await courierRow(c1)).cashHeldMinor, due);
  const t = await customerSees(o.s98.token, /تحویل شد/);
  assert.equal(t.body.includes(fresh) || t.body.includes(o.s98.code), false, 'کدِ تحویل در صفحهٔ مشتری');
}, { needs: ['S98'] });

await E.step('S99', 'اختلافِ نقد: پیک کمتر می‌گیرد ⇐ بی دلیل رد؛ با دلیل ⇐ پروندهٔ بررسی؛ COD «تسویه‌شده» نمی‌شود و تسویه بی دلیل بسته نمی‌شود', async () => {
  o.s99 = await placeOrder(1, 'روبه‌روی مکتب');
  await prepare(o.s99.id);
  const a = E.ok(await assign(o.s99.id, c1, { requireCode: true }), 'سپردن', 201);
  o.s99.asg = a.assignment.id;
  const due = RICE_MINOR + FEE_MINOR;
  await applied(K1, ['assigned', 'picked_up', 'arrived'].map((t) => ev(K1, o.s99.asg, t)));
  //  کدِ مصرف‌شدهٔ سفارشِ دیگر روی این سفارش ⇐ غلط (کدها جدا برای هر مأموریت)
  if (o.s98.used !== a.code) {
    const foreign = await send(K1, [ev(K1, o.s99.asg, 'delivered', { code: o.s98.used, cashCollectedMinor: due })]);
    assert.equal(foreign[0].code, 'bad_code');
  }
  const less = due - 20000;
  const noWhy = await send(K1, [ev(K1, o.s99.asg, 'delivered', { code: a.code, cashCollectedMinor: less })]);
  assert.equal(noWhy[0].code, 'cash_reason_required');
  await applied(K1, [ev(K1, o.s99.asg, 'delivered', { code: a.code, cashCollectedMinor: less, cashReason: 'partial_payment' })]);
  const row = await asg(o.s99.asg);
  assert.deepEqual([row.status, row.cash_review, Number(row.cash_collected_minor), row.settlement_id], ['delivered', true, less, '']);
  const done = E.ok(await A.api('GET', '/shop/delivery/assignments?scope=done'), 'میزِ تحویل').items.find((x) => x.id === o.s99.asg);
  assert.deepEqual([done.cashReview, done.cashCollectedMinor, done.expectedCollectMinor], [true, less, due]);
  assert.equal((await order(o.s99.id)).cod_status, 'collected_by_courier', 'اختلاف خودکار «تسویه‌شده» شد');
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${c1}/settlement-preview`), 'پیش‌نمایشِ تسویه');
  const pr = pv.rows.find((x) => x.assignmentId === o.s99.asg);
  assert.deepEqual([pr.cashReview, pr.collectionDiffMinor], [true, -20000]);
  assert.equal(pv.totals.collectionDiffMinor, -20000);
  //  حتی وقتی همهٔ نقدِ گرفته‌شده شمرده شود، اختلافِ مشتری دلیل می‌خواهد ⇐ بی دلیل بسته نمی‌شود
  const blind = await A.api('POST', `/shop/delivery/couriers/${c1}/settlements`,
    { assignmentIds: pv.rows.map((x) => x.assignmentId), handedOverMinor: pv.totals.collectedCashMinor, receivedMinor: pv.totals.collectedCashMinor },
    { headers: { 'Idempotency-Key': E.key('s') } });
  assert.equal(blind.status, 400, JSON.stringify(blind.body));
  assert.equal(blind.body.error.code, 'reason_required');
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM courier_settlements WHERE shop_id=$1', [owner.shopId])).n, 0);
}, { needs: ['S84'] });

await E.step('S57', 'COD و تسویهٔ پیک: پیک بیشتر هم می‌گیرد (با توضیح)؛ تسویه با کسری و دلیل؛ نقدِ پیک صفر؛ هیچ جمعی دو بار شمرده نمی‌شود', async () => {
  o.s57 = await placeOrder(1, 'پشتِ بانک');
  await prepare(o.s57.id);
  o.s57.asg = E.ok(await assign(o.s57.id, c1), 'سپردن', 201).assignment.id;
  const due = RICE_MINOR + FEE_MINOR;
  await applied(K1, ['assigned', 'picked_up'].map((t) => ev(K1, o.s57.asg, t)));
  const more = due + 1000;
  const noNote = await send(K1, [ev(K1, o.s57.asg, 'delivered', { cashCollectedMinor: more, cashReason: 'other' })]);
  assert.equal(noNote[0].code, 'note_required');
  await applied(K1, [ev(K1, o.s57.asg, 'delivered', { cashCollectedMinor: more, cashReason: 'other', note: 'مشتری پولِ خُرد نداشت؛ ۱۰ افغانی بیشتر داد' })]);
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${c1}/settlement-preview`), 'پیش‌نمایش');
  const ids = [o.s98.asg, o.s99.asg, o.s57.asg];
  assert.deepEqual(pv.rows.map((x) => x.assignmentId).sort(), [...ids].sort());
  const collected = (2 * RICE_MINOR + FEE_MINOR) + (due - 20000) + more;
  assert.deepEqual([pv.totals.expectedCashMinor, pv.totals.collectedCashMinor, pv.totals.collectionDiffMinor, pv.totals.fareTotalMinor],
    [(2 * RICE_MINOR + FEE_MINOR) + 2 * due, collected, -20000 + 1000, 3 * FARE_MINOR]);
  assert.equal((await courierRow(c1)).cashHeldMinor, collected);
  //  پیک ۵۰ افغانی کمتر تحویل می‌دهد ⇐ کسری با دلیل
  o.s57.body = { assignmentIds: ids, handedOverMinor: collected - 5000, receivedMinor: collected - 5000, reason: 'شمارش در حضورِ پیک؛ ۵۰ افغانی کم' };
  o.s57.key = E.key('settle');
  const st = E.ok(await A.api('POST', `/shop/delivery/couriers/${c1}/settlements`, o.s57.body, { headers: { 'Idempotency-Key': o.s57.key } }), 'تسویه', 201).settlement;
  assert.deepEqual([st.assignmentsCount, st.collectedCashMinor, st.collectionDiffMinor, st.handoverDueMinor, st.receivedMinor, st.shortageMinor, st.excessMinor, st.fareTotalMinor],
    [3, collected, -19000, collected, collected - 5000, 5000, 0, 3 * FARE_MINOR], JSON.stringify(st));
  assert.equal(st.confirmedBy, owner.user.id);
  o.s57.settlement = st;
  for (const x of [o.s98, o.s99, o.s57]) assert.equal((await order(x.id)).cod_status, 'settled_to_shop');
  assert.equal((await courierRow(c1)).cashHeldMinor, 0, 'نقدِ پیک پس از تسویه');
  assert.deepEqual(E.ok(await A.api('GET', `/shop/delivery/couriers/${c1}/settlement-preview`), 'پیش‌نمایش').rows, [], 'مأموریتِ تسویه‌شده دوباره آمد');
  //  جمع‌ها یک بار: گزارشِ تسویه = همین یک سند؛ تطبیقِ سفارش هر تحویل را یک بار
  const rep = E.ok(await A.api('GET', '/shop/delivery/settlements'), 'گزارشِ تسویه').totals;
  assert.deepEqual([rep.settlements, rep.assignments, rep.collectedCashMinor, rep.receivedMinor, rep.shortageMinor], [1, 3, collected, collected - 5000, 5000]);
  const rec = E.ok(await A.api('GET', '/shop/orders/reconciliation'), 'تطبیقِ سفارش');
  assert.deepEqual([rec.collected.count, rec.collected.totalMinor], [3, (2 * RICE_MINOR + FEE_MINOR) + 2 * due], 'تحویلِ تکراری دوباره شمرده شد');
}, { needs: ['S99'] });

await E.step('S80', 'تسویهٔ تکراری: همان کلید ⇐ همان سند؛ همان کلید با بدنهٔ دیگر ⇐ رد؛ کلیدِ تازه برای همان مأموریت‌ها ⇐ رد؛ یک اثرِ مالی', async () => {
  const url = `/shop/delivery/couriers/${c1}/settlements`;
  const same = await A.api('POST', url, o.s57.body, { headers: { 'Idempotency-Key': o.s57.key } });
  assert.equal(same.status, 200, JSON.stringify(same.body));
  assert.equal(same.body.settlement.id, o.s57.settlement.id);
  assert.equal(same.body.replayed, true);
  const tweaked = await A.api('POST', url, { ...o.s57.body, receivedMinor: o.s57.body.receivedMinor + 5000 }, { headers: { 'Idempotency-Key': o.s57.key } });
  assert.equal(tweaked.status, 409, JSON.stringify(tweaked.body));
  assert.equal(tweaked.body.error.code, 'idempotency_key_reused');
  const twice = await A.api('POST', url, o.s57.body, { headers: { 'Idempotency-Key': E.key('settle') } });
  assert.equal(twice.status, 409, JSON.stringify(twice.body));
  assert.equal(twice.body.error.code, 'settlement_changed');
  //  دو درخواستِ هم‌زمان با کلیدِ تازهٔ یکسان روی همان سند هم یک اثر
  const k = E.key('settle');
  const pair = await Promise.all([1, 2].map(() => A.api('POST', url, o.s57.body, { headers: { 'Idempotency-Key': k } })));
  assert.ok(pair.every((r) => r.status === 409), JSON.stringify(pair.map((r) => r.status)));
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM courier_settlements WHERE shop_id=$1', [owner.shopId])).n, 1);
  const rep = E.ok(await A.api('GET', '/shop/delivery/settlements'), 'گزارشِ تسویه').totals;
  assert.deepEqual([rep.settlements, rep.receivedMinor], [1, o.s57.body.receivedMinor]);
  assert.equal((await courierRow(c1)).cashHeldMinor, 0);
}, { needs: ['S57'] });

await E.step('S100', 'مشتری حاضر نیست: «ناموفق» فقط با دلیل؛ کد مصرف نمی‌شود؛ مشتری «تحویل نشد» را می‌بیند؛ تلاشِ دوباره و برگشت؛ هیچ مبلغ و نقدی افزوده نمی‌شود', async () => {
  const live0 = (await courierRow(c2)).liveCount;
  o.s100 = await placeOrder(1, 'کوچهٔ بی‌نام');
  const total0 = Number((await order(o.s100.id)).total_minor);
  await prepare(o.s100.id);
  const a = E.ok(await assign(o.s100.id, c2, { requireCode: true }), 'سپردن به پیکِ دو', 201);
  o.s100.asg = a.assignment.id;
  await applied(K2, ['assigned', 'picked_up', 'arrived'].map((t) => ev(K2, o.s100.asg, t)));
  const noWhy = await send(K2, [ev(K2, o.s100.asg, 'failed')]);
  assert.equal(noWhy[0].code, 'reason_required');
  await applied(K2, [ev(K2, o.s100.asg, 'failed', { reason: 'no_answer' })]);
  let row = await asg(o.s100.asg);
  assert.deepEqual([row.status, row.code_used_at, Number(row.cash_collected_minor || 0)], ['failed', null, 0], 'کد یا نقد ثبت شد');
  let ord = await order(o.s100.id);
  assert.deepEqual([ord.status, ord.cod_status, Number(ord.total_minor)], ['delivery_failed', 'to_collect', total0]);
  const seen = await customerSees(o.s100.token, /تحویل نشد/);
  assert.equal(seen.cancel, false);
  //  تلاشِ دوباره (همان مأموریت) ⇐ در راه؛ باز ناموفق ⇐ دکان بسته را پس می‌گیرد
  await applied(K2, [ev(K2, o.s100.asg, 'moving')]);
  assert.equal((await order(o.s100.id)).status, 'out_for_delivery');
  await customerSees(o.s100.token, /در راه/);
  await applied(K2, [ev(K2, o.s100.asg, 'failed', { reason: 'no_answer' })]);
  const self = await send(K2, [ev(K2, o.s100.asg, 'returned')]);
  assert.equal(self[0].code, 'staff_only', 'برگشت را دکان تأیید می‌کند');
  E.ok(await W.api('POST', `/shop/delivery/assignments/${o.s100.asg}/status`, { to: 'returned', from: 'failed' }, { headers: { 'Idempotency-Key': E.key('st') } }), 'دریافتِ بسته در دکان');
  ord = await order(o.s100.id);
  assert.deepEqual([ord.status, ord.cod_status, Number(ord.total_minor), Number(ord.delivery_fee_minor)], ['returned_to_store', 'to_collect', total0, FEE_MINOR], 'هزینهٔ اعلام‌نشده');
  row = await asg(o.s100.asg);
  assert.deepEqual([row.status, row.code_used_at], ['returned', null]);
  await customerSees(o.s100.token, /به دکان برگشت/);
  const k2 = await courierRow(c2);
  assert.deepEqual([k2.cashHeldMinor, k2.liveCount], [0, live0], 'مأموریتِ برگشته هنوز زنده یا نقددار است');
  assert.deepEqual(E.ok(await A.api('GET', `/shop/delivery/couriers/${c2}/settlement-preview`), 'پیش‌نمایش').rows, [], 'سفارشِ تحویل‌نشده در تسویه آمد');
  const t = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(o.s100.token)}`, null, { 'X-Store-Ticket': ticket }), 'پیگیری').order;
  assert.equal(Number(t.totalMinor), total0);
}, { needs: ['S.courier.0'] });

await E.step('S101', 'رقابتِ سپردن: دو سپردنِ هم‌زمان به دو پیک ⇐ فقط یک مأموریت؛ دیگری پیامِ روشن؛ رد و سپردنِ دوباره؛ پذیرشِ دیرِ پیکِ اول بی‌اثر؛ شبکهٔ پیکِ VILL3N خاموش', async () => {
  o.s101 = await placeOrder(1, 'سرِ چهارراه');
  await prepare(o.s101.id);
  const pool = await A.api('POST', `/shop/delivery/orders/${o.s101.id}/assign`, { mode: 'platform', fareMinor: FARE_MINOR }, { headers: { 'Idempotency-Key': E.key('asg') } });
  assert.equal(pool.status, 409, JSON.stringify(pool.body));
  assert.equal(pool.body.error.code, 'platform_unavailable');
  const race = await Promise.all([assign(o.s101.id, c1), assign(o.s101.id, c2)]);
  const won = race.filter((r) => r.status === 201);
  const lost = race.filter((r) => r.status !== 201);
  assert.equal(won.length, 1, JSON.stringify(race.map((r) => [r.status, r.body?.error?.code])));
  assert.equal(lost[0].status, 409);
  assert.equal(lost[0].body.error.code, 'already_assigned');
  assert.match(lost[0].body.error.message, /مأموریتِ زنده/);
  assert.equal((await liveOf(o.s101.id)).length, 1);
  const winner = won[0].body.assignment;
  const [W1, L1] = winner.courierId === c1 ? [K1, K2] : [K2, K1];
  const has = async (dev) => E.ok(await dev.api('GET', '/courier/missions'), 'مأموریت‌ها').items.some((m) => m.orderId === o.s101.id);
  assert.deepEqual([await has(W1), await has(L1)], [true, false]);
  //  برنده رد می‌کند (سرش شلوغ است) ⇐ دکان به دیگری می‌سپارد؛ «قبولِ» دیرِ برنده بی‌اثر
  const late = ev(W1, winner.id, 'assigned');
  await applied(W1, [ev(W1, winner.id, 'rejected', { reason: 'busy' })]);
  assert.equal((await order(o.s101.id)).status, 'packed');
  const other = E.ok(await assign(o.s101.id, winner.courierId === c1 ? c2 : c1), 'سپردنِ دوباره', 201).assignment;
  assert.equal(other.attemptNo, 2);
  const r = await send(W1, [late]);
  assert.deepEqual([r[0].status, r[0].code], ['rejected', 'invalid_transition']);
  await applied(L1, [ev(L1, other.id, 'assigned')]);
  assert.deepEqual((await liveOf(o.s101.id)).map((x) => x.id), [other.id]);
}, { needs: ['S.courier.0'] });

await E.step('S120', 'لغو در مسیر: مشتری پس از قبول لغو نمی‌کند؛ دکان فرامی‌خواند ⇐ پیک می‌بیند ⇐ بسته به دکان برمی‌گردد؛ همه یک وضعیت؛ موجودی، کرایه و پول دو بار نه', async () => {
  const shelf = async () => E.ok(await A.api('GET', `/shop/catalog/products/${rice}`), 'کاتالوگ').product.stock.levels.shop_floor;
  o.s120 = await placeOrder(1, 'کنارِ پارک');
  await prepare(o.s120.id);
  o.s120.asg = E.ok(await assign(o.s120.id, c1), 'سپردن', 201).assignment.id;
  await applied(K1, ['assigned', 'picked_up', 'moving'].map((t) => ev(K1, o.s120.asg, t)));
  //  کالا با پیک رفت ⇐ برنامهٔ دکان فروشِ تحویل را می‌سازد (همان `OrdersLive`)
  const before = await shelf();
  const sent = E.ok(await A.api('GET', `/shop/orders/handover?since=0&orderId=${o.s120.id}`), 'تحویل؟').items.find((x) => x.id === o.s120.id);
  assert.ok(sent, 'سفارشِ در راه به برنامه نرسید');
  assert.equal(A.fulfillOrder(sent).ok, true);
  await A.drain();
  assert.equal(await shelf(), before - 1000);
  //  مشتری پس از قبول نمی‌تواند لغو کند (پیام: با دکان تماس بگیرید)
  const cust = await E.post(`/api/store/${SLUG}/orders/${encodeURIComponent(o.s120.token)}/cancel`, {});
  assert.equal(cust.status, 409, JSON.stringify(cust.body));
  assert.equal(cust.body.error.code, 'cannot_cancel');
  assert.equal((await customerSees(o.s120.token, /در راه/)).cancel, false);
  //  دکان فرامی‌خواند (به خواستِ مشتری)؛ تکرارِ همان کلید و کلیدِ تازه هر دو بی‌اثر
  const url = `/shop/delivery/assignments/${o.s120.asg}/recall`;
  assert.equal((await A.api('POST', url, {}, { headers: { 'Idempotency-Key': E.key('rc') } })).status, 400);
  const key = E.key('rc');
  const rc = E.ok(await A.api('POST', url, { reason: 'customer_request' }, { headers: { 'Idempotency-Key': key } }), 'فراخوانی');
  assert.equal(rc.assignment.recall.outcome, 'pending');
  assert.equal((await A.api('POST', url, { reason: 'customer_request' }, { headers: { 'Idempotency-Key': key } })).body.unchanged, true);
  assert.equal((await A.api('POST', url, { reason: 'customer_request' }, { headers: { 'Idempotency-Key': E.key('rc') } })).body.unchanged, true);
  const card = E.ok(await K1.api('GET', '/courier/missions'), 'مأموریت‌ها').items.find((m) => m.id === o.s120.asg);
  assert.equal(card.recall.reason, 'customer_request', 'پیک فراخوانی را نمی‌بیند');
  //  بسته در دکان تحویل گرفته می‌شود (پیک خودش «برگشت» نمی‌زند)
  E.ok(await W.api('POST', `/shop/delivery/assignments/${o.s120.asg}/status`, { to: 'returned' }, { headers: { 'Idempotency-Key': E.key('st') } }), 'دریافتِ بسته');
  const row = await asg(o.s120.asg);
  assert.deepEqual([row.status, row.recall_outcome], ['returned', 'returned']);
  const late = await A.api('POST', url, { reason: 'customer_request' }, { headers: { 'Idempotency-Key': E.key('rc') } });
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'already_returned');
  E.ok(await A.api('POST', `/shop/orders/${o.s120.id}/transition`, { to: 'restocked', from: 'returned_to_store' }), 'بازگشت به قفسه');
  //  سه نما یک وضعیت: دکان، پیک (دیگر زنده نیست، بی نشانی) و مشتری
  const ord = await order(o.s120.id);
  assert.deepEqual([ord.status, ord.cod_status], ['restocked', 'to_collect']);
  const doneCard = E.ok(await K1.api('GET', '/courier/missions?scope=done'), 'مأموریت‌های تمام‌شده').items.find((m) => m.id === o.s120.asg);
  assert.deepEqual([doneCard.status, doneCard.orderStatus, doneCard.address], ['returned', 'restocked', null]);
  await customerSees(o.s120.token, /به دکان برگشت/);
  //  فروشِ تحویل یک بار لغو (دو بار زدن بی‌اثر) ⇐ موجودی همان پیش از سفارش
  const back = E.ok(await A.api('GET', `/shop/orders/handover?since=0&orderId=${o.s120.id}`), 'برگشت؟').reversals.find((x) => x.id === o.s120.id);
  assert.ok(back, 'برگشت به برنامه نرسید');
  assert.equal(A.reverseOrder(back).ok, true);
  assert.equal(A.reverseOrder(back).already, true);
  await A.drain();
  assert.equal(await shelf(), before, 'موجودی دو بار برگشت یا برنگشت');
  //  پول و کرایه: نه نقدی، نه بازپرداختی، نه کرایه در تسویه
  assert.equal((await courierRow(c1)).cashHeldMinor, 0);
  assert.deepEqual(E.ok(await A.api('GET', `/shop/delivery/couriers/${c1}/settlement-preview`), 'پیش‌نمایش').rows, []);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM online_order_refunds WHERE order_id=$1', [o.s120.id])).n, 0);
  assert.equal(E.ok(await A.api('GET', '/shop/delivery/settlements'), 'گزارش').totals.fareTotalMinor, 3 * FARE_MINOR, 'کرایهٔ سفرِ برگشتی در تسویه آمد');
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیقِ موجودی');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['S80'] });

await E.step('S.courier.errors', 'بی خطای جاوااسکریپت در صفحهٔ پیگیری', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
