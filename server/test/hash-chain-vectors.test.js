'use strict';
/**
 * قالبِ یگانهٔ زنجیرهٔ هش (SEC-02، STF-09) و سندِ معکوس (SEC-03) —
 * سرور (`src/lib/hash-chain.js`) و وب (`license/hash-chain.js`) با **همان**
 * بردارهای `license/test-vectors/hashchain-v1.json` که آزمونِ Kotlin
 * (`core/audit/HashChainVectorsTest.kt`) هم می‌خواند. بی دیتابیس.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

const ROOT = path.join(__dirname, '..', '..');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'license/test-vectors/hashchain-v1.json'), 'utf8'));
const server = require('../src/lib/hash-chain');
const web = require(path.join(ROOT, 'license/hash-chain.js'));

const IMPLS = { server, web };

test('بردارها: قالب و genesis همان است', () => {
  assert.equal(V.format, 'tohid-hashchain-v1');
  for (const impl of Object.values(IMPLS)) {
    assert.equal(impl.FORMAT, V.format);
    assert.equal(impl.GENESIS, V.rules.genesis);
  }
});

for (const [name, impl] of Object.entries(IMPLS)) {
  test(`${name}: canonicalJson همهٔ بردارها`, () => {
    for (const c of V.canonical) assert.equal(impl.canonicalJson(c.input), c.output, c.name);
    for (const c of V.invalid) {
      assert.throws(() => impl.canonicalJson(c.input), (e) => e.code === c.error, c.name);
    }
  });

  test(`${name}: −0 همان 0، و ویژگیِ undefined نوشته نمی‌شود`, () => {
    assert.equal(impl.canonicalJson({ a: -0, b: undefined }), '{"a":0}');
    assert.throws(() => impl.canonicalJson([undefined]), (e) => e.code === 'bad_value');
    assert.throws(() => impl.canonicalJson({ d: new Date(0) }), (e) => e.code === 'bad_value');
    assert.throws(() => impl.canonicalJson({ n: 10n }), (e) => e.code === 'bad_value');
  });

  test(`${name}: هشِ حلقه و زنجیرهٔ کامل`, () => {
    for (const c of V.hash) {
      assert.equal(impl.canonicalJson(c.doc), c.canonical, c.name);
      assert.equal(impl.entryHash(c.prev, c.doc), c.hash, c.name);
      //  همان فرمولِ بخشِ ۱۰.۳، با SHA-256ِ خودِ Node
      assert.equal(createHash('sha256').update(c.prev + c.canonical, 'utf8').digest('hex'), c.hash, c.name);
    }
    assert.throws(() => impl.entryHash('ABC', {}), (e) => e.code === 'bad_prev');
    let prev = V.rules.genesis;
    V.chain.docs.forEach((doc, i) => {
      const e = V.chain.entries[i];
      assert.equal(e.prevHash, prev);
      assert.equal(impl.entryHash(prev, doc), e.hash);
      prev = e.hash;
    });
    assert.equal(prev, V.chain.head);
  });

  test(`${name}: نخستین حلقهٔ شکسته پیدا می‌شود`, () => {
    for (const c of V.verify) {
      const r = impl.verifyEntries(c.entries);
      assert.equal(r.ok, c.ok, c.name);
      if (c.ok) {
        assert.equal(r.head, c.head, c.name);
        assert.equal(r.length, c.length, c.name);
      } else {
        assert.equal(r.index, c.index, c.name);
        assert.equal(r.reason, c.reason, c.name);
      }
    }
  });
}

test('وب: append همان حلقه‌های بردار را می‌سازد و آرایه را دست نمی‌زند', () => {
  const entries = [];
  for (const doc of V.chain.docs) {
    const e = web.append(entries, doc);
    entries.push(e);
  }
  assert.deepEqual(entries, V.chain.entries);
});

test('وب SEC-03: سندِ ثبت‌شده عوض نمی‌شود', () => {
  for (const c of V.guard) {
    const r = web.guardEdit(c.before, c.after);
    assert.equal(r.ok, c.ok, c.name);
    if (!c.ok) assert.equal(r.code, c.code, c.name);
  }
});

test('وب SEC-03: اصلاح فقط با سندِ معکوس', () => {
  for (const c of V.reversal) {
    const opts = { ...c.opts, existing: c.existing };
    if (c.error) {
      assert.throws(() => web.reverseDoc(c.doc, opts), (e) => e.code === c.error, c.name);
      continue;
    }
    const before = web.canonicalJson(c.doc);
    const rev = web.reverseDoc(c.doc, opts);
    assert.equal(web.canonicalJson(rev), web.canonicalJson(c.expected), c.name);
    assert.equal(web.canonicalJson(c.doc), before, 'سندِ اصلی دست نخورد');
    assert.equal(web.findReversal([c.doc, rev], c.doc.id), rev);
    //  جمعِ سند و معکوسش صفر است
    assert.equal(c.doc.total + rev.total, 0);
    assert.equal(rev.lines.reduce((s, l) => s + l.amount, 0) + c.doc.lines.reduce((s, l) => s + l.amount, 0), 0);
    assert.deepEqual(rev.lines.map((l) => l.unitPrice), c.doc.lines.map((l) => l.unitPrice));
  }
});
