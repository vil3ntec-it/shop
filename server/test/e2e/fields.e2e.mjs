/**
 * A00 — فیلدی که مدلِ برنامه نمی‌شناسد روی سرور پاک نمی‌شود.
 *
 * برنامهٔ تازه‌تر (یا سرور) روی یک فاکتور فیلدی می‌نویسد که مدلِ این ساخت ندارد. دستگاه‌ها
 * همان قاعده‌های `LedgerDiff.kt` ِ همین مخزن را دارند (`lib/native.mjs` ⇐ `KOTLIN`)؛ پس
 * برگشتِ هر یک از قاعده‌های A00 در کاتلین همین‌جا قرمز می‌شود. فیلدهای `future*` عمداً در
 * فهرستِ منجمدِ تورِ سرور (`src/lib/newer-fields.js → PRE_A00`) نیستند؛ تا کفِ A00 پر نشده تور
 * `null` ِ هر ساختی را برای آن‌ها کنار می‌گذارد، پس گام‌های برنامه opهای **فرستاده‌شده** را می‌سنجند.
 *
 *   UF.0        برنامهٔ تازه‌تر فیلدِ تازه روی فاکتور می‌نویسد
 *   UF.pull     دستگاهِ موجود: Pull و ویرایشِ بی‌ربط ⇒ هیچ opی روی آن فاکتور؛ سایه فقط از درِ مدل
 *   UF.snapshot گوشیِ نو (Snapshot) و ویرایشِ بی‌ربط ⇒ هیچ opی روی آن؛ ردیفِ سرور دست‌نخورده
 *   UF.edit     گوشیِ نو همان فاکتور را لغو می‌کند ⇒ فقط status و cancelReason، بی null
 *   UF.clear    پاک کردنِ فیلدی که مدل می‌شناسد (سقفِ اعتبار) هنوز null می‌فرستد و می‌نشیند
 *   UF.web      وب (دفترِ JSON ِ خام) هم چیزی را null نمی‌کند ⇒ هستهٔ JS تغییری لازم ندارد
 *   UF.web-clear وب سقفِ اعتبار را پاک می‌کند ⇒ null روی سرور می‌نشیند (وب از تورِ سرور می‌گذرد)
 *   UF.net      ساختِ نصب‌شدهٔ پیش از A00 همان null را می‌فرستد ⇐ سرور برای کلیدی که آن ساخت
 *               نمی‌شناسد کنارش می‌گذارد؛ ساختی که فیلد را می‌شناسد هنوز می‌تواند پاکش کند
 *
 * اجرا:  node test/e2e/fields.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';
import { core, KOTLIN, PRE_A00 } from './lib/native.mjs';

const E = await boot({ name: 'fields' });
const { db } = E;
const owner = await E.owner('owner-uf@e2e.local', 'صاحبِ UF', 'دکانِ UF');
const staff = await E.staff(owner, 'staff-uf@e2e.local', 'شاگردِ UF');
const row = async (t, id) => (await db.one(
  `SELECT data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND row_id=$3`, [owner.shopId, t, id])).data;

/** گوشیِ امروز (بالاتر از کفِ همهٔ فیلدهای فهرست‌شده) و ساختِ نصب‌شدهٔ پیش از A00 */
const NEW = '3.2.9000';
const OLD = '3.2.400';
const FUTURE = {
  futureNote: 'از نسخهٔ تازه‌تر',
  futureLines: [{ method: 'cod', amount: 20000, ref: null, custody: 'courier' }],
};
/** opهایی که به فاکتور دست می‌زنند، جز درجِ ردیفِ تازه */
const touching = (ops, id) => ops.filter((o) => o.row_id === id && o.type !== 'insert')
  .map((o) => `${o.type} ${o.table} ${JSON.stringify(o.fields)}`);
const pick = (r, keys) => Object.fromEntries(keys.map((k) => [k, r[k]]));

let A; let N; let saleId; let before;

