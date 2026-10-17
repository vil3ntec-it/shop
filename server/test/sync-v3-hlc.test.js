'use strict';
/**
 * Sync v3 — ساعتِ HLC و نگهبانِ ساعتِ غلطِ دستگاه (SYN-05، بخشِ ۳.۸.۱ بندِ ۸).
 * خالص است: نه دیتابیس دارد نه شبکه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const hlc = require('../src/lib/hlc');

const T0 = 1_760_000_000_000;

test('قالبِ متنی ترتیبِ واقعی را نگه می‌دارد (مقایسهٔ رشته = مقایسهٔ زمان)', () => {
  const xs = [
    hlc.format({ wall: T0, counter: 0, node: 'b' }),
    hlc.format({ wall: T0, counter: 2, node: 'a' }),
    hlc.format({ wall: T0 + 1, counter: 0, node: 'a' }),
    hlc.format({ wall: 999, counter: 7, node: 'z' }),
    hlc.format({ wall: T0, counter: 10, node: 'a' }),
  ];
  const byString = [...xs].sort();
  const byTuple = [...xs].sort(hlc.compare);
  assert.deepEqual(byString, byTuple);
  assert.equal(byTuple[0], hlc.format({ wall: 999, counter: 7, node: 'z' }));
  assert.equal(byTuple[byTuple.length - 1], hlc.format({ wall: T0 + 1, counter: 0, node: 'a' }));
});

test('parse شکلِ ناقص را قانونی می‌کند و شکلِ نادرست را رد می‌کند', () => {
  const p = hlc.parse(`${T0}:3:dev-a`);
  assert.deepEqual(p, { wall: T0, counter: 3, node: 'dev-a' });
  assert.equal(hlc.canonical(`${T0}:3:dev-a`), `00${T0}:000003:dev-a`);
  for (const bad of ['', 'x', `${T0}:3`, `${T0}:-1:a`, `${T0}:1:`, `abc:1:a`, `${T0}:1:a b`,
    `${T0}:1234567:a`, `1${'0'.repeat(15)}:0:a`, null, 42, {}]) {
    assert.equal(hlc.parse(bad), null, `باید رد شود: ${JSON.stringify(bad)}`);
  }
});

test('tick همیشه بالا می‌رود، حتی اگر ساعتِ سرور عقب برود', () => {
  let t = T0;
  const clock = hlc.createClock({ node: 'server', now: () => t });
  const a = clock.tick();
  const b = clock.tick();
  t = T0 - 60_000;                      //  ساعتِ ماشین یک دقیقه عقب رفت
  const c = clock.tick();
  assert.ok(hlc.compare(a, b) < 0);
  assert.ok(hlc.compare(b, c) < 0);
  assert.equal(hlc.parse(c).wall, T0);   //  دیوار عقب نمی‌رود، شمارنده جلو می‌رود
});

test('recv: HLCِ دستگاهِ کمی جلوتر ساعتِ سرور را جلو می‌برد', () => {
  let t = T0;
  const clock = hlc.createClock({ node: 'server', now: () => t });
  const remote = hlc.format({ wall: T0 + 60_000, counter: 4, node: 'dev-a' });   //  یک دقیقه جلوتر
  const r = clock.recv(remote);
  assert.equal(r.skewed, false);
  assert.ok(hlc.compare(r.hlc, remote) > 0, 'ساعتِ سرور بعد از دیدن، بزرگ‌تر از آن است');
  assert.ok(hlc.compare(clock.tick(), remote) > 0);
});

test('recv: بیش از ۵ دقیقه جلوتر ⇒ «ساعتِ غلط»، و ساعتِ سرور جلو نمی‌رود', () => {
  let t = T0;
  const clock = hlc.createClock({ node: 'server', now: () => t });
  const before = clock.tick();
  const remote = hlc.format({ wall: T0 + hlc.SKEW_MS + 1, counter: 0, node: 'dev-bad' });
  const r = clock.recv(remote);
  assert.equal(r.skewed, true);
  const after = clock.tick();
  assert.ok(hlc.compare(after, remote) < 0, 'سرور با ساعتِ غلط جلو نرفت');
  assert.ok(hlc.compare(before, after) < 0);
  assert.equal(hlc.parse(after).wall, T0);

  //  درست روی مرز هنوز پذیرفته است
  const edge = hlc.format({ wall: T0 + hlc.SKEW_MS, counter: 0, node: 'dev-ok' });
  assert.equal(clock.recv(edge).skewed, false);
});

test('recv: ساعتِ دستگاهِ عقب‌مانده (۴۵ روز) پذیرفته است — آفلاین مهلت ندارد (D-05)', () => {
  const clock = hlc.createClock({ node: 'server', now: () => T0 });
  const old = hlc.format({ wall: T0 - 45 * 86_400_000, counter: 0, node: 'dev-a' });
  const r = clock.recv(old);
  assert.equal(r.skewed, false);
  assert.ok(hlc.compare(r.hlc, old) > 0);
});

test('observe: ساعتِ تازه‌راه‌افتاده از بزرگ‌ترین HLCِ ثبت‌شده عقب‌تر نمی‌دهد', () => {
  const clock = hlc.createClock({ node: 'server', now: () => T0 });
  const seen = hlc.format({ wall: T0 + 120_000, counter: 9, node: 'dev-a' });
  clock.observe(seen);
  assert.ok(hlc.compare(clock.tick(), seen) > 0);
});

test('fromTs: زمانِ ساده (op v2) به HLC، با گیره برای عددِ نادرست', () => {
  assert.equal(hlc.fromTs(T0, 2, 'dev-a'), hlc.format({ wall: T0, counter: 2, node: 'dev-a' }));
  assert.equal(hlc.parse(hlc.fromTs(-5, 0, 'x')).wall, 0);
  assert.equal(hlc.parse(hlc.fromTs('nope', 0, 'x')).wall, 0);
});
