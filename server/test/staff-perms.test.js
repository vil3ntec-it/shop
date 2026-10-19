'use strict';
/**
 * کارمندان و مجوزها — STF-01..03، STF-10 و STF-11 (سناریوهای S44 و S45).
 *
 * آزمونِ مرجع اول: بی هیچ لایه، فهرستِ مجوزِ صاحب، مدیر و شاگرد همان
 * امروز است. بعد لایه‌ها فقط می‌بندند، دستگاهِ گم‌شده خطای روشن می‌گیرد و
 * کدِ ثابتِ شاگرد خاموش و دوباره روشن می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { permissionsOf } = require('../src/lib/permissions');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'perm-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

/** دکان با صاحب، یک مدیر و یک شاگرد — هر سه از راهِ واقعی */
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

test('آزمونِ مرجع: بی لایه، فهرستِ مجوزِ هر نقش در /api/shop و /api/me همان امروز است', async () => {
  const t = await shopWithTeam('مرجع');
  for (const [role, u] of [['owner', t.owner], ['manager', t.manager], ['staff', t.staff]]) {
    const shop = await h.get('/api/shop', { token: u.accessToken });
    assert.equal(shop.body.role, role);
    assert.deepEqual(shop.body.permissions, permissionsOf(role), `shop ${role}`);
    const me = await h.get('/api/me', { token: u.accessToken });
    assert.deepEqual(me.body.permissions, permissionsOf(role), `me ${role}`);
    assert.equal(shop.body.policy.v, 1);
    assert.equal(shop.body.policy.effective['reports.money'].mode, role === 'staff' ? 'deny' : 'allow');
  }
  //  و درهای امروز همان‌طور: مدیر تنظیمات می‌نویسد، شاگرد نه
  assert.equal((await h.put('/api/shop/settings', { settings: { a: 1 } }, { token: t.manager.accessToken })).status, 200);
  assert.equal((await h.put('/api/shop/settings', { settings: { a: 1 } }, { token: t.staff.accessToken })).status, 403);
  assert.equal((await h.get('/api/shop/audit', { token: t.manager.accessToken })).status, 200);
});

test('لایهٔ نقش می‌بندد و سرور اجرایش می‌کند؛ لایهٔ کاربر برمی‌گرداند (S45)', async () => {
  const t = await shopWithTeam('بستن');
  const put = await h.put('/api/shop/permissions/roles/manager',
    { perms: { 'settings.write': { mode: 'deny' }, 'audit.view': { mode: 'deny' } } }, { token: t.owner.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));

  const denied = await h.put('/api/shop/settings', { settings: { a: 2 } }, { token: t.manager.accessToken });
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'permission_denied');
  assert.equal((await h.get('/api/shop/audit', { token: t.manager.accessToken })).status, 403);
  const shop = await h.get('/api/shop', { token: t.manager.accessToken });
  assert.ok(!shop.body.permissions.includes('settings.write'));
  assert.equal(shop.body.policy.effective['settings.write'].mode, 'deny');

  //  صاحب برای همین یک مدیر دوباره باز می‌کند
  const mid = (await h.get('/api/shop/permissions', { token: t.owner.accessToken })).body.shop.members
    .find((m) => m.role === 'manager').userId;
  const one = await h.put(`/api/shop/permissions/members/${mid}`,
    { perms: { 'settings.write': { mode: 'allow' } } }, { token: t.owner.accessToken });
  assert.equal(one.status, 200, JSON.stringify(one.body));
  assert.equal((await h.put('/api/shop/settings', { settings: { a: 3 } }, { token: t.manager.accessToken })).status, 200);

  //  صاحب خودش هرگز بسته نمی‌شود
  assert.equal((await h.put('/api/shop/settings', { settings: { a: 4 } }, { token: t.owner.accessToken })).status, 200);

  //  لایهٔ خالی = برگشت به امروز
  await h.put('/api/shop/permissions/roles/manager', { perms: {} }, { token: t.owner.accessToken });
  assert.equal((await h.get('/api/shop/audit', { token: t.manager.accessToken })).status, 200);
});

