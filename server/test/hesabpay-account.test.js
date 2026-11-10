'use strict';
/**
 * حساب‌پیِ هر دکان (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ PAY-21، PAY-06، مهاجرتِ ۲۰۴) — `lib/hesabpay-account.js`،
 * `routes/shop-hesabpay.js` و مسیرِ هر دکان در `payment-intents` / `payments-webhook` (حساب‌پیِ واقعی روی سرورِ
 * ساختگیِ loopback، نه `fake`).
 *
 * امنیت: پول فقط به حسابِ همان دکان (کلیدِ دکان در هر درخواست)؛ کلیدِ سکو هرگز جایگزین نیست؛ دکانِ دیگر کلید را
 * نمی‌خواند و به کار نمی‌برد؛ کارمند و مدیر نمی‌گذارند؛ رمزِ حساب‌پی در سرور (نبود/غلط ⇒ رد، ۵ خطا ⇒ قفل، حتی با
 * رمزِ درست)؛ راز هرگز در پاسخ، ردیف، سابقه یا رویداد نیست؛ وب‌هوکِ امضاشده با رازِ دکانِ دیگر رد؛ تکرارِ وب‌هوک
 * یک‌بار؛ «فقط مبلغ برابر» هرگز تأیید نمی‌کند؛ هر تغییر سابقه و خبرِ صاحب دارد.
 * نگهبانِ حسابِ گیرنده (مهاجرتِ ۲۰۵، `hesabpay-guard.js`): نخستین حساب فوری، بعدی ۲۴ ساعت در انتظار؛ نام و شناسهٔ
 * پذیرنده اجباری؛ «پرداخت شد» ِ خودکار فقط با پذیرندهٔ همین دکان — آزمون‌های امنیتیِ جدا: `hesabpay-guard.test.js`.
 */
process.env.PAYMENTS_PROVIDER = '';
process.env.HESABPAY_PIN_ITER = '1000';
process.env.RATE_PAY_CREATE_MAX = '100000';
process.env.RATE_PAY_POLL_MAX = '100000';
process.env.RATE_HESABPAY_ACCOUNT_MAX = '100000';
const CRED_KEY = 'cred-key-for-hesabpay-account-test-0123456789';
process.env.HESABPAY_CRED_KEY = CRED_KEY;
//  ⛔ کلیدِ سکو هم در محیط هست — هیچ‌جا نباید به کار برود
process.env.HESABPAY_API_KEY = 'PLATFORM-KEY-never-use';
process.env.HESABPAY_WEBHOOK_SECRET = 'PLATFORM-SECRET-never-use';
process.env.HESABPAY_MERCHANT_ID = 'PLATFORM-MERCHANT';

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const h = require('./helpers');
const { one, query, many, newId, now } = require('../src/db');
const acc = require('../src/lib/hesabpay-account');
const pay = require('../src/lib/payment-intents');
const push = require('../src/lib/push');
const tokens = require('../src/lib/tokens');
const guard = require('../src/lib/hesabpay-guard');

const PIN = '258014';
const NAME = 'دکانِ آزمون';
const URL = '/api/v1/shop/hesabpay';
let admin = '';
let srv;
const seen = [];
const providerStatus = new Map();
let sessionSeq = 0;
const pushes = [];

test.before(async () => {
  await h.start();
  srv = http.createServer((req, res) => {
    let b = '';
    req.on('data', (d) => { b += d; });
    req.on('end', () => {
      seen.push({ method: req.method, url: req.url, auth: req.headers.authorization || '', body: b ? JSON.parse(b) : null });
      res.setHeader('Content-Type', 'application/json');
      if (req.method === 'POST' && req.url === '/api/v1/payment/create-session') {
        const sid = `HS-${++sessionSeq}`;
        return res.end(JSON.stringify({ url: `https://pay.hesab.example/s/${sid}`, session_id: sid }));
      }
      const m = /^\/api\/v1\/payment\/status\/(.+)$/.exec(req.url);
      if (req.method === 'GET' && m) {
        return res.end(JSON.stringify({ data: providerStatus.get(decodeURIComponent(m[1])) || { status: 'pending' } }));
      }
      res.statusCode = 404;
      res.end('{}');
    });
  });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  process.env.HESABPAY_BASE_URL = `http://127.0.0.1:${srv.address().port}/api/v1`;
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'hp-acc-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'hp-acc-admin', password: 'Admin!12345' })).body.token;
  push.setDeliver((row, message) => { pushes.push({ userId: row.user_id, shopId: row.shop_id, message }); });
});
test.after(async () => {
  push.setDeliver(null);
  await new Promise((r) => srv.close(r));
  await h.stop();
});

