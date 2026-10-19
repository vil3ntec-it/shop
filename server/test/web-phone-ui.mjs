/**
 * گوشیِ وب (phone-ui) با کرومیومِ واقعی — نوارِ پایینِ قرصی، و (در PRهای بعدی)
 * «حساب من»، «تنظیمات» و نوار/برگهٔ سبدِ صندوق.
 *
 *   • پهنای ۳۶۰، ۳۹۰ و ۷۶۸: بی بیرون‌زدگیِ افقی، هر تب ≥ ۴۸ پیکسل؛
 *   • تبِ فعال قرصِ تیره (`--nav-pill`) با برچسبِ دیدنی و `aria-current`؛ بقیه
 *     فقط آیکن ولی با نامِ دسترس‌پذیر؛
 *   • axe-core: کنتراست و نامِ پیوند؛ «کاهشِ حرکت» انیمیشن را خاموش می‌کند.
 *
 * اجرا:  node test/web-phone-ui.mjs
 * نبودِ پلی‌رایت یا axe-core قرمز است، نه «رد» (F0-17)؛ ردِ آگاهانه فقط با SKIP_BROWSER_TESTS=1.
 */
import { createRequire } from 'node:module';
import { missingTool, PLAYWRIGHT_INSTALL, SKIP } from './browser-guard.mjs';
import assert from 'node:assert/strict';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const globalRoot = (() => { try { return execSync('npm root -g', { encoding: 'utf8' }).trim(); } catch { return ''; } })();
const pick = (name) => { for (const p of [`${globalRoot}/${name}`, name]) { try { return require(p); } catch { /* بعدی */ } } return null; };
const pw = pick('playwright');
if (!pw) missingTool('پلی‌رایت', PLAYWRIGHT_INSTALL);
let axeSrc = null;
for (const p of [`${globalRoot}/axe-core/axe.min.js`, path.join(import.meta.dirname, '..', 'node_modules', 'axe-core', 'axe.min.js'), process.env.AXE_PATH || '']) {
  if (p && fs.existsSync(p)) { axeSrc = fs.readFileSync(p, 'utf8'); break; }
}
if (!axeSrc && !SKIP) missingTool('axe-core', 'npm i -g axe-core@4.10.3');

