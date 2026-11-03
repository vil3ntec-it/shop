'use strict';
/**
 * پنلِ مدیر — زبانه‌ها (`LOADERS`، `openTab`) و راه‌اندازی. از `app.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان.
 *
 * اسکریپتِ کلاسیک: پس از `app.js` و به ترتیبِ index.html بار می‌شود و ابزارهای همان را به کار می‌برد
 * ($، el، call، msg، fa، dateTime، token…). `LOADERS` هنگامِ بار شدن تابعِ همهٔ زبانه‌ها را
 * می‌خواهد، پس این پرونده آخرِ زبانه‌هاست و پیش از `ops.js` و `image-bank.js` (که زبانهٔ خود را به
 * `LOADERS` می‌افزایند) بار می‌شود.
 */
const LOADERS = {
  shops: loadShops, pump: loadPump, users: loadUsers, subs: loadSubs,
  sales: loadSales, payments: loadPayments, discounts: loadDiscounts, notices: loadNotices,
  requests: loadRequests, plans: loadPlans, support: loadSupport,
  delivery: loadDelivery, backups: loadBackups, health: loadHealth, audit: loadAudit,
};

function openTab(name) {
  for (const tab of document.querySelectorAll('.tab')) {
    tab.classList.toggle('active', tab.dataset.tab === name);
  }
  for (const key of Object.keys(LOADERS)) {
    $(`tab-${key}`).classList.toggle('hidden', key !== name);
  }
  LOADERS[name]().catch(err => console.error(err));
}

