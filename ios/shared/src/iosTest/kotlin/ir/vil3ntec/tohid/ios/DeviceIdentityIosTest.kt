package ir.vil3ntec.tohid.ios

import android.content.Context
import android.content.IosContext
import ir.vil3ntec.tohid.ios.identity.DeviceIdentityGuard
import ir.vil3ntec.tohid.ios.identity.DeviceIdentityKeys as K
import ir.vil3ntec.tohid.ios.identity.IosDeviceIdentity
import ir.vil3ntec.tohid.ios.identity.Probe
import ir.vil3ntec.tohid.ios.identity.Verdict
import ir.vil3ntec.tohid.ios.keychain.Keychain
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  هویتِ دستگاهِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): درخواستِ `SecItemAdd` ِ آینهٔ کلیددان
 *  «پس از نخستین بازگشایی، فقط همین دستگاه» است (نه ردهٔ جابه‌جاشونده)، پروندهٔ نشان و پوشه‌اش `NSURLIsExcludedFromBackupKey`
 *  دارند و بیرون از `filesDir` اند، و یک چرخشِ کامل روی NSUserDefaults و پروندهٔ واقعی. کلیددان همان API ِ برنامه (`Keychain`)
 *  است روی کلیددانِ درونِ حافظهٔ همین فرآیند: test.kexe کلیددانِ iOS ندارد (OSStatus -25291، ‎docs/IOS-PLAN-fa.md‎). جدولِ
 *  تصمیم و بسته شدن پس از هر نوشتن روی لینوکس: ‎jvmTest/…/identity/DeviceIdentityJvmTest‎. هر چه دست خورد سرِ جایش برمی‌گردد.
 */
class DeviceIdentityIosTest {

  @Test
  fun mirrorIsThisDeviceOnlyAndMarkerIsOutsideBackup() = keepingRealState {
    val uid = "ios-test-identity-0001"
    IosDeviceIdentity.Store.putKeychain(uid)
    assertEquals(Probe.Value(uid), IosDeviceIdentity.Store.keychain())
    //  همان واژه‌نامه‌ای که `Keychain.add` (زیرِ `putKeychain` ⇐ `Keychain.put`) به `SecItemAdd` می‌دهد
    assertTrue(Keychain.addRequestIs(K.KEYCHAIN_ACCOUNT, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly))
    assertFalse(Keychain.addRequestIs(K.KEYCHAIN_ACCOUNT, kSecAttrAccessibleAfterFirstUnlock))

    IosDeviceIdentity.Store.putMarker(uid)
    assertEquals(Probe.Value(uid), IosDeviceIdentity.Store.marker())
    assertTrue(IosDeviceIdentity.excludedFromBackup(IosDeviceIdentity.markerDir()))
    assertTrue(IosDeviceIdentity.excludedFromBackup(IosDeviceIdentity.markerFile()))
    //  بازنویسی (`DiskWrite` ِ اتمی، پروندهٔ تازه) نشانِ «بیرون از پشتیبان» را نمی‌اندازد
    IosDeviceIdentity.Store.putMarker("$uid-b")
    assertTrue(IosDeviceIdentity.excludedFromBackup(IosDeviceIdentity.markerFile()))
    //  بیرون از پوشهٔ دفتر — پشتیبانِ خودِ برنامه هم آن را نمی‌برد
    assertFalse(IosDeviceIdentity.markerDir().path.startsWith(IosContext.filesDir.path + "/"))
    assertNotEquals(IosContext.filesDir.path, IosDeviceIdentity.markerDir().path)
  }

