'use strict';
/**
 * «از پشتیبانی بخواه» (مهاجرتِ ۹۶۰، `lib/support-requests.js`) — سمتِ کاربر و صفِ مدیر با «درستش کن» ِ یک‌کلیکی.
 *
 *   ۱) فهرست: هر مشکل سه زبان، کارِ شناخته، پارامترِ نام‌دار؛ هر کارِ خودکار گرداننده دارد.
 *   ۲) کاربر: کد/پارامترِ ناشناخته ۴۰۰، مشکلِ دکان بی دکان ۴۰۰؛ یک لمس ⇒ یک پیام در **همان** رشته (چتِ دوم نه)؛
 *      لمسِ دوباره و همکارِ همان دکان ⇒ همان درخواست (۲۰۰، بی پیامِ دوم)؛ دکانِ دیگر جدا؛ واقعیت‌ها فقط فهرست‌شده.
 *   ۳) نقش: بی‌توکن/توکنِ کاربر ۴۰۱، مالی ۴۰۳، بیننده فقط می‌خواند، مدیرِ عادی پرچم و پول را نمی‌نویسد؛ دکانِ دیگر ۴۰۴.
 *   ۴) پرچم: ردیفِ صریحِ همان دکان، `all` و دکانِ دیگر دست نخورده؛ سابقه (پیش ⇒ پس) + تاریخچهٔ پرچم؛ پاسخِ خودکار و پوشِ دکان؛
 *      کلیدِ یکتایی ⇒ همان پاسخ؛ بار دوم ⇒ `already` بی ردیف/پیامِ دوم؛ «غیرفعال کردنِ سرویس» ⇒ ۴۰۹ و هیچ نشانی.
 *   ۵) اشتراک و سقفِ دستگاه: تأیید لازم، روزِ بد ۴۰۰، یک دوره برای هر درخواست. کدِ ورود: همان کدِ زنده، بی کد در پاسخ.
 *   ۶) خطای همگام‌سازی: خلاصه همیشه، Stack فقط با اجازهٔ زندهٔ `read_private`. «حل شد» و پالایه‌های صف.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const R = require('../src/lib/support-requests');
const push = require('../src/lib/push');

const PASS = 'Admin!12345';
const ids = {};
const toks = {};
let seq = 0;
const key = () => `srq-${Date.now()}-${++seq}-key`;
let A;
let B;
let svc;
const pushed = [];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  for (const [u, role] of [['sr-arshad', 'superadmin'], ['sr-modir', 'admin'], ['sr-mali', 'finance'], ['sr-binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, now() + Object.keys(ids).length]);
  }
  A = await team('الف');
  B = await team('ب');
  svc = await require('../src/lib/admin-service').create({ name: 'پلِ پشتیبانی', roleCap: 'superadmin', createdBy: ids['sr-arshad'] });
  push.setDeliver(async (row, message) => { pushed.push({ row, message }); });
});
test.after(async () => { push.setDeliver(null); await h.stop(); });
test.beforeEach(() => resetAll());

async function tok(u) {
  if (!toks[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    toks[u] = r.body.token;
  }
  return toks[u];
}
async function team(label) {
  const owner = await h.newUser(`صاحبِ ${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ درخواستِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگردِ ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  return { owner, staff, shopId: made.body.shop.id };
}
const dev = (u) => `dv-${String(u?.email || 'guest').replace(/[^A-Za-z0-9_-]/g, '-')}`;
const ask = (u, body) => h.api('POST', '/api/support/requests', { token: u?.accessToken, body: { deviceUid: dev(u), app: 'shop', ...body } });
const mineOf = (u) => h.api('GET', `/api/support/requests?deviceUid=${dev(u)}`, { token: u.accessToken });
const threadOf = async (u) => (await h.get(`/api/support/thread?deviceUid=${dev(u)}`, { token: u.accessToken })).body;
const AD = async (who, method, path, body = null, headers = {}) => h.api(method, `/api/admin/support-requests${path}`, {
  token: await tok(who), body, headers: { 'Idempotency-Key': key(), ...headers },
});
const audits = (action, target) => many('SELECT * FROM audit_logs WHERE action=$1 AND target_id=$2 ORDER BY created_at, id', [action, target]);
const detailOf = (r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail);
const liveRow = (scope, k) => one('SELECT value FROM live_config WHERE scope=$1 AND key=$2', [scope, k]);

/* ── ۱) فهرست ─────────────────────────────────────────────────────── */

