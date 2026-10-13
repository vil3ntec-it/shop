'use strict';
/**
 * عکس و PDF از خودِ برنامهٔ پمپ برای بات — lib/station-reports.js و دو در.
 *
 * خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۲۱): «همان عکس‌های برنامه… PDFِ حساب… در
 * سرور ذخیره نشود.»
 *
 *   ۱) درخواستِ بات روی همان پاسخِ `/rate` به برنامهٔ **همان** پمپ می‌رسد، نه پمپِ دیگر.
 *   ۲) اتصالِ بازِ `?wait=` با درخواستِ تازه همان لحظه جواب می‌گیرد.
 *   ۳) جوابِ برنامه (PNG/PDF) عیناً به بات می‌رسد و چیزی در دیتابیس نمی‌ماند.
 *   ۴) فایلِ بدشکل، نوعِ ناجور و پمپِ دیگر پذیرفته نمی‌شوند.
 *   ۵) کامپیوترِ خاموش ⇒ «جواب نداد»، و صف سقف دارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const reports = require('../src/lib/station-reports');

test.before(async () => { await h.start(); });
test.after(async () => { reports._reset(); await h.stop(); });

async function pump(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: `pc-rq-${name}`, name: 'کامپیوتر', platform: 'windows' } }, { token: u.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  return { id: made.body.station.id, dev: bound.body.deviceToken };
}

const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(64, 1)]);
const PDF = Buffer.from('%PDF-1.7\n% test\n');

test('۱) درخواست فقط به برنامهٔ همان پمپ می‌رسد، و فقط یک بار', async () => {
  const a = await pump('یک');
  const b = await pump('دو');
  const p = reports.request(a.id, 'tanks', {});
  p.catch(() => {});
  const other = await h.get('/api/pump/device/rate', { token: b.dev });
  assert.deepEqual(other.body.reqs, [], 'پمپِ دیگر درخواستِ این پمپ را نمی‌بیند');
  const mine = await h.get('/api/pump/device/rate', { token: a.dev });
  assert.equal(mine.status, 200);
  assert.equal(mine.body.reqs.length, 1);
  assert.equal(mine.body.reqs[0].kind, 'tanks');
  assert.ok('cmd' in mine.body, 'پاسخِ قدیمیِ /rate سرِ جایش است');
  const again = await h.get('/api/pump/device/rate', { token: a.dev });
  assert.deepEqual(again.body.reqs, [], 'همان درخواست دو بار داده نمی‌شود');
  //  پمپِ دیگر نمی‌تواند به جای او جواب بدهد
  const forged = await h.post(`/api/pump/device/report/${mine.body.reqs[0].id}`,
    { ok: true, files: [{ name: 'x', mime: 'image/png', b64: PNG.toString('base64') }] }, { token: b.dev });
  assert.equal(forged.status, 404);
  reports._reset();
});

test('۲) اتصالِ بازِ ?wait= همان لحظهٔ درخواست جواب می‌گیرد', async () => {
  const a = await pump('درجا');
  const t0 = Date.now();
  const polling = h.get('/api/pump/device/rate?wait=10', { token: a.dev });
  await new Promise((r) => setTimeout(r, 300));
  const p = reports.request(a.id, 'debtor', { name: 'هارون' });
  p.catch(() => {});
  const r = await polling;
  assert.ok(Date.now() - t0 < 5000, 'باید پیش از پایانِ مهلت برگردد');
  assert.equal(r.body.reqs.length, 1);
  assert.deepEqual(r.body.reqs[0].arg, { name: 'هارون' });
  reports._reset();
});

test('۳) جوابِ برنامه عیناً به بات می‌رسد — PNG و PDF، بی ذخیره', async () => {
  const a = await pump('جواب');
  const p = reports.request(a.id, 'debtor', { name: 'هارون' });
  const { reqs } = (await h.get('/api/pump/device/rate', { token: a.dev })).body;
  const up = await h.post(`/api/pump/device/report/${reqs[0].id}`, {
    ok: true, caption: 'هارون',
    files: [
      { name: 'هارون', mime: 'image/png', b64: PNG.toString('base64') },
      { name: 'حسابِ هارون', mime: 'application/pdf', b64: PDF.toString('base64') },
    ],
    extra: { qr: 'https://yaqobipump.top/view/#s=1' },
  }, { token: a.dev });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  const out = await p;
  assert.equal(out.files.length, 2);
  assert.ok(out.files[0].data.equals(PNG), 'عکس بایت‌به‌بایت همان است');
  assert.equal(out.files[0].name, 'هارون.png');
  assert.equal(out.files[1].name, 'حسابِ هارون.pdf');
  assert.equal(out.extra.qr, 'https://yaqobipump.top/view/#s=1');
  const twice = await h.post(`/api/pump/device/report/${reqs[0].id}`, { ok: true, files: [] }, { token: a.dev });
  assert.equal(twice.status, 404, 'جوابِ دوم به درخواستِ بسته‌شده نمی‌رسد');
});

test('۴) فایلِ بدشکل یا نوعِ ناجور پذیرفته نمی‌شود؛ «نشد»ِ برنامه به بات می‌رسد', async () => {
  const a = await pump('بد');
  const p = reports.request(a.id, 'tanks', {});
  const caught = p.then(() => null, (e) => e);
  const { reqs } = (await h.get('/api/pump/device/rate', { token: a.dev })).body;
  const id = reqs[0].id;
  const lie = await h.post(`/api/pump/device/report/${id}`,
    { ok: true, files: [{ name: 'x', mime: 'application/pdf', b64: PNG.toString('base64') }] }, { token: a.dev });
  assert.equal(lie.status, 400, 'PNG با برچسبِ PDF');
  const exe = await h.post(`/api/pump/device/report/${id}`,
    { ok: true, files: [{ name: 'x.exe', mime: 'application/octet-stream', b64: PNG.toString('base64') }] }, { token: a.dev });
  assert.equal(exe.status, 400);
  const fail = await h.post(`/api/pump/device/report/${id}`, { ok: false, error: 'حساب پیدا نشد' }, { token: a.dev });
  assert.equal(fail.status, 200);
  const e = await caught;
  assert.ok(e && e.code === 'failed' && /پیدا نشد/.test(e.message), String(e));
});

test('۵) کامپیوترِ خاموش ⇒ «جواب نداد»؛ صف سقف دارد', async () => {
  const a = await pump('خاموش');
  await assert.rejects(reports.request(a.id, 'tanks', {}, { timeoutMs: 1000 }), (e) => e.code === 'timeout');
  const open = [];
  for (let i = 0; i < reports.MAX_OPEN; i++) { const q = reports.request(a.id, 'tanks', {}); q.catch(() => {}); open.push(q); }
  await assert.rejects(reports.request(a.id, 'tanks', {}), (e) => e.code === 'busy');
  await assert.rejects(reports.request(a.id, 'nope', {}), (e) => e.code === 'bad_kind');
  reports._reset();
});
