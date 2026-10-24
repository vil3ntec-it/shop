-- ============================================================
--  PAY-18 — گاوصندوقِ دکان (طرحِ SAFE-V1، دستورِ صاحب محصول)
--
--  مجموعهٔ تازهٔ `safeEntries` (پلهٔ ۴ِ Sync v1ِ دکان): دفترِ فقط‌افزودنیِ
--  شمارش، ورود، خروج، انتقال از/به دخل و تحویل به بانک با شمارهٔ کیسه.
--  مانده = آخرین شمارش + جابه‌جایی‌های پس از آن (`core/safe/SafeCore`).
--
--  همان شکلِ جدول‌های ۰۰۲ و ۰۴۸ تا خواندنِ راهِ قدیم (`/api/sync`) و شمارشِ
--  پنلِ مدیر بی استثنا کار کنند. نوشتنِ راهِ قدیم در این جدول با `sync_v1_only`
--  رد می‌شود (`lib/sync.js → V1_ONLY`)؛ Sync v1 در `sync_rows` می‌نویسد و شکلِ
--  ردیف و مجوز را `lib/safe-ledger.js` می‌سنجد.
--
--    safeEntries → shop_safe_entries
-- ============================================================

CREATE TABLE IF NOT EXISTS shop_safe_entries (
  shop_id     text    NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
  id          text    NOT NULL,
  rev         bigint  NOT NULL,
  version     integer NOT NULL DEFAULT 1,
  created_at  bigint  NOT NULL,
  updated_at  bigint  NOT NULL,
  deleted     boolean NOT NULL DEFAULT false,
  device_id   text    NOT NULL DEFAULT '',
  user_id     text    NOT NULL DEFAULT '',
  data        jsonb   NOT NULL DEFAULT '{}'::jsonb,
  PRIMARY KEY (shop_id, id)
);
CREATE INDEX IF NOT EXISTS idx_shop_safe_entries_rev ON shop_safe_entries (shop_id, rev);
CREATE INDEX IF NOT EXISTS idx_shop_safe_entries_live ON shop_safe_entries (shop_id) WHERE deleted = false;
