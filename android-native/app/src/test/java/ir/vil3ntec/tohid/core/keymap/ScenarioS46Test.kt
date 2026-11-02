package ir.vil3ntec.tohid.core.keymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سناریوی S46 (§۷.۵) — تعویضِ کاربر روی صندوق (STF-04).
 *
 *  «Ctrl+L صندوق را قفل می‌کند. کاربرِ دوم با PIN وارد می‌شود. فاکتورِ نیمه‌کارهٔ کاربرِ اول نگه داشته می‌شود
 *  و سابقه‌ها به نامِ کاربرِ درست ثبت می‌شوند.»
 *
 *  سنجیده: سطرِ نخست — Ctrl+L در keymap.json کارِ سراسریِ «قفلِ صندوق» است، کلیدِ دیگری نیست، برنامهٔ کامپیوتر
 *  آن را به `AppNav.lock()` می‌دهد و `AppRoot` با آن قفلِ برنامه را می‌اندازد (از متنِ منبع، چون AppNav وضعیتِ
 *  Compose دارد — همان روشِ DeskMenuTest).
 *
 *  ساخته‌نشده (سند، نه assert):
 *   • «کاربرِ دوم با PIN وارد می‌شود»: قفل یک PIN ِ دستگاه است (`LockStore`)؛ PIN ِ شخصی (`StaffPins`) فقط
 *     برای برگهٔ تأیید است، نه ورود و تعویضِ کاربر.
 *   • «فاکتورِ نیمه‌کارهٔ کاربرِ اول نگه داشته می‌شود»: سبد یکی است (`CartStore`) و پس از باز شدن همان سبد
 *     جلوی هر کسی است که قفل را باز کرد؛ سبدِ جدا برای هر کاربر نیست.
 *   • «سابقه به نامِ کاربرِ درست»: فروشنده همان حسابِ واردشدهٔ دستگاه است، نه کسی که با PIN باز کرد.
 */
class ScenarioS46Test {

  private fun source(vararg rel: String): File? = rel.flatMap { r ->
    listOf(r, "../$r", "../../$r")
  }.map { File(it) }.firstOrNull { it.isFile }

  @Test
  fun `S46 Ctrl+L کارِ سراسریِ قفلِ صندوق است و کلیدِ دیگری نیست`() {
    val lock = KeyMap.ALL.single { it.id == "global.lock" }
    assertEquals("global", lock.scope)
    assertEquals(listOf(Chord(KeyName.L, mod = true)), lock.keys)
    val others = KeyMap.ALL.filter { it.id != lock.id && Chord(KeyName.L, mod = true) in it.keys }
    assertEquals("Ctrl+L جای دیگری هم هست", emptyList<String>(), others.map { it.id })
  }

  @Test
  fun `S46 برنامهٔ کامپیوتر Ctrl+L را به قفلِ برنامه می‌رساند`() {
    val main = source("desktop/src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt", "src/main/kotlin/ir/vil3ntec/tohid/desktop/Main.kt")
    assertNotNull("desktop/Main.kt پیدا نشد", main)
    assertTrue(Regex("""mod && e\.key == Key\.L -> \{[^}]*AppNav\.lock\(\)""").containsMatchIn(main!!.readText()))
    val nav = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/AppNav.kt", "src/main/java/ir/vil3ntec/tohid/ui/AppNav.kt")
    assertNotNull(nav)
    assertTrue(nav!!.readText().contains("fun lock() { lockNow.intValue++ }"))
    val root = source("android-native/app/src/main/java/ir/vil3ntec/tohid/ui/legacy/AppRootLegacy.kt", "src/main/java/ir/vil3ntec/tohid/ui/legacy/AppRootLegacy.kt")
    assertNotNull(root)
    //  هر بالا رفتنِ lockNow ⇒ اگر رمزِ برنامه روشن است، قفل همان دم
    assertTrue(Regex("""LaunchedEffect\(lockAsk\)[\s\S]{0,200}if \(lockStore\.enabled\) unlocked = false""").containsMatchIn(root!!.readText()))
  }
}
