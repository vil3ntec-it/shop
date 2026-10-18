'use strict';
/**
 * زنجیرهٔ هش — قالبِ یگانهٔ «tohid-hashchain-v1» (SEC-02، STF-09؛ بخشِ ۱۰.۳).
 *
 *   hash = hex( SHA-256( ASCII(prevHash) ‖ UTF-8(canonicalJson(doc)) ) )
 *   prevHash ِ نخستین حلقه = GENESIS (۶۴ صفر)
 *
 * ⛔ این فایل، `license/hash-chain.js` (وب) و `core/audit/HashChain.kt`
 * (اندروید و کامپیوتر) **یک قرارداد**اند و هر سه با همان بردارهای
 * `license/test-vectors/hashchain-v1.json` سنجیده می‌شوند. یکی را عوض کنی
 * و بقیه را نه، زنجیرهٔ ساخته‌شده روی یک سکو روی دیگری «شکسته» دیده می‌شود.
 * قالب عوض شد؟ فایلِ بردارِ تازه (v2) بساز؛ v1 هرگز دست نمی‌خورد.
 *
 * canonicalJson = زیرمجموعهٔ RFC 8785 (JCS):
 *   - کلیدها به ترتیبِ واحدهای UTF-16، بی فاصله؛
 *   - رشته مثلِ JSON.stringify (فقط \" \\ \b \f \n \r \t و \u00xx برای
 *     نویسه‌های زیرِ U+0020)؛ نیم‌جفتِ تنها (lone surrogate) پذیرفته نیست؛
 *   - عدد **فقط صحیح** و |n| ≤ 2^53−1 — مبلغ همیشه به کوچک‌ترین واحد است تا
 *     Kotlin و JS عینِ هم بنویسند؛ −0 همان 0 است؛
 *   - ویژگیِ undefined (فقط در JS) نوشته نمی‌شود، مثلِ JSON.stringify.
 */
const { createHash } = require('crypto');

const FORMAT = 'tohid-hashchain-v1';
const GENESIS = '0'.repeat(64);
const HEX64 = /^[0-9a-f]{64}$/;

class HashChainError extends Error {
  constructor(code, message) {
    super(message || code);
    this.code = code;
  }
}

function quote(s) {
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c >= 0xd800 && c <= 0xdbff) {
      const d = s.charCodeAt(i + 1);
      if (!(d >= 0xdc00 && d <= 0xdfff)) throw new HashChainError('lone_surrogate', 'نیم‌جفتِ تنها در رشته');
      i++;
    } else if (c >= 0xdc00 && c <= 0xdfff) {
      throw new HashChainError('lone_surrogate', 'نیم‌جفتِ تنها در رشته');
    }
  }
  return JSON.stringify(s);
}

function canonicalJson(value) {
  if (value === null) return 'null';
  switch (typeof value) {
    case 'boolean': return value ? 'true' : 'false';
    case 'string': return quote(value);
    case 'number':
      if (!Number.isSafeInteger(value)) {
        throw new HashChainError('not_integer', `عدد باید صحیح و کوچک‌تر از 2^53 باشد: ${value}`);
      }
      return String(value === 0 ? 0 : value);
    case 'object': break;
    default: throw new HashChainError('bad_value', `نوعِ ${typeof value} در سند نمی‌آید`);
  }
  if (Array.isArray(value)) {
    const parts = [];
    for (let i = 0; i < value.length; i++) {
      if (value[i] === undefined) throw new HashChainError('bad_value', 'undefined در آرایه');
      parts.push(canonicalJson(value[i]));
    }
    return `[${parts.join(',')}]`;
  }
  const proto = Object.getPrototypeOf(value);
  if (proto !== Object.prototype && proto !== null) throw new HashChainError('bad_value', 'فقط شیءِ ساده');
  const out = [];
  for (const k of Object.keys(value).sort()) {
    if (value[k] === undefined) continue;
    out.push(`${quote(k)}:${canonicalJson(value[k])}`);
  }
  return `{${out.join(',')}}`;
}

function sha256Hex(text) {
  return createHash('sha256').update(text, 'utf8').digest('hex');
}

function entryHash(prevHash, doc) {
  if (typeof prevHash !== 'string' || !HEX64.test(prevHash)) {
    throw new HashChainError('bad_prev', 'prevHash باید ۶۴ نویسهٔ هگزِ کوچک باشد');
  }
  return sha256Hex(prevHash + canonicalJson(doc));
}

/**
 * سنجشِ یک زنجیرهٔ کامل در حافظه: [{ seq?, prevHash, hash, doc }].
 * ⇒ { ok: true, length, head } یا { ok: false, index, seq, reason, expected, actual }
 * reason: seq_gap | prev_mismatch | bad_doc | hash_mismatch
 */
function verifyEntries(entries) {
  let prev = GENESIS;
  for (let i = 0; i < entries.length; i++) {
    const e = entries[i] || {};
    const fail = (reason, expected, actual) => ({ ok: false, index: i, seq: i + 1, reason, expected, actual });
    if (e.seq !== undefined && e.seq !== i + 1) return fail('seq_gap', i + 1, e.seq);
    if (e.prevHash !== prev) return fail('prev_mismatch', prev, e.prevHash);
    let h;
    try { h = entryHash(prev, e.doc); } catch (err) { return fail('bad_doc', null, err.code || err.message); }
    if (h !== e.hash) return fail('hash_mismatch', h, e.hash);
    prev = h;
  }
  return { ok: true, length: entries.length, head: prev };
}

module.exports = { FORMAT, GENESIS, HEX64, HashChainError, canonicalJson, sha256Hex, entryHash, verifyEntries };
