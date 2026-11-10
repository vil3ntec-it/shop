'use strict';
/**
 * PRD-07 — قاعدهٔ واحدِ کالا در سرور (`src/lib/product-unit-rules.js`)، همتای `data/ProductDraftRules` ِ برنامه:
 *  ۱) بردار: `exactMilli` = `UnitSale.exactMilli` (همان بردارهای `UnitFactorTest`)، و قاعده‌ها روی ردیفِ ذخیره.
 *  ۲) push ِ واقعیِ v3 و v1: پرچمِ خاموش ⇒ همان امروز؛ روشن ⇒ ضریبِ تازهٔ ناجور و تغییرِ واحدِ پایهٔ کالای گردش‌دار
 *     رد (صاحب هم)، ضریبِ کهنهٔ دست‌نخورده و کالای بی گردش آزاد؛ در v1 گردش و حالِ کالا از opهای پیشینِ همان دسته هم.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const live = require('../src/lib/live-config');
const R = require('../src/lib/product-unit-rules');

// ---------------------------------------------------------------- ۱: بردار

test('exactMilli همان UnitSale.exactMilli: فقط ضریبِ نوشته به هزارم', () => {
  for (const [f, m] of [[0.001, 1], [0.007, 7], [0.042, 42], [2.5, 2500], [12, 12000], [24, 24000], [1000, 1_000_000]]) {
    assert.equal(R.exactMilli(f), m, String(f));
  }
  //  «۱ دانه = ۱/۲۴ کارتن» یا نیم‌گرمی از کیلو در دفترِ هزارمی دقیق نمی‌ماند؛ نامثبت و بیرون از سقف هم
  for (const bad of [1 / 24, 0.0417, 0.0005, 1 / 3, 0, -2, NaN, Infinity, 1_000_001]) assert.equal(R.exactMilli(bad), null, String(bad));
});

test('قاعده‌ها روی ردیفِ ذخیره (همان ترتیبِ ProductDraftRules)', async () => {
  const none = async () => false;
  const moved = async () => true;
  const tea = { name: 'چای', unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24 };
  //  ضریب: تازهٔ ناجور رد؛ کهنهٔ ناجورِ دست‌نخورده، دقیق، و بی واحدِ فروشِ دوم آزاد
  assert.equal(await R.reasonOf({ saleUnit: 'کارتن', unitFactor: 1 / 24 }, null, none), R.INEXACT, 'کالای تازه');
  assert.equal(await R.reasonOf({ unitFactor: 0.0417 }, tea, none), R.INEXACT, 'واحدِ فروشِ دوم از ردیفِ ذخیره');
  assert.equal(await R.reasonOf({ unitFactor: '0.0005' }, tea, none), R.INEXACT, 'عددِ متنی همان عدد است (`toMilli` ِ کاتالوگ)');
  assert.equal(await R.reasonOf({ unitFactor: 0 }, tea, none), R.INEXACT);
  assert.equal(await R.reasonOf({ unitFactor: 1 / 12, name: 'چای سبز' }, { ...tea, unitFactor: 1 / 12 }, none), '', 'ضریبِ کهنهٔ دست‌نخورده');
  assert.equal(await R.reasonOf({ unitFactor: 12 }, tea, none), '');
  assert.equal(await R.reasonOf({ unitFactor: null }, tea, none), '', 'null = همان ضریبِ ذخیره');
  assert.equal(await R.reasonOf({ saleUnit: '', unitFactor: 1 / 24 }, tea, none), '', 'واحدِ فروشِ دوم پاک شد');
  assert.equal(await R.reasonOf({ unitFactor: 1 / 24 }, { ...tea, saleUnit: '' }, none), '', 'کالای بی واحدِ فروشِ دوم');
  assert.equal(await R.reasonOf({ name: 'چای', unitFactor: 1 / 3 }, null, none), '', 'کالای تازهٔ بی واحدِ فروشِ دوم');
  //  واحدِ پایه: فقط کالای گردش‌دار، و فقط تغییرِ واقعی
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, tea, moved), R.LOCKED);
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, tea, none), '', 'کالای بی گردش');
  assert.equal(await R.reasonOf({ unit: ' دانه ' }, tea, moved), '', 'فاصله');
  assert.equal(await R.reasonOf({ unit: 'كيلو' }, { ...tea, unit: 'کیلو' }, moved), '', 'ي/ك');
  assert.equal(await R.reasonOf({ unit: '' }, tea, moved), '', 'واحدِ خالیِ op');
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, { ...tea, unit: '' }, moved), '', 'واحدِ خالیِ کهنه تازه نوشته می‌شود');
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, null, moved), '', 'کالای تازه');
  //  نخست واحد، سپس ضریب؛ پرسشِ گردش فقط وقتی واحد واقعاً عوض شود
  assert.equal(await R.reasonOf({ unit: 'کیلو', unitFactor: 1 / 24 }, tea, moved), R.LOCKED);
  let asked = 0;
  await R.reasonOf({ unit: 'دانه', unitFactor: 12 }, tea, async () => { asked++; return true; });
  assert.equal(asked, 0);
  assert.equal(R.touches({ name: 'x', salePrice: 10 }), false, 'ویرایشِ بی واحد/ضریب هیچ پرسشی ندارد');
  assert.equal(R.touches({ unitFactor: null, unit: '  ' }), false);
  assert.equal(R.touches({ unit: 'دانه' }), true);
});

// ---------------------------------------------------------------- ۲: push ِ واقعی

let shopId = '';
const T = {};
let seq = 0;
const oid = () => `01JPU${String(++seq).padStart(21, '0')}`;
let tick = 0;
const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'pos' });

const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `pu3-${who}`, protocol: 3, ops },
});
const push1 = (who, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].accessToken, body: { device_id: `pu1-${who}`, schema_version: 1, ops },
});
const v3 = (table, rowId, fields, kind = 'insert') => ({ op_id: oid(), hlc: H(), table, row_id: rowId, kind, fields });
const v1 = (table, rowId, fields, type = 'insert') => ({ op_id: oid(), table, row_id: rowId, type, ts: Date.now(), fields });
const outcome = (r) => {
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results.map((x) => x.reason || x.status);
};
const row3 = async (rowId) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id=$2`, [shopId, rowId]
))?.data;
const row1 = async (rowId) => (await one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products' AND row_id=$2`,
  [shopId, rowId]
))?.data;
const setFlag = (scope, value) => live.set(scope, R.FLAG, value);

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ واحدها', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ واحدها' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: T.owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const u = await h.newUser('مدیرِ دکانِ واحدها', 'shop');
  const joined = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
  assert.ok([200, 201].includes(joined.status), JSON.stringify(joined.body));
  T.manager = await h.signIn(u, 'shop');
});
test.after(async () => { await h.stop(); });

const tea3 = (extra = {}) => ({ name: 'چای', unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24, salePriceMinor: 5000, ...extra });
const tea1 = (extra = {}) => ({ name: 'چای', unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24, salePrice: 50, ...extra });

test('v3، پرچمِ خاموش (پیش‌فرض، و دکانِ false با all ِ روشن) ⇒ همان امروز', async () => {
  for (const [tag, setup] of [['a', null], ['b', async () => { await setFlag('all', true); await setFlag(shopId, false); }]]) {
    if (setup) await setup();
    const p = `off3${tag}`;
    assert.deepEqual(outcome(await push3('owner', [
      v3('products', p, tea3({ unitFactor: 1 / 24 })),
      v3('warehouseEntries', `${p}-in`, { productId: p, unitsMilli: 10_000, priceMinor: 100 }),
    ])), ['applied', 'applied']);
    assert.deepEqual(outcome(await push3('owner', [v3('products', p, { unit: 'کیلو' }, 'update')])), ['applied']);
    assert.equal((await row3(p)).unit, 'کیلو');
  }
  await live.remove('all', R.FLAG);
});

test('v3، روشن: ضریبِ تازهٔ ناجور رد؛ دقیق و کهنهٔ دست‌نخورده آزاد', async () => {
  await setFlag(shopId, false);
  //  ضریبِ کهنهٔ ناجور پیش از روشن شدن
  assert.deepEqual(outcome(await push3('owner', [v3('products', 'old3', tea3({ unitFactor: 1 / 12 }))])), ['applied']);
  await setFlag(shopId, true);
  assert.deepEqual(outcome(await push3('manager', [
    v3('products', 'new3', tea3({ unitFactor: 1 / 24 })),
    v3('products', 'ok3', tea3({ unitFactor: 0.5 })),
    v3('products', 'old3', { name: 'چای سبز', unitFactor: 1 / 12 }, 'update'),
    v3('products', 'old3', { unitFactor: 0.0417 }, 'update'),
  ])), [R.INEXACT, 'applied', 'applied', R.INEXACT]);
  assert.equal(await row3('new3'), undefined);
  assert.equal((await row3('old3')).name, 'چای سبز');
  assert.equal((await row3('old3')).unitFactor, 1 / 12);
  //  صاحب هم (قاعدهٔ داده است، نه مجوز)
  assert.deepEqual(outcome(await push3('owner', [v3('products', 'ok3', { unitFactor: 0.0005 }, 'update')])), [R.INEXACT]);
  assert.equal((await row3('ok3')).unitFactor, 0.5);
});

test('v3، روشن: واحدِ پایهٔ کالای گردش‌دار عوض نمی‌شود؛ بی گردش و همان واحد آزاد', async () => {
  await setFlag(shopId, true);
  assert.deepEqual(outcome(await push3('owner', [
    v3('products', 'mv3', tea3()),
    v3('products', 'free3', tea3()),
    v3('warehouseEntries', 'mv3-in', { productId: 'mv3', unitsMilli: 48_000, priceMinor: 100 }),
  ])), ['applied', 'applied', 'applied']);
  assert.deepEqual(outcome(await push3('owner', [
    v3('products', 'mv3', { unit: 'کیلو' }, 'update'),
    v3('products', 'mv3', { unit: ' دانه ', name: 'چای خشک' }, 'update'),
    v3('products', 'free3', { unit: 'کیلو' }, 'update'),
  ])), [R.LOCKED, 'applied', 'applied']);
  assert.equal((await row3('mv3')).unit, ' دانه ');
  assert.equal((await row3('free3')).unit, 'کیلو');
  //  فروش هم گردش است، در همان push پیش از تغییر
  assert.deepEqual(outcome(await push3('manager', [
    v3('saleItems', 'free3-si', { saleId: 'free3-s', productId: 'free3', quantityMilli: 1000, unitPriceMinor: 5000, totalPriceMinor: 5000 }),
    v3('products', 'free3', { unit: 'گرام' }, 'update'),
  ])), ['applied', R.LOCKED]);
  assert.equal((await row3('free3')).unit, 'کیلو');
});

test('v1، پرچمِ خاموش ⇒ همان امروز', async () => {
  await setFlag(shopId, false);
  assert.deepEqual(outcome(await push1('owner', [
    v1('products', 'off1', tea1({ unitFactor: 1 / 24 })),
    v1('warehouseEntries', 'off1-in', { productId: 'off1', units: 10, price: 1 }),
  ])), ['applied', 'applied']);
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'off1', { unit: 'کیلو' }, 'update')])), ['applied']);
  assert.equal((await row1('off1')).unit, 'کیلو');
});

test('v1، روشن: ضریبِ ناجور و واحدِ گردش‌دار رد، بقیهٔ دسته سرِ جایش', async () => {
  await setFlag(shopId, false);
  assert.deepEqual(outcome(await push1('owner', [
    v1('products', 'old1', tea1({ unitFactor: 1 / 12 })),
    v1('products', 'mv1', tea1()),
    v1('warehouseEntries', 'mv1-in', { productId: 'mv1', units: 48, price: 1 }),
    v1('products', 'free1', tea1()),
  ])), ['applied', 'applied', 'applied', 'applied']);
  await setFlag(shopId, true);
  assert.deepEqual(outcome(await push1('manager', [
    v1('products', 'new1', tea1({ unitFactor: 1 / 24 })),
    v1('products', 'old1', tea1({ name: 'چای سبز', unitFactor: 1 / 12 }), 'update'),
    v1('products', 'mv1', { unit: 'کیلو' }, 'update'),
    v1('products', 'mv1', { unit: 'دانه', unitFactor: 12 }, 'update'),
    v1('products', 'free1', { unit: 'کیلو' }, 'update'),
    v1('products', 'old1', { unitFactor: '0.0005' }, 'update'),
  ])), [R.INEXACT, 'applied', R.LOCKED, 'applied', 'applied', R.INEXACT]);
  assert.equal(await row1('new1'), undefined);
  assert.equal((await row1('old1')).name, 'چای سبز');
  assert.equal((await row1('old1')).unitFactor, 1 / 12);
  assert.equal((await row1('mv1')).unit, 'دانه');
  assert.equal((await row1('mv1')).unitFactor, 12);
  assert.equal((await row1('free1')).unit, 'کیلو');
  //  صاحب هم
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'mv1', { unit: 'کارتن' }, 'update')])), [R.LOCKED]);
});

test('v1، روشن: گردش و حالِ کالا از opهای پیشینِ همان دسته هم (یک دسته دورِ قاعده نمی‌زند)', async () => {
  await setFlag(shopId, true);
  assert.deepEqual(outcome(await push1('owner', [
    v1('products', 'b1', tea1()),
    v1('warehouseEntries', 'b1-in', { productId: 'b1', units: 5, price: 1 }),
    v1('products', 'b1', { unit: 'کیلو' }, 'update'),
    v1('products', 'b2', tea1({ saleUnit: '' })),
    v1('products', 'b2', { saleUnit: 'کارتن', unitFactor: 1 / 24 }, 'update'),
    //  ردشده حالِ کالا را عوض نمی‌کند: ضریبِ پسین با ضریبِ ذخیره (۲۴) سنجیده می‌شود
    v1('products', 'b3', tea1()),
    v1('products', 'b3', { unitFactor: 1 / 7 }, 'update'),
    v1('products', 'b3', { unitFactor: 24, name: 'چای ۲۴' }, 'update'),
  ])), ['applied', 'applied', R.LOCKED, 'applied', R.INEXACT, 'applied', R.INEXACT, 'applied']);
  assert.equal((await row1('b1')).unit, 'دانه');
  assert.equal((await row1('b2')).saleUnit, '');
  assert.equal((await row1('b3')).name, 'چای ۲۴');
  //  op ِ بی واحد/ضریب و جدولِ دیگر بی هیچ پرسشی
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'b1', { salePrice: 60 }, 'update'), v1('suppliers', 'sup1', { name: 'تأمین', unit: 'x' })])),
    ['applied', 'applied']);
});
