'use strict';
/**
 * F0-22 — CSP ِ نسخهٔ وب: اتصال فقط به خودِ سایت و سرورِ همین نسخه.
 *
 * پیش‌تر `connect-src` هر `https: http: wss: ws:` را می‌پذیرفت، به این
 * دلیل که «نشانیِ سرور را دکان‌دار می‌تواند عوض کند». ولی نشانی در
 * `<meta name="tohid-api-base">` قفل است (`license/api-config.js`:
 * `setBaseUrl` در نسخهٔ قفل هیچ کاری نمی‌کند)، و تنها کدی که به نشانیِ
 * دستیِ دیگری وصل می‌شد ورودِ قدیمیِ WebSocket بود که هیچ‌جا صدا زده
 * نمی‌شود. پس دیوار باید همان‌قدر تنگ باشد که خودِ برنامه.
 *
 * ⛔ اگر روزی `tohid-api-base` عوض شد، `connect-src` هم باید عوض شود —
 * همین آزمون آن دو را با هم می‌سنجد.
 * (حذفِ `unsafe-inline` ِ اسکریپت کارِ فاز ۲ است، نه این‌جا.)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const raw = fs.readFileSync(path.join(__dirname, '..', '..', 'index.html'), 'utf8');
const html = raw.replace(/<!--[\s\S]*?-->/g, '');   // نمونه‌های داخلِ کامنت به حساب نیایند
const csp = (html.match(/<meta http-equiv="Content-Security-Policy" content="([^"]+)">/) || [])[1] || '';
const directive = (name) => {
  const d = csp.split(';').map((s) => s.trim()).find((s) => s.split(/\s+/)[0] === name);
  return d ? d.split(/\s+/).slice(1) : null;
};
const metas = [...html.matchAll(/<meta name="tohid-api-base" content="([^"]*)">/g)].map((m) => m[1]);

test('CSP ِ وب هست و نشانیِ سرور یکی است', () => {
  assert.ok(csp, 'تگِ CSP در index.html پیدا نشد');
  assert.equal(metas.length, 1, `باید دقیقاً یک tohid-api-base ِ واقعی باشد، نه ${metas.length}`);
  assert.match(metas[0], /^https:\/\/[^/]+$/, 'نشانیِ سرور باید https و بی مسیر باشد');
});

test('connect-src هیچ طرحِ بازی (https: http: ws: wss: *) نمی‌پذیرد', () => {
  const src = directive('connect-src');
  assert.ok(src, 'connect-src نیست — یعنی default-src تصمیم می‌گیرد؛ صریح بنویسید');
  const open = src.filter((s) => /^(\*|https?:|wss?:)$/i.test(s));
  assert.deepEqual(open, [], `connect-src هنوز باز است: ${open.join(' ')}`);
});

test('connect-src خودِ سایت و سرورِ همین نسخه را (https و wss) دارد — و جز آن هیچ میزبانی', () => {
  const src = directive('connect-src');
  const origin = new URL(metas[0]).origin;
  const ws = origin.replace(/^https:/, 'wss:');
  for (const need of ["'self'", origin, ws]) assert.ok(src.includes(need), `connect-src باید ${need} را داشته باشد`);
  const hosts = src.filter((s) => /^[a-z]+:\/\//i.test(s));
  assert.deepEqual(hosts.filter((h) => h !== origin && h !== ws), [], 'میزبانِ اضافه در connect-src');
});
