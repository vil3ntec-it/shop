#!/usr/bin/env node
'use strict';
/**
 * قراردادِ API برای کارِ فرانت‌اند بی سورسِ بک‌اند — پایهٔ SEC-AI-01 (docs/security/ai-access-fa.md).
 *
 *   node tools/api-contract/generate.cjs           ⇒ docs/api/routes.json را از نو می‌نویسد
 *   node tools/api-contract/generate.cjs --check   ⇒ کد ۱ اگر docs/api/routes.json کهنه باشد
 *
 * پیش‌نیاز: `server/node_modules` (cd server && npm ci). شبکه، دیتابیسِ واقعی و secret لازم نیست.
 *
 * چطور: خودش را با envِ **پاک** دوباره اجرا می‌کند (PGlite در حافظه، secretِ ساختگی، بی DATABASE_URL ِ
 * محیط ⇒ هرگز به دیتابیسِ واقعی وصل نمی‌شود)، Router/Route ِ Express را می‌پاید تا جای هر ثبتِ مسیر را
 * (فایل:خط) بداند، `createApp()` ِ واقعی را می‌سازد و درختِ router را می‌پیماید. پس فهرستِ مسیرها دقیق
 * است (همان که سرور سوار می‌کند). شکلِ درخواست/پاسخ از متنِ همان handler تقریبی است (`extract.cjs`).
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const { spawnSync } = require('child_process');
const { createRequire } = require('module');
const X = require('./extract.cjs');

const ROOT = path.resolve(__dirname, '..', '..');
const SERVER = path.join(ROOT, 'server');
const OUT = path.join(ROOT, 'docs', 'api', 'routes.json');
const CHECK = process.argv.includes('--check');

if (process.env.API_CONTRACT_CHILD !== '1') {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'api-contract-'));
  const env = {
    PATH: process.env.PATH, HOME: tmp, TMPDIR: tmp, API_CONTRACT_CHILD: '1', NODE_ENV: 'test', TZ: 'UTC',
    DATABASE_URL: 'pglite:memory', BACKUP_ENABLED: 'false', BACKUP_PATH: path.join(tmp, 'backups'),
    API_SECRET: 'api-contract-synthetic-not-a-secret', OTP_SECRET: 'api-contract-synthetic-not-a-secret2',
  };
  const r = spawnSync(process.execPath, [__filename], { env, encoding: 'utf8', maxBuffer: 64 << 20, stdio: ['ignore', 'pipe', 'inherit'] });
  fs.rmSync(tmp, { recursive: true, force: true });
  if (r.status !== 0) process.exit(r.status || 1);
  const text = r.stdout;
  if (CHECK) {
    const old = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : '';
    if (old !== text) {
      console.error('✖ docs/api/routes.json کهنه است: node tools/api-contract/generate.cjs');
      process.exit(1);
    }
    console.log('✓ docs/api/routes.json تازه است');
  } else {
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, text);
    const n = JSON.parse(text).routes.length;
    console.log(`✓ ${path.relative(ROOT, OUT)}: ${n} مسیر`);
  }
  process.exit(0);
}

// ─────────────── فرزند: سرور را بساز، ثبت‌ها را بپا، درخت را بپیما ───────────────
const sreq = createRequire(path.join(SERVER, 'package.json'));
const ereq = createRequire(sreq.resolve('express'));
if (ereq.resolve('router') !== sreq.resolve('router')) throw new Error('router ِ express با router ِ server یکی نیست');
const Router = sreq('router');
const Route = sreq('router/lib/route');

const sources = new Map();
const srcOf = (file) => { if (!sources.has(file)) sources.set(file, fs.readFileSync(file, 'utf8')); return sources.get(file); };
const rel = (file) => path.relative(ROOT, file).split(path.sep).join('/');

/** نخستین frame ِ درونِ server/ (بیرون از node_modules) — جای واقعیِ ثبت. */
function callSite() {
  for (const l of new Error().stack.split('\n').slice(2)) {
    const m = /\(?([^()\s]+):(\d+):(\d+)\)?$/.exec(l.trim());
    if (!m || !m[1].startsWith(SERVER + path.sep) || m[1].includes(`${path.sep}node_modules${path.sep}`)) continue;
    return { file: m[1], line: Number(m[2]), col: Number(m[3]) };
  }
  return null;
}
const siteKey = (s) => (s ? `${s.file}:${s.line}:${s.col}` : '');

