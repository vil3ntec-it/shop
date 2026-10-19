-- ============================================================
--  ویترینِ اینترنتیِ هر دکان — فاز ۴ (WEB-01/03، بخش‌های ۳.۱۰ و ۳۱؛ D-06)
--
--  دو جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد.
--
--  ⛔ پیش‌فرض خاموش، سه قفل پشتِ هم: پرچم‌های `shop.orders` و
--  `shop.storefront` در «تنظیماتِ زنده» + `enabled` ِ خودِ دکان. تا هر سه
--  روشن نباشند نشانیِ عمومی ۴۰۴ است و هیچ ردیفی ساخته نمی‌شود.
--  ⛔ نشانی (D-06): `{slug}.vill3n.top` — `slug` یکتاست و کوچک.
--  ⛔ پول به واحدِ کوچک (یک‌صدمِ افغانی، bigint)، مثلِ `online_orders`.
--  ⛔ WEB-23: نامِ ویترین نامِ کسب‌وکار است؛ نام، عکس یا نشانیِ شخص نه.
--  ⛔ ۳۱.۱: «باز/بسته» (ساعتِ کاری + اعلامِ مالک)، «اتصالِ تأییدشده»
--  (آخرین تماسِ واقعیِ دستگاه) و «پذیرشِ سفارش» سه چیزِ جدایند؛ این‌جا
--  فقط اولی ذخیره می‌شود — دو تای دیگر هر بار از دادهٔ واقعی حساب می‌شوند.
-- ============================================================

CREATE TABLE IF NOT EXISTS storefronts (
  shop_id             text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  slug                text    NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$'),
  enabled             boolean NOT NULL DEFAULT false,
  title               text    NOT NULL DEFAULT '',
  tagline             text    NOT NULL DEFAULT '',
  --  راهِ تماسِ کسب‌وکار (WEB-02) — شمارهٔ شخصیِ کسی نه
  contact_phone       text    NOT NULL DEFAULT '',
  --  رنگِ ویترین (WEB-01): #rrggbb یا خالی = رنگِ برنامه
  accent              text    NOT NULL DEFAULT '' CHECK (accent = '' OR accent ~ '^#[0-9a-f]{6}$'),
  --  پیامِ کوتاهِ مالک کنارِ وضعیت (۳۱.۱)
  notice              text    NOT NULL DEFAULT '',
  --  ۳۱.۱/۳۱.۲ — ساعتِ دکان مرجع است، نه ساعتِ گوشیِ مشتری
  timezone            text    NOT NULL DEFAULT 'Asia/Kabul',
  --  {"0":[[480,1200]], …} — روزِ هفته (۰ = یکشنبه) ⇒ بازه‌ها به دقیقه از نیمه‌شب؛ {} = تنظیم نشده
  weekly_hours        jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  ["2026-10-20", …] — تعطیلیِ استثنایی به تاریخِ محلیِ دکان
  closed_dates        jsonb   NOT NULL DEFAULT '[]'::jsonb,
  --  اعلامِ مالک: auto = طبقِ ساعتِ کاری؛ online_until با declared_until
  presence_mode       text    NOT NULL DEFAULT 'auto'
                        CHECK (presence_mode IN ('auto','online','online_until','offline','closed')),
  declared_until      bigint,
  accept_delivery     boolean NOT NULL DEFAULT true,
  accept_pickup       boolean NOT NULL DEFAULT true,
  delivery_fee_minor  bigint  NOT NULL DEFAULT 0 CHECK (delivery_fee_minor >= 0),
  --  CHK-06 (سادهٔ امروز: یک حداقل برای همهٔ ناحیه‌ها)
  min_order_minor     bigint  NOT NULL DEFAULT 0 CHECK (min_order_minor >= 0),
  updated_by          text    NOT NULL DEFAULT '',
  created_at          bigint  NOT NULL,
  updated_at          bigint  NOT NULL,
  CHECK (accept_delivery OR accept_pickup),
  CHECK (presence_mode <> 'online_until' OR declared_until IS NOT NULL)
);

--  ۳۱.۲ بندِ ۱ — هر اعلامِ آنلاین/آفلاین یا تغییرِ ساعت یک رویداد؛ فقط
--  افزوده می‌شود. زمانِ سرور مرجع است؛ زمانِ دستگاه فقط اطلاعاتِ کمکی.
--  (shop_id, event_id) یکتاست تا صفِ آفلاینِ برنامه دوباره‌فرستادن را بی‌اثر کند.
CREATE TABLE IF NOT EXISTS storefront_presence_events (
  id                  text    PRIMARY KEY,                 -- ULID
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  event_id            text    NOT NULL,
  actor_id            text    NOT NULL DEFAULT '',
  kind                text    NOT NULL CHECK (kind IN ('presence','hours')),
  status              text    NOT NULL DEFAULT '',
  declared_until      bigint,
  store_timezone      text    NOT NULL DEFAULT '',
  detail              jsonb   NOT NULL DEFAULT '{}'::jsonb,
  source_device_id    text    NOT NULL DEFAULT '',
  client_event_at     bigint,
  server_received_at  bigint  NOT NULL,
  UNIQUE (shop_id, event_id)
);
CREATE INDEX IF NOT EXISTS ix_storefront_presence_events_shop
  ON storefront_presence_events(shop_id, server_received_at);
