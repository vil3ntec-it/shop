/* ==========================================================
   پنل «دکان و همگام‌سازی» — ورود، ساخت دکان، دعوت اعضا
   روی TohidShop سوار است و منطق فروشگاه را دست نمی‌زند.
   ========================================================== */
(function () {
  'use strict';
  const S = () => window.TohidShop;
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  /* بعضی مرورگرها و WebViewها localStorage را می‌بندند (فایل محلی،
     حالت ناشناس، ذخیره‌سازی خاموش). آن‌جا خواندن خطا می‌دهد و اگر
     نگیریمش، این لایه وسط کار می‌ایستد. */
  const lsGet = (k) => { try { return localStorage.getItem(k); } catch { return null; } };
  const lsSet = (k, v) => { try { localStorage.setItem(k, v); } catch {} };
  const lsDel = (k) => { try { localStorage.removeItem(k); } catch {} };
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fa = (n) => Number(n).toLocaleString('fa-IR');

  function fmtWhen(ms) {
    if (!ms) return 'هرگز';
    const diff = Date.now() - ms;
    if (diff < 60000) return 'همین حالا';
    if (diff < 3600000) return `${fa(Math.floor(diff / 60000))} دقیقه پیش`;
    if (diff < 86400000) return `${fa(Math.floor(diff / 3600000))} ساعت پیش`;
    return `${fa(Math.floor(diff / 86400000))} روز پیش`;
  }

  let el = null;

  function ensure() {
    if (el) return el;
    el = document.createElement('div');
    el.id = 'shop-modal';
    el.className = 'sync-scrim';
    el.innerHTML = `
      <div class="sync-box">
        <div class="sync-head">
          <h3>دکان و همگام‌سازی</h3>
          <button type="button" class="sync-close" aria-label="بستن">&times;</button>
        </div>
        <div class="sync-body">
          <div id="shop-status"></div>

          <div class="sync-section">
            <p class="sync-hint">آدرسی که از بیرون خانه هم در دسترس باشد، تا شاگرد از خانه‌اش بتواند همگام کند.</p>
          </div>

          <!-- ورود / ثبت‌نام -->
          <div class="sync-section" id="shop-auth">
            <div class="sync-tabs">
              <button type="button" class="sync-tab sync-tab-on" data-tab="login">ورود</button>
              <button type="button" class="sync-tab" data-tab="register">ثبت‌نام</button>
            </div>
            <div data-pane="login">
              <label class="sync-label">ایمیل یا شماره موبایل</label>
              <input type="text" id="shop-id" class="sync-input" dir="ltr" autocomplete="username">
              <label class="sync-label">رمز عبور</label>
              <input type="password" id="shop-pw" class="sync-input" dir="ltr" autocomplete="current-password">
              <button type="button" class="sync-btn sync-btn-block" id="shop-login">ورود</button>
            </div>
            <div data-pane="register" hidden>
              <label class="sync-label">نام</label>
              <input type="text" id="shop-rname" class="sync-input">
              <label class="sync-label">ایمیل</label>
              <input type="email" id="shop-remail" class="sync-input" dir="ltr">
              <label class="sync-label">شماره موبایل (اختیاری)</label>
              <input type="tel" id="shop-rphone" class="sync-input" dir="ltr">
              <label class="sync-label">رمز عبور (حداقل ۸ کاراکتر)</label>
              <input type="password" id="shop-rpw" class="sync-input" dir="ltr" autocomplete="new-password">
              <button type="button" class="sync-btn sync-btn-block" id="shop-register">ساخت حساب</button>
            </div>
          </div>

          <!-- ساخت یا پیوستن -->
          <div class="sync-section" id="shop-setup" hidden>
            <p class="sync-hint">اگر صاحب دکان هستید یک دکان بسازید. اگر شاگرد هستید، کدی که صاحب دکان می‌دهد را وارد کنید.</p>
            <label class="sync-label">نام دکان</label>
            <input type="text" id="shop-name" class="sync-input" placeholder="دکان توحید">
            <button type="button" class="sync-btn sync-btn-block" id="shop-create">ساخت دکان</button>
            <div class="sync-or">یا</div>
            <label class="sync-label">کد دعوت</label>
            <input type="text" id="shop-code" class="sync-input" dir="ltr" placeholder="ABCD-1234" maxlength="9">
            <button type="button" class="sync-btn sync-btn-ghost sync-btn-block" id="shop-join">پیوستن به دکان</button>
          </div>

          <!-- اعضا -->
          <div class="sync-section" id="shop-team" hidden>
            <div class="sync-row-between">
              <b class="sync-label" style="margin:0;">اعضای دکان</b>
              <span class="sync-hint" id="shop-count" style="margin:0;"></span>
            </div>
            <div id="shop-members"></div>
            <button type="button" class="sync-btn sync-btn-ghost sync-btn-block" id="shop-invite">ساخت کد دعوت</button>
            <div id="shop-invite-out"></div>
          </div>

          <div class="sync-section" id="shop-actions" hidden>
            <button type="button" class="sync-btn sync-btn-block" id="shop-sync-now">همگام‌سازی حالا</button>
            <button type="button" class="sync-btn sync-btn-ghost sync-btn-block" id="shop-logout">خروج از حساب</button>
          </div>

          <div id="shop-msg"></div>
        </div>
      </div>`;
    document.body.appendChild(el);

    $('.sync-close', el).addEventListener('click', close);
    el.addEventListener('mousedown', e => { if (e.target === el) close(); });
    $$('[data-tab]', el).forEach(t => t.addEventListener('click', () => {
      $$('[data-tab]', el).forEach(x => x.classList.toggle('sync-tab-on', x === t));
      $$('[data-pane]', el).forEach(p => { p.hidden = p.dataset.pane !== t.dataset.tab; });
    }));

    $('#shop-login', el).addEventListener('click', () => guard('#shop-login', doLogin));
    $('#shop-register', el).addEventListener('click', () => guard('#shop-register', doRegister));
    $('#shop-create', el).addEventListener('click', () => guard('#shop-create', doCreate));
    $('#shop-join', el).addEventListener('click', () => guard('#shop-join', doJoin));
    $('#shop-invite', el).addEventListener('click', () => guard('#shop-invite', doInvite));
    $('#shop-sync-now', el).addEventListener('click', () => guard('#shop-sync-now', doSync));
    $('#shop-logout', el).addEventListener('click', doLogout);
    return el;
  }

  function msg(text, kind) {
    const box = $('#shop-msg', el);
    box.innerHTML = text ? `<div class="sync-msg sync-msg-${kind || 'ok'}">${esc(text)}</div>` : '';
  }

  async function guard(sel, fn) {
    const btn = $(sel, el);
    const old = btn.textContent;
    btn.disabled = true; btn.textContent = 'صبر کنید…';
    msg('');
    try { await fn(); }
    catch (e) { msg(e.message || 'خطای ناشناخته', 'bad'); }
    finally { btn.disabled = false; btn.textContent = old; render(); }
  }

  async function doLogin() {
    const identifier = $('#shop-id', el).value.trim();
    const password = $('#shop-pw', el).value;
    if (!identifier || !password) throw new Error('نام کاربری و رمز عبور را وارد کنید');
    const r = await S().api('/api/v1/auth/login', { method: 'POST', auth: false, body: { identifier, password } });
    const store = JSON.parse(lsGet('tohid-license-v1') || '{}');
    Object.assign(store, {
      accessToken: r.accessToken, refreshToken: r.refreshToken, userId: r.user.id,
      userLabel: r.user.name || r.user.email || r.user.phone || '',
    });
    lsSet('tohid-license-v1', JSON.stringify(store));
    $('#shop-pw', el).value = '';
    await S().refreshShopInfo();
    msg('وارد شدید.', 'ok');
  }

  async function doRegister() {
    await S().api('/api/v1/auth/register', { method: 'POST', auth: false, body: {
      name: $('#shop-rname', el).value.trim(),
      email: $('#shop-remail', el).value.trim() || undefined,
      phone: $('#shop-rphone', el).value.trim() || undefined,
      password: $('#shop-rpw', el).value,
    } });
    msg('حساب ساخته شد. حالا از تب «ورود» وارد شوید.', 'ok');
  }

  async function doCreate() {
    const name = $('#shop-name', el).value.trim();
    await S().apiAuth('/api/v1/shop/create', { method: 'POST', body: { name, maxMembers: 5 } });
    await S().refreshShopInfo();
    await S().sync({ silent: true });
    msg('دکان ساخته شد و اطلاعات این گوشی روی سرور رفت.', 'ok');
  }

  async function doJoin() {
    const code = $('#shop-code', el).value.trim();
    if (!code) throw new Error('کد دعوت را وارد کنید');
    await S().apiAuth('/api/v1/shop/join', { method: 'POST', body: { code } });
    await S().refreshShopInfo();
    await S().sync({ silent: true });
    msg('به دکان پیوستید. اطلاعات دکان روی این گوشی آمد.', 'ok');
  }

  async function doInvite() {
    const r = await S().apiAuth('/api/v1/shop/invite', { method: 'POST', body: { role: 'staff' } });
    $('#shop-invite-out', el).innerHTML =
      `<div class="invite-code"><span>${esc(r.code)}</span>
         <button type="button" class="sync-btn sync-btn-sm" data-copy>کپی</button></div>
       <p class="sync-hint">این کد را به شاگردتان بدهید. یک هفته معتبر است و فقط یک بار کار می‌کند.</p>`;
    const btn = $('#shop-invite-out [data-copy]', el);
    btn.addEventListener('click', async () => {
      try { await navigator.clipboard.writeText(r.code); btn.textContent = 'کپی شد'; }
      catch { btn.textContent = 'کپی نشد'; }
    });
  }

  async function doSync() {
    const r = await S().sync();
    msg(`همگام شد — ${fa(r.pushed)} تغییر رفت، ${fa(r.pulled)} تغییر آمد.`, 'ok');
  }

  function doLogout() {
    const keep = {};
    lsSet('tohid-license-v1', JSON.stringify(keep));
    lsDel('tohid-sync-state-v1');
    lsDel('tohid-sync-shadow-v1');
    render();
    msg('از حساب خارج شدید. اطلاعات دکان روی این گوشی دست‌نخورده است.', 'ok');
  }

  function render() {
    ensure();
    const acct = S().account;
    const st = S().state;
    const shop = st.shop;

    $('#shop-auth', el).hidden = acct.loggedIn;
    $('#shop-setup', el).hidden = !acct.loggedIn || !!shop;
    $('#shop-team', el).hidden = !shop;
    $('#shop-actions', el).hidden = !acct.loggedIn;

    const rows = [];
    if (acct.loggedIn) rows.push(['کاربر', acct.label || '—']);
    if (shop) {
      rows.push(['دکان', shop.name]);
      rows.push(['نقش شما', shop.myRole === 'owner' ? 'صاحب دکان' : 'شاگرد']);
      rows.push(['آخرین همگام‌سازی', fmtWhen(st.lastSyncAt)]);
    }
    $('#shop-status', el).innerHTML = rows.length
      ? `<div class="sync-status">${rows.map(r =>
          `<div class="sync-row"><span>${esc(r[0])}</span><b>${esc(r[1])}</b></div>`).join('')}</div>`
      : '<p class="sync-hint">برای اینکه دفتر دکان روی چند گوشی یکی باشد، وارد حساب شوید.</p>';

    if (shop) {
      const members = st.members || [];
      $('#shop-count', el).textContent = `${fa(members.length)} از ${fa(shop.maxMembers)} نفر`;
      $('#shop-members', el).innerHTML = members.map(m => `
        <div class="member-row">
          <div>
            <b>${esc(m.name || m.email || m.phone || 'کاربر')}${m.isMe ? ' (شما)' : ''}</b>
            <span>${m.role === 'owner' ? 'صاحب دکان' : 'شاگرد'}</span>
          </div>
          ${(shop.myRole === 'owner' && !m.isMe)
            ? `<button type="button" class="sync-btn sync-btn-sm sync-btn-danger" data-remove="${esc(m.userId)}">حذف</button>` : ''}
        </div>`).join('');
      $$('#shop-members [data-remove]', el).forEach(b => b.addEventListener('click', async () => {
        if (!confirm('این عضو از دکان حذف شود؟ اطلاعات ثبت‌شده‌اش باقی می‌ماند.')) return;
        b.disabled = true;
        try {
          await S().apiAuth(`/api/v1/shop/members/${b.dataset.remove}/remove`, { method: 'POST' });
          await S().refreshShopInfo(); render();
        } catch (e) { msg(e.message, 'bad'); b.disabled = false; }
      }));
      $('#shop-invite', el).hidden = shop.myRole !== 'owner';
    }
  }

  function open() { ensure(); render(); el.classList.add('open'); }
  function close() { if (el) el.classList.remove('open'); }

  window.TohidShopUI = { open, close, render };

  /*
   * پنل «دکان مشترک و همگام‌سازی» دیگر در تنظیمات نمی‌نشیند: کارِ حساب
   * و کلیدها به پنل «حساب و اشتراک» خودِ برنامه منتقل شد و آن‌جا یک‌جا
   * دیده می‌شود. خودِ پنجره باقی می‌ماند و با TohidShopUI.open() باز
   * می‌شود، ولی دکمه‌ای در تنظیمات ندارد.
   */
  const MOUNT_IN_SETTINGS = false;

  function mountEntry() {
    if (!MOUNT_IN_SETTINGS) return;
    const settings = document.getElementById('page-settings');
    if (!settings || document.getElementById('shop-entry-panel')) return;
    const panel = document.createElement('div');
    panel.className = 'panel';
    panel.id = 'shop-entry-panel';
    panel.innerHTML = `
      <div class="panel-head"><h2>دکان مشترک و همگام‌سازی</h2>
        <span class="settings-badge" id="shop-entry-badge">—</span></div>
      <p class="settings-hint">دفتر دکان را بین گوشی خودتان و شاگردها یکی نگه می‌دارد. هر کس آفلاین کار می‌کند و وقتی اینترنت وصل شد، همه‌چیز با هم جمع می‌شود.</p>
      <div class="settings-actions-row">
        <button class="btn btn-primary btn-sm" id="btn-open-shop">مدیریت دکان</button>
        <button class="btn btn-secondary btn-sm" id="btn-shop-sync">همگام‌سازی حالا</button>
      </div>`;
    // بالای پنل پشتیبان‌گیری قرار می‌گیرد
    const backup = Array.from(settings.querySelectorAll('.panel'))
      .find(p => /پشتیبان‌گیری از اطلاعات/.test(p.textContent));
    if (backup) settings.insertBefore(panel, backup); else settings.appendChild(panel);

    document.getElementById('btn-open-shop').addEventListener('click', open);
    document.getElementById('btn-shop-sync').addEventListener('click', async (e) => {
      const b = e.currentTarget, old = b.textContent;
      b.disabled = true; b.textContent = 'در حال همگام‌سازی…';
      try { const r = await S().sync(); S()._toast(`همگام شد — ${fa(r.pushed)} رفت، ${fa(r.pulled)} آمد`); }
      catch (err) { S()._toast(err.message, 'bad'); }
      finally { b.disabled = false; b.textContent = old; updateBadge(); }
    });
    updateBadge();
  }

  function updateBadge() {
    const badge = document.getElementById('shop-entry-badge');
    if (!badge) return;
    const st = S().state, acct = S().account;
    if (!acct.loggedIn) { badge.textContent = 'وارد نشده‌اید'; badge.classList.add('warn'); }
    else if (!st.shop) { badge.textContent = 'دکان ساخته نشده'; badge.classList.add('warn'); }
    else { badge.textContent = st.shop.name; badge.classList.remove('warn'); }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mountEntry, { once: true });
  else mountEntry();
  setInterval(updateBadge, 15000);
})();
