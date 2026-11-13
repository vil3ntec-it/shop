/**
 * سناریوی S45 (§۷.۵) — شاگردِ بی‌مجوز (STF-01 تا STF-03).
 *
 *   نتیجه: هر عملِ ممنوع یا «نیازمندِ تأیید» پنجرهٔ درخواستِ تأیید باز می‌کند، نه پیامِ خطای مبهم.
 *   در سابقه، درخواست‌کننده و تأییدکننده هر دو ثبت می‌شوند.
 *
 * بازیگرها: صاحب (سقف‌ها را می‌گذارد و با کلیدِ ES256 ِ خودش «برگهٔ تأیید» امضا می‌کند — همان کاری که پنجرهٔ
 * PIN ِ صندوق می‌کند)؛ شاگرد (فروشِ تخفیف‌دار، لغو و برگشتِ بالای سقف از راهِ Sync v3، همان opهای برنامه).
 * سرور با `SYNC_V3_ENABLED=1`.
 * اجرا:  node test/e2e/run.mjs scenario-s45
 */
import { createRequire } from 'node:module';
import crypto from 'node:crypto';
import { boot, assert } from './lib/harness.mjs';

const E = await boot({ name: 'scenario-s45', env: { SYNC_V3_ENABLED: '1' } });
const require = createRequire(import.meta.url);
const hlc = require('../../src/lib/hlc');
const slipCore = require('../../src/lib/approval-core');
const { db } = E;

/** سطرهایی که این‌جا سنجیده نمی‌شوند — سند، نه assert */
export const PENDING = [
  '«پنجرهٔ درخواستِ تأیید» خودِ برنامه است (JVM): تخفیف (DiscountGateTest، #284)، فروشِ قرضی بیش از سقف (S07، `creditGate`) و تغییرِ قیمتِ بیرون از قاعده (ScenarioS45PriceTest، `PriceApproval`) با درخواست‌کننده و تأییدکننده در سابقه؛ برای لغو در برنامه پنجره‌ای نیست (STF-02)',
  'سقفِ فروشِ قرضی و تغییرِ قیمت را سرور نمی‌سنجد (برنامه با PIN و دلیل، بی برگهٔ امضاشده — STF-03)؛ نقش‌های صندوق‌دار، انباردار، جمع‌آورنده و حسابدار نیستند (STF-01)',
];

const owner = await E.owner('owner-s45@e2e.local', 'صاحبِ S45', 'دکانِ S45');
const staffAcct = await E.staff(owner, 'staff-s45@e2e.local', 'شاگردِ S45');
let n = 0;
const oid = () => `01JS45${String(++n).padStart(20, '0')}`;
const H = () => hlc.format({ wall: Date.now() + n, counter: 0, node: 'pos-s45' });
const push3 = (token, ops) => E.post('/api/sync/v3/push', { device_id: 'pos-s45', protocol: 3, ops }, token);
const sale = (id, total, discount, extra = {}) => ({
  op_id: oid(), hlc: H(), table: 'sales', row_id: id, kind: 'insert',
  fields: { totalMinor: total, discountMinor: discount, finalTotalMinor: total - discount, status: 'completed' }, ...extra,
});
let key;
function slip({ doc, perm, pct_bp = null, amount = null, reason }) {
  const iat = Date.now() - 1000;
  const s = { v: 1, shop: owner.shopId, doc, perm, pct_bp, amount, by: owner.user.id, key: key.id, iat, exp: iat + 10 * 60_000,
    nonce: crypto.randomBytes(12).toString('base64url'), reason };
  s.sig = crypto.sign('sha256', Buffer.from(slipCore.payload(s), 'utf8'), { key: key.priv, dsaEncoding: 'der' }).toString('base64');
  return s;
}

