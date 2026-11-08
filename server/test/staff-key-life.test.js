'use strict';
/**
 * ⛔ عمرِ رازِ دستگاهِ شاگرد (پس از #1002، مهاجرتِ ۳۴۵، `lib/staff-device-keys.js`).
 *
 * وقتی اعتماد تمام شد سرور راز را درونِ همان تراکنشِ کارِ اصلی باطل می‌کند و هر بار سابقه‌ای بی راز می‌نویسد:
 *   - «خروج از همه» (خودِ شاگرد: `POST /api/auth/logout-all`؛ صاحب: `POST /api/shop/staff-keys/members/:id/logout-all`)؛
 *   - برداشتن یا تعلیقِ عضو؛ باطل کردنِ کدِ شماره‌دارِ خودِ او؛ حذفِ حساب؛
 *   - «باطل کردنِ این دستگاه» ِ صاحب (`POST /api/shop/staff-keys/:id/revoke`) که نشست‌های همان دستگاه را هم می‌بندد.
 * پس از آن رازِ پیشین ۴۰۳ می‌گیرد؛ کدِ تازهٔ صاحب **عضوِ تازه** می‌سازد، هرگز همان؛ صاحب نشانهٔ «شاید همان <نام>»
 * را روی عضوِ تازه می‌بیند (هرگز یکی شدنِ خودکار). هر راه دوباره‌زدنی است؛ رازِ باطل برنمی‌گردد (پایگاه هم نمی‌گذارد).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const staffCodes = require('../src/lib/staff-codes');
const deviceKeys = require('../src/lib/staff-device-keys');

let owner;
let shopId;
let standing;
let admin;
const issued = [];   //  هر رازی که سرور داد — هیچ سابقه‌ای نباید آن یا HMAC اش را داشته باشد

const login = async (code, device, name) => {
  const r = await h.post('/api/auth/staff', { code, ...(name ? { name } : {}), device });
  if (r.body && typeof r.body.deviceKey === 'string') issued.push(r.body.deviceKey);
  return r;
};
const revokedAudits = (since, sid = shopId) => many(
  `SELECT * FROM audit_logs WHERE action='staff_device_key.revoked' AND shop_id=$1 AND created_at >= $2 ORDER BY created_at`,
  [sid, since]);
const keyRows = (userId) => many('SELECT * FROM staff_device_keys WHERE user_id=$1 ORDER BY created_at', [userId]);
const memberOf = async (userId, sid = shopId) => one('SELECT * FROM shop_members WHERE shop_id=$1 AND user_id=$2', [sid, userId]);

async function newShop(name) {
  const o = await h.newUser(`صاحبِ ${name}`);
  const id = (await h.post('/api/shop', { name }, { token: o.accessToken })).body.shop.id;
  await h.post('/api/admin/subscriptions', { shopId: id, plan: 'm1', days: 30 }, { token: admin });
  await query('UPDATE shops SET max_members=50 WHERE id=$1', [id]);
  return { owner: o, shopId: id };
}

/** شاگردِ برنامهٔ تازه با کدِ یک‌بارِ شماره‌دارِ خودش (یا کدِ داده‌شده) روی دستگاهِ `uid` */
async function staff(uid, name, { role = 'staff', code = null, sid = shopId, by = owner } = {}) {
  const made = code ? null : await staffCodes.create(sid, by.user.id, { role, maxUses: 1 });
  const r = await login(code || made.code, { uid, name: `دستگاهِ ${name}`, platform: 'android', keyVersion: 1 }, name);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(typeof r.body.deviceKey, 'string', 'برنامهٔ تازه رازِ دستگاه نگرفت');
  const member = await memberOf(r.body.user.id, sid);
  return { uid, name, code: code || made.code, codeId: made ? made.id : '', key: r.body.deviceKey, user: r.body.user, member, session: r.body };
}

/** همان دستگاه با رازِ پیشین و کدِ داده‌شده */
const back = (s, code = s.code) => login(code, { uid: s.uid, key: s.key, keyVersion: 1 });

