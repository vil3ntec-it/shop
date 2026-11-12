/**
 * سناریوی سرتاسریِ S90 (§۱۹.۱۲) — «پنلِ نقش‌ها»: دسترسیِ برنامه و سرور برای هر نقش روی هر عملِ مهم یکی است.
 *
 * برنامه دکمه‌ها را از سه چیزِ سرور می‌چیند و سرور همان کار را جدا می‌سنجد؛ این سناریو برای هر نقش هر سه را می‌خواند و
 * سپس خودِ کار را از راهِ HTTP ِ واقعی می‌زند. سازگار یعنی: دروازهٔ باز ⇒ سرور می‌پذیرد (نه ۴۰۱/۴۰۳)، دروازهٔ بسته ⇒ ۴۰۳.
 *   ۱) `GET /api/shop` ‏`permissions` (فهرستِ کلیدهای MATRIX که برنامه برای «مدیریت»، اعضا، کدِ شاگرد، سابقه و تنظیم می‌خواند)
 *      ⇔ درهای همان کلیدها؛
 *   ۲) `permissions` ِ پیکربندیِ هر بخش (سفارشِ خرید: `manage`/`receive`؛ ورود و خروج: `role` ⇒ «ثبتِ خروجِ دیگری» فقط
 *      صاحب و مدیر) ⇔ ساختنِ پیش‌نویس، رسیدِ کالا و زدن برای عضوِ دیگر؛
 *   ۳) `policy.role` ⇐ `PermCore.canWrite(role, table)` (برنامه پیش از ذخیره می‌پرسد) ⇔ پذیرفتن یا ردِ همان op در Sync v1.
 *
 * بازیگرها: صاحب و شش کارمند با کدِ دعوتِ نقش‌دار (مدیر، شاگرد، صندوق‌دار، انباردار، جمع‌آور، حسابدار) — همان راهِ پیوستنِ برنامه.
 * پیک (توکنِ دامنه‌دارِ جدا) و «پشتیبانی» (نقشِ مدیرانِ پنل) این‌جا نیستند: `role-route-isolation.test.js`، `courier-scope.test.js`
 * و `admin-roles.test.js` مرزِ سرورشان را می‌بندند.
 * اجرا:  node test/e2e/run.mjs roles
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'roles' });
const require = createRequire(import.meta.url);
const perm = require('../../src/lib/perm-core');
const { MATRIX } = require('../../src/lib/permissions');

const ROLES = ['owner', 'manager', 'staff', 'cashier', 'storekeeper', 'picker', 'accountant'];
const owner = await E.owner('owner-roles@e2e.local', 'صاحبِ نقش‌ها', 'دکانِ نقش‌ها');
for (const k of ['shop.purchase_orders', 'shop.staff_time']) await E.flag(owner.shopId, k, true);
const A = { owner };
for (const r of ROLES.slice(1)) A[r] = await E.staff(owner, `${r}-roles@e2e.local`, `کارمندِ ${r}`, r);

let n = 0;
const uid = (p) => `${p}${String(++n).padStart(10, '0')}`;
const deny = (s) => s === 401 || s === 403;
/** ناسازگاری‌ها جمع می‌شوند تا یک بار، همه با هم، دیده شوند */
function report(bad, what) {
  assert.deepEqual(bad, [], `${what}:\n  ${bad.join('\n  ')}`);
}

const shopOf = {};
await E.step('S90.0', 'هر نقش با کدِ دعوتِ خودش پیوست؛ `GET /api/shop` همان نقش، همان فهرستِ MATRIX و همان `policy` ِ هسته را به برنامه می‌دهد', async () => {
  for (const r of ROLES) {
    const s = E.ok(await E.get('/api/shop', A[r].token), `دکانِ ${r}`);
    shopOf[r] = s;
    assert.equal(s.actualRole, r, `نقشِ ${r}`);
    assert.equal(s.policy.role, r, `policy.role ِ ${r}`);
    const want = Object.keys(MATRIX).filter((k) => MATRIX[k].includes(r)).sort();
    assert.deepEqual([...s.permissions].sort(), want, `فهرستِ مجوزِ ${r}`);
    //  هر کلیدِ هسته‌ای که برنامه می‌خواند همان ارزیابیِ هسته است (بی لایهٔ دکان؛ برنامه هم همین را حساب می‌کند)
    assert.deepEqual(s.policy.effective, perm.effective(r, [{}, {}]), `policy.effective ِ ${r}`);
  }
});

