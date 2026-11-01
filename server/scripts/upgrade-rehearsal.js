#!/usr/bin/env node
'use strict';
/**
 * تمرینِ ارتقا (OPS-03@29: «release و migration در staging آزمایش شود»، §۲۹.۸، §۲۹.۹).
 *
 * آزمونِ CI مهاجرت‌ها را روی پایگاهِ **خالی** می‌دواند. خطرِ واقعیِ انتشار جای دیگری است:
 * سرورِ نسخهٔ پیش با دادهٔ واقعی‌نما کار کرده و مهاجرت‌های تازه روی **همان** داده اجرا می‌شوند.
 * این ابزار همان را تمرین می‌کند (ورک‌فلوی `staging-rehearsal.yml`):
 *
 *   ۱) seed         روی سرورِ نسخهٔ پیش (base) فقط از راهِ HTTP ِ عمومی — ثبت‌نامِ سه‌پله‌ای، ساختنِ
 *                   دکان، push ِ کالا، فروش، `$inc` ِ موجودی، قرض‌دار، ویرایش و حذف — پس به schema ِ
 *                   هیچ نسخه‌ای وابسته نیست.
 *   ۲) fingerprint  اثرِ انگشتِ پایگاه: برای هر جدول شمارِ ردیف و هشِ بی‌ترتیبِ **ستون‌های همان لحظه**.
 *   ۳) (بیرون)      سرورِ base خاموش ⇒ `npm run migrate` ِ نسخهٔ تازه روی همان پایگاه (با زمان).
 *   ۴) fingerprint  دوباره، پیش از روشن شدنِ سرورِ تازه (پس فقط مهاجرت‌ها چیزی عوض کرده‌اند).
 *   ۵) check        مقایسه + `verify-data` ِ نسخهٔ تازه + دودآزمایی روی سرورِ تازه: `/api/ready`،
 *                   ورودِ همهٔ صاحبانِ seed و دریافتِ همهٔ opها از صفر با دستگاهِ تازه.
 *
 * قاعدهٔ مقایسه (`compare`): جدول یا ستونِ برداشته‌شده، کم شدنِ ردیف، و عوض شدنِ محتوای ستون‌های
 * پیشینِ جدول‌های حیاتی (دفترِ همگام‌سازی، حساب، دکان، سفارش، پول) ⇒ خطا. عوض شدنِ محتوای جدولِ
 * دیگر ⇒ هشدار با نامِ جدول (شاید اصلاحِ عمدیِ داده). جدول/ستون/ردیفِ تازه ⇒ فقط گزارش.
 *
 * ⛔ هیچ دادهٔ واقعی‌ای این‌جا نیست؛ همه ساختگی و قطعی. ⛔ پایگاهی که نامش `test`، `bench` یا
 * `staging` ندارد پذیرفته نمی‌شود (این ابزار می‌نویسد).
 *
 *   node scripts/upgrade-rehearsal.js seed --url http://127.0.0.1:4100 --out state.json [--shops 3 --products 60 --sales 300]
 *   node scripts/upgrade-rehearsal.js fingerprint --out fp.json            (DATABASE_URL)
 *   node scripts/upgrade-rehearsal.js check --before a.json --after b.json --state state.json --url http://127.0.0.1:4100
 * کدِ خروج: ۰ سالم · ۲ خطای تمرین · ۱ خطای اجرا.
 */
const fs = require('node:fs');

//  جدول‌هایی که مهاجرت هرگز نباید محتوای ستون‌های پیشینشان را عوض کند
const CRITICAL = Object.freeze([
  'oplog', 'sync_rows', 'sync_conflicts', 'sync_devices', 'users', 'shops', 'shop_members',
  'online_orders', 'online_order_items', 'online_order_events', 'online_order_refunds',
  'subscriptions', 'audit_logs', 'hash_chain', 'sms_usage',
]);
const DB_NAME_OK = /(test|bench|staging)/i;

