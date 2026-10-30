package ir.vil3ntec.tohid.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ورودِ گروهیِ عکس: تطبیقِ فایل با کالا و کارِ ادامه‌پذیر (v3 ۳۶.۸، IMG-01/02/09/14). */
class BulkImportTest {

  private val items = listOf(
    BulkMatch.Item("rice", "برنج", listOf("6260000000017")),
    BulkMatch.Item("oil", "روغن نباتی", emptyList()),
    BulkMatch.Item("soda-a", "نوشابه قوطی", listOf("036000291452")),        // UPC-A
    BulkMatch.Item("soda-b", "نوشابه قوطی وارداتی", listOf("0036000291452")), // همان GTIN، ۱۳ رقمی
    BulkMatch.Item("salt", "نمک", listOf("6260000000024")),
  )

  @Test fun `بارکد در نامِ فایل، با پسوندِ شماره و رقمِ فارسی`() {
    for (f in listOf("6260000000017.jpg", "6260000000017_2.JPG", "6260000000017-1.png", "۶۲۶۰۰۰۰۰۰۰۰۱۷.jpeg", "photos/6260000000017 (3).webp")) {
      val r = BulkMatch.match(f, items)
      assertEquals(f, BulkMatch.Status.MATCHED, r.status)
      assertEquals(f, "rice", r.productId)
      assertEquals("barcode", r.how)
    }
  }

  @Test fun `نامِ کاملِ کالا — زیرخط و ی و ک عربی`() {
    assertEquals("oil", BulkMatch.match("روغن_نباتی.jpg", items).productId)
    assertEquals("oil", BulkMatch.match("روغن نباتي.jpg", items).productId)
    //  نامِ «مشابه» تطبیق نیست
    assertEquals(BulkMatch.Status.UNMATCHED, BulkMatch.match("روغن.jpg", items).status)
    assertEquals(BulkMatch.Status.UNMATCHED, BulkMatch.match("برنج ایرانی.jpg", items).status)
  }

  @Test fun `یک GTIN برای دو کالا ⇒ مبهم، هیچ‌کدام خودکار نه`() {
    val r = BulkMatch.match("036000291452.jpg", items)
    assertEquals(BulkMatch.Status.AMBIGUOUS, r.status)
    assertEquals("", r.productId)
    assertEquals(listOf("soda-a", "soda-b"), r.candidates)
  }

  @Test fun `CSV بر نامِ فایل مقدم است`() {
    val csv = BulkMatch.parseCsv("﻿image_filename;barcode;name\n\"label 1.JPG\";6260000000024;\nx.jpg;;روغن نباتی\n")
    assertEquals("salt", BulkMatch.match("dir/LABEL 1.jpg", items, csv).productId)
    assertEquals("csv_barcode", BulkMatch.match("label 1.jpg", items, csv).how)
    assertEquals("oil", BulkMatch.match("x.jpg", items, csv).productId)
    assertTrue(BulkMatch.parseCsv("a,b\n1,2").isEmpty())                      // سرستونِ ناشناخته
  }

