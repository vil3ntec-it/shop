'use strict';
/**
 * CRM-03 (§۱۰.۱ «تغییرِ سقفِ اعتبار») — مجوزِ `customers.credit_limit` در سرور (`lib/credit-limit-perm.js`، پرچمِ خاموشِ
 * `shop.credit_limit_perm`).
 *
 * سنجه‌ها: پرچمِ خاموش همان امروز (لایه ذخیره نمی‌شود، لایهٔ ازپیش‌مانده بی‌اثر)؛ با پرچم، شاگردِ «ممنوع» یا بالای `maxAmount`
 * سقفِ مشتریِ موجود را در v1، v3 و راهِ قدیم عوض نمی‌کند (بالا بردن، برداشتن، پایین آوردن) و ردیفِ صندوقِ تعارض با
 * خودِ op می‌ماند؛ زیرِ سقف، ویرایشِ بی سقف و صاحب آزاد؛ مشتریِ تازه (تصمیمِ ۱۴۰۵/۰۸/۲۳ #۲۵) فقط با سقفِ پیش‌فرض (صفر) یا تا سقفِ نقش؛ نقشِ جعلی در بدنه/فیلد اثری ندارد؛ تکرارِ همان op
 * «تکراری» است؛ شاگرد لایهٔ خودش را باز نمی‌کند؛ دکانِ دیگر با همان شناسهٔ مشتری دست نمی‌خورد.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const perm = require('../src/lib/credit-limit-perm');

let admin = '';
let shopId;
let shopB;
let owner;
let manager;
let staff;
let staffB;
let n = 0;
const oid = () => `01JLP${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now();

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const setRole = (role, perms, who = owner) => h.roleLayer(who.accessToken, role, perms);
const push1 = (who, device, ops, extra = {}) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: 1, ops, ...extra },
});
const push3 = (who, device, ops, extra = {}) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops, ...extra },
});
//  برنامهٔ تازه (A00): `null` ِ صریح همان «برداشتن» است؛ برنامهٔ بی نسخه آن را نمی‌فرستد (‎lib/newer-fields.js‎)
const A00 = { caps: ['a00'] };
const legacy = (who, changes) => h.api('POST', '/api/sync/push', {
  token: who.accessToken, headers: { 'X-App': 'shop' }, body: { deviceId: 'web-1', changes },
});
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const op1 = (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: (clock += 1000), fields });
const op3 = (table, rowId, kind, fields) => ({ op_id: oid(), hlc: H('pos3'), table, row_id: rowId, kind, fields });
const limit1 = async (id, shop = shopId) => (await one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id=$2`, [shop, id]
)).data.creditLimit;
const limit3 = async (id) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name='debtors' AND row_id=$2`, [shopId, id]
)).data.creditLimitMinor;
const boxOf = (opId) => one(`SELECT kind, table_name, detail, loser_value FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, opId]);

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'lp-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'lp-admin', password: 'Admin!12345' })).body.token;
  async function shopWithStaff(label, withManager) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    let m = null;
    if (withManager) {
      const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
      m = await h.newUser(`مدیرِ ${label}`, 'shop');
      assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
    }
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop'), manager: m ? await h.signIn(m, 'shop') : null };
  }
  ({ id: shopId, owner, staff, manager } = await shopWithStaff('سقفِ مشتری', true));
  ({ id: shopB, staff: staffB } = await shopWithStaff('سقفِ مشتریِ ب', false));
  //  مشتری‌های پایه با سقفِ ۵٬۰۰۰ افغانی (v1) و ۵٬۰۰۰٬۰۰ Minor (v3) — نوشتهٔ صاحب
  assert.deepEqual(why(await push1(owner, 'own-1', [
    op1('debtors', 'd-1', 'insert', { name: 'مشتریِ یک', phone: '0700000101', creditLimit: 5000, createdAt: clock }),
    op1('debtors', 'd-2', 'insert', { name: 'مشتریِ دو', phone: '0700000102', creditLimit: 5000, createdAt: clock }),
  ])), ['applied', 'applied']);
  assert.deepEqual(why(await push3(owner, 'own-3', [
    op3('debtors', 'd3-1', 'insert', { name: 'مشتریِ سه', phone: '0700000103', creditLimitMinor: 500000 }),
  ])), ['applied']);
});
test.after(async () => { await h.stop(); });

test('قاعدهٔ خالص: فقط تغییرِ سقفِ مشتریِ موجود؛ برداشتن و عددِ نادرست ⇒ بی‌نهایت؛ ردیفِ کاملِ راهِ قدیم بی کلید = برداشتن', () => {
  const L = (f, o) => perm.limitOf(f, o);
  //  #۲۵ (۱۴۰۵/۰۸/۲۳): مشتریِ تازه از سقفِ پیش‌فرضِ دکان (صفر) سنجیده می‌شود؛ بی فیلد = بی سقف
  assert.deepEqual(perm.need(null, L({ creditLimit: 9000 })).req, { amount: 900000 }, 'مشتریِ تازه با سقف');
  assert.equal(perm.need(null, L({ creditLimit: 9000 })).fresh, true);
  assert.equal(perm.need(null, L({ name: 'x' })).amount, Number.MAX_SAFE_INTEGER, 'مشتریِ تازهٔ بی سقف');
  assert.equal(perm.need(null, L({ creditLimit: 0 })), null, 'مشتریِ تازه با سقفِ پیش‌فرض');
  assert.equal(perm.need(null, L({ creditLimitMinor: 0 }, { shape: 'v3' }), 'v3'), null);
  assert.equal(perm.DEFAULT_MINOR, 0);
  assert.equal(perm.need({ creditLimit: 5000 }, L({ name: 'x' })), null, 'بی فیلدِ سقف');
  assert.equal(perm.need({ creditLimit: 5000 }, L({ creditLimit: 5000 })), null, 'همان سقف');
  assert.equal(perm.need({ creditLimitMinor: 500000 }, L({ creditLimitMinor: 500000 }, { shape: 'v3' }), 'v3'), null);
  assert.deepEqual(perm.need({ creditLimit: 5000 }, L({ creditLimit: 9000 })).req, { amount: 900000 });
  assert.deepEqual(perm.need({ creditLimit: 5000 }, L({ creditLimit: 100 })).req, { amount: 10000 }, 'پایین آوردن هم تغییر است');
  assert.equal(perm.need({ creditLimit: 5000 }, L({ creditLimit: null })).amount, Number.MAX_SAFE_INTEGER, 'برداشتن');
  assert.equal(perm.need({ creditLimit: 5000 }, L({ creditLimit: 'x' })).amount, Number.MAX_SAFE_INTEGER, 'نادرست');
  assert.equal(perm.need({ creditLimit: 5000 }, L({ creditLimit: { $inc: 1 } })).amount, Number.MAX_SAFE_INTEGER, '$inc');
  assert.equal(perm.need({ creditLimit: 5000 }, L({ name: 'x' }, { full: true })).amount, Number.MAX_SAFE_INTEGER, 'راهِ قدیم');
  assert.equal(perm.need({ name: 'x' }, L({ creditLimit: null })), null, 'بی سقف ⇒ بی سقف');
});

test('پرچمِ خاموش (پیش‌فرض): لایه ذخیره نمی‌شود، لایهٔ ازپیش‌مانده بی‌اثر، و شاگرد سقف را مثلِ امروز عوض می‌کند', async () => {
  const set = await setRole('staff', { 'customers.credit_limit': { mode: 'deny' } });
  assert.equal(set.status, 400, JSON.stringify(set.body));
  assert.equal(set.body.error.code, 'not_enforceable_yet');
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.ok(!page.body.policy.editable.includes('customers.credit_limit'));
  await query(`INSERT INTO shop_role_perms (shop_id, role, perms, updated_by, updated_at) VALUES ($1,'staff',$2,'',$3)`,
    [shopId, JSON.stringify({ 'customers.credit_limit': { mode: 'deny' } }), now()]);
  assert.deepEqual(why(await push1(staff, 'pos-1', [op1('debtors', 'd-2', 'update', { creditLimit: 7000 })])), ['applied']);
  assert.equal(await limit1('d-2'), 7000);
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1`, [shopId])).length, 0);
  await query(`DELETE FROM shop_role_perms WHERE shop_id=$1`, [shopId]);
});

test('پرچمِ روشن، v1: بالای `maxAmount` یا برداشتن ⇒ رد و صندوق؛ زیرِ سقف، مشتریِ تازهٔ تا سقف و ویرایشِ بی سقف می‌نشیند؛ تکرار «تکراری»', async () => {
  await liveFlag('shop.credit_limit_perm', true, shopId);
  assert.equal((await setRole('staff', { 'customers.credit_limit': { caps: { maxAmount: 800000 } } })).status, 200);
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.ok(page.body.policy.editable.includes('customers.credit_limit'));
  //  زیرِ سقف ⇒ می‌نشیند؛ تکرارِ همان op ⇒ «تکراری»
  const ok = op1('debtors', 'd-1', 'update', { creditLimit: 8000 });
  assert.deepEqual(why(await push1(staff, 'pos-1', [ok])), ['applied']);
  assert.deepEqual(why(await push1(staff, 'pos-1', [ok])), ['duplicate']);
  assert.equal(await limit1('d-1'), 8000);
  //  بالای سقف ⇒ رد، دفتر همان، صندوقِ تعارض با خودِ op
  const up = op1('debtors', 'd-1', 'update', { creditLimit: 9000, notes: 'یادداشت' });
  assert.deepEqual(why(await push1(staff, 'pos-1', [up])), ['credit_limit_not_allowed']);
  assert.equal(await limit1('d-1'), 8000);
  const box = await boxOf(up.op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.table_name, 'debtors');
  assert.deepEqual([box.detail.perm, box.detail.amount, box.detail.from, box.detail.cap, box.detail.limit, box.detail.path],
    ['customers.credit_limit', 900000, 800000, 'maxAmount', 800000, 'v1']);
  assert.equal(box.loser_value.ops[0].op_id, up.op_id, 'هیچ ویرایشی بی‌صدا گم نمی‌شود');
  //  همان op ِ ردشده دوباره ⇒ باز رد، صندوقِ دوم نه
  assert.deepEqual(why(await push1(staff, 'pos-1', [up])), ['credit_limit_not_allowed']);
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, up.op_id])).length, 1);
  //  برداشتنِ سقف (= بی‌نهایت) ⇒ رد؛ همان `null` از برنامهٔ بی نسخه پیش از نگهبان کنار می‌رود و چیزی عوض نمی‌شود
  assert.deepEqual(why(await push1(staff, 'pos-1', [op1('debtors', 'd-1', 'update', { creditLimit: null })], A00)), ['credit_limit_not_allowed']);
  assert.deepEqual(why(await push1(staff, 'pos-1', [op1('debtors', 'd-1', 'update', { creditLimit: null })])), ['applied']);
  assert.equal(await limit1('d-1'), 8000);
  //  #۲۵: مشتریِ تازه بالای سقفِ نقش یا بی سقف ⇒ رد و صندوق (دورزدنِ پیشین)؛ تا سقف، پیش‌فرض و ویرایشِ بی سقف ⇒ می‌نشیند
  const big = op1('debtors', 'd-new', 'insert', { name: 'تازه', phone: '0700000199', creditLimit: 999999, createdAt: clock });
  const open = op1('debtors', 'd-open', 'insert', { name: 'بی سقف', phone: '0700000197', createdAt: clock });
  assert.deepEqual(why(await push1(staff, 'pos-1', [
    big, open,
    op1('debtors', 'd-zero', 'insert', { name: 'پیش‌فرض', phone: '0700000196', creditLimit: 0, createdAt: clock }),
    op1('debtors', 'd-new', 'insert', { name: 'تازه', phone: '0700000199', creditLimit: 8000, createdAt: clock }),
    op1('debtors', 'd-1', 'update', { notes: 'فقط یادداشت' }),
  ])), ['credit_limit_not_allowed', 'credit_limit_not_allowed', 'applied', 'applied', 'applied']);
  assert.equal(await one(`SELECT 1 FROM sync_rows WHERE app='shop' AND account_id=$1 AND row_id='d-open'`, [shopId]), null, 'هیچ ردیفی');
  const fresh = await boxOf(big.op_id);
  assert.deepEqual([fresh.kind, fresh.detail.amount, fresh.detail.from, fresh.detail.fresh, fresh.detail.limit],
    ['perm_denied', 99999900, 0, true, 800000]);
  assert.equal(fresh.loser_value.ops[0].op_id, big.op_id, 'مدیر همان op را از صندوق «دوباره ثبت با تأییدِ من» می‌کند');
  assert.equal((await boxOf(open.op_id)).detail.amount, null, 'بی سقف');
  //  «درج» روی ردیفِ موجود ویرایش است؛ و درجِ تازه + بالا بردن در همان دسته ⇒ دومی سنجیده می‌شود
  assert.deepEqual(why(await push1(staff, 'pos-1', [
    op1('debtors', 'd-1', 'insert', { name: 'مشتریِ یک', creditLimit: 50000 }),
    op1('debtors', 'd-new2', 'insert', { name: 'تازهٔ دو', creditLimit: 100 }),
    op1('debtors', 'd-new2', 'update', { creditLimit: 9000 }),
  ])), ['credit_limit_not_allowed', 'applied', 'credit_limit_not_allowed']);
  assert.equal(await limit1('d-1'), 8000);
});

test('پرچمِ روشن: «ممنوع» هر تغییری را می‌بندد (پایین آوردن هم)، مدیرِ محدود هم؛ صاحب هرگز؛ نقشِ جعلی در بدنه و فیلد بی‌اثر', async () => {
  assert.equal((await setRole('staff', { 'customers.credit_limit': { mode: 'deny' } })).status, 200);
  assert.equal((await setRole('manager', { 'customers.credit_limit': { mode: 'deny' } })).status, 200);
  assert.deepEqual(why(await push1(staff, 'pos-1', [op1('debtors', 'd-1', 'update', { creditLimit: 100 })])), ['credit_limit_not_allowed']);
  assert.deepEqual(why(await push1(manager, 'mgr-1', [op1('debtors', 'd-1', 'update', { creditLimit: 9000 })])), ['credit_limit_not_allowed']);
  //  نقشِ جعلی: بدنه و فیلدِ op «owner» می‌گویند؛ نقش فقط از عضویتِ توکن است
  const forged = await push1(staff, 'pos-1', [op1('debtors', 'd-1', 'update', { creditLimit: 9000, role: 'owner' })], { role: 'owner' });
  assert.deepEqual(why(forged), ['credit_limit_not_allowed']);
  assert.equal(await limit1('d-1'), 8000);
  //  شاگرد لایهٔ خودش را باز نمی‌کند (نوشتنِ لایه فقط صاحب)
  const me = (await h.get('/api/me', { token: staff.accessToken })).body;
  const uid = me.user?.id || me.id;
  const self = await h.put(`/api/shop/permissions/members/${uid}`, { perms: { 'customers.credit_limit': { mode: 'allow' } } }, { token: staff.accessToken });
  assert.equal(self.status, 403, JSON.stringify(self.body));
  //  صاحب: همیشه
  assert.deepEqual(why(await push1(owner, 'own-1', [op1('debtors', 'd-1', 'update', { creditLimit: 20000 })])), ['applied']);
  assert.equal(await limit1('d-1'), 20000);
});

test('پرچمِ روشن، v3: `creditLimitMinor` ِ مشتریِ موجود بی اجازه ⇒ رد و صندوق؛ مشتریِ تازه فقط با پیش‌فرض؛ صاحب آزاد', async () => {
  const up = op3('debtors', 'd3-1', 'update', { creditLimitMinor: 900000 });
  const r = await push3(staff, 'pos-3', [up]);
  assert.deepEqual(why(r), ['credit_limit_not_allowed'], JSON.stringify(r.body));
  assert.equal(await limit3('d3-1'), 500000);
  const box = await boxOf(up.op_id);
  assert.deepEqual([box.kind, box.detail.perm, box.detail.amount, box.detail.path], ['perm_denied', 'customers.credit_limit', 900000, 'v3']);
  //  `creditLimit` ِ افغانی روی ردیفِ v3 هم همان
  assert.deepEqual(why(await push3(staff, 'pos-3', [op3('debtors', 'd3-1', 'update', { creditLimit: null })], A00)), ['credit_limit_not_allowed']);
  assert.deepEqual(why(await push3(staff, 'pos-3', [op3('debtors', 'd3-1', 'update', { creditLimitMinor: null })], A00)), ['credit_limit_not_allowed']);
  //  #۲۵: شاگردِ «ممنوع» مشتریِ تازه را فقط با سقفِ پیش‌فرض (صفر) می‌سازد؛ سقفِ دیگر یا بی سقف ⇒ رد و صندوق
  const big3 = op3('debtors', 'd3-new', 'insert', { name: 'تازهٔ v3', phone: '0700000198', creditLimitMinor: 99999900 });
  const r3 = await push3(staff, 'pos-3', [big3]);
  assert.deepEqual(why(r3), ['credit_limit_not_allowed'], JSON.stringify(r3.body));
  assert.deepEqual([(await boxOf(big3.op_id)).detail.fresh, (await boxOf(big3.op_id)).detail.path], [true, 'v3']);
  assert.deepEqual(why(await push3(staff, 'pos-3', [op3('debtors', 'd3-open', 'insert', { name: 'بی سقفِ v3' })])), ['credit_limit_not_allowed']);
  assert.deepEqual(why(await push3(staff, 'pos-3', [
    op3('debtors', 'd3-new', 'insert', { name: 'تازهٔ v3', phone: '0700000198', creditLimitMinor: 0 }),
    op3('debtors', 'd3-1', 'update', { notes: 'بی سقف' }),
  ])), ['applied', 'applied']);
  assert.equal(await limit3('d3-new'), 0);
  //  مدیرِ «ممنوع» هم؛ صاحب با هر سقف
  assert.deepEqual(why(await push3(manager, 'mgr-3', [op3('debtors', 'd3-m', 'insert', { name: 'مدیر', creditLimitMinor: 100 })])), ['credit_limit_not_allowed']);
  assert.deepEqual(why(await push3(owner, 'own-3', [op3('debtors', 'd3-o', 'insert', { name: 'صاحب', creditLimitMinor: 99999900 })])), ['applied']);
  //  راه‌های کناری: «دلتا» روی سقف (شمارنده نیست) و صفِ v2 (op ِ v1 از درِ v3)
  const delta = await push3(staff, 'pos-3', [op3('debtors', 'd3-1', 'delta', { creditLimitMinor: 400000 })]);
  assert.notEqual(delta.body.results[0].status, 'applied', JSON.stringify(delta.body));
  const v2 = { op_id: oid(), table: 'debtors', row_id: 'd3-1', type: 'update', ts: Date.now(), fields: { creditLimit: 9000 } };
  const r2 = await push3(staff, 'pos-3', [v2], { format: 'v2', schema_version: 1 });
  assert.equal(r2.body.results[0].reason, 'credit_limit_not_allowed', JSON.stringify(r2.body));
  assert.equal(await limit3('d3-1'), 500000);
  //  تکرارِ op ِ پذیرفته ⇒ «تکراری»
  const again = op3('debtors', 'd3-1', 'update', { notes: 'دوباره' });
  assert.deepEqual(why(await push3(staff, 'pos-3', [again])), ['applied']);
  assert.deepEqual(why(await push3(staff, 'pos-3', [again])), ['duplicate']);
  assert.deepEqual(why(await push3(owner, 'own-3', [op3('debtors', 'd3-1', 'update', { creditLimitMinor: 900000 })])), ['applied']);
  assert.equal(await limit3('d3-1'), 900000);
});

test('پرچمِ روشن، راهِ قدیم: سقفِ تازه یا ردیفِ کاملِ بی سقف ⇒ ۴۰۳ و هیچ نوشتنی؛ همان سقف و مشتریِ تازه با پیش‌فرض آزاد', async () => {
  const t = Date.now();
  const r0 = await legacy(owner, [{ collection: 'debtors', id: 'dl-1', updatedAt: t, data: { name: 'مشتریِ وب', phone: '0700000109', creditLimit: 5000 } }]);
  assert.equal(r0.status, 200, JSON.stringify(r0.body));
  const up = await legacy(staff, [{ collection: 'debtors', id: 'dl-1', updatedAt: t + 10, data: { name: 'مشتریِ وب', phone: '0700000109', creditLimit: 9000 } }]);
  assert.equal(up.status, 403, JSON.stringify(up.body));
  assert.equal(up.body.error.code, 'credit_limit_not_allowed');
  //  ردیفِ کاملِ بی کلیدِ سقف = برداشتنِ سقف
  const drop = await legacy(staff, [{ collection: 'debtors', id: 'dl-1', updatedAt: t + 20, data: { name: 'مشتریِ وب', phone: '0700000109' } }]);
  assert.equal(drop.status, 403, JSON.stringify(drop.body));
  const row = await one(`SELECT data FROM debtors WHERE shop_id=$1 AND id='dl-1'`, [shopId]);
  assert.equal(row.data.creditLimit, 5000);
  //  همان سقف (ویرایشِ نام) و مشتریِ تازه ⇒ می‌نشیند
  const same = await legacy(staff, [{ collection: 'debtors', id: 'dl-1', updatedAt: t + 30, data: { name: 'نامِ تازه', phone: '0700000109', creditLimit: 5000 } }]);
  assert.deepEqual([same.status, same.body.applied], [200, 1], JSON.stringify(same.body));
  //  #۲۵: مشتریِ تازه با سقفِ دیگر یا ردیفِ کاملِ بی سقف ⇒ ۴۰۳؛ با سقفِ پیش‌فرض (صفر) ⇒ می‌نشیند
  for (const data of [{ name: 'تازهٔ وب', creditLimit: 99999 }, { name: 'تازهٔ وب' }]) {
    const bad = await legacy(staff, [{ collection: 'debtors', id: 'dl-2', updatedAt: t + 30, data }]);
    assert.deepEqual([bad.status, bad.body.error?.code], [403, 'credit_limit_not_allowed'], JSON.stringify(bad.body));
  }
  assert.equal(await one(`SELECT 1 FROM debtors WHERE shop_id=$1 AND id='dl-2'`, [shopId]), null);
  const fresh = await legacy(staff, [{ collection: 'debtors', id: 'dl-2', updatedAt: t + 30, data: { name: 'تازهٔ وب', creditLimit: 0 } }]);
  assert.deepEqual([fresh.status, fresh.body.applied], [200, 1], JSON.stringify(fresh.body));
  //  صاحب ⇒ آزاد
  const own = await legacy(owner, [{ collection: 'debtors', id: 'dl-1', updatedAt: t + 40, data: { name: 'نامِ تازه', creditLimit: 30000 } }]);
  assert.deepEqual([own.status, own.body.applied], [200, 1], JSON.stringify(own.body));
  assert.equal((await one(`SELECT data FROM debtors WHERE shop_id=$1 AND id='dl-1'`, [shopId])).data.creditLimit, 30000);
});

test('جداییِ دکان: همان شناسهٔ مشتری در دکانِ دیگر (پرچمِ خاموش) فقط ردیفِ همان دکان را عوض می‌کند', async () => {
  assert.deepEqual(why(await push1(staffB, 'b-1', [op1('debtors', 'd-1', 'insert', { name: 'مشتریِ ب', creditLimit: 1 })])), ['applied']);
  assert.deepEqual(why(await push1(staffB, 'b-1', [op1('debtors', 'd-1', 'update', { creditLimit: 999999 })])), ['applied']);
  assert.equal(await limit1('d-1', shopB), 999999);
  assert.equal(await limit1('d-1'), 20000, 'دکانِ الف دست نخورد');
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1`, [shopB])).length, 0);
});
