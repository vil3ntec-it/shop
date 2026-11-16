'use strict';
/**
 * نسخهٔ برنامهٔ هر دستگاهِ دکان (مهاجرتِ ۹۸۰، `lib/device-app-version.js`) در `GET /api/shop/devices` ⇒ `appVersion`.
 *
 *   ۱) `X-App-Version` ِ هر درخواستِ دکان ⇒ تازه‌ترین نسخهٔ همان دستگاه، در فهرستِ صاحب؛
 *   ۲) سرآیندِ ناجور (متن، اسکریپت، رقمِ فارسی، بلند) نوشته نمی‌شود و خودِ درخواست رد نمی‌شود؛
 *   ۳) ساختِ کهنه که سرآیند نمی‌فرستد همان کار را می‌کند و `appVersion: null` («نامعلوم»)؛
 *   ۴) مرزِ دسترسی همان پیشین: صاحب و مدیر (`members.view`)، نه شاگرد، نه بی توکن، نه دکانِ دیگر؛
 *   ۵) توکنِ پمپ این ستون را هرگز نمی‌نویسد (پرسشِ پمپ همان پیشین).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query } = require('../src/db');
const dav = require('../src/lib/device-app-version');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

/** دکان با صاحب، یک مدیر و یک شاگرد — از راهِ واقعی */
async function shopWithTeam(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const manager = await h.newUser(`مدیر ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: manager.accessToken })).status));
  return { owner, staff, manager };
}

const withVersion = (u, v) => h.get('/api/shop', { token: u.accessToken, headers: { 'X-App-Version': v } });
const deviceOf = async (owner, user) => {
  const r = await h.get('/api/shop/devices', { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const userId = (await h.get('/api/me', { token: user.accessToken })).body.user.id;
  return r.body.devices.find((d) => d.member.userId === userId);
};
/** نوشتنِ میان‌افزار پس از پاسخ است (`.catch` ِ بی‌انتظار) ⇒ تا رسیدنش صبر */
async function settle(owner, user, want) {
  for (let i = 0; i < 40; i++) {
    const d = await deviceOf(owner, user);
    if (d && d.appVersion === want) return d;
    await new Promise((r) => setTimeout(r, 25));
  }
  return deviceOf(owner, user);
}

test('شکلِ نسخه: رقم و نقطه با پسوندِ کوتاه؛ بقیه هیچ', () => {
  for (const ok of ['3.2.615', '1.0.366', '3.2', '3.2.615-preview', 'v1.0.12', ' 3.2.700 ']) assert.ok(dav.clean(ok), ok);
  assert.equal(dav.clean('v1.0.12'), '1.0.12');
  for (const bad of [undefined, null, 42, '', 'abc', '<script>', '3.2.615; DROP TABLE devices', '۳.۲.۶۱۵', '3', '3.2.615 beta',
    '3.2.615-' + 'x'.repeat(17), '1.' + '2'.repeat(40), '3.2.615\u0000', '3..2']) {
    assert.equal(dav.clean(bad), '', String(bad));
  }
  assert.equal(dav.clean('1234.1234567.1234567.1234567-abcdefghijklmnop'), '', 'شکلِ درست ولی بیش از ۳۲ نویسه');
});

test('تازه‌ترین نسخهٔ هر دستگاه در فهرستِ صاحب؛ ساختِ بی سرآیند «نامعلوم» و سالم', async () => {
  const t = await shopWithTeam('نسخه');
  //  ساختِ کهنه: هیچ سرآیندی ⇒ همان پاسخ، و دستگاه بی نسخه
  assert.equal((await h.get('/api/shop', { token: t.staff.accessToken })).status, 200);
  const before = await deviceOf(t.owner, t.staff);
  assert.ok(before, 'دستگاهِ شاگرد در فهرست هست');
  assert.equal(before.appVersion, null);
  //  کلیدهای پیشین همه هستند (افزودنی)
  for (const k of ['id', 'name', 'platform', 'status', 'createdAt', 'lastSeenAt', 'disabledAt', 'blockedAttempts', 'lastBlockedAt', 'member', 'current']) {
    assert.ok(k in before, k);
  }

  assert.equal((await withVersion(t.staff, '3.2.615')).status, 200);
  assert.equal((await settle(t.owner, t.staff, '3.2.615')).appVersion, '3.2.615');
  //  به‌روز شد ⇒ همان ردیف، نسخهٔ تازه
  assert.equal((await withVersion(t.staff, 'v3.2.640')).status, 200);
  assert.equal((await settle(t.owner, t.staff, '3.2.640')).appVersion, '3.2.640');
  //  کامپیوترِ مدیر
  await withVersion(t.manager, '1.0.366');
  assert.equal((await settle(t.owner, t.manager, '1.0.366')).appVersion, '1.0.366');
});

test('سرآیندِ ناجور نوشته نمی‌شود و درخواست رد نمی‌شود', async () => {
  const t = await shopWithTeam('ناجور');
  await withVersion(t.staff, '3.2.600');
  await settle(t.owner, t.staff, '3.2.600');
    //  (رقمِ فارسی در سرآیندِ HTTP نمی‌رود — fetch خودش رد می‌کند؛ همان در آزمونِ `clean` بالا)
  for (const bad of ['<script>alert(1)</script>', '3.2.601; DROP TABLE devices', 'x'.repeat(300), '3.2.603 (beta)', '3.2.604-' + 'y'.repeat(30)]) {
    const r = await withVersion(t.staff, bad);
    assert.equal(r.status, 200, `${bad}: ${JSON.stringify(r.body)}`);
  }
  await new Promise((r) => setTimeout(r, 150));
  assert.equal((await deviceOf(t.owner, t.staff)).appVersion, '3.2.600', 'مقدارِ پیشین ماند');
  const raw = (await query(`SELECT app_version FROM devices WHERE app_version <> '' AND app_version !~ '^[0-9.A-Za-z-]+$'`)).rows;
  assert.deepEqual(raw, []);
});

test('مرزِ دسترسیِ فهرست همان پیشین: صاحب و مدیر، نه شاگرد، نه بی توکن، نه دکانِ دیگر', async () => {
  const t = await shopWithTeam('مرز');
  const other = await shopWithTeam('دیگر');
  await withVersion(t.staff, '3.2.611');
  assert.equal((await h.get('/api/shop/devices', { token: t.owner.accessToken })).status, 200);
  assert.equal((await h.get('/api/shop/devices', { token: t.manager.accessToken })).status, 200);
  const staff = await h.get('/api/shop/devices', { token: t.staff.accessToken, headers: { 'X-App-Version': '3.2.611' } });
  assert.equal(staff.status, 403);
  assert.equal((await h.get('/api/shop/devices')).status, 401);
  //  دکانِ دیگر دستگاه‌های این دکان را نمی‌بیند (نه نسخه‌شان را)
  const theirs = (await h.get('/api/shop/devices', { token: other.owner.accessToken })).body.devices;
  const staffId = (await h.get('/api/me', { token: t.staff.accessToken })).body.user.id;
  assert.equal(theirs.filter((d) => d.member.userId === staffId).length, 0);
});

test('توکنِ پمپ نسخه را روی دستگاه نمی‌نویسد', async () => {
  const p = await h.newUser('پمپ‌دار', 'pump');
  //  `/api/portal/me` هر بخشی را می‌پذیرد (`requireAnyUser`) ⇒ بخش از خودِ توکن: پمپ
  const r = await h.get('/api/portal/me', { token: p.accessToken, headers: { 'X-App-Version': '9.9.999' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await new Promise((res) => setTimeout(res, 150));
  const rows = (await query(`SELECT app_version FROM devices WHERE app_version = '9.9.999'`)).rows;
  assert.equal(rows.length, 0);
});

test('مهاجرتِ ۹۸۰ بازگشت دارد و فقط همان ستون را برمی‌دارد', () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const up = fs.readFileSync(path.join(dir, '980_device_app_version.sql'), 'utf8');
  const down = fs.readFileSync(path.join(dir, 'rollback', '980_device_app_version.down.sql'), 'utf8');
  assert.match(up, /ALTER TABLE devices ADD COLUMN IF NOT EXISTS app_version text NOT NULL DEFAULT ''/);
  assert.match(down, /ALTER TABLE devices DROP COLUMN IF EXISTS app_version/);
  assert.match(down, /DELETE FROM schema_migrations WHERE version = '980_device_app_version\.sql'/);
});
