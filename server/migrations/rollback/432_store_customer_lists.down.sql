-- ============================================================
--  Rollback ِ 432_store_customer_lists.sql — فقط با دست.
--
--  «فهرستِ خرید» ِ حساب برداشته می‌شود؛ فهرستِ هر مرورگر/گوشی روی همان دستگاه می‌ماند (همان رفتارِ پیش از ۴۳۲).
--  پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.storefront.favorites` را خاموش کنید): درهای
--  `/account/shopping-list` بی این جدول ۵۰۰ می‌دهند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/432_store_customer_lists.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_store_customer_lists_shop;
DROP TABLE IF EXISTS store_customer_lists;

DELETE FROM schema_migrations WHERE version = '432_store_customer_lists.sql';
