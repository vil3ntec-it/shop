'use strict';
/**
 * LOC-06/LOC-08 در وبِ خریدار — لایهٔ دریِ `public/kit/dari.js` (همان `core/.../text/DariText.kt`). بی مرورگر و بی پایگاه.
 *   ۱) فهرست (WORDS، PHRASES، KEEP و دو جدولِ پسوند) همان DariText.kt است (`scripts/dari-words.js`)؛ واژهٔ تازه
 *      در کاتلین بی `node scripts/dari-words.js --write` ⇒ همین آزمون سرخ.
 *   ۲) هر `assertEquals("…", DariText.of("…"))` ِ `DariTextTest.kt` (خوانده از خودِ پرونده) همین‌جا هم، و بقیهٔ
 *      موردهای همان پرونده (واژهٔ درونِ واژه، protect، چسبیدن) دستی.
 *   ۳) قالبِ وب: {جای‌خالی} و دادهٔ پرشده (نامِ کالا، دکان) دست نمی‌خورد؛ واژهٔ چسبیده به جای‌خالی نه؛ KEYS.
 *   ۴) واژه‌های نامطمئن (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۱۱) و گفتاری فارسی می‌مانند.
 *   ۵) روی واژه‌نامه‌های واقعیِ وب: جای‌خالی‌ها همان، هیچ «مصرف/مصارف» (هزینهٔ مشتری ≠ مصارفِ دکان)، فارسی دست‌نخورده.
 *   ۶) بارِ نخست (store.js + kit.js + boot.js) زیرِ ۷۵KB و dari.js در آن نیست؛ فقط kit.js با prs می‌خواهدش.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const W = require('../scripts/dari-words');
const D = require('../public/kit/dari');
const KT = require('./kt-source');
const { FIRST_LOAD } = require('./storefront-budget');

const PUB = path.join(__dirname, '..', 'public');
const read = (f) => fs.readFileSync(path.join(PUB, f), 'utf8');
const ZWNJ = '\u200C';
const KT_TEST = path.join(__dirname, '..', '..', 'core', 'src', 'commonTest', 'kotlin', 'ir', 'vil3ntec', 'tohid', 'core', 'text', 'DariTextTest.kt');
const p = (s) => D.protect(s);
const holes = (s) => [...String(s).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');
/** همان پر کردنِ `K.t` ِ کیت: پس از لایه */
const fill = (tpl, v) => tpl.replace(/\{(\w+)\}/g, (m, k) => (v[k] === undefined ? m : v[k]));

test('فهرستِ وب همان DariText.kt است (منبعِ یکتا)', () => {
  const want = W.build();
  assert.deepEqual(JSON.parse(JSON.stringify(D.DATA)), want);
  assert.equal(W.current().text, W.render(want), 'بلوکِ dari.js کهنه است: node scripts/dari-words.js --write');
  //  ۱۴۰۵/۰۸/۱۳: سقف ۲۴ ⇐ ۶۴ با خواستِ «واژهٔ دریِ بیشتر» (همان سقفِ DariTextTest.kt)
  assert.ok(want.words.length >= 15 && want.words.length <= 64, `${want.words.length} واژه — فهرست کوتاه می‌ماند`);
  for (const w of want.words) assert.ok(w.fa && w.dari && w.fa !== w.dari, w.fa);
});

test('سازنده سطرِ ناشناختهٔ کاتلین را بی‌صدا رد نمی‌کند', () => {
  const src = fs.readFileSync(W.KT, 'utf8');
  assert.throws(() => W.build(src.replace('Word("خیابان", "سرک"),', 'Word("خیابان", "سرک", vowel = true),')), /پارامترِ ناشناخته|سطرِ ناشناخته/);
  assert.throws(() => W.build(src.replace('Word("خیابان", "سرک"),', 'Word(fa = "خیابان", dari = "سرک"),')), /سطرِ ناشناخته/);
  assert.throws(() => W.build(src.replace('Word("خیابان", "سرک"),', 'Word("خیابان", "سرک\\u0020"),')), /گریز/);
});

