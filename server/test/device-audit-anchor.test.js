'use strict';
/**
 * STF-09 — لنگرِ سرورِ زنجیرهٔ سابقهٔ عملیاتِ دستگاه (`POST`/`GET /api/sync/v1/audit-anchor`، مهاجرتِ ۴۱۰،
 * `lib/device-audit-anchor.js`) و سنجشِ سرور با آن (`lib/device-audit-chain.js`، `verify-chain --device-audit`).
 *
 *   ۱) پرچمِ خاموش ⇒ `{enabled:false}` و هیچ ردیفی؛ ورودیِ بدشکل ۴۰۰.
 *   ۲) سر پذیرفته و نشانده می‌شود؛ همان گزارش دوباره (پاسخِ گم‌شده، تلاشِ دوباره) هیچ نمی‌نویسد.
 *   ۳) حسابِ پمپ ۴۰۳ `shop_only`؛ دکانِ دیگر لنگرِ این دکان را نه می‌بیند نه عوض می‌کند؛ زنجیرهٔ دستگاهِ دیگر ۴۰۰.
 *   ۴) سنجش با لنگر: بریدنِ دُم (همگام‌نشده)، جابه‌جایی، ویرایش، انشعاب و عقب‌گرد دیده می‌شوند؛
 *      دورهٔ تازه پس از بازیابی شکست نیست، ادامهٔ دورهٔ کهنه پس از آن هست.
 *   ۵) صفحهٔ «سابقهٔ عملیات» ِ صاحب (GET، فقط audit.view) همان حکم را می‌گیرد.
 *   ۶) مهاجرتِ ۴۱۰: rollback جدول و ردیفِ خودش را برمی‌دارد؛ دوباره نشستن همان.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const hc = require('../src/lib/hash-chain');
const ac = require('../src/lib/device-audit-chain');
const anchor = require('../src/lib/device-audit-anchor');
const cli = require('../scripts/verify-chain');

const DEV = '0f1e2d3c4b5a69788796a5b4c3d2e1f0';
const OTHER = 'aa11bb22cc33dd44ee55ff6600778899';

let admin = '';
let owner;
let shopId;

const H = (dev) => ({ 'X-App': 'shop', 'X-Device': dev });
const send = (heads, { dev = DEV, token = owner.accessToken, format = ac.FORMAT } = {}) =>
  h.api('POST', '/api/sync/v1/audit-anchor', { token, headers: H(dev), body: { device_id: dev, format, heads } });
const verdict = (token = owner.accessToken) => h.api('GET', '/api/sync/v1/audit-anchor', { token, headers: H(DEV) });
const flagOn = (scope) => query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain','true',1) ON CONFLICT DO NOTHING", [scope]);
const anchorsIn = async (shop) => (await query('SELECT chain, seq, hash, problem, other_hash FROM device_audit_anchors WHERE shop_id=$1 ORDER BY chain, seq', [shop])).rows;
const events = async (shop) => (await query(
  "SELECT target_id, detail FROM audit_logs WHERE action='security.audit_chain' AND shop_id=$1 ORDER BY created_at", [shop],
)).rows.map((r) => ({ chain: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }));

/** زنجیرهٔ [n] ردیفه با همان سازندهٔ برنامه (`AuditChain.linked`) */
function chainOf(n, { dev = DEV, epoch = 1, tag = 'a' } = {}) {
  let head = { chain: `${dev}/${epoch}`, seq: 0, hash: hc.GENESIS };
  const rows = [];
  for (let i = 1; i <= n; i++) {
    const raw = { id: `aud_${tag}_${dev.slice(0, 4)}_${epoch}_${i}`, type: 'sale', date: '2026-11-08', notes: `ردیفِ ${i}`, createdAt: 1762600000000 + i, amount: i * 7.25 };
    const out = ac.linked(ac.stamped(raw, { userId: 'usr_1', userRole: 'cashier', deviceId: dev }), head);
    head = out.head;
    rows.push(out.row);
  }
  return rows;
}
const headOf = (row) => ({ chain: row.chain, seq: row.chainSeq, hash: row.hash });

