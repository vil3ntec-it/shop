'use strict';
/**
 * SEC-02 — اسنادِ **بی‌شمارهٔ** زنجیرهٔ دستگاه (برگشت، قرض/رسید، خرید، پرداخت به تأمین‌کننده؛ `core/audit/DocChainDocs`).
 *
 *   ۱) بردارِ مرجع (`core/test-vectors/doc-chain-docs-v1.json`) با سازنده‌اش یکی است و هر حلقه‌اش با سنجشِ بدنهٔ
 *      سرور (`lib/doc-chain.bodyProblem`) پذیرفته؛ دست‌کاریِ مبلغ ⇐ `hash_mismatch`.
 *   ۲) همان شکلِ سند از درِ زندهٔ `POST /api/sync/v1/chain` (پرچمِ `shop.doc_chain` روشن) پسِ فاکتورها می‌نشیند،
 *      تأیید می‌شود و سنجشِ پنل (`hash-chain-store.verifyChain`) سالم می‌بیند — سرور نوعِ سند را محدود نمی‌کند، پس
 *      ساختن فقط سهمِ دستگاه بود. با پرچمِ خاموش هیچ ردیفی.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const h = require('./helpers');
const { query, one } = require('../src/db');
const hc = require('../src/lib/hash-chain');
const chainStore = require('../src/lib/hash-chain-store');
const docChain = require('../src/lib/doc-chain');
const vectors = require('../scripts/doc-chain-docs-vectors');
const { saleDoc } = require('../scripts/doc-chain-vectors');

const FIXTURE = JSON.parse(fs.readFileSync(vectors.FIXTURE, 'utf8'));
const DEV = 'dev-chain-docs';

let owner;
let shopId;
let series;

const H = { 'X-App': 'shop', 'X-Device': DEV };
const report = (body) => h.api('POST', '/api/sync/v1/chain', { token: owner.accessToken, headers: H, body: { device_id: DEV, format: hc.FORMAT, ...body } });

test.before(async () => {
  await h.start();
  owner = await h.newUser('صاحبِ زنجیرهٔ اسناد');
  shopId = (await h.post('/api/shop', { name: 'دکانِ زنجیرهٔ اسناد' }, { token: owner.accessToken })).body.shop.id;
  series = (await h.post('/api/shop/series', { deviceId: DEV }, { token: owner.accessToken })).body.series;
});
test.after(async () => { await h.stop(); });

test('بردارِ اسنادِ بی‌شماره: همان خروجیِ سازنده؛ هر حلقه پذیرفته؛ دست‌کاری رد', () => {
  assert.equal(vectors.serialize(vectors.build()), fs.readFileSync(vectors.FIXTURE, 'utf8'), 'node scripts/doc-chain-docs-vectors.js --write');
  const types = new Set(FIXTURE.links.map((l) => l.type));
  assert.deepEqual([...types].sort(), ['debt', 'purchase', 'return', 'sale', 'supplier_pay']);
  for (const l of FIXTURE.links) {
    assert.equal(docChain.bodyProblem(FIXTURE.chain, l), null, `حلقهٔ ${l.seq}`);
    assert.equal(l.line, ['L1', l.seq, l.prevHash, l.hash, l.type, l.ref, l.no, l.body].join('\t'));
    const doc = JSON.parse(l.body);
    //  بی دادهٔ شخصی و بی بخشِ ویرایش‌پذیر
    for (const k of ['notes', 'reason', 'name', 'phone', 'dueDate', 'invoiceNo', 'landedCost']) assert.equal(k in doc, false, `${l.ref}: ${k}`);
  }
  //  «پیش از آغاز»، شناسهٔ ناساز و تکرار کنار
  const refs = FIXTURE.links.map((l) => l.ref);
  assert.equal(refs.includes('dtx_old'), false);
  assert.equal(refs.includes('dtx,bad'), false);
  assert.equal(refs.filter((r) => r === 'spay_01').length, 1);
  const debt = FIXTURE.links.find((l) => l.ref === 'dtx_01');
  assert.equal(docChain.bodyProblem(FIXTURE.chain, { ...debt, body: debt.body.replace('"amount":75000', '"amount":75001') }), 'hash_mismatch');
});

test('پرچمِ خاموش: اسنادِ بی‌شماره هم هیچ ردیفی نمی‌سازند', async () => {
  const chain = `doc/${shopId}/${series}/1`;
  const body = hc.canonicalJson(vectors.plainDoc(chain, 1, 'u', { type: 'debt', id: 'd0', postedAt: 1, currency: 'AFN', customerId: 'c', direction: 'give', amount: 5 }));
  const link = { series, epoch: 1, seq: 1, prevHash: hc.GENESIS, hash: hc.sha256Hex(hc.GENESIS + body), body };
  const r = await report({ heads: [{ series, epoch: 1, seq: 1, hash: link.hash }], links: [link] });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { ok: true, enabled: false });
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain LIKE 'doc/%'")).n, 0);
});

test('درِ زنده: فاکتور، برگشت، قرض، خرید و پرداخت پشتِ هم می‌نشینند و سنجشِ پنل سالم است', async () => {
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain','true',1)", [shopId]);
  const chain = `doc/${shopId}/${series}/1`;
  const sale = FIXTURE.sales[0];
  const docs = FIXTURE.docs.filter((d) => FIXTURE.links.some((l) => l.ref === d.id && l.type === d.type));
  const made = [];
  let prev = hc.GENESIS;
  const add = (doc) => {
    const seq = made.length + 1;
    const body = hc.canonicalJson(doc(seq));
    const hash = hc.sha256Hex(prev + body);
    made.push({ series, epoch: 1, seq, prevHash: prev, hash, body });
    prev = hash;
  };
  add((seq) => saleDoc(chain, seq, 'usr_t', { ...sale, series }));
  const once = new Set();
  for (const d of docs) {
    if (once.has(`${d.type}:${d.id}`)) continue;
    once.add(`${d.type}:${d.id}`);
    add((seq) => vectors.plainDoc(chain, seq, 'usr_t', d));
  }
  const last = made.at(-1);
  const r = await report({ heads: [{ series, epoch: 1, seq: last.seq, hash: last.hash }], links: made });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.problems, []);
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: last.seq, hash: last.hash }]);
  const v = await chainStore.verifyChain(chain);
  assert.equal(v.ok, true, JSON.stringify(v));
  assert.equal(v.length, made.length);
  const types = (await query('SELECT body FROM hash_chain WHERE chain=$1 ORDER BY seq', [chain])).rows
    .map((x) => JSON.parse(x.body).type);
  assert.deepEqual(types, ['sale', 'return', 'return', 'return', 'debt', 'debt', 'purchase', 'purchase', 'supplier_pay']);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1", [shopId])).n, 0);
});
