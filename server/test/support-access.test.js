'use strict';
/**
 * ۲-۹ (ACP-D1 الف، ACP-D2 الف) — اجازهٔ موقتِ دسترسیِ پشتیبانی به یک دکان (G1، TEN-04؛ توانایی ‎support-access.v1‎ ِ پلِ
 * پنل ۱.۵۰.۹۰؛ جدولِ `admin_access_grants`، مهاجرتِ ۸۱۰).
 *
 *   ۱) درها و نقش: بی‌توکن/توکنِ دکان ۴۰۱، مالی ۴۰۳، بیننده فقط می‌خواند، مدیرِ عادی نمی‌نویسد، دکانِ ناموجود ۴۰۴.
 *   ۲) سنجش: مدت عددِ صحیحِ ۵ تا ۱۴۴۰ (پیش‌فرض ۶۰؛ ۱۴۴۱ ⇒ ‎400 bad_minutes‎، نه بریدن)، دامنه، دلیل، رضایت — ردشده هیچ ردیفی ندارد.
 *   ۳) رضایتِ دکان: درخواست ⇒ pending و خبرِ صاحب؛ فقط صاحبِ **همان** دکان می‌پذیرد (تأییدِ دوباره)/رد/لغو می‌کند؛
 *      پذیرفته ⇒ از همان دم تا `minutes`؛ `useGrant` فقط همان درخواست‌کننده، همان دکان و همان دامنه، با شمار و سابقه.
 *   ۴) گذشتن و لغو: زمانِ سرور؛ اجازهٔ گذشته/لغوشده کار نمی‌کند؛ درخواستِ ۲۴ساعتهٔ بی‌پاسخ گذشته است؛ لغوِ دوباره یک‌باره.
 *   ۵) شکستنِ شیشه: فقط مالکِ پلتفرم (مدیرِ ارشدِ دیگر ‎403 owner_only‎ پیش از رمز)، احرازِ دوباره (نشستِ کهنه، رمزِ غلط،
 *      قفلِ پنج‌باره)، دلیلِ ۱۰ تا ۳۰۰، `notify_owner: true`؛ خبرِ صاحب و `notified`؛ رمز هیچ‌جا نمی‌نشیند؛ پلِ پنل.
 *   ۶) هرگز همیشگی: قیدهای پایگاه؛ جداسازیِ دکان‌ها؛ ردیف‌های سابقه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const access = require('../src/lib/support-access');

const PASS = 'Admin!12345';
const ids = {};
const toks = {};
let seq = 0;
const key = () => `sa-${Date.now()}-${++seq}-key`;
let A0;   //  دکانِ «الف»
let B0;   //  دکانِ «ب»
let svc;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  for (const [u, role] of [['sa-malek', 'superadmin'], ['sa-arshad2', 'superadmin'], ['sa-modir', 'admin'],
    ['sa-mali', 'finance'], ['sa-binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, now() + Object.keys(ids).length]);
  }
  //  مالکِ پلتفرم = sa-malek (ستونِ `is_owner`، یکتا)
  await query('UPDATE admins SET is_owner = false');
  await query('UPDATE admins SET is_owner = true WHERE id=$1', [ids['sa-malek']]);
  A0 = await team('الف');
  B0 = await team('ب');
  svc = await require('../src/lib/admin-service').create({ name: 'پلِ پشتیبانی', roleCap: 'superadmin', createdBy: ids['sa-malek'] });
});
test.after(async () => { await h.stop(); });
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
  const made = await h.post('/api/shop', { name: `دکانِ پشتیبانیِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگردِ ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  return { owner, staff, shopId: made.body.shop.id };
}
const base = (shopId) => `/api/admin/shops/${shopId}/access-grants`;
const A = async (who, method, path, body = null, headers = {}) => h.api(method, path, { token: await tok(who), body, headers });
const S = (actor, role, method, path, body = null, headers = {}) => h.api(method, path, {
  body, headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': actor, 'X-Actor-Role': role, ...headers },
});
const O = (t, method, path, body = null) => h.api(method, `/api/shop/support-access${path}`, { token: t.owner.accessToken, body });
const rows = (shopId) => many('SELECT * FROM admin_access_grants WHERE tenant_id=$1 ORDER BY created_at, id', [shopId]);
const row = (id) => one('SELECT * FROM admin_access_grants WHERE id=$1', [id]);
const auditsOf = (action, shopId) => many('SELECT * FROM audit_logs WHERE action=$1 AND shop_id=$2 ORDER BY created_at, id', [action, shopId]);
const detail = (r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail);
const inbox = async (u) => (await h.get('/api/me/notices', { token: u.accessToken })).body.notices || [];
const REQ = { scope: 'read_private', minutes: 30, reason: 'بررسیِ خطای همگام‌سازی', consent: 'tenant' };
const BG = { scope: 'read_private', minutes: 60, reason: 'گزارشِ تقلب؛ صاحب در دسترس نیست', consent: 'break_glass', notify_owner: true };
const fakeReq = (adminId, actor = null) => ({ admin: { id: adminId, name: 'x' }, actor, service: actor ? { keyId: 'k' } : null, headers: {} });

/* ── ۱) درها و نقش ─────────────────────────────────────────────────── */

