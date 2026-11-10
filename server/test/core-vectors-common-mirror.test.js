'use strict';
/**
 * آینهٔ JS ِ `core/src/jvmTest/…/CoreVectorsCommonParityTest.kt` — سطرهای بردارهای مرجع که `CoreVectorsCommonTest`
 * (commonTest، آیفون) در خودِ پرونده نوشته، با JSON ِ امروزِ `core/test-vectors` و `license/test-vectors` سطربه‌سطر؛
 * و هر بخشِ این پنج JSON یا در فهرستِ mirrored/notMirrored ِ همان آزمون است یا سرخ. بردارِ عوض‌شده (کارِ سرور/وب)
 * بی هم‌خوان کردنِ CoreVectorsCommonTest همین‌جا در CI ِ سرور سرخ می‌شود، نه دیر در CI ِ کاتلین.
 * همان regex ها و همان ستون‌ها، تابع‌به‌تابع (money، receipt، calendar، localeFormat، textNormalizer، sections)؛
 * فهرستِ بخش‌ها و الگوهای S/I/D از خودِ پروندهٔ کاتلین خوانده می‌شوند. Long با BigInt (عددِ خامِ JSON)،
 * Double با Number؛ نوع هم سنجیده می‌شود (Long ≠ Double، همان برابریِ List ِ کاتلین). درمانِ سرخ:
 * CoreVectorsCommonTest را با JSON هم‌خوان کن (یا بخشِ تازه را در sections ِ آزمونِ کاتلین بنویس)، نه این آینه را.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const KT = require('./kt-source');

const PARITY = 'core/src/jvmTest/kotlin/ir/vil3ntec/tohid/core/CoreVectorsCommonParityTest.kt';
const COMMON = 'core/src/commonTest/kotlin/ir/vil3ntec/tohid/core/CoreVectorsCommonTest.kt';
const parity = KT.read(PARITY);
const source = KT.read(COMMON);

/** `const val NAME = "…"` ِ companion ِ آزمون (S، I، D)؛ SN همان «(?:$S|null)» */
const kconst = (name) => KT.literal(parity, KT.at(parity, `const val ${name} = `, 'CoreVectorsCommonParityTest.kt'), name).value;
const S = kconst('S');
const I = kconst('I');
const D = kconst('D');
KT.at(parity, 'const val SN = "(?:$S|null)"', 'CoreVectorsCommonParityTest.kt');
const SN = `(?:${S}|null)`;

/* ------------- مقدارهای نوع‌دار: Long = BigInt، Double = { d }، رشته، null، فهرست، Map ------------- */

const dbl = (x) => ({ d: Number(x) });
function canon(v) {
  if (v === null) return 'null';
  if (typeof v === 'bigint') return `L${v}`;
  if (typeof v === 'string') return JSON.stringify(v);
  if (Array.isArray(v)) return `[${v.map(canon).join(',')}]`;
  if (v instanceof Map) return `{${[...v].map(([k, x]) => `${canon(k)}=${canon(x)}`).sort().join(',')}}`;
  if (typeof v === 'object' && typeof v.d === 'number') return `D${Object.is(v.d, -0) ? '-0' : v.d}`;
  throw new Error(`مقدارِ ناشناخته: ${v}`);
}

/** JSON با متنِ خامِ هر عدد (Long ِ بزرگ‌تر از 2^53 گرد نشود) */
const json = (rel) => JSON.parse(KT.read(rel), (k, v, ctx) => (v !== null && typeof v !== 'object' ? { p: v, raw: ctx.source } : v));
const get = (o, key) => {
  if (o === null || typeof o !== 'object' || !(key in o)) throw new Error(`کلیدِ ${key} نیست`);
  return o[key];
};
/** `jsonPrimitive.content` (JsonNull ⇒ null) */
const text = (e) => {
  if (e === null) return null;
  if (!e || !('p' in e)) throw new Error(`JsonPrimitive نیست: ${JSON.stringify(e)}`);
  return typeof e.p === 'string' ? e.p : e.raw;
};
const long = (e) => BigInt(text(e));
const s = (o, k) => text(get(o, k));
const l = (o, k) => long(get(o, k));
const d = (o, k) => dbl(text(get(o, k)));
const longs = (o, k) => get(o, k).map(long);
const list = (o, k) => get(o, k);

