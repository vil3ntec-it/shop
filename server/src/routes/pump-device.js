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
const { requirePumpUser } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden, notFound, unauthorized } = require('../middleware/errors');

const router = express.Router();
const PUMP = catalogOf('pump');

/** توکنِ دستگاهی که همراهِ درخواست آمده — یا null. هیچ‌وقت خطا نمی‌دهد. */
async function deviceOfRequest(req) {
  const m = /^Bearer\s+(pd_\S+)$/i.exec(String(req.headers.authorization || '').trim());
  if (!m) return null;
  try { return await devices.bySecret(m[1]); } catch { return null; }
}

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
      /*
       *  ⛔ «این دستگاه از قبل جایی ثبت شده» فقط با **اثباتِ مالکیت**.
       *
       *  تا ۲.۹.۰ این‌جا پمپ را از روی خودِ `deviceUid` پیدا می‌کردیم — و
       *  این مسیر بی‌توکن است. `deviceUid` راز نیست (در هر مجوز هست،
       *  در پنل دیده می‌شود، از روی نامِ کامپیوتر ساخته می‌شود)، پس هر
       *  کسی با شناسهٔ کامپیوترِ یک پمپ و یک کدِ آزاد، به همان پمپ
       *  می‌رسید و `register` توکنِ دستگاهِ صاحبش را **جایگزین** می‌کرد:
       *  صاحب بیرون می‌افتاد و او با دسترسیِ کاملِ دستگاه جایش می‌نشست.
       *
       *  حالا تمدیدِ «همان پمپ» فقط با توکنِ همان دستگاه (`Authorization`)
       *  است — و برنامهٔ امروز تمدید را از `/device/redeem` می‌زند که
       *  همین توکن را دارد. بی توکن: پمپِ خودِ کد، یا پمپِ تازه.
       */
      const proof = await deviceOfRequest(req);
      let stationId = proof?.station_id || null;

      if (!stationId) {
        const clean = String(code).replace(/\D/g, '');
        const peek = await one(
          `SELECT station_id, status FROM ${require('../lib/tenancy').PUMP.vipTable}
            WHERE code_hash=$1`,
          [require('../lib/vip-codes').hashCode(clean)]
        );
        if (!peek) throw notFound('این کد معتبر نیست', 'bad_code');
        //  ⚠️ کدِ خرج‌شده یا باطل پمپِ یتیم نمی‌سازد — همان دو پاسخِ `redeem`
        if (peek.status === 'used') throw forbidden('این کد قبلاً استفاده شده است', 'code_used');
        if (peek.status !== 'active') throw forbidden('این کد دیگر کار نمی‌کند', 'code_inactive');
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
      //  ⚠️ سقفِ دستگاه **پیش از** خرج کردنِ کد: وگرنه کدی که پول داده شده
      //  خرج می‌شد و بعد `register` با `device_limit` می‌افتاد. خودِ
      //  `register` دوباره و زیرِ قفل می‌سنجد؛ این فقط کد را نگه می‌دارد.
      if (!created) await devices.assertRoom(stationId, deviceUid);

      const out = await vip.redeem(code, { userId: `device:${deviceUid}`, tenantId: stationId });

      const reg = await devices.register(stationId, {
        uid: deviceUid, name: deviceName, platform, ip: clientIp(req),
        maxDevices: await devices.deviceLimitOf(stationId),
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
   بند شدن از راهِ **حساب** — بی هیچ کدی
   ══════════════════════════════════════════════════════════════════ */

/**
 * ⛔ «هیچ کدِ اشتراکی در کار نیست.»
 *
 * جملهٔ صریحِ صاحب مخزن (ریپوی پمپ، `native/docs/PLANS-fa.md`): «من یادم
 * نمیاد که برای اشتراک کدی گفته باشم… اشتراک رو من به حسابِ یارو از
 * سرور می‌دم اینترنتی و تو برنامه تو حسابِ همون ثبت می‌شن.»
 *
 * تا امروز تنها راهِ بند شدنِ یک نصبِ تازه به پمپ، `/activate` بود — و آن
 * **حتماً** یک کدِ شش‌رقمی می‌خواهد و خرجش می‌کند. یعنی کسی که اشتراکش را
 * مدیر مستقیم روی حسابش گذاشته بود، باز هم بی کد نمی‌توانست برنامه را
 * راه بیندازد. این مسیر همان جای خالی است:
 *
 *     ورود با ایمیل/گوگل ⇒ توکنِ حساب ⇒ POST /pump/device/bind
 *                        ⇒ توکنِ دستگاه + همان مجوزِ امضاشدهٔ `activate`
 *
 * ⚠️ سه قیدِ امنیتیِ `activate` این‌جا هم هست و هیچ‌کدام نیفتاده:
 *   ۱) پمپ از روی **حسابِ توکن** پیدا می‌شود (`membershipOf`)، نه از روی
 *      چیزی که کلاینت می‌فرستد — وگرنه عوض کردنِ یک شناسه، دفترِ پمپِ
 *      دیگری را باز می‌کرد.
 *   ۲) مجوز با همان کلیدِ ES256 و همان `aud = tohid-pump-app` امضا
 *      می‌شود، پس مجوزِ بخشِ دکان روی این برنامه نمی‌نشیند.
 *   ۳) `duid` همان شناسهٔ دستگاه است، پس کپیِ پوشهٔ برنامه روی
 *      کامپیوترِ دیگر اشتراک را با خودش نمی‌برد.
 *
 * ⚠️ و این مسیر **اشتراک نمی‌سازد**. اگر حساب اشتراک نداشته باشد،
 * `entitlement.source` همان `free` است و مجوزی صادر نمی‌شود — دستگاه
 * بند می‌شود ولی قفل‌ها بسته می‌مانند. باز کردنشان کارِ پنلِ مدیریت است،
 * نه کارِ این درخواست.
 */
router.post(
  '/bind',
  rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'pump-device-bind' }),
  requirePumpUser,
  async (req, res, next) => {
    try {
      const deviceUid = v.text(req.body?.device?.uid ?? req.body?.deviceUid, {
        max: 120, required: true, field: 'شناسهٔ دستگاه',
      });
      const deviceName = v.text(req.body?.device?.name, { max: 80 });
      const platform = v.text(req.body?.device?.platform, { max: 40 });

      //  ⛔ پمپ از حسابِ توکن، هرگز از بدنهٔ درخواست
      const member = await stations.membershipOf(req.user.id);
      if (!member) throw notFound('برای این حساب پمپی ثبت نشده است', 'no_station');

      /*
       *  ⛔ فقط صاحب و مدیر. توکنِ دستگاه دسترسیِ کاملِ پمپ است (پوشهٔ
       *  ابری، چتِ مشتری‌ها، عوض کردنِ کدِ اپِ کارمندان) و تاریخِ انقضا
       *  ندارد؛ پس کارمندی که با حسابِ خودش یک کامپیوتر را بند می‌کرد، از
       *  دسترسیِ «فقط‌خواندنیِ» خودش بالاتر می‌رفت — و با اخراج هم
       *  نمی‌افتاد. حالا هم نقش سنجیده می‌شود و هم کسی که بند کرد ثبت
       *  می‌شود (`bound_by_user_id`) تا با رفتنش، دستگاهش هم برود.
       */
      if (!['owner', 'manager'].includes(member.role)) {
        throw forbidden('فقط صاحب یا مدیرِ پمپ می‌تواند این کامپیوتر را به پمپ بند کند', 'not_allowed');
      }

      const stationId = member.station_id;
      const reg = await devices.register(stationId, {
        uid: deviceUid, name: deviceName, platform, ip: clientIp(req),
        maxDevices: await devices.deviceLimitOf(stationId), boundBy: req.user.id,
      });

      const st = await stations.getStation(stationId);
      const ent = await entitlementOf(stationId);
      const issued = await signFor(st, ent, deviceUid, deviceName);

      await audit.log({
        userId: req.user.id, action: 'pump.device_bound',
        detail: { stationId, source: ent.source }, ip: clientIp(req),
      });

      res.status(201).json({
        ok: true,
        message: ent.source === 'free'
          ? 'دستگاه به پمپ بند شد. اشتراکِ این حساب هنوز فعال نیست.'
          : 'دستگاه به پمپ بند شد.',
        deviceToken: reg.token,          // فقط همین یک بار
        station: stations.shape(st),
        role: member.role,
        entitlement: ent,
        subscription: ent.subscription,
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
    activeUntil: ent.source === 'trial' ? endsAt : Number(ent.subscription.graceEndsAt || endsAt),
    plan: ent.subscription.plan || (ent.source === 'trial' ? 'trial' : ''),
    planTitle: ent.source === 'trial' ? 'دوره‌ی آزمایشی' : (ent.subscription.plan || ''),
    audience: license.AUDIENCE_PUMP,
    tenantId: station.id,
  });
  if (!issued) return { license: null, reason: 'expired', features: ent.features };
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
    //  کدِ پوشهٔ واقعیِ همین پمپ روی سرورِ خانگی — تا گوشی همان را بپرسد
    if (req.body?.station !== undefined) patch.homeStation = req.body.station;
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
      //  ⛔ کامپیوتر نقشِ «صاحب» نمی‌بخشد: صاحب همان کسی است که اولین بار
      //  می‌پیوندد (`redeemJoinCode`)، نه هر کسی که کدِ این دستگاه را دارد.
      role: ['manager', 'staff'].includes(String(req.body?.role || '')) ? String(req.body.role) : 'staff',
      hours: v.integer(req.body?.hours, { min: 1, max: 720, def: 24 }),
      maxUses: v.integer(req.body?.maxUses, { min: 1, max: 100, def: 10 }),
    });
    res.status(201).json({ ...out, serverTime: now() });
  } catch (err) { next(err); }
});

