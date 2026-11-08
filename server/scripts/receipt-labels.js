#!/usr/bin/env node
'use strict';
/**
 * برچسب‌های فاکتورِ چاپیِ نسخهٔ وبِ دکان — از خودِ `StrReceipt.kt` ِ برنامه (#348).
 *
 *   node scripts/receipt-labels.js           ⇐ فقط می‌سنجد که `license/i18n/receipt.<زبان>.json` همان StrReceipt است
 *   node scripts/receipt-labels.js --write   ⇐ سه واژه‌نامه را از نو می‌نویسد
 *
 * منبع **فقط** `StrReceipt.kt` است: هر `val نام: String get() = t("فارسی", "English", "پښتو")` یک کلید در هر سه
 * واژه‌نامه؛ دری همان فارسی از لایهٔ واژه (`license/dari.js`) در خودِ مرورگر، همان کارِ `I18n.pick` ِ برنامه. وب هیچ
 * برچسبِ دستیِ جدایی برای کاغذ ندارد، پس برنامه و وب از هم جدا نمی‌شوند: سطری در کاتلین که با الگوی این‌جا نخواند
 * خطاست (نه رد شدنِ بی‌صدا)، و آزمونِ `test/web-shop-i18n.test.js` همین `build()` را با پرونده‌ها می‌سنجد.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const KT = path.join(ROOT, 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'core', 'i18n', 'StrReceipt.kt');
const OUT = path.join(ROOT, 'license', 'i18n');
const LANGS = ['fa', 'en', 'ps'];
const file = (l) => path.join(OUT, `receipt.${l}.json`);

/** رشتهٔ کاتلین بی گریز و بی قالب؛ هر چیزِ دیگر ⇒ خطا */
function kstr(raw, where) {
  if (/[\\$]/.test(raw)) throw new Error(`StrReceipt.kt ${where}: گریز یا قالب در رشته پشتیبانی نمی‌شود: ${raw}`);
  return raw;
}

function build(src = fs.readFileSync(KT, 'utf8')) {
  const out = { fa: {}, en: {}, ps: {} };
  const body = src.slice(src.indexOf('object StrReceipt'));
  for (const line of body.split('\n')) {
    const l = line.trim();
    if (!l.startsWith('val ')) continue;
    const m = /^val (\w+): String get\(\) = t\("([^"]*)", "([^"]*)", "([^"]*)"\)$/.exec(l);
    if (!m) throw new Error(`StrReceipt.kt: سطرِ ناشناخته: ${l}`);
    const [, key, fa, en, ps] = m;
    if (key in out.fa) throw new Error(`StrReceipt.kt: کلیدِ تکراری: ${key}`);
    out.fa[key] = kstr(fa, key);
    out.en[key] = kstr(en, key);
    out.ps[key] = kstr(ps, key);
  }
  if (Object.keys(out.fa).length < 20) throw new Error('StrReceipt.kt: جدول ناقص خوانده شد');
  return out;
}

const render = (dict) => `${JSON.stringify(dict, null, 2)}\n`;

module.exports = { build, render, KT, OUT, LANGS, file };

if (require.main === module) {
  const want = build();
  const stale = LANGS.filter((l) => !fs.existsSync(file(l)) || fs.readFileSync(file(l), 'utf8') !== render(want[l]));
  if (process.argv.includes('--write')) {
    for (const l of stale) fs.writeFileSync(file(l), render(want[l]));
    console.log(stale.length ? `receipt.*.json از نو: ${stale.join('، ')}` : 'receipt.*.json: بی تغییر');
  } else if (stale.length) {
    console.error(`license/i18n/receipt.<زبان>.json با StrReceipt.kt یکی نیست (${stale.join('، ')}) — node scripts/receipt-labels.js --write`);
    process.exit(1);
  } else {
    console.log('receipt.*.json = StrReceipt.kt');
  }
}
