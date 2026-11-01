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
const db = require('../db');
const { badRequest } = require('../middleware/errors');
//  دامنهٔ پرچمی که برای «همه یا یک دکان» است (`live_config.scope`) — نامِ دامنه، نه مقایسهٔ برنامه
const SHOP_SCOPE = 'shop';

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
  { key: 'shop.storefront.filters', scope: 'shop', default: false, what: 'فیلترِ کاتالوگِ ویترین (برند، قیمت، موجود، مشخصه)' },
  { key: 'shop.storefront.alerts', scope: 'shop', default: false, what: '«خبرم کن وقتی موجود/ارزان شد» ِ ویترین' },
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

/*
 *  ⛔ پوستهٔ کنسول (۴.۱۲): پرچمِ **شناخته** فقط مقدارِ هم‌نوعِ پیش‌فرضش را می‌پذیرد و فقط در scope ِ خودش —
 *  «true» ِ رشته‌ای یا پرچمِ دکان روی scope ِ امنیت، بی‌صدا هیچ اثری نداشت (برنامه فقط بولی می‌خواند) و پنل
 *  «روشن» نشان می‌داد. کلیدِ ناشناخته همان رفتارِ پیشین را دارد (هر JSON) تا هیچ نویسندهٔ امروزی نشکند.
 */
const ID_RE = /^[A-Za-z0-9_-]{1,80}$/;
async function checkKnown(scope, key, value) {
  const k = KNOWN.find((x) => x.key === String(key || '').trim());
  if (!k) return;
  const s = String(scope || 'all').trim();
  const scopeOk = k.scope === SHOP_SCOPE ? (s === 'all' || (ID_RE.test(s) && await db.one('SELECT 1 AS x FROM shops WHERE id=$1', [s])))
    : k.scope === 'all' ? s === 'all' : s === k.scope;
  if (!scopeOk) {
    const want = k.scope === SHOP_SCOPE ? '«all» یا شناسهٔ یک دکان' : `«${k.scope}»`;
    throw badRequest(`پرچمِ ${k.key} فقط در scope ِ ${want} معنا دارد`, 'bad_flag_scope');
  }
  const typeOk = Array.isArray(k.default)
    ? Array.isArray(value) && value.length <= 500 && value.every((x) => typeof x === 'string' && ID_RE.test(x))
    : typeof value === typeof k.default;
  if (!typeOk) {
    const want = Array.isArray(k.default) ? 'فهرستی از شناسه‌ها' : typeof k.default === 'boolean' ? 'true یا false' : typeof k.default;
    throw badRequest(`مقدارِ ${k.key} باید ${want} باشد`, 'bad_flag_value');
  }
}

router.put('/', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  const b = req.body || {};
  await checkKnown(b.scope || 'all', b.key, b.value);
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
