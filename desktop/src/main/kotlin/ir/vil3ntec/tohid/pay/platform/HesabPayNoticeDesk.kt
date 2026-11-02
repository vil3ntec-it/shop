package ir.vil3ntec.tohid.pay.platform

import android.content.Context

/**
 *  جایگزینِ کامپیوتریِ `android-native/…/pay/platform/HesabPayNoticeAndroid.kt` با همان API (`replaced` در
 *  `build.gradle.kts`): کامپیوتر اعلانِ برنامهٔ حساب‌پی را نمی‌بیند؛ صندوقدار فقط با «پول رسید» تأیید می‌کند.
 */
object HesabPayNotice {
  const val SUPPORTED = false

  val PACKAGES: Set<String> = emptySet()

  @Suppress("UNUSED_PARAMETER")
  fun hintOn(context: Context): Boolean = false

  @Suppress("UNUSED_PARAMETER")
  fun setHintOn(context: Context, on: Boolean) = Unit

  @Suppress("UNUSED_PARAMETER")
  fun granted(context: Context): Boolean = false

  @Suppress("UNUSED_PARAMETER")
  fun openAccess(context: Context) = Unit
}
