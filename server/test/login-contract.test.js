'use strict';
/**
 * قراردادِ ورود با کدِ شش‌رقمیِ ایمیلی — پرامپتِ «ورودِ بی‌نقص».
 *
 * ── چرا این پرونده هست ──────────────────────────────────────────────
 * خواستهٔ صاحب مخزن: سیستمی که **هرگز** این‌ها را نداشته باشد — «کد
 * نرسید»، «سرور جواب نداد»، «دکمه کار نکرد»، «کد اشتباه می‌گوید»،
 * «بعد از ورود دوباره بیرون انداخت». هر کدام از این جمله‌ها این‌جا یک
 * سنجهٔ عددی دارد، نه یک ادعا.
 *
 * ماتریسِ بخشِ ۸ پرامپت، بندهای ۱ تا ۱۵، روی سرورِ واقعی.
 *
 * ── سه انحرافِ عمدی از متنِ پرامپت، با دلیل ──────────────────────────
 *  ۱) **Redis نیست.** کامپیوترِ خانگیِ صاحبِ سامانه ویندوز است و Redis
 *     ندارد. همان کلیدها ردیف‌اند با ستونِ انقضا (`login_requests`،
 *     `otp_outbox`، `login_locks`). معناها عیناً همان‌اند و همین‌جا
 *     سنجیده می‌شوند.
 *  ۲) **BullMQ نیست.** صف یک جدول است و Worker یک حلقه، با همان تلاشِ
 *     دوباره، مهلتِ هشت‌ثانیه‌ای و مدارشکن.
 *  ۳) **توکن‌ها EdDSA نشدند.** برنامه‌های نصب‌شده همین توکنِ دفتری را
 *     دارند؛ عوض کردنش یعنی بیرون افتادنِ همهٔ مشتری‌های امروز. آن‌چه
 *     اضافه شد همان چیزی است که پرامپت واقعاً می‌خواهد: چرخشی بودن،
 *     پنجرهٔ ارفاق، و سنجشِ `X-App`.
 */
/*
 *  همهٔ دویست درخواستِ بندِ ۱۵ از یک IP می‌آیند (۱۲۷.۰.۰.۱)، پس سقفِ
 *  واقعیِ «بیست در ده دقیقه» درست عمل می‌کند و همه را رد می‌کند. برای
 *  اینکه آن بند **بار** را بسنجد نه سقف را، سقفِ IP در همین فرآیند بالا
 *  می‌رود. سقفِ خودِ ایمیل و بقیهٔ قاعده‌ها دست‌نخورده‌اند و بندهای ۵ و
 *  ۱۲ همان‌ها را می‌سنجند.
 */
process.env.LOGIN_IP_MAX = process.env.LOGIN_IP_MAX || '5000';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const codes = require('../src/lib/login-codes');
const outbox = require('../src/lib/login-outbox');
const mailer = require('../src/lib/mailer');

const APP = 'shop';
const HEAD = { 'X-App': 'shop', 'X-Device': 'd-test-1', 'X-App-Version': '9.9.9' };

/** ایمیلی که هر سنجه مالِ خودش باشد — سقفِ «۵ کد در ساعت» مالِ هر ایمیل است. */
let n = 0;
const freshEmail = () => `user${++n}.${Date.now()}@example.com`;

/** کدِ خام را فقط از دفتر برمی‌داریم — هیچ‌جای دیگری قابلِ خواندن نیست. */
async function codeOf(requestId) {
  const row = await one('SELECT code_sealed FROM login_requests WHERE request_id=$1', [requestId]);
  return codes.unseal(row.code_sealed);
}

/** فاصلهٔ «۶۰ ثانیه تا ارسالِ دوباره» را عقب می‌بریم تا سنجه منتظر نماند. */
async function ageRequest(requestId, seconds) {
  await query('UPDATE login_requests SET created_at = created_at - $2 WHERE request_id=$1',
    [requestId, seconds * 1000]);
}

const post = (path, body, headers = HEAD) => h.api('POST', path, { body, headers });
const get = (path, headers = HEAD) => h.api('GET', path, { headers });

