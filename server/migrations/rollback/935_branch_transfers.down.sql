-- ============================================================
--  Rollback ِ 935_branch_transfers.sql — فقط با دست، پس از برگرداندنِ کد.
--  ⛔ اگر انتقالِ بازی (`in_transit`/`partial`) هست، برگشت رد می‌شود و هیچ چیز عوض نمی‌شود: کالای آن در `in_transit` ِ
--     شعبهٔ فرستنده است و کدِ پیشین راهی برای دریافت یا ردش ندارد — اول دریافت یا رد کنید.
--  ⚠️ مجوزِ `stock.view_other_branches` ِ اعضا (POS-16) پاک می‌شود.
--  ⚠️ سندِ انتقال‌های بسته و گام‌هایشان پاک می‌شود؛ حرکت‌هایشان در دفترِ موجودیِ هر شعبه (`stock_moves`، `ref_type =
--     'branch_transfer'`) می‌ماند — همان تاریخچهٔ واقعیِ کالا، و مانده‌ها (`stock_levels`) دست نمی‌خورند.
-- ============================================================
DO $$
DECLARE n bigint;
BEGIN
  IF to_regclass('branch_transfers') IS NOT NULL THEN
    SELECT COUNT(*) INTO n FROM branch_transfers WHERE status IN ('in_transit', 'partial');
    IF n > 0 THEN
      RAISE EXCEPTION 'rollback 935: % open branch transfer(s) — receive or reject them first', n;
    END IF;
  END IF;
END $$;

--  POS-16/S17: مجوزِ «موجودیِ شعبه‌های دیگر» ِ اعضا هم می‌رود (پس از برگشت هیچ‌کس جز شعبه‌های خودش نمی‌بیند)
DROP TABLE IF EXISTS shop_member_branch_perms;
--  تریگر با جدول می‌رود
DROP TABLE IF EXISTS branch_transfer_steps;
DROP TABLE IF EXISTS branch_transfers;
DROP FUNCTION IF EXISTS branch_transfer_guard();

DELETE FROM schema_migrations WHERE version = '935_branch_transfers.sql';
