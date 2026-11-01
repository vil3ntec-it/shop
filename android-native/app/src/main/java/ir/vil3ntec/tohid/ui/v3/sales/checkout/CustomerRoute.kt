package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  چسبِ مسیرِ `sale/checkout/customer?selected=` — نتیجه با `nav.finish`: شناسهٔ قرض‌دار، یا
 *  `CustomerPickerModel.NONE` برای «بدون قرض‌دار»؛ بستن ⇒ null (بی تغییر). «قرض‌دارِ تازه» ⇒
 *  `debtors/new` (G4) و شناسهٔ ساخته‌شده همان‌دم برمی‌گردد.
 */
@Composable
fun RouteScope.CustomerRoute() {
  CustomerPickerSheet(
    d = data,
    selected = args.query("selected"),
    onPick = { id -> nav.finish(id) },
    onNew = { nav.openForResult(MoneyPaths.NEW_DEBTOR) { id -> if (!id.isNullOrBlank()) nav.finish(id) } },
    onClose = { back() },
  )
}
