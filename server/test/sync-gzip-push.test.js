'use strict';
/**
 * SYN-04 — push ِ فشرده (gzip) برای 2G: سرور بدنهٔ `Content-Encoding: gzip` را باز می‌کند (همان `express.json`، سقفِ
 * ۲ مگابایت روی بدنهٔ **باز‌شده**)، پرچمِ خاموشِ `sync.gzip_push` فقط به دستگاه می‌گوید «بفرست»، و وب (`account-code.js`
 * + `sync-engine.js`) بدنهٔ بزرگ‌تر از ۱ کیلوبایت را با `CompressionStream` فشرده می‌کند.
 *
 *   ۱) پرچم: pull ِ دکان با op فقط با `sync.gzip_push` کلیدِ `gzip_push: true` دارد؛ خاموش ⇒ همان پاسخِ پیشین.
 *   ۲) push ِ فشرده = push ِ ساده: `applied`، همان بایت‌ها دوباره ⇒ `duplicate` (بازفرست بی دو بار نشستن)؛ عدد با همان
 *      متنِ جاوااسکریپت (`0.30000000000000004`، `1e+21`، `5e-324`) و همان اثرِ انگشت.
 *   ۳) بمبِ فشرده (۳ مگابایت پس از باز شدن) ⇒ ۴۱۳ ِ تمیز؛ gzip ِ خراب ⇒ ۴۰۰ ِ تمیز؛ کدگذاریِ ناشناخته ⇒ ۴۱۵ ِ تمیز —
 *      هیچ متنِ انگلیسیِ درونِ Node؛ سرور زنده می‌ماند.
 *   ۴) CORS: پیش‌پرواز `Content-Encoding` را می‌پذیرد (وب از دامنهٔ دیگر).
 *   ۵) وب: `call(…, {gzip:true})` ِ واقعی روی سرورِ واقعی — بدنهٔ روی خط gzip ِ همان `JSON.stringify` (بایت‌به‌بایت)،
 *      کوچک یا بی `CompressionStream` ⇒ ساده؛ موتور فقط پس از `gzip_push` می‌خواهد و پس از ۴۱۵/۴۰۰ ِ فشرده برمی‌گردد.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.CORS_ORIGIN = process.env.CORS_ORIGIN || 'https://web.gzip-push.test';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const zlib = require('node:zlib');
const h = require('./helpers');
const { query, one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const ROOT = path.join(__dirname, '..', '..');
const ACCOUNT_SRC = fs.readFileSync(path.join(ROOT, 'license', 'account-code.js'), 'utf8');
const ENGINE_SRC = fs.readFileSync(path.join(ROOT, 'license', 'sync-engine.js'), 'utf8');
const core = require(path.join(ROOT, 'license', 'sync-core.js'));

let owner;
let shopId;
let seq = 0;
const opId = () => `01JGZ${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const withHash = (o) => ({ ...o, hash: syncV1.hashOf(o) });
const product = (id, fields) => withHash({ op_id: opId(), ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { id, ...fields } });
const H = (dev) => ({ 'X-App': 'shop', 'X-Device': dev, 'Content-Type': 'application/json' });
const rawPush = (dev, bytes, encoding = 'gzip') => h.raw('POST', '/api/sync/v1/push', bytes, {
  token: owner.accessToken, headers: { ...H(dev), ...(encoding ? { 'Content-Encoding': encoding } : {}) },
});
const pushBody = (dev, ops) => JSON.stringify({ device_id: dev, schema_version: SCHEMA, ops });
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const flag = (key, on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shopId, key, on ? 'true' : 'false']
);
const rowOf = async (id) => (await one(
  "SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='products' AND row_id=$2",
  [shopId, id]
))?.data || null;
const ENGLISH = /[A-Za-z]{4,} [a-z]{3,}/;   //  «incorrect header check»، «unsupported content encoding» و مانندش

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ فشرده');
  shopId = (await h.post('/api/shop', { name: 'دکانِ فشرده' }, { token: owner.accessToken })).body.shop.id;
});
test.after(async () => { await h.stop(); });

test('۱) پرچمِ `sync.gzip_push`: فقط با پرچم `gzip_push: true` در pull ِ با op', async () => {
  const dev = 'gz-flag';
  let r = await rawPush(dev, Buffer.from(pushBody('gz-other', [product('p-flag', { name: 'چای' })])), '');
  assert.deepEqual(statuses(r), ['applied']);
  const pull = () => h.api('GET', `/api/sync/v1/pull?device_id=${dev}&since=0`, { token: owner.accessToken, headers: H(dev) });
  r = await pull();
  assert.equal(r.status, 200);
  assert.ok(r.body.ops.length > 0);
  assert.equal(r.body.gzip_push, undefined, 'پیش‌فرض خاموش ⇒ همان پاسخ');
  await flag('sync.gzip_push', true);
  r = await pull();
  assert.equal(r.body.gzip_push, true);
  await flag('sync.gzip_push', false);
  r = await pull();
  assert.equal(r.body.gzip_push, undefined);
});

test('۲) push ِ فشرده = push ِ ساده؛ بازفرستِ همان بایت‌ها «تکراری»؛ عدد با متنِ جاوااسکریپت و همان اثرِ انگشت', async () => {
  const dev = 'gz-dev';
  const nums = { a: 0.1 + 0.2, b: 1e21, c: 5e-324, d: 12345678.5, e: 0.0004 };
  const ops = [product('p-gz-1', { name: 'شکر', price: nums.a, cost: nums.b, w: nums.c }), product('p-gz-2', { name: 'برنج', price: nums.d, w: nums.e })];
  const text = pushBody(dev, ops);
  assert.ok(text.includes('0.30000000000000004') && text.includes('1e+21') && text.includes('5e-324'));
  const gz = zlib.gzipSync(Buffer.from(text, 'utf8'));
  let r = await rawPush(dev, gz);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(statuses(r), ['applied', 'applied'], 'اثرِ انگشت پس از باز شدن همان است (hash_mismatch نه)');
  //  پاسخِ گم‌شده ⇒ همان بایت‌ها دوباره
  r = await rawPush(dev, gz);
  assert.deepEqual(statuses(r), ['duplicate', 'duplicate']);
  const p1 = await rowOf('p-gz-1');
  assert.deepEqual([p1.price, p1.cost, p1.w], [nums.a, nums.b, nums.c]);
  assert.deepEqual([(await rowOf('p-gz-2')).price, (await rowOf('p-gz-2')).w], [nums.d, nums.e]);
  //  deflate هم (همان پارسر)
  r = await rawPush(dev, zlib.deflateSync(Buffer.from(pushBody(dev, [product('p-gz-3', { name: 'نمک' })]))), 'deflate');
  assert.deepEqual(statuses(r), ['applied']);
});

test('۳) بمبِ فشرده ۴۱۳؛ gzip ِ خراب ۴۰۰؛ کدگذاریِ ناشناخته ۴۱۵ — پیامِ فارسیِ تمیز، سرور زنده', async () => {
  const dev = 'gz-bad';
  const bomb = zlib.gzipSync(Buffer.from(JSON.stringify({ device_id: dev, schema_version: SCHEMA, ops: [], pad: '0'.repeat(3 * 1024 * 1024) })));
  assert.ok(bomb.length < 16 * 1024, `فشرده ${bomb.length} بایت`);
  let r = await rawPush(dev, bomb);
  assert.equal(r.status, 413, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'body_too_large');

  const broken = Buffer.from(zlib.gzipSync(Buffer.from(pushBody(dev, [product('p-bad', { name: 'x' })]))));
  broken.fill(0x41, 10, 30);
  r = await rawPush(dev, broken);
  assert.equal(r.status, 400, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'bad_json');
  assert.ok(!ENGLISH.test(r.body.error.message), r.body.error.message);

  r = await rawPush(dev, Buffer.from(pushBody(dev, [])), 'compress');
  assert.equal(r.status, 415, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'unsupported_encoding');
  assert.ok(!ENGLISH.test(r.body.error.message), r.body.error.message);

  assert.equal(await rowOf('p-bad'), null);
  r = await rawPush(dev, zlib.gzipSync(Buffer.from(pushBody(dev, [product('p-alive', { name: 'زنده' })]))));
  assert.deepEqual(statuses(r), ['applied'], 'پس از سه بدنهٔ بد سرور همان است');
});

test('۴) CORS: پیش‌پرواز `Content-Encoding` را می‌پذیرد', async () => {
  const res = await fetch(`${h.base()}/api/sync/v1/push`, {
    method: 'OPTIONS',
    headers: { Origin: process.env.CORS_ORIGIN, 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-encoding,content-type' },
  });
  assert.equal(res.status, 204);
  assert.match(res.headers.get('access-control-allow-headers') || '', /(^|, *)Content-Encoding(,|$)/);
});

/* ------------------------------------------------------------------ وب */

