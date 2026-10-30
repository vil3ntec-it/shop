-- ============================================================
--  ترجیحِ جایگزینی (فاز ۴؛ CHK-15): «یکی کلی و یکی برای هر قلم: بهترین مشابه،
--  کالای مشخص، بدون جایگزین، یا بازپرداخت».
--
--  ۱) `online_orders.substitution`: ترجیحِ کلیِ سفارش. «کالای مشخص» فقط برای قلم
--     معنا دارد ⇒ CHECK بی 'specific'.
--  ۲) `online_order_lines.substitute_product_id` / `substitute_name`: جایگزینِ نام‌دارِ
--     «کالای مشخص» (ویترین: کالای همان ویترین با نامِ همان لحظه؛ کارمند: نامِ آزاد).
--     ترجیحِ مؤثرِ هر قلم (خودش، وگرنه ترجیحِ کلی) در ثبت حل و در همان `substitution` ِ
--     قلم نوشته می‌شود.
--
--  ⛔ هیچ دادهٔ موجودی عوض نمی‌شود: پیش‌فرض‌ها '' اند (همان «بی ترجیح» ِ امروز) و خواندنِ
--     کلیدهای تازه پشتِ پرچمِ خاموشِ `shop.orders.substitution` است.
--  برگشت: `migrations/rollback/144_order_substitution.down.sql`.
-- ============================================================

ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS substitution text NOT NULL DEFAULT '';
ALTER TABLE online_orders DROP CONSTRAINT IF EXISTS online_orders_substitution_check;
ALTER TABLE online_orders ADD CONSTRAINT online_orders_substitution_check
  CHECK (substitution IN ('','best_match','none','refund'));

ALTER TABLE online_order_lines ADD COLUMN IF NOT EXISTS substitute_product_id text NOT NULL DEFAULT '';
ALTER TABLE online_order_lines ADD COLUMN IF NOT EXISTS substitute_name text NOT NULL DEFAULT '';
ALTER TABLE online_order_lines DROP CONSTRAINT IF EXISTS online_order_lines_substitute_check;
ALTER TABLE online_order_lines ADD CONSTRAINT online_order_lines_substitute_check
  CHECK (substitution = 'specific' OR (substitute_product_id = '' AND substitute_name = ''));
