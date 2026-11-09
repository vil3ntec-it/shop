'use strict';
/**
 * STF-03 — تخفیفِ خودکارِ قلم (`promo`) و مصرفِ امتیاز (`loyalty_spend`) در برابرِ تعریف‌های دکان، راهِ «بررسی»
 * (`lib/promo-loyalty-review.js`، پرچمِ خاموشِ `shop.promo_loyalty_review`).
 *
 * سنجه‌ها: پرچمِ خاموش ⇒ هیچ سابقه؛ با پرچم فاکتورِ شاگرد در v3، v1 و راهِ قدیم **پذیرفته و دست‌نخورده** می‌نشیند و فقط
 * وقتی اعلامش با قاعده‌های دکان نمی‌خواند (بیش از مجاز، قاعدهٔ ناشناخته، کلیدِ خاموش، کدِ واردنشده؛ امتیازِ بیش از مانده یا
 * سهم، ارزشِ بادکرده، مشتریِ دیگر) یک سابقهٔ `sale.promo_review` (یک بار حتی با تکرار و قلمِ دیرآمده) می‌گیرد؛ اعلامِ درستِ
 * صندوق، صاحب و نقشِ جعلی در بدنه؛ و دکانِ دیگر با قاعده‌های خودش.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { many, query } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const R = require('../src/lib/promo-loyalty-review');

let shopId;
let shopB;
let owner;
let staff;
let staffB;
let ownerB;
let n = 0;
const oid = () => `01JPL${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now() - 3_600_000;

const setFlag = (scope, key, on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value=excluded.value`, [scope, key, JSON.stringify(on)]);
const push3 = (who, device, ops, extra = {}) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops, ...extra },
});
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
const legacy = (who, changes) => h.api('POST', '/api/sync/push', { token: who.accessToken, headers: { 'X-App': 'shop' }, body: { deviceId: 'web-1', changes } });
const statuses = (r) => r.body.results.map((x) => x.status);
const flags = async (shop = shopId) => (await many(
  `SELECT target_id, detail FROM audit_logs WHERE shop_id=$1 AND action='sale.promo_review' ORDER BY created_at, id`, [shop]
)).map((r) => ({ sale: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }));
const since = async (k, shop = shopId) => (await flags(shop)).slice(k);

const ins3 = (table, id, fields, tx) => ({ op_id: oid(), hlc: H('pos3'), table, row_id: id, kind: 'insert', fields, ...(tx ? { tx } : {}) });
const P10 = { id: 'p10', rev: 1, name: 'ده درصد' };
/**
 * فاکتورِ v3 با قلم‌ها و (اختیاری) مصرفِ امتیاز در یک tx — همان ثبتِ صندوق: قلمِ تخفیف‌خورده خالص است و `promo` جمعِ تخفیف.
 * `lines` = [[productId, listMinor, qtyMilli, promo[]]]
 */
