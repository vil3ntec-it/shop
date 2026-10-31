#!/usr/bin/env node
/**
 * اسکنِ secret (§۱۴ برنامهٔ امنیت، docs/security/supply-fa.md) — بی وابستگی، بی شبکه.
 *
 *   node tools/security/secret-scan.mjs --diff <commitِ پایه>   # فقط خط‌های افزوده‌شده (PR)
 *   node tools/security/secret-scan.mjs --all                   # همهٔ فایل‌های git ls-files
 *
 * ⛔ هرگز مقدارِ یافته چاپ نمی‌شود: فقط «مسیر:خط، قاعده، اثرِ انگشتِ ۸ نویسه‌ایِ sha256». اثرِ انگشت
 * برای استثنا کردنِ یک یافتهٔ مشخص در `secret-scan-allow.json` است، بی نوشتنِ خودِ مقدار.
 * «block» ها سدند (کدِ خروجِ ۱)؛ «warn» ها فقط هشدار (الگوی عمومی‌تر، احتمالِ مثبتِ کاذب بیشتر).
 */
import { execFileSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** الگوهای با اطمینانِ بالا — سد. هر کدام فقط شکلِ مشخصِ یک سرویس. */
export const RULES = [
  { id: 'private-key', level: 'block', re: /-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED |PGP )?PRIVATE KEY(?: BLOCK)?-----/ },
  { id: 'aws-access-key', level: 'block', re: /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/ },
  { id: 'github-token', level: 'block', re: /\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{60,})\b/ },
  { id: 'slack-token', level: 'block', re: /\bxox[abprs]-[A-Za-z0-9-]{10,}\b/ },
  { id: 'google-api-key', level: 'block', re: /\bAIza[0-9A-Za-z_-]{35}\b/ },
  { id: 'stripe-live-key', level: 'block', re: /\b[rs]k_live_[0-9A-Za-z]{20,}\b/ },
  { id: 'anthropic-key', level: 'block', re: /\bsk-ant-(?:api|admin)\d{2}-[A-Za-z0-9_-]{40,}/ },
  { id: 'openai-key', level: 'block', re: /\bsk-(?:proj|svcacct|admin)-[A-Za-z0-9_-]{40,}/ },
  { id: 'npm-token', level: 'block', re: /\bnpm_[A-Za-z0-9]{36}\b/ },
  { id: 'telegram-bot-token', level: 'block', re: /\b\d{8,10}:AA[0-9A-Za-z_-]{33}\b/ },
  { id: 'sendgrid-key', level: 'block', re: /\bSG\.[A-Za-z0-9_-]{22}\.[A-Za-z0-9_-]{43}\b/ },
  { id: 'url-credentials', level: 'warn', re: /\b[a-z][a-z0-9+.-]*:\/\/[^\s:@/'"`]{1,64}:[^\s@/'"`$]{8,}@[A-Za-z0-9.-]+/ },
  { id: 'generic-assignment', level: 'warn', re: /\b(?:password|passwd|secret|api_?key|auth_?token|access_?token|private_?key)\b["']?\s*[:=]\s*["'][^"'\s$]{16,}["']/i },
];

/** فایل‌هایی که خودشان راز یا کلیدند — به نامشان سد می‌شوند (حتی دودویی). */
export const FILE_RULES = [
  { id: 'keystore-file', re: /\.(?:jks|keystore|p12|pfx|bks)$/i },
  { id: 'key-file', re: /(?:^|\/)(?:id_(?:rsa|dsa|ecdsa|ed25519)|[^/]+\.(?:pem|key|ppk))$/i },
  { id: 'env-file', re: /(?:^|\/)\.env(?:\.[^/]*)?$/i, unless: /\.(?:example|sample|template)$/i },
];

const MAX_BYTES = 5 * 1024 * 1024;
const fp = (s) => crypto.createHash('sha256').update(s).digest('hex').slice(0, 8);

/** یافته‌های یک خط: [{rule, level, fp}] — بی مقدار. */
export function scanLine(text) {
  const out = [];
  for (const r of RULES) {
    const m = r.re.exec(text);
    if (m) out.push({ rule: r.id, level: r.level, fp: fp(m[0]) });
  }
  return out;
}

export function scanPath(file) {
  for (const r of FILE_RULES) {
    if (r.re.test(file) && !(r.unless && r.unless.test(file))) return { rule: r.id, level: 'block', fp: fp(file) };
  }
  return null;
}

/**
 * آیا یافته در فهرستِ استثنا هست؟ مسیرِ دقیق یا پیشوندِ «/»دار، همان قاعده، اثرِ انگشتِ اختیاری؛
 * `until` (YYYY-MM-DD) بگذرد ⇒ استثنا دیگر پذیرفته نیست و یافته دوباره سد می‌شود.
 */
export function allowed(allow, f, today = new Date().toISOString().slice(0, 10)) {
  return allow.some((a) => (a.path === f.file || (a.path.endsWith('/') && f.file.startsWith(a.path)))
    && a.rule === f.rule && (!a.fp || a.fp === f.fp) && (!a.until || today <= a.until));
}

/** خط‌های افزوده از `git diff -U0`: [{file, line, text}] */
export function addedLines(diff) {
  const out = [];
  let file = null; let n = 0;
  for (const l of diff.split('\n')) {
    if (l.startsWith('+++ ')) { file = l.startsWith('+++ b/') ? l.slice(6) : null; continue; }
    const h = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(l);
    if (h) { n = Number(h[1]); continue; }
    if (file && l.startsWith('+')) { out.push({ file, line: n, text: l.slice(1) }); n++; }
  }
  return out;
}

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 512 * 1024 * 1024 });
}

