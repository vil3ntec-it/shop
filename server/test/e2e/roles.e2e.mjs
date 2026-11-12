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
 *   ۴) «پشتیبانی»: چهار نقشِ مدیرِ پنل (ارشد، مدیر، مالی، فقط‌خواندن) ⇐ `adminRole` ِ پاسخِ ورود که پنل (`public/admin`)
 *      دکمه‌های «فقط ارشد» را با آن می‌چیند ⇔ همان درها (و در کرومیوم: پیدا/پنهان)؛ اجازهٔ موقتِ دسترسی (`support-access.v1`):
 *      درخواست فقط ارشد، در برنامه فقط صاحب می‌بیند و می‌پذیرد، و اجازهٔ زنده فقط برای همان درخواست‌کننده (دانلودِ پشتیبان).
 *
 * بازیگرها: صاحب و شش کارمند با کدِ دعوتِ نقش‌دار (مدیر، شاگرد، صندوق‌دار، انباردار، جمع‌آور، حسابدار) — همان راهِ پیوستنِ برنامه.
 * پیک (توکنِ دامنه‌دارِ جدا) این‌جا نیست: `courier.e2e.mjs` و `courier-scope.test.js` مرزش را می‌بندند. نوارِ «پشتیبانی در حالِ
 * مشاهده» در برنامهٔ دکان هنوز ساخته نشده (TEN-04)؛ دروازهٔ برنامه این‌جا همان `actualRole === 'owner'` ِ `GET /api/shop` است.
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

/* ═══════════════ «پشتیبانی»: نقش‌های مدیرانِ پنل و اجازهٔ موقتِ دسترسی (`support-access.v1`) ═══════════════
 * پنل (`public/admin`) دکمه‌ها و ابزارهای «فقط ارشد» را با یک قاعده می‌چیند: `adminRole === 'superadmin'` ِ پاسخِ ورود
 * (`platform.js` ‏setRole، `ops.js` ‏chainRole/loadPayments، `shops.js`)؛ سرور همان درها را با `requireSuperAdmin` و مرزِ نقش‌های
 * باریک (`lib/admin-roles.js`) جدا می‌سنجد. این‌جا هر چهار نقشِ مدیر وارد می‌شود و همان درها زده می‌شوند.
 */
