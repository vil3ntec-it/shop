-- ============================================================
--  ۲۱۵ — نشانی و پینِ دکان برای مشتریِ ویترین (WEB-02/03، CHK-11؛ ممیزیِ ویترین ۱۴۰۵/۰۸/۱۷، مشکلِ ۶).
--
--  مشتریِ «دریافت از دکان» نمی‌دانست کجا برود: تنظیماتِ ویترین هیچ خانه‌ای برای نشانیِ خودِ دکان نداشت.
--  `pickup_address` ── یک خطِ نشانیِ دکان (تا ۲۰۰ نویسه)، همان که صاحب می‌نویسد؛ خالی ⇒ هیچ‌جا دیده نمی‌شود.
--  `pickup_lat`/`pickup_lng` ── پینِ اختیاریِ درِ دکان (درجه)؛ هر دو یا هیچ ⇒ پیوندِ «مسیر روی نقشه».
--  نمای عمومی فقط `place: { text, map }` است (نشانی و پیوندِ نقشه) — WEB-23: هیچ کلیدِ «address/lat/lng» ِ شخصی‌نما بیرون نمی‌رود.
--  جدا از `market_listings` (جای تقریبیِ بازارچه) — این را صاحب برای مشتریِ همین دکان می‌گذارد.
--
--  فقط افزودنی؛ هیچ ردیفی عوض نمی‌شود (پیش‌فرض: بی نشانی، بی پین = رفتارِ امروز).
--  rollback: migrations/rollback/230_storefront_place.down.sql (آزموده در test/storefront-audit.test.js)
-- ============================================================

ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS pickup_address text NOT NULL DEFAULT '';
ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS pickup_lat double precision;
ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS pickup_lng double precision;
