package ir.vil3ntec.tohid.ios.identity

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ir.vil3ntec.tohid.ios.identity.DeviceIdentityKeys as K

/**
 *  جدولِ تصمیمِ هویتِ دستگاهِ آیفون (‎commonMain/…/identity/DeviceIdentity.kt‎) و اجرای آن روی انبارِ درونِ حافظه — نصبِ تازه،
 *  همان دستگاه، بازیابی روی دستگاهِ دیگر، به‌روزرسانی از نسخهٔ پیشین، قفل، اجرای پشتِ سرِ هم، و بسته شدنِ برنامه پس از
 *  هر نوشتن. همتای شبیه‌سازش (کلیددانِ واقعیِ «فقط همین دستگاه» و پروندهٔ بیرون از پشتیبان): ‎iosTest/…/DeviceIdentityIosTest‎.
 */
class DeviceIdentityJvmTest {

  private val old = "a".repeat(32)
  private val other = "e".repeat(32)
  private val fresh = listOf("b".repeat(32), "c".repeat(32), "d".repeat(32))

  @Test
  fun freshInstallMakesIdentityAndMirror() {
    val m = Mem()
    assertEquals(Verdict.Fresh, DeviceIdentityGuard(m).run())
    assertEquals(fresh[0], m.get(K.SUITE, K.DEVICE))
    assertEquals("1", m.get(K.SUITE, K.MIRRORED))
    assertEquals(Probe.Value(fresh[0]), m.keychain)
    assertEquals(Probe.Value(fresh[0]), m.marker)
    assertQuiet(m)
  }

  @Test
  fun freshInstallOverwritesKeychainLeftByAnEarlierInstall() {
    //  کلیددانِ iOS با پاک کردنِ برنامه نمی‌رود؛ prefs می‌رود ⇒ همان «نصبِ تازه» ِ اندروید
    val m = Mem().apply { keychain = Probe.Value(other) }
    assertEquals(Verdict.Fresh, DeviceIdentityGuard(m).run())
    assertEquals(Probe.Value(fresh[0]), m.keychain)
    assertNotEquals(other, m.get(K.SUITE, K.DEVICE))
  }

  @Test
  fun sameDeviceDoesNothing() {
    val m = Mem().apply { same(old) }
    val before = m.snapshot()
    assertEquals(Verdict.Same(repair = false), DeviceIdentityGuard(m).run())
    assertEquals(0, m.writes)
    assertEquals(before, m.snapshot())
  }

  @Test
  fun restoredOntoAnotherDeviceRotatesLikeAndroid() {
    val m = Mem().apply { restored() }
    assertEquals(Verdict.Restored(old), DeviceIdentityGuard(m).run())
    assertRotated(m, fresh[0])
    assertTrue(m.logs.single().startsWith("restored ${DeviceIdentity.short(old)}"), m.logs.toString())
    assertQuiet(m)
  }

  @Test
  fun restoredWithAnotherIdentityInKeychainRotates() {
    val m = Mem().apply { restored(); keychain = Probe.Value(other) }
    assertEquals(Verdict.Restored(old), DeviceIdentityGuard(m).run())
    assertRotated(m, fresh[0])
  }

  @Test
  fun upgradeFromOldVersionNeverRotates() {
    //  نسخهٔ پیشین نه آینه داشت نه نشان: نخستین اجرای این نسخه فقط آینه می‌نویسد
    val m = Mem().apply { restored(); prefs.getValue(K.SUITE).remove(K.MIRRORED) }
    val others = m.snapshot().filterKeys { it != K.SUITE && !it.startsWith("#") }
    val sync = HashMap(m.prefs.getValue(K.SUITE))
    assertEquals(Verdict.Upgrade, DeviceIdentityGuard(m).run())
    assertEquals(old, m.get(K.SUITE, K.DEVICE))
    assertEquals(Probe.Value(old), m.keychain)
    assertEquals(Probe.Value(old), m.marker)
    //  هیچ چیزِ دیگری دست نخورد: نه سریِ سند، نه replica، نه مجوز — فقط نشانِ آینه افزوده شد
    assertEquals(others, m.snapshot().filterKeys { it != K.SUITE && !it.startsWith("#") })
    assertEquals<Map<String, String>?>(sync + (K.MIRRORED to "1"), m.prefs[K.SUITE])
    assertQuiet(m)
  }

