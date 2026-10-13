'use strict';
/**
 * کسریِ صندوق و شیفتِ بسته‌نشده — نوعِ تازهٔ `shift` (۱۴۰۵/۰۷/۲۱).
 *
 * خواستهٔ صاحب سامانه: «کسری صندوق و شیفت بسته‌نشده رو هم درست کن.»
 * برنامهٔ پمپ این دو را با کلیدِ `cash-<ورق>-<روز|شب>` و `shift-<ورق>-<روز|شب>` می‌فرستد.
 *
 *   ۱) هر دو `shift` می‌شوند — نه «قرض» (که دکمهٔ حساب و نامِ قرض‌دار می‌گرفت).
 *   ۲) `shift` پوش می‌شود و به بات می‌رسد (پولِ گم‌شده خبرِ مهم است).
 *   ۳) کلیدِ برنامه پذیرفته می‌شود و موضوعش خودِ کلید است.
 */
const test = require('node:test');
const assert = require('node:assert');
const state = require('../src/lib/pump-state');
const events = require('../src/lib/events');

test('کلیدهای ورق ⇒ نوعِ shift؛ بقیه دست‌نخورده', () => {
  assert.equal(state.kindOf('cash-12-day', 'low'), 'shift');
  assert.equal(state.kindOf('shift-12-night', 'low'), 'shift');
  assert.equal(state.kindOf('tank-diesel-out', 'out'), 'stock_out');
  assert.equal(state.kindOf('d7-stP-w70', 'low'), 'debt');
});

test('shift پوش می‌شود و نوعِ شناخته‌شده است', () => {
  assert.ok(events.KINDS.includes('shift'));
  assert.ok(events.PUSH_KINDS.includes('shift'));
});

test('هشدارِ ورق از درِ cleanAlert رد می‌شود', () => {
  const a = state.cleanAlert({ k: 'cash-12-day', s: 'low', t: 'کسریِ صندوق — ۲٬۵۰۰ افغانی کم است', n: 'احمد', f: '' });
  assert.equal(a.kind, 'shift');
  assert.equal(a.n, 'احمد');
  assert.equal(state.subjectOf('cash-12-day'), 'cash-12-day');
});
