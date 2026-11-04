-- ============================================================
--  ۲۱۳ — پوشِ گوشی با Firebase (FCM) برای اعلانِ سفارش وقتی برنامه بسته است (تصمیمِ O-01، `docs/FIREBASE-SETUP-fa.md`).
--
--  ۱) دفترِ توکنِ دستگاه (`push_tokens`، مهاجرتِ ۰۰۸): «چرا کهنه شد» و «کی» — برداشتنِ عضو، خروج از دستگاه،
--     از کار انداختنِ دستگاه، توکنِ تازهٔ همان دستگاه، یا پاسخِ UNREGISTERED/INVALID ِ FCM (`lib/push.js`).
--     نمایهٔ `device_uid` برای «همهٔ توکن‌های این دستگاه» هنگامِ خروج.
--  ۲) صفِ اعلانِ سفارش (`order_notify_outbox`، مهاجرتِ ۰۸۰): دو رویدادِ تازهٔ یک‌باره — «پرداخت شد» و «مشتری لغو
--     کرد» — و حالِ پایانیِ `delivered` («سرویسِ پوش پذیرفت؛ یادآوری ندارد»). همان پرچمِ `shop.order_notify`.
--  ۳) یک بار: توکنِ فعالِ کسی که دیگر عضوِ فعالِ همان دکان نیست کهنه می‌شود — پیش از این، شاگردِ برداشته‌شده
--     هنوز خبرِ «کالا تمام شد» ِ همان دکان را روی گوشی‌اش می‌گرفت.
--
--  بی کلیدِ Firebase روی سرور هیچ پیامی نمی‌رود (`lib/push.js` یک بار در لاگ می‌گوید و کاری نمی‌کند).
--  rollback: migrations/rollback/213_device_push.down.sql
-- ============================================================

ALTER TABLE push_tokens ADD COLUMN IF NOT EXISTS stale_reason text NOT NULL DEFAULT '';
ALTER TABLE push_tokens ADD COLUMN IF NOT EXISTS stale_at bigint;
CREATE INDEX IF NOT EXISTS idx_push_device ON push_tokens(device_uid) WHERE device_uid <> '';

ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_event_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_event_check
  CHECK (event IN ('order_created', 'order_paid', 'order_cancelled'));

ALTER TABLE order_notify_outbox DROP CONSTRAINT IF EXISTS order_notify_outbox_status_check;
ALTER TABLE order_notify_outbox ADD CONSTRAINT order_notify_outbox_status_check CHECK (status IN (
  'pending', 'sending', 'provider_accepted', 'acked', 'failed', 'unacked', 'cancelled',
  'delivered'));   -- رویدادِ یک‌باره (پرداخت/لغو): پوش پذیرفته شد و کارِ دیگری نمانده

UPDATE push_tokens t
   SET status = 'stale', stale_reason = 'member_removed', stale_at = (extract(epoch FROM now()) * 1000)::bigint
 WHERE t.app = 'shop' AND t.status = 'active' AND t.shop_id <> '' AND t.user_id <> ''
   AND NOT EXISTS (SELECT 1 FROM shop_members m
                    WHERE m.shop_id = t.shop_id AND m.user_id = t.user_id AND m.status = 'active')
   AND NOT EXISTS (SELECT 1 FROM shops s WHERE s.id = t.shop_id AND s.owner_user_id = t.user_id);
