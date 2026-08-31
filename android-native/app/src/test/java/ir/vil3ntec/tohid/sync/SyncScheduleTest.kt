package ir.vil3ntec.tohid.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سنجشِ زمان‌بندیِ همگام‌سازی.
 *
 *  قرارِ کاربر این است: «هر تغییری که در هر حساب می‌افتد، در جا روی
 *  سرور بنشیند». این تست‌ها همان قرار را می‌سنجند — نه اینکه امیدوار
 *  باشیم درست است.
 */
class SyncScheduleTest {

  @Test
  fun `تغییرِ تکی پس از مکثِ کوتاه می‌رود`() {
    assertEquals(SyncSchedule.QUIET_MS, SyncSchedule.waitAfterChange(windowStart = 0L, now = 1_000L))
  }

  @Test
  fun `چند تغییرِ پشتِ سرِ هم یک بار فرستاده می‌شوند`() {
    //  ده قلم در یک سبد = ده تغییر. پنجره تازه شروع شده، پس مکثِ عادی
    //  و همه با هم می‌روند، نه ده بار پشتِ سرِ هم.
    val start = 10_000L
    assertEquals(SyncSchedule.QUIET_MS, SyncSchedule.waitAfterChange(start, start + 200))
    assertEquals(SyncSchedule.QUIET_MS, SyncSchedule.waitAfterChange(start, start + 900))
  }

  @Test
  fun `کارِ پیوسته، فرستادن را بی‌نهایت عقب نمی‌اندازد`() {
    //  این همان چیزی است که «در جا» را می‌شکست: هر تغییر مکث را از نو
    //  شروع می‌کرد، پس کسی که پیوسته کار می‌کرد هیچ‌وقت چیزی نمی‌فرستاد.
    val start = 10_000L
    //  نه ثانیه بعد از شروعِ پنجره: فقط یک ثانیه مانده، نه دو تا
    assertEquals(1_000L, SyncSchedule.waitAfterChange(start, start + 9_000))
    //  از سقف گذشته: همین حالا
    assertEquals(0L, SyncSchedule.waitAfterChange(start, start + SyncSchedule.MAX_WAIT_MS))
    assertEquals(0L, SyncSchedule.waitAfterChange(start, start + 60_000))
  }

  @Test
  fun `مکث هیچ‌وقت منفی نمی‌شود`() {
    val start = 10_000L
    for (elapsed in longArrayOf(0, 1, 999, 5_000, 9_999, 10_000, 100_000)) {
      val wait = SyncSchedule.waitAfterChange(start, start + elapsed)
      assertTrue("مکثِ منفی: $wait برای $elapsed", wait >= 0L)
      assertTrue("مکثِ بیش از حد: $wait", wait <= SyncSchedule.QUIET_MS)
    }
  }

  @Test
  fun `فاصلهٔ تلاشِ دوباره بلندتر می‌شود ولی بی‌نهایت نه`() {
    assertEquals(5_000L, SyncSchedule.backoffFor(0))
    assertEquals(15_000L, SyncSchedule.backoffFor(1))
    assertEquals(60_000L, SyncSchedule.backoffFor(2))
    assertEquals(300_000L, SyncSchedule.backoffFor(3))
    //  از آخرین پله بالاتر نمی‌رود — نه اینکه ساعت‌ها صبر کند
    assertEquals(300_000L, SyncSchedule.backoffFor(4))
    assertEquals(300_000L, SyncSchedule.backoffFor(999))
    //  و شمارهٔ منفی هم نمی‌شکند
    assertEquals(5_000L, SyncSchedule.backoffFor(-1))
  }

  @Test
  fun `تغییرِ نفرستاده حتماً دوباره امتحان می‌شود`() {
    //  کسی که در جای بی‌آنتن فروشی ثبت می‌کند: تا نرود، دست برنمی‌داریم
    assertTrue(SyncSchedule.shouldRetry(unsent = true, failed = false, ready = true))
    assertTrue(SyncSchedule.shouldRetry(unsent = true, failed = true, ready = true))
    //  حتی وقتی حساب در دسترس نیست، تغییرِ نرفته فراموش نمی‌شود
    assertTrue(SyncSchedule.shouldRetry(unsent = true, failed = false, ready = false))
  }

  @Test
  fun `وقتی چیزی نمانده، شبکه بی‌دلیل بیدار نمی‌شود`() {
    assertFalse(SyncSchedule.shouldRetry(unsent = false, failed = false, ready = true))
    assertFalse(SyncSchedule.shouldRetry(unsent = false, failed = false, ready = false))
  }

  @Test
  fun `نشستِ تمام‌شده بی‌پایان تکرار نمی‌شود`() {
    //  اگر حساب رفته، هزار بار امتحان کردن هم جواب نمی‌دهد؛ کاربر باید
    //  دوباره وارد شود. باتری و دیتا را برای هیچ خرج نمی‌کنیم.
    assertFalse(SyncSchedule.shouldRetry(unsent = false, failed = true, ready = false))
  }

  @Test
  fun `فاصلهٔ گرفتنِ تغییرِ دیگران معقول است`() {
    //  آن‌قدر کوتاه که شریک زود ببیند، آن‌قدر بلند که باتری نبرد
    assertTrue(SyncSchedule.POLL_MS in 30_000L..300_000L)
  }
}
