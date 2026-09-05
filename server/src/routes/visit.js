'use strict';
/**
 * تپشِ بازدید — «من آمدم».
 *
 * ── چرا بی‌توکن ────────────────────────────────────────────────────
 * تمام نکته‌ی این مسیر همان کسی است که هنوز حساب ندارد. اگر توکن
 * می‌خواست، دقیقاً کسانی را می‌شمرد که از قبل شمرده شده بودند.
 *
 * ── چه چیزی برمی‌گردد ──────────────────────────────────────────────
 * پاسخ عمداً کوچک است و چیزی درباره‌ی بقیه نمی‌گوید: فقط ساعت سرور و
 * اینکه پیام پشتیبانیِ خوانده‌نشده دارد یا نه. همین برای برنامه بس است
 * که نقطه‌ی قرمز را نشان بدهد بی‌آنکه هر چند ثانیه کل چت را بکشد.
 */
const express = require('express');
const { one, now } = require('../db');
const v = require('../lib/validate');
const geo = require('../lib/geo');
const visitors = require('../lib/visitors');
const tokens = require('../lib/tokens');
const { membershipOf } = require('../lib/shops');
const { rateLimit, clientIp } = require('../middleware/ratelimit');

const router = express.Router();

//  تپش زیاد می‌آید، ولی نه بی‌نهایت
const visitLimit = rateLimit({ max: 120, keyPrefix: 'visit' });

router.post('/', visitLimit, async (req, res, next) => {
  try {
    const deviceUid = v.id(req.body?.device?.uid || req.body?.deviceUid || '', {
      field: 'شناسه دستگاه', required: false, max: 64,
    });
    if (!deviceUid) return res.json({ ok: true, serverTime: now() });

    //  توکن اگر بود، بازدید به همان حساب می‌چسبد — ولی نبودنش خطا نیست
    const header = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
    let userId = '';
    let shopId = '';
    let name = '';
    if (header) {
      const row = await tokens.verify(header, 'access');
      if (row) {
        const user = await one('SELECT * FROM users WHERE id=$1', [row.subject_id]);
        if (user) {
          userId = user.id;
          name = user.name || '';
          const member = await membershipOf(user.id);
          if (member) shopId = member.shop_id;
        }
      }
    }

    const ip = clientIp(req);
    const location = req.body?.location && typeof req.body.location === 'object'
      ? req.body.location : null;

    const visitor = await visitors.touch({
      app: v.text(req.body?.app, { max: 40 }) || 'shop',
      deviceUid,
      platform: v.text(req.body?.platform, { max: 20 }),
      appVersion: v.text(req.body?.version, { max: 30 }),
      userId, shopId,
      name: name || v.text(req.body?.name, { max: 80 }),
      ip,
      userAgent: String(req.headers['user-agent'] || '').slice(0, 300),
      language: v.text(req.body?.language, { max: 20 }),
      location,
    });

    //  لوکیشن در جدول خودش هم می‌نشیند تا تاریخچه‌اش بماند — همان جایی
    //  که صفحه‌ی کاربر در پنل از آن می‌خواند
    if (location) {
      try {
        await geo.record({ deviceUid, userId, ip }, { ...location, source: location.source || 'startup' });
      } catch (err) {
        //  لوکیشنِ خراب نباید تپش را بشکند
        if (err.code !== 'bad_location' && err.code !== 'device_required') throw err;
      }
    }

    //  پیام خوانده‌نشده‌ی پشتیبانی
    let unread = 0;
    const thread = userId
      ? await one(`SELECT unread_user FROM support_threads WHERE user_id=$1 ORDER BY updated_at DESC LIMIT 1`, [userId])
      : await one(`SELECT unread_user FROM support_threads WHERE device_uid=$1 ORDER BY updated_at DESC LIMIT 1`, [deviceUid]);
    if (thread) unread = Number(thread.unread_user) || 0;

    res.json({ ok: true, serverTime: now(), supportUnread: unread, visits: visitor ? visitor.visits : 1 });
  } catch (err) { next(err); }
});

module.exports = router;