function sale3(id, lines, { head = {}, spend = null } = {}) {
  const tx = `tx.${oid()}`;
  const at = Date.now();
  const items = lines.map(([pid, list, qty, promo = []], i) => {
    const gross = Math.round(list * qty / 1000);
    const off = promo.reduce((a, p) => a + p.discountMinor, 0);
    return ins3('saleItems', `${id}-i${i}`, { saleId: id, productId: pid, quantityMilli: qty, unitPriceMinor: Math.round((gross - off) * 1000 / qty),
      totalPriceMinor: gross - off, listPriceMinor: list, ...(promo.length ? { promo } : {}) }, tx);
  });
  const net = items.reduce((a, x) => a + x.fields.totalPriceMinor, 0) - (spend ? spend.valueMinor : 0);
  const out = [ins3('sales', id, { totalMinor: net, discountMinor: 0, finalTotalMinor: net, paidAmountMinor: net, remainingMinor: 0,
    debtGivenMinor: 0, paymentMethod: 'cash', status: 'completed', date: '2026-11-08', createdAt: at, ...head }, tx), ...items];
  if (spend) {
    out.push(ins3('auditLog', `${id}-lp-s`, { type: 'loyalty_spend', refId: id, payRef: spend.payRef, points: spend.points,
      amountMinor: spend.valueMinor, createdAt: at }, tx));
  }
  return out;
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  async function shopWithStaff(label) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop') };
  }
  ({ id: shopId, owner, staff } = await shopWithStaff('تخفیف'));
  ({ id: shopB, owner: ownerB, staff: staffB } = await shopWithStaff('تخفیفِ ب'));
  //  قاعده‌های دکانِ الف: ۱۰٪ ِ همه («بهترین») و ۲۰٪ با کدِ SAVE20؛ کلیدِ «تخفیف‌های خودکار» روشن
  assert.equal((await h.put('/api/shop/promos/settings', { enabled: true }, { token: owner.accessToken })).status, 200);
  for (const [id, rule] of [['p10', { name: 'ده درصد', kind: 'percent', bp: 1000 }], ['p20', { name: 'کد', kind: 'percent', bp: 2000, code: 'SAVE20' }]]) {
    const r = await h.put(`/api/shop/promos/rules/${id}`, { rule: { stack: 'best', active: true, ...rule } }, { token: owner.accessToken });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  //  کالا و مشتری (v3) — نوشتهٔ صاحب؛ امتیازِ پیشینِ مشتری ۵۰۰
  const r0 = await push3(owner, 'own-3', [
    ins3('products', 'pr-1', { name: 'کالا', salePriceMinor: 100000, purchasePriceMinor: 50000, category: 'c' }),
    ins3('debtors', 'd-l', { name: 'مشتریِ امتیاز', phone: '0700000201' }),
    ins3('debtors', 'd-old', { name: 'مشتریِ ادغام‌شده', phone: '0700000202', mergedInto: 'd-l' }),
    ins3('auditLog', 'd-l-earn', { type: 'loyalty_earn', refId: 'old-sale', payRef: 'd-old', points: 500, amountMinor: 0, createdAt: clock }),
  ]);
  assert.deepEqual(statuses(r0), ['applied', 'applied', 'applied', 'applied'], JSON.stringify(r0.body));
});
test.after(async () => { await h.stop(); });

test('قاعدهٔ خالص: اعلام و سبد مثلِ `PromoEngine.lines`؛ گروه با `compareKey`؛ قاعدهٔ «نزدیکِ انقضا» سقفِ بالا می‌گیرد', () => {
  assert.deepEqual(R.claimsOf({ promo: [{ id: 'a', rev: 2, discountMinor: 500 }, { id: 'b', discountMinor: -1 }, 'x', { discountMinor: 1.6 }] }),
    [{ id: 'a', rev: 2, minor: 500 }, { id: '', rev: null, minor: 2 }]);
  const products = new Map([['p', { salePriceMinor: 100000, purchasePriceMinor: 50000, category: 'c', brand: ' ب ' }]]);
  const cart = R.cartOf([{ id: 'i1', data: { productId: 'p', quantityMilli: 2000, totalPriceMinor: 180000, promo: [{ id: 'a', discountMinor: 20000 }] } },
    { id: 'i2', data: { productId: '', quantityMilli: 1000, totalPriceMinor: 5000, unitPriceMinor: 5000 } }], products, '2026-11-08', 'v3');
  assert.equal(cart.claimed, 20000);
  assert.deepEqual(cart.lines[0], { key: 'i1', productId: 'p', qtyMilli: 2000, unitMinor: 100000, amountMinor: 200000, costMinor: 50000, category: 'c', supplierId: '', brand: 'ب' });
  assert.equal(cart.lines[1].productId, '', 'قلمِ آزاد بی کالا');
  const rules = [{ id: 'g', name: 'گروه', kind: 'percent', bp: 1000, groups: ['عمده'] }];
  assert.equal(R.groupOf({ group: 'عمده ' }, rules), 'عمده');
  assert.equal(R.groupOf({ group: 'عمده' }, []), 'عمده');
  assert.equal(R.groupOf(null, rules), '');
  const at = { date: '2026-11-08', minute: 600 };
  assert.equal(R.allowedOf(cart, [{ id: 'x', name: 'x', kind: 'percent', bp: 1000 }], { at }), 20000);
  assert.equal(R.allowedOf(cart, [], { at }), 0);
  assert.equal(R.allowedOf(cart, [{ id: 'e', name: 'e', kind: 'expiry', bp: 2500, nearDays: 7 }], { at }), 50000, 'همه نزدیکِ انقضا');
  assert.equal(R.saleIdOf('saleItems', 'insert', 'i', { saleId: 's' }), 's');
  assert.equal(R.saleIdOf('auditLog', 'insert', 'a', { type: 'loyalty_spend', refId: 's' }), 's');
  assert.equal(R.saleIdOf('auditLog', 'insert', 'a', { type: 'loyalty_earn', refId: 's' }), '');
  assert.equal(R.saleIdOf('sales', 'update', 's', {}), '');
});