/** همان شکلِ نوشتنِ `sync-v1.js` در رونوشت (هر فیلد با `server_seq` ِ همان op) */
async function put(shop, row, seq) {
  const fsq = Object.fromEntries(Object.keys(row).map((k) => [k, seq]));
  await query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, field_seq, field_src, deleted_at, updated_seq, created_at, updated_at)
     VALUES ('shop','shop',$1,'auditLog',$2,$3::jsonb,$4::jsonb,'{}'::jsonb,NULL,$5,1,1)
     ON CONFLICT (app, account_kind, account_id, table_name, row_id) DO UPDATE SET
       data = excluded.data, field_seq = excluded.field_seq, deleted_at = NULL, updated_seq = excluded.updated_seq`,
    [shop, row.id, JSON.stringify(row), JSON.stringify(fsq), seq],
  );
}
async function fresh(label) {
  const u = await h.newUser(label);
  const id = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken })).body.shop.id;
  return { u, id };
}
async function runCli(args) {
  const lines = [];
  const code = await cli.main(args, (s) => lines.push(s));
  return { code, out: lines.join('\n') };
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  const s = await fresh('لنگرِ سابقه');
  owner = s.u;
  shopId = s.id;
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('۱) پرچمِ خاموش ⇒ {enabled:false} و هیچ ردیفی؛ روشن: ورودیِ بدشکل ۴۰۰', async () => {
  const rows = chainOf(2);
  const off = await send([headOf(rows[1])]);
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal(off.body.enabled, false);
  assert.equal((await verdict()).body.enabled, false);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM device_audit_anchors')).n, 0);

  await flagOn(shopId);
  const bad = async (heads, opts) => {
    const r = await send(heads, opts);
    assert.equal(r.status, 400, `${JSON.stringify(heads)} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  };
  await bad([headOf(rows[0])], { format: 'tohid-hashchain-v1' });
  await bad([{ ...headOf(rows[0]), chain: `${OTHER}/1` }]);                 //  زنجیرهٔ دستگاهِ دیگر
  await bad([{ ...headOf(rows[0]), chain: `${DEV}/0` }]);
  await bad([{ ...headOf(rows[0]), chain: `${DEV}/x` }]);
  await bad([{ ...headOf(rows[0]), seq: 0 }]);
  await bad([{ ...headOf(rows[0]), seq: 1.5 }]);
  await bad([{ ...headOf(rows[0]), hash: 'ABC' }]);
  await bad([headOf(rows[0]), headOf(rows[1])]);                            //  یک زنجیره دو بار
  await bad([1, 2, 3, 4, 5].map((e) => ({ ...headOf(rows[0]), chain: `${DEV}/${e}` })));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM device_audit_anchors')).n, 0);
});

test('۲) سر پذیرفته و نشانده می‌شود؛ گزارشِ تکراری هیچ نمی‌نویسد', async () => {
  const rows = chainOf(3, { tag: 'ok' });
  const r = await send([headOf(rows[2])]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.enabled, true);
  assert.deepEqual(r.body.acks, [headOf(rows[2])]);
  assert.deepEqual(r.body.problems, []);
  assert.equal(r.body.last_epoch, 1);
  const stored = await one('SELECT * FROM device_audit_anchors WHERE shop_id=$1', [shopId]);
  assert.equal(stored.chain, `${DEV}/1`);
  assert.equal(Number(stored.seq), 3);
  assert.equal(stored.hash, rows[2].hash);
  assert.equal(stored.device_id, DEV);
  assert.equal(Number(stored.epoch), 1);
  assert.equal(stored.user_id, owner.user.id);
  assert.equal(stored.problem, '');
  //  همان گزارش دوباره (و دوباره): همان پاسخ، همان یک ردیف، هیچ رویدادی
  for (let i = 0; i < 2; i++) {
    const again = await send([headOf(rows[2])]);
    assert.deepEqual([again.body.acks, again.body.problems], [[headOf(rows[2])], []]);
  }
  assert.equal((await anchorsIn(shopId)).length, 1);
  assert.deepEqual(await events(shopId), []);
  await query('DELETE FROM device_audit_anchors WHERE shop_id=$1', [shopId]);
});

