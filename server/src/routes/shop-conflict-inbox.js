'use strict';
/**
 * صندوقِ سندهای ردشده — `/api/shop/conflict-inbox` (منطق در `lib/conflict-inbox.js`، مهاجرتِ ۳۱۵).
 *
 *   GET  /                    صاحب/مدیر: فهرست { conflicts, open } — ?status=open|closed|all&limit
 *   GET  /mine                هر عضو: ردیف‌های خودش (نشانِ «سرور نپذیرفت» روی همان فاکتور)
 *   GET  /:id                 صاحب/مدیر: فاکتورِ کامل و همهٔ opهای بسته
 *   POST /:id/resubmit {note?}             «دوباره ثبت با تأییدِ من» — یک بار، تکرار ⇒ همان پاسخ
 *   POST /:id/dismiss  {reason, fixedDoc?} «کنار بگذار» (دلیل لازم) / «اصلاح و ثبت» (با شناسهٔ فاکتورِ تازه)
 *
 * ⛔ shop_id و کاربر فقط از عضویتِ توکن. سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر؛ نوشتن سطلِ تنگ‌ترِ خودش.
 * ⛔ کلیدِ اشتراک ندارد (SUB-10: FREE): دادهٔ فروشنده گروگانِ اشتراک نمی‌شود (D-05)؛ همان «دوباره ثبت» ِ همگام‌سازی است.
 * ⛔ پیش‌فرض روشن و بی پرچم: صندوق فقط وقتی چیزی نشان می‌دهد که سندی رد شده باشد.
 */
const express = require('express');
const config = require('../config');
const inbox = require('../lib/conflict-inbox');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'conflict-inbox-ip' }));
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'conflict-inbox', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('data.read'));
//  هر «دوباره ثبت» یک push ِ کامل است
const writeLimit = rateLimit({ max: 30, windowMs: 60 * 1000, keyPrefix: 'conflict-inbox-write', key: (req) => req.user && req.user.id });

const actorOf = (req) => ({ userId: req.user.id, role: req.role });

router.get('/', requirePermission(inbox.RESOLVE_PERMISSION), async (req, res) => {
  res.json(await inbox.list(req.shopId, actorOf(req), { status: String(req.query.status || 'open'), limit: req.query.limit }));
});

router.get('/mine', async (req, res) => {
  res.json(await inbox.mine(req.shopId, actorOf(req), { limit: req.query.limit }));
});

router.get('/:id', requirePermission(inbox.RESOLVE_PERMISSION), async (req, res) => {
  const hideContact = await require('../lib/customer-contact').hiddenForReq(req);
  res.json(await inbox.get(req.shopId, actorOf(req), req.params.id, { hideContact }));
});

router.post('/:id/resubmit', requirePermission(inbox.RESOLVE_PERMISSION), writeLimit, async (req, res) => {
  res.json(await inbox.resubmit(req.shopId, actorOf(req), req.params.id, { note: req.body?.note, ip: clientIp(req) }));
});

router.post('/:id/dismiss', requirePermission(inbox.RESOLVE_PERMISSION), writeLimit, async (req, res) => {
  res.json(await inbox.dismiss(req.shopId, actorOf(req), req.params.id, {
    reason: req.body?.reason, fixedDoc: req.body?.fixedDoc, ip: clientIp(req),
  }));
});

module.exports = router;
