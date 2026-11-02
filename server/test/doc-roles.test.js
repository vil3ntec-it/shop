'use strict';
/**
 * داوریِ نقشِ سندهای سابقه‌سوار در Sync v3 — `src/lib/doc-roles.js`، پرچمِ `shop.sync.doc_roles` (پیش‌فرض خاموش).
 *  ۱) آینه: برای هر نوع، هر هفت نقش و چند لایه، تصمیمِ سرور = همان دروازه‌ای که برنامه پیش از ساختنِ ردیف می‌سنجد
 *     (بازنویسیِ جدا از کدِ اندروید، با نشانیِ همان سطر) ⇒ سرور چیزی را که برنامه اجازه می‌دهد رد نمی‌کند.
 *  ۲) شکل: مبلغ/امتیازِ نه صحیحِ مثبت، `refId` ِ خالی، نوعِ ناشناختهٔ همین خانواده‌ها.
 *  ۳) push ِ واقعی: خاموش ⇒ همان امروز؛ روشن ⇒ پذیرش برای نقشِ درست، ردِ بی‌اجازه، صاحب همیشه، شکلِ بد رد،
 *     ردِ کلِ واحدِ tx؛ و v1 که این داوری را ندارد.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const live = require('../src/lib/live-config');
const staffPerms = require('../src/lib/staff-perms');
const core = require('../src/lib/perm-core');
const docRoles = require('../src/lib/doc-roles');

const CODE = 'ABCDEFGHJKLM';

/** دروازه‌های برنامه، از روی کدِ اندروید (نه از روی `doc-roles.js`) */
const device = {
  //  `ShopRole.canWrite(context, "sales")` — `canSell` ِ GiftCardBarAndroid.kt (فروش و شارژِ کارت)؛ و هر فاکتورِ صندوق
  sell: (role) => core.canWrite(role, 'sales'),
  //  `ShopRole.canCancelSale` = `allows("sales.cancel")` — ShopRole.kt؛ LayawayBarAndroid و SpecialOrderBarAndroid
  cancel: (role, L) => core.allows(role, L, 'sales.cancel'),
  //  `canVoid = canSell && ShopRole.canCancelSale(context)` — GiftCardBarAndroid.kt (#801)
  void: (role, L) => core.canWrite(role, 'sales') && core.allows(role, L, 'sales.cancel'),
  //  برگشت: `ReturnFlow.permDecision` ≠ deny (allow، یا approve با PIN) و سرور ردیفِ `returns` را می‌نشاند؛ یا لغو
  back: (role, L) => (core.canWrite(role, 'returns')
    && core.evaluate(role, L, 'returns.create', { amount: 100 }).decision !== 'deny') || core.allows(role, L, 'sales.cancel'),
  //  StockPlacesUiAndroid.kt: `if (ShopRole.current(context) == ShopRole.STAFF) return`
  places: (role) => role !== 'staff',
  //  LoyaltyPoints.adjust: `may` = صاحب/مدیر (PRM-13، #815)
  manage: (role) => role === 'owner' || role === 'manager',
  //  بی سنجشِ نقش: ساختِ امانی/سفارش، «آماده»، پیش‌پرداخت و قسط
  open: () => true,
};
//  PAY-07: `PayStatusRoute` (PayStatusUi.kt) — `sell = ShopRole.canWrite(context, "sales")` برای «در انتظار ⇐ تأیید/بررسی»،
//  `manage = !ShopRole.isStaff(context)` (صاحب/مدیر) برای بقیه — اصلاحِ ثبتِ دستیِ «تأییدشده» هم؛ همان `PayStatus.allowed` ِ هسته
const payRow = (fromStatus, toStatus) => () => ({
  type: 'pay_status', refId: 's-1', payRow: 1, payMethod: 'afpay_card', fromStatus, toStatus, amountMinor: 150000, note: 'رسیدِ دستگاه',
});

