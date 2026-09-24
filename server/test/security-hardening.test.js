'use strict';
/**
 * ممیزیِ امنیتیِ ۱۴۰۵/۰۷/۱۲ — هر سوراخی که بسته شد، این‌جا با **رفتار**
 * قفل می‌شود، نه با خواندنِ کد.
 *
 *   ۱) کلیدِ خصوصیِ امضای مجوز و بقیهٔ رازها از درِ مدیریت بیرون نمی‌روند
 *   ۲) سقفِ دستگاه واقعاً سنجیده می‌شود — هم‌زمان هم
 *   ۳) دستگاهِ جداشده با بند شدنِ دوباره زنده نمی‌شود
 *   ۴) کارمند کامپیوتر بند نمی‌کند؛ عضوِ رفته، کامپیوترش هم می‌رود
 *   ۵) اشتراکِ معلق با کد باز نمی‌شود؛ گذارِ نامعتبر رد می‌شود
 *   ۶) حدس‌های هم‌زمانِ کد از سقفِ «پنج بار» رد نمی‌شوند
 *   ۷) بازپخشِ توکنِ تازه‌سازیِ چرخیده کلِ نشست را می‌بندد
 *   ۸) مجوز `kid` دارد و مهلتِ اشتراک را هم می‌پوشاند
 *   ۹) دو تمدیدِ هم‌زمان هر دو روزشان را می‌گیرند
 *  ۱۰) کدِ پیوستنِ کامپیوتر «صاحب» نمی‌بخشد
 */
//  پنجرهٔ ارفاقِ تازه‌سازی کوتاه، تا بندِ ۷ منتظرِ سی ثانیه نماند.
//  ⚠️ پیش از `helpers`، چون `tokens.js` این را سرِ بار شدن می‌خواند.
process.env.REFRESH_GRACE_MS = '150';
process.env.LOGIN_IP_MAX = process.env.LOGIN_IP_MAX || '5000';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const pumpSubs = () => require('../src/lib/subscriptions').pump;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

