package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  CRM-15/SEC-10 (د۱۸) — مرزِ سکوی [CustomerErasureLedger]: پس از هر دورِ `SyncV1Worker`، فقط روی دستگاهِ صاحب/مدیر
 *  (`data.delete.any`): فهرستِ سرور ⇐ پاک کردن در دفتر (زیرِ قفلِ موتور، `ShopStore.update`) ⇐ «انجام شد» برای پرونده‌های پاک
 *  (سرور فقط وقتی می‌پذیرد که op ِ پاک کردن به دفترش رسیده باشد ⇒ دورِ بعد). ⛔ پرچمِ خاموش ⇒ ۴۰۴ ⇒ هیچ. حالتِ آموزشی ⇒ هیچ.
 */
object CustomerErasureLedgerAndroid {
  suspend fun afterSync(context: Context, store: ShopStore): Int {
    if (TrainingMode.on || !Backend.isReady(context) || ShopRole.isStaff(context)) return 0   //  صاحب و مدیر = `data.delete.any` ِ سرور
    val ids = try {
      CustomerErasureLedger.ids(Backend.api(context).get(CustomerErasureLedger.PATH))
    } catch (_: ApiFailure.NotFound) {
      return 0
    }
    if (ids.isEmpty()) return 0
    store.update { d -> CustomerErasureLedger.scrub(d, ids.toSet()) }
    val done = CustomerErasureLedger.done(store.data.value, ids)
    if (done.isNotEmpty()) Backend.api(context).post(CustomerErasureLedger.APPLIED, CustomerErasureLedger.appliedBody(done))
    return ids.size
  }
}
