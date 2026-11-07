-- ============================================================
--  Rollback ِ 220_shop_approval_requests.sql — فقط با دست.
--
--  درخواست‌های تأییدِ از راهِ دور پاک می‌شوند؛ تأییدهای مصرف‌شده در `shop_approvals` (nonce = `req_<id>`) می‌مانند.
--  پیش از اجرا پرچمِ `shop.approval_requests` را خاموش کنید (پیش‌فرض خاموش است).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/220_shop_approval_requests.down.sql
-- ============================================================

DROP TABLE IF EXISTS shop_approval_requests;
DELETE FROM schema_migrations WHERE version = '220_shop_approval_requests.sql';
