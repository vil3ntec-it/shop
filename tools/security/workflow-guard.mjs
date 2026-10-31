#!/usr/bin/env node
/**
 * نگهبانِ ورک‌فلوها (§۱۴ برنامهٔ امنیت، docs/security/supply-fa.md).
 *
 *   node tools/security/workflow-guard.mjs [پوشه=.github/workflows]
 *
 * هر فایلِ ‎.yml/.yaml‎ را بی کتابخانه (فقط تورفتگی) می‌خواند و این‌ها را سد می‌کند:
 *   pin      — هر `uses:` ِ بیرونی با SHAِ کاملِ ۴۰ نویسه‌ای و یادداشتِ نسخه (`# v4.2.1`)؛ برچسبِ
 *              جابه‌جاشدنی (`@v4`) یعنی کدِ تازهٔ دیگری با همان مجوزها اجرا می‌شود.
 *   perms    — `permissions:` در بالای فایل هست و هیچ `write`ی ندارد؛ `write` فقط در کارِ انتشاری
 *              که در WRITE_JOBS آمده (کارِ ساخت که کدِ PR و وابستگی‌های Gradle/npm را اجرا می‌کند
 *              هرگز توکنِ نوشتنی ندارد).
 *   trigger  — `pull_request_target` و `workflow_run` (کدِ PR با توکن و secretِ مخزن) ممنوع.
 *   inject   — `${{ … }}` ِ ورودیِ نامطمئن (نامِ شاخه، عنوان/متنِ PR، inputs، پیامِ commit …)
 *              داخلِ `run:`/`script:`؛ به‌جایش `env:` و `"$VAR"`.
 *
 * خروجی بی هیچ مقدارِ secret؛ کدِ خروج ۱ اگر سدی شکسته باشد.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** فایل‌هایی که به دستورِ صاحب محصول دست نمی‌خورند — فقط هشدار، نه سد. */
export const EXCLUDED = {
  'tohid-native-preview.yml': 'به دستورِ صاحب محصول دست نمی‌خورد (برنامهٔ پیش‌نمایش)؛ طرحش در docs/security/supply-fa.md',
};

/** کارهایی که حق دارند مجوزِ نوشتن بگیرند: «فایل:کار» ⇒ دامنه‌های مجاز. */
export const WRITE_JOBS = {
  'tohid-native.yml:publish': ['contents'],
  'tohid-desktop.yml:publish': ['contents'],
  'tohid-admin.yml:publish': ['contents'],
  'desktop-shots.yml:publish': ['contents'],
  'web-pages.yml:deploy': ['contents', 'pages', 'id-token'],
  'android-release.yml:release': ['contents'],
  'codeql.yml:analyze': ['security-events'],
};

const UNTRUSTED = [
  /github\.head_ref/,
  /github\.ref_name/,
  /github\.ref\b/,
  /github\.event\.pull_request\.(title|body|head\.ref|head\.label|head\.repo)/,
  /github\.event\.(issue|comment|review|review_comment|discussion|head_commit|commits|pages|workflow_run|release)\b/,
  /github\.event\.inputs\./,
  /\binputs\./,
];

const SHA_REF = /^[^@\s]+@[0-9a-f]{40}$/;

