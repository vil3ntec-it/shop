'use strict';
/**
 * ⛔ ورودِ دوبارهٔ شاگرد با کد — یافتهٔ CodeQL/بازبینی (۲.۱۴.۵): «کدِ دکان + شناسهٔ دستگاهِ عضوِ دیگر» نشستِ همان عضو را
 * می‌داد، چون عضوِ موجود فقط با شناسهٔ دستگاهِ بدنه پیدا می‌شد و وضعیتِ کد (باطل، تمام‌شده) عمداً سنجیده نمی‌شد.
 *
 * حالا (پرچمِ `auth.staff_session_guard`، پیش‌فرض روشن):
 *   - عضوِ موجود فقط با **رازِ دستگاه** برمی‌گردد (`lib/staff-device-keys`، مهاجرتِ ۳۲۰)؛ شناسهٔ دستگاه اثبات نیست؛
 *   - کد باید هنوز برای همان عضو معتبر باشد (باطل ⇒ `code_inactive`، مهلت ⇒ `code_expired`، مالِ دیگری ⇒ `code_used`)؛
 *   - شناسهٔ بسته بی راز + کدِ ثابت ⇒ `staff_device_unverified` با «کدِ تازه از صاحبِ دکان بگیرید» (نسخهٔ کهنه)؛
 *     کدِ تازهٔ صاحب ⇒ حسابِ تازه، هرگز همان حساب؛
 *   - هر ورود (موفق و هر دلیلِ شکست) در سابقه، بی کد و بی راز.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const staffCodes = require('../src/lib/staff-codes');

let owner;
let shopId;
let standing;

const login = (code, device, name) => h.post('/api/auth/staff', { code, ...(name ? { name } : {}), device });
const users = async () => Number((await one('SELECT COUNT(*)::int AS n FROM users')).n);
const trail = (action, since) => many(
  `SELECT * FROM audit_logs WHERE action=$1 AND shop_id=$2 AND created_at >= $3 ORDER BY created_at`, [action, shopId, since]);
const setGuard = async (value) => {
  await query(`DELETE FROM live_config WHERE key='auth.staff_session_guard'`);
  if (value !== undefined) {
    await query(`INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'auth.staff_session_guard',$2,1)`,
      [shopId, JSON.stringify(value)]);
  }
  require('../src/lib/hot-cache').clear();
};

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  owner = await h.newUser('صاحبِ نگهبان');
  shopId = (await h.post('/api/shop', { name: 'دکانِ نگهبان' }, { token: owner.accessToken })).body.shop.id;
  const admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  await h.post('/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, { token: admin });
  await query('UPDATE shops SET max_members=50 WHERE id=$1', [shopId]);
  //  کدِ ثابتِ دکان (بی‌شمار، مالِ همهٔ شاگردها) — `GET`؛ `POST` کدِ یک‌بار می‌سازد
  standing = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  assert.ok(standing, 'کدِ ثابت نیامد');
});
test.after(async () => { await h.stop(); });

/** مدیرِ دکان با کدِ یک‌بارِ نقشِ مدیر، روی گوشیِ خودش — برنامهٔ تازه (رازِ دستگاه می‌گیرد) */
async function manager(uid, { needKey = true } = {}) {
  const made = await h.post('/api/shop/staff-code', { role: 'manager', maxUses: 1 }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const r = await login(made.body.code, { uid, name: 'گوشیِ مدیر', platform: 'android', keyVersion: 1 }, 'مدیرِ دکان');
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.shop.actualRole, 'manager');
  if (!needKey) return { code: made.body.code, codeId: made.body.id, uid, key: r.body.deviceKey, user: r.body.user, session: r.body };
  assert.equal(typeof r.body.deviceKey, 'string', 'برنامهٔ تازه رازِ دستگاه نگرفت');
  assert.ok(r.body.deviceKey.length >= 40);
  return { code: made.body.code, codeId: made.body.id, uid, key: r.body.deviceKey, user: r.body.user, session: r.body };
}

