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
 *   release  — S64 (§۱۶): کارِ انتشارِ برنامه (RELEASE_JOBS) فقط از main (`github.ref == 'refs/heads/main'` در `if`) و
 *              فقط پس از آزمونِ سبز: خودش یا کاری در زنجیرهٔ `needs` گامِ آزمون (gradle …test، npm test، node --test) دارد
 *              بی `continue-on-error` و بی `if` ِ گام؛ و هیچ کارِ این زنجیره `always()`/`failure()`/`cancelled()` ندارد
 *              (وگرنه پس از آزمونِ سرخ هم می‌دود)؛ هیچ گامِ آزمونِ زنجیره continue-on-error ندارد؛ و هر کارِ آزمونِ همان فایل که
 *              روی main هم می‌دود در زنجیره است. پیش‌تر این فقط `needs`/`if` ِ همان فایل‌ها بود و هیچ آزمونی نگهش نمی‌داشت
 *              (و `disk-windows` ِ نصابِ کامپیوتر، آزمونِ ذخیرهٔ دفتر روی ویندوز، بیرون از زنجیره بود).
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
  //  OPS-01: برچسبِ نسخه‌دارِ انتشارِ مرحله‌ای — فقط همان بایت‌های آزمایشیِ آزموده، بی ساخت و بی کلیدِ امضا
  'release-stage.yml:publish': ['contents'],
  'codeql.yml:analyze': ['security-events'],
};

/** S64 — کارهای انتشارِ برنامه: «فایل:کار» ⇒ چه چیزی منتشر می‌کند. */
export const RELEASE_JOBS = {
  'tohid-native.yml:publish': 'برنامهٔ اندروید (کانالِ آزمایشی و پایدار)',
  'tohid-desktop.yml:publish': 'نصابِ کامپیوتر',
  'tohid-admin.yml:publish': 'برنامهٔ مدیر',
  'android-release.yml:release': 'پوستهٔ WebView (بازنشسته)',
  'release-stage.yml:publish': 'برچسبِ نسخه‌دارِ انتشارِ مرحله‌ای (کپیِ آزمایشیِ آزموده)',
};

/**
 * انتشارِ بی گامِ آزمون که آگاهانه مانده: «فایل:کار» ⇒ دلیل. «فقط از main» برایش همچنان سد است.
 * خالی از ۱۴۰۵/۰۸/۲۳ (S64، OA §۹د): پوستهٔ بازنشستهٔ `android-release.yml` هم کارِ «test» (`testReleaseUnitTest`) گرفت.
 */
export const RELEASE_UNTESTED = {};

/** گامِ آزمون: gradle/gradlew با کارِ test (‎test، jvmTest، testReleaseUnitTest…)، npm test، node --test */
const TEST_RUN = /\b(?:gradlew|gradle)\b[^\n]*?\s:?(?:[\w:]*:)?(?:test|jvmTest|test\w*UnitTest)\b|\bnpm (?:run )?test\b|\bnode --test\b/;
const BYPASS = /\b(?:always|failure|cancelled)\(\)/;

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
    out.push({ n: i + 1, line, key: k.key, value: k.value, raw: k.raw, path: p, indent: k.indent, dash: k.dash });
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

