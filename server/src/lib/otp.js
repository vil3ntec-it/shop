'use strict';
/**
 * کد یک‌بارمصرف — فقط به ایمیل.
 *
 * ⛔ «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳): `request` مقصدِ غیرِ ایمیل را پیش از هر ردیف و هر ارسال رد
 * می‌کند (۴۰۰ ِ `email_only`). کدِ پیامکی، سقف‌های SEC-06 ِ «SMS pumping» و دفترِ پیامکِ کدها با آن رفتند ⇐ به‌جایش
 * همان راهِ ایمیل با همان سقف‌ها. `senders.sms` فقط برای پیامکِ کدِ VIP ِ پمپ می‌ماند (`vip-codes.sms` ⇐ `admin-pump`؛
 * کدِ پمپ دست نمی‌خورد) و `senders.*` برای «آزمایشِ ارسال» ِ تنظیمِ همان سرویس در پنل.
 *
 * کد هرگز به شکل متن ساده ذخیره نمی‌شود — فقط HMAC آن با راز سرور.
 * هر کد: مهلت دارد، تعداد تلاش محدود دارد، بعد از یک بار استفاده باطل
 * می‌شود و ارسال دوباره‌اش محدود است.
 *
 * راه ارسال پیامک از بیرون تعیین می‌شود (OTP_PROVIDER) و با عوض کردن
 * یک متغیر محیطی به سرویس دیگری می‌رود؛ هیچ سرویسی داخل کد قفل نشده.
 */
const {
  createCipheriv, createDecipheriv, createHash, createHmac, randomBytes, randomInt, timingSafeEqual,
} = require('crypto');
const { query, one, many, newId, now } = require('../db');
const config = require('../config');
const { serverSecret } = require('./server-secret');
const settings = require('./sms-settings');
const { sectionOf } = require('./tenancy');
const { badRequest, tooMany, forbidden, upstream } = require('../middleware/errors');

function pepper() {
  return serverSecret(['otp', 'api'], 'shop-otp-pepper');
}

function hashCode(destination, code) {
  return createHmac('sha256', pepper()).update(`${destination}:${code}`).digest('hex');
}

/*
 *  ── کد کجاست ──────────────────────────────────────────────────────────
 *    `code_hash`   HMAC-SHA256(pepper, "destination:code") — برای سنجش
 *    `code_sealed` AES-256-GCM با کلیدِ مشتق از همان راز — فقط برای دو
 *                  کار: خودِ ارسال، و «نمایشِ کد به مدیر» وقتی ایمیلِ
 *                  مشتری خراب است. با مصرف یا انقضا پاک می‌شود.
 *                  **هیچ‌وقت در لاگ نمی‌آید.**
 *
 *  ⚠️ همان الگوی `login-codes.js` است و عمداً همان: دو دفترِ کد باید یک
 *  رفتار داشته باشند، وگرنه باز هم «دو حقیقت» می‌شود. فقط برچسبِ کلید
 *  جداست تا مهروموم‌های یک دفتر در دفترِ دیگر باز نشوند.
 */
function sealKey() {
  return createHash('sha256').update(`${pepper()}:otp-code-seal`).digest();
}
function seal(code) {
  const iv = randomBytes(12);
  const c = createCipheriv('aes-256-gcm', sealKey(), iv);
  const ct = Buffer.concat([c.update(String(code), 'utf8'), c.final()]);
  return `${iv.toString('base64url')}.${c.getAuthTag().toString('base64url')}.${ct.toString('base64url')}`;
}
function unseal(sealed) {
  if (!sealed) return '';
  const [iv, tag, ct] = String(sealed).split('.');
  if (!iv || !tag || !ct) return '';
  try {
    const d = createDecipheriv('aes-256-gcm', sealKey(), Buffer.from(iv, 'base64url'));
    d.setAuthTag(Buffer.from(tag, 'base64url'));
    return Buffer.concat([d.update(Buffer.from(ct, 'base64url')), d.final()]).toString('utf8');
  } catch { return ''; }
}

