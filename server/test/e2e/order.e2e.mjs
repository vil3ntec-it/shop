/**
 * سناریوی سرتاسریِ سفارشِ اینترنتی — از ویترین تا تحویل و بازپرداخت.
 *   E2E-MKT-01، E2E-MKT-02 (S32)، E2E-ORD-03 (S41)، E2E-ORD-04، S43، ORD-17، E2E-RET-11.
 *
 * بازیگرها:
 *   • A — گوشیِ صاحب (برنامهٔ دکان در سطحِ پروتکل): کالا و قیمت از راهِ همگام‌سازی،
 *     سفارش با همان بدنهٔ `OrdersRepository.kt` (`to`، `from`، `readyInMinutes`)؛
 *   • S — گوشیِ شاگرد (شمارهٔ مشتری پوشیده)؛
 *   • مشتری — صفحهٔ ویترین (`/store/<slug>`) در کرومیوم، عرضِ گوشی.
 * پرچم‌ها فقط برای همین دکان و از راهِ live_config: shop.orders، shop.storefront، shop.orders.refunds.
 *
 * اجرا:  node test/e2e/order.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'order' });
const { db } = E;
const owner = await E.owner('owner-ord@e2e.local', 'صاحبِ سفارش', 'دکانِ سفارش');
const staff = await E.staff(owner, 'staff-ord@e2e.local', 'شاگردِ سفارش');
for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.refunds']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-order-store';
const orders = () => db.many('SELECT * FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [owner.shopId]);

let A; let S; let C; let rice; let oil; let first;

/** گذارِ وضعیت با همان بدنهٔ برنامه */
async function go(dev, id, to, from, extra = {}) {
  return dev.api('POST', `/shop/orders/${id}/transition`, { to, from, ...extra });
}
//  DESIGN v2: کارتِ کالا «یک زدن = یک واحد» (−/+ فقط در سبد)
async function addToCart(page, id, times = 1) {
  const li = page.locator(`#products li[data-id="${id}"]`);
  for (let i = 0; i < times; i++) await li.getByRole('button', { name: /افزودن/ }).click();
}
//  نوارِ سبد ⇐ سبدِ باز (در گوشی مدلِ r3/r2) ⇐ «ادامه» ⇐ ثبت
async function toCheckout(page) {
  await page.click('#open-cart');
  await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 10_000 });
  await page.click('#cart-next');
  await page.waitForSelector('#view-checkout:not([hidden])');
}

await E.step('E2E-MKT-01.0', 'A کالا می‌سازد و همگام می‌کند؛ ویترین روشن و دو کالا منتشر', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 120, cost: 90, units: 20 });
  oil = A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 10 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ آزمون', contactPhone: '0700000002', deliveryFeeMinor: 5000, minOrderMinor: 0 }), 'ویترین');
  for (const id of [rice, oil]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), `انتشارِ ${id}`);
});

await E.step('E2E-MKT-01', 'مهمان کالا را می‌بیند و سبد می‌سازد؛ سبد با تازه‌سازی می‌ماند؛ بی شمارهٔ تأییدشده «ثبت» بسته است', async () => {
  C = await E.page('customer');
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}`);
  await page.waitForSelector('#products li[data-id]', { timeout: 15_000 });
  assert.equal(await page.locator('#products li[data-id]').count(), 2);
  await addToCart(page, rice, 2);
  await addToCart(page, oil, 1);
  await page.reload();
  await page.waitForSelector('#products li[data-id]');
  await toCheckout(page);
  await page.check('input[name="fulfillment"][value="pickup"]');
  assert.match(await page.textContent('#t-total'), /۵۴۰|540/, 'جمعِ سبد: ۱۲۰×۲ + ۳۰۰');
  assert.equal(await page.isDisabled('#submit-order'), true, 'بی تأییدِ شماره «ثبت» باز است');
  assert.equal((await orders()).length, 0);
  await E.shot(C, '01-customer-cart');
}, { needs: ['E2E-MKT-01.0'] });

await E.step('E2E-MKT-02', 'S32: قیمت بینِ سبد و ثبت در برنامهٔ دکان عوض می‌شود ⇐ ثبت بی‌صدا نمی‌شود؛ با رضایتِ دوباره قیمتِ تازه', async () => {
  A.setSalePrice(rice, 150);
  await A.drain();
  const { page } = C;
  await page.fill('#c-name', 'مشتریِ آزمون');
  await page.fill('#c-phone', '0791112233');
  await page.click('#verify-send');
  await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10_000 });
  const code = ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1];
  assert.ok(code, 'کدِ آزمایشی دیده نشد');
  await page.fill('#v-code', code);
  await page.click('#verify-check');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10_000 });
  await page.click('#submit-order');
  await page.waitForFunction(() => /قیمت عوض شده/.test(document.getElementById('checkout-msg')?.textContent || ''), null, { timeout: 10_000 });
  assert.equal((await orders()).length, 0, 'با قیمتِ کهنه سفارش ساخته شد');
  assert.match(await page.textContent('#checkout-msg'), /۶۰۰|600/, 'مبلغِ تازه: ۱۵۰×۲ + ۳۰۰');
  await E.shot(C, '02-customer-price-changed');
  await page.click('#submit-order');
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 10_000 });
  const o = await orders();
  assert.equal(o.length, 1);
  assert.equal(Number(o[0].total_minor), 60000);
  assert.equal(o[0].source, 'storefront');
  first = o[0];
  await E.shot(C, '03-customer-receipt');
}, { needs: ['E2E-MKT-01'] });

await E.step('E2E-ORD-03', 'S41: جوابِ «ثبت» وسطِ راه گم می‌شود و مشتری دوباره می‌زند ⇐ فقط یک سفارش', async () => {
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/`);
  await page.waitForSelector('#products li[data-id]');
  await addToCart(page, oil, 1);
  await toCheckout(page);
  await page.check('input[name="fulfillment"][value="pickup"]');
  let cut = 0;
  await page.route('**/checkout', async (route) => {
    if (cut) return route.continue();
    cut++;
    await route.fetch();                       //  سرور می‌گیرد و می‌سازد…
    await route.abort('connectionreset');      //  …ولی جواب به گوشیِ مشتری نمی‌رسد
  });
  await page.click('#submit-order');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10_000 });
  assert.equal((await orders()).length, 2);
  await page.click('#submit-order');           //  «دوباره بزن» — همان کلیدِ یکتایی
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 10_000 });
  await page.unroute('**/checkout');
  assert.equal(cut, 1);
  assert.equal((await orders()).length, 2, 'زدنِ دوباره سفارشِ سوم ساخت');
}, { needs: ['E2E-MKT-02'] });

