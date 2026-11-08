/**
 * دکانِ آزمایشیِ سنجشِ کارایی ِ ویترین (WEB-15): سرورِ واقعی روی پایگاهِ آزمون، پشتِ همان `encode gzip` ِ
 * `server/Caddyfile`، با ۱۲ کالای منتشرشده در سه دسته و ۱۰ عکسِ JPEG ِ واقعی‌نما (۶۰۰×۶۰۰، ~۳۰KB، ساختهٔ همان کرومیوم
 * با دانهٔ ثابت ⇒ همان بایت‌ها در هر اجرا). دادهٔ ساختگی، نه دکانِ واقعی.
 *
 * `test/web-store-vitals.mjs` (آزمون) و `bench/lighthouse-store.mjs` (Lighthouse ِ اختیاری) هر دو همین را می‌سازند.
 *
 *   const s = await startVitalsStore(browser);   // browser ِ پلی‌رایت، فقط برای ساختنِ عکس‌ها
 *   s.base, s.slug, s.checks ([درست؟، پیام، جزئیات])، await s.close()
 */
import { createRequire } from 'node:module';
import http from 'node:http';
import zlib from 'node:zlib';

const require = createRequire(import.meta.url);

export const PRODUCTS = [
  ['rice', 'برنجِ سیلا', 'کیلو', 120.5, 'خوراکی'], ['oil', 'روغنِ آفتاب‌گردان', 'بوتل', 300, 'خوراکی'],
  ['flour', 'آردِ گندم', 'کیلو', 45, 'خوراکی'], ['sugar', 'بوره', 'کیلو', 80, 'خوراکی'],
  ['tea', 'چای سبز', 'بسته', 150, 'خوراکی'], ['beans', 'لوبیا', 'کیلو', 95, 'خوراکی'],
  ['soap', 'صابون', 'دانه', 40, 'بهداشتی'], ['shampoo', 'شامپو', 'بوتل', 220, 'بهداشتی'],
  ['paste', 'خمیردندان', 'دانه', 90, 'بهداشتی'], ['water', 'آبِ معدنی', 'بوتل', 25, 'نوشیدنی'],
  ['juice', 'آبِ میوه', 'قطی', 60, 'نوشیدنی'], ['cola', 'نوشابه', 'قطی', 50, 'نوشیدنی'],
];
const WITH_PHOTO = PRODUCTS.map((p) => p[0]).filter((id) => id !== 'flour' && id !== 'beans');

/** همان `encode zstd gzip` ِ Caddyfile: پاسخِ متنی فشرده، بقیه همان‌طور */
function gzipProxy(origin) {
  const agent = new http.Agent({ keepAlive: true });
  const TEXT = /^(text\/|application\/(json|javascript|manifest\+json|xml)|image\/svg\+xml)/;
  const server = http.createServer((req, res) => {
    const up = http.request({ host: '127.0.0.1', port: origin, path: req.url, method: req.method, headers: req.headers, agent }, (r) => {
      const h = { ...r.headers };
      delete h.connection; delete h['keep-alive'];
      const zip = TEXT.test(String(h['content-type'] || '')) && !h['content-encoding'] && /\bgzip\b/.test(String(req.headers['accept-encoding'] || ''))
        && r.statusCode !== 204 && r.statusCode !== 304 && req.method !== 'HEAD';
      if (!zip) { res.writeHead(r.statusCode, h); r.pipe(res); return; }
      delete h['content-length']; delete h['transfer-encoding'];
      h['content-encoding'] = 'gzip';
      h.vary = h.vary ? `${h.vary}, Accept-Encoding` : 'Accept-Encoding';
      res.writeHead(r.statusCode, h);
      r.pipe(zlib.createGzip()).pipe(res);
    });
    up.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end(); });
    req.pipe(up);
  });
  return { server, agent };
}