function guardDatabase(url = process.env.DATABASE_URL || '') {
  if (/^pglite:/i.test(url)) return;
  let name = '';
  try { name = decodeURIComponent(new URL(url).pathname.replace(/^\//, '')); } catch { /* پایین */ }
  if (!DB_NAME_OK.test(name)) {
    throw new Error(`⛔ نامِ پایگاه باید test، bench یا staging داشته باشد (داده شد: «${name || url}»)`);
  }
}

/* ── ۲) اثرِ انگشت ─────────────────────────────────────────────────── */

const ident = (s) => `"${String(s).replace(/"/g, '""')}"`;

/**
 * { tables: { name: { rows, columns: [..], hash } } } — هش روی `columns` ِ داده‌شده (برای «پس»:
 * همان ستون‌های «پیش») تا افزودنِ ستونِ تازه با پیش‌فرض محتوای پیشین را «عوض‌شده» نشان ندهد.
 */
async function fingerprint(q, { columnsOf = null } = {}) {
  const tables = (await q(
    `SELECT table_name AS t FROM information_schema.tables
      WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' ORDER BY table_name`
  )).map((r) => r.t);
  const out = {};
  for (const t of tables) {
    const all = (await q(
      `SELECT column_name AS c FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = $1 ORDER BY ordinal_position`, [t]
    )).map((r) => r.c);
    const want = columnsOf && columnsOf[t] ? columnsOf[t].filter((c) => all.includes(c)) : all;
    const row = want.length ? `md5(ROW(${want.map(ident).join(', ')})::text)` : `''`;
    const r = (await q(
      `SELECT COUNT(*)::bigint AS n, md5(COALESCE(string_agg(x, '' ORDER BY x), '')) AS h
         FROM (SELECT ${row} AS x FROM ${ident(t)}) s`
    ))[0];
    out[t] = { rows: Number(r.n), columns: all, hashed: want, hash: r.h };
  }
  return { tables: out };
}

/* ── ۵) مقایسه ─────────────────────────────────────────────────────── */

/** ⇒ { errors: [...], warnings: [...], info: [...] } — فقط متن؛ هیچ مقدارِ ردیفی. */
function compare(before, after, { critical = CRITICAL } = {}) {
  const errors = [];
  const warnings = [];
  const info = [];
  for (const [t, b] of Object.entries(before.tables || {})) {
    const a = after.tables?.[t];
    if (!a) { errors.push(`جدولِ ${t} پس از مهاجرت نیست`); continue; }
    const gone = b.columns.filter((c) => !a.columns.includes(c));
    if (gone.length) errors.push(`ستونِ ${gone.join('، ')} از ${t} برداشته شد`);
    const added = a.columns.filter((c) => !b.columns.includes(c));
    if (added.length) info.push(`ستونِ تازه در ${t}: ${added.join('، ')}`);
    if (a.rows < b.rows) errors.push(`${t}: ${b.rows - a.rows} ردیف کم شد (${b.rows} ⇐ ${a.rows})`);
    else if (a.rows > b.rows) info.push(`${t}: ${a.rows - b.rows} ردیفِ تازه`);
    //  هشِ «پس» روی همان ستون‌های «پیش» گرفته شده؛ ستونِ برداشته‌شده بالا خطاست
    const sameCols = !gone.length && a.hashed && a.hashed.join() === b.columns.join();
    if (sameCols && a.rows === b.rows && a.hash !== b.hash) {
      (critical.includes(t) ? errors : warnings).push(`محتوای ستون‌های پیشینِ ${t} عوض شد`);
    }
  }
  for (const t of Object.keys(after.tables || {})) {
    if (!before.tables?.[t]) info.push(`جدولِ تازه: ${t}`);
  }
  return { errors, warnings, info };
}

/* ── HTTP ───────────────────────────────────────────────────────────── */

async function http(base, method, path, { token, body, headers = {} } = {}) {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json', 'X-App': 'shop',
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(60000),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* متن */ }
  return { status: res.status, body: json ?? text };
}
function must(r, what, ok = (s) => s >= 200 && s < 300) {
  if (!ok(r.status)) throw new Error(`${what}: ${r.status} ${JSON.stringify(r.body).slice(0, 300)}`);
  return r.body;
}

/* ── ۱) seed ─────────────────────────────────────────────────────────── */

/** شناسه‌های قطعی و ULID‌شکل (۲۶ نویسه)؛ seed دوباره ⇒ همان opها ⇒ «تکراری» */
const opId = (shop, n) => `01RH${String(shop).padStart(2, '0')}${String(n).padStart(20, '0')}`;

function seedOps(shop, { products, sales }) {
  const ops = [];
  let n = 0;
  const ts = Date.UTC(2026, 9, 1);
  for (let p = 0; p < products; p++) {
    ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'products', row_id: `p${p}`, type: 'insert',
      fields: { name: `کالای ${p}`, price: 1000 + p * 25, stock: 100, barcode: `62${String(shop * 100000 + p).padStart(11, '0')}` } });
  }
  for (let d = 0; d < 10; d++) {
    ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'debtors', row_id: `d${d}`, type: 'insert', fields: { name: `مشتری ${d}`, balance: 0 } });
  }
  for (let s = 0; s < sales; s++) {
    const p = (s * 7) % products;
    const qty = 1 + (s % 3);
    ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'sales', row_id: `s${s}`, type: 'insert',
      fields: { total: (1000 + p * 25) * qty, items: 1, paid: s % 8 === 0 ? 0 : (1000 + p * 25) * qty } });
    ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'products', row_id: `p${p}`, type: 'update', fields: { stock: { $inc: -qty } } });
    if (s % 8 === 0) {
      ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'debtors', row_id: `d${s % 10}`, type: 'update', fields: { balance: { $inc: (1000 + p * 25) * qty } } });
    }
  }
  //  ویرایشِ نام و حذف — op ِ بی فیلد
  ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'products', row_id: 'p1', type: 'update', fields: { name: 'کالای یک، نامِ تازه' } });
  ops.push({ op_id: opId(shop, ++n), ts: ts + n, table: 'products', row_id: `p${products - 1}`, type: 'delete', fields: {} });
  return ops;
}

