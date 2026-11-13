'use strict';
/**
 * همگام‌سازیِ زندهٔ تنظیماتِ دکان — `/api/shop/settings-sync` (مهاجرتِ ۳۰۰؛ `docs/DATA-SAFETY-fa.md` «تنظیماتِ دکان»).
 *
 *   • پرچمِ خاموش (پیش‌فرض): `GET` فقط `{enabled:false}`، `PUT` ۴۰۳ و هیچ ردیفی.
 *   • آخرین نویسنده برنده، هر کلید جدا، با ساعتِ سرور (ساعتِ دستگاه خوانده نمی‌شود)؛ `rev` ِ پیاپی؛ همان مقدار ⇒ هیچ.
 *   • فقط صاحب و مدیر می‌نویسند؛ شاگرد و صندوق‌دار می‌خوانند و نمی‌نویسند.
 *   • سابقه: هر تغییر یک ردیفِ `shop_setting_keys_log` (پیش/پس/کاربر/نقش/دستگاه) و یک `settings.sync` در سابقهٔ دکان.
 *   • جدایی: دکانِ دیگر نه می‌بیند نه می‌نویسد؛ پمپ به این در نمی‌رسد.
 *   • شکل: انبار، نامِ کلید، نوع و اندازه سنجیده می‌شوند (۴۰۰ `bad_setting`).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const BASE = '/api/shop/settings-sync';
let admin = '';
let shopId;
let owner;
let manager;
let staff;
let other;

const flag = (value, scope) => h.put('/api/admin/pump/live-config', { scope, key: 'shop.settings_sync', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const put = (who, entries, extra = {}) => { resetAll(); return h.put(BASE, { entries, ...extra }, { token: who.accessToken }); };
const get = (who, since = 0) => { resetAll(); return h.get(`${BASE}?since=${since}`, { token: who.accessToken }); };
const P = (key, t, v) => ({ store: 'tohid-policy', key, t, v });

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ss-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'ss-admin', password: 'Admin!12345' })).body.token;
  const o = await h.newUser('صاحبِ دکانِ تنظیم', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ تنظیم' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ تنظیم', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ تنظیم', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
  const b = await h.newUser('صاحبِ دکانِ دیگر', 'shop');
  assert.equal((await h.post('/api/shop', { name: 'دکانِ دیگر' }, { token: b.accessToken })).status, 201);
  other = await h.signIn(b, 'shop');
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): GET فقط enabled:false، PUT ۴۰۳ و هیچ ردیفی', async () => {
  assert.equal((await h.get(BASE)).status, 401);
  assert.deepEqual((await get(owner)).body, { enabled: false });
  const w = await put(owner, [P('cash_rounding', 'l', 500)]);
  assert.equal(w.status, 403, JSON.stringify(w.body));
  assert.equal(w.body.error.code, 'settings_sync_disabled');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_setting_keys')).n, 0);
});

test('آخرین نویسنده با ساعتِ سرور برنده، هر کلید جدا؛ rev ِ پیاپی؛ همان مقدار هیچ', async () => {
  await flag(true, shopId);
  const before = Date.now();
  const a = await put(owner, [P('cash_rounding', 'l', 500), P('return_window_days', 'i', 7), P('price_lists', 'b', true)],
    { device_id: 'dev-owner' });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.applied.length, 3);
  assert.deepEqual(a.body.applied.map((x) => x.rev), [1, 2, 3]);
  //  مدیر یک کلید را عوض می‌کند — «at» ِ دستگاه (کهنه) نادیده
  const b = await put(manager, [{ ...P('cash_rounding', 'l', 1000), at: 1 }], { device_id: 'dev-manager' });
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal(b.body.rev, 4);
  assert.ok(b.body.applied[0].at >= before, 'ساعتِ سرور، نه ساعتِ دستگاه');

  const all = (await get(staff)).body;
  assert.equal(all.enabled, true);
  assert.equal(all.rev, 4);
  assert.equal(all.permissions.write, false, 'شاگرد می‌خواند ولی نمی‌نویسد');
  const byKey = Object.fromEntries(all.rows.map((r) => [r.key, r]));
  assert.equal(byKey.cash_rounding.v, 1000);
  assert.equal(byKey.cash_rounding.rev, 4);
  assert.equal(byKey.cash_rounding.by, manager.user.id);
  assert.equal(byKey.return_window_days.v, 7, 'کلیدِ دیگر را نوشتنِ مدیر نبرد');
  assert.equal(byKey.price_lists.v, true);
  assert.deepEqual((await get(owner, 3)).body.rows.map((r) => r.key), ['cash_rounding'], 'فقط تازه‌تر از since');
  assert.equal((await get(owner)).body.permissions.write, true);

  const same = await put(owner, [P('return_window_days', 'i', 7)]);
  assert.equal(same.status, 200);
  assert.deepEqual(same.body.applied, []);
  assert.equal(same.body.unchanged, 1);
  assert.equal(same.body.rev, 4, 'همان مقدار rev نمی‌سازد');

  //  برداشتن: ردیفِ del، و برداشتنِ کلیدی که هرگز نبود هیچ
  const del = await put(owner, [P('price_lists', 'del', null), P('never_set', 'del', null)]);
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.deepEqual(del.body.applied.map((x) => x.key), ['price_lists']);
  const after = (await get(owner, 4)).body.rows;
  assert.deepEqual(after.map((r) => [r.key, r.t, r.v]), [['price_lists', 'del', null]]);
});

test('فقط صاحب و مدیر: شاگرد ۴۰۳؛ سابقه: پیش/پس/کاربر/نقش/دستگاه و settings.sync', async () => {
  const s = await put(staff, [P('cash_rounding', 'l', 1)]);
  assert.equal(s.status, 403, JSON.stringify(s.body));
  assert.equal((await one("SELECT value FROM shop_setting_keys WHERE shop_id=$1 AND key='cash_rounding'", [shopId])).value, 1000);

  const log = await many("SELECT * FROM shop_setting_keys_log WHERE shop_id=$1 AND key='cash_rounding' ORDER BY rev", [shopId]);
  assert.equal(log.length, 2);
  assert.deepEqual([log[0].old_value, log[0].new_value, log[0].role, log[0].device_id], [null, 500, 'owner', 'dev-owner']);
  assert.deepEqual([log[1].old_value, log[1].new_value, log[1].role, log[1].by_user, log[1].device_id],
    [500, 1000, 'manager', manager.user.id, 'dev-manager']);
  const aud = await many("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='settings.sync' ORDER BY created_at", [shopId]);
  assert.ok(aud.length >= 2);
  const detail = typeof aud[0].detail === 'string' ? JSON.parse(aud[0].detail) : aud[0].detail;
  assert.ok(detail.changed.split(',').includes('tohid-policy:cash_rounding'), JSON.stringify(detail));
});

test('لایهٔ نقش (STF-01..03): مدیری که صاحب «تنظیمات» را برایش بسته `write: false` می‌گیرد — همان سنجشِ PUT', async () => {
  //  پیش از این `permissions.write` فقط نقش را می‌دید ⇒ `true`، و برنامه (`ShopSettingsPlan.plan`) تغییرِ محلی را هر دور
  //  می‌فرستاد (۴۰۳) و مقدارِ دکان را هرگز نمی‌نشاند: قاعدهٔ پولِ گوشیِ مدیر برای همیشه جدا از دکان
  resetAll();
  const deny = await h.roleLayer(owner.accessToken, 'manager', { 'settings.write': { mode: 'deny' } });
  assert.equal(deny.status, 200, JSON.stringify(deny.body));
  try {
    const r = (await get(manager)).body;
    assert.equal(r.enabled, true);
    assert.equal(r.permissions.write, false, 'مدیرِ بسته‌شده می‌خواند ولی نمی‌نویسد');
    const w = await put(manager, [P('cash_rounding', 'l', 7)]);
    assert.deepEqual([w.status, w.body.error?.code], [403, 'permission_denied'], 'سرور همان را می‌گوید که GET گفت');
    assert.equal((await get(owner)).body.permissions.write, true, 'صاحب هرگز بسته نمی‌شود');
  } finally {
    resetAll();
    assert.equal((await h.roleLayer(owner.accessToken, 'manager', {})).status, 200);
  }
  assert.equal((await get(manager)).body.permissions.write, true, 'لایهٔ خالی = برگشت به نقش');
});

test('جدایی: دکانِ دیگر نه می‌بیند نه می‌نویسد؛ پمپ به این در نمی‌رسد', async () => {
  await flag(true, 'all');
  const mine = (await get(other)).body;
  assert.equal(mine.enabled, true);
  assert.deepEqual(mine.rows, [], 'ردیف‌های دکانِ «الف» برای «ب» نیست');
  assert.equal((await put(other, [P('cash_rounding', 'l', 5)])).status, 200);
  assert.equal((await one("SELECT value FROM shop_setting_keys WHERE shop_id=$1 AND key='cash_rounding'", [shopId])).value, 1000);
  assert.equal((await get(other)).body.rows.length, 1);

  const u = await h.newUser('صاحبِ پمپ', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپ تنظیم', code: 'ss-pump' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  resetAll();
  const r = await h.get(BASE, { token: u.accessToken });
  assert.ok([401, 403, 404].includes(r.status), `${r.status} ${JSON.stringify(r.body)}`);
  resetAll();
  const w = await h.put(BASE, { entries: [P('cash_rounding', 'l', 5)] }, { token: u.accessToken });
  assert.ok([401, 403, 404].includes(w.status), `${w.status} ${JSON.stringify(w.body)}`);
  await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'shop.settings_sync', value: false }, { token: admin });
});

test('شکل: انبار، نامِ کلید، نوع، اندازه و شمار سنجیده می‌شوند (۴۰۰ bad_setting)', async () => {
  const bad = async (entries, code = 'bad_setting') => {
    const r = await put(owner, entries);
    assert.equal(r.status, 400, `${JSON.stringify(entries).slice(0, 80)} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code);
  };
  await bad([]);
  await bad([{ store: 'tohid-session', key: 'token', t: 's', v: 'x' }]);
  await bad([P('loyalty_shop:abc', 's', '{}')]);
  await bad([P('Cash', 'l', 1)]);
  await bad([P('cash_rounding', 'x', 1)]);
  await bad([P('cash_rounding', 'l', '500')]);
  await bad([P('cash_rounding', 'l', 1.5)]);
  await bad([P('flag', 'b', 'true')]);
  await bad([P('day_end_hour', 'i', 2 ** 40)]);
  await bad([P('ratio', 'f', null)]);
  await bad([P('text', 's', 'x'.repeat(5000))]);
  await bad([P('tags', 'set', ['a', 7])]);
  await bad(Array.from({ length: 201 }, (_, i) => P(`k_${i}`, 'b', true)));
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM shop_setting_keys WHERE shop_id=$1 AND key LIKE 'k\\_%'", [shopId])).n, 0);
  //  مجموعه مرتب و بی تکرار ذخیره می‌شود
  const ok = await put(owner, [P('return_reasons', 'set', ['ب', 'الف', 'ب'])]);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual((await one("SELECT value FROM shop_setting_keys WHERE shop_id=$1 AND key='return_reasons'", [shopId])).value, ['الف', 'ب']);
});

test('مهاجرتِ ۳۰۰: rollback دو جدول و ردیفِ خودش را برمی‌دارد، جدولِ قدیمِ shop_settings دست نمی‌خورد؛ دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '300_shop_settings_sync.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async (t) => (await query('SELECT 1 FROM information_schema.tables WHERE table_name=$1', [t])).rows.length;
  const legacy = await one('SELECT COUNT(*)::int AS n FROM shop_settings');
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has('shop_setting_keys'), 0);
  assert.equal(await has('shop_setting_keys_log'), 0);
  assert.equal(await has('shop_settings'), 1, 'جدولِ قدیمِ تنظیمات (۰۰۲) می‌ماند');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_settings')).n, legacy.n);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has('shop_setting_keys'), 1);
  assert.equal(await has('shop_setting_keys_log'), 1);
});