await E.step('UF.0', 'برنامهٔ تازه‌تر روی فاکتور فیلدی می‌نویسد که مدلِ این ساخت ندارد', async () => {
  A = await E.device('A-phone', owner.email, { appVersion: NEW });
  assert.equal((await A.firstSync()).ok, true);
  const rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 10 });
  saleId = A.sell({ lines: [{ id: rice, qty: 2 }] }).saleId;
  await A.drain();
  N = await E.device('N-newer-app', owner.email, { appVersion: NEW });
  assert.equal((await N.firstSync()).ok, true);
  const d = structuredClone(N.ledger);
  Object.assign(d.sales.find((s) => s.id === saleId), FUTURE);
  N.save(d);
  await N.drain();
  before = await row('sales', saleId);
  assert.deepEqual(pick(before, Object.keys(FUTURE)), FUTURE);
});

await E.step('UF.pull', 'دستگاهِ موجود Pull می‌کند و قرض‌دار می‌سازد ⇒ هیچ opی روی فاکتور؛ سایه فقط از درِ مدل', async () => {
  await A.drain();
  //  B1 (بازبینیِ #341): سایه کلیدی را که دفتر نمی‌تواند نگه دارد ندارد، وگرنه ساختِ بعدی که آن را
  //  می‌شناسد در نخستین ذخیره برای همهٔ ردیف‌ها null می‌فرستاد
  const shadowRow = A.shadow.sales.find((s) => s.id === saleId);
  assert.deepEqual(pick(shadowRow, Object.keys(FUTURE)), { futureNote: undefined, futureLines: undefined });
  A.addDebtor('احمد');
  const sent = A.queue.slice();
  await A.drain();
  assert.deepEqual(touching(sent, saleId), []);
  assert.deepEqual(await row('sales', saleId), before);
}, { needs: ['UF.0'] });

await E.step('UF.snapshot', 'گوشیِ نو از Snapshot و ویرایشِ بی‌ربط ⇒ هیچ opی روی فاکتور؛ ردیفِ سرور دست‌نخورده', async () => {
  const B = await E.device('B-staff-new-phone', staff.email, { appVersion: NEW });
  assert.equal((await B.firstSync()).ok, true);
  B.addDebtor('کریم');
  const sent = B.queue.slice();
  await B.drain();
  assert.deepEqual(touching(sent, saleId), [], `گوشیِ نو فیلدِ تازه را null کرد (KOTLIN=${JSON.stringify(KOTLIN)})`);
  assert.deepEqual(await row('sales', saleId), before);
}, { needs: ['UF.0'] });

await E.step('UF.edit', 'گوشیِ نو همان فاکتور را لغو می‌کند ⇒ فقط status و cancelReason؛ فیلدِ تازه سرِ جایش', async () => {
  const C = await E.device('C-owner-new-phone', owner.email, { appVersion: NEW });
  assert.equal((await C.firstSync()).ok, true);
  const d = structuredClone(C.ledger);
  Object.assign(d.sales.find((s) => s.id === saleId), { status: 'cancelled', cancelReason: 'آزمون' });
  C.save(d);
  const sent = C.queue.slice();
  await C.drain();
  assert.deepEqual(touching(sent, saleId), ['update sales {"status":"cancelled","cancelReason":"آزمون"}']);
  const now = await row('sales', saleId);
  assert.equal(now.status, 'cancelled');
  assert.deepEqual(pick(now, Object.keys(FUTURE)), FUTURE);
}, { needs: ['UF.0'] });

await E.step('UF.clear', 'پاک کردنِ فیلدی که مدل می‌شناسد (سقفِ اعتبار) هنوز null می‌فرستد و روی سرور می‌نشیند', async () => {
  await A.drain();
  const id = A.addDebtor('سقف‌دار');
  const d = structuredClone(A.ledger);
  d.debtors.find((x) => x.id === id).creditLimit = 500;
  A.save(d);
  await A.drain();
  assert.equal((await row('debtors', id)).creditLimit, 500);
  const e = structuredClone(A.ledger);
  delete e.debtors.find((x) => x.id === id).creditLimit;            // `EncodeDefault.NEVER`: null نوشته نمی‌شود
  A.save(e);
  const sent = A.queue.slice();
  await A.drain();
  assert.deepEqual(touching(sent, id), ['update debtors {"creditLimit":null}']);
  assert.equal((await row('debtors', id)).creditLimit, null);
}, { needs: ['UF.0'] });

