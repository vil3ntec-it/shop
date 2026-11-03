package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.PhotoSyncPlan.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SYN-14 — عکس‌ها: ادامه از جای قطع، ردیفِ خراب صف را نمی‌بندد، و اولویتِ پایین‌تر از دفتر */
class PhotoSyncPlanTest {

  @Test
  fun cursorStopsBeforeTheFirstUnfinishedPhoto() {
    //  ردیفِ ۱۲ دانلود نشد ⇒ نشانه روی ۱۱ می‌ماند و دورِ بعد از ۱۲ (پیش‌تر تا ۱۳ می‌رفت و ۱۲ جا می‌ماند)
    val page = listOf(Item(11, true), Item(12, false), Item(13, true))
    assertEquals(11L, PhotoSyncPlan.cursor(10, page, head = 13))
    assertTrue(PhotoSyncPlan.stopped(page))
    //  نخستین ردیف ناتمام ⇒ همان نشانهٔ پیشین
    assertEquals(10L, PhotoSyncPlan.cursor(10, listOf(Item(11, false)), head = 11))
  }

  @Test
  fun aFinishedPageMovesToItsHeadAndNeverBack() {
    assertEquals(13L, PhotoSyncPlan.cursor(10, listOf(Item(11, true), Item(13, true)), head = 13))
    assertFalse(PhotoSyncPlan.stopped(listOf(Item(11, true))))
    //  صفحهٔ خالی: همان head ِ سرور، و head ِ کوچک‌تر نشانه را عقب نمی‌برد
    assertEquals(20L, PhotoSyncPlan.cursor(10, emptyList(), head = 20))
    assertEquals(10L, PhotoSyncPlan.cursor(10, emptyList(), head = 4))
    //  rev ِ بی‌ترتیب یا کوچک‌تر نشانه را پایین نمی‌آورد
    assertEquals(12L, PhotoSyncPlan.cursor(12, listOf(Item(9, true)), head = 12))
  }

  @Test
  fun aBrokenPhotoIsSkippedAfterMaxTriesNotForever() {
    var stuck = 0L to 0
    repeat(PhotoSyncPlan.MAX_TRIES - 1) {
      stuck = PhotoSyncPlan.failed(stuck.first, stuck.second, 12)
      assertFalse(PhotoSyncPlan.giveUp(stuck.first, stuck.second, 12))
    }
    stuck = PhotoSyncPlan.failed(stuck.first, stuck.second, 12)
    assertEquals(12L to PhotoSyncPlan.MAX_TRIES, stuck)
    assertTrue(PhotoSyncPlan.giveUp(stuck.first, stuck.second, 12))
    //  ردیفِ دیگر از یک شمرده می‌شود و شمارِ ردیفِ پیشین به آن نمی‌رسد
    assertFalse(PhotoSyncPlan.giveUp(stuck.first, stuck.second, 13))
    assertEquals(13L to 1, PhotoSyncPlan.failed(stuck.first, stuck.second, 13))
  }

  @Test
  fun photosWaitWhileTheLedgerSyncsButNotForever() {
    assertEquals(0L, PhotoSyncPlan.waitMs(ledgerBusy = false, waitedMs = 0))
    assertEquals(PhotoSyncPlan.WAIT_STEP_MS, PhotoSyncPlan.waitMs(ledgerBusy = true, waitedMs = 0))
    //  سقفِ رویهم: آخرین گام کوتاه‌تر، بعد دیگر صبر نیست
    assertEquals(1_000L, PhotoSyncPlan.waitMs(ledgerBusy = true, waitedMs = PhotoSyncPlan.MAX_WAIT_MS - 1_000))
    assertEquals(0L, PhotoSyncPlan.waitMs(ledgerBusy = true, waitedMs = PhotoSyncPlan.MAX_WAIT_MS))
    var waited = 0L
    while (true) {
      val w = PhotoSyncPlan.waitMs(ledgerBusy = true, waitedMs = waited)
      if (w == 0L) break
      waited += w
    }
    assertEquals(PhotoSyncPlan.MAX_WAIT_MS, waited)
  }
}
