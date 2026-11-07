'use strict';
/**
 * انبارِ عکس — آزمونِ واحدِ دو انبار و لایهٔ `lib/image-store.js` (بی پایگاه).
 *
 *   ۱) امضای SigV4 همان دو بردارِ مستندِ AWS (GET ِ شیء، فهرست با پرسش).
 *   ۲) دیسک: نوشتن/خواندن/پاک/فهرست، نوشتنِ اتمی (پروندهٔ گذرا نمی‌ماند)، کلیدِ بدشکل/پیمایشِ مسیر رد.
 *   ۳) S3 روی یک S3 ِ ساختگیِ محلی که امضای هر درخواست را می‌سنجد: path-style و virtual-host، پیشوند، توکنِ نشست،
 *      فهرستِ چندبرگی، ۴۰۴ ⇒ null، ۵۰۰ ⇒ خطا، کلید و راز فقط از env.
 *   ۴) لایه: کلیدِ حدس‌ناپذیر با بخشِ tenant، شیءِ دکانِ دیگر هرگز (حتی با کلیدِ درست)، بایتِ دست‌خورده یا غیرِ عکس
 *      «نیست»، ترتیبِ خواندن در هر حالت، و `withStaged` که شیءِ نانوشته یا خطادار را پاک می‌کند.
 *   ۵) سنجشِ پیکربندی فقط نامِ env را می‌گوید، هرگز مقدارِ راز را.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const A = require('../src/lib/image-store-adapters');
const config = require('../src/config');
const store = require('../src/lib/image-store');
const F = require('./image-fixtures');

const sha256 = (b) => crypto.createHash('sha256').update(b).digest('hex');
const EMPTY = sha256('');
const AWS = { accessKeyId: 'AKIAIOSFODNN7EXAMPLE', secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY' };
const tmpDirs = [];
const tmp = () => { const d = fs.mkdtempSync(path.join(os.tmpdir(), 'img-store-')); tmpDirs.push(d); return d; };
test.after(() => { for (const d of tmpDirs) fs.rmSync(d, { recursive: true, force: true }); });

const key = (tenant = 'shop:a', buf = F.jpeg(8, 8)) => store.keyFor(tenant, buf);

test('SigV4: همان دو بردارِ مستندِ AWS', () => {
  const get = A.sign({
    method: 'GET', path: '/test.txt', region: 'us-east-1', credentials: AWS,
    headers: { host: 'examplebucket.s3.amazonaws.com', range: 'bytes=0-9', 'x-amz-content-sha256': EMPTY, 'x-amz-date': '20130524T000000Z' },
  });
  assert.equal(sha256(get.canonical), '7344ae5b7ee6c3e7e6b0fe0640412a37625d1fbfff95c48bbb2dc43964946972');
  assert.equal(get.signature, 'f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41');
  assert.match(get.authorization, /^AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE\/20130524\/us-east-1\/s3\/aws4_request, SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, Signature=f0e8/);
  const list = A.sign({
    method: 'GET', path: '/', query: { 'max-keys': 2, prefix: 'J' }, region: 'us-east-1', credentials: AWS,
    headers: { host: 'examplebucket.s3.amazonaws.com', 'x-amz-content-sha256': EMPTY, 'x-amz-date': '20130524T000000Z' },
  });
  assert.equal(list.signature, '34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7');
});

test('دیسک: نوشتن، خواندن، پاک، فهرست؛ اتمی؛ کلیدِ بدشکل و پیمایشِ مسیر رد', async () => {
  const dir = tmp();
  const d = A.disk({ dir });
  const buf = F.png(10, 10);
  const k = key('shop:a', buf);
  assert.equal(await d.get(k), null, 'نبود ⇒ null');
  await d.put(k, buf, 'image/png');
  assert.deepEqual(await d.get(k), buf);
  const file = path.join(dir, ...k.split('/'));
  assert.equal((fs.statSync(file).mode & 0o777).toString(8), '600', 'فقط خودِ سرور می‌خواند');
  assert.deepEqual(fs.readdirSync(path.dirname(file)), [path.basename(file)], 'پروندهٔ گذرا نماند');
  await d.put(k, buf, 'image/png');
  const listed = await d.list();
  assert.deepEqual(listed.map((o) => o.key), [k]);
  assert.ok(listed[0].mtime > 0);
  await d.del(k);
  await d.del(k);
  assert.equal(await d.get(k), null);
  assert.deepEqual(await A.disk({ dir: path.join(dir, 'nope') }).list(), []);
  for (const bad of ['../etc/passwd', `i1/${'a'.repeat(16)}/../../x`, '/abs', 'i1/x/y', `${k}/..`, '']) {
    await assert.rejects(d.put(bad, buf), /bad key/, bad);
    await assert.rejects(d.get(bad), /bad key/, bad);
  }
});

/** S3 ِ ساختگی: امضا، hash ِ بدنه و میزبان را می‌سنجد؛ فهرست دوتایی برگ می‌خورد. */
function fakeS3({ bucket = 'imgs', creds = AWS, region = 'eu-test-1', token = '' } = {}) {
  const objects = new Map();
  const st = { requests: [], fail: 0 };
  const server = http.createServer((req, res) => {
    const parts = [];
    req.on('data', (c) => parts.push(c));
    req.on('end', () => {
      const body = Buffer.concat(parts);
      const url = new URL(req.url, 'http://local');
      st.requests.push({ method: req.method, path: url.pathname, host: req.headers.host, headers: req.headers });
      const send = (code, b = '', type = 'application/xml') => { res.writeHead(code, { 'content-type': type }); res.end(b); };
      if (st.fail > 0) { st.fail--; return send(500, '<Error><Code>InternalError</Code></Error>'); }
      const m = /^AWS4-HMAC-SHA256 Credential=([^/]+)\/(\d{8})\/([^/]+)\/s3\/aws4_request, SignedHeaders=([a-z0-9;-]+), Signature=([0-9a-f]{64})$/
        .exec(req.headers.authorization || '');
      if (!m || m[1] !== creds.accessKeyId || m[3] !== region) return send(403, '<Error><Code>InvalidAccessKeyId</Code></Error>');
      if (sha256(body) !== req.headers['x-amz-content-sha256']) return send(400, '<Error><Code>XAmzContentSHA256Mismatch</Code></Error>');
      if (token && req.headers['x-amz-security-token'] !== token) return send(403, '<Error><Code>InvalidToken</Code></Error>');
      const names = m[4].split(';');
      for (const n of ['host', 'x-amz-date', 'x-amz-content-sha256', ...(token ? ['x-amz-security-token'] : [])]) {
        if (!names.includes(n)) return send(403, `<Error><Code>Unsigned ${n}</Code></Error>`);
      }
      const headers = Object.fromEntries(names.map((n) => [n, req.headers[n]]));
      const query = Object.fromEntries(url.searchParams);
      const want = A.sign({ method: req.method, path: url.pathname, query, headers, region, credentials: creds }).signature;
      if (want !== m[5]) return send(403, '<Error><Code>SignatureDoesNotMatch</Code></Error>');
      const virtual = req.headers.host.startsWith(`${bucket}.`);
      const objectPath = virtual ? url.pathname.slice(1) : url.pathname.slice(bucket.length + 2);
      if (!virtual && !url.pathname.startsWith(`/${bucket}`)) return send(404, '<Error><Code>NoSuchBucket</Code></Error>');
      if (req.method === 'GET' && query['list-type'] === '2') {
        const all = [...objects.keys()].filter((k) => k.startsWith(query.prefix || '')).sort();
        const from = query['continuation-token'] ? all.indexOf(query['continuation-token']) : 0;
        const page = all.slice(from, from + 2);
        const next = all[from + 2];
        return send(200, `<?xml version="1.0"?><ListBucketResult><IsTruncated>${next ? 'true' : 'false'}</IsTruncated>${
          page.map((k) => `<Contents><Key>${k}</Key><LastModified>${new Date(objects.get(k).at).toISOString()}</LastModified></Contents>`).join('')
        }${next ? `<NextContinuationToken>${next}</NextContinuationToken>` : ''}</ListBucketResult>`);
      }
      if (req.method === 'PUT') { objects.set(objectPath, { buf: body, at: Date.now(), type: req.headers['content-type'] }); return send(200); }
      if (req.method === 'GET') {
        const o = objects.get(objectPath);
        return o ? send(200, o.buf, o.type) : send(404, '<Error><Code>NoSuchKey</Code></Error>');
      }
      if (req.method === 'DELETE') { objects.delete(objectPath); return send(204); }
      return send(405);
    });
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    objects, st, endpoint: `http://127.0.0.1:${server.address().port}`, close: () => new Promise((r) => server.close(r)),
  })));
}

