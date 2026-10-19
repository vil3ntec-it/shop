-- ============================================================
--  پلِ v3 ⇒ v1 (دکانِ «دستگاه‌های قاطی»: بعضی v1، بعضی v3)
--
--  پشتِ پرچمِ دکانیِ `shop.sync_v3_to_v1` در «تنظیماتِ زنده» — پیش‌فرض خاموش.
--  خاموش ⇒ هیچ ردیفی در این دو جدول و در `oplog`/`sync_rows` نوشته نمی‌شود.
--
--  صندوقِ خروجی خودِ `sync_v3_ops` است (دفترِ پایدارِ v3)؛ این‌جا فقط:
--    • `sync_v3_v1_state`  تا کجای `sync_v3_ops` به v1 رفته (cursor)
--    • `sync_v3_v1_out`    هر ردیفِ `oplog` که پل نوشته ⇒ (۱) تکرار بی‌اثر،
--                          (۲) پلِ v1 ⇒ v3 همین ردیف‌ها را برنمی‌گرداند (بی حلقه)،
--                          (۳) HLCِ همان op برای داوریِ یکسان در هر دو پروتکل.
--
--  ⛔ جدول‌های v1 (`oplog`، `sync_rows`) دست نمی‌خورند؛ پل همان‌طور در آن‌ها
--     می‌نویسد که `push`ِ v1 می‌نویسد.
--  برگشت: پرچم را خاموش کنید. حذفِ کامل: DROP دو جدول (ردیف‌های نوشته‌شده در
--  `oplog` opهای عادیِ v1اند و می‌مانند).
-- ============================================================

CREATE TABLE IF NOT EXISTS sync_v3_v1_state (
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  --  آخرین server_seqِ `sync_v3_ops` که بررسی شد
  v3_seq          bigint  NOT NULL DEFAULT 0,
  updated_at      bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id)
);

CREATE TABLE IF NOT EXISTS sync_v3_v1_out (
  app             text    NOT NULL,
  account_kind    text    NOT NULL,
  account_id      text    NOT NULL,
  v3_op_id        text    NOT NULL,
  --  یک op v3 گاهی چند opِ v1 می‌شود؛ شمارهٔ همان تکه
  part            smallint NOT NULL DEFAULT 0,
  v3_seq          bigint  NOT NULL,
  v1_op_id        text    NOT NULL,
  v1_seq          bigint  NOT NULL,
  --  HLCِ مؤثرِ همان op در v3 — کفِ HLCِ opهای v1ِ بعدی
  hlc             text    NOT NULL DEFAULT '',
  at              bigint  NOT NULL,
  PRIMARY KEY (app, account_kind, account_id, v3_op_id, part)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sync_v3_v1_out_seq
  ON sync_v3_v1_out(app, account_kind, account_id, v1_seq);
CREATE INDEX IF NOT EXISTS idx_sync_v3_v1_out_hlc
  ON sync_v3_v1_out(app, account_kind, account_id, hlc);