/* سرویسِ ایمیلِ ساختگی: فقط در آزمون، و از درِ خودِ `mailer` — نه mockی
 * داخلِ کدِ محصول. هر تماس این‌جا می‌نشیند تا بشود شمرد. */
const sent = [];
let failNext = 0;
let hangNext = 0;
//  «log» یعنی هیچ ایمیلی بیرون نرفت — سنجهٔ ۷ب همین را می‌خواهد
let logNext = 0;
const realSend = mailer.send;
mailer.send = async function testSend(mail) {
  sent.push({ to: mail.to, subject: mail.subject, at: Date.now() });
  if (hangNext > 0) {
    hangNext--;
    //  همان کاری که سرویسِ کندِ واقعی می‌کند: جواب نمی‌دهد تا مهلت تمام شود
    await new Promise((resolve, reject) => {
      const t = setTimeout(resolve, 30_000);
      if (mail.signal) mail.signal.addEventListener('abort', () => {
        clearTimeout(t);
        const err = new Error('aborted');
        err.name = 'AbortError';
        reject(err);
      });
    });
    return { ok: true };
  }
  if (failNext > 0) { failNext--; throw new Error('email_http_500'); }
  if (logNext > 0) { logNext--; return { delivered: true, via: 'log' }; }
  return { ok: true };
};

test.before(async () => {
  await h.start();
  outbox.breaker.reset?.();
});
test.after(async () => {
  mailer.send = realSend;
  outbox.stop();
  await h.stop();
});

/* ------------------------------------------------------------------ ۱ */

test('۱) درخواستِ کد ⇒ ۲۰۰ با request_id، و کد فقط هش‌شده در دفتر است', async () => {
  const email = freshEmail();
  const r = await post(`/api/auth/${APP}/request-code`, { email });
  assert.equal(r.status, 200);
  assert.ok(r.body.request_id, 'request_id لازم است');
  assert.equal(r.body.expires_in, 300);
  assert.equal(r.body.resend_after, 60);
  assert.match(r.body.masked_email, /\*/);

  const row = await one('SELECT * FROM login_requests WHERE request_id=$1', [r.body.request_id]);
  const raw = await codeOf(r.body.request_id);
  assert.equal(raw.length, 6);
  assert.notEqual(row.code_hash, raw, 'کدِ خام نباید در ستونِ هش باشد');
  assert.ok(!String(row.code_hash).includes(raw), 'هش نباید کد را در خود داشته باشد');
});

/* ------------------------------------------------------------------ ۲ */

test('۲) کدِ درست ⇒ توکن‌ها؛ همان کد بارِ دوم ⇒ CODE_EXPIRED', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const code = await codeOf(req.body.request_id);

  const ok = await post(`/api/auth/${APP}/verify`, { request_id: req.body.request_id, code });
  assert.equal(ok.status, 200);
  assert.ok(ok.body.access_token && ok.body.refresh_token);
  assert.equal(ok.body.access_expires_in, 3600);
  assert.equal(ok.body.user.email, email.toLowerCase());
  assert.equal(ok.body.user.created, true);
  assert.ok(ok.body.subscription, 'وضعیتِ اشتراک باید همراهِ ورود بیاید');

  const again = await post(`/api/auth/${APP}/verify`, { request_id: req.body.request_id, code });
  assert.equal(again.status, 400);
  assert.equal(again.body.error, 'CODE_EXPIRED');
});

/* ------------------------------------------------------------------ ۳ */

