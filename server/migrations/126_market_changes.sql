-- S6 (docs/MARKETPLACE-CONTRACT-fa.md §۳): جریانِ تغییرِ بازار (outbox) — پشتِ پرچمِ `market.changes`، پیش‌فرض خاموش.
-- هر تغییرِ پروفایل، حضور، فهرستِ بازارچه، انتشار/قیمتِ کالا و جریمه، در همان تراکنشِ تغییر، یک ردیف.
-- امروز فقط برای بی‌اعتبار کردنِ کش و نمایهٔ جست‌وجوی بازار با `seq` (lib/market.js)؛ بعداً پایهٔ وب‌هوک و API.
-- `sig` = امضای حالتِ تازه ⇒ تکرارِ همان درخواست ردیفِ دوم نمی‌سازد (lib/market-changes.js).
-- نگهداری: ردیف‌های کهنه‌تر از MARKET_CHANGES_KEEP_DAYS (پیش‌فرض ۱۴ روز) در کارِ دوره‌ای پاک می‌شوند.
-- فقط افزودنی — هیچ جدولِ دیگری دست نمی‌خورد. برگشت: DROP TABLE IF EXISTS market_changes;
CREATE TABLE IF NOT EXISTS market_changes (
  seq        bigserial PRIMARY KEY,
  shop_id    text   NOT NULL,
  entity     text   NOT NULL,          -- shop | presence | listing | product | sanction
  entity_id  text   NOT NULL DEFAULT '',
  kind       text   NOT NULL,          -- create | update | publish | unpublish | price | impose | lift
  sig        text   NOT NULL DEFAULT '',
  at         bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_market_changes_entity ON market_changes(shop_id, entity, entity_id, seq);
CREATE INDEX IF NOT EXISTS ix_market_changes_at ON market_changes(at);
