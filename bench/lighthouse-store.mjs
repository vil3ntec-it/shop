/**
 * Lighthouse ِ ویترین (WEB-15، اختیاری؛ در CI نیست) — امتیازِ «کارایی» برای خانهٔ ویترین و صفحهٔ کالا روی همان دکانِ
 * `server/test/vitals-store.mjs` (۱۲ کالا، ۱۰ عکس، gzip ِ همان Caddy).
 *
 *   npm i -g lighthouse@13.6.0              # یا LIGHTHOUSE_DIR=<پوشه‌ای که node_modules/lighthouse دارد>
 *   TEST_DATABASE_URL=pglite:memory node bench/lighthouse-store.mjs [--devtools] [--runs 3] [--json out.json]
 *
 * پیش‌فرض همان پیش‌تنظیمِ موبایلِ Lighthouse است (شبیه‌سازیِ Lantern: ۱۵۰ms، ۱٫۶Mbps، پردازنده ۴×)؛ `--devtools`
 * همان شبکه را واقعاً روی مرورگر می‌گذارد (۵۶۲٫۵ms برای هر درخواست، مثلِ نمایهٔ 3G ِ `web-store-vitals.mjs`).
 * کرومیوم: CHROME_PATH، وگرنه `/opt/pw-browsers/chromium`، وگرنه همان کرومیومِ پلی‌رایت؛ همیشه `--headless --no-sandbox`.
 * بی lighthouse ⇒ کدِ ۲ و راهِ نصب — هیچ امتیازی ساخته نمی‌شود. امتیاز روی سخت‌افزارِ همین ماشین است، نه گوشیِ واقعی.
 */
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const arg = (name, def) => { const i = process.argv.indexOf(name); return i > 0 ? process.argv[i + 1] : def; };
const devtools = process.argv.includes('--devtools');
const runs = Math.max(1, Number(arg('--runs', 3)) || 3);
const jsonOut = arg('--json', '');

const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const roots = [process.env.LIGHTHOUSE_DIR && path.join(process.env.LIGHTHOUSE_DIR, 'node_modules'), globalRoot,
  path.join(import.meta.dirname, '..', 'server', 'node_modules'), path.join(import.meta.dirname, '..', 'node_modules')].filter(Boolean);
const find = (pkg, entry) => roots.map((r) => path.join(r, pkg, entry)).find((f) => fs.existsSync(f));
const lhEntry = find('lighthouse', 'core/index.js');
const launcherEntry = find('chrome-launcher', 'dist/index.js') || (lhEntry && path.join(path.dirname(path.dirname(lhEntry)), 'node_modules', 'chrome-launcher', 'dist', 'index.js'));
if (!lhEntry || !launcherEntry || !fs.existsSync(launcherEntry)) {
  console.error('❌ lighthouse پیدا نشد — امتیازی ساخته نشد.\n   نصب: npm i -g lighthouse@13.6.0   (یا LIGHTHOUSE_DIR=<پوشه>)');
  process.exit(2);
}
const { default: lighthouse } = await import(pathToFileURL(lhEntry).href);
const chromeLauncher = await import(pathToFileURL(launcherEntry).href);

let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) { console.error('❌ پلی‌رایت پیدا نشد (برای ساختنِ عکس‌های دکانِ آزمایشی)'); process.exit(2); }
const { startVitalsStore } = await import(pathToFileURL(path.join(import.meta.dirname, '..', 'server', 'test', 'vitals-store.mjs')).href);

const pw = await chromium.launch();
const store = await startVitalsStore(pw);
await pw.close();
for (const [c, m, d] of store.checks) if (!c) { console.error(`❌ ${m} — ${d}`); process.exit(1); }

const chromePath = process.env.CHROME_PATH || (fs.existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : chromium.executablePath());
const chrome = await chromeLauncher.launch({ chromePath, chromeFlags: ['--headless=new', '--no-sandbox', '--disable-gpu'] });
const settings = devtools
  ? { throttlingMethod: 'devtools', throttling: { requestLatencyMs: 562.5, downloadThroughputKbps: 1474.56, uploadThroughputKbps: 675, cpuSlowdownMultiplier: 4 } }
  : {};
const PAGES = { home: `${store.base}/store/${store.slug}`, product: `${store.base}/store/${store.slug}#/p/rice` };
const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; };
const out = { lighthouse: null, method: devtools ? 'devtools' : 'simulate', chromePath, pages: {} };
try {
  for (const [name, url] of Object.entries(PAGES)) {
    const rs = [];
    for (let i = 0; i < runs; i++) {
      const r = await lighthouse(url, { port: chrome.port, output: 'json', logLevel: 'error', onlyCategories: ['performance'], ...settings });
      const a = r.lhr.audits;
      out.lighthouse = r.lhr.lighthouseVersion;
      if (r.lhr.runtimeError) console.error(`⚠️  ${name}: ${r.lhr.runtimeError.code} ${r.lhr.runtimeError.message}`);
      rs.push({ score: Math.round((r.lhr.categories.performance.score || 0) * 100), fcp: Math.round(a['first-contentful-paint'].numericValue),
        lcp: Math.round(a['largest-contentful-paint'].numericValue), tbt: Math.round(a['total-blocking-time'].numericValue),
        cls: Math.round(a['cumulative-layout-shift'].numericValue * 1000) / 1000, si: Math.round(a['speed-index'].numericValue) });
    }
    const m = Object.fromEntries(Object.keys(rs[0]).map((k) => [k, median(rs.map((x) => x[k]))]));
    out.pages[name] = { runs: rs, median: m };
    console.log(`${name}: امتیاز ${rs.map((x) => x.score).join('، ')} (میانه ${m.score}) — FCP ${m.fcp}ms، LCP ${m.lcp}ms، TBT ${m.tbt}ms، CLS ${m.cls}، SI ${m.si}ms`);
  }
} finally {
  await chrome.kill();
  await store.close();
}
console.log(`Lighthouse ${out.lighthouse}، روش ${out.method}، ${runs} بار برای هر صفحه`);
if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify(out, null, 1));
process.exit(0);