test('پرچمِ خاموش (پیش‌فرض): تخفیفِ ساختگی و امتیازِ نداشته همان امروز می‌نشیند و هیچ سابقه‌ای نمی‌گیرد', async () => {
  const r = await push3(staff, 'pos-1', sale3('s-off', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 60000 }]]],
    { spend: { payRef: 'd-x', points: 9000, valueMinor: 900000 }, head: { debtorId: 'd-x' } }));
  assert.ok(statuses(r).every((x) => x === 'applied'), JSON.stringify(r.body));
  assert.equal((await flags()).length, 0);
});

test('پرچمِ روشن، v3 ِ تخفیف: اعلامِ درست هیچ؛ بیش از مجاز، کدِ واردنشده و قاعدهٔ ناشناخته ⇒ پذیرفته و یک سابقه؛ تکرار نه', async () => {
  await setFlag(shopId, 'shop.promo_loyalty_review', true);
  const k = (await flags()).length;
  //  درست: ۱۰٪ ِ ۱۰۰۰ افغانی؛ ۲۰٪ با کد؛ دو واحد
  for (const ops of [
    sale3('s-ok', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 10000 }]]]),
    sale3('s-code', [['pr-1', 100000, 1000, [{ id: 'p20', rev: 1, discountMinor: 20000 }]]], { head: { coupons: ['save-20'] } }),
    sale3('s-two', [['pr-1', 100000, 2000, [{ ...P10, discountMinor: 20000 }]]]),
  ]) assert.ok(statuses(await push3(staff, 'pos-1', ops)).every((x) => x === 'applied'));
  assert.equal((await flags()).length, k, JSON.stringify(await since(k)));
  //  بیش از مجاز: ۳۰٪ با نامِ قاعدهٔ ۱۰٪ — پذیرفته و دفتر دست‌نخورده
  const over = sale3('s-over', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 30000 }]]]);
  assert.ok(statuses(await push3(staff, 'pos-1', over)).every((x) => x === 'applied'), 'رد نمی‌شود');
  const row = (await many(`SELECT data FROM sync_v3_rows WHERE account_id=$1 AND table_name='saleItems' AND row_id='s-over-i0'`, [shopId]))[0];
  assert.equal(row.data.totalPriceMinor, 70000, 'دفتر دست نمی‌خورد');
  //  کدی که روی فاکتور نیست ⇒ همان ۱۰٪ مجاز است؛ قاعدهٔ ساختگی
  await push3(staff, 'pos-1', sale3('s-nocode', [['pr-1', 100000, 1000, [{ id: 'p20', rev: 1, discountMinor: 20000 }]]]));
  await push3(staff, 'pos-1', sale3('s-fake', [['pr-1', 100000, 1000, [{ id: 'fake', rev: 1, discountMinor: 5000 }]]]));
  const f = await since(k);
  assert.deepEqual(f.map((x) => [x.sale, x.path, x.role, x.problems]), [
    ['s-over', 'v3', 'staff', 'promo_over'], ['s-nocode', 'v3', 'staff', 'promo_over'], ['s-fake', 'v3', 'staff', 'promo_unknown_rule'],
  ]);
  assert.deepEqual([f[0].promo.claimedMinor, f[0].promo.allowedMinor, f[0].promo.excessMinor, f[0].promo.rules], [30000, 10000, 20000, 'p10']);
  assert.deepEqual([f[1].promo.claimedMinor, f[1].promo.allowedMinor], [20000, 10000]);
  assert.equal(f[2].promo.unknown, 'fake');
  //  تکرارِ همان دسته (پاسخ گم شد) و قلمِ تازهٔ همان فاکتور ⇒ سابقهٔ دوم نه
  assert.ok(statuses(await push3(staff, 'pos-1', over)).every((x) => x === 'duplicate'));
  await push3(staff, 'pos-1', [ins3('saleItems', 's-over-i9', { saleId: 's-over', productId: 'pr-1', quantityMilli: 1000, totalPriceMinor: 10000,
    listPriceMinor: 100000, promo: [{ ...P10, discountMinor: 90000 }] })]);
  assert.equal((await since(k)).length, 3);
});

