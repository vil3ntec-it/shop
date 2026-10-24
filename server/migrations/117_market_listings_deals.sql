-- بازارچه، «ارزان‌تر از قیمتِ دکان» (#345): قیمتِ فروشِ دفتر فقط با خواستِ صاحبِ دکان در
-- بازارچه دیده می‌شود. پیش‌فرض خاموش ⇒ هیچ قیمتِ دفتری منتشر نمی‌شود. فقط افزودنی.
ALTER TABLE market_listings ADD COLUMN IF NOT EXISTS show_deals boolean NOT NULL DEFAULT false;
