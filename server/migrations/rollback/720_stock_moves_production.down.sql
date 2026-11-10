-- ============================================================
--  Rollback ِ 720_stock_moves_production.sql — فقط با دست.
--
--  قیدِ پیشینِ نوعِ حرکت برمی‌گردد. ⛔ دفتر فقط افزودنی است: ردیف‌های `production` ِ نوشته‌شده پاک نمی‌شوند (تاریخچهٔ
--  موجودی‌اند و `stock_levels` جمعِ آن‌هاست)، پس قید با `NOT VALID` می‌نشیند — ردیفِ تازه همان قاعدهٔ پیشین را می‌گیرد،
--  ردیفِ کهنه دست نمی‌خورد. پیش از اجرا پرچمِ `shop.catalog_repack` را خاموش کنید (یا کدِ پیشین را برگردانید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/720_stock_moves_production.down.sql
-- ============================================================

ALTER TABLE stock_moves DROP CONSTRAINT IF EXISTS stock_moves_type_check;
ALTER TABLE stock_moves ADD CONSTRAINT stock_moves_type_check CHECK (type IN (
  'purchase_receipt','sale','customer_return','supplier_return','adjustment',
  'count_adjust','damage','expired','transfer_out','transfer_in','reserve','release')) NOT VALID;
DELETE FROM schema_migrations WHERE version = '720_stock_moves_production.sql';
