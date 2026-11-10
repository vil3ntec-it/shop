#!/usr/bin/env node
/**
 * OPS-05 — «تازه‌ها» برای کاربر (خواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۲: «هر پیش‌نمایش و آزمایشیِ تازه بگوید چه عوض شد»).
 *
 * PR ای که کدِ برنامه را عوض می‌کند (اندروید، کامپیوتر، آیفون یا `core`) باید دست‌کم یک سطرِ تازه در
 * `docs/WHATS-NEW-fa.md` داشته باشد، و هر سطرِ آن پرونده قالبِ درست داشته باشد (همان قاعدهٔ `WhatsNew.parse`؛
 * سنجشِ کامل در `WhatsNewFileTest`).
 *
 *   node tools/whatsnew/check.mjs --base <ref>   # پایه: CI ِ PR ⇒ HEAD^1، finish-pr.sh ⇒ origin/main
 *
 * خروج: ۰ درست (یا کدِ برنامه عوض نشده)، ۱ سطرِ تازه نیست، ۲ قالبِ پرونده بد است.
 * در CI («کیفیتِ کد») فقط هشدار است (سیاستِ مرحلهٔ ۱ ِ همان ورک‌فلو)؛ سدِ محلی `finish-pr.sh` است
 * (`WHATSNEW_OK=<دلیل>` برای تغییری که کاربر هیچ نمی‌بیند). آزمون: `check.test.mjs`.
 */
import { execFileSync } from 'node:child_process';
import { appendFileSync, existsSync, readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const FILE = 'docs/WHATS-NEW-fa.md';

/** کدِ برنامه: آنچه کاربر می‌بیند (نه آزمون، نه سرور، نه ابزار) */
export const APP_CODE = [
  /^android-native\/app\/src\/main\//,
  /^core\/src\/(commonMain|jvmMain|iosMain)\//,
  /^desktop\/src\/main\//,
  /^ios\/iosApp\//,
  /^ios\/shared\/src\/(commonMain|iosMain)\//,
];

const KINDS = ['تازه', 'تغییر', 'رفع', 'برداشته'];
const INSTEAD = '⇐ به‌جایش:';
const HEADING = /^##\s+(\d+\.\d+)\s+·\s+(\S.*)$/;
const SHIPPED = /^<!--\s*shipped:\s*([a-z]+=\d+(\s+[a-z]+=\d+)*)\s*-->$/;
const ENTRY = /^-\s+(\S+?):\s*(.*)$/;

export const isAppCode = (path) => APP_CODE.some((re) => re.test(path));

/** همان `squash` ِ WhatsNew.kt: فاصله‌های پیاپی (ASCII، مثلِ `\s` ِ کاتلین) یکی */
const squash = (s) => s.trim().split(/[ \t\n\x0B\f\r]+/).filter(Boolean).join(' ');

/**
 * سطرهای یادداشتِ پرونده (پس از نخستین «## »، یکدست‌شده)، مشکل‌های قالب و بخش‌های درست (همان `log.sections` ِ
 * `WhatsNew.parse`: سرخطِ درست با دست‌کم یک سطرِ درست، به ترتیبِ پرونده، با `shipped` ِ همان بخش). سطرِ هم‌کلید
 * (نوع + متن + «به‌جایش»، فاصله‌ها یکدست) و شمارهٔ shipped ِ صفر یا بیرون از Int هم همان مشکلِ کاتلین‌اند.
 * @returns {{ entries: string[], problems: string[], sections: { line: string, title: string, at: number, shipped: Record<string, number>,
 *   entries: { kind: string, text: string, instead: string|null }[] }[] }}
 */
export function scan(text) {
  const entries = [];
  const problems = [];
  const sections = [];
  const keys = new Set();
  let inSection = false;
  let inComment = false;
  let sectionLines = 0;
  let sectionAt = 0;
  let cur = null;   //  بخشِ در دست؛ null ⇒ سرخطِ بد (سطرهایش سنجیده می‌شوند، خودش نه)
  let good = 0;     //  سطرهای درستِ همان بخش
  const close = () => {
    if (inSection && sectionLines === 0) problems.push(`سطرِ ${sectionAt}: بخش سطری ندارد`);
    if (cur && good) sections.push(cur);
  };
  String(text ?? '').replace(/\r\n/g, '\n').split('\n').forEach((raw, i) => {
    const n = i + 1;
    const s = raw.trim();
    if (!inSection && !s.startsWith('## ')) return;
    if (inComment) { if (s.endsWith('-->')) inComment = false; return; }
    if (s.startsWith('## ')) {
      close();
      inSection = true; sectionLines = 0; sectionAt = n; good = 0;
      const h = HEADING.exec(s);
      cur = h ? { line: h[1], title: h[2].trim(), at: n, shipped: {}, entries: [] } : null;
      if (!h) problems.push(`سطرِ ${n}: سرخطِ بخش باید «## <دو عدد> · <عنوان>» باشد`);
      return;
    }
    if (s === '') return;
    if (s.startsWith('<!--')) {
      const sh = SHIPPED.exec(s);
      if (/^<!--\s*shipped:/.test(s) && !sh) problems.push(`سطرِ ${n}: shipped باید «کانال=شماره» باشد`);
      if (sh) {
        const shipped = {};
        for (const p of sh[1].split(/\s+/)) {
          const [ch, v] = p.split('=');
          if (Number(v) > 0 && Number(v) <= 2147483647) shipped[ch] = Number(v);
          else problems.push(`سطرِ ${n}: «${p}» در shipped باید «کانال=شماره» باشد`);
        }
        if (cur) cur.shipped = shipped;
      }
      if (!s.endsWith('-->')) inComment = true;
      return;
    }
    const m = ENTRY.exec(s);
    if (!m || !KINDS.includes(m[1])) { problems.push(`سطرِ ${n}: سطر باید «- تازه|تغییر|رفع|برداشته: متن» باشد`); return; }
    const cut = m[2].indexOf(INSTEAD);
    const body = (cut >= 0 ? m[2].slice(0, cut) : m[2]).trim();
    const instead = cut >= 0 ? m[2].slice(cut + INSTEAD.length).trim() : '';
    if (!body) problems.push(`سطرِ ${n}: متن خالی است`);
    else if (m[1] === 'برداشته' && !instead) problems.push(`سطرِ ${n}: «برداشته» بی «${INSTEAD} …» پذیرفته نیست`);
    else if (m[1] !== 'برداشته' && cut >= 0) problems.push(`سطرِ ${n}: «به‌جایش» فقط برای «برداشته» است`);
    else if (keys.has(`${m[1]}|${squash(body)}|${squash(instead)}`)) problems.push(`سطرِ ${n}: همین سطر پیش‌تر آمده (دو سطرِ یکسان یک کلید دارند)`);
    else {
      keys.add(`${m[1]}|${squash(body)}|${squash(instead)}`);
      good++;
      if (cur) cur.entries.push({ kind: m[1], text: body, instead: instead || null });
    }
    sectionLines++;
    entries.push(s.replace(/\s+/g, ' '));
  });
  close();
  return { entries, problems, sections };
}

/** سطرهای تازهٔ «پس از» که «پیش از» نداشت (چندتایی: سطرِ تکراری هم شمرده می‌شود) */
export function addedEntries(before, after) {
  const left = new Map();
  for (const e of scan(before).entries) left.set(e, (left.get(e) ?? 0) + 1);
  return scan(after).entries.filter((e) => {
    const k = left.get(e) ?? 0;
    if (k > 0) { left.set(e, k - 1); return false; }
    return true;
  });
}

/**
 * @param {{ changed: string[], before: string, after: string }} input
 * @returns {{ code: 0|1|2, app: string[], added: string[], problems: string[] }}
 */
export function check({ changed, before, after }) {
  const app = changed.filter(isAppCode);
  const { problems } = scan(after);
  const added = addedEntries(before, after);
  const code = problems.length ? 2 : app.length && !added.length ? 1 : 0;
  return { code, app, added, problems };
}

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
}

