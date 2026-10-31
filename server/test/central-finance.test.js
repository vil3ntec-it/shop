'use strict';
/**
 * مالیِ مرکزی — ماژولِ ۴.۵ ِ پنلِ مرکزیِ مالک (`lib/central-finance.js`، `routes/admin-finance.js`، مهاجرتِ ۱۷۱).
 *
 *   ۱) بازه‌ها: روز/هفته (از شنبه)/ماهِ خورشیدی به وقتِ کابل؛ بازهٔ بد ۴۰۰
 *   ۲) درآمد فقط از ردیفِ واقعی (sub_payments زنده + سندهای اصلاحی)؛ ارزها جدا؛ سری = جمع
 *   ۳) سندِ اصلاحی: فقط مالی/ارشد، دلیل، احرازِ دوباره (نشستِ تازه/رمز/پلِ پنل)، Idempotency-Key؛ خالص هرگز
 *      منفی؛ پرداخت دست نمی‌خورد؛ سابقه در همان تراکنش؛ جدول فقط افزودنی
 *   ۴) بدهی، مغایرت، تاریخچه (و بازنگریِ خودکارِ راهِ پیشین)، خروجیِ CSV با مجوز، درگاهِ BLOCKED-EXTERNAL
 *   ۵) جداییِ دکانِ الف و ب در هر فهرست
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

const PASS = 'Admin!12345';
const ids = {};
const DAY = 86400000;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword(PASS);
  const t = now();
  for (const [u, role] of [['malek', 'superadmin'], ['poshtiban', 'admin'], ['mali', 'finance'], ['binande', 'viewer']]) {
    ids[u] = newId('adm');
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [ids[u], u, hash, role, t]
    );
  }
});
test.after(async () => {
  delete process.env.HESABPAY_API_KEY;
  delete process.env.HESABPAY_WEBHOOK_SECRET;
  await h.stop();
});

const tokens = {};
async function tok(u) {
  if (!tokens[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    tokens[u] = r.body.token;
  }
  return tokens[u];
}
async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}
let seq = 0;
const key = () => `fin-${Date.now()}-${++seq}-key`;
const get = async (who, path) => h.get(`/api/admin/finance${path}`, { token: await tok(who) });
const adjust = async (who, body, headers = {}) =>
  h.api('POST', '/api/admin/finance/adjustments', { token: await tok(who), body, headers });
const grant = async (shopId, price) => {
  const r = await h.post('/api/admin/subscriptions', { shopId, plan: 'custom', days: 30, price }, { token: await tok('malek') });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.subscription;
};
const pay = async (tenantId, amount, extra = {}) => {
  const r = await h.post('/api/admin/payments', { app: 'shop', tenantId, amount, currency: 'AFN', method: 'cash', ...extra }, { token: await tok('malek') });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.payment;
};
const detailOf = (row) => (typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail);

const S = {};   // دکان‌ها، اشتراک‌ها و پرداخت‌های مشترکِ آزمون‌ها

/* ── ۱) بازه‌ها ─────────────────────────────────────────────────────── */

