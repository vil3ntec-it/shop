/**
 * سناریوی S28 (§۷.۳) — ویرایشِ هم‌زمانِ قیمت (SYN-05، SYN-07).
 *
 *   «دستگاه ۱ آفلاین قیمت را ۱۰۰ می‌کند (ساعت ۱۰:۰۰) و دستگاه ۲ آفلاین ۱۲۰ (ساعت ۱۰:۰۵). دستگاه ۲ زودتر وصل می‌شود.»
 *   نتیجه: قیمتِ نهایی ۱۲۰ است، بر اساسِ HLC، نه ترتیبِ رسیدن؛ ۱۰۰ در صندوقِ تعارض با امکانِ برگرداندن.
 *
 * بازیگرها: D1 و D2 — دو دستگاهِ صاحب با موتورِ واقعیِ Sync v3 (`lib/v3-devices.mjs`)؛ «۱۰:۰۰» و «۱۰:۰۵»
 * با ساعتِ خودِ هر دستگاه (`shift`)، پنج دقیقه از هم. سرور با `SYNC_V3_ENABLED=1`.
 *
 * اجرا:  node test/e2e/run.mjs scenario-s28
 */
import { boot, assert } from './lib/harness.mjs';
import { v3Device, rowOf, V3 } from './lib/v3-devices.mjs';

const E = await boot({ name: 'scenario-s28', env: { SYNC_V3_ENABLED: '1' } });

/** سطرهایی که امروز به کاربر نمی‌رسند — سند، نه assert */
export const PENDING = [
  'موتورِ v3 در برنامه تنظیمِ هر دستگاه است و پیش‌فرض v1 (SYN-05)؛ تا روشن نشود این رفتار به دکان نمی‌رسد',
  'صفحهٔ «صندوقِ تعارض» (دیدن، انتخاب، برگرداندن) در برنامه نیست (SYN-07)؛ برگرداندن این‌جا از درِ موتور (`resolveConflict`)',
];

const owner = await E.owner('owner-s28@e2e.local', 'صاحبِ S28', 'دکانِ S28');
const P = { id: 'p-s28', name: 'برنج', salePrice: 90, purchasePrice: 70, stock: 10, barcodes: ['6260000028001'] };
const MIN = 60_000;
let D1; let D2;
const price = async () => (await rowOf(E, owner.shopId, 'products', P.id)).data.salePriceMinor;

await E.step('S28.0', 'دو دستگاه همگام‌اند: کالا با قیمتِ ۹۰ روی هر دو', async () => {
  D1 = v3Device(E, 'S28-D1', owner.token, { products: [P] });
  await D1.engine.load();
  assert.equal((await D1.engine.sync()).ok, true);
  D2 = v3Device(E, 'S28-D2', owner.token, {});
  await D2.engine.load();
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal(D2.find('products', P.id).salePrice, 90);
  assert.equal(await price(), 9000);
});

let h1; let h2;
await E.step('S28.offline', 'هر دو آفلاین: D1 «۱۰:۰۰» ⇒ ۱۰۰، D2 «۱۰:۰۵» ⇒ ۱۲۰؛ هر دو در صف می‌مانند', async () => {
  D1.offline = true; D2.offline = true;
  D1.shift = 1 * MIN;              // «۱۰:۰۰»
  D1.edit((d) => { d.products = d.products.map((x) => (x.id === P.id ? { ...x, salePrice: 100 } : x)); });
  assert.equal(await D1.engine.record(), 1);
  h1 = D1.engine.outbox[D1.engine.outbox.length - 1].ops[0].hlc;
  D2.shift = 1 * MIN + 3 * MIN;    // «۱۰:۰۵» — بعدتر روی ساعتِ خودش (کمتر از نگهبانِ ۵ دقیقه‌ای ِ سرور)
  D2.edit((d) => { d.products = d.products.map((x) => (x.id === P.id ? { ...x, salePrice: 120 } : x)); });
  assert.equal(await D2.engine.record(), 1);
  h2 = D2.engine.outbox[D2.engine.outbox.length - 1].ops[0].hlc;
  assert.ok(V3.hlc.compare(h2, h1) > 0, `HLC ِ D2 تازه‌تر: ${h2} > ${h1}`);
  assert.equal((await D1.engine.sync()).ok, false, 'آفلاین');
  assert.equal(D1.engine.queued(), 1, 'ویرایش در صف ماند');
  assert.equal(D2.engine.queued(), 1);
}, { needs: ['S28.0'] });

await E.step('S28', 'D2 زودتر وصل می‌شود، D1 بعد ⇒ قیمتِ نهایی ۱۲۰ (HLC، نه ترتیبِ رسیدن)؛ ۱۰۰ در صندوقِ تعارض و برگرداندنی', async () => {
  D2.offline = false;
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal(await price(), 12000);
  D1.offline = false;
  assert.equal((await D1.engine.sync()).ok, true, 'D1 آخر رسید');
  assert.equal(await price(), 12000, 'آخرین رسیده (۱۰۰) نبرد');
  assert.equal(D1.engine.queued(), 0);
  //  هر دو دستگاه همان ۱۲۰ را دارند
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal(D1.find('products', P.id).salePrice, 120);
  assert.equal(D2.find('products', P.id).salePrice, 120);
  //  ۱۰۰ در صندوقِ تعارض
  const box = await D1.engine.fetchConflicts('open');
  const cf = box.find((c) => c.table === 'products' && c.row_id === P.id && c.kind === 'lww');
  assert.ok(cf, JSON.stringify(box));
  assert.equal(cf.loser_value, 10000);
  assert.equal(cf.revertible, true);
  assert.equal(D1.engine.status().state, 'conflict', 'نوارِ دستگاه «تعارض» می‌گوید');
  //  امکانِ برگرداندن: صاحب ۱۰۰ را برمی‌گرداند ⇒ همه ۱۰۰ می‌گیرند و صندوق بسته
  const res = await D1.engine.resolveConflict(cf.id, 'revert');
  assert.equal(res.ok, true, JSON.stringify(res));
  assert.equal(await price(), 10000);
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal(D2.find('products', P.id).salePrice, 100);
  assert.equal((await D1.engine.fetchConflicts('open')).some((c) => c.id === cf.id), false);
}, { needs: ['S28.offline'] });

await E.finish();