/** کلیدِ MATRIX ⇐ همان دری که برنامه پشتِ آن کلید باز می‌کند */
const DOORS = [
  ['members.view', (t) => E.get('/api/shop/members', t)],
  ['staffcode.view', (t) => E.get('/api/shop/staff-codes', t)],
  ['staffcode.create', (t) => E.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, t)],
  ['audit.view', (t) => E.get('/api/shop/audit?limit=5', t)],
  ['settings.write', (t) => E.put('/api/shop/settings', { settings: { receiptNote: `نقش ${++n}` } }, t)],
];

await E.step('S90.doors', 'هر نقش × هر درِ مهمِ «مدیریت» (اعضا، کدِ شاگرد، ساختنِ کد، سابقه، تنظیم): کلید در فهرستِ برنامه ⇔ سرور می‌پذیرد؛ نبودِ کلید ⇔ ۴۰۳', async () => {
  const bad = [];
  const seen = { true: 0, false: 0 };
  for (const r of ROLES) {
    for (const [key, call] of DOORS) {
      const gate = shopOf[r].permissions.includes(key);
      seen[gate] += 1;
      const res = await call(A[r].token);
      if (gate ? deny(res.status) || res.status >= 500 : res.status !== 403) bad.push(`${r} ${key}: دروازه=${gate} سرور=${res.status}`);
    }
  }
  report(bad, 'دروازهٔ برنامه با سرور نمی‌خواند');
  //  هر دو سوی دروازه آزموده شد (نه فقط «همه باز» یا «همه بسته»)
  assert.ok(seen.true > 0 && seen.false > 0, JSON.stringify(seen));
}, { needs: ['S90.0'] });

const ROOT = '/api/shop/purchase-orders';
const draftBody = (tag) => ({
  supplierId: 'sup-roles', supplierName: 'تأمین‌کنندهٔ نقش‌ها',
  lines: [{ productId: `p-${tag}`, name: 'کالای نقش', unit: 'عدد', qtyMilli: 100_000, priceMinor: 1000 }],
});
let sentId = '';

await E.step('S90.po', 'سفارشِ خرید: `manage` ِ پیکربندی ⇔ ساختنِ پیش‌نویس، `receive` ⇔ ثبتِ رسیدِ کالا؛ برای هر هفت نقش', async () => {
  const o = E.ok(await E.post(ROOT, draftBody('base'), owner.token, { 'Idempotency-Key': E.key('po') }), 'سفارشِ پایه', 201).order;
  E.ok(await E.post(`${ROOT}/${o.id}/send`, {}, owner.token), 'فرستادن');
  sentId = o.id;
  const bad = [];
  const seen = { manage: { true: 0, false: 0 }, receive: { true: 0, false: 0 } };
  for (const r of ROLES) {
    const cfg = E.ok(await E.get(`${ROOT}/config`, A[r].token), `پیکربندیِ ${r}`);
    assert.equal(cfg.enabled, true);
    seen.manage[cfg.permissions.manage] += 1;
    seen.receive[cfg.permissions.receive] += 1;
    const made = await E.post(ROOT, draftBody(r), A[r].token, { 'Idempotency-Key': E.key(`po-${r}`) });
    if (cfg.permissions.manage ? made.status !== 201 : made.status !== 403) bad.push(`${r} ساختن: manage=${cfg.permissions.manage} سرور=${made.status}`);
    const rec = await E.post(`${ROOT}/${sentId}/receipts`, { id: uid(`rcp${r}`), lines: [{ lineId: 'l1', receivedMilli: 1000 }] }, A[r].token);
    if (cfg.permissions.receive ? ![200, 201].includes(rec.status) : rec.status !== 403) bad.push(`${r} رسید: receive=${cfg.permissions.receive} سرور=${rec.status}`);
  }
  report(bad, 'سفارشِ خرید');
  for (const g of Object.values(seen)) assert.ok(g.true > 0 && g.false > 0, JSON.stringify(seen));
}, { needs: ['S90.0'] });

