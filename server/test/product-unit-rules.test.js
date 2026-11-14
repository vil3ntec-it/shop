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
  //  ۲-۱۶ (R2 الف): گامِ دومِ «ضریبِ ناجور بی واحدِ فروشِ دوم، سپس فقط واحدِ فروش» — ضریبِ خوابیده با روشن شدنِ واحدِ فروش تازه است
  const dormant = { ...tea, saleUnit: '', unitFactor: 1 / 24 };
  assert.equal(await R.reasonOf({ saleUnit: 'کارتن' }, dormant, none), R.INEXACT, 'واحدِ فروش روی ضریبِ ناجورِ خوابیده');
  assert.equal(await R.reasonOf({ saleUnit: 'کارتن' }, { ...dormant, unitFactor: 24 }, none), '', 'ضریبِ خوابیدهٔ دقیق');
  assert.equal(await R.reasonOf({ saleUnit: 'کارتن' }, { ...dormant, unitFactor: undefined }, none), '', 'بی ضریبِ ذخیره همان امروز');
  assert.equal(await R.reasonOf({ saleUnit: 'کارتن' }, { ...dormant, unitFactor: 0 }, none), R.INEXACT, 'ضریبِ خوابیدهٔ صفر');
  assert.equal(await R.reasonOf({ saleUnit: 'بسته' }, { ...tea, unitFactor: 1 / 12 }, none), '', 'واحدِ فروشِ روشن با ضریبِ کهنهٔ دست‌نخورده');
  assert.equal(await R.reasonOf({ saleUnit: 'کارتن', unitFactor: 1 / 24 }, dormant, none), R.INEXACT, 'همان ضریبِ خوابیده در همان op');
  //  واحدِ پایه: فقط کالای گردش‌دار، و فقط تغییرِ واقعی
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, tea, moved), R.LOCKED);
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, tea, none), '', 'کالای بی گردش');
  assert.equal(await R.reasonOf({ unit: ' دانه ' }, tea, moved), '', 'فاصله');
  assert.equal(await R.reasonOf({ unit: 'كيلو' }, { ...tea, unit: 'کیلو' }, moved), '', 'ي/ك');
  //  ۲-۱۶ (R2 الف): خالی کردنِ واحدِ کالای گردش‌دار هم تغییرِ واحد است (گامِ اولِ «خالی، سپس واحدِ تازه»)؛ بی گردش آزاد
  assert.equal(await R.reasonOf({ unit: '' }, tea, moved), R.LOCKED, 'واحدِ خالیِ op روی کالای گردش‌دار');
  assert.equal(await R.reasonOf({ unit: null }, tea, moved), R.LOCKED, 'null همان خالی');
  assert.equal(await R.reasonOf({ unit: '  ' }, tea, moved), R.LOCKED);
  assert.equal(await R.reasonOf({ unit: '' }, tea, none), '', 'واحدِ خالیِ کالای بی گردش');
  assert.equal(await R.reasonOf({ name: 'چای' }, tea, moved), '', 'بی کلیدِ واحد = همان واحد');
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, { ...tea, unit: '' }, moved), '', 'واحدِ خالیِ کهنه (پیش از قاعده) تازه نوشته می‌شود');
  assert.equal(await R.reasonOf({ unit: 'کیلو' }, null, moved), '', 'کالای تازه');
  //  نخست واحد، سپس ضریب؛ پرسشِ گردش فقط وقتی واحد واقعاً عوض شود
  assert.equal(await R.reasonOf({ unit: 'کیلو', unitFactor: 1 / 24 }, tea, moved), R.LOCKED);
  let asked = 0;
  await R.reasonOf({ unit: 'دانه', unitFactor: 12 }, tea, async () => { asked++; return true; });
  assert.equal(asked, 0);
  assert.equal(R.touches({ name: 'x', salePrice: 10 }), false, 'ویرایشِ بی واحد/ضریب هیچ پرسشی ندارد');
  //  ۲-۱۶: واحدِ خالی و واحدِ فروشِ دوم هم سنجیده می‌شوند (پیش‌تر `unit: '  '` بی پرسش می‌گذشت)
  assert.equal(R.touches({ unitFactor: null, unit: '  ' }), true);
  assert.equal(R.touches({ saleUnit: 'کارتن' }), true);
  assert.equal(R.touches({ saleUnit: '', unitFactor: null }), false);
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

