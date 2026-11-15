'use strict';
/**
 * کاتالوگِ پرچم‌ها (`KNOWN` ِ `routes/admin-flags.js`) فقط «شرح و پیش‌فرض» است — افزودنِ ردیف نباید هیچ رفتاری را عوض کند.
 * برای هر ردیفی که دستهٔ ۳۸ افزود (۵۵ کلیدِ بیرون از کاتالوگ؛ سنجهٔ `console-shell.test.js`):
 *   ۱) بی هیچ ردیفِ `live_config`، خواننده‌ای که خودِ کد صدا می‌زند همان `default` ِ کاتالوگ را می‌دهد؛
 *   ۲) نوشتنِ همان `default` از درِ پنل (`PUT /api/admin/flags`، با سنجشِ نوع و scope ِ کاتالوگ) ۲۰۰ است و چیزی را عوض نمی‌کند؛
 *   ۳) مقدارِ دیگر (بولیِ وارونه، عددِ دیگر، شیءِ دیگر) خواننده را عوض می‌کند ⇒ خواننده واقعاً همین کلید را می‌خواند (۱ تهی نیست).
 * خواننده = تابعِ صادرشدهٔ همان پرونده؛ جایی که کد `orders.flagOf` را درجا صدا می‌زند (`site`) همان صدا، و سنجه می‌بیند آن
 * صدا هنوز در همان پرونده هست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

const PASS = 'Admin!12345';
let sup = '';
let shopId = '';
let userToken = '';

test.before(async () => {
  await h.start();
  const hash = await require('../src/lib/password').hashPassword(PASS);
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'parcham','parcham',$2,'superadmin','active',$3)`,
    [newId('adm'), hash, now()]);
  sup = (await h.post('/api/admin/login', { username: 'parcham', password: PASS })).body.token;
  const u = await h.newUser('صاحبِ کاتالوگ', 'shop');
  userToken = u.accessToken;
  shopId = (await h.post('/api/shop', { name: 'دکانِ کاتالوگ' }, { token: userToken })).body.shop.id;
});
test.after(async () => { await h.stop(); });

const L = (f) => require(`../src/lib/${f}`);
const run = (t, p) => require('../src/db').query(t, p);
const ctx = (s) => ({ app: 'shop', accountKind: 'shop', accountId: s, role: 'staff' });
const SRC = path.join(__dirname, '..', 'src');
/** همان صدای درجای کد: `orders.flagOf(shop, KEY)` — و آن صدا هنوز در `site` هست */
const site = (file, name) => {
  const text = fs.readFileSync(path.join(SRC, file), 'utf8');
  assert.match(text, new RegExp(`flagOf\\([^)]*\\b${name}\\b`), `${file}: صدای flagOf با ${name} نیست`);
};
const flagAt = (file, name, key, global = false) => async (s) => { site(file, name); return L('orders').flagOf(global ? 'all' : s, key); };
const pin = (s) => L('courier-checks').decorate(s, { items: [{ live: true, status: 'accepted', orderId: 'o1', address: { lat: 34.5, lng: 69.2 } }] });

