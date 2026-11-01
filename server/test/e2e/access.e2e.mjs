/**
 * سناریوهای سرتاسریِ جداسازی، نشست و مرزِ API — S59، S112، S75، S61، S88، S111، S130
 * (§۱۶، §۱۹.۱۲، §۲۱.۱۹).
 *
 * بازیگرها:
 *   • A و A2 — دو گوشیِ صاحبِ دکانِ «الف»؛ W — گوشیِ شاگردِ «الف» (برنامه در سطحِ پروتکل)؛
 *   • B — گوشیِ صاحبِ دکانِ «ب» (همان برنامه، توکنِ واقعیِ خودش)؛
 *   • مشتری‌های دو ویترین (API ِ همان صفحه) و کرومیوم برای ویترین و پنلِ مدیر.
 * دادهٔ «الف» همه از راهِ واقعی ساخته می‌شود: کالا و قرض‌دار و فروشِ نسیه با QR از همگام‌سازی،
 * سفارش از ویترین، پیک و مأموریت از میزِ تحویل.
 *
 * ⛔ زمان و هماهنگی: هیچ خوابی. «سپری شدنِ زمان» برای توکن‌ها با `expires_at` ِ همان ردیفِ توکن
 * (ساعتِ دیوار دست نمی‌خورد)؛ کدِ TOTP با گامِ صریح (`totp.hotp` روی گامِ بعدی)؛ نبودِ پیام روی
 * سوکت با «کنترلِ مثبت» روی همان سوکت (پیامِ بعدیِ همان صف) سنجیده می‌شود، نه با صبر.
 *
 * پرچم‌ها فقط برای دکان‌های همین سناریو (live_config).
 * اجرا:  node test/e2e/run.mjs access
 */
import { createRequire } from 'node:module';
import crypto from 'node:crypto';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'access' });
//  ⚠️ پس از boot: کدِ سرور پیکربندی را همان لحظهٔ نخستین require می‌خواند
const require = createRequire(import.meta.url);
const { routes } = require('../route-inventory');
const R = require('../../src/lib/receipt-token');
const totp = require('../../src/lib/totp');
const WebSocket = require('ws');
const { db } = E;

const FLAGS = ['shop.orders', 'shop.storefront', 'shop.delivery', 'shop.catalog', 'shop.orders.refunds'];
const ownerA = await E.owner('owner-a-acc@e2e.local', 'صاحبِ الف', 'دکانِ الف');
const staffA = await E.staff(ownerA, 'staff-a-acc@e2e.local', 'شاگردِ الف');
const courierA = await E.staff(ownerA, 'courier-a-acc@e2e.local', 'پیکِ الف');
const ownerB = await E.owner('owner-b-acc@e2e.local', 'صاحبِ ب', 'دکانِ ب');
for (const s of [ownerA.shopId, ownerB.shopId]) for (const k of FLAGS) await E.flag(s, k, true);
const SLUG_A = 'e2e-acc-alef';
const SLUG_B = 'e2e-acc-be';
const SECRET_NAME = 'زعفرانِ-رازِ-الف-۷۳۱';
const DEBTOR_A = 'قرض‌دارِ-رازِ-الف';
const PHONE_A = '0791250001';
const PHONE_B = '0791250002';

let A; let A2; let W; let B; let saffron; let debtor;
const a = {};          //  شناسه‌های واقعیِ «الف»
const secretsOf = () => [ownerA.shopId, a.order, a.orderToken, a.product, a.assignment, a.courier, a.sale, a.qr, SECRET_NAME, DEBTOR_A, PHONE_A.slice(1)].filter(Boolean);

