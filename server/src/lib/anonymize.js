'use strict';
/**
 * O-15 الف (۳-۴ ِ `docs/OWNER-DECISIONS-fa.md`) — بی‌نام کردنِ **کپیِ** دادهٔ واقعی برای آزمودنِ جابه‌جایی و همگام‌سازیِ
 * تازه (DATA-07، S54، SYN-07). فقط روی دیتابیسِ جدایی می‌دود که `scripts/anonymize-backup.js` از یک پشتیبان برگردانده؛
 * ⛔ هرگز روی دیتابیسِ زنده (`assertNotLive`).
 *
 *   نام، تلفن، نشانی، یادداشت، ایمیل و رازها  ⇒ جای‌گزینِ قطعیِ هم‌شکل (`pseudo`): هر حرف با حرفی از همان خط (فارسی ⇒ فارسی،
 *                                         لاتینِ کوچک/بزرگ، رقم ⇒ رقم؛ فاصله و نشانه همان)، پس طول، خط و قالب (قیدِ
 *                                         CHECK مثلِ `^\+[1-9]…` ِ تلفن یا slug) می‌مانند. HMAC با کلیدِ تصادفیِ همین اجرا که
 *                                         هیچ‌جا نوشته نمی‌شود ⇒ برگشت‌ناپذیر، ولی یک مقدار همه‌جا همان جای‌گزین را می‌گیرد
 *                                         (پیوندِ مشتری و فاکتور می‌ماند). جای‌گزین هرگز با مقدارِ واقعیِ دیگری یکی نیست.
 *   مبلغ، قرض، مقدار، شناسه، وضعیت، تاریخ  ⇒ دست‌نخورده؛ اثرِ انگشتِ `restore-drill.fingerprint` پیش و پس یکی است.
 *   مختصاتِ جغرافیایی و تلفنِ عددی          ⇒ مختصات گرد تا یک رقمِ اعشار (~۱۰ کیلومتر)؛ تنها عددهای عوض‌شده (`numKeys`).
 *   bytea (عکس، پیوست، رازِ رمزشده)        ⇒ خالی؛ inet ⇒ 192.0.2.x (نشانیِ مستند).
 * متنِ JSON درونِ ستونِ متنی باز و با همین قاعده بی‌نام می‌شود. ستونِ ناشناخته: شناسه‌وار، تاریخ، عدد و واژهٔ رمزیِ
 * لاتین (`pending`، `sale_unit`) می‌ماند؛ هر چیزِ دیگر (متنِ فارسی، فاصله‌دار، تلفن‌وار) جای‌گزین می‌شود.
 * پیش از نوشتن، سنجشِ نشت: هیچ مقدارِ ماندگاری با مقدارِ جای‌گزین‌شده یا رشتهٔ رقمیِ تلفنی برابر نیست (`leaks`)؛ وگرنه
 * هیچ نوشته‌ای نمی‌شود. زنجیره‌های هش (`hash_chain`، سابقهٔ زنجیره‌دار) پس از این دیگر نمی‌خوانند — کپی برای آزمونِ
 * زنجیره نیست. آزمون: `test/anonymize.test.js`.
 */
const { createHmac, randomBytes } = require('crypto');

const ident = (n) => `"${String(n).replace(/"/g, '""')}"`;
const norm = (k) => String(k).toLowerCase().replace(/[^a-z0-9]/g, '');

/* ───────────── گونهٔ هر نام (ستون یا کلیدِ JSON) ───────────── */

/** همیشه جای‌گزین، پیش از هر قاعدهٔ نگه‌داشتن (شناسهٔ تلگرام و نشانیِ اینترنتی هم کسی را نشان می‌دهند) */
const PII_EXACT = new Set(['tgid', 'chatid', 'fromchat', 'bytg', 'tgusername', 'identifier', 'ip', 'lastip', 'ipkey', 'useragent',
  'devicename', 'devicelocation', 'phonekey', 'searchkey', 'namekey', 'namenorm', 'q', 'pluscode', 'licenseholder', 'slug', 'oldslug',
  'altnames', 'qrpayload', 'proof', 'logtail', 'stack', 'linkvalue']);
