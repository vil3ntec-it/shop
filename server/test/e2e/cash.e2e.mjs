/**
 * سناریوی سرتاسریِ صندوق و قرض — S12 (بستنِ صندوق با اختلاف)، فروشِ نسیه با پرداختِ
 * جزئی (PAY)، دریافتِ بخشی از قرض، و همان عددها روی دستگاهِ دوم و سرور.
 *
 *   A (گوشیِ صاحب) صندوق را با ۲٬۰۰۰ باز می‌کند؛ فروشِ نقدِ ۳۰۰؛ فروشِ ۵۰۰ به «احمد» با ۲۰۰
 *   نقد (۳۰۰ قرض)؛ احمد بعداً ۱۲۰ می‌دهد (۱۸۰ مانده). موردِ انتظار در دخل:
 *   ۲٬۰۰۰ + ۳۰۰ + ۲۰۰ + ۱۲۰ = ۲٬۶۲۰؛ شمرده ۲٬۶۱۰ ⇐ «کسری ۱۰» و هیچ چیزی خودکار «درست» نمی‌شود.
 *   B (کامپیوترِ شاگرد) پس از همگام‌سازی همان ماندهٔ احمد، همان فروش‌ها و همان سابقهٔ صندوق را
 *   می‌بیند؛ سرور هم.
 *
 * S141 (retry ِ مالی): فروشِ نسیه با پرداختِ جزئی؛ جوابِ push سه بار گم می‌شود، دو تلاشِ هم‌زمان و
 *   تلاشِ خودِ دستگاه ⇒ یک فروش، یک کسرِ موجودی، یک اثرِ نقد در صندوق و یک قرض — روی سرور و دستگاهِ دوم.
 *
 * کارها همان `CashShift.kt`، `SalesEngine.record` و `DebtorEngine.addTransaction`اند (lib/native.mjs).
 * اجرا:  node test/e2e/cash.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';
import { core } from './lib/native.mjs';

const E = await boot({ name: 'cash' });
const owner = await E.owner('owner-cash@e2e.local', 'صاحبِ صندوق', 'دکانِ صندوق');
const staff = await E.staff(owner, 'staff-cash@e2e.local', 'شاگردِ صندوق');
const rows = (t) => E.db.many(`SELECT row_id, data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND deleted_at IS NULL`, [owner.shopId, t]);

let A; let B; let rice; let ahmad; let credit;

await E.step('S12.0', 'دو دستگاهِ یک دکان؛ کالا و قرض‌دار از A', async () => {
  A = await E.device('A-owner-phone', owner.email, { seller: 'صاحب' });
  B = await E.device('B-staff-desktop', staff.email, { seller: 'شاگرد' });
  for (const d of [A, B]) assert.equal((await d.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 50 });
  ahmad = A.addDebtor('احمد کریمی', '0791234567');
  await A.drain();
  await B.drain();
  assert.equal(B.stock(rice), 50);
});

await E.step('S12.1', 'صندوق با ۲٬۰۰۰ باز می‌شود و B همان صندوقِ باز را می‌بیند', async () => {
  assert.equal(A.openShift(2000).ok, true);
  assert.equal(A.openShift(500).ok, false, 'صندوقِ دوم روی صندوقِ باز');
  await A.drain();
  await B.drain();
  const s = B.currentShift();
  assert.ok(s && s.open.amount === 2000, 'صندوقِ باز روی B دیده نمی‌شود');
}, { needs: ['S12.0'] });

await E.step('PAY.partial', 'فروشِ نقدِ ۳۰۰ و فروشِ نسیهٔ ۵۰۰ با ۲۰۰ نقد ⇐ ۳۰۰ قرض', async () => {
  assert.equal(A.sell({ lines: [{ id: rice, qty: 3 }] }).ok, true);
  const r = A.sell({ lines: [{ id: rice, qty: 5 }], payment: 'credit', debtorId: ahmad, paid: 200 });
  assert.equal(r.ok, true, r.message);
  credit = r.saleId;
  const s = A.ledger.sales.find((x) => x.id === credit);
  assert.deepEqual([s.finalTotal, s.paidAmount, s.remaining, s.debtGiven], [500, 200, 300, 300]);
  assert.equal(A.debt(ahmad), 300);
  //  نسیه بی قرض‌دار ثبت نمی‌شود
  assert.equal(A.sell({ lines: [{ id: rice, qty: 1 }], payment: 'credit', paid: 0 }).ok, false);
}, { needs: ['S12.1'] });

await E.step('PAY.receive', 'احمد ۱۲۰ از قرضش را می‌دهد ⇐ ۱۸۰ مانده', async () => {
  assert.equal(A.receive(ahmad, 120).ok, true);
  assert.equal(A.debt(ahmad), 180);
}, { needs: ['PAY.partial'] });

let closed;
await E.step('S12.2', 'بستنِ صندوق: موردِ انتظار ۲٬۶۲۰، شمرده ۲٬۶۱۰ ⇐ «کسری ۱۰»؛ چیزی خودکار درست نمی‌شود', async () => {
  closed = A.closeShift(2610, 'شمارشِ آخرِ روز');
  assert.equal(closed.ok, true);
  assert.deepEqual(
    [closed.totals.opening, closed.totals.sales, closed.totals.receipts, closed.totals.returns, closed.totals.expected],
    [2000, 500, 120, 0, 2620],
  );
  assert.equal(closed.diff, -10);
  const row = A.ledger.auditLog.find((x) => x.type === 'shift_close');
  assert.match(row.notes, /مورد انتظار 2,620، شمرده 2,610، کسری 10/);
  assert.equal(A.ledger.expenses.length, 0, 'اختلاف با مصرفِ ساختگی پوشانده شد');
  assert.equal(A.ledger.transactions.filter((t) => t.type === 'receive').length, 1);
  assert.equal(A.currentShift(), null);
}, { needs: ['PAY.receive'] });

await E.step('E2E-SYNC.cash', 'B و سرور همان ماندهٔ قرض، همان فروش‌ها و همان سابقهٔ صندوق را دارند', async () => {
  await A.drain();
  await B.drain();
  assert.equal(B.debt(ahmad), 180);
  assert.equal(B.ledger.sales.length, 2);
  assert.equal(B.ledger.sales.find((s) => s.id === credit).debtGiven, 300);
  const sh = B.shifts();
  assert.equal(sh.length, 1);
  assert.ok(sh[0].close && sh[0].close.amount === 2610, 'بستنِ صندوق روی B نیست');
  assert.equal(B.shiftTotals(sh[0]).expected, 2620, 'B همان موردِ انتظار را از دفترِ همگام‌شده می‌سازد');
  const srv = (await rows('transactions')).map((r) => r.data).filter((t) => t.debtorId === ahmad);
  assert.equal(srv.reduce((s, t) => s + (t.type === 'give' ? t.amount : -t.amount), 0), 180, 'سرور: ماندهٔ احمد');
  const audit = (await rows('auditLog')).map((r) => r.data.type);
  assert.ok(audit.includes('shift_open') && audit.includes('shift_close'));
}, { needs: ['S12.2'] });

await E.step('E2E-CASH.staff', 'شاگرد روی B صندوقِ خودش را باز و بسته می‌کند؛ A همان را می‌بیند', async () => {
  assert.equal(B.openShift(500).ok, true);
  assert.equal(B.sell({ lines: [{ id: rice, qty: 2 }] }).ok, true);
  const c = B.closeShift(700);
  assert.equal(c.ok, true);
  assert.equal(c.totals.expected, 700);
  assert.equal(c.diff, 0);
  await B.drain();
  await A.drain();
  const sh = A.shifts();
  assert.equal(sh.length, 2);
  assert.match(sh[0].close.notes, /بی اختلاف — شاگرد/);
}, { needs: ['E2E-SYNC.cash'] });

await E.step('S141', 'S141: فروشِ نسیه با پرداختِ جزئی؛ جوابِ push سه بار گم می‌شود، دو تلاشِ هم‌زمان ⇐ یک فروش، یک کسرِ موجودی، یک نقد و یک قرض', async () => {
  assert.equal(A.openShift(1000).ok, true);
  await A.drain();
  await B.drain();
  const stock0 = B.stock(rice);
  const debt0 = B.debt(ahmad);
  const give0 = (await rows('transactions')).filter((r) => r.data.debtorId === ahmad && r.data.type === 'give').length;
  //  ۴ × ۱۰۰ = ۴۰۰؛ ۱۵۰ نقد، ۲۵۰ قرض
  const r = A.sell({ lines: [{ id: rice, qty: 4 }], payment: 'credit', debtorId: ahmad, paid: 150 });
  assert.equal(r.ok, true, r.message);
  const sent = A.queue.slice();
  assert.ok(sent.length >= 5, `opهای فروش: ${sent.length}`);
  const body = { device_id: A.deviceId, schema_version: core.BASE_SCHEMA, queued: sent.length, ops: sent };
  //  سه بار همان بدنهٔ `SyncV1Client.push` به سرور می‌رسد و جوابش به دستگاه نمی‌رسد ⇒ صف دست‌نخورده
  const lost = [];
  for (let i = 0; i < 3; i++) lost.push(await A.api('POST', '/sync/v1/push', body));
  assert.equal(lost[0].status, 200);
  assert.ok(lost[0].body.results.every((x) => x.status === 'applied'), JSON.stringify(lost[0].body.results));
  for (const l of lost.slice(1)) assert.ok(l.status === 200 && l.body.results.every((x) => x.status === 'duplicate'), JSON.stringify(l.body));
  assert.equal(A.queue.length, sent.length, 'صف نباید پاک شده باشد');
  //  دو تلاشِ هم‌زمان (دو زبانه/کارگر) — باز هم فقط «تکراری»
  const both = await Promise.all([A.api('POST', '/sync/v1/push', body), A.api('POST', '/sync/v1/push', body)]);
  for (const b of both) assert.ok(b.status === 200 && b.body.results.every((x) => x.status === 'duplicate'), JSON.stringify(b.body));
  //  و تلاشِ خودِ دستگاه
  await A.drain();
  assert.equal(A.queue.length, 0);
  //  سرور: هر op یک بار؛ یک فاکتور، یک قلم، یک حرکتِ فروش، یک قرض
  const ids = sent.map((o) => o.op_id);
  const n = (await E.db.many('SELECT op_id, COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND op_id = ANY($2::text[]) GROUP BY op_id', [owner.shopId, ids]));
  assert.equal(n.length, ids.length, 'opی در دفترِ سرور نیست');
  assert.ok(n.every((x) => x.n === 1), JSON.stringify(n.filter((x) => x.n !== 1)));
  assert.equal((await rows('sales')).filter((x) => x.row_id === r.saleId).length, 1);
  assert.equal((await rows('saleItems')).filter((x) => x.data.saleId === r.saleId).length, 1);
  assert.equal((await rows('stockMovements')).filter((x) => x.data.refId === r.saleId).length, 1);
  assert.equal((await rows('transactions')).filter((x) => x.data.debtorId === ahmad && x.data.type === 'give').length, give0 + 1, 'قرضِ دوم ساخته شد');
  //  دستگاهِ دوم: یک فاکتور، موجودی یک بار کم، قرض یک بار زیاد، نقدِ صندوق یک بار
  await B.drain();
  assert.equal(B.ledger.sales.filter((x) => x.id === r.saleId).length, 1);
  assert.equal(B.stock(rice), stock0 - 4);
  assert.equal(B.debt(ahmad), debt0 + 250);
  const shift = B.currentShift();
  assert.ok(shift, 'صندوقِ باز روی B نیست');
  assert.equal(B.shiftTotals(shift).expected, 1150, 'نقدِ صندوق: ۱٬۰۰۰ + ۱۵۰');
  const closed = A.closeShift(1150);
  assert.equal(closed.ok, true);
  assert.deepEqual([closed.totals.sales, closed.totals.expected, closed.diff], [150, 1150, 0]);
  await A.drain();
}, { needs: ['E2E-CASH.staff'] });

await E.finish();
