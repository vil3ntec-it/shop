#!/usr/bin/env node
/**
 * SEC-AI-02 — «فایلِ secret را نخوان» باید قاعدهٔ اجرایی باشد، نه فقط متنِ پرامپت.
 *
 *   node tools/ai-access/check-settings.mjs    ⇒ کد ۱ اگر `.claude/settings.json` قاعده‌های deny را نداشته باشد
 *                                               یا فایلِ secret‌مانندی در مخزن/workspace بی‌پوشش بماند.
 *
 * چه می‌سنجد (شرح و شاهد: docs/security/ai-access-fa.md):
 *  1. JSON درست است و هوکِ SessionStart ِ اسکیل‌ها سرِ جایش مانده.
 *  2. برای هر الگوی REQUIRED هم `Read(...)` و هم `Edit(...)` در `permissions.deny` هست.
 *     (`Write(path)`/`Glob(path)` هرگز تطبیق نمی‌خورند — مستنداتِ Claude Code — پس قاعدهٔ «بی‌اثر» خطاست.)
 *  3. تنها استثنا `.env.example` است و **پس از** `.env.*` می‌آید (ترتیب در gitignore معنا دارد).
 *  4. هر فایلِ secret‌مانندِ مخزن و workspace (ردیاب‌شده، ردیاب‌نشده و ignoreشده) با همین قاعده‌ها بسته است
 *     و نمونه‌های بی‌خطر (`.env.example`، fixtureِ ساختگی، کدِ پشتیبان) باز می‌مانند.
 *
 * ⚠️ این فقط پیکربندی را می‌سنجد. اجرای واقعیِ قاعده‌ها را `probe-enforcement.mjs` با خودِ Claude Code
 *    می‌آزماید (CI ندارد؛ دستی). هیچ محتوای فایلی خوانده یا چاپ نمی‌شود — فقط نامِ مسیر.
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const SETTINGS = path.join(ROOT, '.claude', 'settings.json');

/** الگوهایی که Read و Edit هر دو باید deny داشته باشند (نحوِ gitignore ِ Claude Code). */
export const REQUIRED = [
  '**/.env', '**/.env.*',
  '**/*.pem', '**/*.key', '**/*.jks', '**/*.keystore', '**/*.p12', '**/*.pfx', '**/*.p8',
  '**/id_rsa*', '**/id_ecdsa*', '**/id_ed25519*',
  '**/secrets/**', '**/secrets.json',
  '/server/data/**',
  '**/*.sql.gz', '**/*.sql.gz.enc', '**/*.pglite.tar.gz', '**/*.pglite.tar.gz.enc', '**/*.dump', '**/*.pgdump',
  '**/*.db', '**/*.sqlite', '**/*.sqlite3',
];
/** تنها استثنای مجاز، و الگویی که باید پیش از آن بیاید. */
export const CARVE_OUTS = { '!**/.env.example': '**/.env.*' };
/** خواندنِ کلِ محیط در ترنسکریپت (SEC-AI-05) — تلاشِ بهترین، نه مرز (شرح در سند). */
export const REQUIRED_BASH = ['Bash(env)', 'Bash(printenv)'];

/** نامِ فایلِ secret‌مانند در workspace — همان دسته‌های یافته در گامِ ۱ِ سند. */
export const SECRET_NAME = new RegExp([
  '(^|/)\\.env(\\.[^/]*)?$',
  '\\.(pem|key|jks|keystore|p12|pfx|p8|dump|pgdump|db|sqlite3?)$',
  '\\.sql\\.gz(\\.enc)?$', '\\.pglite\\.tar\\.gz(\\.enc)?$',
  '(^|/)id_(rsa|ecdsa|ed25519)[^/]*$',
  '(^|/)secrets(/|\\.json$)', '^server/data/',
].join('|'));

