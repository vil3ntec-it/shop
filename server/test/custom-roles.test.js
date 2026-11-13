'use strict';
/**
 * STF-01 — نقشِ سفارشی (`/api/shop/custom-roles`، `lib/custom-roles.js`، پرچمِ خاموشِ `shop.custom_roles`).
 *
 * سنجه‌ها: پرچمِ خاموش همان امروز (لایهٔ سفارشیِ ذخیره‌شده هم بی‌اثر)؛ فقط صاحب می‌سازد و می‌دهد؛ هرگز گشادتر از
 * نقشِ پایه؛ اجرا از همان راه‌های امروز (Sync v3 ِ تخفیف و `requirePermission` ِ کلیدِ لایه‌پذیر)؛ ترتیبِ لایه‌ها
 * [نقش، نقشِ سفارشی، کاربر]؛ دادنِ نقشِ سفارشی نقشِ عضو را همان نقشِ پایه می‌کند؛ پاک کردن عضو را روی نقشِ پایه
 * با رفتارِ امروز می‌گذارد؛ نقش‌های پیش‌فرض دست‌نخورده؛ دکانِ دیگر هیچ.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');

const BASE = '/api/shop/custom-roles';
let admin = '';
let shopId;
let owner;
let manager;
let manager2;
let staff;
let staff2;
let ownerB;
let staffB;
let n = 0;
const oid = () => `01JCR${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

const flag = (value, scope) => h.put('/api/admin/pump/live-config', { scope, key: 'shop.custom_roles', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const call = (method, path, who, body) => { resetAll(); return h.api(method, `${BASE}${path}`, { token: who.accessToken, body }); };
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
/** فاکتورِ ۱۰٬۰۰۰ با درصدِ تخفیفِ داده‌شده */
const sale = (pct) => ({
  op_id: oid(), hlc: H('pos'), table: 'sales', row_id: `cr-s-${n}`, kind: 'insert',
  fields: { totalMinor: 1000000, discountMinor: pct * 10000, finalTotalMinor: 1000000 - pct * 10000, status: 'completed' },
});
const policyOf = async (who) => (await h.get('/api/shop/permissions', { token: who.accessToken })).body.policy;
const memberIdOf = async (who) => (await one('SELECT id FROM shop_members WHERE shop_id=$1 AND user_id=$2', [shopId, who.user.id])).id;
const roleOf = async (who, shop = shopId) => (await one('SELECT role FROM shop_members WHERE shop_id=$1 AND user_id=$2', [shop, who.user.id])).role;

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'cr-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'cr-admin', password: 'Admin!12345' })).body.token;
  const join = async (o, role) => {
    const u = await h.newUser(`${role}-${++n}`, 'shop');
    const code = role === 'staff'
      ? (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code
      : (await h.post('/api/shop/invite', { role }, { token: o.accessToken })).body.code;
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: u.accessToken })).status));
    return h.signIn(u, 'shop');
  };
  const o = await h.newUser('صاحبِ نقش', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ نقش' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(o, 'shop');
  manager = await join(owner, 'manager');
  manager2 = await join(owner, 'manager');
  staff = await join(owner, 'staff');
  staff2 = await join(owner, 'staff');
  const b = await h.newUser('صاحبِ نقشِ ب', 'shop');
  assert.equal((await h.post('/api/shop', { name: 'دکانِ نقشِ ب' }, { token: b.accessToken })).status, 201);
  ownerB = await h.signIn(b, 'shop');
  staffB = await join(ownerB, 'staff');
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): درها ۴۰۳ جز /config، پاسخِ مجوزها همان امروز، و نقشِ سفارشیِ ذخیره‌شده بی‌اثر', async () => {
  const cfg = await call('GET', '/config', owner);
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  assert.deepEqual(cfg.body.baseRoles, ['manager', 'staff', 'cashier', 'storekeeper', 'picker', 'accountant']);
  const c = await call('POST', '', owner, { name: 'ارشد', baseRole: 'manager', perms: {} });
  assert.equal(c.status, 403);
  assert.equal(c.body.error.code, 'custom_roles_disabled');
  assert.equal((await call('GET', '', owner)).body.error.code, 'custom_roles_disabled');
  //  نقشِ سفارشیِ ذخیره‌شده (پیش از خاموش کردن) هیچ اثری ندارد
  await query(`INSERT INTO shop_custom_roles (id, shop_id, name, base_role, perms, created_at, updated_at) VALUES ('crl_off',$1,'خاموش','manager',$2,$3,$3)`,
    [shopId, JSON.stringify({ 'sales.discount': { mode: 'deny' } }), now()]);
  await query(`INSERT INTO shop_member_custom_roles (shop_id, user_id, role_id, assigned_at) VALUES ($1,$2,'crl_off',$3)`, [shopId, manager.user.id, now()]);
  const p = await policyOf(manager);
  assert.equal(p.layers.length, 2);
  assert.equal(p.effective['sales.discount'].mode, 'allow');
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.equal('customRoles' in page.body.shop, false);
  assert.equal(page.body.shop.members.some((m) => 'customRole' in m), false);
  assert.deepEqual(why(await push3(manager, 'm-off', [sale(20)])), ['applied']);
  await query(`DELETE FROM shop_custom_roles WHERE id='crl_off'`);
});

