'use strict';
/**
 * HTTPS ِ مستقیم (`config.tls`) — برای سرورِ شبکهٔ دکان، بی Caddy و بی تونل.
 *
 * چرا: برنامه‌ها `http` را در شبکه نمی‌پذیرند (هر کسِ روی همان وای‌فای توکنِ ورود را می‌خواند —
 * همان دلیلِ `network_security.xml`). گواهیِ خودامضا کافی است: برنامه اثرِ انگشتِ SHA-256 ِ آن را
 * در نخستین وصل نشان می‌دهد و پس از تأییدِ صاحبِ دکان سنجاقش می‌کند. همین اثرِ انگشت هنگامِ
 * راه‌اندازی این‌جا چاپ می‌شود تا با چشم مقایسه شود (docs/SERVERS-fa.md).
 */
const fs = require('node:fs');

function read(file, what) {
  try { return fs.readFileSync(file); } catch (e) {
    throw new Error(`${what} خوانده نشد (${file}): ${e.code || e.message}`);
  }
}

/** گزینه‌های `https.createServer` — یا `null` (همان http ِ امروز) */
function options(tls) {
  if (!tls) return null;
  if (tls.pfxFile) return { pfx: read(tls.pfxFile, 'TLS_PFX_FILE'), passphrase: tls.passphrase || undefined };
  if (tls.certFile && tls.keyFile) {
    return { cert: read(tls.certFile, 'TLS_CERT_FILE'), key: read(tls.keyFile, 'TLS_KEY_FILE') };
  }
  return null;
}

/** اثرِ انگشتِ گواهیِ برگ (SHA-256 ِ DER، همان که برنامه سنجاق می‌کند) — یا '' */
function fingerprintOf(opts) {
  if (!opts?.cert) return '';
  try { return new (require('node:crypto').X509Certificate)(opts.cert).fingerprint256; } catch { return ''; }
}

/**
 * پس از بالا آمدن، اثرِ انگشت را چاپ می‌کند. PEM مستقیم خوانده می‌شود؛ PFX با یک دست‌دادنِ
 * TLS به خودِ همین سرور (روی همین کامپیوتر، بی فرستادنِ هیچ داده‌ای).
 */
function announce(opts, port, host) {
  const print = (fp) => { if (fp) console.log(`اثرِ انگشتِ گواهیِ HTTPS (SHA-256): ${fp}`); };
  const direct = fingerprintOf(opts);
  if (direct) return print(direct);
  const target = !host || host === '0.0.0.0' || host === '::' ? '127.0.0.1' : host;
  const socket = require('node:tls').connect({ host: target, port, rejectUnauthorized: false }, () => {
    print(socket.getPeerCertificate()?.fingerprint256 || '');
    socket.end();
  });
  socket.on('error', () => {});
  socket.setTimeout(3000, () => socket.destroy());
}

module.exports = { options, fingerprintOf, announce };
