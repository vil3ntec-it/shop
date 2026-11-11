'use strict';
/**
 * شمارهٔ مشتری روی کارتِ سفارش — از `orders.js` جدا شد تا آن پرونده زیرِ سقفِ ۸۰۰ خط بماند (رفتار همان).
 *   `maskPhone`  ۴ رقمِ اول + ۳ رقمِ آخر (کارتِ سبک، فهرست؛ ORD-11)
 *   `maskerOf`   ۲-۸ (CRM-15 الف) پشتِ پرچمِ خاموشِ `shop.orders.phone_mask`
 */
function maskPhone(p) {
  const s = String(p || '');
  if (!s) return '';
  if (s.length <= 7) return '•'.repeat(s.length);
  return s.slice(0, 4) + '•'.repeat(s.length - 7) + s.slice(-3);
}

/** ۲-۸ (CRM-15 الف) — پرچمِ خاموش: کارتِ بیننده‌ای که «دیدنِ تماس» ندارد فقط دو رقمِ آخر ([maskerOf]) */
const PHONE_MASK_FLAG = 'shop.orders.phone_mask';

/**
 * پوشانندهٔ `phoneMasked` ِ کارت برای [viewer]: پرچمِ خاموش، یا بیننده‌ای که `customers.contact` دارد (صاحب، لایه‌ها) ⇒ همان
 * [maskPhone] ِ امروز (۴+۳)؛ وگرنه همان قالبِ `ContactMask` ِ برنامه (هر رقم جز دو رقمِ آخر). شمارهٔ کامل همان `detail`
 * (`orders.contact` و `customers.contact`) و پیکِ همان سفارش با درِ تماس (`delivery-call`)؛ این‌جا دست نمی‌خورند.
 * `run` = اتصالِ تراکنشِ جاری (درونِ قفلِ سفارش، نه استخر).
 */
async function maskerOf(shopId, viewer, run = null) {
  if (!(await require('./orders').flagOf(shopId, PHONE_MASK_FLAG, run))) return maskPhone;
  const cc = require('./customer-contact');
  if (await cc.allowed({ shopId, role: viewer && viewer.role, userId: viewer && viewer.id }, run)) return maskPhone;
  return (p) => (p ? cc.maskPhone(p) : '');
}

module.exports = { maskPhone, PHONE_MASK_FLAG, maskerOf };