/**
 * ══ کدِ دسترسیِ پمپ — همان کدی که کارمند در اپِ گوشی می‌زند ══════════
 *
 * خواستهٔ صاحب مخزن: «هر کسی که برنامه را نصب می‌کند باید آن کد را
 * بزند تا بتواند بیاید توی حساب‌ها.» برنامهٔ کامپیوتر همین را نشان
 * می‌دهد و کارمند در اپ می‌زندش (‎POST /pump/public/join‎).
 *
 * دائمی است و حساب نمی‌خواهد — برخلافِ کدِ پیوستنِ شش‌رقمیِ بالا که
 * موقت است و به حسابِ گوگل نقش می‌دهد. «عوض کردن» کدِ قبلی را همان
 * لحظه بی‌اثر می‌کند؛ گوشی‌هایی که از قبل وصل شده‌اند نشانی و رمز را
 * دارند و تا رمزِ خواندنِ سرورِ خانگی عوض نشود، کار می‌کنند.
 */
const access = require('../lib/station-access');

router.get('/access-code', async (req, res, next) => {
  try {
    const code = await access.ensure(req.stationId);
    const st = await stations.getStation(req.stationId);
    res.json({ code, display: access.format(code), station: st.code, serverTime: now() });
  } catch (err) { next(err); }
});

