package ir.vil3ntec.tohid.core.keymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سناریوی S50 (§۷.۵) — راهنمای کلیدها (POS-41، بخشِ ۳.۱۶).
 *
 *  ۱) «F1 در هر صفحه فهرستِ درستِ کلیدهای همان صفحه را نشان می‌دهد که از keymap.json آمده است»: این‌جا
 *     فهرستِ هر صفحه از خودِ `KeyMap` (رونوشتِ keymap.json، KeyMapTest) ساخته می‌شود — سراسری + همان صفحه،
 *     فقط live و partial، کلیدِ پوشانده‌شده با `overrides` بیرون — و سنجیده می‌شود که هر کلید در آن فهرست
 *     دقیقاً به یک کار می‌رسد.
 *  ۳) «آزمونِ خودکارِ تداخلِ کلیدها سبز است»: همان KeyMapTest و HeldKeysTest، و همین فهرستِ هر صفحه.
 *
 *  ساخته‌نشده (سند، نه assert):
 *   • پنجرهٔ F1 ِ برنامهٔ کامپیوتر (`ShortcutsHelp` در desktop/Main.kt) فهرستی دست‌نوشته و یکسان برای همهٔ
 *     صفحه‌هاست و از `KeyMap` نمی‌خواند؛ فهرستِ همین صفحه را نشان نمی‌دهد.
 *   • «نگه‌داشتنِ Ctrl نیم ثانیه ⇒ برچسب‌ها» در هیچ صفحه‌ای نیست؛ «Alt تنها» هم جایی برای سنجیدن ندارد.
 */
class ScenarioS50Test {

  private val scopes = KeyMap.SCOPES.associateBy { it.id }
  private val shown = setOf(KeyStatus.LIVE, KeyStatus.PARTIAL)

  /** فهرستِ F1 ِ یک صفحه: کارهای همان صفحه + سراسری‌هایی که آن صفحه نپوشانده؛ فقط live و partial */
  private fun helpFor(screen: String): List<KeyAction> {
    val own = KeyMap.ALL.filter { it.scope == screen && it.status in shown }
    val hidden = own.flatMap { it.overrides }.toSet()
    val global = KeyMap.ALL.filter { scopes[it.scope]?.kind == KeyScope.Kind.GLOBAL && it.status in shown && it.id !in hidden }
    return own + global
  }

  @Test
  fun `S50 F1 کلیدِ سراسریِ راهنماست و از keymap_json می‌آید`() {
    val help = KeyMap.ALL.single { it.id == "global.help" }
    assertEquals(KeyMap.GLOBAL_HELP, help)
    assertEquals(listOf(Chord(KeyName.F1)), help.keys)
    assertEquals(KeyStatus.LIVE, help.status)
    assertTrue("هیچ کارِ دیگری F1 نیست", KeyMap.ALL.none { it.id != help.id && Chord(KeyName.F1) in it.keys })
  }

  @Test
  fun `S50 هر صفحه فهرستِ خودش را دارد و هر کلید در آن به یک کار می‌رسد`() {
    val screens = KeyMap.SCOPES.filter { it.kind == KeyScope.Kind.SCREEN }
    assertTrue(screens.size >= 5)
    val bad = mutableListOf<String>()
    for (sc in screens) {
      val list = helpFor(sc.id)
      if (list.none { it.id == "global.help" }) bad += "${sc.id}: F1 در فهرست نیست"
      list.flatMap { a -> a.keys.map { it to a.id } }.groupBy({ it.first }, { it.second })
        .filter { it.value.size > 1 }
        .forEach { (k, ids) -> bad += "${sc.id}: ${k.label()} ⇒ $ids" }
    }
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `S50 فهرستِ صفحه‌ها با هم فرق دارد و planned در راهنما نمی‌آید`() {
    val sale = helpFor("sale").map { it.id }.toSet()
    val reports = helpFor("reports").map { it.id }.toSet()
    assertTrue("صندوق کلیدِ خودش را دارد", (sale - reports).isNotEmpty())
    val planned = KeyMap.ALL.filter { it.status == KeyStatus.PLANNED }.map { it.id }.toSet()
    for (sc in KeyMap.SCOPES.filter { it.kind == KeyScope.Kind.SCREEN }) {
      assertTrue(sc.id, helpFor(sc.id).none { it.id in planned })
    }
  }
}