test('۲-۱۶ (R2 الف) v1 و v3، روشن: دو راهِ دوگامی بسته — «واحدِ خالی سپس واحدِ تازه» و «ضریبِ خوابیده سپس واحدِ فروش»', async () => {
  await setFlag(shopId, true);
  assert.deepEqual(outcome(await push1('owner', [
    v1('products', 'r2a', tea1()),
    v1('warehouseEntries', 'r2a-in', { productId: 'r2a', units: 48, price: 1 }),
    v1('products', 'r2b', tea1({ saleUnit: '' })),
  ])), ['applied', 'applied', 'applied']);
  //  یک دسته و دو دسته
  assert.deepEqual(outcome(await push1('manager', [
    v1('products', 'r2a', { unit: '' }, 'update'),
    v1('products', 'r2a', { unit: 'گرام' }, 'update'),
    v1('products', 'r2b', { unitFactor: 1 / 24 }, 'update'),
    v1('products', 'r2b', { saleUnit: 'کارتن' }, 'update'),
  ])), [R.LOCKED, R.LOCKED, 'applied', R.INEXACT]);
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'r2a', { unit: null }, 'update')])), [R.LOCKED]);
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'r2b', { saleUnit: 'کارتن' }, 'update')])), [R.INEXACT]);
  assert.equal((await row1('r2a')).unit, 'دانه');
  assert.equal((await row1('r2b')).saleUnit, '');
  //  درست کردنِ ضریب، سپس واحدِ فروش ⇒ آزاد
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'r2b', { unitFactor: 24 }, 'update'), v1('products', 'r2b', { saleUnit: 'کارتن' }, 'update')])),
    ['applied', 'applied']);
  //  v3: همان دو راه
  assert.deepEqual(outcome(await push3('owner', [
    v3('products', 'r2c', tea3()),
    v3('warehouseEntries', 'r2c-in', { productId: 'r2c', unitsMilli: 48_000, priceMinor: 100 }),
    v3('products', 'r2d', tea3({ saleUnit: '' })),
  ])), ['applied', 'applied', 'applied']);
  assert.deepEqual(outcome(await push3('manager', [
    v3('products', 'r2c', { unit: '' }, 'update'),
    v3('products', 'r2d', { unitFactor: 1 / 24 }, 'update'),
    v3('products', 'r2d', { saleUnit: 'کارتن' }, 'update'),
  ])), [R.LOCKED, 'applied', R.INEXACT]);
  assert.equal((await row3('r2c')).unit, 'دانه');
  //  پرچمِ خاموش ⇒ همان امروز
  await setFlag(shopId, false);
  assert.deepEqual(outcome(await push1('owner', [v1('products', 'r2a', { unit: '' }, 'update')])), ['applied']);
});

test('v1Guard ِ مستقیم، روشن: بدنهٔ بی فهرستِ op (نبود، رشته، شیء، خالی) ⇒ بی خطا و بی دست‌کاریِ بدنه و پاسخ', async () => {
  await setFlag(shopId, true);
  for (const ops of [undefined, null, 'ops', { length: 1e9 }, []]) {
    const req = { sync: { accountId: shopId }, body: { ops } };
    const res = { json: () => 'اصل' };
    const json = res.json;
    await R.v1Guard(req, res);
    assert.equal(req.body.ops, ops, JSON.stringify(ops));
    assert.equal(res.json, json, 'پاسخ دست نمی‌خورد');
  }
});

