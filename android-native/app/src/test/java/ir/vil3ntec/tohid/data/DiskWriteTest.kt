package ir.vil3ntec.tohid.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 *  F0-01 — نوشتنِ اتمی و بررسی‌شده (`DiskWrite`).
 *
 *  روی اندروید و لینوکس هم می‌دود، ولی جای اصلی‌اش ویندوز است
 *  (`tohid-desktop.yml` ← `disk-windows`): آن‌جا `renameTo` فایلِ موجود را
 *  جایگزین نمی‌کرد.
 */
class DiskWriteTest {

  private fun tempDir(): File = Files.createTempDirectory("diskwrite").toFile()

  @Test fun `نوشتنِ پشتِ‌سرِهم روی فایلِ موجود هر بار جایگزین می‌کند`() {
    val dir = tempDir()
    val f = File(dir, "shop-data.json")
    for (i in 1..5) {
      DiskWrite.text(f, "{\"n\":$i,\"name\":\"برنج\"}")
      assertEquals("{\"n\":$i,\"name\":\"برنج\"}", f.readText(Charsets.UTF_8))
    }
    assertFalse("فایلِ کناری نباید بماند", File(dir, "shop-data.json.tmp").exists())
    dir.deleteRecursively()
  }

  @Test fun `رونوشت همان بایت‌ها را روی نسخهٔ قبلی می‌نشاند`() {
    val dir = tempDir()
    val src = File(dir, "a.json").apply { writeBytes(ByteArray(70_000) { (it % 251).toByte() }) }
    val dst = File(dir, "b.json").apply { writeText("کهنه") }
    DiskWrite.copy(src, dst)
    assertArrayEquals(src.readBytes(), dst.readBytes())
    assertFalse(File(dir, "b.json.tmp").exists())
    dir.deleteRecursively()
  }

  @Test fun `پوشهٔ مقصد اگر نباشد ساخته می‌شود`() {
    val dir = tempDir()
    val f = File(dir, "auto-backup/x.json")
    DiskWrite.text(f, "x")
    assertEquals("x", f.readText())
    dir.deleteRecursively()
  }

  @Test fun `جایگزین نشدن بی‌صدا نیست و فایلِ کناری نمی‌ماند`() {
    val dir = tempDir()
    //  جای فایل را پوشه‌ای پر گرفته: روی هیچ سیستمی جایگزین نمی‌شود
    val f = File(dir, "shop-data.json").apply { mkdirs() }
    File(f, "blocker").writeText("x")
    try {
      DiskWrite.text(f, "{}")
      fail("باید IOException می‌داد")
    } catch (expected: IOException) {
      assertTrue(expected.message.orEmpty().isNotBlank())
    }
    assertTrue("پوشهٔ مانع دست‌نخورده می‌ماند", File(f, "blocker").isFile)
    assertFalse(File(dir, "shop-data.json.tmp").exists())
    dir.deleteRecursively()
  }
}
