-- بازگشتِ 136_promo_redemptions.sql — فقط اگر پرچمِ `shop.promo_uses` همه‌جا خاموش است.
-- سابقهٔ مصرفِ کدها از دست می‌رود؛ پیش از اجرا از جدول خروجی بگیرید.
DROP INDEX IF EXISTS idx_shop_promo_redemptions_sale;
DROP INDEX IF EXISTS idx_shop_promo_redemptions_live;
DROP TABLE IF EXISTS shop_promo_redemptions;
DELETE FROM schema_migrations WHERE version = '136_promo_redemptions.sql';