/** باید بسته باشند (مسیرِ نسبی به ریشهٔ مخزن). */
export const MUST_DENY = [
  '.env', 'server/.env', 'server/.env.production', 'deep/dir/.env.local',
  'keys/a.pem', 'b.key', 'android-native/app/tohid-release.jks', 'android/app/release.keystore', 'c.p12', 'd.pfx', 'e.p8',
  'id_rsa', 'x/id_ed25519.pub', 'secrets/a.txt', 'deep/secrets/b.json', 'panel/secrets.json',
  'server/data/backups/shop-20260101-000000.sql.gz', 'server/data/releases/x.json',
  'tmp/shop-1.sql.gz.enc', 'tmp/shop-1.pglite.tar.gz', 'tmp/shop-1.pglite.tar.gz.enc', 'prod.dump', 'prod.pgdump',
  'local.db', 'x.sqlite', 'x.sqlite3',
];
/** نباید بسته شوند — عامل‌ها لازمشان دارند. */
export const MUST_ALLOW = [
  'server/.env.example', 'deep/.env.example', 'server/src/lib/server-secret.js', 'server/test/fixtures/server-secret-golden.json',
  'server/scripts/backup.js', 'server/src/lib/backup-key.js', 'server/migrations/001_core.sql', 'server/test/backup.test.js',
  'desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/SecretBox.kt', 'android-native/KEYSTORE.md', 'docs/security/ai-access-fa.md',
  'server/src/routes/account-backups.js', '.claude/settings.json', 'server/data.js',
];

const INEFFECTIVE = /^(Write|Glob|NotebookEdit|MultiEdit)\(.+\)$/;

/** الگوی gitignore ِ نسبی به ریشهٔ پروژه ⇒ RegExp (فقط آنچه این تنظیمات به کار می‌برد). */
export function patternToRegExp(pattern) {
  let p = pattern;
  const anchored = p.startsWith('/');
  if (anchored) p = p.slice(1);
  if (!anchored && !p.includes('/')) p = `**/${p}`;
  let re = '';
  for (let i = 0; i < p.length; i++) {
    const c = p[i];
    if (c === '*' && p[i + 1] === '*') {
      if (p[i + 2] === '/') { re += '(?:.*/)?'; i += 2; } else { re += '.*'; i += 1; }
    } else if (c === '*') re += '[^/]*';
    else if (c === '?') re += '[^/]';
    else re += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
  }
  //  الگویی که پوشه را می‌گیرد همهٔ زیرِ آن را هم می‌گیرد (gitignore)
  return new RegExp(`^${re}(?:/.*)?$`);
}

/** قاعده‌های `Tool(pattern)` ِ یک ابزار از فهرستِ deny، به همان ترتیب؛ الگوهای `~/` و `//` بیرون از مخزن‌اند. */
export function rulesOf(deny, tool) {
  const out = [];
  for (const r of deny) {
    const m = new RegExp(`^${tool}\\((.+)\\)$`).exec(r);
    if (!m) continue;
    const pat = m[1];
    if (pat.startsWith('~/') || pat.startsWith('//') || pat.startsWith('!~') || pat.startsWith('!//')) continue;
    out.push(pat);
  }
  return out;
}

/** آیا مسیرِ نسبی با این قاعده‌ها بسته است؟ (آخرین تطبیق می‌بَرد؛ `!` استثنا می‌سازد) */
export function isDenied(rel, patterns) {
  let denied = false;
  for (const p of patterns) {
    const neg = p.startsWith('!');
    if (patternToRegExp(neg ? p.slice(1) : p).test(rel)) denied = !neg;
  }
  return denied;
}

