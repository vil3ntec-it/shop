/**
 * سنجه‌های مشترکِ «سفرهای واقعیِ دکان» (`journeys-*.e2e.mjs`) — پس از هر سفر، روی **هر** دستگاه و **سرور**:
 *
 *   • هیچ چیزی گم نشده: شناسه‌های هر مجموعهٔ دفتر روی همهٔ دستگاه‌ها و Snapshot ِ سرور یکی است، و هر ردیف همان
 *     محتوا را دارد (پس از گذر از درِ مدلِ برنامه — `projectLedger`)؛
 *   • هیچ چیزی دو بار نیست: هیچ شناسه‌ای در یک مجموعه تکرار نشده و هیچ شمارهٔ نمایشیِ فاکتوری (`سری-شماره`) دو بار
 *     نیامده؛ هیچ opی بی‌صدا رد نشده (`dropped`) مگر آنچه سفر خودش انتظار دارد؛
 *   • جمع‌ها درست است: موجودیِ هر کالا، ماندهٔ هر قرض‌دار، بدهیِ هر تأمین‌کننده، شمار و جمعِ فاکتورهای زنده و
 *     جمعِ برگشت‌ها روی هر دستگاه با همان عددِ سرور برابر است (و با عددِ مورد انتظارِ سفر، اگر داده شود).
 *
 * سرور = `GET /sync/v1/snapshot` (همان که دستگاهِ تازه می‌گیرد) ⇒ `fromSnapshot` ⇒ درِ مدل؛ پس «دستگاهِ سوم»ی
 * است که هرگز چیزی ننوشته.
 */
import assert from 'node:assert/strict';
import { core, emptyLedger, projectLedger } from './native.mjs';

const stable = (v) => {
  if (Array.isArray(v)) return `[${v.map(stable).join(',')}]`;
  if (v && typeof v === 'object') return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${stable(v[k])}`).join(',')}}`;
  return JSON.stringify(v);
};
const cents = (x) => Math.round(Number(x) * 100);

/** دفترِ سرور از Snapshot (با توکنِ هر دستگاهِ واردشده)، از درِ مدلِ برنامه */
export async function serverLedger(dev) {
  const r = await dev.api('GET', '/sync/v1/snapshot');
  assert.equal(r.status, 200, `Snapshot: ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
  return projectLedger(core.fromSnapshot(r.body, emptyLedger()));
}

/** هیچ شناسهٔ تکراری در هیچ مجموعه، هیچ شمارهٔ فاکتورِ تکراری (سری-شماره) */
export function assertNoDuplicates(name, d) {
  for (const c of core.COLLECTIONS) {
    const ids = (d[c] || []).map((x) => String(x.id));
    const dup = ids.filter((id, i) => ids.indexOf(id) !== i);
    assert.deepEqual(dup, [], `${name}: شناسهٔ تکراری در ${c}: ${dup.join('، ')}`);
  }
  const labels = (d.sales || []).filter((s) => Number(s.invoiceNumber) > 0)
    .map((s) => `${String(s.series || '').trim()}-${s.invoiceNumber}`);
  const dupLabels = labels.filter((l, i) => labels.indexOf(l) !== i);
  assert.deepEqual(dupLabels, [], `${name}: شمارهٔ فاکتورِ تکراری (سری-شماره): ${dupLabels.join('، ')}`);
}

/** جمع‌های دفتر — همان فرمول‌های `ShopStore` (موجودی، قرض، بدهیِ تأمین‌کننده) */
export function totalsOf(dev, d) {
  const stock = {};
  for (const p of d.products) stock[p.id] = Math.round(dev.stock(p.id, d) * 1000) / 1000;
  const debt = {};
  for (const x of d.debtors) debt[x.id] = cents(dev.debt(x.id, d)) / 100;
  const supplier = {};
  for (const x of d.suppliers) supplier[x.id] = cents(dev.supplierDebt(x.id, d)) / 100;
  const live = d.sales.filter((s) => s.status !== 'cancelled');
  return {
    stock, debt, supplier,
    sales: live.length,
    cancelled: d.sales.length - live.length,
    salesTotal: live.reduce((n, s) => n + cents(s.finalTotal), 0) / 100,
    returnsTotal: d.returns.reduce((n, r) => n + cents(r.amount), 0) / 100,
  };
}

/**
 * همهٔ دستگاه‌ها و سرور یک دفتر: همان شناسه‌ها، همان محتوا، بی تکرار، همان جمع‌ها.
 * @param {string} label
 * @param {import('./native.mjs').NativeDevice[]} devices  هر کدام پیش از این همگام (drain) شده‌اند
 * @param {{expect?: object, dropped?: number}} [o]  expect: بخشی از `totalsOf` که باید دقیقاً همین باشد؛
 *   dropped: شمارِ opهای ردشده‌ای که سفر انتظار دارد (پیش‌فرض صفر روی هر دستگاه)
 * @returns {Promise<object>} جمع‌های مشترک
 */
export async function assertSameEverywhere(label, devices, { expect = null, dropped = 0 } = {}) {
  const server = await serverLedger(devices[0]);
  const all = [...devices.map((d) => [d.label, projectLedger(d.ledger)]), ['سرور', server]];
  for (const [name, d] of all) assertNoDuplicates(`${label} · ${name}`, d);
  const [, ref] = all[all.length - 1];
  for (const [name, d] of all.slice(0, -1)) {
    for (const c of core.COLLECTIONS) {
      const mine = new Map((d[c] || []).map((x) => [String(x.id), x]));
      const theirs = new Map((ref[c] || []).map((x) => [String(x.id), x]));
      const lost = [...theirs.keys()].filter((id) => !mine.has(id));
      const extra = [...mine.keys()].filter((id) => !theirs.has(id));
      assert.deepEqual([lost, extra], [[], []], `${label} · ${name}/${c}: [نرسیده از سرور، نرسیده به سرور]`);
      for (const [id, row] of mine) {
        assert.equal(stable(row), stable(theirs.get(id)), `${label} · ${name}/${c}/${id}: محتوا با سرور فرق دارد`);
      }
    }
    for (const c of core.LIST_COLLECTIONS) {
      assert.deepEqual([...(d[c] || [])].sort(), [...(ref[c] || [])].sort(), `${label} · ${name}/${c}`);
    }
  }
  const t = totalsOf(devices[0], ref);
  for (const dev of devices) {
    assert.deepEqual(totalsOf(dev, dev.ledger), t, `${label} · ${dev.label}: جمع‌ها با سرور فرق دارد`);
    assert.equal(dev.queue.length, 0, `${label} · ${dev.label}: صف خالی نیست`);
    if (dropped === 0) assert.deepEqual(dev.dropped.map((x) => `${x.table}/${x.row_id}: ${x.why}`), [], `${label} · ${dev.label}: op ِ ردشده`);
  }
  if (expect) {
    for (const [k, v] of Object.entries(expect)) {
      if (v && typeof v === 'object') {
        for (const [id, n] of Object.entries(v)) assert.equal(t[k][id], n, `${label}: ${k}/${id}`);
      } else {
        assert.equal(t[k], v, `${label}: ${k} = ${t[k]}`);
      }
    }
  }
  return t;
}