async function ticketOf(slug, phone) {
  const s = E.ok(await E.post(`/api/store/${slug}/verify/start`, { phone }), 'کدِ تأیید');
  return E.ok(await E.post(`/api/store/${slug}/verify/check`, { phone, code: s.devCode }), 'تأییدِ شماره').ticket;
}
/** همهٔ ردیف‌های «الف» در هر جدولِ دارای shop_id و دفترِ همگام‌سازی — اثرِ انگشت */
async function footprint(shopId) {
  const tables = await db.many("SELECT DISTINCT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='shop_id' ORDER BY table_name");
  const out = {};
  for (const { table_name: t } of tables) {
    const r = await db.one(`SELECT COUNT(*)::int AS n, COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), '') AS h FROM "${t}" x WHERE x.shop_id = $1`, [shopId]);
    out[t] = `${r.n}:${r.h}`;
  }
  const s = await db.one("SELECT COUNT(*)::int AS n, COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), '') AS h FROM sync_rows x WHERE x.account_kind='shop' AND x.account_id=$1", [shopId]);
  out.sync_rows = `${s.n}:${s.h}`;
  return out;
}
/** فقط جدول‌هایی که عوض شده‌اند — برای پیامِ خطای خوانا */
const changed = (x, y) => Object.keys({ ...x, ...y }).filter((k) => x[k] !== y[k]).map((k) => `${k}: ${x[k]} ⇒ ${y[k]}`);
/** سوکتِ زندهٔ برنامه با بلیتِ یک‌بارمصرف؛ هر پیام در `msgs` و منتظرماندن بر رویداد */
async function live(dev) {
  const t = E.ok(await dev.api('POST', '/sync/v1/live/ticket', {}), 'بلیتِ سوکت').ticket;
  const ws = new WebSocket(`${E.apiBase.replace('http', 'ws')}/api/sync/v1/live?ticket=${t}&device_id=${dev.deviceId}&app=shop`);
  const msgs = [];
  const waiters = [];
  ws.on('message', (m) => { msgs.push(JSON.parse(String(m))); for (const w of [...waiters]) w(); });
  await new Promise((ok, no) => { ws.on('open', ok); ws.on('error', no); });
  const until = (pred, what) => new Promise((ok, no) => {
    const t0 = setTimeout(() => no(new Error(`${dev.label}: ${what} نرسید — ${JSON.stringify(msgs)}`)), 10_000);
    const check = () => { const m = msgs.find(pred); if (m) { clearTimeout(t0); waiters.splice(waiters.indexOf(check), 1); ok(m); } };
    waiters.push(check);
    check();
  });
  await until((m) => m.event === 'hello', 'hello');
  return { ws, msgs, until, send: (o) => ws.send(JSON.stringify(o)) };
}