test('۱) نقش: بی‌توکن و توکنِ دکان ۴۰۱؛ مالی ۴۰۳؛ بیننده و مدیرِ عادی می‌خوانند ولی نمی‌نویسند؛ دکانِ ناموجود ۴۰۴', async () => {
  assert.equal((await h.get(base(A0.shopId))).status, 401);
  assert.equal((await h.get(base(A0.shopId), { token: A0.owner.accessToken })).status, 401);
  assert.equal((await h.post(base(A0.shopId), REQ, { token: A0.owner.accessToken })).status, 401);
  assert.equal((await A('sa-mali', 'GET', base(A0.shopId))).status, 403);
  for (const who of ['sa-binande', 'sa-modir']) {
    const r = await A(who, 'GET', base(A0.shopId));
    assert.equal(r.status, 200, `${who} ⇒ ${r.status}`);
    assert.deepEqual(r.body.grants, []);
    assert.equal(r.body.policy.defaultMinutes, 60);
    assert.equal(r.body.policy.maxMinutes, 1440);
    for (const p of ['', '/break-glass']) {
      assert.equal((await A(who, 'POST', base(A0.shopId) + p, p ? BG : REQ, { 'Idempotency-Key': key() })).status, 403, `${who} POST${p}`);
    }
  }
  assert.equal((await S('panel:4', 'admin', 'POST', base(A0.shopId), REQ)).status, 403, 'اپراتورِ پنل ⇒ admin ⇒ نه');
  const nist = await A('sa-malek', 'GET', base('shp_nist'));
  assert.equal(nist.status, 404);
  assert.equal(nist.body.error.code, 'shop_not_found');
  assert.equal((await rows(A0.shopId)).length, 0);
});

/* ── ۲) سنجش ───────────────────────────────────────────────────────── */

test('۲) مدت ۵ تا ۱۴۴۰ (۱۴۴۱ ⇒ bad_minutes، نه بریدن)، دامنه، دلیل و رضایت پیش از هر نوشتن؛ پیش‌فرضِ ۶۰؛ سقفِ دقیقِ ۱۴۴۰', async () => {
  const cases = [
    [{ ...REQ, minutes: 1441 }, 'bad_minutes'], [{ ...REQ, minutes: 4 }, 'bad_minutes'], [{ ...REQ, minutes: 60.5 }, 'bad_minutes'],
    [{ ...REQ, minutes: '60' }, 'bad_minutes'], [{ ...REQ, minutes: -60 }, 'bad_minutes'], [{ ...REQ, scope: 'everything' }, 'bad_scope'],
    [{ ...REQ, reason: 'نه' }, 'reason_required'], [{ ...REQ, reason: 'د'.repeat(201) }, 'reason_required'],
    [{ ...REQ, reason: undefined }, 'reason_required'], [{ ...REQ, consent: 'break_glass' }, 'bad_consent'],
  ];
  for (const [body, code] of cases) {
    const r = await A('sa-malek', 'POST', base(A0.shopId), body, { 'Idempotency-Key': key() });
    assert.equal(r.status, 400, `${JSON.stringify(body)} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, code, JSON.stringify(body));
  }
  assert.equal((await rows(A0.shopId)).length, 0, 'ردشده ردیفی نساخت');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM admin_write_claims`)).n, 0, 'ردشده نشانِ کلید نگذاشت');

  const d = await A('sa-malek', 'POST', base(A0.shopId), { reason: 'پیش‌فرضِ مدت', scope: undefined });
  assert.equal(d.status, 201, JSON.stringify(d.body));
  assert.equal(d.body.grant.minutes, 60);
  assert.equal(d.body.grant.scope, 'read_private');
  const max = await A('sa-malek', 'POST', base(A0.shopId), { ...REQ, minutes: 1440, scope: 'export', ticket_id: 'T-42' });
  assert.equal(max.status, 201, JSON.stringify(max.body));
  assert.equal(max.body.grant.minutes, 1440);
  assert.equal(max.body.grant.ticketId, 'T-42');
  //  تمیز کردن برای آزمون‌های بعد (لغو با دلیل)
  for (const g of [d.body.grant, max.body.grant]) {
    assert.equal((await A('sa-malek', 'POST', `${base(A0.shopId)}/${g.id}/revoke`, { reason: 'پایانِ آزمونِ سنجش' })).status, 200);
  }
});

