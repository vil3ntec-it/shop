'use strict';
/**
 * S70 (§۱۹.۱۲) — «دو فروشگاه با واحدهای متفاوت»: مقایسهٔ قیمتِ دانه/بسته/کارتن فقط با واحدِ
 * قابلِ مقایسه و تعدادِ داخلِ بستهٔ روشن. منطقِ خالص (بی پایگاه)؛ مصرف در «همین کالا در دکان‌های
 * دیگر» (`lib/market-details.js`). آزمون: `test/market-units.test.js`، `test/market-same-units.test.js`.
 *
 *   اندازه ── فقط وقتی بی حدس روشن است: واحدِ پایه (عدد/دانه/درجن، کیلو/گرم، لیتر/میلی‌لیتر،
 *            متر/سانتی‌متر) یا بسته با **یک** عددِ روشن و واحدِ پایه پس از آن («کارتن ۲۴ عددی»،
 *            «بستهٔ ۶ تایی»، «کیسه ۵۰ کیلویی»، «شیشه ۵۰۰ گرمی»، «قوطی 330ml»).
 *   ⛔ نه ── «بسته»/«کارتن»/«شل» ِ بی عدد؛ دو عدد («۲۴ تایی ۳۳۰ میلی‌لیتری»، «6x330ml»)؛ جداکنندهٔ مبهم
 *            («1,000» هزار است یا اعشار؟)؛ واحدِ محلی که اندازه‌اش از شهری به شهری فرق دارد (سیر، پاو،
 *            من، خروار)؛ دانهٔ اعشاری؛ اندازهٔ صفر ⇒ `null`، هرگز حدس.
 *   قیمت ── `priceMinor` ِ واحدِ کالا ⇒ قیمتِ یک پایه (یک دانه، یک کیلو، یک لیتر، یک متر)، گرد به
 *            نزدیک‌ترین یک‌صدم؛ کمتر از یک یک‌صدم ⇒ `null` (نه صفرِ گمراه‌کننده).
 *   ارزان‌تر ─ فقط میانِ پیشنهادهای **همان پایه** و فقط با دست‌کم دو تا؛ پیشنهادِ بی اندازهٔ روشن هرگز
 *            «ارزان‌ترین» نیست و با هیچ پیشنهادی سنجیده نمی‌شود.
 *   از راهِ دیگر ─ واحدی که خودش اندازه ندارد («شیشه»، «بسته»، «کارتن») اندازه را از «واحدِ فروشِ دوم»
 *            (`saleUnit`: یک واحدِ فروش = ضریب × واحدِ کالا، INV-10) یا «بسته‌ها» (`packs`: نام و اندازه به
 *            هزارمِ واحدِ کالا، PRD-04) می‌گیرد، فقط وقتی **نامِ آن‌ها** اندازهٔ روشن دارد و نسبت دقیق است:
 *            «کارتن ۱۲ عددی» = ۱۲ شیشه ⇒ شیشه یک دانه. ⛔ نسبتِ نادقیق و دانهٔ اعشاری ⇒ آن راه کنار؛ دو اندازهٔ
 *            ناهم‌خوان در یک پایه ⇒ `null` (کدام راست است؟)؛ اندازهٔ خودِ واحد بر پایهٔ دیگر جلو؛ بی آن، دو
 *            پایهٔ گوناگون ⇒ `null`. قیمتِ جدای بسته هرگز (پیشنهادِ آنلاین به همان واحدِ کالاست).
 */

/** ترتیبِ ثابتِ پایه‌ها در پاسخ */
const BASES = Object.freeze(['piece', 'kg', 'l', 'm']);
const MAX_UNIT = 40;
const MAX_NUMBER = 100000;
/** همان `PackBarcodes.MAX_PACKS` و `MAX_SIZE_MILLI` (یک میلیون واحد) */
const MAX_PACKS = 5;
const MAX_REL_MILLI = 1_000_000_000;

