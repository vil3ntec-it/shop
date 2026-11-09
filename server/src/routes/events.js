'use strict';
/**
 * خبرهای دکان.
 *
 *   POST /api/events        ثبت خبر (هر عضو دکان)
 *   GET  /api/events        خواندن خبرها
 *   POST /api/events/seen   «تا اینجا خواندم»
 *
 * چرا خواندن پشتِ اشتراک نیست: صاحب دکان باید بتواند ببیند در نبودش چه
 * گذشت، حتی اگر اشتراکش تمام شده باشد. نوشتن هم آزاد است — خبر داده‌ی
 * دکان نیست، یک پیام است، و بستنش فقط صاحب دکان را کور می‌کند.
 */
const express = require('express');
const v = require('../lib/validate');
const events = require('../lib/events');
const { requireUser, requireShop } = require('../middleware/auth');

const router = express.Router();
router.use(requireUser, requireShop);

/*
 *  ⛔ ممیزیِ ۳۵ (B6): زمانِ خبر از دستگاه می‌آید (صفِ بی‌اینترنت زمانِ خودش را دارد)، ولی **آینده** نه. خبرِ ۲۰۵۸ ِ
 *  یک کارمند همیشه «تازه‌ترین» بود: برنامهٔ صاحب `since` و «دیده شد» را روی آن می‌گذاشت و هیچ خبرِ واقعیِ بعدی
 *  (تمام شدنِ کالا، قرض…) دیگر نمی‌رسید؛ عددِ بیرون از bigint هم ۵۰۰ می‌داد. آینده (با ۵ دقیقه لغزشِ ساعت)، منفی و
 *  ناعدد ⇐ ساعتِ سرور. (پمپ همان کتابخانه را از درِ خودش می‌خواند و دست نخورد.)
 */
const SKEW_MS = 5 * 60 * 1000;
const atOf = (x, t) => { const n = Number(x); return Number.isSafeInteger(n) && n > 0 && n <= t + SKEW_MS ? n : t; };

router.post('/', async (req, res, next) => {
  try {
    const t = require('../db').now();
    const items = (Array.isArray(req.body?.events) ? req.body.events
      : (req.body?.kind ? [req.body] : [])).map((it) => (it && typeof it === 'object' ? { ...it, at: atOf(it.at, t) } : it));
    const out = await events.record(
      { shopId: req.shopId, userId: req.user.id, userName: req.user.name || '' },
      items
    );
    res.status(201).json({ ...out, serverTime: require('../db').now() });
  } catch (err) { next(err); }
});

router.get('/', async (req, res, next) => {
  try {
    const since = v.integer(req.query?.since, { field: 'since', min: 0, max: Number.MAX_SAFE_INTEGER, def: 0 });
    const limit = v.integer(req.query?.limit, { field: 'limit', min: 1, max: 200, def: 50 });
    const list = await events.list(req.shopId, { since, limit });
    res.json({
      events: list,
      seenAt: await events.seenAt(req.shopId, req.user.id),
      unread: await events.unreadCount(req.shopId, req.user.id),
      serverTime: require('../db').now(),
    });
  } catch (err) { next(err); }
});

router.post('/seen', async (req, res, next) => {
  try {
    //  «دیده شد» هم آینده نمی‌گیرد (GREATEST است و برنمی‌گردد) — همان سقفِ زمانِ خبر
    const t = require('../db').now();
    const at = Math.min(v.integer(req.body?.at, {
      field: 'at', min: 0, max: Number.MAX_SAFE_INTEGER, def: t,
    }), t + SKEW_MS);
    await events.markSeen(req.shopId, req.user.id, at);
    res.json({ ok: true, seenAt: at });
  } catch (err) { next(err); }
});

module.exports = router;