test('فهرستِ مشکل‌ها: سه زبان برای هر متن، کارِ شناخته، نامِ هر پارامتر، واقعیتِ شناخته', () => {
  const FIXES = ['enable_flag', 'grant_subscription', 'raise_device_limit', 'resend_email_code', 'show_sync_error', 'none'];
  const codes = R.REG.problems.map((p) => p.code);
  assert.deepEqual(codes, [...new Set(codes)]);
  for (const p of ['flag_off', 'plan_required', 'subscription_expired', 'sync_no_shop', 'sync_refused', 'email_code_not_arriving', 'device_limit', 'other']) {
    assert.ok(codes.includes(p), p);
  }
  for (const p of R.REG.problems) {
    assert.match(p.code, /^[a-z][a-z0-9_]{1,40}$/);
    assert.ok(FIXES.includes(p.fix), `${p.code}: ${p.fix}`);
    for (const f of ['title', 'ask', 'done']) for (const l of ['fa', 'en', 'ps']) assert.ok(p[f][l] && p[f][l].length > 3, `${p.code}.${f}.${l}`);
    for (const x of p.param) {
      assert.match(x, /^[a-z0-9_.]{1,64}$/);
      for (const l of ['fa', 'en', 'ps']) assert.ok(R.REG.names[x]?.[l], `${x}.${l}`);
    }
    if (p.param.length) for (const f of ['title', 'ask', 'done']) assert.ok(p[f].fa.includes('{name}'), `${p.code}.${f}`);
    for (const k of p.facts) assert.ok(R.REG.facts[k], `${p.code}: ${k}`);
  }
  //  هر پرچمِ `flag_off` در کاتالوگِ پرچم‌های شناخته است و هر قابلیتِ `plan_required` کلیدِ اشتراک
  const KNOWN = require('../src/routes/admin-flags').KNOWN.map((k) => k.key);
  for (const f of R.PROBLEMS.get('flag_off').param) assert.ok(KNOWN.includes(f), f);
  const { GRANTABLE_KEYS } = require('../src/lib/features');
  for (const f of R.PROBLEMS.get('plan_required').param) assert.ok(GRANTABLE_KEYS.includes(f), f);
  assert.equal(R.textOf(R.PROBLEMS.get('flag_off'), 'done', 'fa', 'shop.orders'), 'پشتیبانی سفارشِ آنلاین را برای دکانِ شما روشن کرد.');
  assert.equal(R.langOf('prs'), 'fa');
});

/* ── ۲) کاربر ─────────────────────────────────────────────────────── */

test('کاربر: کد و پارامترِ ناشناخته، مشکلِ دکان بی دکان، و مهمانِ بی شناسه رد می‌شوند — هیچ ردیفی', async () => {
  const n0 = (await one('SELECT COUNT(*)::int n FROM support_requests')).n;
  assert.equal((await ask(A.owner, { code: 'flag_off:shop.secret' })).body.error.code, 'bad_problem');
  assert.equal((await ask(A.owner, { code: 'drop_tables' })).body.error.code, 'bad_problem');
  assert.equal((await ask(A.owner, { code: 'subscription_expired:x' })).body.error.code, 'bad_problem');
  const guest = await h.api('POST', '/api/support/requests', { body: { deviceUid: 'dv-guest-1', code: 'flag_off:shop.orders' } });
  assert.equal(guest.status, 400);
  assert.equal(guest.body.error.code, 'shop_required');
  const none = await h.api('POST', '/api/support/requests', { body: { code: 'other' } });
  assert.equal(none.status, 400);
  const pump = await h.api('POST', '/api/support/requests', { body: { deviceUid: 'dv-p', app: 'pump', code: 'other' } });
  assert.equal(pump.body.error.code, 'bad_app');
  assert.equal((await one('SELECT COUNT(*)::int n FROM support_requests')).n, n0);
});

