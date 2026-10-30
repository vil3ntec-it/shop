package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  HW-01 (برگهٔ دوروشی)، HW-02/HW-11 (وضعیتِ چاپگر از `DLE EOT`)، HW-03 (کشو فقط با نقدِ دخل).
 *  منطقِ خالص؛ چاپگر و کشوی واقعی این‌جا نیستند.
 */
class PrinterDrawerTest {

  /* ---------------- HW-03: کشو ---------------- */

  private fun cash(a: Long, custody: String? = null, status: String = "confirmed") = DrawerRule.Payment("cash", a, status, custody)
  private fun card(a: Long) = DrawerRule.Payment("card", a)
  private fun account(a: Long) = DrawerRule.Payment("account", a)

  @Test fun `فروش نسخه ۱ - فقط cash کشو را باز می‌کند، همان رفتار همیشگی`() {
    assertTrue(DrawerRule.opensAfter(DrawerRule.Sale("cash")))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("credit")))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cash", cancelled = true)))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cash", payments = emptyList(), cancelled = true)))
  }

  @Test fun `نقد + کارت و بخشی نقد باقی نسیه کشو را باز می‌کنند`() {
    //  پیش از این `paymentMethod == "cash"` بود و «mixed»/«credit» با نقدِ دریافتی کشو را باز نمی‌کرد
    assertTrue(DrawerRule.opensAfter(DrawerRule.Sale("mixed", payments = listOf(cash(50_00), card(150_00)))))
    assertTrue(DrawerRule.opensAfter(DrawerRule.Sale("credit", payments = listOf(cash(20_00), account(80_00)))))
    assertTrue(DrawerRule.opensAfter(DrawerRule.Sale("cash", payments = listOf(cash(100_00)))))
  }

  @Test fun `کارت، حواله، کیف پول و نسیه خالص کشو را باز نمی‌کنند`() {
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("card", payments = listOf(card(100_00)))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("transfer", payments = listOf(DrawerRule.Payment("transfer", 100_00)))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("wallet", payments = listOf(DrawerRule.Payment("wallet", 100_00)))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("credit", payments = listOf(account(100_00)))))
    //  ردیفِ نقدِ صفر (مثلاً فاکتورِ صفر یا ردیفِ خالی) پولی به دخل نمی‌آورد
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("mixed", payments = listOf(cash(0), card(100_00)))))
  }

  @Test fun `نقد امانت پیک، ردیف شکست‌خورده و فاکتور اینترنتی کشو را باز نمی‌کنند`() {
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cod", payments = listOf(cash(100_00, custody = "courier")))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cash", payments = listOf(cash(100_00, custody = "courier")))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cash", payments = listOf(cash(100_00, status = "failed")))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cash", payments = listOf(cash(100_00, status = "cancelled")))))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("online")))
    assertFalse(DrawerRule.opensAfter(DrawerRule.Sale("cash", online = true, payments = listOf(cash(100_00)))))
    //  سفارشی که مشتری سرِ پیشخوان نقد داد: ردیفِ نقدِ بی‌امانت، کانالِ آنلاین نیست ⇒ همان نقدِ دخل
    assertTrue(DrawerRule.opensAfter(DrawerRule.Sale("cash", online = false, payments = listOf(cash(100_00)))))
  }

  /* ---------------- HW-02/HW-11: وضعیتِ چاپگر ---------------- */

  /** گفت‌وگو با چاپگرِ ساختگی: `answers[n]` پاسخِ `DLE EOT n` (نبود ⇒ ‎-1) */
  private fun talk(answers: Map<Int, Int>): Pair<List<Int>, PrinterStatus.State> {
    val p = PrinterStatus.Probe()
    val asked = ArrayList<Int>()
    while (true) {
      val q = p.next() ?: break
      assertEquals(3, q.size); assertEquals(0x10, q[0].toInt()); assertEquals(0x04, q[1].toInt())
      asked += q[2].toInt()
      p.answer(answers[q[2].toInt()] ?: -1)
      assertTrue(asked.size <= 4, "بیش از چهار پرسش")
    }
    return asked to p.state()
  }

  @Test fun `آنلاین با کاغذ - فقط ۱ و ۴ پرسیده می‌شود`() {
    val (asked, s) = talk(mapOf(1 to 0x12, 4 to 0x12))
    assertEquals(listOf(1, 4), asked)
    assertEquals(false, s.offline)
    val v = PrinterStatus.verdict(s)
    assertTrue(v.ok); assertFalse(v.warn); assertEquals("وصل است و کاغذ دارد", v.message)
  }

  @Test fun `در باز - آفلاین، علت در DLE EOT 2`() {
    val (asked, s) = talk(mapOf(1 to (0x12 or 0x08), 2 to (0x12 or 0x04), 4 to 0x12))
    assertEquals(listOf(1, 2, 4), asked)
    assertEquals(true, s.coverOpen)
    val v = PrinterStatus.verdict(s)
    assertFalse(v.ok); assertEquals("درِ چاپگر باز است — ببندید", v.message)
  }

  @Test fun `کاغذ تمام - از حسگر کاغذ یا ایستادن برای کاغذ`() {
    val a = talk(mapOf(1 to 0x12, 4 to (0x12 or 0x60))).second
    assertTrue(a.paperOut)
    assertEquals("کاغذ تمام شده است", PrinterStatus.verdict(a).message)
    assertFalse(PrinterStatus.verdict(a).ok)
    //  چاپگری که DLE EOT 4 را نمی‌گوید ولی در DLE EOT 2 «ایستاده برای کاغذ» دارد
    val b = talk(mapOf(1 to (0x12 or 0x08), 2 to (0x12 or 0x20))).second
    assertNull(b.paper)
    assertTrue(b.paperOut)
    assertFalse(PrinterStatus.verdict(b).ok)
  }

  @Test fun `خطا - DLE EOT 3 فقط وقتی DLE EOT 2 خطا گفته پرسیده می‌شود`() {
    val (asked, s) = talk(mapOf(1 to (0x12 or 0x08), 2 to (0x12 or 0x40), 3 to (0x12 or 0x08), 4 to 0x12))
    assertEquals(listOf(1, 2, 3, 4), asked)
    assertEquals(true, s.cutterError)
    assertTrue(PrinterStatus.verdict(s).message.startsWith("تیغهٔ برش"))
    val hot = talk(mapOf(1 to (0x12 or 0x08), 2 to (0x12 or 0x40), 3 to (0x12 or 0x40), 4 to 0x12)).second
    assertEquals(true, hot.autoRecoverable)
    assertFalse(PrinterStatus.verdict(hot).ok)
    val dead = talk(mapOf(1 to (0x12 or 0x08), 2 to (0x12 or 0x40), 3 to (0x12 or 0x20), 4 to 0x12)).second
    assertEquals("خطای چاپگر — خاموش و روشنش کنید", PrinterStatus.verdict(dead).message)
    //  آفلاین بی علتِ شناخته
    val off = talk(mapOf(1 to (0x12 or 0x08), 2 to 0x12, 4 to 0x12)).second
    assertEquals("چاپگر آماده نیست (آفلاین)", PrinterStatus.verdict(off).message)
  }

  @Test fun `چاپگر بی‌پاسخ - پس از نخستین پرسش بس است و چاپ بسته نمی‌شود`() {
    val (asked, s) = talk(emptyMap())
    assertEquals(listOf(1), asked)
    assertTrue(s.silent)
    val v = PrinterStatus.verdict(s, "192.168.1.50:9100")
    assertTrue(v.ok); assertTrue(v.warn)
    assertEquals("وصل شد (192.168.1.50:9100) — چاپگر وضعیتش را نگفت", v.message)
  }

  @Test fun `بایت بی‌معنا پاسخ وضعیت نیست - جابه‌جا خوانده نمی‌شود`() {
    //  ‎0x00 و ‎0xFF بیت‌های ثابت را ندارند
    assertFalse(PrinterStatus.valid(0x00)); assertFalse(PrinterStatus.valid(0xFF)); assertTrue(PrinterStatus.valid(0x12))
    val (asked, s) = talk(mapOf(1 to 0x12, 4 to 0xFF))
    assertEquals(listOf(1, 4), asked)
    assertNull(s.paper)
    val v = PrinterStatus.verdict(s)
    assertTrue(v.ok); assertTrue(v.warn)
    //  کاغذ رو به پایان: چاپ می‌شود ولی هشدار دارد
    val near = talk(mapOf(1 to 0x12, 4 to (0x12 or 0x0C))).second
    assertEquals(PrinterStatus.Verdict(true, "وصل است — کاغذ رو به پایان است", warn = true), PrinterStatus.verdict(near))
    assertFailsWith<IllegalArgumentException> { PrinterStatus.query(5) }
  }

  /* ---------------- HW-01: برگهٔ دوروشی ---------------- */

  private fun sample(w: Int, h: Int): MonoImage {
    val img = MonoImage(w, h)
    for (x in 0 until w step 2) img[x, 1] = true
    return img
  }

  private fun indexOf(b: ByteArray, pat: ByteArray, from: Int = 0): Int {
    for (i in from..b.size - pat.size) if ((pat.indices).all { b[i + it] == pat[it] }) return i
    return -1
  }

  @Test fun `برگه دوروشی - اول ستونی، ESC @ میانی، بعد رستری، برش و بی کشو`() {
    val profile = PrinterProfile(paperDots = 384, drawer = true)
    val col = sample(384, 24)
    val ras = sample(384, 24)
    val b = ModeProbe.page(col, ras, profile)
    assertContentEquals(EscPos.INIT, b.copyOfRange(0, 2))
    val escStar = indexOf(b, byteArrayOf(0x1B, 0x2A, 33))
    val gsv0 = indexOf(b, byteArrayOf(0x1D, 0x76, 0x30, 0x00))
    assertTrue(escStar in 2 until gsv0, "ستونی پیش از رستری")
    val midInit = indexOf(b, EscPos.INIT, escStar)
    assertTrue(midInit in escStar until gsv0, "ESC @ میانِ دو نمونه")
    //  همان بایت‌های دو سازنده، بی تغییر
    assertContentEquals(EscPos.column(col), b.copyOfRange(2, 2 + EscPos.column(col).size))
    assertContentEquals(EscPos.raster(ras), b.copyOfRange(gsv0, gsv0 + EscPos.raster(ras).size))
    assertContentEquals(EscPos.cut(CutMode.PARTIAL), b.copyOfRange(b.size - 4, b.size))
    assertEquals(-1, indexOf(b, byteArrayOf(0x1B, 0x70)), "برگهٔ آزمون کشو را باز نمی‌کند")
    assertFailsWith<IllegalArgumentException> { ModeProbe.page(sample(576, 8), ras, profile) }
  }

  @Test fun `پاسخ کاربر - رستری هر جا درست باشد، ستونی فقط اگر تنها آن درست بود`() {
    assertEquals(ImageMode.RASTER, ModeProbe.choose(columnOk = true, rasterOk = true))
    assertEquals(ImageMode.RASTER, ModeProbe.choose(columnOk = false, rasterOk = true))
    assertEquals(ImageMode.COLUMN, ModeProbe.choose(columnOk = true, rasterOk = false))
    assertNull(ModeProbe.choose(columnOk = false, rasterOk = false))
  }
}
