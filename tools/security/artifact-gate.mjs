#!/usr/bin/env node
/**
 * دروازهٔ محتوای خروجی‌ها — SEC-ART-01..05 (برنامهٔ امنیت §۲.۲، §۵).
 *
 * خودِ محتوای APK، پوشهٔ برنامهٔ کامپیوتر (همان که در نصاب می‌رود)، برنامهٔ مدیر و سایتِ وب را باز می‌کند —
 * نه فقط نامِ فایل — و اگر چیزی از این‌ها در آن باشد کار را قرمز می‌کند:
 *   کلیدِ خصوصی (PEM/PKCS8/انبارِ کلید)، `.env`، پشتیبان/خروجیِ دیتابیس، `.git` و فایل‌های درونیِ مخزن،
 *   سورسِ بک‌اند (server/src، مهاجرت‌ها، Dockerfile…)، source map، الگوهای شناخته‌شدهٔ راز.
 *
 *   node tools/security/artifact-gate.mjs [--profile apk|desktop|web] [--allow <json>]
 *        [--license-pin <base64 SPKI>] <پوشه یا فایل>...
 *
 * فایلِ zip‌خانواده (‎.apk .aab .jar .aar .zip .war‎)، ‎.gz‎ و ‎.tar/.tgz‎ — و هر بایگانیِ درونِ آن‌ها تا ۴ لایه — باز
 * می‌شود؛ بایگانیِ ناخوانا (‎.7z .rar .xz .msi .dmg .deb …‎) درونِ خروجی خودش یافته است: چیزی که نمی‌شود دید، پاس
 * نمی‌شود (‎.deb‎ را پیش از دروازه با ‎dpkg-deb -x‎ باز کنید).
 *
 * ⛔ خروجی فقط «قاعده، مسیر، خط» — هرگز مقدار. خروج: ۰ پاک، ۱ یافتهٔ سد، ۲ کاربردِ نادرست/هیچ فایلی.
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { scanContent, scanName, isText, compileAllow, isAllowed } from './artifact-rules.mjs';

const ZIPLIKE = /\.(?:apk|aab|jar|aar|zip|war|ear|apks|xapk)$/i;
const TARLIKE = /\.(?:tar|tgz|tar\.gz)$/i;
const GZIP = /\.gz$/i;
const OPAQUE = /\.(?:7z|rar|bz2|xz|zst|lz4|lzma|cab|msi|msix|appx|dmg|pkg|deb|rpm|iso|img|asar)$/i;
const MAX_DEPTH = 4;
const MAX_ENTRY = 512 << 20;
const MAX_TOTAL = 6 * 1024 ** 3;

/** یافته‌هایی که فقط هشدارند: کلیدِ API ِ گوگل برای کلاینت ساخته شده و با بسته/دامنه محدود می‌شود. */
const WARN_ONLY = new Set(['google-api-key', 'license-key-not-pinned']);