test('کاربر: یک لمس ⇒ یک پیام در همان رشته؛ دوباره و همکارِ همان دکان ⇒ همان درخواست؛ دکانِ دیگر جدا', async () => {
  const first = await ask(A.owner, {
    code: 'flag_off:shop.delivery', lang: 'fa',
    context: { appVersion: '2.14.5', platform: 'android', screen: 'desk:off:delivery', note: 'سلام', email: 'a@b.cd', syncError: 'x', screen2: 1 },
  });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  assert.equal(first.body.created, true);
  assert.equal(first.body.request.code, 'flag_off:shop.delivery');
  const row = await one('SELECT * FROM support_requests WHERE id=$1', [first.body.request.id]);
  assert.deepEqual(row.context, { appVersion: '2.14.5', platform: 'android', screen: 'desk:off:delivery' }, 'فقط واقعیت‌های فهرست‌شدهٔ همین مشکل');
  assert.equal(row.shop_id, A.shopId);
  const th = await threadOf(A.owner);
  assert.equal(th.thread.id, row.thread_id, 'همان رشتهٔ همیشگیِ پشتیبانی');
  const asks = th.messages.filter((m) => m.body === 'لطفاً تحویل و پیک را برای دکانِ من روشن کنید.');
  assert.equal(asks.length, 1);
  assert.equal(asks[0].sender, 'user');
  assert.ok(!JSON.stringify(th).includes('shop.delivery'), '⛔ نامِ درونیِ پرچم در رشتهٔ کاربر نیست');

  const again = await ask(A.owner, { code: 'flag_off:shop.delivery' });
  assert.equal(again.status, 200);
  assert.equal(again.body.created, false);
  assert.equal(again.body.request.id, first.body.request.id);
  const mate = await ask(A.staff, { code: 'flag_off:shop.delivery' });
  assert.equal(mate.status, 200);
  assert.equal(mate.body.request.id, first.body.request.id);
  assert.equal(mate.body.sameShop, true);
  assert.ok(!('message' in mate.body), 'پیامِ رشتهٔ دیگر برنمی‌گردد');
  assert.equal((await threadOf(A.owner)).messages.filter((m) => m.sender === 'user').length, 1, '⛔ پیامِ دوم ساخته نشد');

  const other = await ask(B.owner, { code: 'flag_off:shop.delivery', lang: 'en' });
  assert.equal(other.status, 201);
  assert.notEqual(other.body.request.id, first.body.request.id);
  assert.ok((await threadOf(B.owner)).messages.some((m) => m.body === 'Please turn Delivery and couriers on for my shop.'));

  const mineA = (await mineOf(A.staff)).body.requests.map((r) => r.id);
  const mineB = (await mineOf(B.owner)).body.requests.map((r) => r.id);
  assert.ok(mineA.includes(first.body.request.id));
  assert.ok(!mineB.includes(first.body.request.id), '⛔ دکانِ دیگر درخواستِ «الف» را نمی‌بیند');
  //  شناسهٔ دکانِ ساختگی در بدنه هیچ اثری ندارد
  const spoof = await ask(B.owner, { code: 'flag_off:shop.chat', shopId: A.shopId, shop_id: A.shopId });
  assert.equal((await one('SELECT shop_id FROM support_requests WHERE id=$1', [spoof.body.request.id])).shop_id, B.shopId);
});

test('مهمانِ بی‌حساب: «کدِ ورود نمی‌رسد» با شناسهٔ دستگاه، تکرار ⇒ همان', async () => {
  const body = { deviceUid: 'dv-guest-mail', code: 'email_code_not_arriving', contact: 'Guest.Mail@Test.Local', name: 'مهمان' };
  const a = await h.api('POST', '/api/support/requests', { body });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await h.api('POST', '/api/support/requests', { body });
  assert.equal(b.status, 200);
  assert.equal(b.body.request.id, a.body.request.id);
  const row = await one('SELECT r.scope_key, t.contact FROM support_requests r JOIN support_threads t ON t.id=r.thread_id WHERE r.id=$1', [a.body.request.id]);
  assert.match(row.scope_key, /^thr:/);
  assert.equal(row.contact, 'Guest.Mail@Test.Local');
});

