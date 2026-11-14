/**
 * آزمونِ نگهبانِ ورک‌فلوها. اجرا: node --test "tools/security/*.test.mjs"
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { checkWorkflow, walk, EXCLUDED, RELEASE_JOBS } from './workflow-guard.mjs';

const SHA = 'a'.repeat(40);
const rules = (found) => found.map((f) => f.rule).sort();

const base = (body, top = 'permissions:\n  contents: read\n') => `name: x
on:
  pull_request:
  push:
    branches: [main]
${top}
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
${body}`;

test('ورک‌فلوی سالم: سنجاق، مجوزِ خواندنی، ورودی از env', () => {
  const y = base(`      - uses: actions/checkout@${SHA} # v4.4.0
      - name: s
        env:
          BR: \${{ github.head_ref }}
        run: |
          echo "$BR"
          echo "\${{ github.sha }} \${{ matrix.os }}"
`);
  assert.deepEqual(checkWorkflow('ok.yml', y), []);
});

test('pin: برچسبِ جابه‌جاشدنی و SHAِ بی یادداشت سد می‌شوند', () => {
  const y = base(`      - uses: actions/checkout@v4
      - uses: gradle/actions/setup-gradle@${SHA}
      - uses: ./.github/actions/local
      - uses: docker://alpine@sha256:${'b'.repeat(64)}
`);
  const f = checkWorkflow('p.yml', y);
  assert.deepEqual(rules(f), ['pin', 'pin']);
  assert.match(f[0].msg, /actions\/checkout@v4/);
  assert.match(f[1].msg, /یادداشتِ نسخه/);
});

test('perms: بی permissions، write در بالا، write-all و write در کارِ ساخت سد می‌شوند', () => {
  const ok = `      - uses: actions/checkout@${SHA} # v4\n`;
  assert.deepEqual(rules(checkWorkflow('a.yml', base(ok, ''))), ['perms']);
  assert.deepEqual(rules(checkWorkflow('a.yml', base(ok, 'permissions:\n  contents: write\n'))), ['perms']);
  assert.deepEqual(rules(checkWorkflow('a.yml', base(ok, 'permissions: write-all\n'))), ['perms']);
  assert.deepEqual(rules(checkWorkflow('a.yml', base(ok, 'permissions: { contents: write }\n'))), ['perms']);
  const job = base(ok).replace('    runs-on: ubuntu-latest\n', '    runs-on: ubuntu-latest\n    permissions:\n      contents: write\n');
  assert.deepEqual(rules(checkWorkflow('a.yml', job)), ['perms']);
});

test('perms: write فقط در کارِ فهرست‌شدهٔ انتشار پذیرفته است', () => {
  const y = `name: n
on:
  push:
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - run: gradle --no-daemon testReleaseUnitTest
  publish:
    needs: build
    if: \${{ github.ref == 'refs/heads/main' }}
    permissions:
      contents: write
    runs-on: ubuntu-latest
    steps:
      - run: true
`;
  assert.deepEqual(checkWorkflow('tohid-native.yml', y), []);
  assert.deepEqual(rules(checkWorkflow('other.yml', y)), ['perms']);
  const pages = y.replace('      contents: write\n', '      contents: write\n      packages: write\n');
  assert.deepEqual(rules(checkWorkflow('tohid-native.yml', pages)), ['perms']);
});

test('trigger: pull_request_target و workflow_run سد می‌شوند', () => {
  const y = base(`      - run: true\n`).replace('  pull_request:\n', '  pull_request_target:\n  workflow_run:\n    workflows: [x]\n');
  assert.deepEqual(rules(checkWorkflow('t.yml', y)), ['trigger', 'trigger']);
  const flow = base(`      - run: true\n`).replace(/on:\n(  .*\n)+?(?=permissions)/, 'on: [push, pull_request_target]\n');
  assert.deepEqual(rules(checkWorkflow('t.yml', flow)), ['trigger']);
});

test('inject: ورودیِ نامطمئن داخلِ run (بلوکی و یک‌خطی) سد می‌شود، بیرونِ run نه', () => {
  const y = base(`      - name: a
        if: \${{ inputs.channel == 'stable' }}
        run: |
          if [ "\${{ inputs.channel }}" = stable ]; then echo; fi
          echo "\${{ github.event.pull_request.title }}"
      - run: echo \${{ github.head_ref }}
      - run: echo "\${{ github.ref }}"
      - uses: actions/github-script@${SHA} # v7
        with:
          script: |
            console.log("\${{ github.event.comment.body }}")
`);
  const f = checkWorkflow('i.yml', y);
  assert.deepEqual(rules(f), ['inject', 'inject', 'inject', 'inject', 'inject']);
});

/* ── S64: «آزمونِ سرخ یا شاخهٔ غیرمجاز هرگز منتشر نمی‌شود» ─────────────────────── */

