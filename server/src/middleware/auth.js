'use strict';
/**
 * احراز هویت و کنترل دسترسی.
 *
 * دو قاعده‌ی همیشگی:
 *   ۱) شناسه‌ی کاربر همیشه از توکن خوانده می‌شود، نه از بدنه یا مسیر.
 *   ۲) shop_id هم از عضویت همان کاربر پیدا می‌شود، نه از چیزی که گوشی فرستاده.
 * به همین دلیل کسی نمی‌تواند با عوض کردن یک شناسه در درخواست به اطلاعات
 * حساب یا دکان دیگری برسد.
 */
const tokens = require('../lib/tokens');
const { one, query, now } = require('../db');
const { unauthorized, forbidden } = require('./errors');
const { isCoreFeature } = require('../lib/features');
const { entitlementOf } = require('../lib/entitlement');
const { membershipOf } = require('../lib/shops');
const { can } = require('../lib/permissions');

function bearer(req) {
  const h = req.headers.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(h.trim());
  return m ? m[1].trim() : null;
}

/**
 * کاربر عادیِ یک بخش.
 *
 * ── چرا بخش هم لازم است ───────────────────────────────────────────
 * خواستهٔ صاحب مخزن: «شاپ و پمپ ربطی به هم نداشته باشند، حتی یک ذره.»
 *
 * توکنی که برای بخشِ دکان صادر شده، این‌جا — روی مسیرهای پمپ —
 * **پیدا نمی‌شود**. پس دو بخش از دیدِ دسترسی هم دو چیزِ جدا هستند، نه
 * یک چیز با دو در.
 */
function requireUserOf(app) {
  return async function (req, res, next) {
    try {
      const token = bearer(req);
      if (!token) return next(unauthorized());
      const row = await tokens.verify(token, 'access', app, { anyScope: true });
      if (!row) return invalidToken(token, next);
      req.appSection = app;
      return userFromToken(req, res, next, row);
    } catch (err) { next(err); }
  };
}

/**
 * توکن پذیرفته نشد. اگر مالِ دستگاهی است که صاحب دکان از کار انداخته
 * (STF-10)، خطای روشنِ `device_disabled` — نه «نشست منقضی شد» که برنامه را
 * در حلقهٔ «دوباره وارد شوید» می‌اندازد. فقط در مسیرِ شکست یک پرسش.
 */
async function invalidToken(token, next) {
  try {
    const block = require('../lib/device-block');
    if (await block.explain(token)) return next(forbidden(block.MESSAGE, block.CODE));
  } catch { /* پاسخِ پیش‌فرض */ }
  return next(unauthorized('نشست شما منقضی شده است، دوباره وارد شوید', 'invalid_token'));
}

/** کاربر عادی — بخشِ دکان، همان‌جا که همیشه بود. */
async function requireUser(req, res, next) {
  try {
    const token = bearer(req);
    if (!token) return next(unauthorized());
    const row = await tokens.verify(token, 'access', 'shop', { anyScope: true });
    if (!row) return invalidToken(token, next);
    req.appSection = 'shop';
    return userFromToken(req, res, next, row);
  } catch (err) { next(err); }
}

/**
 * دامنهٔ پیک — DLV-01، v3 §۳.۱۱.
 *
 * توکنی که `scope='courier'` دارد (و عضوی که نقشش «پیک» است) فقط از درهایی
 * می‌گذرد که خودشان `req.courierScope` را روشن کرده‌اند: یعنی فقط
 * `routes/courier.js`. هر درِ دیگر — `/api/shop/*`، `/api/me`، صورت‌حساب،
 * رمز — `403 courier_scope` است، نه «نشست منقضی شد» که برنامه را در
 * حلقهٔ ورود بیندازد.
 */
const COURIER_SCOPE_MESSAGE = 'حسابِ پیک فقط به صفحهٔ مأموریت‌های خودش دسترسی دارد';

function courierScopeDenied(req, next) {
  return next(forbidden(COURIER_SCOPE_MESSAGE, 'courier_scope'));
}

/** درِ پیک این را صدا می‌زند، پیش از `requireUser`. */
function allowCourierScope(req, res, next) {
  req.courierScope = true;
  next();
}

