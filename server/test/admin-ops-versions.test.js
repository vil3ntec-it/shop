'use strict';
/**
 * داشبوردِ نسخهٔ دستگاه‌ها و هشدار — OPS-04 (§۶.۲۴).
 *
 *   ۱) فقط مدیر؛ بی توکن ۴۰۱، کاربرِ دکان راه ندارد.
 *   ۲) هر دستگاه یک بار، با تازه‌ترین ردیف از `sync_devices` و `sync_v3_devices`؛
 *      `v2.14.4` و `2.14.4` یکی‌اند؛ دستگاهِ خاموشِ بیش از ۳۰ روز شمرده نمی‌شود.
 *   ۳) زیرِ حداقل ⇒ down؛ پایین‌تر از آخرین نسخه ⇒ warn؛ بی آخرین نسخه کهنه سنجیده نمی‌شود.
 *   ۴) هر برنامه حدِ خودش را دارد (دکان و پمپ جدا).
 *   ۵) ⛔ هیچ شناسهٔ حساب یا دستگاهی در پاسخ نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const plans = require('../src/lib/plans');
const health = require('../src/lib/system-health');

let admin = '';
const DAY = 24 * 60 * 60 * 1000;
const get = (token) => h.get('/api/admin/system/versions', { token });
const appOf = (b, app) => b.apps.find((a) => a.app === app);
const vOf = (a, version) => a.versions.find((x) => x.version === version);

async function v1(app, acc, dev, version, ageMs) {
  await query(
    `INSERT INTO sync_devices (app, account_kind, account_id, device_id, app_version, last_seen_at)
     VALUES ($1,$2,$3,$4,$5,$6)`,
    [app, app, acc, dev, version, now() - ageMs]
  );
}
async function v3(app, acc, dev, version, ageMs) {
  await query(
    `INSERT INTO sync_v3_devices (app, account_kind, account_id, device_id, app_version, last_seen_at)
     VALUES ($1,$2,$3,$4,$5,$6)`,
    [app, app, acc, dev, version, now() - ageMs]
  );
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
  //  دکان: ۳ × 2.14.4 (یکی با v)، ۱ × 2.13.0، ۱ × نامعلوم، ۱ × خاموشِ ۴۰ روزه
  await v1('shop', 'acc-secret-a', 'dev-secret-1', '2.14.4', 1 * DAY);
  await v1('shop', 'acc-secret-a', 'dev-secret-2', 'v2.14.4', 2 * DAY);
  await v1('shop', 'acc-secret-b', 'dev-secret-3', '2.13.0', 3 * DAY);
  await v1('shop', 'acc-secret-b', 'dev-secret-4', '', 1 * DAY);
  await v1('shop', 'acc-secret-c', 'dev-secret-5', '2.9.0', 40 * DAY);
  //  یک دستگاه در هر دو جدول: v1 کهنه‌تر (2.13.0)، v3 تازه‌تر (2.14.4) ⇒ یک بار، 2.14.4
  await v1('shop', 'acc-secret-d', 'dev-secret-6', '2.13.0', 5 * DAY);
  await v3('shop', 'acc-secret-d', 'dev-secret-6', '2.14.4', 1 * DAY);
  //  ردیفِ تازه‌ترِ بی‌نسخه (v3) ⇒ نسخهٔ ردیفِ دیگرِ همان دستگاه (v1: 2.13.0)
  await v1('shop', 'acc-secret-e', 'dev-secret-7', '2.13.0', 6 * DAY);
  await v3('shop', 'acc-secret-e', 'dev-secret-7', '', 1 * DAY);
  //  پمپ: ۲ × 1.4.0
  await v1('pump', 'st-secret-1', 'pc-secret-1', '1.4.0', 1 * DAY);
  await v3('pump', 'st-secret-2', 'pc-secret-2', '1.4.0', 1 * DAY);
});
test.after(async () => { await h.stop(); });

test('فقط مدیر: بی توکن ۴۰۱، کاربرِ دکان راه ندارد', async () => {
  assert.equal((await get(null)).status, 401);
  const u = await h.newUser('دکان‌دارِ کنجکاو', 'shop');
  const r = await get(u.accessToken);
  assert.ok([401, 403].includes(r.status), String(r.status));
});

test('OPS-04: هر دستگاهِ فعال یک بار، بر پایهٔ تازه‌ترین نسخه', async () => {
  const r = await get(admin);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(r.body.activeDays, 30);
  const shop = appOf(r.body, 'shop');
  assert.equal(shop.devices, 6, JSON.stringify(shop));
  assert.equal(r.body.truncated, false);
  assert.equal(shop.versionsTruncated, false);
  assert.equal(vOf(shop, '2.14.4').devices, 3);
  assert.equal(vOf(shop, '2.13.0').devices, 2);
  //  «آخرین دیده‌شدن» از ردیفِ تازه‌تر (۱ روز پیش)، نه از ردیفِ نسخه‌دارِ ۶ روزه
  assert.ok(vOf(shop, '2.13.0').lastSeenAt > now() - 2 * DAY);
  assert.equal(vOf(shop, '').devices, 1);
  assert.equal(shop.unknown, 1);
  assert.ok(!vOf(shop, '2.9.0'), 'دستگاهِ خاموشِ ۴۰ روزه شمرده شد');
  //  ترتیب: تازه به کهنه، نامعلوم آخر
  assert.deepEqual(shop.versions.map((x) => x.version), ['2.14.4', '2.13.0', '']);
  //  بی حداقل و آخرین نسخه: هشداری نیست و «کهنه» حدس زده نمی‌شود
  assert.equal(shop.status, 'ok');
  assert.equal(shop.outdated, null);
  assert.equal(shop.belowMin, 0);
  assert.equal(appOf(r.body, 'pump').devices, 2);
});

test('هشدار: زیرِ حداقل ⇒ down؛ فقط کهنه ⇒ warn؛ هر برنامه حدِ خودش', async () => {
  try {
    await plans.setConfig('latest_version_shop', '2.14.4');
    await plans.setConfig('min_app_version_shop', '2.14.0');
    await plans.setConfig('latest_version_pump', '1.4.0');
    let b = (await get(admin)).body;
    let shop = appOf(b, 'shop');
    assert.deepEqual([shop.latest, shop.minVersion], ['2.14.4', '2.14.0']);
    assert.equal(shop.belowMin, 2);
    assert.equal(vOf(shop, '2.13.0').state, 'below_min');
    assert.equal(vOf(shop, '2.14.4').state, 'current');
    assert.equal(vOf(shop, '').state, 'unknown');
    assert.equal(shop.status, 'down');
    assert.equal(b.status, 'down');
    assert.equal(appOf(b, 'pump').status, 'ok', 'حدِ دکان به پمپ رسید');

    //  بی حداقل: همان دستگاه فقط «کهنه» است
    await plans.setConfig('min_app_version_shop', '');
    b = (await get(admin)).body;
    shop = appOf(b, 'shop');
    assert.equal(shop.belowMin, 0);
    assert.equal(shop.outdated, 2);
    assert.equal(vOf(shop, '2.13.0').state, 'outdated');
    assert.equal(shop.status, 'warn');
    assert.equal(b.status, 'warn');

    //  حداقلِ سراسریِ قدیمی (`min_app_version`) پشتوانه است
    await plans.setConfig('min_app_version', '2.14.0');
    assert.equal(appOf((await get(admin)).body, 'shop').belowMin, 2);
  } finally {
    for (const k of ['latest_version_shop', 'min_app_version_shop', 'latest_version_pump', 'min_app_version']) {
      await plans.setConfig(k, '');
    }
  }
});

test('بیش از سقفِ گروه ⇒ truncated، پرشمارها می‌مانند', async () => {
  await query(
    `INSERT INTO sync_devices (app, account_kind, account_id, device_id, app_version, last_seen_at)
     SELECT 'shop', 'shop', 'acc-many', 'd' || g, '9.' || g || '.0', $1 FROM generate_series(1, $2::int) g`,
    [now() - DAY, health.VERSION_LIMITS.maxGroups + 5]
  );
  try {
    const b = (await get(admin)).body;
    assert.equal(b.truncated, true);
    //  پرشمارها (2.14.4 با ۳، 2.13.0 با ۲) نه در سقفِ گروه بریده می‌شوند نه در سقفِ نمایش
    const shop = appOf(b, 'shop');
    assert.equal(vOf(shop, '2.14.4').devices, 3);
    assert.equal(vOf(shop, '2.13.0').devices, 2);
    assert.equal(shop.versionsTruncated, true);
    assert.equal(shop.versions.length, health.VERSION_LIMITS.maxVersionsPerApp);
  } finally {
    await query(`DELETE FROM sync_devices WHERE account_id='acc-many'`);
  }
});

test('versionReport: نسخهٔ نادرست «نامعلوم» است، نامِ برنامهٔ ناجور کنار می‌رود', () => {
  const out = health.versionReport([
    { app: 'shop', app_version: '2.14.4-beta', n: 2, last_seen: 5 },
    { app: 'shop', app_version: '2.14.4', n: 1, last_seen: 9 },
    { app: '<script>', app_version: '1.0', n: 7, last_seen: 1 },
  ], { latest_version_shop: '2.14.4' });
  const shop = out.find((a) => a.app === 'shop');
  assert.equal(shop.unknown, 2);
  assert.equal(shop.devices, 3);
  assert.equal(shop.outdated, 0);
  assert.ok(!out.some((a) => a.app === '<script>'));
  assert.deepEqual(out.map((a) => a.app), ['shop', 'pump']);
});

test('⛔ هیچ شناسهٔ حساب یا دستگاهی در پاسخ نیست', async () => {
  const text = JSON.stringify((await get(admin)).body);
  assert.ok(!/secret/.test(text), text.slice(0, 200));
});