  @Test
  fun upgradeWithKeychainFromAnEarlierInstallNeverRotates() {
    //  این نسخه ⇐ پاک کردن ⇐ نسخهٔ پیشین (شناسهٔ تازه، بی نشان) ⇐ دوباره این نسخه: کلیددانِ کهنه دلیلِ بازیابی نیست
    val m = Mem().apply { restored(); prefs.getValue(K.SUITE).remove(K.MIRRORED); keychain = Probe.Value(other) }
    assertEquals(Verdict.Upgrade, DeviceIdentityGuard(m).run())
    assertEquals(old, m.get(K.SUITE, K.DEVICE))
    assertEquals(Probe.Value(old), m.keychain)
  }

  @Test
  fun restoreOntoTheSameDeviceKeepsIdentity() {
    //  کلیددانِ «فقط همین دستگاه» با پشتیبان به همان دستگاه برمی‌گردد؛ پرونده (بیرون از پشتیبان) نه
    val m = Mem().apply { restored(); keychain = Probe.Value(old) }
    assertEquals(Verdict.Same(repair = true), DeviceIdentityGuard(m).run())
    assertEquals(old, m.get(K.SUITE, K.DEVICE))
    assertEquals(Probe.Value(old), m.marker)
    assertEquals("A1", m.get("tohid-doc-series", "userA"))
    assertQuiet(m)
  }

  @Test
  fun sameContainerWithLostKeychainKeepsIdentity() {
    //  گروهِ کلیددان عوض شد (شناسهٔ تیم/بسته): پروندهٔ همین ظرف می‌گوید دستگاه همان است
    val m = Mem().apply { restored(); marker = Probe.Value(old) }
    assertEquals(Verdict.Same(repair = true), DeviceIdentityGuard(m).run())
    assertEquals(old, m.get(K.SUITE, K.DEVICE))
    assertEquals(Probe.Value(old), m.keychain)
    assertEquals(old, m.get(K.V1_SUITE, "srv:userA.replica"))
    assertQuiet(m)
  }

  @Test
  fun lockedDeviceNeverDecides() {
    val restored = Mem().apply { restored(); locked = true }
    assertEquals(Verdict.Undecided::class, DeviceIdentityGuard(restored).run()::class)
    assertEquals(0, restored.writes)
    //  پیش از نخستین بازگشایی NSUserDefaults خالی می‌نماید: «نصبِ تازه» هم نه
    val prewarm = Mem().apply { locked = true }
    assertEquals(Verdict.Undecided::class, DeviceIdentityGuard(prewarm).run()::class)
    assertEquals(0, prewarm.writes)
    assertNull(prewarm.get(K.SUITE, K.DEVICE))
  }

  @Test
  fun unreadableKeychainNeverDecidesThenDecidesOnceReadable() {
    val m = Mem().apply { restored(); keychain = Probe.Unavailable("OSStatus -25308") }
    val before = m.snapshot()
    repeat(3) { assertEquals(Verdict.Undecided::class, DeviceIdentityGuard(m).run()::class) }
    assertEquals(0, m.writes)
    assertEquals(before, m.snapshot())
    m.keychain = Probe.Absent
    assertEquals(Verdict.Restored(old), DeviceIdentityGuard(m).run())
    assertRotated(m, fresh[0])
  }

  @Test
  fun unreadableMarkerNeverDecides() {
    val m = Mem().apply { restored(); marker = Probe.Unavailable("EACCES") }
    assertEquals(Verdict.Undecided::class, DeviceIdentityGuard(m).run()::class)
    assertEquals(0, m.writes)
    assertEquals(old, m.get(K.SUITE, K.DEVICE))
  }

  @Test
  fun repeatedStartsAreIdempotent() {
    val m = Mem().apply { restored() }
    DeviceIdentityGuard(m).run()
    val after = m.snapshot()
    repeat(5) {
      m.writes = 0
      assertEquals(Verdict.Same(repair = false), DeviceIdentityGuard(m).run())
      assertEquals(0, m.writes)
    }
    assertEquals(after, m.snapshot())
    assertEquals(fresh.drop(1), m.uids.toList())
  }

  @Test
  fun crashAfterAnyWriteOfARotationEndsConsistent() {
    val total = Mem().apply { restored() }.also { DeviceIdentityGuard(it).run() }.writes
    assertTrue(total >= 8, "نوشتن‌های یک چرخش: $total")
    for (n in 1..total) {
      val m = Mem().apply { restored(); failAt = n }
      assertFails("بسته شدن پیش از نوشتنِ $n") { DeviceIdentityGuard(m).run() }
      m.failAt = -1
      val v = DeviceIdentityGuard(m).run()
      //  ژورنال نوشته شد (n > 1) ⇒ همان شناسهٔ تازهٔ نخست تا ته؛ وگرنه هیچ چیز عوض نشده بود و چرخشِ تازه
      val expect = if (n == 1) fresh[1] else fresh[0]
      assertEquals(if (n == 1) Verdict.Restored(old) else Verdict.Resume(old, fresh[0]), v, "پس از نوشتنِ $n")
      assertRotated(m, expect)
      assertQuiet(m)
    }
  }

