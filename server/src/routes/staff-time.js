'use strict';
/**
 * ورود و خروجِ کارمند و نرخ‌های پورسانت — `/api/shop/staff-time` (STF-07، STF-08).
 *
 *   GET  /config            همیشه باز: { enabled, memberId, role }
 *   POST /punches           زدنِ ورود یا خروج (خودِ عضو؛ برای عضوِ دیگر فقط صاحب و مدیر)
 *   GET  /punches           زدن‌های یک بازه (شاگرد فقط خودش)
 *   GET  /commission-plan   نرخ‌های پورسانت (صاحب و مدیر)
 *   PUT  /commission-plan   نوشتنِ نرخ‌ها با نسخهٔ فعلی (صاحب و مدیر)
 *
 * ⛔ پیش‌فرض خاموش: بی `shop.staff_time` همه جز `/config` ⇒ ۴۰۳ `staff_time_disabled`.
 * ⛔ shop_id و عضو فقط از عضویتِ کاربرِ توکن. منطق در `lib/staff-time.js`.
 */
const express = require('express');
const st = require('../lib/staff-time');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('data.read'));

const actorOf = (req) => ({ id: req.member.id, role: req.role });
const deviceOf = (req) => (req.device ? String(req.device.device_uid || req.device.id || '') : '');

//  Express 5 خودش promiseِ ردشده را به next(err) می‌دهد؛ پوشاننده لازم نیست
async function needFlag(req, res, next) {
  if (!(await st.flagOf(req.shopId))) return next(forbidden('ورود و خروجِ کارمند برای این دکان روشن نیست', 'staff_time_disabled'));
  next();
}

router.get('/config', async (req, res) => {
  res.json({ enabled: await st.flagOf(req.shopId), memberId: req.member.id, role: req.role });
});

router.post('/punches', needFlag, async (req, res) => {
  const out = await st.punch(req.shopId, actorOf(req), { ...req.body, deviceUid: req.body?.deviceUid || deviceOf(req) });
  if (!out.duplicate) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: `staff_time.punch_${out.punch.kind}`,
      targetType: 'member', targetId: out.punch.memberId, detail: { at: out.punch.at, by: out.punch.byMemberId },
    });
  }
  res.status(out.duplicate ? 200 : 201).json(out);
});

router.get('/punches', needFlag, async (req, res) => {
  res.json(await st.list(req.shopId, actorOf(req), { from: req.query.from, to: req.query.to, memberId: req.query.member }));
});

router.get('/commission-plan', needFlag, requirePermission('members.view'), async (req, res) => {
  res.json(await st.plan(req.shopId));
});

router.put('/commission-plan', needFlag, requirePermission('settings.write'), async (req, res) => {
  const p = await st.savePlan(req.shopId, actorOf(req), req.body || {});
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'staff_time.commission_plan', detail: { version: p.version } });
  res.json(p);
});

module.exports = router;
