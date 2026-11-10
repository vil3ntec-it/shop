'use strict';
/**
 * STF-04 (S46) — PIN ِ شخصیِ صندوق در سرور و نوبت‌های سنجیدهٔ صندوق (`/api/shop/till-pin`، مهاجرتِ ۶۴۰).
 *
 *  • پرچمِ `shop.till_pin` خاموش (پیش‌فرض) ⇒ فقط `/config`، بقیه ۴۰۳ و هیچ ردیفی؛
 *  • PIN فقط اثرِ PBKDF2: نه در پایگاه، نه در پاسخ، نه در سابقه؛ شکلِ PIN ِ تازه همان `PinGuard.newPinProblem`؛
 *  • سنجش در سرور: ۵ خطا ⇒ قفلِ زمان‌دار، PIN ِ درست هم تا پایانِ قفل نه؛ شمارِ خطا پیش از پاسخ می‌نشیند؛
 *    حدس‌های هم‌زمان قفل را دور نمی‌زنند؛
 *  • ⛔ صاحب هرگز بیرون نمی‌ماند: PIN ِ قفل‌شدهٔ صاحب با تأییدِ دوبارهٔ ورود از نو گذاشته و باز می‌شود؛ شاگرد و
 *    مدیر قفلِ صاحب را باز نمی‌کنند؛ هر نوشتن بی تأییدِ دوبارهٔ ورود ۴۰۳ ِ `reauth_required`؛
 *  • نوبت: PIN ِ درست روی دستگاهِ توکن نوبت باز می‌کند، تعویض/PIN ِ خودِ حسابِ دستگاه/قفل/برداشتنِ PIN می‌بندد؛
 *    `whoAt` «چه کسی در آن لحظه» را از روی نوبت‌های سرور می‌گوید؛
 *  • دکانِ دیگر عضوِ این دکان را نمی‌شناسد و نوبت‌هایش را نمی‌بیند.
 */
process.env.TILL_PIN_ITER = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { hashToken } = require('../src/lib/tokens');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let A;   //  دکانِ «الف»: صاحب (حسابِ دستگاه)، مدیر، شاگرد
let B;   //  دکانِ «ب»
const PIN = { owner: '482915', manager: '730264', staff: '615083' };

