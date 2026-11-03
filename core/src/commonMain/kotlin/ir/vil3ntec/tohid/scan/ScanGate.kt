package ir.vil3ntec.tohid.scan

import ir.vil3ntec.tohid.core.platform.currentTimeMillis

/**
 *  جلوگیری از اسکنِ تکراری.
 *
 *  دوربین یک بارکد را در یک ثانیه ده‌ها بار می‌بیند. بدونِ این، یک بار
 *  گرفتنِ کالا جلوی دوربین ده‌ها عدد به سبد اضافه می‌کرد.
 *  پنجرهٔ زمانی همان ۱۲۰۰ میلی‌ثانیهٔ نسخهٔ وب است.
 *
 *  منطقِ خالص و یک نسخه برای هر سه سکو (آیفون گامِ ۷): پیش‌تر کنارِ دوربینِ هر سکو
 *  (`scan/BarcodeScanner.kt` ِ اندروید، `BarcodeScannerDesk` ِ کامپیوتر، `BarcodeScannerIos` ِ آیفون)
 *  سه بار نوشته شده بود؛ صفحه‌های مشترک (`Sale`، `SaleRoute`) همین را می‌سازند.
 */
class ScanGate(private val windowMs: Long = 1200) {
  private var lastCode: String? = null
  private var lastAt = 0L

  fun accept(code: String, now: Long = currentTimeMillis()): Boolean {
    if (code == lastCode && now - lastAt < windowMs) return false
    lastCode = code
    lastAt = now
    return true
  }

  fun reset() {
    lastCode = null
    lastAt = 0
  }
}
