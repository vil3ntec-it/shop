/**
 * نسخهٔ وب — دفتر در IndexedDB (F0-23 دنباله؛ آزمایشی و پیش‌فرض خاموش)، با مرورگرِ واقعی.
 *
 *   • خاموش (پیش‌فرض): هیچ IndexedDBی برای دفتر ساخته نمی‌شود و localStorage همان است
 *   • انتقال: رونوشتِ نویسه‌به‌نویسه + نسخهٔ پیش از انتقال؛ اصل از localStorage می‌رود
 *   • بارِ دوباره: دفتر از IndexedDB می‌آید و تغییرِ تازه مانده است
 *   • خروجی و بازیابی در همین حالت
 *   • برگشت: دفتر دوباره در localStorage، و IndexedDB به‌عنوانِ پشتیبان می‌ماند
 *   • نبودِ IndexedDB (مرورگرِ کهنه، حالتِ ناشناس): روشن نمی‌شود و چیزی جابه‌جا نمی‌شود
 *   • سهمیه: نوشتنِ ناموفق هشدارِ «پر شد» می‌دهد، دفتر در صفحه می‌ماند و با باز شدنِ جا نوشته می‌شود
 *   • ≥۷۰٪ سهمیهٔ IndexedDB ⇒ همان هشدارِ F0-23
 *   • پایگاهِ پاک‌شده: دفترِ خالی نشان داده نمی‌شود و هیچ چیز نوشته نمی‌شود
 *
 * اجرا:  node test/web-idb.mjs
 * نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const origin = `http://127.0.0.1:${server.address().port}`;
const url = `${origin}/index.html`;

const DATA_KEY = 'tohid-shop-data-v1';
const LEDGER = {
  products: [{ id: 'p1', name: 'برنج', unit: 'کیلوگرم', salePrice: 1250000, purchasePrice: 990000, category: 'خوراکی', barcode: '6260000000011' }],
  warehouseEntries: [{ id: 'w', productId: 'p1', units: 10, date: '2026-10-01' }],
  debtors: [{ id: 'c1', name: 'احمد کریمی' }],
  transactions: [{ id: 't', debtorId: 'c1', type: 'give', amount: 5000000, date: '2026-10-01', createdAt: 1 }],
  sales: [{ id: 's1', invoiceNumber: 1, finalTotal: 1250000, total: 1250000, date: '2026-10-01', createdAt: 1, status: 'completed', paymentMethod: 'cash' }],
  saleItems: [],
  nextInvoiceNo: 2,
};
//  کلیدهایی که باید با دفتر یک‌جا بروند (سایهٔ Sync v1، صندوقچهٔ حسابِ دیگر)
const SIDE = {
  'tohid-sync-v1-anon-shadow': JSON.stringify({ debtors: [{ id: 'c1', name: 'احمد کریمی' }] }),
  'tohid-ledger-vault-u2': JSON.stringify({ debtors: [{ id: 'x', name: 'دکانِ دیگر' }] }),
};
const AUTH = JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 });

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.stack || e.message}`); }
};

/* ---------- ابزارهای داخلِ صفحه ---------- */
const nativeKeys = (pg) => pg.evaluate(() => {
  const out = [];
  for (let i = 0; i < localStorage.length; i++) out.push(localStorage.key(i));
  return out.sort();
});
const idbDump = (pg) => pg.evaluate(() => new Promise((resolve, reject) => {
  const req = indexedDB.open('tohid-ledger');
  req.onerror = () => reject(req.error);
  req.onsuccess = () => {
    const db = req.result;
    if (!db.objectStoreNames.contains('kv')) { db.close(); resolve({}); return; }
    const out = {};
    const t = db.transaction('kv', 'readonly');
    const c = t.objectStore('kv').openCursor();
    c.onsuccess = () => { const k = c.result; if (k) { out[k.key] = k.value; k.continue(); } };
    t.oncomplete = () => { db.close(); resolve(out); };
  };
}));
const ledgerDbExists = (pg) => pg.evaluate(async () => (await indexedDB.databases()).some(d => d.name === 'tohid-ledger'));

