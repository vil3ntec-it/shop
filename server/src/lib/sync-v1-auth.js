'use strict';
/**
 * نگهبانِ Sync v1 — حساب **فقط** از توکن.
 *
 * سه توکن این‌جا شناخته می‌شوند و هر کدام به حسابِ خودش می‌رسد:
 *   توکنِ حسابِ دکان   (`tokens.app = 'shop'`)  ⇒ `shop`    / `req.shopId`
 *   توکنِ حسابِ پمپ    (`tokens.app = 'pump'`)  ⇒ `station` / `req.stationId`
 *   توکنِ دستگاهِ پمپ  (`station_devices`)      ⇒ `station` / `req.stationId`
 *
 * ⛔ `account` در بدنه یا نشانی **خوانده نمی‌شود**. کسی که بخواهد با
 * توکنِ دکان در دفترِ یک پمپ بنویسد، همان دکانِ خودش را می‌نویسد و بس.
 *
 * ⚠️ `X-App` (یا `app` در بدنه) اگر بیاید باید با بخشِ توکن بخواند؛
 * ناجورش `403 app_mismatch` است، نه یک دفترِ اشتباه.
 */
const tokens = require('./tokens');
const { one } = require('../db');
const { sectionOf } = require('./tenancy');
const { can } = require('./permissions');
const { unauthorized, forbidden } = require('../middleware/errors');

/** نام‌های برنامه در پرامپت (`dukan`) هم پذیرفته می‌شوند. */
function appAlias(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (s === 'dukan' || s === 'dokan') return 'shop';
  return sectionOf(s);
}

/**
 * از یک توکنِ خام، بافتِ حساب را می‌سازد — یا null.
 * @returns {null | {app, accountKind, accountId, accountName, userId, role, via}}
 */
async function resolveToken(token) {
  if (!token || typeof token !== 'string' || token.length < 20) return null;

  const row = await tokens.verify(token, 'access', null, { anyScope: true });
  if (row) {
    const user = await one('SELECT id, status FROM users WHERE id=$1', [row.subject_id]);
    if (!user || user.status !== 'active') return null;
    if (row.device_id) {
      const dev = await one('SELECT status FROM devices WHERE id=$1', [row.device_id]);
      if (!dev || dev.status !== 'active') return null;
    }
    const app = row.app || 'shop';
    if (app === 'pump') {
      const m = await require('./stations').membershipOf(user.id);
      if (!m) return { app, accountKind: 'station', accountId: '', userId: user.id, role: '', via: 'user', missing: 'no_station' };
      return {
        app, accountKind: 'station', accountId: m.station_id, accountName: m.station_name || '',
        userId: user.id, role: m.role, via: 'user',
      };
    }
    //  ⛔ DLV-01 (§۳.۱۱): توکنِ پیک — یا عضوی که نقشش پیک است — به دفترِ دکان
    //  راه ندارد: pull، snapshot، push، v3 و سوکتِ زنده همه بسته‌اند.
    if (row.scope) return { app, accountKind: 'shop', accountId: '', userId: user.id, role: '', via: 'user', missing: 'courier_scope' };
    //  دکانِ برگزیدهٔ همین نشست، اگر برگزیده (مهاجرتِ ۰۹۷)
    const m = await require('./shops').membershipOf(user.id, { shopId: row.shop_id || null });
    if (!m) return { app, accountKind: 'shop', accountId: '', userId: user.id, role: '', via: 'user', missing: 'no_shop' };
    if (m.role === 'courier') return { app, accountKind: 'shop', accountId: '', userId: user.id, role: m.role, via: 'user', missing: 'courier_scope' };
    return {
      app, accountKind: 'shop', accountId: m.shop_id, accountName: m.shop_name || '',
      userId: user.id, role: m.role, via: 'user',
    };
  }

  //  کامپیوترِ پمپ — حساب ندارد، توکنِ دستگاه دارد
  const dev = await require('./station-devices').bySecret(token);
  if (dev) {
    return {
      app: 'pump', accountKind: 'station', accountId: dev.station_id, accountName: '',
      userId: '', role: 'device', via: 'device', deviceUid: dev.device_uid || '',
    };
  }
  return null;
}

function tokenOf(req) {
  const h = req.headers?.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(h.trim());
  if (m) return m[1].trim();
  const q = req.query?.token;
  return typeof q === 'string' ? q.trim() : '';
}

