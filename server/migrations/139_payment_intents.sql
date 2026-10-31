-- ============================================================
--  پرداختِ درگاهی (حساب‌پی) — PAY-21، PAY-08، سناریوی S35
--
--  payment_intents: «قصدِ پرداخت»ِ یک مبلغِ دقیق برای یک فروشِ صندوق
--  (`pos_sale`، QR سرِ پیشخوان) یا یک سفارشِ آنلاین (`online_order`).
--  payment_events:  هر وب‌هوک/پاسخِ ارائه‌دهنده، یک بار (provider_event_id یکتا).
--
--  ⛔ پول به واحدِ کوچک (یک‌صدمِ افغانی، bigint) — همان `online_orders.total_minor`.
--  ⛔ وضعیت فقط با `lib/payment-intents.js` و compare-and-set روی status عوض می‌شود.
--  ⛔ هر فروش/سفارش در هر لحظه حداکثر یک قصدِ زنده (created/pending) دارد
--     (ux_payment_intents_live_ref)؛ ref_id ِ قصد هرگز عوض نمی‌شود ⇒ یک قصد فقط یک فروش.
--  ⛔ تا پرچمِ `pay.hesabpay` روشن و کلیدهای HESABPAY_* داده نشده‌اند هیچ ردیفی
--     نوشته نمی‌شود (پیش‌فرض خاموش).
--  ⛔ raw ِ رویداد بی هیچ رازی است (امضا و سرآیندها نگه داشته نمی‌شوند).
--
--  فقط افزودنی — هیچ جدولِ موجودی دست نمی‌خورد.
--  بازگشت: DROP TABLE payment_events; DROP TABLE payment_intents;
-- ============================================================

CREATE TABLE IF NOT EXISTS payment_intents (
  id                  text    PRIMARY KEY,                       -- pi_<hex>
  shop_id             text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  kind                text    NOT NULL CHECK (kind IN ('pos_sale','online_order')),
  --  pos_sale: شناسهٔ فروشِ دفترِ دستگاه؛ online_order: online_orders.id
  ref_id              text    NOT NULL,
  amount_minor        bigint  NOT NULL CHECK (amount_minor > 0),
  currency            text    NOT NULL DEFAULT 'AFN' CHECK (currency IN ('AFN')),
  status              text    NOT NULL DEFAULT 'created' CHECK (status IN (
                        'created','pending','paid','failed','expired','cancelled','refunded')),
  provider            text    NOT NULL,
  provider_session_id text    UNIQUE,
  checkout_url        text    NOT NULL DEFAULT '',
  idempotency_key     text    NOT NULL,
  request_hash        text    NOT NULL,
  expires_at          bigint  NOT NULL,
  paid_at             bigint,
  provider_ref        text    NOT NULL DEFAULT '',
  status_reason       text    NOT NULL DEFAULT '',
  --  S35: پرداختِ دیر/دوباره یا اختلافِ مبلغ ⇒ بررسیِ دستی و تطبیق
  needs_review        boolean NOT NULL DEFAULT false,
  created_by          text    NOT NULL DEFAULT '',
  version             integer NOT NULL DEFAULT 1,
  created_at          bigint  NOT NULL,
  updated_at          bigint  NOT NULL,
  UNIQUE (shop_id, idempotency_key)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_intents_live_ref
  ON payment_intents(shop_id, kind, ref_id) WHERE status IN ('created','pending');
CREATE INDEX IF NOT EXISTS ix_payment_intents_ref ON payment_intents(shop_id, kind, ref_id);
CREATE INDEX IF NOT EXISTS ix_payment_intents_status ON payment_intents(status, updated_at);

CREATE TABLE IF NOT EXISTS payment_events (
  id                  text    PRIMARY KEY,                       -- pe_<hex>
  provider            text    NOT NULL,
  provider_event_id   text    NOT NULL UNIQUE,
  --  NULL = قصدش پیدا نشد (۲۰۰ و لاگ؛ بی طوفانِ تکرار)
  intent_id           text    REFERENCES payment_intents(id) ON DELETE CASCADE,
  type                text    NOT NULL,
  --  applied | duplicate_state | mismatch | unconfirmed | deferred | unknown_intent | ignored
  outcome             text    NOT NULL DEFAULT '',
  raw                 jsonb   NOT NULL DEFAULT '{}'::jsonb,
  received_at         bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_payment_events_intent ON payment_events(intent_id, received_at);