async function seeded(ctx, { extra = {}, ledger = LEDGER } = {}) {
  const pg = await ctx.newPage({ viewport: { width: 1366, height: 800 } });
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.goto(url);
  await pg.evaluate(({ d, side, auth }) => {
    localStorage.setItem('tohid-shop-auth-v1', auth);
    localStorage.setItem('tohid-shop-data-v1', d);
    for (const k of Object.keys(side)) localStorage.setItem(k, side[k]);
  }, { d: JSON.stringify(ledger), side: extra, auth: AUTH });
  await pg.reload();
  await pg.waitForFunction(() => window.TohidLedgerStore && window.TohidLedgerStore.isReady());
  return { pg, errors };
}

async function toggleIdb(pg, on) {
  await pg.evaluate(() => goTo('settings'));
  const box = pg.locator('#in-ledger-idb');
  await box.waitFor({ state: 'visible' });
  assert.equal(await box.isChecked(), !on, 'کلید از پیش در همان حالت است');
  await box.click();
  await pg.waitForFunction(() => !document.getElementById('in-ledger-idb').disabled, null, { timeout: 15000 });
}

async function reloadReady(pg) {
  await pg.reload();
  await pg.waitForFunction(() => window.TohidLedgerStore && window.TohidLedgerStore.isReady() && typeof ledgerInMemory !== 'undefined' && ledgerInMemory === true, null, { timeout: 15000 });
}

/* ---------- ۱) خاموش: هیچ چیز عوض نمی‌شود ---------- */
await step('پیش‌فرض خاموش: نه پایگاهی ساخته می‌شود، نه localStorage جابه‌جا می‌شود', async () => {
  const ctx = await browser.newContext();
  const { pg, errors } = await seeded(ctx, { extra: SIDE });
  await pg.waitForTimeout(500);
  const st = await pg.evaluate(() => ({
    mode: TohidLedgerStore.mode(),
    patched: !!Storage.prototype.getItem.tohidLedger,
    debtors: debtors.length,
    raw: localStorage.getItem('tohid-shop-data-v1'),
  }));
  assert.equal(st.mode, 'ls');
  assert.equal(st.patched, false, 'خاموش نباید localStorage را دست بزند');
  assert.equal(st.debtors, 1);
  assert.ok(st.raw && JSON.parse(st.raw).debtors.length === 1);
  assert.equal(await ledgerDbExists(pg), false, 'خاموش نباید پایگاهِ IndexedDBِ دفتر را بسازد');
  await pg.evaluate(() => goTo('settings'));
  assert.equal(await pg.locator('#in-ledger-idb').isChecked(), false);
  assert.deepEqual(errors, []);
  await ctx.close();
});

