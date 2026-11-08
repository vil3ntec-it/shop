package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.core.sync.RestoredDevice.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** DATA-06 — گوشیِ بازیابی‌شده از پشتیبانِ گوشیِ دیگر (شرح سرِ `RestoredDevice.kt`؛ اندروید: `V1ReplicaAndroid.kt`) */
class RestoredDeviceTest {

  private val old = "0123456789abcdef0123456789abcdef"
  private val now = "fedcba9876543210fedcba9876543210"
  private val staffTag = SyncReplica.tag("usr_staff")

  @Test
  fun aReplicaFromAnotherDeviceIsAlwaysFenced() {
    //  رونوشتِ خامِ `tohid-sync-v1` (بستگی هم آمد، یا نسخهٔ پیشین بی بستگی)
    assertEquals(Verdict.FENCE, RestoredDevice.decide(now, bound = old, replica = old, restoredAt = 0, history = true))
    assertEquals(Verdict.FENCE, RestoredDevice.decide(now, bound = "", replica = old, restoredAt = 0, history = true))
    assertEquals(Verdict.FENCE, RestoredDevice.decide(now, bound = "", replica = "$old-$staffTag", restoredAt = 0, history = false))
    //  آیفون: نگهبانِ شناسه replica را پیش‌تر از شناسهٔ تازه کرد، ولی حال هنوز به دستگاهِ پیشین بسته است ⇒ حصار (بازسازی)
    assertEquals(Verdict.FENCE, RestoredDevice.decide(now, bound = old, replica = now, restoredAt = 0, history = true))
  }

  @Test
  fun theNormalRestoreIsFencedWhenTheDeviceIdWasMintedOverHistory() {
    //  `tohid-sync-v1.xml` نیامد (حالِ خالی)، شناسه روی دفتر/صفِ پشتیبان ساخته شد
    assertEquals(Verdict.FENCE, RestoredDevice.decide(now, bound = "", replica = "", restoredAt = 5, history = true))
    //  حسابِ تازه با دفترِ خالی روی همان گوشیِ بازیابی‌شده ⇒ فقط بستن
    assertEquals(Verdict.BIND, RestoredDevice.decide(now, bound = "", replica = "", restoredAt = 5, history = false))
  }

  @Test
  fun anUpgradeFromAnOlderVersionNeverRotates() {
    //  بی بستگی، replica ِ همین دستگاه (خام یا برچسب‌دار) یا هنوز هیچ، شناسه از پیش بود (restoredAt = 0)
    assertEquals(Verdict.BIND, RestoredDevice.decide(now, bound = "", replica = now, restoredAt = 0, history = true))
    assertEquals(Verdict.BIND, RestoredDevice.decide(now, bound = "", replica = "$now-$staffTag", restoredAt = 0, history = true))
    assertEquals(Verdict.BIND, RestoredDevice.decide(now, bound = "", replica = "", restoredAt = 0, history = true))
    assertEquals(Verdict.SAME, RestoredDevice.decide(now, bound = now, replica = now, restoredAt = 0, history = true))
    //  گوشیِ بازیابی‌شده پس از حصار: همان دستگاه، هر بار
    assertEquals(Verdict.SAME, RestoredDevice.decide(now, bound = now, replica = "", restoredAt = 5, history = true))
  }

  @Test
  fun anUnreadableDeviceIdDecidesNothing() {
    assertEquals(Verdict.UNDECIDED, RestoredDevice.decide("", bound = old, replica = old, restoredAt = 5, history = true))
    assertEquals(Verdict.UNDECIDED, RestoredDevice.decide(" ", bound = "", replica = "", restoredAt = 5, history = true))
  }

