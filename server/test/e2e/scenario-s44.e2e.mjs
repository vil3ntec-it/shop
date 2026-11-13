/**
 * سناریوی S44 (§۷.۵) — دستگاهِ گم‌شده (STF-10).
 *
 *   نتیجه: مالک از پنل دستگاه را غیرفعال می‌کند؛ نشست‌ها باطل می‌شوند؛ opهای بعدیِ آن دستگاه رد و ثبت
 *   می‌شوند؛ دادهٔ محلی رمزدار است (SEC-01)؛ opهای همگام‌نشدهٔ قبلی قابلِ بازیابی از پشتیبان‌اند.
 *
 * بازیگرها (برنامه در سطحِ پروتکل): A — گوشیِ صاحب (فهرستِ دستگاه‌ها و «غیرفعال کن»)؛ W — گوشیِ شاگرد
 * که گم می‌شود، با یک فروشِ همگام‌نشده در صف.
 * اجرا:  node test/e2e/run.mjs scenario-s44
 */
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'scenario-s44' });
const { db } = E;

/** سطرهایی که این‌جا سنجیده نمی‌شوند یا ساخته نشده‌اند — سند، نه assert */
export const PENDING = [
  'رمزِ دادهٔ محلی (SEC-01) روی خودِ دستگاه است — آزمون‌های core/atrest در برنامه، نه این سرور',
  'بازیابیِ opهای همگام‌نشدهٔ دستگاهِ گم‌شده: پشتیبانِ همان دستگاه فروش را دارد (این‌جا سنجیده شد)، ولی «بازیابی» هرگز op نمی‌سازد (RestoreRoute؛ ردیف‌های نبودهٔ سرور فقط شمرده می‌شوند) ⇒ راهی برای رساندنِ آن فروش به دفترِ دکان از دستگاهِ دیگر نیست',
];

const owner = await E.owner('owner-s44@e2e.local', 'صاحبِ S44', 'دکانِ S44');
const staffAcct = await E.staff(owner, 'staff-s44@e2e.local', 'شاگردِ S44');
let A; let W; let rice; let lost; let backup; let devId;
const deviceRow = async () => E.ok(await A.api('GET', '/shop/devices'), 'دستگاه‌ها').devices.find((d) => d.id === devId);

await E.step('S44.0', 'شاگرد روی گوشیِ خودش کار می‌کند؛ یک فروشِ آفلاین هنوز همگام نشده و پشتیبانِ گوشی آن را دارد', async () => {
  A = await E.device('A-owner-phone', owner.email);
  W = await E.device('W-staff-phone', staffAcct.email);
  assert.equal((await A.firstSync()).ok, true);
  rice = A.stockIn({ name: 'برنج', price: 600, cost: 450, units: 10 });
  await A.drain();
  assert.equal((await W.firstSync()).ok, true);
  W.offline = true;
  lost = W.sell({ lines: [{ id: rice, qty: 1 }] });
  assert.ok(W.queue.length > 0);
  backup = JSON.parse(JSON.stringify(W.ledger));
  assert.ok(backup.sales.some((s) => s.id === lost.saleId), 'پشتیبانِ گوشی فروشِ همگام‌نشده را دارد');
  devId = E.ok(await A.api('GET', '/shop/devices'), 'دستگاه‌ها').devices.find((d) => d.member.userId === staffAcct.user.id).id;
});

await E.step('S44.disable', 'صاحب از فهرستِ دستگاه‌ها گوشیِ گم‌شده را غیرفعال می‌کند ⇒ نشست‌ها باطل', async () => {
  const off = E.ok(await A.api('POST', `/shop/devices/${devId}/disable`, { reason: 'گم شد' }), 'غیرفعال');
  assert.ok(off.sessionsRevoked >= 1, JSON.stringify(off));
  const d = await deviceRow();
  assert.equal(d.status, 'revoked');
  assert.ok(d.disabledAt);
  //  نشستِ گوشی دیگر به هیچ دری نمی‌رسد (نه با توکنِ کهنه، نه با نوسازی)
  W.offline = false;
  const shop = await W.api('GET', '/shop', undefined, { retry: false });
  assert.ok([401, 403].includes(shop.status), `${shop.status}`);
  assert.equal(shop.body.error.code === 'device_disabled' || shop.status === 401, true, JSON.stringify(shop.body));
}, { needs: ['S44.0'] });

