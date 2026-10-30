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