function main(argv) {
  const at = argv.indexOf('--base');
  const base = at >= 0 ? argv[at + 1] : 'origin/main';
  const changed = git(['diff', '--name-only', base, 'HEAD']).split('\n').filter(Boolean);
  let before = '';
  try { before = git(['show', `${base}:${FILE}`]); } catch { before = ''; }
  const after = existsSync(FILE) ? readFileSync(FILE, 'utf8') : '';
  const r = check({ changed, before, after });
  const gh = !!process.env.GITHUB_ACTIONS;
  const say = (level, msg) => console.log(gh ? `::${level} file=${FILE}::${msg}` : msg);
  if (r.code === 2) {
    say('warning', `«تازه‌ها»: قالبِ ${FILE} درست نیست — ${r.problems.length} مشکل`);
    r.problems.forEach((p) => console.log('  ' + p));
  } else if (r.code === 1) {
    say('warning', `«تازه‌ها»: این PR کدِ برنامه را عوض می‌کند (${r.app.length} پرونده) ولی سطرِ تازه‌ای در ${FILE} ندارد. یک سطرِ ساده از چشمِ کاربر بیفزایید: «- تازه|تغییر|رفع|برداشته: …» (قاعده‌ها سرِ همان پرونده).`);
    r.app.slice(0, 5).forEach((p) => console.log('  ' + p));
  } else {
    console.log(r.app.length ? `«تازه‌ها»: ${r.added.length} سطرِ تازه برای ${r.app.length} پروندهٔ برنامه` : '«تازه‌ها»: کدِ برنامه عوض نشده');
  }
  if (gh && process.env.GITHUB_STEP_SUMMARY) {
    const line = r.code === 0 ? `✅ «تازه‌ها»: ${r.added.length} سطرِ تازه` : r.code === 1 ? '⚠️ «تازه‌ها»: کدِ برنامه عوض شده ولی سطرِ تازه‌ای در docs/WHATS-NEW-fa.md نیست' : '⚠️ «تازه‌ها»: قالبِ docs/WHATS-NEW-fa.md درست نیست';
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, line + '\n');
  }
  return r.code;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) process.exitCode = main(process.argv.slice(2));
