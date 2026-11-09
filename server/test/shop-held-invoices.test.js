'use strict';
/**
 * پشتیبانِ سرورِ فاکتورهای نگه‌داشته — `/api/shop/held-invoices` (مهاجرتِ ۴۲۵؛ POS-12، S09).
 *
 *   • پرچمِ خاموش (پیش‌فرض): `GET` فقط `{enabled:false}`، `PUT`/`DELETE` ۴۰۳ و هیچ ردیفی.
 *   • هر فاکتور جدا با نسخهٔ دستگاه: کهنه‌تر هرگز روی تازه‌تر نمی‌نشیند (۴۰۹ `held_stale` با ردیفِ کنونی)؛ تکرار بی‌خطر.
 *   • دو دستگاه هم‌زمان: آخرین نسخه می‌ماند، هر ترتیبی که برسند؛ `rev` ِ پیاپی.
 *   • سنگِ قبر: فاکتورِ برگشته یا فروخته با نوشتنِ دیررسیده زنده نمی‌شود؛ دستگاهِ تازه فقط زنده‌ها را می‌گیرد.
 *   • مجوز: هر فروشنده همه را می‌بیند؛ دور انداختنِ نگه‌داشتهٔ کسِ دیگر `sales.cancel`؛ بی `data.write` هیچ.
 *   • سقف‌ها و شکل؛ جدایی از دکانِ دیگر؛ rollback ِ مهاجرت.
 *   • «در دستِ دستگاهِ X»: ادعای بازگرداندن، ۴۰۹ با سری و تازه بودن، گرفتن فقط با تأییدِ صریح (سابقه)، دستگاهِ نخست
 *     با دورِ بعد می‌فهمد؛ بازگرداندنِ بی شبکه که دیگری پیش‌تر برش گرداند/فروخت/عوض کرد ⇒ ۴۰۹ `held_conflict`؛
 *     رقابتِ دو ادعای هم‌زمان ⇒ دقیقاً یکی؛ برنامهٔ پیشین (بی base/claim) همان رفتار.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const BASE = '/api/shop/held-invoices';
let admin = '';
let shopId;
let owner;
let manager;
let staff;
let other;
let otherShopId;

const flag = (value, scope) => h.put('/api/admin/pump/live-config', { scope, key: 'shop.held_sync', value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const put = (who, id, version, body, extra = {}) => { resetAll(); return h.put(`${BASE}/${id}`, { version, body, ...extra }, { token: who.accessToken }); };
const del = (who, id, version, reason) => { resetAll(); return h.del(`${BASE}/${id}?version=${version}&reason=${reason}`, { token: who.accessToken }); };
/** «در دستِ دستگاهِ X»: پیش از بازگرداندن (`claim=1`) یا کارِ در صف (`queued`) با نسخه‌ای که دستگاه دیده بود */
const claim = (who, dev, id, version, base, extra = '') => {
  resetAll();
  return h.del(`${BASE}/${id}?version=${version}&reason=resumed&base=${base}&claim=1&device_id=${dev}${extra}`, { token: who.accessToken });
};
const queued = (who, dev, id, version, base, reason = 'resumed') => {
  resetAll();
  return h.del(`${BASE}/${id}?version=${version}&reason=${reason}&base=${base}&device_id=${dev}`, { token: who.accessToken });
};
const rowOf = (id) => one('SELECT version, deleted, reason, device_id, body FROM shop_held_invoices WHERE shop_id=$1 AND id=$2', [shopId, id]);
const get = (who, since = 0) => { resetAll(); return h.get(`${BASE}?since=${since}`, { token: who.accessToken }); };
/** همان شکلِ `HeldInvoice` ِ برنامه */
const inv = (id, extra = {}) => ({
  id, number: 1, label: 'آقای کریمی', heldAt: 1700000000000, device: 'A',
  lines: [{ productId: 'p1', quantity: 2.0, unitPrice: 95.0, specialPrice: 95.0, seller: 'زهرا' }],
  coupons: ['EID5'], ...extra,
});
const count = async (shop = shopId) => (await one('SELECT COUNT(*)::int AS n FROM shop_held_invoices WHERE shop_id=$1', [shop])).n;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'hs-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'hs-admin', password: 'Admin!12345' })).body.token;
  const o = await h.newUser('صاحبِ دکانِ نگه‌داشته', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ نگه‌داشته' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ نگه‌داشته', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const invite = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ نگه‌داشته', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: invite.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
  const b = await h.newUser('صاحبِ دکانِ دیگر', 'shop');
  const bs = await h.post('/api/shop', { name: 'دکانِ دیگرِ نگه‌داشته' }, { token: b.accessToken });
  assert.equal(bs.status, 201);
  otherShopId = bs.body?.shop?.id || bs.body?.id;
  other = await h.signIn(b, 'shop');
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): GET فقط enabled:false، PUT و DELETE ۴۰۳ و هیچ ردیفی', async () => {
  assert.equal((await h.get(BASE)).status, 401);
  assert.deepEqual((await get(owner)).body, { enabled: false });
  const w = await put(owner, 'H1', 10, inv('H1'));
  assert.deepEqual([w.status, w.body.error?.code], [403, 'held_sync_disabled']);
  const d = await del(owner, 'H1', 11, 'resumed');
  assert.deepEqual([d.status, d.body.error?.code], [403, 'held_sync_disabled']);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_held_invoices')).n, 0);
});