/** هر نوع: جدول، فیلدهای همان شکلی که برنامه می‌سازد، و دروازهٔ برنامه */
const CASES = {
  gift_card_issue: ['auditLog', () => ({ type: 'gift_card_issue', payRef: CODE, amountMinor: 200000, notes: 'فروشِ کارت' }), 'sell'],
  gift_card_recharge: ['auditLog', () => ({ type: 'gift_card_recharge', payRef: CODE, amountMinor: 50000 }), 'sell'],
  gift_card_redeem: ['auditLog', () => ({ type: 'gift_card_redeem', payRef: CODE, amountMinor: 100000, refId: 's-1' }), 'sell'],
  gift_card_refund: ['auditLog', () => ({ type: 'gift_card_refund', payRef: CODE, amountMinor: 30000, refId: 'rd-1' }), 'back'],
  gift_card_void: ['auditLog', () => ({ type: 'gift_card_void', payRef: CODE, amountMinor: 0, reason: 'کارتِ گم‌شده' }), 'void'],
  loyalty_earn: ['auditLog', () => ({ type: 'loyalty_earn', refId: 's-1', payRef: 'd-lp', points: 12, amountMinor: 0 }), 'sell'],
  loyalty_spend: ['auditLog', () => ({ type: 'loyalty_spend', refId: 's-1', payRef: 'd-lp', points: 300, amountMinor: 30000 }), 'sell'],
  loyalty_earn_back: ['auditLog', () => ({ type: 'loyalty_earn_back', refId: 's-1', payRef: 'd-lp', points: 4, amountMinor: 0 }), 'back'],
  loyalty_spend_back: ['auditLog', () => ({ type: 'loyalty_spend_back', refId: 's-1', payRef: 'd-lp', points: 100, amountMinor: 10000 }), 'back'],
  layaway: ['auditLog', () => ({
    type: 'layaway', date: '2026-11-02', amountMinor: 550000,
    layaway: { debtorId: 'd-lw', kind: 'layaway', totalMinor: 1833400, depositMinor: 550000, lines: [], schedule: [] },
  }), 'open'],
  'layaway_status:delivered': ['auditLog', () => ({
    type: 'layaway_status', refId: 'lw-1', amountMinor: 1833400, layaway: { to: 'delivered', saleId: 'lw-1-d1' },
  }), 'sell'],
  'layaway_status:cancelled': ['auditLog', () => ({
    type: 'layaway_status', refId: 'lw-1', amountMinor: 550000, reason: 'مشتری پشیمان شد', layaway: { to: 'cancelled' },
  }), 'cancel'],
  special_order: ['auditLog', () => ({
    type: 'special_order', amountMinor: 100000, specialOrder: { debtorId: 'd-so', item: 'کفشِ ۴۲', qtyMilli: 1000, totalMinor: 500000 },
  }), 'open'],
  'special_order_status:ready': ['auditLog', () => ({ type: 'special_order_status', refId: 'so-1', amountMinor: 0, specialOrder: { to: 'ready' } }), 'open'],
  'special_order_status:delivered': ['auditLog', () => ({
    type: 'special_order_status', refId: 'so-1', amountMinor: 500000, specialOrder: { to: 'delivered', saleId: 'so-1-s' },
  }), 'sell'],
  'special_order_status:cancelled': ['auditLog', () => ({
    type: 'special_order_status', refId: 'so-1', amountMinor: 100000, reason: 'تأمین نشد', specialOrder: { to: 'cancelled' },
  }), 'cancel'],
  loyalty_adjust: ['auditLog', () => ({ type: 'loyalty_adjust', payRef: 'd-lp', points: -50, notes: 'اصلاحِ دستی', amountMinor: 0 }), 'manage'],
  stock_place: ['auditLog', () => ({ type: 'stock_place', refId: 'قفسهٔ ۲', reason: 'add' }), 'places'],
  stock_repack: ['auditLog', (id) => ({ type: 'stock_repack', refId: id, refIds: ['warehouseEntries/w1'] }), 'places'],
  stock_assembly: ['auditLog', (id) => ({ type: 'stock_assembly', refId: id, refIds: ['warehouseEntries/w2'] }), 'places'],
  'pay_status:confirmed': ['auditLog', payRow('pending', 'confirmed'), 'sell'],
  'pay_status:flag': ['auditLog', payRow('confirmed', 'needs_review'), 'manage'],
  'pay_status:fail': ['auditLog', payRow('confirmed', 'failed'), 'manage'],
  'pay_status:void': ['auditLog', payRow('confirmed', 'cancelled'), 'manage'],
  'pay_status:needs_review': ['auditLog', payRow('pending', 'needs_review'), 'sell'],
  'pay_status:failed': ['auditLog', payRow('pending', 'failed'), 'manage'],
  'pay_status:cancelled': ['auditLog', payRow('pending', 'cancelled'), 'manage'],
  'pay_status:resolved': ['auditLog', payRow('needs_review', 'confirmed'), 'manage'],
  'pay_status:review_failed': ['auditLog', payRow('needs_review', 'failed'), 'manage'],
  layaway_deposit: ['transactions', () => ({ debtorId: 'd-lw', type: 'receive', amountMinor: 550000, refId: 'lw-1', kind: 'layaway_deposit' }), 'open'],
  layaway_installment: ['transactions', () => ({ debtorId: 'd-lw', type: 'receive', amountMinor: 733400, refId: 'lw-1', kind: 'layaway_installment' }), 'open'],
  layaway_refund: ['transactions', () => ({ debtorId: 'd-lw', type: 'give', amountMinor: 550000, refId: 'lw-1', kind: 'layaway_refund' }), 'cancel'],
  special_order_deposit: ['transactions', () => ({ debtorId: 'd-so', type: 'receive', amountMinor: 100000, refId: 'so-1', kind: 'special_order_deposit' }), 'open'],
  special_order_refund: ['transactions', () => ({ debtorId: 'd-so', type: 'give', amountMinor: 100000, refId: 'so-1', kind: 'special_order_refund' }), 'cancel'],
};
const NAMES = Object.keys(CASES);

