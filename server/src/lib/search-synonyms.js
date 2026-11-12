'use strict';
/**
 * ۴-۱ «ب» (تصمیمِ ۱۴۰۵/۰۸/۲۰؛ POS-04، WEB-05) — فهرستِ مرکزیِ مترادف برای جست‌وجو.
 *
 * یک فهرست برای همهٔ دکان‌ها که صاحبِ پلتفرم در پنلِ مرکزی نگه می‌دارد (ریپوی server، `console-synonyms.js`، ۱.۵۰.۸۹).
 * شکلش همان است که هستهٔ جست‌وجو می‌پذیرد (`text-core.matches(text, q, synonyms)`، همتای `TextNormalizer.kt`):
 *   { version, updatedAt, groups: [["بوره","شکر"], …] }
 * کوچک است و یک‌جا عوض می‌شود ⇒ یک سندِ JSON در `app_config` (`search_synonyms`)، همان الگوی `lib/holidays.js`، بی جدولِ تازه.
 * `search_synonyms` در فهرستِ سفیدِ `PATCH /api/admin/config` نیست ⇒ راهِ نوشتنِ بی‌سنجش ندارد. ⛔ هیچ فرهنگِ ثابتی در کد نیست.
 *
 * ⛔ سنجش همان سنجشِ پنل پیش از پل (`validateSynonyms`): تا ۵۰۰ گروه، هر گروه ۲ تا ۲۰ واژه، هر واژه ۱ تا ۶۰ نویسه پس از
 *    برداشتنِ نویسه‌های کنترلی، و **یک واژه فقط در یک گروه** با کلیدِ یک‌شکلِ `synonymKey` («ي/ى/ی»، «ك/ک»، «ة/ه»، الفِ
 *    همزه‌دار، اعراب و کشیده، نیم‌فاصله و نشانه‌های جهت، رقمِ فارسی/عربی، بزرگ/کوچک). گروهِ بد کلِ درخواست را رد می‌کند
 *    (‎400 synonym_invalid‎)، نه نادیده گرفتنِ بی‌صدا. تکرارِ یک واژه در **همان** گروه یک بار نگه داشته می‌شود.
 * ⛔ نوشتن فقط با `version` ِ فعلی (قفلِ تراکنشیِ همین کلید) ⇒ نسخهٔ کهنه ‎409 synonyms_version‎؛ همان فهرست ⇒ `unchanged`،
 *    بی نسخهٔ تازه و بی سابقهٔ دوم. سابقهٔ `admin.synonyms_updated` در همان تراکنش.
 *
 * جست‌وجو: ویترین با پرچمِ خاموشِ دکانِ `shop.storefront.synonyms` (روی `shop.storefront.search_core`) همین گروه‌ها را با
 * همان قاعدهٔ `text-core.matches` در SQL می‌گیرد (`storefront-search.searchWhere`). صندوقِ برنامه‌ها (POS-04) هنوز نمی‌گیرد.
 */
const { one, tx, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');
const textCore = require('./text-core');
const audit = require('./audit');

const KEY = 'search_synonyms';
const GROUPS_MAX = 500;
const TERMS_MAX = 20;
const TERM_MAX = 60;
const FLAG = 'shop.storefront.synonyms';
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f]/g;
const EMPTY = Object.freeze({ version: 0, updatedAt: 0, groups: Object.freeze([]) });

/**
 * کلیدِ «همان واژه» — فقط برای سنجشِ تکرار؛ متنِ ذخیره‌شده همان است که آمد. همان `synonymKey` ِ پنل، با یک فرق:
 * رقمِ عربی (٠–٩) پیش از برداشتنِ اعراب لاتین می‌شود (در پنل بازهٔ اعراب ‎U+064B–U+0670‎ آن را پاک می‌کند).
 */
function synonymKey(s) {
  return String(s || '')
    .normalize('NFKC')
    .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/ة/g, 'ه')
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
    .replace(/[ً-ٰـ]/g, '')            //  اعراب و کشیده
    .replace(/[‌‍‎‏]/g, '')       //  نیم‌فاصله و نشانه‌های جهت
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

const invalid = (message) => badRequest(message, 'synonym_invalid');

/** گروه‌های ورودی ⇒ گروه‌های پاک (همان ترتیب)؛ هر گروهِ نادرست ⇒ ‎400 synonym_invalid‎. */
function validate(groups) {
  if (!Array.isArray(groups)) throw invalid('فهرست باید آرایه‌ای از گروه‌ها باشد');
  if (groups.length > GROUPS_MAX) throw invalid(`بیش از ${GROUPS_MAX} گروه`);
  const owner = new Map();   //  کلیدِ واژه ⇒ شمارهٔ گروه
  return groups.map((g, i) => {
    if (!Array.isArray(g)) throw invalid(`گروهِ ${i + 1} باید فهرستی از واژه‌ها باشد`);
    if (g.length > TERMS_MAX) throw invalid(`گروهِ ${i + 1}: بیش از ${TERMS_MAX} واژه`);
    const out = [];
    const local = new Set();
    for (const t of g) {
      if (typeof t !== 'string') throw invalid(`گروهِ ${i + 1}: هر واژه باید متن باشد`);
      const s = t.replace(CONTROL, '').replace(/\s+/g, ' ').trim();
      if (!s) continue;
      if (s.length > TERM_MAX) throw invalid(`گروهِ ${i + 1}: «${s.slice(0, 20)}…» بلندتر از ${TERM_MAX} نویسه است`);
      const k = synonymKey(s);
      if (!k || local.has(k)) continue;
      if (owner.has(k)) throw invalid(`«${s}» هم در گروهِ ${owner.get(k) + 1} و هم در گروهِ ${i + 1} آمده — هر واژه فقط در یک گروه`);
      local.add(k);
      out.push(s);
    }
    if (out.length < 2) throw invalid(`گروهِ ${i + 1}: دست‌کم دو واژهٔ جدا لازم است`);
    for (const k of local) owner.set(k, i);
    return out;
  });
}

