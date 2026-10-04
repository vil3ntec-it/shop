'use strict';
/**
 * درِ خودِ نماینده — شورا، چ۳.
 *
 *   GET /api/rep/me   کدهای من، فروش‌های من، کمیسیونِ من
 *
 * ⛔ **هویت فقط از توکن**: نماینده‌ها از `req.user.id` پیدا می‌شوند و فقط
 *    نماینده‌ای که بخشش همان بخشِ نشست است (`req.appSection`) — همان قاعدهٔ
 *    «توکنِ یک بخش در بخشِ دیگر پیدا نمی‌شود». هیچ `repId`ی از درخواست
 *    خوانده نمی‌شود، پس `?rep=<دیگری>` بی‌اثر است.
 * ⛔ **فقط مشتری‌های خودش**: ردیف‌ها همان `discount_uses`ِ کدهای خودِ اوست؛
 *    نامِ مشتری همان نامِ دکان یا پمپ — نه ایمیل، نه شماره.
 * ⚠️ کسی که نماینده نیست ۴۰۳ِ `not_a_rep` می‌گیرد، نه فهرستِ خالی: «هیچ
 *    فروشی ندارید» با «شما نماینده نیستید» یکی نیست.
 */
const express = require('express');
const v = require('../lib/validate');
const reps = require('../lib/sales-reps');
const discounts = require('../lib/discounts');
const { requireAnyUser } = require('../middleware/auth');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireAnyUser);

router.get('/me', async (req, res, next) => {
  try {
    const mine = (await reps.forUser(req.user.id)).filter(r => r.app === (req.appSection || 'shop'));
    if (!mine.length) return next(forbidden('این حساب نمایندهٔ فروش نیست', 'not_a_rep'));
    const opts = { from: v.timestamp(req.query?.from, { def: 0 }), to: v.timestamp(req.query?.to, { def: 0 }) };
    const sales = await reps.sales(mine.map(r => r.id), opts);
    const codes = [];
    for (const r of mine) codes.push(...await discounts.listCodes({ app: r.app, repId: r.id }));
    res.json({
      reps: mine.map(r => ({ id: r.id, app: r.app, name: r.name, commissionPct: r.commissionPct })),
      codes: codes.map(c => ({ code: c.code, app: c.app, plan: c.plan, kind: c.kind, value: c.value,
                               currency: c.currency, status: c.status, uses: c.uses, expiresAt: c.expiresAt })),
      totals: reps.totals(sales),
      sales,
    });
  } catch (err) { next(err); }
});

module.exports = router;
