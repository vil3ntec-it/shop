'use strict';
/**
 * S70 (§۱۹.۱۲) — «دو فروشگاه با واحدهای متفاوت» روی «همین کالا در دکان‌های دیگر»
 * (`GET /api/market/same/:bankProductId`، پرچمِ `market.product_details`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) هر پیشنهاد `perUnit` دارد: قیمتِ یک پایه (دانه/کیلو/لیتر/متر) فقط وقتی واحدِ کالا اندازهٔ
 *      روشن دارد؛ «کارتن» ِ بی عدد ⇒ null.
 *   ۲) `cheapest` فقط درونِ همان پایه و با دست‌کم دو پیشنهاد — قیمتِ خامِ کمترِ «کارتن» ِ مبهم هرگز
 *      «ارزان‌ترین» نمی‌شود، و تنها پیشنهادِ کیلویی هم نه.
 *   ۳) `compare` = گروه‌های هم‌سنج (پایه، کمترین، شمار) روی همهٔ پیشنهادهای پیدا‌شده، نه فقط صفحه.
 *   ۴) دو کالای یک دکان که به همان کالای بانک وصل‌اند به قیمتِ یک پایه چیده می‌شوند، نه قیمتِ خامِ
 *      دو واحدِ ناهم‌سنج (پیش‌تر «عدد ۲۰» پیش از «کارتنِ ۲۴ عددی ۴۵۰» می‌آمد).
 *   ۵) واحدِ بی عدد («شیشه»، «بسته») اندازه را از بسته‌های کالا (`packs`) یا واحدِ فروشِ دوم (`saleUnit` + ضریب)
 *      می‌گیرد؛ ⛔ خودِ بسته‌ها (بارکد، قیمتِ جدا) در پاسخ نیستند.
 * قاعدهٔ خالص: `market-units.test.js`.
 */
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  await setFlag('all', 'market.directory', true);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن، در فهرستِ بازار؛ `products` = [شناسه، فیلدها] همه منتشرشده. */
async function liveStore(label, products) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `un-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 5000,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, products.map(([id, f]) => op(id, { category: 'خوراکی', ...f })), `dev-${uniq()}`);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id] of products) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const r = await h.put('/api/shop/market', { listed: true, category: 'grocery' }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug };
}

/** پیوندِ کالای دکان به کالای بانک (همان ردیفی که انتخابِ عکسِ مرکزی می‌سازد). */
async function linkBank(shopId, productId, bankId) {
  await query(
    `INSERT INTO shop_product_images (shop_id, product_id, source, bank_product_id, updated_at)
     VALUES ($1,$2,'none',$3,$4)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET bank_product_id = EXCLUDED.bank_product_id`,
    [shopId, productId, bankId, now()]
  );
}

test('S70: قیمتِ یک دانه فقط با تعدادِ روشن؛ «ارزان‌ترین» فقط میانِ هم‌سنج‌ها', async () => {
  const bank = `bp-un-${uniq()}`;
  const a = await liveStore('عددی', [['cola', { name: 'نوشابه قوطی', unit: 'عدد', salePrice: 20 }]]);
  const b = await liveStore('کارتنی', [
    ['c1', { name: 'نوشابه قوطی', unit: 'عدد', salePrice: 20 }],
    ['c24', { name: 'نوشابه قوطی کارتنی', unit: 'کارتن ۲۴ عددی', salePrice: 450 }],
  ]);
  const c = await liveStore('مبهم', [['box', { name: 'نوشابه کارتنی', unit: 'کارتن', salePrice: 300 }]]);
  const d = await liveStore('وزنی', [['kg', { name: 'نوشابه', unit: 'کیلو', salePrice: 90 }]]);
  await linkBank(a.shopId, 'cola', bank);
  await linkBank(b.shopId, 'c1', bank);
  await linkBank(b.shopId, 'c24', bank);
  await linkBank(c.shopId, 'box', bank);
  await linkBank(d.shopId, 'kg', bank);
  await setFlag('all', 'market.product_details', true);
  try {
    const r = await h.get(`/api/market/same/${bank}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    const by = new Map(r.body.offers.map((o) => [`${o.shop.slug}/${o.id}`, o]));
    assert.equal(by.size, 5, JSON.stringify(r.body.offers.map((o) => o.id)));

    assert.deepEqual(by.get(`${a.slug}/cola`).perUnit, { base: 'piece', priceMinor: 2000 });
    assert.deepEqual(by.get(`${b.slug}/c24`).perUnit, { base: 'piece', priceMinor: 1875 }, '۴۵۰ ÷ ۲۴');
    assert.equal(by.get(`${c.slug}/box`).perUnit, null, 'تعدادِ داخلِ «کارتن» روشن نیست');
    assert.deepEqual(by.get(`${d.slug}/kg`).perUnit, { base: 'kg', priceMinor: 9000 });

    assert.equal(by.get(`${b.slug}/c24`).cheapest, true);
    assert.equal(by.get(`${a.slug}/cola`).cheapest, false);
    assert.equal(by.get(`${b.slug}/c1`).cheapest, false);
    assert.equal(by.get(`${c.slug}/box`).cheapest, false, 'قیمتِ خامِ کمتر ولی ناهم‌سنج ⇒ هرگز ارزان‌ترین');
    assert.equal(by.get(`${d.slug}/kg`).cheapest, false, 'تنها پیشنهادِ کیلویی ⇒ مقایسه‌ای نیست');
    assert.deepEqual(r.body.compare, [{ base: 'piece', lowestMinor: 1875, count: 3 }]);

    //  درونِ یک دکان: به قیمتِ یک دانه (کارتن ۱۸٫۷۵ پیش از دانهٔ ۲۰)، نه قیمتِ خام (۴۵۰ > ۲۰)
    const inB = r.body.offers.filter((o) => o.shop.slug === b.slug).map((o) => o.id);
    assert.deepEqual(inB, ['c24', 'c1']);

    //  «ارزان‌ترین» روی همهٔ پیشنهادها حساب می‌شود، نه فقط صفحه
    const one1 = await h.get(`/api/market/same/${bank}?limit=1`);
    assert.equal(one1.body.offers.length, 1);
    assert.deepEqual(one1.body.compare, [{ base: 'piece', lowestMinor: 1875, count: 3 }]);
    const cheapestHere = one1.body.offers[0];
    assert.equal(cheapestHere.cheapest, cheapestHere.perUnit !== null && cheapestHere.perUnit.priceMinor === 1875);

    //  دکانِ همین صفحه بیرون ⇒ مقایسه میانِ بقیه
    const ex = await h.get(`/api/market/same/${bank}?exclude=${b.slug}`);
    assert.deepEqual(ex.body.compare, [], 'یک دانه‌ای (الف) و یک کیلویی (د) ⇒ هیچ گروهِ دوتایی');
    assert.equal(ex.body.offers.some((o) => o.cheapest), false);

    //  بی پیشنهاد ⇒ همان شکل
    assert.deepEqual((await h.get('/api/market/same/nothing-here')).body, { bankProductId: 'nothing-here', offers: [], compare: [] });
  } finally {
    await setFlag('all', 'market.product_details', false);
  }
});

