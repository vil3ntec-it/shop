'use strict';
/**
 * TEN-04 — نخستین درِ محتوای خصوصی که اجازهٔ دسترسیِ پشتیبانی می‌خواهد: دانلودِ پشتیبانِ دکان در پنل
 * (`GET /api/admin/accounts/:app/:tenantId/backups/:id`) پشتِ پرچمِ خاموشِ `security / admin.support_grant`.
 *
 *   ۱) خاموش (پیش‌فرض) ⇒ همان امروز: ارشد + دلیل + سابقه، بی اجازه.
 *   ۲) روشن ⇒ بی اجازه، با درخواستِ بی‌پاسخ، با دامنهٔ دیگر، مدیرِ دیگر یا اجازهٔ دکانِ دیگر ‎403 grant_required‎ و هیچ بایت
 *      و هیچ سابقهٔ «دانلود»؛ پس از پذیرفتنِ صاحبِ همان دکان ⇒ همان بایت‌ها، شمارِ استفاده و سابقه با شناسهٔ اجازه؛
 *      لغو ⇒ دوباره بسته.
 *   ۳) بخش‌های دیگر (پمپ) هرگز به اجازه نمی‌رسند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const access = require('../src/lib/support-access');

const PASS = 'Admin!12345';
const toks = {};
let seq = 0;
const key = () => `sge-${Date.now()}-${++seq}`;
const REASON = encodeURIComponent('بررسیِ خطای بازیابی');

test.before(async () => {
  await h.start();
  const hash = await require('../src/lib/password').hashPassword(PASS);
  let i = 0;
  for (const u of ['sge-arshad', 'sge-arshad2']) {
    await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,'superadmin','active',$4)`,
      [newId('adm'), u, hash, now() + (++i)]);
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
async function shopWithBackup(label) {
  const owner = await h.newUser(`صاحبِ ${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const data = Buffer.from(`دفترِ دکانِ ${label}`);
  const up = await h.raw('POST', '/api/me/backups?ext=json', data, { token: owner.accessToken });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  return { owner, shopId: made.body.shop.id, backupId: up.body.backup.id, data };
}
const setFlag = async (on) => {
  await query("DELETE FROM live_config WHERE scope='security' AND key=$1", [access.ENFORCE_KEY]);
  if (on) {
    await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ('security', $1, 'true', $2)", [access.ENFORCE_KEY, now()]);
  }
};
const dl = async (who, s) => h.download(`/api/admin/accounts/shop/${s.shopId}/backups/${s.backupId}?reason=${REASON}`, { token: await tok(who) });
const downloads = (shopId) => many("SELECT * FROM audit_logs WHERE action='admin.account_backup_downloaded' AND shop_id=$1 ORDER BY created_at, id", [shopId]);
const detail = (r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail);
const codeOf = (res) => { try { return JSON.parse(res.buffer.toString('utf8')).error.code; } catch { return ''; } };
const grantReq = async (who, shopId, scope) => {
  const r = await h.api('POST', `/api/admin/shops/${shopId}/access-grants`, {
    token: await tok(who), headers: { 'Idempotency-Key': key() },
    body: { scope, minutes: 30, reason: 'بازیابیِ پشتیبانِ خراب', consent: 'tenant' },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.grant;
};
const accept = async (s, gid) => {
  const r = await h.api('POST', `/api/shop/support-access/${gid}/accept`, { token: s.owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};

test('TEN-04: پرچمِ خاموش ⇒ همان امروز — ارشد با دلیل دانلود می‌کند، بی اجازه و سابقه بی شناسهٔ اجازه', async () => {
  await setFlag(false);
  const s = await shopWithBackup('خاموش');
  assert.equal(await access.enforced(), false);
  const res = await dl('sge-arshad', s);
  assert.equal(res.status, 200);
  assert.ok(res.buffer.equals(s.data));
  const rows = await downloads(s.shopId);
  assert.equal(rows.length, 1);
  assert.equal(detail(rows[0]).grant, undefined);
});

test('TEN-04: پرچمِ روشن ⇒ بی اجازهٔ زندهٔ «خروجی» ِ همین مدیر روی همین دکان هیچ بایتی بیرون نمی‌رود؛ با اجازه همان بایت‌ها و سابقه', async () => {
  const s = await shopWithBackup('الف');
  const other = await shopWithBackup('ب');
  await setFlag(true);
  try {
    assert.equal(await access.enforced(), true);
    const shut = async (who, why) => {
      const r = await dl(who, s);
      assert.equal(r.status, 403, `${why} ⇒ ${r.status}`);
      assert.equal(codeOf(r), 'grant_required', why);
      assert.ok(!r.buffer.includes(s.data), `${why}: بایتی از پشتیبان بیرون آمد`);
    };
    await shut('sge-arshad', 'بی اجازه');
    const g = await grantReq('sge-arshad', s.shopId, 'export');
    await shut('sge-arshad', 'درخواستِ بی‌پاسخ');
    const wrongScope = await grantReq('sge-arshad', s.shopId, 'read_private');
    await accept(s, wrongScope.id);
    await shut('sge-arshad', 'اجازهٔ «خواندن»، نه «خروجی»');
    const elsewhere = await grantReq('sge-arshad', other.shopId, 'export');
    await accept(other, elsewhere.id);
    await shut('sge-arshad', 'اجازهٔ دکانِ دیگر');
    assert.equal((await downloads(s.shopId)).length, 0, 'ردشده سابقهٔ «دانلود» ندارد');

    await accept(s, g.id);
    await shut('sge-arshad2', 'مدیرِ ارشدِ دیگر');
    const ok = await dl('sge-arshad', s);
    assert.equal(ok.status, 200);
    assert.ok(ok.buffer.equals(s.data));
    const rows = await downloads(s.shopId);
    assert.equal(rows.length, 1);
    assert.equal(detail(rows[0]).grant, g.id);
    assert.equal(Number((await one('SELECT uses FROM admin_access_grants WHERE id=$1', [g.id])).uses), 1);
    const used = await many("SELECT detail FROM audit_logs WHERE action='admin.support_access_used' AND shop_id=$1", [s.shopId]);
    assert.ok(used.some((r) => detail(r).grant === g.id && detail(r).what === 'backup_download'), JSON.stringify(used));

    //  لغو ⇒ دوباره بسته
    const rv = await h.api('POST', `/api/shop/support-access/${g.id}/revoke`, { token: s.owner.accessToken });
    assert.equal(rv.status, 200, JSON.stringify(rv.body));
    await shut('sge-arshad', 'پس از لغو');
    assert.equal((await downloads(s.shopId)).length, 1);
  } finally {
    await setFlag(false);
  }
});

test('TEN-04: بخشِ دیگر (پمپ) با پرچمِ روشن هم به اجازه نمی‌رسد', async () => {
  await setFlag(true);
  try {
    assert.equal(await access.grantForExport({}, 'pump', 'st-1', 'backup_download'), null);
    assert.equal(await access.grantForExport({}, 'bank', 'x', 'backup_download'), null);
  } finally {
    await setFlag(false);
  }
});
