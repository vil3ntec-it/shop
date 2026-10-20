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

/* ══════════════════════════════════════════════════════════════════
 *  دادهٔ مرجعِ بزرگ (PERF-01، بخشِ ۹.۱): ۲۰٬۰۰۰ کالا، ۲۰۰٬۰۰۰ فاکتور با
 *  ۳۰۰٬۰۰۰ قلم، ۵٬۰۰۰ مشتری (۲٬۰۰۰ قرض‌دارِ فعال)، ۵۰۰ تأمین‌کننده.
 *
 *  توزیعِ واقع‌گرایانه، نه یکنواخت:
 *   • پرفروشی: چند صد کالا بیشترِ فروش را می‌برند (توانِ ۴ روی rng: ۱۰٪ِ کالاها حدودِ نیمِ فروش) و
 *     جایشان در فهرست با یک جای‌گشتِ ثابت پخش است، نه همه در آغاز.
 *   • هر فاکتور ۱ قلم و قلم‌های اضافه میانِ فاکتورها پخش (۱ تا ۶ قلم).
 *   • کالای کیلویی/لیتری مقدارِ اعشاری (۰٫۲۵، ۰٫۵، ۱٫۵) می‌گیرد؛ بقیه عددِ صحیح.
 *   • ۸٪ نسیه به قرض‌دارانِ فعال، ۵٪ با تخفیف، ۰٫۵٪ لغو، ۰٫۵٪ قلم با مرجوعی.
 *   • ورودیِ انبار برای هر کالا دست‌کم به اندازهٔ فروشش ⇒ موجودیِ منفی نیست.
 *  همهٔ مبلغ‌ها افغانیِ صحیح‌اند — همان `lineTotal`ِ `index.html`
 *  (`Math.round(price×qty)`) و همان ریاضیِ تخفیف/نسیهٔ `completeSale`.
 *  ⛔ هیچ دادهٔ واقعی‌ای نیست؛ هیچ ساعتی از ماشین خوانده نمی‌شود.
 * ══════════════════════════════════════════════════════════════════ */
export const REFERENCE = {
  products: 20000, sales: 200000, saleItems: 300000, customers: 5000,
  activeDebtors: 2000, suppliers: 500, expenses: 3000, days: 365,
};
const FRACTIONAL = new Set(['کیلوگرم', 'لیتر']);
const FRACTION_QTY = [0.25, 0.5, 0.5, 1, 1, 1.5, 2, 3];

