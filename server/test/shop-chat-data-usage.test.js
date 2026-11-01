'use strict';
/**
 * CHAT-13 (v3 §۳۳.۴ و §۳۳.۲): مصرفِ داده برای متن، پیش‌نمایشِ عکس، عکسِ کیفیتِ
 * عادی و پیامِ صوتی — **بایتِ واقعیِ روی سیم** (درخواست + پاسخ، با سرآیندها)،
 * با هر درخواست روی اتصالِ تازه تا شمارشِ سوکت فقط همان درخواست باشد.
 *
 * دو عدد جدا می‌شوند:
 *   payload  — بایتِ خودِ رسانه (به اندازه‌ای که برنامه می‌سازد؛ این‌جا نمونهٔ ثابت)
 *   سربار    — هر چه جز آن (سرآیندِ HTTP، JSON ِ پیام و نمای گفت‌وگو، رسید)
 * سقف‌های پایین فقط برای **سربار**اند و موقت (هدفِ حجمیِ انتشار با صاحب محصول است، §۳۳.۴):
 * برای این‌که سربارِ یک پیام بی‌صدا بزرگ نشود. TLS، فشرده‌سازیِ پروکسی و
 * عکس/صدای واقعیِ دستگاه این‌جا نیستند ⇒ اندازه‌گیری روی دستگاه و شبکهٔ هدف هنوز لازم است.
 *
 * نمونه‌ها برابرِ تنظیمِ برنامهٔ مدیریت (#299، `ChatMedia.kt`): پیش‌نمایش ≤ ۳۲۰px
 * (نمونه ۲۰KB)، عکس ≤ ۱۶۰۰px JPEG ۸۲٪ (نمونه ۲۵۰KB)، صدا Opus ۲۴kbps (۳۰ ثانیه ≈ ۹۰KB).
 */
const http = require('node:http');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

const KB = 1024;
const SAMPLE = { thumb: 20 * KB, image: 250 * KB, voiceSeconds: 30, voiceKbps: 24 };
//  سقف‌های موقتِ سربار (بایت) — نه هدفِ انتشار
const BUDGET = {
  textSend: 2 * KB,        // فرستنده: POST ِ پیام و پاسخش (سنجیده ۱٫۲KB)
  textReceive: 4 * KB,     // گیرنده: long-poll ِ حاملِ پیام + رسیدِ «رسید/خواندم» (سنجیده ۲٫۸KB)
  mediaUpload: 1 * KB,     // هر بارگذاری، جز بایتِ رسانه (سنجیده ۰٫۸KB)
  mediaDownload: 1 * KB,   // هر دریافت، جز بایتِ رسانه (سنجیده ۰٫۶KB)
  idlePoll: 2 * KB,        // یک دورِ خالیِ long-poll، هر ۲۵ ثانیه تا گفت‌وگو باز است (سنجیده ۱٫۱KB)
};

let base = '';
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;

