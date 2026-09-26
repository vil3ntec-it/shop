'use strict';
/**
 * ══ کدِ هشت‌رقمیِ پمپ — برای هر حساب، از همان لحظهٔ ساختن ═══════════════
 *
 * خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۴): «برای هر حساب کاربری یک کد هشت‌رقمی
 * درست بشه تا به برنامهٔ گوشیِ اندروید و آیفون وصل شد و برنامه رو دید.»
 *
 *   ۱) پمپِ تازهٔ حساب همان لحظه کدِ هشت‌رقمی دارد — منتظرِ هیچ صفحه‌ای نیست.
 *   ۲) رقمِ فارسی، خطِ تیره و فاصله پذیرفته است.
 *   ۳) پمپِ قدیمی با کدِ حرفی ⇒ ربات کدِ هشت‌رقمی می‌دهد و کدِ حرفی **هنوز**
 *      در را باز می‌کند (گوشیِ وصل‌شده بیرون نمی‌افتد).
 *   ۴) «عوض کردن» هر دو را باطل می‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const DIGITS = /^[1-9][0-9]{7}$/;
const fa = (s) => s.replace(/[0-9]/g, d => '۰۱۲۳۴۵۶۷۸۹'[d]);

test('پمپِ تازهٔ هر حساب همان لحظه کدِ هشت‌رقمی دارد', async () => {
  const u = await h.newUser('صاحبِ پمپِ تازه', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ هشت‌رقمی' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const row = await one('SELECT access_code FROM stations WHERE id=$1', [made.body.station.id]);
  assert.match(row.access_code, DIGITS, 'بی باز کردنِ هیچ صفحه‌ای');

  const seen = await h.get('/api/pump/access-code', { token: u.accessToken });
  assert.equal(seen.body.code, row.access_code, 'همان کد، نه کدِ دوم');
  assert.equal(seen.body.display, row.access_code.slice(0, 4) + '-' + row.access_code.slice(4));
});

test('گوشی با رقمِ فارسی و خطِ تیره هم می‌پیوندد', async () => {
  const u = await h.newUser('صاحب', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ فارسی' }, { token: u.accessToken });
  const code = (await one('SELECT access_code FROM stations WHERE id=$1', [made.body.station.id])).access_code;
  for (const typed of [code, fa(code.slice(0, 4) + '-' + code.slice(4)), ' ' + code.slice(0, 4) + ' ' + code.slice(4) + ' ']) {
    const r = await h.post('/api/pump/public/join', { code: typed });
    assert.equal(r.status, 200, typed + ' ⇒ ' + JSON.stringify(r.body));
    assert.equal(r.body.station.name, 'پمپِ فارسی');
    assert.equal(r.body.station.accessCode, code.slice(0, 4) + '-' + code.slice(4));
  }
  assert.equal((await h.post('/api/pump/public/join', { code: '0000000' })).status, 404, 'هفت رقم');
});

test('⛔ پمپِ قدیمی: ربات کدِ هشت‌رقمی می‌دهد و کدِ حرفیِ پیشین هنوز کار می‌کند', async () => {
  const u = await h.newUser('صاحبِ قدیمی', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ قدیمی' }, { token: u.accessToken });
  const id = made.body.station.id;
  //  همان حالی که نسخه‌های پیشین ساخته بودند: کدِ حرفی
  await query(`UPDATE stations SET access_code='K7PM3XQ2', access_code_old='' WHERE id=$1`, [id]);
  //  و پمپی که هنوز اصلاً کدی نگرفته
  const u2 = await h.newUser('صاحبِ بی‌کد', 'pump');
  const made2 = await h.post('/api/pump', { name: 'پمپِ بی‌کد' }, { token: u2.accessToken });
  await query(`UPDATE stations SET access_code='', access_code_old='' WHERE id=$1`, [made2.body.station.id]);

  const n = await require('../src/lib/station-access').sweep();
  assert.ok(n >= 2, 'هر دو پمپ کد گرفتند: ' + n);

  const row = await one('SELECT access_code, access_code_old FROM stations WHERE id=$1', [id]);
  assert.match(row.access_code, DIGITS);
  assert.equal(row.access_code_old, 'K7PM3XQ2');
  assert.match((await one('SELECT access_code FROM stations WHERE id=$1', [made2.body.station.id])).access_code, DIGITS);

  //  گوشیِ کهنه با همان کدِ حرفی ⇒ هنوز همان پمپ، و کدِ امروزی را می‌گیرد
  const old = await h.post('/api/pump/public/join', { code: 'k7pm-3xq2' });
  assert.equal(old.status, 200, JSON.stringify(old.body));
  assert.equal(old.body.station.name, 'پمپِ قدیمی');
  assert.equal(old.body.station.accessCode, row.access_code.slice(0, 4) + '-' + row.access_code.slice(4));
  assert.equal((await h.post('/api/pump/public/join', { code: row.access_code })).status, 200);

  //  دویدنِ دوباره هیچ چیزی را جابه‌جا نمی‌کند
  await require('../src/lib/station-access').sweep();
  const again = await one('SELECT access_code FROM stations WHERE id=$1', [id]);
  assert.equal(again.access_code, row.access_code);

  //  ⛔ عوض کردن به دستِ صاحب ⇒ هر دو کدِ قبلی باطل
  const rot = await h.post('/api/pump/access-code/rotate', {}, { token: u.accessToken });
  assert.equal(rot.status, 201, JSON.stringify(rot.body));
  assert.match(rot.body.code, DIGITS);
  assert.equal((await h.post('/api/pump/public/join', { code: 'K7PM3XQ2' })).status, 404);
  assert.equal((await h.post('/api/pump/public/join', { code: row.access_code })).status, 404);
  assert.equal((await h.post('/api/pump/public/join', { code: rot.body.code })).status, 200);
});
