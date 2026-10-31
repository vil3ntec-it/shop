/**
 * سناریوهای سرتاسریِ چرخهٔ سفارش و رسید — S74، S93، S58، S81، S107، S119، S85 (§۱۹.۱۲، §۲۰.۱۲، §۲۱.۱۹).
 *
 * بازیگرها:
 *   • A — گوشیِ صاحب (برنامهٔ دکان در سطحِ پروتکل): کالا، قیمت و فروشِ تحویل از همگام‌سازی،
 *     گذارهای سفارش و بازپرداخت با همان بدنه‌های برنامه؛
 *   • K — گوشیِ پیکِ دکان (فقط برای سهمِ کرایه در S85)؛
 *   • مشتری — صفحهٔ ویترین (`/store/<slug>`) و رسیدِ QR (`/r/<نشان>/<توکن>`) در کرومیوم، عرضِ گوشی؛
 *     سفارش‌های پرشمارِ S93 از API ِ همان صفحه با بلیتِ شمارهٔ تأییدشده.
 *
 * ⛔ زمان: هیچ خوابی برای هماهنگی نیست؛ ردِ خودکار (ORD-05) با جاروبِ `order-timeout.sweep(at)` و زمانِ
 * صریح (مهلت + ۱ دقیقه) — همان پارامترِ زمانِ خودِ کارگر، نه ساعتِ دیواری.
 * ⛔ موجودی (تصمیمِ صاحبِ مخزن): سرور برای سفارش رزرو و کسری ندارد؛ فقط فروشِ تحویلِ برنامه کم می‌کند.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders، shop.storefront، shop.catalog، shop.orders.refunds، shop.delivery.
 * اجرا:  node test/e2e/run.mjs lifecycle
 */
import { createRequire } from 'node:module';
import crypto from 'node:crypto';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'lifecycle' });
//  ⚠️ پس از boot: کدِ سرور پیکربندی را همان لحظهٔ نخستین require می‌خواند
const require = createRequire(import.meta.url);
const timeout = require('../../src/lib/order-timeout');
const R = require('../../src/lib/receipt-token');
const { db } = E;
const owner = await E.owner('owner-life@e2e.local', 'صاحبِ چرخه', 'دکانِ چرخه');
const courierAcct = await E.staff(owner, 'courier-life@e2e.local', 'پیکِ چرخه');
for (const k of ['shop.orders', 'shop.storefront', 'shop.catalog', 'shop.orders.refunds', 'shop.delivery']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-life-store';
const PHONE = '0791240001';
const FEE = 5000;

let A; let K; let C; let rice; let oil; let tea; let ticket; let courierId;
const o = {};
/** «۱٬۲۴۰ افغانی» ⇒ 1240 */
const digits = (s) => Number(String(s || '').replace(/[۰-۹]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d)).replace(/[^0-9]/g, ''));
const row = (id) => db.one('SELECT status, status_reason, total_minor, subtotal_minor, discount_minor, delivery_fee_minor FROM online_orders WHERE id=$1', [id]);
const count = async () => (await db.one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [owner.shopId])).n;
const stockOf = async (id) => E.ok(await A.api('GET', `/shop/catalog/products/${id}`), 'کاتالوگ').product.stock;

