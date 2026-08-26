-- ============================================================
--  هسته: کاربران، مدیران، دکان‌ها، اعضا، کدهای شاگرد، اشتراک‌ها
--  همه‌ی زمان‌ها epoch میلی‌ثانیه (UTC) هستند تا وابسته به منطقه‌ی
--  زمانی سرور نباشند و انتقال دیتابیس بین سرورها بی‌خطر بماند.
-- ============================================================

-- ---------- کاربران ----------
CREATE TABLE users (
  id             text PRIMARY KEY,
  name           text        NOT NULL DEFAULT '',
  email          text        UNIQUE,          -- به شکل حروف کوچک ذخیره می‌شود
  phone          text        UNIQUE,          -- به شکل +93… نرمال می‌شود
  password_hash  text,                        -- ممکن است خالی باشد (ورود با کد یا گوگل)
  status         text        NOT NULL DEFAULT 'active'
                 CHECK (status IN ('active','disabled')),
  created_at     bigint      NOT NULL,
  updated_at     bigint      NOT NULL,
  last_login_at  bigint,
  CONSTRAINT users_need_identifier CHECK (email IS NOT NULL OR phone IS NOT NULL)
);
CREATE INDEX idx_users_created ON users(created_at DESC);

-- ---------- روش‌های ورود بیرونی (گوگل و …) ----------
CREATE TABLE user_identities (
  id          text PRIMARY KEY,
  user_id     text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider    text   NOT NULL,               -- google | phone | email
  subject     text   NOT NULL,               -- شناسه‌ی یکتا نزد ارائه‌دهنده
  email       text   NOT NULL DEFAULT '',
  created_at  bigint NOT NULL,
  UNIQUE (provider, subject)
);
CREATE INDEX idx_identities_user ON user_identities(user_id);

-- ---------- مدیران سامانه ----------
CREATE TABLE admins (
  id             text PRIMARY KEY,
  username       text   NOT NULL UNIQUE,
  name           text   NOT NULL DEFAULT '',
  password_hash  text   NOT NULL,
  role           text   NOT NULL DEFAULT 'admin' CHECK (role IN ('admin','superadmin')),
  status         text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled')),
  created_at     bigint NOT NULL,
  last_login_at  bigint
);

-- ---------- دکان‌ها ----------
CREATE TABLE shops (
  id             text PRIMARY KEY,
  owner_user_id  text   NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  name           text   NOT NULL DEFAULT '',
  status         text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','disabled')),
  max_members    integer NOT NULL DEFAULT 10,
  created_at     bigint NOT NULL,
  updated_at     bigint NOT NULL
);
CREATE INDEX idx_shops_owner ON shops(owner_user_id);

-- ---------- اعضای دکان ----------
CREATE TABLE shop_members (
  id          text PRIMARY KEY,
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id     text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role        text   NOT NULL DEFAULT 'staff' CHECK (role IN ('owner','manager','staff')),
  status      text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','suspended','removed')),
  created_at  bigint NOT NULL,
  updated_at  bigint NOT NULL,
  UNIQUE (shop_id, user_id)
);
CREATE INDEX idx_members_user ON shop_members(user_id, status);

-- ---------- کدهای شاگرد ----------
-- خود کد هرگز ذخیره نمی‌شود؛ فقط هش آن. اگر دیتابیس لو برود،
-- نمی‌توان با آن به دکانی وارد شد.
CREATE TABLE staff_codes (
  id          text PRIMARY KEY,
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  code_hash   text   NOT NULL UNIQUE,
  code_hint   text   NOT NULL DEFAULT '',    -- چند رقم آخر، فقط برای نمایش به صاحب دکان
  role        text   NOT NULL DEFAULT 'staff' CHECK (role IN ('manager','staff')),
  created_by  text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  bigint NOT NULL,
  expires_at  bigint,                        -- NULL = بدون انقضا
  max_uses    integer NOT NULL DEFAULT 1,    -- 0 = نامحدود
  used_count  integer NOT NULL DEFAULT 0,
  status      text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked','exhausted'))
);
CREATE INDEX idx_staff_codes_shop ON staff_codes(shop_id, status);

CREATE TABLE staff_code_uses (
  id            text PRIMARY KEY,
  staff_code_id text   NOT NULL REFERENCES staff_codes(id) ON DELETE CASCADE,
  user_id       text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  used_at       bigint NOT NULL,
  ip            text   NOT NULL DEFAULT ''
);

