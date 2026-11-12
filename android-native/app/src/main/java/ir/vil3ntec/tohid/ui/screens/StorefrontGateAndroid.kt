package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontQueue
import ir.vil3ntec.tohid.data.repo.StorefrontRepository

/**
 *  آیا بخشِ «ویترین» دیده شود؟ — فقط وقتی سرور می‌گوید روشن است و کاربر
 *  اجازهٔ تنظیم دارد. بی اینترنت یا بی حساب: پنهان (هیچ دکمهٔ بی‌اثری نیست).
 *
 *  (LOC-06، ۱۴۰۵/۰۸/۲۲: از `Storefront.kt` ِ قدیم جدا شد (نامِ `…Android.kt`: `Context`)؛ صفحهٔ قدیمِ ویترین بی‌راه بود و برداشته شد — صفحهٔ زنده
 *  `ui/v3/online/storefront`.)
 */
object StorefrontGate {
  val config = mutableStateOf<StorefrontConfigDto?>(null)
  val visible: Boolean get() = config.value?.let { it.flag && it.permissions.manage } == true

  suspend fun refresh(context: android.content.Context): StorefrontConfigDto? {
    if (!Backend.isReady(context)) { config.value = null; return null }
    val r = StorefrontRepository(Backend.api(context)).config().valueOrNull()
    if (r != null) {
      config.value = r
      //  هر ویرایشِ ویترین/بازار که بی اینترنت ماند، با باز شدنِ برنامه دوباره فرستاده می‌شود
      if (r.flag) StorefrontQueue.flush(context)
    }
    return r
  }
}
