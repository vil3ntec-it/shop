/**
 * سناریوی S28b (§۷.۳) — ساعتِ غلطِ دستگاه (SYN-05، بخشِ ۳.۸.۱ بندِ ۸).
 *
 *   ۱) ساعتِ دستگاهِ ۱ را ۲۴ ساعت جلو بکش و ساعتِ دستگاهِ ۲ را روی سالِ ۲۰۰۰ بگذار.
 *   ۲) هر دو آفلاین قیمتِ یک کالا را عوض کنند و بفروشند.
 *   ۳) هر دو وصل شوند.
 *
 * بازیگرها: D1 و D2 — دو دستگاهِ صاحب با موتورِ واقعیِ Sync v3 (`lib/v3-devices.mjs`)، سرور با `SYNC_V3_ENABLED=1`.
 * هر سطرِ «نتیجه» یک assert است، جز آنچه در `PENDING` آمده.
 *
 * اجرا:  node test/e2e/run.mjs scenario-s28b
 */
import { boot, assert } from './lib/harness.mjs';
import { v3Device, rowOf, V3 } from './lib/v3-devices.mjs';

const E = await boot({ name: 'scenario-s28b', env: { SYNC_V3_ENABLED: '1' } });

/** سطرهایی که امروز ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  'موتورِ v3 در برنامه پیش‌فرض خاموش است (SYN-05)؛ نوارِ قرمزِ ساعت فقط با v3 ِ همان دستگاه (قاعده‌اش `core/sync/ClockSkew`، ClockSkewTest)',
];

const owner = await E.owner('owner-s28b@e2e.local', 'صاحبِ S28b', 'دکانِ S28b');
const P = { id: 'p-s28b', name: 'چای', salePrice: 50, purchasePrice: 40, stock: 20, barcodes: ['6260000028018'] };
const DAY = 86_400_000;
let D1; let D2; let T0;
const ops = (device) => E.db.many(
  `SELECT table_name, row_id, kind, hlc, client_hlc, skewed, status FROM sync_v3_ops WHERE account_id=$1 AND device_id=$2 ORDER BY server_seq`,
  [owner.shopId, device],
);

await E.step('S28b.0', 'دو دستگاه با ساعتِ درست همگام‌اند', async () => {
  D1 = v3Device(E, 'S28b-D1', owner.token, { products: [P] });
  await D1.engine.load();
  assert.equal((await D1.engine.sync()).ok, true);
  D2 = v3Device(E, 'S28b-D2', owner.token, {});
  await D2.engine.load();
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal(D2.find('products', P.id).salePrice, 50);
  T0 = Date.now();
});

await E.step('S28b.offline', 'ساعتِ D1 +۲۴ ساعت، D2 سالِ ۲۰۰۰؛ هر دو آفلاین قیمت را عوض می‌کنند و می‌فروشند', async () => {
  D1.offline = true; D2.offline = true;
  D1.shift = DAY;
  D2.shift = Date.UTC(2000, 0, 1) - Date.now();
  D1.edit((d) => { d.products = d.products.map((x) => (x.id === P.id ? { ...x, salePrice: 130 } : x)); });
  D1.sell('s28b-d1', P.id, 2, 130);
  D2.edit((d) => { d.products = d.products.map((x) => (x.id === P.id ? { ...x, salePrice: 140 } : x)); });
  D2.sell('s28b-d2', P.id, 1, 140);
  assert.ok(await D1.engine.record() >= 4, 'قیمت + سه ردیفِ فروش');
  assert.ok(await D2.engine.record() >= 4);
  assert.equal(new Date(D2.find('sales', 's28b-d2').createdAt).getUTCFullYear(), 2000, 'فاکتورِ D2 با ساعتِ غلطِ خودش');
}, { needs: ['S28b.0'] });

await E.step('S28b', 'وصل شدن: تغییرِ D1 نمی‌برد و به صندوق می‌رود؛ HLC ِ سرور جلو نمی‌رود؛ هیچ سندی گم نمی‌شود', async () => {
  D1.offline = false; D2.offline = false;
  assert.equal((await D1.engine.sync()).ok, true);
  assert.equal((await D2.engine.sync()).ok, true);
  assert.equal((await D1.engine.sync()).ok, true);
  assert.equal(D1.engine.queued() + D2.engine.queued(), 0, 'صفِ هر دو خالی');

  //  ۱) تغییرِ قیمتِ D1 در «آخرین نوشتن» برنده نمی‌شود و به صندوق می‌رود (clock_skew، برگرداندنی)
  const p1 = (await ops('S28b-D1')).find((o) => o.table_name === 'products' && o.row_id === P.id && o.kind === 'update');
  assert.ok(p1, 'op ِ قیمتِ D1 رسید و ثبت شد');
  assert.equal(p1.skewed, true);
  assert.notEqual((await rowOf(E, owner.shopId, 'products', P.id)).data.salePriceMinor, 13000, '۱۳۰ ِ ساعت‌جلو نبرد');
  const box = await D1.engine.fetchConflicts('open');
  const cf = box.find((c) => c.row_id === P.id && c.kind === 'clock_skew');
  assert.ok(cf, JSON.stringify(box.map((c) => c.kind)));
  assert.equal(cf.revertible, true);

  //  ۲) HLC ِ سرور با آن جلو نمی‌رود: هیچ HLC ِ مؤثری در سرور به ساعتِ D1 نمی‌رسد
  const max = (await E.db.one(`SELECT MAX(hlc) AS m FROM sync_v3_ops WHERE account_id=$1`, [owner.shopId])).m;
  assert.ok(V3.hlc.compare(max, p1.client_hlc) < 0, `HLC ِ سرور ${max} < ساعتِ D1 ${p1.client_hlc}`);
  assert.ok(V3.hlc.parse(max).wall < Date.now() + 5 * 60_000, 'ساعتِ سرور در مرزِ نگهبان ماند');

  //  ۵) هیچ سندی گم نمی‌شود: هر دو فروش با قلم و حرکتش روی سرور و روی هر دو دستگاه
  for (const id of ['s28b-d1', 's28b-d2']) {
    assert.ok(await rowOf(E, owner.shopId, 'sales', id), `فروشِ ${id} در سرور نیست`);
    assert.ok(await rowOf(E, owner.shopId, 'saleItems', `${id}-1`));
    assert.ok(await rowOf(E, owner.shopId, 'stockMovements', `${id}-m`));
    assert.ok(D1.find('sales', id) && D2.find('sales', id), `${id} روی هر دو دستگاه`);
  }
  //  ۴ (بخشی) HLC ِ مؤثرِ فروش‌ها زمانِ تصحیح‌شده است: نه «فردا» ِ D1، نه سالِ ۲۰۰۰ ِ D2
  const sales = [...await ops('S28b-D1'), ...await ops('S28b-D2')].filter((o) => o.table_name === 'sales');
  assert.equal(sales.length, 2);
  for (const s of sales) {
    const wall = V3.hlc.parse(s.hlc).wall;
    assert.ok(wall >= T0 - 60_000 && wall <= Date.now() + 60_000, `HLC ِ مؤثرِ ${s.row_id} = ${new Date(wall).toISOString()}`);
  }
  //  ۴) «تاریخِ تصحیح‌شده» ِ خودِ فاکتور: فیلدِ جدای `correctedAt` از سرور (زمانِ تصحیح‌شده)، `createdAt`/`date` همان ساعتِ دستگاه
  const want = { 's28b-d1': (at) => at > Date.now() + DAY / 2, 's28b-d2': (at) => new Date(at).getUTCFullYear() === 2000 };
  for (const [id, wrong] of Object.entries(want)) {
    const data = (await rowOf(E, owner.shopId, 'sales', id)).data;
    assert.ok(wrong(data.createdAt), `createdAt ِ ${id} بازنویسی شد: ${data.createdAt}`);
    assert.ok(data.correctedAt >= T0 - 60_000 && data.correctedAt <= Date.now() + 60_000, `correctedAt ِ ${id} = ${data.correctedAt}`);
    assert.equal('correctedAt' in (await rowOf(E, owner.shopId, 'saleItems', `${id}-1`)).data, false, 'قلمِ بی تاریخ فیلد نمی‌گیرد');
  }
  assert.equal(new Date(D1.find('sales', 's28b-d2').date).getUTCFullYear(), 2000, 'تاریخِ دستگاه دست نخورد');
  assert.ok(Math.abs(D1.find('sales', 's28b-d2').correctedAt - Date.now()) < 60 * 60_000, 'D1 فاکتورِ D2 را با تاریخِ تصحیح‌شده دارد');
  assert.ok(Math.abs(D2.find('sales', 's28b-d1').correctedAt - Date.now()) < 60 * 60_000, 'D2 فاکتورِ D1 را با تاریخِ تصحیح‌شده دارد');
  //  SYN-05: فاکتورِ هر دستگاه همان `correctedAt` ِ سرور را روی **خودِ** همان دستگاه هم دارد (`current` ِ پاسخِ push؛ Pull opِ خودش را نمی‌آورد)
  for (const [dev, id] of [[D1, 's28b-d1'], [D2, 's28b-d2']]) {
    assert.equal(dev.find('sales', id).correctedAt, (await rowOf(E, owner.shopId, 'sales', id)).data.correctedAt, `${id} روی دستگاهِ خودش`);
  }
  const price = (await rowOf(E, owner.shopId, 'products', P.id)).data.salePriceMinor;
  assert.deepEqual([D1.find('products', P.id).salePrice * 100, D2.find('products', P.id).salePrice * 100], [price, price], 'قیمتِ هر دو دستگاه = سرور');
  //  ۳) هشدارِ قرمزِ ساعت روی D2: اختلافِ ساعت با سرور از مرزِ نگهبان (`SKEW_MS`، ۵ دقیقه، همان `skew_limit_ms` ِ سرور) گذشت —
  //     همان قاعدهٔ `ClockSkew.level` ِ برنامه (core، ClockSkewTest «S28b»)؛ D1 هم جلوست و هشدار دارد
  assert.ok(Math.abs(D2.engine.status().clockOffsetMs) > V3.hlc.SKEW_MS, `clockOffsetMs = ${D2.engine.status().clockOffsetMs}`);
  assert.ok(D2.engine.status().clockOffsetMs > 20 * 365 * DAY, 'D2 عقب است');
  assert.ok(D1.engine.status().clockOffsetMs < -V3.hlc.SKEW_MS, 'D1 جلوست');
}, { needs: ['S28b.offline'] });

await E.step('S28b.fresh', 'دستگاهِ با ساعتِ درست: سندش هیچ فیلدِ تازه‌ای نمی‌گیرد', async () => {
  D1.shift = 0; D2.shift = 0;
  D1.sell('s28b-ok', P.id, 1, 50);
  assert.equal((await D1.engine.sync()).ok, true);
  const data = (await rowOf(E, owner.shopId, 'sales', 's28b-ok')).data;
  assert.equal('correctedAt' in data, false, JSON.stringify(Object.keys(data)));
  assert.equal(Math.abs(D1.engine.status().clockOffsetMs) <= V3.hlc.SKEW_MS, true, 'بی هشدار');
}, { needs: ['S28b'] });

await E.finish();
