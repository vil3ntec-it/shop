'use strict';
/**
 * دو هشدارِ CodeQL ِ #1020 (دستهٔ ۳۶) — هر دو پیش از رفع سرخ:
 *   ۲۹۰) `nextSale` ِ `lib/finalized-sales.js`: کلیدِ «__proto__» در فیلدهای op ِ دستگاه نمونهٔ فاکتورِ پس از op را
 *        عوض می‌کرد ⇒ فیلدِ ساختگی (مثلِ `cancelReason`) به ارث می‌رسید و در زنجیرهٔ لغو می‌نشست.
 *   ۲۸۹) `writeNew` ِ `scripts/phrase-backup.js`: «بررسیِ وجود سپس جابه‌جایی» ⇒ پرونده‌ای که در این میان ساخته
 *        شود رونویسی می‌شد. حالا پیوندِ سخت (EEXIST ِ اتمی) یا ساختنِ انحصاری؛ هیچ `.tmp` نمی‌ماند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');

test('۲۹۰) «__proto__» در فیلدهای op نمونهٔ فاکتور را عوض نمی‌کند و فیلدِ ساختگی ارث نمی‌رسد', () => {
  const { nextSale } = require('../src/lib/finalized-sales');
  const cur = { id: 's1', total: 100, invoiceNumber: 'A-1', status: 'completed' };
  const fields = JSON.parse('{"__proto__":{"cancelReason":"ساختگی","finalTotal":1},"status":"cancelled"}');
  const out = nextSale(cur, fields, false);
  assert.equal(Object.getPrototypeOf(out), Object.prototype);
  assert.equal(out.cancelReason, undefined);
  assert.equal(out.finalTotal, undefined);
  assert.equal(out.status, 'cancelled');
  assert.equal(out.total, 100);
  for (const k of ['constructor', 'prototype']) {
    const o = nextSale(cur, JSON.parse(`{"${k}":{"x":1},"total":5}`), false);
    assert.equal(Object.hasOwn(o, k), false);
    assert.equal(o.total, 5);
  }
});

function tmpDir() { return fs.mkdtempSync(path.join(os.tmpdir(), 'cq1020-')); }
const left = (dir) => fs.readdirSync(dir).filter((f) => f.includes('.tmp-'));

test('۲۸۹) خروجی رونویسی نمی‌شود حتی اگر بررسیِ وجود فریب بخورد؛ هیچ .tmp نمی‌ماند', () => {
  const pb = require('../scripts/phrase-backup');
  const dir = tmpDir();
  const out = path.join(dir, 'b.tbak');
  pb.writeNew(out, Buffer.from('first'));
  assert.equal(fs.readFileSync(out, 'utf8'), 'first');
  assert.equal((fs.statSync(out).mode & 0o777), 0o600);
  //  پرونده پس از «بررسی» ساخته شده: existsSync دروغ می‌گوید ⇒ کدِ کهنه rename می‌کرد و رونویسی می‌شد
  const realExists = fs.existsSync;
  fs.existsSync = () => false;
  try {
    assert.throws(() => pb.writeNew(out, Buffer.from('second')), (e) => e instanceof pb.PhraseError && e.kind === 'bad_input');
  } finally {
    fs.existsSync = realExists;
  }
  assert.equal(fs.readFileSync(out, 'utf8'), 'first');
  assert.deepEqual(left(dir), []);
});

test('۲۸۹) دیسکِ بی پیوندِ سخت (FAT/exFAT): ساختنِ انحصاری مستقیم، بی رونویسی، بی .tmp', () => {
  const pb = require('../scripts/phrase-backup');
  const dir = tmpDir();
  const out = path.join(dir, 'usb.tbak');
  const realLink = fs.linkSync;
  fs.linkSync = () => { const e = new Error('no hard links'); e.code = 'EPERM'; throw e; };
  try {
    pb.writeNew(out, Buffer.from('on-usb'));
    assert.equal(fs.readFileSync(out, 'utf8'), 'on-usb');
    assert.deepEqual(left(dir), []);
    assert.throws(() => pb.writeNew(out, Buffer.from('again')), (e) => e instanceof pb.PhraseError && e.kind === 'bad_input');
    assert.equal(fs.readFileSync(out, 'utf8'), 'on-usb');
    assert.deepEqual(left(dir), []);
  } finally {
    fs.linkSync = realLink;
  }
});
