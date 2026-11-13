'use strict';
/**
 * STF-02 — «درخواستِ تأیید» از راهِ دور: شاگرد از صندوق می‌پرسد، صاحب یا مدیر روی **دستگاهِ خودش** تأیید می‌کند
 * (`/api/shop/approval-requests`، `lib/approval-requests.js`، پرچمِ خاموشِ `shop.approval_requests`).
 *
 * سنجه‌ها: پرچمِ خاموش همان امروز؛ تصمیم فقط در سرور؛ بسته به دکان + کار + سند + مقدار؛ عمرِ کوتاه؛ یک‌بارمصرف
 * (همان op دوباره تکرار است، opِ دیگر بازپخش)؛ بی‌خطرِ تکرار با کلید؛ خودتأیید و تأییدکنندهٔ کم‌اختیار نه؛ تأییدکننده‌ای
 * که پس از تأیید اختیارش کم شد هم نه؛ دکانِ دیگر هیچ؛ و سابقه (audit_logs + shop_approvals) برای هر گام.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let shopId;
let owner;
let manager;
let staff;
let other;      //  صاحبِ دکانِ «ب»
let n = 0;
const oid = () => `01JAR${String(++n).padStart(21, '0')}`;
const key = () => `k-${Date.now().toString(36)}-${++n}`;
let tick = 0;
const H = (node, wall = Date.now() + (++tick)) => hlc.format({ wall, counter: 0, node });

const BASE = '/api/shop/approval-requests';
const flag = (value, scope = 'all') => h.put('/api/admin/pump/live-config', { scope, key: 'shop.approval_requests', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const setRole = (role, perms) => h.roleLayer(owner.accessToken, role, perms)
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const ask = (who, body) => { resetAll(); return h.post(BASE, { key: key(), reason: 'مشتریِ همیشگی', ...body }, { token: who.accessToken }); };
const act = (who, id, verb, body = {}) => { resetAll(); return h.post(`${BASE}/${id}/${verb}`, body, { token: who.accessToken }); };
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: who.accessToken, body: { device_id: device, protocol: 3, ops },
});
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: 1, ops },
});
const sale = (id, total, discount, extra = {}) => ({
  op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'insert',
  fields: { totalMinor: total, discountMinor: discount, finalTotalMinor: total - discount, status: 'completed' }, ...extra,
});
const ref = (id) => ({ v: 1, request: id });
const reqRow = (id) => one('SELECT * FROM shop_approval_requests WHERE id=$1', [id]);
const row3 = (table, id) => one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
/** درخواستِ تأییدشده به دستِ صاحب برای تخفیفِ ۲۰٪ ِ یک فاکتورِ ۱۰٬۰۰۰ */
async function approved(doc, { pctBp = 2000, amount = 2000, by = owner } = {}) {
  const r = await ask(staff, { perm: 'sales.discount', doc, pctBp, amount });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const a = await act(by, r.body.request.id, 'approve');
  assert.equal(a.status, 200, JSON.stringify(a.body));
  return r.body.request.id;
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ar-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'ar-admin', password: 'Admin!12345' })).body.token;
  const o = await h.newUser('صاحبِ دکانِ درخواست', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ درخواست' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ درخواست', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ درخواست', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
  const b = await h.newUser('صاحبِ دکانِ ب', 'shop');
  assert.equal((await h.post('/api/shop', { name: 'دکانِ ب' }, { token: b.accessToken })).status, 201);
  other = await h.signIn(b, 'shop');
  //  شاگرد تخفیف را «نیازمندِ تأیید» دارد؛ مدیر تا ۱۰٪
  await setRole('staff', { 'sales.discount': { mode: 'approve' } });
  await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } } });
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): درها ۴۰۳ جز /config، و op با ارجاع همان «برگهٔ نادرست» ِ امروز', async () => {
  const cfg = await h.get(`${BASE}/config`, { token: staff.accessToken });
  assert.equal(cfg.status, 200);
  //  STF-03 (`claude/credit-cap-2`): `v3` = سرور Sync v3 دارد — برنامه دکمهٔ کارِ فقط-v3 را بی آن نشان نمی‌دهد
  assert.deepEqual(cfg.body, { enabled: false, approver: false, ttlMs: 600000, v3: true });
  assert.equal((await h.get(`${BASE}/config`, { token: manager.accessToken })).body.approver, true);
  const r = await ask(staff, { perm: 'sales.discount', doc: 'sales/s-off', pctBp: 2000, amount: 2000 });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'approval_requests_disabled');
  assert.equal((await h.get(BASE, { token: owner.accessToken })).body.error.code, 'approval_requests_disabled');
  const op = sale('s-off', 10000, 2000, { approval: ref('apr_000000000000000000000000') });
  const p = await push3(staff, 's-off', [op]);
  assert.equal(p.body.results[0].status, 'rejected');
  assert.equal(p.body.results[0].reason, 'approval_invalid');
  assert.equal((await many('SELECT 1 FROM shop_approval_requests')).length, 0);
  assert.equal((await many('SELECT 1 FROM shop_approvals WHERE shop_id=$1', [shopId])).length, 0);
});

