-- ============================================================
--  Rollback ِ 946_branch_transfer_drafts.sql — فقط با دست، پس از برگرداندنِ کد.
--  ⛔ اگر پیش‌نویسِ بازی (`draft`) هست، برگشت رد می‌شود و هیچ چیز عوض نمی‌شود — اول بفرستید یا لغو کنید (کدِ پیشین پیش‌نویس
--     نمی‌شناسد).
--  ⚠️ سندِ انتقال‌های لغوشده و گام‌هایشان پاک می‌شود؛ حرکت‌هایشان در دفترِ موجودیِ هر شعبه (`stock_moves`، `ref_type =
--     'branch_transfer'`) می‌ماند و مانده‌ها (`stock_levels`) دست نمی‌خورند. گام‌های «پیش‌نویس/ویرایش» ِ انتقال‌های فرستاده پاک و
--     گامِ ارسالشان همان شکلِ ۹۳۵ (`status_from = ''`) می‌شود.
--  ⚠️ تنظیم‌های شعبه‌ایِ دکان (`shop_branch_settings`) می‌روند: کارمند دوباره فقط شعبه‌های خودش را می‌بیند و ارسالِ بیش از
--     موجودی همیشه رد می‌شود.
-- ============================================================
DO $$
DECLARE n bigint;
BEGIN
  IF to_regclass('branch_transfers') IS NOT NULL THEN
    SELECT COUNT(*) INTO n FROM branch_transfers WHERE status = 'draft';
    IF n > 0 THEN
      RAISE EXCEPTION 'rollback 946: % draft branch transfer(s) — send or cancel them first', n;
    END IF;
  END IF;
END $$;

DROP TABLE IF EXISTS shop_branch_settings;

DELETE FROM branch_transfers WHERE status = 'cancelled';
DELETE FROM branch_transfer_steps WHERE kind IN ('draft', 'edit', 'cancel');
UPDATE branch_transfer_steps SET status_from = '' WHERE kind = 'send' AND status_from = 'draft';

ALTER TABLE branch_transfers DROP CONSTRAINT IF EXISTS branch_transfers_status_v2;
ALTER TABLE branch_transfers DROP CONSTRAINT IF EXISTS branch_transfers_open_v2;
ALTER TABLE branch_transfer_steps DROP CONSTRAINT IF EXISTS branch_transfer_steps_kind_v2;
ALTER TABLE branch_transfer_steps DROP CONSTRAINT IF EXISTS branch_transfer_steps_flow_v2;

--  همان CHECK های ۹۳۵
ALTER TABLE branch_transfers ADD CONSTRAINT branch_transfers_status_check
  CHECK (status IN ('in_transit', 'partial', 'received', 'rejected'));
ALTER TABLE branch_transfers ADD CONSTRAINT branch_transfers_check1
  CHECK ((status IN ('in_transit', 'partial')) = (closed_at IS NULL));
ALTER TABLE branch_transfer_steps ADD CONSTRAINT branch_transfer_steps_kind_check
  CHECK (kind IN ('send', 'receive', 'reject'));
ALTER TABLE branch_transfer_steps ADD CONSTRAINT branch_transfer_steps_check CHECK (
     (kind = 'send'    AND status_from = ''                         AND status_to = 'in_transit')
  OR (kind = 'receive' AND status_from IN ('in_transit','partial')  AND status_to IN ('partial','received'))
  OR (kind = 'reject'  AND status_from IN ('in_transit','partial')  AND status_to = 'rejected')
);

--  همان تریگرِ ۹۳۵
CREATE OR REPLACE FUNCTION branch_transfer_guard() RETURNS trigger AS $$
BEGIN
  IF OLD.status IN ('received', 'rejected') THEN
    RAISE EXCEPTION 'branch_transfer % is closed (%)', OLD.id, OLD.status USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.shop_id <> OLD.shop_id OR NEW.from_branch <> OLD.from_branch OR NEW.to_branch <> OLD.to_branch
     OR NEW.from_location <> OLD.from_location OR NEW.to_location <> OLD.to_location
     OR NEW.created_by <> OLD.created_by OR NEW.created_at <> OLD.created_at OR NEW.id <> OLD.id THEN
    RAISE EXCEPTION 'branch_transfer %: ends are immutable', OLD.id USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.status NOT IN ('partial', 'received', 'rejected') THEN
    RAISE EXCEPTION 'branch_transfer %: % -> % is not allowed', OLD.id, OLD.status, NEW.status USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

ALTER TABLE branch_transfers DROP COLUMN IF EXISTS sent_at;
ALTER TABLE branch_transfers DROP COLUMN IF EXISTS sent_by;

DELETE FROM schema_migrations WHERE version = '946_branch_transfer_drafts.sql';
