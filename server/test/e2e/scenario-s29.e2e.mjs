/**
 * سناریوی S29 (§۷.۳) — هابِ محلی (SYN-09، HW-07).
 *
 *   «اینترنتِ فروشگاه قطع است. کامپیوتر هاب است. گوشی ۱ و گوشی ۲ وصل‌اند. گوشی ۱ می‌فروشد.»
 *
 * ⛔ هابِ محلی روی main نیست: برشِ نخستش PR #800 (`claude/syn09-local-hub`، در دستهٔ #806) هنوز باز است.
 * پس سه سطرِ نخستِ «نتیجه» (≤ ۵ ثانیه روی گوشی ۲، جابه‌جاییِ فاکتورِ نگه‌داشته میانِ دستگاه‌ها بی اینترنت،
 * گوشی به‌جای اسکنر بی اینترنت) فقط در `PENDING` آمده‌اند. آنچه امروز سنجیدنی است سطرِ چهارم است:
 * «پس از وصل شدنِ اینترنت، سرور هر op را دقیقاً یک بار دارد» — حتی اگر همان op هم از گوشی (پاسخِ گم‌شده
 * و ارسالِ دوباره) و هم از کامپیوتر (رله، همان کاری که هاب می‌کند) برسد.
 *
 * بازیگرها (برنامه در سطحِ پروتکل، Sync v1): P1 و P2 — دو گوشی؛ C — کامپیوترِ دکان.
 * اجرا:  node test/e2e/run.mjs scenario-s29
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'scenario-s29' });
const { db } = E;

/** سطرهایی که امروز ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  'در گوشی ۲ موجودی ظرفِ ≤ ۵ ثانیه کم می‌شود: هابِ محلی نیست (SYN-09 MISSING؛ PR #800 باز)',
  'فاکتورِ نگه‌داشته میانِ دستگاه‌ها جابه‌جا می‌شود (S09): بی اینترنت راهی جز هاب ندارد (PR #800)',
  'گوشی به‌جای اسکنر برای کامپیوتر: امروز فقط از راهِ سرور (PhoneScanner، PhoneLinkHost؛ HW-07) — بی اینترنت نه',
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

await E.finish();