test('همهٔ برابری‌های سادهٔ DariTextTest.kt در وب هم برقرار است', () => {
  const kt = fs.readFileSync(KT_TEST, 'utf8');
  const str = (s) => s.replace(/\$\{zwnj\}/g, ZWNJ);
  const cases = [...kt.matchAll(/assertEquals\("((?:[^"\\$]|\$\{zwnj\})*)", DariText\.of\("((?:[^"\\$]|\$\{zwnj\})*)"\)\)/g)]
    .map((m) => [str(m[1]), str(m[2])]);
  assert.ok(cases.length >= 30, `${cases.length} مورد از کاتلین`);
  for (const [want, fa] of cases) assert.equal(D.of(fa), want, fa);
});

test('واژه درونِ واژهٔ دیگر، پسوندِ ناشناخته و متنِ بی واژه دست نمی‌خورد (همان کاتلین)', () => {
  for (const s of ['داستان', 'درصدد', 'خرگوشی', 'انبارگردانی', 'استانبول', 'گوشه', 'کارمندیار', `پیش${ZWNJ}انبار`,
    'انباران', `هزینه${ZWNJ}هایش`, 'ذخیره', 'Save', '', 'مانده: 1,250 افغانی', 'خوندي کول', 'شاگرد']) {
    assert.equal(D.of(s), s, s);
  }
  for (const w of D.DATA.words) assert.equal(D.of(w.dari), w.dari, `دریِ «${w.fa}» دوباره عوض می‌شود`);
});

test('دادهٔ protect‌شده (asIs) بایت‌به‌بایت همان؛ واژهٔ چسبیده به داده نه (همان کاتلین)', () => {
  const product = 'گوشی سامسونگ انبار';
  assert.equal(D.of(`گوشی «${p(product)}» به انبار رفت`), `مبایل «${product}» به گدام رفت`);
  const customer = 'فروشگاه کریم';
  assert.equal(D.of(`${p(customer)} — ${p('500')} ؋ بدهکاریم`), `${customer} — 500 ؋ قرض‌داریم`);
  assert.equal(D.of(product), 'مبایل سامسونگ گدام', 'بی نشانه، همان متن دری می‌شد');
  const out = D.of(`${p('گوشی')}${p('')} و ${p('انبار')}`);
  assert.equal(out, 'گوشی و انبار');
  assert.ok(![...out].some((c) => c === D.OPEN || c === D.CLOSE));
  assert.equal(D.of(`${p(`پیش${ZWNJ}`)}انبار`), `پیش${ZWNJ}انبار`);
  assert.equal(D.of(`انبار${p('گردانی')}`), 'انبارگردانی');
  assert.equal(D.of(`${p('12')} انبار`), '12 گدام');
  assert.equal(D.strip('گوشی'), 'گوشی');
  assert.equal(D.strip(`کالای ${p('گوشی')}`), 'کالای گوشی');
});

test('قالبِ وب: {جای‌خالی} و دادهٔ پرشده هرگز دری نمی‌شود', () => {
  //  نامِ کالا و دکان (داده) پس از لایه می‌نشیند ⇒ همان که فروشنده نوشته
  const v = { name: 'گوشی سامسونگ', shop: 'فروشگاه کریم', m: '۱۲۰', n: '۳' };
  assert.equal(fill(D.t('افزودنِ {name} به سبد'), v), 'افزودنِ گوشی سامسونگ به سبد');
  assert.equal(fill(D.t('{shop} — گوشیِ {name}'), v), 'فروشگاه کریم — مبایلِ گوشی سامسونگ');
  assert.equal(D.t('{m} بدهکارید'), '{m} قرض‌دارید');
  assert.equal(fill(D.t('{n} درصد از {name} در انبار'), v), '۳ فیصد از گوشی سامسونگ در گدام');
  //  چسبیده به جای‌خالی: نمی‌دانیم چه می‌آید ⇒ دست نزن
  assert.equal(D.t('{x}انبار'), '{x}انبار');
  assert.equal(D.t('انبار{x}'), 'انبار{x}');
  assert.equal(D.t(`انبار${ZWNJ}{x}`), `انبار${ZWNJ}{x}`);
  //  بی واژهٔ فارسی، تهی و انگلیسی همان
  for (const s of ['', '{n}', 'Checkout', '۱۲۳']) assert.equal(D.t(s), s);
  //  KEYS بر لایه جلو است؛ null = همان فارسی
  assert.equal(D.t('هزینهٔ سبدِ کوچک {fee}', 'zone.small'), 'هزینهٔ سبدِ کوچک {fee}');
  assert.equal(D.t('هزینهٔ سبدِ کوچک {fee}'), 'مصرفِ سبدِ کوچک {fee}', 'بی KEYS لایه «مصرف» می‌گذاشت');
  //  PHRASES ِ کاتلین و KEEP
  assert.equal(D.t('تحویل و هزینه'), 'تحویل و کرایه');
  assert.equal(D.t('فروشگاه من — برنامهٔ گوشی'), 'فروشگاه من — برنامهٔ مبایل');
});

