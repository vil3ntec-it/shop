package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.ledger.AppendOnce

/**
 *  PAY-18 — صفحهٔ گاوصندوق روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `safe_book`، کنارِ `SpecialOrderPref`).
 *  پیش‌فرض **خاموش**: نه کارتی در «تاریخچهٔ فروش»، نه ردیفی — همان امروز. خواندنِ دفترِ گاوصندوق و همگام‌سازیِ آن به این
 *  تنظیم بسته نیست (داده است؛ سرور پشتِ پرچمِ `sync.safe_entries`).
 */
object SafeBookPref {

  private const val KEY = "safe_book"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  sealed interface Saved {
    /** نوشته شد؛ [again] ⇒ همین کلید پیش‌تر نشسته بود و چیزی نوشته نشد */
    data class Ok(val again: Boolean) : Saved
    /** ردِ قاعده روی دفترِ همین لحظه (`SafeBook.Result.Failed`) */
    data class Bad(val failed: SafeBook.Result.Failed) : Saved
    /** دفترِ دیسک خوانده نشد، قفلِ نرم یا دیسک — هیچ نوشته نشد */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ ثبت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`، همان راهِ #567 و
   *  `SpecialOrderPref.record`) — نه حافظهٔ صفحه که پس از Pull ِ کارگر کهنه است. [build] (یکی از `SafeBook.*`) هم روی
   *  همان دفتر می‌دود: مانده، نقدِ دخل و صندوقِ باز همان لحظه‌اند. [key] شناسهٔ ردیفِ تازه است که پنجره هنگامِ باز شدن
   *  می‌سازد ⇒ دو ضربه یک ثبت.
   */
  suspend fun record(store: ShopStore, key: String, build: (ShopData, String) -> SafeBook.Result): Saved {
    var bad: SafeBook.Result.Failed? = null
    var dup = false
    val out = AppendOnce.append<ShopData>(
      key,
      { d, k -> d.safeEntries.any { it.id == k } },
      { cur, k ->
        when (val r = build(cur, k)) {
          is SafeBook.Result.Ok -> if (r.duplicate) { dup = true; null } else r.data
          is SafeBook.Result.Failed -> { bad = r; null }
        }
      },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    return when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true)
      AppendOnce.Outcome.Skipped -> bad?.let { Saved.Bad(it) } ?: if (dup) Saved.Ok(again = true) else Saved.NotSaved
      AppendOnce.Outcome.Unread, AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
  }
}