async function seed(base, { shops = 3, products = 60, sales = 300 } = {}) {
  const state = { base, shops: [] };
  for (let i = 1; i <= shops; i++) {
    const email = `rehearsal-${i}@staging.local`;
    const password = 'Rehearsal!2026';
    const name = `صاحبِ تمرین ${i}`;
    const started = must(await http(base, 'POST', '/api/auth/register/start', { body: { name, email, password } }), 'ثبت‌نام، پلهٔ ۱');
    if (!started.devCode) throw new Error('سرورِ base کدِ آزمایشی برنگرداند — NODE_ENV=test و راهِ ارسالِ log لازم است');
    const verified = must(await http(base, 'POST', '/api/auth/register/verify', { body: { email, code: started.devCode } }), 'ثبت‌نام، پلهٔ ۲');
    const done = must(await http(base, 'POST', '/api/auth/register/complete', {
      body: { ticket: verified.ticket, name, password, app: 'shop', terms: { accepted: true },
        device: { deviceId: `rh-dev-${i}`, name: 'تمرین', platform: 'test' } },
    }), 'ثبت‌نام، پلهٔ ۳');
    const token = done.accessToken;
    const shop = must(await http(base, 'POST', '/api/shop', { token, body: { name: `دکانِ تمرین ${i}` } }), 'ساختنِ دکان').shop;
    const ops = seedOps(i, { products, sales });
    for (let k = 0; k < ops.length; k += 200) {
      const r = must(await http(base, 'POST', '/api/sync/v1/push', {
        token, headers: { 'X-Device': `rh-dev-${i}` }, body: { device_id: `rh-dev-${i}`, schema_version: 2, ops: ops.slice(k, k + 200) },
      }), `push ِ دکانِ ${i}`);
      const bad = (r.results || []).filter((x) => x.status === 'rejected');
      if (bad.length) throw new Error(`push ِ دکانِ ${i}: ${bad.length} op رد شد (${bad[0].reason})`);
    }
    state.shops.push({ email, password, shopId: shop.id, ops: ops.length });
  }
  return state;
}

/* ── ۵) دودآزمایی روی سرورِ تازه ─────────────────────────────────────── */