test.before(async () => {
  base = await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(() => h.stop());

/** یک درخواست روی اتصالِ تازه؛ بایت‌های بالا/پایینِ همان سوکت پس از بسته شدن. */
function wire(method, path, { headers = {}, json = undefined, raw = null } = {}) {
  const body = raw || (json === undefined ? null : Buffer.from(JSON.stringify(json)));
  const u = new URL(base + path);
  return new Promise((resolve, reject) => {
    const req = http.request({
      hostname: u.hostname, port: u.port, path: u.pathname + u.search, method, agent: false,
      headers: {
        Connection: 'close',
        ...(body ? { 'Content-Type': raw ? 'application/octet-stream' : 'application/json', 'Content-Length': body.length } : {}),
        ...headers,
      },
    }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const s = req.socket;
        const done = () => {
          const buf = Buffer.concat(chunks);
          let parsed = null;
          try { parsed = JSON.parse(buf.toString('utf8')); } catch { parsed = null; }
          resolve({ status: res.statusCode, json: parsed, bytes: buf, up: s.bytesWritten, down: s.bytesRead });
        };
        if (s.destroyed) done(); else s.once('close', done);
      });
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

/** JPEG/Ogg ِ ساختار-درست با اندازهٔ دقیق (بایت‌های میانی ثابت؛ سرور فقط نوع و سقف را می‌سنجد). */
function jpegOf(size, fill) {
  const head = Buffer.from([0xff, 0xd8, 0xff, 0xdb, 0x00, 0x43, ...Array(65).fill(1), 0xff, 0xda, 0x00, 0x08, 1, 1, 0, 0, 0x3f, 0]);
  return Buffer.concat([head, Buffer.alloc(size - head.length - 2, fill), Buffer.from([0xff, 0xd9])]);
}
const oggOf = (size) => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(size - 4, 3)]);

const fmt = (n) => n.toLocaleString('en-US');

test('CHAT-13: بایتِ روی سیم برای متن، پیش‌نمایش، عکسِ عادی و صدا (فرستنده و گیرنده) و یک دورِ خالیِ long-poll — سربار زیرِ سقفِ موقت', {
  skip: (() => { try { require.resolve('../src/lib/storefront'); return false; } catch { return 'ویترین روی این شاخه نیست'; } })(),
}, async () => {
  //  دکان با چت و ویترین؛ مشتری با بلیتِ ویترین (همان راهِ مشتری)
  const u = await h.newUser('مصرف', 'shop');
  const shop = (await h.post('/api/shop', { name: 'دکانِ مصرف' }, { token: u.accessToken })).body.shop.id;
  for (const key of ['shop.orders', 'shop.storefront', 'shop.chat']) {
    assert.equal((await h.put('/api/admin/pump/live-config', { scope: shop, key, value: true }, { token: admin })).status, 200);
  }
  const slug = `usage-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'مصرف', contactPhone: '0700000004' }, { token: u.accessToken })).status, 200);
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790000501' });
  const ticket = (await h.post(`/api/store/${slug}/verify/check`, { phone: '0790000501', code: start.body.devCode })).body.ticket;
  assert.ok(ticket);
  const C = { 'X-Store-Ticket': ticket };
  const S = { Authorization: `Bearer ${u.accessToken}` };
  const conv = (await wire('POST', `/api/store/${slug}/chat/conversations`, { headers: C, json: { name: 'مشتری' } })).json.conversation.id;
  const cPath = `/api/store/${slug}/chat/conversations/${conv}`;
  const sPath = `/api/shop/chat/conversations/${conv}`;
  let cursor = (await wire('GET', `${sPath}/messages?after=0`, { headers: S })).json.cursor;

  const rows = [];
  const row = (name, side, r, payload = 0) => {
    assert.ok([200, 201].includes(r.status), `${name}: ${r.status} ${r.bytes.toString('utf8').slice(0, 200)}`);
    const total = r.up + r.down;
    rows.push({ name, side, up: r.up, down: r.down, total, payload, overhead: total - payload });
    return r;
  };
  /** گیرنده (دکان): long-poll ِ حاملِ پیام + رسیدِ «رسید و خواندم» */
  const receive = async (name) => {
    const got = row(`${name} — دریافت (long-poll)`, 'گیرنده', await wire('GET', `${sPath}/messages?after=${cursor}&wait=2000`, { headers: S }));
    assert.equal(got.json.messages.length, 1);
    cursor = got.json.cursor;
    const top = got.json.messages[0].seq;
    row(`${name} — رسید`, 'گیرنده', await wire('POST', `${sPath}/receipts`, { headers: S, json: { deliveredSeq: top, readSeq: top } }));
    return got.json.messages[0];
  };

  //  ۱) متن (۶۰ نویسهٔ فارسی، اندازهٔ پیامِ عادی)
  const text = 'سلام، نانِ تازه و دو کیلو برنج برای فردا صبح دارید؟ ممنون.';
  row('متن — ارسال', 'فرستنده', await wire('POST', `${cPath}/messages`, { headers: C, json: { clientId: cid(), body: text } }));
  await receive('متن');

  //  ۲) عکس: پیش‌نمایش و کیفیتِ عادی بارگذاری می‌شوند، پیام می‌رود؛ گیرنده اول فقط پیش‌نمایش را می‌گیرد
  const thumbBytes = jpegOf(SAMPLE.thumb, 4);
  const imageBytes = jpegOf(SAMPLE.image, 5);
  const t = row('پیش‌نمایش — بارگذاری', 'فرستنده', await wire('POST', `${cPath}/media?role=thumb`, { headers: C, raw: thumbBytes }), thumbBytes.length);
  const i = row('عکسِ عادی — بارگذاری', 'فرستنده', await wire('POST', `${cPath}/media`, { headers: C, raw: imageBytes }), imageBytes.length);
  row('عکس — ارسالِ پیام', 'فرستنده', await wire('POST', `${cPath}/messages`, { headers: C, json: { clientId: cid(), kind: 'image', mediaId: i.json.mediaId, thumbId: t.json.mediaId, meta: { width: 1600, height: 1200 } } }));
  const img = await receive('عکس');
  const gotThumb = row('پیش‌نمایش — دریافت', 'گیرنده', await wire('GET', `${sPath}/media/${img.media.thumbId}`, { headers: S }), thumbBytes.length);
  assert.equal(gotThumb.bytes.length, thumbBytes.length);
  const gotImage = row('عکسِ عادی — دریافت (با زدن)', 'گیرنده', await wire('GET', `${sPath}/media/${img.media.id}`, { headers: S }), imageBytes.length);
  assert.deepEqual(gotImage.bytes, imageBytes, 'عکس سالم رسید');

  //  ۳) صدا: ۳۰ ثانیه با ۲۴kbps
  const voiceBytes = oggOf(Math.round((SAMPLE.voiceSeconds * SAMPLE.voiceKbps * 1000) / 8));
  const v = row('صدا — بارگذاری', 'فرستنده', await wire('POST', `${cPath}/media`, { headers: C, raw: voiceBytes }), voiceBytes.length);
  row('صدا — ارسالِ پیام', 'فرستنده', await wire('POST', `${cPath}/messages`, { headers: C, json: { clientId: cid(), kind: 'audio', mediaId: v.json.mediaId, meta: { durationMs: SAMPLE.voiceSeconds * 1000 } } }));
  const voice = await receive('صدا');
  row('صدا — دریافت (با زدنِ پخش)', 'گیرنده', await wire('GET', `${sPath}/media/${voice.media.id}`, { headers: S }), voiceBytes.length);

  //  ۴) یک دورِ خالیِ long-poll (پاسخِ پایانِ مهلت همین اندازه است؛ مهلتِ واقعی ۲۵ ثانیه)
  const idle = row('دورِ خالیِ long-poll', 'هر دو', await wire('GET', `${sPath}/messages?after=${cursor}&wait=200`, { headers: S }));
  assert.equal(idle.json.messages.length, 0);

  //  گزارش
  const lines = ['| کار | طرف | بالا | پایین | جمع | رسانه | سربار |', '|---|---|---|---|---|---|---|'];
  for (const r of rows) lines.push(`| ${r.name} | ${r.side} | ${fmt(r.up)} | ${fmt(r.down)} | ${fmt(r.total)} | ${fmt(r.payload)} | ${fmt(r.overhead)} |`);
  const sum = (re) => rows.filter((r) => re.test(r.name)).reduce((k, r) => k + r.overhead, 0);
  const perHour = Math.round((3600 / 25) * idle.up + (3600 / 25) * idle.down);
  lines.push('', `دورِ خالیِ long-poll در ساعت (هر ۲۵ ثانیه): ${fmt(perHour)} بایت ≈ ${Math.round(perHour / KB)}KB برای هر گفت‌وگوی باز`);
  console.log(`\nCHAT-13 — بایتِ روی سیم (HTTP بی TLS):\n${lines.join('\n')}\n`);

  //  سقف‌های موقتِ سربار
  const over = [];
  const check = (name, value, cap) => { if (value > cap) over.push(`${name}: ${value} > ${cap}`); };
  check('متن — ارسال', sum(/^متن — ارسال$/), BUDGET.textSend);
  check('متن — دریافت + رسید', sum(/^متن — (دریافت|رسید)/), BUDGET.textReceive);
  for (const r of rows.filter((x) => x.payload && /بارگذاری/.test(x.name))) check(r.name, r.overhead, BUDGET.mediaUpload);
  for (const r of rows.filter((x) => x.payload && /دریافت/.test(x.name))) check(r.name, r.overhead, BUDGET.mediaDownload);
  check('دورِ خالیِ long-poll', idle.up + idle.down, BUDGET.idlePoll);
  assert.deepEqual(over, [], `سربار از سقفِ موقت گذشت:\n${over.join('\n')}`);
  //  رسانهٔ گیرنده فقط با زدن: پیامِ عکس خودش بایتِ عکس را نمی‌آورد
  const imgMsg = rows.find((r) => r.name === 'عکس — دریافت (long-poll)');
  assert.ok(imgMsg.total < SAMPLE.thumb, 'پیامِ عکس در long-poll بی بایتِ رسانه است (دانلودِ خودکار نه)');
});
