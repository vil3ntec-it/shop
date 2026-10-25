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
    /*
     *  تأییدِ دوباره برای کارهای حساس (بندِ ۲۱.۱) — `lib/reauth.js`.
     *  پنجره: چند دقیقه پس از ورود یا تأییدِ دوباره، همان نشست «تازه» است.
     *  `AUTH_REAUTH=off` کلیدِ بازگشت است (رفتارِ پیش از این تغییر).
     */
    reauthWindowMs: num(process.env.REAUTH_WINDOW_MIN, 10) * 60 * 1000,
    reauthEnforced: bool(process.env.AUTH_REAUTH, true),
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
    /*
     *  ⛔ F0-16 — تأخیرِ یکسانِ `password/forgot`. حسابِ موجود ایمیل می‌فرستد
     *  و ناموجود نه؛ بی این کف، زمانِ پاسخ (۴۰۰ میلی‌ثانیه در برابرِ ۳) لو
     *  می‌داد کدام ایمیل حساب دارد. پاسخِ هر دو تا این‌قدر صبر می‌کند؛ ایمیلی
     *  که کندتر برود پشتِ پاسخ ادامه می‌دهد. ۰ = بی کف (فقط برای آزمون).
     */
    uniformMs:  num(process.env.OTP_UNIFORM_MS, 1500),
    /*
     *  SEC-06 — سقفِ هزینه و ضدِ SMS pumping (`lib/otp.js`)؛ فقط پیامکِ واقعی.
     *  شمارهٔ «آشنا» (پیش‌تر کدی را درست زده یا مشتریِ ویترین است) از سقفِ IP و
     *  بلوک معاف است، سقفِ خارجیِ جدا و بالاتر دارد، و فقط knownExtra پیامک بیش
     *  از dailyMax (آشنا شدن با یک کد ارزان است؛ سهمش کوچک می‌ماند).
     */
    smsGuard: {
      ipPhones:      num(process.env.OTP_SMS_IP_PHONES, 10),        // شمارهٔ ناآشنای جدا از یک IP در ساعت
      blockPhones:   num(process.env.OTP_SMS_BLOCK_PHONES, 10),     // در یک بلوکِ هزارتایی در ساعت
      foreignPhones: num(process.env.OTP_SMS_FOREIGN_PHONES, 20),   // شمارهٔ خارجیِ ناآشنای جدا در ساعت، کلِ سرور
      knownForeignPhones: num(process.env.OTP_SMS_KNOWN_FOREIGN_PHONES, 200), // همان برای آشنا
      dailyMax:      num(process.env.OTP_SMS_DAILY_MAX, 2000),      // پیامکِ واقعی در ۲۴ ساعت، کلِ سرور
      knownExtra:    num(process.env.OTP_SMS_KNOWN_EXTRA, 500),     // سهمِ افزودهٔ شمارهٔ آشنا بر dailyMax
    },
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

  //  هشدارِ ورود از دستگاه/شبکهٔ تازه (بندِ ۲۱.۱) — `lib/login-alerts.js`؛
  //  `LOGIN_ALERTS=off` کلیدِ بازگشت است
  loginAlerts: bool(process.env.LOGIN_ALERTS, true),
  //  حسابِ تازه‌ساخته در این چند دقیقه دستگاه‌هایش را راه می‌اندازد — هشدار نه
  loginAlertGraceMs: num(process.env.LOGIN_ALERT_GRACE_MIN, 10) * 60 * 1000,

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
    //  ⛔ بندِ ۲۹.۲ قاعدهٔ ۵: گوگل فقط با اثباتِ روشِ قبلیِ حساب وصل می‌شود.
    //  `1` فقط کلیدِ بازگشتِ اضطراری به وصلِ خودکار با ایمیلِ تأییدشده است.
    legacyEmailLink: bool(process.env.GOOGLE_EMAIL_AUTOLINK, false),
  },

  /*
   *  توزیعِ نسخه‌ها — برنامه‌ها نسخهٔ تازه را از **همین سرور** می‌گیرند، نه از
   *  GitHub. سرور از انتشارِ مخزن می‌خواند (با `GITHUB_TOKEN` اگر مخزن خصوصی
   *  باشد) و فایل‌ها را روی دیسکِ خودش نگه می‌دارد. توکن هرگز به برنامه نمی‌رسد.
   */
  releases: {
    repo:    (process.env.RELEASE_REPO || 'vil3ntec-it/shop').trim(),
    token:   (process.env.GITHUB_TOKEN || process.env.RELEASE_GITHUB_TOKEN || '').trim(),
    dir:     process.env.RELEASE_CACHE_PATH || path.join(ROOT, 'data', 'releases'),
    ttlMs:   num(process.env.RELEASE_CACHE_MINUTES, 10) * 60 * 1000,
    apiBase: process.env.RELEASE_API_BASE || 'https://api.github.com',
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
    //  کدِ وصلِ گوگل (`/auth/google/link-code`) به‌ازای «ایمیلِ توکن + IP».
    //  کلیدِ `otpMax` از بدنه می‌آید و این مسیر ایمیلی در بدنه ندارد، پس
    //  تا امروز فقط به‌ازای IP شمرده می‌شد.
    linkCodeMax:  num(process.env.RATE_LINK_CODE_MAX, 3),
    //  ⛔ F0-16 — `register/start` و `password/forgot` به‌ازای هر **IP**. سقفِ
    //  `otpMax` به‌ازای هر ایمیل است، پس امتحانِ هزار ایمیلِ گوناگون از یک
    //  جا هیچ سقفی نداشت. سی در ربع ساعت برای یک کافی‌نت هم بس است.
    probeIpMax:   num(process.env.RATE_PROBE_IP_MAX, 30),
    /*
     *  زدنِ کد (تأییدِ ثبت‌نام، رمزِ تازه، ورود با کد) — **جدا از فرستادن**.
     *
     *  ⛔ تا ۲.۱۰.۵ همان سطلِ `otpMax` (پنج در ربع ساعت) را می‌خورد، پس
     *  کاربری که ثبت‌نام کرده بود و بعد رمزش را بازیابی می‌کرد، سرِ زدنِ کد
     *  «تلاشِ زیاد» می‌گرفت (سنجهٔ `linkstates`ِ برنامهٔ پمپ روی سرورِ
     *  واقعی). `otpMax` برای **ایمیلِ فرستاده** است، نه برای کدِ زده.
     *  حدس زدنِ کد همچنان بسته است: هر کد سقفِ تلاشِ خودش را دارد
     *  (`OTP_MAX_ATTEMPTS`) و قفلِ حساب (`lockoutTries`) سرِ جایش است.
     */
    verifyMax:    num(process.env.RATE_VERIFY_MAX, 20),
    joinMax:      num(process.env.RATE_JOIN_MAX, 10),
    //  تازه‌سازیِ نشست و خروج. بازتر از ورود چون کارِ روزمره‌ی هر گوشی
    //  است (ساعتی یک بار) و یک دکانِ چندگوشی‌ای پشتِ یک اینترنت
    //  نباید همدیگر را ببندند.
    sessionMax:   num(process.env.RATE_SESSION_MAX, 120),
    //  سنجیدنِ رمز با یک توکنِ باز (`/auth/password`، `/auth/reauth`) — به‌ازای
    //  **هر نشست**، نه هر IP (بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷). ده در پنجره برای
    //  آدمِ واقعی بس است؛ حدس زدن را از یک توکنِ دزدیده کند می‌کند.
    passwordSessionMax: num(process.env.RATE_PASSWORD_SESSION_MAX, 10),
    //  پیامِ همگانی — عمداً تنگ: یک اشتباه نباید هزار پیام بفرستد.
    //  از محیط خوانده می‌شود تا آزمون‌ها بتوانند چند بار بزنند و
    //  خودِ سقف هم جدا سنجیده شود.
    broadcastMax: num(process.env.RATE_BROADCAST_MAX, 5),
    generalMax:   num(process.env.RATE_GENERAL_MAX, 600),
    /*
     *  همگام‌سازی (‎/api/sync/v1/*‎) — **سطلِ خودش، به‌ازای هر حساب**.
     *
     *  ⛔ تا ۲.۱۱.۸ همان سطلِ `generalMax`ِ هر آی‌پی را می‌خورد (۶۰۰ در ربع
     *  ساعت، یعنی چهل در دقیقه). سنجهٔ `tensync`ِ برنامهٔ پمپ (۱۴۰۵/۰۷/۱۴)
     *  نشان داد «بارِ اولِ» یک دفترِ ده‌ساله ~۱٬۶۵۰ دسته است: پمپ چهل
     *  دقیقه پشتِ «تلاشِ زیاد» می‌ماند، و چون سطل مالِ **آی‌پی** بود،
     *  مجوز و اپِ کارمندان و هر دستگاهِ دیگرِ همان اینترنت هم با آن بسته
     *  می‌شدند. حالا همگام‌سازی سطلِ خودش را دارد و بقیه دست نمی‌خورند.
     */
    syncMax:      num(process.env.RATE_SYNC_MAX, 3000),
    //  پشتوانه برای درخواستِ **بی توکن** به همان مسیر — به‌ازای آی‌پی
    syncIpMax:    num(process.env.RATE_SYNC_IP_MAX, 6000),
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
    /*
     *  ثبتِ سفارش از ویترین (‎POST /api/store/:slug/checkout‎) — به‌ازای IP.
     *  خواندنِ ویترین همان `generalMax` را با سطلِ خودش دارد (مثلِ کیو‌آرِ
     *  پمپ). ⚠️ پشتِ تونل و بی `TRUST_PROXY` همهٔ مشتری‌ها یک IP‌اند.
     */
    storeCheckoutMax: num(process.env.RATE_STORE_CHECKOUT_MAX, 20),
    /*
     *  «این پین در محدوده است؟» (‎GET /api/store/:slug/delivery‎) — به‌ازای
     *  (IP، ویترین) در **۲۴ ساعت**. پاسخ خودش با خانهٔ ۳۰۰ متریِ شبکه است؛
     *  این سقف جمع کردنِ همهٔ خانه‌های لبه برای برازشِ مرکز را هم کند می‌کند.
     */
    storeLocateDailyMax: num(process.env.RATE_STORE_LOCATE_DAILY_MAX, 60),
    //  بازارچه (`/api/market/*`): خواندن سطلِ خودش را با `generalMax` دارد؛
    //  جست‌وجوی کالا در همهٔ دکان‌ها گران‌تر است ⇒ سطلِ تنگ‌ترِ خودش، به‌ازای IP.
    //  بازبینیِ امنیتی سقفِ ضربه‌ای در دقیقه گذاشت. ⚠️ اپراتورهای موبایل (CGNAT) صدها
    //  مشتری را پشتِ یک IP می‌برند؛ ۱۰ در دقیقه و ۶۰ در ربع ساعت یعنی مشتریِ عادی ۴۲۹ می‌گرفت.
    //  حالا ۳۰ در دقیقه و ۳۰۰ در ربع ساعت (میانگینِ ۲۰ در دقیقه). سپرِ دیتابیس فقط این سقف
    //  نیست: برشِ یک‌پروازِ دکان‌ها (eligibleCacheMs)، نمایهٔ باریکِ market_search، دست‌کم دو
    //  حرف و سقفِ نتیجه سرِ جایشان‌اند.
    marketSearchMax: num(process.env.RATE_MARKET_SEARCH_MAX, 300),
    marketSearchBurstMax: num(process.env.RATE_MARKET_SEARCH_BURST_MAX, 30),
    //  خانهٔ بازارچه (`/api/market/products`): همان ۳۰ در دقیقه، سطلِ جدا
    marketFeedBurstMax: num(process.env.RATE_MARKET_FEED_BURST_MAX, 30),
    //  فاکتورِ عمومی (QRِ هر فاکتور، `/api/receipt`) به‌ازای IP: ربع‌ساعته و ضربه‌ای در دقیقه.
    //  سقفِ ربع‌ساعته گشاد است: مشتری‌های پشتِ NAT ِ اپراتور یک IP دارند؛ ضربه‌ای تنگ می‌ماند
    receiptReadMax: num(process.env.RATE_RECEIPT_READ_MAX, 300),
    receiptReadBurstMax: num(process.env.RATE_RECEIPT_READ_BURST_MAX, 10),
    //  بارگذاریِ لوگوی فاکتور به‌ازای دکان در شبانه‌روز (لوگوی پیشین پاک می‌شود؛ این سقف بارِ دیسک را می‌بندد)
    receiptLogoDailyMax: num(process.env.RATE_RECEIPT_LOGO_DAILY_MAX, 20),
  },

  /**
   * ویترینِ اینترنتی (فاز ۴، D-06: `{slug}.vill3n.top`).
   * درخواستی که میزبانش `<slug>.<domain>` است و `slug`ش رزرو نیست، در
   * ریشه صفحهٔ ویترین را می‌گیرد. خالی ⇒ فقط نشانیِ `/store/<slug>`.
   */
  storefront: {
    domain: String(process.env.STOREFRONT_DOMAIN ?? 'vill3n.top').trim().toLowerCase().replace(/^\.+|\.+$/g, ''),
    /*
     *  ۳۱.۲ بندِ ۴ — «اتصالِ تأییدشده» از آخرین تماسِ دستگاه‌های دکان:
     *  کهنه‌تر از `staleMs` ⇒ «اتصال تأیید نشده»؛ کهنه‌تر از `offlineMs` ⇒
     *  «موقتاً آفلاین». (N ِ D-18 تا تصمیمِ صاحب محصول: همین ۶۰ دقیقه.)
     */
    staleMs:   num(process.env.STORE_PRESENCE_STALE_MIN, 10) * 60 * 1000,
    offlineMs: num(process.env.STORE_PRESENCE_OFFLINE_MIN, 60) * 60 * 1000,
    //  ۲۳.۴ — شمارهٔ تأییدشدهٔ مشتری چند روز بی کدِ تازه می‌ماند
    ticketTtlMs: num(process.env.STORE_CUSTOMER_TICKET_DAYS, 14) * 24 * 60 * 60 * 1000,
    //  ساختن/ورود به حسابِ مشتری فقط با بلیتی که همین چند دقیقه پیش صادر شده
    accountTicketMaxMs: num(process.env.STORE_ACCOUNT_TICKET_MAX_MIN, 10) * 60 * 1000,
    //  CHK-02 — نشستِ حسابِ مشتری (لغوشدنی) چند روز می‌ماند
    customerSessionDays: num(process.env.STORE_CUSTOMER_SESSION_DAYS, 180),
  },

  /**
   * بازارچه (`lib/market.js`، پرچمِ `market.directory`). نتیجهٔ دروازهٔ هر دکان
   * (پرچم‌ها و اشتراک) این‌قدر در حافظه می‌ماند؛ «روشن»، «فعال» و «در فهرست»
   * همیشه زنده خوانده می‌شوند. ۰ ⇒ بی حافظه.
   */
  market: {
    gateCacheMs: num(process.env.MARKET_GATE_CACHE_MS, 30 * 1000),
    //  «برشِ» دکان‌های در فهرست (شمارِ کالا، دیدهٔ دستگاه، نمایهٔ جست‌وجو) — یک‌پرواز.
    //  «روشن»، «فعال» و «در فهرست» باز هم در هر درخواست زنده سنجیده می‌شوند.
    eligibleCacheMs: num(process.env.MARKET_ELIGIBLE_CACHE_MS, 30 * 1000),
    //  خانهٔ بازارچه: «قفسهٔ» کالاهای هر دکان و «پرفروش/ارزان‌تر» ِ سراسری
    feedCacheMs: num(process.env.MARKET_FEED_CACHE_MS, 60 * 1000),
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

  /**
   * چتِ مشتری ↔ دکان (v3 §۳۳) — پشتِ پرچمِ `shop.chat`، پیش‌فرض خاموش.
   *
   * سقفِ رسانه: برنامه پیش از فرستادن عکس را کوچک و فشرده می‌کند (سرور
   * کتابخانهٔ تصویر ندارد ⇒ فقط بایت‌ها را می‌شناسد، EXIF ِ مکان را می‌برد
   * و سقف را می‌سنجد). پیش‌نمایش (`thumb`) را هم خودِ برنامه می‌سازد.
   * ⚠️ پنجرهٔ ویرایش/حذف برای همه و مدتِ نگهداری تصمیمِ بازِ صاحب محصول‌اند
   * (سند عدد نداده): این‌ها مقدارِ موقت‌اند و `retentionDays = 0` یعنی
   * «سرور خودش چیزی پاک نمی‌کند» تا تصمیم گرفته شود.
   */
  shopChat: {
    imageMax:       num(process.env.CHAT_IMAGE_MAX_KB, 1536) * 1024,
    thumbMax:       num(process.env.CHAT_THUMB_MAX_KB, 64) * 1024,
    audioMax:       num(process.env.CHAT_AUDIO_MAX_KB, 3072) * 1024,
    fileMax:        num(process.env.CHAT_FILE_MAX_KB, 5120) * 1024,
    editWindowMs:   num(process.env.CHAT_EDIT_WINDOW_MIN, 15) * 60 * 1000,
    deleteWindowMs: num(process.env.CHAT_DELETE_WINDOW_MIN, 1440) * 60 * 1000,
    retentionDays:  num(process.env.CHAT_SHOP_RETENTION_DAYS, 0),
    //  به‌ازای هر فرستنده (مشتری: دکان+شماره، کارمند: کاربر) در پنجرهٔ `rateLimit.windowMs`
    sendMax:        num(process.env.RATE_CHAT_SEND_MAX, 300),
    uploadMax:      num(process.env.RATE_CHAT_UPLOAD_MAX, 60),
  },

  //  اعتماد، شکایت و تخلفِ بازارچه (v3 §۳۸.۶) — پشتِ پرچمِ `market.trust`، پیش‌فرض خاموش.
  trust: {
    //  سقفِ ماندگار (در دیتابیس، پس از راه‌اندازیِ دوباره هم سر جاست) در ۲۴ ساعت
    dailyPerPhone:  num(process.env.TRUST_DAILY_PER_PHONE, 5),
    dailyPerIp:     num(process.env.TRUST_DAILY_PER_IP, 20),
    //  سقفِ حافظه‌ای در پنجرهٔ `rateLimit.windowMs`
    fileMax:        num(process.env.RATE_TRUST_FILE_MAX, 10),
    fileIpMax:      num(process.env.RATE_TRUST_FILE_IP_MAX, 30),
    uploadMax:      num(process.env.RATE_TRUST_UPLOAD_MAX, 30),
    imageMax:       num(process.env.TRUST_IMAGE_MAX_KB, 1536) * 1024,
    evidenceMax:    num(process.env.TRUST_EVIDENCE_MAX, 5),
    appealDays:     num(process.env.TRUST_APPEAL_DAYS, 14),
  },

  corsOrigins: list(process.env.CORS_ORIGIN || process.env.CORS_ORIGINS),

  allowRegistration: bool(process.env.ALLOW_REGISTRATION, true),
  //  ⛔ F0-16 — راهِ برگشتِ سازگاری: `1` همان ۴۰۹ِ `already_registered` را در
  //  پلهٔ اولِ ثبت‌نام برمی‌گرداند (رفتارِ پیش از 2.14.2). پیش‌فرض خاموش.
  signupLegacyConflict: bool(process.env.SIGNUP_LEGACY_CONFLICT, false),
  trustProxy:        bool(process.env.TRUST_PROXY, false),
  defaults: {
    timezone:  process.env.DEFAULT_TIMEZONE || 'Asia/Kabul',
    //  ⛔ F0-13: `TRIAL_DAYS` برداشته شد — هیچ جا خوانده نمی‌شد و فقط عددِ دومی
    //  (۷ این‌جا، ۱۴ در docker-compose) نشان می‌داد. مدتِ آزمایشی در
    //  `app_config.trial_days` است (پنلِ مدیر)؛ پیش‌فرضش `plans.TRIAL_DEFAULT`.
  },
  /*
   *  Sync v3 (بخشِ ۳.۸) — ⛔ پیش‌فرض خاموش. خاموش یعنی `/api/sync/v3/*` همان
   *  ۴۰۴ِ سرورِ قدیمی را می‌دهد و برنامه روی v1 می‌ماند؛ v1 و پمپ هیچ‌وقت
   *  دست نمی‌خورند. روشن کردن فقط «در دسترس» می‌کند؛ برنامه خودش انتخاب می‌کند.
   */
  syncV3: {
    enabled: bool(process.env.SYNC_V3_ENABLED, false),
  },
  /*
   *  «دورانِ» همگام‌سازی پس از بازیابی (§۹.۵، `lib/sync-epoch.js`) — فقط دکان.
   *  روشن به‌طورِ پیش‌فرض چون جلوی گم شدنِ فروشِ پس از پشتیبان را می‌گیرد و در کارِ
   *  عادی هیچ رفتاری را عوض نمی‌کند. `false` ⇒ `GET /api/shop/sync-epoch` ۴۰۴ و
   *  pull ِ v3 بی دوران ⇒ برنامه همان رفتارِ پیشین.
   */
  syncEpoch: {
    enabled: bool(process.env.SYNC_EPOCH_ENABLED, true),
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
