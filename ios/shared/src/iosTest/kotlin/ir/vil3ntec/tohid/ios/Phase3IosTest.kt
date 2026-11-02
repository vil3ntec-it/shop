package ir.vil3ntec.tohid.ios

import android.content.Intent
import android.content.IosContext
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.scan.CodeKind
import ir.vil3ntec.tohid.scan.iosScanValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  گامِ ۳ ِ آیفون روی شبیه‌ساز (کارِ `ios-app` در ios-spike.yml؛ روی لینوکس فقط کامپایل می‌شود):
 *  WorkManager ِ داخلِ فرآیند (همان کارگرِ اندروید اجرا می‌شود، `retry` دوباره)، نگاشتِ بارکدِ AVFoundation به
 *  قاعدهٔ ML Kit، و رفت‌وبرگشتِ `Intent` ِ اعلان از راهِ userInfo ِ iOS.
 */
class Phase3IosTest {
  class Counting(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): ListenableWorker.Result {
      runs++
      return if (runs < failFirst + 1) ListenableWorker.Result.retry() else ListenableWorker.Result.success()
    }

    companion object {
      var runs = 0
      var failFirst = 0
    }
  }

  @Test
  fun workManagerRunsTheRegisteredWorker() = runBlocking {
    Counting.runs = 0
    Counting.failFirst = 0
    WorkManager.register(Counting::class) { c, p -> Counting(c, p) }
    WorkManager.getInstance(IosContext).enqueueUniqueWork("test-once", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<Counting>().build())
    withTimeout(5_000) { while (Counting.runs == 0) delay(20) }
    assertEquals(1, Counting.runs)
  }

  @Test
  fun scanValueFollowsMlKit() {
    //  UPC-A: iOS «EAN-13 با صفرِ آغازین» ⇐ همان ۱۲ رقمِ ML Kit
    assertEquals("036000291452" to CodeKind.CHECKED, iosScanValue("0036000291452", AVMetadataObjectTypeEAN13Code))
    assertEquals("6260100000006" to CodeKind.CHECKED, iosScanValue("6260100000006", AVMetadataObjectTypeEAN13Code))
    assertEquals("96385074" to CodeKind.CHECKED, iosScanValue("96385074", AVMetadataObjectTypeEAN8Code))
    assertEquals("00012345678905" to CodeKind.ITF, iosScanValue("00012345678905", AVMetadataObjectTypeITF14Code))
    assertEquals("ABC-123" to CodeKind.OTHER, iosScanValue("ABC-123", AVMetadataObjectTypeCode128Code))
    assertEquals("https://vill3n.top/x" to CodeKind.OTHER, iosScanValue("https://vill3n.top/x", AVMetadataObjectTypeQRCode))
  }

  @Test
  fun noticeIntentSurvivesUserInfo() {
    val sent = Intent(Intent.ACTION_MAIN).putExtra("notif", "n-42").putExtra("order", "o-7").putExtra("n", 3)
    val back = IosNotifications.intentOf(IosNotifications.userInfoOf(sent))
    assertEquals(Intent.ACTION_MAIN, back.action)
    assertEquals("n-42", back.getStringExtra("notif"))
    assertEquals("o-7", back.getStringExtra("order"))
    assertNull(back.getStringExtra("missing"))
  }
}
