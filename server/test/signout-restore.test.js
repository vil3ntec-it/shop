'use strict';
/**
 * «خروج، حذف یا نصبِ دوباره هیچ داده‌ای را گم نمی‌کند» و «کدِ شاگرد مستقیم به دکانِ کارفرما می‌رود» — سمتِ سرور.
 *
 *   • همهٔ مجموعه‌های دفتر (کالا، موجودی، فروش، قلم، برگشت، مشتری/قرض، پرداخت، مصرف، تأمین‌کننده، خرید، پرداختِ
 *     تأمین‌کننده، جابه‌جاییِ کالا، تاریخچهٔ قیمت، سابقه، دسته‌ها، واحدها، دسته‌های مصرف) پس از push ِ v1 در
 *     Snapshot ِ دستگاهِ تازه هستند: صاحب پس از خروج و ورود (یا نصبِ دوباره) و شاگردِ کدِ تازه.
 *   • کدِ شاگرد: نقشِ شاگرد در دکانِ صاحب؛ کدِ غلط/تمام‌شده/مصرف‌شده/دکانِ پر ⇒ کدِ خطای روشن و **هیچ حسابِ شبحی**.
 * سمتِ برنامه: `desktop/src/test/.../SignOutNoLossDesktopTest.kt`؛ ریشه: `core/sync/AccountSafety.kt`.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const staffCodes = require('../src/lib/staff-codes');

let owner;
let shopId;
let n = 0;
const oid = () => `01JNDL${String(++n).padStart(20, '0')}`;
const listId = (name) => crypto.createHash('sha256').update(name, 'utf8').digest('hex').slice(0, 32);
const app = { headers: { 'X-App': 'shop' } };

/** یک ردیف برای هر مجموعهٔ دفترِ دکان (جز گاوصندوق که پشتِ پرچم است) */
const ROWS = {
  products: ['p-1', { name: 'برنج', salePrice: 120, costPrice: 90, createdAt: 1 }],
  warehouseEntries: ['w-1', { productId: 'p-1', units: 50, createdAt: 1 }],
  sales: ['s-1', { total: 240, finalTotal: 240, createdAt: 1, status: 'done' }],
  saleItems: ['si-1', { saleId: 's-1', productId: 'p-1', quantity: 2, price: 120 }],
  returns: ['r-1', { saleId: 's-1', amount: 120, createdAt: 2 }],
  debtors: ['d-1', { name: 'احمد', createdAt: 1 }],
  transactions: ['t-1', { debtorId: 'd-1', type: 'give', amount: 500, createdAt: 1 }],
  expenses: ['e-1', { amount: 70, category: 'برق', createdAt: 1 }],
  suppliers: ['su-1', { name: 'عمده‌فروش', createdAt: 1 }],
  purchases: ['pu-1', { supplierId: 'su-1', total: 900, createdAt: 1 }],
  supplierPayments: ['sp-1', { supplierId: 'su-1', amount: 300, createdAt: 1 }],
  stockMovements: ['sm-1', { productId: 'p-1', qty: -1, kind: 'damage', createdAt: 1 }],
  priceHistory: ['ph-1', { productId: 'p-1', oldPrice: 100, newPrice: 120, at: 1 }],
  auditLog: ['al-1', { action: 'product.create', at: 1 }],
  productCategories: [listId('لبنیات'), { name: 'لبنیات' }],
  productUnits: [listId('بوجی'), { name: 'بوجی' }],
  expenseCategories: [listId('کرایه'), { name: 'کرایه' }],
};

async function admin() {
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}
const usersCount = async () => Number((await one('SELECT COUNT(*)::int AS n FROM users')).n);
const snapshot = async (token) => (await h.api('GET', '/api/sync/v1/snapshot', { token, ...app })).body;