async function shopOf(label) {
  const u = await h.newUser(`صاحب-${label}`);
  const r = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { ...u, token: u.accessToken, shopId: r.body.shop.id };
}
async function member(s, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [id, s.shopId, u.user.id, role, now()],
  );
  return { ...u, token: u.accessToken, memberId: id };
}
const flag = (s, value = true) => h.put('/api/admin/pump/live-config', { scope: s.shopId, key: 'shop.till_pin', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const stale = (who) => query(`DELETE FROM login_attempts WHERE scope='session_auth' AND identifier=$1`, [hashToken(who.token)]);
const verify = (host, target, pin) => h.post('/api/shop/till-pin/verify', { memberId: target.memberId, pin }, { token: host.token });
const setPin = (by, target, pin) => (by === target
  ? h.put('/api/shop/till-pin/me', { pin }, { token: by.token })
  : h.put(`/api/shop/till-pin/members/${target.memberId}`, { pin }, { token: by.token }));
const ok = (r, code = 200) => { assert.equal(r.status, code, JSON.stringify(r.body)); return r.body; };

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'tp-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'tp-admin', password: 'Admin!12345' })).body.token;
  A = await shopOf('الف');
  A.memberId = (await h.get('/api/shop/till-pin/config', { token: A.token })).body.memberId;
  A.manager = await member(A, 'manager', 'مدیرِ صندوق');
  A.staff = await member(A, 'staff', 'شاگردِ صندوق');
  A.cashier = await member(A, 'cashier', 'صندوق‌دار');
  B = await shopOf('ب');
  B.memberId = (await h.get('/api/shop/till-pin/config', { token: B.token })).body.memberId;
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('خاموش (پیش‌فرض): فقط /config، بقیه ۴۰۳ till_pin_disabled و هیچ ردیفی', async () => {
  const c = ok(await h.get('/api/shop/till-pin/config', { token: A.staff.token }));
  assert.deepEqual(c, { enabled: false, memberId: A.staff.memberId, role: 'staff' });
  for (const r of [
    await setPin(A, A, PIN.owner),
    await verify(A, A.staff, PIN.staff),
    await h.get('/api/shop/till-pin/members', { token: A.token }),
    await h.get('/api/shop/till-pin/current', { token: A.token }),
    await h.get('/api/shop/till-pin/sessions', { token: A.token }),
  ]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error?.code, 'till_pin_disabled');
  }
  assert.equal((await one('SELECT count(*)::int AS n FROM shop_till_pins')).n, 0);
  assert.equal((await one('SELECT count(*)::int AS n FROM shop_till_sessions')).n, 0);
  assert.equal(await require('../src/lib/till-pin').whoAt(A.shopId, 'any', Date.now()), null);
  assert.equal((await h.get('/api/shop/till-pin/config')).status, 401);
});

test('گذاشتنِ PIN: شکل و سادگی همان PinGuard؛ فقط اثرِ PBKDF2؛ بی تأییدِ دوبارهٔ ورود ۴۰۳؛ اختیارِ صاحب و مدیر', async () => {
  await flag(A);
  for (const [pin, code] of [['12345', 'till_pin_format'], ['12a456', 'till_pin_format'], ['111111', 'till_pin_weak'],
    ['123456', 'till_pin_weak'], ['654321', 'till_pin_weak']]) {
    const r = await setPin(A, A, pin);
    assert.equal(r.status, 400, pin);
    assert.equal(r.body.error?.code, code, pin);
  }
  //  رقمِ فارسی پذیرفته و همان PIN ِ لاتین است
  ok(await setPin(A, A, '۴۸۲۹۱۵'));
  ok(await setPin(A, A.manager, PIN.manager));
  //  مدیر فقط نقش‌های پایین‌تر: شاگرد آری، صاحب نه
  ok(await setPin(A.manager, A.staff, PIN.staff));
  const up = await setPin(A.manager, A, '902817');
  assert.equal(up.status, 403);
  assert.equal(up.body.error?.code, 'till_pin_forbidden');
  //  شاگرد PIN ِ دیگری را نمی‌گذارد (members.view)، ولی PIN ِ خودش را آری
  assert.equal((await setPin(A.staff, A.cashier, '902817')).status, 403);
  ok(await setPin(A.cashier, A.cashier, '274096'));

  const rows = await many('SELECT member_id, pin_hash, failures, set_by FROM shop_till_pins WHERE shop_id=$1', [A.shopId]);
  assert.equal(rows.length, 4);
  for (const r of rows) {
    assert.match(r.pin_hash, /^pbkdf2-sha256\$1000\$[A-Za-z0-9+/=]+\$[A-Za-z0-9+/=]+$/);
    for (const p of [...Object.values(PIN), '274096']) assert.ok(!r.pin_hash.includes(p), 'PIN ِ خام در پایگاه');
  }
  assert.equal(rows.find((r) => r.member_id === A.staff.memberId).set_by, A.manager.memberId);
  const logs = await many(`SELECT action, detail::text AS d FROM audit_logs WHERE shop_id=$1 AND action LIKE 'till_pin.%'`, [A.shopId]);
  assert.equal(logs.filter((l) => l.action === 'till_pin.set').length, 4);
  for (const l of logs) for (const p of Object.values(PIN)) assert.ok(!l.d.includes(p), 'PIN در سابقه');

  //  نشستِ کهنه ⇒ تأییدِ دوبارهٔ ورود؛ چیزی عوض نمی‌شود
  await stale(A.staff);
  const st = await setPin(A.staff, A.staff, '390471');
  assert.equal(st.status, 403);
  assert.equal(st.body.error?.code, 'reauth_required');
  assert.equal((await one('SELECT set_by FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.staff.memberId])).set_by, A.manager.memberId);

  const list = ok(await h.get('/api/shop/till-pin/members', { token: A.manager.token }));
  assert.equal(list.members.filter((m) => m.pinSet).length, 4);
  assert.ok(!JSON.stringify(list).includes('pbkdf2'), 'اثرِ PIN در پاسخ');
  assert.equal((await h.get('/api/shop/till-pin/members', { token: A.staff.token })).status, 403);
  const c = ok(await h.get('/api/shop/till-pin/config', { token: A.token }));
  assert.equal(c.pinSet, true);
  assert.equal(c.locked, false);
});

test('سنجش: نوبت روی دستگاهِ توکن؛ تعویض، PIN ِ خودِ حسابِ دستگاه و قفل می‌بندند؛ whoAt از نوبت‌های سرور', async () => {
  const tp = require('../src/lib/till-pin');
  const dev = await one('SELECT d.id FROM tokens t JOIN devices d ON d.id = t.device_id WHERE t.token_hash=$1', [hashToken(A.token)]);
  assert.ok(dev, 'نشستِ صاحب دستگاه دارد');
  const t0 = Date.now();
  const a = ok(await verify(A, A.staff, PIN.staff));
  assert.equal(a.session.memberId, A.staff.memberId);
  assert.equal(a.session.deviceMemberId, A.memberId);
  assert.equal(a.session.endedAt, null);
  assert.ok(!JSON.stringify(a).includes(PIN.staff));
  assert.equal(ok(await h.get('/api/shop/till-pin/current', { token: A.token })).session.memberId, A.staff.memberId);
  //  دستگاهِ دیگرِ همین دکان (توکنِ مدیر) نوبتِ دستگاهِ صاحب را ندارد
  assert.equal(ok(await h.get('/api/shop/till-pin/current', { token: A.manager.token })).session, null);
  assert.equal(await tp.whoAt(A.shopId, dev.id, Date.now()), A.staff.memberId);
  assert.equal(await tp.whoAt(A.shopId, dev.id, t0 - 1000), null);

  //  تعویض به مدیر ⇒ نوبتِ شاگرد «switch»؛ فقط یک نوبتِ باز
  const t1 = Date.now();
  const b = ok(await verify(A, A.manager, PIN.manager));
  assert.equal(b.session.memberId, A.manager.memberId);
  const open = await many('SELECT till_member_id FROM shop_till_sessions WHERE shop_id=$1 AND device_id=$2 AND ended_at IS NULL', [A.shopId, dev.id]);
  assert.deepEqual(open.map((r) => r.till_member_id), [A.manager.memberId]);
  assert.equal((await one('SELECT end_reason FROM shop_till_sessions WHERE id=$1', [a.session.id])).end_reason, 'switch');
  assert.equal(await tp.whoAt(A.shopId, dev.id, Date.now() + 1), A.manager.memberId);
  assert.equal(await tp.whoAt(A.shopId, dev.id, t1 - 1), A.staff.memberId);

  //  PIN ِ خودِ حسابِ دستگاه ⇒ نوبت بسته («self») و هیچ نوبتِ تازه
  const self = ok(await verify(A, A, PIN.owner));
  assert.equal(self.session, null);
  assert.equal(ok(await h.get('/api/shop/till-pin/current', { token: A.token })).session, null);
  assert.equal((await one('SELECT end_reason FROM shop_till_sessions WHERE id=$1', [b.session.id])).end_reason, 'self');
  assert.equal(await tp.whoAt(A.shopId, dev.id, Date.now() + 5), null);

  //  قفلِ صندوق (Ctrl+L) ⇒ «lock»؛ دوباره‌زدنی
  const c = ok(await verify(A, A.staff, PIN.staff));
  assert.deepEqual(ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.token })), { ended: 1 });
  assert.deepEqual(ok(await h.post('/api/shop/till-pin/current/end', {}, { token: A.token })), { ended: 0 });
  assert.equal((await h.post('/api/shop/till-pin/current/end', { reason: 'expired' }, { token: A.token })).status, 400);
  assert.equal((await one('SELECT end_reason FROM shop_till_sessions WHERE id=$1', [c.session.id])).end_reason, 'lock');

  //  نوبتِ کهنه (پایانِ عمر) نوبتِ تازه را نمی‌بندد و «expired» در همان پایانِ عمر بسته می‌شود
  const d = ok(await verify(A, A.staff, PIN.staff));
  await query('UPDATE shop_till_sessions SET started_at=started_at-$2, expires_at=$3 WHERE id=$1', [d.session.id, 20 * 3600_000, Date.now() - 1000]);
  assert.equal(ok(await h.get('/api/shop/till-pin/current', { token: A.token })).session, null);
  ok(await verify(A, A.manager, PIN.manager));
  const old = await one('SELECT ended_at, expires_at, end_reason FROM shop_till_sessions WHERE id=$1', [d.session.id]);
  assert.equal(old.end_reason, 'expired');
  assert.equal(Number(old.ended_at), Number(old.expires_at));

  //  فهرست: صاحب و مدیر همه؛ شاگرد فقط خودش
  const all = ok(await h.get('/api/shop/till-pin/sessions', { token: A.token })).sessions;
  assert.ok(all.length >= 5);
  assert.ok(all.some((s) => s.memberId === A.manager.memberId) && all.some((s) => s.memberId === A.staff.memberId));
  const mine = ok(await h.get('/api/shop/till-pin/sessions', { token: A.staff.token })).sessions;
  assert.ok(mine.length >= 3 && mine.every((s) => s.memberId === A.staff.memberId));
  assert.equal((await h.get('/api/shop/till-pin/sessions?from=0', { token: A.token })).status, 400);
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.token }));
});

