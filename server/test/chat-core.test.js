'use strict';
/**
 * هستهٔ مشترکِ چت در مرورگر (`license/chat-core.js` = `server/public/store/chat-core.js`).
 *
 *   ۱. دو نسخه (وبِ دکان و ویترین) بایت‌به‌بایت یکی‌اند.
 *   ۲. backoff: نمایی با لرزشِ نیمه، سقفِ ۵ دقیقه.
 *   ۳. صف: ترتیب در هر گفت‌وگو، خطای گذرا/قطعی/هویت، «دوباره» و «لغو»، بازگشت پس از بسته شدن.
 *   ۴. drain: قطعِ شبکه ⇒ همان شناسه می‌ماند؛ پس از وصل فقط یک بار با همان clientId (CHAT-02/11).
 *   ۵. Thread: ادغام با rev، «حذف برای من» پنهان، رسید فقط از عددِ سرور (CHAT-04).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const WEB = path.join(__dirname, '..', '..', 'license', 'chat-core.js');
const STORE = path.join(__dirname, '..', 'public', 'store', 'chat-core.js');
const C = require(WEB);

const memory = () => { let saved = null; return { load: () => saved, save: (x) => { saved = JSON.parse(JSON.stringify(x)); }, peek: () => saved }; };
const clock = (t0 = 1_000_000) => { let t = t0; const f = () => t; f.add = (ms) => { t += ms; }; return f; };

test('دو نسخهٔ هسته (وب و ویترین) بایت‌به‌بایت یکی‌اند', () => {
  assert.equal(fs.readFileSync(STORE, 'utf8'), fs.readFileSync(WEB, 'utf8'));
});

test('backoff: ۲، ۴، ۸… ثانیه با لرزشِ نیمه و سقفِ ۵ دقیقه', () => {
  assert.equal(C.backoffMs(1, () => 0), 1000);
  assert.equal(C.backoffMs(1, () => 0.999999), 2000);
  assert.equal(C.backoffMs(2, () => 0), 2000);
  assert.equal(C.backoffMs(3, () => 0.5), 6000);
  for (const n of [9, 10, 50, 1e6]) {
    const v = C.backoffMs(n, () => 0.999999);
    assert.ok(v <= 300000 && v >= 150000, `n=${n} ⇒ ${v}`);
  }
});

test('clientId با الگوی سرور جور است و تکراری نیست', () => {
  const ids = new Set();
  for (let i = 0; i < 500; i++) {
    const id = C.newClientId();
    assert.match(id, /^[A-Za-z0-9_.:-]{8,128}$/);
    ids.add(id);
  }
  assert.equal(ids.size, 500);
});

test('دسته‌بندیِ خطا: بی شبکه/۴۲۹/۵xx گذرا، ۴۰۱ هویت، بقیه قطعی', () => {
  assert.equal(C.classify({ status: 0 }), 'retry');
  assert.equal(C.classify(new Error('x')), 'retry');
  for (const s of [408, 425, 429, 500, 502, 503]) assert.equal(C.classify({ status: s }), 'retry', String(s));
  assert.equal(C.classify({ status: 401 }), 'auth');
  for (const s of [400, 403, 404, 409, 413, 415]) assert.equal(C.classify({ status: s }), 'fail', String(s));
});

test('صف: ترتیبِ هر گفت‌وگو؛ سرِ صفِ منتظر پشتی را نگه می‌دارد؛ خطای قطعی نه', () => {
  const now = clock();
  const store = memory();
  const q = new C.Queue({ ...store, now, rand: () => 0 });
  const a = q.add('c1', { body: 'یک' });
  const b = q.add('c1', { body: 'دو' });
  const x = q.add('c2', { body: 'دیگری' });
  assert.ok(a.createdAt < b.createdAt, 'هم‌زمان هم ترتیب دارد');
  assert.equal(q.next('c1').clientId, a.clientId);
  assert.equal(q.next('c2').clientId, x.clientId);

  q.sending(a);
  assert.equal(q.next('c1'), null, 'یکی‌یکی');
  q.failed(a, { status: 0, message: 'بی شبکه' });
  assert.equal(a.state, 'waiting');
  assert.equal(a.nextAt, now() + 1000);
  assert.equal(q.next('c1'), null, 'دومی پیش از اولی نمی‌رود');
  now.add(1000);
  assert.equal(q.next('c1').clientId, a.clientId);

  q.sending(a);
  q.failed(a, { status: 413, message: 'بزرگ' });
  assert.equal(a.state, 'failed');
  assert.equal(a.error, 'بزرگ');
  assert.equal(q.next('c1').clientId, b.clientId, 'پیامِ نرفته صف را قفل نمی‌کند');

  assert.equal(q.retry(a.clientId).state, 'queued');
  assert.equal(q.next('c1').clientId, a.clientId, '«دوباره» همان شناسه');
  q.sending(a);
  assert.equal(q.cancel(a.clientId), null, 'در راه لغو نمی‌شود');
  q.failed(a, { status: 401 });
  assert.equal(a.state, 'auth');
  assert.equal(q.next('c1'), null, 'منتظرِ هویت صف را نگه می‌دارد');
  q.resumeAuth();
  assert.equal(q.next('c1').clientId, a.clientId);
  assert.deepEqual(q.cancel(a.clientId), []);
  assert.equal(q.get(a.clientId), null);
});

test('صف: بسته شدنِ برگه وسطِ ارسال ⇒ همان شناسه دوباره در صف؛ رسانهٔ محلی با «لغو» پاک می‌شود', () => {
  const store = memory();
  const q1 = new C.Queue(store);
  const a = q1.add('c1', { kind: 'image', blobKey: 'k1', thumbKey: 'k2' });
  q1.sending(a);
  const q2 = new C.Queue(store);
  const back = q2.get(a.clientId);
  assert.equal(back.state, 'queued');
  assert.equal(back.clientId, a.clientId);
  assert.deepEqual(q2.cancel(a.clientId), ['k1', 'k2']);
  assert.equal(new C.Queue(store).items.length, 0);
});

test('drain: آفلاین ⇒ می‌ماند؛ آنلاین ⇒ به ترتیب و هر پیام فقط یک بار با همان clientId', async () => {
  const now = clock();
  const q = new C.Queue({ ...memory(), now, rand: () => 0 });
  q.add('c1', { body: 'یک' });
  q.add('c1', { body: 'دو' });
  const server = new Map();
  const calls = [];
  let online = false;
  const send = async (item) => {
    calls.push(item.clientId);
    if (!online) { const e = new Error('اتصال برقرار نشد'); e.status = 0; throw e; }
    if (!server.has(item.clientId)) server.set(item.clientId, { id: `m${server.size + 1}`, body: item.body, clientId: item.clientId });
    return server.get(item.clientId);
  };
  await C.drain(q, 'c1', send);
  assert.equal(calls.length, 1, 'سرِ صف یک بار تلاش کرد و صف ایستاد');
  assert.equal(q.items.length, 2);
  assert.equal(q.wake(), true);
  online = true;
  const sent = [];
  await C.drain(q, 'c1', send, { onSent: (item, m) => sent.push(m.body) });
  assert.deepEqual(sent, ['یک', 'دو']);
  assert.equal(server.size, 2);
  assert.equal(q.items.length, 0);
  assert.equal(calls[0], calls[1], 'تلاشِ دوباره با همان شناسه');
});

test('reconcile: پیامی که سرور دارد دیگر از صف نشان داده نمی‌شود', () => {
  const q = new C.Queue(memory());
  const a = q.add('c1', { body: 'x', blobKey: 'b' });
  q.add('c1', { body: 'y' });
  assert.deepEqual(q.reconcile('c1', [{ id: 'm1', clientId: a.clientId }]), ['b']);
  assert.equal(q.items.length, 1);
});

test('Thread: ادغام با rev، ویرایش جایگزین، «حذف برای من» پنهان؛ رسید فقط از سرور', () => {
  const t = new C.Thread();
  const conv = (r) => ({ id: 'c1', receipts: { peerDeliveredSeq: 0, peerReadSeq: 0, myDeliveredSeq: 0, myReadSeq: 0, ...r } });
  let r = t.apply({ messages: [{ id: 'a', seq: 1, rev: 1, sender: 'shop' }, { id: 'b', seq: 2, rev: 2, sender: 'customer' }], cursor: 2, conversation: conv({}) });
  assert.equal(r.changed, true);
  assert.equal(t.cursor, 2);
  assert.deepEqual(t.receiptNeed(false), { deliveredSeq: 2, readSeq: 0 });
  assert.deepEqual(t.receiptNeed(true), { deliveredSeq: 2, readSeq: 2 });
  r = t.apply({ messages: [], cursor: 2, conversation: conv({ myDeliveredSeq: 2, myReadSeq: 2 }) });
  assert.equal(t.receiptNeed(true), null, 'تکرار نمی‌شود');
  r = t.apply({ messages: [{ id: 'a', seq: 1, rev: 3, sender: 'shop', body: 'ویرایش' }, { id: 'b', seq: 2, rev: 4, hidden: true }], cursor: 4 });
  assert.deepEqual(t.list().map((m) => m.id), ['a']);
  assert.equal(t.list()[0].body, 'ویرایش');
  assert.equal(t.cursor, 4);
  const mine = { seq: 3 };
  assert.equal(C.statusOf(mine, { peerDeliveredSeq: 2, peerReadSeq: 1 }), 'sent');
  assert.equal(C.statusOf(mine, { peerDeliveredSeq: 3, peerReadSeq: 1 }), 'delivered');
  assert.equal(C.statusOf(mine, { peerDeliveredSeq: 3, peerReadSeq: 3 }), 'read');
  assert.equal(C.statusOf({ seq: 1, deleted: true }, {}), '');
});

test('fit: کوچک‌سازی با همان نسبت و هرگز بزرگ‌تر', () => {
  assert.deepEqual(C.fit(4000, 3000, 1600), { w: 1600, h: 1200 });
  assert.deepEqual(C.fit(3000, 4000, 320), { w: 240, h: 320 });
  assert.deepEqual(C.fit(200, 100, 1600), { w: 200, h: 100 });
});

test('voiceType: بی MediaRecorder یا بی میکروفون ⇒ خالی (دکمه پنهان)', () => {
  assert.equal(C.voiceType(null), '');
  assert.equal(C.voiceType({ isTypeSupported: () => true }), '', 'Node میکروفون ندارد');
});