test('ساختن: فقط «نیازمندِ تأیید» می‌پرسد؛ همان کلید بی‌خطر، کلیدِ دوباره با محتوای دیگر ۴۰۹', async () => {
  await flag(true);
  const r = await ask(staff, { key: 'same-key-001', perm: 'sales.discount', doc: 'sales/s-mk', pctBp: 2000, amount: 2000 });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.match(r.body.request.id, /^apr_[0-9a-f]{24}$/);
  assert.equal(r.body.request.status, 'pending');
  assert.equal(r.body.request.requesterId, staff.user.id);
  assert.ok(r.body.request.expiresAt - r.body.request.createdAt === 600000);
  const again = await ask(staff, { key: 'same-key-001', perm: 'sales.discount', doc: 'sales/s-mk', pctBp: 2000, amount: 2000 });
  assert.equal(again.status, 200);
  assert.equal(again.body.duplicate, true);
  assert.equal(again.body.request.id, r.body.request.id);
  const reused = await ask(staff, { key: 'same-key-001', perm: 'sales.discount', doc: 'sales/s-mk', pctBp: 5000, amount: 5000 });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'request_key_reused');
  //  صاحب خودش مجاز است؛ مدیرِ زیرِ سقف هم
  assert.equal((await ask(owner, { perm: 'sales.discount', doc: 'sales/s-o', pctBp: 2000, amount: 2000 })).body.error.code, 'approval_not_needed');
  assert.equal((await ask(manager, { perm: 'sales.discount', doc: 'sales/s-m', pctBp: 500, amount: 500 })).body.error.code, 'approval_not_needed');
  //  مدیرِ بالای سقف می‌پرسد
  assert.equal((await ask(manager, { perm: 'sales.discount', doc: 'sales/s-m2', pctBp: 2000, amount: 2000 })).status, 201);
  //  «ممنوع» (لغو برای شاگرد) ⇒ همان کدِ رد، نه درخواست
  const deny = await ask(staff, { perm: 'sales.cancel', doc: 'sales/s-c', amount: 1000 });
  assert.equal(deny.status, 403);
  assert.equal(deny.body.error.code, 'cancel_not_allowed');
  //  ورودیِ بد
  assert.equal((await ask(staff, { perm: 'orders.refund', doc: 'sales/x' })).body.error.code, 'bad_perm');
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'bad doc' , amount: 1 })).body.error.code, 'request_invalid');
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'sales/s-v' })).body.error.code, 'bad_value');
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'sales/s-v', amount: -1 })).body.error.code, 'bad_value');
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'sales/s-v', amount: 1, reason: '  ' })).body.error.code, 'bad_reason');
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'sales/s-v', amount: 1, ttlMs: 60 * 60_000 })).body.error.code, 'bad_time');
  //  سابقه
  const log = await one(`SELECT * FROM audit_logs WHERE shop_id=$1 AND action='approval_request.create' AND target_id=$2`,
    [shopId, r.body.request.id]);
  assert.equal(log.user_id, staff.user.id);
});