test('S70: اندازهٔ واحدِ بی عدد از بسته‌ها و واحدِ فروشِ دوم؛ بسته‌ها در پاسخ نیستند', async () => {
  const bank = `bp-pk-${uniq()}`;
  const e = await liveStore('شیشه‌ای', [['jar', {
    name: 'مربا شیشه', unit: 'شیشه', salePrice: 25,
    packs: [{ name: 'کارتن ۱۲ عددی', sizeMilli: 12000, barcodes: ['6260000000999'], priceMinor: 27000 }],
  }]]);
  const f = await liveStore('بسته‌ای', [['pk', { name: 'مربا بسته', unit: 'بسته', salePrice: 180, saleUnit: 'عدد', unitFactor: 0.1 }]]);
  const g = await liveStore('کارتنِ مبهم', [['ct', { name: 'مربا کارتن', unit: 'کارتن', salePrice: 200, saleUnit: 'عدد', unitFactor: 0.0417 }]]);
  await linkBank(e.shopId, 'jar', bank);
  await linkBank(f.shopId, 'pk', bank);
  await linkBank(g.shopId, 'ct', bank);
  await setFlag('all', 'market.product_details', true);
  try {
    const r = await h.get(`/api/market/same/${bank}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    const by = new Map(r.body.offers.map((o) => [o.id, o]));
    assert.deepEqual(by.get('jar').perUnit, { base: 'piece', priceMinor: 2500 }, 'کارتنِ ۱۲ عددی = ۱۲ شیشه ⇒ شیشه یک دانه');
    assert.deepEqual(by.get('pk').perUnit, { base: 'piece', priceMinor: 1800 }, 'یک عدد = ۰٫۱ بسته ⇒ ۱۸۰ ÷ ۱۰');
    assert.equal(by.get('ct').perUnit, null, '۱ ÷ ۰٫۰۴۲ دانهٔ درست نیست ⇒ هیچ');
    assert.equal(by.get('pk').cheapest, true);
    assert.equal(by.get('jar').cheapest, false);
    assert.equal(by.get('ct').cheapest, false);
    assert.deepEqual(r.body.compare, [{ base: 'piece', lowestMinor: 1800, count: 2 }]);
    const text = JSON.stringify(r.body);
    for (const leak of ['packs', '6260000000999', '27000', 'sizeMilli']) assert.ok(!text.includes(leak), `«${leak}» در پاسخ نیست`);
  } finally {
    await setFlag('all', 'market.product_details', false);
  }
});
