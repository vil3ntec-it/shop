'use strict';
/**
 * CRM-15 — «چه کسی این پرونده را دید» برای صاحبِ دکان (`lib/customer-access.js`):
 *
 *   GET /api/shop/customer-access?customerId=<شناسهٔ پرونده>&limit=
 *     ⇒ `{ customerId, entries: [{ id, viewerId, viewerName, role, device, kind, door, firstAt, lastAt, count }], retentionDays }`
 *
 * فقط خواندنِ درهای سرور است؛ دیدنِ پرونده روی دستگاه (بی‌اینترنت) ردیفِ `customer_view` ِ `auditLog` ِ دفتر است
 * و برنامه آن را از دفترِ خودش کنارِ این فهرست می‌گذارد.
 *
 * ⛔ `audit.view` (صاحب همیشه؛ پیش‌فرض مدیر و حسابدار، لایهٔ صاحب می‌بندد) — شاگرد ۴۰۳.
 * ⛔ دکان فقط از توکن (`requireShop`)؛ شناسهٔ دکانِ دیگر یا پروندهٔ دکانِ دیگر ⇒ فهرستِ خالی، بی نشت.
 * ⛔ کلیدِ کاتالوگ و پرچم ندارد: سپرِ حریمِ خصوصی است، نه قابلیتِ پولی؛ پس از پایانِ اشتراک هم باز است (SUB-10:
 *    فقط‌خواندنی، پس در دسته‌بندیِ نوشتنی‌ها نیست).
 * ⛔ سقف: سطلِ IP (`customer-access-ip`) پیش از احراز و سطلِ دکان پس از آن.
 */
const express = require('express');
const config = require('../config');
const access = require('../lib/customer-access');
const v = require('../lib/validate');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'customer-access-ip' }));
const limit = rateLimit({ max: 120, keyPrefix: 'customer-access', key: (req) => (req.shopId ? `shop:${req.shopId}` : '') });
router.use(requireUser, requireShop, limit, requirePermission('audit.view'));

router.get('/', async (req, res) => {
  const raw = req.query?.customerId;
  const customerId = typeof raw === 'string' ? access.refOf(raw) : null;
  const max = v.integer(req.query?.limit, { min: 1, max: 200, def: 100, field: 'limit' });
  const entries = customerId ? await access.list(req.shopId, customerId, { limit: max }) : [];
  res.set('Cache-Control', 'no-store');
  res.json({ customerId: customerId || '', entries, retentionDays: access.retentionDays() });
});

module.exports = router;