test('دیدن: صاحب و مدیر همه را، شاگرد فقط خودش؛ دکانِ دیگر هیچ (۴۰۴)', async () => {
  const id = (await ask(staff, { perm: 'sales.discount', doc: 'sales/s-see', pctBp: 2000, amount: 2000 })).body.request.id;
  const all = await h.get(BASE, { token: owner.accessToken });
  assert.equal(all.status, 200);
  const mine = all.body.requests.find((r) => r.id === id);
  assert.equal(mine.status, 'pending');
  assert.ok(mine.requesterName);
  assert.ok((await h.get(BASE, { token: manager.accessToken })).body.requests.some((r) => r.id === id));
  const own = await h.get(BASE, { token: staff.accessToken });
  assert.ok(own.body.requests.every((r) => r.requesterId === staff.user.id));
  assert.equal((await h.get(`${BASE}/${id}`, { token: staff.accessToken })).body.request.id, id);
  //  دکانِ «ب»: نه فهرست، نه یکی، نه تصمیم
  assert.ok(!(await h.get(BASE, { token: other.accessToken })).body.requests.some((r) => r.id === id));
  assert.equal((await h.get(`${BASE}/${id}`, { token: other.accessToken })).status, 404);
  const x = await act(other, id, 'approve');
  assert.equal(x.status, 404);
  assert.equal((await reqRow(id)).status, 'pending');
  assert.equal((await h.get(`${BASE}/apr_nope`, { token: owner.accessToken })).status, 404);
});

test('تأیید: فقط صاحب/مدیر، نه خودش، و فقط با اختیارِ همان مقدار؛ تکرارِ همان تصمیم بی‌خطر', async () => {
  const id = (await ask(staff, { perm: 'sales.discount', doc: 'sales/s-dec', pctBp: 2000, amount: 2000 })).body.request.id;
  assert.equal((await act(staff, id, 'approve')).body.error.code, 'not_approver');
  //  مدیر تا ۱۰٪ ⇒ تأییدِ ۲۰٪ نه
  const weak = await act(manager, id, 'approve');
  assert.equal(weak.status, 403);
  assert.equal(weak.body.error.code, 'approver_not_allowed');
  const ok = await act(owner, id, 'approve', { note: 'اجازه' });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.request.status, 'approved');
  assert.equal(ok.body.request.decidedBy, owner.user.id);
  assert.equal(ok.body.request.note, 'اجازه');
  const again = await act(owner, id, 'approve');
  assert.equal(again.status, 200);
  assert.equal(again.body.duplicate, true);
  assert.equal((await act(manager, id, 'deny')).body.error.code, 'request_closed');
  assert.ok(await one(`SELECT 1 FROM audit_logs WHERE shop_id=$1 AND action='approval_request.approve' AND target_id=$2 AND user_id=$3`,
    [shopId, id, owner.user.id]));
  //  مدیر کارِ خودش را تأیید نمی‌کند
  const mine = (await ask(manager, { perm: 'sales.discount', doc: 'sales/s-self', pctBp: 2000, amount: 2000 })).body.request.id;
  assert.equal((await act(manager, mine, 'approve')).body.error.code, 'approval_self');
});

