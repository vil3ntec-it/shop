-- ============================================================
--  فروش، تخفیف، کمپین، افزونه و پرداخت — بخش‌های ۱۱.۳.۲ و ۱۱.۴
--
--  ⛔ هر جدولی که برای دو بخش است ستونِ `app` دارد و هر پرس‌وجویی
--  روی آن باید `app` را شرط کند — همان قاعدهٔ `plans`.
-- ============================================================

-- ---------- کدهای تخفیف ----------
CREATE TABLE IF NOT EXISTS discount_codes (
  id                text   PRIMARY KEY,
  code              text   NOT NULL,
  app               text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump')),
  --  خالی = روی همهٔ پلن‌های همان بخش
  plan              text   NOT NULL DEFAULT '',
  kind              text   NOT NULL DEFAULT 'percent' CHECK (kind IN ('percent','amount')),
  value             integer NOT NULL DEFAULT 0,
  --  برای «مبلغی»: AFN | USD
  currency          text   NOT NULL DEFAULT 'AFN',
  --  خالی = هر مشتری
  user_id           text   NOT NULL DEFAULT '',
  expires_at        bigint,
  --  NULL = بی‌سقف
  max_uses          integer,
  once_per_customer boolean NOT NULL DEFAULT true,
  uses              integer NOT NULL DEFAULT 0,
  note              text   NOT NULL DEFAULT '',
  status            text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked')),
  created_by        text   NOT NULL DEFAULT '',
  created_at        bigint NOT NULL,
  UNIQUE (app, code)
);
CREATE INDEX IF NOT EXISTS idx_discount_codes_app ON discount_codes(app, status);

