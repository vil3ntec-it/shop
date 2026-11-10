'use strict';
/**
 * پنلِ مرکزی، فاز ۳ — پرچم‌ها و «تنظیماتِ زنده» (`live_config`) با تاریخچه (ماژولِ ۴.۱۲ ِ ممیزی).
 *
 *   GET    /api/admin/flags?scope=all            مقدارهای یک scope + کاتالوگِ پرچم‌های شناخته
 *   GET    /api/admin/flags/history?scope=&key=&limit=&before=   چه کسی، کی، پیشین ⇒ تازه، دلیل
 *   PUT    /api/admin/flags   {scope, key, value, reason}         فقط مدیرِ ارشد، دلیل اجباری
 *   DELETE /api/admin/flags   {scope, key, reason}                فقط مدیرِ ارشد، دلیل اجباری
 *
 * ⛔ همان جدول و همان کتابخانهٔ `PUT /api/admin/pump/live-config` — نه دفترِ دوم. تاریخچه در
 *    `lib/live-config.js` و در همان تراکنشِ نوشتن است، پس درِ پمپ و کانالِ آزمایشی هم رد می‌گذارند.
 * ⛔ کاتالوگ فقط «شرح و پیش‌فرض» است (از جدولِ «نقشهٔ پرچم‌ها» ِ `docs/RULES-fa.md`)؛ مقدار همیشه از
 *    `live_config` می‌آید. کلیدی که نوشته نشده `value: null, set: false` است — پیش‌فرض را پنل نشان می‌دهد.
 */
const express = require('express');
const liveConfig = require('../lib/live-config');
const audit = require('../lib/audit');
const svc = require('../lib/admin-service');
const { requireSuperAdmin } = require('../middleware/auth');
const { requireReason } = require('../lib/admin-access');
const { clientIp } = require('../middleware/ratelimit');
const v = require('../lib/validate');
const db = require('../db');
const { badRequest } = require('../middleware/errors');
//  دامنهٔ پرچمی که برای «همه یا یک دکان» است (`live_config.scope`) — نامِ دامنه، نه مقایسهٔ برنامه
const SHOP_SCOPE = 'shop';

const router = express.Router();

