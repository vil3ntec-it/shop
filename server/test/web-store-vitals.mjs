/**
 * WEB-15 (§۳.۱۰): LCP و INP ِ ویترین روی «گوشیِ ارزان + شبکهٔ کند» — با کرومیومِ واقعی.
 *
 *     node test/web-store-vitals.mjs            (در `npm run test:web-store`)
 *     VITALS_JSON=<پرونده> ⇒ همهٔ اجراها و میانه‌ها در JSON (برای ثبتِ پیش/پس)
 *     فقط برای کاوشِ دستی، هرگز در CI: VITALS_PROFILES=3g و VITALS_RUNS=1
 *
 * مشتری‌ها در افغانستان‌اند: اندرویدِ ارزان و 2G/3G ِ کند. پس هر بار:
 *   • گوشی: ۳۶۰×۷۶۰، DPR ۲، لمسی؛ هر بار زمینهٔ تازه ⇒ کشِ سرد؛
 *   • شبکه با CDP ِ `Network.emulateNetworkConditions`:
 *       3G ِ تند — همان پیش‌تنظیمِ «devtools» ِ موبایلِ Lighthouse (Fast 3G ِ DevTools): ۵۶۲٫۵ms برای هر درخواست،
 *                 ۱۴۷۴٫۵۶kbps پایین، ۶۷۵kbps بالا؛
 *       2G       — عددهای «2G» ِ WebPageTest: ۸۰۰ms، ۲۸۰kbps پایین، ۲۵۶kbps بالا؛
 *   • پردازنده با `Emulation.setCPUThrottlingRate`: 3G ۴×، 2G ۶× (ارزان‌ترین گوشی‌ها) — نسبت به ماشینِ مرجع، نه
 *     ماشینِ همین لحظه: پیمانهٔ ثابتِ JS (`calibrate`) سرعتِ میزبان را می‌سنجد و ضریب طوری تنظیم می‌شود که گوشیِ
 *     شبیه‌سازی‌شده روی CI ِ کندتر یا تندتر همان باشد (مثلِ benchmarkIndex ِ Lighthouse)؛
 *   • فشرده‌سازی مثلِ تولید: میانِ مرورگر و سرور همان `encode gzip` ِ `server/Caddyfile` (`vitals-store.mjs`)؛
 *     بی آن هر بایتِ JS/CSS/JSON چند برابرِ تولید روی سیمِ کند می‌رفت؛
 *   • دادهٔ واقعی‌نما (`vitals-store.mjs`): ۱۲ کالای منتشرشده در سه دسته، ۱۰ تا با عکسِ JPEG ِ واقعی (۶۰۰×۶۰۰، ~۳۰KB)
 *     — نه عکسِ ۳۰۰ بایتیِ آزمون‌های دیگر.
 *   مرزِ شناخته: این‌جا HTTP/1.1 (شش اتصال)، تولید HTTP/2 ِ Caddy.
 *
 * سنجه‌ها (میانهٔ چند بارِ سرد؛ فقط میانه با سقف سنجیده می‌شود تا یکی دو اجرای پرت آزمون را نلرزاند):
 *   • LCP (`largest-contentful-paint`) روی خانهٔ ویترین و صفحهٔ کالا (`#/p/<id>`)؛ و «LCP ِ متن» = آخرین نامزدِ متنیِ
 *     همان LCP: لحظه‌ای که سر، جست‌وجو، دسته‌ها و نام و قیمتِ کالاها کشیده شده‌اند (LCP ِ دکانِ بی‌عکس)؛
 *   • دسته و جست‌وجو نشانیِ جدا ندارند (حالتِ همان صفحه‌اند) و LCP فقط تا نخستین ورودی شمرده می‌شود ⇒ «نقاشیِ نتیجه»:
 *     از `timeStamp` ِ همان لمس/آخرین حرف تا نخستین فریم پس از کشیدنِ فهرستِ تازه (`aria-busy=false`)؛ برای جست‌وجو
 *     ۳۰۰ms ِ مکثِ عمدیِ تایپ (store.js) کم می‌شود؛
 *   • INP (Event Timing: `event` با `interactionId` و `durationThreshold: 16`) روی کارهای واقعی: افزودن به سبد، بازکردنِ
 *     سبد، بازکردنِ دسته، تایپ در جست‌وجو (هر حرف keydown/keyup ِ واقعی) و بازکردنِ کالا؛ INP ِ هر اجرا = بدترین تعامل.
 *     `first-input` همیشه گزارش می‌شود ⇒ نبودنش یعنی سنجه کار نکرد، نه «سریع بود»؛
 *   • CLS زیرِ همان شبکهٔ کند (قلم و عکس دیر می‌رسند) — همان سقفِ ۰٫۱ ِ `web-store.mjs`.
 *
 * سقف‌ها و دلیلشان کنارِ `BUDGET` پایین است.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { startVitalsStore } from './vitals-store.mjs';
import { execSync } from 'node:child_process';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
let chromium = null;
for (const p of [globalRoot && `${globalRoot}/playwright`, 'playwright']) {
  if (!p) continue;
  try { ({ chromium } = require(p)); break; } catch { /* بعدی */ }
}
if (!chromium) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);

