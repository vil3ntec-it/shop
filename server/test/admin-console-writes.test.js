'use strict';
/**
 * درهایی که پنلِ مرکزی (ریپوی server، شاخهٔ `claude/server-40`، ۱.۵۰.۷۵) از سرورِ حساب خواست — ممیزی §۳-۴.
 *
 * پیش از این:
 *   • برگرداندنِ دستگاهِ کارمند فقط برای صاحبِ دکان بود (`/api/shop/devices/:id/enable`)؛ پنل «notSupported» می‌گفت؛
 *   • ازکارانداختن/برگرداندنِ دستگاه، بازبینیِ بانکِ عکس و پشتیبانِ دستی `Idempotency-Key` نداشتند ⇒ پل یادِ ده‌دقیقه‌ایِ
 *     حافظه‌اش را داشت و تکرارِ پس از قطعِ شبکه/راه‌اندازیِ دوباره کارِ دوم می‌ساخت (pg_dump ِ دوم، ردیفِ سابقهٔ دوم)؛
 *   • دکانِ «الف» دستگاهی را که دکانِ «ب» از کار انداخته بود (کاربرِ عضوِ هر دو) برمی‌گرداند؛ دو برگرداندنِ هم‌زمان دو سابقه؛
 *   • `GET …/staff-perms` صفحه نداشت.
 *
 * حالا: `POST /api/admin/shops/:id/staff-perms/devices/:did/enable` (ارشد + دلیل، همان `deviceOfShop` و سابقه)، کلیدِ یکتایی با
 * نشانِ پایگاه (`lib/admin-write-once.js`، مهاجرتِ ۵۰۰)، `?limit=&offset=` و سه توانایی در `/admin/me`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const PASS = 'Admin!12345';
const ids = {};
const toks = {};
let seq = 0;
const key = (p = 'cw') => `${p}-${Date.now()}-${++seq}-key`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  for (const [u, role] of [['cw-arshad', 'superadmin'], ['cw-arshad2', 'superadmin'], ['cw-modir', 'admin'],
    ['cw-mali', 'finance'], ['cw-binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, now()]
    );
  }
});
test.after(async () => { await h.stop(); });
//  سطلِ IP ِ درِ دستگاه (۶۰ در دقیقه، پیش از احراز) با شمارِ درخواستِ همین پرونده پر می‌شود ⇒ هر آزمون از صفر
test.beforeEach(() => resetAll());

async function tok(u) {
  if (!toks[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    toks[u] = r.body.token;
  }
  return toks[u];
}
const A = async (who, method, path, body = null, headers = {}) =>
  h.api(method, `/api/admin${path}`, { token: await tok(who), body, headers });
const withKey = (k) => (k ? { 'Idempotency-Key': k } : {});

/** دکان با صاحب و `n` شاگرد — از راهِ واقعی (هر کدام با دستگاهِ خودش) */
async function team(label, n = 1) {
  const owner = await h.newUser(`صاحبِ ${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = [];
  for (let i = 0; i < n; i++) {
    const s = await h.newUser(`شاگردِ ${label} ${i}`);
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    staff.push(s);
  }
  return { owner, staff, code, shopId: made.body.shop.id };
}
const devOf = async (u) => (await one('SELECT id FROM devices WHERE user_id=$1 ORDER BY created_at LIMIT 1', [u.user.id])).id;
const devRow = (id) => one('SELECT status, disabled_at, disabled_shop_id FROM devices WHERE id=$1', [id]);
const audits = (action, targetId) => many(
  'SELECT * FROM audit_logs WHERE action=$1 AND target_id=$2 ORDER BY created_at', [action, String(targetId)]);
const devPath = (shopId, dev, act) => `/shops/${shopId}/staff-perms/devices/${dev}/${act}`;
const claims = async () => Number((await one('SELECT COUNT(*)::int AS n FROM admin_write_claims')).n);

/* ── ۱) درِ برگرداندنِ دستگاه برای مدیر ──────────────────────────────── */

test('enable: ارشد + دلیل برمی‌گرداند؛ همان سابقهٔ device.enabled با دکان و «admin:شناسه»؛ دوباره ⇒ ۴۰۰ device_not_disabled', async () => {
  const t = await team('روشن');
  const dev = await devOf(t.staff[0]);
  const off = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گوشی گم شد' });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.ok((await devRow(dev)).disabled_at);

  const noReason = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), {});
  assert.equal(noReason.status, 400, JSON.stringify(noReason.body));
  assert.equal(noReason.body.error.code, 'reason_required');
  const short = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'اه' });
  assert.equal(short.body.error.code, 'reason_required');
  assert.ok((await devRow(dev)).disabled_at, 'بی دلیل هیچ تغییری نیست');

  const on = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'گوشی پیدا شد' });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  assert.deepEqual(on.body, { ok: true, id: dev });
  const row = await devRow(dev);
  assert.equal(row.status, 'active');
  assert.equal(row.disabled_at, null);
  const [a] = await audits('device.enabled', dev);
  assert.equal(a.shop_id, t.shopId);
  assert.equal(a.actor_type, 'admin');
  const detail = typeof a.detail === 'string' ? JSON.parse(a.detail) : a.detail;
  assert.equal(detail.reason, 'گوشی پیدا شد');
  assert.equal(detail.by, `admin:${ids['cw-arshad']}`);

  const again = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'دوباره' });
  assert.equal(again.status, 400);
  assert.equal(again.body.error.code, 'device_not_disabled');
  assert.equal((await audits('device.enabled', dev)).length, 1);
  //  نشست‌های پیشین برنمی‌گردند (همان قاعدهٔ صاحبِ دکان)
  assert.equal((await h.get('/api/shop', { token: t.staff[0].accessToken })).status, 401);
});

test('disable: دلیلِ نوشته اجباری (تصمیمِ ۲-۲۰، ۱۴۰۵/۰۸/۲۰) — بی دلیل یا کوتاه ⇒ ۴۰۰ reason_required و دستگاه روشن می‌ماند', async () => {
  const t = await team('دلیلِ خاموش');
  const dev = await devOf(t.staff[0]);
  const c0 = await claims();
  for (const body of [{}, { reason: '' }, { reason: 'اه' }, { reason: '   ' }]) {
    const r = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), body, withKey(key('dis')));
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'reason_required', JSON.stringify(body));
  }
  assert.equal((await devRow(dev)).disabled_at, null, 'بی دلیل هیچ تغییری نیست');
  assert.equal((await audits('device.disabled', dev)).length, 0);
  assert.equal(await claims(), c0, 'کلیدِ یکتایی پیش از دلیل مصرف نمی‌شود');
  const off = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گوشی گم شد' });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.ok((await devRow(dev)).disabled_at);
  const [a] = await audits('device.disabled', dev);
  const detail = typeof a.detail === 'string' ? JSON.parse(a.detail) : a.detail;
  assert.equal(detail.reason, 'گوشی گم شد');
});

test('enable: مرزِ دکان — دستگاهِ عضوِ دکانِ دیگر ⇒ ۴۰۴ device_not_found؛ دکانِ نبوده ⇒ ۴۰۴؛ بی توکن ⇒ ۴۰۱', async () => {
  const a = await team('مرزِ الف');
  const b = await team('مرزِ ب');
  const devB = await devOf(b.staff[0]);
  assert.equal((await A('cw-arshad', 'POST', devPath(b.shopId, devB, 'disable'), { reason: 'گم شد' })).status, 200);
  for (const act of ['enable', 'disable']) {
    const r = await A('cw-arshad', 'POST', devPath(a.shopId, devB, act), { reason: 'از راهِ دکانِ دیگر' });
    assert.equal(r.status, 404, `${act} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'device_not_found');
  }
  assert.ok((await devRow(devB)).disabled_at, 'از راهِ دکانِ «الف» دست نخورد');
  assert.equal((await A('cw-arshad', 'POST', devPath('shp_nist', devB, 'enable'), { reason: 'نبوده' })).status, 404);
  const anon = await h.api('POST', `/api/admin${devPath(b.shopId, devB, 'enable')}`, { body: { reason: 'بی توکن' } });
  assert.equal(anon.status, 401);
});

