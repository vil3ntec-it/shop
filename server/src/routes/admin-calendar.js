'use strict';
/**
 * LOC-04 — جدولِ تعطیلات، سمتِ مدیر (`/api/admin/calendar/holidays`).
 *
 * خواندن برای هر مدیر؛ نوشتن فقط مدیرِ ارشد، چون روزِ کاریِ همهٔ دکان‌ها را
 * عوض می‌کند (همان قاعدهٔ `PATCH /api/admin/config`). هر نوشتن در audit می‌نشیند.
 * منطق و سنجش در `lib/holidays.js` است.
 */
const express = require('express');
const holidays = require('../lib/holidays');
const audit = require('../lib/audit');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAdmin);

router.get('/holidays', async (req, res, next) => {
  try {
    res.json(await holidays.get());
  } catch (err) { next(err); }
});

router.put('/holidays', requireSuperAdmin, async (req, res, next) => {
  try {
    const doc = await holidays.replace(req.body?.holidays, req.body?.version);
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.holidays_updated',
      targetType: 'app_config', targetId: holidays.KEY, detail: { version: doc.version, count: doc.holidays.length },
    });
    res.json(doc);
  } catch (err) { next(err); }
});

module.exports = router;