test('بازه: هفته از شنبه، ماهِ خورشیدی، روز به وقتِ کابل؛ بازهٔ بد و سطلِ بیش از سقف ۴۰۰', () => {
  const fin = require('../src/lib/central-finance');
  //  ۲۰۲۶-۱۰-۳۱ شنبه است ⇒ آغازِ هفته همان روز؛ جمعه ۲۰۲۶-۱۰-۳۰ ⇒ شنبهٔ ۲۰۲۶-۱۰-۲۴
  assert.equal(fin.bucketYmd('2026-10-31', 'week', 'persian'), '2026-10-31');
  assert.equal(fin.bucketYmd('2026-10-30', 'week', 'persian'), '2026-10-24');
  //  ۱ عقرب (آبان) ۱۴۰۵ = ۲۰۲۶-۱۰-۲۳
  assert.deepEqual(fin.persianOf('2026-10-23'), { y: 1405, m: 8, d: 1 });
  assert.equal(fin.bucketYmd('2026-10-31', 'month', 'persian'), '2026-10-23');
  assert.equal(fin.bucketYmd('2026-10-31', 'month', 'gregorian'), '2026-10-01');
  //  روزِ کابل (UTC+4:30): ۲۰:۰۰ ِ UTC ِ ۳۰ اکتبر همان ۳۱ اکتبر است
  assert.equal(fin.localYmd(Date.UTC(2026, 9, 30, 20, 0)), '2026-10-31');
  const w = fin.windowOf({ fromDate: '2026-10-01', toDate: '2026-10-31', group: 'week' });
  assert.equal(w.from, fin.dayStart('2026-10-01'));
  assert.equal(w.to, fin.dayStart('2026-11-01'));
  const b = fin.bucketsOf(w);
  assert.equal(b[0].from, w.from, 'سطلِ اول به آغازِ بازه بریده می‌شود');
  assert.equal(b[b.length - 1].to, w.to);
  for (let i = 1; i < b.length; i++) assert.equal(b[i].from, b[i - 1].to, 'سطل‌ها پشتِ هم، بی شکاف');
  const m = fin.bucketsOf(fin.windowOf({ fromDate: '2026-03-21', toDate: '2026-10-31', group: 'month' }));
  assert.equal(m[0].label, '1405/01', '۱ حمل ۱۴۰۵');
  assert.equal(m[m.length - 1].label, '1405/08');
  assert.throws(() => fin.windowOf({ fromDate: '2026-02-31' }), (e) => e.code === 'bad_window');
  assert.throws(() => fin.windowOf({ fromDate: '2026-10-10', toDate: '2026-10-01' }), (e) => e.code === 'bad_window');
  assert.throws(() => fin.bucketsOf(fin.windowOf({ fromDate: '2025-01-01', toDate: '2026-10-01', group: 'day' })), (e) => e.code === 'bad_window');
});

/* ── ۲) دادهٔ پایه و درآمد ──────────────────────────────────────────── */

test('درآمد فقط از ردیف‌های واقعی: جمعِ هر ارز، سری = جمع، بخش و روش؛ viewer می‌خواند', async () => {
  S.a = await shopOwner('مالیِ الف');
  S.b = await shopOwner('مالیِ ب');
  S.subA = await grant(S.a.shopId, 1000);
  S.subB = await grant(S.b.shopId, 500);
  S.payA = await pay(S.a.shopId, 600);
  S.payB = await pay(S.b.shopId, 500, { method: 'hawala', receiptNo: 'HW-77' });
  assert.equal(S.payA.subscriptionId, S.subA.id);

  const empty = await get('binande', '/summary?fromDate=2020-01-01&toDate=2020-01-31');
  assert.equal(empty.status, 200, JSON.stringify(empty.body));
  assert.deepEqual(empty.body.totals, {}, 'بازهٔ بی‌پرداخت ⇒ هیچ جمعی، نه صفرِ ساختگی');
  assert.ok(empty.body.series.length >= 28 && empty.body.series.every((x) => Object.keys(x.currencies).length === 0));

  const r = await get('binande', '/summary?group=day');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const afn = r.body.totals.AFN;
  assert.equal(afn.payments.count, 2);
  assert.equal(afn.payments.amount, 1100);
  assert.equal(afn.net, 1100);
  assert.ok(Number(r.body.checkedAt) > 0);
  const seriesSum = r.body.series.reduce((s, x) => s + (x.currencies.AFN?.net || 0), 0);
  assert.equal(seriesSum, 1100, 'جمعِ سری همان جمعِ کل');
  assert.deepEqual(r.body.byService, [{ app: 'shop', currency: 'AFN', payments: 1100, adjustments: 0, net: 1100, count: 2 }]);
  assert.deepEqual(r.body.byMethod.map((x) => [x.method, x.amount]).sort(), [['cash', 600], ['hawala', 500]]);

  const byShop = await get('mali', '/breakdown?by=shop&limit=1');
  assert.equal(byShop.status, 200);
  assert.equal(byShop.body.total, 2);
  assert.equal(byShop.body.rows.length, 1, 'صفحه‌بندی سمتِ سرور');
  assert.equal(byShop.body.rows[0].key, S.a.shopId);
  assert.equal(byShop.body.rows[0].name, 'دکانِ مالیِ الف');
  const byPlan = await get('mali', '/breakdown?by=plan');
  assert.deepEqual(byPlan.body.rows.map((x) => [x.key, x.net]), [['custom', 1100]]);
});

