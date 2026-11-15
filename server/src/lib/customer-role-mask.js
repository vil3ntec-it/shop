'use strict';
/**
 * CRM-15 — شماره و نشانیِ مشتری بر پایهٔ نقش (کمترین دسترسی؛ تصمیمِ هماهنگ‌کننده به سپردهٔ صاحب‌کار، ۱۴۰۵/۰۸/۲۴)،
 * پشتِ پرچمِ پیش‌فرض‌خاموشِ `shop.customer_role_mask`. همان ماتریسِ هستهٔ برنامه (`core/crm/CustomerMask.kt`):
 *
 *   صاحب ............ کامل، همیشه.
 *   مدیر ............ کامل، مگر صاحب `customers.contact` را برایش بسته باشد.
 *   کارمندان/صندوق‌دار . شماره «•••• ۰۰۸» و نشانی پنهان؛ جز سفارشِ بازی که خودش ساخت یا رویش کاری کرد ([handling]).
 *   پیک ............. فقط مأموریتِ خودش تا تحویل (`delivery-views.courierView`: نشانی و تماس فقط `live`) — دست نخورد.
 *   حسابدار ......... نام و مانده؛ شماره پوشیده و نشانی پنهان.
 * پیش‌فرضِ تازه فقط یک لایهٔ «deny» پیش از لایه‌های صاحب است ([layersWith])، پس اجازهٔ صریحِ صاحب (لایهٔ نقش یا کاربر) می‌چربد.
 *
 * چون `customer-contact.allowed`/`hiddenForReq` این لایه را می‌گیرند، همهٔ درهای امروزِ CRM-15 خودبه‌خود پیروی می‌کنند:
 * pull/Snapshot ِ همگام‌سازی (فیلدها نیستند)، صندوقِ تعارض، کارت و جزئیاتِ سفارش، «سفارش‌های یک مشتری»، «خبرم کن»، سدِ تخفیف.
 * فهرست‌های مشتری با شمارهٔ پوشیده قالبِ [maskPhone] را می‌گیرند (`customer-contact.maskerForReq`).
 *
 * پرچمِ خاموش ⇒ بیت‌به‌بیت همان امروز (هیچ پرسشِ اضافه جز خواندنِ همان پرچم).
 */
const FLAG = 'shop.customer_role_mask';
const KEY = 'customers.contact';
const KEEP = 3;
const MASK = '••••';
const DENY_LAYER = Object.freeze({ [KEY]: Object.freeze({ mode: 'deny' }) });
const FULL_ROLES = new Set(['owner', 'manager']);
const ND = /^\p{Nd}$/u;
const OPEN = new Set(['\u2066', '\u2067', '\u2068', '\u202A', '\u202B', '\u200E', '\u200F']);
const CLOSE = new Set(['\u2069', '\u202C', '\u200E', '\u200F']);

/** «•••• ۰۰۸» — همان `CustomerMask.masked` ِ برنامه: چهار نقطه و سه رقمِ آخر؛ ≤ ۳ رقم ⇒ فقط نقطه؛ ایمیل ⇒ نویسهٔ نخست و دامنه */
function maskPhone(text) {
  const s = String(text ?? '');
  if (!s.trim()) return '';
  const at = s.indexOf('@');
  if (at > 0) return `${s[0]}•••${s.slice(at)}`;
  let i = 0;
  while (i < s.length && OPEN.has(s[i])) i++;
  let j = s.length;
  while (j > i && CLOSE.has(s[j - 1])) j--;
  const digits = [...s].filter((ch) => ND.test(ch)).join('');
  const body = digits.length <= KEEP ? MASK : `${MASK} ${digits.slice(-KEEP)}`;
  return s.slice(0, i) + body + s.slice(j);
}

/** پرچمِ همین دکان (حافظهٔ کوتاهِ `orders.flagOf`)؛ بی دکان ⇒ خاموش */
async function onFor(shopId, run = null) {
  if (!shopId) return false;
  return require('./orders').flagOf(shopId, FLAG, run);
}

/** پرچم روشن و نقشِ جز صاحب/مدیر ⇒ [DENY_LAYER] پیش از لایه‌های صاحب */
function layersWith(role, layers, on) {
  if (!on || FULL_ROLES.has(role)) return layers;
  return [DENY_LAYER, ...(Array.isArray(layers) ? layers : [])];
}

/** همان [layersWith] با خواندنِ پرچمِ دکان */
async function layersFor(shopId, role, layers, run = null) {
  if (FULL_ROLES.has(role)) return layers;
  return layersWith(role, layers, await onFor(shopId, run));
}

/**
 * «سفارشی که خودش در دست دارد»: سفارشِ باز (`orders.OPEN_STATUSES`)، بیننده اجازهٔ کار روی سفارش دارد (`orders.manage`؛
 * حسابدار نه) و خودش آن را ساخت یا یکی از رویدادهایش با نامِ اوست (پذیرفتن، جمع‌آوری، بسته‌بندی…).
 */
function handling(row, events, viewer) {
  if (!row || !viewer || !viewer.id) return false;
  const { OPEN_STATUSES } = require('./orders');
  if (!OPEN_STATUSES.includes(row.status)) return false;
  if (!require('./permissions').can(viewer.role, 'orders.manage')) return false;
  if (row.created_by && row.created_by === viewer.id) return true;
  return (events || []).some((e) => e.actor_type === 'user' && e.actor_id === viewer.id);
}

/**
 * جزئیاتِ یک سفارش (`orders.loadDetail`): `contact` = شمارهٔ کامل، `mask` = پوشانندهٔ کارت، `apply` = پالایشِ پایانی.
 * پرچمِ خاموش ⇒ همان امروز (`ordersContact` و `maskerOf`، `apply` بی‌اثر). روشن ⇒ کسی که شمارهٔ کامل را بیرون از سفارش نمی‌بیند
 * نشانی را هم نمی‌گیرد، مگر همین سفارش را در دست دارد؛ آن‌وقت شماره و نشانی هر دو (برای کارش لازم است).
 */
async function forDetail(shopId, viewer, row, events, run = null) {
  const cc = require('./customer-contact');
  const who = { shopId, role: viewer && viewer.role, userId: viewer && viewer.id };
  let contact = await cc.ordersContact(who, run);
  const mask = await require('./order-phone-mask').maskerOf(shopId, viewer, run);
  if (!(await onFor(shopId, run))) return { contact, mask, apply: (d) => d };
  const full = await cc.allowed(who, run);
  const mine = !full && handling(row, events, viewer);
  if (mine) contact = true;
  const hide = !full && !mine;
  return { contact, mask, apply: (d) => (hide ? { ...d, address: {} } : d) };
}

module.exports = { FLAG, KEY, KEEP, DENY_LAYER, maskPhone, onFor, layersWith, layersFor, handling, forDetail };