/* ── ۳) رضایتِ دکان ────────────────────────────────────────────────── */

test('۳) درخواست ⇒ pending و خبرِ صاحب؛ فقط صاحبِ همین دکان می‌پذیرد؛ useGrant فقط همان کس، دکان و دامنه', async () => {
  const before = (await inbox(A0.owner)).length;
  const r = await A('sa-malek', 'POST', base(A0.shopId), REQ, { 'Idempotency-Key': key() });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const g = r.body.grant;
  assert.match(g.id, /^[A-Za-z0-9_-]{1,80}$/);
  assert.equal(g.status, 'pending');
  assert.equal(g.consent, 'tenant');
  assert.equal(g.startsAt, null, 'تا پذیرفتن هیچ دسترسی‌ای نیست');
  assert.equal(g.expiresAt, null);
  assert.ok(g.requestExpiresAt - g.createdAt === 24 * 3600 * 1000);
  const box = await inbox(A0.owner);
  assert.equal(box.length, before + 1, 'خبرِ داخلِ برنامه برای صاحب');
  assert.match(box[0].body, /بررسیِ خطای همگام‌سازی/);
  assert.ok(!(await inbox(B0.owner)).some((n) => /پشتیبانی/.test(n.title)), 'صاحبِ دکانِ دیگر خبری نگرفت');
  assert.equal((await auditsOf('admin.support_access_requested', A0.shopId)).length >= 1, true);
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private').catch((e) => e.code), 'grant_required', 'pending کار نمی‌کند');

  //  صاحب می‌بیند؛ دکانِ دیگر و شاگرد نه
  const list = await O(A0, 'GET', '');
  assert.equal(list.status, 200);
  assert.ok(list.body.grants.some((x) => x.id === g.id && x.requestedBy && x.reason === REQ.reason));
  assert.ok(!(await O(B0, 'GET', '')).body.grants.some((x) => x.id === g.id));
  const st = await h.get('/api/shop/support-access', { token: A0.staff.accessToken });
  assert.equal(st.status, 403);
  assert.equal(st.body.error.code, 'owner_only');
  const before2 = await row(g.id);
  for (const act of ['accept', 'deny', 'revoke']) {
    const x = await O(B0, 'POST', `/${g.id}/${act}`);
    assert.equal(x.status, 404, `صاحبِ «ب» ${act} ⇒ ${x.status}`);
    assert.equal(x.body.error.code, 'grant_not_found');
    assert.equal((await h.post(`/api/shop/support-access/${g.id}/${act}`, {}, { token: A0.staff.accessToken })).status, 403);
  }
  assert.deepEqual(await row(g.id), before2, 'دکانِ دیگر و شاگرد هیچ چیزی را عوض نکردند');

  //  صاحبِ «الف» می‌پذیرد ⇒ از همین دم تا ۳۰ دقیقه
  const t0 = Date.now();
  const ok = await O(A0, 'POST', `/${g.id}/accept`);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.grant.status, 'active');
  assert.ok(ok.body.grant.startsAt >= t0 - 1000 && ok.body.grant.startsAt <= Date.now());
  assert.equal(ok.body.grant.expiresAt - ok.body.grant.startsAt, 30 * 60 * 1000);
  assert.match(ok.body.grant.grantedBy, /^owner:/);
  assert.equal((await auditsOf('shop.support_access_accepted', A0.shopId)).length, 1);
  assert.equal((await O(A0, 'POST', `/${g.id}/accept`)).body.error.code, 'grant_not_pending', 'پذیرفتنِ دوباره نه');

  //  useGrant: فقط همان مدیر، همان دکان، همان دامنه — با شمار و سابقه
  const used = await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private', { what: 'آزمون' });
  assert.equal(used, g.id);
  assert.equal(Number((await row(g.id)).uses), 1);
  const u = (await auditsOf('admin.support_access_used', A0.shopId)).at(-1);
  assert.equal(detail(u).grant, g.id);
  assert.equal(u.user_id, ids['sa-malek']);
  for (const [who, shop, scope] of [[ids['sa-arshad2'], A0.shopId, 'read_private'], [ids['sa-malek'], B0.shopId, 'read_private'],
    [ids['sa-malek'], A0.shopId, 'export']]) {
    assert.equal(await access.useGrant(fakeReq(who), shop, scope).catch((e) => e.code), 'grant_required', `${who} ${shop} ${scope}`);
  }
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek'], { id: 'panel:3', name: 'x' }), A0.shopId, 'read_private')
    .catch((e) => e.code), 'grant_required', 'انجام‌دهندهٔ دیگرِ همان کلید نه');
  assert.equal(Number((await row(g.id)).uses), 1);

  //  لغو از سوی صاحب ⇒ دیگر کار نمی‌کند
  const rv = await O(A0, 'POST', `/${g.id}/revoke`);
  assert.equal(rv.status, 200);
  assert.equal(rv.body.grant.status, 'revoked');
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private').catch((e) => e.code), 'grant_required');
  assert.equal((await O(A0, 'POST', `/${g.id}/revoke`)).body.unchanged, true, 'لغوِ دوباره یک‌باره');

  //  رد: pending ⇒ denied، و پس از آن پذیرفتن نه
  const r2 = await A('sa-malek', 'POST', base(A0.shopId), { ...REQ, reason: 'درخواستِ دوم برای رد' });
  const no = await O(A0, 'POST', `/${r2.body.grant.id}/deny`);
  assert.equal(no.body.grant.status, 'denied');
  assert.equal((await O(A0, 'POST', `/${r2.body.grant.id}/accept`)).body.error.code, 'grant_not_pending');
});