const ROOT = path.join(import.meta.dirname, '..', '..');
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  const file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, '') || 'index.html');
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.statusCode = 404; return res.end(); }
  res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}/index.html`;

const LEDGER = {
  products: [
    { id: 'p1', name: 'برنج سیلا', unit: 'کیلو', salePrice: 120, purchasePrice: 95, category: 'خوراکی' },
    { id: 'p2', name: 'روغن آفتاب‌گردان', unit: 'بوتل', salePrice: 310, purchasePrice: 260, category: 'خوراکی' },
    { id: 'p3', name: 'صابون گل', unit: 'دانه', salePrice: 40, purchasePrice: 30, category: 'بهداشتی' },
    { id: 'p4', name: 'چای سبز', unit: 'بسته', salePrice: 150, purchasePrice: 120, category: 'خوراکی' },
  ],
  warehouseEntries: ['p1', 'p2', 'p3', 'p4'].map((p, i) => ({ id: 'w' + i, productId: p, units: 50, date: '2026-10-01' })),
  debtors: [{ id: 'c1', name: 'احمد کریمی' }],
  transactions: [],
  sales: [],
  saleItems: [],
};

const browser = await pw.chromium.launch();
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log(`✅  ${name}`); } catch (e) { failed++; console.log(`❌  ${name}\n    ${e.message}`); }
};

async function open(width, opts = {}) {
  const ctx = await browser.newContext({ viewport: { width, height: 800 }, reducedMotion: opts.reducedMotion || 'no-preference' });
  const pg = await ctx.newPage();
  const errors = [];
  pg.on('pageerror', e => errors.push(String(e)));
  await pg.addInitScript((d) => {
    if (localStorage.getItem('seeded-phone-ui')) return;
    localStorage.setItem('tohid-shop-data-v1', JSON.stringify(d));
    localStorage.setItem('tohid-shop-auth-v1', JSON.stringify({ method: 'local', value: '', name: 'آزمون', loginAt: 1 }));
    localStorage.setItem('seeded-phone-ui', '1');
  }, LEDGER);
  await pg.goto(url);
  await pg.waitForTimeout(1000);
  const skip = pg.getByText('ادامه بدون حساب');
  if (await skip.first().isVisible().catch(() => false)) { await skip.first().click(); await pg.waitForTimeout(300); }
  return { pg, ctx, errors };
}

const overflow = (pg) => pg.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
async function axe(pg, selector, rules) {
  if (!axeSrc) return [];
  await pg.addScriptTag({ content: axeSrc });
  //  دکمه‌ای که قفلِ اشتراک کم‌رنگش کرده (`vip-locked-el`/`lic-locked-el`، رفتارِ پیشین) سنجیده نمی‌شود
  return pg.evaluate(async ([sel, r]) => (await window.axe.run({ include: [[sel]], exclude: [['.vip-locked-el'], ['.lic-locked-el']] }, { runOnly: r })).violations
    .map(v => `${v.id}: ${v.nodes.slice(0, 3).map(n => n.target.join(' ')).join(' ; ')}`), [selector, rules]);
}

/* ============================ نوارِ پایینِ قرصی ============================ */
for (const width of [360, 390, 768]) {
  await step(`نوارِ پایین در ${width}: قرصِ تیرهٔ تبِ فعال، برچسب و ۴۸ پیکسل`, async () => {
    const { pg, ctx, errors } = await open(width);
    for (const page of ['dashboard', 'sale', 'debtors', 'products', 'more']) {
      await pg.evaluate((p) => goTo(p), page);
      //  پایانِ جابه‌جاییِ قرص (transition) — نه زمانِ ثابت، که روی ماشینِ کند لق می‌شود
      await pg.waitForFunction(() => {
        const a = document.querySelector('#bottom-nav .bn-item.active');
        if (!a) return false;
        const probe = document.createElement('i');
        probe.style.background = getComputedStyle(document.documentElement).getPropertyValue('--nav-pill');
        document.body.appendChild(probe);
        const want = getComputedStyle(probe).backgroundColor;
        probe.remove();
        return getComputedStyle(a).backgroundColor === want;
      }, null, { timeout: 5000 }).catch(() => {});
      const nav = await pg.evaluate(() => {
        const css = (el, k) => getComputedStyle(el).getPropertyValue(k).trim();
        const root = document.documentElement;
        const items = [...document.querySelectorAll('#bottom-nav .bn-item')].map(a => {
          const r = a.getBoundingClientRect();
          const label = a.querySelector('.bn-label');
          return {
            page: a.dataset.page, active: a.classList.contains('active'), current: a.getAttribute('aria-current'),
            w: r.width, h: r.height, bg: getComputedStyle(a).backgroundColor,
            labelW: label ? label.getBoundingClientRect().width : 0, name: (label ? label.textContent : '').trim(),
          };
        });
        const probe = document.createElement('i');
        probe.style.background = css(root, '--nav-pill');
        document.body.appendChild(probe);
        const pill = getComputedStyle(probe).backgroundColor;
        probe.remove();
        return { items, pill };
      });
      assert.equal(nav.items.length, 5, 'پنج تب');
      const active = nav.items.filter(i => i.active);
      assert.equal(active.length, 1, `${page}: یک تبِ فعال`);
      assert.equal(active[0].page, page);
      assert.equal(active[0].current, 'page', 'aria-current روی تبِ فعال');
      assert.equal(active[0].bg, nav.pill, 'قرصِ فعال همان --nav-pill');
      assert.ok(active[0].labelW > 20, 'برچسبِ تبِ فعال دیده می‌شود');
      for (const i of nav.items) {
        assert.ok(i.w >= 48 && i.h >= 48, `${i.page}: ${i.w}×${i.h} زیرِ ۴۸`);
        assert.ok(i.name.length > 0, `${i.page}: نامِ دسترس‌پذیر`);
        if (!i.active) { assert.ok(i.labelW <= 1, `${i.page}: برچسبِ غیرفعال پنهانِ دیدنی`); assert.equal(i.current, null); }
      }
      assert.ok(await overflow(pg) <= 0, `${page}: بیرون‌زدگیِ افقی`);
    }
    const v = await axe(pg, '#bottom-nav', ['color-contrast', 'link-name']);
    assert.deepEqual(v, [], 'axe روی نوار');
    assert.deepEqual(errors, []);
    await ctx.close();
  });
}

await step('نوارِ پایین در ۱۲۸۰ پنهان است و منوی کناری می‌آید', async () => {
  const { pg, ctx } = await open(1280);
  assert.equal(await pg.evaluate(() => getComputedStyle(document.getElementById('bottom-nav')).display), 'none');
  await ctx.close();
});

await step('شب: قرصِ روشن روی نوارِ تیره، axe سبز', async () => {
  const { pg, ctx } = await open(390);
  //  «قرض‌داران» بی اشتراک کم‌رنگ است (`vip-locked-el`، رفتارِ پیشین) — تبِ آزاد سنجیده می‌شود
  await pg.evaluate(() => { document.documentElement.setAttribute('data-theme', 'dark'); goTo('more'); });
  await pg.waitForTimeout(900);
  const v = await axe(pg, '#bottom-nav', ['color-contrast', 'link-name']);
  assert.deepEqual(v, []);
  await ctx.close();
});

await step('«کاهشِ حرکت» (تنظیمِ «مثل گوشی»): جابه‌جاییِ قرص بی انیمیشن', async () => {
  const { pg, ctx } = await open(390, { reducedMotion: 'reduce' });
  await pg.evaluate(() => document.documentElement.removeAttribute('data-motion'));
  const dur = await pg.evaluate(() => getComputedStyle(document.querySelector('#bottom-nav .bn-item')).transitionDuration
    .split(',').map(s => parseFloat(s)).reduce((a, b) => Math.max(a, b), 0));
  assert.ok(dur < 0.01, `transition ${dur}s`);
  await ctx.close();
});

/* ============================ تنظیمات: گروه‌ها ============================ */
const SETTINGS_PANELS = [
  'حساب و همگام‌سازی', 'کالای منقضی', 'ظاهر برنامه', 'تاریخ و رقم', 'اطلاعات فروشگاه', 'دوربین بارکدخوان',
  'نصب برنامه روی دستگاه', 'حساب و اشتراک', 'پشتیبان‌گیری از اطلاعات', 'حساب کاربری', 'پاک‌سازی اطلاعات', 'درباره برنامه',
];
for (const width of [360, 390, 768]) {
  await step(`تنظیمات در ${width}: شش گروه، همهٔ ۱۲ پنل، کارتِ گرد، بی بیرون‌زدگی، axe`, async () => {
    const { pg, ctx, errors } = await open(width);
    await pg.evaluate(() => goTo('settings'));
    await pg.waitForTimeout(700);
    const info = await pg.evaluate(() => {
      const page = document.getElementById('page-settings');
      const groups = [...page.querySelectorAll(':scope > .set-group')].map(g => {
        const card = g.querySelector('.set-group-card');
        const cs = getComputedStyle(card);
        return {
          title: g.querySelector('.set-group-title').textContent.trim(),
          labelled: document.getElementById(g.getAttribute('aria-labelledby'))?.textContent.trim(),
          radius: parseFloat(cs.borderTopLeftRadius), shadow: cs.boxShadow,
          panels: [...card.querySelectorAll(':scope > .panel')].map(p => ({
            title: p.querySelector('.panel-head h2')?.textContent.trim(),
            border: getComputedStyle(p).borderTopWidth,
          })),
        };
      });
      return { groups, loose: page.querySelectorAll(':scope > .panel').length, all: page.querySelectorAll('.panel').length };
    });
    assert.deepEqual(info.groups.map(g => g.title), ['عمومی', 'فروش و پرداخت', 'امنیت', 'دستگاه‌ها', 'همگام‌سازی و پشتیبان', 'درباره']);
    info.groups.forEach(g => assert.equal(g.labelled, g.title, 'aria-labelledby'));
    assert.equal(info.loose, 0, 'پنلِ بی‌گروه');
    assert.equal(info.all, SETTINGS_PANELS.length, 'تعدادِ پنل‌ها عوض شده');
    assert.deepEqual(info.groups.flatMap(g => g.panels.map(p => p.title)).sort(), [...SETTINGS_PANELS].sort(), 'هر تنظیم یک بار، با همان نام');
    for (const g of info.groups) {
      assert.ok(g.radius >= 16, `${g.title}: گوشهٔ ${g.radius}`);
      assert.notEqual(g.shadow, 'none', `${g.title}: سایهٔ نرم`);
      g.panels.forEach(p => assert.equal(p.border, '0px', `${p.title}: خطِ دورِ تیره در کارتِ گروه`));
    }
    assert.ok(await overflow(pg) <= 0, 'بیرون‌زدگیِ افقی');
    const v = await axe(pg, '#page-settings', ['color-contrast', 'button-name', 'aria-allowed-attr', 'aria-valid-attr-value']);
    assert.deepEqual(v, []);
    assert.deepEqual(errors, []);
    await ctx.close();
  });
}

await step('تنظیمات: انتخاب‌ها حالتِ خود را نشان می‌دهند (aria-pressed) و کار می‌کنند', async () => {
  const { pg, ctx } = await open(390);
  await pg.evaluate(() => goTo('settings'));
  await pg.waitForTimeout(500);
  const pressed = (sel) => pg.$$eval(sel, bs => bs.map(b => [b.textContent.trim(), b.getAttribute('aria-pressed')]));
  await pg.click('#theme-toggle [data-theme-choice="dark"]');
  await pg.waitForTimeout(200);
  assert.equal(await pg.evaluate(() => document.documentElement.getAttribute('data-theme')), 'dark');
  assert.deepEqual((await pressed('#theme-toggle .theme-toggle-btn')).map(x => x[1]).filter(x => x === 'true').length, 1);
  assert.equal(await pg.getAttribute('#theme-toggle [data-theme-choice="dark"]', 'aria-pressed'), 'true');
  await pg.click('#theme-toggle [data-theme-choice="light"]');
  await pg.click('#expiry-policy-toggle [data-expiry-policy="block"]');
  await pg.waitForTimeout(200);
  assert.equal(await pg.getAttribute('#expiry-policy-toggle [data-expiry-policy="block"]', 'aria-pressed'), 'true');
  assert.equal(await pg.getAttribute('#expiry-policy-toggle [data-expiry-policy="warn"]', 'aria-pressed'), 'false');
  await pg.click('#expiry-policy-toggle [data-expiry-policy="warn"]');
  await pg.waitForTimeout(200);
  assert.equal(await pg.getAttribute('#expiry-policy-toggle [data-expiry-policy="warn"]', 'aria-pressed'), 'true');
  await ctx.close();
});

/* ============================ حساب من ============================ */
const ME = (days, trial = false) => ({
  user: { name: 'نوید احمدی', email: 'owner@example.com', createdAt: '2025-03-01T00:00:00Z' },
  shop: { name: 'دکان نمونه', isOwner: true },
  entitlement: trial
    ? { trial: { active: true, endsAt: new Date(Date.now() + days * 86400000).toISOString() } }
    : { subscription: { active: days > 0, plan: 'سالانه', endsAt: new Date(Date.now() + days * 86400000).toISOString() } },
});
async function paintProfile(pg, me) {
  await pg.evaluate((m) => {
    goTo('profile');
    window.TohidLicense = Object.assign(window.TohidLicense || {}, { isLoggedIn: () => !!m, userLabel: () => 'owner@example.com' });
    profPaint(m);
  }, me);
  await pg.waitForTimeout(300);
  return pg.evaluate(() => ({
    role: document.getElementById('prof-role').hidden ? null : document.getElementById('prof-role').textContent.trim(),
    stats: [...document.querySelectorAll('.prof-stat')].map(s => [s.querySelector('span').textContent, s.querySelector('b').textContent]),
    notice: document.getElementById('prof-notice').hidden ? null : document.getElementById('prof-notice-title').textContent.trim(),
    cta: document.getElementById('prof-notice-btn').textContent.trim(),
  }));
}

for (const width of [360, 390, 768]) {
  await step(`حساب من در ${width}: کارت، نقش، آمارِ واقعی، بی بیرون‌زدگی، axe`, async () => {
    const { pg, ctx, errors } = await open(width);
    const p = await paintProfile(pg, ME(40));
    assert.equal(p.role, 'صاحب دکان');
    assert.deepEqual(p.stats, [['کالا', '4'], ['فاکتور', '0'], ['قرض‌دار', '1']], 'آمار از دفترِ همین دستگاه');
    assert.equal(p.notice, null, 'چهل روز مانده ⇒ نوارِ هشدار نیست');
    assert.ok(await overflow(pg) <= 0, 'بیرون‌زدگیِ افقی');
    const v = await axe(pg, '#page-profile', ['color-contrast', 'button-name', 'label', 'aria-allowed-attr']);
    assert.deepEqual(v, []);
    assert.deepEqual(errors, []);
    await ctx.close();
  });
}

await step('حساب من: نوارِ هشدار فقط وقتی واقعی است — ۵ روز، آزمایشی، تمام‌شده، بی‌حساب', async () => {
  const { pg, ctx } = await open(390);
  let p = await paintProfile(pg, ME(5));
  assert.match(p.notice, /اشتراک — 5 روز مانده/); assert.equal(p.cta, 'تمدید');
  const v = await axe(pg, '#prof-notice', ['color-contrast', 'button-name']);
  assert.deepEqual(v, [], 'کنتراستِ نوارِ تیره');
  p = await paintProfile(pg, ME(3, true));
  assert.match(p.notice, /آزمایشی — 3 روز مانده/); assert.equal(p.cta, 'پلن‌ها');
  p = await paintProfile(pg, ME(-2));
  assert.equal(p.notice, 'اشتراک تمام شده');
  p = await paintProfile(pg, null);
  assert.equal(p.notice, null, 'بی‌حساب هشدارِ اشتراک ندارد');
  assert.equal(p.role, null, 'بی‌حساب نقش ندارد');
  //  دکمهٔ نوار همان پنجرهٔ اشتراک را باز می‌کند
  await paintProfile(pg, ME(5));
  await pg.evaluate(() => { window.__vipOpened = 0; window.TohidLicense.open = () => { window.__vipOpened++; }; });
  await pg.click('#prof-notice-btn');
  assert.equal(await pg.evaluate(() => window.__vipOpened), 1);
  await ctx.close();
});

/* ============================ سبدِ صندوق: نوار و برگهٔ تیره ============================ */
async function posCart(pg, page = 'sale', ids = ['p1', 'p2', 'p3', 'p4']) {
  await pg.evaluate(([pageId, list]) => {
    //  قفلِ اشتراک (اگر هست) جلوی دیدنِ صندوق را نگیرد — آزمون چیدمان است، نه اشتراک
    [document.documentElement, document.body].forEach(r => [...r.classList].filter(c => c.startsWith('vip-lock-')).forEach(c => r.classList.remove(c)));
    goTo(pageId);
    list.forEach((id, i) => addToCart(id, i === 0 ? 2 : 1));
  }, [page, ids]);
  await pg.waitForTimeout(400);
}
//  پایانِ انیمیشنِ بالا آمدنِ برگه — نه زمانِ ثابت
const settle = (pg) => pg.waitForFunction(() => document.getAnimations().every(a => a.playState !== 'running'), null, { timeout: 3000 }).catch(() => {});
const drawerState = (pg, prefix = '') => pg.evaluate((pre) => {
  const d = document.getElementById(pre + 'cart-drawer');
  const vis = (el) => !!el && getComputedStyle(el).display !== 'none' && el.getBoundingClientRect().width > 0;
  const opener = d.querySelector('[data-cart-open]');
  return {
    shown: vis(d), open: d.classList.contains('open'),
    bg: getComputedStyle(d).backgroundColor,
    thumbs: [...d.querySelectorAll('[data-cart-thumbs] .cbar-thumb')].map(t => t.textContent.trim()),
    more: d.querySelector('[data-cart-thumbs] .cbar-more')?.textContent.trim() || null,
    count: d.querySelector('[data-cart-count]').textContent.trim(),
    countShown: vis(d.querySelector('[data-cart-count]')),
    total: d.querySelector('.cart-total strong').textContent.trim(),
    openerShown: vis(opener), openerName: opener.getAttribute('aria-label'), expanded: opener.getAttribute('aria-expanded'),
    finalizeShown: vis(d.querySelector('.cart-summary-bar .btn')),
    stepShown: [...d.querySelectorAll('.cart-row-step')].filter(vis).length,
    rows: d.querySelectorAll('[data-cart-row]').length,
    sheetOpenBody: document.body.classList.contains('cart-sheet-open'),
    focus: document.activeElement && (document.activeElement.hasAttribute('data-cart-close') ? 'close' : document.activeElement.hasAttribute('data-cart-open') ? 'open' : document.activeElement.tagName),
    pill: (() => { const i = document.createElement('i'); i.style.background = getComputedStyle(document.documentElement).getPropertyValue('--sheet'); document.body.appendChild(i); const c = getComputedStyle(i).backgroundColor; i.remove(); return c; })(),
  };
}, prefix);

for (const width of [360, 390, 768]) {
  await step(`صندوق در ${width}: نوارِ تیرهٔ جمع‌شده — عکس/حرفِ اول، «+N»، شمار، مبلغ؛ بی بیرون‌زدگی، axe`, async () => {
    const { pg, ctx, errors } = await open(width);
    await pg.evaluate(() => goTo('sale'));
    assert.equal((await drawerState(pg)).shown, false, 'سبدِ خالی ⇒ نوار نیست');
    await posCart(pg);
    const s = await drawerState(pg);
    assert.ok(s.shown && !s.open, 'نوار پیدا و بسته');
    assert.equal(s.bg, s.pill, 'زمینهٔ نوار همان --sheet');
    assert.deepEqual(s.thumbs, ['چ', 'ص', 'ر'], 'سه قلمِ تازه‌تر، حرفِ اول');
    assert.equal(s.more, '+1');
    assert.equal(s.count, '4'); assert.ok(s.countShown);
    assert.match(s.total, /740/);
    assert.ok(s.openerShown, 'کلِ نوار یک دکمه است');
    assert.match(s.openerName, /4 قلم، 740 افغانی/);
    assert.equal(s.expanded, 'false');
    assert.equal(s.finalizeShown, false, 'دکمهٔ ثبت در برگه است، نه در نوارِ جمع‌شده');
    assert.ok(await overflow(pg) <= 0, 'بیرون‌زدگیِ افقی');
    const v = await axe(pg, '#cart-drawer', ['color-contrast', 'button-name', 'aria-allowed-attr', 'aria-valid-attr-value']);
    assert.deepEqual(v, []);
    assert.deepEqual(errors, []);
    await ctx.close();
  });
}

await step('صندوق ۳۹۰: برگهٔ تیره — باز، − / مقدار / +، Esc، زدنِ بالا، همان پنجرهٔ پرداخت', async () => {
  const { pg, ctx, errors } = await open(390);
  await posCart(pg);
  await pg.click('#cart-drawer [data-cart-open]');
  await settle(pg);
  let s = await drawerState(pg);
  assert.ok(s.open && s.sheetOpenBody, 'برگه باز است');
  assert.equal(s.expanded, 'true');
  assert.equal(s.focus, 'close', 'تمرکز روی «بستن»');
  assert.equal(s.stepShown, 4, 'هر ردیف − مقدار + دارد');
  assert.ok(s.finalizeShown, 'ثبت فروش در برگه');
  const geo = await pg.evaluate(() => {
    const d = document.getElementById('cart-drawer').getBoundingClientRect();
    const b = document.getElementById('btn-finalize-sale').getBoundingClientRect();
    return { top: d.top, bottom: d.bottom, vh: innerHeight, btnW: b.width, w: d.width, btnH: b.height };
  });
  assert.ok(geo.bottom >= geo.vh - 1 && geo.top < geo.vh * 0.2, `برگهٔ تمام‌قد از پایین ${JSON.stringify(geo)}`);
  assert.ok(geo.btnW >= geo.w - 40 && geo.btnH >= 48, 'دکمهٔ اصلیِ تمام‌عرض');
  let v = await axe(pg, '#cart-drawer', ['color-contrast', 'button-name', 'aria-allowed-attr']);
  assert.deepEqual(v, [], 'axe روی برگه');
  //  + و − روی ردیفِ خودش، بی انتخابِ ردیف
  const row = (id) => `#cart-drawer [data-cart-row="${id}"]`;
  await pg.click(`${row('p3')} [data-line-step="1"]`);
  assert.equal(await pg.evaluate(() => cart.find(c => c.productId === 'p3').quantity), 2);
  assert.match(await pg.textContent('#cart-total-amount'), /780/);
  assert.match(await pg.textContent(`${row('p3')} .cart-step-qty`), /2/);
  assert.equal(await pg.evaluate(() => selectedCartId), null, 'دکمه‌های ردیف، ردیف را انتخاب نمی‌کنند');
  await pg.click(`${row('p4')} [data-line-step="-1"]`);
  await pg.waitForTimeout(200);
  assert.deepEqual(await pg.evaluate(() => cart.map(c => c.productId)), ['p1', 'p2', 'p3'], '− تا صفر ردیف را برمی‌دارد');
  //  مقدارِ نوشتنی: همان کادرِ «مقدار»ِ پیشین
  pg.once('dialog', d => d.accept('۵'));
  await pg.click(`${row('p2')} [data-line-qty]`);
  await pg.waitForTimeout(200);
  assert.equal(await pg.evaluate(() => cart.find(c => c.productId === 'p2').quantity), 5);
  //  Esc ⇐ بسته، تمرکز به نوار
  await pg.keyboard.press('Escape');
  await pg.waitForTimeout(300);
  s = await drawerState(pg);
  assert.ok(!s.open && !s.sheetOpenBody, 'Esc برگه را بست');
  assert.equal(s.focus, 'open', 'تمرکز به نوار برگشت');
  //  زدنِ بالای برگه ⇐ بسته
  await pg.click('#cart-drawer [data-cart-open]');
  await pg.waitForTimeout(400);
  await pg.mouse.click(195, 40);
  await pg.waitForTimeout(300);
  assert.equal((await drawerState(pg)).open, false, 'زدنِ صفحهٔ بالای برگه');
  //  دکمهٔ اصلی همان پنجرهٔ پرداختِ پیشین را باز می‌کند
  await pg.click('#cart-drawer [data-cart-open]');
  await pg.waitForTimeout(400);
  await pg.click('#btn-finalize-sale');
  await pg.waitForTimeout(400);
  assert.ok(await pg.isVisible('#form-sale-checkout'), 'پنجرهٔ پرداخت باز شد');
  assert.deepEqual(errors, []);
  await ctx.close();
});

