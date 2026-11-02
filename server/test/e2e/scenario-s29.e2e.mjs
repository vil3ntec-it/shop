/**
 * سناریوی S29 (§۷.۳) — هابِ محلی (SYN-09، HW-07).
 *
 *   «اینترنتِ فروشگاه قطع است. کامپیوتر هاب است. گوشی ۱ و گوشی ۲ وصل‌اند. گوشی ۱ می‌فروشد.»
 *
 * هابِ محلی با دستهٔ هفتم (#806، برشِ نخستِ PR #800) روی main آمد: کامپیوتر push ِ v3 ِ دستگاه‌ها را در صفِ
 * پایدار نگه می‌دارد و پس از وصل شدن **همان بدنه** را با **توکنِ خودِ دستگاه** به سرور می‌دهد
 * (`desktop/hub/HubServer.kt`، LocalHubTest، HubServerTest — پشتِ `app.multi_server` و `hub.local` ِ خاموش).
 * ولی رد و بدلِ op میانِ دستگاه‌ها هنوز نیست؛ پس سه سطرِ نخستِ «نتیجه» فقط در `PENDING` آمده‌اند.
 * آنچه این‌جا سنجیده می‌شود سطرِ چهارم است: «پس از وصل شدنِ اینترنت، سرور هر op را دقیقاً یک بار دارد» —
 * وقتی همان op هم از خودِ گوشی (پاسخِ گم‌شده و ارسالِ دوباره) و هم از هاب (همان بدنه، همان توکن) می‌رسد،
 * در v3 (راهِ هاب) و در v1 (برنامه‌های امروز).
 *
 * بازیگرها (برنامه در سطحِ پروتکل): P1 و P2 — دو گوشی؛ C — کامپیوترِ دکان. سرور با `SYNC_V3_ENABLED=1`.
 * اجرا:  node test/e2e/run.mjs scenario-s29
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'scenario-s29', env: { SYNC_V3_ENABLED: '1' } });
const { db } = E;
const require = createRequire(import.meta.url);
const hlc = require('../../src/lib/hlc');

/** سطرهایی که امروز ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  'در گوشی ۲ موجودی ظرفِ ≤ ۵ ثانیه کم می‌شود: هاب فقط به سرور می‌رساند؛ رد و بدلِ op میانِ دستگاه‌ها نیست (SYN-09 «مانده»)',
  'فاکتورِ نگه‌داشته میانِ دستگاه‌ها جابه‌جا می‌شود (S09): بی اینترنت از راهِ هاب نیست',
  'گوشی به‌جای اسکنر برای کامپیوتر: امروز فقط از راهِ سرور (PhoneScanner، PhoneLinkHost؛ HW-07) — بی اینترنت نه',
  'صفحه، mDNS و جفت‌سازیِ هاب نیست (`hub.url`/`hub.pin` دستی)؛ هاب پیش‌فرض خاموش',
  'وب (آیفون): «استثنای ثبت‌شده» (بخشِ ۱.۳ بندِ ۷) — چیزی برای سنجیدن نیست',
];

const owner = await E.owner('owner-s29@e2e.local', 'صاحبِ S29', 'دکانِ S29');
let P1; let P2; let C; let rice;
const oplogCount = async (table, rowId) => (await db.one(
  'SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND table_name=$2 AND row_id=$3', [owner.shopId, table, rowId],
)).n;

await E.step('S29.0', 'سه دستگاهِ دکان همگام‌اند: برنج ۱۰ عدد روی هر سه', async () => {
  P1 = await E.device('P1-phone', owner.email);
  P2 = await E.device('P2-phone', owner.email);
  C = await E.device('C-desk-hub', owner.email);
  for (const d of [P1, P2, C]) assert.equal((await d.firstSync()).ok, true);
  rice = P1.stockIn({ name: 'برنج', price: 600, cost: 450, units: 10 });
  await P1.drain();
  await P2.drain();
  await C.drain();
  assert.deepEqual([P1.stock(rice), P2.stock(rice), C.stock(rice)], [10, 10, 10]);
});

let sale;
await E.step('S29.offline', 'اینترنتِ دکان قطع: گوشی ۱ دو تا می‌فروشد؛ فروش روی خودِ گوشی ثبت و در صف', async () => {
  for (const d of [P1, P2, C]) d.offline = true;
  sale = P1.sell({ lines: [{ id: rice, qty: 2 }] });
  assert.equal(P1.stock(rice), 8);
  assert.ok(P1.queue.length > 0);
  assert.equal(await oplogCount('sales', sale.saleId), 0, 'سرور هنوز چیزی ندارد');
}, { needs: ['S29.0'] });

await E.step('S29', 'پس از وصل شدن: همان op از گوشی (پاسخِ گم‌شده، دوباره) و از کامپیوتر (رله) ⇒ سرور هر op را دقیقاً یک بار دارد', async () => {
  for (const d of [P1, P2, C]) d.offline = false;
  const ops = P1.queue.slice();
  const body = (dev) => ({ device_id: dev.deviceId, schema_version: dev.wireSchema(), queued: ops.length, ops });
  //  پاسخِ نخست به گوشی نمی‌رسد
  const lost = await P1.api('POST', '/sync/v1/push', body(P1));
  assert.equal(lost.status, 200);
  assert.ok(lost.body.results.every((x) => x.status === 'applied'), JSON.stringify(lost.body.results));
  //  کامپیوتر همان opها را رله می‌کند (هاب) ⇒ هیچ اثرِ دوم
  const relay = await C.api('POST', '/sync/v1/push', body(C));
  assert.equal(relay.status, 200, JSON.stringify(relay.body).slice(0, 300));
  assert.ok(relay.body.results.every((x) => x.status !== 'applied'), `رله اثرِ دوم گذاشت: ${JSON.stringify(relay.body.results)}`);
  //  گوشی صفش را دوباره می‌فرستد
  await P1.drain();
  assert.equal(P1.queue.length, 0);
  for (const op of ops) {
    const n = (await db.one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND op_id=$2', [owner.shopId, op.op_id])).n;
    assert.equal(n, 1, `op ${op.op_id} ${n} بار`);
  }
  assert.equal(await oplogCount('sales', sale.saleId), 1);
  //  همهٔ دستگاه‌ها همان موجودی
  await P2.drain();
  await C.drain();
  assert.deepEqual([P1.stock(rice), P2.stock(rice), C.stock(rice)], [8, 8, 8]);
  assert.equal(P2.ledger.sales.filter((s) => s.id === sale.saleId).length, 1);
}, { needs: ['S29.offline'] });

await E.step('S29.hub', 'راهِ هاب (v3): همان بدنه از گوشی (پاسخِ گم‌شده) و از هاب (همان op_idها، توکنِ خودِ گوشی) ⇒ سرور هر op را یک بار دارد', async () => {
  const at = Date.now();
  const ops = [
    { op_id: '01JS29HUB00000000000000001', hlc: hlc.format({ wall: at, counter: 0, node: 'S29-P1' }), table: 'sales', row_id: 's29-hub-sale', kind: 'insert', tx: 'tx.s29hub', fields: { totalMinor: 120000, discountMinor: 0, finalTotalMinor: 120000, status: 'completed' } },
    { op_id: '01JS29HUB00000000000000002', hlc: hlc.format({ wall: at, counter: 1, node: 'S29-P1' }), table: 'saleItems', row_id: 's29-hub-item', kind: 'insert', tx: 'tx.s29hub', fields: { saleId: 's29-hub-sale', productId: rice, quantityMilli: 2000, unitPriceMinor: 60000, totalPriceMinor: 120000 } },
  ];
  const body = { device_id: 'S29-P1', protocol: 3, ops };
  const first = E.ok(await E.post('/api/sync/v3/push', body, owner.token), 'push ِ گوشی (پاسخ گم شد)');
  assert.ok(first.results.every((x) => x.status === 'applied'), JSON.stringify(first.results));
  const viaHub = E.ok(await E.post('/api/sync/v3/push', body, owner.token), 'همان بدنه از هاب');
  assert.ok(viaHub.results.every((x) => x.status === 'duplicate'), JSON.stringify(viaHub.results));
  const again = E.ok(await E.post('/api/sync/v3/push', body, owner.token), 'ارسالِ دوبارهٔ گوشی');
  assert.ok(again.results.every((x) => x.status === 'duplicate'));
  for (const op of ops) {
    const n = (await db.one('SELECT COUNT(*)::int AS n FROM sync_v3_ops WHERE account_id=$1 AND op_id=$2', [owner.shopId, op.op_id])).n;
    assert.equal(n, 1, `op ${op.op_id} ${n} بار`);
  }
  assert.equal((await db.one(`SELECT COUNT(*)::int AS n FROM sync_v3_rows WHERE account_id=$1 AND table_name='sales' AND row_id='s29-hub-sale'`, [owner.shopId])).n, 1);
}, { needs: ['S29.0'] });

await E.finish();
