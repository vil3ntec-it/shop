'use strict';
/**
 * بیرون کشیدنِ شکلِ تقریبیِ درخواست و پاسخ از متنِ JS ِ یک مسیر — بی اجرا، بی وابستگی.
 * تقریبی است و همین را در خروجی می‌گوید (`shape: inline | partial | none`): فیلدی که handler به
 * تابعِ کمکیِ دیگری می‌دهد این‌جا دیده نمی‌شود. برای قرارداد کافی است، برای اثباتِ امنیت نه.
 */

const OPEN = { '(': ')', '[': ']', '{': '}' };
const REGEX_PREV = /[(,=:[!&|?{};+\-*%<>~^]$|(?:^|[^\w$])(?:return|typeof|case|in|of|void|delete|throw)$/;

/** از اندیسِ `start` (روی یک براکتِ باز) تا براکتِ بستهٔ هم‌تایش؛ رشته، قالب، توضیح و regex را رد می‌کند. */
function matchClose(src, start) {
  const stack = [OPEN[src[start]]];
  let i = start + 1;
  while (i < src.length && stack.length) {
    const c = src[i];
    if (c === '"' || c === "'") { i = skipString(src, i, c); continue; }
    if (c === '`') { i = skipTemplate(src, i); continue; }
    if (c === '/' && src[i + 1] === '/') { i = src.indexOf('\n', i); if (i < 0) return -1; continue; }
    if (c === '/' && src[i + 1] === '*') { i = src.indexOf('*/', i + 2); if (i < 0) return -1; i += 2; continue; }
    if (c === '/' && REGEX_PREV.test(src.slice(Math.max(0, i - 12), i).trimEnd())) { i = skipRegex(src, i); continue; }
    if (OPEN[c]) stack.push(OPEN[c]);
    else if (c === ')' || c === ']' || c === '}') { if (stack.pop() !== c) return -1; }
    i++;
  }
  return stack.length ? -1 : i - 1;
}
function skipString(src, i, q) {
  for (i++; i < src.length; i++) { if (src[i] === '\\') i++; else if (src[i] === q) return i + 1; }
  return src.length;
}
function skipTemplate(src, i) {
  for (i++; i < src.length; i++) {
    if (src[i] === '\\') i++;
    else if (src[i] === '`') return i + 1;
    else if (src[i] === '$' && src[i + 1] === '{') { const e = matchClose(src, i + 1); if (e < 0) return src.length; i = e; }
  }
  return src.length;
}
function skipRegex(src, i) {
  let cls = false;
  for (i++; i < src.length; i++) {
    const c = src[i];
    if (c === '\\') i++;
    else if (c === '[') cls = true;
    else if (c === ']') cls = false;
    else if (c === '/' && !cls) { i++; while (/[a-z]/.test(src[i] || '')) i++; return i; }
    else if (c === '\n') return i;
  }
  return i;
}

/** آرگومان‌های سطحِ بالای فراخوانی که پرانتزِ بازش در `open` است ⇒ [متن]. */
function splitArgs(src, open) {
  const close = matchClose(src, open);
  if (close < 0) return null;
  const args = [];
  let from = open + 1;
  for (let i = open + 1; i < close; i++) {
    const c = src[i];
    if (c === '"' || c === "'") { i = skipString(src, i, c) - 1; continue; }
    if (c === '`') { i = skipTemplate(src, i) - 1; continue; }
    if (c === '/' && src[i + 1] === '/') { i = src.indexOf('\n', i); continue; }
    if (c === '/' && src[i + 1] === '*') { i = src.indexOf('*/', i + 2) + 1; continue; }
    if (c === '/' && REGEX_PREV.test(src.slice(Math.max(0, i - 12), i).trimEnd())) { i = skipRegex(src, i) - 1; continue; }
    if (OPEN[c]) { i = matchClose(src, i); if (i < 0) return null; continue; }
    if (c === ',') { args.push(src.slice(from, i).trim()); from = i + 1; }
  }
  const last = src.slice(from, close).trim();
  if (last) args.push(last);
  return args;
}

/** فراخوانیِ روی (line, col) ِ یک stack frame: از آن‌جا تا نخستین `(` جلو می‌رود. */
function callArgsAt(src, line, col) {
  let off = 0;
  for (let l = 1; l < line; l++) off = src.indexOf('\n', off) + 1;
  off += col - 1;
  const m = /^[\w$.\s]*\(/.exec(src.slice(off, off + 200));
  return m ? splitArgs(src, off + m[0].length - 1) : null;
}

/** کلیدهای سطحِ بالای یک object literal که `{` ِ آن در `open` است. */
function objectKeys(src, open) {
  const parts = splitArgs(src, open) || [];
  const keys = [];
  for (const p of parts) {
    if (p.startsWith('...')) { keys.push('...'); continue; }
    const m = /^(?:async\s+)?(?:get\s+|set\s+)?(?:\[[^\]]*\]|['"]?([\w$-]+)['"]?)\s*(?::|\(|$)/.exec(p);
    if (m && m[1]) keys.push(m[1]);
  }
  return keys;
}

const ERR_STATUS = { badRequest: 400, unauthorized: 401, forbidden: 403, notFound: 404, conflict: 409, tooMany: 429, upstream: 502 };
const ERR_DEFAULT = { badRequest: 'bad_request', unauthorized: 'unauthorized', forbidden: 'forbidden', notFound: 'not_found', conflict: 'conflict', tooMany: 'rate_limited', upstream: 'delivery_failed' };
const strLit = (t) => { const m = /^(['"`])([^'"`$]*)\1$/.exec((t || '').trim()); return m ? m[2] : null; };
const uniq = (a) => [...new Set(a)].sort();

/** فیلدهای `req.<part>` (مستقیم، destructure و نامِ مستعار) در متن. */
function fieldsOf(text, part) {
  const out = [];
  const names = [`req\\.${part}`];
  const alias = new RegExp(`(?:const|let|var)\\s+([\\w$]+)\\s*=\\s*\\(?\\s*req\\.${part}\\b(?:\\s*(?:\\|\\||\\?\\?)\\s*\\{\\s*\\})?\\s*\\)?\\s*[;,\\n]`, 'g');
  for (const m of text.matchAll(alias)) names.push(m[1].replace(/\$/g, '\\$'));
  for (const n of names) {
    for (const m of text.matchAll(new RegExp(`(?<![\\w$.])${n}\\??\\.([A-Za-z_$][\\w$]*)`, 'g'))) out.push(m[1]);
    for (const m of text.matchAll(new RegExp(`(?<![\\w$.])${n}\\??\\.?\\[\\s*['"]([^'"]+)['"]\\s*\\]`, 'g'))) out.push(m[1]);
    const de = new RegExp(`(?:const|let|var)\\s*\\{`, 'g');
    for (const m of text.matchAll(de)) {
      const open = m.index + m[0].length - 1;
      const close = matchClose(text, open);
      if (close < 0) continue;
      if (!new RegExp(`^\\s*=\\s*\\(?\\s*${n}\\b`).test(text.slice(close + 1, close + 60))) continue;
      for (const p of splitArgs(text, open) || []) {
        const k = /^([A-Za-z_$][\w$]*)/.exec(p.replace(/^\.\.\./, ''));
        if (k && !p.startsWith('...')) out.push(k[1]);
      }
    }
  }
  return uniq(out.filter((f) => !['length', 'toString', 'constructor'].includes(f)));
}

/** شکلِ تقریبیِ یک handler از متنش. */
function shapeOf(text) {
  const body = fieldsOf(text, 'body');
  const query = fieldsOf(text, 'query');
  const params = fieldsOf(text, 'params');
  const keys = [];
  const statuses = new Set();
  for (const m of text.matchAll(/\bres(?:\.status\(\s*(\d{3})\s*\))?\.json\(\s*\{/g)) {
    keys.push(...objectKeys(text, m.index + m[0].length - 1));
    statuses.add(m[1] ? Number(m[1]) : 200);
  }
  for (const m of text.matchAll(/\bres\.status\(\s*(\d{3})\s*\)/g)) statuses.add(Number(m[1]));
  for (const m of text.matchAll(/\bres\.sendStatus\(\s*(\d{3})\s*\)/g)) statuses.add(Number(m[1]));
  if (/\bres\.json\(\s*[^{\s]/.test(text) && !statuses.size) statuses.add(200);
  const errors = [];
  for (const m of text.matchAll(/\b(badRequest|unauthorized|forbidden|notFound|conflict|tooMany|upstream)\(/g)) {
    const a = splitArgs(text, m.index + m[0].length - 1) || [];
    errors.push(`${ERR_STATUS[m[1]]} ${strLit(a[1]) || (a.length < 2 ? ERR_DEFAULT[m[1]] : '?')}`);
  }
  for (const m of text.matchAll(/new ApiError\(\s*(\d{3})\s*,\s*(['"])([^'"]+)\2/g)) errors.push(`${m[1]} ${m[3]}`);
  const responseKeys = uniq(keys).slice(0, 60);
  const found = body.length + query.length + responseKeys.length;
  return { body, query, params, responseKeys, statuses: [...statuses].sort(), errors: uniq(errors) , found };
}

/** متنِ تابعِ نام‌دار در همان فایل (function NAME / const NAME = …) — برای handlerِ تعریف‌شده جدا. */
function namedFunctionText(src, name) {
  const re = new RegExp(`(?:async\\s+)?function\\s+${name}\\s*\\(|(?:const|let)\\s+${name}\\s*=\\s*(?:wrap\\()?(?:async\\s*)?(?:function\\s*)?\\(?`, 'g');
  const m = re.exec(src);
  if (!m) return '';
  const brace = src.indexOf('{', m.index + m[0].length);
  if (brace < 0) return '';
  const end = matchClose(src, brace);
  return end < 0 ? '' : src.slice(m.index, end + 1);
}

module.exports = { matchClose, splitArgs, callArgsAt, objectKeys, fieldsOf, shapeOf, namedFunctionText, strLit };
