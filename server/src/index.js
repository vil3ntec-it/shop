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
const { closeDb, healthy, isPglite, pgliteDir, driverName } = require('./db');
const backup = require('./lib/backup');
const outbox = require('./lib/login-outbox');
const live = require('./lib/sync-v1-live');

async function main() {
  const problems = config.validate();
  if (problems.length) {
    console.error('پیکربندی ناقص است:');
    for (const p of problems) console.error(' -', p);
    process.exit(1);
  }
  //  هشدار — نه توقف: پشتیبانِ کلِ دیتابیس بی رمز روی همان دیسکی می‌نشیند
  //  که خودِ دیتابیس هست؛ هر کس به پوشهٔ پشتیبان برسد، همه‌چیز را دارد.
  if (config.env === 'production' && config.backup.enabled && !config.backup.passphrase) {
    console.warn('⚠️ BACKUP_PASSPHRASE خالی است؛ پشتیبان‌های دیتابیس رمز نمی‌شوند.');
  }

  if (!(await healthy())) {
    console.error(isPglite()
      ? `دیتابیسِ درون‌فرآیندی (PGlite) باز نشد. پوشه: ${pgliteDir() || 'حافظه'}`
      : 'اتصال به PostgreSQL برقرار نشد. DATABASE_URL را بررسی کنید.');
    process.exit(1);
  }

  //  کفِ «فروشِ تحویل» برای دکان‌هایی که کدِ پیشین سفارش را خودکار کم می‌کرد (shop.orders.ledger /
  //  shop.catalog) = لحظهٔ همین راه‌اندازی — **پیش از** نخستین درخواست، وگرنه config ِ زودرسِ برنامه کفِ
  //  دیرتری می‌گذاشت و تحویلِ میانِ این دو فروش نمی‌گرفت (lib/order-ledger.js). شکست ⇒ سرور بالا
  //  می‌آید و claimFloor همان لحظهٔ راه‌اندازی را به کار می‌برد.
  try {
    const n = await require('./lib/order-ledger').claimFloorsAtStart();
    if (n) console.log(`[orders] کفِ تحویل برای ${n} دکان گذاشته شد`);
  } catch (e) {
    console.error('[orders] کفِ تحویل', e.message);
  }

  const app = await createApp();
  const server = app.listen(config.port, config.host, () => {
    const where = config.serverUrl || `http://${config.host}:${config.port}`;
    console.log(`سرور فروشگاه روی ${where} بالا آمد (${config.env}، دیتابیس: ${driverName()})`);
    console.log(`بررسی سلامت: ${where.replace(/\/$/, '')}/api/health`);
  });
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;

  /*
   *  Workerِ ایمیلِ کدِ ورود — درخواستِ کد همان لحظه ۲۰۰ می‌گیرد و ارسال
   *  این‌جا با تلاشِ دوباره و مدارشکن انجام می‌شود. بی این، کاربر پشتِ
   *  سرویسِ ایمیل منتظر می‌ماند و هر کندیِ ایمیل، کندیِ ورود می‌شد.
   */
  outbox.start();
  //  کارگرِ اعلانِ سفارش (v3 §۳۰) — روی خودِ سرور، بی‌نیاز به کامپیوترِ دکان؛
  //  بی پرچمِ `shop.order_notify` صفش خالی است و کاری نمی‌کند
  const orderNotify = require('./lib/order-notify');
  orderNotify.start();
  //  ORD-05: زمان‌سنجِ قبولِ سفارشِ ویترین (۱۵ دقیقه؛ فقط سفارشِ مُهرخورده)
  require('./lib/order-timeout').start();
  //  M5: جاروی رزروِ نرمِ گذشته (بی رزروِ نرم ⇒ یک پرسشِ خالی در دقیقه)
  require('./lib/stock-reserve').start();
  //  نسخهٔ schemaی هر بخش از کد به دفتر می‌رود — پنل و مدیر از همان‌جا می‌خوانند
  require('./lib/sync-v1').syncSchemaTable().catch((e) => console.error('[sync] app_schema', e.message));
  //  «چیزی عوض شد» ⇒ دستگاه‌های دیگرِ همان حساب Pull می‌کنند
  live.attach(server);

  const backupTimer = backup.schedule();
  //  PGlite autovacuum ندارد ⇒ آمارِ planner را خودمان تازه می‌کنیم (PERF-03)؛ روی PostgreSQL هیچ
  const pgliteStats = require('./lib/pglite-stats').schedule();
  //  زمان‌بندِ مرکزِ اعلان: هر دقیقه اعلان‌های وقت‌رسیده، و روزی یک بار
  //  «رو به پایان / منقضی»
  const notices = require('./lib/notices');
  notices.startRunner();
  //  (جاروی رزروهای سفارش برداشته شد: سفارشِ آنلاین دیگر موجودی رزرو نمی‌کند — lib/stock.js)
  //  باتِ تلگرامِ پمپ — کدش در ریپوی پمپ است (bots/pump، lib/bots.js)؛ نبود ⇒ بی بات.
  //  تا رمزش از پنل داده نشده، فقط هر چند ثانیه نگاه می‌کند.
  const telegram = require('./lib/bots').pump();
  if (!telegram.available) console.log('[bots] باتِ پمپ نصب نیست (bots/pump) — سرور بی بات بالا می‌آید');
  if (config.telegram.autoStart) telegram.start();

  let closing = false;
  async function shutdown(signal) {
    if (closing) return;
    closing = true;
    console.log(`[${signal}] در حال خاموش شدن…`);
    if (backupTimer) clearInterval(backupTimer);
    if (pgliteStats) pgliteStats.stop();
    outbox.stop();
    orderNotify.stop();
    notices.stopRunner();
    telegram.stop();
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
