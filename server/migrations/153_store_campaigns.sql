-- ============================================================
--  پیوندِ کمپین و QR ِ دکان — MKT-11 (پیوند با ردیابیِ منبع) و MKT-10
--  (QR ِ روی دیوار و رسید که مشتریِ حضوری را به ویترین می‌برد)، بخشِ ۶.۲۰.
--
--  چهار جدولِ تازه؛ هیچ جدولِ موجودی دست نمی‌خورد (نه online_orders، نه Sync):
--   • `store_campaigns` — هر پیوند یک ردیف. `code` نشانیِ کوتاهِ عمومی است
--     (`/store/go/<code>`)، ۱۰ نویسهٔ Crockford، حدس‌نزدنی و سراسری‌یکتا.
--     پیوند به دکان گره دارد، نه به slug: عوض شدنِ نشانیِ ویترین QR ِ چاپ‌شده
--     را نمی‌شکند. `client_id` کلیدِ یکتاییِ برنامه است (دوبار زدن ⇒ همان ردیف).
--   • `store_campaign_days` — شمارِ بازدیدِ یکتای هر روز (روزِ تقویمِ دکان).
--   • `store_campaign_visitors` — فقط برای حذفِ تکرارِ همان روز؛ اثرِ HMAC، نه
--     IP و نه هیچ دادهٔ خامِ مشتری؛ بیش از سه روز نگه داشته نمی‌شود.
--   • `store_campaign_orders` — کدام سفارشِ ویترین از کدام پیوند آمد. جدا از
--     online_orders تا سفارش و گزارش‌های امروز دست نخورند.
--
--  ⛔ پیش‌فرض خاموش: پرچمِ `shop.storefront.campaigns` (و پرچم‌های ویترین).
--  ⛔ shop_id فقط از عضویتِ کاربرِ توکن (دکان) یا از خودِ پیوند (عمومی) می‌آید.
--
--  بازگشت: `migrations/rollback/153_store_campaigns.down.sql`.
-- ============================================================

CREATE TABLE IF NOT EXISTS store_campaigns (
  id          text    PRIMARY KEY,                 -- ULID
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  client_id   text    NOT NULL,
  code        text    NOT NULL UNIQUE CHECK (code ~ '^[0-9A-HJKMNP-TV-Z]{10}$'),
  name        text    NOT NULL,
  source      text    NOT NULL CHECK (source IN ('qr_wall','qr_receipt','social','messaging','sms','print','other')),
  archived    boolean NOT NULL DEFAULT false,
  created_at  bigint  NOT NULL,
  created_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL,
  UNIQUE (shop_id, client_id)
);
CREATE INDEX IF NOT EXISTS ix_store_campaigns_shop ON store_campaigns (shop_id, created_at);

CREATE TABLE IF NOT EXISTS store_campaign_days (
  campaign_id text    NOT NULL REFERENCES store_campaigns(id) ON DELETE CASCADE,
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  day         text    NOT NULL CHECK (day ~ '^\d{4}-\d{2}-\d{2}$'),
  visits      integer NOT NULL DEFAULT 0 CHECK (visits >= 0),
  PRIMARY KEY (campaign_id, day)
);
CREATE INDEX IF NOT EXISTS ix_store_campaign_days_shop ON store_campaign_days (shop_id, day);

CREATE TABLE IF NOT EXISTS store_campaign_visitors (
  campaign_id text    NOT NULL REFERENCES store_campaigns(id) ON DELETE CASCADE,
  day         text    NOT NULL,
  visitor     text    NOT NULL,                     -- HMAC ِ (پیوند، روز، IP، مرورگر) — بازگشت‌ناپذیر
  PRIMARY KEY (campaign_id, day, visitor)
);
CREATE INDEX IF NOT EXISTS ix_store_campaign_visitors_day ON store_campaign_visitors (day);

CREATE TABLE IF NOT EXISTS store_campaign_orders (
  order_id    text    PRIMARY KEY REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  campaign_id text    NOT NULL REFERENCES store_campaigns(id) ON DELETE CASCADE,
  at          bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_store_campaign_orders_campaign ON store_campaign_orders (campaign_id, at);
