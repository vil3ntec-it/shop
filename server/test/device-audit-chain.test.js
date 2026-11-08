'use strict';
/**
 * STF-09 — زنجیرهٔ هشِ سابقهٔ عملیاتِ دستگاه روی سرور (lib/device-audit-chain.js، scripts/verify-chain.js --device-audit).
 *
 * آن‌چه این‌جا قفل می‌شود:
 *   ۱) بردارِ مشترک با Kotlin (`core/test-vectors/audit-chain-v1.json`) همان خروجیِ امروزِ کتابخانه است
 *      (جزئیات، بدنه، هش، سنجشِ همهٔ دست‌کاری‌ها) — `AuditChainVectorsTest` همین را در Kotlin می‌سنجد.
 *   ۲) پس از همگام‌سازی (رونوشتِ `sync_rows`): سالم ⇐ سالم؛ ویرایش ⇐ hash_mismatch؛ حذف (نرم) ⇐ deleted؛
 *      جابه‌جایی ⇐ prev_mismatch؛ پیشوندِ نرسیده ⇐ seq_gap (سرور سخت‌گیر است)؛ بازنویسیِ دُم پس از رسیدن ⇐ rewritten.
 *   ۳) ابزارِ CLI: کدِ ۰ سالم، ۲ شکسته؛ هیچ نوشتنی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const h = require('./helpers');
const { query } = require('../src/db');
const hc = require('../src/lib/hash-chain');
const ac = require('../src/lib/device-audit-chain');
const vectors = require('../scripts/audit-chain-vectors');
const cli = require('../scripts/verify-chain');

const SHOP = 'shp_dac_test';
const DEV = '0f1e2d3c4b5a69788796a5b4c3d2e1f0';

const file = () => JSON.parse(fs.readFileSync(vectors.FIXTURE, 'utf8'));

function chainOf(n, { dev = DEV, from = 1 } = {}) {
  let head = { chain: `${dev}/1`, seq: 0, hash: hc.GENESIS };
  const rows = [];
  for (let i = 1; i <= n; i++) {
    const raw = { id: `aud_${dev.slice(0, 4)}_${i}`, type: 'sale', date: '2026-11-08', refId: null, notes: `ردیفِ ${i}`, createdAt: 1762600000000 + i, amount: i * 10.5 };
    const out = ac.linked(ac.stamped(raw, { userId: 'usr_1', userRole: 'cashier', deviceId: dev }), head);
    head = out.head;
    if (i >= from) rows.push(out.row);
  }
  return rows;
}

/** همان شکلِ نوشتنِ `sync-v1.js`: هر فیلد با `server_seq` ِ همان op */
async function put(row, seq, { deleted = false, fieldSeq = null } = {}) {
  const fsq = fieldSeq || Object.fromEntries(Object.keys(row).map((k) => [k, seq]));
  await query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, field_seq, field_src, deleted_at, updated_seq, created_at, updated_at)
     VALUES ('shop','shop',$1,'auditLog',$2,$3::jsonb,$4::jsonb,'{}'::jsonb,$5,$6,1,1)
     ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
       data = excluded.data, field_seq = excluded.field_seq, deleted_at = excluded.deleted_at, updated_seq = excluded.updated_seq`,
    [SHOP, row.id, JSON.stringify(row), JSON.stringify(fsq), deleted ? 1 : null, seq],
  );
}

async function runCli(args) {
  const lines = [];
  const code = await cli.main(args, (s) => lines.push(s));
  return { code, out: lines.join('\n') };
}

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('۱) بردارِ مشترک همان خروجیِ کتابخانه است', () => {
  const built = vectors.build();
  assert.equal(vectors.serialize(built), fs.readFileSync(vectors.FIXTURE, 'utf8'), 'بردار کهنه است: node scripts/audit-chain-vectors.js --write');
  const v = file();
  assert.equal(v.format, ac.FORMAT);
  assert.equal(v.hashFormat, hc.FORMAT);
  for (const c of v.cases) {
    assert.equal(hc.canonicalJson(ac.detail(c.row)), c.detail, c.row.id);
    assert.equal(ac.detailHash(c.row), c.detailHash, c.row.id);
    assert.equal(hc.canonicalJson(ac.doc(c.row)), c.doc, c.row.id);
    assert.equal(ac.hashOf(c.row), c.hash, c.row.id);
  }
  assert.equal(v.expect.ok, true);
  assert.equal(v.expect.legacy, 2);
  const want = {
    edit_notes: 'hash_mismatch', edit_amount: 'hash_mismatch', edit_user: 'hash_mismatch', edit_nested_double: 'hash_mismatch',
    delete_middle: 'seq_gap', reorder_seq: 'prev_mismatch', fork: 'fork', strip_link: 'bad_link', unlink_middle: 'seq_gap',
    truncate_tail_anchor: 'truncated', rewrite_tail_anchor: 'anchor_mismatch', all_rows_gone_anchor: 'truncated',
    archived_prefix_strict: 'seq_gap',
  };
  for (const t of v.tamper) {
    assert.equal(t.expect.broken ? t.expect.broken.reason : null, want[t.name] || null, t.name);
  }
  //  بی لنگر بریدنِ دم دیده نمی‌شود (لنگرِ دستگاه یا رونوشتِ سرور لازم است)؛ پیشوندِ بایگانی روی دستگاه پذیرفته
  assert.equal(v.tamper.find((t) => t.name === 'truncate_tail_no_anchor').expect.ok, true);
  assert.equal(v.tamper.find((t) => t.name === 'archived_prefix').expect.ok, true);
  //  سطحِ تازه‌ترِ مدل: خوانندهٔ کهنه فقط پیوند را می‌سنجد؛ سرور (همهٔ فیلدها) کامل
  const lv = Object.fromEntries(v.levels.map((x) => [x.name, x.expect]));
  assert.equal(lv.newer_level_known.ok, true);
  assert.equal(lv.newer_level_dropped_field_unknown.unverified, 1);
  assert.equal(lv.newer_level_dropped_field_known.broken.reason, 'hash_mismatch');
});

test('۲) یکسان‌سازی: خالی = نبودن، عددِ اعشاری با بیت‌ها، مبلغ با Math.round', () => {
  const a = { id: 'x', type: 'sale', date: 'd', refId: null, notes: '', createdAt: 5, amount: 0 };
  const b = { id: 'x', type: 'sale', date: 'd', createdAt: 5 };
  assert.deepEqual(ac.doc(a), ac.doc(b));
  assert.deepEqual(ac.norm(12.5), { $f: '4029000000000000' });
  assert.equal(ac.norm(100), 100);
  assert.equal(ac.amountMinor({ amount: 1234.345 }), 123435);
  assert.equal(ac.amountMinor({ amount: 1, amountMinor: 250 }), 250);
  assert.notEqual(ac.detailHash({ notes: 'a' }), ac.detailHash({ notes: 'b' }));
});

test('۳) رونوشتِ همگام‌شده: سالم، بی هیچ نوشتنی', async () => {
  const rows = chainOf(4);
  for (const [i, r] of rows.entries()) await put(r, 10 + i);
  //  ردیفِ کهنهٔ بی زنجیره (وب، نسخهٔ پیشین) کنارش
  await put({ id: 'aud_legacy', type: 'sale', date: '2026-11-07', notes: 'کهنه', createdAt: 1 }, 5);
  const before = Number((await query('SELECT COUNT(*)::int AS n FROM sync_rows')).rows[0].n);
  const r = await ac.verifyShop(SHOP);
  assert.equal(r.ok, true, JSON.stringify(r.broken));
  assert.equal(r.chained, 4);
  assert.equal(r.legacy, 1);
  assert.equal(r.chains[0].head, rows[3].hash);
  const all = await ac.verifyAllShops();
  assert.deepEqual(all.results.map((x) => x.shopId), [SHOP]);
  assert.equal(Number((await query('SELECT COUNT(*)::int AS n FROM sync_rows')).rows[0].n), before);
  const out = await runCli(['--device-audit']);
  assert.equal(out.code, 0, out.out);
  assert.match(out.out, /✔ shp_dac_test/);
});

test('۴) دست‌کاری پس از همگام‌سازی دیده می‌شود: ویرایش، حذف، جابه‌جایی، بازنویسیِ دم', async () => {
  const rows = chainOf(4);
  const reset = async () => { for (const [i, r] of rows.entries()) await put(r, 10 + i); };
  const reason = async () => { const r = await ac.verifyShop(SHOP); return r.broken && [r.broken.seq, r.broken.reason]; };

  //  ویرایشِ یادداشت (op ِ ویرایش از دستگاه)
  await put({ ...rows[1], notes: 'دست‌خورده' }, 50, { fieldSeq: { ...Object.fromEntries(Object.keys(rows[1]).map((k) => [k, 11])), notes: 50 } });
  assert.deepEqual(await reason(), [2, 'hash_mismatch']);
  await reset();

  //  حذف (حذف در sync_rows نرم است: داده می‌ماند)
  await put(rows[2], 60, { deleted: true });
  assert.deepEqual(await reason(), [3, 'deleted']);
  const cliOut = await runCli(['--device-audit', SHOP]);
  assert.equal(cliOut.code, 2);
  assert.match(cliOut.out, /deleted/);
  await reset();

  //  جابه‌جاییِ شماره‌ها
  await put({ ...rows[1], chainSeq: 3 }, 70);
  await put({ ...rows[2], chainSeq: 2 }, 71);
  assert.deepEqual(await reason(), [2, 'prev_mismatch']);
  await reset();

  //  دمِ همگام‌شده دوباره ساخته شد (یادداشت و هش با هم): زنجیره در ظاهر سالم، ولی فیلدِ حلقه دیرتر نوشته شد
  const forged = { ...rows[3], notes: 'دمِ تازه' };
  forged.hash = ac.hashOf(forged);
  await put(forged, 80, { fieldSeq: { ...Object.fromEntries(Object.keys(forged).map((k) => [k, 13])), notes: 80, hash: 80 } });
  assert.deepEqual(await reason(), [4, 'rewritten']);
  await reset();

  assert.equal((await ac.verifyShop(SHOP)).ok, true);
});

test('۵) سرور سخت‌گیر است: پیشوندِ نرسیده ⇐ seq_gap در ۱؛ دو دستگاه جدا سنجیده می‌شوند', () => {
  const r = ac.verifyStored(chainOf(3, { from: 2 }).map((data) => ({ data })));
  assert.equal(r.ok, false);
  assert.deepEqual([r.broken.seq, r.broken.reason], [1, 'seq_gap']);
  const two = ac.verifyStored([...chainOf(2), ...chainOf(3, { dev: 'aa11bb22cc33dd44ee55ff6600778899' })].reverse().map((data) => ({ data })));
  assert.equal(two.ok, true);
  assert.equal(two.chains.length, 2);
  assert.equal(ac.rewrittenOf({ id: 3, hash: 3, notes: 9 }), false);
  assert.equal(ac.rewrittenOf({ id: 3, hash: 9 }), true);
  assert.equal(ac.rewrittenOf(null), false);
});