/** فایل‌های مخزن و workspace: ردیاب‌شده، ردیاب‌نشده و ignoreشده (پوشهٔ ignoreشده یک ردیف). */
export function workspaceFiles(root = ROOT) {
  const run = (args) => execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', maxBuffer: 64 << 20 })
    .split('\n').filter(Boolean);
  const tracked = run(['ls-files', '-co', '--exclude-standard']);
  let ignored = [];
  try { ignored = run(['ls-files', '-oi', '--exclude-standard', '--directory']); } catch { /* بی git ⇒ فقط ردیاب‌شده */ }
  return [...new Set([...tracked, ...ignored.filter((f) => !f.endsWith('/') || SECRET_NAME.test(f))])];
}

/** همهٔ مشکل‌ها (رشته)؛ آرایهٔ خالی یعنی سالم. هیچ محتوایی خوانده نمی‌شود — فقط نامِ مسیرها. */
export function check(settingsText, files = []) {
  const problems = [];
  let s;
  try { s = JSON.parse(settingsText); } catch (e) { return [`.claude/settings.json JSON ِ درست نیست: ${e.message}`]; }

  const start = s?.hooks?.SessionStart;
  const hookText = JSON.stringify(start || '');
  if (!Array.isArray(start) || !hookText.includes('smart-coding-core')) {
    problems.push('هوکِ SessionStart ِ یادآوریِ اسکیل‌ها (بندِ ۴ِ CLAUDE.md) نیست');
  }

  const deny = s?.permissions?.deny;
  if (!Array.isArray(deny)) return [...problems, 'permissions.deny نیست — محدودیت فقط متنِ پرامپت است (SEC-AI-02)'];

  for (const list of ['allow', 'ask', 'deny']) {
    for (const r of s.permissions[list] || []) {
      if (INEFFECTIVE.test(r)) problems.push(`«${r}» در ${list} هرگز تطبیق نمی‌خورد؛ به‌جایش Edit(...) یا Read(...)`);
    }
  }
  for (const tool of ['Read', 'Edit']) {
    for (const p of REQUIRED) {
      if (!deny.includes(`${tool}(${p})`)) problems.push(`قاعدهٔ deny ِ ${tool}(${p}) نیست`);
    }
    const pats = rulesOf(deny, tool);
    for (const p of pats.filter((x) => x.startsWith('!'))) {
      const before = CARVE_OUTS[p];
      if (!before) { problems.push(`استثنای ناشناخته در deny: ${tool}(${p})`); continue; }
      if (pats.indexOf(p) < pats.indexOf(before)) problems.push(`${tool}(${p}) باید پس از ${tool}(${before}) بیاید`);
    }
    for (const f of MUST_DENY) if (!isDenied(f, pats)) problems.push(`${tool}: «${f}» باز است`);
    for (const f of MUST_ALLOW) if (isDenied(f, pats)) problems.push(`${tool}: «${f}» بی‌جا بسته شد`);
    for (const f of files) {
      //  پوشهٔ ignoreشده (`x/`) ⇒ فایلی درونش سنجیده می‌شود
      if (SECRET_NAME.test(f) && !f.endsWith('.env.example') && !isDenied(f.endsWith('/') ? `${f}_` : f, pats)) {
        problems.push(`${tool}: فایلِ secret‌مانندِ workspace بی‌پوشش است: ${f}`);
      }
    }
  }
  for (const r of REQUIRED_BASH) if (!deny.includes(r)) problems.push(`قاعدهٔ deny ِ ${r} نیست`);
  return problems;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const files = workspaceFiles();
  const problems = check(fs.readFileSync(SETTINGS, 'utf8'), files);
  const covered = files.filter((f) => SECRET_NAME.test(f) && !f.endsWith('.env.example'));
  if (problems.length) {
    console.error(`✖ دسترسیِ عامل‌ها (SEC-AI-02): ${problems.length} مشکل`);
    for (const p of problems) console.error(`  - ${p}`);
    process.exit(1);
  }
  console.log(`✓ قاعده‌های deny ِ .claude/settings.json سالم‌اند؛ ${covered.length} فایلِ secret‌مانندِ workspace پوشش دارد: ${covered.join('، ') || '—'}`);
}
