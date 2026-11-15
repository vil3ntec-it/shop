#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارِ مرجعِ اسنادِ **بی‌شمارهٔ** زنجیرهٔ دستگاه — `core/test-vectors/doc-chain-docs-v1.json` (SEC-02).
 * برگشتِ فروش، قرض/رسید، خرید و پرداخت به تأمین‌کننده، پسِ فاکتورهای همان سری (همان الگوی `doc-chain-vectors.js`):
 *
 *   node scripts/doc-chain-docs-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/doc-chain-docs-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * سند این‌جا **مستقل** از Kotlin ساخته می‌شود (همان کلیدهای `DocChainDocs.doc`) و با `hash-chain.js` ِ سرور هش؛
 * `DocChainDocsVectorsTest` (jvmTest ِ core) باید بایت‌به‌بایت همین خط‌ها را بسازد و `doc-chain-docs.test.js` همین
 * حلقه‌ها را از درِ زندهٔ `POST /api/sync/v1/chain` می‌پذیرد (سنجشِ سرور نوعِ سند را محدود نمی‌کند).
 * ⛔ v1 پس از ادغام دست نمی‌خورد؛ شکلِ تازهٔ سند ⇐ `"v": 2` و فایلِ v2.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'core', 'test-vectors', 'doc-chain-docs-v1.json');
const hc = require('../src/lib/hash-chain');
const { bodyProblem } = require('../src/lib/doc-chain');
const { saleDoc } = require('./doc-chain-vectors');

const SHOP = 'shp_vec01';
const SERIES = 'B1';
const EPOCH = 1;
const BY = 'usr_vec01';
const START_NO = 7;
const DOC_FROM = 1761800000000;
const NOW = 1761800900000;
const TYPES = ['return', 'debt', 'purchase', 'supplier_pay'];

const set = (doc, k, v) => { if (v !== null && v !== undefined && v !== '') doc[k] = v; };

/** همان `DocChainDocs.doc` ِ Kotlin (ترتیبِ کلید مهم نیست؛ canonical مرتب می‌کند) */
function plainDoc(chain, seq, by, f) {
  const doc = { chain, seq, kind: 'doc', type: f.type, v: 1, id: f.id, status: 'posted', postedAt: f.postedAt };
  if (by) doc.by = by;
  doc.currency = f.currency;
  if (f.type === 'return') {
    Object.assign(doc, { qtyScale: 1000, saleId: f.saleId, saleItemId: f.saleItemId, productId: f.productId, qty: f.qty, amount: f.amount });
    for (const k of ['docId', 'disposition', 'refund']) set(doc, k, f[k]);
    for (const k of ['toAccount', 'toCredit']) if (typeof f[k] === 'number') doc[k] = f[k];
    set(doc, 'creditTo', f.creditTo);
    if (typeof f.unitPrice === 'number') doc.unitPrice = f.unitPrice;
  } else if (f.type === 'debt') {
    Object.assign(doc, { customerId: f.customerId, direction: f.direction, amount: f.amount });
    set(doc, 'refId', f.refId);
    set(doc, 'debtKind', f.kind);
  } else if (f.type === 'purchase') {
    Object.assign(doc, {
      qtyScale: 1000, supplierId: f.supplierId, productId: f.productId, qty: f.qty, unitPrice: f.unitPrice,
      total: f.total, paid: f.paid, debt: f.debt,
    });
  } else if (f.type === 'supplier_pay') {
    Object.assign(doc, { supplierId: f.supplierId, amount: f.amount });
  } else {
    throw new Error(`نوعِ ناشناخته: ${f.type}`);
  }
  return doc;
}

const validId = (id) => typeof id === 'string' && id.length > 0 && id.length <= 128 && !/[,\t\n\r]/.test(id);
const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

/** همان `DocChainDocs.pending` در نخستین دور (پنجره از `docFrom`) */
function pick(docs) {
  const seen = new Set();
  return docs
    .filter((d) => TYPES.includes(d.type) && validId(d.id) && d.postedAt >= DOC_FROM)
    .sort((a, b) => (a.postedAt - b.postedAt) || cmp(a.type, b.type) || cmp(a.id, b.id))
    .filter((d) => { const k = `${d.type}:${d.id}`; if (seen.has(k)) return false; seen.add(k); return true; });
}

