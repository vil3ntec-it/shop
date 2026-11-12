package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrExpenses
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrSale
/** بیسیس‌پوینت ⇒ «۲۶.۸٪» (یک رقمِ اعشار، رقمِ لاتین مثلِ بقیهٔ عددها) */
internal fun bpText(bp: Long): String {
  val a = kotlin.math.abs(bp)
  return "${if (bp < 0) "-" else ""}${a / 100}.${(a % 100) / 10}٪"
}

/** همان برچسب‌های STOCK_MOVEMENT_LABELS نسخهٔ وب */
fun movementLabelOf(type: String): String = when (type) {
  "purchase_in" -> StrProducts.moveIn
  "sale" -> Str.sale
  "customer_return" -> StrProducts.moveReturn
  "sale_cancel" -> StrExpenses.auditCancelSale
  "supplier_return" -> StrExpenses.auditSupplierReturn
  "adjustment" -> StrExpenses.auditStockAdjustment
  "count_adjust" -> StrGoods.mvCountFix
  "damage" -> StrSale.damagedWord
  //  انتقال بین محل‌ها (موتورِ تازهٔ موجودی): دو ردیف، خروجیِ منفی و ورودیِ مثبت
  "transfer_out" -> StrGoods.mvTransferOut
  "transfer_in" -> StrGoods.mvTransferIn
  "transfer" -> StrPlaces.transfer
  else -> type
}
