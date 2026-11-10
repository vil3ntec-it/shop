'use strict';
/**
 * استودیوی عکسِ کالا — درِ دکان (`/api/shop/images/studio`). شرح: ‎lib/image-studio-jobs.js‎ و ‎lib/image-studio.js‎.
 *
 *   GET  /config                                 پرچم، presetها، گزینه‌ها و سقف‌ها
 *   GET  /batches                                آخرین دسته‌های همین دکان (`?limit=` تا ۵۰)
 *   POST /batches                                `{key, image_ids[], preset?, options?}` ⇒ ۲۰۱ (همان کلید ⇒ ۲۰۰ و همان دسته)
 *   GET  /batches/:id                            پیشرفتِ دقیق و قلم‌ها (`?after=` صفحهٔ بعد)
 *   POST /batches/:id/action                     `{action: pause|resume|cancel|retry}`
 *   GET  /images/:id/versions                    همهٔ نسخه‌های عکسِ خود (قبل/بعد)
 *   POST /images/:id/versions/:version/review    `{decision: approve|reject, confirm_risk?}`
 *
 * ⛔ پیش‌فرض خاموش: `shop.image_bank` و `shop.image_studio` (خاموش ⇒ ۴۰۳، بی هیچ نوشتن).
 * ⛔ سقفِ نرخ برای هر IP **پیش از** احراز، و پس از آن برای هر دکان؛ shop_id فقط از عضویتِ توکن (`requireShop`).
 * ⛔ نوشتن همان مجوزِ عکسِ کالا (`data.write` + `canWrite(role, 'products')`) — مثلِ `POST /api/shop/images/select`.
 */
const express = require('express');
const bank = require('../lib/image-bank');
const jobs = require('../lib/image-studio-jobs');
const studio = require('../lib/image-studio');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
const BASE = '/api/shop/images';

router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'imgstudio-ip' }));
router.use(requireUser, requireShop, requirePermission('data.read'));
router.use(rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'imgstudio', key: (req) => req.shopId }));
router.use(async (req, res, next) => {
  if (!(await bank.shopEnabled(req.shopId))) return next(forbidden('بانکِ تصاویر برای این دکان روشن نیست', 'image_bank_disabled'));
  if (!(await jobs.enabled(req.shopId))) return next(forbidden('استودیوی عکس برای این دکان روشن نیست', 'image_studio_disabled'));
  next();
});

const write = [
  requirePermission('data.write'),
  (req, res, next) => (require('../lib/perm-core').canWrite(req.role, 'products')
    ? next() : next(forbidden('عکسِ کالا در حدِ نقشِ شما نیست', 'table_not_allowed'))),
];

router.get('/config', (req, res) => {
  res.json({
    enabled: true, presets: studio.PRESETS, aspects: Object.keys(studio.ASPECTS),
    limits: { batch_max: jobs.LIMITS.BATCH_MAX, open_max: jobs.LIMITS.OPEN_MAX, versions_max: jobs.LIMITS.VERSIONS_MAX, side: studio.LIMITS.WORK_SIDE },
  });
});

router.get('/batches', async (req, res) => {
  res.json({ items: await jobs.batches(req.shopId, { limit: req.query?.limit }) });
});

router.post('/batches', write, async (req, res) => {
  const out = await jobs.createBatch(req.shopId, req.user.id, req.body || {}, clientIp(req));
  res.status(out.existing ? 200 : 201).json({ ...await jobs.batchView(req.shopId, out.id, { base: BASE }), existing: out.existing });
});

router.get('/batches/:id', async (req, res) => {
  res.json(await jobs.batchView(req.shopId, req.params.id, { after: req.query?.after || '', base: BASE }));
});

router.post('/batches/:id/action', write, async (req, res) => {
  res.json(await jobs.batchAction(req.shopId, req.user.id, req.params.id, String(req.body?.action || ''), clientIp(req)));
});

router.get('/images/:id/versions', async (req, res) => {
  res.json(await jobs.versions(req.shopId, req.params.id, BASE));
});

router.post('/images/:id/versions/:version/review', write, async (req, res) => {
  res.json(await jobs.review(req.shopId, req.user.id, req.params.id, req.params.version, req.body || {}, clientIp(req)));
});

module.exports = router;
