package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.data.ShopData

/**
 *  «چاپ یا اشتراک» ِ جزئیاتِ خرید (e-rows-63b): همان برگهٔ فاکتورِ خریدِ کامپیوتر (`PurchaseBook.invoiceSheet`) با ماندهٔ
 *  واقعی، از چاپِ گوشی (`ReportPrint` ⇐ پنجرهٔ چاپِ سیستم: چاپگر، PDF یا فرستادن). سندِ کسر همان برگهٔ «سندِ کسر».
 */
@Composable
internal fun rememberPurchasePrint(d: ShopData): (PurchaseViews.View) -> Unit {
  val context = LocalContext.current
  return { v ->
    v.invoice?.let { inv ->
      val sheet = PurchaseBook.invoiceSheet(d, inv, remaining = if (v.kind == PurchaseViews.Kind.BILL) v.remaining else null)
      ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet, sheet.name)
    }
  }
}
