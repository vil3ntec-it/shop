package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** HW-05 (آستانه، پیشوند/پسوند، نگهبانِ کادرِ عدد) و HW-11 (سلامتِ بارکدخوان) — بی بارکدخوانِ واقعی */
class KeyScanTest {

  /** نویسه‌ها با فاصلهٔ ثابت از `t0`؛ زمانِ آخرین نویسه را برمی‌گرداند */
  private fun KeyScan.type(s: String, t0: Long, gap: Long): Pair<String?, Long> {
    var t = t0
    var out: String? = null
    s.forEachIndexed { i, c -> if (i > 0) t += gap; feed(c, t)?.let { out = it } }
    return out to t
  }

  @Test fun `پیش‌فرض همان HidScan - ۵۰ میلی‌ثانیه و کمینه ۴`() {
    val k = KeyScan()
    val (_, t) = k.type("5901234123457", 1_000, 8)
    assertEquals("5901234123457", k.submit(t + 8))
    //  تایپِ آدم (۱۵۰ میلی‌ثانیه) اسکن نیست
    val (_, t2) = k.type("1234", 10_000, 150)
    assertNull(k.submit(t2 + 20))
    //  کوتاه‌تر از ۴
    val (_, t3) = k.type("123", 20_000, 5)
    assertNull(k.submit(t3 + 5))
    //  Enterِ دیرهنگام
    val (_, t4) = k.type("12345", 30_000, 5)
    assertNull(k.submit(t4 + 200))
  }

  @Test fun `آستانه قابل تنظیم - بلوتوث کند با ۱۲۰ میلی‌ثانیه`() {
    val slow = "8001234567890"
    val def = KeyScan()
    val (_, a) = def.type(slow, 0, 90)
    assertNull(def.submit(a + 90), "با ۵۰ میلی‌ثانیه شناخته نمی‌شود")
    val tuned = KeyScan(ScanConfig(maxGapMs = 120))
    val (_, b) = tuned.type(slow, 0, 90)
    assertEquals(slow, tuned.submit(b + 90))
    //  بازه: ۵ ⇒ ۱۰ و ۹۰۰۰ ⇒ ۳۰۰
    assertEquals(10, ScanConfig(maxGapMs = 5).normalized().maxGapMs)
    assertEquals(300, ScanConfig(maxGapMs = 9_000).normalized().maxGapMs)
  }

  @Test fun `پیشوند و پسوند از کد برداشته می‌شوند؛ پیشونددار کند هم اسکن است`() {
    val k = KeyScan(ScanConfig(prefix = "]", suffix = "#"))
    //  پسوند خودش پایان است (بارکدخوانِ بی Enter)
    assertEquals("ABC-123", k.type("]ABC-123#", 0, 6).first)
    //  پیشونددار با تأخیرِ ۲۰۰ میلی‌ثانیه (بلوتوث) هنوز اسکن است
    val p = KeyScan(ScanConfig(prefix = "]"))
    val (_, t) = p.type("]4006381333931", 0, 200)
    assertEquals("4006381333931", p.submit(t + 200))
    //  ولی نه پس از مهلتِ سه ثانیه
    val (_, t2) = p.type("]40063813", 10_000, 500)
    assertNull(p.submit(t2 + 500))
    //  نویسهٔ نادیدنی یا غیرِ ASCII در پیشوند پذیرفته نمی‌شود
    assertEquals("]", ScanConfig(prefix = "]\u0002ش").normalized().prefix)
  }

  @Test fun `اسکن هرگز در کادر قیمت نمی‌نشیند - مقدار پیش از هجوم برمی‌گردد`() {
    val g = FieldScanGuard()
    //  کاربر «۲۵۰» را آرام نوشته (هر نویسه ۳۰۰ میلی‌ثانیه)؛ کادر هر بار پیش از نویسه
    var field = ""
    var t = 0L
    for (c in "250") { assertNull(g.key(c, t, field)); field += c; t += 300 }
    assertNull(g.enter(t + 300), "تایپِ آدم + Enter اسکن نیست")
    //  حالا بارکدخوان در همان کادر اسکن می‌کند
    t += 2_000
    val code = "5901234123457"
    for (c in code) { assertNull(g.key(c, t, field)); field += c; t += 6 }
    val hit = g.enter(t)
    assertEquals(FieldScanGuard.Hit(code, "250"), hit)
  }

  @Test fun `سلامت بارکدخوان - درست، کند با پیشنهاد آستانه، بی Enter و پیشوند غایب`() {
    fun keys(s: String, gap: Long) = s.mapIndexed { i, c -> c to i * gap }
    val ok = ScanCheck.evaluate(keys("5901234123457", 7), 12 * 7 + 7, ScanConfig())
    assertTrue(ok.ok); assertEquals("5901234123457", ok.code); assertEquals(7, ok.maxGapMs)
    val slow = ScanCheck.evaluate(keys("5901234123457", 80), 12 * 80 + 80, ScanConfig())
    assertFalse(slow.ok); assertEquals(120, slow.suggestGapMs)
    //  پیشنهاد واقعاً کار می‌کند
    assertTrue(ScanCheck.evaluate(keys("5901234123457", 80), 12 * 80 + 80, ScanConfig(maxGapMs = slow.suggestGapMs!!)).ok)
    val noEnter = ScanCheck.evaluate(keys("5901234123457", 7), null, ScanConfig())
    assertFalse(noEnter.ok); assertTrue(noEnter.message.startsWith("بارکدخوان Enter نزد"))
    val noPrefix = ScanCheck.evaluate(keys("5901234123457", 400), 12 * 400 + 400, ScanConfig(prefix = "]"))
    assertFalse(noPrefix.ok); assertTrue(noPrefix.message.startsWith("پیشوندِ «]» نیامد"))
    val empty = ScanCheck.evaluate(emptyList(), null, ScanConfig())
    assertFalse(empty.ok)
  }
}
