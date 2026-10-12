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
const { one, now } = require('../db');
const v = require('../lib/validate');
const { requireUser, optionalShop } = require('../middleware/auth');
const { entitlementOf } = require('../lib/entitlement');
const { CORE_KEYS } = require('../lib/features');
const license = require('../lib/license');

const router = express.Router();

/** اشتراکی که سقف ندارد (آزمایشی) همان پیش‌فرضِ جدولِ اشتراک را می‌گیرد */
const DEFAULT_MAX_DEVICES = 10;
const DEVICE_WINDOW_MS = 90 * 24 * 60 * 60 * 1000;

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
     *  در ۹۰ روزِ گذشته دیده شده‌اند، جز همین دستگاه. دستگاهی که از قبل
     *  شمرده شده هرگز بیرون نمی‌ماند؛ برای جا باز کردن، صاحب دکان دستگاهِ
     *  کهنه را از «دستگاه‌های من» باطل می‌کند.
     */
    const maxDevices = Number(ent.subscription && ent.subscription.maxDevices) || DEFAULT_MAX_DEVICES;
    const seen = await one(
      `SELECT COUNT(DISTINCT d.device_uid)::int AS n,
              BOOL_OR(d.device_uid = $3) AS known
         FROM devices d
         JOIN shop_members m ON m.user_id = d.user_id AND m.shop_id = $1 AND m.status = 'active'
        WHERE d.status = 'active' AND COALESCE(d.last_seen_at, d.created_at) > $2`,
      [req.shopId, at - DEVICE_WINDOW_MS, deviceUid]
    );
    if (seen && !seen.known && Number(seen.n) >= maxDevices) {
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
