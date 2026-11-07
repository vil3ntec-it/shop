-- ============================================================
--  ۲۲۷ — نبضِ همهٔ نمونه‌های خوشه برای پنلِ مرکزی ۴.۹ (`CLUSTER_ENABLED=1`؛ docs/SCALING-fa.md).
--
--  تا امروز `job-beat` فقط کارهای **همان** نمونه را نشان می‌داد؛ روی پیرو (که هیچ کارگری ندارد) همه «نامعلوم» بود و
--  پنل نمی‌گفت رهبر کیست. حالا هر نمونه هر `CLUSTER_HEARTBEAT_MS` یک ردیفِ کوچک می‌نویسد: کی بالا آمد، آخرین تپش،
--  «رهبرم؟» و برشِ `job-beat` ِ خودش (فقط نام، فاصله، زمان‌ها، شمارنده‌ها و برچسبِ خطا «نام:کد» — هیچ پیامِ خام،
--  راز یا دادهٔ مشتری). `GET /api/admin/ops/health` با این‌ها و دارندهٔ واقعیِ قفلِ رهبر (`pg_locks`) نمای خوشه
--  را می‌سازد. رهبر ردیفِ نمونه‌ای را که ۱۰ دقیقه تپش نداشته برمی‌دارد؛ خاموشیِ تمیز ردیفِ خودش را.
--
--  UNLOGGED: حالتِ گذرا است (پس از crash ِ PostgreSQL خالی و با تپشِ بعدی پر)؛ به نسخهٔ خواندنی نمی‌رود و لازم نیست.
--  تک‌نمونه (پیش‌فرض) به آن دست نمی‌زند. rollback: migrations/rollback/227_cluster_instances.down.sql
-- ============================================================

CREATE UNLOGGED TABLE IF NOT EXISTS cluster_instances (
  instance_id  text    PRIMARY KEY,
  started_at   bigint  NOT NULL,
  beat_at      bigint  NOT NULL,
  leader       boolean NOT NULL DEFAULT false,
  leader_since bigint,
  version      text    NOT NULL DEFAULT '',
  jobs         jsonb   NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_cluster_instances_beat ON cluster_instances (beat_at);