let bad = 0;
const ok = (c, m, d = '') => { if (!c) bad++; console.log((c ? '✅ ' : '❌ ') + m + (d ? ` — ${d}` : '')); };

//  ── شبکه‌ها ──
const PROFILES = {
  '3g': { name: '3G ِ تند', latency: 562.5, down: 1474.56 * 1024 / 8, up: 675 * 1024 / 8, cpu: 4, loads: 5, journeys: 3 },
  '2g': { name: '2G', latency: 800, down: 280e3 / 8, up: 256e3 / 8, cpu: 6, loads: 3, journeys: 5 },
};
//  ── سقف‌ها (میانه) ──
//  3G: «LCP ِ متن» ≤ ۲٫۵s و INP ≤ ۲۰۰ms — همان «خوب» ِ گوگل. LCP ِ با عکس ≤ ۴s (مرزِ «ضعیف» ِ گوگل): کوتاه‌ترین زنجیرِ
//      ممکن «HTML ⇐ JS و داده (هم‌زمان) ⇐ عکس» سه رفت‌وبرگشتِ ۵۶۲٫۵ms است + بایت‌ها + کارِ پردازنده و عکسِ کالا ~۳۰KB
//      (عکسِ همگام‌شدهٔ برنامه همان عکسِ کامل است، نه نسخهٔ کوچک) ⇒ ۲٫۵s با عکس روی این شبکه شدنی نیست، ۴s هست.
//      نتیجهٔ دسته/جست‌وجو یک رفت‌وبرگشت (۵۶۲٫۵ms) + JSON + کشیدن است ⇒ ≤ ۱s.
//  2G: هر درخواست ۸۰۰ms و ۳۵KB/s: JS و CSS و قلم ِ لازمِ نخستین نقاشی (~۱۰۰KB ِ فشرده) خودش ~۳s و دو رفت‌وبرگشت
//      ۱٫۶s ⇒ «LCP ِ متن» ≤ ۶s. عکس‌ها (~۳۰۰KB) با ۳۵KB/s ~۸٫۵s ⇒ LCP ِ با عکس ≤ ۱۲s. نتیجهٔ دسته/جست‌وجو ≤ ۱٫۵s.
//      INP با پردازندهٔ ۶× هم ≤ ۲۰۰ms (میانهٔ ۵ گردش: بیشترِ INP این‌جا تأخیرِ ورودیِ خودِ مرورگر زیرِ ۶× است، پس پراکنده‌تر).
//  CLS ≤ ۰٫۱ هر دو.
const BUDGET = {
  '3g': { text: 2500, lcp: 4000, soft: 1000, inp: 200, cls: 0.1 },
  '2g': { text: 6000, lcp: 12000, soft: 1500, inp: 200, cls: 0.1 },
};
const PHONE = { viewport: { width: 360, height: 760 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'fa-AF' };
//  میانهٔ پیمانهٔ `calibrate` روی ماشینِ مرجع (ماشینِ ایجنت، ۱۴۰۵/۰۸/۱۷)؛ ضریبِ واقعی = cpu × REF_MS / میزبان
const REF_MS = 88;

const browser = await chromium.launch();
const errors = [];
const store = await startVitalsStore(browser);
for (const [c, m, d] of store.checks) ok(c, m, d);
const { base, slug } = store;

//  ── پیمانهٔ پردازنده: میانهٔ ۷ بارِ یک کارِ ثابتِ JS (آرایه، مرتب‌سازی، رشته، شیء) در صفحهٔ بی‌محدودیت ──
async function calibrate() {
  const ctx = await browser.newContext();
  const p = await ctx.newPage();
  const xs = [];
  for (let i = 0; i < 8; i++) {
    xs.push(await p.evaluate(() => {
      const t0 = performance.now();
      const a = []; for (let i = 0; i < 300000; i++) a.push((i * 7919) % 10007);
      a.sort((x, y) => x - y);
      const parts = []; for (let i = 0; i < 30000; i++) parts.push(`کالا${i}`);
      let n = 0; for (const ch of parts.join('،')) if (ch === '،') n++;
      const o = {}; for (let i = 0; i < 50000; i++) o[`k${i % 5000}`] = (o[`k${i % 5000}`] || 0) + i;
      return performance.now() - t0 + (n + a[5] + Object.keys(o).length) * 0;
    }));
  }
  await ctx.close();
  return xs.slice(1).sort((a, b) => a - b)[3]; // نخستین بار گرم‌کردن است
}
const hostMs = await calibrate();
const scale = Math.min(3, Math.max(1 / 3, REF_MS / hostMs));
console.log(`   پیمانهٔ پردازنده: میزبان ${hostMs.toFixed(1)}ms، مرجع ${REF_MS}ms ⇒ ضریبِ هر نمایه × ${scale.toFixed(2)}`);

//  ── سنجه‌ها در صفحه، پیش از هر اسکریپتِ صفحه ──
function vitalsInit() {
  const v = { lcp: 0, text: 0, el: '', url: '', events: [], fi: null, cls: 0 };
  window.__v = v;
  const name = (el) => (!el ? '(متنِ سایه‌ای/جداشده)' : el.tagName.toLowerCase() + (el.id ? `#${el.id}` : '')
    + (typeof el.className === 'string' && el.className.trim() ? `.${el.className.trim().split(/\s+/).join('.')}` : ''));
  try {
    new PerformanceObserver((l) => {
      for (const e of l.getEntries()) {
        v.lcp = e.startTime; v.url = e.url || ''; v.el = name(e.element);
        if (!e.url) v.text = e.startTime;
      }
    }).observe({ type: 'largest-contentful-paint', buffered: true });
  } catch { /* بی LCP ⇒ ۰ و آزمون قرمز */ }
  try {
    new PerformanceObserver((l) => { for (const e of l.getEntries()) if (!e.hadRecentInput) v.cls += e.value; })
      .observe({ type: 'layout-shift', buffered: true });
  } catch { /* بی layout-shift */ }
  try {
    new PerformanceObserver((l) => {
      for (const e of l.getEntries()) if (e.interactionId) v.events.push({ id: e.interactionId, start: e.startTime, dur: e.duration });
    }).observe({ type: 'event', durationThreshold: 16, buffered: true });
    new PerformanceObserver((l) => { for (const e of l.getEntries()) v.fi = { name: e.name, dur: e.duration }; })
      .observe({ type: 'first-input', buffered: true });
  } catch { /* بی Event Timing ⇒ fi نیست و آزمون قرمز */ }
}

async function phone(profile) {
  const ctx = await browser.newContext(PHONE);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.addInitScript(vitalsInit);
  const cdp = await ctx.newCDPSession(page);
  if (profile) {
    const P = PROFILES[profile];
    await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: P.latency, downloadThroughput: P.down, uploadThroughput: P.up });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: P.cpu * scale });
  }
  return { ctx, page, cdp };
}
const settle = (page, ms) => page.waitForTimeout(ms).then(() => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));

