'use strict';
/**
 * پرچمِ ادغامِ سه‌طرفهٔ دفترِ برنامه — `/api/shop/ledger-merge` (PR #407، ذخیرهٔ کهنه).
 *
 *   GET /config?v=2  برنامهٔ تازه ⇒ { enabled, v: 2 } — **پیش‌فرض روشن** (از ۱۴۰۵/۰۸/۱۸، `docs/DATA-SAFETY-fa.md`):
 *                    فقط `false` ِ صریحِ `live_config` (`shop.ledger_merge`، مقدارِ دکان بر `all` جلو) یا «غیرفعال
 *                    کردنِ سرویس» (`shop_holds`) خاموشش می‌کند؛ مقدارِ نادرست همان پیش‌فرض است.
 *   GET /config?v=3  همان پاسخِ `v=2` و `base` — پرچمِ `shop.save_base` (پایهٔ صریحِ ذخیره، `ShopStore.save(base, next)`،
 *                    `docs/DATA-SAFETY-fa.md` «دو ذخیره از یک صفحهٔ کهنه»)، **پیش‌فرض روشن**، فقط `false` ِ صریح یا
 *                    نگه‌داشتِ سرویس خاموشش می‌کند: خاموش‌کنِ اضطراری که ذخیره را بیت‌به‌بیت به رفتارِ پیشین برمی‌گرداند.
 *                    پاسخِ `v=2` و بی `v` بایت‌به‌بایت همان پیشین است (برنامه‌های نصب‌شده چیزی نمی‌بینند).
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
/** پایهٔ صریحِ ذخیره — خاموش‌کنِ اضطراری، پیش‌فرض روشن (`LedgerMergeFlag.base`) */
const BASE_KEY = 'shop.save_base';
/** نسخهٔ پرسشی که پیش‌فرضِ روشن را می‌فهمد (`LedgerMergeFlag.decide`) */
const DEFAULT_ON_V = 2;
/** نسخهٔ پرسشی که `base` را می‌خواهد */
const BASE_V = 3;

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-ledger-merge-ip' }));
router.use(requireUser, requireShop);

/** پیش‌فرض روشن: فقط `false` ِ صریح (دکان بر `all`) یا نگه‌داشتِ «غیرفعال کردنِ سرویس» خاموش است */
const enabledByDefault = (shopId) => defaultOn(shopId, FLAG_KEY);

router.get('/config', async (req, res) => {
  const v = Number.parseInt(typeof req.query.v === 'string' ? req.query.v : '', 10);
  if (v >= BASE_V) {
    const [enabled, base] = await Promise.all([enabledByDefault(req.shopId), defaultOn(req.shopId, BASE_KEY)]);
    return res.json({ enabled, v: DEFAULT_ON_V, base });
  }
  if (v >= DEFAULT_ON_V) return res.json({ enabled: await enabledByDefault(req.shopId), v: DEFAULT_ON_V });
  res.json({ enabled: await flagOf(req.shopId, FLAG_KEY) });
});

module.exports = router;
module.exports.FLAG_KEY = FLAG_KEY;
module.exports.BASE_KEY = BASE_KEY;
module.exports.enabledByDefault = enabledByDefault;
