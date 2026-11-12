'use strict';
/**
 * صفِ رسیدگی به شکایت و تخلفِ بازارچه — `/api/admin/trust` (v3 §۳۸.۶؛ ABUSE-01).
 *
 * خواندن (صف، پرونده، جریمه‌ها): هر مدیری که `lib/admin-roles.js` به GET راه می‌دهد (ارشد، مدیر، بیننده؛ مالی نه) —
 * `trust-read.v1`. نقشِ زیرِ ارشد نمای کم‌تر می‌گیرد (`lib/trust-admin-view.js`: نه نشانه و سابقهٔ شاکی، نه هم‌متن‌ها،
 * نه مدرک، نه شناسهٔ آدم). مدرک (بایتِ عکس) و هر نوشتن فقط ارشد.
 * هر دیدنِ پرونده/مدرک و هر نوشتن در سابقهٔ عملیات می‌نشیند (§۳۹.۸؛ TEN-04 ⇒ `admin.trust_case_viewed`).
 * تصمیم، رأیِ اعتراض و برداشتنِ جریمه بی دلیلِ نوشته انجام نمی‌شوند (همان `requireReason` ِ #245؛ دلیل در سابقه و در
 * تاریخچهٔ پرونده). جریمه فقط سه نوع است: هشدار، پنهان کردنِ کالا، تعلیقِ ویترین — همه برگشت‌پذیر.
 * هر چهار نوشتن `Idempotency-Key` ِ اختیاری می‌پذیرند (`lib/admin-write-once.js`، `idempotent-writes.v1`): همان کلید ⇒ همان
 * پاسخ و یک سابقه. از راهِ پل «چه کسی» کاربرِ پنل است (`req.actor.id` و نامش)، نه شناسهٔ کلید.
 *
 * به پرچمِ `market.trust` بسته نیست: با پرچمِ خاموش ردیفی نیست و صف خالی است؛
 * ولی برداشتنِ جریمه‌ای که پیش‌تر داده شده همیشه ممکن می‌ماند.
 */
const express = require('express');
const trust = require('../lib/market-trust');
const view = require('../lib/trust-admin-view');
const audit = require('../lib/audit');
const writeOnce = require('../lib/admin-write-once');
const { requireReason } = require('../lib/admin-access');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');

const router = express.Router();
router.use(requireAdmin);
const log = (req, entry) => audit.log({ actorType: 'admin', userId: req.admin.id, ip: clientIp(req), ...entry });

router.get('/cases', async (req, res) => {
  const cases = await trust.queue({ status: req.query.status, shopId: req.query.shopId, limit: req.query.limit },
    view.fullView(req) ? undefined : view.queueRow);
  await log(req, { action: 'admin.trust_queue_viewed', detail: { count: cases.length } });
  res.set('Cache-Control', 'no-store');
  res.json({ cases });
});

router.get('/cases/:id', async (req, res) => {
  const c = await trust.adminView(req.params.id);
  await log(req, { shopId: c.shopId, action: 'admin.trust_case_viewed', targetType: 'trust_case', targetId: c.id });
  res.set('Cache-Control', 'no-store');
  res.json({ case: view.fullView(req) ? c : view.caseView(c) });
});

router.get('/cases/:id/evidence/:evidenceId', requireSuperAdmin, async (req, res) => {
  const out = await trust.adminEvidence(req.params.id, req.params.evidenceId);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_evidence_viewed', targetType: 'trust_evidence', targetId: out.media.id,
  });
  require('../lib/shop-chat').sendMedia(res, out.media);
});

/** نوشتنِ ارشد: دلیل پیش از «یک بار»؛ کار و سابقه درونِ آن (بازپخش سابقهٔ دوم نمی‌نویسد). */
const write = (needReason, run) => async (req, res) => {
  const reason = needReason ? requireReason(req) : '';
  const who = view.actorOf(req);
  const out = await writeOnce.writeOnce(req, { resultId: String(req.params.id || '').slice(0, 64) }, () => run(req, who, reason));
  writeOnce.send(res, out);
};

router.post('/cases/:id/assign', requireSuperAdmin, write(false, async (req, who) => {
  const out = await trust.assign(who.id, req.params.id, who.name);
  await log(req, { shopId: out.shopId, action: 'admin.trust_case_assigned', targetType: 'trust_case', targetId: out.id });
  return out;
}));

router.post('/cases/:id/decide', requireSuperAdmin, write(true, async (req, who, reason) => {
  const out = await trust.decide(who.id, req.params.id, req.body, reason, who.name);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_case_decided', targetType: 'trust_case', targetId: out.id,
    detail: { outcome: out.outcome, reason, sanction: out.sanction },
  });
  return out;
}));

router.post('/cases/:id/appeal-decision', requireSuperAdmin, write(true, async (req, who, reason) => {
  const out = await trust.decideAppeal(who.id, req.params.id, req.body, reason);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_appeal_decided', targetType: 'trust_case', targetId: out.id,
    detail: { outcome: out.outcome, reason, lifted: out.lifted },
  });
  return out;
}));

router.get('/sanctions', async (req, res) => {
  const sanctions = await trust.sanctionsForAdmin({ shopId: req.query.shopId, active: req.query.active, limit: req.query.limit });
  res.set('Cache-Control', 'no-store');
  res.json({ sanctions: view.fullView(req) ? sanctions : sanctions.map(view.sanction) });
});

router.post('/sanctions/:id/lift', requireSuperAdmin, write(true, async (req, who, reason) => {
  const out = await trust.liftSanction(who.id, req.params.id, reason);
  await log(req, {
    shopId: out.shopId, action: 'admin.trust_sanction_lifted', targetType: 'trust_sanction', targetId: out.id,
    detail: { kind: out.kind, caseId: out.caseId, reason },
  });
  return out;
}));

module.exports = router;
