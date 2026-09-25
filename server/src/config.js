'use strict';
/**
 * پیکربندی سرور — همه چیز از متغیرهای محیطی خوانده می‌شود.
 *
 * هیچ آدرس، رمز، مسیر یا نام کامپیوتری در کد نیست؛ به همین دلیل همین
 * سرور بدون تغییر روی کامپیوتر خانگی، VPS، سرور اختصاصی یا هاست
 * دارای Node.js بالا می‌آید.
 */
const path = require('path');
const fs = require('fs');

// بارگذاری ساده‌ی .env بدون وابستگی بیرونی
(function loadDotEnv() {
  const p = process.env.ENV_FILE || path.join(__dirname, '..', '.env');
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split('\n')) {
    const s = line.trim();
    if (!s || s.startsWith('#')) continue;
    const i = s.indexOf('=');
    if (i < 0) continue;
    const k = s.slice(0, i).trim();
    let v = s.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    if (process.env[k] === undefined) process.env[k] = v;
  }
})();

const ROOT = path.join(__dirname, '..');
const num  = (v, d) => (v === undefined || v === '' || Number.isNaN(Number(v)) ? d : Number(v));
const bool = (v, d) => (v === undefined || v === '' ? d : /^(1|true|yes|on)$/i.test(String(v)));
const list = (v) => String(v || '').split(',').map(s => s.trim()).filter(Boolean);

/** رشته‌ی اتصال دیتابیس؛ اگر DATABASE_URL نبود، از تکه‌های جداگانه ساخته می‌شود. */
function databaseUrl() {
  if (process.env.DATABASE_URL) return process.env.DATABASE_URL;
  const host = process.env.PGHOST || '127.0.0.1';
  const port = process.env.PGPORT || '5432';
  const user = process.env.PGUSER || 'shop';
  const pass = process.env.PGPASSWORD || '';
  const name = process.env.PGDATABASE || 'shop';
  const auth = pass ? `${encodeURIComponent(user)}:${encodeURIComponent(pass)}` : encodeURIComponent(user);
  return `postgres://${auth}@${host}:${port}/${name}`;
}