/** کارهای فایل: نام ⇒ { if (متنِ کامل، بلوکِ چندخطی هم)، needs، continue-on-error، گام‌ها [{ if, run, coe }] } */
function jobsOf(rows) {
  const jobs = new Map();
  const job = (name) => {
    if (!jobs.has(name)) jobs.set(name, { name, line: 0, if: '', needs: [], coe: false, steps: [] });
    return jobs.get(name);
  };
  for (const r of rows) {
    if (r.path[0] !== 'jobs') continue;
    if (!r.scalar && r.key && r.path.length === 1) { job(r.key).line = r.n; continue; }
    const j = r.path.length > 1 ? job(r.path[1]) : null;
    if (!j) continue;
    const rel = r.path.slice(2);
    if (r.scalar) {
      if (rel.length === 1 && rel[0] === 'if') j.if += ` ${r.line.trim()}`;
      else if (rel[0] === 'needs' && rel.length === 1) j.needs.push(r.line.trim().replace(/^-\s*/, ''));
      else if (rel[0] === 'steps' && j.steps.length && rel[2] === 'run') j.steps[j.steps.length - 1].run += `\n${r.line.trim()}`;
      else if (rel[0] === 'steps' && j.steps.length && rel[2] === 'if') j.steps[j.steps.length - 1].if += ` ${r.line.trim()}`;
      continue;
    }
    if (rel.length === 0) {
      if (r.key === 'if') j.if += ` ${r.value}`;
      else if (r.key === 'continue-on-error') j.coe = r.value !== 'false';
      else if (r.key === 'needs' && r.value) j.needs.push(...r.value.replace(/[[\]]/g, '').split(',').map((x) => x.trim()).filter(Boolean));
      continue;
    }
    if (rel.length === 1 && rel[0] === 'needs') { j.needs.push(r.line.trim().replace(/^-\s*/, '')); continue; }
    if (rel[0] === 'steps' && rel[1] === '-' && rel.length === 2) {
      if (r.dash) j.steps.push({ line: r.n, if: '', run: '', coe: false });
      const st = j.steps[j.steps.length - 1];
      if (!st) continue;
      if (r.key === 'run') st.run += r.value;
      else if (r.key === 'if') st.if += r.value;
      else if (r.key === 'continue-on-error') st.coe = r.value !== 'false';
    }
  }
  for (const j of jobs.values()) j.needs = j.needs.map((x) => x.replace(/^['"]|['"]$/g, '')).filter((x) => x && !x.startsWith('#'));
  return jobs;
}

/** S64: یافته‌های «فقط از main، فقط پس از آزمونِ سبز» برای کارِ انتشارِ [job] */
function releaseGate(name, rows, job, add) {
  const jobs = jobsOf(rows);
  const rel = jobs.get(job);
  const what = RELEASE_JOBS[`${name}:${job}`];
  if (!rel) { add('release', 1, `کارِ انتشارِ «${job}» (${what}) در فایل نیست — RELEASE_JOBS را به‌روز کنید`); return; }
  if (!/github\.ref\s*==\s*'refs\/heads\/main'/.test(rel.if)) {
    add('release', rel.line, `کارِ انتشارِ «${job}» (${what}) شرطِ «فقط از main» ندارد: if: github.ref == 'refs/heads/main'`);
  }
  //  زنجیرهٔ needs: هر کاری که پیش از انتشار باید سبز شود
  const chain = [];
  const seen = new Set();
  const queue = [job];
  while (queue.length) {
    const j = jobs.get(queue.shift());
    if (!j || seen.has(j.name)) continue;
    seen.add(j.name); chain.push(j);
    queue.push(...j.needs);
  }
  for (const j of chain) {
    if (BYPASS.test(j.if)) add('release', j.line, `کارِ «${j.name}» در زنجیرهٔ انتشارِ «${job}» با always()/failure()/cancelled() پس از آزمونِ سرخ هم می‌دود`);
    if (j.coe) add('release', j.line, `کارِ «${j.name}» در زنجیرهٔ انتشارِ «${job}» continue-on-error دارد`);
    for (const st of j.steps) {
      if (TEST_RUN.test(st.run) && st.coe) add('release', st.line, `گامِ آزمونِ کارِ «${j.name}» continue-on-error دارد ⇒ آزمونِ سرخ انتشارِ «${job}» را نگه نمی‌دارد`);
    }
  }
  //  هر کارِ آزمونِ همین فایل که روی main هم می‌دود باید در زنجیره باشد (کارِ فقط-PR نه)
  for (const j of jobs.values()) {
    if (seen.has(j.name) || j.coe || /github\.event_name\s*==\s*'pull_request'/.test(j.if)) continue;
    if (j.steps.some((st) => TEST_RUN.test(st.run) && !st.coe)) {
      add('release', j.line, `کارِ آزمونِ «${j.name}» در needs ِ انتشارِ «${job}» نیست ⇒ سرخ شدنش جلوی انتشار را نمی‌گیرد`);
    }
  }
  if (RELEASE_UNTESTED[`${name}:${job}`]) return;
  const gated = chain.some((j) => !j.coe && j.steps.some((st) => TEST_RUN.test(st.run) && !st.coe && !st.if.trim()));
  if (!gated) {
    add('release', rel.line, `کارِ انتشارِ «${job}» (${what}) به هیچ گامِ آزمونِ سدکننده (بی continue-on-error و بی if) در زنجیرهٔ needs بسته نیست`);
  }
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

  for (const key of Object.keys(RELEASE_JOBS)) {
    const [f, job] = key.split(':');
    if (f === name) releaseGate(name, rows, job, add);
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
