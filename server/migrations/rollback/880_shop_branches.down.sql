-- ============================================================
--  Rollback ِ 880_shop_branches.sql — فقط با دست، پس از برگرداندنِ کد.
--  ⚠️ دادهٔ دفترِ شعبه‌های غیرِ اصلی (account_id = '<دکان>#<شعبه>') پیش از برداشتنِ جدول پاک می‌شود — همان
--  تریگرِ پاک‌سازی برای هر ردیف؛ شعبهٔ اصلی (همهٔ دادهٔ پیش از این مهاجرت) دست نمی‌خورد.
-- ============================================================
DELETE FROM shop_branches;
DROP TRIGGER IF EXISTS shop_branches_purge ON shop_branches;
DROP FUNCTION IF EXISTS shop_branch_purge();
DROP TABLE IF EXISTS shop_member_branches;
ALTER TABLE shop_approval_requests DROP COLUMN IF EXISTS branch_id;
DROP TABLE IF EXISTS shop_branches;
DELETE FROM schema_migrations WHERE version = '880_shop_branches.sql';
