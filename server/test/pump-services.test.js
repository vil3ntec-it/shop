'use strict';
/**
 * سه پلنِ پمپ، از نو (۲.۱۱.۲۳ — ۱۴۰۵/۰۷/۲۰).
 *
 *  «استاندارد: کیو‌آر، اپِ گوشی، بات قفل… بک‌اپ روی سرور نداشته باشد…
 *   اطلاعاتِ تانکِ تیلش به سرور نیاید و اصلاً به سرور وصل حتی نشه، ولی از
 *   سرور اشتراک بتونه دریافت کنه. وی‌آی‌پی همه‌چیز. دائمی همه‌چیز، ولی
 *   خدماتش از سرور باید تمدید بشه… کسی که دائمی می‌خره سالِ اول همه‌چی
 *   فعال باشه، بعد خودم برای خدمات اقدام کنم و از سرور بدم.»
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const DAY = 86_400_000;
const ONLINE = ['kar_app', 'bot', 'messenger', 'cloud', 'cloudbackup'];
const stamp = { 'x-app-id': 'tohid-pump-app', 'x-app-version': '3.1.245', 'x-app-platform': 'windows' };

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
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}
const claims = (tok) => JSON.parse(Buffer.from(String(tok).split('.')[1], 'base64url').toString('utf8'));

async function pumpWith(name, plan) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const stationId = made.body.station.id;
  const t = await adminToken();
  const g = await h.post('/api/admin/pump/subscriptions', { stationId, plan }, { token: t });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: 'pc-' + crypto.randomBytes(4).toString('hex'), name: 'کامپیوترِ پمپ', platform: 'windows' } },
    { token: u.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  const sub = await one('SELECT * FROM station_subscriptions WHERE station_id=$1', [stationId]);
  return { ...u, stationId, deviceToken: bound.body.deviceToken, license: bound.body.license, sub, admin: t };
}

const features = async (p) => (await h.get('/api/pump/me', { token: p.accessToken })).body.entitlement.features;

/* ── استاندارد ─────────────────────────────────────────────────────── */

test('استاندارد: هیچ خدماتِ سرور در مجوز نیست؛ داشبورد و تاریخچه باز، مفاد بسته', async () => {
  const p = await pumpWith('استاندارد', 'std');
  const c = claims(p.license);
  for (const k of ONLINE) assert.ok(!c.feat.includes(k), `${k} در استاندارد — ${JSON.stringify(c.feat)}`);
  assert.ok(c.feat.includes('dashboard') && c.feat.includes('history'));
  assert.ok(!c.feat.includes('profit'));
  assert.equal(c.svc_ends, 0, 'استاندارد پایانِ خدمات ندارد');
});

test('⛔ استاندارد: سرور هم حالِ پمپ، خبر، بکاپ و همگام‌سازی را نمی‌پذیرد — ولی اشتراک و بکاپ‌های قبلی باز', async () => {
  const p = await pumpWith('استانداردِ بسته', 'std');
  const tok = { token: p.deviceToken };
  const st = await h.post('/api/pump/device/state', { alerts: [] }, tok);
  assert.equal(st.status, 403, JSON.stringify(st.body));
  assert.equal(st.body.error?.code, 'plan_no_services');
  const ev = await h.post('/api/pump/device/events', { events: [{ kind: 'low_stock', title: 'مخزن', clientId: 'x' }] }, tok);
  assert.equal(ev.status, 403);
  const up = await h.raw('POST', '/api/pump/device/backups?ext=db&kind=auto', Buffer.from('SQLite format 3\0'), { token: p.deviceToken });
  assert.equal(up.status, 403);
  const push = await h.post('/api/sync/v1/push', {
    device_id: 'd1', schema_version: 1, queued: 1,
    ops: [{ op_id: crypto.randomUUID(), table: 'SafeEntry', row_id: 'R1', type: 'insert', ts: Date.now(), fields: { Title: 'x' } }],
  }, { token: p.deviceToken, headers: stamp });
  assert.equal(push.status, 403, JSON.stringify(push.body));
  const pull = await h.get('/api/sync/v1/pull?device_id=d2&since=0', { token: p.deviceToken, headers: stamp });
  assert.equal(pull.status, 403);
  //  ⛔ آن‌چه همیشه باز است
  assert.equal((await h.get('/api/pump/device/backups', tok)).status, 200, 'دیدنِ بکاپ‌های قبلی');
  assert.equal((await h.post('/api/pump/device/license', {}, tok)).status, 200, 'گرفتنِ اشتراک');
  assert.equal((await h.get('/api/pump/me', { token: p.accessToken })).status, 200);
});

test('وی‌آی‌پی: همه‌چیز، و سرور همه را می‌پذیرد', async () => {
  const p = await pumpWith('وی‌آی‌پی', 'vip');
  const c = claims(p.license);
  for (const k of [...ONLINE, 'profit', 'history', 'dashboard']) assert.ok(c.feat.includes(k), k);
  assert.equal(c.svc_ends, 0);
  assert.equal((await h.post('/api/pump/device/state', { alerts: [] }, { token: p.deviceToken })).status, 200);
});

/* ── دائمی ─────────────────────────────────────────────────────────── */

test('دائمی: سالِ اول همه‌چیز رایگان — پایانِ خدمات یک سال از همین حالا', async () => {
  const before = now();
  const p = await pumpWith('دائمی', 'perm');
  const c = claims(p.license);
  for (const k of [...ONLINE, 'profit', 'history', 'dashboard']) assert.ok(c.feat.includes(k), k);
  const yr = require('../src/lib/plans').endOfPeriod(before, 1, 'year');
  assert.ok(Math.abs(Number(p.sub.services_until) - yr) < 60_000, `services_until ${p.sub.services_until} ≠ ${yr}`);
  assert.ok(Math.abs(c.svc_ends - yr) < 60_000, 'مجوز پایانِ خدمات را می‌برد');
});

