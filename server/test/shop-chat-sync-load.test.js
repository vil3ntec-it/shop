'use strict';
/**
 * CHAT-15 (v3 §۳۳.۴ و §۳۳.۲ بندِ ۷): بارِ هم‌زمانِ گفت‌وگوها، قطعِ اتصالِ
 * زنده، وصلِ دوباره و همگام‌سازیِ طولانی — بی گم شدنِ پیام و بی شکستنِ ترتیب.
 *
 * انتقالِ زندهٔ چت long-poll است (`wait`)؛ منبعِ پیام همیشه `after=<rev>` از
 * دیتابیس است و سوکت/long-poll فقط شتاب (#259، #299). این پرونده قفل می‌کند:
 *   ۱) پیامی که درست میانِ «خواندنِ خالی» و «شروعِ صبر» برسد، long-poll را
 *      بیدار می‌کند (بیدارباشِ گم‌شده نه؛ وگرنه تا پایانِ مهلت دیر می‌رسید).
 *   ۲) قطعِ اتصال وسطِ long-poll، صبرِ سرور را همان دم آزاد می‌کند و وصلِ
 *      دوباره با `after` همهٔ پیام‌های دورانِ قطع را به ترتیب می‌گیرد.
 *   ۳) ده گفت‌وگو و دو طرف هم‌زمان، با تکرارِ هم‌زمانِ همان پیام: seq ِ پیوسته،
 *      ترتیبِ هر فرستنده، و خواننده‌های long-poll همه را بی کم و زیاد می‌گیرند.
 *   ۴) دستگاهِ آفلاین ۴۵۰ پیام را صفحه‌به‌صفحه می‌گیرد در حالی که پیامِ تازه،
 *      ویرایش و «حذف برای همه» هم‌زمان می‌رسند: نمای نهایی = نمای سرور.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const db = require('../src/db');

//  ⛔ پیش از بار شدنِ `lib/shop-chat` (که `many` را هنگامِ بار شدن برمی‌دارد):
//  قلابِ آزمون پس از «خواندنِ تغییرها» — برای ساختنِ دقیقِ پنجرهٔ بیدارباشِ گم‌شده.
const realMany = db.many;
let afterChangesRead = null;
db.many = async (text, params) => {
  const rows = await realMany(text, params);
  const hook = afterChangesRead;
  if (hook && /m\.rev > \$2 ORDER BY m\.rev ASC/.test(text) && params[0] === hook.conv && rows.length === 0) {
    afterChangesRead = null;
    await hook.run();
  }
  return rows;
};

const { query, newId, now } = db;
const chat = require('../src/lib/shop-chat');

let admin = '';
let base = '';
const shops = {};
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

test.before(async () => {
  base = await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  //  ⛔ همهٔ دکان‌ها همین‌جا با HTTP: آزمون‌های سنگینِ بعدی چند ثانیه فقط دیتابیس
  //  می‌زنند و اتصالِ keep-alive ِ بیکار (۵ ثانیهٔ سرورِ آزمون) را نباید پس از آن
  //  دوباره به کار گرفت (ECONNRESET ِ مهار، نه رفتارِ چت).
  for (const name of ['قطع و وصل', 'بیدارباش', 'بار', 'همگام طولانی']) shops[name] = await chatShop(name);
});
test.after(async () => { db.many = realMany; await h.stop(); });

async function chatShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'shop.chat', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { token: u.accessToken, shopId, userId: u.user.id, viewer: { side: 'shop', shopId, userId: u.user.id, role: 'owner' } };
}

const customer = (shopId, phone) => ({ side: 'customer', shopId, phone });

/** تا شرط درست شود (یا مهلت تمام) — بی خوابِ ثابتِ کور. */
async function until(fn, ms = 3000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (await fn()) return true;
    await sleep(10);
  }
  return fn();
}

/** همهٔ پیام‌های سرور از دیدِ بیننده — صفحه‌به‌صفحه تا ته. */
async function serverView(viewer, conv) {
  const out = new Map();
  let after = 0;
  for (;;) {
    const page = await chat.changes(viewer, conv, { after, limit: 200 });
    for (const m of page.messages) out.set(m.id, m);
    after = page.cursor;
    if (!page.hasMore) return out;
  }
}

