'use strict';
/**
 * «سرور بعضی وقت کد را به ایمیل می‌فرستد و بعضی وقت نه» — سه ریشه، هر کدام یک سنجه.
 *
 * ── ۱) پنجرهٔ سیاهِ مدارشکن ─────────────────────────────────────────
 * پنج شکستِ پشتِ سرِ هم (چند کندیِ اینترنت، چند «421 Try again later»ِ
 * جیمیل) مدارشکن را ۳۰ ثانیه باز می‌کند. هر کدی که در آن ۳۰ ثانیه خواسته
 * شود، چهار تلاشش (۲ · ۴ · ۸ ثانیه، روی هم ≈ ۱۴ ثانیه) **همه** به درِ
 * بسته می‌خوردند و ردیف `failed / breaker_open` می‌شد — بی آن‌که حتی یک
 * بار به سرورِ ایمیل زده شود. یعنی درست همان «بعضی وقت نه».
 *
 * ── ۲) نشانیِ غلط مدارشکن را باز می‌کرد ──────────────────────────────
 * `deliver` برای نشانیِ غلط `err.invalidRecipient` را می‌خواند، ولی
 * `mailer` هیچ‌وقت آن را نمی‌گذاشت. پس یک نشانیِ غلط چهار بار تکرار می‌شد
 * و هر چهار بار روی مدارشکن می‌نشست — دو نشانیِ غلط ⇒ درِ همه بسته.
 *
 * ── ۳) کدِ ثبت‌نام و رمز یک تلاش بیشتر نداشت ─────────────────────────
 * `otp.request` همان لحظه می‌فرستد و یک قطعیِ گذرا یعنی «کد فرستاده نشد».
 * حالا یک بارِ دیگر، فقط برای خطای گذرا (نه ۵xxِ «هرگز»).
 */
process.env.LOGIN_BREAKER_RESET_MS = '1500';
process.env.LOGIN_EMAIL_BACKOFF_MS = '50';
process.env.LOGIN_WORKER_TICK_MS = '40';
process.env.OTP_RETRY_DELAY_MS = '30';

const test = require('node:test');
const assert = require('node:assert/strict');
const net = require('net');
const h = require('./helpers');
const { query, one } = require('../src/db');
const outbox = require('../src/lib/login-outbox');
const mailer = require('../src/lib/mailer');

const HEAD = { 'X-App': 'shop', 'X-Device': 'd-mail-1', 'X-App-Version': '9.9.9' };
let n = 0;
const freshEmail = () => `flaky${++n}.${Date.now()}@example.com`;
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

/** سرورِ SMTPِ ساده روی همین کامپیوتر؛ `rcpt` می‌گوید به RCPT چه جوابی بدهد. */
function fakeSmtp(rcpt) {
  let accepted = 0;
  const server = net.createServer((sock) => {
    let buf = ''; let inData = false;
    const w = (s) => sock.write(`${s}\r\n`);
    w('220 fake ESMTP');
    sock.on('data', (d) => {
      buf += d.toString('latin1');
      let i;
      while ((i = buf.indexOf('\r\n')) >= 0) {
        const line = buf.slice(0, i); buf = buf.slice(i + 2);
        if (inData) { if (line === '.') { inData = false; accepted++; w('250 queued'); } continue; }
        const c = line.toUpperCase();
        if (c.startsWith('EHLO')) { w('250-fake'); w('250 AUTH LOGIN'); }
        else if (c === 'AUTH LOGIN') w('334 VXNlcm5hbWU6');
        else if (c.startsWith('RCPT')) w(rcpt);
        else if (c === 'DATA') { inData = true; w('354 go'); }
        else if (c === 'QUIT') { w('221 bye'); sock.end(); }
        else if (/^[A-Za-z0-9+/=]+$/.test(line) && !c.startsWith('MAIL')) w(sock._pw ? '235 ok' : (sock._pw = 1, '334 UGFzc3dvcmQ6'));
        else w('250 ok');
      }
    });
    sock.on('error', () => {});
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve({
    port: server.address().port, close: () => server.close(), accepted: () => accepted,
  })));
}

async function useSmtp(port) {
  await mailer.save({ provider: 'smtp', host: '127.0.0.1', port: String(port), user: 'u', pass: 'p', from: 'bot@example.com', secure: 'none' });
}

test.before(async () => { await h.start(); outbox.breaker.reset(); });
test.after(async () => { outbox.stop(); await h.stop(); });

test('۱) کدی که در پنجرهٔ بازِ مدارشکن خواسته شد، پس از بسته شدنش **می‌رود** — نه failed/breaker_open', async () => {
  const realSend = mailer.send;
  const got = [];
  mailer.send = async (mail) => { got.push(mail.to); return { delivered: true, via: 'smtp' }; };
  try {
    //  همان حالی که پنج شکستِ پشتِ سرِ هم می‌سازد
    for (let i = 0; i < outbox.BREAKER_FAILS; i++) outbox.breaker.fail();
    assert.equal(outbox.breaker.snapshot().state, 'open');

    const email = freshEmail();
    const r = await h.api('POST', '/api/auth/shop/request-code', { body: { email }, headers: HEAD });
    assert.equal(r.status, 200);
    const id = r.body.request_id;

    outbox.start();
    let st;
    for (let i = 0; i < 120; i++) {
      st = await outbox.statusOf(id);
      if (st && (st.state === 'sent' || st.state === 'failed')) break;
      await sleep(50);
    }
    outbox.stop();
    assert.equal(st.state, 'sent', `کد باید برود، نه ${st.state}/${st.reason || ''}`);
    assert.ok(got.includes(email), 'و واقعاً به سرویسِ ایمیل رسیده باشد');
    //  ⛔ انتظار پشتِ درِ بسته «تلاش» نیست: چهار تلاش برای خودِ ارسال می‌ماند
    const row = await one('SELECT attempts FROM otp_outbox WHERE id=$1', [id]);
    assert.ok(Number(row.attempts) <= 2, `انتظار پشتِ مدارشکن تلاش حساب شد: ${row.attempts}`);
  } finally {
    mailer.send = realSend;
    outbox.breaker.reset();
  }
});

