-- بازارچه (WEB، §۲۳، §۳۱.۱): فهرستِ عمومیِ دکان‌هایی که ویترینِ روشن دارند.
--
-- ⛔ پشتِ پرچمِ `market.directory` (پیش‌فرض خاموش). این جدول فقط «چگونه در
-- فهرست دیده شوم» ِ خودِ دکان است؛ ویترین و کالا همان `storefronts` و
-- `storefront_products` می‌مانند و دست نمی‌خورند.
--
--   listed   ── ⛔ فقط با خواستِ صاحب دکان (بازبینیِ حریم): دکانی که ردیف ندارد
--               یا `false` دارد در فهرست **نیست** — ویترینِ خودش با نشانیِ خودش
--               باز می‌ماند. `true` را فقط خودِ صاحب دکان می‌گذارد.
--   category ── یکی از کلیدهای ثابتِ `lib/market.js` (CATEGORIES) یا ''.
--   area     ── نامِ ناحیه/گذر به متنِ کوتاه؛ هیچ نشانیِ دقیق یا نامِ شخص.
--   geo_*    ── فقط برای «نزدیکِ من»: درشت (یک‌صدمِ درجه ≈ ۱ کیلومتر) و هرگز
--               در پاسخ برنمی‌گردد؛ فقط فاصلهٔ گردشده.
CREATE TABLE IF NOT EXISTS market_listings (
  shop_id     text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  listed      boolean NOT NULL DEFAULT false,
  category    text    NOT NULL DEFAULT '' CHECK (category ~ '^[a-z_]{0,24}$'),
  area        text    NOT NULL DEFAULT '' CHECK (char_length(area) <= 60),
  geo_lat     integer CHECK (geo_lat IS NULL OR geo_lat BETWEEN -9000 AND 9000),
  geo_lng     integer CHECK (geo_lng IS NULL OR geo_lng BETWEEN -18000 AND 18000),
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL,
  CHECK ((geo_lat IS NULL) = (geo_lng IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_market_listings_category ON market_listings(category) WHERE listed;

-- جست‌وجوی کالا در همهٔ ویترین‌ها (بازبینیِ امنیتی: `LIKE '%q%'` روی کلِ کالاها
-- در هر درخواستِ بی‌توکن = DoS ِ ارزان). جدولِ باریک: نامِ یکدست‌شدهٔ هر کالای
-- منتشرشدهٔ دکان‌های در فهرست؛ `lib/market.js` فقط دکانی را که «امضای» کالاهایش
-- (md5 ِ شناسه و نام) عوض شده از نو می‌نویسد. فقط نامزد است: هر نتیجه با دادهٔ
-- زنده دوباره سنجیده می‌شود. بی کلیدِ بیرونی (نسخه‌ای از دادهٔ دیگر جدول‌ها است).
CREATE TABLE IF NOT EXISTS market_search (
  shop_id     text NOT NULL,
  product_id  text NOT NULL,
  name_norm   text NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);
CREATE TABLE IF NOT EXISTS market_search_shops (
  shop_id     text   PRIMARY KEY,
  sig         text   NOT NULL,
  indexed_at  bigint NOT NULL
);

-- نمایهٔ سه‌حرفی فقط اگر `pg_trgm` هست (PostgreSQL با contrib)؛ PGlite ِ بی
-- افزونه ⇒ بی نمایه و پیمایشِ همان جدولِ باریک. نبودنش مهاجرت را نمی‌شکند.
DO $$
BEGIN
  BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm در دسترس نیست؛ market_search بی نمایهٔ سه‌حرفی';
  END;
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
    EXECUTE 'CREATE INDEX IF NOT EXISTS ix_market_search_trgm ON market_search USING gin (name_norm gin_trgm_ops)';
  END IF;
END
$$;
