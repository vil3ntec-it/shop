/**
 * سناریوی سرتاسریِ S78 — «تغییرِ نقش هنگامِ اتصال» (§۱۹.۱۲):
 *   «دسترسیِ کاربر لغو/نقشِ او تغییر کند؛ کانالِ زنده و endpointهای قبلی فوراً رد شوند.»
 *
 * بازیگرها (برنامه در سطحِ پروتکل، همان سرآیندها و همان سوکتِ `LiveSocket.kt`):
 *   • A — گوشیِ صاحب؛ D — کامپیوترِ صاحب (همان سوکتی که اسکنِ «گوشی به‌عنوانِ بارکدخوان» را
 *     به سبدِ صندوق می‌برد: `PhoneScanner.received`)؛
 *   • M — گوشیِ عضوی که اول «مدیر» است، بعد «شاگرد» و بعد از دکان برداشته می‌شود.
 *
 * ⛔ بی خواب: بسته‌شدنِ سوکت با رویدادِ `close` (حداکثر ۱۰ ثانیه)؛ «نرسیدنِ» پیام با کنترلِ مثبت
 * روی همان سوکتِ گیرنده (پیامِ بعدیِ همان صف) سنجیده می‌شود.
 *
 * پرچم‌ها فقط برای همین دکان (live_config): shop.orders.
 * اجرا:  node test/e2e/run.mjs revoke
 */
import { createRequire } from 'node:module';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'revoke' });
const require = createRequire(import.meta.url);
const WebSocket = require('ws');
const owner = await E.owner('owner-rv@e2e.local', 'صاحبِ دکان', 'دکانِ نقش‌ها');
const memberAcct = await E.staff(owner, 'manager-rv@e2e.local', 'مدیرِ دکان', 'manager');
await E.flag(owner.shopId, 'shop.orders', true);

let A; let D; let M; let memberId; let sD; let sM;
const opened = [];   //  همهٔ سوکت‌ها — در پایان، حتی پس از گامِ قرمز، بسته می‌شوند

/** سوکتِ زندهٔ برنامه با بلیتِ یک‌بارمصرف */
async function live(dev) {
  const t = E.ok(await dev.api('POST', '/sync/v1/live/ticket', {}), `بلیتِ سوکتِ ${dev.label}`).ticket;
  const ws = new WebSocket(`${E.apiBase.replace('http', 'ws')}/api/sync/v1/live?ticket=${t}&device_id=${dev.deviceId}&app=shop`);
  const msgs = [];
  const waiters = new Set();
  let closed = null;
  ws.on('message', (m) => { msgs.push(JSON.parse(String(m))); for (const w of waiters) w(); });
  ws.on('close', (code) => { closed = { code }; for (const w of waiters) w(); });
  await new Promise((ok, no) => { ws.on('open', ok); ws.on('error', no); });
  opened.push(ws);
  const until = (pred, what, ms = 10_000) => new Promise((ok, no) => {
    const check = () => { const v = pred(); if (v) { clearTimeout(t0); waiters.delete(check); ok(v); } };
    const t0 = setTimeout(() => { waiters.delete(check); no(new Error(`${dev.label}: ${what} — ${ms / 1000} ثانیه نرسید؛ پیام‌ها ${JSON.stringify(msgs)}`)); }, ms);
    waiters.add(check);
    check();
  });
  await until(() => msgs.find((m) => m.event === 'hello'), 'hello');
  return { ws, msgs, until, isClosed: () => closed, send: (o) => ws.send(JSON.stringify(o)) };
}

await E.step('S78.0', 'صاحب با گوشی و کامپیوتر، عضوِ «مدیر» با گوشی؛ هر سه سوکتِ زنده دارند و خبرِ هم را می‌گیرند', async () => {
  A = await E.device('A-owner-phone', owner.email);
  D = await E.device('D-owner-desktop', owner.email);
  M = await E.device('M-member-phone', memberAcct.email);
  for (const d of [A, D, M]) assert.equal((await d.firstSync()).ok, true);
  memberId = E.ok(await A.api('GET', '/shop/members'), 'اعضا').members.find((m) => m.userId === memberAcct.user.id).id;
  sD = await live(D);
  sM = await live(M);
  //  کنترلِ مثبت: پیش از هر تغییری، اسکنِ M به کامپیوترِ صاحب می‌رسد و خبرِ تغییرِ A به M
  sM.send({ type: 'relay', event: 'scan', code: 'M-BEFORE', pair: '' });
  await sD.until(() => sD.msgs.find((m) => m.code === 'M-BEFORE'), 'اسکنِ M پیش از لغو');
  A.stockIn({ name: 'برنج', price: 100, cost: 80, units: 5 });
  await A.drain();
  await sM.until(() => sM.msgs.find((m) => m.event === 'changed'), '«changed» برای M پیش از لغو');
});