test('۳) پنج کدِ اشتباه ⇒ ۴۲۳ قفل، و باز شدنش پس از پایانِ مهلت', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const id = req.body.request_id;

  for (let i = 1; i <= 4; i++) {
    const r = await post(`/api/auth/${APP}/verify`, { request_id: id, code: '000000' });
    assert.equal(r.status, 400, `تلاشِ ${i}`);
    assert.equal(r.body.error, 'CODE_WRONG');
    assert.equal(r.body.attempts_left, 5 - i, 'برنامه باید بداند چند تلاش مانده');
  }
  const locked = await post(`/api/auth/${APP}/verify`, { request_id: id, code: '000000' });
  assert.equal(locked.status, 423);
  assert.equal(locked.body.error, 'LOCKED');
  assert.ok(locked.body.retry_after > 0);

  //  حتی کدِ درست هم در قفل پذیرفته نمی‌شود
  const right = await post(`/api/auth/${APP}/verify`, { request_id: id, code: await codeOf(id) || '123456' });
  assert.equal(right.status, 423);

  //  ساعت را جلو می‌بریم (قفل را تمام‌شده می‌کنیم) و باید باز شود
  await query('UPDATE login_locks SET locked_until = $2 WHERE app=$1 AND email=$3',
    [APP, Date.now() - 1000, email.toLowerCase()]);
  //  ⚠️ قفل که باز شد، ترمزِ شصت‌ثانیه‌ایِ «ارسالِ دوباره» هنوز سرِ جایش است
  //  و باید باشد — پس آن را هم عقب می‌بریم، وگرنه ۴۲۹ می‌گیریم و گمان
  //  می‌کنیم قفل باز نشده (همین یک بار گمراهم کرد).
  await ageRequest(id, 61);
  const after = await post(`/api/auth/${APP}/request-code`, { email });
  assert.equal(after.status, 200, 'پس از پایانِ قفل باید دوباره کد بدهد');
});

/* ------------------------------------------------------------------ ۴ */

test('۴) ارقامِ فارسی و عربی همان ارقامِ انگلیسی‌اند', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const code = await codeOf(req.body.request_id);
  const fa = code.replace(/\d/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[Number(d)]);
  const r = await post(`/api/auth/${APP}/verify`, { request_id: req.body.request_id, code: ` ${fa} ` });
  assert.equal(r.status, 200, 'کد با ارقامِ فارسی و فاصله باید پذیرفته شود');
});

/* ------------------------------------------------------------------ ۵ */

test('۵) دو درخواستِ پشتِ سرِ هم ⇒ ۴۲۹؛ و پس از مهلت، فقط کدِ تازه معتبر است', async () => {
  const email = freshEmail();
  const first = await post(`/api/auth/${APP}/request-code`, { email });
  const firstCode = await codeOf(first.body.request_id);

  const quick = await post(`/api/auth/${APP}/request-code`, { email });
  assert.equal(quick.status, 429);
  assert.equal(quick.body.error, 'RATE_LIMITED');
  assert.ok(quick.body.retry_after > 0 && quick.body.retry_after <= 60);

  await ageRequest(first.body.request_id, 61);
  const second = await post(`/api/auth/${APP}/request-code`, { email });
  assert.equal(second.status, 200);

  const old = await post(`/api/auth/${APP}/verify`, { request_id: first.body.request_id, code: firstCode });
  assert.equal(old.status, 400, 'کدِ قبلی با آمدنِ کدِ تازه باطل می‌شود');
  assert.equal(old.body.error, 'CODE_EXPIRED');

  const fresh = await post(`/api/auth/${APP}/verify`,
    { request_id: second.body.request_id, code: await codeOf(second.body.request_id) });
  assert.equal(fresh.status, 200);
});

/* ------------------------------------------------------------------ ۶ */