test('نگه‌داشتن، بازگشتِ همان بدنه، تکرارِ بی‌خطر و نوشتنِ کهنه که هرگز روی تازه نمی‌نشیند', async () => {
  await flag(true, shopId);
  const a = await put(owner, 'H1', 10, inv('H1'), { device_id: 'dev-a' });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.applied, true);
  assert.equal(a.body.item.version, 10);
  //  همان بدنه برمی‌گردد (ترتیبِ کلیدِ jsonb مهم نیست)
  const g = await get(staff);
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.equal(g.body.enabled, true);
  const item = g.body.items.find((x) => x.id === 'H1');
  assert.deepEqual(item.body, inv('H1'));
  assert.equal(item.mine, false, 'شاگرد نگه‌داشتهٔ صاحب را می‌بیند ولی «مالِ من» نیست');
  assert.equal(item.device_id, 'dev-a');
  assert.equal(g.body.permissions.cancel, false);
  assert.equal((await get(owner)).body.items.find((x) => x.id === 'H1').mine, true);
  assert.equal((await get(owner)).body.permissions.cancel, true);
  //  تکرارِ همان درخواست ⇒ بی تغییر
  const again = await put(owner, 'H1', 10, inv('H1'), { device_id: 'dev-a' });
  assert.deepEqual([again.status, again.body.applied], [200, false]);
  //  ویرایش (نام) با نسخهٔ تازه‌تر
  const ren = await put(staff, 'H1', 12, inv('H1', { label: 'کریمی — منتظر' }));
  assert.deepEqual([ren.status, ren.body.applied], [200, true]);
  //  کهنه‌تر (یا برابر با بدنهٔ دیگر) ⇒ ۴۰۹ با ردیفِ کنونی
  for (const v of [11, 12]) {
    const s = await put(owner, 'H1', v, inv('H1', { label: 'کهنه' }));
    assert.deepEqual([s.status, s.body.error?.code], [409, 'held_stale'], JSON.stringify(s.body));
    assert.equal(s.body.error.current.version, 12);
    assert.equal(s.body.error.current.body.label, 'کریمی — منتظر');
  }
  const row = await one("SELECT version, held_by, created_by, body FROM shop_held_invoices WHERE shop_id=$1 AND id='H1'", [shopId]);
  assert.equal(Number(row.version), 12);
  assert.equal(row.body.label, 'کریمی — منتظر');
  //  ویرایشِ شاگرد «نگه‌دارنده» را عوض نمی‌کند
  assert.equal(row.held_by, row.created_by);
});