/** [کلید، خواننده(shopId)، مقدارِ دیگر، پیش‌نیازها] — مقدارِ دیگر نیامده ⇒ وارونهٔ بولی */
const PARITY = [
  //  پیک و تحویل
  ['shop.delivery_map', flagAt('routes/courier.js', 'courierMap.FLAG', 'shop.delivery_map')],
  ['shop.delivery_courier_call', (s) => L('delivery-call').callFlagOf(s)],
  ['shop.delivery_bag_scan', flagAt('lib/courier-checks.js', 'BAG_FLAG', 'shop.delivery_bag_scan')],
  ['shop.delivery_near_pin', async (s) => Boolean((await pin(s)).items[0].nearPin)],
  ['shop.delivery_near_pin.max_m', async (s) => (await pin(s)).items[0].nearPin.maxM, 500, ['shop.delivery_near_pin']],
  ['shop.delivery_route', flagAt('routes/courier.js', 'courierRoute.FLAG', 'shop.delivery_route')],
  ['shop.delivery_ratings', flagAt('lib/delivery-ratings.js', 'FLAG', 'shop.delivery_ratings')],
  ['shop.courier_week_rule', flagAt('lib/courier-summary.js', 'WEEK_FLAG', 'shop.courier_week_rule')],
  ['shop.delivery_code_rule', async (s) => (await L('delivery-code-rule').policyOf(s)).on],
  ['shop.delivery_code_rule.min_total_minor', async (s) => (await L('delivery-code-rule').policyOf(s)).minTotalMinor, 2_500_000, ['shop.delivery_code_rule']],
  ['shop.delivery_live_location', flagAt('lib/delivery-live.js', 'LOCATION_FLAG', 'shop.delivery_live_location')],
  //  گزارش، صندوق، مجوز، تخفیف
  ['shop.owner_report', (s) => L('owner-report').flagOf(s)],
  ['shop.branches_report', (s) => L('branches-report').flagOf(s)],
  ['shop.debt_minor', (s) => L('receipt-profile').debtMinorFor(s)],
  ['shop.doc_chain', flagAt('lib/doc-chain.js', 'FLAG', 'shop.doc_chain')],
  ['shop.device_cap', flagAt('routes/license.js', 'STRICT_CAP_FLAG', 'shop.device_cap')],
  ['shop.license_offline_30d', flagAt('routes/license.js', 'OFFLINE_FLAG', 'shop.license_offline_30d')],
  ['shop.price_cap', (s) => L('price-cap').on(ctx(s))],
  ['shop.promo_uses', flagAt('routes/shop-promos.js', 'USES_FLAG', 'shop.promo_uses')],
  ['shop.promo_expiry', flagAt('routes/shop-promos.js', 'EXPIRY_FLAG', 'shop.promo_expiry')],
  ['shop.promo_tier_price', flagAt('routes/shop-promos.js', 'TIER_PRICE_FLAG', 'shop.promo_tier_price')],
  ['shop.purchase_orders', (s) => L('purchase-orders').enabled(s)],
  ['shop.staff_time', (s) => L('staff-time').flagOf(s)],
  ['shop.stock_v2', flagAt('routes/shop-stock-ledger.js', 'FLAG_KEY', 'shop.stock_v2')],
  ['shop.order_risk', (s) => L('order-risk').flagIn(run, s)],
  ['shop.order_risk.policy', (s) => L('order-risk').policyIn(run, s), { newCustomerMaxTotalMinor: 500_000, newCustomerMaxOpen: 1, duplicateWindowMin: 5 }],
  ['shop.visual_search', (s) => L('visual-search').flagOn(s)],
  ['image_bank.quality', (s) => L('image-bank').flagOf('image_bank.quality', s)],
  ['shop.image_studio', (s) => L('image-studio-jobs').enabled(s)],
  ['pay.hesabpay', flagAt('lib/payment-intents.js', 'FLAG_KEY', 'pay.hesabpay')],
  //  همگام‌سازیِ دکان
  ['shop.sync.doc_roles', flagAt('lib/doc-roles.js', 'FLAG', 'shop.sync.doc_roles')],
  ['shop.sync_v3_to_v1', (s) => L('sync-v3-to-v1').enabled(ctx(s))],
  ['sync.cursor_from_since', (s) => L('sync-v1-ack').on(ctx(s))],
  ['sync.safe_entries', (s) => L('safe-flag').enabledFor(s)],
  ['sync.keep_pending', async (s) => (await L('sync-v1-keep').flags(ctx(s))).keepPending],
  ['sync.revive_full_row', async (s) => (await L('sync-v1-keep').flags(ctx(s))).reviveRow],
  ['sync.gzip_pull', async (s) => (await L('sync-v1-keep').flags(ctx(s))).gzip],
  ['sync.gzip_push', async (s) => (await L('sync-v1-keep').flags(ctx(s))).gzipPush],
  //  بازار
  ['market.changes', (s) => L('market-changes').enabled(s)],
  ['market.trust', (s) => L('market-trust').flagOf(s)],
  ['market.public_id', flagAt('lib/storefront-public-id.js', 'PUBLIC_ID_FLAG', 'market.public_id')],
  ['market.platform_fee_bps', async (s) => (await L('market-fees').policyIn(run, s)).platformFeeBps, 150],
  ['market.platform_fee_fixed_minor', async (s) => (await L('market-fees').policyIn(run, s)).platformFeeFixedMinor, 2000],
  ['market.commission_bps', async (s) => (await L('market-fees').policyIn(run, s)).commissionBps, 300],
  ['market.commission_fixed_minor', async (s) => (await L('market-fees').policyIn(run, s)).commissionFixedMinor, 1000],
  ['market.account', () => L('market').accountOn()],
  ['market.product_details', () => L('product-details').on()],
  ['market.public_views', flagAt('lib/public-views.js', 'FLAG', 'market.public_views', true)],
  ['storefront.catalog_source', flagAt('lib/storefront-catalog.js', 'CATALOG_FLAG', 'storefront.catalog_source', true)],
  ['image_bank.resume', () => L('image-upload').resumeEnabled()],
  ['console.delivery_thresholds', async () => (await L('admin-market').thresholds()).values,
    { acceptMin: 45, dispatchMin: 30, courierAcceptMin: 15, pickupMin: 30, inDeliveryMin: 120, failedMin: 240, recallMin: 60 }],
  //  ops و امنیت
  ['ops.critical_alerts', async () => (await L('ops-paging').settings()).enabled],
  ['ops.critical_alerts.escalate_min', async () => (await L('ops-paging').settings()).escalateMin, 30],
  ['ops.critical_alerts.max_escalations', async () => (await L('ops-paging').settings()).maxEscalations, 4],
  ['ops.sync_lag', async () => 'lag' in (await L('system-health').snapshot()).sync],
  ['backup.require_encryption', () => L('backup').encryptionRequired()],
  ['admin.support_grant', () => L('support-access').enforced()],
];

