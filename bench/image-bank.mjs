#!/usr/bin/env node
/**
 * bench — بانکِ تصاویر زیرِ بار: جست‌وجوی صاحبِ دکان (IMG-BANK-14، IMG-BANK-18؛ v3 ۳۷.۹ و ۳۷.۱۰).
 *
 *   node bench/image-bank.mjs                                  # PGlite در حافظه، ۱۰٬۰۰۰ کالا، ۸ کاربرِ هم‌زمان
 *   node bench/image-bank.mjs --products 50000 --images 600 --users 16 --ops 40 --json out.json
 *   node bench/image-bank.mjs --db postgres://shop:shoppass@127.0.0.1:5432/shop_bench --scale 0.1
 *
 * ⛔ پایگاه داده را **پاک می‌کند** (همان `resetDatabase` ِ آزمون‌ها)؛ فقط `pglite:` یا پایگاهی با
 * `test`/`bench` در نام. دادهٔ ساختگی با seed ثابت؛ هیچ دادهٔ واقعی.
 *
 * چه ثبت می‌شود (§۳۷.۱۰: «اندازهٔ بانک، تعدادِ هم‌زمان، نوعِ دستگاه، cache سرد/گرم»):
 *   • محیط: پردازنده، هسته‌ها، حافظه، Node، PGlite یا PostgreSQL؛ اندازهٔ بانک و شمارِ کاربرِ هم‌زمان.
 *   • برای هر دسته p50/p95/p99/max، شمار، خطا و بایتِ پاسخ؛ یک دورِ «سرد» و یک دورِ «گرم» با همان پرس‌وجوها:
 *       barcode   — بارکدِ معتبرِ یک کالا (هدفِ §۳۷.۱۰: p95 ≤ ۵۰۰ms)
 *       name      — نامِ کاملِ کالا، صفحهٔ اول (هدف: p95 ≤ ۱۰۰۰ms)
 *       spelling  — همان نام با «ي/ك» ِ عربی و بی نیم‌فاصله
 *       browse    — کلمهٔ پرتکرار + برند + «فقط با عکس»
 *       page1/2   — صفحهٔ اول و دوم (۵۰تایی) با cursor
 *       thumb     — هر عکسِ کوچکِ صفحهٔ اول (همان نشانی‌ای که برنامه می‌خواند)
 *       firstPage — از شروعِ جست‌وجو تا رسیدنِ همهٔ عکس‌های کوچکِ صفحهٔ اول (موازی، مثلِ برنامه)
 *   • دقت: بارکد ⇒ همان کالا اولِ فهرست و «قطعی»؛ نام و املای دیگر ⇒ همان کالا در صفحهٔ اول.
 *   • IMG-BANK-14: هیچ پاسخی نشانیِ فایلِ اصل یا بایتِ عکس ندارد؛ صفحه ≤ ۵۰ ردیف؛ عکسِ کوچک `immutable`.
 *   • منابع: زمانِ CPU ِ فرایند، بیشینهٔ RSS و heap (نمونه‌برداری هر ۵۰ms)، و درخواست در ثانیه.
 * فقط گزارش است (مثلِ `bench/server.mjs`)؛ رد نمی‌کند، چون ماشینِ CI مرجع نیست. `--assert` فقط خطا،
 * دقت و نبودِ فایلِ اصل را سخت می‌گیرد (نه زمان را).
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const require = createRequire(import.meta.url);
const SERVER = path.join(import.meta.dirname, '..', 'server');

/** چندک‌ها — همان قاعدهٔ `bench/server.mjs` */
export function stat(xs) {
  if (!xs.length) return { n: 0, p50: 0, p95: 0, p99: 0, max: 0 };
  const s = xs.slice().sort((a, b) => a - b);
  const q = (p) => s[Math.min(s.length - 1, Math.max(0, Math.ceil(p * s.length) - 1))];
  const r1 = (v) => Math.round(v * 10) / 10;
  return { n: s.length, p50: r1(q(0.5)), p95: r1(q(0.95)), p99: r1(q(0.99)), max: r1(s[s.length - 1]) };
}

export const TARGETS = { barcode: 500, name: 1000 };

/** مولدِ ثابت (mulberry32) — همان داده در هر اجرا */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const ITEMS = ['چای سبز', 'چای سیاه', 'برنج سیلا', 'روغن نباتی', 'صابون گل', 'شامپو', 'بیسکویت کره‌ای', 'شیر خشک',
  'رب گوجه', 'قند شکسته', 'نمک یددار', 'ماکارونی', 'عدس', 'لوبیا قرمز', 'دستمال کاغذی', 'پودر لباس‌شویی',
  'خمیر دندان', 'آب معدنی', 'نوشابه', 'کیک شکلاتی'];
