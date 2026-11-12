-- ============================================================
--  Rollback ِ 847_order_address_media.sql — فقط با دست.
--
--  پیش از اجرا پرچمِ `shop.storefront.address_media` را برای همه خاموش کنید و کدِ پیشین را برگردانید. عکس‌ها و
--  یادداشت‌های صوتیِ نشانی پاک می‌شوند (فقط کمکِ تحویل‌اند؛ نشانی و پینِ سفارش دست نمی‌خورند). در حالتِ انبارِ شیء
--  شیءها می‌مانند تا `image-store-migrate gc --apply`.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/847_order_address_media.down.sql
-- ============================================================

DROP TABLE IF EXISTS order_address_media;

DELETE FROM schema_migrations WHERE version = '847_order_address_media.sql';
