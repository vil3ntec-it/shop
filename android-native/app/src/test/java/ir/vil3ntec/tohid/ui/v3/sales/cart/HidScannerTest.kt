package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.scan.HidScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** بارکدخوانِ سخت‌افزاریِ صندوقِ نسخهٔ ۳ (`Modifier.hidScanner` ⇐ [hidKey]) */
class HidScannerTest {
  private val noSubmit: (Long) -> String? = { null }

  @Test
  fun `پسوندِ تنظیم شده بی Enter کد را همان جا می دهد — خروجیِ feed خوانده می شود`() {
    //  بارکدخوانی که به‌جای Enter پسوند می‌زند: feed ِ نویسهٔ آخر کدِ کامل را برمی‌گرداند (HW-05، #437)
    val step = hidKey(enter = false, ch = '\t', now = 9, feed = { _, _ -> "6260100200300" }, submit = noSubmit)
    assertEquals("6260100200300", step?.code)
  }

  @Test
  fun `نویسهٔ میانِ اسکن خورده می شود و کدی نمی دهد`() {
    val step = hidKey(enter = false, ch = '7', now = 1, feed = { _, _ -> null }, submit = noSubmit)
    assertNotNull(step)
    assertNull(step!!.code)
  }

  @Test
  fun `کلیدِ بی نویسه و Enter ِ آدم به کادر یا دکمه می رسند`() {
    assertNull(hidKey(enter = false, ch = null, now = 1, feed = { _, _ -> error("نباید") }, submit = noSubmit))
    assertNull(hidKey(enter = true, ch = null, now = 1, feed = { _, _ -> error("نباید") }, submit = noSubmit))
  }

  @Test
  fun `Enter پس از نویسه های تند همان کد را با HidScan ِ واقعی می دهد`() {
    val hid = HidScan(maxGapMs = 50, minLength = 4)
    "1234".forEachIndexed { i, c -> assertNull(hidKey(false, c, i.toLong() + 1, hid::feed, hid::submit)!!.code) }
    assertEquals("1234", hidKey(true, null, 5, hid::feed, hid::submit)?.code)
    //  Enter ِ دیرهنگام (آدم) خورده نمی‌شود
    assertNull(hidKey(true, null, 500, hid::feed, hid::submit))
  }
}