async function go(id, to, from, extra = {}) {
  return A.api('POST', `/shop/orders/${id}/transition`, { to, from, ...extra });
}
async function walk(id, steps) {
  for (const [to, from, x] of steps) E.ok(await go(id, to, from, x), `${from} ⇐ ${to}`);
}
/** هر سناریو شمارهٔ خودش (سقفِ ۱۰ سفارش در ساعت برای هر شماره در هر دکان، RSK — order-rate) */
const tickets = new Map();
async function verify(phone) {
  if (!tickets.has(phone)) {
    const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone }), 'کدِ تأیید');
    tickets.set(phone, E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone, code: s.devCode }), 'تأییدِ شماره').ticket);
  }
  return tickets.get(phone);
}
let phone = PHONE;
async function place(lines, fulfillment = 'pickup', key = E.key('web')) {
  const ticket = await verify(phone);
  const body = {
    fulfillment, paymentMethod: 'cod', customer: { name: 'مشتریِ چرخه', phone },
    lines: lines.map(([productId, qty]) => ({ productId, qtyMilli: qty * 1000 })),
    ...(fulfillment === 'delivery' ? { address: { text: 'شهرِ نو، سرکِ دوم', landmark: 'کنارِ نانوایی' } } : {}),
  };
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, body, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': key }), 'ثبتِ سفارش', 201).order;
  const { id } = await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [owner.shopId, r.orderNo]);
  return { ...r, id };
}
const PREP = [['accepted', 'new', { readyInMinutes: 10 }], ['picking', 'accepted'], ['packed', 'picking']];
/** صفحهٔ پیگیریِ مشتری: برچسب، نوعِ نشان و دکمهٔ لغو، پس از خواندنِ تازه */
async function see(token, label, kind, cancel = false) {
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(token)}`);
  await page.reload();
  await page.waitForFunction((l) => (document.getElementById('o-status')?.textContent || '').startsWith(l), label, { timeout: 10_000 })
    .catch(async () => { throw new Error(`برچسبِ «${label}» نیامد؛ صفحه: «${await page.textContent('#o-status')}»`); });
  const cls = await page.getAttribute('#o-status', 'class');
  assert.ok(cls.split(' ').includes(kind), `نشانِ «${label}»: ${cls} ≠ ${kind}`);
  assert.equal(await page.isVisible('#o-cancel'), cancel, `دکمهٔ لغو در «${label}»`);
  return page;
}
async function toast(page, re) {
  await page.waitForFunction((src) => new RegExp(src).test(document.getElementById('toast')?.textContent || ''), re.source, { timeout: 10_000 });
}

await E.step('S.life.0', 'سه کالا از برنامه، ویترین روشن با ارسال و دریافت، پیکِ دکان، مشتریِ تأییدشده', async () => {
  A = await E.device('A-owner-phone', owner.email);
  K = await E.device('K-courier', courierAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  assert.ok(E.ok(await A.ordersConfig(), 'config ِ سفارش‌ها').handoverSince > 0);
  rice = A.stockIn({ name: 'برنج', price: 120, cost: 90, units: 30 });
  oil = A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 20 });
  tea = A.stockIn({ name: 'چای', price: 80, cost: 50, units: 20 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ چرخه', contactPhone: '0700000021', deliveryFeeMinor: FEE, minOrderMinor: 0 }), 'ویترین');
  for (const id of [rice, oil, tea]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), `انتشارِ ${id}`);
  courierId = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: courierAcct.user.id, cashCapMinor: 5_000_000 }), 'پیک', 201).courier.id;
  ticket = await verify(PHONE);
  C = await E.page('customer');
});

await E.step('S74', 'قیمت بینِ بازکردنِ صفحه و ثبت عوض می‌شود ⇐ ۴۰۹ و خلاصهٔ تازه (قلم، جمع و دکمه) پیش از ثبت؛ بی پذیرش هیچ سفارشی؛ با پذیرش همان مبلغِ تازه', async () => {
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}`);
  await page.waitForSelector('#products li[data-id]', { timeout: 15_000 });
  const add = async (id, n) => { for (let i = 0; i < n; i++) await page.locator(`#products li[data-id="${id}"]`).getByRole('button', { name: /افزودن/ }).click(); };
  await add(rice, 2);
  await add(oil, 1);
  await page.click('#open-cart');
  await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 10_000 });
  await page.click('#cart-next');
  await page.waitForSelector('#view-checkout:not([hidden])');
  await page.check('input[name="fulfillment"][value="pickup"]');
  await page.fill('#c-name', 'مشتریِ چرخه');
  await page.fill('#c-phone', PHONE);
  await page.click('#verify-send');
  await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10_000 });
  const code = ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1];
  assert.ok(code, 'کدِ آزمایشی دیده نشد');
  await page.fill('#v-code', code);
  await page.click('#verify-check');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10_000 });
  assert.equal(digits(await page.textContent('#t-total')), 540);
  //  دکان قیمت را در برنامه عوض می‌کند — مشتری هنوز صفحهٔ قدیم را دارد
  A.setSalePrice(rice, 150);
  await A.drain();
  const n0 = await count();
  await page.click('#submit-order');
  await page.waitForFunction(() => /قیمت عوض شده/.test(document.getElementById('checkout-msg')?.textContent || ''), null, { timeout: 10_000 });
  assert.equal(await count(), n0, 'با قیمتِ کهنه سفارش ساخته شد');
  const msg = await page.textContent('#checkout-msg');
  assert.match(msg, /برنج/);
  assert.equal(digits(msg.split('مبلغِ تازه')[1]), 600, msg);
  assert.equal(digits(await page.textContent('#t-total')), 600, 'خلاصهٔ سبد تازه نشد');
  assert.match(await page.textContent('#submit-order'), /پذیرفتنِ قیمتِ تازه/);
  assert.equal(digits(await page.textContent('#cart-lines')).toString().includes('150'), true, 'قلمِ برنج با قیمتِ تازه نیست');
  await E.shot(C, 'S74-repriced');
  await page.click('#submit-order');
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 10_000 });
  const token = decodeURIComponent(new URL(page.url()).hash.slice(4));
  const { id } = await db.one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY order_no DESC LIMIT 1', [owner.shopId]);
  o.p1 = { id, token };
  const r = await row(id);
  assert.deepEqual([Number(r.total_minor), await count()], [60000, n0 + 1]);
}, { needs: ['S.life.0'] });

