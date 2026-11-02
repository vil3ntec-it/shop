-- ============================================================
--  زمانِ تقریبیِ تحویل — WEB-04 (§۶.۱۴؛ §۲۰.۷: همیشه بازه، هرگز وعدهٔ دقیق) — `lib/storefront-eta.js`.
--
--  storefront_eta  تنظیمِ صاحبِ دکان برای برآورد: نمایش روشن/خاموش، دقیقهٔ پیش‌فرضِ آماده‌سازی
--                  (وقتی سابقهٔ «قبول ⇒ بسته‌بندی» هنوز کم است)، دقیقهٔ پیش‌فرضِ راه، چند سفارش هم‌زمان
--                  آماده می‌شود، و دقیقهٔ راهِ هر ناحیهٔ ارسال (`zone_travel` = {شناسهٔ ناحیه: دقیقه}).
--                  خودِ برآورد از دادهٔ زنده ساخته می‌شود (صف، میانهٔ آماده‌سازی، «شلوغم»، پیک‌ها) و ذخیره
--                  نمی‌شود.
--
--  ⛔ پشتِ پرچمِ خاموشِ `shop.storefront.eta`: تا روشن نشود و صاحب تنظیم نکند هیچ ردیفی نوشته نمی‌شود؛
--  نبودِ ردیف = پیش‌فرض‌ها. فقط افزودنی. برگشت: DROP TABLE storefront_eta.
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_eta (
  shop_id      text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  show         boolean NOT NULL DEFAULT true,
  prep_min     integer NOT NULL DEFAULT 15 CHECK (prep_min BETWEEN 1 AND 240),
  travel_min   integer NOT NULL DEFAULT 20 CHECK (travel_min BETWEEN 0 AND 240),
  lanes        integer NOT NULL DEFAULT 2 CHECK (lanes BETWEEN 1 AND 20),
  zone_travel  jsonb   NOT NULL DEFAULT '{}'::jsonb,
  version      integer NOT NULL DEFAULT 1,
  updated_by   text    NOT NULL DEFAULT '',
  updated_at   bigint  NOT NULL
);
