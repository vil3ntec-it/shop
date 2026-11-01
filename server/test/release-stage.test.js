'use strict';
/**
 * OPS-01 — انتشارِ مرحله‌ای با برچسبِ نسخه‌دار، پشتِ پیکربندی (`lib/release-stage.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۰) ⛔ بی کلیدِ `stage.<برچسب>` (پیش‌فرض) هیچ چیز عوض نمی‌شود: همان برچسبِ روان، و
 *      برچسبِ نسخه‌دار ۴۰۴ (هیچ انتشارِ دلبخواهی از مخزن سرو نمی‌شود).
 *   ۱) سطل پایدار، در هر نسخه از نو، و با بالا رفتنِ درصد کسی بیرون نمی‌افتد؛ پخشِ تقریباً یکنواخت.
 *   ۲) درونِ مرحله + `staged=1` ⇒ برچسبِ `tohid-native-v<نسخه>`؛ برنامهٔ قدیمی (بی `staged`)،
 *      بی شناسهٔ دستگاه، بیرونِ سطل، درصدِ ۰، نسخهٔ نه‌بالاتر یا انتشارِ نسخه‌دارِ نبوده ⇒ برچسبِ روان.
 *   ۳) پیکربندیِ ناجور ⇒ مرحله‌ای نیست (fail-closed)؛ برچسبِ نسخه‌دارِ دیگر ۴۰۴.
 *   ۴) پنل: فقط مدیرِ ارشد؛ ورودیِ ناجور ۴۰۰ و بی ذخیره؛ هر تغییر در سابقه و همان دم مؤثر.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

const stage = require('../src/lib/release-stage');

let fake, h, root = '', plain = '';
let stagedUp = true;
const APK = Buffer.from('fake-apk-bytes-0123456789');
const APK70 = Buffer.from('staged-apk-3.2.70-bytes');

test.before(async () => {
  //  GitHubِ ساختگی: روان 3.2.50، آزمایشی 3.2.60، نسخه‌دار 3.2.70 و 3.2.40 (کهنه‌تر از روان)
  fake = http.createServer((req, res) => {
    const rel = (body, assets) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ body, published_at: '2026-10-30T00:00:00Z', assets })); };
    const a = (id, name, size) => ({ id, name, size, updated_at: '2026-10-30T00:00:00Z' });
    switch (req.url) {
      case '/repos/o/r/releases/tags/tohid-native':
        return rel('روان', [a(1, 'Tohid-Native-3.2.50.apk', APK.length), a(2, 'version.txt', 7)]);
      case '/repos/o/r/releases/tags/tohid-native-beta':
        return rel('آزمایشی', [a(11, 'Tohid-Native-3.2.60.apk.beta', APK.length), a(12, 'version.txt', 7)]);
      case '/repos/o/r/releases/tags/tohid-native-v3.2.70':
        if (!stagedUp) { res.statusCode = 500; return res.end('{}'); }
        return rel('مرحله‌ای', [a(21, 'Tohid-Native-3.2.70.apk', APK70.length), a(22, 'version.txt', 7), a(23, 'SHA256SUMS.txt', 3)]);
      case '/repos/o/r/releases/tags/tohid-native-v3.2.40':
        return rel('کهنه', [a(31, 'Tohid-Native-3.2.40.apk', APK.length), a(32, 'version.txt', 7)]);
      case '/repos/o/r/releases/tags/tohid-native-v3.2.71':
        return rel('دیگر', [a(41, 'Tohid-Native-3.2.71.apk', APK.length), a(42, 'version.txt', 7)]);
      //  برچسبِ ۳.۲.۷۲ ولی version.txt ِ آن ۳.۲.۹۹ (انتشارِ اشتباه‌برچسب)
      case '/repos/o/r/releases/tags/tohid-native-v3.2.72':
        return rel('اشتباه', [a(51, 'Tohid-Native-3.2.99.apk', APK.length), a(52, 'version.txt', 7)]);
      case '/repos/o/r/releases/assets/52': return res.end('3.2.99\n');
      case '/repos/o/r/releases/assets/1': return res.end(APK);
      case '/repos/o/r/releases/assets/2': return res.end('3.2.50\n');
      case '/repos/o/r/releases/assets/12': return res.end('3.2.60\n');
      case '/repos/o/r/releases/assets/21': return res.end(APK70);
      case '/repos/o/r/releases/assets/22': return res.end('3.2.70\n');
      case '/repos/o/r/releases/assets/23': return res.end('ok\n');
      case '/repos/o/r/releases/assets/32': return res.end('3.2.40\n');
      case '/repos/o/r/releases/assets/41': return res.end(APK);
      case '/repos/o/r/releases/assets/42': return res.end('3.2.71\n');
      default: res.statusCode = 404; return res.end('{}');
    }
  });
  await new Promise((r) => fake.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${fake.address().port}`;
  process.env.RELEASE_REPO = 'o/r';
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-stage-'));
  h = require('./helpers');
  await h.start();

  const { query, newId, now } = require('../src/db');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'stageroot','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'stageroot', password: 'Root!12345' })).body.token;
  assert.ok(root);
  const made = await h.post('/api/admin/admins', { username: 'stageclerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.ok([200, 201].includes(made.status), JSON.stringify(made.body));
  plain = (await h.post('/api/admin/login', { username: 'stageclerk', password: 'Clerk!12345' })).body.token;
  assert.ok(plain);
});
test.after(async () => { await h.stop(); fake.close(); });

const releases = () => require('../src/routes/releases');
const liveConfig = () => require('../src/lib/live-config');
async function setRaw(key, value) {
  if (value === undefined) await liveConfig().remove('release', key);
  else await liveConfig().set('release', key, value, 'test');
  releases()._forgetBeta();
  releases()._cache.clear();
}
const latest = (q) => h.api('GET', `/api/releases/tohid-native/latest${q}`);

/** دستگاه‌هایی که برای `3.2.70` درون/بیرونِ سطلِ `percent` اند */
function devices(percent, inside, n = 3) {
  const out = [];
  for (let i = 0; out.length < n && i < 10_000; i++) {
    const d = `dev-${i}`;
    if ((stage.bucketOf('tohid-native', '3.2.70', d) < percent) === inside) out.push(d);
  }
  return out;
}

