'use strict';
/**
 * بازگرداندنِ تعارضِ همگام‌سازی یک بار است — یافتهٔ ۱.۵۰.۷۴ ِ ممیزیِ پنلِ مرکزی (§۹، `OWNER-CONSOLE-AUDIT-fa.md` ِ ریپوی server).
 *
 * پیش از این:
 *   • شناسه با `Number(id)` خوانده می‌شد ⇒ «05»، «5e0»، «5.0» و «+5» همه به تعارضِ ۵ می‌رسیدند (و از یادِ پل که به شناسه بسته بود می‌گذشتند)؛
 *   • تعارضِ برگردانده‌شده (‎restored_at‎ پُر) دوباره برگردانده می‌شد ⇒ opِ تازه‌ای که مقدارِ تازه‌ترِ همان خانه را رو می‌نوشت؛
 *   • `Idempotency-Key` نادیده بود ⇒ تکرارِ پس از قطعِ شبکه opِ دوم می‌ساخت؛ پل فقط ده دقیقه و تا راه‌اندازیِ دوباره یادش بود.
 *
 * حالا (`routes/admin-sync.js` + `lib/sync-restore-once.js`، مهاجرتِ ۴۹۵):
 *   • شناسه فقط رقم و یک شکل (‎/^[1-9]\d{0,14}$/‎) ⇒ وگرنه ۴۰۰ `bad_id`؛
 *   • برگردانده‌شده ⇒ ۴۰۹ `already_restored`؛ در حالِ رفتن ⇒ ۴۰۹ `restore_in_progress` (نشانِ کوتاه‌عمر در پایگاه، نه حافظهٔ فرآیند)؛
 *   • همان کلید + همان بدنه ⇒ همان پاسخ با `Idempotent-Replayed: true`؛ همان کلید + بدنهٔ دیگر ⇒ ۴۰۹ `idempotency_key_reused`؛
 *   • شکستِ بازگرداندن نشان را برمی‌دارد ⇒ تلاشِ دوباره «در حالِ رفتن» نمی‌گیرد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

let token = '';
let shopId = '';

test.before(async () => {
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'restore-arshad','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  token = (await h.post('/api/admin/login', { username: 'restore-arshad', password: 'Admin!12345' })).body.token;
  const u = await h.newUser('صاحبِ بازگرداندن', 'shop');
  shopId = (await h.post('/api/shop', { name: 'دکانِ بازگرداندن' }, { token: u.accessToken })).body.shop.id;
});
test.after(async () => { await h.stop(); });

async function conflict({ app = 'shop', row = `p-${newId('r')}`, field = 'name', loser = 'نامِ بازنده' } = {}) {
  const r = await one(
    `INSERT INTO sync_conflicts (app, account_kind, account_id, table_name, row_id, field, loser_value, winner_value,
                                 loser_op_id, winner_op_id, loser_device, winner_device, at)
     VALUES ($1,'shop',$2,'products',$3,$4,$5::jsonb,'"نامِ برنده"'::jsonb,'o1','o2','d1','d2',$6) RETURNING id`,
    [app, shopId, row, field, JSON.stringify(loser), now()]
  );
  return { id: String(r.id), row };
}
const restore = (id, { reason = 'درخواستِ صاحبِ دکان', key = '', body = null } = {}) => h.api('POST',
  `/api/admin/sync/conflicts/${id}/restore`, {
    token, body: body || { reason }, headers: key ? { 'Idempotency-Key': key } : {},
  });
const serverOps = async (row) => Number((await one(
  `SELECT COUNT(*)::int AS n FROM oplog WHERE device_id='server' AND row_id=$1`, [row])).n);
const restoredAt = async (id) => (await one('SELECT restored_at FROM sync_conflicts WHERE id=$1', [Number(id)])).restored_at;
const auditFor = (id) => many(`SELECT * FROM audit_logs WHERE action='sync.conflict_restored' AND target_id=$1`, [String(id)]);

test('شناسهٔ ناهم‌شکل (05، 5e0، 5.0، +5، 0x5، 0، فاصله) ⇒ ۴۰۰ bad_id و هیچ opی', async () => {
  const cf = await conflict();
  const n = Number(cf.id);
  for (const id of [`0${n}`, `${n}e0`, `${n}.0`, `%2B${n}`, `0x${n.toString(16)}`, '0', `%20${n}`, `${n}%20`, '9'.repeat(16)]) {
    const r = await restore(id);
    assert.equal(r.status, 400, `${id} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_id', id);
  }
  assert.equal(await restoredAt(cf.id), null, 'هیچ شکلِ دیگری به همان تعارض نرسید');
  assert.equal(await serverOps(cf.row), 0);
});

test('بازگرداندنِ دوم ⇒ ۴۰۹ already_restored؛ فقط یک op و یک ردیفِ سابقه', async () => {
  const cf = await conflict();
  const first = await restore(cf.id);
  assert.equal(first.status, 200, JSON.stringify(first.body));
  assert.equal(first.body.ok, true);
  assert.ok(await restoredAt(cf.id));
  const second = await restore(cf.id, { reason: 'دوباره از پنجرهٔ دیگر' });
  assert.equal(second.status, 409, JSON.stringify(second.body));
  assert.equal(second.body.error.code, 'already_restored');
  assert.equal(await serverOps(cf.row), 1, 'opِ دوم ساخته نشد');
  assert.equal((await auditFor(cf.id)).length, 1);
});

test('Idempotency-Key: همان کلید + همان بدنه ⇒ همان پاسخ (Idempotent-Replayed)؛ بدنهٔ دیگر ⇒ ۴۰۹؛ کلیدِ بد ⇒ ۴۰۰', async () => {
  const cf = await conflict();
  const key = `restore-${newId('k')}`;
  const a = await restore(cf.id, { key });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.headers.get('idempotent-replayed'), null);
  const b = await restore(cf.id, { key });
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal(b.headers.get('idempotent-replayed'), 'true');
  assert.deepEqual(b.body, a.body, 'همان پاسخ، نه پاسخِ تازه');
  assert.equal(await serverOps(cf.row), 1);
  //  همان کلید با دلیلِ دیگر یا برای تعارضِ دیگر ⇒ ۴۰۹، بی تغییر
  const other = await restore(cf.id, { key, reason: 'دلیلِ دیگری' });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'idempotency_key_reused');
  const cf2 = await conflict();
  const moved = await restore(cf2.id, { key });
  assert.equal(moved.status, 409);
  assert.equal(moved.body.error.code, 'idempotency_key_reused');
  assert.equal(await restoredAt(cf2.id), null);
  //  کلیدِ تازه روی تعارضِ برگردانده‌شده ⇒ همان ۴۰۹ ِ «برگردانده شد»
  const fresh = await restore(cf.id, { key: `restore-${newId('k')}` });
  assert.equal(fresh.status, 409);
  assert.equal(fresh.body.error.code, 'already_restored');
  const bad = await restore(cf2.id, { key: 'short' });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_idempotency_key');
  assert.equal((await auditFor(cf.id)).length, 1);
  assert.equal(await serverOps(cf2.row), 0);
});

test('دو بازگرداندنِ هم‌زمان (دو مدیر/دو پنجره، کلیدِ جدا یا بی‌کلید) ⇒ دقیقاً یکی ۲۰۰ و یک op', async () => {
  for (let round = 0; round < 3; round++) {
    const cf = await conflict();
    const out = await Promise.all([
      restore(cf.id, { key: `w1-${newId('k')}` }), restore(cf.id, { key: `w2-${newId('k')}` }),
      restore(cf.id), restore(cf.id, { reason: 'پنجرهٔ چهارم' }),
    ]);
    const ok = out.filter((r) => r.status === 200);
    assert.equal(ok.length, 1, JSON.stringify(out.map((r) => [r.status, r.body?.error?.code])));
    for (const r of out.filter((x) => x.status !== 200)) {
      assert.equal(r.status, 409);
      assert.ok(['already_restored', 'restore_in_progress'].includes(r.body.error.code), r.body.error.code);
    }
    assert.equal(await serverOps(cf.row), 1);
    assert.equal((await auditFor(cf.id)).length, 1);
  }
});

test('همان کلید هم‌زمان (دو کلیکِ پشتِ هم) ⇒ یک op؛ دومی همان پاسخ یا ۴۰۹ restore_in_progress، هرگز op ِ دوم', async () => {
  const cf = await conflict();
  const key = `dbl-${newId('k')}`;
  const [a, b] = await Promise.all([restore(cf.id, { key }), restore(cf.id, { key })]);
  const codes = [a, b].map((r) => (r.status === 200 ? 200 : r.body.error.code)).sort();
  assert.ok(codes[0] === 200, JSON.stringify(codes));
  assert.ok(codes[1] === 200 || codes[1] === 'restore_in_progress', JSON.stringify(codes));
  assert.equal(await serverOps(cf.row), 1);
  const later = await restore(cf.id, { key });
  assert.equal(later.status, 200);
  assert.equal(later.headers.get('idempotent-replayed'), 'true');
});

test('شکستِ بازگرداندن نشان را برمی‌دارد: تلاشِ دوباره همان خطا را می‌گیرد، نه «در حالِ رفتن»؛ نشانِ کهنه راه را نمی‌بندد', async () => {
  //  بخشِ بی‌شمای همگام‌سازی ⇒ push خطای ۴۰۰ `no_schema` می‌دهد (پس از گرفتنِ نشان)
  const cf = await conflict({ app: 'zz' });
  const a = await restore(cf.id);
  assert.equal(a.status, 400, JSON.stringify(a.body));
  const b = await restore(cf.id);
  assert.equal(b.status, 400, `دومی ${b.status} ${JSON.stringify(b.body)}`);
  assert.equal(await restoredAt(cf.id), null);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM sync_conflict_restore_claims WHERE conflict_id=$1', [Number(cf.id)])).n, 0);
  //  نشانِ ماندهٔ فرآیندِ مُرده: تا کهنه نشده «در حالِ رفتن»، پس از آن کنار می‌رود
  const live = await conflict();
  await query(`INSERT INTO sync_conflict_restore_claims (conflict_id, token, admin_id, idem_key, claimed_at) VALUES ($1,'t-dead','adm_x','',$2)`,
    [Number(live.id), now()]);
  const busy = await restore(live.id);
  assert.equal(busy.status, 409);
  assert.equal(busy.body.error.code, 'restore_in_progress');
  await query('UPDATE sync_conflict_restore_claims SET claimed_at=$2 WHERE conflict_id=$1',
    [Number(live.id), now() - require('../src/lib/sync-restore-once').STALE_MS - 1000]);
  const taken = await restore(live.id);
  assert.equal(taken.status, 200, JSON.stringify(taken.body));
  assert.equal(await serverOps(live.row), 1);
});

test('تعارضِ نبوده ⇒ ۴۰۴ (همان پیشین)؛ بی دلیل ⇒ ۴۰۰ reason_required (مدیرِ عادی ⇒ ۴۰۳ در admin-control-plane)', async () => {
  assert.equal((await restore('999999')).status, 404);
  const noReason = await restore('1', { body: {} });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
});

test('مهاجرتِ ۴۹۵: برگشت فقط جدولِ نشان را برمی‌دارد و ردیفِ schema_migrations ِ خودش را', async () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '495_sync_conflict_restore_claims.sql';
  const mark = await one('SELECT * FROM schema_migrations WHERE version=$1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  const has = async (t) => (await query('SELECT 1 FROM information_schema.tables WHERE table_name=$1', [t])).rows.length;
  const before = Number((await one('SELECT COUNT(*)::int AS n FROM sync_conflicts')).n);
  await query(fs.readFileSync(path.join(dir, 'rollback', '495_sync_conflict_restore_claims.down.sql'), 'utf8'));
  assert.equal(await has('sync_conflict_restore_claims'), 0);
  assert.equal(await one('SELECT 1 FROM schema_migrations WHERE version=$1', [name]), null);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM sync_conflicts')).n), before, 'تعارض‌ها دست نخوردند');
  await query(fs.readFileSync(path.join(dir, name), 'utf8'));
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has('sync_conflict_restore_claims'), 1);
});
