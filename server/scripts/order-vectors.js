#!/usr/bin/env node
'use strict';
/**
 * بردارهای مرجعِ ماشینِ وضعیتِ سفارش — `core/test-vectors/order-states.json`.
 *
 *   node scripts/order-vectors.js           ⇐ فقط می‌سنجد که فایل با خروجیِ امروز یکی است
 *   node scripts/order-vectors.js --write   ⇐ فایل را از نو می‌نویسد
 *
 * منبع **فقط** `src/lib/order-states.js` (و جدولِ دسترسیِ `permissions.js`) است:
 * هر عدد و هر فهرست این‌جا از اجرای همان توابع بیرون می‌آید، نه از بازنویسی.
 * `OrderStatesTest.kt`ِ نیتیو (اندروید و کامپیوتر) و `order-vectors.test.js`ِ
 * سرور (برای هستهٔ `license/orders-ui.js`ِ وب) همین فایل را می‌خوانند؛ پس
 * اگر ماشینِ وضعیت عوض شود و بردار نه، آزمونِ سرور قرمز می‌شود، و اگر بردار
 * عوض شود و آینهٔ کاتلین نه، آزمونِ نیتیو.
 */
const fs = require('fs');
const path = require('path');
const S = require('../src/lib/order-states');
const P = require('../src/lib/permissions');

const ROOT = path.join(__dirname, '..', '..');
const FIXTURE = path.join(ROOT, 'core', 'test-vectors', 'order-states.json');

const FULFILLMENTS = ['pickup', 'delivery'];
/** «کننده»: سیستم، یا کاربری با یکی از نقش‌های دکان */
const ACTORS = ['system', ...P.ROLES];
const actorOf = (a) => (a === 'system' ? { type: 'system' } : { type: 'user', role: a });

function build() {
  const permissions = {};
  for (const k of ['view', 'create', 'manage', 'contact', 'refund']) {
    permissions[`orders.${k}`] = P.ROLES.filter((r) => P.can(r, `orders.${k}`));
  }

  //  دکمه‌های مجازِ هر کارت — همان `nextFor`ی که سرور در `card.next` می‌گذارد
  const nextFor = [];
  for (const status of S.ORDER_STATUSES) {
    for (const fulfillment of FULFILLMENTS) {
      for (const role of P.ROLES) {
        nextFor.push([status, fulfillment, role, S.nextFor({ status, fulfillment }, { type: 'user', role })]);
      }
    }
  }

  //  `check` روی هر گذارِ جدول + یک گذارِ نامجاز از هر وضعیت، با هر دلیلِ ممکن
  const check = [];
  for (const from of S.ORDER_STATUSES) {
    const targets = [...S.nextOf(from)];
    const bad = S.ORDER_STATUSES.find((s) => s !== from && !targets.includes(s));
    if (bad) targets.push(bad);
    for (const to of targets) {
      const reasons = ['', ...(S.REASONS[to] || []), 'bogus'];
      for (const fulfillment of FULFILLMENTS) {
        for (const actor of ACTORS) {
          for (const reason of reasons) {
            for (const note of reason === 'other' ? ['', 'x'] : ['x']) {
              const v = S.check({ status: from, fulfillment }, to, { actor: actorOf(actor), reason, note });
              check.push([from, to, fulfillment, actor, reason, note, v.ok ? 'ok' : v.code]);
            }
          }
        }
      }
    }
  }

  //  دلیل‌هایی که پنجرهٔ «دلیل» برای هر گذار نشان می‌دهد
  const reasonChoices = [];
  for (const from of S.ORDER_STATUSES) {
    for (const to of S.nextOf(from)) {
      if (!S.REASONS[to]) continue;
      const ok = S.REASONS[to].filter((reason) =>
        FULFILLMENTS.some((fulfillment) => S.check({ status: from, fulfillment }, to,
          { actor: { type: 'system' }, reason, note: 'x' }).ok));
      reasonChoices.push([from, to, ok]);
    }
  }

  //  برگهٔ هر سفارش؛ «اکنون» ثابت است تا فایل همیشه همان باشد
  const AT = 1_800_000_000_000;
  const tabOf = [];
  for (const status of S.ORDER_STATUSES) {
    for (const scheduledFor of [null, AT - 60_000, AT, AT + 60_000]) {
      tabOf.push([status, scheduledFor, AT, S.tabOf({ status, scheduled_for: scheduledFor }, AT)]);
    }
  }

  return {
    _doc: 'ساختهٔ server/scripts/order-vectors.js از server/src/lib/order-states.js — دستی ویرایش نشود.',
    statuses: S.ORDER_STATUSES,
    transitions: S.ORDER_TRANSITIONS,
    reasons: S.REASONS,
    reasonRequired: S.REASON_REQUIRED,
    systemOnly: S.SYSTEM_ONLY,
    moneyTargets: S.MONEY_TARGETS,
    tabs: S.TABS,
    openTabs: S.OPEN_TABS,
    permissions,
    nextFor,
    check,
    reasonChoices,
    tabOf,
  };
}

/** یک ردیف در هر خط — فایل خوانا می‌ماند و تفاوتِ git کوچک */
function render(v) {
  const lines = ['{'];
  const keys = Object.keys(v);
  keys.forEach((k, i) => {
    const comma = i < keys.length - 1 ? ',' : '';
    const val = v[k];
    if (Array.isArray(val) && val.some(Array.isArray)) {
      lines.push(`  ${JSON.stringify(k)}: [`);
      val.forEach((row, j) => lines.push(`    ${JSON.stringify(row)}${j < val.length - 1 ? ',' : ''}`));
      lines.push(`  ]${comma}`);
    } else {
      lines.push(`  ${JSON.stringify(k)}: ${JSON.stringify(val)}${comma}`);
    }
  });
  lines.push('}');
  return lines.join('\n') + '\n';
}

const expected = () => render(build());

if (require.main === module) {
  const out = expected();
  if (process.argv.includes('--write')) {
    fs.mkdirSync(path.dirname(FIXTURE), { recursive: true });
    fs.writeFileSync(FIXTURE, out);
    console.log(`نوشته شد: ${path.relative(ROOT, FIXTURE)} (${out.length} بایت)`);
  } else {
    const now = fs.existsSync(FIXTURE) ? fs.readFileSync(FIXTURE, 'utf8') : '';
    if (now !== out) {
      console.error('بردارها کهنه‌اند: cd server && node scripts/order-vectors.js --write');
      process.exit(1);
    }
    console.log('بردارها به‌روزند');
  }
}

module.exports = { build, render, expected, FIXTURE };
