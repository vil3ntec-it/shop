'use strict';
/**
 * ویترین — جست‌وجوی پیش‌بینی‌گر و «چیزی پیدا نشد» ِ مفید (WEB-05، WEB-06؛ §۳.۱۳).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `shop.storefront.search` خاموش (پیش‌فرض) ⇒ پاسخِ `/products` همان قبلی:
 *      فقط `items` و `nextCursor`، بی رقمِ فارسی، بی پرش، بی غلطِ تایپی.
 *   ۲) روشن: رقمِ فارسی/عربی در پرس‌وجو و نامِ کالا یکی؛ نامِ دسته ⇒ همان دسته (`jump`)؛
 *      غلطِ تایپی با بودجهٔ هستهٔ مشترکِ متن (≤۳ حرف هیچ)؛ بی هیچ ⇒ `suggest` (دستهٔ
 *      نزدیک، پرفروش/تازهٔ واقعی، تلفنِ ویترین).
 *   ۳) ⛔ غلطِ تایپی هرگز کالای منتشرنشده، ناموجود (WEB-09) یا کالای دکانِ دیگر نمی‌آورد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const search = require('../src/lib/storefront-search');

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
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields, table = 'products') => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type: 'insert', fields });

const PRODUCTS = {
  rice: { name: 'برنج سیلا', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
  oil: { name: 'روغن 5 لیتری', unit: 'بوتل', salePrice: 300, category: 'خوراکی' },
  milk: { name: 'شیر ۱ لیتری', unit: 'پاکت', salePrice: 60, category: 'لبنیات' },
  salt: { name: 'نمک', unit: 'بسته', salePrice: 20, category: 'خوراکی' },
  shampoo: { name: 'شامپو', unit: 'دانه', salePrice: 150, category: 'لوازم بهداشتی' },
  soap: { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'لوازم بهداشتی' },
};

/** دکانِ روشن با ویترین؛ همهٔ کالاها جز «صابون» منتشرشده. */
async function liveStore(label, products = PRODUCTS) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `s-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000002',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, Object.entries(products).map(([id, f]) => op(id, f)));
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of Object.keys(products).filter((x) => x !== 'soap')) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug };
}

const find = async (s, q, extra = '') => {
  const r = await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent(q)}${extra}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
};
const ids = (b) => b.items.map((p) => p.id).sort();

test('⛔ پرچمِ خاموش: پاسخِ جست‌وجو همان قبلی — بی رقمِ فارسی، بی پرش، بی غلطِ تایپی، بی کلیدِ تازه', async () => {
  const s = await liveStore('جست‌وجو-خاموش');
  for (const q of ['۵ لیتری', 'خوراکی', 'برنچ', 'چیزی‌که‌نیست']) {
    const b = await find(s, q);
    assert.deepEqual(Object.keys(b).sort(), ['items', 'nextCursor'], q);
    assert.deepEqual(b.items, [], q);
  }
  //  جست‌وجوی امروز همان کار را می‌کند
  assert.deepEqual(ids(await find(s, 'روغن')), ['oil']);
});

test('WEB-05: رقمِ فارسی و عربی در پرس‌وجو و نامِ کالا یکی است', async () => {
  const s = await liveStore('جست‌وجو-رقم');
  await setFlag(s.shopId, search.SMART_FLAG, true);
  for (const [q, want] of [['۵ لیتری', ['oil']], ['٥ لیتری', ['oil']], ['5 لیتری', ['oil']], ['1 لیتری', ['milk']], ['۱ لیتری', ['milk']]]) {
    const b = await find(s, q);
    assert.deepEqual(ids(b), want, q);
    assert.equal(b.match, 'exact', q);
  }
  //  نرمال‌سازیِ امروز (ي/ك، نیم‌فاصله) هم همان می‌ماند
  assert.deepEqual(ids(await find(s, 'شير')), ['milk']);
});

test('WEB-05: پرس‌وجو همان نامِ دسته ⇒ همان دسته (jump)، با نیم‌فاصله و ي/ك هم', async () => {
  const s = await liveStore('جست‌وجو-دسته');
  await setFlag(s.shopId, search.SMART_FLAG, true);
  const b = await find(s, 'خوراکی');
  assert.equal(b.match, 'category');
  assert.deepEqual(b.jump, { category: 'خوراکی' });
  assert.deepEqual(ids(b), ['oil', 'rice', 'salt'], 'نامِ هیچ‌کدام «خوراکی» ندارد؛ نتیجه همان دسته است');
  const z = await find(s, 'لوازم‌ بهداشتي');
  assert.deepEqual(z.jump, { category: 'لوازم بهداشتی' });
  assert.deepEqual(ids(z), ['shampoo'], 'صابونِ منتشرنشده در دسته هم نیست');
  //  پارهٔ نامِ دسته پرش نیست
  assert.equal((await find(s, 'لوازم')).jump, undefined);
});

test('WEB-05: غلطِ تایپی با بودجهٔ هستهٔ مشترک — ۴ حرف یک غلط، ۳ حرف هیچ', async () => {
  const s = await liveStore('جست‌وجو-غلط');
  await setFlag(s.shopId, search.SMART_FLAG, true);
  const b = await find(s, 'برنچ');
  assert.equal(b.match, 'fuzzy');
  assert.deepEqual(ids(b), ['rice']);
  assert.equal(b.nextCursor, null);
  const two = await find(s, 'شامپوو سیلا');
  assert.equal(two.match, 'none', 'هر کلمه باید جور باشد');
  //  ۳ حرف: بی بخشش (نمگ ≠ نمک)
  const short = await find(s, 'نمگ');
  assert.equal(short.match, 'none');
  assert.deepEqual(short.items, []);
});

test('WEB-06: «چیزی پیدا نشد» ⇒ دستهٔ نزدیک، تازه‌های واقعی و تلفنِ ویترین؛ هیچ کالای ساختگی', async () => {
  const s = await liveStore('جست‌وجو-هیچ');
  await setFlag(s.shopId, search.SMART_FLAG, true);
  const b = await find(s, 'خوراک');
  assert.equal(b.match, 'none');
  assert.deepEqual(b.items, []);
  assert.deepEqual(b.suggest.categories, ['خوراکی']);
  assert.equal(b.suggest.picksKind, 'newArrivals', 'بی سفارش ⇒ «پرفروش» ِ ساختگی نیست');
  assert.ok(b.suggest.picks.length > 0 && b.suggest.picks.every((p) => p.id !== 'soap'), 'منتشرنشده پیشنهاد نمی‌شود');
  assert.equal(b.suggest.contactPhone, '+93700000002');
  const none = await find(s, 'zzzzqqqq');
  assert.deepEqual(none.suggest.categories, []);
});

test('⛔ غلطِ تایپی هرگز منتشرنشده، ناموجود (WEB-09) یا کالای دکانِ دیگر نمی‌آورد', async () => {
  const a = await liveStore('جست‌وجو-الف');
  const b = await liveStore('جست‌وجو-ب', { kiwi: { name: 'کیوی تازه', unit: 'کیلو', salePrice: 90, category: 'میوه' } });
  await setFlag(a.shopId, search.SMART_FLAG, true);
  await setFlag(b.shopId, search.SMART_FLAG, true);
  assert.equal((await find(a, 'کیوو')).match, 'none', 'کالای دکانِ دیگر');
  assert.deepEqual(ids(await find(b, 'کیوو')), ['kiwi']);
  assert.equal((await find(a, 'صابوت')).match, 'none', 'صابونِ منتشرنشده');
  //  کاتالوگ روشن و فقط برنج در انبار ⇒ روغنِ ناموجود با غلطِ تایپی پیشنهاد نمی‌شود
  await setFlag(a.shopId, 'shop.catalog', true);
  const w = await push(a.token, [op(`we-${uniq()}`, {
    productId: 'rice', units: 10, cartons: 0, perCarton: 0, unit: 'کیلو', price: 100, createdAt: Date.now(),
  }, 'warehouseEntries')]);
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal((await find(a, 'روغم')).match, 'none', 'ناموجود');
  const exact = await find(a, 'روغن');
  assert.deepEqual(ids(exact), ['oil'], 'جست‌وجوی دقیق ناموجود را با نشانش نشان می‌دهد');
  assert.equal(exact.items[0].stock.status, 'out_of_stock');
  assert.deepEqual(ids(await find(a, 'برنچ')), ['rice']);
});

test('سقفِ اسکن و ترتیب: fuzzyPick بیش از limit نمی‌دهد و ترتیبِ ورودی را نگه می‌دارد', () => {
  assert.ok(search.FUZZY_SCAN > 0 && search.FUZZY_SCAN <= 1000);
  const rows = ['برنج الف', 'برنج ب', 'برنچ ج', 'نمک'].map((name, i) => ({ id: String(i), name }));
  assert.deepEqual(search.fuzzyPick(rows, 'برنج', 2).map((r) => r.id), ['0', '1']);
  assert.equal(search.categoryOf('  خوراكي ', ['خوراکی', 'لبنیات']), 'خوراکی');
  assert.equal(search.categoryOf('', ['خوراکی']), '');
  assert.equal(search.fuzzyWorthy('نمگ'), false);
  assert.equal(search.fuzzyWorthy('نمگ برنچ'), true);
});