await E.step('S93', 'هر وضعیتِ سفارش در صفحهٔ مشتری: برچسب، دلیل، نوعِ نشان و فقط در «ثبت شد» دکمهٔ لغو — دریافت، ارسال، ناموفق و دوباره، برگشت، رد، لغوِ دکان و مشتری، ردِ خودکار، بازپرداخت', async () => {
  //  دریافت از دکان (همان سفارشِ S74)
  await see(o.p1.token, 'ثبت شد — منتظرِ تأییدِ دکان', 'warn', true);
  for (const [to, from, x, label, kind] of [
    ['accepted', 'new', { readyInMinutes: 10 }, 'دکان قبول کرد', 'prep'], ['picking', 'accepted', {}, 'در حالِ جمع‌آوری', 'prep'],
    ['packed', 'picking', {}, 'بسته‌بندی شد', 'prep'], ['ready_for_pickup', 'packed', {}, 'آمادهٔ دریافت از دکان', 'prep'],
    ['picked_up', 'ready_for_pickup', {}, 'تحویل گرفته شد', 'ok'],
  ]) {
    E.ok(await go(o.p1.id, to, from, x), `${from} ⇐ ${to}`);
    await see(o.p1.token, label, kind);
  }
  phone = '0791240093';
  //  ارسال: ناموفق با دلیل ⇐ تحویلِ دوباره ⇐ در راه ⇐ تحویل شد
  o.d1 = await place([[oil, 1]], 'delivery');
  await walk(o.d1.id, [...PREP, ['out_for_delivery', 'packed']]);
  await see(o.d1.token, 'در راه', 'info');
  E.ok(await go(o.d1.id, 'delivery_failed', 'out_for_delivery', { reason: 'no_answer' }), 'ناموفق');
  await see(o.d1.token, 'تحویل نشد — پاسخ ندادید', 'review');
  E.ok(await go(o.d1.id, 'retry_scheduled', 'delivery_failed'), 'تحویلِ دوباره');
  await see(o.d1.token, 'تحویلِ دوباره', 'info');
  await walk(o.d1.id, [['out_for_delivery', 'retry_scheduled'], ['delivered', 'out_for_delivery']]);
  const p = await see(o.d1.token, 'تحویل شد', 'ok');
  assert.equal(await p.locator('#o-steps li.step.done').count(), 5, 'همهٔ پله‌ها «انجام‌شده»');
  //  ارسالِ برگشته به دکان
  o.d2 = await place([[tea, 1]], 'delivery');
  await walk(o.d2.id, [...PREP, ['out_for_delivery', 'packed'], ['delivery_failed', 'out_for_delivery', { reason: 'wrong_address' }],
    ['retry_scheduled', 'delivery_failed'], ['returned_to_store', 'retry_scheduled']]);
  await see(o.d2.token, 'به دکان برگشت', 'err');
  E.ok(await go(o.d2.id, 'restocked', 'returned_to_store'), 'قفسه');
  await see(o.d2.token, 'به دکان برگشت', 'err');
  //  ردِ دکان با دلیل، لغوِ دکان با دلیل
  o.r1 = await place([[tea, 2]]);
  E.ok(await go(o.r1.id, 'rejected', 'new', { reason: 'out_of_stock' }), 'رد');
  const rp = await see(o.r1.token, 'دکان نپذیرفت — ناموجود', 'err');
  assert.equal(await rp.locator('#o-steps li.step.stop').count(), 1, 'پایانِ «رد» در پله‌ها');
  o.x1 = await place([[tea, 1]]);
  await walk(o.x1.id, PREP);
  E.ok(await go(o.x1.id, 'cancelled', 'packed', { reason: 'customer_request' }), 'لغوِ دکان');
  await see(o.x1.token, 'لغو شد — به درخواستِ مشتری', 'err');
  //  لغوِ مشتری از همان دکمه (فقط «ثبت شد»)
  o.y1 = await place([[rice, 1]]);
  const yp = await see(o.y1.token, 'ثبت شد — منتظرِ تأییدِ دکان', 'warn', true);
  await yp.click('#o-cancel');
  await yp.locator('dialog.confirm').getByRole('button', { name: 'لغو کن' }).click();
  await toast(yp, /سفارش لغو شد/);
  await see(o.y1.token, 'شما لغو کردید', 'err');
  assert.equal((await row(o.y1.id)).status, 'cancelled_by_customer');
  //  بازپرداختِ کامل
  o.f1 = await place([[tea, 1]]);
  await walk(o.f1.id, [...PREP, ['ready_for_pickup', 'packed'], ['picked_up', 'ready_for_pickup'], ['return_requested', 'picked_up', { note: 'بسته باز بود' }]]);
  await see(o.f1.token, 'درخواستِ مرجوعی', 'review');
  E.ok(await A.api('POST', `/shop/orders/${o.f1.id}/refunds`, { amountMinor: 8000, reason: 'بستهٔ باز', method: 'cash' }, { headers: { 'Idempotency-Key': E.key('rf') } }), 'بازپرداختِ کامل', 201);
  await see(o.f1.token, 'بازپرداخت شد', 'ok');
  //  ردِ خودکار: تنها سفارشِ «تازه»، جاروب با زمانِ «مهلت + ۱ دقیقه»
  o.t1 = await place([[tea, 1]]);
  const fresh = await db.many("SELECT id, accept_deadline_at FROM online_orders WHERE shop_id=$1 AND status='new'", [owner.shopId]);
  assert.deepEqual(fresh.map((x) => x.id), [o.t1.id], 'سفارشِ تازهٔ دیگری هم هست');
  assert.ok(Number(fresh[0].accept_deadline_at) > 0, 'مهلتِ قبول مُهر نخورد');
  await timeout.sweep(Number(fresh[0].accept_deadline_at) - 60_000);
  assert.equal((await row(o.t1.id)).status, 'new', 'پیش از مهلت رد شد');
  await timeout.sweep(Number(fresh[0].accept_deadline_at) + 60_000);
  await see(o.t1.token, 'بی پاسخ ماند — زمانِ پاسخ گذشت', 'err');
  await E.shot(C, 'S93-auto-rejected');
}, { needs: ['S74'] });

