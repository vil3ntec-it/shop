'use strict';
/**
 * صدای مشتری (یادداشتِ صوتیِ نشانی، `lib/address-media.js`) — دو سنجشِ ارزان از روی خودِ بایت، بی رمزگشایی:
 *
 *   hasVideo(buf, mime)     `chat-media.sniff` ظرف را می‌شناسد، نه محتوا را: MP4/3GP ِ `isom`/`mp42`، WebM و Ogg ویدیو هم
 *                           می‌توانند باشند ⇒ ترکِ تصویر (ISO-BMFF: `hdlr` ِ `vide`؛ Matroska: CodecID ِ `V_…`؛ Ogg: Theora)
 *                           یعنی «این صدا نیست» (۴۱۵ ِ همان در).
 *   dropLocation(buf, mime) M4A/3GP: اتمِ مکانِ ضبط (`©xyz` ِ QuickTime، `loci` ِ 3GPP) همان‌جا و با همان اندازه به `free`
 *                           (پخش‌کننده ردش می‌شود) با دادهٔ صفر — هیچ offset ِ `stco`/`co64` جابه‌جا نمی‌شود.
 *                           ⚠️ برچسب‌های دیگر (`©nam`، ID3 ِ MP3) دست نمی‌خورند: پاکِ کاملِ آن‌ها بازنویسیِ `moov` است (STATUS).
 *
 * اتم فقط وقتی پذیرفته است که اندازهٔ ۴ بایتیِ پیشش در مرزِ پرونده بنشیند (نه هر ۴ بایتِ هم‌شکل در دادهٔ صدا).
 */
const BMFF = new Set(['audio/mp4', 'audio/3gpp']);

/** هر جایگاهِ اتمِ [type] با اندازهٔ پذیرفتنی ⇒ `{at, size}` (`at` جای نامِ اتم) */
function atoms(buf, type) {
  const out = [];
  const t = Buffer.from(type, 'latin1');
  for (let i = buf.indexOf(t, 4); i !== -1; i = buf.indexOf(t, i + 4)) {
    const size = buf.readUInt32BE(i - 4);
    if (size >= 8 && i - 4 + size <= buf.length) out.push({ at: i, size });
  }
  return out;
}

//  Matroska/WebM: CodecID (شناسهٔ ‎0x86‎، اندازهٔ یک‌بایتی) با پیشوندِ ‎V_‎ ⇒ ترکِ تصویر
const MKV_VIDEO = /\x86[\x81-\xa0]V_[A-Z0-9]/;

function hasVideo(buf, mime) {
  if (BMFF.has(mime)) return atoms(buf, 'hdlr').some(({ at, size }) => size >= 24 && buf.toString('latin1', at + 12, at + 16) === 'vide');
  if (mime === 'audio/webm') return MKV_VIDEO.test(buf.toString('latin1'));
  if (mime === 'audio/ogg') return buf.includes(Buffer.from('\x80theora', 'latin1'));
  return false;
}

function dropLocation(buf, mime) {
  if (!BMFF.has(mime)) return buf;
  let out = null;
  for (const type of ['\xa9xyz', 'loci']) {
    for (const { at, size } of atoms(buf, type)) {
      out = out || Buffer.from(buf);
      out.write('free', at, 'latin1');
      out.fill(0, at + 4, at - 4 + size);
    }
  }
  return out || buf;
}

module.exports = { hasVideo, dropLocation };