/** همان `kt()` ِ آزمون: رشتهٔ کاتلین با گریز یا null */
function kt(lit) {
  if (lit === 'null') return null;
  assert.ok(lit.length >= 2 && lit[0] === '"' && lit[lit.length - 1] === '"', `رشتهٔ Kotlin نیست: ${lit}`);
  const x = lit.slice(1, -1);
  let out = '';
  let i = 0;
  while (i < x.length) {
    const c = x[i++];
    if (c !== '\\') { out += c; continue; }
    const e = x[i++];
    if (e === 'u') { out += String.fromCharCode(parseInt(x.slice(i, i + 4), 16)); i += 4; } else if (e === 't') out += '\t';
    else if (e === 'n') out += '\n';
    else if (e === 'r') out += '\r';
    else if (e === 'b') out += '\b';
    else if ('"\\\'$'.includes(e)) out += e;
    else assert.fail(`escape ِ ناشناخته \\${e} در ${lit}`);
  }
  return out;
}
const L = (x) => BigInt(x);
const longsIn = (t) => [...t.matchAll(new RegExp(`(${I})L`, 'g'))].map((m) => BigInt(m[1]));

/** بدنهٔ `@Test fun name()` ِ CoreVectorsCommonTest */
function body(name) {
  const m = new RegExp(`\\n  @Test fun ${name}\\(\\) \\{\\n([\\s\\S]*?)\\n  }\\n`).exec(source);
  assert.ok(m, `@Test fun ${name}() در CoreVectorsCommonTest پیدا نشد`);
  return m[1];
}
/** همان `rows`: هر جور (MULTILINE) ⇒ گروه‌ها */
const rows = (b, pattern) => [...b.matchAll(new RegExp(pattern, 'gm'))].map((m) => m.slice(1).map((g) => g ?? ''));
/** هم‌شمار و هم‌ترتیب؛ اختلاف ⇒ نخستین سطرِ ناهم‌خوان */
function same(what, fromJson, fromSource) {
  assert.ok(fromJson.length, `${what}: در JSON سطری نیست`);
  const a = fromJson.map(canon);
  const b = fromSource.map(canon);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if (a[i] !== b[i]) {
      assert.fail(`${what}: CoreVectorsCommonTest با JSON هم‌خوان نیست — سطرِ ${i + 1}: JSON ${a[i]}، پرونده ${b[i]} ` +
        `(JSON ${a.length} سطر، پرونده ${b.length}).`);
    }
  }
}
const re = String.raw;

