-- ============================================================
--  ۸۱۰ — اجازهٔ موقتِ دسترسیِ پشتیبانی به دادهٔ یک دکان (G1 ِ ADMIN-CONTROL-PLANE §۸.۱؛ تصمیمِ ۲-۹ ِ ۱۴۰۵/۰۸/۲۰:
--         ACP-D1 الف و ACP-D2 الف). همان جدولی که طرح برایش شمارهٔ «۰۷۹» را رزرو کرده بود؛ آن شماره دیگر آزاد نیست.
--
--  هر ردیف یک اجازه است، نه یک نشست:
--    consent='tenant'      پشتیبانی می‌خواهد ⇒ `pending`؛ صاحبِ دکان در برنامه‌اش می‌پذیرد (`active`، از همان دم تا
--                          `minutes`) یا رد می‌کند (`denied`). درخواستِ پذیرفته‌نشده پس از ۲۴ ساعت خودش «گذشته» است.
--    consent='break_glass' بی رضایتِ دکان، فقط مالکِ پلتفرم با دلیلِ ۱۰ تا ۳۰۰ نویسه و خبرِ همان دمِ صاحبِ دکان
--                          (`notified_at`) ⇒ همان دم `active`.
--  «گذشته» (`expired`) ستون نیست: از ساعتِ **سرور** و `expires_at` خوانده می‌شود (`lib/support-access.js`).
--
--  ⛔ هرگز همیشگی: مدت ۵ تا ۱۴۴۰ دقیقه (سقفِ ۲۴ ساعت؛ تمدید = اجازهٔ تازه با دلیلِ تازه)، و اجازهٔ فعال بی پایان نیست.
--  ⛔ اجازه به **همان** درخواست‌کننده بسته است (`admin_id` و «انجام‌دهنده»ٔ پل `actor_id`)، نه به هر مدیری.
--  ⛔ فقط بخشِ دکان (`app='shop'`)؛ با رفتنِ دکان می‌رود. سابقهٔ هر کار در `audit_logs` می‌ماند.
--  فقط افزودنی. rollback: migrations/rollback/810_admin_access_grants.down.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS admin_access_grants (
  id            text    PRIMARY KEY,
  app           text    NOT NULL CHECK (app = 'shop'),
  tenant_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  درخواست‌کننده: شناسهٔ مدیر یا کلیدِ سرویسِ پل، و «انجام‌دهنده»ٔ پنل (‎panel:<n>‎) یا ''
  admin_id      text    NOT NULL,
  actor_id      text    NOT NULL DEFAULT '',
  requested_by  text    NOT NULL DEFAULT '',
  scope         text    NOT NULL CHECK (scope IN ('read_private', 'export', 'restore', 'view_as')),
  consent       text    NOT NULL CHECK (consent IN ('tenant', 'break_glass')),
  status        text    NOT NULL CHECK (status IN ('pending', 'active', 'denied', 'revoked')),
  reason        text    NOT NULL CHECK (length(reason) BETWEEN 3 AND 300),
  ticket_id     text    NOT NULL DEFAULT '',
  minutes       integer NOT NULL CHECK (minutes BETWEEN 5 AND 1440),
  --  پذیرنده: کاربرِ صاحبِ دکان (رضایت) یا همان مدیر (شکستنِ شیشه)
  granted_by    text    NOT NULL DEFAULT '',
  created_at    bigint  NOT NULL,
  decided_at    bigint,
  starts_at     bigint,
  expires_at    bigint,
  revoked_at    bigint,
  revoked_by    text    NOT NULL DEFAULT '',
  revoke_reason text    NOT NULL DEFAULT '',
  notified_at   bigint,
  uses          integer NOT NULL DEFAULT 0 CHECK (uses >= 0),
  CHECK (consent = 'tenant' OR length(reason) >= 10),
  CHECK ((starts_at IS NULL) = (expires_at IS NULL)),
  CHECK (expires_at IS NULL OR (expires_at > starts_at AND expires_at - starts_at <= 86400000)),
  CHECK (status <> 'active' OR expires_at IS NOT NULL),
  CHECK ((status = 'revoked') = (revoked_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_admin_access_grants_tenant ON admin_access_grants(app, tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_admin_access_grants_live ON admin_access_grants(app, tenant_id, admin_id, scope)
  WHERE status = 'active';