test('enable: دستگاهی که دکانِ دیگر (کاربرِ عضوِ هر دو) از کار انداخته ⇒ ۴۰۹ disabled_by_other_shop، برای مدیر و صاحبِ دکان', async () => {
  const a = await team('دوگانه الف');
  const b = await team('دوگانه ب', 0);
  const both = a.staff[0];
  //  عضویتِ دوم مستقیم (پیوستن با کد کاربرِ عضوِ دکانِ دیگر را نمی‌پذیرد؛ کاربرِ چنددکانی همان `tenant-matrix` است)
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), b.shopId, both.user.id, now()]);
  const dev = await devOf(both);
  const off = await h.post(`/api/shop/devices/${dev}/disable`, { reason: 'گم شد' }, { token: b.owner.accessToken });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal((await devRow(dev)).disabled_shop_id, b.shopId);

  const viaA = await A('cw-arshad', 'POST', devPath(a.shopId, dev, 'enable'), { reason: 'از راهِ دکانِ الف' });
  assert.equal(viaA.status, 409, JSON.stringify(viaA.body));
  assert.equal(viaA.body.error.code, 'disabled_by_other_shop');
  const ownerA = await h.post(`/api/shop/devices/${dev}/enable`, {}, { token: a.owner.accessToken });
  assert.equal(ownerA.status, 409, JSON.stringify(ownerA.body));
  assert.equal(ownerA.body.error.code, 'disabled_by_other_shop');
  assert.ok((await devRow(dev)).disabled_at, 'دکانِ الف کارِ دکانِ ب را برنگرداند');
  //  دورزدن با «انداختنِ دوباره» از راهِ الف: نشست‌ها باطل، ولی نشانِ «انداخته به دستِ ب» می‌ماند
  const reOff = await A('cw-arshad', 'POST', devPath(a.shopId, dev, 'disable'), { reason: 'از راهِ الف دوباره' });
  assert.equal(reOff.status, 200, JSON.stringify(reOff.body));
  assert.equal((await devRow(dev)).disabled_shop_id, b.shopId);
  const ownerReOff = await h.post(`/api/shop/devices/${dev}/disable`, { reason: 'صاحبِ الف' }, { token: a.owner.accessToken });
  assert.equal(ownerReOff.status, 200);
  assert.equal((await devRow(dev)).disabled_shop_id, b.shopId);
  assert.equal((await h.post(`/api/shop/devices/${dev}/enable`, {}, { token: a.owner.accessToken })).body.error.code, 'disabled_by_other_shop');

  //  همان دکانی که انداخت برمی‌گرداند
  const viaB = await A('cw-arshad', 'POST', devPath(b.shopId, dev, 'enable'), { reason: 'صاحبِ ب خواست' });
  assert.equal(viaB.status, 200, JSON.stringify(viaB.body));

  //  دکانِ انداخته دیگر این کاربر را ندارد ⇒ راه برای همیشه بسته نمی‌ماند
  const off2 = await h.post(`/api/shop/devices/${dev}/disable`, { reason: 'باز گم شد' }, { token: b.owner.accessToken });
  assert.equal(off2.status, 200);
  await query(`UPDATE shop_members SET status='removed' WHERE shop_id=$1 AND user_id=$2`, [b.shopId, both.user.id]);
  const freed = await A('cw-arshad', 'POST', devPath(a.shopId, dev, 'enable'), { reason: 'دکانِ ب دیگر عضو ندارد' });
  assert.equal(freed.status, 200, JSON.stringify(freed.body));
});

