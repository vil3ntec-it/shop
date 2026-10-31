'use strict';
/**
 * جمع‌آوری و بسته‌بندیِ سفارش (PCK، v3 §۶.۱۷) — پشتِ پرچمِ خاموشِ `shop.orders.picking`.
 *
 *   پرچم ── `shop.orders.picking` در «تنظیماتِ زنده» (scope = شناسهٔ دکان یا `all`؛ مقدارِ
 *           خودِ دکان جلو می‌افتد و فقط `true` روشن است — همان `orders.flagOf`).
 *           ⛔ پیش‌فرض خاموش: برنامه هیچ چیزِ تازه‌ای نشان نمی‌دهد و هیچ ردیفی نوشته نمی‌شود.
 *   گامِ ۱ ─ PCK-11: برنامهٔ مدیریت با این پرچم QRِ کدِ تحویل/دریافت را کنارِ عدد نشان می‌دهد
 *           و اسکنِ آن را در کادرِ کد و جست‌وجوی «دریافتِ حضوری» می‌پذیرد
 *           (`core/pick/HandoverCode.kt`). کد همچنان فقط HMAC در سرور است (`lib/delivery.js`).
 *
 * `GET /api/shop/orders/config` و `GET /api/shop/delivery/config` هر دو `picking` را می‌گویند.
 */
const orders = require('./orders');

const FLAG_KEY = 'shop.orders.picking';

/** پرچمِ جمع‌آوری برای این دکان (بی `shop.orders` هم خوانده می‌شود؛ درها جدا می‌سنجند). */
async function flagOf(shopId) {
  return orders.flagOf(shopId, FLAG_KEY);
}

module.exports = { FLAG_KEY, flagOf };
