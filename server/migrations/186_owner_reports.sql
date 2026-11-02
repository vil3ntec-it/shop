-- ============================================================
--  گزارشِ خودکار برای صاحبِ دکان — PAY-20 (پایانِ روز) و RPT-12 (زمان‌بندی‌شده)
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.owner_report` (`lib/owner-report.js`): تا روشن نشود کارگر به دکان دست
--  نمی‌زند و هیچ ردیفی در این دو جدول نوشته نمی‌شود.
--
--  owner_report_settings  تنظیمِ هر دکان: فقط صاحبِ دکان می‌نویسد (`PUT /api/shop/owner-report`).
--                         ساعت به وقتِ محلیِ دکان؛ هفتگی ⇒ روزِ هفته (۰ = یکشنبه … ۶ = شنبه، همان getUTCDay).
--  owner_reports          هر گزارش یک ردیف؛ `UNIQUE (shop_id, report_date)` ⇒ هر دکان در هر روزِ محلی
--                         حداکثر **یک** گزارش، حتی با دو نمونهٔ سرور یا دو دورِ پیاپیِ کارگر: ردیف پیش از
--                         ساختن و فرستادن گرفته می‌شود (`ON CONFLICT DO NOTHING`)؛ فقط برندهٔ درج می‌فرستد.
--
--  فقط افزودنی. برگشت: migrations/rollback/186_owner_reports.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS owner_report_settings (
  shop_id      text     PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  active       boolean  NOT NULL DEFAULT false,
  frequency    text     NOT NULL DEFAULT 'daily' CHECK (frequency IN ('daily','weekly')),
  weekday      smallint NOT NULL DEFAULT 6 CHECK (weekday BETWEEN 0 AND 6),
  hour         smallint NOT NULL DEFAULT 21 CHECK (hour BETWEEN 0 AND 23),
  channels     jsonb    NOT NULL DEFAULT '["inapp"]'::jsonb,
  version      integer  NOT NULL DEFAULT 1,
  updated_by   text     NOT NULL DEFAULT '',
  updated_at   bigint   NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_owner_report_settings_active ON owner_report_settings(shop_id) WHERE active = true;

CREATE TABLE IF NOT EXISTS owner_reports (
  id           text     PRIMARY KEY,                    -- ULID
  shop_id      text     NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  report_date  text     NOT NULL,                       -- روزِ محلیِ ارسال (YYYY-MM-DD)
  frequency    text     NOT NULL CHECK (frequency IN ('daily','weekly')),
  from_date    text     NOT NULL,
  to_date      text     NOT NULL,
  until_at     bigint   NOT NULL,                       -- مرزِ بالای بازه (میلی‌ثانیه، بی خودش)
  timezone     text     NOT NULL,
  status       text     NOT NULL DEFAULT 'claimed' CHECK (status IN ('claimed','sent','error')),
  summary      jsonb    NOT NULL DEFAULT '{}'::jsonb,   -- فقط عددهای ردیف‌های همگام‌شده
  results      jsonb    NOT NULL DEFAULT '{}'::jsonb,   -- نتیجهٔ واقعیِ هر کانال
  notice_id    text     NOT NULL DEFAULT '',
  error        text     NOT NULL DEFAULT '',
  created_at   bigint   NOT NULL,
  sent_at      bigint,
  UNIQUE (shop_id, report_date)
);
CREATE INDEX IF NOT EXISTS ix_owner_reports_shop ON owner_reports(shop_id, created_at DESC);
