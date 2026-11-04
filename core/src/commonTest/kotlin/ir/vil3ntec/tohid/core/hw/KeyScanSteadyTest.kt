package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  HW-05 «بارکدخوانِ لرزان» (`ScanConfig.steady`، فقط با «ابزارهای تازهٔ دستگاه»): وقفهٔ کوتاهِ بلوتوث وسطِ کد،
 *  تهِ اسکنِ شکسته و پژواکِ بارکدخوان — بی بارکدخوانِ واقعی، زمان از بیرون.
 */
class KeyScanSteadyTest {

  /** نویسه‌ها با فاصلهٔ ثابت از `t0`؛ کدِ پسوند (اگر بود) و زمانِ آخرین نویسه */
  private fun KeyScan.type(s: String, t0: Long, gap: Long): Pair<String?, Long> {
    var t = t0
    var out: String? = null
    s.forEachIndexed { i, c -> if (i > 0) t += gap; feed(c, t)?.let { out = it } }
    return out to t
  }

  /** «۶۲۶۱۲» تند، یک وقفه، باقیِ کد تند و Enter */
  private fun KeyScan.split(head: String, tail: String, t0: Long, hiccup: Long): String? {
    val (_, t) = type(head, t0, 8)
    val (_, t2) = type(tail, t + hiccup, 8)
    return submit(t2 + 8)
  }

  @Test fun `خاموش همان امروز است - ته کد پس از وقفه کد شمرده می‌شود`() {
    val k = KeyScan(ScanConfig())
    assertEquals("34567890", k.split("62612", "34567890", 1_000, 120), "بی پرچم رفتار دست نمی‌خورد")
  }

  @Test fun `روشن - وقفهٔ کوتاه وسط کد آن را نمی‌شکند`() {
    val k = KeyScan(ScanConfig(steady = true))
    assertEquals("6261234567890", k.split("62612", "34567890", 1_000, 120))
    //  مرزِ بسته: ۲۰۰ میلی‌ثانیه (چهار برابرِ ۵۰) هنوز پل است
    assertEquals("6261234567890", k.split("62612", "34567890", 5_000, 200))
    //  Enter ِ دیرِ همان اسکنِ جاافتاده (۱۵۰ میلی‌ثانیه) هم پذیرفته است
    val (_, t) = k.type("6261234567890", 9_000, 8)
    assertEquals("6261234567890", k.submit(t + 150))
  }

  @Test fun `روشن - ته اسکن شکسته هرگز کد نیست و اسکن تازهٔ بعدی درست است`() {
    val k = KeyScan(ScanConfig(steady = true))
    assertNull(k.split("62612", "34567890", 1_000, 400), "وقفهٔ ۴۰۰ میلی‌ثانیه ⇒ ته رد می‌شود، نه کالای اشتباه")
    //  بارکدخوانِ بی Enter با پسوند: تهِ شکسته هم کد نمی‌دهد
    val s = KeyScan(ScanConfig(steady = true, suffix = "#"))
    val (_, t) = s.type("62612", 3_000, 8)
    assertNull(s.type("34567890#", t + 400, 8).first)
    //  اسکنِ درستِ پس از آن (یک و نیم ثانیه بعد) پذیرفته است
    val (_, t2) = k.type("5901234123457", 10_000, 8)
    assertEquals("5901234123457", k.submit(t2 + 8))
    assertEquals("5901234123457", s.type("5901234123457#", 10_000, 8).first)
  }

  @Test fun `روشن - تایپ آدم هنوز اسکن نیست`() {
    val k = KeyScan(ScanConfig(steady = true))
    //  آرام، با Enter ِ زود
    val (_, t) = k.type("1234", 0, 150)
    assertNull(k.submit(t + 20))
    //  «۱۲۳» غلتان (۳۰ میلی‌ثانیه)، وقفهٔ ۱۲۰ و «۴» و Enter ِ تند: کمتر از چهار نویسهٔ تند پیش از وقفه ⇒ پل نیست
    val (_, t2) = k.type("123", 10_000, 30)
    k.feed('4', t2 + 120)
    assertNull(k.submit(t2 + 150))
    //  کادرِ قیمت: «۱۵» غلتان و «۰۰» آرام ⇒ کادر دست نمی‌خورد
    val g = FieldScanGuard(ScanConfig(steady = true))
    var field = ""
    var now = 20_000L
    for ((c, gap) in listOf('1' to 0L, '5' to 30L, '0' to 180L, '0' to 200L)) {
      now += gap
      assertNull(g.key(c, now, field)); field += c
    }
    assertNull(g.enter(now + 300))
  }

  @Test fun `پژواک - همان کد در کمتر از ۳۰۰ میلی‌ثانیه، با پنجرهٔ لغزان`() {
    val e = ScanEcho()
    assertFalse(e.echo("6261234567890", 0))
    assertTrue(e.echo("6261234567890", 250))
    assertTrue(e.echo("6261234567890", 500), "خواندنِ پیاپیِ کالای ایستاده پیشِ بارکدخوان")
    assertFalse(e.echo("6261234567890", 801), "پس از ۳۰۰ میلی‌ثانیه دوباره کالاست")
    assertFalse(e.echo("5901234123457", 850), "کدِ دیگر هرگز پژواک نیست")
    assertFalse(e.echo("6261234567890", 900))
    //  ساعتِ پس‌رفته پژواک نمی‌سازد
    assertFalse(e.echo("6261234567890", 100))
  }

  @Test fun `کادر عدد - پژواک فقط کادر را برمی‌گرداند و بی پرچم پژواکی نیست`() {
    fun scanTwice(cfg: ScanConfig): Pair<FieldScanGuard.Hit?, FieldScanGuard.Hit?> {
      val g = FieldScanGuard(cfg)
      var field = "250"
      var t = 1_000L
      fun scan(): FieldScanGuard.Hit? {
        for (c in "6261234567890") { assertNull(g.key(c, t, field)); field += c; t += 6 }
        return g.enter(t).also { h -> h?.let { field = it.restore } }
      }
      val a = scan()
      t += 150
      return a to scan()
    }
    val (a, b) = scanTwice(ScanConfig(steady = true))
    assertEquals(FieldScanGuard.Hit("6261234567890", "250"), a)
    assertEquals(FieldScanGuard.Hit("6261234567890", "250", echo = true), b)
    val (c, d) = scanTwice(ScanConfig())
    assertFalse(c!!.echo); assertFalse(d!!.echo)
  }

  @Test fun `مرز پل و نگه‌داشتن پرچم در مقدار امن`() {
    assertEquals(200, ScanConfig(maxGapMs = 50).bridgeMs)
    assertEquals(300, ScanConfig(maxGapMs = 100).bridgeMs)
    assertEquals(300, ScanConfig(maxGapMs = 300).bridgeMs)
    assertEquals(400, ScanConfig(maxGapMs = 400).bridgeMs, "بی مقدارِ امن هرگز کمتر از خودِ آستانه نیست")
    assertTrue(ScanConfig(steady = true).normalized().steady)
    assertFalse(ScanConfig().normalized().steady)
  }
}
