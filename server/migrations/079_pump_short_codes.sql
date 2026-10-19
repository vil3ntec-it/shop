--  🔢 کدِ ۸ رقمیِ روزدارِ پمپ (۱۴۰۵/۰۷/۲۶، lib/pump-short-codes.js)
--
--  «کدش در حد شش یا هشت رقمی باشه… مقدار روز که می‌خوام رو بزارم.»
--  خودِ کد این‌جا هست (کوتاه است و رازی نیست که دوباره دیده شود)؛ فقط دفترِ
--  صدور است — برنامه بی اینترنت می‌سنجد و این‌جا چیزی برنمی‌گردد.
CREATE TABLE IF NOT EXISTS pump_short_codes (
  id          text PRIMARY KEY,
  code        text NOT NULL,
  days        integer NOT NULL,
  note        text NOT NULL DEFAULT '',
  created_by  text NOT NULL DEFAULT '',
  created_at  bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_pump_short_codes_created ON pump_short_codes(created_at DESC);
