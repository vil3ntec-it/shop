-- پنلِ مرکزیِ مالک، ماژولِ ۴.۵ «مالیِ مرکزی» (شاخهٔ claude/console-finance-api) — مهاجرتِ ۱۷۱.
--
-- مالیِ مرکزی = پولِ اشتراکِ خودِ پلتفرم (sub_payments)، جدا از حساب‌داریِ داخلیِ هر دکان.
--
-- ۱) finance_adjustments — «سندِ اصلاحی». هیچ پرداختی ویرایش یا پاک نمی‌شود؛ هر اصلاح یک سندِ تازه است
--    که به یک پرداخت بسته است:
--      correction  اصلاحِ مبلغ (+ یا −)                      amount ≠ 0
--      refund      بازپرداخت به مشتری                         amount < 0
--      reversal    باطل کردنِ کاملِ باقیماندهٔ پرداخت         amount < 0 (= −خالصِ همان لحظه)
--      reference   بستنِ شمارهٔ تراکنشِ بیرونی به پرداخت    amount = 0، reference اجباری
--    خالصِ یک پرداخت = amount ِ خودش + جمعِ سندهایش (هرگز منفی — در سرور، زیرِ قفلِ ردیفِ پرداخت).
--    ⛔ فقط افزودنی: UPDATE و DELETE با تریگر رد می‌شود (همان الگوی order_ledger_docs، مهاجرتِ ۰۹۵).
--    واحدِ مبلغ همان واحدِ sub_payments.amount است (افغانی یا دالرِ درست، نه یکصدم).
--
-- ۲) sub_payment_revisions — تاریخچهٔ خودکارِ هر UPDATE/DELETE روی sub_payments (راهِ پیشینِ
--    PUT/DELETE ِ ارشد هنوز هست): ردیفِ پیش و پس، با تریگر — پس هیچ راهی بی ردپا عوض نمی‌کند.
--    این جدول هم فقط افزودنی است.
--
-- بازگشت (پیش از نخستین سند): DROP TRIGGER sub_payments_revision ON sub_payments;
--   DROP TABLE sub_payment_revisions; DROP TABLE finance_adjustments; DROP SEQUENCE finance_adjustment_seq;
--   DROP FUNCTION finance_refuse_change(); DROP FUNCTION sub_payments_revision();

CREATE SEQUENCE IF NOT EXISTS finance_adjustment_seq;

CREATE TABLE IF NOT EXISTS finance_adjustments (
  id               text    PRIMARY KEY,                    -- fad_<hex>
  doc_no           text    NOT NULL UNIQUE,                -- ADJ-<سال>-<شماره>
  kind             text    NOT NULL CHECK (kind IN ('correction','refund','reversal','reference')),
  payment_id       text    NOT NULL,                       -- sub_payments.id (بی FK: پرداخت هرگز پاک نمی‌شود)
  app              text    NOT NULL CHECK (app IN ('shop','pump')),
  tenant_id        text    NOT NULL,
  subscription_id  text    NOT NULL DEFAULT '',
  amount           bigint  NOT NULL,
  currency         text    NOT NULL CHECK (currency IN ('AFN','USD')),
  net_before       bigint  NOT NULL,
  net_after        bigint  NOT NULL CHECK (net_after >= 0),
  reference        text    NOT NULL DEFAULT '',
  reason           text    NOT NULL CHECK (char_length(reason) >= 3),
  created_by       text    NOT NULL,
  created_actor    jsonb   NOT NULL DEFAULT '{}'::jsonb,
  reauth           text    NOT NULL DEFAULT '',             -- login | password | panel
  created_at       bigint  NOT NULL,
  CHECK (net_after = net_before + amount),
  CHECK (kind <> 'correction' OR amount <> 0),
  CHECK (kind NOT IN ('refund','reversal') OR amount < 0),
  CHECK (kind <> 'reference' OR (amount = 0 AND reference <> ''))
);
CREATE INDEX IF NOT EXISTS ix_finance_adj_payment ON finance_adjustments(payment_id, created_at);
CREATE INDEX IF NOT EXISTS ix_finance_adj_created ON finance_adjustments(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_finance_adj_tenant ON finance_adjustments(app, tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_finance_adj_sub ON finance_adjustments(subscription_id) WHERE subscription_id <> '';

CREATE TABLE IF NOT EXISTS sub_payment_revisions (
  id          bigserial PRIMARY KEY,
  payment_id  text    NOT NULL,
  op          text    NOT NULL CHECK (op IN ('UPDATE','DELETE')),
  before      jsonb   NOT NULL,
  after       jsonb,
  changed_at  bigint  NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_sub_payment_revisions ON sub_payment_revisions(payment_id, id);
CREATE INDEX IF NOT EXISTS ix_sub_payment_revisions_at ON sub_payment_revisions(changed_at DESC);

--  نمایه برای «تاریخچهٔ مالی» (فیلترِ action روی audit_logs)
CREATE INDEX IF NOT EXISTS ix_audit_action_created ON audit_logs(action, created_at DESC);

CREATE OR REPLACE FUNCTION finance_refuse_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION '% is append-only (% refused)', TG_TABLE_NAME, TG_OP;
END;
$$;

DROP TRIGGER IF EXISTS finance_adjustments_append_only ON finance_adjustments;
CREATE TRIGGER finance_adjustments_append_only
  BEFORE UPDATE OR DELETE ON finance_adjustments
  FOR EACH ROW EXECUTE FUNCTION finance_refuse_change();

DROP TRIGGER IF EXISTS sub_payment_revisions_append_only ON sub_payment_revisions;
CREATE TRIGGER sub_payment_revisions_append_only
  BEFORE UPDATE OR DELETE ON sub_payment_revisions
  FOR EACH ROW EXECUTE FUNCTION finance_refuse_change();

CREATE OR REPLACE FUNCTION sub_payments_revision() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  t bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
BEGIN
  IF TG_OP = 'DELETE' THEN
    INSERT INTO sub_payment_revisions (payment_id, op, before, after, changed_at)
      VALUES (OLD.id, 'DELETE', to_jsonb(OLD), NULL, t);
    RETURN OLD;
  END IF;
  IF to_jsonb(OLD) IS DISTINCT FROM to_jsonb(NEW) THEN
    INSERT INTO sub_payment_revisions (payment_id, op, before, after, changed_at)
      VALUES (OLD.id, 'UPDATE', to_jsonb(OLD), to_jsonb(NEW), t);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS sub_payments_revision ON sub_payments;
CREATE TRIGGER sub_payments_revision
  AFTER UPDATE OR DELETE ON sub_payments
  FOR EACH ROW EXECUTE FUNCTION sub_payments_revision();