test('۱ب) و کدی که پشتِ درِ بسته منقضی شد، همچنان فرستاده نمی‌شود', async () => {
  const realSend = mailer.send;
  let calls = 0;
  mailer.send = async () => { calls++; return { delivered: true }; };
  try {
    for (let i = 0; i < outbox.BREAKER_FAILS; i++) outbox.breaker.fail();
    const email = freshEmail();
    const r = await h.api('POST', '/api/auth/shop/request-code', { body: { email }, headers: HEAD });
    const id = r.body.request_id;
    await query('UPDATE login_requests SET expires_at = 1 WHERE request_id=$1', [id]);
    await query('UPDATE otp_outbox SET next_attempt_at = 0, locked_at = NULL WHERE id=$1', [id]);
    await outbox.processById(id);
    const st = await outbox.statusOf(id);
    assert.equal(st.state, 'failed');
    assert.equal(st.reason, 'expired');
    assert.equal(calls, 0, 'کدِ منقضی هیچ‌وقت فرستاده نمی‌شود');
  } finally {
    mailer.send = realSend;
    outbox.breaker.reset();
  }
});

test('۲) mailer نشانیِ غلط (۵۵۰ روی RCPT) را invalidRecipient می‌گوید، و ۴۲۱ را گذرا', async () => {
  const bad = await fakeSmtp('550 5.1.1 no such user');
  try {
    await useSmtp(bad.port);
    await assert.rejects(mailer.send({ to: 'nobody@example.com', subject: 's', text: 't' }), (e) => {
      assert.equal(e.invalidRecipient, true, e.message);
      assert.equal(e.smtpCode, 550);
      return true;
    });
  } finally { bad.close(); }

  const busy = await fakeSmtp('421 4.7.0 Try again later');
  try {
    await useSmtp(busy.port);
    await assert.rejects(mailer.send({ to: 'x@example.com', subject: 's', text: 't' }), (e) => {
      assert.notEqual(e.invalidRecipient, true, 'گذرا نشانیِ غلط نیست');
      assert.equal(e.temporary, true);
      return true;
    });
  } finally { busy.close(); }
});

test('۲ب) نشانیِ غلط یک تلاش است و مدارشکن را باز نمی‌کند', async () => {
  const bad = await fakeSmtp('550 5.1.1 no such user');
  try {
    await useSmtp(bad.port);
    outbox.breaker.reset();
    for (let k = 0; k < 3; k++) {
      const email = freshEmail();
      const r = await h.api('POST', '/api/auth/shop/request-code', { body: { email }, headers: HEAD });
      const id = r.body.request_id;
      await query('UPDATE otp_outbox SET next_attempt_at = 0, locked_at = NULL WHERE id=$1', [id]);
      await outbox.processById(id);
      const st = await outbox.statusOf(id);
      assert.equal(st.state, 'failed');
      assert.equal(st.reason, 'invalid_recipient');
      assert.equal(st.attempts, 1, 'نشانیِ غلط چهار بار تکرار نمی‌شود');
    }
    assert.equal(outbox.breaker.snapshot().state, 'closed', 'سه نشانیِ غلط درِ بقیه را نمی‌بندد');
  } finally { bad.close(); outbox.breaker.reset(); }
});

test('۳) کدِ ثبت‌نام: خطای گذرا یک بارِ دیگر امتحان می‌شود و می‌رود', async () => {
  const good = await fakeSmtp('250 ok');
  await useSmtp(good.port);
  const otp = require('../src/lib/otp');
  const realSend = mailer.send;
  let calls = 0;
  mailer.send = async (mail) => {
    calls++;
    if (calls === 1) throw Object.assign(new Error('سرور ایمیل گفت: 421 Try again later'), { smtpCode: 421, temporary: true });
    return realSend.call(mailer, mail);
  };
  try {
    const email = freshEmail();
    const out = await otp.request(email, { purpose: 'register', app: 'pump' });
    assert.equal(out.sent, true);
    assert.equal(calls, 2, 'یک تلاشِ دوباره');
    assert.equal(good.accepted(), 1, 'و ایمیل واقعاً به سرورِ ایمیل رسید');
    const { c } = await one('SELECT COUNT(*)::int AS c FROM otp_codes WHERE destination=$1', [email]);
    assert.equal(c, 1, 'یک کد، نه دو');
  } finally { mailer.send = realSend; good.close(); }
});

test('۳ب) ولی «هرگز»ِ سرورِ ایمیل (۵xx) دوباره امتحان نمی‌شود', async () => {
  const good = await fakeSmtp('250 ok');
  await useSmtp(good.port);
  const otp = require('../src/lib/otp');
  const realSend = mailer.send;
  let calls = 0;
  mailer.send = async () => {
    calls++;
    throw Object.assign(new Error('سرور ایمیل گفت: 550 no such user'), { smtpCode: 550, invalidRecipient: true });
  };
  try {
    await assert.rejects(otp.request(freshEmail(), { purpose: 'register', app: 'pump' }), (e) => {
      assert.equal(e.code, 'delivery_failed');
      return true;
    });
    assert.equal(calls, 1);
  } finally { mailer.send = realSend; good.close(); }
});
