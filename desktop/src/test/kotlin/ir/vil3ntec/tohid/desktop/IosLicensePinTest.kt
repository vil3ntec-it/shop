package ir.vil3ntec.tohid.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  کلیدِ عمومیِ مجوز در آیفون از همان منبعِ کامپیوتر سنجاق می‌شود (`ios/shared/build.gradle.kts` ⇐
 *  `BuildConfig.LICENSE_PUBLIC_KEY`). تهی ⇒ `SyncStore.PINNED_PUBLIC_KEY` تهی و کلید از NSUserDefaults ِ ویرایش‌پذیر
 *  (و در پشتیبان) خوانده می‌شود ⇐ `LicenseGuard` و `OfflineCode.verify` با کلیدِ دست‌کاری‌پذیر می‌سنجند.
 *  آزمونِ متنی روی دو پروندهٔ Gradle: پروژهٔ آیفون این‌جا ساخته نمی‌شود.
 */
class IosLicensePinTest {
  private val desk = File("build.gradle.kts").readText()
  private val ios = File("../ios/shared/build.gradle.kts").readText()

  private fun line(src: String, head: String): String? = src.lines().firstOrNull { it.startsWith(head) }?.trim()

  /** مقدارِ `LICENSE_PUBLIC_KEY` در قالبِ BuildConfig؛ نامِ یک `val` ِ همان پرونده به تعریفش باز می‌شود */
  private fun pinExpr(src: String): String {
    val raw = Regex("const val LICENSE_PUBLIC_KEY: String = (.+)").find(src)?.groupValues?.get(1)?.trim()
      ?: error("LICENSE_PUBLIC_KEY در قالبِ BuildConfig نیست")
    val open = "\${q("
    if (!raw.startsWith(open) || !raw.endsWith(")}")) return raw
    val inner = raw.removePrefix(open).removeSuffix(")}")
    return Regex("(?m)^\\s*val ${Regex.escape(inner)} = (.+)$").find(src)?.groupValues?.get(1)?.trim() ?: inner
  }

  @Test fun iphonePinsTheLicenseKeyLikeTheDesktop() {
    assertEquals("if (dev) \"\" else licenseKey", pinExpr(desk))
    assertEquals(pinExpr(desk), pinExpr(ios))
    //  اثرِ انگشتِ امضای APK در هر دو عمداً خالی است (نه APK دارند؛ `Integrity.isGenuine`)
    val signing = Regex("const val SIGNING_SHA256: String = (.+)")
    assertEquals("\"\"", signing.find(desk)?.groupValues?.get(1)?.trim())
    assertEquals("\"\"", signing.find(ios)?.groupValues?.get(1)?.trim())
  }

  @Test fun iphoneReadsTheKeyFromTheSameSourceAsTheDesktop() {
    for (head in listOf("val licenseKey = ", "val dev = ", "fun env(", "fun prop(")) {
      val d = line(desk, head)
      assertNotNull("کامپیوتر: $head", d)
      assertEquals(head, d, line(ios, head))
    }
    assertTrue(desk.contains("TOHID_LICENSE_PUBLIC_KEY") && desk.contains("\"tohid.licenseKey\""))
    //  هر دو `prop` همان `android-native/gradle.properties` را می‌خوانند
    val androidDir = Regex("(?m)^val android = rootDir\\.resolve\\(\"([^\"]+)\"\\)")
    fun propsOf(projectDir: File, src: String): File {
      assertTrue(src.contains("android.resolve(\"gradle.properties\").inputStream().use { load(it) }"))
      val rel = androidDir.find(src)?.groupValues?.get(1) ?: error("val android نیست")
      return projectDir.resolve(rel).resolve("gradle.properties").canonicalFile
    }
    val deskProps = propsOf(File("."), desk)
    assertTrue(deskProps.isFile)
    assertEquals(deskProps, propsOf(File("../ios/shared"), ios))
  }

  @Test fun aNewKeyRebuildsTheIphoneConfigAndABlankKeyWarns() {
    assertTrue(desk.contains("inputs.property(\"licenseKey\", licenseKey)"))
    assertTrue(Regex("inputs\\.property\\(\"licenseKey\", (licenseKey|pinnedKey)\\)").containsMatchIn(ios))
    val warn = Regex("if \\(ios && !dev && licenseKey\\.isBlank\\(\\)\\) \\{\\s*logger\\.warn\\(").find(ios)
    assertNotNull("هشدارِ ساخت برای کلیدِ تهی", warn)
    assertTrue(ios.substring(warn!!.range.last).take(400).contains("سنجاق نشده"))
  }
}
