'use strict';
/**
 * ورودیِ تحویل و پیک — همان کمک‌هایی که پیش‌تر درونِ `lib/delivery.js` بود (بی تغییرِ رفتار).
 * هر ماژولِ `delivery-*` از همین‌جا می‌خواند تا کمکِ تکراری ساخته نشود.
 */
const crypto = require('crypto');
const { stable } = require('./idempotency');
const { badRequest, notFound } = require('../middleware/errors');

const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const ID_RE = /^[0-9A-Z]{26}$/;

const MAX_MONEY_MINOR = 100_000_000_000;   // یک میلیارد افغانی (همان سقفِ سفارش)

const str = (v) => (typeof v === 'string' ? v.trim() : '');

function text(v, max) {
  const { stripControl } = require('./validate');
  return stripControl(str(v)).slice(0, max);
}

function int(v, field, { min = 0, max = MAX_MONEY_MINOR, def } = {}) {
  if ((v === undefined || v === null || v === '') && def !== undefined) return def;
  if (typeof v !== 'number' || !Number.isSafeInteger(v)) throw badRequest(`${field} باید عددِ صحیح باشد`, 'bad_number');
  if (v < min || v > max) throw badRequest(`${field} در بازهٔ مجاز نیست`, 'bad_number');
  return v;
}

function optInt(v, field, opts) {
  return v === undefined || v === null || v === '' ? null : int(v, field, opts);
}

function eventKey(raw, { required = true } = {}) {
  const k = str(raw);
  if (!k) {
    if (required) throw badRequest('شناسهٔ یکتای رویداد لازم است', 'event_id_required');
    return '';
  }
  if (!KEY_RE.test(k)) throw badRequest('شناسهٔ رویداد نامعتبر است', 'bad_event_id');
  return k;
}

function idOf(raw, code = 'bad_id') {
  const id = str(raw);
  if (!ID_RE.test(id)) throw notFound('پیدا نشد', code);
  return id;
}

const n = (x) => (x === null || x === undefined ? null : Number(x));
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};
const hashOf = (obj) => crypto.createHash('sha256').update(stable(obj)).digest('hex');

module.exports = { KEY_RE, ID_RE, MAX_MONEY_MINOR, str, text, int, optInt, eventKey, idOf, n, asJson, hashOf };