/** رازِ پیشین دیگر هیچ حسابی نمی‌دهد: ۴۰۳ ِ «کدِ تازه بگیرید» یا ردِ کد، و هرگز حسابِ پیشین */
function refused(r, s, what) {
  assert.equal(r.status, 403, `${what}: ${JSON.stringify(r.body)}`);
  assert.ok(['staff_device_unverified', 'code_used', 'code_inactive'].includes(r.body.error.code), `${what}: ${r.body.error.code}`);
  assert.notEqual(r.body.user?.id, s.user.id);
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  ({ owner, shopId } = await newShop('دکانِ عمرِ راز'));
  standing = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  assert.ok(standing);
});
test.after(async () => { await h.stop(); });

test('خروج از همه (خودِ شاگرد) ⇒ رازِ پیشین ۴۰۳؛ کدِ تازه ⇒ عضوِ تازه، نه همان؛ صاحب «شاید همان» را می‌بیند', async () => {
  const since = now();
  const s = await staff('life-phone-1', 'سمیر');
  //  پیش از خروج: همان دستگاه با رازش و کدِ ثابت برمی‌گردد (کدِ ثابت برای عضوِ فعال معتبر است)
  const ok = await back(s, standing);
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.user.id, s.user.id);

  const out = await h.post('/api/auth/logout-all', {}, { token: ok.body.accessToken });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.equal(out.body.deviceKeys, 1);
  assert.equal((await h.get('/api/shop/me', { token: ok.body.accessToken })).status, 401, 'نشست‌ها بسته شدند');
  const [row] = await keyRows(s.user.id);
  assert.ok(row.revoked_at, 'راز باطل شد');
  assert.equal(row.revoked_reason, 'logout_all');
  assert.equal(row.revoked_by, s.user.id);

  refused(await back(s, standing), s, 'رازِ پیشین + کدِ ثابت');
  refused(await back(s), s, 'رازِ پیشین + کدِ یک‌بارِ خودش');

  //  کدِ تازهٔ صاحب ⇒ عضوِ تازه (هرگز همان)؛ عضوِ کهنه با فروش و سابقه سرِ جایش
  const fresh = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  const again = await login(fresh.code, { uid: s.uid, key: s.key, name: 'دستگاهِ سمیر', keyVersion: 1 }, 'سمیر');
  assert.equal(again.status, 201, JSON.stringify(again.body));
  assert.notEqual(again.body.user.id, s.user.id, 'کدِ تازه نباید حسابِ پیشین را بدهد');
  assert.equal((await memberOf(s.user.id)).status, 'active', 'عضوِ کهنه خودبه‌خود برداشته یا یکی نمی‌شود');

  const view = await h.get('/api/shop/staff-keys', { token: owner.accessToken });
  assert.equal(view.status, 200, JSON.stringify(view.body));
  assert.equal(view.body.canRevoke, true);
  const newMember = await memberOf(again.body.user.id);
  const hint = view.body.hints.find((x) => x.memberId === newMember.id);
  assert.ok(hint, `نشانهٔ «شاید همان» نیامد: ${JSON.stringify(view.body.hints)}`);
  assert.equal(hint.oldMemberId, s.member.id);
  assert.equal(hint.oldUserId, s.user.id);
  assert.equal(hint.oldName, 'سمیر');
  assert.equal(hint.sameName, true);
  assert.equal(hint.device, 'life…ne-1');
  assert.ok(!view.body.hints.some((x) => x.memberId === s.member.id), 'نشانه فقط روی عضوِ تازه');
  //  دستگاهِ زنده فقط رازِ عضوِ تازه است؛ شناسهٔ کامل و راز هرگز در پاسخ
  const mine = view.body.devices.filter((d) => d.userId === again.body.user.id);
  assert.equal(mine.length, 1);
  assert.equal(mine[0].label, 'دستگاهِ سمیر');
  assert.ok(!view.body.devices.some((d) => d.userId === s.user.id), 'رازِ باطل در فهرست نمی‌آید');
  const text = JSON.stringify(view.body);
  assert.ok(!text.includes(s.uid) && !text.includes(s.key) && !text.includes(again.body.deviceKey), 'شناسهٔ کامل یا راز در پاسخ');

  //  برداشتنِ عضوِ کهنه (همان درِ امروز) ⇒ نشانه می‌رود؛ داده‌اش نمی‌رود
  assert.equal((await h.del(`/api/shop/members/${s.member.id}`, { token: owner.accessToken })).status, 200);
  assert.equal((await memberOf(s.user.id)).status, 'removed');
  assert.ok(await one('SELECT 1 AS x FROM users WHERE id=$1', [s.user.id]), 'حسابِ کهنه پاک نمی‌شود');
  const after = await h.get('/api/shop/staff-keys', { token: owner.accessToken });
  assert.ok(!after.body.hints.some((x) => x.memberId === newMember.id));

  const reasons = (await revokedAudits(since)).map((r) => r.detail.reason);
  assert.ok(reasons.includes('logout_all'), JSON.stringify(reasons));
});