/* ══ ۱) منطق ═══════════════════════════════════════════════════════ */

test('منطق: پیکربندیِ ناجور ⇒ null؛ برچسبِ نسخه‌دار فقط برای پایه‌های شناخته', () => {
  assert.deepEqual(stage.parseStage({ version: '2.14.5', percent: 10 }), { version: '2.14.5', percent: 10 });
  assert.deepEqual(stage.parseStage('{"version":"2.14.5","percent":0}'), { version: '2.14.5', percent: 0 });
  for (const bad of [null, undefined, 'x', '{', [], 10, true, {},
    { version: '2.14.5' }, { percent: 10 }, { version: '2.14.5', percent: 101 }, { version: '2.14.5', percent: -1 },
    { version: '2.14.5', percent: 10.5 }, { version: '2.14.5', percent: '10' }, { version: 'v2.14.5', percent: 10 },
    { version: '2', percent: 10 }, { version: '2.14.5-beta', percent: 10 }, { version: '../x', percent: 10 }]) {
    assert.equal(stage.parseStage(bad), null, JSON.stringify(bad));
  }
  const bases = ['tohid-native', 'tohid-desktop'];
  assert.deepEqual(stage.parseVersionedTag('tohid-native-v2.14.5', bases), { base: 'tohid-native', version: '2.14.5' });
  for (const bad of ['tohid-native', 'tohid-native-beta', 'tohid-native-v', 'tohid-native-vx', 'tohid-admin-v1.2.3', 'tohid-native-v1.2.3/..']) {
    assert.equal(stage.parseVersionedTag(bad, bases), null, bad);
  }
  assert.equal(stage.versionedTag('tohid-desktop', '1.2.3'), 'tohid-desktop-v1.2.3');
  //  فقط کلیدِ `stage.<پایهٔ شناخته>` با مقدارِ درست
  const m = stage.stagesOf([
    { key: 'stage.tohid-native', value: { version: '1.2.3', percent: 5 } },
    { key: 'stage.tohid-admin', value: { version: '1.2.3', percent: 5 } },
    { key: 'stage.tohid-desktop', value: { version: '1.2.3', percent: 500 } },
    { key: 'beta.shops', value: ['a'] },
  ], bases);
  assert.deepEqual([...m.entries()], [['tohid-native', { version: '1.2.3', percent: 5 }]]);
});

