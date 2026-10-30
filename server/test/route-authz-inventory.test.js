'use strict';
/**
 * SEC-AUTH-10 / SEC-AUTH-11 (§۷.۵ و §۱۳ِ برنامهٔ امنیت): «deny-by-default» روی **همهٔ** درهای نوشتنیِ `/api`.
 *
 * فهرست از **روترِ زنده** خوانده می‌شود (`route-inventory.live`، همان که E2E-SEC-13 می‌خواند)، نه از فهرستِ
 * دستی. هر درِ POST/PUT/PATCH/DELETE باید دقیقاً یکی از این‌ها باشد؛ درِ تازه‌ای که هیچ‌کدام نباشد آزمون را سرخ
 * می‌کند تا کسی آگاهانه دربارهٔ احراز و مجوزش تصمیم بگیرد:
 *
 *   PUBLIC   — عمداً بی‌توکن (ورود، ثبت‌نام، کدِ یک‌بارمصرف، گزارشِ خطا…)، هر کدام با دلیل
 *   CUSTOMER — درِ مشتریِ فروشگاهِ آنلاین (نشستِ جدای `X-Store-Session`)؛ بیرون از این برنامه تا ساختنش،
 *              جداسازی‌اش در `role-route-isolation.test.js` (E2E-SEC-13)
 *   SELF     — با توکن، ولی فقط روی **هویتِ خودِ** صدا زننده (رمز، دستگاه‌های خودش، ساختنِ دکانِ خودش…)
 *   BOUND    — احراز + میان‌افزاری که حساب/دکان/پمپ را **از توکن** می‌سازد (`requireShop`، `requireStation`،
 *              `requireSyncAccount`، `requireDevice`، `requireAdmin`)؛ جداسازیِ شیء‌به‌شیء در
 *              `shop-route-inventory.test.js`، `pump-isolation.test.js` و E2E-SEC-13
 *
 * و پویا: هر درِ غیرِ عمومی بی‌توکن ۴۰۱ است؛ توکنِ مدیر روی هیچ درِ غیرِ مدیر کار نمی‌کند (نشستِ مدیر جداست)؛
 * درِ عمومی با بدنهٔ خالی ۵xx نمی‌دهد. SEC-AUTH-11: فیلدهای ممتاز در بدنه (نقش، isAdmin، shopId، ownerId،
 * accountId، userId) نادیده یا رد می‌شوند.
 */
process.env.SYNC_V3_ENABLED = '1';
const inv = require('./route-inventory');
inv.tapMounts();
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const WRITE = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const AUTHN = ['requireUser', 'requireAnyUser', 'requireAppUser', 'requireAdmin', 'requireSyncAccount', 'requireDevice'];
const BINDER = ['requireShop', 'requireStation', 'requireSyncAccount', 'requireDevice', 'requireAdmin'];