/** سندِ ذخیره‌شده ⇒ سندِ پاک؛ سندِ خراب مثلِ «هنوز فهرستی نیست»، گروهِ خراب کنار می‌رود (نوشتهٔ دستی، بازگردانی). */
function parse(raw) {
  if (!raw) return EMPTY;
  let d;
  try { d = JSON.parse(raw); } catch { return EMPTY; }
  if (!d || typeof d !== 'object' || !Array.isArray(d.groups) || !Number.isSafeInteger(d.version) || d.version < 0) return EMPTY;
  const groups = [];
  for (const g of d.groups.slice(0, GROUPS_MAX)) {
    if (!Array.isArray(g)) continue;
    const terms = g.slice(0, TERMS_MAX).filter((t) => typeof t === 'string' && t && t.length <= TERM_MAX);
    if (terms.length >= 2) groups.push(terms);
  }
  return { version: d.version, updatedAt: Number(d.updatedAt) || 0, groups };
}

async function get() {
  const r = await one('SELECT value FROM app_config WHERE key=$1', [KEY]);
  return parse(r && r.value);
}

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/**
 * جایگزینیِ کلِ فهرست. ⇒ { version, updatedAt, groups, unchanged }
 * `meta` = { userId, reason, ip } (دلیل را درِ مسیر با `requireReason` می‌سنجد).
 */
async function replace(list, expectedVersion, meta = {}) {
  const groups = validate(list);
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) {
    throw badRequest('نسخهٔ همان فهرستی که دیدید (version) لازم است', 'bad_version');
  }
  return tx(async (c) => {
    //  ⛔ قفل پیش از خواندن: `FOR UPDATE` روی ردیفی که هنوز نیست چیزی را قفل نمی‌کند (همان تلهٔ `holidays.replace`)
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`app-config:${KEY}`]);
    const r = await c.query('SELECT value FROM app_config WHERE key=$1 FOR UPDATE', [KEY]);
    const cur = parse(r.rows[0] && r.rows[0].value);
    if (same(cur.groups, groups)) return { ...cur, unchanged: true };
    if (cur.version !== expectedVersion) {
      throw conflict('فهرستِ مترادف در این میان عوض شده است؛ دوباره بخوانید و بنویسید', 'synonyms_version');
    }
    const doc = { version: cur.version + 1, updatedAt: now(), groups };
    await c.query(
      `INSERT INTO app_config (key,value,updated_at) VALUES ($1,$2,$3)
       ON CONFLICT (key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at`,
      [KEY, JSON.stringify(doc), doc.updatedAt],
    );
    const before = new Set(cur.groups.flat().map(synonymKey));
    const after = new Set(groups.flat().map(synonymKey));
    await audit.log({
      actorType: 'admin', userId: meta.userId || '', action: 'admin.synonyms_updated', ip: meta.ip || '',
      targetType: 'app_config', targetId: KEY,
      detail: {
        reason: meta.reason || '', from: cur.version, version: doc.version, groups: groups.length, terms: after.size,
        added: [...after].filter((k) => !before.has(k)).length, removed: [...before].filter((k) => !after.has(k)).length,
      },
    }, { client: c });
    return { ...doc, unchanged: false };
  });
}

/* ── جست‌وجو ───────────────────────────────────────────────────────── */

let indexCache = { stamp: '', index: null };

/** نمایهٔ «کلیدِ مقایسه ⇒ کلیدهای همان گروه‌ها» — یک بار برای هر نسخه. */
function indexOf(doc) {
  const stamp = `${doc.version}:${doc.updatedAt}:${doc.groups.length}`;
  if (indexCache.stamp === stamp && indexCache.index) return indexCache.index;
  const index = new Map();
  for (const g of doc.groups) {
    const keys = [...new Set(g.map((t) => textCore.compareKey(t)).filter(Boolean))];
    for (const k of keys) index.set(k, [...new Set([...(index.get(k) || []), ...keys])]);
  }
  indexCache = { stamp, index };
  return index;
}

/**
 * همان `alternatives` ِ `text-core.matches`: خودِ کلید و کلیدِ مقایسهٔ هر واژهٔ هر گروهی که آن را دارد (خودش اول).
 * سقف `TERMS_MAX` تا پرس‌وجوی بزرگ SQL ِ بی‌اندازه نسازد (فهرستِ سنجیده هر واژه را فقط در یک گروه دارد).
 */
function alternativesOf(index, key) {
  const out = [key];
  for (const k of index.get(key) || []) if (!out.includes(k) && out.length <= TERMS_MAX) out.push(k);
  return out;
}

/**
 * گروه‌های جست‌وجوی ویترینِ یک دکان: پرچمِ `shop.storefront.synonyms` روشن و فهرستی هست ⇒ { alternatives(key) }؛
 * وگرنه null (همان جست‌وجوی بی مترادف، بایت‌به‌بایت).
 */
async function forShop(shopId) {
  if (!(await require('./orders').flagOf(String(shopId), FLAG))) return null;
  const doc = await get();
  if (!doc.groups.length) return null;
  const index = indexOf(doc);
  return { alternatives: (key) => alternativesOf(index, key) };
}

module.exports = { KEY, FLAG, GROUPS_MAX, TERMS_MAX, TERM_MAX, synonymKey, validate, parse, get, replace, forShop, alternativesOf, indexOf };
