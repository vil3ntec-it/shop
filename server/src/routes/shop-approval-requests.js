'use strict';
/**
 * «درخواستِ تأیید» از راهِ دور — `/api/shop/approval-requests` (STF-02؛ منطق در `lib/approval-requests.js`).
 *
 *   GET  /config               همیشه باز: { enabled, approver, ttlMs }
 *   POST /                     شاگرد می‌پرسد { key, perm, doc, pctBp?, amount?, reason, ttlMs? }   ⇒ ۲۰۱ (تکرار ۲۰۰)
 *   GET  /?status=open|…       صاحب و مدیر همه؛ بقیه فقط خودشان
 *   GET  /:id                  درخواست‌کننده یا صاحب/مدیر (حال: pending/approved/denied/cancelled/used/expired)
 *   POST /:id/approve {note?}  صاحب یا مدیر روی دستگاهِ خودش — نه خودش، و فقط اگر خودش همان مقدار را مجاز دارد
 *   POST /:id/deny    {note?}
 *   POST /:id/cancel           درخواست‌کننده پس می‌گیرد
 * مصرف: opِ همگام‌سازیِ همان سند با `approval: {v:1, request:<id>}` (`staff-approval.acceptSlip`)، یک بار.
 *
 * ⛔ STF-06 ِ شعبه (پرچمِ `shop.branches`): درِ شعبه‌شناس (بیرون از `lib/branch-gate.js`) — ساختن با شعبهٔ سرآیندِ سنجیدهٔ
 *    `X-Branch-Id` (`branches.resolve`؛ کارمندِ بی اصلی بی سرآیند ⇒ ۴۰۳ `branch_required`)، فهرست/دیدن/تأیید فقط شعبه‌های
 *    همان مدیر (صاحب همه). پرچم خاموش ⇒ سرآیند خوانده هم نمی‌شود.
 * ⛔ پیش‌فرض خاموش: بی `shop.approval_requests` همه جز `/config` ⇒ ۴۰۳ `approval_requests_disabled`.
 * ⛔ shop_id و کاربر فقط از عضویتِ توکن. سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر.
 * ⛔ کلیدِ اشتراک ندارد (SUB-10: FREE): تأییدِ مدیر بخشی از مجوزهای هستهٔ دکان است، نه قابلیتِ پولی.
 */
const express = require('express');
const config = require('../config');
const ar = require('../lib/approval-requests');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');
const branches = require('../lib/branches');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'approval-requests-ip' }));
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'approval-requests', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('data.read'));

const actorOf = (req) => ({ userId: req.user.id, role: req.role });
const deviceOf = (req) => (req.device ? String(req.device.device_uid || req.device.id || '') : '');
/** STF-06: دسترسیِ شعبهٔ همین عضو، یا null وقتی پرچمِ `shop.branches` خاموش است (بی هیچ سنجشِ شعبه) */
const accessOf = async (req) => ((await branches.enabled(req.shopId)) ? branches.accessOf(req.shopId, req.member) : null);

//  Express 5 خودش promiseِ ردشده را به next(err) می‌دهد
async function needFlag(req, res, next) {
  if (!(await ar.enabled(req.shopId))) return next(forbidden('درخواستِ تأیید از راهِ دور برای این دکان روشن نیست', 'approval_requests_disabled'));
  next();
}

router.get('/config', async (req, res) => {
  //  `v3`: این سرور Sync v3 دارد — بی آن، کاری که پیش‌فرضِ نقش برایش «ممنوع» است `approval_needs_v3` می‌گیرد و برنامه دکمه را
  //  نشان نمی‌دهد (`RemoteApproval.offered`)
  res.json({ enabled: await ar.enabled(req.shopId), approver: ar.APPROVER_ROLES.includes(req.role), ttlMs: ar.TTL_DEFAULT_MS,
    v3: Boolean(require('../config').syncV3.enabled) });
});

router.post('/', needFlag, async (req, res) => {
  const access = await accessOf(req);
  const branchId = access ? branches.resolve(access, branches.claimOf(req)) : '';
  const out = await ar.create(req.shopId, actorOf(req), req.body, { deviceId: deviceOf(req), branchId });
  res.status(out.duplicate ? 200 : 201).json(out);
});

router.get('/', needFlag, async (req, res) => {
  res.json(await ar.list(req.shopId, actorOf(req), { status: String(req.query.status || 'open'), limit: req.query.limit, access: await accessOf(req) }));
});

router.get('/:id', needFlag, async (req, res) => {
  res.json(await ar.get(req.shopId, actorOf(req), req.params.id, { access: await accessOf(req) }));
});

router.post('/:id/approve', needFlag, async (req, res) => {
  res.json(await ar.decide(req.shopId, actorOf(req), req.params.id, 'approve', { note: req.body?.note, access: await accessOf(req) }));
});

router.post('/:id/deny', needFlag, async (req, res) => {
  res.json(await ar.decide(req.shopId, actorOf(req), req.params.id, 'deny', { note: req.body?.note, access: await accessOf(req) }));
});

router.post('/:id/cancel', needFlag, async (req, res) => {
  res.json(await ar.cancel(req.shopId, actorOf(req), req.params.id));
});

module.exports = router;