await E.step('S78.role', 'نقش از «مدیر» به «شاگرد» ⇒ درِ مدیر (تطبیقِ پول، فهرستِ اعضا) همان لحظه ۴۰۳؛ درِ شاگرد باز؛ سوکتِ زنده با 4401 بسته و با نقشِ تازه دوباره وصل', async () => {
  assert.equal((await M.api('GET', '/shop/orders/reconciliation')).status, 200, 'مدیر پیش از تغییر');
  assert.equal((await M.api('GET', '/shop/members')).status, 200);
  E.ok(await A.api('PATCH', `/shop/members/${memberId}`, { role: 'staff' }), 'تغییرِ نقش');
  assert.equal((await M.api('GET', '/shop/orders/reconciliation')).status, 403, 'درِ مدیر پس از تغییرِ نقش');
  assert.equal((await M.api('GET', '/shop/members')).status, 403);
  assert.equal((await M.api('GET', '/shop/orders?tab=new')).status, 200, 'درِ شاگرد');
  //  ⚠️ افزوده با اصلاحِ S78: تغییرِ نقش هم «دسترسی عوض شد» است ⇒ سوکتِ زنده همان لحظه بسته (4401)
  //  و برنامه با بلیتِ تازه برمی‌گردد. بی این، سوکتِ همین‌جا بسته‌شده گامِ بعد را بی‌اثر می‌کرد
  //  (برداشتن روی سوکتی سنجیده می‌شد که دیگر باز نبود).
  await sM.until(() => sM.isClosed(), 'سوکتِ زنده پس از تغییرِ نقش بسته نشد');
  assert.equal(sM.isClosed().code, 4401, 'کدِ بستن پس از تغییرِ نقش');
  sM = await live(M);   //  شاگرد هنوز سوکت دارد — با نقشِ تازه
}, { needs: ['S78.0'] });

await E.step('S78.remove', 'برداشتنِ عضو ⇒ همهٔ درهای HTTP همان لحظه ۴۰۱، بلیتِ تازهٔ سوکت نه، و سوکتِ زندهٔ بازش همان لحظه بسته', async () => {
  E.ok(await A.api('POST', `/shop/members/${memberId}/remove`, {}), 'برداشتنِ عضو');
  for (const p of ['/shop/orders?tab=new', '/sync/v1/snapshot']) assert.equal((await M.api('GET', p)).status, 401, `${p} پس از برداشتن`);
  assert.equal((await M.api('POST', '/sync/v1/live/ticket', {})).status, 401, 'بلیتِ تازه پس از برداشتن');
  await sM.until(() => sM.isClosed(), 'سوکتِ زندهٔ عضوِ برداشته‌شده بسته نشد (باز ماند)');
  assert.equal(sM.isClosed().code, 4401, 'کدِ بستن پس از برداشتن');
}, { needs: ['S78.0'] });

await E.step('S78.live', 'سوکتِ عضوِ برداشته‌شده نه اسکنی به صندوقِ کامپیوترِ صاحب می‌برد و نه خبرِ دفترِ دکان را می‌گیرد', async () => {
  //  اگر سوکت هنوز باز است، اسکنِ «تزریقی» می‌فرستد
  if (!sM.isClosed()) sM.send({ type: 'relay', event: 'scan', code: 'M-AFTER-REMOVE', pair: '' });
  //  کنترلِ مثبت روی همان سوکتِ کامپیوتر: اسکنِ گوشیِ خودِ صاحب پس از آن
  const sA = await live(A);
  sA.send({ type: 'relay', event: 'scan', code: 'A-OWN', pair: '' });
  await sD.until(() => sD.msgs.find((m) => m.code === 'A-OWN'), 'اسکنِ گوشیِ صاحب');
  assert.equal(sD.msgs.some((m) => m.code === 'M-AFTER-REMOVE'), false, 'اسکنِ عضوِ برداشته‌شده به صندوقِ کامپیوترِ صاحب رسید');
  //  تغییرِ تازهٔ دفتر: کامپیوتر خبر می‌گیرد (کنترل)، عضوِ برداشته‌شده نه
  const before = sM.msgs.filter((m) => m.event === 'changed').length;
  A.stockIn({ name: 'روغن', price: 300, cost: 250, units: 2 });
  await A.drain();
  await sD.until(() => sD.msgs.filter((m) => m.event === 'changed').length >= 2, '«changed» برای کامپیوترِ صاحب');
  const sDp = await live(D);
  sDp.send({ type: 'ping' });
  await sDp.until(() => sDp.msgs.find((m) => m.event === 'pong'), 'pong');
  assert.equal(sM.msgs.filter((m) => m.event === 'changed').length, before, `عضوِ برداشته‌شده خبرِ دفتر را گرفت: ${JSON.stringify(sM.msgs)}`);
}, { needs: ['S78.0'] });

for (const ws of opened) ws.terminate();
await E.finish();
