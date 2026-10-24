package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.runtime.mutableIntStateOf

/**
 *  نسخهٔ حسابِ همین دستگاه برای صفحه‌هایی که زیرِ یک برگه باز می‌مانند: خروج
 *  (برگهٔ `profile/signout`) یا ثبتِ کدِ اشتراک بالا می‌بردش و «حساب من» و
 *  اشتراک همان لحظه از `SyncStore` دوباره می‌خوانند.
 */
object AccountSession {
  val version = mutableIntStateOf(0)

  fun changed() { version.intValue++ }
}