  @Test
  fun crashDuringFreshOrUpgradeNeverRotatesOrLosesIdentity() {
    for (n in 1..3) {
      val f = Mem().apply { failAt = n }
      assertFails { DeviceIdentityGuard(f).run() }
      f.failAt = -1
      DeviceIdentityGuard(f).run()
      val uid = f.get(K.SUITE, K.DEVICE)!!
      assertEquals(Probe.Value(uid), f.keychain)
      assertEquals(Probe.Value(uid), f.marker)
      assertQuiet(f)

      val u = Mem().apply { restored(); prefs.getValue(K.SUITE).remove(K.MIRRORED); failAt = n }
      assertFails { DeviceIdentityGuard(u).run() }
      u.failAt = -1
      DeviceIdentityGuard(u).run()
      assertEquals(old, u.get(K.SUITE, K.DEVICE), "به‌روزرسانی، بسته شدن پیش از نوشتنِ $n")
      assertEquals(Probe.Value(old), u.keychain)
      assertEquals("A1", u.get("tohid-doc-series", "userA"))
      assertQuiet(u)
    }
  }

  @Test
  fun remapKeepsAccountSeparation() {
    val to = fresh[0]
    assertEquals(to, DeviceIdentity.remap(old, old, to))
    assertEquals("$to-k3x9", DeviceIdentity.remap("$old-k3x9", old, to))
    assertEquals("$to-k3x9", DeviceIdentity.remap("$to-k3x9", old, to))
    assertEquals(to, DeviceIdentity.remap(to, old, to))
    assertNull(DeviceIdentity.remap(other, old, to))
    assertNull(DeviceIdentity.remap("", old, to))
    //  `from` ِ خالی (ژورنالِ بی مبدأ) ⇒ هیچ چیز «مالِ ما» دیده نمی‌شود جز همان شناسهٔ تازه
    assertNull(DeviceIdentity.remap(old, "", to))
  }

  @Test
  fun hexMatchesAndroidDeviceUidFormat() {
    assertEquals("000fff10", DeviceIdentity.hex(byteArrayOf(0, 15, -1, 16)))
    assertEquals(32, DeviceIdentity.hex(ByteArray(16) { it.toByte() }).length)
  }

  @Test
  fun deviceOnlyStoresAreTheSameAsAndroidBackupExclusions() {
    val xml = listOf("../../android-native", "android-native", "../android-native")
      .map { File(it, "app/src/main/res/xml/backup_rules.xml") }.firstOrNull { it.isFile }?.readText()
      ?: error("backup_rules.xml پیدا نشد (cwd=${File(".").absoluteFile})")
    val rule = Regex("""<exclude\s+domain="sharedpref"\s+path="([^"]+)\.xml"\s*/>""")
    //  `tohid-sync-v1` (DATA-06، #991) را اندروید هم کنار می‌گذارد؛ آیفون آن را نگه می‌دارد و چرخش replicaهایش را به شناسهٔ
    //  تازه می‌برد ([K.V1_SUITE])، cursor و سایه‌اش را حصارِ کدِ مشترک (`V1Replica.fence`) — پس «فقط همین دستگاه» نیست ولی در فهرست هست
    for (section in listOf("cloud-backup", "device-transfer")) {
      val body = xml.substringAfter("<$section>").substringBefore("</$section>")
      //  `K.V1_SUITE` = `tohid-sync-v1` (replica و cursor ِ هر حساب) را اندروید از پشتیبان بیرون می‌گذارد (DATA-06، `claude/android-restore-id`)؛
      //  روی آیفون پاک نمی‌شود، چون حصارِ مشترکِ `V1ReplicaAndroid` + `core/sync/RestoredDevice` (در ios-exclude نیست) همان حال را
      //  به `device_uid` ِ امروز می‌بندد و پس از بازیابی پیش از هر Pull «بازسازی از سرور» می‌کند — همان تضمین، بی پاک کردن
      assertEquals((K.DEVICE_ONLY + K.V1_SUITE).toSet(), rule.findAll(body).map { it.groupValues[1] }.toSet(), section)
    }
  }

  // ── کمک ─────────────────────────────────────────────────────────────

