'use strict';
/**
 * زبانهٔ «بانکِ تصاویر» پنلِ مدیر — روی APIِ موجودِ `/api/admin/image-bank`.
 *
 * ورودِ گروهی (IMG-01، IMG-BANK-01): دسته با مجوز ⇐ فایل‌های پوشه (هر فایل
 * جدا سنجیده می‌شود) ⇐ CSV با پیش‌نمایش ⇐ ثبت ⇐ گزارشِ موفق/ناموفق/مبهم و
 * گزارشِ فایل‌های خراب (IMG-03، IMG-PRO-09). هیچ چیزی این‌جا عمومی نمی‌شود؛ همه
 * به `pending` می‌روند و تأیید کارِ مدیرِ ارشد است.
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

  // ---------- بارِ زبانه ----------
  async function loadImageBank() {
    const off = $('ib-off');
    status = await call('GET', `${IB}/status`);
    off.classList.toggle('hidden', status.enabled);
    $('ib-on').classList.toggle('hidden', !status.enabled);
    if (!status.enabled) return;
    await loadBatches();
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
  });

  LOADERS.imgbank = loadImageBank;
})();
