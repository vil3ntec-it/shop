#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارهای مرجعِ زنجیرهٔ هش و سندِ معکوس — `license/test-vectors/hashchain-v1.json`
 * (SEC-02، STF-09، SEC-03؛ بخشِ ۱۰.۳). همان الگوی `money-vectors.js`:
 *
 *   node scripts/hashchain-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/hashchain-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * ⛔ v1 پس از ادغام دست نمی‌خورد: زنجیره‌ای که با v1 ساخته شده باید تا ابد با
 * v1 سنجیده شود. قالبِ تازه ⇐ hashchain-v2.json و سازندهٔ تازه.
 * سرور و وب هر یک خروجی را جدا می‌سازند و این‌جا با هم مقایسه می‌شوند؛ هش‌ها
 * یک بار هم با پیاده‌سازیِ مستقلِ Python (hashlib) سنجیده شده‌اند (شرحِ PR).
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'license', 'test-vectors', 'hashchain-v1.json');
const hc = require('../src/lib/hash-chain');
const web = require(path.join(ROOT, 'license', 'hash-chain.js'));
const G = hc.GENESIS;

function build() {
  const canonicalCases = [
    ['empty object', {}],
    ['empty array', []],
    ['primitives', [null, true, false, 0, 1, -1, 9007199254740991, -9007199254740991]],
    ['key order is UTF-16 code units', { b: 1, a: 2, aa: 3, A: 4, '\u00e9': 5, '\uffff': 6, '\ud83d\ude00': 7, '10': 8, '9': 9, '': 10 }],
    ['string escapes', '"\\/\b\f\n\r\t\u0000\u001f\u007f\u2028 ok'],
    ['persian and emoji', { name: 'دکان توحید', note: '😀 ۱۲۳', unit: 'افغانی' }],
    ['nested document', { total: 10000, id: 's1', lines: [{ qty: 2, unitPrice: 5000, amount: 10000 }], meta: { z: null, a: [] } }],
    ['money in minor units', { amount: 123456789012345, change: -50 }],
  ];
  const invalidCases = [
    ['fraction', { amount: 1.5 }, 'not_integer'],
    ['beyond 2^53-1', { amount: 9007199254740992 }, 'not_integer'],
    ['huge exponent', [1e300], 'not_integer'],
    ['nested fraction', { lines: [{ amount: 0.5 }] }, 'not_integer'],
    ['lone high surrogate', '\ud800', 'lone_surrogate'],
    ['lone low surrogate', 'a\udc00b', 'lone_surrogate'],
    ['lone surrogate in key', { '\ud800': 1 }, 'lone_surrogate'],
  ];
  const sale = (id, no, extra) => Object.assign({
    id, series: 'K2', no, type: 'sale', status: 'posted', postedAt: 1760400000000 + no,
    customerId: 'c_01', currency: 'AFN',
    lines: [{ productId: 'p_rice', qty: 2, unitPrice: 5000, amount: 10000 }, { productId: 'p_oil', qty: 1, unitPrice: 12550, amount: 12550 }],
    discount: 550, total: 22000,
    payments: [{ method: 'cash', amount: 20000 }, { method: 'credit', amount: 2000 }],
  }, extra || {});

  const docs = [sale('K2-000001', 1), sale('K2-000002', 2, { note: 'یادداشت ۲' }), sale('K2-000003', 3, { customerId: null })];
  const entries = [];
  for (const d of docs) entries.push(web.append(entries, d));
  const clone = (x) => JSON.parse(JSON.stringify(x));

  const hashCases = [
    { name: 'genesis + empty object', prev: G, doc: {} },
    { name: 'genesis + sale', prev: G, doc: docs[0] },
    { name: 'linked + persian', prev: entries[0].hash, doc: { kind: 'audit', action: 'sale.void', reason: 'اشتباهِ صندوقدار', at: 1760400000123 } },
  ].map((c) => ({ ...c, canonical: hc.canonicalJson(c.doc), hash: hc.entryHash(c.prev, c.doc) }));

  const tampered = clone(entries); tampered[1].doc.total = 21000;
  const dropped = clone(entries); dropped.splice(1, 1);
  const droppedNoSeq = clone(entries).map(({ seq, ...e }) => e); droppedNoSeq.splice(1, 1);
  const forged = clone(entries); forged[2].hash = 'f'.repeat(64);
  const badGenesis = clone(entries); badGenesis[0].prevHash = '1'.repeat(64);
  const badDoc = clone(entries); badDoc[2].doc.total = 0.5;
  const verifyCases = [
    { name: 'intact chain', entries: clone(entries), ok: true, head: entries[2].hash, length: 3 },
    { name: 'document edited after posting', entries: tampered, ok: false, index: 1, reason: 'hash_mismatch' },
    { name: 'entry removed (seq present)', entries: dropped, ok: false, index: 1, reason: 'seq_gap' },
    { name: 'entry removed (no seq)', entries: droppedNoSeq, ok: false, index: 1, reason: 'prev_mismatch' },
    { name: 'stored hash forged', entries: forged, ok: false, index: 2, reason: 'hash_mismatch' },
    { name: 'first link not genesis', entries: badGenesis, ok: false, index: 0, reason: 'prev_mismatch' },
    { name: 'non-canonical document', entries: badDoc, ok: false, index: 2, reason: 'bad_doc' },
    { name: 'empty chain', entries: [], ok: true, head: G, length: 0 },
  ];
  for (const v of verifyCases) {
    const r = hc.verifyEntries(v.entries);
    const w = web.verifyEntries(v.entries);
    if (r.ok !== v.ok || (v.ok ? r.head !== v.head : (r.index !== v.index || r.reason !== v.reason))) throw new Error('verify ' + v.name + ' ' + JSON.stringify(r));
    if (JSON.stringify(r) !== JSON.stringify(w)) throw new Error('web/server verify differ ' + v.name);
  }

  const draft = { ...clone(docs[0]), status: 'draft' };
  const guardCases = [
    { name: 'new document', before: null, after: docs[0], ok: true },
    { name: 'draft may change', before: draft, after: { ...draft, total: 1 }, ok: true },
    { name: 'posted, identical in another key order', before: docs[0], after: JSON.parse(JSON.stringify(docs[0], Object.keys(docs[0]).reverse().concat(['productId', 'qty', 'unitPrice', 'amount', 'method']))), ok: true },
    { name: 'posted, total changed', before: docs[0], after: { ...docs[0], total: 21000 }, ok: false, code: 'posted_immutable' },
    { name: 'posted, cancelled in place', before: docs[0], after: { ...docs[0], status: 'cancelled' }, ok: false, code: 'posted_immutable' },
    { name: 'posted, line removed', before: docs[0], after: { ...docs[0], lines: docs[0].lines.slice(1) }, ok: false, code: 'posted_immutable' },
  ];
  for (const g of guardCases) {
    const r = web.guardEdit(g.before, g.after);
    if (r.ok !== g.ok || (r.code || undefined) !== g.code) throw new Error('guard ' + g.name);
  }

  const opts = { id: 'K2-R-000001', no: 1, postedAt: 1760400099000, reason: 'اشتباه در مقدار', by: 'u_owner' };
  const reversalCases = [
    { name: 'sale reversed', doc: docs[0], opts },
    { name: 'draft cannot be reversed', doc: draft, opts, error: 'not_posted' },
    { name: 'reversal cannot be reversed', doc: { ...docs[1], reverses: 'K2-000001' }, opts, error: 'is_reversal' },
    { name: 'only once', doc: docs[0], opts, existing: [{ id: 'K2-R-000009', reverses: 'K2-000001', status: 'posted' }], error: 'already_reversed' },
    { name: 'reason required', doc: docs[0], opts: { ...opts, reason: '  ' }, error: 'bad_opts' },
    { name: 'who reversed required', doc: docs[0], opts: { id: opts.id, no: 1, postedAt: opts.postedAt, reason: 'x' }, error: 'bad_opts' },
    { name: 'fraction in document', doc: { ...docs[0], lines: [{ productId: 'p', qty: 1, unitPrice: 5, amount: 2.5 }] }, opts, error: 'not_integer' },
    { name: 'quantity as text', doc: { ...docs[0], lines: [{ productId: 'p', qty: '2', unitPrice: 5, amount: 10 }] }, opts, error: 'not_integer' },
  ].map((c) => {
    let out;
    try {
      out = web.reverseDoc(c.doc, { ...c.opts, existing: c.existing });
    } catch (err) {
      if (!c.error || err.code !== c.error) throw err;
      return c;
    }
    if (c.error) throw new Error('expected error ' + c.name);
    return { ...c, expected: out };
  });

  const out = {
    format: hc.FORMAT,
    version: 1,
    about: 'بردارهای مرجعِ زنجیرهٔ هش (SEC-02، STF-09) و سندِ معکوس (SEC-03). ساخته‌شده با server/scripts/hashchain-vectors.js — دستی ویرایش نشود. سرور (server/src/lib/hash-chain.js)، وب (license/hash-chain.js) و Kotlin (core/audit) همه را می‌گذرانند. v1 هرگز عوض نمی‌شود؛ قالبِ تازه = hashchain-v2.json.',
    rules: {
      canonicalJson: 'RFC 8785 (JCS) subset: object keys sorted by UTF-16 code units; no whitespace; strings escaped exactly like ECMAScript JSON.stringify (\\" \\\\ \\b \\f \\n \\r \\t, other chars below U+0020 as \\u00xx lowercase, everything else literal); lone surrogates are rejected (lone_surrogate); numbers must be integers with |n| <= 2^53-1 (money always in minor units) else not_integer; -0 is written 0; JS-only undefined object properties are omitted.',
      hash: 'lowercase hex of SHA-256( ASCII(prevHash) || UTF-8(canonicalJson(doc)) ); prevHash is 64 lowercase hex chars',
      genesis: G,
      link: 'entries[0].prevHash == genesis; entries[i].prevHash == entries[i-1].hash; entries[i].seq (when present) == i+1; the first failure wins, checked per entry in the order seq_gap, prev_mismatch, bad_doc, hash_mismatch',
      reversal: 'only status=posted documents; never a document that has reverses; never twice; opts id/reason/by non-empty strings, no >= 1 integer, postedAt integer (bad_opts); the whole document must be canonical-valid; then integer values of ' + JSON.stringify(web.NEGATED_KEYS) + ' are negated at top level and inside every object of ' + JSON.stringify(web.LINE_ARRAYS) + ' (null kept, non-integer -> not_integer); unitPrice is kept; then id, no, postedAt, status=posted, reverses=<original id>, reason, by are set.',
      errorOrder: 'not_posted, is_reversal, already_reversed, bad_opts, canonical errors, not_integer from negation',
    },
    canonical: canonicalCases.map(([name, input]) => ({ name, input, output: hc.canonicalJson(input) })),
    invalid: invalidCases.map(([name, input, error]) => ({ name, input, error })),
    hash: hashCases.map(({ name, prev, doc, canonical, hash }) => ({ name, prev, doc, canonical, hash })),
    chain: { name: 'three sales in series K2', docs, entries, head: entries[2].hash },
    verify: verifyCases,
    guard: guardCases,
    reversal: reversalCases,
  };
  for (const c of out.canonical) if (web.canonicalJson(c.input) !== c.output) throw new Error('web canonical differs ' + c.name);
  for (const c of out.invalid) {
    for (const impl of [hc, web]) {
      let code = null;
      try { impl.canonicalJson(c.input); } catch (e) { code = e.code; }
      if (code !== c.error) throw new Error('invalid ' + c.name + ' got ' + code);
    }
  }
  return out;
}

function serialize(out) {
  const text = JSON.stringify(out, null, 2) + '\n';
  //  نیم‌جفتِ تنها با \u نوشته می‌شود؛ فایل باید بی‌کم‌وکاست برگردد
  if (JSON.stringify(JSON.parse(text), null, 2) + '\n' !== text) throw new Error('round trip');
  return text;
}

module.exports = { build, serialize, FIXTURE };

if (require.main === module) {
  const text = serialize(build());
  if (process.argv.includes('--write')) {
    fs.mkdirSync(path.dirname(FIXTURE), { recursive: true });
    fs.writeFileSync(FIXTURE, text);
    console.log('نوشته شد:', path.relative(process.cwd(), FIXTURE));
  } else {
    const same = fs.existsSync(FIXTURE) && fs.readFileSync(FIXTURE, 'utf8') === text;
    console.log(same ? 'بردارها با پیاده‌سازیِ امروز یکی‌اند.' : 'بردارها کهنه‌اند — با --write دوباره بسازید.');
    process.exitCode = same ? 0 : 1;
  }
}
