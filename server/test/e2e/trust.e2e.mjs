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
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'trust', env: { TRUST_DAILY_PER_PHONE: '3' } });
const { db } = E;
const SLUG = 'trust-e2e';
const SLUG_OFF = 'trust-off-e2e';
const PHONE = '0795556677';

const owner = await E.owner('owner-trust@e2e.local', 'صاحبِ گزارش', 'دکانِ گزارش');
//  دکانِ شاهدِ خاموش: بی پرچم‌های `E2E_FLAGS_ON` (با `all` خودِ `market.trust` هم روشن می‌شد)
const off = await E.owner('owner-trust-off@e2e.local', 'صاحبِ خاموش', 'دکانِ خاموش', { flagsOn: false });
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

await E.step('ABUSE-01.evidence', 'مدرک: «افزودنِ عکس» در «گزارش‌های من» ⇒ JPEG ِ تازه بی EXIF/مکان به پرونده؛ همان عکس ⇒ «پیش‌تر»؛ غیرِ عکس ⇒ پیام؛ سقف ⇒ دکمه پنهان؛ پروندهٔ بسته ⇒ بی دکمه', async () => {
  const { page } = G;
  const F = createRequire(import.meta.url)('../image-fixtures');
  const ev = async () => (await db.one(`SELECT COUNT(*)::int AS n FROM trust_evidence e JOIN trust_cases c ON c.id = e.case_id
    WHERE c.shop_id=$1 AND c.kind='complaint'`, [owner.shopId])).n;
  const card = '#tr-mine li.card[data-status="open"]:has(.tr-ev-add)';
  await openStore(page, SLUG);
  await page.waitForSelector('#tr-box', { state: 'attached', timeout: 10_000 });
  await page.click('#tr-box > summary');
  await page.waitForSelector(card, { timeout: 10_000 });
  const cards = await page.$$(card);
  assert.equal(cards.length, 3, 'هر سه پروندهٔ باز دکمه دارند');
  //  شکایت (همان که دکان پاسخ داد) — بی عکس هنوز
  const c = page.locator(card, { hasText: 'پوزش؛ با فروشنده صحبت شد' });
  assert.equal(await c.locator('.tr-ev-n').count(), 1);
  assert.equal((await c.locator('.tr-ev-n').textContent()).trim(), '');
  //  JPEG ِ واقعی (گشودنی در کرومیوم) با بخشِ توضیحِ «مکان» پس از SOI — نه این و نه هیچ metadata ای نباید برسد
  const photo = (seed) => {
    const j = F.jpegImage(320, 240, (x, y) => (x * seed + y * 2) % 256);
    const com = Buffer.from(`LATLONG-SECRET 34.5,69.${seed}`, 'latin1');
    const len = Buffer.alloc(2); len.writeUInt16BE(com.length + 2);
    return Buffer.concat([j.subarray(0, 2), Buffer.from([0xff, 0xfe]), len, com, j.subarray(2)]);
  };
  const shot = photo(3);
  const posts = [];
  page.on('request', (r) => { if (/\/evidence$/.test(r.url()) && r.method() === 'POST') posts.push({ url: r.url(), type: r.headers()['content-type'], ticket: r.headers()['x-store-ticket'] }); });
  await c.locator('.tr-ev-file').setInputFiles({ name: 'receipt.jpg', mimeType: 'image/jpeg', buffer: shot });
  await page.waitForFunction(() => /پیوست شد/.test([...document.querySelectorAll('#tr-mine [data-ev="msg"]')].map((x) => x.textContent).join(' ')), null, { timeout: 15_000 });
  assert.match(await c.locator('.tr-ev-n').textContent(), /۱ عکس پیوست شد/);
  assert.equal(await ev(), 1);
  assert.equal(posts.length, 1);
  assert.equal(posts[0].type, 'application/octet-stream');
  assert.equal(posts[0].ticket, undefined, 'توکنِ پرونده اجازه است، نه بلیت');
  const row = await db.one(`SELECT e.mime, e.data, e.size FROM trust_evidence e JOIN trust_cases c ON c.id = e.case_id WHERE c.shop_id=$1`, [owner.shopId]);
  assert.equal(row.mime, 'image/jpeg');
  assert.ok(!Buffer.from(row.data).includes(Buffer.from('LATLONG-SECRET')), 'نه مکان، نه metadata');
  assert.ok(Number(row.size) < 1536 * 1024);
  //  «دیدنِ عکس‌ها» ⇒ همان عکس از `GET …/evidence/<شناسه>` به‌صورتِ data: (نه نشانیِ توکن‌دار در DOM)
  await c.locator('.tr-ev-view').click();
  await c.locator('.tr-ev-pics img').first().waitFor({ timeout: 10_000 });
  assert.equal(await c.locator('.tr-ev-pics img').count(), 1);
  assert.match(await c.locator('.tr-ev-pics img').getAttribute('src'), /^data:image\/jpeg;base64,/);
  assert.equal(await c.locator('.tr-ev-view').isHidden(), true);
  assert.equal(await c.locator('.tr-ev-pics img').evaluate((i) => i.naturalWidth > 0), true, 'عکس گشوده شد');

  //  همان عکس دوباره ⇒ همان ردیف (سرور با hash)، «پیش‌تر»، شمار همان
  await c.locator('.tr-ev-file').setInputFiles({ name: 'receipt.jpg', mimeType: 'image/jpeg', buffer: shot });
  await page.waitForFunction(() => /پیش‌تر پیوست/.test([...document.querySelectorAll('#tr-mine [data-ev="msg"]')].map((x) => x.textContent).join(' ')), null, { timeout: 15_000 });
  assert.match(await c.locator('.tr-ev-n').textContent(), /۱ عکس پیوست شد/);
  assert.equal(await ev(), 1);
  assert.equal(posts.length, 2);

  //  نه عکس ⇒ پیامِ روشن، هیچ ردیف و هیچ درخواست
  await c.locator('.tr-ev-file').setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('not a photo at all') });
  await page.waitForFunction(() => /فقط عکس/.test([...document.querySelectorAll('#tr-mine [data-ev="msg"]')].map((x) => x.textContent).join(' ')), null, { timeout: 15_000 });
  assert.equal(await ev(), 1);
  assert.equal(posts.length, 2);
  assert.equal(await c.locator('.tr-ev-add').isEnabled(), true);

  //  سقفِ پرونده (۵): چهار عکسِ دیگر ⇒ دکمه پنهان
  for (let i = 0; i < 4; i++) {
    await c.locator('.tr-ev-file').setInputFiles({ name: `p${i}.jpg`, mimeType: 'image/jpeg', buffer: photo(5 + i) });
    await page.waitForFunction((k) => new RegExp(`${k} عکس پیوست شد`).test(document.querySelector('#tr-mine').textContent),
      ['۲', '۳', '۴', '۵'][i], { timeout: 15_000 });
  }
  assert.equal(await ev(), 5);
  assert.equal(await c.locator('.tr-ev-add').isHidden(), true, 'سقف ⇒ بی دکمه');

  //  پروندهٔ بسته ⇒ شمارِ عکس می‌ماند، دکمه نه؛ سرور هم نمی‌پذیرد
  await db.query(`UPDATE trust_cases SET status='closed' WHERE shop_id=$1 AND kind='complaint'`, [owner.shopId]);
  await page.click('#tr-box > summary');
  await page.click('#tr-box > summary');
  await page.waitForSelector('#tr-mine li.card[data-status="closed"]', { timeout: 10_000 });
  const closed = page.locator('#tr-mine li.card[data-status="closed"]');
  assert.match(await closed.locator('.tr-ev-n').textContent(), /۵ عکس پیوست شد/);
  assert.equal(await closed.locator('.tr-ev-add').count(), 0);
  await closed.locator('.tr-ev-view').click();
  await page.waitForFunction(() => document.querySelectorAll('#tr-mine li.card[data-status="closed"] .tr-ev-pics img').length === 5, null, { timeout: 15_000 });
  const srcs = await closed.locator('.tr-ev-pics img').evaluateAll((x) => x.map((i) => i.getAttribute('src').slice(0, 15)));
  assert.deepEqual([...new Set(srcs)], ['data:image/jpeg']);
  const up = await page.evaluate(([u]) => fetch(u, { method: 'POST', body: new Blob([new Uint8Array([0xff, 0xd8, 0xff, 0xd9])]) }).then((r) => r.status),
    [`${E.apiBase}/api/store/${SLUG}/trust/cases/${encodeURIComponent((await db.one(`SELECT id FROM trust_cases WHERE shop_id=$1 AND kind='complaint'`, [owner.shopId])).id)}/evidence`]);
  assert.equal(up, 404, 'شناسهٔ خام توکن نیست');
  const text = await page.textContent('body');
  assert.doesNotMatch(text, /tc_[0-9a-f]{24}/, 'توکن در صفحه نیست');
}, { needs: ['ABUSE-01.reply'] });

await E.finish();