test('مصرف در Sync v3: op با ارجاع می‌نشیند، سابقه نامِ هر دو را دارد؛ همان op تکرار، opِ دیگر بازپخش', async () => {
  const id = await approved('sales/s-use');
  const op = sale('s-use', 10000, 2000, { approval: ref(id) });
  const r = await push3(staff, 's-use', [op]);
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
  assert.ok(await row3('sales', 's-use'));
  const row = await reqRow(id);
  assert.equal(row.status, 'used');
  assert.equal(row.used_op_id, op.op_id);
  //  سابقه در `shop_approvals` (درِ فهرستِ `GET /api/shop/approvals` برداشته شد)
  const a = await one('SELECT * FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [shopId, `req_${id}`]);
  assert.equal(a.approver_id, owner.user.id);
  assert.equal(a.requester_id, staff.user.id);
  assert.equal(a.key_id, 'remote');
  assert.equal(a.op_id, op.op_id);
  assert.equal(a.doc, 'sales/s-use');
  assert.equal((await h.get(`${BASE}/${id}`, { token: staff.accessToken })).body.request.status, 'used');
  //  پاسخ گم شد ⇒ همان op دوباره «تکراری»
  assert.equal((await push3(staff, 's-use', [op])).body.results[0].status, 'duplicate');
  //  همان تأیید روی opِ دیگر (و سندِ دیگر) ⇒ بازپخش
  const replay = await push3(staff, 's-use', [sale('s-use-2', 10000, 2000, { approval: ref(id) })]);
  assert.equal(replay.body.results[0].reason, 'approval_replayed');
  assert.equal(await row3('sales', 's-use-2'), null);
  //  پس از مصرف، لغو و ردِ آن نه
  assert.equal((await act(staff, id, 'cancel')).body.error.code, 'request_closed');
});

test('بسته به سند، مقدار و درخواست‌کننده: بیشتر، سندِ دیگر یا کاربرِ دیگر ⇒ رد و دست‌نخورده', async () => {
  const id = await approved('sales/s-bind', { pctBp: 1000, amount: 1000 });
  const more = await push3(staff, 's-bind', [sale('s-bind', 10000, 2000, { approval: ref(id) })]);
  assert.equal(more.body.results[0].reason, 'approval_too_small');
  const doc = await push3(staff, 's-bind', [sale('s-bind-x', 10000, 1000, { approval: ref(id) })]);
  assert.equal(doc.body.results[0].reason, 'approval_mismatch');
  //  مدیرِ همین دکان با درخواستِ شاگرد (۲۰٪ بالای سقفِ خودش)
  const id2 = await approved('sales/s-bind2');
  const who = await push3(manager, 'm-bind', [sale('s-bind2', 10000, 2000, { approval: ref(id2) })]);
  assert.equal(who.body.results[0].reason, 'approval_mismatch');
  assert.equal((await reqRow(id)).status, 'approved');
  assert.equal((await reqRow(id2)).status, 'approved');
  //  و درست ⇒ می‌نشیند
  assert.equal((await push3(staff, 's-bind', [sale('s-bind', 10000, 1000, { approval: ref(id) })])).body.results[0].status, 'applied');
});

test('در انتظار، ردشده و پس‌گرفته مصرف نمی‌شوند؛ درخواستِ تازهٔ همان سند پیشین را جایگزین می‌کند', async () => {
  const p = (await ask(staff, { perm: 'sales.discount', doc: 'sales/s-st', pctBp: 2000, amount: 2000 })).body.request.id;
  assert.equal((await push3(staff, 's-st', [sale('s-st', 10000, 2000, { approval: ref(p) })])).body.results[0].reason, 'approval_pending');
  //  تخفیفِ بیشتر برای همان فاکتور ⇒ درخواستِ پیشین «جایگزین‌شده»
  const p2 = (await ask(staff, { perm: 'sales.discount', doc: 'sales/s-st', pctBp: 3000, amount: 3000 })).body.request.id;
  const old = await reqRow(p);
  assert.equal(old.status, 'cancelled');
  assert.equal(old.decision_note, 'superseded');
  assert.equal((await act(owner, p, 'approve')).body.error.code, 'request_closed');
  const d = await act(manager, p2, 'deny', { note: 'نه' });
  assert.equal(d.status, 200);
  assert.equal(d.body.request.status, 'denied');
  assert.equal((await push3(staff, 's-st', [sale('s-st', 10000, 2000, { approval: ref(p2) })])).body.results[0].reason, 'approval_not_approved');
  //  پس گرفتن: فقط خودِ درخواست‌کننده
  const p3 = await approved('sales/s-st3');
  assert.equal((await act(owner, p3, 'cancel')).status, 404);
  const c = await act(staff, p3, 'cancel');
  assert.equal(c.body.request.status, 'cancelled');
  assert.equal((await act(staff, p3, 'cancel')).body.duplicate, true);
  assert.equal((await push3(staff, 's-st3', [sale('s-st3', 10000, 2000, { approval: ref(p3) })])).body.results[0].reason, 'approval_not_approved');
  assert.ok(await one(`SELECT 1 FROM audit_logs WHERE shop_id=$1 AND action='approval_request.cancel' AND target_id=$2`, [shopId, p3]));
});

test('زمان: تأییدِ دیرتر از عمر ۴۰۹ و «منقضی» دیده می‌شود؛ opِ دیرتر از عمرِ تأیید هم نه', async () => {
  const id = (await ask(staff, { perm: 'sales.discount', doc: 'sales/s-exp', pctBp: 2000, amount: 2000, ttlMs: 60_000 })).body.request.id;
  await query('UPDATE shop_approval_requests SET created_at=created_at-120000, expires_at=expires_at-120000 WHERE id=$1', [id]);
  assert.equal((await h.get(`${BASE}/${id}`, { token: staff.accessToken })).body.request.status, 'expired');
  assert.ok(!(await h.get(BASE, { token: owner.accessToken })).body.requests.some((r) => r.id === id), 'منقضی در «باز» نیست');
  assert.ok((await h.get(`${BASE}?status=expired`, { token: owner.accessToken })).body.requests.some((r) => r.id === id));
  const late = await act(owner, id, 'approve');
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'request_expired');
  //  تأییدشده، ولی فاکتور دیرتر از پایانِ عمر (بیش از اختلافِ ساعتِ پذیرفتنی) بسته شد
  const id2 = await approved('sales/s-exp2');
  await query('UPDATE shop_approval_requests SET created_at=created_at-3600000, expires_at=expires_at-3600000 WHERE id=$1', [id2]);
  const r = await push3(staff, 's-exp2', [sale('s-exp2', 10000, 2000, { approval: ref(id2) })]);
  assert.equal(r.body.results[0].reason, 'approval_expired');
  assert.equal((await reqRow(id2)).status, 'approved');
});

