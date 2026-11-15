/**
 * S92 (§۱۹.۱۲ «گوشیِ ضعیف/اینترنتِ ضعیف») و S110 (§۲۰.۱۲ «کاهشِ حرکت و گوشیِ ضعیف»)، بخشِ ویترین — کرومیومِ واقعی روی همان
 * دکانِ واقعی‌نمای WEB-15 (`vitals-store.mjs`: ۱۲ کالا، ۱۰ عکسِ JPEG با نسخهٔ کارت، پشتِ gzip ِ Caddyfile) و همان گوشیِ ۳۶۰×۷۶۰ و
 * 2G ِ `web-store-vitals.mjs` (۸۰۰ms، ۲۸۰kbps پایین، ۲۵۶kbps بالا، پردازنده ۶×).
 *
 *     node test/web-store-weak.mjs            (در `npm run test:web-store`)
 *
 *   • زمانِ بار (تا دیده شدنِ کارت‌ها) و **حجمِ انتقال** ِ بارِ سردِ خانه — همهٔ بایت‌های سیم با CDP ِ `Network.loadingFinished`
 *     (`encodedDataLength`، سرآیند هم) ≤ `BUDGET.bytes`؛ کارت‌ها فقط `/thumb` می‌گیرند، هیچ `/photo` تا برگهٔ کالا؛
 *   • **حافظه**: heap ِ JS پس از گردشِ خرید (خانه، دسته، برگهٔ کالا، سبد) و GC ≤ `BUDGET.heap` (`Runtime.getHeapUsage`)؛
 *   • **خطای عکس** (S126 هم): کارتی ۵۰۴، کارتی بریده و کارتی با بایتِ خراب ⇒ جای‌نگهدارِ حرفِ نخست (`K.photo`)، بی آیکنِ شکسته،
 *     همان اندازهٔ کارت؛ افزودن کار می‌کند و عکسِ سبد هم همان حرف؛ برگهٔ کالا با عکسِ ۵۰۴ همان. همهٔ عکس‌ها بریده ⇒ خرید تا
 *     «ثبت» و صفحهٔ سفارش بی هیچ عکس؛
 *   • **برگشت از پس‌زمینه**: `Page.setWebLifecycleState` (frozen ⇐ active)، پنهان/پیدا و قطعِ شبکه در پس‌زمینه ⇒ سبد همان و
 *     افزودنِ بعدی کار می‌کند؛ رفتن به نشانیِ دیگر و «برگشت» ⇒ همان سبد؛
 *   • **شکستِ بارگذاری سبد را گم نمی‌کند**: سبدِ مهمان، سپس سرور در دسترس نیست (هر `/api/store/…` بریده) و بارِ دوباره ⇒ پیامِ خطا،
 *     سبد در localStorage همان بایت‌ها؛ نیمه‌شکست (فقط قیمتِ سبد، `?ids=`) ⇒ سبد همان و هیچ قلمی «ناموجود» نمی‌شود؛ شبکهٔ
 *     برگشته ⇒ همان سبد و کاتالوگ؛
 *   • **کاهشِ حرکت** (S110): با `prefers-reduced-motion: reduce` هیچ انیمیشن یا گذارِ «حرکتی» (transform/translate/scale/جا/اندازه)
 *     در مسیرِ خرید (افزودن، سبد، شمار، برگهٔ کالا، «ادامه») اجرا نمی‌شود — و همان مسیر بی آن حرکت دارد (پس سنجه کور نیست) —
 *     و مسیر زیرِ همان 2G و پردازندهٔ ۶× تا فرمِ ثبت کار می‌کند.
 *
 * مرزِ شناخته: شبیه‌سازی است، نه گوشیِ واقعی؛ «حافظهٔ کم» ِ دستگاه (کشتنِ زبانه در پس‌زمینه) و برنامهٔ خریدِ اندروید این‌جا
 * سنجیده نمی‌شوند. نبودِ پلی‌رایت قرمز است، نه «رد» (F0-17).
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL } from './browser-guard.mjs';
import { startVitalsStore, PRODUCTS } from './vitals-store.mjs';
import { execSync } from 'node:child_process';
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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

//  همان 2G ِ `web-store-vitals.mjs` (عددهای «2G» ِ WebPageTest) و همان گوشی
const G2 = { latency: 800, down: 280e3 / 8, up: 256e3 / 8, cpu: 6 };
const PHONE = { viewport: { width: 360, height: 760 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'fa-AF' };
//  سقف‌ها: بارِ سردِ خانه امروز ~۲۴۰KB (HTML، CSS، JS، قلم، داده و ده کارتِ ~۱۲KB)؛ ۳۲۰KB یعنی ~۹ ثانیه روی 2G و جا برای
//  رشدِ کوچک — هر عکسِ کامل به‌جای کارت (~۳۰KB × ۱۰) یا کتابخانهٔ تازهٔ بارِ اول از آن می‌گذرد. heap پس از GC امروز ~۴MB؛ ۱۶MB
//  یعنی هر نشتِ بزرگ (کاتالوگِ نگه‌داشته، شنونده‌های انباشته) دیده می‌شود.
const BUDGET = { bytes: 320 * 1024, heap: 16 * 1024 * 1024 };
const NAME = Object.fromEntries(PRODUCTS.map(([id, name]) => [id, name]));
const PHOTO = /\/api\/store\/[^/]+\/products\/([^/?]+)\/(thumb|photo)/;

const browser = await chromium.launch();
const errors = [];
const store = await startVitalsStore(browser);
for (const [c, m, d] of store.checks) ok(c, m, d);
const { base, slug } = store;
const HOME = `${base}/store/${slug}`;
const CART_KEY = `store-cart:${slug}`;

async function phone({ slow = false, reduce = false } = {}) {
  const ctx = await browser.newContext({ ...PHONE, reducedMotion: reduce ? 'reduce' : 'no-preference' });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(String(e)));
  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Network.enable');
  if (slow) {
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: G2.latency, downloadThroughput: G2.down, uploadThroughput: G2.up });
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: G2.cpu });
  }
  return { ctx, page, cdp };
}
const settle = (page, ms = 300) => page.waitForTimeout(ms).then(() => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
const cards = (page) => page.waitForSelector('#products li[data-id] .p-name', { timeout: 90000 });
const cartOf = (page) => page.evaluate((k) => localStorage.getItem(k), CART_KEY);
const barCount = (page) => page.evaluate(() => (document.getElementById('cart-bar').hidden ? 0 : document.querySelectorAll('#bar-thumbs .thumb').length));
async function add(page, id) {
  await page.tap(`#products li[data-id="${id}"] .p-add`);
  await page.waitForSelector('#cart-bar:not([hidden])', { timeout: 30000 });
}
/** عکس‌های شکسته‌ای که کاربر می‌بیند: `img` ِ کامل‌شده با عرضِ طبیعیِ ۰ */
const broken = (page) => page.evaluate(() => [...document.querySelectorAll('img')]
  .filter((i) => i.complete && i.naturalWidth === 0 && i.getClientRects().length).map((i) => i.getAttribute('src')));

