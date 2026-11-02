package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.ledger.AppendOnce

/**
 *  PAY-24 — «کارتِ هدیه و اعتبارِ فروشگاه» در صندوقِ **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `gift_cards`، کنارِ
 *  `SpecialOrderPref`). پیش‌فرض **خاموش**: نه دکمه‌ای، نه روشِ پرداختی — صندوق، فاکتور، برگشت و همگام‌سازی همان امروز
 *  (`GiftCardOffTest`). خواندنِ کارت‌ها و اثرشان در دخل و برگشت به این تنظیم بسته نیست (داده است؛ `GiftCards`).
 */
object GiftCardPref {

  private const val KEY = "gift_cards"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  sealed interface Saved {
    /** نوشته شد؛ [again] ⇒ همین کلید پیش‌تر نشسته بود و چیزی نوشته نشد */
    data class Ok(val again: Boolean, val code: String?) : Saved
    data class Bad(val code: String) : Saved
    /** قفلِ نرم یا دیسک — پیامش از `AppRoot` (`ShopStore.blocked`/`diskError`) */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ فروشِ کارت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`، همان راهِ
   *  `SpecialOrderPref.record`)، تا کدی که دستگاهِ دیگر همین حالا فروخت دوباره ثبت نشود. [key] شناسهٔ رویداد است که
   *  پنجره هنگامِ باز شدن می‌سازد ⇒ دو ضربه یک ثبت.
   */
  suspend fun issue(store: ShopStore, key: String, code: String, amountMinor: Long, today: String, now: Long, by: String?): Saved {
    var bad: String? = null
    var made: String? = null
    val out = AppendOnce.append<ShopData>(
      key, GiftCards::hasRow,
      { cur, k ->
        when (val b = GiftCards.issue(cur, k, code, amountMinor, today, now, by)) {
          is GiftCards.Issued.Ok -> { made = b.event.code; b.data }
          is GiftCards.Issued.Bad -> { bad = b.code; null }
        }
      },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    return when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false, code = made)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true, code = null)
      AppendOnce.Outcome.Skipped -> bad?.let { Saved.Bad(it) } ?: Saved.NotSaved
      AppendOnce.Outcome.Unread -> Saved.Bad("UNREAD")
      AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
  }
}
