'use strict';
/**
 * پاک کردنِ metadata ِ JPEG (`chat-media.stripImageMetadata`، همهٔ درهای عکسِ مشتری: چت، مدرکِ شکایت، گزارشِ مشکل، رسانهٔ نشانی):
 * بازبینیِ دستهٔ ۵۵ — هر چه از SOS به بعد بود دست‌نخورده می‌ماند، پس **دادهٔ پس از EOI** (JPEG ِ دومِ MPF با EXIF ِ خودش، ویدیوی
 * Motion Photo با مکان) و APP1 ِ میانِ اسکن‌های progressive به دکان می‌رسید. حالا: تا EOI، و بی APP1/APP13/COM در هیچ جا؛ عکس
 * همان پیکسل‌ها را دارد (رمزگشاییِ واقعی با sharp)، progressive هم.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const sharp = require('sharp');
const { stripImageMetadata } = require('../src/lib/chat-media');

const GPS = Buffer.from('GPSLatitude 34.5553 69.2075', 'latin1');
const app1 = (body) => Buffer.concat([Buffer.from([0xff, 0xe1]), Buffer.from([(body.length + 2) >> 8, (body.length + 2) & 0xff]), body]);

async function photo(progressive) {
  return sharp({ create: { width: 48, height: 32, channels: 3, background: { r: 200, g: 30, b: 90 } } })
    .jpeg({ progressive, quality: 80 }).withMetadata({ exif: { IFD0: { ImageDescription: 'shot at home' } } }).toBuffer();
}
const pixels = async (buf) => (await sharp(buf).raw().toBuffer({ resolveWithObject: true }));

/** شروعِ نشانگرِ SOS ِ شمارهٔ n (پیمایشِ سرآیندها و دادهٔ اسکن) */
function sosAt(buf, n) {
  let i = 2;
  let seen = 0;
  while (i + 4 <= buf.length) {
    const m = buf[i + 1];
    if (m === 0xda && ++seen === n) return i;
    i += 2 + buf.readUInt16BE(i + 2);
    if (m === 0xda) {
      while (!(buf[i] === 0xff && buf[i + 1] !== 0x00 && !(buf[i + 1] >= 0xd0 && buf[i + 1] <= 0xd7))) i++;
    }
  }
  return -1;
}

for (const progressive of [false, true]) {
  test(`${progressive ? 'progressive' : 'baseline'}: دادهٔ پس از EOI (JPEG ِ دوم با EXIF/مکان، ویدیو) دور؛ پیکسل‌ها همان`, async () => {
    const main = await photo(progressive);
    const second = Buffer.concat([await photo(false), GPS]);
    const motion = Buffer.concat([Buffer.from('\0\0\0\x18ftypmp42', 'latin1'), Buffer.from('udta\xa9xyz+34.5553+069.2075/', 'latin1')]);
    const out = stripImageMetadata(Buffer.concat([main, second, motion]), 'image/jpeg');
    assert.equal(out.indexOf(Buffer.from([0xff, 0xd9])), out.length - 2, 'یک EOI و همان پایان');
    for (const leak of ['Exif', 'shot at', 'GPSLatitude', '+34.5553', 'ftyp']) assert.ok(!out.includes(Buffer.from(leak, 'latin1')), leak);
    const [a, b] = [await pixels(main), await pixels(out)];
    assert.deepEqual([b.info.width, b.info.height], [48, 32]);
    assert.ok(a.data.equals(b.data), 'همان پیکسل‌ها');
    assert.ok(out.equals(stripImageMetadata(main, 'image/jpeg')), 'با و بی دنباله یکی');
  });
}

test('progressive: APP1 (EXIF/مکان) ِ میانِ اسکن‌ها هم می‌رود و عکس سالم می‌ماند', async () => {
  const main = await photo(true);
  const at = sosAt(main, 2);
  assert.ok(at > 0, 'اسکنِ دوم');
  const forged = Buffer.concat([main.slice(0, at), app1(Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), GPS])), main.slice(at)]);
  assert.ok((await pixels(forged)).data.length > 0, 'رمزگشا APP1 ِ میانی را می‌پذیرد');
  const out = stripImageMetadata(forged, 'image/jpeg');
  assert.ok(!out.includes(GPS));
  assert.ok((await pixels(out)).data.equals((await pixels(main)).data));
});

test('JPEG ِ بی EOI همان‌قدر که هست می‌ماند؛ FF D9 ِ درونِ سرآیند/جدول با EOI اشتباه نمی‌شود', async () => {
  const main = await photo(false);
  const cut = main.slice(0, main.length - 2);
  assert.equal(stripImageMetadata(cut, 'image/jpeg').slice(-2).toString('hex') === 'ffd9', false);
  //  COM با FF D9 در بدنه (پیش از SOS) ⇒ برداشته می‌شود و عکس کامل می‌ماند
  const com = Buffer.concat([Buffer.from([0xff, 0xfe, 0x00, 0x06]), Buffer.from([0xff, 0xd9, 0xff, 0xd9])]);
  const withCom = Buffer.concat([main.slice(0, 2), com, main.slice(2)]);
  assert.ok((await pixels(stripImageMetadata(withCom, 'image/jpeg'))).data.equals((await pixels(main)).data));
});