router.post('/access-code/rotate', async (req, res, next) => {
  try {
    const code = await access.rotate(req.stationId);
    await audit.log({
      userId: `device:${req.stationDevice.device_uid}`, action: 'pump.access_code.rotated',
      detail: { stationId: req.stationId }, ip: clientIp(req),
    });
    res.status(201).json({ code, display: access.format(code), serverTime: now() });
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

/* ══════════════════════════════════════════════════════════════════
   چتِ پشتیبانی — از دیدِ صاحبِ پمپ (برنامهٔ کامپیوتر)
   ══════════════════════════════════════════════════════════════════ */
const chat = require('../lib/station-chat');
const relay = require('../lib/chat-relay');

function acctParam(req) {
  const a = chat.cleanAcct(req.params.acct);
  if (!a) throw badRequest('شناسهٔ حساب معتبر نیست', 'bad_acct');
  return a;
}

/** همهٔ گفت‌وگوها با نخوانده‌ها — همان چیزی که فهرستِ سمتِ چپ نشان می‌دهد. */
router.get('/chat/threads', async (req, res, next) => {
  try { res.json({ ok: true, threads: await chat.threads(req.stationId), relayDays: relay.relayDays(), serverTime: now() }); }
  catch (err) { next(err); }
});

/** پیام‌های تازهٔ همهٔ گفت‌وگوها بعد از ‎after‎ — برای حلقهٔ چندثانیه‌ای برنامه. */
router.get('/chat/inbox', async (req, res, next) => {
  try {
    const after = Number(req.query.after) || 0;
    const rows = await query(
      `SELECT * FROM station_chat_messages WHERE station_id=$1 AND seq > $2 ORDER BY seq ASC LIMIT 500`,
      [req.stationId, after]
    );
    res.json({ ok: true, messages: rows.rows.map(r => ({ ...chat.shape(r), acct: r.acct })), relayDays: relay.relayDays(), serverTime: now() });
  } catch (err) { next(err); }
});

router.get('/chat/media/:mid', async (req, res, next) => {
  try {
    const m = await chat.getMedia({ stationId: req.stationId, acct: null, id: String(req.params.mid || '') });
    if (!m) throw notFound(relay.GONE_MESSAGE, 'media_gone');
    res.set('Content-Type', m.mime);
    res.set('Content-Length', String(m.size));
    res.end(m.data);
  } catch (err) { next(err); }
});

router.get('/chat/:acct', async (req, res, next) => {
  try {
    const acct = acctParam(req);
    const th = await chat.thread(req.stationId, acct);
    res.json({
      ok: true,
      messages: await chat.list({ stationId: req.stationId, acct, afterSeq: req.query.after, limit: 300 }),
      blocked: !!(th && th.blocked_at), name: th ? th.name : '',
      relayDays: relay.relayDays(),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

router.post('/chat/:acct/media', express.raw({ type: () => true, limit: '26mb' }), async (req, res, next) => {
  try {
    const acct = acctParam(req);
    const out = await chat.putMedia({
      stationId: req.stationId, acct,
      mime: req.headers['content-type'] || '', buf: Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0),
    });
    res.status(201).json({ ok: true, ...out });
  } catch (err) { next(err); }
});

/** پیامِ صاحبِ پمپ — و همان لحظه پوش به مرورگرِ مشتری. */
router.post('/chat/:acct', async (req, res, next) => {
  try {
    const acct = acctParam(req);
    const b = req.body || {};
    const kind = String(b.kind || 'text');
    const msg = await chat.post({
      stationId: req.stationId, acct, from: 'o', name: b.name || 'پمپ',
      kind: ['text', 'image', 'video', 'audio'].includes(kind) ? kind : 'text',
      text: b.text, mediaId: b.mediaId || null,
    });
    const station = await stations.getStation(req.stationId);
    const preview = msg.kind === 'text' ? msg.text.slice(0, 120)
      : msg.kind === 'image' ? '📷 عکس' : msg.kind === 'video' ? '🎥 ویدیو' : '🎤 پیامِ صوتی';
    const pushed = await chat.pushTo(req.stationId, acct, {
      title: (station && station.name) || 'پمپ', body: preview, tag: 'chat-' + acct,
    });
    res.status(201).json({ ok: true, message: msg, pushed });
  } catch (err) { next(err); }
});

router.delete('/chat/message/:id', async (req, res, next) => {
  try {
    res.json({ ok: true, message: await chat.remove({ stationId: req.stationId, acct: null, id: String(req.params.id || ''), by: 'o' }) });
  } catch (err) { next(err); }
});

router.post('/chat/:acct/block', async (req, res, next) => {
  try { await chat.setBlocked(req.stationId, acctParam(req), true); res.json({ ok: true, blocked: true }); }
  catch (err) { next(err); }
});
router.delete('/chat/:acct/block', async (req, res, next) => {
  try { await chat.setBlocked(req.stationId, acctParam(req), false); res.json({ ok: true, blocked: false }); }
  catch (err) { next(err); }
});
router.post('/chat/:acct/seen', async (req, res, next) => {
  try { await chat.seen(req.stationId, acctParam(req), 'o', req.body?.seq); res.json({ ok: true }); }
  catch (err) { next(err); }
});

/*
 *  پشتیبانیِ کامپیوترِ پمپ با مدیرِ سامانه — `/api/pump/device/support`.
 *
 *  ⚠️ همان رشته‌ای که گوشیِ صاحبِ پمپ می‌بیند، چون کلیدش `station_id`
 *  است. یک پمپ، یک گفت‌وگو.
 */
router.use('/support', require('./pump-support').makeRouter((req) => ({
  stationId: req.stationId || '',
  deviceUid: req.stationDevice ? req.stationDevice.device_uid : '',
  who: req.stationDevice ? (req.stationDevice.name || 'کامپیوترِ پمپ') : '',
})));

/*
 *  پشتیبانِ همین پمپ — `/api/pump/device/backups`، با توکنِ **دستگاه**.
 *
 *  ⚠️ برنامهٔ کامپیوترِ پمپ حساب ندارد (قاعدهٔ `CLAUDE.md`): با کدِ
 *  شش‌رقمی فعال می‌شود و توکنِ دستگاه می‌گیرد. پس اگر پشتیبان فقط از
 *  درِ حساب می‌رفت، همان برنامه‌ای که پشتیبان را **می‌سازد** راهی
 *  برای فرستادنش نداشت.
 *
 *  `req.stationId` از `requireDevice` می‌آید — از ردیفِ خودِ توکن،
 *  نه از چیزی که فرستاده شده.
 */
router.use('/backups', require('./account-backups').makeRouter(
  'pump',
  (req) => req.stationId || '',
  { actor: (req) => ({ deviceId: req.stationDevice ? req.stationDevice.id : '' }) }
));

/*
 *  خبرهای همین پمپ — `/api/pump/device/events`، با توکنِ **دستگاه**.
 *
 *  خواستهٔ صاحب مخزن: «برنامه‌ها جوری باشند که بسته هم باشند، هر
 *  اتفاقی که در برنامه بیفتد به سرور برود و سرور وقتی برنامه‌ها بسته
 *  هم هستند پیام را برایشان بدهد.» برنامهٔ کامپیوترِ پمپ حساب ندارد،
 *  پس اگر خبر فقط از درِ حساب می‌رفت، همان برنامه‌ای که خبر را
 *  **می‌سازد** راهی برای فرستادنش نداشت.
 */
router.use('/events', require('./pump-events').makeRouter((req) => ({
  stationId: req.stationId || '',
  deviceUid: req.stationDevice ? req.stationDevice.device_uid : '',
  who: req.stationDevice ? (req.stationDevice.name || 'کامپیوترِ پمپ') : '',
})));

module.exports = router;
