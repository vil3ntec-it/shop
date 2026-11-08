'use strict';
/**
 * CSV ِ ردیف‌های دستهٔ بانکِ تصاویر (‎POST /api/admin/image-bank/batches/:id/rows‎، کلیدِ `csv`) — جداشده از
 * ‎lib/image-bank.js‎ (سقفِ طولِ فایل). رفتار همان پیشین؛ ‎codeql-hardening.test.js‎.
 *
 * ⛔ متن پیش از حلقه با سقفِ سنجیده (`csv_too_large`): بدنهٔ JSON حداکثر ۲ مگابایت است (‎app.js‎) ⇒ هیچ
 *    رشته‌ای به آن نمی‌رسد؛ فقط ورودیِ دست‌کاری‌شده (آرایه/شیئی که String() اش از بدنه بلندتر است) — CodeQL:
 *    loop bound injection.
 */
const { badRequest } = require('../middleware/errors');

const CSV_MAX_CHARS = 2 * 1024 * 1024;
const CSV_RESERVED = new Set(['__proto__', 'constructor', 'prototype']);

/** CSV با سطرِ سرآیند ⇒ ردیف‌ها (row_no = شمارهٔ سطرِ فایل). نقل‌قولِ "…" پشتیبانی می‌شود. */
function parseCsv(text) {
  const lines = [];
  let row = [], field = '', q = false;
  const s = String(text || '').replace(/^﻿/, '');
  if (s.length > CSV_MAX_CHARS) throw badRequest('فایلِ CSV بیش از حد بزرگ است', 'csv_too_large');
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (q) {
      if (ch === '"' && s[i + 1] === '"') { field += '"'; i++; } else if (ch === '"') q = false; else field += ch;
    } else if (ch === '"') q = true;
    else if (ch === ',') { row.push(field); field = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && s[i + 1] === '\n') i++;
      row.push(field); lines.push(row); row = []; field = '';
    } else field += ch;
  }
  if (field || row.length) { row.push(field); lines.push(row); }
  if (!lines.length) return [];
  const head = lines[0].map((h) => h.trim().toLowerCase());
  const out = [];
  for (let n = 1; n < lines.length; n++) {
    if (lines[n].every((x) => !x.trim())) continue;
    //  ⛔ نامِ ستون از فایل است: ردیف با `Object.fromEntries` ساخته می‌شود (ویژگیِ خودِ شیء، بی نوشتنِ پویای
    //     `o[h] = …` که setter ِ `__proto__` را صدا می‌زند — CodeQL: remote property injection) و
    //     `__proto__`/`constructor`/`prototype` باز هم کنار می‌روند. ترتیب و «آخری می‌بَرد» همان پیشین است.
    const cells = head.flatMap((h, j) => (h && !CSV_RESERVED.has(h) ? [[h, (lines[n][j] || '').trim()]] : []));
    out.push(Object.fromEntries([['row_no', n + 1], ...cells]));
  }
  return out;
}

module.exports = { parseCsv, CSV_MAX_CHARS };