test('دو دستگاه هم‌زمان: آخرین نسخه می‌ماند، هر ترتیبی که برسند؛ rev ِ پیاپی و بی گم شدن', async () => {
  for (let round = 0; round < 4; round++) {
    const id = `C${round}`;
    const base = 1000 + round * 10;
    resetAll();
    const order = round % 2 === 0 ? [base + 1, base + 2] : [base + 2, base + 1];
    const out = await Promise.all(order.map((v, i) => h.put(`${BASE}/${id}`, { version: v, body: inv(id, { label: `v${v}` }) },
      { token: (i === 0 ? owner : staff).accessToken })));
    assert.ok(out.every((r) => [200, 409].includes(r.status)), JSON.stringify(out.map((r) => r.body)));
    const row = await one('SELECT version, body FROM shop_held_invoices WHERE shop_id=$1 AND id=$2', [shopId, id]);
    assert.equal(Number(row.version), base + 2, `دور ${round}`);
    assert.equal(row.body.label, `v${base + 2}`);
  }
  //  نگه‌داشته‌های جدا از دو دستگاه همه می‌نشینند، هر کدام با rev ِ خودش
  resetAll();
  const many = await Promise.all(Array.from({ length: 8 }, (_, i) => h.put(`${BASE}/P${i}`, { version: 5, body: inv(`P${i}`) },
    { token: (i % 2 ? owner : staff).accessToken })));
  assert.ok(many.every((r) => r.status === 200), JSON.stringify(many.map((r) => r.body)));
  const revs = many.map((r) => r.body.item.rev);
  assert.equal(new Set(revs).size, 8);
  //  دستگاهِ سوم: از cursor ِ پیشین فقط تازه‌ها
  const all = await get(manager);
  const since = Math.min(...revs) - 1;
  const fresh = await get(manager, since);
  assert.ok(fresh.body.items.length >= 8 && fresh.body.items.length <= all.body.items.length);
  assert.equal(fresh.body.rev, all.body.rev);
});

test('برداشتن: سنگِ قبر، نوشتنِ دیررسیده زنده‌اش نمی‌کند، نگه‌داشتنِ تازه‌تر زنده می‌کند؛ دستگاهِ تازه فقط زنده‌ها', async () => {
  assert.equal((await put(owner, 'R1', 100, inv('R1'))).status, 200);
  const cursor = (await get(owner)).body.rev;
  //  شاگرد روی دستگاهِ دیگر برش می‌گرداند (فروشندهٔ دیگر، بی `sales.cancel`)
  const r = await del(staff, 'R1', 101, 'resumed');
  assert.deepEqual([r.status, r.body.deleted], [200, true], JSON.stringify(r.body));
  //  تکرار ⇒ بی تغییر
  const r2 = await del(staff, 'R1', 101, 'resumed');
  assert.deepEqual([r2.status, r2.body.deleted], [200, false]);
  //  نوشتنِ دیررسیدهٔ دستگاهِ نخست (نسخهٔ پیش از برداشتن) ⇒ ۴۰۹، سنگِ قبر می‌ماند
  const late = await put(owner, 'R1', 100, inv('R1', { label: 'دیر' }));
  assert.deepEqual([late.status, late.body.error?.code], [409, 'held_stale']);
  assert.equal(late.body.error.current.deleted, true);
  assert.equal(late.body.error.current.reason, 'resumed');
  //  دستگاهِ تازه (since=0) سنگِ قبر را نمی‌گیرد؛ دستگاهِ آشنا (cursor) می‌گیرد
  assert.equal((await get(owner)).body.items.some((x) => x.id === 'R1'), false);
  assert.equal((await get(owner)).body.gone.length, 0);
  const g = await get(owner, cursor);
  assert.deepEqual(g.body.gone.map((x) => [x.id, x.reason, x.version]), [['R1', 'resumed', 101]]);
  //  برداشتنِ کهنه‌تر از ردیفِ زنده ⇒ ۴۰۹ (کسی پس از آن عوضش کرد)
  assert.equal((await put(owner, 'R2', 200, inv('R2'))).status, 200);
  assert.equal((await put(staff, 'R2', 205, inv('R2', { label: 'تازه' }))).status, 200);
  const old = await del(owner, 'R2', 203, 'sold');
  assert.deepEqual([old.status, old.body.error?.code], [409, 'held_stale']);
  assert.equal(old.body.error.current.body.label, 'تازه');
  //  نگه‌داشتنِ دوبارهٔ همان فاکتور (نسخهٔ تازه‌تر) پس از برداشتن ⇒ زنده، با نگه‌دارندهٔ تازه
  const back = await put(staff, 'R1', 110, inv('R1', { label: 'دوباره' }));
  assert.deepEqual([back.status, back.body.applied], [200, true]);
  const row = await one("SELECT deleted, held_by, created_by FROM shop_held_invoices WHERE shop_id=$1 AND id='R1'", [shopId]);
  assert.equal(row.deleted, false);
  assert.notEqual(row.held_by, row.created_by, 'نگه‌دارنده همان کسی است که دوباره نگهش داشت');
  assert.equal((await get(staff)).body.items.find((x) => x.id === 'R1').mine, true);
});