/** میان‌افزارِ اکسپرس: `req.sync` = بافتِ حساب. */
async function requireSyncAccount(req, res, next) {
  try {
    const ctx = await resolveToken(tokenOf(req));
    if (!ctx) {
      //  STF-10: دستگاهِ ازکارافتاده خطای روشن می‌گیرد و تلاشش شمرده می‌شود
      const block = require('./device-block');
      if (await block.explain(tokenOf(req))) return next(forbidden(block.MESSAGE, block.CODE));
      return next(unauthorized('نشست شما منقضی شده است، دوباره وارد شوید', 'invalid_token'));
    }
    if (!ctx.accountId) {
      if (ctx.missing === 'courier_scope') {
        return next(forbidden(require('../middleware/auth').COURIER_SCOPE_MESSAGE, 'courier_scope'));
      }
      return next(forbidden(
        ctx.missing === 'no_station' ? 'برای این حساب پمپی ثبت نشده است' : 'برای این حساب دکانی ثبت نشده است',
        ctx.missing || 'no_account'
      ));
    }
    const claimed = appAlias(req.body?.app) || appAlias(req.headers?.['x-app']) || appAlias(req.headers?.['x-app-id']) || appAlias(req.query?.app);
    if (claimed && claimed !== ctx.app) {
      return next(forbidden('این نشست مالِ بخشِ دیگری است', 'app_mismatch'));
    }
    req.sync = ctx;
    req.appSection = ctx.app;
    if (ctx.accountKind === 'shop') { req.shopId = ctx.accountId; req.role = ctx.role; }
    else req.stationId = ctx.accountId;
    next();
  } catch (err) { next(err); }
}

/** Pushِ Sync v1 و v3 (هر دو روتر `/push` دارند) — نه عکس، نه تعارض */
function isPush(req) {
  return req.method === 'POST' && req.path === '/push';
}

/** نوشتنِ عکسِ کالا و صندوقِ عکس در `routes/sync-v1.js` */
function isPhotoWrite(req) {
  return /^\/(photos|photo-inbox)(\/|$)/.test(String(req.path || ''));
}

/**
 * نوشتن: عضوِ دکان باید `data.write` داشته باشد؛ عضوِ پمپ و کامپیوترِ پمپ می‌نویسند.
 *
 * و همان قاعدهٔ `requireDataWrite`ِ راهِ قدیم: شاگرد فقط وقتی دکان
 * `multi_device` دارد. پیش‌تر این‌جا فقط نقش سنجیده می‌شد، پس توکنِ شاگرد
 * در دکانی که اشتراکش تمام شده از راهِ v1 همچنان می‌نوشت. opها روی گوشی
 * می‌مانند (۴۰۳ صف را پاک نمی‌کند) و با تمدید بالا می‌روند.
 */
function requireSyncWrite(req, res, next) {
  const ctx = req.sync;
  if (!ctx) return next(unauthorized());
  if (ctx.accountKind !== 'shop') return next();
  const permCore = require('./perm-core');
  if (!can(ctx.role, 'data.write')) {
    //  A16: Pushِ نقشی که فهرستِ جدولِ مشخص دارد ولی هیچ نمی‌نویسد (حسابدار) یکجا ۴۰۳
    //  نمی‌گیرد: opهای ماندهٔ دستگاهش (پیش از تغییرِ نقش، یا بازیابیِ پشتیبان) در
    //  v1Guard / authorizeِ v3 op به op «table_not_allowed» می‌گیرند، دستگاه آن‌ها را از
    //  صف بیرون می‌اندازد و Pullاش نمی‌خوابد. درهای دیگرِ نوشتن (عکس، تعارض) همان ۴۰۳.
    if (isPush(req) && Array.isArray(permCore.writable(ctx.role))) return next();
    return next(forbidden('اجازه‌ی ثبت اطلاعات ندارید', 'permission_denied'));
  }
  //  A16: عکسِ کالا و صندوقِ عکس یعنی «کالا» — همان `canWrite`ِ هسته (صندوق‌دار و جمع‌آور نه).
  //  سه نقشِ قدیم «*»اند و این‌جا هرگز گیر نمی‌کنند.
  if (isPhotoWrite(req) && !permCore.canWrite(ctx.role, 'products')) {
    return next(forbidden('عکسِ کالا در حدِ نقشِ شما نیست', 'table_not_allowed'));
  }
  if (ctx.role === 'owner' || ctx.role === 'manager') return next();
  (async () => {
    const { entitlementOf } = require('./entitlement');
    const ent = await entitlementOf(ctx.accountId);
    if (ent.features.includes('multi_device')) return next();
    return next(forbidden(
      'اشتراک این دکان تمام شده است. فروش‌های شما روی همین گوشی می‌مانند و ' +
      'به‌محض تمدید بالا می‌روند.',
      'subscription_required'
    ));
  })().catch(next);
}

module.exports = { resolveToken, requireSyncAccount, requireSyncWrite, tokenOf, appAlias };