test('پرچمِ روشن، v3: قلمِ دیرآمده با تخفیفِ ساختگی برای فاکتورِ نشسته ⇒ یک سابقه؛ صاحب و نقشِ جعلی در بدنه', async () => {
  const k = (await flags()).length;
  const ops = sale3('s-late', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 10000 }]]]);
  await push3(staff, 'pos-1', ops.slice(0, 2).map(({ tx, ...o }) => o));
  assert.equal((await flags()).length, k);
  await push3(staff, 'pos-2', [ins3('saleItems', 's-late-i1', { saleId: 's-late', productId: 'pr-1', quantityMilli: 1000, totalPriceMinor: 50000,
    listPriceMinor: 100000, promo: [{ ...P10, discountMinor: 50000 }] })]);
  //  صاحب خودش بررسی‌کننده است؛ شاگردی که در بدنه «owner» می‌گوید شاگرد است
  await push3(owner, 'own-3', sale3('s-owner', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 90000 }]]]));
  await push3(staff, 'pos-1', sale3('s-forged', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 90000 }]]]), { role: 'owner' });
  const f = await since(k);
  assert.deepEqual(f.map((x) => [x.sale, x.role, x.promo.claimedMinor, x.promo.allowedMinor]), [['s-late', 'staff', 60000, 20000], ['s-forged', 'staff', 90000, 10000]]);
});

