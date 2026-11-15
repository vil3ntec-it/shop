'use strict';
/**
 * POS-16/S17 — `GET /api/shop/catalog/products/:id/other-branches` (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰؛ `lib/other-branches.js`):
 * عددِ موجودیِ آزادِ همین کالا در شعبه‌های دیگرِ همین دکان، برای صندوق و قیمت‌پرسی. درونِ روترِ کاتالوگ سوار است
 * (`requireUser`/`requireShop`/`stock.view` و شعبهٔ سنجیدهٔ `req.stockBranch` از سرآیندِ `X-Branch-Id`).
 *
 * ⛔ فقط‌خواندنی؛ پاسخ دقیقاً `{ branches: [{ branchId, branchName, qty }] }`. کدام شعبه‌ها فقط از دسترسی و مجوزِ سرور
 *    (`stock.view_other_branches`، صاحب همیشه) — هیچ شناسهٔ شعبه‌ای از بدنه/پارامتر.
 * ⛔ `shop.branches` یا `shop.catalog` خاموش ⇒ `next('router')` ⇒ همان درِ ناشناخته. سقفِ نرخِ هر کاربرِ هر دکان.
 */
const express = require('express');
const other = require('../lib/other-branches');
const bt = require('../lib/branch-transfers');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
const on = async (req, res, next) => ((await bt.enabled(req.shopId)) ? next() : next('router'));
const peekLimit = rateLimit({
  max: 60, windowMs: 60 * 1000, keyPrefix: 'branch-stock-peek', key: (req) => `${req.shopId || 'x'}:${req.user?.id || ''}`,
});

router.get('/products/:id/other-branches', on, peekLimit, async (req, res) => {
  res.set('Cache-Control', 'private, no-store');
  res.json({ branches: await other.stockElsewhere(req.shopId, req.stockBranch, req.member, req.params.id) });
});

module.exports = router;
