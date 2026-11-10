/**
 * سناریوی S48 ⌨ (§۷.۵) — گزارشِ پایانِ روز (RPT-02 تا RPT-04، PAY-20).
 *
 *   • Ctrl+8، «امروز»، Enter روی ردیفِ «نقد» = ریزِ تراکنش‌ها؛ Ctrl+P چاپ.
 *   • گزارشِ خودکار برای مالک ساعتِ تنظیم‌شده فرستاده می‌شود.
 *   • عددها با جمعِ اسناد برابرند (آزمونِ تطبیق).
 *
 * بازیگرها: A — گوشیِ صاحب (برنامه در سطحِ پروتکل): یک روزِ کاری — فروشِ نقد، فروشِ نسیه و یک برگشت — همه
 * با `createdAt` ِ همان روز (ساعتِ کابل)؛ صاحب گزارشِ خودکار را برای ساعتِ ۲۱ روشن می‌کند و کارگرِ هر دقیقهٔ سرور
 * (`owner-report.sweep`) در ۲۱:۰۲ ِ همان روز می‌دود. تطبیق: جمع‌ها مستقیم از خودِ اسنادِ دفترِ برنامه.
 *
 * پرچم فقط برای همین دکان: shop.owner_report.
 * اجرا:  node test/e2e/run.mjs scenario-s48
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'scenario-s48' });
const require = createRequire(import.meta.url);
const rep = require('../../src/lib/owner-report');
const { db } = E;

/** سطرهایی که این‌جا سنجیده نمی‌شوند یا ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  '«Enter روی ردیفِ نقد = ریزِ تراکنش‌ها» و Ctrl+P روی برنامهٔ کامپیوتر ساخته شده‌اند (زبانهٔ «پایانِ روز» ِ «گزارشِ تازه»: `KeyMap.REPORTS_DRILL` و `GLOBAL_PRINT`) و با `DayEndTest` ِ کاتلین سنجیده می‌شوند (ریزِ هر روش = رویدادهای دفترِ روز، جمع = خالصِ همان ردیف)؛ این سرتاسریِ پروتکلی دکمه‌ای در رابطِ کاتلین نمی‌زند. «گزارشِ تازه» تنظیمِ دستگاه و پیش‌فرض خاموش است (تصمیمِ صاحب‌کار)',
  'گزارشِ خودکار با پرچمِ `shop.owner_report` ِ پیش‌فرض خاموش (تصمیمِ صاحب‌کار)؛ کانالِ واتس‌اپ سرویس‌دهنده ندارد (D-09) — این‌جا «داخلِ برنامه»',
];

const owner = await E.owner('owner-s48@e2e.local', 'صاحبِ S48', 'دکانِ S48');
await E.flag(owner.shopId, 'shop.owner_report', true);
const KABUL = 4.5 * 3_600_000;
//  «امروز» ِ سناریو = دیروزِ کابل (روزی که کاملاً گذشته)
const today = new Date(Date.now() + KABUL - 86_400_000);
const local = (h, m = 0) => Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate(), h, m) - KABUL;
const DATE = new Date(local(12) + KABUL).toISOString().slice(0, 10);
let A; let rice; let tea; let debtor;
const minor = (x) => Math.round(Number(x) * 100);

await E.step('S48.0', 'یک روزِ کاری روی برنامه: سه فروشِ نقد، یک فروشِ نسیه، یک برگشت — همگام', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  let t = local(9);
  A.now = () => (t += 7 * 60_000);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 50 });
  tea = A.stockIn({ name: 'چای', price: 150, cost: 100, units: 50 });
  debtor = A.addDebtor('مشتریِ نسیه', '0791230048');
  const s1 = A.sell({ lines: [{ id: rice, qty: 2 }] });
  for (const r of [
    s1,
    A.sell({ lines: [{ id: tea, qty: 3 }] }),
    A.sell({ lines: [{ id: rice, qty: 1 }, { id: tea, qty: 1 }], discountValue: 50 }),
    A.sell({ lines: [{ id: rice, qty: 1 }], payment: 'credit', debtorId: debtor, paid: 0 }),
  ]) assert.equal(r.ok, true, JSON.stringify(r));
  const item = A.ledger.saleItems.find((i) => i.saleId === s1.saleId);
  assert.equal(A.returnItems(s1.saleId, { [item.id]: 1 }, 'خراب').ok, true);
  await A.drain();
  assert.equal(A.ledger.sales.length, 4);
  assert.ok(A.ledger.sales.some((x) => x.paymentMethod !== 'cash'), 'فروشِ نسیه');
});

await E.step('S48', 'ساعتِ ۲۱: گزارشِ خودکار یک بار برای صاحب فرستاده می‌شود و عددهایش با جمعِ اسناد برابر است', async () => {
  E.ok(await A.api('PUT', '/shop/owner-report', { active: true, hour: 21, channels: ['inapp'] }), 'تنظیمِ گزارشِ خودکار');
  await rep.sweep(local(20, 55));
  assert.equal((await db.many('SELECT 1 FROM owner_reports WHERE shop_id=$1', [owner.shopId])).length, 0, 'پیش از ساعت نه');
  await rep.sweep(local(21, 2));
  await rep.sweep(local(21, 3));
  const rows = await db.many('SELECT * FROM owner_reports WHERE shop_id=$1', [owner.shopId]);
  assert.equal(rows.length, 1, 'یک گزارش در روز');
  const r = rows[0];
  assert.equal(r.status, 'sent', JSON.stringify(r).slice(0, 300));
  assert.equal(r.report_date, DATE);
  const d = await db.one(`SELECT channel, status FROM notice_deliveries WHERE user_id=$1 AND notice_id=$2`, [owner.user.id, r.notice_id]);
  assert.deepEqual([d.channel, d.status], ['inapp', 'sent'], 'به صندوقِ اعلانِ صاحب رسید');

  //  آزمونِ تطبیق: جمع‌ها مستقیم از اسنادِ دفترِ برنامه (نه از هستهٔ گزارش)
  const s = typeof r.summary === 'string' ? JSON.parse(r.summary) : r.summary;
  const sales = A.ledger.sales.filter((x) => x.status !== 'cancelled');
  const returns = A.ledger.returns;
  const sum = (xs, f) => xs.reduce((n, x) => n + f(x), 0);
  assert.equal(s.invoices, sales.length);
  assert.equal(s.sales, sum(sales, (x) => minor(x.finalTotal)));
  assert.equal(s.discount, sum(sales, (x) => minor(x.discount)));
  assert.equal(s.returnCount, returns.length);
  assert.equal(s.returns, sum(returns, (x) => minor(x.amount)));
  assert.equal(s.netSales, s.sales - s.returns);
  const cash = sales.filter((x) => x.paymentMethod === 'cash');
  const credit = sales.filter((x) => x.paymentMethod !== 'cash');
  const cashRow = s.payments.find((p) => p.method === 'cash');
  assert.equal(cashRow.invoices, cash.length);
  assert.equal(cashRow.sales, sum(cash, (x) => minor(x.finalTotal)));
  assert.equal(s.cashNet + s.otherNet, s.netSales);
  assert.equal(sum(s.payments.filter((p) => p.method !== 'cash'), (p) => p.sales), sum(credit, (x) => minor(x.finalTotal)));
}, { needs: ['S48.0'] });

await E.step('S48.keys', 'Ctrl+8 «گزارش‌ها»، Enter «ریز شدن در ردیف» و Ctrl+P «چاپ» در keymap.json (تنها منبعِ میان‌برها)', async () => {
  const km = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '..', '..', '..', 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'core', 'keymap', 'keymap.json'), 'utf8'));
  const by = (id) => km.actions.find((a) => a.id === id);
  assert.deepEqual([by('global.page8').keys, by('global.page8').page, by('global.page8').status], [['Mod+8'], 'reports', 'live']);
  assert.deepEqual(by('global.print').keys, ['Mod+P']);
  //  «Enter روی ردیفِ نقد»: همان کلیدِ ریزِ ردیفِ گزارش (`KeyMap.REPORTS_DRILL`) در دامنهٔ «گزارش‌ها»، نه کلیدِ دیگری
  assert.deepEqual([by('reports.drill').scope, by('reports.drill').keys], ['reports', ['Enter']]);
  assert.ok(km.actions.filter((a) => a.scope === 'reports' && a.id !== 'reports.drill').every((a) => !a.keys.includes('Enter')), 'Enter در گزارش‌ها فقط ریز');
});

await E.finish();
