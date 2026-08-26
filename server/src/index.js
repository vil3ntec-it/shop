#!/usr/bin/env node
'use strict';
/**
 * راه‌اندازی سرور.
 *
 * برای اجرای ۲۴ ساعته: خطاهای پیش‌بینی‌نشده لاگ می‌شوند ولی سرور را
 * نمی‌خوابانند، و هنگام خاموش شدن، درخواست‌های در جریان تمام می‌شوند و
 * بعد اتصال دیتابیس بسته می‌شود (Graceful Shutdown).
 */
const config = require('./config');
const { createApp } = require('./app');
const { closeDb, healthy } = require('./db');
const backup = require('./lib/backup');

async function main() {
  const problems = config.validate();
  if (problems.length) {
    console.error('پیکربندی ناقص است:');
    for (const p of problems) console.error(' -', p);
    process.exit(1);
  }

  if (!(await healthy())) {
    console.error('اتصال به PostgreSQL برقرار نشد. DATABASE_URL را بررسی کنید.');
    process.exit(1);
  }

  const app = await createApp();
  const server = app.listen(config.port, config.host, () => {
    const where = config.serverUrl || `http://${config.host}:${config.port}`;
    console.log(`سرور فروشگاه روی ${where} بالا آمد (${config.env})`);
    console.log(`بررسی سلامت: ${where.replace(/\/$/, '')}/api/health`);
  });
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;

  const backupTimer = backup.schedule();

  let closing = false;
  async function shutdown(signal) {
    if (closing) return;
    closing = true;
    console.log(`[${signal}] در حال خاموش شدن…`);
    if (backupTimer) clearInterval(backupTimer);
    server.close(async () => {
      await closeDb();
      console.log('خاموش شد.');
      process.exit(0);
    });
    // اگر اتصالی گیر کرد، بعد از ۲۰ ثانیه به هر حال می‌بندیم
    setTimeout(() => process.exit(0), 20_000).unref();
  }

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('unhandledRejection', (err) => console.error('[unhandledRejection]', err));
  process.on('uncaughtException', (err) => {
    console.error('[uncaughtException]', err);
    // خطای ناشناخته سرور را در وضعیت نامعلوم می‌گذارد؛ بهتر است تمیز
    // بسته شود و مدیر فرایند (systemd/PM2/Docker) دوباره بالا بیاورد.
    shutdown('uncaughtException');
  });
}

main().catch(err => {
  console.error('راه‌اندازی شکست خورد:', err);
  process.exit(1);
});
