-- ============================================================
--  دستورِ تحویل (فاز ۴؛ CHK-14، CHK-24): رویدادِ تازهٔ `delivery_edit` در خطِ زمانیِ
--  سفارش — هر ویرایشِ «پیش از رسیدن زنگ بزنید»، «تحویل به…»، نشانه یا مبلغِ اسکناسِ
--  مشتری تا پیش از ارسال، با «پیش» و «پس» در `detail`.
--
--  مبلغِ اسکناس خودش ستون نمی‌خواهد: کنارِ دستورهای دیگر در `online_orders.delivery`
--  (jsonb) می‌نشیند و هیچ اثری بر جمع، COD یا تسویه ندارد.
--
--  ⛔ هیچ دادهٔ موجودی عوض نمی‌شود: فقط فهرستِ مجازِ `kind` یکی بزرگ‌تر می‌شود. نوشتنِ این
--     رویداد پشتِ پرچمِ خاموشِ `shop.orders.delivery_prefs` است.
--  برگشت: `migrations/rollback/146_order_delivery_edit_event.down.sql`.
-- ============================================================

ALTER TABLE online_order_events DROP CONSTRAINT IF EXISTS online_order_events_kind_check;
ALTER TABLE online_order_events ADD CONSTRAINT online_order_events_kind_check
  CHECK (kind IN ('created','status','payment','cod','note','delivery_edit'));
