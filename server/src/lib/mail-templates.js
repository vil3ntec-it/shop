'use strict';
/**
 * قالب‌های ایمیلِ هر برنامه — همان دو فایلی که صاحبِ سامانه داد.
 *
 * ── قاعده (۱۴۰۵/۰۷/۱۳) ────────────────────────────────────────────────
 *   «دوتا سایتِ ایمیلِ کد برات دادم، برای هر برنامه همون مدل باشه… و این دو
 *   سایت رو اصلاً ظاهر و غیره رو تغییر نمی‌دی؛ اجازهٔ این کار رو نداری —
 *   ظاهر یا تم‌ها یا نوشته‌ها.»
 *
 *   پس `mail-templates/pump-code.html` و `shop-code.html` **عیناً** همان
 *   فایل‌هایند (هشِ همان فایل‌ها) و این ماژول به آن‌ها فقط دو کار می‌کند:
 *
 *     • کدِ ایمیل: شش رقمِ نمونه (`482916`) جای خودش عوض می‌شود. هیچ خط،
 *       رنگ، قلم یا نوشته‌ای دست نمی‌خورد.
 *     • پیام (نه کد): همان مدل، ولی به‌جای کد یک پیام — «اگه من می‌خواستم
 *       برای کسی هم ایمیل بزنم باز هم همین مدل باشه، اما با تفاوت که پیام
 *       است نه کد». کارتِ کد (عنوان، توضیح، شش رقم و دکمهٔ کپی) جایش عنوان
 *       و متنِ پیام می‌نشیند؛ بقیهٔ صفحه (تشکر، مشخصات، «چرا ویلن»، یادداشت،
 *       بنر، پانوشت) همان است.
 *
 * ⛔ **قالب‌ها با `replace`ی هدفمند و یک‌باره** ویرایش می‌شوند، نه با یک
 *    موتورِ قالب: هر جاگیریِ اضافه یعنی یک جای دیگر از فایلِ صاحبِ سامانه
 *    که «عوض شده». آزمون (`mail-templates.test.js`) همین را می‌سنجد: خروجیِ
 *    کد با خودِ فایل فقط در همان شش رقم فرق دارد.
 *
 * ⚠️ اگر روزی فایلی نبود یا نشانه‌اش پیدا نشد، `null` برمی‌گردد و
 *    فرستنده به کارتِ سادهٔ همیشگی (`mailer.card`) برمی‌گردد — ایمیل
 *    نرفتن بدتر از ایمیلِ ساده است.
 *
 * ⚠️ فایل‌ها `<script>` و `<link>`ِ قلم دارند. بیشترِ برنامه‌های ایمیل
 *    اسکریپت را دور می‌ریزند و قلمِ بیرونی را نمی‌گیرند؛ این عمدی است و
 *    برداشتنش «تغییرِ فایل» بود. دکمهٔ کپی در آن‌ها فقط دیده می‌شود.
 */
const fs = require('node:fs');
const path = require('node:path');

const DIR = path.join(__dirname, 'mail-templates');
const FILES = Object.freeze({ pump: 'pump-code.html', shop: 'shop-code.html' });

/** شش رقمِ نمونهٔ داخلِ هر دو فایل. */
const SAMPLE = '482916';

const cache = new Map();

function appOf(app) {
  return String(app || '').toLowerCase() === 'pump' ? 'pump' : 'shop';
}

