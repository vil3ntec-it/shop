'use strict';
/**
 * STF-07 و STF-08 — `/api/shop/staff-time` پشتِ پرچمِ خاموشِ `shop.staff_time`.
 *
 *  • خاموش ⇒ فقط `/config` (`enabled: false`)، بقیه ۴۰۳ و هیچ ردیفی؛
 *  • زدن همان قاعدهٔ `core/staff/TimeClock.check`: دو ضربه و زدنِ پیش از آخرین ۴۰۹؛
 *    دوباره‌فرستادنِ همان زدن بی‌خطر (۲۰۰، یک ردیف)؛ ساعتِ جلوتر از سرور یا کهنه‌تر از ۷ روز ۴۰۰؛
 *  • شاگرد فقط برای خودش می‌زند و فقط خودش را می‌بیند؛ صاحب برای او «خروجِ جاافتاده» را می‌زند؛
 *  • دو زدنِ هم‌زمانِ هم‌نوع ⇒ دقیقاً یکی؛
 *  • دکانِ دیگر عضوِ این دکان را نمی‌شناسد؛
 *  • نرخ‌های پورسانت: فقط صاحب و مدیر، پایه‌امتیازِ ۰ تا ۱۰٬۰۰۰، قفلِ نسخه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

let admin = '';
let owner;
let staff;
let other;
let seq = 0;
const pid = () => `punch-${Date.now().toString(36)}-${++seq}`;
const MIN = 60_000;

async function shopOf(label) {
  const u = await h.newUser(`صاحب-${label}`);
  const r = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: u.accessToken, shopId: r.body.shop.id };
}
const flag = (scope, value) => h.put('/api/admin/pump/live-config', { scope, key: 'shop.staff_time', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const punch = (who, body) => h.post('/api/shop/staff-time/punches', body, { token: who.token });

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'st-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'st-admin', password: 'Admin!12345' })).body.token;
  owner = await shopOf('الف');
  other = await shopOf('ب');
  const code = (await h.post('/api/shop/invite', { role: 'staff' }, { token: owner.token })).body.code;
  const s = await h.post('/api/auth/staff-login', { code, name: 'شاگرد', device: { uid: `st-phone-${Date.now()}`, name: 'گوشی', platform: 'android' } });
  assert.equal(s.status, 201, JSON.stringify(s.body));
  staff = { token: s.body.accessToken };
  for (const who of [owner, staff, other]) {
    const c = await h.get('/api/shop/staff-time/config', { token: who.token });
    assert.equal(c.status, 200, JSON.stringify(c.body));
    who.memberId = c.body.memberId;
  }
});
test.after(async () => { await h.stop(); });

test('خاموش (پیش‌فرض): فقط /config، بقیه ۴۰۳ و هیچ ردیفی', async () => {
  const c = await h.get('/api/shop/staff-time/config', { token: staff.token });
  assert.equal(c.body.enabled, false);
  assert.equal(c.body.role, 'staff');
  const r = await punch(staff, { id: pid(), kind: 'in', at: Date.now() });
  assert.equal(r.status, 403);
  assert.equal(r.body.error?.code, 'staff_time_disabled');
  assert.equal((await h.get('/api/shop/staff-time/punches', { token: owner.token })).status, 403);
  assert.equal((await h.get('/api/shop/staff-time/commission-plan', { token: owner.token })).status, 403);
  assert.equal((await query('SELECT count(*)::int AS n FROM staff_punches')).rows[0].n, 0);
  assert.equal((await h.get('/api/shop/staff-time/config')).status, 401);
});

test('زدن: ورود و خروج به نوبت؛ دو ضربه، پیش از آخرین و شناسهٔ دوباره‌به‌کاررفته ۴۰۹؛ دوباره‌فرستادن بی‌خطر', async () => {
  await flag(owner.shopId, true);
  assert.equal((await h.get('/api/shop/staff-time/config', { token: staff.token })).body.enabled, true);
  const t0 = Date.now() - 3 * 60 * MIN;
  const first = { id: pid(), kind: 'in', at: t0 };
  const a = await punch(staff, first);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(a.body.punch.memberId, staff.memberId);
  assert.equal(a.body.punch.byMemberId, staff.memberId);

  const again = await punch(staff, first);
  assert.equal(again.status, 200);
  assert.equal(again.body.duplicate, true);
  assert.equal((await punch(staff, { ...first, at: t0 + 1 })).body.error?.code, 'punch_id_conflict');
  assert.equal((await punch(staff, { id: pid(), kind: 'in', at: t0 + MIN })).body.error?.code, 'punch_same_kind');
  assert.equal((await punch(staff, { id: pid(), kind: 'out', at: t0 - MIN })).body.error?.code, 'punch_before_last');
  const out = await punch(staff, { id: pid(), kind: 'out', at: t0 + 120 * MIN });
  assert.equal(out.status, 201, JSON.stringify(out.body));
  assert.equal((await query(`SELECT count(*)::int AS n FROM staff_punches WHERE shop_id=$1`, [owner.shopId])).rows[0].n, 2);
});

test('ساعتِ دستگاه: جلوتر از سرور یا کهنه‌تر از ۷ روز ۴۰۰؛ ورودیِ نادرست ۴۰۰', async () => {
  const bad = [
    [{ id: pid(), kind: 'in', at: Date.now() + 10 * MIN }, 'punch_in_future'],
    [{ id: pid(), kind: 'in', at: Date.now() - 8 * 24 * 60 * MIN }, 'punch_too_old'],
    [{ id: 'short', kind: 'in', at: Date.now() }, 'punch_invalid'],
    [{ id: pid(), kind: 'break', at: Date.now() }, 'punch_invalid'],
    [{ id: pid(), kind: 'in', at: 'دیروز' }, 'punch_invalid'],
  ];
  for (const [body, code] of bad) {
    const r = await punch(staff, body);
    assert.equal(r.status, 400, JSON.stringify(body));
    assert.equal(r.body.error?.code, code);
  }
});

test('شاگرد برای دیگری نمی‌زند و فقط خودش را می‌بیند؛ صاحب «خروجِ جاافتاده» را برای او می‌زند', async () => {
  assert.equal((await punch(staff, { id: pid(), kind: 'in', at: Date.now(), memberId: owner.memberId })).status, 403);
  const ownIn = await punch(owner, { id: pid(), kind: 'in', at: Date.now() - 30 * MIN });
  assert.equal(ownIn.status, 201);
  const sIn = await punch(staff, { id: pid(), kind: 'in', at: Date.now() - 20 * MIN });
  assert.equal(sIn.status, 201);
  const fix = await punch(owner, { id: pid(), kind: 'out', at: Date.now() - 5 * MIN, memberId: staff.memberId });
  assert.equal(fix.status, 201, JSON.stringify(fix.body));
  assert.equal(fix.body.punch.memberId, staff.memberId);
  assert.equal(fix.body.punch.byMemberId, owner.memberId, 'چه کسی زد ثبت می‌شود');

  const mine = (await h.get('/api/shop/staff-time/punches', { token: staff.token })).body.punches;
  assert.ok(mine.length >= 3 && mine.every((p) => p.memberId === staff.memberId), 'شاگرد فقط خودش');
  const filtered = (await h.get(`/api/shop/staff-time/punches?member=${owner.memberId}`, { token: staff.token })).body.punches;
  assert.ok(filtered.every((p) => p.memberId === staff.memberId), 'شاگرد با ?member هم دیگری را نمی‌بیند');
  const all = (await h.get('/api/shop/staff-time/punches', { token: owner.token })).body.punches;
  assert.deepEqual([...new Set(all.map((p) => p.memberId))].sort(), [owner.memberId, staff.memberId].sort());
  assert.ok(all.find((p) => p.memberId === staff.memberId).name, 'نامِ عضو برای گزارش');
  assert.equal((await h.get('/api/shop/staff-time/punches?from=10&to=5', { token: owner.token })).status, 400);
  assert.equal((await h.get(`/api/shop/staff-time/punches?from=0&to=${Date.now()}`, { token: owner.token })).status, 400, 'بیش از ۶۲ روز');
});

test('دو زدنِ هم‌زمانِ هم‌نوع ⇒ دقیقاً یکی می‌نشیند', async () => {
  const at = Date.now() - MIN;
  const [x, y] = await Promise.all([
    punch(staff, { id: pid(), kind: 'in', at }),
    punch(staff, { id: pid(), kind: 'in', at }),
  ]);
  assert.deepEqual([x.status, y.status].sort(), [201, 409]);
});

test('دکانِ دیگر: عضوِ این دکان را نمی‌شناسد و زدن‌هایش را نمی‌بیند', async () => {
  await flag(other.shopId, true);
  assert.equal((await punch(other, { id: pid(), kind: 'in', at: Date.now(), memberId: staff.memberId })).status, 404);
  const seen = (await h.get('/api/shop/staff-time/punches', { token: other.token })).body.punches;
  assert.deepEqual(seen, []);
});

test('نرخ‌های پورسانت: فقط صاحب و مدیر، پایه‌امتیازِ درست، قفلِ نسخه', async () => {
  const empty = await h.get('/api/shop/staff-time/commission-plan', { token: owner.token });
  assert.deepEqual(empty.body, { defaultBp: 0, sellerBp: {}, categoryBp: {}, version: 0, updatedAt: 0 });
  assert.equal((await h.get('/api/shop/staff-time/commission-plan', { token: staff.token })).status, 403);
  const body = { version: 0, defaultBp: 200, sellerBp: { 'شاگرد': 300 }, categoryBp: { cigarettes: 0 } };
  assert.equal((await h.put('/api/shop/staff-time/commission-plan', body, { token: staff.token })).status, 403);
  const w = await h.put('/api/shop/staff-time/commission-plan', body, { token: owner.token });
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.version, 1);
  assert.deepEqual(w.body.sellerBp, { 'شاگرد': 300 });
  assert.equal((await h.put('/api/shop/staff-time/commission-plan', body, { token: owner.token })).status, 409, 'نسخهٔ کهنه');
  for (const bad of [
    { version: 1, defaultBp: 10001 },
    { version: 1, defaultBp: 1.5 },
    { version: 1, defaultBp: 100, sellerBp: { a: -1 } },
    { version: 1, defaultBp: 100, sellerBp: [] },
    { defaultBp: 100 },                                  // بی نسخه
  ]) {
    const r = await h.put('/api/shop/staff-time/commission-plan', bad, { token: owner.token });
    assert.equal(r.status, 400, JSON.stringify(bad));
  }
  assert.equal((await h.get('/api/shop/staff-time/commission-plan', { token: owner.token })).body.version, 1);
  assert.equal((await h.get('/api/shop/staff-time/commission-plan', { token: other.token })).body.version, 0, 'دکانِ دیگر جداست');
});
