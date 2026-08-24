'use strict';
/* تست منطق ساعت مقاوم در برابر دستکاری — همان الگوریتمی که در کلاینت است.
   اینجا به صورت خالص بازسازی شده تا بدون مرورگر هم قابل بررسی باشد. */
const test = require('node:test');
const assert = require('node:assert');

const BACK_TOLERANCE = 10 * 60 * 1000;
const FORWARD_FLAG = 365 * 24 * 60 * 60 * 1000;

/** بازسازی دقیق Clock در license-client.js */
function makeClock() {
  const store = { timeHighWater: 0, serverTimeOffset: 0 };
  let fakeNow = Date.now();
  return {
    store,
    setDeviceClock(ms) { fakeNow = ms; },
    advanceDevice(ms) { fakeNow += ms; },
    read() {
      const raw = fakeNow;
      const candidate = raw + store.serverTimeOffset;
      const highWater = store.timeHighWater || 0;
      let status = 'ok';
      if (candidate < highWater - BACK_TOLERANCE) status = 'rolled_back';
      else if (highWater && candidate > highWater + FORWARD_FLAG) status = 'jumped_forward';
      return { raw, candidate, effective: Math.max(candidate, highWater), highWater, status };
    },
    tick() {
      const c = this.read();
      if (c.candidate > c.highWater) store.timeHighWater = c.candidate;
      store.clockStatus = c.status;
      return c;
    },
    syncWithServer(serverMs) {
      store.serverTimeOffset = serverMs - fakeNow;
      store.timeHighWater = serverMs;
      store.clockStatus = 'ok';
    },
  };
}

const DAY = 86400000;

test('عقب بردن ساعت، اشتراک را تمدید نمی‌کند', () => {
  const c = makeClock();
  const serverNow = Date.UTC(2026, 7, 24);
  const expiry = serverNow + 10 * DAY;

  c.setDeviceClock(serverNow);
  c.syncWithServer(serverNow);
  c.tick();

  // ۱۵ روز می‌گذرد → اشتراک باید تمام شده باشد
  c.advanceDevice(15 * DAY);
  let s = c.tick();
  assert.ok(s.effective > expiry, 'باید منقضی شده باشد');

  // کاربر ساعت را یک سال به عقب می‌برد
  c.setDeviceClock(serverNow - 365 * DAY);
  s = c.tick();
  assert.equal(s.status, 'rolled_back', 'باید عقب‌رفتن ساعت تشخیص داده شود');
  assert.ok(s.effective > expiry, 'زمان مؤثر نباید عقب برود — اشتراک همچنان منقضی است');
});

test('عقب‌رفتن کوچک (اصلاح NTP) مشکوک شمرده نمی‌شود', () => {
  const c = makeClock();
  const t0 = Date.UTC(2026, 7, 24);
  c.setDeviceClock(t0); c.syncWithServer(t0); c.tick();

  c.advanceDevice(-2 * 60 * 1000);   // ۲ دقیقه عقب
  const s = c.tick();
  assert.equal(s.status, 'ok', 'اصلاح چند دقیقه‌ای باید عادی باشد');
});

test('تغییر منطقه زمانی هیچ اثری ندارد', () => {
  // Date.now() همیشه UTC است؛ تغییر TZ آن را جابه‌جا نمی‌کند.
  const c = makeClock();
  const t0 = Date.UTC(2026, 7, 24);
  c.setDeviceClock(t0); c.syncWithServer(t0);
  const before = c.tick();
  // شبیه‌سازی: کاربر منطقه زمانی را عوض می‌کند، ولی epoch ثابت می‌ماند
  const after = c.tick();
  assert.equal(after.status, 'ok');
  assert.equal(after.effective, before.effective);
});

test('بازگشت پس از هفته‌ها آفلاین بودن، گذر زمان را درست ثبت می‌کند', () => {
  // این همان حالتی است که نباید با «جهش مشکوک» اشتباه گرفته شود:
  // دکاندار برنامه را دو ماه باز نکرده است.
  const c = makeClock();
  const t0 = Date.UTC(2026, 7, 24);
  c.setDeviceClock(t0); c.syncWithServer(t0); c.tick();

  c.advanceDevice(60 * DAY);
  const s = c.tick();
  assert.equal(s.status, 'ok', 'گذر واقعی زمان نباید مشکوک شمرده شود');
  assert.equal(c.store.timeHighWater, t0 + 60 * DAY, 'بالاترین زمان باید جلو رفته باشد');

  // و حالا عقب بردن ساعت هم گرفته می‌شود
  c.setDeviceClock(t0);
  assert.equal(c.tick().status, 'rolled_back');
});

test('جهش جلوی نامعقول فقط هشدار می‌دهد و با Sync اصلاح می‌شود', () => {
  const c = makeClock();
  const t0 = Date.UTC(2026, 7, 24);
  c.setDeviceClock(t0); c.syncWithServer(t0); c.tick();

  c.advanceDevice(400 * DAY);            // ساعت را عمداً خیلی جلو می‌برد
  assert.equal(c.tick().status, 'jumped_forward', 'باید هشدار بدهد');

  // این به سود کاربر نیست: فقط زودتر قفل می‌شود.
  // یک بار Sync با سرور همه چیز را درست می‌کند.
  c.setDeviceClock(t0 + DAY);
  c.syncWithServer(t0 + DAY);
  const s = c.tick();
  assert.equal(s.status, 'ok');
  assert.equal(s.effective, t0 + DAY, 'زمان سرور باید کاملاً جایگزین شود');
});

test('گذر عادی زمان، بالاترین زمان را جلو می‌برد', () => {
  const c = makeClock();
  const t0 = Date.UTC(2026, 7, 24);
  c.setDeviceClock(t0); c.syncWithServer(t0); c.tick();

  for (let i = 0; i < 5; i++) { c.advanceDevice(DAY); c.tick(); }
  assert.equal(c.store.timeHighWater, t0 + 5 * DAY);
  assert.equal(c.store.clockStatus, 'ok');
});

test('Sync با سرور، ساعت دستکاری‌شده را اصلاح می‌کند', () => {
  const c = makeClock();
  const t0 = Date.UTC(2026, 7, 24);
  c.setDeviceClock(t0); c.syncWithServer(t0); c.tick();

  // کاربر ساعت دستگاه را ۳۰ روز عقب می‌برد
  c.setDeviceClock(t0 - 30 * DAY);
  assert.equal(c.tick().status, 'rolled_back');

  // اتصال به سرور: زمان واقعی سرور مرجع می‌شود
  const realNow = t0 + 3 * DAY;
  c.syncWithServer(realNow);
  const s = c.tick();
  assert.equal(s.status, 'ok');
  assert.equal(s.effective, realNow, 'زمان سرور باید مرجع باشد، نه ساعت دستگاه');
});
