'use strict';
/**
 * عکسِ QR ِ حساب‌پیِ دکان (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۶، مهاجرتِ ۹۷۰، `lib/hesabpay-qr-image.js`):
 *   ۱. دوباره رمزگذاری: PNG ِ شفاف ⇒ JPEG روی سفید، ضلع ≤ ۱۰۲۴، بی EXIF/GPS؛ WebP، خراب و بزرگ ⇒ ۴۰۰.
 *   ۲. عکس فقط همراهِ متنِ QR ِ همان شماره؛ صندوقِ کارمند هشِ عکسِ فعال را از `/config` و خودِ عکس را از `/qr-image/:hash`
 *      می‌گیرد (هش = sha256 ِ بایت‌ها)؛ دکانِ دیگر و هشِ دیگر ۴۰۴.
 *   ۳. عوض کردنِ عکس = تغییرِ گیرنده: ۲۴ ساعت در انتظار (صندوق و درِ عکس فقط عکسِ کهنه)، سابقه و پوش؛ پس از آن عکسِ تازه و
 *      کهنه پاک. «همان عکس» (`keepQrImage`) با همان متن همان هش را نگه می‌دارد. برداشتن ⇒ هیچ عکس.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const zlib = require('node:zlib');
const h = require('./helpers');
const { one, query, many, newId, now } = require('../src/db');
const push = require('../src/lib/push');
const qr = require('../src/lib/hesabpay-qr-image');
const { crc32 } = require('../src/lib/image-bytes');
const codec = require('../src/lib/image-codec');

const PIN = '258014';
const URL = '/api/v1/shop/hesabpay';
const DAY = 24 * 60 * 60 * 1000;
let admin = '';
const pushes = [];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'hp-qr-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'hp-qr-admin', password: 'Admin!12345' })).body.token;
  push.setDeliver((row, message) => { pushes.push({ shopId: row.shop_id, message }); });
});
test.after(async () => {
  push.setDeliver(null);
  await h.stop();
});

let seq = 0;
async function shop(label) {
  const owner = await h.newUser(`صاحب-qr-${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const f = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'pay.hesabpay', value: true }, { token: admin });
  assert.equal(f.status, 200, JSON.stringify(f.body));
  await push.register({ app: 'shop', token: `push-qr-${shopId}`, userId: owner.user.id, shopId, deviceUid: `qr-${shopId}` });
  const s = { owner, shopId, t: owner.accessToken };
  assert.equal((await h.put(`${URL}/pin`, { newPin: PIN }, { token: s.t })).status, 200);
  return s;
}

async function staff(s) {
  const u = await h.newUser(`شاگرد-qr-${++seq}`);
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), s.shopId, u.user.id, now()]
  );
  return u.accessToken;
}

/** PNG ِ RGBA ِ کوچک: «QR» ِ سیاه روی زمینهٔ **شفاف** (اسکرین‌شاتِ بی‌زمینه) با یک قطعهٔ متنِ فراداده */
function png(w = 48, hgt = 48, { text = 'GPSLatitude 34.5', dark = 0 } = {}) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'latin1'), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td, 0, td.length));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(hgt, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * hgt);
  for (let y = 0; y < hgt; y++) for (let x = 0; x < w; x++) {
    const o = y * (w * 4 + 1) + 1 + x * 4;
    const on = ((x >> 3) + (y >> 3) + dark) % 2 === 0;
    //  خانهٔ روشن = شفاف (rgb صفر!) — بی پهن کردن روی سفید همه سیاه می‌شد
    raw[o] = 0; raw[o + 1] = 0; raw[o + 2] = 0; raw[o + 3] = on ? 255 : 0;
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
    chunk('tEXt', Buffer.from(`Comment\0${text}`, 'latin1')), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]);
}
const b64 = (buf) => buf.toString('base64');
const sha = (buf) => crypto.createHash('sha256').update(buf).digest('hex');
const simple = (extra = {}) => ({ pin: PIN, name: 'دکانِ احمد', mode: 'simple', qr: 'HP|+93799111222|shop', ...extra });
const save = (s, body) => h.put(`${URL}/account`, body, { token: s.t });
const images = async (s) => (await many('SELECT hash FROM hesabpay_qr_images WHERE shop_id=$1 ORDER BY hash', [s.shopId])).map((r) => r.hash);
const elapse = (s) => query('UPDATE shop_hesabpay SET pending_until=$2 WHERE shop_id=$1 AND pending_until > 0', [s.shopId, now() - 1]);

