'use strict';
/**
 * انتقالِ موجودی میانِ شعبه‌ها — `/api/shop/catalog/branch-transfers` (INV-11/S21، فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰؛
 * `lib/branch-transfers.js`). درونِ روترِ کاتالوگ سوار است، پس `requireUser`/`requireShop`/`stock.view` و شعبهٔ سنجیده
 * (`req.stockBranch` از سرآیندِ `X-Branch-Id` با `branches.restOf`؛ نامجاز ⇒ ۴۰۳ پیش از این‌جا) را از همان دارد.
 *
 * ⛔ پرچم‌های `shop.branches` و `shop.catalog` هر دو لازم‌اند؛ هر کدام خاموش ⇒ `next('router')` ⇒ دقیقاً همان پاسخِ
 *    درِ ناشناخته (۴۰۴ ِ امروز)، با و بی سرآیند.
 * ⛔ نوشتن‌ها `stock.move` و کلیدِ یکتایی (`Idempotency-Key`) می‌خواهند؛ سقفِ نرخِ هر کاربرِ هر دکان؛ محدودهٔ مکانِ STF-06
 *    (ارسال از مبدأِ محدوده، دریافت/رد در مقصدِ محدوده). دکان و شعبه فقط از توکن و سرآیندِ سنجیده، هرگز از بدنه/پارامتر.
 */
const express = require('express');
const bt = require('../lib/branch-transfers');
const scope = require('../lib/staff-scope');
const { requirePermission } = require('../middleware/auth');
const { clientIp, rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
const on = async (req, res, next) => ((await bt.enabled(req.shopId)) ? next() : next('router'));
const who = (req) => `${req.shopId || 'x'}:${req.user?.id || ''}`;
const readLimit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'branch-xfer-read', key: who });
const writeLimit = rateLimit({ max: 30, windowMs: 60 * 1000, keyPrefix: 'branch-xfer-write', key: who });
const move = requirePermission('stock.move');

const opts = async (req) => ({
  key: req.get('idempotency-key') || req.body?.clientKey, body: req.body, actor: { id: req.user.id, role: req.role },
  places: await scope.scopeOfReq(req), ip: clientIp(req),
});

function done(res, out, created) {
  if (out.replayed) {
    res.set('Idempotent-Replayed', 'true');
    return res.status(200).json(out);
  }
  return res.status(created ? 201 : 200).json(out);
}

router.get('/branch-transfers/destinations', on, readLimit, move, async (req, res) => {
  res.json({ branch: req.stockBranch, destinations: await bt.destinations(req.shopId, req.stockBranch) });
});

router.get('/branch-transfers', on, readLimit, async (req, res) => {
  res.json({
    branch: req.stockBranch,
    transfers: await bt.list(req.shopId, req.stockBranch, {
      direction: req.query.direction, status: req.query.status, limit: req.query.limit, places: await scope.scopeOfReq(req),
    }),
  });
});

router.get('/branch-transfers/:id', on, readLimit, async (req, res) => {
  res.json(await bt.get(req.shopId, req.stockBranch, req.params.id));
});

router.post('/branch-transfers', on, writeLimit, move, async (req, res) => {
  done(res, await bt.send(req.shopId, req.stockBranch, await opts(req)), true);
});

router.post('/branch-transfers/:id/receive', on, writeLimit, move, async (req, res) => {
  done(res, await bt.receive(req.shopId, req.stockBranch, req.params.id, await opts(req)), false);
});

router.post('/branch-transfers/:id/reject', on, writeLimit, move, async (req, res) => {
  done(res, await bt.reject(req.shopId, req.stockBranch, req.params.id, await opts(req)), false);
});

module.exports = router;