const s3cfg = (f, over = {}) => ({ s3: { endpoint: f.endpoint, bucket: 'imgs', region: 'eu-test-1', prefix: '', pathStyle: true, ...over }, timeoutMs: 5000 });
const ENV = { IMAGE_STORE_S3_ACCESS_KEY_ID: AWS.accessKeyId, IMAGE_STORE_S3_SECRET_ACCESS_KEY: AWS.secretAccessKey };

test('S3 (path-style): امضای سنجیده، نوشتن/خواندن/پاک، فهرستِ چندبرگی، ۴۰۴ ⇒ null، ۵۰۰ ⇒ خطا', async (t) => {
  const f = await fakeS3();
  t.after(() => f.close());
  const s = A.s3(s3cfg(f, { prefix: 'shop-a/' }), { env: ENV });
  const bufs = [F.jpeg(8, 8, { seed: 1 }), F.png(9, 9), F.webp(7, 7), F.jpeg(6, 6, { seed: 2 }), F.jpeg(5, 5, { seed: 3 })];
  const keys = bufs.map((b) => key('shop:a', b));
  assert.equal(await s.get(keys[0]), null);
  for (const [i, b] of bufs.entries()) await s.put(keys[i], b, store.sniff(b));
  assert.deepEqual(await s.get(keys[1]), bufs[1]);
  assert.equal(f.objects.get(`shop-a/${keys[1]}`).type, 'image/png', 'نوعِ محتوا از سنجش، پیشوند در نامِ شیء');
  const put = f.st.requests.find((r) => r.method === 'PUT');
  assert.equal(put.path, `/imgs/shop-a/${keys[0]}`);
  assert.equal(put.headers['x-amz-content-sha256'], sha256(bufs[0]), 'بدنه امضا شده، نه UNSIGNED-PAYLOAD');
  assert.deepEqual((await s.list()).map((o) => o.key).sort(), [...keys].sort(), 'سه برگ (هر برگ ۲)');
  assert.ok(f.st.requests.filter((r) => r.headers.authorization && r.method === 'GET' && r.path === '/imgs').length >= 3);
  await s.del(keys[0]);
  await s.del(keys[0]);
  assert.equal(await s.get(keys[0]), null);
  f.st.fail = 1;
  await assert.rejects(s.get(keys[1]), /s3 get 500/);
  f.st.fail = 1;
  await assert.rejects(s.put(keys[1], bufs[1]), /s3 put 500/);
  await assert.rejects(s.get('../x'), /bad key/);
});

