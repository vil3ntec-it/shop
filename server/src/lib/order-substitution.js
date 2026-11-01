'use strict';
/**
 * ترجیحِ جایگزینی (CHK-15، فاز ۴؛ مهاجرتِ ۱۴۴) — «یکی کلی و یکی برای هر قلم: بهترین مشابه،
 * کالای مشخص، بدون جایگزین، یا بازپرداخت».
 *
 *   کلی ──── `online_orders.substitution`: '' | best_match | none | refund. «کالای مشخص» برای
 *            کلِ سفارش معنا ندارد ⇒ ۴۰۰.
 *   قلم ──── `online_order_lines.substitution` (همان پنج مقدارِ پیشین). قلمِ بی ترجیح ترجیحِ کلی
 *            را **در ثبت** می‌گیرد، تا هر خواننده (کارتِ سفارش، جمع‌آوری) فقط قلم را بخواند.
 *   مشخص ── «کالای مشخص» جایگزینِ نام‌دار می‌خواهد: ویترین شناسهٔ کالایی از همان ویترین
 *            (منتشرشده و زنده، نه خودِ همان قلم) و سرور نامش را همان لحظه از دفتر می‌خواند؛
 *            کارمند نامِ آزاد (و شناسهٔ اختیاری) می‌نویسد.
 *
 * ⛔ پرچمِ جدا و پیش‌فرض خاموشِ `shop.orders.substitution` (live_config، دکان یا all): خاموش ⇒
 *    همان رفتارِ امروز — ویترین ترجیح نمی‌گیرد، کارمند فقط ترجیحِ قلم را (بی نام) و هیچ کلیدِ
 *    تازه‌ای خوانده نمی‌شود. برنامهٔ دکان کادرِ ترجیحِ کلی را فقط با `substitution` ِ `/config` نشان می‌دهد.
 */
const { badRequest, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.orders.substitution';
const LINE_VALUES = Object.freeze(['', 'best_match', 'specific', 'none', 'refund']);
const ORDER_VALUES = Object.freeze(['', 'best_match', 'none', 'refund']);
const PRODUCT_ID_RE = /^[A-Za-z0-9_.:-]{1,80}$/;

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

/** پرچمِ همین دکان (پیش‌فرض خاموش). */
const flagOn = (shopId) => require('./orders').flagOf(shopId, FLAG_KEY);

/** ترجیحِ کلیِ سفارش از بدنه — خاموش ⇒ ''. */
function orderOf(body, on) {
  if (!on) return '';
  const v = str(obj(body).substitution);
  if (!ORDER_VALUES.includes(v)) throw badRequest('ترجیحِ کلیِ جایگزینی معتبر نیست', 'bad_substitution');
  return v;
}

/** ترجیحِ مؤثرِ قلم: خودش، وگرنه ترجیحِ کلی. */
const effective = (own, orderDefault) => own || orderDefault || '';

/**
 * قلمِ سفارشِ کارمند — `substitution` را `parseCreate` (رفتارِ پیشین) سنجیده است. روشن ⇒
 * نامِ جایگزینِ «کالای مشخص» لازم و شناسهٔ اختیاری؛ خاموش ⇒ هیچ کلیدِ تازه‌ای خوانده نمی‌شود.
 */
function staffLine(raw, substitution, orderDefault, on, i) {
  if (!on) return { substitution, substituteProductId: '', substituteName: '' };
  const x = obj(raw);
  const eff = effective(substitution, orderDefault);
  if (eff !== 'specific') return { substitution: eff, substituteProductId: '', substituteName: '' };
  const substituteName = text(x.substituteName, 200);
  if (!substituteName) throw badRequest(`قلمِ ${i + 1}: نامِ کالای جایگزین لازم است`, 'bad_substitution');
  const substituteProductId = str(x.substituteProductId);
  if (substituteProductId && !PRODUCT_ID_RE.test(substituteProductId)) {
    throw badRequest(`قلمِ ${i + 1}: شناسهٔ کالای جایگزین معتبر نیست`, 'bad_substitution');
  }
  return { substitution: eff, substituteProductId, substituteName };
}

/** قلمِ ویترین (پیش از قیمت‌گذاری) — خاموش ⇒ همان ترجیحِ خالیِ امروز. */
function storeLine(raw, productId, on, i) {
  if (!on) return { substitution: '', substituteProductId: '' };
  const x = obj(raw);
  const substitution = str(x.substitution);
  if (!LINE_VALUES.includes(substitution)) throw badRequest(`قلمِ ${i + 1}: ترجیحِ جایگزینی معتبر نیست`, 'bad_substitution');
  if (substitution !== 'specific') return { substitution, substituteProductId: '' };
  const substituteProductId = str(x.substituteProductId);
  if (!PRODUCT_ID_RE.test(substituteProductId)) {
    throw badRequest(`قلمِ ${i + 1}: کالای جایگزین را برگزینید`, 'bad_substitution');
  }
  if (substituteProductId === productId) throw badRequest(`قلمِ ${i + 1}: جایگزین همان کالاست`, 'bad_substitution');
  return { substitution, substituteProductId };
}

/** شناسه‌های قیمت‌گذاری: کالاهای سبد + جایگزین‌های مشخص (برای خواندنِ نامشان در همان پرس‌وجو). */
function withSubstitutes(ids, lines) {
  const subs = lines.map((l) => l.substituteProductId).filter((id) => id && !ids.includes(id));
  return [...ids, ...new Set(subs)];
}

/**
 * ترجیحِ نهاییِ قلمِ ویترین درونِ تراکنشِ ثبت — جایگزینِ مشخص باید همین لحظه در ویترین باشد
 * (منتشرشده، زنده، با نام و قیمت)؛ نبودش ⇒ ۴۰۹ تا مشتری دوباره برگزیند، هرگز جایگزینِ بی‌نام.
 */
function storeResolved(l, byId, orderDefault) {
  const substitution = effective(l.substitution, orderDefault);
  if (substitution !== 'specific') return { substitution, substituteProductId: '', substituteName: '' };
  const p = byId.get(l.substituteProductId);
  if (!p || !p.name || p.priceMinor === null) {
    throw Object.assign(conflict('کالای جایگزینِ برگزیده دیگر در ویترین نیست', 'substitute_unavailable'),
      { data: { productId: l.productId, substituteProductId: l.substituteProductId } });
  }
  return { substitution, substituteProductId: p.id, substituteName: p.name };
}

/** نمای مشتریِ قلم — فقط ترجیحِ خودش (نامِ جایگزین همان که دید). */
const publicLine = (l) => ({ substitution: l.substitution || '', substituteName: l.substituteName || '' });

module.exports = {
  FLAG_KEY, LINE_VALUES, ORDER_VALUES, flagOn, orderOf, effective, staffLine, storeLine, withSubstitutes,
  storeResolved, publicLine,
};
