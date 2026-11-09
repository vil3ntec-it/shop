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
const live = require('./lib/sync-v1-live');
const cluster = require('./lib/cluster');

async function main() {
  const problems = config.validate();
  if (problems.length) {
    console.error('پیکربندی ناقص است:');
    for (const p of problems) console.error(' -', p);
    process.exit(1);
  }
  //  هشدار — نه توقف: پشتیبانِ کلِ دیتابیس بی رمز روی همان دیسکی می‌نشیند
  //  که خودِ دیتابیس هست؛ هر کس به پوشهٔ پشتیبان برسد، همه‌چیز را دارد.
  //  SEC-AUTH-06/15: سرورِ بی NODE_ENV رفتارِ production دارد (کد در پاسخ نیست) ولی سنجشِ رازهای
  //  `validate()` را نمی‌گذراند؛ بی API_SECRET/OTP_SECRET کدِ ثابتِ شاگرد و کدهای دیگر با رشتهٔ ثابتِ
  //  سورس مهر می‌شوند و از روی شناسهٔ دکان ساختنی‌اند. نمی‌ایستیم (سرورِ نصب‌شده بیرون نیفتد)، می‌گوییم.
  if (config.env !== 'production' && !config.devCodes) {
    console.warn('⚠️ NODE_ENV تنظیم نشده است؛ روی سرورِ واقعی NODE_ENV=production بگذارید تا سنجشِ رازها اجرا شود.');
  }
  if (!config.devCodes && (!config.secrets.api || !config.secrets.otp)) {
    console.warn('⚠️ API_SECRET یا OTP_SECRET خالی است؛ کدهای شاگرد و یک‌بارمصرف با رازِ ثابتِ سورس مهر می‌شوند. هر دو را (۲۴+ نویسه) بگذارید.');
  }
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
  //  F0-28: پاکت‌های رازِ پشتیبانِ مهرشده — پس از migrate، پیش از کلیدِ مجوز و نخستین درخواست؛ بی کلید پیامِ بلند، هرگز توقف
  await require('./lib/backup-secrets').startup();
  //  O-18: کلیدِ امضای مجوز — یک خطِ روشن؛ نبودنش در production فقط صدورِ مجوز را ۵۰۳ می‌کند، نه کلِ سرور را
  await require('./lib/license').startupCheck();
  //  HTTPS ِ مستقیم فقط با `TLS_*` (سرورِ شبکهٔ دکان)؛ بی آن همان http ِ امروز (پشتِ تونل/Caddy)
  const tlsListener = require('./lib/tls-listener');
  const tlsOpts = tlsListener.options(config.tls);
  const onListen = () => {
    const where = config.serverUrl || `${tlsOpts ? 'https' : 'http'}://${config.host}:${config.port}`;
    console.log(`سرور فروشگاه روی ${where} بالا آمد (${config.env}، دیتابیس: ${driverName()})`);
    console.log(`بررسی سلامت: ${where.replace(/\/$/, '')}/api/health`);
    if (tlsOpts) tlsListener.announce(tlsOpts, config.port, config.host);
  };
  const server = tlsOpts
    ? require('node:https').createServer(tlsOpts, app).listen(config.port, config.host, onListen)
    : app.listen(config.port, config.host, onListen);
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;

  /*
   *  کارگرهای پس‌زمینه (`src/workers.js`) — `cluster.jobs`: تک‌نمونه (پیش‌فرض) ⇒ همه همین حالا و به همین
   *  ترتیب، مثلِ پیش؛ چند نمونه (`CLUSTER_ENABLED=1`) ⇒ فقط روی رهبر (قفلِ advisory ِ `lib/cluster.js`).
   */
  cluster.jobs(require('./workers').backgroundJobs());
  //  نسخهٔ schemaی هر بخش از کد به دفتر می‌رود — پنل و مدیر از همان‌جا می‌خوانند
  require('./lib/sync-v1').syncSchemaTable().catch((e) => console.error('[sync] app_schema', e.message));
  //  «چیزی عوض شد» ⇒ دستگاه‌های دیگرِ همان حساب Pull می‌کنند
  live.attach(server);
  //  چند نمونه: گذرگاهِ LISTEN/NOTIFY و رقابت برای رهبری (تک‌نمونه ⇒ هیچ)
  if (await cluster.start()) {
    console.log(`[cluster] نمونهٔ ${cluster.instanceId} — سقفِ نرخ: ${config.rateLimit.store}، قفلِ گاوصندوق: ${config.accountLock.scope}`);
  }

  let closing = false;
  async function shutdown(signal) {
    if (closing) return;
    closing = true;
    console.log(`[${signal}] در حال خاموش شدن…`);
    //  همهٔ کارگرهای راه‌افتادهٔ همین نمونه (و رها کردنِ رهبری و گذرگاه، اگر خوشه است)
    cluster.stopJobs();
    await cluster.stop().catch(() => {});
    server.close(async () => {
      await require('./lib/read-replica').stop();
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
