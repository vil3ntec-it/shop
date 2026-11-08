'use strict';
/**
 * پرچمِ ادغامِ سه‌طرفهٔ دفترِ برنامه — `/api/shop/ledger-merge` (PR #407، ذخیرهٔ کهنه).
 *
 *   GET /config?v=2  برنامهٔ تازه ⇒ { enabled, v: 2 } — **پیش‌فرض روشن** (از ۱۴۰۵/۰۸/۱۸، `docs/DATA-SAFETY-fa.md`):
 *                    فقط `false` ِ صریحِ `live_config` (`shop.ledger_merge`، مقدارِ دکان بر `all` جلو) یا «غیرفعال
 *                    کردنِ سرویس» (`shop_holds`) خاموشش می‌کند؛ مقدارِ نادرست همان پیش‌فرض است.
 *   GET /config      برنامهٔ کهنه (بی `v`) ⇒ { enabled } — همان قاعدهٔ پیشین بیت‌به‌بیت (`orders.flagOf`: فقط `true`
 *                    روشن). برنامه‌های نصب‌شدهٔ پیش از این نسخه همان رفتارِ خودشان را نگه می‌دارند.
 *                    خاموش ⇒ برنامه همان رفتارِ پیشین را دارد (`LedgerMergeFlag` در android-native).
 *
 * سرور هیچ رفتارِ دیگری عوض نمی‌کند: نه همگام‌سازی، نه قالبِ داده. فقط پرچم را می‌گوید.
 * سقف: سطلِ IP پیش از احراز (CodeQL)؛ shop_id فقط از عضویتِ توکن (پمپ و کاربرِ بی دکان ⇒ `requireShop`).
 */
const express = require('express');
const config = require('../config');
const { many } = require('../db');
const { requireUser, requireShop } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { flagOf } = require('../lib/orders');

const FLAG_KEY = 'shop.ledger_merge';
/** نسخهٔ پرسشی که پیش‌فرضِ روشن را می‌فهمد (`LedgerMergeFlag.decide`) */
const DEFAULT_ON_V = 2;

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-ledger-merge-ip' }));
router.use(requireUser, requireShop);

const decode = (text) => { try { return JSON.parse(text); } catch { return null; } };

/** پیش‌فرض روشن: فقط `false` ِ صریح (دکان بر `all`) یا نگه‌داشتِ «غیرفعال کردنِ سرویس» خاموش است */
async function enabledByDefault(shopId) {
  const rows = await many(
    `SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)
      UNION ALL
     SELECT '#hold', 'false' FROM shop_holds
      WHERE shop_id=$3 AND kind='service_disabled' AND service=$1 AND lifted_at IS NULL`,
    [FLAG_KEY, 'all', String(shopId)],
  );
  if (rows.some((r) => r.scope === '#hold')) return false;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v !== false;
}

router.get('/config', async (req, res) => {
  const v = Number.parseInt(typeof req.query.v === 'string' ? req.query.v : '', 10);
  if (v >= DEFAULT_ON_V) return res.json({ enabled: await enabledByDefault(req.shopId), v: DEFAULT_ON_V });
  res.json({ enabled: await flagOf(req.shopId, FLAG_KEY) });
});

module.exports = router;
module.exports.FLAG_KEY = FLAG_KEY;
module.exports.enabledByDefault = enabledByDefault;
