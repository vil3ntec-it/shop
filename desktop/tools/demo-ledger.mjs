/**
 * دفترِ نمایشی برای عکس‌گرفتن از برنامهٔ کامپیوتر (CI) — هیچ دادهٔ واقعی‌ای نیست.
 *   node desktop/tools/demo-ledger.mjs <پوشهٔ داده>
 * فایلِ `files/shop-data.json` را با همان شکلِ `ShopData`ِ برنامه می‌سازد.
 */
import fs from 'node:fs';
import path from 'node:path';

const root = process.argv[2];
if (!root) { console.error('پوشهٔ داده لازم است'); process.exit(1); }

const day = 86400000;
const now = Date.now();
const iso = t => new Date(t).toISOString().slice(0, 10);
let seq = 0;
const id = p => `${p}${(++seq).toString(36)}`;

const products = [
  ['برنج درجه یک هندی', 'خوراکی', 'کیلوگرم', 95, 120, 25],
  ['روغن آفتاب‌گردان ۵ لیتری', 'خوراکی', 'عدد', 1450, 1650, 6],
  ['شکر سفید', 'خوراکی', 'کیلوگرم', 70, 85, 30],
  ['چای سبز ۵۰۰ گرمی', 'نوشیدنی', 'بسته', 210, 260, 10],
  ['صابون دست', 'بهداشتی', 'عدد', 35, 50, 20],
  ['شامپو ۴۰۰ میلی‌لیتر', 'بهداشتی', 'عدد', 180, 230, 8],
  ['آرد گندم', 'خوراکی', 'کیلوگرم', 45, 58, 50],
  ['نوشابه ۱٫۵ لیتری', 'نوشیدنی', 'عدد', 60, 80, 24],
  ['پودر لباس‌شویی', 'بهداشتی', 'بسته', 320, 390, 6],
  ['عدس قرمز', 'خوراکی', 'کیلوگرم', 110, 140, 15],
  ['دستمال کاغذی', 'بهداشتی', 'بسته', 55, 75, 12],
  ['رب گوجه', 'خوراکی', 'عدد', 65, 85, 10],
].map(([name, category, unit, purchasePrice, salePrice, minStock], i) => ({
  id: `p${i + 1}`, name, category, unit, purchasePrice, salePrice, minStock,
  barcodes: [`62600${1000 + i}`], active: i !== 11, createdAt: now - 40 * day + i * 1000,
}));

const warehouseEntries = [];
const stockMovements = [];
products.forEach((p, i) => {
  const units = [40, 12, 60, 18, 50, 10, 80, 48, 9, 25, 30, 14][i];
  const e = { id: id('w'), productId: p.id, cartons: 0, perCarton: 0, units, unit: p.unit, price: p.purchasePrice,
    date: iso(now - 30 * day), notes: 'موجودیِ اول', createdAt: now - 30 * day + i,
    expiryDate: i === 1 ? iso(now - 3 * day) : i === 7 ? iso(now + 12 * day) : i === 3 ? iso(now + 200 * day) : '',
    batch: i === 1 ? 'B-1405-07' : '', productionDate: i === 7 ? iso(now - 60 * day) : '' };
  warehouseEntries.push(e);
  stockMovements.push({ id: id('m'), productId: p.id, type: 'purchase_in', qty: units, date: e.date, notes: 'موجودیِ اول', refId: e.id, createdAt: e.createdAt });
});

//  «دفترِ موجودی» (موتورِ تازه، فقط وقتی تنظیمش روشن شود دیده می‌شود): هزینهٔ جانبیِ یک ورود و یک
//  انتقال به انبارِ پشتی — هر دو فیلدِ افزودنی‌اند و موجودیِ موتورِ امروز را عوض نمی‌کنند
warehouseEntries[0].landedCost = 120;
stockMovements.push({ id: id('m'), productId: 'p1', type: 'transfer', qty: 10, date: iso(now - 20 * day), notes: 'پر کردنِ انبارِ پشتی',
  refId: null, createdAt: now - 20 * day, userId: 'علی', reason: 'پر کردنِ انبارِ پشتی', locationId: '', toLocationId: 'انبارِ پشتی' });

