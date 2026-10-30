package ir.vil3ntec.tohid.core.backup

import ir.vil3ntec.tohid.core.backup.RestoreRoute.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S143 (#455): بازیابیِ پشتیبان هرگز op نمی‌سازد — شرح سرِ [RestoreRoute] */
class RestoreRouteTest {

  @Test fun neverSyncedRestoresLocallyWhateverTheRest() {
    for (v3 in listOf(false, true)) for (signedIn in listOf(false, true)) for (online in listOf(false, true)) {
      assertEquals(Route.LOCAL, RestoreRoute.decide(synced = false, v3 = v3, signedIn = signedIn, online = online))
    }
  }

  @Test fun syncedGoesThroughTheServerOnlyWhenSignedInAndOnline() {
    assertEquals(Route.FROM_SERVER, RestoreRoute.decide(synced = true, v3 = false, signedIn = true, online = true))
    assertEquals(Route.NEED_NET, RestoreRoute.decide(synced = true, v3 = false, signedIn = true, online = false))
    assertEquals(Route.NEED_SIGN_IN, RestoreRoute.decide(synced = true, v3 = false, signedIn = false, online = true))
    assertEquals(Route.NEED_SIGN_IN, RestoreRoute.decide(synced = true, v3 = false, signedIn = false, online = false))
  }

  @Test fun syncedOnV3NeverFallsBackToALocalDiff() {
    for (signedIn in listOf(false, true)) for (online in listOf(false, true)) {
      assertEquals(Route.NOT_ON_V3, RestoreRoute.decide(synced = true, v3 = true, signedIn = signedIn, online = online))
    }
  }

  /** هیچ ترکیبی از دستگاهِ همگام به [Route.LOCAL] (جایگزینیِ دفتر بی سرور) نمی‌رسد */
  @Test fun aSyncedDeviceIsNeverRestoredLocally() {
    for (v3 in listOf(false, true)) for (signedIn in listOf(false, true)) for (online in listOf(false, true)) {
      val r = RestoreRoute.decide(synced = true, v3 = v3, signedIn = signedIn, online = online)
      assertTrue(r != Route.LOCAL, "synced v3=$v3 signedIn=$signedIn online=$online ⇒ $r")
    }
  }

  @Test fun countsBackupRowsTheServerLedgerLacks() {
    val backup = mapOf("sales" to listOf("s1", "s2", "gone"), "debtors" to listOf("d1", "deleted"), "expenses" to emptyList())
    val server = mapOf("sales" to listOf("s1", "s2", "s3", "s4"), "debtors" to listOf("d1"))
    assertEquals(2, RestoreRoute.notOnServer(backup, server))
  }

  @Test fun aTableTheServerDoesNotHaveCountsWhole() {
    assertEquals(3, RestoreRoute.notOnServer(mapOf("purchases" to listOf("p1", "p2", "p3")), emptyMap()))
  }

  @Test fun duplicatesAndNewerServerRowsDoNotCount() {
    assertEquals(0, RestoreRoute.notOnServer(mapOf("sales" to listOf("s1", "s1")), mapOf("sales" to listOf("s1", "s9"))))
    assertEquals(0, RestoreRoute.notOnServer(emptyMap(), mapOf("sales" to listOf("s1"))))
  }
}
