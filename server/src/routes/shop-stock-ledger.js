'use strict';
/**
 * موتورِ تازهٔ موجودی و بهای برنامهٔ دکان (#315) — `/api/shop/stock-ledger`.
 *
 *   GET /config   هر عضو ⇒ { enabled } — پرچمِ `shop.stock_v2` (live_config، پیش‌فرض خاموش).
 *                 تا روشن نشده برنامه تنظیمِ «موجودی و بها» را نشان نمی‌دهد و حتی با ردیفِ
 *                 `stock_mode=on` در دفتر همان موتورِ امروز را اجرا می‌کند (`StockLedger.isOn`).
 *
 *                 INV-10: `countAbsorb: true` فقط وقتی `shop.count_absorb` روشن است (خاموش ⇒ کلید نیست، پاسخ بیت‌به‌بیت
 *                 همان) — برنامه با آن فروشِ آفلاینِ پیش از شمارش را دوباره کم نمی‌کند (`CountAbsorbWiring`)، همان
 *                 قاعدهٔ دفترِ سرور (`lib/count-absorb-ledger.js`).
 *
 * ⛔ فقط خواندنِ پرچم؛ هیچ داده‌ای از دکان نمی‌دهد و چیزی نمی‌نویسد. موجودیِ منتشرشده همچنان
 * از `lib/catalog.js` (پرچمِ جدای `shop.catalog`) می‌آید.
 */
const express = require('express');
const { requireUser, requireShop } = require('../middleware/auth');

const FLAG_KEY = 'shop.stock_v2';

const router = express.Router();
router.use(requireUser, requireShop);

router.get('/config', async (req, res) => {
  const { flagOf } = require('../lib/orders');
  const out = { enabled: await flagOf(req.shopId, FLAG_KEY) };
  if (await flagOf(req.shopId, require('../lib/count-absorb-ledger').FLAG_KEY)) out.countAbsorb = true;
  res.json(out);
});

router.FLAG_KEY = FLAG_KEY;
module.exports = router;