await E.step('S58', 'لغو در هر مرحله موجودی را دست نمی‌زند (بی رزرو)؛ بی دلیل یا پس از حرکت رد؛ پس از تحویل برگشت با دو سندِ جدا (بازپرداخت و مرجوعیِ فاکتور) و تکرار بی اثرِ دوم', async () => {
  phone = '0791240058';
  const s0 = await Promise.all([rice, oil, tea].map(stockOf));
  const local0 = [rice, oil, tea].map((id) => A.stock(id));
  //  لغو در «قبول‌شده»، «جمع‌آوری»، «بسته» و «آمادهٔ دریافت» — هر کدام با دلیل
  const at = [['accepted', PREP.slice(0, 1)], ['picking', PREP.slice(0, 2)], ['packed', PREP], ['ready_for_pickup', [...PREP, ['ready_for_pickup', 'packed']]]];
  for (const [from, steps] of at) {
    const x = await place([[oil, 2], [tea, 1]]);
    await walk(x.id, steps);
    const bare = await go(x.id, 'cancelled', from);
    assert.equal(bare.status, 400, `لغوِ بی دلیل در ${from}: ${bare.status}`);
    //  «آمادهٔ دریافت» فقط با «نیامد» لغو می‌شود (نمودارِ ۳.۹)
    if (from === 'ready_for_pickup') assert.equal((await go(x.id, 'cancelled', from, { reason: 'out_of_stock' })).status, 400);
    E.ok(await go(x.id, 'cancelled', from, { reason: from === 'ready_for_pickup' ? 'no_show' : 'out_of_stock' }), `لغو در ${from}`);
    assert.equal((await row(x.id)).status, 'cancelled');
  }
  //  پس از حرکتِ ارسال لغوِ ساده نیست (راهش فراخوانیِ پیک است)
  const moving = await place([[oil, 1]], 'delivery');
  await walk(moving.id, [...PREP, ['out_for_delivery', 'packed']]);
  const late = await go(moving.id, 'cancelled', 'out_for_delivery', { reason: 'customer_request' });
  assert.ok([400, 409].includes(late.status), `لغو پس از حرکت: ${late.status}`);
  assert.equal((await row(moving.id)).status, 'out_for_delivery');
  //  هیچ کدام از این‌ها (و لغو/ردهای S93) موجودیِ سرور یا برنامه را عوض نکرد و رزروی نساخت
  const s1 = await Promise.all([rice, oil, tea].map(stockOf));
  assert.deepEqual(s1.map((s) => [s.levels.shop_floor, s.reservedMilli]), s0.map((s) => [s.levels.shop_floor, s.reservedMilli]));
  assert.ok(s1.every((s) => Number(s.reservedMilli) === 0), 'رزرو ساخته شد');
  await A.drain();
  assert.deepEqual([rice, oil, tea].map((id) => A.stock(id)), local0);
  //  پس از تحویل: P1 ⇐ فروشِ تحویل در برنامه (−۲ برنج، −۱ روغن)
  const sent = E.ok(await A.api('GET', `/shop/orders/handover?since=0&orderId=${o.p1.id}`), 'تحویل؟').items.find((x) => x.id === o.p1.id);
  assert.equal(A.fulfillOrder(sent).ok, true);
  await A.drain();
  assert.equal((await stockOf(rice)).levels.shop_floor, s0[0].levels.shop_floor - 2000);
  //  برگشت: سندِ پول (بازپرداخت) و سندِ کالا (مرجوعیِ فاکتور) جدا
  E.ok(await go(o.p1.id, 'return_requested', 'picked_up', { note: 'یک کیسه پاره' }), 'درخواستِ مرجوعی');
  const key = E.key('rf');
  const body = { amountMinor: 15000, reason: 'یک کیسه برنجِ پاره', method: 'cash' };
  const r1 = E.ok(await A.api('POST', `/shop/orders/${o.p1.id}/refunds`, body, { headers: { 'Idempotency-Key': key } }), 'بازپرداخت', 201);
  const r2 = await A.api('POST', `/shop/orders/${o.p1.id}/refunds`, body, { headers: { 'Idempotency-Key': key } });
  assert.deepEqual([r2.status, r2.body.entry.id], [200, r1.entry.id], 'تکرارِ بازپرداخت سندِ دوم ساخت');
  const sale = A.ledger.sales.find((s) => s.id === `o-${o.p1.id}`);
  const riceLine = A.ledger.saleItems.find((x) => x.saleId === sale.id && x.productId === rice);
  assert.equal(A.returnItems(sale.id, { [riceLine.id]: 1 }, 'کیسهٔ پاره').ok, true);
  await A.drain();
  const back = await db.many("SELECT row_id FROM sync_rows WHERE account_kind='shop' AND account_id=$1 AND table_name='returns' AND data->>'saleId'=$2", [owner.shopId, sale.id]);
  assert.equal(back.length, 1, 'مرجوعیِ فاکتور روی سرور یک سندِ جدا نیست');
  assert.equal(A.returnItems(sale.id, { [riceLine.id]: 5 }, 'دوباره').ok, true, 'مرجوعیِ دوم (فقط یک کیسهٔ مانده)');
  await A.drain();
  const sItem = await db.one("SELECT data FROM sync_rows WHERE account_kind='shop' AND account_id=$1 AND table_name='saleItems' AND row_id=$2", [owner.shopId, riceLine.id]);
  assert.equal(Number(sItem.data.returnedQty), 2, 'مرجوعی از مقدارِ فروخته بیشتر شد');
  assert.equal(A.returnItems(sale.id, { [riceLine.id]: 1 }, 'سوم').ok, false, 'برگشتِ سوم پذیرفته شد');
  assert.equal((await stockOf(rice)).levels.shop_floor, s0[0].levels.shop_floor, 'دو کیسه به قفسه برگشت، نه بیشتر');
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM online_order_refunds WHERE order_id=$1', [o.p1.id])).n, 1);
  assert.equal((await row(o.p1.id)).status, 'return_requested', 'بازپرداختِ نیمه‌کاره «در انتظار» می‌ماند');
  const rep = E.ok(await A.api('GET', '/shop/catalog/report'), 'تطبیقِ موجودی');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['S93'] });