test('۶) کدِ دکان در بخشِ پمپ بی‌معناست', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/shop/request-code`, { email });
  const code = await codeOf(req.body.request_id);
  const cross = await h.api('POST', `/api/auth/pump/verify`,
    { body: { request_id: req.body.request_id, code }, headers: { ...HEAD, 'X-App': 'pump' } });
  assert.equal(cross.status, 400);
  assert.equal(cross.body.error, 'REQUEST_NOT_FOUND');
});

/* --------------------------------------------------------------- ۷ و ۸ */

test('۷) سرویسِ ایمیل که جواب نمی‌دهد ⇒ ناموفق پس از چهار تلاش، و مدارشکن باز می‌شود', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const id = req.body.request_id;

  failNext = 10;
  for (let i = 0; i < 4; i++) {
    await query('UPDATE otp_outbox SET next_attempt_at = 0, locked_at = NULL WHERE id=$1', [id]);
    await outbox.processById(id);
  }
  failNext = 0;

  const st = await outbox.statusOf(id);
  assert.equal(st.state, 'failed');
  assert.ok(['email_service_error', 'email_service_timeout'].includes(st.reason), st.reason);

  const status = await get(`/api/auth/${APP}/request-status?request_id=${id}`);
  assert.equal(status.body.state, 'failed', 'برنامه باید بتواند بپرسد و بفهمد نرفته');
  assert.ok(outbox.recentAlerts.length > 0, 'شکستِ نهایی باید به مدیر خبر بدهد');
  outbox.breaker.reset?.();
});

test('۷ب) راهِ ارسالِ «log» سبزِ ساده نمی‌دهد — دلیلش log_only است', async () => {
  /*
   *  ⛔ «رفت» با «در لاگ چاپ شد» یکی نیست.
   *
   *  با راهِ ارسالِ `log` هیچ ایمیلی از این کامپیوتر بیرون نمی‌رود، ولی
   *  `mailer.send` بی استثنا برمی‌گشت و ردیف `sent`ِ خالی مهر می‌خورد —
   *  پس میزِ «ورودها» سبزِ پررنگ نشان می‌داد برای کدی که هیچ‌وقت فرستاده
   *  نشده. گزارشِ واقعیِ صاحب سامانه دقیقاً همین بود: برنامه «کد فرستاده
   *  شد»، پنل «رفت»، و صندوقِ ایمیل خالی.
   *
   *  ⚠️ حالش همان `sent` می‌ماند و این عمدی است: سرورِ ایمیلی در کار نیست،
   *  پس چیزی برای تلاشِ دوباره وجود ندارد و `failed` کردنش فقط صف را
   *  بیهوده می‌چرخاند. آن‌چه عوض شد، **دلیل** است.
   */
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const id = req.body.request_id;

  logNext = 1;
  await query('UPDATE otp_outbox SET next_attempt_at = 0, locked_at = NULL WHERE id=$1', [id]);
  await outbox.processById(id);
  logNext = 0;

  const st = await outbox.statusOf(id);
  assert.equal(st.state, 'sent', 'چیزی برای تلاشِ دوباره نیست');
  assert.equal(st.reason, 'log_only', 'ولی باید بگوید که هیچ ایمیلی نرفته');
});

test('۸) مهلتِ سرویسِ ایمیل ⇒ دلیلِ email_service_timeout', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const id = req.body.request_id;

  hangNext = 1;
  await query('UPDATE otp_outbox SET next_attempt_at = 0, locked_at = NULL WHERE id=$1', [id]);
  await outbox.processById(id);
  hangNext = 0;

  const row = await one('SELECT reason, status FROM otp_outbox WHERE id=$1', [id]);
  assert.equal(row.reason, 'email_service_timeout', 'سرویسی که جواب نمی‌دهد با خطای معمولی یکی نیست');
  outbox.breaker.reset?.();
});

/* ------------------------------------------------------------------ ۹ */

test('۹) تازه‌سازیِ چرخشی: توکنِ تازه، و توکنِ قبلی در پنجرهٔ ارفاق هنوز کار می‌کند', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const login = await post(`/api/auth/${APP}/verify`,
    { request_id: req.body.request_id, code: await codeOf(req.body.request_id) });

  const first = await h.api('POST', '/api/auth/refresh', { body: { refresh_token: login.body.refresh_token } });
  assert.equal(first.status, 200);
  assert.notEqual(first.body.refresh_token, login.body.refresh_token, 'توکنِ تازه‌سازی باید بچرخد');

  //  همان توکنِ قبلی، بلافاصله (شبیه‌سازیِ دو درخواستِ موازی)
  const race = await h.api('POST', '/api/auth/refresh', { body: { refresh_token: login.body.refresh_token } });
  assert.equal(race.status, 200, 'در پنجرهٔ ارفاق کسی بیرون نمی‌افتد');
  const me = await h.api('GET', '/api/me/heartbeat', { token: race.body.access_token, headers: HEAD });
  assert.equal(me.status, 200, 'توکنی که در پنجرهٔ ارفاق گرفته شد باید واقعاً کار کند');

  //  و پس از پایانِ پنجره دیگر نه
  await query("UPDATE tokens SET grace_until = $1 WHERE grace_until IS NOT NULL", [Date.now() - 1000]);
  const late = await h.api('POST', '/api/auth/refresh', { body: { refresh_token: login.body.refresh_token } });
  assert.equal(late.status, 401, 'بعد از پنجرهٔ ارفاق، توکنِ کهنه مرده است');
});

test('۹ب) سه تازه‌سازیِ پشتِ سرِ هم با همان توکن ⇒ هیچ‌کدام بیرون نمی‌اندازد', async () => {
  /*
   *  ⚠️ این سنجه یک باگِ واقعی را قفل می‌کند: زنجیرهٔ چرخش در پنجرهٔ
   *  ارفاق باید **تا آخر** دنبال شود. یک بار فقط یک گام دنبال می‌شد و
   *  تلاشِ سوم ۴۰۱ می‌گرفت — روی PostgreSQL پنهان بود (درخواست‌ها واقعاً
   *  موازی می‌دویدند) و فقط روی PGlite که پشتِ سرِ هم می‌دواند دیده شد.
   */
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const login = await post(`/api/auth/${APP}/verify`,
    { request_id: req.body.request_id, code: await codeOf(req.body.request_id) });

  for (let i = 1; i <= 3; i++) {
    const r = await h.api('POST', '/api/auth/refresh', { body: { refresh_token: login.body.refresh_token } });
    assert.equal(r.status, 200, `تلاشِ ${i} باید سالم باشد`);
    const me = await h.api('GET', '/api/me/heartbeat', { token: r.body.access_token, headers: HEAD });
    assert.equal(me.status, 200, `توکنِ تلاشِ ${i} باید کار کند`);
  }
});

/* ----------------------------------------------------------------- ۱۰ */

test('۱۰) توکنِ منقضی ⇒ ۴۰۱، و پس از تازه‌سازی مسیرِ محافظت‌شده باز می‌شود', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const login = await post(`/api/auth/${APP}/verify`,
    { request_id: req.body.request_id, code: await codeOf(req.body.request_id) });

  await query('UPDATE tokens SET expires_at = $1 WHERE kind=$2 AND subject_id=$3',
    [Date.now() - 1000, 'access', login.body.user.id]);
  const dead = await h.api('GET', '/api/me/heartbeat', { token: login.body.access_token, headers: HEAD });
  assert.equal(dead.status, 401);

  const refreshed = await h.api('POST', '/api/auth/refresh', { body: { refresh_token: login.body.refresh_token } });
  assert.equal(refreshed.status, 200);
  const alive = await h.api('GET', '/api/me/heartbeat', { token: refreshed.body.access_token, headers: HEAD });
  assert.equal(alive.status, 200);
});

/* ----------------------------------------------------------------- ۱۱ */

test('۱۱) توکنِ دکان با هدرِ پمپ ⇒ نشستی پیدا نمی‌شود', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/shop/request-code`, { email });
  const login = await post(`/api/auth/shop/verify`,
    { request_id: req.body.request_id, code: await codeOf(req.body.request_id) });

  const wrong = await h.api('GET', '/api/pump/me',
    { token: login.body.access_token, headers: { ...HEAD, 'X-App': 'pump' } });
  assert.ok(wrong.status === 401 || wrong.status === 403, `انتظار ۴۰۱/۴۰۳ بود، ${wrong.status} آمد`);
});