  /** پس از چرخش به [uid]: همان چیزی که پشتیبانِ اندروید نمی‌برد رفته، بقیه دست‌نخورده */
  private fun assertRotated(m: Mem, uid: String) {
    assertEquals(uid, m.get(K.SUITE, K.DEVICE))
    assertNotEquals(old, uid)
    assertEquals(Probe.Value(uid), m.keychain)
    assertEquals(Probe.Value(uid), m.marker)
    assertEquals<Map<String, String>?>(
      mapOf(K.DEVICE to uid, K.MIRRORED to "1", K.REPLICA_OWNER to "userA", "shadow" to "{legacy}", "rev" to "12", "last_sync" to "99"),
      m.prefs[K.SUITE],
    )
    assertEquals<Map<String, String>?>(
      mapOf(
        "account_id" to "userA",
        "srv:userA.replica" to uid,
        "srv:userB.replica" to "$uid-k3x9",
        "srv:userA.cursor" to "120",
        "srv:userA.shadow" to "{\"products\":[]}",
      ),
      m.prefs[K.V1_SUITE],
    )
    for (s in K.DEVICE_ONLY - K.SUITE) assertTrue(m.prefs[s].isNullOrEmpty(), s)
    assertEquals<Map<String, String>?>(mapOf("receipt_footer" to "سپاس"), m.prefs["tohid-receipt"])
  }

  /** اجرای بعدی هیچ نمی‌نویسد */
  private fun assertQuiet(m: Mem) {
    m.writes = 0
    assertEquals(Verdict.Same(repair = false), DeviceIdentityGuard(m).run())
    assertEquals(0, m.writes)
  }

  private fun Mem.same(uid: String) {
    prefs[K.SUITE] = hashMapOf(K.DEVICE to uid, K.MIRRORED to "1")
    keychain = Probe.Value(uid)
    marker = Probe.Value(uid)
  }

  /** آیفونِ تازه از پشتیبانِ آیفونِ دیگری که همین نسخه را داشت: همهٔ prefs آمده، کلیددان و پرونده نه */
  private fun Mem.restored() {
    prefs[K.SUITE] = hashMapOf(
      K.DEVICE to old, K.MIRRORED to "1", K.REPLICA_OWNER to "userA",
      "license" to "{signed}", "license_denied" to "0", "offline_code" to "X-1", "account_name" to "دکان", "clock_seen" to "5",
      "shadow" to "{legacy}", "rev" to "12", "last_sync" to "99",
    )
    prefs[K.V1_SUITE] = hashMapOf(
      "account_id" to "userA",
      "srv:userA.replica" to old,
      "srv:userB.replica" to "$old-k3x9",
      "srv:userC.replica" to other,
      "srv:userA.cursor" to "120",
      "srv:userA.shadow" to "{\"products\":[]}",
    )
    prefs["tohid-session"] = hashMapOf("access_token" to "t")
    prefs["tohid-staff-pins"] = hashMapOf("pin:1" to "h")
    prefs["tohid-doc-series"] = hashMapOf("userA" to "A1")
    prefs["tohid-receipt"] = hashMapOf("receipt_footer" to "سپاس")
    keychain = Probe.Absent
    marker = Probe.Absent
  }

  /** انبارِ درونِ حافظه؛ [failAt] ⇒ «بسته شدن» درست پیش از همان نوشتن (نوشتن نمی‌نشیند) */
  private inner class Mem : DeviceIdentityGuard.Io {
    var locked = false
    val prefs = HashMap<String, HashMap<String, String>>()
    var keychain: Probe = Probe.Absent
    var marker: Probe = Probe.Absent
    var failAt = -1
    var writes = 0
    val uids = ArrayDeque(fresh)
    val logs = ArrayList<String>()

    private fun write() {
      writes++
      if (writes == failAt) throw IllegalStateException("بسته شد پیش از نوشتنِ $writes")
    }

    fun snapshot(): Map<String, Any> =
      prefs.mapValues { (_, v) -> HashMap(v) as Any } + mapOf("#keychain" to keychain, "#marker" to marker)

    override fun protectedData() = !locked
    override fun get(suite: String, key: String): String? = prefs[suite]?.get(key)
    override fun keys(suite: String): Set<String> = prefs[suite]?.keys?.toSet().orEmpty()
    override fun edit(suite: String, changes: Map<String, String?>) {
      write()
      val p = prefs.getOrPut(suite) { HashMap() }
      for ((k, v) in changes) if (v == null) p.remove(k) else p[k] = v
    }
    override fun clear(suite: String) {
      write()
      prefs.remove(suite)
    }
    override fun keychain(): Probe = keychain
    override fun putKeychain(uid: String) {
      write()
      keychain = Probe.Value(uid)
    }
    override fun marker(): Probe = marker
    override fun putMarker(uid: String) {
      write()
      marker = Probe.Value(uid)
    }
    override fun newUid(): String = uids.removeFirst()
    override fun log(line: String) {
      logs += line
    }
  }
}
