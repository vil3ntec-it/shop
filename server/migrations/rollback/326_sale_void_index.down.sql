-- ============================================================
--  Rollback ِ 326_sale_void_index.sql — فقط با دست.
--
--  حلقه‌های لغو در `hash_chain` می‌مانند (فقط‌افزودنی). پس از برداشتنِ نمایه، لغوِ دوبارهٔ فاکتوری که پیش‌تر لغو شده
--  بود حلقهٔ دوم می‌گیرد؛ هیچ سندی گم نمی‌شود. پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.doc_chain` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/326_sale_void_index.down.sql
-- ============================================================

DROP TABLE IF EXISTS sale_void_index;

DELETE FROM schema_migrations WHERE version = '326_sale_void_index.sql';
