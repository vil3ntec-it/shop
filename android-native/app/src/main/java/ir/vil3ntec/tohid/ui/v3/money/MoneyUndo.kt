package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateListOf
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3SnackVisuals
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel.PendingDelete
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 *  «حذف با برگرداندن» ِ قرض‌داران (#354، `DebtorLedgerModel.PendingDelete`): ردیف یا قرض‌دار همین
 *  حالا از صفحه می‌رود و دفتر فقط وقتی عوض می‌شود که پیامِ «برگرداندن» بی‌پاسخ بسته شود — پس
 *  «برگرداندن» هیچ داده‌ای را بازنمی‌نویسد (چیزی هنوز پاک نشده). حذف خودِ موتور است
 *  (`DebtorEngine.delete` / `deleteTransaction`) روی آخرین دفتر، نه نسخهٔ کهنه.
 *
 *  بیرونِ مسیرها نگه داشته می‌شود: حسابِ پاک‌شده بسته می‌شود و فهرست همان پنهان‌بودن را می‌بیند؛
 *  رفتن از صفحه پیش از بسته شدنِ پیام حذف را گم نمی‌کند.
 */
internal object MoneyUndo {
  private val scope = MainScope()

  /** حذف‌هایی که هنوز «برگرداندن» دارند */
  val pending = mutableStateListOf<PendingDelete>()

  val hiddenTx: Set<String> get() = DebtorLedgerModel.hiddenTx(pending)
  val hiddenDebtors: Set<String> get() = DebtorLedgerModel.hiddenDebtors(pending)

  fun deleteLater(store: ShopStore, host: SnackbarHostState, p: PendingDelete, message: String) {
    if (p in pending) return
    pending += p
    scope.launch {
      host.currentSnackbarData?.dismiss()
      val answer = host.showSnackbar(V3SnackVisuals(message, V3Tone.SUCCESS, StrKit.undo, duration = SnackbarDuration.Short))
      if (answer == SnackbarResult.ActionPerformed) pending -= p else commit(store, host, p)
    }
  }

  /**
   *  S89: «حذف» ِ ردیفِ قرض از فهرست یا برگه — با کلیدِ `shop.doc_chain` ([chainOn]) سندِ معکوس با همان «برگرداندن»؛
   *  ردیفی که معکوس نمی‌شود (معکوسِ معکوس، اصلِ معکوس‌شده، ساختهٔ سندِ دیگر) همان دم پیامِ دلیل می‌گیرد، نه پیامِ «ثبت شد».
   */
  fun removeTxLater(store: ShopStore, host: SnackbarHostState, txId: String, chainOn: Boolean) {
    if (chainOn) ir.vil3ntec.tohid.data.DebtReverse.refusalOf(store.data.value, txId)?.let { why ->
      scope.launch { host.currentSnackbarData?.dismiss(); host.showSnackbar(V3SnackVisuals(why, V3Tone.ERROR)) }
      return
    }
    deleteLater(store, host, PendingDelete.Transaction(txId, reverse = chainOn), if (chainOn) StrMoney.txReversed else StrMoney.txDeleted)
  }

  private suspend fun commit(store: ShopStore, host: SnackbarHostState, p: PendingDelete) {
    val data = store.data.value
    val result = when (p) {
      is PendingDelete.Transaction -> removeDebtTx(data, p.txId, p.reverse)
      is PendingDelete.Debtor -> DebtorEngine.delete(data, p.debtorId)
    }
    when (result) {
      is DebtorEngine.Result.Failed -> host.showSnackbar(V3SnackVisuals(result.message, V3Tone.ERROR))
      is DebtorEngine.Result.Ok -> store.save(data, result.data)
    }
    //  پس از نوشتن برداشته می‌شود تا ردیف میانِ دو حالت یک لحظه دوباره دیده نشود
    pending -= p
  }
}
