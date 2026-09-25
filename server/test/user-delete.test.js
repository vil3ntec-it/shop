'use strict';
/**
 * حذفِ کاملِ حساب — «از ریشه» (خواستهٔ صاحب سامانه، ۱۴۰۵/۰۷/۱۳).
 *
 * رفتاری و روی دیتابیسِ واقعی: یک صاحبِ پمپ با پمپ، دستگاهِ بند‌شده، کدِ
 * ورود، پیوندِ تلگرام و پشتیبان؛ و یک پمپِ دیگر که او فقط عضوش است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

let superTok = '';
let plainTok = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  for (const [u, role] of [['root', 'superadmin'], ['helper', 'admin']]) {
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
       VALUES ($1,$2,'مدیر',$3,$4,'active',$5)`,
      [newId('adm'), u, await pw.hashPassword('Admin!12345'), role, now()]);
  }
  superTok = (await h.post('/api/admin/login', { username: 'root', password: 'Admin!12345' })).body.token;
  plainTok = (await h.post('/api/admin/login', { username: 'helper', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

async function owner(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const bind = await h.post('/api/pump/device/bind', { device: { uid: `pc-${name}`, name: 'pc', platform: 'windows' } },
    { token: u.accessToken });
  assert.equal(bind.status, 201, JSON.stringify(bind.body));
  return { ...u, stationId: made.body.station.id, deviceToken: bind.body.deviceToken };
}

const count = async (sql, p) => (await one(sql, p)).n;

test('⛔ حذفِ کامل: حساب، پمپِ خودش و هرچه به آن بسته است — و پمپِ دیگران دست نمی‌خورد', async () => {
  const a = await owner('حذفی');
  const b = await owner('همسایه');
  //  «حذفی» عضوِ پمپِ «همسایه» هم هست
  await query(`INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), b.stationId, a.user.id, now()]);
  //  دادهٔ بی‌کلیدِ خارجی که باید برود
  await query(`INSERT INTO telegram_chats (chat_id, kind, station_id, user_id, linked_at, created_at, updated_at)
               VALUES ('77001','private',$1,$2,$3,$3,$3)`, [a.stationId, a.user.id, now()]);
  await query(`INSERT INTO otp_codes (id, purpose, destination, code_hash, app, attempts, max_attempts, expires_at, created_at)
               VALUES ($1,'login',$2,'x','pump',0,5,$3,$3)`, [newId('otp'), a.user.email.toLowerCase(), now() + 60000]);

  //  پیش‌نمایش: می‌گوید چه می‌رود، بی نوشتن
  const pv = await h.get(`/api/admin/users/${a.user.id}/delete-preview`, { token: plainTok });
  assert.equal(pv.status, 200, JSON.stringify(pv.body));
  assert.equal(pv.body.stations.length, 1);
  assert.equal(pv.body.stations[0].id, a.stationId);
  assert.equal(pv.body.memberOf.stations, 1, 'عضوِ پمپِ دیگر');
  assert.ok(await one('SELECT id FROM users WHERE id=$1', [a.user.id]), 'پیش‌نمایش چیزی را پاک نکرد');

  //  ⛔ مدیرِ غیرِ ارشد نمی‌تواند
  const nope = await h.api('DELETE', `/api/admin/users/${a.user.id}`, { token: plainTok, body: { confirmEmail: a.user.email } });
  assert.equal(nope.status, 403, JSON.stringify(nope.body));
  //  ⛔ بی ایمیلِ درست نمی‌شود
  const wrong = await h.api('DELETE', `/api/admin/users/${a.user.id}`, { token: superTok, body: { confirmEmail: 'x@y.z' } });
  assert.equal(wrong.status, 400);
  assert.equal(wrong.body.error.code, 'confirm_mismatch');
  assert.ok(await one('SELECT id FROM users WHERE id=$1', [a.user.id]), 'با ایمیلِ غلط چیزی نرفت');

  const done = await h.api('DELETE', `/api/admin/users/${a.user.id}`, { token: superTok, body: { confirmEmail: a.user.email.toUpperCase() } });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.stations, 1);

  //  رفته‌ها
  assert.equal(await one('SELECT id FROM users WHERE id=$1', [a.user.id]), null, 'حساب');
  assert.equal(await one('SELECT id FROM stations WHERE id=$1', [a.stationId]), null, 'پمپِ خودش');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM station_devices WHERE station_id=$1', [a.stationId]), 0, 'دستگاه');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM station_subscriptions WHERE station_id=$1', [a.stationId]), 0, 'اشتراک');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM station_members WHERE user_id=$1', [a.user.id]), 0, 'عضویت در پمپِ دیگر');
  assert.equal(await count("SELECT COUNT(*)::int AS n FROM telegram_chats WHERE chat_id='77001'", []), 0, 'تلگرام');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination=$1', [a.user.email.toLowerCase()]), 0, 'کدِ ورود');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM tokens WHERE subject_id=$1', [a.user.id]), 0, 'نشست');
  //  ⛔ توکنِ دستگاهِ پمپِ حذف‌شده دیگر کار نمی‌کند
  const dead = await h.get('/api/pump/device/me', { token: a.deviceToken });
  assert.ok(dead.status === 401 || dead.status === 404, `توکنِ دستگاه: ${dead.status}`);
  //  و ورود با همان ایمیل دیگر حسابی نمی‌یابد
  const login = await h.post('/api/auth/login', { email: a.user.email, password: 'Test!12345', app: 'pump' });
  assert.notEqual(login.status, 200);

  //  ⛔ همسایه دست‌نخورده
  assert.ok(await one('SELECT id FROM stations WHERE id=$1', [b.stationId]), 'پمپِ همسایه');
  assert.equal(await count("SELECT COUNT(*)::int AS n FROM station_members WHERE station_id=$1 AND status='active'", [b.stationId]), 1);
  const alive = await h.get('/api/pump/device/me', { token: b.deviceToken });
  assert.equal(alive.status, 200, 'دستگاهِ همسایه هنوز کار می‌کند');

  //  و در دفترِ ممیزی ثبت شد
  const aud = await one("SELECT detail FROM audit_logs WHERE action='admin.user_deleted' AND target_id=$1", [a.user.id]);
  assert.ok(aud, 'ممیزی');
});

test('حسابِ ناموجود ⇒ ۴۰۴، پیش‌نمایش هم', async () => {
  assert.equal((await h.get('/api/admin/users/usr_nope/delete-preview', { token: superTok })).status, 404);
  assert.equal((await h.api('DELETE', '/api/admin/users/usr_nope', { token: superTok, body: { confirmEmail: 'a@b.c' } })).status, 404);
});