test('پرچمِ روشن، امتیاز با قاعدهٔ دکان: مصرفِ درست هیچ؛ بیش از مانده، بیش از سهم، ارزشِ بادکرده و مشتریِ دیگر ⇒ سابقه', async () => {
  await setFlag(shopId, 'shop.loyalty_rule', true);
  const lr = await h.put('/api/shop/loyalty', { on: true, rule: { everyAfn: 100, pointAfn: 1, maxSharePct: 20, silverAfn: 50000, goldAfn: 200000, expiryMonths: 0 } },
    { token: owner.accessToken });
  assert.equal(lr.status, 200, JSON.stringify(lr.body));
  const k = (await flags()).length;
  //  درست: ۲۰۰ امتیاز روی فاکتورِ ۱۰۰۰ افغانی (سهمِ ۲۰٪ = ۲۰۰)؛ مانده ۵۰۰ از مشتریِ ادغام‌شده
  const ok = sale3('s-lp-ok', [['pr-1', 100000, 1000]], { head: { debtorId: 'd-l' }, spend: { payRef: 'd-l', points: 200, valueMinor: 20000 } });
  assert.ok(statuses(await push3(staff, 'pos-1', ok)).every((x) => x === 'applied'));
  assert.equal((await flags()).length, k, JSON.stringify(await since(k)));
  //  مانده حالا ۳۰۰: ۲۵۰ روی ۲٬۰۰۰ افغانی (سقف ۴۰۰) ⇒ سالم، مانده ۵۰
  await push3(staff, 'pos-1', sale3('s-lp-ok2', [['pr-1', 100000, 2000]], { head: { debtorId: 'd-l' }, spend: { payRef: 'd-l', points: 250, valueMinor: 25000 } }));
  assert.equal((await flags()).length, k);
  //  هر مصرفِ نشسته (حتی ناجور) از ماندهٔ همان مشتری کم می‌شود — دفتر همان است که دستگاه‌ها دارند
  //  مشتریِ دیگر (۱ امتیاز)؛ سهم: ۴۰ روی ۱۰۰ افغانی (سقف ۲۰) ⇒ مانده ۱۰؛ ارزش: ۱۰ امتیاز با ارزشِ ۵۰ افغانی ⇒ مانده ۰؛ بیش از مانده
  await push3(staff, 'pos-1', sale3('s-lp-who', [['pr-1', 100000, 1000]], { head: { debtorId: 'd-l' }, spend: { payRef: 'someone', points: 1, valueMinor: 100 } }));
  await push3(staff, 'pos-1', sale3('s-lp-share', [['pr-1', 10000, 1000]], { head: { debtorId: 'd-l' }, spend: { payRef: 'd-l', points: 40, valueMinor: 4000 } }));
  await push3(staff, 'pos-1', sale3('s-lp-val', [['pr-1', 100000, 1000]], { head: { debtorId: 'd-l' }, spend: { payRef: 'd-l', points: 10, valueMinor: 5000 } }));
  await push3(staff, 'pos-1', sale3('s-lp-bal', [['pr-1', 100000, 2000]], { head: { debtorId: 'd-l' }, spend: { payRef: 'd-l', points: 300, valueMinor: 30000 } }));
  const f = await since(k);
  assert.deepEqual(f.map((x) => [x.sale, x.problems]), [
    ['s-lp-who', 'loyalty_customer'], ['s-lp-share', 'loyalty_over_share'], ['s-lp-val', 'loyalty_value'], ['s-lp-bal', 'loyalty_over_balance'],
  ]);
  assert.deepEqual([f[1].loyalty.capPoints, f[2].loyalty.expectedValueMinor], [20, 1000]);
  assert.deepEqual([f[3].loyalty.points, f[3].loyalty.balance, f[3].loyalty.ruleOnServer], [300, 0, true]);
  //  ⛔ بی دادهٔ شخصی
  assert.ok(!JSON.stringify(f).includes('0700000201'));
});