/* ---------- ۲ تا ۵) انتقال، بارِ دوباره، خروجی/بازیابی، برگشت — روی یک دستگاه ---------- */
await step('انتقال، بارِ دوباره، خروجی و بازیابی، و برگشت — بی گم شدنِ حتی یک نویسه', async () => {
  const ctx = await browser.newContext({ acceptDownloads: true });
  const { pg, errors } = await seeded(ctx, { extra: SIDE });
  const before = await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1'));

  // ۲) انتقال
  await toggleIdb(pg, true);
  assert.equal(await pg.evaluate(() => TohidLedgerStore.mode()), 'idb');
  assert.equal(await pg.locator('#in-ledger-idb').isChecked(), true);
  const keys = await nativeKeys(pg);
  for (const k of [DATA_KEY, ...Object.keys(SIDE)]) assert.ok(!keys.includes(k), `«${k}» هنوز در localStorage است`);
  assert.ok(keys.includes('tohid-ledger-store-v1'));
  let db = await idbDump(pg);
  assert.equal(db[DATA_KEY], before, 'رونوشت نویسه‌به‌نویسه همان نیست');
  assert.equal(db[`backup:${DATA_KEY}`].value, before, 'نسخهٔ پیش از انتقال نگه داشته نشد');
  for (const k of Object.keys(SIDE)) assert.equal(db[k], SIDE[k], `«${k}» جابه‌جا نشد`);
  assert.ok(db.meta && db.meta.migratedAt);
  // کدِ قدیمی همان localStorage.getItem را می‌خواند و همان را می‌گیرد
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1')), before);
  assert.equal(await pg.evaluate(() => debtors.length), 1);

  // ۳) تغییرِ تازه از همان راهِ همیشگی (saveState) و بارِ دوباره
  await pg.evaluate(async () => {
    debtors.push({ id: 'c2', name: 'محمود نوری', createdAt: 2 });
    saveState();
    await TohidLedgerStore.flush();
  });
  db = await idbDump(pg);
  assert.equal(JSON.parse(db[DATA_KEY]).debtors.length, 2, 'تغییر در IndexedDB ننشست');
  await reloadReady(pg);
  assert.equal(await pg.evaluate(() => debtors.map(d => d.name).join('،')), 'احمد کریمی،محمود نوری');
  assert.equal(await pg.locator('#ledger-gate').count(), 0, 'پرده پس از رسیدنِ دفتر باید برود');
  assert.ok(!(await nativeKeys(pg)).includes(DATA_KEY), 'پس از بارِ دوباره دفتر نباید به localStorage برگردد');

  // ۴) خروجی همان دفتر را می‌دهد
  await pg.evaluate(() => goTo('settings'));
  const [dl] = await Promise.all([pg.waitForEvent('download'), pg.click('#btn-export-backup')]);
  const file = path.join(os.tmpdir(), `tohid-idb-${process.pid}.json`);
  await dl.saveAs(file);
  const exported = JSON.parse(fs.readFileSync(file, 'utf8'));
  assert.equal(exported.debtors.length, 2, 'خروجی همهٔ دفتر را ندارد');

  //    بازیابی: فایلی با یک قرض‌دارِ دیگر
  exported.debtors = [{ id: 'c9', name: 'زهرا احمدی', createdAt: 9 }];
  fs.writeFileSync(file, JSON.stringify(exported));
  await pg.setInputFiles('#in-import-backup-file', file);
  await pg.waitForSelector('#modal-confirm.open');
  await pg.click('#btn-confirm-ok');
  await pg.waitForFunction(() => debtors.length === 1 && debtors[0].id === 'c9');
  await pg.evaluate(() => TohidLedgerStore.flush());
  await reloadReady(pg);
  assert.equal(await pg.evaluate(() => debtors.map(d => d.name).join('،')), 'زهرا احمدی', 'بازیابی پس از بارِ دوباره نماند');
  fs.rmSync(file, { force: true });

  // ۵) برگشت به localStorage
  const routedNow = await pg.evaluate((side) => {
    const out = {};
    for (const k of ['tohid-shop-data-v1', ...side]) out[k] = localStorage.getItem(k);
    return out;
  }, Object.keys(SIDE));
  const live = routedNow[DATA_KEY];
  await toggleIdb(pg, false);
  assert.equal(await pg.evaluate(() => TohidLedgerStore.mode()), 'ls');
  assert.equal(await pg.evaluate(() => !!Storage.prototype.getItem.tohidLedger), false);
  const back = await nativeKeys(pg);
  assert.ok(back.includes(DATA_KEY) && !back.includes('tohid-ledger-store-v1'));
  assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1')), live);
  //  سایهٔ Sync v1 را خودِ موتور در این مدت نوشته؛ هر چه بود، همان برمی‌گردد
  for (const k of Object.keys(SIDE)) assert.equal(await pg.evaluate((x) => localStorage.getItem(x), k), routedNow[k], `«${k}» برنگشت`);
  assert.equal(routedNow['tohid-ledger-vault-u2'], SIDE['tohid-ledger-vault-u2']);
  db = await idbDump(pg);
  assert.equal(db[DATA_KEY], live, 'IndexedDB پس از برگشت باید پشتیبان بماند، نه پاک شود');
  await reloadReady(pg);
  assert.equal(await pg.evaluate(() => TohidLedgerStore.mode()), 'ls');
  assert.equal(await pg.evaluate(() => debtors.map(d => d.name).join('،')), 'زهرا احمدی');
  assert.deepEqual(errors, [], `خطای صفحه: ${errors.join(' | ')}`);
  await ctx.close();
});