test('۳) پذیرفتن تأییدِ دوبارهٔ ورود می‌خواهد (نشستِ کهنه ⇒ reauth_required)', async () => {
  const r = await A('sa-malek', 'POST', base(B0.shopId), { ...REQ, reason: 'آزمونِ تأییدِ دوباره' });
  assert.equal(r.status, 201);
  //  نشستی که بیش از ده دقیقه پیش ساخته و تازه نشده
  const { hashToken } = require('../src/lib/tokens');
  const hash = hashToken(B0.owner.accessToken);
  await query(`DELETE FROM login_attempts WHERE scope='session_auth' AND identifier=$1`, [hash]);
  const x = await O(B0, 'POST', `/${r.body.grant.id}/accept`);
  assert.equal(x.status, 403, JSON.stringify(x.body));
  assert.equal(x.body.error.code, 'reauth_required');
  assert.equal((await row(r.body.grant.id)).status, 'pending');
  assert.equal((await O(B0, 'POST', `/${r.body.grant.id}/deny`)).status, 200, 'رد کردن تأییدِ دوباره نمی‌خواهد');
});

test('۳) سیلِ درخواست نه: هر دکان تا ۵ درخواستِ بی‌پاسخ؛ ششمی ۴۰۹ و بی خبر؛ لغوِ یکی جا باز می‌کند', async () => {
  const D = await team('د');
  const before = (await inbox(D.owner)).length;
  const made = [];
  for (let i = 0; i < access.PENDING_MAX; i++) {
    const r = await A('sa-malek', 'POST', base(D.shopId), { ...REQ, reason: `درخواستِ شمارهٔ ${i + 1}` });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    made.push(r.body.grant.id);
  }
  const six = await A('sa-malek', 'POST', base(D.shopId), { ...REQ, reason: 'درخواستِ ششم' });
  assert.equal(six.status, 409);
  assert.equal(six.body.error.code, 'too_many_pending');
  assert.equal((await rows(D.shopId)).length, access.PENDING_MAX);
  assert.equal((await inbox(D.owner)).length, before + access.PENDING_MAX, 'ششمی خبری نساخت');
  assert.equal((await A('sa-malek', 'POST', `${base(D.shopId)}/${made[0]}/revoke`, { reason: 'جا باز کن' })).status, 200);
  assert.equal((await A('sa-malek', 'POST', base(D.shopId), { ...REQ, reason: 'پس از لغو' })).status, 201);
});

