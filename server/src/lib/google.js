'use strict';
/**
 * بررسی ورود با حساب گوگل.
 *
 * برنامه‌ی اندروید از گوگل یک ID Token می‌گیرد و همان را به سرور می‌دهد.
 * سرور امضای آن را با کلیدهای عمومی گوگل بررسی می‌کند؛ پس گوشی نمی‌تواند
 * ادعای دروغ بکند. تصمیم درباره‌ی «این چه کسی است» فقط اینجا گرفته می‌شود.
 */
const { createPublicKey, createVerify, timingSafeEqual } = require('crypto');
const config = require('../config');
const { badRequest, forbidden } = require('../middleware/errors');

const ISSUERS = ['accounts.google.com', 'https://accounts.google.com'];

let cache = { keys: null, expiresAt: 0 };

function b64u(s) {
  return Buffer.from(String(s).replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

async function certs() {
  const t = Date.now();
  if (cache.keys && cache.expiresAt > t) return cache.keys;
  const res = await fetch(config.google.certsUrl, { signal: AbortSignal.timeout(10000) });
  if (!res.ok) throw new Error(`کلیدهای گوگل در دسترس نیست (${res.status})`);
  const body = await res.json();
  // به سرآیند Cache-Control گوگل احترام می‌گذاریم، با کف یک ساعت
  const cc = res.headers.get('cache-control') || '';
  const m = /max-age=(\d+)/.exec(cc);
  const ttl = Math.max(3600, m ? Number(m[1]) : 3600) * 1000;
  cache = { keys: body.keys || [], expiresAt: t + ttl };
  return cache.keys;
}

/**
 * @param {string} idToken
 * @returns {{sub:string, email:string|null, emailVerified:boolean, name:string, picture:string}}
 */
async function verifyIdToken(idToken) {
  if (typeof idToken !== 'string' || idToken.split('.').length !== 3) {
    throw badRequest('توکن گوگل معتبر نیست', 'bad_google_token');
  }
  if (!config.google.clientIds.length) {
    throw forbidden('ورود با گوگل روی این سرور تنظیم نشده است', 'google_not_configured');
  }

  const [headB64, payloadB64, sigB64] = idToken.split('.');
  let head, payload;
  try {
    head = JSON.parse(b64u(headB64).toString('utf8'));
    payload = JSON.parse(b64u(payloadB64).toString('utf8'));
  } catch {
    throw badRequest('توکن گوگل خوانده نشد', 'bad_google_token');
  }
  if (head.alg !== 'RS256') throw badRequest('الگوریتم توکن پشتیبانی نمی‌شود', 'bad_google_token');

  const jwk = (await certs()).find(k => k.kid === head.kid);
  if (!jwk) throw forbidden('کلید امضای گوگل شناخته نشد', 'bad_google_token');

  const key = createPublicKey({ key: jwk, format: 'jwk' });
  const verifier = createVerify('RSA-SHA256');
  verifier.update(`${headB64}.${payloadB64}`);
  verifier.end();
  if (!verifier.verify(key, b64u(sigB64))) {
    throw forbidden('امضای توکن گوگل درست نیست', 'bad_google_token');
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (!ISSUERS.includes(payload.iss)) throw forbidden('صادرکننده‌ی توکن گوگل نیست', 'bad_google_token');
  if (Number(payload.exp) < nowSec - 60) throw forbidden('توکن گوگل منقضی شده است', 'google_token_expired');
  if (Number(payload.iat) > nowSec + 300) throw forbidden('زمان توکن گوگل درست نیست', 'bad_google_token');

  const audOk = config.google.clientIds.some(id => {
    const a = Buffer.from(String(payload.aud || ''));
    const b = Buffer.from(id);
    return a.length === b.length && timingSafeEqual(a, b);
  });
  if (!audOk) throw forbidden('این توکن برای برنامه‌ی دیگری صادر شده است', 'bad_audience');
  if (!payload.sub) throw forbidden('توکن گوگل شناسه ندارد', 'bad_google_token');

  return {
    sub: String(payload.sub),
    email: payload.email ? String(payload.email).toLowerCase() : null,
    emailVerified: payload.email_verified === true || payload.email_verified === 'true',
    name: payload.name ? String(payload.name) : '',
    picture: payload.picture ? String(payload.picture) : '',
  };
}

module.exports = { verifyIdToken, certs };