  /** همان سه نمونهٔ بازبینی: پسوندِ شماره بخشی از نامِ کالا بود و کالای دیگری گرفته می‌شد */
  @Test fun `نامِ کامل اول؛ بی پسوند فقط وقتی کامل هیچ نیافت؛ دو جوابِ متفاوت ⇒ مبهم`() {
    val aa = BulkMatch.Item("aa", "باتری قلمی", emptyList())
    val aa4 = BulkMatch.Item("aa4", "باتری قلمی (4)", emptyList())
    val pack = BulkMatch.Item("pack", "Pack", emptyList())
    val pack12 = BulkMatch.Item("pack12", "Pack-12", emptyList())
    val ean8 = BulkMatch.Item("ean8", "کالای هشت‌رقمی", listOf("12345678"))
    val code10 = BulkMatch.Item("code10", "کدِ ده‌رقمی", listOf("1234567812"))
    fun ambiguous(file: String, list: List<BulkMatch.Item>, vararg ids: String) {
      val r = BulkMatch.match(file, list)
      assertEquals(file, BulkMatch.Status.AMBIGUOUS, r.status)
      assertEquals(file, "", r.productId)
      assertEquals(file, ids.toList(), r.candidates)
    }
    fun matched(file: String, list: List<BulkMatch.Item>, id: String) {
      val r = BulkMatch.match(file, list)
      assertEquals(file, BulkMatch.Status.MATCHED, r.status)
      assertEquals(file, id, r.productId)
    }
    //  هر دو کالا هست ⇒ مبهم، هیچ‌کدام خودکار نه
    ambiguous("باتری قلمی (4).jpg", listOf(aa, aa4), "aa", "aa4")
    ambiguous("Pack-12.jpg", listOf(pack, pack12), "pack", "pack12")
    ambiguous("12345678-12.jpg", listOf(ean8, code10), "ean8", "code10")
    //  فقط کالای نامِ کامل ⇒ همان (پیش از این کالای بی پسوند گرفته می‌شد یا هیچ)
    matched("باتری قلمی (4).jpg", listOf(aa4), "aa4")
    matched("Pack-12.jpg", listOf(pack12), "pack12")
    matched("12345678-12.jpg", listOf(code10), "code10")
    //  نامِ کامل هیچ نیافت ⇒ بی پسوندِ شماره (نسخهٔ دومِ عکسِ همان کالا)
    matched("باتری قلمی (4).jpg", listOf(aa), "aa")
    matched("Pack-12.jpg", listOf(pack), "pack")
    matched("12345678-12.jpg", listOf(ean8), "ean8")
    assertEquals("barcode", BulkMatch.match("12345678-12.jpg", listOf(ean8)).how)
    //  دو راه، همان کالا ⇒ تطبیق
    matched("Pack-12.jpg", listOf(BulkMatch.Item("p", "Pack", listOf()), BulkMatch.Item("q", "Other", listOf())), "p")
  }

  @Test fun `CSV — بارکد و نامِ دو کالا ⇒ مبهم؛ نامِ فایلِ تکراری ⇒ مبهم، نه آخری برنده`() {
    val csv = LinkedHashMap<String, List<BulkMatch.CsvRow>>()
    BulkMatch.parseCsv(
      "image_filename,barcode,name\n" +
        "a.jpg,6260000000017,نمک\n" +          // بارکد ⇒ برنج، نام ⇒ نمک
        "b.jpg,6260000000017,برنج\n" +         // هر دو برنج
        "c.jpg,6260000000017,\n" +             // دو ردیف، دو کالا
        "c.jpg,6260000000024,\n" +
        "d.jpg,6260000000024,\n" +             // ردیفِ عیناً تکراری ⇒ یکی
        "d.jpg,6260000000024,\n" +
        "e.jpg,6260000000024,\n" +             // دو ردیفِ متفاوت، همان کالا
        "e.jpg,,نمک\n" +
        "f.jpg,6260000000017,\n",
      csv,
    )
    //  CSVِ دوم همان نامِ فایل را جورِ دیگری می‌گوید
    BulkMatch.parseCsv("file;name\nF.JPG;روغن نباتی\n", csv)
    fun r(f: String) = BulkMatch.match(f, items, csv)
    assertEquals(BulkMatch.Status.AMBIGUOUS, r("a.jpg").status)
    assertEquals(listOf("rice", "salt"), r("a.jpg").candidates)
    assertEquals("csv_conflict", r("a.jpg").how)
    assertEquals("rice", r("b.jpg").productId)
    assertEquals(BulkMatch.Status.AMBIGUOUS, r("c.jpg").status)
    assertEquals(listOf("rice", "salt"), r("c.jpg").candidates)
    assertEquals("csv_duplicate", r("c.jpg").how)
    assertEquals(1, csv["d.jpg"]!!.size)
    assertEquals("salt", r("d.jpg").productId)
    assertEquals("salt", r("e.jpg").productId)
    assertEquals(BulkMatch.Status.AMBIGUOUS, r("f.jpg").status)
    assertEquals(listOf("rice", "oil"), r("f.jpg").candidates)
    //  مبهم با یک نامزد هم «مبهم» است، نه تطبیقِ خودکار؛ نامزدِ حذف‌شده ⇒ بی‌تطبیق
    val j = BulkJob("j")
    j.rows += BulkJob.Row(1, "x.jpg", 1, "x", candidates = listOf("rice"))
    j.plan { photos[it] }
    assertEquals(BulkJob.Status.AMBIGUOUS, j.rows[0].status)
    j.rows[0].status = BulkJob.Status.NEW
    j.plan { null }
    assertEquals(BulkJob.Status.UNMATCHED, j.rows[0].status)
  }

