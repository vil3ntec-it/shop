#!/usr/bin/env node
'use strict';
/**
 * فهرستِ واژه‌های دری برای وبِ خریدار (`public/kit/dari.js`) — از خودِ `DariText.kt` ِ برنامه‌ها.
 *
 *   node scripts/dari-words.js           ⇐ فقط می‌سنجد که بلوکِ دادهٔ dari.js با DariText.kt یکی است
 *   node scripts/dari-words.js --write   ⇐ بلوک را از نو می‌نویسد
 *
 * منبع **فقط** `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/text/DariText.kt` است (WORDS، PHRASES،
 * KEEP و دو جدولِ پسوند)؛ وب هیچ فهرستِ دستیِ جدایی ندارد. هر سطرِ این بلوک‌ها در کاتلین باید با الگوی
 * همین‌جا بخواند، وگرنه خطا (نه رد شدنِ بی‌صدا): شکلِ تازه ⇒ همین پرونده هم به‌روز شود. آزمونِ
 * `test/dari-text.test.js` همین `build()` را با بلوکِ dari.js می‌سنجد، پس واژهٔ تازه در کاتلین بی
 * `--write` CI ِ سرور را سرخ می‌کند.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const KT = path.join(ROOT, 'core', 'src', 'commonMain', 'kotlin', 'ir', 'vil3ntec', 'tohid', 'core', 'text', 'DariText.kt');
const JS = path.join(__dirname, '..', 'public', 'kit', 'dari.js');
const BEGIN = '/* <dari-words> ';
const END = '/* </dari-words> */';

/** نام‌های ثابتِ کاتلین که درونِ جدولِ پسوندها به کار رفته‌اند */
const CONST = { ZWNJ: '‌', KASRA: 'ِ', HAMZA_ABOVE: 'ٔ' };

/** رشتهٔ کاتلین ⇒ رشته؛ فقط `${ZWNJ}`‌گونه از نام‌های بالا، هیچ گریز یا قالبِ دیگری */
function kstr(raw, where) {
  if (/\\/.test(raw)) throw new Error(`${where}: گریزِ «\\» در رشته پشتیبانی نمی‌شود: ${raw}`);
  const s = raw.replace(/\$\{(\w+)\}/g, (m, n) => {
    if (!(n in CONST)) throw new Error(`${where}: نامِ ناشناختهٔ ${n}`);
    return CONST[n];
  });
  if (s.includes('$')) throw new Error(`${where}: قالبِ «$» پشتیبانی نمی‌شود: ${raw}`);
  return s;
}
/** یک مقدارِ کاتلین: "رشته" یا یکی از نام‌های CONST */
function kval(tok, where) {
  const m = /^"([^"]*)"$/.exec(tok);
  if (m) return kstr(m[1], where);
  if (tok in CONST) return CONST[tok];
  throw new Error(`${where}: مقدارِ ناشناخته ${tok}`);
}

/** متنِ میانِ `head` و نخستین `tail` پس از آن */
function block(src, head, tail) {
  const a = src.indexOf(head);
  if (a < 0) throw new Error(`DariText.kt: «${head}» پیدا نشد`);
  const b = src.indexOf(tail, a + head.length);
  if (b < 0) throw new Error(`DariText.kt: پایانِ «${head}» پیدا نشد`);
  return src.slice(a + head.length, b);
}
const lines = (s) => s.split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('//'));