/* ---------- ۶) نبودِ IndexedDB ---------- */
const NO_IDB = {
  'مرورگرِ بی IndexedDB (Safariِ کهنه، WebViewِ بسته)': () => {
    Object.defineProperty(window, 'indexedDB', { configurable: true, get: () => undefined });
  },
  'IndexedDB بسته (حالتِ ناشناسِ Firefox: open خطا می‌دهد)': () => {
    IDBFactory.prototype.open = function () { throw new DOMException('A mutation operation was attempted on a database that did not allow mutations.', 'InvalidStateError'); };
  },
};
for (const [name, block] of Object.entries(NO_IDB)) {
  await step(`${name}: روشن نمی‌شود، دفتر در localStorage می‌ماند و چیزی گم نمی‌شود`, async () => {
    const ctx = await browser.newContext();
    await ctx.addInitScript(block);
    const { pg } = await seeded(ctx);
    const before = await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1'));
    await toggleIdb(pg, true);
    assert.equal(await pg.evaluate(() => TohidLedgerStore.mode()), 'ls');
    assert.equal(await pg.locator('#in-ledger-idb').isChecked(), false, 'کلید باید به خاموش برگردد');
    assert.match(await pg.evaluate(() => document.getElementById('toast-msg').textContent), /IndexedDB نمی‌دهد/);
    assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-ledger-store-v1')), null);
    assert.equal(await pg.evaluate(() => localStorage.getItem('tohid-shop-data-v1')), before);
    await pg.evaluate(() => { debtors.push({ id: 'c3', name: 'نادر', createdAt: 3 }); saveState(); });
    await reloadReady(pg);
    assert.equal(await pg.evaluate(() => debtors.length), 2, 'پس از ردِ IndexedDB، نوشتن در localStorage باید مثل قبل کار کند');
    await ctx.close();
  });
}

