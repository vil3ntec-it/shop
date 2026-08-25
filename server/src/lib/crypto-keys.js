'use strict';
/**
 * کلید امضای License.
 *
 * الگوریتم: ECDSA P-256 + SHA-256.
 * دلیل انتخاب: هم Node و هم WebCrypto مرورگر آن را به صورت بومی و با فرمت
 * امضای یکسان (raw r||s، ۶۴ بایت) پشتیبانی می‌کنند. اگر از crypto.sign کلاسیک
 * Node استفاده می‌شد، امضا DER بود و مرورگر نمی‌توانست آن را بررسی کند.
 *
 * کلید خصوصی فقط روی سرور می‌ماند و هرگز در پاسخ هیچ API برنمی‌گردد.
 * کلید عمومی برای بررسی امضا در اختیار برنامه قرار می‌گیرد (این کاملاً بی‌خطر است).
 */
const fs = require('fs');
const path = require('path');
const { webcrypto } = require('crypto');
const { subtle } = webcrypto;

const ALG = { name: 'ECDSA', namedCurve: 'P-256' };
const SIGN_ALG = { name: 'ECDSA', hash: 'SHA-256' };

function b64uEncode(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64uDecode(str) {
  const s = String(str).replace(/-/g, '+').replace(/_/g, '/');
  return Buffer.from(s + '='.repeat((4 - (s.length % 4)) % 4), 'base64');
}

async function generateKeyPair() {
  const kp = await subtle.generateKey(ALG, true, ['sign', 'verify']);
  const pkcs8 = await subtle.exportKey('pkcs8', kp.privateKey);
  const spki = await subtle.exportKey('spki', kp.publicKey);
  const keyId = b64uEncode(
    await webcrypto.subtle.digest('SHA-256', spki)
  ).slice(0, 16);
  return {
    keyId,
    privateKeyPem: toPem(Buffer.from(pkcs8), 'PRIVATE KEY'),
    publicKeyPem: toPem(Buffer.from(spki), 'PUBLIC KEY'),
    publicKeyB64: Buffer.from(spki).toString('base64'),
  };
}

function toPem(der, label) {
  const b64 = der.toString('base64').match(/.{1,64}/g).join('\n');
  return `-----BEGIN ${label}-----\n${b64}\n-----END ${label}-----\n`;
}
function fromPem(pem) {
  const body = String(pem).replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  return Buffer.from(body, 'base64');
}

async function importPrivateKey(pem) {
  return subtle.importKey('pkcs8', fromPem(pem), ALG, false, ['sign']);
}
async function importPublicKey(pem) {
  return subtle.importKey('spki', fromPem(pem), ALG, true, ['verify']);
}

async function sign(privateKey, dataBuf) {
  const sig = await subtle.sign(SIGN_ALG, privateKey, dataBuf);
  return Buffer.from(sig); // ۶۴ بایت raw r||s — همان چیزی که مرورگر می‌خواهد
}
async function verify(publicKey, sigBuf, dataBuf) {
  return subtle.verify(SIGN_ALG, publicKey, sigBuf, dataBuf);
}

/**
 * بارگذاری جفت‌کلید از مسیر داده‌شده. اگر وجود نداشته باشد خطا می‌دهد،
 * چون تولید خودکار کلید در زمان اجرا یعنی هر ری‌استارت همه‌ی Licenseهای
 * صادرشده را باطل می‌کند — این باید یک قدم آگاهانه (scripts/generate-keys.js) باشد.
 */
async function loadKeyPair(privatePath, publicPath) {
  if (!fs.existsSync(privatePath) || !fs.existsSync(publicPath)) {
    throw new Error(
      `کلید امضا پیدا نشد (${privatePath}). ابتدا «npm run generate-keys» را اجرا کنید.`
    );
  }
  const privPem = fs.readFileSync(privatePath, 'utf8');
  const pubPem = fs.readFileSync(publicPath, 'utf8');
  const spki = fromPem(pubPem);
  const keyId = b64uEncode(await subtle.digest('SHA-256', spki)).slice(0, 16);
  return {
    keyId,
    privateKey: await importPrivateKey(privPem),
    publicKey: await importPublicKey(pubPem),
    publicKeyB64: spki.toString('base64'),
    publicKeyPem: pubPem,
  };
}

function writeKeyPair(dir, kp) {
  fs.mkdirSync(dir, { recursive: true });
  const priv = path.join(dir, 'license-private.pem');
  const pub = path.join(dir, 'license-public.pem');
  // کلید خصوصی فقط برای صاحب فایل قابل خواندن باشد
  fs.writeFileSync(priv, kp.privateKeyPem, { mode: 0o600 });
  fs.writeFileSync(pub, kp.publicKeyPem, { mode: 0o644 });
  return { priv, pub };
}

module.exports = {
  ALG, SIGN_ALG, generateKeyPair, loadKeyPair, writeKeyPair,
  importPrivateKey, importPublicKey, sign, verify,
  b64uEncode, b64uDecode, toPem, fromPem,
};
