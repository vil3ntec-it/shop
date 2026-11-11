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
 *                 PRD-08: `qtyExact: true` فقط وقتی `shop.qty_exact` روشن است (خاموش ⇒ کلید نیست، پاسخ بیت‌به‌بیت همان) —
 *                 صندوقِ برنامه با آن مبلغِ ردیفِ مقدارِ اعشاری (کالای وزنی) را با قاعدهٔ ۳.۴ می‌گیرد (`QtyExactWiring`)،
 *                 همان `order-states.lineTotalMinor` ِ سفارشِ آنلاین. سرور هیچ مبلغی را این‌جا حساب نمی‌کند.
 *
 *                 POS-37: `saleTax: true` فقط وقتی `shop.sale_tax` روشن است (خاموش ⇒ کلید نیست، پاسخ بیت‌به‌بیت همان) —
 *                 صندوقِ برنامه فقط با آن تنظیمِ «مالیاتِ فاکتور» ِ دکان را به کار می‌برد (`SaleTaxWiring`).
 *
 * ⛔ فقط خواندنِ پرچم؛ هیچ داده‌ای از دکان نمی‌دهد و چیزی نمی‌نویسد. موجودیِ منتشرشده همچنان
 * از `lib/catalog.js` (پرچمِ جدای `shop.catalog`) می‌آید.
 */
const express = require('express');
const { requireUser, requireShop } = require('../middleware/auth');

const FLAG_KEY = 'shop.stock_v2';
const QTY_FLAG = 'shop.qty_exact';
const TAX_FLAG = 'shop.sale_tax';

const router = express.Router();
router.use(requireUser, requireShop);

router.get('/config', async (req, res) => {
  const { flagOf } = require('../lib/orders');
  const out = { enabled: await flagOf(req.shopId, FLAG_KEY) };
  if (await flagOf(req.shopId, require('../lib/count-absorb-ledger').FLAG_KEY)) out.countAbsorb = true;
  if (await flagOf(req.shopId, QTY_FLAG)) out.qtyExact = true;
  if (await flagOf(req.shopId, TAX_FLAG)) out.saleTax = true;
  res.json(out);
});

router.FLAG_KEY = FLAG_KEY;
router.QTY_FLAG = QTY_FLAG;
router.TAX_FLAG = TAX_FLAG;
module.exports = router;
