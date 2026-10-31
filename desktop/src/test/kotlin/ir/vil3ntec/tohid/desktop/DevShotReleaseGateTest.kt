package ir.vil3ntec.tohid.desktop

import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.nio.file.Files

/**
 *  SEC-CLI (۱۴۰۵/۰۸/۰۹): ابزارِ عکس و اسکریپتِ آزمون (`DevShot`، با `typecmd` که فرمانِ پوسته اجرا
 *  می‌کند) در ساختِ انتشار با هیچ `-Dtohid.shot…`ی روشن نمی‌شود. `DevShot.start` پیش از ساختنِ
 *  رشته پوشهٔ عکس را می‌سازد؛ پس نبودنِ آن پوشه یعنی هیچ کاری نکرده است.
 *  ساختِ `-Pdev=true` (`desktop-shots.yml`) همان رفتارِ پیشین را دارد و این آزمون آن‌جا کنار می‌رود.
 */
class DevShotReleaseGateTest {

  @Test fun `در ساختِ انتشار ویژگیِ tohid-shot هیچ کاری نمی‌کند`() {
    assumeFalse("ساختِ dev همان رفتارِ پیشین را دارد", ir.vil3ntec.tohid.BuildConfig.DEBUG)
    val dir = Files.createTempDirectory("devshot-gate").toFile().resolve("shots")
    val keys = listOf("tohid.shot", "tohid.shot.script", "tohid.shot.every")
    val old = keys.associateWith { System.getProperty(it) }
    try {
      System.setProperty("tohid.shot", dir.absolutePath)
      System.setProperty("tohid.shot.script", "wait:1")
      System.setProperty("tohid.shot.every", "1")
      DevShot.start()
      assertFalse("پوشهٔ عکس ساخته شد ⇒ ابزارِ آزمون در نسخهٔ انتشار روشن است", dir.exists())
    } finally {
      for ((k, v) in old) if (v == null) System.clearProperty(k) else System.setProperty(k, v)
      dir.parentFile.deleteRecursively()
    }
  }
}
