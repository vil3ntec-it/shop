'use strict';
/**
 * خواندنِ فهرست‌های مشترک از خودِ منبعِ کاتلین — برای آینه‌های JS ِ آزمون‌های کاتلینی که پرونده‌های
 * سرور/وب/license/docs را می‌سنجند (`kotlin-file-mirrors.test.js`، `core-vectors-common-mirror.test.js`،
 * `dari-text.test.js`). هیچ فهرستی این‌جا رونوشت نمی‌شود؛ همان `listOf(…)`/`setOf(…)`/`mapOf(…)` ِ پروندهٔ
 * کاتلین با تجزیه‌گرِ سخت خوانده می‌شود (مثلِ `scripts/dari-words.js` برای DariText.kt): سرخطِ نبود یا
 * تکراری، رشتهٔ خام، قالبِ `$`، گریزِ ناشناخته یا هر نشانهٔ دیگری میانِ عضوها ⇒ خطا، نه رد شدنِ بی‌صدا.
 * فقط پرونده می‌خواند؛ بی پایگاه و بی شبکه.
 */
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
/** پروندهٔ مخزن با پایانِ سطرِ یکدست (`\r\n` ⇒ `\n`، همان `readText().replace` ِ آزمون‌های کاتلین) */
const read = (rel) => fs.readFileSync(path.join(ROOT, rel), 'utf8').replace(/\r\n/g, '\n');

const ESC = { t: '\t', n: '\n', r: '\r', b: '\b', '"': '"', '\\': '\\', "'": "'", $: '$' };

