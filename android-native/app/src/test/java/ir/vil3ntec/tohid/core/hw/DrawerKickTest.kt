package ir.vil3ntec.tohid.core.hw

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  HW-03: کشو «همین حالا یا هرگز» است. نه در صف می‌نشیند، نه در فایل، نه
 *  تلاشِ دوباره دارد؛ و تلاشِ دوبارهٔ رسید یا رسیدی که پس از بازگشتِ صف
 *  چاپ می‌شود نسخهٔ «چاپِ دوباره» را می‌برد.
 *
 *  `Wire` همان کارگرِ `PrintSpooler` است با چاپگرِ ساختگی: هر بایتی که به
 *  چاپگر رسید (حتی تلاشی که «نیمه‌کاره ماند») شمرده می‌شود.
 */
class DrawerKickTest {

  private val profile = PrinterProfile(paperDots = 384, drawer = true)

  /** تصویرِ کوچک؛ نسخهٔ «چاپِ دوباره» یک سطرِ سیاهِ اضافه دارد تا از اصل جدا شود */
  private fun receipt(copy: Boolean): MonoImage {
    val img = MonoImage(384, if (copy) 4 else 3)
    for (x in 0 until 384 step 3) img[x, 1] = true
    if (copy) for (x in 0 until 384) img[x, 3] = true
    return img
  }

  private val first = EscPos.document(receipt(copy = false), profile)
  private val copy = EscPos.document(receipt(copy = true), profile)

  /** ضربهٔ کشو همان‌جایی که `document`/`drawerOnly` می‌گذارد */
  private fun kicks(b: ByteArray) =
    if (b.size >= 7 && b[0] == 0x1B.toByte() && b[1] == 0x40.toByte() && b[2] == 0x1B.toByte() && b[3] == 0x70.toByte()) 1 else 0

  /** کارگرِ صف با چاپگرِ ساختگی — همان گام‌های `PrintSpooler.loop` */
  private class Wire(val q: PrintQueue, val files: Map<String, Pair<ByteArray, ByteArray?>>) {
    val sent = ArrayList<ByteArray>()
    /** `true` ⇒ بایت‌ها رسید ولی خطا برگشت (USB نیمه‌کاره، SO_LINGER، بلوتوث) */
    var failing = true

    fun step(now: Long): Boolean {
      val job = q.next(now) ?: return false
      val (a, b) = files.getValue(job.id)
      val bytes = PrintQueue.payload(job) { c -> if (c) b else a }
      if (bytes == null) { q.discard(job.id); return true }
      q.begin(job.id)
      sent += bytes
      if (failing) q.failed(job.id, "چاپ نیمه‌کاره ماند", now) else q.succeeded(job.id)
      return true
    }
  }

  private fun job(id: String, kind: PrintQueue.Kind = PrintQueue.Kind.RECEIPT) =
    PrintQueue.Job(id, kind, "usb:/dev/bus/usb/001/002", id, createdAt = 0, saleId = id, reason = if (kind == PrintQueue.Kind.REPRINT) "کاغذ گیر کرد" else "")

