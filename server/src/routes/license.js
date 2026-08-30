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
const { now } = require('../db');
const v = require('../lib/validate');
const { requireUser, optionalShop } = require('../middleware/auth');
const { entitlementOf } = require('../lib/entitlement');
const { CORE_KEYS } = require('../lib/features');
const license = require('../lib/license');

const router = express.Router();

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
      plan: ent.subscription.plan || (ent.source === 'trial' ? 'trial' : ''),
      planTitle: ent.source === 'trial' ? 'دوره‌ی آزمایشی' : (ent.subscription.plan || ''),
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

module.exports = router;