test('هیچ لایه‌ای بیش از نقش نمی‌دهد و چیزِ اجرانشدنی پذیرفته نمی‌شود (AUTH-09)', async () => {
  const t = await shopWithTeam('سقف');
  const tok = { token: t.owner.accessToken };
  const cases = [
    ['/api/shop/permissions/roles/staff', { 'settings.write': { mode: 'allow' } }, 'exceeds_role'],
    //  تخفیف و لغو از ۱۴۰۵/۰۷/۲۷ در سرور اجرا می‌شوند (staff-approval.test.js)؛ ولی شاگرد «مجاز» نمی‌گیرد
    ['/api/shop/permissions/roles/staff', { 'sales.discount': { mode: 'allow' } }, 'exceeds_role'],
    ['/api/shop/permissions/roles/staff', { 'sales.cancel': { mode: 'allow' } }, 'exceeds_role'],
    ['/api/shop/permissions/roles/manager', { 'returns.create': { caps: { maxPct: 5 } } }, 'unknown_cap'],
    ['/api/shop/permissions/roles/manager', { 'settings.write': { mode: 'approve' } }, 'not_approvable'],
    ['/api/shop/permissions/roles/manager', { 'data.write': { mode: 'deny' } }, 'unknown_permission'],
    ['/api/shop/permissions/roles/owner', { 'settings.write': { mode: 'deny' } }, 'owner_fixed'],
    ['/api/shop/permissions/roles/cashier', { 'settings.write': { mode: 'deny' } }, 'unknown_role'],
    ['/api/shop/permissions/roles/manager', { 'settings.write': 'deny' }, 'bad_override'],
  ];
  for (const [url, perms, code] of cases) {
    const r = await h.put(url, { perms }, tok);
    assert.equal(r.status, 400, `${url} ${JSON.stringify(perms)}`);
    assert.equal(r.body.error.code, code, `${url} ${JSON.stringify(perms)}`);
  }
  //  کلیدهای پولی حالا که سرور اجرایشان می‌کند ذخیره می‌شوند
  for (const perms of [{ 'sales.discount': { caps: { maxPct: 5 } } }, { 'sales.cancel': { mode: 'deny' } }]) {
    assert.equal((await h.put('/api/shop/permissions/roles/manager', { perms }, tok)).status, 200, JSON.stringify(perms));
  }
  assert.equal((await h.put('/api/shop/permissions/roles/manager', { perms: {} }, tok)).status, 200);
  //  شاگرد با «بدنهٔ دست‌ساز» مجوز نمی‌گیرد و مدیر هم لایه نمی‌نویسد
  const sid = (await h.get('/api/shop/permissions', tok)).body.shop.members.find((m) => m.role === 'staff').userId;
  const grant = await h.put(`/api/shop/permissions/members/${sid}`, { perms: { 'audit.view': { mode: 'allow' } } }, tok);
  assert.equal(grant.body.error.code, 'exceeds_role');
  assert.equal((await h.put('/api/shop/permissions/roles/staff', { perms: {} }, { token: t.manager.accessToken })).status, 403);
  assert.equal((await h.put('/api/shop/permissions/roles/staff', { perms: {} }, { token: t.staff.accessToken })).status, 403);
  //  شاگرد فقط مجوزهای خودش را می‌بیند، نه لایه‌های دیگران
  const mine = await h.get('/api/shop/permissions', { token: t.staff.accessToken });
  assert.equal(mine.status, 200);
  assert.equal(mine.body.shop, undefined);

  //  نمایشِ عددهای مالی برای یک شاگرد: «full» و فقط نمایشی
  const money = await h.put(`/api/shop/permissions/members/${sid}`, { perms: { 'reports.money': { mode: 'allow' } } }, tok);
  assert.equal(money.status, 200, JSON.stringify(money.body));
  const staffShop = await h.get('/api/shop', { token: t.staff.accessToken });
  assert.equal(staffShop.body.policy.effective['reports.money'].mode, 'allow');
  assert.deepEqual(staffShop.body.permissions, permissionsOf('staff'));
});

