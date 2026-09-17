/* ==========================================================
   توحید — چاپِ حرارتی از داخلِ مرورگر
   ----------------------------------------------------------
   قاعده‌ی صاحب مخزن: «وب و نیتیو یک برنامه‌اند». برنامه‌ی اندروید
   فاکتور را مستقیم روی چاپگرِ حرارتی می‌فرستد؛ این فایل همان کار را
   در مرورگر می‌کند، تا جایی که خودِ مرورگر اجازه می‌دهد.

   ── همان راهی که نیتیو می‌رود ─────────────────────────────────────
   فاکتور به‌صورت **تصویر** فرستاده می‌شود، نه متن. این چاپگرها جدولِ
   نویسه‌های فارسی ندارند و متنِ فارسی را به‌هم‌ریخته یا علامتِ سؤال
   چاپ می‌کنند. پس فاکتور روی یک canvas کشیده می‌شود — با همان فونتِ
   صفحه — و همان تصویر با دستورِ GS v 0 می‌رود روی کاغذ.

   عرض بر حسب «نقطه» است نه پیکسل: کاغذِ ۵۸ میلی‌متری ۳۸۴ نقطه دارد و
   ۸۰ میلی‌متری ۵۷۶ — همان دو عددی که `ThermalPrinter.kt` دارد.

   ── و آنچه مرورگر اجازه نمی‌دهد ───────────────────────────────────
   ⚠️ این جدول را پیش از وعده دادن به کاربر بخوانید:

     | راه            | اندروید نیتیو | کروم (اندروید/دسکتاپ) | سافاری آیفون |
     |----------------|---------------|------------------------|--------------|
     | بلوتوث         | ✅ SPP کلاسیک | ⚠️ فقط BLE             | ❌ ندارد      |
     | سیم (USB)      | ✅            | ⚠️ WebUSB              | ❌ ندارد      |
     | شبکه (۹۱۰۰)    | ✅            | ❌ سوکتِ خام ندارد      | ❌           |
     | چاپِ سیستمی     | —             | ✅                     | ✅ AirPrint  |

   یعنی **روی آیفون هیچ راهی برای چاپِ مستقیم نیست** — نه کم‌کاریِ
   این کد، بلکه Safari این APIها را ندارد. راهِ آیفون `window.print()`
   است و AirPrint، و به همین دلیل قطعِ کاغذ در `index.html` درست شد.

   ⚠️ و یک تفاوتِ مهمِ بلوتوث: Web Bluetooth فقط **BLE** می‌شناسد، ولی
   چاپگرهای حرارتیِ ارزان معمولاً بلوتوثِ **کلاسیک (SPP)** هستند —
   همان چیزی که اندروید با آن حرف می‌زند. پس چاپگری که روی گوشی کار
   می‌کند، لزوماً از مرورگر پیدا نمی‌شود. اگر پیدا نشد، ایرادِ تنظیمات
   نیست؛ آن چاپگر BLE ندارد.
   ========================================================== */
