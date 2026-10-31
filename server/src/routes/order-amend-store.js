'use strict';
/**
 * ORD-14 — رضایتِ مشتریِ ویترین به ویرایشِ سفارش: `/api/store/:slug/orders/:token/amendments/:amendId/{accept,reject}`.
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `shop.orders.amend` ⇒ ۴۰۴ (همان «نیست») و هیچ ردیفی.
 * ⛔ توکنِ پیگیری همان اجازه است (مثلِ «لغو» و «پرداخت»): ویترینِ روشن، توکنِ درست و سفارشِ
 *    همین ویترین — وگرنه ۴۰۴ (`sf.track`). دکان، سفارش و پیشنهاد فقط از توکن و نشانی، نه از بدنه.
 * ⛔ سقف: همان سطلِ «ثبت» ِ ویترین به‌ازای IP.
 * پیشوندِ تازه نیست (`/store`)؛ ACCOUNT_PREFIXES دست نمی‌خورد.
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const amend = require('../lib/order-amend');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();
const limit = rateLimit({ max: config.rateLimit.storeCheckoutMax, keyPrefix: 'store-amend' });
const CUSTOMER = Object.freeze({ type: 'customer', id: '' });
const MESSAGE = {
  amend_expired: 'مهلتِ این پیشنهاد گذشته است؛ سفارش همان پیشین ماند',
  order_changed: 'سفارش از آن پس عوض شده است؛ این پیشنهاد دیگر اعمال نمی‌شود',
  amend_closed: 'این پیشنهاد دیگر باز نیست',
};

function decide(decision) {
  return async (req, res) => {
    res.set('Cache-Control', 'no-store');
    const store = await sf.open(req.params.slug);
    if (!(await amend.flagOf(store.shop_id))) throw notFound('پیدا نشد', 'not_found');
    await sf.track(store, req.params.token);   // توکنِ درست و سفارشِ همین ویترین — وگرنه ۴۰۴
    const orderId = sf.orderIdOf(store.shop_id, req.params.token);
    const out = await amend.decide(store.shop_id, orderId, req.params.amendId, decision, CUSTOMER);
    const order = await sf.track(store, req.params.token);   // با کلیدِ `amendment` (پرچم روشن است)
    if (out.code) {
      return res.status(409).json({ error: { code: out.code, message: MESSAGE[out.code], status: out.status }, order });
    }
    res.json({ status: out.status, unchanged: out.unchanged, order });
  };
}

router.post('/:slug/orders/:token/amendments/:amendId/accept', limit, decide('accept'));
router.post('/:slug/orders/:token/amendments/:amendId/reject', limit, decide('reject'));

module.exports = router;
