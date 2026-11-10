'use strict';
/**
 * پوشِ اندروید با Firebase (O-01) — فرستنده با FCM HTTP v1 روی **سرورِ ساختگیِ همین ماشین** (نه شبکه):
 *
 *   ۱) بی حسابِ سرویس ⇒ هیچ درخواستی به FCM نمی‌رود، هیچ خطایی بیرون نمی‌آید و فقط **یک بار** در لاگ گفته می‌شود
 *   ۲) حسابِ سرویس از `FCM_SERVICE_ACCOUNT_FILE` ⇒ JWT ِ RS256 با کلیدِ خودش (سرورِ ساختگی امضا را می‌سنجد)، توکنِ
 *      دسترسی یک بار، پیام با کانال/tag/ttl؛ UNREGISTERED، INVALID_ARGUMENT ِ توکن و SENDER_ID_MISMATCH ⇒ توکن کهنه
 *      با دلیل؛ INVALID_ARGUMENT ِ خودِ پیام ⇒ توکن زنده می‌ماند
 *   ۳) `/api/shop/push`: پیش‌فرض خاموش، فقط همین عضو و همین دستگاه، یک توکنِ زنده برای هر دستگاه
 *   ۴) سفارشِ مشتری ⇒ پوشِ بی دادهٔ مشتری (فقط شماره و شمار)؛ «پرداخت شد» و «مشتری لغو کرد» یک بار
 *   ۵) برداشتنِ عضو و خروج از دستگاه ⇒ توکن کهنه
 */
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-push-fcm';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const nodePath = require('node:path');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const push = require('../src/lib/push');
const N = require('../src/lib/order-notify');
const P = require('../src/lib/payment-providers');

/* ── سرورِ ساختگیِ گوگل: توکنِ OAuth و messages:send ──────────────────────── */
const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});
const PROJECT = 'vill3n-test';
const fake = { tokenCalls: 0, badJwt: 0, sends: [] };
const fail = (res, status, code, message, details = []) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: { code: status, message, status: code, details } }));
};
const server = http.createServer((req, res) => {
  let body = '';
  req.on('data', (c) => { body += c; });
  req.on('end', () => {
    if (req.url === '/token') {
      fake.tokenCalls++;
      const jwt = new URLSearchParams(body).get('assertion') || '';
      const [hd, cl, sig] = jwt.split('.');
      const ok = crypto.verify('RSA-SHA256', Buffer.from(`${hd}.${cl}`), publicKey, Buffer.from(sig || '', 'base64url'));
      const claim = JSON.parse(Buffer.from(cl || '', 'base64url').toString() || '{}');
      if (!ok || !/firebase\.messaging/.test(claim.scope || '')) { fake.badJwt++; return fail(res, 401, 'UNAUTHENTICATED', 'bad jwt'); }
      res.writeHead(200, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ access_token: 'fake-access', expires_in: 3600 }));
    }
    if (req.url !== `/v1/projects/${PROJECT}/messages:send`) return fail(res, 404, 'NOT_FOUND', 'no route');
    if (req.headers.authorization !== 'Bearer fake-access') return fail(res, 401, 'UNAUTHENTICATED', 'no bearer');
    const msg = JSON.parse(body).message;
    fake.sends.push(msg);
    const t = msg.token;
    const fcmCode = (c) => [{ '@type': 'type.googleapis.com/google.firebase.fcm.v1.FcmError', errorCode: c }];
    if (t.startsWith('dead')) return fail(res, 404, 'NOT_FOUND', 'Requested entity was not found.', fcmCode('UNREGISTERED'));
    if (t.startsWith('bad')) return fail(res, 400, 'INVALID_ARGUMENT', 'The registration token is not a valid FCM registration token', fcmCode('INVALID_ARGUMENT'));
    if (t.startsWith('other')) return fail(res, 403, 'PERMISSION_DENIED', 'SenderId mismatch', fcmCode('SENDER_ID_MISMATCH'));
    if (t.startsWith('payload')) return fail(res, 400, 'INVALID_ARGUMENT', "Invalid value at 'message.data[0].value'", fcmCode('INVALID_ARGUMENT'));
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ name: `projects/${PROJECT}/messages/${fake.sends.length}` }));
  });
});

