/**
 * سناریوی سرتاسریِ کارهای مدیر با دلیل — E2E-ADM (پنلِ واقعیِ `/admin/` در کرومیوم) و اثرش
 * روی دستگاه‌های دکان (سطحِ پروتکل)؛ D-05: «داده هرگز قفل نمی‌شود».
 *
 *   • دورهٔ آزمایشیِ دکان تمام شده («یک ماه بعد»)؛ مدیر از پنل با دلیل اشتراک می‌دهد ⇐ شاگرد
 *     می‌نویسد. مدیر با دلیل تعلیق می‌کند ⇐ opهای شاگرد روی دستگاهش می‌مانند (۴۰۳، نه دور
 *     ریختن) و صاحب هنوز می‌نویسد؛ فعال‌سازیِ دوباره با دلیل ⇐ همان صف بالا می‌رود.
 *   • مدیر با دلیل حسابِ شاگرد را می‌بندد ⇐ نشستش همان لحظه بسته؛ باز کردن ⇐ ورود و ادامه.
 *   • هر کار یک ردیفِ سابقه با همان دلیل دارد.
 *
 * اجرا:  node test/e2e/admin.e2e.mjs
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'admin' });
const { db } = E;
const owner = await E.owner('owner-adm@e2e.local', 'صاحبِ مدیریت', 'دکانِ مدیریت');
const staff = await E.staff(owner, 'staff-adm@e2e.local', 'شاگردِ مدیریت');
//  «یک ماه بعد»: دورهٔ آزمایشیِ دکان (از created_at ِ خودش) تمام شده است
await db.query('UPDATE shops SET created_at=$1 WHERE id=$2', [Date.now() - 30 * 86400_000, owner.shopId]);
const audit = (action, target) => db.many('SELECT detail FROM audit_logs WHERE action=$1 AND target_id=$2 ORDER BY created_at', [action, String(target)]);

let A; let S; let P; let rice; let subId;

async function openShop(page) {
  await page.click('.tab[data-tab="shops"]');
  const row = page.locator('#shops-body tr', { hasText: 'دکانِ مدیریت' });
  await row.locator('button').first().click();
  await page.waitForSelector('#shop-detail:not(.hidden)', { timeout: 10_000 });
}

await E.step('E2E-ADM.0', 'آزمایشیِ تمام‌شده: شاگرد نمی‌نویسد (opها روی دستگاه می‌مانند)، صاحب می‌نویسد', async () => {
  A = await E.device('A-owner-phone', owner.email);
  S = await E.device('S-staff-desktop', staff.email);
  for (const d of [A, S]) assert.equal((await d.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 100, cost: 70, units: 20 });
  await A.drain();
  await S.drain();
  S.sell({ lines: [{ id: rice, qty: 1 }] });
  const r = await S.sync();
  assert.equal(r.status, 403, JSON.stringify(r));
  assert.equal(r.body.error.code, 'subscription_required');
  assert.ok(S.queue.length > 0 && S.dropped.length === 0, 'opهای شاگرد باید روی دستگاه بمانند، نه دور ریخته شوند');
});

await E.step('E2E-ADM.grant', 'مدیر در پنل وارد می‌شود و با دلیل اشتراک می‌دهد ⇐ صفِ شاگرد بالا می‌رود', async () => {
  P = await E.page('admin-panel', { width: 1280, height: 900 });
  const { page } = P;
  await page.goto(`${E.apiBase}/admin/`, { waitUntil: 'domcontentloaded' });
  await page.fill('#in-username', 'admin');
  await page.fill('#in-password', 'Admin!12345');
  await page.click('#btn-login');
  await page.waitForSelector('#app-view:not(.hidden)', { timeout: 10_000 });
  await openShop(page);
  await page.fill('#in-days', '30');
  await page.fill('#in-reason', 'رسیدِ حوالهٔ ماهِ مهر');
  await page.click('#btn-grant');
  await page.waitForFunction(() => /اشتراک ثبت شد/.test(document.getElementById('shop-msg')?.textContent || ''), null, { timeout: 10_000 });
  await E.shot(P, '01-admin-subscription-granted');
  const sub = await db.one(`SELECT id, status FROM subscriptions WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 1`, [owner.shopId]);
  subId = sub.id;
  assert.equal(sub.status, 'active');
  const rows = await audit('admin.subscription_granted', subId);
  assert.equal(rows.at(-1).detail.reason, 'رسیدِ حوالهٔ ماهِ مهر');
  await S.drain();
  await A.drain();
  assert.equal(A.ledger.sales.length, 1, 'فروشِ نگه‌داشته‌شدهٔ شاگرد پس از اشتراک به صاحب نرسید');
}, { needs: ['E2E-ADM.0'] });

await E.step('E2E-ADM.suspend', 'تعلیق با دلیل ⇐ صفِ شاگرد می‌ماند و صاحب می‌نویسد؛ فعال‌سازیِ دوباره با دلیل ⇐ همان صف بالا می‌رود', async () => {
  const { page } = P;
  await page.fill('#in-reason', 'چکِ برگشتی');
  await page.click('#btn-suspend');
  await page.waitForFunction(() => /وضعیت اشتراک عوض شد/.test(document.getElementById('shop-msg')?.textContent || ''), null, { timeout: 10_000 });
  assert.equal((await db.one('SELECT status FROM subscriptions WHERE id=$1', [subId])).status, 'suspended');
  const s = S.sell({ lines: [{ id: rice, qty: 2 }] });
  const r = await S.sync();
  assert.equal(r.status, 403, JSON.stringify(r));
  assert.ok(S.queue.length > 0 && S.dropped.length === 0);
  A.sell({ lines: [{ id: rice, qty: 1 }] });
  await A.drain();
  await page.fill('#in-reason', 'چک پاس شد');
  await page.click('#btn-activate');
  await page.waitForFunction(() => /وضعیت اشتراک عوض شد/.test(document.getElementById('shop-msg')?.textContent || ''), null, { timeout: 10_000 });
  await E.shot(P, '02-admin-reactivated');
  await S.drain();
  await A.drain();
  assert.ok(A.ledger.sales.some((x) => x.id === s.saleId), 'فروشِ زمانِ تعلیق گم شد');
  assert.equal(A.stock(rice), S.stock(rice));
  const reasons = (await audit('admin.subscription_status', subId)).map((x) => [x.detail.status || x.detail.to, x.detail.reason]);
  assert.deepEqual(reasons.map((x) => x[1]), ['چکِ برگشتی', 'چک پاس شد'], JSON.stringify(reasons));
}, { needs: ['E2E-ADM.grant'] });

await E.step('E2E-ADM.counts', 'پنل «اطلاعاتِ ثبت‌شده»ی دکان همان دفترِ برنامه‌ها را نشان می‌دهد (کالا و فروش)', async () => {
  const { page } = P;
  await openShop(page);
  await page.waitForFunction(() => (document.getElementById('shop-counts')?.children.length || 0) > 0, null, { timeout: 10_000 });
  const text = await page.locator('#shop-counts').innerText();
  const sales = A.ledger.sales.length;
  assert.ok(text.includes('محصولات: ۱') && text.includes(`فروش‌ها: ${String(sales).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[+d])}`),
    `پنل شمارِ دفترِ برنامه را نشان نمی‌دهد (Sync v1 شمرده نمی‌شود): ${text.replace(/\s+/g, ' ').slice(0, 160)}`);
}, { needs: ['E2E-ADM.suspend'] });

await E.step('E2E-ADM.user', 'مدیر با دلیل حسابِ شاگرد را می‌بندد ⇐ نشستش همان لحظه بسته؛ باز کردن ⇐ ورود و ادامه بی گم شدنِ صف', async () => {
  const { page } = P;
  P.reply = 'گوشیِ شاگرد گم شده است';
  await page.click('.tab[data-tab="users"]');
  const row = page.locator('#users-body tr', { hasText: staff.email });
  await row.waitFor({ timeout: 10_000 });
  await row.locator('button', { hasText: 'غیرفعال' }).click();
  await page.waitForFunction((em) => [...document.querySelectorAll('#users-body tr')].some((tr) => tr.textContent.includes(em) && /فعال$/.test(tr.querySelector('td:last-child button')?.textContent.trim() || '')), staff.email, { timeout: 10_000 });
  await E.shot(P, '03-admin-user-disabled');
  const d = await audit('admin.user_status', staff.user.id);
  assert.equal(d.at(-1).detail.reason, 'گوشیِ شاگرد گم شده است');
  S.sell({ lines: [{ id: rice, qty: 1 }] });
  const r = await S.sync();
  assert.ok([401, 403].includes(r.status), `حسابِ بسته هنوز می‌نویسد: ${JSON.stringify(r)}`);
  assert.ok(S.queue.length > 0, 'صفِ شاگرد نباید پاک شود');
  P.reply = 'گوشی پیدا شد';
  await row.locator('button', { hasText: /^فعال$/ }).click();
  await page.waitForFunction((em) => [...document.querySelectorAll('#users-body tr')].some((tr) => tr.textContent.includes(em) && /غیرفعال/.test(tr.querySelector('td:last-child button')?.textContent || '')), staff.email, { timeout: 10_000 });
  const pending = S.queue.slice();
  if (r.status === 401) await S.login(staff.email);
  S.queue = pending;
  await S.drain();
  await A.drain();
  assert.equal(A.stock(rice), S.stock(rice));
  assert.deepEqual((await audit('admin.user_status', staff.user.id)).map((x) => x.detail.reason), ['گوشیِ شاگرد گم شده است', 'گوشی پیدا شد']);
}, { needs: ['E2E-ADM.grant'] });

await E.step('E2E-ADM.errors', 'بی خطای جاوااسکریپت در پنلِ مدیر', async () => { assert.deepEqual(E.pageErrors, []); });

await E.finish();
