'use strict';
/**
 * پشتیبانیِ صاحبِ پمپ ↔ مدیرِ سامانه.
 *
 * ── چیزی که نبود ───────────────────────────────────────────────────
 * دکان‌دار از روزِ اول `/api/support` را داشت. پمپ‌دار نداشت — و
 * `/api/pump/device/chat/*` جای آن را نمی‌گیرد: آن گفت‌وگوی **مشتریِ
 * کیوآر با صاحبِ پمپ** است، نه گفت‌وگوی صاحبِ پمپ با کسی که برنامه را
 * ساخته. پس پمپ‌داری که گیر می‌کرد هیچ دری نداشت.
 *
 * ── یک پمپ، یک گفت‌وگو ─────────────────────────────────────────────
 *   `/api/pump/support`          گوشیِ صاحبِ پمپ (توکنِ حساب)
 *   `/api/pump/device/support`   کامپیوترِ پمپ (توکنِ دستگاه)
 *
 * هر دو به **همان** رشته می‌رسند، چون کلیدش `station_id` است. صاحبِ
 * پمپ جوابی را که کامپیوترش گرفته روی گوشی هم می‌بیند.
 *
 * ⛔ **این هیچ‌وقت پشتِ اشتراک نمی‌رود.** قاعدهٔ صریحِ صاحب مخزنِ پمپ:
 * «پشتیبانی باشد چون یکی از واجبات است.» کسی که اشتراکش تمام شده،
 * بیشتر از همه لازم دارد بپرسد چرا.
 */
const express = require('express');
const { one, now } = require('../db');
const v = require('../lib/validate');
const support = require('../lib/support');
const relay = require('../lib/chat-relay');
const push = require('../lib/push');
const { rateLimit } = require('../middleware/ratelimit');
const { forbidden, notFound } = require('../middleware/errors');

const writeLimit = rateLimit({ max: 30, keyPrefix: 'pump-support-write' });

/**
 * @param {(req)=>object} idOf کیستیِ درخواست — فقط از میان‌افزار
 */
function makeRouter(idOf) {
  const router = express.Router();

  /** کیستی، یا خطایی که می‌گوید چرا نمی‌شود. */
  function need(req) {
    const id = idOf(req);
    if (!id.stationId) throw forbidden('برای این حساب پمپی ثبت نشده است', 'no_station');
    return { app: 'pump', ...id };
  }

  router.get('/thread', async (req, res, next) => {
    try {
      const id = need(req);
      const thread = await support.threadFor(id);
      res.json({
        thread: support.shapeThread(thread),
        messages: await support.messages(thread.id, {
          after: v.integer(req.query?.after, { min: 0, max: 1e15, def: 0 }),
        }),
        greeting: 'سلام. هر مشکلی یا سؤالی دربارهٔ برنامهٔ پمپ دارید همین‌جا بنویسید.',
        relayDays: relay.relayDays(),
        serverTime: now(),
      });
    } catch (err) { next(err); }
  });

  router.post('/messages', writeLimit, async (req, res, next) => {
    try {
      const id = need(req);
      const kind = v.oneOf(req.body?.kind, ['text', ...support.MEDIA_KINDS], { field: 'نوعِ پیام', def: 'text' });
      const raw = req.body?.body ?? req.body?.text;
      //  پیامِ رسانه‌ای نوشته نمی‌خواهد؛ اگر نوشته دارد همان قاعدهٔ متن
      const body = kind === 'text' || String(raw ?? '').trim() ? support.cleanBody(raw) : '';
      const thread = await support.threadFor({
        ...id, subject: v.text(req.body?.subject, { max: 120 }),
      });
      const message = await support.post(thread.id, {
        sender: 'user', senderId: id.userId || '', senderName: id.who || '', body,
        kind, mediaId: kind === 'text' ? null : req.body?.mediaId,
      });
      res.status(201).json({
        message,
        thread: support.shapeThread(await one('SELECT * FROM support_threads WHERE id=$1', [thread.id])),
        serverTime: now(),
      });
    } catch (err) { next(err); }
  });

  /*
   *  رسانه — عکس، ویدیو، پیامِ صوتی. ⛔ فقط در عبور: رسانه‌ای که مدیر
   *  فرستاده همان لحظه که این طرف کاملش را گرفت از سرور پاک می‌شود
   *  (`support.sendMedia`)؛ برنامه نسخهٔ خودش را نگه می‌دارد.
   */
  router.post('/media', writeLimit, support.rawMedia, async (req, res, next) => {
    try {
      const thread = await support.threadFor(need(req));
      const out = await support.putMedia({
        threadId: thread.id, uploader: 'user',
        mime: req.headers['content-type'] || '', buf: Buffer.isBuffer(req.body) ? req.body : null,
      });
      res.status(201).json({ ok: true, ...out });
    } catch (err) { next(err); }
  });

  router.get('/media/:mid', async (req, res, next) => {
    try {
      const thread = await support.threadFor(need(req));
      const m = await support.getMedia({ id: req.params.mid, threadId: thread.id });
      if (!m) throw notFound(relay.GONE_MESSAGE, 'media_gone');
      support.sendMedia(req, res, m, 'user');
    } catch (err) { next(err); }
  });

  router.post('/read', async (req, res, next) => {
    try {
      const thread = await support.threadFor(need(req));
      await support.markRead(thread.id, 'user');
      res.json({ ok: true });
    } catch (err) { next(err); }
  });

  /**
   * ثبتِ توکنِ پوش — تا جوابِ مدیر به گوشیِ **بسته** هم برسد.
   *
   * ⚠️ کامپیوترِ پمپ حساب ندارد، پس توکنش به `deviceUid` بسته می‌شود
   * نه به کاربر. بی این، تنها گیرندهٔ ممکن گوشیِ صاحبِ پمپ بود.
   */
  router.post('/push', async (req, res, next) => {
    try {
      const id = need(req);
      await push.register({
        app: 'pump',
        token: v.text(req.body?.token, { max: 500, required: true, field: 'توکن پوش' }),
        provider: v.oneOf(req.body?.provider, ['fcm', 'webpush'], { field: 'سرویس', def: 'fcm' }),
        userId: id.userId || '',
        //  ⚠️ بی این، ردیفِ کامپیوترِ پمپ با `user_id` خالی می‌نشست و
        //  هیچ پرس‌وجویی به آن نمی‌رسید — ثبتِ بی‌فایده
        stationId: id.stationId,
        deviceUid: id.deviceUid || '',
        platform: v.text(req.body?.platform, { max: 20 }),
      });
      res.status(201).json({ ok: true });
    } catch (err) { next(err); }
  });

  router.delete('/push', async (req, res, next) => {
    try {
      await push.unregister(v.text(req.body?.token || req.query?.token, {
        max: 500, required: true, field: 'توکن پوش',
      }));
      res.json({ ok: true });
    } catch (err) { next(err); }
  });

  return router;
}

module.exports = { makeRouter };
