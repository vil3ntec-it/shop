'use strict';
/**
 * پشتیبانِ سرورِ فاکتورهای نگه‌داشته — `/api/shop/held-invoices` (منطق و قاعدهٔ تعارض: `lib/shop-held-sync.js`؛
 * مهاجرتِ ۴۲۵؛ POS-12، S09).
 *
 *   GET    /?since=<rev>          فروشنده ⇒ `{enabled, rev?, more?, items?, gone?, permissions?, limits?}`
 *   PUT    /:id                   `{version, body, device_id?}` ⇒ `{enabled, applied, item}` (کهنه ⇒ ۴۰۹ `held_stale`)
 *   DELETE /:id?version=&reason=  `resumed|sold|deleted` ⇒ `{enabled, deleted, version}`؛ دور انداختنِ نگه‌داشتهٔ کسِ
 *                                 دیگر `sales.cancel` (۴۰۳ `held_not_yours`)
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.held_sync` ⇒ `GET` فقط `{enabled:false}` و `PUT`/`DELETE` ۴۰۳ `held_sync_disabled`.
 * ⛔ shop_id فقط از عضویتِ توکن. سقف: سطلِ IP پیش از احراز، سپس سطلِ هر کاربر. فقط برنامهٔ دکان، نه sync-v1.
 * ⛔ کلیدِ اشتراک ندارد (SUB-10: FREE): نگه‌داشتنِ فاکتور بخشی از صندوقِ پایه است؛ دادهٔ دکان گروگان نمی‌شود.
 */
const express = require('express');
const config = require('../config');
const held = require('../lib/shop-held-sync');
const audit = require('../lib/audit');
const perms = require('../lib/staff-perms');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'shop-held-ip' }));
const limit = rateLimit({ max: 240, windowMs: 60 * 1000, keyPrefix: 'shop-held', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('data.write'));

const needFlag = async (req, res, next) => {
  if (!(await held.flagOn(req.shopId))) return next(forbidden('پشتیبانِ فاکتورهای نگه‌داشته برای این دکان روشن نیست', 'held_sync_disabled'));
  next();
};

/** خبر به دستگاه‌های دیگرِ همان دکان — خودِ داده از این در نمی‌رود (همان `settings`) */
function relay(req, rev) {
  try {
    require('../lib/sync-v1-live').relay({ app: 'shop', accountKind: 'shop', accountId: String(req.shopId) }, { event: 'held', rev });
  } catch { /* سوکت نبود: دستگاه‌ها با دورِ بعدی می‌گیرند */ }
}

router.get('/', async (req, res) => {
  if (!(await held.flagOn(req.shopId))) return res.json({ enabled: false });
  const since = Number.parseInt(typeof req.query.since === 'string' ? req.query.since : '0', 10);
  const out = await held.read(req.shopId, Number.isSafeInteger(since) ? since : 0, req.user.id);
  res.json({ enabled: true, ...out, permissions: { cancel: await perms.memberAllows(req, 'sales.cancel') } });
});

router.put('/:id', needFlag, async (req, res) => {
  const out = await held.put(req.shopId, { userId: req.user.id }, req.params.id, req.body);
  if (out.applied) relay(req, out.item.rev);
  res.json({ enabled: true, ...out });
});

router.delete('/:id', needFlag, async (req, res) => {
  const q = (k) => (typeof req.query[k] === 'string' ? req.query[k] : undefined);
  const canCancel = await perms.memberAllows(req, 'sales.cancel');
  const out = await held.remove(req.shopId, { userId: req.user.id, deviceId: q('device_id') }, req.params.id,
    { version: q('version'), reason: q('reason') }, canCancel);
  if (out.deleted) {
    if (q('reason') === 'deleted') {
      await audit.log({
        shopId: req.shopId, userId: req.user.id, action: 'held.discard', targetType: 'held_invoice', targetId: req.params.id,
        detail: { others: out.others, version: out.version }, ip: clientIp(req),
      });
    }
    relay(req, out.rev);
  }
  res.json({ enabled: true, deleted: out.deleted, version: out.version });
});

module.exports = router;
