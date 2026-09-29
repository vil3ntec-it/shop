'use strict';
/**
 * «تنظیماتِ زنده» ی برنامهٔ پمپ — lib/live-config.js و سه در.
 *
 * خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۷): «برنامه رو جوری کن که بعدن اگه قابلیتی
 * خاستم بتونم راحت روش اجرا کنم… لایف اپدیت باشه… روی سرور فشاری نیاد…
 * هیچ منطقی رو دست نزن.»
 *
 * آن‌چه این‌جا قفل می‌شود:
 *   ۱) نسخه روی همان پاسخِ دقیقه‌ایِ `/rate` می‌آید — درخواستِ تازه‌ای نیست.
 *   ۲) در سکوت نسخه عوض نمی‌شود و به دیتابیس هم نمی‌رود.
 *   ۳) نوشتن ⇒ نسخه جلو می‌رود؛ پاک کردن هم.
 *   ۴) هر پمپ فقط `all` + خودش را می‌بیند؛ مقدارِ خودش جلو می‌افتد.
 *   ۵) پاسخِ قدیمیِ `/rate` (`cmd`) دست نخورد.
 *   ۶) کلید و اندازه و scope سنجیده می‌شوند؛ دستگاه نمی‌تواند بنویسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const live = require('../src/lib/live-config');

let admin = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'lcadmin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const login = await h.post('/api/admin/login', { username: 'lcadmin', password: 'Admin!12345' });
  assert.equal(login.status, 200, JSON.stringify(login.body));
  admin = login.body.token;
});
test.after(async () => { await h.stop(); });

async function pump(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: `pc-lc-${name}`, name: 'کامپیوتر', platform: 'windows' } }, { token: u.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  return { id: made.body.station.id, dev: bound.body.deviceToken };
}

const put = (scope, key, value) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });

test('۱) نسخه روی پاسخِ `/rate` می‌آید، و پاسخِ قدیمی (cmd) سرِ جایش است', async () => {
  const a = await pump('نسخه');
  const r = await h.get('/api/pump/device/rate', { token: a.dev });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok('cmd' in r.body, 'کلیدِ cmd برای برنامه‌های امروز لازم است');
  assert.equal(r.body.cmd, null);
  assert.match(String(r.body.liveConfig), /^\d+\.\d+$/);
});

test('۲) در سکوت نسخه عوض نمی‌شود — و از حافظه می‌آید، نه دیتابیس', async () => {
  const a = await pump('سکوت');
  const v1 = (await h.get('/api/pump/device/rate', { token: a.dev })).body.liveConfig;
  const v2 = (await h.get('/api/pump/device/rate', { token: a.dev })).body.liveConfig;
  assert.equal(v1, v2);
  //  ⛔ دیتابیس که کلاً رفت، نسخه همچنان از حافظه جواب می‌دهد
  const db = require('../src/db');
  const realOne = db.one;
  let hits = 0;
  db.one = async (...args) => { if (String(args[0]).includes('live_config_versions')) hits++; return realOne(...args); };
  try {
    await live.versionOf(a.id);
    await live.versionOf(a.id);
  } finally { db.one = realOne; }
  assert.equal(hits, 0, 'نسخهٔ دیده‌شده دوباره از دیتابیس خوانده نمی‌شود');
});

test('۳) نوشتن ⇒ نسخه جلو می‌رود و برگه همان مقدار را می‌دهد؛ پاک کردن هم نسخه را جلو می‌برد', async () => {
  const a = await pump('نوشتن');
  const v0 = (await h.get('/api/pump/device/rate', { token: a.dev })).body.liveConfig;
  const w = await put('all', 'banner.text', 'سلام به همه');
  assert.equal(w.status, 200, JSON.stringify(w.body));
  const v1 = (await h.get('/api/pump/device/rate', { token: a.dev })).body.liveConfig;
  assert.notEqual(v1, v0, 'نسخه باید عوض شود تا برنامه بفهمد');
  const sheet = await h.get('/api/pump/device/live-config', { token: a.dev });
  assert.equal(sheet.status, 200);
  assert.equal(sheet.body.values['banner.text'], 'سلام به همه');
  assert.equal(sheet.body.version, v1);

  const del = await h.del('/api/admin/pump/live-config?scope=all&key=banner.text', { token: admin });
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.equal(del.body.removed, true);
  const v2 = (await h.get('/api/pump/device/rate', { token: a.dev })).body.liveConfig;
  assert.notEqual(v2, v1, 'پاک کردن هم باید دیده شود');
  assert.equal('banner.text' in (await h.get('/api/pump/device/live-config', { token: a.dev })).body.values, false);
});

test('۴) هر پمپ فقط «همه» + خودش را می‌بیند، و مقدارِ خودش جلو می‌افتد', async () => {
  const a = await pump('الف');
  const b = await pump('ب');
  await put('all', 'feature.x', false);
  await put(a.id, 'feature.x', true);
  await put(a.id, 'only.a', { n: 1 });
  const sa = (await h.get('/api/pump/device/live-config', { token: a.dev })).body.values;
  const sb = (await h.get('/api/pump/device/live-config', { token: b.dev })).body.values;
  assert.equal(sa['feature.x'], true, 'مقدارِ خودِ پمپ بر «همه» جلو می‌افتد');
  assert.deepEqual(sa['only.a'], { n: 1 });
  assert.equal(sb['feature.x'], false, 'پمپِ ب مقدارِ «همه» را می‌گیرد');
  assert.equal('only.a' in sb, false, '⛔ پمپِ ب مقدارِ پمپِ الف را نمی‌بیند');
  //  نوشتن برای یک پمپ نسخهٔ پمپِ دیگر را عوض نمی‌کند
  const vb1 = (await h.get('/api/pump/device/rate', { token: b.dev })).body.liveConfig;
  await put(a.id, 'only.a', { n: 2 });
  const vb2 = (await h.get('/api/pump/device/rate', { token: b.dev })).body.liveConfig;
  assert.equal(vb2, vb1);
});

test('۵) سنجش‌ها: کلید، اندازه، scope؛ و دستگاه نمی‌تواند بنویسد', async () => {
  assert.equal((await put('all', 'Bad Key', 1)).status, 400);
  assert.equal((await put('all', 'ok.key', 'x'.repeat(live.MAX_VALUE + 10))).status, 400);
  assert.equal((await put('../etc', 'ok.key', 1)).status, 400);
  assert.equal((await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'a.b' })).status, 401, 'بی ورودِ مدیر نه');
  const a = await pump('دستگاه');
  const byDevice = await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'a.b', value: 1 }, { token: a.dev });
  assert.ok(byDevice.status === 401 || byDevice.status === 403, `دستگاه ${byDevice.status} گرفت`);
  assert.equal((await h.get('/api/pump/device/live-config')).status, 401, 'بی توکنِ دستگاه نه');
});

test('۶) فهرستِ پنل: مقدار، زمان و نویسنده', async () => {
  await put('all', 'list.demo', [1, 2, 3]);
  const r = await h.get('/api/admin/pump/live-config?scope=all', { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const row = r.body.items.find((i) => i.key === 'list.demo');
  assert.deepEqual(row.value, [1, 2, 3]);
  assert.ok(row.updatedAt > 0);
  assert.ok(row.updatedBy.length > 0);
});
