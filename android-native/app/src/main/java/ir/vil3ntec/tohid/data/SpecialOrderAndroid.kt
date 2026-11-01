package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.ledger.AppendOnce

/**
 *  POS-26 — «سفارشِ ویژه با پیش‌پرداخت» در صندوقِ **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `special_orders`، کنارِ
 *  `PayFixPref`). پیش‌فرض **خاموش**: نه دکمه‌ای، نه ردیفی — صندوق همان امروز. خواندنِ سفارش‌ها و اثرِ بازپرداخت در دخل
 *  به این تنظیم بسته نیست (داده است؛ `SpecialOrders`، `CashShift`).
 */
object SpecialOrderPref {

  private const val KEY = "special_orders"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  sealed interface Saved {
    /** نوشته شد ([data] = دفترِ پس از نوشتن)؛ [again] ⇒ همین کلید پیش‌تر نشسته بود و چیزی نوشته نشد */
    data class Ok(val again: Boolean, val data: ShopData?, val saleId: String?) : Saved
    /** ردِ قاعده روی دفترِ همین لحظه (`SpecialOrders.Built.Bad`) */
    data class Bad(val code: String, val detail: String?) : Saved
    /** قفلِ نرم یا دیسک — پیامش از `AppRoot` (`ShopStore.blocked`/`diskError`) */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ ثبت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`، همان راهِ #567 و
   *  `PayFixPref.record`) — نه حافظهٔ صفحه که پس از Pull ِ کارگر کهنه است. قاعده‌ها ([build]) هم روی همان دفتر:
   *  سفارشی که دستگاهِ دیگر همین حالا تحویل یا لغو کرد دوباره پولی جابه‌جا نمی‌کند. [key] شناسهٔ ردیفِ تازه (یا
   *  فاکتورِ تحویل) است که پنجره هنگامِ باز شدن می‌سازد ⇒ دو ضربه یک ثبت.
   */
  suspend fun record(
    store: ShopStore,
    key: String,
    has: (ShopData, String) -> Boolean,
    build: (ShopData, String) -> SpecialOrders.Built,
  ): Saved {
    var bad: SpecialOrders.Built.Bad? = null
    var ok: SpecialOrders.Built.Ok? = null
    val out = AppendOnce.append<ShopData>(
      key, has,
      { cur, k ->
        when (val b = build(cur, k)) {
          is SpecialOrders.Built.Ok -> { ok = b; b.data }
          is SpecialOrders.Built.Bad -> { bad = b; null }
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