/* ── ۳) سندِ اصلاحی ───────────────────────────────────────────────── */

test('سندِ اصلاحی: نقش (مدیرِ عادی و viewer نه)، دلیل، کلیدِ یکتایی؛ پرداخت دست نمی‌خورد؛ سابقه با خالصِ پیش و پس', async () => {
  const body = { paymentId: S.payA.id, kind: 'refund', amount: 100, reason: 'بازپرداختِ بخشی به درخواستِ مشتری' };
  const weak = await adjust('poshtiban', body, { 'Idempotency-Key': key() });
  assert.equal(weak.status, 403);
  assert.equal(weak.body.error.code, 'role_forbidden');
  const view = await adjust('binande', body, { 'Idempotency-Key': key() });
  assert.equal(view.status, 403);
  const noKey = await adjust('mali', body);
  assert.equal(noKey.status, 400);
  assert.equal(noKey.body.error.code, 'idempotency_key_required');
  const noReason = await adjust('mali', { ...body, reason: ' a ' }, { 'Idempotency-Key': key() });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  const tooBig = await adjust('mali', { ...body, amount: 601 }, { 'Idempotency-Key': key() });
  assert.equal(tooBig.status, 409);
  assert.equal(tooBig.body.error.code, 'refund_exceeds_net');
  const stale = await adjust('mali', { ...body, expectedNet: 999 }, { 'Idempotency-Key': key() });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'stale_net');
  assert.equal(Number((await one('SELECT COUNT(*)::int n FROM finance_adjustments')).n), 0, 'هیچ ردِ بالا سندی نساخت');

  const k = key();
  const ok = await adjust('mali', { ...body, expectedNet: 600 }, { 'Idempotency-Key': k });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.adjustment.amount, -100);
  assert.equal(ok.body.adjustment.netBefore, 600);
  assert.equal(ok.body.adjustment.netAfter, 500);
  assert.equal(ok.body.adjustment.reauth, 'login', 'نشستِ همین حالا ساخته‌شده خودش احراز است');
  assert.match(ok.body.adjustment.docNo, /^ADJ-\d{4}-\d{5}$/);
  const again = await adjust('mali', { ...body, expectedNet: 600 }, { 'Idempotency-Key': k });
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.equal(again.body.adjustment.id, ok.body.adjustment.id);
  const reused = await adjust('mali', { ...body, amount: 50 }, { 'Idempotency-Key': k });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');

  const p = await one('SELECT amount, deleted FROM sub_payments WHERE id=$1', [S.payA.id]);
  assert.equal(Number(p.amount), 600, '⛔ خودِ پرداخت ویرایش نمی‌شود');
  const rows = await many(`SELECT * FROM audit_logs WHERE action='admin.finance_adjustment_created' AND target_id=$1`, [S.payA.id]);
  assert.equal(rows.length, 1, 'یک سند، یک ردیفِ سابقه (تکرار ردیفِ دوم نمی‌سازد)');
  const d = detailOf(rows[0]);
  assert.equal(d.reason, body.reason);
  assert.equal(d.netBefore, 600);
  assert.equal(d.netAfter, 500);
  assert.equal(rows[0].shop_id, S.a.shopId);
});

test('سندِ اصلاحی: ده درخواستِ هم‌زمان با یک کلید ⇒ یک سند', async () => {
  const k = key();
  const body = { paymentId: S.payA.id, kind: 'reference', reference: 'HW-2026-0001', reason: 'شمارهٔ حوالهٔ رسیده' };
  const all = await Promise.all(Array.from({ length: 10 }, () => adjust('mali', body, { 'Idempotency-Key': k })));
  assert.ok(all.every((r) => r.status === 201 || r.status === 200), all.map((r) => `${r.status}:${JSON.stringify(r.body).slice(0, 80)}`).join(' '));
  assert.equal(new Set(all.map((r) => r.body.adjustment.id)).size, 1);
  const n = await one(`SELECT COUNT(*)::int n FROM finance_adjustments WHERE kind='reference' AND payment_id=$1`, [S.payA.id]);
  assert.equal(n.n, 1);
  const dup = await adjust('mali', body, { 'Idempotency-Key': key() });
  assert.equal(dup.status, 409);
  assert.equal(dup.body.error.code, 'reference_exists');
});