await step('شب: نوار و برگهٔ سبد axe سبز', async () => {
  const { pg, ctx } = await open(390);
  await pg.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
  await posCart(pg);
  let v = await axe(pg, '#cart-drawer', ['color-contrast', 'button-name']);
  assert.deepEqual(v, [], 'نوار');
  await pg.click('#cart-drawer [data-cart-open]');
  await pg.waitForTimeout(450);
  v = await axe(pg, '#cart-drawer', ['color-contrast', 'button-name']);
  assert.deepEqual(v, [], 'برگه');
  await ctx.close();
});

await step('«انتخاب محصول» در گوشی همان نوار و برگه را دارد', async () => {
  const { pg, ctx } = await open(390);
  await posCart(pg, 'quick-sale', ['p1', 'p2']);
  const s = await drawerState(pg, 'qs-');
  assert.ok(s.shown && !s.open && s.count === '2' && s.thumbs.length === 2);
  await pg.click('#qs-cart-drawer [data-cart-open]');
  await pg.waitForTimeout(400);
  assert.equal((await drawerState(pg, 'qs-')).open, true);
  await ctx.close();
});

await step('کامپیوتر (۱۲۸۰): کشوی سبدِ پیشین بی تغییر — بی نوارِ تیره و بی − + ِ ردیف', async () => {
  const { pg, ctx } = await open(1280);
  await posCart(pg);
  const s = await drawerState(pg);
  assert.ok(s.shown);
  assert.notEqual(s.bg, s.pill, 'زمینهٔ تیرهٔ گوشی در کامپیوتر نیست');
  assert.equal(s.openerShown, false); assert.equal(s.countShown, false);
  assert.ok(s.finalizeShown, '«ثبت فروش» همان‌جا در نوار');
  await pg.click('#cart-summary-bar .cart-total');
  await pg.waitForTimeout(300);
  const t = await drawerState(pg);
  assert.ok(t.open && !t.sheetOpenBody, 'زدنِ نوار همان باز شدنِ فهرست');
  assert.equal(t.stepShown, 0, 'ردیف‌ها همان قرصِ مقدار');
  await ctx.close();
});

await step('«کاهشِ حرکت»: برگهٔ سبد بی انیمیشن', async () => {
  const { pg, ctx } = await open(390, { reducedMotion: 'reduce' });
  await pg.evaluate(() => document.documentElement.removeAttribute('data-motion'));
  await posCart(pg);
  await pg.click('#cart-drawer [data-cart-open]');
  const dur = await pg.evaluate(() => parseFloat(getComputedStyle(document.getElementById('cart-drawer')).animationDuration));
  assert.ok(dur < 0.01, `animation ${dur}s`);
  await ctx.close();
});

await browser.close();
server.close();
console.log(failed ? `\n${failed} مورد قرمز` : '\nهمه سبز');
process.exit(failed ? 1 : 0);
