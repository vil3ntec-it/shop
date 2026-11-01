'use strict';

/**
 *  مجوز اشتراک — دو راه، همان دو راهی که برنامه صدا می‌زند.
 *
 *  `GET /license/public-key` کلید عمومی سرور را می‌دهد. باز است، چون
 *  کلید عمومی راز نیست؛ با آن فقط می‌شود امضا را **سنجید**، نه ساخت.
 *
 *  `POST /license/sync` مجوز این دستگاه را می‌دهد. حق دسترسی از همان
 *  `entitlementOf` می‌آید که صفحه‌ی اشتراک هم از آن می‌خواند، پس یک
 *  حقیقت بیشتر وجود ندارد: اگر اشتراک تمام شده باشد، مجوز تازه صادر
 *  نمی‌شود و مجوز قبلی هم چند روز دیگر خودش منقضی می‌شود.
 */

const express = require('express');
const { now, newId, tx } = require('../db');
const v = require('../lib/validate');
const { requireUser, optionalShop } = require('../middleware/auth');
const { entitlementOf } = require('../lib/entitlement');
const { CORE_KEYS } = require('../lib/features');
const license = require('../lib/license');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();

/** اشتراکی که سقف ندارد (آزمایشی) همان پیش‌فرضِ جدولِ اشتراک را می‌گیرد */
const DEFAULT_MAX_DEVICES = 10;
const DEVICE_WINDOW_MS = 90 * 24 * 60 * 60 * 1000;
/** پرچمِ `live_config` (دکان / `all`، پیش‌فرض خاموش): دستگاهِ ثبت‌شده‌ای که بیرون از n ِ اول است هم رد شود */
const STRICT_CAP_FLAG = 'shop.device_cap';

/**
 *  جای این دستگاه در سقفِ دکان — SEC-CLI-01 (SEC-SUB-03/04).
 *
 *  ⛔ شناسهٔ بدنه از برنامه می‌آید و ساختگی‌پذیر است؛ پس فقط «برچسبِ یک جا» است و هر شناسهٔ تازه
 *  **یک جا می‌گیرد**. تا ۲.۱۴.۵ فقط شمرده می‌شد و نشانده نه: یک توکن با شناسه‌های پیاپی (یا توکنِ
 *  کپی‌شده روی گوشی‌های دیگر) همیشه «n < سقف» می‌دید و بی‌شمار مجوز می‌گرفت، و شناسهٔ باطل‌شده
 *  (`revoked`) چون دیگر شمرده نمی‌شد دوباره مجوز می‌گرفت.
 *
 *  همه زیرِ قفلِ همان دکان (`shop-devices:<دکان>`) و در یک تراکنش — درخواست‌های هم‌زمان از سقف رد
 *  نمی‌شوند. ترتیبِ جاها = نخستین ثبت (`created_at`)؛ دستگاهِ ۹۰ روز ندیده شمرده نمی‌شود.
 *  پرچمِ خاموش ⇒ دستگاهی که از قبل ثبت شده (ورود) مثلِ پیش مجوز می‌گیرد، حتی بیرون از سقف.
 */
async function claimSlot({ shopId, userId, deviceUid, deviceName, maxDevices, strict, at, ip }) {
  return tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`shop-devices:${shopId}`]);
    const mine = (await c.query(
      'SELECT status FROM devices WHERE user_id=$1 AND device_uid=$2', [userId, deviceUid])).rows[0];
    if (mine && mine.status !== 'active') return { ok: false, reason: 'device_revoked' };

    const slots = (await c.query(
      `SELECT d.device_uid
         FROM devices d
         JOIN shop_members m ON m.user_id = d.user_id AND m.shop_id = $1 AND m.status = 'active'
        WHERE d.status = 'active' AND COALESCE(d.last_seen_at, d.created_at) > $2
        GROUP BY d.device_uid
        ORDER BY MIN(d.created_at) ASC, d.device_uid ASC`,
      [shopId, at - DEVICE_WINDOW_MS]
    )).rows.map((r) => r.device_uid);

    const rank = slots.indexOf(deviceUid);
    if (rank >= 0) return (!strict || rank < maxDevices) ? { ok: true } : { ok: false, reason: 'device_limit' };
    if (slots.length >= maxDevices) return { ok: false, reason: 'device_limit' };

    //  جای خالی ⇒ ثبت برای همین حساب (در «دستگاه‌های من» دیده و باطل‌شدنی است)
    await c.query(
      `INSERT INTO devices (id, user_id, device_uid, name, platform, status, created_at, last_seen_at, last_ip)
       VALUES ($1,$2,$3,$4,'license','active',$5,$5,$6)
       ON CONFLICT (user_id, device_uid) DO UPDATE SET last_seen_at = excluded.last_seen_at
       WHERE devices.status = 'active'`,
      [newId('dev'), userId, deviceUid, String(deviceName || '').slice(0, 80), at, String(ip || '').slice(0, 60)]
    );
    return { ok: true, registered: true };
  });
}

