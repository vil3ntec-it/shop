/**
 * سناریوی سرتاسریِ ناموجودی هنگامِ جمع‌آوری — E2E-PCK-06 (S37؛ PCK-03، PCK-05، PCK-06، PCK-07).
 *
 * «کالای ناموجود هنگامِ جمع‌آوری طبقِ سیاستِ جایگزینی/حذف و با مبلغِ نهاییِ درست مدیریت می‌شود.»
 *
 * بازیگرها:
 *   • A — گوشیِ صاحب (برنامهٔ دکان در سطحِ پروتکل): کالا با دسته و بارکد از راهِ همگام‌سازی، سفارش و جمع‌آوری
 *     با همان درهای `PickingRepository.kt`؛
 *   • مشتری — صفحهٔ پیگیریِ سفارش در ویترین (`/store/<slug>#/o/<توکن>`) در کرومیوم، عرضِ گوشی.
 * پرچم‌ها فقط برای همین دکان و از راهِ live_config: shop.orders، shop.storefront، shop.orders.picking.
 *
 * اجرا:  node test/e2e/pick.e2e.mjs
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'pick' });
//  ⚠️ پس از boot: کدِ سرور پیکربندی را همان لحظهٔ نخستین require می‌خواند
const require = createRequire(import.meta.url);
const verifyData = require('../../src/lib/verify-data');
const { db } = E;
const owner = await E.owner('owner-pick@e2e.local', 'صاحبِ جمع‌آوری', 'دکانِ جمع‌آوری');
for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.picking']) await E.flag(owner.shopId, k, true);
const SLUG = 'e2e-pick-store';
const PHONE = '0793334455';
const B_OIL = '6260000000024';
const B_OIL2 = '6260000000031';

let A; let C; let ticket = '';
const P = {};
const lines = (id) => db.many('SELECT product_id, name, qty_milli, unit_price_minor, line_total_minor FROM online_order_lines WHERE order_id=$1 ORDER BY line_no', [id]);
const orderRow = (id) => db.one('SELECT * FROM online_orders WHERE id=$1', [id]);

/** سفارشِ ویترین از راهِ همان بدنهٔ صفحه (شمارهٔ تأییدشده) ⇒ قبول در برنامهٔ دکان ⇒ شروعِ جمع‌آوری */
async function placeAndStart(items) {
  const r = E.ok(await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ جمع‌آوری', phone: PHONE }, lines: items,
  }, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket }), 'ثبتِ سفارشِ ویترین');
  const token = r.order.token;
  const id = token.split('.')[0];
  E.ok(await A.api('POST', `/shop/orders/${id}/transition`, { to: 'accepted', from: 'new' }), 'قبول');
  const st = E.ok(await A.api('POST', `/shop/orders/${id}/pick/start`, {}), 'شروعِ جمع‌آوری');
  return { id, token, sheet: st.pick };
}
const events = async (id, evs) => {
  const r = E.ok(await A.api('POST', `/shop/orders/${id}/pick/events`, { events: evs.map((e) => ({ eventId: E.key('ev'), ...e })) }), 'رویدادِ جمع‌آور');
  for (const x of r.results) assert.equal(x.status, 'accepted', JSON.stringify(x));
  return r.pick;
};
const lineOf = (sheet, productId) => sheet.lines.find((l) => l.productId === productId);

await E.step('E2E-PCK-06.0', 'A کالا با دسته و بارکد می‌سازد و همگام می‌کند؛ ویترین روشن و کالاها منتشر', async () => {
  A = await E.device('A-owner-phone', owner.email);
  assert.equal((await A.firstSync()).ok, true);
  P.oil = A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 10, unit: 'بوتل', category: 'خوراکی', barcode: B_OIL });
  P.oil2 = A.stockIn({ name: 'روغنِ دوم', price: 320, cost: 260, units: 10, unit: 'بوتل', category: 'خوراکی', barcode: B_OIL2 });
  P.soap = A.stockIn({ name: 'صابون', price: 40, cost: 30, units: 10, unit: 'دانه', category: 'بهداشتی' });
  await A.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG, enabled: true, title: 'ویترینِ جمع‌آوری', contactPhone: '0700000003', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترین');
  for (const id of Object.values(P)) E.ok(await A.api('PUT', `/shop/storefront/products/${id}`, { published: true }), `انتشارِ ${id}`);
  const s = E.ok(await E.post(`/api/store/${SLUG}/verify/start`, { phone: PHONE }), 'کدِ تأیید');
  ticket = E.ok(await E.post(`/api/store/${SLUG}/verify/check`, { phone: PHONE, code: s.devCode }), 'تأییدِ شماره').ticket;
  assert.ok(ticket);
});

let first = null;
let subId = '';