const origUse = Router.prototype.use;
Router.prototype.use = function use(...args) {
  const before = this.stack.length;
  const site = callSite();
  let first = args[0];
  while (Array.isArray(first) && first.length) first = first[0];
  const mount = typeof first === 'function' ? '/' : args[0];
  const out = origUse.apply(this, args);
  const sameCall = this.__lastSite === siteKey(site);
  for (let i = before; i < this.stack.length; i++) {
    const idx = (sameCall ? this.__lastIdx + 1 : 0) + (i - before);
    Object.assign(this.stack[i], { __mount: mount, __site: site, __idx: idx, __pathArg: mount !== '/' || typeof args[0] !== 'function' });
  }
  this.__lastSite = siteKey(site);
  this.__lastIdx = this.stack[this.stack.length - 1].__idx;
  return out;
};
for (const m of http.METHODS.map((x) => x.toLowerCase()).concat('all')) {
  const orig = Route.prototype[m];
  if (typeof orig !== 'function') continue;
  Route.prototype[m] = function verb(...handlers) {
    const before = this.stack.length;
    const site = callSite();
    const out = orig.apply(this, handlers);
    for (let i = before; i < this.stack.length; i++) Object.assign(this.stack[i], { __site: site, __idx: i - before, __n: this.stack.length - before });
    return out;
  };
}

const GUARD = /^(require\w*|optional\w*|allow\w*|rateLimit|limit\w*|\w*[Gg]uard\w*)\b/;
const join = (a, b) => (`${a}/${b}`).replace(/\/+/g, '/').replace(/(.)\/$/, '$1');
const isRouter = (fn) => typeof fn === 'function' && Array.isArray(fn.stack) && typeof fn.handle === 'function';

/** برچسبِ میان‌افزار: نامِ تابع، وگرنه متنِ همان آرگومان در فراخوانی. */
function labelOf(fn, site, idx, dropFirst) {
  if (fn.name && !/^(anonymous|bound .*|handle|wrapped|verb)$/.test(fn.name)) return fn.name;
  if (!site) return '';
  const args = X.callArgsAt(srcOf(site.file), site.line, site.col) || [];
  const t = (dropFirst ? args.slice(1) : args)[idx] || '';
  return t.replace(/\s+/g, ' ').slice(0, 80);
}

