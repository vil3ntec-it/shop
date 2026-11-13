'use strict';
/**
 * SUB-10 (و با آن SUB-02 و F0-25): آزمونِ مستقیمِ **همهٔ** APIهای پولی با اشتراکِ
 * منقضی، معلق یا لغوشده — §۲۹.۸ و §۲.۳.
 *
 * مسیرها از خودِ سورس خوانده می‌شوند (`route-inventory.js`، همان روشِ
 * `shop-route-inventory.test.js`). هر مسیرِ نوشتنیِ `/api/shop` باید دقیقاً در یکی
 * از دسته‌های زیر باشد؛ مسیرِ تازه‌ای که دسته نداشته باشد آزمون را سرخ می‌کند:
 *   PAID       — کلیدِ پولیِ کاتالوگ (online_orders، storefront، delivery، multi_device):
 *                برای **صاحبِ دکان هم** ۴۰۳ subscription_expired|subscription_required
 *   JOIN       — پیوستنِ شاگرد با کد (multi_device در lib/staff-codes): ۴۰۳ با کدِ واقعی
 *   STAFF      — نوشتنِ دادهٔ دکان (همگام‌سازی): شاگرد ۴۰۳، صاحب همیشه می‌نویسد
 *                (قاعدهٔ requireDataWrite؛ F0-25 / D-05: دادهٔ فروشنده گروگان نمی‌شود)
 *   FREE       — بی کلیدِ پولی؛ اشتراکِ تمام‌شده هیچ‌گاه پاسخِ subscription_* نمی‌دهد
 * و پیش از هر حالت، با اشتراکِ فعال همان مسیرهای PAID بسته نیستند (ردِ بالا از اشتراک است).
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const inv = require('./route-inventory');
const { query, newId, now } = require('../src/db');
const { PAID_KEYS } = require('../src/lib/features');

const DAY = 24 * 60 * 60 * 1000;
const PAID = /^(POST|PUT|PATCH|DELETE) \/shop\/(orders|order-feedback|storefront|market|delivery)(\/|$)|^POST \/shop\/(staff-code|invite|staff-code\/rotate)$/;
const JOIN = ['POST /shop/staff/join', 'POST /shop/join'];
const STAFF = ['POST /shop/sync', 'POST /shop/sync/push'];
/** نوشتنی‌های بی کلیدِ پولی — هر کدام با دلیل؛ فهرست باید با سورس یکی بماند. */
const FREE = [
  ['هستهٔ دکان (core: settings): نام، تنظیمات، شماره‌گذاری و بستنِ دسترسیِ دیگران پس از پایانِ اشتراک هم باز است', [
    'POST /shop', 'POST /shop/create', 'PUT /shop', 'POST /shop/series',
    'PATCH /shop/members/:id', 'PUT /shop/members/:id', 'DELETE /shop/members/:id', 'POST /shop/members/:id/remove',
    'POST /shop/staff-code/disable', 'DELETE /shop/staff-codes/:id',
    'PUT /shop/permissions/members/:id',
    'POST /shop/devices/:id/disable', 'POST /shop/devices/:id/enable',
    'POST /shop/approver-keys', 'DELETE /shop/approver-keys/:id']],
  ['چت و شکایت کلیدِ کاتالوگ ندارند؛ فقط پرچم', [
    'POST /shop/chat/conversations', 'PUT /shop/chat/conversations/:id/status', 'POST /shop/chat/conversations/:id/messages',
    'PATCH /shop/chat/conversations/:id/messages/:mid', 'POST /shop/chat/conversations/:id/messages/:mid/edit', 'DELETE /shop/chat/conversations/:id/messages/:mid',
    'POST /shop/chat/conversations/:id/receipts', 'POST /shop/chat/conversations/:id/media',
    'PUT /shop/chat/conversations/:id/block', 'DELETE /shop/chat/conversations/:id/block', 'POST /shop/chat/conversations/:id/reports',
    'PUT /shop/chat/conversations/:id/pins/:mid', 'DELETE /shop/chat/conversations/:id/pins/:mid',
    'POST /shop/trust/cases/:id/responses', 'POST /shop/trust/cases/:id/appeal']],
  ['موجودیِ سرور، بانکِ عکس، قاعده و مصرفِ کدِ تخفیف (بخشی از صندوقِ پایه، PRM-02) و رسیدِ اعلان کلیدِ کاتالوگ ندارند', [
    'PUT /shop/catalog/settings', 'POST /shop/catalog/refresh', 'PUT /shop/catalog/products/:id/low',
    'POST /shop/catalog/tasks/:id/resolve', 'POST /shop/catalog/moves',
    'POST /shop/catalog/transfers', 'POST /shop/catalog/transfers/:id/receive', 'POST /shop/catalog/transfers/:id/reject',
    'POST /shop/images/select', 'POST /shop/images', 'POST /shop/images/:id/versions', 'PUT /shop/images/:id/v/:version/:size',
    'POST /shop/images/:id/propose', 'POST /shop/images/:id/report', 'POST /shop/images/:id/process', 'POST /shop/images/:id/detach',
    'POST /shop/images/changes', 'POST /shop/images/changes/:id/rollback',
    'POST /shop/images/studio/batches', 'POST /shop/images/studio/batches/:id/action', 'POST /shop/images/studio/images/:id/versions/:version/review',
    'PUT /shop/promos/settings', 'PUT /shop/promos/rules/:id', 'DELETE /shop/promos/rules/:id',
    'POST /shop/promos/redeem', 'POST /shop/promos/redeem/release',
    'POST /shop/order-notify/:id/ack']],
  ['دسترسیِ پشتیبانی (۲-۹): صاحبِ دکان اجازه را می‌پذیرد، رد یا لغو می‌کند — کنترلِ دادهٔ خودش است و پس از پایانِ اشتراک هم باز می‌ماند؛ فقط صاحب', [
    'POST /shop/support-access/:gid/accept', 'POST /shop/support-access/:gid/deny', 'POST /shop/support-access/:gid/revoke']],
  ['گزارشِ خودکارِ صاحب (PAY-20/RPT-12) بخشی از مدیریتِ پایهٔ دکان است و کلیدِ کاتالوگ ندارد؛ فقط پرچمِ shop.owner_report و فقط صاحب', [
    'PUT /shop/owner-report']],
  ['قاعدهٔ یکسانِ امتیازِ وفاداری (PRM-13) بخشی از صندوقِ پایه است و کلیدِ کاتالوگ ندارد؛ فقط پرچمِ shop.loyalty_rule و فقط صاحب/مدیر', [
    'PUT /shop/loyalty']],
  ['تنظیماتِ دکان (همگام‌سازیِ زنده، مهاجرتِ ۳۰۰) بخشی از هستهٔ دکان است و کلیدِ کاتالوگ ندارد؛ فقط پرچمِ shop.settings_sync و فقط صاحب/مدیر', [
    'PUT /shop/settings-sync']],
  ['پشتیبانِ فاکتورهای نگه‌داشته (مهاجرتِ ۴۲۵، POS-12/S09) بخشی از صندوقِ پایه است و کلیدِ کاتالوگ ندارد؛ فقط پرچمِ shop.held_sync و مجوزِ فروش — '
    + 'نگه‌داشتهٔ فروشنده پس از پایانِ اشتراک هم گروگان نمی‌شود', [
    'PUT /shop/held-invoices/:id', 'DELETE /shop/held-invoices/:id']],
  ['سربرگ و لوگوی فاکتورِ نسخهٔ ۲ بخشی از صندوقِ پایه است و کلیدِ کاتالوگ ندارد (#379؛ فقط پرچمِ shop.receipt_v2)', [
    'PUT /shop/receipt-profile', 'PUT /shop/receipt-profile/logo', 'DELETE /shop/receipt-profile/logo']],
  ['پرسشِ فقط‌خواندنیِ همگام‌سازی پس از بازیابی (§۹.۵): هیچ داده‌ای نمی‌نویسد و مثلِ خودِ همگام‌سازی پس از پایانِ اشتراک هم باز است تا داده گم نشود (پشتِ پرچمِ خاموشِ SYNC_EPOCH_ENABLED)', [
    'POST /shop/sync-epoch/deleted']],
  ['سفارشِ خرید زیرِ کلیدِ رایگانِ purchasing است (FREE_KEYS)؛ فقط پرچمِ shop.purchase_orders و مجوزِ نقش (PUR-02/04، '
    + 'فاکتورِ جدای تأمین‌کننده PUR-14)', [
    'POST /shop/purchase-orders', 'PUT /shop/purchase-orders/:id', 'POST /shop/purchase-orders/:id/send',
    'POST /shop/purchase-orders/:id/cancel', 'POST /shop/purchase-orders/:id/receipts',
    'POST /shop/purchase-orders/:id/bills', 'POST /shop/purchase-orders/:id/bills/:billId/void']],
  ['دکانِ فعالِ نشست (مهاجرتِ ۰۹۷، #273) دادهٔ دکان نمی‌نویسد؛ پس از پایانِ اشتراکِ یک دکان هم باید بتوان به دکانِ دیگر رفت', [
    'POST /shop/active']],
  ['پرداختِ حساب‌پی (PAY-21) کلیدِ کاتالوگ ندارد، فقط پرچمِ pay.hesabpay؛ پرداختِ سفارشِ آنلاین اشتراکِ فعال را خودش می‌سنجد '
    + '(online_payment_unavailable)', [
    'POST /shop/payments/intents', 'POST /shop/payments/intents/:id/cancel']],
  ['«حساب‌پیِ دکان» (حساب و رمزِ جدای حساب‌پی، فقط صاحب) کلیدِ کاتالوگ ندارد: صاحب پس از پایانِ اشتراک هم باید بتواند حسابِ '
    + 'پولش را ببیند، عوض کند یا بردارد', [
    'POST /shop/hesabpay/open', 'PUT /shop/hesabpay/pin', 'POST /shop/hesabpay/pin/reset',
    'PUT /shop/hesabpay/account', 'POST /shop/hesabpay/account/remove',
    //  «این من نبودم — لغو کن» (نگهبانِ حسابِ گیرنده، مهاجرتِ ۲۰۵): دزدیدنِ حساب پس از پایانِ اشتراک هم باید لغوشدنی بماند
    'POST /shop/hesabpay/pending/cancel']],
  ['ورود و خروجِ کارمند و نرخ‌های پورسانت (STF-07، STF-08) کلیدِ کاتالوگ ندارند، فقط پرچمِ shop.staff_time؛ '
    + 'ساعتِ کارِ ثبت‌شده پس از پایانِ اشتراک هم باید ثبت‌شدنی بماند', [
    'POST /shop/staff-time/punches', 'PUT /shop/staff-time/commission-plan']],
  ['حذفِ دادهٔ شخصیِ مشتری (SEC-10) کلیدِ کاتالوگ ندارد، فقط پرچمِ shop.customer_erasure: حقِ حذف به اشتراک بسته نیست '
    + 'و پس از پایانِ اشتراک هم باید شدنی بماند', [
    'POST /shop/customer-erasure']],
  ['محدودهٔ مکانِ کارمندان (STF-06) بخشی از مجوزهای هستهٔ دکان است و کلیدِ کاتالوگ ندارد، فقط پرچمِ shop.staff_scope؛ '
    + 'صاحب باید پس از پایانِ اشتراک هم بتواند دسترسیِ کارمند را ببندد', [
    'PUT /shop/staff-scope/members/:id']],
  ['PIN ِ شخصیِ صندوق و نوبت‌های سنجیدهٔ صندوق (STF-04، S46، مهاجرتِ ۶۴۰) هویتِ کاربرِ صندوق‌اند، بخشی از مجوزهای هستهٔ دکان، '
    + 'و کلیدِ کاتالوگ ندارند، فقط پرچمِ shop.till_pin؛ صاحب باید پس از پایانِ اشتراک هم PIN را بگذارد/بردارد و قفلش را باز کند', [
    'PUT /shop/till-pin/me', 'PUT /shop/till-pin/members/:id', 'DELETE /shop/till-pin/members/:id', 'POST /shop/till-pin/members/:id/unlock',
    'POST /shop/till-pin/verify', 'POST /shop/till-pin/current/end']],
  ['«درخواستِ تأیید» از راهِ دور (STF-02) بخشی از مجوزهای هستهٔ دکان است و کلیدِ کاتالوگ ندارد، فقط پرچمِ '
    + 'shop.approval_requests؛ مثلِ برگهٔ امضاشده (`/shop/approver-keys`) پس از پایانِ اشتراک هم باید کار کند', [
    'POST /shop/approval-requests', 'POST /shop/approval-requests/:id/approve', 'POST /shop/approval-requests/:id/deny',
    'POST /shop/approval-requests/:id/cancel']],
  ['نقشِ سفارشی (STF-01) همان لایهٔ مجوزِ نقش و کاربر (`/shop/permissions/*`، FREE) با نام است و کلیدِ کاتالوگ ندارد، فقط '
    + 'پرچمِ shop.custom_roles؛ صاحب باید پس از پایانِ اشتراک هم بتواند دسترسیِ کارمند را ببندد', [
    'POST /shop/custom-roles', 'PUT /shop/custom-roles/:id', 'DELETE /shop/custom-roles/:id', 'PUT /shop/custom-roles/members/:id']],
  ['دستگاه‌های شناختهٔ کارمندان (رازِ ورود با کد، مهاجرتِ ۳۴۵): «باطل کردنِ این دستگاه» و «خروج از همه» ِ یک عضو فقط '
    + 'دسترسی را کم می‌کنند و کلید و پرچم ندارند؛ صاحب باید پس از پایانِ اشتراک هم بتواند کارمند را بیرون کند (staff-key-life.test.js)', [
    'POST /shop/staff-keys/:id/revoke', 'POST /shop/staff-keys/members/:id/logout-all']],
  ['صندوقِ سندهای ردشده (DATA-SAFETY، مهاجرتِ ۳۱۵): «دوباره ثبت» و «کنار بگذار» ِ صاحب/مدیر همان همگام‌سازیِ دادهٔ فروشنده '
    + 'است و کلیدِ کاتالوگ ندارد (D-05: داده گروگانِ اشتراک نمی‌شود)؛ بی پرچم', [
    'POST /shop/conflict-inbox/:id/resubmit', 'POST /shop/conflict-inbox/:id/dismiss']],
  ['«جست‌وجو با عکس» (AI-11) فقط‌خواندنی است، مثلِ جست‌وجوی متنیِ کالا، و کلیدِ کاتالوگ ندارد؛ فقط پرچمِ shop.visual_search', [
    'POST /shop/visual-search']],
  ['پوشِ گوشی (O-01، FCM): ثبت و پاک کردنِ توکنِ دستگاهِ خودِ عضو — خبر و اعلان هیچ‌وقت پشتِ اشتراک نمی‌رود؛ '
    + 'فقط پرچمِ shop.device_push (پاک کردن همیشه باز)', [
    'POST /shop/push/devices', 'DELETE /shop/push/devices']],
  ['«سابقهٔ دسترسی» (CRM-15، درِ app ِ مهاجرتِ ۶۱۰): دیدنِ پرونده از دفترِ خودِ برنامه (وب، حسابدار) سپرِ حریمِ خصوصی است، '
    + 'نه قابلیتِ پولی؛ کلید و پرچم ندارد و پس از پایانِ اشتراک هم باید ثبت شود (customer-access-app.test.js)', [
    'POST /shop/customer-access']],
];
const FREE_KEYS = FREE.flatMap(([, keys]) => keys);
const FLAGS = ['shop.orders', 'shop.orders.refunds', 'shop.orders.delay', 'shop.delivery', 'shop.storefront', 'shop.storefront.zones',
  'shop.storefront.notify_area', 'shop.storefront.banners',
  'shop.chat', 'shop.catalog', 'shop.image_bank', 'shop.order_notify', 'market.trust', 'shop.staff_time', 'shop.staff_scope', 'shop.till_pin', 'shop.orders.desk', 'shop.orders.bulk', 'shop.doc_chain', 'shop.purchase_orders', 'shop.promo_uses'];