test('نقش‌ها: مدیرِ عادی/مالی/بیننده ⇒ ۴۰۳ روی enable/disable؛ بیننده staff-perms را می‌خواند، مالی نه؛ کلیدِ سرویس با «انجام‌دهندهٔ» admin ⇒ ۴۰۳', async () => {
  const t = await team('نقش');
  const dev = await devOf(t.staff[0]);
  for (const who of ['cw-modir', 'cw-mali', 'cw-binande']) {
    for (const act of ['enable', 'disable']) {
      const r = await A(who, 'POST', devPath(t.shopId, dev, act), { reason: 'آزمونِ نقش' }, withKey(key()));
      assert.equal(r.status, 403, `${who} ${act} ⇒ ${r.status}`);
    }
  }
  assert.equal((await A('cw-mali', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'آزمون' })).body.error.code, 'role_forbidden');
  assert.equal((await A('cw-binande', 'GET', `/shops/${t.shopId}/staff-perms`)).status, 200);
  assert.equal((await A('cw-binande', 'GET', `/shops/${t.shopId}/staff-perms?limit=1`)).status, 200);
  assert.equal((await A('cw-mali', 'GET', `/shops/${t.shopId}/staff-perms`)).status, 403);
  const svc = await require('../src/lib/admin-service').create({ name: 'پلِ نقش', roleCap: 'superadmin', createdBy: ids['cw-arshad'] });
  const r = await h.api('POST', `/api/admin${devPath(t.shopId, dev, 'disable')}`, {
    body: { reason: 'آزمونِ پل' },
    headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': 'panel:7', 'X-Actor-Role': 'admin', 'Idempotency-Key': key() },
  });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal((await devRow(dev)).disabled_at, null, 'هیچ نقشِ باریکی دستگاه را نینداخت');
  assert.equal(await claims(), 0, 'ردِ نقش پیش از هر نشان');
});