  @Test
  fun restoredPrefsWithoutDeviceMirrorRotateOnRealStores() = keepingRealState {
    val old = "a".repeat(32)
    val io = TestSuites()
    io.clearAll()
    try {
      io.edit(K.SUITE, mapOf(K.DEVICE to old, K.MIRRORED to "1", K.REPLICA_OWNER to "u1", "license" to "{signed}", "shadow" to "{}"))
      io.edit(K.V1_SUITE, mapOf("u1.replica" to old, "u2.replica" to "$old-k3x9", "u1.cursor" to "7"))
      io.edit("tohid-doc-series", mapOf("u1" to "A1"))
      Keychain.delete(K.KEYCHAIN_ACCOUNT)
      IosDeviceIdentity.markerFile().delete()

      assertEquals(Verdict.Restored(old), DeviceIdentityGuard(io).run())
      val uid = io.get(K.SUITE, K.DEVICE)!!
      assertNotEquals(old, uid)
      assertEquals(32, uid.length)
      assertEquals(Probe.Value(uid), IosDeviceIdentity.Store.keychain())
      assertEquals(Probe.Value(uid), IosDeviceIdentity.Store.marker())
      assertEquals(uid, io.get(K.V1_SUITE, "u1.replica"))
      assertEquals("$uid-k3x9", io.get(K.V1_SUITE, "u2.replica"))
      assertEquals("7", io.get(K.V1_SUITE, "u1.cursor"))
      assertEquals("u1", io.get(K.SUITE, K.REPLICA_OWNER))
      assertEquals("{}", io.get(K.SUITE, "shadow"))
      assertNull(io.get(K.SUITE, "license"))
      assertNull(io.get("tohid-doc-series", "u1"))
      assertNull(io.get(K.SUITE, K.ROTATING_TO))
      //  اجرای دوباره: همین دستگاه، هیچ نوشتنی
      assertEquals(Verdict.Same(repair = false), DeviceIdentityGuard(io).run())
      assertEquals(uid, io.get(K.SUITE, K.DEVICE))
    } finally {
      io.clearAll()
    }
  }

  /**
   *  همان انبارِ برنامه (`IosDeviceIdentity.Store`: `Keychain` و پروندهٔ واقعی) با suiteهای جدا (`ios-test-…`) تا تنظیم‌های
   *  واقعیِ همین فرآیند دست نخورند؛ «قفل نیست» چون فرآیندِ آزمون UIApplication ندارد.
   */
  private class TestSuites : DeviceIdentityGuard.Io by IosDeviceIdentity.Store {
    private fun prefs(suite: String) = IosContext.getSharedPreferences("ios-test-$suite", Context.MODE_PRIVATE)
    override fun protectedData() = true
    override fun get(suite: String, key: String): String? = prefs(suite).getString(key, null)
    override fun keys(suite: String): Set<String> = prefs(suite).all.keys.toSet()
    override fun edit(suite: String, changes: Map<String, String?>) {
      val e = prefs(suite).edit()
      for ((k, v) in changes) if (v == null) e.remove(k) else e.putString(k, v)
      e.commit()
    }
    override fun clear(suite: String) {
      prefs(suite).edit().clear().commit()
    }
    override fun log(line: String) = Unit
    fun clearAll() = (K.DEVICE_ONLY + K.V1_SUITE).forEach { clear(it) }
  }

  /**
   *  کلیددانِ تازهٔ درونِ حافظه برای همین آزمون (نه کلیددانِ آزمونِ سرتاسری، نه کلیددانِ iOS که test.kexe ندارد: پیش‌تر
   *  `Keychain.read` همین‌جا KeychainException می‌داد) و بازگرداندنِ کلیددان و پروندهٔ نشانِ پیشینِ همین فرآیند
   */
  private fun keepingRealState(body: () -> Unit) {
    val memory = Keychain.memory
    Keychain.memory = HashMap()
    val marker = IosDeviceIdentity.markerFile().takeIf { it.exists() }?.readBytes()
    try {
      body()
    } finally {
      if (marker != null) IosDeviceIdentity.markerFile().writeBytes(marker) else IosDeviceIdentity.markerFile().delete()
      Keychain.memory = memory
    }
  }
}