const PUBLIC = [
  ['ورود، ثبت‌نام و بازیابی — خودشان راهِ گرفتنِ توکن‌اند؛ سقفِ نرخ، قفلِ تلاش و کدِ یک‌بارمصرف (login-hardening، sec-auth)', [
    'POST /api/auth/staff', 'POST /api/auth/staff-login',
    'POST /api/auth/register', 'POST /api/auth/register/start', 'POST /api/auth/register/verify', 'POST /api/auth/register/complete',
    'POST /api/auth/login', 'POST /api/auth/otp/request', 'POST /api/auth/otp/verify',
    'POST /api/auth/google', 'POST /api/auth/google/link-code',
    'POST /api/auth/password/forgot', 'POST /api/auth/password/reset',
    'POST /api/auth/:app/request-code', 'POST /api/auth/:app/verify',
    'POST /api/admin/login']],
  ['نشست با خودِ توکنِ تازه‌سازی سنجیده می‌شود (چرخش و ردِ بازپخش: login-contract)', [
    'POST /api/auth/refresh', 'POST /api/auth/logout']],
  ['گزارشِ خطا، تپشِ بازدید و موقعیتِ یک‌باره — بی دادهٔ حساب، با سقفِ نرخ', [
    'POST /api/errors', 'POST /api/location', 'POST /api/visit']],
  ['پشتیبانیِ مهمانِ بی‌حساب: رشته از توکن یا شناسهٔ دستگاه، نه از شناسهٔ رشته (routes/support.js)', [
    'POST /api/support/messages', 'POST /api/support/read', 'POST /api/support/push', 'DELETE /api/support/push']],
  ['پمپ: فعال‌سازیِ کامپیوتر با کدِ پمپ، و چتِ مشتریِ کیوآر با کدِ پمپ + شناسهٔ حساب (pump-device، pump-public)', [
    'POST /api/pump/device/activate', 'POST /api/pump/public/join',
    'POST /api/pump/public/:code/acct/:id/chat', 'POST /api/pump/public/:code/acct/:id/chat/media',
    'DELETE /api/pump/public/:code/acct/:id/chat/:msg', 'POST /api/pump/public/:code/acct/:id/chat/seen',
    'POST /api/pump/public/:code/acct/:id/chat/push']],
];
const CUSTOMER = /^(POST|PUT|PATCH|DELETE) \/api\/(store|market)\//;
const SELF = [
  ['هویتِ خودِ صدا زننده: رمز، تأییدِ دوباره و خروج از همه (requireAnyUser)', [
    'POST /api/auth/logout-all', 'POST /api/auth/reauth/code', 'POST /api/auth/reauth', 'POST /api/auth/password']],
  ['صفحهٔ «من» و پرتال: فقط ردیف‌های همین کاربر (دستگاهِ دیگری ⇒ S15، رمزِ دوباره ⇒ reauth)', [
    'POST /api/me/notices/:id/read', 'PUT /api/me/settings', 'POST /api/me/redeem', 'POST /api/me/errors',
    'POST /api/me/purchase-request', 'DELETE /api/me/devices/:id', 'PUT /api/me', 'POST /api/me/push', 'DELETE /api/me/push',
    'POST /api/me/backups', 'DELETE /api/me/backups/:id',
    'DELETE /api/portal/devices/:id', 'POST /api/portal/support/messages', 'POST /api/portal/notices/:id/read',
    'PUT /api/portal/settings', 'POST /api/portal/redeem', 'POST /api/portal/errors',
    'POST /api/license/sync', 'POST /api/billing/request']],
  ['ساختن یا پیوستن به دکان/پمپ: صاحبِ تازه خودِ صدا زننده است؛ پیوستن فقط با کد؛ دکانِ فعال فقط از عضویت‌های خودش', [
    'POST /api/shop', 'POST /api/shop/create', 'POST /api/shop/active', 'POST /api/shop/staff/join', 'POST /api/shop/join',
    'POST /api/pump', 'POST /api/pump/claim', 'POST /api/pump/device/bind']],
];
const PUBLIC_KEYS = new Set(PUBLIC.flatMap(([, k]) => k));
const SELF_KEYS = new Set(SELF.flatMap(([, k]) => k));