test('بازتولید: کدِ شاگرد + شناسهٔ دستگاهِ مدیر ⇒ نشستِ مدیر داده نمی‌شود (پیش از رفع ۲۰۱ و نقشِ مدیر)', async () => {
  const since = now();
  const victim = await manager('mgr-phone-1', { needKey: false });
  //  همکارِ شاگرد با کدِ ثابت (که همهٔ شاگردها دارند) و شناسهٔ دستگاهِ مدیر (که در Pull و X-Device دیده می‌شود)
  const reza = await login(standing, { uid: 'reza-phone', keyVersion: 1 }, 'رضا');
  assert.equal(reza.status, 201, JSON.stringify(reza.body));
  const before = await users();

  const takeover = await login(standing, { uid: victim.uid });
  assert.notEqual(takeover.body.user?.id, victim.user.id,
    `ربودنِ نشست: رضا نشستِ مدیر را گرفت (${takeover.status}، نقشِ ${takeover.body.shop?.actualRole})`);
  assert.equal(takeover.status, 403, JSON.stringify(takeover.body));
  assert.equal(takeover.body.error.code, 'staff_device_unverified');
  assert.equal(takeover.body.accessToken, undefined, 'توکن نباید بیاید');
  assert.equal(typeof victim.key, 'string', 'برنامهٔ تازه رازِ دستگاه نگرفت');
  //  رازِ ساختگی یا رازِ دستگاهِ دیگر (خودِ رضا) هم نه
  for (const key of ['x'.repeat(43), reza.body.deviceKey]) {
    const r = await login(standing, { uid: victim.uid, key, keyVersion: 1 });
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'staff_device_unverified');
  }
  //  کدِ یک‌بارِ خودِ مدیر (تمام‌شده) + شناسه‌اش هم نه
  const spent = await login(victim.code, { uid: victim.uid });
  assert.equal(spent.status, 403, JSON.stringify(spent.body));
  assert.equal(spent.body.error.code, 'staff_device_unverified');
  assert.equal(await users(), before, 'تلاشِ ربودن حسابِ شبح ساخت');

  //  هر شکست با دلیل در سابقه، بی کد و بی راز
  const failed = await trail('staff_login.failed', since);
  assert.ok(failed.filter((r) => r.detail.reason === 'staff_device_unverified').length >= 4, JSON.stringify(failed.map((r) => r.detail)));
  const text = JSON.stringify(failed);
  assert.ok(!text.includes(standing) && !text.includes(staffCodes.normalize(standing)), 'کد در سابقه نشست');
  assert.ok(!text.includes(victim.key) && !text.includes(reza.body.deviceKey), 'رازِ دستگاه در سابقه نشست');
  assert.ok(failed.some((r) => r.detail.device === victim.uid && r.target_type === 'staff_code'));
});

test('ورودِ درستِ همان دستگاه پس از خروج: همان حساب و نقش، کدِ یک‌بارِ خودش (تمام‌شده) خرج نمی‌شود', async () => {
  const since = now();
  const m = await manager('mgr-phone-2');
  const out = await h.post('/api/auth/logout', { refreshToken: m.session.refreshToken });
  assert.ok(out.status < 300, JSON.stringify(out.body));

  const back = await login(m.code, { uid: m.uid, key: m.key, keyVersion: 1 });
  assert.equal(back.status, 201, JSON.stringify(back.body));
  assert.equal(back.body.user.id, m.user.id, 'باید همان حساب باشد');
  assert.equal(back.body.shop.actualRole, 'manager');
  assert.equal(back.body.deviceKey, undefined, 'ورودِ دوباره رازِ تازه نمی‌سازد (همان راز می‌ماند)');
  const used = await one('SELECT used_count, status FROM staff_codes WHERE id=$1', [m.codeId]);
  assert.equal(Number(used.used_count), 1);
  assert.equal(used.status, 'exhausted');
  //  همان راز بارها کار می‌کند؛ کدِ ثابتِ فعلی هم
  assert.equal((await login(m.code, { uid: m.uid, key: m.key, keyVersion: 1 })).status, 201);
  const viaStanding = await login(standing, { uid: m.uid, key: m.key, keyVersion: 1 });
  assert.equal(viaStanding.status, 201, JSON.stringify(viaStanding.body));
  assert.equal(viaStanding.body.user.id, m.user.id);
  assert.equal(viaStanding.body.shop.actualRole, 'manager', 'کدِ ثابتِ شاگرد نقشِ مدیر را عوض نمی‌کند');

  const ok = (await trail('staff_login.ok', since)).filter((r) => r.user_id === m.user.id);
  assert.deepEqual(ok.map((r) => r.detail.mode), ['join', 'relogin', 'relogin', 'relogin']);
  assert.ok(ok.slice(1).every((r) => r.detail.proof === true));
});

