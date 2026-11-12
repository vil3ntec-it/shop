'use strict';
/**
 * TEN-04 — درهای خواندنِ محتوای خصوصیِ دکان در پنل پشتِ پرچمِ خاموشِ `security / admin.support_grant` (`lib/support-access.js`
 * › `grantForRead` / `grantForDossier` / `grantForRestore`، `lib/admin-dossier.js`). ماتریسِ همهٔ درها در `tenant-matrix.test.js` › «TEN-04 (اجازه)»؛
 * این‌جا لبه‌هایی که ماتریس نمی‌بیند:
 *
 *   ۱) مالی (که اجازه نمی‌گیرد) پروندهٔ دکان را با پرچمِ روشن هم می‌بیند — فقط فراداده (اشتراک)، بی جای دقیق.
 *   ۲) کاربرِ عضوِ دو دکان: اجازهٔ **هر کدام** پروندهٔ او را کامل می‌کند؛ بی هیچ‌کدام بی دستگاه و جا.
 *   ۳) اجازه مالِ همان مدیر است: ارشدِ دیگر ‎403‎؛ لغوِ صاحب ⇒ دوباره ‎403‎.
 *   ۴) بیرون از اجازه‌های دکان: کاربرِ بی‌دکان (پمپ) و تعارض‌های پمپ همان امروز.
 *   ۵) نوشتن: بازگرداندنِ تعارضِ دکان (`POST /api/admin/sync/conflicts/:id/restore`) اجازهٔ زندهٔ `restore` می‌خواهد (`read_private` نه).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const access = require('../src/lib/support-access');

const PASS = 'Admin!12345';
const toks = {};
let seq = 0;

test.before(async () => {
  await h.start();
  const hash = await require('../src/lib/password').hashPassword(PASS);
  let i = 0;
  for (const [u, role] of [['sgr-arshad', 'superadmin'], ['sgr-arshad2', 'superadmin'], ['sgr-mali', 'finance']]) {
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [newId('adm'), u, hash, role, now() + (++i)]);
  }
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function tok(u) {
  if (!toks[u]) {
    const r = await h.post('/api/admin/login', { username: u, password: PASS });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    toks[u] = r.body.token;
  }
  return toks[u];
}
async function located(label, app = 'shop') {
  const u = await h.newUser(`آدمِ ${label}`, app);
  const loc = await h.post('/api/location', { device: { uid: `sgr-${++seq}` }, location: { lat: 34.5, lng: 69.1, accuracy: 5, label: `جای-${label}` } },
    { token: u.accessToken });
  assert.equal(loc.status, 201, JSON.stringify(loc.body));
  return u;
}
async function shopOf(label) {
  const owner = await located(label);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { owner, shopId: made.body.shop.id };
}
const setFlag = async (on) => {
  await query("DELETE FROM live_config WHERE scope='security' AND key=$1", [access.ENFORCE_KEY]);
  if (on) await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ('security', $1, 'true', $2)", [access.ENFORCE_KEY, now()]);
};
const as = async (who, url) => { resetAll(); return h.get(url, { token: await tok(who) }); };
async function grant(s, who = 'sgr-arshad') {
  resetAll();
  const r = await h.api('POST', `/api/admin/shops/${s.shopId}/access-grants`, {
    token: await tok(who), headers: { 'Idempotency-Key': `sgr-${Date.now()}-${++seq}` },
    body: { scope: 'read_private', minutes: 30, reason: 'بررسیِ دستگاهِ بسته', consent: 'tenant' },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const ok = await h.api('POST', `/api/shop/support-access/${r.body.grant.id}/accept`, { token: s.owner.accessToken });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  return r.body.grant.id;
}
const revoke = async (s, gid) => {
  const r = await h.api('POST', `/api/shop/support-access/${gid}/revoke`, { token: s.owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

test('TEN-04: پرچمِ روشن ⇒ مالی پروندهٔ دکان را فقط با فراداده می‌بیند (اشتراک بله، جای دقیق نه) و اجازه هم نمی‌تواند بخواهد', async () => {
  const s = await shopOf('مالی');
  await setFlag(true);
  try {
    const r = await as('sgr-mali', `/api/admin/shops/${s.shopId}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.location, null);
    assert.deepEqual(r.body.restricted, { code: 'grant_required', hidden: ['location'] });
    assert.ok(Array.isArray(r.body.subscriptions) && r.body.entitlement, 'فرادادهٔ اشتراک می‌ماند');
    assert.ok(!JSON.stringify(r.body).includes('جای-مالی'));
    const g = await h.api('POST', `/api/admin/shops/${s.shopId}/access-grants`, {
      token: await tok('sgr-mali'), headers: { 'Idempotency-Key': `sgr-m-${++seq}` },
      body: { scope: 'read_private', minutes: 30, reason: 'بررسی', consent: 'tenant' },
    });
    assert.equal(g.status, 403, 'مالی اجازه نمی‌خواهد');
  } finally {
    await setFlag(false);
  }
  const off = await as('sgr-mali', `/api/admin/shops/${s.shopId}`);
  assert.equal(off.body.location.label, 'جای-مالی', 'پرچمِ خاموش ⇒ همان امروز');
  assert.equal(off.body.restricted, undefined);
});

test('TEN-04: کاربرِ عضوِ دو دکان ⇒ اجازهٔ هر کدام پرونده‌اش را کامل می‌کند؛ بی هیچ‌کدام بی دستگاه و جا', async () => {
  const mine = await shopOf('خودش');
  const boss = await shopOf('کارفرما');
  const t = now();
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), boss.shopId, mine.owner.user.id, t]);
  const url = `/api/admin/users/${mine.owner.user.id}`;
  await setFlag(true);
  try {
    const shut = await as('sgr-arshad', url);
    assert.equal(shut.status, 200);
    assert.deepEqual(shut.body.restricted?.hidden, ['devices', 'locations', 'lastLocation']);
    assert.deepEqual([shut.body.devices, shut.body.locations, shut.body.user.lastLocation], [[], [], null]);
    assert.equal(shut.body.memberships.length, 2, 'عضویت‌ها فراداده‌اند');
    for (const s of [boss, mine]) {
      const gid = await grant(s);
      const full = await as('sgr-arshad', url);
      assert.equal(full.body.restricted, undefined, `اجازهٔ ${s.shopId}`);
      assert.ok(full.body.locations.some((l) => l.label === 'جای-خودش') && full.body.devices.length > 0);
      const row = await one(`SELECT detail FROM audit_logs WHERE action='admin.user_viewed' AND target_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1`,
        [mine.owner.user.id]);
      assert.equal(row.detail.grant, gid);
      await revoke(s, gid);
    }
    assert.ok((await as('sgr-arshad', url)).body.restricted, 'پس از لغوِ هر دو دوباره بسته');
  } finally {
    await setFlag(false);
  }
});

test('TEN-04: اجازه مالِ همان مدیر است و لغوِ صاحب در را دوباره می‌بندد (اجازه‌ها و دستگاه‌های دکان)', async () => {
  const s = await shopOf('اعضا');
  const url = `/api/admin/shops/${s.shopId}/staff-perms`;
  await setFlag(true);
  try {
    const gid = await grant(s);
    const other = await as('sgr-arshad2', url);
    assert.equal(other.status, 403);
    assert.equal(other.body.error.code, 'grant_required');
    const ok = await as('sgr-arshad', url);
    assert.equal(ok.status, 200, JSON.stringify(ok.body));
    assert.ok(ok.body.devices.length > 0);
    await revoke(s, gid);
    const after = await as('sgr-arshad', url);
    assert.equal(after.status, 403);
    assert.equal(after.body.error.code, 'grant_required');
    assert.equal(Number((await one('SELECT uses FROM admin_access_grants WHERE id=$1', [gid])).uses), 1);
  } finally {
    await setFlag(false);
  }
});

test('TEN-04: بیرون از اجازه‌های دکان ⇒ همان امروز: کاربرِ بی‌دکان (پمپ) و تعارض‌های پمپ', async () => {
  const pumpUser = await located('پمپ', 'pump');
  const url = `/api/admin/users/${pumpUser.user.id}`;
  const pick = (b) => ({ devices: b.devices, locations: b.locations, lastLocation: b.user.lastLocation, restricted: b.restricted });
  const today = pick((await as('sgr-arshad', url)).body);
  assert.ok(today.devices.length > 0, 'آزمون بی‌اثر نیست');
  await setFlag(true);
  try {
    const u = await as('sgr-arshad', url);
    assert.equal(u.status, 200);
    assert.deepEqual(pick(u.body), today);
    const c = await as('sgr-arshad', '/api/admin/sync/conflicts?app=pump&account=st-x');
    assert.equal(c.status, 200, JSON.stringify(c.body));
  } finally {
    await setFlag(false);
  }
});

test('TEN-04: بازگرداندنِ تعارضِ دکان با پرچمِ روشن فقط با اجازهٔ زندهٔ «بازگرداندن» ِ همین آدم؛ بی آن هیچ op و نشانِ «در حالِ رفتن» نمی‌ماند', async () => {
  const s = await shopOf('تعارض');
  const cf = async () => String((await one(
    `INSERT INTO sync_conflicts (app, account_kind, account_id, table_name, row_id, field, loser_value, winner_value,
                                 loser_op_id, winner_op_id, loser_device, winner_device, at)
     VALUES ('shop','shop',$1,'products',$2,'name','"نامِ بازنده"'::jsonb,'"نامِ برنده"'::jsonb,'o1','o2','d1','d2',$3) RETURNING id`,
    [s.shopId, `p-${++seq}`, now()])).id);
  const restore = async (id) => {
    resetAll();
    return h.api('POST', `/api/admin/sync/conflicts/${id}/restore`, { token: await tok('sgr-arshad'), body: { reason: 'درخواستِ صاحبِ دکان' } });
  };
  const restoredAt = async (id) => (await one('SELECT restored_at FROM sync_conflicts WHERE id=$1', [Number(id)])).restored_at;
  await setFlag(true);
  try {
    const id = await cf();
    const shut = await restore(id);
    assert.equal(shut.status, 403, JSON.stringify(shut.body));
    assert.equal(shut.body.error.code, 'grant_required');
    assert.equal(await restoredAt(id), null);
    assert.equal(await one('SELECT 1 FROM sync_conflict_restore_claims WHERE conflict_id=$1', [Number(id)]), null, 'نشان برداشته شد');
    await grant(s);   //  «خواندن» بازگرداندن را باز نمی‌کند
    assert.equal((await restore(id)).body.error.code, 'grant_required');

    resetAll();
    const g = await h.api('POST', `/api/admin/shops/${s.shopId}/access-grants`, {
      token: await tok('sgr-arshad'), headers: { 'Idempotency-Key': `sgr-r-${++seq}` },
      body: { scope: 'restore', minutes: 30, reason: 'بازگرداندنِ نامِ کالا', consent: 'tenant' },
    });
    assert.equal(g.status, 201, JSON.stringify(g.body));
    assert.equal((await h.api('POST', `/api/shop/support-access/${g.body.grant.id}/accept`, { token: s.owner.accessToken })).status, 200);
    const ok = await restore(id);
    assert.equal(ok.status, 200, JSON.stringify(ok.body));
    assert.notEqual(await restoredAt(id), null);
    const row = await one("SELECT detail FROM audit_logs WHERE action='sync.conflict_restored' AND target_id=$1", [id]);
    assert.equal(row.detail.grant, g.body.grant.id);
  } finally {
    await setFlag(false);
  }
  //  خاموش ⇒ همان امروز (ارشد + دلیل)
  const later = await cf();
  assert.equal((await restore(later)).status, 200);
});