test('CHAT-15: قطعِ اتصال وسطِ long-poll صبرِ سرور را آزاد می‌کند؛ وصلِ دوباره با after همهٔ پیام‌های دورانِ قطع را به ترتیب می‌گیرد', async () => {
  const o = shops['قطع و وصل'];
  const me = customer(o.shopId, '+93790000202');
  const conv = (await chat.open(me, {})).conversation.id;
  await chat.send(me, conv, { clientId: cid(), body: 'صفر' });
  const cursor = (await chat.changes(o.viewer, conv, { after: 0 })).cursor;

  const ctl = new AbortController();
  const poll = fetch(`${base}/api/shop/chat/conversations/${conv}/messages?after=${cursor}&wait=20000`, {
    headers: { Authorization: `Bearer ${o.token}` }, signal: ctl.signal,
  }).catch((err) => err);
  assert.ok(await until(() => chat.waiting() === 1), 'long-poll در سرور منتظر ماند');
  ctl.abort();
  assert.ok((await poll) instanceof Error, 'درخواستِ کلاینت قطع شد');
  assert.ok(await until(() => chat.waiting() === 0, 1500), 'قطعِ اتصال صبرِ سرور را تا پایانِ ۲۰ ثانیه نگه نمی‌دارد');

  //  دورانِ قطع: سه پیام از مشتری، یکی از دکان
  for (const b of ['یک', 'دو', 'سه']) await chat.send(me, conv, { clientId: cid(), body: b });
  await chat.send(o.viewer, conv, { clientId: cid(), body: 'چهار' });
  const back = await h.get(`/api/shop/chat/conversations/${conv}/messages?after=${cursor}`, { token: o.token });
  assert.equal(back.status, 200);
  assert.deepEqual(back.body.messages.map((m) => [m.seq, m.body]), [[2, 'یک'], [3, 'دو'], [4, 'سه'], [5, 'چهار']]);
  assert.equal(back.body.hasMore, false);
  //  long-poll ِ تازه پس از وصلِ دوباره هم همان دم با پیامِ بعدی بیدار می‌شود
  const wait = h.get(`/api/shop/chat/conversations/${conv}/messages?after=${back.body.cursor}&wait=10000`, { token: o.token });
  assert.ok(await until(() => chat.waiting() === 1));
  const t0 = Date.now();
  await chat.send(me, conv, { clientId: cid(), body: 'پنج' });
  const next = await wait;
  assert.deepEqual(next.body.messages.map((m) => [m.seq, m.body]), [[6, 'پنج']]);
  assert.ok(Date.now() - t0 < 2000);
  assert.equal(chat.waiting(), 0);
});

test('CHAT-15: پیامی که میانِ «خواندنِ خالی» و «شروعِ صبر» برسد long-poll را همان دم بیدار می‌کند', async () => {
  const o = shops['بیدارباش'];
  const me = customer(o.shopId, '+93790000201');
  const conv = (await chat.open(me, {})).conversation.id;
  await chat.send(me, conv, { clientId: cid(), body: 'پیشین' });
  const start = await chat.changes(o.viewer, conv, { after: 0 });
  assert.equal(start.messages.length, 1);

  //  پیامِ تازه درست پس از خواندنِ خالیِ long-poll و پیش از آن‌که صبر شروع شود
  afterChangesRead = { conv, run: () => chat.send(me, conv, { clientId: cid(), body: 'در شکاف' }) };
  const t0 = Date.now();
  const got = await chat.changes(o.viewer, conv, { after: start.cursor, wait: 4000 });
  const took = Date.now() - t0;
  assert.equal(afterChangesRead, null, 'قلاب اجرا شد');
  assert.deepEqual(got.messages.map((m) => m.body), ['در شکاف']);
  assert.ok(took < 1500, `پیامِ در شکاف باید بی‌درنگ برسد، نه پس از مهلتِ ۴ ثانیه (${took}ms)`);
  assert.equal(chat.waiting(), 0, 'هیچ صبرِ آویزانی نماند');
});

test('CHAT-15: ده گفت‌وگو و دو طرف هم‌زمان با تکرارِ هم‌زمانِ همان پیام — seq ِ پیوسته، ترتیبِ هر فرستنده و خواننده‌های long-poll بی کم و زیاد', async () => {
  const o = shops['بار'];
  const CONVS = 10;
  const PER_SIDE = 12;
  const convs = [];
  for (let i = 0; i < CONVS; i++) {
    const me = customer(o.shopId, `+9379000${String(3000 + i)}`);
    convs.push({ me, id: (await chat.open(me, {})).conversation.id });
  }
  const total = 2 * PER_SIDE;

  //  خواننده: long-poll ِ دکان روی هر گفت‌وگو، با cursor، تا همه را بگیرد
  const readers = convs.map(async ({ id }) => {
    const seen = new Map();
    const revs = new Set();
    let after = 0;
    const end = Date.now() + 30_000;
    while (seen.size < total && Date.now() < end) {
      const page = await chat.changes(o.viewer, id, { after, wait: 2000 });
      assert.ok(page.cursor >= after, 'cursor عقب نمی‌رود');
      for (const m of page.messages) {
        assert.ok(!revs.has(`${m.id}@${m.rev}`), 'یک نسخه از یک پیام دو بار نمی‌رسد');
        revs.add(`${m.id}@${m.rev}`);
        seen.set(m.id, m);
      }
      after = page.cursor;
    }
    return seen;
  });

  //  نویسنده‌ها: هر طرفِ هر گفت‌وگو پشتِ سرِ هم (مثلِ سرِ صفِ ChatQueue)؛
  //  هر پیامِ سوم هم‌زمان دو بار (پاسخِ گم‌شده + تلاشِ دوباره)
  const writer = async (viewer, conv, tag) => {
    for (let k = 0; k < PER_SIDE; k++) {
      const body = { clientId: cid(), body: `${tag}-${k}` };
      if (k % 3 === 0) {
        const [a, b] = await Promise.all([chat.send(viewer, conv, body), chat.send(viewer, conv, body)]);
        assert.equal(a.message.id, b.message.id);
        assert.equal([a.replayed, b.replayed].filter(Boolean).length, 1, 'دقیقاً یکی تکرار است');
      } else {
        await chat.send(viewer, conv, body);
      }
    }
  };
  await Promise.all(convs.flatMap(({ me, id }) => [writer(me, id, 'c'), writer(o.viewer, id, 's')]));
  const got = await Promise.all(readers);

  for (let i = 0; i < CONVS; i++) {
    const truth = [...(await serverView(o.viewer, convs[i].id)).values()].sort((a, b) => a.seq - b.seq);
    assert.deepEqual(truth.map((m) => m.seq), Array.from({ length: total }, (_, k) => k + 1), 'seq پیوسته و بی تکرار');
    for (const tag of ['c', 's']) {
      const mine = truth.filter((m) => m.body.startsWith(`${tag}-`)).map((m) => m.body);
      assert.deepEqual(mine, Array.from({ length: PER_SIDE }, (_, k) => `${tag}-${k}`), `ترتیبِ فرستندهٔ ${tag} همان ترتیبِ نوشتن`);
    }
    assert.deepEqual([...got[i].keys()].sort(), truth.map((m) => m.id).sort(), 'خواننده دقیقاً همهٔ پیام‌ها را گرفت');
  }
  const n = Number((await query('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE shop_id=$1', [o.shopId])).rows[0].n);
  assert.equal(n, CONVS * total, 'تکرارِ هم‌زمان پیامِ دوم نساخت');
  assert.ok(await until(() => chat.waiting() === 0), 'هیچ long-poll ِ آویزانی نماند');
});

