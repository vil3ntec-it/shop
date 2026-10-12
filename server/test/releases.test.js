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
