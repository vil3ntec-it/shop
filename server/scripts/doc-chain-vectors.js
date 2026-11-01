#!/usr/bin/env node
'use strict';
/**
 * سازندهٔ بردارِ مرجعِ زنجیرهٔ اسنادِ دستگاه — `core/test-vectors/doc-chain-v1.json` (SEC-02).
 * همان الگوی `hashchain-vectors.js`:
 *
 *   node scripts/doc-chain-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/doc-chain-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * سندِ فاکتور را این‌جا **مستقل** از Kotlin می‌سازیم (همان کلیدهای `DocChain.saleDoc`) و با
 * `hash-chain.js` ِ سرور هش می‌کنیم؛ `DocChainVectorsTest` (jvmTest ِ core) باید بایت‌به‌بایت همین
 * خط‌ها را بسازد و `doc-chain.test.js` همین حلقه‌ها را می‌پذیرد. پس سندی که گوشی می‌سازد همان است که
 * سرور می‌سنجد. ⛔ v1 پس از ادغام دست نمی‌خورد؛ شکلِ تازهٔ سند ⇐ `"v": 2` و فایلِ v2.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'core', 'test-vectors', 'doc-chain-v1.json');
const hc = require('../src/lib/hash-chain');
const { bodyProblem } = require('../src/lib/doc-chain');

const SHOP = 'shp_vec01';
const SERIES = 'B1';
const EPOCH = 1;
const BY = 'usr_vec01';
const START_NO = 7;

/** همان `DocChain.saleDoc` ِ Kotlin (ترتیبِ کلید مهم نیست؛ canonical مرتب می‌کند) */
function saleDoc(chain, seq, by, f) {
  const doc = {
    chain, seq, kind: 'doc', type: 'sale', v: 1,
    id: f.id, series: f.series, no: f.no, status: 'posted', postedAt: f.postedAt,
  };
  if (by) doc.by = by;
  if (f.customerId) doc.customerId = f.customerId;
  Object.assign(doc, {
    currency: f.currency,
    qtyScale: 1000,
    lines: f.lines.map((l) => ({ productId: l.productId, qty: l.qty, unitPrice: l.unitPrice, amount: l.amount })),
    subtotal: f.subtotal, discount: f.discount, total: f.total, paid: f.paid, method: f.method,
  });
  return doc;
}

function build() {
  const chain = `doc/${SHOP}/${SERIES}/${EPOCH}`;
  //  مبلغ به پول (۱/۱۰۰ افغانی)، مقدار به هزارم
  const sales = [
    //  پیش از روشن شدن (شماره کمتر از startNo) — در زنجیره نمی‌آید
    { id: 'sal_old', series: SERIES, no: 6, postedAt: 1761800000000, customerId: null, currency: 'AFN',
      lines: [{ productId: 'p_rice', qty: 1000, unitPrice: 5000, amount: 5000 }],
      subtotal: 5000, discount: 0, total: 5000, paid: 5000, method: 'cash' },
    //  نقدی، مقدارِ کسری (۱٫۵ کیلو)
    { id: 'sal_01', series: SERIES, no: 7, postedAt: 1761800100000, customerId: null, currency: 'AFN',
      lines: [
        { productId: 'p_rice', qty: 1500, unitPrice: 5000, amount: 7500 },
        { productId: 'p_oil', qty: 2000, unitPrice: 12550, amount: 25100 },
      ],
      subtotal: 32600, discount: 600, total: 32000, paid: 32000, method: 'cash' },
    //  سریِ دستگاهِ دیگر — در این زنجیره نمی‌آید
    { id: 'sal_a1', series: 'A1', no: 8, postedAt: 1761800150000, customerId: null, currency: 'AFN',
      lines: [{ productId: 'p_tea', qty: 1000, unitPrice: 9000, amount: 9000 }],
      subtotal: 9000, discount: 0, total: 9000, paid: 9000, method: 'cash' },
    //  نسیه به یک مشتری (فقط شناسه)، با قلمِ آزاد (بی کالا)
    { id: 'sal_02', series: SERIES, no: 8, postedAt: 1761800200000, customerId: 'deb_07', currency: 'AFN',
      lines: [
        { productId: 'p_tea', qty: 3000, unitPrice: 9000, amount: 27000 },
        { productId: '', qty: 1000, unitPrice: 2500, amount: 2500 },
      ],
      subtotal: 29500, discount: 0, total: 29500, paid: 10000, method: 'credit' },
    //  مبلغِ بزرگ (نزدیکِ ۱۰^۱۲ پول)
    { id: 'sal_03', series: SERIES, no: 9, postedAt: 1761800300000, customerId: null, currency: 'AFN',
      lines: [{ productId: 'p_gold', qty: 1000, unitPrice: 987654321012, amount: 987654321012 }],
      subtotal: 987654321012, discount: 12, total: 987654321000, paid: 987654321000, method: 'mixed' },
  ];
  const links = [];
  let prev = hc.GENESIS;
  let bytes = 0;
  for (const f of sales.filter((s) => s.series === SERIES && s.no >= START_NO).sort((a, b) => a.no - b.no)) {
    const seq = links.length + 1;
    const body = hc.canonicalJson(saleDoc(chain, seq, BY, f));
    const hash = hc.sha256Hex(prev + body);
    const line = ['L1', seq, prev, hash, 'sale', f.id, f.no, body].join('\t');
    const link = { seq, prevHash: prev, hash, type: 'sale', ref: f.id, no: f.no, body, line };
    const p = bodyProblem(chain, link);
    if (p) throw new Error(`حلقهٔ ${seq}: ${p}`);
    links.push(link);
    bytes += Buffer.byteLength(line, 'utf8') + 1;
    prev = hash;
  }
  return {
    format: hc.FORMAT,
    version: 1,
    about: 'بردارِ مرجعِ زنجیرهٔ اسنادِ دستگاه (SEC-02، بخشِ ۱۰.۳). ساخته‌شده با server/scripts/doc-chain-vectors.js — دستی ویرایش نشود. Kotlin (core/audit/DocChain، DocChainVectorsTest) همین خط‌ها را می‌سازد و سرور (lib/doc-chain.js، doc-chain.test.js) همین حلقه‌ها را می‌پذیرد. مبلغ به پول (کوچک‌ترین واحد)، مقدار به هزارم؛ سند بی دادهٔ شخصی.',
    rules: {
      chain: 'doc/<shopId>/<series>/<epoch>',
      doc: 'canonicalJson of { chain, seq, kind:"doc", type:"sale", v:1, id, series, no, status:"posted", postedAt, by? (when non-empty), customerId? (when non-empty), currency, qtyScale:1000, lines:[{productId, qty, unitPrice, amount}], subtotal, discount, total, paid, method }',
      pick: 'sales of this series with no >= startNo and no > last chained no, ascending by no, one per no',
      line: 'L1 TAB seq TAB prevHash TAB hash TAB type TAB ref TAB no TAB body (one line per link, LF-terminated)',
    },
    shopId: SHOP,
    series: SERIES,
    epoch: EPOCH,
    chain,
    by: BY,
    startNo: START_NO,
    sales,
    links,
    head: prev,
    bytes,
  };
}

function serialize(out) {
  return JSON.stringify(out, null, 2) + '\n';
}

module.exports = { build, serialize, FIXTURE, saleDoc };

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
