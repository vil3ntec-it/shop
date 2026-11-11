'use strict';
/**
 * ۲-۹ (ACP-D1 الف، ACP-D2 الف) — اجازهٔ موقتِ دسترسیِ پشتیبانی به یک دکان، سمتِ مدیر (توانایی ‎support-access.v1‎ ِ پلِ پنل،
 * `console-support-access.js` ِ ریپوی server). منطق، چرخه و قاعده‌ها: `lib/support-access.js`.
 *
 *   GET  /api/admin/shops/:id/access-grants                ⇒ { shopId, grants, policy }                  هر نقشِ مدیر جز مالی
 *   POST /api/admin/shops/:id/access-grants                {scope, minutes, reason, consent:'tenant', ticket_id?} ⇒ { grant }
 *   POST /api/admin/shops/:id/access-grants/break-glass    {scope, minutes, reason, consent:'break_glass', notify_owner:true,
 *                                                           password?} ⇒ { grant, notified }               فقط مالکِ پلتفرم
 *   POST /api/admin/shops/:id/access-grants/:gid/revoke    {reason} ⇒ { grant, unchanged }
 *
 * ⛔ زیرِ `requireAdmin` ِ `routes/admin.js` (کنارِ `admin-shop-state.js` روی `/shops/:id`)؛ سقفِ IP پیش از احراز همان‌جا
 *    (`admin-access-grants-ip`) و سقفِ نوشتنِ هر مدیر/انجام‌دهنده این‌جا. نوشتن فقط ارشد (پنل: admin ⇒ superadmin).
 * ⛔ همهٔ ورودی (نقش، مالک، دلیل، مدت، دامنه، رضایت) پیش از هر نوشتن؛ احرازِ دوباره پیش از کلیدِ یکتایی و رمز پیش از آن از
 *    بدنه برداشته می‌شود (هرگز در هشِ کلید یا هیچ ردیفی). `Idempotency-Key` ِ اختیاری ⇒ همان پاسخ، بی اجازه و خبرِ دوم.
 */
const express = require('express');
const access = require('../lib/support-access');
const writeOnce = require('../lib/admin-write-once');
const v = require('../lib/validate');
const { badRequest } = require('../middleware/errors');
const { rateLimit } = require('../middleware/ratelimit');
const { requireSuperAdmin } = require('../middleware/auth');

const router = express.Router({ mergeParams: true });

//  نوشتن: هر مدیر/انجام‌دهنده ۳۰ در ده دقیقه (همان سقفِ پنل) — درخواست، شکستنِ شیشه و لغو
const writeLimit = rateLimit({
  max: 30, windowMs: 10 * 60 * 1000, keyPrefix: 'admin-access-grants-write',
  key: (req) => `${req.admin?.id || 'x'}:${req.actor?.id || ''}`,
});

router.get('/access-grants', async (req, res) => {
  const shop = await access.shopOf(req.params.id);
  res.set('Cache-Control', 'no-store');
  res.json({ shopId: shop.id, grants: await access.list(shop.id), policy: access.POLICY });
});

router.post('/access-grants', requireSuperAdmin, writeLimit, async (req, res) => {
  const shop = await access.shopOf(req.params.id);
  access.requester(req);
  const b = req.body || {};
  if (b.consent !== undefined && b.consent !== 'tenant') throw badRequest('این در فقط درخواست با رضایتِ دکان است', 'bad_consent');
  const input = {
    scope: access.scopeOf(b.scope), minutes: access.minutesOf(b.minutes), reason: access.reasonOf(b, access.REASON.request),
    ticketId: v.text(b.ticket_id ?? b.ticketId, { max: 80 }).trim(),
  };
  const out = await writeOnce.writeOnce(req, { tenantId: shop.id }, () => access.request(req, shop.id, input));
  writeOnce.send(res, out, 201);
});

router.post('/access-grants/break-glass', requireSuperAdmin, writeLimit, async (req, res) => {
  const shop = await access.shopOf(req.params.id);
  access.requester(req);
  await access.assertPlatformOwner(req, shop.id);
  const b = req.body || {};
  if (b.consent !== 'break_glass') throw badRequest('«شکستنِ شیشه» باید consent: break_glass بگوید', 'bad_consent');
  if (b.notify_owner !== true) throw badRequest('«شکستنِ شیشه» بی خبرِ همان دمِ صاحبِ دکان نیست (notify_owner: true)', 'notify_owner_required');
  const input = { scope: access.scopeOf(b.scope), minutes: access.minutesOf(b.minutes), reason: access.reasonOf(b, access.REASON.breakGlass) };
  const reauth = await access.assertReauth(req);
  if (req.body && 'password' in req.body) delete req.body.password;
  const out = await writeOnce.writeOnce(req, { tenantId: shop.id }, () => access.breakGlass(req, shop.id, input, reauth));
  writeOnce.send(res, out, 201);
});

router.post('/access-grants/:gid/revoke', requireSuperAdmin, writeLimit, async (req, res) => {
  const shop = await access.shopOf(req.params.id);
  const gid = v.id(req.params.gid, { field: 'شناسهٔ اجازه' });
  access.requester(req);
  const reason = access.reasonOf(req.body, access.REASON.revoke);
  const out = await writeOnce.writeOnce(req, { tenantId: shop.id, resultId: gid }, () => access.revoke(req, shop.id, gid, reason));
  writeOnce.send(res, out);
});

module.exports = router;