await E.step('UF.web', 'وب (دفترِ JSON ِ خام) از Snapshot و ویرایشِ بی‌ربط ⇒ هیچ opی روی فاکتور', async () => {
  const W = await E.device('W-web', owner.email, { raw: true, appVersion: '3.2' });
  assert.equal((await W.firstSync()).ok, true);
  assert.deepEqual(pick(W.ledger.sales.find((s) => s.id === saleId), Object.keys(FUTURE)), FUTURE, 'وب ردیفِ خام را نگه می‌دارد');
  W.addDebtor('وب');
  const sent = W.queue.slice();
  await W.drain();
  assert.deepEqual(touching(sent, saleId), []);
  assert.deepEqual(pick(await row('sales', saleId), Object.keys(FUTURE)), FUTURE);
}, { needs: ['UF.0'] });

await E.step('UF.web-clear', 'وب سقفِ اعتبارِ یک قرض‌دار را پاک می‌کند ⇒ null روی سرور می‌نشیند (وب از تور می‌گذرد)', async () => {
  await A.drain();
  const id = A.addDebtor('سقفِ وب');
  const d = structuredClone(A.ledger);
  d.debtors.find((x) => x.id === id).creditLimit = 700;
  A.save(d);
  await A.drain();
  const W = await E.device('W2-web', owner.email, { raw: true, appVersion: '3.2' });
  assert.equal((await W.firstSync()).ok, true);
  const e = structuredClone(W.ledger);
  delete e.debtors.find((x) => x.id === id).creditLimit;            //  `index.html`: delete ⇐ sync-core null
  W.save(e);
  const sent = W.queue.slice();
  await W.drain();
  assert.deepEqual(touching(sent, id), ['update debtors {"creditLimit":null}']);
  assert.equal((await row('debtors', id)).creditLimit, null, 'تورِ سرور پاک کردنِ وب را کنار گذاشت');
}, { needs: ['UF.0'] });

await E.step('UF.net', 'ساختِ پیش از A00 null می‌فرستد ⇐ سرور فیلدِ فهرست‌شده را نگه می‌دارد؛ ساختی که فیلد را می‌شناسد پاکش می‌کند', async () => {
  //  فیلدهای فهرست‌شده: `orderId` (هیچ ساختی هنوز نمی‌شناسد) و `channel` (از 3.2.522)
  await N.drain();
  const d = structuredClone(N.ledger);
  Object.assign(d.sales.find((s) => s.id === saleId), { orderId: 'ord_e2e', channel: 'online' });
  N.save(d);
  await N.drain();
  const P = await E.device('P-old-build', owner.email, { appVersion: OLD, kotlin: PRE_A00 });
  assert.equal((await P.firstSync()).ok, true);
  P.addDebtor('قدیمی');
  const bad = P.queue.find((o) => o.row_id === saleId);
  assert.ok(bad && bad.fields.orderId === null, `ساختِ پیش از A00 باید همان null را بسازد: ${JSON.stringify(P.queue)}`);
  const r = E.ok(await P.api('POST', '/sync/v1/push', {
    device_id: P.deviceId, schema_version: core.SCHEMA_VERSION, queued: P.queue.length, ops: P.queue,
  }), 'push');
  const res = r.results.find((x) => x.op_id === bad.op_id);
  assert.equal(res.status, 'applied');
  assert.ok(res.ignored.includes('orderId'), JSON.stringify(res));
  let now = await row('sales', saleId);
  assert.equal(now.orderId, 'ord_e2e', 'تورِ سرور orderId را نگه نداشت');
  assert.equal(now.channel, 'online');
  //  همان «پاک کردنِ channel» از ساختِ قدیمی کنار می‌رود، از ساختی که آن را می‌شناسد می‌نشیند
  const clear = (dev) => {
    const op = { op_id: core.ulid(Date.now()), ts: Date.now(), table: 'sales', row_id: saleId, type: 'update', fields: { channel: null } };
    return dev.api('POST', '/sync/v1/push', { device_id: dev.deviceId, schema_version: core.SCHEMA_VERSION, queued: 1, ops: [{ ...op, hash: core.hashOf(op) }] });
  };
  E.ok(await clear(P), 'push');
  assert.equal((await row('sales', saleId)).channel, 'online', 'ساختِ 3.2.400 channel را نمی‌شناسد');
  E.ok(await clear(A), 'push');
  now = await row('sales', saleId);
  assert.equal(now.channel, null, 'ساختِ تازه باید بتواند channel را پاک کند');
  assert.equal(now.orderId, 'ord_e2e');
}, { needs: ['UF.0'] });

await E.finish();