/* ---------- ۷) سهمیه ---------- */
await step('سهمیهٔ IndexedDB: ≥۷۰٪ ⇒ هشدار؛ پر ⇒ «پر شد»، دفتر در صفحه می‌ماند و با باز شدنِ جا نوشته می‌شود', async () => {
  const ctx = await browser.newContext();
  /*  ⚠️ کرومیوم با `Storage.overrideQuotaForOrigin` فقط عددِ `estimate()` را عوض
      می‌کند و نوشتنِ IndexedDB را رد نمی‌کند (سنجیده شد: ۱٫۲MB در سهمیهٔ ۱۰۰KB
      نوشته شد). پس «پر شدن» همان‌طور ساخته می‌شود که مرورگر گزارشش می‌کند:
      تراکنش با QuotaExceededError لغو می‌شود. فقط سمتِ آزمون است. */
  await ctx.addInitScript(() => {
    const put = IDBObjectStore.prototype.put;
    IDBObjectStore.prototype.put = function () {
      const req = put.apply(this, arguments);
      const t = this.transaction;
      if (window.__idbQuotaFull && !t.__full) {
        //  مثلِ مرورگر: پس از ثبتِ همهٔ نوشتن‌ها، سرِ commit
        t.__full = true;
        queueMicrotask(() => {
          Object.defineProperty(t, 'error', { configurable: true, value: new DOMException('The quota has been exceeded.', 'QuotaExceededError') });
          try { t.abort(); } catch { /* تمام شده */ }
        });
      }
      return req;
    };
  });
  const { pg } = await seeded(ctx);
  await toggleIdb(pg, true);
  const cdp = await ctx.newCDPSession(pg);
  const used = await pg.evaluate(async () => (await navigator.storage.estimate()).usage);

  // ≥۷۰٪: همان پنجرهٔ F0-23 با درصدِ واقعی
  await cdp.send('Storage.overrideQuotaForOrigin', { origin, quotaSize: Math.ceil(used / 0.8) });
  await pg.evaluate(async () => { await TohidLedgerStore.refreshUsage(); checkStorageBudget(); });
  await pg.waitForSelector('#modal-storage.open', { timeout: 5000 });
  assert.match(await pg.locator('#storage-title').innerText(), /٪/);
  await pg.evaluate(() => closeModal('modal-storage'));

  // پر: نوشتنِ یک مگابایت وقتی جا نیست
  await cdp.send('Storage.overrideQuotaForOrigin', { origin });
  await pg.evaluate(() => {
    window.__idbQuotaFull = true;
    products[0].notes = 'ی'.repeat(1024 * 1024);
    saveState();
  });
  await pg.waitForFunction(() => TohidLedgerStore.lastError() && TohidLedgerStore.lastError().name === 'QuotaExceededError', null, { timeout: 10000 });
  await pg.waitForSelector('#modal-storage.open', { timeout: 5000 });
  assert.match(await pg.locator('#storage-title').innerText(), /پر شد/);
  assert.equal(await pg.evaluate(() => JSON.parse(localStorage.getItem('tohid-shop-data-v1')).products[0].notes.length), 1024 * 1024,
    'دفترِ صفحه نباید با شکستِ نوشتن عقب برود');
  let db = await idbDump(pg);
  assert.ok(!JSON.parse(db[DATA_KEY]).products[0].notes, 'نوشتنِ ناموفق نباید نیمه‌کاره روی دیسک بنشیند');

  // جا باز شد: نوشتنِ بعدی همان تغییرِ جامانده را هم می‌برد
  await pg.evaluate(async () => { window.__idbQuotaFull = false; await TohidLedgerStore.flush(); });
  assert.equal(await pg.evaluate(() => TohidLedgerStore.lastError()), null);
  db = await idbDump(pg);
  assert.equal(JSON.parse(db[DATA_KEY]).products[0].notes.length, 1024 * 1024, 'پس از باز شدنِ جا، تغییر نوشته نشد');
  await reloadReady(pg);
  assert.equal(await pg.evaluate(() => products[0].notes.length), 1024 * 1024);
  await ctx.close();
});