test('سقفِ IP پیش از احراز روی درِ دستگاه: ۶۰ درخواستِ بی‌توکن ۴۰۱، شصت‌ویکمی ۴۲۹ (پیش از هر پرسشِ پایگاه)', async () => {
  const url = '/api/admin/shops/shp_x/staff-perms/devices/dev_x/enable';
  for (let i = 0; i < 60; i++) {
    const r = await h.api('POST', url, { body: { reason: 'سیل' } });
    assert.equal(r.status, 401, `#${i} ${r.status}`);
  }
  const over = await h.api('POST', url, { body: { reason: 'سیل' } });
  assert.equal(over.status, 429);
  const viaDisable = await h.api('POST', '/api/admin/shops/shp_x/staff-perms/devices/dev_x/disable', { body: {} });
  assert.equal(viaDisable.status, 429, 'یک سطل برای هر دو درِ دستگاه');
  resetAll();
});

/* ── ۲) Idempotency-Key روی دستگاه ───────────────────────────────────── */

test('Idempotency-Key (دستگاه): همان کلید ⇒ همان پاسخ + Idempotent-Replayed و یک سابقه؛ بدنه یا درِ دیگر ⇒ ۴۰۹؛ کلیدِ بد ⇒ ۴۰۰', async () => {
  const t = await team('کلید');
  const dev = await devOf(t.staff[0]);
  const k = key();
  const a = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گم شد' }, withKey(k));
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.headers.get('idempotent-replayed'), null);
  const b = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گم شد' }, withKey(k));
  assert.equal(b.status, 200);
  assert.equal(b.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(b.body, a.body);
  assert.equal((await audits('device.disabled', dev)).length, 1, 'تکرار ردیفِ دوم نساخت');

  const otherReason = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'دلیلِ دیگر' }, withKey(k));
  assert.equal(otherReason.status, 409);
  assert.equal(otherReason.body.error.code, 'idempotency_key_reused');
  const otherRoute = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'گم شد' }, withKey(k));
  assert.equal(otherRoute.status, 409, 'کلید مالِ همان یک در است');
  assert.equal(otherRoute.body.error.code, 'idempotency_key_reused');
  assert.ok((await devRow(dev)).disabled_at);
  const bad = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'پیدا شد' }, withKey('short'));
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_idempotency_key');

  const k2 = key();
  const on = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'پیدا شد' }, withKey(k2));
  assert.equal(on.status, 200, JSON.stringify(on.body));
  const on2 = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'پیدا شد' }, withKey(k2));
  assert.equal(on2.status, 200, 'تکرار ۴۰۰ ِ device_not_disabled نمی‌گیرد؛ همان پاسخ');
  assert.equal(on2.headers.get('idempotent-replayed'), 'true');
  assert.equal((await audits('device.enabled', dev)).length, 1);

  //  شکستِ روشن نشان نمی‌گذارد: همان کلید دوباره همان ۴۰۰ را می‌گیرد، نه «در راه»
  const k3 = key();
  for (let i = 0; i < 2; i++) {
    const r = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'باز هم' }, withKey(k3));
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'device_not_disabled');
  }
  assert.equal(await claims(), 0);
  //  بی کلید همان رفتارِ پیشین
  const plain = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'بی کلید' });
  assert.equal(plain.status, 200);
  assert.equal(plain.headers.get('idempotent-replayed'), null);
});

test('Idempotency-Key به مدیر و «انجام‌دهنده» بسته است: همان کلید از مدیرِ دیگر یا انجام‌دهندهٔ دیگر ⇒ کارِ خودش، نه پاسخِ دیگری', async () => {
  const t = await team('دامنه');
  const dev = await devOf(t.staff[0]);
  const k = key();
  const body = { reason: 'گم شد' };
  const a = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), body, withKey(k));
  assert.equal(a.status, 200);
  const b = await A('cw-arshad2', 'POST', devPath(t.shopId, dev, 'disable'), body, withKey(k));
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal(b.headers.get('idempotent-replayed'), null, 'پاسخِ مدیرِ دیگر بازپخش نشد');
  assert.equal((await audits('device.disabled', dev)).length, 2);
  const sameAdminAgain = await A('cw-arshad2', 'POST', devPath(t.shopId, dev, 'disable'), body, withKey(k));
  assert.equal(sameAdminAgain.headers.get('idempotent-replayed'), 'true');

  const svc = await require('../src/lib/admin-service').create({ name: 'پلِ دامنه', roleCap: 'superadmin', createdBy: ids['cw-arshad'] });
  const via = (actor, k2) => h.api('POST', `/api/admin${devPath(t.shopId, dev, 'disable')}`, {
    body, headers: { Authorization: `Bearer ${svc.key}`, 'X-Actor-Id': actor, 'X-Actor-Role': 'superadmin', 'Idempotency-Key': k2 },
  });
  const k2 = key();
  const p1 = await via('panel:1', k2);
  assert.equal(p1.status, 200, JSON.stringify(p1.body));
  const p2 = await via('panel:2', k2);
  assert.equal(p2.status, 200);
  assert.equal(p2.headers.get('idempotent-replayed'), null, 'انجام‌دهندهٔ دیگرِ همان کلیدِ سرویس');
  const p1again = await via('panel:1', k2);
  assert.equal(p1again.headers.get('idempotent-replayed'), 'true');
  assert.equal((await audits('device.disabled', dev)).length, 4);
});

