'use strict';
/**
 * زبانهٔ «بانکِ تصاویر» پنلِ مدیر — روی APIِ موجودِ `/api/admin/image-bank`.
 *
 * ورودِ گروهی (IMG-01، IMG-BANK-01): دسته با مجوز ⇐ فایل‌های پوشه (هر فایل
 * جدا سنجیده می‌شود) ⇐ CSV با پیش‌نمایش ⇐ ثبت ⇐ گزارشِ موفق/ناموفق/مبهم و
 * گزارشِ فایل‌های خراب (IMG-03، IMG-PRO-09). هیچ چیزی این‌جا عمومی نمی‌شود؛ همه
 * به `pending` می‌روند و تأیید کارِ مدیرِ ارشد است.
 *
 * صفِ بررسی (IMG-02، IMG-PRO-07، IMG-BANK-17، IMG-09): هر عکس با وضعیت و
 * دلیلِ تطبیق، کالای وصل یا کالاهای ممکن با SKU/بارکد/نوع/اندازه؛ تطبیقِ مبهم
 * فقط با انتخابِ انسانی وصل می‌شود و انتشار فقط کارِ مدیرِ ارشد با دلیل است.
 *
 * تکراری (IMG-BANK-13) فقط نشان و صافی است — نه حذف، نه ادغام. بایگانی
 * (IMG-BANK-15) فقط عکسِ منتشرشده، فقط مدیرِ ارشد با دلیل.
 *
 * مقایسه (IMG-PRO-05، IMG-07): اصل (نسخهٔ ۱) کنارِ هر نسخهٔ دیگر، با «اندازهٔ واقعی» و
 * پیمایشِ هم‌گامِ دو قاب و زمینهٔ شطرنجی؛ نسخهٔ تازه بی‌صدا جاری نمی‌شود — مدیرِ ارشد با
 * دلیل جاری یا ردش می‌کند و رد، اصل و نسخهٔ جاری را دست‌نخورده نگه می‌دارد.
 *
 * پس از `app.js` بار می‌شود ($، el، call، msg، fa، dateTime، token، logout، LOADERS).
 * ⛔ همه درونِ یک تابعِ بی‌نام: نامِ هم‌سان با `app.js` کلِ فایل را می‌خواباند.
 */
