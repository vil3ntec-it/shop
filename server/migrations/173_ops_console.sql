-- پنلِ مرکزیِ مالک، ماژول‌های ۴.۹ تا ۴.۱۱ (console-ops-api): خطاهای سرور، مرکزِ رخداد و هشدارها.
--
--   server_errors        هر خطای ۵xx ِ خودِ سرورِ حساب، یک ردیف (شناسه = همان «کدِ پیگیری» ِ پاسخ) —
--                        پاک‌شده از راز (`lib/ops-errors.js`)؛ تا امروز فقط در لاگِ کنسول بود.
--   ops_incidents        یک ردیف برای هر «اثرِ انگشت» (خطای تکراری = یک رخداد با شمارنده)، از
--                        `client_errors` (برنامه‌ها، گزارشِ خرابیِ OPS-03) و `server_errors`.
--   ops_incident_events  تاریخچهٔ هر رخداد: پیدایش، تغییرِ وضعیت/اولویت، یادداشتِ داخلی، پیوندِ
--                        پشتیبانی، بازگشایی. ⛔ فقط افزوده می‌شود؛ هیچ درِ ویرایش/پاک کردن ندارد.
--   ops_alerts           هشدارِ زنده یا بسته؛ از هر (قاعده، هدف) در هر لحظه فقط **یک** ردیفِ زنده
--                        (ux_ops_alerts_live) ⇒ تکرار شمارنده را بالا می‌برد، ردیفِ تازه نمی‌سازد.
--   ops_cursors          جای خواندنِ `client_errors`/`server_errors` برای ساختنِ رخدادها.
--   client_errors.fingerprint  همان اثرِ انگشت، تا فهرستِ «وقوع»های هر رخداد با نمایه خوانده شود.
--
-- ⛔ هیچ راز، توکن، ایمیل یا شماره در این جدول‌ها نیست (پیام‌ها پیش از نوشتن پوشانده می‌شوند).
-- فقط افزودنی. برگشت: DROP TABLE ops_alerts, ops_incident_events, ops_incidents, ops_cursors, server_errors;
-- ALTER TABLE client_errors DROP COLUMN fingerprint.
CREATE TABLE IF NOT EXISTS server_errors (
  id           text    PRIMARY KEY,
  fingerprint  text    NOT NULL,
  method       text    NOT NULL DEFAULT '',
  route        text    NOT NULL DEFAULT '',
  status       integer NOT NULL,
  code         text    NOT NULL DEFAULT '',
  name         text    NOT NULL DEFAULT '',
  message      text    NOT NULL DEFAULT '',
  step         text    NOT NULL DEFAULT '',
  location     text    NOT NULL DEFAULT '',
  server_version text  NOT NULL DEFAULT '',
  at           bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_server_errors_at ON server_errors(at, id);
CREATE INDEX IF NOT EXISTS ix_server_errors_fp ON server_errors(fingerprint, at DESC);

ALTER TABLE client_errors ADD COLUMN IF NOT EXISTS fingerprint text NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS ix_client_errors_at_id ON client_errors(at, id);
CREATE INDEX IF NOT EXISTS ix_client_errors_fp ON client_errors(fingerprint, at DESC);

CREATE TABLE IF NOT EXISTS ops_cursors (
  name   text    PRIMARY KEY,
  at     bigint  NOT NULL DEFAULT 0,
  last_id text   NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS ops_incidents (
  id                 text    PRIMARY KEY,
  fingerprint        text    NOT NULL UNIQUE,
  source             text    NOT NULL CHECK (source IN ('server','client')),
  app                text    NOT NULL DEFAULT '',
  title              text    NOT NULL,
  kind               text    NOT NULL DEFAULT '',
  status             text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','investigating','resolved')),
  priority           text    NOT NULL DEFAULT 'p3' CHECK (priority IN ('p1','p2','p3','p4')),
  first_seen_at      bigint  NOT NULL,
  last_seen_at       bigint  NOT NULL,
  occurrences        integer NOT NULL DEFAULT 0,
  last_version       text    NOT NULL DEFAULT '',
  support_thread_id  text    NOT NULL DEFAULT '',
  resolved_at        bigint,
  resolved_by        text    NOT NULL DEFAULT '',
  version            integer NOT NULL DEFAULT 1,
  created_at         bigint  NOT NULL,
  updated_at         bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_ops_incidents_last ON ops_incidents(last_seen_at DESC);
CREATE INDEX IF NOT EXISTS ix_ops_incidents_status ON ops_incidents(status, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS ops_incident_events (
  id           text    PRIMARY KEY,
  incident_id  text    NOT NULL REFERENCES ops_incidents(id) ON DELETE CASCADE,
  kind         text    NOT NULL CHECK (kind IN ('created','status','priority','note','link','reopened')),
  from_value   text    NOT NULL DEFAULT '',
  to_value     text    NOT NULL DEFAULT '',
  note         text    NOT NULL DEFAULT '',
  result       text    NOT NULL DEFAULT 'ok',
  actor        text    NOT NULL DEFAULT '',
  actor_detail jsonb   NOT NULL DEFAULT '{}'::jsonb,
  at           bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_ops_incident_events ON ops_incident_events(incident_id, at DESC);

CREATE TABLE IF NOT EXISTS ops_alerts (
  id               text    PRIMARY KEY,
  rule             text    NOT NULL,
  target           text    NOT NULL DEFAULT '',
  dedup_key        text    NOT NULL,
  severity         text    NOT NULL CHECK (severity IN ('critical','high','medium','low')),
  status           text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','acknowledged','resolved')),
  title            text    NOT NULL,
  detail           jsonb   NOT NULL DEFAULT '{}'::jsonb,
  first_seen_at    bigint  NOT NULL,
  last_seen_at     bigint  NOT NULL,
  live_since       bigint  NOT NULL,
  occurrences      integer NOT NULL DEFAULT 1,
  acknowledged_at  bigint,
  acknowledged_by  text    NOT NULL DEFAULT '',
  ack_note         text    NOT NULL DEFAULT '',
  resolved_at      bigint,
  resolved_by      text    NOT NULL DEFAULT '',
  resolution       text    NOT NULL DEFAULT '' CHECK (resolution IN ('','auto','manual')),
  resolve_reason   text    NOT NULL DEFAULT '',
  version          integer NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ops_alerts_live ON ops_alerts(dedup_key) WHERE status <> 'resolved';
CREATE INDEX IF NOT EXISTS ix_ops_alerts_status ON ops_alerts(status, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS ix_ops_alerts_key ON ops_alerts(dedup_key, resolved_at DESC);
CREATE INDEX IF NOT EXISTS ix_ops_alerts_live_since ON ops_alerts(live_since);