/** لایه‌ها: بی لایه، تنگ‌کردنِ لغو، «با تأیید» برای لغو، ممنوع یا «با تأیید» برای برگشت */
const LAYERS = [
  [{}, {}],
  [{ 'sales.cancel': { mode: 'deny' } }, {}],
  [{ 'sales.cancel': { mode: 'approve' } }, {}],
  [{}, { 'returns.create': { mode: 'deny' } }],
  [{ 'returns.create': { mode: 'approve' } }, {}],
];

// ---------------------------------------------------------------- ۱ و ۲: بی پایگاه

test('آینه: هر نوع × هفت نقش × پنج لایه — تصمیمِ سرور همان دروازهٔ برنامه است', () => {
  let n = 0;
  for (const name of NAMES) {
    const [table, fields, gate] = CASES[name];
    const need = docRoles.needOf(table, fields('r-1'));
    assert.notEqual(need, docRoles.BAD, `${name}: نمونهٔ برنامه بدشکل خوانده شد`);
    assert.notEqual(need, null, `${name}: داوری نشد`);
    for (const role of core.ROLES) {
      for (const L of LAYERS) {
        assert.equal(docRoles.permits(need, role, L), device[gate](role, L), `${name} / ${role} / ${JSON.stringify(L)}`);
        n++;
      }
    }
  }
  assert.equal(n, NAMES.length * core.ROLES.length * LAYERS.length);
});

test('آینه: صاحب دکان برای هر نوع و هر لایه‌ای مجاز است', () => {
  for (const name of NAMES) {
    const [table, fields] = CASES[name];
    for (const L of LAYERS) assert.equal(docRoles.permits(docRoles.needOf(table, fields('r-1')), 'owner', L), true, name);
  }
});