const BRANDS = ['احمد', 'کابل', 'هرات', 'آریانا', 'گل', 'نان', 'سیمرغ', 'پامیر', 'بلخ', 'کوهستان'];
const SIZES = [['250', 'g'], ['500', 'g'], ['1', 'kg'], ['5', 'kg'], ['1', 'l'], ['330', 'ml']];

/** کالای iِ بانک — نام یکتا با شماره؛ نامِ هم‌خانواده با اندازهٔ دیگر هم هست («مبهم» ِ واقعی) */
export function productAt(i, F) {
  const item = ITEMS[i % ITEMS.length];
  const brand = BRANDS[Math.floor(i / ITEMS.length) % BRANDS.length];
  const [sv, su] = SIZES[Math.floor(i / (ITEMS.length * BRANDS.length)) % SIZES.length];
  const serial = Math.floor(i / (ITEMS.length * BRANDS.length * SIZES.length));
  return {
    name: `${item} ${brand} ${serial + 1}`, brand, size_value: sv, size_unit: su, category: item.split(' ')[0],
    gtin: F.ean13('626' + String(i).padStart(9, '0')), alt_names: [`${item} ${serial + 1}`],
  };
}

/**
 *  اتصالِ keep-alive که سرور پس از چند ثانیه بیکاری بسته (پس از کاشتنِ کالاها) — یک بار دوباره؛
 *  همان راهِ `bench/server.mjs`. درخواستِ تکراری بی‌خطر است: بارگذاریِ تکراری فقط «تکراری» نشان داده می‌شود.
 */
async function again(fn) {
  try { return await fn(); } catch (e) {
    if (e && e.cause && e.cause.code === 'ECONNRESET') return fn();
    throw e;
  }
}

/** همان نام با «ي/ك» ِ عربی و بی نیم‌فاصله — املایی که کاربرِ دری/فارسی واقعاً می‌زند */
export const respell = (s) => s.replace(/ی/g, 'ي').replace(/ک/g, 'ك').replace(/‌/g, '');

/**
 *  سنجش روی سروری که `h.start()` بالا آورده. `products` کالا (با SQL، از همان `productInput`/
 *  `insertProduct` ِ کتابخانه) و `images` عکسِ تأییدشده با نسخهٔ کوچک (از راهِ API ِ مدیر) می‌کارد.
 */
