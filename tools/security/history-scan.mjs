#!/usr/bin/env node
/**
 * اسکنِ «راز» در کلِ تاریخچهٔ Git (همهٔ شاخه‌ها و برچسب‌ها) — SEC-ART-05. روی هر مخزنی (‎--repo‎) کار می‌کند.
 * مخزنِ عمومی ⇒ هر چه یک بار در تاریخچه آمده لورفته است؛ پاک کردن از HEAD کافی نیست.
 *
 *   node tools/security/history-scan.mjs                 فایل‌های ردیابی‌شدهٔ HEAD
 *   node tools/security/history-scan.mjs --history       هر blobی که از هر شاخه/برچسب رسیدنی است
 *   node tools/security/history-scan.mjs --repo <dir>    مخزنِ دیگر (پیش‌فرض: همین مخزن)
 *   node tools/security/history-scan.mjs --no-allow      بی فهرستِ مجاز (همه‌چیز را نشان بده)
 *
 * ⛔ خروجی فقط «قاعده، مسیر، خط، کامیت» است — هرگز مقدار. خروجِ ۱ یعنی یافته‌ای بیرون از
 * `history-scan-allow.json` هست. هر ردیفِ آن فایل دلیل دارد (رخدادِ ثبت‌شده، داده‌ی آزمون، …).
 */
import { execFileSync, spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scanContent, scanName, compileAllow, isAllowed } from './artifact-rules.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const MAX_BLOB = 32 << 20;

function git(repo, args, opts = {}) {
  return execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8', maxBuffer: 1 << 30, stdio: ['ignore', 'pipe', 'ignore'], ...opts });
}

/** همهٔ blobهای رسیدنی با نخستین مسیرِ هر کدام: Map(sha → path) */
function reachableBlobs(repo) {
  const out = git(repo, ['rev-list', '--all', '--objects']);
  const paths = new Map();
  for (const line of out.split('\n')) {
    const sp = line.indexOf(' ');
    if (sp < 0) continue;
    const sha = line.slice(0, sp);
    const p = line.slice(sp + 1);
    if (p && !paths.has(sha)) paths.set(sha, p);
  }
  return paths;
}

/** محتوای blobها با یک `git cat-file --batch` (بی پروسهٔ جدا برای هر فایل) */
function readBlobs(repo, shas, onBlob) {
  return new Promise((resolve, reject) => {
    const proc = spawn('git', ['-C', repo, 'cat-file', '--batch'], { stdio: ['pipe', 'pipe', 'ignore'] });
    let buf = Buffer.alloc(0);
    let want = null; // { sha, type, size }
    const queue = [...shas];
    let pending = queue.length;
    proc.stdout.on('data', (chunk) => {
      buf = buf.length ? Buffer.concat([buf, chunk]) : chunk;
      for (;;) {
        if (!want) {
          const nl = buf.indexOf(10);
          if (nl < 0) return;
          const [sha, type, size] = buf.subarray(0, nl).toString('latin1').split(' ');
          buf = buf.subarray(nl + 1);
          if (type === 'missing') { pending--; if (!pending) proc.stdin.end(); continue; }
          want = { sha, type, size: Number(size) };
        }
        if (buf.length < want.size + 1) return;
        const body = buf.subarray(0, want.size);
        buf = buf.subarray(want.size + 1);
        if (want.type === 'blob') onBlob(want.sha, body);
        want = null;
        pending--;
        if (!pending) proc.stdin.end();
      }
    });
    proc.on('error', reject);
    proc.on('close', () => resolve());
    if (!queue.length) { proc.stdin.end(); return; }
    proc.stdin.write(queue.join('\n') + '\n');
  });
}

/** کامیت‌هایی که این blob را آوردند (فقط برای یافته‌ها؛ سنگین است) */
function commitsOf(repo, sha) {
  try {
    return git(repo, ['log', '--all', '--format=%h', `--find-object=${sha}`]).split('\n').filter(Boolean);
  } catch { return []; }
}