/** پرچم‌ها و تنظیم‌های زندهٔ شناخته — scope: «shop» = شناسهٔ دکان یا all؛ «all» = فقط all؛ بقیه (security، release، ops) = همان نام. */
const KNOWN = Object.freeze([
  { key: 'shop.orders', scope: 'shop', default: false, what: 'سفارشِ آنلاین (`/api/shop/orders/*`)' },
  { key: 'shop.storefront', scope: 'shop', default: false, what: 'ویترینِ عمومی و مدیریتِ ویترین' },
  { key: 'shop.delivery', scope: 'shop', default: false, what: 'تحویل و پیک' },
  { key: 'shop.orders.refunds', scope: 'shop', default: false, what: 'بازپرداختِ سفارش با دفتر' },
  { key: 'shop.chat', scope: 'shop', default: false, what: 'گفت‌وگو با مشتری' },
  { key: 'shop.chat.voice', scope: 'shop', default: false, what: 'ضبطِ پیامِ صوتیِ مشتری در گفت‌وگوی ویترین (CHAT-07)' },
  { key: 'shop.image_bank', scope: 'shop', default: false, what: 'بانکِ تصاویرِ دکان' },
  { key: 'image_bank.central', scope: 'all', default: false, what: 'پنلِ بانکِ تصاویرِ مدیر' },
  { key: 'account.self_delete', scope: 'all', default: false, what: 'حذفِ حساب به دستِ خودِ کاربر در برنامه و پورتال (Google Play)' },
  { key: 'hashchain.audit', scope: 'security', default: false, what: 'زنجیرهٔ هشِ سابقه' },
  { key: 'beta.shops', scope: 'release', default: [], what: 'دکان‌های کانالِ آزمایشی' },
  { key: 'beta.devices', scope: 'release', default: [], what: 'دستگاه‌های کانالِ آزمایشی' },
  { key: 'shop.receipt_v2', scope: 'shop', default: false, what: 'فاکتورِ نسخهٔ ۲' },
  { key: 'shop.promo_stamp', scope: 'shop', default: false, what: 'نشانِ تخفیفِ خودکار روی فاکتور' },
  { key: 'shop.loyalty_rule', scope: 'shop', default: false, what: 'قاعدهٔ یکسانِ امتیازِ وفاداری برای همهٔ دستگاه‌ها و امتیاز در «حسابِ من» (PRM-13)' },
  { key: 'shop.ledger_merge', scope: 'shop', default: true, what: 'ذخیرهٔ کهنه: ادغامِ سه‌طرفهٔ دفترِ برنامه (#407) — روشن برای برنامهٔ تازه؛ فقط false خاموش' },
  //  ⛔ پیش‌فرض روشن (درستی): فقط خاموش‌کنِ اضطراری — `false` ِ صریح ذخیرهٔ برنامه را به حدسِ پایهٔ پیشین برمی‌گرداند
  { key: 'shop.save_base', scope: 'shop', default: true, what: 'پایهٔ صریحِ ذخیره: دو ذخیره از یک صفحهٔ کهنه هیچ‌کدام را نمی‌برند و سدِ نقش با ادغامِ خاموش هم (برنامهٔ تازه، `?v=3`)؛ فقط false خاموش' },
  //  ⛔ پیش‌فرض روشن (رفعِ امنیتی، نه اجازه): فقط خاموش‌کنِ اضطراری — `false` ِ صریح ورودِ دوبارهٔ شاگرد را به پیش از ۲.۱۴.۵ برمی‌گرداند
  { key: 'auth.staff_session_guard', scope: 'shop', default: true, what: 'ورودِ دوبارهٔ شاگرد با کد فقط با رازِ همان دستگاه و کدی که هنوز برای همان عضو معتبر است (`lib/staff-device-keys.js`) — فقط false خاموش' },
  { key: 'shop.sync_shadow_file', scope: 'shop', default: false, what: 'سایه و cursor ِ همگام‌سازیِ برنامه در فایلِ خودشان، نه تنظیمات (PERF-LEDGER) — خاموش ⇒ همان prefs؛ خاموش کردن امن است' },
  { key: 'sync.pull_own_newer', scope: 'shop', default: true, what: 'pull ِ v1 نوشتهٔ تازه‌ترِ خودِ دستگاه را با opِ کهنه‌ترِ دیگری بازنویسی نمی‌کند (#501) — فقط false خاموش' },
  { key: 'shop.settings_sync', scope: 'shop', default: false, what: 'همگام‌سازیِ زندهٔ تنظیماتِ دکان (قاعده‌های پول، برگشت، مالیات) بینِ دستگاه‌ها' },
  { key: 'shop.held_sync', scope: 'shop', default: false, what: 'پشتیبانِ سرورِ فاکتورهای نگه‌داشته: با پاک کردنِ برنامه گم نمی‌شوند و روی دستگاهِ دیگرِ دکان برمی‌گردند (POS-12، S09)' },
  { key: 'shop.customer_erasure', scope: 'shop', default: false, what: 'حذفِ دادهٔ شخصیِ مشتری با نگه‌داشتنِ سندِ مالیِ ناشناس (SEC-10)' },
  { key: 'shop.orders.dispute_file', scope: 'shop', default: false, what: 'پروندهٔ مدرکِ اختلافِ سفارش (RSK-08)' },
  { key: 'shop.storefront.filters', scope: 'shop', default: false, what: 'فیلترِ کاتالوگِ ویترین (برند، قیمت، موجود، مشخصه)' },
  { key: 'shop.storefront.alerts', scope: 'shop', default: false, what: '«خبرم کن وقتی موجود/ارزان شد» ِ ویترین' },
  { key: 'shop.staff_scope', scope: 'shop', default: false, what: 'دسترسیِ کارمند محدود به مکانِ موجودی (STF-06)' },
  { key: 'shop.returns.sale_cap', scope: 'shop', default: false, what: 'برگشتِ کارمند بیش از جمعِ خودِ فاکتور رد می‌شود (SEC-DATA-07، DATA-04)' },
  //  ⛔ پیش‌فرض روشن (درستی، نه اجازه): فقط خاموش‌کنِ اضطراری — `false` ِ صریح ‎lib/return-line-cap.js‎ را برمی‌دارد
  { key: 'shop.returns.line_cap', scope: 'shop', default: true, what: 'هیچ‌کس (صاحب هم) بیش از فروخته‌شدهٔ هر قلمِ فاکتور برنمی‌گرداند؛ فقط false خاموش می‌کند (DATA-04، سفرِ JB5)' },
  { key: 'shop.sync.pay_fix', scope: 'shop', default: false, what: 'سرور «اصلاحِ روشِ پرداخت» را می‌سنجد: مجوز و برگهٔ تأیید، و بیش از ماندهٔ روش رد (POS-40)' },
  { key: 'shop.sync.refund_cap', scope: 'shop', default: false, what: 'بازپرداختِ سفارشِ ویژه و امانی بیش از پیش‌پرداختِ نگه‌داشته رد می‌شود (POS-26، POS-27)' },
  { key: 'shop.storefront.eta', scope: 'shop', default: false, what: 'زمانِ تقریبیِ تحویلِ ویترین (WEB-04)' },
  { key: 'shop.storefront.web_account', scope: 'shop', default: false, what: 'حسابِ مشتری در وبِ ویترین: پیشنهاد پس از ثبت، نشانی‌های نام‌دار و همگامیِ سبد (CHK-01/09، WEB-14)' },
  { key: 'shop.storefront.account_history', scope: 'shop', default: false, what: '«حسابِ من» ِ ویترین: خریدِ حضوری و مانده' },
  //  ویترین، سفارشِ آنلاین و مشتری: هر کلیدی که کد می‌خواند (سنجه: `console-shell.test.js`، `web-store-allflags.mjs`) — بی این ردیف‌ها
  //  زبانهٔ «پرچم‌ها» و `E2E_FLAGS_ON=all` آن‌ها را نمی‌دیدند و «"true"» ِ رشته‌ای بی‌صدا هیچ اثری نداشت. `shop.orders.ledger` بازنشسته است.
  { key: 'shop.storefront.recent', scope: 'shop', default: false, what: '«دیده‌شده‌های اخیر» ِ ویترین فقط روی دستگاهِ مشتری (WEB-13)' },
  { key: 'shop.storefront.favorites', scope: 'shop', default: false, what: '♥ و «فهرستِ خرید» ِ ویترین، همراهِ حسابِ مشتری (WEB-11)' },
  { key: 'shop.storefront.cart', scope: 'shop', default: false, what: 'سبدِ حسابِ مشتری روی سرور (CHK-03)؛ با `shop.storefront.web_account`' },
  { key: 'shop.storefront.search', scope: 'shop', default: false, what: 'جست‌وجوی ویترین: غلطِ تایپی، نامِ دسته و «پیدا نشد» ِ مفید (WEB-05/06)' },
  { key: 'shop.storefront.search_core', scope: 'shop', default: false, what: 'جست‌وجوی ویترین با قاعده‌های صندوق: رقمِ فارسی = لاتین، «ي» = «ی»، هر ترتیب (SRCH-01)' },
  { key: 'shop.storefront.seo', scope: 'shop', default: false, what: 'صفحهٔ کالای `/p/<id>` با Open Graph و JSON-LD، نقشهٔ سایت و «فرستادنِ نشانی» (WEB-17/20)' },
  { key: 'shop.storefront.zones', scope: 'shop', default: false, what: 'ناحیه‌های ارسال با هزینه، حداقل و «رایگان از…» در فرمِ سفارش (CHK-05/06/07/10)' },
  { key: 'shop.storefront.notify_area', scope: 'shop', default: false, what: '«وقتی به محلِ شما رسیدیم خبرم کن» برای نشانیِ بیرون از ناحیه (CHK-10)' },
  { key: 'shop.storefront.address_extra', scope: 'shop', default: false, what: 'شمارهٔ دوم، Plus Code و ولایت/گذر/منزل در نشانیِ سفارش (CHK-08)' },
  { key: 'shop.storefront.delivery_method', scope: 'shop', default: false, what: 'روش و مسئولِ تحویل در ثبت و پیگیری، و «تغییرِ روش» (S71/S72)' },
  { key: 'shop.storefront.stock_list', scope: 'shop', default: false, what: 'در ۴۰۹ ِ ناموجود همهٔ قلم‌های کم با «کم کن به …» (E2E-MKT-02)' },
  { key: 'shop.storefront.banners', scope: 'shop', default: false, what: 'بنرهای زمان‌دارِ ویترین برای هر ناحیه (WEB-24)' },
  { key: 'shop.storefront.media', scope: 'shop', default: false, what: 'لوگو و تصویرِ سرِ ویترین (M3)' },
  { key: 'shop.storefront.promos', scope: 'shop', default: false, what: 'کدِ تخفیف و تخفیفِ خودکار/زمان‌دارِ ویترین (MKT-01/09)؛ و «کجا اعمال شود» در فرمِ تخفیفِ برنامه (PRM-11)' },
  { key: 'shop.storefront.promo_guard', scope: 'shop', default: false, what: 'تخفیفِ اولین خرید و سدِ سوءاستفاده از تخفیف (MKT-02، RSK-09)؛ با `shop.storefront.promos`' },
  { key: 'shop.storefront.promo_groups', scope: 'shop', default: false, what: 'تخفیفِ گروهِ مشتری در ویترین فقط با گروهِ قرض‌دارِ همان شمارهٔ تأییدشده (PRM-11)؛ با `shop.storefront.promos`' },
  { key: 'shop.storefront.campaigns', scope: 'shop', default: false, what: 'پیوند و QR ِ کمپین با ردیابیِ منبع (MKT-10/11)' },
  { key: 'shop.storefront.bulk', scope: 'shop', default: false, what: 'انتشارِ گروهیِ کالای ویترین از کاتالوگِ واحد (M2)' },
  { key: 'shop.storefront.visual_search', scope: 'shop', default: false, what: '«جست‌وجو با عکس» ِ ویترین (AI-11)؛ با `shop.visual_search`' },
  { key: 'shop.storefront.link_expiry', scope: 'shop', default: false, what: 'لینکِ پیگیریِ سفارش زمان‌دار و باطل‌شدنی؛ QR ِ فاکتور یک سال (TEN-03، O-08)' },
  { key: 'shop.orders.subscribers_only', scope: 'shop', default: false, what: 'سفارشِ تازهٔ مشتری فقط با اشتراکِ فعال (SUB-10)؛ روشن کردن با صاحب محصول' },
  { key: 'shop.orders.desk', scope: 'shop', default: false, what: 'میزِ سفارش: «شلوغم»، توقفِ موقت و خودکار، قبولِ خودکار (ORD-04/08/09)' },
  { key: 'shop.orders.picking', scope: 'shop', default: false, what: 'جمع‌آوری و بسته‌بندیِ سفارش با فهرست، عکس و جایگزین (PCK)' },
  { key: 'shop.orders.substitution', scope: 'shop', default: false, what: '«اگر نبود…» ِ کلی و هر قلم در ثبت و پیگیری (CHK-15)' },
  { key: 'shop.orders.delivery_prefs', scope: 'shop', default: false, what: 'دستورِ تحویل و «با کدام اسکناس» تا پیش از ارسال (CHK-14/24)' },
  { key: 'shop.orders.add_items', scope: 'shop', default: false, what: 'افزودنِ سبد به همان سفارش تا پیش از جمع‌آوری (CHK-21)' },
  { key: 'shop.orders.amend', scope: 'shop', default: false, what: 'پیشنهادِ ویرایشِ سفارش با پذیرش/ردِ مشتری (ORD-14)' },
  { key: 'shop.orders.amend_paid', scope: 'shop', default: false, what: 'ویرایشِ سفارشِ پرداخت‌شده با تسویهٔ تفاوت (ORD-14)؛ با `shop.orders.amend`' },
  { key: 'shop.orders.manual_refund', scope: 'shop', default: false, what: 'بسته‌بندیِ سفارشِ پرداخت‌شده با کسر: «پس داده شد» ِ دستی (نقد یا حساب‌پی) و ثبتِ سرور (ORD-15، PAY-23، تصمیمِ ۱-۶ ب)؛ با `shop.orders.picking`' },
  { key: 'shop.orders.refund_tracking', scope: 'shop', default: false, what: 'پیگیریِ بازپرداخت برای مشتری در صفحهٔ سفارش (NTF-08)' },
  { key: 'shop.orders.issue_reports', scope: 'shop', default: false, what: '«گزارشِ مشکل» با عکس در پیگیریِ سفارش (NTF-07)' },
  { key: 'shop.orders.reviews', scope: 'shop', default: false, what: 'امتیاز و نظرِ مشتری با بازبینیِ دکان (NTF-09)' },
  { key: 'shop.orders.late_apology', scope: 'shop', default: false, what: 'عذرخواهی و کوپنِ خودکار برای تأخیرِ زیاد (NTF-10)' },
  { key: 'shop.orders.delay', scope: 'shop', default: false, what: '«عقب انداختنِ» زمانِ آماده‌شدنِ سفارشِ قبول‌شده (ORD-07)' },
  { key: 'shop.orders.presence', scope: 'shop', default: false, what: 'نشانگرِ «باز / بسته» در بخشِ سفارش‌های کارمند (ORD-03)' },
  { key: 'shop.orders.start_alert', scope: 'shop', default: false, what: 'هشدارِ شروعِ سفارشِ زمان‌بندی‌شده (ORD-18)' },
  { key: 'shop.orders.stuck_alert', scope: 'shop', default: false, what: 'هشدارِ «سفارشِ گیرکرده» به دکان پس از قبول (S79)' },
  { key: 'shop.orders.bulk', scope: 'shop', default: false, what: 'تغییرِ وضعیتِ گروهیِ چند سفارش (ORD-19)' },
  { key: 'shop.orders.notice_resend', scope: 'shop', default: false, what: '«فرستادنِ دوبارهٔ» پیامِ وضعیت به مشتری (ORD-16)' },
  { key: 'shop.orders.online_report', scope: 'shop', default: false, what: 'گزارشِ فروشِ اینترنتی و جست‌وجوهای بی‌نتیجهٔ ویترین (RPT-14)' },
  { key: 'shop.customer_notify', scope: 'shop', default: false, what: 'اعلانِ وضعیتِ سفارش و «خبرم کن» به مشتری (NTF-01/03/04)' },
  { key: 'shop.storefront.web_push', scope: 'shop', default: false, what: 'پوشِ وب ِ مشتری: «خبرم کن روی همین دستگاه» در پیگیریِ سفارش، بی حسابِ بیرونی (CHK-20، NTF-01)؛ با `shop.customer_notify`' },
  { key: 'shop.delivery_customer_view', scope: 'shop', default: false, what: 'نمای پیک برای مشتری: نامِ کوچک، کدِ تحویل و زمانِ تقریبی (S5، DLV-06)؛ با `shop.delivery`' },
  { key: 'market.directory', scope: 'shop', default: false, what: 'فهرستِ بازار' },
  //  دستهٔ ۲۷ (چک‌لیستِ روشن کردن: docs/FLAGS-ON-fa.md) — بی ردیفِ این‌جا زبانهٔ «پرچم‌ها» آن‌ها را نشان نمی‌داد و مقدارِ
  //  نادرست (مثلاً «"true"» ِ رشته‌ای) بی‌صدا هیچ اثری نداشت
  { key: 'shop.approval_requests', scope: 'shop', default: false, what: 'درخواستِ تأیید از راهِ دور: کارمند می‌پرسد، صاحب/مدیر روی گوشیِ خودش تأیید می‌کند (STF-02)' },
  { key: 'shop.credit_cap', scope: 'shop', default: false, what: 'سقفِ فروشِ قرضیِ کارمند در سرور (`sales.credit`، STF-03)' },
  //  CRM-03: فقط سابقهٔ بررسی، هرگز رد (تصمیمِ «رد یا بررسی» با صاحب‌کار؛ ‎lib/credit-limit-review.js‎)
  { key: 'shop.credit_limit_review', scope: 'shop', default: false, what: 'فروشِ نسیهٔ بیش از سقفِ اعتبارِ مشتری بی تأییدِ دستگاه ⇒ سابقهٔ بررسی برای صاحب (CRM-03؛ رد نمی‌کند)' },
  //  CRM-03 / §۱۰.۱: سرور مجوزِ `customers.credit_limit` را در v1، v3 و REST اجرا می‌کند (‎lib/credit-limit-perm.js‎)
  { key: 'shop.credit_limit_perm', scope: 'shop', default: false, what: 'مجوزِ «تغییرِ سقفِ اعتبارِ مشتری» در سرور (`customers.credit_limit`، CRM-03)؛ ویرایشِ بی‌اجازه رد و به صندوقِ تعارض' },
  //  PRD-07: همتای `ProductDraftRules` ِ برنامه در v1 و v3 (‎lib/product-unit-rules.js‎)
  { key: 'shop.product_unit_rules', scope: 'shop', default: false, what: 'واحدِ پایهٔ کالای گردش‌دار و ضریبِ تازهٔ واحدِ فروشِ دوم که دقیقاً به هزارم نیست در همگام‌سازی رد می‌شود (PRD-07؛ صاحب هم)' },
  //  STF-03: فقط سابقهٔ بررسی، هرگز رد (‎lib/promo-loyalty-review.js‎)
  { key: 'shop.promo_loyalty_review', scope: 'shop', default: false, what: 'تخفیفِ خودکارِ قلم و مصرفِ امتیازِ فاکتورِ کارمند با تعریف‌های دکان ⇒ سابقهٔ بررسی برای صاحب (STF-03؛ رد نمی‌کند)' },
  { key: 'shop.custom_roles', scope: 'shop', default: false, what: 'نقشِ سفارشی: نام + نقشِ پایه + محدودیت‌های بیشتر (STF-01)' },
  { key: 'shop.catalog', scope: 'shop', default: false, what: 'دفترِ موجودیِ سرور: مکان‌ها، انتقالِ کالا و تطبیق (INV-11)؛ پایهٔ «پنهان کردنِ ناموجود» ِ ویترین' },
  //  O-06 = ب (ORD-20، CHK-18؛ lib/stock-reserve.js): با `shop.catalog`؛ سرور باز هم موجودی را کم نمی‌کند
  { key: 'shop.stock_reserve', scope: 'shop', default: false, what: 'نگه‌داشتنِ کوتاهِ موجودی برای سفارشِ آنلاین تا دکان قبول کند: آخرین عدد دو بار سفارش نمی‌گیرد (O-06 ب)؛ با shop.catalog' },
  //  PRD-08: فقط برنامه (`QtyExactWiring`)؛ سرور فقط پرچم را در `GET /api/shop/stock-ledger/config` می‌گوید (‎routes/shop-stock-ledger.js‎)
  { key: 'shop.qty_exact', scope: 'shop', default: false, what: 'کالای وزنی (۳ رقمِ اعشار): مبلغِ ردیفِ مقدارِ اعشاری با قاعدهٔ دقیقِ ۳.۴ (همان سفارشِ آنلاین) و مقدارِ سبد/فاکتور/حرکت به هزارمِ دقیق؛ مقدارِ درست همان امروز (PRD-08)' },
  { key: 'shop.count_absorb', scope: 'shop', default: false, what: 'فروشِ آفلاینِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد دوباره از موجودی کم نمی‌شود — سرور و برنامه (INV-10)؛ با shop.catalog' },
  { key: 'market.slug_hold', scope: 'shop', default: false, what: 'نشانیِ پیشینِ ویترین یک سال برای همان دکان می‌ماند تا QR ِ چاپی به دکانِ دیگری نرسد' },
  //  RPT-03 (docs/FLAGS-ON-fa.md): عددِ تازه در پیامی که صاحب همین حالا می‌گیرد ⇒ جدا و خاموش؛ با `shop.owner_report`
  { key: 'shop.owner_report.profit', scope: 'shop', default: false, what: 'سودِ ناخالص با میانگینِ موزون در گزارشِ خودکارِ صاحب (RPT-03)؛ قلمِ بی‌بها ⇒ «تقریبی»' },
  { key: 'shop.backup_contact_guard', scope: 'shop', default: false, what: 'پشتیبانِ کاملِ دکان (`GET /api/me/backups/:id`) فقط برای کسی که شمارهٔ مشتری را می‌بیند (CRM-15، ممیزیِ ۳۵ B1)' },
  //  O-01 (docs/FIREBASE-SETUP-fa.md): هر دو برای «سفارش با برنامهٔ بسته» لازم‌اند
  { key: 'shop.order_notify', scope: 'shop', default: false, what: 'اعلانِ سفارشِ تازه، پرداخت و لغوِ مشتری به گوشی‌های دکان (صفِ پایدار و یادآوری)' },
  { key: 'shop.device_push', scope: 'shop', default: false, what: 'پوشِ گوشی با Firebase: ثبتِ توکنِ دستگاه‌های دکان (برنامهٔ بسته هم زنگ می‌زند)' },
  //  دستهٔ ۳۸: هر کلیدِ دیگری که کدِ غیرِ پمپ می‌خواند (سنجه: `console-shell.test.js` + `test/flag-scan.js`). پیش‌فرض همان پیش‌فرضِ کد است
  //  (`admin-flags.test.js`)؛ عدد/شیء = تنظیمِ زنده. بیرون: مشترک با پمپ (`sync.device_bind`، `sync.live_expiry_close`) و پویا (`stage.*`)
  //  ── پیک و تحویل (همه با `shop.delivery`)
  { key: 'shop.delivery_map', scope: 'shop', default: false, what: 'پیوندِ مسیریاب و نقشهٔ آفلاینِ پیک در کارتِ مأموریت (DLV-05)' },
  { key: 'shop.delivery_courier_call', scope: 'shop', default: false, what: '«تماس با مشتری» روی کارتِ پیک؛ شمارهٔ کامل فقط برای همان تماس' },
  { key: 'shop.delivery_bag_scan', scope: 'shop', default: false, what: '«بسته را گرفتم» ِ پیک فقط با اسکنِ برچسبِ همهٔ کیسه‌های بسته (DLV-07)' },
  { key: 'shop.delivery_near_pin', scope: 'shop', default: false, what: '«تحویل شد» ِ پیک برای سفارشِ پین‌دار فقط نزدیکِ پین (DLV-17)' },
  { key: 'shop.delivery_near_pin.max_m', scope: 'shop', default: 300, what: 'فاصلهٔ مجازِ «تحویل شد» از پین به متر (۲۰ تا ۵۰۰۰؛ نامعتبر ⇒ ۳۰۰)؛ با `shop.delivery_near_pin`' },
  { key: 'shop.delivery_route', scope: 'shop', default: false, what: 'ترتیبِ پیشنهادیِ مأموریت‌های پذیرفتهٔ پیک (`GET /api/courier/route`، DLV-04)' },
  { key: 'shop.delivery_ratings', scope: 'shop', default: false, what: 'امتیازِ تحویل از پیک و مشتری و فهرستِ امتیازها برای دکان (DLV-18)' },
  { key: 'shop.delivery_code_rule', scope: 'shop', default: false, what: 'کدِ تحویلِ اجباری برای سپردن به پیکِ دکان وقتی نقد هنگامِ تحویل یا گران‌قیمت است (DLV-08)' },
  { key: 'shop.delivery_code_rule.min_total_minor', scope: 'shop', default: 0, what: 'آستانهٔ «گران‌قیمت» به واحدِ خرد؛ ۰ ⇒ فقط نقد هنگامِ تحویل؛ با `shop.delivery_code_rule`' },
  { key: 'shop.delivery_live_location', scope: 'shop', default: false, what: 'موقعیتِ زندهٔ پیکِ «در راه» برای مشتری (DLV-16)؛ با `shop.delivery_customer_view`' },
  //  ── صاحب، گزارش، مجوز، کارمند و تخفیف
  { key: 'shop.owner_report', scope: 'shop', default: false, what: 'گزارشِ خودکارِ روزانه/هفتگیِ صاحب در ساعتِ محلیِ دکان (PAY-20، RPT-12)' },
  { key: 'shop.branches_report', scope: 'shop', default: false, what: 'خلاصهٔ هر شعبهٔ همان صاحب و جمعِ همه، فقط خواندنی (RPT-18)؛ هر شعبه با پرچمِ خودش' },
  { key: 'shop.debt_minor', scope: 'shop', default: false, what: 'ماندهٔ حسابِ قرض‌دار در برنامه به پولِ دقیق (واحدِ خرد)، نه جمعِ Double' },
  { key: 'shop.doc_chain', scope: 'shop', default: false, what: 'زنجیرهٔ هشِ اسنادِ مالیِ هر سریِ دستگاه و لنگرِ سابقهٔ دستگاه (SEC-02)' },
  { key: 'shop.device_cap', scope: 'shop', default: false, what: 'سقفِ دستگاهِ اشتراک برای دستگاهِ ثبت‌شده هم: بیرون از n ِ اول ⇒ بی مجوز (جز دستگاهِ خودِ صاحب)' },
  { key: 'shop.license_offline_30d', scope: 'shop', default: false, what: 'مجوزِ ۳۵ روزه به‌جای ده روز تا دستگاهِ بی اینترنت ۳۰ روز بفروشد (SYN-01)؛ هرگز پس از پایانِ اشتراک' },
  { key: 'shop.price_cap', scope: 'shop', default: false, what: 'سقفِ تغییرِ قیمتِ ردیف و «تخفیفِ پنهان» ِ کارمند در سرور (`sales.price`، STF-03)؛ قلمِ آزاد (`sales.free_line`) و قیمتِ عمده فقط برای مشتریِ لیستِ عمده (تصمیمِ ۱-۱۱)' },
  { key: 'shop.promo_uses', scope: 'shop', default: false, what: 'دفترِ مصرفِ کدِ تخفیف و سقفِ `maxUses` در سرور (PRM-02)' },
  { key: 'shop.promo_expiry', scope: 'shop', default: false, what: 'قاعدهٔ «تخفیفِ نزدیکِ انقضا» (PRM-10)' },
  { key: 'shop.promo_tier_price', scope: 'shop', default: false, what: 'پلهٔ «قیمتِ ثابتِ هر واحد» در تخفیفِ پله‌ای (POS-19)' },
  { key: 'shop.purchase_orders', scope: 'shop', default: false, what: 'سفارشِ خرید، رسیدِ دریافت و فاکتورِ تأمین‌کننده (PUR-02/04/14)' },
  { key: 'shop.staff_time', scope: 'shop', default: false, what: 'ورود و خروجِ کارمند و پورسانتِ ماه (STF-07/08)' },
  { key: 'shop.till_pin', scope: 'shop', default: false, what: 'PIN ِ شخصیِ صندوق در سرور و نوبت‌های سنجیدهٔ صندوق: چه کسی پشتِ کدام دستگاه بود (STF-04، S46)' },
  { key: 'shop.stock_v2', scope: 'shop', default: false, what: 'موتورِ تازهٔ موجودی و بهای برنامه (میانگینِ موزون، سری/FEFO، دفترِ حرکت)؛ با `stock_mode=on` ِ دکان' },
  { key: 'shop.order_risk', scope: 'shop', default: false, what: 'قاعده‌های ریسکِ ثبتِ ویترین: سفارشِ تکراری و سقفِ مشتریِ تازه (RSK-02/03/05)' },
  { key: 'shop.order_risk.policy', scope: 'shop', default: { newCustomerMaxTotalMinor: 1_000_000, newCustomerMaxOpen: 2, duplicateWindowMin: 10 },
    what: 'سیاستِ همان قاعده‌ها؛ هر کلید جدا، نامعتبر ⇒ پیش‌فرض، ۰ = آن قاعده خاموش؛ با `shop.order_risk`' },
  { key: 'shop.visual_search', scope: 'shop', default: false, what: '«جست‌وجو با عکس» ِ کارمند و نمایهٔ هشِ عکسِ کالاها (AI-11)؛ پایهٔ `shop.storefront.visual_search`' },
  { key: 'image_bank.quality', scope: 'shop', default: false, what: 'نشان‌های پیشنهادیِ کیفیت روی عکسِ بانک؛ هرگز رد (IMG-15)' },
  { key: 'shop.image_studio', scope: 'shop', default: false, what: 'استودیوی عکس روی سرورِ خودمان: صفِ پردازشِ گروهی، خروجی فقط پس از تأییدِ دکان (AI-02/05)؛ با `shop.image_bank`' },
  { key: 'image_bank.resume', scope: 'all', default: false, what: 'ادامهٔ بارگذاریِ دسته‌ایِ بانکِ تصاویر بی فرستادنِ دوبارهٔ همان فایل (IMG-14)' },
  { key: 'pay.hesabpay', scope: 'shop', default: false, what: 'حساب‌پی ِ خودکار با کلیدهای خودِ دکان؛ پول مستقیم به حسابِ دکان، تأیید فقط با webhook' },
  //  ── همگام‌سازیِ دکان (پمپ هیچ‌کدام را نمی‌خواند)
  { key: 'shop.sync.doc_roles', scope: 'shop', default: false, what: 'داوریِ نقشِ سندهای سابقه‌سوار (کارتِ هدیه، امتیاز، امانی…) در Sync v3' },
  { key: 'shop.sync_v3_to_v1', scope: 'shop', default: false, what: 'پلِ v3 ⇒ v1 برای دکانی که دستگاهِ کهنه و تازه با هم دارد' },
  { key: 'sync.cursor_from_since', scope: 'shop', default: false, what: 'cursor ِ دستگاهِ Sync v1 = آنچه دستگاه تأیید کرد، نه آنچه سرور فرستاد (#481)' },
  { key: 'sync.safe_entries', scope: 'shop', default: false, what: 'گاوصندوق (PAY-18): پلهٔ ۴ ِ schema و جدولِ `safeEntries` در Sync v1/v3' },
  { key: 'sync.keep_pending', scope: 'shop', default: false, what: 'pull ِ Sync v1 می‌گوید تغییرِ محلیِ در صف با opِ رسیده بازنویسی نشود (`KeepPending`)' },
  { key: 'sync.revive_full_row', scope: 'shop', default: false, what: 'op ِ زنده‌کنندهٔ ردیفِ حذف‌شده در pull ِ Sync v1 دادهٔ کاملِ ردیف را دارد' },
  { key: 'sync.gzip_pull', scope: 'shop', default: false, what: 'پاسخِ pull ِ Sync v1 ِ بزرگ‌تر از ۱KB با `Accept-Encoding: gzip` فشرده' },
  { key: 'sync.gzip_push', scope: 'shop', default: false, what: 'pull به دستگاه می‌گوید push ِ بزرگ را فشرده بفرستد (SYN-04)' },
  //  ── بازار (کارمزدها: تصمیمِ صاحب محصول «فعلاً ۰»)
  { key: 'market.changes', scope: 'shop', default: false, what: 'جریانِ تغییرِ بازار (`market_changes`) برای باطل کردنِ کش و نمایهٔ جست‌وجو (S6)' },
  { key: 'market.trust', scope: 'shop', default: false, what: 'شکایت و تخلفِ بازارچه برای مشتری و دکان' },
  { key: 'market.public_id', scope: 'shop', default: false, what: 'شناسهٔ عمومیِ پایدارِ دکان و نشانی‌های پیشینِ ویترین (S3)' },
  { key: 'market.platform_fee_bps', scope: 'shop', default: 0, what: 'کارمزدِ سامانه روی سفارشِ مشتری، صدمِ درصد (۰ تا ۱۰٬۰۰۰)' },
  { key: 'market.platform_fee_fixed_minor', scope: 'shop', default: 0, what: 'مبلغِ ثابتِ کارمزدِ سامانه روی سفارشِ مشتری، واحدِ خرد' },
  { key: 'market.commission_bps', scope: 'shop', default: 0, what: 'کمیسیونِ بازار روی سفارشِ مشتری، صدمِ درصد (۰ تا ۱۰٬۰۰۰)' },
  { key: 'market.commission_fixed_minor', scope: 'shop', default: 0, what: 'مبلغِ ثابتِ کمیسیونِ بازار روی سفارشِ مشتری، واحدِ خرد' },
  { key: 'market.account', scope: 'all', default: false, what: 'حسابِ مشتریِ بازار بی دکان (`/api/market/account/*`، S9)' },
  { key: 'market.same_in_store', scope: 'all', default: false, what: '«همین کالا در دکان‌های دیگر» روی برگهٔ کالای ویترینِ خودِ دکان هم (با `market.product_details`)؛ خاموش ⇒ فقط بازارچه (تصمیمِ ۳-۲۲)' },
  { key: 'market.product_details', scope: 'all', default: false, what: 'برند، بارکد، واحد و دستهٔ سراسری در نمای عمومیِ کالا (M4)' },
  { key: 'market.public_views', scope: 'all', default: false, what: 'پاسخ‌های عمومیِ ویترین و بازار فقط با فیلدهای مجاز (`lib/public-views.js`)' },
  { key: 'storefront.catalog_source', scope: 'all', default: false, what: 'ویترین و بازار کالا را از کاتالوگِ واحد (`catalog_products`) می‌خوانند، نه `sync_rows` (S1)' },
  { key: 'console.delivery_thresholds', scope: 'all',
    default: { acceptMin: 30, dispatchMin: 30, courierAcceptMin: 15, pickupMin: 30, inDeliveryMin: 120, failedMin: 240, recallMin: 60 },
    what: 'آستانه‌های «گیر کرده» ِ سفارش و تحویل در پنل، به دقیقه (۴.۶، ۴.۷)' },
  //  ── ops و امنیت
  { key: 'ops.critical_alerts', scope: 'ops', default: false, what: 'هشدارِ بحرانی با ایمیل و پوش به مدیرانِ پلتفرم، با پیگیری (OPS-01)' },
  { key: 'ops.critical_alerts.escalate_min', scope: 'ops', default: 15, what: 'دقیقه تا پیگیریِ هشدارِ بحرانیِ دیده‌نشده (۵ تا ۲۴۰)' },
  { key: 'ops.critical_alerts.max_escalations', scope: 'ops', default: 2, what: 'بیشترین بارِ پیگیریِ یک هشدارِ بحرانی (۰ تا ۵)' },
  { key: 'ops.sync_lag', scope: 'ops', default: false, what: 'بخشِ «تأخیرِ همگام‌سازی» در سلامتِ سامانه (OPS-04)' },
  { key: 'backup.require_encryption', scope: 'security', default: false, what: 'پشتیبانِ سرور فقط رمزدار و پس از بازگشاییِ همان فایل (SEC-07)' },
  { key: 'auth.phone_add_code', scope: 'security', default: false, what: 'افزودنِ شمارهٔ ورود فقط با کدِ همان شماره (AUTH-08)؛ پیش از روشن کردن برنامه‌ها `phoneCode` بفرستند' },
]);