test('سندِ اصلاحی: نشستِ کهنه ⇒ رمز می‌خواهد؛ رمزِ نادرست سابقه دارد و سقف دارد؛ رمزِ درست ⇒ ۲۰۱', async () => {
  const { hashToken } = require('../src/lib/tokens');
  const t = await tok('malek');
  await query('UPDATE tokens SET issued_at = issued_at - $2 WHERE token_hash=$1', [hashToken(t), 3600000]);
  const body = { paymentId: S.payB.id, kind: 'correction', amount: -50, reason: 'مبلغِ حواله کم‌تر رسید' };
  const noPass = await adjust('malek', body, { 'Idempotency-Key': key() });
  assert.equal(noPass.status, 403);
  assert.equal(noPass.body.error.code, 'reauth_required');
  const wrong = await adjust('malek', { ...body, password: 'nope-nope' }, { 'Idempotency-Key': key() });
  assert.equal(wrong.status, 403);
  assert.equal(wrong.body.error.code, 'reauth_failed');
  const fails = await many(`SELECT * FROM audit_logs WHERE action='admin.finance_reauth_failed' AND user_id=$1`, [ids.malek]);
  assert.equal(fails.length, 1);
  assert.ok(!JSON.stringify(fails).includes('nope-nope'), '⛔ رمز در سابقه نمی‌نشیند');
  const k = key();
  const ok = await adjust('malek', { ...body, password: PASS }, { 'Idempotency-Key': k });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.adjustment.reauth, 'password');
  assert.equal(ok.body.adjustment.netAfter, 450);
  const keyRow = await one(`SELECT * FROM idempotency_keys WHERE key=$1`, [k]);
  assert.ok(keyRow && !JSON.stringify(keyRow).includes(PASS), '⛔ رمز در ردیفِ کلیدِ یکتایی نیست');
  //  تکرار با رمزِ دیگر ⇒ همان سند (رمز جزوِ هشِ بدنه نیست)
  const again = await adjust('malek', { ...body, password: 'something-else' }, { 'Idempotency-Key': k });
  assert.equal(again.status, 200);
  assert.equal(again.body.adjustment.id, ok.body.adjustment.id);
});

test('سندِ اصلاحی از پلِ پنل (کلیدِ سرویس): زمانِ احرازِ دوبارهٔ پنل لازم و تازه؛ «انجام‌دهنده» در سند', async () => {
  const svc = require('../src/lib/admin-service');
  const made = await svc.create({ name: 'پلِ آزمون', roleCap: 'superadmin', createdBy: ids.malek });
  const hdr = (role, extra = {}) => ({
    Authorization: `Bearer ${made.key}`, 'X-Actor-Id': 'panel:1', 'X-Actor-Role': role, 'X-Actor-Name': encodeURIComponent('مالکِ پنل'),
    'Idempotency-Key': key(), ...extra,
  });
  const body = { paymentId: S.payB.id, kind: 'reversal', reason: 'پرداختِ اشتباه — حواله برگشت خورد' };
  const send = (headers) => h.api('POST', '/api/admin/finance/adjustments', { body, headers });
  const noAt = await send(hdr('superadmin'));
  assert.equal(noAt.status, 403);
  assert.equal(noAt.body.error.code, 'reauth_required');
  const old = await send(hdr('superadmin', { 'X-Actor-Reauth-At': String(now() - 3600000) }));
  assert.equal(old.status, 403);
  const plainAdmin = await send(hdr('admin', { 'X-Actor-Reauth-At': String(now()) }));
  assert.equal(plainAdmin.status, 403, 'انجام‌دهندهٔ «مدیرِ عادی» سند نمی‌زند');
  const ok = await send(hdr('finance', { 'X-Actor-Reauth-At': String(now()) }));
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.adjustment.reauth, 'panel');
  assert.equal(ok.body.adjustment.amount, -450, 'ابطال = همهٔ خالصِ همان لحظه');
  assert.equal(ok.body.adjustment.netAfter, 0);
  assert.equal(ok.body.adjustment.actor.id, 'panel:1');
  assert.equal(ok.body.adjustment.actor.name, 'مالکِ پنل');
  const twice = await send(hdr('finance', { 'X-Actor-Reauth-At': String(now()) }));
  assert.equal(twice.status, 409);
  assert.equal(twice.body.error.code, 'nothing_to_reverse');
  const refundAfter = await h.api('POST', '/api/admin/finance/adjustments', {
    body: { paymentId: S.payB.id, kind: 'refund', amount: 1, reason: 'آزمونِ پس از ابطال' },
    headers: hdr('finance', { 'X-Actor-Reauth-At': String(now()) }),
  });
  assert.equal(refundAfter.status, 409, 'خالص هرگز منفی نمی‌شود');
});

