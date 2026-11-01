'use strict';
/**
 * SEC-02 — زنجیرهٔ هشِ اسنادِ مالیِ دستگاه، لنگرشده در سرور (`lib/doc-chain.js`، مهاجرتِ ۱۵۱).
 *
 *   ۱) پرچمِ خاموش (پیش‌فرض) ⇒ `{enabled:false}` و هیچ نوشتنی — رفتارِ امروز.
 *   ۲) بردارِ مرجعِ Kotlin (`core/test-vectors/doc-chain-v1.json`) با همین سنجشِ بدنه پذیرفته می‌شود و
 *      هر دست‌کاری کدِ خودش را دارد.
 *   ۳) حلقه‌های سالم در `hash_chain` (`doc/<دکان>/<سری>/<دوره>`) می‌نشینند و سنجشِ پنل سالم می‌بیند؛
 *      گزارشِ تکراری بی اثر؛ «جا افتاده» مشکل نیست و از تأیید ادامه می‌یابد.
 *   ۴) انشعاب، عقب‌گرد، پیوند/هش/قالبِ نادرست، سریِ دستگاهِ دیگر و دورهٔ کهنه ⇒ مشکل + **یک** رویدادِ
 *      امنیتی برای هر (مشکل، حلقه)؛ هیچ‌کدام push را نمی‌بندد.
 *   ۵) ورودیِ بدشکل ۴۰۰ (خطای برنامه، نه رویداد).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one } = require('../src/db');
const hc = require('../src/lib/hash-chain');
const chainStore = require('../src/lib/hash-chain-store');
const docChain = require('../src/lib/doc-chain');
const { saleDoc } = require('../scripts/doc-chain-vectors');

const FIXTURE = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'doc-chain-v1.json'), 'utf8'));
const DEV = 'dev-chain-a';
const OTHER = 'dev-chain-b';

let owner;
let shopId;
let series;
let otherSeries;

const H = (dev) => ({ 'X-App': 'shop', 'X-Device': dev });
const report = (body, dev = DEV, token = owner.accessToken) =>
  h.api('POST', '/api/sync/v1/chain', { token, headers: H(dev), body: { device_id: dev, ...body } });
const status = (dev = DEV) => h.api('GET', `/api/sync/v1/chain?device_id=${dev}`, { token: owner.accessToken, headers: H(dev) });

/** چند فاکتورِ ساختگیِ پشتِ سرِ هم ⇐ حلقه‌ها (همان سازندهٔ بردار) */
function linksFor(ser, epoch, from, count, { prev = hc.GENESIS, total = 1000 } = {}) {
  const chain = `doc/${shopId}/${ser}/${epoch}`;
  const out = [];
  for (let i = 0; i < count; i++) {
    const seq = from + i;
    const body = hc.canonicalJson(saleDoc(chain, seq, 'usr_t', {
      id: `sal_${ser}_${epoch}_${seq}`, series: ser, no: 100 + seq, postedAt: 1761800000000 + seq, customerId: null,
      currency: 'AFN', lines: [{ productId: 'p1', qty: 1000, unitPrice: total, amount: total }],
      subtotal: total, discount: 0, total, paid: total, method: 'cash',
    }));
    const hash = hc.sha256Hex(prev + body);
    out.push({ series: ser, epoch, seq, prevHash: prev, hash, body });
    prev = hash;
  }
  return out;
}
const head = (ser, epoch, last, extra = {}) => ({ series: ser, epoch, seq: last ? last.seq : 0, hash: last ? last.hash : hc.GENESIS, ...extra });
const body = (heads, links = []) => ({ format: hc.FORMAT, heads, links });
const events = async () => (await query(
  "SELECT target_id, detail FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1 ORDER BY created_at", [shopId]
)).rows.map((r) => ({ chain: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }));
const flagOn = () => query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain','true',1)", [shopId]);

