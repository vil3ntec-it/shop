/**
 * کلاینتِ Sync v3ِ وب در **کرومیومِ واقعی** — IndexedDB ِ واقعی، fetch ِ واقعی، سرورِ
 * واقعی با `SYNC_V3_ENABLED=1` (PGlite یا pg، همان `TEST_DATABASE_URL`).
 *
 * سنجه‌ها: Push (نخستین بارگذاری، یک تراکنشِ IndexedDB برای صف و سایه)، دستگاهِ دوم
 * از Snapshot و Pull، تعارض در صندوق و برگرداندن، ۴۲۶ با صفی که از بازکردنِ دوبارهٔ
 * صفحه هم می‌گذرد، و تنظیمِ پیش‌فرضِ v1.
 *
 * اجرا:  node test/web-sync-v3.mjs   (در `npm run test:web`)
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);

function candidates() {
  const out = [];
  try { out.push(`${execSync('npm root -g', { encoding: 'utf8' }).trim()}/playwright`); } catch { /* نبود */ }
  out.push('playwright');
  return out;
}
let chromium = null;
for (const p of candidates()) {
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

process.env.SYNC_V3_ENABLED = '1';
//  صفحه از مبدأِ دیگری (وب‌سرورِ ایستا) به API می‌زند
process.env.CORS_ORIGIN = '*';
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const h = require('./helpers');
const { one } = require('../src/db');

const ROOT = path.join(import.meta.dirname, '..', '..');
const apiBase = await h.start();

const PAGE = `<!doctype html><meta charset="utf-8"><title>v3</title>
<script src="/license/sync-core.js"></script>
<script src="/license/money.js"></script>
<script src="/license/sync-v3-engine.js"></script>
<script>
  window.ledgers = {};
  window.mk = async function (name, token, protocol) {
    var key = 'ledger-' + name;
    var ledger = {
      read: function () { try { return JSON.parse(localStorage.getItem(key) || 'null') || {}; } catch (e) { return {}; } },
      write: function (d) { localStorage.setItem(key, JSON.stringify(d)); },
    };
    var e = new TohidSyncV3.Engine({
      transport: TohidSyncV3.FetchTransport(${JSON.stringify(apiBase)}, token),
      store: new TohidSyncV3.IdbStore('tohid-sync-v3-test-' + name),
      deviceId: name, ledger: ledger, protocol: protocol || 3,
    });
    e.keepLocalOnly = true;
    await e.load();
    window.ledgers[name] = ledger;
    return e;
  };
</script>`;

const TYPES = { '.js': 'text/javascript; charset=utf-8' };
const site = http.createServer((req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '');
  if (rel === '' || rel === 'v3.html') { res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); res.end(PAGE); return; }
  const file = path.join(ROOT, rel);
  if (!rel.startsWith('license/') || !file.startsWith(ROOT) || !fs.existsSync(file)) { res.writeHead(404).end('no'); return; }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => site.listen(0, '127.0.0.1', r));
const siteBase = `http://127.0.0.1:${site.address().port}`;

let failures = 0;
const results = [];
async function step(name, fn) {
  try { await fn(); results.push(['✅', name]); } catch (err) { failures++; results.push(['❌', `${name} — ${err.message}`]); }
}