test('⛔ سندها و بازنگری‌ها فقط افزودنی‌اند: UPDATE و DELETE ِ مستقیم در پایگاه رد می‌شود', async () => {
  await assert.rejects(query(`UPDATE finance_adjustments SET amount = 0 WHERE payment_id=$1`, [S.payA.id]), /append-only/);
  await assert.rejects(query(`DELETE FROM finance_adjustments WHERE payment_id=$1`, [S.payA.id]), /append-only/);
  await query(`UPDATE sub_payments SET note='یادداشتِ مستقیم' WHERE id=$1`, [S.payA.id]);
  await assert.rejects(query(`DELETE FROM sub_payment_revisions`), /append-only/);
});

/* ── ۴) درآمد پس از سند، بدهی، جدایی ───────────────────────────────── */

test('درآمد پس از سند: اصلاح در روزِ خودش؛ خالص = پرداخت + سند؛ بدهی از همان قاعده', async () => {
  const r = await get('mali', '/summary');
  const afn = r.body.totals.AFN;
  assert.equal(afn.payments.amount, 1100);
  assert.equal(afn.refund.amount, -100);
  assert.equal(afn.correction.amount, -50);
  assert.equal(afn.reversal.amount, -450);
  assert.equal(afn.net, 500);

  const rec = await get('binande', '/receivables');
  assert.equal(rec.status, 200);
  const debts = Object.fromEntries(rec.body.rows.map((x) => [x.tenantId, x.debt]));
  assert.equal(debts[S.a.shopId], 500, '۱۰۰۰ − (۶۰۰ − ۱۰۰)');
  assert.equal(debts[S.b.shopId], 500, 'پرداختِ ابطال‌شده دیگر بدهی را نمی‌پوشاند');
  assert.deepEqual(rec.body.totals.AFN, { count: 2, debt: 1000 });

  //  ⛔ جداییِ دکان‌ها: پالایشِ یک دکان فقط همان را می‌دهد
  const onlyA = await get('mali', `/receivables?shopId=${S.a.shopId}`);
  assert.deepEqual(onlyA.body.rows.map((x) => x.tenantId), [S.a.shopId]);
  const paysA = await get('mali', `/payments?shopId=${S.a.shopId}`);
  assert.deepEqual(paysA.body.payments.map((x) => x.tenantId), [S.a.shopId]);
  assert.equal(paysA.body.payments[0].net, 500);
  assert.deepEqual(paysA.body.payments[0].references, ['HW-2026-0001']);
  const adjsB = await get('mali', `/adjustments?shopId=${S.b.shopId}`);
  assert.ok(adjsB.body.adjustments.length === 2 && adjsB.body.adjustments.every((x) => x.tenantId === S.b.shopId));
});

test('دفترِ پرداخت‌ها: نشانگرِ صفحه، جست‌وجو، پالایشِ نادرست ۴۰۰', async () => {
  const p1 = await get('mali', '/payments?limit=1');
  assert.equal(p1.body.payments.length, 1);
  assert.ok(p1.body.nextCursor);
  const p2 = await get('mali', `/payments?limit=1&cursor=${p1.body.nextCursor}`);
  assert.equal(p2.body.payments.length, 1);
  assert.notEqual(p2.body.payments[0].id, p1.body.payments[0].id);
  const q = await get('mali', '/payments?q=HW-77');
  assert.deepEqual(q.body.payments.map((x) => x.id), [S.payB.id]);
  const byRef = await get('mali', '/payments?q=HW-2026-0001');
  assert.deepEqual(byRef.body.payments.map((x) => x.id), [S.payA.id], 'جست‌وجو در شمارهٔ تراکنشِ بسته‌شده');
  assert.equal((await get('mali', '/payments?cursor=xyz')).body.error.code, 'bad_cursor');
  assert.equal((await get('mali', '/payments?method=bitcoin')).status, 400);
});