(function () {
  'use strict';

  /** عرضِ کاغذ بر حسب نقطه — همان اعدادِ `ThermalPrinter.kt` */
  const WIDTH_58MM = 384;
  const WIDTH_80MM = 576;

  /** تکهٔ نوشتن — بافرِ این چاپگرها کوچک است و یک‌جا نمی‌پذیرد */
  const CHUNK = 512;

  /* ============================ مرورگر چه می‌تواند ============================ */

  /**
   * چه راه‌هایی روی **این** مرورگر باز است.
   *
   * صفحه با این تصمیم می‌گیرد کدام دکمه را بسازد. دکمه‌ای که بخورد به
   * «مرورگر شما این را ندارد»، از نبودنِ دکمه بدتر است.
   */
  function capabilities() {
    const secure = typeof window !== 'undefined' && window.isSecureContext;
    return {
      //  هر دو API فقط روی HTTPS کار می‌کنند
      bluetooth: !!(secure && navigator.bluetooth),
      usb: !!(secure && navigator.usb),
      //  این یکی همه‌جا هست، آیفون هم
      system: typeof window !== 'undefined' && typeof window.print === 'function',
    };
  }

  /* ============================ کشیدنِ فاکتور ============================ */

  /**
   * فاکتور را روی canvas می‌کشد.
   *
   * @param {object} receipt چیزی که باید چاپ شود — متنِ آماده، نه
   *   داده‌ی خام. این فایل از «فروش» و «کالا» چیزی نمی‌داند و همین
   *   باعث می‌شود بشود بی‌دردسر سنجیدش.
   *   {
   *     title:  'نام دکان',
   *     meta:   ['فاکتور #12', 'تاریخ: …'],
   *     rows:   [{ right:'نام کالا', left:'۱۲٬۰۰۰' }, …],
   *     totals: [{ right:'جمع', left:'۵۰٬۰۰۰', strong:true }, …],
   *     footer: ['ممنون از خرید شما'],
   *   }
   * @param {number} width عرض بر حسب نقطه
   */
  function renderCanvas(receipt, width) {
    const w = width === WIDTH_80MM ? WIDTH_80MM : WIDTH_58MM;
    const pad = Math.round(w * 0.04);
    const inner = w - pad * 2;

    const titleSize = Math.round(w * 0.075);
    const headSize = Math.round(w * 0.048);
    const bodySize = Math.round(w * 0.044);
    const smallSize = Math.round(w * 0.038);
    const line = Math.round(bodySize * 1.7);

    //  یک بار با ارتفاعِ سخاوتمندانه می‌کشیم، بعد به اندازهٔ واقعی
    //  می‌بریم. اندازهٔ متنِ فارسی را پیش از کشیدن نمی‌شود دقیق دانست.
    const guess = 400 + (receipt.rows || []).length * line * 2 +
      (receipt.totals || []).length * line + (receipt.meta || []).length * line +
      (receipt.footer || []).length * line;

    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = guess;
    const ctx = canvas.getContext('2d');

    //  زمینه سفید، وگرنه شفاف سیاه چاپ می‌شود و کلِ کاغذ سیاه در می‌آید
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#000';
    ctx.textBaseline = 'top';
    ctx.direction = 'rtl';

    const family = '"Vazirmatn", system-ui, sans-serif';
    let y = pad;

    function text(str, size, bold, align) {
      ctx.font = `${bold ? '700' : '400'} ${size}px ${family}`;
      ctx.textAlign = align || 'right';
      const x = align === 'center' ? w / 2 : (align === 'left' ? pad : w - pad);
      ctx.fillText(String(str == null ? '' : str), x, y, inner);
      y += Math.round(size * 1.65);
    }

    /** دو ستون: راست و چپ، در یک سطر */
    function pair(right, left, size, bold) {
      ctx.font = `${bold ? '700' : '400'} ${size}px ${family}`;
      ctx.textAlign = 'right';
      ctx.fillText(String(right == null ? '' : right), w - pad, y, inner * 0.62);
      ctx.textAlign = 'left';
      ctx.fillText(String(left == null ? '' : left), pad, y, inner * 0.36);
      y += Math.round(size * 1.65);
    }

    function rule(strong) {
      y += Math.round(bodySize * 0.35);
      ctx.fillRect(pad, y, inner, strong ? 2 : 1);
      y += Math.round(bodySize * 0.55);
    }

    if (receipt.title) { text(receipt.title, titleSize, true, 'center'); }
    (receipt.meta || []).forEach((m) => text(m, smallSize, false, 'center'));
    rule(true);

    (receipt.rows || []).forEach((r) => pair(r.right, r.left, bodySize, false));

    if ((receipt.totals || []).length) {
      rule(false);
      receipt.totals.forEach((t) => pair(t.right, t.left, t.strong ? headSize : bodySize, !!t.strong));
    }

    if ((receipt.footer || []).length) {
      rule(false);
      receipt.footer.forEach((f) => text(f, smallSize, false, 'center'));
    }

    y += pad;

    //  بریدنِ ارتفاعِ اضافه — وگرنه چاپگر چند سانتی‌متر کاغذِ سفید
    //  بیرون می‌دهد و کاغذِ حرارتی پول دارد
    const out = document.createElement('canvas');
    out.width = w;
    out.height = Math.max(1, Math.min(y, canvas.height));
    out.getContext('2d').drawImage(canvas, 0, 0);
    return out;
  }

  /* ============================ تبدیل به ESC/POS ============================ */

  /**
   * تصویر → بایت‌های ESC/POS.
   *
   * همان دستورهای `ThermalPrinter.render()` در اندروید: از نو، تصویرِ
   * رستری تکه‌تکه با GS v 0، کمی کاغذ جلو، و بریدن.
   */
  function toEscPos(canvas, width) {
    const w = width === WIDTH_80MM ? WIDTH_80MM : WIDTH_58MM;
    const bytesPerRow = (w + 7) >> 3;
    const rowsPerChunk = 128;

    const ctx = canvas.getContext('2d');
    const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;

    const parts = [];
    parts.push(new Uint8Array([0x1B, 0x40]));           // ESC @ — از نو

    for (let top = 0; top < canvas.height; top += rowsPerChunk) {
      const rows = Math.min(rowsPerChunk, canvas.height - top);
      const data = new Uint8Array(bytesPerRow * rows);

      for (let row = 0; row < rows; row += 1) {
        for (let x = 0; x < w; x += 1) {
          if (x >= canvas.width) continue;
          const i = (((top + row) * canvas.width) + x) * 4;
          const alpha = pixels[i + 3];
          //  شفاف را سفید حساب می‌کنیم — همان قاعدهٔ نسخهٔ اندروید
          const lum = alpha < 128 ? 255
            : (0.299 * pixels[i] + 0.587 * pixels[i + 1] + 0.114 * pixels[i + 2]);
          if (lum < 128) data[row * bytesPerRow + (x >> 3)] |= (0x80 >> (x & 7));
        }
      }

      parts.push(new Uint8Array([0x1D, 0x76, 0x30, 0x00]));               // GS v 0
      parts.push(new Uint8Array([bytesPerRow & 0xFF, (bytesPerRow >> 8) & 0xFF]));
      parts.push(new Uint8Array([rows & 0xFF, (rows >> 8) & 0xFF]));
      parts.push(data);
    }

    parts.push(new Uint8Array([0x0A, 0x0A, 0x0A]));      // کمی کاغذ جلو برود
    parts.push(new Uint8Array([0x1D, 0x56, 0x42, 0x00])); // GS V B — بریدن

    let total = 0;
    parts.forEach((p) => { total += p.length; });
    const out = new Uint8Array(total);
    let at = 0;
    parts.forEach((p) => { out.set(p, at); at += p.length; });
    return out;
  }

  /* ============================ فرستادن ============================ */

  /**
   * بلوتوث — فقط چاپگرهای BLE.
   *
   * ⚠️ Web Bluetooth بلوتوثِ **کلاسیک (SPP)** را نمی‌شناسد، ولی
   * چاپگرهای حرارتیِ ارزان معمولاً همان‌اند. پس چاپگری که روی گوشیِ
   * اندروید کار می‌کند ممکن است اینجا اصلاً پیدا نشود — و این ایرادِ
   * تنظیمات نیست.
   *
   * شناسه‌های زیر همان‌هایی‌اند که چاپگرهای BLE رایج ارائه می‌دهند.
   */
  const BLE_SERVICES = [
    0x18f0,                                   // رایج‌ترین سرویسِ چاپگرهای BLE
    '000018f0-0000-1000-8000-00805f9b34fb',
    '0000ff00-0000-1000-8000-00805f9b34fb',
    '49535343-fe7d-4ae5-8fa9-9fafd205e455',   // Issc / چاپگرهای مبتنی بر آن
  ];

  async function sendBluetooth(bytes) {
    if (!capabilities().bluetooth) {
      throw new Error('این مرورگر چاپِ بلوتوثی ندارد. روی آیفون این راه اصلاً نیست — «چاپ معمولی» را بزنید.');
    }
    const device = await navigator.bluetooth.requestDevice({
      acceptAllDevices: true,
      optionalServices: BLE_SERVICES,
    });
    const server = await device.gatt.connect();

    //  دنبالِ اولین مشخصه‌ای می‌گردیم که بشود روی آن نوشت
    let target = null;
    const services = await server.getPrimaryServices();
    for (const service of services) {
      const chars = await service.getCharacteristics();
      for (const ch of chars) {
        if (ch.properties.write || ch.properties.writeWithoutResponse) { target = ch; break; }
      }
      if (target) break;
    }
    if (!target) {
      try { device.gatt.disconnect(); } catch (e) { /* رفته */ }
      throw new Error('روی این دستگاه راهی برای فرستادن پیدا نشد — چاپگرِ BLE است؟');
    }

    try {
      for (let at = 0; at < bytes.length; at += CHUNK) {
        const slice = bytes.slice(at, at + CHUNK);
        if (target.properties.writeWithoutResponse && target.writeValueWithoutResponse) {
          await target.writeValueWithoutResponse(slice);
        } else {
          await target.writeValue(slice);
        }
      }
    } finally {
      try { device.gatt.disconnect(); } catch (e) { /* رفته */ }
    }
  }

  /**
   * سیم — WebUSB.
   *
   * ردهٔ ۷ در USB یعنی «Printer»؛ همان صافی‌ای که نسخهٔ اندروید هم
   * می‌گذارد تا ماوس و حافظه در فهرست نیایند.
   */
  async function sendUsb(bytes) {
    if (!capabilities().usb) {
      throw new Error('این مرورگر چاپِ سیمی ندارد. روی آیفون این راه اصلاً نیست — «چاپ معمولی» را بزنید.');
    }
    const device = await navigator.usb.requestDevice({ filters: [{ classCode: 7 }] });
    await device.open();
    try {
      if (device.configuration === null) await device.selectConfiguration(1);

      //  دنبالِ رابطِ چاپگر و درگاهِ خروجی
      let ifaceNumber = null;
      let endpoint = null;
      for (const iface of device.configuration.interfaces) {
        for (const alt of iface.alternates) {
          if (alt.interfaceClass !== 7) continue;
          const out = alt.endpoints.find((e) => e.direction === 'out' && e.type === 'bulk');
          if (out) { ifaceNumber = iface.interfaceNumber; endpoint = out.endpointNumber; break; }
        }
        if (endpoint !== null) break;
      }
      if (endpoint === null) throw new Error('راهِ فرستادن روی این چاپگر پیدا نشد');

      await device.claimInterface(ifaceNumber);
      try {
        for (let at = 0; at < bytes.length; at += CHUNK) {
          await device.transferOut(endpoint, bytes.slice(at, at + CHUNK));
        }
      } finally {
        try { await device.releaseInterface(ifaceNumber); } catch (e) { /* رها شد */ }
      }
    } finally {
      try { await device.close(); } catch (e) { /* بسته شد */ }
    }
  }

  /**
   * چاپ — از کشیدن تا فرستادن.
   *
   * @param {'bluetooth'|'usb'} how
   */
  async function print(receipt, width, how) {
    const canvas = renderCanvas(receipt, width);
    const bytes = toEscPos(canvas, width);
    if (how === 'usb') return sendUsb(bytes);
    return sendBluetooth(bytes);
  }

  window.TohidThermal = {
    WIDTH_58MM, WIDTH_80MM,
    capabilities, renderCanvas, toEscPos, print,
    sendBluetooth, sendUsb,
  };
})();
