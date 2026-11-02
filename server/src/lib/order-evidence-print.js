'use strict';
/**
 * RSK-08 — صفحهٔ چاپیِ پروندهٔ اختلاف (همان دادهٔ `order-evidence.fileOf`، بی هیچ دادهٔ دیگر).
 * HTML ِ ساده و راست‌به‌چپ، بی اسکریپت و بی تصویر (درِ آن سیاستِ محتوای بسته دارد)؛ هر متنِ کاربر escape شده.
 * زمان‌ها به UTC تا در چاپ ابهامی نماند؛ مبلغ به واحدِ بزرگ از روی واحدِ کوچک.
 */
const { escapeHtml: h } = require('./mail-templates');

const KIND_FA = Object.freeze({
  not_delivered: 'نرسیده', missing_item: 'کمبودِ کالا', wrong_item: 'کالای اشتباه', damaged: 'کالای خراب',
  cash: 'اختلافِ پول', late: 'دیرکرد', other: 'دیگر',
});
const OUTCOME_FA = Object.freeze({
  customer_favor: 'به سودِ مشتری', shop_favor: 'به سودِ دکان', settled: 'با توافق', withdrawn: 'پس گرفته شد',
});

const at = (ms) => (ms === null || ms === undefined ? '—' : `${new Date(Number(ms)).toISOString().replace('T', ' ').slice(0, 19)} UTC`);
const amount = (minor, currency) => (minor === null || minor === undefined ? '—'
  : `${(Number(minor) / 100).toLocaleString('en-US', { maximumFractionDigits: 2 })} ${currency || ''}`.trim());
const skew = (ms) => (ms === null || ms === undefined ? '—' : `${Math.round(Number(ms) / 1000)} ث`);
const who = (people, id) => (id ? h(people.users[id] || id) : '—');

function table(head, rows) {
  if (!rows.length) return '<p class="none">ثبت نشده است.</p>';
  return `<table><thead><tr>${head.map((x) => `<th>${h(x)}</th>`).join('')}</tr></thead><tbody>${
    rows.map((r) => `<tr>${r.map((c) => `<td>${c}</td>`).join('')}</tr>`).join('')}</tbody></table>`;
}

function disputesHtml(f) {
  if (!f.disputes.length) return '<p class="none">برای این سفارش اختلافی باز نشده است.</p>';
  return f.disputes.map((d) => `<div class="box">
  <div><b>${h(KIND_FA[d.kind] || d.kind)}</b> · ${d.status === 'open' ? 'باز' : 'بسته'} · ${at(d.openedAt)} · ${who(f.people, d.openedBy.userId)}</div>
  <p>${h(d.reason)}</p>
  ${d.assigneeId ? `<p><b>مسئول: ${who(f.people, d.assigneeId)}</b> · مهلت ${at(d.dueAt)}${d.overdue ? ' · <b>مهلت گذشته</b>' : ''}</p>` : ''}
  ${table(['زمان', 'کاربر', 'یادداشت'], d.notes.map((n) => [at(n.at), who(f.people, n.by.userId), h(n.text)]))}
  ${d.resolution ? `<p><b>نتیجه: ${h(OUTCOME_FA[d.resolution.outcome] || d.resolution.outcome)}</b> · ${at(d.resolution.at)} · ${
    who(f.people, d.resolution.by.userId)}<br>${h(d.resolution.text)}</p>` : ''}
</div>`).join('');
}

