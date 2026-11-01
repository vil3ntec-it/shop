package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 *  نسخهٔ حسابِ همین دستگاه برای صفحه‌هایی که زیرِ یک برگه باز می‌مانند: خروج
 *  (برگهٔ `profile/signout`) یا ثبتِ کدِ اشتراک بالا می‌بردش و «حساب من» و
 *  اشتراک همان لحظه از `SyncStore` دوباره می‌خوانند.
 */
object AccountSession {
  val version = mutableIntStateOf(0)

  fun changed() { version.intValue++ }

  /**
   *  کارِ برگه‌ها (خروج، برداشتن، از کار انداختن) پس از بسته شدنِ برگه ادامه دارد؛
   *  دامنهٔ خودِ برگه با بسته شدنش لغو می‌شد و درخواست نیمه‌کاره می‌ماند.
   */
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