/** ساختاری: می‌ماند */
const KEEP_EXACT = new Set(['id', 'rowid', 'opid', 'tablename', 'accountkind', 'app', 'key', 'kind', 'type', 'status', 'state',
  'scope', 'role', 'perm', 'currency', 'unit', 'method', 'disposition', 'channel', 'source', 'mode', 'action', 'hlc', 'version',
  'locale', 'lang', 'language', 'timezone', 'tz', 'category', 'subcategory', 'brand', 'color', 'size', 'sizedim', 'sizevalue',
  'packaging', 'variant', 'plan', 'tier', 'level', 'severity', 'priority', 'platform', 'mime', 'feature', 'op', 'field', 'entity',
  'step', 'province', 'provider', 'service', 'tool', 'alg', 'icon', 'badge', 'hash', 'prevhash', 'digest', 'checksum', 'sha256',
  'barcode', 'gtin', 'gtin14', 'sku', 'serial', 'series', 'currencycode', 'invoicenumber', 'receiptno', 'docno', 'billno', 'accent']);
const ID_SUFFIX = /(id|ids|uid)$/;
const KEEP_SUFFIX = /(hlc|status|type|kind|state|mode|method|role|scope|version|lang|currency|unit|at|date|day)$/;
/** ستون‌هایی که نامِ «code» دارند ولی واژهٔ سامانه‌اند */
const KEEP_COLUMN = new Set(['plans.code', 'server_errors.code', 'stock_transfer_steps.code', 'order_pick_events.code', 'notice_templates.key']);
const SECRET = /(pass(word|wd)?|secret|token|privkey|private|seed|salt|nonce|otp|cipher|sealed|signature|cookie|credential|recovery|p256dh|endpoint|webhook|accesscode|enc$|^sig$|^auth$|^pin$|pinhash|codehash|keyhash|tokenhash|tickethash|apikey|readkey|devicekey|clientkey)/;
const CODE = /code$/;
const PII = /(name|names|phone|phone2|phonenumber|mobile|tel|whatsapp|telegram|email|address|street|house|door|district|city|area|landmark|location|note|notes|reason|comment|message|body|text|title|description|caption|label|nick|contact|holder|tagline|reply|review|problem|detail|details|input|visitor|url|link|error|warnings|customer|supplier|sender|reporter|payee|iban|national|tazkira|passport)$/;
const GEO = /^(lat|lng|lon|latitude|longitude)$|(lat|lng)$/;

const cache = new Map();
/** گونهٔ یک نام: 'keep' | 'pii' | 'secret' | 'code' | 'geo' | 'neutral' */
function classify(name, table = '') {
  if (table && KEEP_COLUMN.has(`${table}.${name}`)) return 'keep';
  const hit = cache.get(name);
  if (hit) return hit;
  const n = norm(name);
  let c = 'neutral';
  if (PII_EXACT.has(n)) c = 'pii';
  else if (GEO.test(n)) c = 'geo';
  else if (KEEP_EXACT.has(n) || ID_SUFFIX.test(n)) c = 'keep';
  else if (SECRET.test(n)) c = 'secret';
  else if (KEEP_SUFFIX.test(n)) c = 'keep';
  else if (CODE.test(n)) c = 'code';
  else if (PII.test(n)) c = 'pii';
  cache.set(name, c);
  return c;
}

/* ───────────── شکلِ مقدار ───────────── */

