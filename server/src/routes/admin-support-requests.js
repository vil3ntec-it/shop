'use strict';
/**
 * صفِ «از پشتیبانی بخواه» و «درستش کن» ِ یک‌کلیکی — `/api/admin/support-requests` (توانایی `support-requests.v1`،
 * `lib/support-requests.js`، مهاجرتِ ۹۶۰). زیرِ همان `requireAdmin` ِ `routes/admin.js` (و `lib/admin-roles.js`: مالی و
 * بیننده برای نوشتن بسته؛ مالی برای خواندن هم — «نه پشتیبانی»).
 *
 *   GET  /                                   صف: ?status=open|resolved|all &problem= &shop= &minAgeHours= &maxAgeHours= &limit= &offset=
 *   GET  /:id                                پرونده: درخواست، رشته و پیام‌ها، حالِ زندهٔ پرچم/اشتراک، کارهای انجام‌شده
 *   GET  /:id/sync-error                     آخرین خطای همگام‌سازیِ همان دکان (فقط خواندن؛ جزئیاتِ خصوصی فقط با اجازهٔ `read_private`)
 *   POST /:id/resolve                        «حل شد»
 *   POST /:id/fix/enable-flag                پرچمِ همان دکان روشن (ارشد)
 *   POST /:id/fix/grant-subscription         تمدید/فعال کردنِ اشتراک {days} (ارشد + `confirm: true` — پول)
 *   POST /:id/fix/raise-device-limit         سقفِ دستگاه {devices} (ارشد + `confirm: true` — پول)
 *   POST /:id/fix/resend-email-code          همان کدِ زندهٔ ورود دوباره به همان ایمیل (مدیر)
 *
 * ⛔ هر نوشتن: `shopId` ِ بدنه باید همان دکانِ درخواست باشد (وگرنه ۴۰۴)، `Idempotency-Key` با `admin-write-once`، یک بار
 *    برای هر (درخواست، کار)، ردیفِ سابقه با پیش ⇒ پس، پاسخِ خودکار در رشتهٔ کاربر و پوش به دستگاه‌های همان دکان.
 * ⛔ CSRF: مثلِ همهٔ `/api/admin/*` فقط Bearer (توکنِ مدیر یا کلیدِ سرویسِ پل)، هیچ کوکی‌ای — درخواستِ بین‌سایتی توکن ندارد.
 */
const express = require('express');
const v = require('../lib/validate');
const audit = require('../lib/audit');
const requests = require('../lib/support-requests');
const once = require('../lib/admin-write-once');
const { requireSuperAdmin } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest } = require('../middleware/errors');

const router = express.Router();
//  کارِ «درستش کن» پول و پرچم را عوض می‌کند: سقفِ هر مدیر/انجام‌دهنده، جدا از سطلِ IP ِ `admin.js`
const fixLimit = rateLimit({ max: 30, windowMs: 10 * 60 * 1000, keyPrefix: 'admin-support-fix', key: (req) => `${req.admin?.id || ''}:${req.actor?.id || ''}` });

const STATUSES = ['open', 'resolved', 'all'];
const hours = (x) => v.integer(x, { field: 'ساعت', min: 0, max: 24 * 365, def: 0 });
const shopOf = (req) => {
  //  '' = درخواستِ بی‌دکان (مهمان)؛ نبودنِ فیلد ⇒ ۴۰۰ (پنل همیشه دکانی را که نشان داده می‌فرستد)
  const s = req.body?.shopId;
  if (s === undefined || s === null || typeof s !== 'string') throw badRequest('شناسهٔ دکانِ همین درخواست لازم است', 'shop_required');
  return String(s).slice(0, 80);
};
const needConfirm = (req) => {
  if (req.body?.confirm !== true) throw badRequest('این کار پول و اشتراک را عوض می‌کند و تأیید می‌خواهد', 'confirm_required');
};

router.get('/', async (req, res, next) => {
  try {
    const status = v.oneOf(req.query?.status, STATUSES, { field: 'حال', def: 'open' });
    const shop = req.query?.shop ? v.id(req.query.shop, { field: 'دکان' }) : '';
    res.set('Cache-Control', 'no-store');
    res.json(await requests.list({
      status, shop, problem: v.text(req.query?.problem, { max: 80 }),
      minAgeHours: hours(req.query?.minAgeHours), maxAgeHours: hours(req.query?.maxAgeHours),
      limit: v.integer(req.query?.limit, { min: 1, max: 200, def: 100 }),
      offset: v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 }),
    }));
  } catch (err) { next(err); }
});

router.get('/:id', async (req, res, next) => {
  try {
    const out = await requests.detail(req.params.id);
    //  دیدنِ پروندهٔ یک درخواست (رشتهٔ یک حساب) سابقه دارد — همان قاعدهٔ `admin.support_thread_viewed` (TEN-04)
    await audit.log({
      shopId: out.request.shopId, actorType: 'admin', userId: req.admin.id, action: 'admin.support_request_viewed',
      targetType: 'support_request', targetId: out.request.id, ip: clientIp(req), detail: { problem: out.request.problem },
    });
    res.set('Cache-Control', 'no-store');
    res.json(out);
  } catch (err) { next(err); }
});

router.get('/:id/sync-error', async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json(await requests.syncErrorOf(req, req.params.id, { shopId: req.query?.shopId === undefined ? undefined : String(req.query.shopId) }));
  } catch (err) { next(err); }
});

router.post('/:id/resolve', fixLimit, async (req, res, next) => {
  try {
    const shopId = shopOf(req);
    const note = v.text(req.body?.note, { max: 200 });
    const out = await once.writeOnce(req, { tenantId: shopId, resultId: String(req.params.id) },
      async () => ({ ok: true, ...(await requests.resolve(req, req.params.id, { shopId, note })) }));
    once.send(res, out);
  } catch (err) { next(err); }
});

/** یک درِ «درستش کن»: نگهبانِ نقش و تأیید پیش از «یک بار» (درخواستِ ردشده نشانی نمی‌گذارد) */
function fixRoute(kind, { money = false } = {}) {
  return async (req, res, next) => {
    try {
      const shopId = shopOf(req);
      if (money) needConfirm(req);
      const body = {
        shopId, note: v.text(req.body?.note, { max: 120 }), resolve: req.body?.resolve === true,
        days: req.body?.days, devices: req.body?.devices,
      };
      const out = await once.writeOnce(req, { tenantId: shopId, resultId: String(req.params.id) },
        () => requests.fix(req, req.params.id, kind, body));
      once.send(res, out);
    } catch (err) { next(err); }
  };
}

router.post('/:id/fix/enable-flag', requireSuperAdmin, fixLimit, fixRoute('enable_flag'));
router.post('/:id/fix/grant-subscription', requireSuperAdmin, fixLimit, fixRoute('grant_subscription', { money: true }));
router.post('/:id/fix/raise-device-limit', requireSuperAdmin, fixLimit, fixRoute('raise_device_limit', { money: true }));
router.post('/:id/fix/resend-email-code', fixLimit, fixRoute('resend_email_code'));

module.exports = router;
