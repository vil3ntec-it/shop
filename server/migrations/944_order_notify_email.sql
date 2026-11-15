-- ============================================================
--  944 — NOTIF-11: کانالِ پشتیبانِ اعلانِ سفارش = ایمیلِ سرور به ایمیلِ تأییدشدهٔ صاحبِ دکان
--  (`lib/order-notify-email.js`، پرچمِ خاموشِ `shop.order_notify_email`؛ «فقط ایمیل»، ۱۴۰۵/۰۸/۲۳).
--
--  همان صفِ `order_notify_outbox` (مهاجرتِ ۰۸۰)، نه جدولِ دوم. فقط دو حالِ تازه در CHECK ِ `fallback_state`:
--    throttled — سقفِ روزانهٔ نامهٔ پشتیبانِ همان دکان پر بود (`shop.order_notify_email.per_day`)؛ نامه نرفت.
--    no_email  — صاحبِ دکان ایمیلِ معتبر ندارد (یا حسابش بسته است)؛ نامه نرفت.
--  و یک نمایهٔ جزئی برای شمارِ نامه‌های ۲۴ ساعتِ هر دکان.
--  ⛔ هیچ ردیفی عوض نمی‌شود؛ بی پرچم این دو مقدار هرگز نوشته نمی‌شوند.
--  بازگشت: `rollback/944_order_notify_email.down.sql`.
-- ============================================================
ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_fallback_state_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_fallback_state_check
  CHECK (fallback_state IN ('', 'not_configured', 'provider_accepted', 'provider_error', 'throttled', 'no_email'));
CREATE INDEX IF NOT EXISTS ix_order_notify_outbox_fallback_email
  ON order_notify_outbox(shop_id, fallback_at) WHERE fallback_channel = 'email';
