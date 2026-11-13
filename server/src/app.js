'use strict';
const path = require('path');
const express = require('express');
const config = require('./config');
const { healthy, pruneExpired, now } = require('./db');
const migrate = require('./migrate');
const plans = require('./lib/plans');
const subs = require('./lib/subscriptions');
const { rateLimit } = require('./middleware/ratelimit');
const { notFoundHandler, errorHandler, notFound } = require('./middleware/errors');
const { requireUser, optionalShop, requireShop, requireAdmin } = require('./middleware/auth');
const { redactPath } = require('./lib/log-redact');

async function createApp({ runMigrations = true } = {}) {
  const app = express();
  app.disable('x-powered-by');
  //  SEC-AUTH-05: فقط پروکسیِ مطمئن (همان قاعدهٔ `clientIp`)، نه هر همسایه‌ای
  if (config.trustProxy) app.set('trust proxy', (addr) => require('./middleware/ratelimit').trustedPeer(addr));

  if (runMigrations) await migrate.run({ log: (m) => console.log(`[migrate] ${m}`) });
  //  نسخهٔ خواندنی: سنجشِ زنده‌بودن و تأخیر از همین حالا (بی `DATABASE_REPLICA_URL` هیچ)
  require('./lib/read-replica').start();
  await plans.seedDefaults();
  //  قالب‌های آمادهٔ مرکزِ اعلان (خوش‌آمد، رو به پایان، تمدید شد، …)
  await require('./lib/notices').seedTemplates();
  await pruneExpired();
  //  §۹.۵: هر بالا آمدن یک «دورانِ» همگام‌سازیِ تازه — پس از بازیابی از پشتیبان، برنامه‌ها
  //  از همین می‌فهمند سرور به عقب رفته و opهای «اعمال‌شده»ی گم‌شده را دوباره می‌فرستند
  //  (`lib/sync-epoch.js`). شکستش بالا آمدن را نمی‌خواباند؛ فقط آن تشخیص نمی‌شود.
  //  پشتِ پرچمِ خاموشِ `SYNC_EPOCH_ENABLED`
  if (config.syncEpoch.enabled) {
    await require('./lib/sync-epoch').rotate('boot')
      .catch(err => console.error('[sync-epoch] دورانِ تازه ساخته نشد:', err.message));
  }
  //  سرور فقط رلهٔ پیام است: پیام و رسانهٔ کهنه‌تر از CHAT_RELAY_DAYS می‌رود
  await require('./lib/chat-relay').sweep().catch(err => console.error('[chat-relay]', err.message));
  //  شناسهٔ پایدارِ این سرور — در نخستین راه‌اندازی ساخته می‌شود (`lib/server-identity.js`)
  await require('./lib/server-identity').serverId().catch(err => console.error('[server-id]', err.message));
  //  مدیر از محیط — نصبی که پنلِ خانگی خودش بالا می‌آورد و ترمینالی در کار نیست
  await require('./lib/admin-bootstrap').ensureAdmin({
    username: process.env.ADMIN_BOOTSTRAP_USER,
    password: process.env.ADMIN_BOOTSTRAP_PASSWORD,
    log: (m) => console.log(`[admin] ${m}`),
  });
  //  فاز ۳: «مالکِ اصلی» (پیشین‌ترین ارشد) نشان بخورد — مدیرِ دیگری غیرفعال یا تنزلش نمی‌دهد
  await require('./lib/admin-owner').ownerId();

  //  ⚠️ حالِ زندهٔ پمپ فهرستِ **همهٔ** قرض‌داران را هم دارد (جست‌وجوی بات، بی
  //  هیچ سقفی بر شمارِ حساب‌ها) — پس سقفِ خودش را می‌گیرد، پیش از پارسرِ همگانی
  //  (پارسرِ اول بدنه را می‌خواند و دومی کاری نمی‌کند).
  app.use(['/api/pump/device/state', '/api/v1/pump/device/state'], express.json({ limit: '4mb' }));
  //  عکس/PDFِ برنامه برای بات (‎/report/:id‎): تا ۴ فایلِ ۶ مگابایتی به base64
  app.use(/^\/api(?:\/v1)?\/pump\/device\/report\//, express.json({ limit: '34mb' }));
  //  وب‌هوکِ درگاهِ پرداخت (حساب‌پی): امضا روی **بایت‌های خام** است ⇒ بدنهٔ خام، پیش از پارسرِ
  //  همگانی (او بدنهٔ خوانده‌شده را دوباره نمی‌خواند). سقفِ کوچک: رویدادِ پرداخت چند صد بایت است.
  app.use(['/api/payments/webhook', '/api/v1/payments/webhook'], express.raw({ type: () => true, limit: '64kb' }));
  //  ⛔ ممیزیِ ۳۵ (B4): NUL ِ رشته‌های بدنه می‌افتد (همگام‌سازی و پمپ دست‌نخورده)؛ SR38-2: بی reviver ِ کند پیش از سقفِ نرخ
  app.use(require('./middleware/json-body').jsonBody());

  /*
   *  بدنه‌ای که JSON نیست.
   *
   *  ── چه چیزی را می‌بندد ──────────────────────────────────────────
   *  خواننده‌ی JSON خودش خطا می‌سازد و آن خطا `expose` دارد، پس پیامش
   *  همان‌طور که هست بیرون می‌رفت:
   *
   *      Unexpected token } in JSON at position 42
   *
   *  یعنی یک جمله‌ی انگلیسیِ داخلیِ Node، با شماره‌ی بایت، به دستِ
   *  کاربرِ فارسی‌زبان. نه او می‌فهمید چه شده، نه ما می‌خواستیم درونِ
   *  کتابخانه‌مان را نشان بدهیم. بدنه‌ی بزرگ‌تر از سقف هم همین‌طور.
   *
   *  حالا هر دو یک پاسخِ تمیز می‌گیرند و جزئیات فقط در لاگِ خودمان
   *  می‌ماند — بی بدنه، چون بدنه ممکن است رمز داشته باشد.
   *  ──────────────────────────────────────────────────────────────
   */
  app.use((err, req, res, next) => {
    //  SYN-04: بدنهٔ فشرده (`Content-Encoding`) — gzip ِ خراب خطای zlib است (`Z_DATA_ERROR`، متنِ انگلیسی) و
    //  کدگذاریِ ناشناخته `encoding.unsupported`؛ هر دو همان پاسخِ تمیز. سقفِ بالا روی بدنهٔ **باز‌شده** است (بمبِ فشرده ۴۱۳).
    const zlibBad = !!err && /^Z_[A-Z_]+$/.test(String(err.code || ''));
    const badEncoding = !!err && err.type === 'encoding.unsupported';
    if (!err || (err.type !== 'entity.parse.failed' && err.type !== 'entity.too.large' && !zlibBad && !badEncoding)) {
      return next(err);
    }
    const tooBig = err.type === 'entity.too.large';
    console.warn(`[body] ${req.method} ${redactPath(req.originalUrl)} — ${err.type || err.code}`);
    if (badEncoding) {
      return res.status(415).json({ error: { code: 'unsupported_encoding', message: 'فشرده‌سازیِ این درخواست شناخته نشد' } });
    }
    res.status(tooBig ? 413 : 400).json({
      error: {
        code: tooBig ? 'body_too_large' : 'bad_json',
        message: tooBig ? 'حجم درخواست بیش از حد مجاز است' : 'بدنه‌ی درخواست درست نبود',
      },
    });
  });

  // ---- سرآیندهای امنیتی ----
  app.use((req, res, next) => {
    res.set('X-Content-Type-Options', 'nosniff');
    res.set('X-Frame-Options', 'DENY');
    res.set('Referrer-Policy', 'no-referrer');
    res.set('Cross-Origin-Opener-Policy', 'same-origin');
    //  وقتی درخواست از HTTPS آمده، به مرورگر می‌گوییم از این به بعد
    //  فقط HTTPS. بدون این، اولین درخواستِ هر بازدید می‌تواند روی HTTP
    //  ساده برود و توکن همان‌جا دیده شود. فقط پشت TLS فرستاده می‌شود:
    //  روی شبکه‌ی محلیِ بدون گواهی، این سرآیند دسترسی را می‌بندد.
    if (req.secure || req.headers['x-forwarded-proto'] === 'https') {
      res.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
    }
    next();
  });

  //  ⛔ ممیزیِ ۳۵: NUL (`%00`) در مسیر (B4 ⇒ `bad_path`) یا پرسش (X2 ⇒ `bad_query`) ⇒ ۴۰۰ پیش از هر کار، نه ۵۰۰ ِ ۲۲۰۲۱؛ `%2500` NUL نیست؛ پرسشِ `RAW_SYNC` دست‌نخورده جز v3 ِ فقط‌دکان (SR40-2، `i`).
  const { RAW_SYNC } = require('./middleware/json-body'), V3 = /^\/api(?:\/v1)?\/sync\/v3(?:\/|$)/i;
  app.use((req, res, next) => {
    const [p, ...q] = req.url.split('?');
    const bad = /%00/.test(p) ? 'bad_path' : (/%00/.test(q.join('?')) && (!RAW_SYNC.test(req.path) || V3.test(req.path)) ? 'bad_query' : '');
    return bad ? res.status(400).json({ error: { code: bad, message: 'نشانیِ درخواست درست نیست' } }) : next();
  });

  // ---- CORS ----
  // برنامه‌ی اندروید به CORS کاری ندارد؛ این فقط برای نسخه‌ی وب است و
  // تنها دامنه‌هایی که در CORS_ORIGIN آمده‌اند اجازه دارند.
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (origin) {
      if (config.corsOrigins.includes(origin) || config.corsOrigins.includes('*')) {
        res.set('Access-Control-Allow-Origin', config.corsOrigins.includes('*') ? '*' : origin);
        res.set('Vary', 'Origin');
        /*
         *  ⚠️ سرآیندهای قراردادِ ورود و Sync v1 هم باید مجاز باشند.
         *  نسخهٔ وب `X-App` · `X-Device` · `X-App-Version` ·
         *  `X-Request-Id` را روی **هر** درخواست می‌فرستد؛ بی این‌ها
         *  پیش‌پروازِ CORS بی‌صدا می‌میرد و مرورگر فقط
         *  «net::ERR_FAILED» می‌گوید — نه ۴۰۰، نه ۴۰۳، هیچ.
         *  همان تله‌ای که یک بار اپِ کارمندانِ پمپ را خواباند.
         */
        //  `Content-Encoding`: push ِ فشردهٔ وب (SYN-04، پرچمِ `sync.gzip_push`)
        res.set('Access-Control-Allow-Headers',
          'Content-Type, Content-Encoding, Authorization, Idempotency-Key, X-App, X-App-Id, X-Device, X-App-Version, X-Request-Id');
        res.set('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS');
        res.set('Access-Control-Max-Age', '600');
      } else if (req.method === 'OPTIONS') {
        return res.status(403).end();
      }
    }
    if (req.method === 'OPTIONS') return res.status(204).end();
    next();
  });

  // ---- ثبت درخواست‌ها ----
  // فقط روش، مسیر، وضعیت و زمان پاسخ. هیچ بدنه، رمز، کد یا توکنی نوشته
  // نمی‌شود؛ بخشِ توکن‌مانندِ مسیر `:tok` می‌شود و مسیر کوتاه می‌شود.
  app.use((req, res, next) => {
    const started = Date.now();
    res.on('finish', () => {
      const ms = Date.now() - started;
      if (res.statusCode >= 400 || ms > 1000 || config.env !== 'production') {
        //  ⛔ توکن در مسیر (پیگیریِ سفارش، پرونده، دعوت…) ⇒ `:tok` (lib/log-redact.js)
        console.log(`${req.method} ${redactPath(req.originalUrl)} ${res.statusCode} ${ms}ms`);
      }
    });
    next();
  });

  /*
   *  ⛔ TEN-02 (cache) — پاسخِ درخواستِ دارای اعتبارنامه هرگز در کشِ مشترک (CDN، پراکسیِ
   *  کش‌دار، قاعدهٔ «همه را کش کن») نمی‌نشیند: پیش‌فرضِ `private, no-cache` و `Vary`
   *  روی همان سرآیندها. مسیری که خودش Cache-Control می‌گذارد (‎no-store‎، ‎private,
   *  max-age‎، ETag ِ فاکتور…) پس از این می‌نویسد و همان می‌ماند. `tenant-surfaces.test.js`.
   */
  const CREDENTIALS = ['authorization', 'x-store-session', 'x-store-ticket'];
  app.use('/api', (req, res, next) => {
    if (CREDENTIALS.some((k) => req.headers[k])) {
      res.set('Cache-Control', 'private, no-cache');
      res.vary('Authorization');
      res.vary('X-Store-Session');
      res.vary('X-Store-Ticket');
    }
    next();
  });

  //  PERF-04: مهلتِ سراسریِ درخواست — `REQUEST_TIMEOUT_MS` (۰ = خاموش)؛ پمپ و v1 بیرون (`middleware/deadline.js`)
  app.use('/api', require('./middleware/deadline').requestDeadline());
  //  ⚠️ همگام‌سازی سطلِ خودش را دارد (‎routes/sync-v1.js‎، شرحش در ‎config.rateLimit.syncMax‎)
  //  ترافیکِ اثبات‌شدهٔ مدیر/پلِ پنل سطلِ خودش را دارد؛ بقیه همین سطلِ IP (`middleware/admin-bucket.js`)
  const general = require('./middleware/admin-bucket').withAdminBucket(
    rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'general' }));
  app.use((req, res, next) => (/^\/api\/sync\/v[13]\//.test(req.path) ? next() : general(req, res, next)));

  // ---- بررسی سلامت ----
  /*
   *  بندِ ۲.۱ پرامپتِ ورود: **همیشه سریع و بی دیتابیس**.
   *
   *  چرا: برنامه پیش از هر کاری این را با مهلتِ سه ثانیه می‌زند تا همان
   *  لحظه بگوید «سرور در دسترس نیست». اگر این مسیر به دیتابیس دست بزند و
   *  دیتابیس کند باشد، تایم‌اوت می‌خورد و برنامه «سرور خاموش است» می‌گوید
   *  در حالی که سرور سالم است. حالِ دیتابیس جدا و بی‌وقفه می‌آید.
   */
  const health = async (req, res) => {
    let worker = 'down';
    try { worker = require('./lib/login-outbox').workerStatus(); } catch { worker = 'down'; }
    res.json({
      ok: true,
      service: 'vill3n-auth',
      server: 'online',
      //  حالِ دیتابیس از آخرین سنجشِ پس‌زمینه می‌آید، نه از یک پرس‌وجوی تازه
      database: lastDbState ? 'connected' : 'unavailable',
      email_worker: worker,
      version: require('../package.json').version,
      time: now(),
      uptimeSeconds: Math.round(process.uptime()),
    });
  };

  /*
   *  سنجشِ دیتابیس در پس‌زمینه — تا `/health` هیچ‌وقت منتظرِ آن نماند.
   *  `unref` یعنی این تیک جلوی بسته شدنِ فرآیند را نمی‌گیرد (آزمون‌ها).
   */
  let lastDbState = true;
  const dbTick = setInterval(() => { healthy().then((ok) => { lastDbState = ok; }).catch(() => { lastDbState = false; }); }, 5000);
  if (dbTick.unref) dbTick.unref();
  healthy().then((ok) => { lastDbState = ok; }).catch(() => { lastDbState = false; });

  /** حالِ کاملِ دیتابیس — برای مدیر و برای پنلِ خانگی، نه برای مسیرِ داغِ ورود. */
  const ready = async (req, res) => {
    const db = await healthy();
    res.status(db ? 200 : 503).json({
      ok: db, database: db ? 'connected' : 'unavailable',
      version: require('../package.json').version, time: now(),
    });
  };
  app.get('/health', health);
  app.get('/ready', ready);

  // ---- API ----
  function apiRouter() {
    const api = express.Router();
    //  نسخهٔ خواندنی (`DATABASE_REPLICA_URL`، پیش‌فرض خاموش ⇒ فقط `next()`): هر درخواستِ نوشتنی ⇒ خواندن‌های
    //  همان مشتری/کاربر تا `REPLICA_STICKY_MS` از پایگاهِ اصلی (`lib/read-replica.js`)
    api.use(require('./lib/read-replica').writes);
    api.use('/admin', require('./lib/admin-capabilities').adminApiVersion);   //  S18: X-Admin-API-Version روی هر /admin/* (۴۰۱/۴۰۴ هم)
    api.get('/health', health);
    api.get('/ready', ready);

    /**
     * تنظیمات عمومی سرور.
     *
     * برنامه با این می‌فهمد کدام راه‌های ورود روی این سرور باز است و
     * لازم نیست برای هر تغییر، نسخه‌ی تازه‌ای از برنامه ساخته شود.
     * فقط خواندنی است و هیچ راز یا دستوری در آن نیست.
     */
    api.get('/config', async (req, res, next) => {
      try {
        const cfg = await plans.allConfig();
        res.json({
          serverTime: now(),
          registrationOpen: config.allowRegistration,
          googleClientId: config.google.clientIds[0] || '',
          //  برنامه‌ی کامپیوتر شناسه‌ی خودش را برمی‌دارد، نه شناسه‌ی سایت.
          googleDesktopClientId: config.google.desktopClientId || '',
          //  ورود با اپل (فقط برنامهٔ آیفون) — `APPLE_CLIENT_IDS`؛ شناسه به برنامه داده نمی‌شود
          appleLogin: config.apple.clientIds.length > 0,
          otpEnabled: config.otp.provider !== 'off',
          //  F0-13: تنها خوانندهٔ مدتِ آزمایشی — همان عددِ `entitlement`
          trialDays: await plans.trialDaysOf('shop'),
          whatsapp: { number: cfg.whatsapp_number || '', message: cfg.whatsapp_message || '' },
          //  §۳۸.۹: حداقلِ همان برنامه‌ای که می‌پرسد (`X-App-Id`/`?app=`؛ نبود ⇒ دکان)
          minAppVersion: require('./lib/app-compat').minVersionOf(cfg, require('./lib/tenancy').appOfRequest(req)),
          //  ثبت‌نام سه‌مرحله‌ای با ایمیل — برنامه با این می‌فهمد این
          //  سرور مسیرهای /auth/register/* را دارد
          emailSignup: true,
          termsVersion: require('./lib/terms').VERSION,
          //  برنامه با این می‌فهمد این سرور چت پشتیبانی، کد وی‌آی‌پی و
          //  تپشِ بازدید دارد — پس نسخه‌های قدیمِ سرور نمی‌شکنند و
          //  نسخه‌ی تازه‌ی برنامه هم دکمه‌ای را نشان نمی‌دهد که مسیرش نیست
          support: true,
          vipCodes: true,
          visitPing: true,
          //  چتِ پشتیبانیِ مشتریِ کیو‌آر با صاحبِ پمپ — و کلیدِ عمومیِ پوش
          pumpChat: true,
          pumpChatVapid: await require('./lib/station-chat').publicKey(),
        });
      } catch (err) { next(err); }
    });
    //  هویت و توانِ همین سرور (`app.multi_server` ِ برنامه‌ها) — عمومی، فقط خواندنی، بی راز.
    //  زیرِ پیشوندِ موجودِ `/config` ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی دست نمی‌خورد
    api.use('/config/server', require('./routes/server-info'));
    /**
     * فهرست پلن‌ها و قیمت‌ها — **بی‌نیاز به ورود**.
     *
     * ── چه چیزی این را لازم کرد ──────────────────────────────────────
     * تنها راهِ گرفتنِ قیمت‌ها `/me/plans` بود که توکن می‌خواهد. نسخه‌ی وب
     * آن را بی‌توکن صدا می‌زد، همیشه ۴۰۱ می‌گرفت و بی‌صدا به فهرستِ
     * قیمتِ داخلِ خودش برمی‌گشت. یعنی هر تغییرِ قیمتی که در پنل داده
     * می‌شد، روی سایت دیده نمی‌شد — و تخفیف هم هرگز نمی‌رسید.
     *
     * قیمت راز نیست: هر کسی که صفحه‌ی اشتراک را باز کند باید ببیندش،
     * چه حساب داشته باشد چه نه. پس اینجا باز است.
     *
     * نامِ واتساپ و لینکش هم آماده می‌آید تا هر برنامه‌ای خودش نسازدش.
     */
    api.get('/plans', async (req, res, next) => {
      try {
        /*
         *  ⚠️ `?app=pump` پلن‌های پمپ را می‌دهد، با واحدِ پولِ خودش
         *  (`pump_currency`، دالر). بی این، هر کسی که این مسیرِ باز را
         *  می‌خواند فهرستِ **دکان** را می‌گرفت — و صفحه‌ای که قیمتِ پمپ
         *  را نشان می‌داد سه پلنِ اشتباه چاپ می‌کرد. نیامدنش همان
         *  دکان است، پس سایتِ امروز دست‌نخورده می‌ماند.
         */
        const app = require('./lib/tenancy').sectionOf(req.query?.app) || 'shop';
        const cfg = await plans.allConfig();
        const number = cfg.whatsapp_number || '';
        const message = cfg.whatsapp_message || '';
        const digits = String(number).replace(/[^0-9]/g, '').replace(/^0/, '93');
        const list = await plans.listPlans({ app });
        res.json({
          app,
          plans: list.map(p => ({
            ...p,
            //  قیمتِ روزانه اینجا حساب می‌شود، نه در سه برنامه‌ی جدا
            pricePerDay: p.days > 0 && p.price > 0
              ? Math.round((p.price / p.days) * 10) / 10 : null,
            whatsappUrl: digits
              ? `https://wa.me/${digits}?text=${encodeURIComponent(`${message} (${p.title})`)}`
              : '',
          })),
          currency: (app === 'pump' ? cfg.pump_currency : cfg.currency) || cfg.currency || 'افغانی',
          trialDays: await plans.trialDaysOf(app),
          whatsapp: {
            number,
            message,
            url: digits ? `https://wa.me/${digits}?text=${encodeURIComponent(message)}` : '',
          },
          serverTime: now(),
        });
      } catch (err) { next(err); }
    });

    /** متن شرایط و ضوابط — همان چیزی که موقع ثبت‌نام نشان داده می‌شود. */
    api.get('/terms', (req, res) => {
      const terms = require('./lib/terms');
      res.json({ version: terms.VERSION, title: terms.TITLE, sections: terms.SECTIONS });
    });
    /*
     *  قراردادِ ورودِ کدِ ایمیلی (پرامپتِ «ورودِ بی‌نقص»).
     *
     *  ⚠️ **پس از `/auth` سوار می‌شود، نه پیش از آن.** `/auth/:app` یک
     *  الگوی پارامتری است و `/api/auth/login` را هم می‌گیرد (با
     *  `app='login'`). چون مسیرهای ثابتِ `routes/auth.js` اول می‌آیند،
     *  ورودِ امروزِ کاربران دست‌نخورده می‌ماند و فقط آن‌چه هیچ‌کس نگرفته
     *  به این‌جا می‌رسد.
     */
    api.use('/auth', require('./routes/auth'));
    api.use('/auth/:app', require('./routes/app-auth'));
    //  WEB-15: کارتِ ویترین (`PUT /sync/v1/photos/:productId/thumb`) — فقط دکان، جدا از `routes/sync-v1.js` ِ مشترک با
    //  پمپ؛ پیش از آن و فقط همان یک در (زنجیره روی خودِ در، نه `router.use`) ⇒ هیچ درخواستِ دیگری از آن نمی‌گذرد
    api.use('/sync/v1', require('./routes/sync-photo-thumbs'));
    //  STF-09: سرِ زنجیرهٔ سابقهٔ دستگاه (`/sync/v1/audit-anchor`) — فقط دکان، جدا از `routes/sync-v1.js` ِ مشترک با
    //  پمپ؛ پیش از آن و فقط همان درها (زنجیره روی خودِ در، نه `router.use`) ⇒ هیچ درخواستِ دیگری از آن نمی‌گذرد
    api.use('/sync/v1', require('./routes/audit-anchor'));
    api.use('/sync/v1', require('./routes/sync-v1'));
    //  Sync v3 (بخشِ ۳.۸) — فقط دکان، پیش‌فرض خاموش (‎config.syncV3‎)
    api.use('/sync/v3', require('./routes/sync-v3'));
    //  «دورانِ» همگام‌سازیِ v1 پس از بازیابی (§۹.۵) — فقط دکان، جدا از `routes/sync-v1.js`
    //  ِ مشترک با پمپ؛ پیش از `/shop` (همان دلیلِ `/shop/orders`)
    api.use('/shop/sync-epoch', require('./routes/shop-sync-epoch'));
    api.use('/errors', require('./routes/errors'));
    api.use('/location', require('./routes/location'));
    api.use('/me', require('./routes/me'));
    //  چتِ مشتری ↔ دکان (v3 §۳۳) — پشتِ پرچمِ `shop.chat`، پیش‌فرض خاموش.
    //  پیش از `/shop` (همان دلیلِ `/shop/orders`)؛ درِ مشتری زیرِ `/store/:slug/chat`
    //  که بی ماژولِ ویترین ۴۰۴ است و بقیهٔ `/store` را دست نمی‌زند.
    api.use('/shop/chat', require('./routes/shop-chat'));
    api.use('/store', require('./routes/shop-chat-store'));
    api.use('/admin/shop-chat', require('./routes/admin-shop-chat'));
    //  شکایت و تخلفِ بازارچه (v3 §۳۸.۶) — پشتِ پرچمِ `market.trust`، پیش‌فرض خاموش.
    //  همان قاعده: دکان پیش از `/shop`، مشتری زیرِ `/store/:slug/trust`، مدیر زیرِ `/admin/trust`.
    api.use('/shop/trust', require('./routes/shop-trust'));
    api.use('/store', require('./routes/trust-store'));
    api.use('/admin/trust', require('./routes/admin-trust'));
    //  کاتالوگ و موجودیِ سرور (v3 §۳۲) — پیش از `/shop`، پشتِ پرچمِ خاموشِ `shop.catalog`
    api.use('/shop/catalog', require('./routes/catalog'));
    //  سفارشِ خرید از تأمین‌کننده (PUR-02/04) — پیش از `/shop`، پشتِ پرچمِ خاموشِ `shop.purchase_orders`
    api.use('/shop/purchase-orders', require('./routes/purchase-orders'));
    //  اعلانِ سفارش (v3 §۳۰) — پیش از `/shop` به همان دلیلِ `/shop/orders`؛ پیش‌فرض خاموش
    api.use('/shop/order-notify', require('./routes/order-notify'));
    //  پوشِ گوشی (FCM، O-01) — ثبتِ توکنِ هر عضو و دستگاه؛ پیش از `/shop`؛ پشتِ پرچمِ خاموشِ `shop.device_push`
    api.use('/shop/push', require('./routes/shop-push'));
    //  PAY-20/RPT-12: گزارشِ خودکار برای صاحبِ دکان — پیش از `/shop`؛ پشتِ پرچمِ خاموشِ `shop.owner_report`
    api.use('/shop/owner-report', require('./routes/owner-report'));
    //  RPT-18: گزارشِ همهٔ شعبه‌ها (دکان‌های همین صاحب) در یک جا — پیش از `/shop`؛ پشتِ پرچمِ خاموشِ `shop.branches_report`
    api.use('/shop/branches-report', require('./routes/branches-report'));
    //  سفارشِ آنلاین (فاز ۴) — پیش از `/shop` تا `requireUser`ِ آن دو بار نرود؛ زیرِ همان پیشوند، پس ACCOUNT_PREFIXESِ پنلِ خانگی دست نمی‌خورد
    api.use('/shop/orders/desk', require('./routes/order-desk'));   // میزِ سفارش (ORD-04/08/09) — پیش از `/shop/orders`؛ پشتِ پرچمِ خاموشِ `shop.orders.desk`
    api.use('/shop/orders/bulk', require('./routes/order-bulk'));   // کارِ گروهی روی سفارش‌ها (ORD-19) — پیش از `/shop/orders`؛ پشتِ پرچمِ خاموشِ `shop.orders.bulk`
    //  پروندهٔ مدرکِ اختلاف (RSK-08) — پیش از `/shop/orders`؛ پشتِ پرچمِ خاموشِ `shop.orders.dispute_file`
    api.use('/shop/orders/evidence', require('./routes/order-evidence'));
    api.use('/shop/order-feedback', require('./routes/order-feedback'));   // NTF-07/09: گزارشِ مشکل و نظرِ مشتری، پشتِ دو پرچمِ خاموش
    //  پوزش و کوپنِ خودکارِ تأخیر (NTF-10) — پیش از `/shop/orders`؛ پشتِ پرچمِ خاموشِ `shop.orders.late_apology`
    api.use('/shop/orders/late-apology', require('./routes/order-late-apology'));
    //  سفارش‌های باز/لغوشدهٔ یک مشتری برای پروندهٔ او (CRM-07) — پیش از `/shop/orders`؛ پشتِ همان پرچمِ `shop.orders`
    api.use('/shop/orders/customer', require('./routes/order-customer'));
    api.use('/shop/orders/risk/queue', require('./routes/order-risk-queue'));   // RSK-03: صفِ پرخطرِ پیش از ارسال، پشتِ `shop.order_risk`
    //  «به فاکتور» ِ یک‌ضرب (`POST /shop/orders/:id/invoice`، lib/order-flow) — پیش از `/shop/orders`
    api.use('/shop/orders', require('./routes/order-flow'));
    api.use('/shop/orders', require('./routes/orders'));
    //  ویترین (فاز ۴): تنظیمِ دکان زیرِ `/shop` (پیش از آن)، و درِ عمومی
    //  `/store` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی (دستورالعمل در PR)
    //  پیوندِ کمپین و QR (MKT-10/11) پیش از `/shop/storefront`؛ پیش‌فرض خاموش
    api.use('/shop/storefront/campaigns', require('./routes/storefront-campaigns'));
    //  تخفیفِ اولین خرید و سدِ سوءاستفاده از تخفیف (MKT-02، RSK-09)؛ پیش‌فرض خاموش
    api.use('/shop/storefront/promo-guard', require('./routes/storefront-promo-guard'));
    api.use('/shop/storefront/eta', require('./routes/storefront-eta'));   // WEB-04: تنظیمِ زمانِ تقریبیِ تحویل، پشتِ پرچمِ خاموش
    api.use('/shop/storefront', require('./routes/storefront-admin'));
    //  تأییدِ جایگزینِ جمع‌آوری توسطِ مشتری (PCK-05) — با توکنِ پیگیری؛ پشتِ پرچمِ خاموشِ `shop.orders.picking`
    api.use('/store', require('./routes/pick-substitutes-store'));
    api.use('/store', require('./routes/order-amend-store'));   // ORD-14: رضایتِ مشتری به ویرایشِ سفارش
    api.use('/store', require('./routes/order-add-store'));   // CHK-21: افزودنِ قلم پیش از جمع‌آوری، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/order-method-store'));   // S72: تغییرِ روشِ تحویل پیش از جمع‌آوری، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/order-pay-store'));   // «پرداخت پس از پذیرش»: نقد هنگامِ تحویل، پشتِ تنظیمِ خاموشِ دکان
    api.use('/store', require('./routes/storefront-promos-store'));   // MKT-01/09: تخفیفِ ویترین، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/delivery-rating-store'));   // DLV-18: امتیازِ مشتری به پیک، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/order-address-media-store'));   // CHK-08: عکسِ دروازه و یادداشتِ صوتیِ نشانی، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/store-push'));   // CHK-20/NTF-01: پوشِ وب ِ مشتری برای همین سفارش، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/storefront-finder-store'));   // WEB-07/10: فیلتر و «خبرم کن»، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/order-feedback-store'));   // NTF-07/09: گزارشِ مشکل با عکس و نظرِ سفارش، پشتِ دو پرچمِ خاموش
    api.use('/store', require('./routes/storefront-eta-store'));   // WEB-04: زمانِ تقریبیِ تحویل، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/storefront-funnel-store'));   // RPT-14: تپشِ بالای قیفِ ویترینِ وب، پشتِ پرچمِ خاموشِ گزارش
    api.use('/store', require('./routes/store-account-history'));   // CRM-13: «حسابِ من» (خرید و مانده)، پشتِ پرچمِ خاموش
    api.use('/store', require('./routes/storefront-visual-store'));   // AI-11: «جست‌وجو با عکس» ِ ویترین، پشتِ دو پرچمِ خاموش
    api.use('/store', require('./routes/storefront'));
    //  فاکتورِ نسخهٔ ۲: سربرگ و لوگوی دکان زیرِ `/shop` (پیش از آن)، و درِ عمومیِ QRِ
    //  هر فاکتور `/receipt` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی (دستورالعمل در PR)
    api.use('/shop/receipt-profile', require('./routes/shop-receipt'));
    //  ذخیرهٔ کهنه (#407): فقط پرچمِ `shop.ledger_merge` (پیش‌فرض خاموش) برای برنامه — پیش از `/shop`
    api.use('/shop/ledger-merge', require('./routes/shop-ledger-merge'));
    //  تنظیماتِ دکان، همگام‌سازیِ زنده (مهاجرتِ ۳۰۰) — پیش از `/shop`؛ پشتِ پرچمِ خاموشِ `shop.settings_sync`
    api.use('/shop/settings-sync', require('./routes/shop-settings-sync'));
    //  پشتیبانِ فاکتورهای نگه‌داشته (مهاجرتِ ۴۲۵، POS-12/S09) — پیش از `/shop`؛ پشتِ پرچمِ خاموشِ `shop.held_sync`
    api.use('/shop/held-invoices', require('./routes/shop-held-invoices'));
    //  PRM-13: قاعدهٔ یکسانِ امتیازِ وفاداری برای همهٔ دستگاه‌های دکان — پیش از `/shop`؛ پشتِ پرچمِ خاموشِ `shop.loyalty_rule`
    api.use('/shop/loyalty', require('./routes/shop-loyalty'));
    api.use('/receipt', require('./routes/receipt-public'));
    //  بازارچه (WEB، §۲۳) — پشتِ پرچمِ `market.directory`، پیش‌فرض خاموش. درِ دکان
    //  پیش از `/shop`؛ درِ عمومی `/market` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی
    api.use('/shop/market', require('./routes/market-admin'));
    api.use('/market', require('./routes/market'));
    //  بانکِ تصاویر (v3 ۳۷) — پشتِ پرچمِ `shop.image_bank`، پیش‌فرض خاموش
    //  IMG-PRO-17: تغییرِ دسته‌ایِ عکسِ کالاها و برگرداندنش — پیش از `/shop/images` (همان پرچم)
    api.use('/shop/images/changes', require('./routes/shop-image-changes'));
    api.use('/shop/images/studio', require('./routes/shop-image-studio')); //  AI-02/AI-05: استودیوی عکس (صفِ پردازشِ محلی، بازبینی) — پیش از `/shop/images`؛ پرچمِ `shop.image_studio`
    api.use('/shop/images', require('./routes/shop-images'));
    //  تحویل و پیک (۲۲.۲ C) — همان قاعده؛ درهای پیک زیرِ `/courier` (۳.۱۱)
    api.use('/shop/delivery', require('./routes/delivery'));
    api.use('/courier', require('./routes/courier'));
    //  تخفیف‌ها و قاعده‌های قیمت (PRM) — کلیدِ دکان، پیش‌فرض خاموش
    api.use('/shop/promos', require('./routes/shop-promos'));
    //  STF-07/STF-08: ورود و خروجِ کارمند و نرخ‌های پورسانت — پشتِ `shop.staff_time`، پیش‌فرض خاموش
    api.use('/shop/staff-time', require('./routes/staff-time'));
    //  STF-06: محدودهٔ مکانِ کارمندان (اجرا در `/shop/catalog`) — پشتِ `shop.staff_scope`، پیش‌فرض خاموش
    api.use('/shop/staff-scope', require('./routes/staff-scope'));
    api.use('/shop/till-pin', require('./routes/shop-till-pin')); //  STF-04/S46: PIN ِ شخصیِ صندوق در سرور و نوبت‌های سنجیدهٔ صندوق (مهاجرتِ ۶۴۰) — پشتِ `shop.till_pin`، پیش‌فرض خاموش
    //  STF-02: «درخواستِ تأیید» از راهِ دور (شاگرد می‌پرسد، صاحب/مدیر روی دستگاهِ خودش) — پشتِ `shop.approval_requests`، پیش‌فرض خاموش
    api.use('/shop/approval-requests', require('./routes/shop-approval-requests'));
    //  صندوقِ سندهای ردشده (DATA-SAFETY، مهاجرتِ ۳۱۵): صاحب/مدیر می‌بیند، دوباره ثبت می‌کند یا کنار می‌گذارد؛ دستگاهِ سازنده ردیفِ خودش را — بی پرچم
    api.use('/shop/conflict-inbox', require('./routes/shop-conflict-inbox'));
    //  STF-01: نقشِ سفارشی (نام + نقشِ پایه + لایهٔ مجوز؛ اجرا در `staff-perms.layersFor`) — پشتِ `shop.custom_roles`، پیش‌فرض خاموش
    api.use('/shop/custom-roles', require('./routes/shop-custom-roles'));
    //  دستگاه‌های شناختهٔ کارمندان (رازِ دستگاهِ ورود با کد): فهرست، «باطل کردنِ این دستگاه»، «خروج از همه» ِ یک عضو — مهاجرتِ ۳۴۵
    api.use('/shop/staff-keys', require('./routes/shop-staff-keys'));
    //  موتورِ تازهٔ موجودیِ برنامهٔ دکان (#315) — فقط خواندنِ پرچمِ `shop.stock_v2`، پیش‌فرض خاموش
    api.use('/shop/stock-ledger', require('./routes/shop-stock-ledger'));
    //  پرداختِ درگاهی (حساب‌پی، PAY-21) — درِ دکان پیش از `/shop`، پشتِ پرچمِ خاموشِ `pay.hesabpay`؛
    //  وب‌هوکِ بی‌توکنِ ارائه‌دهنده زیرِ `/payments` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی
    api.use('/shop/payments', require('./routes/shop-payments'));
    //  حساب‌پیِ هر دکان (پول مستقیم به حسابِ همان دکان؛ رمزِ جدای حساب‌پی) — فقط صاحب، مهاجرتِ ۲۰۴
    api.use('/shop/hesabpay', require('./routes/shop-hesabpay'));
    api.use('/payments', require('./routes/payments-webhook'));
    api.use('/admin/payments', require('./routes/admin-payments'));
    //  SEC-10: حذفِ دادهٔ شخصیِ مشتری به درخواستِ صاحبِ دکان — پشتِ `shop.customer_erasure`، پیش‌فرض خاموش
    api.use('/shop/customer-erasure', require('./routes/shop-customer-erasure'));
    //  CRM-15: «چه کسی این پرونده را دید» (سابقهٔ دسترسی) — فقط `audit.view`/صاحب، بی پرچم (سپرِ حریم، نه رفتارِ تازه)
    api.use('/shop/customer-access', require('./routes/shop-customer-access'));
    //  AI-11: «جست‌وجو با عکس» (کالاهای مشابهِ همان دکان) — پشتِ `shop.visual_search`، پیش‌فرض خاموش
    api.use('/shop/visual-search', require('./routes/shop-visual-search'));
    api.use('/shop', require('./routes/shop'));
    /*
     *  بخشِ پمپ‌بنزین — دفترِ جدا، همان دستگاهِ اشتراک.
     *
     *  ⚠️ ترتیبِ این دو خط مهم است و اتفاقی نیست — همان تله‌ای که
     *  بالاتر برای `/api/v1` و `/api` نوشته شده.
     *
     *  `/pump` یک `router.use(requirePumpUser)` سراسری دارد که روی
     *  **هر** مسیرِ زیرِ خودش می‌نشیند. اگر اول سوار می‌شد،
     *  `/pump/device/activate` — که عمداً بی‌توکن است، چون هنوز هیچ
     *  توکنی وجود ندارد — پیش از رسیدن به مقصد ۴۰۱ می‌گرفت و
     *  فعال‌سازی با کدِ شش‌رقمی اصلاً ممکن نبود.
     *
     *  پس نشانیِ درازتر اول.
     */
    api.use('/pump/public', require('./routes/pump-public'));   // کیو‌آرِ زندهٔ مشتری — بی‌توکن
    api.use('/pump/device', require('./routes/pump-device'));
    api.use('/pump', require('./routes/pump'));
    api.use('/events', require('./routes/events'));
    api.use('/sync', require('./routes/sync'));
    api.use('/shop/sync', require('./routes/sync'));   // نام قدیمی
    api.use('/admin', require('./routes/admin'));
    api.use('/license', require('./routes/license'));
    //  نسخه‌های برنامه — برنامه‌ها از همین سرور می‌گیرند، نه از GitHub
    api.use('/releases', require('./routes/releases'));
    //  پشتیبانی و تپشِ بازدید عمداً توکن اجباری ندارند: همان کسی که
    //  هنوز حساب نساخته، بیشتر از همه به هر دو نیاز دارد.
    api.use('/support', require('./routes/support'));
    api.use('/visit', require('./routes/visit'));
    api.use('/vip', require('./routes/vip'));

    // نام‌های قدیمی صفحه‌ی اشتراک
    const me = require('./routes/me');
    const billing = express.Router();
    billing.use(requireUser, optionalShop);
    billing.get('/status', me.subscriptionHandler);
    billing.get('/plans', me.plansHandler);
    //  نسخه‌ی وب این را صدا می‌زند و تا امروز اینجا نبود، یعنی هر درخواستِ
    //  خریدی که از سایت می‌آمد ۴۰۴ می‌گرفت و بی‌صدا گم می‌شد.
    billing.post('/request', me.purchaseRequestHandler);
    api.use('/billing', billing);

    /*
     *  ══ مرکزِ اعلان · فروش · پورتالِ مشتری · SDK ═══════════════════
     *  دو روترِ مدیر زیرِ همان `/admin` می‌نشینند: `requireAdmin`ِ
     *  `routes/admin.js` پیش از این‌ها می‌دود و مسیرِ پیدا‌نشده به
     *  این‌ها می‌رسد؛ خودشان هم `requireAdmin` دارند تا به تنهایی هم
     *  بسته باشند.
     */
    api.use('/admin', require('./routes/admin-notices'));
    api.use('/admin', require('./routes/admin-sales'));
    //  ماژولِ ۴.۵ ِ پنلِ مرکزی: مالیِ مرکزی (درآمدِ اشتراک، سندِ اصلاحی، بدهی، مغایرت، خروجی) — `requireAdmin` ِ خودش
    api.use('/admin/finance', require('./routes/admin-finance'));
    //  بانکِ مرکزیِ تصاویر (v3 ۳۷.۳) — پشتِ پرچمِ `image_bank.central`، پیش‌فرض خاموش
    api.use('/admin/image-bank', require('./routes/admin-image-bank'));
    //  LOC-04 جدولِ تعطیلاتِ رسمی و POS-04 فهرستِ مرکزیِ مترادفِ صندوق — خواندنِ همگانی؛ نوشتن فقط مدیر (admin-calendar، admin-synonyms)
    api.use('/calendar', require('./routes/calendar')).use('/search', require('./routes/search-synonyms'));
    api.use('/admin/calendar', require('./routes/admin-calendar'));
    /*
     *  ⛔ مسیرِ ناشناختهٔ مدیر ⇒ ۴۰۴ `route_not_found`، نه ۴۰۱ (چسبِ پنلِ مرکزی، بندِ ۱۲.۶ ِ
     *  ADMIN-CONTROL-PLANE). بی این خط، درخواستِ مدیرِ **معتبر** به مسیری که این سرور ندارد به دروازهٔ
     *  پایانیِ `/api` (پایین‌تر، با `requireUser`) می‌افتاد و ۴۰۱ `invalid_token` می‌گرفت؛ پلِ
     *  پنل آن را «نشستِ ارشد تمام شد» می‌خواند. `requireAdmin` همین‌جا دوباره: بی‌توکن و توکنِ دکان
     *  همان ۴۰۱ ِ همیشه را می‌گیرند تا وجود/نبودِ مسیرِ مدیر به بیرون گفته نشود. هر مسیرِ واقعی
     *  بالاتر سوار است، پس این خط به هیچ‌کدام نمی‌رسد.
     */
    api.use('/admin', requireAdmin, (req, res, next) => next(notFound('این مسیرِ مدیر روی این سرور نیست', 'route_not_found')));
    api.use('/portal', require('./routes/portal'));
    api.use('/rep', require('./routes/rep'));       // شورا چ۳: نمایندهٔ فروش — فقط فروش‌های خودش
    api.get('/downloads', require('./routes/portal').downloadsHandler);

    //  ⛔ درِ مستقیمِ داده (`routes/data.js`، ۵۱ در) ۱۴۰۵/۰۸/۲۲ رفت (بی صدازننده؛ نوشتن فقط از همگام‌سازی). دروازهٔ پایانی‌اش
    //  می‌ماند: مسیرِ ناشناخته بی نشستِ دکان همان ۴۰۱ است و درِ پرچمِ خاموش «همان درِ ناشناخته» — قراردادِ برنامهٔ خرید
    //  (`core/test-vectors/account-cart-v1.json`، `ShopperCartSync.doorClosed`)؛ با نشستِ دکان ۴۰۴ (`dead-doors.test.js`).
    api.use(requireUser, requireShop);
    return api;
  }

  /*
   *  ترتیب این دو خط مهم است و اتفاقی نیست.
   *
   *  اگر `/api` اول سوار شود، درخواستِ `/api/v1/health` را هم **همان**
   *  می‌قاپد و داخلش مسیر می‌شود `/v1/health`؛ چون چنین مسیری نیست، به
   *  آخرین لایه (دروازهٔ پایانیِ `requireUser, requireShop`) می‌رسد که توکن می‌خواهد و همه‌چیز
   *  ۴۰۱ برمی‌گشت. یعنی کل `/api/v1/…` — همان چیزی که برنامه‌ها صدا
   *  می‌زنند — بسته بود.
   *
   *  پس نشانیِ درازتر اول.
   */
  app.use('/api/v1', apiRouter());
  app.use('/api', apiRouter());

  // ---- پنل مدیریت ----
  //  CSP فقط برای پنل: نه اسکریپتِ درون‌خطی، نه منبعِ بیرونی، نه قاب.
  //  اگر روزی XSS در پنل پیدا شود، مرورگر اجرای اسکریپتِ تزریقی را
  //  نمی‌پذیرد. style درون‌خطی می‌ماند چون HTMLِ پنل `style=` دارد.
  app.use('/admin', (req, res, next) => {
    res.set('Content-Security-Policy',
      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
      "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    res.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
    next();
  });
  app.use('/admin', express.static(path.join(__dirname, '..', 'public', 'admin'), {
    index: 'index.html', maxAge: 0, etag: true,
  }));
  // ---- ویترینِ اینترنتی (فاز ۴؛ D-06: `{slug}.vill3n.top`) ----
  //  CSP ِ سخت مثلِ پنل، حتی بی style ِ درون‌خطی؛ فقط مکانِ دستگاه برای پین.
  //  `media-src blob:` فقط برای پیامِ صوتیِ چت (CHAT-07): <audio> سرآیندِ بلیت را نمی‌فرستد و رسانهٔ چت پیوندِ
  //  امضاشده ندارد (S118) ⇒ بایت با fetch ِ بلیت‌دار و پخش از blob: ِ همین صفحه؛ blob را فقط اسکریپتِ همین مبدأ می‌سازد. نه data:.
  //  `microphone=(self)` فقط برای ضبطِ پیامِ صوتیِ مشتری (`store/chat-voice.js`، پرچمِ خاموشِ `shop.chat.voice`): فقط همین
  //  مبدأ (نه قابِ بیرونی؛ frame-ancestors 'none')، و مرورگر خودش اجازه می‌پرسد. دوربین و پرداخت همان بسته.
  //  پوسته ایستاست و داده از `/api/store/:slug` می‌آید (همان مبدأ ⇒ بی CORS). ویترینِ خاموش یا ناموجود ⇒ همان پوسته با ۴۰۴.
  const storeDir = path.join(__dirname, '..', 'public', 'store');
  const storefront = require('./lib/storefront');
  const mapOrigin = require('./lib/store-map').origin();   //  CHK-08/۴-۳: تکه‌های نقشهٔ پین (`STORE_MAP_TILES`)؛ بی آن همان CSP
  const storeHeaders = (req, res, next) => {
    res.set('Content-Security-Policy',
      `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:${mapOrigin ? ` ${mapOrigin}` : ''}; media-src 'self' blob:; font-src 'self'; ` +
      "connect-src 'self'; manifest-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    res.set('Permissions-Policy', 'camera=(), microphone=(self), geolocation=(self), payment=()');
    next();
  };
  //  WEB-17/20 (`lib/store-seo.js`، پرچمِ خاموشِ `shop.storefront.seo`): روشن ⇒ همان پوسته با
  //  برچسب‌های Open Graph، hreflang و JSON-LD؛ خاموش ⇒ همان پروندهٔ ایستا، بیت‌به‌بیت.
  const storeSeo = require('./lib/store-seo');
  //  نشانیِ نسخه‌دارِ پرونده‌های ایستا (CDN): `?v=` ِ درست ⇒ یک سال immutable؛ پوستهٔ نسخه‌دار فقط با `ASSET_VERSIONS`
  const assets = require('./lib/asset-versions');
  const staticOpts = { index: false, maxAge: 0, etag: true, setHeaders: assets.immutableIfVersioned };
  const shell = (html) => (config.storefront.assetVersions ? assets.versioned(html) : html);
  const sendShell = (res, dir) => (config.storefront.assetVersions
    ? res.type('html').send(assets.versioned(require('node:fs').readFileSync(path.join(dir, 'index.html'), 'utf8')))
    : res.sendFile('index.html', { root: dir }));
  const seoLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-seo' });
  //  `?lang=` فقط یکی از زبان‌های سئو، وگرنه fa: همان کارِ `langOf` ِ درونِ store-seo، این‌جا پیش از رسیدن به HTML
  const seoLang = (v) => (typeof v === 'string' && storeSeo.LANGS.includes(v) ? v : 'fa');
  const storePage = async (slug, res, lang, onHost = false) => {
    let status = 200;
    let sf = null;
    try { sf = await storefront.open(slug); } catch (err) { if (err.status === 404) status = 404; else throw err; }
    //  S3 (پرچمِ `market.public_id`): نشانیِ پیشین ⇒ نشانیِ تازهٔ همان دکان؛ خاموش ⇒ همان ۴۰۴
    const moved = status === 404 ? await storefront.movedTo(slug) : null;
    if (moved) {
      return res.set('Cache-Control', 'no-store')
        .redirect(301, onHost ? `//${moved}.${config.storefront.domain}/` : `/store/${moved}`);
    }
    const seoPage = sf ? await storeSeo.page(sf, { lang: seoLang(lang) }) : null;
    if (seoPage) return res.status(seoPage.status).set('Cache-Control', 'no-cache').type('html').send(shell(seoPage.body));
    sendShell(res.status(status).set('Cache-Control', 'no-cache'), storeDir);
  };
  //  S3: پیوندِ پایدار (تبلیغ، API) — `/store?id=<ULID>` ⇒ نشانیِ امروز؛ نبود ⇒ همان راهِ امروز (پیش از static، که `/store` را به `/store/` می‌برد)
  app.get('/store', (req, res, next) => (typeof req.query.id !== 'string' ? next()
    : storefront.byPublicId(req.query.id).then((hit) => (hit
      ? res.set('Cache-Control', 'no-store').redirect(302, `/store/${hit.slug}`) : next())).catch(next)));
  //  پیوندِ کمپین و QR ِ دکان (MKT-10/11، `lib/campaigns.js`): `/store/go/<کد>` ⇒ ویترینِ
  //  همان دکان با `?c=<کد>`. «go» هرگز slug نیست (کمینه ۳ نویسه) و زیرِ همان پیشوندِ
  //  `/store` است ⇒ ACCOUNT_PREFIXES ِ پنلِ خانگی چیزِ تازه‌ای نمی‌خواهد. پرچمِ خاموش،
  //  کدِ ناشناخته و ویترینِ بسته همان پوستهٔ ۴۰۴ ِ ویترین‌اند. برنامه با
  //  `Accept: application/json` همان را JSON می‌گیرد. HEAD بازدید نمی‌شمارد.
  const campaigns = require('./lib/campaigns');
  const { clientIp } = require('./middleware/ratelimit');
  const goLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-go' });
  app.get('/store/go/:code', storeHeaders, goLimit, (req, res, next) => {
    const wantsJson = req.accepts(['html', 'json']) === 'json';
    res.set('Cache-Control', 'no-store');
    campaigns.resolve(req.params.code).then(async ({ campaign, store }) => {
      const live = campaign.archived !== true;
      if (req.method === 'GET') {
        await campaigns.recordVisit(campaign, store, { ip: clientIp(req), ua: req.get('user-agent') || '' })
          .catch((err) => console.error('[campaigns] visit', err.message));
      }
      const target = `/store/${store.slug}`;
      if (wantsJson) return res.json({ store: { slug: store.slug, path: target }, campaign: live ? campaign.code : null });
      res.redirect(302, live ? `${target}?c=${campaign.code}` : target);
    }).catch((err) => {
      if (err.status !== 404) return next(err);
      if (wantsJson) return res.status(404).json({ error: { code: 'store_not_found', message: 'این ویترین پیدا نشد' } });
      res.status(404).sendFile('index.html', { root: storeDir });
    });
  });
  //  ویترینِ روشن با پرچمِ سئو ⇒ سقفِ درخواست و `fn(sf)`؛ وگرنه `next()` — همان ۴۰۴ ِ امروزِ
  //  نشانیِ ناشناخته (سقف هم فقط وقتی روشن است شمرده می‌شود، تا خاموش هیچ فرقی نکند)
  const seoOnly = (slugOf, fn) => (req, res, next) => {
    const slug = slugOf(req);
    if (!slug) return next();
    storefront.open(slug).then(async (sf) => {
      if (!(await storeSeo.on(sf))) return next();
      return seoLimit(req, res, (err) => (err ? next(err) : fn(sf, req, res, next).catch(next)));
    }, (err) => (err.status === 404 ? next() : next(err))).catch(next);
  };
  const productPage = (sf, req, res) => storeSeo.page(sf, { productId: req.params.id, lang: seoLang(req.query.lang) })
    .then((p) => storeHeaders(req, res, () => res.status(p.status).set('Cache-Control', 'no-cache').type('html').send(shell(p.body))));
  const sitemapOf = (sf, req, res, next) => storeSeo.sitemap(sf, { page: req.query.page }) //  WEB-17: بیش از یک برگ ⇒ فهرستِ برگ‌ها و `?page=<n>`؛ برگِ نبوده یا بدشکل ⇒ همان ۴۰۴ ِ نشانیِ ناشناخته
    .then((x) => (x === false ? next() : res.set('Cache-Control', 'no-cache').type('application/xml').send(x)));
  const robotsOf = (sf, req, res) => storeSeo.robots(sf)
    .then((x) => res.set('Cache-Control', 'no-cache').type('text/plain').send(x));
  const hostSlug = (req) => storefront.slugFromHost(req.hostname);
  //  پوشِ وب ِ مشتری (`store/sw.js`، فقط push و لمسِ اعلان، بی fetch): روی نشانیِ خودِ دکان (`{slug}.vill3n.top/`) دامنهٔ
  //  کارگر همان `/` است ⇒ فقط همان‌جا `Service-Worker-Allowed: /`؛ مسیرِ `/store/<slug>` زیرِ `/store/` است و سرآیند نمی‌خواهد
  app.get('/store/sw.js', (req, res, next) => { if (hostSlug(req)) res.set('Service-Worker-Allowed', '/'); next(); });
  app.use('/store', storeHeaders, express.static(storeDir, staticOpts));
  app.get('/store/:slug', storeHeaders, (req, res, next) => storePage(req.params.slug, res, req.query.lang).catch(next));
  app.get('/store/:slug/p/:id', seoOnly((req) => req.params.slug, productPage));
  app.get('/store/:slug/sitemap.xml', seoOnly((req) => req.params.slug, sitemapOf));
  app.get('/p/:id', seoOnly(hostSlug, productPage));
  app.get('/sitemap.xml', seoOnly(hostSlug, sitemapOf));
  app.get('/robots.txt', seoOnly(hostSlug, robotsOf));
  //  وب‌اپِ قابلِ نصبِ همان دکان (WEB-14): نام از ویترینِ باز، شروع از نشانیِ خودش
  app.get('/store/:slug/manifest.webmanifest', (req, res, next) => storefront.open(req.params.slug).then((sf) => {
    const onHost = storefront.slugFromHost(req.hostname) === sf.slug;
    const start = onHost ? '/' : `/store/${sf.slug}`;
    res.set('Cache-Control', 'no-cache').type('application/manifest+json').send(JSON.stringify({
      name: sf.title, short_name: String(sf.title).slice(0, 12), lang: 'fa', dir: 'rtl',
      start_url: start, scope: onHost ? '/' : `/store/${sf.slug}`, display: 'standalone',
      background_color: '#F3F6FA', theme_color: '#108B81',
      icons: [
        { src: '/kit/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
        { src: '/kit/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
        { src: '/kit/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    }));
  }).catch((err) => (err.status === 404 ? res.status(404).json({ error: { code: 'store_not_found', message: 'این ویترین پیدا نشد' } }) : next(err))));
  app.get('/', (req, res, next) => {
    const slug = storefront.slugFromHost(req.hostname);
    if (!slug) return next();
    storeHeaders(req, res, () => storePage(slug, res, req.query.lang, true).catch(next));
  });
  // ---- بازارچه (WEB، §۲۳؛ پرچمِ `market.directory`) و کیتِ مشترکِ وبِ خریدار ----
  //  همان سرآیندهای سختِ ویترین (CSP بی unsafe-inline). بازارچهٔ خاموش ⇒ همان پوسته
  //  با ۴۰۴ و پیامِ «هنوز باز نیست». هیچ پیوندی به برنامهٔ مدیریتِ دکان نیست.
  const kitDir = path.join(__dirname, '..', 'public', 'kit');
  const marketDir = path.join(__dirname, '..', 'public', 'market');
  const market = require('./lib/market');
  app.use('/kit', storeHeaders, express.static(kitDir, staticOpts));
  app.get(['/market', '/market/'], storeHeaders, (req, res, next) => market.directoryOn().then((on) => {
    sendShell(res.status(on ? 200 : 404).set('Cache-Control', 'no-cache'), marketDir);
  }).catch(next));
  app.use('/market', storeHeaders, express.static(marketDir, staticOpts));

  // ---- فاکتورِ عمومی (QRِ هر فاکتور، `/r/<نشانِ دکان>/<توکن>`) ----
  //  همان CSP ِ سختِ ویترین، به‌علاوهٔ noindex و بی کش. پوسته همیشه همان است و
  //  هیچ پرسشی نمی‌زند؛ داده و ۴۰۴ ِ یکسان از `/api/receipt` می‌آید.
  const receiptDir = path.join(__dirname, '..', 'public', 'receipt');
  const { privateHeaders } = require('./routes/receipt-public');
  const receiptHeaders = (req, res, next) => storeHeaders(req, res, () => privateHeaders(req, res, next));
  app.use('/r', receiptHeaders, express.static(receiptDir, { index: false, maxAge: 0, etag: true }));
  app.get('/r/:tag/:token', receiptHeaders, (req, res) => res.sendFile('index.html', { root: receiptDir }));

  app.get('/', (req, res) => res.redirect('/admin/'));

  // ---- پورتالِ مشتری و SDK ----
  app.use('/portal', express.static(path.join(__dirname, '..', 'public', 'portal'), {
    index: 'index.html', maxAge: 0, etag: true,
  }));
  app.use('/sdk', express.static(path.join(__dirname, '..', 'public', 'sdk'), { maxAge: 0, etag: true }));

  app.use(notFoundHandler);
  app.use(errorHandler);

  // ---- کارهای دوره‌ای ----
  const HOUSEKEEPING_MS = 6 * 60 * 60 * 1000;
  //  پنلِ مرکزی ۴.۹: «خانه‌تکانی اجرا شد» + شکستِ هر زیرکار (`lib/job-beat.js`)؛ رفتار همان
  const beat = require('./lib/job-beat');
  const beatFail = (err) => beat.failed('housekeeping', err);
  const housekeeping = setInterval(() => {
    //  چند نمونه (`CLUSTER_ENABLED`): خانه‌تکانی فقط روی رهبر (`lib/cluster.js`)؛ تک‌نمونه ⇒ همیشه، مثلِ پیش
    if (!require('./lib/cluster').runsJobs()) return;
    beat.ran('housekeeping', HOUSEKEEPING_MS);
    pruneExpired().catch(err => { beatFail(err); console.error('[housekeeping]', err.message); });
    //  S6: جریانِ تغییرِ بازار فقط MARKET_CHANGES_KEEP_DAYS روز می‌ماند (`lib/market-changes.js`)
    require('./lib/market-changes').prune().catch(err => console.error('[market-changes]', err.message));
    //  پیام‌ها و رسانه‌های چت فقط CHAT_RELAY_DAYS روز روی سرور می‌مانند؛
    //  برنامهٔ پمپ رونوشتِ خودش را دارد
    require('./lib/chat-relay').sweep().catch(err => console.error('[chat-relay]', err.message));
    //  چتِ دکان: رسانهٔ بارگذاری‌شدهٔ بی‌پیام پس از یک ساعت (و نگهداری، اگر روشن شد)
    require('./lib/shop-chat').sweep().catch(err => console.error('[shop-chat]', err.message));
    //  RPT-14: روزهای کهنه‌تر از ۹۲ روزِ «جست‌وجوی بی‌نتیجه» (پرچم خاموش ⇒ جدولِ خالی)
    require('./lib/order-online-report').sweep().catch(err => console.error('[online-report]', err.message));
    //  CRM-15: سابقهٔ دسترسی فقط اگر CUSTOMER_ACCESS_RETENTION_DAYS > 0 (۰ = همان `audit_logs`: سرور پاک نمی‌کند)
    require('./lib/customer-access').sweep().catch(err => console.error('[customer-access]', err.message));
    subs.expireDue().catch(err => console.error('[subscriptions]', err.message));
    //  خبر دادن به کسی که اشتراکش دارد تمام می‌شود — پیش از آنکه قفل
    //  شود، نه بعدش. هر آستانه فقط یک بار، پس تکراری نمی‌رود.
    subs.notifyExpiring().catch(err => console.error('[expiry-notice]', err.message));
    //  و همان دو کار برای بخشِ پمپ. جا انداختنشان یعنی اشتراکِ پمپ
    //  هیچ‌وقت خودش تمام نمی‌شود — تا ابد فعال می‌ماند.
    subs.pump.expireDue().catch(err => console.error('[pump:subscriptions]', err.message));
    subs.pump.notifyExpiring().catch(err => console.error('[pump:expiry-notice]', err.message));
    //  رباتِ دورهٔ آزمایشی: هر پمپی که آغازِ دوره ندارد (شرحش در `lib/trial-sweep.js`)
    require('./lib/trial-sweep').sweep().catch(err => console.error('[trial-sweep]', err.message));
    //  رباتِ کدها: هر پمپی که هنوز کدِ هشت‌رقمیِ اپِ گوشی ندارد (`lib/station-access.js`)
    require('./lib/station-access').sweep().catch(err => console.error('[access-sweep]', err.message));
    //  سلامتِ برنامه‌ها و سایت‌های دیگر، از سرور سنجیده می‌شود نه از
    //  گوشیِ مدیر که ممکن است پشت فیلتر باشد
    require('./lib/managed-apps').checkHealth()
      .catch(err => console.error('[app-health]', err.message));
    //  PAY-21: تطبیقِ قصدهای حساب‌پیِ ۴۸ ساعتِ اخیر با خودِ ارائه‌دهنده (بی کلید ⇒ هیچ کاری)
    //  از همان درِ زمان‌بندِ ۵ دقیقه‌ای (`reconcileTick`) با قفلِ اجاره، تا با آن یا نمونهٔ دیگرِ سرور هم‌زمان اجرا نشود
    require('./lib/payment-intents').reconcileTick({ wide: true })
      .catch(err => console.error('[payments] تطبیق', err.message));
  }, HOUSEKEEPING_MS);
  if (housekeeping.unref) housekeeping.unref();
  beat.register('housekeeping', HOUSEKEEPING_MS);
  //  ⚠️ سرِ بالا آمدن هم — شش ساعت صبر برای پمپی که همین حالا بی‌مُهر است زیاد است
  setImmediate(() => require('./lib/trial-sweep').sweep().catch(err => console.error('[trial-sweep]', err.message)));
  setImmediate(() => require('./lib/station-access').sweep().catch(err => console.error('[access-sweep]', err.message)));
  app.locals.housekeeping = housekeeping;

  return app;
}

module.exports = { createApp };