function authOf(guards, handlerText) {
  const g = guards.join(' ');
  if (/requireAdmin|requireSuperAdmin/.test(g)) return 'admin';
  if (/requireDevice/.test(g)) return 'device';
  if (/require(User|AnyUser|Shop|PumpUser|Station|SyncAccount|UserOf|Courier)/.test(g)) return 'user';
  if (/\bbearer\(req\)|req\.headers\.authorization|req\.get\(\s*['"]authorization/i.test(handlerText)) return 'inline';
  if (/optional(Shop|Station)/.test(g)) return 'optional';
  return 'none-detected';
}

/** متنِ handler: آرگومانِ درون‌خطی، وگرنه toString ِ همان تابع، وگرنه تابعِ نام‌دارِ همان فایل. */
function handlerText(argText, fn, site) {
  if (argText && /=>|function\b/.test(argText)) return argText;
  const own = Function.prototype.toString.call(fn);
  if (own.length > 160 && /\breq\b/.test(own)) return own;
  const name = /([\w$]+)\s*\)?\s*$/.exec(argText || '');
  return name && site ? X.namedFunctionText(srcOf(site.file), name[1]) : '';
}

const routes = [];
const statics = [];

function emit(route, full, guards) {
  const groups = [];
  for (const l of route.stack) {
    const g = groups[groups.length - 1];
    if (g && g.method === l.method && siteKey(g.site) === siteKey(l.__site) && l.__idx > 0) g.layers.push(l);
    else groups.push({ method: l.method, site: l.__site, layers: [l] });
  }
  for (const g of groups) {
    const n = g.layers[0].__n || g.layers.length;
    let args = g.site ? X.callArgsAt(srcOf(g.site.file), g.site.line, g.site.col) || [] : [];
    if (args.length === n + 1) args = args.slice(1);
    const own = g.layers.slice(0, -1).map((l, i) => labelOf(l.handle, null, 0) || (args[i] || '').replace(/\s+/g, ' ').slice(0, 80));
    const all = [...new Set([...guards, ...own.filter((t) => GUARD.test(t))])];
    const hText = handlerText(args[n - 1], g.layers[g.layers.length - 1].handle, g.site);
    const shape = X.shapeOf(hText);
    const pathParams = [...full.matchAll(/[:*]([A-Za-z_$][\w$]*)/g)].map((m) => m[1]);
    routes.push({
      method: (g.method || 'all').toUpperCase(),
      path: full,
      area: (/^\/api\/([^/]+)/.exec(full) || [, 'web'])[1],
      source: g.site ? rel(g.site.file) : null,
      auth: authOf(all, hText),
      guards: all,
      params: [...new Set([...pathParams, ...shape.params])].sort(),
      query: shape.query,
      body: shape.body,
      response: { statuses: shape.statuses, keys: shape.responseKeys },
      errors: shape.errors,
      shape: !hText ? 'unresolved' : shape.found ? 'extracted' : 'empty',
    });
  }
}

function walk(router, prefix, inherited) {
  const guards = inherited.slice();
  for (const layer of router.stack) {
    if (layer.route) {
      const paths = [].concat(layer.route.path);
      const scoped = guards.filter((g) => g.scope === '/' || paths.some((p) => String(p).startsWith(g.scope))).map((g) => g.label);
      for (const p of paths) emit(layer.route, join(prefix, String(p)), [...new Set(scoped)]);
      continue;
    }
    const mounts = [].concat(layer.__mount ?? '/').map(String);
    if (isRouter(layer.handle)) {
      for (const m of mounts) {
        const passed = guards.filter((g) => g.scope === '/' || m.startsWith(g.scope))
          .map((g) => ({ ...g, scope: '/' }));
        walk(layer.handle, join(prefix, m), passed);
      }
    } else if (layer.handle.name === 'serveStatic') {
      for (const m of mounts) statics.push({ path: join(prefix, m), source: layer.__site ? rel(layer.__site.file) : null });
    } else {
      const label = labelOf(layer.handle, layer.__site, layer.__idx, layer.__pathArg);
      if (GUARD.test(label)) for (const m of mounts) guards.push({ scope: m, label });
    }
  }
}

(async () => {
  const log = console.log;
  console.log = () => {};
  console.warn = () => {};
  const { createApp } = sreq('./src/app');
  const app = await createApp();
  console.log = log;
  walk(app.router, '', []);

  //  `/api/v1/…` همان روترِ `/api/…` است: اگر یکی‌اند فقط یک بار نوشته می‌شود
  const key = (r) => `${r.method} ${r.path}`;
  const v1 = routes.filter((r) => r.path.startsWith('/api/v1/'));
  const plain = new Set(routes.filter((r) => !r.path.startsWith('/api/v1/')).map(key));
  const mirrored = v1.length > 0 && v1.every((r) => plain.has(key({ ...r, path: r.path.replace('/api/v1/', '/api/') })));
  const list = (mirrored ? routes.filter((r) => !r.path.startsWith('/api/v1/')) : routes);
  const seen = new Set();
  for (const r of list) { if (seen.has(key(r))) r.duplicateOfEarlier = true; seen.add(key(r)); }
  list.sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : a.method < b.method ? -1 : a.method > b.method ? 1 : 0));

  const head = {
    about: 'قراردادِ ماشین‌خوانِ API ِ سرور برای کارِ فرانت‌اند بی سورسِ بک‌اند (SEC-AI-01). ساخته‌شده؛ دستی ویرایش نکنید.',
    generator: 'node tools/api-contract/generate.cjs (--check)',
    doc: 'docs/security/ai-access-fa.md',
    aliases: mirrored ? { '/api/v1/': '/api/' } : {},
    errorEnvelope: { shape: { error: { code: 'string', message: 'string', ref: 'string (فقط 5xx)' } }, source: 'server/src/middleware/errors.js' },
    fields: {
      auth: 'admin | user | device | optional | inline (توکن درونِ handler) | none-detected (هیچ نگهبانِ نام‌داری دیده نشد؛ به معنای «عمومی» نیست تا بازبینی شود)',
      shape: 'extracted | empty | unresolved — شکلِ درخواست/پاسخ تقریبی است: فیلدی که به تابعِ کمکی می‌رود دیده نمی‌شود',
    },
    counts: { routes: list.length, byAuth: {}, byShape: {} },
  };
  for (const r of list) {
    head.counts.byAuth[r.auth] = (head.counts.byAuth[r.auth] || 0) + 1;
    head.counts.byShape[r.shape] = (head.counts.byShape[r.shape] || 0) + 1;
  }
  const lines = JSON.stringify(head, null, 2).replace(/\n}$/, '');
  const body = list.map((r) => `    ${JSON.stringify(r)}`).join(',\n');
  const st = statics.map((s) => `    ${JSON.stringify(s)}`).join(',\n');
  //  stdout ِ لوله ناهمگام است: پیش از exit باید خالی شود، وگرنه فایل نیمه می‌ماند
  process.stdout.write(`${lines},\n  "static": [\n${st}\n  ],\n  "routes": [\n${body}\n  ]\n}\n`, () => process.exit(0));
})().catch((e) => { console.error('✖', e.stack || e.message); process.exit(1); });