test('آینهٔ CoreVectorsCommonParityTest.money (license/test-vectors/money-v1.json)', () => {
  const m = json('license/test-vectors/money-v1.json');
  const legacy = get(m, 'fromLegacy');
  const fromLegacy = body('moneyFromLegacy');
  same('money-v1 fromLegacy.money', get(legacy, 'money').map((r) => [dbl(text(r[0])), long(r[1])]),
    rows(fromLegacy, re`assertEquals\((${I})L, Money\.fromLegacy\((${D}), Currency\.AFN\)\.minor, ${S}\)`).map((it) => [dbl(it[1]), L(it[0])]));
  same('money-v1 fromLegacy.qty', get(legacy, 'qty').map((r) => [dbl(text(r[0])), long(r[1])]),
    rows(fromLegacy, re`assertEquals\((${I})L, Qty\.fromLegacy\((${D})\)\.milli, ${S}\)`).map((it) => [dbl(it[1]), L(it[0])]));
  const table = (key, ...columns) => {
    const t = get(m, key);
    const names = get(t, 'columns').map(text);
    const at = columns.map((c) => { const i = names.indexOf(c); assert.ok(i >= 0, `money-v1 ${key}: ستونِ ${c} نیست (${names})`); return i; });
    return get(t, 'rows').map((r) => at.map((i) => long(r[i])));
  };
  const arr = /\n {2}private val lineTotals = longArrayOf\(\n([\s\S]*?)\n {2}\)\n/.exec(source);
  assert.ok(arr, 'lineTotals در CoreVectorsCommonTest پیدا نشد');
  const numbers = [...arr[1].matchAll(new RegExp(I, 'g'))].map((x) => BigInt(x[0]));
  assert.equal(numbers.length % 4, 0, 'lineTotals: شمارِ عددها مضربِ ۴ نیست');
  const chunks = [];
  for (let i = 0; i < numbers.length; i += 4) chunks.push(numbers.slice(i, i + 4));
  same('money-v1 lineTotal', table('lineTotal', 'qtyMilli', 'priceMinor', 'stepMinor', 'minor'), chunks);
  same('money-v1 lineTotalOverflow', table('lineTotalOverflow', 'qtyMilli', 'priceMinor', 'stepMinor'),
    rows(body('lineTotal'), re`assertFailsWith<ArithmeticException> \{ Rounding\.lineTotalMinor\((${I})L, (${I})L, (${I})L\) }`).map((r) => r.map(L)));
  same('money-v1 cashRounding', table('cashRounding', 'totalMinor', 'stepMinor', 'adjustMinor'),
    rows(body('cashRounding'),
      re`assertEquals\((${I})L, Rounding\.cashRounding\(Money\((${I})L, Currency\.AFN\), (${I})L\)\?\.amount\?\.minor \?: 0L, ${S}\)`)
      .map((it) => [L(it[1]), L(it[2]), L(it[0])]));
  same('money-v1 allocateDiscount', list(m, 'allocateDiscount').map((o) => [l(o, 'stepMinor'), l(o, 'discount'), longs(o, 'lines'), longs(o, 'shares')]),
    rows(body('allocateDiscount'),
      re`Currency\.AFN\.withStep\((${I})L\)\.let \{ c -> assertEquals\(listOf<Long>\(([^)]*)\), ` +
        re`Rounding\.allocateDiscount\(Money\((${I})L, c\), listOf\(([^\n]*?)\)\)\.map \{ it\.minor }\) }`)
      .map((it) => [L(it[0]), L(it[2]), longsIn(it[3]), longsIn(it[1])]));
});

test('آینهٔ CoreVectorsCommonParityTest.receipt (core/test-vectors/receipt-v1.json)', () => {
  const r = json('core/test-vectors/receipt-v1.json');
  same('receipt-v1 tags', list(r, 'tags').map((o) => [s(o, 'shopId'), s(o, 'tag')]),
    rows(body('receiptTags'), re`assertEquals\((${S}), ReceiptTokens\.shopTag\((${S})\)\)`).map((it) => [kt(it[1]), kt(it[0])]));
});

