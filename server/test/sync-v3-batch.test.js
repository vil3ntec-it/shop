'use strict';
/**
 * PERF (scale-4): اعمالِ دسته‌ایِ push ِ v3 (`lib/sync-v3-batch.js`، پرچمِ `SYNC_V3_BATCH`، پیش‌فرض خاموش).
 *
 * ⛔ برابری: همان رشتهٔ push ها روی دو دکانِ هم‌شکل — یکی راهِ تک‌تک (`store.direct`)، یکی راهِ دسته‌ای —
 * ⇒ همان پاسخِ هر op (وضع، دلیل، شمارهٔ دفتر به ترتیب، HLC، `current`)، همان opها در دفتر به همان ترتیب و اثر،
 * همان ردیف‌ها (داده، HLC ِ هر فیلد، منبع، حذف، کلیدِ تلفن)، همان تعارض‌ها و وظیفه‌ها و همان دستگاه‌ها. رشته:
 * تکراری (میانِ دسته‌ها و درونِ یک دسته)، «آخرین نوشتن» و تعارضش، حذف و ویرایشِ پس از آن، دلتا، لغو، ساعتِ کج،
 * tx ِ شکسته، بارکد/مشتریِ تکراری، موجودیِ منفی، تعدیلِ شمارش و حرکتِ دیررسیده، شاگرد با تخفیف/قرضِ بالای سقف
 * (صندوقِ تأیید) و حذفِ ممنوع، v2، دستهٔ بزرگ، و رشته‌ای تصادفی با seed ِ ثابت.
 * روی PostgreSQL شمارِ رفت‌وبرگشت‌ها هم سنجیده می‌شود (کمتر، نه برابر).
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_LOGIN_EDGE_MAX ||= '100000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');
const config = require('../src/config');
const hlc = require('../src/lib/hlc');
const { resetAll } = require('../src/middleware/ratelimit');

//  حالِ پرچم پیش از این آزمون (env ِ `SYNC_V3_BATCH`)؛ هر اجرا راهِ خودش را صریح می‌گذارد و این را برمی‌گرداند
const INITIAL = config.syncV3.batch;

const BASE = Date.now() - 2 * 3_600_000;
const H = (node, dt) => hlc.format({ wall: BASE + dt, counter: 0, node });
//  ۱۰ دقیقه جلوتر از ساعتِ سرور (سقف ۵ دقیقه) — یک بار، تا هر دو اجرا همان op را بفرستند
const SKEWED = hlc.format({ wall: Date.now() + 600_000, counter: 0, node: 'o2' });
let admin = '';
let LATE = 0;    //  پس از ساختنِ تأییدها: HLC ِ opهای با تأیید (تأیید پیش از op)
const L = (dt) => hlc.format({ wall: LATE + dt, counter: 0, node: 's1' });
let asked = 0;

async function shopWithStaff(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  const id = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser(`شاگردِ ${label}`, 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser(`مدیرِ ${label}`, 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  const shopOf = { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop'), manager: await h.signIn(m, 'shop') };
  shopOf.ids = [id, o.user.id, s.user.id, m.user.id];
  for (const [key, value] of [['shop.credit_cap', true], ['shop.sync.doc_roles', true], ['shop.returns.sale_cap', true], ['shop.approval_requests', true]]) {
    const r = await h.put('/api/admin/pump/live-config', { scope: id, key, value }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const cap = await h.roleLayer(shopOf.owner.accessToken, 'staff', { 'sales.credit': { caps: { maxAmount: 500000 } }, 'sales.discount': { mode: 'approve' } });
  assert.equal(cap.status, 200, JSON.stringify(cap.body));
  //  تأییدِ تخفیفِ شاگرد برای `sales/sap` (مصرفِ آن درونِ واحدی که بعد رد می‌شود باید برگردد)
  resetAll();
  const ask = await h.post('/api/shop/approval-requests', { key: `batch-${Date.now().toString(36)}-${++asked}`, reason: 'مشتری', perm: 'sales.discount',
    doc: 'sales/sap', pctBp: 2000, amount: 2000 }, { token: shopOf.staff.accessToken });
  assert.equal(ask.status, 201, JSON.stringify(ask.body));
  resetAll();
  const ok = await h.post(`/api/shop/approval-requests/${ask.body.request.id}/approve`, {}, { token: shopOf.owner.accessToken });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  shopOf.apr = ask.body.request.id;
  shopOf.ids.push(shopOf.apr);
  return shopOf;
}

/* ------------------------------------------------------------ رشتهٔ دستی */