await E.step('S.acc.0', 'دو دکانِ واقعی: «الف» با کالا، قرض‌دار، فروشِ نسیه با QR، سفارشِ ویترین، پیک و مأموریت؛ «ب» با ویترینِ خودش', async () => {
  A = await E.device('A-owner', ownerA.email);
  W = await E.device('W-staff', staffA.email);
  B = await E.device('B-owner', ownerB.email);
  for (const d of [A, W, B]) assert.equal((await d.firstSync()).ok, true);
  saffron = A.stockIn({ name: SECRET_NAME, price: 900, cost: 700, units: 10 });
  debtor = A.addDebtor(DEBTOR_A, '0700001234');
  const sold = A.sell({ lines: [{ id: saffron, qty: 1 }], payment: 'credit', debtorId: debtor, paid: 400 });
  assert.equal(sold.ok, true, sold.message);
  a.qr = R.encodeToken(crypto.randomBytes(16));
  const d = structuredClone(A.ledger);
  d.sales.find((s) => s.id === sold.saleId).receiptToken = a.qr;
  A.save(d);
  await A.drain();
  a.sale = sold.saleId;
  a.product = saffron;
  B.stockIn({ name: 'برنجِ ب', price: 100, cost: 80, units: 5 });
  await B.drain();
  E.ok(await A.api('PUT', '/shop/storefront', { slug: SLUG_A, enabled: true, title: 'ویترینِ الف', contactPhone: '0700000031', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترینِ الف');
  E.ok(await A.api('PUT', `/shop/storefront/products/${saffron}`, { published: true }), 'انتشار');
  E.ok(await B.api('PUT', '/shop/storefront', { slug: SLUG_B, enabled: true, title: 'ویترینِ ب', contactPhone: '0700000032', deliveryFeeMinor: 0, minOrderMinor: 0 }), 'ویترینِ ب');
  a.ticket = await ticketOf(SLUG_A, PHONE_A);
  const o = E.ok(await E.post(`/api/store/${SLUG_A}/checkout`, { fulfillment: 'delivery', paymentMethod: 'cod', customer: { name: 'مشتریِ الف', phone: PHONE_A }, lines: [{ productId: saffron, qtyMilli: 1000 }], address: { text: 'نشانیِ الف', landmark: 'نشانهٔ الف' } },
    null, { 'X-Store-Ticket': a.ticket, 'Idempotency-Key': E.key('web') }), 'سفارشِ الف', 201).order;
  a.orderToken = o.token;
  a.order = (await db.one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [ownerA.shopId, o.orderNo])).id;
  for (const [to, from, x] of [['accepted', 'new', { readyInMinutes: 10 }], ['picking', 'accepted'], ['packed', 'picking']]) E.ok(await A.api('POST', `/shop/orders/${a.order}/transition`, { to, from, ...(x || {}) }), to);
  a.courier = E.ok(await A.api('POST', '/shop/delivery/couriers', { userId: courierA.user.id, cashCapMinor: 5_000_000 }), 'پیک', 201).courier.id;
  a.assignment = E.ok(await A.api('POST', `/shop/delivery/orders/${a.order}/assign`, { mode: 'shop_courier', courierId: a.courier, fareMinor: 3000 }, { headers: { 'Idempotency-Key': E.key('asg') } }), 'سپردن', 201).assignment.id;
  a.member = E.ok(await A.api('GET', '/shop/members'), 'اعضا').members.find((m) => m.userId === staffA.user.id).id;
});

await E.step('S112', 'دکانِ «ب» با شناسه‌های واقعیِ «الف» در نشانی و بدنه به رکورد، فاکتور، فایل یا گزارشِ «الف» نمی‌رسد و چیزی را عوض نمی‌کند؛ شاگردِ «الف» از حدِّ نقشش بیرون نمی‌رود', async () => {
  //  مشتریِ دیگری با شمارهٔ خودش در ویترینِ «الف» (کدِ تأییدش ردیفِ پیامکِ «الف» است) — پیش از اثرِ انگشت
  const tB = await ticketOf(SLUG_A, PHONE_B);
  const before = await footprint(ownerA.shopId);
  const probes = [
    ['GET', `/shop/orders/${a.order}`], ['POST', `/shop/orders/${a.order}/transition`, { to: 'cancelled', from: 'packed', reason: 'other', note: 'x' }],
    ['POST', `/shop/orders/${a.order}/refunds`, { amountMinor: 100, reason: 'x' }], ['GET', `/shop/delivery/orders/${a.order}`],
    ['POST', `/shop/delivery/assignments/${a.assignment}/status`, { to: 'withdrawn', reason: 'reassign' }], ['GET', `/shop/delivery/couriers/${a.courier}/settlement-preview`],
    ['GET', `/shop/catalog/products/${a.product}`], ['GET', `/shop/catalog/report?productId=${a.product}`],
    ['POST', `/shop/members/${a.member}/remove`, {}], ['PUT', `/shop/storefront/products/${a.product}`, { published: false }],
    ['GET', `/sync/v1/photos/${a.product}`],
  ];
  const leaks = [];
  for (const [m, p, body] of probes) {
    const r = await B.api(m, p, body, { headers: { 'Idempotency-Key': E.key('x'), 'X-Shop-Id': ownerA.shopId } });
    if (r.status < 400 && m !== 'GET') leaks.push(`${m} ${p} ⇒ ${r.status}`);
    if (r.status >= 500) leaks.push(`${m} ${p} ⇒ ${r.status}`);
    const text = JSON.stringify(r.body || '');
    for (const s of secretsOf()) if (text.includes(s)) leaks.push(`${m} ${p} دادهٔ «الف» (${s.slice(0, 10)}…)`);
  }
  //  دفترِ همگام‌سازیِ «ب»: هیچ ردیفی از «الف»، حتی با شناسه‌اش در push
  const snap = E.ok(await B.api('GET', '/sync/v1/snapshot'), 'Snapshot ِ ب');
  for (const s of secretsOf()) if (JSON.stringify(snap).includes(s)) leaks.push(`snapshot ِ ب دادهٔ «الف» (${s.slice(0, 10)}…)`);
  //  فاکتورِ QR ِ «الف» با نشانِ «ب» و سفارشِ «الف» روی ویترینِ «ب» ⇒ ۴۰۴ ِ یکسان
  assert.equal((await E.get(`/api/receipt/${R.shopTag(ownerB.shopId)}/${a.qr}`)).status, 404);
  assert.equal((await E.get(`/api/receipt/${R.shopTag(ownerA.shopId)}/${a.qr}`)).status, 200, 'کنترلِ مثبت: QR ِ «الف» با نشانِ خودش');
  assert.equal((await E.get(`/api/store/${SLUG_B}/orders/${encodeURIComponent(a.orderToken)}`)).status, 404);
  const mine = E.ok(await E.get(`/api/store/${SLUG_A}/orders`, null, { 'X-Store-Ticket': tB }), 'سفارش‌های مشتریِ دیگر').orders;
  assert.deepEqual(mine, [], 'مشتریِ دیگر سفارشِ «الف» را دید');
  //  شاگردِ «الف»: بازپرداخت، تسویه و حذفِ عضو نه (۴۰۳)؛ شمارهٔ مشتری پوشیده
  for (const [m, p, body] of [['POST', `/shop/orders/${a.order}/refunds`, { amountMinor: 100, reason: 'x' }], ['POST', `/shop/delivery/couriers/${a.courier}/settlements`, { assignmentIds: [a.assignment], handedOverMinor: 0, receivedMinor: 0 }], ['POST', `/shop/members/${a.member}/remove`, {}]]) {
    const r = await W.api(m, p, body, { headers: { 'Idempotency-Key': E.key('w') } });
    assert.equal(r.status, 403, `${m} ${p} برای شاگرد: ${r.status}`);
  }
  assert.equal(JSON.stringify(E.ok(await W.api('GET', `/shop/orders/${a.order}`), 'سفارش برای شاگرد')).includes(PHONE_A.slice(1)), false);
  assert.deepEqual(leaks, [], leaks.join('\n'));
  assert.deepEqual(changed(before, await footprint(ownerA.shopId)), [], 'دکانِ «ب» یا شاگرد ردیفی از «الف» را عوض کرد');
  //  کنترلِ مثبت: خودِ «الف» به همه می‌رسد
  assert.equal(E.ok(await A.api('GET', `/shop/orders/${a.order}`), 'سفارش برای صاحب').order.id, a.order);
}, { needs: ['S.acc.0'] });

await E.step('S59', 'جداییِ دکان‌ها و بخشِ پمپ: توکنِ دکان به پمپ و پنلِ مدیر راه ندارد و توکنِ پمپِ همان آدم به دکان نه', async () => {
  for (const p of ['/pump/station', '/pump/state', '/v1/pump/device/state']) {
    const r = await B.api('GET', p);
    assert.ok([401, 403, 404].includes(r.status), `${p} با توکنِ دکان: ${r.status}`);
    assert.equal(JSON.stringify(r.body || '').includes(ownerA.shopId), false);
  }
  for (const p of ['/admin/stats', '/admin/shops', `/admin/shops/${ownerA.shopId}`, '/admin/audit']) {
    assert.equal((await B.api('GET', p)).status, 401, `${p} با توکنِ دکان`);
  }
  //  همان آدم با حسابِ پمپ: توکنِ پمپ به هیچ درِ دکان نمی‌رسد
  const rq = E.ok(await E.post('/api/auth/pump/request-code', { email: ownerA.email, device_name: 'pump' }), 'کدِ پمپ');
  const at = Date.now() - 60_000;
  const code = await E.mailCode(ownerA.email, at);
  const pv = await E.post('/api/auth/pump/verify', { request_id: rq.request_id, code, device_id: 'e2e-pump-acc', device_name: 'pump' }, null, { 'X-App': 'pump' });
  if (pv.status === 200) {
    const pt = pv.body.access_token || pv.body.accessToken;
    for (const p of ['/api/shop/members', `/api/shop/orders/${a.order}`, '/api/sync/v1/snapshot']) {
      const r = await E.get(p, pt, { 'X-App': 'shop' });
      assert.ok([401, 403].includes(r.status), `${p} با توکنِ پمپ: ${r.status}`);
    }
  } else {
    assert.ok([400, 403, 404].includes(pv.status), `ورودِ پمپ: ${pv.status} ${JSON.stringify(pv.body)}`);
  }
}, { needs: ['S.acc.0'] });

await E.step('S75', 'ماتریسِ کامل: هر مسیرِ /api/shop و /api/courier (از خودِ سورس) با توکنِ «ب» و شناسه‌های واقعیِ «الف»؛ سوکتِ زنده، Snapshot و QR؛ هیچ ۵۰۰، هیچ نشت، هیچ تغییر', async () => {
  const list = [...routes('/shop'), ...routes('/courier')];
  assert.ok(list.length > 100, `فقط ${list.length} مسیر`);
  const before = await footprint(ownerA.shopId);
  const fails = [];
  let calls = 0;
  const idSets = [a.order, a.product, a.assignment, a.courier, a.member, a.sale];
  for (const r of list) {
    for (const id of idSets) {
      const url = `/api${r.path.replace(/:[A-Za-z_]+/g, id)}`;
      const body = r.method === 'GET' || r.method === 'DELETE' ? undefined : { shopId: ownerA.shopId, id, orderId: a.order, productId: a.product, assignmentIds: [a.assignment] };
      const res = await B.api(r.method, url, body, { headers: { 'Idempotency-Key': E.key('m'), 'X-Shop-Id': ownerA.shopId } });
      calls++;
      if (res.status >= 500) fails.push(`${r.method} ${url} ⇒ ${res.status}`);
      const text = JSON.stringify(res.body || '');
      for (const s of secretsOf()) if (text.includes(s)) fails.push(`${r.method} ${url} دادهٔ «الف» (${s.slice(0, 10)}…)`);
      if (!r.path.includes(':')) break;   //  مسیرِ بی شناسه یک بار
    }
  }
  console.log(`ماتریس: ${list.length} مسیر، ${calls} درخواست با توکنِ «ب»`);
  assert.ok(calls > list.length * 2, `فقط ${calls} درخواست`);
  //  «ب» هنوز واقعاً واردِ دکانِ خودش است ⇒ ماتریس بی‌اثر نبود
  assert.equal((await B.api('GET', '/shop/members')).status, 200, 'توکنِ «ب» وسطِ ماتریس باطل شد');
  assert.deepEqual(fails, [], fails.join('\n'));
  assert.deepEqual(changed(before, await footprint(ownerA.shopId)), [], 'ماتریس ردیفی از «الف» را عوض کرد');
  //  سوکتِ زنده: تغییرِ «الف» فقط به دستگاهِ دیگرِ «الف» می‌رسد؛ رخدادِ «ب» به «الف» نه
  A2 = await E.device('A2-owner', ownerA.email);
  const sa = await live(A2);
  const sb = await live(B);
  A.stockIn({ name: 'کالای تازهٔ الف', price: 10, cost: 5, units: 1 });
  await A.drain();
  await sa.until((m) => m.event === 'changed', '«changed» برای دستگاهِ دیگرِ الف');
  sb.send({ type: 'relay', event: 'scan', code: 'B-INJECT-1', pair: '' });
  sb.send({ type: 'ping' });
  await sb.until((m) => m.event === 'pong', 'pong ِ ب');
  const sa2 = await live(A);
  sa2.send({ type: 'relay', event: 'scan', code: 'A-OWN-1', pair: '' });
  await sa.until((m) => m.event === 'scan' && m.code === 'A-OWN-1', 'اسکنِ خودِ الف');
  assert.equal(sa.msgs.some((m) => m.code === 'B-INJECT-1'), false, 'اسکنِ «ب» به «الف» رسید');
  assert.equal(sb.msgs.some((m) => m.event === 'changed' || m.code === 'A-OWN-1'), false, `«ب» از «الف» خبر گرفت: ${JSON.stringify(sb.msgs)}`);
  for (const s of [sa, sb, sa2]) s.ws.close();
  //  سوکت با توکنِ ساختگی یا بی بلیت ⇒ ۴۰۱
  const bad = new WebSocket(`${E.apiBase.replace('http', 'ws')}/api/sync/v1/live?ticket=forged-${crypto.randomUUID()}`);
  const code = await new Promise((ok) => { bad.on('unexpected-response', (_, res) => ok(res.statusCode)); bad.on('open', () => ok(101)); bad.on('error', () => ok(0)); });
  assert.equal(code, 401);
}, { needs: ['S112'] });

await E.step('S61', 'CSP روی ویترین، بازارچه، پنلِ مدیر و QR؛ اسکریپتِ تزریقی و منبعِ بیرونی بسته؛ هیچ توکنِ ورود، بلیت یا نشست در نشانی و لاگِ سرور', async () => {
  for (const p of [`/store/${SLUG_A}`, '/admin/', `/r/${R.shopTag(ownerA.shopId)}/${a.qr}`]) {
    const r = await fetch(E.apiBase + p);
    const csp = r.headers.get('content-security-policy') || '';
    assert.match(csp, /script-src 'self'/, `${p}: ${csp}`);
    assert.equal(/script-src[^;]*'unsafe-inline'/.test(csp), false, `${p}: inline مجاز`);
    assert.match(csp, /frame-ancestors 'none'/, p);
  }
  //  لاگِ سرور در طولِ یک جریانِ کامل
  const logged = [];
  const out = process.stdout.write.bind(process.stdout);
  process.stdout.write = (chunk, ...rest) => { logged.push(String(chunk)); return out(chunk, ...rest); };
  const urls = [];
  let pg;
  try {
    pg = await E.page('csp');
    pg.page.on('request', (q) => urls.push(q.url()));
    pg.page.on('response', (q) => { if (q.url().includes('evil.example.com')) urls.push(`RESPONSE ${q.url()}`); });
    await pg.page.route('https://evil.example.com/**', (route) => { urls.push(`BLOCKED-ATTEMPT ${route.request().url()}`); return route.abort(); });
    await pg.page.goto(`${E.apiBase}/store/${SLUG_A}#/o/${encodeURIComponent(a.orderToken)}`);
    await pg.page.waitForSelector('#o-lines li', { timeout: 15_000 });
    await pg.page.evaluate(() => {
      window.__csp = [];
      document.addEventListener('securitypolicyviolation', (e) => window.__csp.push(`${e.violatedDirective} ${e.blockedURI}`));
      const s = document.createElement('script'); s.textContent = 'window.__pwned = 1'; document.head.appendChild(s);
      const x = document.createElement('script'); x.src = 'https://evil.example.com/x.js'; document.head.appendChild(x);
      const i = document.createElement('img'); i.src = 'https://evil.example.com/p.gif'; document.body.appendChild(i);
    });
    await pg.page.waitForFunction(() => window.__csp.length >= 3, null, { timeout: 10_000 });
    assert.equal(await pg.page.evaluate(() => window.__pwned), undefined, 'اسکریپتِ درون‌خطی اجرا شد');
    //  کرومیوم درخواستِ بسته‌شده با CSP را «request» گزارش می‌کند ولی به شبکه نمی‌رسد: نه route و نه پاسخ
    assert.equal(urls.some((u) => u.startsWith('BLOCKED-ATTEMPT') || u.startsWith('RESPONSE')), false, `درخواست به منبعِ بیرونی رسید: ${urls.filter((u) => u.includes('evil'))}`);
    //  جریانِ برنامه: ورود، تازه‌سازی، Snapshot، بلیتِ سوکت
    const D = await E.device('D-log', ownerA.email);
    assert.equal((await D.firstSync()).ok, true);
    await D.refresh();
    const s = await live(D);
    s.ws.close();
    const secrets = [D.token, D.refreshToken, A.token, A.refreshToken, a.ticket].filter(Boolean);
    const text = logged.filter((l) => !l.startsWith('[otp]')).join('');
    for (const sct of secrets) assert.equal(text.includes(sct), false, `رازی در لاگِ سرور (${sct.slice(0, 6)}…)`);
    assert.equal(text.includes(a.orderToken), false, 'توکنِ پیگیریِ سفارش در لاگِ سرور');
    assert.ok(/\/api\/store\/[^ ]+\/orders\/:tok/.test(text), `نشانیِ پیگیری در لاگ پوشیده نشد: ${text.split('\n').filter((l) => l.includes('/orders/')).slice(0, 3)}`);
    for (const u of urls) for (const sct of secrets) assert.equal(u.includes(sct), false, `راز در نشانیِ مرورگر: ${u.slice(0, 80)}`);
  } finally {
    process.stdout.write = out;
  }
}, { needs: ['S.acc.0'] });

await E.step('S88', 'مرزِ API: بدنهٔ بزرگ و JSON ِ خراب، صفحه‌بندی و برگهٔ نامعتبر، پارامترِ ناشناخته، عکسِ بزرگ و سقفِ درخواست ⇒ پاسخِ کنترل‌شده بی هیچ جزئیاتِ درونی', async () => {
  const clean = (r, what) => {
    const t = JSON.stringify(r.body || '');
    //  درهای API ِ عمومی `{error:{code,message}}`؛ درِ همگام‌سازیِ v1 قراردادِ خودش `{ok:false, error:"CODE", message}`
    const code = r.body && r.body.error && (typeof r.body.error === 'string' ? r.body.error : r.body.error.code);
    assert.ok(code && /^[A-Za-z_]+$/.test(code), `${what}: بی کدِ خطا ${t.slice(0, 120)}`);
    assert.equal(/at \w+ \(|node_modules|\/src\/|stack|SELECT |pglite|syntax error at/i.test(t), false, `${what}: جزئیاتِ درونی ${t.slice(0, 200)}`);
  };
  const big = await fetch(`${E.apiBase}/api/shop/settings`, { method: 'PUT', headers: { ...A.headers(), 'Content-Type': 'application/json' }, body: JSON.stringify({ settings: { x: 'a'.repeat(3 * 1024 * 1024) } }) });
  assert.equal(big.status, 413);
  clean({ body: await big.json() }, 'بدنهٔ ۳ مگابایتی');
  const broken = await fetch(`${E.apiBase}/api/shop/settings`, { method: 'PUT', headers: { ...A.headers(), 'Content-Type': 'application/json' }, body: '{"settings": {' });
  assert.equal(broken.status, 400);
  clean({ body: await broken.json() }, 'JSON ِ خراب');
  for (const [p, want] of [['/shop/orders?tab=DROP%20TABLE', 400], ['/shop/orders?cursor=%27%3B--', 400], ['/shop/delivery/assignments?scope=done&before=abc', 400], ['/shop/delivery/assignments?scope=evil', 400]]) {
    const r = await A.api('GET', p);
    assert.equal(r.status, want, `${p}: ${r.status}`);
    clean(r, p);
  }
  const huge = E.ok(await A.api('GET', '/shop/orders?tab=done&limit=1000000'), 'سقفِ صفحه');
  assert.ok((huge.items || huge.orders || []).length <= 200, 'سقفِ صفحه رعایت نشد');
  assert.equal((await A.api('GET', '/shop/orders?tab=done&limit=-5&sort=amount;DROP')).status, 200, 'پارامترِ ناشناخته');
  const photo = await A.api('PUT', `/sync/v1/photos/${a.product}`, { data: Buffer.alloc(700 * 1024, 1).toString('base64'), device_id: A.deviceId });
  assert.ok([400, 413].includes(photo.status), `عکسِ ۷۰۰ کیلوبایتی: ${photo.status}`);
  clean(photo, 'عکسِ بزرگ');
  //  سقفِ درخواستِ کد (ویترین): پس از سقف ۴۲۹ با Retry-After و پیامِ کوتاه
  let r429 = null;
  for (let i = 0; i < 40 && !r429; i++) {
    const r = await fetch(`${E.apiBase}/api/store/${SLUG_B}/verify/start`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phone: `07912600${String(i).padStart(2, '0')}` }) });
    if (r.status === 429) r429 = { status: 429, retry: r.headers.get('retry-after'), body: await r.json() };
  }
  assert.ok(r429, 'سقفِ درخواستِ کد هرگز نرسید');
  clean(r429, '۴۲۹');
  assert.ok(Number(r429.retry) > 0 || Number(r429.body.error.retryAfter) > 0, `بی زمانِ تلاشِ دوباره: ${JSON.stringify(r429)}`);
}, { needs: ['S.acc.0'] });

