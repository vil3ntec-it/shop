/**
 * پایهٔ مشترکِ سناریوهای سرتاسری (E2E) — `server/test/e2e/*.e2e.mjs`.
 *
 * ⛔ دستورِ صاحب مخزن (۱۴۰۵/۰۷/۲۸): سیستمِ دکان را **برنامه‌ها** می‌گردانند (اندروید و
 * کامپیوتر)، نه وب‌سایت. پس «دستگاه»‌های دکان این‌جا در سطحِ پروتکل‌اند
 * (`lib/native.mjs`: همان opها، همان JSON، همان سرآیندهای برنامه) و `index.html` باز
 * نمی‌شود. کرومیوم فقط برای دو سطحی که خودشان وب‌اند: ویترینِ مشتری
 * (`/store/<slug>`) و پنلِ مدیر (`/admin/`).
 *
 * هر سناریو یک فرایندِ جداست و این‌ها را از همین‌جا می‌گیرد:
 *   • سرورِ واقعی (`createApp`) با همان سه کارِ `src/index.js` (کارگرِ ایمیل، جدولِ schema،
 *     سوکتِ زنده) روی دیتابیسِ خودش — پیش‌فرض PGlite در حافظه تا سناریوها موازی بدوند؛
 *   • صندوقِ ایمیلِ آزمون: `mailer.send` نامه را نگه می‌دارد و کدِ ورود از **متنِ همان نامه**
 *     خوانده می‌شود؛
 *   • پرچم‌ها فقط با `live_config`، فقط برای همان دکانِ همان سناریو و از درِ پنلِ مدیر
 *     (`PUT /api/admin/pump/live-config`) — هیچ پیکربندیِ تولیدی دست نمی‌خورد؛
 *   • گام‌ها با شناسهٔ سند، شکاف‌های شناخته (`known-gaps.mjs`) و نتیجه برای `run.mjs`.
 */
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { missingTool, PLAYWRIGHT_INSTALL } from '../../browser-guard.mjs';
import { classify } from './known-gaps.mjs';
import { NativeDevice } from './native.mjs';

const require = createRequire(import.meta.url);
export { assert };
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const fa = (n) => String(n).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[+d]);

/**
 * سرور را بالا می‌آورد.
 * @param {{name:string, env?:Record<string,string>}} opts
 */
export async function boot({ name, env = {} }) {
  //  ⚠️ پیش از نخستین `require`ِ کدِ سرور — config همان لحظه خوانده می‌شود
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), `e2e-${name}-`));
  Object.assign(process.env, {
    NODE_ENV: 'test',
    //  هر سناریو دیتابیسِ خودش: PGlite در حافظه (موازی و بی اثر روی هم).
    //  E2E_DATABASE_URL فقط برای اجرای تکی روی Postgres (run.mjs آن‌وقت ترتیبی می‌دود).
    DATABASE_URL: process.env.E2E_DATABASE_URL || 'pglite:memory',
    API_SECRET: 'e2e-secret-e2e-secret-e2e-secret-1',
    OTP_SECRET: 'e2e-otp-secret-e2e-otp-secret-12',
    BACKUP_ENABLED: 'false',
    BACKUP_PATH: path.join(tmp, 'backups'),
    RATE_GENERAL_MAX: '100000',
    RATE_SYNC_MAX: '100000',
    RATE_LOGIN_EDGE_MAX: '100000',
    //  همهٔ دستگاه‌های آزمون از 127.0.0.1 می‌آیند؛ سقفِ IP و ایمیلِ کدِ ورود برای یک آدم است، نه صد دستگاه
    LOGIN_IP_MAX: '100000',
    LOGIN_EMAIL_MAX: '1000',
    OTP_RESEND_SECONDS: '0',
    OTP_UNIFORM_MS: '0',
    CORS_ORIGIN: '*',
    ...env,
  });

  const { createApp } = require('../../../src/app');
  const db = require('../../../src/db');
  const migrate = require('../../../src/migrate');
  const pwd = require('../../../src/lib/password');
  const mailer = require('../../../src/lib/mailer');
  const live = require('../../../src/lib/sync-v1-live');
  const outbox = require('../../../src/lib/login-outbox');
  const syncV1 = require('../../../src/lib/sync-v1');

  if (!db.isPglite()) {
    await db.query('DROP SCHEMA public CASCADE');
    await db.query('CREATE SCHEMA public');
  }
  await migrate.run();
  await db.query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیرِ آزمون',$2,'superadmin','active',$3)`,
    [db.newId('adm'), await pwd.hashPassword('Admin!12345'), db.now()],
  );

  /* ---- صندوقِ ایمیل: سرویسِ ایمیلِ بیرونی در آزمون نیست؛ نامه همین‌جا می‌ماند ---- */
  const inbox = [];
  mailer.send = async (mail) => { inbox.push({ to: String(mail.to || '').toLowerCase(), subject: mail.subject, text: mail.text || '', at: Date.now() }); return { ok: true }; };

  const app = await createApp({ runMigrations: false });
  const api = await new Promise((r) => { const s = app.listen(0, '127.0.0.1', () => r(s)); });
  //  همان کارهای `src/index.js` پس از listen
  outbox.start();
  await syncV1.syncSchemaTable();
  live.attach(api);
  const apiBase = `http://127.0.0.1:${api.address().port}`;
  return new Harness({ name, db, apiBase, inbox, api, outbox, tmp });
}

