#!/usr/bin/env node
'use strict';
/**
 * پرداختِ ساختگی برای سرورِ توسعه (PAY-21) — فقط وقتی سرور با `PAYMENTS_PROVIDER=fake`،
 * `NODE_ENV=development` (یا test) و `PAYMENTS_FAKE_SECRET` بالا آمده است. وب‌هوکِ امضاشدهٔ «ارائه‌دهندهٔ ساختگی» را می‌فرستد و
 * با `settle: true` همان را «وضعیتِ ارائه‌دهنده» هم می‌کند (تأییدِ دوم می‌گذرد).
 *
 *   node scripts/fake-pay.js --url http://127.0.0.1:3000 --intent pi_… --amount 145000 [--status paid|failed|expired|cancelled]
 *
 * --amount همان `amount` ِ قصد است (پول = یک‌صدمِ افغانی). رازِ امضا: PAYMENTS_FAKE_SECRET
 * (همان مقدارِ سرور؛ پیش‌فرضی نیست ⇒ بی آن اسکریپت خطا می‌دهد). بیرون از توسعه/آزمون سرور این در را ۴۰۱ می‌دهد.
 */
const P = require('../src/lib/payment-providers');

function arg(name, def = '') {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : def;
}

async function main() {
  const url = arg('url', 'http://127.0.0.1:3000').replace(/\/+$/, '');
  const intent = arg('intent');
  const amount = Number(arg('amount'));
  const status = arg('status', 'paid');
  if (!intent || !Number.isSafeInteger(amount) || amount <= 0) {
    console.error('کاربرد: node scripts/fake-pay.js --url <سرور> --intent <pi_…> --amount <پول> [--status paid]');
    process.exit(2);
  }
  const sessionId = P.fake.sessionOf(intent);
  const body = Buffer.from(JSON.stringify({
    eventId: `dev-${Date.now()}`, sessionId, status, amountMinor: amount, currency: 'AFN',
    providerRef: `FAKE-${Date.now()}`, settle: true,
  }));
  const res = await fetch(`${url}/api/v1/payments/webhook/fake`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [P.fake.SIG_HEADER]: P.fake.sign(body) },
    body,
  });
  console.log(res.status, await res.text());
  process.exit(res.ok ? 0 : 1);
}

main().catch((err) => { console.error(err.message); process.exit(1); });
