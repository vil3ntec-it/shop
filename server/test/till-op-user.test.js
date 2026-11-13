'use strict';
/**
 * S46 (STF-04) — «کاربرِ سنجیدهٔ صندوق» روی هر op ِ نشستهٔ دکان (`lib/till-op-user.js`، مهاجرتِ ۷۹۰، پرچمِ `shop.till_op_user`).
 *
 *  • پرچمِ خاموش (پیش‌فرض) ⇒ هیچ ردیفی، و درهای خواندن (پیش‌ساخته، بی رابطِ برنامه) همان درِ ناشناخته (۴۰۴)؛
 *  • پرچم روشن و `shop.till_pin` خاموش ⇒ ردیف با کاربرِ NULL (`pin_off`) — هرگز حدس؛
 *  • هر دو روشن و نوبتِ سنجیده روی **دستگاهِ توکن** ⇒ همان عضو، حتی اگر op نامِ دیگری ادعا کند یا `device_id` ِ بدنه دیگر باشد؛
 *    fields و hash ِ op در `oplog` دست‌نخورده و پاسخِ push همان؛
 *  • op ِ تکراری (دوباره فرستاده) ⇒ یک ردیف؛ op ِ ردشده (موتور یا میان‌افزار) ⇒ هیچ، و جاگذاریِ پس از ردِ میان‌دسته درست؛
 *  • op ِ ساخته‌شده پیش از نوبت، دیررسیده (بیش از ۳۰ دقیقه) یا بی نوبت ⇒ NULL با دلیل؛
 *  • Sync v3 هم؛ دکانِ دیگر نه می‌نویسد نه می‌بیند؛ صاحب و مدیر همه را، بقیه فقط خودشان؛
 *  • خطای نوشتنِ ردیف push را نمی‌شکند (بهترین‌کوشش).
 */
process.env.TILL_PIN_ITER = '1000';
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { hashToken } = require('../src/lib/tokens');
const { resetAll } = require('../src/middleware/ratelimit');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

let admin = '';
let A;   //  دکانِ «الف»: صاحب = حسابِ دستگاهِ صندوق؛ مدیر (دستگاهِ خودش)؛ شاگرد (کاربرِ صندوق)
let B;   //  دکانِ «ب»
const PIN = { owner: '482915', staff: '615083', manager: '730264' };

let n = 0;
const oid = () => `01JS46${String(Date.now()).slice(-8)}${String(++n).padStart(12, '0')}`;
let tick = 0;
const H = (node, wall = Date.now()) => hlc.format({ wall: wall + (++tick), counter: 0, node });

async function shopOf(label) {
  const u = await h.newUser(`صاحب-${label}`);
  const r = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...u, token: u.accessToken, shopId: r.body.shop.id };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [id, s.shopId, u.user.id, role, now()],
  );
  return { ...u, token: u.accessToken, memberId: id };
}
const ok = (r, code = 200) => { assert.equal(r.status, code, JSON.stringify(r.body)); return r.body; };
const flag = (s, key, value = true) => h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const deviceOf = async (who) => (await one(
  'SELECT d.id FROM tokens t JOIN devices d ON d.id = t.device_id WHERE t.token_hash=$1', [hashToken(who.token)])).id;
const verify = (host, target, pin) => h.post('/api/shop/till-pin/verify', { memberId: target.memberId, pin }, { token: host.token });
const stamps = (s) => many('SELECT * FROM shop_till_op_users WHERE shop_id=$1 ORDER BY at, op_id', [s.shopId]);
const stampOf = (s, opId) => one('SELECT * FROM shop_till_op_users WHERE shop_id=$1 AND op_id=$2', [s.shopId, opId]);
const statuses = (r) => r.body.results.map((x) => x.status);

function sale(id, extra = {}) {
  return {
    id, invoiceNumber: 1, total: 1000, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 1000,
    paymentMethod: 'cash', debtorId: null, paidAmount: 1000, remaining: 0, status: 'completed', debtGiven: 0, debtSettled: 0,
    createdAt: Date.now(), date: '2026-11-11', syncStatus: 'pending', ...extra,
  };
}
const v1op = (rowId, fields, { ts = Date.now(), type = 'insert', table = 'sales' } = {}) =>
  ({ op_id: oid(), ts, table, row_id: rowId, type, fields });
