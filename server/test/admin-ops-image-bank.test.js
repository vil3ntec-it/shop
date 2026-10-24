'use strict';
/**
 * پنلِ بانکِ تصاویر — دو مسیرِ افزوده روی `routes/admin-image-bank.js`.
 *
 *   ۱) GET /status: فقط مدیر؛ با پرچمِ خاموش ۲۰۰ و `enabled: false` (نه ۴۰۳)،
 *      تا پنل پیامِ روشن بدهد؛ بقیهٔ مسیرها همچنان ۴۰۳.
 *   ۲) GET /batches: دسته‌های اخیر با شمارِ فایل و ردیف (سالم، خراب، ناموفق،
 *      مبهم) — پیشرفتِ دسته برای IMG-01 / IMG-BANK-01؛ تازه‌ترین بالا.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, newId, now } = require('../src/db');

let admin = '';
let seed = 0;
const AD = (method, path, body) => h.api(method, '/api/admin/image-bank' + path, { token: admin, body });
const ADR = (method, path, buf) => h.raw(method, '/api/admin/image-bank' + path, buf, { token: admin });

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

test('status: فقط مدیر؛ پرچمِ خاموش ⇒ ۲۰۰ و enabled=false، بقیه ۴۰۳', async () => {
  assert.equal((await h.get('/api/admin/image-bank/status')).status, 401);
  const u = await h.newUser('دکان‌دار', 'shop');
  assert.ok([401, 403].includes((await h.get('/api/admin/image-bank/status', { token: u.accessToken })).status));
  const off = await AD('GET', '/status');
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal(off.body.enabled, false);
  assert.equal(off.headers.get('cache-control'), 'no-store');
  assert.equal((await AD('GET', '/batches')).status, 403);
  assert.equal((await AD('GET', '/queue')).status, 403);

  const set = await h.put('/api/admin/pump/live-config', { scope: 'all', key: 'image_bank.central', value: true }, { token: admin });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  const on = await AD('GET', '/status');
  assert.equal(on.body.enabled, true);
  assert.equal(on.body.rows_max, 1000);
  assert.deepEqual(on.body.licenses, ['unknown', 'licensed', 'restricted', 'denied']);
});

test('batches: شمارِ فایل و ردیف به‌ازای هر دسته، تازه‌ترین بالا', async () => {
  const first = await AD('POST', '/batches', { license_source: 'نخست', license_status: 'unknown' });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const b = await AD('POST', '/batches', { license_source: 'کاتالوگ', license_status: 'licensed', commercial_use: true });
  const id = b.body.id;
  for (const n of ['a.jpg', 'b.jpg']) {
    assert.equal((await ADR('PUT', `/batches/${id}/files/${n}`, F.jpeg(800, 600, { seed: ++seed }))).body.status, 'ok');
  }
  assert.equal((await ADR('PUT', `/batches/${id}/files/fake.jpg`, Buffer.from('MZ' + 'x'.repeat(400)))).body.status, 'failed');
  const csv = [
    'sku,barcode,name,brand,size,unit,image_path,license_status',
    'S-1,,کالای یک,برند,1,g,a.jpg,',
    'S-2,,کالای دو,برند,2,g,b.jpg,',
    'S-3,,کالای خراب,برند,3,g,fake.jpg,',
    'S-4,,کالای بی فایل,برند,4,g,none.jpg,',
  ].join('\n');
  const run = await AD('POST', `/batches/${id}/rows`, { csv });
  assert.equal(run.status, 200, JSON.stringify(run.body).slice(0, 300));
  assert.deepEqual(run.body.counts, { ok: 2, failed: 2 });

  const list = await AD('GET', '/batches');
  assert.equal(list.status, 200);
  assert.deepEqual(list.body.items.map((x) => x.id), [id, first.body.id]);
  const row = list.body.items[0];
  assert.deepEqual(
    [row.status, row.license_source, row.license_status, row.commercial_use, row.files, row.files_failed, row.rows, row.rows_failed, row.rows_ambiguous],
    ['open', 'کاتالوگ', 'licensed', true, 3, 1, 4, 2, 0]
  );
  assert.ok(row.created_at > 0);
  assert.deepEqual([list.body.items[1].files, list.body.items[1].rows], [0, 0]);
});

test('review: صف با دلیلِ تطبیق، کالاهای ممکن با اندازه، تکراری و نشانیِ فایل (IMG-02، IMG-PRO-07، IMG-BANK-17، IMG-09، IMG-BANK-13)', async () => {
  for (const size of ['1', '5']) {
    const p = await AD('POST', '/products', { name: 'روغن آفتاب', brand: 'زر', size_value: size, size_unit: 'l', sku: `OIL-${size}` });
    assert.equal(p.status, 201, JSON.stringify(p.body));
  }
  const b = await AD('POST', '/batches', { license_source: 'کاتالوگ', license_status: 'licensed', commercial_use: true });
  const id = b.body.id;
  const same = F.jpeg(800, 600, { seed: ++seed });
  await ADR('PUT', `/batches/${id}/files/oil.jpg`, same);
  await ADR('PUT', `/batches/${id}/files/oil-copy.jpg`, same);
  await ADR('PUT', `/batches/${id}/files/soap.jpg`, F.jpeg(800, 600, { seed: ++seed }));
  const csv = ['sku,barcode,name,brand,size,unit,image_path,license_status',
    ',,روغن آفتاب,زر,,,oil.jpg,', 'SOAP-1,,صابون گل,گل,100,g,soap.jpg,', ',,روغن آفتاب,زر,,,oil-copy.jpg,'].join('\n');
  const run = await AD('POST', `/batches/${id}/rows`, { csv });
  assert.deepEqual(run.body.counts, { ambiguous: 2, ok: 1 });

  const r = await AD('GET', '/review?status=NEEDS_REVIEW&space=pending');
  assert.equal(r.status, 200, JSON.stringify(r.body).slice(0, 300));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const byRow = Object.fromEntries(run.body.rows.map((x) => [x.row_no, x.image_id]));
  const item = (rowNo) => r.body.items.find((x) => x.id === byRow[rowNo]);
  //  مبهم: وصل نیست، دلیل «چند کالای هم‌نام»، دو کالای ممکن با اندازه و SKU
  const oil = item(2);
  assert.equal(oil.match_status, 'ambiguous');
  assert.deepEqual(oil.match_reasons, ['name_many']);
  assert.equal(oil.product, null);
  assert.deepEqual(oil.candidates.map((c) => `${c.size_value}${c.size_unit}:${c.sku}`).sort(), ['1l:OIL-1', '5l:OIL-5']);
  assert.match(oil.file.url, /^\/api\/admin\/image-bank\/file\/[^/]+\/1\/original$/);
  assert.equal(oil.license.status, 'licensed');
  //  همان فایل دوباره ⇒ فقط نشانِ تکراری (IMG-BANK-13)
  assert.deepEqual(item(4).duplicate_of, [oil.id]);
  //  کالای تازه: وصل، با SKU
  const soap = item(3);
  assert.equal(soap.product.sku, 'SOAP-1');
  assert.deepEqual(soap.match_reasons, ['new_product']);
  assert.deepEqual(soap.candidates, []);
  //  فایلِ همان نشانی واقعاً سرو می‌شود
  const file = await h.download(oil.file.url, { token: admin });
  assert.equal(file.status, 200);
});