test('تأییدکننده‌ای که پس از تأیید اختیارش کم شد ⇒ op رد (approver_not_allowed)', async () => {
  await setRole('manager', { 'sales.discount': { caps: { maxPct: 30 } } });
  const id = await approved('sales/s-demote', { by: manager });
  await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } } });
  const r = await push3(staff, 's-demote', [sale('s-demote', 10000, 2000, { approval: ref(id) })]);
  assert.equal(r.body.results[0].reason, 'approver_not_allowed');
  assert.equal((await reqRow(id)).status, 'approved');
});

test('دو op ِ هم‌زمان روی یک تأیید: فقط یکی می‌نشیند', async () => {
  const id = await approved('sales/s-race');
  const a = sale('s-race', 10000, 2000, { approval: ref(id) });
  const b = sale('s-race', 10000, 2000, { approval: ref(id) });
  const [ra, rb] = await Promise.all([push3(staff, 's-race-a', [a]), push3(staff, 's-race-b', [b])]);
  const statuses = [ra.body.results[0].status, rb.body.results[0].status].sort();
  assert.deepEqual(statuses, ['applied', 'rejected'], JSON.stringify([ra.body, rb.body]));
  assert.equal((await many('SELECT 1 FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [shopId, `req_${id}`])).length, 1);
});

test('Sync v1: مدیرِ بالای سقف با تأییدِ صاحب از راهِ دور می‌نشیند؛ بازپخش نه', async () => {
  const r = await ask(manager, { perm: 'sales.discount', doc: 'sales/s-v1r', pctBp: 3000, amount: 3000 });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal((await act(owner, r.body.request.id, 'approve')).status, 200);
  const v1 = (id, extra = {}) => ({ op_id: oid(), table: 'sales', row_id: id, type: 'insert', ts: Date.now(),
    fields: { total: 100, discount: 30, finalTotal: 70, status: 'completed', ...extra } });
  const bare = await push1(manager, 'm-v1r', [v1('s-v1r')]);
  assert.equal(bare.body.results[0].reason, 'approval_required');
  const op = v1('s-v1r', { approval: ref(r.body.request.id) });
  const ok = await push1(manager, 'm-v1r', [op]);
  assert.equal(ok.body.results[0].status, 'applied', JSON.stringify(ok.body));
  const a = await one('SELECT * FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [shopId, `req_${r.body.request.id}`]);
  assert.equal(a.path, 'v1');
  assert.equal(a.approver_id, owner.user.id);
  const replay = await push1(manager, 'm-v1r2', [{ ...op, op_id: oid() }]);
  assert.equal(replay.body.results[0].reason, 'approval_replayed');
});

test('سرورِ بی Sync v3: کارِ پیش‌فرض‌ممنوعِ شاگرد درخواست نمی‌سازد (approval_needs_v3)؛ مدیرِ بالای سقف همان راهِ v1', async () => {
  //  v1Guard تأییدِ چنین کاری را همیشه `approval_needs_v3` می‌کند (v1 ِ مشترک با پمپ نقش را می‌سنجد) ⇒ بی v3 مصرف‌شدنی نیست
  const config = require('../src/config');
  config.syncV3.enabled = false;
  try {
    //  برنامه همین را از `/config` می‌خواند و دکمه را پنهان می‌کند (`RemoteApproval.offered`)
    assert.equal((await h.get(`${BASE}/config`, { token: staff.accessToken })).body.v3, false);
    const r = await ask(staff, { perm: 'sales.discount', doc: 'sales/s-nov3', pctBp: 2000, amount: 2000 });
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'approval_needs_v3');
    assert.equal(await one('SELECT 1 AS x FROM shop_approval_requests WHERE doc=$1', ['sales/s-nov3']), null);
    //  همان دلیل در خودِ v1: برگهٔ تأییدشده هم برای شاگرد نمی‌نشیند
    const v1 = { op_id: oid(), table: 'sales', row_id: 's-nov3', type: 'insert', ts: Date.now(),
      fields: { total: 100, discount: 20, finalTotal: 80, status: 'completed', approval: ref('apr_000000000000000000000000') } };
    assert.equal((await push1(staff, 's-nov3', [v1])).body.results[0].reason, 'approval_needs_v3');
    //  مدیر (پیش‌فرضش «مجاز»، سقفِ ۱۰٪) بالای سقف همان امروز می‌پرسد
    assert.equal((await ask(manager, { perm: 'sales.discount', doc: 'sales/s-nov3-m', pctBp: 2000, amount: 2000 })).status, 201);
  } finally {
    config.syncV3.enabled = true;
  }
  //  سرورِ با v3: همان درخواست ساخته می‌شود (دستگاهِ v3 مصرفش می‌کند)
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'sales/s-nov3', pctBp: 2000, amount: 2000 })).status, 201);
});

