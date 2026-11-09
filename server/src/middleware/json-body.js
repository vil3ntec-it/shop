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

/** NUL از هر رشتهٔ مقدار (هر عمق، آرایه و شیء) — درجا؛ کلیدها همان (مثلِ reviver ِ پیشین) */
function stripNul(root) {
  if (typeof root === 'string') return cut(root);
  if (root === null || typeof root !== 'object') return root;
  const stack = [root];
  while (stack.length) {
    const o = stack.pop();
    const keys = Array.isArray(o) ? null : Object.keys(o);
    const n = keys ? keys.length : o.length;
    for (let i = 0; i < n; i++) {
      const k = keys ? keys[i] : i;
      const v = o[k];
      if (typeof v === 'string') { if (v.includes('\u0000')) o[k] = cut(v); } else if (v !== null && typeof v === 'object') stack.push(v);
    }
  }
  return root;
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