test('CHAT-15: همگام‌سازیِ طولانی — ۴۵۰ پیام صفحه‌به‌صفحه، هم‌زمان با پیامِ تازه، ویرایش و حذف؛ نمای دستگاه = نمای سرور', async () => {
  const o = shops['همگام طولانی'];
  const me = customer(o.shopId, '+93790000204');
  const conv = (await chat.open(me, {})).conversation.id;
  const OLD = 450;
  const ids = [];
  for (let k = 0; k < OLD; k++) ids.push((await chat.send(me, conv, { clientId: cid(), body: `کهنه-${k}` })).message.id);

  //  دستگاهِ دکان که مدتی آفلاین بود از after=0 با صفحه‌های ۱۰۰تایی می‌گیرد
  const device = new Map();
  let after = 0;
  let pages = 0;
  const churn = (async () => {
    for (let k = 0; k < 30; k++) {
      await chat.send(o.viewer, conv, { clientId: cid(), body: `تازه-${k}` });
      if (k < 5) await chat.edit(me, conv, ids[k * 7], { body: `ویرایش‌شده-${k}` });
      if (k >= 5 && k < 10) await chat.remove(me, conv, ids[k * 11], 'all');
      await sleep(1);
    }
  })();
  let last;
  do {
    last = await chat.changes(o.viewer, conv, { after, limit: 100 });
    pages++;
    for (const m of last.messages) {
      const prev = device.get(m.id);
      assert.ok(!prev || m.rev > prev.rev, 'نسخهٔ کهنه‌تر روی تازه‌تر نمی‌نشیند');
      device.set(m.id, m);
    }
    assert.ok(last.cursor >= after);
    after = last.cursor;
  } while (last.hasMore);
  await churn;
  //  پس از آرام شدن: یک دورِ دیگر تا ته
  do {
    last = await chat.changes(o.viewer, conv, { after, limit: 100 });
    for (const m of last.messages) device.set(m.id, m);
    after = last.cursor;
  } while (last.hasMore);

  assert.ok(pages >= 5, `صفحه‌بندی واقعاً چند صفحه بود (${pages})`);
  const truth = await serverView(o.viewer, conv);
  assert.equal(device.size, OLD + 30);
  const pick = (m) => [m.id, m.seq, m.rev, !!m.deleted, m.body || ''];
  assert.deepEqual([...device.values()].map(pick).sort((a, b) => a[1] - b[1]), [...truth.values()].map(pick).sort((a, b) => a[1] - b[1]));
  const ordered = [...device.values()].sort((a, b) => a.seq - b.seq);
  assert.deepEqual(ordered.map((m) => m.seq), Array.from({ length: OLD + 30 }, (_, k) => k + 1));
  assert.equal(ordered.filter((m) => m.deleted).length, 5, 'هر پنج حذف به دستگاهِ دیر رسیده رسید');
  assert.equal(ordered.filter((m) => /^ویرایش‌شده-/.test(m.body || '')).length, 5, 'هر پنج ویرایش رسید');
  assert.deepEqual(ordered.filter((m) => /^تازه-/.test(m.body || '')).map((m) => m.body), Array.from({ length: 30 }, (_, k) => `تازه-${k}`));
});
