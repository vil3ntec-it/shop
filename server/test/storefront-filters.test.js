'use strict';
/**
 * WEB-07 — فیلترِ کاتالوگِ ویترین (`lib/storefront-filters.js`، پرچمِ خاموشِ `shop.storefront.filters`).
 *
 *   ۱) ⛔ خاموش ⇒ `GET /products` با هر پارامترِ تازه (حتی نادرست) بیت‌به‌بیت همان پاسخِ بی پارامتر،
 *      `GET /filters` همان پاسخِ مسیرِ ناشناخته و `features` بی کلید.
 *   ۲) روشن: برند (بی حساسیت به بزرگی/فاصله)، کمینه/بیشینهٔ قیمت (مرز شامل)، مشخصه، ترکیبشان، و
 *      صفحه‌بندیِ کلیدی که با پالایه هیچ کالایی را گم یا تکرار نمی‌کند؛ گزینه‌های `GET /filters`.
 *   ۳) ورودیِ نادرست ⇒ ۴۰۰ با کدِ روشن (عدد، مرز، کمینه > بیشینه، نیمهٔ مشخصه، کلیدِ تکراری، درازا).
 *   ۴) «فقط موجود»: موجودیِ نامعلوم هرگز «موجود» نیست (هیچ کالا)؛ با کاتالوگ فقط کالای دارای مانده.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { h, boot, flag, liveStore, setStock } = require('./storefront-finder-fixture');

boot();

const ids = (r) => r.body.items.map((p) => p.id);
const list = async (s, qs = '') => {
  const r = await h.get(`/api/store/${s.slug}/products${qs}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r;
};

test('WEB-07 خاموش ⇒ همان پاسخ و همان ۴۰۴، بی کلیدِ features', async () => {
  const s = await liveStore('فیلتر خاموش');
  const plain = await list(s);
  for (const qs of ['?brand=Ahmad', '?minPriceMinor=10000&maxPriceMinor=20000', '?inStock=1', '?attrName=x&attrValue=y',
    '?minPriceMinor=abc', '?brand=a&brand=b', '?minPriceMinor=5&maxPriceMinor=1']) {
    const r = await h.get(`/api/store/${s.slug}/products${qs}`);
    assert.equal(r.status, 200, qs);
    assert.deepEqual(r.body, plain.body, `خاموش ⇒ بی اثر: ${qs}`);
  }
  const unknown = await h.get(`/api/store/${s.slug}/no-such-door`);
  const off = await h.get(`/api/store/${s.slug}/filters`);
  assert.equal(off.status, unknown.status);
  assert.deepEqual(off.body, unknown.body, 'همان پاسخِ مسیرِ ناشناخته');
  assert.equal('features' in (await h.get(`/api/store/${s.slug}`)).body, false);
});

test('WEB-07 روشن: برند، قیمت، مشخصه، ترکیب و صفحه‌بندی', async () => {
  const s = await liveStore('فیلتر روشن');
  await flag(s.shopId, 'shop.storefront.filters', true);
  assert.deepEqual((await h.get(`/api/store/${s.slug}`)).body.features, { filters: true });
  assert.deepEqual(ids(await list(s)).sort(), ['oil-a', 'soap-a', 'tea-a', 'tea-b', 'tea-c'], 'بی پالایه همان همه');

  assert.deepEqual(ids(await list(s, '?brand=AHMAD')).sort(), ['tea-a', 'tea-b'], 'برند بی حساسیت به بزرگی و فاصله');
  assert.deepEqual(ids(await list(s, '?brand=Lipton')).sort(), ['oil-a', 'tea-c']);
  assert.deepEqual(ids(await list(s, '?brand=Nope')), []);
  assert.deepEqual(ids(await list(s, '?minPriceMinor=8000&maxPriceMinor=12000')).sort(), ['tea-a', 'tea-b'], 'مرزها شامل');
  assert.deepEqual(ids(await list(s, '?maxPriceMinor=7999')), ['soap-a']);
  assert.deepEqual(ids(await list(s, '?minPriceMinor=30001')), ['oil-a']);
  assert.deepEqual(ids(await list(s, `?attrName=${encodeURIComponent('وزن')}&attrValue=${encodeURIComponent('۵۰۰ گرم')}`)).sort(), ['tea-a', 'tea-c']);
  assert.deepEqual(ids(await list(s, `?brand=lipton&maxPriceMinor=40000&attrName=${encodeURIComponent('وزن')}&attrValue=${encodeURIComponent('۵۰۰ گرم')}`)), ['tea-c'], 'ترکیب = «و»');
  //  قیمتِ آنلاین بر قیمتِ دفتر جلو است (همان `priceOf`)
  assert.equal((await h.put('/api/shop/storefront/products/oil-a', { published: true, onlinePriceMinor: 9000 }, { token: s.token })).status, 200);
  assert.deepEqual(ids(await list(s, '?minPriceMinor=8500&maxPriceMinor=9500')), ['oil-a']);

  //  صفحه‌بندی: یکی‌یکی با نشانگر ⇒ همان مجموعه، بی تکرار
  const seen = [];
  let cursor = null;
  for (let i = 0; i < 10; i++) {
    const r = await list(s, `?brand=ahmad&limit=1${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`);
    seen.push(...ids(r));
    cursor = r.body.nextCursor;
    if (!cursor) break;
  }
  assert.deepEqual(seen.sort(), ['tea-a', 'tea-b']);

  const f = await h.get(`/api/store/${s.slug}/filters`);
  assert.equal(f.status, 200, JSON.stringify(f.body));
  assert.equal(f.headers.get('cache-control'), 'no-store');
  assert.deepEqual([...f.body.brands].sort(), ['Ahmad', 'Lipton', 'ahmad'], 'فقط برندِ واقعی، بی خالی');
  assert.deepEqual(f.body.price, { minMinor: 4000, maxMinor: 30000 });
  const attrs = f.body.attributes.map((a) => ({ name: a.name, values: [...a.values].sort() })).sort((a, b) => (a.name < b.name ? -1 : 1));
  assert.deepEqual(attrs, [{ name: 'رنگ', values: ['سبز'] }, { name: 'وزن', values: ['۲۵۰ گرم', '۵۰۰ گرم'] }]);
  assert.equal(f.body.inStock, false, 'بی کاتالوگ «فقط موجود» معنا ندارد');
});

test('WEB-07 ورودیِ نادرست ⇒ ۴۰۰ با کد', async () => {
  const s = await liveStore('فیلتر بد');
  await flag(s.shopId, 'shop.storefront.filters', true);
  const cases = [
    ['?minPriceMinor=-1', 'bad_filter'], ['?minPriceMinor=1.5', 'bad_filter'], ['?maxPriceMinor=abc', 'bad_filter'],
    ['?maxPriceMinor=100000000001', 'bad_filter'], ['?minPriceMinor=5&maxPriceMinor=4', 'bad_price_range'],
    ['?brand=a&brand=b', 'bad_filter'], [`?brand=${'x'.repeat(81)}`, 'bad_filter'], ['?inStock=maybe', 'bad_filter'],
    ['?attrName=x', 'bad_filter'], ['?attrValue=y', 'bad_filter'], ['?attrName[a]=1&attrValue=y', 'bad_filter'],
  ];
  for (const [qs, code] of cases) {
    const r = await h.get(`/api/store/${s.slug}/products${qs}`);
    assert.equal(r.status, 400, `${qs}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code, qs);
  }
  //  مرزِ درست پذیرفته است؛ پارامترِ خالی یعنی «بی پالایه»
  assert.equal((await h.get(`/api/store/${s.slug}/products?minPriceMinor=0&maxPriceMinor=100000000000`)).status, 200);
  assert.equal(ids(await list(s, '?brand=&minPriceMinor=&inStock=0')).length, 5);
});

test('WEB-07 «فقط موجود»: نامعلوم هرگز موجود نیست؛ با کاتالوگ فقط کالای دارای مانده', async () => {
  const blind = await liveStore('بی کاتالوگ');
  await flag(blind.shopId, 'shop.storefront.filters', true);
  assert.deepEqual(ids(await list(blind, '?inStock=1')), [], 'موجودیِ «در حال بررسی» ادعای موجود نمی‌شود');

  const s = await liveStore('با کاتالوگ', { catalog: true });
  await flag(s.shopId, 'shop.storefront.filters', true);
  await setStock(s, 'tea-a', 3000);
  await setStock(s, 'oil-a', 1000);
  await setStock(s, 'oil-a', -1000);
  const r = await list(s, '?inStock=1');
  assert.deepEqual(ids(r).sort(), ['tea-a'], 'فقط کالای دارای مانده');
  assert.ok(r.body.items.every((p) => p.stock.status === 'in_stock'));
  assert.deepEqual(ids(await list(s, '?inStock=1&brand=Lipton')), []);
  assert.equal((await h.get(`/api/store/${s.slug}/filters`)).body.inStock, true);
  assert.equal(ids(await list(s)).length, 5, 'بی پالایه همان همه، با نشانِ تمام‌شده');
});