/* ── ۴) گذشتن و لغو ────────────────────────────────────────────────── */

test('۴) زمانِ سرور: اجازهٔ گذشته کار نمی‌کند و لغوش یک‌باره است؛ درخواستِ ۲۴ساعتهٔ بی‌پاسخ گذشته؛ لغوِ مدیر', async () => {
  const r = await A('sa-malek', 'POST', base(A0.shopId), { ...REQ, reason: 'آزمونِ گذشتن' });
  const g = r.body.grant;
  assert.equal((await O(A0, 'POST', `/${g.id}/accept`)).status, 200);
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private'), g.id);
  //  ساعتِ سرور از پایان گذشت (همان ردیف، ۳۱ دقیقه عقب)
  await query('UPDATE admin_access_grants SET starts_at = starts_at - 1860000, expires_at = expires_at - 1860000 WHERE id=$1', [g.id]);
  const list = await A('sa-binande', 'GET', base(A0.shopId));
  assert.equal(list.body.grants.find((x) => x.id === g.id).status, 'expired');
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private').catch((e) => e.code), 'grant_required');
  const rv = await A('sa-malek', 'POST', `${base(A0.shopId)}/${g.id}/revoke`, { reason: 'پایانِ کار' });
  assert.equal(rv.status, 200);
  assert.equal(rv.body.unchanged, true);
  assert.equal(rv.body.grant.status, 'expired');

  //  درخواستِ بی‌پاسخ پس از ۲۴ ساعت
  const p = await A('sa-malek', 'POST', base(A0.shopId), { ...REQ, reason: 'درخواستِ کهنه' });
  await query('UPDATE admin_access_grants SET created_at = created_at - 86400001 WHERE id=$1', [p.body.grant.id]);
  assert.equal((await O(A0, 'GET', '')).body.grants.find((x) => x.id === p.body.grant.id).status, 'expired');
  assert.equal((await O(A0, 'POST', `/${p.body.grant.id}/accept`)).body.error.code, 'grant_expired');

  //  لغوِ مدیر روی اجازهٔ زنده: دلیل لازم، دکانِ دیگر ۴۰۴، دوباره یک‌باره
  const q = await A('sa-malek', 'POST', base(A0.shopId), { ...REQ, reason: 'آزمونِ لغوِ مدیر' });
  const gid = q.body.grant.id;
  assert.equal((await O(A0, 'POST', `/${gid}/accept`)).status, 200);
  assert.equal((await A('sa-malek', 'POST', `${base(A0.shopId)}/${gid}/revoke`, { reason: 'نه' })).body.error.code, 'reason_required');
  const other = await A('sa-malek', 'POST', `${base(B0.shopId)}/${gid}/revoke`, { reason: 'دکانِ نادرست' });
  assert.equal(other.status, 404);
  assert.equal(other.body.error.code, 'grant_not_found');
  assert.equal((await A('sa-modir', 'POST', `${base(A0.shopId)}/${gid}/revoke`, { reason: 'مدیرِ عادی' })).status, 403);
  const done = await A('sa-arshad2', 'POST', `${base(A0.shopId)}/${gid}/revoke`, { reason: 'کار تمام شد' }, { 'Idempotency-Key': key() });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.grant.status, 'revoked');
  assert.equal(done.body.grant.revokeReason, 'کار تمام شد');
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private').catch((e) => e.code), 'grant_required');
  assert.equal((await A('sa-malek', 'POST', `${base(A0.shopId)}/${gid}/revoke`, { reason: 'دوباره' })).body.unchanged, true);
  const rr = (await auditsOf('admin.support_access_revoked', A0.shopId)).filter((x) => detail(x).grant === gid);
  assert.equal(rr.length, 1, 'یک لغو ⇒ یک سابقه');
  assert.equal(rr[0].user_id, ids['sa-arshad2']);
});

/* ── ۵) شکستنِ شیشه ────────────────────────────────────────────────── */