const putFlag = (scope, key, value) => h.put('/api/admin/flags', { scope, key, value, reason: 'آزمونِ پیش‌فرضِ کاتالوگ' }, { token: sup });

test('کاتالوگ: هر ردیفِ تازه همان پیش‌فرضِ کد است؛ نوشتنِ پیش‌فرض بی‌اثر، مقدارِ دیگر اثردار', async () => {
  const { KNOWN } = require('../src/routes/admin-flags');
  assert.equal(new Set(PARITY.map((p) => p[0])).size, PARITY.length, 'کلیدِ تکراری در جدول');
  assert.equal(PARITY.length, 57);   //  «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): `auth.phone_add_code` با درِ کدِ شماره رفت
  for (const [key, read, other, needs = []] of PARITY) {
    const k = KNOWN.find((x) => x.key === key);
    assert.ok(k, `${key}: در کاتالوگ نیست`);
    const scope = k.scope === 'shop' ? shopId : k.scope;
    await query('DELETE FROM live_config WHERE scope = $1 OR key = $2', [shopId, key]);
    for (const n of needs) assert.equal((await putFlag(shopId, n, true)).status, 200, n);
    //  ۱) بی ردیف ⇒ پیش‌فرضِ کد = پیش‌فرضِ کاتالوگ
    assert.deepEqual(await read(shopId), k.default, `${key}: پیش‌فرضِ کد با کاتالوگ نمی‌خواند`);
    //  ۲) همان پیش‌فرض از درِ پنل ⇒ پذیرفته و بی‌اثر
    const w = await putFlag(scope, key, k.default);
    assert.equal(w.status, 200, `${key}: ${JSON.stringify(w.body)}`);
    assert.deepEqual(await read(shopId), k.default, `${key}: نوشتنِ پیش‌فرض رفتار را عوض کرد`);
    //  ۳) مقدارِ دیگر ⇒ خواننده عوض می‌شود (پس واقعاً همین کلید را می‌خواند)
    const alt = other === undefined ? !k.default : other;
    assert.equal((await putFlag(scope, key, alt)).status, 200, `${key} ⇐ ${JSON.stringify(alt)}`);
    assert.deepEqual(await read(shopId), alt, `${key}: خواننده این کلید را نمی‌خواند`);
    await query('DELETE FROM live_config WHERE scope = $1 OR key = $2', [shopId, key]);
  }
});

test('کاتالوگ: مقدارِ ناهم‌نوع یا scope ِ نادرست برای ردیف‌های تازه رد و نوشته‌نشده', async () => {
  for (const [scope, key, value, code] of [
    [shopId, 'shop.delivery_map', 'true', 'bad_flag_value'],
    [shopId, 'shop.delivery_near_pin.max_m', '300', 'bad_flag_value'],
    [shopId, 'shop.order_risk.policy', 5, 'bad_flag_value'],
    [shopId, 'market.account', true, 'bad_flag_scope'],
    ['all', 'ops.sync_lag', true, 'bad_flag_scope'],
    ['ops', 'backup.require_encryption', true, 'bad_flag_scope'],
  ]) {
    const r = await putFlag(scope, key, value);
    assert.equal(r.status, 400, `${key} ⇐ ${JSON.stringify(value)}: ${r.status}`);
    assert.equal(r.body.error.code, code, key);
  }
  const n = await query(`SELECT COUNT(*)::int n FROM live_config WHERE key IN ('shop.delivery_map','shop.delivery_near_pin.max_m','shop.order_risk.policy','market.account','ops.sync_lag','backup.require_encryption')`);
  assert.equal(n.rows[0].n, 0, '⛔ مقدارِ نادرست نوشته شد');
});
