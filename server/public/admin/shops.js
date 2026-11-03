'use strict';
/**
 * پنلِ مدیر — زبانهٔ «دکان‌ها» (فهرست، کارتِ دکان، اشتراک). از `app.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان.
 *
 * اسکریپتِ کلاسیک: پس از `app.js` و به ترتیبِ index.html بار می‌شود و ابزارهای همان را به کار می‌برد
 * ($، el، call، msg، fa، dateTime، token…). فقط تعریف دارد؛ هنگامِ بار شدن چیزی اجرا نمی‌شود
 * (دکمه‌ها را `start.js` پس از DOMContentLoaded وصل می‌کند).
 */
// ---------- دکان‌ها ----------
async function loadShops() {
  //  کارتِ کدِ دکان زیرِ همین تب است، پس با همین یکی می‌آید
  loadShopCodes().catch(err => console.error(err));
  fillPlanSelect($('svip-plan'));
  const q = encodeURIComponent($('shop-q').value.trim());
  const out = await call('GET', `/admin/shops?limit=100&q=${q}`);
  const body = $('shops-body');
  body.innerHTML = '';
  for (const s of out.shops) {
    const tr = el('tr');
    tr.appendChild(el('td', null, s.name || '—'));
    tr.appendChild(el('td', null, s.owner_name || '—'));
    const phone = el('td', null, s.owner_phone || s.owner_email || '—');
    phone.dir = 'ltr';
    tr.appendChild(phone);
    tr.appendChild(el('td', null, fa(s.members)));
    const st = el('td');
    st.appendChild(badge(s.sub_status || 'none'));
    tr.appendChild(st);
    tr.appendChild(el('td', null, s.ends_at ? date(s.ends_at) : '—'));
    const act = el('td');
    const btn = el('button', 'btn btn-sm', 'مدیریت');
    btn.onclick = () => openShop(s.id);
    act.appendChild(btn);
    tr.appendChild(act);
    body.appendChild(tr);
  }
  if (!out.shops.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'دکانی پیدا نشد');
    td.colSpan = 7;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

async function openShop(id) {
  const d = await call('GET', `/admin/shops/${id}`);
  currentShop = d;
  $('shop-detail').classList.remove('hidden');
  //  ⚠️ منتظرش نمی‌مانیم: پشتیبان‌ها از دیسک می‌آیند و نباید باز شدنِ
  //  صفحهٔ دکان را عقب بیندازند
  loadAccountBackups('shop', id, 'shop').catch(err => console.error(err));
  $('shop-title').textContent = `دکان: ${d.shop.name}`;

  const sum = $('shop-summary');
  sum.innerHTML = '';
  const ent = d.entitlement;
  const line = el('div', 'row');
  line.appendChild(badge(ent.source === 'trial' ? 'trial' : ent.subscription.status));
  line.appendChild(el('span', 'muted',
    ent.source === 'trial'
      ? `دوره آزمایشی — ${fa(ent.trial.daysLeft)} روز مانده`
      : ent.subscription.endsAt
        ? `پایان: ${date(ent.subscription.endsAt)} (${fa(ent.subscription.daysLeft)} روز مانده)`
        : 'اشتراکی ثبت نشده است'));
  line.appendChild(el('span', 'muted', `ساخت دکان: ${date(d.shop.createdAt)}`));
  //  شناسه — همان که برنامه به سرور می‌فرستد و فهرستِ کانالِ آزمایشی می‌خواهد
  const sid = el('span', 'muted', d.shop.id);
  sid.dir = 'ltr';
  sid.id = 'shop-id';
  line.appendChild(sid);
  if (adminRole === 'superadmin') {
    const b = el('button', 'btn btn-ghost btn-sm', 'به کانالِ آزمایشی');
    b.id = 'btn-shop-beta';
    b.onclick = () => addShopToBeta(d.shop.id, b);
    line.appendChild(b);
  }
  sum.appendChild(line);

  const members = $('shop-members');
  members.innerHTML = '';
  for (const m of d.members) {
    const tr = el('tr');
    tr.appendChild(el('td', null, m.name || '—'));
    const ph = el('td', null, m.phone || m.email || '—');
    ph.dir = 'ltr';
    tr.appendChild(ph);
    tr.appendChild(el('td', null, ROLE_FA[m.role] || m.role));
    const st = el('td');
    st.appendChild(badge(m.status));
    tr.appendChild(st);
    tr.appendChild(el('td', null, date(m.created_at)));
    members.appendChild(tr);
  }

  const counts = $('shop-counts');
  counts.innerHTML = '';
  const LABELS = {
    products: 'محصولات', warehouseEntries: 'ورودی انبار', sales: 'فروش‌ها',
    saleItems: 'اقلام فروش', debtors: 'قرض‌داران', transactions: 'پرداخت‌ها',
    expenses: 'مصارف', suppliers: 'تأمین‌کننده', purchases: 'خریدها',
  };
  for (const [key, label] of Object.entries(LABELS)) {
    counts.appendChild(el('label', 'feat', `${label}: ${fa(d.counts[key] || 0)}`));
  }
  $('shop-detail').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function grantSubscription() {
  if (!currentShop) return;
  const node = $('shop-msg');
  try {
    const days = $('in-days').value.trim();
    //  ⛔ F0-14: با کلیدِ یکتایی — دو بار کلیک یک دوره می‌سازد، نه دو
    const out = await callOnce('grant-shop', 'POST', '/admin/subscriptions', {
      shopId: currentShop.shop.id,
      plan: $('in-plan').value,
      days: days ? Number(days) : null,
      graceDays: Number($('in-grace').value || 0),
      //  سقفِ دستگاه — خالی یعنی همان پیش‌فرضِ پلن
      ...($('in-max-devices').value.trim() ? { maxDevices: Number($('in-max-devices').value) } : {}),
      note: $('in-note').value.trim(),
      ...reasonBody('in-reason'),
    });
    if (!out) return;               // کلیکِ دوم وقتی اولی هنوز در راه است
    $('in-reason').value = '';
    msg(node, 'اشتراک ثبت شد.', 'ok');
    await Promise.all([loadStats(), loadShops(), openShop(currentShop.shop.id)]);
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

async function setSubStatus(status) {
  if (!currentShop) return;
  const node = $('shop-msg');
  const sub = currentShop.subscriptions?.[0];
  if (!sub) return msg(node, 'این دکان اشتراکی ندارد.', 'warn');
  try {
    await call('POST', `/admin/subscriptions/${sub.id}/status`, { status, ...reasonBody('in-reason') });
    $('in-reason').value = '';
    msg(node, 'وضعیت اشتراک عوض شد.', 'ok');
    await Promise.all([loadStats(), loadShops(), openShop(currentShop.shop.id)]);
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}
