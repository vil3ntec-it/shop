package ir.vil3ntec.tohid.sync

import android.content.Context

/**
 *  یک جا برای پرسیدنِ «اشتراک باز است یا نه».
 *
 *  پیش از این، هر جایی که می‌خواست بداند، خودش `License.status(...)` را
 *  صدا می‌زد. آن تابع فقط امضا و تاریخ را می‌سنجد و دو چیزِ بیرونی را
 *  نمی‌داند: ساعتِ گوشی که دستِ کاربر است، و اینکه خودِ برنامه دست‌کاری
 *  شده یا نه. پس هر صدازننده باید آن‌ها را جدا حساب می‌کرد و هیچ‌کدام
 *  نمی‌کردند.
 *
 *  حالا همه از همین‌جا می‌پرسند و هر سه بررسی یک‌جا انجام می‌شود.
 */
object LicenseGuard {

  /** یک روز ارفاق: تنظیمِ منطقهٔ زمانی یا ساعتِ تابستانی، دست‌کاری نیست */
  private const val CLOCK_SLACK = 24L * 60 * 60 * 1000

  /**
   *  ساعتی که می‌شود به آن تکیه کرد.
   *
   *  بالاترین ساعتی که تا حالا دیده‌ایم ذخیره می‌شود. اگر ساعتِ گوشی از
   *  آن عقب‌تر بیفتد، همان عددِ بالاتر ملاک است — وگرنه عقب بردنِ ساعتِ
   *  گوشی، مجوزِ تمام‌شده را دوباره معتبر می‌کرد.
   */
  fun trustedNow(state: SyncStore): Long {
    val phone = System.currentTimeMillis()
    state.clockSeen = phone
    val seen = state.clockSeen
    return if (phone < seen - CLOCK_SLACK) seen else phone
  }

  fun status(context: Context, state: SyncStore): License.Status {
    // فایلِ دست‌کاری‌شده اشتراک نمی‌گیرد
    if (!Integrity.isGenuine(context)) {
      return License.Status(License.State.INVALID, reason = "tampered")
    }
    return License.status(
      state.license,
      state.publicKey,
      state.deviceUid,
      trustedNow(state),
    )
  }
}