test('واژه‌های نامطمئن و گفتاری فارسی می‌مانند', () => {
  //  تصمیمِ صاحب‌کار (README ِ claude-ops §۲.۲): تا صاحب‌کار نگوید عوض نمی‌شوند
  const unsure = ['انبارگردانی', 'گاوصندوق', 'اسکناس', 'پیامک', 'تلفن', 'کالا', 'سود', 'چاپگر', 'تراکنش', 'کیسه', 'دوربین',
    'پول', 'ماشین', 'بله', 'نه', 'خرید', 'سبد', 'سفارش', 'تحویل', 'رایگان', 'قیمت'];
  for (const w of unsure) {
    assert.equal(D.t(w), w, w);
    assert.equal(D.t(`این ${w} را ببینید`), `این ${w} را ببینید`, w);
  }
  const fa = new Set(D.DATA.words.map((w) => w.fa));
  for (const w of unsure) assert.ok(!fa.has(w), `«${w}» نامطمئن است و در فهرست نیست`);
});

//  همهٔ واژه‌نامه‌های فارسیِ وب: اصلی + هر بخش (K.part)
const dictFiles = fs.readdirSync(path.join(PUB, 'kit', 'i18n')).filter((f) => /^(fa|[a-z]+\.fa)\.json$/.test(f)).sort();

