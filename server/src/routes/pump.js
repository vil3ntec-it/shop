'use strict';
/**
 * بخشِ پمپ‌بنزین — همان چیزهایی که بخشِ شاپ دارد، روی دفترِ خودش.
 *
 * ── راهِ داده ───────────────────────────────────────────────────────
 *   برنامهٔ کامپیوتر ──push──▶ پوشهٔ ابریِ همین پمپ ──pull──▶ گوشیِ کارمند
 *                                     ▲
 *                    اپِ کارمند نشانیِ سرورِ خانگی را هم از همین‌جا
 *                    می‌گیرد، پس دیگر از کسی نشانی پرسیده نمی‌شود.
 *
 * ⚠️ پوشهٔ ابری **نسخه** است، نه اصل. دفترِ پمپ روی کامپیوترِ خودِ پمپ
 * می‌ماند؛ همان قاعدهٔ «برنامهٔ نیتیو اصلِ اطلاعات است». این‌جا برای آن
 * است که گوشی از هر جای دنیا ببیندش و اگر کامپیوتر سوخت، داده گم نشود.
 *
 * ⚠️ و فقط **صاحب و مدیرِ** پمپ می‌نویسند. کارمند می‌خواند و در
 * `inbox.json` پیام می‌گذارد — هیچ گوشی‌ای حسابی را عوض نمی‌کند.
 */
const express = require('express');
const { one, query, now, newId } = require('../db');
const v = require('../lib/validate');
const config = require('../config');
const stations = require('../lib/stations');
const subs = require('../lib/subscriptions').pump;
const vip = require('../lib/vip-codes').pump;
const license = require('../lib/license');
const audit = require('../lib/audit');
const plans = require('../lib/plans');
const { catalogOf } = require('../lib/features');
const { entitlementOf } = require('../lib/entitlement').pump;
const { requireUser, requireStation, optionalStation } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound } = require('../middleware/errors');

const router = express.Router();
const PUMP = catalogOf('pump');

/** کاتالوگِ قابلیت‌های پمپ — باز است، چون فهرستِ بخش‌ها راز نیست. */
router.get('/features', (req, res) => {
  res.json({ features: PUMP.FEATURES, free: PUMP.FREE_KEYS, core: PUMP.CORE_KEYS });
});

router.use(requireUser);

/** نوشتن روی پمپ فقط کارِ صاحب و مدیر است. */
function requireStationOwner(req, res, next) {
  if (req.stationRole === 'owner' || req.stationRole === 'manager') return next();
  next(forbidden('این کار فقط از صاحب پمپ برمی‌آید', 'permission_denied'));
}

/**
 * «پمپِ من» — همان چیزی که برنامه بعد از ورود با گوگل صدا می‌زند.
 *
 * اگر پمپی نباشد خطا نمی‌دهد؛ `station: null` برمی‌گرداند تا برنامه
 * بداند باید صفحهٔ «پمپت را بساز» را نشان بدهد.
 */
