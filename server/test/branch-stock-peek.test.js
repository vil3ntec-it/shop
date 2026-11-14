'use strict';
/**
 * POS-16/S17 — «موجودیِ همین کالا در شعبه‌های دیگر» از صندوق/قیمت‌پرسی (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰؛
 * `GET /api/shop/catalog/products/:id/other-branches`، مجوزِ `stock.view_other_branches` با `PUT /api/shop/branches/members/:id/perms`):
 *   ⛔ پرچمِ خاموش ⇒ همان درِ ناشناخته؛ پاسخ فقط `{ branches: [{ branchId, branchName, qty }] }` (بی بها، فروش، حرکت…)؛
 *   ⛔ فقط شعبه‌های دسترسیِ همین عضو، یا همه با مجوز (صاحب همیشه)؛ شعبهٔ همین درخواست و بایگانی نه؛ عدد = آزاد، هرگز منفی؛
 *   ⛔ سرآیندِ جعلی ⇒ ۴۰۳؛ شناسهٔ شعبه در پارامتر/بدنه بی اثر؛ مجوز فقط از صاحب.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const hlc = require('../src/lib/hlc');

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id, device: `dev-o-${made.body.shop.id}`.slice(0, 60) };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [id, s.shopId, u.user.id, role, now()]
  );
  return { ...u, memberId: id, userId: u.user.id, token: u.accessToken, device: `dev-m-${u.user.id}`.slice(0, 60) };
}
async function flag(s, key, value = true) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const B = (branch) => (branch === undefined ? {} : { 'X-Branch-Id': branch });
let opN = 0;
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++opN) * 10, counter: 0, node: 'brpeek' });
const v3op = (table, rowId, fields) => ({ op_id: `01JBP${String(Date.now()).slice(-6)}${String(++opN).padStart(15, '0')}`, table, row_id: rowId, kind: 'insert', hlc: H(), fields });
const entry3 = (id, pid, units) => v3op('warehouseEntries', id, { productId: pid, unitsMilli: units * 1000, createdAt: Date.now() });
function sale3(id, pid, units) {
  const total = units * 5000;
  return [
    v3op('sales', id, { totalMinor: total, discountMinor: 0, finalTotalMinor: total, paidAmountMinor: total, remainingMinor: 0,
      paymentMethod: 'cash', status: 'completed', createdAt: Date.now(), date: '' }),
    v3op('saleItems', `${id}-i`, { saleId: id, productId: pid, quantityMilli: units * 1000, unitPriceMinor: 5000, totalPriceMinor: total, createdAt: Date.now() }),
  ];
}
async function pushV3(who, ops, branch) {
  const r = await h.api('POST', '/api/sync/v3/push', { token: who.token, body: { device_id: who.device, protocol: 3, ops }, headers: B(branch) });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const peek = (who, branch, pid = 'p1', extra = '') => h.api('GET', `/api/shop/catalog/products/${pid}/other-branches${extra}`, { token: who.token, headers: B(branch) });
const grant = (s, m, perms, token = s.token) => h.put(`/api/shop/branches/members/${m.memberId}/perms`, { perms }, { token });
const qtyOf = (r) => Object.fromEntries(r.body.branches.map((b) => [b.branchName, b.qty]));

/** اصلی ۴، الف ۱۰ (فروشِ ۱ ⇒ ۹)، ب ۷، ج هیچ؛ کارمندِ الف، مدیرِ الف‌وب، کارمندِ اصلی */
async function world(tag, { branchesOn = true } = {}) {
  const s = await shop(`موجودیِ دیگر ${tag}`);
  await flag(s, 'shop.catalog');
  assert.equal((await h.api('PUT', '/api/shop/catalog/settings', { token: s.token, body: { syncSource: 'v3' } })).status, 200);
  await pushV3(s, [v3op('products', 'p1', { name: 'چای', unit: 'دانه', salePriceMinor: 5000, purchasePriceMinor: 3999, active: true }), entry3('m-e1', 'p1', 4)]);
  assert.equal((await h.api('GET', '/api/shop/catalog/products/p1', { token: s.token })).status, 200);
  const w = { s, staffA: await member(s, 'staff', `کارمندِ الف ${tag}`), mgrAB: await member(s, 'manager', `مدیرِ الف‌وب ${tag}`),
    mainStaff: await member(s, 'cashier', `صندوقِ اصلی ${tag}`) };
  if (!branchesOn) return w;
  await flag(s, 'shop.branches');
  for (const [k, name] of [['A', 'الف'], ['Bid', 'ب'], ['C', 'ج']]) w[k] = (await h.post('/api/shop/branches', { name }, { token: s.token })).body.branch.id;
  assert.equal((await h.put(`/api/shop/branches/members/${w.staffA.memberId}`, { branches: [w.A] }, { token: s.token })).status, 200);
  assert.equal((await h.put(`/api/shop/branches/members/${w.mgrAB.memberId}`, { branches: [w.A, w.Bid] }, { token: s.token })).status, 200);
  await pushV3(w.staffA, [entry3('a-e1', 'p1', 10), ...sale3('a-s1', 'p1', 1)], w.A);
  await pushV3(w.mgrAB, [entry3('b-e1', 'p1', 7)], w.Bid);
  return w;
}