const release = (o = {}) => `name: r
on:
  push:
    branches: [main]
  pull_request:
permissions:
  contents: read
jobs:
  check:
    runs-on: ubuntu-latest${o.checkCoe ? '\n    continue-on-error: true' : ''}
    steps:
      - uses: actions/checkout@${SHA} # v4.4.0
      - name: آزمون
        run: ${o.testRun ?? './gradlew --no-daemon test'}${o.stepCoe ? '\n        continue-on-error: true' : ''}${o.stepIf ? `\n        if: ${o.stepIf}` : ''}
${o.extraJob ?? ''}  package:
    needs: ${o.packageNeeds ?? 'check'}
    if: >-
      ${o.packageIf ?? "github.event_name != 'pull_request'"}
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew --no-daemon packageMsi
  publish:
    needs: ${o.publishNeeds ?? 'package'}
    if: \${{ ${o.publishIf ?? "github.ref == 'refs/heads/main' && github.event_name != 'pull_request'"} }}
    permissions:
      contents: write
    runs-on: ubuntu-latest
    steps:
      - run: gh release upload x
`;
const releaseMsgs = (y, f = 'tohid-desktop.yml') => checkWorkflow(f, y).filter((x) => x.rule === 'release').map((x) => x.msg);

test('release (S64): انتشار فقط از main و فقط پس از آزمونِ سبز — نمونهٔ سالم می‌گذرد', () => {
  assert.deepEqual(checkWorkflow('tohid-desktop.yml', release()), []);
  //  `needs` به شکلِ فهرست و فهرستِ چندخطی هم
  assert.deepEqual(releaseMsgs(release({ publishNeeds: '[package]' })), []);
  assert.deepEqual(releaseMsgs(release({ publishNeeds: '\n      - package' })), []);
  //  کارِ آزمونِ فقط-PR در زنجیره لازم نیست
  const prOnly = "  lint:\n    if: github.event_name == 'pull_request'\n    runs-on: ubuntu-latest\n    steps:\n      - run: npm test\n";
  assert.deepEqual(releaseMsgs(release({ extraJob: prOnly })), []);
});

test('release (S64): شاخهٔ غیرمجاز — بی شرطِ main سد است', () => {
  assert.match(releaseMsgs(release({ publishIf: "github.event_name != 'pull_request'" }))[0], /فقط از main/);
  assert.match(releaseMsgs(release({ publishIf: "github.ref != 'refs/heads/main'" }))[0], /فقط از main/);
  assert.match(releaseMsgs(release({ publishIf: 'true' }))[0], /فقط از main/);
});

test('release (S64): آزمونِ سرخ — زنجیرهٔ بی آزمون، always()، continue-on-error و کارِ آزمونِ بیرون از needs سد است', () => {
  //  انتشار مستقیم پس از ساخت، بی آزمون
  assert.match(releaseMsgs(release({ packageNeeds: '[]', testRun: 'echo hi' })).join(' | '), /هیچ گامِ آزمونِ سدکننده/);
  //  آزمون هست ولی در needs نیست
  assert.match(releaseMsgs(release({ packageNeeds: '[]' })).join(' | '), /«check» در needs ِ انتشارِ «publish» نیست/);
  //  کارِ میانی پس از آزمونِ سرخ هم می‌دود
  assert.match(releaseMsgs(release({ packageIf: "always() && github.event_name != 'pull_request'" }))[0], /always\(\)/);
  assert.match(releaseMsgs(release({ publishIf: "!cancelled() && github.ref == 'refs/heads/main'" }))[0], /always\(\)/);
  //  گام یا کارِ آزمون گزارشی است
  assert.match(releaseMsgs(release({ stepCoe: true })).join(' | '), /continue-on-error/);
  assert.match(releaseMsgs(release({ checkCoe: true })).join(' | '), /continue-on-error/);
  //  گامِ آزمونِ شرطی تنها آزمونِ زنجیره نیست
  assert.match(releaseMsgs(release({ stepIf: "github.event_name == 'pull_request'" })).join(' | '), /هیچ گامِ آزمونِ سدکننده/);
  //  کارِ آزمونِ دوم (مثلِ disk-windows ِ نصابِ کامپیوتر) که انتشار منتظرش نیست
  const side = '  disk-windows:\n    runs-on: windows-latest\n    steps:\n      - run: ./gradlew --no-daemon test --tests X\n';
  assert.match(releaseMsgs(release({ extraJob: side })).join(' | '), /«disk-windows» در needs/);
  assert.deepEqual(releaseMsgs(release({ extraJob: side, publishNeeds: '[package, disk-windows]' })), []);
});

