'use strict';
/**
 * چند سفارش در یک مسیر با ترتیبِ پیشنهادی — DLV-04 (v3 §۶.۱۸)، پرچمِ `shop.delivery_route`.
 *
 * ترتیب را خودِ برنامهٔ پیک روی دستگاه و بی اینترنت می‌سازد (`core/courier/RoutePlan.forCards`، بردارِ
 * `core/test-vectors/courier-route-v1.json`، `RoutePlanVectorsTest`)؛ سرور فقط پرچم را در `GET /api/courier/me`
 * (`routeEnabled`) می‌گوید. ⛔ درِ دوقلوی سرور (`GET /api/courier/route`) صدازننده نداشت و برداشته شد (B15 ِ
 * `docs/SECURITY-AUDIT-fa.md`، `test/dead-doors.test.js`) ⇒ موقعیتِ پیک برای مسیر هرگز به سرور نمی‌رود (DLV-16).
 */
const FLAG = 'shop.delivery_route';

module.exports = { FLAG };
