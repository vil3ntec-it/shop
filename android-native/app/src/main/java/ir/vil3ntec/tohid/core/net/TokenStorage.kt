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

  /**
   *  ⛔ رازِ دستگاهِ شاگرد (`/auth/staff` ⇐ `deviceKey`، یا `POST /auth/staff/device-key` از نشستِ باز).
   *
   *  سرور عضوِ موجود را دیگر با شناسهٔ دستگاه برنمی‌گرداند (شناسه راز نیست)؛ ورودِ دوباره با کدِ شاگرد همین راز را
   *  می‌خواهد. پس **کنارِ توکن‌ها و با همان رمز** نگه داشته می‌شود (Keystore ِ اندروید، Keychain ِ آیفون، فایلِ
   *  فقط-صاحبِ کامپیوتر)، با پشتیبانِ خودکار جابه‌جا نمی‌شود، و `clear()` ِ خروج پاکش **نمی‌کند** — وگرنه شاگرد
   *  پس از خروج دیگر با همان کد به حسابِ خودش برنمی‌گشت. هرگز لاگ نمی‌شود.
   *  پیش‌فرضِ تهی برای نگه‌دارنده‌های آزمونی؛ نبودنش یعنی «برنامهٔ کهنه»، نه خطا.
   */
  var staffDeviceKey: String?
    get() = null
    set(@Suppress("UNUSED_PARAMETER") value) {}

  fun clear()
}
