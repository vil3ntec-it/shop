/**
 * آزمونِ دروازهٔ محتوای خروجی‌ها و اسکنِ راز — SEC-ART-01..05.
 *   node --test tools/security/
 *
 * ⛔ هیچ رازِ واقعی یا حتی شبه‌رازِ ثابتی در این فایل نیست: کلیدها همین‌جا با `crypto` ساخته می‌شوند و
 * شکلِ توکن‌ها از تکه‌ها سرهم می‌شود، تا اسکنِ خودِ مخزن (history-scan و secret-scan ِ §۱۴) این فایل را راز نبیند.
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createGate, zipEntries, tarEntries } from './artifact-gate.mjs';
import { scanContent, scanName, looksSecret, compileAllow } from './artifact-rules.mjs';
import { scanRepo } from './history-scan.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const GATE = path.join(HERE, 'artifact-gate.mjs');
const SCAN = path.join(HERE, 'history-scan.mjs');

const pem = () => crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1',
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' },
});
const ghToken = () => ['gh', 'p_', crypto.randomBytes(27).toString('base64').replace(/[^A-Za-z0-9]/g, 'x').slice(0, 36)].join('');

function tmp(name) {
  return fs.mkdtempSync(path.join(os.tmpdir(), `art-gate-${name}-`));
}
function put(root, rel, body) {
  const p = path.join(root, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, body);
  return p;
}

/** zipِ کوچک (stored یا deflate) — فقط برای آزمون */
function crc32(buf) {
  let c = ~0;
  for (const b of buf) { c ^= b; for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1)); }
  return ~c >>> 0;
}
function zip(files, { deflate = true } = {}) {
  const locals = []; const centrals = []; let off = 0;
  for (const [name, data0] of Object.entries(files)) {
    const data = Buffer.from(data0);
    const comp = deflate ? zlib.deflateRawSync(data) : data;
    const n = Buffer.from(name);
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(deflate ? 8 : 0, 8);
    lh.writeUInt32LE(crc32(data), 14); lh.writeUInt32LE(comp.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(n.length, 26);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(deflate ? 8 : 0, 10);
    ch.writeUInt32LE(crc32(data), 16); ch.writeUInt32LE(comp.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(n.length, 28); ch.writeUInt32LE(off, 42);
    locals.push(lh, n, comp); centrals.push(ch, n);
    off += lh.length + n.length + comp.length;
  }
  const cd = Buffer.concat(centrals);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(centrals.length / 2, 8); eocd.writeUInt16LE(centrals.length / 2, 10);
  eocd.writeUInt32LE(cd.length, 12); eocd.writeUInt32LE(off, 16);
  return Buffer.concat([...locals, cd, eocd]);
}
function tar(files) {
  const parts = [];
  for (const [name, data0] of Object.entries(files)) {
    const data = Buffer.from(data0);
    const h = Buffer.alloc(512);
    h.write(name, 0, 100); h.write('0000644\0', 100); h.write(`${data.length.toString(8).padStart(11, '0')}\0`, 124);
    h.write('0', 156); h.write('ustar\0', 257); h.write('00', 263);
    h.write('        ', 148);
    let sum = 0; for (const b of h) sum += b;
    h.write(`${sum.toString(8).padStart(6, '0')}\0 `, 148);
    parts.push(h, data, Buffer.alloc((512 - (data.length % 512)) % 512));
  }
  parts.push(Buffer.alloc(1024));
  return Buffer.concat(parts);
}

function gate(target, opts = {}) {
  const g = createGate(opts);
  g.scanTarget(target);
  return g.finish().findings;
}
const rules = (fs_) => fs_.map((f) => `${f.level} ${f.rule} ${f.where}`).sort();

// ---------------------------------------------------------------- قاعده‌ها

test('کلیدِ خصوصیِ PEM با بدنه پیدا می‌شود؛ ثابتِ بی‌بدنهٔ کتابخانه‌ها نه', () => {
  const key = pem().privateKey;
  assert.deepEqual(scanContent(Buffer.from(`x\n${key}\n`)).map((f) => f.rule), ['private-key-pem']);
  //  همان کلید در رشتهٔ JSON با ‎\n‎ (مثلِ فایلِ حسابِ سرویس)
  const json = JSON.stringify({ private_key: key });
  assert.ok(scanContent(Buffer.from(json)).some((f) => f.rule === 'private-key-pem'));
  //  ثابتِ سرآیندِ PEM درونِ کلاسِ یک کتابخانه، بی بدنه (از تکه‌ها، تا اسکنرِ سرآیند این فایل را راز نبیند)
  const hdr = (w) => `-----${w} PRIV${'ATE'} KEY-----`;
  const lib = Buffer.concat([Buffer.from([0, 1, 2]), Buffer.from(`${hdr('BEGIN')}\0${hdr('END')}`), Buffer.from([0])]);
  assert.deepEqual(scanContent(lib), []);
  //  کلیدِ عمومی راز نیست
  assert.deepEqual(scanContent(Buffer.from(pem().publicKey)), []);
});

test('توکن‌های شناخته روی دودویی هم؛ انتسابِ مشکوک فقط روی متن و فقط با آنتروپیِ بالا', () => {
  const tok = ghToken();
  const bin = Buffer.concat([Buffer.from([0, 0, 7]), Buffer.from(tok), Buffer.from([0])]);
  assert.deepEqual(scanContent(bin).map((f) => f.rule), ['github-token']);
  const strong = crypto.randomBytes(24).toString('base64');
  assert.deepEqual(scanContent(Buffer.from(`const apiKey = "${strong}";\n`)).map((f) => f.rule), ['secret-assignment']);
  //  نامِ کلیدِ ذخیره، جای‌نگهدار و قالب راز نیستند
  for (const v of ['tohid-api-key-v1', '${API_SECRET}', 'change-me-please-now', 'license_private_key', 'xxxxxxxxxxxxxxxxxxxx']) {
    assert.equal(looksSecret(v), false, v);
  }
  assert.equal(looksSecret(strong), true);
  //  رمز در نشانی، نه متغیرِ محیط
  assert.deepEqual(scanContent(Buffer.from(`db = "postgres://u:${strong.replace(/[/+=]/g, 'a')}@h/x"`)).map((f) => f.rule), ['url-credentials']);
  assert.deepEqual(scanContent(Buffer.from('postgres://${U}:${P}@db:5432/x')), []);
  //  رمزِ پیش‌فرضِ امضا در اسکریپتِ ساخت
  const lit = (v) => `"${v}"`;
  assert.deepEqual(scanContent(Buffer.from(`val p = env("TOHID_KEYSTORE_PASSWORD") ?: ${lit('abc' + 'def')}\n`)).map((f) => f.rule), ['signing-password-literal']);
  assert.deepEqual(scanContent(Buffer.from('val p = env("TOHID_KEYSTORE_PASSWORD") ?: error("x")\n')), []);
  //  رمز در فرمانِ keytool؛ رمزِ همگانیِ debug و جای‌نگهدار نه
  const flag = ['-store', 'pass'].join('');
  assert.deepEqual(scanContent(Buffer.from(`keytool -list -keystore k.jks ${flag} ${'abc'}${123}x\n`)).map((f) => f.rule), ['signing-password-literal']);
  assert.deepEqual(scanContent(Buffer.from('keytool -list -storepass android -keypass android\nkeytool -storepass <رمز>\n')), []);
  //  ‎.npmrc‎ آری، ستونِ SQL نه
  assert.deepEqual(scanContent(Buffer.from(`//registry.npmjs.org/:${'_auth'}Token=${'abcdefgh'}12345678\n`)).map((f) => f.rule), ['npmrc-auth']);
  assert.deepEqual(scanContent(Buffer.from('SET push_auth = excluded.push_auth, x = 1\n')), []);
  //  خروجیِ pg_dump
  assert.deepEqual(scanContent(Buffer.from('--\n-- PostgreSQL database dump\n--\n')).map((f) => f.rule), ['db-dump']);
});

test('نام‌ها: .env (نه .env.example)، انبارِ کلید، کلیدِ خصوصی، پشتیبانِ دیتابیس', () => {
  const r = (p) => scanName(p).map((f) => f.rule);
  assert.deepEqual(r('server/.env'), ['env-file']);
  assert.deepEqual(r('.env.production'), ['env-file']);
  assert.deepEqual(r('server/.env.example'), []);
  assert.deepEqual(r('app/tohid-release.jks'), ['keystore']);
  assert.deepEqual(r('x/upload.p12'), ['keystore']);
  assert.deepEqual(r('home/.ssh/id_ed25519'), ['private-key-file']);
  assert.deepEqual(r('data/backups/shop-2026.sql.gz.enc'), ['db-backup-file']);
  assert.deepEqual(r('shop.sqlite'), ['db-backup-file']);
  assert.deepEqual(r('server/migrations/001_core.sql'), []);
  assert.deepEqual(r('runtime/lib/security/cacerts'), []);
});

test('فهرستِ مجاز بی دلیل پذیرفته نمی‌شود', () => {
  assert.throws(() => compileAllow([{ rule: 'keystore', path: '**' }]));
  assert.throws(() => compileAllow([{ rule: 'keystore', path: '**', reason: 'ok' }]));
  assert.equal(compileAllow([{ rule: 'keystore', path: 'a/*.jks', reason: 'رخدادِ ثبت‌شده، چرخش در راه' }]).length, 1);
});

// ---------------------------------------------------------------- دروازه

test('APKِ ساختگی: کلید درونِ jarِ درونِ apk با مسیرِ کامل پیدا می‌شود — بی چاپِ مقدار', () => {
  const key = pem().privateKey;
  const jar = zip({ 'META-INF/MANIFEST.MF': 'Manifest-Version: 1.0\n', 'res/k.pem': key });
  const apk = zip({ 'classes.dex': Buffer.from([0x64, 0x65, 0x78, 0, 1, 2]), 'lib/inner.jar': jar, 'assets/ok.txt': 'hello' }, { deflate: false });
  const dir = tmp('apk');
  const p = put(dir, 'app-release.apk', apk);
  assert.deepEqual(rules(gate(p, { profile: 'apk' })), ['FAIL private-key-pem app-release.apk!/lib/inner.jar!/res/k.pem']);
  //  و خطِ فرمان: خروجِ ۱، بی هیچ تکه‌ای از بدنهٔ کلید
  const run = spawnSync(process.execPath, [GATE, '--profile', 'apk', p], { encoding: 'utf8' });
  assert.equal(run.status, 1);
  const body = key.split('\n').filter((l) => l && !l.startsWith('-----')).join('');
  for (let i = 0; i + 16 <= body.length; i += 8) {
    assert.ok(!run.stdout.includes(body.slice(i, i + 16)) && !run.stderr.includes(body.slice(i, i + 16)), 'مقدارِ کلید چاپ شد');
  }
});

test('پوشهٔ برنامهٔ کامپیوتر: .env، انبارِ کلید، پشتیبان و سورسِ بک‌اند', () => {
  const dir = tmp('desk');
  put(dir, 'Tohid/app/tohid.jar', zip({ 'ir/x/Main.class': 'cafebabe' }));
  put(dir, 'Tohid/runtime/lib/security/cacerts', Buffer.from([0xfe, 0xed, 0xfe, 0xed]));
  assert.deepEqual(rules(gate(dir, { profile: 'desktop' })), []);
  put(dir, 'Tohid/app/.env', 'API_SECRET=x\n');
  put(dir, 'Tohid/app/release.keystore', Buffer.from([0xfe, 0xed]));
  put(dir, 'Tohid/app/shop-1405.sql.gz', zlib.gzipSync('--\n-- PostgreSQL database dump\n'));
  put(dir, 'Tohid/app/srv/src/app.js', "const express = require('express');\n");
  put(dir, 'Tohid/app/server/migrations/001_core.sql', 'create table x();');
  assert.deepEqual(rules(gate(dir, { profile: 'desktop' })), [
    'FAIL backend-source Tohid/app/server/migrations/',
    'FAIL backend-source Tohid/app/server/migrations/001_core.sql',
    'FAIL backend-source Tohid/app/srv/src/app.js',
    'FAIL db-backup-file Tohid/app/shop-1405.sql.gz',
    'FAIL db-dump Tohid/app/shop-1405.sql.gz!/shop-1405.sql',
    'FAIL env-file Tohid/app/.env',
    'FAIL keystore Tohid/app/release.keystore',
  ]);
});

test('سایتِ وب: .git، source map، آزمون، سند و سورسِ مخزن', () => {
  const dir = tmp('web');
  put(dir, 'index.html', '<script src="license/a.js"></script>');
  put(dir, 'license/a.js', 'var a = 1;\n');
  put(dir, 'sw.js', 'self.x = 1;');
  assert.deepEqual(rules(gate(dir, { profile: 'web' })), []);
  put(dir, '.git/HEAD', 'ref: refs/heads/main\n');
  put(dir, 'license/b.js', 'var b;\n//# sourceMappingURL=b.js.map\n');
  put(dir, 'license/b.js.map', '{"version":3,"sourcesContent":["x"]}');
  put(dir, 'license/test-vectors/money-v1.json', '{}');
  put(dir, 'docs/RULES-fa.md', '# x');
  put(dir, 'server/src/app.js', '1');
  put(dir, 'CLAUDE.md', '# x');
  assert.deepEqual(rules(gate(dir, { profile: 'web' })), [
    'FAIL backend-source server/',
    'FAIL backend-source server/src/',
    'FAIL backend-source server/src/app.js',
    'FAIL git-metadata .git/',
    'FAIL git-metadata .git/HEAD',
    'FAIL repo-internal CLAUDE.md',
    'FAIL repo-internal docs/',
    'FAIL repo-internal docs/RULES-fa.md',
    'FAIL source-map license/b.js',
    'FAIL source-map license/b.js.map',
    'FAIL test-file license/test-vectors/',
    'FAIL test-file license/test-vectors/money-v1.json',
  ]);
});

test('بایگانیِ tar.gz باز می‌شود؛ بایگانیِ ناخوانا یا خراب خودش سد است', () => {
  const dir = tmp('tar');
  put(dir, 'payload.tar.gz', zlib.gzipSync(tar({ 'app/.env.local': 'X=1', 'app/ok.txt': 'ok' })));
  put(dir, 'blob.7z', Buffer.from('7z\xbc\xaf\x27\x1c', 'latin1'));
  put(dir, 'broken.jar', Buffer.from('PK\x03\x04garbage', 'latin1'));
  assert.deepEqual(rules(gate(dir)), [
    'FAIL env-file payload.tar.gz!/app/.env.local',
    'FAIL uninspectable-archive blob.7z',
    'FAIL uninspectable-archive broken.jar',
  ]);
  assert.equal(tarEntries(tar({ a: 'b' })).length, 1);
  assert.equal(zipEntries(zip({ a: 'b', c: 'd' })).length, 2);
});

test('کلیدِ عمومیِ سنجاق‌شدهٔ مجوز: باید در خروجی باشد؛ خالی فقط هشدار', () => {
  const spki = crypto.createPublicKey(pem().privateKey).export({ type: 'spki', format: 'der' }).toString('base64');
  const dir = tmp('pin');
  const apk = put(dir, 'a.apk', zip({ 'classes.dex': Buffer.concat([Buffer.from([0, 1]), Buffer.from(spki), Buffer.from([0])]) }));
  assert.deepEqual(rules(gate(apk, { licensePin: spki })), []);
  const other = crypto.createPublicKey(pem().privateKey).export({ type: 'spki', format: 'der' }).toString('base64');
  assert.deepEqual(rules(gate(apk, { licensePin: other })), ['FAIL license-key-pin-missing (artifact)']);
  assert.deepEqual(rules(gate(apk, { licensePin: '' })), ['WARN license-key-not-pinned (artifact)']);
  const run = spawnSync(process.execPath, [GATE, '--license-pin', '', apk], { encoding: 'utf8' });
  assert.equal(run.status, 0, 'هشدار کار را قرمز نمی‌کند');
});

test('خطِ فرمان: چیزی که نیست یا خالی است پاس نمی‌شود (خروجِ ۲)', () => {
  assert.equal(spawnSync(process.execPath, [GATE, path.join(os.tmpdir(), 'no-such-artifact-xyz')]).status, 2);
  assert.equal(spawnSync(process.execPath, [GATE, tmp('empty')]).status, 2);
  assert.equal(spawnSync(process.execPath, [GATE]).status, 2);
  const dir = tmp('clean');
  put(dir, 'index.html', '<p>ok</p>');
  assert.equal(spawnSync(process.execPath, [GATE, '--profile', 'web', dir]).status, 0);
});

test('پیوندِ نمادینی که بیرون از خروجی را نشان دهد سد است', (t) => {
  const dir = tmp('link');
  put(dir, 'index.html', 'x');
  const outside = put(tmp('outside'), 'secret.txt', 'x');
  try { fs.symlinkSync(outside, path.join(dir, 'leak.txt')); } catch { t.skip('symlink در این سیستم نیست'); return; }
  assert.deepEqual(rules(gate(dir)), ['FAIL symlink leak.txt']);
});

// ---------------------------------------------------------------- اسکنِ مخزن و تاریخچه

test('اسکنِ مخزن: رازی که پاک شده در تاریخچه می‌ماند و پیدا می‌شود؛ مقدار چاپ نمی‌شود', (t) => {
  const repo = tmp('repo');
  const g = (...a) => execFileSync('git', ['-C', repo, ...a], { stdio: ['ignore', 'pipe', 'pipe'] });
  try { g('init', '-q'); } catch { t.skip('git نیست'); return; }
  g('config', 'user.email', 't@example.invalid'); g('config', 'user.name', 't'); g('config', 'commit.gpgsign', 'false');
  const key = pem().privateKey;
  put(repo, 'README.md', 'hi\n');
  put(repo, 'deploy/server.pem', key);
  put(repo, 'app/.env', 'X=1\n');
  g('add', '-A'); g('commit', '-qm', 'one');
  fs.rmSync(path.join(repo, 'deploy'), { recursive: true });
  fs.rmSync(path.join(repo, 'app/.env'));
  g('add', '-A'); g('commit', '-qm', 'two');
  return (async () => {
    const head = await scanRepo({ repo, history: false });
    assert.deepEqual(head.findings, []);
    const hist = await scanRepo({ repo, history: true });
    assert.deepEqual(hist.findings.map((f) => `${f.rule} ${f.path} ${f.inHead}`).sort(),
      ['env-file app/.env false', 'private-key-pem deploy/server.pem false']);
    const allowFile = put(tmp('allow'), 'allow.json', JSON.stringify({ allow: [] }));
    const run = spawnSync(process.execPath, [SCAN, '--history', '--repo', repo, '--allow', allowFile], { encoding: 'utf8' });
    assert.equal(run.status, 1);
    assert.match(run.stdout, /private-key-pem\thistory-only\tdeploy\/server\.pem:1\tfirst-commit=[0-9a-f]+/);
    const body = key.split('\n').filter((l) => l && !l.startsWith('-----')).join('');
    assert.ok(!run.stdout.includes(body.slice(0, 24)) && !run.stderr.includes(body.slice(0, 24)), 'مقدارِ کلید چاپ شد');
  })();
});