/** وضعیت‌های پاسخِ وب (آرایهٔ vm ⇒ آرایهٔ همین‌جا) */
const webStatuses = (r) => Array.from(r.results, (x) => x.status);

/** `account-code.js` ِ واقعی در vm با `fetch` ِ واقعی روبه‌روی همین سرور؛ هر درخواست در `sent` */
function webAccount({ compression = true } = {}) {
  const sent = [];
  const box = new Map([['tohid-license-v1', JSON.stringify({ accessToken: owner.accessToken, accessExpiresAt: Date.now() + 3600e3 })]]);
  const window = { TohidApiConfig: { baseUrl: () => h.base(), appId: () => 'shop' } };
  const sandbox = {
    window, self: { crypto: require('node:crypto').webcrypto }, navigator: { userAgent: 'test' },
    localStorage: { getItem: (k) => (box.has(k) ? box.get(k) : null), setItem: (k, v) => box.set(k, String(v)), removeItem: (k) => box.delete(k) },
    AbortController, setTimeout, clearTimeout, Blob, Response, TextEncoder, Uint8Array, console,
    async fetch(url, init) {
      const bytes = init.body === undefined ? null : Buffer.from(typeof init.body === 'string' ? init.body : new Uint8Array(init.body));
      sent.push({ url, headers: { ...init.headers }, bytes });
      return fetch(url, init);
    },
  };
  if (compression) sandbox.CompressionStream = CompressionStream;
  vm.createContext(sandbox);
  vm.runInContext(ACCOUNT_SRC, sandbox);
  return { acc: window.TohidAccount, sent };
}

