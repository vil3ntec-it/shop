'use strict';
/**
 * چتِ دکان ↔ حسابِ مشتریِ بازار (S9، پرچمِ `market.account`، مهاجرتِ ۱۲۹ `shop_chat_conversations.customer_id`)
 * — جدا از `lib/shop-chat.js` تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)؛ رفتار همان.
 * بینندهٔ مشتری با نشستِ حساب `{ side: 'customer', shopId, phone, customerId }` است؛ با بلیتِ ویترین
 * `customerId` ندارد. پرچمِ خاموش ⇒ `customer_id` همیشه NULL و همه‌چیز همان «customer_phone = شماره».
 */
const { one, query } = require('../db');

/**
 * «مالِ همین مشتری» — شرطِ SQL، با افزودنِ پارامترها به `args`.
 *   حساب (`customerId`) ⇒ گفت‌وگوی همین حساب، یا گفت‌وگوی بی‌حسابِ شمارهٔ کنونی؛
 *                          پس تغییرِ شماره گفت‌وگوهای پیشین را گم نمی‌کند.
 *   بلیت (فقط شماره)    ⇒ همان امروز، مگر گفت‌وگو مالِ حسابی شده باشد که شماره‌اش
 *                          دیگر این نیست (دارندهٔ تازهٔ شمارهٔ کهنه چیزی نمی‌بیند).
 */
function mineSql(viewer, args, a = 'c') {
  args.push(viewer.phone);
  const p = `$${args.length}`;
  if (viewer.customerId) {
    args.push(viewer.customerId);
    return `(${a}.customer_id = $${args.length} OR (${a}.customer_id IS NULL AND ${a}.customer_phone = ${p}))`;
  }
  return `(${a}.customer_phone = ${p} AND (${a}.customer_id IS NULL OR EXISTS (`
    + `SELECT 1 FROM store_customers sc WHERE sc.id = ${a}.customer_id AND sc.phone = ${p})))`;
}

/** گفت‌وگوی مشتری برای `open`: حساب ممکن است دو تا ببیند (یکی با شمارهٔ پیشین) — گفت‌وگوی خودِ حساب جلوتر. */
function findForCustomer(viewer, orderId) {
  const args = [viewer.shopId, orderId];
  return one(
    `SELECT c.id, c.customer_id FROM shop_chat_conversations c WHERE c.shop_id=$1 AND c.order_id=$2
       AND ${mineSql(viewer, args)} ORDER BY (c.customer_id IS NULL), c.created_at, c.id LIMIT 1`,
    args
  );
}

/**
 * `open` ِ مشتری روی گفت‌وگوی موجود: نشستِ حساب و گفت‌وگوی بی‌حساب ⇒ از این پس مالِ همین حساب؛ نامِ
 * فرستاده فقط وقتی گفت‌وگو هنوز نام ندارد (همان رفتارِ پیشینِ `open`).
 */
async function reopen(viewer, row, name) {
  if (viewer.customerId && !row.customer_id) {
    await query('UPDATE shop_chat_conversations SET customer_id=$2 WHERE id=$1 AND customer_id IS NULL', [row.id, viewer.customerId]);
  }
  if (name) await query("UPDATE shop_chat_conversations SET customer_name=$2 WHERE id=$1 AND customer_name=''", [row.id, name]);
}

/**
 * تغییرِ شمارهٔ حساب (در تراکنشِ `store-customers.changePhone`): گفت‌وگوهای بی‌حسابِ شمارهٔ پیشین مالِ
 * همین حساب می‌شوند و بستن‌های آن شماره روی شمارهٔ تازه هم می‌نشیند (بستنِ دکان با عوض کردنِ شماره دور
 * زده نمی‌شود). شمارهٔ گفت‌وگوها (`customer_phone`) برای سازگاری همان می‌ماند.
 */
async function adoptForPhoneChange(c, customerId, fromPhone, toPhone) {
  const r = await c.query(
    'UPDATE shop_chat_conversations SET customer_id=$1 WHERE customer_id IS NULL AND customer_phone=$2',
    [customerId, fromPhone]
  );
  await c.query(
    `INSERT INTO shop_chat_blocks (shop_id, customer_phone, blocker, by_user_id, created_at)
     SELECT shop_id, $2, blocker, by_user_id, created_at FROM shop_chat_blocks WHERE customer_phone=$1
     ON CONFLICT DO NOTHING`,
    [fromPhone, toPhone]
  );
  return r.rowCount;
}

module.exports = { mineSql, findForCustomer, reopen, adoptForPhoneChange };