const config = {
  env:  process.env.NODE_ENV || 'development',
  host: process.env.HOST || '0.0.0.0',
  port: num(process.env.PORT, 3000),

  /** آدرسی که برنامه‌ها با آن به این سرور می‌رسند — فقط برای نمایش و لینک‌ها. */
  serverUrl: process.env.SERVER_URL || '',

  db: {
    url:              databaseUrl(),
    poolMax:          num(process.env.PG_POOL_MAX, 10),
    idleTimeoutMs:    num(process.env.PG_IDLE_TIMEOUT_MS, 30000),
    connectTimeoutMs: num(process.env.PG_CONNECT_TIMEOUT_MS, 10000),
    ssl:              bool(process.env.PGSSL, false),
  },

  /** رازها — بدون این‌ها سرور در حالت production بالا نمی‌آید. */
  secrets: {
    api:  process.env.API_SECRET || '',
    jwt:  process.env.JWT_SECRET || '',
    otp:  process.env.OTP_SECRET || '',
  },

  tokens: {
    accessTtlMs:  num(process.env.ACCESS_TOKEN_TTL_MIN, 60) * 60 * 1000,
    refreshTtlMs: num(process.env.REFRESH_TOKEN_TTL_DAYS, 90) * 24 * 60 * 60 * 1000,
    adminTtlMs:   num(process.env.ADMIN_TOKEN_TTL_HOURS, 12) * 60 * 60 * 1000,
  },

  otp: {
    //  راه فرستادن کد برای «شماره»: log | webhook | whatsapp
    provider:   process.env.OTP_PROVIDER || 'log',
    //  راه فرستادن کد برای «ایمیل»: log | email
    emailProvider: process.env.OTP_EMAIL_PROVIDER || 'log',
    webhookUrl: process.env.OTP_WEBHOOK_URL || '',
    webhookKey: process.env.OTP_WEBHOOK_KEY || '',
    digits:     num(process.env.OTP_DIGITS, 6),
    ttlMs:      num(process.env.OTP_TTL_MIN, 10) * 60 * 1000,   // ⚠️ ده دقیقه — همان که قالبِ ایمیل می‌گوید، و ایمیل دیر هم می‌رسد
    maxAttempts: num(process.env.OTP_MAX_ATTEMPTS, 5),
    //  دو دقیقه. هر بار «ارسال دوباره» یک پیامک است و پول دارد؛ ۶۰ ثانیه
    //  آن‌قدر کوتاه بود که کاربرِ بی‌حوصله سه بار می‌زد.
    resendMs:   num(process.env.OTP_RESEND_SECONDS, 120) * 1000,
    dailyMax:   num(process.env.OTP_DAILY_MAX, 20),
  },

  /*
   *  سرویس پیامک — هر سرویسی، بدون عوض کردن کد.
   *
   *  هر سرویس پیامکی شکل درخواست خودش را دارد: یکی کلید را در سرآیند
   *  می‌خواهد، یکی در بدنه، یکی اصلاً GET است. به‌جای نوشتن کد برای هر
   *  کدام، شکل درخواست از همین‌جا تنظیم می‌شود.
   *
   *  در URL و سرآیندها و بدنه، این جاگذاری‌ها جایگزین می‌شوند:
   *    {to}       شماره‌ی گیرنده
   *    {code}     کد شش‌رقمی
   *    {message}  متن کامل پیام
   *    {sender}   شماره‌ی فرستنده (خط خدماتی شما)
   *    {key}      کلید API
   *
   *  کلید فقط اینجا روی سرور می‌ماند و هرگز داخل برنامه‌ی گوشی نمی‌رود.
   */
  sms: {
    url:      process.env.SMS_API_URL || '',
    method:   (process.env.SMS_API_METHOD || 'POST').toUpperCase(),
    key:      process.env.SMS_API_KEY || '',
    sender:   process.env.SMS_SENDER || '',
    //  JSON: مثلاً {"Authorization":"Bearer {key}"}
    headers:  process.env.SMS_API_HEADERS || '',
    //  JSON برای POST، یا رشته‌ی query برای GET
    body:     process.env.SMS_API_BODY || '',
    //  متن پیام؛ اگر خالی باشد متن پیش‌فرض می‌رود
    template: process.env.SMS_TEMPLATE || '',
  },

  //  واتساپ — API رسمی Meta. کلیدها فقط اینجا می‌مانند و هرگز داخل
  //  برنامه‌ی گوشی نمی‌روند.
  whatsapp: {
    token:    process.env.WHATSAPP_TOKEN || '',
    phoneId:  process.env.WHATSAPP_PHONE_ID || '',
    template: process.env.WHATSAPP_TEMPLATE || 'otp_login',
    language: process.env.WHATSAPP_LANG || 'fa',
  },

  //  ایمیل — هر سرویسی که API با کلید دارد
  email: {
    url:     process.env.EMAIL_API_URL || '',
    key:     process.env.EMAIL_API_KEY || '',
    from:    process.env.EMAIL_FROM || '',
    //  ⚠️ خالی = عنوانِ قالبِ همان برنامه («کد ورود ویلن»، «کد ورود VILL3N Shop») — lib/otp.js
    subject: process.env.EMAIL_SUBJECT || '',
  },

  google: {
    //  همه‌ی شناسه‌های پذیرفته‌شده — سنجشِ توکن هر کدام را قبول می‌کند.
    //  شناسه‌ی Desktop هم این‌جاست، وگرنه توکنی که خودِ ما خواستیم
    //  در سنجش رد می‌شد (‎aud‎ آن با هیچ‌کدام نمی‌خواند).
    clientIds: [
      ...list(process.env.GOOGLE_CLIENT_ID),
      ...list(process.env.GOOGLE_DESKTOP_CLIENT_ID),
    ].filter((id, i, all) => all.indexOf(id) === i),
    //  ⚠️ شناسه‌ی «برنامه‌ی کامپیوتر» جداست و باید جدا بماند.
    //  گوگل برای برنامه‌ی نصبی نوعِ Desktop می‌خواهد (ورود روی
    //  127.0.0.1 با PKCE)؛ شناسه‌ی Web آن‌جا کار نمی‌کند. پس
    //  ‎/config‎ باید هر کدام را به صاحبِ خودش بدهد، وگرنه برنامه‌ی
    //  کامپیوتر شناسه‌ی سایت را برمی‌دارد و ورود با خطای
    //  ‎invalid_client‎ می‌افتد.
    desktopClientId: (process.env.GOOGLE_DESKTOP_CLIENT_ID || '').trim(),
    certsUrl:  process.env.GOOGLE_CERTS_URL || 'https://www.googleapis.com/oauth2/v3/certs',
  },

  backup: {
    dir:        process.env.BACKUP_PATH || path.join(ROOT, 'data', 'backups'),
    keepDaily:  num(process.env.BACKUP_KEEP_DAILY, 14),
    keepWeekly: num(process.env.BACKUP_KEEP_WEEKLY, 8),
    keepMonthly:num(process.env.BACKUP_KEEP_MONTHLY, 12),
    intervalMs: num(process.env.BACKUP_INTERVAL_HOURS, 24) * 60 * 60 * 1000,
    enabled:    bool(process.env.BACKUP_ENABLED, true),
    passphrase: process.env.BACKUP_PASSPHRASE || '',   // اگر پر باشد، پشتیبان رمز می‌شود
    pgDump:     process.env.PG_DUMP_BIN || 'pg_dump',
    pgRestore:  process.env.PSQL_BIN || 'psql',

    /*
     *  پشتیبانِ **هر حساب** — چیزی جدا از `pg_dump`ِ بالا.
     *
     *  بالایی مالِ صاحبِ سامانه است و کلِ دیتابیس را می‌گیرد. این یکی
     *  مالِ خودِ دکان‌دار و پمپ‌دار است: فایلِ خودش، پوشهٔ خودش.
     *
     *  ⚠️ **سهم دو پله دارد و این عمدی است.** دیسک پول است و حسابی که
     *  هیچ‌وقت اشتراک نخریده نباید بتواند چند گیگابایت از آن را بگیرد.
     *  ولی بستنِ کاملش هم غلط است — همان کسی که اشتراک ندارد، بیشتر
     *  از همه ممکن است گوشی‌اش را گم کند. پس پلهٔ رایگان کوچک است،
     *  نه صفر. `Tier` در `lib/account-backups.js` تصمیم می‌گیرد.
     */
    account: {
      //  بزرگ‌ترین فایلی که یک بار فرستاده می‌شود
      maxBytes:   num(process.env.BACKUP_ACCOUNT_MAX_MB, 64) * 1024 * 1024,
      //  حسابِ بی‌اشتراک
      freeKeep:   num(process.env.BACKUP_ACCOUNT_FREE_KEEP, 3),
      freeBytes:  num(process.env.BACKUP_ACCOUNT_FREE_MB, 32) * 1024 * 1024,
      //  حسابی که اشتراک یا دورهٔ آزمایشی دارد
      paidKeep:   num(process.env.BACKUP_ACCOUNT_KEEP, 20),
      paidBytes:  num(process.env.BACKUP_ACCOUNT_MB, 1024) * 1024 * 1024,
    },
  },

  rateLimit: {
    windowMs:     num(process.env.RATE_WINDOW_MS, 15 * 60 * 1000),
    authMax:      num(process.env.RATE_AUTH_MAX, 10),
    otpMax:       num(process.env.RATE_OTP_MAX, 5),
    joinMax:      num(process.env.RATE_JOIN_MAX, 10),
    //  تازه‌سازیِ نشست و خروج. بازتر از ورود چون کارِ روزمره‌ی هر گوشی
    //  است (ساعتی یک بار) و یک دکانِ چندگوشی‌ای پشتِ یک اینترنت
    //  نباید همدیگر را ببندند.
    sessionMax:   num(process.env.RATE_SESSION_MAX, 120),
    //  پیامِ همگانی — عمداً تنگ: یک اشتباه نباید هزار پیام بفرستد.
    //  از محیط خوانده می‌شود تا آزمون‌ها بتوانند چند بار بزنند و
    //  خودِ سقف هم جدا سنجیده شود.
    broadcastMax: num(process.env.RATE_BROADCAST_MAX, 5),
    generalMax:   num(process.env.RATE_GENERAL_MAX, 600),
    adminMax:     num(process.env.RATE_ADMIN_MAX, 60),
    //  تلاشِ ناموفق از **یک IP** روی یک حساب — کسی که رمز را حدس
    //  می‌زند بسته می‌شود، و فقط خودش
    lockoutTries: num(process.env.LOGIN_LOCKOUT_TRIES, 8),
    /*
     *  تلاشِ ناموفق روی یک حساب از **همه‌ی** IPها.
     *
     *  پشتوانه‌ی حمله‌ی پخش‌شده. عمداً خیلی بالاتر از بالایی است، چون
     *  همین شمارنده است که می‌تواند علیهِ صاحبِ حساب به کار رود: تا
     *  دیروز تنها شمارنده بود و هشت رمزِ غلط از یک غریبه، حسابِ یک
     *  نفرِ دیگر را یک ربع می‌بست.
     *
     *  رسیدن به چهل از یک IP ممکن نیست — `authMax` (ده در ربع ساعت)
     *  زودتر جلویش را می‌گیرد. پس چهل یعنی چند IP هماهنگ.
     */
    lockoutGlobalTries: num(process.env.LOGIN_LOCKOUT_GLOBAL_TRIES, 40),
    lockoutMs:    num(process.env.LOGIN_LOCKOUT_MIN, 15) * 60 * 1000,
  },

  // CORS_ORIGIN چند دامنه را با کاما می‌پذیرد. برنامه‌ی اندروید به CORS
  // نیازی ندارد؛ این فقط برای نسخه‌ی وب است.
  /**
   * باتِ تلگرامِ پمپ (`lib/telegram.js`).
   *
   * ⚠️ رمزِ بات معمولاً از پنلِ مدیر می‌آید (رمزشده در `app_config`)؛
   * `TELEGRAM_BOT_TOKEN` در محیط، اگر باشد، جلوتر است — برای استقرارِ داکر.
   * ⛔ رمزِ بات هیچ‌وقت داخلِ برنامهٔ کامپیوتر یا گوشی نیست: هر کسی آن را
   * از فایلِ برنامه بیرون می‌کشید و از طرفِ پمپ پیام می‌داد.
   */
  telegram: {
    token:   process.env.TELEGRAM_BOT_TOKEN || '',
    apiBase: (process.env.TELEGRAM_API_BASE || 'https://api.telegram.org').replace(/\/$/, ''),
    //  دامنهٔ خودِ پمپ — لینکِ فایلِ نصبِ اندروید و صفحهٔ آیفون از همین
    //  ساخته می‌شود، همان که `KarLink.ApkUrl`/`IphoneUrl`ِ برنامهٔ پمپ دارد.
    pumpSite: (process.env.PUMP_SITE_URL || 'https://yaqobipump.top').replace(/\/$/, ''),
    //  در آزمون خاموش است: حلقهٔ گرفتنِ پیام نباید به اینترنت برود.
    autoStart: bool(process.env.TELEGRAM_AUTOSTART, (process.env.NODE_ENV || '') !== 'test'),
  },

  corsOrigins: list(process.env.CORS_ORIGIN || process.env.CORS_ORIGINS),

  allowRegistration: bool(process.env.ALLOW_REGISTRATION, true),
  trustProxy:        bool(process.env.TRUST_PROXY, false),
  defaults: {
    timezone:  process.env.DEFAULT_TIMEZONE || 'Asia/Kabul',
    trialDays: num(process.env.TRIAL_DAYS, 7),
  },
};

/** بررسی پیکربندی هنگام راه‌اندازی — بهتر است سرور بالا نیاید تا ناامن کار کند. */
function validate() {
  const problems = [];
  if (!config.db.url) problems.push('DATABASE_URL تنظیم نشده است');
  if (config.env === 'production') {
    if (!config.secrets.api || config.secrets.api.length < 24) {
      problems.push('API_SECRET باید حداقل ۲۴ کاراکتر باشد');
    }
    if (!config.secrets.otp || config.secrets.otp.length < 24) {
      problems.push('OTP_SECRET باید حداقل ۲۴ کاراکتر باشد');
    }
  }
  return problems;
}

module.exports = config;
module.exports.validate = validate;
