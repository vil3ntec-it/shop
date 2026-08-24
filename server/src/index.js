'use strict';
const config = require('./config');
const { createApp } = require('./app');
const { pruneExpired, closeDb } = require('./db');

(async () => {
  try {
    const app = await createApp();
    const server = app.listen(config.port, config.host, () => {
      console.log(`توحید | سرور License روی http://${config.host}:${config.port} بالا آمد`);
      console.log(`پنل مدیریت: http://${config.host}:${config.port}/admin/`);
      console.log(`منطقه زمانی پیش‌فرض: ${config.defaults.timezone}`);
      if (!config.corsOrigins.length) {
        console.warn('هشدار: CORS_ORIGINS خالی است — برنامه‌ای که از دامنه دیگری باز شود نمی‌تواند وصل شود.');
      }
    });

    const timer = setInterval(() => {
      try { pruneExpired(); } catch (e) { console.error('[prune]', e.message); }
    }, 6 * 60 * 60 * 1000);
    timer.unref();

    const shutdown = (sig) => {
      console.log(`${sig} دریافت شد — خاموش کردن سرور...`);
      server.close(() => { closeDb(); process.exit(0); });
      setTimeout(() => process.exit(1), 10000).unref();
    };
    process.on('SIGINT', () => shutdown('SIGINT'));
    process.on('SIGTERM', () => shutdown('SIGTERM'));
  } catch (e) {
    console.error('راه‌اندازی سرور ناموفق بود:', e.message);
    process.exit(1);
  }
})();
