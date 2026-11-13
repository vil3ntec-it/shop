package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.platform.DocChainSync

/**
 *  S89 — مرزِ سکوی «حذف/سندِ معکوس» ِ ردیفِ قرض: کلیدِ `shop.doc_chain` ِ همین حساب و دکان (همان پاسخِ ذخیره‌شدهٔ
 *  `DocChainSync.enabled`؛ نپرسیده ⇐ خاموش) و امروز/ساعتِ دستگاه. منطق در `core/debt/DebtReversal` و
 *  `DebtorEngine.removeTransaction`؛ کدِ همگام‌سازی (`sync/`) فقط خوانده می‌شود.
 */
@Composable
internal fun rememberDocChainOn(): Boolean {
  val context = LocalContext.current
  return remember(context) { DocChainSync.enabled(context) }
}

/** «حذف» ِ ردیفِ [txId] روی همین دستگاه: [chainOn] خاموش ⇒ همان حذفِ امروز؛ روشن ⇒ سندِ معکوس */
internal fun removeDebtTx(data: ShopData, txId: String, chainOn: Boolean): DebtorEngine.Result =
  DebtorEngine.removeTransaction(data, txId, chainOn, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis())
