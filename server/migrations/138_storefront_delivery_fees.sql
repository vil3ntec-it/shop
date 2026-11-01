-- ============================================================
--  ویترین، هزینه‌های ارسال (فاز ۴؛ CHK-06، CHK-07)
--
--  ۱) سبدِ کوچک (CHK-06: «حداقلِ سفارش برای هر ناحیه، یا به‌جایش هزینهٔ سبدِ
--     کوچک»): ناحیه‌ای که `small_basket_fee_minor` دارد سبدِ زیرِ حداقلش را رد
--     نمی‌کند؛ همین مبلغ روی هزینهٔ ارسال می‌آید. NULL ⇒ ردِ زیرِ حداقل (رفتارِ
--     امروز). بی حداقل (۰) معنا ندارد ⇒ CHECK.
--  ۲) هزینهٔ بازه (CHK-07: «… مبلغِ سبد و زمانِ تحویل»): هر قاعدهٔ بازهٔ
--     «ارسال» می‌تواند هزینهٔ اضافهٔ خودش را داشته باشد (مثلاً شبِ جمعه). «ارسالِ
--     رایگان از…» فقط هزینهٔ ناحیه را برمی‌دارد؛ هزینهٔ بازه انتخابِ خودِ
--     مشتری است و جدا نشان داده می‌شود. «دریافت از دکان» هزینه ندارد ⇒ CHECK.
--
--  ⛔ هیچ دادهٔ موجودی عوض نمی‌شود: ستون‌ها پیش‌فرضِ بی‌اثر دارند (NULL و ۰) و
--     خودِ ناحیه و بازه پشتِ پرچمِ خاموشِ `shop.storefront.zones` است.
--  مبلغ‌ها عددِ صحیح به یک‌صدمِ افغانی (مثلِ همهٔ `*_minor`).
--  برگشت: `migrations/rollback/138_storefront_delivery_fees.down.sql`.
-- ============================================================

ALTER TABLE storefront_zones ADD COLUMN IF NOT EXISTS small_basket_fee_minor bigint;
ALTER TABLE storefront_zones DROP CONSTRAINT IF EXISTS storefront_zones_small_basket_fee_check;
ALTER TABLE storefront_zones ADD CONSTRAINT storefront_zones_small_basket_fee_check
  CHECK (small_basket_fee_minor IS NULL OR (small_basket_fee_minor > 0 AND min_order_minor > 0));

ALTER TABLE storefront_slot_rules ADD COLUMN IF NOT EXISTS fee_minor bigint NOT NULL DEFAULT 0;
ALTER TABLE storefront_slot_rules DROP CONSTRAINT IF EXISTS storefront_slot_rules_fee_check;
ALTER TABLE storefront_slot_rules ADD CONSTRAINT storefront_slot_rules_fee_check
  CHECK (fee_minor >= 0 AND (fulfillment = 'delivery' OR fee_minor = 0));
