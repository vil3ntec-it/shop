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
   *  ⛔ ثبت فقط با `DebtEntry.record` (#567): زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`) — نه
   *  حافظهٔ [store]: کارگرِ همگام‌سازی نمونهٔ `ShopStore` ِ خودش را دارد و پس از Pull ِ او نوشتنِ این حافظه رسیدِ دستگاهِ
   *  دیگر را از دیسک پاک می‌کرد و `record` برایش DELETE می‌ساخت. قاعده‌های صندوق ([TillReceiveFlow.check]) هم روی
   *  همان دفتر. کلید = شناسهٔ ردیف ⇒ دو ضربه یک ردیف. ساعت از دستگاه.
   */
  suspend fun record(
    store: ShopStore,
    debtorId: String,
    amountMinor: Long?,
    notes: String,
    key: String,
    prepayConfirmed: Boolean,
  ): DebtEntry.Result {
    val today = ir.vil3ntec.tohid.todayIso()
    return DebtEntry.record(store, key, TillReceiveFlow.entry(debtorId, amountMinor, notes, today), today, System.currentTimeMillis()) { cur ->
      TillReceiveFlow.check(cur, debtorId, amountMinor, prepayConfirmed)
    }
  }
}
