-- ============================================================
--  ۷۷۰ — «حساب‌پیِ دستی» در دفترِ بازپرداختِ سفارش (ORD-15، PAY-23؛ تصمیمِ ۱-۶ ب، ۱۴۰۵/۰۸/۲۰)
--
--  تا امروز روشِ هر ردیفِ `online_order_refunds` فقط `cash` بود (۰۹۰: «پرداختِ آنلاین منتظرِ D-08»). تصمیمِ ۱-۶ ب:
--  تا حساب‌پیِ خودکار، دکان پولِ سفارشِ پرداخت‌شده را خودش (نقد یا حساب‌پی) پس می‌دهد و فقط **ثبت** می‌کند. پس
--  بازپرداختِ بخشی پس از رفتنِ پیک (قلمِ ردشده دمِ در یا برگشتِ بعدی) هم باید روشِ `hesabpay` بپذیرد.
--  ⛔ فقط CHECK گشاد می‌شود؛ هیچ ردیفی دست نمی‌خورد. کد `hesabpay` را فقط با پرچمِ خاموشِ `shop.orders.manual_refund`
--  می‌نویسد (`lib/order-refunds.js`)؛ خاموش ⇒ همان ۴۰۰ `bad_method` و هیچ ردیفِ تازه‌ای با این روش.
--  rollback: migrations/rollback/770_order_refund_manual_method.down.sql
-- ============================================================

ALTER TABLE online_order_refunds DROP CONSTRAINT IF EXISTS online_order_refunds_method_check;
ALTER TABLE online_order_refunds ADD CONSTRAINT online_order_refunds_method_check CHECK (method IN ('cash', 'hesabpay'));
