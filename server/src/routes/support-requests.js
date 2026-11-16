'use strict';
/**
 * «از پشتیبانی بخواه» — سمتِ کاربر (`lib/support-requests.js`، مهاجرتِ ۹۶۰).
 *
 *   POST /api/support/requests   {code, context?, lang?, deviceUid?, name?, contact?}  ⇒ ۲۰۱ تازه | ۲۰۰ همان درخواستِ باز
 *   GET  /api/support/requests                                                     ⇒ درخواست‌های بازِ خودم/دکانم (فقط کد و حال)
 *
 * کیستی همان `routes/support.who` است (توکن، وگرنه شناسهٔ دستگاه): کاربر شناسهٔ رشته یا دکان نمی‌فرستد و هر شناسهٔ
 * دکان در بدنه/نشانی نادیده است — دکان فقط از توکن. ⛔ `routes/support.js` (مشترک با پمپ) دست نخورد.
 */
const express = require('express');
const { who } = require('./support');
const requests = require('../lib/support-requests');
const { rateLimit } = require('../middleware/ratelimit');

const router = express.Router();
//  یک لمس، یک درخواست: سقف تنگ‌تر از پیامِ آزاد (۳۰) — تکرارِ همان درخواست هم شمرده می‌شود
const askLimit = rateLimit({ max: 20, keyPrefix: 'support-request' });

router.post('/', askLimit, async (req, res, next) => {
  try {
    const id = await who(req);
    const out = await requests.open(id, {
      code: req.body?.code, param: req.body?.param, context: req.body?.context, lang: req.body?.lang,
    });
    res.status(out.created ? 201 : 200).json({
      request: out.request, created: out.created, sameShop: out.sameShop, ...(out.message ? { message: out.message } : {}),
    });
  } catch (err) { next(err); }
});

router.get('/', async (req, res, next) => {
  try {
    res.set('Cache-Control', 'no-store');
    res.json({ requests: await requests.mine(await who(req)) });
  } catch (err) { next(err); }
});

module.exports = router;