await E.step('E2E-PCK-06.1', 'روغن پیدا نشد ⇒ فقط جایگزینِ هم‌دسته پیشنهاد می‌شود؛ جمع‌آور با اسکن پیشنهاد می‌دهد؛ مشتری قیمتِ کمتر را می‌بیند', async () => {
  first = await placeAndStart([{ productId: P.oil, qtyMilli: 2000 }, { productId: P.soap, qtyMilli: 1000 }]);
  assert.equal(Number((await orderRow(first.id)).total_minor), 2 * 30000 + 4000);
  await events(first.id, [{ lineNo: lineOf(first.sheet, P.oil).lineNo, type: 'not_found' }]);
  const oilLine = lineOf(first.sheet, P.oil).lineNo;
  const sg = E.ok(await A.api('GET', `/shop/orders/${first.id}/pick/substitutes?lineNo=${oilLine}`), 'پیشنهادها');
  assert.deepEqual(sg.suggestions.map((x) => x.productId), [P.oil2], 'صابون (دستهٔ دیگر) پیشنهاد نشد');
  assert.equal(sg.suggestions[0].chargedUnitPriceMinor, 30000, 'گران‌تر ⇒ همان قیمتِ سفارش');
  const unsafe = await A.api('POST', `/shop/orders/${first.id}/pick/substitutions`, { eventId: E.key('sub'), lineNo: oilLine, productId: P.soap });
  assert.equal(unsafe.status, 409);
  assert.equal(unsafe.body.error.reason, 'category_mismatch');
  const wrong = await A.api('POST', `/shop/orders/${first.id}/pick/substitutions`, { eventId: E.key('sub'), lineNo: oilLine, productId: P.oil2, barcode: B_OIL });
  assert.equal(wrong.body.error.code, 'wrong_item', 'اسکنِ کالای دیگر');
  const p = E.ok(await A.api('POST', `/shop/orders/${first.id}/pick/substitutions`, { eventId: E.key('sub'), lineNo: oilLine, productId: P.oil2, barcode: B_OIL2 }), 'پیشنهاد');
  subId = p.substitution.id;
  assert.ok(p.customer.link.endsWith(`#/o/${encodeURIComponent(first.token)}`), p.customer.link);
  assert.match(p.customer.whatsappUrl, /^https:\/\/wa\.me\/93793334455\?text=/, 'صاحب شمارهٔ مشتری را می‌بیند');
}, { needs: ['E2E-PCK-06.0'] });