/* ── ۳) نقش و دکانِ دیگر ──────────────────────────────────────────── */

test('نقش: بی‌توکن و توکنِ کاربر ۴۰۱، مالی ۴۰۳، بیننده فقط می‌خواند، مدیرِ عادی پرچم/پول نه؛ دکانِ دیگر ۴۰۴', async () => {
  const r = (await ask(A.owner, { code: 'flag_off:shop.chat' })).body.request;
  assert.equal((await h.api('GET', '/api/admin/support-requests')).status, 401);
  assert.equal((await h.api('GET', '/api/admin/support-requests', { token: A.owner.accessToken })).status, 401);
  assert.equal((await AD('sr-mali', 'GET', '')).status, 403);
  assert.equal((await AD('sr-mali', 'GET', `/${r.id}`)).status, 403);
  const v = await AD('sr-binande', 'GET', '');
  assert.equal(v.status, 200);
  assert.ok(v.body.requests.some((x) => x.id === r.id));
  assert.equal((await AD('sr-binande', 'POST', `/${r.id}/resolve`, { shopId: A.shopId })).status, 403);
  assert.equal((await AD('sr-binande', 'POST', `/${r.id}/fix/enable-flag`, { shopId: A.shopId })).status, 403);
  assert.equal((await AD('sr-modir', 'POST', `/${r.id}/fix/enable-flag`, { shopId: A.shopId })).status, 403);
  const wrong = await AD('sr-arshad', 'POST', `/${r.id}/fix/enable-flag`, { shopId: B.shopId });
  assert.equal(wrong.status, 404);
  assert.equal(wrong.body.error.code, 'request_not_found');
  assert.equal((await AD('sr-arshad', 'POST', `/${r.id}/resolve`, { shopId: B.shopId })).status, 404);
  assert.equal((await AD('sr-arshad', 'GET', `/${r.id}/sync-error?shopId=${B.shopId}`)).status, 404);
  assert.equal((await AD('sr-arshad', 'POST', `/${r.id}/resolve`, {})).body.error.code, 'shop_required');
  assert.equal((await AD('sr-arshad', 'GET', '/srq_nist')).status, 404);
  assert.equal((await liveRow(A.shopId, 'shop.chat')), null, '⛔ هیچ ردِ نقش/دکانی چیزی ننوشت');
  assert.equal((await many('SELECT 1 FROM support_request_fixes WHERE request_id=$1', [r.id])).length, 0);
});

/* ── ۴) پرچم ──────────────────────────────────────────────────────── */