test('مجوز: دور انداختنِ نگه‌داشتهٔ کسِ دیگر فقط با sales.cancel؛ مالِ خود همیشه؛ بازگرداندن برای هر فروشنده', async () => {
  assert.equal((await put(owner, 'D1', 10, inv('D1'))).status, 200);
  const no = await del(staff, 'D1', 11, 'deleted');
  assert.deepEqual([no.status, no.body.error?.code], [403, 'held_not_yours']);
  assert.equal((await one("SELECT deleted FROM shop_held_invoices WHERE shop_id=$1 AND id='D1'", [shopId])).deleted, false);
  //  مدیر (لغوِ فاکتور) دور می‌اندازد و در سابقه می‌ماند
  const yes = await del(manager, 'D1', 11, 'deleted');
  assert.deepEqual([yes.status, yes.body.deleted], [200, true]);
  const log = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='held.discard' AND target_id='D1'", [shopId]);
  assert.ok(log, 'سابقهٔ دور انداختن');
  //  شاگرد مالِ خودش را دور می‌اندازد
  assert.equal((await put(staff, 'D2', 10, inv('D2'))).status, 200);
  assert.deepEqual([(await del(staff, 'D2', 11, 'deleted')).status], [200]);
  //  دلیلِ نادرست یا نسخهٔ نادرست ⇒ ۴۰۰
  for (const [v, why] of [[11, 'gone'], [0, 'resumed'], ['abc', 'sold']]) {
    const b = await del(owner, 'D3', v, why);
    assert.deepEqual([b.status, b.body.error?.code], [400, 'bad_held'], `${v} ${why}`);
  }
});

test('بی data.write (حسابدار) هیچ؛ دکانِ دیگر نه می‌بیند نه می‌نویسد نه برمی‌دارد', async () => {
  const invite = await h.post('/api/shop/invite', { role: 'accountant' }, { token: owner.accessToken });
  if ([200, 201].includes(invite.status)) {
    const acc = await h.newUser('حسابدارِ نگه‌داشته', 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: invite.body.code }, { token: acc.accessToken })).status));
    const a = await h.signIn(acc, 'shop');
    assert.equal((await get(a)).status, 403);
    assert.equal((await put(a, 'Z1', 1, inv('Z1'))).status, 403);
  }
  await flag(true, otherShopId);
  const before = await one("SELECT version, body FROM shop_held_invoices WHERE shop_id=$1 AND id='H1'", [shopId]);
  const g = await get(other);
  assert.equal(g.status, 200);
  assert.equal(g.body.enabled, true);
  assert.equal(JSON.stringify(g.body).includes('کریمی'), false, 'هیچ نشانی از دکانِ «الف»');
  //  همان شناسه در فضای خودِ «ب» — ردیفِ «الف» دست نمی‌خورد
  const w = await put(other, 'H1', 99999, inv('H1', { label: 'ب' }));
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal((await del(other, 'H1', 100000, 'deleted')).status, 200);
  const after = await one("SELECT version, body FROM shop_held_invoices WHERE shop_id=$1 AND id='H1'", [shopId]);
  assert.deepEqual(after, before);
  assert.equal(await count(otherShopId), 1, 'سنگِ قبرِ همان شناسه فقط در «ب»');
});

