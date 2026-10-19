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
 *
 * چهار نقشِ تازه (STF-01، A16) — فقط وقتی صاحب دکان خودش بدهد؛ سه نقشِ بالا
 * دست نخورده‌اند. جدول‌های همگام‌سازی که هر نقش می‌نویسد در هستهٔ مشترک است
 * (`perm-core.js` ⇐ `canWrite`) و هر سه راهِ همگام‌سازی همان را اجرا می‌کنند.
 *   cashier      صندوق‌دار — فروش، قرض، برگشت و سفارش؛ نه انبار، نه خرید
 *   storekeeper  انباردار — کالا، رسیدِ انبار، خرید، شمارش و اصلاحِ موجودی
 *   picker       جمع‌آور — دیدن و آماده کردنِ سفارش و سپردن به پیک
 *   accountant   حسابدار — فقط‌خواندنی: همه را می‌بیند، هیچ نمی‌نویسد
 */
const ROLES = ['owner', 'manager', 'staff', 'cashier', 'storekeeper', 'picker', 'accountant'];
/** نقش‌هایی که صاحب دکان به کارمند می‌دهد (کد، تغییرِ نقش، لایهٔ نقش) — پیک جداست */
const STAFF_ROLES = Object.freeze(ROLES.filter((r) => r !== 'owner'));

/** کوتاه‌نویسیِ نقش‌های تازه در سطرهای جدول */
const CSH = 'cashier';
const STK = 'storekeeper';
const PCK = 'picker';
const ACC = 'accountant';

const MATRIX = {
  'shop.view':            ['owner', 'manager', 'staff', CSH, STK, PCK, ACC],
  'shop.update':          ['owner'],
  'shop.delete':          ['owner'],

  'members.view':         ['owner', 'manager'],
  'members.manage':       ['owner'],
  'members.role':         ['owner'],

  'staffcode.view':       ['owner', 'manager'],
  'staffcode.create':     ['owner'],
  'staffcode.revoke':     ['owner'],

  'subscription.view':    ['owner', 'manager', 'staff', CSH, STK, PCK, ACC],
  'subscription.request': ['owner', 'manager'],

  'data.read':            ['owner', 'manager', 'staff', CSH, STK, PCK, ACC],
  'data.write':           ['owner', 'manager', 'staff', CSH, STK, PCK],
  'data.delete.any':      ['owner', 'manager'],
  'data.delete.own':      ['owner', 'manager', 'staff', CSH, STK],

  //  کارهای پولیِ حساس — شاگرد نه. سرور در Sync v1 همین را می‌سنجد
  //  (`authorizeOp`)، نه فقط پنهان‌شدنِ دکمه در برنامه.
  'sales.discount':       ['owner', 'manager'],
  'sales.cancel':         ['owner', 'manager'],

  'settings.write':       ['owner', 'manager'],

  //  موجودیِ سرور (v3 §۳۲، INV-08/INV-12) — پشتِ پرچمِ `shop.catalog`. جابه‌جاییِ
  //  قفسه/انبار کارِ روزمره است؛ خراب، مرجوعی، اصلاح و شمارش دلیل و مدیر می‌خواهد.
  'stock.view':           ['owner', 'manager', 'staff', CSH, STK, PCK, ACC],
  'stock.move':           ['owner', 'manager', 'staff', STK],
  'stock.adjust':         ['owner', 'manager', STK],
  'reports.view':         ['owner', 'manager', 'staff', CSH, STK, ACC],
  'audit.view':           ['owner', 'manager', ACC],
  'backup.run':           ['owner'],

  //  سفارشِ آنلاین (فاز ۴) — پشتِ پرچمِ `shop.orders`، پیش‌فرض خاموش.
  //  شمارهٔ کاملِ مشتری فقط برای «کاربرِ مجاز» (ORD-17)؛ بازپرداخت پولی است.
  'orders.view':          ['owner', 'manager', 'staff', CSH, PCK, ACC],
  'orders.create':        ['owner', 'manager', 'staff', CSH],
  'orders.manage':        ['owner', 'manager', 'staff', CSH, PCK],
  'orders.contact':       ['owner', 'manager'],
  'orders.refund':        ['owner', 'manager'],

  //  تحویل و پیک (۲۲.۲ C) — پشتِ پرچمِ `shop.delivery`، پیش‌فرض خاموش.
  //  پیک عضوی است که ردیفِ فعالِ `shop_couriers` دارد و فقط مأموریت‌های خودش
  //  را از `/api/courier` می‌بیند. نقشِ عضویتِ `courier` (DLV-01، مهاجرتِ ۱۰۲)
  //  عمداً در هیچ سطرِ این جدول نیست: توکنش دامنهٔ `courier` دارد و جز درِ
  //  پیک همه‌جا `403 courier_scope` می‌گیرد (`middleware/auth.js`).
  'delivery.view':        ['owner', 'manager', 'staff', CSH, PCK, ACC],
  'delivery.assign':      ['owner', 'manager'],
  'delivery.handover':    ['owner', 'manager', 'staff', CSH, PCK],
  'delivery.couriers':    ['owner', 'manager'],
  'delivery.settle':      ['owner', 'manager'],
  'delivery.photo':       ['owner', 'manager'],

  //  چتِ مشتری ↔ دکان (v3 §۳۳) — پشتِ پرچمِ `shop.chat`، پیش‌فرض خاموش.
  //  شمارهٔ کاملِ مشتری در چت هم همان `orders.contact` را می‌خواهد.
  'chat.view':            ['owner', 'manager', 'staff', CSH, PCK],
  'chat.reply':           ['owner', 'manager', 'staff', CSH, PCK],
  'chat.moderate':        ['owner', 'manager'],
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

module.exports = { ROLES, STAFF_ROLES, MATRIX, can, permissionsOf };