let origin = '';
let admin = '';
const saFile = nodePath.join(os.tmpdir(), `push-fcm-sa-${process.pid}.json`);
const tok = (p) => `${p}${'x'.repeat(40)}:APA91b${crypto.randomBytes(12).toString('hex')}`;

/** console.warn ِ این دوره — «یک بار در لاگ» شمرده می‌شود */
let warns = [];
const realWarn = console.warn;

test.before(async () => {
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  origin = `http://127.0.0.1:${server.address().port}`;
  fs.writeFileSync(saFile, JSON.stringify({
    type: 'service_account', project_id: PROJECT, private_key: privateKey,
    client_email: `push@${PROJECT}.iam.gserviceaccount.com`, token_uri: `${origin}/token`,
  }), { mode: 0o600 });
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  console.warn = (...a) => { warns.push(a.join(' ')); };
});
test.after(async () => {
  console.warn = realWarn;
  delete process.env.FCM_SERVICE_ACCOUNT_FILE;
  push.useFcmOrigin(null);
  fs.rmSync(saFile, { force: true });
  await h.stop();
  await new Promise((r) => server.close(r));
});

function credentials(on) {
  if (on) process.env.FCM_SERVICE_ACCOUNT_FILE = saFile;
  else delete process.env.FCM_SERVICE_ACCOUNT_FILE;
  push.useFcmOrigin(origin);   // تازه: نشانیِ ساختگی، کشِ تنظیم، توکنِ دسترسی و «یک بار» ِ هشدار
  warns = [];
}
const flag = async (scope, key, value = true) => {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const tokenRow = (token) => one('SELECT * FROM push_tokens WHERE token=$1', [token]);

async function owner(name) {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

/* ══ ۱) بی کلید ═══════════════════════════════════════════════════════ */

test('بی حسابِ سرویس: هیچ درخواستی به FCM نمی‌رود، خطایی بیرون نمی‌آید و فقط یک بار در لاگ', async () => {
  const a = await owner('بی‌کلید');
  await push.register({ app: 'shop', token: tok('ok'), userId: a.user.id, shopId: a.shopId, deviceUid: 'd-none' });
  credentials(false);
  await push.save({ enabled: false });
  const before = fake.sends.length + fake.tokenCalls;
  const off1 = await push.sendTo({ userId: a.user.id, app: 'shop' }, { title: 't', body: 'b' });
  const off2 = await push.sendTo({ userId: a.user.id, app: 'shop' }, { title: 't', body: 'b' });
  assert.deepEqual([off1.skipped, off2.skipped], ['push_off', 'push_off']);
  assert.equal(warns.filter((w) => w.includes('پوش روشن')).length, 1, warns.join('\n'));

  await push.save({ enabled: true });
  credentials(false);
  for (let i = 0; i < 3; i++) {
    const r = await push.sendTo({ userId: a.user.id, app: 'shop' }, { title: 't', body: 'b' });
    assert.equal(r.sent, 0);
    assert.equal(r.skipped, 'push_off');
  }
  assert.equal(fake.sends.length + fake.tokenCalls, before, 'هیچ درخواستی به گوگل نرفت');
  assert.equal(warns.length, 1, warns.join('\n'));
  assert.match(warns[0], /FCM_SERVICE_ACCOUNT_FILE/);
  assert.ok(!warns[0].includes('PRIVATE KEY'));
  assert.equal((await tokenRow((await many('SELECT token FROM push_tokens WHERE user_id=$1', [a.user.id]))[0].token)).status, 'active');

  //  فایلِ نبوده ⇒ همان خاموش، یک هشدارِ دیگر، بی شکست
  process.env.FCM_SERVICE_ACCOUNT_FILE = nodePath.join(os.tmpdir(), `nope-${process.pid}.json`);
  push.useFcmOrigin(origin); warns = [];
  assert.equal((await push.sendTo({ userId: a.user.id, app: 'shop' }, { title: 't', body: 'b' })).sent, 0);
  assert.equal(warns.filter((w) => w.includes('ENOENT')).length, 1, warns.join('\n'));
  assert.equal((await push.masked()).configured, false);
});

/* ══ ۲) فرستادن و کنار گذاشتنِ توکنِ مرده ═══════════════════════════════ */

test('FCM v1: JWT ِ امضاشده، یک توکنِ دسترسی، پیام با کانال/tag/ttl؛ UNREGISTERED/INVALID/SENDER_ID_MISMATCH ⇒ کهنه با دلیل', async () => {
  const a = await owner('فرستنده');
  await push.save({ enabled: true });
  credentials(true);
  assert.equal((await push.masked()).source, 'file');
  const t = { ok: tok('ok'), dead: tok('dead'), bad: tok('bad'), other: tok('other'), payload: tok('payload') };
  for (const [k, v] of Object.entries(t)) {
    await push.register({ app: 'shop', token: v, userId: a.user.id, shopId: a.shopId, deviceUid: `d-${k}`, platform: 'android' });
  }
  const calls = fake.tokenCalls;
  const from = fake.sends.length;
  const msg = { title: 'سفارشِ تازه', body: 'باز کنید', channel: 'orders', tag: 'order-x', ttlSeconds: 7200, data: { type: 'order_notify', n: 3 } };
  const out = await push.sendTo({ userId: a.user.id, app: 'shop' }, msg);
  assert.equal(out.sent, 1, JSON.stringify(out));
  assert.equal(out.total, 5);
  assert.equal(fake.tokenCalls - calls, 1, 'یک توکنِ دسترسی برای کلِ دسته');
  assert.equal(fake.badJwt, 0);
  const okMsg = fake.sends.slice(from).find((m) => m.token === t.ok);
  assert.deepEqual(okMsg.android, { priority: 'high', notification: { channel_id: 'orders', tag: 'order-x' }, ttl: '7200s' });
  assert.deepEqual(okMsg.data, { type: 'order_notify', n: '3' }, 'دادهٔ FCM فقط رشته');
  assert.deepEqual(okMsg.notification, { title: 'سفارشِ تازه', body: 'باز کنید' });

  const rows = Object.fromEntries(await Promise.all(Object.entries(t).map(async ([k, v]) => [k, await tokenRow(v)])));
  assert.equal(rows.ok.status, 'active');
  assert.ok(rows.ok.last_ok_at);
  assert.deepEqual([rows.dead.status, rows.dead.stale_reason], ['stale', 'fcm_unregistered']);
  assert.deepEqual([rows.bad.status, rows.bad.stale_reason], ['stale', 'fcm_invalid']);
  assert.deepEqual([rows.other.status, rows.other.stale_reason], ['stale', 'fcm_sender_mismatch']);
  assert.equal(rows.payload.status, 'active', 'خطای خودِ پیام توکن را نمی‌بُرد');
  assert.match(rows.payload.last_error, /INVALID_ARGUMENT/);
  for (const r of Object.values(rows)) assert.ok(!r.last_error.includes(r.token), 'توکن در last_error نیست');

  //  بارِ دوم: توکنِ مرده دیگر فرستاده نمی‌شود؛ توکنِ دسترسی از کش
  const from2 = fake.sends.length;
  await push.sendTo({ userId: a.user.id, app: 'shop' }, msg);
  assert.deepEqual(fake.sends.slice(from2).map((m) => m.token).sort(), [t.ok, t.payload].sort());
  assert.equal(fake.tokenCalls - calls, 1);
  assert.equal(warns.length, 0, warns.join('\n'));
});

/* ══ ۳) ثبتِ توکن از برنامه ═══════════════════════════════════════════ */

test('/api/shop/push: پیش‌فرض خاموش؛ فقط همین عضو و دستگاه؛ یک توکنِ زنده برای هر دستگاه؛ پاک کردن فقط مالِ خودش', async () => {
  const a = await owner('ثبت');
  const b = await owner('دیگری');
  credentials(false);
  let cfg = await h.get('/api/shop/push/config', { token: a.token });
  assert.equal(cfg.status, 200, JSON.stringify(cfg.body));
  assert.deepEqual(cfg.body, { enabled: false, server: { ready: false, fcm: false, apns: false }, device: { registered: false }, orderAlerts: false });
  const t1 = tok('ok');
  const off = await h.post('/api/shop/push/devices', { token: t1 }, { token: a.token });
  assert.equal(off.status, 403);
  assert.equal(off.body.error.code, 'device_push_disabled');
  assert.equal(await tokenRow(t1), null, 'پرچمِ خاموش ⇒ هیچ توکنی جمع نمی‌شود');

  await flag(a.shopId, 'shop.device_push');
  for (const bad of ['', 'short', 'x'.repeat(501), `${'a'.repeat(40)} <script>`]) {
    const r = await h.post('/api/shop/push/devices', { token: bad }, { token: a.token });
    assert.equal(r.status, 400, bad);
  }
  assert.equal((await h.post('/api/shop/push/devices', { token: t1, provider: 'webpush' }, { token: a.token })).status, 400);
  //  شناسه‌های بدنه نادیده‌اند
  const ok = await h.post('/api/shop/push/devices', { token: t1, userId: b.user.id, shopId: b.shopId, platform: 'android' }, { token: a.token });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  const r1 = await tokenRow(t1);
  assert.deepEqual([r1.user_id, r1.shop_id, r1.app, r1.provider, r1.platform], [a.user.id, a.shopId, 'shop', 'fcm', 'android']);
  assert.ok(r1.device_uid, 'دستگاه از نشست');
  credentials(true);
  cfg = await h.get('/api/shop/push/config', { token: a.token });
  assert.deepEqual(cfg.body, { enabled: true, server: { ready: true, fcm: true, apns: false }, device: { registered: true }, orderAlerts: false });
  assert.ok(!JSON.stringify(cfg.body).includes(PROJECT), 'نامِ پروژه به برنامه نمی‌رود');

  //  توکنِ تازهٔ همان دستگاه ⇒ پیشین کهنه
  const t2 = tok('ok');
  assert.equal((await h.post('/api/shop/push/devices', { token: t2 }, { token: a.token })).status, 201);
  assert.deepEqual([(await tokenRow(t1)).status, (await tokenRow(t1)).stale_reason], ['stale', 'replaced']);
  assert.equal((await tokenRow(t2)).status, 'active');

  //  «ب» توکنِ «الف» را پاک نمی‌کند
  const del = await h.api('DELETE', '/api/shop/push/devices', { body: { token: t2 }, token: b.token });
  assert.equal(del.status, 200);
  assert.equal(del.body.revoked, 0);
  assert.equal((await tokenRow(t2)).status, 'active');
  const mine = await h.api('DELETE', '/api/shop/push/devices', { body: {}, token: a.token });
  assert.equal(mine.body.revoked, 1);
  assert.deepEqual([(await tokenRow(t2)).status, (await tokenRow(t2)).stale_reason], ['stale', 'unregistered']);
  assert.equal((await h.get('/api/shop/push/config', { token: a.token })).body.device.registered, false);
});

/* ══ ۴) سفارش: تازه، پرداخت، لغوِ مشتری ═══════════════════════════════ */

let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

async function liveShop(label) {
  const a = await owner(label);
  for (const k of ['shop.orders', 'shop.storefront', 'pay.hesabpay', 'shop.order_notify', 'shop.device_push']) await flag(a.shopId, k);
  const slug = `push-${uniq()}`;
  //  پرداختِ آنلاین از همان «تازه» (مهاجرتِ ۲۱۱ پیش‌فرضِ دکانِ تازه را «پس از پذیرش» کرد)
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', payAfterAccept: false }, { token: a.token });
  assert.ok(put.status < 300, JSON.stringify(put.body));
  const ops = [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 125 } }];
  const p = await h.api('POST', '/api/sync/v1/push', { token: a.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-push' }, body: { device_id: 'dev-push', schema_version: 2, ops } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const pub = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: a.token });
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  const g = await h.post('/api/admin/subscriptions', { shopId: a.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  return { ...a, slug };
}

async function customerOrder(s, phone = '0791230007') {
  const v = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: v.body.devCode });
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'گل‌احمدِ مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 2000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const row = await one('SELECT id, order_no FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1', [s.shopId]);
  return { token: ck.body.order.token, id: row.id, no: Number(row.order_no) };
}

const sendsTo = (from, token) => fake.sends.slice(from).filter((m) => m.token === token);

test('سفارشِ مشتری ⇒ پوشِ بی دادهٔ مشتری؛ «پرداخت شد» و «مشتری لغو کرد» یک بار و delivered', async () => {
  credentials(true);
  await push.save({ enabled: true });
  const s = await liveShop('سفارش‌دار');
  const mobile = tok('ok');
  assert.equal((await h.post('/api/shop/push/devices', { token: mobile }, { token: s.token })).status, 201);

  let from = fake.sends.length;
  const o = await customerOrder(s);
  await N.tick(now() + 1000);
  const [created] = sendsTo(from, mobile);
  assert.ok(created, 'پوشِ سفارشِ تازه رفت');
  assert.match(created.notification.title, /سفارشِ تازه/);
  assert.equal(created.data.type, 'order_notify');
  assert.equal(created.data.event, 'order_created');
  assert.equal(created.data.orderNo, String(o.no));
  assert.equal(created.data.count, '1');
  assert.equal(created.android.notification.tag, `order-${o.id}`);
  /*
   *  بی دادهٔ مشتری و بی مبلغ: «250» و «25000» همان جمعِ سفارش‌اند (۱۲۵ × ۲ کیلو؛ `total_minor`)، از خودِ ردیف خوانده.
   *  ⛔ لرزان بود: توکنِ دستگاه، شناسهٔ دکان (هگزِ تصادفی) و شناسهٔ سفارش و اعلان (ULID) گاهی خودشان «250» داشتند (~۱٪).
   *  آن چهار شناسهٔ مات اول با مقدارِ شناخته‌شده‌شان سنجیده و بعد از متن برداشته می‌شوند؛ بقیهٔ متن (عنوان، متن، داده، tag)
   *  سخت‌تر از پیش سنجیده می‌شود: رقمِ فارسی/عربی و جداکنندهٔ هزارگان هم (۲۵۰، 25,000) نشت است.
   */
  const ord = await one('SELECT total_minor, subtotal_minor FROM online_orders WHERE id=$1', [o.id]);
  const money = [...new Set([ord.total_minor, ord.total_minor / 100, ord.subtotal_minor / 100, 125].map((n) => String(Number(n))))];
  assert.deepEqual(money, ['25000', '250', '125'], 'جمع و بهای سفارشِ همین آزمون');
  assert.equal(created.token, mobile);
  assert.equal(created.data.orderId, o.id);
  assert.equal(created.data.shopId, s.shopId);
  assert.match(created.data.notifId, /^[0-9A-HJKMNP-TV-Z]{26}$/);
  let text = JSON.stringify(created);
  for (const id of [mobile, o.id, s.shopId, created.data.notifId]) text = text.split(id).join('<id>');
  text = text.replace(/[۰-۹]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d)).replace(/[٠-٩]/g, (d) => '٠١٢٣٤٥٦٧٨٩'.indexOf(d))
    .replace(/(\d)[,\u066c\u00a0\u202f](?=\d)/g, '$1');
  for (const pii of ['گل‌احمد', '0791230007', '791230007', 'برنج', ...money]) assert.ok(!text.includes(pii), `بی ${pii}: ${text}`);

  //  پرداختِ آنلاین ⇒ یک پوشِ «پرداخت شد»
  const pay = await h.post(`/api/store/${s.slug}/orders/${o.token}/pay`, {}, { headers: { 'Idempotency-Key': `pay-${uniq()}-key` } });
  assert.equal(pay.status, 201, JSON.stringify(pay.body));
  const intent = await one(`SELECT * FROM payment_intents WHERE ref_id=$1 AND kind='online_order'`, [o.id]);
  const buf = Buffer.from(JSON.stringify({ eventId: `ev-${uniq()}`, sessionId: P.fake.sessionOf(intent.id), status: 'paid', amountMinor: Number(intent.amount_minor), currency: 'AFN', providerRef: 'HP-PUSH-1', settle: true }));
  const w = await h.raw('POST', '/api/payments/webhook/fake', buf, { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(buf) } });
  assert.equal(w.body.outcome, 'applied', JSON.stringify(w.body));
  from = fake.sends.length;
  await N.tick(now() + 2000);
  const paid = sendsTo(from, mobile).filter((m) => m.data.event === 'order_paid');
  assert.equal(paid.length, 1);
  assert.match(paid[0].notification.title, /پرداخت شد/);
  assert.equal(paid[0].android.notification.tag, `order-${o.id}-paid`);
  const paidRow = await one(`SELECT * FROM order_notify_outbox WHERE order_id=$1 AND event='order_paid'`, [o.id]);
  assert.equal(paidRow.status, 'delivered');
  //  دوباره نمی‌رود
  from = fake.sends.length;
  await N.tick(now() + 10 * 60_000);
  assert.equal(sendsTo(from, mobile).filter((m) => m.data.event === 'order_paid').length, 0);
  //  «تازه» ِ برنامه فقط سفارشِ تازه را می‌شمارد
  const pend = await h.get('/api/shop/order-notify/pending', { token: s.token });
  assert.ok(pend.body.notifications.every((n) => n.event === 'order_created'), JSON.stringify(pend.body));

  //  لغوِ مشتری ⇒ همان tag ِ «سفارشِ تازه» جایگزین می‌شود
  const o2 = await customerOrder(s, '0791230008');
  const cancel = await h.post(`/api/store/${s.slug}/orders/${o2.token}/cancel`, {});
  assert.ok(cancel.status < 300, JSON.stringify(cancel.body));
  from = fake.sends.length;
  await N.tick(now() + 20 * 60_000);
  const cancelled = sendsTo(from, mobile).filter((m) => m.data.event === 'order_cancelled');
  assert.equal(cancelled.length, 1, JSON.stringify(fake.sends.slice(from).map((m) => m.data)));
  assert.match(cancelled[0].notification.title, /لغو کرد/);
  assert.equal(cancelled[0].android.notification.tag, `order-${o2.id}`);
  assert.equal((await one(`SELECT status FROM order_notify_outbox WHERE order_id=$1 AND event='order_cancelled'`, [o2.id])).status, 'delivered');

  //  پرچمِ سفارش خاموش ⇒ رویدادِ یک‌باره هم نوشته نمی‌شود
  await flag(s.shopId, 'shop.order_notify', false);
  const o3 = await customerOrder(s, '0791230009');
  await h.post(`/api/store/${s.slug}/orders/${o3.token}/cancel`, {});
  assert.equal((await many('SELECT 1 FROM order_notify_outbox WHERE order_id=$1', [o3.id])).length, 0);
});

