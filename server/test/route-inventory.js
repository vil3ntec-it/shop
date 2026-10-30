'use strict';
/**
 * فهرستِ مسیرها از **خودِ سورس** — همان روشِ `shop-route-inventory.test.js`
 * (سوار شدن‌ها از `src/app.js` و روترهای تودرتو از `router.use(require('./…'))`)،
 * این‌جا جدا تا آزمون‌های دیگر هم آن را بخوانند (WP-0: SUB-10، E2E-SEC-13).
 *
 * هر مسیر: { method, path, file, head, uses }
 *   head — متنِ میان‌افزارهای همان خط تا دستگیره (`(req` / `wrap(`)
 *   uses — میان‌افزارهای `router.use(...)`ِ پیش از آن مسیر در همان فایل
 */
const fs = require('node:fs');
const nodePath = require('node:path');

const SRC = nodePath.join(__dirname, '..', 'src');
const ROUTES = nodePath.join(SRC, 'routes');

/** سوار شدن‌های زیرِ `prefix` (مثلاً '/shop' یا '/courier'): [{ prefix, file }] */
function mounts(prefix) {
  const app = fs.readFileSync(nodePath.join(SRC, 'app.js'), 'utf8');
  const esc = prefix.replace(/[/]/g, '\\/');
  const re = new RegExp(`api\\.use\\(\\s*'(${esc}(?:\\/[^']*)?)'\\s*,\\s*require\\('\\.\\/routes\\/([\\w-]+)'\\)\\s*\\)`, 'g');
  const out = [];
  for (const m of app.matchAll(re)) out.push({ prefix: m[1], file: `${m[2]}.js` });
  for (let i = 0; i < out.length; i++) {
    const src = fs.readFileSync(nodePath.join(ROUTES, out[i].file), 'utf8');
    for (const n of src.matchAll(/router\.use\(\s*require\('\.\/([\w-]+)'\)\s*\)/g)) {
      if (!out.some((o) => o.file === `${n[1]}.js` && o.prefix === out[i].prefix)) out.push({ prefix: out[i].prefix, file: `${n[1]}.js` });
    }
  }
  return out;
}

/** همهٔ مسیرهای زیرِ `prefix`، بی تکرار. */
function routes(prefix) {
  const re = /router\.(get|post|put|patch|delete)\(\s*(\[[^\]]*\]|[`'"][^`'"]+[`'"])\s*,/g;
  const out = [];
  const seen = new Set();
  for (const { prefix: pre, file } of mounts(prefix)) {
    const src = fs.readFileSync(nodePath.join(ROUTES, file), 'utf8');
    for (const m of src.matchAll(re)) {
      const end = [src.indexOf('(req', m.index), src.indexOf('wrap(', m.index)].filter((i) => i > 0);
      const head = src.slice(m.index, end.length ? Math.min(...end) : m.index + 200);
      const uses = [...src.slice(0, m.index).matchAll(/router\.use\(([^;]*?)\);/g)].map((u) => u[1]).join(' ');
      for (const p of [...m[2].matchAll(/[`'"]([^`'"]+)[`'"]/g)].map((x) => x[1])) {
        const path = pre + (p === '/' ? '' : p);
        const key = `${m[1].toUpperCase()} ${path}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ method: m[1].toUpperCase(), path, key, file, head, uses });
      }
    }
  }
  return out;
}

module.exports = { mounts, routes };

/* ── فهرستِ زنده: از روترِ ساخته‌شده از همین سورس، با نامِ میان‌افزارها ──
   همان شگردِ `role-route-isolation.test.js`، این‌جا جدا تا ماتریسِ
   جداسازی (`tenant-matrix.test.js`) هم بخواندش. `trackMounts()` باید
   **پیش از** `require('./helpers')` صدا زده شود. */
let tracking = false;
function trackMounts() {
  if (tracking) return;
  tracking = true;
  const express = require('express');
  const proto = express.Router.prototype;
  const orig = proto.use;
  proto.use = function trackedUse(...args) {
    const before = this.stack.length;
    const out = orig.apply(this, args);
    const p = typeof args[0] === 'string' || Array.isArray(args[0]) ? args[0] : '/';
    for (let i = before; i < this.stack.length; i++) this.stack[i].__mount = p;
    return out;
  };
}

/** همهٔ درهای زیرِ `/api` از سرورِ HTTPِ در حالِ کار: [{ method, path, key, chain }] */
function live(server) {
  const app = server.listeners('request')[0];
  const out = [];
  const join = (a, b) => (a + (b === '/' ? '' : b)).replace(/\/+/g, '/');
  const walk = (router, prefix, pre) => {
    const names = [...pre];
    for (const layer of router.stack || []) {
      if (layer.route) {
        const chain = [...names, ...layer.route.stack.map((l) => l.handle.name)];
        for (const p of [layer.route.path].flat()) {
          if (typeof p !== 'string') continue;
          for (const m of Object.keys(layer.route.methods)) if (m !== '_all') out.push({ method: m.toUpperCase(), path: join(prefix, p), chain });
        }
      } else if (layer.handle && Array.isArray(layer.handle.stack)) {
        for (const mp of [layer.__mount || '/'].flat()) if (typeof mp === 'string') walk(layer.handle, join(prefix, mp), names);
      } else names.push(layer.handle?.name || '');
    }
  };
  for (const layer of app.router.stack) if (layer.__mount === '/api' && layer.handle?.stack) walk(layer.handle, '/api', []);
  const seen = new Set();
  return out.filter((r) => { const k = `${r.method} ${r.path}`; r.key = k; return !seen.has(k) && seen.add(k); });
}

module.exports.trackMounts = trackMounts;
module.exports.live = live;
