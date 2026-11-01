package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.ledger.AppendOnce
import ir.vil3ntec.tohid.core.pay.PayFix

/**
 *  POS-40 — «اصلاحِ روشِ پرداخت» در فاکتورهای گذشته، روی **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `pay_fix`،
 *  کنارِ `TillReceivePref`). پیش‌فرض **خاموش**: نه دکمه‌ای و نه ردیفی — فروش و صندوق همان امروز. خواندنِ ردیف‌های
 *  `pay_fix` ِ دستگاه‌های دیگر به این تنظیم بسته نیست (داده است؛ `PayFixFlow`).
 */
object PayFixPref {

  private const val KEY = "pay_fix"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /** مجوزِ همین کاربر (`sales.pay_fix`): allow | approve | deny */
  fun decision(context: Context): String = ShopRole.decide(context, PayFix.PERM).decision

  sealed interface Saved {
    /** نوشته شد؛ [again] ⇒ همین کلید پیش‌تر نشسته بود و چیزی نوشته نشد */
    data class Ok(val again: Boolean) : Saved
    /** ردِ هسته روی دفترِ همین لحظه (کدِ `PayFix.check`) */
    data class Bad(val code: String, val method: String?) : Saved
    /** قفلِ نرم یا دیسک — پیامش از `AppRoot` (`ShopStore.blocked`/`diskError`) */
    data object NotSaved : Saved
  }

  /**
   *  ⛔ ثبت فقط این‌جا: زیرِ قفلِ موتور روی دفترِ همین لحظهٔ **دیسک** (`DebtEntry.ledgerNow`، همان راهِ #567) — نه
   *  حافظهٔ صفحه که پس از Pull ِ کارگر کهنه است. سنجش (`PayFixFlow.build`) هم روی همان دفتر: اصلاحِ دستگاهِ دیگر
   *  که همین حالا رسید، صندوقی که همین حالا بسته شد. کلید = شناسهٔ ردیف ⇒ دو ضربه یک ردیف. ساعت از دستگاه.
   */
  suspend fun record(store: ShopStore, key: String, r: PayFixFlow.Request): Saved {
    val today = ir.vil3ntec.tohid.todayIso()
    val now = System.currentTimeMillis()
    var bad: PayFixFlow.Built.Bad? = null
    val out = AppendOnce.append<ShopData>(
      key,
      { d, k -> d.auditLog.any { it.id == k } },
      { cur, k ->
        when (val b = PayFixFlow.build(cur, k, r, today, now)) {
          is PayFixFlow.Built.Ok -> cur.copy(auditLog = cur.auditLog + b.row)
          is PayFixFlow.Built.Bad -> { bad = b; null }
        }
      },
    ) { t -> store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) } }
    return when (out) {
      is AppendOnce.Outcome.Wrote -> Saved.Ok(again = false)
      AppendOnce.Outcome.Already -> Saved.Ok(again = true)
      AppendOnce.Outcome.Skipped -> bad?.let { Saved.Bad(it.code, it.method) } ?: Saved.NotSaved
      //  دفترِ دیسک خوانده نشد ⇒ هیچ نوشتنی، تا چیزی از دفتر رونویسی نشود (همان `DebtEntry.NOT_REREAD`)
      AppendOnce.Outcome.Unread -> Saved.Bad("UNREAD", null)
      AppendOnce.Outcome.Unsaved -> Saved.NotSaved
    }
  }
}