const push1 = (who, ops, dev = 's46-claimed-dev') => h.api('POST', '/api/sync/v1/push', {
  token: who.token, headers: { 'X-App': 'shop' }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: who.token, body: { device_id: 's46-v3-dev', protocol: 3, ops },
});
function sale3(id, wall) {
  const tx = `tx.${id}`;
  return [
    { op_id: oid(), hlc: H('pos', wall), table: 'sales', row_id: id, kind: 'insert', tx,
      fields: { totalMinor: 100000, discountMinor: 0, finalTotalMinor: 100000, paidAmountMinor: 100000, status: 'completed', paymentMethod: 'cash' } },
    { op_id: oid(), hlc: H('pos', wall), table: 'saleItems', row_id: `${id}-i1`, kind: 'insert', tx,
      fields: { saleId: id, productId: 'p1', quantityMilli: 1000, unitPriceMinor: 100000, totalPriceMinor: 100000, purchasePriceMinor: 70000 } },
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'s46-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 's46-admin', password: 'Admin!12345' })).body.token;
  A = await shopOf('الف-۴۶');
  A.memberId = (await h.get('/api/shop/till-pin/config', { token: A.token })).body.memberId;
  A.staff = await member(A, 'staff', 'شاگردِ صندوقِ ۴۶');
  A.manager = await member(A, 'manager', 'مدیرِ صندوقِ ۴۶');
  A.device = await deviceOf(A);
  B = await shopOf('ب-۴۶');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('پرچمِ خاموش (پیش‌فرض): push همان و هیچ ردیفی؛ درهای خواندنِ پیش‌ساخته همان درِ ناشناخته (۴۰۴)', async () => {
  const r = await push1(A, [v1op('off-1', sale('off-1'))]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual(await stamps(A), []);
  //  S46: رابطِ «کارِ هر کاربرِ صندوق» در برنامه نیست (PARTIAL-MAP) ⇒ با پرچمِ خاموش این درها نیستند
  const unknown = await h.get('/api/shop/till-pin/no-such-door', { token: A.token });
  assert.equal(unknown.status, 404, JSON.stringify(unknown.body));
  for (const res of [await h.get('/api/shop/till-pin/ops', { token: A.token }),
    await h.get('/api/shop/till-pin/ops/row?table=sales&row_id=off-1', { token: A.token })]) {
    assert.equal(res.status, 404, JSON.stringify(res.body));
    assert.deepEqual(res.body, unknown.body, 'همان پاسخِ درِ ناشناخته');
  }
});

test('پرچم روشن، PIN ِ سرور خاموش ⇒ ردیف با کاربرِ ناشناخته (pin_off)، دستگاه و کاربر از توکن', async () => {
  await flag(A, 'shop.till_op_user');
  const op = v1op('pinoff-1', sale('pinoff-1'));
  ok(await push1(A, [op]));
  const s = await stampOf(A, op.op_id);
  assert.ok(s, 'ردیف نوشته شد');
  assert.equal(s.basis, 'pin_off');
  assert.equal(s.till_member_id, null);
  assert.equal(s.till_session_id, null);
  assert.equal(s.path, 'v1');
  assert.equal(s.table_name, 'sales');
  assert.equal(s.row_id, 'pinoff-1');
  assert.equal(s.device_id, A.device, 'دستگاهِ توکن، نه `device_id` ِ بدنه');
  assert.equal(s.device_user_id, A.user.id);
});

test('نوبتِ سنجیده ⇒ همان عضو، با ادعای کاربرِ دیگر هم؛ op و پاسخ دست‌نخورده؛ تکرار ⇒ یک ردیف؛ ردشده ⇒ هیچ', async () => {
  await flag(A, 'shop.till_pin');
  ok(await h.put('/api/shop/till-pin/me', { pin: PIN.owner }, { token: A.token }));
  ok(await h.put(`/api/shop/till-pin/members/${A.staff.memberId}`, { pin: PIN.staff }, { token: A.token }));
  const sess = ok(await verify(A, A.staff, PIN.staff)).session;
  assert.equal(sess.memberId, A.staff.memberId);

  //  برنامه نامِ مدیر را ادعا می‌کند (فروشنده و کاربرِ صندوق) — سرور همان شاگردِ سنجیده را می‌نویسد
  const claim = sale('ok-1', { seller: 'مدیرِ صندوقِ ۴۶', till_user: A.manager.memberId, tillUserId: A.manager.memberId });
  const base = v1op('ok-1', claim);
  const op = { ...base, hash: syncV1.hashOf(base) };
  const r = await push1(A, [op], 'claimed-other-device');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied']);
  assert.ok(!/till|tillUser|stamp/i.test(JSON.stringify(r.body)), 'پاسخِ push چیزِ تازه‌ای ندارد');
  const s = await stampOf(A, op.op_id);
  assert.equal(s.basis, 'session');
  assert.equal(s.till_member_id, A.staff.memberId);
  assert.equal(s.till_session_id, sess.id);
  assert.equal(s.device_id, A.device);
  assert.equal(Number(s.op_ts), op.ts);
  const log = await one('SELECT fields, hash, device_id FROM oplog WHERE op_id=$1', [op.op_id]);
  assert.deepEqual(log.fields, claim, 'fields ِ op دست‌نخورده');
  assert.equal(log.hash, op.hash, 'hash ِ op دست‌نخورده');
  const row = await one("SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='sales' AND row_id='ok-1'", [A.shopId]);
  assert.equal(row.data.seller, 'مدیرِ صندوقِ ۴۶', 'ادعای برنامه همان می‌ماند');

  //  دوباره فرستاده ⇒ «تکراری» و همان یک ردیف
  const again = await push1(A, [op]);
  assert.deepEqual(statuses(again), ['duplicate']);
  assert.equal((await many('SELECT 1 FROM shop_till_op_users WHERE shop_id=$1 AND op_id=$2', [A.shopId, op.op_id])).length, 1);

  //  ردِ موتور: جدولِ ناشناخته و op_id ِ دکانِ دیگر ⇒ هیچ ردیف؛ op ِ درستِ همان دسته ⇒ ردیف
  const taken = v1op('b-1', sale('b-1'));
  ok(await push1(B, [taken]));
  const bad = { op_id: oid(), ts: Date.now(), table: 'no_such_table', row_id: 'x', type: 'insert', fields: { a: 1 } };
  const stolen = { ...v1op('ok-2', sale('ok-2')), op_id: taken.op_id };
  const good = v1op('ok-3', sale('ok-3'));
  const mix = await push1(A, [bad, stolen, good]);
  assert.equal(mix.status, 200, JSON.stringify(mix.body));
  assert.deepEqual(statuses(mix), ['rejected', 'rejected', 'applied']);
  assert.equal(await stampOf(A, bad.op_id), null);
  assert.equal(await stampOf(A, taken.op_id), null);
  assert.equal(await stampOf(B, taken.op_id), null, 'دکانِ «ب» پرچم ندارد ⇒ هیچ');
  assert.equal((await stampOf(A, good.op_id)).till_member_id, A.staff.memberId);
});

test('ردِ میان‌افزار (نگهبانِ فقط‌دکان) در میانهٔ دسته ⇒ جاگذاری درست، ردشده بی ردیف', async () => {
  //  مدیر حسابِ دستگاهِ خودش؛ شاگرد پشتِ آن صندوق. op ِ فاکتورِ نادرست را `staff-approval.v1Guard` (نه موتور) برمی‌دارد
  A.managerDevice = await deviceOf(A.manager);
  ok(await verify(A.manager, A.staff, PIN.staff));
  const first = v1op('mg-1', sale('mg-1'));
  const broken = { op_id: oid(), ts: Date.now(), table: 'sales', row_id: '', type: 'insert', fields: sale('') };
  const last = v1op('mg-2', sale('mg-2'));
  const r = await push1(A.manager, [first, broken, last]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'rejected', 'applied']);
  for (const op of [first, last]) {
    const s = await stampOf(A, op.op_id);
    assert.equal(s.till_member_id, A.staff.memberId);
    assert.equal(s.device_id, A.managerDevice);
    assert.equal(s.device_user_id, A.manager.user.id);
  }
  assert.equal(await stampOf(A, broken.op_id), null);

  //  ردی که فقط میان‌افزار می‌دهد (POS-40 ِ `till-guard.v1Guard`، پرچمِ `shop.sync.pay_fix`؛ موتور «pay_fix» نمی‌شناسد) — صاحب هم
  await flag(A, 'shop.sync.pay_fix');
  ok(await verify(A, A.staff, PIN.staff));
  const p1 = v1op('pf-1', sale('pf-1'));
  const fix = v1op('pf-fix', { type: 'pay_fix', notes: 'نادرست' }, { table: 'auditLog' });
  const p2 = v1op('pf-2', sale('pf-2'));
  const rp = await push1(A, [p1, fix, p2]);
  assert.equal(rp.status, 200, JSON.stringify(rp.body));
  assert.deepEqual(statuses(rp), ['applied', 'rejected', 'applied']);
  assert.match(rp.body.results[1].reason, /^pay_fix/);
  assert.equal(await one('SELECT 1 AS x FROM oplog WHERE op_id=$1', [fix.op_id]), null, 'میان‌افزار برداشت، موتور ندید');
  assert.equal((await stampOf(A, p1.op_id)).till_member_id, A.staff.memberId);
  assert.equal((await stampOf(A, p2.op_id)).till_member_id, A.staff.memberId);
  assert.equal(await stampOf(A, fix.op_id), null);
  await flag(A, 'shop.sync.pay_fix', false);
});

test('ساخته پیش از نوبت، دیررسیده یا بی نوبت ⇒ ناشناخته با دلیل، هرگز کاربرِ کنونی', async () => {
  const sess = ok(await verify(A, A.staff, PIN.staff)).session;
  const before = v1op('early-1', sale('early-1'), { ts: sess.startedAt - 60_000 });
  ok(await push1(A, [before]));
  let s = await stampOf(A, before.op_id);
  assert.equal(s.basis, 'before_session');
  assert.equal(s.till_member_id, null);

  //  نوبتی که سه ساعت پیش باز شد؛ فروشِ دو ساعت پیش (بی اینترنت) دیر می‌رسد
  await query('UPDATE shop_till_sessions SET started_at=$3 WHERE shop_id=$1 AND id=$2', [A.shopId, sess.id, Date.now() - 3 * 3600_000]);
  const late = v1op('late-1', sale('late-1'), { ts: Date.now() - 2 * 3600_000 });
  const fresh = v1op('fresh-1', sale('fresh-1'));
  ok(await push1(A, [late, fresh]));
  s = await stampOf(A, late.op_id);
  assert.equal(s.basis, 'late');
  assert.equal(s.till_member_id, null);
  assert.equal((await stampOf(A, fresh.op_id)).till_member_id, A.staff.memberId);

  //  قفلِ صندوق (Ctrl+L) نوبت را می‌بندد ⇒ بی نوبت
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.token }));
  const after = v1op('nos-1', sale('nos-1'));
  ok(await push1(A, [after]));
  s = await stampOf(A, after.op_id);
  assert.equal(s.basis, 'no_session');
  assert.equal(s.till_member_id, null);
});

