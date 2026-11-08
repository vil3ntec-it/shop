-- ============================================================
--  Rollback ِ 350_product_photo_thumbs.sql — فقط با دست.
--
--  فقط نسخه‌های کارت می‌روند؛ عکسِ اصلِ کالاها (`product_photos`) دست نمی‌خورد و ویترین بی این جدول همان عکسِ
--  کامل را سرو می‌کند (رفتارِ پیش از ۳۵۰). کدِ پیش از این نسخه را پیش از این پرونده برگردانید.
--  در حالتِ انبارِ شیء (`IMAGE_STORE=disk|s3`) شیءهای نسخه‌های کارت یتیم می‌مانند و `node scripts/image-store-migrate.js
--  gc --apply` برشان می‌دارد. پس از اجرای دوبارهٔ ۳۵۰ برنامه نسخه‌ها را از نو می‌سازد و می‌فرستد.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/350_product_photo_thumbs.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_product_photo_thumbs_store_key;
DROP TABLE IF EXISTS product_photo_thumbs;
DELETE FROM schema_migrations WHERE version = '350_product_photo_thumbs.sql';