const PAGES = {
  home: { url: `/store/${slug}`, ready: '#products li[data-id] .p-name' },
  product: { url: `/store/${slug}#/p/rice`, ready: '#ps-img img' },
};
/** بارِ سرد تا آرام شدنِ شبکه (همهٔ عکس‌ها) */
async function open(page, which) {
  await page.goto(base + PAGES[which].url, { waitUntil: 'load', timeout: 120000 });
  await page.waitForSelector(PAGES[which].ready, { timeout: 60000 });
  await page.waitForLoadState('networkidle', { timeout: 60000 });
  await settle(page, 400);
}
async function load(profile, which) {
  const { ctx, page } = await phone(profile);
  try {
    await open(page, which);
    return await page.evaluate(() => ({ lcp: Math.round(window.__v.lcp), text: Math.round(window.__v.text),
      cls: Math.round(window.__v.cls * 1000) / 1000, el: window.__v.el, url: window.__v.url.replace(/^https?:\/\/[^/]+/, '') }));
  } finally { await ctx.close(); }
}

/** یک تعامل: INP ِ همان تعامل (بیشترین `duration` ِ هر interactionId از همان لحظه) */
async function interact(page, fn) {
  const t0 = await page.evaluate(() => performance.now());
  await fn();
  await settle(page, 700);
  return page.evaluate((t) => {
    const by = new Map();
    for (const e of window.__v.events) if (e.start >= t) by.set(e.id, Math.max(by.get(e.id) || 0, e.dur));
    return Math.max(0, ...by.values());
  }, t0);
}
/** «نقاشیِ نتیجه»: از timeStamp ِ آخرین رویدادِ کاربر تا فریمِ پس از `aria-busy=false` ِ همان فهرست */
const armSoft = (page, type) => page.evaluate((ev) => {
  const ul = document.getElementById('products');
  window.__t0 = 0;
  const mark = (e) => { window.__t0 = e.timeStamp; };
  document.addEventListener(ev, mark, true);
  window.__soft = new Promise((res) => {
    let busy = false;
    const mo = new MutationObserver(() => {
      const b = ul.getAttribute('aria-busy');
      if (b === 'true') busy = true;
      else if (busy && b === 'false') {
        mo.disconnect();
        document.removeEventListener(ev, mark, true);
        requestAnimationFrame(() => setTimeout(() => res(performance.now() - window.__t0), 0));
      }
    });
    mo.observe(ul, { attributes: true, attributeFilter: ['aria-busy'] });
  });
}, type);
const soft = (page) => page.evaluate(() => window.__soft).then(Math.round);
const count = (page) => page.locator('#products li[data-id]').count();

