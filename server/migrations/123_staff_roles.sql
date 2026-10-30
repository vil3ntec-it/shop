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