//  جمع‌آوری و بسته‌بندی (PCK): درهای `/shop/orders/…/pick…` با پرچمِ خودشان هم سنجیده شوند
FLAGS.push('shop.orders.picking');
//  S56: وزنِ بیش از حد (`/shop/orders/:id/pick/weight-holds/decide`) با پرچمِ خودش
FLAGS.push('shop.orders.weight_hold');
//  M2: انتشارِ گروهیِ ویترین (`PUT /shop/storefront/products`) با پرچمِ خودش
FLAGS.push('shop.storefront.bulk');
//  M3: لوگو و تصویرِ سرِ ویترین (`/shop/storefront/media/*`) با پرچمِ خودش
FLAGS.push('shop.storefront.media');
//  MKT-10/11: پیوندِ کمپین و QR (`/shop/storefront/campaigns`) با پرچمِ خودش
FLAGS.push('shop.storefront.campaigns');
//  SEC-10: حذفِ دادهٔ شخصیِ مشتری (`/shop/customer-erasure`) با پرچمِ خودش
FLAGS.push('shop.customer_erasure');
//  MKT-02/RSK-09: اولین خرید و سدِ تخفیف (`/shop/storefront/promo-guard`) با پرچمِ خودش
FLAGS.push('shop.storefront.promo_guard');
//  WEB-04: تنظیمِ زمانِ تقریبیِ تحویل (`PUT /shop/storefront/eta`) با پرچمِ خودش
FLAGS.push('shop.storefront.eta');
//  RSK-08: پروندهٔ مدرکِ اختلافِ سفارش (`POST /shop/orders/evidence/:id/disputes…`، PAID با needWrite) با پرچمِ خودش
FLAGS.push('shop.orders.dispute_file');
//  NTF-10: سیاستِ پوزش و کوپنِ تأخیر (`PUT /shop/orders/late-apology`) با پرچمِ خودش
FLAGS.push('shop.orders.late_apology');
//  PRM-13: قاعدهٔ یکسانِ امتیازِ وفاداری (`PUT /shop/loyalty`، FREE) با پرچمِ خودش
FLAGS.push('shop.loyalty_rule');
//  AI-11: «جست‌وجو با عکس» (`POST /shop/visual-search`) با پرچمِ خودش
FLAGS.push('shop.visual_search');
//  O-01: پوشِ گوشی (`/shop/push/devices`، FREE) با پرچمِ خودش
FLAGS.push('shop.device_push');
//  STF-02: «درخواستِ تأیید» از راهِ دور (`/shop/approval-requests`، FREE) با پرچمِ خودش
FLAGS.push('shop.approval_requests');
//  STF-01: نقشِ سفارشی (`/shop/custom-roles`، FREE) با پرچمِ خودش
FLAGS.push('shop.custom_roles');
//  تنظیماتِ دکان، همگام‌سازیِ زنده (`PUT /shop/settings-sync`، FREE) با پرچمِ خودش
FLAGS.push('shop.settings_sync');
//  POS-12/S09: پشتیبانِ فاکتورهای نگه‌داشته (`PUT`/`DELETE /shop/held-invoices/:id`، FREE) با پرچمِ خودش
FLAGS.push('shop.held_sync');
const writes = () => inv.routes('/shop').filter((r) => r.method !== 'GET');
const paidGate = (r) => /\bneedWrite\b/.test(r.head)
  || [...`${r.uses} ${r.head}`.matchAll(/requireFeature\(\s*'([^']+)'/g)].some((m) => PAID_KEYS.includes(m[1]));
const code = (r) => r.body?.error?.code || '';
const blocked = (r) => r.status === 403 && ['subscription_expired', 'subscription_required'].includes(code(r));
const url = (r) => `/api${r.path.replace(/:[A-Za-z_]+/g, 'x1')}`;

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

/** دکانِ با اشتراکِ فعال، همهٔ پرچم‌ها، یک شاگرد و یک کدِ دعوتِ آماده؛ دورهٔ آزمایشی دیرزمانی تمام شده. */
async function paidShop(label) {
  const owner = await h.newUser(`صاحب-${label}`);
  const shopId = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken })).body.shop.id;
  const g = await h.post('/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  for (const key of FLAGS) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const first = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد-${label}`);
  assert.ok((await h.post('/api/shop/staff/join', { code: first }, { token: staff.accessToken })).status < 300);
  const spare = await h.post('/api/shop/invite', { role: 'staff' }, { token: owner.accessToken });
  assert.equal(spare.status, 201, JSON.stringify(spare.body));
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shopId, Date.now() - 400 * DAY]);
  return { owner, staff, shopId, subId: g.body.subscription.id, spare: spare.body.code };
}

