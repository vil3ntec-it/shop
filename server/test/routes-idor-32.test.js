'use strict';
/**
 * ⛔ بازبینیِ خصمانهٔ درهای تازهٔ دستهٔ ۳۲ — حمله‌هایی که آزمون‌های فهرستی (`route-authz-inventory`، `tenant-matrix`،
 * `shop-route-inventory`) نمی‌بینند (دنبالهٔ همان روشِ دستهٔ ۳۱):
 *   ۱) IDOR ِ میانِ دکان‌ها و میانِ دستگاه‌ها با شناسهٔ نشت‌کرده، همسایه و شکلِ دیگرِ حروف؛
 *   ۲) سطحِ دسترسی: شاگرد، صندوق‌دار، انباردار، حسابدار، مدیرِ «بسته‌شده» با لایهٔ نقش/کاربر (STF-01)، عضوِ معلق،
 *      نشستِ پس از «خروج از همه»، دستگاهِ «باطل‌شده» (#1009)؛
 *   ۳) نوعِ حسابِ نادرست: پمپ، پیک، بلیتِ ویترین روی درِ دکان، توکنِ دکان روی درِ مشتری؛
 *   ۴) ورودیِ بدخواه: آرایه/شیء، `__proto__`، نویسهٔ یونیکدِ فریبنده، CR/LF، بدنهٔ بزرگ؛
 *   ۵) نشت در پاسخ و لاگ.
 * یافته‌های واقعی (هر سه با رفع و همین آزمون — بی رفع سرخ):
 *   ف۱ `POST /api/sync/v1/audit-anchor` ⇐ `device_id` ِ بدنه با پرچمِ خاموشِ `sync.device_bind` سنجیده نمی‌شد: هر عضو
 *      سرِ زنجیرهٔ دستگاهِ دیگری را می‌نشاند ⇒ «انشعاب»/«عقب‌گرد» ِ دروغ روی صفحهٔ صاحب و پیش‌دستی بر سرِ آن دستگاه؛
 *      نشستِ بی دستگاه هم هر شناسه‌ای را می‌نشاند — `lib/device-audit-anchor.ownDevice`؛
 *   ف۲ `GET /api/sync/v1/audit-anchor` ⇐ فقط `can(role)`: مدیر/حسابداری که صاحب «سابقهٔ عملیات» را با لایهٔ نقش یا
 *      کاربر برایش بسته بود (و `/api/shop/audit` ۴۰۳ می‌داد) حکم و شناسهٔ همهٔ دستگاه‌ها را می‌دید — `routes/audit-anchor.auditView`؛
 *   ف۳ `POST /api/auth/staff/device-key` فقط سطلِ IP داشت — سطلِ حساب در `routes-idor-32-limits.test.js`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const util = require('node:util');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const staffCodes = require('../src/lib/staff-codes');
const staffPerms = require('../src/lib/staff-perms');
const docRoles = require('../src/lib/doc-roles');
const offline = require('../src/lib/shop-offline-codes');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

// ── لاگِ سرور (همان لاگ، به‌علاوهٔ نسخه‌ای برای سنجهٔ نشت) ─────────────────────────────────────────────
const METHODS = ['log', 'info', 'warn', 'error', 'debug', 'trace'];
const lines = [];
const real = {};
for (const m of METHODS) {
  real[m] = console[m];
  console[m] = (...a) => { lines.push(util.format(...a)); real[m](...a); };
}
const secrets = new Map();
const remember = (label, value) => { const s = String(value ?? ''); if (s.length >= 8) secrets.set(s, label); };

const FORMAT = 'tohid-audit-chain-v1';
let admin = '';
let A;
let B;
let pump;
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const H = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');
const errOf = (r) => r.body?.error?.code;
const call = (who, method, p, body, headers = {}) => h.api(method, p, { token: who ? who.token : null, headers, ...(body === undefined ? {} : { body }) });
const anchorPost = (who, dev, heads, { extra = {}, headers = {} } = {}) => h.api('POST', '/api/sync/v1/audit-anchor', {
  token: who.token, headers: { 'X-App': 'shop', ...headers }, body: { device_id: dev, format: FORMAT, heads, ...extra },
});
const verdictOf = (who) => h.api('GET', '/api/sync/v1/audit-anchor', { token: who.token, headers: { 'X-App': 'shop' } });
const anchorsOf = async (shopId) => (await query('SELECT chain, seq, hash, device_id, user_id, problem FROM device_audit_anchors WHERE shop_id=$1 ORDER BY chain, seq', [shopId])).rows;
const chainEvents = async (shopId) => Number((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='security.audit_chain'`, [shopId])).n);
function noLeak(r, needles, what) {
  const text = JSON.stringify(r.body ?? '');
  for (const n of needles.filter(Boolean)) assert.ok(!text.includes(n), `${what}: «${n}» در پاسخ آمد: ${text.slice(0, 300)}`);
}
const neighbour = (id) => id.slice(0, -1) + (id.slice(-1) === '0' ? '1' : '0');

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
/** دستگاهِ ثبت‌شدهٔ نشستِ این کاربر (همان `device_uid` ِ `requireSyncAccount` ⇐ `req.sync.deviceUid`) */
const devOf = async (userId) => (await one('SELECT device_uid FROM devices WHERE user_id=$1 ORDER BY created_at LIMIT 1', [userId])).device_uid;

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,$4,'active',$5,$5)`, [id, shopId, u.user.id, role, now()]);
  remember(`token:${name}`, u.accessToken);
  remember(`refresh:${name}`, u.refreshToken);
  return { ...u, token: u.accessToken, userId: u.user.id, memberId: id, name, dev: await devOf(u.user.id) };
}

async function shopWith(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  remember(`token:owner ${label}`, o.accessToken);
  const name = `دکانِ ${label}-${uniq()}`;
  const made = await h.post('/api/shop', { name }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.shop.id;
  await query('UPDATE shops SET max_members=50 WHERE id=$1', [id]);
  const S = { id, name, label, owner: { ...o, token: o.accessToken, userId: o.user.id, name: `صاحبِ ${label}`, dev: await devOf(o.user.id) } };
  for (const role of ['manager', 'staff', 'storekeeper', 'cashier', 'accountant', 'courier']) S[role] = await member(id, role, `${role}-${label}`);
  await setFlag(id, 'shop.doc_chain', true);
  return S;
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'idor32-admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'idor32-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  remember('token:admin', admin);
  A = await shopWith('الف');
  B = await shopWith('ب');
  pump = await h.newUser('کاربرِ پمپ', 'pump');
  pump.token = pump.accessToken;
  remember('token:pump', pump.accessToken);
});
test.after(async () => {
  for (const m of METHODS) console[m] = real[m];
  await h.stop();
});
test.beforeEach(() => resetAll());

// ══════════════════════════════════════════════════════════════════ لنگرِ سابقه (STF-09)

test('ف۱ جعلِ دستگاه (پرچمِ `sync.device_bind` خاموش): هیچ عضوی سرِ زنجیرهٔ دستگاهِ دیگری را نمی‌نشاند — نه انشعاب و عقب‌گردِ دروغ، نه پیش‌دستی', async () => {
  const victim = A.owner.dev;
  remember('dev:owner-الف', victim);
  //  همان چیزی که بعداً خودِ دستگاه گزارش می‌کند
  const genuine = { chain: `${victim}/1`, seq: 5, hash: H('victim-5') };
  const attacks = [
    ['انشعاب: همان شماره، هشِ دیگر', [{ ...genuine, hash: H('forged') }]],
    ['پیش‌دستی: شمارهٔ بزرگ ⇒ عقب‌گردِ دروغ', [{ ...genuine, seq: 999, hash: H('ahead') }]],
    ['جهشِ دوره ⇒ عقب‌گردِ همهٔ دوره‌های بعدی', [{ chain: `${victim}/16`, seq: 1, hash: H('epoch') }]],
  ];
  let first = null;
  for (const who of [A.staff, A.cashier, A.storekeeper, A.accountant, A.manager]) {
    for (const [what, heads] of attacks) {
      const r = await anchorPost(who, victim, heads);
      assert.deepEqual([r.status, errOf(r)], [403, 'device_mismatch'], `${who.name} ${what}: ${r.status} ${JSON.stringify(r.body)}`);
      noLeak(r, [victim, A.name, A.owner.userId], `${who.name} ${what}`);
      //  همان پاسخ برای هر شناسهٔ دیگری ⇒ پاسخ نمی‌گوید این شناسه دستگاهِ واقعیِ دکان است یا نه
      if (!first) first = r.body;
      assert.deepEqual(r.body, first);
    }
  }
  //  سرآیندِ `X-Device` ِ همان قربانی، شکلِ دیگرِ حروف و شناسهٔ همسایه هم دستگاهِ دیگرند
  for (const [dev, headers] of [[victim, { 'X-Device': victim }], [victim.toUpperCase(), {}], [neighbour(victim), {}], [`${victim}-x`, {}]]) {
    const r = await anchorPost(A.staff, dev, [{ chain: `${dev}/1`, seq: 1, hash: H(dev) }], { headers });
    assert.deepEqual([r.status, r.body], [403, first], `${dev}: ${JSON.stringify(r.body)}`);
  }
  //  دکانِ دیگر با شناسهٔ دستگاهِ «الف» هم همان
  const cross = await anchorPost(B.owner, victim, [genuine]);
  assert.deepEqual([cross.status, cross.body], [403, first]);
  assert.deepEqual(await anchorsOf(A.id), [], 'هیچ سری به نامِ دستگاهِ صاحب');
  assert.deepEqual(await anchorsOf(B.id), []);
  //  تلاش در سابقه می‌ماند (`sync.device_mismatch` ِ `requireSyncAccount`، با پرچمِ خاموش «فقط ثبت»)
  const seen = await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='sync.device_mismatch' AND target_id=$1 AND user_id=$2`, [victim, A.staff.userId]);
  assert.ok(Number(seen.n) >= 1, 'تلاشِ جعل در سابقه');

  //  خودِ دستگاه: گزارشش پاک می‌نشیند — بی انشعاب، بی عقب‌گرد، بی رویداد
  const own = await anchorPost(A.owner, victim, [genuine]);
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.deepEqual([own.body.acks, own.body.problems, own.body.last_epoch], [[genuine], [], 1]);
  assert.equal(await chainEvents(A.id), 0, 'هیچ هشدارِ دروغی روی صفحهٔ صاحب');
  const rows = await anchorsOf(A.id);
  assert.deepEqual(rows.map((x) => [x.chain, Number(x.seq), x.device_id, x.user_id, x.problem]), [[genuine.chain, 5, victim, A.owner.userId, '']]);
  const v = await verdictOf(A.owner);
  assert.equal(v.status, 200, JSON.stringify(v.body));
  assert.equal(v.body.chains.some((c) => c.broken), false);
});

