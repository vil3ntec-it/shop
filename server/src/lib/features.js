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
  { key: 'multi_device', label: 'چند کاربر روی یک دکان',  core: false },
];

/**
 * قابلیت‌های رایگان — بدون حساب و بدون اشتراک هم کار می‌کنند.
 * چیزهایی که اینجا نیستند، فقط با دوره آزمایشی یا اشتراک باز می‌شوند:
 *   sales (فروش)، barcode (اسکنر)، debtors (قرض‌داران)، multi_device (چند نفر روی یک حساب)
 */
const FREE_KEYS = [
  'warehouse', 'expenses', 'purchasing', 'reports', 'audit_log', 'backup', 'csv_export',
];

const ALL_KEYS       = FEATURES.map(f => f.key);
const CORE_KEYS      = FEATURES.filter(f => f.core).map(f => f.key);
const GRANTABLE_KEYS = FEATURES.filter(f => !f.core).map(f => f.key);

/**
 * فقط کلیدهای شناخته‌شده و غیرتکراری را نگه می‌دارد.
 *
 * `app` می‌گوید کاتالوگِ کدام بخش ملاک است. نیامدنش یعنی دکان — پس هر
 * کدی که از قبل این را یک‌آرگومانی صدا می‌زد، همان رفتارِ قبلی را دارد.
 * قابلیتِ پمپ در اشتراکِ دکان (و برعکس) همین‌جا می‌افتد بیرون.
 */
function sanitizeFeatures(input, app) {
  if (!Array.isArray(input)) return [];
  const allowed = app === 'pump' ? PUMP_GRANTABLE_KEYS : GRANTABLE_KEYS;
  const seen = new Set();
  const out = [];
  for (const raw of input) {
    if (typeof raw !== 'string') continue;
    const key = raw.trim();
    if (!allowed.includes(key) || seen.has(key)) continue;
    seen.add(key);
    out.push(key);
  }
  return out;
}

function isKnownFeature(key) { return ALL_KEYS.includes(key); }
function isCoreFeature(key)  { return CORE_KEYS.includes(key); }

/** قابلیت‌هایی که فقط با اشتراک/دوره آزمایشی باز می‌شوند. */
const PAID_KEYS = GRANTABLE_KEYS.filter(k => !FREE_KEYS.includes(k));

function isFreeFeature(key) { return CORE_KEYS.includes(key) || FREE_KEYS.includes(key); }

// ════════════════════════════════════════════════════════════════════
//  کاتالوگِ بخشِ پمپ‌بنزین
//
//  برنامه‌ی پمپ بخش‌های خودش را دارد و هیچ‌کدام با بخش‌های دکان یکی
//  نیست. کلیدها همان نام‌هایی است که `StationSnapshot` در برنامه‌ی
//  نیتیو به‌کار می‌برد، تا قفل و عکسِ داده یک زبان داشته باشند.
//
//  core یعنی هرگز قفل نمی‌شود — حتی با اشتراکِ تمام‌شده. صاحبِ پمپ
//  باید همیشه بتواند وارد شود و دفترِ خودش را ببیند؛ گروگان گرفتنِ
//  داده، سریع‌ترین راهِ از دست دادنِ اعتماد است.
// ════════════════════════════════════════════════════════════════════
const PUMP_FEATURES = [
  /*
   *  ⚠️ `dashboard` از ۱۴۰۵/۰۶/۳۱ دیگر «هسته» نیست.
   *
   *  خواستهٔ صریحِ صاحب مخزن در `native/docs/PLANS-fa.md`ِ ریپوی پمپ:
   *  «داشبورد هم قفل باشه» در پلنِ استاندارد. تا وقتی `core: true` بود،
   *  هیچ پلنی نمی‌توانست ببنددش — یعنی پلنِ استاندارد روی سرور اصلاً
   *  قابلِ ساختن نبود.
   *
   *  ⛔ و این «گروگان گرفتنِ دفتر» نیست: `debtors` و `settings` هسته
   *  مانده‌اند و همهٔ دفترهای دیگر هم در برنامه قفل نمی‌شوند — برنامه
   *  فقط همین شش دروازه را می‌بندد (‎Services/Entitlements.cs‎).
   */
  { key: 'dashboard',   label: 'داشبورد',             core: false },
  { key: 'settings',    label: 'تنظیمات',             core: true  },
  { key: 'debtors',     label: 'قرض‌داران',            core: true  },

  { key: 'safe',        label: 'گاوصندوق',            core: false },
  { key: 'sarrafi',     label: 'صرافی',               core: false },
  { key: 'expense',     label: 'مصارف',               core: false },
  { key: 'chakana',     label: 'چکنه',                core: false },
  { key: 'extraincome', label: 'عایدات',              core: false },
  { key: 'invoice',     label: 'فاکتورها',            core: false },
  { key: 'storage',     label: 'خریدِ تیل',            core: false },
  { key: 'staff',       label: 'کارمندان',            core: false },
  { key: 'companies',   label: 'شرکت‌های تیل',         core: false },
  { key: 'amanat',      label: 'امانت',               core: false },
  { key: 'reports',     label: 'گزارش و چاپ',         core: false },
  { key: 'kar_app',     label: 'اپِ کارمندان',         core: false },
  { key: 'bot',         label: 'رباتِ دستیار',         core: false },
  { key: 'messenger',   label: 'پیام‌رسان',            core: false },
  { key: 'cloud',       label: 'پوشهٔ ابری و کیو‌آرِ زنده', core: false },
  /*
   *  ⚠️ پشتیبانِ خودکار روی سرور کلیدِ **جدا** دارد و این عمدی است.
   *
   *  تا دیروز هم عکسِ زندهٔ حساب (کیو‌آر) و هم پشتیبان زیرِ `cloud`
   *  بودند. ولی پلنِ «استاندارد» باید پشتیبان را داشته باشد و کیو‌آر
   *  را نه — و با یک کلیدِ مشترک این نشدنی بود: فرستادنِ `cloud` هر دو
   *  را باز می‌کرد. پس `cloud` مالِ کیو‌آر و پوشهٔ ابری ماند و پشتیبان
   *  کلیدِ خودش را گرفت.
   *
   *  برنامه هر دو نام را می‌شناسد (‎Entitlements.Has‎): `cloudbackup`
   *  مستقیم، و `cloud` به‌عنوانِ هم‌معنی — پس اشتراک‌های امروزی که فقط
   *  `cloud` دارند پشتیبانشان بسته نمی‌شود.
   */
  { key: 'cloudbackup', label: 'پشتیبانِ خودکار روی سرور', core: false },
  /*
   *  این دو تا امروز روی سرور نبودند و پلنِ استاندارد بی آن‌ها ساخته
   *  نمی‌شد (`native/docs/PLANS-fa.md` ⇒ «سه کارِ سرور»).
   *
   *  ⚠️ قفلِ `profit` فقط **دیدنِ صفحه** را می‌بندد. نرخِ اتحادیه از
   *  جاهای دیگر هم خوانده می‌شود (زیانِ افزایشِ قیمت، فاکتورها) و
   *  آن‌ها دست‌نخورده‌اند — خواستهٔ صریحِ صاحب مخزن: «ببین که نرخِ
   *  اتحادیه این وسط آسیب نبینه.»
   */
  { key: 'profit',      label: 'مفاد / ضرر / اتحادیه', core: false },
  { key: 'history',     label: 'تاریخچه‌ها',           core: false },
  { key: 'multi_device', label: 'چند دستگاه روی یک پمپ', core: false },
];