await E.step('S44', 'opهای بعدیِ آن گوشی رد و شمرده می‌شوند؛ هیچ اثری در دفترِ دکان؛ صفِ گوشی دور ریخته نمی‌شود', async () => {
  const before = (await deviceRow()).blockedAttempts || 0;
  const r = await W.sync();
  assert.equal(r.ok, false, 'گوشیِ غیرفعال همگام شد');
  const push = await W.api('POST', '/sync/v1/push', { device_id: W.deviceId, schema_version: W.wireSchema(), queued: W.queue.length, ops: W.queue }, { retry: false });
  assert.ok([401, 403].includes(push.status), `${push.status}`);
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND row_id=$2', [owner.shopId, lost.saleId])).n, 0, 'فروشِ گوشیِ گم‌شده به دفتر نرسید');
  assert.ok(W.queue.length > 0, 'صف روی گوشی ماند');
  const after = await deviceRow();
  assert.ok(after.blockedAttempts > before, `تلاش‌ها شمرده شد: ${before} ⇒ ${after.blockedAttempts}`);
  //  دفترِ دکان دست‌نخورده: موجودی همان ۱۰
  await A.drain();
  assert.equal(A.stock(rice), 10);
}, { needs: ['S44.disable'] });

//  د۱۸: پشتیبانِ گوشی صفِ نرفته را هم دارد (`pending-ops.json` ِ `BackupBundle`) ⇒ صاحب همان opها را از برنامهٔ خودش می‌فرستد
await E.step('S44.replay', 'بازیابی از پشتیبان (پرچمِ `shop.lost_device_replay`): همان opها با همان شناسه یک بار به دفترِ دکان؛ تکرار «تکراری»؛ خاموش ۴۰۴', async () => {
  const body = { deviceId: W.deviceId, ops: JSON.parse(JSON.stringify(W.queue)) };
  const off = await A.api('POST', `/shop/devices/${devId}/replay`, body);
  assert.equal(off.status, 404, JSON.stringify(off.body));
  assert.equal(off.body.error.code, 'lost_device_replay_off');
  await E.flag(owner.shopId, 'shop.lost_device_replay', true);
  //  پشتیبانِ دستگاهِ دیگر (شناسهٔ همگام‌سازیِ دیگر) رد
  const other = await A.api('POST', `/shop/devices/${devId}/replay`, { ...body, deviceId: 'some-other-device' });
  assert.equal(other.status, 400, JSON.stringify(other.body));
  assert.equal(other.body.error.code, 'device_mismatch');
  const r = E.ok(await A.api('POST', `/shop/devices/${devId}/replay`, body), 'بازیابی');
  assert.equal(r.counts.applied, body.ops.length, JSON.stringify(r));
  assert.equal(r.counts.rejected, 0, JSON.stringify(r.results));
  assert.equal((await db.one('SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND row_id=$2', [owner.shopId, lost.saleId])).n, 1, 'فروشِ گوشیِ گم‌شده یک بار رسید');
  const again = E.ok(await A.api('POST', `/shop/devices/${devId}/replay`, body), 'دوباره');
  assert.equal(again.counts.duplicate, body.ops.length);
  assert.equal(again.counts.applied, 0);
  //  گوشیِ صاحب فروش را می‌گیرد؛ موجودی یکی کم شد
  await A.drain();
  assert.ok(A.ledger.sales.some((x) => x.id === lost.saleId), 'فروش به دستگاهِ صاحب نرسید');
  assert.equal(A.stock(rice), 9);
  //  گوشیِ گم‌شده هنوز بسته است
  assert.equal((await W.sync()).ok, false);
  const audit = await db.one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='lost_device.replayed' ORDER BY created_at DESC LIMIT 1", [owner.shopId]);
  assert.ok(audit, 'سابقه');
}, { needs: ['S44'] });

await E.finish();
