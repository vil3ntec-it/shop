-- ============================================================
--  ۲۱۵ — حالتِ مشترکِ چند نمونهٔ سرور روی یک PostgreSQL (`CLUSTER_ENABLED=1`؛ docs/SCALING-fa.md).
--
--  ۱) `rate_limit_hits` — انبارِ مشترکِ سقفِ نرخ (`RATE_LIMIT_STORE=pg`، `middleware/ratelimit.js`): هر سطل
--     (کلیدِ درهم با sha256) و هر برشِ پنجره یک ردیفِ کوچک؛ پذیرفتن/رد در یک upsert ِ شرطی. UNLOGGED: بی
--     WAL (ارزان در هر درخواست) و پس از crash ِ PostgreSQL خالی — سقفِ نرخ از نو شمرده می‌شود، هیچ دادهٔ
--     کسب‌وکاری گم نمی‌شود. به نسخهٔ پشتیبان (replica) هم نمی‌رود و لازم نیست.
--  ۲) `live_ticket_uses` — «این بلیتِ سوکت مصرف شد» (`lib/sync-v1-live.js`): بلیتِ خوشه رمزشده و خودبسنده است و
--     نمونه‌ای جز صادرکننده هم بازش می‌کند؛ فقط هشِ بلیتِ مصرف‌شده (نه توکن) تا پایانِ ۶۰ ثانیه می‌ماند تا
--     دوباره پذیرفته نشود.
--
--  تک‌نمونه (پیش‌فرض) به هیچ‌کدام دست نمی‌زند. پاک‌سازی: رهبرِ خوشه هر دقیقه ردیف‌های گذشته را برمی‌دارد.
--  rollback: migrations/rollback/215_cluster_state.down.sql
-- ============================================================

CREATE UNLOGGED TABLE IF NOT EXISTS rate_limit_hits (
  key        text    NOT NULL,
  slot       bigint  NOT NULL,          -- floor(زمان / برش)؛ برش = پنجره / ۶۰ (کمینه ۱ ثانیه)
  n          integer NOT NULL,
  expires_at bigint  NOT NULL,          -- ms: پس از این، این برش دیگر در هیچ پنجره‌ای نیست
  PRIMARY KEY (key, slot)
);
CREATE INDEX IF NOT EXISTS idx_rate_limit_hits_expires ON rate_limit_hits (expires_at);

CREATE UNLOGGED TABLE IF NOT EXISTS live_ticket_uses (
  ticket_hash text   PRIMARY KEY,
  expires_at  bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_live_ticket_uses_expires ON live_ticket_uses (expires_at);
