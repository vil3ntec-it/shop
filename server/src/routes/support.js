'use strict';
/**
 * چت پشتیبانی — سمت کاربر.
 *
 * ── چرا توکن اجباری نیست ───────────────────────────────────────────
 * کسی که هنوز ثبت‌نام نکرده و همان‌جا گیر کرده، بیشتر از هر کسِ دیگری
 * به پشتیبانی نیاز دارد. پس اگر توکن نبود، شناسه‌ی دستگاه کافی است و
 * رشته به همان بسته می‌شود. بعداً که حساب ساخت، همان رشته به حسابش
 * می‌چسبد و از اول توضیح نمی‌دهد.
 *
 * ── چرا کسی نمی‌تواند چت دیگری را بخواند ───────────────────────────
 * شناسه‌ی رشته را کاربر نمی‌فرستد؛ سرور از روی توکن یا شناسه‌ی دستگاهِ
 * خودش پیدایش می‌کند. یعنی حتی اگر کسی شناسه‌ی رشته‌ی دیگری را حدس
 * بزند، به آن نمی‌رسد.
 */
const express = require('express');
const { one, now } = require('../db');
const v = require('../lib/validate');
const support = require('../lib/support');
const push = require('../lib/push');
const tokens = require('../lib/tokens');
const { membershipOf } = require('../lib/shops');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { badRequest } = require('../middleware/errors');
const config = require('../config');

const router = express.Router();

//  نوشتن پیام محدودِ نرخ است؛ خواندن نه، چون برنامه هر چند ثانیه
//  می‌پرسد «چیزی تازه هست؟»
const writeLimit = rateLimit({ max: 30, keyPrefix: 'support-write' });

/**
 * کیستیِ درخواست — با توکن اگر بود، وگرنه با شناسه‌ی دستگاه.
 *
 * توکنِ نامعتبر خطا نمی‌دهد، فقط نادیده گرفته می‌شود: کاربری که نشستش
 * منقضی شده هم باید بتواند بپرسد «چرا نمی‌توانم وارد شوم؟».
 */
async function who(req) {
  const header = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  let user = null;
  let shopId = '';
  if (header) {
    const row = await tokens.verify(header, 'access');
    if (row) {
      user = await one('SELECT * FROM users WHERE id=$1', [row.subject_id]);
      if (user) {
        const member = await membershipOf(user.id);
        if (member) shopId = member.shop_id;
      }
    }
  }
  const deviceUid = v.id(
    req.body?.device?.uid || req.body?.deviceUid || req.query?.deviceUid || '',
    { field: 'شناسه دستگاه', required: false, max: 64 }
  );
  if (!user && !deviceUid) {
    throw badRequest('برای پشتیبانی، شناسه‌ی دستگاه لازم است', 'device_required');
  }
  return {
    app: v.text(req.body?.app || req.query?.app, { max: 40 }) || 'shop',
    userId: user ? user.id : '',
    shopId,
    deviceUid,
    who: user ? (user.name || '') : v.text(req.body?.name || req.query?.name, { max: 80 }),
    contact: user ? (user.email || user.phone || '') : v.text(req.body?.contact, { max: 120 }),
  };
}

/**
 * گفت‌وگوی من — با پیام‌هایش.
 *
 * `after` می‌گیرد تا برنامه فقط تازه‌ها را بردارد و لازم نباشد هر بار
 * کل گفت‌وگو را از سرور بکشد.
 */
router.get('/thread', async (req, res, next) => {
  try {
    const id = await who(req);
    const thread = await support.threadFor(id);
    const after = v.integer(req.query?.after, { min: 0, max: 1e15, def: 0 });
    res.json({
      thread: support.shapeThread(thread),
      messages: await support.messages(thread.id, { after }),
      serverTime: now(),
      //  متن خوش‌آمد، تا صفحه‌ی خالی سرد نباشد
      greeting: 'سلام. هر مشکلی یا سؤالی دارید همین‌جا بنویسید — پاسخ می‌دهیم.',
    });
  } catch (err) { next(err); }
});

/** پیام تازه. */
router.post('/messages', writeLimit, async (req, res, next) => {
  try {
    const id = await who(req);
    const body = v.text(req.body?.body ?? req.body?.text, {
      max: support.MAX_BODY, required: true, field: 'پیام',
    });
    const thread = await support.threadFor({
      ...id,
      subject: v.text(req.body?.subject, { max: 120 }),
    });
    const message = await support.post(thread.id, {
      sender: 'user',
      senderId: id.userId,
      senderName: id.who,
      body,
    });
    res.status(201).json({ message, thread: support.shapeThread(await one('SELECT * FROM support_threads WHERE id=$1', [thread.id])) });
  } catch (err) { next(err); }
});

/** «خواندم» — نقطه‌ی قرمز را پاک می‌کند. */
router.post('/read', async (req, res, next) => {
  try {
    const id = await who(req);
    const thread = await support.threadFor(id);
    await support.markRead(thread.id, 'user');
    res.json({ ok: true });
  } catch (err) { next(err); }
});

/**
 * ثبت توکن پوش.
 *
 * تا این نباشد، پیامِ پشتیبانی فقط وقتی دیده می‌شود که برنامه باز باشد.
 * با این، به گوشیِ بسته هم می‌رسد.
 */
router.post('/push', async (req, res, next) => {
  try {
    const id = await who(req);
    const token = v.text(req.body?.token, { max: 500, required: true, field: 'توکن پوش' });
    await push.register({
      app: id.app,
      token,
      provider: v.oneOf(req.body?.provider, ['fcm', 'webpush'], { field: 'سرویس', def: 'fcm' }),
      userId: id.userId,
      deviceUid: id.deviceUid,
      platform: v.text(req.body?.platform, { max: 20 }),
    });
    res.status(201).json({ ok: true });
  } catch (err) { next(err); }
});

router.delete('/push', async (req, res, next) => {
  try {
    const token = v.text(req.body?.token || req.query?.token, { max: 500, required: true, field: 'توکن پوش' });
    await push.unregister(token);
    res.json({ ok: true });
  } catch (err) { next(err); }
});

module.exports = router;
module.exports.who = who;