test('۳) حسابِ پمپ ۴۰۳ shop_only و هیچ ردیفی', async () => {
  const code = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: admin });
  assert.equal(code.status, 201, JSON.stringify(code.body));
  const uid = `pc-anchor-${Date.now().toString(36)}`;
  const act = await h.post('/api/pump/device/activate', {
    code: code.body.code, device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: `st-anchor-${Date.now().toString(36)}`, name: 'پمپِ لنگر' },
  });
  assert.equal(act.status, 201, JSON.stringify(act.body));
  const pump = (method, body) => h.api(method, '/api/sync/v1/audit-anchor', { token: act.body.deviceToken, headers: { 'X-App': 'pump', 'X-Device': uid }, body });
  const heads = [{ chain: `${uid}/1`, seq: 1, hash: 'a'.repeat(64) }];
  for (const [method, body] of [['POST', { device_id: uid, format: ac.FORMAT, heads }], ['GET', null]]) {
    const r = await pump(method, body);
    assert.deepEqual([r.status, r.body?.error?.code], [403, 'shop_only'], `${method} ${JSON.stringify(r.body)}`);
  }
  //  کاربرِ پمپ (نه دستگاه) هم
  const pu = await h.newUser('پمپ‌دارِ لنگر', 'pump');
  const r = await h.api('POST', '/api/sync/v1/audit-anchor', { token: pu.accessToken, headers: { 'X-App': 'pump' }, body: { device_id: uid, format: ac.FORMAT, heads } });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM device_audit_anchors WHERE device_id = $1", [uid])).n, 0);
  //  بی توکن ۴۰۱
  assert.equal((await h.api('POST', '/api/sync/v1/audit-anchor', { body: { device_id: DEV, format: ac.FORMAT, heads: [] } })).status, 401);
});

test('۴) جداسازیِ دکان‌ها: «ب» (شاگردِ «الف» هم) لنگرِ «الف» را نه می‌بیند نه عوض می‌کند', async () => {
  const a = await fresh('لنگرِ الف');
  const b = await fresh('لنگرِ ب');
  await flagOn(a.id);
  await flagOn(b.id);
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.id, b.u.user.id, now()]);
  const rows = chainOf(3, { tag: 'ten' });
  for (const [i, r] of rows.entries()) await put(a.id, r, 10 + i);
  assert.equal((await send([headOf(rows[2])], { token: a.u.accessToken })).status, 200);
  const before = await anchorsIn(a.id);
  //  همان زنجیره با هشِ دیگر از نشستِ «ب» (با سرآیندِ دکانِ «الف») ⇒ فقط در فضای «ب»
  const evil = { ...headOf(rows[2]), hash: 'f'.repeat(64) };
  const r = await h.api('POST', '/api/sync/v1/audit-anchor', {
    token: b.u.accessToken, headers: { ...H(DEV), 'X-Shop-Id': a.id }, body: { device_id: DEV, format: ac.FORMAT, heads: [evil], shopId: a.id, shop_id: a.id },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.problems, []);
  assert.deepEqual(await anchorsIn(a.id), before, 'لنگرِ «الف» دست نخورد');
  assert.equal((await anchorsIn(b.id)).length, 1);
  assert.equal(JSON.stringify(r.body).includes(a.id), false);
  //  حکمِ «ب» فقط زنجیرهٔ «ب» است: سرِ ۳ بی ردیف (در راه)؛ هیچ ردیف یا هشی از «الف»
  const vb = await verdict(b.u.accessToken);
  assert.equal(vb.status, 200, JSON.stringify(vb.body));
  assert.equal(JSON.stringify(vb.body).includes(rows[2].hash), false);
  assert.equal(vb.body.chains.length, 1);
  assert.equal(vb.body.chains[0].rows, 0);
  const va = await verdict(a.u.accessToken);
  assert.equal(va.body.valid, true, JSON.stringify(va.body));
  assert.equal(va.body.chains[0].anchor.hash, rows[2].hash);
});

