/**
 * bench — دادهٔ مرجعِ قطعی (seed ثابت) برای سنجشِ کارایی (بخشِ ۹.۱).
 *
 * فقط تعدادِ کالا عوض می‌شود؛ بقیهٔ دفتر (قرض‌دار، فروش، مصرف، تأمین‌کننده)
 * در هر دو اندازه یکی است تا اثرِ «تعدادِ کالا» جدا دیده شود.
 * همان شکلی که `index.html` در `tohid-shop-data-v1` نگه می‌دارد.
 *
 * ⛔ هیچ دادهٔ واقعی‌ای این‌جا نیست؛ نام‌ها از چند واژهٔ ثابت ساخته می‌شوند.
 */

/** mulberry32 — کوچک، سریع، و در هر ماشین همان دنباله */
export function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const WORDS = ['برنج', 'روغن', 'شکر', 'چای', 'صابون', 'شامپو', 'آرد', 'نخود', 'لوبیا', 'رب', 'ماکارونی', 'نمک', 'عدس', 'پنیر', 'شیر', 'کیک', 'بیسکویت', 'نوشابه', 'آب‌معدنی', 'دستمال'];
const BRANDS = ['ممتاز', 'درجه‌یک', 'خانواده', 'اقتصادی', 'طلایی', 'سبز', 'کابل', 'هرات', 'بلخ', 'آریانا'];
const SIZES = ['۲۵۰ گرم', '۵۰۰ گرم', '۱ کیلو', '۵ کیلو', '۱ لیتر', '۲ لیتر', 'بستهٔ ۱۲'];
const CATS = ['خوراکی', 'نوشیدنی', 'بهداشتی', 'شوینده', 'لبنیات', 'تنقلات', 'حبوبات', 'کنسرو'];
const UNITS = ['عدد', 'کیلوگرم', 'بسته', 'کارتن', 'لیتر'];
const PEOPLE = ['احمد', 'محمود', 'فریدون', 'نسرین', 'زهرا', 'کریم', 'رحیم', 'شبنم', 'یوسف', 'مریم'];
const FAMILY = ['کریمی', 'رحیمی', 'احمدی', 'نوری', 'حیدری', 'صدیقی', 'امیری', 'هاشمی'];

export const FIXED = { debtors: 300, txPerDebtor: 3, sales: 1500, itemsPerSale: 3, expenses: 300, suppliers: 50 };

const pad = (n, w) => String(n).padStart(w, '0');
/** بارکدِ EAN-13 با رقمِ کنترلِ درست */
export function ean13(n) {
  const body = '626' + pad(n, 9);
  let sum = 0;
  for (let i = 0; i < 12; i++) sum += Number(body[i]) * (i % 2 ? 3 : 1);
  return body + ((10 - (sum % 10)) % 10);
}

export function makeLedger(productCount, seed = 20260922) {
  const r = rng(seed);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const int = (lo, hi) => lo + Math.floor(r() * (hi - lo + 1));
  const T0 = Date.UTC(2026, 0, 1);
  const day = (i) => new Date(T0 + i * 86400000).toISOString().slice(0, 10);

  const products = [], warehouseEntries = [];
  for (let i = 0; i < productCount; i++) {
    const buy = int(20, 4000) * 10;
    const p = {
      id: 'bp' + pad(i, 6), name: `${pick(WORDS)} ${pick(BRANDS)} ${pick(SIZES)} ${i}`,
      category: pick(CATS), unit: pick(UNITS), purchasePrice: buy, salePrice: Math.round(buy * 1.18),
      wholesalePrice: 0, minStock: int(0, 20), notes: '', barcodes: [ean13(i)], createdAt: T0 + i * 1000,
    };
    products.push(p);
    const per = int(1, 24), cartons = int(1, 10);
    warehouseEntries.push({ id: 'bw' + pad(i, 6), productId: p.id, cartons, perCarton: per, units: cartons * per, unit: p.unit, price: buy, date: day(i % 280), notes: '', createdAt: T0 + i * 1000 + 1 });
  }

  const debtors = [], transactions = [];
  for (let i = 0; i < FIXED.debtors; i++) {
    debtors.push({ id: 'bd' + pad(i, 5), name: `${pick(PEOPLE)} ${pick(FAMILY)}`, phone: '07' + pad(int(0, 99999999), 8), notes: '', createdAt: T0 + i });
    for (let k = 0; k < FIXED.txPerDebtor; k++) {
      transactions.push({ id: `bt${pad(i, 5)}${k}`, debtorId: 'bd' + pad(i, 5), type: k % 2 ? 'receive' : 'give', amount: int(1, 500) * 1000, date: day(int(0, 280)), notes: '', createdAt: T0 + i * 10 + k });
    }
  }

  const sales = [], saleItems = [];
  for (let i = 0; i < FIXED.sales; i++) {
    let total = 0;
    const id = 'bs' + pad(i, 6);
    for (let k = 0; k < FIXED.itemsPerSale; k++) {
      const p = products[int(0, productCount - 1)];
      const q = int(1, 5);
      total += q * p.salePrice;
      saleItems.push({ id: `bi${pad(i, 6)}${k}`, saleId: id, productId: p.id, quantity: q, unitPrice: p.salePrice, purchasePrice: p.purchasePrice, totalPrice: q * p.salePrice, returnedQty: 0 });
    }
    sales.push({ id, invoiceNumber: 1000 + i, total, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: total, paymentMethod: 'cash', debtorId: null, paidAmount: total, remaining: 0, status: 'completed', debtGiven: 0, debtSettled: 0, createdAt: T0 + i * 60000, date: day(Math.floor(i / 6)) });
  }

  const expenseCategories = ['برق', 'کرایه', 'آب', 'نان', 'حمل‌ونقل', 'تعمیرات', 'حقوق', 'سایر'];
  const expenses = [];
  for (let i = 0; i < FIXED.expenses; i++) expenses.push({ id: 'be' + pad(i, 5), title: pick(expenseCategories), category: pick(expenseCategories), amount: int(1, 200) * 1000, date: day(int(0, 280)), notes: '', createdAt: T0 + i });

  const suppliers = [];
  for (let i = 0; i < FIXED.suppliers; i++) suppliers.push({ id: 'bu' + pad(i, 4), name: `شرکت ${pick(BRANDS)} ${i}`, phone: '07' + pad(int(0, 99999999), 8), address: '', notes: '', createdAt: T0 + i });

  return {
    debtors, transactions, expenses, expenseCategories,
    products, productCategories: CATS.slice(), productUnits: ['عدد', 'کیلوگرم', 'گرم', 'لیتر', 'متر', 'کارتن', 'بسته'],
    warehouseEntries, sales, saleItems, returns: [], nextInvoiceNo: 1000 + FIXED.sales,
    suppliers, purchases: [], supplierPayments: [], stockMovements: [], priceHistory: [], auditLog: [],
  };
}
