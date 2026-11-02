/**
 * آزمونِ model pack ِ CodeQL (`.github/codeql/extensions/`). اجرا: node --test "tools/security/*.test.mjs"
 *
 * CodeQL ِ محلی نیست، پس این آزمون آنچه را بی CodeQL سنجیدنی است سد می‌کند تا model بی‌صدا کهنه نشود:
 *   • هر سطرِ `file:<مسیر>` به فایلِ موجود و export ِ موجود اشاره دارد (جابه‌جایی یا تغییرِ نام ⇒ قرمز)؛
 *   • `rateLimit` ِ مخزن واقعاً سقف می‌گذارد (۴۲۹ + `Retry-After`)، پس هم‌ارز گرفتنش با express-rate-limit راست است؛
 *   • pack به `codeql/javascript-all` وصل است و `codeql.yml` با تغییرِ آن اجرا می‌شود؛
 *   • هیچ‌جا js/missing-rate-limiting خاموش یا فیلتر نشده (model جای ساکت کردن نیست).
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const EXT = path.join(ROOT, '.github', 'codeql', 'extensions');
const PACK = path.join(EXT, 'shop-javascript');
const require = createRequire(import.meta.url);

/** سطرهای داده‌ای ِ هر فایلِ model: {file, extensible, row} — سطرها آرایهٔ JSON‌اند (بی کتابخانهٔ YAML). */
function modelRows() {
  const out = [];
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else if (/\.ya?ml$/.test(e.name) && e.name !== 'codeql-pack.yml') {
        let extensible = null;
        for (const line of fs.readFileSync(p, 'utf8').split('\n')) {
          const ex = /^\s*extensible:\s*(\S+)/.exec(line);
          if (ex) extensible = ex[1];
          const row = /^\s*-\s*(\[.*\])\s*$/.exec(line);
          if (row) out.push({ file: path.relative(ROOT, p), extensible, row: JSON.parse(row[1]) });
        }
      }
    }
  };
  walk(EXT);
  return out;
}

test('pack: وصل به codeql/javascript-all و همهٔ models/', () => {
  const y = fs.readFileSync(path.join(PACK, 'codeql-pack.yml'), 'utf8');
  assert.match(y, /^library:\s*true\s*$/m);
  assert.match(y, /^extensionTargets:\s*\n\s+codeql\/javascript-all:\s*'\*'\s*$/m);
  assert.match(y, /^dataExtensions:\s*\n\s+-\s*models\/\*\*\/\*\.yml\s*$/m);
});

test('model ِ rateLimit هست و هر file: به فایل و export ِ موجود می‌رسد', () => {
  const rows = modelRows();
  const rl = rows.filter((r) => r.extensible === 'typeModel' && r.row[0] === 'express-rate-limit');
  assert.deepEqual(rl.map((r) => r.row), [['express-rate-limit', 'file:server/src/middleware/ratelimit.js', 'Member[rateLimit]']]);
  for (const { file, row } of rows) {
    for (const cell of row) {
      const m = /^file:(.+)$/.exec(cell);
      if (!m) continue;
      const target = path.join(ROOT, m[1]);
      assert.ok(fs.existsSync(target), `${file}: ${cell} نیست`);
      const member = /^Member\[([^\]]+)\]/.exec(row[row.indexOf(cell) + 1] || '');
      if (member) assert.equal(typeof require(target)[member[1]], 'function', `${file}: ${m[1]} ${member[1]} را export نمی‌کند`);
    }
  }
});

test('rateLimit واقعاً سقف می‌گذارد: پس از max ⇒ ۴۲۹ و Retry-After', () => {
  const { rateLimit, resetAll } = require(path.join(ROOT, 'server/src/middleware/ratelimit.js'));
  resetAll();
  const mw = rateLimit({ max: 2, windowMs: 60_000, keyPrefix: 'codeql-model-test' });
  assert.equal(typeof mw, 'function');
  assert.equal(mw.length, 3, 'میان‌افزارِ Express: (req, res, next)');
  const req = { socket: { remoteAddress: '203.0.113.7' }, headers: {} };
  const headers = {};
  const res = { set: (k, v) => { headers[k] = v; } };
  const calls = [];
  for (let i = 0; i < 3; i += 1) mw(req, res, (err) => calls.push(err));
  assert.deepEqual(calls.slice(0, 2), [undefined, undefined]);
  assert.equal(calls[2]?.status, 429);
  assert.equal(calls[2]?.code, 'rate_limited');
  assert.match(headers['Retry-After'], /^\d+$/);
  resetAll();
});

test('codeql.yml: با تغییرِ pack اجرا می‌شود و پرسش خاموش/فیلتر نشده', () => {
  const y = fs.readFileSync(path.join(ROOT, '.github', 'workflows', 'codeql.yml'), 'utf8');
  assert.equal((y.match(/^\s+- '\.github\/codeql\/\*\*'$/gm) || []).length, 2, 'هم pull_request و هم push');
  const code = y.split('\n').filter((l) => !/^\s*#/.test(l)).join('\n');
  assert.doesNotMatch(code, /missing-rate-limiting|disable-default-queries/, 'پرسش نباید فیلتر یا خاموش شود');
  assert.match(code, /queries: security-extended/);
});
