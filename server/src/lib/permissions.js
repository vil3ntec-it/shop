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

  'settings.write':       ['owner', 'manager'],
  'reports.view':         ['owner', 'manager', 'staff'],
  'audit.view':           ['owner', 'manager'],
  'backup.run':           ['owner'],
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