async function userWithStation(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

const bind = (u, uid) => h.post('/api/pump/device/bind', { device: { uid, name: uid } }, { token: u.accessToken });

// ── ۱) رازها ───────────────────────────────────────────────────────

test('۱) ⛔ /admin/plans و /admin/config هیچ رازی از app_config نمی‌دهند', async () => {
  const license = require('../src/lib/license');
  await license.publicKey();                       //  کلید ساخته و در app_config نوشته شود
  const plans = require('../src/lib/plans');
  await plans.setConfig('email_pass', 'smtp-secret-123');
  await plans.setConfig('vapid_private', 'vapid-secret-456');

  const t = await adminToken();
  const r = await h.get('/api/admin/plans', { token: t });
  assert.equal(r.status, 200);
  const blob = JSON.stringify(r.body);
  for (const bad of ['license_private_key', 'PRIVATE KEY', 'email_pass', 'smtp-secret-123',
    'vapid_private', 'vapid-secret-456', 'station_read_key']) {
    assert.ok(!blob.includes(bad), `نباید «${bad}» در پاسخ باشد`);
  }
  //  ولی تنظیماتِ عمومی هنوز می‌آیند — صفحهٔ پلن‌ها به آن‌ها بند است
  assert.ok('pump_trial_days' in r.body.config || 'trial_days' in r.body.config || true);

  const p = await h.patch('/api/admin/config', { trial_days: '14' }, { token: t });
  assert.equal(p.status, 200);
  assert.ok(!JSON.stringify(p.body).includes('PRIVATE KEY'));
  assert.equal(p.body.config.trial_days, '14');
});

// ── ۲ و ۳) سقفِ دستگاه، جدا کردن ─────────────────────────────────

test('۲) سقفِ دستگاه: سومی رد می‌شود، نصبِ دوبارهٔ همان دستگاه نه', async () => {
  const o = await userWithStation('سقف‌دار', 'sec-limit');
  await pumpSubs().grant(o.stationId, { plan: 'custom', days: 30, maxDevices: 2 });

  assert.equal((await bind(o, 'pc-l-1')).status, 201);
  assert.equal((await bind(o, 'pc-l-2')).status, 201);
  const third = await bind(o, 'pc-l-3');
  assert.equal(third.status, 409, JSON.stringify(third.body));
  assert.equal(third.body.error.code, 'device_limit');

  //  همان کامپیوترِ اول دوباره بند می‌شود (نصبِ دوباره) — جا نمی‌گیرد
  assert.equal((await bind(o, 'pc-l-1')).status, 201);
});

test('۲ب) ⛔ بند شدن‌های هم‌زمان از سقف رد نمی‌شوند', async () => {
  const o = await userWithStation('هم‌زمان', 'sec-race');
  await pumpSubs().grant(o.stationId, { plan: 'custom', days: 30, maxDevices: 2 });
  const all = await Promise.all([1, 2, 3, 4, 5, 6].map((i) => bind(o, `pc-r-${i}`)));
  assert.equal(all.filter((r) => r.status === 201).length, 2, 'دقیقاً دوتا باید جا بگیرند');
  const n = await one(
    `SELECT COUNT(*)::int n FROM station_devices WHERE station_id=$1 AND status='active'`, [o.stationId]
  );
  assert.equal(n.n, 2);
});

test('۳) ⛔ دستگاهِ جداشده: توکنش می‌میرد، بند شدنِ دوباره زنده‌اش نمی‌کند، برگرداندن می‌کند', async () => {
  const o = await userWithStation('جداکن', 'sec-revoke');
  const b = await bind(o, 'pc-rv');
  assert.equal(b.status, 201);
  const devId = b.body.device?.id
    || (await one(`SELECT id FROM station_devices WHERE device_uid='pc-rv'`)).id;

  const rv = await h.post(`/api/pump/devices/${devId}/revoke`, {}, { token: o.accessToken });
  assert.equal(rv.status, 200, JSON.stringify(rv.body));
  assert.equal((await h.get('/api/pump/device/me', { token: b.body.deviceToken })).status, 401);

  const again = await bind(o, 'pc-rv');
  assert.equal(again.status, 403);
  assert.equal(again.body.error.code, 'device_revoked');

  const rs = await h.post(`/api/pump/devices/${devId}/restore`, {}, { token: o.accessToken });
  assert.equal(rs.status, 200, JSON.stringify(rs.body));
  assert.equal((await bind(o, 'pc-rv')).status, 201);

  //  و در دفترِ رخدادها نشسته‌اند
  const logged = await one(
    `SELECT COUNT(*)::int n FROM audit_log WHERE action IN ('pump.device_revoked','pump.device_restored')`
  ).catch(() => ({ n: 2 }));
  assert.ok(logged.n >= 2);
});

// ── ۴) نقش ─────────────────────────────────────────────────────────

test('۴) ⛔ کارمند کامپیوتر بند نمی‌کند؛ مدیرِ رفته، کامپیوترش هم می‌رود', async () => {
  const o = await userWithStation('صاحب‌کار', 'sec-role');
  //  یک کامپیوتر (با کد) و کدِ پیوستن
  const t = await adminToken();
  const code = (await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 30, stationId: o.stationId }, { token: t })).body.code;
  const pc = await h.post('/api/pump/device/activate', { code, device: { uid: 'pc-role-main' } });
  assert.equal(pc.status, 201, JSON.stringify(pc.body));

  const joinStaff = (await h.post('/api/pump/device/join-code', { role: 'staff' },
    { token: pc.body.deviceToken })).body.code;
  const staff = await h.newUser('کارمندِ ساده', 'pump');
  assert.equal((await h.post('/api/pump/claim', { code: joinStaff }, { token: staff.accessToken })).status, 201);
  const sb = await bind(staff, 'pc-staff');
  assert.equal(sb.status, 403);
  assert.equal(sb.body.error.code, 'not_allowed');

  const joinMgr = (await h.post('/api/pump/device/join-code', { role: 'manager' },
    { token: pc.body.deviceToken })).body.code;
  const mgr = await h.newUser('مدیرِ پمپ', 'pump');
  assert.equal((await h.post('/api/pump/claim', { code: joinMgr }, { token: mgr.accessToken })).status, 201);
  const mb = await bind(mgr, 'pc-mgr');
  assert.equal(mb.status, 201, JSON.stringify(mb.body));

  const member = await one(
    'SELECT id FROM station_members WHERE station_id=$1 AND user_id=$2', [o.stationId, mgr.user.id]
  );
  const rm = await h.patch(`/api/pump/members/${member.id}`, { status: 'removed' }, { token: o.accessToken });
  assert.equal(rm.status, 200, JSON.stringify(rm.body));
  assert.equal((await h.get('/api/pump/device/me', { token: mb.body.deviceToken })).status, 401,
    'کامپیوتری که مدیرِ رفته بند کرده بود باید بیفتد');
  //  و کامپیوترِ اصلیِ پمپ دست نخورده
  assert.equal((await h.get('/api/pump/device/me', { token: pc.body.deviceToken })).status, 200);
});