test('۵-الف) وب: `call` ِ فشرده روی سرورِ واقعی — gzip ِ همان JSON.stringify، بازفرست «تکراری»؛ کوچک یا بی CompressionStream ساده', async () => {
  const dev = 'gz-web';
  const { acc, sent } = webAccount();
  const ops = [];
  for (let i = 0; i < 12; i++) ops.push(product(`p-web-${i}`, { name: `کالای وب ${i}`, price: 0.1 + 0.2 * i, note: 'ب'.repeat(60) }));
  const body = { device_id: dev, schema_version: SCHEMA, ops };
  let r = await acc.authFetch('/api/sync/v1/push', { method: 'POST', body, gzip: true });
  assert.deepEqual(webStatuses(r), Array(12).fill('applied'));
  const wire = sent[sent.length - 1];
  assert.equal(wire.headers['Content-Encoding'], 'gzip');
  assert.equal(wire.headers['Content-Type'], 'application/json');
  assert.ok(wire.bytes.length < JSON.stringify(body).length / 2, `${wire.bytes.length} بایت روی خط`);
  assert.equal(zlib.gunzipSync(wire.bytes).toString('utf8'), JSON.stringify(body), 'بایت‌به‌بایت همان متنِ جاوااسکریپت');
  r = await acc.authFetch('/api/sync/v1/push', { method: 'POST', body, gzip: true });
  assert.deepEqual(webStatuses(r), Array(12).fill('duplicate'));

  //  بدنهٔ کوچک (زیرِ ۱ کیلوبایت) ⇒ ساده
  r = await acc.authFetch('/api/sync/v1/push', { method: 'POST', body: { device_id: dev, schema_version: SCHEMA, ops: [product('p-web-small', { name: 'کوچک' })] }, gzip: true });
  assert.deepEqual(webStatuses(r), ['applied']);
  assert.equal(sent[sent.length - 1].headers['Content-Encoding'], undefined);
  //  بی gzip ِ صریح ⇒ همان پیشین
  r = await acc.authFetch('/api/sync/v1/push', { method: 'POST', body: { ...body, ops: body.ops.map((o) => ({ ...o })) } });
  assert.equal(sent[sent.length - 1].headers['Content-Encoding'], undefined);

  //  مرورگرِ بی CompressionStream ⇒ ساده و همان نتیجه
  const old = webAccount({ compression: false });
  const one2 = [product('p-web-old', { name: 'ب'.repeat(1200) })];
  r = await old.acc.authFetch('/api/sync/v1/push', { method: 'POST', body: { device_id: dev, schema_version: SCHEMA, ops: one2 }, gzip: true });
  assert.deepEqual(webStatuses(r), ['applied']);
  assert.equal(old.sent[old.sent.length - 1].headers['Content-Encoding'], undefined);
  assert.equal(old.sent[old.sent.length - 1].bytes.toString('utf8'), JSON.stringify({ device_id: dev, schema_version: SCHEMA, ops: one2 }));
});

