-- ============================================================
--  Rollback ِ 750_courier_location_consent.sql — فقط با دست.
--
--  رضایتِ ثبت‌شدهٔ پیک‌ها می‌رود؛ با پرچمِ روشنِ `shop.delivery_live_location.consent` هیچ پیکی دیگر موقعیت
--  نمی‌فرستد تا دوباره «بله» بزند. پیش از اجرا کدِ پیشین را برگردانید (یا آن پرچم را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/750_courier_location_consent.down.sql
-- ============================================================

ALTER TABLE shop_couriers DROP COLUMN IF EXISTS location_consent_at;

DELETE FROM schema_migrations WHERE version = '750_courier_location_consent.sql';
