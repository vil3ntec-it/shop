-- ============================================================
--  ۶۱۰ — «سابقهٔ دسترسی» (CRM-15): درِ تازهٔ `app` برای دیدنی که برنامه از دفترِ خودش گزارش می‌دهد.
--
--  `POST /api/shop/customer-access` (`lib/customer-access.noteApp`): وب (همیشه وصل؛ دفترِ مرورگر) و نقشی که
--  `auditLog` نمی‌نویسد (حسابدار، `perm-core.canWrite`) دیدنِ پرونده را به سرور می‌گویند؛ بیننده، نقش، دکان و
--  دستگاه فقط از توکن. `customer_ref` همان شناسهٔ پرونده، یا `*` برای خروجیِ برگهٔ همهٔ مشتریان (فقط `export`).
--  فقط CHECK ِ `door` بازتر می‌شود؛ هیچ ستون، ردیف یا تریگری عوض نمی‌شود (نگهبانِ فقط‌افزودنیِ ۳۸۰ همان).
--  rollback: migrations/rollback/610_customer_access_app_door.down.sql
-- ============================================================

ALTER TABLE customer_access_log DROP CONSTRAINT IF EXISTS customer_access_log_door_check;
ALTER TABLE customer_access_log ADD CONSTRAINT customer_access_log_door_check
  CHECK (door IN ('debtor', 'orders', 'app'));
