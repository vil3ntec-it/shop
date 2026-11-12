-- ============================================================
--  Rollback ِ 836_store_address_media.sql — فقط با دست.
--
--  عکسِ دروازه و یادداشتِ صوتیِ نشانی‌ها و پیوندِ سفارش ⇐ نشانی برداشته می‌شوند؛ نشانی‌ها و سفارش‌ها می‌مانند.
--  در حالتِ disk/s3 ِ انبارِ عکس، شیءهای بی‌ردیف را `npm run image-store -- gc` برمی‌دارد.
--  کدِ تازه بی این جدول‌ها خطای پایگاه می‌دهد ⇒ پیش از آن پرچمِ `shop.storefront.address_media` را خاموش کنید.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/836_store_address_media.down.sql
-- ============================================================

DROP TABLE IF EXISTS online_order_address_refs;
DROP TABLE IF EXISTS store_address_media;

DELETE FROM schema_migrations WHERE version = '836_store_address_media.sql';
