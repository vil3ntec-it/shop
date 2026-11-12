'use strict';
/**
 * پرچم‌های پیش‌فرض‌روشنِ `orders.flagOf` (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲، خواستِ صاحب‌کار «کلیدهای کم‌خطر را روشن کن»).
 *
 *   - هر ۱۷ کلید بی ردیف ⇒ روشن (بیرون و درونِ تراکنش)؛ کاتالوگِ پنل (`KNOWN`) همان پیش‌فرض را نشان می‌دهد.
 *   - `false` ِ صریحِ دکان یا `all` ⇒ خاموش؛ `true` ِ دکان بر `false` ِ `all` جلو؛ مقدارِ نادرست ⇒ همان پیش‌فرضِ روشن.
 *   - «غیرفعال کردنِ سرویس» ِ `shop_holds` ⇒ خاموش (فقط همان دکان).
 *   - کلیدِ بیرون از فهرست همان قاعدهٔ پیشین: بی ردیف خاموش، فقط `true` روشن.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, tx } = require('../src/db');
const orders = require('../src/lib/orders');
const { DEFAULT_ON, KEYS } = require('../src/lib/flag-defaults');

const LIST = [
  'shop.storefront.filters', 'shop.storefront.recent', 'shop.storefront.search', 'shop.storefront.search_core',
  'shop.storefront.gallery', 'shop.storefront.stock_list', 'shop.storefront.address_extra', 'shop.storefront.map_pin',
  'shop.orders.refund_tracking', 'shop.orders.refund_receipt', 'shop.orders.presence', 'shop.orders.online_report',
  'shop.branches_report', 'shop.delivery_route', 'shop.delivery_map', 'shop.orders.start_alert', 'shop.orders.stuck_alert',
];
//  بیرون از فهرست: پول، داده یا هزینه — همان پیش‌فرضِ خاموش
const OFF = ['shop.orders', 'shop.storefront', 'shop.orders.refunds', 'shop.storefront.alerts', 'shop.customer_notify'];

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function shop(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return made.body.shop.id;
}
const put = (scope, key, value) => query(
  'INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1) ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value',
  [String(scope), key, value],
);
const clear = (key) => query('DELETE FROM live_config WHERE key=$1', [key]);
const hold = (shopId, key) => query(
  "INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_at) VALUES ($1,$2,'service_disabled',$3,'آزمون','admin',1)",
  [newId('hold'), shopId, key],
);
//  درونِ تراکنش (`run` ِ اتصالِ جاری) همان پاسخ
const inTx = (shopId, key) => tx((c) => orders.flagOf(shopId, key, (sql, p) => c.query(sql, p)));

test('فهرست: همان ۱۷ کلید، فقط‌خواندنی، و کاتالوگِ پنل همان پیش‌فرض را دارد', () => {
  assert.deepEqual([...DEFAULT_ON].sort(), [...LIST].sort());
  assert.equal(DEFAULT_ON.size, 17);
  assert.deepEqual([...KEYS].sort(), [...LIST].sort());
  assert.ok(Object.isFrozen(KEYS) && Object.isFrozen(DEFAULT_ON));
  assert.throws(() => DEFAULT_ON.add('shop.orders'), TypeError);
  assert.throws(() => DEFAULT_ON.delete('shop.storefront.filters'), TypeError);
  assert.throws(() => DEFAULT_ON.clear(), TypeError);
  assert.ok(!DEFAULT_ON.has('shop.orders') && DEFAULT_ON.has('shop.orders.stuck_alert'));
  const { KNOWN } = require('../src/routes/admin-flags');
  for (const key of LIST) {
    const k = KNOWN.find((x) => x.key === key);
    assert.ok(k, `${key}: در کاتالوگ نیست`);
    assert.deepEqual([k.scope, k.default], ['shop', true], key);
  }
  for (const key of OFF) assert.equal(KNOWN.find((x) => x.key === key).default, false, key);
});

test('بی ردیف ⇒ روشن؛ false ِ صریحِ دکان یا all ⇒ خاموش؛ دکان بر all جلو؛ نگه‌داشتِ سرویس ⇒ خاموش', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  for (const key of LIST) {
    await clear(key);
    assert.equal(await orders.flagOf(a, key), true, `${key}: بی ردیف`);
    assert.equal(await inTx(a, key), true, `${key}: بی ردیف، درونِ تراکنش`);

    await put(a, key, 'false');
    assert.equal(await orders.flagOf(a, key), false, `${key}: false ِ دکان`);
    assert.equal(await inTx(a, key), false, `${key}: false ِ دکان، درونِ تراکنش`);
    assert.equal(await orders.flagOf(b, key), true, `${key}: دکانِ دیگر دست نمی‌خورد`);
    await clear(key);

    await put('all', key, 'false');
    assert.equal(await orders.flagOf(a, key), false, `${key}: false ِ all`);
    assert.equal(await orders.flagOf(b, key), false, `${key}: false ِ all برای همه`);
    await put(a, key, 'true');
    assert.equal(await orders.flagOf(a, key), true, `${key}: true ِ دکان بر false ِ all`);
    assert.equal(await orders.flagOf(b, key), false, key);
    await clear(key);

    await put(a, key, '"no"');
    assert.equal(await orders.flagOf(a, key), true, `${key}: مقدارِ نادرست ⇒ پیش‌فرضِ روشن`);
    await put(a, key, 'true');
    assert.equal(await orders.flagOf(a, key), true, `${key}: true ِ صریح`);
    await clear(key);

    await hold(a, key);
    assert.equal(await orders.flagOf(a, key), false, `${key}: نگه‌داشتِ سرویس`);
    assert.equal(await inTx(a, key), false, `${key}: نگه‌داشتِ سرویس، درونِ تراکنش`);
    await put(a, key, 'true');
    assert.equal(await orders.flagOf(a, key), false, `${key}: نگه‌داشت بر true هم جلو`);
    assert.equal(await orders.flagOf(b, key), true, `${key}: نگه‌داشتِ دکانِ دیگر`);
    await clear(key);
    await query('DELETE FROM shop_holds WHERE service=$1', [key]);
  }
});

test('کلیدِ بیرون از فهرست همان قاعدهٔ پیشین است: بی ردیف خاموش، فقط true روشن', async () => {
  const a = await shop('ج');
  for (const key of OFF) {
    await clear(key);
    assert.equal(await orders.flagOf(a, key), false, `${key}: بی ردیف`);
    assert.equal(await inTx(a, key), false, `${key}: بی ردیف، درونِ تراکنش`);
    await put(a, key, '"no"');
    assert.equal(await orders.flagOf(a, key), false, `${key}: مقدارِ نادرست`);
    await put(a, key, 'true');
    assert.equal(await orders.flagOf(a, key), true, `${key}: true ِ صریح`);
    await clear(key);
  }
});
