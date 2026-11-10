'use strict';
/**
 * HW-12 — پیدا کردنِ چاپگرِ خامِ شبکه با mDNS/DNS-SD، روی بردارهای مشترکِ `core/test-vectors/mdns-v1.json`.
 *
 *   ۱. بسته‌ها را dnspython (رمزگذار/رمزگشای مستقل) ساخته و `out` ِ هر parse را خودش خوانده؛ پیاده‌سازیِ
 *      مرجعِ `mdns-ref.js` باید همان را بخواند — همان کاری که `core/hw/Mdns.kt` در `MdnsVectorsTest` می‌کند.
 *   ۲. بستهٔ بدشکل (اشارهٔ حلقه‌ای/رو به جلو، برچسبِ ناشناخته، نامِ بلندتر از ۲۵۵، دادهٔ بریده، پاسخِ خطا) هیچ است.
 *   ۳. از پاسخ‌ها فقط چاپگرِ «خام» (`_pdl-datastream._tcp`) با نشانیِ خصوصیِ RFC1918 می‌ماند.
 *   ۴. ثابت‌های `Mdns.kt` (نامِ خدمت، گروه و درگاه) همان بردارها و RFC 6762 است.
 *
 * دستگاهِ واقعی این‌جا نیست؛ این‌ها بایت را می‌پایند. پاسخ‌دهندهٔ ساختگی روی loopback در `MdnsProbeTest` (CI) است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const M = require('./mdns-ref.js');

const ROOT = path.join(__dirname, '..', '..');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'test-vectors', 'mdns-v1.json'), 'utf8'));
const bytes = (h) => Uint8Array.from(Buffer.from(h, 'hex'));
const hex = (u) => Buffer.from(u).toString('hex');

test('پرسشِ PTR ِ یک‌باره همان بایت‌های dnspython است', () => {
  assert.equal(V.service, '_pdl-datastream._tcp.local');
  for (const q of V.query) assert.equal(hex(M.query(V.service, q.id)), q.hex, String(q.id));
  //  پرسش بسته‌ای است که خودِ رمزگشا هم پرسش می‌خواند
  const back = M.parse(M.query(V.service, 0x1234));
  assert.deepEqual(back, { id: 0x1234, response: false, records: [] });
});

test('parse همان خوانشِ dnspython است (PTR، SRV، A؛ بیتِ cache-flush کنار)', () => {
  assert.ok(V.parse.length >= 8);
  for (const c of V.parse) assert.deepEqual(M.parse(bytes(c.hex)), c.out, c.name);
  //  فشرده‌سازیِ نام واقعاً به کار رفته: «Kitchen» + اشاره به نامِ خدمت (0xC00C)، و نامِ رکوردِ بعد فقط همان اشاره
  assert.ok(V.parse[1].hex.includes('074b69746368656ec00cc00c'));
});

test('بستهٔ بدشکل یا پاسخِ خطا ⇒ هیچ (null)، بی استثنا و بی حلقه', () => {
  assert.ok(V.malformed.length >= 9);
  for (const m of V.malformed) assert.equal(M.parse(bytes(m.hex)), null, m.name + ' — ' + m.why);
  //  همهٔ آن‌ها جز «پاسخِ خطا» را رمزگشای مستقل هم رد کرده است
  for (const m of V.malformed) {
    if (m.name === 'rcode-nxdomain') assert.equal(m.dnspython, 'dnspython-accepts');
    else assert.match(m.dnspython, /^dnspython-rejects/, m.name);
  }
  //  هر برش از یک بستهٔ درست: یا null یا بسته‌ای کوتاه‌تر — هرگز پرتابِ خطا
  const full = bytes(V.parse[0].hex);
  for (let n = 0; n < full.length; n++) {
    const r = M.parse(full.subarray(0, n));
    assert.ok(r === null || Array.isArray(r.records), String(n));
  }
});

test('printers: فقط چاپگرِ خام با نشانیِ خصوصی، یکتا و به ترتیبِ نام', () => {
  assert.ok(V.printers.length >= 12);
  for (const c of V.printers) {
    const messages = c.packets.map((h) => M.parse(bytes(h))).filter(Boolean);
    assert.deepEqual(M.printers(messages, V.service), c.out, c.name);
  }
  for (const ip of ['10.0.0.7', '172.16.0.1', '172.31.255.254', '192.168.1.50']) assert.ok(M.isPrivate(ip), ip);
  for (const ip of ['8.8.8.8', '100.64.1.2', '169.254.1.1', '172.32.0.1', '224.0.0.251', '127.0.0.1']) assert.ok(!M.isPrivate(ip), ip);
});

test('Mdns.kt همان نامِ خدمت، گروه و درگاهِ RFC 6762 را دارد', () => {
  const src = fs.readFileSync(path.join(ROOT, 'core', 'src', 'commonMain', 'kotlin', 'ir', 'vil3ntec', 'tohid', 'core', 'hw', 'Mdns.kt'), 'utf8');
  const one = (re) => { const m = src.match(re); assert.ok(m, String(re)); return m[1]; };
  assert.equal(one(/const val PDL = "([^"]+)"/), V.service);
  assert.equal(one(/const val GROUP = "([^"]+)"/), '224.0.0.251');
  assert.equal(one(/const val PORT = (\d+)/), '5353');
  for (const [k, v] of Object.entries(M.TYPE)) assert.equal(one(new RegExp(`const val TYPE_${k} = (\\d+)`)), String(v), k);
});
