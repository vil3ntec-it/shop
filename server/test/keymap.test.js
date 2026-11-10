'use strict';
/**
 * ثبتِ مرکزیِ کلیدها (MASTER-SPEC ۳.۱۶) — سمتِ وب.
 *
 * منبع: android-native/app/src/main/java/ir/vil3ntec/tohid/core/keymap/keymap.json
 * `gen-keymap.mjs` از آن KeyMap.kt و license/keymap.js را می‌سازد. این‌جا:
 *   ۱. هر دو فایلِ ساخته‌شده تازه‌اند (json عوض شده و generator اجرا نشده ⇒ رد)؛
 *   ۲. license/keymap.js کلید را با e.code می‌خواند (چیدمانِ فارسی فرقی نمی‌کند)،
 *      Mod یعنی Ctrl یا Cmd، و پیرایه‌ها دقیق‌اند؛
 *   ۳. ترتیبِ Alt+۱ تا ۹ همان KEY_PAGESِ index.html است.
 * قاعده‌های تداخل و کلیدهای رزرو در KeyMapTest (Kotlin) است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '..', '..');
const GEN = path.join(ROOT, 'android-native/app/src/main/java/ir/vil3ntec/tohid/core/keymap/gen-keymap.mjs');
const km = require(path.join(ROOT, 'license', 'keymap.js'));

const ev = (code, mods = {}) => ({ code, key: '?', ctrlKey: false, metaKey: false, shiftKey: false, altKey: false, ...mods });

test('KeyMap.kt و license/keymap.js با keymap.json یکی‌اند', async () => {
  const gen = await import(GEN);
  for (const [file, text] of Object.entries(gen.generate())) {
    assert.equal(fs.readFileSync(file, 'utf8'), text, `${path.relative(ROOT, file)} کهنه است — node ${path.relative(ROOT, GEN)}`);
  }
});

test('generator نامِ ناشناخته و شناسهٔ تکراری را رد می‌کند', async () => {
  const gen = await import(GEN);
  const base = JSON.parse(fs.readFileSync(gen.PATHS.json, 'utf8'));
  const bad = (mutate) => { const j = structuredClone(base); mutate(j); return () => gen.load(JSON.stringify(j)); };
  assert.throws(bad((j) => { j.actions[0].keys = ['Mod+Foo']; }), /ناشناخته/);
  assert.throws(bad((j) => { j.actions[0].keys = ['Ctrl+P']; }), /پیرایه/);
  assert.throws(bad((j) => { j.actions.push({ ...j.actions[0] }); }), /تکراری/);
  assert.throws(bad((j) => { j.actions[0].scope = 'nope'; }), /دامنه/);
  assert.throws(bad((j) => { j.actions[0].overrides = ['global.nope']; }), /نیست/);
});

test('کلید با کدِ فیزیکی: Alt+۱ با چیدمانِ فارسی هم صفحهٔ اول است', () => {
  assert.equal(km.matches(ev('Digit1', { altKey: true, key: '۱' }), 'global.page1'), true);
  assert.equal(km.matches(ev('Digit1', { altKey: true }), 'global.page2'), false);
  assert.equal(km.matches(ev('Numpad1', { altKey: true }), 'global.page1'), false, 'Alt+NumPad در ویندوز کدِ Alt است (۵.۱ بند ۴)');
  assert.equal(km.matches(ev('KeyK', { ctrlKey: true, key: 'ن' }), 'global.palette'), true);
});

test('Mod یعنی Ctrl یا Cmd؛ پیرایه‌ها دقیق‌اند', () => {
  assert.equal(km.matches(ev('KeyK', { metaKey: true }), 'global.palette'), true);
  assert.equal(km.matches(ev('KeyK', { ctrlKey: true, shiftKey: true }), 'global.palette'), false);
  assert.equal(km.matches(ev('KeyK'), 'global.palette'), false);
  assert.equal(km.matches(ev('Enter', { ctrlKey: true }), 'global.save'), true);
  assert.equal(km.matches(ev('NumpadEnter', { ctrlKey: true }), 'global.save'), true);
  assert.equal(km.matches(ev('Slash'), 'global.find'), true);
  assert.equal(km.matches(ev('KeyF', { ctrlKey: true }), 'global.find'), false, 'Ctrl+F در وب جست‌وجوی مرورگر است');
  assert.equal(km.matches(ev('F11'), 'global.fullscreen'), false, 'تمام‌صفحه در وب با مرورگر است');
  assert.throws(() => km.matches(ev('KeyK'), 'global.nope'), /ناشناخته/);
});

test('برچسب: Ctrl روی ویندوز، Cmd روی مک', () => {
  assert.equal(km.label('global.palette'), 'Ctrl+K');
  assert.equal(km.label('global.palette', true), 'Cmd+K');
  assert.equal(km.label('global.page3'), 'Alt+3');
  assert.equal(km.label('sale.discountInvoice'), 'Alt+Shift+X');
  assert.equal(km.label('global.zoomIn'), '');
});

test('ترتیبِ Alt+۱ تا ۹ همان KEY_PAGESِ index.html است', () => {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const m = html.match(/const KEY_PAGES = (\[[^\]]*\]);/);
  assert.ok(m, 'KEY_PAGES در index.html پیدا نشد');
  const pages = JSON.parse(m[1].replace(/'/g, '"'));
  const fromMap = Object.entries(km.ACTIONS).filter(([, a]) => a.page).map(([, a]) => a.page);
  assert.deepEqual(fromMap, pages);
  pages.forEach((p, i) => assert.equal(km.ACTIONS[`global.page${i + 1}`].page, p));
});

test('#960: راهنمای وب فقط کلیدهایی را دارد که وب پیاده کرده — Alt+I «قیمت‌پرسی» نه', async () => {
  //  status ِ کامپیوتر partial است (Ctrl+I در F1 ِ کامپیوتر)، ولی وب قیمت‌پرسی ندارد
  assert.equal(km.ACTIONS['sale.priceCheck'].status, 'partial');
  assert.equal(km.ACTIONS['sale.priceCheck'].webStatus, 'planned');
  const sale = km.help('sale');
  const ids = sale.map((r) => r.id);
  assert.ok(!ids.includes('sale.priceCheck'), 'Alt+I در راهنمای وب آمد');
  assert.ok(!sale.some((r) => r.keys.includes('Alt+I')), 'Alt+I در راهنمای وب آمد');
  //  هیچ کلیدی که وب پیاده نکرده — هر دامنه، با status ِ کامپیوترِ live یا partial — در راهنمای وب نیست
  for (const scope of Object.keys(km.SCOPES)) {
    for (const r of km.help(scope)) {
      assert.notEqual(km.ACTIONS[r.id].webStatus, 'planned', `${scope}: ${r.id}`);
      assert.ok(km.ACTIONS[r.id].web.length, `${scope}: ${r.id} بی کلیدِ وب`);
    }
  }
  //  آنچه index.html واقعاً دارد: Alt+۱ تا ۹، «/» و Esc
  assert.deepEqual(km.help('reports').map((r) => r.keys.join('/')),
    ['Alt+1', 'Alt+2', 'Alt+3', 'Alt+4', 'Alt+5', 'Alt+6', 'Alt+7', 'Alt+8', 'Alt+9', '/', 'Esc']);
  assert.equal(km.help('sale', true)[0].keys[0], 'Option+1');
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  assert.ok(!/KeyI|e\.key === 'i'/.test(html.slice(html.indexOf('const KEY_PAGES'), html.indexOf('wireExpiryPolicy'))),
    'اگر وب قیمت‌پرسی گرفت، webStatus ِ sale.priceCheck را هم عوض کنید');
  //  generator وضعیتِ وبِ نادرست و «live بی کلیدِ وب» را رد می‌کند
  const gen = await import(GEN);
  const base = JSON.parse(fs.readFileSync(gen.PATHS.json, 'utf8'));
  const bad = (mutate) => { const j = structuredClone(base); mutate(j); return () => gen.load(JSON.stringify(j)); };
  const zoom = (j) => j.actions.find((a) => a.id === 'global.zoomIn');
  assert.throws(bad((j) => { zoom(j).webStatus = 'live'; }), /کلیدِ وب ندارد/);
  assert.throws(bad((j) => { j.actions[0].webStatus = 'soon'; }), /وضعیتِ وبِ نادرست/);
});

test('POS-41: Alt+P ِ پنلِ پرداخت (چاپِ همین فاکتور) در کامپیوتر partial است؛ وب پنلِ پرداخت ندارد ⇒ planned و بیرون از راهنمای وب', () => {
  const a = km.ACTIONS['sale.payment.togglePrint'];
  assert.equal(a.status, 'partial');
  assert.equal(a.webStatus, 'planned');
  assert.equal(km.label('sale.payment.togglePrint'), 'Alt+P');
  assert.ok(!km.help('sale.payment').some((r) => r.id === 'sale.payment.togglePrint'));
  //  دو کلیدِ Tab ِ همان پنل هنوز planned اند (سنجش روی کامپیوترِ واقعی)
  assert.equal(km.ACTIONS['sale.payment.nextField'].status, 'planned');
  assert.equal(km.ACTIONS['sale.payment.prevField'].status, 'planned');
  //  پنلِ کامپیوتر کلید را از keymap می‌گیرد، نه نوشتهٔ دستی
  const panel = fs.readFileSync(path.join(ROOT, 'android-native/app/src/main/java/ir/vil3ntec/tohid/ui/components/pos/PayPanel.kt'), 'utf8');
  assert.ok(panel.includes('hit(KeyMap.SALE_PAYMENT_TOGGLE_PRINT) -> PayPanelKey.TOGGLE_PRINT'));
});
