/**
 * سناریوی سرتاسریِ مشتری — آزمون‌های پذیرشِ §۲۳.۵ (S131 تا S140)، هر کدام یک گام با همان شناسه.
 *
 * بازیگرها:
 *   • A — گوشیِ صاحبِ «دکانِ یک» (برنامهٔ دکان در سطحِ پروتکل): کالا، قیمت و موجودی فقط از راهِ همگام‌سازی؛
 *   • B — گوشیِ صاحبِ «دکانِ دو» (برای پیگیری میانِ دو ویترین)؛
 *   • G — مرورگرِ مهمان (کرومیوم، عرضِ گوشی) که مشتریِ X می‌شود؛ D2 — دستگاهِ دیگرِ همان X؛
 *     W — مرورگری که پیش‌تر شمارهٔ Y در آن تأیید شده؛ Y — مشتریِ دیگرِ همان دکان.
 * پرچم‌ها فقط از راهِ live_config: shop.orders، shop.storefront و shop.catalog (دکانِ یک)، shop.orders و
 * shop.storefront (دکانِ دو)، market.directory (همه). `CATALOG_REFRESH_THROTTLE_MS=0` ⇒ ویترین فروشِ رسیده را
 * در همان خواندنِ بعدی می‌بیند (بی انتظار و بی خواب).
 *
 * اجرا:  node test/e2e/customer.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'customer', env: { CATALOG_REFRESH_THROTTLE_MS: '0' } });
const { db } = E;
const owner = await E.owner('owner-cust@e2e.local', 'صاحبِ دکانِ یک', 'دکانِ یک');
const owner2 = await E.owner('owner-cust2@e2e.local', 'صاحبِ دکانِ دو', 'دکانِ دو');
for (const k of ['shop.orders', 'shop.storefront', 'shop.catalog']) await E.flag(owner.shopId, k, true);
for (const k of ['shop.orders', 'shop.storefront']) await E.flag(owner2.shopId, k, true);
await E.flag('all', 'market.directory', true);

const SLUG = 'e2e-customer-store';
const SLUG_B = 'e2e-customer-two';
const X = '0791112233';
const X_E164 = '+93791112233';
const Y = '0792223344';
const Y_E164 = '+93792223344';
const NET = 'اتصال برقرار نشد';

const orders = (shopId = owner.shopId) => db.many('SELECT * FROM online_orders WHERE shop_id=$1 ORDER BY order_no', [shopId]);
const linesOf = (orderId) => db.many('SELECT product_id, qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [orderId]);

let A; let B; let G; let rice; let oil; let last; let naan;
let cartAtS132 = null;
let first = null;       //  سفارشِ X (ارسال، با هزینه)
let firstToken = '';
let yOrder = null;      //  سفارشِ Y

/* ───────────── کمک‌ها: همان کارهایی که مشتری با انگشت می‌کند ───────────── */