(() => {
  const IB = '/admin/image-bank';
  const UPLOAD_PARALLEL = 4;

  //  برچسبِ آدمیزادِ کدهای خطا و نشان‌ها (همان کدهای `lib/image-bank.js`)
  const ERR_FA = {
    image_type: 'فایل عکس نیست یا پسوندش جعلی است',
    image_corrupt: 'عکسِ خراب یا نیمه‌کاره',
    image_empty: 'فایلِ خالی',
    too_large: 'حجمِ فایل بیش از سقف',
    image_too_large_dimensions: 'ابعادِ بیش از حد (بمبِ فشرده‌سازی)',
    file_missing: 'فایلِ این ردیف بارگذاری نشده',
    file_failed: 'فایلِ این ردیف خراب بود',
    bad_barcode: 'بارکدِ نامعتبر',
    bad_gtin: 'بارکدِ نامعتبر',
    identity_required: 'نه بارکد، نه SKU، نه نام',
    name_required: 'نامِ کالا لازم است',
    image_path_required: 'ستونِ مسیرِ عکس خالی است',
    batch_paused: 'دسته متوقف بود',
    batch_cancelled: 'دسته لغو شده بود',
    batch_done: 'دسته بسته بود',
    row_error: 'خطای پیش‌بینی‌نشده',
    upload_failed: 'بارگذاری نشد',
  };
  const FLAG_FA = { extension_mismatch: 'پسوند با نوع نمی‌خواند', too_small: 'عکسِ کوچک' };
  const ROW_FA = {
    ok: 'موفق', failed: 'ناموفق', ambiguous: 'مبهم', needs_review: 'نیازمندِ بررسی',
    duplicate: 'تکراری', skipped: 'رد شد',
  };
  const ROW_CLS = { ok: 'b-ok', failed: 'b-bad', ambiguous: 'b-warn', needs_review: 'b-warn', duplicate: 'b-mute', skipped: 'b-mute' };
  const BATCH_FA = { open: 'باز', paused: 'متوقف', cancelled: 'لغوشده', done: 'تمام‌شده' };
  const errFa = (code) => ERR_FA[code] || code || '—';

  //  تطبیق: وضعیت و دلیل (همان کدهای `lib/image-bank.js` و `image-identity`)
  const MATCH_FA = { exact: 'قطعی', probable: 'محتمل', ambiguous: 'مبهم', unconfirmed: 'تأییدنشده' };
  const MATCH_CLS = { exact: 'b-ok', probable: 'b-warn', ambiguous: 'b-bad', unconfirmed: 'b-mute' };
  const REASON_FA = {
    product_id: 'شناسهٔ کالا در CSV', barcode_exact: 'بارکدِ یکسان', sku_exact: 'SKUِ یکسان',
    sku_many: 'چند کالا با همین SKU', name_same: 'فقط نامِ یکسان', name_exact: 'نامِ دقیقاً یکسان',
    name_many: 'چند کالای هم‌نام', new_product: 'کالای تازه', shop_proposal: 'پیشنهادِ دکان',
    previous_selection: 'انتخابِ پیشین', admin_linked: 'وصل به دستِ مدیر', brand_match: 'برندِ هم‌خوان',
    brand_same: 'برندِ یکسان', size: 'اندازهٔ متفاوت', mass: 'وزنِ متفاوت', volume: 'حجمِ متفاوت',
    brand: 'برندِ متفاوت', variant: 'نوع/طعمِ متفاوت', count: 'شمارِ متفاوت', pack_count: 'شمارِ بسته متفاوت',
    packaging: 'بسته‌بندیِ متفاوت', barcode_different: 'بارکدِ متفاوت', name_different: 'نامِ متفاوت',
  };
  const IMG_FA = { NEEDS_REVIEW: 'در انتظارِ بررسی', APPROVED: 'منتشرشده', REJECTED: 'ردشده', ARCHIVED: 'بایگانی', DRAFT: 'پیش‌نویس' };
  const reasonsFa = (list) => (list || []).map((r) => REASON_FA[r] || r).join('، ') || '—';
  const sizeOf = (p) => [p.size_value, p.size_unit].filter(Boolean).join(' ') + (Number(p.pack_count) > 1 ? ` × ${fa(p.pack_count)}` : '');
  const productLine = (p) => (p ? [p.name, p.brand, p.variant, sizeOf(p), p.packaging].filter(Boolean).join(' · ') : '—');
  const keyLine = (p) => (p ? [p.sku && `SKU ${p.sku}`, p.gtin && `بارکد ${p.gtin}`].filter(Boolean).join(' · ') || '—' : '—');

  let status = null;
  let batchId = '';
  let csvText = '';
  const localFails = new Map();

  const badge = (cls, text) => el('span', `badge ${cls}`, text);
  const td = (text, cls = '') => el('td', cls, text);

  /** PUT با بدنهٔ خام (فایل) — `call()` بدنه را JSON می‌کند. */
  async function putFile(path, file) {
    const res = await fetch(API + path, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: file,
    });
    if (res.status === 401) { logout(); throw new Error('نشست تمام شد، دوباره وارد شوید'); }
    let json = null;
    try { json = await res.json(); } catch { json = null; }
    if (!res.ok) {
      const err = new Error(json?.error?.message || `خطای ${res.status}`);
      err.code = json?.error?.code || (res.status === 413 ? 'too_large' : 'upload_failed');
      throw err;
    }
    return json;
  }

  function counts(box, c) {
    box.innerHTML = '';
    const keys = Object.keys(c || {});
    if (!keys.length) { box.appendChild(el('span', 'muted', '—')); return; }
    for (const k of keys) box.appendChild(badge(ROW_CLS[k] || 'b-mute', `${ROW_FA[k] || k}: ${fa(c[k])}`));
  }

  // ---------- دسته‌ها ----------
  async function loadBatches() {
    const body = $('ib-batches');
    body.innerHTML = '';
    const out = await call('GET', `${IB}/batches`);
    if (!out.items.length) {
      const tr = el('tr');
      const c = td('هنوز دسته‌ای ساخته نشده', 'muted');
      c.colSpan = 6;
      tr.appendChild(c);
      body.appendChild(tr);
    }
    for (const b of out.items) {
      const tr = el('tr');
      tr.dataset.batch = b.id;
      tr.appendChild(td(dateTime(b.created_at)));
      tr.appendChild(td(`${b.license_source || '—'} · ${b.license_status}${b.commercial_use ? ' · تجاری' : ''}`));
      const st = el('td');
      st.appendChild(badge(b.status === 'open' ? 'b-ok' : b.status === 'paused' ? 'b-warn' : 'b-mute', BATCH_FA[b.status] || b.status));
      tr.appendChild(st);
      tr.appendChild(td(`${fa(b.files)}${b.files_failed ? ` (${fa(b.files_failed)} خراب)` : ''}`));
      tr.appendChild(td(`${fa(b.rows)}${b.rows_failed ? ` · ${fa(b.rows_failed)} ناموفق` : ''}${b.rows_ambiguous ? ` · ${fa(b.rows_ambiguous)} مبهم` : ''}`));
      const act = el('td');
      const pick = el('button', 'btn btn-sm btn-ghost', 'باز کردن');
      pick.onclick = () => useBatch(b.id).catch((err) => msg($('ib-msg'), err.message, 'bad'));
      act.appendChild(pick);
      tr.appendChild(act);
      body.appendChild(tr);
    }
  }

  async function useBatch(id) {
    batchId = id;
    localFails.clear();
    $('ib-batch-id').textContent = id;
    $('ib-work').classList.remove('hidden');
    await loadReport();
  }

  async function createBatch() {
    const out = await call('POST', `${IB}/batches`, {
      license_source: $('ib-lic-source').value.trim(),
      license_status: $('ib-lic-status').value,
      commercial_use: $('ib-lic-commercial').checked,
      note: $('ib-note').value.trim(),
    });
    msg($('ib-msg'), 'دستهٔ تازه ساخته شد. حالا فایل‌ها و بعد CSV را بدهید.', 'ok');
    await useBatch(out.id);
    await loadBatches();
  }

  // ---------- فایل‌ها ----------
  async function uploadFiles() {
    if (!batchId) return;
    const picked = [...$('ib-folder').files, ...$('ib-files').files];
    if (!picked.length) { msg($('ib-msg'), 'فایلی انتخاب نشده است', 'warn'); return; }
    const progress = $('ib-progress');
    let done = 0;
    let failed = 0;
    let i = 0;
    const show = () => { progress.textContent = `${fa(done)} از ${fa(picked.length)} فایل · ${fa(failed)} ناموفق`; };
    show();
    $('btn-ib-upload').disabled = true;
    const worker = async () => {
      while (i < picked.length) {
        const f = picked[i++];
        try {
          const out = await putFile(`${IB}/batches/${encodeURIComponent(batchId)}/files/${encodeURIComponent(f.name)}`, f);
          if (out.status === 'failed') failed++;
          else localFails.delete(f.name);
        } catch (err) {
          //  ردِ پیش از سرور (حجم، شبکه) هم در گزارش بماند
          failed++;
          localFails.set(f.name, err.code || 'upload_failed');
        }
        done++;
        show();
      }
    };
    try {
      await Promise.all(Array.from({ length: Math.min(UPLOAD_PARALLEL, picked.length) }, worker));
    } finally {
      $('btn-ib-upload').disabled = false;
    }
    await loadReport();
    await loadBatches();
  }

  // ---------- CSV ----------
  async function readCsv() {
    const f = $('ib-csv').files[0];
    csvText = f ? await f.text() : '';
    const lines = csvText.split(/\r\n|\n|\r/).filter((l) => l.trim()).length - 1;
    $('ib-csv-info').textContent = f ? `${fa(Math.max(0, lines))} ردیف` : '';
    if (status && lines > status.rows_max) {
      msg($('ib-msg'), `حداکثر ${fa(status.rows_max)} ردیف در هر بار؛ فایل را تقسیم کنید.`, 'bad');
    }
  }

  async function sendRows(dry) {
    if (!batchId) return;
    if (!csvText) { msg($('ib-msg'), 'اول فایلِ CSV را انتخاب کنید', 'warn'); return; }
    const btns = [$('btn-ib-dry'), $('btn-ib-run')];
    btns.forEach((b) => { b.disabled = true; });
    try {
      const out = await call('POST', `${IB}/batches/${encodeURIComponent(batchId)}/rows`, { csv: csvText, dry_run: dry });
      counts($('ib-preview'), out.counts);
      $('ib-preview-label').textContent = dry ? 'پیش‌نمایش (هنوز چیزی ثبت نشده):' : 'ثبت شد:';
      if (!dry) await Promise.all([loadReport(), loadBatches()]);
    } finally {
      btns.forEach((b) => { b.disabled = false; });
    }
  }

  async function setBatchStatus(to) {
    if (!batchId) return;
    if (to === 'cancelled' && !confirm('لغوِ دسته: عکس‌های بررسی‌نشدهٔ همین دسته «رد» می‌شوند (پاک نه). ادامه؟')) return;
    const out = await call('POST', `${IB}/batches/${encodeURIComponent(batchId)}/status`, { status: to });
    msg($('ib-msg'), `دسته ${BATCH_FA[out.status] || out.status} شد${out.rejected ? ` · ${fa(out.rejected)} عکس رد شد` : ''}.`, 'ok');
    await Promise.all([loadReport(), loadBatches()]);
  }

  // ---------- گزارش ----------
  async function loadReport() {
    if (!batchId) return;
    const rep = await call('GET', `${IB}/batches/${encodeURIComponent(batchId)}/report`);
    $('ib-batch-status').textContent = BATCH_FA[rep.status] || rep.status;
    counts($('ib-rep-counts'), rep.counts);
    //  IMG-03 / IMG-PRO-09: فایل‌های خراب — هر کدام با دلیل
    const fbody = $('ib-rep-files');
    fbody.innerHTML = '';
    const failedFiles = [...rep.files.failed.map((f) => [f.name, f.error]), ...[...localFails].map(([n, e]) => [n, e])];
    $('ib-rep-files-count').textContent = `${fa(rep.files.counts.ok || 0)} سالم · ${fa(failedFiles.length)} خراب`;
    for (const [name, error] of failedFiles) {
      const tr = el('tr');
      tr.dataset.file = name;
      tr.appendChild(td(name));
      tr.appendChild(td(errFa(error)));
      tr.appendChild(td(error, 'muted'));
      fbody.appendChild(tr);
    }
    //  ردیف‌های غیرِ موفق
    const rbody = $('ib-rep-rows');
    rbody.innerHTML = '';
    for (const r of rep.rows.filter((x) => x.status !== 'ok')) {
      const tr = el('tr');
      tr.dataset.row = String(r.row_no);
      tr.appendChild(td(fa(r.row_no)));
      const st = el('td');
      st.appendChild(badge(ROW_CLS[r.status] || 'b-mute', ROW_FA[r.status] || r.status));
      tr.appendChild(st);
      tr.appendChild(td(r.error ? errFa(r.error) : r.candidates.length ? `${fa(r.candidates.length)} کالای ممکن — انتخابِ انسانی` : '—'));
      tr.appendChild(td(r.error || '', 'muted'));
      rbody.appendChild(tr);
    }
    const paused = rep.status === 'paused';
    const closed = rep.status === 'cancelled' || rep.status === 'done';
    $('btn-ib-pause').classList.toggle('hidden', paused || closed);
    $('btn-ib-resume').classList.toggle('hidden', !paused);
    $('btn-ib-cancel').classList.toggle('hidden', closed);
    for (const id of ['btn-ib-upload', 'btn-ib-dry', 'btn-ib-run']) $(id).disabled = rep.status !== 'open';
  }

  async function downloadCsv() {
    if (!batchId) return;
    const res = await fetch(`${API}${IB}/batches/${encodeURIComponent(batchId)}/report?format=csv`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error(`خطای ${res.status}`);
    const url = URL.createObjectURL(await res.blob());
    const a = el('a');
    a.href = url;
    a.download = `batch-${batchId}-errors.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }

  // ---------- صفِ بررسی ----------
  let rvCursor = null;
  const rvItems = new Map();

  async function loadReview(reset = true) {
    const body = $('ib-review');
    if (reset) { body.innerHTML = ''; rvItems.clear(); rvCursor = null; }
    const q = new URLSearchParams({ status: $('ib-rv-status').value, space: $('ib-rv-space').value });
    if (rvCursor) q.set('cursor', rvCursor);
    const out = await call('GET', `${IB}/review?${q}`);
    rvCursor = out.next_cursor;
    $('btn-ib-rv-more').classList.toggle('hidden', !rvCursor);
    const onlyDups = $('ib-rv-dups').checked;
    for (const it of out.items) {
      rvItems.set(it.id, it);
      if (onlyDups && !it.duplicate_of.length) continue;
      const tr = el('tr');
      tr.dataset.image = it.id;
      tr.appendChild(td(it.id.slice(-8)));
      const prod = el('td');
      if (it.product) prod.textContent = productLine(it.product);
      else if (it.candidates.length) prod.textContent = `${fa(it.candidates.length)} کالای ممکن — انتخابِ انسانی`;
      else prod.textContent = '—';
      tr.appendChild(prod);
      tr.appendChild(td(it.product ? keyLine(it.product) : '—', 'muted'));
      const m = el('td');
      m.appendChild(badge(MATCH_CLS[it.match_status] || 'b-mute', MATCH_FA[it.match_status] || it.match_status || '—'));
      tr.appendChild(m);
      tr.appendChild(td(reasonsFa(it.match_reasons), 'ib-reasons'));
      const flags = el('td');
      if (it.duplicate_of.length) flags.appendChild(badge('b-warn', `تکراری × ${fa(it.duplicate_of.length)}`));
      for (const f of it.quality_flags) flags.appendChild(badge('b-mute', FLAG_FA[f] || f));
      if (it.from_shop) flags.appendChild(badge('b-mute', 'از دکان'));
      if (it.versions_pending) flags.appendChild(badge('b-warn', `نسخهٔ تازه × ${fa(it.versions_pending)}`));
      tr.appendChild(flags);
      tr.appendChild(td(it.license ? `${it.license.status}${it.license.commercial_use ? ' · تجاری' : ''}` : '—'));
      const act = el('td');
      const open = el('button', 'btn btn-sm btn-ghost', 'بررسی');
      open.onclick = () => openDetail(it.id).catch((err) => msg($('ib-rv-msg'), err.message, 'bad'));
      act.appendChild(open);
      tr.appendChild(act);
      body.appendChild(tr);
    }
    if (reset && !body.children.length) {
      const tr = el('tr');
      const c = td(onlyDups ? 'در این صفحه عکسِ تکراری نیست' : 'چیزی در این صف نیست', 'muted');
      c.colSpan = 8;
      tr.appendChild(c);
      body.appendChild(tr);
    }
  }

  /** فایل با توکن ⇐ data: (CSPِ پنل img-src 'self' data: است، نه blob:). */
  async function imageData(url) {
    const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
    if (!res.ok) throw new Error(`عکس خوانده نشد (${res.status})`);
    const blob = await res.blob();
    return new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = () => reject(new Error('عکس خوانده نشد'));
      r.readAsDataURL(blob);
    });
  }

  let current = null;
  async function openDetail(id) {
    const it = rvItems.get(id);
    if (!it) return;
    current = it;
    const box = $('ib-detail');
    box.classList.remove('hidden');
    msg($('ib-dt-msg'), '');
    $('ib-dt-id').textContent = it.id;
    $('ib-dt-status').textContent = IMG_FA[it.status] || it.status;
    $('ib-dt-match').innerHTML = '';
    $('ib-dt-match').appendChild(badge(MATCH_CLS[it.match_status] || 'b-mute', MATCH_FA[it.match_status] || '—'));
    $('ib-dt-reasons').textContent = reasonsFa(it.match_reasons);
    $('ib-dt-product').textContent = it.product ? `${productLine(it.product)} (${keyLine(it.product)})` : 'به کالایی وصل نیست';
    await showDups(it);
    //  کالاهای ممکن: SKU/بارکد/نوع/اندازه کنارِ هم تا انتخاب انسانی باشد (IMG-09، IMG-PRO-07)
    const cb = $('ib-dt-cands');
    cb.innerHTML = '';
    $('ib-dt-cands-box').classList.toggle('hidden', !it.candidates.length);
    for (const p of it.candidates) {
      const tr = el('tr');
      tr.dataset.product = p.id;
      tr.appendChild(td(p.missing ? '(کالا پیدا نشد)' : p.name));
      tr.appendChild(td(p.brand || '—'));
      tr.appendChild(td(p.variant || '—'));
      tr.appendChild(td(sizeOf(p) || '—'));
      tr.appendChild(td(p.packaging || '—'));
      tr.appendChild(td(keyLine(p), 'muted'));
      const a = el('td');
      if (!p.missing && it.status === 'NEEDS_REVIEW') {
        const b = el('button', 'btn btn-sm', 'همین کالاست');
        b.onclick = () => pick(p).catch((err) => msg($('ib-dt-msg'), err.message, 'bad'));
        a.appendChild(b);
      }
      tr.appendChild(a);
      cb.appendChild(tr);
    }
    const sup = adminRole === 'superadmin';
    $('btn-ib-approve').classList.toggle('hidden', !sup || it.status !== 'NEEDS_REVIEW');
    $('btn-ib-reject').classList.toggle('hidden', !sup || it.status !== 'NEEDS_REVIEW');
    $('btn-ib-archive').classList.toggle('hidden', !sup || it.status !== 'APPROVED');
    $('ib-dt-super').classList.toggle('hidden', sup || !['NEEDS_REVIEW', 'APPROVED'].includes(it.status));
    const img = $('ib-dt-img');
    img.removeAttribute('src');
    img.alt = 'در حالِ بار…';
    if (it.file?.url) {
      img.src = await imageData(it.file.url);
      img.alt = `عکسِ ${it.id}`;
    } else img.alt = 'فایلی نیست';
    await showVersions(it);
  }

  // ---------- مقایسهٔ اصل و نسخه (IMG-PRO-05، IMG-07) ----------
  const REVIEW_FA = { approved: 'پذیرفته', needs_review: 'منتظرِ بررسی', rejected: 'ردشده' };
  let cmp = null;   // { id, d (adminDetail), pick }

  /**
   * قابِ مقایسه زیرِ جزئیاتِ عکس — همین‌جا ساخته می‌شود تا `index.html` (بالای سقفِ اندازه) بزرگ‌تر نشود.
   * اصل در قابِ اول (راست)، نسخه در دوم؛ زمینهٔ شطرنجی و ۱:۱ در `admin.css` (`.ib-cmp`).
   */
  function buildCompareBox() {
    const box = el('div', 'hidden');
    box.id = 'ib-dt-cmp-box';
    const h = el('h2', '', 'مقایسهٔ اصل و نسخه — اصل هرگز عوض نمی‌شود');
    h.style.marginTop = '14px';
    box.appendChild(h);
    const vers = el('div', 'row');
    vers.id = 'ib-dt-versions';
    vers.style.cssText = 'flex-wrap:wrap;gap:6px;';
    box.appendChild(vers);
    const zr = el('div', 'row');
    zr.style.marginTop = '8px';
    const zl = el('label');
    zl.style.margin = '0';
    const zoom = el('input');
    zoom.type = 'checkbox';
    zoom.id = 'ib-cmp-zoom';
    zl.append(zoom, ' اندازهٔ واقعی (۱:۱) — دو قاب با هم می‌چرخند');
    zr.appendChild(zl);
    box.appendChild(zr);
    const grid = el('div', 'grid grid-2');
    grid.style.marginTop = '8px';
    for (const k of ['a', 'b']) {
      const fig = el('figure');
      fig.style.margin = '0';
      const cap = el('figcaption', 'muted');
      cap.id = `ib-cmp-${k}-cap`;
      const pane = el('div', 'ib-cmp');
      pane.id = `ib-cmp-${k}`;
      const img = el('img');
      img.id = `ib-cmp-${k}-img`;
      img.alt = '';
      pane.appendChild(img);
      fig.append(cap, pane);
      grid.appendChild(fig);
    }
    box.appendChild(grid);
    const act = el('div', 'row');
    act.style.marginTop = '8px';
    const ok = el('button', 'btn btn-sm', 'جاری کردنِ این نسخه');
    ok.id = 'btn-ib-ver-approve';
    const no = el('button', 'btn btn-sm btn-danger', 'ردِ این نسخه');
    no.id = 'btn-ib-ver-reject';
    const note = el('span', 'muted');
    note.id = 'ib-cmp-note';
    act.append(ok, no, note);
    box.appendChild(act);
    $('ib-detail').appendChild(box);
  }

  /** نسخه‌های عکس؛ فقط وقتی بیش از یکی هست. پیش‌فرض: تازه‌ترین نسخهٔ منتظر، وگرنه جاری. */
  async function showVersions(it) {
    const box = $('ib-dt-cmp-box');
    const d = await call('GET', `${IB}/images/${encodeURIComponent(it.id)}`);
    const vs = d.versions || [];
    if (vs.length < 2) { box.classList.add('hidden'); cmp = null; return; }
    const waiting = vs.filter((v) => v.review === 'needs_review' && v.version !== d.current_version);
    const def = (waiting[waiting.length - 1] || vs.find((v) => v.version === d.current_version && v.version !== 1) || vs[vs.length - 1]).version;
    cmp = { id: it.id, d, pick: def };
    box.classList.remove('hidden');
    renderVersions();
    await renderCompare();
  }

  function versionLabel(v) {
    const kind = v.version === 1 ? 'اصل' : `پردازش‌شده${v.tool ? ` (${v.tool})` : ''}`;
    return `نسخهٔ ${fa(v.version)} · ${kind} · ${REVIEW_FA[v.review] || v.review}${v.version === cmp.d.current_version ? ' · جاری' : ''}`;
  }

  function renderVersions() {
    const row = $('ib-dt-versions');
    row.innerHTML = '';
    for (const v of cmp.d.versions) {
      if (v.version === 1) continue;                     // اصل همیشه در قابِ راست است
      const b = el('button', `btn btn-sm btn-ghost ib-ver${v.version === cmp.pick ? ' on' : ''}`, versionLabel(v));
      b.dataset.version = String(v.version);
      b.onclick = () => { cmp.pick = v.version; renderVersions(); renderCompare().catch((err) => msg($('ib-dt-msg'), err.message, 'bad')); };
      row.appendChild(b);
    }
  }

  async function renderCompare() {
    const { d } = cmp;
    const a = d.versions.find((v) => v.version === 1);
    const b = d.versions.find((v) => v.version === cmp.pick);
    $('ib-cmp-a-cap').textContent = `${versionLabel(a)} — ${fa(a.width)}×${fa(a.height)}`;
    $('ib-cmp-b-cap').textContent = `${versionLabel(b)} — ${fa(b.width)}×${fa(b.height)}`;
    const [ia, ib] = [$('ib-cmp-a-img'), $('ib-cmp-b-img')];
    ia.removeAttribute('src'); ib.removeAttribute('src');
    [ia.src, ib.src] = await Promise.all([imageData(a.urls.original), imageData(b.urls.original)]);
    ia.alt = 'اصل'; ib.alt = `نسخهٔ ${b.version}`;
    const sup = adminRole === 'superadmin';
    const open = b.version !== d.current_version && b.review !== 'rejected';
    $('btn-ib-ver-approve').classList.toggle('hidden', !sup || !open || d.status !== 'APPROVED');
    $('btn-ib-ver-reject').classList.toggle('hidden', !sup || !open || !['NEEDS_REVIEW', 'APPROVED'].includes(d.status));
    $('ib-cmp-note').textContent = b.version === d.current_version ? 'همین نسخه جاری است'
      : b.review === 'rejected' ? 'این نسخه رد شده است؛ نسخهٔ جاری همان ماند'
        : sup ? '' : 'جاری کردن یا ردِ نسخه فقط با مدیرِ ارشد است';
  }

  /** دو قاب با هم: پیمایشِ یکی همان نسبت را به دیگری می‌دهد؛ قفلِ مشترک ⇒ بی حلقه. */
  function syncScroll(a, b) {
    let busy = false;
    const follow = (from, to) => () => {
      if (busy) return;
      busy = true;
      const fx = from.scrollWidth - from.clientWidth;
      const fy = from.scrollHeight - from.clientHeight;
      to.scrollLeft = fx > 0 ? (from.scrollLeft / fx) * (to.scrollWidth - to.clientWidth) : 0;
      to.scrollTop = fy > 0 ? (from.scrollTop / fy) * (to.scrollHeight - to.clientHeight) : 0;
      requestAnimationFrame(() => { busy = false; });
    };
    a.addEventListener('scroll', follow(a, b));
    b.addEventListener('scroll', follow(b, a));
  }

  /** جاری کردن یا ردِ نسخهٔ انتخاب‌شده — همان approve/reject ِ سرور با `version` و دلیل. */
  async function decideVersion(kind) {
    if (!cmp) return;
    const v = cmp.pick;
    const reason = askReason(kind === 'approve'
      ? `دلیلِ جاری کردنِ نسخهٔ ${fa(v)}؟ (دکان‌های سنجاق‌شده فقط «به‌روزرسانیِ موجود» می‌بینند)`
      : `دلیلِ ردِ نسخهٔ ${fa(v)}؟ (اصل و نسخهٔ جاری دست نمی‌خورند)`);
    if (!reason) return;
    await call('POST', `${IB}/images/${encodeURIComponent(cmp.id)}/${kind}`, { reason, version: v });
    //  فهرست (نشانِ «نسخهٔ تازه»)، عکسِ جاری و نسخه‌ها از نو؛ پیام پس از بازشدنِ دوباره
    await refreshCurrent();
    msg($('ib-dt-msg'), kind === 'approve'
      ? `نسخهٔ ${fa(v)} جاری شد؛ اصل و نسخه‌های دیگر سرِ جایشان‌اند.`
      : `نسخهٔ ${fa(v)} رد شد؛ نسخهٔ جاری همان ماند.`, 'ok');
  }

  /** هر عکسِ هم‌فایل با وضعیت و فضایش — فقط خواندن (IMG-BANK-13). */
  async function showDups(it) {
    const ul = $('ib-dt-dups');
    ul.innerHTML = '';
    if (!it.duplicate_of.length) { ul.appendChild(el('li', '', '—')); return; }
    const rows = await Promise.all(it.duplicate_of.map((id) => call('GET', `${IB}/images/${encodeURIComponent(id)}`)
      .then((d) => ({ id, d })).catch(() => ({ id, d: null }))));
    for (const { id, d } of rows) {
      const li = el('li', '', d
        ? `${id} — ${IMG_FA[d.status] || d.status} · ${d.space === 'central' ? 'بانکِ عمومی' : 'در انتظار'}${d.product ? ` · ${productLine(d.product)}` : ''}`
        : `${id} — دیده نمی‌شود`);
      li.dataset.dup = id;
      ul.appendChild(li);
    }
  }

  async function refreshCurrent() {
    const id = current?.id;
    await loadReview(true);
    if (id && rvItems.has(id)) await openDetail(id);
    else { $('ib-detail').classList.add('hidden'); current = null; }
  }

  async function pick(p) {
    if (!current) return;
    if (!confirm(`این عکس به «${productLine(p)}» وصل شود؟`)) return;
    await call('PATCH', `${IB}/images/${encodeURIComponent(current.id)}`, { bank_product_id: p.id });
    msg($('ib-dt-msg'), 'وصل شد؛ تطبیق «قطعی» به دستِ مدیر.', 'ok');
    await refreshCurrent();
  }

  const DECIDE = {
    approve: ['دلیلِ انتشار در بانکِ عمومی:', 'منتشر شد.'],
    reject: ['دلیلِ رد:', 'رد شد.'],
    archive: ['دلیلِ بایگانی (انتخابِ تازه متوقف می‌شود؛ سنجاقِ قبلیِ دکان‌ها می‌ماند):', 'بایگانی شد.'],
  };
  async function decide(kind) {
    if (!current) return;
    const reason = askReason(DECIDE[kind][0]);
    if (!reason) return;
    await call('POST', `${IB}/images/${encodeURIComponent(current.id)}/${kind}`, { reason });
    msg($('ib-rv-msg'), DECIDE[kind][1], 'ok');
    $('ib-detail').classList.add('hidden');
    current = null;
    await loadReview(true);
  }

  // ---------- بارِ زبانه ----------
  async function loadImageBank() {
    const off = $('ib-off');
    status = await call('GET', `${IB}/status`);
    off.classList.toggle('hidden', status.enabled);
    $('ib-on').classList.toggle('hidden', !status.enabled);
    if (!status.enabled) return;
    await Promise.all([loadBatches(), loadReview(true)]);
    if (batchId) await loadReport();
  }

  const guard = (fn) => () => fn().catch((err) => msg($('ib-msg'), err.message, 'bad'));
  document.addEventListener('DOMContentLoaded', () => {
    $('btn-ib-new').onclick = guard(createBatch);
    $('btn-ib-upload').onclick = guard(uploadFiles);
    $('ib-csv').onchange = guard(readCsv);
    $('btn-ib-dry').onclick = guard(() => sendRows(true));
    $('btn-ib-run').onclick = guard(() => sendRows(false));
    $('btn-ib-pause').onclick = guard(() => setBatchStatus('paused'));
    $('btn-ib-resume').onclick = guard(() => setBatchStatus('open'));
    $('btn-ib-cancel').onclick = guard(() => setBatchStatus('cancelled'));
    $('btn-ib-csv-out').onclick = guard(downloadCsv);
    const rv = (fn) => () => fn().catch((err) => msg($('ib-rv-msg'), err.message, 'bad'));
    const dt = (fn) => () => fn().catch((err) => msg($('ib-dt-msg'), err.message, 'bad'));
    $('btn-ib-rv-load').onclick = rv(() => loadReview(true));
    $('btn-ib-rv-more').onclick = rv(() => loadReview(false));
    $('btn-ib-approve').onclick = dt(() => decide('approve'));
    $('btn-ib-reject').onclick = dt(() => decide('reject'));
    $('btn-ib-archive').onclick = dt(() => decide('archive'));
    $('ib-rv-dups').onchange = rv(() => loadReview(true));
    $('btn-ib-dt-close').onclick = () => { $('ib-detail').classList.add('hidden'); current = null; };
    buildCompareBox();
    $('btn-ib-ver-approve').onclick = dt(() => decideVersion('approve'));
    $('btn-ib-ver-reject').onclick = dt(() => decideVersion('reject'));
    $('ib-cmp-zoom').onchange = () => {
      for (const id of ['ib-cmp-a', 'ib-cmp-b']) $(id).classList.toggle('zoom', $('ib-cmp-zoom').checked);
    };
    syncScroll($('ib-cmp-a'), $('ib-cmp-b'));
  });

  LOADERS.imgbank = loadImageBank;
})();
