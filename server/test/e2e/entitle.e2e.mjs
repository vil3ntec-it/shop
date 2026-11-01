/**
 * سناریوهای سرتاسریِ اشتراک و نسخهٔ برنامه — S60، S123، S129 (§۱۶، §۲۱.۱۹).
 *
 * بازیگرها (برنامه در سطحِ پروتکل):
 *   • O — گوشیِ صاحب؛ T — کامپیوترِ شاگرد (همان که بی اشتراکِ چنددستگاهی نمی‌نویسد)؛
 *   • V — گوشیِ کهنه‌ای که ساختِ قدیمی دارد (`X-App-Version` پایین‌تر از حداقل)؛
 *   • مدیرِ سرور از درِ پنل (`/api/admin/*`).
 *
 * ⛔ زمان: هیچ خوابی و هیچ دست‌کاریِ ساعتِ دیوار. «گذشتِ زمان» برای اشتراک با `ends_at` ِ همان ردیفِ
 * اشتراک و `created_at` ِ دکان (پایانِ آزمایشی) — همان کاری که `admin.e2e.mjs` می‌کند؛ ساعتِ گوشی با
 * `now()` ِ همان دستگاه (ساعتِ غلطِ گوشی) و سرآیندِ `X-Client-Time`.
 *
 * اجرا:  node test/e2e/run.mjs entitle
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'entitle' });
const { db } = E;
const DAY = 86_400_000;
const owner = await E.owner('owner-ent@e2e.local', 'صاحبِ اشتراک', 'دکانِ اشتراک');
const staff = await E.staff(owner, 'staff-ent@e2e.local', 'شاگردِ اشتراک');
//  «یک ماه بعد»: آزمایشیِ دکان (از created_at ِ خودش) تمام شده است
await db.query('UPDATE shops SET created_at=$1 WHERE id=$2', [Date.now() - 30 * DAY, owner.shopId]);

let O; let T; let V; let rice; let admin; let grantKey; let subId;
const sub = (dev, h = {}) => dev.api('GET', '/me/subscription', undefined, { headers: h });
const subOf = async (dev, h = {}) => { const b = E.ok(await sub(dev, h), 'اشتراک'); return b.subscription || b; };
const rowsOf = async () => (await db.one("SELECT COUNT(*)::int AS n, COALESCE(md5(string_agg(row_id || ':' || data::text, '|' ORDER BY row_id)), '') AS h FROM sync_rows WHERE account_kind='shop' AND account_id=$1 AND deleted_at IS NULL", [owner.shopId]));
const salesOnServer = async () => (await db.one("SELECT COUNT(*)::int AS n FROM sync_rows WHERE account_kind='shop' AND account_id=$1 AND table_name='sales'", [owner.shopId])).n;
const grant = (days, key) => E.post('/api/admin/subscriptions', { shopId: owner.shopId, plan: 'pro', days, reason: 'رسیدِ حواله' }, admin, { 'Idempotency-Key': key });

await E.step('S.ent.0', 'آزمایشیِ تمام‌شده؛ مدیر با کلیدِ یکتایی ۵ روز اشتراک می‌دهد؛ شاگرد می‌نویسد', async () => {
  admin = await E.adminToken();
  O = await E.device('O-owner', owner.email);
  T = await E.device('T-staff-desktop', staff.email);
  for (const d of [O, T]) assert.equal((await d.firstSync()).ok, true);
  rice = O.stockIn({ name: 'برنج', price: 100, cost: 70, units: 50 });
  await O.drain();
  await T.drain();
  grantKey = E.key('grant');
  const g = E.ok(await grant(5, grantKey), 'اعطای ۵ روز', [200, 201]);
  subId = g.subscription?.id || g.id;
  assert.ok(subId, JSON.stringify(g).slice(0, 200));
  T.sell({ lines: [{ id: rice, qty: 1 }] });
  await T.drain();
  assert.equal(T.queue.length, 0);
});

await E.step('S123', 'جعلِ اشتراک بی‌اثر: ساعتِ گوشی، ادعای vip در بدنه، نسخهٔ دیگرِ برنامه و درِ مستقیم؛ پرداختِ تکراری (حتی هم‌زمان) یک دوره', async () => {
  const s0 = await subOf(T);
  const ends0 = s0.endsAt;
  assert.ok(ends0 - Date.now() <= 5 * DAY + 60_000 && ends0 - Date.now() > 4 * DAY, `endsAt: ${JSON.stringify(s0)}`);
  //  ساعتِ گوشی ده سال جلو، و ادعای «vip» در بدنه و سرآیند ⇒ همان پایانِ سرور
  const fwd = await subOf(T, { 'X-Client-Time': String(Date.now() + 3650 * DAY) });
  assert.equal(fwd.endsAt, ends0);
  assert.ok(Math.abs(Number(fwd.serverTime) - Date.now()) < 60_000, `ساعتِ پاسخ ساعتِ سرور نیست: ${JSON.stringify(fwd).slice(0, 200)}`);
  const claim = await T.api('POST', '/me/purchase-request', { plan: 'vip', vip: true, endsAt: Date.now() + 3650 * DAY, status: 'active' });
  assert.ok([201, 400, 403].includes(claim.status), `درخواستِ خرید: ${claim.status}`);
  assert.equal((await subOf(T)).endsAt, ends0, 'ادعای کاربر اشتراک را عوض کرد');
  //  پرداختِ تکراری: همان کلید دو بار پشتِ سرِ هم و دو بار هم‌زمان ⇒ یک ردیف و همان پایان
  const n0 = (await db.one('SELECT COUNT(*)::int AS n FROM subscriptions WHERE shop_id=$1', [owner.shopId])).n;
  const again = await grant(5, grantKey);
  assert.ok([200, 201].includes(again.status), JSON.stringify(again.body).slice(0, 200));
  const pair = await Promise.all([grant(5, grantKey), grant(5, grantKey)]);
  assert.ok(pair.every((r) => [200, 201].includes(r.status)), JSON.stringify(pair.map((r) => r.status)));
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM subscriptions WHERE shop_id=$1', [owner.shopId])).n, n0, 'پرداختِ تکراری دورهٔ دوم ساخت');
  assert.equal((await subOf(T)).endsAt, ends0, 'پرداختِ تکراری دوره را دراز کرد');
  //  درِ مستقیمِ مدیر با توکنِ کاربر ⇒ ۴۰۱
  assert.equal((await T.api('POST', '/admin/subscriptions', { shopId: owner.shopId, plan: 'vip', days: 3650 })).status, 401);
}, { needs: ['S.ent.0'] });

await E.step('S60', 'انقضای اشتراک با ساعتِ غلطِ گوشی و قطع/وصل: یک قاعده (سرور)؛ نوشته‌های شاگرد روی گوشی می‌مانند، صاحب می‌نویسد، داده نه پاک نه قفل؛ تمدید همه را بالا می‌برد', async () => {
  //  ساعتِ شاگرد ۴۰۰ روز جلو، پیش از پایان ⇒ هنوز می‌نویسد (پایان را سرور می‌گوید)
  const realNow = T.now.bind(T);
  T.now = () => realNow() + 400 * DAY;
  T.sell({ lines: [{ id: rice, qty: 1 }] });
  await T.drain();
  assert.equal(T.queue.length, 0, 'ساعتِ جلوی گوشی نوشتن را بست');
  //  «پنج روز بعد»: پایانِ همان ردیف
  await db.query('UPDATE subscriptions SET ends_at=$1 WHERE id=$2', [Date.now() - 1000, subId]);
  const before = await rowsOf();
  const sales0 = await salesOnServer();
  //  ساعتِ شاگرد حالا ۴۰۰ روز عقب (گوشی می‌گوید «هنوز وقت هست») و بی اینترنت دو فروش
  T.now = () => realNow() - 400 * DAY;
  T.offline = true;
  T.sell({ lines: [{ id: rice, qty: 2 }] });
  T.sell({ lines: [{ id: rice, qty: 1 }] });
  const queued = T.queue.length;
  assert.ok(queued > 0);
  T.offline = false;
  const r = await T.sync();
  assert.equal(r.status, 403, JSON.stringify(r).slice(0, 200));
  assert.equal(r.body.error.code, 'subscription_required');
  assert.match(r.body.error.message, /روی همین گوشی می‌مانند/);
  assert.deepEqual([T.queue.length, T.dropped.length], [queued, 0], 'نوشتهٔ شاگرد دور ریخته شد');
  assert.equal(await salesOnServer(), sales0);
  //  همان قاعده برای ساعتِ جلو: هنوز بسته
  T.now = () => realNow() + 400 * DAY;
  assert.equal((await T.sync()).status, 403);
  //  داده نه پاک نه قفل: شاگرد و صاحب می‌خوانند، صاحب می‌نویسد
  assert.equal((await T.api('GET', '/sync/v1/snapshot')).status, 200, 'خواندنِ دفتر پس از پایان بسته شد');
  O.sell({ lines: [{ id: rice, qty: 1 }] });
  await O.drain();
  assert.equal(O.queue.length, 0, 'صاحب پس از پایان نمی‌نویسد');
  const after = await rowsOf();
  assert.ok(after.n >= before.n, 'ردیفی از دفتر پاک شد');
  const st = await subOf(T);
  assert.notEqual(st.status, 'active', JSON.stringify(st).slice(0, 200));
  //  تمدید ⇐ همان صفِ شاگرد بی کم و زیاد بالا می‌رود
  E.ok(await grant(30, E.key('grant')), 'تمدید', [200, 201]);
  T.now = realNow;
  await T.drain();
  assert.equal(T.queue.length, 0);
  assert.equal(await salesOnServer(), sales0 + 3, 'دو فروشِ صف + فروشِ صاحب');
  assert.equal((await subOf(T)).status, 'active');
}, { needs: ['S123'] });

await E.step('S129', 'به‌روزرسانی: حداقلِ نسخه از پنل ⇒ گوشیِ کهنه پیامِ ارتقا می‌گیرد ولی سرور ردش نمی‌کند؛ کهنه و تازه کنارِ هم همگام می‌شوند و داده‌ای گم نمی‌شود', async () => {
  const cfg = async (dev) => (await E.get('/api/config', null, { 'X-App-Id': 'shop', 'X-App': 'shop', 'X-App-Version': dev.appVersion })).body;
  assert.equal((await cfg(O)).minAppVersion, '', 'پیش‌فرض: هیچ حداقلی');
  E.ok(await E.put('/api/admin/downloads', { app: 'shop', version: '3.2.9999', minVersion: '3.2.5000' }, admin), 'حداقلِ نسخه');
  assert.equal((await cfg(O)).minAppVersion, '3.2.5000');
  V = await E.device('V-old-phone', owner.email, { appVersion: '3.2.100' });
  assert.equal((await V.firstSync()).ok, true, 'گوشیِ کهنه از سرور رانده شد');
  //  هر دو می‌نویسند؛ هر کدام کارِ دیگری را می‌گیرد
  V.sell({ lines: [{ id: rice, qty: 1 }] });
  O.sell({ lines: [{ id: rice, qty: 2 }] });
  await V.drain();
  await O.drain();
  await V.drain();
  assert.equal(V.queue.length + O.queue.length, 0);
  assert.equal(V.stock(rice), O.stock(rice), 'کهنه و تازه یک موجودی ندارند');
  const audit = await db.many("SELECT detail FROM audit_logs WHERE action='admin.downloads_updated' ORDER BY created_at DESC LIMIT 1");
  assert.equal(audit[0].detail.minVersion, '3.2.5000');
  //  برداشتنِ حداقل ⇒ همان رفتارِ پیشین
  E.ok(await E.put('/api/admin/downloads', { app: 'shop', version: '3.2.9999', minVersion: '' }, admin), 'برداشتنِ حداقل');
  assert.equal((await cfg(O)).minAppVersion, '');
}, { needs: ['S.ent.0'] });

await E.finish();
