'use strict';
const path = require('path');
const express = require('express');
const config = require('./config');
const ck = require('./lib/crypto-keys');
const { getDb, pruneExpired } = require('./db');
const { rateLimit } = require('./middleware/ratelimit');
const { notFoundHandler, errorHandler } = require('./middleware/errors');
const { requireUser, requireFeature } = require('./middleware/auth');

async function createApp() {
  const app = express();
  app.disable('x-powered-by');
  if (config.trustProxy) app.set('trust proxy', true);

  getDb();
  pruneExpired();
  app.locals.keys = await ck.loadKeyPair(config.privateKeyPath, config.publicKeyPath);

  app.use(express.json({ limit: '64kb' }));

  // ---- سرآیندهای امنیتی ----
  app.use((req, res, next) => {
    res.set('X-Content-Type-Options', 'nosniff');
    res.set('X-Frame-Options', 'DENY');
    res.set('Referrer-Policy', 'no-referrer');
    res.set('Cross-Origin-Opener-Policy', 'same-origin');
    next();
  });

  // ---- CORS: فقط دامنه‌های مجاز ----
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (origin) {
      if (config.corsOrigins.includes(origin)) {
        res.set('Access-Control-Allow-Origin', origin);
        res.set('Vary', 'Origin');
        res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
        res.set('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
        res.set('Access-Control-Max-Age', '600');
      } else if (req.method === 'OPTIONS') {
        // دامنه‌ی ناشناس: سرآیند CORS نمی‌دهیم و مرورگر خودش جلویش را می‌گیرد
        return res.status(403).end();
      }
    }
    if (req.method === 'OPTIONS') return res.status(204).end();
    next();
  });

  app.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'general' }));

  // ---- مسیرها ----
  app.get('/health', (req, res) => res.json({ ok: true, time: Date.now() }));

  const api = express.Router();
  api.use('/auth', require('./routes/auth'));
  api.use('/license', require('./routes/license'));
  api.use('/admin', require('./routes/admin'));

  /**
   * نمونه‌ی مسیر محافظت‌شده با قابلیت.
   * هر API آنلاینی که به قابلیت اشتراکی وابسته است باید همین‌طور پشت
   * requireFeature برود — این همان «بررسی دوباره در سمت سرور» است که
   * دور زدن قفل در Frontend را بی‌اثر می‌کند.
   */
  api.get('/protected/reports', requireUser, requireFeature('reports'), (req, res) => {
    res.json({ ok: true, message: 'دسترسی گزارشات تأیید شد', subscription: req.subscriptionState });
  });
  api.get('/protected/backup', requireUser, requireFeature('backup'), (req, res) => {
    res.json({ ok: true, message: 'دسترسی پشتیبان‌گیری تأیید شد' });
  });

  app.use('/api/v1', api);

  // ---- پنل مدیریت ----
  app.use('/admin', express.static(path.join(__dirname, '..', 'public', 'admin'), {
    index: 'index.html', maxAge: 0, etag: true,
  }));
  app.get('/', (req, res) => res.redirect('/admin/'));

  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}

module.exports = { createApp };
