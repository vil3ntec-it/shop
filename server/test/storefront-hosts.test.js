'use strict';
/**
 * نشانی‌های دیگرِ ویترینِ یک دکان (`lib/storefront-hosts.js`، `routes/storefront-hosts.js`، مهاجرتِ ۹۱۲؛ SERVERS §۳).
 *
 *  • سرورِ بی `STOREFRONT_ALIAS_DOMAINS` ⇒ ریشهٔ هر Host ِ ناشناس همان راهِ امروز (پنل) و درِ تنظیم بسته
 *  • پرچمِ دکان خاموش ⇒ `{enabled:false}` و ۴۰۳؛ فقط صاحب می‌نویسد (۴۰۳ ِ owner_only برای شاگرد)
 *  • نامِ سالم فقط زیرِ دامنهٔ گرداننده؛ نشانیِ دکانِ دیگر ⇒ ۴۰۹ بی نامِ آن دکان؛ هر ورودیِ دشمنی ⇒ ۴۰۰
 *  • Host ِ دشمنی: هیچ ریدایرکتِ بیرونی، هیچ نشانیِ ساخته‌شده از Host، فقط برابریِ کاملِ نامِ ثبت‌شده
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const h = require('./helpers');
const config = require('../src/config');
const { query, newId, now } = require('../src/db');
const hosts = require('../src/lib/storefront-hosts');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

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
test.after(async () => { config.storefront.aliasDomains = ''; await h.stop(); });
test.beforeEach(() => { resetAll(); hosts.forget(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ با ویترینِ روشن و اشتراکِ فعال؛ [alias] ⇒ پرچمِ `shop.storefront.host_alias` هم */
async function store(label, { alias = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, user: u };
  for (const key of ['shop.orders', 'shop.storefront']) await setFlag(s.shopId, key, true);
  if (alias) await setFlag(s.shopId, hosts.FLAG, true);
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  s.slug = `hst-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true },
    { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return s;
}

function rawGet(path, host, extra = {}) {
  const u = new URL(h.base());
  return new Promise((resolve, reject) => {
    const req = http.get({ hostname: u.hostname, port: u.port, path, headers: { Host: host, ...extra } }, (res) => {
      res.resume();
      res.on('end', () => resolve(res));
    });
    req.on('error', reject);
  });
}

const put = (s, list) => h.put('/api/shop/storefront/hosts', { hosts: list }, { token: s.token });

test('host-alias: سرورِ بی STOREFRONT_ALIAS_DOMAINS ⇒ ریشه همان پنل، تنظیم بسته، هیچ پرسشی از جدول', async () => {
  config.storefront.aliasDomains = '';
  const s = await store('بی‌دامنه');
  assert.equal(hosts.enabled(), false);
  const r = await put(s, ['shop.example.af']);
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'host_alias_unavailable');
  //  ردیفِ دستی هم بی دامنهٔ گرداننده کار نمی‌کند (جدول خوانده نمی‌شود)
  await query('INSERT INTO storefront_hosts (host, shop_id, created_at) VALUES ($1,$2,$3)', ['shop.example.af', s.shopId, now()]);
  const root = await rawGet('/', 'shop.example.af');
  assert.equal(root.statusCode, 302);
  assert.equal(root.headers.location, '/admin/');
  assert.equal(await hosts.slugOfHost('shop.example.af'), '');
  await query('DELETE FROM storefront_hosts WHERE shop_id=$1', [s.shopId]);
});

test('host-alias: پرچمِ دکان خاموش ⇒ {enabled:false} و ۴۰۳؛ شاگرد ⇒ owner_only', async () => {
  config.storefront.aliasDomains = 'example.af';
  const off = await store('پرچم‌خاموش', { alias: false });
  assert.deepEqual((await h.get('/api/shop/storefront/hosts', { token: off.token })).body, { enabled: false });
  const r = await put(off, ['a.example.af']);
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'storefront_host_alias_disabled');

  const s = await store('صاحب');
  const code = (await h.post('/api/shop/staff-code', {}, { token: s.token })).body.code;
  const staff = await h.newUser('شاگرد', 'shop');
  assert.ok((await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken })).status < 300);
  const st = await h.put('/api/shop/storefront/hosts', { hosts: ['staff.example.af'] }, { token: staff.accessToken });
  assert.equal(st.status, 403);
  assert.equal(st.body.error.code, 'owner_only');
  const view = await h.get('/api/shop/storefront/hosts', { token: staff.accessToken });
  assert.equal(view.status, 200);
  assert.equal(view.body.permissions.manage, false);
  //  بی توکن ⇒ ۴۰۱
  assert.equal((await h.put('/api/shop/storefront/hosts', { hosts: [] })).status, 401);
});

test('host-alias: صاحب نشانی می‌افزاید؛ ریشهٔ همان نشانی ⇒ /store/<slug> ِ همان میزبان؛ یکتا میانِ دکان‌ها', async () => {
  config.storefront.aliasDomains = 'example.af, .Shop2.Example.com.';
  const s = await store('دامنه‌دار');
  const r = await put(s, ['  Shop.Example.AF. ', 'www.shop.example.af', 'shop.example.af']);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.hosts.map((x) => x.host).sort(), ['shop.example.af', 'www.shop.example.af']);
  assert.deepEqual(r.body.domains, ['example.af', 'shop2.example.com']);
  assert.equal(r.body.permissions.manage, true);
  assert.deepEqual(r.body.added.sort(), ['shop.example.af', 'www.shop.example.af']);

  const root = await rawGet('/?c=ABCDEFGHIJ&lang=en&x=%0d%0aSet-Cookie:a', 'shop.example.af');
  assert.equal(root.statusCode, 302);
  assert.equal(root.headers.location, `/store/${s.slug}?c=ABCDEFGHIJ&lang=en`);
  assert.equal(root.headers['cache-control'], 'no-store');
  assert.equal(root.headers['set-cookie'], undefined);
  //  بزرگ و کوچک و نقطهٔ پایانی همان نشانی؛ درگاه بی‌اثر
  assert.equal((await rawGet('/', 'WWW.Shop.Example.AF.:8443')).headers.location, `/store/${s.slug}`);
  //  `/store/<slug>` روی همان میزبان همان ویترین است
  assert.equal((await rawGet(`/store/${s.slug}`, 'shop.example.af')).statusCode, 200);

  //  دکانِ دیگر همان نشانی را نمی‌گیرد — ۴۰۹ بی نامِ دکانِ نخست
  const other = await store('رقیب');
  const taken = await put(other, ['shop.example.af']);
  assert.equal(taken.status, 409);
  assert.equal(taken.body.error.code, 'host_taken');
  assert.ok(!JSON.stringify(taken.body).includes(s.slug));
  assert.ok(!JSON.stringify(taken.body).includes(s.shopId));
  //  برداشتن ⇒ ریشه دوباره همان پنل
  const cleared = await put(s, ['www.shop.example.af']);
  assert.deepEqual(cleared.body.removed, ['shop.example.af']);
  assert.equal((await rawGet('/', 'shop.example.af')).headers.location, '/admin/');
  //  حالا دکانِ دیگر می‌تواند
  assert.equal((await put(other, ['shop.example.af'])).status, 200);
  assert.equal((await rawGet('/', 'shop.example.af')).headers.location, `/store/${other.slug}`);

  //  پرچمِ دکان خاموش شد ⇒ نشانیِ همان دکان دیگر ویترین نیست
  await setFlag(other.shopId, hosts.FLAG, false);
  hosts.forget();
  assert.equal((await rawGet('/', 'shop.example.af')).headers.location, '/admin/');
});

test('host-alias: ورودیِ دشمنی ⇒ ۴۰۰ و هیچ ردیفی؛ نامِ ویترین و نشانیِ خودِ سرور رزرو؛ سقفِ ۵', async () => {
  config.storefront.aliasDomains = 'example.af';
  const s = await store('دشمنی');
  const savedUrl = config.serverUrl;
  const savedPublic = config.publicBaseUrls;
  config.serverUrl = 'https://api.example.af';
  config.publicBaseUrls = 'https://api2.example.af';
  try {
    const bad = {
      'https://shop.example.af': 'host_invalid', 'shop.example.af:443': 'host_invalid', 'shop.example.af/x': 'host_invalid',
      'user@shop.example.af': 'host_invalid', 'shop.example.af\r\nX-Evil: 1': 'host_invalid', 'sh op.example.af': 'host_invalid',
      'a..example.af': 'host_invalid', '-a.example.af': 'host_invalid', 'a_b.example.af': 'host_invalid',
      'еxample.af': 'host_invalid', '127.0.0.1': 'host_invalid', '10.0.0.8': 'host_invalid', '[::1]': 'host_invalid',
      'localhost': 'host_invalid', '': 'host_invalid', [`${'a'.repeat(64)}.example.af`]: 'host_invalid',
      'evil.com': 'host_domain_unknown', 'example.af.evil.com': 'host_domain_unknown', 'notexample.af': 'host_domain_unknown',
      [`${s.slug}.vill3n.top`]: 'host_reserved', 'vill3n.top': 'host_reserved', 'api.vill3n.top': 'host_reserved',
      'api.example.af': 'host_reserved', 'API2.example.af': 'host_reserved',
    };
    for (const [raw, code] of Object.entries(bad)) {
      resetAll();
      const r = await put(s, [raw]);
      assert.equal(r.status, 400, `${JSON.stringify(raw)} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
      assert.equal(r.body.error.code, code, JSON.stringify(raw));
    }
    resetAll();
    for (const body of [{}, { hosts: 'shop.example.af' }, { hosts: [123] }, { hosts: [null] }, { hosts: [{ host: 'a.example.af' }] }]) {
      const r = await h.put('/api/shop/storefront/hosts', body, { token: s.token });
      assert.equal(r.status, 400, JSON.stringify(body));
    }
    const six = ['a', 'b', 'c', 'd', 'e', 'f'].map((x) => `${x}.example.af`);
    assert.equal((await put(s, six)).body.error.code, 'hosts_too_many');
    assert.equal((await query('SELECT count(*)::int AS n FROM storefront_hosts WHERE shop_id=$1', [s.shopId])).rows[0].n, 0);
    //  سقفِ نوشتنِ هر دکان: ۲۰ در ساعت ⇒ بیست‌ویکمی ۴۲۹
    resetAll();
    for (let i = 0; i < 20; i += 1) assert.notEqual((await put(s, [])).status, 429);
    assert.equal((await put(s, [])).status, 429);
  } finally {
    config.serverUrl = savedUrl;
    config.publicBaseUrls = savedPublic;
  }
});

