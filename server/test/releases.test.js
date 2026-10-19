'use strict';
/**
 * توزیعِ نسخه از سرور — برنامه دیگر به github.com وصل نمی‌شود.
 *
 *   • نسخه و فهرستِ فایل‌ها از انتشارِ مخزن، با توکن اگر داده شده باشد
 *   • فایل یک بار از GitHub گرفته و روی دیسک نگه داشته می‌شود
 *   • برچسب و نامِ ناشناخته ۴۰۴ — نامِ فایل هرگز مسیر نمی‌شود
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

let fake, h, hits = { release: 0, asset: 0, auth: '' };
let betaDown = false, betaVersion = '3.2.60';
const APK = Buffer.from('fake-apk-bytes-0123456789');

test.before(async () => {
  fake = http.createServer((req, res) => {
    if (req.url === '/repos/o/r/releases/tags/tohid-native') {
      hits.release++; hits.auth = req.headers.authorization || '';
      res.setHeader('Content-Type', 'application/json');
      return res.end(JSON.stringify({
        body: 'یادداشتِ نسخه', published_at: '2026-10-12T00:00:00Z',
        assets: [
          { id: 1, name: 'Tohid-Native-3.2.50.apk', size: APK.length, updated_at: '2026-10-12T00:00:00Z' },
          { id: 2, name: 'version.txt', size: 7, updated_at: '2026-10-12T00:00:00Z' },
        ],
      }));
    }
    if (req.url === '/repos/o/r/releases/tags/tohid-native-beta') {
      if (betaDown) { res.statusCode = 500; return res.end('{}'); }
      res.setHeader('Content-Type', 'application/json');
      return res.end(JSON.stringify({
        body: 'آزمایشی', published_at: '2026-10-13T00:00:00Z',
        assets: [
          //  ⛔ نامِ نصبیِ آزمایشی به `.apk` ختم نمی‌شود (برنامه‌های قدیمی در راهِ GitHub هر `.apk`ی را برمی‌دارند)
          { id: 11, name: 'Tohid-Native-3.2.60.apk.beta', size: APK.length, updated_at: '2026-10-13T00:00:00Z' },
          //  زمانِ به‌روزشدن با نسخه عوض می‌شود — درست مثلِ GitHub، تا کشِ دیسکِ سرور تازه شود
          { id: 12, name: 'version.txt', size: 7, updated_at: `2026-10-13T00:00:00Z-${betaVersion}` },
        ],
      }));
    }
    if (req.url === '/repos/o/r/releases/assets/12') return res.end(betaVersion + '\n');
    if (req.url === '/repos/o/r/releases/assets/11') return res.end(APK);
    if (req.url === '/repos/o/r/releases/assets/1') { hits.asset++; return res.end(APK); }
    if (req.url === '/repos/o/r/releases/assets/2') return res.end('3.2.50\n');
    res.statusCode = 404; res.end('{}');
  });
  await new Promise(r => fake.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${fake.address().port}`;
  process.env.RELEASE_REPO = 'o/r';
  process.env.GITHUB_TOKEN = 'secret-token';
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-'));
  h = require('./helpers');
  await h.start();
});
test.after(async () => { await h.stop(); fake.close(); });

test('آخرین نسخه از سرور، با توکنِ سرور به GitHub', async () => {
  const r = await h.api('GET', '/api/releases/tohid-native/latest');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.version, '3.2.50');
  assert.equal(r.body.notes, 'یادداشتِ نسخه');
  assert.deepEqual(r.body.assets.map(a => a.name), ['Tohid-Native-3.2.50.apk', 'version.txt']);
  assert.equal(hits.auth, 'Bearer secret-token');
  //  توکن هرگز در پاسخ نیست
  assert.ok(!JSON.stringify(r.body).includes('secret-token'));
});

test('فایل یک بار از GitHub، بعد از دیسکِ سرور', async () => {
  const a = await h.download('/api/releases/tohid-native/file/Tohid-Native-3.2.50.apk');
  const b = await h.download('/api/releases/tohid-native/file/Tohid-Native-3.2.50.apk');
  assert.equal(a.status, 200);
  assert.deepEqual(Buffer.from(a.buffer), APK);
  assert.deepEqual(Buffer.from(b.buffer), APK);
  assert.equal(hits.asset, 1);
});

test('برچسب و نامِ ناشناخته ۴۰۴', async () => {
  assert.equal((await h.api('GET', '/api/releases/secret-tag/latest')).status, 404);
  assert.equal((await h.download('/api/releases/tohid-native/file/..%2F..%2Fetc%2Fpasswd')).status, 404);
  assert.equal((await h.download('/api/releases/tohid-native/file/nope.apk')).status, 404);
});

//  F0-27 — کانالِ آزمایشی
const releases = () => require('../src/routes/releases');
async function beta(key, value) {
  await require('../src/lib/live-config').set('release', key, value, 'test');
  releases()._forgetBeta();
  releases()._cache.clear();
}

test('بی شناسه، یا شناسهٔ غیرعضو ⇒ همیشه پایدار', async () => {
  await beta('beta.shops', ['shopA']);
  for (const q of ['', '?shop=shopZ', '?device=dev9', '?shop=../x']) {
    const r = await h.api('GET', `/api/releases/tohid-native/latest${q}`);
    assert.equal(r.status, 200, q);
    assert.equal(r.body.tag, 'tohid-native', q);
    assert.equal(r.body.channel, 'stable', q);
    assert.equal(r.body.version, '3.2.50', q);
  }
});

test('دکان یا دستگاهِ آزمایشی ⇒ نسخهٔ بالاتر از برچسبِ beta، و فایل از همان برچسب', async () => {
  await beta('beta.shops', 'shopA, shopB');
  await beta('beta.devices', ['dev1']);
  for (const q of ['?shop=shopB', '?device=dev1', '?shop=shopZ&device=dev1']) {
    const r = await h.api('GET', `/api/releases/tohid-native/latest${q}`);
    assert.equal(r.body.tag, 'tohid-native-beta', q);
    assert.equal(r.body.channel, 'beta', q);
    assert.equal(r.body.version, '3.2.60', q);
    assert.ok(r.body.assets.some((a) => a.name === 'Tohid-Native-3.2.60.apk.beta'));
  }
  const f = await h.download('/api/releases/tohid-native-beta/file/Tohid-Native-3.2.60.apk.beta');
  assert.equal(f.status, 200);
  assert.deepEqual(Buffer.from(f.buffer), APK);
});

test('پایدارِ تازه‌تر از آزمایشی ⇒ عضوِ آزمایشی هم پایدار می‌گیرد؛ انتشارِ آزمایشیِ خراب ⇒ پایدار، بی خطا', async () => {
  await beta('beta.shops', ['shopA']);
  betaVersion = '3.2.40';
  releases()._cache.clear();
  let r = await h.api('GET', '/api/releases/tohid-native/latest?shop=shopA');
  assert.equal(r.body.tag, 'tohid-native');
  assert.equal(r.body.version, '3.2.50');
  betaVersion = '3.2.60';
  betaDown = true;
  releases()._cache.clear();
  r = await h.api('GET', '/api/releases/tohid-native/latest?shop=shopA');
  assert.equal(r.status, 200);
  assert.equal(r.body.tag, 'tohid-native');
  betaDown = false;
});

test('پاسخِ وابسته به شناسه در کشِ مشترک نمی‌نشیند', async () => {
  const r1 = await fetch(`${h.base()}/api/releases/tohid-native/latest?shop=shopA`);
  const r2 = await fetch(`${h.base()}/api/releases/tohid-native/latest`);
  assert.match(r1.headers.get('cache-control'), /private/);
  assert.match(r2.headers.get('cache-control'), /public/);
});

test('§۳۸.۹: حداقل نسخهٔ دکان در خوراکِ انتشار؛ نبود یا نادرست ⇒ خالی', async () => {
  const plans = require('../src/lib/plans');
  let r = await h.api('GET', '/api/releases/tohid-native/latest');
  assert.equal(r.body.minVersion, '', 'پیش‌فرض: حداقلی نیست');
  await plans.setConfig('min_app_version_shop', '3.2.45');
  r = await h.api('GET', '/api/releases/tohid-native/latest');
  assert.equal(r.status, 200);
  assert.equal(r.body.minVersion, '3.2.45');
  await plans.setConfig('min_app_version_pump', '9.0.0');
  r = await h.api('GET', '/api/releases/tohid-native/latest');
  assert.equal(r.body.minVersion, '3.2.45', 'حداقلِ پمپ به برنامهٔ دکان نمی‌رسد');
  await plans.setConfig('min_app_version_shop', 'نادرست');
  r = await h.api('GET', '/api/releases/tohid-native/latest');
  assert.equal(r.body.minVersion, '');
  await plans.setConfig('min_app_version_shop', '');
});
