-- ============================================================
--  کلیدِ یکتایی (Idempotency-Key) برای کارهای پولیِ پنلِ مدیر — F0-14
--
--  دو بار کلیک روی «ثبت اشتراک» (یا تکرارِ درخواست پس از قطعِ شبکه)
--  دو دوره اشتراک می‌ساخت. پنل حالا برای هر ثبت یک کلید می‌فرستد و
--  سرور کلید را در **همان تراکنشِ** اعطا، زیرِ قفلِ همان کلید، ثبت
--  می‌کند؛ درخواستِ تکراری پاسخِ اول را پس می‌گیرد و چیزی نمی‌سازد.
--
--  فقط جدولِ تازه است؛ هیچ جدولِ موجودی دست نمی‌خورد.
--  ردیف‌ها بعد از ۷ روز پاک می‌شوند (lib/idempotency.js).
-- ============================================================
CREATE TABLE IF NOT EXISTS idempotency_keys (
  scope         text   NOT NULL,
  key           text   NOT NULL,
  request_hash  text   NOT NULL,
  tenant_id     text   NOT NULL DEFAULT '',
  result_id     text   NOT NULL DEFAULT '',
  response      jsonb  NOT NULL DEFAULT '{}'::jsonb,
  created_at    bigint NOT NULL,
  PRIMARY KEY (scope, key)
);
CREATE INDEX IF NOT EXISTS ix_idempotency_keys_created ON idempotency_keys(created_at);
CREATE INDEX IF NOT EXISTS ix_idempotency_keys_tenant ON idempotency_keys(tenant_id);