/** رشتهٔ کاتلین که در `src[at]` با `"` آغاز می‌شود ⇒ { value, end }؛ end جای پس از `"` ِ پایانی */
function literal(src, at, where) {
  if (src[at] !== '"') throw new Error(`${where}: رشتهٔ کاتلین نیست: ${src.slice(at, at + 40)}`);
  if (src.startsWith('"""', at)) throw new Error(`${where}: رشتهٔ خام (""") پشتیبانی نمی‌شود`);
  let out = '';
  let i = at + 1;
  for (;;) {
    const c = src[i];
    if (c === undefined || c === '\n') throw new Error(`${where}: رشتهٔ بسته‌نشده: ${src.slice(at, at + 60)}`);
    if (c === '"') return { value: out, end: i + 1 };
    if (c === '$' && /[\p{L}_{]/u.test(src[i + 1] || '')) throw new Error(`${where}: قالبِ «$» پشتیبانی نمی‌شود: ${src.slice(at, i + 20)}`);
    if (c === '\\') {
      const e = src[i + 1];
      if (e === 'u') {
        const hex = src.slice(i + 2, i + 6);
        if (!/^[0-9a-fA-F]{4}$/.test(hex)) throw new Error(`${where}: گریزِ \\u ِ بد: ${src.slice(i, i + 6)}`);
        out += String.fromCharCode(parseInt(hex, 16));
        i += 6;
        continue;
      }
      if (!Object.prototype.hasOwnProperty.call(ESC, e)) throw new Error(`${where}: گریزِ ناشناختهٔ \\${e}`);
      out += ESC[e];
      i += 2;
      continue;
    }
    out += c;
    i++;
  }
}

/** فاصله، سطرِ نو و توضیحِ `//`/`/* *\/` را رد می‌کند */
function skip(src, i) {
  for (;;) {
    while (i < src.length && /\s/.test(src[i])) i++;
    if (src.startsWith('//', i)) { const n = src.indexOf('\n', i); i = n < 0 ? src.length : n + 1; continue; }
    if (src.startsWith('/*', i)) { const n = src.indexOf('*/', i + 2); if (n < 0) return src.length; i = n + 2; continue; }
    return i;
  }
}

/** جای سرخطِ یکتا؛ نبود یا بیش از یکی ⇒ خطا */
function at(src, head, where) {
  const a = src.indexOf(head);
  if (a < 0) throw new Error(`${where}: «${head}» پیدا نشد`);
  if (src.indexOf(head, a + 1) >= 0) throw new Error(`${where}: «${head}» بیش از یک بار آمده`);
  return a + head.length;
}

/** عضوهای `head` (مثلاً «private val x = listOf(») تا `)` ِ بستن؛ pairs ⇒ «"k" to "v"» */
function members(src, head, where, pairs) {
  const { items } = seq(src, at(src, head, where), where, pairs);
  if (!items.length) throw new Error(`${where}: «${head}» خالی خوانده شد`);
  return items;
}

/** عضوهای رشته‌ایِ پس از `(` (در `i`) تا `)` ِ بستن ⇒ { items, end }؛ end جای پس از `)` */
function seq(src, i, where, pairs = false) {
  const out = [];
  for (;;) {
    i = skip(src, i);
    if (src[i] === ')') break;
    const k = literal(src, i, where);
    i = skip(src, k.end);
    if (pairs) {
      if (!/^to\s/.test(src.slice(i, i + 3))) throw new Error(`${where}: «to» پس از «${k.value}» نیامد: ${src.slice(i, i + 30)}`);
      const v = literal(src, skip(src, i + 2), where);
      out.push([k.value, v.value]);
      i = skip(src, v.end);
    } else out.push(k.value);
    if (src[i] === ',') { i++; continue; }
    if (src[i] === ')') break;
    throw new Error(`${where}: نشانهٔ ناشناخته پس از «${k.value}»: ${src.slice(i, i + 30)}`);
  }
  return { items: out, end: i + 1 };
}

/** `listOf("…", …)` / `setOf(…)` ِ یک پرونده ⇒ آرایهٔ رشته‌ها؛ عضوِ تکراری ⇒ خطا */
function list(rel, head) {
  const where = `${path.basename(rel)} ${head.trim()}`;
  const out = members(read(rel), head, where, false);
  const dup = out.find((x, i) => out.indexOf(x) !== i);
  if (dup !== undefined) throw new Error(`${where}: عضوِ تکراری «${dup}»`);
  return out;
}

/** `mapOf("k" to "v", …)` ِ یک پرونده ⇒ آرایهٔ جفت‌ها (به همان ترتیب)؛ کلیدِ تکراری ⇒ خطا */
function map(rel, head) {
  const where = `${path.basename(rel)} ${head.trim()}`;
  const out = members(read(rel), head, where, true);
  const keys = out.map(([k]) => k);
  const dup = keys.find((x, i) => keys.indexOf(x) !== i);
  if (dup !== undefined) throw new Error(`${where}: کلیدِ تکراری «${dup}»`);
  return out;
}

/** تنها جورِ `re` (بی پرچمِ g) در متن؛ هیچ یا بیش از یکی ⇒ خطا */
function one(text, re, where) {
  const all = [...text.matchAll(new RegExp(re.source, `${re.flags.replace('g', '')}g`))];
  if (all.length !== 1) throw new Error(`${where}: ${re} باید یک بار بیاید، ${all.length} بار آمد`);
  return all[0];
}

/* ---------- `DariSources` ِ DariWordsTest.kt، خط‌به‌خط: متن‌هایی که از همان لایهٔ DariText می‌گذرند ---------- */

const SHOPPER = 'core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/shopper/ShopperText.kt';
const WEB_I18N = 'server/public/kit/i18n';
const isLetter = (c) => /\p{L}/u.test(c || '');
const isLetterOrDigit = (c) => /[\p{L}\p{Nd}]/u.test(c || '');

/** همان `kotlinLiteral`: هر `${…}` یا `$نام` ⇒ `{}`؛ `"…" + "…"` پشتِ هم یکی؛ `\x` ⇒ x (همان کاتلین) */
function kotlinLiteral(src, start) {
  let sb = '';
  let i = start;
  for (;;) {
    i++;
    while (src[i] !== '"') {
      if (i >= src.length) throw new Error('ShopperText.kt: رشتهٔ بسته‌نشده');
      if (src[i] === '\\') { sb += src[i + 1]; i += 2; } else if (src[i] === '$' && src[i + 1] === '{') {
        let depth = 0;
        for (;;) { if (src[i] === '{') depth++; else if (src[i] === '}' && --depth === 0) break; i++; }
        sb += '{}';
        i++;
      } else if (src[i] === '$' && (isLetter(src[i + 1]) || src[i + 1] === '_')) {
        i++;
        while (isLetterOrDigit(src[i]) || src[i] === '_') i++;
        sb += '{}';
      } else { sb += src[i]; i++; }
    }
    //  `\s` ِ جاوا (ASCII)، نه یونیکد
    const next = /^[ \t\n\x0B\f\r]*\+[ \t\n\x0B\f\r]*"/.exec(src.substring(i + 1, Math.min(src.length, i + 200)));
    if (!next) return sb;
    i += next[0].length;
  }
}

/** همان `shopperTexts()`: متنِ فارسیِ هر `t(…)` ِ ShopperText با `{}` به‌جای هر `${…}`؛ کمتر از ۱۵۱ ⇒ خطا */
function shopperTexts(src = read(SHOPPER)) {
  const out = new Set();
  for (const m of src.matchAll(/(?<![\w.])t\([ \t\n\x0B\f\r]*"/g)) out.add(kotlinLiteral(src, m.index + m[0].length - 1));
  if (!(out.size > 150)) throw new Error(`نوشته‌های برنامهٔ خرید خوانده نشد (${out.size})`);
  return out;
}

/** همان `webTexts()`: متنِ فارسیِ `fa.json` و `<بخش>.fa.json` ِ ویترین با `{}` به‌جای هر {جای‌خالی} — با همان regex، نه JSON.parse */
function webTexts(dir = path.join(ROOT, WEB_I18N)) {
  const files = fs.readdirSync(dir).filter((f) => /^(fa|[a-z]+\.fa)\.json$/.test(f));
  if (!(files.length >= 10)) throw new Error(`واژه‌نامهٔ وب پیدا نشد (${files.length})`);
  const out = new Set();
  for (const f of files) {
    const text = fs.readFileSync(path.join(dir, f), 'utf8');
    for (const m of text.matchAll(/"(?:[^"\\]|\\.)*"\s*:\s*"((?:[^"\\]|\\.)*)"/g)) {
      const v = m[1].replace(/\\u([0-9a-fA-F]{4})/g, (x, h) => String.fromCharCode(parseInt(h, 16)))
        .replaceAll('\\"', '"').replaceAll('\\n', '\n').replaceAll('\\\\', '\\');
      out.add(v.replace(/\{\w+\}/g, '{}'));
    }
  }
  return out;
}

/* ---------- جدول‌های `Str*` ِ برنامهٔ دکان: همان `rows(I18nTables.all)` ِ DariCoverageTest.kt ---------- */
//  کاتلین هر ویژگی/تابعِ String ِ هر جدول را با آرگومانِ ساختگی صدا می‌زند و دری = `DariText.of(fa)` ِ همان `t`/`td`
//  (I18n.kt: جدول دریِ جدا ندارد؛ هر جای‌خالیِ فارسی `asIs` است). این‌جا فارسیِ نخستین آرگومانِ هر `t(`/`td(` ِ بدنهٔ
//  هر `object` ِ فهرست از خودِ پرونده خوانده می‌شود؛ هر چه خوانده نشد با جایش برمی‌گردد (unread)، نه رد شدنِ بی‌صدا.

const I18N = 'android-native/app/src/main/java/ir/vil3ntec/tohid/core/i18n';
const I18N_TABLES = `${I18N}/I18nTables.kt`;
/** ریشه‌های بستهٔ کاتلین برای ثابتی که فارسیِ `t(…)` است (`CourierInputText.X`، `StorefrontOwner.DAY_NAMES[i]`) */
const KT_ROOTS = ['android-native/app/src/main/java', 'core/src/commonMain/kotlin'];
const isIdent = (c) => /[\p{L}\p{Nd}_]/u.test(c || '');
/** شکلی که خواننده نمی‌شناسد ⇒ یک ردیفِ unread (خطای واژگانی مثلِ رشتهٔ بسته‌نشده همان خطاست) */
class Unread extends Error {}

/** همان `skip`، با توضیحِ تودرتوی کاتلین */
function gap(src, i) {
  for (;;) {
    while (i < src.length && /\s/.test(src[i])) i++;
    if (src.startsWith('//', i)) { const n = src.indexOf('\n', i); i = n < 0 ? src.length : n + 1; continue; }
    if (!src.startsWith('/*', i)) return i;
    for (let depth = 0; ;) {
      if (i >= src.length) throw new Error(`توضیحِ بسته‌نشده: ${src.slice(0, 60)}`);
      if (src.startsWith('/*', i)) { depth++; i += 2; } else if (src.startsWith('*/', i)) { i += 2; if (!--depth) break; } else i++;
    }
  }
}

/** رشتهٔ کاتلین در `at` ⇒ { text, end, raw }؛ هر `${…}`/`$نام` ⇒ `{}` و کدِ درونِ `${…}` هم گشته می‌شود (visit) */
function ktString(src, at, visit, depth) {
  const raw = src.startsWith('"""', at);
  let text = '';
  let i = at + (raw ? 3 : 1);
  for (;;) {
    const c = src[i];
    if (c === undefined || (!raw && c === '\n')) throw new Error(`رشتهٔ بسته‌نشده: ${src.slice(at, at + 60)}`);
    if (raw && src.startsWith('"""', i)) { while (src[i + 3] === '"') { text += '"'; i++; } return { text, end: i + 3, raw }; }
    if (!raw && c === '"') return { text, end: i + 1, raw };
    if (c === '$' && src[i + 1] === '{') { i = ktCode(src, i + 2, '}', visit, depth + 1); text += '{}'; continue; }
    if (c === '$' && /[\p{L}_]/u.test(src[i + 1] || '')) { i++; while (isIdent(src[i])) i++; text += '{}'; continue; }
    if (!raw && c === '\\') {
      const e = src[i + 1];
      if (e === 'u' && /^[0-9a-fA-F]{4}$/.test(src.slice(i + 2, i + 6))) { text += String.fromCharCode(parseInt(src.slice(i + 2, i + 6), 16)); i += 6; continue; }
      if (!Object.prototype.hasOwnProperty.call(ESC, e)) throw new Error(`گریزِ ناشناختهٔ \\${e}: ${src.slice(at, i + 4)}`);
      text += ESC[e];
      i += 2;
      continue;
    }
    text += c;
    i++;
  }
}

/** کدِ کاتلین از `i` تا `close` ِ هم‌سطح (`}` یا `)`) ⇒ جای پس از آن؛ هر واژهٔ کد ⇒ visit(word, start, end, عمقِ {}) */
function ktCode(src, i, close, visit, depth = 0) {
  const open = [];
  const PAIR = { ')': '(', '}': '{', ']': '[' };
  for (;;) {
    i = gap(src, i);
    const c = src[i];
    const d = depth + open.filter((b) => b === '{').length;
    if (c === undefined) throw new Error(`«${close}» ِ بستن نیامد`);
    if (c === '"') { i = ktString(src, i, visit, d).end; continue; }
    if (c === "'") {
      const m = /^'(?:\\u[0-9a-fA-F]{4}|\\.|[^\\'\n])'/.exec(src.slice(i, i + 8));
      if (!m) throw new Error(`نویسهٔ بد: ${src.slice(i, i + 8)}`);
      i += m[0].length;
      continue;
    }
    if (c === '`') { i = src.indexOf('`', i + 1) + 1; if (!i) throw new Error('`نام` ِ بسته‌نشده'); continue; }
    if ('({['.includes(c)) { open.push(c); i++; continue; }
    if (')}]'.includes(c)) {
      if (!open.length && c === close) return i + 1;
      if (open.pop() !== PAIR[c]) throw new Error(`«${c}» ِ نابه‌جا: ${src.slice(Math.max(0, i - 40), i + 1)}`);
      i++;
      continue;
    }
    if (isIdent(c) && !isIdent(src[i - 1])) {
      let e = i;
      while (isIdent(src[e])) e++;
      visit(src.slice(i, e), i, e, d);
      i = e;
      continue;
    }
    i++;
  }
}

const noop = () => {};

/** یک جزءِ فارسی: رشته، `asIs(…)` ⇒ `{}`، `(…)`، `if (…) A else B` (هر دو شاخه) یا ثابتِ `Obj.NAME`/`Obj.LIST[i]` */
function ktTerm(src, i, constant) {
  if (src[i] === '"') {
    const s = ktString(src, i, noop, 0);
    if (s.raw) throw new Unread('رشتهٔ خام (""")');
    return { texts: [s.text], end: s.end };
  }
  if (src[i] === '(') {
    const inner = ktConcat(src, gap(src, i + 1), constant);
    const j = gap(src, inner.end);
    if (src[j] !== ')') throw new Unread(`«(…» بی «)»: ${src.slice(j, j + 30)}`);
    return { texts: inner.texts, end: j + 1 };
  }
  const w = /^[A-Za-z_][\w.]*/.exec(src.slice(i, i + 200));
  if (!w) throw new Unread(`«${src.slice(i, i + 30)}»`);
  let j = i + w[0].length;
  if (w[0] === 'if') {
    j = gap(src, j);
    if (src[j] !== '(') throw new Unread('if بی «(»');
    const yes = ktConcat(src, gap(src, ktCode(src, j + 1, ')', noop)), constant);
    j = gap(src, yes.end);
    if (!/^else\b/.test(src.slice(j, j + 5))) throw new Unread('if بی else');
    const no = ktConcat(src, gap(src, j + 4), constant);
    return { texts: [...yes.texts, ...no.texts], end: no.end };
  }
  if (w[0] === 'asIs' && src[gap(src, j)] === '(') return { texts: ['{}'], end: ktCode(src, gap(src, j) + 1, ')', noop) };
  const idx = /^\[(\d+)\]/.exec(src.slice(j, j + 12));
  if (idx) j += idx[0].length;
  if (src[gap(src, j)] === '(') throw new Unread(`تابعِ «${w[0]}(…)»`);
  if (!w[0].includes('.')) throw new Unread(`متغیرِ «${w[0]}»`);
  const v = constant(w[0], idx ? Number(idx[1]) : undefined);
  if (v === undefined) throw new Unread(`ثابتِ «${w[0]}${idx ? idx[0] : ''}» خوانده نشد`);
  return { texts: [v], end: j };
}

/** `جزء + جزء + …` ⇒ { texts: هر ترکیب، end } */
function ktConcat(src, i, constant) {
  let texts = [''];
  for (;;) {
    const t = ktTerm(src, i, constant);
    texts = texts.flatMap((a) => t.texts.map((b) => a + b));
    i = gap(src, t.end);
    if (src[i] !== '+') return { texts, end: t.end };
    i = gap(src, i + 1);
  }
}

/** نخستین آرگومانِ `t(`/`td(` (پس از `(` در `i`؛ `fa =` ِ نام‌دار هم) ⇒ { texts } یا { why } */
function faArg(src, i, constant) {
  i = gap(src, i);
  const named = /^fa\s*=(?!=)/.exec(src.slice(i, i + 8));
  if (named) i = gap(src, i + named[0].length);
  try {
    const { texts, end } = ktConcat(src, i, constant);
    const j = gap(src, end);
    if (src[j] !== ',' && src[j] !== ')') throw new Unread(`پس از فارسی «${src.slice(j, j + 30)}»`);
    return { texts };
  } catch (e) {
    if (e instanceof Unread) return { why: e.message };
    throw e;
  }
}

/** بدنهٔ `object <name> … {` ِ متن ⇒ { start, end } (پس از `{` تا پیش از `}`)؛ نبود ⇒ null */
function objectBody(src, name) {
  const head = new RegExp(`^(?:(?:internal|private) )?object ${name}\\b[^{\\n]*\\{`, 'm').exec(src);
  if (!head) return null;
  const start = head.index + head[0].length;
  return { start, end: ktCode(src, start, '}', noop) - 1 };
}

const pkgFiles = new Map();
/** `Obj.NAME` یا `pkg.Obj.NAME` (و `LIST[i]` ِ `listOf("…")`) ِ پروندهٔ دیگر، با import ِ `from` ⇒ متن؛ هر شکلِ دیگر ⇒ undefined */
function ktConstant(chain, index, from) {
  const seg = chain.split('.');
  const name = seg.pop();
  const obj = seg.pop();
  let pkg = seg.join('.');
  if (!pkg) {
    const imp = new RegExp(`^import ([\\w.]+)\\.${obj}$`, 'm').exec(from) || /^package ([\w.]+)$/m.exec(from);
    pkg = imp ? imp[1] : '';
  }
  if (!pkg) return undefined;
  if (!pkgFiles.has(pkg)) {
    const rels = KT_ROOTS.map((r) => `${r}/${pkg.replaceAll('.', '/')}`).filter((d) => fs.existsSync(path.join(ROOT, d)));
    pkgFiles.set(pkg, rels.flatMap((d) => fs.readdirSync(path.join(ROOT, d)).filter((f) => f.endsWith('.kt')).map((f) => read(`${d}/${f}`))));
  }
  const hits = pkgFiles.get(pkg).map((s) => [s, objectBody(s, obj)]).filter(([, b]) => b);
  if (hits.length !== 1) return undefined;
  const [s, b] = hits[0];
  const body = s.slice(b.start, b.end);
  const decl = [...body.matchAll(new RegExp(`^[ \\t]*(?:const )?val ${name}(?:[ \\t]*:[ \\t]*[\\w<>]+)?[ \\t]*=[ \\t]*`, 'gm'))];
  if (decl.length !== 1) return undefined;
  const at0 = decl[0].index + decl[0][0].length;
  try {
    if (index !== undefined) return body.startsWith('listOf(', at0) ? seq(body, at0 + 7, `${obj}.${name}`).items[index] : undefined;
    const v = ktConcat(body, at0, (c, ix) => ktConstant(c, ix, s));
    const ok = v.texts.length === 1 && !v.texts[0].includes('{}') && /^[ \t]*(?:\/\/[^\n]*)?(?:\n|;|$)/.test(body.slice(v.end));
    return ok ? v.texts[0] : undefined;
  } catch {
    return undefined;   //  ثابتِ ناخوانا ⇒ همان ردیفِ unread ِ «ثابتِ … خوانده نشد»
  }
}

/** جدولِ `name` در متنِ `src` ⇒ { rows: [{ table, key, fa }], unread: ['Table.key: چرا'] }؛ key همان نامِ ویژگی/تابع */
function tableTexts(src, name, constant = ktConstant) {
  const body = objectBody(src, name);
  if (!body) throw new Error(`«object ${name}» پیدا نشد`);
  const rows = [];
  const unread = [];
  let key = null;
  let prev = null;
  ktCode(src, body.start, '}', (w, s, e, depth) => {
    const last = prev;
    prev = w;
    if (depth === 0 && (w === 'val' || w === 'var' || w === 'fun')) {
      const k = /^\s+(?:<[^>]*>\s*)?(?:[\p{L}\p{Nd}_]+\.)*([\p{L}\p{Nd}_]+)/u.exec(src.slice(e, e + 200));
      if (!k) throw new Error(`${name}: نامِ پس از «${w}» خوانده نشد: ${src.slice(e, e + 40)}`);
      key = k[1];
      return;
    }
    if ((w !== 't' && w !== 'td') || last === 'fun' || /[.:]/.test(src[s - 1] || '')) return;
    const open = /^[ \t]*\(/.exec(src.slice(e, e + 8));
    if (!open) return;
    const r = faArg(src, e + open[0].length, (c, ix) => constant(c, ix, src));
    if (r.why) unread.push(`${name}.${key}: ${r.why}`);
    else for (const fa of r.texts) rows.push({ table: name, key, fa });
  });
  return { rows, unread };
}

/** همان `I18nTables.all`: نامِ جدول‌ها به همان ترتیب؛ نشانهٔ دیگر، تکرار یا خالی ⇒ خطا */
function appTables(src = read(I18N_TABLES)) {
  const where = 'I18nTables.kt all';
  let i = at(src, 'val all: List<Any> get() = listOf(', where);
  const out = [];
  for (;;) {
    i = gap(src, i);
    if (src[i] === ')') break;
    const m = /^[A-Za-z_]\w*/.exec(src.slice(i, i + 80));
    if (!m) throw new Error(`${where}: نشانهٔ ناشناخته: ${src.slice(i, i + 30)}`);
    out.push(m[0]);
    i = gap(src, i + m[0].length);
    if (src[i] === ',') { i++; continue; }
    if (src[i] !== ')') throw new Error(`${where}: نشانهٔ ناشناخته پس از «${m[0]}»: ${src.slice(i, i + 30)}`);
    break;
  }
  const dup = out.find((x, k) => out.indexOf(x) !== k);
  if (dup !== undefined || !out.length) throw new Error(`${where}: ${dup ? `جدولِ تکراری «${dup}»` : 'خالی'}`);
  return out;
}

/** همان `rows(I18nTables.all)`: فارسیِ هر `t(…)`/`td(…)` ِ هر جدول (`{}` به‌جای هر جای‌خالی) و آن‌چه خوانده نشد */
function appTexts() {
  const files = fs.readdirSync(path.join(ROOT, I18N)).filter((f) => f.endsWith('.kt')).map((f) => read(`${I18N}/${f}`));
  const owners = new Map();
  for (const s of files) for (const m of s.matchAll(/^object (\w+)/gm)) owners.set(m[1], [...(owners.get(m[1]) || []), s]);
  const tables = appTables();
  const rows = [];
  const unread = [];
  for (const name of tables) {
    const hits = owners.get(name) || [];
    if (hits.length !== 1) throw new Error(`I18nTables: «object ${name}» ${hits.length} بار در ${I18N}`);
    const r = tableTexts(hits[0], name);
    rows.push(...r.rows);
    unread.push(...r.unread);
  }
  return { tables, rows, unread };
}

module.exports = {
  ROOT, read, literal, skip, at, seq, list, map, one, shopperTexts, webTexts, SHOPPER, WEB_I18N,
  appTables, appTexts, tableTexts, I18N_TABLES,
};