test('v1OpOf همان پذیرشِ validateOp ِ v1 را دارد (شکل‌ها، نام‌های دیگر، فیلدِ ناجور، دلتا، اندازه، hash)', () => {
  const ok = (extra = {}) => ({ op_id: 'op-1', table: 'products', row_id: 'p1', type: 'update', fields: { unit: 'دانه' }, ...extra });
  const fields = { unit: 'کیلو', unitFactor: 2 };
  const cases = [
    ok(), ok({ type: 'insert' }), ok({ type: 'delete', fields: undefined }), ok({ type: 'delete', fields: {} }), ok({ type: 'delete', fields: { a: 1 } }),
    { opId: 'op-2', tableName: 'Products', rowId: 'p2', opType: 'insert', fields },
    { op_id: 'op-3', table_name: 'pro_ducts', id: 'p3', op_type: 'update', fields },
    ok({ op_id: 'bad id' }), ok({ op_id: 'x'.repeat(65) }), ok({ op_id: 7 }), ok({ row_id: '' }), ok({ row_id: 'r'.repeat(81) }), ok({ row_id: 5 }),
    ok({ type: 'upsert' }), ok({ table: 'nope' }), ok({ fields: null }), ok({ fields: [1] }), ok({ fields: 'x' }), ok({ type: 'insert', fields: {} }),
    ok({ fields: { $x: 1 } }), ok({ fields: { ['k'.repeat(65)]: 1 } }), ok({ fields: { '': 1 } }),
    ok({ fields: { stock: { $inc: 2 } } }), ok({ type: 'insert', fields: { stock: { $inc: 2 } } }), ok({ fields: { stock: { $set: 2 } } }),
    ok({ fields: { big: 'x'.repeat(syncV1.MAX_FIELDS_BYTES + 1) } }),
    ok({ hash: 'nope' }), ok({ hash: syncV1.hashOf({ table: 'products', row_id: 'p1', type: 'update', fields: { unit: 'دانه' } }) }),
    ok({ table: 'warehouseEntries', fields: { productId: 'p1', units: 1 } }),
    null, 'op', [ok()],
  ];
  for (const raw of cases) {
    const v = syncV1.validateOp('shop', raw);
    const o = R.v1OpOf(raw);
    assert.equal(!!o, v.ok, JSON.stringify(raw)?.slice(0, 120));
    if (v.ok) {
      assert.deepEqual([o.op_id, o.row_id, o.type, o.table], [v.op.op_id, v.op.row_id, v.op.type, v.op.table], JSON.stringify(raw));
      assert.deepEqual(o.fields, v.op.type === 'delete' ? null : v.op.fields);
    }
  }
});

test('v1Guard ِ مستقیم، روشن: op ِ ردشده از **همان** فهرستِ درخواست برداشته می‌شود (در جا، نه req.body.ops = …) و پاسخ سرِ جایش', async () => {
  await setFlag(shopId, true);
  const a = v1('products', 'ip-a', tea1());
  const bad = v1('products', 'ip-b', tea1({ unitFactor: 1 / 7 }));
  const c = v1('suppliers', 'ip-c', { name: 'تأمین' });
  const ops = [a, bad, c];
  const req = { sync: { accountId: shopId }, body: { ops } };
  let sent = null;
  const res = { json: (b) => { sent = b; return b; } };
  await R.v1Guard(req, res);
  assert.equal(req.body.ops, ops, 'همان آرایه');
  assert.deepEqual(req.body.ops, [a, c]);
  res.json({ ok: true, results: [{ op_id: a.op_id, status: 'applied' }, { op_id: c.op_id, status: 'applied' }] });
  assert.deepEqual(sent.results.map((r) => [r.op_id, r.status, r.reason]), [
    [a.op_id, 'applied', undefined], [bad.op_id, 'rejected', R.INEXACT], [c.op_id, 'applied', undefined],
  ]);
});

// ---------------------------------------------------------------- ۳: راهِ قدیم (`/api/sync`، F0-02؛ د۱۸)

const pushL = (who, changes) => h.api('POST', '/api/sync', {
  token: T[who].accessToken, body: { deviceId: `pul-${who}`, operationId: oid(), changes },
});
const lg = (collection, id, data, extra = {}) => ({ collection, id, updatedAt: Date.now() + (++tick), deleted: false, data, ...extra });
const rowL = async (id) => (await one('SELECT data FROM products WHERE shop_id=$1 AND id=$2', [shopId, id]))?.data;
const legacyOut = (r) => {
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
};

test('راهِ قدیم، پرچمِ خاموش ⇒ همان امروز (برابری: ضریبِ ناجور و تغییرِ واحدِ گردش‌دار می‌نشینند)', async () => {
  await setFlag(shopId, false);
  const a = legacyOut(await pushL('owner', [
    lg('products', 'lgoff', tea1({ unitFactor: 1 / 24 })),
    lg('warehouseEntries', 'lgoff-in', { productId: 'lgoff', units: 10, price: 1 }),
  ]));
  assert.equal(a.applied, 2);
  assert.deepEqual(a.conflicts, []);
  const b = legacyOut(await pushL('owner', [lg('products', 'lgoff', tea1({ unit: 'کیلو', unitFactor: 1 / 24 }))]));
  assert.equal(b.applied, 1);
  assert.deepEqual(b.conflicts, []);
  assert.equal((await rowL('lgoff')).unit, 'کیلو');
});