test('۵) شکستنِ شیشه فقط مالکِ پلتفرم — مدیرِ ارشدِ دیگر ۴۰۳ owner_only پیش از رمز، با سابقه', async () => {
  const r = await A('sa-arshad2', 'POST', `${base(A0.shopId)}/break-glass`, { ...BG, password: 'wrong-password' });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'owner_only');
  assert.equal((await auditsOf('admin.support_access_denied', A0.shopId)).length, 1);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='admin.support_access_reauth_failed'`)).n, 0, 'رمز سنجیده نشد');
  assert.ok(!(await rows(A0.shopId)).some((x) => x.consent === 'break_glass'));
});

test('۵) شکستنِ شیشه: دلیلِ ۱۰ تا ۳۰۰، notify_owner، رضایت و مدت؛ نشستِ کهنه ⇒ رمز؛ رمزِ غلط سابقه دارد', async () => {
  const cases = [
    [{ ...BG, notify_owner: false }, 'notify_owner_required'], [{ ...BG, notify_owner: undefined }, 'notify_owner_required'],
    [{ ...BG, consent: 'tenant' }, 'bad_consent'], [{ ...BG, reason: 'کوتاه است' }, 'reason_required'],
    [{ ...BG, reason: 'د'.repeat(301) }, 'reason_required'], [{ ...BG, minutes: 1441 }, 'bad_minutes'],
  ];
  for (const [body, code] of cases) {
    const x = await A('sa-malek', 'POST', `${base(A0.shopId)}/break-glass`, body);
    assert.equal(x.status, 400, `${JSON.stringify(body)} ⇒ ${x.status}`);
    assert.equal(x.body.error.code, code);
  }
  //  نشستِ مالک کهنه شد (بیش از ده دقیقه پیش، بی ‎/admin/reauth‎)
  const { hashToken } = require('../src/lib/tokens');
  const th = hashToken(await tok('sa-malek'));
  await query(`UPDATE tokens SET issued_at = issued_at - 700000 WHERE token_hash=$1`, [th]);
  await query(`DELETE FROM login_attempts WHERE scope='session_auth' AND identifier=$1`, [th]);
  const none = await A('sa-malek', 'POST', `${base(A0.shopId)}/break-glass`, BG);
  assert.equal(none.status, 403, JSON.stringify(none.body));
  assert.equal(none.body.error.code, 'reauth_required');
  const bad = await A('sa-malek', 'POST', `${base(A0.shopId)}/break-glass`, { ...BG, password: 'not-the-password' });
  assert.equal(bad.status, 403);
  assert.equal(bad.body.error.code, 'reauth_failed');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='admin.support_access_reauth_failed'`)).n, 1);
  assert.ok(!(await rows(A0.shopId)).some((x) => x.consent === 'break_glass'), 'هیچ اجازه‌ای بی احراز');
});

test('۵) شکستنِ شیشه با رمزِ درست: همان دم فعال، خبرِ صاحب و notified=true؛ رمز هیچ‌جا نیست؛ کلیدِ یکتایی یک بار', async () => {
  const before = (await inbox(A0.owner)).length;
  const k = key();
  const body = { ...BG, minutes: 45, password: PASS };
  const r = await A('sa-malek', 'POST', `${base(A0.shopId)}/break-glass`, body, { 'Idempotency-Key': k });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.notified, true);
  const g = r.body.grant;
  assert.equal(g.consent, 'break_glass');
  assert.equal(g.status, 'active');
  assert.equal(g.expiresAt - g.startsAt, 45 * 60 * 1000);
  assert.ok(g.notifiedAt >= g.startsAt);
  const box = await inbox(A0.owner);
  assert.equal(box.length, before + 1);
  assert.match(box[0].title, /بی اجازهٔ شما/);
  assert.match(box[0].body, /گزارشِ تقلب/);
  assert.equal(await access.useGrant(fakeReq(ids['sa-malek']), A0.shopId, 'read_private'), g.id);

  //  همان کلید و همان بدنه ⇒ همان پاسخ، بی اجازه و خبرِ دوم
  const again = await A('sa-malek', 'POST', `${base(A0.shopId)}/break-glass`, body, { 'Idempotency-Key': k });
  assert.equal(again.status, 201);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.grant.id, g.id);
  assert.equal((await rows(A0.shopId)).filter((x) => x.consent === 'break_glass').length, 1);
  assert.equal((await inbox(A0.owner)).length, before + 1);

  const bg = await auditsOf('admin.support_access_break_glass', A0.shopId);
  assert.equal(bg.length, 1);
  assert.equal(detail(bg[0]).reauth, 'password');
  assert.equal(detail(bg[0]).reason, BG.reason);
  const nt = await auditsOf('admin.support_access_owner_notified', A0.shopId);
  assert.equal(detail(nt.at(-1)).notified, true);
  //  ⛔ رمز در هیچ ردیف، سابقه یا پاسخِ ذخیره‌شدهٔ کلیدی نیست
  for (const t of ['audit_logs', 'admin_access_grants', 'idempotency_keys', 'admin_write_claims', 'notice_deliveries', 'notices']) {
    const n = (await one(`SELECT COUNT(*)::int AS n FROM ${t} x WHERE x::text LIKE $1`, [`%${PASS}%`])).n;
    assert.equal(n, 0, `رمز در ${t}`);
  }

  //  صاحب نمی‌تواند «شکستنِ شیشه» را خودش ببندد (بی رضایتِ او باز شده)؛ مدیرِ ارشد می‌بندد
  const own = await O(A0, 'POST', `/${g.id}/revoke`);
  assert.equal(own.status, 409);
  assert.equal(own.body.error.code, 'break_glass_owner');
  assert.equal((await A('sa-malek', 'POST', `${base(A0.shopId)}/${g.id}/revoke`, { reason: 'پایانِ بررسی' })).body.grant.status, 'revoked');
});

