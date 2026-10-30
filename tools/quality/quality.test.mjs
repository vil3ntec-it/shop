/**
 * Tests for the quality gate itself: ratchet arithmetic, detectors and the
 * committed baseline. Run: node --test tools/quality/
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { checkPerFile, lowerPerFile, lowerTotals, attribute } from './lib/ratchet.mjs';
import { lineCount, findAll, duplicateMigrations } from './lib/measure.mjs';
import { DUPLICATE_HELPERS, PRODUCT_LITERAL, MAX_LINES, MIGRATION_NUMBER, isSource, globToRegExp } from './rules.mjs';
import { parseLcov } from './checks/coverage.mjs';
import { migrationFindings } from './checks/migrations.mjs';
import { platformHits, measured, run as runPlatformDeps } from './checks/platform-deps.mjs';

const rule = (id) => DUPLICATE_HELPERS.find((r) => r.id === id);
const hits = (id, text) => findAll(text, rule(id).pattern, rule(id).confirm).length;

test('per-file caps: listed files may not grow, others get the default cap', () => {
  const caps = { 'big.kt': 900, 'shrunk.kt': 1000 };
  const current = new Map([['big.kt', 901], ['shrunk.kt', 950], ['new.kt', 801], ['small.kt', 10]]);
  const { over, lowerable } = checkPerFile(caps, current, MAX_LINES);
  assert.deepEqual(over.map((o) => [o.file, o.listed]), [['big.kt', true], ['new.kt', false]]);
  assert.deepEqual(lowerable.map((l) => l.file), ['shrunk.kt']);
});

test('lowering never raises a cap and drops files back under the limit or deleted', () => {
  const caps = { 'a.kt': 900, 'b.kt': 900, 'gone.kt': 1200, 'fixed.kt': 850 };
  const next = lowerPerFile(caps, new Map([['a.kt', 950], ['b.kt', 820], ['fixed.kt', 700]]), MAX_LINES);
  assert.deepEqual(next, { 'a.kt': 900, 'b.kt': 820 });
  assert.deepEqual(lowerTotals({ afn: 3, minorOf: 2 }, { afn: 1, minorOf: 5 }), { afn: 1, minorOf: 2 });
});

test('attribution: only a change that touched the file and made it worse is "introduced"', () => {
  const changed = new Map([['grew.js', 'grew.js'], ['same.js', 'same.js'], ['added.js', null], ['moved.js', 'old.js']]);
  const before = { 'grew.js': 5, 'same.js': 7, 'old.js': 3 };
  const at = (base) => (base == null ? null : before[base]);
  assert.equal(attribute('grew.js', 6, changed, at), 'introduced');
  assert.equal(attribute('same.js', 7, changed, at), 'pre-existing');
  assert.equal(attribute('added.js', 1, changed, at), 'introduced');
  assert.equal(attribute('moved.js', 3, changed, at), 'pre-existing');
  assert.equal(attribute('untouched.js', 99, changed, at), 'pre-existing');
  assert.equal(attribute('x.js', 1, null, at), 'unknown');
});

test('duplicate migration numbers are found, malformed names included; distinct numbers pass', () => {
  assert.deepEqual(duplicateMigrations(['101_a.sql', '102_b.sql', '102_c.sql', '0102_d.sql', '102-E.sql'], MIGRATION_NUMBER),
    [{ number: 102, files: ['0102_d.sql', '102-E.sql', '102_b.sql', '102_c.sql'] }]);
  assert.deepEqual(duplicateMigrations(['001_a.sql', '002_b.sql', 'notes.sql'], MIGRATION_NUMBER), []);
});

test('migrations block only the change that adds a duplicate; one already on main is a warning', () => {
  const files = ['server/migrations/115_a.sql', 'server/migrations/115_b.sql', 'server/migrations/116_Foo.sql',
    'server/migrations/116-bar.sql', 'server/migrations/117_c.sql'];
  //  this PR added 116-bar.sql (116_Foo.sql is on main); 115 was duplicated before it
  const changed = new Map([['server/migrations/116-bar.sql', null], ['server/migrations/117_c.sql', 'server/migrations/117_c.sql']]);
  const { dups, badNames } = migrationFindings(files, changed);
  assert.deepEqual(dups.map((d) => [d.number, d.cause, d.added]), [[115, 'pre-existing', []], [116, 'introduced', ['116-bar.sql']]]);
  assert.deepEqual(badNames, ['116_Foo.sql', '116-bar.sql']);
  //  a rename onto a used number counts as adding; no parent commit ⇒ unknown (not blocking)
  const renamed = migrationFindings(files, new Map([['server/migrations/116_Foo.sql', 'server/migrations/120_x.sql']]));
  assert.equal(renamed.dups.find((d) => d.number === 116).cause, 'introduced');
  assert.ok(migrationFindings(files, null).dups.every((d) => d.cause === 'unknown'));
  assert.deepEqual(migrationFindings(files, new Map()).dups.map((d) => d.cause), ['pre-existing', 'pre-existing']);
});

test('helper detectors: real declarations count, comments and look-alikes do not', () => {
  assert.equal(hits('afn', 'private fun afn(minor: Long): String = ""\nfun Long.afn() = 1\n// fun afn(x)'), 2);
  assert.equal(hits('minorOf', 'internal fun minorOf(text: String): Long? = null\nval y = minorOf(t)'), 1);
  assert.equal(hits('StatusPill', 'private fun StatusPill(text: String) {}\nStatusPill("a")'), 1);
  const htmlEsc = "function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;'); }";
  const csvEsc = 'const esc = (v) => /[",\\n]/.test(v) ? `"${v}"` : v;';
  assert.equal(hits('html-escape', `${htmlEsc}\n${csvEsc}`), 1);
  assert.equal(hits('secret-derivation', "return config.secrets.api || config.secrets.jwt || 'x';"), 1);
  assert.equal(hits('secret-derivation', 'if (!config.secrets.api || config.secrets.api.length < 24) {}'), 0);
});

test("product literals: comparisons and case labels count, plain values do not", () => {
  const n = (s) => findAll(s, PRODUCT_LITERAL.pattern).length;
  assert.equal(n("if (app === 'pump') {}\nif ('shop' !== kind) {}\nswitch (a) { case \"pump\": }"), 3);
  assert.equal(n("const app = 'pump';\n// if (app === 'shop')"), 0);
});

test('source files: tests, vectors, vendor and the frozen app are excluded', () => {
  assert.ok(isSource('server/src/lib/delivery.js'));
  assert.ok(isSource('android-native/app/src/main/java/a/Sale.kt'));
  for (const f of ['server/test/a.test.js', 'android-native/app/src/test/java/a/SaleTest.kt',
    'core/test-vectors/x.json', 'server/public/vendor/lib.js', 'x.min.js', 'index.html', 'README.md']) {
    assert.equal(isSource(f), false, f);
  }
});

test('glob matching for ESLint scopes', () => {
  const core = globToRegExp('license/*-core.js');
  assert.ok(core.test('license/pay-core.js'));
  assert.equal(core.test('license/sub/pay-core.js'), false);
  const server = globToRegExp('server/src/**/*.js');
  assert.ok(server.test('server/src/app.js'));
  assert.ok(server.test('server/src/lib/delivery.js'));
  assert.equal(server.test('server/test/a.js'), false);
});

