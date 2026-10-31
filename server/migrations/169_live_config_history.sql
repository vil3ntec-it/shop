-- فاز ۳ ِ پنلِ مرکزی (console-phase3): تاریخچهٔ «تنظیماتِ زنده» و پرچم‌ها (`live_config`).
--
-- هر نوشتن و پاک کردن (از هر دری: `/api/admin/flags`، `/api/admin/pump/live-config`، کانالِ آزمایشیِ
-- `/api/admin/release/beta` و کلیدهای یک‌بارهٔ خودِ سرور) در **همان تراکنش** یک ردیف می‌گذارد:
-- چه کسی، کی، مقدارِ پیشین ⇒ تازه، و دلیل. `old_value`/`new_value` همان متنِ JSONِ ستونِ `value`اند؛
-- NULL یعنی «نبود» (کلیدِ تازه) یا «پاک شد».
--
-- فقط افزودنی. برگشت: DROP TABLE live_config_history.
CREATE TABLE IF NOT EXISTS live_config_history (
  id          text    PRIMARY KEY,
  scope       text    NOT NULL,
  key         text    NOT NULL,
  action      text    NOT NULL CHECK (action IN ('set','remove','set_once')),
  old_value   text,
  new_value   text,
  changed_by  text    NOT NULL DEFAULT '',
  actor       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  reason      text    NOT NULL DEFAULT '',
  created_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_live_config_history_key ON live_config_history(scope, key, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_live_config_history_at ON live_config_history(created_at DESC);
