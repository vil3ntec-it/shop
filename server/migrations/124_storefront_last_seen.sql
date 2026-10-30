-- S7 (docs/MARKETPLACE-CONTRACT-fa.md §۳): آخرین تماسِ دستگاهِ دکان، روی خودِ ردیفِ ویترین.
-- هنگامِ همگام‌سازی و ضربانِ `POST /api/shop/storefront/heartbeat` نوشته می‌شود، حداکثر هر ۳۰ ثانیه
-- یک بار (lib/storefront.js: noteSeen). خوانندهٔ آن (حضورِ ویترین و برشِ بازار) فقط با پرچمِ
-- `market.load` (scope ِ all، پیش‌فرض خاموش) است؛ خاموش ⇒ همان MAX(sync_devices.last_seen_at) ِ پیشین.
-- NULL = هنوز هیچ تماسی ثبت نشده (همان ۰ ِ پیشین).
-- پرکردنِ یک‌بارهٔ ردیف‌های موجود از sync_devices تا روشن کردنِ پرچم دکانی را «بی تماس» نشان ندهد.
-- فقط افزودنی. برگشت: ALTER TABLE storefronts DROP COLUMN IF EXISTS last_seen_at;
ALTER TABLE storefronts ADD COLUMN IF NOT EXISTS last_seen_at bigint;
UPDATE storefronts sf SET last_seen_at = d.t
  FROM (SELECT account_id, MAX(last_seen_at) AS t FROM sync_devices
         WHERE app = 'shop' AND account_kind = 'shop' GROUP BY account_id) d
 WHERE d.account_id = sf.shop_id AND sf.last_seen_at IS NULL;
