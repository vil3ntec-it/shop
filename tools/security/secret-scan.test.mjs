/**
 * آزمونِ اسکنِ secret. همهٔ «راز»ها ساختگی‌اند و در زمانِ اجرا سرهم می‌شوند تا خودِ این فایل
 * در اسکنِ مخزن چیزی نشان ندهد. اجرا: node --test "tools/security/*.test.mjs"
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scanLine, scanPath, allowed, addedLines, RULES } from './secret-scan.mjs';

const SCRIPT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'secret-scan.mjs');
const rnd = (n, abc = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789') => Array.from({ length: n }, (_, i) => abc[(i * 7 + 3) % abc.length]).join('');
const alnum = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

const FAKE = {
  'private-key': '-----BEGIN ' + 'RSA PRIVATE KEY-----',
  'aws-access-key': 'AKIA' + rnd(16),
  'github-token': 'gh' + 'p_' + rnd(36, alnum),
  'slack-token': 'xo' + 'xb-' + rnd(24, alnum),
  'google-api-key': 'AI' + 'za' + rnd(35, alnum),
  'stripe-live-key': 'sk' + '_live_' + rnd(24, alnum),
  'anthropic-key': 'sk-' + 'ant-api03-' + rnd(48, alnum),
  'openai-key': 'sk-' + 'proj-' + rnd(48, alnum),
  'npm-token': 'np' + 'm_' + rnd(36, alnum),
  'telegram-bot-token': '1234567' + '89:AA' + rnd(33, alnum),
  'sendgrid-key': 'S' + 'G.' + rnd(22, alnum) + '.' + rnd(43, alnum),
};

test('هر قاعدهٔ سد نمونهٔ ساختگیِ خودش را می‌گیرد', () => {
  for (const [rule, secret] of Object.entries(FAKE)) {
    const hits = scanLine(`const x = "${secret}";`);
    assert.ok(hits.some((h) => h.rule === rule && h.level === 'block'), rule);
  }
  assert.equal(RULES.filter((r) => r.level === 'block').length, Object.keys(FAKE).length);
});

test('متنِ عادی، شناسه‌ها و نمونه‌های بی‌خطر سد نمی‌شوند', () => {
  for (const line of [
    'const sha = "11d5960a326750d5838078e36cf38b85af677262";',
    'echo "$KEYSTORE_B64" | base64 -d > "$RUNNER_TEMP/tohid-release.jks"',
    'password: process.env.SMTP_PASS',
    'TOHID_KEY_PASSWORD: ${{ secrets.TOHID_KEY_PASSWORD }}',
    '-----BEGIN PUBLIC KEY-----',
    'key: AKIA_EXAMPLE',
  ]) {
    assert.deepEqual(scanLine(line).filter((h) => h.level === 'block'), [], line);
  }
});

test('خروجی مقدار ندارد؛ فقط اثرِ انگشتِ ۸ نویسه‌ای', () => {
  const [hit] = scanLine(FAKE['aws-access-key']);
  assert.match(hit.fp, /^[0-9a-f]{8}$/);
  assert.ok(!JSON.stringify(hit).includes(FAKE['aws-access-key']));
});

test('فایلِ کلید به نامش سد می‌شود؛ الگوی ‎.env.example‎ نه', () => {
  assert.equal(scanPath('android-native/app/x.jks').rule, 'keystore-file');
  assert.equal(scanPath('deploy/id_ed25519').rule, 'key-file');
  assert.equal(scanPath('server/.env').rule, 'env-file');
  assert.equal(scanPath('server/.env.production').rule, 'env-file');
  assert.equal(scanPath('server/.env.example'), null);
  assert.equal(scanPath('server/src/keys.js'), null);
});

test('استثنا: مسیر/پیشوند، قاعده، اثرِ انگشت و تاریخِ انقضا', () => {
  const allow = [
    { path: 'a/b.jks', rule: 'keystore-file', until: '2026-12-22' },
    { path: 'server/test/', rule: 'generic-assignment' },
    { path: 'x.yml', rule: 'url-credentials', fp: 'abcd1234' },
  ];
  assert.ok(allowed(allow, { file: 'a/b.jks', rule: 'keystore-file' }, '2026-12-22'));
  assert.ok(!allowed(allow, { file: 'a/b.jks', rule: 'keystore-file' }, '2026-12-23'));
  assert.ok(!allowed(allow, { file: 'a/c.jks', rule: 'keystore-file' }, '2026-11-01'));
  assert.ok(allowed(allow, { file: 'server/test/x.js', rule: 'generic-assignment' }));
  assert.ok(!allowed(allow, { file: 'server/test/x.js', rule: 'aws-access-key' }));
  assert.ok(allowed(allow, { file: 'x.yml', rule: 'url-credentials', fp: 'abcd1234' }));
  assert.ok(!allowed(allow, { file: 'x.yml', rule: 'url-credentials', fp: 'ffff0000' }));
});

test('addedLines: فقط خط‌های افزوده با شمارهٔ خطِ درست', () => {
  const diff = ['diff --git a/f.js b/f.js', '--- a/f.js', '+++ b/f.js', '@@ -1,0 +5,2 @@', '+one', '+two', '@@ -9 +12 @@', '-old', '+new', '--- a/g', '+++ /dev/null'].join('\n');
  assert.deepEqual(addedLines(diff), [
    { file: 'f.js', line: 5, text: 'one' }, { file: 'f.js', line: 6, text: 'two' }, { file: 'f.js', line: 12, text: 'new' },
  ]);
});

test('سرتاسری: --diff رازِ تازه را سد می‌کند، مقدار را چاپ نمی‌کند و خطِ حذف‌شده را نمی‌شمارد', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'secret-scan-'));
  const g = (...a) => execFileSync('git', a, { cwd: dir, encoding: 'utf8' });
  g('init', '-q'); g('config', 'user.email', 't@t'); g('config', 'user.name', 't');
  fs.mkdirSync(path.join(dir, 'tools/security'), { recursive: true });
  fs.copyFileSync(SCRIPT, path.join(dir, 'tools/security/secret-scan.mjs'));
  fs.writeFileSync(path.join(dir, 'tools/security/secret-scan-allow.json'), '{"allow":[]}');
  fs.writeFileSync(path.join(dir, 'old.js'), `const k = "${FAKE['github-token']}";\n`);
  g('add', '.'); g('commit', '-qm', 'base');
  fs.writeFileSync(path.join(dir, 'old.js'), 'const k = process.env.K;\n');
  fs.writeFileSync(path.join(dir, 'new.js'), `ok\nconst a = "${FAKE['aws-access-key']}";\n`);
  g('add', '.'); g('commit', '-qm', 'change');
  const run = () => {
    try { return { code: 0, out: execFileSync(process.execPath, ['tools/security/secret-scan.mjs', '--diff', 'HEAD~1'], { cwd: dir, encoding: 'utf8' }) }; }
    catch (e) { return { code: e.status, out: e.stdout }; }
  };
  const r = run();
  assert.equal(r.code, 1);
  assert.match(r.out, /file=new\.js,line=2::\[aws-access-key\]/);
  assert.ok(!r.out.includes(FAKE['aws-access-key']));
  assert.ok(!r.out.includes('old.js'));
  fs.writeFileSync(path.join(dir, 'new.js'), 'ok\n');
  g('add', '.'); g('commit', '-qm', 'clean');
  execFileSync('git', ['reset', '-q', '--soft', 'HEAD~2'], { cwd: dir }); g('commit', '-qm', 'squashed');
  assert.equal(run().code, 0);
  fs.rmSync(dir, { recursive: true, force: true });
});