test('ف۱ پرچمِ `sync.device_bind` روشن هم همان ۴۰۳ (از `requireSyncAccount`) — رفتارِ دو حالت یکی است', async () => {
  await setFlag(A.id, 'sync.device_bind', true);
  try {
    const r = await anchorPost(A.staff, A.owner.dev, [{ chain: `${A.owner.dev}/1`, seq: 1, hash: H('bind-on') }]);
    assert.deepEqual([r.status, errOf(r)], [403, 'device_mismatch'], JSON.stringify(r.body));
    const mine = await anchorPost(A.staff, A.staff.dev, [{ chain: `${A.staff.dev}/1`, seq: 1, hash: H('bind-on-own') }]);
    assert.equal(mine.status, 200, JSON.stringify(mine.body));
  } finally {
    await setFlag(A.id, 'sync.device_bind', false);
  }
  await query('DELETE FROM device_audit_anchors WHERE shop_id=$1 AND device_id<>$2', [A.id, A.owner.dev]);
});

test('ف۱ نشستِ بی دستگاه (ورود بی `device`): ۴۰۳ `device_unbound` برای هر شناسه — نه سرِ دستگاهِ خودش، نه دیگری', async () => {
  const bare = await h.post('/api/auth/login', { identifier: A.cashier.email, password: A.cashier.password });
  assert.equal(bare.status, 200, JSON.stringify(bare.body));
  remember('token:bare', bare.body.accessToken);
  const who = { token: bare.body.accessToken, name: 'صندوق‌دارِ بی دستگاه' };
  for (const dev of [A.cashier.dev, A.owner.dev, `fresh-${uniq()}`]) {
    const r = await anchorPost(who, dev, [{ chain: `${dev}/1`, seq: 3, hash: H(`bare-${dev}`) }]);
    assert.deepEqual([r.status, errOf(r)], [403, 'device_unbound'], `${dev}: ${JSON.stringify(r.body)}`);
    noLeak(r, [dev], 'بی دستگاه');
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM device_audit_anchors WHERE shop_id=$1 AND user_id=$2', [A.id, A.cashier.userId])).n, 0);
  //  همان آدم با نشستِ دستگاه‌دار سرِ دستگاهِ خودش را می‌فرستد
  const ok = await anchorPost(A.cashier, A.cashier.dev, [{ chain: `${A.cashier.dev}/1`, seq: 3, hash: H('cashier-own') }]);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
});

test('لنگر: هر نقشِ دفتر سرِ دستگاهِ خودش را می‌فرستد (پاسبانی، بی `data.write`)؛ ردیف به نامِ همان کاربر و دستگاه', async () => {
  for (const who of [A.staff, A.storekeeper, A.accountant, A.manager]) {
    const head = { chain: `${who.dev}/1`, seq: 2, hash: H(`own-${who.name}`) };
    const r = await anchorPost(who, who.dev, [head]);
    assert.equal(r.status, 200, `${who.name}: ${JSON.stringify(r.body)}`);
    assert.deepEqual(r.body.problems, []);
    const row = await one('SELECT device_id, user_id FROM device_audit_anchors WHERE shop_id=$1 AND chain=$2', [A.id, head.chain]);
    assert.deepEqual([row.device_id, row.user_id], [who.dev, who.userId]);
  }
});

test('ف۲ سطحِ حکم: لایهٔ نقش/کاربرِ بستهٔ `audit.view` ⇐ ۴۰۳ همان‌طور که `/api/shop/audit`؛ شاگرد، صندوق‌دار و انباردار ۴۰۳؛ صاحب و مدیرِ باز ۲۰۰', async () => {
  for (const who of [A.owner, A.manager, A.accountant]) assert.equal((await verdictOf(who)).status, 200, who.name);
  for (const who of [A.staff, A.cashier, A.storekeeper]) {
    const r = await verdictOf(who);
    assert.deepEqual([r.status, errOf(r)], [403, 'permission_denied'], `${who.name}: ${JSON.stringify(r.body)}`);
  }
  //  صاحب «سابقهٔ عملیات» را برای نقشِ مدیر می‌بندد
  await staffPerms.setRoleLayer(A.id, 'manager', { 'audit.view': { mode: 'deny' } }, A.owner.userId);
  try {
    assert.equal((await h.get('/api/shop/audit', { token: A.manager.token })).status, 403, 'مرجع: درِ سابقهٔ دکان بسته است');
    const r = await verdictOf(A.manager);
    assert.deepEqual([r.status, errOf(r)], [403, 'permission_denied'], `مدیرِ بسته‌شده: ${JSON.stringify(r.body)}`);
    noLeak(r, [A.owner.dev, A.manager.dev], 'مدیرِ بسته‌شده');
  } finally {
    await staffPerms.setRoleLayer(A.id, 'manager', {}, A.owner.userId);
  }
  assert.equal((await verdictOf(A.manager)).status, 200, 'لایه برداشته شد');
  //  لایهٔ همین کاربر (حسابدار)
  await staffPerms.setMemberLayer(A.id, A.accountant.userId, { 'audit.view': { mode: 'deny' } }, A.owner.userId);
  try {
    assert.equal((await h.get('/api/shop/audit', { token: A.accountant.token })).status, 403);
    const r = await verdictOf(A.accountant);
    assert.deepEqual([r.status, errOf(r)], [403, 'permission_denied'], `حسابدارِ بسته‌شده: ${JSON.stringify(r.body)}`);
    //  فرستادنِ سرِ دستگاهِ خودش (پاسبانی) بسته نمی‌شود
    const own = await anchorPost(A.accountant, A.accountant.dev, [{ chain: `${A.accountant.dev}/1`, seq: 9, hash: H('acc-closed') }]);
    assert.equal(own.status, 200, JSON.stringify(own.body));
  } finally {
    await staffPerms.setMemberLayer(A.id, A.accountant.userId, {}, A.owner.userId);
  }
  //  صاحب هرگز بسته نمی‌شود
  assert.equal((await verdictOf(A.owner)).status, 200);
});

test('لنگر، نوعِ حساب: بی توکن، دست‌خورده، ادمین، بلیتِ ویترین ۴۰۱؛ پمپ ۴۰۳؛ پیک ۴۰۳ courier_scope — بی ردیف', async () => {
  const before = (await anchorsOf(A.id)).length;
  const heads = [{ chain: `${A.owner.dev}/1`, seq: 7, hash: H('kind') }];
  for (const [who, want, what] of [
    [{ token: null }, 401, 'بی توکن'], [{ token: h.tampered(A.owner.token) }, 401, 'دست‌خورده'], [{ token: admin }, 401, 'ادمین'],
    [{ token: `stk_${'a'.repeat(40)}` }, 401, 'بلیت‌مانند'], [pump, 403, 'پمپ'], [A.courier, 403, 'پیک'],
  ]) {
    for (const method of ['POST', 'GET']) {
      const r = method === 'POST' ? await anchorPost(who, A.owner.dev, heads) : await verdictOf(who);
      assert.equal(r.status, want, `${what} ${method}: ${JSON.stringify(r.body)}`);
      noLeak(r, [A.name, A.owner.dev], `${what} ${method}`);
    }
  }
  assert.equal(errOf(await anchorPost(A.courier, A.courier.dev, heads)), 'courier_scope');
  assert.equal((await anchorsOf(A.id)).length, before);
});

test('لنگر، نشستِ بسته: عضوِ معلق ۴۰۳، پس از «خروج از همه» ۴۰۱، دستگاهِ «باطل‌شده» (#1009) ۴۰۱/۴۰۳ — بی ردیف', async () => {
  const S = await shopWith('معلق');
  //  معلق: نشست هنوز باز، عضویت نه
  await query(`UPDATE shop_members SET status='suspended' WHERE id=$1`, [S.cashier.memberId]);
  const s1 = await anchorPost(S.cashier, S.cashier.dev, [{ chain: `${S.cashier.dev}/1`, seq: 1, hash: H('susp') }]);
  assert.equal(s1.status, 403, JSON.stringify(s1.body));
  //  «خروج از همه» ِ خودِ کاربر
  const out = await h.post('/api/auth/logout-all', {}, { token: S.storekeeper.token });
  assert.ok(out.status < 300, JSON.stringify(out.body));
  const s2 = await anchorPost(S.storekeeper, S.storekeeper.dev, [{ chain: `${S.storekeeper.dev}/1`, seq: 1, hash: H('lo') }]);
  assert.equal(s2.status, 401, JSON.stringify(s2.body));
  //  شاگردِ کدی با رازِ دستگاه؛ صاحب «باطل کردنِ این دستگاه» را می‌زند
  const made = await staffCodes.create(S.id, S.owner.userId, { role: 'staff', maxUses: 1 });
  const uid = `u-idor32-${uniq()}`;
  const joined = await h.post('/api/auth/staff', { code: made.code, name: 'شاگردِ باطل', device: { uid, name: 'گوشی', platform: 'android', keyVersion: 1 } });
  assert.equal(joined.status, 201, JSON.stringify(joined.body));
  remember('token:revoked-staff', joined.body.accessToken);
  remember('key:revoked-staff', joined.body.deviceKey);
  const st = { token: joined.body.accessToken };
  assert.equal((await anchorPost(st, uid, [{ chain: `${uid}/1`, seq: 1, hash: H('rv-1') }])).status, 200, 'پیش از باطل');
  const ov = await h.get('/api/shop/staff-keys', { token: S.owner.token });
  const dev = ov.body.devices.find((d) => d.userId === joined.body.user.id);
  assert.ok(dev, JSON.stringify(ov.body));
  assert.equal((await h.post(`/api/shop/staff-keys/${dev.id}/revoke`, {}, { token: S.owner.token })).status, 200);
  const s3 = await anchorPost(st, uid, [{ chain: `${uid}/1`, seq: 2, hash: H('rv-2') }]);
  assert.ok([401, 403].includes(s3.status), JSON.stringify(s3.body));
  assert.equal((await verdictOf(st)).status === 200, false);
  const rows = await anchorsOf(S.id);
  assert.deepEqual(rows.map((r) => Number(r.seq)), [1], 'فقط سرِ پیش از باطل');
});

test('لنگر، ورودیِ بدخواه: آرایه/شیء/عدد، `__proto__`، یونیکدِ فریبنده، CR/LF، بلند، بدنهٔ آرایه، ۲.۵ مگابایت ⇒ ۴۰۰/۴۰۳/۴۱۳، هرگز ۵۰۰ و بی ردیف', async () => {
  const S = await shopWith('بدخواه');
  const me = S.owner;
  const dev = me.dev;
  const good = { chain: `${dev}/1`, seq: 1, hash: H('good') };
  const post = (body) => h.api('POST', '/api/sync/v1/audit-anchor', { token: me.token, headers: { 'X-App': 'shop' }, body });
  const cases = [
    [{ device_id: [dev], format: FORMAT, heads: [good] }, 400],
    [{ device_id: { toString: dev }, format: FORMAT, heads: [good] }, 400],
    [{ device_id: 12345678, format: FORMAT, heads: [good] }, 400],
    [{ device_id: `${dev.slice(0, -1)}а`, format: FORMAT, heads: [good] }, 400],   //  «а» ِ سیریلیک
    [{ device_id: `${dev}\r\n[sync] جعلی`, format: FORMAT, heads: [good] }, 400],
    [{ device_id: 'x'.repeat(65), format: FORMAT, heads: [good] }, 400],
    [{ device_id: dev, format: [FORMAT], heads: [good] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, seq: '1' }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, seq: 2 ** 53 }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, seq: -1 }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, hash: H('u').toUpperCase() }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, chain: [good.chain] }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, chain: `${dev}/1\n` }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, chain: `${dev.toUpperCase()}/1` }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: [{ ...good, chain: `${dev}/../1` }] }, 400],
    [{ device_id: dev, format: FORMAT, heads: Array.from({ length: 5 }, (_, i) => ({ ...good, chain: `${dev}/${i + 1}` })) }, 400],
    [[{ device_id: dev, format: FORMAT, heads: [good] }], 400],
  ];
  for (const [body, want] of cases) {
    const r = await post(body);
    assert.equal(r.status, want, `${JSON.stringify(body).slice(0, 120)} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }
  //  `__proto__` در بدنه، در سر و در `heads` ِ شیء‌گونه: نه آلودگیِ prototype، نه ردیف
  const proto = await h.api('POST', '/api/sync/v1/audit-anchor', {
    token: me.token, headers: { 'X-App': 'shop', 'Content-Type': 'application/json' },
    body: JSON.parse(`{"__proto__":{"polluted":1,"deviceUid":"${A.owner.dev}"},"device_id":"${dev}","format":"${FORMAT}",` +
      `"heads":{"0":${JSON.stringify(good)},"length":1,"__proto__":{"length":9}}}`),
  });
  assert.equal(proto.status, 200, JSON.stringify(proto.body));
  assert.deepEqual([proto.body.acks, proto.body.problems], [[], []], 'heads ِ نا-آرایه یعنی هیچ سری');
  const head = await h.api('POST', '/api/sync/v1/audit-anchor', {
    token: me.token, headers: { 'X-App': 'shop' },
    body: JSON.parse(`{"device_id":"${dev}","format":"${FORMAT}","heads":[{"__proto__":{"seq":1},"chain":"${good.chain}","hash":"${good.hash}"}]}`),
  });
  assert.equal(head.status, 400, JSON.stringify(head.body));
  assert.equal({}.polluted, undefined);
  assert.equal({}.deviceUid, undefined);
  //  `__proto__.deviceUid` ِ بدنه دستگاهِ نشست را عوض نمی‌کند
  const spoof = await h.api('POST', '/api/sync/v1/audit-anchor', {
    token: me.token, headers: { 'X-App': 'shop' },
    body: JSON.parse(`{"__proto__":{"deviceUid":"${A.owner.dev}"},"device_id":"${A.owner.dev}","format":"${FORMAT}","heads":[]}`),
  });
  assert.deepEqual([spoof.status, errOf(spoof)], [403, 'device_mismatch']);
  //  بدنهٔ بزرگ ⇒ ۴۱۳ پیش از هر خواندن
  const big = await post({ device_id: dev, format: FORMAT, heads: [good], pad: 'x'.repeat(2.5 * 1024 * 1024) });
  assert.equal(big.status, 413, JSON.stringify(big.body).slice(0, 200));
  assert.deepEqual(await anchorsOf(S.id), []);
  assert.equal(await chainEvents(S.id), 0);
});

test('لنگر، IDOR ِ حکم: «ب» در حکمِ خودش هیچ زنجیره، هش یا شناسهٔ دستگاهی از «الف» نمی‌بیند — حتی با سرآیند/پرسشِ دکانِ «الف»', async () => {
  const head = { chain: `${A.owner.dev}/2`, seq: 4, hash: H('a-secret-head') };
  assert.equal((await anchorPost(A.owner, A.owner.dev, [head])).status, 200);
  const r = await h.api('GET', `/api/sync/v1/audit-anchor?shopId=${A.id}&shop_id=${A.id}`, { token: B.owner.token, headers: { 'X-App': 'shop', 'X-Shop-Id': A.id } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  noLeak(r, [A.id, A.owner.dev, head.hash, A.name, A.staff.dev], 'حکمِ «ب»');
  const mine = await verdictOf(A.owner);
  assert.ok(mine.body.chains.some((c) => c.anchor && c.anchor.hash === head.hash));
});

// ══════════════════════════════════════════════════════════════════ پیامِ صوتیِ ویترین (CHAT-07)

const ogg = (tag) => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(300, tag)]);

async function chatStore(S) {
  for (const f of ['shop.orders', 'shop.storefront', 'shop.chat']) await setFlag(S.id, f, true);
  const slug = `v32-${S.label === 'الف' ? 'a' : 'b'}-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${S.label}`, contactPhone: '0700000001' }, { token: S.owner.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return slug;
}
async function ticketOf(slug, phone) {
  const start = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(start.status, 200, JSON.stringify(start.body));
  const check = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  remember(`ticket:${phone}`, check.body.ticket);
  return check.body.ticket;
}
const pub = (slug, ticket, p, { method = 'GET', body, headers = {}, token = null } = {}) =>
  h.api(method, `/api/store/${slug}/chat${p}`, { token, body: body ?? null, headers: ticket ? { 'X-Store-Ticket': ticket, ...headers } : headers });

test('صدای ویترین: بایت فقط برای مشتریِ همان گفت‌وگو با بلیت؛ مشتریِ دیگر، ویترینِ دیگر و شناسهٔ همسایه همان ۴۰۴ ِ ساختگی؛ پاک‌شده و نافرستاده ۴۰۴', async () => {
  const slugA = await chatStore(A);
  const slugB = await chatStore(B);
  const tx = await ticketOf(slugA, '0790003201');
  const ty = await ticketOf(slugA, '0790003202');
  const opened = await pub(slugA, tx, '/conversations', { method: 'POST', body: { name: 'نادرِ صدا' } });
  assert.equal(opened.status, 201, JSON.stringify(opened.body));
  const conv = opened.body.conversation.id;
  //  دکان صدا می‌فرستد
  const up = await h.raw('POST', `/api/shop/chat/conversations/${conv}/media`, ogg(7), { token: A.owner.token, headers: { 'Content-Type': 'audio/ogg' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const mid = up.body.mediaId;
  const sent = await h.post(`/api/shop/chat/conversations/${conv}/messages`, { clientId: `c-${uniq()}-0000`, kind: 'audio', mediaId: mid, meta: { durationMs: 3000 } }, { token: A.owner.token });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  //  صدای نافرستاده (فقط بارگذاری‌شده) هم
  const draft = (await h.raw('POST', `/api/shop/chat/conversations/${conv}/media`, ogg(9), { token: A.owner.token, headers: { 'Content-Type': 'audio/ogg' } })).body.mediaId;

  //  مشتریِ همان گفت‌وگو: همان بایت، نه صفحه
  const got = await pub(slugA, tx, `/conversations/${conv}/media/${mid}`);
  assert.equal(got.status, 200, JSON.stringify(got.body));
  assert.equal(got.headers.get('content-type'), 'audio/ogg');
  assert.equal(got.headers.get('x-content-type-options'), 'nosniff');
  assert.match(got.headers.get('content-security-policy') || '', /sandbox/);
  assert.match(got.headers.get('cache-control') || '', /no-store/);

  const miss = await pub(slugA, tx, `/conversations/${conv}/media/${newId('chm')}`);
  assert.equal(miss.status, 404, JSON.stringify(miss.body));
  const missConv = await pub(slugA, ty, `/conversations/${newId('chc')}/media/${mid}`);
  assert.equal(missConv.status, 404, JSON.stringify(missConv.body));
  const same = (r, ref, what) => { assert.deepEqual([r.status, r.body], [ref.status, ref.body], `${what}: ${JSON.stringify(r.body)}`); };
  //  مشتریِ دیگرِ همان ویترین با شناسه‌های نشت‌کرده
  same(await pub(slugA, ty, `/conversations/${conv}/media/${mid}`), missConv, 'مشتریِ دیگر');
  //  شناسهٔ همسایه و حروفِ بزرگ
  same(await pub(slugA, tx, `/conversations/${conv}/media/${neighbour(mid)}`), miss, 'رسانهٔ همسایه');
  same(await pub(slugA, tx, `/conversations/${conv}/media/${mid.toUpperCase()}`), miss, 'حروفِ بزرگ');
  //  صدای نافرستادهٔ دکان
  same(await pub(slugA, tx, `/conversations/${conv}/media/${draft}`), miss, 'نافرستاده');
  //  همان بلیت روی ویترینِ «ب» (چتِ روشن): گفت‌وگوی «الف» نیست
  const viaB = await pub(slugB, tx, `/conversations/${conv}/media/${mid}`);
  assert.equal(viaB.status, 404, JSON.stringify(viaB.body));
  noLeak(viaB, [A.name, conv], 'ویترینِ «ب»');

  //  نوعِ حساب: بی بلیت، بلیتِ دست‌خورده، بلیت در querystring (برای <audio src>)، توکنِ دکان، پمپ و پیک ⇒ ۴۰۱ و بی بایت
  for (const [what, r] of [
    ['بی بلیت', await pub(slugA, '', `/conversations/${conv}/media/${mid}`)],
    ['دست‌خورده', await pub(slugA, h.tampered(tx), `/conversations/${conv}/media/${mid}`)],
    ['querystring', await pub(slugA, '', `/conversations/${conv}/media/${mid}?ticket=${encodeURIComponent(tx)}&token=${encodeURIComponent(tx)}`)],
    ['توکنِ صاحب', await pub(slugA, '', `/conversations/${conv}/media/${mid}`, { token: A.owner.token })],
    ['بلیت در Authorization', await pub(slugA, '', `/conversations/${conv}/media/${mid}`, { token: tx })],
    ['پمپ', await pub(slugA, '', `/conversations/${conv}/media/${mid}`, { token: pump.token })],
    ['پیک', await pub(slugA, '', `/conversations/${conv}/media/${mid}`, { token: A.courier.token })],
  ]) {
    assert.deepEqual([r.status, errOf(r)], [401, 'verification_required'], `${what}: ${JSON.stringify(r.body)}`);
    assert.notEqual(r.headers.get('content-type'), 'audio/ogg', what);
  }
  //  چتِ خاموش ⇐ ۴۰۴ حتی با بلیتِ درست
  await setFlag(A.id, 'shop.chat', false);
  assert.deepEqual([(await pub(slugA, tx, `/conversations/${conv}/media/${mid}`)).status], [404]);
  await setFlag(A.id, 'shop.chat', true);
  //  «حذف برای همه» ِ دکان ⇒ بایت می‌رود
  const del = await h.api('DELETE', `/api/shop/chat/conversations/${conv}/messages/${sent.body.message.id}?for=all`, { token: A.owner.token });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  same(await pub(slugA, tx, `/conversations/${conv}/media/${mid}`), miss, 'پاک‌شده برای همه');
});

// ══════════════════════════════════════════════════════════════════ قفل‌های تازه (pg-concurrency)

test('قفلِ «سفارش ⇐ مأموریت» (`delivery.lockOrderOf`، تسویه): پیک و صاحبِ «ب» با شناسهٔ مأموریتِ «الف» ⇐ «پیدا نشد»، سفارش و مأموریتِ «الف» دست نمی‌خورد', async () => {
  const courierOf = async (S) => {
    const g = await h.post('/api/admin/subscriptions', { shopId: S.id, plan: 'm1', days: 30 }, { token: admin });
    assert.ok(g.status < 300, JSON.stringify(g.body));
    for (const k of ['shop.orders', 'shop.delivery']) await setFlag(S.id, k, true);
    const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: S.owner.token });
    assert.equal(inv.status, 201, JSON.stringify(inv.body));
    const cl = await h.post('/api/auth/staff', { code: inv.body.code, name: `پیکِ ${S.label}`, device: { uid: `cr-${uniq()}`, name: 'گوشیِ پیک', platform: 'android' } });
    assert.equal(cl.status, 201, JSON.stringify(cl.body));
    remember(`token:courier-${S.label}`, cl.body.accessToken);
    return { token: cl.body.accessToken, id: (await h.get('/api/courier/me', { token: cl.body.accessToken })).body.courier.id };
  };
  const ca = await courierOf(A);
  const cb = await courierOf(B);
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'delivery', paymentMethod: 'cod',
    customer: { name: 'احمدِ نهان', phone: '0791234532' }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 50_000 }], deliveryFeeMinor: 0,
  }, { token: A.owner.token, headers: { 'Idempotency-Key': `o32-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const order = r.body.order;
  for (const to of ['accepted', 'picking', 'packed']) assert.equal((await h.post(`/api/shop/orders/${order.id}/transition`, { to }, { token: A.owner.token })).status, 200);
  const as = await h.post(`/api/shop/delivery/orders/${order.id}/assign`, { mode: 'shop_courier', courierId: ca.id }, { token: A.owner.token });
  assert.equal(as.status, 201, JSON.stringify(as.body));
  const aid = as.body.assignment.id;
  const before = await one('SELECT status, settlement_id FROM delivery_assignments WHERE id=$1', [aid]);
  let n = 0;
  const ev = (id, type, extra = {}) => ({ eventId: `ev32-${Date.now()}-${++n}`, assignmentId: id, type, deviceTime: Date.now(), ...extra });
  //  پیکِ «ب» با شناسهٔ واقعی، همسایه و ساختگی ⇐ همان ردِ «پیدا نشد»
  const says = (c, events) => h.post('/api/courier/events', { deviceId: 'idor-32', events }, { token: c.token });
  const out = await says(cb, [ev(aid, 'picked_up'), ev(neighbour(aid), 'picked_up'), ev(newId('dla'), 'picked_up')]);
  assert.equal(out.status, 200, JSON.stringify(out.body));
  const codes = out.body.results.map((x) => [x.status, x.code || x.error?.code || '']);
  assert.equal(new Set(codes.map((c) => JSON.stringify(c))).size, 1, JSON.stringify(out.body.results));
  assert.notEqual(codes[0][0], 'applied');
  noLeak(out, [order.id, A.name, 'احمدِ نهان'], 'پیکِ «ب»');
  //  تسویهٔ «ب» با مأموریتِ «الف» زیرِ پیکِ خودش
  const st = await h.post(`/api/shop/delivery/couriers/${cb.id}/settlements`, { assignmentIds: [aid], handedOverMinor: 0, receivedMinor: 0 },
    { token: B.owner.token, headers: { 'Idempotency-Key': `s32-${uniq()}` } });
  assert.ok(st.status >= 400 && st.status < 500, JSON.stringify(st.body));
  noLeak(st, [order.id, A.name], 'تسویهٔ «ب»');
  assert.deepEqual(await one('SELECT status, settlement_id FROM delivery_assignments WHERE id=$1', [aid]), before);
  //  پیکِ خودِ «الف» همچنان کار می‌کند (قفل ردیفِ «الف» را نگه نداشت)
  const mine = await says(ca, [ev(aid, 'assigned'), ev(aid, 'picked_up')]);
  assert.equal(mine.body.results.at(-1).status, 'applied', JSON.stringify(mine.body));
});

