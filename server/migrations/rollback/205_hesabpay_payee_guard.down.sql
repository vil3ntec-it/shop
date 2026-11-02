-- ============================================================
--  Rollback ِ 205_hesabpay_payee_guard.sql — فقط با دست.
--
--  حسابِ «در انتظارِ فعال شدن»، قفلِ بخش، نام و اثرِ انگشتِ حساب‌ها و تاریخچهٔ حساب‌های گیرنده پاک می‌شوند؛ حسابِ
--  فعالِ هر دکان (ستون‌های ۲۰۴) دست نمی‌خورد. پیش از اجرا کدِ پیش از این نسخه را برگردانید (کدِ همین نسخه این
--  ستون‌ها را می‌خواند).
--
--  اجرا (در یک تراکنش):
--    psql "$DATABASE_URL" -1 -f migrations/rollback/205_hesabpay_payee_guard.down.sql
-- ============================================================

ALTER TABLE payment_intents DROP COLUMN IF EXISTS payee_fp;
DROP TABLE IF EXISTS hesabpay_payees;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS section_locked_until;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS pending_until;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS pending;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS first_set_at;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS active_since;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS payee_fp;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS merchant_last4;
ALTER TABLE shop_hesabpay DROP COLUMN IF EXISTS account_name;
DELETE FROM schema_migrations WHERE version = '205_hesabpay_payee_guard.sql';