const pwd = require('../../src/lib/password');
const ADMIN_ROLES = ['superadmin', 'admin', 'finance', 'viewer'];
const ADMIN_PASS = 'Admin!12345';
const admins = {};
await E.step('S90.admins', 'هر چهار نقشِ مدیرِ پنل (ارشد، مدیر، مالی، فقط‌خواندن) وارد می‌شود و پاسخِ ورود همان نقش را به پنل می‌دهد', async () => {
  const hash = await pwd.hashPassword(ADMIN_PASS);
  for (const r of ADMIN_ROLES.slice(1)) {
    await E.db.query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$3,$4,$5,'active',$6)`,
      [E.db.newId('adm'), `s90-${r}`, `مدیرِ ${r}`, hash, r, E.db.now()],
    );
  }
  for (const r of ADMIN_ROLES) {
    const username = r === 'superadmin' ? 'admin' : `s90-${r}`;
    const out = E.ok(await E.post('/api/admin/login', { username, password: ADMIN_PASS }), `ورودِ ${r}`);
    assert.equal(out.admin.role, r, `نقشِ ${r} در پاسخِ ورود`);
    admins[r] = { username, token: out.token, role: out.admin.role };
  }
});

/** درهای پشتِ دکمه/ابزارِ «فقط ارشد» ِ پنل ⇐ همان درخواستی که پنل می‌فرستد */
const SUPER_DOORS = [
  ['#beta-panel (پلن‌ها ⇐ کانالِ آزمایشی)', (t) => E.get('/api/admin/release/beta', t)],
  ['#btn-shop-beta / ذخیرهٔ کانالِ آزمایشی', (t) => E.put('/api/admin/release/beta', { shops: [owner.shopId], reason: 'S90 نقش‌ها' }, t)],
  ['#ops-chain-tools (بررسیِ زنجیره)', (t) => E.post('/api/admin/system/chain-verify', { rows: false }, t)],
  ['#ops-recon-tools (تطبیقِ فروشِ آنلاین)', (t) => E.get('/api/admin/system/reconciliation', t)],
  ['#btn-ops-pay-reconcile (تطبیقِ پرداخت‌ها)', (t) => E.post('/api/admin/payments/reconcile', {}, t)],
];

await E.step('S90.admin-doors', 'پنل: دکمه‌های «فقط ارشد» ⇔ سرور — ارشد می‌پذیرد، مدیر/مالی/فقط‌خواندن ۴۰۳ (همان قاعدهٔ `adminRole` ِ پنل)', async () => {
  const bad = [];
  const seen = { true: 0, false: 0 };
  for (const r of ADMIN_ROLES) {
    const gate = admins[r].role === 'superadmin';
    for (const [what, call] of SUPER_DOORS) {
      seen[gate] += 1;
      const res = await call(admins[r].token);
      if (gate ? deny(res.status) || res.status >= 500 : res.status !== 403) bad.push(`${r} ${what}: دروازه=${gate} سرور=${res.status}`);
    }
  }
  report(bad, 'دکمهٔ «فقط ارشد» ِ پنل با سرور نمی‌خواند');
  assert.ok(seen.true > 0 && seen.false > 0, JSON.stringify(seen));
}, { needs: ['S90.admins'] });

await E.step('S90.admin-panel', 'پنل در کرومیوم: برای مدیرِ عادی ابزارهای «فقط ارشد» پنهان و یادداشتِ «فقط با مدیرِ ارشد» پیدا؛ برای ارشد برعکس', async () => {
  const seenBy = {};
  for (const r of ['superadmin', 'admin']) {
    const P = await E.page(`panel-${r}`, { width: 1280, height: 900 });
    const { page } = P;
    await page.goto(`${E.apiBase}/admin/`, { waitUntil: 'domcontentloaded' });
    await page.fill('#in-username', admins[r].username);
    await page.fill('#in-password', ADMIN_PASS);
    await page.click('#btn-login');
    await page.waitForSelector('#app-view:not(.hidden)', { timeout: 10_000 });
    await page.click('button.tab[data-tab="plans"]');
    await page.waitForSelector('#tab-plans:not(.hidden)', { timeout: 10_000 });
    const beta = await page.isVisible('#beta-panel');
    await page.click('button.tab[data-tab="ops"]');
    await page.waitForSelector('#tab-ops:not(.hidden)', { timeout: 10_000 });
    //  `loadOps` ⇐ `chainRole()` پیش از هر درخواست: یکی از دو (ابزار یا یادداشت) پیدا می‌شود
    await page.waitForSelector('#ops-chain-tools:not(.hidden), #ops-chain-super:not(.hidden)', { timeout: 10_000 });
    seenBy[r] = {
      beta,
      chainTools: await page.isVisible('#ops-chain-tools'), chainNote: await page.isVisible('#ops-chain-super'),
      reconTools: await page.isVisible('#ops-recon-tools'), reconNote: await page.isVisible('#ops-recon-super'),
    };
    await E.shot(P, `S90-panel-${r}`);
    await P.ctx.close();
    E.pages.splice(E.pages.indexOf(P), 1);
  }
  const want = (sup) => ({ beta: sup, chainTools: sup, chainNote: !sup, reconTools: sup, reconNote: !sup });
  assert.deepEqual(seenBy.superadmin, want(true), 'پنلِ ارشد');
  assert.deepEqual(seenBy.admin, want(false), 'پنلِ مدیرِ عادی');
  //  همان درها از نشستِ همین دو نفر (گامِ پیش هر چهار نقش را دارد؛ این‌جا فقط پیوندِ «پیدا ⇔ پذیرفته»)
  for (const r of ['superadmin', 'admin']) {
    const chain = await SUPER_DOORS[2][1](admins[r].token);
    const recon = await SUPER_DOORS[3][1](admins[r].token);
    assert.equal(!deny(chain.status), seenBy[r].chainTools, `${r} بررسیِ زنجیره: پیدا=${seenBy[r].chainTools} سرور=${chain.status}`);
    assert.equal(!deny(recon.status), seenBy[r].reconTools, `${r} تطبیق: پیدا=${seenBy[r].reconTools} سرور=${recon.status}`);
  }
  assert.deepEqual(E.pageErrors, [], E.pageErrors.join(' | '));
}, { needs: ['S90.admins'] });

let backupId = '';
let grantId = '';
const GRANTS = `/api/admin/shops/${owner.shopId}/access-grants`;
const grantBody = { scope: 'export', minutes: 15, reason: 'S90: بازیابیِ پشتیبانِ خراب', consent: 'tenant' };
const download = (t) => E.get(`/api/admin/accounts/shop/${owner.shopId}/backups/${backupId}?reason=${encodeURIComponent('S90 پشتیبانی')}`, t);

await E.step('S90.support', '`support-access.v1`: درخواستِ اجازه فقط ارشد، فهرست برای همه جز مالی؛ در برنامه فقط صاحب می‌بیند و می‌پذیرد (شش نقشِ دیگر ۴۰۳ `owner_only`)', async () => {
  const bad = [];
  //  پنل: دیدنِ اجازه‌ها برای هر نقشِ مدیر جز مالی (`routes/admin-access-grants.js`)؛ نوشتن فقط ارشد
  for (const r of ADMIN_ROLES) {
    const list = await E.get(GRANTS, admins[r].token);
    const canList = r !== 'finance';
    if (canList ? list.status !== 200 : list.status !== 403) bad.push(`${r} فهرست: می‌خواست=${canList} سرور=${list.status}`);
    if (r === 'superadmin') continue;
    const asked = await E.post(GRANTS, grantBody, admins[r].token, { 'Idempotency-Key': E.key(`grant-${r}`) });
    if (asked.status !== 403) bad.push(`${r} درخواستِ اجازه: سرور=${asked.status} (فقط ارشد)`);
  }
  const g = E.ok(await E.post(GRANTS, grantBody, admins.superadmin.token, { 'Idempotency-Key': E.key('grant-super') }), 'درخواستِ ارشد', 201).grant;
  assert.equal(g.status, 'pending');
  grantId = g.id;
  //  برنامه: «پشتیبانی» فقط برای صاحب (`actualRole` ِ `GET /api/shop`) ⇔ سرور (`routes/shop-support-access.js` ‏ownerOnly)
  for (const r of ROLES) {
    const gate = shopOf[r].actualRole === 'owner';
    const res = await E.get('/api/shop/support-access', A[r].token);
    if (gate) {
      if (res.status !== 200) bad.push(`${r} دیدنِ اجازه‌ها: ${res.status}`);
      else if (!res.body.grants.some((x) => x.id === grantId && x.status === 'pending')) bad.push(`${r}: درخواستِ تازه در فهرستِ صاحب نیست`);
    } else if (res.status !== 403 || res.body?.error?.code !== 'owner_only') {
      bad.push(`${r} دیدنِ اجازه‌ها: دروازه=بسته سرور=${res.status} ${res.body?.error?.code || ''}`);
    }
    if (!gate) {
      const acc = await E.post(`/api/shop/support-access/${grantId}/accept`, {}, A[r].token);
      if (acc.status !== 403) bad.push(`${r} پذیرفتن: سرور=${acc.status} (فقط صاحب)`);
    }
  }
  report(bad, 'پشتیبانی');
  const acc = E.ok(await E.post(`/api/shop/support-access/${grantId}/accept`, {}, owner.token), 'پذیرفتنِ صاحب').grant;
  assert.equal(acc.status, 'active');
}, { needs: ['S90.0', 'S90.admins'] });

await E.step('S90.support-use', 'اجازهٔ زنده فقط برای همان درخواست‌کننده: با `admin.support_grant` روشن، دانلودِ پشتیبانِ دکان ⇐ ارشدِ دارای اجازه ۲۰۰؛ مدیرِ دیگر ۴۰۳؛ پس از لغوِ صاحب ارشد هم ۴۰۳ `grant_required`', async () => {
  const up = await fetch(`${E.apiBase}/api/me/backups?ext=json`, {
    method: 'POST', headers: { Authorization: `Bearer ${owner.token}`, 'Content-Type': 'application/octet-stream' },
    body: Buffer.from('دفترِ دکانِ نقش‌ها (S90)'),
  });
  const upBody = await up.json();
  assert.equal(up.status, 201, JSON.stringify(upBody));
  backupId = upBody.backup.id;
  await E.flag('security', 'admin.support_grant', true);
  try {
    const mine = await download(admins.superadmin.token);
    assert.equal(mine.status, 200, `ارشدِ دارای اجازه: ${mine.status} ${JSON.stringify(mine.body).slice(0, 200)}`);
    //  اجازه مالِ همان کس است: مدیرِ عادی (دکمهٔ دانلود را ندارد: فقط ارشد) و مالی هر دو بسته
    for (const r of ['admin', 'finance', 'viewer']) {
      const res = await download(admins[r].token);
      assert.equal(res.status, 403, `${r} دانلود: ${res.status}`);
    }
    E.ok(await E.post(`/api/shop/support-access/${grantId}/revoke`, {}, owner.token), 'لغوِ صاحب');
    const after = await download(admins.superadmin.token);
    assert.equal(after.status, 403, `ارشد پس از لغو: ${after.status}`);
    assert.equal(after.body?.error?.code, 'grant_required');
    const used = await E.db.many("SELECT action FROM audit_logs WHERE shop_id=$1 AND action LIKE '%support_access%' ORDER BY created_at, id", [owner.shopId]);
    const acts = used.map((x) => x.action);
    for (const a of ['shop.support_access_accepted', 'admin.support_access_used', 'shop.support_access_revoked']) {
      assert.ok(acts.includes(a), `سابقهٔ ${a} نیست: ${acts.join('، ')}`);
    }
  } finally {
    await E.flag('security', 'admin.support_grant', false);
  }
}, { needs: ['S90.support'] });

await E.finish();