CREATE TABLE IF NOT EXISTS discount_uses (
  id               text   PRIMARY KEY,
  code_id          text   NOT NULL REFERENCES discount_codes(id) ON DELETE CASCADE,
  app              text   NOT NULL DEFAULT 'shop',
  user_id          text   NOT NULL DEFAULT '',
  tenant_id        text   NOT NULL DEFAULT '',
  subscription_id  text   NOT NULL DEFAULT '',
  price            integer NOT NULL DEFAULT 0,
  final_price      integer NOT NULL DEFAULT 0,
  created_at       bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_discount_uses_code ON discount_uses(code_id);

-- ---------- تاریخچهٔ قیمت ----------
--  هر بار که قیمتِ یک پلن عوض می‌شود یک ردیف. اشتراک‌های قبلی قیمتِ
--  خودشان را دارند (ستونِ price روی خودِ اشتراک، پایین).
CREATE TABLE IF NOT EXISTS plan_price_history (
  id          text   PRIMARY KEY,
  app         text   NOT NULL DEFAULT 'shop',
  plan        text   NOT NULL,
  prev_price  integer,
  price       integer NOT NULL,
  currency    text   NOT NULL DEFAULT 'AFN',
  changed_at  bigint NOT NULL,
  changed_by  text   NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_price_hist ON plan_price_history(app, plan, changed_at DESC);

-- ---------- کمپین ----------
CREATE TABLE IF NOT EXISTS campaigns (
  id                text   PRIMARY KEY,
  name              text   NOT NULL DEFAULT '',
  app               text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump','both')),
  filter            jsonb  NOT NULL DEFAULT '{}'::jsonb,
  discount_code_id  text   NOT NULL DEFAULT '',
  notice_id         text   NOT NULL DEFAULT '',
  status            text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','closed')),
  created_by        text   NOT NULL DEFAULT '',
  created_at        bigint NOT NULL
);

-- ---------- افزونه‌ها (Add-on) ----------
--  یک قابلیتِ جدا روی یک اشتراک. در `entitlement.js` به فهرستِ
--  قابلیت‌های پلن اضافه می‌شود — قاعدهٔ «فهرستِ خالی = پلنِ کامل» دست
--  نمی‌خورد.
CREATE TABLE IF NOT EXISTS subscription_addons (
  id               text   PRIMARY KEY,
  app              text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump')),
  subscription_id  text   NOT NULL,
  tenant_id        text   NOT NULL,
  feature          text   NOT NULL,
  price            integer NOT NULL DEFAULT 0,
  currency         text   NOT NULL DEFAULT 'AFN',
  note             text   NOT NULL DEFAULT '',
  created_by       text   NOT NULL DEFAULT '',
  created_at       bigint NOT NULL,
  removed_at       bigint
);
CREATE INDEX IF NOT EXISTS idx_addons_sub ON subscription_addons(app, subscription_id) WHERE removed_at IS NULL;

-- ---------- پرداخت‌ها ----------
--  ⛔ نامِ جدول `sub_payments` است، نه `payments`: جدولِ `payments` از
--  ۰۰۲ مالِ **دادهٔ خودِ دکان** است (رسیدهای مشتریِ دکان‌دار، همگام‌شده
--  از برنامه). هم‌نام کردنشان یعنی `CREATE TABLE IF NOT EXISTS` بی‌صدا
--  رد می‌شود و ایندکس روی ستونِ نبودهٔ `app` می‌شکند — همان اتفاقی که
--  یک بار افتاد.
CREATE TABLE IF NOT EXISTS sub_payments (
  id               text   PRIMARY KEY,
  app              text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump')),
  --  shop | station
  tenant_kind      text   NOT NULL DEFAULT 'shop',
  tenant_id        text   NOT NULL,
  subscription_id  text   NOT NULL DEFAULT '',
  amount           integer NOT NULL,
  currency         text   NOT NULL DEFAULT 'AFN' CHECK (currency IN ('AFN','USD')),
  method           text   NOT NULL DEFAULT 'cash' CHECK (method IN ('cash','hawala','exchange')),
  receipt_no       text   NOT NULL DEFAULT '',
  note             text   NOT NULL DEFAULT '',
  paid_at          bigint NOT NULL,
  created_by       text   NOT NULL DEFAULT '',
  created_at       bigint NOT NULL,
  deleted          boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_sub_payments_app    ON sub_payments(app, paid_at DESC) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_sub_payments_tenant ON sub_payments(app, tenant_id) WHERE deleted = false;

-- ---------- قیمتِ خودِ اشتراک ----------
--  «مشتری‌های فعلی تا پایانِ دوره‌شان با قیمتِ قبلی هستند» فقط وقتی
--  ممکن است که قیمت روی خودِ اشتراک نشسته باشد. تا امروز نبود: اشتراک
--  فقط کدِ پلن را داشت و قیمت همیشه از جدولِ پلن‌ها خوانده می‌شد.
ALTER TABLE subscriptions         ADD COLUMN IF NOT EXISTS price    integer;
ALTER TABLE subscriptions         ADD COLUMN IF NOT EXISTS currency text NOT NULL DEFAULT 'AFN';
ALTER TABLE station_subscriptions ADD COLUMN IF NOT EXISTS price    integer;
ALTER TABLE station_subscriptions ADD COLUMN IF NOT EXISTS currency text NOT NULL DEFAULT 'USD';

--  تخفیفِ مستقیم و افزونه هم در تاریخچه می‌نشینند
ALTER TABLE subscription_history DROP CONSTRAINT IF EXISTS subscription_history_action_check;
ALTER TABLE subscription_history ADD CONSTRAINT subscription_history_action_check
  CHECK (action IN ('grant','renew','status','expire','discount','addon','permanent'));
ALTER TABLE station_subscription_history DROP CONSTRAINT IF EXISTS station_subscription_history_action_check;
ALTER TABLE station_subscription_history ADD CONSTRAINT station_subscription_history_action_check
  CHECK (action IN ('grant','renew','status','expire','discount','addon','permanent'));

-- ---------- Cloud Sync — انتخابِ خودِ مشتری ----------
ALTER TABLE shops    ADD COLUMN IF NOT EXISTS cloud_sync boolean NOT NULL DEFAULT false;
ALTER TABLE stations ADD COLUMN IF NOT EXISTS cloud_sync boolean NOT NULL DEFAULT false;

-- ---------- خطاهایی که برنامه‌ها گزارش می‌دهند (SDK: `reportError`) ----------
--  بخشِ ۱۳.۳: «ارسال خطاها». بی این، هر خرابیِ سمتِ مشتری فقط روی
--  کنسولِ خودش می‌ماند و صاحبِ سامانه هیچ‌وقت نمی‌بیندش.
--  ⛔ شناسهٔ کاربر/حساب از توکن می‌آید، نه از بدنهٔ گزارش.
CREATE TABLE IF NOT EXISTS client_errors (
  id          text   PRIMARY KEY,
  app         text   NOT NULL DEFAULT 'shop' CHECK (app IN ('shop','pump')),
  user_id     text   NOT NULL DEFAULT '',
  tenant_id   text   NOT NULL DEFAULT '',
  version     text   NOT NULL DEFAULT '',
  platform    text   NOT NULL DEFAULT '',
  message     text   NOT NULL DEFAULT '',
  stack       text   NOT NULL DEFAULT '',
  context     jsonb  NOT NULL DEFAULT '{}'::jsonb,
  created_at  bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_client_errors ON client_errors(app, created_at DESC);
