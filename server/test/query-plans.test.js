'use strict';
/**
 * PERF-03 — نگهبانِ نقشهٔ پرس‌وجوهای پرتکرار (`scripts/query-plans.js`، `docs/PERF-QUERY-PLANS-fa.md`).
 *
 * ⛔ فقط PostgreSQL؛ روی PGlite رد می‌شود (planner و آمارِ دیگر — یکی را به دیگری تعمیم نده، §۲۹.۶ بند ۸).
 * دادهٔ کوچک (مقیاسِ ۰٫۰۲ ِ همان دادهٔ ساختگیِ ابزار) و `EXPLAIN` بی ANALYZE. روی دادهٔ کوچک planner به حق
 * Seq Scan را ارزان‌تر می‌بیند؛ پس `enable_seqscan = off`: اگر باز هم نمایهٔ چشم‌داشته به کار نرود، یعنی پرس‌وجو
 * یا نمایه چنان عوض شده که دیگر به هم نمی‌خورند (همان پسرفتی که این نگهبان برایش است). سرعت را خودِ ابزار
 * روی دادهٔ بزرگ می‌سنجد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const qp = require('../scripts/query-plans');

const skip = db.isPglite() && 'PGlite: نقشهٔ پرس‌وجو فقط روی PostgreSQL سنجیده می‌شود';
let ctx = null;

test.before(async () => {
  if (skip) return;
  await h.resetDatabase();
  ctx = await qp.seed(db.query, { scale: 0.02 });
});
test.after(async () => { await db.closeDb(); });

test('PERF-03: OPEN_STATUSES ِ خلاصهٔ سفارش دقیقاً وضعیت‌های برگه‌های باز است (پایهٔ بازنویسیِ خلاصه)', () => {
  const S = require('../src/lib/order-states');
  const { OPEN_STATUSES } = require('../src/lib/orders');
  const open = S.ORDER_STATUSES.filter((status) => [null, 0, Number.MAX_SAFE_INTEGER]
    .some((at) => S.tabOf({ status, scheduled_for: at === null ? null : 1 }, at ?? 0) !== 'done'));
  assert.deepEqual([...OPEN_STATUSES].sort(), open.sort());
  assert.match(S.tabWhere('done', '$2'), new RegExp(`NOT IN \\(${OPEN_STATUSES.map((x) => `'${x}'`).join(',')}\\)`));
});

async function inTx(fn) {
  const c = await db.getPool().connect();
  try {
    await c.query('BEGIN');
    return await fn(c);
  } finally {
    await c.query('ROLLBACK');
    c.release();
  }
}

test('PERF-03: پرس‌وجوهای کلیدی نمایهٔ خودشان را به کار می‌برند', { skip }, async () => {
  const keyed = qp.QUERIES.filter((q) => q.expect);
  assert.ok(keyed.length >= 25, `فقط ${keyed.length} پرس‌وجو با نمایهٔ چشم‌داشته`);
  const bad = await inTx(async (c) => {
    await c.query('SET LOCAL enable_seqscan = off');
    const out = [];
    for (const q of keyed) {
      const nodes = qp.nodesOf((await qp.explainOne(c, q, ctx, { analyze: false })).Plan);
      if (!qp.meets(q, nodes)) {
        out.push(`${q.id}: چشم‌داشت ${q.expect.join(' یا ')}، نقشه ${nodes.map((n) => n.type + (n.index ? `(${n.index})` : n.rel ? `(${n.rel})` : '')).join(' > ')}`);
      }
    }
    return out;
  });
  assert.deepEqual(bad, []);
});

test('PERF-03: نگهبان پسرفت را می‌بیند — پرس‌وجوی پیشینِ خلاصه/صف و نبودِ نمایه‌های ۱۹۸ رد می‌شوند', { skip }, async () => {
  const missed = await inTx(async (c) => {
    await c.query('SET LOCAL enable_seqscan = off');
    const out = [];
    for (const q of qp.QUERIES.filter((x) => x.beforeSql)) {
      const nodes = qp.nodesOf((await qp.explainOne(c, { ...q, sql: q.beforeSql, params: q.beforeParams || q.params }, ctx, { analyze: false })).Plan);
      if (!qp.meets(q, nodes)) out.push(q.id);
    }
    for (const ix of qp.newIndexes()) await c.query(`DROP INDEX ${ix}`);
    for (const q of qp.QUERIES.filter((x) => x.expect && x.expect.some((ix) => qp.newIndexes().includes(ix)))) {
      const nodes = qp.nodesOf((await qp.explainOne(c, q, ctx, { analyze: false })).Plan);
      if (!qp.meets(q, nodes)) out.push(q.id);
    }
    return out.sort();
  });
  assert.deepEqual(missed, ['orderDesk.queueOf', 'orderRisk.countOf', 'orderRisk.profile', 'orders.summary', 'promoGuard.firstOrder',
    'roleRows.v1.moved', 'roleRows.v1.taken', 'roleRows.v3.moved', 'roleRows.v3.taken', 'v3.barcodeDup', 'v3.sumMoves']);
});

test('PERF-03: هر نمایهٔ مهاجرتِ ۱۹۸ نگهبانِ پرس‌وجو دارد و هست', { skip }, async () => {
  const made = qp.newIndexes();
  assert.equal(made.length, 5, made.join(', '));
  const guarded = new Set(qp.QUERIES.flatMap((q) => q.expect || []));
  for (const ix of made) assert.ok(guarded.has(ix), `${ix} هیچ پرس‌وجوی نگهبانی ندارد`);
  const r = await db.query('SELECT indexname FROM pg_indexes WHERE schemaname = $1 AND indexname = ANY($2::text[])', ['public', made]);
  assert.deepEqual(r.rows.map((x) => x.indexname).sort(), [...made].sort());
});

test('PERF-03: خلاصهٔ سفارش و صفِ میز (بازنویسی‌شده) همان نتیجهٔ پرس‌وجوی پیشین را می‌دهند', { skip }, async () => {
  const pairs = ['orders.summary', 'orderDesk.queueOf'].map((id) => qp.QUERIES.find((q) => q.id === id));
  for (const q of pairs) {
    for (const shop of Object.values(qp.SHOPS).concat('qp-shop-none')) {
      for (const at of [ctx.now - 3 * 86_400_000, ctx.now, ctx.now + 86_400_000]) {
        const c = { ...ctx, now: at, shops: { ...ctx.shops, A: shop } };
        const now = (await db.query(q.sql, q.params(c))).rows;
        const was = (await db.query(q.beforeSql, (q.beforeParams || q.params)(c))).rows;
        assert.deepEqual(now, was, `${q.id} ${shop} ${at}`);
      }
    }
  }
  //  دادهٔ ساختگی واقعاً سفارشِ باز در هر برگه دارد (وگرنه برابری چیزی نمی‌سنجید)
  const s = (await db.query(pairs[0].sql, pairs[0].params(ctx))).rows[0];
  assert.ok(s.new > 0 && s.preparing > 0 && s.on_the_way > 0 && s.ready > 0, JSON.stringify(s));
});