/* ---- یک دکان‌دارِ واقعی ---- */
let owner = await h.newUser('دکان‌دارِ v3 ِ وب', 'shop');
const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ v3 ِ وب' } });
const shopId = shop.body?.shop?.id || shop.body?.id;
owner = await h.signIn(owner, 'shop');
const token = owner.accessToken;
const rowOf = (table, id) => one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3 AND deleted_at IS NULL`, [shopId, table, id]
);

const browser = await chromium.launch();
const errors = [];
//  دو «دستگاه» = دو کانتکستِ جدا (IndexedDB و localStorage ِ جدا)
const ctxA = await browser.newContext();
const ctxB = await browser.newContext();
const A = await ctxA.newPage();
const B = await ctxB.newPage();
for (const p of [A, B]) p.on('pageerror', (e) => errors.push(String(e.message)));
await A.goto(`${siteBase}/v3.html`);
await B.goto(`${siteBase}/v3.html`);

await step('تنظیمِ هر دستگاه: پیش‌فرض v1', async () => {
  assert.equal(await A.evaluate(() => TohidSyncV3.choice()), 'v1');
});

await step('نخستین بارگذاری: صف و سایه در یک تراکنشِ IndexedDB، Push، ردیف‌ها با fromLegacy', async () => {
  const out = await A.evaluate(async (t) => {
    localStorage.setItem('ledger-web-a', JSON.stringify({
      products: [{ id: 'wp1', name: 'شیر', salePrice: 115.5, purchasePrice: 19.95 }],
      sales: [{ id: 'ws1', total: 231, finalTotal: 231, paidAmount: 100, remaining: 131, status: 'completed' }],
      saleItems: [{ id: 'wsi1', saleId: 'ws1', productId: 'wp1', quantity: 2, unitPrice: 115.5, totalPrice: 231 }],
    }));
    const e = await mk('web-a', t);
    await e.initialize({ keepLocalOnly: true });
    const made = await e.record();
    //  بستن و بازکردنِ موتور پیش از Push: صف از IndexedDB برمی‌گردد
    const again = await mk('web-a', t);
    const queued = again.queued();
    const r = await again.sync();
    return { made, queued, r, st: again.status() };
  }, token);
  assert.equal(out.made, 3);
  assert.equal(out.queued, 3);
  assert.equal(out.r.ok, true, JSON.stringify(out.r));
  assert.equal(out.st.queued, 0);
  assert.equal((await rowOf('products', 'wp1')).data.salePriceMinor, 11550);
  assert.equal((await rowOf('products', 'wp1')).data.purchasePriceMinor, 1995);
  assert.equal((await rowOf('sales', 'ws1')).data.paidAmountMinor, 10000);
});

await step('دستگاهِ دوم: Snapshot، ویرایشِ قیمت، Pull روی دستگاهِ اول بی پژواک', async () => {
  const b = await B.evaluate(async (t) => {
    const e = await mk('web-b', t);
    const r = await e.sync();
    const d = ledgers['web-b'].read();
    const before = d.products[0].salePrice;
    d.products[0].salePrice = 120.25;
    ledgers['web-b'].write(d);
    const r2 = await e.sync();
    return { r, r2, before, queued: e.queued() };
  }, token);
  assert.equal(b.r.ok, true);
  assert.equal(b.before, 115.5);
  assert.equal(b.r2.ok, true);
  assert.equal(b.queued, 0);
  assert.equal((await rowOf('products', 'wp1')).data.salePriceMinor, 12025);
  const a = await A.evaluate(async (t) => {
    const e = await mk('web-a', t);
    const r = await e.sync();
    return { r, price: ledgers['web-a'].read().products[0].salePrice, echo: await e.record() };
  }, token);
  assert.equal(a.r.ok, true);
  assert.equal(a.price, 120.25);
  assert.equal(a.echo, 0);
});

await step('تعارض: نوشتهٔ بازنده در صندوق، برگرداندن و رسیدن به دستگاهِ دیگر', async () => {
  //  هر دو بی‌خبر از هم نام را عوض می‌کنند؛ A اول می‌فرستد، B (HLCِ تازه‌تر) بعد
  await A.evaluate(async (t) => {
    const e = await mk('web-a', t);
    const d = ledgers['web-a'].read(); d.products[0].name = 'نامِ A'; ledgers['web-a'].write(d);
    await e.record();
  }, token);
  await new Promise((r) => setTimeout(r, 5));
  await B.evaluate(async (t) => {
    const e = await mk('web-b', t);
    const d = ledgers['web-b'].read(); d.products[0].name = 'نامِ B'; ledgers['web-b'].write(d);
    await e.record();
  }, token);
  await A.evaluate(async (t) => { const e = await mk('web-a', t); return e.pushOnce(); }, token);
  await B.evaluate(async (t) => { const e = await mk('web-b', t); return e.pushOnce(); }, token);
  assert.equal((await rowOf('products', 'wp1')).data.name, 'نامِ B');
  const a = await A.evaluate(async (t) => {
    const e = await mk('web-a', t);
    const list = await e.fetchConflicts('open');
    const cf = list.find((c) => c.table === 'products' && c.field === 'name');
    const st = e.status();
    const res = cf ? await e.resolveConflict(cf.id, 'revert') : null;
    return { cf, st, res };
  }, token);
  assert.ok(a.cf, 'تعارض در صندوق نیست');
  assert.equal(a.cf.loser_value, 'نامِ A');
  assert.equal(a.st.state, 'conflict');
  assert.equal(a.res.ok, true, JSON.stringify(a.res));
  assert.equal((await rowOf('products', 'wp1')).data.name, 'نامِ A');
  const name = await B.evaluate(async (t) => { const e = await mk('web-b', t); await e.sync(); return ledgers['web-b'].read().products[0].name; }, token);
  assert.equal(name, 'نامِ A');
});

await step('۴۲۶: صف می‌ماند (حتی پس از بازکردنِ دوبارهٔ صفحه) و موتور می‌ایستد', async () => {
  const out = await A.evaluate(async (t) => {
    const e = await mk('web-a', t, 4);
    const d = ledgers['web-a'].read(); d.products.push({ id: 'wp426', name: 'نو', salePrice: 1 }); ledgers['web-a'].write(d);
    const r = await e.sync();
    return { r, st: e.status() };
  }, token);
  assert.equal(out.r.upgrade, true);
  assert.equal(out.st.state, 'upgrade');
  assert.equal(out.st.queued, 1);
  assert.equal(await rowOf('products', 'wp426'), null);
  await A.reload();
  const after = await A.evaluate(async (t) => {
    const e = await mk('web-a', t, 4);
    return { st: e.status(), r: await e.sync() };
  }, token);
  assert.equal(after.st.upgradeRequired, true);
  assert.equal(after.st.queued, 1);
  assert.equal(after.r.stopped, true);
});

await step('رابطِ window.TohidSync: با v1 (پیش‌فرض) نصب نمی‌شود؛ با v3 همان نام‌ها، با AccountTransport', async () => {
  const ctxC = await browser.newContext();
  const C = await ctxC.newPage();
  C.on('pageerror', (e) => errors.push(String(e.message)));
  await C.goto(`${siteBase}/v3.html`);
  const out = await C.evaluate(async ({ api, t }) => {
    //  همان شکلِ TohidAccount (account-code.js): authFetch پاسخ یا خطای {status, body}
    const acc = {
      account: () => ({ userId: 'u-web-c' }), deviceId: () => 'web-c-device',
      configured: () => true, isSignedIn: () => true,
      authFetch: async (p, o) => {
        const r = await fetch(api + p, {
          method: (o && o.method) || 'GET',
          headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + t },
          body: o && o.body !== undefined ? JSON.stringify(o.body) : undefined,
        });
        const text = await r.text();
        const data = text ? JSON.parse(text) : null;
        if (!r.ok) { const e = new Error((data && data.message) || 'خطا'); e.status = r.status; e.body = data; throw e; }
        return data;
      },
    };
    const before = window.TohidSync;
    const off = TohidSyncV3.install(acc);
    localStorage.setItem(TohidSyncV3.CHOICE_KEY, 'v3');
    let state = { products: [{ id: 'wpc', name: 'از رابط', salePrice: 7 }] };
    const on = TohidSyncV3.install(acc);
    TohidSync.bind({ read: () => state, write: (d) => { state = d; } });
    const r = await TohidSync.sync();
    state = Object.assign({}, state, { products: state.products.map((p) => (p.id === 'wpc' ? Object.assign({}, p, { salePrice: 8 }) : p)) });
    TohidSync.recordChange();
    const r2 = await TohidSync.sync();
    return {
      untouched: before === undefined, off, on, engine: TohidSync.engine, r, r2, st: TohidSync.status(),
      pulled: !!state.products.find((p) => p.id === 'wp1'),
    };
  }, { api: apiBase, t: token });
  await ctxC.close();
  assert.equal(out.untouched, true);
  assert.equal(out.off, false, 'با تنظیمِ پیش‌فرض نصب نمی‌شود');
  assert.equal(out.on, true);
  assert.equal(out.engine, 'v3');
  assert.equal(out.r.ok, true, JSON.stringify(out.r));
  assert.equal(out.r2.ok, true, JSON.stringify(out.r2));
  assert.equal(out.st.queued, 0);
  assert.equal(out.st.engine, 'v3');
  assert.ok(['green', 'yellow', 'red', 'grey'].includes(out.st.dot));
  assert.equal(out.pulled, true, 'ردیف‌های سرور از Snapshot رسیدند');
  assert.equal((await rowOf('products', 'wpc')).data.salePriceMinor, 800);
});

await step('بی خطای صفحه', async () => {
  assert.deepEqual(errors, []);
});

await browser.close();
site.close();
await h.stop();

for (const [mark, name] of results) console.log(`${mark} ${name}`);
if (failures) {
  console.error(`\n${failures} سنجه شکست خورد`);
  process.exit(1);
}
console.log('\nکلاینتِ Sync v3ِ وب در کرومیوم: همه سبز');
process.exit(0);
