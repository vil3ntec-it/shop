/**
 * Quality-gate rules: which files each check looks at, and what it counts.
 * Rules are code (reviewed like code); the caps they are measured against
 * live in baseline.json (data that may only go down). See docs/QUALITY-fa.md.
 */

/** A new non-test source file may not exceed this; bigger files on the baseline may not grow. */
export const MAX_LINES = 800;

const SOURCE_EXT = /\.(js|mjs|cjs|ts|tsx|kt|kts|java|css|html)$/;
const NOT_SOURCE = new RegExp([
  '(^|/)(test|tests|androidTest|testFixtures|test-vectors|fixtures|vendor|node_modules)/',
  '\\.test\\.[cm]?js$',
  'Tests?\\.kt$',
  '\\.min\\.(js|css)$',
].join('|'));

/** The frozen shop web app: measured on its own, never refactored here. */
export const FROZEN_FILES = ['index.html'];

export function isSource(file) {
  return SOURCE_EXT.test(file) && !NOT_SOURCE.test(file) && !FROZEN_FILES.includes(file);
}

export const isServerJs = (file) => /^server\/src\/.*\.js$/.test(file);
export const isKotlinMain = (file) => /\.kt$/.test(file) && isSource(file);
export const isWebJs = (file) => /\.(js|mjs|cjs|html)$/.test(file) && isSource(file);

/**
 * Helpers that already exist in several hand-written copies (AUDIT §1.5).
 * Each rule counts declarations outside its canonical `home`; the total may
 * only go down. When a canonical helper lands, add its path to `home`.
 */