const ISO_DATE = /^\d{4}-\d{2}-\d{2}([T ][\d:.]+(Z|[+-]\d{2}:?\d{2})?)?$/;
const NUMBERISH = /^-?\d+(\.\d+)?$/;
const WORD = /^[a-z][a-z0-9_.:\-/]{0,47}$/;
const IDISH = /^[0-9A-Za-z_\-:.]{8,96}$/;
const digitsOf = (s) => String(s).replace(/[^0-9]/g, '');
/** تلفن‌وار: ۹ تا ۱۵ رقم با «+»/«00»/«07»/«09»/«93» در آغاز، یا ۹ رقمِ «7…» */
function phoneish(s) {
  const t = String(s).trim();
  if (!/^\+?[\d\s\-()]{9,22}$/.test(t)) return false;
  const d = digitsOf(t);
  if (d.length < 9 || d.length > 15) return false;
  return t.startsWith('+') || d.startsWith('00') || (d.startsWith('07') && d.length === 10) || (d.startsWith('09') && d.length === 11)
    || (d.startsWith('93') && d.length === 11) || (d.startsWith('7') && d.length === 9);
}
/** مقدارِ ستون/کلیدِ ناشناخته‌ای که می‌ماند */
function structural(s) {
  const t = String(s);
  if (t === '' || ISO_DATE.test(t) || t === 'true' || t === 'false') return true;
  if (phoneish(t)) return false;
  return NUMBERISH.test(t) || WORD.test(t) || (IDISH.test(t) && !/@/.test(t) && /\d/.test(t));
}
const jsonText = (s) => /^\s*[[{]/.test(s) && (() => { try { return JSON.parse(s); } catch { return undefined; } })();

/* ───────────── جای‌گزینِ هم‌شکل ───────────── */

const FA = 'ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی';
const UPPER = 'ABCDEFGHJKMNPQRSTVWXYZ';
const LOWER = 'abcdefghijkmnopqrstuvwxyz';
const DIGIT = '0123456789';
const HEX = '0123456789abcdef';
const isFa = (c) => /[؀-ۿ]/.test(c) && !/[ً-ٟ٠-٩۰-۹،؛؟٪-٭۔]/.test(c);

function makePseudo(key) {
  const fwd = new Map();   //  «گونه\0مقدار» ⇒ جای‌گزین
  const back = new Map();  //  جای‌گزین ⇒ مقدار (یکتایی)
  let originals = new Set();
  function stream(tag, v, round) {
    let block = 0;
    let buf = Buffer.alloc(0);
    return () => {
      if (!buf.length) buf = createHmac('sha256', key).update(`${tag}\0${round}\0${block++}\0${v}`).digest();
      const b = buf[0]; buf = buf.subarray(1); return b;
    };
  }
  function once(tag, v, round, shape = v) {
    const next = stream(tag, v, round);
    const hex = /^[0-9a-f]+$/.test(shape);
    const keepHead = tag === 'phone' ? Math.min(2, shape.length) : 0;
    let out = '';
    for (const [i, c] of [...shape].entries()) {
      if (i < keepHead) { out += c; continue; }
      const pick = (set) => set[next() % set.length];
      if (/[0-9]/.test(c)) out += pick(DIGIT);
      else if (hex) out += pick(HEX);
      else if (/[a-z]/.test(c)) out += pick(LOWER);
      else if (/[A-Z]/.test(c)) out += pick(UPPER);
      else if (isFa(c)) out += pick(FA);
      else if (/[۰-۹]/.test(c)) out += String.fromCharCode(0x06F0 + (next() % 10));
      else if (/\p{L}/u.test(c)) out += pick(LOWER);
      else out += c;
    }
    return out;
  }
  function pseudo(tag, v) {
    const s = String(v);
    if (s === '') return s;
    const k = `${tag}\0${s}`;
    if (fwd.has(k)) return fwd.get(k);
    let out = s;
    for (let round = 0; round < 50; round++) {
      //  ایمیل: بخشِ پیش از @ هم‌شکل (با دانهٔ کلِ نشانی، تا ali@x و ali@y دو جای‌گزین بگیرند)، دامنهٔ نمونه
      out = tag === 'email' ? `${once('email', s, round, s.split('@')[0] || 'u')}@example.invalid` : once(tag, s, round);
      if (out !== s && !originals.has(out) && !back.has(out)) break;
    }
    fwd.set(k, out);
    back.set(out, s);
    return out;
  }
  return { pseudo, setOriginals(o) { originals = o; } };
}

/* ───────────── یک مقدار (و JSON) ───────────── */

/** گونهٔ نهاییِ یک رشته زیرِ نامِ [cls] ⇒ برچسبِ جای‌گزین یا null (بماند) */
function tagOf(cls, s) {
  if (s === '' || cls === 'keep') return null;
  if (cls === 'geo') return NUMBERISH.test(s) ? null : 'text';
  if (cls === 'neutral' && structural(s)) return null;
  if (/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(s)) return 'email';
  if (phoneish(s)) return 'phone';
  return cls === 'secret' ? 'secret' : cls === 'code' ? 'code' : 'text';
}

/**
 * یک مقدار را (با کلیدهای JSON ِ درونش) می‌پیماید. `visit(tag|null, s)` برای هر رشته؛ `num(cls, n)` برای هر عدد.
 * @returns مقدارِ تازه (بی تغییر ⇒ همان شیء)
 */
function walk(v, cls, visit, num) {
  if (v === null || v === undefined || typeof v === 'boolean') return v;
  if (typeof v === 'number') return cls === 'keep' ? v : num(cls, v);
  if (typeof v === 'string') {
    if (cls === 'keep') return visit(null, v);
    if (cls === 'geo' && NUMBERISH.test(v)) { const g = num(cls, Number(v)); return g === Number(v) ? v : String(g); }
    if (cls !== 'keep') {
      const j = jsonText(v);
      if (j !== undefined && j !== null && typeof j === 'object') {
        const w = walk(j, cls, visit, num);
        return w === j ? v : JSON.stringify(w);
      }
    }
    return visit(tagOf(cls, v), v);
  }
  if (Array.isArray(v)) {
    let changed = false;
    const out = v.map((x) => { const y = walk(x, cls, visit, num); if (y !== x) changed = true; return y; });
    return changed ? out : v;
  }
  if (typeof v === 'object') {
    let changed = false;
    const out = {};
    for (const [k, x] of Object.entries(v)) {
      //  هر کلید گونهٔ خودش را دارد (کلیدِ ناشناخته «خنثی» می‌ماند، نه گونهٔ ستون)؛ زیرِ کلیدِ ساختاری همه می‌ماند
      //  (`field_hlc` ِ همگام‌سازی: نامِ فیلد ⇒ HLC)؛ عضوهای آرایه گونهٔ پدر را
      const y = walk(x, cls === 'keep' ? 'keep' : classify(k), visit, num);
      if (y !== x) changed = true;
      out[k] = y;
    }
    return changed ? out : v;
  }
  return v;
}

/* ───────────── دیتابیس ───────────── */

const TEXTY = new Set(['text', 'character varying', 'character', 'jsonb', 'json', 'ARRAY']);

async function columnsOf(q) {
  const rows = (await q(
    `SELECT c.table_name, c.column_name, c.data_type, c.is_nullable, c.is_generated, c.character_maximum_length
       FROM information_schema.columns c JOIN information_schema.tables t
         ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
      WHERE c.table_schema = 'public' ORDER BY c.table_name, c.ordinal_position`
  )).rows;
  const by = new Map();
  for (const r of rows) {
    if (r.is_generated === 'ALWAYS') continue;
    const cls = classify(r.column_name, r.table_name);
    const geo = cls === 'geo' && ['double precision', 'numeric', 'real'].includes(r.data_type);
    if (!TEXTY.has(r.data_type) && r.data_type !== 'bytea' && r.data_type !== 'inet' && !geo) continue;
    if (cls === 'keep' && !TEXTY.has(r.data_type)) continue;   //  ستونِ متنیِ ساختاری فقط در گذرِ ۱ (سنجشِ نشت) خوانده می‌شود
    if (!by.has(r.table_name)) by.set(r.table_name, []);
    by.get(r.table_name).push({ name: r.column_name, type: r.data_type, cls, nullable: r.is_nullable === 'YES', max: r.character_maximum_length });
  }
  return by;
}

const CAST = { jsonb: '::jsonb', json: '::json', inet: '::inet', ARRAY: '::text[]', 'double precision': '::float8', numeric: '::numeric', real: '::float4' };
const BATCH = 500;

async function eachRow(q, table, cols, fn) {
  const tids = (await q(`SELECT ctid::text AS t FROM ${ident(table)}`)).rows.map((r) => r.t);
  const sel = cols.map((c) => ident(c.name)).join(', ');
  for (let i = 0; i < tids.length; i += BATCH) {
    const rows = (await q(`SELECT ctid::text AS __t, ${sel} FROM ${ident(table)} WHERE ctid = ANY($1::tid[])`, [tids.slice(i, i + BATCH)])).rows;
    for (const r of rows) await fn(r);
  }
  return tids.length;
}

/** عددِ تلفن‌وار زیرِ نامِ نام/تلفن/راز (مثلاً `phone: 700123456`) هم جای‌گزین می‌شود؛ هر عددِ دیگر می‌ماند */
const phoneNumber = (cls, n) => ['pii', 'secret', 'code'].includes(cls) && Number.isSafeInteger(n) && n > 0 && phoneish(String(n));

/**
 * بی‌نام کردنِ همهٔ جدول‌های `public` ِ دیتابیسِ [q] (کپیِ جدا، **یک اتصال**: تراکنشِ هر جدول). دو گذر: ۱) خواندن —
 * مقدارهای جای‌گزین‌شدنی و رشته‌های رقمیِ تلفن. ۲) نوشتن (ماشه‌های کاربر خاموش، هر جدول در یک تراکنش) و در همان گذر سنجشِ
 * نشت: هر مقدارِ ماندگار (شناسه، ستونِ ساختاری) که با مقدارِ جای‌گزین‌شده یا رشتهٔ رقمیِ تلفنی یکی باشد ⇒ `leaks` و
 * صدازننده هیچ فایلی بیرون نمی‌دهد (کپی انداخته می‌شود). جای‌گزین هرگز با مقدارِ واقعیِ دیگری یکی نیست (یکتایی و قیدها).
 * @returns {Promise<{tables:number, rows:number, changed:object, numKeys:string[], leaks:object[]}>}
 */
async function anonymize(q, { key = randomBytes(32) } = {}) {
  const by = await columnsOf(q);
  const all = new Set();        //  هر مقدارِ جای‌گزین‌شدنی (پرهیز از برخورد)
  const leakSet = new Set();    //  همان، ≥ ۵ نویسه و نه واژهٔ رمزیِ لاتین (سنجشِ نشت)
  const phoneDigits = new Set();
  const collect = (tag, s) => {
    if (!tag) return s;
    all.add(s);
    if (s.length >= 5 && !WORD.test(s)) leakSet.add(s);
    if (tag === 'phone') phoneDigits.add(digitsOf(s));
    return s;
  };
  const seeNum = (cls, n) => { if (phoneNumber(cls, n)) { all.add(String(n)); phoneDigits.add(String(n)); } return n; };
  let rows = 0;
  for (const [table, cols] of by) {
    const texty = cols.filter((c) => TEXTY.has(c.type) && c.cls !== 'keep');
    if (!texty.length) continue;
    rows += await eachRow(q, table, texty, async (r) => {
      for (const c of texty) walk(r[c.name], c.cls, collect, seeNum);
    });
  }
  const leakAt = new Map();
  const check = (where, s) => {
    if (s.length < 5) return;
    const runs = s.match(/\d{9,15}/g) || [];
    if (leakSet.has(s) || runs.some((d) => phoneDigits.has(d))) leakAt.set(where, (leakAt.get(where) || 0) + 1);
  };

  const p = makePseudo(key);
  p.setOriginals(all);
  const changed = { text: 0, phone: 0, email: 0, code: 0, secret: 0, geo: 0, bytea: 0, inet: 0 };
  let where = '';
  const visit = (tag, s) => { if (!tag) { check(where, s); return s; } changed[tag]++; return p.pseudo(tag, s); };
  const num = (cls, n) => {
    if (phoneNumber(cls, n)) { changed.phone++; return Number(p.pseudo('phone', String(n))); }
    if (cls !== 'geo' || !Number.isFinite(n)) return n;
    const g = Math.round(n * 10) / 10;
    if (g !== n) changed.geo++;
    return g;
  };
  const numKeys = new Set();   //  کلیدهای دفتر که عددشان عوض شد (اثرِ انگشت فقط همین‌ها را جدا می‌بیند)
  for (const [table, cols] of by) {
    await q('BEGIN');
    try {
      await q(`ALTER TABLE ${ident(table)} DISABLE TRIGGER USER`);
      const blob = cols.filter((c) => c.type === 'bytea');
      if (blob.length) {
        const set = blob.map((c) => `${ident(c.name)} = ${c.nullable ? 'NULL' : "decode('', 'hex')"}`).join(', ');
        const r = await q(`UPDATE ${ident(table)} SET ${set} WHERE ${blob.map((c) => `octet_length(${ident(c.name)}) > 0`).join(' OR ')}`);
        changed.bytea += Number(r.rowCount || r.affectedRows || 0);
      }
      const rest = cols.filter((c) => c.type !== 'bytea');
      if (rest.length) {
        await eachRow(q, table, rest, async (r) => {
          const sets = [];
          const vals = [];
          for (const c of rest) {
            const v = r[c.name];
            if (v === null || v === undefined) continue;
            where = `${table}.${c.name}`;
            if (c.cls === 'keep') { walk(v, 'keep', visit, num); continue; }   //  ساختاری: فقط سنجشِ نشت
            let w;
            if (c.type === 'inet') w = `192.0.2.${1 + (createHmac('sha256', key).update(String(v)).digest()[0] % 254)}`;
            else if (!TEXTY.has(c.type)) w = num(c.cls, Number(v));
            else w = walk(v, c.cls, visit, num);
            if (w === v || (!TEXTY.has(c.type) && c.type !== 'inet' && w === Number(v))) continue;
            if (c.type === 'inet') changed.inet++;
            if (c.type === 'jsonb' && w && typeof w === 'object' && !Array.isArray(w)) {
              for (const k of Object.keys(w)) if (typeof v[k] === 'number' && w[k] !== v[k]) numKeys.add(k);
            }
            if (typeof w === 'string' && c.max && w.length > c.max) w = w.slice(0, c.max);
            vals.push(['jsonb', 'json'].includes(c.type) ? JSON.stringify(w) : w);
            sets.push(`${ident(c.name)} = $${vals.length}${CAST[c.type] || ''}`);
          }
          if (!sets.length) return;
          vals.push(r.__t);
          await q(`UPDATE ${ident(table)} SET ${sets.join(', ')} WHERE ctid = $${vals.length}::tid`, vals);
        });
      }
      await q(`ALTER TABLE ${ident(table)} ENABLE TRIGGER USER`);
      await q('COMMIT');
    } catch (err) {
      await q('ROLLBACK').catch(() => {});
      err.message = `${table}: ${err.message}`;
      throw err;
    }
  }
  const leaks = [...leakAt].map(([at, count]) => ({ where: at, count }));
  return { tables: by.size, rows, changed, numKeys: [...numKeys].sort(), leaks };
}

/** تفاوت‌های اثرِ انگشت (`restore-drill.compare` ⇒ diffs) که فقط همان کلیدهای عددِ عوض‌شده‌اند (مختصات، تلفنِ عددی) */
function onlyNumKeys(diffs, numKeys) {
  const g = new Set(numKeys);
  return diffs.every((d) => d.part === 'ledger' && g.has(String(d.key).split('.').pop()));
}

/** ⛔ هدف نباید دیتابیسِ زنده باشد */
function assertNotLive(url) {
  const live = require('../config').db.url;
  if (!url || String(url) === String(live)) throw new Error('بی‌نام‌کننده فقط روی کپیِ جدا کار می‌کند — هدف همان دیتابیسِ زنده است');
}

module.exports = { anonymize, classify, phoneish, structural, makePseudo, walk, onlyNumKeys, assertNotLive };
