'use strict';
/**
 * LOC-04 — `GET /api/calendar/holidays`: جدولِ تعطیلاتِ رسمی برای همهٔ برنامه‌ها.
 *
 * بی‌توکن: تعطیلاتِ رسمی دادهٔ همگانی است و برنامه پیش از ورود هم روزِ کاری را
 * می‌خواهد. فقط خواندن است؛ نوشتن در `routes/admin-calendar.js` (مدیرِ ارشد).
 * `ETag` همان نسخهٔ جدول است، پس برنامه با `If-None-Match` ِ ارزان می‌پرسد.
 * برنامه‌های بومی روزی یک بار می‌گیرند (`core/calendar/Holidays.due`).
 *
 *   ?from=YYYY-MM-DD&to=YYYY-MM-DD   (اختیاری) فقط همان بازه
 *
 * ⛔ سقفِ IP پیش از هر کار (`calendar-ip`، ۱۲۰ در دقیقه): پشتِ NAT ِ اپراتور هزاران دستگاه یک IP اند ولی
 *    هر کدام روزی یک بار می‌پرسد؛ پاسخ هم از پایگاه است، پس بی سقف درِ همگانیِ ارزانِ پایگاه نمی‌شود.
 */
const express = require('express');
const holidays = require('../lib/holidays');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'calendar-ip' }));

/** `If-None-Match` می‌تواند چند ETag (با ویرگول) یا `*` باشد */
function matches(header, etag) {
  if (typeof header !== 'string' || !header) return false;
  return header.split(',').some((t) => { const v = t.trim(); return v === '*' || v === etag; });
}

router.get('/holidays', async (req, res, next) => {
  try {
    const from = typeof req.query?.from === 'string' ? req.query.from : '';
    const to = typeof req.query?.to === 'string' ? req.query.to : '';
    const doc = await holidays.get();
    const list = holidays.range(doc, { from, to });
    const etag = `W/"hol-${doc.version}-${from}-${to}"`;
    res.set('ETag', etag);
    res.set('Cache-Control', 'public, max-age=3600');
    if (matches(req.get('If-None-Match'), etag)) return res.status(304).end();
    res.json({ version: doc.version, updatedAt: doc.updatedAt, holidays: list });
  } catch (err) { next(err); }
});

module.exports = router;
