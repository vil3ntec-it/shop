-- ============================================================
--  Rollback ِ 790_shop_till_op_users.sql — فقط با دست.
--
--  سرور دوباره هر op را فقط به نامِ حسابِ دستگاه می‌بیند (همان رفتارِ پیش از ۷۹۰)؛ نوبت‌های صندوق (۶۴۰)، فروش‌ها و
--  سندها دست نمی‌خورند. پیش از اجرا کدِ پیشین را برگردانید (یا پرچمِ `shop.till_op_user` را خاموش کنید).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/790_shop_till_op_users.down.sql
-- ============================================================

DROP INDEX IF EXISTS ix_shop_till_op_users_member;
DROP INDEX IF EXISTS ix_shop_till_op_users_row;
DROP INDEX IF EXISTS ix_shop_till_op_users_at;
DROP TABLE IF EXISTS shop_till_op_users;

DELETE FROM schema_migrations WHERE version = '790_shop_till_op_users.sql';