await E.step('S111', 'نشستِ پایدار: بستن و بازکردنِ برنامه، قطع و وصل، توکنِ دسترسیِ منقضی (تازه‌سازیِ خودکار)، تازه‌سازِ منقضی (ورودِ دوباره)، خروجِ دستی و بیرون‌کردن از دستگاهِ دیگر', async () => {
  const P = await E.device('P-phone', ownerB.email);
  //  بستن و بازکردن: همان توکن‌های ذخیره‌شده روی گوشی
  const reopen = Object.assign(Object.create(Object.getPrototypeOf(P)), P, { requests: [] });
  assert.equal((await reopen.api('GET', '/shop/members')).status, 200);
  //  قطع و وصل: صف می‌ماند، پس از وصل بی ورودِ دوباره
  reopen.offline = true;
  await assert.rejects(reopen.api('GET', '/shop/members'), /هواپیما/);
  reopen.offline = false;
  assert.equal((await reopen.api('GET', '/shop/members')).status, 200);
  //  توکنِ دسترسی منقضی (ساعتِ توکن، نه ساعتِ دیوار) ⇒ یک ۴۰۱ و تازه‌سازیِ چرخشیِ خودکار
  const expire = (kind, dev) => db.query("UPDATE tokens SET expires_at=$1 WHERE subject_id=$2 AND kind=$3 AND revoked_at IS NULL AND device_id=(SELECT id FROM devices WHERE device_uid=$4 AND user_id=$2 LIMIT 1)", [Date.now() - 1000, ownerB.user.id, kind, dev.deviceId]);
  await expire('access', reopen);
  const oldRefresh = reopen.refreshToken;
  assert.equal((await reopen.api('GET', '/shop/members')).status, 200);
  assert.equal(reopen.refreshes, 1);
  assert.notEqual(reopen.refreshToken, oldRefresh, 'تازه‌ساز نچرخید');
  //  تازه‌سازِ منقضی ⇒ دیگر راهی جز ورود نیست
  await expire('access', reopen);
  await expire('refresh', reopen);
  assert.equal((await reopen.api('GET', '/shop/members')).status, 401);
  assert.equal(reopen.lastRefresh.status, 401);
  await reopen.login(ownerB.email);
  assert.equal((await reopen.api('GET', '/shop/members')).status, 200, 'ورودِ دوباره');
  //  خروجِ دستی: هر دو توکن باطل
  const L = await E.device('L-phone', ownerB.email);
  const [acc, ref] = [L.token, L.refreshToken];
  assert.equal((await L.logout()).status, 200);
  assert.equal((await E.get('/api/shop/members', acc, { 'X-App': 'shop' })).status, 401);
  assert.equal((await E.post('/api/auth/refresh', { refreshToken: ref }, null, { 'X-App': 'shop' })).status, 401);
  //  بیرون کردن از دستگاهِ دیگر: X نشستِ Y را می‌بندد ⇒ Y دیگر نه دسترسی دارد نه تازه‌سازی
  const Y = await E.device('Y-phone', ownerB.email);
  const X = await E.device('X-phone', ownerB.email);
  const dev = E.ok(await X.api('GET', '/me/devices'), 'دستگاه‌ها').devices.find((d) => d.device_uid === Y.deviceId);
  E.ok(await X.api('DELETE', `/me/devices/${dev.id}`), 'بیرون کردنِ Y');
  assert.equal((await Y.api('GET', '/shop/members')).status, 401);
  assert.equal(Y.lastRefresh.status, 401, 'تازه‌سازِ دستگاهِ بیرون‌شده هنوز کار می‌کند');
  assert.equal((await X.api('GET', '/shop/members')).status, 200, 'دستگاهِ X بیرون افتاد');
}, { needs: ['S.acc.0'] });