test('در دستِ دستگاهِ X: ادعا، ۴۰۹ با سری و تازه بودن، گرفتن فقط با force و در سابقه، و دستگاهِ نخست می‌فهمد', async () => {
  const series = async (who, dev) => (await h.post('/api/shop/series', { deviceId: dev }, { token: who.accessToken })).body.series;
  const sa = await series(owner, 'dev-a');
  const sb = await series(staff, 'dev-b');
  assert.ok(sa && sb && sa !== sb, `${sa} ${sb}`);
  assert.equal((await put(owner, 'K1', 10, inv('K1'), { device_id: 'dev-a' })).status, 200);
  const cursor = (await get(owner)).body.rev;
  //  پرچمِ خاموش ⇒ همان ۴۰۳ و هیچ ادعایی
  await flag(false, shopId);
  assert.deepEqual([(await claim(owner, 'dev-a', 'K1', 11, 10)).body.error?.code], ['held_sync_disabled']);
  await flag(true, shopId);
  assert.equal((await rowOf('K1')).deleted, false);

  //  «الف» برش می‌گرداند ⇒ سنگِ قبرِ resumed به نامِ همان دستگاه = ادعا
  const a = await claim(owner, 'dev-a', 'K1', 11, 10);
  assert.deepEqual([a.status, a.body.deleted, a.body.claim?.granted], [200, true, true], JSON.stringify(a.body));
  assert.ok(a.body.claim.until > Date.now());
  assert.deepEqual([(await rowOf('K1')).device_id, (await rowOf('K1')).reason], ['dev-a', 'resumed']);
  //  همان دستگاه دوباره ⇒ همان ادعا، بی تغییرِ نسخه
  const a2 = await claim(owner, 'dev-a', 'K1', 12, 11);
  assert.deepEqual([a2.status, a2.body.deleted, a2.body.claim?.granted, Number((await rowOf('K1')).version)], [200, false, true, 11]);

  //  «ب» با نسخه‌ای که پیش از آن دیده بود ⇒ ۴۰۹ «در دستِ دستگاهِ الف»، هیچ تغییری
  const b = await claim(staff, 'dev-b', 'K1', 13, 10);
  assert.deepEqual([b.status, b.body.error?.code], [409, 'held_claimed'], JSON.stringify(b.body));
  assert.deepEqual([b.body.error.holder.series, b.body.error.holder.reason, b.body.error.holder.fresh], [sa, 'resumed', true]);
  assert.equal((await rowOf('K1')).device_id, 'dev-a');
  //  تأییدِ صریح ⇒ گرفتن: سنگِ قبر به نامِ «ب» با نسخهٔ بزرگ‌تر، در سابقه
  const f = await claim(staff, 'dev-b', 'K1', 13, 10, '&force=1');
  assert.deepEqual([f.status, f.body.deleted, f.body.claim?.takeover?.series], [200, true, sa], JSON.stringify(f.body));
  assert.deepEqual([(await rowOf('K1')).device_id, Number((await rowOf('K1')).version)], ['dev-b', 13]);
  const log = await one("SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='held.takeover' AND target_id='K1'", [shopId]);
  assert.equal(log?.detail?.from_series, sa);
  //  «الف» با دورِ بعد می‌بیند که «ب» گرفتش (سنگِ قبر با دستگاه و سری)
  const gone = (await get(owner, cursor)).body.gone.find((x) => x.id === 'K1');
  assert.deepEqual([gone.device_id, gone.series, gone.reason, gone.version], ['dev-b', sb, 'resumed', 13]);
  //  ادعای کهنه (پس از CLAIM_MS) هنوز می‌پرسد، فقط «تازه» نیست
  const held = require('../src/lib/shop-held-sync');
  await query('UPDATE shop_held_invoices SET updated_at = updated_at - $3 WHERE shop_id=$1 AND id=$2', [shopId, 'K1', held.CLAIM_MS + 1]);
  const late = await claim(owner, 'dev-a', 'K1', 20, 11);
  assert.deepEqual([late.status, late.body.error?.code, late.body.error?.holder?.fresh, late.body.error?.holder?.series], [409, 'held_claimed', false, sb]);

  //  ردیفِ زنده‌ای که پس از نسخهٔ دیده‌شده عوض شد ⇒ ۴۰۹ held_stale با ردیفِ تازه (اول همان را بگیرد)، بعد ادعا
  assert.equal((await put(owner, 'K2', 10, inv('K2'), { device_id: 'dev-a' })).status, 200);
  assert.equal((await put(owner, 'K2', 12, inv('K2', { label: 'تازه' }), { device_id: 'dev-a' })).status, 200);
  const st = await claim(staff, 'dev-b', 'K2', 14, 10);
  assert.deepEqual([st.status, st.body.error?.code, st.body.error?.current?.body?.label], [409, 'held_stale', 'تازه']);
  assert.equal((await rowOf('K2')).deleted, false);
  assert.deepEqual([(await claim(staff, 'dev-b', 'K2', 14, 12)).body.claim?.granted, (await rowOf('K2')).device_id], [true, 'dev-b']);
  //  ادعا فقط با بازگرداندن؛ نسخهٔ پایهٔ نادرست ⇒ ۴۰۰
  resetAll();
  assert.equal((await h.del(`${BASE}/K2?version=30&reason=sold&base=1&claim=1&device_id=dev-a`, { token: owner.accessToken })).status, 400);
  assert.equal((await claim(owner, 'dev-a', 'K2', 30, -1)).status, 400);
  //  برنامهٔ پیشین (بی base و claim) ⇒ همان رفتار: برداشتنِ دوباره بی تغییر
  assert.deepEqual([(await del(staff, 'K1', 40, 'resumed')).status, (await del(staff, 'K1', 40, 'resumed')).body.deleted], [200, false]);
});

