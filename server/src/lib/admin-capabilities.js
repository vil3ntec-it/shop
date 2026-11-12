'use strict';
/**
 * دو قراردادِ ثابتِ درهای مدیر (`routes/admin.js`) با پلِ پنلِ مرکزی — جدا شد تا آن فایل از سقفِ اندازه نگذرد.
 */

/*
 *  «توانایی»های این سرور برای پلِ پنلِ خانگی (ریپوی server، `account-admin.js`) — بندِ ۱۲.۶ ِ
 *  ADMIN-CONTROL-PLANE. پل پیش از صدا زدنِ هر درِ تازه همین را می‌خواند؛ سرورِ کهنه‌تر این فیلد را
 *  ندارد ⇒ پل «داده در دسترس نیست» نشان می‌دهد، نه عددِ ساختگی. ⛔ نام‌ها قرارداد‌ند و همان‌اند که پل
 *  می‌شناسد؛ هر چه این‌جا گفته شود درش واقعاً هست (`admin-control-plane.test.js` ⇒ CAPABILITY_PROBES).
 *  فهرست به نقش بسته نیست: مرزِ نقش را خودِ هر در می‌سنجد (`lib/admin-roles.js`).
 */
const CAPABILITIES = Object.freeze([
  'shop-health.v1',   //  GET /admin/shops/:id/health
  'shop-holds.v1',    //  GET|POST /admin/shops/:id/holds · POST …/holds/:holdId/lift (چهار حالت + بایگانی)
  'flags.v1',         //  GET /admin/flags · GET /admin/flags/history (نوشتن: ارشد + دلیل)
  'actor.v1',         //  سرآیندهای X-Actor-Id/Role/Name با کلیدِ سرویس ⇒ «انجام‌دهنده» در سابقه
  'service-key.v1',   //  Bearer vsk_… · GET|POST /admin/service-keys · POST …/:keyId/revoke
  //  پوستهٔ کنسول (ریپوی server، ماژولِ ۴.۳ و جست‌وجوی سراسری):
  'admin-sessions.v1', //  GET /admin/admins/:id/sessions · POST …/sessions/revoke (+ دلیل و «پیشین ⇒ تازه» در سابقهٔ نقش)
  'search.v1',        //  GET /admin/search?q= — دکان، کاربر، پرداخت، سفارش، خطا (و مدیر فقط برای ارشدِ انسانی)، به نقش
  'market-admin.v1',  //  GET /admin/market/overview|sellers|orders|commission|history (۴.۶، فقط خواندن)
  'market-regions.v1', //  S109: GET /admin/market/regions · GET|PUT /admin/shops/:id/census (نشانِ آزمایشی/تکراری + دلیل)
  'delivery-watch.v1', //  GET /admin/delivery/overview|queue|events|connectivity|report|thresholds · PUT …/thresholds (۴.۷)
  ...require('./ops-overview').CAPABILITIES,   //  ops.v1 (+release-stage.v1 با OPS-01) — پنلِ مرکزی ۴.۸–۴.۱۱، ۴.۱۳
  'finance.v1',       //  ماژولِ ۴.۵: GET /admin/finance/(summary…export) · POST …/adjustments (app.js ⇒ routes/admin-finance.js)
  //  ممیزیِ پنلِ مرکزی §۹ (۱۴۰۵/۰۸/۲۰):
  'sync-restore-once.v1',   //  POST /admin/sync/conflicts/:id/restore — شناسهٔ یک‌شکل، ۴۰۹ already_restored، Idempotency-Key
  'subscription-period.v1', //  GET /admin/subscriptions/period-end — پایانِ دوره از سرور (S15)
  'admin-contract.v1',      //  GET /admin/contract + سرآیندِ X-Admin-API-Version (S18)
  //  درخواستِ پنلِ مرکزی ۱.۵۰.۷۵ (ممیزی §۳-۴):
  'staff-perms.v1',         //  GET /admin/shops/:id/staff-perms[?limit=&offset=] · POST …/devices/:did/disable|enable (ارشد + دلیل)
  'image-bank.v1',          //  GET /admin/image-bank/status|review|search|images/:id|reports|file · POST …/images/:id/approve|reject|archive|search
  'idempotent-writes.v1',   //  Idempotency-Key روی دستگاه (disable/enable)، بازبینیِ بانکِ عکس و POST /admin/backups (lib/admin-write-once.js)
  //  LOC-04: ویرایشگرِ تعطیلاتِ رسمی در پنلِ مرکزی (خواندن: هر مدیر؛ نوشتن: ارشد + دلیل، یک‌باره، قفلِ نسخه)
  'holidays.v1',            //  GET|PUT /admin/calendar/holidays
  //  درخواستِ پنلِ مرکزی ۱.۵۰.۸۹ و ۱.۵۰.۹۰ (تصمیم‌های ۴-۱ «ب» و ۲-۹ ِ ۱۴۰۵/۰۸/۲۰):
  'synonyms.v1',            //  GET|PUT /admin/search/synonyms — فهرستِ مرکزیِ مترادف (نوشتن: ارشد + دلیل، قفلِ نسخه، یک‌باره)
  'support-access.v1',      //  GET|POST /admin/shops/:id/access-grants · POST …/break-glass · POST …/:gid/revoke (مهاجرتِ ۸۱۰)
  //  IMG-PRO-17 در پنلِ مرکزی (۱.۵۰.۹۶): خواندن مدیر/بیننده (جزئیات با `read_private` وقتی admin.support_grant روشن است)؛
  //  برگرداندن فقط ارشد + دلیل + اجازهٔ زندهٔ `restore` + Idempotency-Key
  'image-changes.v1',       //  GET /admin/shops/:id/images/changes[/:cid] · POST …/:cid/rollback
]);

