package ir.vil3ntec.tohid.ui.v3.money

import android.content.Context
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.PurchaseBook
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
 *
 *  `base` در هر در: دفتری که نتیجهٔ موتور از آن ساخته شد (همان `data` ای که به موتور رفت) — پایهٔ صریحِ
 *  `ShopStore.save`؛ دو ثبت از یک برگهٔ کهنه هیچ‌کدام را نمی‌برند.
 */
internal object MoneyWriter {
  private val scope = MainScope()

  private fun ok(store: ShopStore, toasts: V3Toasts?, context: Context?, base: ShopData, data: ShopData, done: String) {
    scope.launch { store.save(base, data) }
    toasts?.show(done, V3Tone.SUCCESS)
    context?.let { Haptics.success(it) }
  }

  /** شناسهٔ ردیفِ ثبت‌شده ⇐ ثبت شد (برگه/فرم بسته شود)؛ null ⇐ موتور رد کرد */
  fun ledger(store: ShopStore, toasts: V3Toasts?, context: Context?, base: ShopData, result: LedgerEngine.Result, done: String): String? = when (result) {
    is LedgerEngine.Result.Failed -> { toasts?.show(result.message, V3Tone.ERROR); null }
    is LedgerEngine.Result.Ok -> { ok(store, toasts, context, base, result.data, done); result.id }
  }

  /** PUR-05: فاکتورِ خریدِ چندردیفه (`PurchaseBook.record`) — شمارهٔ فاکتور ⇐ ثبت شد؛ null ⇐ رد با پیامِ خودش */
  fun purchase(store: ShopStore, toasts: V3Toasts?, context: Context?, base: ShopData, result: PurchaseBook.Result, done: String): Int? = when (result) {
    is PurchaseBook.Result.Failed -> { toasts?.show(result.message, V3Tone.ERROR); null }
    is PurchaseBook.Result.Ok -> { ok(store, toasts, context, base, result.data, done); result.invoiceNo }
  }

  fun debtor(store: ShopStore, toasts: V3Toasts?, context: Context?, base: ShopData, result: DebtorEngine.Result, done: String): String? = when (result) {
    is DebtorEngine.Result.Failed -> { toasts?.show(result.message, V3Tone.ERROR); null }
    is DebtorEngine.Result.Ok -> { ok(store, toasts, context, base, result.data, done); result.id }
  }

  /**
   *  ادغامِ دو مشتری (CRM-10) — تنها درش `ShopStore.mergeCustomers` است (قفلِ نوشتن، قفلِ نرم، op،
   *  و بسته روی همگام‌سازیِ v3). بیرونِ صفحه اجرا می‌شود تا بسته شدنِ برگه آن را نبرد؛ پیامِ رد
   *  همان پیامِ موتور است.
   */
  fun merge(
    store: ShopStore, toasts: V3Toasts?, context: Context?, keepId: String, dropId: String,
    /** CRM-03 — «تغییرِ سقفِ اعتبار» ِ همین کاربر؛ رد ⇒ پیامِ موتور و هیچ نوشتن */
    limitRules: ir.vil3ntec.tohid.core.perm.CreditLimitPerm.Rules,
    done: (CustomerMerge.Preview) -> String,
  ) {
    scope.launch {
      when (val r = store.mergeCustomers(keepId, dropId, limitRules)) {
        is CustomerMerge.Result.Failed -> toasts?.show(r.message, V3Tone.ERROR)
        is CustomerMerge.Result.Ok -> {
          toasts?.show(done(r.preview), V3Tone.SUCCESS)
          context?.let { Haptics.success(it) }
        }
      }
    }
  }
}