test('«روشن کن»: ردیفِ صریحِ همان دکان، سابقهٔ پیش ⇒ پس، پاسخِ خودکار و پوشِ دکان؛ یک بار', async () => {
  const lc = require('../src/lib/live-config');
  await lc.set(A.shopId, 'shop.orders', false, 'test');
  //  دستگاهِ شاگرد (همان دکان) و صاحبِ «ب» برای پوش
  await push.register({ app: 'shop', token: `tok-staffA-${seq}`, provider: 'fcm', userId: A.staff.user.id, shopId: A.shopId, deviceUid: 'dv-sa', platform: 'android' });
  await push.register({ app: 'shop', token: `tok-ownerB-${seq}`, provider: 'fcm', userId: B.owner.user.id, shopId: B.shopId, deviceUid: 'dv-ob', platform: 'android' });
  const r = (await ask(A.owner, { code: 'flag_off:shop.orders', context: { screen: 'orders:setup' } })).body.request;
  const allBefore = await liveRow('all', 'shop.orders');
  const bBefore = await liveRow(B.shopId, 'shop.orders');
  const list = await AD('sr-arshad', 'GET', '?problem=flag_off:shop.orders');
  const card = list.body.requests.find((x) => x.id === r.id);
  assert.equal(card.title, 'سفارشِ آنلاین برای این دکان خاموش است');
  assert.equal(card.fix, 'enable_flag');
  const det = await AD('sr-arshad', 'GET', `/${r.id}`);
  assert.deepEqual(det.body.state.flag, { key: 'shop.orders', effective: false, explicit: false, hold: false });
  assert.ok(det.body.messages.some((m) => m.body === 'لطفاً سفارشِ آنلاین را برای دکانِ من روشن کنید.'));

  //  کارِ نادرست برای این مشکل ⇒ ۴۰۹ بی نشان
  const bad = await AD('sr-arshad', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId, confirm: true });
  assert.equal(bad.body.error.code, 'fix_not_for_problem');

  pushed.length = 0;
  const k = key();
  const done = await AD('sr-arshad', 'POST', `/${r.id}/fix/enable-flag`, { shopId: A.shopId }, { 'Idempotency-Key': k });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.already, false);
  assert.equal(done.body.fix.result, 'done');
  assert.equal(done.body.state.flag.effective, true);
  assert.equal(JSON.parse((await liveRow(A.shopId, 'shop.orders')).value), true);
  assert.deepEqual(await liveRow('all', 'shop.orders'), allBefore, '⛔ `all` دست نخورد');
  assert.deepEqual(await liveRow(B.shopId, 'shop.orders'), bBefore, '⛔ دکانِ دیگر دست نخورد');
  const hist = await one(`SELECT * FROM live_config_history WHERE scope=$1 AND key='shop.orders' ORDER BY created_at DESC, id DESC LIMIT 1`, [A.shopId]);
  assert.match(hist.reason, new RegExp(r.id));
  const au = await audits('admin.support_request_fix', r.id);
  assert.equal(au.length, 1);
  const d = detailOf(au[0]);
  assert.equal(au[0].shop_id, A.shopId);
  assert.equal(au[0].user_id, ids['sr-arshad']);
  assert.equal(d.fix, 'enable_flag');
  assert.equal(d.problem, 'flag_off');
  assert.equal(d.param, 'shop.orders');
  assert.equal(d.before.effective, false);
  assert.equal(d.after.effective, true);
  const msgs = (await threadOf(A.owner)).messages;
  const reply = msgs.filter((m) => m.body === 'پشتیبانی سفارشِ آنلاین را برای دکانِ شما روشن کرد.');
  assert.equal(reply.length, 1);
  assert.equal(reply[0].sender, 'admin');
  const toShop = pushed.filter((p) => p.row.shop_id === A.shopId && p.message.data?.type === 'support');
  assert.ok(toShop.some((p) => p.row.user_id === A.staff.user.id), 'پوش به دستگاهِ دیگرِ همان دکان');
  assert.ok(!pushed.some((p) => p.row.shop_id === B.shopId), '⛔ دکانِ دیگر پوش نگرفت');

  const replay = await AD('sr-arshad', 'POST', `/${r.id}/fix/enable-flag`, { shopId: A.shopId }, { 'Idempotency-Key': k });
  assert.equal(replay.headers.get('idempotent-replayed'), 'true');
  const second = await AD('sr-arshad', 'POST', `/${r.id}/fix/enable-flag`, { shopId: A.shopId });
  assert.equal(second.status, 200);
  assert.equal(second.body.already, true);
  assert.equal((await audits('admin.support_request_fix', r.id)).length, 1, '⛔ ردیفِ دوم نه');
  assert.equal((await threadOf(A.owner)).messages.filter((m) => m.sender === 'admin').length, 1, '⛔ پاسخِ دوم نه');

  //  حل شد ⇒ لمسِ تازه درخواستِ تازه است
  const res1 = await AD('sr-modir', 'POST', `/${r.id}/resolve`, { shopId: A.shopId, note: 'روشن شد' });
  assert.equal(res1.status, 200, JSON.stringify(res1.body));
  assert.equal(res1.body.request.status, 'resolved');
  assert.equal((await audits('admin.support_request_resolved', r.id)).length, 1);
  const res2 = await AD('sr-modir', 'POST', `/${r.id}/resolve`, { shopId: A.shopId });
  assert.equal(res2.body.unchanged, true);
  assert.equal((await audits('admin.support_request_resolved', r.id)).length, 1);
  assert.equal((await AD('sr-arshad', 'POST', `/${r.id}/fix/enable-flag`, { shopId: A.shopId })).body.error.code, 'request_resolved');
  const fresh = await ask(A.owner, { code: 'flag_off:shop.orders' });
  assert.equal(fresh.status, 201);
  assert.notEqual(fresh.body.request.id, r.id);
});