const stockLeft = Object.fromEntries(warehouseEntries.map(w => [w.productId, w.units]));
const debtors = [
  { id: 'c1', name: 'احمد کریمی', phone: '0700123456', notes: 'همسایهٔ دکان', createdAt: now - 25 * day },
  { id: 'c2', name: 'محمود رحیمی', phone: '0799876543', notes: '', createdAt: now - 20 * day },
  { id: 'c3', name: 'فریده احمدی', phone: '', notes: '', createdAt: now - 10 * day },
];
const transactions = [];
const sales = [];
const saleItems = [];
const auditLog = [];
let inv = 1;
const sellers = ['علی', 'رضا'];
for (let k = 0; k < 28; k++) {
  //  ساعتِ ۹ تا ۱۶ِ همان روز — نه «اکنون + چند ساعت» که فاکتورِ امروز را به فردا می‌برد
  const dayStart = new Date(now - (27 - k) * day); dayStart.setHours(0, 0, 0, 0);
  const t = Math.min(now - 60_000, dayStart.getTime() + 3600_000 * (9 + (k % 8)));
  const lines = [products[k % 11], products[(k * 3 + 1) % 11]];
  const qty = [1 + (k % 3), 2];
  let total = 0;
  const sid = id('s');
  //  بیش از موجودی فروخته نشود — همان قاعدهٔ `SalesEngine.record`ِ برنامه
  lines.forEach((p, j) => {
    let q = p.unit === 'کیلوگرم' ? qty[j] + 0.5 : qty[j];
    const left = (stockLeft[p.id] ?? 0);
    if (q > left) q = Math.max(0, Math.floor(left));
    if (q <= 0) return;
    stockLeft[p.id] = left - q;
    const tp = Math.round(q * p.salePrice);
    total += tp;
    saleItems.push({ id: id('i'), saleId: sid, productId: p.id, name: '', quantity: q, unitPrice: p.salePrice, purchasePrice: p.purchasePrice, totalPrice: tp, returnedQty: 0 });
    stockMovements.push({ id: id('m'), productId: p.id, type: 'sale', qty: -q, date: iso(t), notes: `فاکتور #${inv}`, refId: sid, createdAt: t });
  });
  const credit = k % 5 === 2;
  const debtorId = credit ? debtors[k % 3].id : (k % 7 === 0 ? 'c1' : null);
  const discount = k % 6 === 0 ? 20 : 0;
  const finalTotal = total - discount;
  const paid = credit ? Math.round(finalTotal / 3) : finalTotal;
  const remaining = finalTotal - paid;
  const cancelled = k === 9;
  sales.push({ id: sid, invoiceNumber: inv, total, discountType: 'amount', discountValue: discount, discount, finalTotal,
    paymentMethod: credit ? 'credit' : 'cash', debtorId, paidAmount: paid, remaining, status: cancelled ? 'cancelled' : 'completed',
    debtGiven: credit ? remaining : 0, debtSettled: 0, createdAt: t, date: iso(t), syncStatus: 'synced',
    cancelReason: cancelled ? 'مشتری پشیمان شد' : '', seller: sellers[k % 2] });
  if (credit) transactions.push({ id: id('t'), debtorId, type: 'give', amount: remaining, date: iso(t), notes: `فاکتور #${inv}`, createdAt: t });
  auditLog.push({ id: id('a'), type: 'sale', date: iso(t), refId: sid, notes: `ثبت فروش فاکتور #${inv} به مبلغ ${finalTotal} افغانی`, createdAt: t, amount: 0 });
  inv++;
}
transactions.push({ id: id('t'), debtorId: 'c1', type: 'receive', amount: 400, date: iso(now - 2 * day), notes: 'نقد آورد', createdAt: now - 2 * day });

const expenses = [
  ['کرایهٔ دکان', 'کرایه', 6000, 20], ['برق', 'برق', 850, 12], ['معاشِ شاگرد', 'معاش', 9000, 5], ['ترانسپورتِ بار', 'ترانسپورت', 600, 2],
].map(([title, category, amount, ago]) => ({ id: id('e'), title, category, amount, date: iso(now - ago * day), notes: '', createdAt: now - ago * day }));

const suppliers = [{ id: 'sp1', name: 'شرکت پخش البرز', phone: '0791112233', address: 'کابل، مندوی', notes: '', createdAt: now - 35 * day }];
const purchases = [{ id: 'pu1', productId: 'p1', supplierId: 'sp1', quantity: 20, unit: 'کیلوگرم', purchasePrice: 95, totalAmount: 1900,
  date: iso(now - 8 * day), notes: '', paidAmount: 1000, debt: 900, warehouseEntryId: null, createdAt: now - 8 * day }];

//  صندوقِ باز از امروز صبح
const shiftOpen = new Date(); shiftOpen.setHours(8, 0, 0, 0);
auditLog.push({ id: id('a'), type: 'shift_open', date: iso(now), refId: null, notes: 'باز کردنِ صندوق با 2,000 افغانی — علی', createdAt: shiftOpen.getTime(), amount: 2000 });

const data = {
  debtors, transactions, expenses, products,
  productCategories: ['خوراکی', 'نوشیدنی', 'بهداشتی'],
  warehouseEntries, sales, saleItems, returns: [], nextInvoiceNo: inv,
  suppliers, purchases, supplierPayments: [], stockMovements, priceHistory: [], auditLog,
};
const dir = path.join(root, 'files');
fs.mkdirSync(dir, { recursive: true });
fs.writeFileSync(path.join(dir, 'shop-data.json'), JSON.stringify(data));
console.log(`دفترِ نمایشی: ${products.length} کالا، ${sales.length} فاکتور ⇐ ${dir}`);