test('فقط صاحب می‌سازد؛ هرگز گشادتر از نقشِ پایه؛ نامِ تکراری و نقشِ پایهٔ نادرست رد', async () => {
  await flag(true, shopId);
  assert.equal((await call('GET', '/config', owner)).body.enabled, true);
  //  مدیر می‌بیند ولی نمی‌سازد؛ شاگرد اصلاً نه
  assert.equal((await call('GET', '', manager)).status, 200);
  assert.equal((await call('POST', '', manager, { name: 'x', baseRole: 'staff' })).status, 403);
  assert.equal((await call('GET', '', staff)).status, 403);
  //  شاگرد تخفیف ندارد ⇒ «مجاز» گشادتر از نقش است
  const wide = await call('POST', '', owner, { name: 'شاگردِ تخفیف‌دار', baseRole: 'staff', perms: { 'sales.discount': { mode: 'allow' } } });
  assert.equal(wide.status, 400);
  assert.equal(wide.body.error.code, 'exceeds_role');
  assert.equal((await call('POST', '', owner, { name: 'صاحب‌نما', baseRole: 'owner' })).body.error.code, 'unknown_role');
  assert.equal((await call('POST', '', owner, { name: 'پیک‌نما', baseRole: 'courier' })).body.error.code, 'unknown_role');
  assert.equal((await call('POST', '', owner, { name: ' ', baseRole: 'staff' })).body.error.code, 'bad_role_name');
  const ok = await call('POST', '', owner, { name: 'مدیرِ کم‌تخفیف', baseRole: 'manager', perms: { 'sales.discount': { caps: { maxPct: 5 } }, 'audit.view': { mode: 'deny' } } });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.deepEqual(ok.body.role.perms, { 'sales.discount': { caps: { maxPct: 5 } }, 'audit.view': { mode: 'deny' } });
  assert.equal((await call('POST', '', owner, { name: 'مدیرِ  کم‌تخفیف', baseRole: 'staff' })).body.error.code, 'role_name_taken');
  //  نقشِ پایه ثابت است
  assert.equal((await call('PUT', `/${ok.body.role.id}`, owner, { baseRole: 'staff' })).body.error.code, 'base_role_fixed');
});

test('دادن به مدیر: همان راه‌های امروز اجرا می‌کنند (تخفیفِ v3 و `requirePermission`)؛ مدیرِ دیگر همان امروز', async () => {
  const list = await call('GET', '', owner);
  const role = list.body.roles.find((r) => r.name === 'مدیرِ کم‌تخفیف');
  const a = await call('PUT', `/members/${await memberIdOf(manager)}`, owner, { roleId: role.id });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.baseRole, 'manager');
  assert.equal(await roleOf(manager), 'manager');

  const p = await policyOf(manager);
  assert.equal(p.layers.length, 3);
  assert.deepEqual(p.effective['sales.discount'], { mode: 'allow', caps: { maxPct: 5 } });
  assert.equal(p.effective['audit.view'].mode, 'deny');
  //  تخفیفِ ۲۰٪ ⇒ «نیازمند تأیید» با سقفِ نقشِ سفارشی؛ ۵٪ می‌نشیند
  const over = sale(20);
  const r = await push3(manager, 'm-c', [over]);
  assert.deepEqual(why(r), ['approval_required']);
  assert.deepEqual(why(await push3(manager, 'm-c', [sale(5)])), ['applied']);
  //  کلیدِ لایه‌پذیرِ REST از همان `requirePermission`
  assert.equal((await h.get('/api/shop/approvals', { token: manager.accessToken })).status, 403);
  //  مدیرِ بی نقشِ سفارشی: همان امروز
  assert.deepEqual(why(await push3(manager2, 'm2', [sale(20)])), ['applied']);
  assert.equal((await h.get('/api/shop/approvals', { token: manager2.accessToken })).status, 200);
  assert.equal((await policyOf(manager2)).layers.length, 2);
  //  صفحهٔ صاحب: نقش و عضوش
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.equal(page.body.shop.customRoles.find((x) => x.id === role.id).members[0], manager.user.id);
  assert.deepEqual(page.body.shop.members.find((m) => m.userId === manager.user.id).customRole, { id: role.id, name: role.name });
});

