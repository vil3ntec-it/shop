'use strict';
/**
 * صفِ پیام‌های متنیِ نرفتهٔ ویترین (`public/store/chat-out.js`، CHAT-02 در وب) — منطقِ خالص، همان قاعده‌های
 * `ChatOutbox` ِ برنامهٔ خرید (`core/shopper/ShopperChat.kt`). رفتارِ صفحه (حباب، «دوباره»، «لغو»، بستنِ برگه) در
 * `web-store-chat.mjs` با کرومیومِ واقعی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Q = require('../public/store/chat-out.js');
const { FIRST_LOAD } = require('./storefront-budget');

const SLUG = 'chat-store';
const PHONE = '+93791112244';
const seq = (() => { let i = 0; return () => ((i = (i * 7 + 3) % 97) / 97); })();
const net = { status: 0, code: 'network' };
function q0() { return Q.forPhone(Q.empty(''), PHONE); }
function put(s, convId, body, now = 1000) { const r = Q.add(s, { slug: SLUG, convId, body }, now, seq); assert.ok(r.state, JSON.stringify(r)); return r; }

test('یک clientId تا رسیدن: همان شناسه پس از خطا، «دوباره»، بستنِ برگه و بارِ دوباره؛ شناسه با الگوی سرور', () => {
  let { state: s, item } = put(q0(), 'c1', 'سلام');
  assert.match(item.clientId, /^[A-Za-z0-9_.:-]{8,128}$/);
  const id = item.clientId;
  s = Q.sending(s, id);
  s = Q.failed(s, id, net, 2000, 0.5);
  assert.equal(Q.find(s, id).clientId, id);
  s = Q.retry(s, id);
  s = Q.sending(s, id);
  //  برگه وسطِ ارسال بسته شد ⇒ از دیسک: دوباره در صف، همان شناسه
  const back = Q.load(Q.dump(s));
  assert.equal(back.items.length, 1);
  assert.deepEqual([back.items[0].clientId, back.items[0].state], [id, Q.QUEUED]);
  assert.equal(Q.due(back, SLUG, 0).clientId, id);
  //  دو پیام در یک میلی‌ثانیه ⇒ دو شناسهٔ جدا و ترتیبِ نوشتن
  const a = put(Q.empty(PHONE), 'c1', 'یک', 5000);
  const b = put(a.state, 'c1', 'دو', 5000);
  assert.notEqual(a.item.clientId, b.item.clientId);
  assert.ok(b.item.createdAt > a.item.createdAt);
});

test('فقط سرِ صفِ هر گفت‌وگو می‌رود؛ گفت‌وگوی دیگر جدا؛ «نرفته» پشتِ سری‌ها را گیر نمی‌اندازد', () => {
  let s = q0();
  const one = put(s, 'c1', 'اول'); s = one.state;
  const two = put(s, 'c1', 'دوم'); s = two.state;
  const other = put(s, 'c2', 'گفت‌وگوی دیگر'); s = other.state;
  assert.equal(Q.next(s, 'c1', 0).clientId, one.item.clientId);
  s = Q.sending(s, one.item.clientId);
  assert.equal(Q.next(s, 'c1', 0), null, 'پیامِ در راه سرِ صف را نگه می‌دارد');
  assert.equal(Q.due(s, SLUG, 0).clientId, other.item.clientId, 'گفت‌وگوی دیگر منتظرِ این یکی نیست');
  //  خطای قطعی (مثلاً رشتهٔ بسته) ⇒ «نرفت» و پیامِ بعدی می‌رود
  s = Q.failed(s, one.item.clientId, { status: 409, code: 'chat_closed' }, 0, 0);
  assert.equal(Q.find(s, one.item.clientId).state, Q.FAILED);
  assert.equal(Q.next(s, 'c1', 0).clientId, two.item.clientId);
  //  ویترینِ دیگر صفِ این ویترین را نمی‌فرستد
  assert.equal(Q.due(s, 'another-store', 0), null);
});

test('backoff ِ نمایی با لرزش از ۲ تا ۶۰ ثانیه؛ پیشِ از وقت نمی‌رود؛ `online` همین حالا', () => {
  for (let n = 1; n <= 30; n++) {
    for (const r of [0, 0.5, 1]) {
      const d = Q.delayMs(n, r);
      assert.ok(d >= 2000 && d <= 60000, `تلاشِ ${n}: ${d}`);
    }
  }
  assert.equal(Q.delayMs(1, 1), 2000);
  assert.equal(Q.delayMs(2, 1), 4000);
  assert.equal(Q.delayMs(3, 1), 8000);
  assert.equal(Q.delayMs(10, 1), 60000);
  assert.ok(Q.delayMs(10, 0) >= 30000, 'لرزشِ نیمه');

  let { state: s, item } = put(q0(), 'c1', 'بی‌اینترنت');
  for (const e of [net, { status: 429 }, { status: 503 }, { status: 408 }]) assert.equal(Q.failureOf(e), 'retry', JSON.stringify(e));
  s = Q.failed(Q.sending(s, item.clientId), item.clientId, net, 10_000, 1);
  const x = Q.find(s, item.clientId);
  assert.deepEqual([x.state, x.attempts, x.nextAt], [Q.WAITING, 1, 12_000]);
  assert.equal(Q.next(s, 'c1', 11_999), null, 'پیش از وقت نه');
  assert.equal(Q.next(s, 'c1', 12_000).clientId, item.clientId);
  assert.equal(Q.wakeAt(s, SLUG), 12_000);
  s = Q.failed(Q.sending(s, item.clientId), item.clientId, net, 12_000, 1);
  assert.equal(Q.find(s, item.clientId).nextAt, 12_000 + 4000, 'تلاشِ دوم دو برابر');
  s = Q.online(s);
  assert.equal(Q.next(s, 'c1', 12_001).clientId, item.clientId, 'اینترنت برگشت ⇒ همین حالا');
  assert.equal(Q.wakeAt(Q.done(s, item.clientId), SLUG), null, 'چیزی منتظرِ زمان نیست ⇒ بیدار شدنِ بی‌پایان نه');
});

test('۴۰۱ همهٔ صف را تا تأییدِ دوبارهٔ شماره نگه می‌دارد؛ همان شماره ⇒ همان پیام‌ها دوباره می‌روند', () => {
  let s = q0();
  const a = put(s, 'c1', 'یک'); s = a.state;
  const b = put(s, 'c2', 'دو'); s = b.state;
  assert.equal(Q.failureOf({ status: 401, code: 'verification_required' }), 'verify');
  s = Q.failed(Q.sending(s, a.item.clientId), a.item.clientId, { status: 401, code: 'verification_required' }, 0, 0);
  assert.equal(Q.find(s, a.item.clientId).state, Q.VERIFY);
  assert.equal(Q.due(s, SLUG, Infinity), null, 'هیچ گفت‌وگویی بی بلیت نمی‌رود');
  assert.equal(Q.next(s, 'c2', Infinity), null);
  //  بی بلیت باز شد (شماره هنوز تأیید نشده) ⇒ صف دست نمی‌خورد
  assert.deepEqual(Q.forPhone(s, null), s);
  s = Q.reverified(s, PHONE);
  assert.equal(s.items.length, 2);
  assert.equal(Q.due(s, SLUG, 0).clientId, a.item.clientId, 'همان پیام، همان شناسه');
});

test('شمارهٔ دیگر روی همین دستگاه تأیید شد ⇒ صف پاک؛ برچسبِ شماره روی دیسک', () => {
  let s = put(q0(), 'c1', 'پیامِ نفرِ اول').state;
  const disk = JSON.parse(Q.dump(s));
  assert.equal(disk.phone, PHONE);
  s = Q.load(Q.dump(s));
  assert.equal(s.phone, PHONE);
  const other = Q.reverified(s, '+93790000000');
  assert.deepEqual([other.phone, other.items.length], ['+93790000000', 0]);
  assert.equal(Q.forPhone(s, '+93790000000').items.length, 0);
  assert.equal(Q.forPhone(s, PHONE).items.length, 1);
});

test('«دوباره» و «لغو»؛ پیامِ در راه لغو نمی‌شود و «دوباره» ِ آن بی‌اثر است', () => {
  let s = q0();
  const a = put(s, 'c1', 'لغو شو'); s = a.state;
  s = Q.failed(Q.sending(s, a.item.clientId), a.item.clientId, { status: 400, code: 'empty_message' }, 0, 0);
  s = Q.retry(s, a.item.clientId);
  const r = Q.find(s, a.item.clientId);
  assert.deepEqual([r.state, r.attempts, r.nextAt, r.error], [Q.QUEUED, 0, 0, '']);
  const inFlight = Q.sending(s, a.item.clientId);
  const no = Q.cancel(inFlight, a.item.clientId);
  assert.equal(no.ok, false);
  assert.equal(no.state.items.length, 1, 'در راه ⇒ می‌ماند');
  assert.equal(Q.find(Q.retry(inFlight, a.item.clientId), a.item.clientId).state, Q.SENDING);
  const yes = Q.cancel(s, a.item.clientId);
  assert.deepEqual([yes.ok, yes.state.items.length], [true, 0]);
  assert.equal(Q.cancel(yes.state, 'cxxxxxxxxxxxx').ok, false);
});

test('روی دیسک فقط متن، با کلیدِ store-chat-out؛ ردیفِ خراب، تکراری یا با فیلدِ اضافه کنار', () => {
  assert.equal(Q.KEY, 'store-chat-out');
  let s = put(q0(), 'c1', 'متن').state;
  const raw = JSON.parse(Q.dump(s));
  assert.deepEqual(Object.keys(raw.items[0]).sort(), ['attempts', 'body', 'clientId', 'convId', 'createdAt', 'error', 'nextAt', 'replyTo', 'slug', 'state']);
  const bad = Q.load(JSON.stringify({ phone: PHONE, items: [
    raw.items[0], raw.items[0],
    { ...raw.items[0], clientId: 'x' },
    { ...raw.items[0], clientId: 'c-other-0000000', convId: '../x' },
    { ...raw.items[0], clientId: 'c-other-0000001', body: '' },
    { ...raw.items[0], clientId: 'c-other-0000002', body: 'x'.repeat(4001) },
    { ...raw.items[0], clientId: 'c-other-0000003', kind: 'image', mediaId: 'm1', bytes: 'AAAA' },
  ] }));
  assert.equal(bad.items.length, 2);
  assert.ok(bad.items.every((x) => !('mediaId' in x) && !('bytes' in x) && !('kind' in x)));
  for (const junk of ['', 'نه JSON', 'null', '[]', '{"items":5}', null, undefined]) assert.deepEqual(Q.load(junk).items, []);
});

test('پاسخ (CHAT-14): فقط شناسهٔ پیامِ اصل با پیام در صف می‌ماند و از دیسک برمی‌گردد؛ شناسهٔ بد رد', () => {
  const r = Q.add(q0(), { slug: SLUG, convId: 'c1', body: 'بله، همان', replyTo: 'msg_abc123' }, 1, seq);
  assert.equal(r.item.replyTo, 'msg_abc123');
  assert.equal(Q.load(Q.dump(r.state)).items[0].replyTo, 'msg_abc123');
  assert.equal(put(q0(), 'c1', 'بی پاسخ').item.replyTo, null);
  for (const bad of ['../x', 'a b', 'x'.repeat(65), 5]) assert.deepEqual(Q.add(q0(), { slug: SLUG, convId: 'c1', body: 'سلام', replyTo: bad }, 1, seq), { error: 'bad' }, String(bad));
  const disk = JSON.parse(Q.dump(r.state));
  disk.items[0].replyTo = '../../x';
  assert.equal(Q.load(JSON.stringify(disk)).items.length, 0, 'ردیفِ دست‌خورده کنار');
});

test('حداکثر ۵۰ پیام؛ پُر ⇒ کهنه‌ترین «نرفته» جا باز می‌کند، وگرنه رد (پیامِ در صف بی‌صدا نمی‌رود)', () => {
  let s = q0();
  const ids = [];
  for (let i = 0; i < Q.MAX; i++) { const r = put(s, 'c1', `پیام ${i}`, 1000 + i); s = r.state; ids.push(r.item.clientId); }
  assert.equal(s.items.length, 50);
  assert.deepEqual(Q.add(s, { slug: SLUG, convId: 'c1', body: 'پنجاه و یکم' }, 9999, seq), { error: 'full' });
  s = Q.failed(s, ids[7], { status: 409, code: 'chat_closed' }, 0, 0);
  const r = Q.add(s, { slug: SLUG, convId: 'c1', body: 'پنجاه و یکم' }, 9999, seq);
  assert.equal(r.state.items.length, 50);
  assert.equal(Q.find(r.state, ids[7]), null, 'نرفته جا داد');
  assert.ok(Q.find(r.state, ids[0]), 'پیامِ در صف ماند');
  //  دیسکِ بیش از سقف ⇒ تازه‌ترین ۵۰
  const many = { phone: PHONE, items: Array.from({ length: 60 }, (_, i) => ({ ...JSON.parse(Q.dump(s)).items[0], clientId: `c-many-${String(i).padStart(4, '0')}`, createdAt: i + 1 })) };
  assert.equal(Q.load(JSON.stringify(many)).items.length, 50);
});

test('متنِ تهی یا بلندتر از ۴۰۰۰ نویسه رد، هرگز بریده؛ همان پاک‌سازیِ سرور', () => {
  const s = q0();
  for (const b of ['', '   ', '\n\t', '\u0000\u0007']) assert.deepEqual(Q.add(s, { slug: SLUG, convId: 'c1', body: b }, 1, seq), { error: 'empty' }, JSON.stringify(b));
  assert.deepEqual(Q.add(s, { slug: SLUG, convId: 'c1', body: 'ب'.repeat(4001) }, 1, seq), { error: 'too_long' });
  const full = put(s, 'c1', 'ب'.repeat(4000));
  assert.equal(full.item.body.length, 4000);
  assert.equal(put(s, 'c1', '  سلام\u0007  ').item.body, 'سلام');
  assert.deepEqual(Q.add(s, { slug: SLUG, convId: '../x', body: 'سلام' }, 1, seq), { error: 'bad' });
});

test('chat-out.js دیربار است: نه در بارِ نخست و نه در store.js؛ فقط از chat.js', () => {
  const pub = path.join(__dirname, '..', 'public');
  const first = ['store/store.js', 'kit/kit.js', 'kit/boot.js'].map((f) => fs.readFileSync(path.join(pub, f), 'utf8'));
  assert.ok(first.every((t) => !t.includes('chat-out')));
  assert.ok(fs.readFileSync(path.join(pub, 'store', 'chat.js'), 'utf8').includes("K.js('/store/chat-out.js')"));
  assert.ok(first.reduce((n, t) => n + Buffer.byteLength(t), 0) < FIRST_LOAD);
});
