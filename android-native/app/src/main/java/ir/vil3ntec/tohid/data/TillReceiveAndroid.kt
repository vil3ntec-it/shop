package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  POS-39 — کلیدِ «دریافت از مشتری در صندوق (F7)» روی **همین دستگاه** (پروندهٔ `tohid-policy`، کنارِ
 *  `ShopPolicy`). پیش‌فرض **خاموش**: F7 در صندوق همان امروز هیچ کاری نمی‌کند.
 */
object TillReceivePref {

  private const val KEY = "till_receive"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /**
   *  ثبت روی دفترِ **همین لحظه** (`ShopStore.update`، زیرِ قفلِ نوشتن) با ساعتِ دستگاه.
   *  @return (نتیجهٔ `update`: null هیچ / true نوشته شد / false قفلِ نرم یا دیسک، نتیجهٔ `TillReceiveFlow`)
   */
  suspend fun record(
    store: ShopStore,
    debtorId: String,
    amountMinor: Long?,
    notes: String,
    key: String,
    prepayConfirmed: Boolean,
    newId: () -> String,
  ): Pair<Boolean?, TillReceiveFlow.Outcome?> {
    var out: TillReceiveFlow.Outcome? = null
    val wrote = store.update { cur ->
      TillReceiveFlow.apply(cur, debtorId, amountMinor, notes, key, prepayConfirmed, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), newId)
        .also { out = it }
        .let { (it as? TillReceiveFlow.Outcome.Ok)?.data }
    }
    return wrote to out
  }
}
