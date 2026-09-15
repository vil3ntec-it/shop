'use strict';
/**
 * ══ کیو‌آرِ زندهٔ مشتری — تنها مسیرِ عمومیِ بخشِ پمپ ══════════════════════
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * «من دارم زنده تغییرات میارم توی حسابِ طرف، طرف هم داره با کیو‌آر حسابشو
 *  چک می‌کنه و می‌خوام درجا برای اون هم بره… که هر دقیقه بتونه چک کنه.»
 *
 * ── راه ───────────────────────────────────────────────────────────
 *
 *   برنامهٔ کامپیوتر ──PUT files/acct-<شناسه>──▶ station_files   {v, k, at, d}
 *   گوشیِ مشتری     ──GET /pump/public/<کد>/acct/<شناسه>?k=…──▶ {at, d}
 *
 * مشتری هیچ حسابی ندارد و نباید داشته باشد؛ چیزی که دارد رمزِ **همان یک
 * حساب** است (‎k‎) که داخلِ کیو‌آرش چاپ شده. این‌جا آن رمز با رمزِ داخلِ
 * فایل مقایسه می‌شود (زمان‌ثابت) و فقط ‎{at, d}‎ برمی‌گردد — خودِ ‎k‎ هرگز.
 *
 * ⚠️ «پمپ نیست»، «فایل نیست» و «رمز غلط» عمداً یک جواب دارند (‎404‎)، تا
 * کسی با آزمون‌وخطا نفهمد کدام پمپ‌ها و کدام حساب‌ها هستند.
 *
 * ⚠️ CORS این مسیر باز است (‎*‎): صفحهٔ مشتری روی دامنهٔ خودِ پمپ است
 * (‎yaqobipump.top/view‎) و از این‌جا می‌پرسد. داده‌ای که پشتِ رمزِ همان
 * حساب است، برای همان حساب عمومی است؛ چیزِ دیگری از این در بیرون نمی‌رود.
 *
 * ⚠️ در ‎app.js‎ باید **پیش از** ‎/pump‎ سوار شود: آن روتر ‎requirePumpUser‎ی
 * سراسری دارد و این مسیر عمداً بی‌توکن است.
 */
const express = require('express');
const crypto = require('crypto');
const { one, now } = require('../db');
const config = require('../config');
const stations = require('../lib/stations');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();

/** همان پیشوندی که برنامه برای فایلِ هر حساب می‌گذارد (‎AcctLive.CloudPrefix‎). */
const PREFIX = 'acct-';

function timingEqual(a, b) {
  const x = Buffer.from(String(a), 'utf8');
  const y = Buffer.from(String(b), 'utf8');
  if (x.length !== y.length) return false;
  return crypto.timingSafeEqual(x, y);
}

router.get(
  '/:code/acct/:id',
  rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'pump-public' }),
  async (req, res, next) => {
    try {
      res.set('Access-Control-Allow-Origin', '*');
      res.set('Cache-Control', 'no-store');

      const id = String(req.params.id || '').trim();
      const key = String(req.query.k || '').trim();
      const miss = () => notFound('چنین حسابی نیست', 'not_found');
      if (!/^[a-z][0-9]{1,18}$/.test(id) || !/^[A-Za-z0-9]{8,64}$/.test(key)) throw miss();

      const st = await stations.byCode(req.params.code);
      if (!st) throw miss();

      const row = await one(
        'SELECT data, updated_at FROM station_files WHERE station_id=$1 AND path=$2',
        [st.id, PREFIX + id]
      );
      const env = row && row.data && typeof row.data === 'object' ? row.data : null;
      if (!env || !timingEqual(env.k || '', key)) throw miss();

      res.json({
        ok: true,
        at: Number(env.at) || Number(row.updated_at) || 0,
        d: env.d === undefined ? null : env.d,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

module.exports = router;
