'use strict';
/**
 * ولایت‌های افغانستان — فهرستِ مرجعِ «نمایش در بازارچه» و شمارشِ منطقه‌ای (S109، §۲۰.۵، §۲۰.۱۱).
 *
 * کلید همان کدِ سه‌حرفیِ ISO 3166-2:AF است (پایدار، بی وابستگی به املا)؛ نام‌ها و املاهای دیگر (دری، پشتو،
 * انگلیسی، املای رایجِ دیگر) فقط برای شناختنِ ورودی‌اند. ورودیِ ناشناس هرگز به نزدیک‌ترین ولایت حدس زده
 * نمی‌شود ⇒ `null` و ۴۰۰. ولسوالی‌ها (حدودِ ۴۰۰، با مرزهای در حالِ تغییر) فهرستِ مرجعِ جدای §۲۰.۵ می‌خواهند؛
 * تا آن متنِ یکدست‌شده‌اند ([cleanDistrict]).
 */
/** همان یکدست‌سازیِ `storefront.normalize` (این پرونده بی وابستگی می‌ماند) */
const normalize = (s) => String(s || '')
  .toLowerCase()
  .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/[ۀة]/g, 'ه').replace(/[أإآ]/g, 'ا')
  .replace(/[\u064b-\u0670\u0640]/g, '')
  .replace(/[\u200c\u200d]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();

const { badRequest } = require('../middleware/errors');

const PROVINCES = Object.freeze([
  ['BDS', 'بدخشان', ['Badakhshan']],
  ['BDG', 'بادغیس', ['Badghis']],
  ['BGL', 'بغلان', ['Baghlan']],
  ['BAL', 'بلخ', ['Balkh']],
  ['BAM', 'بامیان', ['Bamyan', 'Bamiyan']],
  ['DAY', 'دایکندی', ['Daykundi', 'Daikundi', 'دایکُندی']],
  ['FRA', 'فراه', ['Farah']],
  ['FYB', 'فاریاب', ['Faryab']],
  ['GHA', 'غزنی', ['Ghazni', 'غزني']],
  ['GHO', 'غور', ['Ghor']],
  ['HEL', 'هلمند', ['Helmand']],
  ['HER', 'هرات', ['Herat']],
  ['JOW', 'جوزجان', ['Jowzjan', 'Jawzjan']],
  ['KAB', 'کابل', ['Kabul']],
  ['KAN', 'کندهار', ['Kandahar', 'قندهار']],
  ['KAP', 'کاپیسا', ['Kapisa']],
  ['KHO', 'خوست', ['Khost']],
  ['KNR', 'کنر', ['Kunar', 'کونړ']],
  ['KDZ', 'کندز', ['Kunduz', 'قندوز', 'کندوز']],
  ['LAG', 'لغمان', ['Laghman']],
  ['LOG', 'لوگر', ['Logar']],
  ['NAN', 'ننگرهار', ['Nangarhar', 'ننګرهار']],
  ['NIM', 'نیمروز', ['Nimruz', 'Nimroz']],
  ['NUR', 'نورستان', ['Nuristan']],
  ['PKA', 'پکتیکا', ['Paktika']],
  ['PIA', 'پکتیا', ['Paktia', 'Paktiya']],
  ['PAN', 'پنجشیر', ['Panjshir', 'پنجشیر']],
  ['PAR', 'پروان', ['Parwan']],
  ['SAM', 'سمنگان', ['Samangan']],
  ['SAR', 'سرپل', ['Sar-e Pol', 'Sar-e Pul', 'Sar-i-Pul', 'سر پل']],
  ['TAK', 'تخار', ['Takhar']],
  ['URU', 'ارزگان', ['Uruzgan', 'Oruzgan', 'اروزگان']],
  ['WAR', 'وردک', ['Wardak', 'Maidan Wardak', 'میدان وردک']],
  ['ZAB', 'زابل', ['Zabul']],
].map(([code, name, also]) => Object.freeze({ code, name, also: Object.freeze(also) })));

const BY_CODE = new Map(PROVINCES.map((p) => [p.code, p]));
const key = (s) => normalize(s).replace(/[-‐‑ـ\s]+/g, '');
const BY_NAME = new Map(PROVINCES.flatMap((p) => [p.code, p.name, ...p.also].map((n) => [key(n), p])));

/** فهرستِ ثابت برای برنامهٔ دکان (`{code, name}`) — به ترتیبِ الفبای نامِ دری */
const LIST = Object.freeze(
  PROVINCES.map((p) => Object.freeze({ code: p.code, name: p.name })).sort((a, b) => a.name.localeCompare(b.name, 'fa'))
);

/** ورودی (کد یا نام با هر املای شناخته) ⇒ کد؛ تهی ⇒ ''؛ ناشناس ⇒ null */
function codeOf(v) {
  if (v === undefined || v === null) return '';
  const s = String(v).trim();
  if (!s) return '';
  const up = s.toUpperCase();
  if (BY_CODE.has(up)) return up;
  const p = BY_NAME.get(key(s));
  return p ? p.code : null;
}

function nameOf(code) {
  const p = BY_CODE.get(String(code || ''));
  return p ? p.name : '';
}

/** نامِ ولسوالی: بی نویسهٔ کنترلی، حروفِ عربی ⇒ فارسی، فاصله‌های یکدست، حداکثر ۶۰ نویسه */
function cleanDistrict(v) {
  return String(v === undefined || v === null ? '' : v)
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک')
    .replace(/[‌‍]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 60);
}

/** کلیدِ گروه‌بندیِ ولسوالی در شمارش (املای «سر پل» و «سرپل» یکی) */
const districtKey = (v) => key(cleanDistrict(v));

/** پاسخِ «نمایش در بازارچه» ِ دکان (`market.listingView`): ولایت، نامش، ولسوالی و فهرستِ ولایت‌ها برای انتخاب */
function viewOf(row) {
  const province = row && row.province ? String(row.province) : '';
  return { province, provinceName: nameOf(province), district: row && row.district ? String(row.district) : '', provinces: LIST };
}

/**
 * ولایت و ولسوالیِ تازه برای `market.saveListing`: فقط فیلدِ آمده عوض می‌شود. ولایتِ ناشناس ⇒ ۴۰۰ `bad_province`
 * (هرگز حدسِ نزدیک‌ترین)؛ ولسوالیِ غیرِ متنی ⇒ ۴۰۰ `bad_district`.
 */
function inputOf(b, has, cur) {
  const out = { province: cur && cur.province ? String(cur.province) : '', district: cur && cur.district ? String(cur.district) : '' };
  if (has('province')) {
    const code = codeOf(b.province);
    if (code === null) throw badRequest('ولایت در فهرست نیست', 'bad_province');
    out.province = code;
  }
  if (has('district')) {
    if (b.district !== null && typeof b.district !== 'string') throw badRequest('نامِ ولسوالی باید متن باشد', 'bad_district');
    out.district = cleanDistrict(b.district);
  }
  return out;
}

module.exports = { PROVINCES, LIST, codeOf, nameOf, cleanDistrict, districtKey, viewOf, inputOf };
