'use strict';
/**
 * `GET /api/v1/config/server` — هویت و توانِ همین سرور (شرح: `lib/server-identity.js`).
 *
 * عمومی و فقط خواندنی: بی توکن، بی نوشتن، بی کلیدِ پولی (SUB-10: «FREE/عمومی»؛ سنجه‌اش در
 * `test/server-info.test.js`). زیرِ پیشوندِ موجودِ `/config` است ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی
 * چیزِ تازه‌ای نمی‌خواهد و از تونلِ `api.vill3n.top` همان روزِ اول می‌رسد.
 */
const express = require('express');
const identity = require('../lib/server-identity');

const router = express.Router();

router.get('/', async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-cache');
    res.json(await identity.info());
  } catch (err) { next(err); }
});

module.exports = router;