test('۵) شکستنِ شیشه بی صاحبِ فعال ⇒ notified=false (اجازه می‌ماند و رابط «خبر تأیید نشد» می‌گوید)', async () => {
  const C = await team('ج');
  await query(`UPDATE shop_members SET status='suspended' WHERE shop_id=$1 AND role='owner'`, [C.shopId]);
  const r = await A('sa-malek', 'POST', `${base(C.shopId)}/break-glass`, { ...BG, password: PASS });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.notified, false);
  assert.equal(r.body.grant.notifiedAt, null);
  assert.equal(detail((await auditsOf('admin.support_access_owner_notified', C.shopId))[0]).notified, false);
});

test('۵) پلِ پنل: کارِ خودکارِ پنل نه؛ اپراتور نه؛ ارشدِ پنل می‌شکند (مالکِ پنل را پنل سنجیده)؛ X-Actor-Reauth-At ِ کهنه نه', async () => {
  const sys = await S('panel:system', 'superadmin', 'POST', `${base(B0.shopId)}/break-glass`, BG);
  assert.equal(sys.status, 403);
  assert.equal(sys.body.error.code, 'human_actor_required');
  const sysReq = await S('panel:system', 'superadmin', 'POST', base(B0.shopId), REQ);
  assert.equal(sysReq.body.error.code, 'human_actor_required');
  assert.equal((await S('panel:4', 'admin', 'POST', `${base(B0.shopId)}/break-glass`, BG)).status, 403);
  const stale = await S('panel:1', 'superadmin', 'POST', `${base(B0.shopId)}/break-glass`, BG, { 'X-Actor-Reauth-At': String(Date.now() - 3_600_000) });
  assert.equal(stale.status, 403);
  assert.equal(stale.body.error.code, 'reauth_required');
  assert.equal((await rows(B0.shopId)).filter((x) => x.consent === 'break_glass').length, 0);

  const ok = await S('panel:1', 'superadmin', 'POST', `${base(B0.shopId)}/break-glass`, BG, { 'X-Actor-Name': encodeURIComponent('صاحبِ پنل') });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.notified, true);
  assert.equal(ok.body.grant.requestedBy, 'صاحبِ پنل');
  const bg = (await auditsOf('admin.support_access_break_glass', B0.shopId)).at(-1);
  assert.equal(detail(bg).reauth, 'panel');
  assert.equal(detail(bg).actor.id, 'panel:1');
  //  اجازه مالِ همان انجام‌دهنده است
  const svcId = (await row(ok.body.grant.id)).admin_id;
  assert.equal(await access.useGrant(fakeReq(svcId, { id: 'panel:1', name: 'x' }), B0.shopId, 'read_private'), ok.body.grant.id);
  assert.equal(await access.useGrant(fakeReq(svcId, { id: 'panel:2', name: 'y' }), B0.shopId, 'read_private').catch((e) => e.code), 'grant_required');
  //  لغو از پل
  const rv = await S('panel:1', 'superadmin', 'POST', `${base(B0.shopId)}/${ok.body.grant.id}/revoke`, { reason: 'پایانِ کارِ پنل' });
  assert.equal(rv.status, 200, JSON.stringify(rv.body));
  assert.equal(rv.body.grant.status, 'revoked');
});

