'use strict';
/**
 * صفحه‌های عمومیِ حقوقی — `GET /legal/privacy` و `GET /legal/terms` (‎`?lang=fa|prs|en|ps`‎، پیش‌فرض fa)؛ `/legal/` ⇐ حریمِ خصوصی.
 *
 * ── چرا ─────────────────────────────────────────────────────────────
 * Google Play و App Store برای فهرستِ برنامه یک نشانیِ عمومیِ «سیاستِ حریمِ خصوصی» می‌خواهند. این نشانی پیش‌تر صفحهٔ
 * GitHub Pages ِ مخزن بود و نامِ مخزن را به هر کاربر و مهاجمی نشان می‌داد. حالا همان متن از خودِ سرور و روی دامنهٔ خودِ
 * صاحب‌کار داده می‌شود؛ درونِ برنامه‌ها همین متن بی اینترنت باز می‌شود (`core/.../legal/Legal.kt`).
 *
 * ── قرارداد ──────────────────────────────────────────────────────────
 *   • عمومی، بی نشست و بی کوکی؛ فقط GET/HEAD. سقفِ نرخ همان سطلِ عمومیِ IP ِ همهٔ درها (`app.js` ‹general›).
 *   • متن فقط از `lib/legal-texts.json` (رونوشتِ منبعِ یکتای `core/legal/legal-texts.json`)؛ هیچ داده‌ای از پایگاه، پیکربندی،
 *     نامِ میزبان، نسخهٔ سرور یا پیوندِ بیرونی در صفحه نیست — تنها پیوندها مسیرهای نسبیِ همین سرورند (`/portal/…`، `/legal/…`).
 *   • کش‌پذیر: `Cache-Control: public, max-age=3600` و ETag ِ محتوا (۳۰۴). صفحه برای هر (سند، زبان) یک بار ساخته می‌شود.
 *   • CSP ِ سخت‌تر از ویترین: نه اسکریپت، نه قاب، نه فرم؛ فقط همان style ِ درون‌خطی با hash و قلمِ `/kit/fonts`.
 *   • دری (`prs`) همان فارسی از لایهٔ واژهٔ وب (`public/kit/dari.js`، ساختهٔ `DariText.kt`) است، مثلِ برنامه‌ها.
 */
const express = require('express');
const crypto = require('node:crypto');
const legal = require('../lib/legal-texts.json');
const { escapeHtml } = require('../lib/mail-templates');
const dari = require('../../public/kit/dari.js');

const router = express.Router();

const DOCS = ['privacy', 'terms'];
const LANGS = ['fa', 'prs', 'en', 'ps'];
const NATIVE = { fa: 'فارسی', prs: 'دری', en: 'English', ps: 'پښتو' };
const HTML_LANG = { fa: 'fa', prs: 'fa-AF', en: 'en', ps: 'ps' };

const STYLE = [
  "@font-face{font-family:Vazirmatn;src:url(/kit/fonts/vazirmatn-regular.woff2) format('woff2');font-weight:400;font-display:swap}",
  "@font-face{font-family:Vazirmatn;src:url(/kit/fonts/vazirmatn-bold.woff2) format('woff2');font-weight:700;font-display:swap}",
  ':root{color-scheme:light dark;--bg:#f3f6fa;--card:#fff;--text:#14212b;--muted:#4f5d68;--line:#dfe5eb;--accent:#0f766e}',
  '@media (prefers-color-scheme:dark){:root{--bg:#0e1418;--card:#162026;--text:#e7eef2;--muted:#a9b6be;--line:#26343c;--accent:#6fd6c8}}',
  '*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:Vazirmatn,Tahoma,system-ui,sans-serif;line-height:1.85}',
  'main{max-width:760px;margin:0 auto;padding:24px 16px 48px}h1{font-size:24px;line-height:1.4;margin:8px 0 4px}h2{font-size:17px;margin:4px 0 8px}',
  '.lead{color:var(--muted);margin:0 0 20px}section{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:10px 18px 12px;margin:0 0 12px}',
  'li{margin:4px 0}a{color:var(--accent)}nav{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:14px}nav [aria-current]{font-weight:700;color:var(--text);text-decoration:none}',
  '.foot{margin-top:20px;font-size:14px}',
].join('');
const CSP = `default-src 'none'; style-src 'sha256-${crypto.createHash('sha256').update(STYLE).digest('base64')}'; font-src 'self'; ` +
  "img-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

