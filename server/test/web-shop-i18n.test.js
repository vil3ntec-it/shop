'use strict';
/**
 * چهار زبانِ نسخهٔ وبِ دکان (`index.html` + `license/`) — فارسی، دری، انگلیسی، پشتو — بی مرورگر و بی پایگاه.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پوشش: هر متنِ فارسیِ `index.html` (برچسب، صفت، و رشته‌های اسکریپتِ درون‌خطی) و هر رشتهٔ فارسیِ `license/*.js`
 *      یا کلیدِ واژه‌نامهٔ صفحه است (`license/i18n/web.fa.json`) یا «داده»: در `license/*.js` با نشانِ «// داده: دلیل» روی
 *      همان سطر یا بلوکِ «// <داده: …>» … «// </داده>»، و در index.html ِ یخ‌زده در `web.data.json`. یعنی **هیچ فارسیِ
 *      خامِ تازه‌ای** در license/ نمی‌آید: یا ترجمه دارد یا داده است. پرونده‌های تازه (`web-i18n.js`، `thermal-print.js`)
 *      هیچ فارسیِ بی‌نشان ندارند.
 *   ۲) واژه‌نامه: fa (فهرستِ کلیدها) و en و ps هم‌کلید؛ جای‌خالی‌های {n} در هر سه یکی؛ انگلیسی بی حرفِ فارسی؛ هیچ متنِ خالی؛
 *      هیچ کلیدِ کهنه (کلیدی که دیگر در صفحه یا license نیست).
 *   ۳) فاکتور: `receipt.<زبان>.json` همان StrReceipt.kt است (`scripts/receipt-labels.js`)؛ هر برچسبِ `receipt.web.json`
 *      در index.html هست و به کلیدی از StrReceipt (یا واژه‌نامهٔ صفحه) می‌رسد.
 *   ۴) دری: `license/dari.js` رونوشتِ بایت‌به‌بایتِ `server/public/kit/dari.js` (همان DariText.kt).
 *   ۵) CSP بدتر نمی‌شود: CSP ِ index.html همان؛ پرونده‌های تازه بی eval، Function، رویدادِ درون‌خطی، `javascript:` و صفتِ style.
 *   ۶) سرویس‌ورکر لایه و واژه‌نامه‌های کوچک را پیش‌کش دارد (واژه‌نامهٔ بزرگ با نخستین بارِ همان زبان کش می‌شود).
 *   ۷) رسیدِ قرض (واتس‌اپ/کپی، `debt-book.js`): فارسی بایت‌به‌بایت همان؛ هر متنِ ثابتش در بخشِ `debt` ِ receipt.web.json؛ در
 *      انگلیسی و پشتو برچسب از StrReceipt/واژه‌نامه و نام، شرح، واحد و مبلغ دست‌نخورده.
 *   ۸) ظرف‌های دادهٔ لایه (`DATA_SEL`) هنوز در index.html هستند؛ تقویمِ نمایش در lang.<زبان>.json.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { scan, HOLE } = require('./web-literals');
const R = require('../scripts/receipt-labels');

const ROOT = path.join(__dirname, '..', '..');
const read = (f) => fs.readFileSync(path.join(ROOT, f), 'utf8');
const json = (f) => JSON.parse(read(`license/i18n/${f}`));
/** حرفِ فارسی/عربی/پشتو (نه رقم، نه «؋ ٪ ٬ ٫ ، ؟») */
const LETTER = /[\u0621-\u064A\u066E-\u06D3\u06D5\u06FA-\u06FF]/;
const ENT = { amp: '&', lt: '<', gt: '>', quot: '"', apos: '\'', nbsp: ' ', zwnj: '\u200C', rlm: '\u200F', lrm: '\u200E', hellip: '…', mdash: '—', ndash: '–', laquo: '«', raquo: '»', times: '×', larr: '←', rarr: '→' };
const dec = (s) => s.replace(/&(#x?[0-9a-f]+|\w+);/gi, (m, e) => (e[0] === '#'
  ? String.fromCodePoint(/^#x/i.test(e) ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10))
  : (ENT[e.toLowerCase()] ?? m)));
const norm = (s) => s.replace(/[\s\u00a0]+/g, ' ').trim();
const renumber = (s) => { let i = 0; return s.replace(new RegExp(HOLE, 'g'), () => `{${i++}}`); };

/** تکه‌های متنیِ یک رشته: بیرون از برچسب‌های HTML، و مقدارِ صفت‌های دیدنی درونِ برچسب */
function segments(text) {
  const out = [];
  const attr = /\s(?:placeholder|title|aria-label|alt|data-tip)\s*=\s*(?:"([^"]*)"|'([^']*)')/g;
  let t = text;
  //  تکه‌ای از الحاق که وسطِ برچسب شروع یا تمام می‌شود: `…متن">` (ته‌ِ مقدارِ صفت) و `<b title="متن…` (سرِ آن)
  const tail = /^([^<>]*?)["']\s*\/?>/.exec(t);
  if (tail) { out.push(tail[1]); t = t.slice(tail[0].length); }
  const head = /<[^<>]*$/.exec(t) || /\s[\w-]+\s*=\s*["'][^"'<>]*$/.exec(t);
  if (head) { const v = /=\s*["']([^"']*)$/.exec(head[0]); if (v) out.push(v[1]); t = t.slice(0, head.index); }
  t.replace(/<[^>]*>/g, (tag) => { for (const m of tag.matchAll(attr)) out.push(m[1] ?? m[2]); return ''; });
  out.push(...t.split(/<[^>]*>/));
  return out.map((s) => renumber(norm(dec(s)))).filter((s) => LETTER.test(s));
}

const FA = json('web.fa.json');
const EN = json('web.en.json');
const PS = json('web.ps.json');
const KEYS = new Set(FA);
const DATA = json('web.data.json')['index.html'];
/** هر کلید به تکه‌های بی‌جای‌خالی؛ برای رشته‌ای که تکه‌ای از الحاق (`'…' + x`) است */
const CHUNKS = `\u0001${FA.flatMap((k) => k.split(/\{\d+\}/).map(norm)).filter(Boolean).join('\u0001')}\u0001`;

/** رشته‌های بی‌پوشش و بی‌نشانِ یک اسکریپت */
function uncovered(src, where, extraData = {}) {
  const { strings, dataLines } = scan(src);
  const miss = [];
  for (const s of strings) {
    if (!LETTER.test(s.text) || dataLines.has(s.line) || s.key || s.console) continue;
    for (const seg of segments(s.text)) {
      if (KEYS.has(seg) || Object.hasOwn(extraData, seg)) continue;
      if (s.plus && seg.split(/\{\d+\}/).map(norm).filter((c) => LETTER.test(c)).every((c) => CHUNKS.includes(c))) continue;
      miss.push(`${where}:${s.line}: ${seg}`);
    }
  }
  return miss;
}

const html = read('index.html');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
const markup = html.replace(/<script[\s\S]*?<\/script>/g, '').replace(/<style[\s\S]*?<\/style>/g, '').replace(/<!--[\s\S]*?-->/g, '');
const LICENSE = fs.readdirSync(path.join(ROOT, 'license')).filter((f) => f.endsWith('.js') && f !== 'dari.js');

test('۱) هر متنِ دیدنیِ index.html (برچسب و صفت) کلیدِ واژه‌نامه یا داده است', () => {
  const miss = segments(markup).filter((s) => !KEYS.has(s) && !Object.hasOwn(DATA, s));
  assert.deepEqual(miss, [], `بی ترجمه:\n${miss.join('\n')}`);
});

test('۱) هر رشتهٔ فارسیِ اسکریپتِ درون‌خطیِ index.html ترجمه دارد یا داده است', () => {
  assert.ok(scripts.length >= 1 && scripts.some((s) => s.length > 100000), 'اسکریپتِ بزرگِ صفحه پیدا نشد');
  const miss = scripts.flatMap((s, i) => uncovered(s, `index.html#${i}`, DATA));
  assert.deepEqual(miss, [], `بی ترجمه:\n${miss.slice(0, 80).join('\n')}`);
});

test('۱) هیچ فارسیِ خامِ تازه در license/: هر رشته ترجمه دارد یا «// داده:» است', () => {
  assert.ok(LICENSE.length > 30);
  const miss = LICENSE.flatMap((f) => uncovered(read(`license/${f}`), `license/${f}`));
  assert.deepEqual(miss, [], `فارسیِ خامِ بی‌نشان:\n${miss.slice(0, 80).join('\n')}`);
});

test('۱) پرونده‌های تازهٔ زبان و چاپ هیچ فارسیِ بی‌نشان ندارند (همه از واژه‌نامه)', () => {
  for (const f of ['license/web-i18n.js', 'license/thermal-print.js']) {
    const { strings, dataLines } = scan(read(f));
    const raw = strings.filter((s) => LETTER.test(s.text) && !dataLines.has(s.line)).map((s) => `${s.line}: ${s.text}`);
    assert.deepEqual(raw, [], f);
  }
});

test('۱) نشانِ «داده» فقط کنارِ رشتهٔ فارسی است (نشانِ بیهوده پوششِ دروغ نمی‌سازد)', () => {
  for (const f of LICENSE) {
    const { strings, dataLines } = scan(read(`license/${f}`));
    const lines = new Set(strings.filter((s) => /[\u0600-\u06FF]/.test(s.text)).map((s) => s.line));
    const src = read(`license/${f}`).split('\n');
    const lone = [...dataLines].filter((l) => !lines.has(l) && /\/\/ داده:|\/\* داده:/.test(src[l - 1] || ''));
    assert.deepEqual(lone, [], `${f}: نشانِ داده بی رشته`);
  }
});

test('۲) واژه‌نامهٔ صفحه: fa و en و ps هم‌کلید، با همان جای‌خالی‌ها؛ en بی حرفِ فارسی؛ ps فقط گاهی همان فارسی', () => {
  assert.ok(FA.length > 1500, `کلیدها: ${FA.length}`);
  assert.deepEqual(FA, [...FA].sort(), 'web.fa.json مرتب است');
  assert.equal(new Set(FA).size, FA.length, 'کلیدِ تکراری');
  assert.deepEqual(Object.keys(EN).sort(), FA);
  assert.deepEqual(Object.keys(PS).sort(), FA);
  const holes = (s) => (s.match(/\{\d+\}/g) || []).sort().join();
  for (const k of FA) {
    assert.ok(LETTER.test(k), `کلید بی حرف: ${k}`);
    assert.equal(norm(k), k, `کلید باید فاصلهٔ یکسان‌شده داشته باشد: ${k}`);
    for (const [l, d] of [['en', EN], ['ps', PS]]) {
      assert.ok(typeof d[k] === 'string' && d[k].trim(), `${l}: خالی: ${k}`);
      assert.equal(holes(d[k]), holes(k), `${l}: جای‌خالی: ${k} ⇐ ${d[k]}`);
    }
    assert.ok(!LETTER.test(EN[k].replace(/«[^»]*»|“[^”]*”/g, '')) || /\{\d+\}/.test(k), `en: فارسی مانده: ${k} ⇐ ${EN[k]}`);
  }
  const same = FA.filter((k) => PS[k] === k);
  assert.ok(same.length < FA.length * 0.05, `پشتو بیش از اندازه همان فارسی است (${same.length})`);
});

test('۲) هیچ کلیدِ کهنه: هر کلید هنوز در index.html یا license/ یا واژه‌نامهٔ انتقالِ موجودی هست', () => {
  const hay = [html, ...LICENSE.map((f) => read(`license/${f}`)), read('license/i18n/transfers.fa.json')]
    .map((s) => norm(dec(s.replace(/(['"`])\s*\+\s*(['"`])/g, '')))).join('\n');
  const intl = json('web.data.json').intl;
  const stale = FA.filter((k) => !Object.hasOwn(intl, k) && !k.split(/\{\d+\}/).map(norm).filter((c) => LETTER.test(c)).every((c) => hay.includes(c)));
  assert.deepEqual(stale, [], 'کلیدی که دیگر در صفحه نیست');
});

test('۳) برچسب‌های فاکتور همان StrReceipt.kt ِ برنامه است (scripts/receipt-labels.js)', () => {
  const want = R.build();
  for (const l of R.LANGS) assert.deepEqual(json(`receipt.${l}.json`), want[l], `receipt.${l}.json — node scripts/receipt-labels.js --write`);
  assert.ok(want.fa.subtotal && want.en.total === 'Total' && want.ps.footer, 'چند برچسبِ پایه');
});

test('۳) نقشهٔ برچسبِ صفحه ⇐ StrReceipt: هر برچسب در index.html (رسیدِ قرض: debt-book.js) هست و کلیدش در StrReceipt', () => {
  const map = json('receipt.web.json');
  const labels = R.build().fa;
  const page = norm(dec(html));
  const debtSrc = norm(read('license/debt-book.js'));
  for (const [section, m] of Object.entries(map)) {
    if (section.startsWith('_')) continue;
    const hay = section === 'debt' ? `${debtSrc}\n${page}` : page;
    for (const [fa, v] of Object.entries(m)) {
      for (const c of fa.split(/\{\d+\}/).map(norm).filter((x) => LETTER.test(x))) assert.ok(hay.includes(c), `${section}: «${c}» در ${section === 'debt' ? 'debt-book.js' : 'index.html'} نیست`);
      if (v === '=') { assert.ok(KEYS.has(fa), `${section}: «${fa}» در واژه‌نامهٔ صفحه نیست`); continue; }
      const refs = [...String(v).matchAll(/\[(\w+)\]/g)].map((x) => x[1]);
      assert.ok(refs.length, `${section}: ${fa} ⇐ ${v}`);
      for (const r of refs) assert.ok(Object.hasOwn(labels, r), `${section}: کلیدِ ناشناختهٔ StrReceipt: ${r}`);
    }
  }
  for (const s of ['label', 'kind', 'footer', 'meta', 'title', 'preview', 'debt']) assert.ok(map[s], s);
});

/**
 *  همان مترجمِ رسیدِ قرضِ web-i18n.js (`DEBT_TR`) برای en/ps، بی مرورگر: کلید ⇐ بخشِ `debt`؛ «=» ⇐ واژه‌نامهٔ صفحه،
 *  `[key]` ⇐ StrReceipt؛ `phrase` (متنِ `debtState`) با الگوی همان بخش و جای‌خالیِ عددی.
 */
function debtTr(lang, asked) {
  const map = json('receipt.web.json').debt;
  const page = lang === 'en' ? EN : PS;
  const labels = R.build()[lang];
  const res = (k) => {
    const v = map[k];
    if (v === undefined) return null;
    return v === '=' ? page[k] : v.replace(/\[(\w+)\]/g, (m, n) => labels[n]);
  };
  return {
    key: (k) => {
      const key = norm(k);
      if (asked) asked.add(key);
      const out = res(key);
      return out == null ? null : /^\s*/.exec(k)[0] + out;
    },
    phrase: (s) => {
      for (const k of Object.keys(map)) {
        if (!/\{0\}/.test(k) && k === s) return res(k);
        const re = new RegExp(`^${k.split('{0}').map((x) => x.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('([\\d,.]+)')}$`);
        const m = re.exec(s);
        if (m && /\{0\}/.test(k)) return res(k).replace('{0}', m[1]);
      }
      return null;
    },
  };
}
const DEBT = (() => {
  const B = require('../../license/debt-book.js');
  const data = {
    products: [{ id: 'p1', name: 'تنظیمات', unit: 'کیلو' }, { id: 'p2', name: 'گوشی', unit: '' }],
    debtors: [{ id: 'c1', name: 'نقدی', phone: '0790000000' }],
    sales: [{ id: 's1', invoiceNumber: 1, debtorId: 'c1', total: 400, discount: 25, finalTotal: 375, paidAmount: 75, remaining: 300, date: '2026-10-01' }],
    saleItems: [
      { id: 'i1', saleId: 's1', productId: 'p1', quantity: 1.25, unitPrice: 300, totalPrice: 375, returnedQty: 1 },
      { id: 'i2', saleId: 's1', productId: '', name: '', quantity: 1, unitPrice: 10, totalPrice: 10 },
      { id: 'i3', saleId: 's1', productId: 'gone', quantity: 1, unitPrice: 15, totalPrice: 15 },
    ],
    transactions: [
      { id: 'g', debtorId: 'c1', type: 'give', amount: 300, date: '2026-10-01', notes: 'فروش نسیه — فاکتور #1', dueDate: '2026-10-05', createdAt: 1 },
      { id: 'g2', debtorId: 'c1', type: 'give', amount: 50, date: '2026-10-02', notes: 'فروش', createdAt: 2 },
      { id: 'r', debtorId: 'c1', type: 'receive', amount: 400, date: '2026-10-06', notes: 'نقدی', createdAt: 6 },
    ],
  };
  const money = (m) => Math.round(m / 100).toLocaleString('en-US');
  const fmt = {
    money, date: (s) => s, qty: (n) => String(n), phone: (p) => p,
    state: (m) => (m > 0 ? `${money(m)} افغانی بدهکار` : m < 0 ? `${money(-m)} افغانی موجودی دارد` : 'حساب صاف است'),
  };
  return { B, all: () => ['g', 'g2', 'r'].flatMap((id) => ['', 'تنظیمات'].map((shop) => B.receipt(data, 'c1', id, shop, '2026-10-17', fmt))) };
})();

test('۷) رسیدِ قرض: هر متنِ ثابتِ debt-book.js در بخشِ `debt` است؛ فارسی (بی مترجم) همان رسیدِ همیشه', () => {
  const asked = new Set();
  const all = DEBT.all();
  assert.equal(all.length, 6);
  for (const r of all) {
    const L = DEBT.B.localize(r, debtTr('en', asked));
    assert.equal(L.name, r.name, 'نامِ پرونده همان');
    const same = DEBT.B.localize(r, { key: () => null, phrase: () => null });
    assert.deepEqual([same.title, same.rows], [r.title, r.rows], 'بی ترجمه = همان فارسی');
  }
  const map = json('receipt.web.json').debt;
  const missing = [...asked].filter((k) => LETTER.test(k) && !Object.hasOwn(map, k));
  assert.deepEqual(missing, [], 'متنِ ثابتِ رسیدِ قرض بیرون از بخشِ debt');
  //  هیچ تکهٔ داده به مترجم نمی‌رسد: نامِ کالا/مشتری/دکان و شرح که خودشان کلیدند پرسیده نمی‌شوند
  for (const d of ['تنظیمات', 'نقدی', 'فروش', 'فروش نسیه — فاکتور #1', 'گوشی', 'کیلو']) assert.ok(!asked.has(d), `داده به مترجم رسید: ${d}`);
  assert.ok(DEBT.B.receiptText(all[0]).startsWith('فروشگاه — رسیدِ قرض\nمشتری: نقدی\nتلفن: 0790000000'));
});

test('۷) رسیدِ قرض در انگلیسی و پشتو: برچسب از StrReceipt/واژه‌نامه، داده و مبلغ دست‌نخورده', () => {
  const L = R.build();
  for (const lang of ['en', 'ps']) {
    const tr = debtTr(lang);
    const D = lang === 'en' ? EN : PS;
    const [give, named, , , recv] = DEBT.all().map((r) => DEBT.B.localize(r, tr));
    const text = DEBT.B.receiptText(give);
    assert.ok(named.title.startsWith('تنظیمات — '), `${lang}: نامِ دکان داده است`);
    const rows = Object.fromEntries(give.rows);
    assert.ok(text.startsWith(`${L[lang].fallbackShop} — `), `${lang}: عنوان`);
    assert.equal(rows[L[lang].customer], 'نقدی', `${lang}: نامِ مشتری داده است`);
    assert.equal(rows[L[lang].phone], '0790000000');
    assert.equal(rows[L[lang].note], 'فروش نسیه — فاکتور #1', `${lang}: شرح داده است`);
    assert.ok(Object.hasOwn(rows, 'تنظیمات — 1.25 کیلو × 300'), `${lang}: نامِ کالا و واحد داده‌اند`);
    assert.ok(give.rows.some(([k]) => k === `   ${L[lang].returned}: 1 کیلو`), `${lang}: مرجوعی`);
    assert.equal(rows[L[lang].subtotal], D['{0} افغانی'].replace('{0}', '400'), `${lang}: مبلغ`);
    for (const k of ['discount', 'total', 'paid']) assert.ok(Object.hasOwn(rows, L[lang][k]), `${lang}: ${k}`);
    assert.ok(give.rows.some(([k]) => k.startsWith(lang === 'en' ? 'Custom item — ' : PS['قلم آزاد'])), `${lang}: قلمِ آزاد ترجمه می‌شود`);
    const recvText = DEBT.B.receiptText(recv);
    const back = Object.fromEntries(recv.rows);
    assert.ok(recvText.split('\n')[0].endsWith(D['رسیدِ دریافت']), `${lang}: نوعِ رسید`);
    assert.equal(back[D['مانده پس از این ردیف']], D['{0} افغانی موجودی دارد'].replace('{0}', '50'), `${lang}: متنِ debtState`);
    assert.equal(back[D['مانده پیش از دریافت']], D['{0} افغانی بدهکار'].replace('{0}', '350'));
    assert.equal(back[D['بابتِ {0}'].replace('{0}', D['فاکتور #{0}'].replace('{0}', '1'))], D['{0} افغانی'].replace('{0}', '300'), `${lang}: بابتِ فاکتور`);
    assert.ok(Object.hasOwn(back, D['بابتِ {0}'].replace('{0}', D['برد {0}'].replace('{0}', '2026-10-02'))), `${lang}: بابتِ برد`);
    assert.equal(back[L[lang].note], 'نقدی', `${lang}: شرحِ «نقدی» داده است، نه «${D['نقدی']}»`);
    if (lang === 'en') {
      //  انگلیسی: هیچ حرفِ فارسی جز دادهٔ دفتر
      for (const t of [text, recvText]) {
        const rest = ['تنظیمات', 'نقدی', 'فروش نسیه — فاکتور #1', 'کیلو', 'گوشی'].reduce((s, d) => s.split(d).join(''), t);
        assert.ok(!LETTER.test(rest), `en: فارسی مانده:\n${t}`);
      }
    }
  }
});

test('۸) ظرف‌های دادهٔ web-i18n.js (`DATA_SEL`) هنوز در index.html هستند؛ تقویمِ نمایش در lang.<زبان>.json', () => {
  const src = read('license/web-i18n.js');
  const block = /const DATA_SEL = \[([\s\S]*?)\]\.join/.exec(src);
  assert.ok(block, 'DATA_SEL');
  const sels = [...block[1].matchAll(/'([^']+)'/g)].map((m) => m[1]).filter((x) => x !== '[data-raw]');
  assert.ok(sels.length >= 10);
  for (const sel of sels) {
    for (const part of sel.split(/\s*>\s*|\s+/)) {
      const cls = /^\.([\w-]+)$/.exec(part);
      const id = /^#([\w-]+)$/.exec(part);
      const attr = /^\[([\w-]+)\]$/.exec(part);
      if (cls) assert.match(html, new RegExp(`class="(?:[^"]*\\s)?${cls[1]}(?:\\s[^"]*)?"`), sel);
      else if (id) assert.match(html, new RegExp(`id="${id[1]}"`), sel);
      else if (attr) assert.ok(html.includes(`${attr[1]}="`), sel);
      else assert.fail(`گزینش‌گرِ ناشناخته: ${sel}`);
    }
  }
  for (const l of ['fa', 'en', 'ps']) for (const k of ['cal.note', 'cal.auto', 'cal.solar', 'cal.gregorian']) assert.ok(json(`lang.${l}.json`)[k], `${l}: ${k}`);
});

test('۴) دریِ وبِ دکان همان لایهٔ ویترین است: license/dari.js = server/public/kit/dari.js', () => {
  assert.equal(read('license/dari.js'), read('server/public/kit/dari.js'), 'node server/scripts/dari-words.js --write');
  const D = require('../../license/dari.js');
  assert.equal(D.of('انبار'), 'گدام');
  assert.equal(D.t('موجودی {0} در انبار'), 'موجودی {0} در گدام', 'جای‌خالی دست نمی‌خورد');
});

test('۴) دری: هیچ کلیدِ صفحه در لایهٔ واژه جای‌خالی‌اش را گم نمی‌کند', () => {
  const D = require('../../license/dari.js');
  const holes = (s) => (s.match(/\{\d+\}/g) || []).sort().join();
  const bad = FA.filter((k) => holes(D.t(k)) !== holes(k));
  assert.deepEqual(bad, []);
});

test('۵) CSP بدتر نمی‌شود: همان CSP ِ index.html؛ پرونده‌های تازه بی eval/Function/رویدادِ درون‌خطی/style', () => {
  const csp = /<meta http-equiv="Content-Security-Policy" content="([^"]+)">/.exec(html);
  assert.ok(csp);
  assert.equal(csp[1], "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: blob:; media-src 'self' data: blob:; connect-src 'self' https://api.vill3n.top wss://api.vill3n.top blob: data:; worker-src 'self'; manifest-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'");
  for (const f of ['license/web-i18n.js', 'license/thermal-print.js', 'license/dari.js']) {
    const src = read(f);
    for (const [bad, re] of [
      ['eval', /\beval\s*\(/], ['new Function', /new\s+Function\s*\(/], ['setTimeout با رشته', /set(?:Timeout|Interval)\s*\(\s*['"`]/],
      ['رویدادِ درون‌خطی', /\son[a-z]+\s*=\s*["']/i], ['setAttribute(on…)', /setAttribute\(\s*['"]on/i], ['javascript:', /javascript:/i],
      ['صفتِ style', /setAttribute\(\s*['"]style|\sstyle\s*=\s*["']/], ['innerHTML', /\.(?:inner|outer)HTML\s*=/], ['document.write', /document\.write/],
    ]) assert.ok(!re.test(src), `${f}: ${bad}`);
  }
  //  بارکننده فقط اسکریپتِ همین مبدأ می‌سازد
  assert.match(read('license/shop-sync.js'), /src: 'license\/web-i18n\.js\?v=1'/);
  assert.match(read('license/web-i18n.js'), /s\.src = `license\/dari\.js\?v=\$\{V\}`/);
});

test('۶) سرویس‌ورکر لایه و واژه‌نامه‌های کوچک را پیش‌کش دارد (آفلاین)', () => {
  const sw = read('sw.js');
  //  واژه‌نامهٔ بزرگِ صفحه و dari.js با نخستین بارِ همان زبان کش می‌شوند (سرِ همین فهرست در sw.js)
  for (const f of ['license/web-i18n.js',
    'license/i18n/receipt.fa.json', 'license/i18n/receipt.en.json', 'license/i18n/receipt.ps.json', 'license/i18n/receipt.web.json',
    'license/i18n/lang.fa.json', 'license/i18n/lang.en.json', 'license/i18n/lang.ps.json']) {
    assert.ok(sw.includes(`'./${f}'`), `sw.js: ${f}`);
  }
});

test('زبانِ گزینش‌گر و چاپ: lang.<زبان>.json هم‌کلید؛ پیام‌های چاپگر همان کلیدهای thermal-print.js', () => {
  const fa = json('lang.fa.json');
  for (const l of ['en', 'ps']) assert.deepEqual(Object.keys(json(`lang.${l}.json`)).sort(), Object.keys(fa).sort(), l);
  const used = [...read('license/thermal-print.js').matchAll(/msg\('(\w+)'\)/g)].map((m) => `thermal.${m[1]}`);
  assert.ok(used.length >= 4);
  for (const k of used) assert.ok(fa[k], k);
  for (const k of ['title', 'hint']) assert.ok(fa[k], k);
});
