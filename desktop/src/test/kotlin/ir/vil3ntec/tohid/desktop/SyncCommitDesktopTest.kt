package ir.vil3ntec.tohid.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

/**
 *  F0-05 — لایهٔ کامپیوتری: `commit()`ِ تنظیمات واقعاً آخرین نوشتن است.
 *
 *  سایهٔ همگام‌سازی حالا با `commit()` می‌نشیند و ژورنال پس از آن پاک
 *  می‌شود. در `DesktopPrefs` پیش‌تر `commit()` همان‌جا می‌نوشت و
 *  `apply()`ی که هنوز در صف بود **بعد از آن** متنِ کهنه‌تر را روی فایل
 *  می‌گذاشت — یعنی سایه پس از «commit» روی دیسک عقب می‌رفت.
 */
class SyncCommitDesktopTest {

  @Test fun `commit پس از applyِ در صف، روی دیسک می‌ماند`() {
    val dir = File(System.getProperty("java.io.tmpdir"), "prefs-order-${System.nanoTime()}").apply { mkdirs() }
    try {
      //  ۱) رشتهٔ یگانهٔ نوشتن را نگه می‌داریم: نوشتنِ بعدی در یک FIFO می‌ماند
      //  تا کسی از آن بخواند (فقط جایی که mkfifo هست — لینوکس/مکِ CI)
      val held = File(dir, "held.json")
      val fifo = File(dir, "held.json.tmp")
      assumeTrue(runCatching { ProcessBuilder("mkfifo", fifo.path).start().waitFor() == 0 }.getOrDefault(false))
      DesktopPrefs(held).edit().putString("h", "1").apply()

      //  ۲) حالِ کهنه در صف، بعد commitِ حالِ تازه — همان ترتیبی که سایه می‌گیرد
      val f = File(dir, "tohid-sync-v1.json")
      val prefs = DesktopPrefs(f)
      prefs.edit().putString("acc.shadow", "کهنه").apply()
      var ok = false
      val committing = thread { ok = prefs.edit().putString("acc.shadow", "تازه").commit() }

      //  ۳) رها کردنِ رشتهٔ نوشتن و صبر تا همهٔ صف بنشیند
      Thread.sleep(200)
      FileInputStream(fifo).use { it.readBytes() }
      committing.join(30_000)
      val barrier = File(dir, "barrier.json")
      DesktopPrefs(barrier).edit().putString("b", "1").apply()
      val deadline = System.currentTimeMillis() + 30_000
      while (!barrier.isFile && System.currentTimeMillis() < deadline) Thread.sleep(20)

      assertTrue(ok)
      assertEquals("تازه", DesktopPrefs(f).getString("acc.shadow", null))
    } finally {
      dir.deleteRecursively()
    }
  }
}
