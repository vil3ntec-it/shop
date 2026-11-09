-- ============================================================
--  Rollback ِ 425_shop_held_invoices.sql — فقط با دست.
--
--  فاکتورهای نگه‌داشته دوباره فقط در فایلِ هر دستگاه می‌مانند (همان رفتارِ پیش از ۴۲۵). هیچ دستگاهی چیزی از دست
--  نمی‌دهد: برنامه هر نگه‌داشته را پیش از فرستادن روی خودِ دستگاه دارد. پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ
--  `shop.held_sync` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/425_shop_held_invoices.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_shop_held_invoices_live;
DROP INDEX IF EXISTS ix_shop_held_invoices_rev;
DROP TABLE IF EXISTS shop_held_invoices;

DELETE FROM schema_migrations WHERE version = '425_shop_held_invoices.sql';
