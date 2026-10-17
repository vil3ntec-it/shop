-- ============================================================
--  Sync v3 — سمتِ سرور، فازِ ۱ (بخشِ ۳.۸ و SYN-01..08، 10، 11، 13)
--
--  فقط `app=shop`. جدول‌های v1 (`oplog`، `sync_rows`، …) دست نمی‌خورند؛
--  v3 دفترِ خودش را دارد و پلِ v1⇒v3 فقط از `oplog` **می‌خواند**.
--  تا `SYNC_V3_ENABLED` روشن نشود، هیچ مسیری به این جدول‌ها نمی‌رسد.
-- ============================================================

-- ---------- دفترِ opها ----------
CREATE TABLE IF NOT EXISTS sync_v3_ops (
  app             text    NOT NULL,
  account_kind    text    NOT NULL CHECK (account_kind IN ('shop','station')),
  account_id      text    NOT NULL,
  --  ULIDِ برنامه؛ یکتا در هر حساب ⇒ فرستادنِ دوباره «تکراری» است
  op_id           text    NOT NULL,
  --  ترتیبِ رسیدن — فقط برای cursor؛ داوری با HLC است
  server_seq      bigserial NOT NULL,
  device_id       text    NOT NULL DEFAULT '',
  --  v3 = برنامهٔ تازه، v2 = صفِ قدیمِ همان برنامه، v1 = پلِ v1، server = خودِ سرور
  origin          text    NOT NULL DEFAULT 'v3' CHECK (origin IN ('v3','v2','v1','server')),
  tx_id           text    NOT NULL DEFAULT '',
  --  HLCِ مؤثر (برای opِ «ساعت‌کج» همان HLCِ سرور هنگامِ رسیدن)
  hlc             text    NOT NULL,
  --  HLC همان‌طور که دستگاه فرستاد
  client_hlc      text    NOT NULL DEFAULT '',
  skewed          boolean NOT NULL DEFAULT false,
  table_name      text    NOT NULL,
  row_id          text    NOT NULL,
  kind            text    NOT NULL CHECK (kind IN ('insert','update','delete','delta','cancel')),
  fields          jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  آنچه واقعاً نشست: {set, inc, deleted, revived}؛ null ⇒ هیچ (به صندوق رفت)
  effect          jsonb,
  status          text    NOT NULL CHECK (status IN ('applied','partial','conflict')),
  received_at     bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id, op_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_v3_ops_seq ON sync_v3_ops(server_seq);
CREATE INDEX IF NOT EXISTS idx_sync_v3_ops_account_seq
  ON sync_v3_ops(app, account_kind, account_id, server_seq);

-- ---------- حالِ هر ردیف ----------
CREATE TABLE IF NOT EXISTS sync_v3_rows (
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  table_name      text    NOT NULL,
  row_id          text    NOT NULL,
  data            jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  HLCِ برندهٔ هر فیلد — «آخرین نوشتن» با همین داوری می‌شود
  field_hlc       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  نویسندهٔ هر فیلد {d: دستگاه، s: server_seq، o: op_id}
  field_src       jsonb   NOT NULL DEFAULT '{}'::jsonb,
  --  HLCِ درج (اسنادِ فقط‌افزودنی و حرکتِ کالا: «جمعِ حرکت‌ها تا T»)
  row_hlc         text    NOT NULL DEFAULT '',
  inserted        boolean NOT NULL DEFAULT false,
  created_by      text    NOT NULL DEFAULT '',
  deleted_hlc     text,
  deleted_at      bigint,
  --  کلیدِ «تکراری» (شمارهٔ تلفنِ یکدست‌شدهٔ مشتری)
  match_key       text    NOT NULL DEFAULT '',
  updated_seq     bigint  NOT NULL DEFAULT 0,
  created_at      bigint  NOT NULL,
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id, table_name, row_id)
);
CREATE INDEX IF NOT EXISTS idx_sync_v3_rows_match
  ON sync_v3_rows(app, account_kind, account_id, table_name, match_key) WHERE match_key <> '';

-- ---------- صندوقِ تعارض و وظیفه‌های بررسی ----------
CREATE TABLE IF NOT EXISTS sync_v3_conflicts (
  id              text    PRIMARY KEY,
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  --  lww · clock_skew · stale_after_delete · stale_delete · append_only_edit
  --  · negative_stock · duplicate_barcode · duplicate_customer
  kind            text    NOT NULL,
  table_name      text    NOT NULL,
  row_id          text    NOT NULL,
  field           text    NOT NULL DEFAULT '',
  winner_value    jsonb,
  loser_value     jsonb,
  winner_hlc      text    NOT NULL DEFAULT '',
  loser_hlc       text    NOT NULL DEFAULT '',
  winner_op_id    text    NOT NULL DEFAULT '',
  loser_op_id     text    NOT NULL DEFAULT '',
  winner_device   text    NOT NULL DEFAULT '',
  loser_device    text    NOT NULL DEFAULT '',
  detail          jsonb   NOT NULL DEFAULT '{}'::jsonb,
  status          text    NOT NULL DEFAULT 'open' CHECK (status IN ('open','kept','reverted','dismissed')),
  --  وظیفه‌ها: برای هر کلید فقط یکی باز
  dedupe_key      text    NOT NULL DEFAULT '',
  at              bigint  NOT NULL,
  resolved_at     bigint,
  resolved_by     text    NOT NULL DEFAULT '',
  resolution_op_id text   NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_sync_v3_conflicts_account
  ON sync_v3_conflicts(app, account_kind, account_id, status, at DESC);

-- ---------- دستگاه‌ها (SYN-08، SYN-10) ----------
CREATE TABLE IF NOT EXISTS sync_v3_devices (
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  device_id       text    NOT NULL,
  cursor          bigint  NOT NULL DEFAULT 0,
  last_push_at    bigint,
  last_pull_at    bigint,
  last_ok_at      bigint,
  last_error      text    NOT NULL DEFAULT '',
  last_error_at   bigint,
  --  opهای منتظرِ روی خودِ دستگاه (گزارشِ برنامه)
  queued          integer NOT NULL DEFAULT 0,
  clock_offset_ms bigint  NOT NULL DEFAULT 0,
  app_version     text    NOT NULL DEFAULT '',
  last_seen_at    bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id, device_id)
);

-- ---------- پلِ v1 ⇒ v3 ----------
CREATE TABLE IF NOT EXISTS sync_v3_bridge (
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  --  آخرین server_seqِ `oplog`ِ v1 که به v3 آمد
  v1_seq          bigint  NOT NULL DEFAULT 0,
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id)
);