/** بدنهٔ مشترکِ هر دو — از روی ردیفِ توکن، کاربر و دستگاه را می‌آورد. */
async function userFromToken(req, res, next, row) {
  try {
    if (row.scope && !(row.scope === tokens.COURIER_SCOPE && req.courierScope === true)) {
      return courierScopeDenied(req, next);
    }

    const user = await one('SELECT * FROM users WHERE id=$1', [row.subject_id]);
    if (!user) return next(unauthorized('حساب پیدا نشد', 'invalid_token'));
    if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

    if (row.device_id) {
      const dev = await one('SELECT * FROM devices WHERE id=$1', [row.device_id]);
      if (!dev || dev.status !== 'active') {
        const block = require('../lib/device-block');
        if (await block.isDisabledRow(dev)) return next(forbidden(block.MESSAGE, block.CODE));
        return next(forbidden('دسترسی این دستگاه لغو شده است', 'device_revoked'));
      }
      req.device = dev;
      query('UPDATE devices SET last_seen_at=$2 WHERE id=$1', [dev.id, now()]).catch(() => {});
    }

    req.user = user;
    req.tokenRow = row;
    next();
  } catch (err) { next(err); }
}

/** کاربرِ بخشِ پمپ. */
const requirePumpUser = requireUserOf('pump');

/**
 * کاربرِ **هر** بخشی — فقط برای کارهایی که مالِ خودِ هویت‌اند.
 *
 * ── چه چیزی این را لازم کرد ────────────────────────────────────────
 * ⛔ `‎/api/auth/logout-all‎` و `‎/api/auth/password‎` هر دو
 * `requireUser` داشتند، یعنی **فقط توکنِ دکان** را می‌شناختند. پس
 * برنامهٔ پمپ نه می‌توانست رمزش را عوض کند و نه از همهٔ دستگاه‌ها بیرون
 * بیاید: هر دو ۴۰۱ می‌گرفتند و پیامش هم می‌گفت «نشست منقضی شده،
 * دوباره وارد شوید» — در حالی که نشست سالم بود.
 *
 * رمز و هویت **مشترک**‌اند (یک `users` برای هر دو بخش)، پس این دو کار
 * باید از هر دو در باز باشند. دادهٔ هر بخش همچنان پشتِ توکنِ همان بخش
 * است و این تابع هیچ دری به دفترها باز نمی‌کند.
 *
 * ⚠️ `req.appSection` از روی **ردیفِ توکن** پر می‌شود، نه از درخواست —
 * تا «خروج از همهٔ دستگاه‌ها» بداند کدام بخش را ببندد.
 */
async function requireAnyUser(req, res, next) {
  try {
    const token = bearer(req);
    if (!token) return next(unauthorized());
    const row = await tokens.verify(token, 'access', null, { anyScope: true });
    if (!row) return invalidToken(token, next);
    req.appSection = row.app || 'shop';
    return userFromToken(req, res, next, row);
  } catch (err) { next(err); }
}

/** عضویت در دکان لازم است — shop_id از همین‌جا می‌آید. */
async function requireShop(req, res, next) {
  try {
    if (!req.user) return next(unauthorized());
    const member = await membershipOf(req.user.id);
    if (!member) return next(forbidden('برای این حساب دکانی ثبت نشده است', 'no_shop'));
    //  نقشِ پیک — حتی با توکنی که پیش از پیک شدن گرفته شده
    if (member.role === 'courier' && req.courierScope !== true) return courierScopeDenied(req, next);
    req.member = member;
    req.shopId = member.shop_id;
    req.role = member.role;
    next();
  } catch (err) { next(err); }
}

/**
 * عضویت در پمپ لازم است — `station_id` از همین‌جا می‌آید.
 *
 * قرینه‌ی `requireShop`، و به همان دلیل: شناسه هرگز از بدنه‌ی درخواست
 * خوانده نمی‌شود، پس کسی نمی‌تواند با عوض کردنش به دفترِ پمپِ دیگری برسد.
 */
async function requireStation(req, res, next) {
  try {
    if (!req.user) return next(unauthorized());
    const { membershipOf } = require('../lib/stations');
    const member = await membershipOf(req.user.id);
    if (!member) return next(forbidden('برای این حساب پمپی ثبت نشده است', 'no_station'));
    req.stationMember = member;
    req.stationId = member.station_id;
    req.stationRole = member.role;
    next();
  } catch (err) { next(err); }
}