/** کلید عمومی — بدون ورود، چون برنامه پیش از ورود هم باید بتواند بسنجد */
router.get('/public-key', async (req, res, next) => {
  try {
    res.json({ publicKey: await license.publicKey(), serverTime: now() });
  } catch (err) { next(err); }
});

router.use(requireUser, optionalShop);

/**
 *  مجوز تازه برای این دستگاه.
 *
 *  `deviceUid` از خود برنامه می‌آید و در مجوز نوشته می‌شود؛ مجوزی که روی
 *  گوشی دیگری کپی شود، آنجا رد می‌شود.
 */
router.post('/sync', async (req, res, next) => {
  try {
    // برنامه شناسه را تودرتو می‌فرستد (`device.uid`)؛ شکل ساده هم
    // پذیرفته می‌شود تا ابزارهای دیگر و آزمون‌ها هم بتوانند صدا بزنند
    const raw = req.body?.device?.uid ?? req.body?.deviceUid;
    const deviceUid = v.text(raw, {
      max: 120, required: true, field: 'شناسه‌ی دستگاه',
    });
    const deviceName = v.text(req.body?.device?.name ?? req.body?.deviceName, { max: 120 });

    if (!req.shopId) {
      return res.json({
        license: null,
        reason: 'no_shop',
        features: [...CORE_KEYS],
        serverTime: now(),
      });
    }

    const ent = await entitlementOf(req.shopId);
    const at = now();

    // بدون اشتراک و بدون آزمایش، مجوزی صادر نمی‌شود. برنامه همان
    // قابلیت‌های همیشه‌باز را دارد و این را از نبودِ مجوز می‌فهمد.
    if (ent.source === 'free') {
      return res.json({
        license: null,
        reason: 'no_subscription',
        source: ent.source,
        features: ent.features,
        serverTime: at,
      });
    }

    /*
     *  سقفِ دستگاه — `max_devices`ِ اشتراک تا امروز ذخیره می‌شد ولی برای
     *  دکان هیچ‌جا سنجیده نمی‌شد؛ یک حسابِ پولی روی بی‌شمار گوشی مجوز
     *  می‌گرفت. شمرده می‌شوند: دستگاه‌های فعالِ اعضای فعالِ همین دکان که
     *  در ۹۰ روزِ گذشته دیده شده‌اند. برای جا باز کردن، صاحب دکان دستگاهِ
     *  کهنه را از «دستگاه‌های من» باطل می‌کند. شرحِ جا و قفل: `claimSlot`.
     */
    const maxDevices = Number(ent.subscription && ent.subscription.maxDevices) || DEFAULT_MAX_DEVICES;
    const strict = await require('../lib/orders').flagOf(req.shopId, STRICT_CAP_FLAG);
    const slot = await claimSlot({
      shopId: req.shopId, userId: req.user.id, deviceUid, deviceName, maxDevices, strict, at, ip: clientIp(req),
    });
    if (!slot.ok && slot.reason === 'device_revoked') {
      return res.json({
        license: null,
        reason: 'device_revoked',
        message: 'این دستگاه از «دستگاه‌های من» باطل شده است و مجوز نمی‌گیرد.',
        source: ent.source,
        features: ent.features,
        serverTime: at,
      });
    }
    if (!slot.ok) {
      return res.json({
        license: null,
        reason: 'device_limit',
        message: `اشتراکِ این دکان برای ${maxDevices} دستگاه است و همه پر شده‌اند. ` +
          'یک دستگاهِ قدیمی را از «دستگاه‌های من» باطل کنید یا اشتراک را بزرگ‌تر کنید.',
        maxDevices,
        source: ent.source,
        features: ent.features,
        serverTime: at,
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
      core: [...CORE_KEYS],
      subscriptionEndsAt: endsAt,
      activeUntil: ent.source === 'trial' ? endsAt : Number(ent.subscription.graceEndsAt || endsAt),
      plan: ent.subscription.plan || (ent.source === 'trial' ? 'trial' : ''),
      planTitle: ent.source === 'trial' ? 'دوره‌ی آزمایشی' : (ent.subscription.plan || ''),
      at,
    });
    if (!issued) {
      return res.json({ license: null, reason: 'expired', source: ent.source, features: ent.features, serverTime: at });
    }

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

module.exports = router;
