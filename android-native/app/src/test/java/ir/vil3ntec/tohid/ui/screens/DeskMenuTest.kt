package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  منوی گروه‌بندی‌شدهٔ کامپیوتر (v3 §۲۰.۳، VISUAL-DIRECTION §۳):
 *  میان‌برها عوض نمی‌شوند، هیچ پیوندِ مرده‌ای نیست، «در دست ساخت» کلیک ندارد.
 */
class DeskMenuTest {

  private val ui: File? = listOf(
    "src/main/java/ir/vil3ntec/tohid/ui",
    "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui",
    "android-native/app/src/main/java/ir/vil3ntec/tohid/ui",
  ).map { File(it) }.firstOrNull { it.isDirectory }

  /** از متنِ AppNav.kt، نه خودِ شیء — AppNav وضعیتِ Compose دارد (همان روشِ KeyMapTest) */
  private fun shortcuts(): List<Pair<String, String>> {
    val nav = File(ui!!, "AppNav.kt").readText().substringAfter("val SHORTCUTS = listOf(").substringBefore(")\n")
    return Regex(""""([a-z]+)" to "([^"]+)"""").findAll(nav).map { it.groupValues[1] to it.groupValues[2] }.toList()
  }

  @Test
  fun `هر نُه میان‌برِ صفحه دقیقاً یک بار با همان کلید در منو هست`() {
    assertTrue("پوشهٔ ui پیدا نشد", ui != null)
    val sc = shortcuts()
    assertEquals(9, sc.size)
    for (admin in listOf(true, false)) {
      val items = DeskMenu.groups(admin, sc).flatMap { it.items }
      sc.forEachIndexed { i, (id, _) ->
        val hits = items.filter { it.id == id }
        assertEquals("$id یک بار", 1, hits.size)
        assertEquals("$id کلید", "Ctrl+${i + 1}", hits[0].key)
      }
      //  شناسه‌ها تکراری نیستند
      assertEquals(items.size, items.map { it.id }.toSet().size)
    }
  }

  @Test
  fun `هر بخشِ زنده به مسیری در AppRoot می‌رود — پیوندِ مرده نیست`() {
    val root = File(ui!!, "AppRoot.kt").readText()
    val live = DeskMenu.groups(true, shortcuts()).flatMap { it.items }.filter { it.state == DeskMenu.State.LIVE }
    val missing = live.filterNot { Regex("""\n\s*"${it.id}"\s*->""").containsMatchIn(root) }.map { it.id }
    assertEquals("مسیرِ AppRoot ندارند", emptyList<String>(), missing)
  }

  @Test
  fun `در دست ساخت ها شناسهٔ مسیر و کلید ندارند`() {
    val soon = DeskMenu.groups(true, shortcuts()).flatMap { it.items }.filter { it.state == DeskMenu.State.SOON }
    assertTrue(soon.isNotEmpty())
    assertTrue(soon.all { it.id.startsWith("soon:") && it.key.isEmpty() })
  }

  @Test
  fun `گروه‌ها — کانال‌های فروش و سدِ شاگرد`() {
    val g = DeskMenu.groups(true, shortcuts())
    assertEquals(listOf("اصلی", "کانال‌های فروش", "مدیریت و سامانه"), g.map { it.title })
    assertTrue(g[1].items.any { it.id == "sale" })
    val staff = DeskMenu.groups(false, shortcuts()).flatMap { it.items }.map { it.id }
    assertFalse("settings" in staff)
    assertFalse("audit" in staff)
    assertEquals("صاحب دکان", DeskMenu.roleLabel("owner"))
    assertEquals("شاگرد", DeskMenu.roleLabel("staff"))
  }
}