export async function startVitalsStore(browser) {
  process.env.NODE_ENV = 'test';
  process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
  process.env.API_SECRET = 'test-secret-test-secret-test-sec';
  process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
  process.env.BACKUP_ENABLED = 'false';
  process.env.RATE_GENERAL_MAX = '100000';
  const { createApp } = require('../src/app');
  const { query, newId, now, closeDb } = require('../src/db');
  const migrate = require('../src/migrate');
  const pw = require('../src/lib/password');
  const checks = [];

  await query('DROP SCHEMA public CASCADE');
  await query('CREATE SCHEMA public');
  await migrate.run();
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  const app = await createApp({ runMigrations: false });
  const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
  const origin = server.address().port;
  const gz = gzipProxy(origin);
  await new Promise((r) => gz.server.listen(0, '127.0.0.1', r));

  const call = async (method, p, b, t, headers = {}) => {
    const r = await fetch(`http://127.0.0.1:${origin}${p}`, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
      body: b === undefined || b === null ? undefined : JSON.stringify(b) });
    return { status: r.status, body: await r.json().catch(() => null) };
  };
  const email = 'owner@vitals.local', password = 'Passw0rd!test';
  const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
  const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
  const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
    device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  const T = d1.body.accessToken;
  const shopId = (await call('POST', '/api/shop', { name: 'دکانِ آزمایشی' }, T)).body.shop.id;
  const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  for (const key of ['shop.orders', 'shop.storefront']) await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
  const slug = 'vitals-store';
  const put = await call('PUT', '/api/shop/storefront', {
    slug, enabled: true, title: 'سوپرمارکتِ آزمایشی', tagline: 'کالای روزانه، نزدیکِ شما', contactPhone: '0700000001',
    deliveryFeeMinor: 5000, minOrderMinor: 0,
  }, T);
  checks.push([put.status === 200, 'ویترین ساخته و روشن شد', JSON.stringify(put.body).slice(0, 120)]);
  const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
  const pushed = await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2,
    ops: PRODUCTS.map(([id, name, unit, salePrice, category]) => op(id, { name, unit, salePrice, category })) },
  T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
  checks.push([pushed.status === 200, 'کالاها از راهِ همگام‌سازیِ واقعی رسیدند', '']);
  for (const [id] of PRODUCTS) await call('PUT', `/api/shop/storefront/products/${id}`, { published: true }, T);

  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const photos = await page.evaluate((n) => {
    const out = [];
    for (let k = 0; k < n; k++) {
      const c = document.createElement('canvas'); c.width = 600; c.height = 600;
      const g = c.getContext('2d');
      let s = 1234567 + k * 7919;
      const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
      const grd = g.createLinearGradient(0, 0, 600, 600);
      grd.addColorStop(0, `hsl(${k * 40}, 60%, 70%)`); grd.addColorStop(1, `hsl(${k * 40 + 60}, 50%, 35%)`);
      g.fillStyle = grd; g.fillRect(0, 0, 600, 600);
      for (let i = 0; i < 260; i++) {
        g.fillStyle = `hsla(${Math.floor(rnd() * 360)}, 55%, ${Math.floor(30 + rnd() * 50)}%, 0.55)`;
        g.beginPath(); g.arc(rnd() * 600, rnd() * 600, 4 + rnd() * 40, 0, Math.PI * 2); g.fill();
      }
      out.push(c.toDataURL('image/jpeg', 0.72).split(',')[1]);
    }
    return out;
  }, WITH_PHOTO.length);
  await ctx.close();
  let sizes = [];
  for (let k = 0; k < photos.length; k++) {
    const r = await call('PUT', `/api/sync/v1/photos/${WITH_PHOTO[k]}`, { data: photos[k] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });
    if (r.status !== 200) sizes = null;
    else if (sizes) sizes.push(Math.round(Buffer.from(photos[k], 'base64').length / 1024));
  }
  checks.push([Boolean(sizes), 'ده عکسِ واقعی‌نما همگام شد', sizes ? `${sizes.join('، ')} KB` : 'ناکام']);

  return {
    base: `http://127.0.0.1:${gz.server.address().port}`, slug, checks,
    async close() {
      gz.server.close();
      gz.agent.destroy();
      server.close();
      await closeDb();
    },
  };
}