const setStatus = (s, status) => h.post(`/api/admin/subscriptions/${s.subId}/status`, { status }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const STATES = {
  'منقضی با گذشتِ زمان': (s) => query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [s.subId, Date.now() - 1000]),
  'منقضی (وضعیتِ expired)': (s) => setStatus(s, 'expired'),
  'معلق (suspended)': (s) => setStatus(s, 'suspended'),
  'لغوشده (cancelled)': (s) => setStatus(s, 'cancelled'),
};

let seq = 0;
const op = () => ({ op_id: `01JS${String(Date.now()).slice(-9)}${String(++seq).padStart(7, '0')}`,
  ts: Date.now(), table: 'products', row_id: `p-${seq}`, type: 'insert', fields: { name: 'کالا' } });
/** راه‌های نوشتنِ دادهٔ دکان (STAFF و بیرون از /api/shop) با بدنهٔ درست. */
const DATA_WRITES = {
  'POST /shop/sync': (t) => h.post('/api/shop/sync', { deviceId: 'd1', changes: [{ collection: 'sales', id: `s-${++seq}`, updatedAt: Date.now(), data: { total: 100 } }] }, { token: t }),
  'POST /shop/sync/push': (t) => h.post('/api/shop/sync/push', { deviceId: 'd1', changes: [{ collection: 'sales', id: `s-${++seq}`, updatedAt: Date.now(), data: { total: 100 } }] }, { token: t }),
  'POST /sync': (t) => h.post('/api/sync', { deviceId: 'd1', changes: [{ collection: 'sales', id: `s-${++seq}`, updatedAt: Date.now(), data: { total: 100 } }] }, { token: t }),
  'POST /sync/v1/push': (t, dev) => h.api('POST', '/api/sync/v1/push', { token: t, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, schema_version: 2, ops: [op()] } }),
  'POST /sync/v3/push': (t, dev) => h.post('/api/sync/v3/push', { device_id: dev, protocol: 3, ops: [] }, { token: t }),
  //  SEC-02: گزارشِ زنجیرهٔ اسنادِ دستگاه — همان قاعدهٔ push (`requireSyncWrite`)؛ پرچمِ `shop.doc_chain` روشن است
  'POST /sync/v1/chain': (t, dev) => h.api('POST', '/api/sync/v1/chain', { token: t, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, format: 'tohid-hashchain-v1', heads: [], links: [] } }),
};
/**
 * FREE ِ بیرون از /api/shop: پاسبانیِ یکپارچگی، نه نوشتنِ دادهٔ دکان — شاگرد و صاحب با هر وضعِ اشتراک، هرگز
 * subscription_* و هرگز ۵xx؛ با پرچمِ روشن واقعاً می‌نشیند (`enabled: true`).
 */
