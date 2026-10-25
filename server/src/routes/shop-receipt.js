'use strict';
/**
 * سربرگِ فاکتورِ دکان — `/api/shop/receipt-profile` (فاکتورِ نسخهٔ ۲). منطق در
 * `lib/receipt-profile.js`.
 *
 *   GET    /config       هر عضو ⇒ { enabled } — پرچمِ `shop.receipt_v2` (live_config، پیش‌فرض خاموش):
 *                         تا روشن نشده برنامه همان فاکتورِ پیشین را می‌زند، توکن نمی‌سازد و سربرگ نمی‌پرسد
 *   GET    /             هر عضو ⇒ { profile, rev } (دستگاه برای چاپِ بی اینترنت نگه می‌دارد)
 *   PUT    /             `settings.write` ⇒ نام، نشانی، تلفن، متنِ پایین، qr، logoMode، digits، symbol
 *   GET    /logo         هر عضو ⇒ بایت‌های لوگو (ETag = hash)
 *   PUT    /logo         `settings.write` ⇒ بدنهٔ خامِ عکس (JPG/PNG/WebP، ≤ ۲۰۰ KB)، ۲۰ بار در شبانه‌روز
 *   DELETE /logo         `settings.write`
 */
const express = require('express');
const config = require('../config');
const profiles = require('../lib/receipt-profile');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop);

const write = requirePermission('settings.write');
//  سقفِ شبانه‌روزیِ بارگذاری به‌ازای دکان (نه کاربر یا IP)
const logoLimit = rateLimit({
  max: config.rateLimit.receiptLogoDailyMax, windowMs: 24 * 60 * 60 * 1000,
  keyPrefix: 'receipt-logo-day', key: (req) => req.shopId,
});
const rawLogo = express.raw({ type: () => true, limit: profiles.LOGO_MAX_BYTES + 1024 });

const logged = (req, action, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'shop', targetId: req.shopId, detail, ip: clientIp(req),
});

router.get('/config', async (req, res) => {
  res.json({ enabled: await profiles.enabledFor(req.shopId) });
});

router.get('/', async (req, res) => {
  res.json(await profiles.read(req.shopId));
});

router.put('/', write, async (req, res) => {
  const out = await profiles.update(req.shopId, req.body);
  await logged(req, 'receipt.profile', { rev: out.rev, qr: out.profile.qr });
  res.json(out);
});

router.get('/logo', async (req, res) => {
  const logo = await profiles.logoOf(req.shopId);
  if (!logo) throw notFound('لوگویی گذاشته نشده است', 'logo_not_found');
  const etag = `"${logo.hash}"`;
  res.set('ETag', etag).set('Cache-Control', 'private, no-cache');
  if (req.get('if-none-match') === etag) return res.status(304).end();
  res.type(logo.mime).send(logo.data);
});

router.put('/logo', write, logoLimit, rawLogo, async (req, res) => {
  const out = await profiles.putLogo(req.shopId, req.body);
  await logged(req, 'receipt.logo', { hash: out.profile.logo.hash, rev: out.rev });
  res.json(out);
});

router.delete('/logo', write, async (req, res) => {
  const out = await profiles.removeLogo(req.shopId);
  await logged(req, 'receipt.logo_removed', { rev: out.rev });
  res.json(out);
});

module.exports = router;