test.before(async () => {
  await h.start();
  owner = await h.newUser('صاحبِ زنجیره');
  shopId = (await h.post('/api/shop', { name: 'دکانِ زنجیره' }, { token: owner.accessToken })).body.shop.id;
  series = (await h.post('/api/shop/series', { deviceId: DEV }, { token: owner.accessToken })).body.series;
  otherSeries = (await h.post('/api/shop/series', { deviceId: OTHER }, { token: owner.accessToken })).body.series;
  assert.match(series, /^[A-Z][1-9]$/);
  assert.notEqual(series, otherSeries);
});
test.after(async () => { await h.stop(); });

test('بردارِ Kotlin: هر حلقه پذیرفته؛ دست‌کاری با کدِ خودش رد', () => {
  for (const l of FIXTURE.links) {
    assert.equal(docChain.bodyProblem(FIXTURE.chain, l), null, `حلقهٔ ${l.seq}`);
    assert.equal(l.line, ['L1', l.seq, l.prevHash, l.hash, l.type, l.ref, l.no, l.body].join('\t'));
  }
  assert.equal(FIXTURE.links.at(-1).hash, FIXTURE.head);
  const [first] = FIXTURE.links;
  //  مبلغ عوض شد ⇐ هش نمی‌خواند
  assert.equal(docChain.bodyProblem(FIXTURE.chain, { ...first, body: first.body.replace('"total":32000', '"total":32001') }), 'hash_mismatch');
  //  فاصلهٔ اضافه ⇐ قالبِ canonical نیست
  assert.equal(docChain.bodyProblem(FIXTURE.chain, { ...first, body: first.body.replace('{', '{ ') }), 'not_canonical');
  //  حلقه در زنجیرهٔ دیگری یا جای دیگری
  assert.equal(docChain.bodyProblem('doc/shp_x/B1/1', first), 'body_mismatch');
  assert.equal(docChain.bodyProblem(FIXTURE.chain, { ...first, seq: 2 }), 'body_mismatch');
  assert.equal(docChain.bodyProblem(FIXTURE.chain, { ...first, body: 'not json' }), 'not_canonical');
});

test('پرچمِ خاموش: هیچ نوشتنی، پاسخِ {enabled:false}', async () => {
  const links = linksFor(series, 1, 1, 2);
  const g = await status();
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual(g.body, { ok: true, enabled: false });
  const r = await report(body([head(series, 1, links[1])], links));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { ok: true, enabled: false });
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain LIKE 'doc/%'")).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM device_chain_heads')).n, 0);
});

test('حلقه‌های سالم می‌نشینند؛ تکرار بی اثر؛ «جا افتاده» از تأیید ادامه می‌یابد؛ سنجشِ پنل سالم', async () => {
  await flagOn();
  const chain = `doc/${shopId}/${series}/1`;
  const all = linksFor(series, 1, 1, 6);

  let r = await report(body([head(series, 1, all[2])], all.slice(0, 3)));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: 3, hash: all[2].hash }]);
  assert.deepEqual(r.body.problems, []);

  //  همان گزارش دوباره (جوابِ گم‌شده و تلاشِ دوباره) ⇐ همان تأیید، بی ردیفِ تازه
  r = await report(body([head(series, 1, all[2])], all.slice(0, 3)));
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: 3, hash: all[2].hash }]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain=$1', [chain])).n, 3);

  //  ۵ و ۶ بی ۴ ⇐ سرور تا ۳ دارد؛ مشکل نیست
  r = await report(body([head(series, 1, all[5])], all.slice(4)));
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: 3, hash: all[2].hash }]);
  assert.deepEqual(r.body.problems, []);
  r = await report(body([head(series, 1, all[5])], all.slice(3)));
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: 6, hash: all[5].hash }]);

  const v = await chainStore.verifyChain(chain);
  assert.equal(v.ok, true, JSON.stringify(v));
  assert.equal(v.length, 6);
  assert.equal(v.head, all[5].hash);
  const row = await one('SELECT * FROM device_chain_heads WHERE shop_id=$1 AND series=$2 AND epoch=1', [shopId, series]);
  assert.equal(row.device_id, DEV);
  assert.equal(Number(row.reported_seq), 6);
  assert.equal(row.problem, '');
  assert.deepEqual(await events(), []);

  const g = await status();
  assert.equal(g.body.enabled, true);
  assert.deepEqual(g.body.series, [{ series, lastEpoch: 1, epochs: [{ epoch: 1, seq: 6, hash: all[5].hash, mine: true }] }]);
});

