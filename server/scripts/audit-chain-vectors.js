#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارِ مرجعِ زنجیرهٔ سابقهٔ عملیاتِ دستگاه — `core/test-vectors/audit-chain-v1.json` (STF-09).
 * همان الگوی `doc-chain-vectors.js`:
 *
 *   node scripts/audit-chain-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/audit-chain-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * ردیف‌ها همان شکلِ JSON ِ `AuditEntry` ِ برنامه‌اند (با `encodeDefaults`: `refId: null`، `notes: ""`، `amount`)
 * و با `lib/device-audit-chain.js` مُهر و حلقه می‌خورند؛ `AuditChainVectorsTest` (jvmTest ِ core) باید همان
 * جزئیات، بدنه و هش را بایت‌به‌بایت بسازد و همان نتیجهٔ سنجش را بدهد، و `device-audit-chain.test.js` همین‌ها
 * را روی سرور. ⛔ v1 پس از ادغام دست نمی‌خورد؛ شکلِ تازه ⇐ `"v": 2` و فایلِ v2.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'core', 'test-vectors', 'audit-chain-v1.json');
const hc = require('../src/lib/hash-chain');
const ac = require('../src/lib/device-audit-chain');

const DEV_A = '0f1e2d3c4b5a69788796a5b4c3d2e1f0';
const DEV_B = 'aa11bb22cc33dd44ee55ff6600778899';
const CHAIN_A = `${DEV_A}/1`;
const CHAIN_B = `${DEV_B}/1`;
const OWNER = { userId: 'usr_owner01', userRole: 'owner' };
const CASHIER = { userId: 'usr_cash02', userRole: 'cashier' };

/** ردیفِ خامِ برنامه (پیش از مُهر) — همان کلیدهای `AuditEntry` با پیش‌فرض‌های نوشته‌شده */
const raw = (id, o) => ({ id, type: 'sale', date: '2026-11-08', refId: null, notes: '', createdAt: 1762600000000, amount: 0, ...o });

function sealAll(plan) {
  const heads = {};
  return plan.map(([dev, who, row, lv]) => {
    const chain = dev === DEV_A ? CHAIN_A : CHAIN_B;
    const head = heads[chain] || { chain, seq: 0, hash: hc.GENESIS };
    const out = ac.linked(ac.stamped(row, { ...who, deviceId: dev }), head, lv || 1);
    heads[chain] = out.head;
    return out.row;
  });
}

const clone = (x) => JSON.parse(JSON.stringify(x));
const summary = (r) => ({
  ok: r.ok, legacy: r.legacy, chained: r.chained, unverified: r.unverified,
  broken: r.broken ? { chain: r.broken.chain, seq: r.broken.seq, reason: r.broken.reason } : null,
  chains: r.chains.map((c) => ({ chain: c.chain, from: c.from, seq: c.seq, head: c.head, rows: c.rows })),
});

