-- بازارچه (WEB، §۲۳، §۳۱.۱): فهرستِ عمومیِ دکان‌هایی که ویترینِ روشن دارند.
--
-- ⛔ پشتِ پرچمِ `market.directory` (پیش‌فرض خاموش). این جدول فقط «چگونه در
-- فهرست دیده شوم» ِ خودِ دکان است؛ ویترین و کالا همان `storefronts` و
-- `storefront_products` می‌مانند و دست نمی‌خورند.
--
--   listed   ── دکانی که ردیف ندارد «در فهرست» حساب می‌شود (ویترینش از قبل
--               عمومی است)؛ `false` یعنی صاحب دکان نخواسته در فهرست باشد —
--               ویترینِ خودش با نشانیِ خودش باز می‌ماند.
--   category ── یکی از کلیدهای ثابتِ `lib/market.js` (CATEGORIES) یا ''.
--   area     ── نامِ ناحیه/گذر به متنِ کوتاه؛ هیچ نشانیِ دقیق یا نامِ شخص.
--   geo_*    ── فقط برای «نزدیکِ من»: درشت (یک‌صدمِ درجه ≈ ۱ کیلومتر) و هرگز
--               در پاسخ برنمی‌گردد؛ فقط فاصلهٔ گردشده.
CREATE TABLE IF NOT EXISTS market_listings (
  shop_id     text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  listed      boolean NOT NULL DEFAULT true,
  category    text    NOT NULL DEFAULT '' CHECK (category ~ '^[a-z_]{0,24}$'),
  area        text    NOT NULL DEFAULT '' CHECK (char_length(area) <= 60),
  geo_lat     integer CHECK (geo_lat IS NULL OR geo_lat BETWEEN -9000 AND 9000),
  geo_lng     integer CHECK (geo_lng IS NULL OR geo_lng BETWEEN -18000 AND 18000),
  updated_by  text    NOT NULL DEFAULT '',
  updated_at  bigint  NOT NULL,
  CHECK ((geo_lat IS NULL) = (geo_lng IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_market_listings_category ON market_listings(category) WHERE listed;