test('برداشتن و تعلیقِ عضو ⇒ رازها باطل در همان کار؛ دوباره فعال شدن رازِ مرده را برنمی‌گرداند؛ دوباره‌زدنی', async () => {
  const since = now();
  const gone = await staff('life-phone-2', 'رحیم');
  assert.equal((await h.post(`/api/shop/members/${gone.member.id}/remove`, {}, { token: owner.accessToken })).status, 200);
  assert.equal((await keyRows(gone.user.id))[0].revoked_reason, 'member_removed');
  refused(await back(gone, standing), gone, 'پس از برداشتن');
  const n = (await revokedAudits(since)).length;
  //  دوباره ⇒ ۲۰۰، هیچ سابقهٔ تازه‌ای برای راز
  assert.equal((await h.post(`/api/shop/members/${gone.member.id}/remove`, {}, { token: owner.accessToken })).status, 200);
  assert.equal((await revokedAudits(since)).length, n);

  const paused = await staff('life-phone-3', 'نسیم');
  assert.equal((await h.patch(`/api/shop/members/${paused.member.id}`, { status: 'suspended' }, { token: owner.accessToken })).status, 200);
  assert.equal((await keyRows(paused.user.id))[0].revoked_reason, 'member_suspended');
  assert.equal((await h.patch(`/api/shop/members/${paused.member.id}`, { status: 'active' }, { token: owner.accessToken })).status, 200);
  refused(await back(paused, standing), paused, 'پس از تعلیق و دوباره فعال');
  assert.equal((await keyRows(paused.user.id)).filter((k) => !k.revoked_at).length, 0, 'رازِ مرده زنده نشد');
});

test('باطل کردنِ کدِ شماره‌دارِ خودِ عضو ⇒ رازش باطل؛ کدِ ثابت یا کدِ شماره‌دارِ دیگر رازِ کسی را نمی‌برد', async () => {
  const since = now();
  //  کدِ چندبار: هر که خرجش کرد مالِ اوست
  const multi = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 3 });
  const a = await staff('life-phone-4', 'عارف', { code: multi.code });
  const bystander = await staff('life-phone-5', 'بهار');
  const unused = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  assert.equal((await h.del(`/api/shop/staff-codes/${unused.id}`, { token: owner.accessToken })).status, 200);
  assert.equal((await revokedAudits(since)).length, 0, 'کدِ خرج‌نشده رازی نمی‌برد');

  assert.equal((await h.del(`/api/shop/staff-codes/${multi.id}`, { token: owner.accessToken })).status, 200);
  assert.equal((await keyRows(a.user.id))[0].revoked_reason, 'code_revoked');
  refused(await back(a, standing), a, 'پس از باطل شدنِ کدِ خودش، با کدِ ثابت');
  const kept = await back(bystander, standing);
  assert.equal(kept.status, 201, 'رازِ عضوِ دیگر دست نخورد');
  assert.equal(kept.body.user.id, bystander.user.id);
  const [row] = await revokedAudits(since);
  assert.equal(row.detail.invite, multi.id);
  assert.equal(row.target_id, a.user.id);
});

