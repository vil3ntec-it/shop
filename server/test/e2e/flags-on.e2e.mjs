/**
 * «همه با هم روشن» — قابلیت‌های پیش‌فرض‌خاموشِ دستهٔ ۲۷ و تنظیم‌های چندسرور، با هم روی یک دکان؛ و دکانِ دوم با
 * پرچم‌های خاموش که باید بیت‌به‌بیت همان رفتارِ پیشین را داشته باشد. چک‌لیستِ صاحب‌کار: docs/FLAGS-ON-fa.md.
 *
 * دکانِ «الف» (همه روشن): shop.approval_requests · shop.credit_cap · shop.custom_roles · shop.catalog ·
 *   shop.storefront.web_account · market.slug_hold · «پنهان کردنِ ناموجود» (`hideOutOfStock`) · محدودهٔ پیک؛ و پرچم‌های
 *   سرورِ دسته‌های ۲۸ تا ۳۱ (`FLAGS_ON_BATCH` ِ `lib/harness.mjs`، صریحاً `true` حتی پیش‌فرض‌روشن‌ها): auth.staff_session_guard ·
 *   shop.save_base · shop.ledger_merge · sync.pull_own_newer · shop.returns.line_cap · shop.sync_shadow_file ·
 *   shop.settings_sync · shop.count_absorb (گامِ FLAGS.B31). `desk.keystore` پرچمِ هر کامپیوتر است، نه سرور
 *   (`KekVaultTest`، `SessionKeyVaultTest`، `docs/FLAGS-ON-fa.md` §۱۰).
 * دکانِ «ب» (همان کارها، پرچم‌های تازه خاموش): فقط پیش‌نیازهای قدیمیِ هر دو دکان (سفارش، ویترین، پیک، ناحیه، رسانه)؛
 *   حالتِ «همه روشن» ِ کلِ مجموعه (`E2E_FLAGS_ON`) هم به آن نمی‌رسد.
 * سرور با همان تنظیم‌های تولیدیِ پیشنهادی: CLUSTER_ENABLED=1 · HOT_CACHE_MS=2000 · IMAGE_STORE=disk؛ روی PostgreSQL
 * (`E2E_DATABASE_URL`) DATABASE_REPLICA_URL هم (همان پایگاه = نسخهٔ بی‌تأخیر). روی PGlite خوشه و نسخهٔ خواندنی
 * خودشان خاموش می‌مانند (`cluster.enabled()`، `read-replica.configured()`) و همین در گامِ نخست سنجیده می‌شود.
 *
 * بازیگرها: گوشیِ صاحب، مدیر و دو صندوق‌دار (برنامه در سطحِ پروتکل، `lib/native.mjs`)، مشتریِ وب (API ِ همان
 * صفحهٔ ویترین)، دو پیک. هیچ خوابی برای هماهنگی نیست؛ تنها «تازه‌کردنِ دفترِ ویترین» با `catalog.refresh` (همان
 * که `storefront-hide-sold-out.test.js` می‌کند، چون خواندنِ ویترین دفتر را هر چند ثانیه یک بار تازه می‌کند).
 *
 * اجرا:  node test/e2e/run.mjs flags-on
 *        E2E_DATABASE_URL=postgres://shop:shoppass@127.0.0.1:55432/<db> node test/e2e/run.mjs flags-on
 */
import { createRequire } from 'node:module';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { boot, assert, FLAGS_ON_BATCH } from './lib/harness.mjs';
import { NativeDevice } from './lib/native.mjs';

const PG = /^postgres/i.test(process.env.E2E_DATABASE_URL || '');
const IMG = fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-flags-img-'));
const E = await boot({
  name: 'flags-on',
  env: {
    CLUSTER_ENABLED: '1', HOT_CACHE_MS: '2000', IMAGE_STORE: 'disk', IMAGE_STORE_DIR: IMG,
    RATE_STORE_CHECKOUT_MAX: '1000',
    ...(PG ? { DATABASE_REPLICA_URL: process.env.E2E_DATABASE_URL } : {}),
  },
});
const require = createRequire(import.meta.url);
const config = require('../../src/config');
const cluster = require('../../src/lib/cluster');
const replica = require('../../src/lib/read-replica');
const hot = require('../../src/lib/hot-cache');
const catalog = require('../../src/lib/catalog');
const F = require('../image-fixtures');
const { db } = E;

/** پرچم‌های تازهٔ پیش‌فرض‌خاموش (فقط «الف») و پیش‌نیازهای قدیمیِ هر دو دکان */
const NEW_FLAGS = ['shop.approval_requests', 'shop.credit_cap', 'shop.custom_roles', 'shop.catalog', 'shop.storefront.web_account', 'market.slug_hold'];
const BASE_FLAGS = ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.storefront.zones', 'shop.storefront.media'];
const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
const INSIDE = { lat: 34.53, lng: 69.17 };
const ZONE = 'کارته‌سه';
const CAP_MINOR = 50_000;   //  سقفِ قرضِ صندوق‌دار: ۵۰۰ افغانی

const ownerA = await E.owner('owner-a-flags@e2e.local', 'صاحبِ الف', 'دکانِ الف');
const managerA = await E.staff(ownerA, 'manager-a-flags@e2e.local', 'مدیرِ الف', 'manager');
const cashA = await E.staff(ownerA, 'cash-a-flags@e2e.local', 'صندوق‌دارِ الف');
const cash2A = await E.staff(ownerA, 'cash2-a-flags@e2e.local', 'صندوق‌دارِ دومِ الف');
const k1A = await E.staff(ownerA, 'k1-a-flags@e2e.local', 'پیکِ خیرخانه');
const k2A = await E.staff(ownerA, 'k2-a-flags@e2e.local', 'پیکِ کارته‌سه');
//  دکانِ شاهدِ خاموش: حالتِ «همه روشن» ِ کلِ مجموعه (`E2E_FLAGS_ON`) هم به آن نمی‌رسد
const ownerB = await E.owner('owner-b-flags@e2e.local', 'صاحبِ ب', 'دکانِ ب', { flagsOn: false });
const cashB = await E.staff(ownerB, 'cash-b-flags@e2e.local', 'صندوق‌دارِ ب');
const kB = await E.staff(ownerB, 'k-b-flags@e2e.local', 'پیکِ ب');

const SLUG_A = `flags-a-${Date.now().toString(36)}`;
const SLUG_B = `flags-b-${Date.now().toString(36)}`;
const A = {};   //  دستگاه‌ها و شناسه‌های «الف»
const B = {};   //  همان، «ب»