/** سورسِ بک‌اند و فایل‌های درونیِ مخزن — از روی مسیر */
const PATH_RULES = [
  { id: 'backend-source', re: /(?:^|\/)server\/(?:src|migrations|scripts|test)\// },
  { id: 'backend-source', re: /(?:^|\/)migrations\/\d{3,}_[^/]+\.sql$/i },
  { id: 'backend-source', re: /(?:^|\/)(?:Dockerfile|docker-compose\.ya?ml|Caddyfile|\.dockerignore)$/ },
  { id: 'git-metadata', re: /(?:^|\/)\.git(?:\/|$)|(?:^|\/)\.git(?:ignore|attributes|modules|-credentials)$/ },
  { id: 'repo-internal', re: /(?:^|\/)(?:\.github|\.claude|\.idea|\.gradle|\.vscode)\// },
  { id: 'repo-internal', re: /(?:^|\/)(?:CLAUDE\.md|local\.properties|keystore\.properties|signing\.properties)$/i },
  { id: 'source-map', re: /\.(?:js|mjs|cjs|css)\.map$|(?:^|\/)[^/]+\.map$/i, when: (p) => !/\.(?:class|dex)$/i.test(p) },
];
/** فقط برای وب: آزمون، بردار و سندِ درونی جای سایتِ عمومی نیستند */
const WEB_RULES = [
  { id: 'test-file', re: /(?:^|\/)(?:test|tests|__tests__|test-vectors|e2e)\/|\.(?:test|spec|e2e)\.[cm]?js$/i },
  { id: 'repo-internal', re: /(?:^|\/)docs\/|(?:^|\/)(?:RULES|HANDOFF|SECURITY-[A-Z-]+)(?:-fa)?\.md$/ },
  { id: 'backend-source', re: /(?:^|\/)(?:android-native|admin-android|android|desktop|core|tools|bench|server)\// },
];
/** سورسِ بک‌اند — از روی محتوای متنیِ JS */
const BACKEND_CONTENT = /require\(\s*['"](?:express|pg|@electric-sql\/pglite|nodemailer)['"]\s*\)|process\.env\.(?:DATABASE_URL|API_SECRET|OTP_SECRET|JWT_SECRET|LICENSE_PRIVATE_KEY|BACKUP_PASSPHRASE)\b/;
const SOURCE_MAP_REF = /[#@]\s*sourceMappingURL\s*=\s*(?!data:)[^\s'"*]+\.map\b/;

// ---------------------------------------------------------------- zip

function findEocd(buf) {
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) return i;
  }
  return -1;
}

/** ورودی‌های یک zip: ‎[{ name, read() }]‎. zipِ خراب ⇒ خطا (و خطا یعنی یافته، نه پاس). */
export function zipEntries(buf) {
  const eocd = findEocd(buf);
  if (eocd < 0) throw new Error('zip: no end-of-central-directory');
  let count = buf.readUInt16LE(eocd + 10);
  let cdOff = buf.readUInt32LE(eocd + 16);
  if (cdOff === 0xffffffff || count === 0xffff) {
    const loc = eocd - 20;
    if (loc < 0 || buf.readUInt32LE(loc) !== 0x07064b50) throw new Error('zip64: locator missing');
    const z64 = Number(buf.readBigUInt64LE(loc + 8));
    if (buf.readUInt32LE(z64) !== 0x06064b50) throw new Error('zip64: record missing');
    count = Number(buf.readBigUInt64LE(z64 + 32));
    cdOff = Number(buf.readBigUInt64LE(z64 + 48));
  }
  const out = [];
  let p = cdOff;
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('zip: bad central directory');
    const method = buf.readUInt16LE(p + 10);
    let csize = buf.readUInt32LE(p + 20);
    let usize = buf.readUInt32LE(p + 24);
    const nlen = buf.readUInt16LE(p + 28);
    const xlen = buf.readUInt16LE(p + 30);
    const clen = buf.readUInt16LE(p + 32);
    let lho = buf.readUInt32LE(p + 42);
    const name = buf.subarray(p + 46, p + 46 + nlen).toString('utf8');
    //  zip64: اندازه‌ها و جای سرآیند در extra
    let x = p + 46 + nlen;
    const xend = x + xlen;
    while (x + 4 <= xend) {
      const id = buf.readUInt16LE(x); const sz = buf.readUInt16LE(x + 2);
      if (id === 0x0001) {
        let q = x + 4;
        if (usize === 0xffffffff) { usize = Number(buf.readBigUInt64LE(q)); q += 8; }
        if (csize === 0xffffffff) { csize = Number(buf.readBigUInt64LE(q)); q += 8; }
        if (lho === 0xffffffff) { lho = Number(buf.readBigUInt64LE(q)); }
      }
      x += 4 + sz;
    }
    p = xend + clen;
    if (name.endsWith('/')) continue;
    out.push({
      name, method, usize,
      read() {
        if (buf.readUInt32LE(lho) !== 0x04034b50) throw new Error(`zip: bad local header for ${name}`);
        const start = lho + 30 + buf.readUInt16LE(lho + 26) + buf.readUInt16LE(lho + 28);
        const raw = buf.subarray(start, start + csize);
        if (usize > MAX_ENTRY) throw new Error(`zip: entry too large ${name}`);
        if (method === 0) return raw;
        if (method === 8) return zlib.inflateRawSync(raw, { maxOutputLength: Math.max(usize, 1) + 1024 });
        throw new Error(`zip: unsupported method ${method} for ${name}`);
      },
    });
  }
  return out;
}

/** ورودی‌های یک tar (ustar/gnu؛ نامِ بلندِ ‎././@LongLink‎ هم) */
export function tarEntries(buf) {
  const out = [];
  let p = 0;
  let longName = null;
  while (p + 512 <= buf.length) {
    const h = buf.subarray(p, p + 512);
    if (h.every((b) => b === 0)) break;
    const str = (o, n) => h.subarray(o, o + n).toString('utf8').replace(/\0.*$/s, '');
    let name = str(0, 100);
    const prefix = str(345, 155);
    if (prefix && h.subarray(257, 262).toString('latin1') === 'ustar') name = `${prefix}/${name}`;
    const size = parseInt(str(124, 12).trim() || '0', 8);
    if (!Number.isFinite(size) || size < 0) throw new Error('tar: bad size');
    const type = String.fromCharCode(h[156] || 48);
    const body = buf.subarray(p + 512, p + 512 + size);
    p += 512 + Math.ceil(size / 512) * 512;
    if (type === 'L') { longName = body.toString('utf8').replace(/\0.*$/s, ''); continue; }
    if (longName) { name = longName; longName = null; }
    if (type === '0' || type === '\0' || type === '7') out.push({ name, read: () => body });
    else if (type === '1' || type === '2') out.push({ name, link: true, read: () => Buffer.alloc(0) });
  }
  return out;
}

// ---------------------------------------------------------------- scan

/** نمای UTF-16LE (رشته‌های XMLِ دودویی و ‎resources.arsc‎ ِ اندروید) */
function utf16View(buf) {
  const a = buf.toString('utf16le');
  const b = buf.length > 1 ? buf.subarray(1).toString('utf16le') : '';
  return Buffer.from(`${a}\n${b}`, 'latin1');
}

export function createGate({ profile = 'any', allow = [], licensePin = null } = {}) {
  const compiled = compileAllow(allow);
  const findings = [];
  const stats = { files: 0, archives: 0, bytes: 0 };
  let pinSeen = false;
  const pinBytes = licensePin ? Buffer.from(licensePin, 'latin1') : null;

  const add = (rule, where, line) => {
    if (isAllowed(compiled, rule, where)) return;
    const level = WARN_ONLY.has(rule) ? 'WARN' : 'FAIL';
    const key = `${level}\t${rule}\t${where}${line ? `:${line}` : ''}`;
    if (!findings.some((f) => f.key === key)) findings.push({ key, level, rule, where, line });
  };

  function scanPath(where) {
    const lower = where.toLowerCase();
    for (const f of scanName(where)) add(f.rule, where);
    for (const r of PATH_RULES) if (r.re.test(where) && (!r.when || r.when(where))) add(r.id, where);
    if (profile === 'web') for (const r of WEB_RULES) if (r.re.test(where)) add(r.id, where);
    if (OPAQUE.test(lower)) add('uninspectable-archive', where);
  }

  function scanBuffer(where, buf, depth) {
    stats.files++;
    stats.bytes += buf.length;
    if (stats.bytes > MAX_TOTAL) throw new Error('artifact larger than the gate limit');
    scanPath(where);
    if (pinBytes && !pinSeen && buf.includes(pinBytes)) pinSeen = true;
    const gz = buf.length > 2 && buf[0] === 0x1f && buf[1] === 0x8b;
    if (gz || TARLIKE.test(where)) {
      if (depth >= MAX_DEPTH) { add('uninspectable-archive', where); return; }
      let body = buf;
      try { if (gz) body = zlib.gunzipSync(buf, { maxOutputLength: MAX_ENTRY }); } catch { add('uninspectable-archive', where); return; }
      stats.archives++;
      const isTar = TARLIKE.test(where) || (body.length >= 512 && body.subarray(257, 262).toString('latin1') === 'ustar');
      if (!isTar) { scanBuffer(`${where}!/${path.basename(where).replace(GZIP, '')}`, body, depth + 1); return; }
      let entries;
      try { entries = tarEntries(body); } catch { add('uninspectable-archive', where); return; }
      for (const e of entries) {
        if (e.link) { add('symlink', `${where}!/${e.name}`); continue; }
        scanBuffer(`${where}!/${e.name}`, e.read(), depth + 1);
      }
      return;
    }
    if (ZIPLIKE.test(where) || (depth === 0 && buf.length > 4 && buf.readUInt32LE(0) === 0x04034b50)) {
      if (depth >= MAX_DEPTH) { add('uninspectable-archive', where); return; }
      let entries;
      try { entries = zipEntries(buf); } catch { add('uninspectable-archive', where); return; }
      stats.archives++;
      for (const e of entries) {
        let body;
        try { body = e.read(); } catch { add('uninspectable-archive', `${where}!/${e.name}`); continue; }
        scanBuffer(`${where}!/${e.name}`, body, depth + 1);
      }
      return;
    }
    for (const f of scanContent(buf)) add(f.rule, where, f.line);
    if (/\.(?:arsc|xml)$/i.test(where) && !isText(buf) && buf.length < (16 << 20)) {
      for (const f of scanContent(utf16View(buf))) add(f.rule, where);
    }
    if (/\.(?:[cm]?js|ts|html?)$/i.test(where) && isText(buf)) {
      const text = buf.toString('utf8');
      if (BACKEND_CONTENT.test(text)) add('backend-source', where);
      if (SOURCE_MAP_REF.test(text)) add('source-map', where);
    }
  }

  function scanTarget(target) {
    const st = fs.lstatSync(target);
    const base = path.basename(target);
    if (st.isSymbolicLink()) { add('symlink', base); return; }
    if (st.isFile()) { scanBuffer(base, fs.readFileSync(target), 0); return; }
    const root = fs.realpathSync(target);
    const walk = (dir, rel) => {
      for (const d of fs.readdirSync(dir, { withFileTypes: true })) {
        const abs = path.join(dir, d.name);
        const r = rel ? `${rel}/${d.name}` : d.name;
        if (d.isSymbolicLink()) {
          //  پیوندی که بیرونِ خروجی را نشان دهد یعنی محتوایی که این دروازه نمی‌بیند
          let real = null;
          try { real = fs.realpathSync(abs); } catch { /* پیوندِ شکسته */ }
          if (!real || !(real + path.sep).startsWith(root + path.sep)) add('symlink', r);
          continue;
        }
        if (d.isDirectory()) { scanPath(`${r}/`); walk(abs, r); continue; }
        if (d.isFile()) scanBuffer(r, fs.readFileSync(abs), 0);
      }
    };
    walk(target, '');
  }

  return {
    scanTarget,
    scanBuffer: (where, buf) => scanBuffer(where, buf, 0),
    finish() {
      if (licensePin === '') add('license-key-not-pinned', '(artifact)');
      else if (pinBytes && !pinSeen) add('license-key-pin-missing', '(artifact)');
      return { findings, stats };
    },
  };
}

function usage() {
  console.error('کاربرد: node tools/security/artifact-gate.mjs [--profile apk|desktop|web] [--allow <json>] [--license-pin <base64>] <path>...');
  return 2;
}

export function main(argv) {
  const rest = [];
  let profile = 'any';
  let allowFile = null;
  let licensePin = null;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--profile') profile = argv[++i];
    else if (a === '--allow') allowFile = argv[++i];
    else if (a === '--license-pin') licensePin = (argv[++i] ?? '').trim();
    else if (a.startsWith('--')) return usage();
    else rest.push(a);
  }
  if (!rest.length || !['any', 'apk', 'desktop', 'web'].includes(profile)) return usage();
  const allow = allowFile ? JSON.parse(fs.readFileSync(allowFile, 'utf8')).allow : [];
  const gate = createGate({ profile, allow, licensePin });
  for (const t of rest) {
    if (!fs.existsSync(t)) { console.error(`artifact-gate: «${t}» نیست — چیزی که نیست سنجیده نمی‌شود`); return 2; }
    gate.scanTarget(t);
  }
  const { findings, stats } = gate.finish();
  if (!stats.files) { console.error('artifact-gate: هیچ فایلی برای سنجش پیدا نشد'); return 2; }
  for (const f of findings) {
    console.log(f.key);
    //  حاشیه‌نویسیِ GitHub — باز هم بی مقدار
    if (process.env.GITHUB_ACTIONS) console.log(`::${f.level === 'FAIL' ? 'error' : 'warning'} title=artifact-gate ${f.rule}::${f.where}${f.line ? `:${f.line}` : ''}`);
  }
  const fails = findings.filter((f) => f.level === 'FAIL').length;
  const summary = `artifact-gate (${profile}): ${stats.files} فایل، ${stats.archives} بایگانیِ بازشده، ` +
    `${Math.round(stats.bytes / 1048576)}MB — ${fails} یافتهٔ سد، ${findings.length - fails} هشدار.`;
  console.error(summary);
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY,
      `### دروازهٔ محتوای خروجی (${profile}) — SEC-ART\n\n${fails ? '❌' : '✅'} ${summary}\n\n` +
      (findings.length ? `\`\`\`\n${findings.map((f) => f.key).join('\n')}\n\`\`\`\n` : ''));
  }
  return fails ? 1 : 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let code;
  try { code = main(process.argv.slice(2)); } catch (err) { console.error(`artifact-gate: ${err.message}`); code = 2; }
  process.exit(code);
}
