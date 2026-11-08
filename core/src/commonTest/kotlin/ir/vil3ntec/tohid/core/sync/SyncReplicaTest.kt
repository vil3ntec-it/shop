package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** «شناسهٔ همگام‌سازیِ» هر حساب روی هر دستگاه — شرح سرِ `SyncReplica.kt`؛ سفرِ سرتاسری: `journeys-a.e2e.mjs` (J2b) */
class SyncReplicaTest {

  private val dev = "0123456789abcdef0123456789abcdef"

  @Test
  fun firstAccountAndEveryAccountWithHistoryKeepTheRawDeviceId() {
    //  نخستین حساب روی دستگاه (هنوز صاحبی نیست) ⇒ خام
    assertEquals(dev, SyncReplica.choose(dev, "usr_owner", rawOwner = "", fresh = true))
    //  همان صاحب پس از خروج و ورود ⇒ خام
    assertEquals(dev, SyncReplica.choose(dev, "usr_owner", rawOwner = "usr_owner", fresh = false))
    //  حسابِ دیگری که پیش از این نسخه با شناسهٔ خام همگام شده بود ⇒ عوض نمی‌شود (opهای خودش دو بار نمی‌نشیند)
    assertEquals(dev, SyncReplica.choose(dev, "usr_staff", rawOwner = "usr_owner", fresh = false))
    //  بی حساب ⇒ خام
    assertEquals(dev, SyncReplica.choose(dev, " ", rawOwner = "usr_owner", fresh = true))
  }

  @Test
  fun anotherAccountStartingFreshOnTheSamePhoneGetsItsOwnStableId() {
    val staff = SyncReplica.choose(dev, "usr_staff", rawOwner = "usr_owner", fresh = true)
    assertNotEquals(dev, staff)
    assertTrue(staff.startsWith("$dev-"))
    assertEquals(staff, SyncReplica.choose(dev, "usr_staff", rawOwner = "usr_owner", fresh = true), "پایدار")
    assertNotEquals(staff, SyncReplica.choose(dev, "usr_other", rawOwner = "usr_owner", fresh = true))
    //  نویسه‌ها و سقفِ `validate.id` ِ سرور
    assertTrue(Regex("^[A-Za-z0-9_.:-]{1,64}$").matches(staff), staff)
    assertTrue(SyncReplica.scoped("x".repeat(80), "usr_staff").length <= 64)
  }

  @Test
  fun tagMatchesTheJavascriptMirror() {
    //  همان `replicaTag` ِ `server/test/e2e/lib/native.mjs` (FNV-1a ِ ۶۴ بیتی، base36)
    assertEquals("39gjg7qg46c1q", SyncReplica.tag("usr_abc"))
    assertEquals("c2tzdir0tu62", SyncReplica.tag("usr_0123456789abcdef"))
    assertEquals("4srrna9nrxwo", SyncReplica.tag("حسن"))
  }
}
