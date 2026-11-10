'use strict';
/**
 * LOC-04 — جدولِ تعطیلات، سمتِ مدیر (`/api/admin/calendar/holidays`).
 *
 * خواندن برای هر مدیر؛ نوشتن فقط مدیرِ ارشد **با دلیل** (۳ تا ۲۰۰ نویسه، `requireReason`)، چون روزِ کاریِ همهٔ
 * دکان‌ها را عوض می‌کند (همان قاعدهٔ «ارشد + دلیل» ِ آستانه‌ها و پرچم‌ها). هر نوشتن در همان تراکنش سابقه دارد؛
 * تکرارِ همان جدول یک بار است (`unchanged`). منطق و سنجش در `lib/holidays.js`؛ «توانایی» ِ پل: `holidays.v1`.
 *
 * ⛔ سقفِ IP پیش از احراز (`admin-calendar-ip`، ۶۰ در دقیقه) در `routes/admin.js` است، پیش از `requireAdmin` ِ آن روتر که
 *    هر `/api/admin/*` اول از آن می‌گذرد — همان شیوهٔ `/admin/contract`.
 */
const express = require('express');
const holidays = require('../lib/holidays');
const { requireReason } = require('../lib/admin-access');
const { clientIp } = require('../middleware/ratelimit');
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
    const reason = requireReason(req);
    const doc = await holidays.replace(req.body?.holidays, req.body?.version, { userId: req.admin.id, reason, ip: clientIp(req) });
    res.json(doc);
  } catch (err) { next(err); }
});

module.exports = router;