export async function runImageBankBench({
  h, products = 10000, images = 300, users = 8, ops = 30, seed = 37, log = () => {},
} = {}) {
  const bank = require(path.join(SERVER, 'src', 'lib', 'image-bank.js'));
  const F = require(path.join(SERVER, 'test', 'image-fixtures.js'));
  const { query, newId, now } = require(path.join(SERVER, 'src', 'db.js'));
  const pw = require(path.join(SERVER, 'src', 'lib', 'password.js'));
  const result = {
    at: new Date().toISOString(),
    env: {
      cpu: os.cpus()[0]?.model, cores: os.cpus().length, memGB: Math.round(os.totalmem() / 2 ** 30),
      os: `${os.platform()} ${os.release()}`, node: process.version,
      driver: /^pglite:/i.test(process.env.TEST_DATABASE_URL || '') ? 'pglite' : 'postgres',
      network: 'loopback (127.0.0.1) — بی تأخیرِ شبکهٔ واقعی',
    },
    bank: { products, images }, users, opsPerUser: ops, targets: TARGETS,
    seed: {}, cold: {}, warm: {}, accuracy: {}, payload: {}, resources: {},
  };

  //  مدیرِ ارشد، مدیرِ عادی، یک دکان، پرچم‌ها
  const t0 = performance.now();
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'bench-root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  const root = (await h.post('/api/admin/login', { username: 'bench-root', password: 'Root!12345' })).body.token;
  await h.post('/api/admin/admins', { username: 'bench-clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  const clerk = (await h.post('/api/admin/login', { username: 'bench-clerk', password: 'Clerk!12345' })).body.token;
  const owner = await h.newUser('بنچ', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ سنجش' }, { token: owner.accessToken })).body.shop;
  const flag = (scope, key) => h.put('/api/admin/pump/live-config', { scope, key, value: true }, { token: root });
  await flag('all', 'image_bank.central');
  await flag(shop.id, 'shop.image_bank');
  const token = owner.accessToken;

  //  کالاها — دسته‌های ۵۰۰تایی در تراکنش
  const ids = new Array(products);
  for (let start = 0; start < products; start += 500) {
    await bank.tx(async (c) => {
      for (let i = start; i < Math.min(products, start + 500); i++) {
        ids[i] = await bank.insertProduct(c, bank.productInput(productAt(i, F)), 'bench');
      }
    });
  }
  result.seed.productsMs = Math.round(performance.now() - t0);

  //  عکس‌های تأییدشده با نسخهٔ کوچک — روی کالاهای پخش در کلِ بانک
  const t1 = performance.now();
  const step = Math.max(1, Math.floor(products / Math.max(1, images)));
  let seeded = 0;
  const withImage = [];
  for (let k = 0; k < images && k * step < products; k++) {
    const pid = ids[k * step];
    withImage.push(k * step);
    const up = await again(() => h.raw('POST', `/api/admin/image-bank/images?bank_product_id=${pid}&license_status=licensed&commercial_use=true&license_source=bench`,
      F.jpeg(800, 600, { seed: seed * 1000 + k }), { token: clerk }));
    if (up.status !== 201) throw new Error(`بارگذاری: ${up.status} ${JSON.stringify(up.body)}`);
    const th = await again(() => h.raw('PUT', `/api/admin/image-bank/images/${up.body.id}/v/1/thumb`, F.jpeg(400, 300, { seed: seed * 1000 + k + 500000 }), { token: clerk }));
    if (th.status !== 200) throw new Error(`نسخهٔ کوچک: ${th.status} ${JSON.stringify(th.body)}`);
    const ok = await again(() => h.post(`/api/admin/image-bank/images/${up.body.id}/approve`, { reason: 'سنجش' }, { token: root }));
    if (ok.status !== 200) throw new Error(`تأیید: ${ok.status} ${JSON.stringify(ok.body)}`);
    seeded++;
  }
  result.bank.images = seeded;
  result.seed.imagesMs = Math.round(performance.now() - t1);
  log(`کاشته شد: ${products} کالا (${result.seed.productsMs}ms)، ${seeded} عکس (${result.seed.imagesMs}ms)`);

  //  ── بار ──────────────────────────────────────────────────────────────
  const base = await h.start();
  const auth = { Authorization: `Bearer ${token}` };
  const S = '/api/shop/images';
  async function get(p) {
    const t = performance.now();
    const res = await again(() => fetch(base + p, { headers: auth }));
    const buf = Buffer.from(await res.arrayBuffer());
    return { ms: performance.now() - t, status: res.status, buf, headers: res.headers };
  }

  //  هر کاربر فهرستِ ثابتِ پرس‌وجوهای خودش را دارد؛ دورِ گرم همان فهرست است
  const plans = [];
  for (let u = 0; u < users; u++) {
    const r = rng(seed + u);
    const plan = [];
    for (let i = 0; i < ops; i++) {
      const kind = i % 5;
      //  مرور از کالایی که عکس دارد شروع می‌شود تا عکس‌های کوچکِ صفحهٔ اول واقعاً خوانده شوند
      const idx = kind === 3 && withImage.length ? withImage[Math.floor(r() * withImage.length)] : Math.floor(r() * products);
      plan.push({ kind, idx });
    }
    plans.push(plan);
  }

  const peak = { rss: 0, heap: 0 };
  const sampler = setInterval(() => {
    const m = process.memoryUsage();
    peak.rss = Math.max(peak.rss, m.rss); peak.heap = Math.max(peak.heap, m.heapUsed);
  }, 50);

  async function round(label) {
    const xs = { barcode: [], name: [], spelling: [], browse: [], page1: [], page2: [], thumb: [], firstPage: [] };
    const acc = { barcode: [0, 0], name: [0, 0], spelling: [0, 0] };
    const pay = { maxItems: 0, maxPageBytes: 0, originals: 0, notImmutable: 0 };
    let errors = 0; let firstError = null; let requests = 0;
    const fail = (what) => { errors++; firstError ||= what; };
    const check = (r, what) => {
      requests++;
      if (r.status !== 200) { fail(`${what} ${r.status} ${r.buf.toString('utf8').slice(0, 160)}`); return null; }
      const text = r.buf.toString('utf8');
      if (/\/original\b|"data"|base64/.test(text)) pay.originals++;
      pay.maxPageBytes = Math.max(pay.maxPageBytes, r.buf.length);
      const body = JSON.parse(text);
      pay.maxItems = Math.max(pay.maxItems, body.items.length);
      return body;
    };
    const cpu0 = process.cpuUsage();
    const w0 = performance.now();
    await Promise.all(plans.map(async (plan) => {
      for (const { kind, idx } of plan) {
        const p = productAt(idx, F);
        const want = ids[idx];
        if (kind === 0) {
          const r = await get(`${S}/search?barcode=${p.gtin}`);
          xs.barcode.push(r.ms);
          const b = check(r, 'barcode');
          acc.barcode[1]++;
          if (b && b.items[0]?.product.id === want && b.items[0].match.certain) acc.barcode[0]++;
        } else if (kind === 1 || kind === 2) {
          const q = kind === 1 ? p.name : respell(p.name);
          const r = await get(`${S}/search?q=${encodeURIComponent(q)}`);
          (kind === 1 ? xs.name : xs.spelling).push(r.ms);
          const b = check(r, kind === 1 ? 'name' : 'spelling');
          const a = kind === 1 ? acc.name : acc.spelling;
          a[1]++;
          if (b && b.items.some((it) => it.product.id === want)) a[0]++;
        } else if (kind === 3) {
          //  مرور: کلمهٔ پرتکرار + برند + فقط با عکس، و عکس‌های کوچکِ صفحهٔ اول موازی (مثلِ برنامه)
          const q = p.category;
          const t = performance.now();
          const r = await get(`${S}/search?q=${encodeURIComponent(q)}&brand=${encodeURIComponent(p.brand)}&has_image=1`);
          xs.browse.push(r.ms);
          const b = check(r, 'browse');
          if (!b) continue;
          const urls = b.items.flatMap((it) => it.images.slice(0, 1)).map((im) => im.urls.thumb).filter(Boolean);
          await Promise.all(urls.map(async (u) => {
            const f = await get(u.replace(/^\/api\/v1/, '/api'));
            requests++;
            xs.thumb.push(f.ms);
            if (f.status !== 200) fail(`thumb ${f.status}`);
            else if (!/immutable/.test(f.headers.get('cache-control') || '')) pay.notImmutable++;
          }));
          xs.firstPage.push(performance.now() - t);
        } else {
          const r1 = await get(`${S}/search?q=${encodeURIComponent(p.category)}&limit=50`);
          xs.page1.push(r1.ms);
          const b = check(r1, 'page1');
          if (!b || !b.next_cursor) continue;
          const r2 = await get(`${S}/search?q=${encodeURIComponent(p.category)}&limit=50&cursor=${b.next_cursor}`);
          xs.page2.push(r2.ms);
          check(r2, 'page2');
        }
      }
    }));
    const wall = performance.now() - w0;
    const cpu = process.cpuUsage(cpu0);
    const out = {};
    for (const [k, v] of Object.entries(xs)) out[k] = stat(v);
    out.errors = errors; if (firstError) out.firstError = firstError;
    out.requests = requests; out.wallMs = Math.round(wall); out.rps = Math.round((requests / wall) * 1000);
    out.cpuMs = Math.round((cpu.user + cpu.system) / 1000);
    const accOut = Object.fromEntries(Object.entries(acc).map(([k, [ok, n]]) => [k, { ok, n, rate: n ? ok / n : 1 }]));
    log(`${label}: ${requests} درخواست در ${out.wallMs}ms (${out.rps}/s)، خطا ${errors}`);
    for (const k of Object.keys(xs)) {
      const s = out[k];
      if (s.n) log(`  ${label}.${k.padEnd(10)} n ${String(s.n).padStart(5)}  p50 ${String(s.p50).padStart(7)}  p95 ${String(s.p95).padStart(7)}  p99 ${String(s.p99).padStart(7)}  max ${String(s.max).padStart(7)} ms`);
    }
    return { out, acc: accOut, pay };
  }

  const cold = await round('سرد');
  const warm = await round('گرم');
  clearInterval(sampler);
  result.cold = cold.out;
  result.warm = warm.out;
  result.accuracy = { cold: cold.acc, warm: warm.acc };
  result.payload = {
    maxItemsPerPage: Math.max(cold.pay.maxItems, warm.pay.maxItems),
    maxPageBytes: Math.max(cold.pay.maxPageBytes, warm.pay.maxPageBytes),
    responsesWithOriginal: cold.pay.originals + warm.pay.originals,
    thumbsNotImmutable: cold.pay.notImmutable + warm.pay.notImmutable,
  };
  result.resources = { peakRssMB: Math.round(peak.rss / 2 ** 20), peakHeapMB: Math.round(peak.heap / 2 ** 20) };
  result.verdict = {
    barcodeP95: { ms: result.warm.barcode.p95, target: TARGETS.barcode, pass: result.warm.barcode.p95 <= TARGETS.barcode },
    nameP95: { ms: result.warm.name.p95, target: TARGETS.name, pass: result.warm.name.p95 <= TARGETS.name },
  };
  return result;
}

/** شکستِ سختِ `--assert` (و آزمونِ CI): خطا، دقت، فایلِ اصل، سقفِ صفحه — نه زمان */
export function problemsOf(r) {
  const out = [];
  for (const phase of ['cold', 'warm']) {
    if (r[phase].errors) out.push(`${phase}: ${r[phase].errors} خطا (${r[phase].firstError})`);
    for (const [k, a] of Object.entries(r.accuracy[phase])) if (a.ok !== a.n) out.push(`${phase}: دقتِ ${k} ${a.ok}/${a.n}`);
  }
  if (r.payload.responsesWithOriginal) out.push(`${r.payload.responsesWithOriginal} پاسخ نشانیِ فایلِ اصل یا بایت داشت`);
  if (r.payload.maxItemsPerPage > 50) out.push(`صفحهٔ ${r.payload.maxItemsPerPage} ردیفی`);
  if (r.payload.thumbsNotImmutable) out.push(`${r.payload.thumbsNotImmutable} عکسِ کوچک بی immutable`);
  return out;
}

//  ── خطِ فرمان ─────────────────────────────────────────────────────────
if (import.meta.url === `file://${process.argv[1]}`) {
  const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : d; };
  const DB = arg('--db', process.env.BENCH_DATABASE_URL || 'pglite:memory');
  if (!/^pglite:/i.test(DB)) {
    let name = '';
    try { name = new URL(DB).pathname.replace(/^\//, ''); } catch { /* پایین */ }
    if (!/(test|bench)/i.test(name)) {
      console.error(`⛔ این ابزار پایگاه داده را پاک می‌کند؛ نامِ پایگاه باید test یا bench داشته باشد (داده شد: «${name || DB}»)`);
      process.exit(2);
    }
  }
  process.env.TEST_DATABASE_URL = DB;
  Object.assign(process.env, { RATE_SYNC_MAX: '10000000', RATE_SYNC_IP_MAX: '10000000', RATE_ADMIN_MAX: '100000' });
  const log = console.log.bind(console);
  console.log = (...a) => {
    const m = typeof a[0] === 'string' && /^(GET|POST|PUT|PATCH|DELETE) \/\S* (\d{3}) (\d+)ms$/.exec(a[0]);
    if (m && Number(m[2]) < 400 && Number(m[3]) <= 1000) return;
    if (typeof a[0] === 'string' && a[0].startsWith('[otp]')) return;
    log(...a);
  };
  const h = require(path.join(SERVER, 'test', 'helpers.js'));
  await h.start();
  let code = 0;
  try {
    const scale = Number(arg('--scale', 1)) || 1;
    const r = await runImageBankBench({
      h, products: Math.max(1, Math.round(Number(arg('--products', 10000)) * scale)),
      images: Math.max(1, Math.round(Number(arg('--images', 300)) * scale)),
      users: Number(arg('--users', 8)), ops: Number(arg('--ops', 30)), log,
    });
    log(`دقت (گرم): ${Object.entries(r.accuracy.warm).map(([k, a]) => `${k} ${a.ok}/${a.n}`).join('، ')}`);
    log(`IMG-BANK-14: بیشینهٔ ردیفِ صفحه ${r.payload.maxItemsPerPage}، بیشینهٔ بایتِ صفحه ${r.payload.maxPageBytes}، پاسخِ با فایلِ اصل ${r.payload.responsesWithOriginal}`);
    log(`منابع: RSS ${r.resources.peakRssMB}MB، heap ${r.resources.peakHeapMB}MB؛ CPU سرد ${r.cold.cpuMs}ms، گرم ${r.warm.cpuMs}ms`);
    log(`هدفِ §۳۷.۱۰ (گرم): بارکد p95 ${r.verdict.barcodeP95.ms}ms ≤ ${TARGETS.barcode} ⇒ ${r.verdict.barcodeP95.pass ? 'پاس' : 'نه'}؛ نام p95 ${r.verdict.nameP95.ms}ms ≤ ${TARGETS.name} ⇒ ${r.verdict.nameP95.pass ? 'پاس' : 'نه'}`);
    const out = arg('--json', '');
    if (out) fs.writeFileSync(out, JSON.stringify(r, null, 2));
    const probs = problemsOf(r);
    if (probs.length) { log(`⛔ ${probs.join(' | ')}`); if (process.argv.includes('--assert')) code = 1; }
  } finally {
    await h.stop();
  }
  process.exit(code);
}
