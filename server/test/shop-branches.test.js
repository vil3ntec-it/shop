'use strict';
/**
 * شعبه درونِ یک دکان — فازِ ۱ ِ سرور (`docs/BRANCHES-fa.md`، تصمیمِ صاحب‌کار ۱، ۱b، ۳۴ ِ ۱۴۰۵/۰۸/۲۳).
 *
 * از راهِ واقعی (درخواستِ HTTP، نه صدا زدنِ کتابخانه):
 *   ⛔ پرچمِ `shop.branches` خاموش ⇒ درهای `/api/shop/branches*` همان پاسخِ مسیرِ ناشناخته، سرآیندِ `X-Branch-Id` بی اثر،
 *      همگام‌سازی و درهای امروز بایت‌به‌بایت همان (آزمونِ برابری).
 *   ⛔ صاحب ⇐ همهٔ شعبه‌ها + جمع؛ کارمند ⇐ فقط شعبه(های) خودش؛ نبودِ ردیف = شعبهٔ اصلی (همان امروز).
 *   ⛔ دادهٔ هر شعبه در بخشِ جدای همگام‌سازی (v3)؛ شعبهٔ اصلی همان حسابِ امروز ⇒ هیچ ردیفی کوچ داده نمی‌شود.
 *   ⛔ نفوذ: شناسهٔ شعبهٔ دیگر در سرآیند/بدنه، شعبهٔ دکانِ دیگر، کارمندِ شعبه روی v1/راهِ قدیم/درهای امروز ⇒ ۴۰۳ بی هیچ ردیف.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const hlc = require('../src/lib/hlc');

let admin = '';

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
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

// ── کمکی‌ها ────────────────────────────────────────────────────────────

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
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
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
let tick = 0;
const H = () => hlc.format({ wall: Date.now() - 600_000 + (++tick) * 10, counter: 0, node: 'branch' });
const oid = () => `01JBR${String(Date.now()).slice(-6)}${String(++opN).padStart(15, '0')}`;
const v3op = (table, rowId, fields) => ({ op_id: oid(), table, row_id: rowId, kind: 'insert', hlc: H(), fields });
const product3 = (id) => v3op('products', id, { name: `کالای ${id}`, unit: 'دانه', salePriceMinor: 5000, purchasePriceMinor: 4000, active: true });
const expense3 = (id, minor) => v3op('expenses', id, { title: `مصرفِ ${id}`, amountMinor: minor, date: '2026-11-13', createdAt: Date.now() });
function sale3(id, minor, at = Date.now()) {
  return v3op('sales', id, {
    totalMinor: minor, discountMinor: 0, finalTotalMinor: minor, paidAmountMinor: minor, remainingMinor: 0,
    paymentMethod: 'cash', status: 'completed', createdAt: at, date: '',
  });
}

async function v3(who, method, path, { body = null, branch, query: qs = '' } = {}) {
  return h.api(method, `/api/sync/v3/${path}${qs}`, { token: who.token, body, headers: B(branch) });
}
async function pushV3(who, ops, branch) {
  return v3(who, 'POST', 'push', { body: { device_id: who.device, protocol: 3, ops }, branch });
}
async function rowsOf(accountId, table) {
  return many(
    `SELECT row_id FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 ORDER BY row_id`,
    [accountId, table]
  );
}
let v1N = 0;
const v1id = () => `01JBV${String(Date.now()).slice(-8)}${String(++v1N).padStart(8, '0')}`;
const v1op = (table, rowId, fields) => ({ op_id: v1id(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
function v1(who, method, path, { body = null, branch } = {}) {
  return h.api(method, `/api/sync/v1/${path}`, {
    token: who.token, body, headers: { 'X-App': 'shop', 'X-Device': who.device, ...B(branch) },
  });
}

/** دکان با پرچمِ روشن، دو شعبهٔ تازه و سه کارمند: اصلی، الف، الف+ب */
async function world(tag) {
  const s = await shop(`شعبه‌ها ${tag}`);
  await flag(s, 'shop.branches');
  const a = await h.post('/api/shop/branches', { name: 'شعبهٔ الف' }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await h.post('/api/shop/branches', { name: 'شعبهٔ ب' }, { token: s.token });
  assert.equal(b.status, 201, JSON.stringify(b.body));
  const main = await member(s, 'staff', `اصلی ${tag}`);
  const onlyA = await member(s, 'staff', `الف ${tag}`);
  const aAndB = await member(s, 'manager', `الف‌وب ${tag}`);
  let r = await h.put(`/api/shop/branches/members/${onlyA.memberId}`, { branches: [a.body.branch.id] }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  r = await h.put(`/api/shop/branches/members/${aAndB.userId}`, { branches: [a.body.branch.id, b.body.branch.id] }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { s, A: a.body.branch.id, Bid: b.body.branch.id, main, onlyA, aAndB };
}

// ── پرچمِ خاموش: همان امروز ───────────────────────────────────────────

test('پرچمِ خاموش ⇒ درهای شعبه همان پاسخِ مسیرِ ناشناخته و سرآیندِ شعبه بی اثر (برابری)', async () => {
  const s = await shop('خاموش');
  const unknown = await h.get('/api/shop/branches-nope-zz', { token: s.token });
  for (const [m, p, body] of [['GET', '/api/shop/branches'], ['POST', '/api/shop/branches', { name: 'x' }],
    ['GET', '/api/shop/branches/report'], ['GET', '/api/shop/branches/members'],
    ['PUT', '/api/shop/branches/members/x', { branches: [''] }], ['PATCH', '/api/shop/branches/x', { name: 'y' }]]) {
    const r = await h.api(m, p, { token: s.token, body });
    assert.equal(r.status, unknown.status, `${m} ${p}`);
    assert.deepEqual(r.body, unknown.body, `${m} ${p}`);
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_branches WHERE shop_id=$1', [s.shopId])).n, 0);

  //  همگام‌سازیِ v3: با و بی سرآیند ⇒ همان حساب، همان نتیجه
  const r1 = await pushV3(s, [product3('p-off-1')]);
  const r2 = await pushV3(s, [product3('p-off-2')], 'brn_000000000000000000000000');
  assert.equal(r1.status, 200); assert.equal(r2.status, 200);
  assert.deepEqual(r1.body.results.map((x) => x.status), r2.body.results.map((x) => x.status));
  assert.deepEqual((await rowsOf(s.shopId, 'products')).map((x) => x.row_id), ['p-off-1', 'p-off-2']);
  const p1 = await v3(s, 'GET', 'snapshot');
  const p2 = await v3(s, 'GET', 'snapshot', { branch: 'brn_000000000000000000000000' });
  assert.equal(p1.status, 200);
  assert.deepEqual(p2.body.tables, p1.body.tables);

  //  v1 و درهای امروز: سرآیند بی اثر
  const st1 = await v1(s, 'GET', `pull?since=0&device_id=${s.device}`);
  const st2 = await v1(s, 'GET', `pull?since=0&device_id=${s.device}`, { branch: 'brn_000000000000000000000000' });
  assert.equal(st1.status, 200, JSON.stringify(st1.body)); assert.equal(st2.status, st1.status);
  assert.deepEqual(st2.body.ops, st1.body.ops);
  const g1 = await h.get('/api/shop', { token: s.token });
  const g2 = await h.api('GET', '/api/shop', { token: s.token, headers: B('brn_000000000000000000000000') });
  assert.equal(g2.status, g1.status);
  const still = ({ serverTime, ...rest }) => rest;   //  ساعتِ سرور در هر پاسخ تازه است
  assert.deepEqual(still(g2.body), still(g1.body));
});

// ── درهای مدیریت ──────────────────────────────────────────────────────

test('صاحب شعبه می‌سازد، نام می‌دهد، بایگانی می‌کند و کارمند را به شعبه می‌بندد؛ کارمند فقط شعبه‌های خودش را می‌بیند', async () => {
  const w = await world('درها');
  const { s, A, Bid } = w;
  const list = await h.get('/api/shop/branches', { token: s.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual(list.body.branches.map((b) => b.id), ['', A, Bid]);
  assert.equal(list.body.branches[0].main, true);
  assert.equal(list.body.all, true);

  //  نامِ تکراری (با نامِ شعبهٔ اصلی هم) و نامِ تهی رد
  assert.equal((await h.post('/api/shop/branches', { name: 'شعبهٔ الف' }, { token: s.token })).body.error.code, 'branch_name_taken');
  assert.equal((await h.post('/api/shop/branches', { name: '   ' }, { token: s.token })).status, 400);
  //  نامِ شعبهٔ اصلی
  const rn = await h.api('PATCH', '/api/shop/branches/main', { token: s.token, body: { name: 'مرکز' } });
  assert.equal(rn.status, 200, JSON.stringify(rn.body));
  assert.equal((await h.get('/api/shop/branches', { token: s.token })).body.branches[0].name, 'مرکز');
  //  شعبهٔ اصلی بایگانی نمی‌شود
  assert.equal((await h.api('PATCH', '/api/shop/branches/main', { token: s.token, body: { status: 'archived' } })).body.error.code, 'main_branch_fixed');

  //  کارمند: فقط شعبه‌های خودش، بی نامِ بقیه
  const mine = await h.get('/api/shop/branches', { token: w.onlyA.token });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  assert.deepEqual(mine.body.branches.map((b) => b.id), [A]);
  assert.equal(mine.body.all, false);
  const def = await h.get('/api/shop/branches', { token: w.main.token });
  assert.deepEqual(def.body.branches.map((b) => b.id), ['']);

  //  اعضا (فقط صاحب)
  const mem = await h.get('/api/shop/branches/members', { token: s.token });
  assert.equal(mem.status, 200);
  const byUser = Object.fromEntries(mem.body.members.map((m) => [m.userId, m.branches]));
  assert.deepEqual(byUser[w.main.userId], ['']);
  assert.deepEqual(byUser[w.onlyA.userId], [A]);
  assert.deepEqual(byUser[w.aAndB.userId].sort(), [A, Bid].sort());

  //  بازگشت به پیش‌فرض: [''] ⇒ ردیف پاک
  const back = await h.put(`/api/shop/branches/members/${w.main.memberId}`, { branches: [''] }, { token: s.token });
  assert.equal(back.status, 200);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_member_branches WHERE shop_id=$1 AND user_id=$2', [s.shopId, w.main.userId])).n, 0);
  //  شعبهٔ ناشناخته/بایگانی و صاحب ⇒ رد
  assert.equal((await h.put(`/api/shop/branches/members/${w.main.memberId}`, { branches: ['brn_ffffffffffffffffffffffff'] }, { token: s.token })).body.error.code, 'branch_not_found');
  assert.equal((await h.put(`/api/shop/branches/members/${w.main.memberId}`, { branches: [] }, { token: s.token })).status, 400);
  assert.equal((await h.put(`/api/shop/branches/members/${s.user.id}`, { branches: [A] }, { token: s.token })).body.error.code, 'owner_fixed');

  //  سابقهٔ عملیات
  const logs = await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'branch.%' ORDER BY created_at`, [s.shopId]);
  assert.ok(logs.some((l) => l.action === 'branch.created'), JSON.stringify(logs));
  assert.ok(logs.some((l) => l.action === 'branch.member_set'), JSON.stringify(logs));

  //  بایگانیِ ب ⇒ کارمندِ الف‌وب فقط الف را دارد
  const ar = await h.api('PATCH', `/api/shop/branches/${Bid}`, { token: s.token, body: { status: 'archived' } });
  assert.equal(ar.status, 200, JSON.stringify(ar.body));
  assert.deepEqual((await h.get('/api/shop/branches', { token: w.aAndB.token })).body.branches.map((b) => b.id), [A]);
  assert.equal((await pushV3(w.aAndB, [product3('p-arch')], Bid)).status, 403);
});

test('نفوذ: کارمند و مدیر هیچ درِ نوشتنیِ شعبه را ندارند؛ شعبهٔ دکانِ دیگر دیده و نوشته نمی‌شود', async () => {
  const w = await world('نفوذ‌در');
  const other = await world('دیگر');
  for (const who of [w.main, w.onlyA, w.aAndB]) {
    assert.equal((await h.post('/api/shop/branches', { name: 'دزد' }, { token: who.token })).status, 403);
    assert.equal((await h.api('PATCH', `/api/shop/branches/${w.A}`, { token: who.token, body: { name: 'دزد' } })).status, 403);
    assert.equal((await h.put(`/api/shop/branches/members/${who.memberId}`, { branches: [w.A, w.Bid, ''] }, { token: who.token })).status, 403);
    assert.equal((await h.get('/api/shop/branches/members', { token: who.token })).status, 403);
  }
  //  صاحبِ دکانِ دیگر: شعبه‌های این دکان «نیست»
  assert.equal((await h.api('PATCH', `/api/shop/branches/${w.A}`, { token: other.s.token, body: { name: 'دزد' } })).body.error.code, 'branch_not_found');
  assert.equal((await h.put(`/api/shop/branches/members/${other.onlyA.memberId}`, { branches: [w.A] }, { token: other.s.token })).body.error.code, 'branch_not_found');
  assert.equal((await h.put(`/api/shop/branches/members/${w.onlyA.memberId}`, { branches: [other.A] }, { token: other.s.token })).body.error.code, 'member_not_found');
  assert.equal((await pushV3(other.s, [product3('p-x')], w.A)).status, 403);
  const names = (await many('SELECT name FROM shop_branches WHERE shop_id=$1 ORDER BY name', [w.s.shopId])).map((r) => r.name);
  assert.deepEqual(names, ['شعبهٔ الف', 'شعبهٔ ب']);
});

// ── همگام‌سازی ─────────────────────────────────────────────────────────

test('v3: هر شعبه بخشِ جدای خودش را دارد؛ شناسهٔ شعبهٔ دیگر در سرآیند ⇒ ۴۰۳ و در بدنه بی اثر', async () => {
  const w = await world('v3');
  const { s, A, Bid } = w;
  //  کارمندِ الف بی سرآیند ⇒ شعبهٔ اصلی ندارد ⇒ ۴۰۳ ِ «شعبه را بگو»
  const none = await pushV3(w.onlyA, [product3('p-none')]);
  assert.equal(none.status, 403); assert.equal(none.body.error.code, 'branch_required');
  //  الف
  const pa = await pushV3(w.onlyA, [product3('p-a'), sale3('s-a', 30000), expense3('e-a', 1000)], A);
  assert.equal(pa.status, 200, JSON.stringify(pa.body));
  for (const x of pa.body.results) assert.notEqual(x.status, 'rejected', JSON.stringify(x));
  //  دستکاریِ بدنه: branch_id ِ ب در بدنه با سرآیندِ الف ⇒ همچنان الف
  const tb = await v3(w.onlyA, 'POST', 'push', { branch: A, body: { device_id: w.onlyA.device, protocol: 3, branch_id: Bid, branchId: Bid, ops: [product3('p-a2')] } });
  assert.equal(tb.status, 200);
  //  ب از سرآیند ⇒ ۴۰۳ بی هیچ ردیف
  const pb = await pushV3(w.onlyA, [product3('p-steal')], Bid);
  assert.equal(pb.status, 403); assert.equal(pb.body.error.code, 'branch_forbidden');
  //  شعبهٔ اصلی برای کارمندِ الف ⇒ ۴۰۳
  assert.equal((await pushV3(w.onlyA, [product3('p-main-steal')], '')).status, 403);
  //  شناسهٔ ساختگی/دکانِ دیگر ⇒ همان ۴۰۳
  assert.equal((await pushV3(w.onlyA, [product3('p-fake')], 'brn_abcdefabcdefabcdefabcdef')).body.error.code, 'branch_forbidden');
  assert.equal((await pushV3(w.onlyA, [product3('p-bad')], "x' OR 1=1 --")).body.error.code, 'branch_forbidden');

  //  اصلی (کارمندِ اصلی بی سرآیند)
  const pm = await pushV3(w.main, [product3('p-m'), sale3('s-m', 70000)]);
  assert.equal(pm.status, 200);
  //  کارمندِ اصلی روی الف ⇒ ۴۰۳
  assert.equal((await pushV3(w.main, [product3('p-m-steal')], A)).status, 403);

  //  ردیف‌ها کجا نشستند: اصلی = همان حسابِ امروز، الف = بخشِ جدا
  assert.deepEqual((await rowsOf(s.shopId, 'products')).map((r) => r.row_id), ['p-m']);
  assert.deepEqual((await rowsOf(`${s.shopId}#${A}`, 'products')).map((r) => r.row_id), ['p-a', 'p-a2']);
  assert.deepEqual((await rowsOf(`${s.shopId}#${Bid}`, 'products')).map((r) => r.row_id), []);

  //  خواندن: Snapshot و pull ِ هر شعبه فقط خودش
  const snapA = await v3(w.onlyA, 'GET', 'snapshot', { branch: A });
  assert.equal(snapA.status, 200);
  const idsOf = (t) => (Array.isArray(t) ? t.map((r) => r.row_id || r.id) : Object.keys(t || {})).sort();
  assert.deepEqual(idsOf(snapA.body.tables.products), ['p-a', 'p-a2']);
  const snapM = await v3(w.main, 'GET', 'snapshot');
  const idsM = JSON.stringify(snapM.body.tables);
  assert.ok(!idsM.includes('p-a') && !idsM.includes('s-a') && !idsM.includes('e-a'), 'دادهٔ الف در اصلی نیست');
  const pullM = await v3(w.main, 'GET', 'pull', { query: `?device_id=${w.main.device}x&since=0` });
  assert.equal(pullM.status, 200);
  assert.ok(!JSON.stringify(pullM.body).includes('p-a'));
  const pullA = await v3(w.onlyA, 'GET', 'pull', { branch: A, query: `?device_id=${w.onlyA.device}y&since=0` });
  assert.ok(!JSON.stringify(pullA.body).includes('p-m') && !JSON.stringify(pullA.body).includes('s-m'));
  //  وضعیت و تعارض‌ها هم بخشِ خودش
  assert.equal((await v3(w.onlyA, 'GET', 'status', { branch: Bid })).status, 403);
  assert.equal((await v3(w.onlyA, 'GET', 'conflicts', { branch: Bid })).status, 403);
  assert.equal((await v3(w.onlyA, 'GET', 'conflicts', { branch: A })).status, 200);

  //  مدیرِ الف‌وب: هر دو، نه اصلی؛ صاحب: هر سه
  assert.equal((await pushV3(w.aAndB, [product3('p-b')], Bid)).status, 200);
  assert.equal((await pushV3(w.aAndB, [product3('p-ab-main')], '')).status, 403);
  const ownA = await v3(s, 'GET', 'snapshot', { branch: A });
  assert.equal(ownA.status, 200);
  assert.ok(JSON.stringify(ownA.body.tables).includes('p-a'));
  assert.deepEqual((await rowsOf(`${s.shopId}#${Bid}`, 'products')).map((r) => r.row_id), ['p-b']);

  //  تغییرِ صاحب از درخواستِ بعد: الف از کارمند گرفته شد
  await h.put(`/api/shop/branches/members/${w.onlyA.memberId}`, { branches: [Bid] }, { token: s.token });
  assert.equal((await pushV3(w.onlyA, [product3('p-late')], A)).status, 403);
  assert.equal((await pushV3(w.onlyA, [product3('p-late-b')], Bid)).status, 200);
});

test('v3 در شعبه: پرچم و بستنِ مجوزِ دکان همان‌جا هم اجرا می‌شود (بی باز شدن با شناسهٔ بخش)', async () => {
  const w = await world('مجوز');
  const { s, A } = w;
  //  پرچمِ داوریِ سندِ دکان (`shop.sync.doc_roles`) فقط روی دکان روشن است، نه روی «بخش»
  await flag(s, 'shop.sync.doc_roles');
  const refund = (id) => v3op('transactions', id, { debtorId: 'd-lw', type: 'give', amountMinor: 550000, refId: 'lw-1', kind: 'layaway_refund' });
  const ok = await pushV3(w.aAndB, [refund('t-ok')], A);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.results[0].status, 'applied', JSON.stringify(ok.body.results));
  //  صاحب «لغو» را برای همین مدیر می‌بندد (لایهٔ کاربر) ⇒ در بخشِ الف هم رد
  const layer = await h.put(`/api/shop/permissions/members/${w.aAndB.userId}`, { perms: { 'sales.cancel': { mode: 'deny' } } }, { token: s.token });
  assert.equal(layer.status, 200, JSON.stringify(layer.body));
  const no = await pushV3(w.aAndB, [refund('t-no')], A);
  assert.equal(no.status, 200, JSON.stringify(no.body));
  assert.equal(no.body.results[0].status, 'rejected', JSON.stringify(no.body.results));
  assert.equal(no.body.results[0].reason, 'doc_not_allowed');
  assert.deepEqual((await rowsOf(`${s.shopId}#${A}`, 'transactions')).map((r) => r.row_id), ['t-ok']);
});

test('v1، راهِ قدیم و درهای امروز فقط شعبهٔ اصلی‌اند: کارمندِ بی شعبهٔ اصلی و هر ادعای شعبهٔ دیگر ⇒ ۴۰۳', async () => {
  const w = await world('v1');
  const { s, A } = w;
  //  کارمندِ اصلی روی v1 مثلِ امروز
  const okPush = await v1(w.main, 'POST', 'push', { body: { device_id: w.main.device, schema_version: 2, ops: [v1op('expenses', 'e-v1', { title: 'نان', amount: 10, date: '2026-11-13', createdAt: Date.now() })] } });
  assert.equal(okPush.status, 200, JSON.stringify(okPush.body));
  //  کارمندِ فقط الف ⇒ v1 بسته (هر دری)
  for (const [m, p, body] of [['GET', `pull?since=0&device_id=${w.onlyA.device}`], ['GET', `snapshot?device_id=${w.onlyA.device}`],
    ['POST', 'push', { device_id: w.onlyA.device, schema_version: 2, ops: [v1op('expenses', 'e-steal', { title: 'x', amount: 1, date: '2026-11-13', createdAt: Date.now() })] }]]) {
    const r = await v1(w.onlyA, m, p, { body });
    assert.equal(r.status, 403, `${m} ${p} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'branch_main_only');
  }
  //  صاحب با ادعای شعبهٔ الف روی v1 ⇒ ۴۰۳ (v1 فقط اصلی را دارد؛ وگرنه دادهٔ الف در اصلی می‌نشست)
  const claim = await v1(s, 'POST', 'push', { branch: A, body: { device_id: s.device, schema_version: 2, ops: [v1op('expenses', 'e-claim', { title: 'x', amount: 1, date: '2026-11-13', createdAt: Date.now() })] } });
  assert.equal(claim.status, 403); assert.equal(claim.body.error.code, 'branch_unsupported');
  const rows = await many(`SELECT row_id FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='expenses' ORDER BY row_id`, [s.shopId]);
  assert.deepEqual(rows.map((r) => r.row_id), ['e-v1']);
  //  درهای امروز و راهِ قدیم برای کارمندِ فقط الف
  for (const p of ['/api/shop', '/api/shop/sync/pull', '/api/sync/pull', '/api/events', '/api/me/backups']) {
    const r = await h.get(p, { token: w.onlyA.token });
    assert.equal(r.status, 403, `${p} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'branch_main_only', p);
  }
  //  کارمندِ اصلی همان امروز
  assert.equal((await h.get('/api/shop', { token: w.main.token })).status, 200);
  //  ادعای الف روی درِ امروز ⇒ ۴۰۳ (حتی صاحب)
  const g = await h.api('GET', '/api/shop', { token: s.token, headers: B(A) });
  assert.equal(g.status, 403); assert.equal(g.body.error.code, 'branch_unsupported');
  //  بی توکن همان پاسخِ امروز (نگهبان چیزی نمی‌گوید)
  assert.equal((await h.api('GET', '/api/shop', { headers: B(A) })).status, 401);
});

// ── گزارشِ جمع ─────────────────────────────────────────────────────────

test('گزارش: صاحب هر شعبه را جدا و جمعِ همه را می‌بیند؛ کارمند فقط شعبهٔ خودش و بی جمع', async () => {
  const w = await world('گزارش');
  const { s, A, Bid } = w;
  //  شعبهٔ اصلی همان راهِ امروز (v1، دفترِ `sync_rows`)؛ شعبه‌ها v3 در بخشِ خودشان
  const sale1 = (id, total) => v1op('sales', id, { total, discount: 0, finalTotal: total, paymentMethod: 'cash', status: 'completed', createdAt: Date.now(), date: '' });
  const mp = await v1(w.main, 'POST', 'push', { body: { device_id: w.main.device, schema_version: 2, ops: [sale1('r-m1', 1000), sale1('r-m2', 200)] } });
  assert.equal(mp.status, 200, JSON.stringify(mp.body));
  assert.equal((await pushV3(w.onlyA, [sale3('r-a1', 5000)], A)).status, 200);
  assert.equal((await pushV3(w.aAndB, [sale3('r-b1', 700)], Bid)).status, 200);

  const own = await h.get('/api/shop/branches/report', { token: s.token });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  const by = Object.fromEntries(own.body.branches.map((b) => [b.id, b.summary]));
  assert.equal(by[''].invoices, 2);
  assert.equal(by[A].invoices, 1);
  assert.equal(by[Bid].invoices, 1);
  assert.equal(by[''].sales + by[A].sales + by[Bid].sales, own.body.totals.sales);
  assert.equal(own.body.totals.invoices, 4);
  assert.ok(own.body.totals.sales > 0);

  const a = await h.get('/api/shop/branches/report', { token: w.onlyA.token });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.deepEqual(a.body.branches.map((b) => b.id), [A]);
  assert.equal(a.body.totals, undefined);
  //  IDOR: شعبهٔ دیگر در پارامتر ⇒ ۴۰۳
  assert.equal((await h.get(`/api/shop/branches/report?branch=${Bid}`, { token: w.onlyA.token })).status, 403);
  assert.equal((await h.get('/api/shop/branches/report?branch=', { token: w.onlyA.token })).status, 403);
  const ab = await h.get(`/api/shop/branches/report?branch=${Bid}`, { token: w.aAndB.token });
  assert.equal(ab.status, 200);
  assert.deepEqual(ab.body.branches.map((b) => b.id), [Bid]);
  const m = await h.get('/api/shop/branches/report', { token: w.main.token });
  assert.deepEqual(m.body.branches.map((b) => b.id), ['']);
  assert.equal(m.body.branches[0].summary.invoices, 2);
});

// ── کوچ و پاک شدن ──────────────────────────────────────────────────────

test('کوچ: دکانِ امروز بی هیچ ردیفِ تازه «شعبهٔ اصلی» است؛ پاک شدنِ دکان بخش‌های شعبه را هم پاک می‌کند', async () => {
  const s = await shop('کوچ');
  //  پیش از روشن کردن: داده در حسابِ امروز
  assert.equal((await pushV3(s, [product3('k-1')])).status, 200);
  await flag(s, 'shop.branches');
  const list = await h.get('/api/shop/branches', { token: s.token });
  assert.deepEqual(list.body.branches.map((b) => [b.id, b.name, b.main]), [['', 'شعبهٔ اصلی', true]]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_branches WHERE shop_id=$1', [s.shopId])).n, 0);
  const snap = await v3(s, 'GET', 'snapshot');
  assert.ok(JSON.stringify(snap.body.tables).includes('k-1'), 'دادهٔ پیشین همان شعبهٔ اصلی است');

  const b = await h.post('/api/shop/branches', { name: 'شعبهٔ تازه' }, { token: s.token });
  assert.equal((await pushV3(s, [product3('k-b')], b.body.branch.id)).status, 200);
  const key = `${s.shopId}#${b.body.branch.id}`;
  assert.equal((await rowsOf(key, 'products')).length, 1);
  await query('DELETE FROM shops WHERE id=$1', [s.shopId]);
  for (const t of ['sync_v3_rows', 'sync_v3_ops', 'sync_v3_devices']) {
    assert.equal((await one(`SELECT COUNT(*)::int AS n FROM ${t} WHERE account_id=$1`, [key])).n, 0, t);
  }
});
