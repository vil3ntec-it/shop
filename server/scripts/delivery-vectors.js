#!/usr/bin/env node
'use strict';
/**
 * بردارِ مرجعِ ماشینِ وضعیتِ «مأموریتِ تحویل» — `core/test-vectors/delivery-states-v1.json`.
 *
 *   node scripts/delivery-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/delivery-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * منبعِ حقیقت `src/lib/delivery-states.js` است: وضعیت‌ها، گذارها، پایانی‌ها
 * و جدولِ «چه کسی چه گذاری را می‌تواند» همه از اجرای `check()` بیرون می‌آیند.
 * دو آینهٔ دستیِ کاتلین همین فایل را می‌خوانند (`DeliveryStatesVectorsTest.kt`):
 * `CourierFlow` (برنامهٔ پیک) و `DeskActions` (میزِ تحویلِ دکان).
 *
 * `knownDivergences` تنها بخشِ دستی است: هر جا آینهٔ کاتلین عمداً یا به
 * اشتباه با سرور فرق دارد، همین‌جا با دلیل ثبت می‌شود و آزمونِ کاتلین
 * **دقیقاً** همین فهرست را می‌پذیرد — نه کمتر، نه بیشتر.
 */
const fs = require('fs');
const path = require('path');
const D = require('../src/lib/delivery-states');
const { render } = require('./order-vectors');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'core', 'test-vectors', 'delivery-states-v1.json');

/** کننده‌ها؛ مأموریت همیشه مالِ پیکِ `c1` است */
const ACTORS = {
  courier: { kind: 'courier', courierId: 'c1', active: true },
  courier_inactive: { kind: 'courier', courierId: 'c1', active: false },
  courier_other: { kind: 'courier', courierId: 'c2', active: true },
  staff: { kind: 'staff', canAssign: true, canHandover: true },
  staff_assign: { kind: 'staff', canAssign: true, canHandover: false },
  staff_handover: { kind: 'staff', canAssign: false, canHandover: true },
  staff_none: { kind: 'staff', canAssign: false, canHandover: false },
};

/** دلیلِ معتبرِ نخست (نه «other») تا جوابِ `check` فقط دسترسی را بسنجد */
const reasonFor = (to) => (D.REASONS[to] ? D.REASONS[to][0] : '');

function verdict(mode, from, to, actor, note = 'x') {
  const v = D.check({ status: from, mode, courier_id: 'c1' }, to, { actor: ACTORS[actor], reason: reasonFor(to), note });
  return v.ok ? 'ok' : v.code;
}

/**
 * جاهایی که آینهٔ کاتلین با سرور فرق دارد — دستی، با دلیل. `kind`:
 *   latent-bug — آینه با سرور نمی‌خواند و درست نیست، ولی امروز راهی در صفحه به آن نمی‌رسد (تصمیم با صاحب محصول)
 *   ui-choice  — سرور می‌پذیرد ولی صفحه عمداً دکمه‌اش را نشان نمی‌دهد
 *   unreachable — سرور می‌پذیرد ولی مأموریت هرگز به این وضعیت/روش نمی‌رسد
 * `pairs` = [روش، از، به]
 */
const KNOWN_DIVERGENCES = [
  {
    id: 'courier-delivered-after-arrived',
    impl: 'CourierFlow.actions',
    kind: 'ui-choice',
    pairs: [['shop_courier', 'picked_up', 'delivered'], ['shop_courier', 'moving', 'delivered']],
    note: '«تحویل شد» فقط پس از «رسیدم» پیش‌نهاد می‌شود (کد/نقد/عکس جلوی مشتری)؛ سرور میان‌بر را هم می‌پذیرد.',
  },
  {
    id: 'desk-external-no-movement',
    impl: 'DeskActions.of',
    kind: 'ui-choice',
    pairs: [['external', 'picked_up', 'moving'], ['external', 'picked_up', 'arrived'], ['external', 'moving', 'arrived']],
    note: 'میز برای شرکتِ بیرونی «در راه / رسید» ثبت نمی‌کند؛ فقط دادن به شرکت، تحویل، ناموفق، تلاشِ دوباره و برگشت.',
  },
  {
    id: 'desk-requested-only-shop-courier',
    impl: 'DeskActions.of',
    kind: 'unreachable',
    pairs: [
      ['external', 'requested', 'assigned'], ['external', 'requested', 'rejected'], ['external', 'requested', 'withdrawn'],
      ['pickup', 'requested', 'withdrawn'],
    ],
    note: 'delivery.js مأموریتِ شرکتِ بیرونی و دریافتِ حضوری را مستقیم «assigned» می‌سازد؛ «requested» فقط پیکِ دکان است.',
  },
  {
    id: 'desk-pickup-never-departs',
    impl: 'DeskActions.of',
    kind: 'unreachable',
    pairs: [['pickup', 'picked_up', 'delivered'], ['pickup', 'moving', 'delivered'], ['pickup', 'arrived', 'delivered']],
    note: 'دریافتِ حضوری از «assigned» فقط به «delivered» یا «withdrawn» می‌رود؛ هرگز «picked_up/moving/arrived» نمی‌شود.',
  },
  {
    id: 'desk-platform-mode',
    impl: 'DeskActions.of',
    kind: 'unreachable',
    pairs: 'platform',
    note: 'روشِ platform هنگامِ ساخت رد می‌شود (platform_unavailable، شبکهٔ پیکِ VILL3N فعال نیست)؛ DeskActions شاخه‌ای برایش ندارد.',
  },
];