const SYNC_FREE = {
  //  STF-09: سرِ زنجیرهٔ سابقهٔ عملیاتِ دستگاه (`routes/audit-anchor.js`، بی `requireSyncWrite`)؛ پرچمِ `shop.doc_chain` روشن است.
  //  بریدن یا دست‌کاریِ سابقه پس از پایانِ اشتراک هم باید دیده شود
  'POST /sync/v1/audit-anchor': (t, dev) => h.api('POST', '/api/sync/v1/audit-anchor', { token: t, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, format: 'tohid-audit-chain-v1', heads: [{ chain: `${dev}/1`, seq: ++seq, hash: 'a'.repeat(64) }] } }),
};
const freeOk = (res) => res.status < 300 && res.body?.enabled === true && !/^subscription_/.test(code(res));
/**
 * ⛔ لنگرِ سابقه فقط برای دستگاهِ همین نشست (`device-audit-anchor.ownDevice`، بازبینیِ درهای دستهٔ ۳۲) ⇒ FREE با
 * همان `device_uid` ِ ثبت‌شدهٔ نشستِ هر نفر، نه شناسهٔ ساختگی (پیش‌تر `dev-staff`/`dev-owner` با پرچمِ خاموشِ
 * `sync.device_bind` فقط ثبت می‌شد)
 */
