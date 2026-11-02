package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.ledger.AppendOnce

/**
 *  POS-27 — «فروشِ امانی با پیش‌پرداخت و جدولِ قسط» در صندوقِ **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `layaway`،
 *  کنارِ `SpecialOrderPref`). پیش‌فرض **خاموش**: نه دکمه‌ای، نه ردیفی — صندوق، فاکتور، برگشت، دخل، همگام‌سازی و خروجی
 *  همان امروز (`LayawayOffTest`). خواندنِ سندها و اثرِ بازپرداخت در دخل به این تنظیم بسته نیست (داده است؛ `Layaways`،
 *  `CashShift`).
 */
object LayawayPref {

  private const val KEY = "layaway"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  /** سرِ برنامه (`AppRoot`، کنارِ `StockFlag.load`): کنار گذاشتنِ کالای امانی‌های باز فقط با همین تنظیم (`Layaways.reserving`) */
  fun load(context: Context): Boolean = on(context).also { Layaways.reserving = it }

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
    Layaways.reserving = on
  }

  sealed interface Saved {
    /** نوشته شد ([data] = دفترِ پس از نوشتن)؛ [again] ⇒ همین کلید پیش‌تر نشسته بود و چیزی نوشته نشد */
    data class Ok(val again: Boolean, val data: ShopData?, val saleId: String?) : Saved
    /** ردِ قاعده روی دفترِ همین لحظه (`Layaways.Built.Bad`) */
    data class Bad(val code: String, val detail: String?) : Saved
    /** قفلِ نرم یا دیسک — پیامش از `AppRoot` (`ShopStore.blocked`/`diskError`) */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ ثبت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`، همان راهِ
   *  `SpecialOrderPref.record`) — نه حافظهٔ صفحه که پس از Pull ِ کارگر کهنه است. قاعده‌ها ([build]) هم روی همان دفتر:
   *  قسطی که دستگاهِ دیگر همین حالا گرفت، یا سندی که همین حالا تحویل یا لغو شد، دوباره پولی جابه‌جا نمی‌کند. [key] از
   *  شناسهٔ سند ساخته می‌شود (`Layaways.payKey`/`deliverKey`/`cancelKey`) ⇒ دو ضربه یا تکرار پس از قطع، یک ثبت.
   */
  suspend fun record(
    store: ShopStore,
    key: String,
    has: (ShopData, String) -> Boolean,
    build: (ShopData, String) -> Layaways.Built,
  ): Saved {
    var bad: Layaways.Built.Bad? = null
    var ok: Layaways.Built.Ok? = null
    val out = AppendOnce.append<ShopData>(
      key, has,
      { cur, k ->
        when (val b = build(cur, k)) {
          is Layaways.Built.Ok -> { ok = b; b.data }
          is Layaways.Built.Bad -> { bad = b; null }
        }
      },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    return when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false, data = out.data, saleId = ok?.saleId)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true, data = null, saleId = null)
      AppendOnce.Outcome.Skipped -> bad?.let { Saved.Bad(it.code, it.detail) } ?: Saved.NotSaved
      //  دفترِ دیسک خوانده نشد ⇒ هیچ نوشتنی، تا چیزی از دفتر رونویسی نشود (همان `DebtEntry.NOT_REREAD`)
      AppendOnce.Outcome.Unread -> Saved.Bad("UNREAD", null)
      AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
  }
}