//  DESIGN v2: کارتِ کالا «یک زدن = یک واحد» (−/+ فقط در سبد)
async function addToCart(page, id, times = 1) {
  const li = page.locator(`#products li[data-id="${id}"]`);
  for (let i = 0; i < times; i++) await li.getByRole('button', { name: /افزودن/ }).click();
}
async function openStore(page, slug = SLUG, hash = '') {
  await page.goto(`${E.apiBase}/store/${slug}${hash}`);
  await page.waitForSelector('#products li[data-id]', { state: 'attached', timeout: 15_000 });
}
async function toCheckout(page) {
  await page.click('#open-cart');
  await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 10_000 });
  await page.click('#cart-next');
  await page.waitForSelector('#view-checkout:not([hidden])');
}
/** سبدِ همین دکان همان‌طور که در حافظهٔ مرورگر هست */
const cartOf = (page, slug = SLUG) => page.evaluate((s) => JSON.parse(localStorage.getItem(`store-cart:${s}`) || 'null'), slug);
const ticketOf = (page) => page.evaluate(() => JSON.parse(localStorage.getItem('store-ticket') || 'null'));
/** همان «بستن و بازکردنِ معمولِ صفحه»: زبانه بسته می‌شود، زبانهٔ تازه در همان مرورگر */
async function reopen(pg) {
  await pg.page.close();
  pg.page = await pg.ctx.newPage();
  pg.page.on('pageerror', (e) => E.pageErrors.push(`${pg.label}: ${e.message}`));
  pg.page.on('dialog', (d) => d.accept().catch(() => {}));
  return pg.page;
}
/** کدِ آزمایشی از پیامِ خودِ صفحه (فقط بیرون از تولید نشان داده می‌شود) */
async function devCodeIn(page, sel) {
  await page.waitForFunction((s) => /\d{4,8}/.test(document.querySelector(s)?.textContent || ''), sel, { timeout: 10_000 });
  return (await page.textContent(sel)).match(/(\d{4,8})/)[1];
}
/** مشتری از راهِ API (همان بدنهٔ ویترین) — برای Y که فقط باید سفارشی داشته باشد */
async function apiCustomer(slug, phone) {
  const s = E.ok(await E.post(`/api/store/${slug}/verify/start`, { phone }), 'کدِ تأیید');
  const v = E.ok(await E.post(`/api/store/${slug}/verify/check`, { phone, code: s.devCode }), 'تأییدِ شماره');
  return v.ticket;
}
/** هر کلیدِ بدنهٔ عمومی که بوی دادهٔ خصوصی بدهد (بهای خرید، عمده، ایمیل، رمز، مالک) */
function privateKeys(obj, path = '$', out = []) {
  if (Array.isArray(obj)) obj.forEach((x, i) => privateKeys(x, `${path}[${i}]`, out));
  else if (obj && typeof obj === 'object') {
    for (const [k, v] of Object.entries(obj)) {
      if (/cost|purchase|wholesale|email|password|owner|userId|deviceId|customerPhone|customerName/i.test(k)) out.push(`${path}.${k}`);
      privateKeys(v, `${path}.${k}`, out);
    }
  }
  return out;
}