//  واژه (یکدست‌شده، بی فاصله و نیم‌فاصله) ⇒ [پایه، اندازه به هزارمِ پایه، فقط با عدد؟]
const WORDS = new Map([
  ...['عدد', 'دانه', 'piece', 'pieces', 'pcs', 'pc'].map((w) => [w, ['piece', 1000]]),
  //  «تا» و تک‌حرف‌های لاتین (g، l، m) فقط پس از عدد: «L» و «M» ِ تنها اندازهٔ پوشاک هم هست
  ['تا', ['piece', 1000, true]],
  ...['درجن', 'دوجین', 'dozen'].map((w) => [w, ['piece', 12000]]),
  ...['کیلو', 'کیلوگرم', 'کیلوگرام', 'kg', 'kgs', 'kilo', 'kilogram'].map((w) => [w, ['kg', 1000]]),
  ...['گرم', 'گرام', 'gr', 'gram', 'grams'].map((w) => [w, ['kg', 1]]),
  ['g', ['kg', 1, true]],
  ...['لیتر', 'lit', 'liter', 'litre', 'ltr'].map((w) => [w, ['l', 1000]]),
  ['l', ['l', 1000, true]],
  ...['میلیلیتر', 'ml', 'سیسی', 'cc'].map((w) => [w, ['l', 1]]),
  ...['متر', 'meter', 'metre'].map((w) => [w, ['m', 1000]]),
  ['m', ['m', 1000, true]],
  ...['سانتیمتر', 'سانت', 'cm'].map((w) => [w, ['m', 10]]),
]);
//  پسوندِ صفت پس از عدد: «عددی»، «تایی»، «کیلویی»، «گرمی»
const SUFFIXES = ['یی', 'ای', 'ی'];
const PACK = /^([^\d.]*?)\s*(\d+(?:\.\d+)?)\s*([^\d.]+)$/;