test('نشانِ «در راه»: نشانِ زندهٔ همان کلید ⇒ ۴۰۹ idempotency_in_progress (بدنهٔ دیگر ⇒ reused)؛ نشانِ کهنه راه را نمی‌بندد', async () => {
  const t = await team('نشان');
  const dev = await devOf(t.staff[0]);
  const once = require('../src/lib/admin-write-once');
  const idem = require('../src/lib/idempotency');
  const k = key();
  const body = { reason: 'گم شد' };
  const fake = {
    method: 'POST', baseUrl: `/api/admin/shops/${t.shopId}/staff-perms`, path: `/devices/${dev}/disable`, body,
    get: () => k, admin: { id: ids['cw-arshad'] },
  };
  const parsed = idem.fromRequest(fake, once.scopeOf(fake));
  await query('INSERT INTO admin_write_claims (scope, key, token, request_hash, claimed_at) VALUES ($1,$2,$3,$4,$5)',
    [parsed.scope, parsed.key, 't-dead', parsed.hash, now()]);
  const busy = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), body, withKey(k));
  assert.equal(busy.status, 409, JSON.stringify(busy.body));
  assert.equal(busy.body.error.code, 'idempotency_in_progress');
  const other = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'دیگر' }, withKey(k));
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  assert.equal((await devRow(dev)).disabled_at, null);
  await query('UPDATE admin_write_claims SET claimed_at=$1 WHERE key=$2', [now() - once.STALE_MS - 1000, k]);
  const taken = await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), body, withKey(k));
  assert.equal(taken.status, 200, JSON.stringify(taken.body));
  assert.ok((await devRow(dev)).disabled_at);
  assert.equal(await claims(), 0);
});

/* ── ۳) هم‌زمانی (روی PostgreSQL ِ واقعی معنا دارد؛ PGlite پشتِ هم می‌چیند) ── */