export function makeReference(opts = {}) {
  const o = { ...REFERENCE, ...opts };
  if (o.saleItems < o.sales) throw new Error('saleItems باید ≥ sales باشد');
  if (o.activeDebtors > o.customers) throw new Error('activeDebtors باید ≤ customers باشد');
  const r = rng(o.seed ?? 20261019);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const int = (lo, hi) => lo + Math.floor(r() * (hi - lo + 1));
  const T0 = Date.UTC(2025, 9, 19);            // یک سالِ کامل پیش از ۲۰۲۶-۱۰-۱۹
  const day = (i) => new Date(T0 + i * 86400000).toISOString().slice(0, 10);

  const products = [];
  for (let i = 0; i < o.products; i++) {
    const buy = int(2, 4000) * 10;
    products.push({
      id: 'rp' + pad(i, 6), name: `${pick(WORDS)} ${pick(BRANDS)} ${pick(SIZES)} ${i}`,
      category: pick(CATS), unit: pick(UNITS), purchasePrice: buy, salePrice: Math.round(buy * 1.18),
      wholesalePrice: 0, minStock: int(0, 20), notes: '', barcodes: [ean13(500000000 + i)], createdAt: T0 - 86400000 + i,
    });
  }
  // جای‌گشتِ ثابت: «پرفروش‌ها» در سراسرِ فهرست پخش‌اند
  const perm = products.map((_, i) => i);
  for (let i = perm.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [perm[i], perm[j]] = [perm[j], perm[i]]; }
  const popular = () => products[perm[Math.min(o.products - 1, Math.floor(o.products * r() ** 4))]];

  const debtors = [];
  for (let i = 0; i < o.customers; i++) {
    debtors.push({ id: 'rd' + pad(i, 5), name: `${pick(PEOPLE)} ${pick(FAMILY)} ${i}`, phone: '07' + pad(int(0, 99999999), 8), notes: '', createdAt: T0 - 86400000 + i });
  }

  // تعدادِ قلمِ هر فاکتور: ۱ + سهمی از قلم‌های اضافه (دقیقاً saleItems − sales)
  const lines = new Uint8Array(o.sales).fill(1);
  for (let extra = o.saleItems - o.sales; extra > 0;) {
    const s = int(0, o.sales - 1);
    if (lines[s] < 6) { lines[s]++; extra--; }
  }

  const sales = [], saleItems = [], transactions = [], returns = [];
  const soldBy = new Map();
  const perDay = o.sales / o.days;
  for (let i = 0; i < o.sales; i++) {
    const d = Math.min(o.days - 1, Math.floor(i / perDay));
    const createdAt = T0 + d * 86400000 + 8 * 3600000 + Math.floor((i - d * perDay) * (12 * 3600000 / perDay));
    const id = 'rs' + pad(i, 7);
    const cancelled = r() < 0.005;
    let total = 0;
    const used = new Set();
    for (let k = 0; k < lines[i]; k++) {
      // یک کالا دو بار در یک فاکتور نمی‌آید (همان سبدِ برنامه)
      let p = popular();
      while (used.has(p.id)) p = products[int(0, o.products - 1)];
      used.add(p.id);
      const q = FRACTIONAL.has(p.unit) ? pick(FRACTION_QTY) : (r() < 0.7 ? 1 : int(2, 6));
      const totalPrice = Math.round(p.salePrice * q);
      total += totalPrice;
      const item = { id: `ri${pad(i, 7)}${k}`, saleId: id, productId: p.id, quantity: q, unitPrice: p.salePrice, purchasePrice: p.purchasePrice, totalPrice, returnedQty: 0 };
      if (!cancelled && q >= 2 && r() < 0.005) {
        item.returnedQty = 1;
        returns.push({ id: 'rr' + pad(returns.length, 6), saleId: id, saleItemId: item.id, productId: p.id, quantity: 1, amount: Math.round(totalPrice / q), reason: '', date: day(d), createdAt: createdAt + 3600000 });
      }
      if (!cancelled) soldBy.set(p.id, (soldBy.get(p.id) || 0) + q - item.returnedQty);
      saleItems.push(item);
    }
    const discount = r() < 0.05 ? Math.min(total, int(1, 20) * 10) : 0;
    const finalTotal = Math.max(0, total - discount);
    const credit = !cancelled && r() < 0.08 && finalTotal > 0;
    const debtorId = credit ? debtors[int(0, o.activeDebtors - 1)].id : null;
    const paid = credit ? Math.round(finalTotal * r() * 0.5) : finalTotal;
    const remaining = Math.max(0, finalTotal - paid);
    sales.push({
      id, invoiceNumber: 1000 + i, total, discountType: 'amount', discountValue: discount, discount, finalTotal,
      paymentMethod: credit ? 'credit' : 'cash', debtorId, paidAmount: paid, remaining,
      status: cancelled ? 'cancelled' : 'completed', debtGiven: debtorId && remaining > 0 ? remaining : 0, debtSettled: 0,
      createdAt, date: day(d), seller: 'bench',
    });
    if (debtorId && remaining > 0) {
      transactions.push({ id: 'rt' + pad(transactions.length, 6), debtorId, type: 'give', amount: remaining, date: day(d), notes: `فروش نسیه — فاکتور #${1000 + i}`, createdAt });
      if (r() < 0.6) transactions.push({ id: 'rt' + pad(transactions.length, 6), debtorId, type: 'receive', amount: Math.max(1, Math.round(remaining * r())), date: day(Math.min(o.days - 1, d + int(1, 30))), notes: '', createdAt: createdAt + 86400000 });
    }
  }

  const warehouseEntries = [];
  for (const p of products) {
    const need = soldBy.get(p.id) || 0;
    const per = int(1, 24);
    const n = need > 50 ? 3 : need > 0 ? 2 : 1;
    let left = Math.ceil(need) + int(0, 40);
    for (let k = 0; k < n; k++) {
      const units = k === n - 1 ? Math.max(1, left) : Math.max(1, Math.round(left / (n - k)));
      left -= units;
      const d = Math.floor(k * o.days / n);
      warehouseEntries.push({ id: `rw${p.id.slice(2)}${k}`, productId: p.id, cartons: 0, perCarton: per, units, unit: p.unit, price: p.purchasePrice, date: day(d), notes: '', createdAt: T0 + d * 86400000 });
    }
  }

  const expenseCategories = ['برق', 'کرایه', 'آب', 'نان', 'حمل‌ونقل', 'تعمیرات', 'حقوق', 'سایر'];
  const expenses = [];
  for (let i = 0; i < o.expenses; i++) {
    const d = int(0, o.days - 1);
    expenses.push({ id: 're' + pad(i, 5), title: pick(expenseCategories), category: pick(expenseCategories), amount: int(1, 200) * 1000, date: day(d), notes: '', createdAt: T0 + d * 86400000 + i });
  }
  const suppliers = [];
  for (let i = 0; i < o.suppliers; i++) suppliers.push({ id: 'ru' + pad(i, 4), name: `شرکت ${pick(BRANDS)} ${i}`, phone: '07' + pad(int(0, 99999999), 8), address: '', notes: '', createdAt: T0 + i });

  return {
    debtors, transactions, expenses, expenseCategories,
    products, productCategories: CATS.slice(), productUnits: ['عدد', 'کیلوگرم', 'گرم', 'لیتر', 'متر', 'کارتن', 'بسته'],
    warehouseEntries, sales, saleItems, returns, nextInvoiceNo: 1000 + o.sales,
    suppliers, purchases: [], supplierPayments: [], stockMovements: [], priceHistory: [], auditLog: [],
  };
}

/** ردیف‌های دفتر به شکلِ `sync_rows`ِ Sync v1 ({table, id, data}) — `data` بی `id`، همان `bodyOf`ِ sync-core.js */
export const SYNC_COLLECTIONS = ['products', 'warehouseEntries', 'sales', 'saleItems', 'returns', 'debtors', 'transactions', 'expenses', 'suppliers'];
export function* syncRowsOf(ledger) {
  for (const table of SYNC_COLLECTIONS) {
    for (const rec of ledger[table] || []) {
      const { id, ...data } = rec;
      yield { table, id, data };
    }
  }
}