/* ── ۵) مغایرت و تاریخچه ─────────────────────────────────────────── */

test('مغایرت: بی اشتراک، دکانِ ناپیدا، ارزِ ناهمسان، رسیدِ تکراری، ویرایش/حذفِ راهِ پیشین با بازنگریِ خودکار', async () => {
  const c = await shopOwner('مغایرت');
  const loose = await pay(c.shopId, 70);                      // دکانِ بی اشتراک ⇒ پرداختِ بی اشتراک
  const sub = await grant(c.shopId, 300);
  const usd = await pay(c.shopId, 5, { currency: 'USD', subscriptionId: sub.id });
  //  دادهٔ کهنه: پرداختِ پمپی که پمپش نیست، و دو رسیدِ هم‌شماره (پیش از قیدِ یکتا)
  const t = now();
  for (const [id, rcpt] of [['pay_ghost1', 'DUP-1'], ['pay_ghost2', 'DUP-1']]) {
    await query(`INSERT INTO sub_payments (id, app, tenant_kind, tenant_id, amount, currency, method, receipt_no, paid_at, created_at)
                 VALUES ($1,'pump','station','st_nist',9,'USD','cash',$2,$3,$3)`, [id, rcpt, t]);
  }
  //  راهِ پیشینِ ارشد: ویرایش و حذف
  const put = await h.put(`/api/admin/payments/${loose.id}`, { amount: 75 }, { token: await tok('malek') });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const del = await h.del(`/api/admin/payments/${usd.id}`, { token: await tok('malek') });
  assert.equal(del.status, 200);

  const r = await get('binande', '/discrepancies');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const f = Object.fromEntries(r.body.findings.map((x) => [x.code, x]));
  assert.ok(f.payment_without_subscription.items.some((x) => x.paymentId === loose.id));
  assert.ok(f.tenant_missing.items.some((x) => x.paymentId === 'pay_ghost1'));
  assert.ok(f.subscription_missing.count === 0);
  assert.deepEqual(f.duplicate_receipt.items.map((x) => x.paymentId).sort(), ['pay_ghost1', 'pay_ghost2']);
  assert.ok(f.legacy_edited.items.some((x) => x.paymentId === loose.id));
  assert.ok(f.legacy_edited.items.some((x) => x.paymentId === S.payA.id), 'UPDATE ِ مستقیم در پایگاه هم دیده می‌شود (تریگر)');
  assert.ok(f.legacy_deleted.items.some((x) => x.paymentId === usd.id));
  assert.equal(f.currency_mismatch.count, 0, 'پرداختِ دالریِ حذف‌شده دیگر مغایرت نیست');
  assert.equal(r.body.gateway.state, 'blocked_external');
  assert.match(r.body.gateway.message, /^BLOCKED-EXTERNAL: کلیدِ حساب‌پی نیامده/);

  const det = await get('mali', `/payments/${loose.id}`);
  assert.equal(det.status, 200);
  const rev = det.body.revisions.find((x) => x.op === 'UPDATE');
  assert.ok(rev && rev.changed.includes('amount') && rev.before.amount === 70 && rev.after.amount === 75, JSON.stringify(det.body.revisions));
  assert.ok(det.body.audit.some((x) => x.action === 'admin.payment_updated'));
  const detDel = await get('mali', `/payments/${usd.id}`);
  assert.equal(detDel.body.payment.deleted, true);
  assert.ok(detDel.body.revisions.some((x) => x.changed.includes('deleted')));
  const nope = await adjust('mali', { paymentId: usd.id, kind: 'refund', amount: 1, reason: 'پرداختِ حذف‌شده' }, { 'Idempotency-Key': key() });
  assert.equal(nope.status, 409);
  assert.equal(nope.body.error.code, 'payment_deleted');
  assert.equal((await get('mali', '/payments/pay_nist')).status, 404);
});

