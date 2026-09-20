-- ============================================================
--  VILL3N Sync v1 — دفترِ تغییرات (Oplog)، حالِ هر ردیف، تعارض‌ها
--
--  قانونِ طلایی (بخشِ ۲۰ی پرامپت): هرگز کل داده به سرور نمی‌رود، فقط
--  «تغییر». هر تغییرِ کاربر یک ردیفِ کوچک در `oplog` است:
--  «فیلدِ X از ردیفِ Y عوض شد». حذف نرم است و از پنل برمی‌گردد.
--
--  ⚠️ همهٔ جدول‌ها **هم `app` دارند هم حساب** (`account_kind` +
--  `account_id`). یک جدول برای دو بخش است — همان استثنایی که
--  `account_backups` هم دارد — پس هر پرس‌وجویی روی این‌ها **باید**
--  `app` و حساب را با هم شرط کند. بی آن، پمپی با شناسه‌ای برابرِ یک
--  دکان دفترِ او را می‌دید.
--
--  ⚠️ شناسهٔ حساب هیچ‌وقت از درخواست خوانده نمی‌شود: از توکن می‌آید
--  (`req.shopId` / `req.stationId`)، در `routes/sync-v1.js`.
--
--  این با `routes/sync.js` (همگام‌سازیِ رکوردیِ قدیمیِ دکان) یکی نیست
--  و جدول‌هایش هم جدا هستند؛ آن یکی دست‌نخورده کار می‌کند.
-- ============================================================

-- ---------- دفترِ تغییرات ----------
CREATE TABLE IF NOT EXISTS oplog (
  id              text    PRIMARY KEY,
  app             text    NOT NULL,
  account_kind    text    NOT NULL CHECK (account_kind IN ('shop','station')),
  account_id      text    NOT NULL,
  device_id       text    NOT NULL DEFAULT '',
  --  شناسهٔ خودِ برنامه (ULID). یکتا، پس فرستادنِ دوباره «تکراری» است
  --  نه «دو بار اعمال شد».
  op_id           text    NOT NULL UNIQUE,
  --  ترتیبِ رسیدن به سرور — همان چیزی که cursor از آن ساخته می‌شود و
  --  «آخرین زمانِ سرور برنده» با آن داوری می‌شود
  server_seq      bigserial NOT NULL,
  client_ts       bigint  NOT NULL DEFAULT 0,
  table_name      text    NOT NULL,
  row_id          text    NOT NULL,
  op_type         text    NOT NULL CHECK (op_type IN ('insert','update','delete')),
  fields          jsonb   NOT NULL DEFAULT '{}'::jsonb,
  hash            text    NOT NULL DEFAULT '',
  schema_version  integer NOT NULL DEFAULT 1,
  received_at     bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_oplog_account_seq
  ON oplog(app, account_kind, account_id, server_seq);
CREATE INDEX IF NOT EXISTS idx_oplog_row
  ON oplog(app, account_kind, account_id, table_name, row_id);

-- ---------- حالِ فعلیِ هر ردیف ----------
--  از روی oplog ساخته می‌شود؛ برای snapshot و برای داوریِ فیلد به فیلد.
CREATE TABLE IF NOT EXISTS sync_rows (
  app             text    NOT NULL,
  account_kind    text    NOT NULL CHECK (account_kind IN ('shop','station')),
  account_id      text    NOT NULL,
  table_name      text    NOT NULL,
  row_id          text    NOT NULL,
  data            jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  هر فیلد آخرین بار با کدام server_seq نوشته شد — داوریِ تعارض
  field_seq       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  و از کدام دستگاه — تا «تعارض» از «ویرایشِ پشتِ سرِ همِ خودِ یک
  --  دستگاه» جدا شود
  field_src       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  حذفِ نرم. هیچ ردیفی واقعاً پاک نمی‌شود.
  deleted_at      bigint,
  updated_seq     bigint  NOT NULL DEFAULT 0,
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id, table_name, row_id)
);
CREATE INDEX IF NOT EXISTS idx_sync_rows_deleted
  ON sync_rows(app, account_kind, account_id, deleted_at)
  WHERE deleted_at IS NOT NULL;

-- ---------- تعارض‌ها: نسخهٔ بازنده ----------
CREATE TABLE IF NOT EXISTS sync_conflicts (
  id              bigserial PRIMARY KEY,
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  table_name      text    NOT NULL,
  row_id          text    NOT NULL,
  field           text    NOT NULL,
  loser_value     jsonb,
  winner_value    jsonb,
  loser_op_id     text    NOT NULL DEFAULT '',
  winner_op_id    text    NOT NULL DEFAULT '',
  loser_device    text    NOT NULL DEFAULT '',
  winner_device   text    NOT NULL DEFAULT '',
  at              bigint  NOT NULL,
  restored_at     bigint
);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_account
  ON sync_conflicts(app, account_kind, account_id, at DESC);

-- ---------- دستگاه‌ها: تا کجا خوانده‌اند ----------
CREATE TABLE IF NOT EXISTS sync_devices (
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  device_id       text    NOT NULL,
  cursor          bigint  NOT NULL DEFAULT 0,
  last_push_at    bigint,
  last_pull_at    bigint,
  last_op_at      bigint,
  queued_count    integer NOT NULL DEFAULT 0,
  app_version     text    NOT NULL DEFAULT '',
  schema_version  integer NOT NULL DEFAULT 0,
  last_seen_at    bigint  NOT NULL DEFAULT 0,
  PRIMARY KEY (app, account_kind, account_id, device_id)
);

-- ---------- نسخهٔ Schema هر برنامه ----------
--  برنامهٔ جدیدتر از این ⇒ 426؛ قدیمی‌تر ⇒ پذیرفته و Up-migration.
--  مقدار از `lib/sync-v1-migrations.js` می‌آید و سرِ بالا آمدن نوشته
--  می‌شود؛ این‌جا فقط جای نگه داشتنش است.
CREATE TABLE IF NOT EXISTS app_schema (
  app             text    PRIMARY KEY,
  schema_version  integer NOT NULL DEFAULT 1,
  updated_at      bigint  NOT NULL DEFAULT 0
);

-- ---------- گزارشِ خطای برنامه‌ها ----------
CREATE TABLE IF NOT EXISTS client_errors (
  id              bigserial PRIMARY KEY,
  app             text    NOT NULL,
  account_kind    text    NOT NULL DEFAULT '',
  account_id      text    NOT NULL DEFAULT '',
  device_id       text    NOT NULL DEFAULT '',
  app_version     text    NOT NULL DEFAULT '',
  schema_version  integer NOT NULL DEFAULT 0,
  message         text    NOT NULL DEFAULT '',
  stack           text    NOT NULL DEFAULT '',
  log_tail        text    NOT NULL DEFAULT '',
  at              bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_client_errors_at ON client_errors(app, at DESC);