const localDigits = (s) => String(s).replace(/[0-9]/g, (d) => '۰۱۲۳۴۵۶۷۸۹'[d]);
const MARK = /\*\*([^*\n]+)\*\*|\[([^\]\n]+)\]\((\/[A-Za-z0-9/_#.?=&-]*)\)/g;

/** متنِ منبع ⇒ HTML: هر تکه گریزانده؛ فقط `**پررنگ**` و `[نوشته](/مسیرِ-نسبی)` (همان قاعدهٔ `Legal.spans`) */
function inline(s) {
  let out = '';
  let at = 0;
  for (const m of s.matchAll(MARK)) {
    out += escapeHtml(s.slice(at, m.index));
    out += m[1] != null ? `<b>${escapeHtml(m[1])}</b>` : `<a href="${escapeHtml(m[3])}">${escapeHtml(m[2])}</a>`;
    at = m.index + m[0].length;
  }
  return out + escapeHtml(s.slice(at));
}

/** یک سند به یک زبان؛ دری = فارسی از لایهٔ واژه */
function textOf(doc, lang) {
  const base = legal[doc][lang === 'prs' ? 'fa' : lang];
  const labels = legal.labels[lang === 'prs' ? 'fa' : lang];
  const f = lang === 'prs' ? (s) => dari.of(s) : (s) => s;
  return {
    short: f(base.short), title: f(base.title), lead: f(base.lead || ''), version: f(labels.version), language: f(labels.language),
    sections: base.sections.map((s) => ({ id: s.id, title: f(s.title), paras: s.paras.map(f), items: s.items.map(f) })),
  };
}

function render(doc, lang, base) {
  const t = textOf(doc, lang);
  const rtl = lang !== 'en';
  const num = (n) => (rtl ? localDigits(n) : String(n));
  const sub = t.lead || t.version.replace('{v}', num(legal[doc].version));
  const other = DOCS.find((d) => d !== doc);
  const q = (l) => (l === 'fa' ? '' : `?lang=${l}`);
  const langs = LANGS.map((l) => (l === lang
    ? `<span aria-current="page" lang="${HTML_LANG[l]}">${NATIVE[l]}</span>`
    : `<a href="${base}/${doc}${q(l)}" lang="${HTML_LANG[l]}" hreflang="${HTML_LANG[l]}">${NATIVE[l]}</a>`)).join('');
  const alternates = LANGS.map((l) => `<link rel="alternate" hreflang="${HTML_LANG[l]}" href="${base}/${doc}${q(l)}">`).join('');
  const sections = t.sections.map((s, i) => `<section id="${escapeHtml(s.id)}"><h2>${doc === 'terms' ? `${num(i + 1)}. ` : ''}${inline(s.title)}</h2>` +
    s.paras.map((p) => `<p>${inline(p)}</p>`).join('') + (s.items.length ? `<ul>${s.items.map((x) => `<li>${inline(x)}</li>`).join('')}</ul>` : '') +
    '</section>').join('\n');
  return '<!doctype html>\n' +
    `<html lang="${HTML_LANG[lang]}" dir="${rtl ? 'rtl' : 'ltr'}"><head><meta charset="utf-8">` +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    `<title>${inline(t.title)}</title><meta name="description" content="${escapeHtml(t.short)}">${alternates}<style>${STYLE}</style></head>\n` +
    `<body><main><nav aria-label="${escapeHtml(t.language)}">${langs}</nav>\n<h1>${inline(t.title)}</h1><p class="lead">${escapeHtml(sub)}</p>\n` +
    `${sections}\n<p class="foot"><a href="${base}/${other}${q(lang)}">${escapeHtml(textOf(other, lang).short)}</a></p></main></body></html>\n`;
}

/** هر (سند، زبان) یک بار، با ETag ِ محتوا؛ پایه همیشه `/legal` است (مسیرِ سوارشده در `app.js`) */
const pages = new Map();
const page = (doc, lang, base) => {
  const k = `${base}|${doc}|${lang}`;
  if (!pages.has(k)) {
    const html = render(doc, lang, base);
    pages.set(k, { html, etag: `"${crypto.createHash('sha256').update(html).digest('base64url').slice(0, 27)}"` });
  }
  return pages.get(k);
};

router.get('/', (req, res) => res.redirect(302, `${req.baseUrl}/privacy`));

router.get('/:doc', (req, res, next) => {
  const doc = req.params.doc;
  if (!DOCS.includes(doc)) return next();
  const lang = LANGS.includes(req.query.lang) ? req.query.lang : 'fa';
  res.set('Content-Security-Policy', CSP);
  res.set('Cache-Control', 'public, max-age=3600');
  res.vary('Accept-Encoding');
  const p = page(doc, lang, req.baseUrl);
  res.set('ETag', p.etag);
  //  `If-None-Match` می‌تواند چند ETag (با ویرگول) یا `*` باشد
  const tags = String(req.get('if-none-match') || '').split(',').map((t) => t.trim());   //  بی regex: سرآیندِ بیرونی (CodeQL ReDoS)
  if (tags.includes(p.etag) || tags.includes('*')) return res.status(304).end();
  res.type('html').send(p.html);
});

module.exports = router;
module.exports.render = render;
