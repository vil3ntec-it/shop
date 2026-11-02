-- ============================================================
--  OPS-01@29 (§۲۹.۸): «هشدارهای بحرانی به مسئولِ مربوط برسند» — `lib/ops-paging.js`.
--
--  هشدارِ بحرانی (`ops_alerts`، severity = critical) تا امروز فقط در پنل دیده می‌شد. این‌جا:
--    ops_alert_recipients  مسئول‌ها — فقط مدیرِ پلتفرم (`admins`، کلیدِ بیرونی)؛ نقش و فعال بودن را سرور
--                          هنگامِ نوشتن و هنگامِ فرستادن هر دو می‌سنجد
--    ops_alert_pages       یک ردیف برای هر رخداد (= هر ردیفِ `ops_alerts`): کلیدِ اصلی همان شناسهٔ هشدار ⇒
--                          راه‌اندازیِ دوباره یا نمونهٔ دوم دوباره خبر نمی‌دهد؛ سطحِ پیگیری (escalation) و
--                          «بسته شد» ِ فرستاده‌شده همین‌جاست
--    ops_alert_page_log    هر ارسال به هر گیرنده در هر کانال، با نتیجهٔ واقعی (فقط افزوده می‌شود؛ ۹۰ روز)
--  پشتِ پرچمِ `ops.critical_alerts` (scope ‏`ops`، پیش‌فرض خاموش)؛ خاموش ⇒ این جدول‌ها خالی می‌مانند.
-- ============================================================

CREATE TABLE IF NOT EXISTS ops_alert_recipients (
  admin_id   text    PRIMARY KEY REFERENCES admins(id) ON DELETE CASCADE,
  tier       text    NOT NULL DEFAULT 'primary' CHECK (tier IN ('primary','escalation')),
  email      text    NOT NULL DEFAULT '',
  added_by   text    NOT NULL DEFAULT '',
  added_at   bigint  NOT NULL
);

CREATE TABLE IF NOT EXISTS ops_alert_pages (
  alert_id          text    PRIMARY KEY REFERENCES ops_alerts(id) ON DELETE CASCADE,
  dedup_key         text    NOT NULL,
  state             text    NOT NULL CHECK (state IN ('sent','suppressed','acked','resolved_sent','closed')),
  level             integer NOT NULL DEFAULT 0,
  first_sent_at     bigint,
  last_sent_at      bigint,
  next_at           bigint,
  resolved_sent_at  bigint,
  sends             integer NOT NULL DEFAULT 0,
  last_result       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  created_at        bigint  NOT NULL,
  updated_at        bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_ops_alert_pages_key ON ops_alert_pages(dedup_key, first_sent_at DESC);
CREATE INDEX IF NOT EXISTS ix_ops_alert_pages_state ON ops_alert_pages(state, next_at);

CREATE TABLE IF NOT EXISTS ops_alert_page_log (
  id        text    PRIMARY KEY,
  alert_id  text    NOT NULL,
  kind      text    NOT NULL CHECK (kind IN ('alert','escalation','resolved')),
  level     integer NOT NULL DEFAULT 0,
  admin_id  text    NOT NULL DEFAULT '',
  channel   text    NOT NULL CHECK (channel IN ('email','push')),
  ok        boolean NOT NULL DEFAULT false,
  detail    text    NOT NULL DEFAULT '',
  at        bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_ops_alert_page_log_alert ON ops_alert_page_log(alert_id, at);
CREATE INDEX IF NOT EXISTS ix_ops_alert_page_log_at ON ops_alert_page_log(at);
