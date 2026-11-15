/**
 * سرورِ آزمونِ سرتاسریِ آیفون (گامِ ۸ِ docs/IOS-PLAN-fa.md، §۰.۱۳) — همان سرورِ واقعیِ مخزن.
 *
 *   node ios/e2e/backend.mjs <خروجی.json> [درگاه]
 *
 * • همان پایهٔ سناریوهای سرتاسریِ سرور (`server/test/e2e/lib/harness.mjs`): `createApp` با کارگرِ ایمیل،
 *   جدولِ schema و سوکتِ زنده، روی PGlite ِ در حافظه، فقط روی 127.0.0.1. شبیه‌سازِ iOS شبکهٔ همین مک
 *   را دارد، پس برنامه در آزمون (`ios/shared/src/iosTest/…/e2e`) همین سرور را می‌بیند.
 * • یک صاحبِ دکان با ایمیل و رمز (همان راهِ سه‌پلهٔ ثبت‌نامِ برنامه) و دکانش؛ برنامه در آزمون با همان
 *   ایمیل و رمز از صفحهٔ ورود وارد می‌شود.
 * • یک کالا با موجودی از «دستگاهِ دیگرِ» همان دکان (سطحِ پروتکل، `lib/native.mjs`) — برنامهٔ آیفون پس از ورود
 *   آن را با همگام‌سازی می‌گیرد و در صندوق می‌فروشد (فروشِ بی موجودی را موتورِ فروش رد می‌کند).
 * • نگاهِ آزمون به سرور (`probe`): `GET /rows?table=<نامِ جدول>` ⇐ ردیف‌های همگام‌شدهٔ همین دکان در
 *   `sync_rows` — آزمون با آن می‌بیند فروش و کالایی که در آیفون ثبت شد واقعاً به سرور رسید.
 * • سفارشِ آنلاین (گامِ ۱۰، §۰.۱۵): `GET /order/new` ⇐ مثلِ مشتری از ویترینِ همین دکان یک سفارشِ «ارسال» —
 *   بارِ اول بخشِ سفارش و ویترین برای همین دکان روشن (`shop.orders`، `shop.storefront` از همان راهِ پنلِ مدیر)،
 *   ویترین ساخته و روشن؛ هر بار یک کالای تازه از دستگاهِ کاشت، انتشار، تأییدِ شماره (کدِ آزمایشیِ بیرون از تولید) و
 *   `POST /store/:slug/checkout` — همان راهِ صفحهٔ ویترین. `GET /order?id=` ⇐ وضعیتِ سفارش و شمارِ «فاکتور شد» ِ سرور.
 *   تا آزمون این‌ها را نخواهد هیچ‌کدام روشن نیست (گام‌های پیشین همان دکانِ بی‌سفارشِ همیشه را می‌بینند).
 * • نشانی‌ها و حساب در `<خروجی.json>`؛ تا کشته نشود زنده می‌ماند (CI پس از آزمون می‌کُشد).
 */
import fs from 'node:fs';
import http from 'node:http';
import { boot } from '../../server/test/e2e/lib/harness.mjs';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const out = process.argv[2];
if (!out) { console.error('کاربرد: node ios/e2e/backend.mjs <خروجی.json> [درگاه]'); process.exit(2); }
const port = Number(process.argv[3] || 0);

//  ویترین کالای تازهٔ دستگاهِ کاشت را همان لحظه ببیند (پیش‌فرض: هر نیم‌ثانیه یک پرسش؛ همان `order-flow.test.js`)
const E = await boot({ name: 'ios-e2e', port, env: { CATALOG_REFRESH_THROTTLE_MS: '0' } });
const owner = await E.owner('ios-e2e@example.com', 'صاحبِ آیفون', 'دکانِ آیفون');

//  کالای صندوق: از دستگاهِ دیگرِ همین دکان، با ۵۰ عدد موجودی
const seed = { name: 'برنجِ آیفون', price: 100, cost: 70, units: 50, category: 'خوراکی' };
const other = await E.device('ios-e2e-seed', owner.email, { seller: 'صاحب' });
const first = await other.firstSync();
if (!first.ok) throw new Error(`همگام‌سازیِ نخستِ دستگاهِ کاشت: ${JSON.stringify(first)}`);
other.stockIn(seed);
await other.drain();

/* ── سفارشِ آنلاین (گامِ ۱۰): همان راهِ ویترین، فقط وقتی آزمون بخواهد ── */
const SLUG = 'ios-e2e-store';
let storeOpen = false;
let orderSeq = 0;

