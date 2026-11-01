'use strict';
const path = require('path');
const express = require('express');
const config = require('./config');
const { healthy, pruneExpired, now, driverName } = require('./db');
const migrate = require('./migrate');
const plans = require('./lib/plans');
const subs = require('./lib/subscriptions');
const { rateLimit } = require('./middleware/ratelimit');
const { notFoundHandler, errorHandler, notFound } = require('./middleware/errors');
const { requireUser, optionalShop, requireAdmin } = require('./middleware/auth');
const { redactPath } = require('./lib/log-redact');

async function createApp({ runMigrations = true } = {}) {
  const app = express();
  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', true);

  if (runMigrations) await migrate.run({ log: (m) => console.log(`[migrate] ${m}`) });
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
    console.warn(`[body] ${req.method} ${redactPath(req.originalUrl)} — ${err.type}`);
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

  //  ⚠️ همگام‌سازی سطلِ خودش را دارد (‎routes/sync-v1.js‎، شرحش در ‎config.rateLimit.syncMax‎)
  const general = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'general' });
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
    //  سفارشِ آنلاین (فاز ۴) — پیش از `/shop` تا `requireUser`ِ آن دو بار نرود؛
    //  زیرِ همان پیشوند، پس ACCOUNT_PREFIXESِ پنلِ خانگی دست نمی‌خورد
    //  میزِ سفارش (ORD-04/08/09) — پیش از `/shop/orders`؛ پشتِ پرچمِ خاموشِ `shop.orders.desk`
    api.use('/shop/orders/desk', require('./routes/order-desk'));
    //  کارِ گروهی روی سفارش‌ها (ORD-19) — پیش از `/shop/orders`؛ پشتِ پرچمِ خاموشِ `shop.orders.bulk`
    api.use('/shop/orders/bulk', require('./routes/order-bulk'));
    api.use('/shop/orders', require('./routes/orders'));
    //  ویترین (فاز ۴): تنظیمِ دکان زیرِ `/shop` (پیش از آن)، و درِ عمومی
    //  `/store` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی (دستورالعمل در PR)
    api.use('/shop/storefront', require('./routes/storefront-admin'));
    //  تأییدِ جایگزینِ جمع‌آوری توسطِ مشتری (PCK-05) — با توکنِ پیگیری؛ پشتِ پرچمِ خاموشِ `shop.orders.picking`
    api.use('/store', require('./routes/pick-substitutes-store'));
    api.use('/store', require('./routes/order-amend-store'));   // ORD-14: رضایتِ مشتری به ویرایشِ سفارش
    api.use('/store', require('./routes/storefront'));
    //  فاکتورِ نسخهٔ ۲: سربرگ و لوگوی دکان زیرِ `/shop` (پیش از آن)، و درِ عمومیِ QRِ
    //  هر فاکتور `/receipt` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی (دستورالعمل در PR)
    api.use('/shop/receipt-profile', require('./routes/shop-receipt'));
    //  ذخیرهٔ کهنه (#407): فقط پرچمِ `shop.ledger_merge` (پیش‌فرض خاموش) برای برنامه — پیش از `/shop`
    api.use('/shop/ledger-merge', require('./routes/shop-ledger-merge'));
    api.use('/receipt', require('./routes/receipt-public'));
    //  بازارچه (WEB، §۲۳) — پشتِ پرچمِ `market.directory`، پیش‌فرض خاموش. درِ دکان
    //  پیش از `/shop`؛ درِ عمومی `/market` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی
    api.use('/shop/market', require('./routes/market-admin'));
    api.use('/market', require('./routes/market'));
    //  بانکِ تصاویر (v3 ۳۷) — پشتِ پرچمِ `shop.image_bank`، پیش‌فرض خاموش
    api.use('/shop/images', require('./routes/shop-images'));
    //  تحویل و پیک (۲۲.۲ C) — همان قاعده؛ درهای پیک زیرِ `/courier` (۳.۱۱)
    api.use('/shop/delivery', require('./routes/delivery'));
    api.use('/courier', require('./routes/courier'));
    //  تخفیف‌ها و قاعده‌های قیمت (PRM) — کلیدِ دکان، پیش‌فرض خاموش
    api.use('/shop/promos', require('./routes/shop-promos'));
    //  STF-07/STF-08: ورود و خروجِ کارمند و نرخ‌های پورسانت — پشتِ `shop.staff_time`، پیش‌فرض خاموش
    api.use('/shop/staff-time', require('./routes/staff-time'));
    //  موتورِ تازهٔ موجودیِ برنامهٔ دکان (#315) — فقط خواندنِ پرچمِ `shop.stock_v2`، پیش‌فرض خاموش
    api.use('/shop/stock-ledger', require('./routes/shop-stock-ledger'));
    //  پرداختِ درگاهی (حساب‌پی، PAY-21) — درِ دکان پیش از `/shop`، پشتِ پرچمِ خاموشِ `pay.hesabpay`؛
    //  وب‌هوکِ بی‌توکنِ ارائه‌دهنده زیرِ `/payments` — پیشوندِ تازه ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی
    api.use('/shop/payments', require('./routes/shop-payments'));
    api.use('/payments', require('./routes/payments-webhook'));
    api.use('/admin/payments', require('./routes/admin-payments'));
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
     *  ⚠️ پیش از `routes/data` — آن روتر روی `/` است و روی **هر** مسیری
     *  `requireUser, requireShop` می‌زند؛ هر چیزی که بعدش سوار شود برای
     *  توکنِ پمپ یا مهمان هیچ‌وقت دیده نمی‌شود (همان تلهٔ `/api/v1`).
     */
    api.use('/admin', require('./routes/admin-notices'));
    api.use('/admin', require('./routes/admin-sales'));
    //  ماژولِ ۴.۵ ِ پنلِ مرکزی: مالیِ مرکزی (درآمدِ اشتراک، سندِ اصلاحی، بدهی، مغایرت، خروجی) — `requireAdmin` ِ خودش
    api.use('/admin/finance', require('./routes/admin-finance'));
    //  بانکِ مرکزیِ تصاویر (v3 ۳۷.۳) — پشتِ پرچمِ `image_bank.central`، پیش‌فرض خاموش
    api.use('/admin/image-bank', require('./routes/admin-image-bank'));
    //  LOC-04: جدولِ تعطیلاتِ رسمی — خواندنِ همگانی، نوشتنِ مدیرِ ارشد
    api.use('/calendar', require('./routes/calendar'));
    api.use('/admin/calendar', require('./routes/admin-calendar'));
    /*
     *  ⛔ مسیرِ ناشناختهٔ مدیر ⇒ ۴۰۴ `route_not_found`، نه ۴۰۱ (چسبِ پنلِ مرکزی، بندِ ۱۲.۶ ِ
     *  ADMIN-CONTROL-PLANE). بی این خط، درخواستِ مدیرِ **معتبر** به مسیری که این سرور ندارد به
     *  `routes/data` ِ پایین (روی `/`، با `requireUser`) می‌افتاد و ۴۰۱ `invalid_token` می‌گرفت؛ پلِ
     *  پنل آن را «نشستِ ارشد تمام شد» می‌خواند. `requireAdmin` همین‌جا دوباره: بی‌توکن و توکنِ دکان
     *  همان ۴۰۱ ِ همیشه را می‌گیرند تا وجود/نبودِ مسیرِ مدیر به بیرون گفته نشود. هر مسیرِ واقعی
     *  بالاتر سوار است، پس این خط به هیچ‌کدام نمی‌رسد.
     */
    api.use('/admin', requireAdmin, (req, res, next) => next(notFound('این مسیرِ مدیر روی این سرور نیست', 'route_not_found')));
    api.use('/portal', require('./routes/portal'));
    api.use('/rep', require('./routes/rep'));       // شورا چ۳: نمایندهٔ فروش — فقط فروش‌های خودش
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
  //  پوسته ایستاست و داده از `/api/store/:slug` می‌آید (همان مبدأ ⇒ بی CORS).
  //  ویترینِ خاموش یا ناموجود ⇒ همان پوسته با وضعیتِ ۴۰۴.
  const storeDir = path.join(__dirname, '..', 'public', 'store');
  const storefront = require('./lib/storefront');
  const storeHeaders = (req, res, next) => {
    res.set('Content-Security-Policy',
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; " +
      "connect-src 'self'; manifest-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    res.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=(self), payment=()');
    next();
  };
  //  WEB-17/20 (`lib/store-seo.js`، پرچمِ خاموشِ `shop.storefront.seo`): روشن ⇒ همان پوسته با
  //  برچسب‌های Open Graph، hreflang و JSON-LD؛ خاموش ⇒ همان پروندهٔ ایستا، بیت‌به‌بیت.
  const storeSeo = require('./lib/store-seo');
  const seoLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-seo' });
  const storePage = async (slug, res, lang) => {
    let status = 200;
    let sf = null;
    try { sf = await storefront.open(slug); } catch (err) { if (err.status === 404) status = 404; else throw err; }
    const seoPage = sf ? await storeSeo.page(sf, { lang }) : null;
    if (seoPage) return res.status(seoPage.status).set('Cache-Control', 'no-cache').type('html').send(seoPage.body);
    res.status(status).set('Cache-Control', 'no-cache').sendFile('index.html', { root: storeDir });
  };
  //  ویترینِ روشن با پرچمِ سئو ⇒ سقفِ درخواست و `fn(sf)`؛ وگرنه `next()` — همان ۴۰۴ ِ امروزِ
  //  نشانیِ ناشناخته (سقف هم فقط وقتی روشن است شمرده می‌شود، تا خاموش هیچ فرقی نکند)
  const seoOnly = (slugOf, fn) => (req, res, next) => {
    const slug = slugOf(req);
    if (!slug) return next();
    storefront.open(slug).then(async (sf) => {
      if (!(await storeSeo.on(sf))) return next();
      return seoLimit(req, res, (err) => (err ? next(err) : fn(sf, req, res).catch(next)));
    }, (err) => (err.status === 404 ? next() : next(err))).catch(next);
  };
  const productPage = (sf, req, res) => storeSeo.page(sf, { productId: req.params.id, lang: req.query.lang })
    .then((p) => storeHeaders(req, res, () => res.status(p.status).set('Cache-Control', 'no-cache').type('html').send(p.body)));
  const sitemapOf = (sf, req, res) => storeSeo.sitemap(sf)
    .then((x) => res.set('Cache-Control', 'no-cache').type('application/xml').send(x));
  const robotsOf = (sf, req, res) => storeSeo.robots(sf)
    .then((x) => res.set('Cache-Control', 'no-cache').type('text/plain').send(x));
  const hostSlug = (req) => storefront.slugFromHost(req.hostname);
  app.use('/store', storeHeaders, express.static(storeDir, { index: false, maxAge: 0, etag: true }));
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
    storeHeaders(req, res, () => storePage(slug, res, req.query.lang).catch(next));
  });
  // ---- بازارچه (WEB، §۲۳؛ پرچمِ `market.directory`) و کیتِ مشترکِ وبِ خریدار ----
  //  همان سرآیندهای سختِ ویترین (CSP بی unsafe-inline). بازارچهٔ خاموش ⇒ همان پوسته
  //  با ۴۰۴ و پیامِ «هنوز باز نیست». هیچ پیوندی به برنامهٔ مدیریتِ دکان نیست.
  const kitDir = path.join(__dirname, '..', 'public', 'kit');
  const marketDir = path.join(__dirname, '..', 'public', 'market');
  const market = require('./lib/market');
  app.use('/kit', storeHeaders, express.static(kitDir, { index: false, maxAge: 0, etag: true }));
  app.get(['/market', '/market/'], storeHeaders, (req, res, next) => market.directoryOn().then((on) => {
    res.status(on ? 200 : 404).set('Cache-Control', 'no-cache').sendFile('index.html', { root: marketDir });
  }).catch(next));
  app.use('/market', storeHeaders, express.static(marketDir, { index: false, maxAge: 0, etag: true }));

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
    beat.ran('housekeeping', HOUSEKEEPING_MS);
    pruneExpired().catch(err => { beatFail(err); console.error('[housekeeping]', err.message); });
    //  پیام‌ها و رسانه‌های چت فقط CHAT_RELAY_DAYS روز روی سرور می‌مانند؛
    //  برنامهٔ پمپ رونوشتِ خودش را دارد
    require('./lib/chat-relay').sweep().catch(err => console.error('[chat-relay]', err.message));
    //  چتِ دکان: رسانهٔ بارگذاری‌شدهٔ بی‌پیام پس از یک ساعت (و نگهداری، اگر روشن شد)
    require('./lib/shop-chat').sweep().catch(err => console.error('[shop-chat]', err.message));
    //  RPT-14: روزهای کهنه‌تر از ۹۲ روزِ «جست‌وجوی بی‌نتیجه» (پرچم خاموش ⇒ جدولِ خالی)
    require('./lib/order-online-report').sweep().catch(err => console.error('[online-report]', err.message));
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