test('آینهٔ CoreVectorsCommonParityTest.calendar (core/test-vectors/calendar.json)', () => {
  const c = json('core/test-vectors/calendar.json');
  const b = body('calendar');
  const solar = list(c, 'solar').map((o) => [s(o, 'iso'), ...longs(o, 'solar')]);
  same('calendar solar ⇒ toSolar', solar,
    rows(b, re`assertEquals\(Ymd\((${I}), (${I}), (${I})\), SolarHijri\.toSolar\((${S})\)\)`).map((it) => [kt(it[3]), L(it[0]), L(it[1]), L(it[2])]));
  same('calendar solar ⇒ fromSolar', solar,
    rows(b, re`assertEquals\((${S}), SolarHijri\.fromSolar\((${I}), (${I}), (${I})\)\)`).map((it) => [kt(it[0]), L(it[1]), L(it[2]), L(it[3])]));
  same('calendar fromSolarInvalid', get(c, 'fromSolarInvalid').map((e) => e.map(long)),
    rows(b, re`assertEquals\(null, SolarHijri\.fromSolar\((${I}), (${I}), (${I})\)\)`).map((r) => r.map(L)));
  same('calendar invalidIso', get(c, 'invalidIso').map((e) => [text(e)]),
    rows(b, re`assertEquals\(null, CivilDays\.jdnOfIso\((${S})\)\)`).map((it) => [kt(it[0])]));
  same('calendar lunar', list(c, 'lunar').map((o) => [s(o, 'iso'), l(o, 'adjust'), ...longs(o, 'lunar')]),
    rows(b, re`assertEquals\(Ymd\((${I}), (${I}), (${I})\), LunarHijri\.toLunar\((${S}), (${I})\)\)`)
      .map((it) => [kt(it[3]), L(it[4]), L(it[0]), L(it[1]), L(it[2])]));
  same('calendar formatLunarLong', list(c, 'formatLunarLong').map((o) => [s(o, 'iso'), s(o, 'digits'), l(o, 'adjust'), s(o, 'out')]),
    rows(b, re`assertEquals\((${S}), LunarHijri\.formatLong\((${S}), (${S}), (${I})\)\)`).map((it) => [kt(it[1]), kt(it[2]), L(it[3]), kt(it[0])]));
  same('calendar formatSolarLong', list(c, 'formatSolarLong').map((o) => [s(o, 'iso'), s(o, 'lang'), s(o, 'digits'), s(o, 'out')]),
    rows(b, re`assertEquals\((${S}), SolarHijri\.formatLong\((${S}), (${S}), (${S})\)\)`).map((it) => [kt(it[1]), kt(it[2]), kt(it[3]), kt(it[0])]));
  same('calendar weekday', list(c, 'weekday').map((o) => [s(o, 'iso'), l(o, 'out')]),
    rows(b, re`assertEquals\((${I}), CivilDays\.isoWeekday\((${S})\)\)`).map((it) => [kt(it[1]), L(it[0])]));
  same('calendar addDays', list(c, 'addDays').map((o) => [s(o, 'iso'), l(o, 'n'), s(o, 'out')]),
    rows(b, re`assertEquals\((${S}), CivilDays\.addDays\((${S}), (${I})\)\)`).map((it) => [kt(it[1]), L(it[2]), kt(it[0])]));
  same('calendar weekStart', list(c, 'weekStart').map((o) => {
    const rules = get(o, 'rules');
    return [s(o, 'iso'), new Map(rules === null ? [] : Object.entries(rules).map(([k, v]) => [k, text(v)])), s(o, 'out')];
  }), rows(b, re`assertEquals\((${S}), BusinessDay\.weekStartOf\((${S}), WeekRules\(([^)]*)\)\)\)`).map((r) => {
    const rules = new Map(r[2].split(', ').filter((x) => x.trim()).map((arg) => {
      const at = arg.indexOf(' = ');
      assert.ok(at >= 0, `WeekRules: «${arg}»`);
      return [arg.slice(0, at), arg.slice(at + 3)];
    }));
    return [kt(r[1]), rules, kt(r[0])];
  }));
  same('calendar businessDate', list(c, 'businessDate').map((o) => [s(o, 'local'), l(o, 'dayStart'), s(o, 'out')]),
    rows(b, re`assertEquals\((${SN}), BusinessDay\.businessDate\((${S}), (${I})\)\)`).map((it) => [kt(it[1]), L(it[2]), kt(it[0])]));
  same('calendar businessDateAt', list(c, 'businessDateAt').map((o) => [l(o, 'epochMs'), l(o, 'offset'), l(o, 'dayStart'), s(o, 'out')]),
    rows(b, re`assertEquals\((${SN}), BusinessDay\.businessDateAt\((${I})L, (${I}), (${I})\)\)`).map((it) => [L(it[1]), L(it[2]), L(it[3]), kt(it[0])]));
});