test('Sync v3: همان قاعده؛ تکرار یک ردیف؛ ردشده هیچ', async () => {
  const sess = ok(await verify(A, A.staff, PIN.staff)).session;
  const ops = sale3('v3-1', Date.now());
  const r = await push3(A, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  for (const op of ops) {
    const s = await stampOf(A, op.op_id);
    assert.equal(s.path, 'v3');
    assert.equal(s.basis, 'session');
    assert.equal(s.till_member_id, A.staff.memberId);
    assert.equal(s.till_session_id, sess.id);
    assert.equal(s.device_id, A.device);
  }
  assert.equal((await stampOf(A, ops[0].op_id)).table_name, 'sales');
  const again = await push3(A, ops);
  assert.deepEqual(statuses(again), ['duplicate', 'duplicate']);
  assert.equal((await many('SELECT 1 FROM shop_till_op_users WHERE shop_id=$1 AND op_id = ANY($2::text[])',
    [A.shopId, ops.map((o) => o.op_id)])).length, 2);
  const bad = { op_id: oid(), hlc: H('pos'), table: 'no_such_table', row_id: 'x', kind: 'insert', fields: { a: 1 } };
  const rb = await push3(A, [bad]);
  assert.deepEqual(statuses(rb), ['rejected']);
  assert.equal(await stampOf(A, bad.op_id), null);
  //  پیش از نوبت (دیوارِ HLC) ⇒ ناشناخته
  const early = sale3('v3-early', sess.startedAt - 120_000);
  ok(await push3(A, early));
  assert.equal((await stampOf(A, early[0].op_id)).basis, 'before_session');
});

test('خواندن: صاحب و مدیر همه، شاگرد فقط خودش؛ جمعِ هر کاربر؛ ردیفِ یک فاکتور', async () => {
  const all = ok(await h.get('/api/shop/till-pin/ops', { token: A.token }));
  assert.equal(all.truncated, false);
  assert.ok(all.ops.some((o) => o.tillUser === null), 'ناشناخته‌ها برای صاحب');
  const okOne = all.ops.find((o) => o.rowId === 'ok-1');
  assert.equal(okOne.tillUser.memberId, A.staff.memberId);
  assert.equal(okOne.tillUser.name, 'شاگردِ صندوقِ ۴۶');
  assert.equal(okOne.deviceUser.userId, A.user.id);
  assert.equal(okOne.basis, 'session');
  const staffSum = all.byUser.find((u) => u.memberId === A.staff.memberId);
  const staffOps = all.ops.filter((o) => o.tillUser && o.tillUser.memberId === A.staff.memberId);
  assert.equal(staffSum.ops, staffOps.length);
  assert.equal(staffSum.sales, staffOps.filter((o) => o.table === 'sales').length);
  assert.ok(all.byUser.some((u) => u.memberId === null && u.name === ''), 'ناشناخته به نامِ کسی نیست');

  const mine = ok(await h.get(`/api/shop/till-pin/ops?member=${A.memberId}`, { token: A.staff.token }));
  assert.ok(mine.ops.length > 0);
  assert.ok(mine.ops.every((o) => o.tillUser && o.tillUser.memberId === A.staff.memberId), 'شاگرد فقط خودش (member نادیده)');
  assert.deepEqual(mine.byUser.map((u) => u.memberId), [A.staff.memberId]);
  const mgr = ok(await h.get(`/api/shop/till-pin/ops?member=${A.staff.memberId}&table=sales`, { token: A.manager.token }));
  assert.ok(mgr.ops.length > 0 && mgr.ops.every((o) => o.table === 'sales' && o.tillUser.memberId === A.staff.memberId));

  const row = ok(await h.get('/api/shop/till-pin/ops/row?table=sales&row_id=ok-1', { token: A.token }));
  assert.deepEqual(row.ops.map((o) => [o.tillUser && o.tillUser.memberId, o.opType]), [[A.staff.memberId, 'insert']]);
  //  فاکتورِ ناشناخته برای شاگرد هیچ
  assert.deepEqual(ok(await h.get('/api/shop/till-pin/ops/row?table=sales&row_id=pinoff-1', { token: A.staff.token })).ops, []);
  assert.equal((await h.get('/api/shop/till-pin/ops/row?table=sales', { token: A.token })).status, 400);
  assert.equal((await h.get('/api/shop/till-pin/ops?from=9&to=1', { token: A.token })).status, 400);
});

test('دکانِ دیگر: نوبتِ «الف» به push ِ «ب» نمی‌رسد؛ «ب» ردیف‌های «الف» را نمی‌بیند', async () => {
  ok(await verify(A, A.staff, PIN.staff));
  await flag(B, 'shop.till_op_user');
  await flag(B, 'shop.till_pin');
  const op = v1op('b-2', sale('b-2'));
  ok(await push1(B, [op]));
  const s = await stampOf(B, op.op_id);
  assert.equal(s.basis, 'no_session');
  assert.equal(s.till_member_id, null);
  assert.equal(await stampOf(A, op.op_id), null);
  const seen = ok(await h.get('/api/shop/till-pin/ops', { token: B.token }));
  assert.deepEqual(seen.ops.map((o) => o.rowId), ['b-2']);
  assert.deepEqual(ok(await h.get('/api/shop/till-pin/ops/row?table=sales&row_id=ok-1', { token: B.token })).ops, []);
  //  «ب» با شناسهٔ عضوِ «الف» هم چیزی نمی‌گیرد
  assert.deepEqual(ok(await h.get(`/api/shop/till-pin/ops?member=${A.staff.memberId}`, { token: B.token })).ops, []);
});

test('بهترین‌کوشش: خطای نوشتنِ ردیف push را نمی‌شکند و پاسخ همان است', async () => {
  ok(await verify(A, A.staff, PIN.staff));
  await query('ALTER TABLE shop_till_op_users RENAME TO shop_till_op_users_gone');
  try {
    const r = await push1(A, [v1op('err-1', sale('err-1'))]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(statuses(r), ['applied']);
    const r3 = await push3(A, sale3('err-3', Date.now()));
    assert.equal(r3.status, 200, JSON.stringify(r3.body));
    assert.deepEqual(statuses(r3), ['applied', 'applied']);
  } finally {
    await query('ALTER TABLE shop_till_op_users_gone RENAME TO shop_till_op_users');
  }
  assert.equal((await many("SELECT 1 FROM shop_till_op_users WHERE shop_id=$1 AND row_id IN ('err-1','err-3')", [A.shopId])).length, 0);
});
