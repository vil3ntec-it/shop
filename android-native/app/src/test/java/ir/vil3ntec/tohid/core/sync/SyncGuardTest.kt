package ir.vil3ntec.tohid.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SYN-08 و SYN-10 — قاعده‌ها روی JVM، بی گوشی. */
class SyncGuardTest {

  private val now = 1_700_000_000_000L

  @Test
  fun `زمانِ آخرین همگام‌سازی`() {
    assertEquals("هنوز همگام نشده", SyncBadge.since(0, now))
    assertEquals("همین حالا", SyncBadge.since(now - 30_000, now))
    assertEquals("5 دقیقه پیش", SyncBadge.since(now - 5 * 60_000, now))
    assertEquals("3 ساعت پیش", SyncBadge.since(now - 3 * 3_600_000, now))
    assertEquals("2 روز پیش", SyncBadge.since(now - 2 * 86_400_000L, now))
    //  ساعتِ دستگاه عقب رفته: منفی نشان داده نمی‌شود
    assertEquals("همین حالا", SyncBadge.since(now + 60_000, now))
  }

  @Test
  fun `نوارِ وضعیت — شمارِ منتظر، خطا و آخرین همگام‌سازی`() {
    assertEquals(
      "همگام با سرور · آخرین همگام‌سازی: همین حالا",
      SyncBadge.line("همگام با سرور", 0, now, "", now),
    )
    assertEquals(
      "در صفِ ارسال · در صف: 3 · آخرین همگام‌سازی: 5 دقیقه پیش",
      SyncBadge.line("در صفِ ارسال", 3, now - 5 * 60_000, "", now),
    )
    val err = SyncBadge.line("همگام‌سازی خطا دارد", 2, 0, "سرور جواب نداد\n503", now)
    assertEquals("همگام‌سازی خطا دارد: سرور جواب نداد 503 · در صف: 2 · آخرین همگام‌سازی: هنوز همگام نشده", err)
    val long = SyncBadge.line("خطا", 0, 0, "x".repeat(500), now)
    assertTrue(long.contains("x".repeat(SyncBadge.MAX_ERROR - 1) + "…"))
    assertFalse(long.contains("x".repeat(SyncBadge.MAX_ERROR)))
  }

  @Test
  fun `صفِ خالی ⇒ خروج آزاد؛ هر تغییرِ نرفته ⇒ بسته`() {
    assertEquals(LogoutGuard.Allow, LogoutGuard.decide(0, 0))
    assertEquals(LogoutGuard.Block(4), LogoutGuard.decide(4))
    assertEquals(LogoutGuard.Block(1), LogoutGuard.decide(0, 1))
    assertEquals(LogoutGuard.Block(5), LogoutGuard.decide(3, 2))
    //  عددِ خراب صف را خالی نشان نمی‌دهد و خروج را هم باز نمی‌کند
    assertEquals(LogoutGuard.Block(2), LogoutGuard.decide(-1, 2))
  }

  @Test
  fun `خروجِ اجباری فقط با پشتیبان و دو تأیید`() {
    assertFalse(LogoutGuard.canForce(backupWritten = false, confirmations = 2))
    assertFalse(LogoutGuard.canForce(backupWritten = true, confirmations = 1))
    assertTrue(LogoutGuard.canForce(backupWritten = true, confirmations = 2))
  }

  @Test
  fun `پیامِ قفل شمار را می‌گوید و راهِ همگام‌سازی را نشان می‌دهد`() {
    val on = LogoutGuard.message(7, online = true)
    assertTrue(on.startsWith("7 تغییر"))
    assertTrue(on.contains(LogoutGuard.SYNC_NOW))
    val off = LogoutGuard.message(1, online = false)
    assertTrue(off.contains("وصل نیست"))
    assertTrue(LogoutGuard.forceMessage(3, "/x/tohid-1.zip").contains("/x/tohid-1.zip"))
  }
}
