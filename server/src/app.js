'use strict';
const path = require('path');
const express = require('express');
const config = require('./config');
const { healthy, pruneExpired, now } = require('./db');
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
  await pruneExpired();

  app.use(express.json({ limit: '2mb' }));

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
        res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization, Idempotency-Key');
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
  const health = async (req, res) => {
    const db = await healthy();
    res.status(db ? 200 : 503).json({
      ok: db,
      server: 'online',
      database: db ? 'connected' : 'unavailable',
      version: require('../package.json').version,
      time: now(),
      uptimeSeconds: Math.round(process.uptime()),
    });
  };
  app.get('/health', health);

  // ---- API ----
  function apiRouter() {
    const api = express.Router();
    api.get('/health', health);

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
        const cfg = await plans.allConfig();
        const number = cfg.whatsapp_number || '';
        const message = cfg.whatsapp_message || '';
        const digits = String(number).replace(/[^0-9]/g, '').replace(/^0/, '93');
        const list = await plans.listPlans();
        res.json({
          plans: list.map(p => ({
            ...p,
            //  قیمتِ روزانه اینجا حساب می‌شود، نه در سه برنامه‌ی جدا
            pricePerDay: p.days > 0 && p.price > 0
              ? Math.round((p.price / p.days) * 10) / 10 : null,
            whatsappUrl: digits
              ? `https://wa.me/${digits}?text=${encodeURIComponent(`${message} (${p.title})`)}`
              : '',
          })),
          currency: cfg.currency || 'افغانی',
          trialDays: Number(cfg.trial_days || 0),
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
    api.use('/auth', require('./routes/auth'));
    api.use('/location', require('./routes/location'));
    api.use('/me', require('./routes/me'));
    api.use('/shop', require('./routes/shop'));
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

  app.use(notFoundHandler);
  app.use(errorHandler);

  // ---- کارهای دوره‌ای ----
  const housekeeping = setInterval(() => {
    pruneExpired().catch(err => console.error('[housekeeping]', err.message));
    subs.expireDue().catch(err => console.error('[subscriptions]', err.message));
    //  خبر دادن به کسی که اشتراکش دارد تمام می‌شود — پیش از آنکه قفل
    //  شود، نه بعدش. هر آستانه فقط یک بار، پس تکراری نمی‌رود.
    subs.notifyExpiring().catch(err => console.error('[expiry-notice]', err.message));
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
