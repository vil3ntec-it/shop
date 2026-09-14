'use strict';
/**
 * برنامهٔ کامپیوترِ پمپ — بی حساب، بی مرورگر، بی نشانی.
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * «نشانی توی خودِ برنامه باشد و دیده نشود، و اشتراک هم از سرور برایش
 * برود، و کسی نتواند کرک کند یا دور بزند.»
 *
 * ── راه ───────────────────────────────────────────────────────────
 *
 *   صاحبِ پمپ شش رقم را در برنامه می‌زند
 *        │
 *        ▼
 *   POST /api/pump/activate   ← تنها مسیرِ بی‌توکنِ این پرونده
 *        │  پمپ ساخته/پیدا می‌شود · اشتراک فعال می‌شود
 *        │  دستگاه ثبت می‌شود · مجوزِ امضاشده صادر می‌شود
 *        ▼
 *   توکنِ دستگاه  ──▶  /api/pump/device/…  از این به بعد
 *
 * از آن لحظه برنامه هیچ‌چیز از کاربر نمی‌پرسد: نه نشانی، نه رمز، نه
 * حساب. نشانی در خودِ برنامه قفل است و این مسیرها بقیه‌اش را می‌دهند.
 *
 * ── چرا این «دور زدن» را سخت‌تر می‌کند ────────────────────────────
 * چون تصمیم دیگر در برنامه گرفته نمی‌شود. مجوز را **سرور** امضا
 * می‌کند، به همین دستگاه و همین پمپ می‌بندد، و ده روزه منقضی‌اش
 * می‌کند. برنامه‌ای که دست‌کاری شده هم نمی‌تواند مجوز بسازد: کلید
 * خصوصی دستِ سرور است. و نوشتن روی پوشهٔ ابری هر بار روی سرور سنجیده
 * می‌شود، نه در برنامه.
 */
const express = require('express');
const { one, query, now } = require('../db');
const v = require('../lib/validate');
const config = require('../config');
const stations = require('../lib/stations');
const devices = require('../lib/station-devices');
const vip = require('../lib/vip-codes').pump;
const subs = require('../lib/subscriptions').pump;
const license = require('../lib/license');
const audit = require('../lib/audit');
const { catalogOf } = require('../lib/features');
const { entitlementOf } = require('../lib/entitlement').pump;
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound, unauthorized } = require('../middleware/errors');

const router = express.Router();
const PUMP = catalogOf('pump');

/* ══════════════════════════════════════════════════════════════════
   فعال‌سازی — تنها مسیرِ بی‌توکن
   ══════════════════════════════════════════════════════════════════ */

/**
 * کدِ شش‌رقمی ⇒ پمپ + اشتراک + توکنِ دستگاه + مجوز.
 *
 * ⚠️ محدودیتِ نرخ این‌جا از هر جای دیگری مهم‌تر است: این مسیر بی‌توکن
 * است و کد شش رقم دارد. بی این، کسی می‌توانست پشت‌سرهم بیازماید تا به
 * کدِ خریده‌نشده‌ای برسد.
 */
