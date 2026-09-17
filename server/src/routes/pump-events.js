'use strict';
/**
 * خبرهای پمپ — «برنامه بسته هم باشد، خبر برسد».
 *
 *   POST /api/pump/device/events   کامپیوترِ پمپ، با توکنِ دستگاه
 *   POST /api/pump/events          گوشیِ صاحبِ پمپ، با توکنِ حساب
 *   GET  /api/pump/events          خواندن — هر دو در
 *   POST /api/pump/events/seen     «تا این‌جا خواندم»
 *
 * ── چه چیزی نبود ───────────────────────────────────────────────────
 * ⛔ `‎/api/events‎` از روزِ اول `requireShop` داشت، یعنی بخشِ پمپ هیچ
 * دفترِ خبری روی ابر نداشت. «اضافه برد» و «کم مانده» فقط روی سرورِ
 * **خانگی** می‌نشستند و گوشیِ کارمند هر پانزده دقیقه از همان شبکه
 * می‌پرسید. پس صاحبِ پمپی که بیرون بود — یا مودمش خاموش بود — هیچ‌وقت
 * خبر نمی‌گرفت، و برنامهٔ بسته هیچ زنگی نداشت.
 *
 * ── دو در، یک رشته ─────────────────────────────────────────────────
 * همان الگوی `pump-support` و `account-backups`: کامپیوترِ پمپ حساب
 * ندارد، پس با توکنِ **دستگاه** می‌نویسد؛ صاحبِ پمپ با توکنِ **حساب**
 * همان‌ها را می‌خواند. کلید `station_id` است، پس هر دو یک دفتر
 * می‌بینند.
 *
 * ⛔ **هیچ‌وقت پشتِ اشتراک نمی‌رود.** همان قاعده‌ای که بخشِ دکان دارد:
 * خبر دادهٔ کاربر نیست، یک پیام است، و بستنش فقط صاحبِ پمپ را کور
 * می‌کند. کسی که اشتراکش تمام شده بیشتر از همه لازم دارد بداند تیلش
 * تمام شده.
 */
const express = require('express');
const { now } = require('../db');
const v = require('../lib/validate');
const events = require('../lib/events').pump;
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

/*
 *  صفِ آفلاین یک‌جا می‌رسد و هر بسته تا پنجاه خبر دارد، پس سقف روی
 *  «درخواست» است نه «خبر». بی این، برنامه‌ای که در حلقه افتاده
 *  می‌توانست دفترِ خبر را پر کند.
 */
const writeLimit = rateLimit({ max: 120, keyPrefix: 'pump-events-write' });

/**
 * @param {(req)=>object} idOf کیستیِ درخواست — فقط از میان‌افزار
 */
function makeRouter(idOf) {
  const router = express.Router();

  function need(req) {
    const id = idOf(req) || {};
    if (!id.stationId) throw forbidden('برای این حساب پمپی ثبت نشده است', 'no_station');
    return id;
  }

  /**
   * کیستیِ خواننده.
   *
   * ⚠️ کامپیوترِ پمپ حساب ندارد، ولی «تا کجا خواندم» به یک شناسه
   * احتیاج دارد. پس برای او `device:<uid>` است — همان قراری که
   * `vip.redeem` هنگامِ فعال‌سازی می‌گذارد. بی این، دو کامپیوترِ یک
   * پمپ نقطهٔ خوانده‌شدنِ هم را جابه‌جا می‌کردند.
   */
  function readerOf(id) {
    return id.userId || (id.deviceUid ? `device:${id.deviceUid}` : '');
  }

  router.post('/', writeLimit, async (req, res, next) => {
    try {
      const id = need(req);
      const items = Array.isArray(req.body?.events) ? req.body.events
        : (req.body?.kind ? [req.body] : []);
      const out = await events.record({
        tenantId: id.stationId,
        userId: id.userId || '',
        userName: id.who || '',
        deviceUid: id.deviceUid || '',
      }, items);
      res.status(201).json({ ...out, serverTime: now() });
    } catch (err) { next(err); }
  });

  router.get('/', async (req, res, next) => {
    try {
      const id = need(req);
      const since = v.integer(req.query?.since, {
        field: 'since', min: 0, max: Number.MAX_SAFE_INTEGER, def: 0,
      });
      const limit = v.integer(req.query?.limit, { field: 'limit', min: 1, max: 200, def: 50 });
      const reader = readerOf(id);
      res.json({
        events: await events.list(id.stationId, { since, limit }),
        seenAt: reader ? await events.seenAt(id.stationId, reader) : 0,
        unread: reader ? await events.unreadCount(id.stationId, reader) : 0,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  });

  router.post('/seen', async (req, res, next) => {
    try {
      const id = need(req);
      const at = v.integer(req.body?.at, {
        field: 'at', min: 0, max: Number.MAX_SAFE_INTEGER, def: now(),
      });
      const reader = readerOf(id);
      if (reader) await events.markSeen(id.stationId, reader, at);
      res.json({ ok: true, seenAt: at });
    } catch (err) { next(err); }
  });

  return router;
}

module.exports = { makeRouter };
