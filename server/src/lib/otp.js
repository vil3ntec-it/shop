'use strict';
/**
 * کد یک‌بارمصرف ورود با شماره.
 *
 * کد هرگز به شکل متن ساده ذخیره نمی‌شود — فقط HMAC آن با راز سرور.
 * هر کد: مهلت دارد، تعداد تلاش محدود دارد، بعد از یک بار استفاده باطل
 * می‌شود و ارسال دوباره‌اش محدود است.
 *
 * راه ارسال پیامک از بیرون تعیین می‌شود (OTP_PROVIDER) و با عوض کردن
 * یک متغیر محیطی به سرویس دیگری می‌رود؛ هیچ سرویسی داخل کد قفل نشده.
 */
const { createHmac, randomInt, timingSafeEqual } = require('crypto');
const { query, one, newId, now } = require('../db');
const config = require('../config');
const settings = require('./sms-settings');
const { badRequest, tooMany, forbidden, upstream } = require('../middleware/errors');

function pepper() {
  return config.secrets.otp || config.secrets.api || 'shop-otp-pepper';
}

function hashCode(destination, code) {
  return createHmac('sha256', pepper()).update(`${destination}:${code}`).digest('hex');
}

function randomCode(digits) {
  let s = '';
  for (let i = 0; i < digits; i++) s += String(randomInt(10));
  // اولین رقم صفر نباشد تا کد کوتاه به نظر نرسد
  if (s[0] === '0') s = String(randomInt(1, 10)) + s.slice(1);
  return s;
}

/**
 * جاگذاری‌ها را با مقدارهای واقعی عوض می‌کند.
 *
 * `{to}` و `{code}` و … . برای آدرس و query، مقدارها encode می‌شوند تا
 * شماره یا متن فارسی آدرس را نشکند.
 */
function fill(text, vars, encode = false) {
  return String(text).replace(/\{(to_plain|to|code|message|sender|key)\}/g, (_, name) => {
    const value = String(vars[name] ?? '');
    return encode ? encodeURIComponent(value) : value;
  });
}

/** JSON تنظیمات را می‌خواند و جاگذاری‌ها را داخل مقدارها عوض می‌کند. */
function parseJson(text, label, vars) {
  if (!text || !String(text).trim()) return {};
  let raw;
  try {
    raw = JSON.parse(text);
  } catch {
    throw new Error(`${label} یک JSON درست نیست`);
  }
  const out = {};
  for (const [k, v] of Object.entries(raw)) {
    out[k] = typeof v === 'string' ? fill(v, vars) : v;
  }
  return out;
}

/** پاسخ سرویس را می‌سنجد و اگر خطا بود، متنش را نشان می‌دهد نه فقط شماره. */
async function check(res) {
  const body = await res.text().catch(() => '');
  if (!res.ok) {
    throw new Error(`سرویس پیامک پاسخ ${res.status} داد: ${body.slice(0, 200)}`);
  }
  //  بعضی سرویس‌ها ۲۰۰ می‌دهند و خطا را داخل بدنه می‌گذارند. اگر این را
  //  نمی‌سنجیدیم، «فرستاده شد» می‌گفتیم و پیامکی نرفته بود.
  let parsed = null;
  try { parsed = JSON.parse(body); } catch { /* بدنه‌ی متنی؛ مشکلی نیست */ }
  if (parsed && (parsed.error || parsed.Error)) {
    const detail = parsed.description || parsed.message || JSON.stringify(parsed);
    throw new Error(`سرویس پیامک قبول نکرد: ${String(detail).slice(0, 200)}`);
  }
  return { delivered: true, via: 'sms', response: body.slice(0, 200) };
}

// ---------- راه‌های ارسال ----------
//
//  هر راه یک تابع است و از متغیرهای محیطی تنظیم می‌شود. هیچ سرویسی
//  داخل کد قفل نشده و هیچ کلیدی داخل برنامه‌ی گوشی نیست — کلیدها فقط
//  اینجا روی سرور می‌مانند.