test('release (S64): هر کارِ انتشارِ فهرست در فایلِ خودش هست؛ پوستهٔ بازنشسته فقط از main', () => {
  assert.match(releaseMsgs(release().replace('  publish:', '  ship:'), 'tohid-admin.yml')[0], /در فایل نیست/);
  const dir = path.join(path.dirname(new URL(import.meta.url).pathname), '../../.github/workflows');
  for (const key of Object.keys(RELEASE_JOBS)) assert.ok(fs.existsSync(path.join(dir, key.split(':')[0])), key);
  const old = fs.readFileSync(path.join(dir, 'android-release.yml'), 'utf8');
  assert.deepEqual(releaseMsgs(old, 'android-release.yml'), []);
  assert.match(releaseMsgs(old.replace("github.ref == 'refs/heads/main'", 'true'), 'android-release.yml')[0], /فقط از main/);
  //  S64 (۱۴۰۵/۰۸/۲۳): پوستهٔ بازنشسته دیگر استثنای «بی آزمون» ندارد — برداشتنِ needs ِ آزمون سد است
  assert.match(releaseMsgs(old.replace('    needs: test\n', ''), 'android-release.yml').join(' | '), /«test» در needs/);
  assert.match(releaseMsgs(old.replace('testReleaseUnitTest', 'assembleRelease'), 'android-release.yml').join(' | '), /هیچ گامِ آزمونِ سدکننده/);
});

test('release (OPS-01): انتشارِ مرحله‌ای فقط از main، پس از آزمون روی کپیِ موقت؛ بی‌اثر کردنش سد است', () => {
  const dir = path.join(path.dirname(new URL(import.meta.url).pathname), '../../.github/workflows');
  const yml = fs.readFileSync(path.join(dir, 'release-stage.yml'), 'utf8');
  assert.deepEqual(releaseMsgs(yml, 'release-stage.yml'), []);
  assert.deepEqual(checkWorkflow('release-stage.yml', yml), []);
  assert.match(releaseMsgs(yml.replace("github.ref == 'refs/heads/main' && ", ''), 'release-stage.yml')[0], /فقط از main/);
  assert.match(releaseMsgs(yml.replace('    needs: [verify, rehearse]\n', '    needs: verify\n'), 'release-stage.yml').join(' | '), /«rehearse» در needs/);
  assert.match(releaseMsgs(yml.replace('node --test --test-concurrency=1', 'node --version'), 'release-stage.yml').join(' | '), /هیچ گامِ آزمونِ سدکننده/);
  //  فقط کارِ publish نوشتنی است
  assert.match(checkWorkflow('release-stage.yml', yml.replace('    timeout-minutes: 45\n', '    timeout-minutes: 45\n    permissions:\n      contents: write\n'))
    .map((x) => x.msg).join(' | '), /«rehearse» مجوزِ نوشتنیِ «contents»/);
});

test('walk: متنِ بلوکِ run کلید شمرده نمی‌شود', () => {
  const rows = walk(`jobs:\n  a:\n    steps:\n      - run: |\n          permissions: write-all\n          uses: x@v1\n`);
  const keys = rows.filter((r) => !r.scalar && r.key).map((r) => r.key);
  assert.deepEqual(keys, ['jobs', 'a', 'steps', 'run']);
  assert.equal(rows.filter((r) => r.scalar).length, 2);
});

test('همهٔ ورک‌فلوهای مخزن (جز مستثناها) سدها را می‌گذرانند', () => {
  const dir = path.join(path.dirname(new URL(import.meta.url).pathname), '../../.github/workflows');
  const bad = [];
  for (const f of fs.readdirSync(dir).filter((x) => /\.ya?ml$/.test(x))) {
    if (EXCLUDED[f]) continue;
    for (const x of checkWorkflow(f, fs.readFileSync(path.join(dir, f), 'utf8'))) bad.push(`${f}:${x.line} [${x.rule}] ${x.msg}`);
  }
  assert.deepEqual(bad, []);
});