test('تاریخچهٔ مالی: ثبت، سند، رمزِ نادرست — صفحه‌بندی با نشانگر', async () => {
  const all = await get('binande', '/history?limit=200');
  assert.equal(all.status, 200);
  const actions = new Set(all.body.entries.map((x) => x.action));
  for (const a of ['admin.payment_created', 'admin.finance_adjustment_created', 'admin.finance_reauth_failed', 'admin.payment_updated', 'admin.payment_deleted']) {
    assert.ok(actions.has(a), `${a} در تاریخچه`);
  }
  const svcRow = all.body.entries.find((x) => x.action === 'admin.finance_adjustment_created' && x.actor?.id === 'panel:1');
  assert.ok(svcRow, 'انجام‌دهندهٔ پلِ پنل در سابقه');
  const one1 = await get('binande', '/history?limit=1');
  const two = await get('binande', `/history?limit=1&cursor=${one1.body.nextCursor}`);
  assert.notEqual(two.body.entries[0].id, one1.body.entries[0].id);
  const forA = await get('binande', `/history?targetId=${S.payA.id}`);
  assert.ok(forA.body.entries.every((x) => x.targetId === S.payA.id));
});

/* ── ۶) خروجیِ CSV ───────────────────────────────────────────────── */

test('خروجیِ CSV: فقط مالی/ارشد؛ صفحه‌به‌صفحه؛ BOM؛ خنثیِ فرمول؛ ردیفِ سابقه', async () => {
  for (const who of ['binande', 'poshtiban']) {
    const r = await get(who, '/export');
    assert.equal(r.status, 403, `${who} ⇒ ${r.status}`);
  }
  const d = await shopOwner('فرمول');
  await grant(d.shopId, 10);
  await pay(d.shopId, 10, { receiptNo: '=HYPERLINK("x")' });
  const r = await get('mali', '/export?dataset=payments&limit=2');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.csv.startsWith('﻿payment_id,receipt_no,'));
  assert.equal(r.body.rows, 2);
  assert.ok(r.body.nextCursor, 'بیش از یک صفحه ⇒ نشانگرِ بعدی');
  const all = await get('mali', '/export?dataset=payments');
  assert.ok(all.body.csv.includes(`"'=HYPERLINK(""x"")"`), all.body.csv.slice(0, 600));
  const adj = await get('malek', '/export?dataset=adjustments');
  assert.equal(adj.status, 200);
  assert.ok(adj.body.csv.includes('ADJ-'));
  const logs = await many(`SELECT * FROM audit_logs WHERE action='admin.finance_exported' ORDER BY created_at`);
  assert.equal(logs.length, 3);
  assert.equal(detailOf(logs[0]).rows, 2);
});

/* ── ۷) در انتظار و درگاه ─────────────────────────────────────────── */

test('در انتظار: درخواستِ خرید با قیمتِ برآوردیِ پلن؛ «ناموفق» ِ پرداختِ دستی گفته می‌شود، نه صفر', async () => {
  const e = await shopOwner('خریدار');
  await query(`INSERT INTO plans (id, code, app, title, amount, unit, price_afn, active, sort_order, created_at, updated_at)
               VALUES ($1,'fin_m1','shop','یک‌ماههٔ آزمون',1,'month',1200,true,99,$2,$2)`, [newId('pln'), now()]);
  const plan = 'fin_m1';
  await query(`INSERT INTO purchase_requests (id, shop_id, user_id, plan_code, note, status, created_at) VALUES ($1,$2,$3,$4,'',$5,$6)`,
    [newId('preq'), e.shopId, e.user.id, plan, 'pending', now() - 2 * DAY]);
  const r = await get('mali', '/pending');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.purchaseRequests.counts.pending, 1);
  const it = r.body.purchaseRequests.items[0];
  assert.equal(it.shopId, e.shopId);
  assert.equal(it.ageDays, 2);
  assert.equal(it.quotedPrice, 1200, 'قیمتِ امروزِ همان پلن');
  assert.equal(it.planTitle, 'یک‌ماههٔ آزمون');
  assert.equal(r.body.subscriptionPayments.failed, null);
  assert.equal(r.body.gateway.state, 'blocked_external');
});