await E.step('S81', 'برگشتِ جزئی و تکراری: همان کلید بی اثرِ دوم؛ دو بازپرداختِ هم‌زمان که با هم از مانده بیشترند ⇐ فقط یکی؛ جمع هرگز از مبلغِ سفارش بالاتر نه؛ تطبیق درست', async () => {
  phone = '0791240081';
  o.g1 = await place([[rice, 3], [oil, 1]]);
  await walk(o.g1.id, [...PREP, ['ready_for_pickup', 'packed'], ['picked_up', 'ready_for_pickup'], ['return_requested', 'picked_up', { note: 'دو قلم برگشت' }]]);
  const total = Number((await row(o.g1.id)).total_minor);
  assert.equal(total, 3 * 15000 + 30000);
  const url = `/shop/orders/${o.g1.id}/refunds`;
  const k1 = E.key('rf');
  const a = E.ok(await A.api('POST', url, { amountMinor: 20000, reason: 'یک کیسه', method: 'cash' }, { headers: { 'Idempotency-Key': k1 } }), 'بازپرداختِ ۱', 201);
  const again = await Promise.all([1, 2].map(() => A.api('POST', url, { amountMinor: 20000, reason: 'یک کیسه', method: 'cash' }, { headers: { 'Idempotency-Key': k1 } })));
  assert.ok(again.every((r) => r.status === 200 && r.body.entry.id === a.entry.id), JSON.stringify(again.map((r) => r.status)));
  const reuse = await A.api('POST', url, { amountMinor: 25000, reason: 'یک کیسه', method: 'cash' }, { headers: { 'Idempotency-Key': k1 } });
  assert.ok([409, 422].includes(reuse.status), `همان کلید با مبلغِ دیگر: ${reuse.status}`);
  //  مانده ۵۵۰ ⇐ ۴۰۰ و ۳۰۰ هم‌زمان: هر کدام تنها جا دارد، با هم نه
  const race = await Promise.all([40000, 30000].map((m) => A.api('POST', url, { amountMinor: m, reason: 'هم‌زمان', method: 'cash' }, { headers: { 'Idempotency-Key': E.key('rf') } })));
  assert.deepEqual(race.map((r) => r.status).sort(), [201, 409], JSON.stringify(race.map((r) => [r.status, r.body?.error?.code])));
  assert.equal(race.find((r) => r.status === 409).body.error.code, 'refund_exceeds_remaining');
  const sum = (await db.many('SELECT amount_minor FROM online_order_refunds WHERE order_id=$1', [o.g1.id])).reduce((s, x) => s + Number(x.amount_minor), 0);
  assert.ok(sum <= total && [60000, 50000].includes(sum), `جمعِ بازپرداخت ${sum}`);
  const over = await A.api('POST', url, { amountMinor: total - sum + 1, reason: 'یک ریال بیش', method: 'cash' }, { headers: { 'Idempotency-Key': E.key('rf') } });
  assert.equal(over.status, 409);
  const det = E.ok(await A.api('GET', `/shop/orders/${o.g1.id}`), 'جزئیات').order.refund;
  assert.deepEqual([det.totalMinor, det.refundedMinor, det.remainingMinor, det.entries.length], [total, sum, total - sum, 2]);
  const rec = E.ok(await A.api('GET', '/shop/orders/reconciliation'), 'تطبیق');
  assert.equal(rec.issues.filter((x) => x.orderId === o.g1.id).length, 0, JSON.stringify(rec.issues));
}, { needs: ['S.life.0'] });