const why = (dev, saleId) => dev.dropped.filter((o) => o.row_id === saleId || o.fields?.saleId === saleId || o.fields?.refId === saleId).map((o) => `${o.table}:${o.why}`);
const serverRow = (shopId, table, id) => db.one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3 AND deleted_at IS NULL`, [shopId, table, id]);
const asked = async (dev, body) => E.ok(await dev.api('POST', '/shop/approval-requests', { key: E.key('apr'), reason: 'مشتریِ همیشگی', ...body }), 'درخواستِ تأیید', 201).request;
const levels = async (owner, pid) => {
  const st = E.ok(await E.get(`/api/shop/catalog/products/${pid}`, owner.token), 'موجودیِ کالا').product.stock;
  return { floor: st.levels.shop_floor, wh: st.levels.warehouse, transit: st.levels.in_transit, sellable: st.sellableMilli };
};
const xfer = (owner, p, body, key = E.key('xfer')) => E.post(`/api/shop/catalog/transfers${p}`, body, owner.token, { 'Idempotency-Key': key });
const listed = async (slug) => E.ok(await E.get(`/api/store/${slug}/products`), `فهرستِ ویترینِ ${slug}`).items.map((p) => p.id);
const fresh = async (shopId) => { await catalog.refresh(shopId); };
const setRole = (owner, role, perms) => E.roleLayer(owner, role, perms);
const sha = (b) => crypto.createHash('sha256').update(b).digest('hex');

/* ═════════════════════════════ آماده‌سازی ═════════════════════════════ */

await E.step('FLAGS.0', 'سرور با تنظیم‌های چندسرور؛ پرچم‌های تازه فقط برای «الف»؛ کالا از گوشیِ صاحب؛ ویترین و ناحیه برای هر دو', async () => {
  assert.equal(config.hotCache.ttlMs, 2000);
  assert.equal(config.imageStore.mode, 'disk');
  assert.equal(config.cluster.enabled, true);
  //  PGlite: خوشه و نسخهٔ خواندنی خودشان کنار می‌روند (یک فرآیند)؛ PostgreSQL: هر دو روشن و این نمونه رهبر
  assert.equal(cluster.enabled(), PG);
  assert.equal(replica.configured(), PG);
  if (PG) assert.equal(cluster.runsJobs(), true, 'تک نمونه ⇒ همین نمونه رهبر است و کارگرها می‌دوند');

  for (const k of BASE_FLAGS) { await E.flag(ownerA.shopId, k, true); await E.flag(ownerB.shopId, k, true); }
  for (const k of new Set([...NEW_FLAGS, ...FLAGS_ON_BATCH])) await E.flag(ownerA.shopId, k, true);
  //  پرچمِ تازه‌نوشته همان دم دیده می‌شود (حافظهٔ کوتاه با نوشتنِ پنل باطل می‌شود، نه پس از ۲ ثانیه)
  assert.equal(E.ok(await E.get('/api/shop/approval-requests/config', ownerA.token), 'config').enabled, true);
  assert.equal(E.ok(await E.get('/api/shop/approval-requests/config', ownerB.token), 'config').enabled, false);

  for (const [X, owner, staff, staff2, slug, label] of [[A, ownerA, cashA, cash2A, SLUG_A, 'A'], [B, ownerB, cashB, null, SLUG_B, 'B']]) {
    X.owner = await E.device(`${label}-owner`, owner.email);
    assert.equal((await X.owner.firstSync()).ok, true);
    X.rice = X.owner.stockIn({ name: 'برنج', price: 100, cost: 80, units: 50, unit: 'کیلو' });
    X.tea = X.owner.stockIn({ name: 'چای', price: 50, cost: 40, units: 3, unit: 'بسته' });
    X.oil = X.owner.stockIn({ name: 'روغن', price: 300, cost: 250, units: 6, unit: 'بوتل' });
    await X.owner.drain();
    X.cash = await E.device(`${label}-cash`, staff.email);
    assert.equal((await X.cash.firstSync()).ok, true);
    if (staff2) { X.cash2 = await E.device(`${label}-cash2`, staff2.email); assert.equal((await X.cash2.firstSync()).ok, true); }
    E.ok(await X.owner.api('PUT', '/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000099', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
    E.ok(await X.owner.api('PUT', '/shop/storefront/delivery', { zones: [{ name: ZONE, kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 0 }] }), 'ناحیهٔ ارسال');
    for (const id of [X.rice, X.tea, X.oil]) E.ok(await X.owner.api('PUT', `/shop/storefront/products/${id}`, { published: true }), 'انتشار');
  }
  //  صندوق‌دارِ «الف»: تخفیف «نیازمندِ تأیید» (پیش‌فرضِ شاگرد «ممنوع» است) و قرض تا ۵۰۰ افغانی
  E.ok(await setRole(ownerA, 'staff', { 'sales.discount': { mode: 'approve' }, 'sales.credit': { caps: { maxAmount: CAP_MINOR } } }), 'لایهٔ نقشِ شاگرد');
});

/* ═════════════════════════════ «الف»: همه روشن ═════════════════════════════ */

await E.step('FLAGS.APR', 'تأییدِ از راهِ دور، تخفیفِ صندوق‌دار روی Sync v1: سرورِ بی v3 همان دمِ پرسیدن «فقط با v3» می‌گوید، نه پس از تأییدِ بیهوده', async () => {
  A.mgr = await E.device('A-manager', managerA.email);
  const cfg = E.ok(await A.cash.api('GET', '/shop/approval-requests/config'), 'config');
  assert.deepEqual([cfg.enabled, cfg.approver], [true, false]);
  //  v1 ِ مشترک با پمپ تخفیفِ شاگرد را با نقش می‌سنجد ⇒ همان «ممنوع» ِ پیشین، با لایهٔ «نیازمندِ تأیید» هم
  const s0 = A.cash.sell({ lines: [{ id: A.rice, qty: 2 }], discountValue: 20 });
  assert.equal(s0.ok, true, JSON.stringify(s0));
  await A.cash.drain();
  assert.ok(why(A.cash, s0.saleId).includes('sales:discount_not_allowed'), JSON.stringify(A.cash.dropped.map((o) => [o.table, o.why])));
  assert.equal(await serverRow(ownerA.shopId, 'sales', s0.saleId), null);
  //  پرسیدن ⇒ ۴۰۹ ِ روشن؛ هیچ درخواستی در صندوقِ مدیر نمی‌نشیند
  const r = await A.cash.api('POST', '/shop/approval-requests', { key: E.key('apr'), perm: 'sales.discount', doc: 'sales/s-apr-v1', pctBp: 1000, amount: 2000, reason: 'مشتریِ همیشگی' });
  assert.deepEqual([r.status, r.body.error.code], [409, 'approval_needs_v3']);
  assert.deepEqual(E.ok(await A.mgr.api('GET', '/shop/approval-requests?status=pending'), 'صندوقِ مدیر').requests, []);
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.CAP', 'سقفِ قرض + تأییدِ مدیر: بالای سقف رد و به صندوقِ تعارض؛ صندوق‌دار می‌پرسد، مدیر روی گوشیِ خودش تأیید می‌کند، فاکتور می‌نشیند (یک‌بارمصرف)', async () => {
  A.mgr ||= await E.device('A-manager', managerA.email);
  const debtor = A.cash.addDebtor('حاجی کریم', '0799000111');
  await A.cash.drain();
  //  ۴۰۰ افغانی (زیرِ سقف) ⇒ می‌نشیند
  const low = A.cash.sell({ lines: [{ id: A.rice, qty: 4 }], payment: 'credit', debtorId: debtor, paid: 0 });
  await A.cash.drain();
  assert.deepEqual(why(A.cash, low.saleId), []);
  assert.ok(await serverRow(ownerA.shopId, 'sales', low.saleId));
  //  ۸۰۰ افغانی (بالای سقف) ⇒ رد با همراه‌ها، و یک ردیف برای صاحب در صندوقِ تعارض
  const over = A.cash.sell({ lines: [{ id: A.rice, qty: 8 }], payment: 'credit', debtorId: debtor, paid: 0 });
  await A.cash.drain();
  assert.ok(why(A.cash, over.saleId).includes('sales:approval_required'), JSON.stringify(A.cash.dropped.slice(-6).map((o) => [o.table, o.why])));
  assert.equal(await serverRow(ownerA.shopId, 'sales', over.saleId), null);
  const box = await db.one(`SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`,
    [ownerA.shopId, A.cash.dropped.find((o) => o.row_id === over.saleId && o.table === 'sales').op_id]);
  assert.equal(box.kind, 'approval_required');
  const detail = typeof box.detail === 'string' ? JSON.parse(box.detail) : box.detail;
  assert.deepEqual([detail.perm, detail.amount, detail.limit], ['sales.credit', 80_000, CAP_MINOR]);

  //  صندوق‌دار برای فاکتورِ تازه می‌پرسد؛ مدیر در صندوقِ خودش می‌بیند و تأیید می‌کند
  const saleId = `s-cap-${Date.now().toString(36)}`;
  const req = await asked(A.cash, { perm: 'sales.credit', doc: `sales/${saleId}`, amount: 80_000 });
  assert.equal(req.status, 'pending');
  const inbox = E.ok(await A.mgr.api('GET', '/shop/approval-requests?status=pending'), 'صندوقِ مدیر').requests;
  assert.deepEqual(inbox.map((x) => [x.id, x.perm, x.amount]), [[req.id, 'sales.credit', 80_000]]);
  assert.equal((await A.cash.api('POST', `/shop/approval-requests/${req.id}/approve`, {})).body.error.code, 'not_approver');
  assert.equal(E.ok(await A.mgr.api('POST', `/shop/approval-requests/${req.id}/approve`, { note: 'باشد' }), 'تأییدِ مدیر').request.status, 'approved');
  assert.equal(E.ok(await A.cash.api('GET', `/shop/approval-requests/${req.id}`), 'حالِ درخواست').request.status, 'approved');
  const s = A.cash.sell({ lines: [{ id: A.rice, qty: 8 }], payment: 'credit', debtorId: debtor, paid: 0, saleId, approval: { v: 1, request: req.id } });
  assert.equal(s.ok, true, JSON.stringify(s));
  await A.cash.drain();
  assert.deepEqual(why(A.cash, saleId), []);
  assert.equal(Number((await serverRow(ownerA.shopId, 'sales', saleId)).data.debtGiven), 800);
  assert.equal((await db.one('SELECT status FROM shop_approval_requests WHERE id=$1', [req.id])).status, 'used');
  const log = await db.one('SELECT perm, doc, approver_id, path FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [ownerA.shopId, `req_${req.id}`]);
  assert.deepEqual([log.perm, log.doc, log.approver_id, log.path], ['sales.credit', `sales/${saleId}`, managerA.user.id, 'v1']);
  //  یک‌بارمصرف: همان تأیید برای فاکتورِ دیگر نه
  const again = A.cash.sell({ lines: [{ id: A.rice, qty: 8 }], payment: 'credit', debtorId: debtor, paid: 0, approval: { v: 1, request: req.id } });
  await A.cash.drain();
  assert.equal(await serverRow(ownerA.shopId, 'sales', again.saleId), null);
  assert.ok(why(A.cash, again.saleId).some((w) => /^sales:approval_(mismatch|replayed)$/.test(w)), JSON.stringify(why(A.cash, again.saleId)));
  //  دفترِ صاحب: قرضِ مشتری = ۴۰۰ + ۸۰۰ (فاکتورهای ردشده نیستند)
  await A.owner.drain();
  assert.equal(A.owner.debt(debtor), 1200);
  assert.ok(A.owner.ledger.sales.some((x) => x.id === saleId));
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.ROLE', 'نقشِ سفارشی: «صندوق‌دارِ بی‌برگشت» برگشتِ همین عضو را می‌بندد؛ صندوق‌دارِ دیگر و لایهٔ نقش دست‌نخورده', async () => {
  const r = E.ok(await E.post('/api/shop/custom-roles', { name: 'صندوق‌دارِ بی‌برگشت', baseRole: 'staff', perms: { 'returns.create': { mode: 'deny' } } }, ownerA.token), 'نقشِ سفارشی', 201).role;
  //  گشادتر از نقشِ پایه پذیرفته نیست
  assert.equal((await E.post('/api/shop/custom-roles', { name: 'شاگردِ همه‌کاره', baseRole: 'staff', perms: { 'sales.cancel': { mode: 'allow' } } }, ownerA.token)).body.error.code, 'exceeds_role');
  A.noReturns = r.id;
  E.ok(await E.put(`/api/shop/custom-roles/members/${cashA.user.id}`, { roleId: r.id }, ownerA.token), 'دادنِ نقش');
  const pol = E.ok(await A.cash.api('GET', '/shop/permissions'), 'مجوزهای صندوق‌دار').policy;
  assert.equal(pol.layers.length, 3, '[نقش، نقشِ سفارشی، کاربر]');
  assert.equal(pol.effective['returns.create'].mode, 'deny');
  assert.deepEqual(pol.effective['sales.credit'], { mode: 'allow', caps: { maxAmount: CAP_MINOR } }, 'لایهٔ نقش زیرِ نقشِ سفارشی سر جایش است');

  const sale = A.cash.sell({ lines: [{ id: A.rice, qty: 3 }] });
  await A.cash.drain();
  assert.deepEqual(why(A.cash, sale.saleId), []);
  const item = A.cash.ledger.saleItems.find((i) => i.saleId === sale.saleId);
  assert.equal(A.cash.returnItems(sale.saleId, { [item.id]: 1 }, 'کیسه پاره بود').ok, true);
  await A.cash.drain();
  const rejected = A.cash.dropped.filter((o) => o.table === 'returns');
  assert.ok(rejected.length && rejected.every((o) => o.why === 'return_not_allowed'), JSON.stringify(A.cash.dropped.slice(-6).map((o) => [o.table, o.why])));
  const returns = () => db.many(`SELECT 1 FROM sync_rows WHERE account_id=$1 AND table_name='returns' AND deleted_at IS NULL`, [ownerA.shopId]);
  assert.equal((await returns()).length, 0);

  //  صندوق‌دارِ دوم (همان نقشِ شاگرد، بی نقشِ سفارشی) همان برگشت را ثبت می‌کند
  await A.cash2.drain();
  const item2 = A.cash2.ledger.saleItems.find((i) => i.saleId === sale.saleId);
  assert.equal(A.cash2.returnItems(sale.saleId, { [item2.id]: 1 }, 'کیسه پاره بود').ok, true);
  await A.cash2.drain();
  assert.deepEqual(A.cash2.dropped, []);
  assert.equal((await returns()).length, 1);
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.XFER', 'انتقالِ کالا: ارسال، دریافتِ ناقص در دو گام، کسریِ دلیل‌دار؛ تکرارِ همان گام بی اثرِ دوم؛ دفتر = مانده‌ها', async () => {
  await fresh(ownerA.shopId);
  assert.deepEqual(await levels(ownerA, A.oil), { floor: 6000, wh: 0, transit: 0, sellable: 6000 });
  const t = E.ok(await xfer(ownerA, '', { from: 'shop_floor', to: 'warehouse', lines: [{ productId: A.oil, qtyMilli: 6000 }], note: 'به گدامِ پشتی' }), 'ارسال', 201).transfer;
  assert.equal(t.status, 'in_transit');
  assert.deepEqual(await levels(ownerA, A.oil), { floor: 0, wh: 0, transit: 6000, sellable: 0 }, '«در مسیر» فروختنی نیست');

  const k1 = E.key('rcv');
  const p1 = E.ok(await xfer(ownerA, `/${t.id}/receive`, { final: false, lines: [{ productId: A.oil, receivedMilli: 4000 }] }, k1), 'دریافتِ ۱');
  assert.equal(p1.transfer.status, 'partial');
  //  پاسخ گم شد و همان گام دوباره رفت ⇒ همان، بی اثرِ دوم
  const again = await xfer(ownerA, `/${t.id}/receive`, { final: false, lines: [{ productId: A.oil, receivedMilli: 4000 }] }, k1);
  assert.equal(again.status, 200);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(await levels(ownerA, A.oil), { floor: 0, wh: 4000, transit: 2000, sellable: 4000 });

  const done = E.ok(await xfer(ownerA, `/${t.id}/receive`, { lines: [{ productId: A.oil, receivedMilli: 1000 }], reason: 'یک بوتل شکسته رسید' }), 'دریافتِ پایانی');
  assert.equal(done.transfer.status, 'received');
  assert.deepEqual(done.transfer.lines.map((l) => [l.sentMilli, l.receivedMilli, l.shortMilli, l.pendingMilli]), [[6000, 5000, 1000, 0]]);
  assert.deepEqual(await levels(ownerA, A.oil), { floor: 0, wh: 5000, transit: 0, sellable: 5000 }, 'هر واحد یک بار: ۶ − ۱ کسری');
  assert.equal((await xfer(ownerA, `/${t.id}/receive`, { final: false, lines: [{ productId: A.oil, receivedMilli: 1 }] })).body.error.code, 'transfer_closed');
  const tasks = E.ok(await E.get('/api/shop/catalog/tasks?status=open', ownerA.token), 'وظیفه‌ها').tasks.filter((x) => x.kind === 'transfer_short');
  assert.deepEqual(tasks.map((x) => [x.productId, x.detail.shortMilli]), [[A.oil, 1000]]);
  const rep = E.ok(await E.get('/api/shop/catalog/report', ownerA.token), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.HIDE', 'پنهان کردنِ ناموجود: چایِ فروخته‌شده از فهرستِ ویترین می‌رود، صفحهٔ کالا «ناموجود» می‌گوید؛ موجود شد ⇒ برمی‌گردد', async () => {
  E.ok(await A.owner.api('PUT', '/shop/storefront', { hideOutOfStock: true }), 'سیاستِ ناموجود');
  await fresh(ownerA.shopId);
  assert.deepEqual((await listed(SLUG_A)).sort(), [A.rice, A.tea, A.oil].sort());
  const s = A.cash.sell({ lines: [{ id: A.tea, qty: 3 }] });
  assert.equal(s.ok, true, JSON.stringify(s));
  await A.cash.drain();
  await fresh(ownerA.shopId);
  assert.deepEqual((await listed(SLUG_A)).sort(), [A.rice, A.oil].sort(), 'چایِ تمام‌شده پنهان');
  const detail = E.ok(await E.get(`/api/store/${SLUG_A}/products/${A.tea}`), 'صفحهٔ کالا');
  assert.equal(detail.product.stock.status, 'out_of_stock');
  A.owner.addEntry(A.tea, 2, 40, 'بسته');
  await A.owner.drain();
  await fresh(ownerA.shopId);
  assert.deepEqual((await listed(SLUG_A)).sort(), [A.rice, A.tea, A.oil].sort(), 'موجود شد ⇒ دوباره در فهرست');
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.WEB', 'حسابِ مشتری در وب: ثبتِ حساب، ذخیرهٔ نشانی و سفارشِ ارسال با همان نشانی؛ «سفارش‌های من»', async () => {
  const pub = E.ok(await E.get(`/api/store/${SLUG_A}`), 'ویترین');
  assert.equal(pub.features?.webAccount, true);
  const phone = '0791230777';
  const st = E.ok(await E.post(`/api/store/${SLUG_A}/verify/start`, { phone }), 'کدِ تأیید');
  const ticket = E.ok(await E.post(`/api/store/${SLUG_A}/verify/check`, { phone, code: st.devCode }), 'تأییدِ شماره').ticket;
  const acct = E.ok(await E.post(`/api/store/${SLUG_A}/account`, { name: 'مریم' }, null, { 'X-Store-Ticket': ticket }), 'ساختنِ حساب', 201);
  const sess = { 'X-Store-Session': acct.session };
  const addr = { label: 'خانه', text: 'کارته‌سه، سرکِ دوم', landmark: 'پشتِ مکتب', ...INSIDE };
  E.ok(await E.post(`/api/store/${SLUG_A}/account/addresses`, addr, null, sess), 'ذخیرهٔ نشانی', 201);
  const saved = E.ok(await E.get(`/api/store/${SLUG_A}/account/addresses`, null, sess), 'نشانی‌ها').addresses;
  assert.deepEqual(saved.map((x) => [x.label, x.lat, x.lng]), [['خانه', INSIDE.lat, INSIDE.lng]]);
  //  صفحه نشانیِ ذخیره را در فرم می‌گذارد؛ ثبت فقط با نشست (بی بلیت)
  const { text, landmark, lat, lng } = saved[0];
  const r = E.ok(await E.post(`/api/store/${SLUG_A}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مریم', phone },
    address: { text, landmark, lat, lng }, lines: [{ productId: A.rice, qtyMilli: 2000 }],
  }, null, { ...sess, 'Idempotency-Key': E.key('web') }), 'سفارشِ وب', 201).order;
  const mine = E.ok(await E.get(`/api/store/${SLUG_A}/orders`, null, sess), 'سفارش‌های من').orders;
  assert.deepEqual(mine.map((o) => o.orderNo), [r.orderNo]);
  const row = await db.one('SELECT id, address FROM online_orders WHERE shop_id=$1 AND order_no=$2', [ownerA.shopId, r.orderNo]);
  const a = typeof row.address === 'string' ? JSON.parse(row.address) : row.address;
  assert.equal(a.zone?.name, ZONE, 'ناحیه از پینِ نشانیِ ذخیره');
  A.webOrder = row.id;
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.AREA', 'محدودهٔ پیک: سفارشِ وب فقط به پیکِ همان ناحیه سپرده می‌شود؛ پیکِ بیرون ⇒ ۴۰۹ و هیچ مأموریتی', async () => {
  const c1 = E.ok(await A.owner.api('POST', '/shop/delivery/couriers', { userId: k1A.user.id, areas: ['خیرخانه'] }), 'پیکِ خیرخانه', 201).courier.id;
  const c2 = E.ok(await A.owner.api('POST', '/shop/delivery/couriers', { userId: k2A.user.id, areas: ['کارته سه'] }), 'پیکِ کارته‌سه', 201).courier.id;
  const cfg = E.ok(await A.owner.api('GET', '/shop/delivery/config'), 'تنظیمِ پیک');
  assert.deepEqual(cfg.areaChoices, [ZONE]);
  for (const [to, x] of [['accepted', { readyInMinutes: 15 }], ['picking'], ['packed']]) {
    E.ok(await A.owner.api('POST', `/shop/orders/${A.webOrder}/transition`, { to, ...(x || {}) }), `گذار به ${to}`);
  }
  const out = await A.owner.api('POST', `/shop/delivery/orders/${A.webOrder}/assign`, { mode: 'shop_courier', courierId: c1 }, { headers: { 'Idempotency-Key': E.key('asg') } });
  assert.equal(out.status, 409, JSON.stringify(out.body));
  assert.equal(out.body.error.code, 'courier_out_of_area');
  assert.equal((await db.many('SELECT 1 FROM delivery_assignments WHERE order_id=$1', [A.webOrder])).length, 0, 'سفارش در صفِ سپردنِ دستی ماند');
  const ok = E.ok(await A.owner.api('POST', `/shop/delivery/orders/${A.webOrder}/assign`, { mode: 'shop_courier', courierId: c2 }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن به پیکِ همان ناحیه', 201);
  const live = await db.many('SELECT courier_id FROM delivery_assignments WHERE order_id=$1', [A.webOrder]);
  assert.deepEqual(live.map((x) => x.courier_id), [c2]);
  assert.ok(ok.assignment.id);
}, { needs: ['FLAGS.WEB'] });

