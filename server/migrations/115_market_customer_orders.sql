-- بازارچه: «سفارش‌های من» با شمارهٔ تأییدشده، از همهٔ ویترین‌ها (S138، §۲۳.۲).
-- ویترینِ هر دکان همین را با `shop_id` می‌خواند (ix_online_orders_shop_*)؛ فهرستِ
-- بازارچه بی دکان است، پس بی این نمایه کلِ سفارش‌های ویترین پیمایش می‌شد.
-- فقط نمایه — هیچ ستون یا داده‌ای عوض نمی‌شود. برگشت: DROP INDEX.
CREATE INDEX IF NOT EXISTS ix_online_orders_storefront_phone
  ON online_orders(customer_phone, created_at DESC) WHERE source = 'storefront';
