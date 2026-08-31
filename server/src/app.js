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
        });
      } catch (err) { next(err); }
    });
    api.use('/auth', require('./routes/auth'));
    api.use('/me', require('./routes/me'));
    api.use('/shop', require('./routes/shop'));
    api.use('/sync', require('./routes/sync'));
    api.use('/shop/sync', require('./routes/sync'));   // نام قدیمی
    api.use('/admin', require('./routes/admin'));
    api.use('/license', require('./routes/license'));

    // نام‌های قدیمی صفحه‌ی اشتراک
    const me = require('./routes/me');
    const billing = express.Router();
    billing.use(requireUser, optionalShop);
    billing.get('/status', me.subscriptionHandler);
    billing.get('/plans', me.plansHandler);
    api.use('/billing', billing);

    api.use('/', require('./routes/data'));
    return api;
  }

  app.use('/api', apiRouter());
  app.use('/api/v1', apiRouter());

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
  }, 6 * 60 * 60 * 1000);
  if (housekeeping.unref) housekeeping.unref();
  app.locals.housekeeping = housekeeping;

  return app;
}

module.exports = { createApp };
