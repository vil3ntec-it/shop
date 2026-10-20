'use strict';
/**
 * لاگِ درخواست بی توکن (بازبینیِ حریم): توکنِ پیگیریِ سفارش، توکنِ پرونده و هر
 * بخشِ توکن‌مانندِ مسیر در لاگِ ۴xx/کند/۵xx به `:tok` بدل می‌شود؛ نامِ مسیر و
 * نشانیِ ویترین می‌مانند تا لاگ هنوز به کار بیاید.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { redactPath } = require('../src/lib/log-redact');

const ORDER_TOKEN = '01M5AZC5CRTSERSECA2KK4NQ9X.AjLLolSdBBRU2LkvfLxFDya_Q4NfKjEC';

test('redactPath: بخشِ توکن‌مانند ⇒ `:tok`؛ نامِ مسیر، نشانی و شناسهٔ کوتاه می‌مانند', () => {
  assert.equal(redactPath(`/api/store/golestan-test/orders/${ORDER_TOKEN}`), '/api/store/golestan-test/orders/:tok');
  assert.equal(redactPath(`/api/store/golestan-test/orders/${ORDER_TOKEN}/cancel`), '/api/store/golestan-test/orders/:tok/cancel');
  assert.equal(redactPath('/api/store/v-mvfpsx771/trust/cases/tc_57c7ffd1d4a80467fc3bd714'), '/api/store/v-mvfpsx771/trust/cases/:tok');
  assert.equal(redactPath('/api/shop/storefront/products/rice'), '/api/shop/storefront/products/rice');
  assert.equal(redactPath('/api/admin/pump/live-config'), '/api/admin/pump/live-config');
  assert.equal(redactPath('/api/store/x/account/logout-all'), '/api/store/x/account/logout-all');
  //  JWT، هگزِ بلند، شمارهٔ تلفن و درصد-رمزشده
  assert.equal(redactPath('/r/eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig'), '/r/:tok');
  assert.equal(redactPath('/f/9f86d081884c7d659a2feaa0c55ad015'), '/f/:tok');
  assert.equal(redactPath('/c/%2B93790000201'), '/c/:tok');
  assert.equal(redactPath('/c/0790000201'), '/c/:tok');
  assert.equal(redactPath('/api/store/x/orders/2'), '/api/store/x/orders/2');
  //  querystring هرگز
  assert.equal(redactPath('/api/store/x/delivery?lat=34.5&lng=69.1'), '/api/store/x/delivery');
  assert.equal(redactPath(''), '');
});

test('⛔ لاگِ ۴۰۴ ِ پیگیریِ سفارش توکن را ندارد', async () => {
  await h.start();
  const lines = [];
  const orig = { log: console.log, warn: console.warn, error: console.error };
  const grab = (...a) => { lines.push(a.map(String).join(' ')); };
  console.log = grab; console.warn = grab; console.error = grab;
  try {
    await h.get(`/api/store/no-such-shop/orders/${ORDER_TOKEN}`);
    await h.get(`/api/store/no-such-shop/orders/${ORDER_TOKEN}/reorder?x=1`);
    //  لاگ پس از «finish» نوشته می‌شود
    await new Promise((r) => setTimeout(r, 50));
  } finally {
    Object.assign(console, orig);
    await h.stop();
  }
  const out = lines.join('\n');
  assert.ok(out.includes('/api/store/no-such-shop/orders/:tok'), out);
  assert.ok(!out.includes('AjLLolSd') && !out.includes('01M5AZC5CRTSERSECA2KK4NQ9X'), `توکن در لاگ: ${out}`);
});
