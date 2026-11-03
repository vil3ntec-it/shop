'use strict';
/**
 * SEC-AI-01 — قراردادِ API (`docs/api/routes.json`، #583) دیگر بی‌صدا کهنه نمی‌شود.
 *
 * چرا کهنه ماند: تنها آزمونِ CI (`tools/api-contract/extract.test.cjs` در «کیفیتِ کد») فقط پارسرِ تقریبی را می‌سنجد؛
 * خودِ `generate.cjs --check` سرور را بالا می‌آورد و `server/node_modules` می‌خواهد، پس در هیچ کاری اجرا نمی‌شد و از
 * #605 به بعد ۲۰۵ مسیرِ تازه بی آن‌که کسی بفهمد بیرون از قرارداد ماند. این‌جا همان `--check` در کارِ سرور (که
 * وابستگی‌ها را دارد) سد است.
 *
 *   ۱) قراردادِ مخزن با سرورِ همین کامیت یکی است — با envِ «آلوده» (دیتابیسِ واقعیِ ناموجود، منطقهٔ زمانیِ دیگر، پرچم‌های
 *      زنده)، چون مولد خودش را با envِ پاک دوباره اجرا می‌کند و خروجی نباید به ماشین وابسته باشد.
 *   ۲) نگهبان تهی نیست: قراردادی که یک مسیرش افتاده سرخ می‌شود و همان مسیر را نام می‌برد.
 *
 * سرخ شد؟ `node tools/api-contract/generate.cjs` و کامیتِ `docs/api/routes.json`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..');
const GEN = path.join(ROOT, 'tools', 'api-contract', 'generate.cjs');
const CONTRACT = path.join(ROOT, 'docs', 'api', 'routes.json');

const polluted = {
  ...process.env,
  DATABASE_URL: 'postgres://nobody:nothing@127.0.0.1:1/none',
  TEST_DATABASE_URL: 'postgres://nobody:nothing@127.0.0.1:1/none',
  TZ: 'Asia/Kabul',
  LANG: 'fa_AF.UTF-8',
  SYNC_V3_ENABLED: '1',
  BACKUP_ENABLED: 'true',
};
//  یک بار در هر اجرای CI بس است: کارهای PostgreSQL و PGlite. زیرِ Electron ِ مرکز فرمان Node ِ دیگری است و مولد خودش را با
//  همان دوباره اجرا می‌کند؛ قرارداد به سرور وابسته است، نه به Node ِ اجراکننده.
const skip = process.versions.electron ? 'زیرِ Electron: قرارداد در کارهای PostgreSQL و PGlite سنجیده می‌شود' : false;
const check = (extra = []) => spawnSync(process.execPath, [GEN, '--check', ...extra], {
  cwd: os.tmpdir(), env: polluted, encoding: 'utf8', timeout: 120_000,
});

test('SEC-AI-01: docs/api/routes.json همان مسیرهای سرورِ همین کامیت است (envِ ماشین بی‌اثر)', { skip }, () => {
  const r = check();
  assert.equal(r.status, 0, `قرارداد کهنه است — node tools/api-contract/generate.cjs\n${r.stdout}${r.stderr}`);
  assert.match(r.stdout, /تازه است/);
});

test('SEC-AI-01: قراردادی که یک مسیرش افتاده سرخ می‌شود و همان مسیر را نام می‌برد', { skip }, () => {
  const lines = fs.readFileSync(CONTRACT, 'utf8').split('\n');
  const i = lines.findIndex((l) => l.includes('"path":"/api/admin/2fa"') && l.includes('"method":"GET"'));
  assert.ok(i > 0, 'مسیرِ نمونه در قرارداد هست');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'api-contract-test-'));
  try {
    const stale = path.join(dir, 'routes.json');
    fs.writeFileSync(stale, lines.filter((_, k) => k !== i).join('\n'));
    const r = check(['--out', stale]);
    assert.equal(r.status, 1, `${r.stdout}${r.stderr}`);
    assert.match(r.stderr, /کهنه است/);
    assert.match(r.stderr, /\+ GET \/api\/admin\/2fa\n/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