  @Test fun `کشو هرگز در صف نمی‌نشیند`() {
    try {
      PrintQueue().enqueue(job("d", PrintQueue.Kind.DRAWER))
      fail("کارِ کشو نباید پذیرفته شود")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test fun `تلاشِ دوباره — USBِ نیمه‌کاره کشو را دوباره باز نمی‌کند و نسخهٔ دوم نشانِ چاپِ دوباره دارد`() {
    val q = PrintQueue()
    //  فروشِ نقدی: یک ضربهٔ جدا، همان لحظه، بیرون از صف
    val kick = KickOnce(requestedAt = 0)
    val wireKicks = if (kick.take(10)) kicks(EscPos.drawerOnly(profile)) else 0
    assertTrue(q.enqueue(job("s1")))
    val w = Wire(q, mapOf("s1" to (first to copy)))
    w.step(0)                 // تلاشِ نخست: تکهٔ اول رسید و خطا برگشت
    w.failing = false
    w.step(10_000)            // تلاشِ دوم
    assertEquals(2, w.sent.size)
    assertArrayEquals("تلاشِ نخست همان اصل است", first, w.sent[0])
    assertArrayEquals("تلاشِ دوم نسخهٔ «چاپِ دوباره» است", copy, w.sent[1])
    assertEquals("کشو فقط یک بار، و بیرون از صف", 1, wireKicks + w.sent.sumOf(::kicks))
    assertFalse("ضربه دوباره داده نمی‌شود", kick.take(20))
  }

  @Test fun `چاپِ معوق — یک ساعت تلاش هیچ‌وقت کشو را باز نمی‌کند`() {
    val q = PrintQueue().apply { deferred = true }
    //  حتی اگر بایتِ کهنه‌ای ضربه داشته باشد (نسخه‌ای پیش از این اصلاح)، از صف پاک می‌شود
    val stale = EscPos.document(receipt(copy = false), profile, openDrawer = true)
    val staleCopy = EscPos.document(receipt(copy = true), profile, openDrawer = true)
    q.enqueue(job("s1"))
    val w = Wire(q, mapOf("s1" to (stale to staleCopy)))
    var now = 0L
    while (now <= 3_600_000L) { w.step(now); now += 1_000 }
    assertTrue("یک ساعت تلاش", w.sent.size > 50)
    assertEquals(0, w.sent.sumOf(::kicks))
    assertTrue("پس از تلاشِ نخست همه نسخهٔ «چاپِ دوباره»اند", w.sent.drop(1).all { it.contentEquals(staleCopy.let(EscPos::withoutDrawerKick)) })
  }

  @Test fun `بازگشتِ صف — پس از بسته شدنِ برنامه کشو باز نمی‌شود و رسیدِ نیمه‌راه نشانِ چاپِ دوباره دارد`() {
    val before = PrintQueue()
    before.enqueue(job("s1"))
    before.begin("s1")        // برنامه وسطِ فرستادن بسته شد
    before.enqueue(job("s2")) // هنوز نوبتش نرسیده بود
    //  کارِ کشوی ذخیره‌شده از نسخه‌ای کهنه
    val saved = before.all() + job("d1", PrintQueue.Kind.DRAWER)
    val q = PrintQueue()
    q.restore(saved)
    assertEquals(listOf("s1", "s2"), q.all().map { it.id })
    assertTrue(q.all()[0].needsCopyMark)
    assertFalse("رسیدی که هرگز فرستاده نشد بی نشان چاپ می‌شود", q.all()[1].needsCopyMark)
    val w = Wire(q, mapOf("s1" to (first to copy), "s2" to (first to copy), "d1" to (EscPos.drawerOnly(profile) to null)))
    w.failing = false
    while (w.step(0)) Unit
    assertEquals(2, w.sent.size)
    assertArrayEquals(copy, w.sent[0])
    assertArrayEquals(first, w.sent[1])
    assertEquals(0, w.sent.sumOf(::kicks))
  }

  @Test fun `رسیدِ بی‌نسخهٔ نشان‌دار پس از خطا چاپ نمی‌شود`() {
    val q = PrintQueue()
    q.enqueue(job("s1"))
    q.failed("s1", "x", 0)
    assertNull(PrintQueue.payload(q.all().single()) { c -> if (c) null else first })
  }

  @Test fun `چاپِ دوبارهٔ دستی خودش نشان دارد و نسخهٔ دیگری نمی‌خواهد`() {
    val r = job("r1", PrintQueue.Kind.REPRINT).copy(attempts = 2, restored = true)
    assertFalse(r.needsCopyMark)
    assertArrayEquals(copy, PrintQueue.payload(r) { c -> if (c) null else copy })
  }

  @Test fun `KickOnce — یک بار، و فقط در مهلت`() {
    val k = KickOnce(requestedAt = 1_000)
    assertTrue(k.take(1_000 + KickOnce.WINDOW_MS))
    assertFalse(k.take(1_001))
    assertFalse("دیر رسید ⇒ هرگز", KickOnce(requestedAt = 0).take(KickOnce.WINDOW_MS + 1))
  }

  @Test fun `withoutDrawerKick — همان سندِ بی کشو`() {
    for (mode in ImageMode.entries) {
      val p = profile.copy(mode = mode)
      val img = receipt(copy = false)
      assertArrayEquals(EscPos.document(img, p), EscPos.withoutDrawerKick(EscPos.document(img, p, openDrawer = true)))
      assertArrayEquals(EscPos.document(img, p), EscPos.withoutDrawerKick(EscPos.document(img, p)))
    }
    assertArrayEquals(EscPos.INIT, EscPos.withoutDrawerKick(EscPos.drawerOnly(profile)))
    assertArrayEquals(byteArrayOf(1, 2), EscPos.withoutDrawerKick(EscPos.drawerKick() + byteArrayOf(1, 2)))
  }

  @Test fun `تصویرِ پهن‌تر از کاغذ پذیرفته نمی‌شود`() {
    try {
      EscPos.document(MonoImage(576, 2), PrinterProfile(paperDots = 384))
      fail("بی‌صدا بریده نشود")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.contains("576"))
    }
  }
}
