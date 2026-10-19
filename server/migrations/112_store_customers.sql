-- ============================================================
--  حسابِ مشتریِ ویترین (فاز ۴؛ ۲۳.۲–۲۳.۵ S131–S140، CHK-01/02/09، WEB-12)
--
--  هویت ── فقط شمارهٔ تأییدشده با کدِ یک‌بارمصرف (همان بلیتِ ۲۳). حسابِ
--           مشتری از حسابِ دکان/کارمند جداست (۲۳.۴) و به هیچ دکانی بند نیست؛
--           هر ویترین فقط سفارش‌های خودش را برای همان شماره نشان می‌دهد.
--  نشست ── توکنِ تصادفیِ ۲۵۶ بیتی؛ در سرور فقط SHA-256 اش. انقضای مطلق،
--           لغو با «خروج»؛ قطعِ اینترنت نشست را پاک نمی‌کند (۲۳.۴).
--  نشانی ─ نشانی‌های نام‌دار (CHK-09): پین منبعِ اصلی است (CHK-08).
--
--  ⛔ سبدِ مهمان در مرورگر می‌ماند؛ ساختنِ حساب آن را جابه‌جا، تکرار یا
--     پاک نمی‌کند (S140). سفارش‌ها همان ردیف‌های `online_orders` ِ دکان‌اند.
--  ⛔ پاک کردنِ حساب ⇒ نشست‌ها و نشانی‌ها پاک می‌شوند؛ سفارش، سندِ دکان است و می‌ماند.
--  بازگشت: DROP چهار جدول — هیچ جدولِ دیگری به این‌ها کلید ندارد.
-- ============================================================

CREATE TABLE IF NOT EXISTS store_customers (
  id             text    PRIMARY KEY,                         -- ULID
  phone          text    NOT NULL UNIQUE CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
  name           text    NOT NULL DEFAULT '' CHECK (length(name) <= 80),
  status         text    NOT NULL DEFAULT 'active' CHECK (status IN ('active','blocked')),
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL
);

CREATE TABLE IF NOT EXISTS store_customer_sessions (
  id             text    PRIMARY KEY,                         -- ULID
  customer_id    text    NOT NULL REFERENCES store_customers(id) ON DELETE CASCADE,
  token_hash     text    NOT NULL UNIQUE,
  created_at     bigint  NOT NULL,
  last_used_at   bigint  NOT NULL,
  expires_at     bigint  NOT NULL,
  revoked_at     bigint
);
CREATE INDEX IF NOT EXISTS ix_store_customer_sessions_customer ON store_customer_sessions(customer_id);

CREATE TABLE IF NOT EXISTS store_customer_addresses (
  id             text    PRIMARY KEY,                         -- ULID
  customer_id    text    NOT NULL REFERENCES store_customers(id) ON DELETE CASCADE,
  label          text    NOT NULL CHECK (length(label) BETWEEN 1 AND 40),
  text           text    NOT NULL DEFAULT '' CHECK (length(text) <= 300),
  landmark       text    NOT NULL DEFAULT '' CHECK (length(landmark) <= 200),
  lat            double precision CHECK (lat IS NULL OR lat BETWEEN -90 AND 90),
  lng            double precision CHECK (lng IS NULL OR lng BETWEEN -180 AND 180),
  created_at     bigint  NOT NULL,
  updated_at     bigint  NOT NULL,
  CHECK ((lat IS NULL) = (lng IS NULL)),
  CHECK (lat IS NOT NULL OR text <> '' OR landmark <> '')
);
CREATE INDEX IF NOT EXISTS ix_store_customer_addresses_customer ON store_customer_addresses(customer_id, created_at);

--  نسلِ بلیت‌های هر شماره (۲۳، بازبینیِ امنیتی): «خروج از همه‌جا» و پاک کردنِ
--  حساب یکی بالا می‌برند ⇒ هر بلیتِ «شماره تأیید شد» ِ پیشین بی‌اعتبار.
--  کلید HMAC ِ شماره است، نه خودِ شماره — پس از پاک کردنِ حساب شمارهٔ خام نمی‌ماند.
--  بی کلیدِ بیرونی: حساب پاک می‌شود، نسل باید بماند.
CREATE TABLE IF NOT EXISTS store_phone_revocations (
  phone_key      text    PRIMARY KEY,                         -- HMAC-SHA256 ِ شماره (hex)
  gen            integer NOT NULL CHECK (gen > 0),
  updated_at     bigint  NOT NULL
);

--  «سفارش‌های من» ِ هر ویترین با همین شماره: نمایهٔ `ix_online_orders_storefront_phone`
--  ِ ‎115_market_customer_orders.sql‎ (customer_phone, created_at DESC) همین را هم
--  می‌پوشاند (سفارش‌های یک شماره اندک‌اند؛ shop_id فیلتر می‌شود). ⛔ پیش‌تر این‌جا
--  نمایه‌ای با **همان نام** و ستون‌های دیگر بود ⇒ با IF NOT EXISTS یکی از دو بی‌صدا
--  ساخته نمی‌شد؛ برداشته شد.
