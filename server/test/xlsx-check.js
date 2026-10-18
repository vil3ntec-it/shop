'use strict';
/**
 * سنجه‌های کمینه برای فایلِ ‎.xlsx‎ — بی کتابخانه (سرور عمداً وابستگیِ
 * تازه نمی‌گیرد). هم `owner-export.test.js` و هم `web-owner-export.mjs`
 * از همین می‌خوانند.
 *
 *   unzip(bytes)     ⇐ Map<نامِ فایل، متن> — با سنجشِ CRC و اندازه
 *   wellFormed(xml)  ⇐ خطا اگر XML خوش‌ساخت نباشد (برچسبِ بسته‌نشده، & یا < خام…)
 *   sheetRows(xml)   ⇐ ردیف‌ها، هر خانه {ref, t, s, v}
 */
const zlib = require('node:zlib');

function unzip(bytes) {
  const buf = Buffer.from(bytes);
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('پایانِ ZIP پیدا نشد');
  const count = buf.readUInt16LE(eocd + 10);
  const cdSize = buf.readUInt32LE(eocd + 12);
  let p = buf.readUInt32LE(eocd + 16);
  if (p + cdSize !== eocd) throw new Error('فهرستِ مرکزیِ ZIP سرِ جایش نیست');
  const out = new Map();
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('سرآیندِ فهرستِ مرکزی خراب است');
    const method = buf.readUInt16LE(p + 10);
    const crc = buf.readUInt32LE(p + 16);
    const csize = buf.readUInt32LE(p + 20);
    const usize = buf.readUInt32LE(p + 24);
    const nlen = buf.readUInt16LE(p + 28);
    const xlen = buf.readUInt16LE(p + 30);
    const clen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nlen);
    p += 46 + nlen + xlen + clen;
    if (buf.readUInt32LE(local) !== 0x04034b50) throw new Error(`سرآیندِ محلیِ ${name} خراب است`);
    const lnlen = buf.readUInt16LE(local + 26);
    const lxlen = buf.readUInt16LE(local + 28);
    if (buf.toString('utf8', local + 30, local + 30 + lnlen) !== name) throw new Error(`نامِ ${name} در دو سرآیند یکی نیست`);
    const start = local + 30 + lnlen + lxlen;
    const raw = buf.subarray(start, start + csize);
    let data;
    if (method === 0) data = raw;
    else if (method === 8) data = zlib.inflateRawSync(raw);
    else throw new Error(`روشِ فشرده‌سازیِ ناشناخته ${method} در ${name}`);
    if (data.length !== usize) throw new Error(`اندازهٔ ${name} نمی‌خواند`);
    if ((zlib.crc32(data) >>> 0) !== crc) throw new Error(`CRCِ ${name} نمی‌خواند`);
    if (out.has(name)) throw new Error(`${name} دو بار در ZIP است`);
    out.set(name, data.toString('utf8'));
  }
  return out;
}

const BAD_AMP = /&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)/;

function wellFormed(xml) {
  if (!xml.startsWith('<?xml ')) throw new Error('اعلانِ XML نیست');
  const token = /<\?[^?]*\?>|<(\/?)([A-Za-z_][\w:.-]*)((?:\s+[A-Za-z_][\w:.-]*="[^"<]*")*)\s*(\/?)>|([^<]+)/y;
  const stack = [];
  let roots = 0;
  let m;
  token.lastIndex = 0;
  while (token.lastIndex < xml.length) {
    const at = token.lastIndex;
    m = token.exec(xml);
    if (!m) throw new Error(`XML از نویسهٔ ${at} خوش‌ساخت نیست: ${JSON.stringify(xml.slice(at, at + 60))}`);
    if (m[0].startsWith('<?')) continue;
    if (m[5] !== undefined) {
      if (!stack.length && m[5].trim()) throw new Error('متن بیرون از ریشه');
      if (BAD_AMP.test(m[5])) throw new Error(`& خام در متن: ${m[5].slice(0, 40)}`);
      continue;
    }
    if (BAD_AMP.test(m[3] || '')) throw new Error(`& خام در ویژگی‌های <${m[2]}>`);
    const names = [...(m[3] || '').matchAll(/([A-Za-z_][\w:.-]*)="/g)].map((x) => x[1]);
    if (new Set(names).size !== names.length) throw new Error(`ویژگیِ تکراری در <${m[2]}>`);
    if (m[1]) {
      const open = stack.pop();
      if (open !== m[2]) throw new Error(`</${m[2]}> به جای </${open}>`);
    } else if (!m[4]) {
      if (!stack.length) roots++;
      stack.push(m[2]);
    } else if (!stack.length) roots++;
  }
  if (stack.length) throw new Error(`برچسبِ بسته‌نشده: <${stack.pop()}>`);
  if (roots !== 1) throw new Error(`XML باید یک ریشه داشته باشد (${roots})`);
}

function unesc(s) {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&');
}

function sheetRows(xml) {
  const rows = [];
  for (const r of xml.matchAll(/<row r="(\d+)">(.*?)<\/row>/gs)) {
    const cells = [];
    for (const c of r[2].matchAll(/<c r="([A-Z]+\d+)"([^>]*)>(.*?)<\/c>/gs)) {
      const t = (c[2].match(/ t="([^"]+)"/) || [])[1] || 'n';
      const s = (c[2].match(/ s="([^"]+)"/) || [])[1] || '0';
      const v = t === 'inlineStr'
        ? unesc((c[3].match(/<t[^>]*>(.*?)<\/t>/s) || [])[1] || '')
        : (c[3].match(/<v>(.*?)<\/v>/s) || [])[1];
      cells.push({ ref: c[1], t, s, v });
    }
    rows.push(cells);
  }
  return rows;
}

/** نامِ برگه‌ها به ترتیب، از روی ‎xl/workbook.xml‎ */
function sheetNames(files) {
  return [...files.get('xl/workbook.xml').matchAll(/<sheet name="([^"]*)" sheetId="(\d+)" r:id="rId(\d+)"\/>/g)]
    .map((m) => unesc(m[1]));
}

module.exports = { unzip, wellFormed, sheetRows, sheetNames };
