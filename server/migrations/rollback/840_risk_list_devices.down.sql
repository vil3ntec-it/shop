-- ============================================================
--  Rollback ِ 840_risk_list_devices.sql — فقط با دست.
--
--  «دستگاه» از فهرستِ سیاه و سفید می‌رود (ردیف‌های `device` پاک می‌شوند — نسخهٔ پیشین آن‌ها را نمی‌شناسد) و چکیدهٔ
--  دستگاهِ سفارش‌ها هم. شماره و پین دست نمی‌خورند. پیش از اجرا کدِ پیشین را برگردانید (یا `shop.order_risk` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/840_risk_list_devices.down.sql
-- ============================================================

DELETE FROM shop_risk_lists WHERE kind = 'device';
DROP INDEX IF EXISTS ux_shop_risk_lists_device;
ALTER TABLE shop_risk_lists DROP CONSTRAINT IF EXISTS shop_risk_lists_shape_check;
ALTER TABLE shop_risk_lists DROP CONSTRAINT IF EXISTS shop_risk_lists_kind_check;
ALTER TABLE shop_risk_lists ADD CONSTRAINT shop_risk_lists_kind_check CHECK (kind IN ('phone', 'pin'));
ALTER TABLE shop_risk_lists ADD CONSTRAINT shop_risk_lists_check CHECK (
     (kind = 'phone' AND phone <> '' AND lat IS NULL AND lng IS NULL AND radius_m IS NULL)
  OR (kind = 'pin' AND phone = '' AND lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180
      AND radius_m BETWEEN 10 AND 2000 AND list = 'block')
);
ALTER TABLE shop_risk_lists DROP COLUMN IF EXISTS order_no;
ALTER TABLE shop_risk_lists DROP COLUMN IF EXISTS device_key;
DROP TABLE IF EXISTS online_order_devices;

DELETE FROM schema_migrations WHERE version = '840_risk_list_devices.sql';
