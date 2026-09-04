package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  چراغِ خودکار، روی ساعتِ ساختگی.
 *
 *  اینجا همان جایی است که «حلقهٔ روشن‑خاموش» یا «چراغی که روشن ماند»
 *  گرفته می‌شود — دو چیزی که روی گوشیِ فروشنده پیدا کردنشان سخت است و
 *  اینجا در چند خط دیده می‌شوند.
 */
class LightPilotTest {

  private val flips = mutableListOf<Boolean>()
  private val pilot = LightPilot { on -> flips += on }
  private val lit: Boolean get() = flips.lastOrNull() == true

  private fun dark(spread: Int = 20, clipped: Float = 0f) =
    Look(luma = 30, roiLuma = 30, roiSpread = spread, roiClipped = clipped, samples = 4096)

  private fun bright(spread: Int = 120) =
    Look(luma = 140, roiLuma = 150, roiSpread = spread, roiClipped = 0f, samples = 4096)

  /** ناحیه‌ای که چراغ سوزانده — همان بازتاب روی پلاستیک */
  private fun burnt() =
    Look(luma = 200, roiLuma = 230, roiSpread = 12, roiClipped = 0.55f, samples = 4096)

  /** چراغ کار کرد: هم روشن‌تر شد هم کنتراست گرفت */
  private fun helped() =
    Look(luma = 110, roiLuma = 120, roiSpread = 90, roiClipped = 0.01f, samples = 4096)

  @Test
  fun `نورِ کافی، چراغ روشن نمی‌شود`() {
    for (t in 0L..20L) pilot.onFrame(bright(), t * 100)
    assertTrue("هیچ فرمانی به چراغ نباید برود", flips.isEmpty())
  }

  @Test
  fun `تاریک ولی خوانا، چراغ روشن نمی‌شود`() {
    /*
     *  مهم‌ترین قاعده (§۹): اگر بارکد بی‌چراغ خوانده می‌شود، چراغ کاری
     *  ندارد. روشن کردنش فقط ریسکِ بازتاب است.
     */
    pilot.sawCode(1000)
    pilot.onFrame(dark(), 1100)
    pilot.onFrame(dark(), 1400)
    assertTrue("خوانشِ موفق یعنی نور کافی است", flips.isEmpty())
  }

  @Test
  fun `تاریک و تخت و ناخوانا، چراغ روشن می‌شود`() {
    pilot.onFrame(dark(), 5000)
    assertEquals(listOf(true), flips)
    assertEquals(LightPilot.State.TRYING, pilot.state)
  }

  @Test
  fun `تاریکِ پرکنتراست، چراغ لازم ندارد`() {
    //  شب است ولی بارکد سیاه‌وسفیدِ تیز است؛ رمزگشا از پسش برمی‌آید
    pilot.onFrame(dark(spread = 140), 5000)
    assertTrue(flips.isEmpty())
  }

  @Test
  fun `پیش از نشستنِ نوردهی، قضاوت نمی‌کند`() {
    pilot.onFrame(dark(), 0)
    //  فریمِ گذارِ بلافاصله بعد از چراغ، همیشه سوخته به نظر می‌رسد
    pilot.onFrame(burnt(), 100)
    assertTrue("نباید در همان صدم ثانیه پس بکشد", lit)
    assertEquals(LightPilot.State.TRYING, pilot.state)
  }

  @Test
  fun `چراغ که کمک کرد، می‌ماند`() {
    pilot.onFrame(dark(), 0)
    pilot.onFrame(helped(), 600)
    assertEquals(LightPilot.State.ON, pilot.state)
    assertTrue(lit)
    //  و بی‌دلیل خاموش نمی‌شود
    pilot.onFrame(helped(), 3000)
    assertTrue(lit)
  }

  @Test
  fun `بازتاب روی پلاستیک، چراغ را خاموش می‌کند`() {
    pilot.onFrame(dark(), 0)
    pilot.onFrame(burnt(), 600)
    assertFalse("ناحیهٔ سوخته یعنی چراغ دارد بد می‌کند", lit)
    assertEquals(LightPilot.State.COOLDOWN, pilot.state)
  }

  @Test
  fun `چراغِ بی‌فایده هم خاموش می‌شود`() {
    //  نه سوزاند نه کمک کرد — باتری خوردن بی‌جهت
    pilot.onFrame(dark(), 0)
    pilot.onFrame(dark(), 600)
    assertFalse(lit)
  }

  @Test
  fun `بازتابِ دیرآمده هم گرفته می‌شود`() {
    //  کالا کمی می‌چرخد و تازه آن‌وقت نور صاف برمی‌گردد
    pilot.onFrame(dark(), 0)
    pilot.onFrame(helped(), 600)
    assertEquals(LightPilot.State.ON, pilot.state)
    pilot.onFrame(burnt(), 2000)
    assertFalse(lit)
    assertEquals(LightPilot.State.COOLDOWN, pilot.state)
  }

  @Test
  fun `حلقهٔ روشن خاموش پیش نمی‌آید`() {
    /*
     *  ممنوع‌ترین رفتار (§۳۱): روشن → بازتاب → خاموش → تاریک → روشن …
     *  اینجا صحنه ثابت است و همان بستهٔ براق جلوی دوربین مانده. حتی پس
     *  از گذشتنِ مهلت هم نباید دوباره روشن شود.
     */
    pilot.onFrame(dark(), 0)
    pilot.onFrame(burnt(), 600)
    val afterFirst = flips.size

    var t = 1000L
    repeat(300) {
      //  همان بستهٔ براق، با همان روشناییِ پیش از چراغ
      pilot.onFrame(dark(), t)
      t += 100
    }
    assertEquals("هیچ فرمانِ تازه‌ای به چراغ نرفته", afterFirst, flips.size)
    assertFalse(lit)
  }