class Harness {
  constructor(o) {
    Object.assign(this, o);
    this.results = [];
    this.pages = [];
    this.pageErrors = [];
    this.shotsDir = process.env.E2E_SHOTS_DIR || path.join(os.tmpdir(), 'e2e-shots');
    fs.mkdirSync(this.shotsDir, { recursive: true });
    this._admin = null;
    this._browser = null;
    this._seq = 0;
    this._failedIds = new Set();
  }

  /* ════════════════════════ HTTP (بیرون از برنامه: مدیر، مشتری) ════════════════════════ */

  async call(method, p, body, token, headers = {}) {
    const r = await fetch(this.apiBase + p, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      body: body === undefined || body === null ? undefined : JSON.stringify(body),
    });
    const text = await r.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 300) }; }
    return { status: r.status, body: json, headers: r.headers };
  }
  get(p, t, h) { return this.call('GET', p, undefined, t, h); }
  post(p, b, t, h) { return this.call('POST', p, b ?? {}, t, h); }
  put(p, b, t, h) { return this.call('PUT', p, b ?? {}, t, h); }
  key(prefix = 'k') { return `${prefix}-${++this._seq}-${Date.now()}`; }

  /** پاسخِ موفق یا خطای خوانا */
  ok(r, what, status = [200, 201]) {
    const want = Array.isArray(status) ? status : [status];
    assert.ok(want.includes(r.status), `${what}: ${r.status} ${JSON.stringify(r.body).slice(0, 300)}`);
    return r.body;
  }

  /* ════════════════════════ حساب‌ها ════════════════════════ */

  /** ثبت‌نامِ سه‌پله‌ای — همان راهِ برنامه (devCode فقط بیرون از تولید برمی‌گردد) */
  async register(email, name) {
    const password = 'Passw0rd!e2e';
    const s = this.ok(await this.post('/api/auth/register/start', { name, email, password }), `ثبت‌نام ۱ (${email})`);
    const v = this.ok(await this.post('/api/auth/register/verify', { email, code: s.devCode }), `ثبت‌نام ۲ (${email})`);
    const d = this.ok(await this.post('/api/auth/register/complete', {
      ticket: v.ticket, name, password,
      device: { deviceId: `reg-${email.replace(/[^a-z0-9]/gi, '')}`.slice(0, 60), name: 'ثبت‌نام', platform: 'test' },
      app: 'shop', terms: { accepted: true },
    }), `ثبت‌نام ۳ (${email})`);
    return { email, name, password, token: d.accessToken, refreshToken: d.refreshToken, user: d.user };
  }

  /** صاحبِ دکان: ثبت‌نام + ساختنِ دکان */
  async owner(email, name, shopName) {
    const a = await this.register(email, name);
    const r = this.ok(await this.post('/api/shop', { name: shopName }, a.token), `ساختنِ دکانِ ${shopName}`);
    a.shopId = r.shop.id;
    return a;
  }

  /** شاگرد: ثبت‌نام + پیوستن با کدِ شاگردی که صاحب ساخته (همان دو درِ برنامه) */
  async staff(owner, email, name, role = 'staff') {
    const inv = this.ok(await this.post('/api/shop/staff-code', { role, maxUses: 1 }, owner.token), 'کدِ شاگرد');
    const a = await this.register(email, name);
    this.ok(await this.post('/api/shop/staff/join', { code: inv.code }, a.token), `پیوستنِ ${name}`);
    a.shopId = owner.shopId;
    a.role = role;
    return a;
  }

  /** آخرین کدِ ورودِ رسیده به این ایمیل — از متنِ خودِ نامه */
  async mailCode(email, after = 0, timeout = 15_000) {
    const until = Date.now() + timeout;
    for (;;) {
      const m = [...this.inbox].reverse().find((x) => x.to === email.toLowerCase() && x.at >= after);
      const code = m && (m.text.match(/^\s*(\d{6})\s*$/m) || [])[1];
      if (code) return code;
      if (Date.now() > until) throw new Error(`کدِ ورود به ${email} نرسید`);
      await sleep(100);
    }
  }

  /** دستگاهِ برنامهٔ دکان (سطحِ پروتکل)، واردشده با کدِ ایمیل */
  async device(label, email, opts = {}) {
    const d = new NativeDevice(this, label, opts);
    await d.login(email);
    return d;
  }

  /* ════════════════════════ مدیر و پرچم ════════════════════════ */

  async adminToken() {
    if (!this._admin) this._admin = this.ok(await this.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' }), 'ورودِ مدیر').token;
    return this._admin;
  }

  /** پرچم فقط برای همین دکان، از همان راهِ «تنظیماتِ زنده»ی پنل */
  async flag(shopId, key, value = true) {
    this.ok(await this.put('/api/admin/pump/live-config', { scope: String(shopId), key, value }, await this.adminToken()), `پرچمِ ${key}`);
  }

  /* ════════════════════════ کرومیوم: فقط ویترین و پنلِ مدیر ════════════════════════ */

  async page(label, { width = 390, height = 844 } = {}) {
    if (!this._browser) {
      let globalRoot = '';
      try { globalRoot = execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { /* نبود */ }
      let pw = null;
      for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
        if (!p) continue;
        try { pw = require(p); break; } catch { /* بعدی */ }
      }
      if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
      this._browser = await pw.chromium.launch();
    }
    const ctx = await this._browser.newContext({ viewport: { width, height }, locale: 'fa-AF' });
    const page = await ctx.newPage();
    //  `pg.reply`: پاسخِ پنجرهٔ prompt (مثلاً «دلیل»ِ پنلِ مدیر) — مثلِ آدمی که می‌نویسد و «تأیید» می‌زند
    const pg = { label, ctx, page, reply: undefined, dialogs: [] };
    page.on('pageerror', (e) => this.pageErrors.push(`${label}: ${e.message}`));
    page.on('dialog', (d) => { pg.dialogs.push(d.message()); d.accept(d.type() === 'prompt' ? pg.reply : undefined).catch(() => {}); });
    this.pages.push(pg);
    return pg;
  }

  async shot(pg, name) {
    const file = path.join(this.shotsDir, `${this.name}-${name}.png`);
    try { await pg.page.screenshot({ path: file, fullPage: true }); } catch { /* عکس شرطِ قبولی نیست */ }
    return file;
  }

  /* ════════════════════════ گام‌ها ════════════════════════ */

  /**
   * یک گامِ سناریو. شناسه (مثلاً `E2E-SYNC-12.1`) در جدولِ نهایی می‌آید.
   * `needs`: گام‌هایی که این گام بی آن‌ها معنا ندارد — اگر افتاده باشند «اجرا نشد».
   */
  async step(id, name, fn, { needs = [] } = {}) {
    const t0 = Date.now();
    const blocked = needs.filter((n) => this._failedIds.has(n));
    if (blocked.length) {
      this._failedIds.add(id);
      this.results.push({ id, name, status: 'skip', ms: 0, error: `پیش‌نیازِ ${blocked.join('، ')} قرمز است` });
      console.log(`⏭  ${id} ${name} — پیش‌نیاز قرمز`);
      return;
    }
    let error = null;
    try { await fn(); } catch (e) { error = e; }
    const verdict = classify(this.name, id, error);
    if (error && verdict.status === 'fail') for (const pg of this.pages) await this.shot(pg, `FAIL-${id}-${pg.label}`);
    const ms = Date.now() - t0;
    if (verdict.status !== 'pass' && verdict.status !== 'xpass') this._failedIds.add(id);
    this.results.push({ id, name, status: verdict.status, ms, error: error ? String(error.message || error).slice(0, 800) : null, ref: verdict.ref || null });
    const icon = { pass: '✅', fail: '❌', 'known-gap': '🟧', xpass: '⚠️' }[verdict.status];
    console.log(`${icon} ${id} ${name} (${(ms / 1000).toFixed(1)}s)${error ? `\n     ${String(error.message || error).split('\n')[0].slice(0, 500)}` : ''}${verdict.ref ? `\n     ⇐ ${verdict.ref}` : ''}`);
  }

  /** پایان: بستن، نوشتنِ نتیجه برای `run.mjs`، کدِ خروج */
  async finish() {
    for (const pg of this.pages) await pg.ctx.close().catch(() => {});
    if (this._browser) await this._browser.close().catch(() => {});
    this.outbox.stop();
    this.api.closeAllConnections?.();
    await new Promise((r) => this.api.close(r));
    await this.db.closeDb().catch(() => {});
    const failed = this.results.filter((r) => r.status === 'fail').length;
    const out = process.env.E2E_RESULTS_DIR;
    if (out) {
      fs.mkdirSync(out, { recursive: true });
      fs.writeFileSync(path.join(out, `${this.name}.json`), JSON.stringify({ scenario: this.name, results: this.results }, null, 2));
    }
    console.log(failed ? `\n❌ ${this.name}: ${failed} گامِ قرمز` : `\n✅ ${this.name}: همه سبز (یا شکافِ شناخته)`);
    process.exit(failed ? 1 : 0);
  }
}
