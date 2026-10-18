-- ============================================================
--  SEC-02 / STF-09 — زنجیرهٔ هشِ «فقط افزودنی» (بخشِ ۱۰.۳)
--
--  هر زنجیره (chain) فهرستی از حلقه‌هاست: seq از ۱ پشتِ سرِ هم،
--  prev_hash = hash ِ حلقهٔ قبلی (برای ۱: ۶۴ صفر)، و
--      hash = SHA-256( prev_hash ‖ body )
--  که body همان canonicalJson ِ سند است (قالبِ tohid-hashchain-v1،
--  license/test-vectors/hashchain-v1.json). هش را Node می‌سازد و
--  scripts/verify-chain.js دوباره می‌سازد و می‌سنجد.
--
--  نامِ زنجیره: audit/<shop|->/<actorType>/<user|->  (سابقهٔ هر کاربر)
--               doc/<shop>/<series>                  (اسنادِ مالیِ هر سری)
--
--  ⛔ خاموش است: تا `security / hashchain.audit` در live_config روشن نشود
--  هیچ ردیفی این‌جا نوشته نمی‌شود و رفتارِ سرور همان امروز است.
--  ⛔ دادهٔ شخصی (IP، متنِ جزئیات) در body نیست — فقط شناسه و درهم.
-- ============================================================

CREATE TABLE IF NOT EXISTS hash_chain (
  chain       text   NOT NULL,
  seq         bigint NOT NULL CHECK (seq >= 1),
  prev_hash   text   NOT NULL CHECK (prev_hash ~ '^[0-9a-f]{64}$'),
  hash        text   NOT NULL CHECK (hash ~ '^[0-9a-f]{64}$'),
  body        text   NOT NULL,
  created_at  bigint NOT NULL,
  PRIMARY KEY (chain, seq)
);

--  ⛔ حلقهٔ تازه فقط پشتِ سرِ آخرین حلقه می‌نشیند: شکاف و انشعاب در خودِ
--  دیتابیس ناممکن است (دو نوشتنِ هم‌زمان روی یک seq را کلیدِ اصلی رد می‌کند).
CREATE OR REPLACE FUNCTION hash_chain_check_link() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.seq = 1 THEN
    IF NEW.prev_hash <> repeat('0', 64) THEN
      RAISE EXCEPTION 'hash_chain %: first link must start from genesis', NEW.chain;
    END IF;
  ELSIF NOT EXISTS (
    SELECT 1 FROM hash_chain
     WHERE chain = NEW.chain AND seq = NEW.seq - 1 AND hash = NEW.prev_hash
  ) THEN
    RAISE EXCEPTION 'hash_chain %: link % does not follow link %', NEW.chain, NEW.seq, NEW.seq - 1;
  END IF;
  RETURN NEW;
END;
$$;

--  ⛔ SEC-03: نوشته‌شده هرگز عوض یا پاک نمی‌شود — اصلاح فقط با حلقهٔ تازه.
CREATE OR REPLACE FUNCTION hash_chain_refuse_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'hash_chain is append-only (% refused)', TG_OP;
END;
$$;

DROP TRIGGER IF EXISTS hash_chain_link ON hash_chain;
CREATE TRIGGER hash_chain_link
  BEFORE INSERT ON hash_chain
  FOR EACH ROW EXECUTE FUNCTION hash_chain_check_link();

DROP TRIGGER IF EXISTS hash_chain_immutable ON hash_chain;
CREATE TRIGGER hash_chain_immutable
  BEFORE UPDATE OR DELETE ON hash_chain
  FOR EACH ROW EXECUTE FUNCTION hash_chain_refuse_change();

DROP TRIGGER IF EXISTS hash_chain_no_truncate ON hash_chain;
CREATE TRIGGER hash_chain_no_truncate
  BEFORE TRUNCATE ON hash_chain
  FOR EACH STATEMENT EXECUTE FUNCTION hash_chain_refuse_change();