/** بارِ اول: بخشِ سفارش و ویترین برای همین دکان (پنلِ مدیر)، ویترینِ روشن (صاحب، از دستگاهِ کاشت) */
async function openStore() {
  if (storeOpen) return;
  for (const k of ['shop.orders', 'shop.storefront']) await E.flag(owner.shopId, k, true);
  E.ok(await other.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ آیفون', contactPhone: '0700000003' }), 'ویترین');
  storeOpen = true;
}

/** یک سفارشِ «ارسال» مثلِ مشتری: کالای تازه (دستگاهِ کاشت) ⇐ انتشار ⇐ تأییدِ شماره ⇐ ثبت */
async function placeOrder() {
  await openStore();
  const n = ++orderSeq;
  const item = { name: `خرمای آیفون ${n}`, price: 90, cost: 60, units: 20, category: 'خوراکی' };
  const productId = other.stockIn(item);
  await other.drain();
  E.ok(await other.api('PUT', `/shop/storefront/products/${productId}`, { published: true }), 'انتشارِ کالا');
  const phone = `07966${String(n).padStart(5, '0')}`;
  const v = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { email: mailOf(phone) }), 'تأییدِ شماره ۱');
  const c = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { email: mailOf(phone), code: v.devCode }), 'تأییدِ شماره ۲');
  const ck = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'delivery',
    customer: { name: 'مشتریِ آیفون', phone },
    address: { text: 'کابل، کارتهٔ سه', landmark: 'کنارِ مسجد' },
    lines: [{ productId, qtyMilli: 2000 }],
  }, null, { 'Idempotency-Key': `ios-e2e-${n}-${Date.now()}`, 'X-Store-Ticket': c.ticket }), 'ثبتِ سفارش', 201);
  const row = await E.db.one('SELECT id, order_no, total_minor FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1', [owner.shopId]);
  return { id: row.id, orderNo: Number(row.order_no), totalMinor: Number(row.total_minor), token: ck.order.token, product: item.name };
}

/** حالِ سفارش روی سرور: وضعیت و شمارِ «فاکتور شد» (رویدادِ `note` با `type = invoice`، `lib/order-flow.js`) */
async function orderState(id) {
  const o = await E.db.one('SELECT id, order_no, status FROM online_orders WHERE id=$1 AND shop_id=$2', [id, owner.shopId]);
  if (!o) return null;
  const inv = await E.db.many(
    "SELECT detail->>'saleId' AS sale FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='invoice'", [id],
  );
  return { id: o.id, orderNo: Number(o.order_no), status: o.status, invoices: inv.length, saleId: inv[0] ? inv[0].sale : null };
}

const ROWS_MAX = 50;
const json = (res, code, body) => res.writeHead(code, { 'Content-Type': 'application/json' }).end(JSON.stringify(body));
const probe = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  try {
    if (url.pathname === '/order/new') return json(res, 200, await placeOrder());
    if (url.pathname === '/order') {
      const st = await orderState(url.searchParams.get('id') || '');
      return st ? json(res, 200, st) : json(res, 404, { error: 'not_found' });
    }
    const table = url.searchParams.get('table') || '';
    if (url.pathname !== '/rows' || !/^[A-Za-z]{1,40}$/.test(table)) return json(res, 404, { error: 'not_found' });
    const rows = await E.db.many(
      'SELECT data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND deleted_at IS NULL ORDER BY row_id LIMIT $3',
      [owner.shopId, table, ROWS_MAX],
    );
    return json(res, 200, { table, rows: rows.map((r) => r.data) });
  } catch (e) {
    console.error(`probe ${url.pathname}:`, e);
    return json(res, 500, { error: String(e.message || e) });
  }
});
await new Promise((r) => probe.listen(0, '127.0.0.1', r));

const info = {
  api: E.apiBase,
  probe: `http://127.0.0.1:${probe.address().port}`,
  email: owner.email,
  password: owner.password,
  shopId: owner.shopId,
  product: seed.name,
  price: seed.price,
  stock: seed.units,
};
fs.writeFileSync(out, JSON.stringify(info, null, 2));
console.log(`سرورِ آزمونِ آیفون: ${E.apiBase} (نگاه: ${info.probe}) — ${owner.email}، دکانِ ${owner.shopId}، کالای «${seed.name}» × ${seed.units}`);

const stop = async () => {
  probe.close();
  E.outbox.stop();
  E.api.closeAllConnections?.();
  await new Promise((r) => E.api.close(r));
  await E.db.closeDb().catch(() => {});
  process.exit(0);
};
process.on('SIGTERM', stop);
process.on('SIGINT', stop);