router.post(
  '/activate',
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'pump-activate' }),
  async (req, res, next) => {
    try {
      const code = v.text(req.body?.code, { max: 20, required: true, field: 'کد' });
      const deviceUid = v.text(req.body?.device?.uid ?? req.body?.deviceUid, {
        max: 120, required: true, field: 'شناسهٔ دستگاه',
      });
      const deviceName = v.text(req.body?.device?.name, { max: 80 });
      const platform = v.text(req.body?.device?.platform, { max: 40 });
      const wantCode = v.text(req.body?.station?.code ?? req.body?.stationCode, { max: 40 });
      const wantName = v.text(req.body?.station?.name ?? req.body?.stationName, { max: 80 });

      /*
       *  ── پمپ کدام است ────────────────────────────────────────────
       *  سه حالت، به همین ترتیب:
       *    ۱) این دستگاه از قبل جایی ثبت شده  ⇒ همان پمپ
       *    ۲) کد به نامِ پمپِ مشخصی صادر شده  ⇒ همان پمپ
       *    ۳) هیچ‌کدام                          ⇒ پمپِ تازه
       *
       *  حالتِ اول همان چیزی است که «تمدید» را ساده می‌کند: سالِ بعد
       *  همان برنامه، کدِ تازه، همان پمپ — نه پمپِ دومِ خالی.
       */
      const known = await one(
        `SELECT d.station_id FROM station_devices d
           JOIN stations s ON s.id = d.station_id
          WHERE d.device_uid=$1 AND d.status='active' AND s.status='active'
          ORDER BY d.created_at DESC LIMIT 1`,
        [deviceUid]
      );

      let stationId = known?.station_id || null;

      if (!stationId) {
        const clean = String(code).replace(/\D/g, '');
        const peek = await one(
          `SELECT station_id, status FROM ${require('../lib/tenancy').PUMP.vipTable}
            WHERE code_hash=$1`,
          [require('../lib/vip-codes').hashCode(clean)]
        );
        if (!peek) throw notFound('این کد معتبر نیست', 'bad_code');
        stationId = peek.station_id || null;
      }

      let created = false;
      if (!stationId) {
        //  پمپِ تازه، هنوز بی صاحبِ گوگل. کارمند یا کارفرما بعداً با
        //  کدِ پیوستن می‌آید.
        const st = await stations.createStationForDevice({ code: wantCode, name: wantName });
        stationId = st.id;
        created = true;
      }

      //  اشتراک: همان کد را خرج می‌کند. اگر کد بد باشد این‌جا می‌افتد و
      //  پمپِ تازه‌ساخته بی‌اشتراک می‌ماند — که درست است، نه نیمه‌کاره.
      const out = await vip.redeem(code, { userId: `device:${deviceUid}`, tenantId: stationId });

      const reg = await devices.register(stationId, {
        uid: deviceUid, name: deviceName, platform, ip: clientIp(req),
      });

      const st = await stations.getStation(stationId);
      const ent = await entitlementOf(stationId);
      const issued = await signFor(st, ent, deviceUid, deviceName);

      await audit.log({
        action: 'pump.device_activated',
        detail: { stationId, created, plan: out.plan, days: out.days }, ip: clientIp(req),
      });

      res.status(201).json({
        ok: true,
        message: `اشتراک این پمپ فعال شد — ${out.days} روز.`,
        deviceToken: reg.token,          // فقط همین یک بار
        station: stations.shape(st),
        createdStation: created,
        entitlement: ent,
        subscription: subs.stateOf(out.subscription),
        ...issued,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

/* ══════════════════════════════════════════════════════════════════
   از این‌جا به بعد: توکنِ دستگاه
   ══════════════════════════════════════════════════════════════════ */

/**
 * نگهبانِ توکنِ دستگاه.
 *
 * ⚠️ `station_id` از خودِ ردیفِ توکن می‌آید، نه از بدنهٔ درخواست — همان
 * قاعده‌ای که همه‌جای این سامانه هست و به همین دلیل: وگرنه عوض کردنِ یک
 * شناسه در درخواست، دفترِ پمپِ دیگری را باز می‌کرد.
 */
async function requireDevice(req, res, next) {
  try {
    const h = req.headers.authorization || '';
    const m = /^Bearer\s+(.+)$/i.exec(h.trim());
    const row = m ? await devices.bySecret(m[1].trim()) : null;
    if (!row) return next(unauthorized('این دستگاه ثبت نشده است', 'device_not_registered'));
    req.stationDevice = row;
    req.stationId = row.station_id;
    next();
  } catch (err) { next(err); }
}

/** مجوزِ امضاشده برای این دستگاه و همین پمپ. */
async function signFor(station, ent, deviceUid, deviceName = '') {
  if (ent.source === 'free') {
    return { license: null, reason: 'no_subscription', features: ent.features };
  }
  const endsAt = ent.source === 'trial'
    ? Number(ent.trial.endsAt || 0)
    : Number(ent.subscription.endsAt || 0);

  const issued = await license.issue({
    deviceUid,
    accountId: station.id,
    deviceName,
    features: ent.features,
    core: [...PUMP.CORE_KEYS],
    subscriptionEndsAt: endsAt,
    plan: ent.subscription.plan || (ent.source === 'trial' ? 'trial' : ''),
    planTitle: ent.source === 'trial' ? 'دوره‌ی آزمایشی' : (ent.subscription.plan || ''),
    audience: license.AUDIENCE_PUMP,
    tenantId: station.id,
  });
  return {
    license: issued.token,
    publicKey: await license.publicKey(),
    features: ent.features,
    expiresAt: issued.expiresAt,
    subscriptionEndsAt: issued.subscriptionEndsAt,
  };
}

router.use(requireDevice);

/** «پمپِ من» از دیدِ برنامه — وضعیت، اشتراک، و نشانیِ خانگی. */
router.get('/me', async (req, res, next) => {
  try {
    const st = await stations.getStation(req.stationId);
    const ent = await entitlementOf(req.stationId);
    res.json({
      station: stations.shape(st),
      entitlement: ent,
      subscription: ent.subscription,
      trial: ent.trial,
      device: devices.shape(req.stationDevice),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

/** مجوزِ تازه. برنامه هر چند روز یک بار می‌گیردش. */
router.post('/license', async (req, res, next) => {
  try {
    const st = await stations.getStation(req.stationId);
    const ent = await entitlementOf(req.stationId);
    const issued = await signFor(st, ent, req.stationDevice.device_uid, req.stationDevice.name);
    res.json({ source: ent.source, ...issued, serverTime: now() });
  } catch (err) { next(err); }
});

/** ثبتِ نشانیِ سرورِ خانگی و رمزِ فقط‌خواندنی‌اش. */
router.post('/home', async (req, res, next) => {
  try {
    const homeUrl = v.text(req.body?.homeUrl ?? req.body?.url, {
      max: 300, required: true, field: 'نشانیِ سرورِ خانگی',
    });
    const patch = { homeUrl };
    if (req.body?.readKey !== undefined) patch.readKey = v.text(req.body.readKey, { max: 200 });
    const st = await stations.updateStation(req.stationId, patch);
    res.json({ station: stations.shape(st), serverTime: now() });
  } catch (err) { next(err); }
});

/** تمدید با کدِ تازه، بی فعال‌سازیِ دوباره. */
router.post(
  '/redeem',
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'pump-device-redeem' }),
  async (req, res, next) => {
    try {
      const code = v.text(req.body?.code, { max: 20, required: true, field: 'کد' });
      const out = await vip.redeem(code, {
        userId: `device:${req.stationDevice.device_uid}`, tenantId: req.stationId,
      });
      const st = await stations.getStation(req.stationId);
      const ent = await entitlementOf(req.stationId);
      const issued = await signFor(st, ent, req.stationDevice.device_uid, req.stationDevice.name);
      res.status(201).json({
        ok: true,
        message: `اشتراک تمدید شد — ${out.days} روز.`,
        entitlement: ent,
        subscription: subs.stateOf(out.subscription),
        ...issued,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

/**
 * کدِ پیوستن برای گوشیِ کارمند.
 *
 * پمپی که با کد فعال شده هیچ حسابِ گوگلی ندارد؛ این کد راهِ کارمند
 * به آن است. برنامهٔ کامپیوتر نشانش می‌دهد و کارمند در اپ می‌زندش.
 */
router.post('/join-code', async (req, res, next) => {
  try {
    const out = await devices.mintJoinCode(req.stationId, {
      role: v.text(req.body?.role, { max: 10 }) || 'staff',
      hours: v.integer(req.body?.hours, { min: 1, max: 720, def: 24 }),
      maxUses: v.integer(req.body?.maxUses, { min: 1, max: 100, def: 10 }),
    });
    res.status(201).json({ ...out, serverTime: now() });
  } catch (err) { next(err); }
});

/* ── پوشهٔ ابری ────────────────────────────────────────────────── */

function cleanPath(raw) {
  const s = String(raw || '').trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(s) || s.includes('..')) {
    throw badRequest('نامِ فایل معتبر نیست', 'bad_path');
  }
  return s;
}

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

router.put('/files/:path', async (req, res, next) => {
  try {
    const path = cleanPath(req.params.path);
    const data = req.body?.data;
    if (data === undefined || data === null || typeof data !== 'object') {
      throw badRequest('داده باید یک شیء باشد', 'bad_data');
    }

    //  ⚠️ همان بررسیِ اشتراکی که مسیرِ کاربر دارد. اگر این‌جا نبود، کلِ
    //  قفل با یک درخواستِ دستگاه دور زده می‌شد.
    const ent = await entitlementOf(req.stationId);
    if (!ent.features.includes('cloud')) {
      const err = forbidden(
        'اشتراک این پمپ تمام شده است. دفترِ شما روی کامپیوترِ خودتان سالم می‌ماند.',
        'subscription_required'
      );
      err.entitlement = { source: ent.source, trial: ent.trial };
      throw err;
    }

    const text = JSON.stringify(data);
    if (text.length > 1_500_000) throw badRequest('این فایل بیش از حد بزرگ است', 'too_large');

    const t = now();
    const bumped = await one(
      `UPDATE station_rev SET last_rev = last_rev + 1 WHERE station_id=$1 RETURNING last_rev`,
      [req.stationId]
    );
    const rev = bumped ? Number(bumped.last_rev) : 1;
    const row = await one(
      `INSERT INTO station_files (station_id, path, data, rev, size, device_id, user_id, updated_at)
       VALUES ($1,$2,$3::jsonb,$4,$5,$6,'',$7)
       ON CONFLICT (station_id, path) DO UPDATE SET
         data=excluded.data, rev=excluded.rev, size=excluded.size,
         device_id=excluded.device_id, updated_at=excluded.updated_at
       RETURNING *`,
      [req.stationId, path, text, rev, text.length, req.stationDevice.id, t]
    );
    res.json({ path: row.path, rev: Number(row.rev), updatedAt: Number(row.updated_at), serverTime: t });
  } catch (err) { next(err); }
});

module.exports = router;