await E.step('FLAGS.IMG', 'انبارِ عکس روی دیسک: عکسِ کالا از گوشی به پوشه می‌رود (نه پایگاه) و ویترین همان بایت‌ها را می‌دهد', async () => {
  const pic = F.jpeg(320, 320, { seed: 975 });
  E.ok(await A.owner.api('PUT', `/sync/v1/photos/${A.rice}`, { data: pic.toString('base64') }), 'عکسِ کالا');
  const row = await db.one('SELECT (data IS NOT NULL) AS in_db, store_key FROM product_photos WHERE account_id=$1 AND product_id=$2', [ownerA.shopId, A.rice]);
  assert.equal(row.in_db, false, 'بایت در پایگاه نیست');
  assert.ok(row.store_key && fs.existsSync(path.join(IMG, row.store_key)), 'شیء روی دیسک');
  const res = await fetch(`${E.apiBase}/api/store/${SLUG_A}/products/${A.rice}/photo`);
  assert.equal(res.status, 200);
  assert.equal(sha(Buffer.from(await res.arrayBuffer())), sha(pic));
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.SLUG', 'نگه‌داشتنِ نشانی: «الف» نشانی‌اش را عوض می‌کند و «ب» نشانیِ کهنه را نمی‌گیرد؛ «ب» (خاموش) همان پیشین', async () => {
  const next = `${SLUG_A}-n`;
  E.ok(await A.owner.api('PUT', '/shop/storefront', { slug: next }), 'نشانیِ تازهٔ «الف»');
  const taken = await B.owner.api('PUT', '/shop/storefront', { slug: SLUG_A });
  assert.equal(taken.status, 409, JSON.stringify(taken.body));
  assert.equal(taken.body.error.code, 'slug_taken');
  //  «ب» بی پرچم: نشانیِ رهاشده‌اش همان دم آزاد است (رفتارِ پیشین)
  const nextB = `${SLUG_B}-n`;
  E.ok(await B.owner.api('PUT', '/shop/storefront', { slug: nextB }), 'نشانیِ تازهٔ «ب»');
  E.ok(await A.owner.api('PUT', '/shop/storefront', { slug: SLUG_B }), '«الف» نشانیِ رهاشدهٔ «ب» را می‌گیرد');
  E.ok(await A.owner.api('PUT', '/shop/storefront', { slug: next }), '«الف» به نشانیِ خودش برمی‌گردد');
  A.slug = next;
  B.slug = nextB;
}, { needs: ['FLAGS.0'] });