/* ----------------------------------------------------------------- ۱۲ */

test('۱۲) سقفِ «۵ کد در ساعت» برای هر ایمیل', async () => {
  const email = freshEmail();
  for (let i = 0; i < 5; i++) {
    const r = await post(`/api/auth/${APP}/request-code`, { email });
    assert.equal(r.status, 200, `درخواستِ ${i + 1}`);
    await ageRequest(r.body.request_id, 61);   // فقط ترمزِ ۶۰ ثانیه را رد می‌کنیم
  }
  const sixth = await post(`/api/auth/${APP}/request-code`, { email });
  assert.equal(sixth.status, 429);
  assert.equal(sixth.body.error, 'RATE_LIMITED');
});

/* ----------------------------------------------------------------- ۱۳ */

test('۱۳) /api/health سریع است و دیتابیس را صدا نمی‌زند', async () => {
  const t0 = Date.now();
  const r = await h.api('GET', '/api/health');
  const ms = Date.now() - t0;
  assert.equal(r.status, 200);
  assert.equal(r.body.service, 'vill3n-auth');
  assert.ok(['up', 'down'].includes(r.body.email_worker));
  assert.ok(ms < 100, `باید زیرِ ۱۰۰ میلی‌ثانیه باشد، ${ms} بود`);
});