test('عضوِ موجود با رازِ درست ولی کدِ باطل، تمام‌شده یا مالِ دیگری ⇒ رد؛ کدِ تازه ⇒ همان حساب', async () => {
  const ali = await login(standing, { uid: 'ali-phone', keyVersion: 1 }, 'علی');
  assert.equal(ali.status, 201, JSON.stringify(ali.body));
  const key = ali.body.deviceKey;
  const dev = { uid: 'ali-phone', key, keyVersion: 1 };

  //  کدِ یک‌بارِ کسِ دیگر (تمام‌شده) ⇒ code_used
  const other = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  assert.equal((await login(other.code, { uid: 'someone-else', keyVersion: 1 }, 'دیگری')).status, 201);
  const notMine = await login(other.code, dev);
  assert.equal(notMine.status, 403, JSON.stringify(notMine.body));
  assert.equal(notMine.body.error.code, 'code_used');

  //  کدِ مهلت‌دارِ گذشته ⇒ code_expired (کدِ خودِ علی با مهلت)
  const timed = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 5, expiresAt: now() + 60_000 });
  assert.equal((await login(timed.code, dev)).status, 201, 'کدِ باز و در مهلت');
  await query('UPDATE staff_codes SET expires_at=$2 WHERE id=$1', [timed.id, now() - 1000]);
  const late = await login(timed.code, dev);
  assert.equal(late.status, 403, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'code_expired');

  //  «عوض کردنِ کد» ⇒ کدِ ثابتِ پیشین برای عضوِ موجود هم باطل؛ کدِ تازه همان حساب را برمی‌گرداند
  const rotated = await h.post('/api/shop/staff-code/rotate', {}, { token: owner.accessToken });
  assert.equal(rotated.status, 200, JSON.stringify(rotated.body));
  const old = await login(standing, dev);
  assert.equal(old.status, 403, JSON.stringify(old.body));
  assert.equal(old.body.error.code, 'code_inactive');
  standing = rotated.body.code;
  const again = await login(standing, dev);
  assert.equal(again.status, 201, JSON.stringify(again.body));
  assert.equal(again.body.user.id, ali.body.user.id);

  //  باطل‌کردنِ کدِ یک‌بار ⇒ code_inactive
  const once = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 3 });
  assert.equal((await login(once.code, dev)).status, 201);
  await h.del(`/api/shop/staff-codes/${once.id}`, { token: owner.accessToken });
  const dead = await login(once.code, dev);
  assert.equal(dead.status, 403, JSON.stringify(dead.body));
  assert.equal(dead.body.error.code, 'code_inactive');
});

test('دستگاهِ تازه کدِ تازه می‌خواهد: کدِ تمام‌شده هیچ حسابی نمی‌سازد؛ شناسهٔ بسته + کدِ تازه ⇒ حسابِ تازه، نه همان', async () => {
  const m = await manager('mgr-phone-3');
  const before = await users();
  //  گوشیِ تازه با کدِ یک‌بارِ مصرف‌شده
  const spent = await login(m.code, { uid: 'mgr-new-phone', keyVersion: 1 });
  assert.equal(spent.status, 403, JSON.stringify(spent.body));
  assert.ok(['code_used', 'code_inactive'].includes(spent.body.error.code), spent.body.error.code);
  assert.equal(await users(), before);

  //  نصبِ تازه روی همان شناسه (رازی نمانده) + کدِ تازهٔ صاحب ⇒ عضوِ تازه با نقشِ همان کد؛ حسابِ مدیر دست‌نخورده
  const fresh = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  const r = await login(fresh.code, { uid: m.uid, keyVersion: 1 }, 'مدیر، نصبِ تازه');
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.notEqual(r.body.user.id, m.user.id, 'کدِ تازه نباید حسابِ بسته‌شده را بدهد');
  assert.equal(r.body.shop.actualRole, 'staff');
  assert.equal(typeof r.body.deviceKey, 'string');
  //  حسابِ تازه با رازِ خودش برمی‌گردد؛ رازِ مدیر هنوز مالِ مدیر است
  const mine = await login(fresh.code, { uid: m.uid, key: r.body.deviceKey, keyVersion: 1 });
  assert.equal(mine.status, 201, JSON.stringify(mine.body));
  assert.equal(mine.body.user.id, r.body.user.id);
  const mgr = await login(m.code, { uid: m.uid, key: m.key, keyVersion: 1 });
  assert.equal(mgr.status, 201, JSON.stringify(mgr.body));
  assert.equal(mgr.body.user.id, m.user.id);
  assert.equal(mgr.body.shop.actualRole, 'manager');
});

