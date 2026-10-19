package ir.vil3ntec.tohid.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
