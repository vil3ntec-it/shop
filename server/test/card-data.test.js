'use strict';
/**
 * S117 — هیچ CVV، PIN ِ کارت یا شمارهٔ کاملِ کارت در دیتابیس، گزارشِ خطا (فایلِ crash) و گزارشِ پشتیبانی نمی‌نشیند.
 *
 *   ۱) هیچ فرم، ستون یا کلیدی برای شمارهٔ کارت، CVV یا رمزِ کارت نیست: پرداخت فقط از صفحهٔ ارائه‌دهنده
 *      است و برنامه‌ها و سرور دادهٔ کارت نمی‌گیرند (نگهبان روی مهاجرت‌ها، سرور، وب‌ها و برنامه‌ها).
 *   ۲) اگر شمارهٔ کارت در متنِ خطا یا لاگِ برنامه بیاید (مثلاً کاربر آن را در کادری نوشت)، گزارشِ خطا
 *      (`POST /api/errors`) و پوشاننده‌ی پنل (`ops-errors.scrub`) آن را «[کارت]» می‌کنند — فقط رشته‌ی
 *      ۱۵ تا ۱۹ رقمی با رقمِ کنترلِ Luhn؛ بارکد، زمان و شناسه‌ی دیگر دست نمی‌خورند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { one } = require('../src/db');
const { maskCards, scrub } = require('../src/lib/ops-errors');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const VISA = '4111111111111111';
const AMEX = '378282246310005';

test('S117: شمارهٔ کاملِ کارت (Luhn، ۱۵ تا ۱۹ رقم، با فاصله یا خط) پوشانده می‌شود؛ بارکد و شناسهٔ دیگر نه', () => {
  assert.equal(maskCards(`کارت ${VISA} بود`), 'کارت [کارت] بود');
  assert.equal(maskCards('4111 1111 1111 1111'), '[کارت]');
  assert.equal(maskCards('4111-1111-1111-1111'), '[کارت]');
  assert.equal(maskCards(`x${AMEX}y`), 'x[کارت]y');
  assert.equal(maskCards('6011000990139424'), '[کارت]');
  //  دست نمی‌خورند: Luhn ِ نادرست، بارکدِ EAN-13، زمانِ میلی‌ثانیه، رشتهٔ رقمیِ بلندتر از کارت
  for (const keep of ['4111111111111112', '6261234567890', '1730000000000', '41111111111111111111', '12345']) {
    assert.equal(maskCards(keep), keep, keep);
  }
  assert.equal(scrub(`خطا برای ${VISA} و ali@example.com`), 'خطا برای [کارت] و [ایمیل]');
});

test('S117: گزارشِ خطای برنامه شمارهٔ کارت را در پیام، stack و لاگ نگه نمی‌دارد', async () => {
  const r = await h.api('POST', '/api/errors', {
    headers: { 'X-App': 'shop', 'X-Device': 'dev-card-1', 'X-App-Version': '2.14.5' },
    body: { message: `NumberFormatException: "${VISA}"`, stack: `at Pay.parse(${AMEX})`, log_tail: 'note=4111 1111 1111 1111' },
  });
  assert.equal(r.status, 202, JSON.stringify(r.body));
  const row = await one('SELECT message, stack, log_tail FROM client_errors WHERE id = $1', [r.body.id]);
  assert.ok(row);
  const all = `${row.message}\n${row.stack}\n${row.log_tail}`;
  assert.ok(!/\d{4}[ -]?\d{4}[ -]?\d{4}/.test(all), all);
  assert.equal(row.message, 'NumberFormatException: "[کارت]"');
  assert.equal(row.log_tail, 'note=[کارت]');
});

const CARD_FIELD = /card_?(number|no|num)\b|cardNumber|\bpan\b|\bcvv2?\b|\bcvc\b|card_?pin|شمار[هۀ]ٔ?\s?کارت|رمز[ِ\s]+دوم/i;
const EXT = new Set(['.js', '.mjs', '.cjs', '.html', '.json', '.sql', '.kt', '.kts']);
const SKIP_DIR = new Set(['node_modules', 'vendor', 'build', '.gradle', 'test', 'tests']);
function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory()) { if (!SKIP_DIR.has(e.name)) walk(path.join(dir, e.name), out); }
    else if (EXT.has(path.extname(e.name)) && !e.name.endsWith('.min.js')) out.push(path.join(dir, e.name));
  }
  return out;
}

test('S117: هیچ ستون، کلید یا کادری برای شمارهٔ کارت، CVV یا رمزِ کارت نیست (مهاجرت‌ها، سرور، وب‌ها، برنامه‌ها)', () => {
  const root = path.join(__dirname, '..', '..');
  const files = [];
  for (const r of ['index.html', 'server/src', 'server/public', 'server/migrations', 'android-native/app/src/main', 'desktop/src/main', 'core/src/commonMain']) {
    const abs = path.join(root, r);
    assert.ok(fs.existsSync(abs), `پوشهٔ ${r} نیست`);
    if (fs.statSync(abs).isDirectory()) walk(abs, files); else files.push(abs);
  }
  assert.ok(files.length > 300, `فقط ${files.length} پرونده`);
  const hits = [];
  for (const f of files) {
    fs.readFileSync(f, 'utf8').split('\n').forEach((line, i) => {
      if (CARD_FIELD.test(line)) hits.push(`${path.relative(root, f)}:${i + 1}: ${line.trim().slice(0, 120)}`);
    });
  }
  assert.deepEqual(hits, [], 'دادهٔ کارت فقط در صفحهٔ ارائه‌دهنده گرفته می‌شود (S117)');
});