/*
 *  رایگان: **همهٔ دفترِ خودِ کاربر**، چاپ و پشتیبانِ محلی.
 *
 *  ⛔ قاعدهٔ صاحب مخزن (`native/docs/PLANS-fa.md`، قاعدهٔ ۳): «دفترِ
 *  خودِ کاربر هیچ‌وقت گروگان گرفته نمی‌شود — اشتراکِ تمام‌شده هیچ‌کدام
 *  از بخش‌های دفتر را نمی‌بندد.» و جدولِ پلن‌هایش هم «دفترِ کامل» را
 *  برای **هر چهار** پلن ✅ گذاشته، رایگان را هم.
 *
 *  تا دیروز فقط سه کلید این‌جا بود، یعنی روی کاغذِ سرور یک پمپِ
 *  بی‌اشتراک گاوصندوق و صرافی و فاکتور و شرکت‌ها را هم نداشت. در عمل
 *  چیزی نمی‌بست (برنامه فقط شش دروازه را می‌شناسد) ولی حقیقت را وارونه
 *  می‌گفت — و روزی که کسی همین فهرست را ملاک می‌گرفت، دفترِ مردم بسته
 *  می‌شد.
 *
 *  آنچه بیرونِ این فهرست ماند، همان است که پول می‌خواهد: داشبورد،
 *  مفاد/ضرر، تاریخچه‌ها، کیو‌آر، اپِ کارمندان و ربات، پشتیبانِ ابری.
 */
const PUMP_FREE_KEYS = [
  'expense', 'extraincome', 'reports',
  'safe', 'sarrafi', 'chakana', 'invoice', 'storage', 'staff', 'companies', 'amanat',
];

const PUMP_ALL_KEYS       = PUMP_FEATURES.map(f => f.key);
const PUMP_CORE_KEYS      = PUMP_FEATURES.filter(f => f.core).map(f => f.key);
const PUMP_GRANTABLE_KEYS = PUMP_FEATURES.filter(f => !f.core).map(f => f.key);
const PUMP_PAID_KEYS      = PUMP_GRANTABLE_KEYS.filter(k => !PUMP_FREE_KEYS.includes(k));

/** کاتالوگِ یک بخش. نامِ ناشناخته → کاتالوگِ دکان، مثل همیشه. */
function catalogOf(app) {
  if (app === 'pump') {
    return {
      app: 'pump',
      FEATURES: PUMP_FEATURES,
      ALL_KEYS: PUMP_ALL_KEYS,
      CORE_KEYS: PUMP_CORE_KEYS,
      GRANTABLE_KEYS: PUMP_GRANTABLE_KEYS,
      FREE_KEYS: PUMP_FREE_KEYS,
      PAID_KEYS: PUMP_PAID_KEYS,
    };
  }
  return {
    app: 'shop',
    FEATURES, ALL_KEYS, CORE_KEYS, GRANTABLE_KEYS, FREE_KEYS, PAID_KEYS,
  };
}

module.exports = {
  FEATURES, ALL_KEYS, CORE_KEYS, GRANTABLE_KEYS, FREE_KEYS, PAID_KEYS,
  sanitizeFeatures, isKnownFeature, isCoreFeature, isFreeFeature,
  //  بخشِ پمپ
  PUMP_FEATURES, PUMP_ALL_KEYS, PUMP_CORE_KEYS, PUMP_GRANTABLE_KEYS,
  PUMP_FREE_KEYS, PUMP_PAID_KEYS, catalogOf,
};
