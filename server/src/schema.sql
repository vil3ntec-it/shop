-- ============================================================
-- توحید | سرور اشتراک و License  —  اسکیمای دیتابیس
-- همه‌ی زمان‌ها epoch میلی‌ثانیه (UTC) هستند.
-- ============================================================

PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- ---------- کاربران ----------
CREATE TABLE IF NOT EXISTS users (
  id             TEXT PRIMARY KEY,
  email          TEXT UNIQUE,                -- حداقل یکی از email/phone باید پر باشد
  phone          TEXT UNIQUE,
  name           TEXT NOT NULL DEFAULT '',
  password_hash  TEXT NOT NULL,
  status         TEXT NOT NULL DEFAULT 'active'   -- active | disabled
                 CHECK (status IN ('active','disabled')),
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL,
  last_login_at  INTEGER
);
CREATE INDEX IF NOT EXISTS idx_users_created ON users(created_at DESC);

-- ---------- مدیران ----------
CREATE TABLE IF NOT EXISTS admins (
  id             TEXT PRIMARY KEY,
  username       TEXT NOT NULL UNIQUE,
  name           TEXT NOT NULL DEFAULT '',
  password_hash  TEXT NOT NULL,
  role           TEXT NOT NULL DEFAULT 'admin'    -- admin | superadmin
                 CHECK (role IN ('admin','superadmin')),
  status         TEXT NOT NULL DEFAULT 'active'
                 CHECK (status IN ('active','disabled')),
  created_at     INTEGER NOT NULL,
  last_login_at  INTEGER
);

-- ---------- اشتراک‌ها ----------
-- هر کاربر حداکثر یک اشتراک فعال دارد؛ تاریخچه با status نگه داشته می‌شود.
CREATE TABLE IF NOT EXISTS subscriptions (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan              TEXT NOT NULL DEFAULT 'custom',
  status            TEXT NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active','suspended','cancelled','expired')),
  starts_at         INTEGER NOT NULL,
  ends_at           INTEGER NOT NULL,
  timezone          TEXT NOT NULL DEFAULT 'Asia/Kabul',
  features          TEXT NOT NULL DEFAULT '[]',   -- JSON array از کلید قابلیت‌ها
  max_devices       INTEGER NOT NULL DEFAULT 1,
  grace_days        INTEGER NOT NULL DEFAULT 0,   -- مهلت پس از پایان، پیش از قفل شدن
  license_ttl_days  INTEGER,                      -- NULL = License تا پایان اشتراک معتبر است
  note              TEXT NOT NULL DEFAULT '',
  created_at        INTEGER NOT NULL,
  updated_at        INTEGER NOT NULL,
  created_by        TEXT                          -- admin id
);
CREATE INDEX IF NOT EXISTS idx_subs_user ON subscriptions(user_id);
-- هر کاربر فقط یک اشتراک زنده (active/suspended) داشته باشد
CREATE UNIQUE INDEX IF NOT EXISTS idx_subs_one_live
  ON subscriptions(user_id) WHERE status IN ('active','suspended');

-- ---------- دستگاه‌ها ----------
CREATE TABLE IF NOT EXISTS devices (
  id                TEXT PRIMARY KEY,
  user_id           TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_uid        TEXT NOT NULL,               -- شناسه‌ای که خود برنامه می‌سازد
  name              TEXT NOT NULL DEFAULT '',
  platform          TEXT NOT NULL DEFAULT '',
  fingerprint_hash  TEXT NOT NULL DEFAULT '',
  status            TEXT NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active','revoked')),
  created_at        INTEGER NOT NULL,
  last_seen_at      INTEGER,
  last_sync_at      INTEGER,
  last_ip           TEXT NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_devices_user_uid ON devices(user_id, device_uid);
CREATE INDEX IF NOT EXISTS idx_devices_user ON devices(user_id);

-- ---------- Licenseهای صادرشده ----------
CREATE TABLE IF NOT EXISTS licenses (
  id               TEXT PRIMARY KEY,
  user_id          TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id        TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  subscription_id  TEXT REFERENCES subscriptions(id) ON DELETE SET NULL,
  version          INTEGER NOT NULL DEFAULT 1,
  key_id           TEXT NOT NULL,
  features         TEXT NOT NULL DEFAULT '[]',
  starts_at        INTEGER NOT NULL,
  ends_at          INTEGER NOT NULL,             -- پایان اعتبار خودِ License
  sub_ends_at      INTEGER NOT NULL,             -- پایان اشتراک (برای نمایش)
  issued_at        INTEGER NOT NULL,
  status           TEXT NOT NULL DEFAULT 'active'
                   CHECK (status IN ('active','revoked','superseded')),
  revoked_at       INTEGER,
  revoked_reason   TEXT NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_lic_user ON licenses(user_id);
CREATE INDEX IF NOT EXISTS idx_lic_device ON licenses(device_id);

-- ---------- توکن‌های نشست ----------
-- فقط هش توکن ذخیره می‌شود؛ خود توکن هرگز در دیتابیس نیست.
CREATE TABLE IF NOT EXISTS tokens (
  token_hash   TEXT PRIMARY KEY,
  kind         TEXT NOT NULL CHECK (kind IN ('access','refresh','admin')),
  subject_id   TEXT NOT NULL,                    -- user id یا admin id
  device_id    TEXT,
  issued_at    INTEGER NOT NULL,
  expires_at   INTEGER NOT NULL,
  revoked_at   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_tokens_subject ON tokens(subject_id, kind);
CREATE INDEX IF NOT EXISTS idx_tokens_expiry ON tokens(expires_at);

-- ---------- سابقه عملیات مدیر ----------
CREATE TABLE IF NOT EXISTS audit_log (
  id          TEXT PRIMARY KEY,
  actor_type  TEXT NOT NULL,                     -- admin | user | system
  actor_id    TEXT NOT NULL DEFAULT '',
  action      TEXT NOT NULL,
  target_type TEXT NOT NULL DEFAULT '',
  target_id   TEXT NOT NULL DEFAULT '',
  detail      TEXT NOT NULL DEFAULT '',          -- JSON
  ip          TEXT NOT NULL DEFAULT '',
  created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_target ON audit_log(target_type, target_id);

-- ---------- تلاش‌های ناموفق ورود ----------
CREATE TABLE IF NOT EXISTS login_attempts (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  scope       TEXT NOT NULL,                     -- user | admin
  identifier  TEXT NOT NULL,
  ip          TEXT NOT NULL DEFAULT '',
  ok          INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_attempts ON login_attempts(scope, identifier, created_at DESC);