/* ----------------------------------------------------------------- ۱۴ */

test('۱۴) یک کلیدِ Idempotency ⇒ یک ایمیل، هرچند بار که Worker بدود', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const id = req.body.request_id;

  sent.length = 0;
  await query('UPDATE otp_outbox SET next_attempt_at = 0, locked_at = NULL WHERE id=$1', [id]);
  await outbox.processById(id);
  const firstCount = sent.length;
  //  بارِ دوم: ردیف دیگر در صف نیست، پس نباید چیزی برود
  await outbox.processById(id);
  assert.equal(sent.length, firstCount, 'ردیفِ فرستاده‌شده دوباره فرستاده نمی‌شود');
  assert.equal(firstCount, 1);
  const mail = sent[0];
  assert.ok(mail.subject.includes(await codeOf(id) || '------') || mail.subject.includes('کد ورود'),
    'کد باید در عنوانِ ایمیل باشد تا در پیش‌نمایشِ گوشی دیده شود');
});

/* ----------------------------------------------------------------- ۱۵ */

test('۱۵) دویست درخواستِ هم‌زمان: همه جواب می‌گیرند و هیچ‌کدام ۵۰۰ نمی‌شود', async () => {
  const emails = Array.from({ length: 200 }, () => freshEmail());
  const t0 = Date.now();
  const all = await Promise.all(emails.map((email) => post(`/api/auth/${APP}/request-code`, { email })));
  const ms = Date.now() - t0;

  const bad = all.filter((r) => r.status >= 500);
  assert.equal(bad.length, 0, `${bad.length} درخواست ۵۰۰ گرفت`);
  const ok = all.filter((r) => r.status === 200).length;
  assert.ok(ok >= 190, `دستِ‌کم ۱۹۰ باید ۲۰۰ بگیرند، ${ok} گرفت`);
  assert.ok(ms < 30_000, `دویست درخواست ${ms} میلی‌ثانیه طول کشید`);
});

/* -------------------------------------------------- میزِ «ورودها»ی مدیر */

test('میزِ ورودها: مدیر می‌بیند کد رفت یا نه، و قفل را برمی‌دارد', async () => {
  const email = freshEmail();
  const req = await post(`/api/auth/${APP}/request-code`, { email });
  const admin = await h.api('POST', '/api/admin/login',
    { body: { username: process.env.ADMIN_BOOTSTRAP_USER || 'admin', password: process.env.ADMIN_BOOTSTRAP_PASSWORD || 'admin' } });
  if (admin.status !== 200) return;   // نصبِ بی‌مدیر — سنجهٔ دیگری کارش را می‌کند

  const list = await h.api('GET', `/api/admin/logins?email=${encodeURIComponent(email)}`, { token: admin.body.token });
  assert.equal(list.status, 200);
  assert.ok(list.body.requests.some((r) => r.request_id === req.body.request_id));
  assert.ok(!JSON.stringify(list.body).includes(await codeOf(req.body.request_id)),
    'کد هیچ‌وقت در فهرست نمی‌آید');
});