test('۵) سنجش با لنگر: بریدنِ دُمِ همگام‌نشده، ویرایش، جابه‌جایی، انشعاب و عقب‌گرد', async () => {
  const { u, id } = await fresh('سنجشِ لنگر');
  await flagOn(id);
  const go = (heads, dev = DEV) => send(heads, { dev, token: u.accessToken });
  const reason = async (opts) => { const r = await ac.verifyShop(id, opts); return r.broken && [r.broken.seq, r.broken.reason]; };
  const rows = chainOf(6, { tag: 'v' });
  const reset = async () => {
    await query("DELETE FROM sync_rows WHERE account_id=$1 AND table_name='auditLog'", [id]);
    await query('DELETE FROM device_audit_anchors WHERE shop_id=$1', [id]);
  };

  //  سالم: شش ردیف رسید، لنگر ۶
  for (const [i, r] of rows.entries()) await put(id, r, 10 + i);
  assert.deepEqual((await go([headOf(rows[5])])).body.problems, []);
  let rep = await ac.verifyShop(id);
  assert.equal(rep.ok, true, JSON.stringify(rep.broken));
  assert.deepEqual(rep.chains[0].anchor, { seq: 6, hash: rows[5].hash });
  assert.equal(rep.pending, 0);
  assert.equal((await runCli(['--device-audit', id])).code, 0);

  //  بریدنِ دُمی که هنوز همگام نشده بود: دستگاه تا ۶ نوشت و گزارش کرد، فقط ۱..۴ رسید
  await reset();
  for (const [i, r] of rows.slice(0, 4).entries()) await put(id, r, 10 + i);
  await go([headOf(rows[5])]);
  rep = await ac.verifyShop(id);
  assert.equal(rep.ok, true, 'تا پیر شدنِ لنگر «در راه» است، نه شکست');
  assert.equal(rep.pending, 2);
  assert.match((await runCli(['--device-audit', id])).out, /لنگر #6، 2 ردیفِ زیرِ لنگر هنوز در راه/);
  assert.deepEqual(await reason({ now: Date.now() + ac.GRACE_MS + 60_000 }), [5, 'truncated']);
  //  …یا همین حالا اگر دستگاه دورهٔ تازه گرفت (دورهٔ کهنه هرگز ادامه نمی‌یابد ⇒ ۵ و ۶ دیگر نمی‌رسند)
  const e2 = chainOf(1, { epoch: 2, tag: 'v2' });
  await put(id, e2[0], 30);
  await go([headOf(e2[0])]);
  assert.deepEqual(await reason(), [5, 'truncated']);
  const out = await runCli(['--device-audit', id]);
  assert.equal(out.code, 2);
  assert.match(out.out, /truncated/);

  //  همهٔ ردیف‌های زنجیره رفته‌اند ولی لنگر هست (و دوره بسته)
  await query("DELETE FROM sync_rows WHERE account_id=$1 AND table_name='auditLog' AND data->>'chain' = $2", [id, `${DEV}/1`]);
  assert.deepEqual(await reason(), [1, 'truncated']);

  //  ویرایشِ هم‌خوان (یادداشت و هشِ ردیف و دنباله از نو، `field_seq` هم): زنجیره در ظاهر سالم، لنگر نمی‌خواند
  await reset();
  const forged = rows.map((r) => ({ ...r }));
  forged[2].notes = 'دست‌خورده';
  for (let i = 2; i < forged.length; i++) {
    if (i > 2) forged[i].prev = forged[i - 1].hash;
    forged[i].hash = ac.hashOf(forged[i]);
  }
  for (const [i, r] of forged.entries()) await put(id, r, 10 + i);
  await go([headOf(rows[5])]);
  assert.equal(ac.verifyStored(forged.map((data) => ({ data }))).ok, true, 'بی لنگر دیده نمی‌شد');
  assert.deepEqual(await reason(), [6, 'anchor_mismatch']);

  //  جابه‌جایی (شماره‌های ۲ و ۳)
  await reset();
  for (const [i, r] of rows.entries()) await put(id, i === 1 ? { ...r, chainSeq: 3 } : i === 2 ? { ...r, chainSeq: 2 } : r, 10 + i);
  await go([headOf(rows[5])]);
  assert.deepEqual(await reason(), [2, 'prev_mismatch']);

  //  انشعاب: همان شماره با هشِ دیگر — یک بار رویداد، گزارشِ دوباره هم همان
  await reset();
  for (const [i, r] of rows.entries()) await put(id, r, 10 + i);
  await go([headOf(rows[3])]);
  const other = { ...headOf(rows[3]), hash: 'e'.repeat(64) };
  for (let i = 0; i < 2; i++) assert.deepEqual((await go([other])).body.problems, [{ chain: `${DEV}/1`, seq: 4, problem: 'fork' }]);
  assert.deepEqual((await anchorsIn(id)).map((x) => [Number(x.seq), x.problem, x.other_hash]), [[4, 'fork', 'e'.repeat(64)]]);
  assert.deepEqual(await reason(), [4, 'fork']);
  //  دو ردیفِ درست‌هش‌شده با یک شماره در رونوشت هم انشعاب است (تاریخچهٔ دوم پسِ همان ردیفِ ۳)
  await reset();
  for (const [i, r] of rows.entries()) await put(id, r, 10 + i);
  const twin = ac.linked({ ...rows[3], id: 'aud_twin', notes: 'دوقلو' }, headOf(rows[2])).row;
  assert.equal(twin.chainSeq, 4);
  await put(id, twin, 40);
  assert.deepEqual(await reason(), [4, 'fork']);

  //  عقب‌گرد در همان دوره: سرِ ۶ گزارش شد، بعد ۳ با هشِ دیگر
  await reset();
  for (const [i, r] of rows.entries()) await put(id, r, 10 + i);
  await go([headOf(rows[5])]);
  const back = await go([{ ...headOf(rows[2]), hash: 'c'.repeat(64) }]);
  assert.deepEqual(back.body.problems, [{ chain: `${DEV}/1`, seq: 3, problem: 'rollback' }]);
  assert.deepEqual(await reason(), [3, 'rollback']);
  const ev = await events(id);
  assert.deepEqual(ev.map((e) => [e.problem, e.seq]), [['restarted', 0], ['fork', 4], ['rollback', 3]]);
});

test('۶) دورهٔ تازه پس از بازیابیِ درست شکست نیست؛ ادامهٔ دورهٔ کهنه پس از آن عقب‌گرد است؛ جهشِ دوره رد', async () => {
  const { u, id } = await fresh('بازیابیِ لنگر');
  await flagOn(id);
  const go = (heads) => send(heads, { token: u.accessToken });
  const e1 = chainOf(4, { tag: 'r1' });
  for (const [i, r] of e1.entries()) await put(id, r, 10 + i);
  assert.deepEqual((await go([headOf(e1[3])])).body.problems, []);
  //  بازیابی از پشتیبان (دفترِ عقب‌رفته) ⇒ `AuditChain.resume` دورهٔ ۲ را از ۱ می‌گیرد؛ دورهٔ ۱ شاهد
  const e2 = chainOf(2, { epoch: 2, tag: 'r2' });
  for (const [i, r] of e2.entries()) await put(id, r, 20 + i);
  const r2 = await go([headOf(e1[3]), headOf(e2[1])]);
  assert.deepEqual(r2.body.problems, []);
  assert.deepEqual(r2.body.acks, [headOf(e1[3]), headOf(e2[1])]);
  assert.equal(r2.body.last_epoch, 2);
  const rep = await ac.verifyShop(id);
  assert.equal(rep.ok, true, JSON.stringify(rep.broken));
  assert.deepEqual(rep.chains.map((c) => [c.chain, c.rows, c.anchor.seq]), [[`${DEV}/1`, 4, 4], [`${DEV}/2`, 2, 2]]);
  assert.deepEqual((await events(id)).map((e) => [e.problem, e.chain]), [['restarted', `${DEV}/2`]]);
  const v = await verdict(u.accessToken);
  assert.deepEqual([v.body.enabled, v.body.valid, v.body.broken], [true, true, null]);

  //  ادامهٔ دورهٔ ۱ پس از دورهٔ ۲ هرگز کارِ برنامه نیست
  const e1more = chainOf(5, { tag: 'r1' });
  const stale = await go([headOf(e1more[4])]);
  assert.deepEqual(stale.body.problems, [{ chain: `${DEV}/1`, seq: 5, problem: 'rollback' }]);
  const broken = await ac.verifyShop(id);
  assert.deepEqual([broken.broken.chain, broken.broken.seq, broken.broken.reason], [`${DEV}/1`, 5, 'rollback']);
  const vb = await verdict(u.accessToken);
  assert.equal(vb.body.valid, false);
  assert.deepEqual([vb.body.broken.seq, vb.body.broken.reason], [5, 'rollback']);

  //  جهشِ بیش از ۱۶ دوره: بی ردیف، بی رویداد
  const far = chainOf(1, { epoch: 2 + anchor.MAX_EPOCH_STEP + 1, tag: 'far' });
  const j = await go([headOf(far[0])]);
  assert.deepEqual(j.body.problems, [{ chain: far[0].chain, seq: 1, problem: 'bad_epoch' }]);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM device_audit_anchors WHERE shop_id=$1 AND chain=$2', [id, far[0].chain])).n, 0);
});

test('۷) حکمِ سرور فقط برای audit.view: شاگرد ۴۰۳', async () => {
  const { u, id } = await fresh('اجازهٔ لنگر');
  await flagOn(id);
  const code = (await h.post('/api/shop/staff-code', {}, { token: u.accessToken })).body.code;
  const staff = await h.newUser('شاگردِ لنگر');
  assert.ok((await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken })).status < 300);
  const r = await verdict(staff.accessToken);
  assert.deepEqual([r.status, r.body?.error?.code], [403, 'permission_denied'], JSON.stringify(r.body));
  //  ولی سرِ دستگاهِ خودش را می‌فرستد (پاسبانی، رایگان)
  const rows = chainOf(1, { dev: OTHER, tag: 'st' });
  const s = await send([headOf(rows[0])], { dev: OTHER, token: staff.accessToken });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.deepEqual(s.body.problems, []);
  assert.equal((await verdict(u.accessToken)).status, 200);
});

test('۸) مهاجرتِ ۴۱۰: rollback جدول و ردیفِ خودش را برمی‌دارد؛ دوباره نشستن همان', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '410_device_audit_anchors.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async (t) => (await query('SELECT 1 FROM information_schema.tables WHERE table_name=$1', [t])).rows.length;
  const rowsBefore = (await one("SELECT COUNT(*)::int AS n FROM sync_rows WHERE table_name='auditLog'")).n;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(down);
  assert.equal(await has('device_audit_anchors'), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM sync_rows WHERE table_name='auditLog'")).n, rowsBefore, 'سابقهٔ همگام‌شده دست نخورد');
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has('device_audit_anchors'), 1);
  //  پس از برگشت، همان در دوباره کار می‌کند
  const rows = chainOf(1, { tag: 'mig' });
  const r = await send([headOf(rows[0])]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.acks, [headOf(rows[0])]);
});
