'use strict';
/**
 * محدودهٔ مکانِ کارمندان — `/api/shop/staff-scope` (STF-06، `lib/staff-scope.js`).
 *
 *   GET /config         همیشه باز: { enabled, places, mine } — `mine: []` یعنی همهٔ مکان‌ها
 *   GET /members        محدودهٔ هر عضوِ غیرِ مالک (members.view)
 *   PUT /members/:id    `{ places: [...] }` — فقط صاحب (members.role) و از نشستِ تازه احراز‌شده
 *
 * ⛔ پیش‌فرض خاموش: بی `shop.staff_scope` همه جز `/config` ⇒ ۴۰۳ `staff_scope_disabled`.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن. اجرای محدوده در `routes/catalog.js`.
 */
const express = require('express');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const scope = require('../lib/staff-scope');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { forbidden } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');
const { requireRecentAuth } = require('../lib/reauth');

const router = express.Router();
router.use(requireUser, requireShop);

async function needFlag(req, res, next) {
  if (!(await scope.enabled(req.shopId))) return next(forbidden('محدودهٔ مکانِ کارمندان برای این دکان روشن نیست', 'staff_scope_disabled'));
  next();
}

router.get('/config', async (req, res) => {
  const enabled = await scope.enabled(req.shopId);
  res.json({ enabled, places: scope.PLACES, mine: (enabled && (await scope.scopeOfReq(req))) || [] });
});

router.get('/members', needFlag, requirePermission('members.view'), async (req, res) => {
  res.json({ places: scope.PLACES, members: await scope.members(req.shopId) });
});

router.put('/members/:id', needFlag, requirePermission('members.role'), requireRecentAuth, async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه عضو' });
  const { member, places } = await scope.setMember(req.shopId, id, req.body?.places, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'perms.scope_updated',
    targetType: 'member', targetId: member.user_id, detail: { places }, ip: clientIp(req),
  });
  res.json({ userId: member.user_id, places, members: await scope.members(req.shopId) });
});

module.exports = router;