function build() {
  //  ردیف‌های کهنهٔ بی مُهر (نسخهٔ پیشین، وب، سرور) — «نامعلوم»، بیرون از زنجیره
  const legacy = [
    raw('aud_old1', { type: 'shift_open', amount: 1500.5, createdAt: 1762500000000, date: '2026-11-07', refId: 'shf_1' }),
    raw('aud_old2', { type: 'price_change', notes: 'برنج: ۵۰ ⇐ ۵۵', refId: 'p_rice', createdAt: 1762500100000, date: '2026-11-07' }),
  ];
  const plan = [
    [DEV_A, OWNER, raw('aud_a1', { refId: 'sal_01', amount: 320, notes: 'فروش ۳۲۰' })],
    [DEV_A, CASHIER, raw('aud_a2', { type: 'cash_in', amount: 12.5, by: 'علی', reason: 'deposit', createdAt: 1762600001000 })],
    [DEV_B, CASHIER, raw('aud_b1', { type: 'no_sale', by: 'زهرا', approvedBy: 'مدیر', reason: 'پولِ خرد', createdAt: 1762600001500 })],
    //  گردکردنِ مبلغ (۱۲۳۴٫۳۴۵ ⇐ ۱۲۳۴۳۵ پول) و گزارشِ Z با عددِ اعشاری، فهرست با null، شیءِ تو در تو
    [DEV_A, OWNER, raw('aud_a3', {
      type: 'shift_close', amount: 1234.345, refId: 'shf_2', createdAt: 1762600002000,
      z: { cash: 100.25, count: 3, methods: [{ m: 'cash', v: 0.1 }, null, { m: 'card', v: 0 }], none: {} },
      refIds: ['sales/s7', 'transactions/t1'],
    })],
    [DEV_B, OWNER, raw('aud_b2', { type: 'delete_product', refId: 'p_old', notes: 'حذف — دلیل: تکراری', createdAt: 1762600003000 })],
    [DEV_A, CASHIER, raw('aud_a4', { type: 'loyalty_earn', points: 25, createdAt: 1762600004000, approval: { amount: 9007199254740993, sig: 'x' } })],
  ];
  const sealed = sealAll(plan);
  const rows = [legacy[0], ...sealed.slice(0, 3), legacy[1], ...sealed.slice(3)];

  const cases = sealed.map((row) => ({
    row,
    detail: hc.canonicalJson(ac.detail(row)),
    detailHash: ac.detailHash(row),
    doc: hc.canonicalJson(ac.doc(row)),
    hash: row.hash,
  }));

  const a = (id) => rows.findIndex((r) => r.id === id);
  const tamper = [];
  const add = (name, mutate, opts = {}) => {
    const rs = clone(rows);
    const extra = mutate(rs) || {};
    const o = { ...opts, ...extra };
    tamper.push({ name, rows: rs, ...(o.anchors ? { anchors: o.anchors } : {}), ...(o.strict ? { strict: true } : {}), ...(o.known ? { known: o.known } : {}), expect: summary(ac.verify(rs, o)) });
  };
  const headA = { chain: CHAIN_A, seq: 4, hash: sealed[5].hash };
  const anchorsA = { current: CHAIN_A, heads: { [CHAIN_A]: headA } };

  add('intact_shuffled', (rs) => { rs.reverse(); });
  add('intact_with_anchor', () => {}, { anchors: anchorsA });
  add('edit_notes', (rs) => { rs[a('aud_a2')].notes = 'دست‌خورده'; });
  add('edit_amount', (rs) => { rs[a('aud_b2')].amount = 999; });
  add('edit_user', (rs) => { rs[a('aud_a3')].userId = 'usr_cash02'; });
  add('edit_nested_double', (rs) => { rs[a('aud_a3')].z.cash = 100.26; });
  add('delete_middle', (rs) => { rs.splice(a('aud_a2'), 1); });
  add('reorder_seq', (rs) => { const x = rs[a('aud_a2')].chainSeq; rs[a('aud_a2')].chainSeq = rs[a('aud_a3')].chainSeq; rs[a('aud_a3')].chainSeq = x; });
  add('fork', (rs) => { const c = clone(rs[a('aud_a2')]); c.id = 'aud_a2x'; rs.push(c); });
  add('strip_link', (rs) => { delete rs[a('aud_a2')].hash; });
  add('unlink_middle', (rs) => { const r = rs[a('aud_a2')]; for (const k of ac.LINK) delete r[k]; });
  add('truncate_tail_no_anchor', (rs) => { rs.splice(a('aud_a4'), 1); });
  add('truncate_tail_anchor', (rs) => { rs.splice(a('aud_a4'), 1); }, { anchors: anchorsA });
  add('rewrite_tail_anchor', (rs) => {
    const i = a('aud_a4');
    rs[i].notes = 'دمِ دوباره‌ساخته';
    rs[i].hash = ac.hashOf(rs[i]);
  }, { anchors: anchorsA });
  add('all_rows_gone_anchor', (rs) => { for (let i = rs.length - 1; i >= 0; i--) if (rs[i].chain === CHAIN_A) rs.splice(i, 1); }, { anchors: anchorsA });
  add('archived_prefix', (rs) => { rs.splice(a('aud_a1'), 1); });
  add('archived_prefix_strict', (rs) => { rs.splice(a('aud_a1'), 1); }, { strict: true });

  //  سطحِ بالاترِ مدل: فیلدی که این نسخه نمی‌شناسد (پس از خواندن در برنامه می‌افتد)
  const lvRows = (() => {
    const [r1] = sealAll([[DEV_A, OWNER, raw('aud_lv1', { type: 'stock_transfer', createdAt: 1762600009000, futureField: 'x' }), 2]]);
    return [r1];
  })();
  const lvDropped = lvRows.map((r) => { const c = clone(r); delete c.futureField; return c; });
  const levels = [
    { name: 'newer_level_known', rows: lvRows, known: 2, expect: summary(ac.verify(lvRows, { known: 2 })) },
    { name: 'newer_level_dropped_field_unknown', rows: lvDropped, known: 1, expect: summary(ac.verify(lvDropped, { known: 1 })) },
    { name: 'newer_level_dropped_field_known', rows: lvDropped, known: 2, expect: summary(ac.verify(lvDropped, { known: 2 })) },
  ];

  return {
    format: ac.FORMAT,
    hashFormat: hc.FORMAT,
    version: 1,
    about: 'بردارِ مرجعِ زنجیرهٔ سابقهٔ عملیاتِ دستگاه (STF-09). ساخته‌شده با server/scripts/audit-chain-vectors.js — دستی ویرایش نشود. Kotlin (core/audit/AuditChain، AuditChainVectorsTest) و سرور (lib/device-audit-chain.js، device-audit-chain.test.js) همین جزئیات، بدنه، هش و نتیجهٔ سنجش را می‌سازند.',
    rules: {
      chain: '<deviceId>/<epoch>; seq per chain from 1; order = chainSeq, never list position or device clock',
      norm: 'safe integer stays; any other number => {"$f": 16-hex IEEE-754 bits}; then prune: null, "", 0, false, [] and {} never stay in an object (list items keep their place)',
      detail: 'every row key except id,type,date,refId,createdAt,amount,amountMinor,userId,userRole,deviceId,chain,chainSeq,prev,hash,chainV; detailHash = sha256(canonicalJson(detail))',
      doc: 'prune({ v:1, kind:"audit", chain, seq:chainSeq, lv (only if chainV > 1), id, type, date, refId, createdAt, amountMinor (amountMinor if safe integer else Math.round(amount*100)), userId, userRole, deviceId, detailHash })',
      hash: 'sha256(prev ‖ canonicalJson(doc)) — tohid-hashchain-v1',
    },
    devices: [DEV_A, DEV_B],
    chains: [CHAIN_A, CHAIN_B],
    plan: plan.map(([dev, who, row, lv]) => ({ device: dev, ...who, row, ...(lv ? { level: lv } : {}) })),
    rows,
    expect: summary(ac.verify(rows)),
    cases,
    tamper,
    levels,
  };
}

/** JSON ِ خوانا؛ هر ردیف (شیءِ دارای `id` و `type`) در یک خط تا فایل کوچک بماند */
function serialize(out) {
  const pad = (n) => '  '.repeat(n);
  const isRow = (v) => v && typeof v === 'object' && !Array.isArray(v) && 'id' in v && 'type' in v;
  const fmt = (v, d) => {
    if (v === null || typeof v !== 'object' || isRow(v)) return JSON.stringify(v);
    if (Array.isArray(v)) return v.length ? `[\n${v.map((x) => pad(d + 1) + fmt(x, d + 1)).join(',\n')}\n${pad(d)}]` : '[]';
    const ks = Object.keys(v);
    return ks.length ? `{\n${ks.map((k) => `${pad(d + 1)}${JSON.stringify(k)}: ${fmt(v[k], d + 1)}`).join(',\n')}\n${pad(d)}}` : '{}';
  };
  return fmt(out, 0) + '\n';
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
    console.log(same ? 'بردار با پیاده‌سازیِ امروز یکی است.' : 'بردار کهنه است — با --write دوباره بسازید.');
    process.exitCode = same ? 0 : 1;
  }
}