test('روی واژه‌نامه‌های واقعیِ وب: جای‌خالی‌ها همان، ناخالی، بی «مصرف»، فارسی دست‌نخورده', () => {
  assert.ok(dictFiles.includes('fa.json') && dictFiles.length >= 10, dictFiles.join(' '));
  const changed = [];
  const all = {};
  for (const f of dictFiles) {
    const raw = read(`kit/i18n/${f}`);
    const fa = JSON.parse(raw);
    for (const [k, v] of Object.entries(fa)) {
      all[k] = v;
      const out = D.t(v, k);
      assert.ok(typeof out === 'string' && out.trim(), `${f} ${k}: خالی`);
      assert.equal(holes(out), holes(v), `${f} ${k}: جای‌خالی‌ها`);
      const spend = (x) => (x.match(/مصرف|مصارف/g) || []).length;   //  «یک‌بارمصرف» ِ خودِ فارسی می‌ماند
      assert.equal(spend(out), spend(v), `${f} ${k}: «${out}» — هزینهٔ مشتری «مصرف» نیست؛ کلید را در KEYS ِ dari.js بگذار`);
      assert.ok(![...out].some((c) => c === D.OPEN || c === D.CLOSE), `${f} ${k}: نشانه ماند`);
      if (out !== v) changed.push(k);
    }
    assert.equal(JSON.stringify(fa), JSON.stringify(JSON.parse(raw)), `${f}: لایه واژه‌نامهٔ فارسی را عوض کرد`);
  }
  //  واژه‌های پایهٔ K.FB ِ کیت (وقتی واژه‌نامه نیامد) هم از همان K.t می‌گذرند
  const fbLine = read('kit/kit.js').split('\n').find((l) => /^\s*K\.FB = \{/.test(l));
  const fb = Function(`'use strict'; return (${fbLine.replace(/^\s*K\.FB = /, '').replace(/;\s*$/, '')});`)();
  for (const [k, v] of Object.entries(fb)) assert.equal(D.t(v, k), D.t(all[k], k), `K.FB ${k}`);
  assert.ok(changed.length >= 10, `${changed.length} متنِ وب دری شد: ${changed.join(' ')}`);
  for (const k of ['shop.go', 'phone.l', 'stock.wh', 'ck.fee', 'ah.owe']) assert.ok(changed.includes(k), k);
  for (const [k, v] of Object.entries(D.KEYS)) {
    assert.ok(k in all, `KEYS: کلیدِ ${k} در هیچ واژه‌نامه‌ای نیست`);
    if (v !== null) assert.equal(holes(v), holes(all[k]), `KEYS ${k}: جای‌خالی‌ها`);
  }
});

//  دورِ ۶ (پوششِ کامل): شکل‌هایی که دری‌شان فقط در جملهٔ کاملِ PHRASES می‌آید و استثناهای نام‌دارشان —
//  خوانده از خودِ `DariCoverageTest.kt` (`farsiOnlyForms`/`farsiOnlyAllowed`، `test/kt-source.js`)، نه رونوشت
const COVERAGE_KT = 'android-native/app/src/test/java/ir/vil3ntec/tohid/core/i18n/DariCoverageTest.kt';
const FARSI_ONLY = KT.list(COVERAGE_KT, 'private val farsiOnlyForms = listOf(');
const FARSI_ALLOWED = KT.list(COVERAGE_KT, 'private val farsiOnlyAllowed = listOf(');
/** همان `farsiOnlyLeft` ِ کاتلین: استثناها بیرون، سپس هر شکلِ فهرست که ماند */
const farsiOnlyLeft = (text) => {
  let t = text;
  for (const a of FARSI_ALLOWED) t = t.replaceAll(a, '');
  return FARSI_ONLY.filter((w) => t.includes(w));
};

test('دورِ ۶: هیچ متنِ دریِ وب شکلِ فقط‌فارسی ندارد؛ جملهٔ کامل با {جای‌خالی} سرِ جای خود', () => {
  assert.ok(FARSI_ONLY.length >= 8 && FARSI_ONLY.includes('دوباره بزنید'), FARSI_ONLY.join(' '));
  const left = [];
  let n = 0;
  for (const f of dictFiles) {
    for (const [k, v] of Object.entries(JSON.parse(read(`kit/i18n/${f}`)))) {
      if (D.KEYS[k] === null) continue;   //  KEYS ِ null: عمداً فارسی تا تصمیمِ صاحب‌کار
      const out = D.t(v, k);
      if (FARSI_ONLY.some((w) => v.includes(w))) n++;
      for (const w of farsiOnlyLeft(out)) left.push(`${f} ${k}: «${w}» در «${out}»`);
    }
  }
  assert.deepEqual(left, [], 'شکلِ فقط‌فارسی در دریِ وب — جملهٔ کامل در DariText.PHRASES');
  assert.ok(n >= 15, `${n} متنِ وب با شکلِ فقط‌فارسی سنجیده شد`);
  //  نمونه‌ها: خطای شبکه، کدِ تأیید، و قالبی با {جای‌خالی} ِ چسبیده (پس از لایه پر می‌شود)
  assert.equal(D.t('اتصال برقرار نشد؛ اینترنت را ببینید و دوباره بزنید.', 'err.network'), 'اتصال برقرار نشد؛ انترنت را بررسی کنید و دوباره کوشش کنید.');
  assert.equal(D.t('مهلتِ این کد تمام شده است؛ کدِ تازه بخواهید.', 'err.otp_expired'), 'میعادِ این کد ختم شده است؛ کدِ جدید بخواهید.');
  const v = { list: 'گوشی سامسونگ ۲×، ', sum: '۱۲۰ ؋' };
  assert.equal(fill(D.t('قیمت عوض شده است. {list}مبلغِ تازه: {sum}. اگر می‌پذیرید، دوباره بزنید.', 'ck.repriced'), v),
    'قیمت تغییر کرده است. گوشی سامسونگ ۲×، مبلغِ جدید: ۱۲۰ ؋. اگر می‌پذیرید، دوباره کوشش کنید.', 'نامِ کالا دری نمی‌شود');
  assert.equal(D.t('دکان شلوغ بود', 'reason.busy'), 'دکان مصروف بود');
});

//  آینهٔ خط‌به‌خطِ همان آزمونِ کاتلین (`DariCoverageTest`، «… برنامهٔ دکان، برنامهٔ خرید و ویترین»، بخشِ خرید/وب):
//  متن‌ها با همان `DariSources` (ShopperText.kt و regex ِ واژه‌نامه‌ها، نه JSON.parse، بی KEYS)، هر `{}` یک
//  `protect("v<i>")` و همان `DariText.of` (`D.of`). سرخ این‌جا = سرخِ CI ِ اندروید، پیش از آن.
test('آینهٔ DariCoverageTest: برنامهٔ خرید و ویترین با همان of(protect) شکلِ فقط‌فارسی ندارند', () => {
  //  خودِ سنجه می‌تواند سرخ شود (مثلِ دستهٔ ۳۹: np.fail بی جملهٔ PHRASES) و استثنا همان استثنای کاتلین است؛
  //  جملهٔ نمونه ساختگی است تا جملهٔ تازهٔ PHRASES سنجه را بی‌اثر نکند
  const sample = `${D.protect('v0')} آینه؛ دوباره بزنید.`;
  assert.ok(!(D.shape(sample) in D.DATA.phrases), D.shape(sample));
  assert.deepEqual(farsiOnlyLeft(D.of(sample)), ['دوباره بزنید']);
  assert.deepEqual(farsiOnlyLeft('رمز را دوباره بزنید؛ مهلت'), ['مهلت']);
  const shopper = KT.shopperTexts();
  const web = KT.webTexts();
  const bad = [];
  for (const k of new Set([...shopper, ...web])) {
    let i = 0;
    const marked = k.split('{}').reduce((a, b) => a + D.protect(`v${i++}`) + b);
    for (const w of farsiOnlyLeft(D.of(marked))) bad.push(`خرید/وب: «${w}» در «${k}»`);
  }
  assert.deepEqual(bad, [], 'شکلِ فقط‌فارسی در دری ماند — جملهٔ کاملِ دری در DariText.PHRASES بنویسید (و node scripts/dari-words.js --write)');
  assert.ok(shopper.size > 150 && web.size > 300, `خرید ${shopper.size}، وب ${web.size}`);
  //  همان خواندنِ ShopperText: `${…}` و `$نام` ⇒ `{}`، `"…" + "…"` یکی، `x.t(` نه
  const fake = KT.shopperTexts(`${Array.from({ length: 150 }, (_, i) => `a = t("w${i}", "")`).join('\n')}
b = t("x\${f(1)} y", "")
c = t("p" +
  "q$n", "")
d = o.t("z", "")`);
  assert.ok(fake.size === 152 && fake.has('x{} y') && fake.has('pq{}') && !fake.has('z'), [...fake].slice(-3).join(' | '));
  assert.throws(() => KT.shopperTexts('t("تنها", "")'), /خوانده نشد/);
});

test('جملهٔ کاملِ دری با {} (کلید‌به‌کلید): مقدار و {جای‌خالی} بایت‌به‌بایت سرِ جای خود', () => {
  //  همان PHRASES ِ کاتلین: هر {} یک مقدارِ asIs است، به همان ترتیب
  assert.equal(D.of(`PIN درست نیست — ${p('2')} تلاش مانده`), 'PIN درست نیست — 2 بار دیگر می‌توانید کوشش کنید');
  assert.equal(D.of(`شلوغم ${p('گوشی انبار')} دقیقه`), 'مصروفم گوشی انبار دقیقه', 'مقدار هرگز دری نمی‌شود');
  assert.equal(D.shape(`اعتبار تا ${p('1405/08/20')}`), 'اعتبار تا {}');
  //  قالبِ وب: {نام} همان {} است و پس از لایه پر می‌شود
  assert.equal(D.t('شلوغ — {m} دقیقهٔ دیگر'), 'مصروف — {m} دقیقهٔ دیگر');
  assert.equal(fill(D.t('اعتبار تا {d}'), { d: '۱۴۰۵/۰۸/۲۰' }), 'میعاد تا ۱۴۰۵/۰۸/۲۰');
  //  بی نشانه (فارسیِ خامِ پرشده) کلیدِ {} نمی‌خورد
  assert.equal(D.of('PIN درست نیست — 2 تلاش مانده'), 'PIN درست نیست — 2 تلاش مانده');
  for (const [fa, dari] of Object.entries(D.DATA.phrases)) {
    assert.equal(fa.split('{}').length, dari.split('{}').length, `شمارِ {} در «${fa}»`);
    assert.notEqual(fa, dari, fa);
  }
});

test('سازنده کلیدِ تکراریِ PHRASES را نمی‌پذیرد و جملهٔ دوسطری را می‌خواند', () => {
  const src = fs.readFileSync(W.KT, 'utf8');
  const dup = src.replace('    // </dari-phrases>', '    "اعتبار تا {}" to "تا {}",\n    // </dari-phrases>');
  assert.throws(() => W.build(dup), /تکراری/);
  const two = W.build();
  assert.ok(Object.keys(two.phrases).some((k) => k.startsWith('مشتری منتظر است؟')), 'جملهٔ دوسطری خوانده نشد');
});

test('هر واژه و جملهٔ دری در فهرستِ بازبینیِ صاحب‌کار (docs/DARI-REVIEW-fa.md) هست', () => {
  const doc = fs.readFileSync(path.join(__dirname, '..', '..', 'docs', 'DARI-REVIEW-fa.md'), 'utf8');
  const cell = (s) => s.replace(/\|/g, '\\|');
  const missing = [];
  for (const w of D.DATA.words) if (!doc.includes(`| ${cell(w.fa)} | ${cell(w.dari)} |`)) missing.push(`${w.fa} ⇐ ${w.dari}`);
  for (const [fa, dari] of Object.entries(D.DATA.phrases)) if (!doc.includes(`| ${cell(fa)} | ${cell(dari)} |`)) missing.push(`${fa} ⇐ ${dari}`);
  assert.deepEqual(missing, [], 'ردیفِ «فارسی | دری» در فهرستِ بازبینی نیست');
  assert.match(doc, /نیاز به تأییدِ صاحب‌کار/);
});

test('مرورگر: dari.js بی module خودش را روی VILL3N.tx می‌گذارد و ES5 است', () => {
  const src = read('kit/dari.js');
  const ctx = { VILL3N: {} };
  vm.runInNewContext(src, ctx);
  assert.equal(typeof ctx.VILL3N.tx, 'function');
  assert.equal(ctx.VILL3N.tx('شمارهٔ موبایل', 'phone.l'), 'شمارهٔ مبایل');
  assert.equal(ctx.VILL3N.tx('هزینهٔ سبدِ کوچک {fee}', 'zone.small'), 'هزینهٔ سبدِ کوچک {fee}');
  //  مانندِ کیت: گوشیِ کهنه هم باید بخواندش (بی let/const/arrow/template/class)
  const code = src.split('\n').filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l)).join('\n');
  assert.doesNotMatch(code, /\b(let|const|class)\s|=>|`/);
});

test('بارِ نخست زیرِ سقف (storefront-budget)؛ لایه و فهرست فقط با دری بار می‌شوند', () => {
  const first = ['store/store.js', 'kit/kit.js', 'kit/boot.js'].reduce((s, f) => s + fs.statSync(path.join(PUB, f)).size, 0);
  assert.ok(first < FIRST_LOAD, `بارِ نخست ${first} بایت`);
  for (const f of ['store/index.html', 'market/index.html']) assert.doesNotMatch(read(f), /dari\.js/, `${f} نباید dari.js را بخواهد`);
  for (const f of ['kit/boot.js', 'store/store.js']) assert.doesNotMatch(read(f), /'[^']*dari\.js'/, `${f} نباید dari.js را بخواهد`);
  const kit = read('kit/kit.js');
  assert.match(kit, /LANG == 'prs' && K\.js\('\/kit\/dari\.js'\)/, 'کیت فقط برای دری لایه را می‌خواهد');
  assert.doesNotMatch(kit, /کمپیوتر|گدام|فیصد|مبایل/, 'هیچ واژهٔ فهرست در بارِ نخست');
  //  دری در گزینش‌گر میانِ فارسی و English (پنجرهٔ تنظیمات، kit/settings.js ِ دیربار با زدنِ دکمه)؛ lang/dir ِ آن در boot
  const settings = read('kit/settings.js');
  assert.match(settings, /\[\['fa', 'فارسی'\], \['prs', 'دری'\], \['en', 'English'\], \['ps', 'پښتو'\]\]/);
  assert.doesNotMatch(settings, /کمپیوتر|گدام|فیصد|مبایل/, 'هیچ واژهٔ فهرست در پنجرهٔ تنظیمات');
  assert.match(kit, /K\.js\('\/kit\/settings\.js'\)/, 'کیت پنجرهٔ تنظیمات را فقط با زدنِ دکمه می‌خواهد');
  for (const f of ['store/index.html', 'market/index.html']) assert.doesNotMatch(read(f), /settings\.js|order-kit\.js/, `${f}: پرونده‌های دیربارِ کیت در بارِ نخست نیستند`);
  const boot = read('kit/boot.js');
  assert.match(boot, /prs: 'rtl'/);
  assert.match(boot, /lang=\(fa\|prs\|en\|ps\)/);
});
