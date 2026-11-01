#!/usr/bin/env node
/**
 * bench — نتیجهٔ PostgreSQL و PGlite کنارِ هم، هر کدام جدا (PERF-06؛ §۲۹.۶ بند ۸، §۲۹.۸).
 *
 *   node bench/db-compare.mjs --pg perf-server-postgres.json --pglite perf-server-pglite.json [--md out.md]
 *
 * ورودی دو خروجیِ `bench/server.mjs --json` است، یکی با `--db postgres://…` و یکی با `--db pglite:…`
 * (کارِ «سرور — postgres/pglite» ِ `.github/workflows/perf.yml`). خروجی یک جدولِ Markdown است:
 *   • هر خانه فقط از پروندهٔ خودش؛ هیچ میانگین یا ترکیبی از دو پایگاه ساخته نمی‌شود.
 *   • «نسبت» = p50 ِ PGlite ÷ p50 ِ PostgreSQL، فقط برای دیدن؛ بودجهٔ API (§۹.۳) مالِ PostgreSQL است.
 *   • سنجه‌ای که فقط یک طرف دارد ⇒ «—»، نه صفر.
 * ⛔ پروندهٔ جابه‌جا (درایورِ نادرست) یا مقیاسِ ناهمسان ⇒ خطا و کدِ ۲؛ جدولِ گمراه‌کننده ساخته نمی‌شود.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const GROUPS = ['sync', 'catalog', 'orders'];

class InputError extends Error {}

function check(r, driver, label) {
  if (!r || !r.env || r.env.driver !== driver) {
    throw new InputError(`پروندهٔ ${label} خروجیِ bench/server.mjs روی ${label} نیست (درایور: ${r?.env?.driver || 'نامعلوم'})`);
  }
}

const r1 = (v) => Math.round(v * 10) / 10;

/** یک سنجه ⇒ { p50, p95, p99 [, totalMs, ops] } یا null */
function pick(v) {
  if (!v || typeof v !== 'object') return null;
  if (v.page && v.totalMs !== undefined) {
    //  همگام‌سازیِ کاملِ دستگاهِ تازه: کلِ زمان + صدکِ صفحه‌ها
    return { p50: v.page.p50, p95: v.page.p95, p99: v.page.p99, totalMs: v.totalMs, ops: v.ops };
  }
  if (v.p50 === undefined) return null;
  return { p50: v.p50, p95: v.p95, p99: v.p99, errors: v.errors || 0 };
}

export function compare(pg, lite) {
  check(pg, 'postgres', 'PostgreSQL');
  check(lite, 'pglite', 'PGlite');
  if (Number(pg.scale) !== Number(lite.scale)) {
    throw new InputError(`مقیاسِ دو اجرا یکی نیست (PostgreSQL ${pg.scale}، PGlite ${lite.scale}) — کنارِ هم گذاشتنشان بی‌معناست`);
  }
  const rows = [];
  for (const g of GROUPS) {
    const keys = [...new Set([...Object.keys(pg[g] || {}), ...Object.keys(lite[g] || {})])];
    for (const k of keys) {
      const a = pick(pg[g]?.[k]);
      const b = pick(lite[g]?.[k]);
      if (!a && !b) continue;
      const ratio = a && b && a.p50 > 0 ? r1(b.p50 / a.p50) : null;
      rows.push({ key: `${g}.${k}`, pg: a, pglite: b, ratio });
    }
  }
  const plans = [...new Set([...Object.keys(pg.plans || {}), ...Object.keys(lite.plans || {})])]
    .map((name) => ({ name, pg: pg.plans?.[name] || null, pglite: lite.plans?.[name] || null }));
  return {
    scale: pg.scale,
    pg: { at: pg.at || null, server: pg.env.server || '', node: pg.env.node || '', cores: pg.env.cores, rows: pg.dataset?.syncRows, rssMB: pg.memory?.rssMB, totalMs: pg.totalMs },
    pglite: { at: lite.at || null, node: lite.env.node || '', cores: lite.env.cores, rows: lite.dataset?.syncRows, rssMB: lite.memory?.rssMB, totalMs: lite.totalMs },
    rows,
    burst: { pg: pg.burst || null, pglite: lite.burst || null },
    plans,
  };
}

