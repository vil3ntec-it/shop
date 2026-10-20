'use strict';
/**
 * بانکِ تصاویر — درِ دکان (v3 ۳۷.۵ تا ۳۷.۹). شرح: ‎lib/image-bank.js‎.
 *
 *   GET  /config                      پرچم، اندازه‌ها، مرحله‌های پردازش
 *   GET  /search?q=&barcode=&…        جست‌وجوی بانکِ مرکزی (صفحه‌ای، بی فایلِ اصل)
 *   POST /select                      «انتخاب از بانک» / عکسِ خود / بی عکس
 *   GET  /products/:productId         کدام عکس نمایش داده می‌شود و چرا
 *   GET  /mine                        عکس‌های اختصاصیِ همین دکان
 *   POST /                            بارگذاریِ عکسِ اختصاصی (بایت‌ها در بدنه)
 *   POST /:id/versions                نسخهٔ تازهٔ عکسِ خود (اصل دست نمی‌خورد)
 *   PUT  /:id/v/:version/:size        نسخهٔ کوچکِ ساختهٔ برنامه
 *   POST /:id/propose                 پیشنهاد به بانکِ مرکزی — فقط صاحبِ دکان، با اجازهٔ صریح
 *   POST /:id/report                  «عکسِ اشتباه / بستهٔ قدیمی»
 *   POST /:id/process                 مرحلهٔ پردازش — بیرونی‌ها ۵۰۳ و بی هیچ ارسالی
 *   GET  /file/:id/:version/:size     خودِ فایل (دسترسی برای هر درخواست سنجیده می‌شود)
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.image_bank` همه‌چیز ۴۰۳ است و هیچ ردیفی
 *    نوشته نمی‌شود. عکسِ همگام‌شدهٔ امروز (`/api/sync/v1/photos`) دست نمی‌خورد.
 * ⛔ خواندنِ عکسِ خصوصیِ دکانِ دیگر با عوض‌کردنِ شناسه ⇒ همان ۴۰۴ِ «نیست» و یک
 *    ردیفِ سابقهٔ امنیتی (IMG-BANK-12).
 */
const express = require('express');
const bank = require('../lib/image-bank');
const pipeline = require('../lib/image-pipeline');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden, notFound, badRequest } = require('../middleware/errors');
const config = require('../config');

const router = express.Router();
const BASE = '/api/shop/images';
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const body = (max) => express.raw({ type: () => true, limit: max + 1024 });

router.use(requireUser, requireShop, requirePermission('data.read'));
router.use(rateLimit({ max: config.rateLimit.syncMax, keyPrefix: 'imgbank', key: (req) => req.shopId }));
router.use(wrap(async (req, res, next) => {
  if (!(await bank.shopEnabled(req.shopId))) {
    return next(forbidden('بانکِ تصاویر برای این دکان روشن نیست', 'image_bank_disabled'));
  }
  next();
}));

//  A16: نوشتن در بانکِ تصویر یعنی عکسِ کالا ⇐ همان `canWrite(role, 'products')`ِ هسته
const write = [
  requirePermission('data.write'),
  (req, res, next) => (require('../lib/perm-core').canWrite(req.role, 'products')
    ? next() : next(forbidden('عکسِ کالا در حدِ نقشِ شما نیست', 'table_not_allowed'))),
];
const bufOf = (req) => (Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0));
const version = (v) => {
  const n = Number(v);
  if (!Number.isInteger(n) || n < 1 || n > 100000) throw badRequest('نسخه درست نیست', 'bad_version');
  return n;
};

router.get('/config', (req, res) => {
  res.json({ enabled: true, sizes: bank.DERIV, roles: bank.ROLES, report_kinds: bank.REPORT_KINDS, pipeline: pipeline.describe() });
});

router.get('/search', wrap(async (req, res) => {
  res.set('Cache-Control', 'private, no-cache');
  res.json(await bank.search(req.query || {}, { base: BASE }));
}));

router.post('/select', write, wrap(async (req, res) => {
  const out = await bank.select(req.shopId, req.user.id, req.body || {});
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'image.selected', targetType: 'product', targetId: out.product_id,
    ip: clientIp(req), detail: { source: out.source, image_id: out.image_id, version: out.version, match: out.match_status },
  });
  res.json(out);
}));

router.get('/products/:productId', wrap(async (req, res) => {
  res.json(await bank.resolve(req.shopId, req.params.productId, BASE));
}));