/* ═════════════════════════════ «الف»: پرچم‌های دسته‌های ۲۸ تا ۳۱ با هم ═════════════════════════════ */

await E.step('FLAGS.B31', 'پرچم‌های تازه با هم: هر کدام از درِ خودش؛ شمارش + فروشِ دیررس + انتقال روی دفترِ سرور؛ تنظیماتِ دکان با نقشِ سفارشی؛ ورودِ دوبارهٔ شاگرد با نقشِ سفارشی', async () => {
  const orders = require('../../src/lib/orders');
  for (const k of FLAGS_ON_BATCH) assert.equal(await orders.flagOf(ownerA.shopId, k), true, k);
  //  ۱) هر پرچم از درِ خودش (همان پرسشِ برنامه)
  assert.deepEqual(E.ok(await A.owner.api('GET', '/shop/ledger-merge/config?v=3'), 'ادغامِ دفتر'), { enabled: true, v: 2, base: true, shadowFile: true });
  for (const dev of [A.owner, A.cash, A.cash2]) assert.equal(await dev.stockFlags(), true, `${dev.label}: countAbsorb`);

  //  ۲) INV-10 با دفترِ سرور و انتقال: صندوق‌دارِ دوم آفلاین ۲ برنج می‌فروشد ⇐ صاحب قفسه را می‌شمارد (عددِ درست) ⇐
  //     ۵ برنج به گدام می‌رود ⇐ فروشِ دیررس می‌رسد ⇒ هیچ‌جا دو بار کم نیست؛ قفسه = شمرده − انتقال؛ دفتر = مانده‌ها
  for (const dev of [A.owner, A.cash, A.cash2]) await dev.drain();
  await A.owner.drain();
  await fresh(ownerA.shopId);
  const rice0 = A.owner.stock(A.rice);
  assert.deepEqual(await levels(ownerA, A.rice), { floor: rice0 * 1000, wh: 0, transit: 0, sellable: rice0 * 1000 }, 'پیش از شمارش: سرور = دستگاه');
  A.cash2.offline = true;
  const late = A.cash2.sell({ lines: [{ id: A.rice, qty: 2 }] });
  assert.equal(late.ok, true, JSON.stringify(late));
  await new Promise((r) => setTimeout(r, 5));
  const counted = rice0 - 2;
  assert.equal(A.owner.count({ [A.rice]: counted }, { [A.rice]: Date.now() }), 1);
  await A.owner.drain();
  await fresh(ownerA.shopId);
  assert.equal((await levels(ownerA, A.rice)).floor, counted * 1000, 'شمارش در سرور');
  const t = E.ok(await xfer(ownerA, '', { from: 'shop_floor', to: 'warehouse', lines: [{ productId: A.rice, qtyMilli: 5000 }], note: 'پس از شمارش' }), 'ارسال', 201).transfer;
  E.ok(await xfer(ownerA, `/${t.id}/receive`, { lines: [{ productId: A.rice, receivedMilli: 5000 }] }), 'دریافت');
  A.cash2.offline = false;
  await A.cash2.drain();
  for (const dev of [A.owner, A.cash, A.cash2]) await dev.drain();
  await fresh(ownerA.shopId);
  assert.deepEqual(why(A.cash2, late.saleId), []);
  assert.ok(await serverRow(ownerA.shopId, 'sales', late.saleId), 'فروش در دفترِ سرور');
  for (const dev of [A.owner, A.cash, A.cash2]) assert.equal(dev.stock(A.rice), counted, `${dev.label}: همان شمرده`);
  assert.deepEqual(await levels(ownerA, A.rice), { floor: (counted - 5) * 1000, wh: 5000, transit: 0, sellable: counted * 1000 }, 'هیچ‌جا دو بار کم نیست');
  const mv = E.ok(await A.owner.api('GET', `/shop/catalog/moves?productId=${A.rice}&limit=200`), 'حرکت‌ها').items;
  assert.deepEqual(mv.filter((m) => m.reason === 'count_absorb').map((m) => m.deltaOnHandMilli), [2000]);
  const rep = E.ok(await E.get('/api/shop/catalog/report', ownerA.token), 'تطبیق');
  assert.equal(rep.ok, true, JSON.stringify(rep.mismatches));

  //  ۳) تنظیماتِ دکان + نقشِ سفارشی: مدیری که «تنظیمات» برایش بسته است می‌خواند، `write: false` می‌گیرد و PUT ۴۰۳ —
  //     همان پاسخ از دو در (پیش‌تر GET فقط نقش را می‌دید ⇒ `true` و برنامه قاعدهٔ دکان را هرگز نمی‌نشاند)
  A.mgr ||= await E.device('A-manager', managerA.email);
  const P = (key, t, v) => ({ store: 'tohid-policy', key, t, v });
  const put1 = E.ok(await A.owner.api('PUT', '/shop/settings-sync', { entries: [P('return_window_days', 'i', 7)], device_id: A.owner.deviceId }), 'تنظیمِ صاحب');
  assert.equal(put1.applied.length, 1);
  const noSettings = E.ok(await E.post('/api/shop/custom-roles', { name: 'مدیرِ بی‌تنظیمات', baseRole: 'manager', perms: { 'settings.write': { mode: 'deny' } } }, ownerA.token), 'نقشِ سفارشیِ مدیر', 201).role;
  E.ok(await E.put(`/api/shop/custom-roles/members/${managerA.user.id}`, { roleId: noSettings.id }, ownerA.token), 'دادنِ نقش به مدیر');
  const seen = E.ok(await A.mgr.api('GET', '/shop/settings-sync'), 'تنظیماتِ مدیر');
  assert.deepEqual([seen.enabled, seen.permissions.write], [true, false], 'مدیرِ بسته‌شده فقط می‌خواند');
  assert.deepEqual(seen.rows.filter((x) => x.key === 'return_window_days').map((x) => x.v), [7]);
  const mgrPut = await A.mgr.api('PUT', '/shop/settings-sync', { entries: [P('return_window_days', 'i', 30)] });
  assert.deepEqual([mgrPut.status, mgrPut.body.error?.code], [403, 'permission_denied']);
  for (const dev of [A.cash, A.cash2]) {
    assert.equal(E.ok(await dev.api('GET', '/shop/settings-sync'), 'تنظیماتِ صندوق‌دار').permissions.write, false);
    assert.equal((await dev.api('PUT', '/shop/settings-sync', { entries: [P('return_window_days', 'i', 30)] })).status, 403);
  }
  E.ok(await E.put(`/api/shop/custom-roles/members/${managerA.user.id}`, { roleId: null }, ownerA.token), 'برداشتنِ نقش');
  assert.equal(E.ok(await A.mgr.api('GET', '/shop/settings-sync'), 'تنظیماتِ مدیر').permissions.write, true, 'نقشِ پایه ⇒ می‌نویسد');

  //  ۴) ورودِ دوبارهٔ شاگرد (رازِ دستگاه) + نقشِ سفارشی: همان حساب، همان نقشِ «بی‌برگشت»؛ همان شناسهٔ گوشی بی راز ⇒ رد
  const code = E.ok(await A.owner.api('POST', '/shop/staff-code', { role: 'staff', maxUses: 1 }), 'کدِ شاگرد', [200, 201]).code;
  const K = new NativeDevice(E, 'A-code-staff');
  await K.loginStaff(code, 'رحیم');
  assert.ok(K.staffKey, 'رازِ دستگاه');
  E.ok(await E.put(`/api/shop/custom-roles/members/${K.user.id}`, { roleId: A.noReturns }, ownerA.token), 'نقشِ بی‌برگشت برای شاگردِ کد');
  const id = K.user.id;
  assert.equal((await K.logout()).blocked, false);
  await K.loginStaff(code, 'رحیم');
  assert.equal(K.user.id, id, 'رازِ دستگاه ⇒ همان حساب');
  assert.equal(E.ok(await K.api('GET', '/shop/permissions'), 'مجوزهای شاگردِ کد').policy.effective['returns.create'].mode, 'deny', 'نقشِ سفارشی سرِ جایش');
  const thief = new NativeDevice(E, 'A-code-thief', { deviceId: K.deviceId });
  await assert.rejects(() => thief.loginStaff(code, 'رحیم'), (e) => [401, 403].includes(e.status), 'همان شناسهٔ گوشی بی راز');
}, { needs: ['FLAGS.0', 'FLAGS.ROLE'] });

