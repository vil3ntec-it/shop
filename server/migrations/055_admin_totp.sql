-- ============================================================
--  F0-15 — تأییدِ دومرحله‌ایِ «اختیاری» برای مدیرِ سرور (TOTP، RFC 6238)
--
--  همه ستونِ تازه با پیش‌فرض؛ هیچ ردیفی عوض نمی‌شود و ورودِ امروز
--  دست‌نخورده می‌ماند. تا مدیری خودش روشنش نکند، هیچ چیز فرق ندارد.
--
--    totp_secret      رازِ base32 — فقط وقتی روشن شد پر است
--    totp_pending     رازِ در حالِ ساختن، تا کدِ اول درست بیاید
--    totp_enabled_at  روشن شدن (null یعنی خاموش)
--    totp_last_step   آخرین گامِ پذیرفته ⇒ یک کد دو بار پذیرفته نمی‌شود
--    totp_recovery    درهمِ SHA-256 کدهای بازیابیِ یک‌بارمصرف (JSON)
-- ============================================================
ALTER TABLE admins ADD COLUMN IF NOT EXISTS totp_secret     text   NOT NULL DEFAULT '';
ALTER TABLE admins ADD COLUMN IF NOT EXISTS totp_pending    text   NOT NULL DEFAULT '';
ALTER TABLE admins ADD COLUMN IF NOT EXISTS totp_enabled_at bigint;
ALTER TABLE admins ADD COLUMN IF NOT EXISTS totp_last_step  bigint NOT NULL DEFAULT 0;
ALTER TABLE admins ADD COLUMN IF NOT EXISTS totp_recovery   text   NOT NULL DEFAULT '[]';