test('«روشن کن» با «غیرفعال کردنِ سرویس» ِ همان دکان ⇒ ۴۰۹ و هیچ نشان/پرچمی؛ کلیدِ سرویسِ پل ⇒ «انجام‌دهنده» در سابقه', async () => {
  const r = (await ask(B.owner, { code: 'flag_off:pay.hesabpay' })).body.request;
  await query(`INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_at) VALUES ($1,$2,'service_disabled','pay.hesabpay','آزمون','t',$3)`,
    [newId('hld'), B.shopId, now()]);
  const held = await AD('sr-arshad', 'POST', `/${r.id}/fix/enable-flag`, { shopId: B.shopId });
  assert.equal(held.status, 409, JSON.stringify(held.body));
  assert.equal(held.body.error.code, 'shop_hold_active');
  assert.equal(await liveRow(B.shopId, 'pay.hesabpay'), null);
  assert.equal((await many('SELECT 1 FROM support_request_fixes WHERE request_id=$1', [r.id])).length, 0, 'شکست ⇒ نشان برداشته شد');
  await query(`UPDATE shop_holds SET lifted_at=$2 WHERE shop_id=$1 AND service='pay.hesabpay'`, [B.shopId, now()]);

  const viaPanel = await h.api('POST', `/api/admin/support-requests/${r.id}/fix/enable-flag`, {
    body: { shopId: B.shopId, resolve: true },
    headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': 'panel-owner-1', 'X-Actor-Role': 'superadmin', 'X-Actor-Name': 'Owner', 'Idempotency-Key': key() },
  });
  assert.equal(viaPanel.status, 200, JSON.stringify(viaPanel.body));
  assert.equal(viaPanel.body.request.status, 'resolved', '`resolve: true` ⇒ پس از کارِ موفق بسته');
  const au = (await audits('admin.support_request_fix', r.id))[0];
  assert.equal(detailOf(au).actor.id, 'panel-owner-1');
});

/* ── ۵) اشتراک، سقفِ دستگاه، کدِ ورود ─────────────────────────────── */

test('«تمدید»: تأیید لازم، روزِ بد ۴۰۰، یک دوره برای هر درخواست؛ سقفِ دستگاه از همان اشتراک', async () => {
  const r = (await ask(A.owner, { code: 'subscription_expired' })).body.request;
  assert.equal((await AD('sr-arshad', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId })).body.error.code, 'confirm_required');
  assert.equal((await AD('sr-arshad', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId, confirm: true, days: 0 })).body.error.code, 'bad_days');
  assert.equal((await AD('sr-arshad', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId, confirm: true, days: 9999 })).body.error.code, 'bad_days');
  assert.equal((await AD('sr-modir', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId, confirm: true })).status, 403);
  const t0 = now();
  const g = await AD('sr-arshad', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId, confirm: true, days: 30 });
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.equal(g.body.state.subscription.status, 'active');
  assert.ok(g.body.state.subscription.endsAt >= t0 + 29 * 86400e3);
  const subs1 = await many(`SELECT * FROM subscriptions WHERE shop_id=$1 AND status='active'`, [A.shopId]);
  assert.equal(subs1.length, 1);
  const end1 = Number(subs1[0].ends_at);
  const again = await AD('sr-arshad', 'POST', `/${r.id}/fix/grant-subscription`, { shopId: A.shopId, confirm: true, days: 30 });
  assert.equal(again.body.already, true);
  assert.equal(Number((await one(`SELECT ends_at FROM subscriptions WHERE id=$1`, [subs1[0].id])).ends_at), end1, '⛔ دورهٔ دوم نه');
  const au = detailOf((await audits('admin.support_request_fix', r.id))[0]);
  assert.equal(au.before.status === 'active', false);
  assert.equal(au.after.status, 'active');
  assert.equal(au.after.days, 30);
  assert.ok((await threadOf(A.owner)).messages.some((m) => m.body === 'پشتیبانی اشتراکِ دکانِ شما را تمدید کرد.'));

  const dl = (await ask(A.owner, { code: 'device_limit', context: { devices: 3, maxDevices: 3, rejected: 9 } })).body.request;
  assert.deepEqual((await one('SELECT context FROM support_requests WHERE id=$1', [dl.id])).context, { devices: 3, maxDevices: 3 });
  const cur = Number(subs1[0].max_devices);
  assert.equal((await AD('sr-arshad', 'POST', `/${dl.id}/fix/raise-device-limit`, { shopId: A.shopId, confirm: true, devices: cur })).body.error.code, 'bad_devices');
  const up = await AD('sr-arshad', 'POST', `/${dl.id}/fix/raise-device-limit`, { shopId: A.shopId, confirm: true });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  const after = await one(`SELECT * FROM subscriptions WHERE id=$1`, [subs1[0].id]);
  assert.equal(Number(after.max_devices), cur + 1);
  assert.equal(Number(after.ends_at), end1, 'پایانِ اشتراک همان ماند');
  assert.equal(detailOf((await audits('admin.support_request_fix', dl.id))[0]).after.maxDevices, cur + 1);
});

