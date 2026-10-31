'use strict';
/**
 * نقش‌های مدیرِ سرورِ حساب و مرزِ هر کدام — **تنها** جای این قاعده (فاز ۳ ِ پنلِ مرکزی).
 *
 *   superadmin  همه‌چیز (همان «مدیرِ ارشد»؛ مالکِ اصلی هم یکی از همین‌هاست: `lib/admin-owner.js`)
 *   admin       همان اختیاراتِ امروزِ «مدیر» — دست نخورد تا هیچ رفتاری عوض نشود
 *   finance     مالی: خواندنِ فراداده، اشتراک، پرداخت، درخواستِ خرید، «پایانِ اشتراکِ» دکان؛ نه پشتیبانی،
 *               نه کاربر، نه اعلانِ جمعی، نه تنظیم
 *   viewer      فقط خواندن (همان چیزهایی که مدیرِ عادی می‌خواند؛ هیچ نوشتنی)
 *
 * ⛔ مرز در **سرور** سنجیده می‌شود، برای آدم و برای «انجام‌دهنده»ای که پلِ پنل با کلیدِ سرویس
 * می‌فرستد (`lib/admin-service.js`) — یک قاعده برای هر دو. نقشِ باریک **پیش‌فرض‌بسته** است: درِ
 * تازه‌ای که این‌جا نیامده برای finance/viewer بسته است تا کسی آن را عمداً باز کند.
 *
 * ⚠️ مسیرِ «فقط ارشد» همچنان با `requireSuperAdmin`ِ خودش بسته است؛ این‌جا فقط چیزی **بیشتر** می‌بندد.
 */
const { forbidden } = require('../middleware/errors');

const ROLES = Object.freeze(['superadmin', 'admin', 'finance', 'viewer']);
/** برای «سقف» (کلیدِ سرویس): نقشِ کم‌رتبه‌تر برنده است. */
const RANK = Object.freeze({ viewer: 0, finance: 1, admin: 2, superadmin: 3 });

const READ = new Set(['GET', 'HEAD']);

/** درهای خودِ نشست (خروج، تأییدِ دوباره، دوعاملیِ خودش) — برای هر نقشِ انسانی باز. */
const SELF = [
  ['GET', /^\/admin\/(me|2fa)$/],
  ['POST', /^\/admin\/(logout|reauth|2fa\/[a-z-]+)$/],
];

/**
 * درهای مالی. مسیر بی پیشوندِ `/api` (یا `/api/v1`)، با حروفِ کوچک و بی `/` ِ پایانی
 * (مسیریابیِ Express به بزرگی و کوچکی حساس نیست).
 */
const FINANCE = [
  ['GET', /^\/admin\/(me|features|stats|audit)$/],
  ['GET', /^\/admin\/shops$/],
  ['GET', /^\/admin\/shops\/[^/]+(\/(history|health|holds))?$/],
  ['GET', /^\/admin\/(subscriptions|pump\/subscriptions)(\/[^/]+(\/addons)?|\/expiring)?$/],
  ['GET', /^\/admin\/payments(\/[^/]+\/receipt)?$/],
  ['GET', /^\/admin\/sales\/(summary|subscriptions|expiring|debts)$/],
  ['GET', /^\/admin\/purchase-requests$/],
  ['GET', /^\/admin\/(plans|price-history|discount-codes|campaigns)$/],
  ['GET', /^\/admin\/plans\/[^/]+\/price-history$/],
  ['GET', /^\/admin\/campaigns\/[^/]+\/stats$/],
  ['GET', /^\/admin\/flags(\/history)?$/],
  //  جست‌وجوی سراسری — خودِ در برای مالی فقط دکان و پرداخت را می‌گردد (`routes/admin-search.js`)
  ['GET', /^\/admin\/search$/],
  ['GET', /^\/admin\/shop-desk\/groups$/],
  //  ۴.۶ و ۴.۷: فقط درهای پولیِ بازار و پیک (سفارش، کمیسیون، نقد و تسویهٔ پیک) — نه پرونده و نه آستانه
  ['GET', /^\/admin\/market\/(overview|orders|commission)$/],
  ['GET', /^\/admin\/delivery\/(overview|report)$/],
  ['POST', /^\/admin\/(subscriptions|pump\/subscriptions)$/],
  ['PUT', /^\/admin\/subscriptions\/[^/]+$/],
  ['POST', /^\/admin\/(subscriptions|pump\/subscriptions)\/[^/]+\/status$/],
  ['POST', /^\/admin\/pump\/subscriptions\/[^/]+\/services$/],
  ['POST', /^\/admin\/payments$/],
  ['POST', /^\/admin\/purchase-requests\/[^/]+\/(approve|reject)$/],
  ['POST', /^\/admin\/discount-codes\/quote$/],
  //  درِ حالت‌های دکان خودش نوع را می‌سنجد: مالی فقط «پایانِ اشتراک» (`lib/shop-holds.js`)
  ['POST', /^\/admin\/shops\/[^/]+\/holds(\/[^/]+\/lift)?$/],
];

/** درهایی که کلیدِ سرویس هرگز به آن‌ها نمی‌رسد — هویتِ ماشین مدیر نمی‌سازد و کلید نمی‌چرخاند. */
const SERVICE_DENY = /^\/admin\/(admins|service-keys|2fa|reauth|logout)(\/|$)/;

const MESSAGE = 'این کار در حدِ نقشِ شما در سرورِ حساب نیست';

/** «/api/v1/Admin/Shops/x/?q=1» ⇒ «/admin/shops/x» */
function pathOf(req) {
  const p = String(req.originalUrl || req.url || '').split('?')[0].toLowerCase().replace(/\/+$/, '');
  return p.replace(/^\/api(?:\/v1)?(?=\/)/, '');
}

const matches = (list, method, path) => list.some(([m, re]) => m === method && re.test(path));

/**
 * خطای ۴۰۳ اگر این نقش به این در نمی‌رسد؛ وگرنه null.
 * @param {{method:string, originalUrl?:string, url?:string, admin:{role:string}, service?:object}} req
 */
function check(req) {
  const role = req.admin && req.admin.role;
  const method = String(req.method || 'GET').toUpperCase();
  const path = pathOf(req);
  if (req.service && SERVICE_DENY.test(path)) {
    return forbidden('کلیدِ سرویس به مدیران، کلیدها و نشستِ مدیر دست ندارد', 'service_forbidden');
  }
  if (role === 'superadmin' || role === 'admin') return null;
  const head = method === 'HEAD' ? 'GET' : method;
  if (!req.service && matches(SELF, head, path)) return null;
  if (role === 'viewer' && READ.has(method)) return null;
  if (role === 'finance' && matches(FINANCE, head, path)) return null;
  return forbidden(MESSAGE, 'role_forbidden');
}

/** کم‌رتبه‌ترِ دو نقش (برای سقفِ کلیدِ سرویس). */
function lower(a, b) {
  if (!(a in RANK) || !(b in RANK)) return 'viewer';
  return RANK[a] <= RANK[b] ? a : b;
}

module.exports = { ROLES, RANK, FINANCE, SERVICE_DENY, MESSAGE, pathOf, check, lower };
