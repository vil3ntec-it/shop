/**
 * آزمونِ نگهبانِ ورک‌فلوها. اجرا: node --test "tools/security/*.test.mjs"
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { checkWorkflow, walk, EXCLUDED } from './workflow-guard.mjs';

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
      - run: true
  publish:
    needs: build
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