/** عضویتِ پمپ اگر بود، ولی نبودنش خطا نیست. */
async function optionalStation(req, res, next) {
  try {
    if (req.user) {
      const { membershipOf } = require('../lib/stations');
      const member = await membershipOf(req.user.id);
      if (member) {
        req.stationMember = member;
        req.stationId = member.station_id;
        req.stationRole = member.role;
      }
    }
    next();
  } catch (err) { next(err); }
}

/**
 * قابلیتِ پولیِ بخشِ پمپ — تصمیم از روی دیتابیس سرور، نه از روی گوشی.
 *
 * قرینه‌ی `requireFeature`، ولی روی کاتالوگ و دفترِ پمپ. یکی کردنشان
 * وسوسه‌انگیز بود، ولی آن‌وقت یک `req.shopId`ِ جامانده می‌توانست
 * قابلیتِ پمپ را با اشتراکِ دکان باز کند.
 */
function requireStationFeature(featureKey) {
  return async function (req, res, next) {
    try {
      const { catalogOf } = require('../lib/features');
      const cat = catalogOf('pump');
      if (cat.CORE_KEYS.includes(featureKey)) return next();
      if (!req.user) return next(unauthorized());
      if (!req.stationId) return next(forbidden('برای این حساب پمپی ثبت نشده است', 'no_station'));

      const ent = await require('../lib/entitlement').pump.entitlementOf(req.stationId, now());
      if (!ent.features.includes(featureKey)) {
        const err = ent.trial.used && !ent.trial.active
          ? forbidden('اشتراک این پمپ به پایان رسیده است.', 'subscription_expired')
          : forbidden('این قابلیت نیازمند اشتراک است', 'subscription_required');
        err.entitlement = { source: ent.source, trial: ent.trial };
        return next(err);
      }
      req.stationEntitlement = ent;
      next();
    } catch (err) { next(err); }
  };
}

/** عضویت اگر بود، ولی نبودنش خطا نیست (مثلاً صفحه‌ی «من»). */
async function optionalShop(req, res, next) {
  try {
    if (req.user) {
      const member = await membershipOf(req.user.id);
      if (member && member.role === 'courier' && req.courierScope !== true) return courierScopeDenied(req, next);
      if (member) {
        req.member = member;
        req.shopId = member.shop_id;
        req.role = member.role;
      }
    }
    next();
  } catch (err) { next(err); }
}

/**
 * نوشتن روی دفتر دکان — وقتی اشتراک لازم است و وقتی نیست.
 *
 * ── سوراخی که این می‌بندد ─────────────────────────────────────────
 * تا امروز `/api/sync` و `/api/data/*` فقط `requireUser, requireShop`
 * داشتند و هیچ بررسی اشتراکی نبود. یعنی دکانی که نه اشتراک داشت و نه
 * دوره‌ی آزمایشی (`source: 'free'`)، باز هم می‌توانست بی‌محدودیت
 * push و pull کند — و «چند کاربر روی یک دکان»، که خودش قابلیت پولی
 * است، عملاً مجانی بود.
 *
 * اشتراک فقط در گوشی اجرا می‌شد؛ و چیزی که فقط در گوشی اجرا شود،
 * اجرا نشده است.
 * ──────────────────────────────────────────────────────────────────
 *
 * ## قاعده — و چرا این‌طور
 *
 * ۱) **خواندن هرگز بسته نمی‌شود.** حتی با اشتراک تمام‌شده. داده‌ی
 *    فروشنده مالِ خودش است؛ باید بتواند روی گوشی تازه بیاوردش و
 *    پشتیبان بگیرد. گروگان گرفتن داده، سریع‌ترین راه از دست دادن
 *    اعتماد است — و `backup` هم در فهرست رایگان هست، پس بستن خواندن
 *    با خودِ کاتالوگ قابلیت‌ها هم جور درنمی‌آمد.
 *
 * ۲) **صاحب دکان همیشه می‌نویسد.** کارِ خودش روی گوشیِ خودش، هیچ‌وقت
 *    نباید به خاطر تمام شدن اشتراک از بین برود.
 *
 * ۳) **شاگرد برای نوشتن، اشتراک لازم دارد.** این دقیقاً همان
 *    `multi_device` است که در کاتالوگ قابلیت‌ها پولی علامت خورده.
 *    نوشته‌هایش روی گوشی خودش می‌مانند و لحظه‌ای که دکان تمدید شود
 *    بالا می‌روند — چیزی گم نمی‌شود، فقط منتظر می‌ماند.
 */
