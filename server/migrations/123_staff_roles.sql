-- ============================================================
--  نقش‌های تازهٔ کارمند — STF-01 (بخشِ ۱۰.۱)، بستهٔ A16.
--
--  `shop_members.role`، `staff_codes.role` و `shop_role_perms.role` چهار
--  مقدارِ تازه می‌پذیرند:
--    cashier      صندوق‌دار
--    storekeeper  انباردار
--    picker       جمع‌آور
--    accountant   حسابدار (فقط‌خواندنی)
--  آنچه هر نقش می‌کند در `lib/permissions.js` (MATRIX) و `lib/perm-core.js`
--  (`canWrite`: جدول‌های همگام‌سازیِ نوشتنی) است.
--
--  ⛔ هیچ ردیفی عوض نمی‌شود: تا صاحب دکانی خودش نقشِ تازه ندهد هیچ عضوی
--  آن را نمی‌گیرد — رفتارِ امروز همان است. 'courier' (مهاجرتِ ۱۰۲) سرِ جایش.
--
--  `sync_op_authors` (جدولِ تازه و خالی؛ بازبینیِ دومِ #336، ج): «برگشتِ خودیِ» صندوق‌دار — که
--  فقط برای آن اصلاحِ موجودی می‌نویسد — برگشتی است که **همین کاربر** ساخته، نه شناسهٔ دستگاهی که
--  برنامه می‌فرستد (شناسهٔ دستگاهِ دیگران در Pull دیده می‌شود). oplog (پمپ‌مشترک) و sync_v3_ops کاربر
--  را نگه نمی‌دارند، پس نویسندهٔ opهای درجِ برگشتِ نقش‌های قاعده‌دار (امروز فقط صندوق‌دار) این‌جا
--  ثبت می‌شود (`lib/role-rows.js`: noteAuthors). شاخصِ `idx_sync_v3_ops_row` برای یافتنِ نخستین
--  درجِ یک ردیف در v3 است (همان شاخصِ `idx_oplog_row` ِ v1).
--
--  برگشت (عضوِ نقشِ تازه کنار گذاشته می‌شود، نه «شاگرد»: شاگرد همه را می‌نویسد و
--  حسابدارِ فقط‌خواندنی با آن دسترسیِ بیشتری می‌گرفت؛ `role`='staff' فقط برای قیدِ CHECK است
--  و صاحب دکان هنگامِ فعال کردنِ دوباره نقشش را خودش انتخاب می‌کند):
--    UPDATE shop_members SET role='staff',
--           status = CASE WHEN status='active' THEN 'suspended' ELSE status END
--     WHERE role IN ('cashier','storekeeper','picker','accountant');
--    UPDATE staff_codes SET status='revoked' WHERE role IN ('cashier','storekeeper','picker','accountant');
--    UPDATE staff_codes SET role='staff' WHERE role IN ('cashier','storekeeper','picker','accountant');
--    DELETE FROM shop_role_perms WHERE role IN ('cashier','storekeeper','picker','accountant');
--    ALTER TABLE shop_members DROP CONSTRAINT shop_members_role_check;
--    ALTER TABLE shop_members ADD CONSTRAINT shop_members_role_check CHECK (role IN ('owner','manager','staff','courier'));
--    ALTER TABLE staff_codes DROP CONSTRAINT staff_codes_role_check;
--    ALTER TABLE staff_codes ADD CONSTRAINT staff_codes_role_check CHECK (role IN ('manager','staff','courier'));
--    ALTER TABLE shop_role_perms DROP CONSTRAINT shop_role_perms_role_check;
--    ALTER TABLE shop_role_perms ADD CONSTRAINT shop_role_perms_role_check CHECK (role IN ('manager','staff'));
--    DROP INDEX IF EXISTS idx_sync_v3_ops_row;
--    DROP TABLE IF EXISTS sync_op_authors;
-- ============================================================

ALTER TABLE shop_members DROP CONSTRAINT IF EXISTS shop_members_role_check;
ALTER TABLE shop_members ADD CONSTRAINT shop_members_role_check
  CHECK (role IN ('owner','manager','staff','courier','cashier','storekeeper','picker','accountant'));

ALTER TABLE staff_codes DROP CONSTRAINT IF EXISTS staff_codes_role_check;
ALTER TABLE staff_codes ADD CONSTRAINT staff_codes_role_check
  CHECK (role IN ('manager','staff','courier','cashier','storekeeper','picker','accountant'));

ALTER TABLE shop_role_perms DROP CONSTRAINT IF EXISTS shop_role_perms_role_check;
ALTER TABLE shop_role_perms ADD CONSTRAINT shop_role_perms_role_check
  CHECK (role IN ('manager','staff','cashier','storekeeper','picker','accountant'));

CREATE TABLE IF NOT EXISTS sync_op_authors (
  app         text   NOT NULL,
  account_id  text   NOT NULL,
  op_id       text   NOT NULL,
  user_id     text   NOT NULL,
  created_at  bigint NOT NULL,
  PRIMARY KEY (app, account_id, op_id)
);

CREATE INDEX IF NOT EXISTS idx_sync_v3_ops_row
  ON sync_v3_ops(app, account_kind, account_id, table_name, row_id);
