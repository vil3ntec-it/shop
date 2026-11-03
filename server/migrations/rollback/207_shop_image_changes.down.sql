-- ============================================================
--  Rollback ِ 207_shop_image_changes.sql — فقط با دست.
--
--  دسته‌های تغییرِ عکسِ کالا (IMG-PRO-17) و وضعِ پیش و پسِ هر کالا پاک می‌شوند؛ انتخابِ کنونیِ عکسِ هر کالا
--  (`shop_product_images`) و سابقهٔ عملیات دست نمی‌خورند. پیش از اجرا کدِ پیش از این نسخه را برگردانید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/207_shop_image_changes.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_image_change_items;
DROP TABLE IF EXISTS shop_image_changes;
DELETE FROM schema_migrations WHERE version = '207_shop_image_changes.sql';
