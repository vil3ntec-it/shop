'use strict';
/**
 * فرستادن ایمیل — از خودِ سرور، بدون هیچ کتابخانه‌ی بیرونی.
 *
 * ── چرا این فایل هست ───────────────────────────────────────────────
 * ثبت‌نام سه‌پله‌ای کد شش‌رقمی را به ایمیل می‌فرستاد، ولی راهِ فرستادنش
 * فقط «چاپ در لاگِ سرور» بود مگر اینکه کسی دستی EMAIL_API_URL را در
 * فایل .env می‌گذاشت. یعنی در عمل کد هیچ‌جا نمی‌رفت: نه کاربر می‌دیدش،
 * نه مدیر می‌توانست از گوشی‌اش درستش کند. هر ثبت‌نامی همان‌جا می‌ماند.
 *
 * حالا:
 *   • تنظیمات ایمیل در دیتابیس است و از برنامه‌ی مدیریت عوض می‌شود
 *   • SMTP خودمان اینجا نوشته شده (Gmail، Zoho، هر سروری)
 *   • راهِ «API» هم هست برای Resend و Brevo و مانندشان
 *   • یک دکمه‌ی «آزمایش» هست که واقعاً می‌فرستد و خطا را نشان می‌دهد
 *
 * ── چرا SMTP دستی و نه nodemailer ──────────────────────────────────
 * این سرور عمداً فقط دو وابستگی دارد (express و pg) تا روی هر هاستی و
 * بدون اینترنتِ npm بالا بیاید. SMTP هم آن‌قدر کوچک است که ارزش یک
 * وابستگی تازه را ندارد: چند خط فرمان متنی روی یک سوکت TLS.
 *
 * رمزِ سرویس هرگز کامل بیرون نمی‌رود — نه به برنامه‌ی مدیریت، نه به لاگ.
 */
const net = require('net');
const tls = require('tls');
const plans = require('./plans');
const config = require('../config');

const PREFIX = 'email_';

/**
 * کلیدهای تنظیمات و پیش‌فرضشان از .env.
 *
 * `provider`:
 *   log     — فقط در لاگ سرور چاپ شود (برای سرور خانگیِ بدون ایمیل)
 *   smtp    — با سرور ایمیلِ خودتان یا Gmail/Zoho/…
 *   api     — سرویس‌های HTTP مثل Resend و Brevo
 */
const FIELDS = {
  provider:  () => (config.email.url ? 'api' : 'log'),
  from:      () => config.email.from,
  fromName:  () => 'توحید',
  //  SMTP
  host:      () => process.env.SMTP_HOST || '',
  port:      () => process.env.SMTP_PORT || '587',
  user:      () => process.env.SMTP_USER || '',
  pass:      () => process.env.SMTP_PASS || '',
  //  ssl = از همان اول TLS (پورت ۴۶۵) | starttls = ساده شروع و بعد رمز (۵۸۷)
  secure:    () => process.env.SMTP_SECURE || 'starttls',
  //  API
  url:       () => config.email.url,
  key:       () => config.email.key,
  //  متن‌ها
  otpSubject:  () => config.email.subject,
  otpTemplate: () => '',
};

let cache = { at: 0, value: null };
const TTL_MS = 10 * 1000;

function invalidate() { cache = { at: 0, value: null }; }

async function current() {
  const t = Date.now();
  if (cache.value && t - cache.at < TTL_MS) return cache.value;
  const rows = await plans.allConfig();
  const out = {};
  for (const [name, fallback] of Object.entries(FIELDS)) {
    const stored = rows[PREFIX + name];
    out[name] = stored !== undefined && stored !== '' ? stored : fallback();
  }
  cache = { at: t, value: out };
  return out;
}

async function save(patch = {}) {
  for (const name of Object.keys(FIELDS)) {
    if (!(name in patch)) continue;
    const value = patch[name];
    if (value === undefined || value === null) continue;
    await plans.setConfig(PREFIX + name, String(value));
  }
  invalidate();
  return current();
}

