'use strict';
/**
 * ⛔ سقفِ نرخِ ورود با کدِ شاگرد (`/auth/staff`): به‌ازای هر IP (`joinLimit`) **و** به‌ازای هر کد (`staffCodeLimit`،
 * کلید HMAC ِ کد). کدِ لورفته از چند اینترنت هم بیش از `RATE_JOIN_CODE_MAX` در پنجره امتحان نمی‌شود.
 * عددهای کوچک فقط برای همین پرونده (هر پرونده فرایندِ جدا دارد)؛ `TRUST_PROXY` تا IP ِ ساختگی از loopback خوانده شود.
 */
process.env.RATE_JOIN_MAX = '6';
process.env.RATE_JOIN_CODE_MAX = '3';
process.env.TRUST_PROXY = 'true';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { resetAll } = require('../src/middleware/ratelimit');

const from = (ip) => ({ headers: { 'X-Forwarded-For': ip } });
const code = (n) => `SHG-AAAA-BBBB-${String(n).padStart(4, '2').replace(/[01]/g, '2').slice(-4)}`;

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('یک کد از چند IP: پس از سقفِ «هر کد» ۴۲۹، حتی از IP ِ تازه', async () => {
  resetAll();
  const same = 'SHG-ZZZZ-YYYY-XXXX';
  for (let i = 1; i <= 3; i++) {
    const r = await h.post('/api/auth/staff', { code: same, device: { uid: `lim-${i}` } }, from(`203.0.113.${i}`));
    assert.notEqual(r.status, 429, `تلاشِ ${i} نباید بسته باشد`);
    assert.equal(r.body.error.code, 'bad_code');
  }
  const blocked = await h.post('/api/auth/staff', { code: same, device: { uid: 'lim-4' } }, from('203.0.113.99'));
  assert.equal(blocked.status, 429, JSON.stringify(blocked.body));
  //  کدِ دیگر از همان IP ِ تازه باز است (سطلِ کد، نه سطلِ همه)
  const other = await h.post('/api/auth/staff', { code: 'SHG-ZZZZ-YYYY-WWWW', device: { uid: 'lim-5' } }, from('203.0.113.99'));
  assert.notEqual(other.status, 429);
  //  شکلِ دیگرِ همان کد (کوچک، بی خط) همان سطل است
  const lower = await h.post('/api/auth/staff', { code: 'shgzzzzyyyyxxxx', device: { uid: 'lim-6' } }, from('203.0.113.98'));
  assert.equal(lower.status, 429);
});

test('یک IP با کدهای گوناگون: پس از سقفِ «هر IP» ۴۲۹', async () => {
  resetAll();
  for (let i = 1; i <= 6; i++) {
    const r = await h.post('/api/auth/staff', { code: code(i + 2), device: { uid: `ip-${i}` } }, from('198.51.100.7'));
    assert.notEqual(r.status, 429, `تلاشِ ${i}`);
  }
  const blocked = await h.post('/api/auth/staff', { code: code(9), device: { uid: 'ip-7' } }, from('198.51.100.7'));
  assert.equal(blocked.status, 429, JSON.stringify(blocked.body));
  assert.notEqual((await h.post('/api/auth/staff', { code: code(9), device: { uid: 'ip-8' } }, from('198.51.100.8'))).status, 429);
});
