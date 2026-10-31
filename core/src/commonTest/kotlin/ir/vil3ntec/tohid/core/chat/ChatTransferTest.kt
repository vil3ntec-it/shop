package ir.vil3ntec.tohid.core.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CHAT-05 (پیشرفتِ فرستادنِ رسانه) و CHAT-07 (جای دستگاه پیش از ضبط/پخش/عکس) */
class ChatTransferTest {

  private val mb = 1024L * 1024

  /** عکس = اصلی + پیش‌نمایش در یک نوار؛ پس از اصلی نوار به صفر برنمی‌گردد */
  @Test
  fun imageProgressSpansBothUploads() {
    val p = ChatProgress(listOf(900_000L, 100_000L))
    assertEquals(0, p.at(0, 0))
    assertEquals(45, p.at(0, 450_000))
    assertEquals(90, p.at(0, 900_000))
    assertNull(p.done(0), "اصلی پذیرفته شد؛ درصد همان ۹۰ است و دوباره گزارش نمی‌شود")
    assertEquals(95, p.at(1, 50_000))
    assertEquals(99, p.at(1, 100_000), "همهٔ بایت‌ها رفت ولی سرور هنوز نپذیرفته ⇒ ۹۹، نه ۱۰۰")
    assertEquals(100, p.done(1))
    assertEquals(100, p.percent)
  }

  /** فقط تغییرِ دست‌کم یک درصد؛ تکهٔ کوچکِ بعدی صفحه را از نو نمی‌کشد */
  @Test
  fun reportsOnlyWholePercentSteps() {
    val p = ChatProgress(listOf(1_000_000L))
    assertEquals(0, p.at(0, 5_000))
    assertNull(p.at(0, 9_999), "هنوز زیرِ ۱٪")
    assertEquals(1, p.at(0, 10_000))
    var reports = 0
    for (b in 10_000L..1_000_000L step 16_384L) if (p.at(0, b) != null) reports++
    assertTrue(reports <= 99, "حداکثر یک گزارش برای هر درصد ($reports)")
  }

  /** تلاشِ دوبارهٔ همان بخش (بایتِ کمتر) نوار را عقب نمی‌برد؛ عددِ بیرون از بازه بریده می‌شود */
  @Test
  fun neverGoesBackwardsAndClamps() {
    val p = ChatProgress(listOf(200L, 200L))
    assertEquals(50, p.at(0, 10_000), "بیش از اندازهٔ بخش ⇒ همان اندازه")
    assertNull(p.at(0, 20), "عقب‌گرد نشان داده نمی‌شود")
    assertEquals(50, p.percent)
    assertNull(p.at(0, -5))
    assertNull(p.at(7, 100), "بخشِ ناشناخته")
    assertNull(p.done(-1))
    assertEquals(50, p.percent)
  }

  /** صدا (یک بخش) و پیامی که همهٔ بخش‌هایش پیش‌تر رفته‌اند */
  @Test
  fun singlePartAndNothingLeft() {
    val v = ChatProgress(listOf(300_000L))
    assertEquals(99, v.at(0, 300_000))
    assertEquals(100, v.done(0))
    val none = ChatProgress(emptyList())
    assertEquals(100, none.percent, "بی بخشِ مانده ⇒ فقط فرستادنِ خودِ پیام")
    val zero = ChatProgress(listOf(0L))
    assertEquals(0, zero.percent)
    assertEquals(100, zero.done(0))
  }

  /** ضبطِ ۵ دقیقه با ۲۴ کیلوبیت: ۹۰۰ کیلوبایت + سربار */
  @Test
  fun voiceNeedIsTheWorstCaseRecording() {
    assertEquals(900_000L + ChatSpace.VOICE_OVERHEAD_BYTES, ChatSpace.voiceNeed(5 * 60 * 1000L))
    assertEquals(ChatSpace.VOICE_OVERHEAD_BYTES, ChatSpace.voiceNeed(-1, 24_000))
    assertEquals(1_500_000L + 64_000L, ChatSpace.imageNeed(1_500_000, 64_000))
    assertEquals(0L, ChatSpace.imageNeed(-1, -1))
  }

  /** پس از نوشتن دست‌کم RESERVE_BYTES بماند؛ مرز دقیق؛ جای نامعلوم کار را نمی‌بندد */
  @Test
  fun fitsKeepsAReserve() {
    val need = 1 * mb
    assertTrue(ChatSpace.fits(ChatSpace.RESERVE_BYTES + need, need), "درست به اندازه")
    assertFalse(ChatSpace.fits(ChatSpace.RESERVE_BYTES + need - 1, need), "یک بایت کمتر ⇒ جا نیست")
    assertFalse(ChatSpace.fits(0, need), "دستگاهِ پر")
    assertFalse(ChatSpace.fits(10 * mb, 0), "حتی بی نوشتن، زیرِ ذخیره ⇒ جا نیست")
    assertTrue(ChatSpace.fits(null, need), "نامعلوم ⇒ کار بسته نمی‌شود")
    assertTrue(ChatSpace.fits(-1, need), "خطای سنجش ⇒ کار بسته نمی‌شود")
    assertTrue(ChatSpace.fits(64 * mb, -5), "نیازِ منفی ⇒ صفر")
  }
}