/** تنظیمات، امن برای فرستادن به برنامه‌ی مدیریت — بدون رمز و بدون کلید. */
async function masked() {
  const s = await current();
  return {
    provider: s.provider,
    from: s.from,
    fromName: s.fromName,
    host: s.host,
    port: s.port,
    user: s.user,
    secure: s.secure,
    url: s.url,
    otpSubject: s.otpSubject,
    otpTemplate: s.otpTemplate,
    passSet: !!s.pass,
    passHint: s.pass ? '••••••••' : '',
    keySet: !!s.key,
    keyHint: s.key ? `••••${String(s.key).slice(-4)}` : '',
    //  برنامه‌ی مدیریت با این می‌فهمد ایمیل واقعاً راه افتاده یا نه، و
    //  اگر نه، دقیقاً چه چیزی کم است — نه یک «تنظیم نشده» مبهم
    ready: readiness(s).ok,
    missing: readiness(s).missing,
  };
}

/** چه چیزی کم است تا ایمیل واقعاً برود. */
function readiness(s) {
  const missing = [];
  if (s.provider === 'smtp') {
    if (!s.host) missing.push('نشانی سرور SMTP');
    if (!s.user) missing.push('نام کاربری');
    if (!s.pass) missing.push('رمز');
    if (!s.from) missing.push('ایمیل فرستنده');
  } else if (s.provider === 'api') {
    if (!s.url) missing.push('نشانی سرویس');
    if (!s.from) missing.push('ایمیل فرستنده');
  }
  return { ok: missing.length === 0, missing };
}

/* ==========================================================
   SMTP
   ========================================================== */

/**
 * یک گفت‌وگوی SMTP کامل روی یک سوکت.
 *
 * SMTP خط‌به‌خط است: هر فرمان یک خط، هر پاسخ یک عدد سه‌رقمی. اینجا هر
 * فرمان فرستاده می‌شود و منتظر عددِ مورد انتظار می‌مانیم؛ اگر عدد دیگری
 * آمد، همان خطِ سرور را به عنوان خطا بالا می‌دهیم — چون پیام خودِ سرورِ
 * ایمیل («رمز اپلیکیشن لازم است»، «فرستنده مجاز نیست») صدبار از هر
 * پیام عمومیِ ما گویاتر است.
 */
