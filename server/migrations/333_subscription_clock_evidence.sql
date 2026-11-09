-- ============================================================
--  ۳۳۳ — شاهدِ ساعت برای بستنِ خودکارِ اشتراکِ **دکان** (SUB-03 ساعت؛ ۱۴۰۵/۰۸/۱۹). پمپ دست نمی‌خورد.
--
--  تا امروز خانه‌تکانی (`subs.expireDue`) ستونِ `status` را برای همیشه `expired` می‌نوشت. ساعتِ سرور که جلو
--  می‌پرید (RTC ِ خراب پس از راه‌اندازی، NTP ِ نادرست) همهٔ دکان‌های پول‌داده را می‌بست و درست شدنِ ساعت هیچ‌کدام
--  را باز نمی‌کرد. حالا:
--    auto_expired_at   ساعتِ سرور در لحظه‌ای که **خانه‌تکانی** ردیف را بست (پایانِ دستیِ مدیر ⇒ NULL، پایانی).
--                      ساعتِ کنونی پیش از همین لحظه و هنوز در مدت/مهلت ⇒ بستن نادرست بود: `stateOf` همان دم
--                      «فعال» می‌خواند و خانه‌تکانیِ بعدی ستون را برمی‌گرداند (زیرِ قفلِ اعطا، فقط بی ردیفِ زندهٔ دیگر).
--  نمایهٔ جزئی: بررسیِ «برگرداندن» در هر خانه‌تکانی (و `GET /api/me/subscription`) فقط ردیف‌های همین ستون را می‌بیند؛
--  و بستنِ خودکارِ دکان با نمایهٔ عبارتیِ «پایانِ مهلت» دیگر کلِ جدول را نمی‌پیماید.
--  فقط افزودنی (ستونِ NULL‌پذیر). rollback: migrations/rollback/333_subscription_clock_evidence.down.sql
-- ============================================================

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS auto_expired_at bigint;

CREATE INDEX IF NOT EXISTS ix_subs_auto_expired ON subscriptions (auto_expired_at)
  WHERE status = 'expired' AND auto_expired_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_subs_active_grace_end ON subscriptions ((ends_at + grace_days * 86400000::bigint))
  WHERE status = 'active';