// ── ۱. بارِ سرد روی 2G: زمان، حجمِ سیم، فقط کارت‌ها؛ سپس گردشِ خرید و heap؛ سپس برگشت از پس‌زمینه ─────────────
{
  const { ctx, page, cdp } = await phone({ slow: true });
  const urls = new Map();
  let bytes = 0;
  cdp.on('Network.requestWillBeSent', (e) => urls.set(e.requestId, e.request.url));
  cdp.on('Network.loadingFinished', (e) => { bytes += e.encodedDataLength; });
  const t0 = Date.now();
  await page.goto(HOME, { waitUntil: 'load', timeout: 120000 });
  await cards(page);
  const usable = Date.now() - t0;
  await page.waitForLoadState('networkidle', { timeout: 120000 });
  await settle(page);
  const all = [...urls.values()];
  const photos = all.filter((u) => /\/photo(\?|$)/.test(u));
  const thumbs = all.filter((u) => /\/thumb(\?|$)/.test(u));
  console.log(`   2G: کارت‌ها پس از ${usable}ms دیده شدند؛ ${all.length} درخواست، ${(bytes / 1024).toFixed(1)}KB روی سیم (${thumbs.length} کارت، ${photos.length} عکسِ کامل)`);
  ok(bytes > 0 && bytes <= BUDGET.bytes, `S92: حجمِ انتقالِ بارِ سردِ خانه روی 2G ≤ ${BUDGET.bytes / 1024}KB`, `${(bytes / 1024).toFixed(1)}KB`);
  ok(photos.length === 0 && thumbs.length >= 1, 'S92: کارت‌ها فقط نسخهٔ کوچک (`/thumb`)؛ هیچ عکسِ کامل پیش از برگهٔ کالا', `${thumbs.length}/${photos.length}`);

  //  گردشِ خرید روی همان 2G
  await add(page, 'rice');
  await add(page, 'soap');
  await page.tap('#cats button:has-text("بهداشتی")');
  await page.waitForFunction(() => document.querySelectorAll('#products li[data-id]').length === 3, null, { timeout: 60000 });
  await page.tap('#products li[data-id="shampoo"] .p-open');
  await page.waitForSelector('#ps-img img', { timeout: 60000 });
  await page.evaluate(() => { location.hash = '#/cart'; });
  await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 30000 });
  await page.evaluate(() => { location.hash = '#/'; });
  await settle(page, 500);
  await cdp.send('HeapProfiler.enable');
  await cdp.send('HeapProfiler.collectGarbage');
  const heap = await cdp.send('Runtime.getHeapUsage');
  console.log(`   heap ِ JS پس از گردش و GC: ${(heap.usedSize / 1048576).toFixed(2)}MB از ${(heap.totalSize / 1048576).toFixed(2)}MB`);
  ok(heap.usedSize > 0 && heap.usedSize <= BUDGET.heap, `S92: حافظهٔ JS پس از گردشِ خرید ≤ ${BUDGET.heap / 1048576}MB`, `${(heap.usedSize / 1048576).toFixed(2)}MB`);

  //  برگشت از پس‌زمینه: یخ‌زده ⇐ فعال، پنهان ⇐ پیدا، و شبکه‌ای که در پس‌زمینه رفت و برگشت
  const before = await cartOf(page);
  await cdp.send('Page.setWebLifecycleState', { state: 'frozen' });
  await ctx.setOffline(true);
  await page.waitForTimeout(1500);
  await ctx.setOffline(false);
  await cdp.send('Page.setWebLifecycleState', { state: 'active' });
  await page.evaluate(() => {
    for (const v of ['hidden', 'visible']) {
      Object.defineProperty(document, 'visibilityState', { value: v, configurable: true });
      Object.defineProperty(document, 'hidden', { value: v === 'hidden', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    }
  });
  await settle(page, 800);
  ok(await cartOf(page) === before && await barCount(page) === 2, 'S92: برگشت از پس‌زمینه (یخ‌زده، پنهان، قطعِ شبکه) ⇒ سبد همان');
  await add(page, 'paste');
  ok(await barCount(page) === 3, 'S92: پس از برگشت، افزودنِ بعدی کار می‌کند');
  await page.goto('about:blank');
  await page.goBack({ waitUntil: 'load', timeout: 120000 });
  await cards(page);
  await settle(page, 500);
  ok(await barCount(page) === 3, 'S92: رفتن به صفحهٔ دیگر و «برگشت» ⇒ همان سبد');
  await ctx.close();
}

// ── ۲. خطای عکس: ۵۰۴، اتصالِ بریده، بایتِ خراب ⇒ حرفِ نخست، نه آیکنِ شکسته ─────────────────────────────────
{
  const { ctx, page } = await phone();
  const fail = { rice: 504, oil: 'abort', tea: 'garbage' };
  await page.route(PHOTO, (r) => {
    const id = decodeURIComponent(PHOTO.exec(r.request().url())[1]);
    const how = fail[id];
    if (how === 504) return r.fulfill({ status: 504, body: '' });
    if (how === 'abort') return r.abort('connectionreset');
    if (how === 'garbage') return r.fulfill({ status: 200, contentType: 'image/jpeg', body: Buffer.from('نه عکس') });
    return r.continue();
  });
  await page.goto(HOME, { waitUntil: 'load' });
  await cards(page);
  await page.waitForLoadState('networkidle');
  await settle(page);
  const view = await page.evaluate((ids) => ids.map((id) => {
    const box = document.querySelector(`#products li[data-id="${id}"] .p-img`);
    const ph = box && box.querySelector('.ph');
    const r = box && box.getBoundingClientRect();
    return { id, img: Boolean(box && box.querySelector('img')), ph: ph ? ph.textContent : '', tile: box ? box.classList.contains('tile') : null, w: r ? Math.round(r.width) : 0, h: r ? Math.round(r.height) : 0 };
  }), ['rice', 'oil', 'tea', 'sugar']);
  const fine = view.find((v) => v.id === 'sugar');
  for (const id of Object.keys(fail)) {
    const v = view.find((x) => x.id === id);
    ok(!v.img && v.ph === NAME[id].slice(0, 1) && v.tile === false && v.w === fine.w && v.h === fine.h,
      `S92/S126: عکسِ «${id}» (${fail[id]}) ⇒ حرفِ نخستِ نام به‌جای عکسِ شکسته، همان اندازهٔ کارت`, JSON.stringify(v));
  }
  ok(fine.img && !fine.ph, 'کارتِ سالم همان عکس را دارد', JSON.stringify(fine));
  ok((await broken(page)).length === 0, 'هیچ عکسِ شکسته‌ای دیده نمی‌شود', (await broken(page)).join(' '));
  await add(page, 'rice');
  await settle(page);
  const thumb = await page.evaluate(() => { const t = document.querySelector('#bar-thumbs .thumb'); return t ? { img: Boolean(t.querySelector('img')), text: t.textContent } : null; });
  ok(thumb && !thumb.img && thumb.text === NAME.rice.slice(0, 1), 'عکسِ نیامدهٔ نوارِ سبد هم همان حرف', JSON.stringify(thumb));
  await page.tap('#products li[data-id="rice"] .p-open');
  await page.waitForSelector('#product-sheet #ps-name', { timeout: 30000 });
  await page.waitForFunction(() => !document.querySelector('#ps-img img') || document.querySelector('#ps-img img').complete, null, { timeout: 30000 });
  await settle(page);
  ok(await page.evaluate((l) => !document.querySelector('#ps-img img') && document.querySelector('#ps-img .ph')?.textContent === l, NAME.rice.slice(0, 1)),
    'برگهٔ کالا با عکسِ ۵۰۴ ⇒ همان حرف');
  await ctx.close();
}

// ── ۳. همهٔ عکس‌ها بریده ⇒ خرید تا صفحهٔ سفارش ──────────────────────────────────────────────────────────
{
  const { ctx, page } = await phone();
  await page.route(PHOTO, (r) => r.abort('connectionreset'));
  await page.goto(HOME, { waitUntil: 'load' });
  await cards(page);
  await page.waitForLoadState('networkidle');
  await add(page, 'oil');
  await add(page, 'soap');
  await page.click('#cart-bar');
  await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'));
  await page.click('#cart-next');
  await page.waitForSelector('#view-checkout:not([hidden])', { timeout: 30000 });
  await page.check('input[name="fulfillment"][value="pickup"]');
  const total = await page.textContent('#t-total');
  ok(total.includes('۳۴۰'), 'بی عکس: جمعِ سبد پیش از ثبت دیده می‌شود (۳۰۰ + ۴۰)', total);
  await page.fill('#c-name', 'مشتریِ بی‌عکس');
  await page.fill('#c-phone', '0791112244');
  await page.fill('#c-email', mailOf('0791112244'));
  await page.click('#verify-send');
  await page.waitForSelector('#verify-code-row:not([hidden])', { timeout: 15000 });
  const code = ((await page.textContent('#verify-msg')).match(/(\d{4,8})/) || [])[1];
  await page.fill('#v-code', code || '');
  await page.click('#verify-check');
  await page.waitForFunction(() => !document.getElementById('submit-order').disabled, null, { timeout: 15000 });
  await page.click('#submit-order');
  await page.waitForSelector('#view-order:not([hidden])', { timeout: 30000 });
  await page.waitForFunction(() => /شمارهٔ/.test(document.getElementById('o-title').textContent), null, { timeout: 30000 });
  ok(true, 'S126: همهٔ عکس‌ها بریده ⇒ سفارش ثبت شد و صفحهٔ پیگیری باز شد');
  ok((await broken(page)).length === 0, 'بی عکس: هیچ عکسِ شکسته‌ای دیده نشد', (await broken(page)).join(' '));
  await ctx.close();
}

// ── ۴. شکستِ بارگذاری سبدِ مهمان را گم نمی‌کند ───────────────────────────────────────────────────────────
{
  const { ctx, page } = await phone();
  await page.goto(HOME, { waitUntil: 'load' });
  await cards(page);
  await add(page, 'rice');
  await add(page, 'shampoo');
  await add(page, 'shampoo');
  const saved = await cartOf(page);
  ok(Boolean(saved) && Object.keys(JSON.parse(saved)).length === 2, 'سبدِ مهمان: دو قلم در localStorage');

  //  سرور در دسترس نیست (هر درِ داده) ⇒ پیامِ خطا، سبد دست نخورده
  await page.route(/\/api\/store\//, (r) => r.abort('connectionrefused'));
  await page.reload({ waitUntil: 'load' });
  await page.waitForFunction(() => (document.getElementById('boot')?.textContent || '').trim().length > 0, null, { timeout: 30000 });
  await settle(page, 500);
  ok(await cartOf(page) === saved, 'S92: بارِ ناکام (سرورِ دور) سبد را عوض نمی‌کند', await page.textContent('#boot'));

  //  نیمه‌شکست: ویترین و کاتالوگ می‌آیند ولی قیمتِ تازهٔ سبد (`?ids=`) نه ⇒ سبد همان، هیچ «ناموجود»
  await page.unroute(/\/api\/store\//);
  await page.route(/\/api\/store\/[^/]+\/products\?ids=/, (r) => r.abort('timedout'));
  await page.reload({ waitUntil: 'load' });
  await cards(page);
  await settle(page, 800);
  ok(await cartOf(page) === saved, 'S92: قیمتِ سبد نیامد ⇒ سبد همان بایت‌ها');
  ok(await page.evaluate((k) => Object.values(JSON.parse(localStorage.getItem(k))).every((l) => !l.unavailable), CART_KEY), 'S92: هیچ قلمی بی‌دلیل «ناموجود» نشد');

  //  شبکه برگشت ⇒ همان سبد با قیمتِ سرور
  await page.unroute(/\/api\/store\/[^/]+\/products\?ids=/);
  await page.reload({ waitUntil: 'load' });
  await cards(page);
  await page.waitForLoadState('networkidle');
  await settle(page, 500);
  const back = JSON.parse(await cartOf(page));
  ok(Object.keys(back).sort().join() === 'rice,shampoo' && back.shampoo.qtyMilli === 2000 && await barCount(page) === 2, 'S92: پس از برگشتِ شبکه همان سبد', JSON.stringify(Object.keys(back)));
  await ctx.close();
}

// ── ۵. کاهشِ حرکت (S110): مسیرِ خرید بی حرکت، و همان مسیر بی تنظیم حرکت دارد ─────────────────────────────
const MOTION = /transform|translate|scale|rotate|^(top|left|right|bottom|inset|width|height|margin)/;
async function motionOf(page) {
  return page.evaluate((src) => {
    const re = new RegExp(src);
    const name = (el) => (el && el.tagName ? el.tagName.toLowerCase() + (el.id ? `#${el.id}` : '') + (typeof el.className === 'string' && el.className.trim() ? `.${el.className.trim().split(/\s+/).slice(0, 2).join('.')}` : '') : '?');
    return document.getAnimations().filter((a) => a.playState === 'running' || a.playState === 'pending').flatMap((a) => {
      const eff = a.effect;
      const dur = Number(eff && eff.getComputedTiming ? eff.getComputedTiming().duration : 0) || 0;
      if (dur <= 10) return [];
      const props = a.transitionProperty ? [a.transitionProperty]
        : [...new Set((eff && eff.getKeyframes ? eff.getKeyframes() : []).flatMap((k) => Object.keys(k)))].filter((k) => !['offset', 'easing', 'composite', 'computedOffset'].includes(k));
      return props.filter((p) => re.test(p)).map((p) => `${name(eff && eff.target)}:${a.animationName || 'transition'}:${p}`);
    });
  }, MOTION.source);
}
async function journeyMotion({ slow, reduce }) {
  const { ctx, page } = await phone({ slow, reduce });
  const seen = new Set();
  const watch = async (fn) => {
    await fn();
    for (let i = 0; i < 4; i++) { for (const m of await motionOf(page)) seen.add(m); await page.waitForTimeout(40); }
  };
  try {
    await page.goto(HOME, { waitUntil: 'load', timeout: 120000 });
    await cards(page);
    await page.waitForLoadState('networkidle', { timeout: 120000 });
    await watch(() => add(page, 'rice'));
    await watch(() => add(page, 'rice'));
    await watch(() => page.tap('#products li[data-id="soap"] .p-open'));
    await page.waitForSelector('#ps-img img', { timeout: 60000 });
    await watch(() => page.tap('#ps-add'));
    await watch(() => page.tap('#ps-close'));
    await settle(page, 400);
    await watch(() => page.tap('#cart-bar'));
    await page.waitForFunction(() => document.body.classList.contains('cart-open') || document.getElementById('cart-sheet').classList.contains('on'), null, { timeout: 30000 });
    await settle(page, 400);
    const lines = await page.locator('#sheet-lines li').count();
    await watch(() => page.tap('#sheet-lines li:first-child .stepper button:last-child'));
    await watch(() => page.tap('#cart-next'));
    await page.waitForSelector('#view-checkout:not([hidden])', { timeout: 90000 });
    await page.waitForSelector('#c-name', { state: 'visible', timeout: 90000 });
    return { seen: [...seen], lines };
  } finally { await ctx.close(); }
}
{
  const plain = await journeyMotion({ slow: false, reduce: false });
  ok(plain.seen.length > 0, 'سنجهٔ حرکت کور نیست: همان مسیر بی «کاهشِ حرکت» حرکت دارد', plain.seen.slice(0, 6).join(' | '));
  const calm = await journeyMotion({ slow: true, reduce: true });
  ok(calm.lines === 2, 'S110: با کاهشِ حرکت و 2G و پردازندهٔ ۶× مسیرِ خرید تا فرمِ ثبت کار کرد (دو قلم در سبد)', String(calm.lines));
  ok(calm.seen.length === 0, 'S110: با `prefers-reduced-motion: reduce` هیچ انیمیشن/گذارِ حرکتی در مسیرِ خرید', calm.seen.join(' | '));
}

ok(errors.length === 0, 'بی خطای جاوااسکریپت', errors.join(' | '));
await browser.close();
await store.close();
console.log(bad ? `❌ ${bad} ایراد` : '✅ ویترین: گوشیِ ضعیف، شبکهٔ ضعیف، خطای عکس و کاهشِ حرکت');
process.exit(bad ? 1 : 0);
