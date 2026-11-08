package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.SignOutSafety.Flush
import ir.vil3ntec.tohid.core.sync.SignOutSafety.Hold
import ir.vil3ntec.tohid.core.sync.SignOutSafety.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** خروجِ بی گم شدنِ داده — قاعده‌های خالص (شرح سرِ `AccountSafety.kt`) */
class AccountSafetyTest {

  @Test
  fun signOutIsFreeOnlyWhenTheServerHasEverything() {
    assertEquals(SignOutSafety.Allow, SignOutSafety.decide(Flush(queued = 0)))
    assertEquals(Hold(3, Reason.OFFLINE), SignOutSafety.decide(Flush(queued = 3, online = false)))
    assertEquals(Hold(2, Reason.FAILED), SignOutSafety.decide(Flush(queued = 2, failed = true)))
    assertEquals(Hold(1, Reason.FAILED), SignOutSafety.decide(Flush(queued = 0, legacy = 1)))
    assertEquals(Hold(4, Reason.NOT_SIGNED_IN), SignOutSafety.decide(Flush(queued = 4, signedIn = false)))
  }

  @Test
  fun serverRejectionHoldsEvenWithAnEmptyQueue() {
    //  ردشده‌ها از صف بیرون رفته‌اند ولی به سرور نرسیده‌اند ⇒ «صفر» نیست
    assertEquals(Hold(2, Reason.REJECTED), SignOutSafety.decide(Flush(queued = 0, rejected = 2)))
  }

  @Test
  fun neverReconciledLedgerIsNotZeroPending() {
    assertEquals(Hold(1, Reason.NOT_RECONCILED), SignOutSafety.decide(Flush(queued = 0, reconciled = false)))
    assertEquals(Hold(1, Reason.OFFLINE), SignOutSafety.decide(Flush(queued = 0, reconciled = false, online = false)))
  }

  @Test
  fun brokenCountsNeverOpenSignOut() {
    assertEquals(Hold(2, Reason.FAILED), SignOutSafety.decide(Flush(queued = -1, legacy = 2)))
    assertEquals(0, SignOutSafety.pendingOf(Flush(queued = -5, legacy = -1)))
  }

  @Test
  fun forcedSignOutNeedsTwoConfirmationsAndASafeCopy() {
    assertTrue(SignOutSafety.canForce(2, backupWritten = true, keptForAccount = true))
    assertFalse(SignOutSafety.canForce(1, backupWritten = true, keptForAccount = true))
    assertFalse(SignOutSafety.canForce(2, backupWritten = false, keptForAccount = true))
    assertFalse(SignOutSafety.canForce(9, backupWritten = true, keptForAccount = false))
  }

  @Test
  fun syncKeyCarriesOnlyThisAccountsOwnLegacyState() {
    val has = setOf("Ahmad", "dev-1", "u-other")
    fun carry(sameDesk: Boolean = true, prev: String = "", targetHas: Boolean = false, c: List<String> = listOf("Ahmad")) =
      SyncAccountKey.carryFrom("u-ahmad", targetHas, sameDesk, prev, c, "dev-1") { it in has }
    assertEquals("Ahmad", carry())
    assertEquals("Ahmad", carry(prev = "u-ahmad"))
    assertNull(carry(sameDesk = false), "دفترِ روی میز مالِ کسِ دیگری بود")
    assertNull(carry(prev = "u-other"), "کلیدِ پیشین مالِ حسابِ دیگری بود")
    assertNull(carry(targetHas = true), "کلیدِ تازه حالِ خودش را دارد")
    assertNull(carry(c = listOf("dev-1")), "کلیدِ بی‌حسابِ دستگاه هرگز منبع نیست")
    assertNull(carry(c = listOf("nobody")), "کلیدی بی حال")
    assertEquals("Ahmad", carry(c = listOf("", "u-ahmad", "dev-1", "Ahmad")))
  }

  @Test
  fun emptyLedgerNeverDeletesEverything() {
    assertTrue(EmptyLedgerGuard.blocks(localRows = 0, shadowRows = 120))
    assertFalse(EmptyLedgerGuard.blocks(localRows = 1, shadowRows = 120))
    assertFalse(EmptyLedgerGuard.blocks(localRows = 0, shadowRows = 0))
  }
}