test('پرچمِ خاموش ⇒ درِ موجودیِ شعبه‌های دیگر و درِ مجوز همان درِ ناشناخته، با و بی سرآیند', async () => {
  const w = await world('خاموش', { branchesOn: false });
  for (const who of [w.s, w.staffA]) {
    for (const claim of [undefined, 'brn_000000000000000000000000']) {
      const r = await peek(who, claim);
      const u = await h.api('GET', '/api/shop/catalog/products/p1/zz-no-such-door', { token: who.token, headers: B(claim) });
      assert.deepEqual([r.status, r.body], [u.status, u.body]);
      assert.equal(r.status, 404);
    }
  }
  const g = await grant(w.s, w.staffA, ['stock.view_other_branches']);
  const u = await h.api('PUT', '/api/shop/branches-zz-nope', { token: w.s.token, body: {} });
  assert.deepEqual([g.status, g.body], [u.status, u.body]);
  //  کاتالوگ خاموش (شعبه روشن) ⇒ باز همان
  const s2 = await shop('موجودیِ دیگر بی کاتالوگ');
  await flag(s2, 'shop.branches');
  const r = await peek(s2, undefined);
  const u2 = await h.api('GET', '/api/shop/catalog/products/p1/zz-no-such-door', { token: s2.token });
  assert.deepEqual([r.status, r.body], [u2.status, u2.body]);
});

test('فقط عدد: صاحب همهٔ شعبه‌های دیگر را می‌بیند؛ کارمند فقط دسترسی‌اش؛ مجوزِ صاحب ⇒ همه؛ برداشتن ⇒ باز فقط خودش', async () => {
  const w = await world('عدد');
  const { s, A, Bid, C } = w;
  //  صاحب با سرآیندِ الف: اصلی، ب و ج (الف خودش نیست)
  const own = await peek(s, A);
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.deepEqual(Object.keys(own.body), ['branches']);
  for (const b of own.body.branches) assert.deepEqual(Object.keys(b).sort(), ['branchId', 'branchName', 'qty']);
  assert.deepEqual(own.body.branches.map((b) => b.branchId), ['', Bid, C]);
  assert.deepEqual(qtyOf(own), { 'شعبهٔ اصلی': 4000, ب: 7000, ج: 0 });
  //  هیچ بها، فروش یا نامِ کالا
  const text = JSON.stringify(own.body);
  for (const bad of ['چای', 'a-s1', 'cost', 'sale', 'purchase', 'price', 'onHand']) assert.ok(!text.includes(bad), bad);
  assert.equal(own.headers.get('cache-control'), 'private, no-store');
  //  صاحب با سرآیندِ اصلی ⇒ الف ۹ (۱۰ − فروشِ ۱)
  assert.deepEqual(qtyOf(await peek(s, undefined)), { الف: 9000, ب: 7000, ج: 0 });
  //  کارمندِ فقط الف ⇒ هیچ؛ مدیرِ الف‌وب با سرآیندِ الف ⇒ فقط ب، با سرآیندِ ب ⇒ فقط الف
  assert.deepEqual((await peek(w.staffA, A)).body, { branches: [] });
  assert.deepEqual(qtyOf(await peek(w.mgrAB, A)), { ب: 7000 });
  assert.deepEqual(qtyOf(await peek(w.mgrAB, Bid)), { الف: 9000 });
  //  مجوز از صاحب ⇒ همه؛ فهرستِ اعضا نشانش می‌دهد؛ برداشتن ⇒ باز هیچ
  const g = await grant(s, w.staffA, ['stock.view_other_branches']);
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual(g.body.perms, ['stock.view_other_branches']);
  const mem = (await h.get('/api/shop/branches/members', { token: s.token })).body.members;
  assert.deepEqual(mem.find((m) => m.userId === w.staffA.userId).perms, ['stock.view_other_branches']);
  assert.equal(mem.find((m) => m.userId === w.mgrAB.userId).perms, undefined, 'بی مجوز همان شکلِ پیشین');
  assert.deepEqual(qtyOf(await peek(w.staffA, A)), { 'شعبهٔ اصلی': 4000, ب: 7000, ج: 0 });
  assert.ok(await one("SELECT 1 AS x FROM audit_logs WHERE shop_id=$1 AND action='branch.member_perms' AND target_id=$2", [s.shopId, w.staffA.userId]));
  assert.equal((await grant(s, w.staffA, [])).status, 200);
  assert.deepEqual((await peek(w.staffA, A)).body, { branches: [] });
  //  شعبهٔ بایگانی دیده نمی‌شود
  assert.equal((await h.api('PATCH', `/api/shop/branches/${C}`, { token: s.token, body: { status: 'archived' } })).status, 200);
  assert.deepEqual((await peek(s, A)).body.branches.map((b) => b.branchId), ['', Bid]);
  //  منفی ⇒ صفر (فروشِ آفلاینِ بیش از موجودیِ ب)
  await pushV3(w.mgrAB, sale3('b-s1', 'p1', 9), Bid);
  assert.deepEqual(qtyOf(await peek(s, A)), { 'شعبهٔ اصلی': 4000, ب: 0 });
  //  کالای ناشناس ⇒ ۴۰۴ ِ کاتالوگ
  const nf = await peek(s, A, 'nope');
  assert.equal(nf.status, 404);
  assert.equal(nf.body.error.code, 'product_not_found');
});