/* ═════════════════════════════ «ب»: پرچم‌ها خاموش ⇒ همان پیشین ═════════════════════════════ */

await E.step('FLAGS.OFF', 'دکانِ «ب» با پرچم‌های خاموش: همان کارها همان پاسخ‌های پیشین را می‌گیرند', async () => {
  //  تأییدِ از راهِ دور: فقط /config، بقیه ۴۰۳
  //  #988: پاسخ کلیدِ `v3` (Sync v3 ِ همین سرور) هم دارد — مقدارش مالِ سرور است، نه پرچمِ دکان
  const cfgB = E.ok(await B.cash.api('GET', '/shop/approval-requests/config'), 'config');
  assert.equal(typeof cfgB.v3, 'boolean');
  assert.deepEqual(cfgB, { enabled: false, approver: false, ttlMs: 600000, v3: cfgB.v3 });
  const ask = await B.cash.api('POST', '/shop/approval-requests', { key: E.key('apr'), perm: 'sales.discount', doc: 'sales/s-b', pctBp: 1000, amount: 2000, reason: 'x' });
  assert.deepEqual([ask.status, ask.body.error.code], [403, 'approval_requests_disabled']);
  //  تخفیفِ شاگرد همان «ممنوع» ِ پیشین (لایهٔ نقشی گذاشته نشده)
  const d = B.cash.sell({ lines: [{ id: B.rice, qty: 2 }], discountValue: 20 });
  await B.cash.drain();
  assert.ok(why(B.cash, d.saleId).includes('sales:discount_not_allowed'), JSON.stringify(B.cash.dropped.map((o) => [o.table, o.why])));

  //  سقفِ قرض ذخیره نمی‌شود و قرضِ بزرگ همان امروز می‌نشیند
  const cap = await setRole(ownerB, 'staff', { 'sales.credit': { caps: { maxAmount: CAP_MINOR } } });
  assert.deepEqual([cap.status, cap.body.error.code], [400, 'not_enforceable_yet']);
  const debtor = B.cash.addDebtor('حاجی کریم', '0799000222');
  const big = B.cash.sell({ lines: [{ id: B.rice, qty: 8 }], payment: 'credit', debtorId: debtor, paid: 0 });
  await B.cash.drain();
  assert.deepEqual(why(B.cash, big.saleId), []);
  assert.equal(Number((await serverRow(ownerB.shopId, 'sales', big.saleId)).data.debtGiven), 800);
  //  صندوقِ تعارضِ «ب» فقط همان ردِ تخفیفِ پیشین را دارد، نه هیچ «نیازمندِ تأیید»
  assert.deepEqual((await db.many('SELECT kind FROM sync_v3_conflicts WHERE account_id=$1', [ownerB.shopId])).map((x) => x.kind), ['perm_denied']);

  //  نقشِ سفارشی
  const cr = await E.post('/api/shop/custom-roles', { name: 'صندوق‌دار', baseRole: 'staff', perms: {} }, ownerB.token);
  assert.deepEqual([cr.status, cr.body.error.code], [403, 'custom_roles_disabled']);
  assert.equal(E.ok(await B.cash.api('GET', '/shop/permissions'), 'مجوزها').policy.layers.length, 2);
  //  برگشتِ شاگرد آزاد (همان امروز)
  const item = B.cash.ledger.saleItems.find((i) => i.saleId === big.saleId);
  assert.equal(B.cash.returnItems(big.saleId, { [item.id]: 1 }, 'برگشت').ok, true);
  await B.cash.drain();
  assert.ok(!B.cash.dropped.some((o) => o.table === 'returns'));

  //  انتقالِ کالا: درِ پیش‌ساختهٔ کاتالوگ با پرچمِ خاموش همان درِ ناشناخته (۴۰۴)
  const t = await xfer(ownerB, '', { from: 'shop_floor', to: 'warehouse', lines: [{ productId: B.oil, qtyMilli: 1000 }] });
  assert.deepEqual([t.status, t.body.error.code], [404, 'not_found']);

  //  ویترین: بی حسابِ وب؛ «پنهان کن» بی دفترِ کالا هیچ کالایی را پنهان نمی‌کند
  assert.equal(E.ok(await E.get(`/api/store/${B.slug}`), 'ویترینِ «ب»').features?.webAccount, undefined);
  const hideB = E.ok(await E.get('/api/shop/storefront/config', ownerB.token), 'تنظیمِ ویترین');
  assert.equal(hideB.settings.hideOutOfStock, false, 'پیش‌فرض: نشان بده');
  const tea = B.cash.sell({ lines: [{ id: B.tea, qty: 3 }] });
  await B.cash.drain();
  assert.deepEqual(why(B.cash, tea.saleId), []);
  assert.deepEqual((await listed(B.slug)).sort(), [B.rice, B.tea, B.oil].sort(), 'چایِ تمام‌شده هنوز در فهرست (همان پیشین)');
  E.ok(await B.owner.api('PUT', '/shop/storefront', { hideOutOfStock: true }), 'سیاستِ ناموجود ِ «ب»');
  const items = E.ok(await E.get(`/api/store/${B.slug}/products`), 'فهرست').items;
  assert.deepEqual(items.map((p) => p.id).sort(), [B.rice, B.tea, B.oil].sort(), 'موجودیِ نامعلوم هرگز پنهان نمی‌شود');
  assert.ok(items.every((p) => p.stock.status === 'unverified'), JSON.stringify(items.map((p) => p.stock)));

  //  پیکِ بی محدوده: هر ناحیه
  const kc = E.ok(await B.owner.api('POST', '/shop/delivery/couriers', { userId: kB.user.id }), 'پیکِ «ب»', 201).courier;
  assert.deepEqual(kc.areas, []);
  const phone = '0791230888';
  const st = E.ok(await E.post(`/api/store/${B.slug}/verify/start`, { phone }), 'کدِ تأیید');
  const ticket = E.ok(await E.post(`/api/store/${B.slug}/verify/check`, { phone, code: st.devCode }), 'تأییدِ شماره').ticket;
  const o = E.ok(await E.post(`/api/store/${B.slug}/checkout`, {
    fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ ب', phone },
    address: { text: 'کارته‌سه', landmark: 'پشتِ مکتب', ...INSIDE }, lines: [{ productId: B.rice, qtyMilli: 1000 }],
  }, null, { 'X-Store-Ticket': ticket, 'Idempotency-Key': E.key('web') }), 'سفارشِ «ب»', 201).order;
  const oid = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [ownerB.shopId, o.orderNo])).id;
  for (const [to, x] of [['accepted', { readyInMinutes: 15 }], ['picking'], ['packed']]) E.ok(await B.owner.api('POST', `/shop/orders/${oid}/transition`, { to, ...(x || {}) }), `گذار به ${to}`);
  E.ok(await B.owner.api('POST', `/shop/delivery/orders/${oid}/assign`, { mode: 'shop_courier', courierId: kc.id }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201);

  //  پرچم‌های «الف» به «ب» نرسید
  for (const k of NEW_FLAGS) assert.equal(await require('../../src/lib/orders').flagOf(ownerB.shopId, k), false, k);
  //  پرچم‌های دسته‌های ۲۸ تا ۳۱ بی هیچ ردیف: پیش‌فرض‌روشن‌ها روشن، پیش‌فرض‌خاموش‌ها همان پیشین
  assert.deepEqual(E.ok(await B.owner.api('GET', '/shop/ledger-merge/config?v=3'), 'ادغامِ دفترِ «ب»'), { enabled: true, v: 2, base: true, shadowFile: false });
  assert.deepEqual(E.ok(await B.owner.api('GET', '/shop/ledger-merge/config'), 'ادغامِ دفترِ «ب» (برنامهٔ کهنه)'), { enabled: false });
  assert.equal(await B.cash.stockFlags(), false, 'countAbsorb ِ «ب»');
  assert.deepEqual(E.ok(await B.owner.api('GET', '/shop/settings-sync'), 'تنظیماتِ «ب»'), { enabled: false });
  const ssB = await B.owner.api('PUT', '/shop/settings-sync', { entries: [{ store: 'tohid-policy', key: 'return_window_days', t: 'i', v: 7 }] });
  assert.deepEqual([ssB.status, ssB.body.error?.code], [403, 'settings_sync_disabled']);
}, { needs: ['FLAGS.0'] });

