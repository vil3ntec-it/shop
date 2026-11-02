'use strict';
/**
 * LOC-10 — کدِ پستی در هیچ فرمی اجباری نیست (افغانستان کدِ پستیِ کاربردی ندارد).
 *
 *   ۱) نشانیِ تحویل (`orders.parseAddress`، مشترکِ سفارشِ کارمند، ویترین و «اصلاحِ نشانی») با پین،
 *      نشانه یا نشانیِ متنی، هر کدام به‌تنهایی، پذیرفته است؛ کلیدِ کدِ پستیِ فرستاده‌شده دور ریخته
 *      می‌شود و نبودش هرگز ۴۰۰ نیست.
 *   ۲) نگهبان: در کدِ صفحه‌ها و فرم‌ها (وبِ ویترین، بازارچه، پنل، وبِ مدیریت، برنامهٔ اندروید و
 *      کامپیوتر، core) و در مهاجرت‌های پایگاه هیچ کادر، ستون یا سنجشی برای کدِ پستی نیست؛ اگر روزی
 *      افزوده شود این آزمون جای دقیقش را می‌گوید تا اختیاری بودنش بازبینی شود.
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
  for (const f of files) {
    const lines = fs.readFileSync(f, 'utf8').split('\n');
    lines.forEach((line, i) => { if (POSTAL.test(line)) hits.push(`${path.relative(ROOT, f)}:${i + 1}: ${line.trim().slice(0, 120)}`); });
  }
  assert.deepEqual(hits, [], 'کدِ پستی پیدا شد — باید اختیاری بماند (LOC-10)');
});