function scenario(shop) {
  let n = 0;
  const id = () => `01JBATCH${String(++n).padStart(18, '0')}`;
  const op = (table, rowId, kind, dt, fields, extra = {}, node = 'o1') => ({
    op_id: id(), hlc: H(node, dt), table, row_id: rowId, kind, ...(fields ? { fields } : {}), ...extra,
  });
  const sale = (sid, dt, { tx = `tx-${sid}`, pid = 'p1', qty = -1000, node = 'o1', extra = {} } = {}) => [
    op('sales', sid, 'insert', dt, { totalMinor: 5000, finalTotalMinor: 5000, paidAmountMinor: 5000, status: 'completed', ...extra }, { tx }, node),
    op('saleItems', `${sid}-i`, 'insert', dt + 1, { saleId: sid, productId: pid, quantityMilli: -qty, unitPriceMinor: 5000, totalPriceMinor: 5000 }, { tx }, node),
    op('stockMovements', `${sid}-m`, 'insert', dt + 2, { productId: pid, qtyMilli: qty, type: 'sale', refId: sid }, { tx }, node),
    op('transactions', `${sid}-t`, 'insert', dt + 3, { kind: 'sale_cash', type: 'in', amountMinor: 5000, refId: sid }, { tx }, node),
  ];
  const p4 = op('products', 'p4', 'insert', 40, { name: 'چهارم' });
  const s1 = [];
  s1.push(op('products', 'p1', 'insert', 10, { name: 'یک', barcodes: ['111'], salePriceMinor: 5000 }));
  s1.push(op('products', 'p2', 'insert', 20, { name: 'دو', barcodes: ['111', '222'] }));
  s1.push(op('products', 'p3', 'insert', 30, { salePriceMinor: 3000, name: 'سه' }));   //  ترتیبِ کلید ≠ ترتیبِ jsonb
  s1.push(p4, { ...p4 });                                            //  تکراری درونِ همان دسته
  s1.push(op('debtors', 'c1', 'insert', 50, { name: 'احمد', phone: '0799 123 456' }));
  s1.push(op('debtors', 'c2', 'insert', 60, { name: 'احمد جان', phone: '+93۷۹۹۱۲۳۴۵۶' }));
  s1.push(op('stockMovements', 'm-in-1', 'insert', 70, { productId: 'p1', qtyMilli: 10000, type: 'purchase' }));
  s1.push(op('stockMovements', 'm-in-2', 'insert', 80, { productId: 'p2', qtyMilli: 5000, type: 'purchase' }));
  s1.push(...sale('s1', 100, { extra: { createdAt: BASE - 86_400_000 } }));   //  «تاریخِ تصحیح‌شده»
  s1.push(...sale('s2', 110, { pid: 'p3' }));                        //  موجودیِ منفی
  s1.push(op('stockMovements', 'p8-m1', 'insert', 112, { productId: 'p8', qtyMilli: -100 }, { tx: 'tx-p8' }),   //  یک وظیفه، دو بار در یک واحد
    op('stockMovements', 'p8-m2', 'insert', 113, { productId: 'p8', qtyMilli: -200 }, { tx: 'tx-p8' }));
  s1.push(op('products', 'p1', 'update', 120, { name: 'یکِ تازه' }), op('products', 'p1', 'update', 130, { name: 'یکِ تازه‌تر', salePriceMinor: 5500 }));
  s1.push(op('sales', 's1', 'delta', 140, { paidAmountMinor: 100 }));
  s1.push(op('sales', 's-bad', 'insert', 150, { totalMinor: 10 }, { tx: 'tx-bad' }), op('stockMovements', 'm-bad', 'insert', 151, { productId: 'p1', qty: -2 }, { tx: 'tx-bad' }));
  s1.push(op('stockCounts', 'sc1', 'insert', 200, { productId: 'p2', countedQtyMilli: 4000 }));
  s1.push(op('products', 'p9', 'update', 210, { name: 'بی درج' }));  //  ویرایشِ ردیفِ ناموجود

  const s2 = [
    op('stockMovements', 'm-late', 'insert', 190, { productId: 'p2', qtyMilli: -500, type: 'sale' }, {}, 'o2'),   //  پیش از شمارش ⇒ تعدیل
    op('products', 'p1', 'update', 125, { name: 'یکِ دستگاهِ دو' }, {}, 'o2'),                                 //  بازنده
    op('products', 'p2', 'update', 300, { name: 'دوِ دستگاهِ دو' }, {}, 'o2'),                                  //  برنده با تعارض
    op('products', 'p3', 'delete', 310, null, {}, 'o2'),
    op('products', 'p3', 'delete', 300, null, {}, 'o2'),                                                       //  حذفِ کهنه‌تر، همان دسته
    op('sales', 's2', 'cancel', 320, null, { reason: 'اشتباه' }, 'o2'),
    op('sales', 's2', 'cancel', 315, null, { reason: 'دوباره' }, 'o2'),
    { ...op('products', 'p4', 'update', 0, { name: 'ساعتِ کج' }, {}, 'o2'), hlc: SKEWED },
    op('products', 'p4', 'update', 330, { salePriceMinor: 1200 }, {}, 'o2'),
  ];
  const s3 = [
    op('products', 'p3', 'update', 305, { name: 'سهِ پس از حذف' }),   //  کهنه‌تر از حذف
    op('products', 'p3', 'update', 340, { name: 'سهِ زنده؟' }),
    op('debtors', 'c1', 'update', 350, { phone: '0700 000 001' }),
    //  حذفِ کهنه‌تر از ویرایشِ همین دسته ⇒ تعارض با `current` ِ کاملِ ردیف (ترتیبِ کلیدهای jsonb)
    op('products', 'p6', 'insert', 360, { salePriceMinor: 600, name: 'شش', barcodes: ['666'] }),
    op('products', 'p6', 'update', 380, { name: 'ششِ تازه' }),
    op('products', 'p6', 'delete', 370, null),
  ];
  const s4 = [
    //  واحدی که پس از نوشتنِ حرکت رد می‌شود ⇒ برگشتِ واحد؛ همان ردیف دوباره در همان دسته
    op('stockMovements', 'stx-m', 'insert', 395, { productId: 'p1', qtyMilli: -2000, type: 'sale' }, { tx: 'stx' }, 's1'),
    op('sales', 'stx', 'insert', 396, { totalMinor: 900, discountMinor: 100, status: 'completed' }, { tx: 'stx' }, 's1'),
    op('stockMovements', 'stx-m', 'insert', 390, { productId: 'p1', qtyMilli: -1000, type: 'sale' }, {}, 's1'),   //  کهنه‌تر از ردشده
    //  وظیفهٔ «موجودیِ منفی» (نوشتنِ dedupe ِ `after`) در واحدی که بعد رد می‌شود ⇒ برمی‌گردد
    op('stockMovements', 'stx2-m', 'insert', 391, { productId: 'p7', qtyMilli: -500, type: 'sale' }, { tx: 'stx2' }, 's1'),
    op('sales', 'stx2', 'insert', 392, { totalMinor: 900, discountMinor: 100, status: 'completed' }, { tx: 'stx2' }, 's1'),
    op('stockMovements', 'stx3-m', 'insert', 393, { productId: 'p7', qtyMilli: -100, type: 'sale' }, {}, 's1'),      //  همان وظیفه، این بار می‌ماند
    ...sale('st1', 400, { node: 's1', extra: { discountMinor: 500 } }),                                         //  تخفیفِ بی اجازه
    ...sale('st2', 410, { node: 's1', extra: { debtGivenMinor: 900000, remainingMinor: 900000, paidAmountMinor: 0, debtorId: 'c1', paymentMethod: 'credit' } }),
    ...sale('st3', 420, { node: 's1', extra: { debtGivenMinor: 100000, remainingMinor: 100000, paidAmountMinor: 0, debtorId: 'c1', paymentMethod: 'credit' } }),
    op('products', 'p4', 'delete', 430, null, {}, 's1'),
    op('products', 'p5', 'insert', 440, { name: 'شاگرد' }, {}, 's1'),
    //  تخفیف با تأییدِ درست (مصرفِ درخواست = نوشتنِ داورِ بیرونی) + رسیدِ بدشکل ⇒ کلِ واحد رد و مصرف برمی‌گردد؛
    //  همان تأیید در واحدِ بعدی می‌نشیند
    { ...op('sales', 'sap', 'insert', 0, { totalMinor: 10000, discountMinor: 2000, finalTotalMinor: 8000, status: 'completed' }, { tx: 'sap1', approval: { v: 1, request: shop.apr } }, 's1'), hlc: L(1) },
    { ...op('transactions', 'sap-t', 'insert', 0, { kind: 'layaway_deposit', type: 'give', amountMinor: 8000, refId: 'sap' }, { tx: 'sap1' }, 's1'), hlc: L(2) },
    { ...op('sales', 'sap', 'insert', 0, { totalMinor: 10000, discountMinor: 2000, finalTotalMinor: 8000, status: 'completed' }, { tx: 'sap2', approval: { v: 1, request: shop.apr } }, 's1'), hlc: L(3) },
    //  برگشتِ بیش از فاکتورِ همین دسته (سقفِ برگشت می‌خواند)، و برگشتِ درست
    op('sales', 'sr1', 'insert', 450, { totalMinor: 1000, discountMinor: 0, finalTotalMinor: 1000, status: 'completed' }, {}, 's1'),
    op('saleItems', 'sr1-i', 'insert', 451, { saleId: 'sr1', quantityMilli: 1000, totalPriceMinor: 1000 }, {}, 's1'),
    op('returns', 'rr1', 'insert', 452, { saleId: 'sr1', amountMinor: 5000 }, {}, 's1'),
    op('returns', 'rr2', 'insert', 453, { saleId: 'sr1', amountMinor: 400 }, {}, 's1'),
  ];
  const ts = BASE + 500;
  const v2 = [
    { op_id: id(), ts, table: 'sales', row_id: 's-v2', type: 'insert', fields: { total: 120.5, paidAmount: 100, remaining: 20.5, status: 'completed', syncStatus: 'pending' } },
    { op_id: id(), ts: ts + 1, table: 'sales', row_id: 's-v2', type: 'update', fields: { paidAmount: { $inc: 20.5 }, remaining: { $inc: -20.5 } } },
    { op_id: id(), ts: ts + 2, table: 'productUnits', row_id: 'u-v2', type: 'insert', fields: { name: 'کارتن' } },
    { op_id: id(), ts: ts + 3, table: 'nope', row_id: 'x', type: 'insert', fields: { a: 1 } },
  ];
  const big = [];
  for (let i = 0; i < 40; i++) big.push(...sale(`b${i}`, 600 + i * 10, { node: 'm1', pid: i % 3 ? 'p1' : 'p2' }));
  return [
    { who: 'owner', dev: 'o1', ops: s1 },
    { who: 'owner', dev: 'o2', ops: s2 },
    { who: 'owner', dev: 'o1', ops: s3 },
    { who: 'staff', dev: 's1', ops: s4 },
    { who: 'staff', dev: 's1', ops: s4 },                       //  دوباره (پاسخ گم شد): همان داوری، صندوقِ همان
    { who: 'owner', dev: 'o1', ops: s1 },                       //  همه تکراری
    { who: 'owner', dev: 'v2dev', ops: v2, extra: { format: 'v2', schema_version: 3 } },
    { who: 'manager', dev: 'm1', ops: big },
  ];
}

