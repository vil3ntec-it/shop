package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  S22 — تنظیم‌های انقضا روی **همین دستگاه** (پروندهٔ `tohid-policy`، کنارِ `block_expired` ِ `ShopPolicy`):
 *  - `expiry_near_days`: هشدارِ «نزدیک به انقضا» چند روز پیش (۱ تا ۳۶۵)؛ پیش‌فرض ۳۰ = همان امروز (`ExpiryEngine.nearDays`).
 *  - `expiry_approval`: «منقضی فقط با مجوز» — فروشِ منقضی با تأییدِ مدیر/صاحب (همان PIN ِ سقفِ اعتبار)؛ پیش‌فرض خاموش
 *    ⇒ همان «فقط هشدار». با «فروخته نشود» (`blockExpired`) بی اثر است: بستن همیشه بستن است.
 */
object ExpiryPref {

  private const val DAYS = "expiry_near_days"
  private const val APPROVAL = "expiry_approval"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun nearDays(context: Context): Int =
    runCatching { prefs(context).getInt(DAYS, ExpiryEngine.NEAR_DAYS) }.getOrDefault(ExpiryEngine.NEAR_DAYS)
      .takeIf { it in 1..ExpiryEngine.MAX_NEAR_DAYS } ?: ExpiryEngine.NEAR_DAYS

  /** نادرست (بیرون از ۱ تا ۳۶۵) ⇒ `false` و چیزی نوشته نمی‌شود */
  fun setNearDays(context: Context, days: Int): Boolean {
    if (days !in 1..ExpiryEngine.MAX_NEAR_DAYS) return false
    prefs(context).edit().putInt(DAYS, days).apply()
    ExpiryEngine.nearDays = days
    return true
  }

  /** آغازِ برنامه: روزِ هشدار به `ExpiryEngine` */
  fun load(context: Context) { ExpiryEngine.nearDays = nearDays(context) }

  fun approval(context: Context): Boolean = runCatching { prefs(context).getBoolean(APPROVAL, false) }.getOrDefault(false)

  fun setApproval(context: Context, on: Boolean) { prefs(context).edit().putBoolean(APPROVAL, on).apply() }

  /** صندوق: «منقضی فقط با مجوز» روشن و «فروخته نشود» خاموش */
  fun gate(context: Context): Boolean = approval(context) && !ShopPolicy.blockExpired(context)
}
