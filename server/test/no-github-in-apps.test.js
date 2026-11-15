'use strict';
/**
 * نگهبان (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۲۳): هیچ برنامه‌ای کاربر را به GitHub نمی‌برد و نامش را نشان نمی‌دهد — نه پیوندِ
 * حریمِ خصوصی/شرایط (درونِ برنامه‌اند، `core/.../legal`، و نشانیِ عمومی‌شان `/legal/*` ِ خودِ سرور است)، نه صفحهٔ «درباره».
 *
 *   ۱) هیچ پروندهٔ منبعِ برنامه‌ها `github.io` یا `github.com` ندارد: برنامهٔ دکان (اندروید، کامپیوتر، آیفون، حالتِ پیک)،
 *      `core`، برنامهٔ خرید (اندروید، آیفون)، برنامهٔ مدیر، و وبِ خریدار (ویترین، بازارچه، کیت، پورتال، فاکتورِ عمومی).
 *   ۲) هیچ نوشتهٔ دیدنیِ برنامه (رشته‌های جدول‌های `Str*`، `ShopperText`، واژه‌نامه‌های وب و متنِ حقوقی) نامِ GitHub /
 *      «گیت‌هاب» ندارد.
 *
 * بیرون، عمداً (HANDOFF ِ `claude/e-rows-63p`): (الف) آزمون‌ها؛ (ب) کانالِ به‌روزرسانیِ نسخهٔ مستقیم — پوشه‌های `update/`
 * (`Updater*.kt`، `ReleaseFeed.kt`…) که تصمیمِ جدایی دارند؛ (ج) پوستهٔ WebView ِ بازنشستهٔ `android/` (فقط ساختِ دستی)؛
 * (د) پنلِ مدیرِ سامانه (`public/admin`، ابزارِ خودِ صاحب‌کار، پیوندِ انتشارهای آزمایشی) و صفحه‌های GitHub Pages ِ ریشه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const ROOTS = [
  'android-native/app/src/main', 'desktop/src/main', 'core/src', 'customer-android/app/src/main', 'customer-ios',
  'ios/shared/src', 'admin-android/app/src/main',
  'server/public/store', 'server/public/market', 'server/public/kit', 'server/public/portal', 'server/public/receipt',
];
const EXT = /\.(kt|kts|java|swift|xml|json|js|mjs|html|css|plist|strings|properties)$/;
const SKIP_DIR = /(^|\/)(build|\.gradle|node_modules|test|tests|commonTest|jvmTest|iosTest|androidTest|androidUnitTest|update)(\/|$)/;
const URL = /github\.(io|com)/i;

function walk(rel, out = []) {
  const abs = path.join(ROOT, rel);
  if (!fs.existsSync(abs)) return out;
  for (const e of fs.readdirSync(abs, { withFileTypes: true })) {
    const r = `${rel}/${e.name}`;
    if (e.isDirectory()) { if (!SKIP_DIR.test(`${r}/`)) walk(r, out); } else if (EXT.test(e.name) && !/^Updater/.test(e.name)) out.push(r);
  }
  return out;
}

test('هیچ پروندهٔ منبعِ برنامه‌ها پیوندِ github.io یا github.com ندارد (جز کانالِ به‌روزرسانی)', () => {
  const files = ROOTS.flatMap((r) => walk(r));
  assert.ok(files.length > 1500, `پرونده‌ها خوانده نشد (${files.length})`);
  //  نگهبانِ خودِ نگهبان: پوشه‌های اصلی واقعاً پیمایش شده‌اند
  for (const must of ['ui/v3/settings/sections/AboutScreen.kt', 'core/account/AccountDeletion.kt', 'customer/ui/SettingsScreen.kt', 'kit/settings.js']) {
    assert.ok(files.some((f) => f.endsWith(must)), must);
  }
  const bad = [];
  for (const f of files) {
    fs.readFileSync(path.join(ROOT, f), 'utf8').split('\n').forEach((line, i) => { if (URL.test(line)) bad.push(`${f}:${i + 1}: ${line.trim().slice(0, 140)}`); });
  }
  assert.deepEqual(bad, [], 'پیوندِ GitHub در برنامه — متن درونِ برنامه یا نشانیِ خودِ سرور (`/legal/*`، `/api/releases`)');
});

test('هیچ نوشتهٔ دیدنیِ برنامه نامِ GitHub / گیت‌هاب ندارد', () => {
  const NAME = /github|گیت‌?هاب|ګیټ‌?هب/i;
  const bad = [];
  const kt = [
    ...walk('android-native/app/src/main/java/ir/vil3ntec/tohid/core/i18n'),
    'core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/shopper/ShopperText.kt',
    'core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/shopper/ShopperPlaceText.kt',
    'core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/text/DariText.kt',
    ...walk('core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/legal'),
  ];
  assert.ok(kt.length > 40, `جدول‌ها خوانده نشد (${kt.length})`);
  for (const f of kt) {
    const src = fs.readFileSync(path.join(ROOT, f), 'utf8').split('\n').filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l)).join('\n');
    for (const [, s] of src.matchAll(/"((?:[^"\\\n]|\\.)*)"/g)) if (NAME.test(s)) bad.push(`${f}: «${s}»`);
  }
  for (const f of fs.readdirSync(path.join(ROOT, 'server/public/kit/i18n'))) {
    for (const [k, v] of Object.entries(JSON.parse(fs.readFileSync(path.join(ROOT, 'server/public/kit/i18n', f), 'utf8')))) {
      if (NAME.test(String(v))) bad.push(`kit/i18n/${f} ${k}: «${v}»`);
    }
  }
  assert.ok(!NAME.test(fs.readFileSync(path.join(ROOT, 'core/legal/legal-texts.json'), 'utf8')), 'متنِ حقوقی');
  assert.deepEqual(bad, []);
});