let seq = 0;
async function shop(label, { flag = true } = {}) {
  const owner = await h.newUser(`صاحب-${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  if (flag) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'pay.hesabpay', value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  await push.register({ app: 'shop', token: `push-${shopId}`, userId: owner.user.id, shopId, deviceUid: `d-${shopId}` });
  return { owner, shopId, t: owner.accessToken };
}

async function member(s, role) {
  const u = await h.newUser(`${role}-${++seq}`);
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]
  );
  return u.accessToken;
}

const setPin = (s, body = { newPin: PIN }) => h.put(`${URL}/pin`, body, { token: s.t });
/** «خودکار» برای نخستین بار (فوری). `s.merchant` = شناسهٔ پذیرندهٔ همین دکان — وب‌هوک/درِ وضعیت باید همان را بگویند */
async function auto(s, { apiKey, webhookSecret, merchantId } = {}) {
  assert.equal((await setPin(s)).status, 200);
  s.merchant = merchantId || `MERCH-${apiKey}`;
  const r = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'auto', apiKey, webhookSecret, merchantId: s.merchant }, { token: s.t });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.auto.webhookUrl.split('/').pop();
}
const createPos = (s, amount = 145000) => h.post('/api/v1/shop/payments/intents', {
  kind: 'pos_sale', refId: `sale-${++seq}`, amount, currency: 'AFN', idempotencyKey: `key-${Date.now()}-${++seq}-hp`,
}, { token: s.t });
const intentRow = (id) => one('SELECT * FROM payment_intents WHERE id=$1', [id]);
const sign = (secret, raw) => crypto.createHmac('sha256', secret).update(raw).digest('hex');
function hook(path, body, secret) {
  const raw = Buffer.from(JSON.stringify(body));
  return h.raw('POST', path, raw, { headers: { 'Content-Type': 'application/json', 'x-hesab-signature': sign(secret, raw) } });
}
const hookPath = (token) => `/api/v1/payments/webhook/hesabpay/${token}`;

// ════════════════════════════════════════════════════════════════════

test('رمزگذاری: گرد و برگشت؛ بسته به دکان و ستون؛ دست‌کاری، کلیدِ دیگر یا بی کلید ⇒ هیچ؛ چرخشِ کلید', () => {
  const box = acc._seal('shop_a', 'api_key', 'K-123456789');
  assert.match(box, /^v1\.[\w-]+\.[\w-]+\.[\w-]+$/);
  assert.ok(!box.includes('K-123456789'));
  assert.equal(acc._unseal('shop_a', 'api_key', box), 'K-123456789');
  assert.equal(acc._unseal('shop_b', 'api_key', box), null, 'ردیفِ دکانِ دیگر باز نمی‌شود');
  assert.equal(acc._unseal('shop_a', 'secret', box), null, 'ستونِ دیگر باز نمی‌شود');
  const parts = box.split('.');
  const flip = (x) => (x[0] === 'A' ? `B${x.slice(1)}` : `A${x.slice(1)}`);
  assert.equal(acc._unseal('shop_a', 'api_key', [parts[0], parts[1], flip(parts[2]), parts[3]].join('.')), null);
  assert.equal(acc._unseal('shop_a', 'api_key', [parts[0], parts[1], parts[2], parts[3].slice(0, 8)].join('.')), null, 'برچسبِ کوتاه پذیرفته نمی‌شود');
  try {
    process.env.HESABPAY_CRED_KEY = 'another-cred-key-for-rotation-test-0123456789';
    assert.equal(acc._unseal('shop_a', 'api_key', box), null, 'کلیدِ دیگر ⇒ هیچ');
    process.env.HESABPAY_CRED_KEY_PREVIOUS = CRED_KEY;
    assert.equal(acc._unseal('shop_a', 'api_key', box), 'K-123456789', 'کلیدِ پیشین فقط برای خواندن');
    process.env.HESABPAY_CRED_KEY = 'short';
    assert.equal(acc.credKeyState(), 'weak');
    assert.throws(() => acc._seal('shop_a', 'api_key', 'x'), (e) => e.code === 'hesabpay_cred_key_missing');
    delete process.env.HESABPAY_CRED_KEY;
    assert.equal(acc.credKeyState(), 'missing');
  } finally {
    process.env.HESABPAY_CRED_KEY = CRED_KEY;
    delete process.env.HESABPAY_CRED_KEY_PREVIOUS;
  }
  assert.equal(acc.credKeyState(), 'ok');
});

test('شکلِ رمز، شماره و QR: ۶ رقم و نه ساده؛ شمارهٔ افغانستان یک‌دست؛ QR ِ بلند یا با نویسهٔ کنترلی رد', () => {
  for (const [pin, want] of [['258014', null], ['۲۵۸۰۱۴', null], ['12345', 'length'], ['12a456', 'length'], ['111111', 'weak'], ['123456', 'weak'], ['654321', 'weak']]) {
    assert.equal(acc.pinProblem(pin), want, pin);
  }
  for (const [v, want] of [['0799123456', '0799123456'], ['+93 799 123 456', '0799123456'], ['۰۷۹۹۱۲۳۴۵۶', '0799123456'],
    ['0093799123456', '0799123456'], ['799123456', '0799123456'], ['0699123456', null], ['07991234567', null], ['abc', null]]) {
    assert.equal(acc.normAccount(v), want, v);
  }
  assert.equal(acc.normQr('  hesabpay://pay?to=0799123456  '), 'hesabpay://pay?to=0799123456');
  assert.equal(acc.normQr('x'.repeat(513)), null);
  assert.equal(acc.normQr('a\u0001b'), null);
  assert.equal(acc.accountInQr('hesabpay://pay?to=+93799123456&n=1'), '0799123456');
});

test('فقط صاحب: مدیر و کارمند نه می‌بینند، نه رمز می‌گذارند، نه حساب؛ بی توکن ۴۰۱', async () => {
  const s = await shop('فقط-صاحب');
  for (const role of ['manager', 'staff', 'cashier']) {
    const tk = await member(s, role);
    assert.equal((await h.get(URL, { token: tk })).body.error.code, 'owner_only', role);
    assert.equal((await h.put(`${URL}/pin`, { newPin: PIN }, { token: tk })).body.error.code, 'owner_only', role);
    const r = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple', account: '0799123456' }, { token: tk });
    assert.equal(r.status, 403, role);
    assert.equal((await h.post(`${URL}/pin/reset`, {}, { token: tk })).status, 403, role);
  }
  assert.equal((await h.get(URL)).status, 401);
  assert.equal(await one('SELECT 1 AS x FROM shop_hesabpay WHERE shop_id=$1', [s.shopId]), null, 'هیچ ردیفی نوشته نشد');
});

test('رمزِ حساب‌پی در سرور: نبود ⇒ ۴۰۰، غلط ⇒ ۴۰۳ با شمارِ مانده، ۵ خطا ⇒ قفل (رمزِ درست هم) با سابقه و خبرِ صاحب', async () => {
  const s = await shop('قفل');
  //  بی رمز ⇒ اول رمز
  assert.equal((await h.post(`${URL}/open`, { pin: PIN }, { token: s.t })).body.error.code, 'hesabpay_pin_not_set');
  assert.equal((await setPin(s, { newPin: '123456' })).body.error.code, 'hesabpay_pin_weak');
  assert.equal((await setPin(s, { newPin: '1234' })).body.error.code, 'hesabpay_pin_format');
  const first = await setPin(s);
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.equal(first.body.pinSet, true);
  //  بارِ دوم بی رمزِ فعلی ⇒ رد (کسی که نشست دارد رمز را بی‌صدا عوض نمی‌کند)
  assert.equal((await setPin(s, { newPin: '975310' })).body.error.code, 'hesabpay_pin_required');
  for (const path of ['/open', '/account/remove']) {
    assert.equal((await h.post(`${URL}${path}`, {}, { token: s.t })).body.error.code, 'hesabpay_pin_required', path);
  }
  assert.equal((await h.put(`${URL}/account`, { name: NAME, mode: 'simple', account: '0799123456' }, { token: s.t })).body.error.code, 'hesabpay_pin_required');
  pushes.length = 0;
  for (let i = 1; i <= 4; i++) {
    const r = await h.post(`${URL}/open`, { pin: '000001' }, { token: s.t });
    assert.equal(r.status, 403);
    assert.deepEqual([r.body.error.code, r.body.error.attemptsLeft], ['hesabpay_pin_wrong', 5 - i]);
  }
  const fifth = await h.put(`${URL}/account`, { pin: '000001', name: NAME, mode: 'simple', account: '0799123456' }, { token: s.t });
  assert.equal(fifth.status, 423);
  assert.equal(fifth.body.error.code, 'hesabpay_pin_locked');
  assert.ok(fifth.body.error.retryAfter > 0 && fifth.body.error.retryAfter <= 15 * 60);
  //  قفل: رمزِ درست هم پذیرفته نمی‌شود — هیچ کاری
  for (const [m, path, body] of [['post', '/open', { pin: PIN }], ['put', '/account', { pin: PIN, name: NAME, mode: 'simple', account: '0799123456' }],
    ['put', '/pin', { pin: PIN, newPin: '975310' }], ['post', '/account/remove', { pin: PIN }]]) {
    const r = await h[m](`${URL}${path}`, body, { token: s.t });
    assert.equal(r.body.error.code, 'hesabpay_pin_locked', path);
  }
  assert.equal((await h.get(URL, { token: s.t })).body.locked, true);
  assert.equal((await one('SELECT mode FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).mode, 'off');
  const audits = await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'hesabpay.%' ORDER BY created_at`, [s.shopId]);
  assert.deepEqual(audits.map((a) => a.action), ['hesabpay.pin_set', 'hesabpay.pin_wrong', 'hesabpay.pin_wrong', 'hesabpay.pin_wrong', 'hesabpay.pin_wrong', 'hesabpay.pin_locked']);
  assert.ok(pushes.some((p) => p.shopId === s.shopId && p.userId === s.owner.user.id && p.message.data.kind === 'pin_locked'), 'خبرِ قفل به صاحب');
  //  پایانِ قفل ⇒ رمزِ درست دوباره کار می‌کند و شمار صفر می‌شود؛ ۵ خطای بعدی قفلِ دوبرابر
  await query('UPDATE shop_hesabpay SET pin_locked_until=$2 WHERE shop_id=$1', [s.shopId, now() - 1]);
  const ok = await h.post(`${URL}/open`, { pin: PIN }, { token: s.t });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await one('SELECT pin_failures FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).pin_failures, 0);
  await query('UPDATE shop_hesabpay SET pin_failures=9 WHERE shop_id=$1', [s.shopId]);
  const second = await h.post(`${URL}/open`, { pin: '000001' }, { token: s.t });
  assert.equal(second.status, 423);
  assert.ok(second.body.error.retryAfter > 15 * 60 && second.body.error.retryAfter <= 30 * 60, String(second.body.error.retryAfter));
});

