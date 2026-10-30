'use strict';
/**
 * نقش‌های تازهٔ کارمند — STF-01 (بخشِ ۱۰.۱)، بستهٔ A16:
 * صندوق‌دار (cashier)، انباردار (storekeeper)، جمع‌آور (picker) و حسابدار
 * (accountant، فقط‌خواندنی).
 *
 * از راهِ HTTPِ واقعی: صاحب دکان کدِ دعوت با نقشِ تازه می‌سازد، کارمند با آن
 * می‌پیوندد و همان توکن به درهای واقعی می‌خورد. سه چیز سنجیده می‌شود:
 *   ۱. درهای `/api/shop` (MATRIX) برای هر نقش؛
 *   ۲. هر سه راهِ همگام‌سازی (v1 با `v1Guard`، v3 با `authorize`، راهِ قدیمِ
 *      `/api/sync` و `/api/data`) جدولی را که نقش نمی‌نویسد رد می‌کنند و
 *      همراه‌های همان سند را هم با آن برمی‌گردانند؛
 *   ۳. آزمونِ مرجع: صاحب، مدیر و شاگرد همان امروزند.
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const { permissionsOf } = require('../src/lib/permissions');

const NEW_ROLES = ['cashier', 'storekeeper', 'picker', 'accountant'];
/** نقشی که در فیلدِ قدیمِ `role` می‌رود: نسخه‌های قدیمِ برنامه نقشِ ناشناخته را «مدیر» می‌پنداشتند */
const LEGACY = { owner: 'owner', manager: 'manager', staff: 'staff', cashier: 'staff', storekeeper: 'staff', picker: 'staff', accountant: 'staff' };