test('شکل: نمونه‌های بدِ هر خانواده BAD، نوع‌های دیگرِ سابقه دست نمی‌خورند', () => {
  const bad = (table, f) => assert.equal(docRoles.needOf(table, f), docRoles.BAD, JSON.stringify(f));
  const base = (name) => CASES[name][1]('r-1');
  for (const name of ['gift_card_issue', 'gift_card_recharge', 'gift_card_redeem', 'gift_card_refund']) {
    for (const amountMinor of [0, -5, 12.5, '100', undefined, 10_000_000_001]) bad('auditLog', { ...base(name), amountMinor });
    bad('auditLog', { ...base(name), payRef: ' ' });
  }
  for (const name of ['gift_card_redeem', 'gift_card_refund', 'loyalty_earn', 'loyalty_spend_back', 'layaway_status:cancelled',
    'special_order_status:ready', 'stock_place', 'stock_repack', 'layaway_installment', 'special_order_refund']) {
    bad(CASES[name][0], { ...base(name), refId: '' });
    const f = base(name); delete f.refId; bad(CASES[name][0], f);
  }
  bad('auditLog', { ...base('gift_card_void'), amountMinor: 100 });
  bad('auditLog', { ...base('gift_card_void'), reason: ' ab ' });
  for (const points of [0, -1, 1.5, undefined, 1_000_000_001]) bad('auditLog', { ...base('loyalty_earn'), points });
  bad('auditLog', { ...base('loyalty_spend'), amountMinor: -1 });
  bad('auditLog', { ...base('layaway'), layaway: undefined });
  bad('auditLog', { ...base('layaway'), amountMinor: -1 });
  bad('auditLog', { ...base('layaway_status:delivered'), layaway: { to: 'open' } });
  bad('auditLog', { ...base('special_order_status:ready'), specialOrder: { to: 'lost' } });
  bad('auditLog', { ...base('stock_place'), reason: 'rename' });
  for (const k of ['layaway_deposit', 'layaway_refund', 'special_order_deposit']) {
    bad('transactions', { ...base(k), amountMinor: 0 });
    bad('transactions', { ...base(k), type: base(k).type === 'give' ? 'receive' : 'give' });
  }
  for (const type of ['gift_card_mint', 'loyalty_bonus', 'layaway_x', 'special_order_x']) bad('auditLog', { type, refId: 'x', amountMinor: 1 });
  bad('transactions', { ...base('layaway_deposit'), kind: 'layaway_bonus' });
  //  نوع‌های دیگر مالِ این داوری نیستند
  for (const f of [{ type: 'sale', refId: 's' }, { type: 'gift_card', amountMinor: -1 }, { type: 'stock_adjustment' }, { type: 'loyalty' }, {}]) {
    assert.equal(docRoles.needOf('auditLog', f), null, JSON.stringify(f));
  }
  for (const f of [{ type: 'give', amountMinor: 5, kind: 'store_credit' }, { type: 'receive', amountMinor: 5 }]) {
    assert.equal(docRoles.needOf('transactions', f), null, JSON.stringify(f));
  }
  assert.equal(docRoles.needOf('sales', { type: 'gift_card_void' }), null);
});

test('شکل: وضعیتِ پرداخت (PAY-07) — گذارِ نادستی یا ناروا، ردیف/روش/فاکتورِ گم و مبلغِ منفی BAD', () => {
  const base = CASES['pay_status:confirmed'][1]('r-1');
  const bad = (f) => assert.equal(docRoles.needOf('auditLog', f), docRoles.BAD, JSON.stringify(f));
  //  پایانی برنمی‌گردد، بازپرداخت فقط از راهِ برگشت، وضعیتِ ناشناخته
  for (const [fromStatus, toStatus] of [['confirmed', 'pending'], ['confirmed', 'confirmed'], ['pending', 'refunded'], ['pending', 'pending'],
    ['failed', 'confirmed'], ['cancelled', 'confirmed'], ['refunded', 'confirmed'], ['refunded', 'failed'], ['confirmed', 'partially_refunded'],
    ['partially_refunded', 'failed'], ['paid', 'confirmed'], ['pending', 'paid']]) {
    bad({ ...base, fromStatus, toStatus });
  }
  for (const payRow of [-1, 1.5, '1', undefined, null, 1000]) bad({ ...base, payRow });
  bad({ ...base, payMethod: ' ' });
  bad({ ...base, refId: '' });
  bad({ ...base, amountMinor: -1 });
  bad({ ...base, amountMinor: 1.5 });
  const f = { ...base }; delete f.fromStatus; bad(f);
  bad({ type: 'pay_status_bulk', refId: 's-1' });
  //  مبلغِ نبوده = صفر (ردیفِ بی مبلغ هم شکل دارد)
  const g = { ...base }; delete g.amountMinor; assert.equal(docRoles.needOf('auditLog', g), docRoles.SELL);
});

// ---------------------------------------------------------------- ۳: push ِ واقعی

