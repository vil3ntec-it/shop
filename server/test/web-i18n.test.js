'use strict';
/**
 * سه زبانِ وبِ خریدار (fa پیش‌فرض، en، ps) — واژه‌نامه‌های server/public/kit/i18n.
 *
 * آن‌چه این پرونده قفل می‌کند (بی مرورگر و بی پایگاه):
 *   ۱) هر کلیدی که کیت، بازارچه و ویترین به کار می‌برند (K.t، data-t/ta/tp و
 *      خانواده‌های پویا: دستهٔ دکان، وضعیت و دلیلِ سفارش، پذیرش) در هر سه زبان هست.
 *   ۲) سه واژه‌نامه همان کلیدها را دارند (جز شکلِ مفردِ اختیاریِ «‎.1‎»)، و جای‌نگهدارهای
 *      {…} ِ هر متن در سه زبان یکی است.
 *   ۳) en هیچ حرفِ فارسی/پشتو ندارد؛ هیچ متنِ خالی نیست.
 *   ۴) در JS ِ صفحه‌ها هیچ متنِ فارسیِ ثابت نمانده (همه از واژه‌نامه)، جز واژه‌های پایهٔ
 *      `K.FB` (وقتی واژه‌نامه نیامد) که باید بی‌کم‌وکاست همان fa.json باشند.
 *   ۵) هر کدِ خطای سرور که صفحه نشان می‌دهد ترجمهٔ خودش را دارد.
 *   ۶) هر واژه‌نامه زیرِ ۲۱KB (بارِ نخستِ صفحه = JS + واژه‌نامه؛ شرح سرِ همان آزمون).
 *   ۷) واژه‌نامهٔ جدای صفحهٔ پیگیریِ سفارش (`kit/i18n/order.<زبان>.json`، `K.part('order')`) فقط کلیدهای
 *      همان صفحه را دارد، هیچ کلیدِ واژه‌نامهٔ اصلی یا `K.FB` را نه، و زیرِ ۴KB است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const pub = path.join(__dirname, '..', 'public');
const read = (f) => fs.readFileSync(path.join(pub, f), 'utf8');
const LANGS = ['fa', 'en', 'ps'];
const main = Object.fromEntries(LANGS.map((l) => [l, JSON.parse(read(`kit/i18n/${l}.json`))]));
//  صفحهٔ پیگیریِ سفارش واژه‌نامهٔ خودش را با `K.part('order')` می‌گیرد؛ کلیدها روی هم همان یک واژه‌نامه‌اند
const part = Object.fromEntries(LANGS.map((l) => [l, JSON.parse(read(`kit/i18n/order.${l}.json`))]));
const dict = Object.fromEntries(LANGS.map((l) => [l, { ...main[l], ...part[l] }]));
const SOURCES = ['kit/kit.js', 'market/market.js', 'store/store.js', 'store/order.js', 'store/checkout.js', 'store/product.js', 'store/share.js', 'store/recent.js', 'market/index.html', 'store/index.html'];
const ARABIC = /[\u0600-\u06FF]/;

function usedKeys() {
  const keys = new Set();
  for (const f of SOURCES) {
    const s = read(f);
    for (const m of s.matchAll(/\b(?:K\.)?t\(\s*'([a-z][\w.]*[\w])'/g)) keys.add(m[1]);
    for (const m of s.matchAll(/\bt\(\s*[^'()]*\?\s*'([a-z][\w.]*)'\s*:\s*'([a-z][\w.]*)'/g)) { keys.add(m[1]); keys.add(m[2]); }
    for (const m of s.matchAll(/label: '([a-z][\w.]*)'/g)) keys.add(m[1]);
    for (const m of s.matchAll(/\['((?:od|ck)\.[\w.]+)',/g)) keys.add(m[1]);
    for (const m of s.matchAll(/data-t[ap]?="([^"]+)"/g)) keys.add(m[1]);
    for (const m of s.matchAll(/K\.has\('([a-z][\w.]*[\w])'\)/g)) keys.add(m[1]);
  }
  //  خانواده‌های پویا از منبعِ سرور
  const { CATEGORIES } = require('../src/lib/market');
  const { ORDER_STATUSES, REASONS } = require('../src/lib/order-states');
  for (const c of CATEGORIES) keys.add(`cat.${c.key}`);
  for (const s of ORDER_STATUSES) keys.add(`order.${s}`);
  for (const r of new Set(Object.values(REASONS).flat())) keys.add(`reason.${r}`);
  for (const a of ['accepting', 'delayed', 'later']) keys.add(`accept.${a}`);
  return keys;
}
const holes = (s) => [...String(s).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');

test('هر کلیدِ به‌کاررفته در هر سه زبان هست', () => {
  const keys = usedKeys();
  assert.ok(keys.size > 250, `کلیدها پیدا شدند (${keys.size})`);
  for (const l of LANGS) {
    const missing = [...keys].filter((k) => typeof dict[l][k] !== 'string');
    assert.deepEqual(missing, [], `${l}: کلیدِ ناموجود`);
  }
});

test('سه واژه‌نامه همان کلیدها و همان جای‌نگهدارها را دارند؛ هیچ متنی خالی نیست', () => {
  const base = (d) => Object.keys(d).filter((k) => !k.endsWith('.1')).sort();
  assert.deepEqual(base(dict.en), base(dict.fa), 'en = fa');
  assert.deepEqual(base(dict.ps), base(dict.fa), 'ps = fa');
  for (const l of LANGS) {
    for (const [k, v] of Object.entries(dict[l])) {
      assert.ok(typeof v === 'string' && v.trim(), `${l}.${k} خالی است`);
      const ref = dict.fa[k.replace(/\.1$/, '')];
      //  شکلِ مفرد شمار را خودش می‌نویسد ({n} ندارد)
      const want = k.endsWith('.1') ? holes(ref).replace(/(^|,)n(,|$)/, '$1').replace(/^,|,$/g, '') : holes(ref);
      assert.equal(holes(v), want, `${l}.${k}: جای‌نگهدارها`);
    }
  }
  //  کلیدِ مفرد فقط برای کلیدی که {n} دارد
  for (const l of LANGS) {
    for (const k of Object.keys(dict[l]).filter((x) => x.endsWith('.1'))) {
      assert.ok(/\{n\}/.test(dict.fa[k.slice(0, -2)]), `${l}.${k} بی {n}`);
    }
  }
});

test('انگلیسی هیچ حرفِ فارسی/پشتو ندارد؛ فارسی و پشتو فارسی‌نویس‌اند', () => {
  const bad = Object.entries(dict.en).filter(([, v]) => ARABIC.test(v)).map(([k]) => k);
  assert.deepEqual(bad, []);
  for (const l of ['fa', 'ps']) {
    const latinOnly = Object.entries(dict[l]).filter(([k, v]) => !ARABIC.test(v) && !/^(sep|dist|title\.market|a\.logo)$/.test(k) && /[a-z]{3}/i.test(v)).map(([k]) => k);
    assert.deepEqual(latinOnly, [], `${l}: متنِ ترجمه‌نشده`);
  }
  //  پشتو با فارسی یکی نیست (جز چند واژهٔ مشترکِ واقعی)
  const same = Object.keys(dict.fa).filter((k) => dict.ps[k] === dict.fa[k] && ARABIC.test(dict.fa[k]));
  assert.ok(same.length < 15, `پشتو بیش از اندازه همان فارسی است: ${same.join(' ')}`);
});

test('در JS ِ صفحه‌ها متنِ فارسیِ ثابت نمانده', () => {
  for (const f of ['kit/kit.js', 'market/market.js', 'store/store.js', 'store/order.js', 'store/checkout.js', 'store/product.js', 'store/share.js']) {
    const lines = read(f).split('\n').filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l) && !/^\s*K\.FB = \{/.test(l)).map((l) => l.replace(/\s\/\/.*$/, ''));
    //  استثنا: نامِ خودِ زبان‌ها در گزینش‌گر و رقم/جداکنندهٔ فارسی (نه متن)
    const hits = lines.filter((l) => [...l.matchAll(/'([^'\\]*)'/g)].some((m) => ARABIC.test(m[1]) && !/^(فارسی|پښتو|[۰-۹٬٫]+)$/.test(m[1])));
    assert.deepEqual(hits, [], `${f}: متنِ ثابت`);
  }
});

test('کدهای خطای سرور که صفحه نشان می‌دهد ترجمه دارند', () => {
  const codes = ['verification_unavailable', 'verification_required', 'phone_not_verified', 'otp_wrong', 'otp_expired',
    'otp_locked', 'otp_used', 'otp_not_found', 'otp_required', 'otp_resend_wait', 'otp_daily_limit', 'rate_limited',
    'product_unavailable', 'qty_over_limit', 'below_min_order', 'fulfillment_unavailable', 'bad_address', 'bad_customer',
    'bad_lines', 'store_not_found', 'store_not_accepting_orders', 'market_not_found', 'order_not_found', 'cannot_cancel', 'query_too_short', 'bad_location'];
  for (const l of LANGS) for (const c of codes) assert.ok(dict[l][`err.${c}`], `${l}: err.${c}`);
});

test('واژه‌های پایهٔ K.FB (واژه‌نامه نیامد) همان fa.json‌اند و فقط یک خط', () => {
  const lines = read('kit/kit.js').split('\n').filter((l) => /^\s*K\.FB = \{/.test(l));
  assert.equal(lines.length, 1);
  const fb = Function(`'use strict'; return (${lines[0].replace(/^\s*K\.FB = /, '').replace(/;\s*$/, '')});`)();
  assert.ok(Object.keys(fb).length >= 30, 'سبد، ثبت و وضعیتِ سفارش را می‌پوشاند');
  for (const k of ['money', 'ps.add', 'ck.title', 'ck.sending', 'bar.label', 'od.placed', 'order.new', 'err.generic']) assert.ok(fb[k], k);
  for (const [k, v] of Object.entries(fb)) assert.equal(v, main.fa[k], `K.FB['${k}'] = fa.json`);
});

//  ۲۲KB از ۱ نوامبر ۲۰۲۶ (پیش‌تر ۲۰KB)؛ سپس صفحهٔ پیگیریِ سفارش واژه‌نامهٔ جدای خودش را گرفت (fa.json ۲۱٬۲۸۶ ⇐
//  ۱۹٬۴۷۹ بایت) و سقف ۲۱KB شد — جا برای کلیدهای فرمِ ثبت (CHK) تا آن هم مثلِ پیگیری جدا شود، سپس ۲۰KB.
test('هر واژه‌نامه زیرِ ۲۱KB است', () => {
  for (const l of LANGS) {
    const n = fs.statSync(path.join(pub, 'kit', 'i18n', `${l}.json`)).size;
    assert.ok(n <= 21 * 1024, `${l}.json: ${n} بایت`);
  }
});

test('واژه‌نامهٔ صفحهٔ پیگیری فقط کلیدهای همان صفحه را دارد و کوچک است', () => {
  const html = read('store/index.html');
  const from = html.indexOf('id="view-order"');
  const to = html.indexOf('id="view-orders"');
  assert.ok(from > 0 && to > from, 'بخشِ view-order پیدا شد');
  //  هر جای دیگرِ کیت، بازارچه و ویترین (جز order.js و همان بخش) که کلیدی از این واژه‌نامه را بخواهد بی آن می‌ماند
  const elsewhere = SOURCES.filter((f) => f !== 'store/order.js' && f !== 'store/index.html').map(read).join('\n') +
    html.slice(0, from) + html.slice(to);
  const base = (d) => Object.keys(d).filter((k) => !k.endsWith('.1')).sort();
  assert.ok(base(part.fa).length >= 25, `${base(part.fa).length} کلید`);
  assert.deepEqual(base(part.en), base(part.fa), 'en = fa');
  assert.deepEqual(base(part.ps), base(part.fa), 'ps = fa');
  const fbLine = read('kit/kit.js').split('\n').find((l) => /^\s*K\.FB = \{/.test(l));
  for (const l of LANGS) {
    for (const k of Object.keys(part[l])) {
      assert.ok(!(k in main[l]), `${l}: ${k} در هر دو واژه‌نامه`);
      assert.ok(!fbLine.includes(`'${k}'`), `${k} واژهٔ پایهٔ K.FB است و باید در واژه‌نامهٔ اصلی بماند`);
      const b = k.replace(/\.1$/, '');
      assert.ok(!elsewhere.includes(`'${b}'`) && !elsewhere.includes(`"${b}"`), `${b} بیرون از صفحهٔ پیگیری هم به کار رفته`);
    }
    const n = fs.statSync(path.join(pub, 'kit', 'i18n', `order.${l}.json`)).size;
    assert.ok(n <= 4 * 1024, `order.${l}.json: ${n} بایت`);
  }
  //  صفحه همان واژه‌نامه را با بارِ خودش می‌گیرد
  assert.ok(read('store/store.js').includes("K.part('order', $('view-order'))"));
});