test('سقفِ درخواستِ باز: بیش از ده تا ⇒ ۴۲۹', async () => {
  await query(`UPDATE shop_approval_requests SET status='cancelled' WHERE requester_id=$1 AND status='pending'`, [staff.user.id]);
  for (let i = 0; i < 10; i++) {
    assert.equal((await ask(staff, { perm: 'sales.discount', doc: `sales/s-cap-${i}`, amount: 100 })).status, 201);
  }
  const r = await ask(staff, { perm: 'sales.discount', doc: 'sales/s-cap-x', amount: 100 });
  assert.equal(r.status, 429);
  assert.equal(r.body.error.code, 'too_many_pending');
  //  یکی پس گرفته شد ⇒ جا باز است
  const first = (await h.get(BASE, { token: staff.accessToken })).body.requests.find((x) => x.doc === 'sales/s-cap-0');
  assert.equal((await act(staff, first.id, 'cancel')).status, 200);
  assert.equal((await ask(staff, { perm: 'sales.discount', doc: 'sales/s-cap-x', amount: 100 })).status, 201);
  await query(`UPDATE shop_approval_requests SET status='cancelled' WHERE requester_id=$1 AND status='pending'`, [staff.user.id]);
});

test('پرچمِ خاموش پس از تأیید: ارجاع دیگر پذیرفته نمی‌شود', async () => {
  const id = await approved('sales/s-flagoff');
  await flag(false);
  const r = await push3(staff, 's-flagoff', [sale('s-flagoff', 10000, 2000, { approval: ref(id) })]);
  assert.equal(r.body.results[0].reason, 'approval_invalid');
  assert.equal((await reqRow(id)).status, 'approved');
});