await E.step('FLAGS.CACHE', 'حافظهٔ کوتاهِ پرچم: خاموش کردنِ پرچم از پنل همان دم اثر دارد (نه پس از ۲ ثانیه)', async () => {
  await E.flag(ownerA.shopId, 'shop.approval_requests', false);
  const r = await A.cash.api('POST', '/shop/approval-requests', { key: E.key('apr'), perm: 'sales.discount', doc: 'sales/s-x', pctBp: 1000, amount: 2000, reason: 'x' });
  assert.deepEqual([r.status, r.body.error?.code], [403, 'approval_requests_disabled']);
  await E.flag(ownerA.shopId, 'shop.approval_requests', true);
  assert.equal(E.ok(await A.cash.api('GET', '/shop/approval-requests/config'), 'config').enabled, true);
  assert.ok(hot.ttl() === 2000);
}, { needs: ['FLAGS.0'] });

if (PG) {
  await E.step('FLAGS.REPLICA', 'نسخهٔ خواندنی (PostgreSQL): زنده، بی‌تأخیر؛ خواندن‌های ویترین از آن یا با دلیلِ روشن از اصلی', async () => {
    await replica.probe();
    const st = replica.stats();
    assert.deepEqual([st.configured, st.usable, st.lagMs], [true, true, 0], JSON.stringify(st));
    E.ok(await E.get(`/api/store/${A.slug || SLUG_A}/products`), 'فهرستِ ویترین');
    const after = replica.stats();
    console.log(`نسخهٔ خواندنی: ${after.reads} خواندن، ${after.fallbacks} برگشت، اصلی به این دلیل‌ها: ${JSON.stringify(after.primary)}`);
    assert.ok(after.reads + Object.values(after.primary).reduce((n, x) => n + x, 0) > 0, 'خواندنِ داوطلب از درِ نسخه گذشت');
  }, { needs: ['FLAGS.0'] });
}

try { fs.rmSync(IMG, { recursive: true, force: true }); } catch { /* پوشهٔ موقتِ خودِ همین سناریو */ }
await E.finish();
