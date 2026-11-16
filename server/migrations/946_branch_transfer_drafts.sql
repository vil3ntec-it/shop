-- ============================================================
--  ۹۴۶ — پایانِ فازِ ۴ ِ شعبه‌ها (`docs/BRANCHES-fa.md` §۱۰؛ پشتِ پرچم‌های خاموشِ `shop.branches` و `shop.catalog`):
--    • انتقالِ میانِ شعبه‌ها **پیش‌نویس** و **لغو** هم دارد — ماشینِ وضعیتِ کامل:
--        draft ⇒ in_transit (ارسال) ⇒ partial ⇒ received | rejected
--        draft ⇒ cancelled (بی هیچ اثرِ موجودی) · in_transit (هیچ نرسیده) ⇒ cancelled (فرستنده؛ کالا به مبدأ)
--      پیش‌نویس مالِ شعبهٔ فرستنده است: شعبهٔ مقصد آن را نمی‌بیند تا فرستاده شود؛ مقصد، مکان‌ها و ردیف‌هایش تا ارسال عوض
--      می‌شوند؛ پس از ارسال دو سر و مکان‌ها ثابت (همان تریگرِ ۹۳۵، بازنوشته).
--    • `sent_at`/`sent_by`: لحظه و کننده‌ٔ ارسال (ردیف‌های پیش از ۹۴۶ همه در دمِ ساختن فرستاده شدند ⇒ NULL = «همان ساختن»،
--      بی کوچ؛ نما همین را می‌خواند).
--    • `shop_branch_settings` (یک ردیف برای هر دکان، فقط صاحب می‌نویسد): `staff_other_stock` — کارمند (نه صاحب و مدیر)
--      موجودیِ شعبه‌های دیگر را در صندوق/قیمت‌پرسی ببیند (POS-16/S17، پیش‌فرض خاموش)؛ `transfer_negative` — ارسالِ بیش از
--      موجودیِ سرور (پیش‌فرض خاموش ⇒ ۴۰۹ `insufficient_stock`؛ روشن ⇒ حرکتِ «ثبت‌شده» با وظیفهٔ `negative_stock`، هرگز بی‌صدا).
--
--  ⛔ فقط افزودنی: هیچ ردیفِ امروز عوض نمی‌شود؛ CHECK ها فقط گشادتر (همهٔ ردیف‌های امروز در وضعیت‌های پیشین‌اند).
--  برگشت: rollback/946_branch_transfer_drafts.down.sql (پیش‌نویسِ باز ⇒ رد؛ لغوشده‌ها و گام‌های پیش‌نویس پاک).
-- ============================================================

ALTER TABLE branch_transfers ADD COLUMN IF NOT EXISTS sent_by text NOT NULL DEFAULT '';
ALTER TABLE branch_transfers ADD COLUMN IF NOT EXISTS sent_at bigint;

--  CHECK های وضعیتِ ۹۳۵ (نامِ خودکار) ⇒ همان با پیش‌نویس و لغو
DO $$
DECLARE r record;
BEGIN
  FOR r IN SELECT conname FROM pg_constraint
            WHERE conrelid = 'branch_transfers'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%in_transit%' LOOP
    EXECUTE format('ALTER TABLE branch_transfers DROP CONSTRAINT %I', r.conname);
  END LOOP;
  FOR r IN SELECT conname FROM pg_constraint
            WHERE conrelid = 'branch_transfer_steps'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%reject%' LOOP
    EXECUTE format('ALTER TABLE branch_transfer_steps DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;

ALTER TABLE branch_transfers ADD CONSTRAINT branch_transfers_status_v2
  CHECK (status IN ('draft', 'in_transit', 'partial', 'received', 'rejected', 'cancelled'));
ALTER TABLE branch_transfers ADD CONSTRAINT branch_transfers_open_v2
  CHECK ((status IN ('draft', 'in_transit', 'partial')) = (closed_at IS NULL));

ALTER TABLE branch_transfer_steps ADD CONSTRAINT branch_transfer_steps_kind_v2
  CHECK (kind IN ('draft', 'edit', 'send', 'receive', 'reject', 'cancel'));
ALTER TABLE branch_transfer_steps ADD CONSTRAINT branch_transfer_steps_flow_v2 CHECK (
     (kind = 'draft'   AND status_from = ''                         AND status_to = 'draft')
  OR (kind = 'edit'    AND status_from = 'draft'                    AND status_to = 'draft')
  OR (kind = 'send'    AND status_from IN ('', 'draft')             AND status_to = 'in_transit')
  OR (kind = 'receive' AND status_from IN ('in_transit', 'partial') AND status_to IN ('partial', 'received'))
  OR (kind = 'reject'  AND status_from IN ('in_transit', 'partial') AND status_to = 'rejected')
  OR (kind = 'cancel'  AND status_from IN ('draft', 'in_transit')   AND status_to = 'cancelled')
);

--  گذارِ نامجاز هرگز، حتی با باگِ کد: بسته ⇒ ثابت؛ شناسه، دکان، شعبهٔ فرستنده و سازنده ⇒ ثابت؛ پس از ارسال مقصد، مکان‌ها و
--  لحظهٔ ارسال ⇒ ثابت. partial دیگر لغو نمی‌شود (چیزی رسیده — فقط دریافت یا رد در مقصد).
CREATE OR REPLACE FUNCTION branch_transfer_guard() RETURNS trigger AS $$
BEGIN
  IF OLD.status IN ('received', 'rejected', 'cancelled') THEN
    RAISE EXCEPTION 'branch_transfer % is closed (%)', OLD.id, OLD.status USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.id <> OLD.id OR NEW.shop_id <> OLD.shop_id OR NEW.from_branch <> OLD.from_branch
     OR NEW.created_by <> OLD.created_by OR NEW.created_at <> OLD.created_at THEN
    RAISE EXCEPTION 'branch_transfer %: ends are immutable', OLD.id USING ERRCODE = 'check_violation';
  END IF;
  IF OLD.status = 'draft' THEN
    IF NEW.status NOT IN ('draft', 'in_transit', 'cancelled') THEN
      RAISE EXCEPTION 'branch_transfer %: % -> % is not allowed', OLD.id, OLD.status, NEW.status USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
  END IF;
  IF NEW.to_branch <> OLD.to_branch OR NEW.from_location <> OLD.from_location OR NEW.to_location <> OLD.to_location
     OR NEW.sent_by <> OLD.sent_by OR NEW.sent_at IS DISTINCT FROM OLD.sent_at THEN
    RAISE EXCEPTION 'branch_transfer %: ends are immutable', OLD.id USING ERRCODE = 'check_violation';
  END IF;
  IF (OLD.status = 'in_transit' AND NEW.status NOT IN ('partial', 'received', 'rejected', 'cancelled'))
     OR (OLD.status = 'partial' AND NEW.status NOT IN ('partial', 'received', 'rejected')) THEN
    RAISE EXCEPTION 'branch_transfer %: % -> % is not allowed', OLD.id, OLD.status, NEW.status USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

--  POS-16/S17 و INV-11: تنظیم‌های شعبه‌ایِ دکان — فقط صاحب (`PUT /api/shop/branches/settings`)؛ بی ردیف ⇒ هر دو خاموش
CREATE TABLE IF NOT EXISTS shop_branch_settings (
  shop_id            text    PRIMARY KEY REFERENCES shops(id) ON DELETE CASCADE,
  staff_other_stock  boolean NOT NULL DEFAULT false,
  transfer_negative  boolean NOT NULL DEFAULT false,
  updated_by         text    NOT NULL DEFAULT '',
  updated_at         bigint  NOT NULL
);