await E.step('S131.0', 'دو دکان از برنامه: کالا، موجودی و ویترین؛ «دکانِ یک» در بازارچه', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 120, cost: 90, units: 20 });
  oil = A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 10 });
  last = A.stockIn({ name: 'روغنِ آخر', price: 200, cost: 150, units: 1 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ مشتری', tagline: 'کالای روزانه', contactPhone: '0700000005', deliveryFeeMinor: 5000, minOrderMinor: 0 }), 'ویترین');
  for (const id of [rice, oil, last]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), `انتشارِ ${id}`);
  E.ok(await A.api('PUT', '/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }), 'در بازارچه');

  B = await E.device('B-owner2-phone', owner2.email);
  assert.equal((await B.firstSync()).ok, true);
  naan = B.stockIn({ name: 'نانِ تندوری', price: 15, cost: 10, units: 50 });
  await B.drain();
  E.ok(await B.api('PUT', '/shop/storefront', { slug: SLUG_B, enabled: true, title: 'ویترینِ دو', contactPhone: '0700000006', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترینِ دو');
  E.ok(await B.api('PUT', `/shop/storefront/products/${naan}`, { published: true }), 'انتشارِ نان');
});

await E.step('S131', 'مهمانِ بی حساب بازارچه، دکان، کالا و قیمتِ عمومی را می‌بیند؛ هیچ دادهٔ خصوصی بیرون نمی‌آید', async () => {
  G = await E.page('guest');
  const { page } = G;
  const credentialed = [];
  page.on('request', (r) => {
    const h = r.headers();
    if (h.authorization || h['x-store-ticket'] || h['x-store-session']) credentialed.push(r.url());
  });
  //  خانهٔ بازارچه: کارتِ همان دکان
  await page.goto(`${E.apiBase}/market/`);
  await page.waitForSelector(`#sug article.shop-card[data-slug="${SLUG}"]`, { timeout: 15_000 });
  assert.match(await page.textContent(`#sug article.shop-card[data-slug="${SLUG}"]`), /ویترینِ مشتری/);
  //  کالا و قیمتِ عمومی در جست‌وجوی بازارچه، بی هیچ سرآیندِ هویت
  const hit = E.ok(await E.get(`/api/market/search?q=${encodeURIComponent('برنج')}`), 'جست‌وجوی بازارچه').products.find((p) => p.id === rice);
  assert.ok(hit && hit.priceMinor === 12000 && hit.shop.slug === SLUG, JSON.stringify(hit));
  //  ویترین: سه کالا با همان قیمتِ دفترِ دکان
  await openStore(page);
  assert.equal(await page.locator('#products li[data-id]').count(), 3);
  assert.match(await page.textContent(`#products li[data-id="${rice}"]`), /۱۲۰/, 'قیمتِ برنج روی کارت');
  assert.match(await page.textContent(`#products li[data-id="${oil}"]`), /۳۰۰/, 'قیمتِ روغن روی کارت');
  const items = E.ok(await E.get(`/api/store/${SLUG}/products`), 'کاتالوگِ ویترین').items;
  assert.deepEqual(items.map((p) => [p.id, p.priceMinor]).sort(), [[rice, 12000], [oil, 30000], [last, 20000]].sort());
  //  هیچ بدنهٔ عمومی بهای خرید، ایمیل، مالک یا مشتری را ندارد
  const bodies = [
    E.ok(await E.get('/api/market'), 'بازارچه'),
    E.ok(await E.get('/api/market/shops'), 'دکان‌ها'),
    E.ok(await E.get(`/api/market/search?q=${encodeURIComponent('روغن')}`), 'جست‌وجو'),
    E.ok(await E.get(`/api/store/${SLUG}`), 'ویترین'),
    { items },
    E.ok(await E.get(`/api/store/${SLUG}/products/${rice}`), 'برگهٔ کالا'),
  ];
  const leaks = bodies.flatMap((b) => privateKeys(b));
  assert.deepEqual(leaks, [], 'کلیدِ خصوصی در پاسخِ عمومی');
  const raw = JSON.stringify(bodies);
  assert.equal(raw.includes(owner.email), false, 'ایمیلِ صاحبِ دکان');
  assert.equal(raw.includes(owner.user.id), false, 'شناسهٔ حسابِ صاحبِ دکان');
  assert.deepEqual(credentialed, [], 'صفحهٔ مهمان سرآیندِ هویت فرستاد');
  await E.shot(G, 'S131-guest-store');
}, { needs: ['S131.0'] });

await E.step('S132', 'سبدِ مهمان با تازه‌سازی و با بستن و بازکردنِ صفحه می‌ماند؛ فقط دادهٔ لازمِ کالا، جدا برای هر دکان', async () => {
  let { page } = G;
  await addToCart(page, rice, 2);
  await addToCart(page, oil, 1);
  const before = await cartOf(page);
  assert.deepEqual(Object.keys(before).sort(), [rice, oil].sort());
  assert.deepEqual([before[rice].qtyMilli, before[oil].qtyMilli], [2000, 1000]);
  //  حداقلِ داده (§۲۳.۲): فقط کالا، نه نام و شمارهٔ کسی
  const keys = new Set(Object.values(before).flatMap((l) => Object.keys(l)));
  for (const k of keys) assert.ok(['qtyMilli', 'priceMinor', 'name', 'unit', 'maxQtyMilli', 'photo', 'unavailable'].includes(k), `کلیدِ اضافه در سبد: ${k}`);
  await page.reload();
  await page.waitForSelector('#products li[data-id]', { state: 'attached' });
  assert.deepEqual(await cartOf(page), before, 'سبد پس از تازه‌سازی');
  assert.ok(await page.isVisible('#cart-bar'), 'نوارِ سبد پس از تازه‌سازی');
  page = await reopen(G);
  await openStore(page);
  assert.deepEqual(await cartOf(page), before, 'سبد پس از بستن و بازکردن');
  assert.ok(await page.isVisible('#cart-bar'), 'نوارِ سبد پس از بازکردن');
  //  سبدِ دکانِ دیگر جداست
  await openStore(page, SLUG_B);
  assert.equal(await cartOf(page, SLUG_B), null, 'سبدِ دکانِ یک در دکانِ دو پیدا شد');
  assert.equal(await page.isVisible('#cart-bar'), false);
  cartAtS132 = before;
}, { needs: ['S131'] });

await E.step('S133', 'بی شمارهٔ تأییدشده سفارش نهایی نمی‌شود؛ راهنما کوتاه و روشن است؛ سرور هم رد می‌کند', async () => {
  const { page } = G;
  await openStore(page);
  await toCheckout(page);
  assert.equal(await page.isDisabled('#submit-order'), true, 'بی تأیید «ثبت» باز است');
  const hint = (await page.textContent('#verify-text')).trim();
  assert.ok(await page.isVisible('#verify-text') && hint.length <= 120 && /تأیید/.test(hint) && /کد/.test(hint), hint);
  await page.fill('#c-name', 'مشتریِ ایکس');
  await page.fill('#c-phone', X);
  assert.equal(await page.isDisabled('#submit-order'), true, 'نوشتنِ شماره بی کد «ثبت» را باز کرد');
  assert.ok(await page.isVisible('#verify-send'));
  //  سرور: بی بلیت ⇒ ۴۰۱ با پیامِ کوتاه؛ هیچ سفارشی
  const body = { fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ ایکس', phone: X }, lines: [{ productId: rice, qtyMilli: 1000 }] };
  const r = await E.post(`/api/store/${SLUG}/checkout`, body, null, { 'Idempotency-Key': E.key('s133') });
  assert.equal(r.status, 401, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'verification_required');
  assert.ok(r.body.error.message.length <= 80, r.body.error.message);
  assert.equal((await orders()).length, 0);
}, { needs: ['S132'] });

await E.step('S139', 'قطعِ شبکه هنگامِ فرستادن و زدنِ کد: پیامِ دقیقِ «اتصال»، نه «کدِ اشتباه»؛ تلاشِ دوباره؛ بلیتِ موجود پاک نمی‌شود', async () => {
  const { page } = G;
  //  فرستادنِ کد: شبکه قطع
  await page.route('**/verify/start', (r) => r.abort('internetdisconnected'));
  await page.click('#verify-send');
  await page.waitForFunction((t) => (document.getElementById('verify-msg')?.textContent || '').includes(t), NET, { timeout: 10_000 });
  assert.doesNotMatch(await page.textContent('#verify-msg'), /اشتباه/);
  assert.equal(await page.isEnabled('#verify-send'), true, 'دکمه برای تلاشِ دوباره باز نیست');
  await page.unroute('**/verify/start');
  await page.click('#verify-send');
  await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10_000 });
  const code = await devCodeIn(page, '#verify-msg');
  await page.fill('#v-code', code);
  //  زدنِ کد: شبکه قطع ⇒ کد هنوز سنجیده نشده، پس «اشتباه» گفته نمی‌شود و کادر می‌ماند
  await page.route('**/verify/check', (r) => r.abort('internetdisconnected'));
  await page.click('#verify-check');
  await page.waitForFunction((t) => (document.getElementById('verify-msg')?.textContent || '').includes(t), NET, { timeout: 10_000 });
  assert.doesNotMatch(await page.textContent('#verify-msg'), /اشتباه/);
  assert.equal(await page.inputValue('#v-code'), code, 'کدِ نوشته‌شده پاک شد');
  assert.equal(await ticketOf(page), null);
  await page.unroute('**/verify/check');
  await page.click('#verify-check');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10_000 });
  const tk = await ticketOf(page);
  assert.equal(tk && tk.phone, X_E164);
  //  نشستِ موجود: شکستِ شبکه در «ثبت» بلیت را پاک نمی‌کند و چیزی هم ساخته نمی‌شود
  await page.route('**/checkout', (r) => r.abort('internetdisconnected'));
  await page.check('input[name="fulfillment"][value="pickup"]');
  await page.click('#submit-order');
  await page.waitForFunction((t) => (document.getElementById('checkout-msg')?.textContent || '').includes(t), NET, { timeout: 10_000 });
  await page.unroute('**/checkout');
  assert.deepEqual(await ticketOf(page), tk, 'بلیت با خطای شبکه پاک شد');
  assert.equal(await page.isEnabled('#submit-order'), true, 'پس از خطای شبکه «ثبت» بسته ماند');
  //  و با بازکردنِ دوبارهٔ صفحه هم شماره تأییدشده می‌ماند
  await page.reload();
  await page.waitForSelector('#view-checkout:not([hidden])');
  assert.deepEqual(await ticketOf(page), tk);
  assert.equal(await page.inputValue('#c-phone'), X);
  assert.equal((await orders()).length, 0, 'درخواستِ قطع‌شده سفارش ساخت');
}, { needs: ['S133'] });

await E.step('S135', 'قیمت بینِ سبد و ثبت در برنامهٔ دکان عوض می‌شود ⇐ تغییر نشان داده می‌شود و بی «پذیرفتن» سفارشی با مبلغِ تازه ساخته نمی‌شود', async () => {
  const { page } = G;
  await page.fill('#c-name', 'مشتریِ ایکس');
  await page.check('input[name="fulfillment"][value="delivery"]');
  await page.fill('#a-text', 'کارتهٔ سه، سرکِ دوم');
  //  ۱۲۰×۲ + ۳۰۰ + ۵۰ ارسال
  assert.match(await page.textContent('#t-total'), /۵۹۰/);
  A.setSalePrice(rice, 150);
  await A.drain();
  await page.click('#submit-order');
  await page.waitForFunction(() => /قیمت عوض شده/.test(document.getElementById('checkout-msg')?.textContent || ''), null, { timeout: 10_000 });
  const msg = await page.textContent('#checkout-msg');
  assert.match(msg, /برنج/);
  assert.match(msg, /۱۲۰/);
  assert.match(msg, /۱۵۰/);
  assert.match(msg, /۶۵۰/, 'مبلغِ تازه: ۱۵۰×۲ + ۳۰۰ + ۵۰');
  assert.match(await page.textContent('#submit-order'), /پذیرفتن/);
  assert.equal((await orders()).length, 0, 'با قیمتِ کهنه بی‌صدا سفارش ساخته شد');
  //  همان سبد با مبلغِ کهنه از راهِ API هم رد می‌شود
  const tk = await ticketOf(page);
  const stale = await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ ایکس', phone: X },
    lines: [{ productId: rice, qtyMilli: 2000 }, { productId: oil, qtyMilli: 1000 }], expectedTotalMinor: 54000,
  }, null, { 'X-Store-Ticket': tk.ticket, 'Idempotency-Key': E.key('s135') });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'price_changed');
  assert.equal(stale.body.error.quote.totalMinor, 60000);
  assert.equal((await orders()).length, 0);
  await E.shot(G, 'S135-price-changed');
  //  پذیرش ⇒ یک سفارش با مبلغِ پذیرفته‌شده
  await page.click('#submit-order');
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 10_000 });
  const o = await orders();
  assert.equal(o.length, 1);
  assert.deepEqual([Number(o[0].total_minor), Number(o[0].delivery_fee_minor), o[0].fulfillment], [65000, 5000, 'delivery']);
  first = o[0];
  firstToken = decodeURIComponent(new URL(page.url()).hash.slice(4));
}, { needs: ['S139'] });

