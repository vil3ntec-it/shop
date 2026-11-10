-- ============================================================
--  ۴۹۱ — بالای قیفِ ویترینِ وب برای «گزارشِ فروشِ اینترنتی» (RPT-14؛ ۱۴۰۵/۰۸/۲۰)
--
--  قیفِ گزارش از «ثبت» آغاز می‌شد؛ بازدید ⇐ دیدنِ کالا ⇐ سبد نبود (`visits: null`). ویترینِ وب با پرچمِ روشن
--  برای هر گام یک تپشِ بی بدنه می‌فرستد (`POST /api/store/:slug/funnel/:step`، `lib/storefront-funnel.js`).
--    storefront_funnel_seen ─ فقط برای حذفِ تکرارِ همان روز: (دکان، روزِ UTC، بازدیدکننده، گام)؛ بازدیدکننده
--                             HMAC ِ (دکان، روز، IP، مرورگر) است — بازگشت‌ناپذیر و هر روز تازه؛ ۲ روز می‌ماند.
--    storefront_funnel_days ─ شمارِ یکتای هر روز (بازدید، دیدنِ کالا، سبد)؛ ۹۲ روز، همان پنجرهٔ گزارش.
--  ⛔ نه IP، نه مرورگر، نه شمارهٔ مشتری. پرچمِ خاموشِ `shop.orders.online_report` ⇒ هیچ ردیفی.
--  فقط افزودنی. rollback: migrations/rollback/491_storefront_funnel.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_funnel_seen (
  shop_id   text     NOT NULL,
  day       integer  NOT NULL,                                     -- روزِ UTC (ms / 86400000)
  visitor   text     NOT NULL CHECK (length(visitor) = 22),        -- HMAC (base64url) — بازگشت‌ناپذیر
  step      smallint NOT NULL CHECK (step BETWEEN 1 AND 3),        -- ۱ بازدید، ۲ دیدنِ کالا، ۳ سبد
  PRIMARY KEY (shop_id, day, visitor, step)
);
--  نگهداری (پاک کردنِ روزهای کهنه‌تر از ۲ روز) بی پیمایشِ هر دکان
CREATE INDEX IF NOT EXISTS ix_storefront_funnel_seen_day ON storefront_funnel_seen (day);

CREATE TABLE IF NOT EXISTS storefront_funnel_days (
  shop_id   text     NOT NULL,
  day       integer  NOT NULL,
  visits    integer  NOT NULL DEFAULT 0 CHECK (visits >= 0),
  views     integer  NOT NULL DEFAULT 0 CHECK (views >= 0),
  carts     integer  NOT NULL DEFAULT 0 CHECK (carts >= 0),
  PRIMARY KEY (shop_id, day)
);
CREATE INDEX IF NOT EXISTS ix_storefront_funnel_days_day ON storefront_funnel_days (day);
