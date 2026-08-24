'use strict';
/**
 * کاتالوگ قابلیت‌ها (Permissions).
 *
 * این فایل تنها منبع حقیقت برای نام قابلیت‌هاست و هم سرور و هم پنل مدیریت
 * از آن استفاده می‌کنند. کلاینت نسخه‌ای از همین لیست را دارد، ولی تصمیم
 * نهایی همیشه بر اساس قابلیت‌های امضاشده داخل License گرفته می‌شود.
 *
 * core: قابلیت پایه — هرگز قفل نمی‌شود، حتی بعد از پایان اشتراک.
 *       (کاربر باید بتواند وارد برنامه شود و اطلاعاتش را ببیند.)
 */
const FEATURES = [
  { key: 'dashboard',  label: 'داشبورد',                 core: true  },
  { key: 'products',   label: 'محصولات',                 core: true  },
  { key: 'settings',   label: 'تنظیمات',                 core: true  },

  { key: 'sales',      label: 'فروش (صندوق)',            core: false },
  { key: 'warehouse',  label: 'انبار',                   core: false },
  { key: 'debtors',    label: 'قرض‌داران',                core: false },
  { key: 'expenses',   label: 'مصارف',                   core: false },
  { key: 'purchasing', label: 'خرید و تأمین‌کننده',       core: false },
  { key: 'reports',    label: 'گزارشات',                 core: false },
  { key: 'audit_log',  label: 'سابقه عملیات',            core: false },
  { key: 'barcode',    label: 'اسکنر بارکد',             core: false },
  { key: 'backup',     label: 'پشتیبان‌گیری و بازیابی',   core: false },
  { key: 'csv_export', label: 'خروجی CSV و چاپ گزارش',   core: false },
];

const ALL_KEYS       = FEATURES.map(f => f.key);
const CORE_KEYS      = FEATURES.filter(f => f.core).map(f => f.key);
const GRANTABLE_KEYS = FEATURES.filter(f => !f.core).map(f => f.key);

/** فقط کلیدهای شناخته‌شده و غیرتکراری را نگه می‌دارد. */
function sanitizeFeatures(input) {
  if (!Array.isArray(input)) return [];
  const seen = new Set();
  const out = [];
  for (const raw of input) {
    if (typeof raw !== 'string') continue;
    const key = raw.trim();
    if (!GRANTABLE_KEYS.includes(key) || seen.has(key)) continue;
    seen.add(key);
    out.push(key);
  }
  return out;
}

function isKnownFeature(key) { return ALL_KEYS.includes(key); }
function isCoreFeature(key)  { return CORE_KEYS.includes(key); }

module.exports = { FEATURES, ALL_KEYS, CORE_KEYS, GRANTABLE_KEYS, sanitizeFeatures, isKnownFeature, isCoreFeature };
