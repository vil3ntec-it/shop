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

  const row = await tokens.verify(token, 'access', null);
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
    const m = await require('./shops').membershipOf(user.id);
    if (!m) return { app, accountKind: 'shop', accountId: '', userId: user.id, role: '', via: 'user', missing: 'no_shop' };
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
    if (!ctx) return next(unauthorized('نشست شما منقضی شده است، دوباره وارد شوید', 'invalid_token'));
    if (!ctx.accountId) {
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

/** نوشتن: عضوِ دکان باید `data.write` داشته باشد؛ عضوِ پمپ و کامپیوترِ پمپ می‌نویسند. */
function requireSyncWrite(req, res, next) {
  const ctx = req.sync;
  if (!ctx) return next(unauthorized());
  if (ctx.accountKind === 'shop' && !can(ctx.role, 'data.write')) {
    return next(forbidden('اجازه‌ی ثبت اطلاعات ندارید', 'permission_denied'));
  }
  next();
}

module.exports = { resolveToken, requireSyncAccount, requireSyncWrite, tokenOf, appAlias };
