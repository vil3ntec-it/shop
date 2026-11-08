-- ============================================================
--  ۳۱۵ — صندوقِ سندهای ردشده برای صاحب و مدیر (`lib/conflict-inbox.js`، `docs/DATA-SAFETY-fa.md`)
--
--  سندِ پولیِ ردشده (قرضِ بالای سقف، برگشتِ بیش از فروخته، «نیازمندِ تأیید»، سقفِ قیمت) از پیش کامل در
--  `sync_v3_conflicts.loser_value` می‌نشست (#989). حالا صاحب/مدیر از برنامه سه کار با آن دارد:
--    «دوباره ثبت با تأییدِ من» ⇒ `resubmitted`  (opهای تازه با شناسهٔ بسته به همین ردیف — یک بار، نه دو بار)
--    «اصلاح و ثبت»            ⇒ `fixed`        (فاکتورِ تازهٔ صندوق؛ شناسه‌اش در `detail.fixed_doc`)
--    «کنار بگذار»              ⇒ `dismissed`    (دلیلِ اجباری در `resolution_note`)
--
--  فقط افزودنی: دو حالِ تازه در CHECK و یک ستونِ متنی با پیش‌فرض. ردیف‌های موجود دست نمی‌خورند.
--  rollback: migrations/rollback/315_conflict_inbox.down.sql
-- ============================================================

ALTER TABLE sync_v3_conflicts DROP CONSTRAINT IF EXISTS sync_v3_conflicts_status_check;
ALTER TABLE sync_v3_conflicts ADD CONSTRAINT sync_v3_conflicts_status_check
  CHECK (status IN ('open','kept','reverted','dismissed','resubmitted','fixed'));

--  دلیلِ «کنار بگذار» یا یادداشتِ «دوباره ثبت» ِ همان کسی که بست (≤ ۲۰۰ نویسه، `conflict-inbox.js`)
ALTER TABLE sync_v3_conflicts ADD COLUMN IF NOT EXISTS resolution_note text NOT NULL DEFAULT '';
