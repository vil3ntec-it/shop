package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.ledger.AppendOnce

/**
 *  INV-20 «محل‌ها و انتقال» و INV-21 «بسته‌بندیِ دوباره و ساختِ بسته» در انبارِ **همین دستگاه** (پروندهٔ
 *  `tohid-policy`، کلیدهای `stock_places` و `stock_repack`). پیش‌فرض **خاموش**: نه دکمه‌ای، نه ردیفی — انبار همان امروز.
 */
object StockPlacesPref {

  private const val PLACES = "stock_places"
  private const val REPACK = "stock_repack"
  private const val BOM = "stock_bom"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun places(context: Context): Boolean = runCatching { prefs(context).getBoolean(PLACES, false) }.getOrDefault(false)

  fun setPlaces(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(PLACES, on).apply()
  }

  fun repack(context: Context): Boolean = runCatching { prefs(context).getBoolean(REPACK, false) }.getOrDefault(false)

  fun setRepack(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(REPACK, on).apply()
  }

  /**
   *  INV-21 — «دستورِ ساخت و سریِ تولید» (`stock_bom`، مالِ دکان): دستورِ ساختِ ثبت‌شده (`Bom`) و سری و بهای میانگین در سندِ
   *  بسته‌بندی (`Repack` ِ `lots`). فقط همراهِ [repack]؛ پیش‌فرض **خاموش** ⇒ پنجره و ردیف‌ها همان امروز.
   */
  fun bom(context: Context): Boolean = runCatching { prefs(context).getBoolean(BOM, false) }.getOrDefault(false)

  fun setBom(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(BOM, on).apply()
  }

  sealed interface Saved {
    /** نوشته شد؛ [again] ⇒ همین کلید پیش‌تر نشسته بود و چیزی نوشته نشد */
    data class Ok(val again: Boolean) : Saved
    /** ردِ قاعده روی دفترِ همین لحظه */
    data class Bad(val message: String) : Saved
    /** قفلِ نرم یا دیسک، یا دفترِ دیسک خوانده نشد */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ ثبت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`، همان راهِ `SpecialOrderPref.record`)
   *  — نه حافظهٔ صفحه که پس از Pull ِ کارگر کهنه است؛ موجودیِ مبدأ هم روی همان دفتر سنجیده می‌شود. [key] شناسهٔ ردیفِ
   *  تازه است که پنجره هنگامِ باز شدن می‌سازد ⇒ دو ضربه یک ثبت.
   */
  suspend fun record(
    store: ShopStore,
    key: String,
    has: (ShopData, String) -> Boolean,
    build: (ShopData, String) -> StockPlaces.Result,
  ): Saved {
    var bad: String? = null
    var again = false
    val out = AppendOnce.append<ShopData>(
      key, has,
      { cur, k ->
        when (val r = build(cur, k)) {
          is StockPlaces.Result.Ok -> r.data
          is StockPlaces.Result.Failed -> { bad = r.message; null }
          StockPlaces.Result.Already -> { again = true; null }
        }
      },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    return when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true)
      AppendOnce.Outcome.Skipped -> if (again) Saved.Ok(again = true) else bad?.let { Saved.Bad(it) } ?: Saved.NotSaved
      AppendOnce.Outcome.Unread, AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
  }
}