test('پرچمِ روشن، v1 و راهِ قدیم: همان سنجش روی دفترِ v1 (افغانی) و جدول‌های قدیم؛ بی قاعدهٔ امتیازِ سرور فقط مانده', async () => {
  await setFlag(shopId, 'shop.loyalty_rule', false);
  const k = (await flags()).length;
  const op1 = (table, rowId, fields) => ({ op_id: oid(), table, row_id: rowId, type: 'insert', ts: (clock += 1000), fields });
  const at = clock + 5000;
  const r1 = await push1(staff, 'pos-v1', [
    op1('products', 'pv-1', { name: 'کالای v1', salePrice: 1000, purchasePrice: 500 }),
    op1('debtors', 'dv-1', { name: 'مشتریِ v1', phone: '0700000203', createdAt: clock }),
    op1('auditLog', 'dv-1-earn', { type: 'loyalty_earn', refId: 'old-v1', payRef: 'dv-1', points: 100, amount: 0, createdAt: clock }),
    op1('sales', 'sv-1', { total: 300, finalTotal: 300, discount: 0, paymentMethod: 'cash', status: 'completed', debtorId: 'dv-1', createdAt: at }),
    op1('saleItems', 'sv-1-i', { saleId: 'sv-1', productId: 'pv-1', quantity: 1, unitPrice: 400, totalPrice: 400, listPriceMinor: 100000,
      promo: [{ ...P10, discountMinor: 60000 }] }),
    op1('auditLog', 'sv-1-lp-s', { type: 'loyalty_spend', refId: 'sv-1', payRef: 'dv-1', points: 150, amount: 100, createdAt: at }),
  ]);
  assert.ok(statuses(r1).every((x) => x === 'applied'), JSON.stringify(r1.body));
  const t = Date.now();
  assert.equal((await legacy(owner, [{ collection: 'products', id: 'pl-1', updatedAt: t, data: { name: 'کالای وب', salePrice: 1000, purchasePrice: 500 } }])).status, 200);
  //  کالایی که دکان ندارد تخفیف نمی‌گیرد (همان قلمِ آزادِ صندوق) ⇒ اعلامش همه بیش از مجاز
  const rl = await legacy(staff, [
    { collection: 'sales', id: 'sl-1', updatedAt: t, data: { total: 500, finalTotal: 500, status: 'completed', createdAt: t } },
    { collection: 'saleItems', id: 'sl-1-i', updatedAt: t, data: { saleId: 'sl-1', productId: 'pl-1', quantity: 1, unitPrice: 500, totalPrice: 500,
      listPriceMinor: 100000, promo: [{ ...P10, discountMinor: 50000 }] } },
    { collection: 'sales', id: 'sl-2', updatedAt: t, data: { total: 900, finalTotal: 900, status: 'completed', createdAt: t } },
    { collection: 'saleItems', id: 'sl-2-i', updatedAt: t, data: { saleId: 'sl-2', productId: 'pl-none', quantity: 1, unitPrice: 900, totalPrice: 900,
      promo: [{ ...P10, discountMinor: 10000 }] } },
  ]);
  assert.equal(rl.status, 200, JSON.stringify(rl.body));
  const f = await since(k);
  assert.deepEqual(f.map((x) => [x.sale, x.path, x.problems]), [
    ['sv-1', 'v1', 'promo_over,loyalty_over_balance'], ['sl-1', f[1].path, 'promo_over'], ['sl-2', f[1].path, 'promo_over'],
  ]);
  assert.deepEqual([f[2].promo.claimedMinor, f[2].promo.allowedMinor], [10000, 0]);
  assert.ok(['legacy', 'api'].includes(f[1].path));
  assert.deepEqual([f[0].promo.claimedMinor, f[0].promo.allowedMinor, f[0].loyalty.ruleOnServer, f[0].loyalty.balance], [60000, 10000, false, 100]);
  assert.deepEqual([f[1].promo.claimedMinor, f[1].promo.allowedMinor], [50000, 10000]);
});

