package ir.vil3ntec.tohid.core.keymap

import ir.vil3ntec.tohid.core.keys.KeyHelp
import ir.vil3ntec.tohid.ui.components.keys.keyHelpItems
import ir.vil3ntec.tohid.ui.components.keys.keyHelpSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سناریوی S50 (§۷.۵) — راهنمای کلیدها (POS-41، بخشِ ۳.۱۶).
 *
 *  ۱) «F1 در هر صفحه فهرستِ درستِ کلیدهای همان صفحه را نشان می‌دهد که از keymap.json آمده است»: با تنظیمِ
 *     «راهنمای کلیدِ هر صفحه» (`KeyHelpPref`، پیش‌فرض خاموش) پنجرهٔ F1 ِ کامپیوتر `KeyMapHelp` است و فهرستش
 *     `keyHelpSections(مسیرِ صفحهٔ باز)` — از خودِ `KeyMap` (رونوشتِ keymap.json، KeyMapTest) با `core/keys/KeyHelp`:
 *     کارهای همان صفحه، بعد سراسری‌هایی که آن صفحه نپوشانده؛ فقط live و partial؛ هر کلید به یک کار می‌رسد.
 *  ۲) «با نگه‌داشتنِ Ctrl نیم ثانیه برچسب‌ها نمایان می‌شوند؛ Alt ِ تنها استفاده نشود»: پنجرهٔ کامپیوتر هر کلید را
 *     پیش از همه به `KeyLabels.onKey` می‌دهد؛ زمان‌سنجِ `KeyHelp.Hold` (core، KeyHelpTest) فقط Ctrl/Cmd ِ تنها را
 *     می‌شمارد و `KeyLabelsOverlay` برچسب‌های Ctrl ِ همان صفحه را نشان می‌دهد.
 *  ۳) «آزمونِ خودکارِ تداخلِ کلیدها سبز است»: همان KeyMapTest و HeldKeysTest، و همین فهرستِ هر صفحه.
 *
 *  تنظیمِ خاموش ⇒ همان فهرستِ دست‌نوشتهٔ پیشین (`ShortcutsHelp`) و بی برچسب.
 */
class ScenarioS50Test {

  private fun source(vararg rel: String): File? = rel.flatMap { r -> listOf(r, "../$r", "../../$r") }.map { File(it) }.firstOrNull { it.isFile }

  @Test
  fun `S50 F1 کلیدِ سراسریِ راهنماست و از keymap_json می‌آید`() {
    val help = KeyMap.ALL.single { it.id == "global.help" }
    assertEquals(KeyMap.GLOBAL_HELP, help)
    assertEquals(listOf(Chord(KeyName.F1)), help.keys)
    assertEquals(KeyStatus.LIVE, help.status)
    assertTrue("هیچ کارِ دیگری F1 نیست", KeyMap.ALL.none { it.id != help.id && Chord(KeyName.F1) in it.keys })
    assertEquals(KeyMap.ALL.size, keyHelpItems().size)
  }

  @Test
  fun `S50 هر صفحه فهرستِ خودش را دارد و هر کلید در آن به یک کار می‌رسد`() {
    val screens = KeyMap.SCOPES.filter { it.kind == KeyScope.Kind.SCREEN }
    assertTrue(screens.size >= 5)
    val bad = mutableListOf<String>()
    for (sc in screens) {
      val list = KeyHelp.forScreen(keyHelpItems(), setOf("global"), sc.id).flatMap { it.items }
      if (list.none { it.id == "global.help" }) bad += "${sc.id}: F1 در فهرست نیست"
      list.flatMap { a -> a.keys.map { it to a.id } }.groupBy({ it.first }, { it.second })
        .filter { it.value.size > 1 }
        .forEach { (k, ids) -> bad += "${sc.id}: $k ⇒ $ids" }
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `S50 مسیرِ صفحهٔ باز فهرستِ همان صفحه را می‌دهد و planned نمی‌آید`() {
    val sale = keyHelpSections("sale")
    assertEquals("sale", sale.first().scope)
    assertTrue(sale.first().items.all { it.scope == "sale" })
    val reports = keyHelpSections("reports").flatMap { it.items }.map { it.id }.toSet()
    assertTrue("صندوق کلیدِ خودش را دارد", (sale.flatMap { it.items }.map { it.id }.toSet() - reports).isNotEmpty())
    //  S42: Ctrl+B ِ «شلوغم» زنده است و در فهرستِ «سفارش‌ها» می‌آید
    assertTrue(keyHelpSections("orders").first().items.any { it.id == "orders.busy" && it.keys == listOf("Ctrl+B") })
    //  صفحهٔ بی کلیدِ خود ⇒ فقط سراسری
    assertEquals(listOf("global"), keyHelpSections("dashboard").map { it.scope })
    val planned = KeyMap.ALL.filter { it.status == KeyStatus.PLANNED }.map { it.id }.toSet()
    for (route in listOf("sale", "debtors", "purchasing", "orders", "reports", "products", "dashboard")) {
      assertTrue(route, keyHelpSections(route).flatMap { it.items }.none { it.id in planned })
    }
  }

  @Test
  fun `S50 برچسب‌های Ctrl همان کلیدهای Ctrl ِ همان صفحه‌اند`() {
    val labels = KeyHelp.ctrlLabels(keyHelpSections("sale"))
    assertTrue(labels.isNotEmpty())
    assertTrue(labels.all { it.keys.first().startsWith("Ctrl+") })
    assertTrue("Ctrl+K ِ سراسری هم هست", labels.any { it.id == "global.palette" })
  }

  @Test
  fun `S50 پنجرهٔ کامپیوتر F1 را از keymap می‌کشد و Ctrl را پیش از همه می‌شمارد`() {
    val main = source("desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt", "src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt")
    assertNotNull("desktop/Main.kt پیدا نشد", main)
    val text = main!!.readText()
    assertTrue(text.contains("if (ir.vil3ntec.tohid.ui.components.keys.KeyHelpPref.on.value) TohidTheme(dialogTheme) { ir.vil3ntec.tohid.ui.components.keys.KeyMapHelp() }"))
    assertTrue(text.contains("else TohidTheme(dialogTheme) { ShortcutsHelp() }"))
    assertTrue(Regex("""onPreviewKeyEvent = \{ e ->\s+ir\.vil3ntec\.tohid\.ui\.components\.keys\.KeyLabels\.onKey\(e\)""").containsMatchIn(text))
    assertTrue(text.contains("ir.vil3ntec.tohid.ui.components.keys.KeyLabelsOverlay()"))
    val keys = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/components/keys/KeyHelpAndroid.kt", "src/main/java/ir/vil3ntec/tohid/ui/components/keys/KeyHelpAndroid.kt")!!.readText()
    assertTrue(keys.contains("keyHelpSections(route)"))
    //  Alt ِ تنها برچسب نمی‌آورد: فقط Ctrl/Cmd، و Ctrl+Alt هم نه
    assertTrue(keys.contains("e.key == Key.CtrlLeft || e.key == Key.CtrlRight || e.key == Key.MetaLeft || e.key == Key.MetaRight"))
    assertTrue(keys.contains("hold.down(ctrl && !e.isAltPressed && !e.isShiftPressed, now)"))
    assertFalse(keys.contains("Key.AltLeft"))
  }
}