test('رازِ باطل از نشستِ باز برنمی‌گردد (`/auth/staff/device-key` ⇒ revoked)', async () => {
  //  برنامهٔ کهنه با کدِ شماره‌دارِ خودش، بعد رازش را از نشستِ باز می‌گیرد
  //  کدِ دوبار (پس از یک بار هنوز فعال و باطل‌کردنی؛ کدِ یک‌بارِ خرج‌شده «تمام‌شده» است و باطل‌کردنی نیست)
  const c = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 2 });
  const legacy = await login(c.code, { uid: 'life-legacy', name: 'کهنه' }, 'کاظم');
  assert.equal(legacy.status, 201, JSON.stringify(legacy.body));
  const got = await h.post('/api/auth/staff/device-key', {}, { token: legacy.body.accessToken });
  assert.equal(got.status, 201, JSON.stringify(got.body));
  issued.push(got.body.deviceKey);
  assert.equal((await h.del(`/api/shop/staff-codes/${c.id}`, { token: owner.accessToken })).status, 200);
  const again = await h.post('/api/auth/staff/device-key', {}, { token: legacy.body.accessToken });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.deviceKey, null);
  assert.equal(again.body.reason, 'revoked');
  assert.equal((await keyRows(legacy.body.user.id)).filter((k) => !k.revoked_at).length, 0);
});

