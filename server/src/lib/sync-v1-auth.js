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
const { one, many, now } = require('../db');
const { sectionOf, SHOP } = require('./tenancy');
const { can } = require('./permissions');
const { unauthorized, forbidden } = require('../middleware/errors');

/** نام‌های برنامه در پرامپت (`dukan`) هم پذیرفته می‌شوند. */
function appAlias(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (s === 'dukan' || s === 'dokan') return 'shop';
  return sectionOf(s);
}

/**
 * بافتِ حساب از ردیفِ توکنی که **پیش‌تر** سنجیده شده (باطل نیست؛ انقضا را صدازننده می‌داند):
 * کاربرِ فعال، دستگاهِ فعال، عضویت و نقش. همان بخشِ دومِ `resolveToken`، جدا تا سوکتِ زندهٔ
 * برنامهٔ کهنه پس از پیر شدنِ توکن هم با همین قاعده سنجیده شود (S78، `sync-v1-live`).
 * @returns {Promise<null | {app, accountKind, accountId, accountName, userId, role, via, deviceUid, expiresAt}>}
 */
async function contextOfRow(row) {
  const user = await one('SELECT id, status FROM users WHERE id=$1', [row.subject_id]);
  if (!user || user.status !== 'active') return null;
  //  SEC-AUTH-14: دستگاهی که این نشست با آن ساخته شد — شناسهٔ دستگاهِ همگام‌سازی باید همین باشد
  let deviceUid = '';
  if (row.device_id) {
    const dev = await one('SELECT status, device_uid FROM devices WHERE id=$1', [row.device_id]);
    if (!dev || dev.status !== 'active') return null;
    deviceUid = String(dev.device_uid || '');
  }
  const app = row.app || 'shop';
  //  S78: سوکتِ زنده سرِ همین لحظه بسته می‌شود (`sync-v1-live`، کدِ 4403)
  const expiresAt = Number(row.expires_at) || 0;
  if (app === 'pump') {
    const m = await require('./stations').membershipOf(user.id);
    if (!m) return { app, accountKind: 'station', accountId: '', userId: user.id, role: '', via: 'user', missing: 'no_station' };
    return {
      app, accountKind: 'station', accountId: m.station_id, accountName: m.station_name || '',
      userId: user.id, role: m.role, via: 'user', deviceUid, expiresAt,
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
    userId: user.id, role: m.role, via: 'user', deviceUid, expiresAt,
  };
}

/**
 * از یک توکنِ خام، بافتِ حساب و ردیفِ توکن را می‌سازد — یا null. سوکتِ زنده ردیف را
 * نگه می‌دارد (`sync-v1-live`)؛ درهای HTTP فقط `resolveToken`.
 * @returns {Promise<null | {ctx: object, row: object|null}>}
 */
async function resolveSession(token) {
  if (!token || typeof token !== 'string' || token.length < 20) return null;

  const row = await tokens.verify(token, 'access', null, { anyScope: true });
  if (row) {
    const ctx = await contextOfRow(row);
    return ctx ? { ctx, row } : null;
  }

  //  کامپیوترِ پمپ — حساب ندارد، توکنِ دستگاه دارد
  const dev = await require('./station-devices').bySecret(token);
  if (dev) {
    return {
      ctx: {
        app: 'pump', accountKind: 'station', accountId: dev.station_id, accountName: '',
        userId: '', role: 'device', via: 'device', deviceUid: dev.device_uid || '',
      },
      row: null,
    };
  }
  return null;
}

/**
 * از یک توکنِ خام، بافتِ حساب را می‌سازد — یا null.
 * @returns {null | {app, accountKind, accountId, accountName, userId, role, via, expiresAt?}}
 */
async function resolveToken(token) {
  const out = await resolveSession(token);
  return out ? out.ctx : null;
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
    const wrongDevice = await checkDevice(req, ctx);
    if (wrongDevice) return next(wrongDevice);
    //  گاوصندوق (PAY-18): پرچمِ `sync.safe_entries` یک بار برای همین درخواست (‎lib/safe-flag.js‎)
    if (ctx.app === SHOP.app && ctx.accountKind === SHOP.app) ctx.safeEntries = await require('./safe-flag').enabledFor(ctx.accountId);
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

/*
 *  ── SEC-AUTH-14: شناسهٔ دستگاهِ همگام‌سازی از آنِ همین نشست است ────────────────
 *
 *  `device_id`ِ push/pull/status/عکس (و v3) را کلاینت می‌فرستد و سرور تا ۲.۱۴.۴ آن را
 *  بی سنجش می‌پذیرفت. چند تصمیم به همین شناسه بسته است: «حذف فقط ردیفی که خودِ همین
 *  دستگاه ساخته» (`sync-v1.authorizeOp`، `data.delete.own`ِ شاگرد)، مکانِ دفترِ هر دستگاه
 *  (`sync_devices`) و سرِ زنجیرهٔ اسنادِ هر دستگاه (#541). شاگردی که شناسهٔ دستگاهِ صاحب را
 *  از `GET /status` برمی‌داشت، با فرستادنِ همان ردیف‌های صاحب را پاک می‌کرد.
 *
 *  حالا: توکنی که با دستگاهِ ثبت‌شده ساخته شده (`tokens.device_id` ⇐ `devices.device_uid`،
 *  همان `SyncStore.deviceUid` که برنامه هنگامِ ورود هم می‌فرستد) یا توکنِ کامپیوترِ پمپ
 *  (`station_devices.device_uid`) فقط با **همان** شناسه همگام می‌شود. هر شناسهٔ دیگری در
 *  بدنه، querystring یا `X-Device`:
 *    • پرچمِ `sync.device_bind` (live_config، `all` یا شناسهٔ دکان/پمپ) روشن ⇒ `403 device_mismatch`
 *    • خاموش (پیش‌فرض) ⇒ فقط ثبت (یک ردیفِ سابقهٔ `sync.device_mismatch` در روز برای هر
 *      دستگاهِ ادعاشده + هشدار در لاگ) تا صاحبِ سامانه پیش از روشن کردن ببیند برنامه‌ای می‌شکند یا نه.
 *  نشستِ بی دستگاه (نسخه‌های کهنه‌ای که هنگامِ ورود شناسه نفرستادند) «بسته» نیست؛ با پرچمِ روشن
 *  فقط شناسه‌ای که مالِ **کاربرِ دیگری** ثبت شده رد می‌شود.
 */
const DEVICE_FLAG = 'sync.device_bind';
const DEVICE_MISMATCH = 'این شناسهٔ دستگاه مالِ این نشست نیست. از برنامه خارج و دوباره وارد شوید.';

/** همهٔ شناسه‌هایی که درخواست برای «دستگاهِ من» آورده — هر مسیر یکی از این‌ها را می‌خواند. */
function claimedDevices(req) {
  const b = req.body && typeof req.body === 'object' && !Buffer.isBuffer(req.body) ? req.body : {};
  const q = req.query || {};
  const out = [];
  for (const v of [b.device_id, b.deviceId, q.device_id, q.deviceId, req.headers?.['x-device']]) {
    const s = typeof v === 'string' ? v.trim().slice(0, 64) : '';
    if (s && !out.includes(s)) out.push(s);
  }
  return out;
}

function decodeFlag(text) {
  try { return JSON.parse(text); } catch { return undefined; }
}

/** پرچمِ حساب بر `all` جلو می‌افتد؛ فقط `true` روشن است (همان قاعدهٔ پرچم‌های دکان). */
async function bindEnforced(ctx) {
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [DEVICE_FLAG, 'all', String(ctx.accountId || '')]
  );
  let v;
  for (const r of rows) if (r.scope === 'all') v = decodeFlag(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decodeFlag(r.value);
  return v === true;
}

const warned = new Map();
async function noteMismatch(req, ctx, claimed, enforced) {
  const key = `${ctx.accountId}|${ctx.deviceUid}|${claimed}`;
  const t = Date.now();
  if (!(warned.get(key) > t)) {
    if (warned.size > 5000) warned.clear();
    warned.set(key, t + 3_600_000);
    console.warn(`[sync] شناسهٔ دستگاهِ ناجور ${enforced ? '(رد شد)' : '(فقط ثبت؛ پرچمِ sync.device_bind خاموش)'}: ` +
      `account=${ctx.accountId} bound=${ctx.deviceUid || '-'} claimed=${claimed} path=${String(req.originalUrl || '').split('?')[0]}`);
  }
  await require('./audit').log({
    shopId: ctx.accountKind === 'shop' ? ctx.accountId : '', actorType: ctx.via === 'device' ? 'system' : 'user',
    userId: ctx.userId || '', action: 'sync.device_mismatch', targetType: 'device', targetId: claimed,
    detail: { bound: ctx.deviceUid || '', enforced, app: ctx.app, account: ctx.accountId },
    once: true, onceSince: now() - 86_400_000,
  }).catch(() => {});
}

/** خطای ۴۰۳ اگر باید رد شود؛ وگرنه null (با ثبت در حالتِ «فقط ثبت»). */
async function checkDevice(req, ctx) {
  const claims = claimedDevices(req);
  if (!claims.length) return null;
  if (ctx.deviceUid) {
    const other = claims.find((c) => c !== ctx.deviceUid);
    if (!other) return null;
    const enforced = await bindEnforced(ctx);
    await noteMismatch(req, ctx, other, enforced);
    return enforced ? forbidden(DEVICE_MISMATCH, 'device_mismatch') : null;
  }
  //  نشستِ بی دستگاه: فقط با پرچمِ روشن، و فقط شناسه‌ای که دستگاهِ ثبت‌شدهٔ کاربرِ دیگری است
  if (!ctx.userId || !(await bindEnforced(ctx))) return null;
  for (const c of claims) {
    const taken = await one('SELECT 1 AS x FROM devices WHERE device_uid=$1 AND user_id<>$2 LIMIT 1', [c, ctx.userId]);
    if (taken) {
      await noteMismatch(req, ctx, c, true);
      return forbidden(DEVICE_MISMATCH, 'device_mismatch');
    }
  }
  return null;
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

module.exports = {
  resolveToken, resolveSession, contextOfRow, requireSyncAccount, requireSyncWrite, tokenOf, appAlias,
  claimedDevices, checkDevice, DEVICE_FLAG,
};
