/**
 * صفحهٔ «ویترین» ِ صاحب دکان در نسخهٔ وب — با کرومیومِ واقعی و سرورِ واقعی.
 *
 *     npm run test:web-storefront-owner
 *
 * `index.html` از یک وب‌سرورِ ایستا (همان چیدمانِ GitHub Pages) باز می‌شود و
 * هر درخواستش به `tohid-api-base` به سرورِ واقعیِ همین آزمون (PGlite) می‌رود.
 *   • بی پرچم کارتِ «ویترین» دیده نمی‌شود (دکمهٔ بی‌اثر نیست)؛ با پرچم دیده می‌شود.
 *   • تنظیمات: خطای نشانیِ رزرو کنارِ کادر؛ ذخیره ⇒ همان در سرور.
 *   • وضعیت (§۳۱ PRES): برچسبِ مشتری؛ «آفلاین» ثبت می‌شود؛ بی شبکه اعلام در صف
 *     می‌ماند و «ثبت شد» نمی‌گوید (PRES-02/09)؛ «دوباره بفرست» همان رویداد را
 *     یک بار ثبت می‌کند؛ «آنلاین تا…» ساعتِ دکان را نشان می‌دهد (PRES-01).
 *   • کالاها: انتشارِ تکی، انتشارِ گروهی با گزارش، قیمتِ آنلاین و سقف؛ کارت/جدول.
 *   • بی بیرون‌زدگی در ۳۶۰ و ۱۲۸۰، axe-core، بی خطای جاوااسکریپت.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}

process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
process.env.RATE_GENERAL_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
process.env.OTP_UNIFORM_MS = '0';
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pwHash = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const step = async (name, fn) => { try { await fn(); } catch (e) { ok(false, name, e.message.split('\n')[0]); } };

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
             VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pwHash.hashPassword('Admin!12345'), now()]);
const app = await createApp({ runMigrations: false });
const api = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const apiBase = `http://127.0.0.1:${api.address().port}`;

const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(apiBase + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  return { status: r.status, body: await r.json().catch(() => null) };
};

//  ── دکانِ آزمایشی (دادهٔ ساختگی) ──
const email = 'owner@sfowner.local', password = 'Passw0rd!test';
const s1 = await call('POST', '/api/auth/register/start', { name: 'صاحب', email, password });
const v1 = await call('POST', '/api/auth/register/verify', { email, code: s1.body.devCode });
const d1 = await call('POST', '/api/auth/register/complete', { ticket: v1.body.ticket, name: 'صاحب', password,
  device: { deviceId: 'dev-owner', name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
const T = d1.body.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ آزمایشی' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner', schema_version: 2, ops: [
  op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
  op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
] }, T, { 'X-App': 'shop', 'X-Device': 'dev-owner' });

//  ── وب‌سرورِ ایستا: همان فایل‌های سایت ──
const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.ttf': 'font/ttf', '.mp3': 'audio/mpeg' };
const site = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => site.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${site.address().port}/index.html`;

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium/chrome-linux/chrome' }).catch(() => chromium.launch());
const errors = [];
let offlinePresence = false;

async function open(width) {
  const ctx = await browser.newContext({ viewport: { width, height: 820 }, locale: 'fa-AF' });
  const pg = await ctx.newPage();
  pg.on('pageerror', (e) => errors.push(String(e)));
  pg.on('dialog', async (d) => {
    const m = d.message();
    if (/قیمتِ آنلاینِ «روغن»/.test(m)) await d.accept('290');
    else if (/سقفِ هر سفارشِ «روغن»/.test(m)) await d.accept('5');
    else await d.dismiss();
  });
  await pg.goto(url);
  const base = await pg.evaluate(() => window.TohidApiConfig.baseUrl());
  //  هر درخواستِ برنامه به سرورِ واقعیِ همین آزمون
  await pg.route(`${base}/**`, async (route) => {
    const u = new URL(route.request().url());
    if (offlinePresence && u.pathname.endsWith('/shop/storefront/presence')) return route.abort('internetdisconnected');
    //  درخواستِ پس‌زمینه‌ای (نوسازیِ نشست، همگام‌سازی) ممکن است وقتی هنوز در راه است صفحه بسته شود؛
    //  آن خطا از آزمون نیست. هر خطای دیگر همچنان آزمون را می‌شکند.
    try {
      const response = await route.fetch({ url: apiBase + u.pathname + u.search });
      await route.fulfill({ response });
    } catch (e) {
      if (!/closed|disposed/i.test(String(e && e.message || e))) throw e;
    }
  });
  await pg.evaluate((tok) => {
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'صاحب', loginAt: 1 }));
    localStorage.setItem('tohid-license-v1', JSON.stringify({
      accessToken: tok.access, refreshToken: tok.refresh, accessExpiresAt: Date.now() + 3600_000, userId: tok.userId, userLabel: 'صاحب',
    }));
  }, { access: T, refresh: d1.body.refreshToken, userId: d1.body.user && d1.body.user.id });
  await pg.reload();
  await pg.waitForFunction(() => window.TohidStorefrontOwner && window.TohidShop, null, { timeout: 15000 });
  return { ctx, pg };
}
const overflow = (pg) => pg.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);

//  ۱) بی پرچم: کارت پنهان
await step('بی پرچم', async () => {
  const { ctx, pg } = await open(1280);
  await pg.evaluate(() => goTo('more'));
  await pg.evaluate(() => window.TohidStorefrontOwner.refreshGate());
  ok(await pg.evaluate(() => document.getElementById('more-storefront').hidden === true), 'بی پرچم کارتِ «ویترین» دیده نمی‌شود (دکمهٔ بی‌اثر نیست)');
  await ctx.close();
});

for (const key of ['shop.orders', 'shop.storefront']) {
  await call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value: true }, A);
}

const { ctx, pg } = await open(1280);
await step('ورود به صفحه', async () => {
  await pg.evaluate(() => goTo('more'));
  await pg.waitForFunction(() => document.getElementById('more-storefront').hidden === false, null, { timeout: 10000 });
  ok(true, 'با پرچم، کارتِ «ویترین» در «بیشتر» پیدا شد');
  await pg.click('#more-storefront');
  await pg.waitForSelector('#page-storefront.active #sf-root .sf-tabs', { timeout: 10000 });
  ok((await pg.textContent('#sf-root')).includes('هنوز ویترینی ساخته نشده'), 'پیش از ساختن: راهنمای «تنظیمات»');
});

await step('تنظیمات', async () => {
  await pg.click('[data-sf-tab="settings"]');
  await pg.fill('#sf-slug', 'api');
  await pg.fill('#sf-title', 'ویترینِ آزمایشی');
  await pg.click('#sf-save');
  ok((await pg.textContent('#sf-settings')).includes('این نشانی رزرو است'), 'نشانیِ رزرو: خطا کنارِ همان کادر، بی درخواست');
  ok((await pg.inputValue('#sf-title')) === 'ویترینِ آزمایشی', 'نوشته‌ها پس از خطا سرِ جایشان‌اند');
  await pg.fill('#sf-slug', 'demo-owner');
  await pg.fill('#sf-phone', '0700000001');
  await pg.fill('#sf-fee', '۵۰');
  for (const d of [6, 0, 1, 2, 3, 4]) await pg.fill(`#sf-day${d}`, '08:00-20:00');
  await pg.fill('#sf-day5', '20:00-02:00');
  await pg.check('#sf-enabled');
  await pg.click('#sf-save');
  ok((await pg.textContent('#sf-settings')).includes('شبِ پس از ۲۴'), 'عبور از نیمه‌شب در همان روز رد می‌شود (PRES-06)');
  await pg.fill('#sf-day5', '');
  await pg.click('#sf-save');
  await pg.waitForFunction(() => !document.getElementById('sf-save') || /ذخیرهٔ ویترین/.test(document.getElementById('sf-save').textContent), null, { timeout: 10000 });
  await pg.waitForTimeout(400);
  const cfg = (await call('GET', '/api/shop/storefront/config', null, T)).body.settings;
  ok(cfg && cfg.slug === 'demo-owner' && cfg.enabled && cfg.deliveryFeeMinor === 5000 && cfg.weeklyHours['6'] && !cfg.weeklyHours['5'],
    'تنظیمات همان‌طور در سرور نشست', JSON.stringify(cfg && { slug: cfg.slug, fee: cfg.deliveryFeeMinor, hours: cfg.weeklyHours }));
});

await step('وضعیت', async () => {
  await pg.click('[data-sf-tab="status"]');
  await pg.waitForSelector('#sf-badge');
  ok(/باز است|بسته است|آفلاین|اعلام شده/.test(await pg.textContent('#sf-badge')), 'برچسبِ وضعیت همان جملهٔ مشتری', await pg.textContent('#sf-badge'));
  await pg.click('[data-sf-mode="offline"]');
  await pg.waitForSelector('#sf-notice', { timeout: 10000 });
  ok((await pg.textContent('#sf-notice')).includes('ثبت شد') && (await pg.textContent('#sf-badge')) === 'موقتاً آفلاین', '«آفلاین» ثبت شد و برچسب همان شد');

  //  PRES-02/09: بی شبکه
  offlinePresence = true;
  await pg.click('[data-sf-mode="closed"]');
  await pg.waitForSelector('#sf-pending', { timeout: 10000 });
  ok((await pg.textContent('#sf-root')).includes('هنوز به سرور نرسیده') && !(await pg.textContent('#sf-root')).includes('ثبت شد —'),
    'بی شبکه: هشدارِ «هنوز به سرور نرسیده»، بی ادعای ثبت (PRES-02/09)');
  ok((await pg.textContent('#sf-badge')) === 'موقتاً آفلاین', 'وضعیتِ عمومی تا تأییدِ سرور عوض نشد');
  ok(await pg.isDisabled('[data-sf-mode="online"]'), 'تا اعلامِ در صف نرفته، اعلامِ دیگری روی آن نمی‌نشیند');
  offlinePresence = false;
  await pg.click('[data-sf="flush"]');
  await pg.waitForFunction(() => !document.getElementById('sf-pending'), null, { timeout: 10000 });
  ok((await pg.textContent('#sf-badge')) === 'بسته است', '«دوباره بفرست» ⇒ همان اعلام ثبت شد');
  const n = await one(`SELECT COUNT(*)::int AS n FROM storefront_presence_events WHERE shop_id=$1 AND kind='presence'`, [shopId]);
  ok(n.n === 2, 'هر اعلام یک رویداد — تکرار رویدادِ دوم نساخت', `${n.n}`);

  //  PRES-01: «آنلاین تا…» به ساعتِ دکان
  const p = Object.fromEntries(new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Kabul', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(new Date(Date.now() + 2 * 3600_000)).map((x) => [x.type, x.value]));
  const hhmm = `${String(+p.hour % 24).padStart(2, '0')}:${p.minute}`;
  await pg.fill('#sf-until', hhmm);
  await pg.click('[data-sf-mode="online_until"]');
  await pg.waitForFunction(() => /آنلاین اعلام شده/.test(document.getElementById('sf-badge').textContent), null, { timeout: 10000 });
  const fa = hhmm.replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[+d]);
  ok((await pg.textContent('#sf-badge')).includes(fa), '«آنلاین تا…» با همان ساعتِ دکان (PRES-01)', await pg.textContent('#sf-badge'));
  const pub = (await call('GET', '/api/store/demo-owner')).body.store.status;
  ok(pub && pub.badge === 'declared_until', 'مشتری هم همان را می‌بیند');
});

await step('کالاها', async () => {
  await pg.click('[data-sf-tab="products"]');
  await pg.waitForSelector('.sf-table tbody tr', { timeout: 10000 });
  ok((await pg.locator('.sf-table tbody tr').count()) === 3, 'هر سه کالای دفتر با نشانِ ویترین (پیش‌فرض: جدول در پهن)');
  ok((await pg.textContent('.sf-table')).includes('بیرون از ویترین'), 'پیش‌فرض «منتشر نشده»');
  await pg.check('tr[data-id="rice"] [data-sf="publish"]');
  await pg.waitForFunction(() => /در ویترین/.test(document.querySelector('tr[data-id="rice"]').textContent), null, { timeout: 10000 });
  await pg.check('tr[data-id="oil"] [data-sf="select"]');
  await pg.check('tr[data-id="soap"] [data-sf="select"]');
  ok((await pg.textContent('#sf-bulk')).includes('۲ انتخاب‌شده'), 'نوارِ عملیاتِ گروهی پس از انتخاب');
  await pg.click('[data-sf="bulk-on"]');
  await pg.waitForSelector('#sf-bulk-report', { timeout: 10000 });
  ok((await pg.textContent('#sf-bulk-report')).includes('۲ کالا انجام شد'), 'گزارشِ هر ردیف پس از انتشارِ گروهی');
  const cat = (await call('GET', '/api/store/demo-owner/products')).body.items.map((x) => x.id).sort();
  ok(cat.join(',') === 'oil,rice,soap', 'هر سه در کاتالوگِ عمومی', cat.join(','));
  await pg.click('tr[data-id="oil"] [data-sf="edit"]');
  await pg.waitForFunction(() => /۲۹۰/.test(document.querySelector('tr[data-id="oil"]').textContent), null, { timeout: 10000 });
  const m = await one('SELECT online_price_minor, max_qty_milli FROM storefront_products WHERE shop_id=$1 AND product_id=$2', [shopId, 'oil']);
  ok(Number(m.online_price_minor) === 29000 && Number(m.max_qty_milli) === 5000, 'قیمتِ آنلاین و سقف در سرور', JSON.stringify(m));
  await pg.click('[data-sf-view="card"]');
  await pg.waitForSelector('.sf-cards .sf-card');
  ok((await pg.locator('.sf-card').count()) === 3, 'نمای کارتی همان داده');
  await pg.reload();
  await pg.waitForFunction(() => window.TohidStorefrontOwner);
  await pg.evaluate(() => goTo('storefront'));
  await pg.click('[data-sf-tab="products"]');
  await pg.waitForSelector('.sf-cards .sf-card', { timeout: 10000 });
  ok(true, 'انتخابِ نما برای همین کاربر و همین صفحه ماند');
  await pg.click('[data-sf-filter="off"]');
  ok((await pg.locator('.sf-card').count()) === 0, 'پالایهٔ «بیرون از ویترین»');
  await pg.click('[data-sf-filter="all"]');
});

await step('چیدمان و دسترس‌پذیری', async () => {
  ok(await overflow(pg) <= 0, 'بی بیرون‌زدگی در ۱۲۸۰');
  const phone = await open(360);
  await phone.pg.evaluate(() => goTo('storefront'));
  await phone.pg.waitForSelector('#sf-root .sf-tabs', { timeout: 10000 });
  for (const t of ['status', 'settings', 'products']) {
    await phone.pg.click(`[data-sf-tab="${t}"]`);
    await phone.pg.waitForTimeout(500);
    ok(await overflow(phone.pg) <= 0, `گوشی ۳۶۰: بی بیرون‌زدگی (${t})`, `${await overflow(phone.pg)}px`);
  }
  ok((await phone.pg.locator('[data-sf-view]').count()) === 0, 'گوشی همیشه کارتی — کلیدِ نما نیست');
  if (axeSrc) {
    await phone.pg.addScriptTag({ content: axeSrc });
    const v = [];
    for (const t of ['status', 'settings', 'products']) {
      await phone.pg.click(`[data-sf-tab="${t}"]`);
      await phone.pg.waitForTimeout(400);
      const out = await phone.pg.evaluate(async () => (await window.axe.run(document.getElementById('page-storefront'),
        { runOnly: ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'] })).violations
        .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`));
      out.forEach((x) => v.push(`${t} ${x}`));
    }
    ok(v.length === 0, 'دسترس‌پذیری (axe-core)', v.join(' | '));
  } else if (SKIP) console.log('⏭  axe-core نیست و SKIP_BROWSER_TESTS=1');
  else ok(false, 'axe-core پیدا نشد');
  await phone.ctx.close();
});

ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | ').slice(0, 400));
await ctx.close();
await browser.close();
site.close();
api.close();
await closeDb();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین در وب: وضعیت، تنظیمات و کالاها');
process.exit(bad ? 1 : 0);
