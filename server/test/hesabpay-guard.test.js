'use strict';
/**
 * نگهبانِ حسابِ گیرندهٔ حساب‌پی (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱: «هیچ‌کس — شاگرد یا دزدِ گوشی — نتواند حساب یا QR ِ
 * خودش را جای حسابِ دکان بگذارد یا چیزی بدزدد»؛ PAY-21، PAY-06، مهاجرتِ ۲۰۵) — `lib/hesabpay-guard.js`،
 * `lib/hesabpay-account.js`، `routes/shop-hesabpay.js`، `lib/payment-gateway.payeeHeld` و `payment-hesabpay.forShop`.
 *
 * هر ویژگیِ امنیتی یک آزمون (سرخ پیش از این تغییر، سبز پس از آن):
 *   ۱. شاگرد/مدیر: هیچ درِ حساب‌پی (حتی با رمزِ حساب‌پیِ درست)، نه لغو، نه گزارش
 *   ۲. حسابِ دوم ۲۴ ساعت «در انتظار»؛ پول تا آن دم به حسابِ کهنه؛ پوش؛ فعال شدن با سابقه و تاریخچه
 *   ۳. «این من نبودم — لغو کن»: حسابِ تازه پاک، بخش ۲۴ ساعت بسته، صندوق همان حسابِ کهنه
 *   ۴. دزدِ گوشی / رمزِ ورودِ دزدیده: دستگاهِ تازه رد پیش از رمز (بی سوختنِ تلاش)؛ گوشیِ مطمئن بی تأییدِ دوبارهٔ ورود رد
 *   ۵. QR ِ کسِ دیگر کنارِ شمارهٔ دکان، QR ِ بی شماره، عکسِ QR ⇒ رد
 *   ۶. «خودکار»: وب‌هوکِ امضاشدهٔ درست با پذیرندهٔ دیگر/بی پذیرنده/اثرِ انگشتِ کهنه ⇒ «نیازمندِ بررسی»، هرگز «پرداخت شد»
 *   ۷. دکانِ دیگر: لغو و گزارش و حسابِ در انتظارِ «الف» را نمی‌بیند و دست نمی‌زند
 *   ۸. گزارشِ «پول به کدام حساب رفت» (مسیر و گزارشِ خودکارِ صاحب) بی شماره و کلید؛ راز در لاگ نه
 *   ۹. رمزِ ورودِ دزدیده + `device_uid` ِ دانستهٔ گوشیِ صاحب ⇒ نشستِ تازه ⇒ نامطمئن (اعتماد به زنجیرهٔ توکنِ سرور، نه شناسهٔ برنامه)
 *  ۱۰. «پول رسید» ِ صندوق با گیرنده‌ای جز حساب‌های همین دکان (همگام‌سازیِ v1) ⇒ هشدار و پوشِ فوری به صاحب، یک بار
 */
process.env.PAYMENTS_PROVIDER = '';
process.env.HESABPAY_PIN_ITER = '1000';
process.env.RATE_PAY_CREATE_MAX = '100000';
process.env.RATE_PAY_POLL_MAX = '100000';
process.env.RATE_HESABPAY_ACCOUNT_MAX = '100000';
process.env.HESABPAY_CRED_KEY = 'cred-key-for-hesabpay-guard-test-0123456789abc';

const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const h = require('./helpers');
const { one, query, many, newId, now } = require('../src/db');
const pay = require('../src/lib/payment-intents');
const push = require('../src/lib/push');
const guard = require('../src/lib/hesabpay-guard');
const report = require('../src/lib/owner-report');
const { hashToken } = require('../src/lib/tokens');

const PIN = '258014';
const URL = '/api/v1/shop/hesabpay';
const DAY = 24 * 60 * 60 * 1000;
let admin = '';
let srv;
const providerStatus = new Map();
let sessionSeq = 0;
const pushes = [];