test('بازگرداندنِ بی شبکه: کارِ در صف که دستگاهِ دیگری پیش‌تر برش گرداند، فروخت یا عوض کرد ⇒ ۴۰۹ held_conflict و هیچ تغییری', async () => {
  //  برگرداندنِ دستگاهِ «الف» با شبکه، و «ب» همان را بی شبکه باز کرده بود
  assert.equal((await put(owner, 'Q1', 10, inv('Q1'), { device_id: 'dev-a' })).status, 200);
  assert.equal((await claim(owner, 'dev-a', 'Q1', 11, 10)).status, 200);
  const c1 = await queued(staff, 'dev-b', 'Q1', 15, 10);
  assert.deepEqual([c1.status, c1.body.error?.code, c1.body.error?.holder?.reason, c1.body.error?.holder?.device_id], [409, 'held_conflict', 'resumed', 'dev-a']);
  assert.deepEqual([(await rowOf('Q1')).device_id, Number((await rowOf('Q1')).version)], ['dev-a', 11]);
  //  کارِ در صفِ خودِ «الف» پس از ادعا ⇒ بی تغییر
  assert.deepEqual([(await queued(owner, 'dev-a', 'Q1', 12, 11)).status], [200]);
  //  فروخته در «الف» (نگه‌داشته‌ای که در دفترِ «الف» فروش رفت)
  assert.equal((await put(owner, 'Q2', 10, inv('Q2'), { device_id: 'dev-a' })).status, 200);
  assert.equal((await queued(owner, 'dev-a', 'Q2', 11, 10, 'sold')).status, 200);
  const c2 = await queued(staff, 'dev-b', 'Q2', 15, 10);
  assert.deepEqual([c2.status, c2.body.error?.code, c2.body.error?.holder?.reason], [409, 'held_conflict', 'sold']);
  //  عوض‌شده (نام) در «الف» پس از نسخه‌ای که «ب» دید: ردیف زنده می‌ماند، «ب» دوتایی را می‌بیند
  assert.equal((await put(owner, 'Q3', 10, inv('Q3'), { device_id: 'dev-a' })).status, 200);
  assert.equal((await put(owner, 'Q3', 12, inv('Q3', { label: 'منتظر' }), { device_id: 'dev-a' })).status, 200);
  const c3 = await queued(staff, 'dev-b', 'Q3', 15, 10);
  assert.deepEqual([c3.status, c3.body.error?.code, c3.body.error?.holder?.reason], [409, 'held_conflict', 'held']);
  assert.deepEqual([(await rowOf('Q3')).deleted, (await rowOf('Q3')).body.label], [false, 'منتظر']);
  //  «ب» آخرین نسخه را دیده بود ⇒ بازگرداندنِ عادی
  assert.equal((await put(owner, 'Q4', 10, inv('Q4'), { device_id: 'dev-a' })).status, 200);
  const ok = await queued(staff, 'dev-b', 'Q4', 15, 10);
  assert.deepEqual([ok.status, ok.body.deleted, (await rowOf('Q4')).device_id], [200, true, 'dev-b']);
  //  دور انداختن (نه بازگرداندن) هیچ‌گاه «دوتایی» نیست
  assert.equal((await put(owner, 'Q5', 10, inv('Q5'), { device_id: 'dev-a' })).status, 200);
  assert.equal((await claim(owner, 'dev-a', 'Q5', 11, 10)).status, 200);
  assert.deepEqual([(await queued(manager, 'dev-c', 'Q5', 15, 10, 'deleted')).status], [200]);
});

