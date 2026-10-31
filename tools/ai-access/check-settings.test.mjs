/**
 * SEC-AI-02 — آزمونِ `check-settings.mjs`: قاعده‌های deny ِ `.claude/settings.json` واقعاً هستند و
 * فایل‌های secret‌مانندِ گامِ ۱ (docs/security/ai-access-fa.md) را می‌پوشانند.
 *   node --test tools/ai-access/check-settings.test.mjs
 * «سرخ پیش از اصلاح»: تنظیماتِ main پیش از این تغییر (فقط هوک) رد می‌شود.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
  SETTINGS, check, isDenied, rulesOf, workspaceFiles, MUST_DENY, MUST_ALLOW,
} from './check-settings.mjs';

const real = fs.readFileSync(SETTINGS, 'utf8');
const parsed = () => JSON.parse(real);
const withDeny = (fn) => {
  const s = parsed();
  s.permissions.deny = fn(s.permissions.deny.slice());
  return JSON.stringify(s);
};
const has = (problems, re) => problems.some((p) => re.test(p));

test('تنظیماتِ همین شاخه با فایل‌های واقعیِ workspace سالم است', () => {
  assert.deepEqual(check(real, workspaceFiles()), []);
});

test('سرخ پیش از اصلاح: تنظیماتِ فقط‌هوک (main پیش از این تغییر) رد می‌شود', () => {
  const s = parsed();
  delete s.permissions;
  const problems = check(JSON.stringify(s), workspaceFiles());
  assert.ok(has(problems, /permissions\.deny نیست/), problems.join('\n'));
});

test('کلیدِ امضای ردیاب‌شده در مخزن (jks) بسته است و با برداشتنِ قاعده باز می‌شود', () => {
  const files = ['android-native/app/tohid-release.jks'];
  assert.deepEqual(check(real, files), []);
  const problems = check(withDeny((d) => d.filter((r) => !r.endsWith('(**/*.jks)'))), files);
  assert.ok(has(problems, /Read\(\*\*\/\*\.jks\) نیست/));
  assert.ok(has(problems, /بی‌پوشش است: android-native\/app\/tohid-release\.jks/));
});

test('.env.example باز می‌ماند و استثنا باید پس از .env.* بیاید', () => {
  const pats = rulesOf(parsed().permissions.deny, 'Read');
  assert.equal(isDenied('server/.env.example', pats), false);
  assert.equal(isDenied('server/.env.production', pats), true);
  //  جهش: استثنا پیش از قاعده ⇒ بی‌اثر
  const moved = withDeny((d) => {
    const i = d.indexOf('Read(!**/.env.example)');
    d.splice(i, 1);
    return ['Read(!**/.env.example)', ...d];
  });
  const problems = check(moved);
  assert.ok(has(problems, /Read\(!\*\*\/\.env\.example\) باید پس از/));
  assert.ok(has(problems, /server\/\.env\.example» بی‌جا بسته شد/));
});

test('جهش: استثنای تازه برای کلید (!*.pem) پذیرفته نمی‌شود', () => {
  const problems = check(withDeny((d) => [...d, 'Read(!**/*.pem)']));
  assert.ok(has(problems, /استثنای ناشناخته در deny: Read\(!\*\*\/\*\.pem\)/));
  assert.ok(has(problems, /keys\/a\.pem» باز است/));
});

test('جهش: Write(path) به‌جای Edit(path) بی‌اثر است و رد می‌شود', () => {
  const problems = check(withDeny((d) => d.map((r) => r.replace(/^Edit\(/, 'Write('))));
  assert.ok(has(problems, /Write\(\*\*\/\.env\)» در deny هرگز تطبیق نمی‌خورد/));
  assert.ok(has(problems, /قاعدهٔ deny ِ Edit\(\*\*\/\.env\) نیست/));
});

test('جهش: هوکِ SessionStart پاک شود ⇒ رد', () => {
  const s = parsed();
  delete s.hooks;
  assert.ok(has(check(JSON.stringify(s)), /SessionStart/));
});

test('جهش: پشتیبان و دادهٔ سرور (server/data) بی قاعده ⇒ رد', () => {
  const problems = check(withDeny((d) => d.filter((r) => !r.includes('/server/data/**'))),
    ['server/data/']);
  assert.ok(has(problems, /server\/data\/releases\/x\.json» باز است/));
  assert.ok(has(problems, /بی‌پوشش است: server\/data\//));
});

test('جهش: env/printenv بی قاعده ⇒ رد (SEC-AI-05)', () => {
  const problems = check(withDeny((d) => d.filter((r) => r !== 'Bash(printenv)')));
  assert.ok(has(problems, /Bash\(printenv\) نیست/));
});

test('فایل‌های secret‌مانندِ ساختگیِ workspace پوشش دارند؛ کد و fixture ِ ساختگی باز', () => {
  const files = ['server/.env', 'a/b/.env.local', 'ops/prod.p12', 'deep/secrets/x', 'server/data/', 'id_ed25519'];
  assert.deepEqual(check(real, files), []);
  const pats = rulesOf(parsed().permissions.deny, 'Edit');
  for (const f of MUST_DENY) assert.equal(isDenied(f, pats), true, f);
  for (const f of MUST_ALLOW) assert.equal(isDenied(f, pats), false, f);
});

test('JSON ِ خراب ⇒ رد، بی پرتابِ استثنا', () => {
  assert.ok(has(check('{ "hooks": '), /JSON ِ درست نیست/));
});