/* ------------------------------------------------- رشتهٔ تصادفی (seed ثابت) */

function fuzz(seed, pushes) {
  let x = seed >>> 0;
  const rnd = () => { x = (x + 0x6D2B79F5) >>> 0; let t = x; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  const pick = (a) => a[Math.floor(rnd() * a.length)];
  let n = 0;
  const sent = [];
  const out = [];
  const prods = ['f-p1', 'f-p2', 'f-p3', 'f-p4', 'f-p5'];
  for (let k = 0; k < pushes; k++) {
    const who = rnd() < 0.2 ? 'staff' : 'owner';
    const dev = who === 'staff' ? 'fs1' : pick(['fo1', 'fo2', 'fo3']);
    const ops = [];
    const size = 5 + Math.floor(rnd() * 35);
    while (ops.length < size) {
      const r = rnd();
      const dt = 1000 + Math.floor(rnd() * 5000);
      const base = { op_id: `01JFUZZ${String(++n).padStart(19, '0')}`, hlc: H(dev, dt) };
      if (r < 0.08 && sent.length) { ops.push({ ...pick(sent) }); continue; }                 //  تکراری
      if (r < 0.35) {
        const pid = pick(prods);
        const kind = pick(['insert', 'update', 'update', 'delete']);
        ops.push({ ...base, table: 'products', row_id: pid, kind, ...(kind === 'delete' ? {} : { fields: { name: `${pid}-${n}`, salePriceMinor: Math.floor(rnd() * 9000), ...(rnd() < 0.3 ? { barcodes: [pick(['9', '8', '7'])] } : {}) } }) });
      } else if (r < 0.45) {
        ops.push({ ...base, table: 'debtors', row_id: pick(['f-c1', 'f-c2', 'f-c3']), kind: pick(['insert', 'update']), fields: { name: `c${n}`, phone: pick(['0799111222', '0799333444', '+93799111222']) } });
      } else if (r < 0.55) {
        ops.push({ ...base, table: 'stockCounts', row_id: `f-sc${n}`, kind: 'insert', fields: { productId: pick(prods), countedQtyMilli: Math.floor(rnd() * 8000) } });
      } else if (r < 0.62) {
        ops.push({ ...base, table: 'sales', row_id: pick(['f-s1', 'f-s2']), kind: pick(['delta', 'cancel']), ...(rnd() < 0.5 ? { kind: 'delta', fields: { paidAmountMinor: 10 } } : { kind: 'cancel', reason: 'r' }) });
      } else if (r < 0.66) {
        ops.push({ ...base, table: 'stockMovements', row_id: `f-bad${n}`, kind: 'insert', fields: { productId: 'f-p1', qty: 1 } });   //  نادرست
      } else {
        const sid = `f-s${n}`;
        const tx = rnd() < 0.8 ? `ftx-${n}` : undefined;
        const pid = pick(prods);
        const disc = who === 'staff' && rnd() < 0.3 ? { discountMinor: 100 } : {};
        const debt = who === 'staff' && rnd() < 0.3 ? { debtGivenMinor: pick([100000, 900000]), debtorId: 'f-c1' } : {};
        const g = [
          { ...base, table: 'sales', row_id: sid, kind: 'insert', fields: { totalMinor: 3000, status: 'completed', ...disc, ...debt } },
          { op_id: `01JFUZZ${String(++n).padStart(19, '0')}`, hlc: H(dev, dt + 1), table: 'stockMovements', row_id: `${sid}-m`, kind: 'insert', fields: { productId: pid, qtyMilli: -Math.floor(1 + rnd() * 3000), type: 'sale', refId: sid } },
        ];
        if (rnd() < 0.15) g.push({ op_id: `01JFUZZ${String(++n).padStart(19, '0')}`, hlc: H(dev, dt + 2), table: 'stockMovements', row_id: `${sid}-x`, kind: 'insert', fields: { productId: pid, qty: 1 } });
        for (const o of g) ops.push(tx ? { ...o, tx } : o);
      }
    }
    ops.forEach((o) => { if (!sent.includes(o)) sent.push(o); });
    out.push({ who, dev, ops });
  }
  return out;
}

/* ------------------------------------------------------- اجرا و مقایسه */

async function run(shop, steps, batchOn) {
  config.syncV3.batch = batchOn;
  const out = [];
  try {
    for (const st of steps) {
      resetAll();
      const r = await h.api('POST', '/api/sync/v3/push', {
        token: shop[st.who].accessToken, body: { device_id: st.dev, protocol: 3, ops: st.ops, ...(st.extra || {}) },
      });
      out.push({ status: r.status, results: r.body.results, applied: r.body.applied, error: r.body.error && r.body.error.code });
    }
  } finally {
    config.syncV3.batch = INITIAL;
  }
  return out;
}

const SERVER_HLC = /\d{15}:\d{6}:server/g;

/** حالِ دکان و پاسخ‌ها، با شمارهٔ دفترِ نسبی، شناسه‌های دکان/کاربر و HLC ِ خودِ سرور به ترتیب */
async function snapshot(shop, responses) {
  const acc = [shop.id];
  const ops = await db.many(
    `SELECT op_id, server_seq, device_id, origin, tx_id, hlc, client_hlc, skewed, table_name, row_id, kind, fields, effect, status
       FROM sync_v3_ops WHERE app='shop' AND account_id=$1 ORDER BY server_seq`, acc);
  const rank = new Map(ops.map((o, i) => [Number(o.server_seq), i + 1]));
  const r = (s) => (s == null ? s : rank.get(Number(s)) ?? `?${s}`);
  const rows = (await db.many(
    `SELECT table_name, row_id, data, field_hlc, field_src, row_hlc, inserted, created_by, deleted_hlc,
            deleted_at IS NOT NULL AS deleted, match_key, updated_seq
       FROM sync_v3_rows WHERE app='shop' AND account_id=$1 ORDER BY table_name, row_id`, acc))
    .map((x) => ({ ...x, updated_seq: r(x.updated_seq),
      field_src: Object.fromEntries(Object.entries(x.field_src || {}).map(([k, v]) => [k, { ...v, s: r(v.s) }])) }));
  const conflicts = await db.many(
    `SELECT kind, table_name, row_id, field, winner_value, loser_value, winner_hlc, loser_hlc, winner_op_id, loser_op_id,
            winner_device, loser_device, detail, status, dedupe_key
       FROM sync_v3_conflicts WHERE app='shop' AND account_id=$1
      ORDER BY kind, table_name, row_id, field, loser_op_id, winner_op_id, dedupe_key`, acc);
  const approvals = await db.many(
    `SELECT id, status, used_op_id FROM shop_approval_requests WHERE shop_id=$1 ORDER BY created_at, id`, acc);
  const devices = await db.many(
    `SELECT device_id, cursor, queued, last_error, app_version FROM sync_v3_devices WHERE app='shop' AND account_id=$1
      ORDER BY device_id`, acc);
  const results = responses.map((p) => ({ ...p, results: (p.results || []).map(function shape(x) {
    const { server_seq, converted, ...rest } = x;
    return { ...rest, ...(server_seq != null ? { seq: r(server_seq) } : {}), ...(converted ? { converted: converted.map(shape) } : {}) };
  }) }));
  let text = JSON.stringify({ ops: ops.map(({ server_seq, ...o }) => o), rows, conflicts, devices, approvals, results });
  shop.ids.forEach((v, i) => { text = text.split(v).join(`<id${i}>`); });
  const stamps = [...new Set(text.match(SERVER_HLC) || [])].sort();
  stamps.forEach((v, i) => { text = text.split(v).join(`<srv${i}>`); });
  return JSON.parse(text);
}

function same(a, b, label) {
  for (const k of ['results', 'ops', 'rows', 'conflicts', 'devices', 'approvals']) assert.deepEqual(b[k], a[k], `${label}: ${k}`);
  //  پاسخ بایت‌به‌بایت (ترتیبِ کلیدها هم: حالِ ردیف در حافظه همان است که دیتابیس برمی‌گرداند)
  assert.equal(JSON.stringify(b.results), JSON.stringify(a.results), `${label}: پاسخ بایت‌به‌بایت`);
}

let A;
let B;
let C;
let D;
test.before(async () => {
  await h.start();
  await require('../src/lib/sync-v1').syncSchemaTable();
  const pw = require('../src/lib/password');
  await db.query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'batch-admin','مدیر',$2,'superadmin','active',$3)`, [db.newId('adm'), await pw.hashPassword('Admin!12345'), db.now()]);
  admin = (await h.post('/api/admin/login', { username: 'batch-admin', password: 'Admin!12345' })).body.token;
  A = await shopWithStaff('تک‌تک');
  B = await shopWithStaff('دسته‌ای');
  C = await shopWithStaff('تصادفیِ تک‌تک');
  D = await shopWithStaff('تصادفیِ دسته‌ای');
  LATE = Date.now();
});
test.after(async () => { config.syncV3.batch = INITIAL; await h.stop(); });

test('پرچم پیش‌فرض خاموش است؛ ترتیبِ کلیدِ jsonb و دسته‌بندیِ پرس‌وجوها', async () => {
  const batch = require('../src/lib/sync-v3-batch');
  //  پیش‌فرض (بی env) خاموش؛ با `SYNC_V3_BATCH=1` همان env
  assert.equal(INITIAL, /^(1|true|yes|on)$/i.test(process.env.SYNC_V3_BATCH || ''));
  assert.equal(batch.on(), INITIAL);
  //  حالِ ردیف در حافظه = آنچه دیتابیس برمی‌گرداند (ترتیبِ کلید هم)
  const v = { zeta: 1, a: { yy: [3, { b: 1, aa: 2 }], x: null }, 'نام': 'ن', 10: 't', 2: 'u', ab: true, b: -0.5 };
  const back = (await db.one('SELECT $1::jsonb AS j', [JSON.stringify(v)])).j;
  assert.equal(JSON.stringify(batch.jsonbOrder(JSON.parse(JSON.stringify(v)))), JSON.stringify(back));
  for (const sql of ['SELECT 1', ' select x from t where kind = $1']) assert.equal(batch.readOnly(sql), true, sql);
  for (const sql of ['SELECT * FROM t FOR UPDATE', 'SELECT pg_advisory_xact_lock(1)', 'SELECT nextval($1)', 'UPDATE t SET a=1',
    'WITH x AS (SELECT 1) SELECT * FROM x', "SELECT 'update'", { text: 'SELECT 1' }]) assert.equal(batch.readOnly(sql), false, String(sql));
  assert.equal(batch.seesLedger('SELECT 1 FROM sync_v3_rows'), true);
  assert.equal(batch.seesLedger('SELECT 1 FROM sync_v3_devices'), false);
  assert.equal(batch.seesLedger({ text: 'x' }), true);
});

test('همان رشته ⇒ همان پاسخ و همان دفتر، ردیف، تعارض و دستگاه (تک‌تک و دسته‌ای)', async () => {
  const a = await snapshot(A, await run(A, scenario(A), false));
  const b = await snapshot(B, await run(B, scenario(B), true));
  same(a, b, 'رشتهٔ دستی');
  //  آنچه رشته باید واقعاً پوشانده باشد (اگر نه، برابری چیزی را نسنجیده)
  const flat = a.results.flatMap((p) => p.results);
  const has = (f, why) => assert.ok(flat.some(f), why);
  assert.ok(a.results.every((p) => p.status === 200), JSON.stringify(a.results.map((p) => p.error)));
  has((x) => x.status === 'duplicate', 'تکراری');
  has((x) => x.reason === 'legacy_field', 'ردِ نادرست');
  has((x) => x.reason === 'tx_rejected', 'tx ِ شکسته');
  has((x) => x.reason === 'approval_required', 'قرضِ بالای سقف');
  has((x) => x.reason === 'delete_not_allowed', 'حذفِ ممنوع');
  has((x) => x.reason === 'return_exceeds_sale', 'سقفِ برگشت (فاکتورِ همین دسته)');
  has((x) => x.reason === 'doc_invalid', 'رسیدِ بدشکل');
  assert.deepEqual(a.approvals.map((x) => x.status), ['used'], 'تأیید یک بار و در واحدِ دوم مصرف شد');
  has((x) => x.skewed === true, 'ساعتِ کج');
  has((x) => x.status === 'conflict' || x.conflicts > 0, 'تعارض');
  const kinds = new Set(a.conflicts.map((c) => c.kind));
  for (const k of ['duplicate_barcode', 'duplicate_customer', 'negative_stock', 'approval_required', 'lww']) assert.ok(kinds.has(k), `صندوق: ${k} (${[...kinds]})`);
  assert.ok(a.ops.some((o) => o.row_id.startsWith('srv.cadj.')) && a.ops.some((o) => o.row_id.startsWith('srv.late.')), 'تعدیلِ شمارش و حرکتِ دیررسیده');
  assert.ok(a.rows.some((x) => x.data && x.data.correctedAt), 'تاریخِ تصحیح‌شده');
  assert.equal(a.results[5].results.every((x) => x.status === 'duplicate' || x.status === 'rejected'), true, 'دستهٔ دوباره');
});

test('رشتهٔ تصادفی (seed ثابت) ⇒ همان نتیجه در هر دو راه', async () => {
  const steps = fuzz(0x5ca1e4, 14);
  const a = await snapshot(C, await run(C, steps, false));
  const b = await snapshot(D, await run(D, steps, true));
  same(a, b, 'رشتهٔ تصادفی');
  assert.ok(a.results.every((p) => p.status === 200));
  assert.ok(a.ops.length > 150, `opهای نشسته ${a.ops.length}`);
});

test('PostgreSQL: دستهٔ بزرگ با رفت‌وبرگشتِ بسیار کمتر', { skip: db.isPglite() && 'PGlite: شمارشِ پرس‌وجو فقط روی pg' }, async (t) => {
  const pg = require('pg');
  const orig = pg.Client.prototype.query;
  let count = 0;
  pg.Client.prototype.query = function counted(...args) { count++; return orig.apply(this, args); };
  const sizes = {};
  try {
    for (const [shop, batchOn, tag] of [[A, false, 'direct'], [B, true, 'batch']]) {
      const ops = [];
      for (let i = 0; i < 100; i++) {
        const sid = `rt-${i}`;
        const tx = `rt-tx-${i}`;
        ops.push({ op_id: `01JRT${tag}${String(4 * i).padStart(15, '0')}`, tx, hlc: H('rt', 9000 + i * 5), table: 'sales', row_id: sid, kind: 'insert', fields: { totalMinor: 100, status: 'completed' } });
        ops.push({ op_id: `01JRT${tag}${String(4 * i + 1).padStart(15, '0')}`, tx, hlc: H('rt', 9001 + i * 5), table: 'saleItems', row_id: `${sid}-i`, kind: 'insert', fields: { saleId: sid, productId: 'p1', quantityMilli: 1000 } });
        ops.push({ op_id: `01JRT${tag}${String(4 * i + 2).padStart(15, '0')}`, tx, hlc: H('rt', 9002 + i * 5), table: 'stockMovements', row_id: `${sid}-m`, kind: 'insert', fields: { productId: 'p1', qtyMilli: -10, type: 'sale', refId: sid } });
        ops.push({ op_id: `01JRT${tag}${String(4 * i + 3).padStart(15, '0')}`, tx, hlc: H('rt', 9003 + i * 5), table: 'transactions', row_id: `${sid}-t`, kind: 'insert', fields: { kind: 'sale_cash', type: 'in', amountMinor: 100, refId: sid } });
      }
      config.syncV3.batch = batchOn;
      resetAll();
      count = 0;
      const r = await h.api('POST', '/api/sync/v3/push', { token: shop.owner.accessToken, body: { device_id: 'rt', protocol: 3, ops } });
      sizes[tag] = count;
      config.syncV3.batch = INITIAL;
      assert.equal(r.status, 200, JSON.stringify(r.body));
      assert.ok(r.body.results.every((x) => x.status === 'applied'), JSON.stringify(r.body.results.find((x) => x.status !== 'applied')));
    }
  } finally {
    pg.Client.prototype.query = orig;
    config.syncV3.batch = INITIAL;
  }
  t.diagnostic(`پرس‌وجوی push ِ ۴۰۰ op: تک‌تک ${sizes.direct}، دسته‌ای ${sizes.batch}`);
  assert.ok(sizes.batch * 5 < sizes.direct, JSON.stringify(sizes));
});

test('داورِ بیرونی که خودش دفتر را بنویسد ⇒ از آن پس خواندن از دیتابیس (پس از نوشتنِ مانده‌ها)', async () => {
  const batch = require('../src/lib/sync-v3-batch');
  const ctx = { app: 'shop', accountKind: 'shop', accountId: A.id };
  const plan = [{ tx: '', items: [{ i: 0, ops: [{ op_id: 'taint-op', table: 'products', row_id: 'p1', fields: {} }] }] }];
  const DONE = new Error('rollback');
  await assert.rejects(db.tx(async (c) => {
    const s = await batch.open(c, ctx, plan);
    await s.begin('u1');
    const before = await s.load(ctx, 'products', 'p1');
    assert.equal(before.data.x, undefined);
    //  خواندنِ بی نوشتن: همان حافظه
    await s.c.query("SELECT 1 FROM sync_v3_rows WHERE app=$1 AND account_id=$2 AND table_name='products' AND row_id='p1'", ['shop', A.id]);
    await s.c.query(`UPDATE sync_v3_rows SET data = data || '{"x":1}'::jsonb
       WHERE app=$1 AND account_id=$2 AND table_name='products' AND row_id='p1'`, ['shop', A.id]);
    assert.equal((await s.load(ctx, 'products', 'p1')).data.x, 1, 'نوشتهٔ بیرونی دیده می‌شود');
    assert.equal(await s.dup(ctx, 'taint-op'), undefined);
    await s.undo('u1');
    throw DONE;
  }), (e) => e === DONE);
});