test('«کد را دوباره بفرست»: همان کدِ زندهٔ همان ایمیل، بی کد در پاسخ و سابقه؛ کدِ زنده نیست ⇒ ۴۰۹', async () => {
  const email = 'resend.me@test.local';
  const body = { deviceUid: 'dv-guest-resend', code: 'email_code_not_arriving', contact: email };
  const r = (await h.api('POST', '/api/support/requests', { body })).body.request;
  const none = await AD('sr-modir', 'POST', `/${r.id}/fix/resend-email-code`, { shopId: '' });
  assert.equal(none.status, 409);
  assert.equal(none.body.error.code, 'no_live_code');
  const codes = require('../src/lib/login-codes');
  const made = await codes.requestCode({ app: 'shop', email, ip: '127.0.0.9' });
  const reqId = made.request_id || made.requestId || made.request?.request_id;
  assert.ok(reqId, JSON.stringify(made));
  const out = await AD('sr-modir', 'POST', `/${r.id}/fix/resend-email-code`, { shopId: '' });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.equal(out.body.fix.after.book, 'login');
  assert.match(out.body.fix.after.email, /\*/, 'ایمیل پوشانده');
  const sealed = await one('SELECT code_sealed FROM login_requests WHERE request_id=$1', [reqId]);
  const code = codes.unseal(sealed.code_sealed);
  assert.ok(code && !JSON.stringify(out.body).includes(code), '⛔ کد در پاسخ نیست');
  const au = (await audits('admin.support_request_fix', r.id))[0];
  assert.ok(!JSON.stringify(au.detail).includes(code), '⛔ کد در سابقه نیست');
  assert.ok(!JSON.stringify(au.detail).includes(email), '⛔ ایمیلِ خام در سابقه نیست');
});

/* ── ۶) خطای همگام‌سازی، صف ───────────────────────────────────────── */