test('دائمی: پس از پایانِ خدمات، فقط خدماتِ سرور می‌روند — مفاد/داشبورد/تاریخچه و دفتر می‌مانند', async () => {
  const p = await pumpWith('دائمیِ تمام‌خدمات', 'perm');
  await query('UPDATE station_subscriptions SET services_until=$2 WHERE id=$1', [p.sub.id, now() - DAY]);
  const f = await features(p);
  for (const k of ONLINE) assert.ok(!f.includes(k), `${k} پس از پایانِ خدمات`);
  for (const k of ['profit', 'history', 'dashboard', 'debtors', 'safe']) assert.ok(f.includes(k), k);
  assert.equal((await h.post('/api/pump/device/state', { alerts: [] }, { token: p.deviceToken })).status, 403);
  //  اشتراکِ دائمی خودش هنوز فعال است
  const lic = await h.post('/api/pump/device/license', {}, { token: p.deviceToken });
  assert.equal(lic.status, 200);
  assert.ok(lic.body.license);
});

test('مدیر خدماتِ دائمی را تمدید می‌کند: سال، ماه، دلخواه — و قطع', async () => {
  const p = await pumpWith('دائمیِ تمدیدی', 'perm');
  const t = p.admin;
  const path = `/api/admin/pump/subscriptions/${p.sub.id}/services`;
  await query('UPDATE station_subscriptions SET services_until=$2 WHERE id=$1', [p.sub.id, now() - DAY]);

  //  تمام‌شده ⇒ از همین حالا
  const plans = require('../src/lib/plans');
  const t0 = now();
  const y = await h.post(path, { amount: 1, unit: 'year' }, { token: t });
  assert.equal(y.status, 200, JSON.stringify(y.body));
  assert.ok(Math.abs(y.body.servicesUntil - plans.endOfPeriod(t0, 1, 'year')) < 60_000);
  let f = await features(p);
  for (const k of ONLINE) assert.ok(f.includes(k), `${k} پس از تمدید`);

  //  زنده ⇒ از پایانِ فعلی جلو می‌رود
  const m = await h.post(path, { amount: 2, unit: 'month' }, { token: t });
  assert.equal(m.body.servicesUntil, plans.endOfPeriod(y.body.servicesUntil, 2, 'month'));

  //  دلخواه
  const custom = now() + 10 * DAY;
  const c = await h.post(path, { until: custom }, { token: t });
  assert.equal(c.body.servicesUntil, custom);

  //  قطع (گذشته ⇒ همین حالا)
  const cut = await h.post(path, { until: 1 }, { token: t });
  assert.equal(cut.status, 200);
  f = await features(p);
  for (const k of ONLINE) assert.ok(!f.includes(k), `${k} پس از قطع`);

  //  در تاریخچه می‌نشیند
  const hist = await one(`SELECT count(*)::int AS n FROM station_subscription_history WHERE subscription_id=$1 AND action='services'`, [p.sub.id]);
  assert.equal(hist.n, 4);
  //  در فهرستِ مشتری‌ها دیده می‌شود
  const list = await h.get('/api/admin/sales/subscriptions?app=pump', { token: t });
  const row = (list.body.subscriptions || list.body.items || []).find(r => r.id === p.sub.id);
  if (row) assert.ok(row.servicesUntil > 0);
});

test('⛔ خدماتِ جدا فقط برای دائمی؛ نامعتبرها رد', async () => {
  const p = await pumpWith('وی‌آی‌پیِ بی‌خدمات', 'vip');
  const path = `/api/admin/pump/subscriptions/${p.sub.id}/services`;
  const r = await h.post(path, { amount: 1, unit: 'year' }, { token: p.admin });
  assert.equal(r.status, 400);
  const d = await pumpWith('دائمیِ نامعتبر', 'perm');
  const bad = await h.post(`/api/admin/pump/subscriptions/${d.sub.id}/services`, { amount: 1, unit: 'decade' }, { token: d.admin });
  assert.equal(bad.status, 400);
  const anon = await h.post(`/api/admin/pump/subscriptions/${d.sub.id}/services`, { amount: 1, unit: 'year' });
  assert.equal(anon.status, 401);
});

test('دائمیِ امروزی (بی ستونِ خدمات): یک سال از شروعِ خودش', async () => {
  const p = await pumpWith('دائمیِ قدیمی', 'perm');
  await query('UPDATE station_subscriptions SET services_until=NULL, starts_at=$2 WHERE id=$1', [p.sub.id, now() - 400 * DAY]);
  let f = await features(p);
  for (const k of ONLINE) assert.ok(!f.includes(k), `${k} — سالِ اولش گذشته`);
  await query('UPDATE station_subscriptions SET starts_at=$2 WHERE id=$1', [p.sub.id, now() - 100 * DAY]);
  f = await features(p);
  for (const k of ONLINE) assert.ok(f.includes(k), `${k} — هنوز در سالِ اول`);
});

test('تبدیل به دائمی از پنل: سالِ اولِ خدمات از همان لحظه', async () => {
  const p = await pumpWith('تبدیلی', 'vip');
  const t0 = now();
  const r = await h.post(`/api/admin/pump/subscriptions/${p.sub.id}/permanent`, {}, { token: p.admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const row = await one('SELECT services_until FROM station_subscriptions WHERE id=$1', [p.sub.id]);
  const yr = require('../src/lib/plans').endOfPeriod(t0, 1, 'year');
  assert.ok(Math.abs(Number(row.services_until) - yr) < 60_000);
});
