-- ============================================================
--  ۲۰۶ — S109 (§۲۰.۱۱، §۲۰.۱۲، §۲۰.۴، §۲۰.۵): شمارشِ منطقه‌ای ِ فروشگاه‌های عمومیِ فعال.
--
--  ۱) ولایت و ولسوالیِ دکان در «نمایش در بازارچه» (`market_listings`) — نوشتهٔ خودِ صاحبِ دکان:
--     • province: کدِ سه‌حرفیِ ISO 3166-2:AF ِ یکی از ۳۴ ولایت (`lib/af-provinces.js`)، یا تهی (نگفته)
--     • district: نامِ ولسوالی، متنِ یکدست‌شده، حداکثر ۶۰ نویسه (فهرستِ مرجعِ ولسوالی‌ها کارِ §۲۰.۵ است)
--  ۲) نشانِ «آزمایشی» یا «تکراری» ِ دکان — نوشتهٔ مدیرِ سامانه با دلیل و سابقه (`/api/admin/shops/:id/census`).
--     فقط از شمارشِ منطقه‌ای کنار می‌گذارد؛ ویترین، بازارچه و فروشِ دکان را هیچ تغییری نمی‌دهد.
--  rollback: migrations/rollback/206_market_regions.down.sql
-- ============================================================

ALTER TABLE market_listings ADD COLUMN IF NOT EXISTS province text NOT NULL DEFAULT ''
  CHECK (province ~ '^([A-Z]{3})?$');
ALTER TABLE market_listings ADD COLUMN IF NOT EXISTS district text NOT NULL DEFAULT ''
  CHECK (char_length(district) <= 60);

CREATE TABLE IF NOT EXISTS shop_census_marks (
  shop_id       text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  kind          text    NOT NULL CHECK (kind IN ('test', 'duplicate')),
  duplicate_of  text    NOT NULL DEFAULT '' CHECK (char_length(duplicate_of) <= 80),
  reason        text    NOT NULL DEFAULT '' CHECK (char_length(reason) <= 500),
  set_by        text    NOT NULL DEFAULT '',
  set_at        bigint  NOT NULL,
  CHECK (kind <> 'duplicate' OR duplicate_of <> ''),
  CHECK (duplicate_of <> shop_id)
);