test('ترتیبِ لایه‌ها [نقش، نقشِ سفارشی، کاربر] و نقشِ سفارشیِ شاگرد: «نیازمند تأیید» به جای «ممنوع»', async () => {
  //  لایهٔ کاربر بر نقشِ سفارشی می‌نشیند
  const mid = await memberIdOf(manager);
  assert.equal((await h.put(`/api/shop/permissions/members/${mid}`, { perms: { 'sales.discount': { caps: { maxPct: 30 } } } }, { token: owner.accessToken })).status, 200);
  assert.deepEqual((await policyOf(manager)).effective['sales.discount'], { mode: 'allow', caps: { maxPct: 30 } });
  assert.deepEqual(why(await push3(manager, 'm-c', [sale(20)])), ['applied']);
  assert.equal((await h.put(`/api/shop/permissions/members/${mid}`, { perms: {} }, { token: owner.accessToken })).status, 200);
  //  لایهٔ نقشِ «staff» زیرِ نقشِ سفارشی است: نقشِ سفارشی آن را برای همین عضو برمی‌دارد
  assert.equal((await h.roleLayer(owner.accessToken, 'staff', { 'returns.create': { mode: 'deny' } })).status, 200);
  const senior = await call('POST', '', owner, { name: 'شاگردِ ارشد', baseRole: 'staff', perms: { 'sales.discount': { mode: 'approve' }, 'returns.create': { mode: 'allow' } } });
  assert.equal(senior.status, 201, JSON.stringify(senior.body));
  assert.equal((await call('PUT', `/members/${staff.user.id}`, owner, { roleId: senior.body.role.id })).status, 200);
  const p = await policyOf(staff);
  assert.equal(p.effective['returns.create'].mode, 'allow');
  assert.equal(p.effective['sales.discount'].mode, 'approve');
  assert.equal((await policyOf(staff2)).effective['returns.create'].mode, 'deny');
  assert.deepEqual(why(await push3(staff, 's-c', [sale(10)])), ['approval_required']);
  assert.deepEqual(why(await push3(staff2, 's-2', [sale(10)])), ['discount_not_allowed']);
  assert.equal((await h.roleLayer(owner.accessToken, 'staff', {})).status, 200);
});

test('نقشِ پایهٔ دیگر نقشِ عضو را همان می‌کند؛ برداشتن و پاک کردن عضو را روی نقشِ پایه با رفتارِ امروز می‌گذارد', async () => {
  const cashier = await call('POST', '', owner, { name: 'صندوق‌دارِ صبح', baseRole: 'cashier', perms: { 'returns.create': { caps: { maxAmount: 1000 } } } });
  assert.equal(cashier.status, 201);
  const a = await call('PUT', `/members/${staff2.user.id}`, owner, { roleId: cashier.body.role.id });
  assert.equal(a.body.baseRole, 'cashier');
  assert.equal(await roleOf(staff2), 'cashier');
  const staff2Now = await h.signIn(staff2, 'shop');
  assert.deepEqual((await policyOf(staff2Now)).effective['returns.create'], { mode: 'allow', caps: { maxAmount: 1000 } });
  //  برداشتن: همان cashier بی لایهٔ سفارشی
  const un = await call('PUT', `/members/${staff2.user.id}`, owner, { roleId: null });
  assert.equal(un.status, 200);
  assert.equal(un.body.role, null);
  assert.equal(await roleOf(staff2), 'cashier');
  assert.deepEqual((await policyOf(staff2Now)).effective['returns.create'], { mode: 'allow', caps: {} });
  //  پاک کردنِ نقشِ مدیر: مدیر همان مدیرِ امروز
  const role = (await call('GET', '', owner)).body.roles.find((r) => r.name === 'مدیرِ کم‌تخفیف');
  assert.equal((await call('DELETE', `/${role.id}`, owner)).status, 200);
  assert.equal((await policyOf(manager)).layers.length, 2);
  assert.deepEqual(why(await push3(manager, 'm-c', [sale(20)])), ['applied']);
  assert.equal((await h.get('/api/shop/approvals', { token: manager.accessToken })).status, 200);
  assert.equal((await one('SELECT 1 AS x FROM shop_member_custom_roles WHERE user_id=$1', [manager.user.id])), null);
  //  صاحب و عضوِ ناشناخته نقشِ سفارشی نمی‌گیرند
  assert.equal((await call('PUT', `/members/${owner.user.id}`, owner, { roleId: cashier.body.role.id })).body.error.code, 'owner_fixed');
  assert.equal((await call('PUT', '/members/nobody', owner, { roleId: cashier.body.role.id })).status, 404);
});

test('دکانِ دیگر: نقشِ «الف» برای «ب» نیست و پرچمِ «الف» «ب» را روشن نمی‌کند', async () => {
  const roleA = (await call('GET', '', owner)).body.roles[0];
  assert.equal((await call('GET', '/config', ownerB)).body.enabled, false);
  const shopB = (await one('SELECT shop_id FROM shop_members WHERE user_id=$1', [ownerB.user.id])).shop_id;
  await flag(true, shopB);
  const list = await call('GET', '', ownerB);
  assert.deepEqual(list.body.roles, []);
  assert.equal((await call('PUT', `/${roleA.id}`, ownerB, { name: 'دزدیده' })).status, 404);
  assert.equal((await call('DELETE', `/${roleA.id}`, ownerB)).status, 404);
  assert.equal((await call('PUT', `/members/${staffB.user.id}`, ownerB, { roleId: roleA.id })).status, 404);
  //  عضوِ «الف» از «ب» دیده نمی‌شود
  assert.equal((await call('PUT', `/members/${staff.user.id}`, ownerB, { roleId: null })).status, 404);
  assert.equal((await one('SELECT name FROM shop_custom_roles WHERE id=$1', [roleA.id])).name, roleA.name);
  assert.equal(await roleOf(staffB, shopB), 'staff');
});
