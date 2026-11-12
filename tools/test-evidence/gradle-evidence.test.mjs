/**
 * آزمونِ مبدلِ شواهدِ Gradle (GATE-NEW-05). اجرا: node --test tools/test-evidence/gradle-evidence.test.mjs
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { parseJUnit, collect, filterOf, sh, unxml, brief, runInfo, javaLine, SUITES } from './gradle-evidence.mjs';

const SCRIPT = fileURLToPath(new URL('./gradle-evidence.mjs', import.meta.url));

//  همان شکلِ گزارشِ Gradle (TEST-*.xml): گذشته، سرخ با موجودیت و CDATA، SKIP ِ Assume، و testcase ِ بی بدنه
const XML = `<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="ir.vil3ntec.tohid.desktop.AtRestDiskTest" tests="4" skipped="1" failures="1" errors="0" timestamp="2026-11-12T09:32:26.083Z" hostname="vm" time="0.5">
  <properties/>
  <testcase name="تاریخ خورشیدی به ISO با رقم لاتین" classname="ir.vil3ntec.tohid.desktop.AtRestDiskTest" time="0.011"/>
  <testcase name="کلید &quot;رمز&quot; &lt;نه&gt; &amp; ۱۲" classname="ir.vil3ntec.tohid.desktop.AtRestDiskTest" time="0.25">
    <failure message="expected:&lt;2000&gt; but was:&lt;2001&gt;" type="java.lang.AssertionError">java.lang.AssertionError: expected:&lt;2000&gt; but was:&lt;2001&gt;
	at org.junit.Assert.fail(Assert.java:89)
	at ir.vil3ntec.tohid.desktop.AtRestDiskTest.x(AtRestDiskTest.kt:12)
</failure>
  </testcase>
  <testcase name="ویندوز — فایلِ کلید فقط برای صاحبش" classname="ir.vil3ntec.tohid.desktop.AtRestDiskTest" time="0.001">
    <skipped message="got: &lt;false&gt;, expected: is &lt;true&gt;" type="org.junit.AssumptionViolatedException">org.junit.AssumptionViolatedException
	at org.junit.Assume.assumeThat(Assume.java:106)</skipped>
  </testcase>
  <testcase name="خطا" classname="ir.vil3ntec.tohid.desktop.AtRestDiskTest" time="">
    <error message="boom" type="java.lang.IllegalStateException"><![CDATA[java.lang.IllegalStateException: boom <raw>]]></error>
  </testcase>
  <system-out><![CDATA[]]></system-out>
</testsuite>`;

function tmpDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'gradle-evidence-'));
}

test('parseJUnit: گذشته، سرخ (failure و error)، SKIP با پیام؛ موجودیت‌ها و CDATA باز می‌شوند', () => {
  const t = parseJUnit(XML);
  assert.equal(t.length, 4);
  assert.deepEqual(t.map((x) => x.result), ['pass', 'fail', 'skip', 'fail']);
  assert.equal(t[0].name, 'تاریخ خورشیدی به ISO با رقم لاتین');
  assert.equal(t[0].durationMs, 11);
  assert.equal(t[0].output, undefined);
  assert.equal(t[1].name, 'کلید "رمز" <نه> & ۱۲');
  assert.match(t[1].output, /^java\.lang\.AssertionError: expected:<2000> but was:<2001>/);
  assert.match(t[1].output, /AtRestDiskTest\.kt:12/);
  assert.equal(t[2].output, 'got: <false>, expected: is <true>');
  assert.equal(t[3].durationMs, null);
  assert.match(t[3].output, /boom <raw>/);
});

test('parseJUnit: پسوندِ هدفِ KMP («[jvm]») از نام برداشته می‌شود — همان نامِ KTEST و الگوی --tests', () => {
  const [t] = parseJUnit('<testcase name="سنجه نامِ ممنوع را می‌گیرد[jvm]" classname="ir.vil3ntec.tohid.core.KotlinTestNamesTest" time="0.002"/>');
  assert.equal(t.name, 'سنجه نامِ ممنوع را می‌گیرد');
  assert.equal(filterOf(t.className, t.name), "'ir.vil3ntec.tohid.core.KotlinTestNamesTest.سنجه نامِ ممنوع را می‌گیرد'");
});

test('brief: پیامِ تکراری دو بار نمی‌آید، خروجی کران‌دار است', () => {
  assert.equal(brief('e: x', 'e: x\n\tat a\n\tat b'), 'e: x\n\tat a\n\tat b');
  assert.equal(brief('پیام', 'java.lang.Error\n\tat a'), 'پیام\njava.lang.Error\n\tat a');
  assert.equal(brief('', 'x\n'.repeat(50)).split('\n').length, 8);
  assert.ok(brief('m', 'y'.repeat(5000)).length <= 2000);
});

test('unxml: موجودیتِ عددی و هگز؛ ناشناخته دست نمی‌خورد', () => {
  assert.equal(unxml('&#1575;&#x628; &foo; &amp;lt;'), 'اب &foo; &lt;');
});

test('filterOf/sh: الگوی یک آزمون در شل همان متن است؛ نامِ ناامن ⇒ کلاس', () => {
  const name = "نام با ' و فاصله — «ب»";
  const out = spawnSync('bash', ['-c', `printf %s ${filterOf('a.B', name)}`], { encoding: 'utf8' }).stdout;
  assert.equal(out, `a.B.${name}`);
  assert.equal(filterOf('a.B', 'x.y'), sh('a.B'));
  assert.equal(filterOf('a.B', 'همه *'), sh('a.B'));
  assert.equal(filterOf('a.B', 'classMethod'), sh('a.B'));
  assert.equal(SUITES.desktop(sh('a.B')), "./desktop/gradlew --no-daemon -p desktop test --tests 'a.B'");
  assert.equal(SUITES.core(sh('a.B')), "(cd core && ../desktop/gradlew --no-daemon jvmTest --tests 'a.B')");
});

test('collect: هر پرونده و دسته، شمارها، و پوشهٔ بی گزارش در missing', () => {
  const root = tmpDir();
  fs.mkdirSync(path.join(root, 'd'));
  fs.writeFileSync(path.join(root, 'd', 'TEST-a.xml'), XML);
  fs.writeFileSync(path.join(root, 'd', 'other.bin'), 'x');
  const { lines, counts, missing } = collect(root, ['desktop=d', 'core=nope']);
  assert.equal(lines.length, 4);
  assert.deepEqual(counts, { desktop: { pass: 1, fail: 2, skip: 1 } });
  assert.deepEqual(missing, ['core=nope']);
  assert.equal(lines[0].id, 'ir.vil3ntec.tohid.desktop.AtRestDiskTest#تاریخ خورشیدی به ISO با رقم لاتین');
  assert.equal(lines[0].suite, 'desktop');
  assert.equal(lines[0].repro, "./desktop/gradlew --no-daemon -p desktop test --tests 'ir.vil3ntec.tohid.desktop.AtRestDiskTest.تاریخ خورشیدی به ISO با رقم لاتین'");
  assert.throws(() => collect(root, ['android=d']), /دستهٔ ناشناخته/);
});

test('runInfo: commit و اجرای CI از GITHUB_*، بی متغیرِ دیگر', () => {
  const keep = { ...process.env };
  try {
    Object.assign(process.env, { GITHUB_SHA: 'abc123', GITHUB_RUN_ID: '42', GITHUB_REPOSITORY: 'o/r', GITHUB_REF_NAME: 'main', GITHUB_JOB: 'evidence', SECRET_X: 'nope', JAVA_TOOL_OPTIONS: '-Dtok=nope' });
    const r = runInfo('.', ['desktop=d']);
    assert.equal(r.kind, 'run');
    assert.equal(r.commit, 'abc123');
    assert.equal(r.ci, 'https://github.com/o/r/actions/runs/42');
    assert.equal(r.ref, 'main');
    assert.ok(!JSON.stringify(r).includes('nope'));
  } finally {
    for (const k of Object.keys(process.env)) if (!(k in keep)) delete process.env[k];
    Object.assign(process.env, keep);
  }
});

test('javaLine: گزینه‌های JVM ِ محیط (Picked up …) در شواهد نمی‌آیند', () => {
  const err = 'Picked up JAVA_TOOL_OPTIONS: -Djavax.net.ssl.trustStorePassword=hunter2 -Dhttps.proxyHost=10.0.0.1\n'
    + 'NOTE: Picked up JDK_JAVA_OPTIONS: -Dx=secret\n'
    + 'openjdk version "17.0.13" 2024-10-15\nOpenJDK Runtime Environment Temurin-17.0.13+11 (build 17.0.13+11)\nOpenJDK 64-Bit Server VM\n';
  const line = javaLine(err);
  assert.equal(line, 'openjdk version "17.0.13" 2024-10-15 · OpenJDK Runtime Environment Temurin-17.0.13+11 (build 17.0.13+11)');
  assert.ok(!/hunter2|secret|proxyHost/.test(line));
  assert.equal(javaLine(''), '');
});

test('CLI: JSONL با خطِ run و یک خط برای هر آزمون؛ بی گزارش ⇒ کدِ ۱', () => {
  const root = tmpDir();
  const dir = path.join(root, 'r');
  fs.mkdirSync(dir);
  fs.writeFileSync(path.join(dir, 'TEST-a.xml'), XML);
  const out = path.join(root, 'o', 'gradle.jsonl');
  const ok = spawnSync(process.execPath, [SCRIPT, '--out', out, `desktop=${dir}`], { encoding: 'utf8', env: { ...process.env, GITHUB_STEP_SUMMARY: '' } });
  assert.equal(ok.status, 0, ok.stderr);
  const rows = fs.readFileSync(out, 'utf8').trim().split('\n').map((l) => JSON.parse(l));
  assert.equal(rows[0].kind, 'run');
  assert.equal(rows.length, 5);
  assert.ok(rows.slice(1).every((r) => r.kind === 'test' && r.id && r.repro && r.result));
  assert.match(ok.stdout, /\| desktop \| 1 \| 2 \| 1 \|/);
  const none = spawnSync(process.execPath, [SCRIPT, '--out', path.join(root, 'x.jsonl'), `desktop=${path.join(root, 'empty')}`], { encoding: 'utf8' });
  assert.equal(none.status, 1);
  const half = spawnSync(process.execPath, [SCRIPT, '--out', path.join(root, 'y.jsonl'), `desktop=${dir}`, `core=${path.join(root, 'empty')}`], { encoding: 'utf8' });
  assert.equal(half.status, 1);
  assert.match(half.stderr, /core=/);
});
