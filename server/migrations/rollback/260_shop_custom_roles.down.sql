-- ============================================================
--  Rollback ِ 260_shop_custom_roles.sql — فقط با دست.
--
--  نقش‌های سفارشی و پیوندِ عضوها پاک می‌شوند؛ عضوها روی همان نقشِ پایه (`shop_members.role`) با لایه‌های
--  نقش و کاربرِ امروزشان می‌مانند. پیش از اجرا پرچمِ `shop.custom_roles` را خاموش کنید (پیش‌فرض خاموش است).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/260_shop_custom_roles.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_member_custom_roles;
DROP TABLE IF EXISTS shop_custom_roles;
DELETE FROM schema_migrations WHERE version = '260_shop_custom_roles.sql';