await E.step('S90.time', 'ورود و خروج: هر نقش زدنِ خودش را ثبت می‌کند؛ «ثبتِ خروجِ دیگری» (کارتِ مدیر، `role` ِ پیکربندی) فقط صاحب و مدیر — سرور هم همان', async () => {
  const members = E.ok(await E.get('/api/shop/members', owner.token), 'اعضا').members;
  const memberOf = (r) => members.find((m) => m.userId === A[r].user.id).id;
  const bad = [];
  const t0 = Date.now() - 60_000;
  let k = 0;
  const cfgOf = {};
  for (const r of ROLES) {
    const cfg = E.ok(await E.get('/api/shop/staff-time/config', A[r].token), `پیکربندیِ ${r}`);
    assert.equal(cfg.enabled, true);
    assert.equal(cfg.role, r);
    cfgOf[r] = cfg;
    const mine = await E.post('/api/shop/staff-time/punches', { id: uid('punchin'), kind: 'in', at: t0 + (++k) }, A[r].token);
    if (mine.status !== 201) bad.push(`${r} زدنِ خودش: ${mine.status}`);
  }
  //  برنامه «ثبتِ خروجِ …» را فقط به صاحب و مدیر نشان می‌دهد (`StaffTimeHub`: manager = owner|manager)
  const OTHER = { owner: 'staff', manager: 'cashier' };
  for (const r of ROLES) {
    const gate = cfgOf[r].role === 'owner' || cfgOf[r].role === 'manager';
    const other = OTHER[r] || (r === 'storekeeper' ? 'picker' : 'storekeeper');
    const theirs = await E.post('/api/shop/staff-time/punches', { id: uid('punchout'), kind: 'out', at: t0 + 1000 + (++k), memberId: memberOf(other) }, A[r].token);
    if (gate ? theirs.status !== 201 : theirs.status !== 403) bad.push(`${r} خروجِ ${other}: دروازه=${gate} سرور=${theirs.status}`);
  }
  report(bad, 'ورود و خروج');
}, { needs: ['S90.0'] });

const TABLES = ['products', 'debtors', 'suppliers', 'expenses'];
const v1op = (table, rowId, fields) => ({ op_id: uid('01JRS90'), table, row_id: rowId, type: 'insert', ts: Date.now(), fields });
const FIELDS = {
  products: { name: 'کالای نقش', salePrice: 10, purchasePrice: 8, unit: 'عدد' },
  debtors: { name: 'قرض‌دارِ نقش', phone: '0700009090' },
  suppliers: { name: 'تأمین‌کنندهٔ نقش' },
  expenses: { amount: 5, category: 'متفرقه' },
};

await E.step('S90.sync', 'Sync v1: برنامه پیش از ذخیره `PermCore.canWrite(policy.role, جدول)` را می‌پرسد؛ همان op از همان نقش ⇔ سرور می‌پذیرد یا `table_not_allowed`', async () => {
  const bad = [];
  const seen = { true: 0, false: 0 };
  for (const r of ROLES) {
    const role = shopOf[r].policy.role;
    const ops = TABLES.map((t) => v1op(t, `${t}-${r}-${++n}`, FIELDS[t]));
    const res = E.ok(await E.post('/api/sync/v1/push', { device_id: `roles-${r}`, schema_version: 1, ops }, A[r].token), `push ${r}`);
    res.results.forEach((x, i) => {
      const t = TABLES[i];
      const app = perm.canWrite(role, t);
      seen[app] += 1;
      const got = x.status === 'applied' || x.reason === 'applied';
      if (app !== got) bad.push(`${r} ${t}: برنامه=${app} سرور=${x.reason || x.status}`);
      if (!app && x.reason !== 'table_not_allowed') bad.push(`${r} ${t}: ردِ بی دلیلِ روشن (${x.reason || x.status})`);
    });
  }
  report(bad, 'نوشتنِ همگام‌سازی');
  assert.ok(seen.true > 0 && seen.false > 0, JSON.stringify(seen));
  console.log(`     S90: ${ROLES.length} نقش × ${TABLES.length} جدول — ${seen.true} پذیرفته، ${seen.false} رد`);
}, { needs: ['S90.0'] });

await E.finish();
