'use strict';
/*
 *  ══ «کد واقعاً می‌رسد؟» — با یک صندوقِ SMTPِ واقعی ══════════════════════
 *
 *  بندِ ۱۹ پرامپت: «کد ۶ رقمی کمتر از ۳ ثانیه می‌رسد.» این ادعا تا امروز
 *  هیچ سنجه‌ای نداشت — آزمون‌های دیگر کد را از **دفتر** می‌خوانند، نه از
 *  چیزی که واقعاً بیرون رفته. یعنی اگر `mailer` می‌شکست، همه سبز می‌ماندند
 *  و فقط مشتری می‌فهمید.
 *
 *  این‌جا یک سرورِ SMTPِ واقعی روی ۱۲۷.۰.۰.۱ بالا می‌آید و `lib/mailer.js`
 *  — که خودش SMTP می‌نویسد، بی nodemailer — همان گفت‌وگوی واقعی را با آن
 *  انجام می‌دهد: EHLO · AUTH LOGIN · MAIL FROM · RCPT TO · DATA. آن‌چه در
 *  صندوق می‌نشیند دقیقاً همان چیزی است که به مشتری می‌رسد.
 *
 *  ⚠️ گواهیِ TLS کامیت نمی‌شود و نباید بشود: سرِ هر اجرا با `openssl`
 *  ساخته می‌شود و با همان اجرا می‌میرد. گواهیِ کامیت‌شده یعنی یک کلیدِ
 *  خصوصی در Git، هرچقدر هم بی‌خطر.
 *
 *  ⚠️ `NODE_EXTRA_CA_CERTS` فقط سرِ **شروعِ** پروسه خوانده می‌شود، پس این
 *  فایل خودش را یک بار با همان متغیر دوباره اجرا می‌کند.
 */
const path = require('path');
const os = require('os');
const fs = require('fs');
const { execFileSync, spawnSync } = require('child_process');

if (!process.env.MAIL_PROBE_CHILD) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'shop-mail-'));
  const crt = path.join(dir, 'smtp.crt');
  const key = path.join(dir, 'smtp.key');
  try {
    execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes',
      '-keyout', key, '-out', crt, '-days', '1', '-subj', '/CN=127.0.0.1',
      '-addext', 'subjectAltName=IP:127.0.0.1,DNS:localhost'], { stdio: 'ignore' });
  } catch {
    //  ⚠️ نبودنِ openssl سنجه را **رد** می‌کند، نه قرمز — همان قاعدهٔ
    //  پلی‌رایت در این مخزن.
    console.log('⏭ رد شد — openssl روی این ماشین نیست');
    process.exit(0);
  }
  const r = spawnSync(process.execPath, [__filename], {
    stdio: 'inherit',
    env: { ...process.env, MAIL_PROBE_CHILD: '1', NODE_EXTRA_CA_CERTS: crt, MAIL_PROBE_CRT: crt, MAIL_PROBE_KEY: key },
  });
  try { fs.rmSync(dir, { recursive: true, force: true }); } catch { /* مهم نیست */ }
  process.exit(r.status === null ? 1 : r.status);
}

const CRT = process.env.MAIL_PROBE_CRT;
const KEY = process.env.MAIL_PROBE_KEY;

process.env.TEST_DATABASE_URL = process.env.TEST_DATABASE_URL || 'pglite:memory';
process.env.SMTP_HOST = '127.0.0.1';
process.env.SMTP_PORT = '2465';
process.env.SMTP_SECURE = 'ssl';
process.env.SMTP_USER = 'robot@vill3n.test';
process.env.SMTP_PASS = 'not-a-real-password';
process.env.EMAIL_FROM = 'robot@vill3n.test';
process.env.LOGIN_IP_MAX = '100000';
process.env.RATE_LOGIN_EDGE_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';

const { startMailbox } = require('./fixtures/smtp-mailbox.js');

let pass = 0, fail = 0;
const check = (name, ok, extra = '') => {
  if (ok) { pass++; console.log('  ✅', name); }
  else { fail++; console.log('  ❌', name, extra ? '— ' + String(extra).slice(0, 220) : ''); }
};

