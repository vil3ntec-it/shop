'use strict';
/**
 * حداقل نسخهٔ پشتیبانی‌شدهٔ هر برنامه — v3 §۳۸.۹.
 *
 *   ۱) مقایسهٔ عددی (۲٫۱۰ > ۲٫۹) و فقط شکلِ درستِ نسخه پذیرفته است.
 *   ۲) هر برنامه حداقلِ خودش را دارد؛ دکان روی پمپ اثر ندارد. کلیدِ سراسریِ
 *      قدیمی فقط پشتوانه است.
 *   ۳) پنل: شکلِ نادرست ⇒ ۴۰۰؛ بالاتر از آخرین نسخه ⇒ ۴۰۰ و **هیچ** چیزِ
 *      همان درخواست ذخیره نمی‌شود؛ خالی ⇒ حداقل برداشته می‌شود.
 *   ۴) ⛔ پیش‌فرض: هیچ حداقلی نیست ⇒ `/api/config` و `/api/downloads` همان
 *      رشتهٔ خالیِ پیشین را می‌دهند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const C = require('../src/lib/app-compat');

let admin = '';

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
});
test.after(async () => { await h.stop(); });

const put = (body) => h.put('/api/admin/downloads', body, { token: admin });
const downloads = async (app) => (await h.get(`/api/downloads?app=${app}`)).body.downloads[0];
const cfgFor = async (app) => (await h.api('GET', '/api/config', { headers: app ? { 'X-App-Id': app } : {} })).body;

test('مقایسه عددی است و فقط شکلِ درستِ نسخه پذیرفته است', () => {
  assert.equal(C.compare('2.10', '2.9'), 1);
  assert.equal(C.compare('2.14.2', '2.14.10'), -1);
  assert.equal(C.compare('2.14', '2.14.0'), 0);
  assert.equal(C.valid('v2.14.2'), '2.14.2');
  for (const bad of ['', '2', 'abc', '2.x', '1.2.3.4.5', ' ', null, undefined]) assert.equal(C.valid(bad), '', String(bad));
  assert.equal(C.minVersionOf({}, 'shop'), '');
  assert.equal(C.minVersionOf({ min_app_version: '1.0.0' }, 'pump'), '1.0.0', 'کلیدِ قدیمی پشتوانه است');
  assert.equal(C.minVersionOf({ min_app_version: '1.0.0', min_app_version_shop: '2.1.0' }, 'shop'), '2.1.0');
  assert.equal(C.minVersionOf({ min_app_version_shop: 'nonsense' }, 'shop'), '');
});

test('⛔ پیش‌فرض: هیچ حداقلی نیست', async () => {
  assert.equal((await cfgFor()).minAppVersion, '');
  assert.equal((await downloads('shop')).minVersion, '');
  assert.equal((await downloads('pump')).minVersion, '');
});

test('پنل: حداقلِ دکان ذخیره می‌شود و فقط به دکان می‌رسد', async () => {
  const r = await put({ app: 'shop', version: '2.14.2', minVersion: '2.14.0' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.downloads[0].minVersion, '2.14.0');
  assert.equal((await downloads('shop')).minVersion, '2.14.0');
  assert.equal((await downloads('pump')).minVersion, '', 'پمپ شمارهٔ نسخهٔ خودش را دارد');
  assert.equal((await cfgFor()).minAppVersion, '2.14.0', 'بی X-App-Id ⇒ دکان');
  assert.equal((await cfgFor('pump')).minAppVersion, '');

  const audit = await query(`SELECT detail FROM audit_logs WHERE action='admin.downloads_updated' ORDER BY created_at DESC LIMIT 1`);
  assert.equal(audit.rows[0].detail.minVersion, '2.14.0');
});

test('پنل: شکلِ نادرست یا بالاتر از آخرین نسخه رد می‌شود و هیچ چیزِ همان درخواست ذخیره نمی‌شود', async () => {
  await put({ app: 'shop', version: '2.14.2', url: 'https://example.test/a.apk', minVersion: '2.14.0' });

  const bad = await put({ app: 'shop', minVersion: 'دو' });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_version');

  const above = await put({ app: 'shop', url: 'https://example.test/b.apk', minVersion: '2.15.0' });
  assert.equal(above.status, 400);
  assert.equal(above.body.error.code, 'min_above_latest');
  const d = await downloads('shop');
  assert.equal(d.minVersion, '2.14.0');
  assert.equal(d.url, 'https://example.test/a.apk', 'نشانی هم با درخواستِ ردشده عوض نشد');

  //  آخرین نسخه در همان درخواست بالا برود ⇒ پذیرفته است
  const both = await put({ app: 'shop', version: '2.15.0', minVersion: '2.15.0' });
  assert.equal(both.status, 200, JSON.stringify(both.body));
  assert.equal((await downloads('shop')).minVersion, '2.15.0');
});

test('پنل: خالی ⇒ حداقل برداشته می‌شود؛ پمپ مستقل است', async () => {
  assert.equal((await put({ app: 'pump', version: '1.4.0', minVersion: '1.3.0' })).status, 200);
  assert.equal((await put({ app: 'shop', minVersion: '' })).status, 200);
  assert.equal((await downloads('shop')).minVersion, '');
  assert.equal((await downloads('pump')).minVersion, '1.3.0');
  assert.equal((await cfgFor('pump')).minAppVersion, '1.3.0');
});

test('فقط مدیرِ ارشد حداقل را می‌گذارد', async () => {
  const u = await h.newUser('کنجکاو', 'shop');
  const r = await h.put('/api/admin/downloads', { app: 'shop', minVersion: '9.9.9' }, { token: u.accessToken });
  assert.ok([401, 403].includes(r.status), String(r.status));
  assert.equal((await downloads('shop')).minVersion, '');
});