test('قفل: ۵ خطا ⇒ ۴۲۳ و PIN ِ درست هم نه؛ شمارِ خطا می‌نشیند؛ شاگرد و مدیر قفلِ صاحب را باز نمی‌کنند؛ صاحب با تأییدِ دوباره برمی‌گردد', async () => {
  const left = [];
  for (let i = 0; i < 4; i++) {
    const r = await verify(A.manager, A, '000001');
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error?.code, 'till_pin_wrong');
    left.push(r.body.error?.attemptsLeft);
  }
  assert.deepEqual(left, [4, 3, 2, 1]);
  const fifth = await verify(A.manager, A, '000001');
  assert.equal(fifth.status, 423, JSON.stringify(fifth.body));
  assert.equal(fifth.body.error?.code, 'till_pin_locked');
  const right = await verify(A.manager, A, PIN.owner);
  assert.equal(right.status, 423, 'PIN ِ درست هم تا پایانِ قفل نه');
  const row = await one('SELECT failures, locked_until FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.memberId]);
  assert.equal(row.failures, 5);
  assert.ok(Number(row.locked_until) > Date.now() + 14 * 60_000);
  assert.equal((await one(`SELECT count(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='till_pin.locked'`, [A.shopId])).n, 1);
  assert.equal(ok(await h.get('/api/shop/till-pin/config', { token: A.token })).locked, true);

  //  مدیر و شاگرد قفلِ صاحب را باز نمی‌کنند
  const byManager = await h.post(`/api/shop/till-pin/members/${A.memberId}/unlock`, {}, { token: A.manager.token });
  assert.equal(byManager.status, 403);
  assert.equal(byManager.body.error?.code, 'till_pin_forbidden');
  assert.equal((await h.post(`/api/shop/till-pin/members/${A.memberId}/unlock`, {}, { token: A.staff.token })).status, 403);
  //  صاحب: نشستِ کهنه ⇒ تأییدِ دوبارهٔ ورود با رمزِ حساب ⇒ PIN ِ تازه ⇒ باز
  await stale(A);
  assert.equal((await setPin(A, A, '518206')).body.error?.code, 'reauth_required');
  ok(await h.post('/api/auth/reauth', { password: A.password }, { token: A.token }));
  ok(await setPin(A, A, PIN.owner));
  assert.equal(ok(await verify(A.manager, A, PIN.owner)).session.memberId, A.memberId);
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.manager.token }));

  //  صاحب قفلِ شاگرد را باز می‌کند (با تأییدِ تازه)؛ قفل برداشته ولی شمار (پلهٔ دوبرابر) می‌ماند (R14)؛ PIN ِ درست صفرش می‌کند
  for (let i = 0; i < 5; i++) await verify(A, A.staff, '000002');
  assert.equal((await verify(A, A.staff, PIN.staff)).status, 423);
  ok(await h.post(`/api/shop/till-pin/members/${A.staff.memberId}/unlock`, {}, { token: A.token }));
  const opened = await one('SELECT failures, locked_until FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.staff.memberId]);
  assert.deepEqual([opened.failures, Number(opened.locked_until)], [5, 0]);
  ok(await verify(A, A.staff, PIN.staff));
  assert.equal((await one('SELECT failures FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.staff.memberId])).failures, 0);
  ok(await h.post('/api/shop/till-pin/current/end', { reason: 'lock' }, { token: A.token }));
});

test('حدس‌های هم‌زمان: قفل دور زده نمی‌شود (دقیقاً ۵ خطا شمرده و بقیه ۴۲۳)', async () => {
  const out = await Promise.all(Array.from({ length: 9 }, (_, i) => verify(A.manager, A.cashier, `00010${i}`)));
  const codes = out.map((r) => r.status).sort();
  assert.deepEqual(codes, [403, 403, 403, 403, 423, 423, 423, 423, 423], JSON.stringify(out.map((r) => r.body)));
  assert.equal((await one('SELECT failures FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [A.shopId, A.cashier.memberId])).failures, 5);
});

test('برداشتنِ PIN نوبتِ بازِ همان عضو را می‌بندد و سنجشِ بعدی «ندارد»', async () => {
  const s = ok(await verify(A, A.staff, PIN.staff)).session;
  const r = ok(await h.del(`/api/shop/till-pin/members/${A.staff.memberId}`, { token: A.token }));
  assert.equal(r.removed, true);
  assert.equal(r.ended, 1);
  assert.equal((await one('SELECT end_reason FROM shop_till_sessions WHERE id=$1', [s.id])).end_reason, 'pin_removed');
  const again = await verify(A, A.staff, PIN.staff);
  assert.equal(again.status, 409);
  assert.equal(again.body.error?.code, 'till_pin_not_set');
  ok(await setPin(A, A.staff, PIN.staff));
});

test('دکانِ دیگر: عضوِ «الف» را نمی‌شناسد، PIN اش را نمی‌گذارد/برنمی‌دارد و نوبت‌هایش را نمی‌بیند', async () => {
  await flag(B);
  const before = await many('SELECT member_id, pin_hash, failures, locked_until FROM shop_till_pins WHERE shop_id=$1 ORDER BY member_id', [A.shopId]);
  for (const r of [
    await verify(B, A.staff, PIN.staff),
    await h.put(`/api/shop/till-pin/members/${A.staff.memberId}`, { pin: '390471' }, { token: B.token }),
    await h.del(`/api/shop/till-pin/members/${A.staff.memberId}`, { token: B.token }),
    await h.post(`/api/shop/till-pin/members/${A.memberId}/unlock`, {}, { token: B.token }),
  ]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error?.code, 'member_not_found');
  }
  //  با شناسهٔ کاربر هم همان
  assert.equal((await h.post('/api/shop/till-pin/verify', { userId: A.staff.user.id, pin: PIN.staff }, { token: B.token })).status, 404);
  const seen = ok(await h.get(`/api/shop/till-pin/sessions?member=${A.staff.memberId}`, { token: B.token })).sessions;
  assert.deepEqual(seen, []);
  assert.ok(!JSON.stringify(ok(await h.get('/api/shop/till-pin/members', { token: B.token }))).includes(A.staff.memberId));
  assert.deepEqual(await many('SELECT member_id, pin_hash, failures, locked_until FROM shop_till_pins WHERE shop_id=$1 ORDER BY member_id', [A.shopId]), before);
});

test('سقفِ نرخِ سنجش برای هر حسابِ دستگاه (۲۰ در دقیقه) جدا از قفلِ PIN', async () => {
  const lone = await member(A, 'staff', 'بی‌PIN');
  for (let i = 0; i < 20; i++) assert.equal((await verify(A, lone, '390471')).status, 409);
  assert.equal((await verify(A, lone, '390471')).status, 429);
  assert.equal((await verify(A.manager, lone, '390471')).status, 409, 'سطلِ هر کاربر');
});

test('مهاجرتِ ۶۴۰: برگشت دو جدول را برمی‌دارد و دکان و اعضا می‌مانند؛ دوباره بالا می‌آید و همان رفتار', async () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const members = (await one('SELECT count(*)::int AS n FROM shop_members WHERE shop_id=$1', [A.shopId])).n;
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '640_shop_till_pins.down.sql'), 'utf8');
  await query(down);
  const tables = (await many(`SELECT table_name FROM information_schema.tables WHERE table_name IN ('shop_till_pins','shop_till_sessions')`));
  assert.deepEqual(tables, []);
  assert.equal((await one('SELECT count(*)::int AS n FROM shop_members WHERE shop_id=$1', [A.shopId])).n, members);
  assert.deepEqual(await require('../src/migrate').run(), ['640_shop_till_pins.sql']);
  ok(await setPin(A, A.staff, PIN.staff));
  assert.equal(ok(await verify(A, A.staff, PIN.staff)).session.memberId, A.staff.memberId);
});