test('PRM-02: کدِ سقف‌دار روی فاکتور بی مصرفِ گرفته‌شده برای همان فاکتور ⇒ `promo_unredeemed`؛ با مصرف هیچ', async () => {
  await setFlag(shopId, 'shop.promo_uses', true);
  const r = await h.put('/api/shop/promos/rules/p30', { rule: { name: 'سی درصد', kind: 'percent', bp: 3000, code: 'CAP30', maxUses: 5, stack: 'best', active: true } },
    { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const k = (await flags()).length;
  const claim = [{ id: 'p30', rev: 1, discountMinor: 30000 }];
  const red = await h.post('/api/shop/promos/redeem', { saleId: 's-cap-ok', codes: ['CAP30'] }, { token: staff.accessToken });
  assert.equal(red.status, 200, JSON.stringify(red.body));
  await push3(staff, 'pos-1', sale3('s-cap-ok', [['pr-1', 100000, 1000, claim]], { head: { coupons: ['CAP30'] } }));
  assert.equal((await flags()).length, k, JSON.stringify(await since(k)));
  await push3(staff, 'pos-1', sale3('s-cap-skip', [['pr-1', 100000, 1000, claim]], { head: { coupons: ['CAP30'] } }));
  const f = await since(k);
  assert.deepEqual(f.map((x) => [x.sale, x.problems, x.promo.unredeemed, x.promo.allowedMinor]), [['s-cap-skip', 'promo_unredeemed', 'p30', 30000]]);
  await setFlag(shopId, 'shop.promo_uses', false);
});

test('کلیدِ «تخفیف‌های خودکار» خاموش ⇒ هر اعلامی بیش از مجاز (`off`)؛ دکانِ دیگر با قاعده‌های خودش، سابقه‌ها جدا', async () => {
  //  دکانِ ب: پرچمِ بررسی روشن ولی هیچ قاعده‌ای ندارد ⇒ همان `p10` ِ دکانِ الف آن‌جا ناشناخته است
  await setFlag(shopB, 'shop.promo_loyalty_review', true);
  await push3(ownerB, 'ob', [ins3('products', 'pr-1', { name: 'کالای ب', salePriceMinor: 100000 })]);
  await push3(staffB, 'b-1', sale3('sb-1', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 10000 }]]]));
  const fb = await flags(shopB);
  assert.deepEqual(fb.map((x) => [x.sale, x.problems, x.promo.allowedMinor, x.promo.off]), [['sb-1', 'promo_over,promo_unknown_rule', 0, true]]);
  assert.ok(!(await flags()).some((x) => x.sale === 'sb-1'), 'سابقهٔ دکانِ ب در الف نیست');
  //  دکانِ الف کلید را خاموش می‌کند ⇒ همان اعلامِ درستِ دیروز حالا بیش از مجاز است، با «قاعده پس از فاکتور عوض شد» نه
  const k = (await flags()).length;
  assert.equal((await h.put('/api/shop/promos/settings', { enabled: false }, { token: owner.accessToken })).status, 200);
  await push3(staff, 'pos-1', sale3('s-keyoff', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 10000 }]]]));
  const f = await since(k);
  assert.deepEqual(f.map((x) => [x.sale, x.promo.off, x.promo.allowedMinor, x.promo.ruleChanged]), [['s-keyoff', true, 0, undefined]]);
  const audit = await h.get('/api/shop/audit?limit=500', { token: owner.accessToken });
  assert.ok(audit.body.entries.some((e) => e.action === 'sale.promo_review'));
  assert.equal((await h.get('/api/shop/audit', { token: staff.accessToken })).status, 403, 'شاگرد سابقه را نمی‌بیند');
});

test('شکستِ بررسی هرگز push را نمی‌خواباند: خطای پرسش و عددِ ناامن ⇒ همان «applied»', async () => {
  await h.put('/api/shop/promos/settings', { enabled: true }, { token: owner.accessToken });
  const orders = require('../src/lib/orders');
  const real = orders.flagOf;
  orders.flagOf = async (shop, key, run) => { if (key === R.FLAG) throw new Error('db down'); return real(shop, key, run); };
  const k = (await flags()).length;
  try {
    const r = await push3(staff, 'pos-1', sale3('s-boom', [['pr-1', 100000, 1000, [{ ...P10, discountMinor: 90000 }]]]));
    assert.ok(statuses(r).every((x) => x === 'applied'), JSON.stringify(r.body));
  } finally { orders.flagOf = real; }
  assert.equal((await flags()).length, k);
  //  مقدارِ ناامن روی قلم ⇒ قلم کنار، اعلامش بیش از مجاز؛ push همان
  const r2 = await push3(staff, 'pos-1', [
    ins3('sales', 's-huge', { totalMinor: 1, finalTotalMinor: 1, paymentMethod: 'cash', status: 'completed', createdAt: Date.now() }),
    ins3('saleItems', 's-huge-i', { saleId: 's-huge', productId: 'pr-1', quantityMilli: 1e30, totalPriceMinor: 1, promo: [{ ...P10, discountMinor: 5000 }] }),
  ]);
  assert.equal(r2.status, 200, JSON.stringify(r2.body));
  assert.ok((await since(k)).every((x) => x.sale === 's-huge'));
});
