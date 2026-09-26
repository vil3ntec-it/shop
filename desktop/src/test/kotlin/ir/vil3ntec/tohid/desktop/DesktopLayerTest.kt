package ir.vil3ntec.tohid.desktop

import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.core.net.TokenStore
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.Receipt
import ir.vil3ntec.tohid.print.ThermalPrinter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 *  لایهٔ کامپیوتری — همان چیزهایی که روی اندروید سیستم می‌دهد و این‌جا
 *  خودمان ساخته‌ایم. آزمون‌های منطقِ دکان جدا هستند (همان آزمون‌های
 *  `android-native/app/src/test` که این‌جا هم می‌دوند).
 */
class DesktopLayerTest {

  @Test fun `تنظیمات با نوعِ درست روی دیسک می‌ماند و از نو خوانده می‌شود`() {
    val f = File.createTempFile("prefs", ".json").apply { delete() }
    val a = DesktopPrefs(f)
    a.edit().putString("s", "سلام").putInt("i", 7).putLong("l", 1L shl 40)
      .putBoolean("b", true).putFloat("f", 1.5f).putStringSet("set", setOf("x", "y")).commit()
    val b = DesktopPrefs(f)
    assertEquals("سلام", b.getString("s", null))
    assertEquals(7, b.getInt("i", 0))
    assertEquals(1L shl 40, b.getLong("l", 0))
    assertTrue(b.getBoolean("b", false))
    assertEquals(1.5f, b.getFloat("f", 0f))
    assertEquals(setOf("x", "y"), b.getStringSet("set", null))
    //  همان قاعدهٔ اندروید: Int با getLong خوانده نمی‌شود
    assertEquals(-1L, b.getLong("i", -1L))
    b.edit().remove("s").clear().commit()
    assertTrue(DesktopPrefs(f).all.isEmpty())
  }

  @Test fun `apply هم به دیسک می‌رسد`() {
    val f = File.createTempFile("prefs", ".json").apply { delete() }
    DesktopPrefs(f).edit().putString("k", "v").apply()
    val deadline = System.currentTimeMillis() + 3000
    while (!f.isFile && System.currentTimeMillis() < deadline) Thread.sleep(20)
    Thread.sleep(100)
    assertEquals("v", DesktopPrefs(f).getString("k", null))
  }

  @Test fun `توکن رمزشده روی دیسک می‌نشیند و دستکاری یعنی بی‌نشستی`() {
    val tokens = TokenStore(DesktopContext)
    tokens.save("access-راز", "refresh-راز", 123L)
    assertEquals("access-راز", tokens.accessToken)
    assertEquals("refresh-راز", tokens.refreshToken)
    val raw = DesktopContext.getSharedPreferences("tohid-session-secure", 0).getString("access_token", null)!!
    assertFalse("توکن خام روی دیسک است", raw.contains("access"))
    DesktopContext.getSharedPreferences("tohid-session-secure", 0).edit()
      .putString("access_token", raw.dropLast(4) + "AAAA").commit()
    assertNull(tokens.accessToken)
    tokens.clear()
    assertFalse(tokens.signedIn)
  }

  @Test fun `فاکتورِ گوشی روی کامپیوتر کشیده می‌شود و ESC-POS می‌شود`() {
    val sale = Sale(id = "s1", invoiceNumber = 12, total = 50.0, finalTotal = 50.0, paidAmount = 50.0, createdAt = 1L, date = "2026-09-26")
    val item = SaleItem(id = "i1", saleId = "s1", name = "بیسکویت شکلاتی", quantity = 2.0, unitPrice = 25.0, totalPrice = 50.0)
    val d = ShopData(sales = listOf(sale), saleItems = listOf(item))
    val bmp = Receipt.render(DesktopContext, d, sale, "فروشگاه کریم", ThermalPrinter.WIDTH_58MM)
    assertEquals(ThermalPrinter.WIDTH_58MM, bmp.width)
    assertTrue("فاکتور کوتاه است: ${bmp.height}", bmp.height > 300)
    //  چیزی واقعاً کشیده شده (سیاه روی سفید)
    var dark = 0
    for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
      if (bmp.getPixel(x, y) and 0xFF < 100) dark++
    }
    assertTrue("فاکتور خالی است", dark > 500)
    javax.imageio.ImageIO.write(bmp.image, "png", File(DesktopContext.root, "receipt.png"))
    val bytes = ThermalPrinter.render(bmp, ThermalPrinter.WIDTH_58MM)
    assertEquals(0x1B.toByte(), bytes[0]); assertEquals(0x40.toByte(), bytes[1])
    assertEquals(listOf<Byte>(0x1D, 0x56, 0x42, 0x00), bytes.takeLast(4))
  }

  @Test fun `چاپگرِ شبکه همان پیشوندِ اندروید را می‌خواند`() {
    val link = ThermalPrinter.Link.parse("net:192.168.1.50:9100")
    assertEquals(ThermalPrinter.Link.Network("192.168.1.50", 9100), link)
    assertNotEquals(null, ThermalPrinter.Link.parse("sys:EPSON"))
  }

  class Probe(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      runs++
      latch.countDown()
      return if (runs < 2) Result.retry() else Result.success()
    }
    companion object {
      @Volatile var runs = 0
      val latch = CountDownLatch(2)
    }
  }

  @Test fun `کارِ یک‌باره اجرا و با retry دوباره اجرا می‌شود`() {
    val req = OneTimeWorkRequestBuilder<Probe>()
      .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 50, TimeUnit.MILLISECONDS)
      .build()
    WorkManager.getInstance(DesktopContext).enqueueUniqueWork("probe", ExistingWorkPolicy.REPLACE, req)
    assertTrue(Probe.latch.await(5, TimeUnit.SECONDS))
    assertEquals(2, Probe.runs)
  }
}