export function scan(mode, base) {
  const found = [];
  const push = (file, line, hit) => found.push({ file, line, ...hit });
  if (mode === 'diff') {
    const names = git(['diff', '--name-only', '--diff-filter=AMR', base, 'HEAD']).split('\n').filter(Boolean);
    for (const f of names) { const h = scanPath(f); if (h) push(f, 1, h); }
    const binary = new Set(git(['diff', '--numstat', '--diff-filter=AMR', base, 'HEAD']).split('\n')
      .filter((l) => l.startsWith('-\t-\t')).map((l) => l.split('\t')[2]));
    for (const a of addedLines(git(['diff', '-U0', '--no-color', '--no-ext-diff', '--diff-filter=AMR', base, 'HEAD']))) {
      if (binary.has(a.file)) continue;
      for (const h of scanLine(a.text)) push(a.file, a.line, h);
    }
  } else {
    for (const f of git(['ls-files', '-z']).split('\0').filter(Boolean)) {
      const h = scanPath(f); if (h) push(f, 1, h);
      //  یک بار باز، سنجش و خواندن روی همان fd (نه stat روی نام و سپس خواندنِ نام: فایل میانِ این دو عوض نمی‌شود)؛
      //  O_NONBLOCK تا FIFO مثلِ پیش (stat ⇐ «فایل نیست») رد شود، نه این‌که اسکن را نگه دارد.
      let buf; let fd;
      try {
        fd = fs.openSync(f, fs.constants.O_RDONLY | (fs.constants.O_NONBLOCK ?? 0));
        const st = fs.fstatSync(fd);
        if (!st.isFile() || st.size > MAX_BYTES) continue;
        buf = fs.readFileSync(fd);
      } catch { continue; } finally { if (fd !== undefined) fs.closeSync(fd); }
      if (buf.subarray(0, 8000).includes(0)) continue;
      const lines = buf.toString('utf8').split('\n');
      for (let i = 0; i < lines.length; i++) for (const x of scanLine(lines[i])) push(f, i + 1, x);
    }
  }
  return found;
}

function main(argv) {
  const mode = argv.includes('--all') ? 'all' : 'diff';
  const base = argv[argv.indexOf('--diff') + 1];
  if (mode === 'diff' && (!base || base.startsWith('--'))) {
    console.error('کاربرد: --diff <commitِ پایه> یا --all'); return 2;
  }
  const allowFile = path.join(HERE, 'secret-scan-allow.json');
  const allow = JSON.parse(fs.readFileSync(allowFile, 'utf8')).allow;
  let blocking = 0; let warnings = 0; let known = 0;
  for (const f of scan(mode, base)) {
    const where = `file=${f.file},line=${f.line}`;
    if (allowed(allow, f)) { known++; continue; }
    if (f.level === 'block') { blocking++; console.log(`::error ${where}::[${f.rule}] secretِ احتمالی (fp=${f.fp}) — مقدار چاپ نمی‌شود. اگر واقعی است: برداشتن کافی نیست، چرخاندن لازم است.`); }
    else { warnings++; console.log(`::warning ${where}::[${f.rule}] شبیهِ secret (fp=${f.fp}) — بررسی کنید`); }
  }
  console.log(`اسکنِ secret (${mode}): ${blocking} سد، ${warnings} هشدار، ${known} استثنای شناخته`);
  return blocking ? 1 : 0;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  process.exit(main(process.argv.slice(2)));
}
