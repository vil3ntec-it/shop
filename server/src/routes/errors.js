'use strict';
/**
 * گزارشِ خطای برنامه — بندِ ۲۰.۸ پرامپت.
 *
 *   POST /api/errors     برنامه خطایش را می‌فرستد (با اجازهٔ کاربر)
 *
 * ── چرا ارزشش را دارد ──────────────────────────────────────────────────
 * «من قبل از تماسِ مشتری خبر داشته باشم.» بی این، هر باگ فقط وقتی دیده
 * می‌شود که کسی زنگ بزند — و آن‌وقت هم شرحش «کار نمی‌کند» است.
 *
 * ── سه قاعده ───────────────────────────────────────────────────────────
 *  ⛔ **هیچ دادهٔ مشتری این‌جا نمی‌آید.** فقط پیام، Stackِ کوتاه و چند خطِ
 *     آخرِ لاگ. هر چیزی که شبیهِ ایمیل، شماره یا توکن باشد پوشانده می‌شود.
 *  ⛔ **توکن اجباری نیست.** برنامه‌ای که هنگامِ ورود می‌شکند هنوز نشستی
 *     ندارد — و همان خطا دقیقاً همانی است که باید دیده شود.
 *  ⚠️ سقفِ نرخ دارد، وگرنه یک حلقهٔ خطا در یک برنامه دفتر را پر می‌کند.
 */
const express = require('express');
const { query, now, newId } = require('../db');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { resolveToken, tokenOf } = require('../lib/sync-v1-auth');
const { sectionOf } = require('../lib/tenancy');

const router = express.Router();

const MAX_MESSAGE = 500;
const MAX_STACK = 4000;
const MAX_LOG = 4000;

/** هر چیزی که به کسی برگردد — ایمیل، شماره، توکنِ بلند — پوشانده می‌شود. */
function scrub(text, limit) {
  return String(text || '')
    .replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, '[ایمیل]')
    //  هر دو شکل: ۰۷xxxxxxxx افغانستان و ۰۹xxxxxxxxx ایران، با یا بی کدِ کشور
    .replace(/\b(?:\+?9[38][\s-]?)?0?7\d{8}\b/g, '[شماره]')
    .replace(/\b(?:\+?98[\s-]?)?0?9\d{9}\b/g, '[شماره]')
    .replace(/\b[A-Za-z0-9_-]{32,}\b/g, '[توکن]')
    .slice(0, limit);
}

router.post(
  '/',
  rateLimit({ max: Number(process.env.RATE_ERRORS_MAX || 60), keyPrefix: 'client-errors' }),
  express.json({ limit: '64kb' }),
  async (req, res, next) => {
    try {
      //  توکن اگر بود، خطا به همان حساب می‌چسبد؛ نبود، باز هم ثبت می‌شود
      let ctx = null;
      try { ctx = await resolveToken(tokenOf(req)); } catch { ctx = null; }

      const app = ctx?.app
        || sectionOf(req.body?.app || req.headers['x-app'] || req.headers['x-app-id'])
        || 'shop';

      await query(
        `INSERT INTO client_errors (id, app, account_kind, account_id, device_id, app_version, schema_version,
                                    message, stack, log_tail, at, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$11)`,
        [
          newId('cer'),
          app,
          ctx?.accountKind || '',
          ctx?.accountId || '',
          String(req.headers['x-device'] || req.body?.device_id || '').slice(0, 64),
          String(req.headers['x-app-version'] || req.body?.app_version || '').slice(0, 32),
          Number(req.body?.schema_version || 0) || 0,
          scrub(req.body?.message, MAX_MESSAGE),
          scrub(req.body?.stack, MAX_STACK),
          scrub(req.body?.log_tail || req.body?.logTail, MAX_LOG),
          now(),
        ]
      );
      //  پاسخ کوتاه است و هیچ‌وقت خطا نمی‌دهد: برنامه‌ای که دارد می‌شکند
      //  نباید به‌خاطرِ گزارشِ خطا دوباره بشکند.
      res.status(202).json({ ok: true });
    } catch (err) {
      //  حتی خرابیِ خودِ ثبت هم به برنامه برنمی‌گردد
      if (String(err?.code || '').startsWith('42')) return res.status(202).json({ ok: true });
      next(err);
    }
  }
);

module.exports = router;