const senders = {
  /** چاپ در لاگ سرور — برای سرور خانگی بدون سرویس پیامک. */
  async log(to, code) {
    console.log(`[otp] کد ورود برای ${to}: ${code}`);
    return { delivered: true, via: 'log' };
  },

  /**
   * فرستادن به یک آدرس دلخواه (هر سرویس پیامکی که وب‌هوک دارد).
   * بدنه: { to, code, message }
   */
  async webhook(to, code, message) {
    if (!config.otp.webhookUrl) throw new Error('OTP_WEBHOOK_URL تنظیم نشده است');
    const res = await fetch(config.otp.webhookUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(config.otp.webhookKey ? { 'X-Api-Key': config.otp.webhookKey } : {}),
      },
      body: JSON.stringify({ to, code, message }),
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) throw new Error(`سرویس پیامک پاسخ ${res.status} داد`);
    return { delivered: true, via: 'webhook' };
  },

  /**
   * پیامک، با هر سرویسی.
   *
   * شکل درخواست از متغیرهای محیطی می‌آید، پس برای هر سرویس تازه لازم
   * نیست کدی نوشته یا نسخه‌ای منتشر شود: آدرس، سرآیندها و بدنه را از
   * پنل خود سرویس برمی‌دارید و در .env می‌گذارید.
   */
  async sms(to, code, message) {
    //  تنظیمات از دیتابیس می‌آید (و اگر آنجا نبود، از .env)
    const cfg = await settings.current();
    if (!cfg.url) throw new Error('نشانی سرویس پیامک تنظیم نشده است');

    //  `{to}` شماره را همان‌طور که هست می‌دهد: `+93790000000`.
    //  `{to_plain}` بدون `+` و بدون هر چیزِ غیررقم — چند سرویس (از جمله
    //  EasySendSMS) `+` را قبول نمی‌کنند و شماره را نامعتبر می‌خوانند.
    const vars = {
      to,
      to_plain: String(to).replace(/\D/g, ''),
      code,
      message,
      sender: cfg.sender,
      key: cfg.key,
    };
    const url = fill(cfg.url, vars);

    const headers = { ...parseJson(cfg.headers, 'SMS_API_HEADERS', vars) };
    const options = { method: cfg.method, headers, signal: AbortSignal.timeout(15000) };

    if (cfg.method === 'GET') {
      // بدنه در GET معنی ندارد؛ اگر داده شده باشد به آدرس چسبانده می‌شود
      const query = cfg.body ? fill(cfg.body, vars, true) : '';
      const full = query ? url + (url.includes('?') ? '&' : '?') + query.replace(/^\?/, '') : url;
      const res = await fetch(full, options);
      return check(res);
    }

    if (cfg.body) {
      headers['Content-Type'] = headers['Content-Type'] || 'application/json';
      options.body = JSON.stringify(parseJson(cfg.body, 'SMS_API_BODY', vars));
    }
    const res = await fetch(url, options);
    return check(res);
  },

  /**
   * واتساپ، با API رسمی Meta (WhatsApp Cloud API).
   *
   * از شماره‌ی واتساپ شخصی نمی‌شود کد فرستاد؛ این کار حساب Business و
   * یک قالب پیام تأییدشده می‌خواهد. متن آزاد هم فقط تا ۲۴ ساعت بعد از
   * پیام خود کاربر مجاز است، برای همین از قالب استفاده می‌شود.
   */
  async whatsapp(to, code) {
    const { token, phoneId, template, language } = config.whatsapp;
    if (!token || !phoneId) throw new Error('WHATSAPP_TOKEN یا WHATSAPP_PHONE_ID تنظیم نشده است');
    const res = await fetch(`https://graph.facebook.com/v21.0/${phoneId}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        messaging_product: 'whatsapp',
        to: String(to).replace(/[^0-9]/g, ''),
        type: 'template',
        template: {
          name: template,
          language: { code: language },
          components: [
            { type: 'body', parameters: [{ type: 'text', text: code }] },
            // قالب‌های احراز هویت واتساپ دکمه‌ی کپی دارند و کد را دوباره می‌خواهند
            { type: 'button', sub_type: 'url', index: '0', parameters: [{ type: 'text', text: code }] },
          ],
        },
      }),
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`واتساپ پاسخ ${res.status} داد: ${body.slice(0, 200)}`);
    }
    return { delivered: true, via: 'whatsapp' };
  },

  /**
   * ایمیل — از راه تنظیماتی که در برنامه‌ی مدیریت گذاشته شده.
   *
   * کارِ فرستادن در `lib/mailer` است: SMTP خودمان یا سرویسِ HTTP. اینجا
   * فقط متنِ کد ساخته می‌شود، با قالبی که در پنل قابل عوض کردن است.
   */
  async email(to, code, message) {
    const mailer = require('./mailer');
    const cfg = await mailer.current();
    const subject = (cfg.otpSubject || 'کد ورود توحید').replace(/\{code\}/g, code);
    const text = cfg.otpTemplate
      ? cfg.otpTemplate.replace(/\{code\}/g, code)
      : `کد شما: ${code}\n\nاین کد تا چند دقیقه‌ی دیگر کار می‌کند. اگر شما درخواستش نکرده‌اید، همین ایمیل را نادیده بگیرید.`;
    const html = mailer.card({
      title: 'کد ورود شما',
      lead: 'این کد را در برنامه یا سایت بزنید:',
      code,
      body: 'کد تا چند دقیقه‌ی دیگر کار می‌کند و فقط یک بار.',
      footer: 'اگر شما درخواستش نکرده‌اید، این ایمیل را نادیده بگیرید — حسابی ساخته نمی‌شود.',
    });
    await mailer.send({ to, subject, text, html });
    return { delivered: true, via: 'email' };
  },
};

/** ایمیل است یا شماره */
function isEmail(destination) {
  return String(destination || '').includes('@');
}

/**
 * راه ارسال از روی خود مقصد انتخاب می‌شود: ایمیل با سرویس ایمیل، شماره
 * با پیامک یا واتساپ. هر کدام متغیر خودش را دارد.
 */
async function sender(destination) {
  if (isEmail(destination)) {
    //  راهِ ایمیل هم مثل پیامک از پنل می‌آید، نه فقط از .env. تا وقتی
    //  چیزی تنظیم نشده باشد `log` است و کد فقط در لاگِ سرور می‌نشیند.
    const mailer = require('./mailer');
    const cfg = await mailer.current();
    return cfg.provider === 'log' ? senders.log : senders.email;
  }
  //  راه ارسالِ شماره هم از پنل مدیریت عوض می‌شود، نه فقط از .env
  const { provider } = await settings.current();
  return senders[provider] || senders.log;
}

/**
 * ساخت و فرستادن کد.
 * @param {string} destination شماره یا ایمیل\n * @returns {{sent:boolean, expiresAt:number, resendAfter:number, devCode?:string}}
 */
async function request(destination, { purpose = 'login', ip = '' } = {}) {
  const t = now();

  // فاصله‌ی ارسال دوباره
  const last = await one(
    'SELECT created_at FROM otp_codes WHERE destination=$1 AND purpose=$2 ORDER BY created_at DESC LIMIT 1',
    [destination, purpose]
  );
  if (last && t - Number(last.created_at) < config.otp.resendMs) {
    const wait = Math.ceil((config.otp.resendMs - (t - Number(last.created_at))) / 1000);
    throw tooMany(`${wait} ثانیه دیگر می‌توانید کد تازه بخواهید`, 'otp_resend_wait');
  }

  // سقف روزانه برای یک شماره
  const { n } = await one(
    'SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination=$1 AND created_at > $2',
    [destination, t - 24 * 3600 * 1000]
  );
  if (n >= config.otp.dailyMax) {
    throw tooMany('امروز درخواست کد برای این نشانی زیاد بوده است', 'otp_daily_limit');
  }

  const code = randomCode(config.otp.digits);
  const expiresAt = t + config.otp.ttlMs;
  const codeRow = newId('otp');
  await query(
    `INSERT INTO otp_codes (id, purpose, destination, code_hash, attempts, max_attempts, expires_at, created_at, ip)
     VALUES ($1,$2,$3,$4,0,$5,$6,$7,$8)`,
    [codeRow, purpose, destination, hashCode(destination, code), config.otp.maxAttempts, expiresAt, t, ip]
  );

  //  متن پیام: اگر سرویس شما قالب تأییدشده می‌خواهد، همان را در
  //  SMS_TEMPLATE بگذارید با {code} داخلش.
  const smsCfg = await settings.current();
  const message = smsCfg.template
    ? smsCfg.template.replace(/\{code\}/g, code)
    : `کد ورود شما: ${code}`;

  /*
   *  ── چرا این try اینجاست ──────────────────────────────────────────
   *  اگر تنظیماتِ ایمیل یا پیامک خراب باشد، اینجا خطا می‌داد و کاربر
   *  یک «خطای داخلی سرور» می‌دید — بی هیچ نشانی از اینکه کد اصلاً
   *  بیرون نرفته. یعنی دقیقاً همان چیزی که ثبت‌نام را بی‌صدا می‌شکست.
   *
   *  حالا: کدِ ساخته‌شده پاک می‌شود (وگرنه مهلتِ «ارسال دوباره» را
   *  می‌گرفت و کاربر تا دو دقیقه حتی نمی‌توانست دوباره تلاش کند) و
   *  خطایی برمی‌گردد که هم کاربر می‌فهمد و هم مدیر می‌داند کجا را
   *  درست کند.
   */
  try {
    await (await sender(destination))(destination, code, message);
  } catch (err) {
    await query('DELETE FROM otp_codes WHERE id=$1', [codeRow]);
    console.error('[otp] فرستادن کد نشد:', err.message);
    throw upstream(
      isEmail(destination)
        ? 'کد به ایمیل شما فرستاده نشد. کمی بعد دوباره تلاش کنید؛ اگر باز هم نشد، سرویس ایمیل سرور تنظیم نیست.'
        : 'کد فرستاده نشد. کمی بعد دوباره تلاش کنید؛ اگر باز هم نشد، سرویس پیامک سرور تنظیم نیست.',
      'delivery_failed'
    );
  }

  //  `resendSeconds` هم می‌رود چون ساعتِ گوشی ممکن است با سرور جور نباشد.
  //  با ثانیه، برنامه لازم نیست ساعتش را با سرور تنظیم کند.
  const out = {
    sent: true,
    expiresAt,
    resendAfter: t + config.otp.resendMs,
    resendSeconds: Math.ceil(config.otp.resendMs / 1000),
  };
  // فقط بیرون از حالت production و فقط وقتی راه ارسالی تنظیم نشده
  //  فقط بیرون از production و فقط وقتی هیچ راه ارسالی تنظیم نشده — وگرنه
  //  کد در پاسخ HTTP برمی‌گشت و کسی که شماره‌ی دیگری را می‌زد کدش را می‌دید.
  const via = isEmail(destination)
    ? (await require('./mailer').current()).provider
    : smsCfg.provider;
  if (config.env !== 'production' && via === 'log') out.devCode = code;
  return out;
}

/** بررسی کد. در صورت درستی، همان لحظه باطل می‌شود. */
async function verify(destination, code, { purpose = 'login' } = {}) {
  const clean = String(code || '').replace(/\D/g, '');
  if (!clean) throw badRequest('کد را وارد کنید', 'otp_required');

  const row = await one(
    `SELECT * FROM otp_codes
      WHERE destination=$1 AND purpose=$2 AND consumed_at IS NULL
      ORDER BY created_at DESC LIMIT 1`,
    [destination, purpose]
  );
  if (!row) throw forbidden('کدی برای این شماره صادر نشده است', 'otp_not_found');
  if (Number(row.expires_at) < now()) throw forbidden('مهلت این کد تمام شده است', 'otp_expired');
  if (row.attempts >= row.max_attempts) throw forbidden('تعداد تلاش بیش از حد بود', 'otp_locked');

  await query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id=$1', [row.id]);

  const expected = Buffer.from(row.code_hash, 'hex');
  const actual = Buffer.from(hashCode(destination, clean), 'hex');
  const ok = expected.length === actual.length && timingSafeEqual(expected, actual);
  if (!ok) throw forbidden('کد درست نیست', 'otp_wrong');

  await query('UPDATE otp_codes SET consumed_at=$2 WHERE id=$1', [row.id, now()]);
  return true;
}

module.exports = { request, verify, hashCode, senders, isEmail };