// ---------- راه‌اندازی ----------
document.addEventListener('DOMContentLoaded', () => {
  $('btn-login').onclick = login;
  $('in-password').addEventListener('keydown', (e) => { if (e.key === 'Enter') login(); });
  $('in-totp').addEventListener('keydown', (e) => { if (e.key === 'Enter') login(); });
  //  F0-15
  $('btn-2fa').onclick = () => {
    $('twofa-panel').classList.remove('hidden');
    load2fa().catch(err => msg($('twofa-msg'), err.message, 'bad'));
  };
  $('btn-2fa-close').onclick = () => { $('twofa-panel').classList.add('hidden'); showRecoveryCodes([]); $('twofa-pass').value = ''; };
  $('btn-2fa-start').onclick = () => twofa('start');
  $('btn-2fa-enable').onclick = () => twofa('enable');
  $('btn-2fa-renew').onclick = () => twofa('renew');
  $('btn-2fa-disable').onclick = () => twofa('disable');
  $('btn-logout').onclick = async () => {
    try { await call('POST', '/admin/logout', {}); } catch { /* بی‌اهمیت */ }
    logout();
  };
  $('btn-refresh').onclick = () => refresh().catch(err => alert(err.message));
  $('btn-shop-search').onclick = () => loadShops();
  $('btn-health-load').onclick = () => loadHealth().catch(err => alert(err.message));
  $('btn-beta-save').onclick = () => saveBeta();
  $('shop-q').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadShops(); });
  //  پمپ‌بنزین‌ها
  $('btn-pump-search').onclick = () => loadPump();
  $('btn-pu-search').onclick = () => loadPumpUsers();
  //  دکمه بینِ «همه» و «رو به پایان» می‌چرخد تا جای اضافه نگیرد
  $('btn-psub-expiring').onclick = (e) => {
    const showAll = e.target.dataset.mode === 'expiring';
    e.target.dataset.mode = showAll ? '' : 'expiring';
    e.target.textContent = showAll ? 'رو به پایان' : 'همه';
    loadPumpSubs(!showAll);
  };
  $('pump-q').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadPump(); });
  $('btn-close-pump').onclick = () => {
    $('pump-detail').classList.add('hidden');
    currentStation = null;
  };
  $('btn-pump-grant').onclick = grantPumpSubscription;
  $('btn-pump-suspend').onclick = () => setPumpSubStatus('suspended');
  $('btn-pump-activate').onclick = () => setPumpSubStatus('active');
  $('btn-pump-cancel').onclick = () => {
    if (confirm('اشتراک این پمپ لغو شود؟')) setPumpSubStatus('cancelled');
  };
  $('btn-pvip-make').onclick = () =>
    makeCode({ path: '/admin/pump/vip-codes', prefix: 'pvip', reload: loadPumpCodes });
  $('btn-svip-make').onclick = () =>
    makeCode({ path: '/admin/vip-codes', prefix: 'svip', reload: loadShopCodes });

  //  پشتیبانی و پیامِ همگانی
  $('btn-sup-search').onclick = () => loadSupport().catch(err => alert(err.message));
  $('sup-q').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadSupport(); });
  $('sup-app').onchange = () => loadSupport();
  $('sup-status').onchange = () => loadSupport();
  $('btn-sup-close').onclick = () => {
    $('sup-detail').classList.add('hidden');
    currentThread = null;
  };
  $('btn-sup-send').onclick = sendReply;
  $('btn-sup-pending').onclick = () => setThreadStatus('pending');
  $('btn-sup-closed').onclick = () => setThreadStatus('closed');
  $('btn-sup-reopen').onclick = () => setThreadStatus('open');
  $('btn-bc-send').onclick = sendBroadcast;

  //  ایمیل و پوش
  $('btn-mail-save').onclick = saveMail;
  $('btn-mail-test').onclick = testMail;
  $('btn-push-save').onclick = savePush;
  $('btn-tg-save').onclick = saveTelegram;
  $('btn-user-search').onclick = () => loadUsers();
  $('btn-sub-filter').onclick = () => loadSubs();
  $('btn-close-shop').onclick = () => { $('shop-detail').classList.add('hidden'); currentShop = null; };
  $('btn-grant').onclick = grantSubscription;
  $('btn-suspend').onclick = () => setSubStatus('suspended');
  $('btn-activate').onclick = () => setSubStatus('active');
  $('btn-cancel').onclick = () => { if (confirm('اشتراک این دکان لغو شود؟')) setSubStatus('cancelled'); };
  $('btn-save-cfg').onclick = async () => {
    try {
      await call('PATCH', '/admin/config', {
        trial_days: $('cfg-trial').value,
        whatsapp_number: $('cfg-wa').value,
        whatsapp_message: $('cfg-wamsg').value,
        currency: $('cfg-currency').value,
        pump_currency: $('cfg-pump-currency').value,
        pump_trial_days: $('cfg-pump-trial').value,
      });
      msg($('cfg-msg'), 'ذخیره شد.', 'ok');
    } catch (err) { msg($('cfg-msg'), err.message, 'bad'); }
  };
  $('btn-backup-now').onclick = async () => {
    msg($('backup-msg'), 'در حال گرفتن پشتیبان…', 'warn');
    try {
      const out = await call('POST', '/admin/backups', { kind: 'manual' });
      msg($('backup-msg'), `پشتیبان ساخته شد (${fa(Math.round(out.bytes / 1024))} کیلوبایت).`, 'ok');
      await loadBackups();
    } catch (err) { msg($('backup-msg'), err.message, 'bad'); }
  };

  //  فروش · پرداخت · تخفیف · مرکزِ اعلان
  $('btn-sal-filter').onclick = () => loadSalesSubs().catch(err => msg($('sales-msg'), err.message, 'bad'));
  $('btn-exp-show').onclick = () => loadExpiring().catch(err => msg($('exp-msg'), err.message, 'bad'));
  $('btn-exp-remind').onclick = remindExpiring;
  $('btn-dl-save').onclick = saveDownloads;
  $('dl-app').onchange = () => loadDownloads().catch(() => {});
  $('btn-pay-add').onclick = addPayment;
  $('btn-pay-refresh').onclick = () => loadPayments().catch(err => msg($('pay-msg'), err.message, 'bad'));
  $('btn-dc-make').onclick = makeDiscountCode;
  $('dc-app').onchange = () => fillPlanOptions().catch(() => {});
  $('btn-cmp-make').onclick = makeCampaign;
  $('btn-ph-show').onclick = () => loadPriceHistory().catch(err => alert(err.message));
  $('btn-nt-count').onclick = countAudience;
  $('btn-nt-save').onclick = () => saveNotice().catch(() => {});
  $('btn-nt-preview').onclick = previewNotice;
  $('btn-nt-test').onclick = testNotice;
  $('btn-nt-send').onclick = sendNotice;
  $('btn-nt-list').onclick = () => loadNoticeList().catch(err => msg($('nt-msg'), err.message, 'bad'));
  $('btn-nt-system').onclick = async () => {
    try {
      const out = await call('POST', '/admin/notices/run-system', {});
      msg($('nt-msg'), `${fa(out.checked)} اشتراک سنجیده شد — ${fa(out.expiring)} رو به پایان، ${fa(out.expired)} منقضی.`, 'ok');
      await loadNoticeList();
    } catch (err) { msg($('nt-msg'), err.message, 'bad'); }
  };
  $('nt-template').onchange = () => applyTemplate().catch(err => msg($('nt-msg'), err.message, 'bad'));

  for (const tab of document.querySelectorAll('.tab')) {
    tab.onclick = () => openTab(tab.dataset.tab);
  }

  if (token) {
    call('GET', '/admin/me')
      .then(async (out) => {
        $('who').textContent = `${out.admin.name || out.admin.username} — ${out.admin.role === 'superadmin' ? 'مدیر ارشد' : 'مدیر'}`;
        setRole(out.admin.role);
        show();
        await refresh();
      })
      .catch(() => logout());
  }
});