test('انشعاب، عقب‌گرد و حلقهٔ نادرست ⇐ مشکل و یک رویدادِ امنیتی برای هر (مشکل، حلقه)؛ حلقه نمی‌نشیند', async () => {
  const chain = `doc/${shopId}/${series}/1`;
  const all = linksFor(series, 1, 1, 6);
  const forged = linksFor(series, 1, 1, 6, { total: 999 }); // همان شماره‌ها، مبلغِ دیگر: تاریخچهٔ بازنویسی‌شده

  //  حلقهٔ ۲ ِ دیگر (تاریخچه پس از گزارش عوض شد)
  let r = await report(body([head(series, 1, forged[5])], forged.slice(1, 3)));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'fork', seq: 2 }]);
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: 6, hash: all[5].hash }]);
  r = await report(body([head(series, 1, forged[5])], forged.slice(1, 3)));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'fork', seq: 2 }]);
  let ev = await events();
  assert.equal(ev.length, 1, 'تکرارِ همان مشکل رویدادِ دوم نمی‌سازد');
  assert.equal(ev[0].problem, 'fork');
  assert.equal(ev[0].seq, 2);
  assert.equal(ev[0].series, series);
  assert.equal(ev[0].deviceId, DEV);
  assert.equal(ev[0].chain, chain);

  //  سرِ عقب‌تر از سرور (دفترِ دستگاه برگشت)
  r = await report(body([head(series, 1, all[1])]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'rollback', seq: 2 }]);
  //  همان‌جا با هشِ دیگر
  r = await report(body([head(series, 1, forged[5])]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'fork', seq: 6 }]);

  //  حلقهٔ ۷: پیوند، هش و قالبِ نادرست
  const [next] = linksFor(series, 1, 7, 1, { prev: all[5].hash });
  r = await report(body([head(series, 1, next)], [{ ...next, prevHash: forged[5].hash }]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'prev_mismatch', seq: 7 }]);
  r = await report(body([head(series, 1, next)], [{ ...next, hash: forged[0].hash }]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'hash_mismatch', seq: 7 }]);
  r = await report(body([head(series, 1, next)], [{ ...next, body: next.body.replace('{', '{ ') }]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'not_canonical', seq: 7 }]);
  const elsewhere = linksFor(otherSeries, 1, 7, 1, { prev: all[5].hash })[0];
  r = await report(body([head(series, 1, next)], [{ ...elsewhere, series }]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'body_mismatch', seq: 7 }]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain=$1', [chain])).n, 6, 'هیچ حلقهٔ نادرستی ننشست');

  //  دفترِ محلیِ شکسته (دستگاه خودش می‌گوید)
  r = await report(body([head(series, 1, all[5], { broken: 'truncated' })]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'truncated', seq: 6 }]);

  ev = await events();
  assert.deepEqual(ev.map((e) => `${e.problem}:${e.seq}`),
    ['fork:2', 'rollback:2', 'fork:6', 'prev_mismatch:7', 'hash_mismatch:7', 'not_canonical:7', 'body_mismatch:7', 'truncated:6']);
  const row = await one('SELECT problem, problem_seq FROM device_chain_heads WHERE shop_id=$1 AND series=$2 AND epoch=1', [shopId, series]);
  assert.equal(row.problem, 'truncated');

  //  زنجیرهٔ سرور دست نخورد و درست ادامه می‌یابد
  r = await report(body([head(series, 1, next)], [next]));
  assert.deepEqual(r.body.problems, []);
  assert.deepEqual(r.body.acks, [{ series, epoch: 1, seq: 7, hash: next.hash }]);
  assert.equal((await chainStore.verifyChain(chain)).ok, true);
});

