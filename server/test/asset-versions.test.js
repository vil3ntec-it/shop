'use strict';
/**
 * نشانیِ نسخه‌دارِ پرونده‌های ایستای ویترین (CDN؛ `lib/asset-versions.js`، docs/SCALING-fa.md):
 *   • `?v=` ِ برابرِ هشِ امروزِ پرونده ⇒ `public, max-age=31536000, immutable`؛ بی `v` یا `v` ِ کهنه ⇒ همان
 *     `max-age=0` + ETag ِ امروز (نشانی‌های امروز بی‌تغییر)؛
 *   • `ASSET_VERSIONS` خاموش (پیش‌فرض) ⇒ پوسته بایت‌به‌بایت همان پرونده؛ روشن ⇒ همان پوسته با `?v=` و همان CSP.
 */
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const assets = require('../src/lib/asset-versions');

const PUB = path.join(__dirname, '..', 'public');
const SHELL = fs.readFileSync(path.join(PUB, 'store', 'index.html'), 'utf8');

test.before(async () => { await h.start(); });
test.after(async () => { config.storefront.assetVersions = false; await h.stop(); });

test('?v= ِ درست ⇒ یک سال immutable؛ بی v یا v ِ کهنه ⇒ همان امروز', async () => {
  const v = assets.versionOf(path.join(PUB, 'store', 'store.js'));
  assert.match(v, /^[A-Za-z0-9_-]{12}$/);
  const plain = await fetch(`${h.base()}/store/store.js`);
  assert.equal(plain.status, 200);
  assert.equal(plain.headers.get('cache-control'), 'public, max-age=0');
  assert.ok(plain.headers.get('etag'));
  const pinned = await fetch(`${h.base()}/store/store.js?v=${v}`);
  assert.equal(pinned.headers.get('cache-control'), assets.YEAR);
  assert.equal(Buffer.from(await pinned.arrayBuffer()).toString('utf8'), await plain.text(), 'همان بایت‌ها');
  const stale = await fetch(`${h.base()}/store/store.js?v=old000000000`);
  assert.equal(stale.headers.get('cache-control'), 'public, max-age=0', 'نسخهٔ کهنه یک سال نمی‌گیرد');
  const kit = await fetch(`${h.base()}/kit/kit.js?v=${assets.versionOf(path.join(PUB, 'kit', 'kit.js'))}`);
  assert.equal(kit.headers.get('cache-control'), assets.YEAR);
  assert.match(kit.headers.get('content-security-policy') || '', /script-src 'self'/, 'همان CSP');
});

test('versioned: فقط نشانی‌های ایستای موجودِ kit/store/market', () => {
  const out = assets.versioned('<script src="/kit/kit.js"></script><link href="/store/nope.css"><a href="/kit/../x.js"><a href="#/">');
  assert.match(out, /src="\/kit\/kit\.js\?v=[A-Za-z0-9_-]{12}"/);
  assert.ok(out.includes('href="/store/nope.css"'), 'پروندهٔ ناموجود دست نمی‌خورد');
  assert.ok(out.includes('href="/kit/../x.js"'), '«..» دست نمی‌خورد');
  assert.ok(out.includes('href="#/"'));
});

test('پوسته: خاموش ⇒ بایت‌به‌بایت؛ روشن ⇒ با ?v= و همان CSP', async () => {
  const off = await fetch(`${h.base()}/store/no-such-store-xyz`);
  assert.equal(off.status, 404);
  assert.equal(await off.text(), SHELL);
  config.storefront.assetVersions = true;
  const on = await fetch(`${h.base()}/store/no-such-store-xyz`);
  assert.equal(on.status, 404);
  const body = await on.text();
  assert.equal(body, assets.versioned(SHELL));
  assert.match(body, /src="\/store\/store\.js\?v=[A-Za-z0-9_-]{12}"/);
  assert.equal(on.headers.get('cache-control'), 'no-cache', 'خودِ پوسته کش نمی‌شود');
  assert.match(on.headers.get('content-security-policy') || '', /default-src 'self'/);
});