  /**
   *  بازبینی: اصلِ سیصد عکس در `filesDir/image-bulk` می‌ماند و پشتیبانِ ابری
   *  (سقفِ ۲۵ مگ) را پر می‌کرد. حالا فقط نسخهٔ آماده زیرِ `no_backup` و پایانِ کار ⇒ خالی.
   */
  @Test fun `ورودِ گروهی هیچ فایلی زیرِ پوشه‌های پشتیبان‌دار نمی‌گذارد و پایانِ کار پوشه را خالی می‌کند`() {
    val app = java.nio.file.Files.createTempDirectory("app").toFile()
    try {
      val backedUp = listOf(File(app, "files"), File(app, "shared_prefs"), File(app, "databases")).onEach { it.mkdirs() }
      val noBackup = File(app, "no_backup").apply { mkdirs() }
      val store = BulkStore.under(noBackup)
      assertTrue(store.root.canonicalPath.startsWith(noBackup.canonicalPath + File.separator))

      //  «افزودن»: هر فایلِ متمایز فقط نسخهٔ آماده (بی EXIF) را می‌گذارد
      val j = job().plan { photos[it] }
      for (r in j.rows) if (!store.has(r.sum)) store.put(r.sum, "display-${r.sum}".toByteArray(), "thumb".toByteArray())
      store.save(j)
      store.tidy(j)
      assertFalse(store.has("s5"))                     // کالای یکسان — دیگر لازم نیست
      assertNotNull(store.get("s3"))                   // مبهم — هنوز با انتخابِ کاربر راه می‌افتد
      assertEquals("display-s1", String(store.get("s1")!!.display))

      //  پردازش: انجام‌شده ⇒ فایلش پاک
      j.rows[0].status = BulkJob.Status.DONE
      j.rows[1].status = BulkJob.Status.DONE
      store.tidy(j)
      assertFalse(store.has("s1")); assertFalse(store.has("s2")); assertTrue(store.has("s4"))

      //  همه نهایی (ناموفق هم) ⇒ کلِ پوشهٔ فایل‌ها
      j.rows[3].status = BulkJob.Status.DONE
      j.rows[4].status = BulkJob.Status.FAILED
      j.rows[6].status = BulkJob.Status.DONE
      assertTrue(j.settled())
      store.save(j)
      store.tidy(j)
      assertEquals(listOf("job.json"), store.root.list()!!.toList())
      assertNull(store.get("s4"))
      for (d in backedUp) assertTrue("${d.name} باید خالی بماند", d.walk().none { it.isFile })
      assertEquals(j.toJson(), store.load()!!.toJson())
      store.clear()
      assertFalse(store.root.exists())
    } finally {
      app.deleteRecursively()
    }
  }

  @Test fun `GTIN — رقمِ کنترل`() {
    assertEquals("06260000000019", BulkMatch.gtin14("6260000000019"))
    assertEquals("", BulkMatch.gtin14("6260000000017"))   // رقمِ کنترلِ غلط؛ تطبیقِ رقم‌به‌رقم هنوز کار می‌کند
    assertEquals(BulkMatch.gtin14("036000291452"), BulkMatch.gtin14("0036000291452"))
    assertEquals("", BulkMatch.gtin14("12345"))
  }

