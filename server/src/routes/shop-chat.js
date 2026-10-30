'use strict';
/**
 * چتِ مشتری ↔ دکان، سمتِ دکان — `/api/shop/chat` (v3 §۳۳).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.chat` فقط `GET /config` جواب می‌دهد
 * (`enabled: false`، تا برنامه بخش را نشان ندهد)؛ بقیه ۴۰۴ و بی هیچ ردیف.
 * ⛔ shop_id فقط از عضویتِ کاربرِ توکن می‌آید، هرگز از درخواست.
 * ⛔ دسترسی‌ها سمتِ سرور: `chat.view` (همه)، `chat.reply` (نوشتن)،
 * `chat.moderate` (بلاک، حذفِ هر پیامِ دکان)؛ شمارهٔ کامل = `orders.contact`.
 *
 * زیرِ پیشوندِ موجودِ `/shop` است ⇒ `ACCOUNT_PREFIXES` ِ پنلِ خانگی چیزِ
 * تازه‌ای نمی‌خواهد.
 */
const express = require('express');
const config = require('../config');
const chat = require('../lib/shop-chat');
const { can } = require('../lib/permissions');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, refund } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
router.use(requireUser, requireShop, requirePermission('chat.view'));

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const viewerOf = (req) => ({ side: 'shop', shopId: req.shopId, userId: req.user.id, role: req.role });
const reply = requirePermission('chat.reply');
const moderate = requirePermission('chat.moderate');
//  تکرارِ همان پیام/همان بایت سهمیه را پس می‌دهد (CHAT-11) ⇒ `refundable`
const sendLimit = rateLimit({ max: config.shopChat.sendMax, keyPrefix: 'shop-chat-send', key: (req) => req.user && req.user.id, refundable: true });
const uploadLimit = rateLimit({ max: config.shopChat.uploadMax, keyPrefix: 'shop-chat-upload', key: (req) => req.user && req.user.id, refundable: true });

const on = wrap(async (req, res, next) => {
  if (!(await chat.flagOf(req.shopId))) throw notFound('چت برای این دکان روشن نیست', 'chat_disabled');
  res.set('Cache-Control', 'no-store');
  next();
});

router.get('/config', wrap(async (req, res) => {
  const s = config.shopChat;
  res.json({
    enabled: await chat.flagOf(req.shopId),
    limits: {
      bodyMax: chat.MAX_BODY, imageMax: s.imageMax, thumbMax: s.thumbMax, audioMax: s.audioMax, fileMax: s.fileMax,
      editWindowMs: s.editWindowMs, deleteWindowMs: s.deleteWindowMs,
    },
    reasons: chat.REASONS,
    permissions: Object.fromEntries(
      ['reply', 'moderate'].map((k) => [k, can(req.role, `chat.${k}`)]).concat([['contact', can(req.role, 'orders.contact')]])
    ),
  });
}));

router.get('/conversations', on, wrap(async (req, res) => {
  res.json(await chat.list(viewerOf(req), {
    cursor: req.query.cursor, limit: req.query.limit, status: req.query.status, q: req.query.q,
  }));
}));

router.post('/conversations', on, reply, wrap(async (req, res) => {
  const out = await chat.open(viewerOf(req), req.body);
  res.status(out.created ? 201 : 200).json({ conversation: out.conversation });
}));

router.get('/search', on, wrap(async (req, res) => {
  res.json(await chat.search(viewerOf(req), req.query.q, { limit: req.query.limit }));
}));

router.get('/conversations/:id', on, wrap(async (req, res) => {
  res.json({ conversation: await chat.viewOf(viewerOf(req), req.params.id) });
}));

router.put('/conversations/:id/status', on, reply, wrap(async (req, res) => {
  res.json({ conversation: await chat.setStatus(viewerOf(req), req.params.id, req.body?.status) });
}));

router.get('/conversations/:id/messages', on, wrap(async (req, res) => {
  res.json(await chat.changes(viewerOf(req), req.params.id, {
    after: req.query.after, limit: req.query.limit, wait: req.query.wait,
  }));
}));

router.post('/conversations/:id/messages', on, reply, sendLimit, wrap(async (req, res) => {
  const out = await chat.send(viewerOf(req), req.params.id, req.body);
  if (out.replayed) { res.set('Idempotent-Replayed', 'true'); refund(req, 'shop-chat-send'); }
  res.status(out.replayed ? 200 : 201).json(out);
}));

router.patch('/conversations/:id/messages/:mid', on, reply, wrap(async (req, res) => {
  res.json(await chat.edit(viewerOf(req), req.params.id, req.params.mid, req.body));
}));

router.delete('/conversations/:id/messages/:mid', on, wrap(async (req, res) => {
  res.json(await chat.remove(viewerOf(req), req.params.id, req.params.mid, req.query.for));
}));

router.post('/conversations/:id/receipts', on, wrap(async (req, res) => {
  res.json(await chat.receipts(viewerOf(req), req.params.id, req.body));
}));

router.post('/conversations/:id/media', on, reply, uploadLimit, chat.rawMedia, wrap(async (req, res) => {
  const out = await chat.putMedia(viewerOf(req), req.params.id, req.body, { role: req.query.role });
  if (out.reused) refund(req, 'shop-chat-upload');
  res.status(201).json(out);
}));

router.get('/conversations/:id/media/:mediaId', on, wrap(async (req, res) => {
  chat.sendMedia(res, await chat.getMedia(viewerOf(req), req.params.id, req.params.mediaId));
}));

router.put('/conversations/:id/block', on, moderate, wrap(async (req, res) => {
  res.json({ conversation: await chat.setBlock(viewerOf(req), req.params.id, true) });
}));

router.delete('/conversations/:id/block', on, moderate, wrap(async (req, res) => {
  res.json({ conversation: await chat.setBlock(viewerOf(req), req.params.id, false) });
}));

router.post('/conversations/:id/reports', on, wrap(async (req, res) => {
  res.status(201).json(await chat.report(viewerOf(req), req.params.id, req.body));
}));

module.exports = router;
