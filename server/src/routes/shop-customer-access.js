'use strict';
/**
 * CRM-15 — «چه کسی این پرونده را دید» برای صاحبِ دکان (`lib/customer-access.js`):
 *
 *   GET /api/shop/customer-access?customerId=<شناسهٔ پرونده>&limit=
 *     ⇒ `{ customerId, entries: [{ id, viewerId, viewerName, role, device, kind, door, firstAt, lastAt, count, all? }], retentionDays }`
 *       (`all: true` = خروجیِ برگهٔ همهٔ مشتریان، `*`)
 *
 *   POST /api/shop/customer-access   { customerId, kind: 'detail'|'phone'|'export' }
 *     ⇒ `{ recorded: true, kind }` — دیدنی که برنامه از **دفترِ خودش** گزارش می‌دهد (درِ `app`، مهاجرتِ ۶۱۰): وب
 *       (`license/customer-access.js`) و نقشی که `auditLog` نمی‌نویسد (حسابدار؛ `CustomerAccessNote` ِ برنامه).
 *
 * فهرست درهای سرور و درِ `app` را دارد؛ دیدنِ پرونده روی دستگاهِ نویسنده (بی‌اینترنت) ردیفِ `customer_view` ِ
 * `auditLog` ِ دفتر است و برنامه آن را از دفترِ خودش کنارِ این فهرست می‌گذارد.
 *
 * ⛔ GET: `audit.view` (صاحب همیشه؛ پیش‌فرض مدیر و حسابدار، لایهٔ صاحب می‌بندد) — شاگرد ۴۰۳.
 * ⛔ POST: هر عضوِ دکان (هر که پرونده را می‌بیند)؛ بیننده، نقش، دکان و دستگاه فقط از توکن، بدنه فقط شناسه و نوع؛
 *    `phone` ِ اصلِ بی `customers.contact` ⇐ `detail` (`lib/customer-access.noteApp`).
 * ⛔ دکان فقط از توکن (`requireShop`)؛ شناسهٔ دکانِ دیگر یا پروندهٔ دکانِ دیگر ⇒ فهرستِ خالی، بی نشت.
 * ⛔ کلیدِ کاتالوگ و پرچم ندارد: سپرِ حریمِ خصوصی است، نه قابلیتِ پولی؛ پس از پایانِ اشتراک هم باز است (SUB-10:
 *    POST در دستهٔ FREE).
 * ⛔ سقف: سطلِ IP (`customer-access-ip`) پیش از احراز؛ پس از آن سطلِ دکان برای GET و سطلِ هر کاربر برای POST.
 *
 *   POST /api/shop/customer-access/offline   { notes: [{ id, customerId, kind, at }] } (≤ ۵۰) — CRM-15 (د۱۸): دیدن‌های صفِ
 *     آفلاینِ برنامه با زمانِ خودشان (`noteOffline`)؛ پرچمِ خاموشِ `shop.customer_access_offline` ⇒ ۴۰۴ (برنامه صف را دور
 *     می‌ریزد = همان امروز)؛ همان سطلِ هر کاربرِ POST.
 */
const express = require('express');
const config = require('../config');
const access = require('../lib/customer-access');
const v = require('../lib/validate');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound, badRequest } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'customer-access-ip' }));
const limit = rateLimit({ max: 120, keyPrefix: 'customer-access', key: (req) => (req.shopId ? `shop:${req.shopId}` : '') });
const noteLimit = rateLimit({
  max: 120, keyPrefix: 'customer-access-note', key: (req) => (req.shopId && req.user ? `shop:${req.shopId}:${req.user.id}` : ''),
});
router.use(requireUser, requireShop);

//  CRM-15 (د۱۸): دیدن‌های صفِ آفلاینِ برنامه (حسابدارِ بی‌اینترنت) — پرچمِ خاموشِ `shop.customer_access_offline` پیش از بدنه
router.post('/offline', noteLimit, async (req, res, next) => {
  if (!(await require('../lib/orders').flagOf(req.shopId, access.OFFLINE_FLAG))) {
    return next(notFound('ثبتِ دیدنِ بی‌اینترنت روشن نیست', 'customer_access_offline_off'));
  }
  const notes = Array.isArray(req.body?.notes) ? req.body.notes : null;
  if (!notes || notes.length > access.OFFLINE_MAX) return next(badRequest('فهرستِ دیدن‌ها درست نیست', 'bad_notes'));
  const results = await access.noteOffline(req, notes);
  res.set('Cache-Control', 'no-store');
  res.json({ results });
});

router.post('/', noteLimit, async (req, res) => {
  const b = req.body && typeof req.body === 'object' && !Array.isArray(req.body) ? req.body : {};
  const out = await access.noteApp(req, { customerId: b.customerId, kind: b.kind });
  res.set('Cache-Control', 'no-store');
  res.json({ recorded: true, kind: out.kind });
});

router.get('/', limit, requirePermission('audit.view'), async (req, res) => {
  const raw = req.query?.customerId;
  const customerId = typeof raw === 'string' ? access.refOf(raw) : null;
  const max = v.integer(req.query?.limit, { min: 1, max: 200, def: 100, field: 'limit' });
  const entries = customerId ? await access.list(req.shopId, customerId, { limit: max }) : [];
  res.set('Cache-Control', 'no-store');
  res.json({ customerId: customerId || '', entries, retentionDays: access.retentionDays() });
});

module.exports = router;