function build(src = fs.readFileSync(KT, 'utf8')) {
  const words = lines(block(src, 'val WORDS: List<Word> = listOf(', ').sortedByDescending { it.fa.length }')).map((l) => {
    const m = /^Word\("([^"]*)", "([^"]*)"((?:, \w+ = (?:true|false|"[^"]*"))*)\),?$/.exec(l);
    if (!m) throw new Error(`DariText.kt WORDS: سطرِ ناشناخته: ${l}`);
    const w = { fa: kstr(m[1], 'WORDS'), dari: kstr(m[2], 'WORDS') };
    for (const [, k, v] of m[3].matchAll(/, (\w+) = (true|false|"[^"]*")/g)) {
      if (!['vowelEnd', 'person', 'plural'].includes(k)) throw new Error(`DariText.kt WORDS: پارامترِ ناشناختهٔ ${k}`);
      if (v === 'true') w[k] = true;
      else if (v !== 'false') w[k] = kstr(v.slice(1, -1), 'WORDS');
    }
    return w;
  });
  const phrases = {};
  //  جملهٔ بلند: «"فارسی" to» در یک سطر و «"دری",» در سطرِ بعد
  const pairs = [];
  for (const l of lines(block(src, 'val PHRASES: Map<String, String> = mapOf(', '\n  )'))) {
    if (pairs.length && /^"[^"]*" to$/.test(pairs[pairs.length - 1])) pairs[pairs.length - 1] += ` ${l}`;
    else pairs.push(l);
  }
  for (const l of pairs) {
    const m = /^"([^"]*)" to "([^"]*)",?$/.exec(l);
    if (!m) throw new Error(`DariText.kt PHRASES: سطرِ ناشناخته: ${l}`);
    const fa = kstr(m[1], 'PHRASES');
    if (fa in phrases) throw new Error(`DariText.kt PHRASES: کلیدِ تکراری: ${fa}`);
    phrases[fa] = kstr(m[2], 'PHRASES');
  }
  const keep = block(src, 'val KEEP: List<String> = listOf(', ')\n').split(',').map((x) => x.trim()).filter(Boolean)
    .map((x) => kval(x, 'KEEP'));
  const vowelTails = {};
  for (const l of lines(block(src, 'private val VOWEL_TAILS: Map<String, String> = mapOf(', '\n  )'))) {
    const m = /^("[^"]*"|\w+) to ("[^"]*"|\w+),?$/.exec(l);
    if (!m) throw new Error(`DariText.kt VOWEL_TAILS: سطرِ ناشناخته: ${l}`);
    vowelTails[kval(m[1], 'VOWEL_TAILS')] = kval(m[2], 'VOWEL_TAILS');
  }
  const consonantTails = block(src, 'private val CONSONANT_TAILS: Set<String> = setOf(', '\n  )').split(',')
    .map((x) => x.trim()).filter(Boolean).map((x) => kval(x, 'CONSONANT_TAILS'));
  if (words.length < 10 || !keep.length || !consonantTails.length || !Object.keys(vowelTails).length) {
    throw new Error('DariText.kt: فهرست ناقص خوانده شد');
  }
  return { words, phrases, keep, vowelTails, consonantTails };
}

/** همان بلوکِ درونِ dari.js: یک سطرِ JSON (نویسهٔ نامرئی با گریزِ \u تا دیده شود) */
function render(data) {
  const json = JSON.stringify(data).replace(/[‌ِٔ]/g, (c) => `\\u${c.charCodeAt(0).toString(16).toUpperCase().padStart(4, '0')}`);
  return `${BEGIN}ساختهٔ scripts/dari-words.js از DariText.kt — دستی ویرایش نکن */\n  var DATA = ${json};\n  ${END}`;
}

/** بلوکِ کنونیِ dari.js */
function current(js = fs.readFileSync(JS, 'utf8')) {
  const a = js.indexOf(BEGIN);
  const b = js.indexOf(END);
  if (a < 0 || b < a) throw new Error('dari.js: بلوکِ <dari-words> پیدا نشد');
  return { a, b: b + END.length, text: js.slice(a, b + END.length) };
}

/**
 *  هم‌زادِ `license/dari.js`: رونوشتِ بایت‌به‌بایتِ همین dari.js — هیچ فهرستِ دومی نه (`dari-text.test.js`). وبِ دکان که
 *  آن را می‌خواند به تصمیمِ صاحب برداشته شد (۱۴۰۵/۰۸/۲۰)؛ رونوشت به‌عنوانِ کتابخانهٔ مشترک می‌ماند (docs/RULES-fa.md، بخشِ نخست).
 */
const WEB = path.join(ROOT, 'license', 'dari.js');

module.exports = { build, render, current, KT, JS, WEB };

if (require.main === module) {
  const want = render(build());
  const js = fs.readFileSync(JS, 'utf8');
  const cur = current(js);
  const next = js.slice(0, cur.a) + want + js.slice(cur.b);
  const web = fs.existsSync(WEB) ? fs.readFileSync(WEB, 'utf8') : '';
  if (process.argv.includes('--write')) {
    if (cur.text !== want) fs.writeFileSync(JS, next);
    if (web !== next) fs.writeFileSync(WEB, next);
    console.log(cur.text === want ? 'dari.js: بی تغییر' : 'dari.js: بلوکِ واژه‌ها از نو نوشته شد');
    if (web !== next) console.log('license/dari.js: رونوشت از نو نوشته شد');
  } else if (cur.text !== want || web !== js) {
    console.error('dari.js (یا رونوشتِ license/dari.js) با DariText.kt یکی نیست — node scripts/dari-words.js --write');
    process.exit(1);
  } else {
    console.log('dari.js = license/dari.js = DariText.kt');
  }
}
