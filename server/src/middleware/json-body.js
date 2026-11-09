'use strict';
/**
 * پارسرِ همگانیِ بدنهٔ JSON (`app.js`) — پیش از هر سقفِ نرخ و احراز می‌دود.
 *
 *  ⛔ ممیزیِ ۳۵ (B4): NUL (`\u0000`) در رشته‌های بدنه — PostgreSQL متن و jsonb ِ NUL‌دار را نمی‌پذیرد، پس هر دری
 *  که رشته را بی `validate.text` (که NUL را از پیش می‌انداخت) می‌نوشت ۵۰۰ می‌داد (خرج کردنِ کد، پاک کردنِ مشتری…).
 *  همان کارِ `validate.text`، این‌بار برای همه. همگام‌سازی و پمپ (`sync`، `shop/sync`، `pump`) پارسرِ دست‌نخوردهٔ
 *  پیشین را دارند: آن راه‌ها مشترک با پمپ‌اند و opِ همگام‌سازی بیت‌به‌بیت همانی می‌ماند که دستگاه فرستاد.
 *
 *  ⛔ SR38-2 (بازبینیِ ۳۸): نه `reviver` — JSON.parse با reviver ۱۰–۲۵ برابر کندتر است و این‌جا پیش از سقفِ نرخ، پس هر
 *  بدنهٔ ۲MB ِ بی‌نام حلقهٔ رویداد را ~۰٫۷ ثانیه می‌بست. NUL در رشتهٔ JSON فقط از گریزِ `\u0000` می‌آید (نویسهٔ کنترلیِ
 *  خام در JSON نیست) ⇒ فقط بدنه‌ای که آن را دارد (یا utf-8 نیست) پس از پارس یک بار، بی بازگشت، پیموده می‌شود.
 */
const express = require('express');

const LIMIT = '2mb';
const RAW_SYNC = /^\/api(?:\/v1)?\/(?:sync|shop\/sync|pump)(?:\/|$)/;
const DIRTY = Symbol('jsonNul');
const ESC = Buffer.from('\\u0000');

const cut = (s) => (s.includes('\u0000') ? s.replace(/\u0000/g, '') : s);

/**
 * NUL از هر رشتهٔ مقدار (هر عمق، آرایه و شیء)؛ کلیدها همان (مثلِ reviver ِ پیشین). نسخهٔ تمیز از پایین به بالا ساخته
 * می‌شود — بی بازگشت (بدنهٔ ۲MB می‌تواند هزاران لایه باشد) و بی نوشتنِ پویا روی شیءِ کاربر (`Object.fromEntries` ویژگیِ
 * خودی می‌سازد، «__proto__» هم فقط کلید است؛ CodeQL ۲۹۴/۲۹۵). فقط بدنه‌ای که گریزِ `\u0000` دارد این راه را می‌رود.
 */
function stripNul(root) {
  if (typeof root === 'string') return cut(root);
  if (root === null || typeof root !== 'object') return root;
  const isNode = (v) => v !== null && typeof v === 'object';
  const done = new Map();
  const of = (v) => (isNode(v) ? done.get(v) : typeof v === 'string' ? cut(v) : v);
  const stack = [[root, false]];
  while (stack.length) {
    const [o, ready] = stack.pop();
    if (Array.isArray(o)) {
      if (ready) { done.set(o, o.map(of)); continue; }
      stack.push([o, true]);
      for (const v of o) if (isNode(v)) stack.push([v, false]);
    } else {
      const entries = Object.entries(o);
      if (ready) { done.set(o, Object.fromEntries(entries.map(([k, v]) => [k, of(v)]))); continue; }
      stack.push([o, true]);
      for (const [, v] of entries) if (isNode(v)) stack.push([v, false]);
    }
  }
  return done.get(root);
}

function jsonBody() {
  const plain = express.json({ limit: LIMIT });
  const clean = express.json({
    limit: LIMIT,
    verify: (req, _res, buf, enc) => { if (!/^utf-?8$/i.test(String(enc || 'utf-8')) || buf.includes(ESC)) req[DIRTY] = true; },
  });
  return function jsonBodyParser(req, res, next) {
    if (RAW_SYNC.test(req.path)) return plain(req, res, next);
    return clean(req, res, (err) => {
      if (!err && req[DIRTY]) req.body = stripNul(req.body);
      next(err);
    });
  };
}

module.exports = { jsonBody, stripNul, RAW_SYNC };
