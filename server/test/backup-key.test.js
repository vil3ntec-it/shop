'use strict';
/**
 * کلیدِ بکاپِ هر پمپ (۲.۱۱.۲۲): عضوِ همان پمپ می‌گیرد، پمپِ دیگر و حسابِ تازه
 * کلیدِ پمپِ قبلی را هرگز نمی‌گیرند. — «حسابِ جدید و آزمایشیِ دوباره راهی برای
 * دیدنِ بکاپِ قبلی نشود.»
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function pumpOwner(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

test('عضوِ پمپ کلیدِ همان پمپ را می‌گیرد — همیشه همان', async () => {
  const o = await pumpOwner('صاحب');
  const a = await h.get('/api/pump/backup-key', { token: o.accessToken });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.stationId, o.stationId);
  assert.equal(Buffer.from(a.body.key, 'base64').length, 32);
  const b = await h.get('/api/pump/backup-key', { token: o.accessToken });
  assert.equal(b.body.key, a.body.key, 'کلید پایدار است، نه تازه با هر پرسش');
});

test('⛔ حسابِ تازه (پمپِ تازه) کلیدِ پمپِ قبلی را نمی‌گیرد', async () => {
  const first = await pumpOwner('اول');
  const second = await pumpOwner('دوم');
  const k1 = (await h.get('/api/pump/backup-key', { token: first.accessToken })).body.key;
  const k2 = (await h.get('/api/pump/backup-key', { token: second.accessToken })).body.key;
  assert.notEqual(k1, k2);
});

test('بی پمپ یا بی توکن هیچ کلیدی نیست', async () => {
  const u = await h.newUser('بی‌پمپ', 'pump');
  const none = await h.get('/api/pump/backup-key', { token: u.accessToken });
  assert.notEqual(none.status, 200);
  const anon = await h.get('/api/pump/backup-key');
  assert.equal(anon.status, 401);
});