test('S3: virtual-host، توکنِ نشست، کلیدِ نادرست ⇒ رد؛ کلید و راز فقط از env', async (t) => {
  const f = await fakeS3({ token: 'sess-tok' });
  t.after(() => f.close());
  const env = { AWS_ACCESS_KEY_ID: AWS.accessKeyId, AWS_SECRET_ACCESS_KEY: AWS.secretAccessKey, AWS_SESSION_TOKEN: 'sess-tok' };
  const s = A.s3(s3cfg(f, { pathStyle: false }), { env });
  const buf = F.jpeg(8, 8, { seed: 7 });
  const k = key('shop:b', buf);
  await s.put(k, buf, 'image/jpeg');
  assert.deepEqual(await s.get(k), buf);
  const r = f.st.requests.at(-1);
  assert.equal(r.host, `imgs.127.0.0.1:${new URL(f.endpoint).port}`);
  assert.equal(r.path, `/${k}`);
  assert.equal(r.headers['x-amz-security-token'], 'sess-tok');
  const wrong = A.s3(s3cfg(f, { pathStyle: false }), { env: { ...env, AWS_SECRET_ACCESS_KEY: 'wrong-secret' } });
  await assert.rejects(wrong.get(k), /s3 get 403/, 'امضای نادرست');
  assert.deepEqual(A.envCredentials({ IMAGE_STORE_S3_ACCESS_KEY_ID: 'a', AWS_ACCESS_KEY_ID: 'b', AWS_SECRET_ACCESS_KEY: 's' }),
    { accessKeyId: 'a', secretAccessKey: 's', sessionToken: '' }, 'نامِ ویژه بر نامِ همگانی جلو');
  for (const v of Object.values(s3cfg(f).s3)) assert.ok(!String(v).includes(AWS.secretAccessKey), 'راز در پیکربندی نیست');
});

