'use strict';
/**
 * پرچمِ ادغامِ سه‌طرفهٔ دفترِ برنامه — `/api/shop/ledger-merge` (PR #407، ذخیرهٔ کهنه).
 *
 *   GET /config   هر عضو ⇒ { enabled } — پرچمِ `shop.ledger_merge` (live_config، پیش‌فرض خاموش؛ همان قاعدهٔ
 *                 `orders.flagOf`: مقدارِ دکان بر `all` جلو می‌افتد و فقط `true` روشن است).
 *                 خاموش ⇒ برنامه همان رفتارِ پیشین را دارد (`LedgerMergeFlag` در android-native).
 *
 * سرور هیچ رفتارِ دیگری عوض نمی‌کند: نه همگام‌سازی، نه قالبِ داده. فقط پرچم را می‌گوید.
 */
const express = require('express');
const { requireUser, requireShop } = require('../middleware/auth');
const { flagOf } = require('../lib/orders');

const FLAG_KEY = 'shop.ledger_merge';

const router = express.Router();
router.use(requireUser, requireShop);

router.get('/config', async (req, res) => {
  res.json({ enabled: await flagOf(req.shopId, FLAG_KEY) });
});

module.exports = router;
module.exports.FLAG_KEY = FLAG_KEY;
