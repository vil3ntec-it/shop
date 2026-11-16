package ir.vil3ntec.tohid.desktop

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 *  عاملِ e-syncdiag — «همگام‌سازی حالا» روی گوشی هر بار «همگام‌سازی ناموفق بود» می‌گفت و هیچ دوری راه نمی‌انداخت.
 *
 *  ریشه: `SyncV1Worker.now` کارِ یک‌باره را هم با مکثِ ۵۰۰ میلی‌ثانیه (`setInitialDelay`) و هم فوری (`setExpedited`) می‌ساخت؛
 *  androidx.work 2.9.1 (همان نسخهٔ `android-native/app/build.gradle.kts`) در `WorkRequest.Builder.build` برای این جفت
 *  `IllegalArgumentException("Expedited jobs cannot be delayed")` پرتاب می‌کند. همهٔ فراخوان‌ها `runCatching` داشتند ⇒ پس از هر
 *  ذخیره، پیامِ سوکتِ زنده، ورود و «همگام‌سازی حالا» هیچ دوری نمی‌رفت (فقط کارِ دوره‌ایِ ۱۵ دقیقه‌ای)، `AutoSync.lastError` همان
 *  متنِ انگلیسی می‌شد و صفحه «همگام‌سازی کامل نشد» ِ ناشناخته نشان می‌داد. شیمِ کامپیوتر `setExpedited` را نادیده می‌گرفت.
 */
class SyncNowRequestTest {

  @Test fun `شیمِ WorkManager همان سنجشِ androidx ِ گوشی را دارد`() {
    val e = assertThrows(IllegalArgumentException::class.java) {
      OneTimeWorkRequestBuilder<SyncV1Worker>()
        .setInitialDelay(500, TimeUnit.MILLISECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    }
    assertEquals("Expedited jobs cannot be delayed", e.message)
    assertThrows(IllegalArgumentException::class.java) {
      PeriodicWorkRequestBuilder<SyncV1Worker>(15, TimeUnit.MINUTES).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
    }
    //  هر کدام به‌تنهایی درست است
    OneTimeWorkRequestBuilder<SyncV1Worker>().setInitialDelay(500, TimeUnit.MILLISECONDS).build()
    OneTimeWorkRequestBuilder<SyncV1Worker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
  }

  @Test fun `«همگام‌سازی حالا» و دورِ پس از هر تغییر کارِ پذیرفتنی می‌سازند و پرتاب نمی‌کنند`() {
    try {
      SyncV1Worker.now(DesktopContext)
      SyncV1Worker.schedule(DesktopContext)
    } finally {
      //  دورِ واقعی این‌جا لازم نیست: فقط ساختن و صف شدن
      WorkManager.getInstance(DesktopContext).cancelUniqueWork("tohid-sync-v1-now")
      WorkManager.getInstance(DesktopContext).cancelUniqueWork("tohid-sync-v1-periodic")
    }
  }
}