// ════════════════════════════════════════════════════════════════════

test('۱. دوباره رمزگذاری: PNG ِ شفاف ⇒ JPEG روی سفید و بی فراداده؛ بزرگ کوچک می‌شود؛ WebP و خراب ⇒ ۴۰۰', () => {
  const p = qr.prepare(`data:image/png;base64,${b64(png())}`);
  assert.equal(p.mime, 'image/jpeg');
  assert.deepEqual([p.width, p.height], [48, 48]);
  assert.equal(p.hash, sha(p.bytes));
  assert.ok(!p.bytes.includes(Buffer.from('GPS')) && !p.bytes.includes(Buffer.from('Exif')), 'هیچ فراداده‌ای نمی‌ماند');
  //  خانهٔ شفاف روی سفید (نه سیاه): گوشهٔ بالا-چپ سیاه، خانهٔ کناری روشن
  const back = codec.decode(p.bytes, require('../src/lib/image-bytes').inspect(p.bytes));
  const lum = (x, y) => back.rgb[(y * back.w + x) * 3];
  assert.ok(lum(2, 2) < 60 && lum(12, 2) > 200, `سیاه ${lum(2, 2)} / روشن ${lum(12, 2)}`);
  //  ضلعِ بلند ≤ ۱۰۲۴
  const big = qr.prepare(b64(png(1300, 650)));
  assert.deepEqual([big.width, big.height], [1024, 512]);
  for (const [raw, code] of [
    ['', 'hesabpay_qr_image_bad'],
    ['@@not-base64@@', 'hesabpay_qr_image_bad'],
    [b64(Buffer.from('RIFF\x24\x00\x00\x00WEBPVP8 \x18\x00\x00\x00\x30\x01\x00\x9d\x01\x2a\x10\x00\x10\x00\x00\x00\x00\x00\x00\x00\x00\x00', 'latin1')), 'hesabpay_qr_image_format'],
    [b64(Buffer.from('this is not an image at all, sorry')), 'image_type'],
    ['A'.repeat(2_200_000), 'hesabpay_qr_image_too_big'],
  ]) {
    assert.throws(() => qr.prepare(raw), (e) => e.code === code || e.status === 400 && (e.code || '').startsWith('image'), code);
  }
});

