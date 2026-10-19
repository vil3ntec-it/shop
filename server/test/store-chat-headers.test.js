'use strict';
/**
 * سرآیندهای امنیتیِ صفحهٔ ویترین پس از چتِ مشتری (v3 §۳۳).
 *
 *   • میکروفون فقط برای صفحهٔ ویترین و فقط همان مبدأ: `microphone=(self)`؛
 *     پنلِ مدیر همان `microphone=()` می‌ماند (پیامِ صوتیِ مشتری، نه بیشتر).
 *   • CSP هنوز سخت است: بی 'unsafe-inline' و بی 'unsafe-eval'، اسکریپت فقط
 *     'self'؛ تنها افزوده `blob:` برای img/media (عکس و صدای چت که با بلیتِ
 *     سرآیندی خوانده و در همین صفحه نشان داده می‌شوند).
 *   • دوربین و پرداخت همچنان بسته‌اند؛ مکان همان (self) ِ پینِ نشانی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const directives = (csp) => Object.fromEntries(String(csp || '').split(';').map((d) => d.trim()).filter(Boolean)
  .map((d) => { const [k, ...v] = d.split(/\s+/); return [k, v]; }));

test('صفحهٔ ویترین: میکروفون فقط (self)، CSP بی unsafe-inline، blob فقط برای img/media', async () => {
  for (const p of ['/store/demo-shop', '/store/store.js', '/store/chat-core.js']) {
    const r = await h.get(p);
    const pp = r.headers.get('permissions-policy');
    assert.match(pp, /(^|,\s*)microphone=\(self\)(,|$)/, `${p}: ${pp}`);
    assert.match(pp, /camera=\(\)/);
    assert.match(pp, /payment=\(\)/);
    assert.match(pp, /geolocation=\(self\)/);
    const csp = r.headers.get('content-security-policy');
    assert.ok(csp, `${p} بی CSP`);
    assert.doesNotMatch(csp, /unsafe-inline|unsafe-eval/);
    const d = directives(csp);
    assert.deepEqual(d['script-src'], ["'self'"]);
    assert.deepEqual(d['style-src'], ["'self'"]);
    assert.deepEqual(d['connect-src'], ["'self'"]);
    assert.deepEqual(d['img-src'], ["'self'", 'data:', 'blob:']);
    assert.deepEqual(d['media-src'], ["'self'", 'blob:']);
    assert.deepEqual(d['object-src'], ["'none'"]);
    assert.deepEqual(d['frame-ancestors'], ["'none'"]);
  }
});

test('پنلِ مدیر: میکروفون همچنان بسته', async () => {
  const r = await h.get('/admin/');
  assert.match(r.headers.get('permissions-policy'), /microphone=\(\)/);
});

test('هستهٔ چتِ ویترین همان فایلِ وب است و با نوعِ اسکریپت سرو می‌شود', async () => {
  const r = await h.download('/store/chat-core.js');
  assert.equal(r.status, 200);
  assert.match(r.headers.get('content-type'), /javascript/);
  const web = require('fs').readFileSync(require('path').join(__dirname, '..', '..', 'license', 'chat-core.js'));
  assert.ok(r.buffer.equals(web));
});