await E.step('S140', 'سبدِ مهمان با تأییدِ شماره نه کم می‌شود، نه دو بار، نه به شمارهٔ دیگری می‌رود', async () => {
  //  سفارشِ X دقیقاً همان سبدِ مهمان است (همان کالاها، همان مقدار)؛ پس از ثبت سبد خالی
  const lines = await linesOf(first.id);
  assert.deepEqual(lines.map((l) => [l.product_id, Number(l.qty_milli)]).sort(), Object.entries(cartAtS132).map(([id, l]) => [id, l.qtyMilli]).sort());
  assert.equal(first.customer_phone, X_E164);
  assert.deepEqual(await cartOf(G.page), {}, 'سبد پس از ثبت خالی نشد');
  assert.equal((await orders()).length, 1, 'سفارشِ دوم از همان سبد');
  //  مرورگری که پیش‌تر شمارهٔ Y در آن تأیید شده: سبدِ تازهٔ X هرگز به Y نمی‌رسد
  const W = await E.page('shared-browser');
  const p = W.page;
  await openStore(p);
  await addToCart(p, oil, 1);
  await toCheckout(p);
  await p.fill('#c-name', 'مشتریِ وای');
  await p.fill('#c-phone', Y);
  await p.click('#verify-send');
  await p.waitForSelector('#verify-code-row:not([hidden])', { timeout: 10_000 });
  await p.fill('#v-code', await devCodeIn(p, '#verify-msg'));
  await p.click('#verify-check');
  await p.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 10_000 });
  const yTicket = await ticketOf(p);
  assert.equal(yTicket.phone, Y_E164);
  //  کسِ دیگری پشتِ همان مرورگر شمارهٔ خودش (X) را می‌نویسد ⇒ «ثبت» تا تأییدِ X بسته
  await p.fill('#c-phone', X);
  assert.equal(await p.isDisabled('#submit-order'), true, 'سبد با بلیتِ Y برای X باز شد');
  assert.equal(await p.isVisible('#verify-send'), true);
  //  و سرور هم بلیتِ Y را برای شمارهٔ X نمی‌پذیرد
  const wrong = await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ ایکس', phone: X }, lines: [{ productId: oil, qtyMilli: 1000 }],
  }, null, { 'X-Store-Ticket': yTicket.ticket, 'Idempotency-Key': E.key('s140') });
  assert.equal(wrong.status, 403, JSON.stringify(wrong.body));
  assert.equal(wrong.body.error.code, 'phone_not_verified');
  assert.equal((await orders()).length, 1, 'سفارش با بلیتِ شمارهٔ دیگر ساخته شد');
  assert.deepEqual(Object.keys(await cartOf(p)), [oil], 'سبدِ این مرورگر عوض شد');
}, { needs: ['S135'] });