/* ---------- ۷ب) دفترِ دیررس: صفحه پیش از رسیدنِ دفتر بالا آمده است ---------- */
await step('دفترِ دیررس: پرده، هیچ نوشتنی، همگام‌سازی صبر می‌کند؛ با رسیدنِ دفتر همه‌چیز سرِ جایش', async () => {
  const ctx = await browser.newContext();
  const { pg, errors } = await seeded(ctx);
  await toggleIdb(pg, true);
  await pg.evaluate(async () => { debtors.push({ id: 'c2', name: 'محمود نوری', createdAt: 2 }); saveState(); await TohidLedgerStore.flush(); });
  const onDisk = (await idbDump(pg))[DATA_KEY];
  //  بازِ IndexedDB دو ثانیه دیر جواب می‌دهد — صفحه حتماً پیش از دفتر بالا می‌آید
  await ctx.addInitScript(() => {
    const open = IDBFactory.prototype.open;
    IDBFactory.prototype.open = function (name) {
      const req = open.apply(this, arguments);
      if (name === 'tohid-ledger' && !window.__ledgerOpenedOnce) {
        window.__ledgerOpenedOnce = true;
        let h = null;
        Object.defineProperty(req, 'onsuccess', { configurable: true, get: () => h, set: (fn) => { h = fn; } });
        req.addEventListener('success', (e) => setTimeout(() => { if (h) h.call(req, e); }, 2000));
      }
      return req;
    };
  });
  await pg.reload({ waitUntil: 'domcontentloaded' });
  await pg.waitForSelector('#ledger-gate[role="status"]', { state: 'attached', timeout: 5000 });
  const early = await pg.evaluate(() => {
    saveState();      // پیش از رسیدنِ دفتر: نباید دفترِ خالی را بنویسد
    return {
      inMemory: ledgerInMemory,
      debtors: debtors.length,
      raw: (() => { try { return localStorage.getItem('tohid-shop-data-v1'); } catch (e) { return 'throw'; } })(),
      write: (() => { try { localStorage.setItem('tohid-shop-data-v1', '{}'); return 'wrote'; } catch (e) { return e.name; } })(),
      //  موتورِ همگام‌سازی: درِ ورودش منتظرِ دفتر است، و پل چیزی نمی‌دهد
      held: !!window.TohidSync && /ledgerLoaded/.test(String(window.TohidSync.start)),
    };
  });
  assert.deepEqual(early, { inMemory: false, debtors: 0, raw: null, write: 'InvalidStateError', held: true });
  await pg.waitForFunction(() => ledgerInMemory === true && debtors.length === 2, null, { timeout: 10000 });
  assert.equal(await pg.locator('#ledger-gate').count(), 0);
  assert.equal(await pg.evaluate(() => /ledgerLoaded/.test(String(window.TohidSync.start))), false, 'موتور پس از رسیدنِ دفتر باید به حالِ خودش برگردد');
  await pg.evaluate(() => goTo('debtors'));
  assert.match(await pg.locator('#page-debtors').innerText(), /محمود نوری/, 'دفترِ رسیده دوباره کشیده نشد');
  assert.equal((await idbDump(pg))[DATA_KEY], onDisk, 'پیش از رسیدنِ دفتر چیزی روی دیسک نوشته شد');
  assert.deepEqual(errors, [], `خطای صفحه: ${errors.join(' | ')}`);
  await ctx.close();
});

/* ---------- ۸) پایگاهِ پاک‌شده در حالی که نشانه می‌گوید IndexedDB ---------- */
await step('پایگاهِ دفتر پاک شده: دفترِ خالی نشان داده نمی‌شود، چیزی نوشته نمی‌شود، همگام‌سازی چیزی نمی‌خواند', async () => {
  const ctx = await browser.newContext();
  const { pg } = await seeded(ctx);
  await toggleIdb(pg, true);
  await pg.evaluate(() => new Promise((resolve, reject) => {
    const r = indexedDB.deleteDatabase('tohid-ledger');
    r.onsuccess = resolve; r.onerror = () => reject(r.error);
  }));
  await pg.reload();
  await pg.waitForSelector('#ledger-gate[role="alertdialog"]', { timeout: 15000 });
  const st = await pg.evaluate(() => {
    saveState();
    return {
      ready: TohidLedgerStore.isReady(),
      inMemory: ledgerInMemory,
      native: localStorage.length ? Array.from({ length: localStorage.length }, (_, i) => localStorage.key(i)) : [],
      bridge: window.TohidSync ? 'bound' : 'none',
    };
  });
  assert.equal(st.ready, false);
  assert.equal(st.inMemory, false);
  assert.ok(!st.native.includes(DATA_KEY), 'دفترِ خالی نباید در localStorage نوشته شود');
  const db = await idbDump(pg);
  assert.equal(db[DATA_KEY], undefined, 'دفترِ خالی نباید در IndexedDB نوشته شود');
  assert.match(await pg.locator('#ledger-gate').innerText(), /باز نشد/);
  await ctx.close();
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} سنجه قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