async function journey(profile) {
  const { ctx, page, cdp } = await phone(profile);
  const r = { inp: {} };
  try {
    await open(page, 'home');
    r.inp.add = await interact(page, async () => {
      await page.tap('#products li[data-id="rice"] .p-add');
      await page.waitForSelector('#cart-bar:not([hidden])');
    });
    r.inp.cart = await interact(page, async () => {
      await page.tap('#cart-bar');
      await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'));
    });
    await page.evaluate(() => { location.hash = '#/'; });
    await page.waitForFunction(() => !document.body.classList.contains('cart-open'));
    await settle(page, 300);
    await armSoft(page, 'pointerdown');
    r.inp.category = await interact(page, async () => {
      await page.tap('#cats button:has-text("بهداشتی")');
      r.category = await soft(page);
    });
    r.categoryOk = await count(page) === 3;
    await page.tap('#cats button[aria-pressed="false"]:first-child');
    await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length === 12, null, { timeout: 30000 });
    await settle(page, 300);
    await page.tap('#q');
    await armSoft(page, 'input');
    r.inp.search = await interact(page, async () => {
      //  هر حرف یک keydown/keyup ِ واقعی (CDP)؛ `pressSequentially` حرفِ فارسی را فقط insertText می‌فرستد و تعامل نمی‌سازد
      for (const ch of 'شامپو') {
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyDown', key: ch, text: ch, unmodifiedText: ch });
        await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', key: ch });
        await page.waitForTimeout(120);
      }
      r.search = await soft(page) - 300; // مکثِ عمدیِ ۳۰۰ms ِ store.js پس از آخرین حرف جزوِ کندی نیست
    });
    r.searchOk = await count(page) === 1;
    r.inp.product = await interact(page, async () => {
      await page.tap('#products li[data-id="shampoo"] .p-open');
      await page.waitForSelector('#ps-img img', { timeout: 30000 });
    });
    r.fi = await page.evaluate(() => window.__v.fi);
    r.worst = Math.max(...Object.values(r.inp));
    return r;
  } finally { await ctx.close(); }
}

const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; };

//  گرم کردن: دو بارِ بی‌محدودیت از هر صفحه (JIT ِ سرور، کشِ پایگاه)
for (let i = 0; i < 2; i++) for (const w of Object.keys(PAGES)) await load(null, w);