test('صاحب: «باطل کردنِ این دستگاه» راز و نشست‌های همان دستگاه را می‌بندد؛ «خروج از همه» ِ عضو؛ هر دو دوباره‌زدنی', async () => {
  const s = await staff('life-phone-6', 'شبنم');
  const second = await back(s, standing);   //  نشستِ دوم روی همان دستگاه
  assert.equal(second.status, 201);
  const other = await staff('life-phone-7', 'شبنم-دوم');
  const list = await h.get('/api/shop/staff-keys', { token: owner.accessToken });
  const row = list.body.devices.find((d) => d.userId === s.user.id);
  assert.ok(row, JSON.stringify(list.body.devices));
  assert.equal(row.memberId, s.member.id);
  assert.equal(row.label, 'دستگاهِ شبنم');
  assert.equal(row.platform, 'android');
  assert.equal(row.device, 'life…ne-6');
  assert.ok(row.createdAt > 0);
  assert.ok(row.lastUsedAt >= row.createdAt, 'آخرین استفاده از ورودِ دوباره');

  const r = await h.post(`/api/shop/staff-keys/${row.id}/revoke`, {}, { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.revoked, 1);
  assert.ok(r.body.sessions >= 2, JSON.stringify(r.body));
  assert.equal((await h.get('/api/shop/me', { token: second.body.accessToken })).status, 401);
  assert.equal((await h.get('/api/shop/me', { token: other.session.accessToken })).status, 200, 'دستگاهِ دیگر دست نخورد');
  refused(await back(s, standing), s, 'پس از «باطل کردنِ این دستگاه»');
  const twice = await h.post(`/api/shop/staff-keys/${row.id}/revoke`, {}, { token: owner.accessToken });
  assert.equal(twice.status, 200);
  assert.equal(twice.body.revoked, 0);
  assert.ok(!(await h.get('/api/shop/staff-keys', { token: owner.accessToken })).body.devices.some((d) => d.id === row.id));
  assert.equal((await keyRows(s.user.id))[0].revoked_reason, 'owner_revoked');
  assert.equal((await keyRows(s.user.id))[0].revoked_by, owner.user.id);

  const all = await h.post(`/api/shop/staff-keys/members/${other.member.id}/logout-all`, {}, { token: owner.accessToken });
  assert.equal(all.status, 200, JSON.stringify(all.body));
  assert.equal(all.body.revoked, 1);
  assert.ok(all.body.sessions >= 1);
  assert.equal((await h.get('/api/shop/me', { token: other.session.accessToken })).status, 401);
  refused(await back(other, standing), other, 'پس از «خروج از همه» ِ صاحب');
  const again = await h.post(`/api/shop/staff-keys/members/${other.member.id}/logout-all`, {}, { token: owner.accessToken });
  assert.equal(again.status, 200);
  assert.equal(again.body.revoked, 0);
  assert.equal((await keyRows(other.user.id))[0].revoked_reason, 'owner_logout_all');
  //  صاحب و عضوِ ناموجود
  const ownerMember = await memberOf(owner.user.id);
  assert.equal((await h.post(`/api/shop/staff-keys/members/${ownerMember.id}/logout-all`, {}, { token: owner.accessToken })).status, 400);
  assert.equal((await h.post('/api/shop/staff-keys/sdk_nope/revoke', {}, { token: owner.accessToken })).status, 404);
});

test('جداسازیِ دکان: صاحبِ دکانِ دیگر نه می‌بیند، نه باطل می‌کند، نه بیرون می‌اندازد', async () => {
  const s = await staff('life-phone-8', 'وحید');
  const b = await newShop('دکانِ دیگر');
  const theirs = await h.get('/api/shop/staff-keys', { token: b.owner.accessToken });
  assert.equal(theirs.status, 200);
  assert.deepEqual(theirs.body.devices, []);
  assert.deepEqual(theirs.body.hints, []);
  const [k] = await keyRows(s.user.id);
  const steal = await h.post(`/api/shop/staff-keys/${k.id}/revoke`, {}, { token: b.owner.accessToken });
  assert.equal(steal.status, 404, JSON.stringify(steal.body));
  const kick = await h.post(`/api/shop/staff-keys/members/${s.member.id}/logout-all`, {}, { token: b.owner.accessToken });
  assert.equal(kick.status, 404, JSON.stringify(kick.body));
  assert.equal((await keyRows(s.user.id))[0].revoked_at, null, 'رازِ «الف» زنده ماند');
  assert.equal((await h.get('/api/shop/me', { token: s.session.accessToken })).status, 200, 'نشستِ «الف» زنده ماند');
  //  باطل شدن در «ب» (کاربرِ «ب» خروج از همه) رازِ دکانِ «الف» را نمی‌برد مگر مالِ همان کاربر باشد
  const bStaff = await staff('life-phone-8b', 'ب-شاگرد', { sid: b.shopId, by: b.owner });
  assert.equal((await h.post('/api/auth/logout-all', {}, { token: bStaff.session.accessToken })).status, 200);
  assert.equal((await keyRows(s.user.id))[0].revoked_at, null);
  //  بی توکن
  assert.equal((await h.get('/api/shop/staff-keys')).status, 401);
});

test('مدیر فهرست را می‌بیند ولی باطل نمی‌کند (members.manage فقط صاحب)؛ شاگرد هیچ', async () => {
  const mgr = await staff('life-mgr', 'مدیرِ دکان', { role: 'manager' });
  const s = await staff('life-phone-9', 'لیلا');
  const view = await h.get('/api/shop/staff-keys', { token: mgr.session.accessToken });
  assert.equal(view.status, 200, JSON.stringify(view.body));
  assert.equal(view.body.canRevoke, false);
  const row = view.body.devices.find((d) => d.userId === s.user.id);
  assert.ok(row);
  const r = await h.post(`/api/shop/staff-keys/${row.id}/revoke`, {}, { token: mgr.session.accessToken });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'permission_denied');
  const all = await h.post(`/api/shop/staff-keys/members/${s.member.id}/logout-all`, {}, { token: mgr.session.accessToken });
  assert.equal(all.status, 403);
  assert.equal((await keyRows(s.user.id))[0].revoked_at, null);
  const st = await h.get('/api/shop/staff-keys', { token: s.session.accessToken });
  assert.equal(st.status, 403, JSON.stringify(st.body));
});

test('حذفِ حساب ⇒ رازها باطل و سابقه در دکان؛ همان دستگاه با کدِ ثابت فقط حسابِ تازه می‌گیرد', async () => {
  const since = now();
  const s = await staff('life-phone-10', 'ناهید');
  const out = await require('../src/lib/user-delete').remove(s.user.id, { confirm: s.user.id });
  assert.equal(out.ok, true, JSON.stringify(out));
  assert.equal(out.rows.staff_device_keys, 1);
  assert.equal((await keyRows(s.user.id)).length, 0);
  const [row] = (await revokedAudits(since)).filter((x) => x.target_id === s.user.id);
  assert.equal(row.detail.reason, 'account_deleted');
  refused(await back(s), s, 'پس از حذفِ حساب، کدِ خودش');
  const fresh = await back(s, standing);
  assert.equal(fresh.status, 201, JSON.stringify(fresh.body));
  assert.notEqual(fresh.body.user.id, s.user.id);
});