/** `ahmad@gmail.com` ⇒ `ah***@gmail.com` — شماره هم همین‌طور کوتاه می‌شود. */
function mask(destination) {
  const s = String(destination || '');
  const at = s.indexOf('@');
  if (at < 0) return s.length <= 4 ? '***' : `***${s.slice(-4)}`;
  const name = s.slice(0, at), host = s.slice(at + 1);
  return `${name.slice(0, name.length > 2 ? 2 : 1)}***@${host}`;
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

/**
 *  مقصد را برای لاگ کوتاه می‌کند.
 *
 *  `ali@example.com` → `a***@example.com`، و `+93792236008` → `+93***008`.
 *  آن‌قدر می‌ماند که مدیرِ سرور بفهمد کدام درخواست بوده، نه آن‌قدر که
 *  لاگ خودش یک دفترِ نشانی شود.
 */
function redact(to) {
  const s = String(to || '');
  if (s.includes('@')) {
    const [name, host] = s.split('@');
    return `${name.slice(0, 1)}***@${host}`;
  }
  return s.length > 6 ? `${s.slice(0, 3)}***${s.slice(-3)}` : '***';
}

// ---------- راه‌های ارسال ----------
//
//  هر راه یک تابع است و از متغیرهای محیطی تنظیم می‌شود. هیچ سرویسی
//  داخل کد قفل نشده و هیچ کلیدی داخل برنامه‌ی گوشی نیست — کلیدها فقط
//  اینجا روی سرور می‌مانند.

const senders = {
  /**
   *  چاپ در لاگ سرور — برای سرور خانگی بدونِ سرویس پیامک یا ایمیل.
   *
   *  ── چرا در production کد نوشته نمی‌شود ────────────────────────────
   *  این خط کدِ خام را می‌نوشت، بی هیچ شرطی. یعنی روی سرورِ واقعی — و
   *  `OTP_EMAIL_PROVIDER` **پیش‌فرضش همین `log` است** — هر کدِ ورود و
   *  هر **کدِ بازیابیِ رمز** صاف می‌رفت داخلِ لاگ. لاگ هم چیزی است که
   *  می‌چرخد: در `docker logs`، در پشتیبان، روی صفحهٔ هر کسی که به
   *  سرور دسترسی دارد. یعنی هر کسی که لاگ را می‌دید، می‌توانست رمزِ
   *  هر حسابی را عوض کند.
   *
   *  خواستهٔ صاحب مخزن هم صریح بود: «Token بازیابی نباید در Log ذخیره
   *  شود».
   *
   *  بیرون از production همان‌طور که بود می‌ماند: سرورِ توسعه و
   *  سنجه‌ها بی آن کار نمی‌کنند و آنجا کسی جز خودِ سازنده نیست.
   *
   *  ⚠️ روی سرورِ واقعی، این یعنی کد به دستِ هیچ‌کس نمی‌رسد. که درست
   *  هم هست: `log` راهِ **ارسال** نیست، جای خالیِ آن است. اگر ورود با
   *  کد یا بازیابیِ رمز لازم است، `EMAIL_API_URL` یا `SMS_API_URL` را
   *  تنظیم کنید. همین خط هم یک بار همین را می‌گوید.
   *  ──────────────────────────────────────────────────────────────────
   */
  async log(to, code) {
    if (config.env === 'production' || !config.devCodes) {
      console.warn(
        `[otp] کدی برای ${redact(to)} صادر شد ولی راهِ ارسالی تنظیم نیست، ` +
        'پس به دستِ کسی نمی‌رسد. EMAIL_API_URL یا SMS_API_URL را تنظیم کنید. ' +
        '(کد عمداً نوشته نمی‌شود — لاگ جای راز نیست.)'
      );
    } else {
      console.log(`[otp] کد ورود برای ${to}: ${code}`);
    }
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
  async email(to, code, message, { app = '', purpose = 'login' } = {}) {
    const mailer = require('./mailer');
    const cfg = await mailer.current();
    const templates = require('./mail-templates');
    /*
     *  ⛔ **کد در عنوان نمی‌آید** — عنوان در اعلانِ گوشی دیده می‌شود
     *  (خواستهٔ صاحب سامانه، ۱۴۰۵/۰۷/۱۳: «توی اعلانات کدی نباشد؛ روی ایمیل
     *  که کلیک کند، کد آن‌جا نشان داده شود»). عنوانِ پیش‌فرض همان `<title>`ِ
     *  قالبِ همان برنامه است؛ عنوانی که مدیر نوشته و `{code}` دارد، بی کد.
     */
    const custom = String(cfg.otpSubject || '').replace(/[:：\-–—]?\s*\{code\}\s*/g, ' ').replace(/\s+/g, ' ').trim();
    const subject = (purpose === 'reset' ? 'کد بازیابیِ رمز' : '')
      || custom || templates.titleOf(app) || 'کد ورود فروشگاه من';
    const text = cfg.otpTemplate
      ? cfg.otpTemplate.replace(/\{code\}/g, code)
      : `کد ورود شما در ادامه است.\n\n${code}\n\nاین کد تا ده دقیقه‌ی دیگر کار می‌کند. اگر شما درخواستش نکرده‌اید، همین ایمیل را نادیده بگیرید.`;
    /*
     *  ⛔ قالبِ ایمیل همان فایلِ خودِ صاحبِ سامانه برای همان برنامه است
     *  (`lib/mail-templates`): برای کدِ ورود و ثبت‌نام **هیچ نوشته‌ای عوض
     *  نمی‌شود**؛ فقط کدِ بازیابیِ رمز عنوانِ درستِ خودش را می‌گیرد، چون
     *  «کدِ ورود» برای رمزِ فراموش‌شده دروغ است. قالب که نبود، کارتِ ساده.
     */
    const html = templates.codeHtml(purpose === 'reset'
      ? { app, code, title: 'کد بازیابیِ رمز', lead: 'برای گذاشتنِ رمزِ تازه، این کد شش‌رقمی را در برنامه وارد کنید.' }
      : { app, code })
      || mailer.card({
        title: 'کد ورود شما',
        lead: 'این کد را در برنامه یا سایت بزنید:',
        code,
        body: 'کد تا چند دقیقه‌ی دیگر کار می‌کند و فقط یک بار.',
        footer: 'اگر شما درخواستش نکرده‌اید، این ایمیل را نادیده بگیرید — حسابی ساخته نمی‌شود.',
      });
    await mailer.send({ to, subject, text, html, fromName: templates.brandOf(app) || undefined });
    return { delivered: true, via: 'email' };
  },
};

/** ایمیل است یا شماره */
function isEmail(destination) {
  return String(destination || '').includes('@');
}

/**
 * راهِ ارسالِ کد: فقط ایمیل («فقط ایمیل»، ۱۴۰۵/۰۸/۲۳). راهِ ایمیل از پنل می‌آید، نه فقط از .env؛ تا وقتی چیزی
 * تنظیم نشده `log` است و کد فقط در لاگِ سرور می‌نشیند. مقصدِ غیرِ ایمیل ⇒ `log` (هرگز پیامک).
 */
async function sender(destination) {
  if (!isEmail(destination)) return senders.log;
  const cfg = await require('./mailer').current();
  return cfg.provider === 'log' ? senders.log : senders.email;
}

/** سقفِ روزانهٔ هر نشانی (ردیف‌های ناموفق شمرده نمی‌شوند). */
async function overDailyMax(destination, at) {
  const { n } = await one(
    `SELECT COUNT(*)::int AS n FROM otp_codes
      WHERE destination = $1 AND created_at > $2 AND via <> 'failed'`,
    [destination, at - 24 * 3600 * 1000]
  );
  return n >= config.otp.dailyMax;
}

/**
 * ساخت و فرستادن کد.
 * @param {string} destination ایمیل (هر چیزِ دیگر ⇒ ۴۰۰ ِ `email_only`)\n * @returns {{sent:boolean, expiresAt:number, resendAfter:number, devCode?:string}}
 */
async function request(destination, { purpose = 'login', ip = '', app = '' } = {}) {
  //  ⛔ «فقط ایمیل»: شماره هرگز مقصدِ کد نیست — پیش از هر پرس‌وجو، ردیف یا ارسال
  if (!isEmail(destination)) throw badRequest('کد فقط به ایمیل فرستاده می‌شود', 'email_only');
  const t = now();

  // فاصله‌ی ارسال دوباره
  const last = await one(
    `SELECT created_at FROM otp_codes WHERE destination = $1 AND purpose=$2 AND via <> 'failed'
      ORDER BY created_at DESC LIMIT 1`,
    [destination, purpose]
  );
  if (last && t - Number(last.created_at) < config.otp.resendMs) {
    const wait = Math.ceil((config.otp.resendMs - (t - Number(last.created_at))) / 1000);
    throw tooMany(`${wait} ثانیه دیگر می‌توانید کد تازه بخواهید`, 'otp_resend_wait');
  }

  // سقف روزانه برای یک نشانی
  if (await overDailyMax(destination, t)) {
    throw tooMany('امروز درخواست کد برای این نشانی زیاد بوده است', 'otp_daily_limit');
  }

  const send = await sender(destination);

  const code = randomCode(config.otp.digits);
  const expiresAt = t + config.otp.ttlMs;
  const codeRow = newId('otp');
  await query(
    `INSERT INTO otp_codes (id, purpose, destination, code_hash, code_sealed, app,
                            attempts, max_attempts, expires_at, created_at, ip)
     VALUES ($1,$2,$3,$4,$5,$6,0,$7,$8,$9,$10)`,
    [codeRow, purpose, destination, hashCode(destination, code), seal(code),
     sectionOf(app) || '', config.otp.maxAttempts, expiresAt, t, ip]
  );

  //  متنِ ساده؛ قالبِ ایمیل از `mail-templates` (همان `senders.email`)
  const message = `کد ورود شما: ${code}`;

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
    const args = [destination, code, message, { app: sectionOf(app) || '', purpose }];
    try {
      await send(...args);
    } catch (first) {
      /*
       *  ⛔ **یک بارِ دیگر، فقط برای خطای گذرا** (۱۴۰۵/۰۷/۱۷). کدِ ثبت‌نام و
       *  رمز همان لحظه فرستاده می‌شود و صف ندارد؛ یک «421 Try again later»ِ
       *  جیمیل یا یک قطعیِ کوتاهِ اینترنت یعنی «کد فرستاده نشد» — همان «بعضی
       *  وقت نه». ۵xx («این گیرنده هرگز») دوباره امتحان نمی‌شود.
       */
      if (first && first.smtpCode >= 500) throw first;
      await new Promise((r) => setTimeout(r, Number(process.env.OTP_RETRY_DELAY_MS) || 1500));
      await send(...args);
    }
  } catch (err) {
    await query('DELETE FROM otp_codes WHERE id=$1', [codeRow]);
    console.error('[otp] فرستادن کد نشد:', err.message);
    throw upstream(
      'کد به ایمیل شما فرستاده نشد. کمی بعد دوباره تلاش کنید؛ اگر باز هم نشد، سرویس ایمیل سرور تنظیم نیست.',
      'delivery_failed'
    );
  }

  /*
   *  ⛔ «ساخته شد» با «رفت» یکی نیست — و یک بار همین، کاربر را ساعت‌ها
   *  دنبالِ ایمیلی فرستاد که هیچ‌وقت فرستاده نشده بود. راهی که واقعاً به
   *  کار رفت روی همان ردیف مهر می‌خورد تا میزِ مدیر بتواند `log` را از
   *  `smtp` جدا کند.
   *
   *  ⚠️ و نشستنش اجباری نیست: اگر این `UPDATE` هم نشود، کد از قبل رفته
   *  و ثبت‌نامِ کاربر نباید به‌خاطرِ یک ستونِ گزارشی بشکند.
   */
  const via = (await require('./mailer').current()).provider;
  await query('UPDATE otp_codes SET via=$2, sent_at=$3 WHERE id=$1', [codeRow, String(via || ''), now()])
    .catch(() => {});

  //  `resendSeconds` هم می‌رود چون ساعتِ گوشی ممکن است با سرور جور نباشد.
  //  با ثانیه، برنامه لازم نیست ساعتش را با سرور تنظیم کند.
  const out = {
    sent: true,
    expiresAt,
    resendAfter: t + config.otp.resendMs,
    resendSeconds: Math.ceil(config.otp.resendMs / 1000),
  };
  //  فقط بیرون از production و فقط وقتی هیچ راه ارسالی تنظیم نشده — وگرنه
  //  کد در پاسخ HTTP برمی‌گشت و کسی که شماره‌ی دیگری را می‌زد کدش را می‌دید.
  //  SEC-AUTH-06: فقط با `NODE_ENV`ِ صریحِ development/test — نبودنش production حساب می‌شود
  if (config.env !== 'production' && config.devCodes && via === 'log') out.devCode = code;
  return out;
}

/** بررسی کد. در صورت درستی، همان لحظه باطل می‌شود. */
async function verify(destination, code, { purpose = 'login' } = {}) {
  const clean = String(code || '').replace(/\D/g, '');
  if (!clean) throw badRequest('کد را وارد کنید', 'otp_required');

  /*
   *  ⛔ **هر کدِ زندهٔ همین نشانی پذیرفته می‌شود، نه فقط تازه‌ترین.**
   *
   *  گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳): «کدِ شش‌رقمی آمد اما برنامه و سرور
   *  آن را نمی‌بینند.» ایمیل‌ها دیر می‌رسند؛ کاربری که یک بار دیگر «بفرست»
   *  زده، ایمیلِ **نخست** را زودتر باز می‌کند و همان را می‌زند — و سرور فقط
   *  تازه‌ترین را می‌سنجید و «کد درست نیست» می‌گفت. کدی که خودمان فرستاده‌ایم
   *  و هنوز وقت دارد، کدِ درست است.
   *
   *  ⚠️ سقفِ حدس زدن ضعیف نشد: هر تلاش یک خانه از سقفِ **تازه‌ترین** ردیف
   *  برمی‌دارد (همان شمارنده‌ای که پیش از این بود)، و با پذیرفته شدنِ یکی،
   *  بقیهٔ کدهای زندهٔ همان نشانی هم باطل می‌شوند.
   */
  const live = await many(
    `SELECT * FROM otp_codes
      WHERE destination=$1 AND purpose=$2 AND consumed_at IS NULL AND via <> 'failed'
      ORDER BY created_at DESC LIMIT 3`,
    [destination, purpose]
  );
  const row = live[0];
  if (!row) throw forbidden('کدی برای این شماره صادر نشده است', 'otp_not_found');
  const t0 = now();
  const fresh = live.filter((r) => Number(r.expires_at) >= t0);
  if (!fresh.length) throw forbidden('مهلت این کد تمام شده است', 'otp_expired');
  if (row.attempts >= row.max_attempts) throw forbidden('تعداد تلاش بیش از حد بود', 'otp_locked');

  //  ⛔ یک خانه از سقف، اتمی و **پیش از** سنجیدن — وگرنه درخواست‌های
  //  هم‌زمان همه از زیرِ سقف رد می‌شدند (همان قاعدهٔ `login-codes.js`).
  const claim = await one(
    'UPDATE otp_codes SET attempts = attempts + 1 WHERE id=$1 AND attempts < max_attempts RETURNING id',
    [row.id]
  );
  if (!claim) throw forbidden('تعداد تلاش بیش از حد بود', 'otp_locked');

  const actual = Buffer.from(hashCode(destination, clean), 'hex');
  const match = fresh.find((r) => {
    const expected = Buffer.from(r.code_hash, 'hex');
    return expected.length === actual.length && timingSafeEqual(expected, actual);
  });
  if (!match) throw forbidden('کد درست نیست', 'otp_wrong');

  //  ⛔ مصرف‌شده یعنی کد دیگر نه لازم است و نه باید بشود دیدش — و فقط یک
  //  بار: دو درخواستِ هم‌زمان با کدِ درست هر دو «درست» نمی‌گیرند.
  const used = await one(
    'UPDATE otp_codes SET consumed_at=$2, code_sealed=NULL WHERE id=$1 AND consumed_at IS NULL RETURNING id',
    [match.id, now()]
  );
  if (!used) throw forbidden('این کد قبلاً به کار رفته است', 'otp_used');
  //  کدهای زندهٔ دیگرِ همین نشانی دیگر به کاری نمی‌آیند
  await query(
    `UPDATE otp_codes SET consumed_at=$3, code_sealed=NULL
      WHERE destination=$1 AND purpose=$2 AND consumed_at IS NULL`,
    [destination, purpose, now()]
  );
  return true;
}

//  میزِ مدیر — «کدهای شش‌رقمی» (فهرست، ارسالِ دوبارهٔ همان کد، نمایش) — `lib/otp-desk.js`
const { listRequests, requestById, reveal, resend } = require('./otp-desk')({
  mask, unseal, senders, sender, isEmail, overDailyMax,
});

module.exports = {
  request, verify, hashCode, senders, isEmail,
  listRequests, requestById, reveal, resend, mask,
};