let shopId = '';
const T = {};
let seq = 0;
const oid = () => `01JDR${String(++seq).padStart(21, '0')}`;
let tick = 0;
const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'pos' });

const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `dr3-${who}`, protocol: 3, ops },
});
const push1 = (who, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].accessToken, body: { device_id: `dr1-${who}`, schema_version: 1, ops },
});
const v3op = (table, rowId, fields, tx) => ({ op_id: oid(), hlc: H(), table, row_id: rowId, kind: 'insert', fields, tx });
/** opِ همان نوع با شناسهٔ ردیفِ یکتا */
const opOf = (name, tag, tx) => {
  const [table, fields] = CASES[name];
  const rowId = `${tag}-${name.replace(':', '-')}`;
  return v3op(table, rowId, fields(rowId), tx);
};
const results = (r) => r.body.results.map((x) => x.reason || x.status);
const inV3 = async (table, rowId) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
))?.data;
async function expectEach(who, names, tag, want) {
  const r = await push3(who, names.map((n) => opOf(n, tag)));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(results(r), names.map(() => want), `${who}/${tag}: ${names.join(', ')} ⇒ ${JSON.stringify(r.body.results)}`);
  return r;
}
const setFlag = (scope, value) => live.set(scope, docRoles.FLAG, value);
const ofGate = (...gates) => NAMES.filter((n) => gates.includes(CASES[n][2]));
const AUDIT_NAMES = NAMES.filter((n) => CASES[n][0] === 'auditLog');

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ سندها', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ سندها' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  for (const role of ['manager', 'staff', 'cashier', 'picker']) {
    const inv = await h.post('/api/shop/invite', { role }, { token: T.owner.accessToken });
    assert.equal(inv.status, 201, `${role}: ${JSON.stringify(inv.body)}`);
    const u = await h.newUser(`کارمندِ ${role}`, 'shop');
    const joined = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
    assert.ok([200, 201].includes(joined.status), `${role}: ${JSON.stringify(joined.body)}`);
    T[role] = await h.signIn(u, 'shop');
  }
});
test.after(async () => { await h.stop(); });

test('پرچم خاموش (پیش‌فرض، و دکانِ `false` با `all` ِ روشن) ⇒ همان امروز: همه می‌نشیند، پاسخ همان شکل', async () => {
  const plain = await push3('cashier', [v3op('auditLog', 'off-sale-a', { type: 'sale', refId: 'off-sale', notes: 'ثبت فروش' })]);
  const keys = Object.keys(plain.body.results[0]).sort();
  for (const [tag, setup] of [['off0', null], ['off1', async () => { await setFlag('all', true); await setFlag(shopId, false); }]]) {
    if (setup) await setup();
    const c = await expectEach('cashier', NAMES, `${tag}c`, 'applied');
    for (const x of c.body.results) assert.deepEqual(Object.keys(x).sort(), keys);
    await expectEach('picker', AUDIT_NAMES, `${tag}p`, 'applied');
    await expectEach('staff', ofGate('places'), `${tag}s`, 'applied');
  }
  await live.remove('all', docRoles.FLAG);
});

test('روشن: هر نوع برای نقشی که برنامه اجازه می‌دهد می‌نشیند', async () => {
  await setFlag(shopId, true);
  await expectEach('cashier', ofGate('sell', 'back', 'places', 'open'), 'ok-c', 'applied');
  await expectEach('manager', ofGate('cancel', 'void'), 'ok-m', 'applied');
  //  بی سنجش در برنامه ⇒ جمع‌آور (فقط سابقه می‌نویسد) هم
  await expectEach('picker', ofGate('open').filter((n) => AUDIT_NAMES.includes(n)), 'ok-p', 'applied');
  await expectEach('staff', ofGate('sell', 'back', 'open'), 'ok-s', 'applied');
  assert.equal((await inV3('auditLog', 'ok-m-gift_card_void')).reason, 'کارتِ گم‌شده');
});

test('روشن: صاحب دکان هر نوع را می‌نشاند', async () => {
  await expectEach('owner', NAMES, 'ok-o', 'applied');
});

