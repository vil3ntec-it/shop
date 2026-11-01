package ir.vil3ntec.tohid.ui.components.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-25 — پیش‌فاکتور در هر دو صندوق (حالتِ Compose، پس متن سنجیده می‌شود): فقط با `QuotePref` ِ همین دستگاه،
 *  «به سبد» فقط از راهِ `Quotes.convert` و `CartStore.set`، و هیچ نوشتنی در دفترِ دکان.
 */
class QuoteWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val bar = "ir/vil3ntec/tohid/ui/components/pos/QuoteBarAndroid.kt"

  @Test fun `فقط با تنظیمِ همین دستگاه`() {
    val s = src(bar)
    val gate = s.indexOf("if (!on) return")
    assertTrue(s.contains("val on = remember { QuotePref.on(context) }"))
    assertTrue(gate in 0 until s.indexOf("OutlinedButton("))
  }

  @Test fun `به سبد فقط با Quotes convert و بی دفترِ دکان`() {
    val s = src(bar)
    assertTrue(s.indexOf("Quotes.convert(") in 0 until s.indexOf("cart.set(r.quote.lines.map(Quotes::cartLine))"))
    assertFalse(s.contains("store.save"))
    assertFalse(s.contains("store.update"))
    assertFalse(s.contains("SalesEngine.record"))
  }

  @Test fun `هر دو صندوق و تنظیمات`() {
    fun count(rel: String, what: String) = Regex(Regex.escape(what)).findAll(src(rel)).count()
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/Sale.kt", "QuoteBar(d, cartStore, ::toast"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/DeskSale.kt", "QuoteBar(d, cartStore, ::toast"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/Settings.kt", "QuoteSettingsWired(context)"))
  }
}