test('۲. عکس فقط همراهِ متنِ QR ِ همان شماره؛ صندوقِ کارمند همان عکس را می‌گیرد؛ دکانِ دیگر و هشِ دیگر ۴۰۴', async () => {
  const s = await shop('عکس');
  //  عکس بی متنِ خوانده‌شده، یا با QR ِ کسِ دیگر ⇒ هیچ ذخیره (و تلاشِ رمز نمی‌سوزد)
  assert.equal((await save(s, simple({ qr: '', account: '0799111222', qrImage: b64(png()) }))).body.error.code, 'hesabpay_qr_image_unread');
  assert.equal((await save(s, simple({ account: '0700999888', qrImage: b64(png()) }))).body.error.code, 'hesabpay_qr_mismatch');
  assert.equal((await save(s, simple({ qrPng: b64(png()) }))).body.error.code, 'hesabpay_qr_image');
  assert.deepEqual(await images(s), []);
  assert.equal((await one('SELECT pin_failures FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).pin_failures, 0);

  const r = await save(s, simple({ qrImage: b64(png()) }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.match(r.body.qrImage, /^[0-9a-f]{64}$/);
  assert.deepEqual(await images(s), [r.body.qrImage]);
  const set = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.account_set'`, [s.shopId]);
  assert.deepEqual([set.detail.qr, set.detail.qrImage, set.detail.immediate], [true, true, true]);

  const cashier = await staff(s);
  const cfg = (await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay;
  assert.deepEqual(cfg.simple, { account: '0799111222', qrPayload: 'HP|+93799111222|shop', qrImage: r.body.qrImage });
  const img = await h.get(`/api/v1/shop/payments/qr-image/${r.body.qrImage}`, { token: cashier });
  assert.equal(img.status, 200, JSON.stringify(img.body));
  assert.equal(img.body.mime, 'image/jpeg');
  assert.equal(sha(Buffer.from(img.body.data, 'base64')), r.body.qrImage, 'هشِ نشانی همان بایت‌هاست');
  assert.equal((await h.get(`/api/v1/shop/payments/qr-image/${'0'.repeat(64)}`, { token: cashier })).status, 404);
  assert.equal((await h.get('/api/v1/shop/payments/qr-image/..%2Fetc', { token: cashier })).status, 404);
  const other = await shop('دیگر');
  assert.equal((await h.get(`/api/v1/shop/payments/qr-image/${r.body.qrImage}`, { token: other.t })).status, 404, 'دکانِ دیگر عکسِ این دکان را نمی‌بیند');
});

test('۳. عوض کردنِ عکس = تغییرِ گیرنده: ۲۴ ساعت در انتظار، صندوق فقط عکسِ کهنه، سابقه و پوش؛ سپس تازه و کهنه پاک', async () => {
  const s = await shop('عوض');
  const first = (await save(s, simple({ qrImage: b64(png()) }))).body.qrImage;
  const cashier = await staff(s);
  //  «همان عکس» با همان متن ⇒ همان هش (نام عوض شد؛ باز هم ۲۴ ساعت مثلِ هر تغییرِ پس از نخستین)
  const kept = await save(s, simple({ name: 'دکانِ احمد و پسران', keepQrImage: true }));
  assert.equal(kept.status, 200, JSON.stringify(kept.body));
  assert.equal((await one('SELECT pending FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).pending.qr_image, first);
  //  عکسِ تازه ⇒ جای حسابِ در انتظارِ پیشین
  const r = await save(s, simple({ qrImage: b64(png(48, 48, { dark: 1 })) }));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.qrImage, first, 'تا فعال شدن عکسِ فعال همان کهنه است');
  assert.ok(r.body.pending && r.body.pending.qrImage === true && r.body.pending.until >= now() + DAY - 60_000, JSON.stringify(r.body.pending));
  const next = (await one('SELECT pending FROM shop_hesabpay WHERE shop_id=$1', [s.shopId])).pending.qr_image;
  assert.notEqual(next, first);
  assert.deepEqual(await images(s), [first, next].sort());
  assert.equal((await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay.simple.qrImage, first);
  assert.equal((await h.get(`/api/v1/shop/payments/qr-image/${next}`, { token: cashier })).status, 404, 'عکسِ در انتظار هرگز به صندوق نمی‌رسد');
  const pend = await one(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.change_pending' ORDER BY created_at DESC LIMIT 1`, [s.shopId]);
  assert.equal(pend.detail.qrImage, true);
  assert.ok(pushes.some((p) => p.shopId === s.shopId && p.message.data.kind === 'change_pending'));

  await elapse(s);
  assert.equal((await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay.simple.qrImage, next);
  assert.equal((await h.get(`/api/v1/shop/payments/qr-image/${next}`, { token: cashier })).status, 200);
  assert.deepEqual(await images(s), [next], 'عکسِ کهنه پس از فعال شدنِ تازه پاک');

  //  برداشتنِ حساب ⇒ هیچ عکس و هیچ QR در صندوق
  assert.equal((await h.post(`${URL}/account/remove`, { pin: PIN }, { token: s.t })).status, 200);
  assert.deepEqual(await images(s), []);
  assert.equal((await h.get('/api/v1/shop/payments/config', { token: cashier })).body.hesabpay.simple, undefined);
  assert.equal((await h.get(`/api/v1/shop/payments/qr-image/${next}`, { token: cashier })).status, 404);
});
