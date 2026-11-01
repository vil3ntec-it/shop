package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PAY-18 — صفحهٔ گاوصندوق (حالتِ Compose، پس متن سنجیده می‌شود): فقط با `SafeBookPref` ِ همین دستگاه (خاموش ⇒ «تاریخچهٔ
 *  فروش» همان امروز)، هر نوشتن فقط از `SafeBookPref.record` (دفترِ همین لحظهٔ دیسک) و مجوزِ `safe.move` همان سرور.
 */
class SafePanelWiringTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun count(text: String, what: String) = Regex(Regex.escape(what)).findAll(text).count()

  private val panel = "ir/vil3ntec/tohid/ui/screens/SafePanelAndroid.kt"

  @Test fun `فقط با تنظیمِ همین دستگاه در تاریخچهٔ فروش`() {
    val s = src("ir/vil3ntec/tohid/ui/screens/SalesHistory.kt")
    assertEquals(1, count(s, "val safeBook = remember { ir.vil3ntec.tohid.data.SafeBookPref.on(roleContext) }"))
    assertEquals(1, count(s, "if (safeBook) item { SafePanel(d = d, store = store, me = me, toast = ::toast) }"))
    assertEquals(1, count(s, "SafePanel("))
    //  پیش‌فرضِ تنظیم خاموش است
    assertTrue(src("ir/vil3ntec/tohid/data/SafeBookAndroid.kt").contains("getBoolean(KEY, false)"))
  }

  @Test fun `هر نوشتن فقط از SafeBookPref record و روی دفترِ دیسک`() {
    val s = src(panel)
    for (x in listOf("store.save", "store.update", "store.data")) assertFalse(x, s.contains(x))
    assertEquals(1, count(s, "SafeBookPref.record(store, key)"))
    for (x in listOf("SafeBook.count(cur,", "SafeBook.deposit(cur,", "SafeBook.withdraw(cur,", "SafeBook.toBank(cur,", "SafeBook.transfer(cur,")) {
      assertTrue(x, s.contains(x))
    }
    val pref = src("ir/vil3ntec/tohid/data/SafeBookAndroid.kt")
    assertTrue(pref.contains("store.update { mem -> t(DebtEntry.ledgerNow(store, mem)) }"))
    assertTrue(pref.contains("AppendOnce.append<ShopData>("))
  }

  @Test fun `مجوزِ گاوصندوق همان سرور`() {
    val s = src(panel)
    assertTrue(s.contains("ShopRole.allows(context, \"safe.move\")"))
    assertTrue(s.contains("ShopRole.canWrite(context, \"safeEntries\")"))
    //  بی safe.move فقط «از دخل به گاوصندوق»
    val buttons = s.substring(s.indexOf("if (canMove) {\n        TextButton"), s.indexOf("if (shift == null) Text("))
    assertEquals(1, count(buttons, "OutlinedButton(onClick = { mode = SafeCore.FROM_DRAWER })"))
    assertTrue(buttons.contains("if (canMove) OutlinedButton(onClick = { mode = SafeCore.TO_DRAWER })"))
  }

  @Test fun `تنظیمات در هر دو رابط و فهرستِ کامپیوتر`() {
    assertEquals(1, count(src("ir/vil3ntec/tohid/ui/legacy/SettingsLegacy.kt"), "SafeBookSettingsWired(context)"))
    assertEquals(1, count(src("ir/vil3ntec/tohid/ui/screens/Settings.kt"), "SafeBookSettingsWired(context)"))
    assertEquals(1, count(src("ir/vil3ntec/tohid/ui/legacy/AppRootLegacy.kt"), "StrSafe.settingsTitle to"))
  }
}
