'use strict';
/**
 * LOC-10 — کدِ پستی در هیچ فرمی اجباری نیست (افغانستان کدِ پستیِ کاربردی ندارد).
 *
 *   ۱) نشانیِ تحویل (`orders.parseAddress`، مشترکِ سفارشِ کارمند، ویترین و «اصلاحِ نشانی») با پین،
 *      نشانه یا نشانیِ متنی، هر کدام به‌تنهایی، پذیرفته است؛ کلیدِ کدِ پستیِ فرستاده‌شده دور ریخته
 *      می‌شود و نبودش هرگز ۴۰۰ نیست.
 *   ۲) نگهبان: در کدِ صفحه‌ها و فرم‌ها (وبِ ویترین، بازارچه، پنل، وبِ مدیریت، برنامهٔ اندروید و
 *      کامپیوتر، core) و در مهاجرت‌های پایگاه هیچ کادر، ستون یا سنجشی برای کدِ پستی نیست؛ اگر روزی
 *      افزوده شود این آزمون جای دقیقش را می‌گوید تا اختیاری بودنش بازبینی شود. سطرهای بازبینی‌شده
 *      (کادرِ اختیاریِ §۴.۴ در نشانیِ تحویلِ قرض‌دار، CRM-09) با متنِ دقیق در `REVIEWED_OPTIONAL`اند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { parseAddress } = require('../src/lib/orders');

const ROOT = path.join(__dirname, '..', '..');

test('LOC-10: نشانیِ بی کدِ پستی پذیرفته است و کلیدِ کدِ پستی دور ریخته می‌شود', () => {
  assert.deepEqual(parseAddress({ lat: 34.53, lng: 69.17 }), { lat: 34.53, lng: 69.17 });
  assert.deepEqual(parseAddress({ landmark: 'پشتِ مسجدِ جامع' }), { landmark: 'پشتِ مسجدِ جامع' });
  assert.deepEqual(parseAddress({ text: 'کارته‌سه، سرکِ دوم' }), { text: 'کارته‌سه، سرکِ دوم' });
  const withZip = parseAddress({ landmark: 'نانوایی', postalCode: '1001', postal_code: '1001', zip: '1001', zipCode: '1001' });
  assert.deepEqual(withZip, { landmark: 'نانوایی' }, 'کلیدِ کدِ پستی در نشانی نمی‌نشیند');
  //  تنها شرطِ نشانی: پین یا نشانه یا متن — نه کدِ پستی
  assert.throws(() => parseAddress({ postalCode: '1001' }), (e) => e.code === 'bad_address');
});

const POSTAL = /postal|post_?code|postcode|zip_?code|zipcode|کد[\s‌]?پستی/i;

//  بازبینیِ اختیاری بودن (۱۴۰۵/۰۸/۱۲، CRM-09 ِ #887): §۴.۴ ِ مشخصات در فرمِ نشانی «کدِ پستیِ **اختیاری**» می‌خواهد و
//  نشانیِ تحویلِ قرض‌دار (`Debtor.deliveryAddress`) آن را دارد. فقط همین سطرها، **دقیقاً با همین متن**، پذیرفته‌اند و
//  چرا اختیاری‌اند کنارِ هر کدام آمده؛ سطرِ تازه در هر جای دیگر، یا هر تغییرِ همین سطرها (مثلاً برداشتنِ `isNotEmpty()`
//  یا پیش‌فرضِ خالی) ⇒ دوباره سرخ و دوباره بازبینی. وب، سرور و مهاجرت‌ها هیچ سطرِ پذیرفته‌ای ندارند.
const KT = 'android-native/app/src/main/java/ir/vil3ntec/tohid/';
const REVIEWED_OPTIONAL = new Map([
  [KT + 'core/i18n/StrMoney.kt|val addressPostal: String get() = t("کدِ پستی (اختیاری)", "Postal code (optional)", "پوستي کوډ (اختیاري)")', 'برچسبِ کادر با «(اختیاری)» در هر سه زبان'],
  [KT + 'data/CustomerCrm.kt|private val POSTAL = Regex("^[0-9]{4,10}$")', 'شکلِ رقم، فقط برای مقدارِ پر (سطرِ isNotEmpty)'],
  [KT + 'data/CustomerCrm.kt|val postal = ir.vil3ntec.tohid.latinDigits(a.postalCode).filterNot { it.isWhitespace() }', 'یکدست‌کردنِ رقم؛ خالی خالی می‌ماند'],
  [KT + 'data/CustomerCrm.kt|if (postal.isNotEmpty() && !POSTAL.matches(postal)) return DebtorEngine.Result.Failed(StrLedger.postalDigitsOnly)', 'سنجش فقط وقتی پر است؛ خالی هرگز خطا نیست'],
  //  دریِ دورِ ۷ (#983): همان پیام به جدولِ StrLedger رفت — همان سنجشِ «فقط وقتی پر است»ِ سطرِ بالا
  [KT + 'core/i18n/StrLedger.kt|val postalDigitsOnly: String get() = t("کدِ پستی فقط رقم است", "The postal code is digits only", "پوستي کوډ یوازې عددونه دي")', 'متنِ خطای همان سنجشِ اختیاری'],
  [KT + 'data/CustomerCrm.kt|val next = DeliveryAddress(area, landmark, gate, floor, plus, postal).takeIf { it != DeliveryAddress() }', 'خالی ⇒ بخشِ خالی (نوشته نمی‌شود)'],
  [KT + 'data/CustomerCrm.kt|listOf(a.landmark, a.area, a.gate, a.floor, a.plusCode, a.postalCode).filter { it.isNotBlank() }.joinToString(" · ")', 'نمایش؛ بخشِ خالی نمی‌آید'],
  [KT + 'data/DeliveryAddress.kt|*  منزل؛ `floor` = طبقه؛ `plusCode` = Plus Code (بزرگ)؛ `postalCode` = فقط رقم.', 'شرح؛ همان‌جا: «هر بخش اختیاری»'],
  [KT + 'data/DeliveryAddress.kt|@OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val postalCode: String = "",', 'پیش‌فرضِ خالی و نوشته نمی‌شود'],
  [KT + 'ui/v3/money/DebtorFormSheets.kt|V3TextField(f.address.postalCode, { f = f.copy(address = f.address.copy(postalCode = it)) }, StrMoney.addressPostal, Modifier.weight(1f),', 'کادرِ اختیاری با همان برچسب'],
]);
const EXT = new Set(['.js', '.mjs', '.cjs', '.html', '.json', '.sql', '.kt', '.kts', '.css']);
const SKIP_DIR = new Set(['node_modules', 'vendor', 'build', '.gradle', 'test', 'tests']);

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) {
      if (!SKIP_DIR.has(e.name)) walk(path.join(dir, e.name), out);
    } else if (EXT.has(path.extname(e.name)) && !e.name.endsWith('.min.js')) {
      out.push(path.join(dir, e.name));
    }
  }
  return out;
}

test('LOC-10: هیچ فرم، سنجش یا ستونی کدِ پستی نمی‌خواهد (وب، برنامه‌ها، سرور، مهاجرت‌ها)', () => {
  const roots = [
    'index.html',
    'server/src',
    'server/public',
    'server/migrations',
    'android-native/app/src/main',
    'desktop/src/main',
    'core/src/commonMain',
  ];
  const files = [];
  for (const r of roots) {
    const abs = path.join(ROOT, r);
    assert.ok(fs.existsSync(abs), `پوشهٔ ${r} نیست — نگهبان بی‌صدا تهی نشود`);
    if (fs.statSync(abs).isDirectory()) walk(abs, files);
    else files.push(abs);
  }
  assert.ok(files.length > 300, `فقط ${files.length} پرونده خوانده شد`);
  const hits = [];
  const seen = new Set();
  for (const f of files) {
    const rel = path.relative(ROOT, f).split(path.sep).join('/');
    const lines = fs.readFileSync(f, 'utf8').split('\n');
    lines.forEach((line, i) => {
      if (!POSTAL.test(line)) return;
      const key = `${rel}|${line.trim()}`;
      if (REVIEWED_OPTIONAL.has(key)) seen.add(key);
      else hits.push(`${rel}:${i + 1}: ${line.trim().slice(0, 120)}`);
    });
  }
  assert.deepEqual(hits, [], 'کدِ پستی پیدا شد — باید اختیاری بماند (LOC-10)');
  //  فهرستِ بازبینی‌شده کهنه نماند: سطری که دیگر نیست باید از فهرست برود
  assert.deepEqual([...REVIEWED_OPTIONAL.keys()].filter((k) => !seen.has(k)), [], 'سطرِ بازبینی‌شده دیگر نیست — از REVIEWED_OPTIONAL بردار');
});
