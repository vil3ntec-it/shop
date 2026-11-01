-- برگشتِ مهاجرتِ ۱۳۸ (CHK-06، CHK-07). دستی و فقط پس از برگرداندنِ کدِ پیشین اجرا شود:
--   psql "$DATABASE_URL" -f migrations/rollback/138_storefront_delivery_fees.down.sql
-- این پوشه را `src/migrate.js` نمی‌خواند (فقط *.sql ِ خودِ migrations).
-- فقط تنظیمِ «هزینهٔ سبدِ کوچک» و «هزینهٔ بازه» پاک می‌شود؛ سفارش‌های ثبت‌شده
-- مبلغِ خودشان را در `delivery_fee_minor` و ریزِ آن را در JSON ِ همان سفارش نگه می‌دارند.
BEGIN;
ALTER TABLE storefront_slot_rules DROP CONSTRAINT IF EXISTS storefront_slot_rules_fee_check;
ALTER TABLE storefront_slot_rules DROP COLUMN IF EXISTS fee_minor;
ALTER TABLE storefront_zones DROP CONSTRAINT IF EXISTS storefront_zones_small_basket_fee_check;
ALTER TABLE storefront_zones DROP COLUMN IF EXISTS small_basket_fee_minor;
DELETE FROM schema_migrations WHERE version = '138_storefront_delivery_fees.sql';
COMMIT;
