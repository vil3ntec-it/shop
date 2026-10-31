package ir.vil3ntec.tohid.core.backup

import ir.vil3ntec.tohid.core.backup.RestoreRoute.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S143 (#455): بازیابیِ پشتیبان هرگز op نمی‌سازد — شرح سرِ [RestoreRoute] */
class RestoreRouteTest {

  private val unknownOrSome = listOf<Int?>(null, 1, 500)

  /** LOCAL فقط وقتی که وارد نشده یا حسابِ سرور خالی است */
  @Test fun neverSyncedRestoresLocallyOnlySignedOutOrOnAnEmptyAccount() {
    for (v3 in listOf(false, true)) for (online in listOf(false, true)) for (rows in listOf<Int?>(null, 0, 3)) {
      assertEquals(Route.LOCAL, RestoreRoute.decide(synced = false, v3 = v3, signedIn = false, online = online, serverRows = rows))
    }
    for (v3 in listOf(false, true)) for (online in listOf(false, true)) {
      assertEquals(Route.LOCAL, RestoreRoute.decide(synced = false, v3 = v3, signedIn = true, online = online, serverRows = 0))
    }
  }

  /**
   *  #485 (بازبینِ مستقل): واردشده ولی هرگز همگام‌نشده، و سرور ردیف دارد ⇒ ردیف‌ها از سرور، نه LOCAL —
   *  وگرنه پشتیبانِ کهنه با ردیف‌های سرور می‌آمیخت (فاکتورِ ۲۰۰، برگشت‌های ۱۰۰ + ۲۰۰ = ۳۰۰، `returnedQty` ۳ از ۲).
   */
  @Test fun signedInNeverSyncedGoesThroughTheServerWhenItHasRows() {
    assertEquals(Route.FROM_SERVER, RestoreRoute.decide(synced = false, v3 = false, signedIn = true, online = true, serverRows = 1))
    assertEquals(Route.FROM_SERVER, RestoreRoute.decide(synced = false, v3 = false, signedIn = true, online = true, serverRows = 500))
  }

  /** ندانیم سرور چه دارد ⇒ «اول همگام کنید»، هیچ چیزی عوض نمی‌شود */
  @Test fun signedInNeverSyncedWithoutAServerAnswerAsksToSyncFirst() {
    assertEquals(Route.SYNC_FIRST, RestoreRoute.decide(synced = false, v3 = false, signedIn = true, online = true, serverRows = null))
    for (rows in unknownOrSome) {
      assertEquals(Route.SYNC_FIRST, RestoreRoute.decide(synced = false, v3 = false, signedIn = true, online = false, serverRows = rows))
    }
    //  v3 هنوز راهِ «از سرور» ندارد
    for (online in listOf(false, true)) for (rows in unknownOrSome) {
      assertEquals(Route.NOT_ON_V3, RestoreRoute.decide(synced = false, v3 = true, signedIn = true, online = online, serverRows = rows))
    }
  }

  /** هیچ ترکیبی از «واردشده و سرورِ نه‌خالی» به LOCAL نمی‌رسد */
  @Test fun aSignedInDeviceOnANonEmptyAccountIsNeverRestoredLocally() {
    for (synced in listOf(false, true)) for (v3 in listOf(false, true)) for (online in listOf(false, true)) for (rows in unknownOrSome) {
      val r = RestoreRoute.decide(synced = synced, v3 = v3, signedIn = true, online = online, serverRows = rows)
      assertTrue(r != Route.LOCAL, "synced=$synced v3=$v3 online=$online rows=$rows ⇒ $r")
    }
  }

  @Test fun syncedGoesThroughTheServerOnlyWhenSignedInAndOnline() {
    assertEquals(Route.FROM_SERVER, RestoreRoute.decide(synced = true, v3 = false, signedIn = true, online = true, serverRows = null))
    assertEquals(Route.NEED_NET, RestoreRoute.decide(synced = true, v3 = false, signedIn = true, online = false, serverRows = null))
    assertEquals(Route.NEED_SIGN_IN, RestoreRoute.decide(synced = true, v3 = false, signedIn = false, online = true, serverRows = null))
    assertEquals(Route.NEED_SIGN_IN, RestoreRoute.decide(synced = true, v3 = false, signedIn = false, online = false, serverRows = null))
  }

  @Test fun syncedOnV3NeverFallsBackToALocalDiff() {
    for (signedIn in listOf(false, true)) for (online in listOf(false, true)) {
      assertEquals(Route.NOT_ON_V3, RestoreRoute.decide(synced = true, v3 = true, signedIn = signedIn, online = online, serverRows = null))
    }
  }

  /** هیچ ترکیبی از دستگاهِ همگام به [Route.LOCAL] (جایگزینیِ دفتر بی سرور) نمی‌رسد */
  @Test fun aSyncedDeviceIsNeverRestoredLocally() {
    for (v3 in listOf(false, true)) for (signedIn in listOf(false, true)) for (online in listOf(false, true)) for (rows in listOf<Int?>(null, 0, 3)) {
      val r = RestoreRoute.decide(synced = true, v3 = v3, signedIn = signedIn, online = online, serverRows = rows)
      assertTrue(r != Route.LOCAL, "synced v3=$v3 signedIn=$signedIn online=$online rows=$rows ⇒ $r")
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
