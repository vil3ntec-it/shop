/**
 * سناریوی سرتاسریِ مرجوعی — S13 (برگشت با رسید) روی دو دستگاه و سرور.
 *
 *   فاکتورِ سه‌قلمی: الف ۱ × ۱۵۰، ب ۲ × ۲۵۰، ج ۱ × ۳۵۰ = ۱٬۰۰۰، تخفیفِ ۱۰٪ ⇐ ۹۰۰.
 *   برگشتِ ۱ از ۲ واحدِ «ب» ⇐ ۲۵۰ − ۲۵ = ۲۲۵؛ نقدِ موردِ انتظارِ صندوق ۲۲۵ کم می‌شود؛ برگشتِ
 *   دوباره بیش از فروخته‌شده ممکن نیست؛ برگشت روی فاکتورِ نسیه از قرض کم می‌کند نه از دخل؛
 *   دستگاهِ دوم و سرور همان را می‌بینند (returnedQty با `$inc`، نه «آخرین نوشتن»). S13.waste همان
 *   برگشت با جریانِ تازه (`ReturnFlow.post`، RET-07): مقصدِ «ضایعات» موجودی را بالا نمی‌برد.
 *
 * کارها همان `SalesEngine.record/recordReturn`، `ReturnFlow.post` و `CashShift.kt`اند (lib/native.mjs).
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

await E.step('S13.waste', 'S13/RET-07: برگشتِ تازه با مقصدِ «ضایعات» ⇐ ۲۲۵ نقد؛ موجودیِ قابلِ فروش زیاد نمی‌شود و حرکتِ «ضایعات» ثبت می‌شود', async () => {
  //  همان فاکتورِ S13.1 (۱٬۰۰۰ با ۱۰٪ ⇐ ۹۰۰)، این بار با جریانِ تازه (`ReturnFlow.post`)
  const s = A.sell({ lines: [{ id: P.a, qty: 1 }, { id: P.b, qty: 2 }, { id: P.c, qty: 1 }], discountType: 'percent', discountValue: 10 });
  assert.equal(s.ok, true, s.message);
  const it = A.ledger.saleItems.find((x) => x.saleId === s.saleId && x.productId === P.b).id;
  const stock = A.stock(P.b);
  const cash = A.shiftTotals(A.currentShift()).expected;
  const r = A.returnFlow(s.saleId, { [it]: { qty: 1, disposition: 'damaged' } }, { reason: 'خراب یا معیوب', mode: 'cash' });
  assert.equal(r.ok, true, r.code);
  assert.deepEqual([r.amount, r.cash, r.account], [225, 225, 0]);
  assert.equal(A.stock(P.b), stock, `برگشتِ «خراب» موجودیِ قابلِ فروش را از ${stock} به ${A.stock(P.b)} برد (مقصدِ «ضایعات»)`);
  assert.equal(A.shiftTotals(A.currentShift()).expected, cash - 225, 'نقدِ موردِ انتظارِ صندوق ۲۲۵ کم نشد');
  const entry = A.ledger.warehouseEntries.find((e) => e.refId === r.docId);
  assert.ok(entry, 'ردیفِ اصلاحیِ «ضایعات» در انبار نیست');
  assert.deepEqual([entry.productId, entry.units, entry.adjKind, entry.isAdjustment], [P.b, -1, 'damage', true]);
  const row = A.ledger.returns.find((x) => x.docId === r.docId);
  assert.deepEqual([row.disposition, row.refund, row.amount, row.reason], ['damaged', 'cash', 225, 'خراب یا معیوب']);
  assert.ok(A.ledger.stockMovements.some((m) => m.type === 'damage' && m.refId === row.id && m.qty === -1), 'حرکتِ «ضایعات» ثبت نشد');
  //  همان سند دوباره ثبت نمی‌شود؛ بیش از «قابلِ برگشت» رد می‌شود (بریده نمی‌شود)
  assert.equal(A.returnFlow(s.saleId, { [it]: { qty: 1, disposition: 'damaged' } }, { reason: 'خراب یا معیوب', docId: r.docId }).code, 'ALREADY_POSTED');
  assert.equal(A.returnFlow(s.saleId, { [it]: { qty: 2, disposition: 'damaged' } }, { reason: 'خراب یا معیوب' }).code, 'QTY_EXCEEDS');
  //  واحدِ دوم «به موجودی» ⇐ موجودی یکی بالا می‌رود (فرقِ دو مقصد)
  const back = A.returnFlow(s.saleId, { [it]: { qty: 1, disposition: 'restock' } }, { reason: 'پشیمانیِ مشتری' });
  assert.equal(back.ok, true, back.code);
  assert.equal(A.stock(P.b), stock + 1);
  //  دستگاهِ دوم و سرور همان سند، همان ضایعات و همان موجودی
  await A.drain();
  await B.drain();
  assert.equal(B.stock(P.b), A.stock(P.b), 'موجودیِ «ب» روی دستگاهِ دوم');
  assert.equal(B.ledger.warehouseEntries.find((e) => e.refId === r.docId)?.adjKind, 'damage');
  assert.equal(B.ledger.saleItems.find((x) => x.id === it).returnedQty, 2);
  const srvReturn = (await rows('returns')).find((x) => x.data.docId === r.docId);
  assert.deepEqual([srvReturn?.data.disposition, srvReturn?.data.amount], ['damaged', 225]);
  const srvEntry = (await rows('warehouseEntries')).find((x) => x.data.refId === r.docId);
  assert.deepEqual([srvEntry?.data.adjKind, srvEntry?.data.units], ['damage', -1]);
}, { needs: ['S13.0'] });

await E.step('S13.credit.v2', 'RET-06: برگشتِ تازه روی فاکتورِ نسیه ⇐ اول از بدهیِ همان فاکتور (رسیدِ customer_return)، دخل دست نمی‌خورد', async () => {
  const debtor = A.addDebtor('نادر');
  const s = A.sell({ lines: [{ id: P.c, qty: 2 }], payment: 'credit', debtorId: debtor, paid: 0 });
  assert.equal(s.ok, true, s.message);
  const it = A.ledger.saleItems.find((x) => x.saleId === s.saleId).id;
  const cash = A.shiftTotals(A.currentShift()).expected;
  const r = A.returnFlow(s.saleId, { [it]: { qty: 1, disposition: 'restock' } }, { reason: 'اشتباه در فروش' });
  assert.equal(r.ok, true, r.code);
  assert.deepEqual([r.amount, r.account, r.cash], [350, 350, 0]);
  assert.equal(A.debt(debtor), 350);
  assert.equal(A.shiftTotals(A.currentShift()).expected, cash, 'برگشتِ نسیه نباید از دخل کم کند');
  const receipt = A.ledger.transactions.find((t) => t.refId === r.docId);
  assert.deepEqual([receipt?.type, receipt?.kind, receipt?.amount], ['receive', 'customer_return', 350]);
  await A.drain();
  await B.drain();
  assert.equal(B.debt(debtor), 350);
  assert.equal(B.ledger.sales.find((x) => x.id === s.saleId).debtSettled, 350);
}, { needs: ['S13.0'] });

await E.finish();