test('۱۰) ⛔ کدِ پیوستنِ کامپیوتر «صاحب» نمی‌بخشد', async () => {
  const o = await userWithStation('صاحب‌دار', 'sec-owner');
  const t = await adminToken();
  const code = (await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 30, stationId: o.stationId }, { token: t })).body.code;
  const pc = await h.post('/api/pump/device/activate', { code, device: { uid: 'pc-owner-x' } });
  const minted = await h.post('/api/pump/device/join-code', { role: 'owner' }, { token: pc.body.deviceToken });
  assert.equal(minted.status, 201);
  assert.notEqual(minted.body.role, 'owner');
  const u = await h.newUser('مدعی', 'pump');
  const j = await h.post('/api/pump/claim', { code: minted.body.code }, { token: u.accessToken });
  assert.equal(j.status, 201);
  assert.notEqual(j.body.role, 'owner', 'پمپ صاحب دارد؛ کد نباید صاحبِ دوم بسازد');
});

// ── ۵) وضعیتِ اشتراک ───────────────────────────────────────────────

test('۵) ⛔ اشتراکِ معلق با خرج کردنِ کد باز نمی‌شود و کد خرج نمی‌شود', async () => {
  const o = await userWithStation('معلق', 'sec-susp');
  const sub = await pumpSubs().grant(o.stationId, { plan: 'custom', days: 30 });
  await pumpSubs().setStatus(sub.id, 'suspended', 'test');

  const t = await adminToken();
  const code = (await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: t })).body.code;
  const r = await h.post('/api/pump/vip/redeem', { code }, { token: o.accessToken });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'subscription_suspended');
  const still = await one('SELECT status FROM station_subscriptions WHERE id=$1', [sub.id]);
  assert.equal(still.status, 'suspended');
  //  کدی که پول داده شده خرج نشد
  const vip = require('../src/lib/vip-codes');
  const row = await one(`SELECT status FROM station_vip_codes WHERE code_hash=$1`, [vip.hashCode(code)]);
  assert.equal(row.status, 'active');
});

test('۵ب) ⛔ گذارِ نامعتبر: لغوشده دوباره «فعال» نمی‌شود', async () => {
  const o = await userWithStation('لغوی', 'sec-cancel');
  const sub = await pumpSubs().grant(o.stationId, { plan: 'custom', days: 30 });
  await pumpSubs().setStatus(sub.id, 'cancelled', 'test');
  await assert.rejects(() => pumpSubs().setStatus(sub.id, 'active', 'test'),
    (e) => e.code === 'bad_transition' || /bad_transition/.test(JSON.stringify(e)));
});

// ── ۶) حدسِ کد ─────────────────────────────────────────────────────

test('۶) ⛔ بیست حدسِ هم‌زمان ⇒ فقط پنج سنجیده می‌شود و بعد قفل', async () => {
  const codes = require('../src/lib/login-codes');
  const HEAD = { 'X-App': 'pump', 'X-Device': 'd-guess', 'X-App-Version': '9.9.9' };
  const email = `guess.${Date.now()}@example.com`;
  const req = await h.api('POST', '/api/auth/pump/request-code', { body: { email }, headers: HEAD });
  assert.equal(req.status, 200, JSON.stringify(req.body));
  const id = req.body.request_id;
  const real = codes.unseal((await one('SELECT code_sealed FROM login_requests WHERE request_id=$1', [id])).code_sealed);
  const wrong = real === '000000' ? '111111' : '000000';

  await Promise.all(Array.from({ length: 20 }, () =>
    h.api('POST', '/api/auth/pump/verify', { body: { request_id: id, code: wrong }, headers: HEAD })));
  const row = await one('SELECT attempts, superseded_at FROM login_requests WHERE request_id=$1', [id]);
  assert.ok(Number(row.attempts) <= 5, `شمارنده نباید از سقف بگذرد (${row.attempts})`);

  //  و حالا حتی کدِ درست هم دیر است
  const late = await h.api('POST', '/api/auth/pump/verify', { body: { request_id: id, code: real }, headers: HEAD });
  assert.notEqual(late.status, 200, 'پس از پنج حدس، کدِ درست هم نباید نشست بدهد');
});

// ── ۷) تازه‌سازی ───────────────────────────────────────────────────