  private fun job(): BulkJob {
    val j = BulkJob("j1")
    fun add(file: String, sum: String) {
      val m = BulkMatch.match(file, items)
      j.rows += BulkJob.Row(j.rows.size + 1, file, 100, sum, productId = m.productId, candidates = m.candidates, how = m.how)
    }
    add("6260000000017.jpg", "s1")
    add("روغن نباتی.jpg", "s2")
    add("copy.jpg", "s1")                 // همان بایت‌ها
    add("036000291452.jpg", "s3")
    add("نامعلوم.jpg", "s4")
    add("6260000000017_2.jpg", "s5")      // کالای یکسان، فایلِ دیگر
    add("6260000000024.jpg", "s6")        // نمک — عکس دارد
    return j
  }
  private val photos = mapOf("rice" to false, "oil" to false, "soda-a" to false, "soda-b" to false, "salt" to true)

  @Test fun `برنامه — تکراری، کالای یکسان، مبهم، بی‌تطبیق و عکس‌دار`() {
    val j = job().plan { photos[it] }
    assertEquals(
      listOf("ready", "ready", "duplicate", "ambiguous", "unmatched", "same_product", "has_photo"),
      j.rows.map { it.status.wire },
    )
    //  تیکِ «جایگزینی» ⇒ کالای عکس‌دار هم آماده
    j.replace = true
    j.rows.forEach { if (it.status == BulkJob.Status.HAS_PHOTO) it.status = BulkJob.Status.NEW }
    j.plan { photos[it] }
    assertEquals(BulkJob.Status.READY, j.rows[6].status)
    //  انتخابِ انسان برای مبهم
    j.choose(4, "soda-b") { photos[it] }
    assertEquals(BulkJob.Status.READY, j.rows[3].status)
    assertEquals("manual", j.rows[3].how)
    //  کالای حذف‌شده ⇒ بی‌تطبیق
    j.choose(5, "gone") { photos[it] }
    assertEquals(BulkJob.Status.UNMATCHED, j.rows[4].status)
  }

  @Test fun `ادامه پس از بستنِ برنامه — همان قدم، بی بارگذاریِ دوباره`() {
    val j = job().plan { photos[it] }
    val r0 = j.rows[0]
    r0.local = true
    r0.status = BulkJob.Status.WORKING
    r0.upload.imageId = "img_1"; r0.upload.display = true
    j.rows[1].status = BulkJob.Status.DONE
    val back = BulkJob.fromJson(j.toJson())!!.reopen()
    assertEquals(BulkJob.Status.PENDING_BANK, back.rows[0].status)       // عکسِ دستگاه گذاشته شده بود
    assertEquals("img_1", back.rows[0].upload.imageId)
    assertTrue(back.rows[0].upload.display)
    assertFalse(back.rows[0].upload.thumb)
    assertEquals(BulkJob.Status.DONE, back.rows[1].status)
    assertEquals(listOf("soda-a", "soda-b"), back.rows[3].candidates)
    assertTrue(back.resumed())
    assertEquals(back.rows[0], back.next())
    //  کالایی که حالا عکس دارد (همین ردیف گذاشت) «عکس‌دار» شمرده نمی‌شود
    back.plan { true }
    assertEquals(BulkJob.Status.PENDING_BANK, back.rows[0].status)
    val c = back.counts()
    assertEquals(1, c.done); assertEquals(2, c.duplicate); assertEquals(1, c.ambiguous); assertEquals(1, c.unmatched)
  }

  @Test fun `فایلِ کارِ خراب ⇒ کارِ تازه، نه برنامهٔ خراب`() {
    assertNull(BulkJob.fromJson("{"))
    assertNull(BulkJob.fromJson("{\"v\":9,\"rows\":[]}"))
  }
}