/* ── ۶) هرگز همیشگی؛ جداسازی؛ سابقه ───────────────────────────────── */

test('۶) قیدهای پایگاه: بیش از ۲۴ ساعت، مدتِ بیرون از ۵..۱۴۴۰، فعالِ بی پایان و دکانِ دیگرِ برنامه رد می‌شوند', async () => {
  const t = Date.now();
  const ins = (over) => query(
    `INSERT INTO admin_access_grants (id, app, tenant_id, admin_id, scope, consent, status, reason, minutes, created_at, starts_at, expires_at, revoked_at)
     VALUES ($1,$2,$3,'adm_x','read_private','tenant',$4,'دلیلِ آزمون',$5,$6,$7,$8,$9)`,
    [newId('agr'), over.app ?? 'shop', A0.shopId, over.status ?? 'active', over.minutes ?? 60, t, over.starts ?? t,
      over.expires === undefined ? t + 3_600_000 : over.expires, over.revoked ?? null]);
  for (const [what, over] of [['۲۴ ساعت و یک', { expires: t + 86_400_001 }], ['۱۴۴۱ دقیقه', { minutes: 1441 }], ['۴ دقیقه', { minutes: 4 }],
    ['فعالِ بی پایان', { starts: null, expires: null }], ['پایان پیش از آغاز', { expires: t - 1 }], ['بخشِ پمپ', { app: 'pump' }],
    ['لغوشدهٔ بی زمان', { status: 'revoked' }]]) {
    await assert.rejects(ins(over), `${what} باید رد شود`);
  }
  await ins({});   //  شاهد: ردیفِ درست می‌نشیند
  await query(`DELETE FROM admin_access_grants WHERE admin_id='adm_x'`);
});

test('۶) جداسازی: فهرستِ مدیر و صاحب هر کدام فقط اجازه‌های همان دکان؛ سابقه‌ها با شناسهٔ همان دکان', async () => {
  const a = (await A('sa-malek', 'GET', base(A0.shopId))).body.grants.map((x) => x.id);
  const b = (await A('sa-malek', 'GET', base(B0.shopId))).body.grants.map((x) => x.id);
  assert.ok(a.length > 0 && b.length > 0);
  assert.equal(a.filter((x) => b.includes(x)).length, 0);
  assert.deepEqual(a.sort(), (await rows(A0.shopId)).map((x) => x.id).sort());
  assert.deepEqual((await O(A0, 'GET', '')).body.grants.map((x) => x.id).sort(), a.sort());
  for (const g of (await A('sa-malek', 'GET', base(A0.shopId))).body.grants) {
    assert.deepEqual(Object.keys(g).filter((k) => /admin_id|actor_id|password|token/i.test(k)), [], 'فقط فراداده');
  }
  const acts = await many(`SELECT action, COUNT(*)::int AS n FROM audit_logs WHERE action LIKE '%support_access%' AND shop_id=$1 GROUP BY action`, [A0.shopId]);
  const by = Object.fromEntries(acts.map((x) => [x.action, x.n]));
  for (const k of ['admin.support_access_requested', 'admin.support_access_revoked', 'admin.support_access_break_glass', 'admin.support_access_used',
    'admin.support_access_owner_notified', 'admin.support_access_denied', 'shop.support_access_accepted', 'shop.support_access_denied',
    'shop.support_access_revoked']) {
    assert.ok(by[k] > 0, `سابقهٔ ${k}`);
  }
});

test('۶) سقفِ IP پیش از احراز: ۶۰ در دقیقه روی درهای مترادف و دسترسیِ پشتیبانی — شصت‌ویکمیِ بی‌توکن ۴۲۹ است، نه ۴۰۱', async () => {
  for (const path of ['/api/admin/search/synonyms', base(A0.shopId)]) {
    resetAll();
    for (let i = 0; i < 60; i++) assert.equal((await h.get(path)).status, 401, `${path} #${i + 1}`);
    assert.equal((await h.get(path)).status, 429, `${path} ⇒ سقف پیش از احراز`);
  }
  resetAll();
});
