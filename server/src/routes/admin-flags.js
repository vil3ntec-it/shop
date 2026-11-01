'use strict';
/**
 * پنلِ مرکزی، فاز ۳ — پرچم‌ها و «تنظیماتِ زنده» (`live_config`) با تاریخچه (ماژولِ ۴.۱۲ ِ ممیزی).
 *
 *   GET    /api/admin/flags?scope=all            مقدارهای یک scope + کاتالوگِ پرچم‌های شناخته
 *   GET    /api/admin/flags/history?scope=&key=&limit=&before=   چه کسی، کی، پیشین ⇒ تازه، دلیل
 *   PUT    /api/admin/flags   {scope, key, value, reason}         فقط مدیرِ ارشد، دلیل اجباری
 *   DELETE /api/admin/flags   {scope, key, reason}                فقط مدیرِ ارشد، دلیل اجباری
 *
 * ⛔ همان جدول و همان کتابخانهٔ `PUT /api/admin/pump/live-config` — نه دفترِ دوم. تاریخچه در
 *    `lib/live-config.js` و در همان تراکنشِ نوشتن است، پس درِ پمپ و کانالِ آزمایشی هم رد می‌گذارند.
 * ⛔ کاتالوگ فقط «شرح و پیش‌فرض» است (از جدولِ «نقشهٔ پرچم‌ها» ِ `docs/RULES-fa.md`)؛ مقدار همیشه از
 *    `live_config` می‌آید. کلیدی که نوشته نشده `value: null, set: false` است — پیش‌فرض را پنل نشان می‌دهد.
 */
const express = require('express');
const liveConfig = require('../lib/live-config');
const audit = require('../lib/audit');
const svc = require('../lib/admin-service');
const { requireSuperAdmin } = require('../middleware/auth');
const { requireReason } = require('../lib/admin-access');
const { clientIp } = require('../middleware/ratelimit');
const v = require('../lib/validate');

const router = express.Router();

/** پرچم‌های شناخته — scope: «shop» = شناسهٔ دکان یا all؛ «all» = فقط all. */
const KNOWN = Object.freeze([
  { key: 'shop.orders', scope: 'shop', default: false, what: 'سفارشِ آنلاین (`/api/shop/orders/*`)' },
  { key: 'shop.storefront', scope: 'shop', default: false, what: 'ویترینِ عمومی و مدیریتِ ویترین' },
  { key: 'shop.delivery', scope: 'shop', default: false, what: 'تحویل و پیک' },
  { key: 'shop.orders.refunds', scope: 'shop', default: false, what: 'بازپرداختِ سفارش با دفتر' },
  { key: 'shop.chat', scope: 'shop', default: false, what: 'گفت‌وگو با مشتری' },
  { key: 'shop.image_bank', scope: 'shop', default: false, what: 'بانکِ تصاویرِ دکان' },
  { key: 'image_bank.central', scope: 'all', default: false, what: 'پنلِ بانکِ تصاویرِ مدیر' },
  { key: 'hashchain.audit', scope: 'security', default: false, what: 'زنجیرهٔ هشِ سابقه' },
  { key: 'beta.shops', scope: 'release', default: [], what: 'دکان‌های کانالِ آزمایشی' },
  { key: 'beta.devices', scope: 'release', default: [], what: 'دستگاه‌های کانالِ آزمایشی' },
  { key: 'shop.receipt_v2', scope: 'shop', default: false, what: 'فاکتورِ نسخهٔ ۲' },
  { key: 'shop.promo_stamp', scope: 'shop', default: false, what: 'نشانِ تخفیفِ خودکار روی فاکتور' },
  { key: 'market.directory', scope: 'shop', default: false, what: 'فهرستِ بازار' },
]);

router.get('/', async (req, res) => {
  const scope = String(req.query?.scope || 'all');
  const out = await liveConfig.list(scope);
  res.json({ ...out, known: KNOWN, reserved: liveConfig.RESERVED });
});

router.get('/history', async (req, res) => {
  res.json({
    entries: await liveConfig.history({
      scope: req.query?.scope ? String(req.query.scope) : '',
      key: req.query?.key ? String(req.query.key) : '',
      limit: v.integer(req.query?.limit, { min: 1, max: 200, def: 50 }),
      before: v.integer(req.query?.before, { min: 0, max: Number.MAX_SAFE_INTEGER, def: 0 }),
    }),
  });
});

router.put('/', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const b = req.body || {};
  const out = await liveConfig.set(b.scope || 'all', b.key, b.value, svc.label(req), { reason, actor: svc.actorRecord(req) });
  await audit.log({
    actorType: 'admin', userId: req.admin.id, action: 'admin.flag_set', ip: clientIp(req),
    targetType: 'live_config', targetId: `${out.scope}:${out.key}`, detail: { scope: out.scope, key: out.key, reason },
  });
  res.json({ ok: true, ...out });
});

router.delete('/', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const b = { ...(req.query || {}), ...(req.body || {}) };
  const out = await liveConfig.remove(b.scope || 'all', b.key, svc.label(req), { reason, actor: svc.actorRecord(req) });
  if (out.removed) {
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.flag_removed', ip: clientIp(req),
      targetType: 'live_config', targetId: `${out.scope}:${out.key}`, detail: { scope: out.scope, key: out.key, reason },
    });
  }
  res.json({ ok: true, ...out });
});

module.exports = router;
module.exports.KNOWN = KNOWN;