  @Test
  fun `صحنه که واقعاً عوض شد، دوباره تصمیم می‌گیرد`() {
    pilot.onFrame(dark(), 0)
    pilot.onFrame(burnt(), 600)
    //  مهلت گذشت و فروشنده کالای دیگری آورد: روشناییِ ناحیه فرق کرده
    val other = Look(luma = 60, roiLuma = 90, roiSpread = 20, roiClipped = 0f, samples = 4096)
    pilot.onFrame(other, 9000)
    assertEquals("باید دوباره قابلِ تصمیم شود", LightPilot.State.OFF, pilot.state)
    //  و حالا اگر آن کالا هم تاریک بود، چراغ حق دارد امتحان کند
    pilot.onFrame(dark(), 9100)
    assertTrue(lit)
  }

  @Test
  fun `اسکنِ موفق چراغ را خاموش نمی‌کند`() {
    /*
     *  گزارشِ صاحب مخزن: «اگر هم شانس بیاورم و اسکن بشود، در جا خاموش
     *  می‌شود». نورِ دکان با اسکن عوض نمی‌شود و کالای بعدی هم همان‌قدر
     *  تاریک است؛ خاموش کردن یعنی چشمکِ چراغ سرِ هر کالا.
     */
    pilot.onFrame(dark(), 0)
    pilot.onFrame(helped(), 600)
    assertTrue(lit)
    pilot.done(700)
    assertTrue("چراغ باید برای کالای بعدی روشن بماند", lit)
    assertEquals(LightPilot.State.ON, pilot.state)
  }

  @Test
  fun `نور که برگردد، پلکِ سنجش چراغ را خاموش می‌کند`() {
    pilot.onFrame(dark(), 0)
    pilot.onFrame(helped(), 600)
    assertEquals(LightPilot.State.ON, pilot.state)

    //  چند ثانیه بعد، چراغ یک چشم‌برهم‌زدن خاموش می‌شود تا نورِ واقعی
    //  سنجیده شود
    pilot.onFrame(helped(), 5000)
    assertEquals(LightPilot.State.PEEK, pilot.state)
    //  خودِ لامپ در همان لحظه خاموش است — پلک همین است — ولی نشانِ روی
    //  صفحه نباید بابتش بپرد
    assertFalse("لامپ برای سنجش خاموش می‌شود", lit)
    assertTrue("نشانِ روی صفحه روشن می‌ماند", pilot.lit)

    //  و نورِ دکان برگشته: خاموش می‌ماند
    pilot.onFrame(bright(), 5400)
    assertEquals(LightPilot.State.OFF, pilot.state)
    assertFalse(lit)
  }

  @Test
  fun `اگر هنوز تاریک بود، پلک چراغ را برمی‌گرداند`() {
    pilot.onFrame(dark(), 0)
    pilot.onFrame(helped(), 600)
    pilot.onFrame(helped(), 5000)
    assertEquals(LightPilot.State.PEEK, pilot.state)

    pilot.onFrame(dark(), 5400)
    assertEquals("تاریک است؛ چراغ برمی‌گردد", LightPilot.State.ON, pilot.state)
    assertTrue(lit)
  }

  @Test
  fun `نزدیک شدنِ کالا، چراغ را پس نمی‌کشد`() {
    /*
     *  گزارشِ صاحب مخزن: «محصول را که جلویش می‌آورم خاموش می‌شود».
     *  کالا که نزدیک می‌آید کمی روشن‌تر و کمی سوخته می‌شود — این
     *  «بازتاب» نیست.
     */
    pilot.onFrame(dark(), 0)
    val closer = Look(luma = 150, roiLuma = 165, roiSpread = 70, roiClipped = 0.12f, samples = 4096)
    pilot.onFrame(closer, 600)
    assertTrue("نزدیک شدنِ عادی نباید چراغ را ببرد", lit)
  }

  @Test
  fun `بستنِ اسکنر، چراغ را خاموش می‌کند`() {
    pilot.onFrame(dark(), 0)
    assertTrue(lit)
    pilot.close()
    assertFalse("چراغ نباید پشتِ سرِ صفحهٔ بسته روشن بماند", lit)
  }

  @Test
  fun `بستن، حتی وقتی چراغ خاموش بوده، فرمانِ خاموش می‌دهد`() {
    pilot.close()
    assertEquals(listOf(false), flips)
  }

  @Test
  fun `دستِ کاربر، خودکار را کنار می‌زند`() {
    pilot.byHand(true, 0)
    assertTrue(lit)
    //  حتی با تصویرِ کاملاً سوخته هم تا مهلتِ دستی تمام نشده دست نمی‌زند
    pilot.onFrame(burnt(), 1000)
    assertTrue("تصمیمِ آدم مقدم است", lit)
  }

  @Test
  fun `پس از مهلتِ دستی، سوختگیِ آشکار باز هم خاموش می‌کند`() {
    pilot.byHand(true, 0)
    pilot.onFrame(burnt(), 60_000)
    assertFalse(lit)
  }

  @Test
  fun `فریمِ بی‌داده هیچ تصمیمی نمی‌سازد`() {
    pilot.onFrame(Look.NOTHING, 0)
    assertTrue(flips.isEmpty())
  }
}