test('منطق: سطل پایدار، در هر نسخه از نو، یکنواخت؛ بالا رفتنِ درصد کسی را بیرون نمی‌اندازد', () => {
  const ids = Array.from({ length: 4000 }, (_, i) => `device-${i}`);
  assert.equal(stage.bucketOf('tohid-native', '3.2.70', 'd1'), stage.bucketOf('tohid-native', '3.2.70', 'd1'));
  const at = (p, v = '3.2.70') => new Set(ids.filter((d) => stage.inStage({ version: v, percent: p }, 'tohid-native', d)));
  const p10 = at(10);
  const p50 = at(50);
  assert.ok(p10.size > 4000 * 0.08 && p10.size < 4000 * 0.12, `۱۰٪ ≈ ${p10.size}`);
  assert.ok(p50.size > 4000 * 0.46 && p50.size < 4000 * 0.54, `۵۰٪ ≈ ${p50.size}`);
  for (const d of p10) assert.ok(p50.has(d), 'درونِ ۱۰٪ درونِ ۵۰٪ هم هست');
  assert.equal(at(0).size, 0);
  //  مرزِ سطل: سطلِ برابر با درصد بیرون است، یکی کمتر درون
  const edge = (b) => ids.find((d) => stage.bucketOf('tohid-native', '3.2.70', d) === b);
  assert.equal(stage.inStage({ version: '3.2.70', percent: 10 }, 'tohid-native', edge(10)), false, 'سطلِ ۱۰ در ۱۰٪ نیست');
  assert.equal(stage.inStage({ version: '3.2.70', percent: 10 }, 'tohid-native', edge(9)), true, 'سطلِ ۹ در ۱۰٪ هست');
  assert.equal(at(100).size, 4000);
  //  نسخهٔ دیگر ⇒ سطل‌بندیِ دیگر (همیشه همان دستگاه‌ها پیش‌قراول نیستند)
  const other = at(10, '3.2.71');
  const same = [...p10].filter((d) => other.has(d)).length;
  assert.ok(same < p10.size * 0.3, `هم‌پوشانی ${same} از ${p10.size}`);
  //  بی شناسه یا شناسهٔ ناجور ⇒ بیرون، حتی در ۱۰۰٪
  for (const d of ['', undefined, '../x', 'a b', 'x'.repeat(81)]) {
    assert.equal(stage.inStage({ version: '3.2.70', percent: 100 }, 'tohid-native', d), false, String(d));
  }
});

/* ══ ۰) و ۲) خوراکِ انتشار ═════════════════════════════════════════ */

test('⛔ بی پیکربندی: همان برچسبِ روان برای همه؛ برچسبِ نسخه‌دار ۴۰۴', async () => {
  await setRaw('stage.tohid-native', undefined);
  for (const q of ['', '?staged=1&device=dev-1', '?device=dev-1']) {
    const r = await latest(q);
    assert.equal(r.status, 200, q);
    assert.deepEqual([r.body.tag, r.body.channel, r.body.version], ['tohid-native', 'stable', '3.2.50'], q);
  }
  assert.equal((await h.download('/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk')).status, 404);
  assert.equal((await h.api('GET', '/api/releases/tohid-native-v3.2.70/latest')).status, 404);
});

