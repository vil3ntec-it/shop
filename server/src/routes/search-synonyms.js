'use strict';
/**
 * POS-04 (۴-۱ «ب») — `GET /api/search/synonyms`: فهرستِ مرکزیِ مترادف برای جست‌وجوی **صندوقِ برنامه‌ها**.
 *
 * همان سندِ `lib/search-synonyms.js` ({ version, updatedAt, groups }) که ویترین با پرچمِ `shop.storefront.synonyms` در SQL به
 * کار می‌برد و صاحبِ پلتفرم در پنلِ مرکزی نگه می‌دارد (نوشتن فقط `routes/admin-synonyms.js`). برنامه روزی یک بار می‌گیرد
 * (`core/text/SynonymList`، `TillSynonyms`) و صندوق بی شبکه هم با نسخهٔ ذخیره‌شده می‌گردد؛ فهرستِ تهی = همان جست‌وجوی پیشین.
 *
 * بی‌توکن، مثلِ `GET /api/calendar/holidays`: گروه‌های واژه دادهٔ همگانی‌اند (نه نام، نه دکان، نه شمار) و برنامه پیش از
 * ورود هم صندوق دارد. فقط خواندن. `ETag` همان نسخه است ⇒ پرسشِ ارزانِ `If-None-Match`.
 * ⛔ سقفِ IP پیش از هر کار (`search-synonyms-ip`، ۱۲۰ در دقیقه)؛ پاسخ از پایگاه است.
 */
const express = require('express');
const synonyms = require('../lib/search-synonyms');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'search-synonyms-ip' }));

/** `If-None-Match` می‌تواند چند ETag (با ویرگول) یا `*` باشد */
function matches(header, etag) {
  if (typeof header !== 'string' || !header) return false;
  return header.split(',').some((t) => { const v = t.trim(); return v === '*' || v === etag; });
}

router.get('/synonyms', async (req, res, next) => {
  try {
    const doc = await synonyms.get();
    const etag = `W/"syn-${doc.version}"`;
    res.set('ETag', etag);
    res.set('Cache-Control', 'public, max-age=3600');
    if (matches(req.get('If-None-Match'), etag)) return res.status(304).end();
    res.json({ version: doc.version, updatedAt: doc.updatedAt, groups: doc.groups });
  } catch (err) { next(err); }
});

module.exports = router;