router.get('/', async (req, res) => {
  const scope = String(req.query?.scope || 'all');
  const out = await liveConfig.list(scope);
  res.json({ ...out, known: KNOWN, reserved: liveConfig.RESERVED });
});

router.get('/history', async (req, res) => {
  res.json({
    entries: await liveConfig.history({
      scope: req.query?.scope ? String(req.query.scope) : '',
      key: req.query?.key ? String(req.query.key) : '',
      limit: v.integer(req.query?.limit, { min: 1, max: 200, def: 50 }),
      before: v.integer(req.query?.before, { min: 0, max: Number.MAX_SAFE_INTEGER, def: 0 }),
    }),
  });
});

/*
 *  ⛔ پوستهٔ کنسول (۴.۱۲): پرچمِ **شناخته** فقط مقدارِ هم‌نوعِ پیش‌فرضش را می‌پذیرد و فقط در scope ِ خودش —
 *  «true» ِ رشته‌ای یا پرچمِ دکان روی scope ِ امنیت، بی‌صدا هیچ اثری نداشت (برنامه فقط بولی می‌خواند) و پنل
 *  «روشن» نشان می‌داد. کلیدِ ناشناخته همان رفتارِ پیشین را دارد (هر JSON) تا هیچ نویسندهٔ امروزی نشکند.
 */
