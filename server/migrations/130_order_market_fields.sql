-- S10 (docs/MARKETPLACE-CONTRACT-fa.md §۳): جای فیلدهای آیندهٔ بازار روی سفارش.
-- هیچ گزارش یا جمعی این‌ها را نمی‌خواند؛ فقط تا داده‌های امروز بعداً قابلِ تفکیک باشند.
-- منطقِ سبدِ چنددکانی ساخته نشده است؛ کارمزد و کمیسیون را `lib/market-fees.js` هنگامِ ساخت می‌نویسد.
--
--   market_order_group_id ── سبدِ چنددکانی (یک پرداخت، چند سفارش). NULL = تک‌دکانی (همهٔ امروز).
--   platform_fee_minor ───── کارمزدِ سامانه، یک‌صدم. پیش‌فرض ۰؛ نرخ از live_config (`market.*`، پیش‌فرض ۰).
--   commission_minor ─────── کمیسیونِ بازار، یک‌صدم. پیش‌فرض ۰؛ همان. یک بار در ساخت، هرگز بازنویسی نه.
--   channel ──────────────── درِ ورودِ سفارشِ مشتری: storefront | market_app | api:<appId>.
--                            '' = نامعلوم/نه از مشتری: همهٔ ردیف‌های پیشین و سفارشِ دستیِ کارمند
--                            (منبعِ آن همان `source` است). سفارشِ تازهٔ ویترین ⇒ 'storefront'
--                            (lib/orders.js `create`؛ سرور می‌گذارد، نه بدنهٔ درخواست).
--
-- فقط افزودنی با پیش‌فرضِ ثابت (بی بازنویسیِ جدول) — هیچ داده‌ای عوض نمی‌شود.
-- برگشت (همراه با برگرداندنِ کدِ lib/orders.js که `channel` را می‌نویسد؛ آزمون همین خط‌ها را اجرا می‌کند):
--   rollback: ALTER TABLE online_orders DROP CONSTRAINT IF EXISTS ck_online_orders_channel;
--   rollback: ALTER TABLE online_orders DROP COLUMN IF EXISTS channel;
--   rollback: ALTER TABLE online_orders DROP COLUMN IF EXISTS commission_minor;
--   rollback: ALTER TABLE online_orders DROP COLUMN IF EXISTS platform_fee_minor;
--   rollback: ALTER TABLE online_orders DROP COLUMN IF EXISTS market_order_group_id;
--   rollback: DELETE FROM schema_migrations WHERE version = '130_order_market_fields.sql';
ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS market_order_group_id text;
ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS platform_fee_minor bigint NOT NULL DEFAULT 0 CHECK (platform_fee_minor >= 0);
ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS commission_minor bigint NOT NULL DEFAULT 0 CHECK (commission_minor >= 0);
ALTER TABLE online_orders ADD COLUMN IF NOT EXISTS channel text NOT NULL DEFAULT '';
ALTER TABLE online_orders DROP CONSTRAINT IF EXISTS ck_online_orders_channel;
ALTER TABLE online_orders ADD CONSTRAINT ck_online_orders_channel
  CHECK (channel IN ('', 'storefront', 'market_app') OR channel ~ '^api:[A-Za-z0-9_.-]{1,64}$');
