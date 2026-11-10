'use strict';
/**
 * فهرستِ کلیدهای `live_config` از **خودِ سورس** (کاتالوگِ `KNOWN` ِ `routes/admin-flags.js`؛ `console-shell.test.js`،
 * `admin-flags.test.js`). کلیدِ تازه‌ای که هر جای کدِ غیرِ پمپ می‌خواند و در کاتالوگ نیست ⇒ CI سرخ.
 *
 * سرور (`server/src`، جز پرونده‌های مشترکِ پمپ) — بی محدودیتِ پیشوند، هر رشتهٔ نقطه‌دار در یکی از این جاها:
 *   const   ثابتی که نامش FLAG دارد یا به KEY/KEYS می‌رسد (همهٔ مقدارهای همان خط جز کلیدِ شیء)، یا ثابتی که تا دو خط پس از
 *           `live_config` به کار رفته (`sync-v1-keep.js`)
 *   call    آرگومانِ `flagOf`/`flagOn`/`flagIn`/`readFlag`/`defaultOn`/`isOn`
 *   sql     `key = '…'` / `key LIKE '…%'` در پرونده‌ای که `live_config` را می‌خواند
 *   obj     `key: '…'` در همان پرونده‌ها (`{ scope: 'ops', key: 'ops.sync_lag' }`)
 * وب (`server/public/*`، `license/`) و کاتلین (سورسِ اصلیِ برنامه‌ها، نه آزمون): همان `const`، و `ref` = نامِ
 * بک‌تیک‌دار روی خطی که «پرچم»/flag دارد — فقط با پیشوندی که کاتالوگ یا سرور می‌شناسد (نه `features.eta`).
 * پویا (`stage.<برچسب>`، `KEY_PREFIX`) دیده نمی‌شود: ردیفِ ثابت ندارد و درِ خودش را دارد (`admin-release.js`).
 */
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
/** پرونده‌های مشترکِ پمپ (همان سدِ `tools/claude-ops/finish-pr.sh`، به‌علاوهٔ station-*) خوانده نمی‌شوند */
const PUMP = /(^|\/)server\/src\/(lib|routes)\/sync-v1\.js$|pump|station/;
/** خودِ کاتالوگ خواننده نیست (هر ردیفش `key: '…'` است و همه را «خوانده» نشان می‌داد) */
const CATALOG = 'server/src/routes/admin-flags.js';
const DIRS = Object.freeze([
  ['server/src', 'srv'],
  ...['admin', 'kit', 'market', 'portal', 'receipt', 'sdk', 'store'].map((d) => [`server/public/${d}`, 'web']),
  ['license', 'web'],
  ...['android-native/app/src/main', 'android/app/src/main', 'core/src/commonMain', 'core/src/jvmMain', 'core/src/iosMain',
    'desktop/src/main', 'admin-android/app/src/main', 'customer-android/app/src/main', 'ios/shared', 'customer-ios/shared']
    .map((d) => [d, 'kt']),
]);
const SKIP_DIR = /^(node_modules|build|test|tests|androidTest|commonTest|jvmTest|iosTest|fonts|i18n|\.gradle)$/;
const FILE_LIKE = /\.(js|mjs|cjs|css|html|json|png|svg|kt|kts|md|txt|webmanifest|woff2?)$/;
const Q = `['"\`]([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)['"\`]`;
const READS = /live_config|liveConfig\.|\b(flagOf|flagOn|flagIn|readFlag|defaultOn)\s*\(/;
const CONST = /^\s*(?:export\s+)?(?:private\s+|internal\s+)?(?:const val|const|let|var|val)\s+([A-Z][A-Z0-9_]*)\b\s*(?::\s*\w+\s*)?=/;

function scanFile(rel, mode, add) {
  const lines = fs.readFileSync(path.join(ROOT, rel), 'utf8').split('\n');
  const reads = mode === 'srv' && READS.test(lines.join('\n'));
  const near = new Set();
  if (reads) {
    lines.forEach((l, i) => {
      if (/live_config/.test(l)) for (const j of [i, i + 1, i + 2]) for (const m of (lines[j] || '').matchAll(/\b[A-Z][A-Z0-9_]+\b/g)) near.add(m[0]);
    });
  }
  lines.forEach((l, i) => {
    const at = `${rel}:${i + 1}`;
    const c = l.match(CONST);
    if (c && (/FLAG|(^|_)KEYS?$/.test(c[1]) || near.has(c[1]))) for (const m of l.matchAll(new RegExp(`${Q}(?!\\s*:)`, 'g'))) add(m[1], at, 'const', mode);
    if (mode === 'srv') {
      for (const m of l.matchAll(new RegExp(`\\b(?:flagOf|flagOn|flagIn|readFlag|defaultOn|isOn)\\s*\\([^;]*?${Q}`, 'g'))) add(m[1], at, 'call', mode);
      if (reads) {
        for (const m of l.matchAll(/\bkey\s*(?:=|LIKE)\s*'([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)%?'/g)) add(m[1], at, 'sql', mode);
        for (const m of l.matchAll(new RegExp(`\\bkey\\s*:\\s*${Q}`, 'g'))) add(m[1], at, 'obj', mode);
      }
    } else if (/پرچم|\bflag/i.test(l)) {
      for (const m of l.matchAll(/`([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)`/g)) add(m[1], at, 'ref', mode);
    }
  });
}

function walk(dir, mode, ext, add) {
  const abs = path.join(ROOT, dir);
  if (!fs.existsSync(abs)) return 0;
  let n = 0;
  for (const e of fs.readdirSync(abs, { withFileTypes: true })) {
    const rel = `${dir}/${e.name}`;
    if (e.isDirectory()) { if (!SKIP_DIR.test(e.name)) n += walk(rel, mode, ext, add); }
    else if (ext.test(e.name) && !PUMP.test(rel) && rel !== CATALOG) { scanFile(rel, mode, add); n += 1; }
  }
  return n;
}

/**
 * ⇒ { keys: Map(کلید ⇒ [{at, how}])، files: شمارِ پرونده‌های خوانده‌شده در هر پوشه }
 * کلیدِ وب/کاتلین فقط با پیشوندی که سرور یا `known` دارد (نامِ پاسخ‌ها و مجوزها مثلِ `features.eta`/`sales.price` نه).
 */
function scanFlags(known = []) {
  const srv = new Map();
  const cli = new Map();
  const add = (k, at, how, mode) => {
    if (FILE_LIKE.test(k)) return;
    const m = mode === 'srv' ? srv : cli;
    if (!m.has(k)) m.set(k, []);
    m.get(k).push({ at, how });
  };
  const files = {};
  for (const [dir, mode] of DIRS) files[dir] = walk(dir, mode, mode === 'kt' ? /\.kt$/ : /\.(js|mjs|cjs)$/, add);
  const ns = new Set([...srv.keys(), ...known].map((k) => k.split('.')[0]));
  const keys = new Map(srv);
  for (const [k, v] of cli) if (ns.has(k.split('.')[0])) keys.set(k, [...(keys.get(k) || []), ...v]);
  return { keys, files };
}

module.exports = { scanFlags, DIRS, PUMP, CATALOG };