const trio = (s) => (s ? `${s.p50} / ${s.p95} / ${s.p99}` : '—');
function cell(s) {
  if (!s) return '—';
  let t = trio(s);
  if (s.totalMs !== undefined) t += ` (کل ${(s.totalMs / 1000).toFixed(1)}s، ${s.ops} op)`;
  if (s.errors) t += ` ❌ ${s.errors} خطا`;
  return t;
}
const burstCell = (b) => (b ? `${trio(b)} ms، ${b.rps} درخواست/ثانیه، خطا ${b.errors}` : '—');
const planCell = (p) => (p ? `${p.ms}ms${p.seqScan ? ' ⚠️ Seq Scan' : ''}` : '—');

export function markdown(c) {
  const pgName = `PostgreSQL${c.pg.server ? ` ${c.pg.server.split(' ')[0]}` : ''}`;
  const out = [];
  out.push(`### سرور — ${pgName} و PGlite، جدا (مقیاسِ ${c.scale})`);
  out.push('');
  out.push(`- ${pgName}: ${c.pg.rows ?? '?'} ردیف، RSS ${c.pg.rssMB ?? '?'}MB، کلِ اجرا ${c.pg.totalMs ? Math.round(c.pg.totalMs / 1000) : '?'}s${c.pg.at ? `، ${c.pg.at}` : ''}`);
  out.push(`- PGlite: ${c.pglite.rows ?? '?'} ردیف، RSS ${c.pglite.rssMB ?? '?'}MB، کلِ اجرا ${c.pglite.totalMs ? Math.round(c.pglite.totalMs / 1000) : '?'}s${c.pglite.at ? `، ${c.pglite.at}` : ''}`);
  out.push('- هر خانه p50 / p95 / p99 به میلی‌ثانیه و فقط از اجرای همان پایگاه است؛ «نسبت» = p50 ِ PGlite ÷ p50 ِ PostgreSQL.');
  out.push('');
  out.push(`| سنجه | ${pgName} | PGlite | نسبت |`);
  out.push('|---|---|---|---|');
  for (const r of c.rows) out.push(`| ${r.key} | ${cell(r.pg)} | ${cell(r.pglite)} | ${r.ratio == null ? '—' : `×${r.ratio}`} |`);
  if (c.burst.pg || c.burst.pglite) {
    const n = c.burst.pg?.devices ?? c.burst.pglite?.devices;
    out.push(`| burst (${n} دستگاه × ۱۰ فروش) | ${burstCell(c.burst.pg)} | ${burstCell(c.burst.pglite)} | — |`);
  }
  if (c.plans.length) {
    out.push('');
    out.push(`| نقشهٔ پرس‌وجو (EXPLAIN ANALYZE) | ${pgName} | PGlite |`);
    out.push('|---|---|---|');
    for (const p of c.plans) out.push(`| ${p.name} | ${planCell(p.pg)} | ${planCell(p.pglite)} |`);
  }
  return `${out.join('\n')}\n`;
}

function main(argv) {
  const arg = (k) => { const i = argv.indexOf(k); return i >= 0 && argv[i + 1] ? argv[i + 1] : ''; };
  const pgFile = arg('--pg');
  const liteFile = arg('--pglite');
  const mdFile = arg('--md');
  if (!pgFile || !liteFile) throw new InputError('کاربرد: node bench/db-compare.mjs --pg <postgres.json> --pglite <pglite.json> [--md out.md]');
  const read = (f) => {
    try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch (e) { throw new InputError(`${f}: ${e.message}`); }
  };
  const md = markdown(compare(read(pgFile), read(liteFile)));
  process.stdout.write(md);
  if (mdFile) fs.writeFileSync(mdFile, md);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    main(process.argv.slice(2));
  } catch (e) {
    if (!(e instanceof InputError)) throw e;
    console.error(`⛔ ${e.message}`);
    process.exit(2);
  }
}
