'use strict';
/**
 * بارکنندهٔ مشترکِ `sharp` (libvips) — وابستگیِ **اختیاری**. نبود یا بار نشدن ⇒ `lib()` = null و هر کاربر همان راهِ امروزش
 * (WebP ِ ویترین: همان JPEG؛ zoom ِ بانک: همان display). هرگز زیرِ Electron (سرورِ درونیِ برنامهٔ کامپیوتر): libvips با glib ِ
 * خودِ Electron ناسازگار است (هشدارِ SharpElectronLinux و GLib-CRITICAL) و در CI ِ #1035 پرونده را تا کشته شدن نگه داشت.
 * کشِ درونیِ libvips خاموش و یک رشته: سقفِ حافظه و بار را هر کاربر خودش نگه می‌دارد.
 */
let sharp; // undefined = هنوز نسنجیده، null = نیست
function lib() {
  if (sharp === undefined) {
    if (process.versions.electron) { sharp = null; return sharp; }
    try {
      sharp = require('sharp');
      sharp.cache(false);
      sharp.concurrency(1);
    } catch { sharp = null; }
  }
  return sharp;
}

module.exports = { lib };