/*
 *  S18 — نسخهٔ قراردادِ درهای مدیر با پل. **فقط با شکستنِ** یک درِ موجود (برداشتن، عوض شدنِ معنا یا شکلِ پاسخ) بالا می‌رود؛
 *  درِ تازه نسخه را عوض نمی‌کند و فقط `routesHash` ِ `GET /admin/contract` را. پنلِ کهنه سرآیند را نمی‌خواند ⇒ همان کار.
 *  فهرست از `admin-contract.json` است که `tools/api-contract/generate.cjs` از `docs/api/routes.json` می‌سازد (`--check` هر دو).
 */
const ADMIN_API_VERSION = 1;
const VERSION_HEADER = 'X-Admin-API-Version';

/** میان‌افزارِ سرآیند — سرِ همهٔ `/api[/v1]/admin` در `app.js`، پیش از هر احراز (پاسخِ ۴۰۱ و ۴۰۴ هم دارندش). */
function adminApiVersion(req, res, next) {
  res.set(VERSION_HEADER, String(ADMIN_API_VERSION));
  next();
}

/** پاسخِ `GET /admin/contract` (`routes/admin-bridge.js`). */
function contract() {
  const file = require('./admin-contract.json');
  return {
    version: ADMIN_API_VERSION, routesHash: file.routesHash, count: file.routes.length, generatedFrom: file.source,
    serverVersion: require('../../package.json').version, capabilities: CAPABILITIES, routes: file.routes,
  };
}

/**
 * ⛔ یک WHERE برای ردیف‌ها و `total` ِ `GET /admin/shops` — پیش‌تر `total` همهٔ دکان‌ها را می‌شمرد و
 * صفحه‌بندیِ پنل با جست‌وجو «۴ از ۲» نشان می‌داد (چسبِ پنلِ مرکزی، بندِ ۱۲.۶). پارامترها: $1 = جست‌وجو،
 * $2 = الگوی LIKE، $3 = حالتِ مدیریتی (`''` همه، `'none'` بی حالت، یا یکی از `shop-holds.KINDS`).
 * فقط روی `shops s JOIN users u` معنا دارد.
 */
const SHOP_WHERE = `
      --  ⛔ ایمیل هم گشته می‌شود — همان قاعدهٔ admin-pump.js: صاحبِ
      --  سامانه معمولاً فقط ایمیلِ طرف را دارد، نه نامِ دکانش.
      WHERE ($1 = '' OR lower(s.name) LIKE $2 OR lower(u.name) LIKE $2
             OR lower(coalesce(u.email,'')) LIKE $2
             OR coalesce(u.phone,'') LIKE $2)
        AND ($3 = '' OR ($3 = 'none' AND NOT EXISTS (SELECT 1 FROM shop_holds h WHERE h.shop_id=s.id AND h.lifted_at IS NULL))
             OR EXISTS (SELECT 1 FROM shop_holds h WHERE h.shop_id=s.id AND h.lifted_at IS NULL AND h.kind=$3))`;

module.exports = { CAPABILITIES, SHOP_WHERE, ADMIN_API_VERSION, VERSION_HEADER, adminApiVersion, contract };