let routes = [];
let admin = '';
test.before(async () => {
  await h.start();
  routes = inv.live(h.server().listeners('request')[0]).filter((r) => WRITE.has(r.method));
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'arshad','ارشد',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'arshad', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

const call = (...a) => { resetAll(); return h.api(...a); };
const url = (r) => r.path.replace(/:([A-Za-z_]+)/g, 'x1');
const kind = (r) => (PUBLIC_KEYS.has(r.key) ? 'PUBLIC' : CUSTOMER.test(r.key) ? 'CUSTOMER' : SELF_KEYS.has(r.key) ? 'SELF' : 'BOUND');

test('SEC-AUTH-10: هر درِ نوشتنیِ /api دسته‌ای روشن دارد؛ SELF احراز دارد و BOUND حساب را از توکن می‌سازد', () => {
  assert.ok(routes.length > 300, `فقط ${routes.length} درِ نوشتنی — فهرستِ زنده خوانده نشد؟`);
  const keys = new Set(routes.map((r) => r.key));
  const stale = [...PUBLIC_KEYS, ...SELF_KEYS].filter((k) => !keys.has(k));
  assert.deepEqual(stale, [], `در فهرست هست ولی در سورس نیست:\n${stale.join('\n')}`);
  const bad = [];
  for (const r of routes) {
    const names = r.chain.filter(Boolean);
    const k = kind(r);
    if (k === 'SELF' && !names.some((n) => AUTHN.includes(n))) bad.push(`${r.key}: SELF بی احراز [${names}]`);
    if (k === 'BOUND' && !(names.some((n) => AUTHN.includes(n)) && names.some((n) => BINDER.includes(n)))) {
      bad.push(`${r.key}: نه عمومی، نه SELF، و زنجیره‌اش احراز + حسابِ توکن ندارد [${names}]`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

test('SEC-AUTH-10: بی‌توکن هر درِ غیرِ عمومی ۴۰۱؛ درِ عمومی با بدنهٔ خالی ۵xx نمی‌دهد', async () => {
  const bad = [];
  for (const r of routes) {
    const k = kind(r);
    if (k === 'CUSTOMER') continue;
    const res = await call(r.method, url(r), { body: {} });
    if (k === 'PUBLIC') { if (res.status >= 500) bad.push(`${r.key} ⇒ ${res.status}`); continue; }
    if (res.status !== 401) bad.push(`${r.key} بی‌توکن ⇒ ${res.status} ${res.body?.error?.code || ''}`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

test('SEC-AUTH-10: توکنِ مدیر روی هیچ درِ نوشتنیِ غیرِ مدیر کار نمی‌کند — نشستِ مدیر از نشستِ دکان/پمپ جداست', async () => {
  const bad = [];
  for (const r of routes) {
    const k = kind(r);
    if (k === 'PUBLIC' || k === 'CUSTOMER' || r.path.startsWith('/api/admin/')) continue;
    const res = await call(r.method, url(r), { body: {}, token: admin });
    if (res.status !== 401) bad.push(`${r.key} با توکنِ مدیر ⇒ ${res.status} ${res.body?.error?.code || ''}`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

/* ── SEC-AUTH-11: mass assignment ─────────────────────────────────── */

test('SEC-AUTH-11: نقش، isAdmin، ownerId، shopId، accountId و userId در بدنه هیچ دسترسی‌ای نمی‌سازند', async () => {
  const a = await h.newUser('صاحبِ الف');
  const b = await h.newUser('صاحبِ ب');
  const sb = (await h.post('/api/shop', { name: 'دکانِ ب' }, { token: b.accessToken })).body.shop.id;
  //  ۱) ساختنِ دکان: صاحب همیشه خودِ صدا زننده است
  const made = await h.post('/api/shop', {
    name: 'دکانِ الف', ownerId: b.user.id, owner_id: b.user.id, userId: b.user.id, id: sb, shopId: sb,
    role: 'owner', max_members: 999, maxMembers: 999, status: 'active', plan: 'pro',
  }, { token: a.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const sa = made.body.shop.id;
  assert.notEqual(sa, sb);
  const rows = await many('SELECT user_id, role FROM shop_members WHERE shop_id=$1', [sa]);
  assert.deepEqual(rows, [{ user_id: a.user.id, role: 'owner' }]);
  assert.equal((await one('SELECT max_members FROM shops WHERE id=$1', [sa])).max_members, 10);
  assert.equal((await many('SELECT 1 FROM shop_members WHERE shop_id=$1 AND user_id=$2', [sb, a.user.id])).length, 0);

  //  ۲) «من»: نوع، وضعیت و ایمیلِ حساب از بدنه عوض نمی‌شوند
  const meBefore = await one('SELECT kind, status, email FROM users WHERE id=$1', [a.user.id]);
  const me = await h.put('/api/me', {
    name: 'الف', id: b.user.id, userId: b.user.id, role: 'admin', isAdmin: true, is_admin: true, kind: 'admin',
    status: 'disabled', email: 'evil@test.local', accountId: sb,
  }, { token: a.accessToken });
  assert.ok(me.status < 300, JSON.stringify(me.body));
  assert.deepEqual(await one('SELECT kind, status, email FROM users WHERE id=$1', [a.user.id]), meBefore);
  assert.equal((await one('SELECT name FROM users WHERE id=$1', [b.user.id])).name, 'صاحبِ ب');

  //  ۳) نقشِ عضو: «صاحب» از هیچ دری ساخته نمی‌شود؛ شناسهٔ دکان/کاربرِ بدنه نادیده است
  const s = await h.newUser('شاگردِ الف');
  const mem = newId('mem');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'staff','active',$4,$4)`, [mem, sa, s.user.id, now()]);
  const toOwner = await h.patch(`/api/shop/members/${mem}`, { role: 'owner' }, { token: a.accessToken });
  assert.equal(toOwner.status, 400, JSON.stringify(toOwner.body));
  const moved = await h.patch(`/api/shop/members/${mem}`, { role: 'manager', shopId: sb, shop_id: sb, userId: b.user.id }, { token: a.accessToken });
  assert.equal(moved.status, 200, JSON.stringify(moved.body));
  assert.deepEqual(await one('SELECT shop_id, user_id, role FROM shop_members WHERE id=$1', [mem]), { shop_id: sa, user_id: s.user.id, role: 'manager' });
  //  مدیرِ تازه نقش‌ها را عوض نمی‌کند (members.role فقط صاحب)
  const sTok = (await h.signIn(s)).accessToken;
  assert.equal((await h.patch(`/api/shop/members/${mem}`, { role: 'manager' }, { token: sTok })).status, 403);
  for (const role of ['owner', 'admin', 'superadmin']) {
    const inv2 = await h.post('/api/shop/invite', { role }, { token: a.accessToken });
    assert.equal(inv2.status, 400, `${role}: ${JSON.stringify(inv2.body)}`);
  }

  //  ۴) مدیرِ پنل: نقشِ ناشناخته رد می‌شود
  const made2 = await h.post('/api/admin/admins', { username: 'clerk2', password: 'Clerk!12345', role: 'owner' }, { token: admin });
  assert.equal(made2.status, 400, JSON.stringify(made2.body));

  //  ۵) پمپ: صاحبِ پمپِ تازه همان صدا زننده است
  const p = await h.newUser('صاحبِ پمپ', 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ الف', ownerId: b.user.id, owner_user_id: b.user.id, stationId: 'x' }, { token: p.accessToken });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  assert.deepEqual(await many('SELECT user_id, role FROM station_members WHERE station_id=$1', [st.body.station.id]), [{ user_id: p.user.id, role: 'owner' }]);
});
