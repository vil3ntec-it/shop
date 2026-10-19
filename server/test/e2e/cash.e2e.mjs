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
 * کارها همان `CashShift.kt`، `SalesEngine.record` و `DebtorEngine.addTransaction`اند (lib/native.mjs).
 * اجرا:  node test/e2e/cash.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

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

await E.finish();