router.get('/me', optionalStation, async (req, res, next) => {
  try {
    if (!req.stationId) {
      return res.json({ station: null, entitlement: null, serverTime: now() });
    }
    const st = await stations.getStation(req.stationId);
    const ent = await entitlementOf(req.stationId);
    res.json({
      station: stations.shape(st),
      role: req.stationRole,
      entitlement: ent,
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

/**
 * ساختِ پمپ.
 *
 * هر حساب یک پمپ. `code` اختیاری است — اگر نیاید، از روی شناسه ساخته
 * می‌شود تا کسی که تازه برنامه را باز کرده مجبور نباشد چیزی بنویسد.
 */
router.post('/', async (req, res, next) => {
  try {
    const name = v.text(req.body?.name, { max: 80 });
    const code = v.text(req.body?.code, { max: 40 });
    const st = await stations.createStation(req.user.id, { name, code });
    await audit.log({
      userId: req.user.id, action: 'station.created',
      detail: { stationId: st.id, code: st.code }, ip: clientIp(req),
    });
    const ent = await entitlementOf(st.id);
    res.status(201).json({ station: stations.shape(st), entitlement: ent, serverTime: now() });
  } catch (err) { next(err); }
});

router.use(requireStation);

/** نامِ پمپ و نشانیِ سرورِ خانگی‌اش. */
router.patch('/', requireStationOwner, async (req, res, next) => {
  try {
    const patch = {};
    if (req.body?.name !== undefined) patch.name = v.text(req.body.name, { max: 80 });
    if (req.body?.homeUrl !== undefined) patch.homeUrl = v.text(req.body.homeUrl, { max: 300 });
    const st = await stations.updateStation(req.stationId, patch);
    res.json({ station: stations.shape(st), serverTime: now() });
  } catch (err) { next(err); }
});

/**
 * ثبتِ نشانیِ سرورِ خانگی — همان چیزی که کارِ «آدرس نپرس» را ممکن می‌کند.
 *
 * برنامهٔ کامپیوتر هر بار که بالا می‌آید این را می‌فرستد. اپِ کارمند
 * بعد از ورود با گوگل، همان را از `/pump/me` می‌خواند و مستقیم وصل
 * می‌شود — دیگر نه کسی نشانی می‌نویسد و نه کیو‌آری لازم است.
 *
 * آی‌پیِ خانگی با هر بار روشن شدنِ مودم عوض می‌شود، پس این مسیر باید
 * ارزان و پرتکرار باشد؛ به همین خاطر فقط همین یک ستون را می‌نویسد.
 */
router.post('/home', requireStationOwner, async (req, res, next) => {
  try {
    const homeUrl = v.text(req.body?.homeUrl ?? req.body?.url, {
      max: 300, required: true, field: 'نشانیِ سرورِ خانگی',
    });
    const st = await stations.updateStation(req.stationId, { homeUrl });
    res.json({ station: stations.shape(st), serverTime: now() });
  } catch (err) { next(err); }
});

/** اعضای پمپ. */
router.get('/members', async (req, res, next) => {
  try {
    res.json({ members: await stations.members(req.stationId), serverTime: now() });
  } catch (err) { next(err); }
});

router.patch('/members/:id', requireStationOwner, async (req, res, next) => {
  try {
    const row = await stations.updateMember(req.stationId, req.params.id, {
      role: req.body?.role, status: req.body?.status,
    });
    res.json({ member: row, serverTime: now() });
  } catch (err) { next(err); }
});

// ──────────────────────────────────────────────────────────────────
//  اشتراک
// ──────────────────────────────────────────────────────────────────

/** وضعیتِ اشتراکِ این پمپ. */
router.get('/subscription', async (req, res, next) => {
  try {
    const ent = await entitlementOf(req.stationId);
    res.json({
      entitlement: ent,
      subscription: ent.subscription,
      trial: ent.trial,
      history: (await subs.changeLog(req.stationId, 20)).map(r => ({
        action: r.action, plan: r.plan, newEndsAt: r.new_ends_at ? Number(r.new_ends_at) : null,
        createdAt: Number(r.created_at), note: r.note || '',
      })),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

/**
 * خرج کردنِ کدِ شش‌رقمی.
 *
 * محدودیتِ نرخ همان سقفی است که بخشِ شاپ دارد: کد شش رقم است و بدون
 * این، کسی می‌توانست با آزمودنِ پشت‌سرهم به کدِ زنده‌ی دیگری برسد.
 */
router.post(
  '/vip/redeem',
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'pump-vip-redeem' }),
  async (req, res, next) => {
    try {
      const code = v.text(req.body?.code, { max: 20, required: true, field: 'کد' });
      const out = await vip.redeem(code, { userId: req.user.id, tenantId: req.stationId });
      await audit.log({
        userId: req.user.id, action: 'pump.vip.redeemed',
        detail: { stationId: req.stationId, plan: out.plan, days: out.days }, ip: clientIp(req),
      });
      const ent = await entitlementOf(req.stationId);
      res.status(201).json({
        ok: true,
        message: `اشتراک پمپ شما فعال شد — ${out.days} روز.`,
        subscription: subs.stateOf(out.subscription),
        entitlement: ent,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

/**
 * مجوزِ امضاشده برای این دستگاه.
 *
 * قرینه‌ی `/license/sync`، ولی با شنونده‌ی `tohid-pump-app` — تا مجوزِ
 * دکان نتواند برنامهٔ پمپ را باز کند.
 */
router.post('/license', async (req, res, next) => {
  try {
    const raw = req.body?.device?.uid ?? req.body?.deviceUid;
    const deviceUid = v.text(raw, { max: 120, required: true, field: 'شناسه‌ی دستگاه' });
    const deviceName = v.text(req.body?.device?.name ?? req.body?.deviceName, { max: 120 });

    const ent = await entitlementOf(req.stationId);
    const at = now();

    if (ent.source === 'free') {
      return res.json({
        license: null, reason: 'no_subscription', source: ent.source,
        features: ent.features, serverTime: at,
      });
    }

    const endsAt = ent.source === 'trial'
      ? Number(ent.trial.endsAt || 0)
      : Number(ent.subscription.endsAt || 0);

    const issued = await license.issue({
      deviceUid,
      accountId: String(req.user.id),
      deviceName,
      features: ent.features,
      core: [...PUMP.CORE_KEYS],
      subscriptionEndsAt: endsAt,
      plan: ent.subscription.plan || (ent.source === 'trial' ? 'trial' : ''),
      planTitle: ent.source === 'trial' ? 'دوره‌ی آزمایشی' : (ent.subscription.plan || ''),
      audience: license.AUDIENCE_PUMP,
      at,
    });

    res.json({
      license: issued.token,
      publicKey: await license.publicKey(),
      source: ent.source,
      features: ent.features,
      expiresAt: issued.expiresAt,
      subscriptionEndsAt: issued.subscriptionEndsAt,
      serverTime: at,
    });
  } catch (err) { next(err); }
});

// ──────────────────────────────────────────────────────────────────
//  پوشهٔ ابریِ پمپ
// ──────────────────────────────────────────────────────────────────

/** نامِ فایل — همان محدودیتی که یک نامِ فایلِ واقعی دارد. */
function cleanPath(raw) {
  const s = String(raw || '').trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(s) || s.includes('..')) {
    throw badRequest('نامِ فایل معتبر نیست', 'bad_path');
  }
  return s;
}

/** فهرستِ پوشه — بی خودِ داده، تا گوشی بداند چه چیزی تازه شده. */
router.get('/files', async (req, res, next) => {
  try {
    const { rows } = await query(
      `SELECT path, rev, size, updated_at FROM station_files
        WHERE station_id=$1 ORDER BY path`, [req.stationId]
    );
    const r = await one('SELECT last_rev FROM station_rev WHERE station_id=$1', [req.stationId]);
    res.json({
      files: rows.map(x => ({
        path: x.path, rev: Number(x.rev), size: Number(x.size), updatedAt: Number(x.updated_at),
      })),
      rev: r ? Number(r.last_rev) : 0,
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

/** خواندنِ یک فایل — هر عضوِ پمپ می‌تواند. */
router.get('/files/:path', async (req, res, next) => {
  try {
    const path = cleanPath(req.params.path);
    const row = await one(
      'SELECT * FROM station_files WHERE station_id=$1 AND path=$2', [req.stationId, path]
    );
    if (!row) throw notFound('این فایل هنوز ساخته نشده است', 'file_not_found');
    res.json({
      path: row.path, data: row.data, rev: Number(row.rev),
      updatedAt: Number(row.updated_at), serverTime: now(),
    });
  } catch (err) { next(err); }
});

/**
 * نوشتنِ یک فایل — فقط صاحب و مدیر.
 *
 * ⚠️ `inbox.json` استثناست و عمداً: راهِ برگشتِ داده از گوشیِ کارمند
 * همان است. اگر کارمند نتواند رویش بنویسد، «صندوقِ ورودی» اصلاً وجود
 * ندارد. ولی همان‌جا هم چیزی را عوض نمی‌کند — فقط اضافه می‌کند.
 */
router.put('/files/:path', async (req, res, next) => {
  try {
    const path = cleanPath(req.params.path);
    const isInbox = path === 'inbox.json';
    if (!isInbox && req.stationRole !== 'owner' && req.stationRole !== 'manager') {
      throw forbidden('فقط برنامهٔ خودِ پمپ روی این فایل می‌نویسد', 'read_only');
    }

    const data = req.body?.data;
    if (data === undefined || data === null || typeof data !== 'object') {
      throw badRequest('داده باید یک شیء باشد', 'bad_data');
    }
    const text = JSON.stringify(data);
    //  سقف، همان سقفِ بدنه‌ی درخواست است. بی این، یک پمپ می‌توانست
    //  دیتابیس را با یک فایل پر کند.
    if (text.length > 1_500_000) throw badRequest('این فایل بیش از حد بزرگ است', 'too_large');

    const t = now();
    const bumped = await one(
      `UPDATE station_rev SET last_rev = last_rev + 1 WHERE station_id=$1 RETURNING last_rev`,
      [req.stationId]
    );
    const rev = bumped ? Number(bumped.last_rev) : 1;

    const row = await one(
      `INSERT INTO station_files (station_id, path, data, rev, size, device_id, user_id, updated_at)
       VALUES ($1,$2,$3::jsonb,$4,$5,$6,$7,$8)
       ON CONFLICT (station_id, path) DO UPDATE SET
         data=excluded.data, rev=excluded.rev, size=excluded.size,
         device_id=excluded.device_id, user_id=excluded.user_id, updated_at=excluded.updated_at
       RETURNING *`,
      [req.stationId, path, text, rev, text.length,
        req.device?.id || '', req.user.id, t]
    );
    res.json({ path: row.path, rev: Number(row.rev), updatedAt: Number(row.updated_at), serverTime: t });
  } catch (err) { next(err); }
});

module.exports = router;