export const DUPLICATE_HELPERS = [
  {
    id: 'afn',
    what: 'Kotlin `fun afn(` — متنِ پول با «افغانی»',
    applies: isKotlinMain,
    home: [],
    pattern: /\bfun\s+(?:<[^>]*>\s*)?(?:\w+(?:<[^>]*>)?\??\.)?afn\s*\(/g,
  },
  {
    id: 'minorOf',
    what: 'Kotlin `fun minorOf(` — عددِ تایپ‌شده به واحدِ کوچک (نسخه‌ها در ورودیِ خالی ناهم‌سان‌اند)',
    applies: isKotlinMain,
    home: [],
    pattern: /\bfun\s+(?:<[^>]*>\s*)?(?:\w+(?:<[^>]*>)?\??\.)?minorOf\s*\(/g,
  },
  {
    id: 'StatusPill',
    what: 'Kotlin `fun StatusPill(` بیرون از ui/components/common',
    applies: isKotlinMain,
    home: ['android-native/app/src/main/java/ir/vil3ntec/tohid/ui/components/common/StatusPill.kt'],
    pattern: /\bfun\s+StatusPill\s*\(/g,
  },
  {
    id: 'html-escape',
    what: 'JS: کمکیِ escapeِ HTML (`esc`، `escapeHtml`…) دوباره نوشته شده',
    applies: isWebJs,
    home: [],
    pattern: /(?:\bfunction\s+(?:esc|escHtml|escapeHtml|escapeHTML|htmlEscape)\s*\(|\b(?:const|let|var)\s+(?:esc|escHtml|escapeHtml|escapeHTML|htmlEscape)\s*=)/g,
    //  only a real HTML escape counts (not e.g. a CSV `esc`)
    confirm: (text, index) => /&amp;|&lt;/.test(text.slice(index, index + 400)),
  },
  {
    id: 'secret-derivation',
    what: 'سرور: زنجیرهٔ تقدمِ رمز (`config.secrets.a || config.secrets.b …`، نسخه‌های pepper())',
    applies: isServerJs,
    home: [],
    pattern: /(?<![!\w.])config\.secrets\.\w+\s*\|\|\s*config\.secrets\.\w+(?![\w.])/g,
  },
];

/** Inline product/role literals in the server (`=== 'pump'`, `case 'shop':` …), AUDIT §1.8. */
export const PRODUCT_LITERAL = {
  applies: isServerJs,
  pattern: /[!=]==?\s*['"](?:pump|shop)['"]|['"](?:pump|shop)['"]\s*[!=]==?|\bcase\s+['"](?:pump|shop)['"]/g,
};

/**
 * Migrations: numbers must be unique (the only blocking check). migrate.js
 * applies every `.sql` in the folder, so the number is the leading digits of
 * any such name; a name not like NNN_name.sql is still a migration (warned).
 */
export const MIGRATIONS_DIR = 'server/migrations';
export const MIGRATION_NUMBER = /^(\d+)/;
export const MIGRATION_NAME = /^\d{3,}_[a-z0-9_]+\.sql$/;

/** ESLint (correctness only, pinned; run with npx so package.json is untouched) on changed files matching these. */
export const ESLINT_PACKAGE = 'eslint@9.39.5';
export const ESLINT_CONFIG = 'tools/quality/eslint.config.mjs';
export const ESLINT_SERVER_FILES = ['server/src/**/*.js'];

/**
 * Pure JS cores shared with old phones: must stay ES5 (no let/const/arrow/
 * template/class). Every `license/*-core.js` is included automatically.
 */
export const ES5_FILES = [
  'license/*-core.js',
  'license/debt-book.js',
  'license/hash-chain.js',
  'license/keymap.js',
  'license/ledger-store.js',
  'license/money.js',
  'license/report-board.js',
];

/** Coverage (report only) runs the server tests that need no database. */
export const COVERAGE_DIR = 'server';
export const NEEDS_DATABASE = /require\(['"]\.\/helpers['"]\)|src\/(?:db|app)['"]|TEST_DATABASE_URL|pglite/;

/** Minimal glob: `*` inside one path segment, `**` across segments. */
export function globToRegExp(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') {
      re += glob[i + 2] === '/' ? '(?:.*/)?' : '.*';
      i += glob[i + 2] === '/' ? 2 : 1;
    } else if (c === '*') re += '[^/]*';
    else re += c.replace(/[.+?^${}()|[\]\\]/g, '\\$&');
  }
  return new RegExp(`^${re}$`);
}

export const matchesAny = (globs) => {
  const res = globs.map(globToRegExp);
  return (file) => res.some((r) => r.test(file));
};

/**
 * Platform dependencies in logic (docs/IOS-PLAN-fa.md §۴ گامِ ۰، RULES-fa «v3 · هسته‌های مشترک»):
 * what keeps a Kotlin file off iPhone. Measured in the Android app and core/commonMain; a new file
 * may have none, an existing one may only go down. Boundary files (the platform side of an
 * interface) are not measured. `*Desk.kt` is not a boundary name: ui/screens/DeliveryDesk.kt is a screen.
 */
export const PLATFORM_DEPS = {
  applies: (file) => isKotlinMain(file)
    && (/^android-native\/app\/src\/main\/java\//.test(file) || /^core\/src\/commonMain\//.test(file)),
  boundary: (file) => /\/platform\//.test(file) || /(?:Android|Desktop|\.android|\.desktop)\.kt$/.test(file),
  patterns: [
    { id: 'android.*', pattern: /(?<![\w.])android\.[a-z]/g },
    { id: 'androidx (غیرِ Compose)', pattern: /(?<![\w.])androidx\.(?!compose\.)[a-z]/g },
    { id: 'Context', pattern: /(?<![\w.])(?:Local)?Context\b/g },
    { id: 'SharedPreferences', pattern: /(?<![\w.])SharedPreferences\b/g },
    { id: 'java.* / javax.*', pattern: /(?<![\w.])javax?\.[a-z]/g },
    { id: 'org.json', pattern: /(?<![\w.])org\.json\b/g },
    { id: 'System.currentTimeMillis', pattern: /\bSystem\.currentTimeMillis\b/g },
    { id: 'String.format', pattern: /\bString\.format\b|"\.format\s*\(/g },
    { id: 'synchronized', pattern: /\bsynchronized\s*\(|@Synchronized\b/g },
  ],
};