await E.step('S45.0', 'صاحب: تخفیفِ شاگرد «نیازمندِ تأیید»، برگشت با سقفِ ۵۰۰ افغانی؛ کلیدِ تأییدِ صاحب ثبت', async () => {
  E.ok(await E.roleLayer(owner, 'staff', { 'sales.discount': { mode: 'approve' }, 'returns.create': { caps: { maxAmount: 50000 } } }), 'سقف‌ها');
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const r = E.ok(await E.post('/api/shop/approver-keys', { public_key: publicKey.export({ format: 'der', type: 'spki' }).toString('base64'), label: 'صندوقِ ۱', device_id: 'pos-s45' }, owner.token), 'کلید', [200, 201]);
  key = { id: r.key.id, priv: privateKey };
  const shop = E.ok(await E.get('/api/shop', staffAcct.token), 'دکان');
  assert.equal(shop.policy.effective['sales.discount'].mode, 'approve', 'برنامه از پیش می‌داند که باید «درخواستِ تأیید» باز کند');
});

await E.step('S45.ask', 'عملِ «نیازمندِ تأیید» بی برگه ⇒ پاسخِ روشنِ approval_required با مجوز و سقف (جای پنجرهٔ تأیید)، نه خطای مبهم؛ هیچ اثری', async () => {
  const r = await push3(staffAcct.token, [sale('s45-disc', 10000, 1500)]);
  assert.equal(r.status, 200);
  const res = r.body.results[0];
  assert.deepEqual([res.status, res.reason], ['rejected', 'approval_required'], JSON.stringify(res));
  const box = await db.one(`SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND row_id=$2 AND status='open'`, [owner.shopId, 's45-disc']);
  assert.equal(box.kind, 'approval_required');
  assert.equal(box.detail.perm, 'sales.discount', 'کدام مجوز');
  //  برگشتِ ۸۰۰ افغانی از فاکتورِ بی‌تخفیفِ نشسته (سقفِ شاگرد ۵۰۰)
  assert.equal((await push3(staffAcct.token, [sale('s45-base', 100000, 0)])).body.results[0].status, 'applied');
  const ret = await push3(staffAcct.token, [{ op_id: oid(), hlc: H(), table: 'returns', row_id: 's45-ret', kind: 'insert', fields: { amountMinor: 80000, saleId: 's45-base' } }]);
  assert.equal(ret.body.results[0].reason, 'approval_required', `برگشتِ بالای سقف هم همان پاسخ: ${JSON.stringify(ret.body.results[0])}`);
  const retBox = await db.one(`SELECT detail FROM sync_v3_conflicts WHERE account_id=$1 AND row_id=$2 AND status='open'`, [owner.shopId, 's45-ret']);
  assert.equal(retBox.detail.cap, 'maxAmount', 'کدام سقف');
  assert.equal(await db.one(`SELECT 1 FROM sync_v3_rows WHERE account_id=$1 AND row_id='s45-disc'`, [owner.shopId]), null);
}, { needs: ['S45.0'] });

await E.step('S45', 'صاحب تأیید می‌کند (برگهٔ امضاشده) ⇒ می‌نشیند؛ در سابقه درخواست‌کننده و تأییدکننده هر دو ثبت', async () => {
  const s = slip({ doc: 'sales/s45-ok', perm: 'sales.discount', pct_bp: 1500, amount: 1500, reason: 'مشتریِ همیشگی' });
  const op = sale('s45-ok', 10000, 1500, { approval: s });
  const r = await push3(staffAcct.token, [op]);
  assert.equal(r.body.results[0].status, 'applied', JSON.stringify(r.body));
  const list = await E.approvals(owner.shopId);
  const a = list.find((x) => x.doc === 'sales/s45-ok');
  assert.ok(a, JSON.stringify(list));
  assert.equal(a.requesterId, staffAcct.user.id, 'درخواست‌کننده');
  assert.equal(a.approverId, owner.user.id, 'تأییدکننده');
  assert.ok(a.approverName);
  assert.equal(a.reason, 'مشتریِ همیشگی');
  //  همان برگه روی سندِ دیگر ⇒ رد (یک‌بارمصرف)
  const replay = await push3(staffAcct.token, [sale('s45-other', 10000, 1500, { approval: s })]);
  assert.equal(replay.body.results[0].status, 'rejected');
  //  شاگرد سابقهٔ دکان را نمی‌بیند (`audit.view`)
  assert.equal((await E.get('/api/shop/audit', staffAcct.token)).status, 403);
}, { needs: ['S45.0'] });

await E.finish();
