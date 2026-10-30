-- ============================================================
--  M4 (قراردادِ دکان ⇐ بازار، بخشِ ۳): جزئیاتِ کالا در نمای عمومی
--
--  ۱) `market_search` (نمایهٔ باریکِ جست‌وجوی بازار، ۱۱۴) سه ستونِ تازه می‌گیرد
--     تا جست‌وجو با برند و بارکد و «همین کالا در دکان‌های دیگر» با
--     `bank_product_id` بی پیمایشِ کلِ کالاها باشد. مثلِ `name_norm` فقط نامزد
--     است: هر نتیجه با دادهٔ زنده دوباره سنجیده می‌شود.
--  ۲) `market_listings.category_map`: نگاشتِ «دستهٔ آزادِ کالاهای دکان» ⇒ یکی از
--     کلیدهای ثابتِ دستهٔ دکان‌ها (`CATEGORIES` در `lib/market.js`). درختِ دسته
--     ساخته نمی‌شود (پرسشِ بازِ §۶ِ قرارداد).
--
--  ⛔ هیچ دادهٔ موجودی عوض نمی‌شود؛ همه پیش‌فرضِ بی‌اثر دارند. خواندنِ این‌ها
--     پشتِ پرچمِ `market.product_details` (پیش‌فرض خاموش) است.
--  برگشت: `migrations/rollback/122_market_product_details.down.sql`.
-- ============================================================

ALTER TABLE market_search ADD COLUMN IF NOT EXISTS brand_norm text NOT NULL DEFAULT '';
ALTER TABLE market_search ADD COLUMN IF NOT EXISTS barcodes text[] NOT NULL DEFAULT '{}';
ALTER TABLE market_search ADD COLUMN IF NOT EXISTS bank_product_id text NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS ix_market_search_barcodes ON market_search USING gin (barcodes);
CREATE INDEX IF NOT EXISTS ix_market_search_brand ON market_search (brand_norm) WHERE brand_norm <> '';
CREATE INDEX IF NOT EXISTS ix_market_search_bank ON market_search (bank_product_id) WHERE bank_product_id <> '';

ALTER TABLE market_listings ADD COLUMN IF NOT EXISTS category_map jsonb NOT NULL DEFAULT '{}'::jsonb;