export async function scanRepo({ repo, history = false, allow = [] }) {
  const findings = [];
  const headBlobs = new Map(); // sha → [path]
  for (const line of git(repo, ['ls-tree', '-r', '-z', 'HEAD']).split('\0')) {
    if (!line) continue;
    const tab = line.indexOf('\t');
    const [, type, sha] = line.slice(0, tab).split(' ');
    if (type !== 'blob') continue;
    const p = line.slice(tab + 1);
    if (!headBlobs.has(sha)) headBlobs.set(sha, []);
    headBlobs.get(sha).push(p);
  }
  const targets = history ? reachableBlobs(repo) : new Map([...headBlobs].map(([s, ps]) => [s, ps[0]]));
  //  نام‌ها: هر مسیری که دیده شده، نه فقط نخستین
  const namePaths = history ? null : [...headBlobs.values()].flat();
  if (namePaths) {
    for (const p of namePaths) for (const f of scanName(p)) findings.push({ ...f, path: p, inHead: true });
  } else {
    const seenPath = new Set();
    for (const line of git(repo, ['log', '--all', '--format=', '--name-only', '--diff-filter=AR']).split('\n')) {
      if (!line || seenPath.has(line)) continue;
      seenPath.add(line);
      for (const f of scanName(line)) findings.push({ ...f, path: line, inHead: [...headBlobs.values()].some((ps) => ps.includes(line)) });
    }
  }
  const sizes = new Map();
  for (const line of git(repo, ['cat-file', '--batch-check', '--batch-all-objects']).split('\n')) {
    const [sha, type, size] = line.split(' ');
    if (type === 'blob') sizes.set(sha, Number(size));
  }
  const want = [...targets.keys()].filter((s) => (sizes.get(s) ?? 0) <= MAX_BLOB);
  const skipped = targets.size - want.length;
  await readBlobs(repo, want, (sha, body) => {
    for (const f of scanContent(body)) {
      const inHead = headBlobs.has(sha);
      const p = inHead ? headBlobs.get(sha)[0] : targets.get(sha);
      findings.push({ ...f, path: p, blob: sha, inHead });
    }
  });
  const compiled = compileAllow(allow);
  const open = [];
  const allowed = [];
  for (const f of findings) (isAllowed(compiled, f.rule, f.path) ? allowed : open).push(f);
  return { findings: open, allowed, scanned: want.length, skipped };
}

function fmt(repo, f, withCommits) {
  const where = f.line ? `${f.path}:${f.line}` : f.path;
  const state = f.inHead ? 'HEAD' : 'history-only';
  const commits = withCommits && f.blob ? commitsOf(repo, f.blob).slice(-1).join(',') : '';
  return `${f.rule}\t${state}\t${where}${commits ? `\tfirst-commit=${commits}` : ''}`;
}

async function main(argv) {
  const args = new Set(argv);
  const opt = (k) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : null; };
  const repo = path.resolve(opt('--repo') || path.join(HERE, '..', '..'));
  const allowFile = opt('--allow') || path.join(HERE, 'history-scan-allow.json');
  const allow = args.has('--no-allow') ? [] : JSON.parse(fs.readFileSync(allowFile, 'utf8')).allow;
  const history = args.has('--history');
  const { findings, allowed, scanned, skipped } = await scanRepo({ repo, history, allow });
  const lines = findings.map((f) => fmt(repo, f, history));
  for (const l of [...new Set(lines)].sort()) console.log(l);
  console.error(`history-scan: ${history ? 'history' : 'HEAD'}، ${scanned} blob سنجیده شد` +
    `${skipped ? `، ${skipped} blobِ بزرگ‌تر از ${MAX_BLOB >> 20}MB فقط با نام` : ''}؛ ` +
    `${findings.length} یافتهٔ باز، ${allowed.length} یافتهٔ مجاز (با دلیل در فهرستِ مجاز).`);
  if (process.env.GITHUB_STEP_SUMMARY && !history) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      `### رازها در مخزن (SEC-ART-05)\n\n${findings.length ? `❌ ${findings.length} یافتهٔ باز:\n\n\`\`\`\n${[...new Set(lines)].join('\n')}\n\`\`\`\n` : '✅ یافتهٔ بازی نیست.'}\n` +
      `${allowed.length} یافتهٔ ثبت‌شده در \`tools/security/history-scan-allow.json\` (هر کدام با دلیل).\n`);
  }
  return findings.length ? 1 : 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).then((code) => process.exit(code), (err) => { console.error(`history-scan: ${err.message}`); process.exit(2); });
}