test('هم‌زمان: چهار ازکارانداختن با یک کلید ⇒ یک اثر (یک سابقه)؛ بقیه همان پاسخ یا ۴۰۹ idempotency_in_progress', async () => {
  for (let round = 0; round < 3; round++) {
    const t = await team(`هم‌زمان ${round}`);
    const dev = await devOf(t.staff[0]);
    const k = key('par');
    const out = await Promise.all([1, 2, 3, 4].map(() =>
      A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گم شد' }, withKey(k))));
    const fresh = out.filter((r) => r.status === 200 && r.headers.get('idempotent-replayed') === null);
    assert.equal(fresh.length, 1, JSON.stringify(out.map((r) => [r.status, r.body?.error?.code])));
    for (const r of out) {
      if (r.status === 200) continue;
      assert.equal(r.status, 409);
      assert.equal(r.body.error.code, 'idempotency_in_progress');
    }
    assert.equal((await audits('device.disabled', dev)).length, 1, 'فقط یک اثر');
    assert.equal(await claims(), 0);
  }
});

//  ⛔ پنجرهٔ «یافتنِ پاسخ ⇄ دیدنِ نشان»: دومی پاسخ را (هنوز ثبت‌نشده) نمی‌یابد، اولی همان دم پاسخ را ثبت و نشان را برمی‌دارد،
//  و دومی با برداشتِ تازهٔ READ COMMITTED نشان را هم نمی‌بیند ⇒ کارِ دوم (دو ۲۰۰ ِ تازه با یک کلید). پایانِ اولی باید زیرِ
//  همان قفلِ کلید باشد. `find` ِ دومی تا ثبتِ پاسخِ اولی (یا ۱٫۵ ثانیه) نگه داشته می‌شود تا پنجره همیشه باز شود.
test('هم‌زمان: پایانِ کارِ اول میانِ «یافتنِ پاسخ» و «دیدنِ نشان» ِ دومی ⇒ یک اثر؛ دومی بازپخش یا ۴۰۹، نه کارِ دوم', {
  skip: require('../src/db').isPglite() && 'PGlite یک اتصال است و تراکنش‌ها را پشتِ هم می‌چیند — این پنجره فقط روی PostgreSQL هست',
}, async () => {
  const t = await team('پنجرهٔ یافتن');
  const dev = await devOf(t.staff[0]);
  const idem = require('../src/lib/idempotency');
  const k = key('win');
  const origFind = idem.find;
  let calls = 0;
  idem.find = async (x, run) => {
    const row = await origFind(x, run);
    if (x.key === k && ++calls === 2 && !row) {
      for (let i = 0; i < 30 && !(await origFind(x)); i++) await new Promise((r) => setTimeout(r, 50));
    }
    return row;
  };
  try {
    const out = await Promise.all([1, 2].map(() =>
      A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گم شد' }, withKey(k))));
    const fresh = out.filter((r) => r.status === 200 && r.headers.get('idempotent-replayed') === null);
    assert.equal(fresh.length, 1, JSON.stringify(out.map((r) => [r.status, r.headers.get('idempotent-replayed'), r.body?.error?.code])));
    for (const r of out) if (r !== fresh[0]) assert.ok(r.status === 409 || r.headers.get('idempotent-replayed') === 'true', String(r.status));
    assert.equal((await audits('device.disabled', dev)).length, 1, 'فقط یک اثر');
  } finally {
    idem.find = origFind;
  }
});

test('هم‌زمان: دو برگرداندنِ همان دستگاه با دو کلیدِ جدا (دو پنجره) ⇒ دقیقاً یکی ۲۰۰ و یک سابقه', async () => {
  for (let round = 0; round < 3; round++) {
    const t = await team(`دو پنجره ${round}`);
    const dev = await devOf(t.staff[0]);
    assert.equal((await A('cw-arshad', 'POST', devPath(t.shopId, dev, 'disable'), { reason: 'گم شد' })).status, 200);
    const out = await Promise.all([key('w1'), key('w2'), ''].map((k) =>
      A('cw-arshad', 'POST', devPath(t.shopId, dev, 'enable'), { reason: 'پیدا شد' }, withKey(k))));
    assert.equal(out.filter((r) => r.status === 200).length, 1, JSON.stringify(out.map((r) => [r.status, r.body?.error?.code])));
    for (const r of out.filter((x) => x.status !== 200)) assert.equal(r.body.error.code, 'device_not_disabled');
    assert.equal((await audits('device.enabled', dev)).length, 1);
  }
});

/* ── ۴) بانکِ عکس ────────────────────────────────────────────────────── */

let imgSeed = 4000;
async function pendingImage(label) {
  const p = await A('cw-modir', 'POST', '/image-bank/products', { name: `کالای ${label}`, brand: 'آزمون', gtin: F.ean13(`59012${String(++imgSeed).padStart(7, '0')}`) });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const up = await h.raw('POST', `/api/admin/image-bank/images?bank_product_id=${p.body.id}&license_status=licensed&commercial_use=true&license_source=supplier-feed`,
    F.jpeg(800, 600, { seed: ++imgSeed }), { token: await tok('cw-modir') });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  return up.body.id;
}

test('بانکِ عکس: approve/search/archive/reject با کلید ⇒ یک بار (Idempotent-Replayed، یک سابقه)؛ کلید با بدنهٔ دیگر ⇒ ۴۰۹؛ مدیرِ عادی ⇒ ۴۰۳', async () => {
  await require('../src/lib/live-config').set('all', 'image_bank.central', true, 'test');
  const id = await pendingImage('یک بار');
  const IB = (act, body, k, who = 'cw-arshad') => A(who, 'POST', `/image-bank/images/${id}/${act}`, body, withKey(k));
  assert.equal((await IB('approve', { reason: 'مجوز دیده شد' }, key(), 'cw-modir')).status, 403);
  assert.equal((await IB('approve', { reason: 'مجوز دیده شد' }, key(), 'cw-mali')).status, 403);
  const steps = [
    ['approve', { reason: 'مجوز دیده شد' }, 'admin.bank_image_approved'],
    ['search', { reason: 'فعلاً پنهان', enabled: false }, 'admin.bank_image_search'],
    ['archive', { reason: 'بستهٔ قدیمی' }, 'admin.bank_image_archived'],
  ];
  for (const [act, body, action] of steps) {
    const k = key(act);
    const a = await IB(act, body, k);
    assert.equal(a.status, 200, `${act} ${JSON.stringify(a.body)}`);
    const b = await IB(act, body, k);
    assert.equal(b.status, 200, `${act} تکرار ${JSON.stringify(b.body)}`);
    assert.equal(b.headers.get('idempotent-replayed'), 'true', act);
    assert.deepEqual(b.body, a.body, act);
    assert.equal((await audits(action, id)).length, 1, `${act}: یک سابقه`);
    const c = await IB(act, { ...body, reason: 'دلیلِ دیگر' }, k);
    assert.equal(c.status, 409, act);
    assert.equal(c.body.error.code, 'idempotency_key_reused', act);
  }
  const id2 = await pendingImage('رد');
  const k = key('reject');
  const r1 = await A('cw-arshad', 'POST', `/image-bank/images/${id2}/reject`, { reason: 'نورِ بد' }, withKey(k));
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  const r2 = await A('cw-arshad', 'POST', `/image-bank/images/${id2}/reject`, { reason: 'نورِ بد' }, withKey(k));
  assert.equal(r2.status, 200, 'تکرار ۴۰۹ ِ not_pending نمی‌گیرد');
  assert.equal(r2.headers.get('idempotent-replayed'), 'true');
  assert.equal((await audits('admin.bank_image_rejected', id2)).length, 1);
});

/* ── ۵) پشتیبانِ دستی ────────────────────────────────────────────────── */

test('POST /admin/backups با کلید ⇒ یک pg_dump: تکرار ۲۰۱ ِ همان پاسخ؛ هم‌زمان ⇒ یک اجرا؛ نوعِ دیگر با همان کلید ⇒ ۴۰۹', async () => {
  const backup = require('../src/lib/backup');
  const real = backup.run;
  let calls = 0;
  backup.run = async ({ kind }) => {
    calls += 1;
    await new Promise((r) => setTimeout(r, 150));
    return { kind, file: `/tmp/fake-${calls}.sql.gz`, bytes: 10, createdAt: now(), encrypted: false };
  };
  try {
    const before = (await many(`SELECT 1 FROM audit_logs WHERE action='admin.backup_created'`)).length;
    const k = key('bk');
    const a = await A('cw-arshad', 'POST', '/backups', { kind: 'manual' }, withKey(k));
    assert.equal(a.status, 201, JSON.stringify(a.body));
    const b = await A('cw-arshad', 'POST', '/backups', { kind: 'manual' }, withKey(k));
    assert.equal(b.status, 201);
    assert.equal(b.headers.get('idempotent-replayed'), 'true');
    assert.deepEqual(b.body, a.body);
    assert.equal(calls, 1);
    const other = await A('cw-arshad', 'POST', '/backups', { kind: 'weekly' }, withKey(k));
    assert.equal(other.status, 409);
    assert.equal(other.body.error.code, 'idempotency_key_reused');

    const k2 = key('bkpar');
    const par = await Promise.all([1, 2, 3].map(() => A('cw-arshad', 'POST', '/backups', { kind: 'manual' }, withKey(k2))));
    assert.equal(calls, 2, `هم‌زمان فقط یک اجرا: ${JSON.stringify(par.map((r) => [r.status, r.body?.error?.code]))}`);
    for (const r of par) {
      if (r.status === 201) continue;
      assert.equal(r.status, 409);
      assert.equal(r.body.error.code, 'idempotency_in_progress');
    }
    assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='admin.backup_created'`)).length, before + 2);
    assert.equal((await A('cw-modir', 'POST', '/backups', { kind: 'manual' }, withKey(key()))).status, 403);
    //  بی کلید: همان رفتارِ پیشین (کنسولِ وبِ خودِ سرور کلید نمی‌فرستد)
    assert.equal((await A('cw-arshad', 'POST', '/backups', { kind: 'manual' })).status, 201);
    assert.equal(calls, 3);
  } finally {
    backup.run = real;
  }
});

test('POST /admin/backups با کلید روی پشتیبانِ واقعی (pg_dump یا PGlite): ۲۰۱ و تکرارِ بی فایلِ دوم', async () => {
  const k = key('bkreal');
  const a = await A('cw-arshad', 'POST', '/backups', { kind: 'manual' }, withKey(k));
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await A('cw-arshad', 'POST', '/backups', { kind: 'manual' }, withKey(k));
  assert.equal(b.headers.get('idempotent-replayed'), 'true');
  assert.equal(b.body.file, a.body.file);
});

/* ── ۶) صفحه‌بندیِ staff-perms ───────────────────────────────────────── */

test('GET staff-perms: بی پارامتر همان پاسخِ امروز (بی `page`)؛ با limit/offset صفحه‌های پایدار با سقفِ ۲۰۰', async () => {
  const t = await team('صفحه', 4);
  const P = (qs = '') => A('cw-arshad', 'GET', `/shops/${t.shopId}/staff-perms${qs}`);
  const full = await P();
  assert.equal(full.status, 200, JSON.stringify(full.body));
  assert.deepEqual(Object.keys(full.body).sort(), ['devices', 'perms', 'shop']);
  assert.equal(full.body.perms.members.length, 5);
  const devTotal = full.body.devices.length;
  assert.ok(devTotal >= 5);

  const first = await P('?limit=2');
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.equal(first.body.perms.members.length, 2);
  assert.equal(first.body.devices.length, 2);
  assert.deepEqual(first.body.page, { limit: 2, offset: 0, membersTotal: 5, devicesTotal: devTotal, nextOffset: 2 });
  assert.deepEqual(first.body.perms.catalog, full.body.perms.catalog, 'فهرستِ مجوز و لایهٔ نقش در هر صفحه کامل');
  assert.deepEqual(first.body.perms.roles, full.body.perms.roles);
  assert.equal(first.body.perms.members[0].role, 'owner', 'صاحب همیشه اول');

  const members = [];
  const devices = [];
  for (let off = 0, guard = 0; off !== null && guard < 10; guard++) {
    const r = await P(`?limit=2&offset=${off}`);
    members.push(...r.body.perms.members.map((m) => m.userId));
    devices.push(...r.body.devices.map((d) => d.id));
    off = r.body.page.nextOffset;
  }
  assert.deepEqual([...members].sort(), full.body.perms.members.map((m) => m.userId).sort(), 'همهٔ اعضا، بی تکرار');
  assert.deepEqual([...devices].sort(), full.body.devices.map((d) => d.id).sort(), 'همهٔ دستگاه‌ها، بی تکرار');

  const past = await P('?offset=50');
  assert.equal(past.status, 200);
  assert.equal(past.body.perms.members.length, 0);
  assert.equal(past.body.devices.length, 0);
  assert.equal(past.body.page.limit, 50, 'فقط offset ⇒ limit ِ پیش‌فرض');
  assert.equal(past.body.page.nextOffset, null);
  for (const qs of ['?limit=201', '?limit=0', '?limit=abc', '?offset=-1', '?limit=1&limit=2']) {
    assert.equal((await P(qs)).status, 400, qs);
  }
  assert.equal((await P('?limit=200')).status, 200);
});

/* ── ۷) توانایی‌ها و مهاجرت ──────────────────────────────────────────── */

test('GET /admin/me و /admin/contract: staff-perms.v1، image-bank.v1 و idempotent-writes.v1؛ درِ enable در قرارداد', async () => {
  const me = await A('cw-binande', 'GET', '/me');
  for (const c of ['staff-perms.v1', 'image-bank.v1', 'idempotent-writes.v1', 'sync-restore-once.v1']) {
    assert.ok(me.body.capabilities.includes(c), c);
  }
  const contract = await A('cw-arshad', 'GET', '/contract');
  assert.equal(contract.status, 200);
  const r = contract.body.routes.find((x) => x.method === 'POST' && x.path === '/api/admin/shops/:id/staff-perms/devices/:did/enable');
  assert.ok(r, 'درِ تازه در قرارداد');
  assert.equal(r.superadmin, true);
});

test('مهاجرتِ ۵۰۰: برگشت فقط جدولِ نشان و ردیفِ schema_migrations ِ خودش را برمی‌دارد', async () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '500_admin_write_claims.sql';
  const mark = await one('SELECT * FROM schema_migrations WHERE version=$1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  const has = async (t) => (await query('SELECT 1 FROM information_schema.tables WHERE table_name=$1', [t])).rows.length;
  const keys = Number((await one('SELECT COUNT(*)::int AS n FROM idempotency_keys')).n);
  await query(fs.readFileSync(path.join(dir, 'rollback', '500_admin_write_claims.down.sql'), 'utf8'));
  assert.equal(await has('admin_write_claims'), 0);
  assert.equal(await one('SELECT 1 FROM schema_migrations WHERE version=$1', [name]), null);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM idempotency_keys')).n), keys, 'کلیدهای ثبت‌شده دست نخوردند');
  await query(fs.readFileSync(path.join(dir, name), 'utf8'));
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has('admin_write_claims'), 1);
});
