/**
 * چتِ مشتری ↔ دکان با کرومیومِ واقعی (v3 §۳۳) — ویترینِ مشتری و نسخهٔ وبِ دکان.
 *
 *     npm run test:web          (همین پرونده هم در آن هست)
 *     node test/web-chat.mjs
 *
 * سرورِ واقعی، دکانِ واقعی (ثبت‌نام، همگام‌سازیِ کالا، ویترین، سفارش)؛ هیچ
 * پاسخِ ساختگی‌ای در کار نیست. آن‌چه سنجیده می‌شود:
 *   • ⛔ پرچمِ خاموش: نه پیوندِ «پیام به دکان» در ویترین، نه ردیفِ چت در برنامهٔ وب.
 *   • CHAT-02/11: بی اینترنت در صف ⇒ پس از وصل دقیقاً یک پیام؛ پاسخِ گم‌شده
 *     (درخواست رسید، جواب نرسید) ⇒ تلاشِ دوباره با همان clientId ⇒ باز هم یکی.
 *   • CHAT-04: «به سرور رسید» ⇒ «رسید» ⇒ «خوانده شد» فقط با رسیدِ واقعیِ طرف.
 *   • CHAT-05/06: عکسِ ۲۴۰۰×۱۲۰۰ با EXIF (جهتِ ۶ + GPS) ⇒ روی دستگاه ۸۰۰×۱۶۰۰
 *     (چرخیده)، بی EXIF و بی مختصات، با پیش‌نمایشِ ≤ ۳۲۰px.
 *   • پیامِ صوتی با میکروفونِ ساختگیِ کرومیوم زیرِ Permissions-Policy ِ همان صفحه.
 *   • شمارهٔ مشتری: صاحب (orders.contact) کامل، شاگرد پوشیده و بی دکمهٔ مسدود.
 *   • بلاک و گزارش از دو طرف؛ CSP ِ ویترین بی هیچ نقض؛ JS ِ فشردهٔ صفحه < ۷۵KB.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';

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
//  نسخهٔ وب از مبدأِ دیگری به سرور می‌زند (همان web-backup.mjs)
process.env.CORS_ORIGIN = '*';

const { createApp } = require('../src/app');
const { query, one, newId, now, closeDb } = require('../src/db');
const migrate = require('../src/migrate');
const pw = require('../src/lib/password');

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };
const step = async (name, fn) => { try { await fn(); } catch (e) { ok(false, name, process.env.CHAT_DEBUG ? e.message.slice(0, 1500) : e.message.split('\n')[0]); } };
const until = async (fn, ms = 15000, every = 150) => {
  const end = Date.now() + ms;
  for (;;) { const v = await fn(); if (v) return v; if (Date.now() > end) return v; await new Promise((r) => setTimeout(r, every)); }
};

await query('DROP SCHEMA public CASCADE');
await query('CREATE SCHEMA public');
await migrate.run();
await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
             VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
const app = await createApp({ runMigrations: false });
const server = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
const base = `http://127.0.0.1:${server.address().port}`;

//  ── نسخهٔ وب از ریشهٔ مخزن، با نشانیِ همین سرور (همان روشِ web-backup.mjs) ──
const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
const site = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html';
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404).end('no'); return; }
  if (rel === 'index.html') {
    const src = fs.readFileSync(file, 'utf8');
    const real = (src.replace(/<!--[\s\S]*?-->/g, '').match(/<meta name="tohid-api-base" content="([^"]+)">/) || [])[1];
    const html = src
      .replace(/<meta name="tohid-api-base" content="[^"]*">/g, `<meta name="tohid-api-base" content="${base}">`)
      .replace(/(<meta http-equiv="Content-Security-Policy" content="[^"]*?)(connect-src [^;"]*)/, (_, head, d) => head + d
        .split(real.replace(/^https:/, 'wss:')).join(base.replace(/^http/, 'ws')).split(real).join(base));
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
    return;
  }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => site.listen(0, '127.0.0.1', r));
const siteBase = `http://127.0.0.1:${site.address().port}`;

const call = async (method, p, b, t, headers = {}) => {
  const r = await fetch(base + p, { method, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: `Bearer ${t}` } : {}), ...headers },
    body: b === undefined || b === null ? undefined : JSON.stringify(b) });
  const ct = r.headers.get('content-type') || '';
  return { status: r.status, body: /json/.test(ct) ? await r.json().catch(() => null) : Buffer.from(await r.arrayBuffer()) };
};
async function register(email, name) {
  const password = 'Passw0rd!test';
  const s = await call('POST', '/api/auth/register/start', { name, email, password });
  const v = await call('POST', '/api/auth/register/verify', { email, code: s.body.devCode });
  const d = await call('POST', '/api/auth/register/complete', { ticket: v.body.ticket, name, password,
    device: { deviceId: `dev-${email.replace(/[^A-Za-z0-9]/g, '-')}`, name: 'تست', platform: 'test' }, app: 'shop', terms: { accepted: true } });
  return d.body;
}

//  ── دکان، ویترین و سفارشِ آزمایشی (دادهٔ ساختگی، نه دکانِ واقعی) ──
const owner = await register('owner@chat.local', 'صاحب');
const T = owner.accessToken;
const shopId = (await call('POST', '/api/shop', { name: 'دکانِ چت' }, T)).body.shop.id;
const A = (await call('POST', '/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
const flag = (key, value) => call('PUT', '/api/admin/pump/live-config', { scope: shopId, key, value }, A);
for (const key of ['shop.orders', 'shop.storefront']) await flag(key, true);
const slug = 'chat-store';
await call('PUT', '/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ چت', contactPhone: '0700000001', deliveryFeeMinor: 0, minOrderMinor: 0 }, T);
const op = (row, fields) => ({ op_id: `op-${row}-${Date.now()}`, ts: Date.now(), table: 'products', row_id: row, type: 'insert', fields });
await call('POST', '/api/sync/v1/push', { device_id: 'dev-owner-chat-local', schema_version: 2, ops: [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 100, category: 'خوراکی' })] },
  T, { 'X-App': 'shop', 'X-Device': 'dev-owner-chat-local' });
await call('PUT', '/api/shop/storefront/products/rice', { published: true }, T);
const PHONE = '0791112233';
const vs = await call('POST', `/api/store/${slug}/verify/start`, { phone: PHONE });
const vc = await call('POST', `/api/store/${slug}/verify/check`, { phone: PHONE, code: vs.body.devCode });
const made = await call('POST', `/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتریِ چت', phone: PHONE },
  lines: [{ productId: 'rice', qtyMilli: 1000 }] }, null, { 'Idempotency-Key': 'ck-chat-0001', 'X-Store-Ticket': vc.body.ticket });
ok(made.status === 201 || made.status === 200, 'سفارشِ آزمایشی ثبت شد', String(made.status));
const order = made.body.order;

const staffUser = await register('staff@chat.local', 'شاگرد');
await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
  [newId('mem'), shopId, staffUser.user.id, now()]);

const messagesOf = async () => (await query('SELECT * FROM shop_chat_messages WHERE shop_id=$1 ORDER BY seq', [shopId])).rows;
const convRow = () => one('SELECT * FROM shop_chat_conversations WHERE shop_id=$1 ORDER BY created_at LIMIT 1', [shopId]);

const browser = await chromium.launch({ args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] });
const errors = [];
const violations = [];
const watch = (page, tag) => {
  page.on('pageerror', (e) => errors.push(`${tag}: ${e}`));
  page.on('console', (m) => { if (/Content Security Policy|Permissions policy/i.test(m.text())) violations.push(`${tag}: ${m.text()}`); });
  page.on('dialog', (d) => d.accept());
};

//  ── عکسِ آزمایشی: JPEG ِ واقعیِ ۲۴۰۰×۱۲۰۰ از خودِ کرومیوم + Exif (جهتِ ۶ و GPS) ──
const SECRET = 'LATLONG-SECRET-1';
function withExif(jpeg) {
  const secret = Buffer.from(SECRET, 'latin1');
  const tiff = Buffer.alloc(56 + secret.length);
  tiff.write('MM', 0, 'latin1'); tiff.writeUInt16BE(42, 2); tiff.writeUInt32BE(8, 4);
  tiff.writeUInt16BE(2, 8);
  tiff.writeUInt16BE(0x0112, 10); tiff.writeUInt16BE(3, 12); tiff.writeUInt32BE(1, 14); tiff.writeUInt16BE(6, 18);
  tiff.writeUInt16BE(0x8825, 22); tiff.writeUInt16BE(4, 24); tiff.writeUInt32BE(1, 26); tiff.writeUInt32BE(38, 30);
  tiff.writeUInt32BE(0, 34);
  tiff.writeUInt16BE(1, 38);
  tiff.writeUInt16BE(0x001b, 40); tiff.writeUInt16BE(7, 42); tiff.writeUInt32BE(secret.length, 44); tiff.writeUInt32BE(56, 48);
  tiff.writeUInt32BE(0, 52);
  secret.copy(tiff, 56);
  const payload = Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), tiff]);
  const head = Buffer.alloc(4); head.writeUInt16BE(0xffe1, 0); head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([jpeg.subarray(0, 2), head, payload, jpeg.subarray(2)]);
}
function jpegSize(buf) {
  for (let i = 2; i < buf.length - 9;) {
    if (buf[i] !== 0xff) return null;
    const m = buf[i + 1];
    if (m >= 0xc0 && m <= 0xc3) return { h: buf.readUInt16BE(i + 5), w: buf.readUInt16BE(i + 7) };
    i += 2 + buf.readUInt16BE(i + 2);
  }
  return null;
}
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'web-chat-'));
const photoPath = path.join(tmp, 'photo.jpg');
{
  const p = await browser.newPage();
  const b64 = await p.evaluate(async () => {
    const c = document.createElement('canvas'); c.width = 2400; c.height = 1200;
    const g = c.getContext('2d');
    g.fillStyle = '#d33'; g.fillRect(0, 0, 1200, 1200); g.fillStyle = '#33d'; g.fillRect(1200, 0, 1200, 1200);
    for (let i = 0; i < 4000; i++) { g.fillStyle = `hsl(${i % 360},70%,50%)`; g.fillRect((i * 37) % 2400, (i * 53) % 1200, 9, 9); }
    const blob = await new Promise((r) => c.toBlob(r, 'image/jpeg', 0.95));
    const bytes = new Uint8Array(await blob.arrayBuffer());
    let s = ''; for (const x of bytes) s += String.fromCharCode(x);
    return btoa(s);
  });
  await p.close();
  fs.writeFileSync(photoPath, withExif(Buffer.from(b64, 'base64')));
}

// ════════════════════════════════════════════════════════════════════
//  ۰) پرچمِ خاموش
// ════════════════════════════════════════════════════════════════════
{
  const ctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
  const page = await ctx.newPage();
  watch(page, 'ویترینِ خاموش');
  await page.goto(`${base}/store/${slug}`);
  await page.waitForSelector('#products li', { timeout: 15000 });
  await page.waitForTimeout(500);
  ok(await page.isHidden('#store-chat'), 'پرچمِ خاموش: ویترین پیوندِ «پیام به دکان» ندارد');
  await page.goto(`${base}/store/${slug}#/o/${order.token}`);
  await page.waitForSelector('#view-order:not([hidden])');
  await page.waitForTimeout(300);
  ok(await page.isHidden('#o-chat'), 'پرچمِ خاموش: رسیدِ سفارش دکمهٔ گفت‌وگو ندارد');
  await ctx.close();
  ok((await one('SELECT COUNT(*)::int n FROM shop_chat_conversations')).n === 0, 'پرچمِ خاموش: هیچ ردیفی ساخته نشد');
}
async function webApp(user, width = 1280) {
  const ctx = await browser.newContext({ viewport: { width, height: 860 }, locale: 'fa-AF' });
  await ctx.addInitScript(([bundle]) => {
    if (sessionStorage.getItem('seeded')) return;
    sessionStorage.setItem('seeded', '1');
    localStorage.setItem('tohid-license-v1', JSON.stringify(bundle));
    localStorage.setItem('tohid-unlocked-v1', '1');
    //  ورودِ همین دستگاه (همان web-pos-draft.mjs) — بی آن پوستهٔ برنامه پشتِ صفحهٔ ورود می‌ماند
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: bundle.userLabel, loginAt: 1 }));
  }, [{ accessToken: user.accessToken, refreshToken: user.refreshToken, accessExpiresAt: Date.now() + 3600_000, userId: user.user.id, userLabel: user.user.name || 'کاربر' }]);
  const page = await ctx.newPage();
  watch(page, 'وب');
  await page.goto(`${siteBase}/index.html`);
  await page.waitForFunction(() => typeof window.goTo === 'function' && window.TohidShopChat, null, { timeout: 15000 });
  return { ctx, page };
}
{
  const { ctx, page } = await webApp(owner);
  await page.waitForTimeout(1500);
  ok((await page.locator('[data-chat-nav]').count()) === 0, 'پرچمِ خاموش: برنامهٔ وب ردیفِ چت ندارد');
  await page.evaluate(() => { location.hash = '#chat'; });
  await page.waitForTimeout(400);
  ok(await page.evaluate(() => !document.querySelector('#page-chat.active')), 'پرچمِ خاموش: #chat باز نمی‌شود');
  await ctx.close();
}
await flag('shop.chat', true);

// ════════════════════════════════════════════════════════════════════
//  ۱) ویترین: مشتری دربارهٔ سفارشش
// ════════════════════════════════════════════════════════════════════
const cctx = await browser.newContext({ viewport: { width: 360, height: 760 }, locale: 'fa-AF' });
await cctx.grantPermissions(['microphone'], { origin: base });
const cpage = await cctx.newPage();
watch(cpage, 'ویترین');
await cpage.goto(`${base}/store/${slug}#/o/${order.token}`);
await cpage.waitForSelector('#o-chat:not([hidden])', { timeout: 15000 });
ok(await cpage.isVisible('#store-chat'), 'پرچمِ روشن: «پیام به دکان» در سربرگ');
await cpage.click('#o-chat');
await cpage.waitForSelector('#ch-verify:not([hidden])', { timeout: 10000 });
ok(true, 'بی شمارهٔ تأییدشده، اول تأییدِ شماره');
await cpage.fill('#ch-phone', PHONE);
await cpage.click('#ch-send-code');
await cpage.waitForSelector('#ch-code-row:not([hidden])', { timeout: 10000 });
const code = ((await cpage.textContent('#ch-verify-msg')).match(/(\d{4,8})/) || [])[1];
await cpage.fill('#ch-code', code || '');
await cpage.click('#ch-check');
await cpage.waitForSelector('#ch-thread:not([hidden])', { timeout: 10000 });
await cpage.waitForFunction(() => /#\/chat\/c\//.test(location.hash), null, { timeout: 10000 });
const conv0 = await convRow();
ok(conv0 && conv0.order_id === (await one('SELECT id FROM online_orders WHERE shop_id=$1', [shopId])).id, 'گفت‌وگو به همان سفارش بسته است');
ok((await cpage.textContent('#ch-sub')).includes('سفارش'), 'سربرگِ گفت‌وگو شمارهٔ سفارش را می‌گوید');

const T1 = 'سلام، سفارشم کی آماده است؟';
await step('ویترین CHAT-02: بی اینترنت در صف ⇒ پس از وصل دقیقاً یک پیام', async () => {
  await cctx.setOffline(true);
  await cpage.fill('#ch-text', T1);
  await cpage.press('#ch-text', 'Enter');
  await cpage.waitForSelector('#ch-msgs [data-status="waiting"]', { timeout: 10000 });
  ok((await messagesOf()).length === 0, 'آفلاین: هنوز هیچ پیامی روی سرور نیست و پیام «منتظرِ اینترنت» است');
  const saved = await cpage.evaluate((s) => JSON.parse(localStorage.getItem('store-chat-queue:' + s) || '[]'), slug);
  ok(saved.length === 1 && saved[0].body === T1 && /^[A-Za-z0-9_.:-]{8,128}$/.test(saved[0].clientId), 'صف در حافظهٔ مرورگر با clientId نوشته شد');
  await cctx.setOffline(false);
  await cpage.waitForSelector('#ch-msgs li[data-id] [data-status="sent"]', { timeout: 15000 });
  await cpage.waitForTimeout(2500);
  const rows = (await messagesOf()).filter((m) => m.body === T1);
  ok(rows.length === 1 && rows[0].client_id === saved[0].clientId, 'پس از وصل: دقیقاً یک پیام با همان clientId', String(rows.length));
  ok((await cpage.locator('#ch-msgs li', { hasText: T1 }).count()) === 1, 'روی صفحه هم یک بار (نه صف + سرور)');
});

const T2 = 'پیامِ دوم با پاسخِ گم‌شده';
await step('ویترین CHAT-11: پاسخِ گم‌شده ⇒ تلاشِ دوباره با همان شناسه ⇒ باز هم یک پیام', async () => {
  let dropped = 0;
  await cpage.route('**/chat/conversations/*/messages', async (route) => {
    if (route.request().method() === 'POST' && dropped === 0) {
      dropped++;
      await route.fetch();          // به سرور رسید و ثبت شد…
      return route.abort('connectionreset'); // …ولی جواب به مرورگر نرسید
    }
    return route.continue();
  });
  await cpage.fill('#ch-text', T2);
  await cpage.press('#ch-text', 'Enter');
  await until(async () => (await cpage.locator(`#ch-msgs li[data-id]`, { hasText: T2 }).count()) === 1, 15000);
  await cpage.unroute('**/chat/conversations/*/messages');
  const rows = (await messagesOf()).filter((m) => m.body === T2);
  ok(dropped === 1 && rows.length === 1, 'پاسخِ گم‌شده دوباره فرستاده شد و سرور پیامِ دوم نساخت', `${dropped}/${rows.length}`);
});

let convId = '';
await step('CHAT-04: رسید و خوانده فقط با اعلامِ واقعیِ دکان', async () => {
  const list = await call('GET', '/api/shop/chat/conversations?status=all', null, T);
  convId = list.body.conversations[0].id;
  ok((await cpage.locator('#ch-msgs [data-status="sent"]').count()) === 2, 'پیش از رسیدِ دکان: هر دو «به سرور رسید»');
  await call('POST', `/api/shop/chat/conversations/${convId}/receipts`, { deliveredSeq: 2 }, T);
  await cpage.waitForFunction(() => document.querySelectorAll('#ch-msgs [data-status="delivered"]').length === 2, null, { timeout: 30000 });
  ok(true, 'دکان گرفت ⇒ «به دستگاهِ طرف رسید»');
  await call('POST', `/api/shop/chat/conversations/${convId}/receipts`, { readSeq: 1 }, T);
  await cpage.waitForFunction(() => document.querySelectorAll('#ch-msgs [data-status="read"]').length === 1, null, { timeout: 30000 });
  ok((await cpage.locator('#ch-msgs [data-status="delivered"]').count()) === 1, 'فقط پیامِ خوانده‌شده «خوانده شد» است');
});

const R1 = 'سلام، تا یک ساعت دیگر آماده است';
await step('پاسخِ دکان یک بار می‌رسد و مشتری رسیدِ «خوانده» می‌فرستد', async () => {
  await call('POST', `/api/shop/chat/conversations/${convId}/messages`, { clientId: 'shop-reply-0001', body: R1 }, T);
  await cpage.waitForSelector(`#ch-msgs li.theirs:has-text("${R1}")`, { timeout: 30000 });
  ok((await cpage.locator('#ch-msgs li', { hasText: R1 }).count()) === 1, 'پاسخ یک بار دیده شد');
  const r = await until(async () => { const c = await convRow(); return Number(c.customer_read_seq) >= 3 ? c : null; }, 10000);
  ok(!!r, 'صفحهٔ باز ⇒ رسیدِ «خوانده» تا seq ِ ۳', r ? String(r.customer_read_seq) : '');
});

await step('ویترین CHAT-05/06: عکس روی دستگاه کوچک، چرخیده و بی EXIF', async () => {
  await cpage.setInputFiles('#ch-file', photoPath);
  const msg = await until(async () => (await messagesOf()).find((m) => m.kind === 'image'), 20000);
  ok(!!msg, 'پیامِ عکس ثبت شد');
  if (!msg) return;
  const main = await call('GET', `/api/shop/chat/conversations/${convId}/media/${msg.media_id}`, null, T);
  const thumb = await call('GET', `/api/shop/chat/conversations/${convId}/media/${msg.thumb_id}`, null, T);
  const s = main.body.toString('latin1');
  ok(!s.includes('Exif') && !s.includes(SECRET) && !thumb.body.toString('latin1').includes(SECRET), 'نه Exif، نه مختصات (اصل و پیش‌نمایش)');
  const dim = jpegSize(main.body);
  ok(dim && dim.w === 800 && dim.h === 1600, 'جهتِ ۶ پیش از رسم اعمال شد و ابعاد ≤ ۱۶۰۰ (۲۴۰۰×۱۲۰۰ ⇒ ۸۰۰×۱۶۰۰)', JSON.stringify(dim));
  const td = jpegSize(thumb.body);
  ok(td && Math.max(td.w, td.h) <= 320 && thumb.body.length <= 64 * 1024, 'پیش‌نمایش ≤ ۳۲۰px و ≤ ۶۴KB', `${JSON.stringify(td)} ${thumb.body.length}`);
  ok(main.body.length < fs.statSync(photoPath).size, 'نسخهٔ فرستاده کوچک‌تر از اصل', `${main.body.length} < ${fs.statSync(photoPath).size}`);
  const meta = typeof msg.meta === 'string' ? JSON.parse(msg.meta) : msg.meta;
  ok(meta.width === 800 && meta.height === 1600, 'ابعادِ پیام همان فایل');
  await cpage.waitForFunction(() => [...document.querySelectorAll('#ch-msgs .chat-thumb img')].some((i) => i.complete && i.naturalWidth > 0), null, { timeout: 15000 });
  ok(true, 'پیش‌نمایش در خودِ ویترین باز شد (blob زیرِ CSP)');
});

await step('ویترین: پیامِ صوتی با میکروفونِ همین صفحه (Permissions-Policy: microphone=(self))', async () => {
  const vt = await cpage.evaluate(() => window.TohidChatCore.voiceType());
  if (!vt) { ok(await cpage.isHidden('#ch-mic'), 'این مرورگر Opus/AAC ضبط نمی‌کند ⇒ دکمه پنهان'); return; }
  ok(await cpage.isVisible('#ch-mic'), `دکمهٔ صدا پیدا (${vt})`);
  await cpage.click('#ch-mic');
  await cpage.waitForSelector('#ch-voice .chat-rec', { timeout: 10000 });
  await cpage.waitForTimeout(1500);
  await cpage.click('#ch-mic');
  await cpage.waitForSelector('#ch-voice audio', { timeout: 10000 });
  ok(/کیلوبایت/.test(await cpage.textContent('#ch-voice')), 'پیش از فرستادن مدت و حجم دیده می‌شود');
  await cpage.click('#ch-voice >> text=فرستادنِ صدا');
  const msg = await until(async () => (await messagesOf()).find((m) => m.kind === 'audio'), 20000);
  const media = msg && await one('SELECT mime, size FROM shop_chat_media WHERE id=$1', [msg.media_id]);
  ok(!!media && /^audio\/(webm|ogg|mp4|aac)$/.test(media.mime), 'صدا با Opus/AAC رسید', media ? `${media.mime} ${media.size}` : '');
});

await step('ویترین: بی بیرون‌زدگی در ۳۲۰ و ۷۶۸', async () => {
  for (const w of [320, 768]) {
    await cpage.setViewportSize({ width: w, height: 760 });
    const over = await cpage.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    ok(over <= 0, `بی بیرون‌زدگی در ${w}`, `${over}px`);
  }
  await cpage.setViewportSize({ width: 360, height: 760 });
});

// ════════════════════════════════════════════════════════════════════
//  ۲) نسخهٔ وبِ دکان
// ════════════════════════════════════════════════════════════════════
const { ctx: wctx, page: wpage } = await webApp(owner);
await step('وب: پرچمِ روشن ⇒ ردیفِ «گفت‌وگو با مشتری» و صفحه', async () => {
  await wpage.waitForSelector('[data-chat-nav]', { state: 'attached', timeout: 15000 });
  await wpage.evaluate(() => { location.hash = '#chat'; });
  await wpage.waitForSelector('#page-chat.active', { timeout: 10000 });
  await wpage.waitForSelector('#page-chat .sc-table tbody tr', { timeout: 10000 });
  ok(true, 'صفحهٔ مدیریتِ کامپیوتر پیش‌فرض جدولی است');
  ok((await wpage.textContent('#page-chat .sc-table')).includes('+93791112233'), 'صاحب (orders.contact) شمارهٔ کامل را می‌بیند');
  await wpage.click('#page-chat [data-view="card"]');
  await wpage.waitForSelector('#page-chat .sc-card', { timeout: 5000 });
  await wpage.reload();
  await wpage.waitForFunction(() => window.TohidShopChat && window.TohidShopChat.enabled, null, { timeout: 15000 });
  await wpage.evaluate(() => { location.hash = '#chat'; window.dispatchEvent(new HashChangeEvent('hashchange')); });
  await wpage.waitForSelector('#page-chat .sc-card', { timeout: 10000 });
  ok(true, 'نمای کارتی برای همین کاربر و همین صفحه ماند');
});

await step('وب: باز کردنِ گفت‌وگو ⇒ پیام‌ها و رسیدِ «خوانده» ِ واقعی', async () => {
  await wpage.click('#page-chat .sc-card');
  await wpage.waitForSelector(`#page-chat .sc-msgs li.theirs:has-text("${T1}")`, { timeout: 15000 });
  const r = await until(async () => { const c = await convRow(); const top = Number(c.seq); return Number(c.shop_read_seq) >= top ? c : null; }, 10000);
  ok(!!r, 'دکان تا آخرین پیام «خواند» (فقط چون صفحه باز است)');
  await wpage.waitForFunction(() => [...document.querySelectorAll('#page-chat .sc-thumb img')].some((i) => i.complete && i.naturalWidth > 0), null, { timeout: 15000 });
  ok(true, 'عکسِ مشتری در برنامهٔ وب دیده شد');
});

const W1 = 'از وب: سفارش آماده شد';
await step('وب CHAT-02: بی اینترنت در صف ⇒ پس از وصل دقیقاً یک پیام', async () => {
  await wctx.setOffline(true);
  await wpage.fill('#sc-text', W1);
  await wpage.press('#sc-text', 'Enter');
  await wpage.waitForSelector('#page-chat .sc-msgs [data-status="waiting"]', { timeout: 10000 });
  ok((await messagesOf()).filter((m) => m.body === W1).length === 0, 'آفلاین: روی سرور نیست');
  await wctx.setOffline(false);
  await wpage.waitForSelector(`#page-chat .sc-msgs li[data-id]:has-text("${W1}")`, { timeout: 15000 });
  await wpage.waitForTimeout(2000);
  ok((await messagesOf()).filter((m) => m.body === W1).length === 1, 'پس از وصل: دقیقاً یکی');
  ok((await wpage.locator('#page-chat .sc-msgs li', { hasText: W1 }).count()) === 1, 'روی صفحه هم یکی');
});

await step('وب CHAT-04: «خوانده شد» فقط وقتی ویترینِ باز آن را دید', async () => {
  await cpage.waitForSelector(`#ch-msgs li.theirs:has-text("${W1}")`, { timeout: 30000 });
  await wpage.waitForFunction((t) => [...document.querySelectorAll('#page-chat .sc-msgs li.mine')].some((li) => li.textContent.includes(t) && li.querySelector('[data-status="read"]')), W1, { timeout: 30000 });
  ok(true, 'وضعیتِ پیامِ دکان «خوانده شد» با رسیدِ مشتری');
});

await step('وب: عکس از برنامهٔ وب هم بی EXIF', async () => {
  const before = (await messagesOf()).filter((m) => m.kind === 'image').length;
  await wpage.setInputFiles('#page-chat .sc-file', photoPath);
  const msg = await until(async () => { const l = (await messagesOf()).filter((m) => m.kind === 'image'); return l.length > before ? l[l.length - 1] : null; }, 20000);
  const main = msg && await call('GET', `/api/shop/chat/conversations/${convId}/media/${msg.media_id}`, null, T);
  ok(!!main && !main.body.toString('latin1').includes('Exif') && !main.body.toString('latin1').includes(SECRET) && msg.sender === 'shop', 'عکسِ دکان هم بی Exif/مختصات');
});

await step('وب: مسدود کردن ⇒ ویترین دیگر نمی‌فرستد؛ برداشتن ⇒ باز', async () => {
  await wpage.click('#page-chat .sc-block');
  await wpage.waitForFunction(() => /برداشتنِ مسدودی/.test(document.querySelector('#page-chat .sc-block').textContent), null, { timeout: 10000 });
  await cpage.waitForFunction(() => document.getElementById('ch-text').disabled, null, { timeout: 30000 });
  ok(/نمی‌پذیرد/.test(await cpage.textContent('#ch-hint')), 'ویترین: «این دکان فعلاً پیامِ تازه نمی‌پذیرد»');
  await wpage.click('#page-chat .sc-block');
  await cpage.waitForFunction(() => !document.getElementById('ch-text').disabled, null, { timeout: 30000 });
  ok(true, 'برداشتنِ مسدودی به ویترین رسید');
});

await step('گزارش از دو طرف', async () => {
  await cpage.click(`#ch-msgs li.theirs:has-text("${R1}") [data-report]`);
  await cpage.selectOption('#ch-reason', 'abuse');
  await cpage.click('#ch-report-form button[type="submit"]');
  await cpage.waitForFunction(() => /گزارش رسید/.test(document.getElementById('ch-banner').textContent), null, { timeout: 10000 });
  await wpage.click('#page-chat .sc-report');
  await wpage.selectOption('#sc-reason', 'spam');
  await wpage.click('#page-chat .sc-report-form button[type="submit"]');
  await wpage.waitForFunction(() => /گزارش رسید/.test(document.querySelector('#page-chat .sc-banner').textContent), null, { timeout: 10000 });
  const reps = (await query('SELECT reporter, reason, message_id FROM shop_chat_reports WHERE shop_id=$1 ORDER BY reporter', [shopId])).rows;
  ok(reps.length === 2 && reps[0].reporter === 'customer' && reps[0].message_id && reps[1].reporter === 'shop', 'دو گزارش، یکی با رونوشتِ همان پیام', JSON.stringify(reps));
});

// ════════════════════════════════════════════════════════════════════
//  ۳) شاگرد: شمارهٔ پوشیده، بی مسدود کردن
// ════════════════════════════════════════════════════════════════════
await step('شاگرد: شمارهٔ پوشیده و بی دکمهٔ مسدود (orders.contact / chat.moderate)', async () => {
  const { ctx, page } = await webApp(staffUser, 1280);
  await page.waitForSelector('[data-chat-nav]', { state: 'attached', timeout: 15000 });
  await page.evaluate(() => { location.hash = '#chat'; });
  await page.waitForSelector('#page-chat .sc-table tbody tr', { timeout: 10000 });
  const txt = await page.textContent('#page-chat .sc-table');
  ok(!txt.includes('791112233') && /•/.test(txt), 'شاگرد شمارهٔ کامل را نمی‌بیند', txt.slice(0, 80));
  await page.click('#page-chat .sc-row-btn');
  await page.waitForSelector('#page-chat .sc-thread:not([hidden])');
  ok(await page.isHidden('#page-chat .sc-block'), 'شاگرد دکمهٔ مسدود ندارد');
  await ctx.close();
});

//  ── دسترس‌پذیری (axe-core): همان قاعده‌های web-store.mjs، روی خودِ گفت‌وگو ──
const AXE_RULES = ['color-contrast', 'button-name', 'label', 'image-alt', 'link-name'];
const axeRun = (page, sel) => page.evaluate(async ([s, rules]) => (await window.axe.run(document.querySelector(s), { runOnly: rules })).violations
  .map((x) => `${x.id}: ${x.nodes.slice(0, 2).map((n) => n.target.join(' ')).join(' ; ')}`), [sel, AXE_RULES]);
if (axeSrc) {
  await step('دسترس‌پذیری: گفت‌وگوی ویترین و صفحهٔ چتِ وب', async () => {
    const t = await cpage.evaluate(() => localStorage.getItem('store-ticket'));
    const actx = await browser.newContext({ viewport: { width: 360, height: 760 }, bypassCSP: true, locale: 'fa-AF' });
    await actx.addInitScript((v) => localStorage.setItem('store-ticket', v), t);
    const ap = await actx.newPage();
    await ap.goto(`${base}/store/${slug}#/chat/c/${convId}`);
    await ap.waitForSelector('#ch-msgs li.theirs', { timeout: 15000 });
    await ap.waitForTimeout(500);
    await ap.addScriptTag({ content: axeSrc });
    const v1 = await axeRun(ap, '#view-chat');
    await actx.close();
    await wpage.addScriptTag({ content: axeSrc });
    const v2 = await axeRun(wpage, '#page-chat');
    ok(v1.length === 0 && v2.length === 0, 'دسترس‌پذیری (axe-core): کنتراست، نامِ دکمه، برچسب، متنِ جایگزین', [...v1, ...v2].join(' | '));
  });
} else {
  ok(false, 'axe-core پیدا نشد (npm i -g axe-core@4.10.3، یا AXE_PATH)');
}

//  ── CSP، خطا و بودجهٔ JS ──
const storeJs = ['store.js', 'chat-core.js'].map((f) => fs.readFileSync(path.join(import.meta.dirname, '..', 'public', 'store', f)));
const gz = storeJs.reduce((n, b) => n + zlib.gzipSync(b).length, 0);
ok(gz < 75 * 1024, 'JS ِ فشردهٔ ویترین زیرِ ۷۵KB (۳.۱۰)', `${gz} بایت`);
ok(violations.length === 0, 'هیچ نقضِ CSP/Permissions-Policy', violations.join(' | ').slice(0, 400));
ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | ').slice(0, 400));

await wctx.close();
await cctx.close();
await browser.close();
site.close();
server.close();
await closeDb();
fs.rmSync(tmp, { recursive: true, force: true });
console.log(bad ? `❌ ${bad} ایراد` : '✅ چتِ مشتری ↔ دکان: ویترین و وب');
process.exit(bad ? 1 : 0);
