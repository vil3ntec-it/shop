-- ============================================================
--  ۲۱۱ — «پرداخت پس از پذیرش» برای دکانِ **تازه** پیش‌فرض روشن (تصمیمِ سپرده‌شده به هماهنگ‌کننده، ۱۴۰۵/۰۸/۱۳).
--
--  `shops.pay_after_accept_default`: دکان‌های موجود (هر ردیفِ همین لحظه) ⇒ `false` — رفتارِ امروزشان دست
--  نمی‌خورد؛ دکانِ تازه (هر INSERT ِ بعدی، `lib/shops.createShop`) ⇒ `true`. ویترین فقط **یک بار**، وقتی ردیفش
--  نخستین بار ساخته می‌شود (`lib/storefront-owner.saveSettings`)، همین مقدار را در `storefronts.pay_after_accept`
--  (مهاجرتِ ۲۱۰) می‌نشاند — مگر صاحب در همان درخواست خودش گفته باشد. ویترینِ موجود و هر تنظیمِ صاحب دست نمی‌خورد.
--
--  ⛔ ADD COLUMN با DEFAULT false همهٔ ردیف‌های موجود را false می‌کند؛ SET DEFAULT true فقط INSERT‌های بعدی را.
--     هیچ ردیفی بازنویسی نمی‌شود.
--  rollback: migrations/rollback/211_order_pay_after_accept_default.down.sql (آزموده در test/order-flow.test.js)
-- ============================================================

ALTER TABLE shops ADD COLUMN IF NOT EXISTS pay_after_accept_default boolean NOT NULL DEFAULT false;
ALTER TABLE shops ALTER COLUMN pay_after_accept_default SET DEFAULT true;