await E.step('E2E-ORD-04', 'A سفارش را در فهرستِ «جدید» می‌بیند، با زمانِ آماده قبول و تا «تحویل گرفته شد» جلو می‌برد (S43)', async () => {
  const list = E.ok(await A.api('GET', '/shop/orders?tab=new'), 'فهرستِ جدید');
  assert.ok((list.items || list.orders || []).some((o) => o.id === first.id), JSON.stringify(list).slice(0, 300));
  const t0 = Date.now();
  let r = await go(A, first.id, 'accepted', 'new', { readyInMinutes: 20 });
  E.ok(r, 'قبول');
  const ready = Number((await orders()).find((o) => o.id === first.id).ready_at) - t0;
  assert.ok(ready > 19 * 60_000 && ready < 21 * 60_000, `readyInMinutes ِ برنامه به ready_at نرسید: ${ready}`);
  for (const [to, from] of [['picking', 'accepted'], ['packed', 'picking'], ['ready_for_pickup', 'packed'], ['picked_up', 'ready_for_pickup']]) {
    r = await go(A, first.id, to, from);
    E.ok(r, `${from} ⇐ ${to}`);
  }
  assert.equal((await orders()).find((o) => o.id === first.id).status, 'picked_up');
  //  رویدادِ ساختن «created» است (هر دو در: ویترین و برنامه)، گذارها «status»
  const ev = await db.many("SELECT to_status, actor_id FROM online_order_events WHERE order_id=$1 AND kind IN ('created','status') ORDER BY seq", [first.id]);
  assert.deepEqual(ev.map((e) => e.to_status), ['new', 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'], `رویدادها: ${ev.map((e) => e.to_status).join('، ')}`);
  assert.ok(ev.slice(1).every((e) => e.actor_id === owner.user.id), 'هر گذار کننده دارد');
  const det = E.ok(await A.api('GET', `/shop/orders/${first.id}`), 'جزئیات').order;
  assert.deepEqual(det.events.map((e) => e.toStatus).filter(Boolean), ['new', 'accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up'], 'خطِ زمانیِ برنامه');
}, { needs: ['E2E-MKT-02'] });

await E.step('E2E-ORD-04.illegal', 'گذارِ غیرمجاز و وضعیتِ کهنه رد می‌شوند؛ هیچ رویدادی اضافه نمی‌شود', async () => {
  const n0 = (await db.many('SELECT 1 FROM online_order_events WHERE order_id=$1', [first.id])).length;
  for (const to of ['delivered', 'accepted', 'new', 'refunded_full']) {
    const r = await go(A, first.id, to, 'picked_up');
    assert.ok([400, 409, 422].includes(r.status), `${to}: ${r.status} ${JSON.stringify(r.body)}`);
  }
  const stale = await go(A, first.id, 'return_requested', 'accepted');
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'status_changed');
  assert.equal((await db.many('SELECT 1 FROM online_order_events WHERE order_id=$1', [first.id])).length, n0);
}, { needs: ['E2E-ORD-04'] });

await E.step('E2E-ORD-04.customer', 'مشتری در صفحهٔ پیگیری همان وضعیت و همان خطِ زمانی را می‌بیند', async () => {
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/orders`);
  await page.waitForSelector('#my-orders li', { timeout: 10_000 });
  await page.locator('#my-orders li').last().click();
  await page.waitForFunction(() => /تحویل گرفته شد/.test(document.getElementById('o-status')?.textContent || ''), null, { timeout: 10_000 });
  assert.equal(await page.locator('#o-timeline li').count(), 6);
  assert.equal(await page.isVisible('#o-cancel'), false, 'پس از قبول، لغوِ رایگان نیست');
  await E.shot(C, '04-customer-tracking-picked-up');
}, { needs: ['E2E-ORD-04'] });

await E.step('ORD-17', 'شاگرد سفارش را می‌بیند ولی شمارهٔ مشتری پوشیده است', async () => {
  S = await E.device('S-staff-phone', staff.email);
  const r = E.ok(await S.api('GET', `/shop/orders/${first.id}`), 'سفارش برای شاگرد');
  const text = JSON.stringify(r);
  assert.equal(text.includes('791112233'), false, 'شمارهٔ کامل به شاگرد رسید');
  const own = JSON.stringify(E.ok(await A.api('GET', `/shop/orders/${first.id}`), 'سفارش برای صاحب'));
  assert.ok(own.includes('791112233'), 'صاحب شمارهٔ کامل را دارد');
}, { needs: ['E2E-ORD-04'] });

await E.step('E2E-RET-11', 'مرجوعی ⇐ بازپرداختِ جزئی با کلیدِ یکتا؛ تکرار بی‌اثر؛ بیش از مانده رد؛ گزارشِ تطبیق درست؛ مشتری می‌بیند', async () => {
  E.ok(await go(A, first.id, 'return_requested', 'picked_up', { note: 'یک کیسه پاره بود' }), 'درخواستِ مرجوعی');
  const key = E.key('refund');
  const body = { amountMinor: 10000, reason: 'یک کیسه برنج خراب بود', method: 'cash' };
  const r1 = E.ok(await A.api('POST', `/shop/orders/${first.id}/refunds`, body, { headers: { 'Idempotency-Key': key } }), 'بازپرداخت', 201);
  const r2 = await A.api('POST', `/shop/orders/${first.id}/refunds`, body, { headers: { 'Idempotency-Key': key } });
  assert.equal(r2.status, 200, `تکرار: ${r2.status} ${JSON.stringify(r2.body).slice(0, 200)}`);
  assert.equal(r2.body.entry.id, r1.entry.id, 'تکرارِ همان کلید ردیفِ دوم ساخت');
  const over = await A.api('POST', `/shop/orders/${first.id}/refunds`, { amountMinor: 50001, reason: 'بیش از مانده' }, { headers: { 'Idempotency-Key': E.key('refund') } });
  assert.equal(over.status, 409, `بیش از مانده: ${over.status} ${JSON.stringify(over.body).slice(0, 200)}`);
  assert.equal(over.body.error.code, 'refund_exceeds_remaining');
  const viaStaff = await S.api('POST', `/shop/orders/${first.id}/refunds`, { amountMinor: 100, reason: 'شاگرد' }, { headers: { 'Idempotency-Key': E.key('refund') } });
  assert.equal(viaStaff.status, 403, `شاگرد بازپرداخت زد: ${viaStaff.status}`);
  assert.equal((await orders()).find((o) => o.id === first.id).status, 'return_requested', 'بازپرداختِ نیمه‌کاره: «در انتظار» می‌ماند');
  //  ردیفِ دوم با `final` ⇐ «بازپرداختِ بخشی» و پایانِ کار
  E.ok(await A.api('POST', `/shop/orders/${first.id}/refunds`, { amountMinor: 5000, reason: 'کرایهٔ رفت‌وآمد', method: 'cash', final: true }, { headers: { 'Idempotency-Key': E.key('refund') } }), 'بازپرداختِ پایانی', 201);
  const refunds = await db.many('SELECT amount_minor FROM online_order_refunds WHERE order_id=$1 ORDER BY created_at', [first.id]);
  assert.deepEqual(refunds.map((x) => Number(x.amount_minor)), [10000, 5000]);
  assert.equal((await orders()).find((o) => o.id === first.id).status, 'refunded_partial', 'وضعیت پس از بازپرداختِ جزئی');
  const rep = E.ok(await A.api('GET', '/shop/orders/reconciliation'), 'گزارشِ تطبیق');
  assert.deepEqual([rep.collected.totalMinor, rep.refunds.totalMinor, rep.netMinor], [60000, 15000, 45000], `تطبیق: ${JSON.stringify(rep).slice(0, 300)}`);
  const { page } = C;
  await page.reload();
  await page.waitForFunction(() => /بخشی بازپرداخت شد/.test(document.getElementById('o-status')?.textContent || ''), null, { timeout: 10_000 });
  await E.shot(C, '05-customer-refunded-partial');
}, { needs: ['E2E-ORD-04'] });

await E.step('E2E-ORD.errors', 'بی خطای جاوااسکریپت در ویترین', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