test('لایه‌ها مالِ همان دکان‌اند (دکانِ دیگر نه)', async () => {
  const a = await shopWithTeam('الف');
  const b = await shopWithTeam('ب');
  const aStaff = (await h.get('/api/shop/permissions', { token: a.owner.accessToken })).body.shop.members
    .find((m) => m.role === 'staff').userId;
  const r = await h.put(`/api/shop/permissions/members/${aStaff}`, { perms: { 'reports.money': { mode: 'allow' } } }, { token: b.owner.accessToken });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'member_not_found');
});

test('دستگاهِ گم‌شده: نشست باطل، خطای روشن، تلاش‌ها شمرده (STF-10، S44)', async () => {
  const t = await shopWithTeam('دستگاه');
  const list = await h.get('/api/shop/devices', { token: t.owner.accessToken });
  assert.equal(list.status, 200);
  const staffId = (await h.get('/api/me', { token: t.staff.accessToken })).body.user.id;
  const dev = list.body.devices.find((d) => d.member.userId === staffId);
  assert.ok(dev, 'دستگاهِ شاگرد در فهرست هست');
  const mine = list.body.devices.find((d) => d.current);
  assert.ok(mine, 'دستگاهِ همین درخواست نشان خورده');

  //  شاگرد و مدیر نمی‌توانند؛ صاحب دستگاهِ خودش را نه
  assert.equal((await h.post(`/api/shop/devices/${dev.id}/disable`, {}, { token: t.manager.accessToken })).status, 403);
  const self = await h.post(`/api/shop/devices/${mine.id}/disable`, {}, { token: t.owner.accessToken });
  assert.equal(self.body.error.code, 'current_device');

  const off = await h.post(`/api/shop/devices/${dev.id}/disable`, { reason: 'گم شد' }, { token: t.owner.accessToken });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.ok(off.body.sessionsRevoked >= 1);

  const api = await h.get('/api/shop', { token: t.staff.accessToken });
  assert.equal(api.status, 403);
  assert.equal(api.body.error.code, 'device_disabled');
  const sync = await h.get('/api/sync/v1/status', { token: t.staff.accessToken });
  assert.equal(sync.status, 403);
  assert.equal(sync.body.error.code, 'device_disabled');
  const push = await h.post('/api/sync/v1/push', { ops: [] }, { token: t.staff.accessToken });
  assert.equal(push.body.error.code, 'device_disabled');

  const after = (await h.get('/api/shop/devices', { token: t.owner.accessToken })).body.devices.find((d) => d.id === dev.id);
  assert.equal(after.status, 'revoked');
  assert.ok(after.disabledAt);
  assert.ok(after.blockedAttempts >= 3, `شمرده شد: ${after.blockedAttempts}`);

  //  ورودِ دوباره روی همان دستگاه هم به همان در می‌خورد
  const uid = (await query('SELECT device_uid FROM devices WHERE id=$1', [dev.id])).rows[0].device_uid;
  const again = await h.post('/api/auth/login', {
    identifier: t.staff.email, password: t.staff.password, app: 'shop',
    device: { deviceId: uid, name: 'تست', platform: 'test' },
  });
  //  (خودِ مسیرِ ورود مالِ کارِ «ورود و نشست» است و امروز توکن می‌دهد؛
  //  ولی آن توکن روی همین دستگاه به هیچ دری نمی‌رسد)
  assert.equal(again.status, 200);
  const r = await h.get('/api/shop', { token: again.body.accessToken });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'device_disabled');

  //  دکانِ دیگر به این دستگاه دست ندارد
  const other = await shopWithTeam('دیگر');
  const foreign = await h.post(`/api/shop/devices/${dev.id}/enable`, {}, { token: other.owner.accessToken });
  assert.equal(foreign.status, 404);

  //  پیدا شد: برمی‌گردد، ولی نشست‌های قبلی نه — دوباره وارد می‌شود
  assert.equal((await h.post(`/api/shop/devices/${dev.id}/enable`, {}, { token: t.owner.accessToken })).status, 200);
  assert.equal((await h.get('/api/shop', { token: t.staff.accessToken })).status, 401);
  const fresh = await h.post('/api/auth/login', {
    identifier: t.staff.email, password: t.staff.password, app: 'shop',
    device: { deviceId: uid, name: 'تست', platform: 'test' },
  });
  assert.equal(fresh.status, 200, JSON.stringify(fresh.body));
  assert.equal((await h.get('/api/shop', { token: fresh.body.accessToken })).status, 200);
});

