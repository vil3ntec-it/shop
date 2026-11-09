package ir.vil3ntec.tohid.ui.v3.settings.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  OPS-02 — عبارتِ بازیابی در عکسِ صفحه، ضبطِ صفحه و پیش‌نمایشِ «برنامه‌های اخیر» ِ اندروید نمی‌آید (`FLAG_SECURE`)، فقط
 *  تا وقتی عبارت روی صفحه است (نشان دادن، نوشتنِ دوباره، باز کردنِ نسخهٔ رمزدار) و پس از آن پاک می‌شود. کامپیوتر و آیفون
 *  هیچ (همان API، بی‌اثر)؛ و عبارت هیچ‌جا در سابقه/خروجیِ برنامه نوشته نمی‌شود. (رابطِ Compose، پس متن سنجیده می‌شود.)
 */
class RecoveryPhraseSecureWiringTest {

  private fun src(rel: String): String =
    listOf("", "../", "../../")
      .map { File(it + rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private val sheet = "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/settings/sections/RecoveryPhraseSheet.kt"
  private val android = "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/Platform.kt"
  /** پیاده‌سازیِ اندرویدی جدا (مرزِ سکو: `android.view`/`android.app` بیرون از `Platform.kt` ِ هم‌رسانده با کامپیوتر و آیفون) */
  private val androidImpl = "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/SecureWindowAndroid.kt"
  private val desk = "desktop/src/main/kotlin/ir/vil3ntec/tohid/ui/PlatformDesk.kt"
  private val ios = "ios/shared/src/iosMain/kotlin/ir/vil3ntec/tohid/ui/PlatformIos.kt"

  @Test fun `برگهٔ عبارت فقط تا عبارت روی صفحه است پنجره را امن می‌کند`() {
    val s = src(sheet)
    //  درونِ محتوای برگه (پنجرهٔ خودِ برگه) و با گام، نه همیشه
    val phrase = s.indexOf("V3Sheet(onDismiss = { if (!ctrl.making) ctrl.cancelPhrase() }")
    val secure = s.indexOf("ir.vil3ntec.tohid.ui.Platform.SecureWindow(RecoveryPhraseFlow.Step.secret(ctrl.phraseStep))")
    assertTrue(phrase >= 0 && secure > phrase)
    assertTrue("پیش از نخستین گام", secure < s.indexOf("RecoveryPhraseFlow.Step.INTRO ->"))
    //  باز کردنِ نسخهٔ رمزدار: عبارتِ نوشته‌شده هم
    val unlock = s.indexOf("V3Sheet(onDismiss = ctrl::cancelUnlock")
    assertTrue(unlock > 0 && s.indexOf("ir.vil3ntec.tohid.ui.Platform.SecureWindow(true)", unlock) > unlock)
    assertEquals(2, Regex(Regex.escape("Platform.SecureWindow(")).findAll(s).count())
  }

  @Test fun `اندروید FLAG_SECURE را روی پنجرهٔ برگه و Activity می‌گذارد و پس از آن پاک می‌کند`() {
    assertTrue(src(android).contains("fun SecureWindow(on: Boolean) = secureWindowAndroid(on)"))
    val a = src(androidImpl)
    val fn = a.indexOf("internal fun secureWindowAndroid(on: Boolean)")
    assertTrue(fn > 0)
    val body = a.substring(fn)
    assertTrue(body.contains("DisposableEffect(on, view)"))
    assertTrue("پنجرهٔ خودِ برگه", body.contains("(view.parent as? DialogWindowProvider)?.window"))
    assertTrue("و پنجرهٔ Activity (برگه با Inherit همان را می‌خواند)", body.contains("view.context.activity()?.window"))
    assertTrue(body.contains("if (!on) emptyList()"))
    assertTrue(body.contains("it.addFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
    //  فقط آنچه همین‌جا گذاشته شد پاک می‌شود
    assertTrue(body.contains("onDispose { set.forEach { w -> runCatching { w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } } }"))
  }

  @Test fun `کامپیوتر و آیفون همان API را بی‌اثر دارند`() {
    for (f in listOf(desk, ios)) {
      val t = src(f)
      assertTrue(f, t.contains("fun SecureWindow(on: Boolean) = Unit"))
      assertFalse(f, t.contains("FLAG_SECURE"))
    }
    //  پیاده‌سازیِ اندرویدی در ساختِ کامپیوتر و آیفون نمی‌آید (همان جدول‌های جایگزین/کنار)
    assertTrue(src("desktop/build.gradle.kts").contains("\"ui/SecureWindowAndroid.kt\","))
    assertTrue(src("ios/shared/ios-exclude.txt").lines().any { it.startsWith("ui/SecureWindowAndroid.kt ") })
  }

  @Test fun `عبارت هیچ‌جا در سابقهٔ برنامه نوشته نمی‌شود`() {
    val files = listOf(
      sheet,
      "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/settings/sections/BackupController.kt",
      "android-native/app/src/main/java/ir/vil3ntec/tohid/data/SnapshotSealAndroid.kt",
      "core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/backup/RecoveryPhraseFlow.kt",
      "core/src/commonMain/kotlin/ir/vil3ntec/tohid/core/backup/RecoveryPhrase.kt",
    )
    val log = Regex("""\bprintln\(|\bLog\.[diwevx]\(|System\.(out|err)|printStackTrace\(|\blogger\.""")
    for (f in files) assertEquals(f, emptyList<String>(), log.findAll(src(f)).map { it.value }.toList())
    //  کلاسِ جریان `data class` نیست ⇒ `toString` عبارت را بیرون نمی‌دهد
    assertTrue(src(files[3]).contains("\nclass RecoveryPhraseFlow("))
  }
}
