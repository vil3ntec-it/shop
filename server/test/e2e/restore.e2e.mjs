/**
 * سناریوی سرتاسریِ بازیابیِ پشتیبان روی دستگاهِ دوم — S143 (§۲۸.۸، فاز ۱).
 *
 *   «بازیابی پشتیبان روی دستگاه دوم هم‌زمان با فعالیت دستگاه اول؛ سری مستقل، حفظ اسناد قدیم و
 *    جلوگیری از تکرار عملیات.»
 *
 * بازیگرها (هر دو با حسابِ صاحبِ دکان، در سطحِ پروتکل — `lib/native.mjs`):
 *   • A — گوشیِ صاحب که کار می‌کند (فروش، نسیه، دریافتِ قرض)؛
 *   • B — کامپیوترِ صاحب که پشتیبان گرفته و بعد همان پشتیبان را بازیابی می‌کند.
 *
 * بازیابی در برنامه همان `ShopStore.applyRestore` ⇒ `writeInternal` ⇒ `SyncV1Engine.record` است؛
 * این‌جا همان `NativeDevice.save(دفترِ پشتیبان)` (تفاضل با سایه ⇒ صف). پشتیبان همان JSON ِ دفتر است
 * (`BackupBundle`/`importJson`).
 *
 * سنجه‌ها: اسنادِ پشتیبان (قدیم) سرِ جایشان؛ اسنادی که A پس از پشتیبان ساخته (پیش و هم‌زمان با بازیابی)
 * روی سرور و A و B می‌مانند؛ هیچ opی دو بار اثر نمی‌کند (یک فاکتور، یک قرض، موجودی یک بار).
 * «سری مستقل» (شمارهٔ سندِ هر دستگاه، F0-03) در `DocSeries` ِ کاتلین است و این‌جا سنجیده نمی‌شود.
 *
 * اجرا:  node test/e2e/restore.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'restore' });
const owner = await E.owner('owner-rst@e2e.local', 'صاحبِ بازیابی', 'دکانِ بازیابی');
const live = (t) => E.db.many(`SELECT row_id, data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND deleted_at IS NULL`, [owner.shopId, t]);
const serverDebt = async (debtorId) => (await live('transactions')).map((r) => r.data).filter((t) => t.debtorId === debtorId)
  .reduce((s, t) => s + (t.type === 'give' ? t.amount : -t.amount), 0);
async function serverStock(productId) {
  const entered = (await live('warehouseEntries')).map((r) => r.data).filter((e) => e.productId === productId).reduce((s, e) => s + e.units, 0);
  const sales = await live('sales');
  const cancelled = new Set(sales.filter((s) => s.data.status === 'cancelled').map((s) => s.row_id));
  const sold = (await live('saleItems')).map((r) => r.data).filter((i) => i.productId === productId && !cancelled.has(i.saleId))
    .reduce((s, i) => s + i.quantity - i.returnedQty, 0);
  return entered - sold;
}

let A; let B; let rice; let ahmad; let backup;
const before = {};
const after = {};

await E.step('S143.0', 'A و B (هر دو صاحب) یک دکان؛ A می‌فروشد؛ B پشتیبان می‌گیرد', async () => {
  A = await E.device('A-owner-phone', owner.email, { seller: 'صاحب (گوشی)' });
  B = await E.device('B-owner-desktop', owner.email, { seller: 'صاحب (کامپیوتر)' });
  for (const d of [A, B]) assert.equal((await d.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 50 });
  ahmad = A.addDebtor('احمد کریمی', '0791234567');
  before.cash = A.sell({ lines: [{ id: rice, qty: 2 }] });
  before.credit = A.sell({ lines: [{ id: rice, qty: 3 }], payment: 'credit', debtorId: ahmad, paid: 0 });
  assert.ok(before.cash.ok && before.credit.ok);
  await A.drain();
  await B.drain();
  assert.deepEqual(B.ledger.sales.map((s) => s.id).sort(), [before.cash.saleId, before.credit.saleId].sort());
  //  پشتیبانِ B همان JSON ِ دفترِ این لحظه
  backup = JSON.parse(JSON.stringify(B.ledger));
});

await E.step('S143.after', 'A پس از پشتیبان کار می‌کند: فروش، دریافتِ قرض؛ B همه را می‌گیرد', async () => {
  after.sale = A.sell({ lines: [{ id: rice, qty: 4 }] });
  assert.equal(after.sale.ok, true);
  assert.equal(A.receive(ahmad, 120).ok, true);
  after.receive = A.ledger.transactions.at(-1).id;
  await A.drain();
  await B.drain();
  assert.ok(B.ledger.sales.some((s) => s.id === after.sale.saleId));
  assert.equal(B.debt(ahmad), 180);
}, { needs: ['S143.0'] });

await E.step('S143', 'B پشتیبانِ پیش را بازیابی می‌کند وقتی A هم‌زمان می‌فروشد ⇐ اسنادِ قدیم و تازه همه‌جا می‌مانند و هیچ عملی دو بار نمی‌شود', async () => {
  //  A همین لحظه بی اینترنت یک فروشِ دیگر دارد (هم‌زمان با بازیابی)
  A.offline = true;
  after.during = A.sell({ lines: [{ id: rice, qty: 1 }] });
  assert.equal(after.during.ok, true);
  //  B: «بازیابی» ⇒ ShopStore.applyRestore(پشتیبان) ⇒ record
  B.save(JSON.parse(JSON.stringify(backup)));
  await B.drain();
  A.offline = false;
  await A.drain();
  await B.drain();
  await A.drain();
  const all = [before.cash.saleId, before.credit.saleId, after.sale.saleId, after.during.saleId].sort();
  //  ۱) اسنادِ قدیم (درونِ پشتیبان) سرِ جایشان
  for (const id of [before.cash.saleId, before.credit.saleId]) {
    assert.ok((await live('sales')).some((r) => r.row_id === id), `فاکتورِ قدیمِ ${id} روی سرور نیست`);
  }
  //  ۲) اسنادی که A پس از پشتیبان ساخت نه روی سرور پاک می‌شوند، نه روی A، نه روی B
  const srv = (await live('sales')).map((r) => r.row_id).sort();
  assert.deepEqual(srv, all, 'بازیابی روی دستگاهِ دوم فاکتورهای تازهٔ دستگاهِ اول را از سرور پاک کرد');
  assert.deepEqual(A.ledger.sales.map((s) => s.id).sort(), all, 'فاکتورهای A پس از بازیابیِ B از A رفتند');
  assert.deepEqual(B.ledger.sales.map((s) => s.id).sort(), all);
  assert.ok((await live('transactions')).some((r) => r.row_id === after.receive), 'دریافتِ قرضِ پس از پشتیبان پاک شد');
  //  ۳) هیچ عملی دو بار نیست: موجودی ۵۰ − (۲ + ۳ + ۴ + ۱)، قرضِ احمد ۳۰۰ − ۱۲۰، یک op برای هر ردیف
  assert.equal(await serverStock(rice), 40);
  assert.equal(A.stock(rice), 40);
  assert.equal(B.stock(rice), 40);
  assert.equal(await serverDebt(ahmad), 180);
  assert.equal(A.debt(ahmad), 180);
  assert.equal(B.debt(ahmad), 180);
  const dup = await E.db.many(`SELECT row_id, COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND table_name='sales' AND op_type='insert' GROUP BY row_id HAVING COUNT(*) > 1`, [owner.shopId]);
  assert.deepEqual(dup, [], 'فاکتور دو بار درج شد');
}, { needs: ['S143.after'] });

await E.finish();
