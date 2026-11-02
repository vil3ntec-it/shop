package ir.vil3ntec.tohid.ui.v3.sales.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  چسبِ `scan?for=sale`: دوربینِ پیوسته روی صندوق — هر کد به `SaleScan.reads`، پاسخِ صندوق در
 *  پایینِ همین برگه (`SaleScanPanel`)، بارکدِ دستی و «هر اسکن». کارِ بارکدِ ناشناس («ثبتِ کالا»، «ثبتِ سریع»
 *  یا «قلمِ آزاد» — `QuickTillModel`) ⇒ برگه جایش را به همان مسیر می‌دهد و «برگشت» به صندوق برمی‌گردد.
 */
@Composable
fun RouteScope.SaleScanRoute() {
  DisposableEffect(Unit) {
    SaleScan.open.value = true
    onDispose {
      SaleScan.open.value = false
      SaleScan.note.value = null
    }
  }
  ScanScreen(
    onCode = { SaleScan.reads.trySend(SaleScan.Read(it)) },
    onClose = { back() },
    overlay = !desk,
    continuous = true,
    bottom = {
      SaleScanPanel(
        shown = SaleScan.note.value,
        multiplier = SaleScan.multiplier.intValue,
        onMultiplier = { SaleScan.multiplier.intValue = it },
        onManual = { SaleScan.reads.trySend(SaleScan.Read(it, manual = true)) },
        onAction = { path -> nav.replace(path) },
      )
    },
  )
}