test('آینهٔ CoreVectorsCommonParityTest.localeFormat (core/test-vectors/locale-format.json)', () => {
  const f = json('core/test-vectors/locale-format.json');
  const b = body('localeFormat');
  same('locale-format asciiDigits', list(f, 'asciiDigits').map((o) => [s(o, 'in'), s(o, 'out')]),
    rows(b, re`assertEquals\((${S}), LocaleFormat\.asciiDigits\((${S})\)\)`).map((it) => [kt(it[1]), kt(it[0])]));
  same('locale-format formatAfghani', list(f, 'formatAfghani').map((o) => [d(o, 'amount'), s(o, 'symbol'), s(o, 'digits'), s(o, 'out')]),
    rows(b, re`assertEquals\((${S}), LocaleFormat\.formatAfghani\((${D}), (${S}), (${S})\)\)`).map((it) => [dbl(it[1]), kt(it[2]), kt(it[3]), kt(it[0])]));
  same('locale-format normalizePhone', list(f, 'normalizePhone').map((o) => [s(o, 'in'), s(o, 'out')]),
    rows(b, re`assertEquals\((${SN}), LocaleFormat\.normalizePhone\((${S})\)\)`).map((it) => [kt(it[1]), kt(it[0])]));
  same('locale-format formatPhone', list(f, 'formatPhone').map((o) => [s(o, 'in'), s(o, 'out')]),
    rows(b, re`assertEquals\((${S}), LocaleFormat\.formatPhone\((${S})\)\)`).map((it) => [kt(it[1]), kt(it[0])]));
});

test('آینهٔ CoreVectorsCommonParityTest.textNormalizer (core/test-vectors/text-normalize.json)', () => {
  const t = json('core/test-vectors/text-normalize.json');
  const b = body('textNormalizer');
  for (const key of ['searchKey', 'compareKey']) {
    same(`text-normalize ${key}`, list(t, key).map((o) => [s(o, 'in'), s(o, 'out')]),
      rows(b, re`assertEquals\((${S}), TextNormalizer\.${key}\((${S})\)\)`).map((it) => [kt(it[1]), kt(it[0])]));
  }
  same('text-normalize matches', list(t, 'matches').map((o) => {
    const syn = o.synonyms === undefined || o.synonyms === null ? null : o.synonyms.map((g) => g.map(text));
    return [s(o, 'text'), s(o, 'query'), syn, s(o, 'out')];
  }), rows(b, re`^\s*assertEquals\((true|false), TextNormalizer\.matches\((${S}), (${S}), (null|listOf\(.*\))\)\)$`).map((r) => {
    const syn = r[3] === 'null' ? null
      : [...r[3].matchAll(new RegExp(re`listOf\((${S}(?:, ${S})*)\)`, 'g'))].map((g) => [...g[1].matchAll(new RegExp(S, 'g'))].map((x) => kt(x[0])));
    return [kt(r[1]), kt(r[2]), syn, r[0]];
  }));
  same('text-normalize editDistance', list(t, 'editDistance').map((o) => [s(o, 'a'), s(o, 'b'), l(o, 'max'), l(o, 'out')]),
    rows(b, re`assertEquals\((${I}), TextNormalizer\.editDistance\((${S}), (${S}), (${I})\)\)`).map((it) => [kt(it[1]), kt(it[2]), L(it[3]), L(it[0])]));
  same('text-normalize typoBudget', list(t, 'typoBudget').map((o) => [l(o, 'len'), l(o, 'out')]),
    rows(b, re`assertEquals\((${I}), TextNormalizer\.typoBudget\((${I})\)\)`).map((it) => [L(it[1]), L(it[0])]));
  same('text-normalize fuzzyMatches', list(t, 'fuzzyMatches').map((o) => [s(o, 'text'), s(o, 'query'), s(o, 'out')]),
    rows(b, re`assertEquals\((true|false), TextNormalizer\.fuzzyMatches\((${S}), (${S})\)\)`).map((it) => [kt(it[1]), kt(it[2]), it[0]]));
});