await E.step('S107', '۴۰۴/۴۰۹: پیوندِ کالای برداشته، کالای برداشته و قیمتِ عوض‌شده در سبد، پیوندِ سفارشِ قدیم و ویترینِ ناموجود هر کدام صفحه یا پیامِ درست؛ هیچ قلم یا سفارشی بی‌صدا حذف نمی‌شود', async () => {
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/`);
  await page.waitForSelector('#products li[data-id]');
  for (const id of [rice, oil]) await page.locator(`#products li[data-id="${id}"]`).getByRole('button', { name: /افزودن/ }).click();
  //  دکان روغن را از ویترین برمی‌دارد ⇐ مشتری دوباره باز می‌کند
  E.ok(await A.api('PUT', `/shop/storefront/products/${oil}`, { published: false }), 'برداشتنِ روغن');
  await page.reload();
  await toast(page, /۱ کالای سبد دیگر در ویترین نیست/);
  await page.click('#open-cart');
  await page.waitForSelector('#cart-sheet li.cline.bad');
  assert.equal(await page.locator('#cart-sheet li.cline').count(), 2, 'قلمِ برداشته بی‌صدا حذف شد');
  assert.match(await page.textContent('#cart-sheet li.cline.bad'), /دیگر در ویترین نیست — بردارید/);
  //  ثبت با قلمِ ناموجود: «ادامه» بسته؛ API ِ مستقیم ۴۰۹ با شناسهٔ همان قلم
  assert.equal(await page.isDisabled('#cart-next'), true, 'ادامه با قلمِ ناموجود باز است');
  const n0 = await count();
  const api = await E.post(`/api/store/${SLUG}/checkout`, { fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'م', phone: PHONE }, lines: [{ productId: rice, qtyMilli: 1000 }, { productId: oil, qtyMilli: 1000 }] }, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': E.key('web') });
  assert.equal(api.status, 409, JSON.stringify(api.body));
  assert.equal(api.body.error.code, 'product_unavailable');
  assert.equal(await count(), n0);
  //  قیمتِ برنج در دکان عوض می‌شود ⇐ پیام و قیمتِ تازه در سبد، نه حذف
  A.setSalePrice(rice, 170);
  await A.drain();
  await page.goto(`${E.apiBase}/store/${SLUG}#/`);
  await page.reload();
  await toast(page, /قیمتِ ۱ کالای سبد با قیمتِ امروز تازه شد/);
  await page.click('#open-cart');
  await page.waitForSelector('#cart-sheet li.cline');
  assert.equal(digits(await page.locator('#cart-sheet li.cline:not(.bad)').textContent()).toString().includes('170'), true, 'قیمتِ تازه در سبد نیامد');
  //  پیوندِ کالای برداشته ⇐ پیامِ روشن و بازگشت به فهرست
  const fresh = await E.page('old-link');
  await fresh.page.goto(`${E.apiBase}/store/${SLUG}#/p/${oil}`);
  await toast(fresh.page, /این کالا دیگر در ویترین نیست/);
  assert.equal(await fresh.page.isVisible('#product-sheet #ps-add'), false, 'برگهٔ کالای برداشته باز شد');
  //  پیوندِ سفارشِ قدیم (روغنِ برداشته در آن است) ⇐ همان رسید، همان قیمت‌ها
  await fresh.page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(o.p1.token)}`);
  await fresh.page.waitForSelector('#o-lines li');
  assert.equal(await fresh.page.locator('#o-lines li').count(), 2, 'قلمِ برداشته از رسیدِ قدیم افتاد');
  assert.match(await fresh.page.textContent('#o-lines'), /روغن/);
  //  ویترینِ ناموجود ⇐ صفحهٔ «پیدا نشد»، نه خطای خام
  await fresh.page.goto(`${E.apiBase}/store/e2e-no-such-store`);
  await fresh.page.waitForSelector('#view-missing:not([hidden])', { timeout: 10_000 });
  assert.match(await fresh.page.textContent('#view-missing'), /این ویترین پیدا نشد/);
  await E.shot(fresh, 'S107-missing');
  E.ok(await A.api('PUT', `/shop/storefront/products/${oil}`, { published: true }), 'بازگرداندنِ روغن');
}, { needs: ['S93'] });

await E.step('S119', 'رسیدِ تاریخی: قیمتِ تازهٔ کالا رسیدِ سفارش و فاکتورِ QR را عوض نمی‌کند؛ پرداختِ جزئی دقیقاً در مانده؛ بازپرداختِ جزئی در جمعِ دکان', async () => {
  //  سفارشِ S74 با برنج ۱۵۰؛ قیمتِ امروز ۱۷۰ (S107) ⇐ رسید همان ۱۵۰ × ۲
  const tr = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(o.p1.token)}`, null, { 'X-Store-Ticket': ticket }), 'پیگیری').order;
  const line = tr.lines.find((l) => l.productId === rice);
  assert.deepEqual([line.unitPriceMinor, line.qtyMilli, line.lineTotalMinor, tr.totalMinor], [15000, 2000, 30000, 60000]);
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(o.p1.token)}`);
  await page.reload();
  await page.waitForSelector('#o-lines li');
  assert.match(await page.textContent('#o-lines'), /۱۵۰|150/);
  assert.equal(/۱۷۰|170/.test(await page.textContent('#o-lines')), false, 'قیمتِ امروز در رسیدِ قدیم');
  const shopView = E.ok(await A.api('GET', `/shop/orders/${o.p1.id}`), 'جزئیات').order;
  assert.equal(shopView.lines.find((l) => l.productId === rice).unitPriceMinor, 15000);
  assert.deepEqual([shopView.refund.totalMinor, shopView.refund.refundedMinor, shopView.refund.remainingMinor], [60000, 15000, 45000]);
  //  فاکتورِ QR ِ فروشِ نسیه: ۲ چای × ۸۰ = ۱۶۰، پرداخت ۶۰ ⇐ باقی ۱۰۰
  const debtor = A.addDebtor('محمودِ آزمون', '0700000099');
  const sold = A.sell({ lines: [{ id: tea, qty: 2 }], payment: 'credit', debtorId: debtor, paid: 60 });
  assert.equal(sold.ok, true, sold.message);
  const qr = R.encodeToken(crypto.randomBytes(16));
  //  برنامه توکنِ QR را هنگامِ فروش روی فاکتور می‌گذارد (SalesEngine.receiptToken)
  const d = structuredClone(A.ledger);
  d.sales.find((s) => s.id === sold.saleId).receiptToken = qr;
  A.save(d);
  await A.drain();
  const tag = R.shopTag(owner.shopId);
  const first = E.ok(await E.get(`/api/receipt/${tag}/${qr}`), 'فاکتورِ QR').receipt;
  assert.deepEqual(first.items.map((x) => [x.name, x.qty, x.unitPrice, x.total]), [['چای', '2', '80', '160']]);
  assert.equal(digits(first.total), 160);
  assert.equal(digits(first.payments[0].amount), 60);
  assert.equal(digits(first.remaining.amount), 100);
  assert.equal(JSON.stringify(first).includes('محمود'), false, 'نامِ قرض‌دار روی فاکتورِ عمومی');
  A.setSalePrice(tea, 95);
  await A.drain();
  const second = E.ok(await E.get(`/api/receipt/${tag}/${qr}`), 'فاکتورِ QR پس از قیمتِ تازه').receipt;
  assert.deepEqual(second, first, 'قیمتِ تازه فاکتورِ قدیم را عوض کرد');
  const rp = await E.page('qr');
  await rp.page.goto(`${E.apiBase}/r/${tag}/${qr}`);
  await rp.page.waitForFunction(() => /باقی‌مانده/.test(document.body.textContent || ''), null, { timeout: 10_000 });
  const text = await rp.page.textContent('body');
  assert.ok(/۱۰۰|100/.test(text) && !/۹۵|95/.test(text), 'صفحهٔ QR');
  await E.shot(rp, 'S119-qr');
}, { needs: ['S58'] });

await E.step('S85', 'جمعِ مشتری = اقلام − تخفیف + هزینهٔ ارسال برای هر سفارش؛ در تسویه سهمِ پیک (کرایه) + سهمِ دکان = نقدِ گرفته‌شده', async () => {
  phone = '0791240085';
  //  همهٔ سفارش‌های این سناریو
  const all = await db.many('SELECT id, fulfillment, subtotal_minor, discount_minor, delivery_fee_minor, total_minor FROM online_orders WHERE shop_id=$1', [owner.shopId]);
  assert.ok(all.length >= 10);
  for (const x of all) {
    const lines = await db.many('SELECT qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE order_id=$1', [x.id]);
    for (const l of lines) assert.equal(Number(l.line_total_minor), Math.round(Number(l.qty_milli) * Number(l.unit_price_minor) / 1000), JSON.stringify(l));
    assert.equal(Number(x.subtotal_minor), lines.reduce((s, l) => s + Number(l.line_total_minor), 0));
    assert.equal(Number(x.delivery_fee_minor), x.fulfillment === 'delivery' ? FEE : 0);
    assert.equal(Number(x.total_minor), Number(x.subtotal_minor) - Number(x.discount_minor) + Number(x.delivery_fee_minor), JSON.stringify(x));
  }
  //  سفارشِ ارسالی با پیک: کرایه از همان نقد برداشته می‌شود
  const d3 = await place([[oil, 1], [tea, 1]], 'delivery');
  await walk(d3.id, PREP);
  const fare = 7000;
  const a = E.ok(await A.api('POST', `/shop/delivery/orders/${d3.id}/assign`, { mode: 'shop_courier', courierId, fareMinor: fare }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201).assignment.id;
  let n = 0;
  const ev = (type, extra = {}) => ({ eventId: `${K.deviceId}-ev-${++n}`, assignmentId: a, type, deviceTime: Date.UTC(2026, 9, 30, 7) + n * 60_000, deviceSeq: n, ...extra });
  const total = Number((await row(d3.id)).total_minor);
  for (const x of E.ok(await K.api('POST', '/courier/events', { events: [ev('assigned'), ev('picked_up'), ev('delivered', { cashCollectedMinor: total })] }), 'رویدادهای پیک').results) {
    assert.equal(x.status, 'applied', JSON.stringify(x));
  }
  const pv = E.ok(await A.api('GET', `/shop/delivery/couriers/${courierId}/settlement-preview`), 'پیش‌نمایش').totals;
  assert.deepEqual([pv.expectedCashMinor, pv.collectedCashMinor, pv.fareTotalMinor], [total, total, fare]);
  const st = E.ok(await A.api('POST', `/shop/delivery/couriers/${courierId}/settlements`,
    { assignmentIds: [a], farePaidFromCash: true, handedOverMinor: total - fare, receivedMinor: total - fare },
    { headers: { 'Idempotency-Key': E.key('st') } }), 'تسویه', 201).settlement;
  assert.deepEqual([st.handoverDueMinor, st.receivedMinor, st.fareTotalMinor, st.shortageMinor, st.excessMinor], [total - fare, total - fare, fare, 0, 0]);
  assert.equal(st.receivedMinor + st.fareTotalMinor, st.collectedCashMinor, 'سهمِ دکان + سهمِ پیک ≠ نقدِ گرفته‌شده');
  assert.equal(st.collectedCashMinor, total);
}, { needs: ['S58', 'S81'] });

await E.step('S.life.errors', 'بی خطای جاوااسکریپت در ویترین و فاکتورِ QR', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
