'use strict';
/**
 * متنِ «حریمِ خصوصی» و «شرایط» — یک منبع (`core/legal/legal-texts.json`)، رونوشت‌های یکسان:
 *   ۱) `tools/legal/generate.mjs --check`: رونوشتِ سرور (`src/lib/legal-texts.json`) و کاتلینِ برنامه‌ها
 *      (`core/.../legal/LegalTexts{Fa,En,Ps}.kt`) همان منبع‌اند؛ ویرایشِ دستیِ رونوشت یا فراموش کردنِ ابزار ⇒ سرخ.
 *   ۲) قاعده‌های منبع (سه زبان هم‌ساختار، نشانه‌گذاریِ مجاز، بی پیوندِ بیرونی/github) واقعاً رد می‌کنند.
 *   ۳) قراردادِ `GET /api/auth/terms` و ثبت‌نام (`lib/terms.js`) همان فارسی و همان نسخهٔ «1» ِ پیشین است.
 *   ۴) رونوشتِ موقتِ GitHub Pages (`privacy.html`، تا عوض شدنِ نشانیِ فهرستِ فروشگاه‌ها) بند‌به‌بند همان فارسیِ منبع است،
 *      پس آزمون‌های رفتار‌به‌متنِ همان صفحه (`self-delete`، `privacy-erasure-text`، `backup-passphrase-warning`) منبع را هم
 *      می‌پوشانند. با پاک شدنِ `privacy.html` آن سه به `src/lib/legal-texts.json` برمی‌گردند (HANDOFF).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const ROOT = path.join(__dirname, '..', '..');
const gen = () => import(pathToFileURL(path.join(ROOT, 'tools', 'legal', 'generate.mjs')).href);
const src = () => JSON.parse(fs.readFileSync(path.join(ROOT, 'core', 'legal', 'legal-texts.json'), 'utf8'));

test('رونوشتِ سرور و کاتلینِ برنامه‌ها همان منبع‌اند (generate.mjs --check)', async () => {
  const g = await gen();
  const files = g.build();
  assert.ok(Object.keys(files).some((f) => f.endsWith('LegalTextsPs.kt')));
  assert.deepEqual(g.stale(files), [], 'node tools/legal/generate.mjs را بزنید');
  //  سرور همان بایت‌های منبع را می‌خواند
  assert.equal(fs.readFileSync(path.join(__dirname, '..', 'src', 'lib', 'legal-texts.json'), 'utf8'),
    fs.readFileSync(path.join(ROOT, 'core', 'legal', 'legal-texts.json'), 'utf8'));
});

test('قاعده‌های منبع: بخشِ کم، پیوندِ بیرونی، github و نشانه‌گذاریِ ناشناخته رد می‌شوند', async () => {
  const g = await gen();
  const bad = [
    (s) => { s.privacy.en.sections.pop(); },
    (s) => { s.terms.ps.sections[0].paras = []; },
    (s) => { s.privacy.fa.sections[0].items[0] += ' [GitHub](https://github.com/x)'; },
    (s) => { s.privacy.ps.sections[0].items[0] += ' github'; },
    (s) => { s.privacy.en.sections[0].items[0] = 'see [portal](//evil.example/)'; },
    (s) => { s.terms.en.sections[1].paras[0] = '<b>x</b> *y'; },
    (s) => { s.terms.en.title = 'شرایط'; },
    (s) => { s.privacy.ps.sections[2].id = 'other'; },
    (s) => { s.terms.version = ''; },
  ];
  for (const [i, f] of bad.entries()) {
    const s = src();
    f(s);
    assert.throws(() => g.validate(s), /legal-texts:/, `مورد ${i}`);
  }
  assert.doesNotThrow(() => g.validate(src()));
});

test('قراردادِ شرایطِ ثبت‌نام همان است: نسخهٔ «1»، همان عنوان و نه بندِ فارسی', () => {
  const terms = require('../src/lib/terms');
  const s = src();
  assert.equal(terms.VERSION, '1');
  assert.equal(terms.VERSION, s.terms.version);
  assert.equal(terms.TITLE, 'شرایط و ضوابط استفاده از برنامه');
  assert.equal(terms.SECTIONS.length, 9);
  assert.deepEqual(terms.SECTIONS.map((x) => x.title), s.terms.fa.sections.map((x) => x.title));
  assert.ok(terms.SECTIONS[4].body.startsWith('خدمات پشتیبانی برنامه به‌صورت ۲۴ ساعته'));
  assert.ok(terms.plainText().startsWith('شرایط و ضوابط استفاده از برنامه\n\n- حفظ و محرمانگی اطلاعات:'));
});

test('رونوشتِ موقتِ GitHub Pages (privacy.html) بند‌به‌بند همان فارسیِ منبع است و نشانِ «پاک می‌شود» دارد', () => {
  const html = fs.readFileSync(path.join(ROOT, 'privacy.html'), 'utf8');
  assert.match(html, /رونوشتِ موقتِ GitHub Pages/);
  const conv = (s) => s.replace(/\s+/g, ' ').trim().replace(/<b>([^<]*)<\/b>/g, '**$1**')
    .replace(/<a href="https:\/\/[^"/]+(\/portal\/#delete-account)">([^<]*)<\/a>/g, '[$2]($1)');
  const main = html.slice(html.indexOf('<main>'), html.indexOf('</main>'));
  const page = [...main.matchAll(/<(h2|li|p)(?: [^>]*)?>([\s\S]*?)<\/\1>/g)].map((m) => conv(m[2])).slice(1); //  بی خطِ «آخرین بازبینی»
  const fa = src().privacy.fa;
  const want = fa.sections.flatMap((s) => [s.title, ...s.paras, ...s.items]);
  assert.deepEqual(page, want);
  assert.ok(main.includes(`<h1>${fa.title}</h1>`));
  assert.ok(main.includes(fa.lead));
});

test('نامِ پیوندهای ویترین و بازارچه (`set.privacy`، `set.terms`) همان نامِ کوتاهِ منبع است', () => {
  const s = src();
  for (const l of ['fa', 'en', 'ps']) {
    const dict = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'public', 'kit', 'i18n', `${l}.json`), 'utf8'));
    assert.equal(dict['set.privacy'], s.privacy[l].short, l);
    assert.equal(dict['set.terms'], s.terms[l].short, l);
  }
  const js = fs.readFileSync(path.join(__dirname, '..', 'public', 'kit', 'settings.js'), 'utf8');
  assert.match(js, /'\/legal\/privacy' \+ lq/);
  assert.match(js, /'\/legal\/terms' \+ lq/);
});
