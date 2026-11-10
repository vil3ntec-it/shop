'use strict';
/**
 * کارگرهای پس‌زمینهٔ سرور — یک فهرست برای `index.js` و آزمونِ چند نمونه (`test/cluster-instances.test.js`).
 *
 * هر ردیف `[نام، start، stop]` است و به `cluster.jobs` می‌رود: تک‌نمونه (پیش‌فرض) ⇒ همه همان لحظه و به
 * همین ترتیب (همان رفتارِ پیشینِ `index.js`)؛ چند نمونه (`CLUSTER_ENABLED=1`) ⇒ فقط روی رهبر (`lib/cluster.js`).
 * `start()` هر چه برگرداند (تایمرِ پشتیبان، دستهٔ آمارِ PGlite) به `stop(آن)` می‌رسد.
 */
const config = require('./config');

function backgroundJobs() {
  const outbox = require('./lib/login-outbox');
  const backup = require('./lib/backup');
  const orderNotify = require('./lib/order-notify');
  const payments = require('./lib/payment-intents');
  const notices = require('./lib/notices');
  const opsAlerts = require('./lib/ops-alerts');
  const opsPaging = require('./lib/ops-paging');
  //  باتِ تلگرامِ پمپ — کدش در ریپوی پمپ است (bots/pump، lib/bots.js)؛ نبود ⇒ بی بات.
  //  تا رمزش از پنل داده نشده، فقط هر چند ثانیه نگاه می‌کند.
  const telegram = require('./lib/bots').pump();
  if (!telegram.available) console.log('[bots] باتِ پمپ نصب نیست (bots/pump) — سرور بی بات بالا می‌آید');
  const mod = (name) => [name, () => require(`./lib/${name}`).start(), () => require(`./lib/${name}`).stop()];
  return [
    /*
     *  Workerِ ایمیلِ کدِ ورود — درخواستِ کد همان لحظه ۲۰۰ می‌گیرد و ارسال
     *  این‌جا با تلاشِ دوباره و مدارشکن انجام می‌شود. بی این، کاربر پشتِ
     *  سرویسِ ایمیل منتظر می‌ماند و هر کندیِ ایمیل، کندیِ ورود می‌شد.
     */
    ['login-outbox', () => outbox.start(), () => outbox.stop()],
    //  کارگرِ اعلانِ سفارش (v3 §۳۰) — روی خودِ سرور، بی‌نیاز به کامپیوترِ دکان؛
    //  بی پرچمِ `shop.order_notify` صفش خالی است و کاری نمی‌کند
    ['order-notify', () => orderNotify.start(), () => orderNotify.stop()],
    //  NTF-01: اعلانِ وضعیت به مشتری (بی پرچمِ `shop.customer_notify` ⇒ صفِ خالی، یک پرسشِ کوچک)
    mod('customer-notify'),
    //  ORD-05: زمان‌سنجِ قبولِ سفارشِ ویترین (۱۵ دقیقه؛ فقط سفارشِ مُهرخورده)
    mod('order-timeout'),
    //  ORD-09: توقفِ خودکارِ سفارش‌گیری (فقط دکانِ با پرچمِ `shop.orders.desk` و توقفِ خودکارِ روشن)
    mod('order-desk'),
    //  ORD-18: هشدارِ «شروعِ سفارشِ زمان‌بندی‌شده» (فقط دکانِ با پرچمِ `shop.orders.start_alert`)
    mod('order-start-alert'),
    //  PAY-20/RPT-12: گزارشِ خودکارِ صاحبِ دکان (فقط دکانِ با پرچمِ `shop.owner_report` و تنظیمِ روشنِ صاحب)
    mod('owner-report'),
    //  حساب‌پیِ هر دکان: «پول رسید» ِ صندوق با گیرنده‌ای جز حسابِ دکان ⇒ هشدار و پوشِ فوری به صاحب (فقط دکانِ دارای حساب)
    mod('hesabpay-guard'),
    //  WEB-10: «خبرم کن وقتی موجود/ارزان شد» (بی پرچمِ `shop.storefront.alerts` ⇒ جدولِ خالی، یک پرسشِ کوچک)
    mod('storefront-alerts'),
    //  NTF-10: پوزش و کوپنِ خودکارِ تأخیر (فقط دکانِ با پرچمِ `shop.orders.late_apology` و سیاستِ روشنِ صاحب)
    mod('order-late-apology'),
    //  AI-11: نمایهٔ هشِ عکسِ کالاها برای «جست‌وجو با عکس» — فقط دکان‌های پرچمِ `shop.visual_search`، دسته‌ای و بی‌تکرار
    mod('visual-search'),
    //  PAY-21: تطبیقِ زمان‌بندی‌شدهٔ حساب‌پی (هر ۵ دقیقه، قفل میانِ نمونه‌ها)؛ بی کلیدِ ارائه‌دهنده هیچ پرسشی نمی‌رود
    ['payment-reconciler', () => payments.startReconciler(), () => payments.stopReconciler()],
    ['backup', () => backup.schedule(), (timer) => { if (timer) clearInterval(timer); }],
    //  PGlite autovacuum ندارد ⇒ آمارِ planner را خودمان تازه می‌کنیم (PERF-03)؛ روی PostgreSQL هیچ
    ['pglite-stats', () => require('./lib/pglite-stats').schedule(), (h) => { if (h) h.stop(); }],
    //  زمان‌بندِ مرکزِ اعلان: هر دقیقه اعلان‌های وقت‌رسیده، و روزی یک بار «رو به پایان / منقضی»
    ['notices', () => {
      notices.startRunner();
      require('./lib/job-beat').register('notices', 60_000);   //  پنلِ مرکزی ۴.۹: «زمان‌بندی شد» از همین لحظه
    }, () => notices.stopRunner()],
    //  هشدارهای مرکزیِ پنلِ مالک (۴.۱۱): هر ۵ دقیقه، با یکی کردنِ تکرار و سقفِ سیل (`lib/ops-alerts.js`)
    ['ops-alerts', () => opsAlerts.start(), () => opsAlerts.stop()],
    //  OPS-01@29: خبرِ بحرانی به مسئول + پیگیری (هر دقیقه؛ پرچمِ خاموشِ `ops.critical_alerts` ⇒ فقط پرسشِ پرچم)
    ['ops-paging', () => opsPaging.start(), () => opsPaging.stop()],
    //  O-06 = ب: جاروی نگه‌داشتنِ گذشتهٔ سفارشِ آنلاین (بی پرچمِ `shop.stock_reserve` ⇒ یک پرسشِ خالی در دقیقه)
    mod('stock-reserve'),
    ['telegram', () => { if (config.telegram.autoStart) telegram.start(); }, () => telegram.stop()],
  ];
}

module.exports = { backgroundJobs };
