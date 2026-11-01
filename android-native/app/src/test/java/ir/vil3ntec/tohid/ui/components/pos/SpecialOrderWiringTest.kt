package ir.vil3ntec.tohid.ui.components.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  POS-26 — سفارشِ ویژه در هر دو صندوق (حالتِ Compose، پس متن سنجیده می‌شود): فقط با `SpecialOrderPref` ِ همین دستگاه،
 *  هر نوشتن فقط از `SpecialOrderPref.record` (دفترِ همین لحظهٔ دیسک) و فروشِ تحویل فقط از `SpecialOrders.deliver`.
 */
class SpecialOrderWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val bar = "ir/vil3ntec/tohid/ui/components/pos/SpecialOrderBarAndroid.kt"

  @Test fun `فقط با تنظیمِ همین دستگاه`() {
    val s = src(bar)
    assertTrue(s.contains("val on = remember { SpecialOrderPref.on(context) }"))
    assertTrue(s.indexOf("if (!on) return") in 0 until s.indexOf("OutlinedButton("))
  }

  @Test fun `هر نوشتن فقط از SpecialOrderPref record`() {
    val s = src(bar)
    for (x in listOf("store.save", "store.update", "SalesEngine.record", "DebtorEngine.")) assertFalse(x, s.contains(x))
    assertEquals(4, Regex(Regex.escape("SpecialOrderPref.record(store, key,")).findAll(s).count())
    for (x in listOf("SpecialOrders.create(", "SpecialOrders.ready(", "SpecialOrders.deliver(", "SpecialOrders.cancel(")) assertTrue(x, s.contains(x))
    //  لغو و بازپرداخت فقط با مجوزِ «لغوِ فاکتور»
    assertTrue(s.contains("ShopRole.canCancelSale(context)"))
  }

  @Test fun `هر دو صندوق و تنظیمات`() {
    fun count(rel: String, what: String) = Regex(Regex.escape(what)).findAll(src(rel)).count()
    val call = "SpecialOrderBar(d, store, cartDebtorId, ::toast"
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/Sale.kt", call))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/legacy/SaleLegacy.kt", call))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/screens/DeskSale.kt", call))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/legacy/SettingsLegacy.kt", "SpecialOrderSettingsWired(context)"))
    assertEquals(1, count("ir/vil3ntec/tohid/ui/legacy/AppRootLegacy.kt", "StrSpecialOrder.settingsTitle to"))
  }
}