-- ---------- اشتراک‌ها (به دکان وابسته‌اند، نه به یک نفر) ----------
CREATE TABLE subscriptions (
  id           text PRIMARY KEY,
  shop_id      text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  plan         text   NOT NULL DEFAULT 'custom',
  status       text   NOT NULL DEFAULT 'active'
               CHECK (status IN ('active','suspended','cancelled','expired','pending')),
  starts_at    bigint NOT NULL,
  ends_at      bigint NOT NULL,
  features     jsonb  NOT NULL DEFAULT '[]'::jsonb,
  max_devices  integer NOT NULL DEFAULT 10,
  grace_days   integer NOT NULL DEFAULT 0,
  note         text   NOT NULL DEFAULT '',
  created_at   bigint NOT NULL,
  updated_at   bigint NOT NULL,
  created_by   text   NOT NULL DEFAULT ''
);
CREATE INDEX idx_subs_shop ON subscriptions(shop_id, status);
-- هر دکان فقط یک اشتراک زنده دارد
CREATE UNIQUE INDEX idx_subs_one_live ON subscriptions(shop_id)
  WHERE status IN ('active','suspended','pending');

-- ---------- دستگاه‌ها ----------
CREATE TABLE devices (
  id            text PRIMARY KEY,
  user_id       text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_uid    text   NOT NULL,
  name          text   NOT NULL DEFAULT '',
  platform      text   NOT NULL DEFAULT '',
  status        text   NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked')),
  created_at    bigint NOT NULL,
  last_seen_at  bigint,
  last_sync_at  bigint,
  last_ip       text   NOT NULL DEFAULT '',
  UNIQUE (user_id, device_uid)
);

-- ---------- توکن‌های نشست ----------
CREATE TABLE tokens (
  token_hash  text PRIMARY KEY,
  kind        text   NOT NULL CHECK (kind IN ('access','refresh','admin')),
  subject_id  text   NOT NULL,
  device_id   text,
  issued_at   bigint NOT NULL,
  expires_at  bigint NOT NULL,
  revoked_at  bigint
);
CREATE INDEX idx_tokens_subject ON tokens(subject_id, kind);
CREATE INDEX idx_tokens_expiry ON tokens(expires_at);

-- ---------- کدهای یک‌بارمصرف ورود ----------
CREATE TABLE otp_codes (
  id            text PRIMARY KEY,
  purpose       text   NOT NULL DEFAULT 'login',
  destination   text   NOT NULL,             -- شماره یا ایمیل نرمال‌شده
  code_hash     text   NOT NULL,
  attempts      integer NOT NULL DEFAULT 0,
  max_attempts  integer NOT NULL DEFAULT 5,
  expires_at    bigint NOT NULL,
  consumed_at   bigint,
  created_at    bigint NOT NULL,
  ip            text   NOT NULL DEFAULT ''
);
CREATE INDEX idx_otp_dest ON otp_codes(destination, created_at DESC);

-- ---------- تلاش‌های ورود ----------
CREATE TABLE login_attempts (
  id          bigserial PRIMARY KEY,
  scope       text   NOT NULL,
  identifier  text   NOT NULL,
  ip          text   NOT NULL DEFAULT '',
  ok          boolean NOT NULL DEFAULT false,
  created_at  bigint NOT NULL
);
CREATE INDEX idx_attempts ON login_attempts(scope, identifier, created_at DESC);

-- ---------- سابقه‌ی عملیات ----------
CREATE TABLE audit_logs (
  id           text PRIMARY KEY,
  shop_id      text   NOT NULL DEFAULT '',
  actor_type   text   NOT NULL DEFAULT 'user',   -- user | admin | system
  user_id      text   NOT NULL DEFAULT '',
  action       text   NOT NULL,
  target_type  text   NOT NULL DEFAULT '',
  target_id    text   NOT NULL DEFAULT '',
  detail       jsonb  NOT NULL DEFAULT '{}'::jsonb,
  ip           text   NOT NULL DEFAULT '',
  created_at   bigint NOT NULL
);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_shop ON audit_logs(shop_id, created_at DESC);

-- ---------- پلن‌ها و تنظیمات سراسری ----------
CREATE TABLE plans (
  id           text PRIMARY KEY,
  code         text   NOT NULL UNIQUE,
  title        text   NOT NULL,
  amount       integer,
  unit         text,
  price_afn    integer NOT NULL DEFAULT 0,
  negotiable   boolean NOT NULL DEFAULT false,
  features     jsonb  NOT NULL DEFAULT '[]'::jsonb,
  max_devices  integer NOT NULL DEFAULT 10,
  badge        text   NOT NULL DEFAULT '',
  sort_order   integer NOT NULL DEFAULT 0,
  active       boolean NOT NULL DEFAULT true,
  created_at   bigint NOT NULL,
  updated_at   bigint NOT NULL
);

CREATE TABLE app_config (
  key         text PRIMARY KEY,
  value       text   NOT NULL,
  updated_at  bigint NOT NULL
);

CREATE TABLE purchase_requests (
  id          text PRIMARY KEY,
  shop_id     text   NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  user_id     text   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan_code   text   NOT NULL,
  note        text   NOT NULL DEFAULT '',
  status      text   NOT NULL DEFAULT 'pending'
              CHECK (status IN ('pending','approved','rejected')),
  created_at  bigint NOT NULL,
  handled_at  bigint,
  handled_by  text
);
CREATE INDEX idx_preq_status ON purchase_requests(status, created_at DESC);
