'use strict';
/**
 * رشته‌های ثابتِ یک جاوااسکریپت — بی هیچ وابستگی (نه espree، نه acorn) — برای پوششِ زبانِ نسخهٔ وبِ دکان
 * (`web-shop-i18n.test.js`). توضیح، رشته، قالب (با `${…}` ِ تودرتو) و regex را می‌شناسد؛ برای هر رشته متن (گریزها
 * باز)، شمارهٔ سطر و اینکه کنارِ `+` است (تکه‌ای از الحاق) را می‌دهد، و نشانِ «داده» ِ توضیح‌ها را:
 *   • `// داده: دلیل` (یا `/* داده: … *\/`) روی همان سطر ⇒ همهٔ رشته‌های آن سطر داده‌اند؛
 *   • `// <داده: دلیل>` تا `// </داده>` ⇒ همهٔ سطرهای میانِ آن دو.
 */

const KEYWORDS_BEFORE_REGEX = new Set(['return', 'typeof', 'case', 'do', 'else', 'in', 'of', 'new', 'delete', 'void', 'throw', 'instanceof', 'yield', 'await']);
const HOLE = '\uE000';

function unescape(raw) {
  return raw.replace(/\\(u\{[0-9a-fA-F]+\}|u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|\r\n|[\s\S])/g, (m, e) => {
    if (e[0] === 'u' && e[1] === '{') return String.fromCodePoint(parseInt(e.slice(2, -1), 16));
    if (e[0] === 'u' && e.length === 5) return String.fromCharCode(parseInt(e.slice(1), 16));
    if (e[0] === 'x' && e.length === 3) return String.fromCharCode(parseInt(e.slice(1), 16));
    if (e === '\n' || e === '\r\n' || e === '\r') return '';
    return { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f', v: '\v', 0: '\0' }[e] ?? e;
  });
}

/**
 * @returns {{ strings: {text:string, line:number, kind:'str'|'tpl', plus:boolean, key?:boolean, console?:boolean}[], dataLines: Set<number> }}
 *   `key`: کلیدِ شیء یا `case` (داده)؛ `console`: نخستین آرگومانِ `console.x(…)` (پیامِ برنامه‌نویس).
 *   `text` ِ قالب: هر `${…}` یک HOLE (U+E000)؛ رشته‌های درونِ `${…}` جدا می‌آیند.
 */
function scan(src) {
  const strings = [];
  const dataLines = new Set();
  const blocks = [];
  let i = 0;
  let line = 1;
  let prev = '';        //  آخرین نشانهٔ معنادار (برای تشخیصِ regex و `+`)
  let lastStr = null;   //  آخرین رشته، تا اگر پس از آن `+` آمد، «کنارِ +» شود
  const stack = [];     //  قالب‌های باز: عمقِ آکولادِ هر `${`
  let depth = 0;

  const comment = (text, from, to) => {
    if (/<داده/.test(text)) blocks.push({ open: from });
    if (/<\/داده>/.test(text)) { const b = blocks.findLast((x) => x.close === undefined); if (b) b.close = to; }
    else if (/(^|\s|\/\/|\*|·)داده:/.test(text) && !/<داده/.test(text)) for (let l = from; l <= to; l++) dataLines.add(l);
  };
  const hist = [];      //  چهار نشانهٔ معنادارِ پیشین (برای `console.x(`)
  const push = (s) => {
    s.before = prev;
    if (hist.length === 4 && hist[0] === 'console' && hist[1] === '.' && hist[3] === '(') s.console = true;
    strings.push(s);
    lastStr = s;
  };
  const sig = (t) => {
    if (t === '+' && lastStr && prev !== '+') lastStr.plus = true;
    //  کلیدِ شیء (`{ 'متن': … }`) و `case 'متن':` — داده‌اند، نه متنِ صفحه
    if (t === ':' && lastStr && prev === 'str' && ['{', ',', 'case'].includes(lastStr.before)) lastStr.key = true;
    prev = t;
    hist.push(t); if (hist.length > 4) hist.shift();
    if (t !== '+') lastStr = null;
  };
  const strTok = () => { prev = 'str'; hist.push('str'); if (hist.length > 4) hist.shift(); };

  function readTemplate() {
    //  i روی نویسهٔ پس از ` (یا پس از } ِ پایانِ ${}) است
    let text = '';
    const start = line;
    while (i < src.length) {
      const c = src[i];
      if (c === '\\') { text += unescape(src.slice(i, i + 2)); if (src[i + 1] === '\n') line++; i += 2; continue; }
      if (c === '`') { i++; return { text, start, done: true }; }
      if (c === '$' && src[i + 1] === '{') { i += 2; return { text, start, done: false }; }
      if (c === '\n') line++;
      text += c;
      i++;
    }
    return { text, start, done: true };
  }

  let tpl = null; //  قالبِ در حالِ ساخت (برای تکه‌های پس از `}`)
  const tplParts = [];
  while (i < src.length) {
    const c = src[i];
    if (c === '\n') { line++; i++; continue; }
    if (c === ' ' || c === '\t' || c === '\r') { i++; continue; }
    if (c === '/' && src[i + 1] === '/') {
      const end = src.indexOf('\n', i);
      comment(src.slice(i, end < 0 ? src.length : end), line, line);
      i = end < 0 ? src.length : end;
      continue;
    }
    if (c === '/' && src[i + 1] === '*') {
      const end = src.indexOf('*/', i + 2);
      const body = src.slice(i, end < 0 ? src.length : end + 2);
      const from = line;
      line += (body.match(/\n/g) || []).length;
      comment(body, from, line);
      i = end < 0 ? src.length : end + 2;
      continue;
    }
    if (c === '\'' || c === '"') {
      let j = i + 1;
      let raw = '';
      while (j < src.length && src[j] !== c) {
        if (src[j] === '\\') { raw += src.slice(j, j + 2); if (src[j + 1] === '\n') line++; j += 2; continue; }
        if (src[j] === '\n') break;
        raw += src[j];
        j++;
      }
      const s = { text: unescape(raw), line, kind: 'str', plus: prev === '+' };
      push(s);
      strTok();
      i = j + 1;
      continue;
    }
    if (c === '`') {
      i++;
      const part = readTemplate();
      if (part.done) { push({ text: part.text, line: part.start, kind: 'tpl', plus: prev === '+' }); strTok(); continue; }
      tplParts.push({ text: part.text + HOLE, start: part.start, plus: prev === '+' });
      stack.push(depth);
      depth = 0;
      prev = '(';
      lastStr = null;
      continue;
    }
    if (c === '{') { depth++; sig('{'); i++; continue; }
    if (c === '}') {
      if (stack.length && depth === 0) {
        //  پایانِ ${…}: ادامهٔ همان قالب
        i++;
        depth = stack.pop();
        const cur = tplParts[tplParts.length - 1];
        const part = readTemplate();
        if (part.done) {
          tplParts.pop();
          push({ text: cur.text + part.text, line: cur.start, kind: 'tpl', plus: cur.plus });
          strTok();
        } else {
          cur.text += part.text + HOLE;
          stack.push(depth);
          depth = 0;
          prev = '(';
        }
        continue;
      }
      depth--;
      sig('}');
      i++;
      continue;
    }
    if (c === '/') {
      const regex = prev === '' || /^[(,=:[!&|?{};+\-*%<>~^]$/.test(prev) || prev === '}' || KEYWORDS_BEFORE_REGEX.has(prev);
      if (regex) {
        let j = i + 1;
        let cls = false;
        while (j < src.length && src[j] !== '\n') {
          if (src[j] === '\\') { j += 2; continue; }
          if (src[j] === '[') cls = true; else if (src[j] === ']') cls = false; else if (src[j] === '/' && !cls) break;
          j++;
        }
        j++;
        while (/[a-z]/i.test(src[j] || '')) j++;
        i = j;
        prev = 'regex';
        lastStr = null;
        continue;
      }
      sig('/');
      i++;
      continue;
    }
    const w = /^[A-Za-z_$\u0080-￿][\w$\u0080-￿]*|^\d[\w.]*/.exec(src.slice(i, i + 64));
    if (w && !/[\u0600-\u06FF]/.test(w[0][0])) { sig(w[0]); i += w[0].length; continue; }
    sig(c);
    i++;
  }
  for (const b of blocks) for (let l = b.open; l <= (b.close ?? b.open); l++) dataLines.add(l);
  return { strings, dataLines };
}

module.exports = { scan, HOLE };
