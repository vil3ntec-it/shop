'use strict';
/**
 * دو درِ قراردادِ پلِ پنل (ممیزیِ پنلِ مرکزی §۹؛ سوار در `routes/admin.js` پشتِ `requireAdmin` و سقفِ IP ِ پیش از آن):
 *
 *   GET /api/admin/contract                 S18 — نسخه، اثرِ انگشتِ فهرست، توانایی‌ها و درهای مدیر (`lib/admin-capabilities.js`)
 *   GET /api/admin/subscriptions/period-end S15 — پایانِ دورهٔ «مقدار + واحد» با **همان** تابعِ اعطا (`subscriptions.periodEnd`)
 *        ?app=shop|pump (پیش‌فرض دکان) &amount=1… &unit=day|week|month|year &from=<ms، اختیاری>
 *
 * هر دو فقط می‌خوانند (هیچ دادهٔ حسابی، هیچ نوشتنی، هیچ سابقه‌ای) و برای هر نقشِ مدیر بازند (`lib/admin-roles.js`).
 * ⛔ «از» هرگز در گذشته نیست: پایه = بیشینهٔ «از» و همین حالا — همان قاعدهٔ اعطا و تمدید (دوره از گذشته شروع نمی‌شود).
 * ⛔ ورودی سخت‌گیر: رقمِ ساده بی صفرِ آغاز؛ «1.5»، «1e1»، «01» و پارامترِ تکراری ۴۰۰ — نه گرد کردنِ بی‌صدا.
 */
const express = require('express');
const { now } = require('../db');
const subs = require('../lib/subscriptions');
const tenancy = require('../lib/tenancy');
const { contract } = require('../lib/admin-capabilities');
const { badRequest } = require('../middleware/errors');

const router = express.Router();

router.get('/contract', (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(contract());
});

/** سقفِ هر واحد — همان بازهٔ «روزها»ی اعطا (۳۶۵۰ روز). */
const MAX = Object.freeze({ day: 3650, week: 520, month: 120, year: 10 });
const DIGITS = /^[1-9]\d{0,12}$/;
const LATEST = 4102444800000;   //  همان سقفِ `v.timestamp` (سالِ ۲۱۰۰)

router.get('/subscriptions/period-end', (req, res, next) => {
  const q = req.query || {};
  const app = q.app === undefined || q.app === '' ? tenancy.SHOP.app : tenancy.sectionOf(typeof q.app === 'string' ? q.app : '');
  if (!app) return next(badRequest('بخش فقط دکان یا پمپ است', 'bad_app'));
  const unit = typeof q.unit === 'string' && Object.hasOwn(MAX, q.unit) ? q.unit : '';
  if (!unit) return next(badRequest('واحد فقط روز، هفته، ماه یا سال است', 'bad_unit'));
  const amount = typeof q.amount === 'string' && DIGITS.test(q.amount) ? Number(q.amount) : 0;
  if (!amount || amount > MAX[unit]) return next(badRequest(`مقدار عددِ درستِ ۱ تا ${MAX[unit]} است`, 'bad_amount'));
  let from = null;
  if (q.from !== undefined && q.from !== '') {
    from = typeof q.from === 'string' && /^\d{1,13}$/.test(q.from) ? Number(q.from) : -1;
    if (from < 0 || from > LATEST) return next(badRequest('«از» زمانِ میلی‌ثانیه‌ای است', 'bad_from'));
  }
  const t = now();
  const base = Math.max(from ?? t, t);
  const T = tenancy.byApp(app);
  const endsAt = subs.periodEnd(T, base, amount, unit);
  //  منطقهٔ نادرست در `DEFAULT_TIMEZONE` ⇒ `periodEnd` همان UTC را داد؛ پاسخ هم همان را بگوید
  const zone = require('../lib/finance-window').tz();
  let local = T === tenancy.SHOP;
  try { if (local) new Intl.DateTimeFormat('en-US', { timeZone: zone }); } catch { local = false; }
  res.json({
    app, amount, unit, base, endsAt,
    rule: local ? 'calendar_local' : 'calendar_utc',
    zone: local ? zone : 'UTC',
    serverTime: t,
  });
});

module.exports = router;
