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
//  فرمِ ثبت هم (`K.part('checkout')`، با checkout.js ِ دیربار): هر بخش فقط کلیدهای صفحهٔ خودش
const PARTS = {
  order: { js: 'store/order.js', from: 'id="view-order"', to: 'id="view-orders"' },
  checkout: { js: 'store/checkout.js', from: 'id="view-checkout"', to: 'id="view-order"' },
  //  دلخواه‌ها و فهرستِ خرید (WEB-11، catalog.js ِ دیربار): فقط بخشِ `#saved` ترجمه می‌شود، نه کلِ کاتالوگ (نشانه‌های data-i)
  catalog: { js: 'store/catalog.js', from: 'id="saved"', to: 'id="recent"', root: 'saved' },
  //  فیلتر و «خبرم کن» (WEB-07/10، filters.js ِ دیربار): فرمِ `#filters`؛ دکمه‌های برگهٔ کالا را خودِ filters.js می‌سازد
  filters: { js: 'store/filters.js', from: 'id="filters"', to: 'id="stock-note"', root: 'filters' },
  //  گزارشِ مشکل و نظرِ سفارش (NTF-07/09، order-feedback.js ِ دیربار): گره‌ها را خودِ اسکریپت می‌سازد، پس بخشی در index.html ندارد
  feedback: { js: 'store/order-feedback.js', root: 'o-fb' },
  //  پیشنهادِ ویرایش و پیگیریِ بازپرداخت (ORD-14/NTF-08، order-changes.js ِ دیربار): گره‌ها را خودِ اسکریپت می‌سازد
  changes: { js: 'store/order-changes.js', root: 'o-ch' },
  //  نظرهای تأییدشده روی صفحهٔ دکان (NTF-09، reviews.js ِ دیربار، فقط با `features.reviews`): بخشش را هم خودِ اسکریپت می‌سازد
  reviews: { js: 'store/reviews.js', root: 'rv' },
  //  «حسابِ من» (CRM-13، account.js ِ دیربار): بخش را خودِ account.js می‌سازد ⇒ پوستهٔ HTML بی هیچ کلیدِ آن
  account: { js: 'store/account.js', from: 'id="view-orders"', to: '</main>', root: 'view-orders' },
  //  «همین کالا در دکان‌های دیگر» (S70، same.js ِ دیربار از product.js، فقط برای کالای پیوسته به بانک): بخش را خودِ اسکریپت می‌سازد
  same: { js: 'store/same.js', root: 'ps-same' },
  //  گالریِ عکس‌های بیشترِ کالا (PRD-17/27، gallery.js ِ دیربار از product.js، فقط با `features.gallery`): بخش را خودِ اسکریپت می‌سازد
  gallery: { js: 'store/gallery.js', root: 'ps-gal' },
  //  بزرگ‌نماییِ عکسِ کالا (IMG-11، zoom.js ِ دیربار از product.js، فقط برای کالای عکس‌دار): دکمه و بیننده را خودِ اسکریپت می‌سازد
  zoom: { js: 'store/zoom.js', root: 'ps-zoom' },
  //  «انتخاب روی نقشه» ِ پینِ نشانی (CHK-08/۴-۳، pin-map.js ِ دیربار از برگهٔ ثبت، فقط با `features.mapPin`): بخش را خودِ اسکریپت می‌سازد
  map: { js: 'store/pin-map.js', root: 'pin-map-box' },
  //  «جست‌وجو با عکس» (AI-11، visual-search.js ِ دیربار از catalog.js، فقط با `features.visualSearch`): بخش را خودِ اسکریپت می‌سازد
  vsearch: { js: 'store/visual-search.js', root: 'vs' },
  //  روش و مسئولِ تحویل و «تغییرِ روش» (S71/S72، delivery-method.js ِ دیربار از برگهٔ ثبت و پیگیری): گره‌ها را خودِ اسکریپت می‌سازد
  method: { js: 'store/delivery-method.js', root: 'o-dm' },
  //  گفت‌وگو با دکان (یک رشته برای هر سفارش، chat.js ِ دیربار از سرِ ویترین و پیگیری، فقط با `features.chat`/`order.chat`):
  //  برگه و کارتِ پیگیری را خودِ اسکریپت پس از رسیدنِ واژه‌نامه می‌سازد
  chat: { js: 'store/chat.js', root: 'chat-sheet' },
  //  صفِ پیام‌های نرفتهٔ گفت‌وگو (CHAT-02، chat-out.js ِ دیربار از chat.js): حباب‌های «در صف» را خودِ اسکریپت می‌سازد
  chatq: { js: 'store/chat-out.js', root: 'chat-sheet' },
  //  پاسخ، ویرایش، سنجاق و جست‌وجوی گفت‌وگو (CHAT-14، chat-acts.js ِ دیربار از chat.js): گره‌ها را خودِ اسکریپت می‌سازد
  chatacts: { js: 'store/chat-acts.js', root: 'chat-sheet' },
  //  ضبطِ پیامِ صوتیِ مشتری (CHAT-07، chat-voice.js ِ دیربار با دکمهٔ میکروفون، فقط با `limits.voice`): بخش را خودِ اسکریپت می‌سازد
  voice: { js: 'store/chat-voice.js', root: 'ct-vc' },
  //  «پرداخت پس از پذیرش» (lib/order-flow، pay-step.js ِ دیربار از پیگیری، فقط با `order.paymentStep`): بخش را خودِ اسکریپت می‌سازد
  paystep: { js: 'store/pay-step.js', root: 'o-ps' },
  //  یادداشت و «اگر نبود…» ِ هر قلم (CHK-16/15، line-prefs.js ِ دیربار از برگهٔ ثبت و پیگیری): گره‌ها را خودِ اسکریپت می‌سازد
  lines: { js: 'store/line-prefs.js', root: 'cart-lines' },
  //  حسابِ مشتری در وب (CHK-01/03/09، WEB-14؛ acct.js ِ دیربار، فقط با `features.webAccount`): گره‌ها را خودِ اسکریپت می‌سازد
  //  cart-sync.js (سبدِ حساب) همان واژه‌نامه را دارد: دیربار فقط از acct.js و پس از آن
  //  erase.js (SEC-10، «پاک کردنِ داده‌های من»، دیربار فقط از acct.js و پس از واژه‌نامهٔ آن) کادرِ کدِ همین واژه‌نامه را به کار می‌برد
  acct: { js: 'store/acct.js', also: ['store/cart-sync.js', 'store/erase.js'], root: 'ac-box' },
  //  «پاک کردنِ داده‌های من در این دکان» (SEC-10، erase.js ِ دیربار از acct.js، فقط با `features.erasure`): بخش را خودِ اسکریپت می‌سازد
  erase: { js: 'store/erase.js', root: 'er-box' },
  //  «خبرم کن روی همین دستگاه» (پوشِ وب، CHK-20/NTF-01؛ order-push.js ِ دیربار از پیگیری، فقط با `order.push`): بخش را خودِ اسکریپت می‌سازد
  //  «روی همین دستگاه هم خبرم کن» ِ «خبرم کن» ِ کالا (WEB-10، alert-push.js ِ دیربار از filters.js، فقط با `push` ِ «هشدارهای من»)
  //  همان واژه‌نامه را دارد؛ push-client.js (کارگرِ سرویس و اشتراکِ مشترکِ هر دو) هیچ متنی ندارد
  push: { js: 'store/order-push.js', also: ['store/alert-push.js', 'store/push-client.js'], root: 'o-np' },
  //  خلاصهٔ صفحهٔ تأیید (CHK-20، §۱۹.۲ بندِ ۸: دکانِ مسئول، ناحیه، بازه و «کپیِ نشانیِ پیگیری»؛ order-facts.js ِ دیربار از پیگیری)
  facts: { js: 'store/order-facts.js', root: 'o-facts' },
  //  «وزنِ بیشتر از سفارش» (S56، weight-hold.js ِ دیربار از پیگیری، فقط با کلیدِ `weightHold` = نگه‌داشتِ باز): بخش را خودِ اسکریپت می‌سازد
  weigh: { js: 'store/weight-hold.js', root: 'o-wh' },
  //  عکسِ دروازه و یادداشتِ صوتیِ نشانی (CHK-08، addr-media.js ِ دیربار از پیگیری، فقط با کلیدِ `addressMedia`): بخش را خودِ اسکریپت می‌سازد
  addrmedia: { js: 'store/addr-media.js', root: 'o-am' },
  //  کارتِ «کالای جایگزین» و «جایگزینی در انتظارِ پاسخِ شما» (PCK-05، S93؛ order.js فقط وقتی سفارش «در حال جمع‌آوری» است):
  //  بخشِ `#o-subs` ِ index.html و نشانِ وضعیت با کلیدِ `substitutionWait` ِ پیگیری
  subs: { js: 'store/order.js', from: 'id="o-subs"', to: 'data-t="od.items"', root: 'o-subs' },
};
const parts = Object.fromEntries(Object.keys(PARTS).map((n) => [n, Object.fromEntries(LANGS.map((l) => [l, JSON.parse(read(`kit/i18n/${n}.${l}.json`))]))]));
const dict = Object.fromEntries(LANGS.map((l) => [l, Object.assign({}, main[l], ...Object.values(parts).map((p) => p[l]))]));
const SOURCES = ['kit/kit.js', 'kit/settings.js', 'kit/order-kit.js', 'market/market.js', 'store/store.js', 'store/order.js', 'store/checkout.js', 'store/product.js', 'store/share.js', 'store/recent.js', 'store/catalog.js', 'store/filters.js', 'store/order-feedback.js', 'store/order-changes.js', 'store/reviews.js', 'market/index.html', 'store/index.html', 'store/account.js', 'store/visual-search.js', 'store/delivery-method.js', 'store/chat.js', 'store/chat-out.js', 'store/chat-acts.js', 'store/chat-voice.js', 'store/pay-step.js', 'store/line-prefs.js', 'store/acct.js', 'store/cart-sync.js', 'store/erase.js', 'store/order-push.js', 'store/sw.js', 'store/alert-push.js', 'store/push-client.js', 'store/same.js', 'store/gallery.js', 'store/zoom.js', 'store/order-facts.js', 'store/pin-text.js', 'store/pin-map.js', 'store/weight-hold.js', 'store/addr-media.js'];
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
  for (const f of ['kit/kit.js', 'kit/settings.js', 'kit/order-kit.js', 'market/market.js', 'store/store.js', 'store/order.js', 'store/checkout.js', 'store/product.js', 'store/share.js', 'store/catalog.js', 'store/filters.js', 'store/reviews.js', 'store/account.js', 'store/delivery-method.js', 'store/chat.js', 'store/chat-photo.js', 'store/chat-out.js', 'store/chat-acts.js', 'store/chat-voice.js', 'store/pay-step.js', 'store/line-prefs.js', 'store/acct.js', 'store/cart-sync.js', 'store/erase.js', 'store/order-push.js', 'store/sw.js', 'store/alert-push.js', 'store/push-client.js', 'store/same.js', 'store/gallery.js', 'store/zoom.js', 'store/pin-text.js', 'store/pin-map.js', 'store/weight-hold.js']) {
    const lines = read(f).split('\n').filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l) && !/^\s*K\.FB = \{/.test(l)).map((l) => l.replace(/\s\/\/.*$/, ''));
    //  استثنا: نامِ خودِ زبان‌ها در گزینش‌گر و رقم/جداکنندهٔ فارسی (نه متن)
    const hits = lines.filter((l) => [...l.matchAll(/'([^'\\]*)'/g)].some((m) => ARABIC.test(m[1]) && !/^(فارسی|دری|پښتو|[۰-۹٬٫]+)$/.test(m[1])));
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

for (const [name, P] of Object.entries(PARTS)) test(`واژه‌نامهٔ ${name} فقط کلیدهای همان صفحه را دارد و کوچک است`, () => {
  const part = parts[name];
  const html = read('store/index.html');
  const from = P.from ? html.indexOf(P.from) : html.length;
  const to = P.from ? html.indexOf(P.to) : html.length;
  assert.ok(!P.from || (from > 0 && to > from), `بخشِ ${P.from} پیدا شد`);
  //  هر جای دیگرِ کیت، بازارچه و ویترین (جز order.js و همان بخش) که کلیدی از این واژه‌نامه را بخواهد بی آن می‌ماند
  const elsewhere = SOURCES.filter((f) => f !== P.js && !(P.also || []).includes(f) && f !== 'store/index.html').map(read).join('\n') +
    html.slice(0, from) + html.slice(to);
  const base = (d) => Object.keys(d).filter((k) => !k.endsWith('.1')).sort();
  assert.ok(base(part.fa).length >= 10, `${base(part.fa).length} کلید`);
  assert.deepEqual(base(part.en), base(part.fa), 'en = fa');
  assert.deepEqual(base(part.ps), base(part.fa), 'ps = fa');
  const fbLine = read('kit/kit.js').split('\n').find((l) => /^\s*K\.FB = \{/.test(l));
  for (const l of LANGS) {
    for (const k of Object.keys(part[l])) {
      assert.ok(!(k in main[l]), `${l}: ${k} در هر دو واژه‌نامه`);
      for (const other of Object.keys(PARTS).filter((x) => x !== name)) assert.ok(!(k in parts[other][l]), `${l}: ${k} در ${name} و ${other}`);
      assert.ok(!fbLine.includes(`'${k}'`), `${k} واژهٔ پایهٔ K.FB است و باید در واژه‌نامهٔ اصلی بماند`);
      const b = k.replace(/\.1$/, '');
      assert.ok(!elsewhere.includes(`'${b}'`) && !elsewhere.includes(`"${b}"`), `${b} بیرون از صفحهٔ پیگیری هم به کار رفته`);
    }
    const n = fs.statSync(path.join(pub, 'kit', 'i18n', `${name}.${l}.json`)).size;
    assert.ok(n <= 4 * 1024, `${name}.${l}.json: ${n} بایت`);
  }
  //  صفحه همان واژه‌نامه را با بارِ خودش می‌گیرد
  assert.ok((read('store/store.js') + read(P.js)).includes(`K.part('${name}', $('${P.root || `view-${name}`}'))`));
});