  @Test
  fun remapKeepsEveryAccountSeparate() {
    //  صاحب (خام) ⇒ خامِ تازه؛ شاگرد (برچسب‌دار) ⇒ همان برچسب روی شناسهٔ تازه
    assertEquals(now, RestoredDevice.remap(old, now, "usr_owner", rawOwner = ""))
    val staff = RestoredDevice.remap("$old-$staffTag", now, "usr_staff", rawOwner = "usr_owner")
    assertEquals("$now-$staffTag", staff)
    assertEquals(SyncReplica.scoped(now, "usr_staff"), staff)
    assertNotEquals(now, staff)
    //  خامِ تازه را حسابِ دیگری زودتر گرفته ⇒ شناسهٔ خودِ همین حساب (دو حساب هرگز یک شناسه)
    val second = RestoredDevice.remap(old, now, "usr_b", rawOwner = "usr_a")
    assertEquals(SyncReplica.scoped(now, "usr_b"), second)
    assertNotEquals(now, second)
    //  از پیش مالِ همین دستگاه ⇒ دست نمی‌خورد؛ خالی ⇒ خالی (از نو انتخاب)
    assertEquals("$now-$staffTag", RestoredDevice.remap("$now-$staffTag", now, "usr_staff", "usr_owner"))
    assertEquals("", RestoredDevice.remap("", now, "usr_owner", ""))
    //  دوباره‌پذیر
    assertEquals(staff, RestoredDevice.remap(staff, now, "usr_staff", "usr_owner"))
    assertTrue(staff.length <= 64)
  }

  @Test
  fun belongsOnlyToThisDevice() {
    assertTrue(RestoredDevice.belongs(now, now))
    assertTrue(RestoredDevice.belongs(SyncReplica.scoped(now, "usr_staff"), now))
    assertFalse(RestoredDevice.belongs(old, now))
    assertFalse(RestoredDevice.belongs(SyncReplica.scoped(old, "usr_staff"), now))
    assertFalse(RestoredDevice.belongs(now, ""))
    assertFalse(RestoredDevice.belongs("x-$staffTag", now))
  }

  @Test
  fun historyAtMintIsTheBackupItself() {
    assertFalse(RestoredDevice.mintedOverHistory("", listOf("shop-settings.json", "photos")))
    assertTrue(RestoredDevice.mintedOverHistory("usr_owner", emptyList()))
    assertTrue(RestoredDevice.mintedOverHistory("", listOf("oplog-usr_owner.json")))
    assertTrue(RestoredDevice.mintedOverHistory("", listOf("sync-journal-usr_owner.json")))
    assertTrue(RestoredDevice.mintedOverHistory("", listOf("sync-replay-usr_owner-sent.jsonl")))
    //  حالِ Sync v3: پوشهٔ ساختِ پیشین در خودِ `filesDir` و پوشهٔ امروز (`V3StateDir`)
    assertTrue(RestoredDevice.mintedOverHistory("", listOf("sync-v3-usr_owner")))
    assertTrue(RestoredDevice.mintedOverHistory("", listOf("sync-v3")))
  }

  @Test
  fun aCrashMidFenceRepeatsTheSameDecisionAndResult() {
    //  پیش از `commit` ِ یگانه هیچ چیزی جز گام‌های دوباره‌پذیر ننشسته ⇒ همان تصمیم و همان replica
    val first = RestoredDevice.decide(now, bound = "", replica = old, restoredAt = 0, history = true)
    val again = RestoredDevice.decide(now, bound = "", replica = old, restoredAt = 0, history = true)
    assertEquals(first, again)
    //  مالکِ خام در گامِ پیش از `commit` نوشته شد (همین حساب) ⇒ همان نتیجه
    assertEquals(RestoredDevice.remap(old, now, "usr_owner", ""), RestoredDevice.remap(old, now, "usr_owner", "usr_owner"))
    //  پس از `commit`: همین دستگاه
    assertEquals(Verdict.SAME, RestoredDevice.decide(now, bound = now, replica = RestoredDevice.remap(old, now, "usr_owner", ""), restoredAt = 0, history = true))
  }
}
