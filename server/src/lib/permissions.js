'use strict';
/**
 * دسترسی‌ها بر پایه‌ی نقش — تصمیم همیشه سمت سرور گرفته می‌شود.
 *
 * پنهان کردن یک دکمه در گوشی امنیت نیست؛ کسی که درخواست را دستی
 * بسازد باز هم به همین جدول می‌رسد.
 *
 *   owner    صاحب دکان — همه‌کاره
 *   manager  مدیر — کارهای روزمره و مدیریتی، بجز مالکیت و اشتراک
 *   staff    شاگرد — فروش و ثبت روزمره؛ حذف فقط روی رکورد خودش
 */
const ROLES = ['owner', 'manager', 'staff'];

const MATRIX = {
  'shop.view':            ['owner', 'manager', 'staff'],
  'shop.update':          ['owner'],
  'shop.delete':          ['owner'],

  'members.view':         ['owner', 'manager'],
  'members.manage':       ['owner'],
  'members.role':         ['owner'],

  'staffcode.view':       ['owner', 'manager'],
  'staffcode.create':     ['owner'],
  'staffcode.revoke':     ['owner'],

  'subscription.view':    ['owner', 'manager', 'staff'],
  'subscription.request': ['owner', 'manager'],

  'data.read':            ['owner', 'manager', 'staff'],
  'data.write':           ['owner', 'manager', 'staff'],
  'data.delete.any':      ['owner', 'manager'],
  'data.delete.own':      ['owner', 'manager', 'staff'],

  //  کارهای پولیِ حساس — شاگرد نه. سرور در Sync v1 همین را می‌سنجد
  //  (`authorizeOp`)، نه فقط پنهان‌شدنِ دکمه در برنامه.
  'sales.discount':       ['owner', 'manager'],
  'sales.cancel':         ['owner', 'manager'],

  'settings.write':       ['owner', 'manager'],

  //  موجودیِ سرور (v3 §۳۲، INV-08/INV-12) — پشتِ پرچمِ `shop.catalog`. جابه‌جاییِ
  //  قفسه/انبار کارِ روزمره است؛ خراب، مرجوعی، اصلاح و شمارش دلیل و مدیر می‌خواهد.
  'stock.view':           ['owner', 'manager', 'staff'],
  'stock.move':           ['owner', 'manager', 'staff'],
  'stock.adjust':         ['owner', 'manager'],
  'reports.view':         ['owner', 'manager', 'staff'],
  'audit.view':           ['owner', 'manager'],
  'backup.run':           ['owner'],

  //  سفارشِ آنلاین (فاز ۴) — پشتِ پرچمِ `shop.orders`، پیش‌فرض خاموش.
  //  شمارهٔ کاملِ مشتری فقط برای «کاربرِ مجاز» (ORD-17)؛ بازپرداخت پولی است.
  'orders.view':          ['owner', 'manager', 'staff'],
  'orders.create':        ['owner', 'manager', 'staff'],
  'orders.manage':        ['owner', 'manager', 'staff'],
  'orders.contact':       ['owner', 'manager'],
  'orders.refund':        ['owner', 'manager'],

  //  تحویل و پیک (۲۲.۲ C) — پشتِ پرچمِ `shop.delivery`، پیش‌فرض خاموش.
  //  پیک عضوی است که ردیفِ فعالِ `shop_couriers` دارد و فقط مأموریت‌های خودش
  //  را از `/api/courier` می‌بیند. نقشِ عضویتِ `courier` (DLV-01، مهاجرتِ ۱۰۲)
  //  عمداً در هیچ سطرِ این جدول نیست: توکنش دامنهٔ `courier` دارد و جز درِ
  //  پیک همه‌جا `403 courier_scope` می‌گیرد (`middleware/auth.js`).
  'delivery.view':        ['owner', 'manager', 'staff'],
  'delivery.assign':      ['owner', 'manager'],
  'delivery.handover':    ['owner', 'manager', 'staff'],
  'delivery.couriers':    ['owner', 'manager'],
  'delivery.settle':      ['owner', 'manager'],
  'delivery.photo':       ['owner', 'manager'],

  //  چتِ مشتری ↔ دکان (v3 §۳۳) — پشتِ پرچمِ `shop.chat`، پیش‌فرض خاموش.
  //  شمارهٔ کاملِ مشتری در چت هم همان `orders.contact` را می‌خواهد.
  'chat.view':            ['owner', 'manager', 'staff'],
  'chat.reply':           ['owner', 'manager', 'staff'],
  'chat.moderate':        ['owner', 'manager'],

  //  شکایت و تخلفِ بازارچه (v3 §۳۸.۶) — پشتِ پرچمِ `market.trust`، پیش‌فرض خاموش.
  //  دیدنِ پرونده‌های دکان، پاسخ و اعتراض فقط صاحب و مدیر؛ هویتِ شاکی برای هیچ‌کدام نیست.
  'trust.view':           ['owner', 'manager'],
  'trust.respond':        ['owner', 'manager'],
};

function can(role, permission) {
  const allowed = MATRIX[permission];
  if (!allowed) return false;
  return allowed.includes(role);
}

/** فهرست دسترسی‌های یک نقش — برنامه با همین، دکمه‌ها را می‌چیند. */
function permissionsOf(role) {
  return Object.keys(MATRIX).filter(k => can(role, k));
}

module.exports = { ROLES, MATRIX, can, permissionsOf };
