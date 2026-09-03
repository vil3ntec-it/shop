package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ریاضیِ «کجا را نگاه کن».
 *
 *  اینجا همان جایی است که یک علامتِ جابه‌جا، فوکوس را می‌برد سرِ نقطهٔ
 *  قرینه و کسی هم نمی‌فهمد چرا اسکن بدتر شده. پس هر چهار چرخش سنجیده
 *  می‌شود، با نقطه‌ای که جوابش دستی معلوم است.
 */
class AimTest {

  private fun near(expected: Float, actual: Float, what: String) {
    assertTrue("$what: انتظار $expected، شد $actual", kotlin.math.abs(expected - actual) < 0.02f)
  }

  @Test
  fun `بی چرخش، کادر همان است`() {
    val box = Aim.Box(0.1f, 0.2f, 0.3f, 0.4f)
    val out = Aim.toBuffer(box, 0)
    near(0.1f, out.left, "چپ")
    near(0.4f, out.bottom, "پایین")
  }

  @Test
  fun `چرخش نود درجه، گوشه بالا چپ می رود سمت بالا راست`() {
    //  نقطه‌ای در گوشهٔ بالا-چپِ تصویرِ سرِپا
    val box = Aim.Box(0.0f, 0.0f, 0.1f, 0.1f)
    val out = Aim.toBuffer(box, 90)
    //  با چرخشِ ۹۰ درجه، آن گوشه در بافر پایین-چپ بوده: u=y=0، v=1-x=1
    near(0.0f, out.left, "چپ")
    near(0.9f, out.top, "بالا")
    near(1.0f, out.bottom, "پایین")
  }

  @Test
  fun `چرخش صد و هشتاد، همه چیز قرینه می شود`() {
    val out = Aim.toBuffer(Aim.Box(0.0f, 0.0f, 0.2f, 0.2f), 180)
    near(0.8f, out.left, "چپ")
    near(1.0f, out.right, "راست")
  }

  @Test
  fun `چرخش دویست و هفتاد، وارونه نود است`() {
    val box = Aim.Box(0.0f, 0.0f, 0.1f, 0.1f)
    val out = Aim.toBuffer(box, 270)
    near(0.9f, out.left, "چپ")
    near(0.0f, out.top, "بالا")
  }

  @Test
  fun `میانه در هر چرخشی میانه می ماند`() {
    val middle = Aim.Box(0.4f, 0.4f, 0.6f, 0.6f)
    for (rotation in listOf(0, 90, 180, 270)) {
      val out = Aim.toBuffer(middle, rotation)
      near(0.5f, out.cx, "میانهٔ افقی در $rotation")
      near(0.5f, out.cy, "میانهٔ عمودی در $rotation")
    }
  }

  @Test
  fun `چرخش منفی و بیش از یک دور هم پذیرفته می شود`() {
    val box = Aim.Box(0.1f, 0.2f, 0.3f, 0.4f)
    assertEquals(Aim.toBuffer(box, 90), Aim.toBuffer(box, 450))
    assertEquals(Aim.toBuffer(box, 270), Aim.toBuffer(box, -90))
  }

  @Test
  fun `گشاد کردن از مرز بیرون نمی زند`() {
    val out = Aim.padded(Aim.Box(0.0f, 0.0f, 0.2f, 0.2f), by = 1f)
    assertTrue("چپ نباید منفی شود", out.left >= 0f)
    assertTrue("راست نباید از یک بگذرد", out.right <= 1f)
  }

  //  ── انتخاب از میانِ چند بارکد ──────────────────────────────────

  private data class Code(val name: String, val at: Aim.Box)

  @Test
  fun `بزرگ تر برنده است`() {
    val small = Code("ریز", Aim.Box(0.45f, 0.45f, 0.50f, 0.50f))
    val big = Code("درشت", Aim.Box(0.30f, 0.30f, 0.70f, 0.70f))
    val pick = Aim.bestOf(listOf(small, big)) { it.at }
    assertEquals("درشت", pick?.name)
  }

  @Test
  fun `هم اندازه، آن که میانه تر است برنده است`() {
    val middle = Code("میانه", Aim.Box(0.40f, 0.40f, 0.60f, 0.60f))
    val corner = Code("کناری", Aim.Box(0.02f, 0.02f, 0.22f, 0.22f))
    val pick = Aim.bestOf(listOf(corner, middle)) { it.at }
    assertEquals("میانه", pick?.name)
  }

  @Test
  fun `دو بارکدِ هم وزن یعنی هیچ کدام`() {
    //  دو کالای کنارِ هم روی قفسه، هم‌اندازه و هم‌فاصله از میانه —
    //  اینجا حدس زدن یعنی گاهی کالای غلط در فاکتور
    val left = Code("چپی", Aim.Box(0.20f, 0.40f, 0.40f, 0.60f))
    val right = Code("راستی", Aim.Box(0.60f, 0.40f, 0.80f, 0.60f))
    assertNull("نباید بینشان انتخاب کند", Aim.bestOf(listOf(left, right)) { it.at })
  }

  @Test
  fun `یک بارکد همیشه پذیرفته می شود`() {
    val only = Code("تنها", Aim.Box(0.01f, 0.01f, 0.03f, 0.03f))
    assertNotNull(Aim.bestOf(listOf(only)) { it.at })
  }

  @Test
  fun `فهرست خالی جواب ندارد`() {
    assertNull(Aim.bestOf(emptyList<Code>()) { it.at })
  }

  @Test
  fun `بارکدِ بی کادر کنار گذاشته می شود`() {
    val known = Code("دار", Aim.Box(0.30f, 0.30f, 0.70f, 0.70f))
    val blind = Code("بی‌کادر", Aim.Box(0f, 0f, 0f, 0f))
    val pick = Aim.bestOf(listOf(blind, known)) { if (it.name == "دار") it.at else null }
    assertEquals("دار", pick?.name)
  }
}
