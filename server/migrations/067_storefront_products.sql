-- ============================================================
--  نشانِ «در ویترین» برای هر کالا — فاز ۴ (PRD-27، WEB-08/09)
--
--  خودِ کالا در دفترِ همگام‌سازی است (`sync_rows`، جدولِ `products`) و
--  این‌جا فقط نشانِ ویترین کنارش می‌نشیند — دادهٔ همگام‌سازی و قالبش
--  دست نمی‌خورد، پس برنامه‌های نصب‌شده هیچ فرقی نمی‌بینند.
--
--  ⛔ پیش‌فرض «منتشر نشده»: کالایی که ردیف ندارد یا `published = false`
--  است در ویترین نیست و از ویترین خریده نمی‌شود.
--  ⛔ قیمت را سرور می‌گوید: `online_price_minor` اگر هست، وگرنه قیمتِ
--  فروشِ همان کالا در دفتر. قیمتی که مشتری می‌فرستد هرگز خوانده نمی‌شود.
-- ============================================================

CREATE TABLE IF NOT EXISTS storefront_products (
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  product_id          text    NOT NULL CHECK (product_id ~ '^[A-Za-z0-9_.:-]{1,80}$'),
  published           boolean NOT NULL DEFAULT false,
  --  null = قیمتِ فروشِ دفتر
  online_price_minor  bigint  CHECK (online_price_minor IS NULL OR online_price_minor >= 0),
  --  null = بی سقف (به هزارمِ واحد، مثلِ `qty_milli`)
  max_qty_milli       bigint  CHECK (max_qty_milli IS NULL OR max_qty_milli > 0),
  updated_by          text    NOT NULL DEFAULT '',
  updated_at          bigint  NOT NULL,
  PRIMARY KEY (shop_id, product_id)
);
CREATE INDEX IF NOT EXISTS ix_storefront_products_published
  ON storefront_products(shop_id, product_id) WHERE published;