test('پیکربندی: حالتِ نادرست، پوشه یا سطل یا کلیدِ نبوده — فقط نامِ env در پیام، هرگز راز', () => {
  const base = { mode: 'db', read: '', dir: '', s3: { endpoint: '', bucket: '', region: 'us-east-1', prefix: '', pathStyle: true } };
  assert.deepEqual(A.problems(base, {}), []);
  assert.match(A.problems({ ...base, mode: 'S3x' }, {}).join(), /IMAGE_STORE فقط/);
  assert.match(A.problems({ ...base, mode: 'disk' }, {}).join(), /IMAGE_STORE_DIR/);
  assert.match(A.problems({ ...base, mode: 'disk', read: 'disk', dir: '/x' }, {}).join(), /IMAGE_STORE_READ فقط با IMAGE_STORE=db/);
  const s3 = A.problems({ ...base, mode: 's3' }, {}).join(' | ');
  assert.match(s3, /IMAGE_STORE_S3_ENDPOINT/);
  assert.match(s3, /IMAGE_STORE_S3_BUCKET/);
  assert.match(s3, /IMAGE_STORE_S3_ACCESS_KEY_ID/);
  const ok = { ...base, mode: 's3', s3: { ...base.s3, endpoint: 'https://s3.example', bucket: 'b' } };
  assert.deepEqual(A.problems(ok, { AWS_ACCESS_KEY_ID: 'AK', AWS_SECRET_ACCESS_KEY: 'top-secret-value' }), []);
  assert.ok(!A.problems({ ...ok, mode: 'x' }, { AWS_SECRET_ACCESS_KEY: 'top-secret-value' }).join().includes('top-secret-value'));
  assert.deepEqual(A.problems({ ...base, read: 'disk', dir: '/x' }, {}), [], 'پنجرهٔ برگشت: db + خواندن از دیسک');
});

/** انبارِ ساختگی در حافظه با همان قرارداد — برای آزمونِ لایه */
function memory() {
  const m = new Map();
  return {
    kind: 'memory', m,
    async put(k, b) { A.assertKey(k); m.set(k, Buffer.from(b)); },
    async get(k) { A.assertKey(k); return m.has(k) ? Buffer.from(m.get(k)) : null; },
    async del(k) { m.delete(k); },
    async list() { return [...m.keys()].map((k) => ({ key: k, mtime: 0 })); },
  };
}

function withMode(t, mode, adapter = memory(), read = '') {
  const prev = { ...config.imageStore };
  config.imageStore.mode = mode;
  config.imageStore.read = read;
  store.useBackend(adapter);
  t.after(() => { Object.assign(config.imageStore, prev); store.useBackend(null); });
  return adapter;
}

test('کلید: حدس‌ناپذیر، بخشِ tenant، sha256 ِ بایت؛ ابزار با seed همان کلید را دوباره می‌سازد', () => {
  const buf = F.jpeg(8, 8);
  const a = store.keyFor('shop:a', buf);
  const b = store.keyFor('shop:a', buf);
  assert.match(a, A.KEY_RE);
  assert.notEqual(a, b, '۱۲۸ بیتِ تصادفی');
  assert.equal(a.split('/')[1], b.split('/')[1]);
  assert.equal(a.slice(-16), sha256(buf).slice(0, 16));
  assert.ok(store.owns('shop:a', a));
  assert.ok(!store.owns('shop:b', a), 'کلیدِ دکانِ دیگر مالِ این دکان نیست');
  assert.ok(!store.owns('shop:a', '../x'));
  const seed = { secret: Buffer.from('s'), id: 'product_photos:["shop","a","rice"]:md5' };
  assert.equal(store.keyFor('shop:a', buf, seed), store.keyFor('shop:a', buf, seed));
  assert.notEqual(store.keyFor('shop:a', buf, seed), store.keyFor('shop:a', buf, { ...seed, secret: Buffer.from('t') }));
  assert.equal(store.accountTenant('shop', 'x'), store.shopTenant('x'));
  assert.equal(store.has('ph.'), "(ph.data IS NOT NULL OR ph.store_key <> '')");
});

