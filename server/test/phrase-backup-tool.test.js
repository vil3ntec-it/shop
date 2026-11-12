'use strict';
/**
 * OPS-02 — ابزارِ پشتیبانِ دستیِ رمزدار با عبارتِ بازیابی (`scripts/phrase-backup.js`)، بیرون از برنامه.
 *
 *   ۱) همان بردارِ Python ِ `PhraseBackupTest.kt` (کلید، پاکت بایت‌به‌بایت با nonce ِ ثابت) ⇒ قالب همان برنامه است.
 *   ۲) باز کردن با هر شکلِ نوشتن (خط‌تیره، کوچک، رقمِ فارسی)؛ غلطِ تایپی، عبارتِ دیگر، بیتِ برگشته، تکرارِ دیگر در KID،
 *      پاکتِ کلیدِ دستگاه و فایلِ ساده هرگز باز نمی‌شوند (همان کدهای `AtRestException`).
 *   ۳) خطِ فرمان: seal ⇐ open رفت‌وبرگشتِ بایت‌به‌بایت؛ ZIP ِ بریده رمز نمی‌شود؛ خروجی رونویسی نمی‌شود؛ عبارت نه در
 *      خروجی و نه در پیام؛ پروندهٔ خروجی فقط برای صاحبش.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const pb = require('../scripts/phrase-backup');

const SCRIPT = path.join(__dirname, '..', 'scripts', 'phrase-backup.js');
const CANONICAL = 'K7QM2Z9P4XDH6VNB3RTC8WJF2';
const SALT = Buffer.from(Array.from({ length: 16 }, (_, i) => i));
const PLAIN = Buffer.from('{"v":1,"shop":"test"}');
const VECTOR = '8954454e430d0a1a01217270312e3130303030302e41414543417751464267634943516f4c4441304f4477'
  + 'a0a1a2a3a4a5a6a7a8a9aaab77d57bca0d9a893cb71b3796bfdb701b9af6d48db1247a1e30709f9e16347826ae533989f5';
const kindOf = (fn) => { try { fn(); return null; } catch (e) { return e.kind || e.message; } };

/** ZIP ِ «ذخیره» (بی فشرده‌سازی) — همان ساختاری که برنامه می‌سازد: قلم‌ها، فهرستِ مرکزی، END */
function zip(entries) {
  const locals = [];
  const central = [];
  let at = 0;
  for (const [name, text] of entries) {
    const n = Buffer.from(name);
    const d = Buffer.from(text);
    const crc = 0;   //  `zipComplete` CRC نمی‌سنجد (همان `ZipTail`)
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt32LE(crc, 14);
    lh.writeUInt32LE(d.length, 18); lh.writeUInt32LE(d.length, 22); lh.writeUInt16LE(n.length, 26);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt32LE(crc, 16);
    ch.writeUInt32LE(d.length, 20); ch.writeUInt32LE(d.length, 24); ch.writeUInt16LE(n.length, 28); ch.writeUInt32LE(at, 42);
    locals.push(lh, n, d);
    central.push(ch, n);
    at += 30 + n.length + d.length;
  }
  const cd = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(at, 16);
  return Buffer.concat([...locals, cd, end]);
}

test('۱) کلید و پاکت همان بردارِ Python ِ برنامه‌اند (بایت‌به‌بایت)', () => {
  assert.equal(pb.deriveKey(CANONICAL, SALT, 100000).toString('hex'), '8da3a2ed25e2f04e6c85cf72419627f0d6105d04469e9926b85835d0a70b2308');
  assert.equal(pb.kidOf(SALT, 100000), 'rp1.100000.AAECAwQFBgcICQoLDA0ODw');
  const sealed = pb.seal(PLAIN, CANONICAL, { iterations: 100000, salt: SALT, nonce: Buffer.from(Array.from({ length: 12 }, (_, i) => 0xa0 + i)) });
  assert.equal(sealed.toString('hex'), VECTOR);
});

