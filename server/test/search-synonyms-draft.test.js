'use strict';
/**
 * POS-04 و WEB-05 — پیش‌نویسِ فهرستِ مترادف (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ #۶؛ `lib/search-synonyms-draft.js`).
 *
 *   ۱) پیش‌نویس همان سنجشِ پنل را می‌گذرد (۵۰ تا ۱۰۰ گروه، هر واژه فقط در یک گروه)، هر گروه دست‌کم یک واژهٔ فارسی/دری و یک
 *      واژهٔ دیگر دارد، و با هستهٔ جست‌وجو (`text-core.matches` = `TextNormalizer.kt` ِ صندوق) هر واژهٔ گروه کالای نام‌برده با
 *      واژهٔ دیگرِ همان گروه را پیدا می‌کند؛ بی فهرست نه.
 *   ۲) پیش‌فرض خاموش: پیش‌نمایش هیچ نمی‌نویسد؛ `--apply` بی دلیل رد؛ بار کردن افزودن است — گروهِ صاحب‌کار دست نمی‌خورد و
 *      گروهِ هم‌پوشان کنار می‌رود؛ تکرار ⇒ unchanged؛ همان سند از درِ صندوق (`GET /api/search/synonyms`).
 *   ۳) ویترین با پرچمِ دکانِ `shop.storefront.synonyms`: «تیل» روغن را، «وریجې» برنج را، «مالګه» نمک را پیدا می‌کند؛ پرچمِ خاموش ⇒ نه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const syn = require('../src/lib/search-synonyms');
const draft = require('../src/lib/search-synonyms-draft');
const sf = require('../src/lib/storefront');
const tc = require('../src/lib/text-core');

const PASS = 'Admin!12345';
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const FA = /[؀-ۿ]/;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'syd-arshad','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword(PASS), now()]);
  admin = (await h.post('/api/admin/login', { username: 'syd-arshad', password: PASS })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('۱) پیش‌نویس همان سنجشِ پنل را می‌گذرد و هر واژه کالای هم‌گروهش را پیدا می‌کند', () => {
  const groups = draft.readDraft();
  assert.ok(groups.length >= 50 && groups.length <= 100, `${groups.length} گروه`);
  for (const g of groups) {
    assert.ok(g.filter((t) => FA.test(t)).length >= 1 && g.length >= 2, g.join('،'));
    assert.ok(!g.some((t) => /[0-9۰-۹٠-٩]/.test(t)), `رقم در «${g.join('،')}»`);
  }
  //  مثال‌های تصمیم و پشتو
  const has = (a, b) => groups.some((g) => g.includes(a) && g.includes(b));
  assert.ok(has('روغن', 'تیل'));
  assert.ok(has('برنج', 'وریجې'));
  assert.ok(has('نمک', 'مالګه'));
  assert.ok(has('کچالو', 'سیب‌زمینی'));
  //  هر واژه ⇐ کالای نام‌برده با واژهٔ دیگرِ همان گروه (همان قاعدهٔ صندوق و ویترین)؛ بی فهرست نه
  let pairs = 0;
  for (const g of groups) {
    for (const q of g) {
      for (const name of g) {
        if (name === q) continue;
        assert.ok(tc.matches(`${name} ۵ کیلو`, q, groups), `«${q}» ⇐ «${name}»`);
        pairs++;
      }
    }
  }
  assert.ok(pairs > 300, `${pairs}`);
  assert.equal(tc.matches('روغن آفتاب‌گردان', 'تیل'), false, 'بی فهرست مترادفی نیست');
  assert.equal(tc.matches('روغن آفتاب‌گردان', 'تیل', groups), true);
  assert.equal(tc.matches('وریجې', 'برنج', groups), true);
  //  سندِ خوانای صاحب‌کار همان داده است
  const doc = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', '..', 'docs', 'SYNONYMS-draft-fa.md'), 'utf8');
  for (const g of groups) assert.ok(doc.includes(`| ${g.join(' · ')} |`), `«${g.join('،')}» در docs/SYNONYMS-draft-fa.md نیست`);
});

test('۲) پیش‌فرض خاموش؛ بار کردن افزودن است و گروهِ صاحب‌کار دست نمی‌خورد؛ تکرار unchanged', async () => {
  const preview = await draft.load();
  assert.equal(preview.apply, false);
  assert.equal(preview.added, preview.draft);
  assert.equal(await one("SELECT 1 FROM app_config WHERE key='search_synonyms'"), null, 'پیش‌نمایش هیچ ننوشت');
  await assert.rejects(() => draft.load({ apply: true }), /دلیل/);
  assert.equal(await one("SELECT 1 FROM app_config WHERE key='search_synonyms'"), null);

  //  صاحب‌کار پیش‌تر در پنل یک گروه گذاشته که «شکر» دارد
  const own = await h.api('PUT', '/api/admin/search/synonyms', { token: admin, body: { version: 0, groups: [['شکر', 'قند']], reason: 'گروهِ خودِ صاحب‌کار' } });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  const r = await draft.load({ apply: true, reason: 'پیش‌نویسِ بازبینی‌شده' });
  assert.equal(r.unchanged, false);
  assert.deepEqual(r.skipped.map((s) => s.clash), [['شکر']], 'فقط گروهِ «شکر» کنار رفت');
  assert.equal(r.added, r.draft - 1);
  const doc = await syn.get();
  assert.deepEqual(doc.groups[0], ['شکر', 'قند'], 'گروهِ صاحب‌کار همان و اول');
  assert.equal(doc.groups.length, 1 + r.added);
  assert.ok(!doc.groups.some((g) => g.includes('بوره')), 'گروهِ هم‌پوشان نیامد');
  const again = await draft.load({ apply: true, reason: 'دوباره' });
  assert.equal(again.unchanged, true);
  assert.equal((await syn.get()).version, doc.version);
  //  صندوقِ برنامه‌ها همان سند را می‌گیرد
  const pub = await h.get('/api/search/synonyms');
  assert.equal(pub.status, 200);
  assert.deepEqual(pub.body.groups, doc.groups);
});

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

test('۳) ویترین: «تیل»، «وریجې»، «مالګه» با پرچمِ دکان کالای هم‌معنا را پیدا می‌کنند؛ خاموش ⇒ نه', async () => {
  const u = await h.newUser(`ویترینِ پیش‌نویس ${uniq()}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${uniq()}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `syd-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ پیش‌نویس', contactPhone: '0700000001' }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const names = ['روغن آفتاب‌گردان', 'برنج سیلا', 'نمک ید دار', 'صابون'];
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-syd' },
    body: { device_id: 'dev-syd', schema_version: 2, ops: names.map((name, i) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: `p${i}`, type: 'insert', fields: { name, unit: 'دانه', salePrice: 10 + i } })) },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (let i = 0; i < names.length; i++) {
    assert.equal((await h.put(`/api/shop/storefront/products/p${i}`, { published: true }, { token: u.accessToken })).status, 200);
  }
  const search = async (q) => {
    resetAll();
    const r = await h.get(`/api/store/${slug}/products?q=${encodeURIComponent(q)}&limit=50`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body.items.map((x) => names[Number(x.id.slice(1))]).sort();
  };
  await setFlag(shopId, sf.SEARCH_FLAG, true);
  assert.deepEqual(await search('تیل'), [], 'پرچمِ مترادف خاموش');
  await setFlag(shopId, syn.FLAG, true);
  assert.deepEqual(await search('تیل'), ['روغن آفتاب‌گردان']);
  assert.deepEqual(await search('وریجې'), ['برنج سیلا']);
  assert.deepEqual(await search('مالګه'), ['نمک ید دار']);
  assert.deepEqual(await search('rice'), ['برنج سیلا']);
  assert.deepEqual(await search('صابون'), ['صابون'], 'بی گروه همان امروز');
});