test('رقابت: دو دستگاه هم‌زمان یک نگه‌داشته را ادعا می‌کنند ⇒ دقیقاً یکی می‌گیرد و دیگری ۴۰۹ با نامِ همان', async () => {
  for (let round = 0; round < 6; round++) {
    const id = `RC${round}`;
    assert.equal((await put(owner, id, 10, inv(id), { device_id: 'dev-a' })).status, 200);
    resetAll();
    const who = round % 2 ? [[owner, 'dev-a'], [staff, 'dev-b']] : [[staff, 'dev-b'], [owner, 'dev-a']];
    const out = await Promise.all(who.map(([w, dev]) => h.del(`${BASE}/${id}?version=11&reason=resumed&base=10&claim=1&device_id=${dev}`,
      { token: w.accessToken })));
    const ok = out.filter((r) => r.status === 200);
    const no = out.filter((r) => r.status === 409);
    assert.deepEqual([ok.length, no.length], [1, 1], `دور ${round}: ${JSON.stringify(out.map((r) => r.body))}`);
    assert.equal(no[0].body.error.code, 'held_claimed');
    const row = await rowOf(id);
    assert.deepEqual([row.deleted, row.reason, Number(row.version)], [true, 'resumed', 11]);
    assert.equal(no[0].body.error.holder.device_id, row.device_id, 'بازنده نامِ همان برنده را می‌بیند');
  }
});

test('سقف‌ها و شکل: ردیف، بایت، شمارِ زنده، شناسه و نسخه', async () => {
  const bad = async (id, version, body, code = 'bad_held', status = 400) => {
    const r = await put(owner, id, version, body);
    assert.deepEqual([r.status, r.body.error?.code], [status, code], `${id} ⇒ ${JSON.stringify(r.body).slice(0, 120)}`);
  };
  await bad('X1', 1, inv('X2'));
  await bad('X1', 1, inv('X1', { lines: [] }));
  await bad('X1', 1, inv('X1', { lines: [{ productId: 'p1', quantity: 'دو' }] }));
  await bad('X1', 1, inv('X1', { label: 7 }));
  await bad('X1', 1, inv('X1', { label: 'x'.repeat(600) }));
  await bad('X1', 0, inv('X1'));
  await bad('X1', -5, inv('X1'));
  await bad('X1', 1.5, inv('X1'));
  await bad('X1', 1, [inv('X1')]);
  await bad('a.b', 1, inv('a.b'));
  await bad('X1', 1, inv('X1', { lines: Array.from({ length: 201 }, (_, i) => ({ productId: `p${i}`, quantity: 1 })) }), 'held_too_large');
  await bad('X1', 1, inv('X1', { lines: Array.from({ length: 150 }, (_, i) => ({ productId: `p${i}`, quantity: 1, note: 'ی'.repeat(120) })) }), 'held_too_large');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM shop_held_invoices WHERE shop_id=$1 AND id='X1'", [shopId])).n, 0);
  //  سقفِ زنده‌ها: تا ۶۰؛ شصت‌ویکمی ۴۰۹، ویرایشِ موجود همچنان
  const live = (await one('SELECT COUNT(*)::int AS n FROM shop_held_invoices WHERE shop_id=$1 AND NOT deleted', [shopId])).n;
  for (let i = live; i < 60; i++) assert.equal((await put(owner, `L${i}`, 1, inv(`L${i}`))).status, 200);
  await bad('L60', 1, inv('L60'), 'too_many_held', 409);
  assert.equal((await put(owner, 'L59', 2, inv('L59', { label: 'ویرایش' }))).status, 200);
  assert.ok(await count() >= 60);
});

test('مهاجرتِ ۴۲۵: rollback جدول و ردیفِ خودش را برمی‌دارد؛ دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '425_shop_held_invoices.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async (t) => (await query('SELECT 1 FROM information_schema.tables WHERE table_name=$1', [t])).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has('shop_held_invoices'), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  assert.equal(await has('shop_setting_keys'), 1, 'جدولِ دیگر دست نمی‌خورد');
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has('shop_held_invoices'), 1);
});
