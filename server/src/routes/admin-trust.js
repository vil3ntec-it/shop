'use strict';
/**
 * صفِ رسیدگی به شکایت و تخلفِ بازارچه — `/api/admin/trust` (v3 §۳۸.۶؛ ABUSE-01).
 *
 * فقط مدیرِ ارشد. هر دیدنِ پرونده/مدرک و هر نوشتن در سابقهٔ عملیات می‌نشیند
 * (§۳۹.۸). تصمیم، رأیِ اعتراض و برداشتنِ جریمه بی دلیلِ نوشته انجام نمی‌شوند
 * (همان `requireReason` ِ #245؛ دلیل در سابقه و در تاریخچهٔ پرونده).
 * جریمه فقط سه نوع است: هشدار، پنهان کردنِ کالا، تعلیقِ ویترین — همه برگشت‌پذیر.
 *
 * به پرچمِ `market.trust` بسته نیست: با پرچمِ خاموش ردیفی نیست و صف خالی است؛
 * ولی برداشتنِ جریمه‌ای که پیش‌تر داده شده همیشه ممکن می‌ماند.
 */
const express = require('express');
const trust = require('../lib/market-trust');
const audit = require('../lib/audit');
const { requireReason } = require('../lib/admin-access');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();
router.use(requireAdmin);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const log = (req, entry) => audit.log({ actorType: 'admin', userId: req.admin.id, ip: clientIp(req), ...entry });

router.get('/cases', requireSuperAdmin, wrap(async (req, res) => {
  const cases = await trust.queue({ status: req.query.status, shopId: req.query.shopId, limit: req.query.limit });
  await log(req, { action: 'admin.trust_queue_viewed', detail: { count: cases.length } });
  res.set('Cache-Control', 'no-store');
  res.json({ cases });
}));

router.get('/cases/:id', requireSuperAdmin, wrap(async (req, res) => {
  const c = await trust.adminView(req.params.id);
  await log(req, { shopId: c.shopId, action: 'admin.trust_case_viewed', targetType: 'trust_case', targetId: c.id });
  res.set('Cache-Control', 'no-store');
  res.json({ case: c });
}));

router.get('/cases/:id/evidence/:evidenceId', requireSuperAdmin, wrap(async (req, res) => {
  const out = await trust.adminEvidence(req.params.id, req.params.evidenceId);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_evidence_viewed', targetType: 'trust_evidence', targetId: out.media.id,
  });
  require('../lib/shop-chat').sendMedia(res, out.media);
}));

router.post('/cases/:id/assign', requireSuperAdmin, wrap(async (req, res) => {
  const out = await trust.assign(req.admin.id, req.params.id);
  await log(req, { shopId: out.shopId, action: 'admin.trust_case_assigned', targetType: 'trust_case', targetId: out.id });
  res.json(out);
}));

router.post('/cases/:id/decide', requireSuperAdmin, wrap(async (req, res) => {
  const reason = requireReason(req);
  const out = await trust.decide(req.admin.id, req.params.id, req.body, reason);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_case_decided', targetType: 'trust_case', targetId: out.id,
    detail: { outcome: out.outcome, reason, sanction: out.sanction },
  });
  res.json(out);
}));

router.post('/cases/:id/appeal-decision', requireSuperAdmin, wrap(async (req, res) => {
  const reason = requireReason(req);
  const out = await trust.decideAppeal(req.admin.id, req.params.id, req.body, reason);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_appeal_decided', targetType: 'trust_case', targetId: out.id,
    detail: { outcome: out.outcome, reason, lifted: out.lifted },
  });
  res.json(out);
}));

router.get('/sanctions', requireSuperAdmin, wrap(async (req, res) => {
  const sanctions = await trust.sanctionsForAdmin({ shopId: req.query.shopId, active: req.query.active, limit: req.query.limit });
  res.set('Cache-Control', 'no-store');
  res.json({ sanctions });
}));

router.post('/sanctions/:id/lift', requireSuperAdmin, wrap(async (req, res) => {
  const reason = requireReason(req);
  const out = await trust.liftSanction(req.admin.id, req.params.id, reason);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_sanction_lifted', targetType: 'trust_sanction', targetId: out.id,
    detail: { kind: out.kind, caseId: out.caseId, reason },
  });
  res.json(out);
}));

module.exports = router;