test('درگاه: بی جدول ⇒ BLOCKED-EXTERNAL؛ جدول بی کلید ⇒ همان؛ با کلید ⇒ شمار، بی شمارهٔ تراکنش و «نیازمندِ بررسی»', async () => {
  const r0 = await get('mali', '/gateway');
  assert.equal(r0.body.state, 'blocked_external');
  assert.equal(r0.body.code, 'payment_intents_missing');
  assert.ok(!('items' in r0.body), 'بی درگاه هیچ فهرست/عددِ ساختگی');
  //  همان شکلِ مهاجرتِ ۱۳۹ ِ #518 (فقط ستون‌هایی که این‌جا خوانده می‌شوند)
  await query(`CREATE TABLE payment_intents (
    id text PRIMARY KEY, shop_id text NOT NULL, kind text NOT NULL, ref_id text NOT NULL, amount_minor bigint NOT NULL,
    currency text NOT NULL DEFAULT 'AFN', status text NOT NULL, provider text NOT NULL DEFAULT 'hesabpay',
    provider_ref text NOT NULL DEFAULT '', paid_at bigint, needs_review boolean NOT NULL DEFAULT false,
    status_reason text NOT NULL DEFAULT '', created_at bigint NOT NULL, updated_at bigint NOT NULL)`);
  try {
    const t = now();
    for (const [id, status, ref, review] of [['pi_1', 'paid', 'TX-1', false], ['pi_2', 'paid', '', false], ['pi_3', 'paid', 'TX-3', true], ['pi_4', 'failed', '', false]]) {
      await query(`INSERT INTO payment_intents (id, shop_id, kind, ref_id, amount_minor, status, provider_ref, paid_at, needs_review, created_at, updated_at)
                   VALUES ($1,$2,'online_order','o1',145000,$3,$4,$5,$6,$7,$7)`, [id, S.a.shopId, status, ref, status === 'paid' ? t : null, review, t]);
    }
    const r1 = await get('mali', '/gateway');
    assert.equal(r1.body.code, 'hesabpay_key_missing');
    assert.equal(r1.body.intents, 4);
    process.env.HESABPAY_API_KEY = 'test-key-not-real';
    process.env.HESABPAY_WEBHOOK_SECRET = 'test-secret-not-real';
    const r2 = await get('mali', '/gateway');
    assert.equal(r2.body.state, 'live');
    const paid = r2.body.byStatus.find((x) => x.status === 'paid');
    assert.deepEqual([paid.count, paid.unmatched, paid.review, paid.amountMinor], [3, 1, 1, 435000]);
    assert.ok(!JSON.stringify(r2.body).includes('test-key-not-real'), '⛔ کلید هرگز در پاسخ');
    const un = await get('mali', '/gateway?match=unmatched');
    assert.deepEqual(un.body.items.map((x) => x.id), ['pi_2']);
    const d = await get('mali', '/discrepancies');
    const f = Object.fromEntries(d.body.findings.map((x) => [x.code, x]));
    assert.equal(f.gateway_review.count, 1);
    assert.equal(f.gateway_unmatched.count, 1);
    const s = await get('mali', '/summary');
    assert.equal(s.body.totals.AFN.payments.count >= 3, true);
    assert.ok(!JSON.stringify(s.body.totals).includes('145000'), '⛔ پولِ درگاه با درآمدِ اشتراک جمع نمی‌شود');
  } finally {
    delete process.env.HESABPAY_API_KEY;
    delete process.env.HESABPAY_WEBHOOK_SECRET;
    await query('DROP TABLE payment_intents');
  }
});

test('مرزِ نقش در سرور: viewer همه را می‌خواند ولی سند و خروجی نه؛ بی‌توکن ۴۰۱', async () => {
  for (const p of ['/summary', '/breakdown', '/payments', '/adjustments', '/receivables', '/pending', '/gateway', '/discrepancies', '/history']) {
    assert.equal((await get('binande', p)).status, 200, p);
    assert.equal((await h.get(`/api/admin/finance${p}`)).status, 401, p);
  }
  const me = await h.get('/api/admin/me', { token: await tok('mali') });
  assert.ok(me.body.capabilities.includes('finance.v1'));
});
