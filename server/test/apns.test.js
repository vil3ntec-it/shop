'use strict';
/**
 * APNs — فرستندهٔ پوشِ برنامهٔ آیفون (`lib/apns.js`؛ docs/IOS-PLAN-fa.md §۰.۱۰).
 *
 * اپلِ واقعی از ماشینِ آزمون در دسترس نیست و نباید باشد: یک سرورِ HTTP/2 ِ ساختگی روی 127.0.0.1
 * (بی TLS، بی شبکه) جای `api.push.apple.com` می‌نشیند و هر چه می‌رسد — مسیر، سرآیند، JWT، بدنه —
 * سنجیده می‌شود. کلیدِ p8 همین‌جا ساخته می‌شود (EC ِ P-256، همان نوعِ کلیدِ APNs).
 * آزمونِ سرتاسری با دیتابیس (کهنه شدنِ ردیف، دستِ نخوردنِ FCM) در `push-delivery.test.js` است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http2 = require('node:http2');
const util = require('node:util');
const { generateKeyPairSync, verify } = require('node:crypto');
const apns = require('../src/lib/apns');

const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const P8 = privateKey.export({ type: 'pkcs8', format: 'pem' });
const P8_BODY = P8.split('\n').filter((l) => l && !l.startsWith('-----')).join('');
const ENV = Object.freeze({
  APNS_KEY_ID: 'ABC123DEFG',
  APNS_TEAM_ID: 'TEAM123456',
  APNS_KEY_P8: P8,
  APNS_BUNDLE_ID: 'ir.vil3ntec.tohid',
  APNS_ENV: 'sandbox',
});
const TOKEN = 'a1'.repeat(32);
const T0 = Date.UTC(2026, 10, 2, 10, 0, 0);

/** «اپلِ» ساختگی: `script(req, n)` ⇒ {status, reason?, headers?} | 'drop' (بریدنِ نشست) */
function mockApple(script) {
  const seen = [];
  const sessions = new Set();
  const server = http2.createServer();
  server.on('session', (s) => { sessions.add(s); s.on('close', () => sessions.delete(s)); });
  server.on('stream', (stream, headers) => {
    const chunks = [];
    stream.on('data', (c) => chunks.push(c));
    stream.on('end', () => {
      const req = { headers, body: Buffer.concat(chunks).toString('utf8'), session: stream.session };
      seen.push(req);
      const r = script(req, seen.length);
      if (r === 'drop') { stream.session.destroy(); return; }
      stream.respond({ ':status': r.status, 'apns-id': `id-${seen.length}`, ...(r.headers || {}) });
      stream.end(r.reason ? JSON.stringify({ reason: r.reason }) : '');
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve({
      seen,
      origin: `http://127.0.0.1:${server.address().port}`,
      close: () => new Promise((r) => { for (const s of sessions) s.destroy(); server.close(r); }),
    }));
  });
}

function senderFor(origin, extra = {}) {
  const waits = [];
  let clock = T0;
  const s = apns.createSender(apns.readConfig(ENV).cfg, {
    origin,
    sleep: async (ms) => { waits.push(ms); },
    now: () => clock,
    ...extra,
  });
  return { s, waits, tick: (ms) => { clock += ms; } };
}

function decodeJwt(jwt) {
  const [h, c, sig] = jwt.split('.');
  const json = (x) => JSON.parse(Buffer.from(x, 'base64url').toString('utf8'));
  return { head: json(h), claims: json(c), signed: Buffer.from(`${h}.${c}`), sig: Buffer.from(sig, 'base64url') };
}

/* ---------------------------- لاگ‌گیر ---------------------------- */
const METHODS = ['log', 'info', 'warn', 'error', 'debug'];
const lines = [];
const real = {};
test.before(() => {
  for (const m of METHODS) { real[m] = console[m]; console[m] = (...a) => { lines.push(util.format(...a)); }; }
});
test.after(() => { for (const m of METHODS) console[m] = real[m]; });

/* ------------------------------ تنظیم ----------------------------- */

test('تنظیم: بی هیچ متغیری خاموشِ بی‌صدا؛ ناقص یا خراب فقط نامِ متغیر/دلیل را می‌گوید', () => {
  assert.deepEqual(apns.readConfig({}), { ok: false, reason: 'off' });

  const partial = apns.readConfig({ ...ENV, APNS_TEAM_ID: '' });
  assert.equal(partial.reason, 'missing');
  assert.deepEqual(partial.missing, ['APNS_TEAM_ID']);

  assert.equal(apns.readConfig({ ...ENV, APNS_KEY_P8: 'not-a-key' }).reason, 'bad_key');
  const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey.export({ type: 'pkcs8', format: 'pem' });
  assert.equal(apns.readConfig({ ...ENV, APNS_KEY_P8: rsa }).reason, 'bad_key', 'فقط EC ِ P-256 (ES256)');
  const p384 = generateKeyPairSync('ec', { namedCurve: 'secp384r1' }).privateKey.export({ type: 'pkcs8', format: 'pem' });
  assert.equal(apns.readConfig({ ...ENV, APNS_KEY_P8: p384 }).reason, 'bad_key');
  assert.equal(apns.readConfig({ ...ENV, APNS_ENV: 'staging' }).reason, 'bad_env');
  assert.equal(apns.readConfig({ ...ENV, APNS_KEY_ID: 'short' }).reason, 'bad_key_id');
  assert.equal(apns.readConfig({ ...ENV, APNS_TEAM_ID: 'team/../x1' }).reason, 'bad_team_id');
  assert.equal(apns.readConfig({ ...ENV, APNS_BUNDLE_ID: 'ir.x\r\nX: y' }).reason, 'bad_bundle_id');

  for (const r of [partial, apns.readConfig({ ...ENV, APNS_KEY_P8: 'not-a-key' })]) {
    assert.ok(!JSON.stringify(r).includes(P8_BODY.slice(0, 20)), 'کلید در نتیجه نیست');
  }
});

test('تنظیم: محیطِ پیش‌فرض production؛ sandbox/development؛ کلید به سه شکلِ فایلِ ‎.env‎', () => {
  const prod = apns.readConfig({ ...ENV, APNS_ENV: '' });
  assert.equal(prod.cfg.env, 'production');
  assert.equal(prod.cfg.origin, 'https://api.push.apple.com');
  assert.equal(apns.readConfig(ENV).cfg.origin, 'https://api.sandbox.push.apple.com');
  assert.equal(apns.readConfig({ ...ENV, APNS_ENV: 'Development' }).cfg.env, 'sandbox');

  const escaped = P8.trim().replace(/\n/g, '\\n');
  assert.ok(apns.readConfig({ ...ENV, APNS_KEY_P8: escaped }).ok, 'PEM با «\\n» ِ نوشته‌شده');
  assert.ok(apns.readConfig({ ...ENV, APNS_KEY_P8: Buffer.from(P8).toString('base64') }).ok, 'base64 ِ کلِ فایل');
  assert.ok(apns.readConfig({ ...ENV, APNS_KEY_P8: P8_BODY }).ok, 'فقط بدنهٔ base64');
});

/* ------------------------------- JWT ------------------------------ */

test('JWT ِ ES256: kid و iss درست، امضای خامِ ۶۴ بایتی با کلیدِ عمومی درست، ۴۰ دقیقه یکی', () => {
  const { s, tick } = senderFor('http://127.0.0.1:1');
  const first = s.providerToken();
  const d = decodeJwt(first);
  assert.deepEqual(d.head, { alg: 'ES256', kid: 'ABC123DEFG' });
  assert.deepEqual(d.claims, { iss: 'TEAM123456', iat: T0 / 1000 });
  assert.equal(d.sig.length, 64, 'r‖s ِ JWS، نه DER');
  assert.ok(verify('sha256', d.signed, { key: publicKey, dsaEncoding: 'ieee-p1363' }, d.sig));

  tick(39 * 60 * 1000);
  assert.equal(s.providerToken(), first, 'پیش از ۴۰ دقیقه همان (اپل تازه کردنِ زیر ۲۰ دقیقه را رد می‌کند)');
  tick(2 * 60 * 1000);
  const next = s.providerToken();
  assert.notEqual(next, first);
  assert.equal(decodeJwt(next).claims.iat, (T0 + 41 * 60 * 1000) / 1000);
  s.close();
});

/* ----------------------------- فرستادن ---------------------------- */

test('فرستادن: مسیر، سرآیندها و بدنهٔ اعلان همان قاعدهٔ اپل', async () => {
  const apple = await mockApple(() => ({ status: 200 }));
  const { s } = senderFor(apple.origin);
  try {
    const r = await s.send(TOKEN, {
      title: 'سفارشِ تازه', body: 'برای دیدن باز کنید', channel: 'orders', collapseId: 'ntf_1',
      data: { type: 'order_notify', orderId: 'ord_1', orderNo: 12, aps: 'نباید جای aps بنشیند' },
    });
    assert.equal(r.ok, true);
    assert.equal(r.attempts, 1);
    const [req] = apple.seen;
    assert.equal(req.headers[':method'], 'POST');
    assert.equal(req.headers[':path'], `/3/device/${TOKEN}`);
    assert.equal(req.headers['apns-topic'], 'ir.vil3ntec.tohid');
    assert.equal(req.headers['apns-push-type'], 'alert');
    assert.equal(req.headers['apns-priority'], '10');
    assert.equal(req.headers['apns-expiration'], String(T0 / 1000 + 24 * 3600));
    assert.equal(req.headers['apns-collapse-id'], 'ntf_1');
    assert.match(req.headers.authorization, /^bearer [\w-]+\.[\w-]+\.[\w-]+$/);
    const jwt = decodeJwt(req.headers.authorization.slice('bearer '.length));
    assert.ok(verify('sha256', jwt.signed, { key: publicKey, dsaEncoding: 'ieee-p1363' }, jwt.sig));

    const body = JSON.parse(req.body);
    assert.deepEqual(body.aps, { alert: { title: 'سفارشِ تازه', body: 'برای دیدن باز کنید' }, sound: 'default', 'thread-id': 'orders' });
    assert.equal(body.type, 'order_notify');
    assert.equal(body.orderNo, '12', 'دادهٔ پیام رشته است، مثلِ FCM');
  } finally {
    s.close();
    await apple.close();
  }
});

test('۴۱۰ و BadDeviceToken ⇒ مرده؛ DeviceTokenNotForTopic ⇒ نه مرده، نه دوباره', async () => {
  const replies = [{ status: 410, reason: 'Unregistered' }, { status: 400, reason: 'BadDeviceToken' }, { status: 400, reason: 'DeviceTokenNotForTopic' }];
  const apple = await mockApple((req, n) => replies[n - 1]);
  const { s, waits } = senderFor(apple.origin);
  try {
    assert.deepEqual(await s.send(TOKEN, { title: 'x', body: 'y' }), { ok: false, dead: true, status: 410, reason: 'Unregistered', attempts: 1 });
    const bad = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.equal(bad.dead, true);
    assert.equal(bad.reason, 'BadDeviceToken');
    const topic = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.equal(topic.dead, undefined, 'شناسهٔ نادرستِ سرور همهٔ توکن‌ها را نکشد');
    assert.equal(topic.attempts, 1);
    assert.equal(apple.seen.length, 3);
    assert.deepEqual(waits, []);
  } finally {
    s.close();
    await apple.close();
  }
});

test('۵۰۳ و ۴۲۹ ⇒ دوباره با عقب‌نشینیِ نمایی (و retry-after)؛ پس از سقف رها', async () => {
  let plan = [];
  const apple = await mockApple((req, n) => plan[n - 1] || { status: 200 });
  try {
    plan = [{ status: 503, reason: 'ServiceUnavailable' }, { status: 500, reason: 'InternalServerError' }];
    let { s, waits } = senderFor(apple.origin);
    const ok = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.equal(ok.ok, true);
    assert.equal(ok.attempts, 3);
    assert.equal(waits.length, 2);
    assert.ok(waits[0] >= 400 && waits[0] <= 600, `نخستین درنگ ~۵۰۰ms (${waits[0]})`);
    assert.ok(waits[1] >= 800 && waits[1] <= 1200, `دومی ~۱۰۰۰ms (${waits[1]})`);
    s.close();

    apple.seen.length = 0;
    plan = [{ status: 429, reason: 'TooManyRequests', headers: { 'retry-after': '2' } }];
    ({ s, waits } = senderFor(apple.origin));
    assert.equal((await s.send(TOKEN, { title: 'x', body: 'y' })).ok, true);
    assert.deepEqual(waits, [2000], 'retry-after ِ اپل');
    s.close();

    apple.seen.length = 0;
    plan = Array(10).fill({ status: 429, reason: 'TooManyRequests' });
    ({ s, waits } = senderFor(apple.origin, { retries: 2 }));
    const gave = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.deepEqual(gave, { ok: false, status: 429, reason: 'TooManyRequests', attempts: 3 });
    assert.equal(apple.seen.length, 3);
    s.close();
  } finally {
    await apple.close();
  }
});

test('قطعِ اتصال وسطِ درخواست ⇒ نشستِ تازه و همان پیام دوباره', async () => {
  const apple = await mockApple((req, n) => (n === 1 ? 'drop' : { status: 200 }));
  const { s, waits } = senderFor(apple.origin);
  try {
    const r = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.equal(r.ok, true);
    assert.equal(r.attempts, 2);
    assert.equal(waits.length, 1);
    assert.notEqual(apple.seen[0].session, apple.seen[1].session, 'اتصالِ تازه');
    assert.equal(apple.seen[0].body, apple.seen[1].body);
  } finally {
    s.close();
    await apple.close();
  }
});

test('ExpiredProviderToken ⇒ یک بار JWT ِ تازه؛ توکنِ جوان تازه نمی‌شود (TooManyProviderTokenUpdates)', async () => {
  let plan = [];
  const apple = await mockApple((req, n) => plan[n - 1] || { status: 200 });
  const { s, tick } = senderFor(apple.origin);
  try {
    assert.equal((await s.send(TOKEN, { title: 'x', body: 'y' })).ok, true);
    tick(25 * 60 * 1000);
    plan = [{ status: 200 }, { status: 403, reason: 'ExpiredProviderToken' }];
    const r = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.equal(r.ok, true);
    const auth = apple.seen.map((x) => x.headers.authorization);
    assert.equal(auth[1], auth[0], 'تا خطای اپل همان توکن');
    assert.notEqual(auth[2], auth[1], 'پس از ExpiredProviderToken توکنِ تازه');

    apple.seen.length = 0;
    plan = [{ status: 403, reason: 'ExpiredProviderToken' }];
    const young = await s.send(TOKEN, { title: 'x', body: 'y' });
    assert.equal(young.fatal, true, 'توکنِ تازه‌ساز هنوز زیرِ ۲۰ دقیقه است ⇒ ساعتِ سرور را باید دید، نه تازه کردن');
    assert.equal(apple.seen.length, 1);
  } finally {
    s.close();
    await apple.close();
  }
});

test('InvalidProviderToken ⇒ fatal بی تلاشِ دوباره؛ توکنِ غیرهگز بی هیچ درخواستی رد', async () => {
  const apple = await mockApple(() => ({ status: 403, reason: 'InvalidProviderToken' }));
  const { s } = senderFor(apple.origin);
  try {
    assert.deepEqual(await s.send(TOKEN, { title: 'x', body: 'y' }), { ok: false, fatal: true, status: 403, reason: 'InvalidProviderToken', attempts: 1 });
    for (const t of ['../../3/device/x', 'apns-hex-1', '', 'ab', `${TOKEN}0`, null]) {
      const r = await s.send(t, { title: 'x', body: 'y' });
      assert.equal(r.dead, true);
      assert.equal(r.reason, 'BadTokenFormat');
    }
    assert.equal(apple.seen.length, 1);
  } finally {
    s.close();
    await apple.close();
  }
});

test('بدنهٔ بلندتر از ۴KB کوتاه می‌شود، نه رد', () => {
  const json = apns.payloadOf({ title: 'عنوان', body: 'متنِ بلند '.repeat(2000), data: { orderId: 'ord_1' } });
  assert.ok(Buffer.byteLength(json) <= 4096, String(Buffer.byteLength(json)));
  const p = JSON.parse(json);
  assert.equal(p.aps.alert.title, 'عنوان');
  assert.equal(p.orderId, 'ord_1');
  assert.ok(p.aps.alert.body.endsWith('…'));
});

test('هیچ توکن، کلید یا JWT در لاگ و نتیجه نیست — حتی با تنظیمِ خراب', async () => {
  const before = lines.length;
  const bad = { ...ENV, APNS_KEY_P8: `${P8_BODY.slice(0, 40)}garbage` };
  assert.equal(apns.sender(bad), null);
  assert.deepEqual(apns.status(bad), { configured: false, env: '', reason: 'bad_key' });
  assert.equal(apns.sender({ ...ENV, APNS_TEAM_ID: '' }), null);

  const apple = await mockApple((req, n) => (n === 1 ? 'drop' : { status: 410, reason: 'Unregistered' }));
  const { s } = senderFor(apple.origin);
  const results = [];
  try {
    results.push(await s.send(TOKEN, { title: 'x', body: 'y' }));
    results.push(await s.send('zz-not-hex', { title: 'x', body: 'y' }));
  } finally {
    s.close();
    await apple.close();
  }
  const jwt = s.providerToken();
  const printed = lines.slice(before).join('\n') + JSON.stringify(results);
  assert.ok(lines.length > before, 'تنظیمِ خراب یک هشدار دارد');
  assert.match(lines.slice(before).join('\n'), /\[apns\] خاموش: bad_key/);
  assert.match(lines.slice(before).join('\n'), /APNS_TEAM_ID/);
  for (const secret of [TOKEN, P8_BODY.slice(0, 24), P8_BODY.slice(-24), jwt, jwt.split('.')[2], 'zz-not-hex']) {
    assert.ok(!printed.includes(secret), `راز در لاگ/نتیجه: ${secret.slice(0, 6)}…`);
  }
  //  برگشت به محیطِ آزمون (بی APNS_*) ⇒ خاموش
  assert.equal(apns.sender(), null);
});