await E.step('S134', 'سفارش با شمارهٔ تأییدشده به همان هویت وصل است: فهرستِ همان شماره و حسابِ مشتری، نه شمارهٔ دیگر', async () => {
  const tk = await ticketOf(G.page);
  //  Y هم سفارشی در همین دکان دارد (برای سنجشِ جدایی)
  const yTicket = await apiCustomer(SLUG, Y);
  const yr = await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ وای', phone: Y }, lines: [{ productId: oil, qtyMilli: 1000 }],
  }, null, { 'X-Store-Ticket': yTicket, 'Idempotency-Key': E.key('y') });
  assert.equal(yr.status, 201, JSON.stringify(yr.body));
  yOrder = yr.body.order;
  //  فهرستِ «سفارش‌های من» با بلیتِ X: فقط سفارشِ X
  const mine = E.ok(await E.get(`/api/store/${SLUG}/orders`, null, { 'X-Store-Ticket': tk.ticket }), 'سفارش‌های X').orders;
  assert.deepEqual(mine.map((o) => o.orderNo), [Number(first.order_no)]);
  assert.equal(mine[0].token, firstToken);
  //  حسابِ مشتری (نشستِ ماندگار) از همان بلیت ⇒ همان شماره و همان سفارش
  const acc = await E.post(`/api/store/${SLUG}/account`, { name: 'مشتریِ ایکس' }, null, { 'X-Store-Ticket': tk.ticket });
  assert.equal(acc.status, 201, JSON.stringify(acc.body));
  assert.equal(acc.body.customer.phone, X_E164);
  const hist = E.ok(await E.get(`/api/store/${SLUG}/account/orders`, null, { 'X-Store-Session': acc.body.session }), 'تاریخچهٔ حساب').orders;
  assert.deepEqual(hist.map((o) => o.orderNo), [Number(first.order_no)], 'حسابِ X سفارشِ دیگری دارد');
  //  و ثبت با نشستِ حساب (بی بلیت) هم به همان شماره می‌نشیند
  const viaSession = await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ ایکس', phone: X }, lines: [{ productId: rice, qtyMilli: 1000 }],
  }, null, { 'X-Store-Session': acc.body.session, 'Idempotency-Key': E.key('acc') });
  assert.equal(viaSession.status, 201, JSON.stringify(viaSession.body));
  const row = (await orders()).find((o) => Number(o.order_no) === viaSession.body.order.orderNo);
  assert.equal(row.customer_phone, X_E164);
  //  نشستِ X برای ثبت با شمارهٔ Y کار نمی‌کند
  const cross = await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتریِ وای', phone: Y }, lines: [{ productId: rice, qtyMilli: 1000 }],
  }, null, { 'X-Store-Session': acc.body.session, 'Idempotency-Key': E.key('cross') });
  assert.equal(cross.status, 403, JSON.stringify(cross.body));
  assert.equal((await orders()).filter((o) => o.customer_phone === Y_E164).length, 1);
}, { needs: ['S140'] });