test('روشن: صندوق‌دار بی اجازهٔ لغو ⇒ باطلِ کارت، لغوِ امانی/سفارش و بردِ بازپرداختشان رد', async () => {
  await expectEach('cashier', ofGate('cancel', 'void'), 'no-c', 'doc_not_allowed');
  for (const n of ofGate('cancel', 'void')) assert.equal(await inV3(CASES[n][0], `no-c-${n.replace(':', '-')}`), undefined, n);
});

test('روشن: فروش، محل و برگشت فقط برای نقشی که برنامه اجازه می‌دهد', async () => {
  //  جمع‌آور `sales` نمی‌نویسد ⇒ فروش و شارژ و خرجِ کارت، امتیاز، تحویل؛ و نه برگشت
  await expectEach('picker', ofGate('sell', 'back', 'cancel', 'void').filter((n) => AUDIT_NAMES.includes(n)), 'no-p', 'doc_not_allowed');
  //  شاگرد محل و بسته‌بندی ندارد (`StockPlacesEntry`)
  await expectEach('staff', ofGate('places'), 'no-s', 'doc_not_allowed');
  //  صندوق‌دار با برگشتِ «ممنوع» (و بی لغو) ⇒ برگشت به کارت و برگشتِ امتیاز رد؛ «با تأیید» (PIN) ⇒ می‌نشیند
  await staffPerms.setRoleLayer(shopId, 'cashier', { 'returns.create': { mode: 'deny' } }, 'test');
  await expectEach('cashier', ofGate('back'), 'no-cr', 'doc_not_allowed');
  await staffPerms.setRoleLayer(shopId, 'cashier', { 'returns.create': { mode: 'approve' } }, 'test');
  await expectEach('cashier', ofGate('back'), 'ok-cr', 'applied');
  await staffPerms.setRoleLayer(shopId, 'cashier', {}, 'test');
  //  مدیرِ تنگ‌شده (لغو ممنوع) ⇒ باطل و لغو رد — لایه‌ها همان `policy` ِ برنامه
  await staffPerms.setRoleLayer(shopId, 'manager', { 'sales.cancel': { mode: 'deny' } }, 'test');
  await expectEach('manager', ofGate('cancel', 'void'), 'no-m', 'doc_not_allowed');
  await staffPerms.setRoleLayer(shopId, 'manager', {}, 'test');
  await expectEach('manager', ofGate('cancel', 'void'), 'ok-m2', 'applied');
});

test('روشن: شکلِ بد برای همه رد، صاحب دکان هم؛ نوعِ دیگرِ سابقه دست نمی‌خورد', async () => {
  for (const who of ['owner', 'cashier']) {
    const r = await push3(who, [
      v3op('auditLog', `bad-${who}-1`, { type: 'gift_card_issue', payRef: CODE, amountMinor: 0 }),
      v3op('auditLog', `bad-${who}-2`, { type: 'gift_card_redeem', payRef: CODE, amountMinor: 1000 }),
      v3op('auditLog', `bad-${who}-3`, { type: 'gift_card_mint', payRef: CODE, amountMinor: 1000 }),
      v3op('auditLog', `bad-${who}-4`, { type: 'loyalty_earn', refId: 's', payRef: 'd', points: 0, amountMinor: 0 }),
      v3op('transactions', `bad-${who}-5`, { debtorId: 'd', type: 'receive', amountMinor: 100, kind: 'layaway_installment' }),
      v3op('auditLog', `bad-${who}-6`, { type: 'sale', refId: 's', notes: 'ثبت فروش' }),
    ]);
    assert.deepEqual(results(r), ['doc_invalid', 'doc_invalid', 'doc_invalid', 'doc_invalid', 'doc_invalid', 'applied'], `${who}: ${JSON.stringify(r.body)}`);
  }
});