test('سرآیندِ جعلی ⇒ ۴۰۳؛ شناسهٔ شعبه در پارامتر بی اثر؛ مجوز فقط از صاحب و فقط برای عضوِ همین دکان', async () => {
  const w = await world('جعل');
  const other = await world('جعلِ دکانِ دیگر');
  const { s, A, Bid } = w;
  for (const claim of [Bid, 'brn_000000000000000000000000', 'brn_xyz', other.A, `${A},${Bid}`, undefined]) {
    const r = await peek(w.staffA, claim);
    assert.equal(r.status, 403, `${claim} ${JSON.stringify(r.body)}`);
    assert.ok(['branch_forbidden', 'branch_required'].includes(r.body.error.code));
    assert.ok(!JSON.stringify(r.body).includes('7000'));
  }
  //  پارامترهای شعبه خوانده نمی‌شوند
  const q = await peek(w.staffA, A, 'p1', `?branch=${Bid}&branchId=${Bid}&branches=${Bid}`);
  assert.deepEqual(q.body, { branches: [] });
  //  صندوق‌دارِ شعبهٔ اصلی (بی ردیف ⇒ فقط اصلی) هیچ نمی‌بیند
  assert.deepEqual((await peek(w.mainStaff, undefined)).body, { branches: [] });
  //  مجوز: فقط صاحب؛ ناشناخته رد؛ صاحب ثابت؛ عضوِ دکانِ دیگر «نیست»
  const byMgr = await grant(s, w.staffA, ['stock.view_other_branches'], w.mgrAB.token);
  assert.equal(byMgr.status, 403);
  assert.equal(byMgr.body.error.code, 'owner_only');
  assert.equal((await grant(s, w.staffA, ['stock.adjust'])).body.error.code, 'unknown_permission');
  assert.equal((await grant(s, w.staffA, 'stock.view_other_branches')).body.error.code, 'bad_perms');
  const ownerMember = (await one("SELECT id FROM shop_members WHERE shop_id=$1 AND role='owner'", [s.shopId])).id;
  assert.equal((await grant(s, { memberId: ownerMember }, ['stock.view_other_branches'])).body.error.code, 'owner_fixed');
  const foreign = await grant(s, other.staffA, ['stock.view_other_branches']);
  assert.equal(foreign.status, 404);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_member_branch_perms WHERE user_id=$1', [other.staffA.userId])).n, 0);
  //  مجوزِ دکانِ دیگر به این دکان نمی‌رسد
  assert.equal((await grant(other.s, other.staffA, ['stock.view_other_branches'])).status, 200);
  assert.deepEqual((await peek(w.staffA, A)).body, { branches: [] });
  //  دکانِ دیگر با همان شناسهٔ کالا فقط شعبه‌های خودش
  const o = await peek(other.s, other.A);
  assert.ok(!JSON.stringify(o.body).includes(Bid) && !JSON.stringify(o.body).includes(w.C));
});