test.before(async () => {
  await h.start();
  srv = http.createServer((req, res) => {
    let b = '';
    req.on('data', (d) => { b += d; });
    req.on('end', () => {
      res.setHeader('Content-Type', 'application/json');
      if (req.method === 'POST' && req.url === '/api/v1/payment/create-session') {
        const sid = `HG-${++sessionSeq}`;
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
     VALUES ($1,'hp-guard-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'hp-guard-admin', password: 'Admin!12345' })).body.token;
  push.setDeliver((row, message) => { pushes.push({ userId: row.user_id, shopId: row.shop_id, deviceUid: row.device_uid, message }); });
});
test.after(async () => {
  push.setDeliver(null);
  await new Promise((r) => srv.close(r));
  await h.stop();
});

let seq = 0;
async function shop(label) {
  const owner = await h.newUser(`صاحب-${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const f = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'pay.hesabpay', value: true }, { token: admin });
  assert.equal(f.status, 200, JSON.stringify(f.body));
  //  دو دستگاهِ صاحب برای پوش (گوشی و کمپیوتر)
  for (const d of ['phone', 'desk']) {
    await push.register({ app: 'shop', token: `push-${d}-${shopId}`, userId: owner.user.id, shopId, deviceUid: `${d}-${shopId}` });
  }
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

const simple = (account, extra = {}) => ({ pin: PIN, name: 'دکانِ احمد', mode: 'simple', account, ...extra });
const save = (s, body, token = s.t) => h.put(`${URL}/account`, body, { token });
const cancel = (s, token = s.t) => h.post(`${URL}/pending/cancel`, {}, { token });
const till = async (s) => (await h.get('/api/v1/shop/payments/config', { token: s.t })).body.hesabpay;
const rowOf = (s) => one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [s.shopId]);
const actions = async (s) => (await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE 'hesabpay.%' ORDER BY created_at`, [s.shopId])).map((a) => a.action);
const kinds = (s) => pushes.filter((p) => p.shopId === s.shopId).map((p) => p.message.data.kind);
/** ۲۴ ساعت گذشت (فقط زمانِ حسابِ در انتظار؛ ساعتِ سرور دست نمی‌خورد) */
const elapse = (s) => query('UPDATE shop_hesabpay SET pending_until=$2 WHERE shop_id=$1 AND pending_until > 0', [s.shopId, now() - 1]);
/** نشستِ این توکن دیگر «تازه» نیست (ده دقیقه از ورود گذشت) ⇒ کارِ حساس `reauth_required` */
const stale = (token) => query(`DELETE FROM login_attempts WHERE scope='session_auth' AND identifier=$1`, [hashToken(token)]);
/** گوشیِ همیشگیِ صاحب: دستگاه‌ها و زنجیره‌های نشستِ امروزش ۸ روزه می‌شوند (ورود ۸ روز پیش، از آن پس فقط تازه‌سازی) */
async function ageDevices(s, days = 8) {
  await query('UPDATE devices SET created_at=$2 WHERE user_id=$1', [s.owner.user.id, now() - days * DAY]);
  await query('UPDATE tokens SET session_at=$2 WHERE subject_id=$1', [s.owner.user.id, now() - days * DAY]);
}
/** همان صاحب (یا کسی که رمزِ ورودش را دارد) روی دستگاهی تازه — یا با `device_uid` ِ دستگاهی دیگر — وارد می‌شود */
async function newDevice(s, id) {
  const r = await h.post('/api/auth/login', {
    identifier: s.owner.email, password: s.owner.password, device: { deviceId: id, name: 'گوشیِ تازه', platform: 'test' },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.accessToken;
}
async function ready(s, account = '0799111222') {
  assert.equal((await h.put(`${URL}/pin`, { newPin: PIN }, { token: s.t })).status, 200);
  const r = await save(s, simple(account));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}

// ════════════════════════════════════════════════════════════════════

test('۱. شاگرد و مدیر — حتی با رمزِ حساب‌پیِ درست — نه حساب می‌گذارند، نه لغو، نه گزارش؛ حسابِ دکان دست نمی‌خورد', async () => {
  const s = await shop('شاگرد');
  await ready(s);
  const before = await rowOf(s);
  for (const role of ['staff', 'cashier', 'manager']) {
    const tk = await member(s, role);
    for (const [m, path, body] of [['put', '/account', simple('0700999888')], ['post', '/pending/cancel', {}], ['get', '/payees', null],
      ['put', '/pin', { pin: PIN, newPin: '975310' }], ['post', '/pin/reset', {}], ['post', '/account/remove', { pin: PIN }], ['post', '/open', { pin: PIN }]]) {
      const r = m === 'get' ? await h.get(`${URL}${path}`, { token: tk }) : await h[m](`${URL}${path}`, body, { token: tk });
      assert.deepEqual([r.status, r.body.error && r.body.error.code], [403, 'owner_only'], `${role} ${path}`);
    }
  }
  assert.deepEqual(await rowOf(s), before, 'هیچ ستونی عوض نشد');
  assert.equal((await till(s)).simple.account, '0799111222');
});

test('۲. حسابِ دوم ۲۴ ساعت در انتظار: صندوق و درگاه حسابِ کهنه؛ پوش به همهٔ دستگاه‌های صاحب؛ سپس فعال با سابقه و تاریخچه', async () => {
  const s = await shop('در-انتظار');
  const first = await ready(s, '0799111222');
  assert.equal(first.pending, null, 'نخستین حساب فوری');
  assert.equal(first.payee.last4, '1222');
  pushes.length = 0;
  const r = await save(s, simple('0788333444', { name: 'حسابِ تازه' }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.account, '0799111222', 'نمای صاحب: حسابِ فعال هنوز کهنه');
  assert.deepEqual([r.body.pending.name, r.body.pending.last4], ['حسابِ تازه', '3444']);
  assert.ok(r.body.pending.until >= now() + DAY - 60_000);
  //  صندوق (کارمند) هیچ چیزِ حسابِ تازه را نمی‌بیند
  const cashier = await member(s, 'staff');
  const cfg = (await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay;
  assert.deepEqual([cfg.simple.account, cfg.payee.last4, cfg.payee.name], ['0799111222', '1222', 'دکانِ احمد']);
  assert.ok(!JSON.stringify(cfg).includes('0788333444'));
  //  پوشِ «۲۴ ساعتِ دیگر عوض می‌شود» به هر دو دستگاهِ صاحب
  const pend = pushes.filter((p) => p.shopId === s.shopId && p.message.data.kind === 'change_pending');
  assert.deepEqual(pend.map((p) => p.deviceUid).sort(), [`desk-${s.shopId}`, `phone-${s.shopId}`]);
  assert.ok(pend.every((p) => p.userId === s.owner.user.id && /این من نبودم/.test(p.message.body)));
  //  هنوز زمانش نرسیده ⇒ با هر خواندن هم فعال نمی‌شود
  assert.equal(await guard.activateDue(s.shopId), false);
  assert.equal((await till(s)).simple.account, '0799111222');
  //  ۲۴ ساعت گذشت ⇒ نخستین خواندن فعالش می‌کند (یک بار)
  await elapse(s);
  assert.equal((await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay.simple.account, '0788333444');
  assert.equal(await guard.activateDue(s.shopId), false, 'دوباره نه');
  const st = (await h.get(URL, { token: s.t })).body;
  assert.deepEqual([st.pending, st.payee.name, st.payee.last4], [null, 'حسابِ تازه', '3444']);
  assert.deepEqual(await actions(s), ['hesabpay.pin_set', 'hesabpay.account_set', 'hesabpay.change_pending', 'hesabpay.change_activated']);
  assert.ok(kinds(s).includes('change_activated'));
  const hist = await many('SELECT last4, retired_at FROM hesabpay_payees WHERE shop_id=$1 ORDER BY active_from', [s.shopId]);
  assert.deepEqual(hist.map((x) => [x.last4, x.retired_at === null]), [['1222', false], ['3444', true]]);
});

test('۳. «این من نبودم — لغو کن»: تأییدِ دوبارهٔ ورود، حسابِ تازه پاک، بخش ۲۴ ساعت بسته، پول همان حسابِ کهنه', async () => {
  const s = await shop('لغو');
  await ready(s, '0799111222');
  assert.equal((await cancel(s)).body.error.code, 'hesabpay_nothing_pending');
  assert.equal((await save(s, simple('0788333444'))).status, 200);
  //  نشستِ کهنه ⇒ اول تأییدِ دوبارهٔ ورود (پنجرهٔ «رمز را دوباره بزنید» ِ برنامه)
  const fresh = await newDevice(s, 'owner-desk');
  await stale(fresh);
  assert.equal((await cancel(s, fresh)).body.error.code, 'reauth_required');
  assert.ok((await rowOf(s)).pending, 'بی تأییدِ دوباره هیچ تغییری');
  //  از هر دستگاهِ صاحب (حتی تازه) و بی رمزِ حساب‌پی
  const other = await newDevice(s, 'owner-tablet');
  pushes.length = 0;
  const r = await cancel(s, other);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.pending, null);
  assert.ok(r.body.sectionLockedUntil >= now() + DAY - 60_000);
  const row = await rowOf(s);
  assert.deepEqual([row.pending, Number(row.pending_until), row.account], [null, 0, '0799111222']);
  await elapse(s);
  assert.equal((await till(s)).simple.account, '0799111222', 'حسابِ تازه هرگز فعال نشد');
  //  بخش بسته: هیچ تغییری — حتی با رمزِ درست و از دستگاهِ مطمئن
  for (const [m, path, body] of [['put', '/account', simple('0788333444')], ['post', '/account/remove', { pin: PIN }],
    ['put', '/pin', { pin: PIN, newPin: '975310' }], ['post', '/pin/reset', {}]]) {
    const x = await h[m](`${URL}${path}`, body, { token: s.t });
    assert.deepEqual([x.status, x.body.error.code], [423, 'hesabpay_section_locked'], path);
  }
  assert.deepEqual(kinds(s), ['change_cancelled', 'change_cancelled']);
  const acts = await actions(s);
  assert.ok(acts.includes('hesabpay.change_cancelled'));
  assert.equal(acts.filter((a) => a === 'hesabpay.change_refused').length, 4);
  //  پس از ۲۴ ساعت دوباره باز
  await query('UPDATE shop_hesabpay SET section_locked_until=$2 WHERE shop_id=$1', [s.shopId, now() - 1]);
  assert.equal((await save(s, simple('0788333444'))).status, 200);
});

test('۴. دزدِ گوشی / رمزِ ورودِ دزدیده: دستگاهِ تازه پیش از رمز رد (بی سوختنِ تلاش، با سابقه و خبر)؛ گوشیِ مطمئن بی تأییدِ دوباره رد', async () => {
  const s = await shop('دستگاهِ-تازه');
  await ready(s, '0799111222');
  await ageDevices(s);
  const thief = await newDevice(s, 'thief-phone');
  pushes.length = 0;
  const before = await rowOf(s);
  for (const [m, path, body] of [['put', '/account', simple('0700999888')], ['put', '/account', simple('0700999888', { pin: '000001' })],
    ['post', '/account/remove', { pin: PIN }], ['put', '/pin', { pin: PIN, newPin: '975310' }], ['post', '/pin/reset', {}]]) {
    const r = await h[m](`${URL}${path}`, body, { token: thief });
    assert.deepEqual([r.status, r.body.error.code], [403, 'hesabpay_device_untrusted'], path);
    assert.ok(r.body.error.trustedAfter > now(), 'کی این دستگاه مطمئن می‌شود');
  }
  const after = await rowOf(s);
  assert.equal(after.pin_failures, 0, 'رمزِ غلط از دستگاهِ تازه حتی یک تلاش نشد');
  assert.deepEqual({ ...after, version: 0 }, { ...before, version: 0 });
  assert.equal(after.pending, null);
  assert.equal((await actions(s)).filter((a) => a === 'hesabpay.change_refused').length, 5);
  assert.deepEqual(kinds(s), ['change_refused', 'change_refused'], 'خبر به هر دو دستگاهِ صاحب، یک بار در ۱۵ دقیقه');
  //  دیدن (با رمز) از دستگاهِ تازه بی‌خطر است و شدنی؛ حال می‌گوید «این دستگاه مطمئن نیست»
  assert.equal((await h.post(`${URL}/open`, { pin: PIN }, { token: thief })).status, 200);
  assert.equal((await h.get(URL, { token: thief })).body.device.trusted, false);
  //  گوشیِ همیشگی (مطمئن) در دستِ دزد، نشستِ کهنه ⇒ بی تأییدِ دوبارهٔ ورود هیچ
  await stale(s.t);
  for (const [m, path, body] of [['put', '/account', simple('0700999888')], ['put', '/pin', { pin: PIN, newPin: '975310' }],
    ['post', '/pin/reset', {}], ['post', '/account/remove', { pin: PIN }]]) {
    assert.equal((await h[m](`${URL}${path}`, body, { token: s.t })).body.error.code, 'reauth_required', path);
  }
  assert.equal((await rowOf(s)).pin_failures, 0);
  //  صاحب از گوشیِ همیشگی پس از تأییدِ دوباره ⇒ شدنی، ولی باز هم ۲۴ ساعت در انتظار
  const again = await h.post('/api/auth/reauth', { password: s.owner.password }, { token: s.t });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal((await h.get(URL, { token: s.t })).body.device.trusted, true);
  const ok = await save(s, simple('0700999888'));
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.pending.last4, '9888');
  assert.equal((await till(s)).simple.account, '0799111222');
});

test('۴ب. صاحبِ تازه از نخستین دستگاهش؛ کسی که رمزِ ورود را دارد از دستگاهِ دوم نه رمزِ نخست می‌گذارد، نه حساب', async () => {
  const s = await shop('صاحبِ-تازه');
  const thief = await newDevice(s, 'second-device');
  const r = await h.put(`${URL}/pin`, { newPin: PIN }, { token: thief });
  assert.deepEqual([r.status, r.body.error.code], [403, 'hesabpay_device_untrusted']);
  assert.equal((await rowOf(s)).pin_hash, '');
  assert.equal((await h.put(`${URL}/pin`, { newPin: PIN }, { token: s.t })).status, 200, 'نخستین دستگاه (قدیمی‌ترین) مطمئن است');
  assert.equal((await save(s, simple('0700999888'), thief)).body.error.code, 'hesabpay_device_untrusted');
});

test('۵. QR: فقط متن و فقط با همان شمارهٔ حساب — QR ِ کسِ دیگر، بی شماره، یا عکس ⇒ رد', async () => {
  const s = await shop('QR');
  assert.equal((await h.put(`${URL}/pin`, { newPin: PIN }, { token: s.t })).status, 200);
  for (const [body, code] of [
    [simple('0799111222', { qr: 'hesabpay://pay?to=0700999888' }), 'hesabpay_qr_mismatch'],
    [simple('', { qr: 'https://evil.example/pay/me' }), 'hesabpay_qr_no_account'],
    [simple('0799111222', { qr: 'data:image/png;base64,iVBORw0KGgo=' }), 'hesabpay_qr_image'],
    [simple('0799111222', { qrImage: 'iVBORw0KGgo=' }), 'hesabpay_qr_image'],
    [{ ...simple('0799111222'), name: '' }, 'hesabpay_name_required'],
    [{ ...simple('0799111222'), name: 'x'.repeat(61) }, 'hesabpay_bad_name'],
    [{ pin: PIN, name: 'الف', mode: 'auto', apiKey: 'KEY-QR-00001', webhookSecret: 'whsec-QR-0001' }, 'hesabpay_merchant_required'],
  ]) {
    const r = await save(s, body);
    assert.deepEqual([r.status, r.body.error.code], [400, code], JSON.stringify(body).slice(0, 80));
  }
  assert.equal((await rowOf(s)).pin_failures, 0, 'خطای فرم تلاشِ رمز را نمی‌سوزاند');
  assert.equal((await rowOf(s)).mode, 'off');
  const ok = await save(s, simple('', { qr: 'HP|+93799111222|shop' }));
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual([ok.body.account, ok.body.qrPayload], ['0799111222', 'HP|+93799111222|shop']);
});

/** «خودکار» با پذیرندهٔ همین دکان (نخستین بار، فوری) ⇒ نشانیِ وب‌هوک */
async function auto(s, key) {
  assert.equal((await h.put(`${URL}/pin`, { newPin: PIN }, { token: s.t })).status, 200);
  s.merchant = `MERCH-${key}`;
  s.secret = `whsec-${key}`;
  const r = await save(s, { pin: PIN, name: 'دکانِ خودکار', mode: 'auto', apiKey: `KEY-${key}`, webhookSecret: s.secret, merchantId: s.merchant });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.auto.webhookUrl.split('/').pop();
}
const createPos = (s, amount = 145000) => h.post('/api/v1/shop/payments/intents', {
  kind: 'pos_sale', refId: `sale-${++seq}`, amount, currency: 'AFN', idempotencyKey: `key-${Date.now()}-${++seq}-hg`,
}, { token: s.t });
const intentRow = (id) => one('SELECT * FROM payment_intents WHERE id=$1', [id]);
function hook(token, body, secret) {
  const raw = Buffer.from(JSON.stringify(body));
  const sig = crypto.createHmac('sha256', secret).update(raw).digest('hex');
  return h.raw('POST', `/api/v1/payments/webhook/hesabpay/${token}`, raw, { headers: { 'Content-Type': 'application/json', 'x-hesab-signature': sig } });
}

test('۶. «خودکار»: وب‌هوکِ امضاشده و درست ولی پذیرندهٔ دیگر / بی پذیرنده / اثرِ انگشتِ کهنه ⇒ «نیازمندِ بررسی» + هشدار، هرگز «پرداخت شد»', async () => {
  const s = await shop('پذیرنده');
  const tk = await auto(s, 'MX-0001');
  const fp = (await rowOf(s)).payee_fp;
  const logs = [];
  const orig = { warn: console.warn, error: console.error, log: console.log };
  for (const k of Object.keys(orig)) console[k] = (...a) => { logs.push(a.join(' ')); orig[k](...a); };
  try {
    //  الف) پذیرندهٔ کسِ دیگر در درِ وضعیت (و وب‌هوک) — همان مبلغ، همان مرجع، امضای درست
    const i1 = (await createPos(s)).body;
    const r1 = await intentRow(i1.id);
    assert.equal(r1.payee_fp, fp, 'اثرِ انگشتِ حسابِ فعال روی قصد');
    providerStatus.set(r1.provider_session_id, { status: 'completed', amount: '1450.00', transaction_id: 'TX-1', merchant_id: 'MERCH-THIEF' });
    pushes.length = 0;
    const w1 = await hook(tk, { event_id: 'MX-E1', session_id: r1.provider_session_id, reference: i1.id, status: 'success', amount: '1450.00', merchant_id: 'MERCH-THIEF' }, s.secret);
    assert.deepEqual([w1.status, w1.body.outcome], [200, 'needs_review'], JSON.stringify(w1.body));
    assert.deepEqual([(await intentRow(i1.id)).status, (await intentRow(i1.id)).needs_review], ['pending', true]);
    const alert = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.payee_alert' AND target_id=$2`, [s.shopId, i1.id]);
    assert.equal(alert.detail.missing, 'merchant');
    assert.deepEqual(kinds(s), ['payee_alert', 'payee_alert'], 'خبر به هر دو دستگاهِ صاحب');
    //  تطبیقِ شبانه هم «پرداخت شد» نمی‌زند
    const rec = await pay.reconcile({ sinceMs: 60 * 60 * 1000 });
    assert.ok(rec.mismatched >= 1, JSON.stringify(rec));
    assert.equal((await intentRow(i1.id)).status, 'pending');
    //  ب) هیچ‌جا پذیرنده‌ای نیامده
    const i2 = (await createPos(s)).body;
    const r2 = await intentRow(i2.id);
    providerStatus.set(r2.provider_session_id, { status: 'completed', amount: '1450.00', transaction_id: 'TX-2' });
    const w2 = await hook(tk, { event_id: 'MX-E2', session_id: r2.provider_session_id, reference: i2.id, status: 'success', amount: '1450.00' }, s.secret);
    assert.equal(w2.body.outcome, 'needs_review');
    assert.equal((await intentRow(i2.id)).status, 'pending');
    //  پ) قصدی که با حسابِ دیگری ساخته شده (اثرِ انگشتِ کهنه) — حتی با پذیرندهٔ درست
    const i3 = (await createPos(s)).body;
    const r3 = await intentRow(i3.id);
    await query(`UPDATE payment_intents SET payee_fp='hp1_old' WHERE id=$1`, [i3.id]);
    providerStatus.set(r3.provider_session_id, { status: 'completed', amount: '1450.00', transaction_id: 'TX-3', merchant_id: s.merchant });
    const w3 = await hook(tk, { event_id: 'MX-E3', session_id: r3.provider_session_id, reference: i3.id, status: 'success', amount: '1450.00' }, s.secret);
    assert.equal(w3.body.outcome, 'needs_review');
    const a3 = await one(`SELECT detail FROM audit_logs WHERE action='hesabpay.payee_alert' AND target_id=$1`, [i3.id]);
    assert.equal(a3.detail.missing, 'payee');
    //  گزارشِ صاحب همین هشدارها را نشان می‌دهد
    const alerts = (await h.get(`${URL}/payees`, { token: s.t })).body.alerts;
    assert.deepEqual(alerts.map((x) => [x.intentId, x.missing]).sort(), [[i1.id, ['merchant']], [i2.id, ['merchant']], [i3.id, ['payee']]].sort());
    //  ت) درست: پذیرندهٔ همین دکان ⇒ «پرداخت شد»
    const i4 = (await createPos(s)).body;
    const r4 = await intentRow(i4.id);
    providerStatus.set(r4.provider_session_id, { status: 'completed', amount: '1450.00', transaction_id: 'TX-4', merchant_id: s.merchant });
    const w4 = await hook(tk, { event_id: 'MX-E4', session_id: r4.provider_session_id, reference: i4.id, status: 'success', amount: '1450.00' }, s.secret);
    assert.equal(w4.body.outcome, 'applied', JSON.stringify(w4.body));
    assert.equal((await intentRow(i4.id)).status, 'paid');
  } finally {
    Object.assign(console, orig);
  }
  //  هیچ کلید، راز یا شناسهٔ کاملِ پذیرنده در لاگ یا رویدادِ ثبت‌شده
  const blob = logs.join('\n') + JSON.stringify(await many('SELECT raw FROM payment_events'));
  for (const secret of ['KEY-MX-0001', 'whsec-MX-0001', 'MERCH-MX', 'MERCH-THIEF']) assert.ok(!blob.includes(secret), secret);
});

test('۷. دکانِ دیگر: لغو، گزارش و حسابِ در انتظارِ «الف» را نمی‌بیند و دست نمی‌زند', async () => {
  const a = await shop('الفِ-جدا');
  const b = await shop('بِ-جدا');
  await ready(a, '0799111222');
  assert.equal((await save(a, simple('0788333444'))).status, 200);
  const before = await rowOf(a);
  const bc = await h.post(`${URL}/pending/cancel?shopId=${a.shopId}`, { shopId: a.shopId }, { token: b.t, headers: { 'X-Shop-Id': a.shopId } });
  assert.equal(bc.body.error.code, 'hesabpay_nothing_pending', 'لغو فقط درونِ دکانِ خودِ «ب»');
  const rep = (await h.get(`${URL}/payees?shopId=${a.shopId}`, { token: b.t })).body;
  assert.deepEqual([rep.active, rep.pending, rep.accounts], [null, null, []]);
  assert.ok(!JSON.stringify(rep).includes(a.shopId));
  const st = (await h.get(URL, { token: b.t })).body;
  assert.deepEqual([st.payee, st.pending], [null, null]);
  assert.deepEqual(await rowOf(a), before);
  //  اثرِ انگشت بسته به دکان: همان شماره در دکانِ دیگر اثرِ دیگری دارد
  assert.notEqual(guard.fpOf(a.shopId, 'simple', '0799111222'), guard.fpOf(b.shopId, 'simple', '0799111222'));
});

test('۸. «پول به کدام حساب رفت»: مسیرِ صاحب و گزارشِ خودکار، فقط نام و ۴ رقم؛ حسابِ غیرِ فعال جدا و با هشدار', async () => {
  const s = await shop('گزارش');
  const tk = await auto(s, 'RP-0001');
  const fpA = (await rowOf(s)).payee_fp;
  const i = (await createPos(s)).body;
  const r = await intentRow(i.id);
  providerStatus.set(r.provider_session_id, { status: 'completed', amount: '1450.00', transaction_id: 'TX-R', merchant_id: s.merchant });
  assert.equal((await hook(tk, { event_id: 'RP-E1', session_id: r.provider_session_id, reference: i.id, status: 'success', amount: '1450.00' }, s.secret)).body.outcome, 'applied');
  const rep = (await h.get(`${URL}/payees?days=7`, { token: s.t })).body;
  assert.equal(rep.days, 7);
  assert.deepEqual([rep.active.name, rep.active.last4, rep.accounts.length, rep.alerts], ['دکانِ خودکار', '0001', 1, []]);
  assert.deepEqual(rep.accounts[0].paid, { count: 1, amountMinor: 145000 });
  assert.ok(!JSON.stringify(rep).match(/KEY-RP|whsec|MERCH-RP-/), 'بی کلید و شناسهٔ کامل');
  //  گزارشِ خودکارِ صاحب: پرداختِ خودکار + «پول رسید» ِ صندوق به حسابِ فعال و به حسابی دیگر
  const w = { fromAt: now() - DAY, until: now() + 60_000 };
  const sales = [
    { payments: [{ method: 'hesabpay', status: 'confirmed', verification: 'cashier', amount: 5000, payee: fpA }] },
    { payments: [{ method: 'hesabpay', status: 'confirmed', verification: 'cashier', amount: 7000, payee: 'hp1_someone_else' }] },
    { payments: [{ method: 'cash', status: 'confirmed', amount: 9000 }] },
  ];
  const got = await guard.receivedBy(s.shopId, w, sales);
  const mine = got.accounts.find((x) => x.fp === fpA);
  const other = got.accounts.find((x) => x.fp === 'hp1_someone_else');
  assert.deepEqual([mine.active, mine.count, mine.amountMinor, mine.last4], [true, 2, 150000, '0001']);
  assert.deepEqual([other.active, other.amountMinor], [false, 7000]);
  const text = report.render({ frequency: 'daily', from: '2026-11-01', to: '2026-11-01', until: now(), timezone: 'Asia/Kabul',
    invoices: 0, returnCount: 0, payments: [], topProducts: [], lowStock: null, hesabpay: { ...got, alerts: 2 } }).body;
  assert.match(text, /حساب‌پی: دکانِ خودکار — ۰۰۰۱/);
  assert.match(text, /⚠️ حسابِ دیگر/);
  assert.match(text, /۲ پرداختِ حساب‌پی به حسابِ دکان ثابت نشد/);
});

test('۹. رمزِ ورودِ دزدیده + `device_uid` ِ دانستهٔ گوشیِ همیشگیِ صاحب ⇒ نشستِ تازه ⇒ نامطمئن؛ گوشیِ صاحب همچنان مطمئن', async () => {
  const s = await shop('شناسهٔ-دانسته');
  await ready(s, '0799111222');
  await ageDevices(s);
  const uid = (await one('SELECT device_uid FROM devices WHERE user_id=$1', [s.owner.user.id])).device_uid;
  //  دزد همان شناسهٔ دستگاه را می‌فرستد ⇒ همان ردیفِ ۸ روزهٔ دستگاه، ولی زنجیرهٔ نشستِ تازه
  const thief = await newDevice(s, uid);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM devices WHERE user_id=$1', [s.owner.user.id])).n, 1, 'همان ردیفِ دستگاه');
  assert.equal((await h.get(URL, { token: thief })).body.device.trusted, false);
  for (const [m, path, body] of [['put', '/account', simple('0700999888')], ['put', '/pin', { pin: PIN, newPin: '975310' }],
    ['post', '/pin/reset', {}], ['post', '/account/remove', { pin: PIN }]]) {
    const r = await h[m](`${URL}${path}`, body, { token: thief });
    assert.deepEqual([r.status, r.body.error && r.body.error.code], [403, 'hesabpay_device_untrusted'], path);
  }
  assert.equal((await rowOf(s)).pending, null);
  //  تازه‌سازیِ نشستِ دزد زنجیره را کهنه نمی‌کند (همان ورودِ امروز)
  const ref = await one(`SELECT session_at FROM tokens WHERE subject_id=$1 AND kind='refresh' ORDER BY issued_at DESC LIMIT 1`, [s.owner.user.id]);
  assert.ok(Number(ref.session_at) > now() - 60_000);
  //  گوشیِ خودِ صاحب (زنجیرهٔ ۸ روزه) همچنان می‌تواند — باز هم ۲۴ ساعت در انتظار
  assert.equal((await h.get(URL, { token: s.t })).body.device.trusted, true);
  const ok = await save(s, simple('0788333444'));
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.pending.last4, '3444');
});

test('۹ب. تازه‌سازی زمانِ ورودِ زنجیره را نگه می‌دارد؛ ورودِ تازه زنجیرهٔ تازه است', async () => {
  const s = await shop('زنجیره');
  await ageDevices(s);
  const r = await h.post('/api/auth/refresh', { refreshToken: s.owner.refreshToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const born = (tk) => one('SELECT session_at FROM tokens WHERE token_hash=$1', [hashToken(tk)]);
  assert.ok(Number((await born(r.body.accessToken)).session_at) <= now() - 7 * DAY, 'تازه‌سازی همان زنجیرهٔ کهنه');
  assert.ok(Number((await born(r.body.refreshToken)).session_at) <= now() - 7 * DAY);
  const again = await h.post('/api/auth/login', { identifier: s.owner.email, password: s.owner.password, device: { deviceId: 'x-relogin' } });
  assert.ok(Number((await born(again.body.accessToken)).session_at) > now() - 60_000, 'ورودِ تازه ⇒ زنجیرهٔ تازه');
});

let opSeq = 0;
const op = (rowId, fields) => ({
  op_id: `01JHP${String(Date.now()).slice(-8)}${String(++opSeq).padStart(13, '0')}`, ts: Date.now(), table: 'sales', row_id: rowId, type: 'insert', fields,
});
const tillPay = (amount, payee) => ({ method: 'hesabpay', kind: 'wallet', amount, status: 'confirmed', verification: 'cashier', ref: 'HP-1', ...(payee === undefined ? {} : { payee }) });

test('۱۰. «پول رسید» ِ صندوق با گیرنده‌ای جز حساب‌های همین دکان ⇒ هشدار + پوشِ فوری به صاحب (یک بار)؛ حسابِ خودِ دکان نه', async () => {
  const s = await shop('صندوق');
  await ready(s, '0799111222');
  const fpA = (await rowOf(s)).payee_fp;
  const t0 = now();
  const push = (ops) => h.api('POST', '/api/sync/v1/push', {
    token: s.t, headers: { 'X-App': 'shop', 'X-Device': 'till-1' }, body: { device_id: 'till-1', schema_version: 3, ops },
  });
  const r = await push([
    op('s-ok', { status: 'completed', finalTotal: 50, createdAt: t0, payments: [tillPay(5000, fpA)] }),
    op('s-thief', { status: 'completed', finalTotal: 70, createdAt: t0, payments: [{ method: 'cash', kind: 'cash', amount: 1000, status: 'confirmed' }, tillPay(6000, 'hp1_thief_account')] }),
    op('s-old-app', { status: 'completed', finalTotal: 30, createdAt: t0, payments: [tillPay(3000)] }),
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  pushes.length = 0;
  assert.equal(await guard.tillSweep(), 1);
  const al = await many(`SELECT target_type, target_id, detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.payee_alert'`, [s.shopId]);
  assert.deepEqual(al.map((a) => [a.target_type, a.target_id, a.detail.why, a.detail.amountMinor]), [['sale', 's-thief', 'unknown', 6000]]);
  assert.deepEqual(kinds(s), ['till_payee_alert', 'till_payee_alert'], 'پوش به هر دو دستگاهِ صاحب');
  assert.ok(pushes.every((p) => p.userId === s.owner.user.id && p.message.data.saleId === 's-thief'));
  //  دورِ بعد: تکراری نه
  pushes.length = 0;
  assert.equal(await guard.tillSweep(), 0);
  assert.deepEqual(kinds(s), []);
  //  حسابِ کهنه پس از فعال شدنِ حسابِ تازه (بیرون از مهلتِ کشِ صندوق) ⇒ هشدار
  assert.equal((await save(s, simple('0788333444'))).status, 200);
  await elapse(s);
  await till(s);
  await query('UPDATE hesabpay_payees SET retired_at=$2 WHERE shop_id=$1 AND fp=$3', [s.shopId, now() - 10 * 60_000, fpA]);
  assert.equal((await push([op('s-stale', { status: 'completed', finalTotal: 40, createdAt: now(), payments: [tillPay(4000, fpA)] })])).status, 200);
  assert.equal(await guard.tillSweep(), 1);
  const stale = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.payee_alert' AND target_id='s-stale'`, [s.shopId]);
  assert.equal(stale.detail.why, 'retired');
  //  گزارشِ صاحب همان هشدارها را با منبعِ «صندوق» نشان می‌دهد
  const rep = (await h.get(`${URL}/payees`, { token: s.t })).body.alerts;
  assert.deepEqual(rep.map((a) => [a.source, a.saleId]).sort(), [['till', 's-stale'], ['till', 's-thief']]);
  //  X/Z: صندوق تاریخچهٔ حساب‌ها (نام، ۴ رقم، اثرِ انگشت) را از پیکربندی می‌گیرد
  const cfg = await till(s);
  assert.deepEqual(cfg.payees.map((p) => [p.last4, p.active]), [['3444', true], ['1222', false]]);
});