test('آزمونِ مرجع: دستگاهی که خودِ کاربر لغو کرده همان device_revoked/۴۰۱ِ امروز را می‌گیرد', async () => {
  const u = await h.newUser('خودش');
  const devs = (await h.get('/api/me/devices', { token: u.accessToken })).body.devices;
  assert.equal(devs.length, 1);
  await h.del(`/api/me/devices/${devs[0].id}`, { token: u.accessToken });
  const r = await h.get('/api/me', { token: u.accessToken });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'invalid_token');
});

test('کدِ ثابتِ شاگرد: خاموش، بی ساختِ خودکار، و روشن با چرخش (STF-11)', async () => {
  const owner = await h.newUser('صاحبِ کد');
  await h.post('/api/shop', { name: 'دکانِ کد' }, { token: owner.accessToken });
  const tok = { token: owner.accessToken };
  const first = (await h.get('/api/shop/staff-code', tok)).body;
  assert.equal(first.disabled, false);

  //  آزمونِ مرجع: باطل کردنِ کدِ ثابت با شناسه‌اش، مثلِ امروز، فقط کدِ تازه می‌سازد
  assert.equal((await h.del(`/api/shop/staff-codes/${first.id}`, tok)).status, 200);
  const second = (await h.get('/api/shop/staff-code', tok)).body;
  assert.ok(second.code && second.code !== first.code);
  assert.equal(second.generation, first.generation + 1);

  const off = await h.post('/api/shop/staff-code/disable', {}, tok);
  assert.equal(off.status, 200);
  assert.equal(off.body.disabled, true);
  const still = (await h.get('/api/shop/staff-code', tok)).body;
  assert.equal(still.code, null);
  assert.equal(still.disabled, true);
  const staff = await h.newUser('شاگردِ دیر');
  const join = await h.post('/api/shop/join', { code: second.code }, { token: staff.accessToken });
  assert.ok(join.status >= 400, 'کدِ خاموش کسی را وارد نمی‌کند');
  //  دوباره زدن آسیبی ندارد
  assert.equal((await h.post('/api/shop/staff-code/disable', {}, tok)).status, 200);

  const rot = await h.post('/api/shop/staff-code/rotate', {}, tok);
  assert.equal(rot.status, 200, JSON.stringify(rot.body));
  assert.ok(rot.body.code && rot.body.code !== second.code);
  const back = (await h.get('/api/shop/staff-code', tok)).body;
  assert.equal(back.code, rot.body.code);
  assert.equal(back.disabled, false);
  const join2 = await h.post('/api/shop/join', { code: back.code }, { token: staff.accessToken });
  assert.ok([200, 201].includes(join2.status));
});

test('پنلِ مدیر: خواندن و نوشتنِ لایه با همان سنجش', async () => {
  const t = await shopWithTeam('پنل');
  const shopId = (await h.get('/api/shop', { token: t.owner.accessToken })).body.shop.id;
  const admin = (await h.post('/api/admin/login', { username: 'perm-admin', password: 'Admin!12345' })).body.token;
  const g = await h.get(`/api/admin/shops/${shopId}/staff-perms`, { token: admin });
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.equal(g.body.perms.members.length, 3);
  assert.ok(g.body.devices.length >= 3);
  const bad = await h.put(`/api/admin/shops/${shopId}/staff-perms/roles/staff`, { perms: { 'settings.write': { mode: 'allow' } } }, { token: admin });
  assert.equal(bad.body.error.code, 'exceeds_role');
  const ok = await h.put(`/api/admin/shops/${shopId}/staff-perms/roles/manager`, { perms: { 'audit.view': { mode: 'deny' } } }, { token: admin });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await h.get('/api/shop/audit', { token: t.manager.accessToken })).status, 403);
});