router.get('/mine', wrap(async (req, res) => {
  res.json(await bank.shopList(req.shopId, {
    productId: req.query?.product_id || '', cursor: req.query?.cursor || '', limit: req.query?.limit, base: BASE,
  }));
}));

router.post('/', write, body(bank.DERIV.display.bytes), wrap(async (req, res) => {
  const out = await bank.shopUpload(req.shopId, req.user.id, bufOf(req), {
    productId: req.query?.product_id || '', role: req.query?.role || 'main',
  });
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'image.uploaded', targetType: 'image', targetId: out.id, ip: clientIp(req) });
  res.status(201).json(out);
}));

router.post('/:id/versions', write, body(bank.DERIV.display.bytes), wrap(async (req, res) => {
  const out = await bank.shopVersion(req.shopId, req.user.id, req.params.id, bufOf(req));
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'image.version_added', targetType: 'image', targetId: out.image_id, detail: { version: out.version } });
  res.status(201).json(out);
}));

router.put('/:id/v/:version/:size', write, body(bank.DERIV.zoom.bytes), wrap(async (req, res) => {
  res.json(await bank.shopDerivative(req.shopId, req.params.id, version(req.params.version), req.params.size, bufOf(req)));
}));

//  ⛔ اشتراکِ عکسِ دکان با بانک تصمیمِ صاحبِ دکان است (shop.update = فقط owner)
router.post('/:id/propose', requirePermission('shop.update'), wrap(async (req, res) => {
  const out = await bank.propose(req.shopId, req.user.id, req.params.id, req.body || {});
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'image.proposed_to_bank', targetType: 'image', targetId: req.params.id,
    ip: clientIp(req), detail: { pending_id: out.id, consent: true },
  });
  res.status(out.existing ? 200 : 201).json(out);
}));

/** عکسی که این دکان می‌بیند — وگرنه همان ۴۰۴ و سابقهٔ امنیتی. */
async function readable(req, id, ver, size) {
  const img = await bank.imageRow(id);
  if (img && await bank.shopCanRead(req.shopId, img, ver, size)) return img;
  if (img) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'security.image_access_denied', targetType: 'image',
      targetId: img.id, ip: clientIp(req), detail: { space: img.space, size, version: ver },
    });
  }
  throw notFound('عکس پیدا نشد');
}

router.post('/:id/report', write, wrap(async (req, res) => {
  const img = await bank.imageRow(req.params.id);
  const ok = img && await readable(req, img.id, Number(img.current_version), 'thumb');
  if (!ok) throw notFound('عکس پیدا نشد');
  res.status(201).json(await bank.report(req.shopId, req.user.id, img, req.body || {}));
}));

router.post('/:id/process', write, wrap(async (req, res) => {
  const step = String(req.body?.step || '');
  //  مرحلهٔ بیرونی/ناموجود پیش از هر خواندنی رد می‌شود: هیچ بایتی جابه‌جا نمی‌شود
  const s = pipeline.stepOf(step);
  if (!s || s.status !== 'available') pipeline.run(step, Buffer.alloc(0));
  const img = await bank.imageRow(req.params.id);
  if (!img || img.space !== 'shop' || img.owner_shop_id !== req.shopId) throw notFound('عکس پیدا نشد');
  const file = await bank.readFile(img.id, Number(img.current_version), 'original');
  if (step === 'strip_metadata') {
    const out = await bank.shopVersion(req.shopId, req.user.id, img.id, pipeline.run(step, file.data));
    return res.status(201).json({ step, ...out });
  }
  const result = pipeline.run(step, file.data);
  res.json({ step, result });
}));

router.get('/file/:id/:version/:size', wrap(async (req, res) => {
  const size = String(req.params.size);
  if (!bank.SIZES.includes(size)) throw notFound('عکس پیدا نشد');
  const ver = version(req.params.version);
  const img = await readable(req, req.params.id, ver, size);
  const file = await bank.readFile(img.id, ver, size);
  if (!file) throw notFound('این اندازه هنوز ساخته نشده', 'no_derivative');
  //  نشانی نسخه‌دار و نسخهٔ کوچک یک‌بارنویس ⇒ cache بی‌خطر؛ خصوصی، نه CDNِ عمومی
  res.set('Cache-Control', 'private, max-age=31536000, immutable');
  res.set('ETag', '"' + file.hash + '"');
  res.set('Content-Type', file.mime);
  res.set('X-Content-Type-Options', 'nosniff');
  if (req.headers['if-none-match'] === '"' + file.hash + '"') return res.status(304).end();
  res.send(file.data);
}));

module.exports = router;
