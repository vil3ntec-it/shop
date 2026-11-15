-- ============================================================
--  Rollback ِ 900_branch_stock.sql — فقط با دست، پس از برگرداندنِ کد.
--  ⚠️ موجودیِ سرورِ شعبه‌های غیرِ اصلی (`branch_id <> ''` و کلیدِ `<شعبه>|…` ِ `stock_sources`) پاک می‌شود — کدِ فازِ ۱
--  آن را نمی‌شناسد. شعبهٔ اصلی (همهٔ دادهٔ پیش از این مهاجرت: `branch_id = ''`) دست نمی‌خورد. ردیف‌های آینهٔ کاتالوگ در
--  بخشِ شعبه (opهای `srv.cm.`) می‌مانند: همان کالاهای شعبهٔ اصلی‌اند و دفترِ همان بخش.
-- ============================================================
DELETE FROM stock_moves WHERE branch_id <> '';
DELETE FROM stock_levels WHERE branch_id <> '';
DELETE FROM stock_tasks WHERE branch_id <> '';
DELETE FROM stock_sources WHERE source_key ~ '^brn_[0-9a-f]{24}[|]';
DROP TABLE IF EXISTS shop_branch_mirror;
DROP TABLE IF EXISTS catalog_branch_state;
DROP INDEX IF EXISTS ix_sync_v3_rows_catalog_seq;

--  همان تابعِ پاک‌سازیِ ۸۸۰
CREATE OR REPLACE FUNCTION shop_branch_purge() RETURNS trigger AS $$
DECLARE k text;
BEGIN
  IF OLD.id = '' THEN RETURN OLD; END IF;
  k := OLD.shop_id || '#' || OLD.id;
  DELETE FROM sync_v3_ops WHERE account_id = k;
  DELETE FROM sync_v3_rows WHERE account_id = k;
  DELETE FROM sync_v3_conflicts WHERE account_id = k;
  DELETE FROM sync_v3_devices WHERE account_id = k;
  DELETE FROM sync_v3_bridge WHERE account_id = k;
  DELETE FROM sync_v3_v1_out WHERE account_id = k;
  DELETE FROM sync_v3_v1_state WHERE account_id = k;
  DELETE FROM oplog WHERE account_id = k;
  DELETE FROM sync_rows WHERE account_id = k;
  DELETE FROM sync_conflicts WHERE account_id = k;
  DELETE FROM sync_devices WHERE account_id = k;
  DELETE FROM sync_op_authors WHERE account_id = k;
  DELETE FROM shop_member_branches WHERE shop_id = OLD.shop_id AND branch_id = OLD.id;
  RETURN OLD;
END $$ LANGUAGE plpgsql;

DELETE FROM schema_migrations WHERE version = '900_branch_stock.sql';
