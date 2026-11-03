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
 * • نشانی‌ها و حساب در `<خروجی.json>`؛ تا کشته نشود زنده می‌ماند (CI پس از آزمون می‌کُشد).
 */
import fs from 'node:fs';
import http from 'node:http';
import { boot } from '../../server/test/e2e/lib/harness.mjs';

const out = process.argv[2];
if (!out) { console.error('کاربرد: node ios/e2e/backend.mjs <خروجی.json> [درگاه]'); process.exit(2); }
const port = Number(process.argv[3] || 0);

const E = await boot({ name: 'ios-e2e', port });
const owner = await E.owner('ios-e2e@example.com', 'صاحبِ آیفون', 'دکانِ آیفون');

//  کالای صندوق: از دستگاهِ دیگرِ همین دکان، با ۵۰ عدد موجودی
const seed = { name: 'برنجِ آیفون', price: 100, cost: 70, units: 50, category: 'خوراکی' };
const other = await E.device('ios-e2e-seed', owner.email, { seller: 'صاحب' });
const first = await other.firstSync();
if (!first.ok) throw new Error(`همگام‌سازیِ نخستِ دستگاهِ کاشت: ${JSON.stringify(first)}`);
other.stockIn(seed);
await other.drain();

const ROWS_MAX = 50;
const probe = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const table = url.searchParams.get('table') || '';
  if (url.pathname !== '/rows' || !/^[A-Za-z]{1,40}$/.test(table)) {
    res.writeHead(404, { 'Content-Type': 'application/json' }).end('{"error":"not_found"}');
    return;
  }
  try {
    const rows = await E.db.many(
      'SELECT data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND deleted_at IS NULL ORDER BY row_id LIMIT $3',
      [owner.shopId, table, ROWS_MAX],
    );
    res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ table, rows: rows.map((r) => r.data) }));
  } catch (e) {
    res.writeHead(500, { 'Content-Type': 'application/json' }).end(JSON.stringify({ error: String(e.message || e) }));
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