await E.step('S130', 'حسابِ مدیر: ورودِ دومرحله‌ای در پنل (کدِ غلط رد، کدِ درست یک بار، کدِ بازیابی یک بار)، مدیرِ عادی به کارهای ارشد نمی‌رسد، هر کارِ مدیر با کننده و دلیل در سابقه', async () => {
  const root = await E.adminToken();
  const nextCode = async (secret) => {
    const row = await db.one("SELECT totp_last_step FROM admins WHERE username='admin'");
    return totp.hotp(totp.base32Decode(secret), Math.max(totp.stepAt(), Number(row.totp_last_step) + 1));
  };
  //  روشن کردن: رمز لازم؛ کدِ درست ⇒ ده کدِ بازیابی
  assert.equal((await E.post('/api/admin/2fa/setup', { password: 'wrong' }, root)).status, 403);
  const { secret } = E.ok(await E.post('/api/admin/2fa/setup', { password: 'Admin!12345' }, root), 'راه‌اندازی');
  const on = E.ok(await E.post('/api/admin/2fa/enable', { code: totp.generate(secret) }, root), 'روشن کردن');
  assert.equal(on.recovery_codes.length, 10);
  //  پنل در کرومیوم: رمزِ درست تنها کافی نیست
  const pg = await E.page('admin', { width: 1280, height: 800 });
  const { page } = pg;
  await page.goto(`${E.apiBase}/admin/`);
  await page.fill('#in-username', 'admin');
  await page.fill('#in-password', 'Admin!12345');
  await page.click('#btn-login');
  await page.waitForSelector('#totp-row:not(.hidden)', { timeout: 10_000 });
  assert.equal(await page.isVisible('#app-view'), false, 'بی کد وارد شد');
  const good = await nextCode(secret);
  await page.fill('#in-totp', good === '000000' ? '111111' : '000000');
  await page.click('#btn-login');
  await page.waitForFunction(() => /کدِ تأیید درست نیست/.test(document.getElementById('login-msg')?.textContent || ''), null, { timeout: 10_000 });
  await page.fill('#in-totp', good);
  await page.click('#btn-login');
  await page.waitForSelector('#app-view:not(.hidden)', { timeout: 10_000 });
  await E.shot(pg, 'S130-admin-2fa');
  const replay = await E.post('/api/admin/login', { username: 'admin', password: 'Admin!12345', totp: good });
  assert.equal(replay.status, 401, 'همان کد دو بار');
  const rec = E.ok(await E.post('/api/admin/login', { username: 'admin', password: 'Admin!12345', recovery_code: on.recovery_codes[0] }), 'کدِ بازیابی');
  assert.ok(rec.token);
  assert.equal((await E.post('/api/admin/login', { username: 'admin', password: 'Admin!12345', recovery_code: on.recovery_codes[0] })).status, 401, 'کدِ بازیابی دو بار');
  //  مدیرِ عادی: کارهای ارشد (ساختنِ مدیر، حذفِ کاربر، فهرستِ مدیران) ۴۰۳
  E.ok(await E.post('/api/admin/admins', { username: 'ops-acc', name: 'مدیرِ روزانه', password: 'Ops!Strong-12345', role: 'admin' }, rec.token), 'مدیرِ عادی', 201);
  const ops = E.ok(await E.post('/api/admin/login', { username: 'ops-acc', password: 'Ops!Strong-12345' }), 'ورودِ مدیرِ عادی').token;
  assert.equal((await E.post('/api/admin/admins', { username: 'x-acc', password: 'X!Strong-12345', role: 'superadmin' }, ops)).status, 403);
  assert.equal((await E.get('/api/admin/admins', ops)).status, 403);
  assert.equal((await E.call('DELETE', `/api/admin/users/${ownerB.user.id}`, null, ops)).status, 403);
  //  کارِ روزانهٔ مدیرِ عادی (اشتراک با دلیل) ⇐ در سابقه با کننده و دلیل
  E.ok(await E.post('/api/admin/subscriptions', { shopId: ownerB.shopId, plan: 'pro', days: 30, reason: 'رسیدِ حوالهٔ آزمون' }, ops), 'اشتراک', [200, 201]);
  const audit = E.ok(await E.get('/api/admin/audit?limit=50', rec.token), 'سابقه').entries;
  const opsId = (await db.one("SELECT id FROM admins WHERE username='ops-acc'")).id;
  const grant = audit.find((e) => e.user_id === opsId && /subscription/.test(e.action));
  assert.ok(grant, `کارِ مدیرِ عادی در سابقه نیست: ${audit.map((e) => e.action).join('، ')}`);
  assert.match(JSON.stringify(grant.detail), /رسیدِ حوالهٔ آزمون/);
  assert.ok(audit.some((e) => e.action === 'admin.2fa_enabled'), 'روشن کردنِ دومرحله‌ای در سابقه نیست');
  assert.ok(audit.some((e) => e.action === 'admin.admin_created'), 'ساختنِ مدیر در سابقه نیست');
}, { needs: ['S.acc.0'] });

await E.step('S.acc.errors', 'بی خطای جاوااسکریپت در ویترین و پنل', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