test('برنامهٔ کهنه (بی رازِ دستگاه): پیوستنِ تازه کار می‌کند؛ ورودِ دوباره پیامِ روشنِ «کدِ تازه از صاحبِ دکان بگیرید»', async () => {
  const since = now();
  const join = await login(standing, { uid: 'old-app-phone', name: 'گوشیِ کهنه' }, 'حسن');
  assert.equal(join.status, 201, JSON.stringify(join.body));
  assert.equal(join.body.deviceKey, undefined, 'برنامهٔ کهنه رازی نمی‌گیرد که دور بریزد');

  const again = await login(standing, { uid: 'old-app-phone' });
  assert.equal(again.status, 403, JSON.stringify(again.body));
  assert.equal(again.body.error.code, 'staff_device_unverified');
  assert.match(again.body.error.message, /کدِ تازه از صاحبِ دکان بگیرید/);

  //  کدِ تازهٔ صاحب راه را باز می‌کند (حسابِ تازه)
  const fresh = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  const ok = await login(fresh.code, { uid: 'old-app-phone' }, 'حسن');
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.notEqual(ok.body.user.id, join.body.user.id);
  const reasons = (await trail('staff_login.failed', since)).map((r) => r.detail.reason);
  assert.ok(reasons.includes('staff_device_unverified'), JSON.stringify(reasons));
});

test('نشستِ بازِ پیش از رفع رازِ دستگاه را از همان نشست می‌گیرد (فقط یک بار، فقط حسابِ کد)؛ بعد ورودِ دوباره کار می‌کند', async () => {
  const legacy = await login(standing, { uid: 'legacy-phone', name: 'گوشیِ قدیمی' }, 'کریم');
  assert.equal(legacy.status, 201, JSON.stringify(legacy.body));
  assert.equal(legacy.body.deviceKey, undefined);

  assert.equal((await h.post('/api/auth/staff/device-key', {})).status, 401, 'بی توکن ۴۰۱');
  const got = await h.post('/api/auth/staff/device-key', {}, { token: legacy.body.accessToken });
  assert.equal(got.status, 201, JSON.stringify(got.body));
  assert.equal(typeof got.body.deviceKey, 'string');
  //  دوباره ⇒ هیچ (چرخاندن نه: توکنِ دزدیده رازِ دستگاهِ درست را باطل نمی‌کند)
  const twice = await h.post('/api/auth/staff/device-key', {}, { token: legacy.body.accessToken });
  assert.equal(twice.status, 200, JSON.stringify(twice.body));
  assert.equal(twice.body.deviceKey, null);
  assert.equal(twice.body.reason, 'exists');
  //  صاحب (حسابِ ایمیلی) ⇒ لازم نیست
  const own = await h.post('/api/auth/staff/device-key', {}, { token: owner.accessToken });
  assert.equal(own.status, 200);
  assert.equal(own.body.reason, 'not_staff_account');

  await h.post('/api/auth/logout', { refreshToken: legacy.body.refreshToken });
  const back = await login(standing, { uid: 'legacy-phone', key: got.body.deviceKey, keyVersion: 1 });
  assert.equal(back.status, 201, JSON.stringify(back.body));
  assert.equal(back.body.user.id, legacy.body.user.id);
  //  همان راز با شناسهٔ دستگاهِ دیگر هیچ
  const elsewhere = await login(standing, { uid: 'other-phone-x', key: got.body.deviceKey, keyVersion: 1 });
  assert.equal(elsewhere.status, 201);
  assert.notEqual(elsewhere.body.user.id, legacy.body.user.id, 'راز به شناسهٔ دستگاه بسته است');
});

test('کلیدِ اضطراری: `auth.staff_session_guard` = false ⇒ رفتارِ پیشین؛ نبودنِ ردیف یا مقدارِ دیگر ⇒ روشن', async () => {
  await login(standing, { uid: 'kill-phone', keyVersion: 1 }, 'ناصر');
  try {
    await setGuard(false);
    const legacy = await login(standing, { uid: 'kill-phone' });
    assert.equal(legacy.status, 201, 'خاموش ⇒ رفتارِ پیش از ۲.۱۴.۵');
    await setGuard('yes');
    assert.equal((await login(standing, { uid: 'kill-phone' })).status, 403, 'مقدارِ نادرست ⇒ روشن');
  } finally {
    await setGuard(undefined);
  }
  assert.equal((await login(standing, { uid: 'kill-phone' })).status, 403, 'بی ردیف ⇒ روشن');
});

test('مهاجرتِ ۳۲۰: rollback جدول و ردیفش را برمی‌دارد و دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '320_staff_device_keys.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async () => (await query("SELECT 1 FROM information_schema.tables WHERE table_name='staff_device_keys'")).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has(), 1);
  const r = await login(standing, { uid: 'after-rollback', keyVersion: 1 }, 'پس از برگشت');
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(typeof r.body.deviceKey, 'string');
});