const ownDev = async (u) => (await query('SELECT device_uid FROM devices WHERE user_id=$1 ORDER BY created_at LIMIT 1', [u.user.id])).rows[0].device_uid;

test('SUB-10: هر مسیرِ نوشتنیِ /api/shop دقیقاً یک دسته دارد و دروازهٔ سورس با دسته می‌خواند', () => {
  const all = writes();
  assert.ok(all.length > 60, `فقط ${all.length} مسیرِ نوشتنی`);
  const keys = all.map((r) => r.key);
  const problems = [];
  for (const r of all) {
    const cats = [PAID.test(r.key), JOIN.includes(r.key), STAFF.includes(r.key), FREE_KEYS.includes(r.key)].filter(Boolean).length;
    if (cats !== 1) problems.push(`${r.key} (${r.file}): ${cats} دسته — PAID است یا FREE؟ دلیلش را این‌جا بنویسید`);
    if (PAID.test(r.key) && !paidGate(r)) problems.push(`${r.key}: PAID است ولی در سورس needWrite/requireFeature(کلیدِ پولی) ندارد`);
    if (!PAID.test(r.key) && paidGate(r)) problems.push(`${r.key}: دروازهٔ پولی دارد ولی در PAID نیست`);
  }
  for (const k of [...JOIN, ...STAFF, ...FREE_KEYS]) if (!keys.includes(k)) problems.push(`${k} دیگر در سورس نیست — فهرست را به‌روز کنید`);
  assert.deepEqual(problems, [], problems.join('\n'));
  assert.ok(all.filter((r) => PAID.test(r.key)).length >= 19, 'سفارش، ویترین، بازارچه، پیک و کدِ شاگرد');
});

