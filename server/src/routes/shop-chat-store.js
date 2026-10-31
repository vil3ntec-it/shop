'use strict';
/**
 * چتِ مشتری ↔ دکان، سمتِ مشتری — `/api/store/:slug/chat/…` (v3 §۳۳).
 *
 * هویتِ مشتری = شمارهٔ تأییدشده با بلیتِ ویترین (سرآیندِ `X-Store-Ticket`)،
 * همان که ویترین (PR #244، `lib/storefront.js`) پس از کدِ یک‌بارمصرف می‌دهد.
 * قراردادی که این‌جا از ویترین لازم است، فقط دو تابع:
 *
 *   open(slug)            ⇒ ردیفِ ویترینِ روشن با `shop_id` — یا خطای ۴۰۴
 *   verifiedPhoneOfTicket(ticket) ⇒ (ناهم‌گام) شمارهٔ ‎+93…‎ ِ تأییدشده — یا null
 *                                   (منقضی، دست‌خورده یا لغوشده با «خروج از همه‌جا»)
 *
 * تا ویترین روی سرور نیست، این درها همان ۴۰۴ ِ «ویترین نیست» را می‌دهند.
 *
 * ⛔ پیش‌فرض خاموش: ویترینِ روشن (پرچم‌های خودش) + پرچمِ `shop.chat` ِ همان
 * دکان؛ هر کدام نباشد ⇒ ۴۰۴ و هیچ ردیفی ساخته نمی‌شود.
 * ⛔ shop_id فقط از `slug` ِ ویترینِ روشن، شماره فقط از بلیتِ امضاشده.
 * ⛔ سقفِ درخواست: خواندن با سطلِ `generalMax` به‌ازای IP؛ نوشتن و بارگذاری
 * به‌ازای (دکان، شماره) — تا پشتِ یک IP ِ مشترک، مشتری‌ها جای هم را نگیرند.
 */
const express = require('express');
const config = require('../config');
const chat = require('../lib/shop-chat');
const { goneSignal } = require('../lib/chat-wait');
const { rateLimit } = require('../middleware/ratelimit');
const { ApiError, notFound } = require('../middleware/errors');

const router = express.Router();

//  شماره با کلیدِ یکتا (RSK-07): شکلِ دیگرِ همان گوشی سطلِ تازه نمی‌گیرد
const viewerKey = (req) => (req.chatViewer ? `${req.chatViewer.shopId}:${require('../lib/validate').phoneKey(req.chatViewer.phone)}` : '');
const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-chat-read' });
const sendLimit = rateLimit({ max: config.shopChat.sendMax, keyPrefix: 'store-chat-send', key: viewerKey });
const uploadLimit = rateLimit({ max: config.shopChat.uploadMax, keyPrefix: 'store-chat-upload', key: viewerKey });

/** ماژولِ ویترین — اگر هنوز روی این سرور نیست، null (نه خطای ۵۰۰). */
function storefront() {
  try {
    return require('../lib/storefront');
  } catch (err) {
    if (err && err.code === 'MODULE_NOT_FOUND' && /lib[\\/]storefront/.test(String(err.message))) return null;
    throw err;
  }
}

const customer = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  const sf = storefront();
  if (!sf || typeof sf.open !== 'function' || typeof sf.verifiedPhoneOfTicket !== 'function') {
    throw notFound('این ویترین پیدا نشد', 'store_not_found');
  }
  const store = await sf.open(req.params.slug);
  if (!(await chat.flagOf(store.shop_id))) throw notFound('چت برای این دکان روشن نیست', 'chat_disabled');
  const phone = await sf.verifiedPhoneOfTicket(req.get('x-store-ticket') || '');
  if (!phone) throw new ApiError(401, 'verification_required', 'برای چت با دکان، شمارهٔ خود را تأیید کنید');
  req.chatViewer = { side: 'customer', shopId: store.shop_id, phone };
  next();
};

const base = '/:slug/chat';
const v = (req) => req.chatViewer;

router.get(`${base}/conversations`, readLimit, customer, async (req, res) => {
  res.json(await chat.list(v(req), { cursor: req.query.cursor, limit: req.query.limit }));
});

router.post(`${base}/conversations`, readLimit, customer, sendLimit, async (req, res) => {
  const out = await chat.open(v(req), req.body);
  res.status(out.created ? 201 : 200).json({ conversation: out.conversation });
});

router.get(`${base}/search`, readLimit, customer, async (req, res) => {
  res.json(await chat.search(v(req), req.query.q, { limit: req.query.limit }));
});

router.get(`${base}/conversations/:id`, readLimit, customer, async (req, res) => {
  res.json({ conversation: await chat.viewOf(v(req), req.params.id) });
});

router.get(`${base}/conversations/:id/messages`, readLimit, customer, async (req, res) => {
  const out = await chat.changes(v(req), req.params.id, {
    after: req.query.after, limit: req.query.limit, wait: req.query.wait, signal: goneSignal(res),
  });
  if (out) res.json(out); //  null ⇒ کلاینت وسطِ long-poll رفت
});

router.post(`${base}/conversations/:id/messages`, readLimit, customer, sendLimit, async (req, res) => {
  const out = await chat.send(v(req), req.params.id, req.body);
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  res.status(out.replayed ? 200 : 201).json(out);
});

router.patch(`${base}/conversations/:id/messages/:mid`, readLimit, customer, sendLimit, async (req, res) => {
  res.json(await chat.edit(v(req), req.params.id, req.params.mid, req.body));
});

router.delete(`${base}/conversations/:id/messages/:mid`, readLimit, customer, async (req, res) => {
  res.json(await chat.remove(v(req), req.params.id, req.params.mid, req.query.for));
});

router.post(`${base}/conversations/:id/receipts`, readLimit, customer, async (req, res) => {
  res.json(await chat.receipts(v(req), req.params.id, req.body));
});

router.post(`${base}/conversations/:id/media`, readLimit, customer, uploadLimit, chat.rawMedia, async (req, res) => {
  res.status(201).json(await chat.putMedia(v(req), req.params.id, req.body, { role: req.query.role }));
});

router.get(`${base}/conversations/:id/media/:mediaId`, readLimit, customer, async (req, res) => {
  chat.sendMedia(res, await chat.getMedia(v(req), req.params.id, req.params.mediaId));
});

router.put(`${base}/conversations/:id/block`, readLimit, customer, async (req, res) => {
  res.json({ conversation: await chat.setBlock(v(req), req.params.id, true) });
});

router.delete(`${base}/conversations/:id/block`, readLimit, customer, async (req, res) => {
  res.json({ conversation: await chat.setBlock(v(req), req.params.id, false) });
});

router.post(`${base}/conversations/:id/reports`, readLimit, customer, sendLimit, async (req, res) => {
  res.status(201).json(await chat.report(v(req), req.params.id, req.body));
});

module.exports = router;