/* ══ ۵) کنار گذاشتن ═══════════════════════════════════════════════════ */

test('برداشتنِ عضو و خروج از دستگاه ⇒ توکن کهنه با دلیل؛ دستگاهِ دیگر دست نمی‌خورد', async () => {
  const a = await owner('کنار');
  await flag(a.shopId, 'shop.device_push');
  const staff = await h.newUser('شاگردِ کنار');
  const memberId = newId('mem');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [memberId, a.shopId, staff.user.id, now()]);
  const ts = tok('ok');
  const ta = tok('ok');
  assert.equal((await h.post('/api/shop/push/devices', { token: ts }, { token: staff.accessToken })).status, 201);
  assert.equal((await h.post('/api/shop/push/devices', { token: ta }, { token: a.token })).status, 201);
  //  یک گوشیِ دیگرِ صاحب (همان کاربر، دستگاهِ دیگر)
  const other = tok('ok');
  await push.register({ app: 'shop', token: other, userId: a.user.id, shopId: a.shopId, deviceUid: 'another-phone' });

  const rm = await h.api('DELETE', `/api/shop/members/${memberId}`, { token: a.token });
  assert.ok(rm.status < 300, JSON.stringify(rm.body));
  assert.deepEqual([(await tokenRow(ts)).status, (await tokenRow(ts)).stale_reason], ['stale', 'member_removed']);
  assert.equal((await tokenRow(ta)).status, 'active');

  const out = await h.post('/api/auth/logout', { refreshToken: a.refreshToken }, { token: a.token });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.deepEqual([(await tokenRow(ta)).status, (await tokenRow(ta)).stale_reason], ['stale', 'logout']);
  assert.equal((await tokenRow(other)).status, 'active', 'گوشیِ دیگرِ همان کس می‌ماند');

  //  ورودِ دوباره و ثبتِ همان توکن ⇒ همان ردیف زنده
  const again = await h.signIn(a);
  assert.equal((await h.post('/api/shop/push/devices', { token: ta }, { token: again.accessToken })).status, 201);
  const back = await tokenRow(ta);
  assert.deepEqual([back.status, back.stale_reason], ['active', '']);
});

