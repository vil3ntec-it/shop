-- ============================================================
--  OPS-09 و SEC-06 — دفترِ مصرفِ پیامک (یک دفتر برای هر دو).
--
--  تا امروز هیچ‌جا ثبت نمی‌شد هر پیامکِ واقعاً فرستاده‌شده مالِ کدام دکان
--  بود و چقدر خرج داشت: `otp_codes` شناسهٔ دکان ندارد و پیامکِ کدِ اشتراک
--  (`vip-codes`) هیچ ردیفی نمی‌گذارد. پس پنلِ VILL3N نه «مصرف و هزینهٔ
--  پیامکِ هر دکان» را می‌دید (OPS-09)، نه سقفِ هزینهٔ روزانه (SEC-06)
--  چیزی برای شمردن داشت.
--
--  یک ردیف = یک تلاشِ فرستادن. `lib/sms-usage.js` تنها نویسنده است.
--    shop_id    '' یعنی بی دکان (ورود، ثبت‌نام، …)
--    via        راهی که واقعاً به کار رفت: sms | webhook | whatsapp | log
--    ok         provider پذیرفت؛ ناموفق هزینه ندارد
--    parts      شمارِ تکه‌های پیامک (صورت‌حساب به‌ازای تکه است)
--    cost_minor هزینه در همان لحظه به واحدِ خُرد (بهای واحد × تکه)؛ راهِ
--               `log` و ناموفق ۰. بهای واحد بعداً عوض شود، گذشته عوض نمی‌شود.
--  ⛔ هیچ ستونِ شماره، متن یا کد: دفترِ هزینه جای دادهٔ شخصی نیست.
--
--  نمایه‌ها: (shop_id, at) برای گزارشِ هر دکان؛ (at) برای جمعِ روزانهٔ
--  سراسری (سقفِ SEC-06). برگشت: DROP TABLE sms_usage.
-- ============================================================
CREATE TABLE IF NOT EXISTS sms_usage (
  id          text     PRIMARY KEY,
  shop_id     text     NOT NULL DEFAULT '',
  app         text     NOT NULL DEFAULT '',
  purpose     text     NOT NULL DEFAULT '',
  via         text     NOT NULL DEFAULT '',
  ok          boolean  NOT NULL DEFAULT true,
  parts       integer  NOT NULL DEFAULT 1 CHECK (parts >= 1),
  cost_minor  bigint   NOT NULL DEFAULT 0 CHECK (cost_minor >= 0),
  at          bigint   NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_sms_usage_shop_at ON sms_usage (shop_id, at);
CREATE INDEX IF NOT EXISTS ix_sms_usage_at ON sms_usage (at);