test('۲) باز کردن با هر شکلِ نوشتن؛ هر خطا همان کدِ برنامه', () => {
  const sealed = Buffer.from(VECTOR, 'hex');
  assert.equal(pb.isSealed(sealed), true);
  for (const typed of ['K7QM2-Z9P4X-DH6VN-B3RTC-8WJF2', 'k7qm2 z9p4x dh6vn b3rtc 8wjf2', 'K۷QM۲-Z۹P۴X-DH۶VN-B۳RTC-۸WJF۲']) {
    assert.deepEqual(pb.open(sealed, typed), PLAIN, typed);
  }
  //  عبارتِ دیگرِ درست‌ساخت (رقمِ وارسیِ درست) ⇒ برچسب نمی‌خواند
  const A = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
  const body = Array.from(crypto.randomBytes(24), (b) => A[b % 32]).join('');
  const other = [...A].map((c) => body + c).find((p) => pb.normalize(p));
  assert.ok(other && pb.normalize(other) !== CANONICAL);
  assert.equal(kindOf(() => pb.open(sealed, other)), 'auth_failed');
  assert.equal(kindOf(() => pb.open(sealed, 'K7QM2-Z9P4X-DH6VN-B3RTC-8WJF3')), 'key_missing', 'غلطِ تایپی ⇒ رقمِ وارسی');
  const flipped = Buffer.from(sealed); flipped[flipped.length - 20] ^= 1;
  assert.equal(kindOf(() => pb.open(flipped, CANONICAL)), 'auth_failed');
  const otherIter = Buffer.from(sealed); otherIter[10 + 'rp1.10000'.length] = '1'.charCodeAt(0);
  assert.equal(kindOf(() => pb.open(otherIter, CANONICAL)), 'auth_failed', 'KID زیرِ برچسب است');
  const device = pb.sealWith(PLAIN, 'dk-2026-01', Buffer.alloc(32));
  assert.equal(kindOf(() => pb.open(device, CANONICAL)), 'key_missing');
  assert.equal(kindOf(() => pb.open(PLAIN, CANONICAL)), 'malformed');
  assert.equal(kindOf(() => pb.open(sealed.subarray(0, 40), CANONICAL)), 'malformed');
  for (const n of [99999, 5000001]) assert.equal(kindOf(() => pb.kidOf(SALT, n)), 'bad_input');
});

test('۳) ZIP ِ کامل: END و شمارِ قلم؛ بریده سرِ مرزِ قلم ناقص', () => {
  const z = zip([['ledger.json', '{"products":[]}'], ['device/held-invoices.json', '[]']]);
  assert.deepEqual(pb.zipComplete(z), { ok: true, entries: 2 });
  assert.equal(pb.zipComplete(z.subarray(0, 30 + 'ledger.json'.length + 15)).ok, false);
  assert.equal(pb.zipComplete(z.subarray(0, z.length - 1)).ok, false);
  assert.equal(pb.zipComplete(Buffer.alloc(0)).ok, false);
});

function cli(args, phrase, { cwd } = {}) {
  return spawnSync(process.execPath, [SCRIPT, ...args], {
    input: phrase === undefined ? '' : `${phrase}\n`, encoding: 'utf8', cwd, env: { ...process.env, TOHID_PHRASE: '' },
  });
}