test('روشن: وضعیتِ پرداخت (PAY-07) — صندوق‌دار فقط تأیید و «نیازمند بررسی» ِ ردیفِ منتظر؛ اصلاحِ تأییدشده، ناموفق، لغو و حلِ بررسی فقط صاحب و مدیر', async () => {
  await setFlag(shopId, true);
  const sell = ['pay_status:confirmed', 'pay_status:needs_review'];
  const manage = ['pay_status:flag', 'pay_status:fail', 'pay_status:void', 'pay_status:failed', 'pay_status:cancelled',
    'pay_status:resolved', 'pay_status:review_failed'];
  await expectEach('cashier', sell, 'ps-c', 'applied');
  await expectEach('cashier', manage, 'ps-cx', 'doc_not_allowed');
  await expectEach('staff', manage, 'ps-sx', 'doc_not_allowed');
  await expectEach('picker', sell, 'ps-px', 'doc_not_allowed');
  await expectEach('manager', [...sell, ...manage], 'ps-m', 'applied');
  await expectEach('owner', manage, 'ps-o', 'applied');
  const row = await inV3('auditLog', 'ps-m-pay_status-resolved');
  assert.equal(row.toStatus, 'confirmed');
  assert.equal(row.fromStatus, 'needs_review');
  assert.equal(row.payRow, 1);
  //  نوشته‌نشده: ردیفی از صندوق‌دار برای «ناموفق» نماند
  assert.equal(await inV3('auditLog', 'ps-cx-pay_status-failed'), undefined);
});

test('روشن: ردِ یک ردیف کلِ واحدِ tx را برمی‌گرداند (وضعیتِ لغو + بردِ بازپرداخت)', async () => {
  const r = await push3('cashier', [
    opOf('layaway_status:cancelled', 'tx-c', 'tx.lw-x'),
    opOf('layaway_refund', 'tx-c', 'tx.lw-x'),
  ]);
  assert.deepEqual(results(r), ['doc_not_allowed', 'tx_rejected'], JSON.stringify(r.body));
  assert.equal(await inV3('transactions', 'tx-c-layaway_refund'), undefined);
});

test('روشن: درجِ دوبارهٔ ردیفی که پیش‌تر نشسته داوری نمی‌شود (سیاستِ log بی‌اثرش می‌کند)', async () => {
  const fields = CASES.gift_card_void[1]('ok-m-gift_card_void');
  const r = await push3('cashier', [v3op('auditLog', 'ok-m-gift_card_void', fields)]);
  assert.deepEqual(results(r), ['applied'], JSON.stringify(r.body));
});

test('روشن: فاکتورِ نقدی با ردیفِ جدای گردکردنِ نقدی (PAY-09، `roundingMinor`) داوری نمی‌شود و برای صندوق‌دار می‌نشیند', async () => {
  //  گرد فیلدِ خودِ فاکتور است (`Sale.roundingMinor`)، نه ردیفِ سابقه یا رسید ⇒ هیچ خانواده‌ای از این داوری نیست
  const sale = { invoiceNumber: 7001, totalMinor: 12_300, finalTotalMinor: 12_500, roundingMinor: 200, paymentMethod: 'cash', status: 'completed', date: '2026-11-02' };
  for (const roundingMinor of [200, -300, 500]) assert.equal(docRoles.needOf('sales', { ...sale, roundingMinor }), null);
  assert.equal(docRoles.needOf('auditLog', { type: 'sale', refId: 'rounded-sale', notes: 'ثبت فروش', amount: 125 }), null);
  const r = await push3('cashier', [
    v3op('sales', 'rounded-sale', sale, 'tx.rounded'),
    v3op('auditLog', 'rounded-sale-a', { type: 'sale', refId: 'rounded-sale', notes: 'ثبت فروش' }, 'tx.rounded'),
  ]);
  assert.deepEqual(results(r), ['applied', 'applied'], JSON.stringify(r.body));
  assert.equal((await inV3('sales', 'rounded-sale')).roundingMinor, 200);
});

test('v1 (بی تغییر، `sync-v1.js` مشترک با پمپ): پرچمِ روشن هم این ردیف‌ها را فقط با `canWrite` ِ جدول می‌نشاند', async () => {
  const v1 = (rowId, fields) => ({ op_id: oid(), table: 'auditLog', row_id: rowId, type: 'insert', ts: Date.now(), fields });
  const r = await push1('cashier', [v1('v1-void', { type: 'gift_card_void', payRef: CODE, amount: 0, reason: 'کارتِ گم‌شده' })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(results(r), ['applied'], JSON.stringify(r.body));
});