await E.step('E2E-PCK-06.2', 'مشتری صفحهٔ پیگیری را باز می‌کند و جایگزین را می‌پذیرد', async () => {
  C = await E.page('customer');
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(first.token)}`);
  await page.waitForSelector('#o-subs:not([hidden]) #o-sub-list button.btn-accent', { timeout: 15_000 });
  const text = await page.textContent('#o-sub-list');
  assert.match(text, /روغنِ دوم/);
  assert.match(text, /۶۰۰|600/, 'مبلغِ جایگزین: ۲ × ۳۰۰ (نه ۳۲۰)');
  await E.shot(C, '01-substitute-offer');
  await page.click('#o-sub-list button.btn-accent');
  await page.waitForFunction(() => !document.querySelector('#o-sub-list button'), null, { timeout: 10_000 });
  const row = await db.one('SELECT status, decided_by FROM order_pick_substitutions WHERE id=$1', [subId]);
  assert.deepEqual([row.status, row.decided_by], ['accepted', 'customer']);
  await E.shot(C, '02-substitute-accepted');
}, { needs: ['E2E-PCK-06.1'] });

await E.step('E2E-PCK-06.3', 'صابون پیدا نشد و جایگزینِ امن ندارد ⇒ حذف؛ بسته ⇒ مبلغِ نهایی = جایگزین به قیمتِ کمتر', async () => {
  const soapLine = lineOf(first.sheet, P.soap).lineNo;
  await events(first.id, [{ lineNo: soapLine, type: 'not_found' }]);
  const sg = E.ok(await A.api('GET', `/shop/orders/${first.id}/pick/substitutes?lineNo=${soapLine}`), 'پیشنهادِ صابون');
  assert.deepEqual(sg.suggestions, [], 'هیچ کالای «بهداشتیِ» دیگری نیست');
  const pk = E.ok(await A.api('POST', `/shop/orders/${first.id}/pick/pack`, { bags: 1 }, { headers: { 'Idempotency-Key': E.key('pack') } }), 'بسته');
  assert.equal(pk.pick.totals.totalMinor, 60000);
  assert.equal(pk.pick.totals.deductedMinor, 4000, 'صابونِ حذف‌شده کسر شد');
  assert.deepEqual((await lines(first.id)).map((l) => [l.product_id, Number(l.qty_milli), Number(l.unit_price_minor), Number(l.line_total_minor)]),
    [[P.oil2, 2000, 30000, 60000]]);
  const o = await orderRow(first.id);
  assert.equal(o.status, 'packed');
  assert.equal(Number(o.total_minor), 60000);
  //  مشتری در پیگیری همان را می‌بیند
  const tr = E.ok(await E.get(`/api/store/${SLUG}/orders/${encodeURIComponent(first.token)}`), 'پیگیری');
  assert.equal(tr.order.totalMinor, 60000);
  assert.deepEqual(tr.order.lines.map((l) => l.name), ['روغنِ دوم']);
}, { needs: ['E2E-PCK-06.2'] });

await E.step('E2E-PCK-06.4', 'مشتری تا پایانِ مهلت جواب نمی‌دهد ⇒ ترجیحِ ذخیره‌شده (بی ترجیح ⇒ حذف)؛ صفحه نتیجه را نشان می‌دهد؛ مبلغ درست', async () => {
  //  PCK-14: «پیدا نشد» ِ قبلی تا رسیدگیِ مدیر ویترین را بسته بود — انباردار رسیدگی می‌کند و کالا برمی‌گردد
  const open = E.ok(await A.api('GET', '/shop/orders/pick/issues'), 'وظیفه‌های بررسی').issues;
  assert.deepEqual(open.map((i) => i.productId).sort(), [P.oil, P.soap].sort());
  const held = await E.post(`/api/store/${SLUG}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ جمع‌آوری', phone: PHONE }, lines: [{ productId: P.oil, qtyMilli: 1000 }],
  }, '', { 'Idempotency-Key': E.key('ck'), 'X-Store-Ticket': ticket });
  assert.equal(held.body?.error?.code, 'product_unavailable', 'کالای «پیدا نشد» تا رسیدگی فروخته نمی‌شود');
  for (const i of open) E.ok(await A.api('POST', `/shop/orders/pick/issues/${i.id}/resolve`, { action: 'resolved', note: 'شمرده شد؛ به قفسه آمد' }), 'رسیدگی');
  const second = await placeAndStart([{ productId: P.oil, qtyMilli: 1000 }, { productId: P.soap, qtyMilli: 2000 }]);
  const oilLine = lineOf(second.sheet, P.oil).lineNo;
  const soapLine = lineOf(second.sheet, P.soap).lineNo;
  await events(second.id, [{ lineNo: oilLine, type: 'not_found' }, { lineNo: soapLine, type: 'pick', qtyMilli: 2000 }]);
  const p = E.ok(await A.api('POST', `/shop/orders/${second.id}/pick/substitutions`,
    { eventId: E.key('sub'), lineNo: oilLine, productId: P.oil2, barcode: B_OIL2, waitMinutes: 2 }), 'پیشنهاد');
  //  تا مهلت هست، بسته صبر می‌کند
  const early = await A.api('POST', `/shop/orders/${second.id}/pick/pack`, { bags: 1 }, { headers: { 'Idempotency-Key': E.key('pack') } });
  assert.equal(early.status, 409);
  assert.equal(early.body.error.code, 'substitution_pending');
  //  گذشتِ مهلت (بی خواب): مهلتِ ثبت‌شده به گذشته
  await db.query('UPDATE order_pick_substitutions SET deadline_at = $2 WHERE id = $1', [p.substitution.id, Date.now() - 1000]);
  const { page } = C;
  await page.goto(`${E.apiBase}/store/${SLUG}#/o/${encodeURIComponent(second.token)}`);
  await page.waitForSelector('#o-subs:not([hidden])', { timeout: 15_000 });
  assert.equal(await page.locator('#o-sub-list button').count(), 0, 'پس از مهلت دکمه نیست');
  assert.match(await page.textContent('#o-sub-list'), /حذف/);
  await E.shot(C, '03-substitute-expired');
  const pk = E.ok(await A.api('POST', `/shop/orders/${second.id}/pick/pack`, { bags: 1 }, { headers: { 'Idempotency-Key': E.key('pack') } }), 'بسته');
  assert.equal(pk.pick.totals.totalMinor, 8000, 'فقط دو صابون');
  assert.equal(pk.pick.totals.deductedMinor, 30000);
  const row = await db.one('SELECT status, decided_by FROM order_pick_substitutions WHERE id=$1', [p.substitution.id]);
  assert.deepEqual([row.status, row.decided_by], ['rejected', 'timeout']);
  assert.deepEqual((await lines(second.id)).map((l) => l.product_id), [P.soap]);
}, { needs: ['E2E-PCK-06.0'] });

await E.step('E2E-PCK-06.5', 'دفترِ سفارش‌ها پس از جایگزین و حذف سالم است (verify-data)', async () => {
  const rep = await verifyData.verify({ shopId: owner.shopId });
  assert.deepEqual(rep.checks.filter((c) => c.count > 0).map((c) => c.id), []);
  assert.deepEqual(E.pageErrors, [], 'خطای JS در صفحهٔ مشتری');
}, { needs: ['E2E-PCK-06.3', 'E2E-PCK-06.4'] });

await E.finish();