function build() {
  const chain = `doc/${SHOP}/${SERIES}/${EPOCH}`;
  //  مبلغ به پول (۱/۱۰۰ افغانی)، مقدار به هزارم
  const sales = [
    { id: 'sal_01', series: SERIES, no: 7, postedAt: 1761800100000, customerId: 'deb_07', currency: 'AFN',
      lines: [{ productId: 'p_rice', qty: 2000, unitPrice: 5000, amount: 10000 }],
      subtotal: 10000, discount: 0, total: 10000, paid: 4000, method: 'credit' },
  ];
  const docs = [
    //  پیش از آغاز — در زنجیره نمی‌آید
    { type: 'debt', id: 'dtx_old', postedAt: 1761799999999, currency: 'AFN', customerId: 'deb_07', direction: 'give', amount: 100 },
    //  برگشتِ دوقلمیِ یک سند (هر ردیف یک سند با همان docId): نقد، و اعتبارِ فروشگاه با ضایعات
    { type: 'return', id: 'ret_01a', postedAt: 1761800200000, currency: 'AFN', saleId: 'sal_01', saleItemId: 'si_01', productId: 'p_rice',
      qty: 500, amount: 2500, docId: 'rdoc_01', disposition: 'restock', refund: 'cash', toAccount: 1000 },
    { type: 'return', id: 'ret_01b', postedAt: 1761800200000, currency: 'AFN', saleId: 'sal_01', saleItemId: 'si_01', productId: 'p_rice',
      qty: 250, amount: 1250, docId: 'rdoc_01', disposition: 'damaged', refund: 'store_credit', toCredit: 1250, creditTo: 'deb_07' },
    //  برگشتِ بی رسید (بی فاکتور): قیمتِ واحدِ پس‌داده
    { type: 'return', id: 'ret_02', postedAt: 1761800250000, currency: 'AFN', saleId: '', saleItemId: '', productId: 'p_oil',
      qty: 1000, amount: 12550, unitPrice: 12550 },
    //  قرضِ دستی، و رسیدِ ساختهٔ سیستم (لغوِ فروشِ نسیه)
    { type: 'debt', id: 'dtx_01', postedAt: 1761800300000, currency: 'AFN', customerId: 'deb_07', direction: 'give', amount: 75000 },
    { type: 'debt', id: 'dtx_02', postedAt: 1761800300000, currency: 'AFN', customerId: 'deb_07', direction: 'receive', amount: 6000,
      refId: 'sal_01', kind: 'sale_cancel' },
    //  خرید، و برگشت به تأمین‌کننده (مقدار و مبلغِ منفی)، با مبلغِ بزرگ
    { type: 'purchase', id: 'pur_01', postedAt: 1761800400000, currency: 'AFN', supplierId: 'sup_01', productId: 'p_gold',
      qty: 1500, unitPrice: 658436214008, total: 987654321012, paid: 900000000000, debt: 87654321012 },
    { type: 'purchase', id: 'pur_02', postedAt: 1761800450000, currency: 'AFN', supplierId: 'sup_01', productId: 'p_rice',
      qty: -2000, unitPrice: 4000, total: -8000, paid: 0, debt: -8000 },
    { type: 'supplier_pay', id: 'spay_01', postedAt: 1761800500000, currency: 'AFN', supplierId: 'sup_01', amount: 50000 },
    //  شناسه‌ای که در کلید جا نمی‌شود — کنار؛ همان سند دوباره (دو دستگاه) — یک بار
    { type: 'debt', id: 'dtx,bad', postedAt: 1761800600000, currency: 'AFN', customerId: 'deb_07', direction: 'give', amount: 1 },
    { type: 'supplier_pay', id: 'spay_01', postedAt: 1761800500000, currency: 'AFN', supplierId: 'sup_01', amount: 50000 },
  ];
  const links = [];
  let prev = hc.GENESIS;
  let bytes = 0;
  const push = (type, ref, no, doc) => {
    const seq = links.length + 1;
    const body = hc.canonicalJson(doc(seq));
    const hash = hc.sha256Hex(prev + body);
    const line = ['L1', seq, prev, hash, type, ref, no, body].join('\t');
    const link = { seq, prevHash: prev, hash, type, ref, no, body, line };
    const p = bodyProblem(chain, link);
    if (p) throw new Error(`حلقهٔ ${seq}: ${p}`);
    links.push(link);
    bytes += Buffer.byteLength(line, 'utf8') + 1;
    prev = hash;
  };
  for (const s of sales.filter((x) => x.series === SERIES && x.no >= START_NO).sort((a, b) => a.no - b.no)) {
    push('sale', s.id, s.no, (seq) => saleDoc(chain, seq, BY, s));
  }
  for (const d of pick(docs)) push(d.type, d.id, 0, (seq) => plainDoc(chain, seq, BY, d));
  const chained = links.filter((l) => l.type !== 'sale');
  const docAt = Math.min(Math.max(DOC_FROM, ...pick(docs).map((d) => d.postedAt)), NOW);
  return {
    format: hc.FORMAT,
    version: 1,
    about: 'بردارِ مرجعِ اسنادِ بی‌شمارهٔ زنجیرهٔ دستگاه (SEC-02): برگشت، قرض/رسید، خرید و پرداخت به تأمین‌کننده پسِ فاکتورهای همان سری. ساخته‌شده با server/scripts/doc-chain-docs-vectors.js — دستی ویرایش نشود. Kotlin (core/audit/DocChainDocs، DocChainDocsVectorsTest) همین خط‌ها را می‌سازد و سرور (lib/doc-chain.js، doc-chain-docs.test.js) همین حلقه‌ها را می‌پذیرد. مبلغ به پول، مقدار به هزارم؛ سند بی دادهٔ شخصی.',
    rules: {
      doc: 'canonicalJson of { chain, seq, kind:"doc", type, v:1, id, status:"posted", postedAt, by? (when non-empty), currency, ...type fields } — return: qtyScale, saleId, saleItemId, productId, qty, amount, docId?, disposition?, refund?, toAccount?, toCredit?, creditTo?, unitPrice?; debt: customerId, direction, amount, refId?, debtKind?; purchase: qtyScale, supplierId, productId, qty, unitPrice, total, paid, debt; supplier_pay: supplierId, amount',
      pick: 'after the sales of the series: plain docs with postedAt >= docFrom (and inside the 3-day window before the marker), id without , TAB CR LF, not chained before; ascending by postedAt, then type, then id; one per type:id',
      marker: 'docAt = max(docAt, min(newest chained postedAt, now)); docIds = chained keys still inside the window',
      line: 'L1 TAB seq TAB prevHash TAB hash TAB type TAB ref TAB 0 TAB body (one line per link, LF-terminated)',
    },
    shopId: SHOP,
    series: SERIES,
    epoch: EPOCH,
    chain,
    by: BY,
    startNo: START_NO,
    docFrom: DOC_FROM,
    now: NOW,
    sales,
    docs,
    links,
    head: prev,
    bytes,
    docAt,
    docIds: chained.map((l) => `${l.type}:${l.ref}`).sort(),
  };
}

function serialize(out) {
  return JSON.stringify(out, null, 2) + '\n';
}

module.exports = { build, serialize, FIXTURE, plainDoc };

if (require.main === module) {
  const text = serialize(build());
  if (process.argv.includes('--write')) {
    fs.mkdirSync(path.dirname(FIXTURE), { recursive: true });
    fs.writeFileSync(FIXTURE, text);
    console.log('نوشته شد:', path.relative(process.cwd(), FIXTURE));
  } else {
    const same = fs.existsSync(FIXTURE) && fs.readFileSync(FIXTURE, 'utf8') === text;
    console.log(same ? 'بردار با پیاده‌سازیِ امروز یکی است.' : 'بردار کهنه است — با --write دوباره بسازید.');
    process.exitCode = same ? 0 : 1;
  }
}
