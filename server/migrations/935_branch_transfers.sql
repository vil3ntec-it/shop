-- ============================================================
--  ۹۳۵ — انتقالِ موجودی میانِ شعبه‌های یک دکان (INV-11/S21، فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰؛ پشتِ پرچم‌های
--  خاموشِ `shop.branches` و `shop.catalog`).
--
--  ⛔ فقط افزودنی — هیچ جدول، ستون یا ردیفِ امروز عوض نمی‌شود. انتقالِ درونِ شعبه (`stock_transfers`، مهاجرتِ ۱۳۷/۲۶۵)
--     همان می‌ماند؛ انتقالِ میانِ شعبه‌ها جدول‌های جدای خودش را دارد.
--  ⛔ موجودی جای دیگری نیست: اثرِ هر گام همان حرکت‌های `stock_moves` ِ بخشِ هر شعبه است (`lib/branch-transfers.js`) —
--     ارسال: مبدأ ⇐ `in_transit` ِ شعبهٔ فرستنده؛ دریافت: `in_transit` ِ فرستنده ⇐ مکانِ مقصد در شعبهٔ گیرنده؛ رد: ماندهٔ
--     در مسیر به مبدأ. «در مسیرِ هر انتقال» = فرستاده − رسیده در ردیف‌های `lines` ِ همان انتقال.
--  ⛔ ماشینِ وضعیت در خودِ پایگاه هم: گذار فقط in_transit ⇒ partial ⇒ … ⇒ received | rejected؛ انتقالِ بسته دیگر
--     عوض نمی‌شود و دو سرِ انتقال هرگز (تریگرِ `branch_transfer_guard`).
--
--  برگشت: rollback/935_branch_transfers.down.sql (انتقالِ باز ⇒ برگشت رد می‌شود؛ اول دریافت یا رد).
-- ============================================================

CREATE TABLE IF NOT EXISTS branch_transfers (
  id             text    PRIMARY KEY CHECK (id ~ '^bxf_[0-9a-f]{24}$'),
  shop_id        text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  --  '' = شعبهٔ اصلی؛ شعبهٔ فرستنده فقط از سرآیندِ سنجیدهٔ درِ ارسال
  from_branch    text    NOT NULL CHECK (from_branch = '' OR from_branch ~ '^brn_[0-9a-f]{24}$'),
  to_branch      text    NOT NULL CHECK (to_branch = '' OR to_branch ~ '^brn_[0-9a-f]{24}$'),
  from_location  text    NOT NULL CHECK (from_location IN ('shop_floor','warehouse')),
  to_location    text    NOT NULL CHECK (to_location IN ('shop_floor','warehouse')),
  status         text    NOT NULL DEFAULT 'in_transit' CHECK (status IN ('in_transit','partial','received','rejected')),
  --  [{ productId, sentMilli, receivedMilli|null }] — حداکثر ۱۰۰ کالا (سنجش در کد)
  lines          jsonb   NOT NULL,
  note           text    NOT NULL DEFAULT '',
  --  دلیلِ رد یا کسری
  reason         text    NOT NULL DEFAULT '',
  created_by     text    NOT NULL,
  created_at     bigint  NOT NULL,
  closed_by      text    NOT NULL DEFAULT '',
  closed_at      bigint,
  CHECK (from_branch <> to_branch),
  CHECK ((status IN ('in_transit','partial')) = (closed_at IS NULL))
);
CREATE INDEX IF NOT EXISTS ix_branch_transfers_from ON branch_transfers(shop_id, from_branch, created_at, id);
CREATE INDEX IF NOT EXISTS ix_branch_transfers_to ON branch_transfers(shop_id, to_branch, created_at, id);

--  هر گام یک ردیف با کلیدِ یکتایی‌اش (`Idempotency-Key`)، در فضای **شعبهٔ کننده** (سرآیندِ سنجیده) — کلیدِ یک شعبه هرگز
--  گامِ شعبهٔ دیگر را پس نمی‌دهد. همان کلید + همان بدنه ⇒ همان نتیجه؛ بدنهٔ دیگر ⇒ ۴۰۹.
CREATE TABLE IF NOT EXISTS branch_transfer_steps (
  shop_id      text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  branch_id    text    NOT NULL CHECK (branch_id = '' OR branch_id ~ '^brn_[0-9a-f]{24}$'),
  step_key     text    NOT NULL,
  transfer_id  text    NOT NULL REFERENCES branch_transfers(id) ON DELETE CASCADE,
  kind         text    NOT NULL CHECK (kind IN ('send','receive','reject')),
  body_hash    text    NOT NULL,
  status_from  text    NOT NULL,
  status_to    text    NOT NULL,
  --  [{ productId, qtyMilli }] ِ همین گام (ارسال: فرستاده؛ دریافت: رسیده در همین گام؛ رد: برگشته)
  lines        jsonb   NOT NULL DEFAULT '[]'::jsonb,
  note         text    NOT NULL DEFAULT '',
  final        boolean NOT NULL DEFAULT true,
  actor        text    NOT NULL DEFAULT '',
  created_at   bigint  NOT NULL,
  PRIMARY KEY (shop_id, branch_id, step_key),
  CHECK (
       (kind = 'send'    AND status_from = ''                         AND status_to = 'in_transit')
    OR (kind = 'receive' AND status_from IN ('in_transit','partial')  AND status_to IN ('partial','received'))
    OR (kind = 'reject'  AND status_from IN ('in_transit','partial')  AND status_to = 'rejected')
  )
);
CREATE INDEX IF NOT EXISTS ix_branch_transfer_steps_xfer ON branch_transfer_steps(shop_id, transfer_id, created_at);

--  گذارِ نامجاز هرگز، حتی با باگِ کد: بسته ⇒ ثابت؛ دو سر، مکان‌ها، فرستاده‌ها و سازنده ⇒ ثابت
CREATE OR REPLACE FUNCTION branch_transfer_guard() RETURNS trigger AS $$
BEGIN
  IF OLD.status IN ('received', 'rejected') THEN
    RAISE EXCEPTION 'branch_transfer % is closed (%)', OLD.id, OLD.status USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.shop_id <> OLD.shop_id OR NEW.from_branch <> OLD.from_branch OR NEW.to_branch <> OLD.to_branch
     OR NEW.from_location <> OLD.from_location OR NEW.to_location <> OLD.to_location
     OR NEW.created_by <> OLD.created_by OR NEW.created_at <> OLD.created_at OR NEW.id <> OLD.id THEN
    RAISE EXCEPTION 'branch_transfer %: ends are immutable', OLD.id USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.status NOT IN ('partial', 'received', 'rejected') THEN
    RAISE EXCEPTION 'branch_transfer %: % -> % is not allowed', OLD.id, OLD.status, NEW.status USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS branch_transfer_guard ON branch_transfers;
CREATE TRIGGER branch_transfer_guard BEFORE UPDATE ON branch_transfers
  FOR EACH ROW EXECUTE FUNCTION branch_transfer_guard();
