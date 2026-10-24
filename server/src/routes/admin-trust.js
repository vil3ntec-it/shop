'use strict';
/**
 * صفِ رسیدگی به شکایت و تخلفِ بازارچه — `/api/admin/trust` (v3 §۳۸.۶؛ ABUSE-01)؛
 * و `GET /report`، گزارشِ سوءاستفادهٔ هر دکان برای پنل (OPS-09).
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
const { many } = require('../db');

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

/**
 * گزارشِ سوءاستفاده برای پنلِ VILL3N (OPS-09): به‌ازای هر دکان در بازه — شمارِ
 * پرونده، در جریان، واردشده/ردشده، شدید، نشان‌دار (همان متن/همان IP) و جریمهٔ
 * فعال؛ و دسته‌ها. فقط‌خواندنی و فقط شمار؛ نه متنِ شکایت، نه شمارهٔ شاکی. دیدنش
 * مثلِ دیدنِ صف سابقه دارد.
 */
const REPORT_DAYS = Object.freeze({ def: 30, max: 366 });
const REPORT_SHOPS = 200;
router.get('/report', requireSuperAdmin, wrap(async (req, res) => {
  const raw = Number(req.query.days);
  const days = Number.isInteger(raw) ? Math.min(REPORT_DAYS.max, Math.max(1, raw)) : REPORT_DAYS.def;
  const since = Date.now() - days * 24 * 60 * 60 * 1000;
  const [shops, categories] = await Promise.all([
    many(
      `SELECT c.shop_id, COALESCE(s.name, '') AS shop_name, COUNT(*)::int AS cases,
              COUNT(*) FILTER (WHERE c.status IN ('open','in_review','appealed'))::int AS pending,
              COUNT(*) FILTER (WHERE c.outcome = 'upheld' AND c.appeal_outcome <> 'reversed')::int AS upheld,
              COUNT(*) FILTER (WHERE c.outcome = 'dismissed' OR c.appeal_outcome = 'reversed')::int AS dismissed,
              COUNT(*) FILTER (WHERE c.severity = 'high')::int AS high,
              COUNT(*) FILTER (WHERE c.kind = 'complaint')::int AS complaints,
              COUNT(*) FILTER (WHERE c.kind = 'report')::int AS reports,
              COUNT(*) FILTER (WHERE jsonb_array_length(c.flags) > 0)::int AS flagged,
              MAX(c.created_at) AS last_at,
              (SELECT COUNT(*)::int FROM trust_sanctions t WHERE t.shop_id = c.shop_id AND t.lifted_at IS NULL) AS active_sanctions
         FROM trust_cases c LEFT JOIN shops s ON s.id = c.shop_id
        WHERE c.created_at >= $1
        GROUP BY c.shop_id, s.name
        ORDER BY upheld DESC, high DESC, cases DESC, c.shop_id
        LIMIT ${REPORT_SHOPS + 1}`,
      [since]
    ),
    many(
      `SELECT kind, category, COUNT(*)::int AS n FROM trust_cases WHERE created_at >= $1
        GROUP BY kind, category ORDER BY n DESC, kind, category`,
      [since]
    ),
  ]);
  const list = shops.slice(0, REPORT_SHOPS).map((r) => ({
    shopId: r.shop_id, shopName: r.shop_name, cases: r.cases, pending: r.pending, upheld: r.upheld,
    dismissed: r.dismissed, high: r.high, complaints: r.complaints, reports: r.reports, flagged: r.flagged,
    activeSanctions: r.active_sanctions, lastAt: Number(r.last_at) || null,
  }));
  const sum = (k) => list.reduce((a, r) => a + r[k], 0);
  await log(req, { action: 'admin.trust_report_viewed', detail: { days, shops: list.length } });
  res.set('Cache-Control', 'no-store');
  res.json({
    days, since, truncated: shops.length > REPORT_SHOPS,
    totals: { cases: sum('cases'), pending: sum('pending'), upheld: sum('upheld'), dismissed: sum('dismissed'), high: sum('high'), flagged: sum('flagged') },
    categories: categories.map((c) => ({ kind: c.kind, category: c.category, count: c.n })),
    shops: list,
  });
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