test('راهِ قدیم، روشن: ردیفِ کاملِ کالا — ضریبِ تازهٔ ناجور و واحدِ گردش‌دار ⇒ تعارض با دادهٔ سرور، بقیهٔ دسته می‌نشیند', async () => {
  await setFlag(shopId, false);
  legacyOut(await pushL('owner', [
    lg('products', 'lgold', tea1({ unitFactor: 1 / 12 })),
    lg('products', 'lgmv', tea1()),
    lg('warehouseEntries', 'lgmv-in', { productId: 'lgmv', units: 48, price: 1 }),
    lg('products', 'lgfree', tea1()),
  ]));
  await setFlag(shopId, true);
  const out = legacyOut(await pushL('manager', [
    lg('products', 'lgnew', tea1({ unitFactor: 1 / 24 })),
    lg('products', 'lgold', tea1({ name: 'چای سبز', unitFactor: 1 / 12 })),
    lg('products', 'lgmv', tea1({ unit: 'کیلو' })),
    lg('products', 'lgfree', tea1({ unit: 'کیلو' })),
    lg('suppliers', 'lgsup', { name: 'تأمین' }),
  ]));
  assert.equal(out.applied, 3);
  assert.deepEqual(out.conflicts.map((c) => [c.id, c.reason]), [['lgnew', R.INEXACT], ['lgmv', R.LOCKED]]);
  const mv = out.conflicts.find((c) => c.id === 'lgmv');
  assert.equal(mv.collection, 'products');
  assert.equal(mv.data.unit, 'دانه', 'دادهٔ سرور همراهِ تعارض تا برنامهٔ کهنه برگردد');
  assert.equal(mv.deleted, false);
  assert.equal(out.conflicts[0].data, null, 'کالای تازهٔ ردشده: سرور چیزی ندارد');
  assert.equal(await rowL('lgnew'), undefined);
  assert.equal((await rowL('lgold')).name, 'چای سبز');
  assert.equal((await rowL('lgmv')).unit, 'دانه');
  assert.equal((await rowL('lgfree')).unit, 'کیلو');
  //  صاحب هم؛ واحدِ خالی (۲-۱۶) هم تغییر است
  const own = legacyOut(await pushL('owner', [lg('products', 'lgmv', tea1({ unit: '' }))]));
  assert.deepEqual(own.conflicts.map((c) => [c.id, c.reason]), [['lgmv', R.LOCKED]]);
  assert.equal((await rowL('lgmv')).unit, 'دانه');
});

test('راهِ قدیم، روشن: گردشِ همان دسته (پیش از ردیفِ کالا) و گردشِ دفترِ v1 هم شمرده می‌شود؛ حذف و همان واحد آزاد', async () => {
  await setFlag(shopId, true);
  const out = legacyOut(await pushL('owner', [
    lg('products', 'lgb1', tea1()),
    lg('saleItems', 'lgb1-s', { productId: 'lgb1', qty: 1 }),
    lg('products', 'lgb1', tea1({ unit: 'کیلو' })),
    lg('products', 'lgb2', tea1({ unit: ' دانه ', name: 'چای ۲' })),
  ]));
  assert.deepEqual(out.conflicts.map((c) => [c.id, c.reason]), [['lgb1', R.LOCKED]]);
  assert.equal((await rowL('lgb1')).unit, 'دانه');
  assert.equal((await rowL('lgb2')).name, 'چای ۲');
  //  کالایی که فقط در دفترِ v1 گردش دارد
  assert.deepEqual(outcome(await push1('owner', [v1('warehouseEntries', 'lgb2-v1', { productId: 'lgb2', units: 3, price: 1 })])), ['applied']);
  const v = legacyOut(await pushL('owner', [lg('products', 'lgb2', tea1({ unit: 'کیلو' }))]));
  assert.deepEqual(v.conflicts.map((c) => [c.id, c.reason]), [['lgb2', R.LOCKED]]);
  //  حذفِ کالا و جدولِ دیگر بی پرسش
  const d = legacyOut(await pushL('owner', [lg('products', 'lgb2', {}, { deleted: true }), lg('expenses', 'lge', { unit: 'x' })]));
  assert.deepEqual(d.conflicts, []);
  assert.equal(d.applied, 2);
});

test('راهِ قدیم، روشن: پاسخِ تکراری (همان operationId) همان تعارض را دارد', async () => {
  await setFlag(shopId, true);
  const body = { deviceId: 'pul-rep', operationId: oid(), changes: [lg('products', 'lgrep', tea1({ unitFactor: 1 / 9 }))] };
  const a = legacyOut(await h.api('POST', '/api/sync', { token: T.owner.accessToken, body }));
  const b = legacyOut(await h.api('POST', '/api/sync', { token: T.owner.accessToken, body }));
  assert.deepEqual(a.conflicts.map((c) => c.reason), [R.INEXACT]);
  assert.equal(b.replayed, true);
  assert.deepEqual(b.conflicts.map((c) => c.reason), [R.INEXACT]);
});