test('خطای همگام‌سازی: خلاصه همیشه، Stack و لاگ فقط با اجازهٔ زندهٔ `read_private` ِ همین مدیر', async () => {
  const r = (await ask(B.owner, { code: 'sync_refused', context: { syncError: 'rejected_ops', rejected: 4 } })).body.request;
  await query(`INSERT INTO client_errors (id, app, account_kind, account_id, device_id, app_version, schema_version, message, stack, log_tail, at, created_at)
               VALUES ($1,'shop','shop',$2,'dev-b1','2.14.5',0,'۴ تغییر پذیرفته نشد','STACK-PRIVATE','LOG-PRIVATE',$3,$3)`, [newId('cer'), B.shopId, now()]);
  const plain = await AD('sr-modir', 'GET', `/${r.id}/sync-error`);
  assert.equal(plain.status, 200, JSON.stringify(plain.body));
  assert.equal(plain.body.restricted, true);
  assert.equal(plain.body.errors[0].message, '۴ تغییر پذیرفته نشد');
  assert.ok(!JSON.stringify(plain.body).includes('STACK-PRIVATE') && !JSON.stringify(plain.body).includes('dev-b1'));
  const t = now();
  await query(`INSERT INTO admin_access_grants (id, app, tenant_id, admin_id, actor_id, requested_by, scope, consent, status, reason, minutes,
                 granted_by, created_at, decided_at, starts_at, expires_at)
               VALUES ($1,'shop',$2,$3,'','sr-modir','read_private','tenant','active','بررسیِ همگام‌سازی',30,'owner',$4,$4,$4,$5)`,
  [newId('agr'), B.shopId, ids['sr-modir'], t, t + 30 * 60_000]);
  const full = await AD('sr-modir', 'GET', `/${r.id}/sync-error`);
  assert.equal(full.body.restricted, false);
  assert.equal(full.body.errors[0].stack, 'STACK-PRIVATE');
  const other = await AD('sr-arshad', 'GET', `/${r.id}/sync-error`);
  assert.equal(other.body.restricted, true, '⛔ اجازهٔ مدیرِ دیگر به کارِ این یکی نمی‌آید');
  assert.equal((await audits('admin.support_sync_error_viewed', r.id)).length, 3);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='admin.support_access_used' AND shop_id=$1`, [B.shopId])).length, 1);
});

test('صف: تازه‌ترین بالا، پالایهٔ کد/دکان/حال/سن، شمارِ باز و نخوانده؛ پالایهٔ ناشناخته ۴۰۰', async () => {
  const x = (await ask(B.owner, { code: 'plan_required:delivery' })).body.request;
  const all = await AD('sr-binande', 'GET', '?limit=200');
  const created = all.body.requests.map((r) => r.createdAt);
  assert.deepEqual(created, [...created].sort((a, b) => b - a), 'تازه‌ترین بالا');
  assert.equal(all.body.requests[0].id, x.id);
  assert.ok(all.body.open >= 1 && all.body.unread >= 1);
  const onlyB = await AD('sr-binande', 'GET', `?shop=${B.shopId}`);
  assert.ok(onlyB.body.requests.length && onlyB.body.requests.every((r) => r.shopId === B.shopId));
  const plan = await AD('sr-binande', 'GET', '?problem=plan_required');
  assert.ok(plan.body.requests.every((r) => r.problem === 'plan_required'));
  assert.equal(plan.body.requests.find((r) => r.id === x.id).title, 'تحویل و پیک در اشتراکِ این دکان نیست');
  const resolved = await AD('sr-binande', 'GET', '?status=resolved');
  assert.ok(resolved.body.requests.every((r) => r.status === 'resolved') && resolved.body.requests.length >= 1);
  const old = await AD('sr-binande', 'GET', '?minAgeHours=1');
  assert.ok(!old.body.requests.some((r) => r.id === x.id), JSON.stringify(old.body).slice(0, 300));
  await query('UPDATE support_requests SET created_at=$2 WHERE id=$1', [x.id, now() - 3 * 3600e3]);
  const aged = await AD('sr-binande', 'GET', '?minAgeHours=1');
  assert.ok(aged.body.requests?.some((r) => r.id === x.id), JSON.stringify(aged.body).slice(0, 300));
  assert.ok(!(await AD('sr-binande', 'GET', '?maxAgeHours=2')).body.requests.some((r) => r.id === x.id));
  assert.equal((await AD('sr-binande', 'GET', '?problem=nope')).body.error.code, 'bad_problem');
  assert.equal((await AD('sr-binande', 'GET', '?status=x')).status, 400);
  const viewed = await AD('sr-binande', 'GET', `/${x.id}`);
  assert.equal(viewed.status, 200);
  assert.equal((await audits('admin.support_request_viewed', x.id)).length, 1);
});
