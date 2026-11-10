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

module.exports = { ROOT, read, literal, skip, at, seq, list, map, one, shopperTexts, webTexts, SHOPPER, WEB_I18N };
