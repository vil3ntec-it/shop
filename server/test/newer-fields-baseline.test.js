'use strict';
/**
 * A00 — نگهبانِ فهرستِ منجمدِ `PRE_A00` (`src/lib/newer-fields.js`) در برابرِ مدلِ امروزِ برنامه.
 *
 * مدل‌ها همان‌طور خوانده می‌شوند که دستگاه‌های سرتاسری می‌خوانند (`parseModels` ِ
 * `test/e2e/lib/native.mjs` از `android-native/.../data/Models.kt`).
 *
 *   ۱) هر فیلدِ مدلِ دفتر یا در `PRE_A00` هست، یا تورِ A00 آن را می‌پوشاند — یعنی کفِ A00
 *      (نخستین ساختِ منتشرشده با #341) برای گوشی و کامپیوتر پر شده است. تا آن روز فیلدِ
 *      تازه‌ای که به مدل بیاید این‌جا قرمز می‌شود: `null` اش از ساختِ تازه هم کنار می‌رفت.
 *   ۲) هر جدول و کفِ `PRE_A00` درست است: جدولِ همگامِ v1، نسخهٔ معتبر برای هر دو خط.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const N = require('../src/lib/newer-fields');
const compat = require('../src/lib/app-compat');
const { tablesOf } = require('../src/lib/sync-v1');

test('هر فیلدِ مدل در PRE_A00 است یا کفِ A00 آن را می‌پوشاند', async () => {
  const native = await import('./e2e/lib/native.mjs');
  const missing = [];
  for (const [table, cls] of Object.entries(native.COLLECTION_MODEL)) {
    for (const f of native.MODELS[cls]) {
      if (!N.PRE_A00[table] || !Object.prototype.hasOwnProperty.call(N.PRE_A00[table], f.key)) missing.push(`${table}.${f.key}`);
    }
  }
  const covered = Boolean(N.A00.phone && N.A00.desktop);
  assert.ok(Object.keys(native.COLLECTION_MODEL).length >= 14, 'مدل‌ها خوانده نشدند');
  assert.deepEqual(covered ? [] : missing, [],
    'فیلدِ تازه در مدل، ولی کفِ A00 (newer-fields.js → A00) هنوز خالی است: پیش از افزودنِ فیلد، '
    + 'نخستین ساختِ منتشرشدهٔ گوشی و کامپیوتر با #341 را آن‌جا بنویسید. PRE_A00 منجمد است و فیلدِ تازه در آن نمی‌آید.');
});

test('PRE_A00: جدول‌های همگام و کف‌های معتبر', () => {
  const tables = new Set(tablesOf('shop'));
  for (const [table, fields] of Object.entries(N.PRE_A00)) {
    assert.ok(tables.has(table), `${table} جدولِ همگامِ دکان نیست`);
    for (const [key, floor] of Object.entries(fields)) {
      if (floor === null) continue;
      assert.ok(/^3\.2\.\d+$/.test(floor.phone) && compat.valid(floor.phone), `${table}.${key} گوشی`);
      assert.ok(/^1\.0\.\d+$/.test(floor.desktop) && compat.valid(floor.desktop), `${table}.${key} کامپیوتر`);
    }
  }
  for (const line of Object.values(N.LINES)) {
    const v = N.A00[line];
    assert.ok(v === null || compat.valid(v), `A00.${line}`);
  }
});