function smtpSend(cfg, mail) {
  const port = Number(cfg.port) || 587;
  const useTls = String(cfg.secure).toLowerCase() === 'ssl' || port === 465;

  return new Promise((resolve, reject) => {
    let socket = useTls
      ? tls.connect({ host: cfg.host, port, servername: cfg.host })
      : net.connect({ host: cfg.host, port });

    let buffer = '';
    let done = false;
    let waiting = null;      // { expect, resolve, reject }
    const log = [];

    const timer = setTimeout(() => fail(new Error('سرور ایمیل پاسخ نداد (۳۰ ثانیه)')), 30000);

    function fail(err) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      try { socket.destroy(); } catch { /* بسته بوده */ }
      reject(err);
    }

    function finish(value) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      try { socket.end(); } catch { /* بسته بوده */ }
      resolve(value);
    }

    /** منتظر یک پاسخ کامل با کدِ مورد انتظار. */
    function expect(codes) {
      return new Promise((res, rej) => { waiting = { codes, res, rej }; });
    }

    function onLineBlock(text) {
      const code = Number(text.slice(0, 3));
      log.push(text.trim().slice(0, 200));
      if (!waiting) return;
      const w = waiting;
      waiting = null;
      if (w.codes.includes(code)) w.res(text);
      else w.rej(new Error(`سرور ایمیل گفت: ${text.trim().slice(0, 200)}`));
    }

    function attach(s) {
      s.setEncoding('utf8');
      s.on('data', (chunk) => {
        buffer += chunk;
        //  پاسخِ چندخطی: خطِ آخر بعد از عدد یک فاصله دارد، نه خط تیره
        let idx;
        while ((idx = buffer.indexOf('\r\n')) >= 0) {
          const line = buffer.slice(0, idx);
          buffer = buffer.slice(idx + 2);
          pending.push(line);
          if (/^\d{3} /.test(line)) {
            const block = pending.join('\n');
            pending = [];
            onLineBlock(block);
          }
        }
      });
      s.on('error', (err) => fail(new Error(`اتصال به سرور ایمیل نشد: ${err.message}`)));
      s.on('close', () => {
        if (!done && waiting) fail(new Error('سرور ایمیل ارتباط را بست'));
      });
    }

    let pending = [];

    function send(line) {
      socket.write(line + '\r\n');
    }

    attach(socket);

    (async () => {
      try {
        await expect([220]);
        send(`EHLO ${hostnameOf(cfg.from) || 'localhost'}`);
        let greeting = await expect([250]);

        if (!useTls && String(cfg.secure).toLowerCase() !== 'none') {
          if (!/STARTTLS/i.test(greeting)) {
            throw new Error('این سرور STARTTLS ندارد — پورت ۴۶۵ با حالت SSL را امتحان کنید');
          }
          send('STARTTLS');
          await expect([220]);
          //  از اینجا به بعد همان سوکت، اما رمزشده. بدون این، رمزِ حساب
          //  به شکل ساده روی شبکه می‌رفت.
          const plainSocket = socket;
          socket = tls.connect({ socket: plainSocket, servername: cfg.host });
          buffer = '';
          pending = [];
          attach(socket);
          await new Promise((res, rej) => {
            socket.once('secureConnect', res);
            socket.once('error', rej);
          });
          send(`EHLO ${hostnameOf(cfg.from) || 'localhost'}`);
          greeting = await expect([250]);
        }

        if (cfg.user) {
          //  AUTH LOGIN همه‌جا هست؛ PLAIN هم اگر آن یکی نبود
          if (/AUTH[ =-].*LOGIN/i.test(greeting)) {
            send('AUTH LOGIN');
            await expect([334]);
            send(Buffer.from(cfg.user, 'utf8').toString('base64'));
            await expect([334]);
            send(Buffer.from(cfg.pass, 'utf8').toString('base64'));
            await expect([235]);
          } else {
            send('AUTH PLAIN ' + Buffer.from(`\0${cfg.user}\0${cfg.pass}`, 'utf8').toString('base64'));
            await expect([235]);
          }
        }

        send(`MAIL FROM:<${cfg.from}>`);
        await expect([250]);
        send(`RCPT TO:<${mail.to}>`);
        await expect([250, 251]);
        send('DATA');
        await expect([354]);
        socket.write(buildMessage(cfg, mail));
        socket.write('\r\n.\r\n');
        await expect([250]);
        send('QUIT');

        finish({ delivered: true, via: 'smtp', response: log.slice(-1)[0] || '' });
      } catch (err) {
        fail(err);
      }
    })();
  });
}

function hostnameOf(email) {
  const at = String(email || '').indexOf('@');
  return at > 0 ? String(email).slice(at + 1) : '';
}

/** عنوان و نام فارسی باید MIME-encode شوند، وگرنه در صندوق ورودی خط‌خطی می‌آیند. */
function mimeWord(text) {
  const s = String(text || '');
  // eslint-disable-next-line no-control-regex
  if (/^[\x20-\x7E]*$/.test(s)) return s;
  return `=?UTF-8?B?${Buffer.from(s, 'utf8').toString('base64')}?=`;
}

/** خطی که با نقطه شروع می‌شود در SMTP معنی «پایان پیام» دارد؛ باید دو تا شود. */
function dotStuff(text) {
  return String(text).replace(/\r?\n/g, '\r\n').replace(/^\./gm, '..');
}