test('host-alias: Host ِ دشمنی هرگز ریدایرکتِ بیرونی نمی‌سازد و ویترینی را جا نمی‌زند', async () => {
  config.storefront.aliasDomains = 'example.af';
  const s = await store('میزبان');
  assert.equal((await put(s, ['host.example.af'])).status, 200);
  const hostile = [
    'evil.com', 'host.example.af.evil.com', 'evil.com.host.example.af', 'host.example.af@evil.com', 'xhost.example.af',
    'host-example.af', '127.0.0.1', '[::1]', `${'a'.repeat(300)}.example.af`, 'host.example.af..', 'host.example.af%2Fevil',
    'host.example.af/../x', 'HOST.EXAMPLE.AF\\evil.com',
  ];
  for (const host of hostile) {
    const r = await rawGet('/', host).catch(() => null);
    if (!r) continue;   //  تجزیه‌گرِ HTTP ِ خودِ node ردش کرد — همان هم امن است
    const loc = String(r.headers.location || '');
    assert.ok(!/^(https?:)?\/\//i.test(loc), `${host} ⇒ ${loc}`);
    assert.notEqual(loc, `/store/${s.slug}`, `${host} نباید ویترین شود`);
  }
  //  X-Forwarded-Host بی TRUST_PROXY نادیده است: Host ِ واقعی همان پنل
  assert.equal((await rawGet('/', 'host.example.af')).headers.location, `/store/${s.slug}`);
  const fwd = await rawGet('/', '127.0.0.1', { 'X-Forwarded-Host': 'host.example.af' });
  assert.equal(fwd.headers.location, '/admin/');
  assert.equal(await hosts.slugOfHost('evil.com'), '');
  assert.equal(await hosts.slugOfHost(''), '');
  assert.equal(await hosts.slugOfHost(null), '');
});

test('host-alias: با اشتراکِ تمام‌شده نوشتن بسته است (SUB-10: PAID)، دیدن باز', async () => {
  config.storefront.aliasDomains = 'example.af';
  const s = await store('منقضی');
  await query('UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1', [s.shopId, Date.now() - 1000]);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * 24 * 60 * 60 * 1000]);
  const r = await put(s, ['late.example.af']);
  assert.equal(r.status, 403);
  assert.match(r.body.error.code, /^subscription_/);
  assert.equal((await h.get('/api/shop/storefront/hosts', { token: s.token })).status, 200);
});