test('۴) خطِ فرمان: seal ⇐ open بایت‌به‌بایت، بی رونویسی، عبارت هیچ‌جا، ZIP ِ ناقص رمز نمی‌شود', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'phrase-backup-'));
  try {
    const plainZip = zip([['ledger.json', JSON.stringify({ sales: [{ id: 's1', total: 0.1 + 0.2 }] })], ['settings.json', '{}']]);
    const src = path.join(dir, 'backup.zip');
    const sealedPath = path.join(dir, 'backup.tbak');
    const back = path.join(dir, 'back.zip');
    fs.writeFileSync(src, plainZip);
    const phrase = 'k7qm2-z9p4x-dh6vn-b3rtc-8wjf2';

    let r = cli(['seal', src, sealedPath], phrase);
    assert.equal(r.status, 0, r.stderr);
    assert.ok(!`${r.stdout}${r.stderr}`.toUpperCase().includes(CANONICAL.slice(0, 10)), 'عبارت چاپ نمی‌شود');
    const sealed = fs.readFileSync(sealedPath);
    assert.equal(pb.isSealed(sealed), true);
    assert.ok(!sealed.includes(Buffer.from('ledger.json')), 'نامِ قلم‌ها هم رمز شده');
    assert.match(pb.parse(sealed).kid, /^rp1\.600000\./);
    if (process.platform !== 'win32') assert.equal(fs.statSync(sealedPath).mode & 0o077, 0, 'فقط صاحبِ فایل');

    r = cli(['check', sealedPath]);
    assert.equal(r.status, 0);
    assert.match(r.stdout, /عبارتِ بازیابی/);

    r = cli(['open', sealedPath, back], 'K7QM2 Z9P4X DH6VN B3RTC 8WJF2');
    assert.equal(r.status, 0, r.stderr);
    assert.deepEqual(fs.readFileSync(back), plainZip);
    assert.match(r.stdout, /۲|2/);

    //  خروجیِ موجود رونویسی نمی‌شود
    const before = fs.readFileSync(back);
    r = cli(['open', sealedPath, back], phrase);
    assert.equal(r.status, 1);
    assert.deepEqual(fs.readFileSync(back), before);
    //  عبارتِ نادرست ⇒ کدِ ۲ و هیچ خروجی
    r = cli(['open', sealedPath, path.join(dir, 'x.zip')], 'K7QM2-Z9P4X-DH6VN-B3RTC-8WJF3');
    assert.equal(r.status, 2);
    assert.equal(fs.existsSync(path.join(dir, 'x.zip')), false);
    //  ZIP ِ بریده رمز نمی‌شود (پشتیبانِ خراب در پاکت «سالم» به نظر می‌رسید)
    fs.writeFileSync(path.join(dir, 'cut.zip'), plainZip.subarray(0, plainZip.length - 10));
    r = cli(['seal', path.join(dir, 'cut.zip'), path.join(dir, 'cut.tbak')], phrase);
    assert.equal(r.status, 4);
    assert.equal(fs.existsSync(path.join(dir, 'cut.tbak')), false);
    //  هیچ پروندهٔ موقتی نماند
    assert.deepEqual(fs.readdirSync(dir).filter((f) => f.includes('.tmp')), []);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('۵) «پشتیبانِ فقط دفتر» ِ رمزدارِ برنامه (`….json.sealed`): open همان JSON را با کدِ ۰ می‌دهد و check آن را می‌شناسد', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'phrase-backup-'));
  try {
    const ledger = Buffer.from(JSON.stringify({ products: [{ id: 'p1', name: 'زعفرانِ هرات' }], sales: [], customers: [] }));
    const phrase = 'k7qm2-z9p4x-dh6vn-b3rtc-8wjf2';
    //  همان پاکتی که `PhraseBackup.sealing` ِ برنامه می‌سازد (بردارِ بایت‌به‌بایت در آزمونِ ۱)
    const sealedPath = path.join(dir, 'tohid-shop-backup.json.sealed');
    fs.writeFileSync(sealedPath, pb.seal(ledger, phrase, { iterations: 100000 }));
    const back = path.join(dir, 'ledger.json');
    let r = cli(['open', sealedPath, back], phrase);
    assert.equal(r.status, 0, `${r.stdout}${r.stderr}`);
    assert.match(r.stdout, /فقط دفتر/);
    assert.deepEqual(fs.readFileSync(back), ledger);
    r = cli(['check', back]);
    assert.equal(r.status, 0);
    assert.match(r.stdout, /فقط دفتر/);
    //  نه ZIP و نه JSON ِ دفتر ⇒ همان کدِ ۴ («نوشته شد تا بررسی شود»)
    const odd = path.join(dir, 'odd.sealed');
    fs.writeFileSync(odd, pb.seal(Buffer.from('[1,2,3]'), phrase, { iterations: 100000 }));
    r = cli(['open', odd, path.join(dir, 'odd.out')], phrase);
    assert.equal(r.status, 4);
    assert.equal(pb.jsonLedger(Buffer.from('{"a":1')).ok, false);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
