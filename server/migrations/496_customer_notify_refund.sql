-- ============================================================
--  ۴۹۶ — اعلانِ هر بازپرداخت به مشتریِ ویترین (NTF-08، §۶.۱۹؛ ۱۴۰۵/۰۸/۲۰)
--
--  هر ردیفِ دفترِ بازپرداخت (`online_order_refunds`، #255) ⇒ دقیقاً یک پیامِ مشتری در همان صفِ
--  `customer_notify_outbox` (همان کانال‌ها: پوشِ وب و …)؛ نوشته در همان تراکنشِ ثبتِ بازپرداخت.
--
--  `refund_id`: ردیفِ دفتری که این پیام از آن است — فقط برای پیامِ بازپرداخت؛ پیام‌های دیگر NULL.
--  ⛔ یکتایی در خودِ پایگاه (نه فقط در کد):
--    • رویدادِ پیامِ بازپرداخت `order_refund:<refund_id>` است و CHECK ِ تازهٔ `event` آن را به همان `refund_id` می‌بندد؛
--      پس همان UNIQUE (order_id, event) ِ ۱۴۳ (دست‌نخورده) هر بازپرداخت را حداکثر یک پیام می‌کند و دو بازپرداختِ
--      یک سفارش دو پیام‌اند. رویدادهای دیگر همان فهرستِ ۱۹۷ با `refund_id` ِ NULL (NTF-04 همان).
--    • FK (refund_id, order_id) ⇒ online_order_refunds(id, order_id): پیام فقط به بازپرداختِ **همان** سفارش اشاره
--      می‌کند (نه سفارشِ دیگر، نه دکانِ دیگر).
--  چرا قیدِ یکتای پیشین برداشته نشد (OPS-03@29): نسخهٔ پیش با `ON CONFLICT (order_id, event)` روی همین پایگاه
--  هم باید بنویسد؛ نمایهٔ دقیقِ (order_id, event) همان می‌ماند.
--
--  ⛔ پیش‌فرض خاموش: ردیف فقط با `shop.customer_notify` + `shop.orders.refund_tracking` (و دفترِ
--  `shop.orders.refunds`) نوشته می‌شود (`lib/customer-notify.onRefundIn`).
--  فقط افزودنی (ستونِ NULL‌پذیر، یکتاییِ بدیهیِ (id, order_id) روی دفتر، CHECK ِ گسترده‌تر).
--  rollback: migrations/rollback/496_customer_notify_refund.down.sql
-- ============================================================

ALTER TABLE online_order_refunds ADD CONSTRAINT online_order_refunds_id_order_key UNIQUE (id, order_id);

ALTER TABLE customer_notify_outbox ADD COLUMN IF NOT EXISTS refund_id text;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_refund_fk
  FOREIGN KEY (refund_id, order_id) REFERENCES online_order_refunds(id, order_id) ON DELETE CASCADE;

ALTER TABLE customer_notify_outbox DROP CONSTRAINT IF EXISTS customer_notify_outbox_event_check;
ALTER TABLE customer_notify_outbox ADD CONSTRAINT customer_notify_outbox_event_check CHECK (
  (refund_id IS NULL AND event IN (
    'order_received', 'order_accepted', 'order_rejected', 'order_ready',
    'order_on_the_way', 'order_delivery_failed', 'order_delivery_retry',
    'order_completed', 'order_cancelled', 'order_late_apology'))
  OR (refund_id IS NOT NULL AND event = 'order_refund:' || refund_id));
