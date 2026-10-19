/**
 * سناریوی سرتاسریِ مرجوعی — S13 (برگشت با رسید) روی دو دستگاه و سرور.
 *
 *   فاکتورِ سه‌قلمی: الف ۱ × ۱۵۰، ب ۲ × ۲۵۰، ج ۱ × ۳۵۰ = ۱٬۰۰۰، تخفیفِ ۱۰٪ ⇐ ۹۰۰.
 *   برگشتِ ۱ از ۲ واحدِ «ب» ⇐ ۲۵۰ − ۲۵ = ۲۲۵؛ نقدِ موردِ انتظارِ صندوق ۲۲۵ کم می‌شود؛ برگشتِ
 *   دوباره بیش از فروخته‌شده ممکن نیست؛ برگشت روی فاکتورِ نسیه از قرض کم می‌کند نه از دخل؛
 *   دستگاهِ دوم و سرور همان را می‌بینند (returnedQty با `$inc`، نه «آخرین نوشتن»).
 *
 * کارها همان `SalesEngine.record/recordReturn` و `CashShift.kt`اند (lib/native.mjs).
 * اجرا:  node test/e2e/return.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'return' });
const owner = await E.owner('owner-ret@e2e.local', 'صاحبِ مرجوعی', 'دکانِ مرجوعی');
const staff = await E.staff(owner, 'staff-ret@e2e.local', 'شاگردِ مرجوعی');
const rows = (t) => E.db.many(`SELECT row_id, data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND deleted_at IS NULL`, [owner.shopId, t]);

let A; let B; const P = {}; let saleId; let itemB;

await E.step('S13.0', 'سه کالا با موجودی؛ صندوق باز با ۱٬۰۰۰', async () => {
  A = await E.device('A-owner-phone', owner.email);
  B = await E.device('B-staff-desktop', staff.email);
  for (const d of [A, B]) assert.equal((await d.firstSync()).ok, true);
  P.a = A.stockIn({ name: 'کالای الف', price: 150, cost: 100, units: 10 });
  P.b = A.stockIn({ name: 'کالای ب', price: 250, cost: 200, units: 10 });
  P.c = A.stockIn({ name: 'کالای ج', price: 350, cost: 300, units: 10 });
  assert.equal(A.openShift(1000).ok, true);
  await A.drain();
  await B.drain();
});

await E.step('S13.1', 'فاکتورِ ۱٬۰۰۰ با تخفیفِ ۱۰٪ ⇐ ۹۰۰', async () => {
  const r = A.sell({ lines: [{ id: P.a, qty: 1 }, { id: P.b, qty: 2 }, { id: P.c, qty: 1 }], discountType: 'percent', discountValue: 10 });
  assert.equal(r.ok, true, r.message);
  saleId = r.saleId;
  const s = A.ledger.sales.find((x) => x.id === saleId);
  assert.deepEqual([s.total, s.discount, s.finalTotal], [1000, 100, 900]);
  itemB = A.ledger.saleItems.find((x) => x.saleId === saleId && x.productId === P.b).id;
}, { needs: ['S13.0'] });

await E.step('S13.2', 'برگشتِ ۱ از ۲ واحدِ «ب» ⇐ ۲۲۵ و نقدِ موردِ انتظارِ صندوق ۲۲۵ کم', async () => {
  const r = A.returnItems(saleId, { [itemB]: 1 }, 'خراب');
  assert.equal(r.ok, true, r.message);
  assert.equal(r.amount, 225);
  const t = A.shiftTotals(A.currentShift());
  assert.deepEqual([t.sales, t.returns, t.expected], [900, 225, 1675]);
}, { needs: ['S13.1'] });

await E.step('S13.3', 'برگشتِ دوباره بیش از فروخته‌شده ممکن نیست (۵ ⇐ فقط ۱ مانده بود)', async () => {
  const r = A.returnItems(saleId, { [itemB]: 5 }, 'خراب');
  assert.equal(r.ok, true);
  assert.equal(r.amount, 225, 'فقط یک واحدِ مانده برگشت');
  assert.equal(A.ledger.saleItems.find((x) => x.id === itemB).returnedQty, 2);
  assert.equal(A.returnItems(saleId, { [itemB]: 1 }).ok, false, 'سومی نباید چیزی برگرداند');
}, { needs: ['S13.2'] });

await E.step('E2E-SYNC.return', 'B و سرور همان برگشت‌ها، همان returnedQty و همان موجودی را دارند', async () => {
  await A.drain();
  await B.drain();
  assert.equal(B.ledger.returns.filter((r) => r.saleId === saleId).length, 2);
  assert.equal(B.ledger.saleItems.find((x) => x.id === itemB).returnedQty, 2);
  for (const id of Object.values(P)) assert.equal(B.stock(id), A.stock(id), `موجودیِ ${id}`);
  const srv = await rows('returns');
  assert.equal(srv.reduce((s, r) => s + r.data.amount, 0), 450);
  const item = (await rows('saleItems')).find((r) => r.row_id === itemB);
  assert.equal(item.data.returnedQty, 2);
}, { needs: ['S13.3'] });

await E.step('S13.inc', 'دو دستگاه هم‌زمان از یک قلمِ یک فاکتور برمی‌گردانند ⇐ هر دو حساب می‌شوند (`$inc`)', async () => {
  const r = A.sell({ lines: [{ id: P.a, qty: 3 }] });
  await A.drain();
  await B.drain();
  const it = A.ledger.saleItems.find((x) => x.saleId === r.saleId).id;
  A.offline = true;
  B.offline = true;
  assert.ok(A.returnItems(r.saleId, { [it]: 1 }).ok);
  assert.ok(B.returnItems(r.saleId, { [it]: 1 }).ok);
  A.offline = false;
  B.offline = false;
  await A.drain();
  await B.drain();
  await A.drain();
  assert.equal(A.ledger.saleItems.find((x) => x.id === it).returnedQty, 2, 'A: یکی از دو برگشت گم شد');
  assert.equal(B.ledger.saleItems.find((x) => x.id === it).returnedQty, 2, 'B: یکی از دو برگشت گم شد');
  assert.equal((await rows('saleItems')).find((x) => x.row_id === it).data.returnedQty, 2);
}, { needs: ['E2E-SYNC.return'] });

await E.step('S13.credit', 'برگشت روی فاکتورِ نسیه از قرضِ همان فاکتور کم می‌کند، نه از دخل', async () => {
  const debtor = A.addDebtor('کریم');
  const s = A.sell({ lines: [{ id: P.c, qty: 2 }], payment: 'credit', debtorId: debtor, paid: 0 });
  const it = A.ledger.saleItems.find((x) => x.saleId === s.saleId).id;
  const before = A.shiftTotals(A.currentShift()).expected;
  assert.equal(A.returnItems(s.saleId, { [it]: 1 }).ok, true);
  assert.equal(A.debt(debtor), 350);
  //  ۳۵۰ از «رسید» و ۳۵۰ از «مرجوعی» ⇒ نقدِ دخل بی‌تغییر
  assert.equal(A.shiftTotals(A.currentShift()).expected, before);
  await A.drain();
  await B.drain();
  assert.equal(B.debt(debtor), 350);
}, { needs: ['S13.0'] });

await E.step('S13.waste', 'S13/RET-07: کالای «خراب» به موجودیِ قابلِ فروش برنمی‌گردد (مقصدِ ضایعات)', async () => {
  //  ۱۰ − ۲ فروش + ۲ برگشتِ «خراب» ⇒ سند: موجودیِ قابلِ فروش زیاد نمی‌شود ⇐ ۸
  assert.equal(A.stock(P.b), 8, `برگشتِ «خراب» موجودیِ قابلِ فروش را ${A.stock(P.b)} کرد (مقصدِ «ضایعات» در SalesEngine.recordReturn نیست)`);
}, { needs: ['S13.3'] });

await E.finish();