test('پوش به مدیر: درخواستِ تازه به صاحب و مدیرِ فعال می‌رسد (نه خودش، نه دکانِ دیگر)؛ تکرارِ همان کلید پوشِ دوم ندارد', async () => {
  const push = require('../src/lib/push');
  const got = [];
  await flag(true);
  push.setDeliver((row, message) => { got.push({ userId: row.user_id, shopId: row.shop_id, message }); });
  try {
    for (const [who, tag] of [[owner, 'o'], [manager, 'm'], [staff, 's'], [other, 'x']]) {
      await push.register({ app: 'shop', token: `push-ar-${tag}`, userId: who.user.id, shopId: who === other ? '' : shopId, deviceUid: `d-ar-${tag}` });
    }
    const body = { key: key(), reason: 'مشتریِ همیشگی', perm: 'sales.discount', doc: 'sales/s-push', pctBp: 2000, amount: 250000 };
    resetAll();
    const r = await h.post(BASE, body, { token: staff.accessToken });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    for (let i = 0; i < 50 && got.length < 2; i++) await new Promise((res) => setTimeout(res, 20));
    const to = got.map((g) => g.userId).sort();
    assert.deepEqual(to, [manager.user.id, owner.user.id].sort(), JSON.stringify(got));
    const m = got[0].message;
    assert.equal(m.data.type, 'approval_request');
    assert.equal(m.data.id, r.body.request.id);
    assert.match(m.body, /تخفیف/);
    assert.match(m.body, /۲٬۵۰۰/);
    assert.ok(!/cardNumber|شمارهٔ کارت/.test(JSON.stringify(m)));
    //  همان کلید و همان محتوا ⇒ همان درخواست، بی پوشِ دوم
    resetAll();
    const again = await h.post(BASE, body, { token: staff.accessToken });
    assert.equal(again.status, 200);
    await new Promise((res) => setTimeout(res, 60));
    assert.equal(got.length, 2);
  } finally {
    push.setDeliver(null);
  }
});

test('نام و نقشِ تأییدکننده در get/list برای صندوق (برگشت نقش را می‌سنجد)', async () => {
  await flag(true);
  const id = (await ask(staff, { perm: 'sales.discount', doc: 'sales/s-who', pctBp: 2000, amount: 2000 })).body.request.id;
  const before = await h.get(`${BASE}/${id}`, { token: staff.accessToken });
  assert.equal(before.body.request.deciderRole, undefined);
  assert.equal((await act(owner, id, 'approve')).status, 200);
  const after = await h.get(`${BASE}/${id}`, { token: staff.accessToken });
  assert.equal(after.body.request.status, 'approved');
  assert.equal(after.body.request.decidedBy, owner.user.id);
  assert.equal(after.body.request.deciderRole, 'owner');
  assert.ok(after.body.request.deciderName);
  const listed = (await h.get(`${BASE}?status=approved`, { token: owner.accessToken })).body.requests.find((r) => r.id === id);
  assert.equal(listed.deciderRole, 'owner');
});