test('سریِ دستگاهِ دیگر و دورهٔ کهنه ⇐ مشکل بی نوشتن؛ دورهٔ تازه رویدادِ «آغازِ دوباره»', async () => {
  //  دستگاهِ دیگر سریِ این دستگاه را گزارش می‌کند
  const theirs = linksFor(series, 1, 8, 1);
  let r = await report(body([head(series, 1, theirs[0])], theirs), OTHER);
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'series_owner', seq: 0 }]);
  assert.deepEqual(r.body.acks, []);
  r = await report(body([head(series, 1, theirs[0])], theirs), OTHER);
  assert.equal((await events()).filter((e) => e.problem === 'series_owner').length, 1);

  //  دورهٔ تازهٔ همان سری (دفترِ محلی شکست و از نو) ⇐ «restarted» یک بار
  const fresh = linksFor(series, 2, 1, 2);
  r = await report(body([head(series, 2, fresh[1])], fresh));
  assert.deepEqual(r.body.problems, []);
  assert.deepEqual(r.body.acks, [{ series, epoch: 2, seq: 2, hash: fresh[1].hash }]);
  r = await report(body([head(series, 2, fresh[1])], fresh));
  assert.equal((await events()).filter((e) => e.problem === 'restarted').length, 1);

  //  برگشت به دورهٔ ۱ ⇐ عقب‌گرد، بی نوشتن
  const [eight] = linksFor(series, 1, 8, 1, { prev: (await one(
    'SELECT hash FROM hash_chain WHERE chain=$1 AND seq=7', [`doc/${shopId}/${series}/1`]
  )).hash });
  r = await report(body([head(series, 1, eight)], [eight]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 1, problem: 'rollback', seq: 8 }]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM hash_chain WHERE chain=$1', [`doc/${shopId}/${series}/1`])).n, 7);

  //  جهشِ نامعقولِ دوره ⇐ bad_epoch، بی ردیف و بی رویداد
  const evBefore = (await events()).length;
  r = await report(body([head(series, 2 + 17, null)]));
  assert.deepEqual(r.body.problems, [{ series, epoch: 19, problem: 'bad_epoch', seq: 0 }]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM device_chain_heads WHERE shop_id=$1 AND series=$2 AND epoch=19', [shopId, series])).n, 0);
  assert.equal((await events()).length, evBefore);

  const g = await status();
  assert.equal(g.body.series[0].lastEpoch, 2);
  assert.deepEqual(g.body.series[0].epochs.map((e) => [e.epoch, e.seq]), [[1, 7], [2, 2]]);
  //  دستگاهِ دیگر فقط سریِ خودش را می‌بیند
  assert.deepEqual((await status(OTHER)).body.series.map((s) => s.series), [otherSeries]);
});

test('ورودیِ بدشکل ⇐ ۴۰۰ (بی رویداد)؛ push با زنجیرهٔ مشکل‌دار باز می‌ماند', async () => {
  const before = (await events()).length;
  const ok = linksFor(series, 2, 3, 1);
  for (const bad of [
    { format: 'x', heads: [], links: [] },
    body([{ series: 'b1', epoch: 1, seq: 0, hash: hc.GENESIS }]),
    body([head(series, 0, null)]),
    body([head(series, 2, ok[0])], [{ ...ok[0], epoch: 3 }]),
    body([head(series, 2, ok[0]), head(series, 2, ok[0])]),
    body([head(series, 2, ok[0])], Array.from({ length: docChain.MAX_LINKS + 1 }, () => ok[0])),
    body([head(series, 2, ok[0])], [{ ...ok[0], hash: 'ABC' }]),
  ]) {
    const r = await report(bad);
    assert.equal(r.status, 400, JSON.stringify(bad).slice(0, 120));
  }
  assert.equal((await events()).length, before);
  const noDevice = await h.api('POST', '/api/sync/v1/chain', { token: owner.accessToken, headers: H(DEV), body: body([]) });
  assert.equal(noDevice.status, 400);

  //  زنجیرهٔ مشکل‌دار هیچ op ای را نمی‌بندد
  const push = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken, headers: H(DEV),
    body: { device_id: DEV, schema_version: 2, ops: [{ op_id: '01JS000000000000000000CHN1', ts: Date.now(), table: 'products', row_id: 'p-chain', type: 'insert', fields: { name: 'کالا' } }] },
  });
  assert.equal(push.status, 200, JSON.stringify(push.body));
  assert.equal(push.body.results[0].status, 'applied');
});
