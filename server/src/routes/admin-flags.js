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

/** پرچم‌های شناخته — scope: «shop» = شناسهٔ دکان یا all؛ «all» = فقط all. */
const KNOWN = Object.freeze([
  { key: 'shop.orders', scope: 'shop', default: false, what: 'سفارشِ آنلاین (`/api/shop/orders/*`)' },
  { key: 'shop.storefront', scope: 'shop', default: false, what: 'ویترینِ عمومی و مدیریتِ ویترین' },
  { key: 'shop.delivery', scope: 'shop', default: false, what: 'تحویل و پیک' },
  { key: 'shop.orders.refunds', scope: 'shop', default: false, what: 'بازپرداختِ سفارش با دفتر' },
  { key: 'shop.chat', scope: 'shop', default: false, what: 'گفت‌وگو با مشتری' },
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
  { key: 'market.directory', scope: 'shop', default: false, what: 'فهرستِ بازار' },
  //  دستهٔ ۲۷ (چک‌لیستِ روشن کردن: docs/FLAGS-ON-fa.md) — بی ردیفِ این‌جا زبانهٔ «پرچم‌ها» آن‌ها را نشان نمی‌داد و مقدارِ
  //  نادرست (مثلاً «"true"» ِ رشته‌ای) بی‌صدا هیچ اثری نداشت
  { key: 'shop.approval_requests', scope: 'shop', default: false, what: 'درخواستِ تأیید از راهِ دور: کارمند می‌پرسد، صاحب/مدیر روی گوشیِ خودش تأیید می‌کند (STF-02)' },
  { key: 'shop.credit_cap', scope: 'shop', default: false, what: 'سقفِ فروشِ قرضیِ کارمند در سرور (`sales.credit`، STF-03)' },
  //  CRM-03: فقط سابقهٔ بررسی، هرگز رد (تصمیمِ «رد یا بررسی» با صاحب‌کار؛ ‎lib/credit-limit-review.js‎)
  { key: 'shop.credit_limit_review', scope: 'shop', default: false, what: 'فروشِ نسیهٔ بیش از سقفِ اعتبارِ مشتری بی تأییدِ دستگاه ⇒ سابقهٔ بررسی برای صاحب (CRM-03؛ رد نمی‌کند)' },
  { key: 'shop.custom_roles', scope: 'shop', default: false, what: 'نقشِ سفارشی: نام + نقشِ پایه + محدودیت‌های بیشتر (STF-01)' },
  { key: 'shop.catalog', scope: 'shop', default: false, what: 'دفترِ موجودیِ سرور: مکان‌ها، انتقالِ کالا و تطبیق (INV-11)؛ پایهٔ «پنهان کردنِ ناموجود» ِ ویترین' },
  { key: 'shop.count_absorb', scope: 'shop', default: false, what: 'فروشِ آفلاینِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد دوباره از موجودی کم نمی‌شود — سرور و برنامه (INV-10)؛ با shop.catalog' },
  { key: 'market.slug_hold', scope: 'shop', default: false, what: 'نشانیِ پیشینِ ویترین یک سال برای همان دکان می‌ماند تا QR ِ چاپی به دکانِ دیگری نرسد' },
  //  RPT-03 (docs/FLAGS-ON-fa.md): عددِ تازه در پیامی که صاحب همین حالا می‌گیرد ⇒ جدا و خاموش؛ با `shop.owner_report`
  { key: 'shop.owner_report.profit', scope: 'shop', default: false, what: 'سودِ ناخالص با میانگینِ موزون در گزارشِ خودکارِ صاحب (RPT-03)؛ قلمِ بی‌بها ⇒ «تقریبی»' },
  { key: 'shop.backup_contact_guard', scope: 'shop', default: false, what: 'پشتیبانِ کاملِ دکان (`GET /api/me/backups/:id`) فقط برای کسی که شمارهٔ مشتری را می‌بیند (CRM-15، ممیزیِ ۳۵ B1)' },
  //  O-01 (docs/FIREBASE-SETUP-fa.md): هر دو برای «سفارش با برنامهٔ بسته» لازم‌اند
  { key: 'shop.order_notify', scope: 'shop', default: false, what: 'اعلانِ سفارشِ تازه، پرداخت و لغوِ مشتری به گوشی‌های دکان (صفِ پایدار و یادآوری)' },
  { key: 'shop.device_push', scope: 'shop', default: false, what: 'پوشِ گوشی با Firebase: ثبتِ توکنِ دستگاه‌های دکان (برنامهٔ بسته هم زنگ می‌زند)' },
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