(async () => {
  const box = await startMailbox({ port: 2465, cert: CRT, key: KEY });
  const h = require('./helpers');
  const base = await h.start();
  const { one, query } = require('../src/db');
  const codes = require('../src/lib/login-codes');
  const mailer = require('../src/lib/mailer');
  const outbox = require('../src/lib/login-outbox');
  if (mailer.invalidate) mailer.invalidate();
  /*
   *  ⚠️ Workerِ ایمیل عمداً از `createApp` روشن نمی‌شود (وگرنه هر آزمونی
   *  یک تایمر جا می‌گذاشت)؛ `index.js` روشنش می‌کند. پس این سنجه هم مثلِ
   *  سرورِ واقعی خودش روشنش می‌کند.
   */
  outbox.start();

  const H = { 'Content-Type': 'application/json', 'X-App': 'shop', 'X-App-Id': 'shop', 'X-Device': 'mail-probe' };

  console.log('\n── ۱) کدِ ورود واقعاً به صندوق می‌رسد ──');
  const email = 'ahmad.probe@example.com';
  const t0 = Date.now();
  const r = await fetch(`${base}/api/auth/shop/request-code`, {
    method: 'POST', headers: H, body: JSON.stringify({ email }),
  });
  const body = await r.json();
  check('پاسخِ request-code ۲۰۰ است', r.status === 200, r.status + ' ' + JSON.stringify(body).slice(0, 150));

  const deadline = Date.now() + 15000;
  while (box.inbox.length === 0 && Date.now() < deadline) await new Promise(s => setTimeout(s, 100));
  const took = Date.now() - t0;

  check('ایمیل در صندوق نشست', box.inbox.length === 1, `${box.inbox.length} نامه`);
  if (box.inbox.length) {
    const m = box.inbox[0];
    check('به همان نشانی رفت', m.to.includes(email), m.to.join(','));
    const row = await one('SELECT code_sealed FROM login_requests WHERE email=$1 ORDER BY created_at DESC LIMIT 1', [email]);
    const real = codes.unseal(row.code_sealed);
    /*  بدنهٔ MIME می‌تواند base64 باشد، پس «رشته را بگرد» کافی نیست؛ و کد
     *  در HTML با فاصله بینِ رقم‌ها نوشته می‌شود.  */
    const decoded = m.raw + '\n' + m.raw.split('\n')
      .filter(l => /^[A-Za-z0-9+/=]{16,}$/.test(l.trim()))
      .map(l => { try { return Buffer.from(l.trim(), 'base64').toString('utf8'); } catch { return ''; } })
      .join('\n');
    const spaced = real.split('').join(' ');
    check('متنِ نامه همان کدِ دفتر را دارد', decoded.includes(real) || decoded.includes(spaced), 'کد=' + real);
    check(`کمتر از ۳ ثانیه رسید (${took}ms)`, took < 3000, took + 'ms');
    check('نامه موضوع دارد و خالی نیست', /Subject:/i.test(m.raw) && m.raw.length > 200, String(m.raw.length));
    const raw = await one('SELECT code_hash FROM login_requests WHERE email=$1 ORDER BY created_at DESC LIMIT 1', [email]);
    check('کدِ خام در ستونِ هشِ دفتر نیست', !String(raw.code_hash).includes(real), String(raw.code_hash).slice(0, 20));
    const v = await fetch(`${base}/api/auth/shop/verify`, {
      method: 'POST', headers: H,
      body: JSON.stringify({ request_id: body.request_id || body.requestId, email, code: real }),
    }).then(x => x.json());
    check('همان کدِ رسیده توکن می‌دهد', Boolean(v.access_token || v.accessToken), JSON.stringify(v).slice(0, 150));
  }

  console.log('\n── ۲) صندوق که نپذیرفت، سرور صریح می‌گوید ──');
  await new Promise(res => box.server.close(res));
  const bad = await startMailbox({ port: 2465, cert: CRT, key: KEY, failAll: true });
  await query('DELETE FROM otp_outbox');
  const r2 = await fetch(`${base}/api/auth/shop/request-code`, {
    method: 'POST', headers: H, body: JSON.stringify({ email: 'reject@example.com' }),
  });
  check('باز هم ۲۰۰ می‌گیرد (وجودِ حساب لو نمی‌رود)', r2.status === 200, String(r2.status));
  const dl2 = Date.now() + 20000;
  let st = null;
  while (Date.now() < dl2) {
    st = await one("SELECT status, attempts, last_error FROM otp_outbox WHERE email=$1 ORDER BY id DESC LIMIT 1", ['reject@example.com']);
    //  ⚠️ تا `last_error` ننشسته صبر می‌کنیم: `attempts` یک لحظه زودتر
    //  بالا می‌رود و بریدنِ حلقه همان‌جا سنجهٔ بعدی را **گاهی** سرخ می‌کرد
    //  — همان شکلِ مسابقه، نه یک خرابیِ واقعی.
    if (st && st.last_error) break;
    if (st && st.status === 'failed') break;
    await new Promise(s => setTimeout(s, 250));
  }
  check('ردیفِ صف تلاش را ثبت کرد', Boolean(st && (st.status === 'failed' || Number(st.attempts) >= 1)), JSON.stringify(st));
  check('خطای خودِ صندوق در دفتر نوشته شد', Boolean(st && st.last_error), String(st && st.last_error).slice(0, 120));
  check('هیچ نامه‌ای پذیرفته نشد', bad.inbox.length === 0, String(bad.inbox.length));

  await new Promise(res => bad.server.close(res));
  outbox.stop();
  await h.stop();
  console.log(`\n${fail ? '❌' : '✅'} ${pass} سبز، ${fail} قرمز\n`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
