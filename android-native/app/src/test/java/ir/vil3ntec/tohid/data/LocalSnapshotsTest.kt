package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  OPS-02 — نسخهٔ محلیِ نیم‌ساعته فقط وقتی دفتر عوض شده، و هرس با
 *  «۶ آخر + ۷ روزانه + ۴ هفتگی».
 *
 *  ⛔ آخرین بند همان «هرگز جلوی فروش را نگیرد» است: وقتی رونوشت‌گیری دفتر
 *  را باز نگه داشته، نوشتنِ دفتر (`DiskWrite`، همان مسیرِ ثبتِ فروش) باید
 *  بگذرد. جای اصلی‌اش ویندوز است (`tohid-desktop.yml` ← `disk-windows`).
 *  دادهٔ این‌جا ساختگی است.
 */
class LocalSnapshotsTest {

  private fun tempDir(): File = Files.createTempDirectory("snapshots").toFile()
  private val kabul: (Long) -> Int = { 270 }

  @Test fun `بی دفتر یا دفترِ خالی، نسخه‌ای ساخته نمی‌شود`() {
    val dir = tempDir()
    val ledger = File(dir, "shop-data.json")
    val snaps = File(dir, "auto-backups")
    assertNull(LocalSnapshots.takeIfChanged(ledger, snaps, "auto-1.json"))
    ledger.writeText("")
    assertNull(LocalSnapshots.takeIfChanged(ledger, snaps, "auto-1.json"))
    assertTrue(LocalSnapshots.list(snaps).isEmpty())
  }

  @Test fun `فقط وقتی دفتر عوض شده نسخهٔ تازه ساخته می‌شود`() {
    val dir = tempDir()
    val ledger = File(dir, "shop-data.json")
    val snaps = File(dir, "auto-backups")
    ledger.writeText("""{"products":[{"id":"p1","name":"برنج"}]}""")

    val first = LocalSnapshots.takeIfChanged(ledger, snaps, "auto-2026-10-17-100000.json")
    assertNotNull(first)
    assertEquals(ledger.readText(), first!!.readText())
    //  نیم ساعت بعد، دفتر همان است ⇐ هیچ
    assertNull(LocalSnapshots.takeIfChanged(ledger, snaps, "auto-2026-10-17-103000.json"))
    //  هم‌اندازه ولی متفاوت ⇐ نسخهٔ تازه (اندازه به‌تنهایی معیار نیست)
    ledger.writeText("""{"products":[{"id":"p1","name":"روغن"}]}""")
    first.setLastModified(System.currentTimeMillis() - 60_000)
    val second = LocalSnapshots.takeIfChanged(ledger, snaps, "auto-2026-10-17-110000.json")
    assertNotNull(second)
    assertEquals(2, LocalSnapshots.list(snaps).size)
    assertEquals("auto-2026-10-17-110000.json", LocalSnapshots.list(snaps).first().file.name)
  }

  @Test fun `هرس فقط نسخه‌های خودکار را می‌بیند و قاعده را اجرا می‌کند`() {
    val dir = tempDir()
    val day = 86_400_000L
    val now = 1_760_000_000_000L
    //  ۳۰ روز، روزی یک نسخه
    for (i in 0 until 30) {
      val f = File(dir, "auto-d$i.json")
      f.writeText("{\"n\":$i}")
      f.setLastModified(now - i * day)
    }
    //  فایل‌های دیگرِ پوشه دست نمی‌خورند
    File(dir, "auto-x.json.tmp").writeText("نیمه‌کاره")
    File(dir, "before-restore.json").writeText("{}")

    val removed = LocalSnapshots.prune(dir, kabul)
    val left = LocalSnapshots.list(dir).map { it.file.name }.toSet()
    assertEquals(30 - left.size, removed)
    //  d0 = پنجشنبه ۲۰۲۵/۱۰/۰۹ ساعتِ ۱۳:۲۳ کابل. ۷ روزِ آخر (d0..d6؛ ۶ آخر درونشان است)
    //  + تازه‌ترینِ دو هفتهٔ پیش‌تر (هفته از شنبه): جمعه‌های d13 و d20 ⇐ ۹ نسخه
    assertEquals((0..6).map { "auto-d$it.json" }.toSet() + setOf("auto-d13.json", "auto-d20.json"), left)
    assertFalse("auto-d29.json" in left)
    assertTrue(File(dir, "auto-x.json.tmp").exists())
    assertTrue(File(dir, "before-restore.json").exists())
    //  هرسِ دوباره چیزی پاک نمی‌کند
    assertEquals(0, LocalSnapshots.prune(dir, kabul))
  }

  @Test fun `same — یکی، اندازهٔ متفاوت، محتوای متفاوت، فایلِ بزرگ`() {
    val dir = tempDir()
    val a = File(dir, "a")
    val b = File(dir, "b")
    val big = ByteArray(300_000) { (it % 251).toByte() }
    a.writeBytes(big)
    b.writeBytes(big)
    assertTrue(LocalSnapshots.same(a, b))
    b.writeBytes(big.copyOf().also { it[299_999] = 1 })
    assertFalse(LocalSnapshots.same(a, b))
    b.writeBytes(big.copyOf(1000))
    assertFalse(LocalSnapshots.same(a, b))
    assertFalse(LocalSnapshots.same(a, File(dir, "نیست")))
  }

  @Test fun `⛔ رونوشت‌گیریِ باز جلوی نوشتنِ دفتر را نمی‌گیرد`() {
    val dir = tempDir()
    val ledger = File(dir, "shop-data.json")
    DiskWrite.text(ledger, "{\"n\":1}")
    LocalSnapshots.openShared(ledger).use { reading ->
      //  همان مسیرِ ثبتِ فروش (`ShopStore` ⇐ `DiskWrite.text`) در حالی که خواننده باز است
      DiskWrite.text(ledger, "{\"n\":2}")
      assertEquals("{\"n\":2}", ledger.readText())
      //  خواننده هم نسخهٔ قبلی را کامل دارد (نه نیمه‌کاره)
      assertEquals("{\"n\":1}", reading.readBytes().toString(Charsets.UTF_8))
    }
  }
}