function requireDataWrite(req, res, next) {
  (async () => {
    if (!req.shopId) return next(forbidden('برای این حساب دکانی ثبت نشده است', 'no_shop'));

    // صاحب دکان و مدیرش همیشه می‌نویسند
    if (req.role === 'owner' || req.role === 'manager') return next();

    const { entitlementOf } = require('../lib/entitlement');
    const ent = await entitlementOf(req.shopId);
    if (ent.features.includes('multi_device')) return next();

    return next(forbidden(
      'اشتراک این دکان تمام شده است. فروش‌های شما روی همین گوشی می‌مانند و ' +
      'به‌محض تمدید بالا می‌روند.',
      'subscription_required'
    ));
  })().catch(next);
}

/** دسترسی بر پایه‌ی نقش. */
function requirePermission(permission) {
  return function (req, res, next) {
    if (!req.member) return next(forbidden('برای این حساب دکانی ثبت نشده است', 'no_shop'));
    if (!can(req.member.role, permission)) {
      return next(forbidden('این کار در حد دسترسی شما نیست', 'permission_denied'));
    }
    //  STF-01..03: لایه‌های دکان/کاربر فقط می‌بندند — سقفِ نقش همین بالا سنجیده شد
    const perms = require('../lib/staff-perms');
    if (!perms.isLayered(permission)) return next();
    perms.memberAllows(req, permission).then((ok) => (ok
      ? next()
      : next(forbidden('صاحب دکان این کار را برای شما بسته است', 'permission_denied'))
    )).catch(next);
  };
}

/**
 * قابلیت‌های اشتراکی — تصمیم از روی دیتابیس سرور گرفته می‌شود،
 * حتی اگر گوشی قفل را دور زده باشد.
 */
function requireFeature(featureKey) {
  return async function (req, res, next) {
    try {
      if (isCoreFeature(featureKey)) return next();
      if (!req.user) return next(unauthorized());
      if (!req.shopId) return next(forbidden('برای این حساب دکانی ثبت نشده است', 'no_shop'));

      const ent = await entitlementOf(req.shopId, now());
      if (!ent.features.includes(featureKey)) {
        const err = ent.trial.used && !ent.trial.active
          ? forbidden('اشتراک این دکان به پایان رسیده است.', 'subscription_expired')
          : forbidden('این قابلیت نیازمند اشتراک است', 'subscription_required');
        err.entitlement = { source: ent.source, trial: ent.trial };
        return next(err);
      }
      req.entitlement = ent;
      next();
    } catch (err) { next(err); }
  };
}

/** مدیر سامانه — کاملاً جدا از کاربران عادی. */
async function requireAdmin(req, res, next) {
  try {
    const token = bearer(req);
    if (!token) return next(unauthorized());
    const row = await tokens.verify(token, 'admin');
    if (!row) return next(unauthorized('نشست مدیر منقضی شده است', 'invalid_token'));
    const admin = await one('SELECT * FROM admins WHERE id=$1', [row.subject_id]);
    if (!admin || admin.status !== 'active') {
      return next(forbidden('حساب مدیر غیرفعال است', 'admin_disabled'));
    }
    req.admin = admin;
    req.tokenRow = row;
    next();
  } catch (err) { next(err); }
}

function requireSuperAdmin(req, res, next) {
  if (!req.admin) return next(unauthorized());
  if (req.admin.role !== 'superadmin') return next(forbidden('فقط مدیر ارشد'));
  next();
}

module.exports = {
  allowCourierScope, COURIER_SCOPE_MESSAGE,
  bearer, requireUser, requireUserOf, requireAnyUser, requirePumpUser, requireShop, optionalShop, requireDataWrite,
  requirePermission, requireFeature, requireAdmin, requireSuperAdmin,
  //  بخشِ پمپ‌بنزین
  requireStation, optionalStation, requireStationFeature,
};