/** متنِ خامِ قالبِ یک برنامه — یک بار از دیسک، بعد از حافظه. */
function raw(app) {
  const key = appOf(app);
  if (cache.has(key)) return cache.get(key);
  let text = null;
  try { text = fs.readFileSync(path.join(DIR, FILES[key]), 'utf8'); } catch { text = null; }
  cache.set(key, text);
  return text;
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** متنِ چندخطی ⇒ پاراگراف‌های امن؛ خطِ خالی پاراگرافِ تازه است. */
function paragraphs(body) {
  return String(body ?? '').trim().split(/\n{2,}/).filter(Boolean)
    .map((p) => `<p>${escapeHtml(p).replace(/\n/g, '<br>')}</p>`).join('\n');
}

/**
 * خطِ پیش‌نمایش — همان یک خطی که گوشی در **اعلان** و فهرستِ ایمیل‌ها نشان
 * می‌دهد.
 *
 * ⛔ خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۳، با عکسِ اعلانِ جیمیل): «کد ده جا نوشته
 * شده و یارو اصلاً لازم نیست بیاید توی ایمیل ببیند؛ توی اعلانات کدی نباشد و
 * روی ایمیل که کلیک کند، کد آن‌جا نشان داده شود.» قالب‌ها خطِ پیش‌نمایش
 * ندارند، پس جیمیل از نخستین نوشته‌ها یکی می‌سازد و به کد می‌رسد.
 *
 * ⚠️ **نامرئی است** و ظاهر و نوشته‌های قالب را عوض نمی‌کند: یک `div`ِ پنهان
 * در همان ابتدای فایل، و نویسه‌های پُرکنندهٔ استاندارد تا جیمیل از آن‌ها
 * جلوتر نرود.
 */
const PREVIEW = Object.freeze({
  code: 'کدِ شما آماده است — برای دیدنش همین ایمیل را باز کنید.',
});
const FILLER = '&#847;&zwnj;&nbsp;'.repeat(90);
function preheader(line) {
  return '<div style="display:none;max-height:0;max-width:0;overflow:hidden;opacity:0;'
    + 'mso-hide:all;font-size:1px;line-height:1px;color:transparent">'
    + `${escapeHtml(line)}${FILLER}</div>\n`;
}

/**
 * عنوانِ ایمیل — همان `<title>`ِ فایلِ صاحبِ سامانه («کد ورود ویلن»،
 * «کد ورود VILL3N Shop»). ⛔ **کد در عنوان نمی‌آید**: عنوان هم در اعلان دیده
 * می‌شود.
 */
function titleOf(app) {
  const m = /<title>([^<]*)<\/title>/.exec(raw(app) || '');
  return m ? m[1].trim() : '';
}

/** نامِ برنامه برای «فرستنده» — همان عنوانِ قالب بی «کد ورود» («ویلن»، «VILL3N Shop»). */
function brandOf(app) {
  return titleOf(app).replace(/^کد\s*ورود\s*/, '').trim();
}

/** یک جاگیریِ **دقیقاً یک‌باره** — نبودنش یعنی قالب چیزِ دیگری است، پس `null`. */
function once(text, from, to) {
  if (text === null) return null;
  const i = text.indexOf(from);
  if (i < 0 || text.indexOf(from, i + 1) >= 0) return null;
  return text.slice(0, i) + to + text.slice(i + from.length);
}

/** بلوکی که با `open` شروع می‌شود تا نخستین `close` بعدش — برداشته می‌شود. */
function cut(text, open, close) {
  if (text === null) return null;
  const i = text.indexOf(open);
  if (i < 0) return null;
  const j = text.indexOf(close, i);
  if (j < 0) return null;
  return text.slice(0, i) + text.slice(j + close.length);
}

/**
 * ایمیلِ **کد** — همان فایل، با شش رقمِ واقعی.
 *
 * `title`/`lead` فقط برای ایمیل‌هایی که کدِ ورود نیستند (کدِ اشتراک، کدِ
 * بازیابیِ رمز) و می‌خواهند همان مدل را با عنوانِ درست بفرستند. برای کدِ
 * ورود هر دو خالی می‌مانند و **هیچ نوشته‌ای عوض نمی‌شود**.
 */
function codeHtml({ app, code, title = '', lead = '' } = {}) {
  const digits = String(code ?? '').replace(/\D/g, '');
  if (digits.length !== 6) return null;
  const key = appOf(app);
  let html = raw(key);
  if (html === null) return null;

  if (key === 'pump') {
    html = once(html, `<div class="code" id="code" dir="ltr">${SAMPLE}</div>`,
      `<div class="code" id="code" dir="ltr">${digits}</div>`);
    if (title) html = once(html, '<h2>کد ورود شما</h2>', `<h2>${escapeHtml(title)}</h2>`);
    if (lead) {
      html = once(html,
        '<p>سلام. برای ورود به حساب‌تان در ویلن، این کد شش‌رقمی را در برنامه وارد کنید.</p>',
        `<p>${escapeHtml(lead)}</p>`);
    }
  } else {
    const spans = SAMPLE.split('').map((d) => `<span>${d}</span>`).join('');
    html = once(html, spans, digits.split('').map((d) => `<span>${d}</span>`).join(''));
    if (title) html = once(html, '<h3>کد ورود شما</h3>', `<h3>${escapeHtml(title)}</h3>`);
    if (lead) {
      html = once(html,
        '<p class="sub">این کد برای ۱۰ دقیقه معتبر است و فقط یک بار قابل استفاده می‌باشد.</p>',
        `<p class="sub">${escapeHtml(lead)}</p>`);
    }
  }
  return html === null ? null : preheader(PREVIEW.code) + html;
}

/**
 * ایمیلِ **پیام** — همان مدل، ولی به‌جای کارتِ کد: عنوان و متن.
 */
function messageHtml({ app, title, body } = {}) {
  const key = appOf(app);
  let html = raw(key);
  if (html === null) return null;
  const h = escapeHtml(title || '');
  const p = paragraphs(body);

  if (key === 'pump') {
    html = once(html, '<h2>کد ورود شما</h2>', `<h2>${h}</h2>`);
    html = once(html,
      '<p>سلام. برای ورود به حساب‌تان در ویلن، این کد شش‌رقمی را در برنامه وارد کنید.</p>',
      p);
    html = cut(html, '<div class="code-box">', '</div>\n\n');
    html = cut(html, '<div class="meta">', '</div>\n    </div>\n');
    html = cut(html, '<p class="warn">', '</p>\n');
  } else {
    html = once(html, '<h3>کد ورود شما</h3>', `<h3>${h}</h3>`);
    html = once(html,
      '<p class="sub">این کد برای ۱۰ دقیقه معتبر است و فقط یک بار قابل استفاده می‌باشد.</p>',
      `<div class="sub" style="text-align:right">${p}</div>`);
    html = cut(html, '<div class="digits" id="digits" dir="ltr">', '</div>\n');
    html = cut(html, '<button type="button" class="copy" id="copyBtn">', '</button>\n');
  }
  if (html === null) return null;
  //  دکمهٔ کپی رفته، پس اسکریپتش هم بی‌کار است
  html = cut(html, '<script>', '</script>');
  if (html === null) return null;
  //  پیش‌نمایشِ پیام همان عنوانِ خودِ پیام است
  return preheader(title || '') + html;
}

/** قالبِ این برنامه هست؟ (برای سنجه‌ها و صفحهٔ مدیر) */
function available(app) {
  return raw(app) !== null;
}

module.exports = { codeHtml, messageHtml, available, appOf, titleOf, brandOf, preheader, PREVIEW, SAMPLE, FILES, DIR };
