'use strict';
/*
 *  ══ صندوقِ SMTPِ واقعی، روی همین کامپیوتر ═══════════════════════════════
 *
 *  پرسش: «کدِ شش‌رقمی واقعاً از سرور بیرون می‌رود و به یک صندوق می‌رسد؟»
 *  جوابش را با ادعا سبز نمی‌کنیم. `lib/mailer.js` خودش SMTP می‌نویسد
 *  (بی nodemailer)، پس این‌جا یک سرورِ SMTPِ واقعی بالا می‌آید و همان
 *  گفت‌وگوی واقعی را انجام می‌دهد: EHLO · AUTH LOGIN · MAIL FROM ·
 *  RCPT TO · DATA · QUIT. آن‌چه می‌رسد همان چیزی است که به مشتری می‌رسید.
 *
 *  بندِ ۱۹ پرامپت: «کد ۶ رقمی کمتر از ۳ ثانیه می‌رسد» — این‌جا سنجیده
 *  می‌شود، نه حدس زده.
 */
const tls = require('tls');
const fs = require('fs');

function startMailbox({ port = 2465, cert, key, failAll = false } = {}) {
  const inbox = [];
  const server = tls.createServer({ cert: fs.readFileSync(cert), key: fs.readFileSync(key) }, (sock) => {
    let buf = '';
    let mode = null;         // 'data' وقتی داخلِ DATA هستیم
    let body = '';
    let rcpt = [];
    sock.write('220 mailbox.test ESMTP\r\n');
    sock.on('data', (chunk) => {
      buf += chunk.toString('utf8');
      let i;
      while ((i = buf.indexOf('\r\n')) >= 0) {
        const line = buf.slice(0, i); buf = buf.slice(i + 2);
        if (mode === 'data') {
          if (line === '.') {
            mode = null;
            //  ⚠️ نامه‌ای که رد می‌شود در صندوق **نمی‌نشیند** — وگرنه سنجهٔ
            //  «هیچ نامه‌ای پذیرفته نشد» با دستِ خودِ ابزار سبزِ دروغ می‌شد.
            if (!failAll) inbox.push({ to: rcpt.slice(), raw: body, at: Date.now() });
            rcpt = []; body = '';
            sock.write(failAll ? '550 rejected by policy\r\n' : '250 OK queued\r\n');
          } else body += line + '\n';
          continue;
        }
        const up = line.toUpperCase();
        if (up.startsWith('EHLO') || up.startsWith('HELO')) sock.write('250-mailbox.test\r\n250 AUTH LOGIN PLAIN\r\n');
        else if (up.startsWith('AUTH LOGIN')) { sock.write('334 VXNlcm5hbWU6\r\n'); mode = 'user'; }
        else if (mode === 'user') { mode = 'pass'; sock.write('334 UGFzc3dvcmQ6\r\n'); }
        else if (mode === 'pass') { mode = null; sock.write('235 ok\r\n'); }
        else if (up.startsWith('AUTH PLAIN')) sock.write('235 ok\r\n');
        else if (up.startsWith('MAIL FROM')) sock.write('250 OK\r\n');
        else if (up.startsWith('RCPT TO')) { rcpt.push(line.replace(/.*<|>.*/g, '')); sock.write('250 OK\r\n'); }
        else if (up === 'DATA') { mode = 'data'; sock.write('354 go ahead\r\n'); }
        else if (up === 'QUIT') { sock.write('221 bye\r\n'); sock.end(); }
        else sock.write('250 OK\r\n');
      }
    });
    sock.on('error', () => {});
  });
  return new Promise((res) => server.listen(port, '127.0.0.1', () => res({ server, inbox })));
}
module.exports = { startMailbox };
