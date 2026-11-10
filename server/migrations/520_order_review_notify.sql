-- ============================================================
--  ۵۲۰ — «سفارش به بررسی رفت» در صفِ اعلانِ سفارش (O-06 = ب؛ طرحِ PR ‏#429؛ ۱۴۰۵/۰۸/۱۹)
--
--  وقتی فروشِ حضوری موجودیِ نگه‌داشتهٔ سفارشی را برد (`stock_reservations.short_at`، مهاجرتِ ۴۳۵،
--  `lib/stock-reserve.recheckShort`)، همان صفِ پایدارِ اعلانِ سفارش (`order_notify_outbox`، ۰۸۰ و ۲۱۳) یک ردیفِ
--  یک‌بارهٔ `order_review` می‌گیرد: یک پوش به گیرندگانِ همیشگیِ اعلانِ سفارش، بی یادآوری و بی دادهٔ مشتری.
--  هر سفارش یک بار با همان یکتاییِ (order_id, event) ِ ۰۸۰. پشتِ هر دو پرچمِ خاموشِ `shop.stock_reserve` و
--  `shop.order_notify` ⇒ تا روشن نشوند هیچ ردیفی نوشته نمی‌شود.
--  فقط CHECK ِ `event` بازتر می‌شود؛ هیچ ستون یا ردیفی عوض نمی‌شود.
--  rollback: migrations/rollback/520_order_review_notify.down.sql
-- ============================================================

ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_event_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_event_check
  CHECK (event IN ('order_created', 'order_paid', 'order_cancelled', 'order_review'));
