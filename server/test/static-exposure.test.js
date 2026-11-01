'use strict';
/**
 * SEC-ART-04 (بخشِ همین سرور): از درهای ایستای سرور (‎/admin /store /kit /market /r /portal /sdk‎)
 * هیچ فایلِ نقطه‌دار (‎.env‎، ‎.git‎)، فایلِ بیرون از `public/` (سورس، `package.json`، پشتیبان) و فهرستِ
 * پوشه گرفتنی نیست. `express.static` پیش‌فرض‌های امن دارد (dotfiles: ignore، بی serve-index)؛ این آزمون
 * نگهبانِ همان پیش‌فرض‌هاست تا کسی روزی `dotfiles: 'allow'`، `serve-index` یا یک `static(ROOT)` نگذارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');

const PUBLIC = path.join(__dirname, '..', 'public');
const MOUNTS = ['/admin', '/store', '/kit', '/market', '/r', '/portal', '/sdk'];
const DIR_OF = { '/admin': 'admin', '/store': 'store', '/kit': 'kit', '/market': 'market', '/r': 'receipt', '/portal': 'portal', '/sdk': 'sdk' };
/** نشانه‌هایی که اگر در پاسخ بیایند یعنی فایلِ بیرون از `public/` یا فایلِ نقطه‌دار رسیده */
const LEAK = /"name":\s*"shop-server"|DATABASE_URL|API_SECRET|OTP_SECRET|LICENSE_PRIVATE_KEY|\[core\]|ref: refs\/heads|PostgreSQL database dump|-----BEGIN/;

let base;
const planted = [];
function plant(rel, body) {
  const p = path.join(PUBLIC, rel);
  if (fs.existsSync(p)) return;
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, body);
  planted.push(p);
}

function cleanup() {
  for (const p of planted) fs.rmSync(p, { force: true });
  fs.rmSync(path.join(PUBLIC, 'kit', '.git'), { recursive: true, force: true });
  fs.rmSync(path.join(PUBLIC, 'store', '.backup'), { recursive: true, force: true });
}
//  آزمونِ نیمه‌کاره هم فایلِ کاشته را در public جا نگذارد
process.on('exit', cleanup);

test.before(async () => {
  //  فایل‌های نقطه‌دار و پشتیبانِ ساختگی درونِ خودِ public — بدترین حالت: کسی واقعاً آن‌جا گذاشته‌شان
  plant('admin/.env', 'API_SECRET=planted-for-test\n');
  plant('kit/.git/HEAD', 'ref: refs/heads/main\n');
  plant('store/.backup/shop.sql', '-- PostgreSQL database dump\n');
  base = await h.start();
});
test.after(async () => {
  cleanup();
  await h.stop();
});

async function fetchRaw(p) {
  //  ‎fetch‎ ِ Node مسیر را عادی می‌کند (‎/../‎ را می‌خورد)؛ برای پیمایشِ واقعی، درخواستِ خام
  const http = require('http');
  const u = new URL(base);
  return new Promise((resolve, reject) => {
    const req = http.request({ host: u.hostname, port: u.port, path: p, method: 'GET' }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { body += c; });
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
    req.on('error', reject);
    req.end();
  });
}

test('فایلِ نقطه‌دار (.env، .git، پوشهٔ پشتیبانِ نقطه‌دار) از هیچ درِ ایستا داده نمی‌شود', async () => {
  for (const p of ['/admin/.env', '/kit/.git/HEAD', '/store/.backup/shop.sql', '/admin/.env?x=1', '/kit/%2egit/HEAD', '/admin/%2eenv']) {
    const r = await fetchRaw(p);
    assert.notEqual(r.status, 200, `${p} ⇒ ${r.status}`);
    assert.doesNotMatch(r.body, LEAK, `${p}: محتوای فایل رسید`);
  }
});

test('پیمایشِ مسیر به بیرون از public (سورس، package.json، .env ِ سرور) بسته است', async () => {
  const tries = [];
  for (const m of MOUNTS) {
    tries.push(`${m}/../package.json`, `${m}/..%2f..%2fpackage.json`, `${m}/%2e%2e/%2e%2e/package.json`,
      `${m}/..%2f..%2fsrc%2fconfig.js`, `${m}/%2e%2e%2f%2e%2e%2f.env`, `${m}/....//....//package.json`,
      `${m}/..%5c..%5cpackage.json`);
  }
  tries.push('/.env', '/.git/HEAD', '/.git/config', '/package.json', '/src/config.js', '/data/backups/', '/server/.env');
  for (const p of tries) {
    const r = await fetchRaw(p);
    //  ‎/r/:tag/:token‎ و پوسته‌های SPA برای هر مسیری index.html ِ خودشان را می‌دهند (۲۰۰) — آن نشت نیست؛
    //  نشت یعنی محتوای فایلِ بیرون از public
    assert.doesNotMatch(r.body, LEAK, `${p}: فایلِ بیرون از public رسید (${r.status})`);
    if (r.status === 200) assert.match(r.body, /<!doctype html|<html/i, `${p}: ۲۰۰ بی صفحهٔ SPA`);
  }
});

test('فهرستِ پوشه (directory listing) هیچ‌جا نیست', async () => {
  for (const m of MOUNTS) {
    const files = fs.readdirSync(path.join(PUBLIC, DIR_OF[m]), { withFileTypes: true }).filter((d) => d.isFile() && d.name !== 'index.html').map((d) => d.name);
    for (const sub of ['/', '/fonts/', '/i18n/']) {
      const r = await fetchRaw(`${m}${sub}`);
      //  خودِ صفحهٔ index (پنل، ویترین) فهرست نیست؛ فقط پاسخی که جز آن است باید بی نامِ فایل‌ها باشد
      const index = path.join(PUBLIC, DIR_OF[m], sub, 'index.html');
      if (fs.existsSync(index) && r.body === fs.readFileSync(index, 'utf8')) continue;
      const rootIndex = path.join(PUBLIC, DIR_OF[m], 'index.html');
      if (fs.existsSync(rootIndex) && r.body === fs.readFileSync(rootIndex, 'utf8')) continue;
      const listed = files.filter((f) => r.body.includes(`href="${f}"`) || r.body.includes(`>${f}<`));
      assert.deepEqual(listed, [], `${m}${sub} فهرستِ فایل‌ها را نشان داد (${r.status})`);
    }
  }
});

test('هیچ source map یا فایلِ پشتیبان/راز درونِ public نیست', () => {
  const bad = [];
  const walk = (d) => {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (planted.includes(p) || /[\\/](?:\.git|\.backup)(?:[\\/]|$)/.test(p)) continue;
      if (e.isDirectory()) { walk(p); continue; }
      if (/\.map$|\.(?:sql|dump|bak|gz|enc|pem|key|jks|p12)$|^\.env/i.test(e.name)) bad.push(path.relative(PUBLIC, p));
    }
  };
  walk(PUBLIC);
  assert.deepEqual(bad, []);
});
