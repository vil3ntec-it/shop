'use strict';
/**
 * CRM-15 — اجرای سرورِ `customers.contact` (شماره و نشانیِ مشتری)، نه فقط پوشاندن در صفحه.
 *
 * نقشِ بی مجوز (پیش‌فرض: انباردار و جمع‌آور؛ یا هر کارمندی که صاحب برایش بسته) از سرور هم شمارهٔ کامل و نشانی نمی‌گیرد:
 *   ۱. درهای REST (`GET /api/debtors`، `/api/debtors/:id`) ⇐ شماره پوشیده با همان قالبِ برنامه (`ContactMask`: جز دو رقمِ آخر «•»)
 *      و نشانی نه؛
 *   ۲. هر سه راهِ همگام‌سازی (راهِ قدیمِ `/api/sync`، v1 و v3): pull، Snapshot، حذف‌شده‌ها و صندوقِ تعارض بی شماره و نشانی
 *      (یا پوشیده در صندوقِ تعارض)؛
 *   ۳. ⛔ push ِ همان نقش (یا PUT ِ REST) هرگز شماره و نشانیِ ردیفِ موجود را عوض یا پاک نمی‌کند — نه با مقدارِ پوشیده، نه با خالی؛
 *      ردیفِ تازه شماره‌ای را که خودِ کارمند نوشته می‌گیرد (پوشیده نه)؛
 *   ۴. شمارهٔ کاملِ سفارش و گفت‌وگو = `orders.contact` **و** `customers.contact`: پیش‌فرضِ صاحب و مدیر همان، فقط بستنِ صاحب اثر دارد.
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');

const PHONE = '0799123456';
const PHONE2 = '+93 700 111 222';
const ADDR = { area: 'کارتهٔ چهار', landmark: 'مسجدِ جامع', gate: 'سبز' };
const MASKED = '••••••••56';
const MASKED2 = '+•• ••• ••• •22';

/** بی هیچ لایه: سه نقشِ قدیم، صندوق‌دار و حسابدار می‌بینند. «X» = همان نقش با لایهٔ کاربرِ صاحب */
const ALLOWED = ['owner', 'manager', 'staff', 'cashier', 'accountant', 'pickerX'];
const DENIED = ['storekeeper', 'picker', 'cashierX', 'managerX'];

