'use strict';
/**
 * نسخهٔ فروشگاهیِ برنامهٔ دکان (Google Play با `-Pstore=play`، کافه‌بازار با `-Pstore=bazaar`) کدِ به‌روزرسانیِ درونِ
 * برنامه و هیچ نشانیِ GitHub را **درونِ بسته** ندارد (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۲۳؛ `docs/GOOGLE-PLAY-fa.md`):
 *   ۱) `update/Updater.kt` ِ واقعی فقط در `src/direct/java` است (نسخهٔ مستقیم و پیش‌نمایش) و `src/main` آن را ندارد؛
 *   ۲) `app/build.gradle.kts` نسخهٔ فروشگاهی را به `src/store/java` و بقیه را به `src/direct/java` می‌بندد؛
 *   ۳) `src/store` هیچ «github»، نشانیِ http یا نامِ مخزن ندارد؛
 *   ۴) هیچ رشتهٔ کدِ `src/main` (که در هر دو نسخه است) نشانیِ GitHub ندارد؛
 *   ۵) API ِ جایگزینِ فروشگاهی همان API ِ همگانیِ `Updater` ِ واقعی است (همان نام‌ها، پارامترها و فیلدهای `Release`)، پس
 *      صدازننده‌ها در هر دو نسخه کامپایل می‌شوند. ساختِ اندروید فقط در CI است (این‌جا Android SDK نیست).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const APP = path.join(__dirname, '..', '..', 'android-native', 'app');
const PKG = 'java/ir/vil3ntec/tohid/update';
const read = (rel) => fs.readFileSync(path.join(APP, rel), 'utf8');
function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out); else out.push(p);
  }
  return out;
}
/** کدِ کاتلین بی توضیح‌ها (سطرِ `//`، `/* … *\/` و `//` ِ پایانِ سطر) */
const code = (t) => t.replace(/\/\*[\s\S]*?\*\//g, '').split('\n').map((l) => l.replace(/(^|\s)\/\/.*$/, '')).join('\n');

test('Updater ِ واقعی فقط در src/direct است و ساخت نسخهٔ فروشگاهی را به src/store می‌بندد', () => {
  assert.ok(fs.existsSync(path.join(APP, 'src/direct', PKG, 'Updater.kt')));
  assert.ok(!fs.existsSync(path.join(APP, 'src/main', PKG, 'Updater.kt')), 'Updater.kt به src/main برگشت');
  const gradle = read('build.gradle.kts');
  assert.match(gradle, /val storeManaged = store == "bazaar" \|\| store == "play"/);
  assert.match(gradle, /sourceSets\["main"\]\.java\.srcDir\(if \(storeManaged\) "src\/store\/java" else "src\/direct\/java"\)/);
  //  هیچ جای دیگری از Gradle یکی از این دو پوشه را به نسخهٔ دیگر نمی‌دهد
  assert.equal(code(gradle).match(/src\/(store|direct)\/java/g).length, 2);
});

test('src/store هیچ github، نشانی یا نامِ مخزنی ندارد', () => {
  const files = walk(path.join(APP, 'src/store'));
  assert.ok(files.some((f) => f.endsWith('UpdaterStoreAndroid.kt')));
  for (const f of files) {
    const t = fs.readFileSync(f, 'utf8');
    assert.doesNotMatch(t, /github/i, f);
    assert.doesNotMatch(t, /https?:|vil3ntec-it|api\.|releases\//i, f);
  }
});

test('هیچ رشتهٔ کدِ src/main نشانیِ GitHub ندارد (در بستهٔ فروشگاهی هم هست)', () => {
  const bad = [];
  for (const f of walk(path.join(APP, 'src/main')).filter((x) => /\.(kt|java|xml)$/.test(x))) {
    const t = f.endsWith('.xml') ? fs.readFileSync(f, 'utf8').replace(/<!--[\s\S]*?-->/g, '') : code(fs.readFileSync(f, 'utf8'));
    for (const [s] of t.matchAll(/"(?:[^"\\\n]|\\.)*"/g)) if (/github\.(com|io)|api\.github|vnd\.github/i.test(s)) bad.push(`${path.relative(APP, f)}: ${s}`);
  }
  assert.deepEqual(bad, []);
});

/** API ِ همگانیِ `object Updater`: هر `fun` ِ غیرخصوصی با نام و نوعِ پارامترها، `var`/`val` ِ همگانی و فیلدهای `Release` */
function api(src) {
  const t = code(src);
  const body = t.slice(t.indexOf('object Updater'));
  const out = [];
  for (const m of body.matchAll(/^ {2}((?:suspend )?)fun (\w+)\(([\s\S]*?)\)\s*(?::\s*([^=\n{]+?))?\s*[={]/gm)) {
    const params = m[3].split(/,(?![^(]*\))/).map((p) => p.trim()).filter(Boolean)
      .map((p) => p.replace(/^@\w+(\([^)]*\))?\s*/, '').replace(/\s*=.*$/, '').replace(/\s+/g, ''));
    out.push(`${m[1]}fun ${m[2]}(${params.join(',')})${m[4] ? `:${m[4].trim().replace(/\s+/g, '')}` : ''}`);
  }
  for (const m of body.matchAll(/^ {2}(var|val) (\w+)(:[^=\n]+)?/gm)) out.push(`${m[1]} ${m[2]}${(m[3] || '').replace(/\s+/g, '')}`);
  const rel = /data class Release\(([\s\S]*?)\n {2}\)/.exec(body);
  out.push(`Release(${rel[1].split('\n').map((l) => l.trim().replace(/\s*=.*$/, '').replace(/,$/, '')).filter((l) => l.startsWith('val ')).join(',')})`);
  return out.sort();
}

test('جایگزینِ فروشگاهی همان API ِ همگانیِ Updater ِ واقعی را دارد', () => {
  const real = api(read(`src/direct/${PKG}/Updater.kt`));
  const stub = api(read(`src/store/${PKG}/UpdaterStoreAndroid.kt`));
  //  خودِ سنجه چیزی را جا نیندازد
  for (const must of ['suspend fun check(', 'suspend fun download(', 'fun readyFile(', 'fun install(', 'fun stableApkUrl(', 'var lastNote', 'Release(']) {
    assert.ok(real.some((x) => x.startsWith(must)), must);
  }
  //  `stableApkUrl` ِ واقعی نوعِ برگشت را از عبارت می‌گیرد؛ نامِ پارامترها و نوع‌ها یکی
  const norm = (xs) => xs.map((x) => x.replace(/^fun stableApkUrl\(repo:String\)(:String)?$/, 'fun stableApkUrl(repo:String)'));
  assert.deepEqual(norm(stub), norm(real));
});