let admin = '';
let shopId = '';
const T = {};                                      //  نقش ⇒ نشستِ واقعی
let n = 0;
const oid = () => `01JRL${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const push1 = (role, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[role].accessToken, body: { device_id: `r1-${role}`, schema_version: 1, ops },
});
const push3 = (role, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[role].accessToken, body: { device_id: `r3-${role}`, protocol: 3, ops },
});
const v1op = (table, rowId, fields, type = 'insert') => ({ op_id: oid(), table, row_id: rowId, type, ts: Date.now(), fields });
const v3op = (role, table, rowId, fields, tx = '') => ({
  op_id: oid(), hlc: H(role), table, row_id: rowId, kind: 'insert', fields, ...(tx ? { tx } : {}),
});
const reasons = (r) => r.body.results.map((x) => x.reason || x.status);
const inOplog = async (opId) => !!(await one('SELECT 1 FROM oplog WHERE op_id=$1', [opId]));
const inV3 = async (rowId) => !!(await one(
  `SELECT 1 FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND row_id=$2`, [shopId, rowId]
));

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'roles-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'roles-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیرِ پنل');

  const o = await h.newUser('صاحبِ دکانِ نقش‌ها', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ نقش‌ها' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  //  شاگرد با کدِ ثابت و مدیر و چهار نقشِ تازه با کدِ دعوت — همان راهِ برنامه
  const standing = (await h.get('/api/shop/staff-code', { token: T.owner.accessToken })).body.code;
  for (const role of ['staff', 'manager', ...NEW_ROLES]) {
    let code = standing;
    if (role !== 'staff') {
      const inv = await h.post('/api/shop/invite', { role }, { token: T.owner.accessToken });
      assert.equal(inv.status, 201, `${role}: ${JSON.stringify(inv.body)}`);
      assert.equal(inv.body.role, role);
      code = inv.body.code;
    }
    const u = await h.newUser(`کارمندِ ${role}`, 'shop');
    const joined = await h.post('/api/shop/join', { code }, { token: u.accessToken });
    assert.ok([200, 201].includes(joined.status), `${role}: ${JSON.stringify(joined.body)}`);
    assert.equal(joined.body.role, LEGACY[role], `${role} join legacy`);
    assert.equal(joined.body.actualRole, role, `${role} join actual`);
    T[role] = await h.signIn(u, 'shop');
  }
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------- نقش و مجوزها */

test('هر نقشِ تازه با کدِ دعوت می‌پیوندد؛ فیلدِ قدیمِ role برای نسخهٔ قدیم «staff» و نقشِ واقعی در actualRole', async () => {
  for (const role of ['owner', 'manager', 'staff', ...NEW_ROLES]) {
    const shop = await h.get('/api/shop', { token: T[role].accessToken });
    assert.equal(shop.status, 200, role);
    assert.equal(shop.body.role, LEGACY[role], role);
    assert.equal(shop.body.actualRole, role, role);
    assert.equal(shop.body.isOwner, role === 'owner');
    assert.deepEqual(shop.body.permissions, permissionsOf(role), role);
    assert.equal(shop.body.policy.role, role);
    //  /api/me و پاسخِ ورود هم همین‌طور
    const me = await h.get('/api/me', { token: T[role].accessToken });
    assert.equal(me.body.shop.role, LEGACY[role], `me ${role}`);
    assert.equal(me.body.shop.actualRole, role, `me ${role}`);
    assert.equal(T[role].shop.role, LEGACY[role], `login ${role}`);
    assert.equal(T[role].shop.actualRole, role, `login ${role}`);
    const perms = await h.get('/api/shop/permissions', { token: T[role].accessToken });
    assert.equal(perms.body.role, LEGACY[role], `permissions ${role}`);
    assert.equal(perms.body.actualRole, role, `permissions ${role}`);
  }
  //  آزمونِ مرجع: سه نقشِ قدیم دقیقاً همان فهرستِ پیش از A16
  assert.deepEqual(permissionsOf('staff'), ['shop.view', 'subscription.view', 'data.read', 'data.write', 'data.delete.own',
    'stock.view', 'stock.move', 'reports.view', 'orders.view', 'orders.create', 'orders.manage', 'delivery.view',
    'delivery.handover', 'chat.view', 'chat.reply']);
  assert.deepEqual(permissionsOf('manager'), ['shop.view', 'members.view', 'staffcode.view', 'subscription.view',
    'subscription.request', 'data.read', 'data.write', 'data.delete.any', 'data.delete.own', 'sales.discount', 'sales.cancel',
    'settings.write', 'stock.view', 'stock.move', 'stock.adjust', 'reports.view', 'audit.view', 'orders.view', 'orders.create',
    'orders.manage', 'orders.contact', 'orders.refund', 'delivery.view', 'delivery.assign', 'delivery.handover',
    'delivery.couriers', 'delivery.settle', 'delivery.photo', 'chat.view', 'chat.reply', 'chat.moderate',
    'trust.view', 'trust.respond']);
  assert.equal(permissionsOf('owner').length, 40);
  //  پرونده‌های اعتماد و شکایتِ بازارچه (main، §۳۸.۶) فقط صاحب و مدیر — هیچ نقشِ تازه
  for (const role of NEW_ROLES) {
    assert.ok(!permissionsOf(role).some((k) => k.startsWith('trust.')), role);
  }
  //  حسابدار عددِ مالی و سابقه را می‌بیند؛ بقیهٔ تازه‌ها نه
  const acc = (await h.get('/api/shop', { token: T.accountant.accessToken })).body.policy.effective;
  assert.equal(acc['reports.money'].mode, 'allow');
  assert.equal(acc['audit.view'].mode, 'allow');
  assert.equal(acc['settings.write'].mode, 'deny');
  const csh = (await h.get('/api/shop', { token: T.cashier.accessToken })).body.policy.effective;
  assert.equal(csh['reports.money'].mode, 'deny');
  assert.equal(csh['returns.create'].mode, 'allow');
});

test('درهای /api/shop: مدیریتِ کارمند، تنظیمات و سابقه برای هر نقشِ تازه', async () => {
  const mid = (await h.get('/api/shop/members', { token: T.owner.accessToken })).body.members
    .find((m) => m.role === 'staff').id;
  for (const role of NEW_ROLES) {
    const tok = T[role].accessToken;
    assert.equal((await h.get('/api/shop/members', { token: tok })).status, 403, `${role} members`);
    assert.equal((await h.put('/api/shop/settings', { settings: { a: 1 } }, { token: tok })).status, 403, `${role} settings`);
    assert.equal((await h.post('/api/shop/invite', { role: 'staff' }, { token: tok })).status, 403, `${role} invite`);
    assert.equal((await h.patch(`/api/shop/members/${mid}`, { role: 'cashier' }, { token: tok })).status, 403, `${role} role`);
    const audit = await h.get('/api/shop/audit', { token: tok });
    assert.equal(audit.status, role === 'accountant' ? 200 : 403, `${role} audit`);
  }
  //  صاحب نقشِ عضو را به نقشِ تازه می‌برد و برمی‌گرداند
  const to = await h.patch(`/api/shop/members/${mid}`, { role: 'cashier' }, { token: T.owner.accessToken });
  assert.equal(to.status, 200, JSON.stringify(to.body));
  assert.equal(to.body.member.role, 'cashier');
  assert.equal((await h.get('/api/shop', { token: T.staff.accessToken })).body.actualRole, 'cashier');
  //  برنامهٔ نصبی همان در را با PUT می‌زند (HttpURLConnection PATCH ندارد)
  const viaPut = await h.put(`/api/shop/members/${mid}`, { role: 'accountant' }, { token: T.owner.accessToken });
  assert.equal(viaPut.status, 200, JSON.stringify(viaPut.body));
  assert.equal(viaPut.body.member.role, 'accountant');
  assert.equal((await h.put(`/api/shop/members/${mid}`, { role: 'cashier' }, { token: T.cashier.accessToken })).status, 403);
  assert.equal((await h.patch(`/api/shop/members/${mid}`, { role: 'staff' }, { token: T.owner.accessToken })).body.member.role, 'staff');
  //  نقشِ ناشناخته و «صاحب» پذیرفته نیست
  assert.equal((await h.patch(`/api/shop/members/${mid}`, { role: 'owner' }, { token: T.owner.accessToken })).status, 400);
  assert.equal((await h.post('/api/shop/invite', { role: 'janitor' }, { token: T.owner.accessToken })).status, 400);
});

test('لایهٔ نقشِ تازه: صاحب می‌بندد، ولی بیش از نقش نمی‌دهد', async () => {
  const put = (role, perms) => h.put(`/api/shop/permissions/roles/${role}`, { perms }, { token: T.owner.accessToken });
  const ok = await put('accountant', { 'reports.money': { mode: 'deny' } });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await h.get('/api/shop', { token: T.accountant.accessToken })).body.policy.effective['reports.money'].mode, 'deny');
  const over = await put('storekeeper', { 'settings.write': { mode: 'allow' } });
  assert.equal(over.status, 400);
  assert.equal(over.body.error.code, 'exceeds_role');
  const cashApprove = await put('cashier', { 'sales.discount': { mode: 'approve' } });
  assert.equal(cashApprove.status, 200, JSON.stringify(cashApprove.body));
  const page = (await h.get('/api/shop/permissions', { token: T.owner.accessToken })).body.shop;
  for (const role of NEW_ROLES) assert.ok(role in page.roles, role);
  const entry = page.catalog.find((c) => c.key === 'audit.view');
  assert.equal(entry.defaults.accountant, 'allow');
  assert.equal(entry.defaults.cashier, 'deny');
  assert.equal((await put('accountant', {})).status, 200);
  assert.equal((await put('cashier', {})).status, 200);
});

test('کالا/انبار و سفارش: stock.move/adjust فقط انباردار، سفارش برای صندوق‌دار و جمع‌آور، حسابدار فقط می‌بیند', async () => {
  for (const role of NEW_ROLES) {
    const cfg = await h.get('/api/shop/catalog/config', { token: T[role].accessToken });
    assert.equal(cfg.status, 200, role);
    assert.deepEqual(cfg.body.permissions, { move: role === 'storekeeper', adjust: role === 'storekeeper', settings: false }, role);
  }
  const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'shop.orders', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const want = { cashier: [200, true, true], storekeeper: [403, false, false], picker: [200, false, true], accountant: [200, false, false] };
  for (const role of NEW_ROLES) {
    const tok = T[role].accessToken;
    const [view, create, manage] = want[role];
    assert.equal((await h.get('/api/shop/orders', { token: tok })).status, view, `${role} view`);
    const c = await h.post('/api/shop/orders', {}, { token: tok });
    assert.equal(c.status === 403, !create, `${role} create ${c.status}`);
    const t = await h.post('/api/shop/orders/ord_none/transition', { to: 'accepted' }, { token: tok });
    assert.equal(t.status === 403, !manage, `${role} manage ${t.status}`);
  }
});

test('سفارش: صندوق‌دار ثبت می‌کند، جمع‌آور قبول و جمع می‌کند، حسابدار فقط می‌بیند و دکمهٔ گذار ندارد', async () => {
  const body = {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 }],
  };
  const made = await h.post('/api/shop/orders', body, { token: T.cashier.accessToken, headers: { 'Idempotency-Key': 'roles-order-1' } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.order.id;
  const cardOf = async (role) => (await h.get('/api/shop/orders', { token: T[role].accessToken })).body
    .items.find((o) => o.id === id);
  assert.deepEqual((await cardOf('accountant')).next, []);
  assert.ok((await cardOf('picker')).next.includes('accepted'));
  assert.ok((await cardOf('staff')).next.includes('accepted'), 'مرجع: شاگرد همان دکمه‌ها را دارد');
  const acc = await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted' }, { token: T.accountant.accessToken });
  assert.equal(acc.status, 403);
  for (const to of ['accepted', 'picking', 'packed']) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: T.picker.accessToken });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
});

/* ------------------------------------------------------------ Sync v1 */

test('Sync v1: صندوق‌دار می‌فروشد ولی خرید و مصرف نمی‌نویسد؛ همراه‌های خریدِ ردشده هم برمی‌گردند', async () => {
  const sale = [
    v1op('sales', 's1-csh', { total: 100, discount: 0, finalTotal: 100, status: 'completed' }),
    //  ۲ عدد: حرکتِ «sale» ِ دستهٔ بعد (m3) جا دارد — هر قلم تا مقدارش (یافتهٔ ۱ ِ بازبینی، e13092d)
    v1op('saleItems', 'si1-csh', { saleId: 's1-csh', productId: 'p1', quantity: 2, unitPrice: 50 }),
    v1op('stockMovements', 'm1-csh', { productId: 'p1', type: 'sale', qty: -1, refId: 's1-csh' }),
    v1op('auditLog', 'a1-csh', { type: 'sale', refId: 's1-csh', notes: 'ثبت فروش' }),
  ];
  const ok = await push1('cashier', sale);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual(reasons(ok), ['applied', 'applied', 'applied', 'applied']);

  const buy = [
    v1op('stockMovements', 'm2-csh', { productId: 'p1', type: 'purchase', qty: 5, refId: 'pu1-csh' }),
    v1op('purchases', 'pu1-csh', { productId: 'p1', quantity: 5, totalAmount: 400 }),
    v1op('auditLog', 'a2-csh', { type: 'purchase', refId: 'pu1-csh' }),
    v1op('expenses', 'e1-csh', { amount: 10, category: 'متفرقه' }),
    v1op('stockMovements', 'm3-csh', { productId: 'p1', type: 'sale', qty: -1, refId: 's1-csh' }),
  ];
  const r = await push1('cashier', buy);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => x.op_id), buy.map((o) => o.op_id));
  //  حرکتِ «purchase» خودش هم بیرون از قاعدهٔ حرکتِ صندوق‌دار است (فقط فاکتور و برگشت)
  assert.deepEqual(reasons(r), ['table_not_allowed', 'table_not_allowed', 'tx_rejected', 'table_not_allowed', 'applied']);
  for (const o of buy.slice(0, 4)) assert.equal(await inOplog(o.op_id), false, o.table);
  assert.equal(await inOplog(buy[4].op_id), true);

  //  برگشتِ ضایعات (#318): برگشت + اصلاحِ موجودی در رسیدِ انبار + حرکتِ انبار — همه در حدِ صندوق‌دار
  const ret = [
    v1op('returns', 'r1-csh', { saleId: 's1-csh', saleItemId: 'si1-csh', productId: 'p1', quantity: 1, amount: 100, docId: 'rd1-csh' }),
    v1op('warehouseEntries', 'w1-csh', { productId: 'p1', units: -1, isAdjustment: true, adjKind: 'damage', refId: 'rd1-csh' }),
    v1op('stockMovements', 'm4-csh', { productId: 'p1', type: 'damage', qty: -1, refId: 'r1-csh' }),
  ];
  const back = await push1('cashier', ret);
  assert.equal(back.status, 200, JSON.stringify(back.body));
  assert.deepEqual(reasons(back), ['applied', 'applied', 'applied']);
  //  رسیدِ انبارِ صندوق‌دار فقط اصلاحِ موجودیِ قلمِ برگشتِ خودش است (قاعدهٔ ردیفِ هسته):
  //  رسیدِ دستی، نوعِ اصلاحِ دیگر، اشاره به برگشتی که نیست، ویرایشِ ردیفِ موجود، و اصلاحِ دوم
  //  برای قلمی که اصلاحش نشسته ⇒ رد
  const wh = [
    v1op('warehouseEntries', 'w2-csh', { productId: 'p1', units: 10, price: 50 }),
    v1op('warehouseEntries', 'w3-csh', { productId: 'p1', units: -1, isAdjustment: true, adjKind: 'count', refId: 'rd1-csh' }),
    v1op('warehouseEntries', 'w4-csh', { productId: 'p1', units: -1, isAdjustment: true, adjKind: 'damage', refId: 'rd-404' }),
    v1op('warehouseEntries', 'w1-csh', { units: -5 }, 'update'),
    v1op('warehouseEntries', 'w5-csh', { productId: 'p1', units: 1, isAdjustment: true, adjKind: 'customer_return', refId: 'rd1-csh' }),
  ];
  const whr = await push1('cashier', wh);
  assert.equal(whr.status, 200, JSON.stringify(whr.body));
  assert.deepEqual(reasons(whr), wh.map(() => 'table_not_allowed'));
  for (const o of wh) assert.equal(await inOplog(o.op_id), false, o.row_id);
  //  برگشتی که همین دستگاه در دستهٔ پیش ثبت کرد پایهٔ اصلاحِ تازه است (قلمی که هنوز اصلاح ندارد)
  const r2 = await push1('cashier', [
    v1op('returns', 'r2-csh', { saleId: 's1-csh', saleItemId: 'si1-csh', productId: 'p1', quantity: 1, amount: 0, docId: 'rd2-csh' }),
  ]);
  assert.deepEqual(reasons(r2), ['applied'], JSON.stringify(r2.body));
  const w6 = await push1('cashier', [
    v1op('warehouseEntries', 'w6-csh', { productId: 'p1', units: 1, isAdjustment: true, adjKind: 'customer_return', refId: 'rd2-csh' }),
  ]);
  assert.deepEqual(reasons(w6), ['applied'], JSON.stringify(w6.body));

  //  ولی انباردار برگشت نمی‌زند
  const sk = await push1('storekeeper', [
    v1op('returns', 'r1-stk', { saleId: 's1-csh', saleItemId: 'si1-csh', productId: 'p1', quantity: 1, amount: 100 }),
  ]);
  assert.deepEqual(reasons(sk), ['table_not_allowed']);
});

test('Sync v1: انباردار کالا و خرید می‌نویسد ولی فروش نه؛ جمع‌آور فقط سابقه؛ حسابدار هیچ (op به op، نه ۴۰۳ِ کلِ دسته)', async () => {
  const goods = await push1('storekeeper', [
    v1op('products', 'p-stk', { name: 'برنج', salePrice: 50 }),
    v1op('purchases', 'pu-stk', { productId: 'p-stk', quantity: 10, totalAmount: 300 }),
    v1op('stockMovements', 'm-stk', { productId: 'p-stk', type: 'purchase', qty: 10, refId: 'pu-stk' }),
  ]);
  assert.deepEqual(reasons(goods), ['applied', 'applied', 'applied'], JSON.stringify(goods.body));

  const sale = [
    v1op('saleItems', 'si-stk', { saleId: 's-stk', productId: 'p-stk', quantity: 1, unitPrice: 50 }),
    v1op('stockMovements', 'm2-stk', { productId: 'p-stk', type: 'sale', qty: -1, refId: 's-stk' }),
    v1op('sales', 's-stk', { total: 50, discount: 0, finalTotal: 50, status: 'completed' }),
    v1op('transactions', 't-stk', { amount: 50, saleId: 's-stk' }),
  ];
  const r = await push1('storekeeper', sale);
  assert.deepEqual(reasons(r), ['table_not_allowed', 'tx_rejected', 'table_not_allowed', 'table_not_allowed'], JSON.stringify(r.body));
  for (const o of sale) assert.equal(await inOplog(o.op_id), false, o.table);

  const pick = await push1('picker', [
    v1op('auditLog', 'a-pck', { type: 'order', notes: 'جمع شد' }),
    v1op('products', 'p-pck', { name: 'x' }),
  ]);
  assert.deepEqual(reasons(pick), ['applied', 'table_not_allowed']);

  //  ۴۰۳ِ کلِ دسته دستگاهِ حسابدار را از Pull هم می‌انداخت (opِ ماندهٔ پیش از تغییرِ نقش)؛
  //  حالا هر op جدا «table_not_allowed» می‌گیرد و دستگاه آن را از صف بیرون می‌اندازد
  const accOps = [v1op('auditLog', 'a-acc', { type: 'x' }), v1op('sales', 's-acc', { total: 1, discount: 0, finalTotal: 1, status: 'completed' })];
  const acc = await push1('accountant', accOps);
  assert.equal(acc.status, 200, JSON.stringify(acc.body));
  assert.deepEqual(reasons(acc), ['table_not_allowed', 'table_not_allowed']);
  for (const o of accOps) assert.equal(await inOplog(o.op_id), false, o.table);
  //  ولی می‌خواند
  const pull = await h.api('GET', '/api/sync/v1/pull?device_id=r1-accountant&since=0', {
    token: T.accountant.accessToken, headers: { 'X-App': 'shop' },
  });
  assert.equal(pull.status, 200, JSON.stringify(pull.body));
});

test('Sync v1 آزمونِ مرجع: شاگرد و مدیر هنوز هر جدولی را می‌نویسند (A16 چیزی از آن‌ها نگرفته)', async () => {
  for (const role of ['staff', 'manager']) {
    const r = await push1(role, [
      v1op('purchases', `pu-${role}`, { productId: 'p1', quantity: 1, totalAmount: 10 }),
      v1op('expenses', `e-${role}`, { amount: 5, category: 'متفرقه' }),
      v1op('products', `p-${role}`, { name: 'x' }),
      v1op('sales', `s-${role}`, { total: 10, discount: 0, finalTotal: 10, status: 'completed' }),
    ]);
    assert.deepEqual(reasons(r), ['applied', 'applied', 'applied', 'applied'], `${role}: ${JSON.stringify(r.body)}`);
  }
});

/* ------------------------------------------------------------ Sync v3 */

test('Sync v3: جدولِ بیرون از نقش کلِ tx را برمی‌گرداند؛ درونِ نقش می‌نشیند', async () => {
  //  صندوق‌دار: خرید ⇒ رد با همراهش؛ فروش ⇒ می‌نشیند
  const buy = [
    v3op('c', 'purchases', 'pu3-csh', { productId: 'p1', quantityMilli: 1000, totalAmountMinor: 4000 }, 'tx-pu3'),
    v3op('c', 'stockMovements', 'm3-pu-csh', { productId: 'p1', qtyMilli: 1000, type: 'purchase', refId: 'pu3-csh' }, 'tx-pu3'),
  ];
  const sale = [
    v3op('c', 'sales', 's3-csh', { totalMinor: 2000, status: 'completed' }, 'tx-s3'),
    v3op('c', 'saleItems', 'si3-csh', { saleId: 's3-csh', productId: 'p1', quantityMilli: 1000 }, 'tx-s3'),
    v3op('c', 'stockMovements', 'm3-s-csh', { productId: 'p1', qtyMilli: -1000, type: 'sale', refId: 's3-csh' }, 'tx-s3'),
  ];
  const r = await push3('cashier', [...buy, ...sale]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(reasons(r), ['table_not_allowed', 'tx_rejected', 'applied', 'applied', 'applied']);
  assert.equal(await inV3('pu3-csh'), false);
  assert.equal(await inV3('m3-pu-csh'), false);
  assert.equal(await inV3('s3-csh'), true);

  //  انباردار: فروش ⇒ رد؛ کالا ⇒ می‌نشیند
  const s = await push3('storekeeper', [
    v3op('k', 'stockMovements', 'm3-stk', { productId: 'p1', qtyMilli: -1000, type: 'sale' }, 'tx-stk'),
    v3op('k', 'sales', 's3-stk', { totalMinor: 1000 }, 'tx-stk'),
    v3op('k', 'products', 'p3-stk', { name: 'روغن', salePriceMinor: 9000 }),
  ]);
  assert.deepEqual(reasons(s), ['tx_rejected', 'table_not_allowed', 'applied'], JSON.stringify(s.body));
  assert.equal(await inV3('m3-stk'), false);
  assert.equal(await inV3('p3-stk'), true);

  //  رسیدِ انبارِ صندوق‌دار در v3: اصلاحِ برگشتِ همین tx پذیرفته، رسیدِ دستی کلِ tx ِ خودش را برمی‌گرداند
  const ret3 = await push3('cashier', [
    v3op('c', 'warehouseEntries', 'w3-ret-csh', { productId: 'p1', unitsMilli: -1000, isAdjustment: true, adjKind: 'damage', refId: 'rd3-csh' }, 'tx-r3'),
    v3op('c', 'returns', 'r3-csh', { saleId: 's3-csh', saleItemId: 'si3-csh', productId: 'p1', quantityMilli: 1000, amountMinor: 2000, docId: 'rd3-csh' }, 'tx-r3'),
    v3op('c', 'returns', 'r3b-csh', { saleId: 's3-csh', saleItemId: 'si3-csh', productId: 'p1', quantityMilli: 1000, amountMinor: 0, docId: 'rd3-csh' }, 'tx-r3'),
  ]);
  assert.deepEqual(reasons(ret3), ['applied', 'applied', 'applied'], JSON.stringify(ret3.body));
  const man3 = await push3('cashier', [
    v3op('c', 'warehouseEntries', 'w3-man-csh', { productId: 'p1', unitsMilli: 5000, priceMinor: 100 }, 'tx-m3'),
    v3op('c', 'stockMovements', 'm3-man-csh', { productId: 'p1', qtyMilli: 5000, type: 'purchase' }, 'tx-m3'),
  ]);
  assert.deepEqual(reasons(man3), ['table_not_allowed', 'tx_rejected'], JSON.stringify(man3.body));
  assert.equal(await inV3('w3-man-csh'), false);
  //  برگشتِ همین دستگاه از دستهٔ پیش هم نشانیِ درست است — ولی هر قلم یک اصلاح: r3-csh اصلاحش را
  //  دارد، r3b-csh نه
  const later3 = await push3('cashier', [
    v3op('c', 'warehouseEntries', 'w3-later-csh', { productId: 'p1', unitsMilli: 1000, isAdjustment: true, adjKind: 'customer_return', refId: 'r3-csh' }),
    v3op('c', 'warehouseEntries', 'w3-later2-csh', { productId: 'p1', unitsMilli: 1000, isAdjustment: true, adjKind: 'customer_return', refId: 'r3b-csh' }),
  ]);
  assert.deepEqual(reasons(later3), ['table_not_allowed', 'applied'], JSON.stringify(later3.body));

  const p = await push3('picker', [v3op('p', 'products', 'p3-pck', { name: 'x' })]);
  assert.deepEqual(reasons(p), ['table_not_allowed']);

  const a = await push3('accountant', [v3op('a', 'auditLog', 'a3-acc', { type: 'x' })]);
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.deepEqual(reasons(a), ['table_not_allowed']);
  assert.equal(await inV3('a3-acc'), false);

  //  مرجع: شاگرد هنوز خرید می‌نویسد
  const st = await push3('staff', [v3op('s', 'purchases', 'pu3-stf', { productId: 'p1', quantityMilli: 1000, totalAmountMinor: 100 })]);
  assert.deepEqual(reasons(st), ['applied'], JSON.stringify(st.body));
});

/* ------------------------------------------- راهِ قدیم: /api/sync و /api/data */

test('راهِ قدیم: /api/sync و درهای دفترِ /api/* (routes/data) همان قاعده را دارند، همه یا هیچ', async () => {
  const legacy = (role, changes) => h.post('/api/sync/push', { deviceId: `lg-${role}`, changes }, { token: T[role].accessToken });
  const mixed = [
    { collection: 'sales', id: 'lg-s1', updatedAt: Date.now(), data: { total: 10 } },
    { collection: 'purchases', id: 'lg-pu1', updatedAt: Date.now(), data: { totalAmount: 10 } },
  ];
  const r = await legacy('cashier', mixed);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'table_not_allowed');
  assert.equal(await one(`SELECT 1 FROM sales WHERE shop_id=$1 AND id='lg-s1'`, [shopId]), null);
  assert.equal((await legacy('cashier', mixed.slice(0, 1))).status, 200);
  assert.equal((await legacy('staff', [{ ...mixed[1], id: 'lg-pu2' }])).status, 200);

  const full = (role, id) => h.post('/api/sales/full', {
    sale: { id, total: 10, finalTotal: 10 }, items: [{ productId: 'p1', quantity: 1, unitPrice: 10 }],
    stockMovements: [{ productId: 'p1', qty: -1, type: 'sale' }],
  }, { token: T[role].accessToken });
  assert.equal((await full('cashier', 'lg-full-1')).status, 201);
  const bad = await full('storekeeper', 'lg-full-2');
  assert.equal(bad.status, 403);
  assert.equal(bad.body.error.code, 'table_not_allowed');
  assert.equal(await one(`SELECT 1 FROM sales WHERE shop_id=$1 AND id='lg-full-2'`, [shopId]), null);
  assert.equal((await h.post('/api/expenses', { data: { amount: 1 } }, { token: T.cashier.accessToken })).status, 403);
  //  رسیدِ انبارِ دستیِ صندوق‌دار از راهِ قدیم هم بسته است؛ اصلاحِ یک برگشتِ همین دسته نه
  const inv = await h.post('/api/inventory', { data: { productId: 'p1', units: 10 } }, { token: T.cashier.accessToken });
  assert.equal(inv.status, 403);
  assert.equal(inv.body.error.code, 'table_not_allowed');
  const lgRet = await legacy('cashier', [
    { collection: 'returns', id: 'lg-r1', updatedAt: Date.now(), data: { saleId: 'lg-s1', productId: 'p1', quantity: 1, amount: 10, docId: 'lg-rd1' } },
    { collection: 'warehouseEntries', id: 'lg-w1', updatedAt: Date.now(), data: { productId: 'p1', units: -1, isAdjustment: true, adjKind: 'damage', refId: 'lg-rd1' } },
    { collection: 'stockMovements', id: 'lg-m1', updatedAt: Date.now(), data: { productId: 'p1', qty: -1, type: 'damage', refId: 'lg-r1' } },
  ]);
  assert.equal(lgRet.status, 200, JSON.stringify(lgRet.body));
  const acc = await h.post('/api/products', { data: { name: 'x' } }, { token: T.accountant.accessToken });
  assert.equal(acc.status, 403);
  assert.equal((await h.get('/api/products', { token: T.accountant.accessToken })).status, 200);
});

/* ---------------------------------------- نوشتن بیرون از همگام‌سازی */

test('عکسِ کالا، صندوقِ عکس، بانکِ تصویر و پشتیبانِ حساب فقط در حدِ نقش', async () => {
  //  PNGِ ۱×۱ (سرور عکسِ کمتر از ۱۰۰ بایت را «خالی» می‌داند ⇒ دنباله‌ای از صفر)
  const png = Buffer.concat([Buffer.from('89504e470d0a1a0a0000000d4948445200000001000000010806000000'
    + '1f15c4890000000d49444154789c63f8ffff3f0005fe02fea7d6a5d60000000049454e44ae426082', 'hex'), Buffer.alloc(160)])
    .toString('base64');
  const photo = (role, id) => h.api('PUT', `/api/sync/v1/photos/${id}`, {
    token: T[role].accessToken, headers: { 'X-App': 'shop' }, body: { data: png, device_id: `ph-${role}` },
  });
  assert.equal((await photo('storekeeper', 'p-ph-stk')).status, 200);
  assert.equal((await photo('staff', 'p-ph-stf')).status, 200, 'مرجع: شاگرد همان امروز');
  for (const role of ['cashier', 'picker']) {
    const r = await photo(role, `p-ph-${role}`);
    assert.equal(r.status, 403, role);
    assert.equal(r.body.error.code, 'table_not_allowed', role);
    const del = await h.api('DELETE', '/api/sync/v1/photos/p-ph-stk', { token: T[role].accessToken, headers: { 'X-App': 'shop' } });
    assert.equal(del.status, 403, `${role} delete`);
    const inbox = await h.api('POST', '/api/sync/v1/photo-inbox', {
      token: T[role].accessToken, headers: { 'X-App': 'shop' }, body: { data: png, pair: 'x', device_id: 'ph' },
    });
    assert.equal(inbox.status, 403, `${role} inbox`);
  }
  assert.equal((await photo('accountant', 'p-ph-acc')).status, 403);

  //  بانکِ تصویر: نوشتن یعنی کالا
  const flag = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'shop.image_bank', value: true }, { token: admin });
  assert.equal(flag.status, 200, JSON.stringify(flag.body));
  for (const role of ['cashier', 'picker']) {
    const r = await h.post('/api/shop/images/select', {}, { token: T[role].accessToken });
    assert.equal(r.status, 403, role);
    assert.equal(r.body.error.code, 'table_not_allowed', role);
  }
  const sk = await h.post('/api/shop/images/select', {}, { token: T.storekeeper.accessToken });
  assert.notEqual(sk.body?.error?.code, 'table_not_allowed');
  assert.notEqual(sk.body?.error?.code, 'permission_denied');

  //  پشتیبانِ حساب: فقط نقش‌های «*» بالا می‌فرستند؛ دیدن برای همه باز است
  const data = Buffer.from(JSON.stringify({ products: [] }));
  for (const role of NEW_ROLES) {
    const up = await h.raw('POST', '/api/me/backups?ext=json&kind=manual', data, { token: T[role].accessToken });
    assert.equal(up.status, 403, role);
    assert.equal(up.body.error.code, 'permission_denied', role);
    assert.equal((await h.get('/api/me/backups', { token: T[role].accessToken })).status, 200, role);
  }
  const ok = await h.raw('POST', '/api/me/backups?ext=json&kind=manual', data, { token: T.staff.accessToken });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
});

test('اعلانِ سفارشِ تازه فقط برای رسیدگی‌کنندهٔ سفارش؛ «باز شد»ِ حسابدار اعلانِ دکان را نمی‌بندد', async () => {
  const notify = require('../src/lib/order-notify');
  const ids = new Set(await notify.recipientIds(shopId));
  for (const role of ['owner', 'manager', 'staff', 'cashier', 'picker']) assert.ok(ids.has(T[role].user.id), role);
  for (const role of ['storekeeper', 'accountant']) assert.ok(!ids.has(T[role].user.id), role);

  const ack = (role, body) => h.post('/api/shop/order-notify/onf_none/ack', body, { token: T[role].accessToken });
  for (const body of [{ kind: 'opened' }, {}]) {
    const r = await ack('accountant', body);
    assert.equal(r.status, 403, JSON.stringify(body));
    assert.equal(r.body.error.code, 'permission_denied');
  }
  //  «رسید» فقط ثبت است و برای حسابدار هم باز است (این‌جا پرچم خاموش است)
  assert.notEqual((await ack('accountant', { kind: 'received' })).body?.error?.code, 'permission_denied');
  assert.notEqual((await ack('picker', { kind: 'opened' })).body?.error?.code, 'permission_denied');
  assert.notEqual((await ack('staff', {})).body?.error?.code, 'permission_denied');
});