await E.step('S136', 'رسید: اقلام، مقدار، قیمتِ هر قلم، هزینهٔ ارسال، مبلغِ نهایی، پرداخت‌شده و مانده — همه همان سرور', async () => {
  const { page } = G;
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(firstToken)}`);
  await page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 10_000 });
  const o = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(firstToken)}`), 'پیگیری').order;
  assert.deepEqual([o.subtotalMinor, o.deliveryFeeMinor, o.totalMinor], [60000, 5000, 65000]);
  const shown = await page.$$eval('#o-lines li', (lis) => lis.map((li) => li.textContent.replace(/\s+/g, ' ').trim()));
  assert.equal(shown.length, 2);
  const riceLine = shown.find((s) => s.includes('برنج'));
  const oilLine = shown.find((s) => s.includes('روغن'));
  //  مقدار × قیمتِ هر قلم و جمعِ قلم
  assert.match(riceLine, /۲.*×.*۱۵۰/, riceLine);
  assert.match(riceLine, /۳۰۰/, riceLine);
  assert.match(oilLine, /۱.*×.*۳۰۰/, oilLine);
  //  ردیف‌های جمع: برچسب و مبلغ کنارِ هم
  const cells = await page.$$eval('#o-totals > span', (xs) => xs.map((x) => x.textContent.trim()));
  const rows = Object.fromEntries(cells.reduce((a, c, i) => (i % 2 ? a[a.length - 1].push(c) : a.push([c]), a), []));
  assert.deepEqual(rows, {
    'جمعِ کالاها': '۶۰۰ افغانی', 'هزینهٔ ارسال': '۵۰ افغانی', 'مبلغِ نهایی': '۶۵۰ افغانی',
    'پرداخت‌شده': '۰ افغانی', 'مانده (نقد هنگامِ تحویل)': '۶۵۰ افغانی',
  });
  assert.match(await page.textContent('#o-title'), new RegExp(Number(o.orderNo).toLocaleString('fa-AF')));
  await E.shot(G, 'S136-receipt');
}, { needs: ['S134'] });