test('سابقهٔ باطل شدن هرگز راز یا HMAC ندارد؛ پایگاه رازِ باطل را برنمی‌گرداند و پیوندِ راز را عوض نمی‌کند', async () => {
  const rows = await many(`SELECT action, detail::text AS d FROM audit_logs WHERE action LIKE 'staff_device_key.%' OR action LIKE 'staff_login.%'
    OR action IN ('auth.logout_all','member.device_key_revoked','member.logout_all','member.removed','member.updated','staff_code.revoked')`);
  assert.ok(rows.some((r) => r.action === 'staff_device_key.revoked'));
  assert.ok(issued.length >= 10, `${issued.length}`);
  for (const secret of issued) {
    const hash = deviceKeys.hashKey(secret);
    for (const r of rows) {
      assert.ok(!r.d.includes(secret) && !r.d.includes(hash), `راز در سابقهٔ ${r.action}`);
    }
  }
  const dead = await one('SELECT * FROM staff_device_keys WHERE revoked_at IS NOT NULL LIMIT 1');
  await assert.rejects(query('UPDATE staff_device_keys SET revoked_at=NULL WHERE id=$1', [dead.id]), /final/);
  await assert.rejects(query(`UPDATE staff_device_keys SET revoked_reason='rotated' WHERE id=$1`, [dead.id]), /final/);
  const live = await one('SELECT * FROM staff_device_keys WHERE revoked_at IS NULL LIMIT 1');
  await assert.rejects(query(`UPDATE staff_device_keys SET user_id=$2 WHERE id=$1`, [live.id, owner.user.id]), /immutable/);
  await assert.rejects(query(`UPDATE staff_device_keys SET key_hash='x' WHERE id=$1`, [live.id]), /immutable/);
  //  دوباره‌زدنی در خودِ کتابخانه: بار دوم هیچ
  const { tx } = require('../src/db');
  const first = await tx((c) => deviceKeys.revokeIn(c, { userIds: [live.user_id], reason: 'logout_all' }));
  assert.equal(first.revoked, 1);
  const second = await tx((c) => deviceKeys.revokeIn(c, { userIds: [live.user_id], reason: 'logout_all' }));
  assert.equal(second.revoked, 0);
  //  بی هیچ صافی هرگز «همه»
  assert.equal((await tx((c) => deviceKeys.revokeIn(c, { reason: 'logout_all' }))).revoked, 0);
  assert.equal((await tx((c) => deviceKeys.revokeIn(c, { userIds: [], reason: 'logout_all' }))).revoked, 0);
  await assert.rejects(tx((c) => deviceKeys.revokeIn(c, { userIds: [live.user_id], reason: 'whatever' })), /دلیلِ ناشناخته/);
});

test('مهاجرتِ ۳۴۵: rollback ستون‌ها، نگهبان و ردیفش را برمی‌دارد و دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '345_staff_device_key_life.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const cols = async () => (await many(
    `SELECT column_name FROM information_schema.columns WHERE table_name='staff_device_keys' AND column_name IN ('revoked_reason','revoked_by')`)).length;
  const trig = async () => (await many(`SELECT 1 FROM information_schema.triggers WHERE trigger_name='staff_device_keys_guard'`)).length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  const revokedBefore = Number((await one('SELECT COUNT(*)::int AS n FROM staff_device_keys WHERE revoked_at IS NOT NULL')).n);
  await query(down);
  assert.equal(await cols(), 0);
  assert.equal(await trig(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM staff_device_keys WHERE revoked_at IS NOT NULL')).n), revokedBefore,
    'رازهای باطل باطل می‌مانند');
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await cols(), 2);
  assert.equal(await trig() > 0, true);
  //  ردیف‌های باطلِ پیشین «rotated» می‌گیرند؛ کار مثلِ پیش
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM staff_device_keys WHERE revoked_at IS NOT NULL AND revoked_reason IS NULL')).n), 0);
  const s = await staff('life-after-rollback', 'پس از برگشت');
  assert.equal((await h.post('/api/auth/logout-all', {}, { token: s.session.accessToken })).body.deviceKeys, 1);
});
