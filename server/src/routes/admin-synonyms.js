'use strict';
/**
 * ۴-۱ «ب» — فهرستِ مرکزیِ مترادف برای جست‌وجو، سمتِ مدیر (توانایی ‎synonyms.v1‎ ِ پلِ پنل، `console-synonyms.js` ِ ریپوی server).
 *
 *   GET /api/admin/search/synonyms   ⇒ { version, updatedAt, groups }                     هر نقشِ مدیر
 *   PUT /api/admin/search/synonyms   { groups, version, reason } ⇒ همان + unchanged        فقط ارشد + دلیل (۳ تا ۲۰۰)
 *
 * ⛔ زیرِ `requireAdmin` ِ `routes/admin.js` (کنارِ `admin-search.js` روی `/search`)؛ سقفِ IP پیش از احراز همان‌جا
 *    (`admin-synonyms-ip`). نقشِ مالی به این در نمی‌رسد (`lib/admin-roles.js`)؛ بیننده فقط می‌خواند.
 * ⛔ `Idempotency-Key` ِ اختیاری (`lib/admin-write-once.js`): همان کلید + همان بدنه ⇒ همان پاسخ؛ بی‌کلید هم تکرارِ همان فهرست
 *    `unchanged` است، پس کارِ دوم و سابقهٔ دوم نیست. منطق و سنجش: `lib/search-synonyms.js`.
 */
const express = require('express');
const synonyms = require('../lib/search-synonyms');
const writeOnce = require('../lib/admin-write-once');
const { requireReason } = require('../lib/admin-access');
const { clientIp } = require('../middleware/ratelimit');
const { requireSuperAdmin } = require('../middleware/auth');

const router = express.Router();

router.get('/synonyms', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(await synonyms.get());
});

router.put('/synonyms', requireSuperAdmin, async (req, res) => {
  const reason = requireReason(req);
  synonyms.validate(req.body?.groups);   //  شکلِ ورودی پیش از نشانِ کلید (درخواستِ ردشده نشانی نمی‌گذارد)
  const out = await writeOnce.writeOnce(req, { resultId: synonyms.KEY }, () => synonyms.replace(
    req.body?.groups, req.body?.version, { userId: req.admin.id, reason, ip: clientIp(req) }));
  writeOnce.send(res, out);
});

module.exports = router;