test('قفل‌های تازه همه دکان‌دار یا از شناسهٔ سرور: `lockOrderOf`/تسویه با `a.shop_id=$1`، قفلِ مشورتیِ پیوستن/بازار از شناسهٔ نشست/کد/دکان، نه ورودی', () => {
  const src = (f) => fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', f), 'utf8');
  const delivery = src('delivery.js');
  const lock = delivery.slice(delivery.indexOf('function lockOrderOf'), delivery.indexOf('function lockOrderOf') + 400);
  assert.match(lock, /WHERE a\.shop_id=\$1 AND a\.id=\$2 FOR UPDATE OF o/);
  assert.match(lock, /o\.shop_id = a\.shop_id/);
  assert.match(src('delivery-settle.js'), /WHERE a\.shop_id=\$1 AND a\.courier_id=\$2 AND a\.id IN \(\$\{ph\}\) ORDER BY o\.id FOR UPDATE OF o/);
  for (const [f, re] of [
    ['staff-codes.js', /pg_advisory_xact_lock\(hashtext\(\$1\)\)', \[`staff-join-user:\$\{userId\}`\]/],
    ['staff-codes.js', /pg_advisory_xact_lock\(hashtext\(\$1\)\)', \[`staff-join-shop:\$\{code\.shop_id\}`\]/],
    ['market.js', /pg_advisory_xact_lock\(hashtext\(\$1\)\)', \[`market-search:\$\{id\}`\]/],
  ]) assert.match(src(f), re, f);
  //  هیچ قفلِ مشورتیِ تازه‌ای با رشتهٔ ساخته از ورودی (نه `$1`)
  for (const f of ['staff-codes.js', 'market.js', 'delivery.js', 'delivery-settle.js', 'catalog-products.js']) {
    assert.doesNotMatch(src(f), /pg_advisory_xact_lock\([^$)]*\$\{/, f);
  }
});

// ══════════════════════════════════════════════════════════════════ کدِ آفلاین، BOM

test('کدِ آفلاینِ دکان (pg-concurrency-2): کدِ آورده‌شده در «الف» برای «ب» ۴۰۹ ِ «جای دیگر»؛ پمپ ۴۰۱، پیک ۴۰۳؛ کدِ آرایه/شیء ۴۰۰ — بی ردیفِ تازه', async () => {
  const pc = offline.computerOfFingerprint(`win-${'b'.repeat(32)}`);
  const made = await h.post('/api/admin/shop-offline-codes', { plan: 'sub', computer: pc, days: 30 }, { token: admin });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  remember('offline-code', made.body.code);
  const { serial } = await offline.verify(made.body.code);
  await query('DELETE FROM shop_offline_codes WHERE serial=$1', [serial]);   //  دیتابیسِ بازگردانده‌شده (راهِ تازهٔ `ON CONFLICT`)
  const redeem = (who, body) => h.post('/api/vip/offline-code', body, { token: who.token });
  assert.equal((await redeem(pump, { code: made.body.code, computer: pc })).status, 401);
  assert.equal((await redeem(A.courier, { code: made.body.code, computer: pc })).status, 403);
  for (const body of [{ code: [made.body.code], computer: pc }, { code: { a: made.body.code }, computer: pc }, { code: made.body.code, computer: [pc] }]) {
    assert.equal((await redeem(A.owner, body)).status, 400, JSON.stringify(body));
  }
  const got = await redeem(A.owner, { code: made.body.code, computer: pc });
  assert.equal(got.status, 200, JSON.stringify(got.body));
  const again = await redeem(A.owner, { code: made.body.code, computer: pc });
  assert.equal(again.status, 200, 'تکرار در همان دکان');
  const other = await redeem(B.owner, { code: made.body.code, computer: pc });
  assert.deepEqual([other.status, errOf(other)], [409, 'code_used_elsewhere'], JSON.stringify(other.body));
  noLeak(other, [A.id, A.name], 'کدِ «الف» از «ب»');
  const rows = (await query('SELECT redeemed_shop_id FROM shop_offline_codes WHERE serial=$1', [serial])).rows;
  assert.deepEqual(rows.map((x) => x.redeemed_shop_id), [A.id]);
});

test('BOM ِ سابقه (`stock_bom`، repack-bom): شکلِ بد ⇒ BAD — آرایه به‌جای شیء، `parts` ِ نا-آرایه، `__proto__`، refId ِ خالی/نا-رشته؛ درست ⇒ اجازهٔ محل', () => {
  const need = (f) => docRoles.needOf('auditLog', { type: 'stock_bom', ...f });
  assert.equal(need({ refId: 'p-1', bom: { parts: [{ productId: 'p-2', qtyMilli: 1000 }] } }), docRoles.PLACES);
  assert.equal(need({ refId: 'p-1', bom: { parts: [] } }), docRoles.PLACES, 'خالی = برداشتن');
  for (const f of [
    { refId: 'p-1', bom: [{ parts: [] }] }, { refId: 'p-1', bom: { parts: { 0: 'x', length: 1 } } },
    { refId: 'p-1', bom: JSON.parse('{"__proto__":{"parts":[]}}') }, { refId: 'p-1', bom: null }, { refId: 'p-1' },
    { refId: '   ', bom: { parts: [] } }, { refId: ['p-1'], bom: { parts: [] } }, { refId: { toString: 'p-1' }, bom: { parts: [] } },
  ]) assert.equal(need(f), docRoles.BAD, JSON.stringify(f));
  assert.equal({}.parts, undefined);
});

// ══════════════════════════════════════════════════════════════════ نشت

test('نشت: هیچ توکن، بلیت، راز یا کدی در لاگِ سرور', () => {
  const all = lines.join('\n');
  for (const [s, label] of secrets) {
    if (label.startsWith('dev:')) continue;   //  شناسهٔ دستگاه راز نیست (`sync.device_mismatch` آن را در لاگ می‌گذارد)
    assert.equal(all.includes(s), false, `${label} در لاگ آمد`);
  }
  //  شناسهٔ دستگاهِ CR/LF دار (ورودیِ بدخواه) هرگز سطرِ ساختگی نمی‌سازد
  assert.equal(all.split(/\r?\n|\r/).some((l) => l.startsWith('[sync] جعلی')), false, 'سطرِ ساختگی در لاگ');
});
