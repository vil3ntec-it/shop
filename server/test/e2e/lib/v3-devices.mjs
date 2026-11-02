/**
 * دستگاهِ Sync v3 برای سناریوهای سرتاسری (S28، S28b) — موتورِ واقعیِ `license/sync-v3-engine.js`
 * (همان بردارهای HLC و مبدلِ برنامهٔ کاتلین) روی سرورِ همین سناریو، بی ساختگی.
 *
 *   • ساعتِ دستگاه: `rawNow = Date.now() + shift` — «ساعتِ دستگاه را جلو/عقب بکش» یعنی `shift` را عوض کن؛
 *     موتور مثلِ برنامه با اختلافِ آخرین `server_time` تصحیح می‌کند (بخشِ ۳.۸.۱ بندِ ۸).
 *   • «آفلاین» = `offline = true`: هیچ درخواستی بیرون نمی‌رود.
 */
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(import.meta.url);
const ROOT = path.join(import.meta.dirname, '..', '..', '..', '..');
export const V3 = require(path.join(ROOT, 'license', 'sync-v3-engine.js'));
const core = require(path.join(ROOT, 'license', 'sync-core.js'));

export function v3Device(E, name, token, data = {}) {
  let state = core.cloneLedger(data);
  const ledger = { read: () => state, write: (d) => { state = core.cloneLedger(d); }, get: () => state };
  const real = V3.FetchTransport(E.apiBase, token);
  const dev = { name, ledger, shift: 0, offline: false };
  const transport = {
    async request(method, p, body) {
      if (dev.offline) throw new Error('به سرور نرسیدیم (حالتِ هواپیما)');
      return real.request(method, p, body);
    },
  };
  dev.engine = new V3.Engine({
    transport, store: new V3.MemoryStore(), ledger, deviceId: name, random: () => 0.5, now: () => Date.now() + dev.shift,
  });
  dev.engine.keepLocalOnly = true;
  /** ویرایشِ دفترِ همین دستگاه (همان کاری که صفحه با saveData می‌کند) */
  dev.edit = (fn) => { const d = ledger.get(); fn(d); ledger.write(d); };
  dev.find = (table, id) => (ledger.get()[table] || []).find((r) => r.id === id);
  /** فروشِ نقدِ یک قلم: سند + قلم + حرکت (همان سه ردیفِ برنامه) با ساعتِ خودِ دستگاه */
  dev.sell = (saleId, product, qty, price) => {
    const at = Date.now() + dev.shift;
    const date = new Date(at).toISOString().slice(0, 10);
    dev.edit((d) => {
      d.sales = [...(d.sales || []), { id: saleId, invoiceNo: (d.sales || []).length + 1, total: qty * price, discount: 0, finalTotal: qty * price, paidAmount: qty * price, remaining: 0, status: 'completed', syncStatus: 'pending', createdAt: at, date }];
      d.saleItems = [...(d.saleItems || []), { id: `${saleId}-1`, saleId, productId: product, quantity: qty, returnedQty: 0, unitPrice: price, purchasePrice: 0, totalPrice: qty * price }];
      d.stockMovements = [...(d.stockMovements || []), { id: `${saleId}-m`, productId: product, qty: -qty, reason: 'sale' }];
    });
  };
  return dev;
}

export const rowOf = (E, shopId, table, id) => E.db.one(
  `SELECT * FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id],
);
