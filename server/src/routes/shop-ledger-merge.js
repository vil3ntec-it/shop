'use strict';
/**
 * پرچمِ ادغامِ سه‌طرفهٔ دفترِ برنامه — `/api/shop/ledger-merge` (PR #407، ذخیرهٔ کهنه).
 *
 *   GET /config?v=2  برنامهٔ تازه ⇒ { enabled, v: 2 } — **پیش‌فرض روشن** (از ۱۴۰۵/۰۸/۱۸، `docs/DATA-SAFETY-fa.md`):
 *                    فقط `false` ِ صریحِ `live_config` (`shop.ledger_merge`، مقدارِ دکان بر `all` جلو) یا «غیرفعال
 *                    کردنِ سرویس» (`shop_holds`) خاموشش می‌کند؛ مقدارِ نادرست همان پیش‌فرض است.
 *   GET /config?v=3  همان `v=2` و `shadowFile`: پرچمِ `shop.sync_shadow_file` (**پیش‌فرض خاموش**، `orders.flagOf`: فقط
 *                    `true` روشن) — سایه و cursor ِ Sync v1 ِ برنامه در فایلِ خودشان (`ShadowStore`، PERF-LEDGER).
 *                    سرورِ کهنه `v=3` را `v=2` می‌بیند و کلید ندارد ⇒ برنامه خاموش می‌خواند.
 *   GET /config      برنامهٔ کهنه (بی `v`) ⇒ { enabled } — همان قاعدهٔ پیشین بیت‌به‌بیت (`orders.flagOf`: فقط `true`
 *                    روشن). برنامه‌های نصب‌شدهٔ پیش از این نسخه همان رفتارِ خودشان را نگه می‌دارند.
 *                    خاموش ⇒ برنامه همان رفتارِ پیشین را دارد (`LedgerMergeFlag` در android-native).
 *
 * سرور هیچ رفتارِ دیگری عوض نمی‌کند: نه همگام‌سازی، نه قالبِ داده. فقط پرچم را می‌گوید.
 * سقف: سطلِ IP پیش از احراز (CodeQL)؛ shop_id فقط از عضویتِ توکن (پمپ و کاربرِ بی دکان ⇒ `requireShop`).
 */
const express = require('express');
const config = require('../config');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { flagOf } = require('../lib/orders');
const { defaultOn } = require('../lib/default-on-flag');

const FLAG_KEY = 'shop.ledger_merge';
/** سایهٔ همگام‌سازیِ برنامه در فایلِ خودش (`docs/PERF-LEDGER-fa.md`) — پیش‌فرض خاموش */
const SHADOW_KEY = 'shop.sync_shadow_file';
/** نسخهٔ پرسشی که `shadowFile` را هم می‌خواهد */
const SHADOW_V = 3;
/** نسخهٔ پرسشی که پیش‌فرضِ روشن را می‌فهمد (`LedgerMergeFlag.decide`) */
const DEFAULT_ON_V = 2;

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-ledger-merge-ip' }));
router.use(requireUser, requireShop);

/** پیش‌فرض روشن: فقط `false` ِ صریح (دکان بر `all`) یا نگه‌داشتِ «غیرفعال کردنِ سرویس» خاموش است */
const enabledByDefault = (shopId) => defaultOn(shopId, FLAG_KEY);

router.get('/config', async (req, res) => {
  const v = Number.parseInt(typeof req.query.v === 'string' ? req.query.v : '', 10);
  if (v >= SHADOW_V) {
    const [enabled, shadowFile] = await Promise.all([enabledByDefault(req.shopId), flagOf(req.shopId, SHADOW_KEY)]);
    return res.json({ enabled, v: SHADOW_V, shadowFile });
  }
  if (v >= DEFAULT_ON_V) return res.json({ enabled: await enabledByDefault(req.shopId), v: DEFAULT_ON_V });
  res.json({ enabled: await flagOf(req.shopId, FLAG_KEY) });
});

module.exports = router;
module.exports.FLAG_KEY = FLAG_KEY;
module.exports.SHADOW_KEY = SHADOW_KEY;
module.exports.enabledByDefault = enabledByDefault;
