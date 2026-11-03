'use strict';
/**
 * IMG-PRO-17 — تغییرِ دسته‌ایِ عکسِ کالاها و برگرداندنش (`/api/shop/images/changes`). شرح: ‎lib/image-changes.js‎.
 *
 *   GET  /                 آخرین دسته‌های همین دکان (`?limit=` تا ۵۰)
 *   GET  /:id              یک دسته با وضعِ پیش و پسِ هر کالا
 *   POST /                 `{key, note?, items:[{product_id, source, image_id?, version?, confirm?, expect?}]}` ⇒ ۲۰۱
 *                          (همان کلید ⇒ ۲۰۰ و همان دسته)؛ هر قلم همان قاعدهٔ `POST /api/shop/images/select`
 *   POST /:id/rollback     `{reason?}` ⇒ همهٔ کالاها به وضعِ پیش از دسته، یا ۴۰۹ `changed_since` و هیچ
 *
 * ⛔ پیش‌فرض خاموش: همان پرچمِ `shop.image_bank` (خاموش ⇒ ۴۰۳ `image_bank_disabled`، بی هیچ نوشتن).
 * ⛔ سقفِ نرخ برای هر IP **پیش از** احراز، و پس از آن برای هر دکان؛ shop_id فقط از عضویتِ توکن (`requireShop`).
 * ⛔ نوشتن همان مجوزِ عکسِ کالا (`data.write` + `canWrite(role, 'products')`) — مثلِ `POST /select`.
 */
const express = require('express');
const bank = require('../lib/image-bank');
const changes = require('../lib/image-changes');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();

router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'imgchanges-ip' }));
router.use(requireUser, requireShop, requirePermission('data.read'));
router.use(rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'imgchanges', key: (req) => req.shopId }));
router.use(async (req, res, next) => {
  if (!(await bank.shopEnabled(req.shopId))) {
    return next(forbidden('بانکِ تصاویر برای این دکان روشن نیست', 'image_bank_disabled'));
  }
  next();
});

const write = [
  requirePermission('data.write'),
  (req, res, next) => (require('../lib/perm-core').canWrite(req.role, 'products')
    ? next() : next(forbidden('عکسِ کالا در حدِ نقشِ شما نیست', 'table_not_allowed'))),
];

router.get('/', async (req, res) => {
  res.set('Cache-Control', 'private, no-store');
  res.json(await changes.list(req.shopId, { limit: req.query?.limit }));
});

router.get('/:id', async (req, res) => {
  res.set('Cache-Control', 'private, no-store');
  res.json(await changes.detail(req.shopId, req.params.id));
});

router.post('/', write, async (req, res) => {
  const out = await changes.apply(req.shopId, req.user.id, req.body || {}, { ip: clientIp(req) });
  res.status(out.replayed ? 200 : 201).json(out);
});

router.post('/:id/rollback', write, async (req, res) => {
  res.json(await changes.rollback(req.shopId, req.user.id, req.params.id, req.body || {}, { ip: clientIp(req) }));
});

module.exports = router;