/** موتورِ وب در vm با `authFetch` ِ ساختگی که گزینه‌های هر درخواست را نگه می‌دارد */
function webEngine({ fail = null } = {}) {
  const calls = [];
  const box = new Map();
  const ledgerBox = { data: { products: [{ id: 'p-e', name: 'چای', price: 100 }] } };
  let failNext = 0;
  const window = {
    TohidSyncCore: core,
    TohidApiConfig: { baseUrl: () => 'http://127.0.0.1' },
    TohidAccount: {
      configured: () => true, isSignedIn: () => true, account: () => ({ userId: owner.user.id }),
      deviceId: () => 'gz-engine', appId: () => 'shop', on() { }, heartbeat: async () => ({}),
      accessToken: async () => owner.accessToken,
      async authFetch(p, o = {}) {
        calls.push({ p, gzip: o.gzip === true });
        if (p === '/api/sync/v1/push' && failNext > 0 && o.gzip === true) { failNext--; throw Object.assign(new Error('x'), { status: fail }); }
        if (p.startsWith('/api/sync/v1/pull')) {
          const r = await h.api('GET', p, { token: owner.accessToken, headers: H('gz-engine') });
          return r.body;
        }
        const r = await h.api(o.method || 'GET', p, { token: owner.accessToken, body: o.body, headers: H('gz-engine') });
        if (r.status >= 400) throw Object.assign(new Error(`http ${r.status}`), { status: r.status });
        return r.body;
      },
    },
    addEventListener() { }, dispatchEvent() { },
  };
  const ctx = vm.createContext({
    window, localStorage: { getItem: (k) => (box.has(k) ? box.get(k) : null), setItem: (k, v) => box.set(k, String(v)), removeItem: (k) => box.delete(k) },
    navigator: { onLine: true }, document: { addEventListener() { } },
    URL, console, setTimeout: () => 0, clearTimeout() { }, setInterval: () => 0, clearInterval() { },
  });
  vm.runInContext(ENGINE_SRC, ctx);
  const sync = window.TohidSync;
  sync.bind({ read: () => JSON.parse(JSON.stringify(ledgerBox.data)), write: (d) => { ledgerBox.data = JSON.parse(JSON.stringify(d)); } });
  return {
    sync, calls, arm(n) { failNext = n; },
    save(mutate) { const d = JSON.parse(JSON.stringify(ledgerBox.data)); mutate(d); ledgerBox.data = d; return sync.recordChange('save'); },
  };
}
const pushes = (calls) => calls.filter((c) => c.p === '/api/sync/v1/push');

test('۵-ب) موتورِ وب: فشرده فقط پس از `gzip_push` ِ سرور؛ پس از ۴۱۵ ِ بدنهٔ فشرده بارِ بعد ساده و هیچ op گم نمی‌شود', async () => {
  const web = webEngine({ fail: 415 });
  assert.equal((await web.sync.firstSync()).did, 'baseline');
  web.save((d) => { d.products[0].price = 110; });
  await web.sync.sync();
  assert.ok(pushes(web.calls).length >= 1);
  assert.ok(pushes(web.calls).every((c) => c.gzip === false), 'پرچمِ خاموش ⇒ هرگز');

  await flag('sync.gzip_push', true);
  //  دستگاهِ دیگر یک op می‌گذارد تا pull ِ بعدی op داشته باشد (پاسخِ بی op افزوده ندارد)
  await rawPush('gz-other-2', Buffer.from(pushBody('gz-other-2', [product('p-other-e', { name: 'دیگری' })])), '');
  await web.sync.sync();
  web.calls.length = 0;
  web.save((d) => { d.products[0].price = 120; });
  await web.sync.sync();
  assert.deepEqual(pushes(web.calls).map((c) => c.gzip), [true]);

  //  میان‌راهی بدنهٔ فشرده را نمی‌پذیرد (۴۱۵) ⇒ خطا، op در صف؛ دورِ بعد ساده و رسید
  web.calls.length = 0;
  web.arm(1);
  web.save((d) => { d.products[0].price = 130; });
  await web.sync.sync().catch(() => null);
  assert.equal(web.sync.queueSize() > 0, true, 'op در صف ماند');
  await web.sync.sync();
  assert.deepEqual(pushes(web.calls).map((c) => c.gzip), [true, false]);
  assert.equal(web.sync.queueSize(), 0);
  assert.equal((await rowOf('p-e')).price, 130);
  await flag('sync.gzip_push', false);
});