function printHtml(f) {
  const o = f.order;
  const cur = o.currency;
  const asg = f.delivery.assignments;
  const sections = [
    ['سفارش', table(['شماره', 'وضعیت', 'روش', 'پرداخت', 'جمع', 'مشتری', 'نشانه', 'ساخته'], [[
      h(o.orderNo), h(o.status), h(o.fulfillment), `${h(o.paymentMethod)} / ${h(o.paymentStatus)}`, h(amount(o.totalMinor, cur)),
      `${h(o.customer.name)} ${h(o.customer.phoneMasked)}`, `${h(o.address.landmark)} ${h(o.address.area)}${o.address.hasPin ? ' · پین' : ''}`,
      at(o.createdAt),
    ]])],
    ['اقلام', table(['#', 'کالا', 'مقدار', 'جمع'], o.lines.map((l) => [
      h(l.lineNo), `${h(l.name)}${l.substituteName ? ` ← ${h(l.substituteName)}` : ''}`, h(`${l.qtyMilli / 1000} ${l.unit || ''}`),
      h(amount(l.lineTotalMinor, cur)),
    ]))],
    ['اختلاف‌ها', disputesHtml(f)],
    ['خطِ زمانیِ سفارش', table(['#', 'زمان', 'نوع', 'از ← به', 'کننده', 'دلیل'], f.timeline.map((e) => [
      h(e.seq), at(e.at), h(e.kind), `${h(e.fromStatus)} ← ${h(e.toStatus)}`,
      e.actorType === 'user' ? who(f.people, e.actorId) : h(e.actorType), h(e.reason || e.note),
    ]))],
    ['مأموریت‌های پیک', table(['تلاش', 'روش', 'پیک', 'وضعیت', 'نقدِ موردِ انتظار', 'نقدِ گرفته', 'کد', 'تحویل'], asg.map((a) => [
      h(a.attemptNo), h(a.mode), h(f.people.couriers[a.courierId] || a.externalName || a.courierId || '—'), h(a.status),
      h(amount(a.expectedCollectMinor, cur)), h(amount(a.cashCollectedMinor, cur)),
      a.confirmation.hasCode ? (a.confirmation.codeVerified ? 'تأیید شد' : 'تأیید نشد') : (a.confirmation.manualConfirm ? 'تأییدِ دستی' : '—'),
      at(a.deliveredAt),
    ]))],
    ['رویدادهای پیک (زمانِ دستگاه و سرور)', table(['تلاش', '#', 'نوع', 'از ← به', 'زمانِ سرور', 'زمانِ دستگاه', 'اختلافِ ساعت', 'دستگاه'],
      asg.flatMap((a) => a.events.map((e) => [
        h(a.attemptNo), h(e.seq), h(e.kind), `${h(e.fromStatus)} ← ${h(e.toStatus)}`, at(e.serverTime), at(e.deviceTime), skew(e.skewMs), h(e.deviceId),
      ])))
      + `<p class="none">${f.clock.warnings ? `⚠ ${h(f.clock.warnings)} رویداد با ساعتِ دستگاهِ بیش از ۵ دقیقه دور از سرور. ` : ''}${h(f.clock.note)}</p>`],
    ['موقعیت', table(['تلاش', '#', 'فاصله تا پین (متر)', 'زمان'], f.location.atDelivery.map((x) => [
      h((asg.find((a) => a.id === x.assignmentId) || {}).attemptNo), h(x.seq), h(x.distanceM), at(x.serverTime),
    ])) + `<p class="none">${h(f.location.note)}</p>`],
    ['اسکن‌ها', table(['نوع', 'ردیف', 'نتیجه', 'کننده', 'زمان'], [
      ...f.scans.pickEvents.map((e) => [h(e.kind), h(e.lineNo ?? '—'), `${h(e.outcome)} ${h(e.reject)}`, who(f.people, e.actorId), at(e.at)]),
      ...f.scans.shelfScans.map((s) => [`قفسه ${h(s.slot)}`, `کیسهٔ ${h(s.bag)}`, '—', who(f.people, s.by), at(s.at)]),
      ...f.scans.bagsAtPickup.map((b) => ['کیسه‌ها هنگامِ گرفتن', h(b.bagsScanned), '—', 'پیک', at(b.serverTime)]),
    ])],
    ['عکس‌ها (فقط فهرست)', table(['نوع', 'شناسه', 'اندازه', 'زمانِ سرور', 'زمانِ دستگاه'], [
      ...f.pickPhotos.map((p) => ['بسته‌بندی', h(p.id), h(p.size), at(p.createdAt), at(p.deviceTime)]),
      ...asg.flatMap((a) => (a.photos || []).map((p) => ['تحویل', h(p.id), h(p.size), at(p.createdAt), at(p.deviceTime)])),
      ...f.issues.flatMap((i) => i.photos.map((p) => ['گزارشِ مشتری', h(p.id), h(p.size), at(p.createdAt), '—'])),
    ]) + (f.delivery.photosVisible ? '' : '<p class="none">عکسِ تحویل فقط برای صاحب و مدیر دیده می‌شود.</p>')],
    ['گزارش‌های مشکلِ مشتری', table(['زمان', 'نوع', 'شرح', 'وضعیت', 'پاسخ'], f.issues.map((i) => [
      at(i.createdAt), h(i.kind), h(i.text), h(i.status), h(i.reply || ''),
    ]))],
    ['تماس‌ها', table(['نوع', 'زمان', 'جزئیات'], [
      ...f.contacts.courierCalls.map((c) => ['تماسِ پیک با مشتری', at(c.at), who(f.people, c.userId)]),
      ...f.contacts.conversations.map((c) => ['گفت‌وگو', at(c.startedAt), h(`مشتری ${c.fromCustomer} · دکان ${c.fromStaff}`)]),
    ]) + `<p class="none">${h(f.contacts.note)}</p>`],
  ];
  return `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><title>پروندهٔ اختلافِ سفارشِ ${h(o.orderNo)}</title>
<style>body{font-family:Vazirmatn,Tahoma,sans-serif;margin:24px;color:#111;background:#fff}h1{font-size:20px}h2{font-size:15px;margin-top:22px;border-bottom:1px solid #999}
table{border-collapse:collapse;width:100%;font-size:12px}th,td{border:1px solid #bbb;padding:3px 6px;text-align:right;vertical-align:top}
.none{color:#555;font-size:12px}.box{border:1px solid #999;padding:6px;margin:6px 0}@media print{body{margin:8mm}}</style></head><body>
<h1>پروندهٔ اختلافِ سفارشِ ${h(o.orderNo)}</h1>
<p class="none">ساخته‌شده ${at(f.generatedAt)} · فقط داده‌های ثبت‌شده؛ هیچ داده‌ای در ساختِ این پرونده عوض نشده است.</p>
${sections.map(([t, body]) => `<h2>${h(t)}</h2>${body}`).join('\n')}
</body></html>`;
}

module.exports = { printHtml, KIND_FA, OUTCOME_FA };
