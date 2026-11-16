'use strict';
/**
 * `GET /legal/privacy` و `GET /legal/terms` (`routes/legal-pages.js`) — نشانیِ عمومیِ «سیاستِ حریمِ خصوصی» ِ فهرستِ
 * Google Play و App Store، روی دامنهٔ خودِ سرور (نه GitHub Pages):
 *   • عمومی: بی نشست، بی کوکی، با توکنِ ساختگی هم همان پاسخ (درِ تازه به هیچ نقش و دکانی بند نیست؛ ۴۰۳ ندارد چون چیزی را
 *     نمی‌دهد که مالِ کسی باشد)؛
 *   • `text/html; charset=utf-8`، کش‌پذیر (`public, max-age`) با ETag و ۳۰۴، و CSP ِ بی اسکریپت؛
 *   • متن همان منبعِ یکتا (`lib/legal-texts.json`) در fa/prs/en/ps، با همهٔ بخش‌ها و نشانه‌های `#delete-account`؛
 *   • هیچ نامِ میزبانِ درونی، نشانیِ بیرونی، `github` یا جزئیاتِ سرور در صفحه نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const legal = require('../src/lib/legal-texts.json');

test.before(() => h.start());
test.after(() => h.stop());

const get = async (path, headers = {}) => {
  const res = await fetch(`${h.base()}${path}`, { headers, redirect: 'manual' });
  return { status: res.status, headers: res.headers, text: await res.text() };
};

/** هر نامِ میزبان یا نشانیِ بیرونی؛ پیوندِ نسبی (`/portal/…`) مجاز است */
const LEAKS = [/github/i, /https?:\/\//i, /\/\/[a-z0-9.-]+\.[a-z]{2,}/i, /vill3n/i, /localhost/i, /127\.0\.0\.1/, /api\.[a-z0-9-]+\./i,
  /x-powered-by/i, /NODE_ENV|process\.env|\/home\/|\/app\/|\.js:\d+/];

test('حریمِ خصوصی و شرایط عمومی‌اند: HTML، بی نشست و بی کوکی، کش‌پذیر، با CSP ِ بی اسکریپت', async () => {
  for (const doc of ['privacy', 'terms']) {
    const r = await get(`/legal/${doc}`);
    assert.equal(r.status, 200, doc);
    assert.equal(r.headers.get('content-type'), 'text/html; charset=utf-8');
    assert.match(r.headers.get('cache-control'), /^public, max-age=\d+$/);
    assert.equal(r.headers.get('set-cookie'), null);
    assert.equal(r.headers.get('x-powered-by'), null);
    const csp = r.headers.get('content-security-policy');
    assert.match(csp, /default-src 'none'/);
    assert.doesNotMatch(csp, /script-src|unsafe-inline/);
    assert.match(csp, /frame-ancestors 'none'/);
    assert.doesNotMatch(r.text, /<script/i);
    //  توکنِ ساختگی یا بی‌ربط هیچ فرقی نمی‌کند (درِ عمومی نشست را نمی‌خواند)
    const withToken = await get(`/legal/${doc}`, { Authorization: 'Bearer not-a-real-token', 'X-Store-Session': 'x' });
    assert.equal(withToken.status, 200);
    assert.equal(withToken.text, r.text);
  }
});

test('ETag و ۳۰۴؛ HEAD بی بدنه', async () => {
  const r = await get('/legal/privacy');
  const etag = r.headers.get('etag');
  assert.ok(etag);
  const again = await get('/legal/privacy', { 'If-None-Match': etag });
  assert.equal(again.status, 304);
  const head = await fetch(`${h.base()}/legal/terms`, { method: 'HEAD' });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), '');
});

test('هر چهار زبان متنِ همان منبع را با جهت و زبانِ درست دارند؛ زبانِ ناشناخته ⇒ فارسی', async () => {
  const want = { fa: ['fa', 'rtl', legal.privacy.fa.title], en: ['en', 'ltr', 'Privacy policy of “Daily Shop”'], ps: ['ps', 'rtl', legal.privacy.ps.title] };
  for (const [lang, [code, dir, title]] of Object.entries(want)) {
    const r = await get(`/legal/privacy${lang === 'fa' ? '' : `?lang=${lang}`}`);
    assert.match(r.text, new RegExp(`<html lang="${code}" dir="${dir}">`));
    assert.ok(r.text.includes(`<h1>${title}</h1>`), lang);
    for (const s of legal.privacy[lang].sections) assert.ok(r.text.includes(`<section id="${s.id}">`), `${lang} ${s.id}`);
  }
  const prs = await get('/legal/terms?lang=prs');
  assert.match(prs.text, /<html lang="fa-AF" dir="rtl">/);
  assert.ok(prs.text.includes('معلومات'), 'دری از لایهٔ واژه («اطلاعات» ⇐ «معلومات»)');
  assert.equal((await get('/legal/privacy?lang=de')).text, (await get('/legal/privacy')).text);
  assert.equal((await get('/legal/privacy?lang=en&lang=ps')).text, (await get('/legal/privacy')).text);
});

test('شرایط همان نه بند و نسخهٔ قراردادِ ثبت‌نام است؛ حذفِ حساب از وب پیوندِ نسبیِ همین سرور است', async () => {
  const terms = await get('/legal/terms?lang=en');
  assert.equal(legal.terms.en.sections.length, 9);
  for (const s of legal.terms.en.sections) assert.ok(terms.text.includes(s.title), s.id);
  assert.ok(terms.text.includes('Version 1'));
  assert.ok((await get('/legal/terms')).text.includes('نسخهٔ ۱'));
  const privacy = await get('/legal/privacy');
  assert.ok(privacy.text.includes('<section id="delete-account">'));
  assert.ok(privacy.text.includes('<a href="/portal/#delete-account">'));
  assert.ok(privacy.text.includes('<b>حساب:</b>'), 'پررنگِ منبع ⇒ <b>');
  assert.doesNotMatch(privacy.text, /\*\*/);
});

test('هیچ نامِ میزبان، نشانیِ بیرونی، github یا جزئیاتِ سرور در هیچ صفحه‌ای نیست', async () => {
  for (const doc of ['privacy', 'terms']) for (const lang of ['fa', 'prs', 'en', 'ps']) {
    const r = await get(`/legal/${doc}?lang=${lang}`);
    for (const re of LEAKS) assert.doesNotMatch(r.text, re, `${doc} ${lang}: ${re}`);
    //  هر href فقط مسیرِ نسبیِ همین سرور
    for (const [, href] of r.text.matchAll(/href="([^"]*)"/g)) assert.match(href, /^\/(legal|portal)\//, href);
  }
});

test('سندِ ناشناخته همان ۴۰۴ ِ مسیرِ ناشناخته است؛ /legal/ ⇐ حریمِ خصوصی', async () => {
  assert.equal((await get('/legal/github')).status, 404);
  assert.equal((await get('/legal/privacy/x')).status, 404);
  const root = await get('/legal/');
  assert.equal(root.status, 302);
  assert.equal(root.headers.get('location'), '/legal/privacy');
});