function buildMessage(cfg, mail) {
  const boundary = `b${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
  const from = cfg.fromName ? `${mimeWord(cfg.fromName)} <${cfg.from}>` : cfg.from;
  const head = [
    `From: ${from}`,
    `To: <${mail.to}>`,
    `Subject: ${mimeWord(mail.subject)}`,
    `Date: ${new Date().toUTCString()}`,
    `Message-ID: <${boundary}@${hostnameOf(cfg.from) || 'localhost'}>`,
    'MIME-Version: 1.0',
    `Content-Type: multipart/alternative; boundary="${boundary}"`,
    '',
  ].join('\r\n');

  const text = [
    `--${boundary}`,
    'Content-Type: text/plain; charset=UTF-8',
    'Content-Transfer-Encoding: base64',
    '',
    chunk64(Buffer.from(mail.text || '', 'utf8').toString('base64')),
    `--${boundary}`,
    'Content-Type: text/html; charset=UTF-8',
    'Content-Transfer-Encoding: base64',
    '',
    chunk64(Buffer.from(mail.html || mail.text || '', 'utf8').toString('base64')),
    `--${boundary}--`,
    '',
  ].join('\r\n');

  return dotStuff(head + text);
}

/** base64 در ایمیل باید خطهای کوتاه داشته باشد. */
function chunk64(s) {
  return (s.match(/.{1,76}/g) || []).join('\r\n');
}

/* ==========================================================
   API (Resend، Brevo، Mailgun و مانندشان)
   ========================================================== */

async function apiSend(cfg, mail) {
  const res = await fetch(cfg.url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(cfg.key ? { Authorization: `Bearer ${cfg.key}` } : {}),
    },
    body: JSON.stringify({
      from: cfg.fromName ? `${cfg.fromName} <${cfg.from}>` : cfg.from,
      to: [mail.to],
      subject: mail.subject,
      text: mail.text,
      html: mail.html || mail.text,
    }),
    signal: AbortSignal.timeout(20000),
  });
  const body = await res.text().catch(() => '');
  if (!res.ok) throw new Error(`سرویس ایمیل پاسخ ${res.status} داد: ${body.slice(0, 200)}`);
  return { delivered: true, via: 'api', response: body.slice(0, 200) };
}

/* ==========================================================
   در دسترسِ بقیه‌ی سرور
   ========================================================== */

/**
 * فرستادن یک ایمیل.
 * @param {{to:string, subject:string, text:string, html?:string}} mail
 */
async function send(mail) {
  const cfg = await current();
  const to = String(mail.to || '').trim();
  if (!to || !to.includes('@')) throw new Error('نشانی ایمیل درست نیست');

  if (cfg.provider === 'log') {
    console.log(`[email] به ${to} — ${mail.subject}\n${mail.text}`);
    return { delivered: true, via: 'log' };
  }
  const ready = readiness(cfg);
  if (!ready.ok) throw new Error(`تنظیمات ایمیل کامل نیست: ${ready.missing.join('، ')}`);

  if (cfg.provider === 'api') return apiSend(cfg, { ...mail, to });
  return smtpSend(cfg, { ...mail, to });
}

/**
 * قالبِ ایمیل — یک کارتِ ساده‌ی راست‌به‌چپ.
 *
 * عمداً بی‌تصویر و بی‌فونتِ بیرونی: بیشتر برنامه‌های ایمیل تصویر را
 * نمی‌آورند و فونت را نمی‌گیرند، و ایمیلی که بی‌آن‌ها زشت شود بدتر از
 * ایمیلِ ساده است.
 */
function card({ title, lead = '', code = '', body = '', footer = '' }) {
  const codeBlock = code
    ? `<div style="margin:24px 0;text-align:center">
         <div style="display:inline-block;font-size:34px;letter-spacing:10px;font-weight:700;
                     color:#0f172a;background:#f1f5f9;border-radius:14px;padding:16px 26px;
                     font-family:monospace">${code}</div>
       </div>`
    : '';
  return `<div dir="rtl" style="background:#f8fafc;padding:28px 12px;font-family:Tahoma,Arial,sans-serif">
  <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:18px;
              border:1px solid #e2e8f0;padding:28px 24px;color:#0f172a">
    <h1 style="margin:0 0 12px;font-size:20px">${title}</h1>
    ${lead ? `<p style="margin:0 0 8px;font-size:15px;line-height:2;color:#334155">${lead}</p>` : ''}
    ${codeBlock}
    ${body ? `<div style="font-size:14px;line-height:2;color:#475569">${body}</div>` : ''}
    ${footer ? `<p style="margin:20px 0 0;font-size:12px;color:#94a3b8;line-height:2">${footer}</p>` : ''}
  </div>
</div>`;
}

module.exports = { current, save, masked, invalidate, send, card, readiness, FIELDS };