function build() {
  const allowed = [];
  const noteRequired = [];
  for (const mode of D.MODES) {
    for (const from of D.STATUSES) {
      for (const to of D.TRANSITIONS[from]) {
        for (const actor of Object.keys(ACTORS)) allowed.push([mode, from, to, actor, verdict(mode, from, to, actor)]);
        if (verdict(mode, from, to, 'staff') === 'ok' && verdict(mode, from, to, 'staff', '') === 'note_required') {
          noteRequired.push([mode, from, to]);
        }
      }
    }
  }
  //  «همهٔ گذارهای کارمند در platform» از خودِ جدول باز می‌شود تا فهرست دستی کهنه نشود
  const staffOk = (mode) => allowed
    .filter(([m, , , actor, out]) => m === mode && actor.startsWith('staff') && out === 'ok')
    .map(([m, from, to]) => [m, from, to])
    .filter((p, i, list) => list.findIndex((q) => q.join() === p.join()) === i);
  const knownDivergences = KNOWN_DIVERGENCES.map((d) => (d.pairs === 'platform' ? { ...d, pairs: staffOk('platform') } : d));

  return {
    _doc: 'ساختهٔ server/scripts/delivery-vectors.js از server/src/lib/delivery-states.js — دستی ویرایش نشود؛ '
      + 'allowed = [روش، از، به، کننده، نتیجهٔ check با دلیلِ معتبر و توضیح]',
    statuses: D.STATUSES,
    modes: D.MODES,
    transitions: D.TRANSITIONS,
    terminal: D.STATUSES.filter(D.isTerminal),
    live: D.LIVE,
    departed: D.DEPARTED,
    courierTargets: D.COURIER_TARGETS,
    reasons: D.REASONS,
    reasonRequired: D.REASON_REQUIRED,
    actors: ACTORS,
    noteRequired,
    allowed,
    knownDivergences,
  };
}

/**
 * همان `render`ِ order-vectors (یک ردیف در هر خط)؛ هر ناهم‌خوانی هم یک
 * سرخط و هر جفتش یک خط، تا تفاوتِ git کوچک بماند.
 */
function expected() {
  const { knownDivergences, ...rest } = build();
  const lines = [render(rest).replace(/\n}\n$/, ','), '  "knownDivergences": ['];
  knownDivergences.forEach(({ pairs, ...meta }, i) => {
    lines.push(`    ${JSON.stringify(meta).slice(0, -1)}, "pairs": [`);
    pairs.forEach((p, j) => lines.push(`      ${JSON.stringify(p)}${j < pairs.length - 1 ? ',' : ''}`));
    lines.push(`    ]}${i < knownDivergences.length - 1 ? ',' : ''}`);
  });
  lines.push('  ]', '}');
  return lines.join('\n') + '\n';
}

if (require.main === module) {
  const out = expected();
  if (process.argv.includes('--write')) {
    fs.mkdirSync(path.dirname(FIXTURE), { recursive: true });
    fs.writeFileSync(FIXTURE, out);
    console.log(`نوشته شد: ${path.relative(ROOT, FIXTURE)} (${out.length} بایت)`);
  } else {
    const now = fs.existsSync(FIXTURE) ? fs.readFileSync(FIXTURE, 'utf8') : '';
    if (now !== out) {
      console.error('بردارِ تحویل کهنه است: cd server && node scripts/delivery-vectors.js --write');
      process.exit(1);
    }
    console.log('بردارِ تحویل به‌روز است');
  }
}

module.exports = { build, expected, FIXTURE, KNOWN_DIVERGENCES };