test('lcov parsing and line counting', () => {
  assert.deepEqual(parseLcov('SF:src/a.js\nDA:1,1\nLF:10\nLH:7\nend_of_record\n'), [{ file: 'src/a.js', found: 10, hit: 7 }]);
  assert.equal(lineCount('a\nb\n'), 2);
  assert.equal(lineCount(''), 0);
});

test('platform deps: every forbidden kind counts; comments, Compose and look-alikes do not', () => {
  const src = [
    'import android.content.Context',
    'import androidx.core.content.edit',
    'import androidx.compose.runtime.Composable',
    'import java.io.File',
    'import javax.crypto.Cipher',
    'import org.json.JSONObject',
    'fun f(ctx: Context, p: SharedPreferences) {',
    '  val c = LocalContext.current',
    '  val t = System.currentTimeMillis()',
    '  val s = String.format("%d", 1) + "%02d".format(2)',
    '  synchronized(lock) {}',
    '}',
    '@Synchronized fun g() = Foo::class.java.simpleName',
    '// import java.util.Date',
    ' * android.graphics در توضیح',
    'package ir.vil3ntec.tohid.android_like',
    'val name = item.context ?: myContextual',
  ].join('\n');
  assert.deepEqual(platformHits(src), {
    'android.*': 1, 'androidx (غیرِ Compose)': 1, Context: 2, SharedPreferences: 1, 'java.* / javax.*': 2,
    'org.json': 1, 'System.currentTimeMillis': 1, 'String.format': 2, synchronized: 2,
  });
  assert.deepEqual(platformHits('import kotlinx.coroutines.sync.Mutex\nval now = clock.nowMillis()'), {});
});

