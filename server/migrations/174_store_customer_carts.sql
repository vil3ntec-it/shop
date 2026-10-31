-- ============================================================
--  سبدِ ماندگارِ حسابِ مشتری روی سرور (فاز ۴؛ CHK-03، ۲۳ S140)
--
--  هر (مشتری، ویترین) یک ردیف. `items` آرایهٔ JSON ِ قلم‌هاست:
--  {productId, qtyMilli, note} — حداکثر ۱۰۰ قلم، همان سقفِ ثبتِ سفارش.
--  قیمت ذخیره نمی‌شود؛ هر خواندن از کالای ویترین و ثبت دوباره می‌سنجد (CHK-17).
--  نسخه ── هر نوشتن یکی بالا می‌برد و نوشتن با نسخهٔ کهنه رد می‌شود. خالی کردن
--           ردیف را نگه می‌دارد تا نسخه به صفر برنگردد (بی ABA).
--
--  ⛔ پرچمِ پیش‌فرض خاموشِ `shop.storefront.cart`: تا روشن نشود این جدول خالی است.
--  ⛔ پاک کردنِ حساب یا دکان ⇒ سبد هم (cascade). سبدِ مهمان در مرورگر می‌ماند (S140).
--  بازگشت: DROP همین جدول — هیچ جدولِ دیگری به آن کلید ندارد.
-- ============================================================

CREATE TABLE IF NOT EXISTS store_customer_carts (
  customer_id    text    NOT NULL REFERENCES store_customers(id) ON DELETE CASCADE,
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  items          jsonb   NOT NULL DEFAULT '[]'::jsonb
                 CHECK (CASE WHEN jsonb_typeof(items) = 'array' THEN jsonb_array_length(items) <= 100 ELSE false END),
  version        integer NOT NULL DEFAULT 0 CHECK (version >= 0),
  updated_at     bigint  NOT NULL,
  PRIMARY KEY (customer_id, shop_id)
);
--  پاک شدنِ دکان (cascade) بی پیمایشِ کلِ جدول
CREATE INDEX IF NOT EXISTS ix_store_customer_carts_shop ON store_customer_carts(shop_id);
