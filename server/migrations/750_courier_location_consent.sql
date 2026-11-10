-- ============================================================
--  ۷۵۰ — رضایتِ خودِ پیک برای موقعیتِ زنده (S94، §۲۱؛ ۱۴۰۵/۰۸/۱۹)
--
--  تا امروز موقعیتِ زندهٔ پیکِ «در راه» فقط با پرچمِ دکان (`shop.delivery_live_location`) گرفته می‌شد؛ خودِ پیک
--  رضایتی نمی‌داد و نمی‌توانست پسش بگیرد. حالا `shop_couriers.location_consent_at`: لحظهٔ آخرین «بله» ِ خودِ پیک
--  (NULL = بی رضایت یا پس‌گرفته). ⛔ پشتِ پرچمِ خاموشِ `shop.delivery_live_location.consent`
--  (lib/courier-location-consent.js): خاموش ⇒ همان رفتارِ امروز و این ستون خوانده نمی‌شود.
--  فقط افزودنی. rollback: migrations/rollback/750_courier_location_consent.down.sql
-- ============================================================

ALTER TABLE shop_couriers ADD COLUMN IF NOT EXISTS location_consent_at bigint;
