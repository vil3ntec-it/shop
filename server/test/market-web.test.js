'use strict';
/**
 * پوستهٔ وبِ بازارچه و کیتِ مشترک (WEB، §۲۳): بازارچهٔ خاموش ⇒ ۴۰۴، CSP ِ سخت،
 * بی اسکریپت/style ِ درون‌خطی، manifest ِ نصب، و هیچ پیوندی به برنامهٔ مدیریت.
 * (رفتارِ مرورگری در `test/web-market.mjs`.)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

const PUB = path.join(__dirname, '..', 'public');
let admin = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

test('بازارچه: خاموش ۴۰۴، روشن ۲۰۰، CSP ِ سخت و کیت از همین مبدأ', async () => {
  const off = await fetch(`${h.base()}/market/`);
  assert.equal(off.status, 404, 'پیش‌فرض خاموش');
  assert.match(await off.text(), /بازارچه هنوز باز نیست/, 'همان پوسته با پیامِ روشن');
  const r = await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'market.directory', value: true }, { token: admin });
  assert.equal(r.status, 200);
  for (const p of ['/market', '/market/']) {
    const on = await fetch(`${h.base()}${p}`);
    assert.equal(on.status, 200, p);
    const csp = on.headers.get('content-security-policy') || '';
    assert.match(csp, /default-src 'self'; script-src 'self'; style-src 'self';/);
    assert.doesNotMatch(csp, /unsafe-inline|unsafe-eval/);
    assert.match(csp, /connect-src 'self';/);
    assert.match(on.headers.get('permissions-policy') || '', /geolocation=\(self\)/);
  }
  for (const f of ['/kit/kit.css', '/kit/kit.js', '/kit/icon.svg', '/kit/icon-192.png', '/kit/icon-512.png',
    '/kit/icon-maskable-512.png', '/kit/fonts/vazirmatn-regular.woff2', '/market/market.js']) {
    const res = await fetch(`${h.base()}${f}`);
    assert.equal(res.status, 200, f);
  }
  const man = await (await fetch(`${h.base()}/market/manifest.webmanifest`)).json();
  assert.equal(man.start_url, '/market/');
  assert.equal(man.display, 'standalone');
  assert.ok(man.icons.some((i) => i.sizes === '512x512' && i.purpose === 'maskable'));
  await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'market.directory', value: false }, { token: admin });
});

test('پوسته: بی اسکریپت/style ِ درون‌خطی، بی پیوند به برنامهٔ مدیریت، JS زیرِ ۷۵KB', () => {
  const html = fs.readFileSync(path.join(PUB, 'market', 'index.html'), 'utf8');
  assert.doesNotMatch(html, /<script(?![^>]*\ssrc=)[^>]*>/i);
  assert.doesNotMatch(html, /\sstyle=|\son[a-z]+=/i);
  const all = ['market/index.html', 'market/market.js', 'kit/kit.js', 'kit/boot.js', 'store/index.html', 'store/store.js', 'store/order.js', 'store/checkout.js', 'store/share.js', 'store/recent.js']
    .map((f) => fs.readFileSync(path.join(PUB, f), 'utf8')).join('\n');
  assert.doesNotMatch(all, /index\.html|\/admin\b|\/portal\b/, 'هیچ پیوندی به برنامهٔ مدیریتِ دکان یا پنل');
  assert.doesNotMatch(all, /\.(innerHTML|outerHTML)\s*=|insertAdjacentHTML\(|document\.write\(|\beval\(|new Function\(/, 'دادهٔ سرور فقط با textContent');
  const size = ['kit/kit.js', 'market/market.js'].reduce((s, f) => s + fs.statSync(path.join(PUB, f)).size, 0);
  assert.ok(size < 75 * 1024, `${size} بایت`);
});
