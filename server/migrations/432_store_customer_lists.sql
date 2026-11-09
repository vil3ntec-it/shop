-- ============================================================
--  ۴۳۲ — «فهرستِ خرید» ِ حسابِ مشتری روی سرور (WEB-11؛ ۱۴۰۵/۰۸/۱۹)
--
--  تا امروز «فهرستِ خرید» ِ ویترین فقط روی همان مرورگر بود (`store-list:<نشانی>`)؛ حالا با نشستِ حساب
--  مثلِ ♥ همراهِ مشتری است (وب و برنامهٔ خرید). هر (مشتری، ویترین) یک ردیف؛ `items` آرایهٔ JSON ِ
--  {productId, qtyMilli} — حداکثر ۱۰۰ قلم، همان سقفِ سبد و ثبت. قیمت، نام و موجودی هرگز ذخیره نمی‌شود:
--  هر خواندن از کالای امروزِ ویترین است و «همه به سبد» قیمت را از نو می‌خواند.
--  نسخه ── هر نوشتن یکی بالا می‌برد؛ نوشتن با نسخهٔ کهنه رد می‌شود (۴۰۹). خالی کردن ردیف را نگه می‌دارد
--           تا نسخه به صفر برنگردد (بی ABA).
--
--  ⛔ همان پرچمِ پیش‌فرض خاموشِ `shop.storefront.favorites` ِ ♥: تا روشن نشود این جدول خالی است.
--  ⛔ پاک کردنِ حساب یا دکان ⇒ فهرست هم (cascade)؛ حذفِ دادهٔ مشتری (SEC-10) ⇒ فهرستِ همان دکان.
--  فقط افزودنی. rollback: migrations/rollback/432_store_customer_lists.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS store_customer_lists (
  customer_id    text    NOT NULL REFERENCES store_customers(id) ON DELETE CASCADE,
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  items          jsonb   NOT NULL DEFAULT '[]'::jsonb
                 CHECK (CASE WHEN jsonb_typeof(items) = 'array' THEN jsonb_array_length(items) <= 100 ELSE false END),
  version        integer NOT NULL DEFAULT 0 CHECK (version >= 0),
  updated_at     bigint  NOT NULL,
  PRIMARY KEY (customer_id, shop_id)
);
--  پاک شدنِ دکان (cascade) بی پیمایشِ کلِ جدول
CREATE INDEX IF NOT EXISTS ix_store_customer_lists_shop ON store_customer_lists(shop_id);
