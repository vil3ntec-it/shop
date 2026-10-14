/**
 * bench — حجمِ APK و نصب‌کننده از انتشارهای پایدار (F0-26، بخشِ ۹).
 *
 *   node bench/sizes.mjs [--json out.json]
 *
 * از API عمومیِ GitHub می‌خواند (توکن لازم نیست). هیچ چیزی دانلود نمی‌شود — فقط
 * اندازه‌ای که GitHub برای هر فایل نگه می‌دارد. بی‌توکن می‌پرسد؛ برای سقفِ
 * بالاترِ درخواست، توکن را در BENCH_GH_TOKEN بگذارید. اگر شبکه درخواستِ
 * بی‌توکن را نپذیرفت (پراکسیِ محیطِ ایجنت)، از `gh api` می‌پرسد.
 */
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

const REPO = 'vil3ntec-it/shop';
const TAGS = ['tohid-native', 'tohid-desktop'];

const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'shop-bench' };
if (process.env.BENCH_GH_TOKEN) headers.Authorization = `Bearer ${process.env.BENCH_GH_TOKEN}`;

const mb = (n) => (n / 1024 / 1024).toFixed(2);
const out = { at: new Date().toISOString(), releases: [] };

for (const tag of TAGS) {
  const api = `repos/${REPO}/releases/tags/${tag}`;
  let r = null, why = '';
  try {
    const res = await fetch(`https://api.github.com/${api}`, { headers });
    if (res.ok) r = await res.json(); else why = `HTTP ${res.status}`;
  } catch (e) { why = e.message; }
  if (!r) { try { r = JSON.parse(execFileSync('gh', ['api', api], { encoding: 'utf8' })); } catch { /* gh نیست */ } }
  if (!r) { console.log(`❌  ${tag}: ${why}`); out.releases.push({ tag, error: why }); continue; }
  const assets = (r.assets || []).map((a) => ({ name: a.name, bytes: a.size, updated: a.updated_at }));
  out.releases.push({ tag, name: r.name, published: r.published_at, assets });
  console.log(`\n${tag} — ${r.name} (${r.published_at})`);
  for (const a of assets) console.log(`  ${a.name.padEnd(48)} ${String(a.bytes).padStart(11)} B  ${mb(a.bytes).padStart(7)} MB`);
}

const i = process.argv.indexOf('--json');
if (i > 0 && process.argv[i + 1]) fs.writeFileSync(process.argv[i + 1], JSON.stringify(out, null, 2));
