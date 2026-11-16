-- ============================================================
--  960 — «از پشتیبانی بخواه»: درخواستِ ساخت‌یافتهٔ پشتیبانی با یک لمس، و «درستش کن» ِ یک‌کلیکیِ مدیر
--  (`lib/support-requests.js`، فهرستِ مشکل‌ها `lib/support-problems.json`، درها `routes/support-requests.js` و
--  `routes/admin-support-requests.js`).
--
--  support_requests — هر درخواست یک ردیف، کنارِ همان رشتهٔ پشتیبانیِ همیشگی (چتِ دوم ساخته نشد):
--    problem/param — کدِ پایه (`flag_off`، `subscription_expired`، …) و پارامترِ مجازِ همان (نامِ پرچم یا کلیدِ قابلیت).
--    scope_key     — دکان (`shop:<id>`) یا، برای مهمانِ بی‌دکان، خودِ رشته (`thr:<id>`).
--    context       — فقط واقعیت‌های فهرست‌شده (نسخهٔ برنامه، سکو، صفحه، کدِ کوتاهِ همگام‌سازی، شمار)؛ هیچ راز، کارت یا دادهٔ شخصی.
--    ⛔ یکتایی «تا باز است»: یک درخواستِ باز برای هر (scope_key، problem، param) — لمسِ دوباره همان ردیف را برمی‌گرداند.
--  support_request_fixes — هر «درستش کن» یک بار برای هر درخواست (UNIQUE)، با پیش ⇒ پس و انجام‌دهنده.
--  ⛔ رشته که پاک شود (حذفِ حساب، `lib/user-delete.js`) درخواست‌ها و کارهایش هم می‌روند (ON DELETE CASCADE).
--  بازگشت: `rollback/960_support_requests.down.sql`.
-- ============================================================
CREATE TABLE IF NOT EXISTS support_requests (
  id           text PRIMARY KEY,
  thread_id    text NOT NULL REFERENCES support_threads(id) ON DELETE CASCADE,
  shop_id      text NOT NULL DEFAULT '',
  user_id      text NOT NULL DEFAULT '',
  device_uid   text NOT NULL DEFAULT '',
  scope_key    text NOT NULL,
  problem      text NOT NULL CHECK (problem ~ '^[a-z][a-z0-9_]{1,40}$'),
  param        text NOT NULL DEFAULT '' CHECK (param ~ '^[a-z0-9_.]{0,64}$'),
  lang         text NOT NULL DEFAULT 'fa' CHECK (lang IN ('fa', 'en', 'ps')),
  context      jsonb NOT NULL DEFAULT '{}'::jsonb,
  message_id   text NOT NULL DEFAULT '',
  status       text NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'resolved')),
  resolved_at  bigint,
  resolved_by  text NOT NULL DEFAULT '',
  created_at   bigint NOT NULL,
  updated_at   bigint NOT NULL,
  CHECK ((status = 'resolved') = (resolved_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_support_requests_open ON support_requests(scope_key, problem, param) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS ix_support_requests_list ON support_requests(status, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_support_requests_shop ON support_requests(shop_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_support_requests_thread ON support_requests(thread_id, created_at DESC);

CREATE TABLE IF NOT EXISTS support_request_fixes (
  id          text PRIMARY KEY,
  request_id  text NOT NULL REFERENCES support_requests(id) ON DELETE CASCADE,
  action      text NOT NULL CHECK (action ~ '^[a-z][a-z0-9_]{1,40}$'),
  actor       text NOT NULL DEFAULT '',
  before      jsonb NOT NULL DEFAULT '{}'::jsonb,
  after       jsonb NOT NULL DEFAULT '{}'::jsonb,
  result      text NOT NULL DEFAULT 'done',
  created_at  bigint NOT NULL,
  UNIQUE (request_id, action)
);