/* ══ ۶) مهاجرتِ ۲۱۳ ══════════════════════════════════════════════════ */

test('مهاجرتِ ۲۱۳: توکنِ عضوِ رفته کهنه می‌شود؛ rollback و دوباره نشستن همان', async () => {
  const a = await owner('مهاجرت');
  const gone = await h.newUser('شاگردِ رفته');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','removed',$4,$4)`,
    [newId('mem'), a.shopId, gone.user.id, now()]);
  const tGone = tok('ok');
  const tOwner = tok('ok');
  await push.register({ app: 'shop', token: tGone, userId: gone.user.id, shopId: a.shopId, deviceUid: 'gone-phone' });
  await push.register({ app: 'shop', token: tOwner, userId: a.user.id, shopId: a.shopId, deviceUid: 'owner-phone' });
  const dir = nodePath.join(__dirname, '..', 'migrations');
  const up = fs.readFileSync(nodePath.join(dir, '213_device_push.sql'), 'utf8');
  const down = fs.readFileSync(nodePath.join(dir, 'rollback', '213_device_push.down.sql'), 'utf8');
  const mark = await one("SELECT * FROM schema_migrations WHERE version='213_device_push.sql'");
  assert.ok(mark, 'مهاجرت نشسته است');
  const col = async () => (await query(
    "SELECT column_name FROM information_schema.columns WHERE table_name='push_tokens' AND column_name='stale_reason'"
  )).rows.length;
  await query(down);
  assert.equal(await col(), 0);
  const eventCheck = async () => (await one(
    "SELECT pg_get_constraintdef(oid) AS d FROM pg_constraint WHERE conname='order_notify_outbox_event_check'"
  )).d;
  assert.ok(!(await eventCheck()).includes('order_paid'), 'CHECK ِ پیشین برگشت');
  await query(up);
  assert.ok((await eventCheck()).includes('order_cancelled'));
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await col(), 1);
  assert.deepEqual([(await tokenRow(tGone)).status, (await tokenRow(tGone)).stale_reason], ['stale', 'member_removed']);
  assert.equal((await tokenRow(tOwner)).status, 'active', 'صاحب همیشه می‌ماند');
});