await E.step('S137', 'پیگیری فقط با پیوندِ امن: شمارهٔ سفارش، شناسه، امضای دست‌کاری‌شده و توکنِ دکانِ دیگر به هیچ‌چیز نمی‌رسند', async () => {
  //  دارندهٔ پیوند (مرورگرِ تازه، بی بلیت) سفارش را می‌بیند — ولی نه نام، شماره یا نشانی
  const L = await E.page('link-holder');
  await L.page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(firstToken)}`);
  await L.page.waitForFunction(() => document.querySelectorAll('#o-lines li').length > 0, null, { timeout: 10_000 });
  const pub = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(firstToken)}`), 'پیگیری با پیوند').order;
  const raw = JSON.stringify(pub);
  for (const secret of [X_E164, X, '791112233', 'مشتریِ ایکس', 'کارتهٔ سه']) assert.equal(raw.includes(secret), false, `پیگیری «${secret}» را لو داد`);
  //  حدس‌ها
  const [id, mac] = firstToken.split('.');
  const flipped = `${id}.${mac[0] === 'A' ? 'B' : 'A'}${mac.slice(1)}`;
  //  شناسهٔ سفارشِ Y با امضای سفارشِ X (توکن = شناسه + امضای همان سفارش در همان دکان)
  const yId = yOrder.token.split('.')[0];
  const guesses = [String(first.order_no), '1', '2', id, flipped, `${id}.`, `${yId}.${mac}`];
  for (const g of guesses) {
    const r = await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(g)}`);
    assert.equal(r.status, 404, `حدسِ «${g}» ⇒ ${r.status}`);
    assert.equal(JSON.stringify(r.body).includes('برنج'), false);
  }
  //  توکنِ درستِ دکانِ یک روی ویترینِ دکانِ دو
  assert.equal((await E.get(`/api/store/${SLUG_B}/orders/${encodeURIComponent(firstToken)}`)).status, 404);
  //  فهرستِ سفارش‌ها بی بلیت بسته؛ با بلیتِ Y فقط سفارشِ Y
  assert.equal((await E.get(`/api/store/${SLUG}/orders`)).status, 401);
  assert.equal((await E.get('/api/market/orders')).status, 401);
  //  صفحه با شمارهٔ حدسی: «پیدا نشد» و هیچ قلمی
  await L.page.goto(`${E.apiBase}/store/${SLUG}#/o/${first.order_no}`);
  await L.page.waitForFunction(() => /پیدا نشد/.test(document.getElementById('o-status')?.textContent || ''), null, { timeout: 10_000 });
  assert.equal(await L.page.locator('#o-lines li').count(), 0);
}, { needs: ['S136'] });

