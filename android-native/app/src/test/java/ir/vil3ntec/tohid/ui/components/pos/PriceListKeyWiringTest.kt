package ir.vil3ntec.tohid.ui.components.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-18 — Ctrl+R در صندوقِ کامپیوتر (حالتِ Compose، پس متن سنجیده می‌شود): کلید از `PosKeys.priceList` و فقط با
 *  همان `PriceListPrefs.on` که نشان را نشان می‌دهد، پیش از کلیدهای صفحه؛ اثرش همان `PriceList.toggle` ِ نشان است
 *  (شمارِ `toggles`)، و گوشی (بی شمار) همان امروز.
 */
class PriceListKeyWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val desk = "ir/vil3ntec/tohid/ui/screens/DeskSale.kt"
  private val chip = "ir/vil3ntec/tohid/ui/components/pos/PriceListAtPosAndroid.kt"

  @Test fun `صندوقِ کامپیوتر Ctrl+R را با همان کلیدِ نشان و پیش از کلیدهای صفحه می‌گیرد`() {
    val s = src(desk)
    assertTrue(s.contains("val priceListOn = remember { ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs.on(context) }"))
    val key = s.indexOf("if (PosKeys.priceList(keyName(e.key), e.isPrimaryModifier, e.isShiftPressed, e.isAltPressed, priceListOn)) {")
    assertTrue(key >= 0)
    assertTrue(key < s.indexOf("when (PosKeys.screen(keyName(e.key), e.isPrimaryModifier)) {"))
    assertTrue(s.indexOf("priceListToggles++", key) in key until key + 220)
    assertEquals(1, Regex(Regex.escape("PriceListAtPos(cartStore, d, cart, cartDebtorId, toggles = priceListToggles)")).findAll(s).count())
  }

  @Test fun `نشان شمار را فقط پس از دروازه و فقط با افزایش با همان toggle می‌خواند`() {
    val s = src(chip)
    assertTrue(s.contains("modifier: Modifier = Modifier, toggles: Int = 0)"))
    val gate = s.indexOf("if (!on) return")
    val effect = s.indexOf("LaunchedEffect(toggles) { if (toggles != seen[0]) { seen[0] = toggles; toggle() } }")
    assertTrue(gate in 0 until effect)
    //  همان `PriceList.toggle` ِ نشان؛ انتخابِ دستی با خودِ فاکتور نوشته می‌شود (POS-12/18: نگه‌داشتن و بازگشت)
    val toggle = s.indexOf("  fun toggle() {\n    state = PriceList.toggle(state)\n    cartStore.setPriceList(CartPriceList.hand(state))\n  }")
    assertTrue(toggle in (gate + 1) until effect)
    assertEquals(2, Regex(Regex.escape("PriceListChip(state.list, { toggle() }")).findAll(s).count())
    //  گوشی شمار نمی‌دهد ⇒ همان امروز
    assertTrue(src("ir/vil3ntec/tohid/ui/v3/sales/cart/PhoneTillTools.kt").contains("PriceListAtPos(cart, data, lines, debtorId)"))
  }
}