/** «کلید: مقدار» ِ یک خطِ YAML، بی توضیح؛ یا null. */
function keyOf(line) {
  const m = /^(\s*)(- +)?(["']?)([A-Za-z0-9_.\-]+)\3\s*:(?:\s+(.*))?$/.exec(line);
  if (!m) return null;
  const indent = m[1].length + (m[2] ? m[2].length : 0);
  const raw = m[5] ?? '';
  const value = raw.replace(/\s+#.*$/, '').trim();
  return { indent, key: m[4], value, raw, dash: !!m[2], dashIndent: m[1].length };
}

/**
 * خطوط را با مسیرِ کلیدها برمی‌گرداند و محتوای بلوکِ `|`/`>` را جدا (scalar: true) نشان می‌دهد.
 */
export function walk(text) {
  const out = [];
  const stack = []; // {indent, key}
  let blockIndent = -1; // تورفتگیِ کلیدی که بلوکِ چندخطی دارد
  let blockKey = null;
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const ind = line.length - line.trimStart().length;
    if (blockIndent >= 0) {
      if (line.trim() === '') continue;
      if (ind > blockIndent) {
        out.push({ n: i + 1, line, scalar: true, blockKey, path: stack.map((s) => s.key) });
        continue;
      }
      blockIndent = -1; blockKey = null;
    }
    if (line.trim() === '' || line.trimStart().startsWith('#')) continue;
    const k = keyOf(line);
    if (!k) { out.push({ n: i + 1, line, path: stack.map((s) => s.key) }); continue; }
    if (k.dash) {
      //  آیتمِ تازهٔ فهرست: آیتمِ قبلی و کلیدهایش بسته می‌شوند
      while (stack.length && stack[stack.length - 1].indent > k.dashIndent) stack.pop();
      stack.push({ indent: k.dashIndent + 0.5, key: '-' });
    }
    while (stack.length && stack[stack.length - 1].indent >= k.indent) stack.pop();
    const p = stack.map((s) => s.key);
    out.push({ n: i + 1, line, key: k.key, value: k.value, raw: k.raw, path: p, indent: k.indent });
    stack.push({ indent: k.indent, key: k.key });
    if (/^[|>][+-]?\d*$/.test(k.value)) { blockIndent = k.indent; blockKey = k.key; }
  }
  return out;
}

/** «on:» ِ فایل: فهرستِ رویدادها. */
function triggers(rows) {
  const ev = [];
  for (const r of rows) {
    if (r.scalar || !r.key) continue;
    if (r.path.length === 0 && (r.key === 'on' || r.key === 'true')) {
      if (r.value) ev.push(...r.value.replace(/[[\]]/g, '').split(',').map((s) => s.trim()).filter(Boolean));
    } else if (r.path.length === 1 && (r.path[0] === 'on' || r.path[0] === 'true')) {
      ev.push(r.key);
    }
  }
  return ev;
}

/** دامنه‌های نوشتنیِ یک بلوکِ permissions (درختی یا یک‌خطی). */
function writeScopes(rows, prefix) {
  const scopes = [];
  for (const r of rows) {
    if (r.scalar || !r.key) continue;
    const head = r.path.slice(0, prefix.length).join('/');
    if (r.path.length === prefix.length && head === prefix.join('/') && r.key === 'permissions') {
      if (r.value === 'write-all') scopes.push('write-all');
      const flow = /^\{(.*)\}$/.exec(r.value);
      if (flow) for (const part of flow[1].split(',')) {
        const [s, v] = part.split(':').map((x) => x && x.trim());
        if (v === 'write') scopes.push(s);
      }
    }
    if (r.path.length === prefix.length + 1 && head === prefix.join('/') && r.path[prefix.length] === 'permissions' && r.value === 'write') {
      scopes.push(r.key);
    }
  }
  return scopes;
}

function hasPermissions(rows, prefix) {
  return rows.some((r) => !r.scalar && r.key === 'permissions' && r.path.join('/') === prefix.join('/'));
}

/** یافته‌های یک فایل: [{rule, line, msg}] */
export function checkWorkflow(file, text) {
  const name = path.basename(file);
  const rows = walk(text);
  const found = [];
  const add = (rule, line, msg) => found.push({ file: name, rule, line, msg });

  for (const ev of triggers(rows)) {
    if (ev === 'pull_request_target' || ev === 'workflow_run') {
      add('trigger', 1, `رویدادِ ${ev}: کدِ PR با توکن/secretِ مخزن — ممنوع`);
    }
  }

  if (!hasPermissions(rows, [])) add('perms', 1, '`permissions:` در بالای فایل نیست (پیش‌فرضِ مخزن ممکن است نوشتنی باشد)');
  for (const s of writeScopes(rows, [])) add('perms', 1, `مجوزِ نوشتنیِ «${s}» در بالای فایل — فقط در کارِ انتشار`);

  const jobs = [...new Set(rows.filter((r) => !r.scalar && r.key && r.path.length === 1 && r.path[0] === 'jobs').map((r) => r.key))];
  for (const job of jobs) {
    const allowed = WRITE_JOBS[`${name}:${job}`] || [];
    for (const s of writeScopes(rows, ['jobs', job])) {
      if (!allowed.includes(s)) {
        const row = rows.find((r) => r.key === 'permissions' && r.path.join('/') === `jobs/${job}`);
        add('perms', row ? row.n : 1, `کارِ «${job}» مجوزِ نوشتنیِ «${s}» دارد ولی در WRITE_JOBS نیست`);
      }
    }
  }

  for (const r of rows) {
    if (r.scalar || r.key !== 'uses') continue;
    const ref = r.value.replace(/^['"]|['"]$/g, '');
    if (ref.startsWith('./') || /^docker:\/\/.+@sha256:[0-9a-f]{64}$/.test(ref)) continue;
    if (!SHA_REF.test(ref)) {
      add('pin', r.n, `«${ref}» با SHAِ کامل سنجاق نشده`);
    } else if (!/#\s*v?\d/.test(r.raw)) {
      add('pin', r.n, `«${ref}» یادداشتِ نسخه (# v…) ندارد`);
    }
  }

  for (const r of rows) {
    const inRun = (r.scalar && (r.blockKey === 'run' || r.blockKey === 'script'))
      || (!r.scalar && (r.key === 'run' || r.key === 'script') && !/^[|>]/.test(r.value));
    if (!inRun) continue;
    for (const m of r.line.matchAll(/\$\{\{([^}]*)\}\}/g)) {
      if (UNTRUSTED.some((re) => re.test(m[1]))) {
        add('inject', r.n, `«\${{${m[1]}}}» داخلِ run — از env و "$VAR" استفاده کنید`);
      }
    }
  }
  return found;
}

export function run(dir = '.github/workflows') {
  const files = fs.readdirSync(dir).filter((f) => /\.ya?ml$/.test(f)).sort();
  let errors = 0;
  for (const f of files) {
    const found = checkWorkflow(f, fs.readFileSync(path.join(dir, f), 'utf8'));
    for (const x of found) {
      if (EXCLUDED[f]) {
        console.log(`::warning file=${path.join(dir, f)},line=${x.line}::[${x.rule}] ${x.msg} (مستثنا: ${EXCLUDED[f]})`);
      } else {
        errors++;
        console.log(`::error file=${path.join(dir, f)},line=${x.line}::[${x.rule}] ${x.msg}`);
      }
    }
  }
  console.log(`${files.length} ورک‌فلو سنجیده شد؛ ${errors} خطا`);
  return errors;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  process.exit(run(process.argv[2]) ? 1 : 0);
}
