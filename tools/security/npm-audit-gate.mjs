#!/usr/bin/env node
/**
 * دروازهٔ `npm audit` (§۱۴ برنامهٔ امنیت، docs/security/supply-fa.md).
 *
 *   node tools/security/npm-audit-gate.mjs [پوشه=server] [--json-out <فایل>]
 *
 * فقط وابستگی‌های production (`--omit=dev`) و از روی package-lock.json.
 *   critical ⇒ همیشه سد · high ⇒ سد از HIGH_BLOCK_FROM (تا آن روز هشدار) · بقیه ⇒ فقط گزارش.
 * استثنا فقط با شناسهٔ GHSA، دلیل و تاریخِ انقضا در `audit-allow.json` (بی تاریخ پذیرفته نیست).
 * خطای شبکه/رجیستری (نه یافته) ⇒ سه بار تلاش، بعد هشدار و عبور: CI به‌خاطرِ قطعیِ رجیستری قرمز نشود.
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
/** ۱۴۰۵/۱۰/۰۱ — از این روز high هم سد است (طرحِ docs/security/supply-fa.md) */
export const HIGH_BLOCK_FROM = '2026-12-22';

/** مشاوره‌های یکتا از گزارشِ JSONِ نسخهٔ ۲ؚ npm audit */
export function advisories(report) {
  const out = new Map();
  for (const [pkg, v] of Object.entries(report.vulnerabilities || {})) {
    for (const via of v.via || []) {
      if (typeof via !== 'object' || !via) continue;
      const id = (/GHSA-[a-z0-9-]+/i.exec(via.url || '') || [String(via.source ?? pkg)])[0];
      if (!out.has(id)) out.set(id, { id, severity: via.severity || v.severity, package: via.name || pkg, range: via.range || v.range, title: via.title || '' });
    }
  }
  return [...out.values()];
}

/** تصمیم: {block, warn, info, allowed, expired} */
export function evaluate(report, allow = [], today = new Date().toISOString().slice(0, 10)) {
  const res = { block: [], warn: [], info: [], allowed: [], expired: [] };
  for (const a of advisories(report)) {
    const ex = allow.find((x) => x.id === a.id);
    if (ex && ex.until && today <= ex.until) { res.allowed.push({ ...a, until: ex.until }); continue; }
    if (ex) res.expired.push({ ...a, until: ex.until || '—' });
    if (a.severity === 'critical' || (a.severity === 'high' && today >= HIGH_BLOCK_FROM)) res.block.push(a);
    else if (a.severity === 'high') res.warn.push(a);
    else res.info.push(a);
  }
  return res;
}

/** گزارشِ خطای زیرساخت (نه یافته)؟ */
export function infraError(report) {
  if (!report || typeof report !== 'object') return 'خروجیِ npm audit JSON نیست';
  if (report.error) return `npm audit: ${report.error.code || ''} ${report.error.summary || ''}`.trim();
  if (!report.vulnerabilities || !report.metadata) return 'گزارشِ npm audit ناقص است';
  return null;
}

function audit(dir) {
  const r = spawnSync('npm', ['audit', '--omit=dev', '--json'], { cwd: dir, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  try { return JSON.parse(r.stdout); } catch { return null; }
}

function main(argv) {
  const dir = argv.find((a) => !a.startsWith('--') && argv[argv.indexOf(a) - 1] !== '--json-out') || 'server';
  const outIdx = argv.indexOf('--json-out');
  const allow = JSON.parse(fs.readFileSync(path.join(HERE, 'audit-allow.json'), 'utf8')).allow;
  let report = null; let err = null;
  for (let i = 1; i <= 3; i++) {
    report = audit(dir); err = infraError(report);
    if (!err) break;
    console.log(`::warning::npm audit (${dir}) تلاشِ ${i}: ${err}`);
  }
  if (outIdx >= 0 && report) {
    fs.mkdirSync(path.dirname(argv[outIdx + 1]), { recursive: true });
    fs.writeFileSync(argv[outIdx + 1], JSON.stringify(report, null, 2));
  }
  if (err) {
    console.log(`::warning title=npm audit::رجیستری پاسخ نداد (${err}) — این اجرا سنجیده نشد (NOT-TESTED)، سد نشد`);
    return 0;
  }
  const r = evaluate(report, allow);
  const line = (a) => `${a.id} ${a.package} ${a.range} — ${a.title}`;
  for (const a of r.block) console.log(`::error title=npm audit ${a.severity}::${line(a)} ⇒ cd ${dir} && npm audit fix --omit=dev --package-lock-only`);
  for (const a of r.warn) console.log(`::warning title=npm audit high::${line(a)} (از ${HIGH_BLOCK_FROM} سد می‌شود)`);
  for (const a of r.info) console.log(`  ${a.severity}: ${line(a)}`);
  for (const a of r.allowed) console.log(`  استثنا تا ${a.until}: ${line(a)}`);
  for (const a of r.expired) console.log(`::warning title=npm audit::استثنای ${a.id} گذشته یا بی تاریخ است (${a.until}) — دیگر پذیرفته نیست`);
  const m = report.metadata.vulnerabilities || {};
  console.log(`npm audit ${dir}: critical ${m.critical || 0}، high ${m.high || 0}، moderate ${m.moderate || 0}، low ${m.low || 0} — ${r.block.length} سد`);
  return r.block.length ? 1 : 0;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  process.exit(main(process.argv.slice(2)));
}