test('خواندن: مالکیت، درستی و نوعِ شیء؛ ترتیبِ پایگاه/انبار در هر حالت', async (t) => {
  const mem = withMode(t, 'disk');
  const buf = F.jpeg(16, 16, { seed: 3 });
  const k = store.keyFor('shop:a', buf);
  await mem.put(k, buf);
  assert.deepEqual(await store.bytes('shop:a', { data: null, store_key: k }), buf);
  assert.equal(await store.bytes('shop:b', { data: null, store_key: k }), null, '⛔ کلیدِ دکانِ دیگر حتی اگر درست باشد');
  //  شیءِ دست‌خورده (همان کلید، بایتِ دیگر) و شیءِ غیرِ عکس با کلیدِ درست
  const other = F.jpeg(16, 16, { seed: 4 });
  mem.m.set(k, other);
  assert.equal(await store.bytes('shop:a', { data: null, store_key: k }), null, 'sha256 ِ کلید نمی‌خواند');
  const html = Buffer.from('<html><script>alert(1)</script></html>');
  const hk = store.keyFor('shop:a', html);
  mem.m.set(hk, html);
  assert.equal(await store.bytes('shop:a', { data: null, store_key: hk }), null, '⛔ فقط JPEG/PNG/WebP');
  //  حالتِ شیء: اول شیء؛ نبود ⇒ همان رونوشتِ پایگاه
  mem.m.set(k, buf);
  const dbCopy = F.jpeg(16, 16, { seed: 9 });
  assert.deepEqual(await store.bytes('shop:a', { data: dbCopy, store_key: k }), buf, 'حالتِ شیء ⇒ شیء');
  mem.m.delete(k);
  assert.deepEqual(await store.bytes('shop:a', { data: dbCopy, store_key: k }), dbCopy, 'شیء نیست ⇒ پایگاه');
  assert.equal(await store.bytes('shop:a', { data: null, store_key: '' }), null);
  assert.equal(await store.bytes('shop:a', null), null);
  //  حالتِ db: اول پایگاه؛ فقط-انبار با IMAGE_STORE_READ
  config.imageStore.mode = 'db';
  mem.m.set(k, buf);
  assert.deepEqual(await store.bytes('shop:a', { data: dbCopy, store_key: k }), dbCopy, 'حالتِ db ⇒ پایگاه');
  assert.deepEqual(await store.bytes('shop:a', { data: null, store_key: k }), buf, 'پنجرهٔ برگشت');
  assert.deepEqual(await store.bytes('shop:a', { data: new Uint8Array(dbCopy), store_key: '' }), dbCopy, 'Uint8Array ِ PGlite');
});

test('نوشتن: db ⇒ بی شیء؛ شیء ⇒ پیش از ردیف، و پاک اگر ردیف نوشته نشد یا خطا', async (t) => {
  const mem = withMode(t, 'db');
  const buf = F.png(12, 12);
  const s0 = await store.withStaged('shop:a', buf, async (s) => ({ ...s }));
  assert.deepEqual(s0, { data: buf, key: '', used: false });
  assert.equal(mem.m.size, 0, 'حالتِ db هیچ شیئی نمی‌سازد');

  config.imageStore.mode = 's3';
  const kept = await store.withStaged('shop:a', buf, async (s) => {
    assert.equal(s.data, null);
    assert.ok(store.owns('shop:a', s.key));
    assert.deepEqual(mem.m.get(s.key), buf, 'شیء پیش از نوشتنِ ردیف هست');
    s.used = true;
    return s.key;
  });
  assert.ok(mem.m.has(kept));
  const unused = await store.withStaged('shop:a', buf, async (s) => s.key);
  assert.ok(!mem.m.has(unused), 'تکرار ⇒ شیءِ آماده پاک');
  let failed = '';
  await assert.rejects(store.withStaged('shop:a', buf, async (s) => { failed = s.key; s.used = true; throw new Error('boom'); }), /boom/);
  assert.ok(failed && !mem.m.has(failed), 'خطای تراکنش ⇒ شیءِ آماده پاک');
  await assert.rejects(store.withStaged('shop:a', Buffer.from('not an image'), async () => {}), /only JPEG/);
  await store.drop('shop:b', [kept]);
  assert.ok(mem.m.has(kept), '⛔ پاکِ دکانِ دیگر به شیءِ این دکان نمی‌رسد');
  await store.drop('shop:a', [kept, '', null]);
  assert.ok(!mem.m.has(kept));
  assert.equal(store.status().mode, 's3');
});