test('platform deps: app and commonMain are measured, boundary folders, tests and jvmMain are not', () => {
  const app = 'android-native/app/src/main/java/ir/vil3ntec/tohid';
  assert.ok(measured(`${app}/sale/Cart.kt`));
  assert.ok(measured(`${app}/ui/screens/DeliveryDesk.kt`));
  assert.ok(measured('core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/Money.kt'));
  for (const f of [`${app}/platform/Prefs.kt`, `${app}/data/StoreAndroid.kt`, `${app}/data/Store.desktop.kt`,
    'core/src/jvmMain/kotlin/ir/vil3ntec/tohid/core/Ulid.kt', 'android-native/app/src/test/java/a/CartTest.kt',
    'desktop/src/main/kotlin/a/Main.kt']) {
    assert.equal(measured(f), false, f);
  }
});

test('platform deps: a new file is red, an old one may not grow, the boundary and shrinking pass', () => {
  const app = 'android-native/app/src/main/java/a';
  const text = {
    [`${app}/New.kt`]: 'import android.content.Context',
    [`${app}/Old.kt`]: 'import java.io.File\nimport org.json.JSONObject',
    [`${app}/Shrunk.kt`]: 'import java.io.File',
    [`${app}/platform/Prefs.kt`]: 'import android.content.SharedPreferences',
    [`${app}/Clean.kt`]: 'val x = 1',
  };
  const before = { [`${app}/Old.kt`]: 'import java.io.File', [`${app}/Shrunk.kt`]: 'import java.io.File\nimport java.util.Date' };
  const ctx = {
    baseline: { platformDeps: { [`${app}/Old.kt`]: 1, [`${app}/Shrunk.kt`]: 2 } },
    files: Object.keys(text),
    changed: new Map([[`${app}/New.kt`, null], [`${app}/Old.kt`, `${app}/Old.kt`], [`${app}/platform/Prefs.kt`, null]]),
    read: (f) => text[f],
    atBase: (base, fn) => (before[base] == null ? null : fn(before[base])),
  };
  const { violations } = runPlatformDeps(ctx);
  assert.deepEqual(violations.map((v) => [v.file, v.value, v.cap, v.cause]).sort(),
    [[`${app}/New.kt`, 1, 0, 'introduced'], [`${app}/Old.kt`, 2, 1, 'introduced']]);
});

test('the committed baseline is well formed', () => {
  const b = JSON.parse(fs.readFileSync(new URL('./baseline.json', import.meta.url), 'utf8'));
  for (const [f, n] of Object.entries(b.fileSize)) assert.ok(Number.isInteger(n) && n > MAX_LINES, f);
  for (const [f, n] of Object.entries(b.productLiterals)) assert.ok(Number.isInteger(n) && n > 0, f);
  assert.deepEqual(Object.keys(b.duplicateHelpers).sort(), DUPLICATE_HELPERS.map((r) => r.id).sort());
  for (const n of Object.values(b.duplicateHelpers)) assert.ok(Number.isInteger(n) && n >= 0);
  assert.ok(b.frozen['index.html'].lines > 0 && b.frozen['index.html'].bytes > 0);
  for (const [f, n] of Object.entries(b.platformDeps)) assert.ok(Number.isInteger(n) && n > 0 && measured(f), f);
});