function assertComplete(snap, who) {
  for (const [table, [id, fields]] of Object.entries(ROWS)) {
    const row = (snap.tables[table] || []).find((r) => r.id === id);
    assert.ok(row, `${who}: ${table}/${id} در Snapshot نیست`);
    for (const [k, v] of Object.entries(fields)) assert.deepEqual(row.data[k], v, `${who}: ${table}.${k}`);
  }
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
  [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  owner = await h.newUser('صاحبِ بی‌گم‌شدن');
  shopId = (await h.post('/api/shop', { name: 'دکانِ بی‌گم‌شدن' }, { token: owner.accessToken })).body.shop.id;
  await h.post('/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, { token: await admin() });

  const ops = Object.entries(ROWS).map(([table, [row_id, fields]]) => ({
    op_id: oid(), ts: Date.now(), table, row_id, type: 'insert', fields,
  }));
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken, ...app, body: { device_id: 'owner-phone', schema_version: 3, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
});
test.after(async () => { await h.stop(); });

test('خروج و ورودِ دوبارهٔ صاحب (یا نصبِ دوباره) — Snapshot ِ دستگاهِ تازه همهٔ مجموعه‌ها را دارد', async () => {
  const out = await h.post('/api/auth/logout', { refreshToken: owner.refreshToken }, { token: owner.accessToken });
  assert.ok(out.status < 300, JSON.stringify(out.body));
  //  نصبِ تازه = شناسهٔ دستگاهِ تازه
  const back = await h.post('/api/auth/login', {
    identifier: owner.email, password: owner.password, device: { deviceId: 'owner-reinstalled', name: 'گوشیِ نو' },
  });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  assert.equal(back.body.shop.id, shopId, 'ورود دکانِ همان حساب را می‌گوید');
  const snap = await snapshot(back.body.accessToken);
  assertComplete(snap, 'صاحب پس از ورودِ دوباره');
  assert.ok(Number(snap.cursor) > 0);
  owner = { ...owner, ...back.body };
});

test('کدِ شاگرد ⇒ همان دکانِ صاحب با نقشِ شاگرد، و Snapshot ِ کامل', async () => {
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  const r = await h.post('/api/auth/staff', { code, name: 'علی', device: { uid: 'staff-phone-1', name: 'گوشیِ علی' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.shop.id, shopId);
  assert.equal(r.body.shop.role, 'staff');
  assert.ok(r.body.user.id && r.body.user.id !== owner.user.id, 'حسابِ خودِ شاگرد، نه صاحب');
  assertComplete(await snapshot(r.body.accessToken), 'شاگرد');
  //  همان دستگاه دوباره ⇒ همان حساب
  const again = await h.post('/api/auth/staff', { code, device: { uid: 'staff-phone-1' } });
  assert.equal(again.body.user.id, r.body.user.id);
});

test('کدِ غلط، تمام‌شده، مصرف‌شده و دکانِ پر ⇒ کدِ خطای روشن و هیچ حسابِ شبحی', async () => {
  let users = await usersCount();
  const bad = await h.post('/api/auth/staff', { code: 'XXXX-XXXX-XXXX', device: { uid: 'ghost-0' } });
  assert.ok(bad.status === 400 || bad.status === 404, JSON.stringify(bad.body));
  assert.equal(bad.body.error.code, 'bad_code');
  assert.equal(await usersCount(), users);

  const soon = await staffCodes.create(shopId, owner.user.id, { role: 'staff', expiresAt: now() + 60_000, maxUses: 1 });
  await query('UPDATE staff_codes SET expires_at=$2 WHERE id=$1', [soon.id, now() - 1000]);
  const expired = await h.post('/api/auth/staff', { code: soon.code, device: { uid: 'ghost-1' } });
  assert.equal(expired.status, 403, JSON.stringify(expired.body));
  assert.equal(expired.body.error.code, 'code_expired');
  assert.equal(await usersCount(), users, 'کدِ تمام‌شده حساب نمی‌سازد');

  const once = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  const first = await h.post('/api/auth/staff', { code: once.code, name: 'رضا', device: { uid: 'once-1' } });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  users = await usersCount();
  const second = await h.post('/api/auth/staff', { code: once.code, device: { uid: 'once-2' } });
  assert.equal(second.status, 403, JSON.stringify(second.body));
  assert.ok(['code_used', 'code_inactive'].includes(second.body.error.code), second.body.error.code);
  assert.equal(await usersCount(), users, 'کدِ مصرف‌شده حساب نمی‌سازد');
  //  همان دستگاهِ پیوسته هنوز وارد می‌شود (کدِ یک‌بار برای عضوِ فعال دوباره خرج نمی‌شود)
  assert.equal((await h.post('/api/auth/staff', { code: once.code, device: { uid: 'once-1' } })).status, 201);

  const open = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  const members = Number((await one(`SELECT COUNT(*)::int AS n FROM shop_members WHERE shop_id=$1 AND status='active'`, [shopId])).n);
  await query('UPDATE shops SET max_members=$2 WHERE id=$1', [shopId, members]);
  const full = await h.post('/api/auth/staff', { code: open, device: { uid: 'ghost-2' } });
  assert.equal(full.status, 403, JSON.stringify(full.body));
  assert.equal(full.body.error.code, 'shop_full');
  assert.equal(await usersCount(), users, 'دکانِ پر حساب نمی‌سازد');
});

test('مسابقه با پیش‌سنجی: پیوستنِ ناموفقِ حسابی که همین لحظه ساخته شد ⇒ حساب و هویتش پاک؛ حسابِ کهنه هرگز', async () => {
  const dead = await staffCodes.create(shopId, owner.user.id, { role: 'staff', maxUses: 1 });
  await query(`UPDATE staff_codes SET status='revoked' WHERE id=$1`, [dead.id]);
  const t = now();
  const fresh = newId('usr');
  await query(`INSERT INTO users (id, name, kind, status, created_at, updated_at) VALUES ($1,'تازه','staff','active',$2,$2)`, [fresh, t]);
  await query(`INSERT INTO user_identities (id, user_id, provider, subject, created_at) VALUES ($1,$2,'staff',$3,$4)`, [newId('idn'), fresh, `${shopId}:race-1`, t]);
  await assert.rejects(staffCodes.redeem(dead.code, fresh, '', { freshAt: t }), (e) => e.code === 'code_inactive');
  assert.equal(await one('SELECT id FROM users WHERE id=$1', [fresh]), null, 'حسابِ شبح ماند');
  assert.equal(await one(`SELECT id FROM user_identities WHERE user_id=$1`, [fresh]), null);
  //  حسابِ کهنه (لحظهٔ دیگر) با همان شکست دست نمی‌خورد
  await assert.rejects(staffCodes.redeem(dead.code, owner.user.id, '', { freshAt: t }));
  assert.ok(await one('SELECT id FROM users WHERE id=$1', [owner.user.id]));
});
