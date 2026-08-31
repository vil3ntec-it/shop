/* ==========================================================
   توحید | حساب مشترک دکان و همگام‌سازی چنددستگاهی
   ----------------------------------------------------------
   منطق فروشگاه دست نمی‌خورد. این لایه فقط:
     ۱) دفتر دکان را بین گوشی‌های اعضا همگام می‌کند
     ۲) هر دستگاه آفلاین کار می‌کند و بعداً تفاوت‌ها را رد و بدل می‌کند
     ۳) وقتی موجودی کسری می‌آورد، روشن و کوتاه می‌گوید چرا

   چرا فروش‌ها با هم قاطی نمی‌شوند:
   هر رکورد شناسه‌ی یکتای خودش را دارد. دو نفر که آفلاین فروخته‌اند،
   رکوردهایشان کنار هم جمع می‌شود، نه روی هم.
   ========================================================== */
(function () {
  'use strict';

  const DATA_KEY   = 'tohid-shop-data-v1';       // همان کلیدی که خود برنامه استفاده می‌کند
  const SHADOW_KEY = 'tohid-sync-shadow-v1';     // عکس آخرین وضعیت همگام‌شده
  const STATE_KEY  = 'tohid-sync-state-v1';      // rev، زمان آخرین همگام‌سازی، اعضا
  const ACCT_KEY   = 'tohid-license-v1';         // توکن‌ها (مشترک با لایه اشتراک)
  const SERVER_KEY = 'tohid-license-server-url';
  const DEVICE_KEY = 'tohid-device-uid-v1';

  const COLLECTIONS = [
    'debtors', 'transactions', 'expenses',
    'products', 'warehouseEntries',
    'sales', 'saleItems', 'returns',
    'suppliers', 'purchases', 'supplierPayments',
    'stockMovements', 'priceHistory', 'auditLog',
  ];
  // این‌ها یک آرایه‌ی ساده‌اند و به‌صورت «تنظیمات مشترک» همگام می‌شوند
  const SETTING_LISTS = ['expenseCategories', 'productCategories', 'productUnits'];

  const AUTO_SYNC_MS = 3 * 60 * 1000;
  const REQ_TIMEOUT = 20000;
  // هر عضو یک بازه‌ی شماره فاکتور جدا می‌گیرد تا دو نفرِ آفلاین
  // فاکتور با شماره‌ی یکسان صادر نکنند
  const INVOICE_BLOCK = 100000;

  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));

  // ---------- ذخیره‌سازی ----------
  const read = (k, dflt) => { try { return JSON.parse(localStorage.getItem(k)) ?? dflt; } catch { return dflt; } };
  const write = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); return true; } catch (e) { console.error('[sync] ذخیره ناموفق', e); return false; } };

  const OWNER_KEY  = 'tohid-ledger-owner-v1';    // دفتر روی این مرورگر مال کدام حساب است
  const VAULT_KEY  = (uid) => 'tohid-ledger-vault-' + String(uid).replace(/[^A-Za-z0-9_-]/g, '_');

  const getData = () => read(DATA_KEY, null);
  const setData = (d) => write(DATA_KEY, d);
  const getState = () => read(STATE_KEY, { rev: 0, lastSyncAt: 0, members: [], shop: null, invoiceBlock: null });
  const setState = (patch) => write(STATE_KEY, Object.assign(getState(), patch));
  const getAcct = () => read(ACCT_KEY, {});
  const setAcct = (patch) => write(ACCT_KEY, Object.assign(getAcct(), patch));

  /* بعضی مرورگرها و WebViewها localStorage را می‌بندند (فایل محلی،
     حالت ناشناس، ذخیره‌سازی خاموش). آن‌جا خواندن خطا می‌دهد و اگر
     نگیریمش، این لایه وسط کار می‌ایستد. */
  const lsGet = (k) => { try { return localStorage.getItem(k); } catch { return null; } };
  const lsSet = (k, v) => { try { localStorage.setItem(k, v); } catch {} };
  const lsDel = (k) => { try { localStorage.removeItem(k); } catch {} };

  /* نشانی سرور از `license/api-config.js` می‌آید — یک جا برای همه.
     اگر آن فایل نبود، خواندنِ سادهٔ قبلی جایگزین می‌شود. */
  function serverUrl() {
    const cfg = window.TohidApiConfig;
    if (cfg) return cfg.baseUrl();
    return (lsGet(SERVER_KEY) || '').trim().replace(/\/+$/, '');
  }
  function setServerUrl(v) {
    const cfg = window.TohidApiConfig;
    if (cfg) { cfg.setBaseUrl(v); return; }
    lsSet(SERVER_KEY, String(v || '').trim().replace(/\/+$/, ''));
  }
  function deviceUid() {
    let u = lsGet(DEVICE_KEY) || '';
    if (!/^[A-Za-z0-9_-]{8,128}$/.test(u)) {
      const b = new Uint8Array(16); crypto.getRandomValues(b);
      u = Array.from(b).map(x => x.toString(16).padStart(2, '0')).join('');
      lsSet(DEVICE_KEY, u);
    }
    return u;
  }

  // ---------- ارتباط با سرور ----------
  class ApiError extends Error { constructor(m, c) { super(m); this.code = c; } }

  async function api(path, { method = 'GET', body, auth = true } = {}) {
    const base = serverUrl();
    if (!base) throw new ApiError('آدرس سرور تنظیم نشده است', 'no_server');
    const headers = { 'Content-Type': 'application/json' };
    const acct = getAcct();
    if (auth && acct.accessToken) headers.Authorization = 'Bearer ' + acct.accessToken;

    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), REQ_TIMEOUT);
    let res;
    try {
      res = await fetch(base + path, { method, headers, signal: ctrl.signal, body: body ? JSON.stringify(body) : undefined });
    } catch (e) {
      throw new ApiError(e.name === 'AbortError' ? 'سرور پاسخ نداد' : 'اتصال به سرور برقرار نشد', 'network');
    } finally { clearTimeout(timer); }

    let data = null; try { data = await res.json(); } catch {}
    if (!res.ok) {
      const err = new ApiError((data && data.error && data.error.message) || `خطای سرور (${res.status})`,
                               (data && data.error && data.error.code) || 'http_' + res.status);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  async function apiAuth(path, opts) {
    try { return await api(path, opts); }
    catch (e) {
      if (e.status !== 401) throw e;
      const acct = getAcct();
      if (!acct.refreshToken) throw e;
      const r = await api('/api/v1/auth/refresh', { method: 'POST', auth: false, body: { refreshToken: acct.refreshToken } });
      setAcct({ accessToken: r.accessToken });
      return api(path, opts);
    }
  }

  /* ---------- دفتر، به نام حساب ----------

     اشکالی که این می‌بندد:

     سرور حساب‌ها را کاملا جدا نگه می‌دارد — آن سمت درست بود. خرابی این
     طرف بود: روی یک مرورگر فقط یک دفتر وجود داشت و به هیچ حسابی بسته
     نبود. خروج از حساب توکن را پاک می‌کرد و `tohid-shop-data-v1` را
     دست‌نخورده می‌گذاشت.

       ۱) احمد وارد می‌شود، ۵۰۰ فروش ثبت می‌کند، خارج می‌شود
       ۲) محمود روی همان مرورگر وارد می‌شود
       ۳) سایه خالی است ← همه‌ی ۵۰۰ فروش احمد «تغییر تازه‌ی محمود» دیده
          می‌شود و صاف می‌رود داخل دکان او

     یک گوشی مشترک در دکان، یا حتی امتحان کردن دو حساب، کافی بود.

     قاعده‌ی حالا: هر دفتر یک صاحب دارد. دفتر بی‌صاحب (کسی که آفلاین
     شروع کرده و حالا اولین بار وارد می‌شود) به نام او سند می‌خورد —
     کار چند هفته‌اش را از دست نمی‌دهد. حساب دیگری که وارد شود، دفتر
     قبلی زیر نام صاحبش بایگانی می‌شود و دفتر خودش باز. هیچ داده‌ای پاک
     نمی‌شود. */

  /** دفتر روی میز را با حساب فعلی یکی می‌کند. اگر جابه‌جا شد، true. */
  function alignLedgerToAccount() {
    const me = String(getAcct().userId || '').trim();
    if (!me) return false;                      // حسابی در کار نیست

    const owner = String(lsGet(OWNER_KEY) || '').trim();
    if (owner === me) return false;             // همان حساب — دست نمی‌خوریم

    if (!owner) {                               // دفتر بی‌صاحب: به نام او
      lsSet(OWNER_KEY, me);
      return false;
    }

    // حساب عوض شده — دفتر قبلی می‌رود کنار، دفتر این حساب می‌آید
    const current = lsGet(DATA_KEY);
    if (current) lsSet(VAULT_KEY(owner), current); else lsDel(VAULT_KEY(owner));

    const mine = lsGet(VAULT_KEY(me));
    if (mine) lsSet(DATA_KEY, mine); else lsDel(DATA_KEY);

    // سایه و شماره‌ی تغییر مال دکان قبلی بودند؛ نگه داشتنشان یعنی همان
    // قاطی‌شدنی که قرار بود بسته شود
    lsDel(SHADOW_KEY);
    setState({ rev: 0, lastSyncAt: 0, invoiceBlock: null });
    lsSet(OWNER_KEY, me);
    return true;
  }

  // ---------- تشخیص تغییرات محلی ----------
  /** اثر انگشت کوتاه یک رکورد؛ برای فهمیدن اینکه ویرایش شده یا نه. */
  function fingerprint(obj) {
    const s = JSON.stringify(obj);
    let h = 0;
    for (let i = 0; i < s.length; i++) { h = (h * 31 + s.charCodeAt(i)) | 0; }
    return h + ':' + s.length;
  }

  /** تفاوت وضعیت فعلی با آخرین وضعیت همگام‌شده. */
  function collectLocalChanges() {
    const data = getData();
    if (!data) return { changes: [], settings: null };
    const shadow = read(SHADOW_KEY, {});
    const changes = [];
    const t = Date.now();

    for (const col of COLLECTIONS) {
      const arr = Array.isArray(data[col]) ? data[col] : [];
      const prev = shadow[col] || {};
      const seen = new Set();

      for (const rec of arr) {
        if (!rec || !rec.id) continue;
        seen.add(rec.id);
        const fp = fingerprint(rec);
        if (prev[rec.id] !== fp) {
          changes.push({ collection: col, id: rec.id, updatedAt: t, deleted: false, data: rec });
        }
      }
      // رکوردهایی که قبلاً بودند و حالا نیستند: حذف شده‌اند
      for (const id of Object.keys(prev)) {
        if (!seen.has(id)) {
          changes.push({ collection: col, id, updatedAt: t, deleted: true, data: null });
        }
      }
    }

    const settings = {};
    for (const k of SETTING_LISTS) if (Array.isArray(data[k])) settings[k] = data[k];
    return { changes, settings: { data: settings, updatedAt: t } };
  }

  /** ثبت وضعیت فعلی به عنوان «همگام‌شده». */
  function snapshotShadow() {
    const data = getData();
    if (!data) return;
    const shadow = {};
    for (const col of COLLECTIONS) {
      shadow[col] = {};
      for (const rec of (Array.isArray(data[col]) ? data[col] : [])) {
        if (rec && rec.id) shadow[col][rec.id] = fingerprint(rec);
      }
    }
    write(SHADOW_KEY, shadow);
  }

  // ---------- ادغام داده‌های دیگران ----------
  /**
   * تغییرات سرور را روی داده‌ی محلی می‌نشاند.
   * رکوردها بر اساس شناسه ادغام می‌شوند: چیزی پاک نمی‌شود مگر tombstone بیاید.
   */
  function mergeRemote(changes, settings) {
    const data = getData() || {};
    const meta = read('tohid-sync-meta-v1', {});
    let touched = 0;

    for (const col of COLLECTIONS) if (!Array.isArray(data[col])) data[col] = [];

    for (const ch of changes) {
      const col = ch.collection;
      if (!COLLECTIONS.includes(col)) continue;
      const arr = data[col];
      const idx = arr.findIndex(r => r && r.id === ch.id);

      if (ch.deleted) {
        if (idx >= 0) { arr.splice(idx, 1); touched++; }
      } else if (ch.data && ch.data.id) {
        // اگر رکورد دقیقاً همان چیزی است که داریم (مثلاً تغییر خودمان که
        // برگشته)، چیزی عوض نشده — وگرنه برنامه بی‌دلیل صفحه را تازه می‌کند
        const same = idx >= 0 && fingerprint(arr[idx]) === fingerprint(ch.data);
        if (idx >= 0) arr[idx] = ch.data; else arr.push(ch.data);
        if (!same) touched++;
        // چه کسی این رکورد را ساخته — برای توضیح کسری موجودی لازم است
        if (!meta[col]) meta[col] = {};
        meta[col][ch.id] = { userId: ch.userId || '', at: ch.updatedAt || 0 };
      }
    }

    if (settings && settings.data) {
      for (const k of SETTING_LISTS) {
        if (Array.isArray(settings.data[k]) && settings.data[k].length) {
          // اتحاد دو فهرست، بدون تکرار
          const merged = Array.from(new Set([...(data[k] || []), ...settings.data[k]]));
          data[k] = merged;
        }
      }
    }

    // داده همیشه نوشته می‌شود (حتی اگر فقط meta عوض شده باشد)
    setData(data);
    write('tohid-sync-meta-v1', meta);
    return touched;
  }

  // ---------- کسری موجودی ----------
  /**
   * محاسبه‌ی موجودی، دقیقاً به همان روشی که خود برنامه حساب می‌کند:
   * مجموع ورودی‌های انبار منهای فروش‌های لغو‌نشده (با کسر مرجوعی).
   */
  function stockOf(data, productId) {
    const inbound = (data.warehouseEntries || [])
      .filter(w => w.productId === productId)
      .reduce((s, w) => s + (w.units || 0), 0);
    const sold = (data.saleItems || [])
      .filter(si => {
        if (si.productId !== productId) return false;
        const sale = (data.sales || []).find(s => s.id === si.saleId);
        return !sale || sale.status !== 'cancelled';
      })
      .reduce((s, si) => s + ((si.quantity || 0) - (si.returnedQty || 0)), 0);
    return inbound - sold;
  }

  function memberName(userId) {
    const st = getState();
    const m = (st.members || []).find(x => x.userId === userId);
    if (!m) return '';
    return (m.name || m.email || m.phone || '').trim();
  }

  /** فهرست کالاهایی که موجودی‌شان منفی شده، با توضیح اینکه چرا. */
  function findShortages() {
    const data = getData();
    if (!data || !Array.isArray(data.products)) return [];
    const meta = read('tohid-sync-meta-v1', {});
    const myId = getAcct().userId || '';
    const out = [];

    for (const p of data.products) {
      const s = stockOf(data, p.id);
      if (s >= 0) continue;

      // چه کسی این کالا را فروخته؟ فروش‌های دیگران را جدا می‌کنیم
      const byOthers = {};
      let othersQty = 0, myQty = 0;
      for (const si of (data.saleItems || [])) {
        if (si.productId !== p.id) continue;
        const sale = (data.sales || []).find(x => x.id === si.saleId);
        if (sale && sale.status === 'cancelled') continue;
        const qty = (si.quantity || 0) - (si.returnedQty || 0);
        const owner = (meta.saleItems && meta.saleItems[si.id] && meta.saleItems[si.id].userId) || '';
        if (owner && owner !== myId) {
          const nm = memberName(owner) || 'یکی از اعضا';
          byOthers[nm] = (byOthers[nm] || 0) + qty;
          othersQty += qty;
        } else {
          myQty += qty;
        }
      }
      out.push({
        productId: p.id, name: p.name, unit: p.unit || '',
        shortage: Math.abs(s), stock: s,
        othersQty, myQty,
        byOthers: Object.entries(byOthers).map(([name, qty]) => ({ name, qty })),
      });
    }
    return out;
  }

  /** پیام کوتاه و روشن برای یک کسری. */
  function shortageMessage(sh) {
    const u = sh.unit ? ' ' + sh.unit : '';
    const who = sh.byOthers.length
      ? sh.byOthers.map(o => `${o.name} ${fa(o.qty)}${u}`).join('، ')
      : '';
    if (who) {
      return `«${sh.name}»: ${fa(sh.shortage)}${u} کسری. ${who} فروخته که تازه همگام شد. ` +
             `اگر جنس در دکان هست، ورودی انبار ثبت کنید.`;
    }
    return `«${sh.name}»: ${fa(sh.shortage)}${u} بیشتر از موجودی فروخته شده. ` +
           `یعنی ورودی انبارش ثبت نشده. اگر جنس در دکان هست، ورودی انبار ثبت کنید.`;
  }

  const fa = (n) => Number(n).toLocaleString('fa-IR');

  // ---------- همگام‌سازی ----------
  let syncing = false;

  /**
   * @param {object} opts
   * @param {boolean} opts.reload  اگر false باشد، پس از دریافت داده‌ی تازه
   *   صفحه خودکار تازه نمی‌شود (برای WebView اندروید یا تست‌ها مفید است).
   */
  async function sync({ silent = false, reload = true } = {}) {
    if (syncing) return { skipped: true };
    const acct = getAcct();
    if (!acct.accessToken) throw new ApiError('ابتدا وارد حساب شوید', 'no_account');
    if (!navigator.onLine) throw new ApiError('اینترنت در دسترس نیست', 'network');

    syncing = true;
    try {
      // ۰) پیش از هر چیز: دفتر روی میز باید مال همین حساب باشد
      const swapped = alignLedgerToAccount();

      // ۱) تغییرات خودم را بفرست
      const { changes, settings } = collectLocalChanges();
      let rejected = [];
      if (changes.length || settings) {
        const pushed = await apiAuth('/api/v1/shop/sync/push', {
          method: 'POST', body: { deviceId: deviceUid(), changes, settings },
        });
        snapshotShadow();

        /* تغییری که سرور رد کرد، بی‌صدا گم نمی‌شود.

           تا دیروز `conflicts` خوانده نمی‌شد. یعنی اگر شریک شما همان
           فاکتور را زودتر عوض کرده بود، ویرایش شما رد می‌شد، سایه
           «فرستاده شد» ثبت می‌کرد، و کار شما بی‌هیچ پیامی ناپدید
           می‌شد — بدتر: چون rev رکورد عوض نشده بود، در pull بعدی هم
           نمی‌آمد و دو طرف تا ابد ناهمگام می‌ماندند.

           حالا سرور نسخه‌ی خودش را همراه تعارض می‌فرستد؛ همان‌جا جای
           نسخه‌ی محلی می‌نشیند و کاربر می‌بیند چه چیزی اعمال نشد. */
        rejected = Array.isArray(pushed && pushed.conflicts) ? pushed.conflicts : [];
        if (rejected.length) {
          mergeRemote(
            rejected
              .filter(c => c && c.collection && c.id && (c.deleted || c.data))
              .map(c => ({ collection: c.collection, id: c.id, deleted: !!c.deleted, data: c.data })),
            null
          );
          snapshotShadow();
          if (!silent) toast(conflictMessage(rejected), 'warn');
        }
      }

      // ۲) تغییرات دیگران را بگیر (صفحه‌به‌صفحه)
      let st = getState();
      let since = st.rev || 0;
      let total = 0, guard = 0;
      let lastSettings = null;
      while (guard++ < 50) {
        const r = await apiAuth(`/api/v1/shop/sync/pull?since=${since}`);
        total += mergeRemote(r.changes || [], r.settings);
        lastSettings = r.settings;
        since = r.rev;
        if (!r.hasMore) break;
      }

      // ۳) بعد از ادغام، وضعیت همگام‌شده دوباره ثبت می‌شود تا
      //    داده‌ی تازه‌رسیده به‌عنوان «تغییر محلی» دوباره فرستاده نشود
      snapshotShadow();
      setState({ rev: since, lastSyncAt: Date.now() });

      await refreshShopInfo();
      const shortages = findShortages();
      renderShortageBanner(shortages);

      if (total > 0 && reload !== false) offerReload(total);

      return { pushed: changes.length, pulled: total, rev: since, shortages, rejected, swapped };
    } finally { syncing = false; }
  }

  /**
   * پیام فارسی تعارض‌ها.
   *
   * دو حالت جدا می‌شوند چون کاری که کاربر باید بکند فرق دارد: یکی
   * «شریکت زودتر عوض کرده» است و آن یکی «اجازه‌ات نمی‌رسد».
   */
  function conflictMessage(rejected) {
    const stale = rejected.filter(c => c.reason === 'stale').length;
    const denied = rejected.filter(c => c.reason === 'delete_not_allowed').length;
    const other = rejected.length - stale - denied;
    const parts = [];
    if (stale) parts.push(`${fa(stale)} مورد چون نسخه‌ی تازه‌تری روی سرور بود`);
    if (denied) parts.push(`${fa(denied)} مورد چون اجازه‌ی حذفش را نداشتید`);
    if (other) parts.push(`${fa(other)} مورد دیگر`);
    return `${fa(rejected.length)} تغییر اعمال نشد: ${parts.join('، ')}. نسخه‌ی سرور جایش نشست.`;
  }

  async function refreshShopInfo() {
    try {
      const r = await apiAuth('/api/v1/shop/me');
      setState({ shop: r.shop, members: r.members || [] });
      assignInvoiceBlock(r);
      return r;
    } catch { return null; }
  }

  /**
   * هر عضو یک بازه‌ی شماره فاکتور جدا می‌گیرد.
   * بدون این کار، دو نفر که آفلاین می‌فروشند هر دو فاکتور #۱۰۰۰ صادر می‌کنند.
   */
  function assignInvoiceBlock(shopInfo) {
    const st = getState();
    if (st.invoiceBlock) return;
    const me = getAcct().userId;
    const idx = (shopInfo.members || []).findIndex(m => m.userId === me);
    if (idx < 0) return;
    const block = INVOICE_BLOCK * (idx + 1);
    const data = getData();
    if (data && (!data.nextInvoiceNo || data.nextInvoiceNo < block)) {
      data.nextInvoiceNo = block;
      setData(data);
    }
    setState({ invoiceBlock: block });
  }

  // ---------- رابط کاربری ----------
  function toast(msg, kind) {
    let el = $('#sync-toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'sync-toast';
      document.body.appendChild(el);
    }
    el.className = 'sync-toast show ' + (kind || '');
    el.textContent = msg;
    clearTimeout(el._t);
    el._t = setTimeout(() => el.classList.remove('show'), 4000);
  }

  /** آیا الان reload بی‌خطر است؟ (وسط کار کاربر نباشیم) */
  function safeToReload() {
    if ($$('.modal-scrim.open').length) return false;
    const cart = read('tohid-shop-draft-cart-v1', []);
    if (Array.isArray(cart) && cart.length) return false;
    return true;
  }

  function offerReload(count) {
    if (safeToReload()) { location.reload(); return; }
    let bar = $('#sync-reload-bar');
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'sync-reload-bar';
      bar.innerHTML = '<span></span><button type="button" class="sync-btn sync-btn-sm">به‌روزرسانی</button>';
      bar.querySelector('button').addEventListener('click', () => location.reload());
      document.body.appendChild(bar);
    }
    bar.querySelector('span').textContent = `${fa(count)} تغییر تازه از دکان رسید.`;
    bar.classList.add('show');
  }

  function renderShortageBanner(shortages) {
    let bar = $('#stock-alert-bar');
    if (!shortages.length) { if (bar) bar.remove(); return; }
    if (!bar) {
      bar = document.createElement('div');
      bar.id = 'stock-alert-bar';
      bar.innerHTML = '<div class="sa-head"><b></b><button type="button" class="sync-btn sync-btn-sm">جزئیات</button></div>';
      bar.querySelector('button').addEventListener('click', () => openShortageModal());
      document.body.appendChild(bar);
    }
    bar.querySelector('b').textContent =
      `${fa(shortages.length)} کالا بیشتر از موجودی فروخته شده`;
    bar.classList.add('show');
  }

  function openShortageModal() {
    const shortages = findShortages();
    let m = $('#stock-alert-modal');
    if (!m) {
      m = document.createElement('div');
      m.id = 'stock-alert-modal';
      m.className = 'sync-scrim';
      m.innerHTML = `
        <div class="sync-box">
          <div class="sync-head">
            <h3>کسری موجودی</h3>
            <button type="button" class="sync-close" aria-label="بستن">&times;</button>
          </div>
          <div class="sync-body"><div id="shortage-list"></div>
            <p class="sync-hint">تا وقتی ورودی انبار ثبت نشود، فروش این کالاها ممکن نیست.</p>
          </div>
        </div>`;
      m.querySelector('.sync-close').addEventListener('click', () => m.classList.remove('open'));
      m.addEventListener('mousedown', e => { if (e.target === m) m.classList.remove('open'); });
      document.body.appendChild(m);
    }
    $('#shortage-list', m).innerHTML = shortages.length
      ? shortages.map(s => `<div class="shortage-item">
           <b>${esc(s.name)}</b>
           <span>${esc(shortageMessage(s))}</span>
         </div>`).join('')
      : '<p class="sync-hint">کسری موجودی وجود ندارد.</p>';
    m.classList.add('open');
  }

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // ---------- خودکار ----------
  async function autoSync(reason) {
    if (!autoEnabled) return;
    const acct = getAcct();
    if (!acct.accessToken || !serverUrl() || !navigator.onLine) return;
    const st = getState();
    if (reason === 'interval' && Date.now() - (st.lastSyncAt || 0) < AUTO_SYNC_MS) return;
    try { await sync({ silent: true }); }
    catch (e) { if (e.code !== 'network' && e.code !== 'no_account') console.warn('[sync]', e.message); }
  }

  let autoTimer = null;
  let autoEnabled = true;

  /** روشن/خاموش کردن همگام‌سازی خودکار (برای WebView اندروید یا تست). */
  function setAuto(on) {
    autoEnabled = !!on;
    if (!autoEnabled && autoTimer) { clearInterval(autoTimer); autoTimer = null; }
    if (autoEnabled && !autoTimer) startAutoTimer();
    return autoEnabled;
  }
  function startAutoTimer() {
    autoTimer = setInterval(() => { if (autoEnabled) autoSync('interval'); }, 60 * 1000);
    if (autoTimer.unref) autoTimer.unref();
  }

  function init() {
    renderShortageBanner(findShortages());
    if (autoEnabled) autoSync('startup');
    startAutoTimer();
    window.addEventListener('online', () => {
      if (!autoEnabled) return;
      toast('اینترنت وصل شد — در حال همگام‌سازی…');
      autoSync('online');
    });
  }

  // ---------- API عمومی ----------
  window.TohidShop = {
    sync, autoSync, setAuto,
    get autoEnabled() { return autoEnabled; },
    get state() { return getState(); },
    get account() { const a = getAcct(); return { userId: a.userId, label: a.userLabel, loggedIn: !!a.accessToken }; },
    api, apiAuth, serverUrl, setServerUrl, deviceUid,
    findShortages, shortageMessage, stockOf, openShortageModal, offerReload,
    collectLocalChanges, snapshotShadow, mergeRemote, refreshShopInfo,
    alignLedgerToAccount, conflictMessage,
    _toast: toast,
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();