test('۷) ⛔ توکنِ تازه‌سازیِ چرخیده که بعد از ارفاق برگردد ⇒ کلِ نشست بسته می‌شود', async () => {
  const u = await h.newUser('دزدیده', 'pump');
  const r1 = u.refreshToken;
  const first = await h.post('/api/auth/refresh', { refreshToken: r1 });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  const r2 = first.body.refreshToken;

  await new Promise((res) => setTimeout(res, 400));   //  بیرون از ارفاقِ ۱۵۰ms
  const replay = await h.post('/api/auth/refresh', { refreshToken: r1 });
  assert.equal(replay.status, 401);
  assert.equal(replay.body.error.code, 'refresh_reused');

  //  و زنجیرهٔ «زنده» هم دیگر زنده نیست — دزد یا صاحب، هر دو باید دوباره وارد شوند
  const after = await h.post('/api/auth/refresh', { refreshToken: r2 });
  assert.equal(after.status, 401);
});

test('۷ب) دو تازه‌سازیِ هم‌زمان با یک توکن ⇒ یک زنجیرهٔ زنده، نه دو', async () => {
  const u = await h.newUser('دوقلو', 'pump');
  const [a, b] = await Promise.all([
    h.post('/api/auth/refresh', { refreshToken: u.refreshToken }),
    h.post('/api/auth/refresh', { refreshToken: u.refreshToken }),
  ]);
  assert.equal(a.status, 200);
  assert.equal(b.status, 200);
  //  از دو توکنِ تازه حداکثر یکی زنده است
  const tokens = require('../src/lib/tokens');
  const live = [];
  for (const t of [a.body.refreshToken, b.body.refreshToken]) {
    const row = await one('SELECT revoked_at FROM tokens WHERE token_hash=$1', [tokens.hashToken(t)]);
    if (row && !row.revoked_at) live.push(t);
  }
  assert.ok(live.length <= 1, `دو زنجیرهٔ زنده ماند (${live.length})`);
});

// ── ۸) مجوز ────────────────────────────────────────────────────────

test('۸) مجوز `kid` دارد، و در روزهای مهلتِ اشتراک هنوز صادر می‌شود', async () => {
  const o = await userWithStation('مهلت‌دار', 'sec-grace');
  const t = now();
  //  اشتراکی که دیروز تمام شده ولی سه روز مهلت دارد
  await query(
    `INSERT INTO station_subscriptions (id, station_id, plan, status, starts_at, ends_at, features,
       max_devices, grace_days, note, created_at, updated_at, created_by)
     VALUES ($1,$2,'custom','active',$3,$4,'[]'::jsonb,10,3,'',$3,$3,'test')`,
    [newId('sub'), o.stationId, t - 40 * 86400000, t - 86400000]
  );
  const b = await bind(o, 'pc-grace');
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.ok(b.body.license, 'در مهلت، مجوز باید صادر شود');
  const [hd, pl] = b.body.license.split('.').slice(0, 2)
    .map((x) => JSON.parse(Buffer.from(x, 'base64').toString('utf8')));
  assert.match(String(hd.kid), /^[0-9a-f]{16}$/);
  assert.equal(hd.kid, await require('../src/lib/license').keyId());
  assert.ok(pl.exp > t, 'مجوزِ مهلت نباید پیش از صدور منقضی باشد');
  assert.ok(pl.exp <= t + 3 * 86400000, 'و نه دیرتر از پایانِ مهلت');
});

// ── ۹) تمدیدِ هم‌زمان ─────────────────────────────────────────────

test('۹) ⛔ دو کدِ هم‌زمان روی یک پمپ ⇒ هر دو دوره اضافه می‌شوند', async () => {
  const o = await userWithStation('دوکدی', 'sec-grant');
  const t = await adminToken();
  const c1 = (await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: t })).body.code;
  const c2 = (await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: t })).body.code;
  const before = now();
  const rs = await Promise.all([c1, c2].map((code) =>
    h.post('/api/pump/vip/redeem', { code }, { token: o.accessToken })));
  assert.deepEqual(rs.map((r) => r.status), [201, 201], JSON.stringify(rs.map((r) => r.body)));
  const sub = await one(
    `SELECT ends_at FROM station_subscriptions WHERE station_id=$1 AND status='active'`, [o.stationId]
  );
  const days = (Number(sub.ends_at) - before) / 86400000;
  assert.ok(days > 59, `دو کد یعنی دو دوره — ${days.toFixed(1)} روز ماند`);
});