const out = { profiles: PROFILES, budget: BUDGET, hostMs, scale, runs: {}, median: {} };
const only = (process.env.VITALS_PROFILES || '').split(',').filter((x) => PROFILES[x]);
for (const prof of only.length ? only : Object.keys(PROFILES)) {
  const P = { ...PROFILES[prof] };
  if (Number(process.env.VITALS_RUNS)) P.loads = P.journeys = Number(process.env.VITALS_RUNS);
  const B = BUDGET[prof];
  const runs = { home: [], product: [], journey: [] };
  for (let i = 0; i < Math.max(P.loads, P.journeys); i++) {
    if (i < P.loads) runs.home.push(await load(prof, 'home'));
    if (i < P.loads) runs.product.push(await load(prof, 'product'));
    if (i < P.journeys) runs.journey.push(await journey(prof));
  }
  out.runs[prof] = runs;
  const J = runs.journey;
  const m = {
    homeText: median(runs.home.map((x) => x.text)), home: median(runs.home.map((x) => x.lcp)),
    productText: median(runs.product.map((x) => x.text)), product: median(runs.product.map((x) => x.lcp)),
    cls: Math.max(median(runs.home.map((x) => x.cls)), median(runs.product.map((x) => x.cls))),
    category: median(J.map((x) => x.category)), search: median(J.map((x) => x.search)), inp: median(J.map((x) => x.worst)),
  };
  for (const k of Object.keys(J[0].inp)) m[`inp.${k}`] = median(J.map((x) => x.inp[k]));
  out.median[prof] = m;
  const list = (xs) => xs.join('، ');
  console.log(`── ${P.name} (پردازنده ${P.cpu}× ِ مرجع = ${(P.cpu * scale).toFixed(1)}× ِ این میزبان؛ ${P.loads} بار و ${P.journeys} گردش) ──`);
  console.log(`   خانه  LCP ${list(runs.home.map((x) => x.lcp))} ms (${runs.home[0].el} ${runs.home[0].url})؛ متن ${list(runs.home.map((x) => x.text))}؛ CLS ${list(runs.home.map((x) => x.cls))}`);
  console.log(`   کالا  LCP ${list(runs.product.map((x) => x.lcp))} ms (${runs.product[0].el} ${runs.product[0].url})؛ متن ${list(runs.product.map((x) => x.text))}؛ CLS ${list(runs.product.map((x) => x.cls))}`);
  console.log(`   نتیجه  دسته ${list(J.map((x) => x.category))} ms؛ جست‌وجو ${list(J.map((x) => x.search))} ms`);
  console.log(`   INP   ${list(J.map((x) => x.worst))} ms — میانهٔ هر کار: ${Object.keys(J[0].inp).map((k) => `${k} ${m[`inp.${k}`]}`).join('، ')}`);
  ok(runs.home.every((x) => x.lcp > 0 && x.text > 0) && runs.product.every((x) => x.lcp > 0 && x.text > 0), `${P.name}: LCP سنجیده شد (largest-contentful-paint)`);
  ok(J.every((x) => x.fi && x.fi.name), `${P.name}: Event Timing سنجیده شد (first-input)`, JSON.stringify(J[0].fi));
  ok(J.every((x) => x.categoryOk && x.searchOk), `${P.name}: دستهٔ «بهداشتی» ⇒ سه کالا و جست‌وجوی «شامپو» ⇒ یک کالا`);
  ok(m.homeText <= B.text && m.productText <= B.text, `${P.name}: LCP ِ متن (خانه و صفحهٔ کالا) ≤ ${B.text}ms (WEB-15)`, `میانه ${m.homeText} و ${m.productText}ms`);
  ok(m.home <= B.lcp, `${P.name}: LCP ِ خانهٔ ویترین ≤ ${B.lcp}ms (WEB-15)`, `میانه ${m.home}ms`);
  ok(m.product <= B.lcp, `${P.name}: LCP ِ صفحهٔ کالا ≤ ${B.lcp}ms (WEB-15)`, `میانه ${m.product}ms`);
  ok(m.category <= B.soft && m.search <= B.soft, `${P.name}: نتیجهٔ دسته و جست‌وجو ≤ ${B.soft}ms (WEB-15)`, `میانه ${m.category} و ${m.search}ms`);
  ok(m.inp <= B.inp, `${P.name}: INP ≤ ${B.inp}ms (WEB-15)`, `میانه ${m.inp}ms`);
  ok(m.cls <= B.cls, `${P.name}: CLS ≤ ${B.cls} (WEB-15)`, String(m.cls));
}
if (process.env.VITALS_JSON) fs.writeFileSync(process.env.VITALS_JSON, JSON.stringify(out, null, 1));

ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await browser.close();
await store.close();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: LCP و INP زیرِ سقف');
process.exit(bad ? 1 : 0);