test('عوض کردنِ رمز با رمزِ فعلی؛ رمزِ کهنه دیگر کار نمی‌کند؛ هش هرگز در پاسخ یا سابقه', async () => {
  const s = await shop('رمزِ-تازه');
  await setPin(s);
  assert.equal((await setPin(s, { pin: '000001', newPin: '975310' })).body.error.code, 'hesabpay_pin_wrong');
  const r = await setPin(s, { pin: PIN, newPin: '975310' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await h.post(`${URL}/open`, { pin: PIN }, { token: s.t })).body.error.code, 'hesabpay_pin_wrong');
  assert.equal((await h.post(`${URL}/open`, { pin: '975310' }, { token: s.t })).status, 200);
  const row = await one('SELECT pin_hash FROM shop_hesabpay WHERE shop_id=$1', [s.shopId]);
  assert.match(row.pin_hash, /^pbkdf2-sha256\$\d+\$/);
  const logs = JSON.stringify(await many('SELECT detail FROM audit_logs WHERE shop_id=$1', [s.shopId]));
  assert.ok(!logs.includes(row.pin_hash.split('$')[3]) && !logs.includes('975310'));
  assert.ok(pushes.some((p) => p.shopId === s.shopId && p.message.data.kind === 'pin_changed'));
});

test('حالتِ ساده: شمارهٔ یک‌دست برای صندوق؛ هیچ قصدِ سروری (تأیید فقط با «پول رسید» ِ صندوقدار)', async () => {
  const s = await shop('ساده');
  await setPin(s);
  assert.equal((await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple', account: '0699' }, { token: s.t })).body.error.code, 'hesabpay_bad_account');
  assert.equal((await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple' }, { token: s.t })).body.error.code, 'hesabpay_account_required');
  assert.equal((await h.put(`${URL}/account`, { pin: PIN, mode: 'x' }, { token: s.t })).body.error.code, 'hesabpay_bad_mode');
  //  خطای فرم تلاشِ رمز را نمی‌سوزاند
  assert.equal((await one('SELECT pin_failures FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).pin_failures, 0);
  const r = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple', account: '+93 ۷۹۹ ۱۲۳ ۴۵۶', qr: 'hesabpay://pay?to=0799123456' }, { token: s.t });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.mode, r.body.connected, r.body.account, r.body.auto], ['simple', true, '0799123456', null]);
  //  صندوقِ کارمند همان شماره و QR را می‌گیرد؛ درگاهِ خودکار نه
  const cashier = await member(s, 'staff');
  const cfg = (await h.get('/api/v1/shop/payments/config', { token: cashier })).body;
  const fp1 = (await one('SELECT payee_fp FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).payee_fp;
  assert.match(fp1, /^hp1_[0-9a-f]{32}$/);
  assert.deepEqual(cfg, { hesabpay: { enabled: false, flag: true, mode: 'simple', simple: { account: '0799123456', qrPayload: 'hesabpay://pay?to=0799123456' },
    payee: { name: NAME, last4: '3456', fp: fp1 }, payees: [{ fp: fp1, name: NAME, last4: '3456', active: true }] } });
  const c = await createPos(s);
  assert.equal(c.body.error.code, 'hesabpay_disabled', 'حالتِ ساده هیچ قصد/تأییدِ سروری ندارد');
  //  فقط QR (شماره از خودِ QR) — حسابِ دوم ⇒ ۲۴ ساعت «در انتظار»؛ تا آن دم صندوق همان حسابِ نخست
  const q = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple', qr: 'HP|+93788000111|shop' }, { token: s.t });
  assert.equal(q.status, 200, JSON.stringify(q.body));
  assert.deepEqual([q.body.account, q.body.pending.last4], ['0799123456', '0111']);
  assert.equal((await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay.simple.account, '0799123456');
  await query('UPDATE shop_hesabpay SET pending_until=$2 WHERE shop_id=$1', [s.shopId, now() - 1]);
  assert.equal((await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay.simple.account, '0788000111');
  const set = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.account_set' ORDER BY created_at DESC LIMIT 1`, [s.shopId]);
  assert.deepEqual([set.detail.mode, set.detail.last4, set.detail.immediate], ['simple', '3456', true]);
  const pend = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.change_pending' ORDER BY created_at DESC LIMIT 1`, [s.shopId]);
  assert.deepEqual([pend.detail.mode, pend.detail.last4], ['simple', '0111']);
  assert.ok(pushes.some((p) => p.shopId === s.shopId && p.message.data.kind === 'account_changed' && /اگر شما نبودید/.test(p.message.body)));
  assert.ok(pushes.some((p) => p.shopId === s.shopId && p.message.data.kind === 'change_pending' && /این من نبودم/.test(p.message.body)));
});

test('حالتِ خودکار: کلید رمزشده و فقط‌نوشتنی (پاسخ، ردیف، سابقه، صندوق)؛ بی کلیدِ رمزگذاری ⇒ ۵۰۳ ِ روشن', async () => {
  const s = await shop('خودکار');
  await setPin(s);
  try {
    delete process.env.HESABPAY_CRED_KEY;
    const r = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'auto', apiKey: 'SHOPKEY-aaaa-1111', webhookSecret: 'whsec-shop-aaaa', merchantId: 'MERCH-77-0042' }, { token: s.t });
    assert.equal(r.status, 503);
    assert.equal(r.body.error.code, 'hesabpay_cred_key_missing');
    assert.match(r.body.error.message, /HESABPAY_CRED_KEY/);
  } finally { process.env.HESABPAY_CRED_KEY = CRED_KEY; }
  assert.equal((await one('SELECT mode FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).mode, 'off');
  const r = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'auto', apiKey: 'SHOPKEY-aaaa-1111', webhookSecret: 'whsec-shop-aaaa', merchantId: 'MERCH-77-0042' }, { token: s.t });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.auto.keyLast4, r.body.auto.merchantLast4, r.body.payee.name, r.body.payee.last4], ['1111', '0042', NAME, '0042']);
  assert.match(r.body.auto.webhookUrl, /\/api\/v1\/payments\/webhook\/hesabpay\/[A-Za-z0-9_-]{32}$/);
  const opened = await h.post(`${URL}/open`, { pin: PIN }, { token: s.t });
  const cashier = await member(s, 'staff');
  const cfg = await h.get('/api/v1/shop/payments/config', { token: cashier });
  const row = await one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [s.shopId]);
  assert.equal(cfg.body.hesabpay.payee.fp, row.payee_fp);
  assert.deepEqual(cfg.body, { hesabpay: { enabled: true, flag: true, mode: 'auto', payee: { name: NAME, last4: '0042', fp: row.payee_fp },
    payees: [{ fp: row.payee_fp, name: NAME, last4: '0042', active: true }] } });
  const logs = await many('SELECT detail FROM audit_logs WHERE shop_id=$1', [s.shopId]);
  for (const blob of [r.body, opened.body, (await h.get(URL, { token: s.t })).body, cfg.body, row, logs].map((x) => JSON.stringify(x))) {
    //  شناسهٔ پذیرنده هم فقط‌نوشتنی است (فقط ۴ نویسهٔ آخر «0042» دیده می‌شود)
    for (const secret of ['SHOPKEY-aaaa', 'whsec-shop-aaaa', 'MERCH-77']) assert.ok(!blob.includes(secret), `${secret} در ${blob.slice(0, 80)}`);
  }
  assert.ok(row.api_key_enc.startsWith('v1.') && row.secret_enc.startsWith('v1.') && row.merchant_enc.startsWith('v1.'));
});

test('پول به حسابِ همان دکان: قصد و تأییدِ دوم با کلیدِ خودِ دکان؛ کلیدِ سکو هرگز؛ دکانِ بی‌حساب خاموش', async () => {
  const a = await shop('الفِ-پول');
  const b = await shop('بِ-پول');
  const none = await shop('بی‌حساب');
  await auto(a, { apiKey: 'KEY-A-0001', webhookSecret: 'whsec-AAAA' });
  await auto(b, { apiKey: 'KEY-B-0002', webhookSecret: 'whsec-BBBB' });
  seen.length = 0;
  const ia = await createPos(a);
  const ib = await createPos(b);
  assert.equal(ia.status, 201, JSON.stringify(ia.body));
  assert.equal(ib.status, 201, JSON.stringify(ib.body));
  const made = seen.filter((x) => x.url === '/api/v1/payment/create-session');
  assert.deepEqual(made.map((x) => x.auth), ['API-KEY KEY-A-0001', 'API-KEY KEY-B-0002']);
  assert.equal(made[0].body.reference, ia.body.id);
  //  دکانِ بی‌حساب: خاموش؛ هیچ درخواستی (نه با کلیدِ سکو)
  assert.deepEqual((await h.get('/api/v1/shop/payments/config', { token: none.t })).body, { hesabpay: { enabled: false, flag: true, mode: 'off' } });
  assert.equal((await createPos(none)).body.error.code, 'hesabpay_disabled');
  assert.ok(!seen.some((x) => x.auth.includes('PLATFORM') || JSON.stringify(x.body || {}).includes('PLATFORM')), '⛔ کلیدِ سکو هرگز');
  assert.equal(await pay.gatewayFor(none.shopId), null);
  //  تطبیق هم با کلیدِ همان دکان
  providerStatus.set((await intentRow(ia.body.id)).provider_session_id, { status: 'completed', amount: '1450.00', transaction_id: 'TX-A', merchant_id: a.merchant });
  seen.length = 0;
  const out = await pay.reconcile({ sinceMs: 60 * 60 * 1000 });
  assert.ok(out.paid >= 1, JSON.stringify(out));
  assert.equal((await intentRow(ia.body.id)).status, 'paid');
  assert.equal((await intentRow(ib.body.id)).status, 'pending');
  //  #853 در صندوق: نمای قصد و ردیفِ پرداختِ آماده گیرندهٔ قصد را می‌گویند — صندوقِ گوشی فقط با گیرندهٔ فعال خودکار می‌بندد
  const fpA = (await one('SELECT payee_fp FROM shop_hesabpay WHERE shop_id=$1', [a.shopId])).payee_fp;
  assert.equal(ia.body.payeeFp, fpA);
  const paidView = (await h.get(`/api/v1/shop/payments/intents/${ia.body.id}`, { token: a.t })).body;
  assert.deepEqual([paidView.status, paidView.payeeFp, paidView.payment.payee, paidView.payment.verification], ['paid', fpA, fpA, 'gateway']);
  const asked = seen.filter((x) => x.url.startsWith('/api/v1/payment/status/'));
  assert.ok(asked.length >= 2 && !asked.some((x) => x.auth.includes('PLATFORM')), JSON.stringify(asked));
  const statusFor = (sid) => asked.find((x) => x.url === `/api/v1/payment/status/${encodeURIComponent(sid)}`);
  assert.equal(statusFor((await intentRow(ia.body.id)).provider_session_id).auth, 'API-KEY KEY-A-0001');
  assert.equal(statusFor((await intentRow(ib.body.id)).provider_session_id).auth, 'API-KEY KEY-B-0002');
});

test('وب‌هوک: رازِ دکانِ دیگر ⇒ ۴۰۱؛ رویدادِ دکانِ دیگر قصدِ این دکان را نمی‌بندد؛ تکرار یک‌بار؛ رازِ سکو هرگز', async () => {
  const a = await shop('الفِ-وب‌هوک');
  const b = await shop('بِ-وب‌هوک');
  const ta = await auto(a, { apiKey: 'KEY-WA-0001', webhookSecret: 'whsec-WA' });
  const tb = await auto(b, { apiKey: 'KEY-WB-0002', webhookSecret: 'whsec-WB' });
  const ia = (await createPos(a)).body;
  const sid = (await intentRow(ia.id)).provider_session_id;
  providerStatus.set(sid, { status: 'completed', amount: '1450.00', transaction_id: 'TX-WA', merchant_id: a.merchant });
  const ev = { event_id: 'HE-SAME-1', session_id: sid, reference: ia.id, status: 'success', amount: '1450.00', transaction_id: 'TX-WA' };
  //  ۱) نشانیِ «الف» با رازِ «ب» / رازِ سکو ⇒ ۴۰۱ و هیچ تغییری
  assert.equal((await hook(hookPath(ta), ev, 'whsec-WB')).status, 401);
  assert.equal((await hook(hookPath(ta), ev, 'PLATFORM-SECRET-never-use')).status, 401);
  assert.equal((await hook('/api/v1/payments/webhook/hesabpay', ev, 'PLATFORM-SECRET-never-use')).status, 401);
  assert.equal((await hook('/api/v1/payments/webhook/hesabpay', ev, 'whsec-WB')).status, 401, 'درِ بی‌دکان هم رازِ دکانِ قصد را می‌خواهد');
  //  ۲) نشانیِ «ب» با رازِ درستِ «ب» ولی قصدِ «الف» ⇒ قصدِ ناشناخته (درونِ «ب»)، «الف» دست نمی‌خورد
  const cross = await hook(hookPath(tb), { ...ev, event_id: 'HE-CROSS-1' }, 'whsec-WB');
  assert.equal(cross.status, 200, JSON.stringify(cross.body));
  assert.equal(cross.body.ignored, 'unknown_intent');
  assert.equal((await intentRow(ia.id)).status, 'pending');
  //  نشانیِ ناشناخته ⇒ ۴۰۱
  assert.equal((await hook(hookPath('x'.repeat(32)), ev, 'whsec-WA')).status, 401);
  assert.equal((await hook(hookPath('short'), ev, 'whsec-WA')).status, 401);
  //  ۳) درست: نشانی و رازِ «الف» ⇒ paid؛ همان رویداد دوباره ⇒ تکراری
  const ok = await hook(hookPath(ta), ev, 'whsec-WA');
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.outcome, 'applied');
  assert.equal((await intentRow(ia.id)).status, 'paid');
  const again = await hook(hookPath(ta), ev, 'whsec-WA');
  assert.deepEqual(again.body, { ok: true, duplicate: true });
  const evRows = await many(`SELECT provider_event_id, intent_id FROM payment_events WHERE intent_id=$1`, [ia.id]);
  assert.equal(evRows.length, 1);
  assert.equal(evRows[0].provider_event_id, `hesabpay:${a.shopId}:HE-SAME-1`);
  //  ۴) همان شناسهٔ رویداد از پذیرندهٔ دکانِ «ب» برای قصدِ خودش «تکراری» شمرده نمی‌شود
  const ib = (await createPos(b)).body;
  const sidB = (await intentRow(ib.id)).provider_session_id;
  providerStatus.set(sidB, { status: 'completed', amount: '1450.00', transaction_id: 'TX-WB', merchant_id: b.merchant });
  const okB = await hook(hookPath(tb), { ...ev, session_id: sidB, reference: ib.id, transaction_id: 'TX-WB' }, 'whsec-WB');
  assert.equal(okB.body.outcome, 'applied', JSON.stringify(okB.body));
  assert.equal((await intentRow(ib.id)).status, 'paid');
  //  ۵) درِ کهنهٔ بی‌دکان با رازِ همان دکانِ قصد ⇒ کار می‌کند (و تکرارش تکراری)
  const ia2 = (await createPos(a)).body;
  const sid2 = (await intentRow(ia2.id)).provider_session_id;
  providerStatus.set(sid2, { status: 'completed', amount: '1450.00', transaction_id: 'TX-WA2', merchant_id: a.merchant });
  const legacy = await hook('/api/v1/payments/webhook/hesabpay', { ...ev, event_id: 'HE-2', session_id: sid2, reference: ia2.id }, 'whsec-WA');
  assert.equal(legacy.body.outcome, 'applied', JSON.stringify(legacy.body));
  //  رازی در رویدادِ ثبت‌شده نیست
  assert.ok(!JSON.stringify(await many('SELECT raw FROM payment_events')).match(/whsec|KEY-W/));
});

test('«فقط مبلغ برابر» هرگز تأیید نمی‌کند: رویدادِ امضاشده بی مرجع/جلسهٔ درست ⇒ ناشناخته؛ مبلغِ دیگر ⇒ اختلاف', async () => {
  const s = await shop('فقط-مبلغ');
  const tk = await auto(s, { apiKey: 'KEY-AM-0001', webhookSecret: 'whsec-AM' });
  const i = (await createPos(s)).body;
  const sid = (await intentRow(i.id)).provider_session_id;
  providerStatus.set(sid, { status: 'completed', amount: '1450.00', transaction_id: 'TX-AM', merchant_id: s.merchant });
  //  همان مبلغ، بی مرجع و بی جلسه (مثلِ پیامکِ «۱۴۵۰ افغانی رسید»)
  const r1 = await hook(hookPath(tk), { event_id: 'AM-1', status: 'success', amount: '1450.00', transaction_id: 'TX-X' }, 'whsec-AM');
  assert.equal(r1.body.ignored, 'unknown_intent');
  //  همان مبلغ با مرجعِ ساختگی
  const r2 = await hook(hookPath(tk), { event_id: 'AM-2', reference: 'pi_not_real', status: 'success', amount: '1450.00' }, 'whsec-AM');
  assert.equal(r2.body.ignored, 'unknown_intent');
  assert.equal((await intentRow(i.id)).status, 'pending');
  //  مرجعِ درست با مبلغِ دیگر ⇒ اختلاف و بررسی، نه پرداخت
  const r3 = await hook(hookPath(tk), { event_id: 'AM-3', session_id: sid, reference: i.id, status: 'success', amount: '1000.00' }, 'whsec-AM');
  assert.equal(r3.body.outcome, 'mismatch');
  const row = await intentRow(i.id);
  assert.deepEqual([row.status, row.needs_review], ['pending', true]);
});

test('دکانِ دیگر: حال، نما و کلیدِ «الف» را نمی‌بیند و به کار نمی‌برد', async () => {
  const a = await shop('الفِ-جدا');
  const b = await shop('بِ-جدا');
  await auto(a, { apiKey: 'KEY-ISO-A-9999', webhookSecret: 'whsec-ISO-A' });
  const before = await one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [a.shopId]);
  const sb = (await h.get(URL, { token: b.t })).body;
  assert.deepEqual([sb.pinSet, sb.mode], [false, 'off']);
  //  حتی با `shopId` ِ «الف» در بدنه و نشانی
  assert.equal((await h.post(`${URL}/open?shopId=${a.shopId}`, { pin: PIN, shopId: a.shopId }, { token: b.t })).body.error.code, 'hesabpay_pin_not_set');
  await setPin(b);
  const ob = await h.post(`${URL}/open`, { pin: PIN, shopId: a.shopId }, { token: b.t });
  assert.equal(ob.body.mode, 'off');
  /*
   *  «ب» هیچ نشانی از «الف» نمی‌بیند: نه چهار رقمِ آخرِ کلید («9999»)، نه کلید و راز، نه نشانیِ وب‌هوک و شناسهٔ «الف».
   *  ⛔ لرزان بود (~۰٫۰۶٪): «9999» در کلِ متن به `device.trustedAfter` ِ خودِ «ب» (زمانِ ms ِ نشست + ۷ روز) هم می‌خورد.
   *  تنها عددِ زمانیِ پاسخ اول با مقدارِ دقیقش (session_at ِ توکنِ «ب» + TRUST_MS) سنجیده و بعد از متن برداشته می‌شود؛
   *  بقیهٔ متن مثلِ پیش و سخت‌تر سنجیده می‌شود. بازتولید: ساعتی که «اکنون + ۷ روز» را در 17999… بگذارد.
   */
  assert.deepEqual([ob.body.lockedUntil, ob.body.holdUntil, ob.body.sectionLockedUntil], [null, null, null]);
  //  جاهایی که کلید/حسابِ دکان در نما می‌نشیند برای «ب» تهی‌اند
  assert.deepEqual([ob.body.auto, ob.body.payee, ob.body.pending, ob.body.account, ob.body.qrPayload], [null, null, null, '', '']);
  const sessB = await one('SELECT session_at FROM tokens WHERE token_hash=$1', [tokens.hashToken(b.t)]);
  assert.equal(ob.body.device.trustedAfter, Number(sessB.session_at) + guard.TRUST_MS, 'زمانِ نشستِ خودِ «ب»');
  const rowA = await one('SELECT key_last4, webhook_token FROM shop_hesabpay WHERE shop_id=$1', [a.shopId]);
  assert.equal(rowA.key_last4, '9999');
  assert.match(rowA.webhook_token, /^[\w-]{32}$/);
  const viewB = JSON.stringify({ ...ob.body, device: { ...ob.body.device, trustedAfter: '<B>' } });
  for (const leak of [rowA.key_last4, 'KEY-ISO-A', 'whsec-ISO-A', rowA.webhook_token, a.shopId]) {
    assert.ok(!viewB.includes(leak), `بی ${leak}: ${viewB}`);
  }
  assert.equal((await createPos(b)).body.error.code, 'hesabpay_disabled', '«ب» با کلیدِ «الف» قصد نمی‌سازد');
  assert.deepEqual(await one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [a.shopId]), before);
});

test('پرداختِ خودکارِ باز ⇒ حساب عوض/برداشته نمی‌شود؛ برداشتن با رمز و سابقه؛ بازنشانیِ رمز ⇒ حساب پاک و ۲۴ ساعت نگه', async () => {
  const s = await shop('بازنشانی');
  await auto(s, { apiKey: 'KEY-RS-0001', webhookSecret: 'whsec-RS' });
  const i = (await createPos(s)).body;
  const change = await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple', account: '0799123456' }, { token: s.t });
  assert.equal(change.body.error.code, 'hesabpay_payments_open');
  assert.equal((await h.post(`${URL}/account/remove`, { pin: PIN }, { token: s.t })).body.error.code, 'hesabpay_payments_open');
  assert.equal((await h.post(`/api/v1/shop/payments/intents/${i.id}/cancel`, {}, { token: s.t })).status, 200);
  const rm = await h.post(`${URL}/account/remove`, { pin: PIN }, { token: s.t });
  assert.equal(rm.status, 200, JSON.stringify(rm.body));
  assert.deepEqual([rm.body.mode, rm.body.connected], ['off', false]);
  const row = await one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [s.shopId]);
  assert.deepEqual([row.api_key_enc, row.secret_enc, row.key_last4], ['', '', '']);
  assert.ok(row.pin_hash, 'رمز می‌ماند');
  //  بازنشانی: رمز و حساب پاک، ۲۴ ساعت حسابِ تازه نه (رمزِ تازه بله)
  await h.put(`${URL}/account`, { pin: PIN, name: NAME, mode: 'simple', account: '0799123456' }, { token: s.t });
  pushes.length = 0;
  const reset = await h.post(`${URL}/pin/reset`, {}, { token: s.t });
  assert.equal(reset.status, 200, JSON.stringify(reset.body));
  assert.deepEqual([reset.body.pinSet, reset.body.mode], [false, 'off']);
  assert.ok(reset.body.holdUntil > now() + 23 * 3600 * 1000);
  assert.ok(pushes.some((p) => p.message.data.kind === 'pin_reset' && p.userId === s.owner.user.id));
  assert.equal((await setPin(s, { newPin: '975310' })).status, 200);
  const held = await h.put(`${URL}/account`, { pin: '975310', name: NAME, mode: 'simple', account: '0799123456' }, { token: s.t });
  assert.equal(held.status, 423);
  assert.equal(held.body.error.code, 'hesabpay_on_hold');
  const actions = (await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'hesabpay.%'`, [s.shopId])).map((x) => x.action);
  for (const want of ['hesabpay.account_set', 'hesabpay.account_removed', 'hesabpay.pin_reset', 'hesabpay.pin_set']) assert.ok(actions.includes(want), want);
});
