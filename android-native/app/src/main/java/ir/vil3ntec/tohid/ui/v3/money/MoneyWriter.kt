package ir.vil3ntec.tohid.ui.v3.money

import android.content.Context
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 *  نوشتنِ نتیجهٔ موتورهای پول — همان کارِ `apply` ِ صفحه‌های امروز: دفترِ تازهٔ موتور ذخیره،
 *  «ثبت شد» و لرزشِ موفقیت؛ یا دلیلِ ردِ موتور (پیامِ خودش) بی هیچ تغییری. هیچ حسابی این‌جا نیست.
 *  ذخیره بیرونِ صفحه انجام می‌شود تا بسته شدنِ برگه آن را نبرد.
 */
internal object MoneyWriter {
  private val scope = MainScope()

  private fun ok(store: ShopStore, toasts: V3Toasts?, context: Context?, data: ShopData, done: String) {
    scope.launch { store.save(data) }
    toasts?.show(done, V3Tone.SUCCESS)
    context?.let { Haptics.success(it) }
  }

  /** شناسهٔ ردیفِ ثبت‌شده ⇐ ثبت شد (برگه/فرم بسته شود)؛ null ⇐ موتور رد کرد */
  fun ledger(store: ShopStore, toasts: V3Toasts?, context: Context?, result: LedgerEngine.Result, done: String): String? = when (result) {
    is LedgerEngine.Result.Failed -> { toasts?.show(result.message, V3Tone.ERROR); null }
    is LedgerEngine.Result.Ok -> { ok(store, toasts, context, result.data, done); result.id }
  }

  fun debtor(store: ShopStore, toasts: V3Toasts?, context: Context?, result: DebtorEngine.Result, done: String): String? = when (result) {
    is DebtorEngine.Result.Failed -> { toasts?.show(result.message, V3Tone.ERROR); null }
    is DebtorEngine.Result.Ok -> { ok(store, toasts, context, result.data, done); result.id }
  }
}
