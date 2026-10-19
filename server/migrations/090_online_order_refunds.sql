-- ============================================================
--  دفترِ بازپرداختِ سفارشِ آنلاین — v3 §۳۸.۲ و §۳۸.۷ (FIN-01)
--
--  تا امروز «بازپرداختِ جزئی/کامل» فقط یک وضعیت بود و هیچ مبلغی
--  ثبت نمی‌شد؛ پس گزارش نمی‌توانست بگوید چه‌قدر پول برگشته است.
--  این جدول هر بازپرداخت را با مبلغ، دلیل، کننده و زمانِ سرور نگه می‌دارد.
--
--  ⛔ فقط افزوده می‌شود — هیچ مسیری ردیفش را ویرایش یا پاک نمی‌کند
--     (سابقهٔ مالی پاک نمی‌شود؛ اصلاح فقط با سندِ جبرانیِ بعدی).
--  ⛔ پول به واحدِ کوچک (یک‌صدمِ افغانی، bigint) — همان `online_orders`.
--  ⛔ تکرارِ همان درخواست بازپرداختِ دوم نمی‌سازد: (order_id, client_key) یکتاست.
--  ⛔ تا پرچمِ `shop.orders.refunds` در «تنظیماتِ زنده» روشن نشود هیچ
--     ردیفی در آن نوشته نمی‌شود (پیش‌فرض خاموش).
--
--  بازگشت: جدول تازه است و هیچ جدولِ موجودی دست نمی‌خورد؛ خاموش ماندنِ
--  پرچم یعنی هیچ مسیری به آن نمی‌رسد.
-- ============================================================

CREATE TABLE IF NOT EXISTS online_order_refunds (
  id            text    PRIMARY KEY,                       -- ULID
  order_id      text    NOT NULL REFERENCES online_orders(id) ON DELETE CASCADE,
  shop_id       text    NOT NULL,
  seq           integer NOT NULL,
  client_key    text    NOT NULL,
  request_hash  text    NOT NULL,
  amount_minor  bigint  NOT NULL CHECK (amount_minor > 0),
  currency      text    NOT NULL DEFAULT 'AFN',
  --  امروز فقط نقد (نقد هنگامِ تحویل)؛ پرداختِ آنلاین منتظرِ D-08 است
  method        text    NOT NULL DEFAULT 'cash' CHECK (method IN ('cash')),
  reason        text    NOT NULL,
  --  «این آخرین بازپرداخت است» ⇒ سفارش بسته می‌شود (جزئی یا کامل)
  final         boolean NOT NULL DEFAULT false,
  actor_id      text    NOT NULL DEFAULT '',
  created_at    bigint  NOT NULL,
  UNIQUE (order_id, client_key),
  UNIQUE (order_id, seq)
);
CREATE INDEX IF NOT EXISTS ix_online_order_refunds_shop ON online_order_refunds(shop_id, created_at);