const ID_RE = /^[A-Za-z0-9_-]{1,80}$/;
async function checkKnown(scope, key, value) {
  const k = KNOWN.find((x) => x.key === String(key || '').trim());
  if (!k) return;
  const s = String(scope || 'all').trim();
  const scopeOk = k.scope === SHOP_SCOPE ? (s === 'all' || (ID_RE.test(s) && await db.one('SELECT 1 AS x FROM shops WHERE id=$1', [s])))
    : k.scope === 'all' ? s === 'all' : s === k.scope;
  if (!scopeOk) {
    const want = k.scope === SHOP_SCOPE ? '«all» یا شناسهٔ یک دکان' : `«${k.scope}»`;
    throw badRequest(`پرچمِ ${k.key} فقط در scope ِ ${want} معنا دارد`, 'bad_flag_scope');
  }
  const typeOk = Array.isArray(k.default)
    ? Array.isArray(value) && value.length <= 500 && value.every((x) => typeof x === 'string' && ID_RE.test(x))
    : typeof value === typeof k.default;
  if (!typeOk) {
    const want = Array.isArray(k.default) ? 'فهرستی از شناسه‌ها' : typeof k.default === 'boolean' ? 'true یا false' : typeof k.default;
    throw badRequest(`مقدارِ ${k.key} باید ${want} باشد`, 'bad_flag_value');
  }
}

router.put('/', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const b = req.body || {};
  await checkKnown(b.scope || 'all', b.key, b.value);
  const out = await liveConfig.set(b.scope || 'all', b.key, b.value, svc.label(req), { reason, actor: svc.actorRecord(req) });
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.flag_set', ip: clientIp(req),
    targetType: 'live_config', targetId: `${out.scope}:${out.key}`, detail: { scope: out.scope, key: out.key, reason },
  });
  res.json({ ok: true, ...out });
});

router.delete('/', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const b = { ...(req.query || {}), ...(req.body || {}) };
  const out = await liveConfig.remove(b.scope || 'all', b.key, svc.label(req), { reason, actor: svc.actorRecord(req) });
  if (out.removed) {
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.flag_removed', ip: clientIp(req),
      targetType: 'live_config', targetId: `${out.scope}:${out.key}`, detail: { scope: out.scope, key: out.key, reason },
    });
  }
  res.json({ ok: true, ...out });
});

module.exports = router;
module.exports.KNOWN = KNOWN;