test('درونِ مرحله با staged=1 ⇒ برچسبِ نسخه‌دار و فایل از همان؛ برنامهٔ قدیمی و بی‌شناسه ⇒ روان', async () => {
  await setRaw('stage.tohid-native', { version: '3.2.70', percent: 100 });
  const [d] = devices(100, true, 1);
  const r = await latest(`?staged=1&device=${d}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.tag, r.body.channel, r.body.version], ['tohid-native-v3.2.70', 'staged', '3.2.70']);
  assert.ok(r.body.assets.some((x) => x.name === 'Tohid-Native-3.2.70.apk'));
  const f = await h.download('/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk');
  assert.equal(f.status, 200);
  assert.deepEqual(Buffer.from(f.buffer), APK70);
  assert.equal((await h.download('/api/releases/tohid-native-v3.2.70/file/SHA256SUMS.txt')).status, 200);
  //  برنامهٔ قدیمی (برچسبِ نسخه‌دار را نمی‌شناسد) و بی شناسهٔ دستگاه ⇒ روان
  for (const q of [`?device=${d}`, '?staged=1', '?staged=1&shop=shopA', `?staged=true&device=${d}`]) {
    const o = await latest(q);
    assert.deepEqual([o.body.tag, o.body.version], ['tohid-native', '3.2.50'], q);
  }
  //  نسخه‌دارِ دیگر از همان مخزن هرگز
  assert.equal((await h.download('/api/releases/tohid-native-v3.2.71/file/Tohid-Native-3.2.71.apk')).status, 404);
  //  برچسبِ دسکتاپ مرحله ندارد
  assert.equal((await h.download('/api/releases/tohid-desktop-v3.2.70/file/Tohid-Native-3.2.70.apk')).status, 404);
});

test('درصد: درونِ سطل نسخهٔ تازه، بیرون روان؛ ۰ = ایست ولی دانلودِ نیمه‌کاره ادامه دارد', async () => {
  await setRaw('stage.tohid-native', { version: '3.2.70', percent: 30 });
  for (const d of devices(30, true)) {
    assert.equal((await latest(`?staged=1&device=${d}`)).body.version, '3.2.70', d);
  }
  for (const d of devices(30, false)) {
    const r = await latest(`?staged=1&device=${d}`);
    assert.deepEqual([r.body.tag, r.body.version], ['tohid-native', '3.2.50'], d);
  }
  await setRaw('stage.tohid-native', { version: '3.2.70', percent: 0 });
  for (const d of devices(30, true)) {
    assert.equal((await latest(`?staged=1&device=${d}`)).body.version, '3.2.50', `ایست: ${d}`);
  }
  assert.equal((await h.download('/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk')).status, 200);
});

test('نسخهٔ مرحله نه‌بالاتر از روان، یا انتشارِ نسخه‌دارِ خراب ⇒ روان، بی خطا', async () => {
  await setRaw('stage.tohid-native', { version: '3.2.40', percent: 100 });
  let r = await latest('?staged=1&device=dev-1');
  assert.deepEqual([r.body.tag, r.body.version], ['tohid-native', '3.2.50']);
  await setRaw('stage.tohid-native', { version: '3.2.90', percent: 100 });
  r = await latest('?staged=1&device=dev-1');
  assert.equal(r.status, 200);
  assert.deepEqual([r.body.tag, r.body.version], ['tohid-native', '3.2.50'], 'انتشارِ نسخه‌دار نیست');
  await setRaw('stage.tohid-native', { version: '3.2.72', percent: 100 });
  r = await latest('?staged=1&device=dev-1');
  assert.deepEqual([r.body.tag, r.body.version], ['tohid-native', '3.2.50'], 'نسخهٔ درونِ انتشار با برچسب نمی‌خواند');
  await setRaw('stage.tohid-native', { version: '3.2.70', percent: 100 });
  stagedUp = false;
  r = await latest('?staged=1&device=dev-1');
  assert.equal(r.status, 200);
  assert.deepEqual([r.body.tag, r.body.version], ['tohid-native', '3.2.50'], 'GitHub خطا داد');
  stagedUp = true;
});

test('پیکربندیِ ناجور (از درِ پمپ) ⇒ مرحله‌ای نیست و برچسبِ نسخه‌دار ۴۰۴', async () => {
  for (const bad of [{ version: '3.2.70', percent: 150 }, { version: '3.2.70', percent: '100' }, '3.2.70', ['3.2.70', 100]]) {
    await setRaw('stage.tohid-native', bad);
    const r = await latest('?staged=1&device=dev-1');
    assert.deepEqual([r.body.tag, r.body.version], ['tohid-native', '3.2.50'], JSON.stringify(bad));
    assert.equal((await h.download('/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk')).status, 404, JSON.stringify(bad));
  }
  await setRaw('stage.tohid-native', undefined);
});

test('عضوِ آزمایشی درونِ مرحله ⇒ بالاترینِ مرحله و آزمایشی', async () => {
  await setRaw('beta.devices', ['dev-1']);
  await setRaw('stage.tohid-native', { version: '3.2.70', percent: 100 });
  let r = await latest('?staged=1&device=dev-1');
  assert.deepEqual([r.body.tag, r.body.version], ['tohid-native-v3.2.70', '3.2.70']);
  //  بی `staged` همان قاعدهٔ پیشینِ آزمایشی
  r = await latest('?device=dev-1');
  assert.deepEqual([r.body.tag, r.body.version], ['tohid-native-beta', '3.2.60']);
  await setRaw('beta.devices', undefined);
  await setRaw('stage.tohid-native', undefined);
});

/* ══ ۴) پنل ═════════════════════════════════════════════════════════ */

const stageRows = () => require('../src/db').many(
  "SELECT action, detail FROM audit_logs WHERE action LIKE 'admin.release_stage_%' ORDER BY created_at", []
);

test('پنل: فقط مدیرِ ارشد؛ ورودیِ ناجور ۴۰۰ و بی ذخیره', async () => {
  assert.equal((await h.get('/api/admin/release/stage', { token: plain })).status, 403);
  assert.equal((await h.put('/api/admin/release/stage', { tag: 'tohid-native', version: '3.2.70', percent: 5 }, { token: plain })).status, 403);
  assert.equal((await h.api('GET', '/api/admin/release/stage', {})).status, 401);
  for (const [body, code] of [
    [{ tag: 'tohid-native-beta', version: '3.2.70', percent: 5 }, 'bad_release_tag'],
    [{ tag: 'secret', version: '3.2.70', percent: 5 }, 'bad_release_tag'],
    [{ tag: 'tohid-native', version: 'v3.2.70', percent: 5 }, 'bad_stage'],
    [{ tag: 'tohid-native', version: '3.2.70', percent: 101 }, 'bad_stage'],
    [{ tag: 'tohid-native', version: '3.2.70', percent: '5' }, 'bad_stage'],
    [{ tag: 'tohid-native', version: '3.2.70' }, 'bad_stage'],
  ]) {
    const r = await h.put('/api/admin/release/stage', body, { token: root });
    assert.equal(r.status, 400, JSON.stringify(body));
    assert.equal(r.body.error.code, code, JSON.stringify(body));
  }
  const g = await h.get('/api/admin/release/stage', { token: root });
  assert.equal(g.status, 200);
  assert.deepEqual(g.body.stages, { 'tohid-native': null, 'tohid-desktop': null });
  assert.equal((await stageRows()).length, 0);
});

test('پنل: شروع، بالا بردن و پایان — همان دم مؤثر، هر تغییر یک سابقه، بی‌تغییر بی سابقه', async () => {
  const [d] = devices(10, false, 1);
  const set = (percent) => h.put('/api/admin/release/stage',
    { tag: 'tohid-native', version: '3.2.70', percent, reason: 'آزمونِ مرحله' }, { token: root });
  let r = await set(10);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.stages['tohid-native'].percent, 10);
  assert.equal((await latest(`?staged=1&device=${d}`)).body.version, '3.2.50', 'بیرونِ ۱۰٪');
  assert.equal((await set(10)).status, 200);
  r = await set(100);
  assert.equal(r.body.stages['tohid-native'].version, '3.2.70');
  //  بی صبرِ ۶۰ ثانیهٔ کش
  assert.equal((await latest(`?staged=1&device=${d}`)).body.version, '3.2.70', '۱۰۰٪ همان دم');
  const end = await h.api('DELETE', '/api/admin/release/stage', { token: root, body: { tag: 'tohid-native', reason: 'پایان' } });
  assert.equal(end.status, 200, JSON.stringify(end.body));
  assert.equal(end.body.removed, true);
  assert.equal(end.body.stages['tohid-native'], null);
  assert.equal((await latest(`?staged=1&device=${d}`)).body.version, '3.2.50');
  assert.equal((await h.download('/api/releases/tohid-native-v3.2.70/file/Tohid-Native-3.2.70.apk')).status, 404);
  const rows = await stageRows();
  assert.deepEqual(rows.map((x) => x.action), ['admin.release_stage_set', 'admin.release_stage_set', 'admin.release_stage_end']);
  const detail = (x) => (typeof x.detail === 'string' ? JSON.parse(x.detail) : x.detail);
  assert.equal(detail(rows[1]).from, '3.2.70@10');
  assert.equal(detail(rows[1]).percent, 100);
  assert.equal(detail(rows[0]).reason, 'آزمونِ مرحله');
  //  پایانِ دوباره ⇒ بی سابقه
  assert.equal((await h.api('DELETE', '/api/admin/release/stage', { token: root, body: { tag: 'tohid-native' } })).body.removed, false);
  assert.equal((await stageRows()).length, 3);
});
