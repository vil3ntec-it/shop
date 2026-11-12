/**
 * ABUSE-01 (v3 §۳۸.۶) — «گزارشِ مشکل» در ویترینِ وب (`store/trust.js`)، در کرومیوم، با همان سرور.
 *
 *   • پرچمِ خاموشِ `market.trust` ⇒ نه trust.js، نه درخواست به `…/trust/…`، نه هیچ گره‌ای.
 *   • روشن: پایینِ صفحهٔ دکان «گزارشِ مشکل با این دکان»؛ بی شمارهٔ تأییدشده ⇒ کد همان‌جا، سپس همان فرستادن ⇒ پرونده؛
 *     «گزارش‌های من» با وضعیت؛ در برگهٔ کالا «گزارشِ این کالا» ⇒ پروندهٔ کالا. تکرار ⇒ «پیش‌تر ثبت شده».
 *   • سقفِ روزانه ⇒ پیامِ «امروز گزارش زیاد شد» و دکمه دوباره باز؛ پاسخِ دکان (از برنامه) در «گزارش‌های من» دیده می‌شود.
 *   • بی نشتِ داده: نه شماره و نه توکن در نشانی، در متنِ صفحه یا در نمای دکان.
 * اجرا:  node test/e2e/run.mjs trust
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'trust', env: { TRUST_DAILY_PER_PHONE: '3' } });
const { db } = E;
const SLUG = 'trust-e2e';
const SLUG_OFF = 'trust-off-e2e';
const PHONE = '0795556677';

const owner = await E.owner('owner-trust@e2e.local', 'صاحبِ گزارش', 'دکانِ گزارش');
const off = await E.owner('owner-trust-off@e2e.local', 'صاحبِ خاموش', 'دکانِ خاموش');
for (const o of [owner, off]) for (const k of ['shop.orders', 'shop.storefront']) await E.flag(o.shopId, k, true);
let A; let rice; let oil; let tea; let naan;
let G;

async function openStore(page, slug) {
  await page.goto(`${E.apiBase}/store/${slug}`);
  await page.waitForSelector('#products li[data-id]', { state: 'attached', timeout: 15_000 });
  await page.waitForSelector('#boot', { state: 'hidden', timeout: 15_000 });
}
const note = (page, sel, re) => page.waitForFunction(([s, r]) => new RegExp(r).test(document.querySelector(s)?.textContent || ''),
  [sel, re.source], { timeout: 10_000 });

await E.step('TR.0', 'دو دکان از برنامه با کالا و ویترین؛ «گزارشِ مشکل» فقط برای یکی روشن', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 120, cost: 90, units: 20 });
  oil = A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 10 });
  tea = A.stockIn({ name: 'چای', price: 150, cost: 100, units: 10 });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ گزارش', contactPhone: '0700000007' }), 'ویترین');
  for (const id of [rice, oil, tea]) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), `انتشارِ ${id}`);
  const B = await E.device('B-off-phone', off.email);
  assert.equal((await B.firstSync()).ok, true);
  naan = B.stockIn({ name: 'نان', price: 15, cost: 10, units: 50 });
  await B.drain();
  E.ok(await B.api('PUT', '/shop/storefront', { slug: SLUG_OFF, enabled: true, title: 'ویترینِ خاموش', contactPhone: '0700000008' }), 'ویترینِ دو');
  E.ok(await B.api('PUT', `/shop/storefront/products/${naan}`, { published: true }), 'انتشارِ نان');
  await E.flag(owner.shopId, 'market.trust', true);
});

await E.step('TR.off', 'پرچمِ خاموش ⇒ نه trust.js، نه درخواستِ trust، نه بخش و نه دکمهٔ برگهٔ کالا', async () => {
  G = await E.page('guest');
  const { page } = G;
  const asked = [];
  page.on('request', (r) => { if (/\/trust(\.js|\/)|trust\.[a-z]{2}\.json/.test(r.url())) asked.push(r.url()); });
  await openStore(page, SLUG_OFF);
  await page.waitForTimeout(800);
  assert.equal(await page.$('#tr-box'), null);
  await page.goto(`${E.apiBase}/store/${SLUG_OFF}#/p/${encodeURIComponent(naan)}`);
  await page.waitForSelector('#product-sheet:not([hidden])', { timeout: 15_000 });
  await page.waitForTimeout(500);
  assert.equal(await page.$('#tr-ps'), null);
  assert.deepEqual(asked, []);
}, { needs: ['TR.0'] });

await E.step('ABUSE-01.web', 'شکایت از دکان: بی شماره ⇒ کد همان‌جا ⇒ پرونده؛ «گزارش‌های من» با وضعیت؛ تکرار ⇒ «پیش‌تر ثبت شده»', async () => {
  const { page } = G;
  const urls = [];
  page.on('request', (r) => urls.push(r.url()));
  await openStore(page, SLUG);
  await page.waitForSelector('#tr-box', { state: 'attached', timeout: 10_000 });
  await page.click('#tr-box > summary');
  //  بی دسته ⇒ ایرادِ فرم و هیچ درخواستی
  const before = urls.length;
  await page.click('#tr-c-send');
  await note(page, '#tr-c-msg', /موضوع را برگزینید/);
  assert.equal(urls.slice(before).filter((u) => /\/trust\//.test(u)).length, 0);

  await page.selectOption('#tr-c-cat', 'rude');
  await page.fill('#tr-c-text', 'فروشنده با مشتری بد برخورد کرد');
  await page.click('#tr-c-send');
  await page.waitForSelector('#tr-c-verify', { timeout: 10_000 });
  await page.fill('#tr-c-phone', PHONE);
  await page.click('#tr-c-vsend');
  await page.waitForSelector('#tr-c-vrow:not([hidden])', { timeout: 10_000 });
  await note(page, '#tr-c-msg', /\d{4,8}/);
  const code = (await page.textContent('#tr-c-msg')).match(/(\d{4,8})/)[1];
  await page.fill('#tr-c-code', code);
  await page.click('#tr-c-vok');
  await note(page, '#tr-c-msg', /گزارش ثبت شد/);
  await page.waitForSelector('#tr-mine li[data-status="open"]', { timeout: 10_000 });
  assert.match(await page.textContent('#tr-mine'), /رفتارِ بد/);
  assert.match(await page.textContent('#tr-mine'), /باز/);
  assert.equal(await page.inputValue('#tr-c-text'), '', 'فرم پس از ثبت پاک شد');

  const rows = await db.many('SELECT kind, subject, category FROM trust_cases WHERE shop_id=$1', [owner.shopId]);
  assert.deepEqual(rows.map((r) => [r.kind, r.subject, r.category]), [['complaint', 'shop', 'rude']]);

  //  همان موضوعِ باز دوباره ⇒ همان پرونده
  await page.selectOption('#tr-c-cat', 'other');
  await page.fill('#tr-c-text', 'باز هم همان');
  await page.click('#tr-c-send');
  await note(page, '#tr-c-msg', /پیش‌تر ثبت شده/);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM trust_cases WHERE shop_id=$1', [owner.shopId])).n, 1);

  //  بی نشت: شماره و توکن نه در نشانی‌ها، نه در متنِ صفحه
  const text = await page.textContent('body');
  assert.doesNotMatch(text, /0795556677|795556677|tc_[0-9a-f]{24}/);
  assert.ok(!urls.some((u) => /795556677/.test(u)), 'شماره در نشانی');
}, { needs: ['TR.0'] });

await E.step('ABUSE-01.product', 'برگهٔ کالا: «گزارشِ این کالا» ⇒ پروندهٔ همان کالا (بلیت همان)؛ سقفِ روزانه ⇒ پیامِ روشن، دکمه باز', async () => {
  const { page } = G;
  await page.goto(`${E.apiBase}/store/${SLUG}#/p/${encodeURIComponent(oil)}`);
  await page.waitForSelector('#product-sheet:not([hidden])', { timeout: 15_000 });
  await page.waitForSelector('#tr-ps', { state: 'attached', timeout: 10_000 });
  await page.click('#tr-ps > summary');
  await page.selectOption('#tr-r-cat', 'counterfeit');
  await page.click('#tr-r-send');
  await note(page, '#tr-r-msg', /گزارش ثبت شد/);
  const r = await db.one(`SELECT product_id FROM trust_cases WHERE shop_id=$1 AND kind='report'`, [owner.shopId]);
  assert.equal(r.product_id, oil);

  //  سومی (چای) هنوز زیرِ سقفِ ۳؛ چهارمی (برنج) ⇒ ۴۲۹
  for (const [id, cat] of [[tea, 'wrong_price'], [rice, 'misleading']]) {
    await page.goto(`${E.apiBase}/store/${SLUG}#/p/${encodeURIComponent(id)}`);
    await page.waitForSelector('#tr-ps', { state: 'attached', timeout: 15_000 });
    await page.click('#tr-ps > summary');
    await page.selectOption('#tr-r-cat', cat);
    await page.click('#tr-r-send');
    await page.waitForFunction(() => /ثبت شد|زیاد شد/.test(document.getElementById('tr-r-msg')?.textContent || ''), null, { timeout: 10_000 });
  }
  assert.match(await page.textContent('#tr-r-msg'), /امروز گزارش زیاد شد/);
  assert.equal(await page.isEnabled('#tr-r-send'), true);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM trust_cases WHERE shop_id=$1', [owner.shopId])).n, 3);
}, { needs: ['ABUSE-01.web'] });

await E.step('ABUSE-01.reply', 'پاسخِ دکان از برنامه در «گزارش‌های من» دیده می‌شود؛ نمای دکان شاکی را نمی‌شناسد', async () => {
  const list = E.ok(await A.api('GET', '/shop/trust/cases'), 'پرونده‌های دکان');
  assert.equal(list.cases.length, 3);
  assert.doesNotMatch(JSON.stringify(list), /795556677|tc_[0-9a-f]{24}\.|reporter/);
  const c = list.cases.find((x) => x.kind === 'complaint');
  E.ok(await A.api('POST', `/shop/trust/cases/${c.id}/responses`, { body: 'پوزش؛ با فروشنده صحبت شد', clientId: 'reply-tr-e2e-0001' }), 'پاسخ');
  const { page } = G;
  await openStore(page, SLUG);
  await page.waitForSelector('#tr-box', { state: 'attached', timeout: 10_000 });
  await page.click('#tr-box > summary');
  await page.waitForFunction(() => /پوزش؛ با فروشنده صحبت شد/.test(document.getElementById('tr-mine')?.textContent || ''), null, { timeout: 10_000 });
  assert.equal(await page.$$eval('#tr-mine li.card', (x) => x.length), 3);
}, { needs: ['ABUSE-01.product'] });

await E.finish();
