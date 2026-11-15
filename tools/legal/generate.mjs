#!/usr/bin/env node
/**
 * متنِ «حریمِ خصوصی» و «شرایط و ضوابط» — یک منبع، چند رونوشتِ ساخته‌شده.
 *
 *   node tools/legal/generate.mjs           ⇐ رونوشت‌ها را از منبع می‌نویسد
 *   node tools/legal/generate.mjs --check   ⇐ فقط می‌سنجد (رونوشتِ کهنه یا دست‌خورده ⇒ خروجِ ۱)
 *
 * منبعِ یکتا: `core/legal/legal-texts.json` (fa، en، ps؛ دری در برنامه‌ها و سرور از لایهٔ واژهٔ `DariText`).
 * رونوشت‌ها (دستی ویرایش نشوند):
 *   • `server/src/lib/legal-texts.json` — همان بایت‌ها؛ سرور (`GET /legal/*` و `GET /api/auth/terms`) فقط پوشهٔ `server/` را
 *     در image دارد، پس منبع را نمی‌بیند.
 *   • `core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/legal/LegalTexts{Fa,En,Ps}.kt` — برنامهٔ دکان (اندروید، کامپیوتر،
 *     آیفون) و برنامهٔ خرید (اندروید، آیفون) متن را درونِ خودشان دارند و صفحه‌شان بی اینترنت باز می‌شود.
 * آزمونِ `server/test/legal-texts.test.js` همین `--check` را می‌زند، پس رونوشتِ جامانده CI را سرخ می‌کند.
 *
 * قاعده‌های منبع (این‌جا سنجیده می‌شوند، نه فقط در آزمون):
 *   • سه زبان با همان بخش‌ها (همان `id` و همان شمارِ بند و فهرست)؛ هیچ متنِ خالی؛ انگلیسی بی حرفِ فارسی/پشتو.
 *   • نشانه‌گذاری فقط `**پررنگ**` و `[نوشته](/مسیرِ-نسبی)`؛ پیوندِ بیرونی، نامِ میزبان، `http` و `github` ممنوع
 *     (صفحه روی دامنهٔ خودِ سرور است و چیزی از زیرساخت نشان نمی‌دهد).
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
export const SOURCE = 'core/legal/legal-texts.json';
export const SERVER_COPY = 'server/src/lib/legal-texts.json';
const KT_DIR = 'core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/legal';
export const LANGS = ['fa', 'en', 'ps'];
const DOCS = ['privacy', 'terms'];
const LETTER = /[؀-ۿ]/;

function fail(msg) { throw new Error(`legal-texts: ${msg}`); }

/** هر متن: فقط `**…**` و `[…](/…)`؛ بی نشانی بیرونی */
export function checkMarkup(s, where) {
  if (typeof s !== 'string' || !s.trim()) fail(`${where}: متنِ خالی`);
  if (/github/i.test(s)) fail(`${where}: «github» در متن`);
  if (/https?:|\/\/|www\./i.test(s)) fail(`${where}: نشانیِ بیرونی در متن (فقط مسیرِ نسبی، مثلِ /portal/)`);
  const rest = s.replace(/\[([^\]\n]+)\]\((\/[A-Za-z0-9/_#.?=&-]*)\)/g, '$1').replace(/\*\*([^*\n]+)\*\*/g, '$1');
  if (/[[\]*]/.test(rest)) fail(`${where}: نشانه‌گذاریِ ناشناخته (فقط **پررنگ** و [نوشته](/مسیر)): ${s}`);
}

export function validate(src) {
  for (const l of LANGS) {
    const lab = src.labels?.[l];
    if (!lab || !lab.version?.includes('{v}') || !lab.language || !lab.heading) fail(`labels.${l} ناقص`);
  }
  for (const d of DOCS) {
    const doc = src[d];
    if (!doc || typeof doc.version !== 'string' || !/^[0-9A-Za-z.-]{1,20}$/.test(doc.version)) fail(`${d}.version`);
    const base = doc.fa;
    for (const l of LANGS) {
      const x = doc[l];
      if (!x) fail(`${d}.${l} نیست`);
      for (const k of ['short', 'title']) checkMarkup(x[k], `${d}.${l}.${k}`);
      if (x.lead) checkMarkup(x.lead, `${d}.${l}.lead`);
      if (!!x.lead !== !!base.lead) fail(`${d}.${l}.lead با fa نمی‌خواند`);
      if (!Array.isArray(x.sections) || x.sections.length !== base.sections.length) fail(`${d}.${l}: شمارِ بخش‌ها با fa یکی نیست`);
      x.sections.forEach((s, i) => {
        const b = base.sections[i];
        const at = `${d}.${l}.sections[${i}]`;
        if (s.id !== b.id || !/^[a-z][a-z-]*$/.test(s.id)) fail(`${at}.id «${s.id}» ≠ «${b.id}»`);
        checkMarkup(s.title, `${at}.title`);
        if (s.paras.length !== b.paras.length || s.items.length !== b.items.length) fail(`${at}: شمارِ بند/فهرست با fa یکی نیست`);
        if (!s.paras.length && !s.items.length) fail(`${at}: بخشِ تهی`);
        [...s.paras, ...s.items].forEach((t, j) => checkMarkup(t, `${at}#${j}`));
      });
      if (l === 'en' && LETTER.test(JSON.stringify(x))) fail(`${d}.en حرفِ فارسی/پشتو دارد`);
    }
    if (new Set(base.sections.map((s) => s.id)).size !== base.sections.length) fail(`${d}: id ِ تکراری`);
  }
  return src;
}

/** رشتهٔ کاتلین با گریزِ `\`، `"` و `$` */
const kq = (s) => `"${s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$')}"`;
const klist = (xs, pad) => (xs.length ? `listOf(\n${xs.map((x) => `${pad}  ${kq(x)},`).join('\n')}\n${pad})` : 'emptyList()');
const NAME = { fa: 'Fa', en: 'En', ps: 'Ps' };

function kotlinDoc(kind, version, x) {
  const secs = x.sections.map((s) => `      LegalSection(\n        ${kq(s.id)}, ${kq(s.title)},\n        paras = ${klist(s.paras, '        ')},\n        items = ${klist(s.items, '        ')},\n      ),`).join('\n');
  return `LegalDoc(\n    LegalKind.${kind}, ${kq(version)},\n    short = ${kq(x.short)},\n    title = ${kq(x.title)},\n    lead = ${kq(x.lead || '')},\n    sections = listOf(\n${secs}\n    ),\n  )`;
}

export function kotlin(src, lang) {
  const head = `// ساخته‌شده به دستِ tools/legal/generate.mjs از ${SOURCE} — دستی ویرایش نکنید (منبع را ویرایش کنید و ابزار را بزنید).\n` +
    'package ir.vil3ntec.tohid.core.legal\n\n';
  const consts = lang === 'fa'
    ? `  /** نسخهٔ شرایط — هنگامِ پذیرش روی حساب ثبت می‌شود (\`POST /auth/register/complete\` ‹terms.version›) */\n  const val TERMS_VERSION: String = ${kq(src.terms.version)}\n  const val PRIVACY_VERSION: String = ${kq(src.privacy.version)}\n\n`
    : '';
  const lab = src.labels[lang];
  return `${head}internal object LegalTexts${NAME[lang]} : LegalTextSet {\n${consts}` +
    `  override val labels = LegalLabels(version = ${kq(lab.version)}, language = ${kq(lab.language)}, heading = ${kq(lab.heading)})\n\n` +
    `  override val privacy = ${kotlinDoc('PRIVACY', src.privacy.version, src.privacy[lang])}\n\n` +
    `  override val terms = ${kotlinDoc('TERMS', src.terms.version, src.terms[lang])}\n}\n`;
}

/** همهٔ رونوشت‌ها: مسیرِ نسبی ⇒ محتوا */
export function build(raw = fs.readFileSync(path.join(ROOT, SOURCE), 'utf8')) {
  const src = validate(JSON.parse(raw));
  const out = { [SERVER_COPY]: raw };
  for (const l of LANGS) out[`${KT_DIR}/LegalTexts${NAME[l]}.kt`] = kotlin(src, l);
  return out;
}

/** رونوشت‌هایی که با منبع نمی‌خوانند */
export function stale(files = build()) {
  return Object.entries(files).filter(([rel, text]) => {
    const p = path.join(ROOT, rel);
    return !fs.existsSync(p) || fs.readFileSync(p, 'utf8') !== text;
  }).map(([rel]) => rel);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const files = build();
  const bad = stale(files);
  if (process.argv.includes('--check')) {
    if (bad.length) {
      console.error(`رونوشتِ متنِ حقوقی با ${SOURCE} نمی‌خواند: ${bad.join('، ')} ⇒ node tools/legal/generate.mjs`);
      process.exit(1);
    }
    console.log(`legal-texts: ${Object.keys(files).length} رونوشت با منبع یکی است`);
  } else {
    for (const rel of bad) {
      fs.mkdirSync(path.dirname(path.join(ROOT, rel)), { recursive: true });
      fs.writeFileSync(path.join(ROOT, rel), files[rel]);
    }
    console.log(`legal-texts: ${bad.length ? `نوشته شد: ${bad.join('، ')}` : 'همه به‌روز بود'}`);
  }
}