test('SUB-10: با اشتراکِ فعال هیچ مسیرِ PAID برای صاحب، و هیچ راهِ نوشتنِ داده برای شاگرد، subscription_* نمی‌دهد', async () => {
  const s = await paidShop('فعال');
  const bad = [];
  for (const r of writes().filter((x) => PAID.test(x.key))) {
    const res = await h.api(r.method, url(r), { body: {}, token: s.owner.accessToken });
    if (/^subscription_/.test(code(res)) || res.status >= 500) bad.push(`${r.key} ⇒ ${res.status} ${code(res)}`);
  }
  for (const [k, call] of Object.entries(DATA_WRITES)) {
    const res = await call(s.staff.accessToken, 'dev-staff');
    if (res.status >= 300) bad.push(`شاگرد ${k} ⇒ ${res.status} ${code(res)}`);
  }
  for (const [k, call] of Object.entries(SYNC_FREE)) {
    for (const [who, t, u] of [['شاگرد', s.staff.accessToken, s.staff], ['صاحب', s.owner.accessToken, s.owner]]) {
      const res = await call(t, await ownDev(u));
      if (!freeOk(res)) bad.push(`FREE ${who} ${k} ⇒ ${res.status} ${code(res)} ${JSON.stringify(res.body).slice(0, 120)}`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

for (const [name, apply] of Object.entries(STATES)) {
  test(`SUB-10 / F0-25: اشتراکِ ${name} — هر API پولی بسته، صاحب داده‌اش را می‌نویسد، بقیه دست‌نخورده`, async () => {
    const s = await paidShop(name);
    await apply(s);
    const bad = [];
    const all = writes();
    //  ۱) PAID: صاحبِ دکان هم رد می‌شود
    for (const r of all.filter((x) => PAID.test(x.key))) {
      const res = await h.api(r.method, url(r), { body: {}, token: s.owner.accessToken });
      if (!blocked(res)) bad.push(`PAID ${r.key} ⇒ ${res.status} ${code(res)}`);
    }
    //  ۲) JOIN با کدِ واقعی که پیش از پایانِ اشتراک ساخته شده
    for (const k of JOIN) {
      const newcomer = await h.newUser(`تازه-${name}`);
      const res = await h.post(`/api${k.split(' ')[1]}`, { code: s.spare }, { token: newcomer.accessToken });
      if (!blocked(res)) bad.push(`JOIN ${k} ⇒ ${res.status} ${code(res)}`);
    }
    //  ۳) دادهٔ دکان: شاگرد بسته، صاحب همیشه می‌نویسد (D-05)
    for (const [k, call] of Object.entries(DATA_WRITES)) {
      const st = await call(s.staff.accessToken, 'dev-staff');
      if (!blocked(st)) bad.push(`شاگرد ${k} ⇒ ${st.status} ${code(st)}`);
      const ow = await call(s.owner.accessToken, 'dev-owner');
      if (ow.status >= 300) bad.push(`صاحب ${k} ⇒ ${ow.status} ${code(ow)}`);
    }
    //  ۴) FREE: هرگز پاسخِ اشتراکی، هرگز ۵۰۰ (ساختِ دکانِ تازه آخرِ کار)
    const free = all.filter((x) => FREE_KEYS.includes(x.key)).sort((a, b) => (a.key.startsWith('POST /shop/create') || a.key === 'POST /shop') - (b.key.startsWith('POST /shop/create') || b.key === 'POST /shop'));
    for (const r of free) {
      const res = await h.api(r.method, url(r), { body: {}, token: s.owner.accessToken });
      if (/^subscription_/.test(code(res)) || res.status >= 500) bad.push(`FREE ${r.key} ⇒ ${res.status} ${code(res)}`);
    }
    //  ۵) FREE ِ بیرون از /api/shop: شاگرد هم (برخلافِ دادهٔ دکان) باز است
    for (const [k, call] of Object.entries(SYNC_FREE)) {
      for (const [who, t, u] of [['شاگرد', s.staff.accessToken, s.staff], ['صاحب', s.owner.accessToken, s.owner]]) {
        const res = await call(t, await ownDev(u));
        if (!freeOk(res)) bad.push(`FREE ${who} ${k} ⇒ ${res.status} ${code(res)}`);
      }
    }
    assert.deepEqual(bad, [], bad.join('\n'));
  });
}
