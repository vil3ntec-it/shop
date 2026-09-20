'use strict';
const path = require('path');
const express = require('express');
const config = require('./config');
const { healthy, pruneExpired, now, driverName } = require('./db');
const migrate = require('./migrate');
const plans = require('./lib/plans');
const subs = require('./lib/subscriptions');
const { rateLimit } = require('./middleware/ratelimit');
const { notFoundHandler, errorHandler } = require('./middleware/errors');
const { requireUser, optionalShop } = require('./middleware/auth');

async function createApp({ runMigrations = true } = {}) {
  const app = express();
  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', true);

  if (runMigrations) await migrate.run({ log: (m) => console.log(`[migrate] ${m}`) });
  await plans.seedDefaults();
  //  قالب‌های آمادهٔ مرکزِ اعلان (خوش‌آمد، رو به پایان، تمدید شد، …)
  await require('./lib/notices').seedTemplates();
  await pruneExpired();
  //  مدیر از محیط — نصبی که پنلِ خانگی خودش بالا می‌آورد و ترمینالی در کار نیست
  await require('./lib/admin-bootstrap').ensureAdmin({
    username: process.env.ADMIN_BOOTSTRAP_USER,
    password: process.env.ADMIN_BOOTSTRAP_PASSWORD,
    log: (m) => console.log(`[admin] ${m}`),
  });

  app.use(express.json({ limit: '2mb' }));

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
    if (!err || (err.type !== 'entity.parse.failed' && err.type !== 'entity.too.large')) {
      return next(err);
    }
    const tooBig = err.type === 'entity.too.large';
    console.warn(`[body] ${req.method} ${req.path.slice(0, 120)} — ${err.type}`);
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
        res.set('Access-Control-Allow-Headers',
          'Content-Type, Authorization, Idempotency-Key, X-App, X-App-Id, X-Device, X-App-Version, X-Request-Id');
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
  // نمی‌شود؛ حتی مسیرهایی که شناسه دارند کوتاه می‌شوند.
  app.use((req, res, next) => {
    const started = Date.now();
    res.on('finish', () => {
      const ms = Date.now() - started;
      if (res.statusCode >= 400 || ms > 1000 || config.env !== 'production') {
        const path = req.originalUrl.split('?')[0].slice(0, 120);
        console.log(`${req.method} ${path} ${res.statusCode} ${ms}ms`);
      }
    });
    next();
  });

  app.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'general' }));

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
          otpEnabled: config.otp.provider !== 'off',
          trialDays: Number(cfg.trial_days || 0),
          whatsapp: { number: cfg.whatsapp_number || '', message: cfg.whatsapp_message || '' },
          minAppVersion: cfg.min_app_version || '',
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
          trialDays: Number((app === 'pump' ? cfg.pump_trial_days : cfg.trial_days) || 0),
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
    api.use('/sync/v1', require('./routes/sync-v1'));
    api.use('/errors', require('./routes/errors'));
    api.use('/location', require('./routes/location'));
    api.use('/me', require('./routes/me'));
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
     *  ⚠️ پیش از `routes/data` — آن روتر روی `/` است و روی **هر** مسیری
     *  `requireUser, requireShop` می‌زند؛ هر چیزی که بعدش سوار شود برای
     *  توکنِ پمپ یا مهمان هیچ‌وقت دیده نمی‌شود (همان تلهٔ `/api/v1`).
     */
    api.use('/admin', require('./routes/admin-notices'));
    api.use('/admin', require('./routes/admin-sales'));
    api.use('/portal', require('./routes/portal'));
    api.get('/downloads', require('./routes/portal').downloadsHandler);

    api.use('/', require('./routes/data'));
    return api;
  }

  /*
   *  ترتیب این دو خط مهم است و اتفاقی نیست.
   *
   *  اگر `/api` اول سوار شود، درخواستِ `/api/v1/health` را هم **همان**
   *  می‌قاپد و داخلش مسیر می‌شود `/v1/health`؛ چون چنین مسیری نیست، به
   *  آخرین لایه (`routes/data` روی `/`) می‌رسد که توکن می‌خواهد و همه‌چیز
   *  ۴۰۱ برمی‌گشت. یعنی کل `/api/v1/…` — همان چیزی که برنامه‌ی وب و
   *  اندروید صدا می‌زنند — بسته بود.
   *
   *  پس نشانیِ درازتر اول.
   */
  app.use('/api/v1', apiRouter());
  app.use('/api', apiRouter());

  // ---- پنل مدیریت ----
  app.use('/admin', express.static(path.join(__dirname, '..', 'public', 'admin'), {
    index: 'index.html', maxAge: 0, etag: true,
  }));
  app.get('/', (req, res) => res.redirect('/admin/'));

  // ---- پورتالِ مشتری و SDK ----
  app.use('/portal', express.static(path.join(__dirname, '..', 'public', 'portal'), {
    index: 'index.html', maxAge: 0, etag: true,
  }));
  app.use('/sdk', express.static(path.join(__dirname, '..', 'public', 'sdk'), { maxAge: 0, etag: true }));

  app.use(notFoundHandler);
  app.use(errorHandler);

  // ---- کارهای دوره‌ای ----
  const housekeeping = setInterval(() => {
    pruneExpired().catch(err => console.error('[housekeeping]', err.message));
    subs.expireDue().catch(err => console.error('[subscriptions]', err.message));
    //  خبر دادن به کسی که اشتراکش دارد تمام می‌شود — پیش از آنکه قفل
    //  شود، نه بعدش. هر آستانه فقط یک بار، پس تکراری نمی‌رود.
    subs.notifyExpiring().catch(err => console.error('[expiry-notice]', err.message));
    //  و همان دو کار برای بخشِ پمپ. جا انداختنشان یعنی اشتراکِ پمپ
    //  هیچ‌وقت خودش تمام نمی‌شود — تا ابد فعال می‌ماند.
    subs.pump.expireDue().catch(err => console.error('[pump:subscriptions]', err.message));
    subs.pump.notifyExpiring().catch(err => console.error('[pump:expiry-notice]', err.message));
    //  سلامتِ برنامه‌ها و سایت‌های دیگر، از سرور سنجیده می‌شود نه از
    //  گوشیِ مدیر که ممکن است پشت فیلتر باشد
    require('./lib/managed-apps').checkHealth()
      .catch(err => console.error('[app-health]', err.message));
  }, 6 * 60 * 60 * 1000);
  if (housekeeping.unref) housekeeping.unref();
  app.locals.housekeeping = housekeeping;

  return app;
}

module.exports = { createApp };
