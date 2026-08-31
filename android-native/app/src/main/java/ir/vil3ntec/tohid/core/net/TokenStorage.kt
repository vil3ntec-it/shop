package ir.vil3ntec.tohid.core.net

/**
 *  جایی که توکن‌های نشست نگه داشته می‌شوند.
 *
 *  `ApiClient` به این وابسته است، نه به پیاده‌سازیِ اندرویدی‌اش. دو سود
 *  دارد: جریانِ «۴۰۱ → تازه‌سازی → تکرار» بدونِ گوشی و شبیه‌ساز سنجیده
 *  می‌شود، و روزی که نگهداری جای دیگری برود، `ApiClient` دست نمی‌خورد.
 */
interface TokenStorage {

  var accessToken: String?
  var refreshToken: String?

  /** ساعتی که توکنِ دسترسی می‌میرد؛ صفر یعنی نمی‌دانیم */
  var accessExpiresAt: Long

  val signedIn: Boolean get() = !accessToken.isNullOrBlank()

  /**
   *  ذخیرهٔ نشست.
   *
   *  `refresh = null` یعنی «دست نزن»، نه «پاک کن»: سرور در پاسخِ
   *  تازه‌سازی توکنِ تازه‌سازی را دوباره نمی‌فرستد.
   */
  fun save(access: String?, refresh: String?, expiresAt: Long = 0)

  fun clear()
}
