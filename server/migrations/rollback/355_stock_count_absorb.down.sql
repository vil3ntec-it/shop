-- ============================================================
--  Rollback ِ 355_stock_count_absorb.sql — فقط با دست.
--
--  پیش از اجرا پرچمِ `shop.count_absorb` را خاموش کن و یک بار کاتالوگ را بخوان: حرکت‌های `count_absorb` با حرکتِ
--  افزودنیِ برعکس برمی‌گردند (`lib/count-absorb-ledger.js`). سپس دو جدول و ستون برداشته می‌شوند؛ `stock_moves` و
--  `stock_levels` دست نمی‌خورند.
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/355_stock_count_absorb.down.sql
-- ============================================================

DROP TABLE IF EXISTS stock_count_marks;
DROP TABLE IF EXISTS stock_device_events;
ALTER TABLE catalog_sync_state DROP COLUMN IF EXISTS count_absorb;
DELETE FROM schema_migrations WHERE version = '355_stock_count_absorb.sql';