await E.step('S138', 'X از دستگاهِ دیگر سفارش‌های خودش را می‌بیند (ویترین و بازارچه)، نه سفارشِ Y؛ Y فقط مالِ خودش را', async () => {
  const D2 = await E.page('customer-device-2');
  const p = D2.page;
  //  دستگاهِ تازه: هیچ سفارشِ محلی؛ ورود با کد در «سفارش‌های من»ِ بازارچه
  await p.goto(`${E.apiBase}/market/#/orders`);
  await p.waitForSelector('#verify-card:not([hidden])', { timeout: 15_000 });
  await p.fill('#v-phone', X);
  await p.click('#v-send');
  await p.waitForSelector('#v-code-row:not([hidden])', { timeout: 10_000 });
  await p.fill('#v-code', await devCodeIn(p, '#v-msg'));
  await p.click('#v-check');
  await p.waitForSelector('#who:not([hidden])', { timeout: 10_000 });
  await p.waitForFunction(() => document.querySelectorAll('#orders a').length > 0, null, { timeout: 10_000 });
  const xMine = (await orders()).filter((o) => o.customer_phone === X_E164);
  const hrefs = await p.$$eval('#orders a', (as) => as.map((a) => a.getAttribute('href')));
  assert.equal(hrefs.length, xMine.length, hrefs.join(' '));
  assert.ok(hrefs.every((h) => h.startsWith(`/store/${SLUG}#/o/`)), hrefs.join(' '));
  assert.equal(hrefs.some((h) => h.includes(encodeURIComponent(yOrder.token))), false, 'سفارشِ Y در فهرستِ X');
  //  همان دستگاه در ویترین: «سفارش‌های من» از سرور
  await openStore(p, SLUG, '#/orders');
  await p.waitForFunction((n) => document.querySelectorAll('#my-orders li').length === n, xMine.length, { timeout: 10_000 });
  const storeList = await p.$$eval('#my-orders a', (as) => as.map((a) => a.getAttribute('href')));
  assert.ok(storeList.includes(`#/o/${encodeURIComponent(firstToken)}`), storeList.join(' '));
  assert.equal(storeList.some((h) => h.includes(encodeURIComponent(yOrder.token))), false);
  //  پیوندِ سفارش از دستگاهِ دوم باز می‌شود
  await p.click(`#my-orders a[href="#/o/${encodeURIComponent(firstToken)}"]`);
  await p.waitForFunction(() => document.querySelectorAll('#o-lines li').length === 2, null, { timeout: 10_000 });
  //  Y از راهِ API: فقط سفارشِ خودش، در ویترین و بازارچه
  const yTicket = await apiCustomer(SLUG, Y);
  const yStore = E.ok(await E.get(`/api/store/${SLUG}/orders`, null, { 'X-Store-Ticket': yTicket }), 'سفارش‌های Y').orders;
  assert.deepEqual(yStore.map((o) => o.token), [yOrder.token]);
  const yMarket = E.ok(await E.get('/api/market/orders', null, { 'X-Store-Ticket': yTicket }), 'بازارچهٔ Y').orders;
  assert.deepEqual(yMarket.map((o) => o.token), [yOrder.token]);
  await E.shot(D2, 'S138-second-device');
}, { needs: ['S137'] });

await E.step('S135.stock', 'آخرین واحد در دکان فروخته می‌شود ⇐ سبدِ مشتری «دیگر نیست» را نشان می‌دهد و «ادامه» بسته است؛ هیچ سفارشی', async () => {
  const { page } = G;
  await openStore(page);
  await addToCart(page, last, 1);
  assert.equal((await cartOf(page))[last].qtyMilli, 1000);
  const before = (await orders()).length;
  //  فروشنده همان واحد را در دکان می‌فروشد و همگام می‌کند
  assert.equal(A.sell({ lines: [{ id: last, qty: 1 }] }).ok, true);
  await A.drain();
  assert.equal(A.stock(last), 0);
  //  مشتری صفحه را دوباره باز می‌کند: ویترین همان خواندن موجودیِ تازه را دارد
  await openStore(page);
  await page.waitForFunction((id) => JSON.parse(localStorage.getItem(`store-cart:${document.location.pathname.split('/')[2]}`) || '{}')[id]?.unavailable === true, last, { timeout: 10_000 });
  await page.click('#open-cart');
  await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 10_000 });
  assert.equal(await page.isDisabled('#cart-next'), true, '«ادامه» با کالای تمام‌شده باز است');
  assert.match(await page.textContent('#sheet-msg'), /دیگر در ویترین نیستند/);
  assert.match(await page.textContent('#sheet-lines li.bad'), /روغنِ آخر/);
  assert.equal((await orders()).length, before);
  await E.shot(G, 'S135-stock-gone');
}, { needs: ['S131.0'] });

await E.step('E2E-CUST.errors', 'بی خطای جاوااسکریپت در بازارچه و ویترین', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
