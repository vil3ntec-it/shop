'use strict';
/**
 * کدِ اشتراکِ آفلاینِ فروشگاه — سمتِ مدیر (lib/shop-offline-codes.js).
 * زیرِ ‎/api/admin/shop-offline-codes‎؛ همان سه درِ پمپ: فهرست، صدور، باطل.
 * ⛔ خودِ کد فقط همین یک بار در پاسخِ صدور می‌آید و در ممیزی نمی‌نشیند.
 */
const express = require('express');
const v = require('../lib/validate');
const offline = require('../lib/shop-offline-codes');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');

const router = express.Router();

router.get('/', async (req, res, next) => {
  try {
    res.json({ codes: await offline.list({ limit: v.integer(req.query?.limit, { min: 1, max: 500, def: 100 }) }) });
  } catch (err) { next(err); }
});

router.post('/', requireSuperAdmin, async (req, res, next) => {
  try {
    const out = await offline.issue({
      plan: v.text(req.body?.plan, { max: 10 }),
      computer: v.text(req.body?.computer, { max: 40 }),
      account: v.text(req.body?.account, { max: 200 }),
      days: req.body?.days === undefined || req.body?.days === null || req.body?.days === ''
        ? null : v.integer(req.body.days, { field: 'روزها', min: 1, max: 3650 }),
      note: v.text(req.body?.note, { max: 300 }),
      createdBy: req.admin.id,
    });
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.shop_offline_code_issued',
      targetType: 'offline_code', targetId: out.offline.id,
      detail: { plan: out.offline.plan, computer: out.offline.computer, account: out.offline.accountEmail, endsAt: out.offline.endsAt },
    });
    res.status(201).json(out);
  } catch (err) { next(err); }
});

router.post('/:id/revoke', async (req, res, next) => {
  try {
    const row = await offline.revoke(v.id(req.params.id));
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.shop_offline_code_revoked',
      targetType: 'offline_code', targetId: row.id,
    });
    res.json({ offline: row });
  } catch (err) { next(err); }
});

module.exports = router;