/** `val sections = mapOf("پرونده" to Pair(setOf(…)|emptySet(), setOf(…)|emptySet()), …)` ِ آزمون */
function sectionsPlan(src = parity) {
  const where = 'CoreVectorsCommonParityTest.kt sections';
  const set = (i) => {
    if (src.startsWith('emptySet()', i)) return { items: [], end: i + 'emptySet()'.length };
    if (!src.startsWith('setOf(', i)) throw new Error(`${where}: setOf/emptySet نیامد: ${src.slice(i, i + 30)}`);
    return KT.seq(src, i + 'setOf('.length, where);
  };
  const plan = [];
  let i = KT.at(src, '    val sections = mapOf(', where);
  for (;;) {
    i = KT.skip(src, i);
    if (src[i] === ')') break;
    const file = KT.literal(src, i, where);
    i = KT.skip(src, file.end);
    if (!src.startsWith('to Pair(', i)) throw new Error(`${where}: «to Pair(» پس از ${file.value} نیامد`);
    const a = set(KT.skip(src, i + 'to Pair('.length));
    i = KT.skip(src, a.end);
    if (src[i] !== ',') throw new Error(`${where}: «,» میانِ دو مجموعهٔ ${file.value} نیامد`);
    const b = set(KT.skip(src, i + 1));
    i = KT.skip(src, b.end);
    if (src[i] === ',') i = KT.skip(src, i + 1);
    if (src[i] !== ')') throw new Error(`${where}: «)» ِ Pair ِ ${file.value} نیامد`);
    plan.push([file.value, a.items, b.items]);
    i = KT.skip(src, i + 1);
    if (src[i] === ',') { i++; continue; }
    if (src[i] === ')') break;
    throw new Error(`${where}: نشانهٔ ناشناخته پس از ${file.value}: ${src.slice(i, i + 30)}`);
  }
  return plan;
}

test('آینهٔ CoreVectorsCommonParityTest.sections: هر بخشِ پنج JSON یا mirrored است یا notMirrored', () => {
  const plan = sectionsPlan();
  assert.deepEqual(plan.map(([f]) => f), ['license/test-vectors/money-v1.json', 'core/test-vectors/receipt-v1.json',
    'core/test-vectors/calendar.json', 'core/test-vectors/locale-format.json', 'core/test-vectors/text-normalize.json']);
  for (const [file, mirrored, notMirrored] of plan) {
    const keys = Object.keys(json(file)).filter((k) => !(k.startsWith('_') || k === 'about' || k === 'version'));
    assert.deepEqual([...new Set(keys)].sort(), [...new Set([...mirrored, ...notMirrored])].sort(),
      `${file}: بخش‌های JSON با فهرستِ CoreVectorsCommonParityTest نمی‌خوانند — بخشِ تازه را در CoreVectorsCommonTest بیاور و در ` +
      'mirrored بنویس، یا اگر آگاهانه فقط JVM است، در notMirrored.');
  }
  //  خودِ سنجه سرخ می‌شود: سطرِ عوض‌شده (Long ِ بزرگ هم) و نوعِ دیگر (Long ≠ Double)
  assert.throws(() => same('x', [[L('9223372036854775807')]], [[L('9223372036854775806')]]), /سطرِ 1/);
  assert.throws(() => same('x', [[L(1)]], [[dbl('1')]]), /هم‌خوان نیست/);
  assert.throws(() => same('x', [[L(1)]], []), /پرونده 0/);
  assert.throws(() => sectionsPlan('    val sections = mapOf(\n  "a.json" to Pair(setOf("x"), listOf("y")),\n)'), /setOf\/emptySet/);
});