async function smoke(base, state) {
  const errors = [];
  const ready = await http(base, 'GET', '/api/ready');
  if (ready.status !== 200) errors.push(`/api/ready ‏${ready.status}`);
  for (const s of state.shops) {
    const login = await http(base, 'POST', '/api/auth/login', {
      body: { identifier: s.email, password: s.password, app: 'shop', device: { deviceId: `rh-check-${s.shopId}`, name: 'تمرین', platform: 'test' } },
    });
    if (login.status !== 200 || !login.body?.accessToken) { errors.push(`ورودِ ${s.email}: ${login.status}`); continue; }
    let since = 0;
    let got = 0;
    for (let page = 0; page < 1000; page++) {
      const r = await http(base, 'GET', `/api/sync/v1/pull?device_id=rh-fresh-${s.shopId}&since=${since}`, { token: login.body.accessToken });
      if (r.status !== 200) { errors.push(`pull ِ ${s.shopId}: ${r.status}`); break; }
      got += r.body.ops.length;
      since = r.body.cursor;
      if (!r.body.has_more) break;
    }
    if (got !== s.ops) errors.push(`دکانِ ${s.shopId}: ${got} op از ${s.ops} رسید`);
  }
  return errors;
}

/* ── خطِ فرمان ──────────────────────────────────────────────────────── */

async function main(argv) {
  const [cmd, ...rest] = argv;
  const arg = (k, d = '') => { const i = rest.indexOf(k); return i >= 0 && rest[i + 1] ? rest[i + 1] : d; };
  const readJson = (f) => JSON.parse(fs.readFileSync(f, 'utf8'));
  if (cmd === 'seed') {
    const state = await seed(arg('--url'), {
      shops: Number(arg('--shops', 3)), products: Number(arg('--products', 60)), sales: Number(arg('--sales', 300)),
    });
    fs.writeFileSync(arg('--out', 'rehearsal-state.json'), JSON.stringify(state, null, 2));
    console.log(`seed: ${state.shops.length} دکان، ${state.shops.reduce((a, s) => a + s.ops, 0)} op`);
    return 0;
  }
  if (cmd === 'fingerprint') {
    guardDatabase();
    const db = require('../src/db');
    try {
      const prev = arg('--columns-from') ? readJson(arg('--columns-from')) : null;
      const columnsOf = prev ? Object.fromEntries(Object.entries(prev.tables).map(([t, v]) => [t, v.columns])) : null;
      const fp = await fingerprint((s, p) => db.many(s, p), { columnsOf });
      fs.writeFileSync(arg('--out', 'fingerprint.json'), JSON.stringify(fp, null, 2));
      const tables = Object.values(fp.tables);
      console.log(`fingerprint: ${tables.length} جدول، ${tables.reduce((a, t) => a + t.rows, 0)} ردیف`);
    } finally {
      await db.closeDb();
    }
    return 0;
  }
  if (cmd === 'check') {
    const res = compare(readJson(arg('--before')), readJson(arg('--after')));
    const smokeErrors = arg('--url') && arg('--state') ? await smoke(arg('--url'), readJson(arg('--state'))) : [];
    for (const m of res.info) console.log(`  · ${m}`);
    for (const m of res.warnings) console.log(`  ⚠ ${m}`);
    for (const m of [...res.errors, ...smokeErrors]) console.log(`  ✖ ${m}`);
    const bad = res.errors.length + smokeErrors.length;
    console.log(bad ? `\n✖ تمرینِ ارتقا: ${bad} خطا` : `\n✔ تمرینِ ارتقا سالم${res.warnings.length ? ` (${res.warnings.length} هشدار)` : ''}`);
    return bad ? 2 : 0;
  }
  throw new Error('کاربرد: seed | fingerprint | check (شرح در سرِ همین فایل)');
}

if (require.main === module) {
  main(process.argv.slice(2)).then((code) => process.exit(code), (err) => {
    console.error(`✖ ${err.message}`);
    process.exit(1);
  });
}

module.exports = { CRITICAL, fingerprint, compare, seed, seedOps, smoke, guardDatabase, main };
