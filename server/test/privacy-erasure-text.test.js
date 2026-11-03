'use strict';
/**
 * SEC-10 — «متنِ privacy.html پیش از روشن کردنِ `shop.customer_erasure`» (§۲۶: «صفحه حریم خصوصی باید دقیقاً با رفتار کد
 * یکی باشد»). صفحه بخشِ `#customer-erasure` دارد و هر چه کد عمداً نگه می‌دارد (`KEPT` ِ `lib/customer-erasure.js`)
 * همان‌جا گفته شده است؛ ردیفِ تازه در `KEPT` بی جمله‌اش در صفحه ⇒ سرخ. بی پایگاه‌داده.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { KEPT, FLAG } = require('../src/lib/customer-erasure');

const html = fs.readFileSync(path.join(__dirname, '..', '..', 'privacy.html'), 'utf8');
const section = (html.match(/<section id="customer-erasure">([\s\S]*?)<\/section>/) || [])[1] || '';
const text = section.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

/** هر ردیفِ `KEPT` ⇐ عبارتی که صفحه برای آن می‌گوید */
const SAYS = {
  order_ledger_docs: 'سندهای مالی و تسویه',
  online_order_refunds: 'بازپرداخت',
  courier_settlements: 'تسویهٔ پیک',
  delivery_assignments: 'سندهای مالی و تسویه',
  delivery_ratings: 'امتیازِ ستاره‌ای',
  shop_risk_lists: 'شماره‌ای که دکان بسته',
  shop_chat_blocks: 'گفت‌وگویش را مسدود کرده',
  storefront_promo_claims: 'کدِ یک‌طرفهٔ شماره برای سقفِ کدِ تخفیف',
  trust_cases: 'پروندهٔ رسیدگیِ مدیرِ سامانه',
  store_customers: 'حسابِ سراسریِ مشتری',
  device_sync_ledger: 'دفترِ دستگاهِ دکان',
  audit_logs: 'فقط زمان، روش و شمارِ ردیف‌ها',
  backups: 'نسخه‌های پشتیبانِ پیش از حذف',
};

test('privacy.html بخشِ «پاک کردنِ دادهٔ یک مشتری» دارد و فقط با روشن بودنِ دکان', () => {
  assert.equal(FLAG, 'shop.customer_erasure');
  assert.ok(section, 'بخشِ #customer-erasure در privacy.html نیست');
  assert.match(text, /فقط وقتی آن دکان این امکان را روشن کرده باشد/);
  //  چه کسی: مشتری با تأییدِ تازهٔ شماره، صاحب با تأییدِ دوبارهٔ هویت، نه کارمند؛ و نه با سفارشِ باز (۴۰۹ `open_orders`)
  for (const s of ['تأییدِ تازهٔ همان شماره', 'تأییدِ دوبارهٔ هویت', 'کارمند نمی‌تواند', 'سفارشِ بازی']) assert.ok(text.includes(s), s);
  assert.match(text, /برگشت‌پذیر نیست/);
});

test('هر چه کد عمداً نگه می‌دارد (KEPT) در صفحه گفته شده است', () => {
  assert.deepEqual([...KEPT].sort(), Object.keys(SAYS).sort(), 'KEPT عوض شد ⇒ privacy.html و SAYS ِ همین آزمون را هم');
  for (const k of KEPT) assert.ok(text.includes(SAYS[k]), `${k} ⇐ «${SAYS[k]}» در privacy.html نیست`);
});