let admin = '';
let shopId = '';
const T = {};
let n = 0;
const oid = () => `01JCC${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });

async function member(role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, userId: u.user.id };
}
async function setFlag(key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function personal(who, mode) {
  const r = await h.put(`/api/shop/permissions/members/${T[who].userId}`,
    { perms: { 'customers.contact': { mode } } }, { token: T.owner.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const tok = (who) => ({ token: T[who].token });

/** ردیفِ مشتری از نگاهِ هر کس: با مجوز کامل، بی مجوز بی شماره و نشانی (`masked` ⇒ شمارهٔ پوشیده به جای نبودن) */
function seesContact(who, data, { masked = false } = {}) {
  assert.ok(data, `${who}: ردیف نیامد`);
  if (ALLOWED.includes(who)) {
    assert.equal(data.phone, PHONE, `${who} phone`);
    assert.equal(data.phone2, PHONE2, `${who} phone2`);
    assert.deepEqual(data.deliveryAddress, ADDR, `${who} address`);
    return;
  }
  assert.equal(data.phone, masked ? MASKED : undefined, `${who} phone`);
  assert.equal(data.phone2, masked ? MASKED2 : undefined, `${who} phone2`);
  assert.equal(data.deliveryAddress, undefined, `${who} address`);
  //  هیچ جای پاسخ شمارهٔ کامل نیست
  assert.ok(!JSON.stringify(data).includes('0799123'), `${who}: ${JSON.stringify(data)}`);
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'contact-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'contact-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیرِ پنل');

  const o = await h.newUser('صاحبِ دکانِ شماره‌ها', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ شماره‌ها' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = { ...o, token: o.accessToken, userId: o.user.id };
  for (const role of ['manager', 'staff', 'cashier', 'storekeeper', 'picker', 'accountant']) T[role] = await member(role, `کارمندِ ${role}`);
  T.cashierX = await member('cashier', 'صندوق‌دارِ بسته');
  T.managerX = await member('manager', 'مدیرِ بسته');
  T.pickerX = await member('picker', 'جمع‌آورِ باز');
  //  لایهٔ همین کاربر (صاحب در «کارمندان»)
  await personal('cashierX', 'deny');
  await personal('managerX', 'deny');
  await personal('pickerX', 'allow');
});
test.after(async () => { await h.stop(); });

/* ----------------------------------------------------------------- قالب */

test('قالبِ پوشاندن همان ContactMask ِ برنامه است (جز دو رقمِ آخر؛ رقمِ فارسی هم رقم؛ نشانه‌ها می‌مانند)', () => {
  const c = require('../src/lib/customer-contact');
  assert.equal(c.maskPhone('0799123456'), '••••••••56');
  assert.equal(c.maskPhone('+93 799 123 456'), '+•• ••• ••• •56');
  assert.equal(c.maskPhone('۰۷۹۹۱۲۳۴۵۶'), '••••••••۵۶');
  assert.equal(c.maskPhone('⁦0799123456⁩'), '⁦••••••••56⁩');
  assert.equal(c.maskPhone(''), '');
  assert.equal(c.maskPhone('—'), '—');
  assert.equal(c.maskPhone('12'), '12');
  assert.equal(c.maskPhone('123', 1), '••3');
  //  پمپ و حسابِ بیرونِ دکان هرگز پالایش نمی‌شوند (پاسخِ پمپ بایت‌به‌بایت همان)
  return c.hiddenForSync({ app: 'pump', accountKind: 'station', accountId: 'st-1', role: 'device', userId: '' })
    .then((hidden) => assert.equal(hidden, false));
});

/* ------------------------------------------------------------------ REST */

test('REST: GET /api/debtors و /api/debtors/:id — بی مجوز شماره پوشیده و نشانی نیست؛ با مجوز کامل', async () => {
  const made = await h.post('/api/debtors',
    { id: 'd-rest', data: { name: 'علی', phone: PHONE, phone2: PHONE2, deliveryAddress: ADDR } }, tok('owner'));
  assert.equal(made.status, 201, JSON.stringify(made.body));
  for (const who of [...ALLOWED, ...DENIED]) {
    const row = await h.get('/api/debtors/d-rest', tok(who));
    assert.equal(row.status, 200, `${who}: ${JSON.stringify(row.body)}`);
    assert.equal(row.body.name, 'علی');
    seesContact(who, row.body, { masked: true });
    const list = await h.get('/api/debtors?limit=500', tok(who));
    assert.equal(list.status, 200, who);
    seesContact(who, list.body.items.find((x) => x.id === 'd-rest'), { masked: true });
  }
});

test('REST: PUT ِ همان ردیفِ پوشیده (یا با شمارهٔ خالی) شماره و نشانی را بازنویسی نمی‌کند؛ ردیفِ تازه شمارهٔ نوشته را می‌گیرد', async () => {
  const made = await h.post('/api/debtors',
    { id: 'd-put', data: { name: 'رضا', phone: PHONE, phone2: PHONE2, deliveryAddress: ADDR } }, tok('owner'));
  assert.equal(made.status, 201, JSON.stringify(made.body));
  //  کارمندِ بسته همان چیزی را که دیده برمی‌گرداند — شمارهٔ پوشیده و بی نشانی
  const seen = (await h.get('/api/debtors/d-put', tok('cashierX'))).body;
  assert.equal(seen.phone, MASKED);
  const put = await h.put('/api/debtors/d-put',
    { data: { name: 'رضای تازه', phone: seen.phone, phone2: seen.phone2, notes: 'دیدار' }, updatedAt: Date.now() + 1000 }, tok('cashierX'));
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const wipe = await h.put('/api/debtors/d-put',
    { data: { name: 'رضای تازه', phone: '', phone2: null, deliveryAddress: {}, notes: 'دیدار' }, updatedAt: Date.now() + 2000 }, tok('cashierX'));
  assert.equal(wipe.status, 200, JSON.stringify(wipe.body));
  const back = (await h.get('/api/debtors/d-put', tok('owner'))).body;
  assert.equal(back.name, 'رضای تازه', 'بقیهٔ ویرایش می‌نشیند');
  assert.equal(back.notes, 'دیدار');
  seesContact('owner', back);
  //  ردیفِ تازه: شماره‌ای که خودِ کارمند نوشته می‌ماند؛ مقدارِ پوشیده هرگز
  assert.equal((await h.post('/api/debtors', { id: 'd-new1', data: { name: 'تازه', phone: '0700000011' } }, tok('cashierX'))).status, 201);
  assert.equal((await h.post('/api/debtors', { id: 'd-new2', data: { name: 'پوشیده', phone: MASKED } }, tok('cashierX'))).status, 201);
  assert.equal((await h.get('/api/debtors/d-new1', tok('owner'))).body.phone, '0700000011');
  assert.equal((await h.get('/api/debtors/d-new2', tok('owner'))).body.phone, undefined);
  //  و صاحب (با مجوز) همان امروز: شماره را عوض و پاک می‌کند
  assert.equal((await h.put('/api/debtors/d-new1', { data: { name: 'تازه', phone: '0700000012' }, updatedAt: Date.now() + 3000 }, tok('owner'))).status, 200);
  assert.equal((await h.get('/api/debtors/d-new1', tok('owner'))).body.phone, '0700000012');
});

/* ------------------------------------------------------- راهِ قدیم /api/sync */

test('راهِ قدیم /api/sync: pull بی شماره و نشانی برای نقشِ بی مجوز؛ push ِ ردیفِ کامل آن‌ها را نگه می‌دارد', async () => {
  for (const who of ['owner', 'manager', 'storekeeper', 'picker', 'cashierX', 'pickerX']) {
    const r = await h.get('/api/sync?since=0&limit=2000', tok(who));
    assert.equal(r.status, 200, `${who}: ${JSON.stringify(r.body)}`);
    const ch = r.body.changes.find((c) => c.collection === 'debtors' && c.id === 'd-rest');
    assert.equal(ch.data.name, 'علی');
    seesContact(who, ch.data);
  }
  const push = await h.post('/api/sync', { changes: [
    { collection: 'debtors', id: 'd-rest', updatedAt: Date.now() + 5000, data: { name: 'علی', notes: 'از راهِ قدیم' } },
  ] }, tok('cashierX'));
  assert.equal(push.status, 200, JSON.stringify(push.body));
  assert.equal(push.body.applied, 1);
  const back = (await h.get('/api/debtors/d-rest', tok('owner'))).body;
  assert.equal(back.notes, 'از راهِ قدیم');
  seesContact('owner', back);
});

/* ------------------------------------------------------------------- v1 */

const v1push = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].token, body: { device_id: device, schema_version: 1, ops },
});
const v1op = (rowId, fields, type = 'insert') => ({ op_id: oid(), table: 'debtors', row_id: rowId, type, ts: Date.now(), fields });
const v1snap = async (who) => (await h.get('/api/sync/v1/snapshot', tok(who))).body.tables.debtors || [];

test('v1: pull، Snapshot و حذف‌شده‌ها بی شماره و نشانی؛ push ِ نقشِ بی مجوز آن‌ها را عوض یا پاک نمی‌کند', async () => {
  const full = { name: 'حسن', phone: PHONE, phone2: PHONE2, deliveryAddress: ADDR };
  const seed = await v1push('owner', 'v1-owner', [v1op('d-v1', full), v1op('d-v1del', full), v1op('d-v1del', null, 'delete')]);
  assert.deepEqual(seed.body.results.map((x) => x.status), ['applied', 'applied', 'applied'], JSON.stringify(seed.body));

  for (const who of ['owner', 'manager', 'storekeeper', 'picker', 'cashierX', 'pickerX']) {
    const pull = await h.get(`/api/sync/v1/pull?device_id=v1-${who}-reader&since=0&limit=2000`, tok(who));
    assert.equal(pull.status, 200, `${who}: ${JSON.stringify(pull.body)}`);
    const ins = pull.body.ops.find((o) => o.row_id === 'd-v1' && o.type === 'insert');
    assert.equal(ins.fields.name, 'حسن');
    seesContact(who, ins.fields);
    seesContact(who, (await v1snap(who)).find((r) => r.id === 'd-v1').data);
    const del = await h.get('/api/sync/v1/deleted', tok(who));
    assert.equal(del.status, 200, who);
    seesContact(who, del.body.rows.find((r) => r.row_id === 'd-v1del').data);
  }

  //  ⛔ برگرداندنِ خالی/پوشیده از دستگاهِ کارمندِ بسته
  const wipe = await v1push('cashierX', 'v1-cx', [v1op('d-v1', { name: 'حسنِ تازه', phone: '', deliveryAddress: {} }, 'update')]);
  assert.equal(wipe.status, 200, JSON.stringify(wipe.body));
  assert.equal(wipe.body.results[0].status, 'applied');
  assert.deepEqual(wipe.body.results[0].ignored, ['phone', 'deliveryAddress']);
  const masked = await v1push('cashierX', 'v1-cx', [v1op('d-v1', { phone: MASKED, phone2: MASKED2 }, 'update')]);
  assert.equal(masked.body.results[0].status, 'applied', JSON.stringify(masked.body));
  assert.deepEqual(masked.body.results[0].ignored, ['phone', 'phone2']);
  //  ردیفِ تازه: شمارهٔ نوشته می‌ماند، مقدارِ پوشیده نه
  const fresh = await v1push('cashierX', 'v1-cx', [
    v1op('d-v1new', { name: 'تازه', phone: '0700000022' }), v1op('d-v1new2', { name: 'پوشیده', phone: MASKED }),
  ]);
  assert.deepEqual(fresh.body.results.map((x) => x.status), ['applied', 'applied'], JSON.stringify(fresh.body));

  const rows = await v1snap('owner');
  const d = rows.find((r) => r.id === 'd-v1').data;
  assert.equal(d.name, 'حسنِ تازه', 'بقیهٔ ویرایش می‌نشیند');
  seesContact('owner', d);
  assert.equal(rows.find((r) => r.id === 'd-v1new').data.phone, '0700000022');
  assert.equal(rows.find((r) => r.id === 'd-v1new2').data.phone, undefined);
});

test('v1: صندوقِ تعارضِ شماره برای نقشِ بی مجوز پوشیده است', async () => {
  await v1push('owner', 'v1-c1', [v1op('d-v1c', { name: 'تعارض', phone: PHONE })]);
  //  دستگاهِ دیگرِ صاحب که هنوز pull نکرده شماره را عوض می‌کند ⇒ تعارض (بازنده = شمارهٔ پیشین)
  const r = await v1push('owner', 'v1-c2', [v1op('d-v1c', { phone: '0711111199' }, 'update')]);
  assert.equal(r.body.results[0].conflicts, 1, JSON.stringify(r.body));
  for (const who of ['owner', 'manager', 'storekeeper', 'cashierX']) {
    const list = await h.get('/api/sync/v1/conflicts?all=1', tok(who));
    assert.equal(list.status, 200, who);
    const cf = list.body.conflicts.find((c) => c.row_id === 'd-v1c' && c.field === 'phone');
    assert.ok(cf, `${who}: ${JSON.stringify(list.body)}`);
    const open = ALLOWED.includes(who);
    assert.equal(cf.loser_value, open ? PHONE : MASKED, who);
    assert.equal(cf.winner_value, open ? '0711111199' : '••••••••99', who);
  }
});

/* ------------------------------------------------------------------- v3 */

const v3push = (who, device, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].token, body: { device_id: device, protocol: 3, ops },
});
const v3op = (node, rowId, fields, kind = 'insert') => ({ op_id: oid(), hlc: H(node), table: 'debtors', row_id: rowId, kind, fields });
const v3snap = async (who) => {
  const r = await h.get('/api/sync/v3/snapshot', tok(who));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.tables.debtors || [];
};

test('v3: pull و Snapshot بی شماره و نشانی؛ push ِ نقشِ بی مجوز هرگز بازنویسی نمی‌کند (پلِ v1 دست نمی‌خورد)', async () => {
  const seed = await v3push('owner', 'v3-owner', [v3op('o', 'd-v3', { name: 'کریم', phone: PHONE, phone2: PHONE2, deliveryAddress: ADDR })]);
  assert.equal(seed.status, 200, JSON.stringify(seed.body));
  assert.equal(seed.body.results[0].status, 'applied', JSON.stringify(seed.body));

  for (const who of ['owner', 'manager', 'storekeeper', 'picker', 'cashierX', 'pickerX']) {
    const pull = await h.get(`/api/sync/v3/pull?device_id=v3-${who}-reader&since=0&limit=2000&max_bytes=262144`, tok(who));
    assert.equal(pull.status, 200, `${who}: ${JSON.stringify(pull.body)}`);
    const ins = pull.body.ops.find((o) => o.row_id === 'd-v3' && o.kind === 'insert');
    assert.ok(ins, `${who}: ${JSON.stringify(pull.body.ops.map((o) => o.row_id))}`);
    assert.equal(ins.effect.set.name, 'کریم');
    seesContact(who, ins.effect.set);
    const rows = await v3snap(who);
    seesContact(who, rows.find((r) => r.id === 'd-v3').data);
    //  ردیفِ v1 که با pull ِ همین کارمند به v3 آمد هم پالایش می‌شود …
    seesContact(who, rows.find((r) => r.id === 'd-v1').data);
  }
  //  … ولی خودِ ردیف در v3 دست نخورد (پل زیرِ بافتِ کارمندِ بسته اجرا شد)
  assert.equal((await v3snap('owner')).find((r) => r.id === 'd-v1').data.phone, PHONE);

  const wipe = await v3push('cashierX', 'v3-cx', [v3op('cx', 'd-v3', { name: 'کریمِ تازه', phone: '', deliveryAddress: null }, 'update')]);
  assert.equal(wipe.status, 200, JSON.stringify(wipe.body));
  assert.equal(wipe.body.results[0].status, 'applied', JSON.stringify(wipe.body));
  assert.deepEqual(wipe.body.results[0].ignored, ['phone', 'deliveryAddress']);
  const masked = await v3push('cashierX', 'v3-cx', [v3op('cx', 'd-v3', { phone: MASKED, phone2: MASKED2 }, 'update')]);
  assert.equal(masked.body.results[0].status, 'applied', JSON.stringify(masked.body));
  assert.deepEqual(masked.body.results[0].ignored, ['phone', 'phone2']);
  const fresh = await v3push('cashierX', 'v3-cx', [
    v3op('cx', 'd-v3new', { name: 'تازه', phone: '0700000033' }), v3op('cx', 'd-v3new2', { name: 'پوشیده', phone: MASKED }),
  ]);
  assert.deepEqual(fresh.body.results.map((x) => x.status), ['applied', 'applied'], JSON.stringify(fresh.body));

  const rows = await v3snap('owner');
  const d = rows.find((r) => r.id === 'd-v3').data;
  assert.equal(d.name, 'کریمِ تازه', 'بقیهٔ ویرایش می‌نشیند');
  seesContact('owner', d);
  assert.equal(rows.find((r) => r.id === 'd-v3new').data.phone, '0700000033');
  assert.equal(rows.find((r) => r.id === 'd-v3new2').data.phone, undefined);
  //  دستگاهِ v1 ِ صاحب هم همان را می‌بیند
  seesContact('owner', (await v1snap('owner')).find((r) => r.id === 'd-v1').data);
});

test('v3: صندوقِ تعارض (lww ِ شماره و «مشتریِ تکراری») برای نقشِ بی مجوز پوشیده است', async () => {
  await v3push('owner', 'v3-c1', [v3op('c1', 'd-v3c', { name: 'تعارض', phone: PHONE })]);
  const r = await v3push('owner', 'v3-c2', [v3op('c2', 'd-v3c', { phone: '0711111188' }, 'update')]);
  assert.equal(r.body.results[0].conflicts, 1, JSON.stringify(r.body));
  await v3push('owner', 'v3-c1', [v3op('c1', 'd-v3d', { name: 'هم‌شماره', phone: '0711111188' })]);
  for (const who of ['owner', 'manager', 'storekeeper', 'cashierX']) {
    const list = await h.get('/api/sync/v3/conflicts?status=all&limit=500', tok(who));
    assert.equal(list.status, 200, who);
    const open = ALLOWED.includes(who);
    const cf = list.body.conflicts.find((c) => c.row_id === 'd-v3c' && c.field === 'phone');
    assert.ok(cf, `${who}: ${JSON.stringify(list.body.conflicts.map((c) => [c.kind, c.row_id, c.field]))}`);
    assert.equal(cf.loser_value, open ? PHONE : MASKED, who);
    assert.equal(cf.winner_value, open ? '0711111188' : '••••••••88', who);
    const dup = list.body.conflicts.find((c) => c.kind === 'duplicate_customer' && (c.detail.rows || []).includes('d-v3d'));
    assert.ok(dup, who);
    assert.equal(dup.detail.phone, open ? '711111188' : '•••••••88', who);
  }
});

/* ------------------------------------------------- نامِ فیلدِ ناامن (#914) */

test('⛔ نامِ فیلدِ ناامن (__proto__، constructor) از اصلِ بی مجوز رد می‌شود و دادهٔ ردیف دست نمی‌خورد — v1، v3، راهِ قدیم، REST', async () => {
  //  کلیدِ «خودی» ِ __proto__ فقط با JSON.parse ساخته می‌شود (در شیءِ نوشته‌شده نمونهٔ اولیه را عوض می‌کند)
  const evil = (extra) => JSON.parse(`{"name":"دستکاری","phone":"","polluted":"no",${extra}}`);
  const PROTO = '"__proto__":{"isAdmin":true,"phone":"0700000099"}';
  const CTOR = '"constructor":{"prototype":{"isAdmin":true}}';

  //  v1: هر دو op رد، ردیف همان
  const v1 = await v1push('cashierX', 'v1-cx', [
    v1op('d-v1', evil(PROTO), 'update'), v1op('d-v1', evil(CTOR), 'update'), v1op('d-v1evil', evil(PROTO)),
  ]);
  assert.equal(v1.status, 200, JSON.stringify(v1.body));
  assert.deepEqual(v1.body.results.map((r) => [r.status, r.reason]),
    [['rejected', 'bad_field_name'], ['rejected', 'bad_field_name'], ['rejected', 'bad_field_name']], JSON.stringify(v1.body));
  const rows1 = await v1snap('owner');
  const d1 = rows1.find((r) => r.id === 'd-v1').data;
  assert.equal(d1.name, 'حسنِ تازه');
  assert.equal(d1.polluted, undefined);
  assert.equal(d1.isAdmin, undefined);
  seesContact('owner', d1);
  assert.equal(rows1.find((r) => r.id === 'd-v1evil'), undefined);

  //  v3: همان
  for (const extra of [PROTO, CTOR]) {
    const r = await v3push('cashierX', 'v3-cx', [v3op('cx', 'd-v3', evil(extra), 'update')]);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.results[0].status, 'rejected', JSON.stringify(r.body));
    assert.equal(r.body.results[0].reason, 'bad_field_name');
  }
  const d3 = (await v3snap('owner')).find((x) => x.id === 'd-v3').data;
  assert.equal(d3.name, 'کریمِ تازه');
  assert.equal(d3.polluted, undefined);
  seesContact('owner', d3);

  //  راهِ قدیم و REST: ردیف کنار می‌رود (`bad_field_name`)
  const legacy = await h.post('/api/sync', { changes: [
    { collection: 'debtors', id: 'd-rest', updatedAt: Date.now() + 9000, data: evil(PROTO) },
  ] }, tok('cashierX'));
  assert.equal(legacy.status, 200, JSON.stringify(legacy.body));
  assert.equal(legacy.body.applied, 0);
  assert.deepEqual(legacy.body.conflicts.map((c) => c.reason), ['bad_field_name']);
  const put = await h.put('/api/debtors/d-put', { data: evil(CTOR), updatedAt: Date.now() + 9000 }, tok('cashierX'));
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.deepEqual(put.body.conflicts.map((c) => c.reason), ['bad_field_name']);
  for (const id of ['d-rest', 'd-put']) {
    const back = (await h.get(`/api/debtors/${id}`, tok('owner'))).body;
    assert.notEqual(back.name, 'دستکاری', id);
    assert.equal(back.polluted, undefined, id);
    assert.equal(back.isAdmin, undefined, id);
    seesContact('owner', back);
  }
  //  و هیچ چیزی به نمونهٔ اولیهٔ اشیا نرسید
  assert.equal({}.isAdmin, undefined);
});

/* ------------------------------------------------------- سفارش و گفت‌وگو */

test('سفارش و گفت‌وگو: شمارهٔ کامل = orders.contact و customers.contact؛ پیش‌فرضِ صاحب و مدیر همان', async () => {
  await setFlag('shop.orders', true);
  await setFlag('shop.chat', true);
  const made = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod',
    customer: { name: 'احمد', phone: '0791234567' }, lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 5000 }],
  }, { token: T.owner.token, headers: { 'Idempotency-Key': 'crm15-order-1' } });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.order.id;
  assert.equal(made.body.order.customer.phone, '+93791234567');

  for (const who of ['owner', 'manager', 'managerX', 'staff']) {
    const o = (await h.get(`/api/shop/orders/${id}`, tok(who))).body.order;
    const full = who === 'owner' || who === 'manager';
    assert.equal(o.customer.phone, full ? '+93791234567' : undefined, who);
    assert.equal(o.canContact, full, who);
    assert.match(o.customer.phoneMasked, /^\+937•+567$/, who);
  }

  //  گفت‌وگوی همان سفارش
  const conv = await h.post('/api/shop/chat/conversations', { orderId: id }, tok('managerX'));
  assert.ok([200, 201].includes(conv.status), JSON.stringify(conv.body));
  assert.equal(conv.body.conversation.customer.phone, undefined);
  const cid = conv.body.conversation.id;
  for (const who of ['owner', 'manager', 'managerX']) {
    const c = (await h.get(`/api/shop/chat/conversations/${cid}`, tok(who))).body.conversation;
    const full = who !== 'managerX';
    assert.equal(c.customer.phone, full ? '+93791234567' : undefined, who);
    assert.equal((await h.get('/api/shop/chat/config', tok(who))).body.permissions.contact, full, who);
    //  جست‌وجو با شماره فقط برای کسی که شمارهٔ کامل را می‌بیند
    assert.equal((await h.get('/api/shop/chat/conversations?q=1234567', tok(who))).body.conversations.length, full ? 1 : 0, who);
  }

  //  درهایی که شمارهٔ مشتری می‌خوانند یا با آن جست‌وجو می‌کنند (فهرستِ سیاه/سفید، سفارش‌های یک مشتری)
  await setFlag('shop.order_risk', true);
  assert.equal((await h.get('/api/shop/orders/risk/lists', tok('manager'))).status, 200);
  const risk = await h.get('/api/shop/orders/risk/lists', tok('managerX'));
  assert.equal(risk.status, 403, JSON.stringify(risk.body));
  assert.equal(risk.body.error.code, 'permission_denied');
  assert.equal((await h.get('/api/shop/orders/customer?phone=0791234567', tok('manager'))).status, 200);
  assert.equal((await h.get('/api/shop/orders/customer?phone=0791234567', tok('managerX'))).status, 403);
});

test('CRM-09: پینِ نشانیِ تحویل (`deliveryPin`، فیلدِ جدای قرض‌دار) هم تماس است — بی مجوز در REST و v1 نیست و push ِ نقشِ بسته عوضش نمی‌کند', async () => {
  const PIN = { lat: 34.528123, lng: 69.172346 };
  const full = { name: 'نسیم', phone: PHONE, phone2: PHONE2, deliveryAddress: ADDR, deliveryPin: PIN };
  assert.equal((await h.post('/api/debtors', { id: 'd-pin-rest', data: full }, tok('owner'))).status, 201);
  const seed = await v1push('owner', 'v1-owner', [v1op('d-pin', full)]);
  assert.deepEqual(seed.body.results.map((x) => x.status), ['applied'], JSON.stringify(seed.body));
  for (const who of [...ALLOWED, ...DENIED]) {
    const row = (await h.get('/api/debtors/d-pin-rest', tok(who))).body;
    const pull = await h.get(`/api/sync/v1/pull?device_id=v1-pin-${who}&since=0&limit=2000`, tok(who));
    const ins = pull.body.ops.find((o) => o.row_id === 'd-pin' && o.type === 'insert');
    const snap = (await v1snap(who)).find((r) => r.id === 'd-pin').data;
    for (const [door, data] of [['REST', row], ['pull', ins.fields], ['Snapshot', snap]]) {
      if (ALLOWED.includes(who)) assert.deepEqual(data.deliveryPin, PIN, `${who} ${door}`);
      else assert.equal(data.deliveryPin, undefined, `${who} ${door}: ${JSON.stringify(data)}`);
      assert.ok(ALLOWED.includes(who) || !JSON.stringify(data).includes('34.528'), `${who} ${door}: جای خانه`);
    }
  }
  //  ⛔ کارمندِ بسته پین را نه پاک می‌کند نه جابه‌جا
  const moved = await v1push('cashierX', 'v1-cx-pin', [v1op('d-pin', { name: 'نسیمِ تازه', deliveryPin: { lat: 34.6, lng: 69.3 } }, 'update')]);
  assert.equal(moved.body.results[0].status, 'applied', JSON.stringify(moved.body));
  assert.deepEqual(moved.body.results[0].ignored, ['deliveryPin']);
  const wipe = await v1push('cashierX', 'v1-cx-pin', [v1op('d-pin', { deliveryPin: null }, 'update')]);
  assert.equal(wipe.body.results[0].status, 'applied', JSON.stringify(wipe.body));
  const back = (await v1snap('owner')).find((r) => r.id === 'd-pin').data;
  assert.equal(back.name, 'نسیمِ تازه', 'بقیهٔ ویرایش می‌نشیند');
  assert.deepEqual(back.deliveryPin, PIN);
});
