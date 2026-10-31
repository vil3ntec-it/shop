'use strict';
/**
 * LOC-04 — `GET /api/calendar/holidays`: جدولِ تعطیلاتِ رسمی برای همهٔ برنامه‌ها.
 *
 * بی‌توکن: تعطیلاتِ رسمی دادهٔ همگانی است و برنامه پیش از ورود هم روزِ کاری را
 * می‌خواهد. فقط خواندن است؛ نوشتن در `routes/admin-calendar.js` (مدیرِ ارشد).
 * `ETag` همان نسخهٔ جدول است، پس برنامه با `If-None-Match` ِ ارزان می‌پرسد.
 *
 *   ?from=YYYY-MM-DD&to=YYYY-MM-DD   (اختیاری) فقط همان بازه
 */
const express = require('express');
const holidays = require('../lib/holidays');

const router = express.Router();

router.get('/holidays', async (req, res, next) => {
  try {
    const from = typeof req.query?.from === 'string' ? req.query.from : '';
    const to = typeof req.query?.to === 'string' ? req.query.to : '';
    const doc = await holidays.get();
    const list = holidays.range(doc, { from, to });
    const etag = `W/"hol-${doc.version}-${from}-${to}"`;
    res.set('ETag', etag);
    res.set('Cache-Control', 'public, max-age=3600');
    if (req.get('If-None-Match') === etag) return res.status(304).end();
    res.json({ version: doc.version, updatedAt: doc.updatedAt, holidays: list });
  } catch (err) { next(err); }
});

module.exports = router;