/** رقمِ فارسی/عربی ⇒ لاتین، ی/ک ِ عربی، بی اعراب/کشیده/نیم‌فاصله، حروفِ کوچک، یک فاصله. */
function norm(raw) {
  return String(raw)
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
    .replace(/٫/g, '.')
    .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک')
    .replace(/[ً-ٰٟـ‌‍]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

function wordOf(raw, withNumber) {
  const w = raw.replace(/ /g, '');
  const tries = [w, ...(withNumber ? SUFFIXES.filter((s) => w.endsWith(s)).map((s) => w.slice(0, -s.length)) : [])];
  for (const t of tries) {
    const hit = WORDS.get(t);
    if (hit && (withNumber || !hit[2])) return { base: hit[0], milli: hit[1] };
  }
  return null;
}

/** اندازهٔ واحدِ کالا ⇒ `{ base, milli }` (هزارمِ پایه)، یا `null` وقتی روشن نیست. */
function sizeOf(unit) {
  if (typeof unit !== 'string') return null;
  const s = norm(unit);
  if (!s || s.length > MAX_UNIT || /[,٬]/.test(s)) return null;
  const bare = wordOf(s, false);
  if (bare) return bare;
  const m = PACK.exec(s);
  if (!m) return null;
  const n = Number(m[2]);
  const w = wordOf(m[3].trim(), true);
  if (!w || !(n > 0) || n > MAX_NUMBER) return null;
  if (w.base === 'piece' && !Number.isInteger(n)) return null;
  const milli = Math.round(n * w.milli * 1000) / 1000;
  return milli > 0 ? { base: w.base, milli } : null;
}

/**
 * اندازهٔ یک واحدِ کالا از واحدِ دیگری به نامِ [name] که [relMilli] هزارمِ واحدِ کالاست ⇒ `{ base, milli }` یا `null`.
 * یک واحدِ کالا = اندازهٔ [name] × ۱۰۰۰ ÷ [relMilli]؛ فقط دقیق تا هزارمِ `milli` و برای دانه فقط عددِ صحیح.
 */
function through(name, relMilli) {
  if (!Number.isSafeInteger(relMilli) || relMilli <= 0 || relMilli > MAX_REL_MILLI) return null;
  const s = sizeOf(name);
  if (!s) return null;
  const micro = Math.round(s.milli * 1000) * 1000;       //  اندازهٔ نام به میلیونیمِ پایه × ۱۰۰۰، عددِ صحیح
  if (!Number.isSafeInteger(micro) || micro % relMilli !== 0) return null;
  const milli = micro / relMilli / 1000;
  if (!(milli > 0) || (s.base === 'piece' && milli % 1000 !== 0)) return null;
  return { base: s.base, milli };
}

/**
 * اندازهٔ واحدِ کالا با `saleUnit` (`{ unit, factorMilli }`) و `packs` (`[{ name, sizeMilli }]`) ⇒ `{ base, milli }` یا
 * `null` — همان `sizeOf` وقتی چیزِ دیگری نیست. قاعده سرِ همین پرونده («از راهِ دیگر»).
 */
function sizeOfItem(unit, { saleUnit = null, packs = [] } = {}) {
  const own = sizeOf(unit);
  const ways = [];
  if (saleUnit && typeof saleUnit === 'object') ways.push(through(saleUnit.unit, saleUnit.factorMilli));
  for (const p of (Array.isArray(packs) ? packs : []).slice(0, MAX_PACKS)) {
    if (p && typeof p === 'object') ways.push(through(p.name, p.sizeMilli));
  }
  const all = [own, ...ways].filter(Boolean);
  if (!all.length) return null;
  const pick = own || all[0];
  if (all.some((x) => x.base === pick.base && x.milli !== pick.milli)) return null;
  if (!own && all.some((x) => x.base !== pick.base)) return null;
  return { base: pick.base, milli: pick.milli };
}

/** قیمتِ یک پایه از قیمتِ واحدِ کالا (و `more` = `{ saleUnit, packs }` ِ `sizeOfItem`) ⇒ `{ base, priceMinor }` یا `null`. */
function perUnit(unit, priceMinor, more) {
  if (!Number.isSafeInteger(priceMinor) || priceMinor <= 0) return null;
  const s = more ? sizeOfItem(unit, more) : sizeOf(unit);
  if (!s) return null;
  const per = Math.round((priceMinor * 1000) / s.milli);
  return Number.isSafeInteger(per) && per >= 1 ? { base: s.base, priceMinor: per } : null;
}

/** گروه‌های هم‌سنج (هر پایه با دست‌کم دو پیشنهاد) ⇒ `[{ base, lowestMinor, count }]` به ترتیبِ BASES. */
function compare(perUnits) {
  const by = new Map();
  for (const p of perUnits) {
    if (!p) continue;
    const g = by.get(p.base) || { base: p.base, lowestMinor: p.priceMinor, count: 0 };
    g.count += 1;
    g.lowestMinor = Math.min(g.lowestMinor, p.priceMinor);
    by.set(p.base, g);
  }
  return [...by.values()].filter((g) => g.count >= 2).sort((a, b) => BASES.indexOf(a.base) - BASES.indexOf(b.base));
}

/** همین پیشنهاد کمترین قیمتِ یک پایه را در گروهِ هم‌سنجِ خودش دارد؟ */
function cheapest(per, groups) {
  return Boolean(per) && groups.some((g) => g.base === per.base && g.lowestMinor === per.priceMinor);
}

/**
 * ترتیبِ کامل (لغت‌نامه‌ای) برای `{ per, priceMinor }`: هم‌سنج‌ها اول، به پایه و قیمتِ یک پایه؛
 * بقیه پس از آن به قیمتِ خام (فقط برای ترتیبِ پایدار، نه ادعای ارزانی).
 */
function order(a, b) {
  if (Boolean(a.per) !== Boolean(b.per)) return a.per ? -1 : 1;
  if (a.per) {
    const x = BASES.indexOf(a.per.base) - BASES.indexOf(b.per.base);
    if (x) return x;
    if (a.per.priceMinor !== b.per.priceMinor) return a.per.priceMinor - b.per.priceMinor;
  }
  return a.priceMinor - b.priceMinor;
}

module.exports = { BASES, sizeOf, sizeOfItem, perUnit, compare, cheapest, order };
