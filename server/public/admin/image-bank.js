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
    if (reset && !out.items.length) {
      const tr = el('tr');
      const c = td('چیزی در این صف نیست', 'muted');
      c.colSpan = 8;
      tr.appendChild(c);
      body.appendChild(tr);
    }
    for (const it of out.items) {
      rvItems.set(it.id, it);
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
      tr.appendChild(flags);
      tr.appendChild(td(it.license ? `${it.license.status}${it.license.commercial_use ? ' · تجاری' : ''}` : '—'));
      const act = el('td');
      const open = el('button', 'btn btn-sm btn-ghost', 'بررسی');
      open.onclick = () => openDetail(it.id).catch((err) => msg($('ib-rv-msg'), err.message, 'bad'));
      act.appendChild(open);
      tr.appendChild(act);
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
    $('ib-dt-dups').textContent = it.duplicate_of.length ? `همان فایلِ ${it.duplicate_of.join('، ')} — فقط نشان؛ حذف یا ادغام نمی‌شود` : '—';
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
    $('ib-dt-super').classList.toggle('hidden', sup || it.status !== 'NEEDS_REVIEW');
    const img = $('ib-dt-img');
    img.removeAttribute('src');
    img.alt = 'در حالِ بار…';
    if (it.file?.url) {
      img.src = await imageData(it.file.url);
      img.alt = `عکسِ ${it.id}`;
    } else img.alt = 'فایلی نیست';
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

  async function decide(kind) {
    if (!current) return;
    const reason = askReason(kind === 'approve' ? 'دلیلِ انتشار در بانکِ عمومی:' : 'دلیلِ رد:');
    if (!reason) return;
    await call('POST', `${IB}/images/${encodeURIComponent(current.id)}/${kind}`, { reason });
    msg($('ib-rv-msg'), kind === 'approve' ? 'منتشر شد.' : 'رد شد.', 'ok');
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
    $('btn-ib-dt-close').onclick = () => { $('ib-detail').classList.add('hidden'); current = null; };
  });

  LOADERS.imgbank = loadImageBank;
})();
