package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  DATA-06 (§۲۹.۸): «restore روی دستگاهِ دوم سری/هویتِ اسناد را ایمن مدیریت کند».
 *
 *  هویتِ دستگاه (`tohid-sync` ⇐ `SyncStore.deviceUid`) با پشتیبانِ اندروید جابه‌جا نمی‌شود، پس گوشیِ
 *  دومی که از پشتیبانِ گوشیِ اول بالا بیاید شناسهٔ تازه دارد. ولی سریِ سند (`tohid-doc-series`، F0-03)
 *  تا ۲.۱۴.۴ همراهِ پشتیبان می‌رفت: گوشیِ دوم همان «A1» و همان `nextInvoiceNo` ِ دفتر را می‌گرفت و
 *  هر دو گوشی «A1-155» می‌ساختند. این‌جا هر سه راهِ پشتیبان (اندروید ۱۲+، پیش از ۱۲، و
 *  `TohidBackupAgent` با رمزِ دفتر) سنجیده می‌شوند. سرور برای شناسهٔ تازه سریِ تازه می‌دهد
 *  (`server/test/doc-series.test.js`).
 */
class DeviceOnlyPrefsTest {

  private fun file(rel: String): String =
    listOf("src/main", "../android-native/app/src/main", "android-native/app/src/main")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  /**
   *  پرونده‌های SharedPreferences که مالِ همین دستگاه‌اند و هرگز با پشتیبانِ اندروید جابه‌جا نمی‌شوند.
   *  `tohid-sync-v1.xml` (۱۴۰۵/۰۸/۱۸): replica و cursor ِ هر حساب (#991) — با آن گوشیِ دوم همان `device_id` ِ گوشیِ اول را
   *  داشت؛ گوشیِ بازیابی‌شده شناسهٔ خودش را می‌گیرد و پیش از Pull از سرور بازسازی می‌شود (`RestoredDeviceDesktopTest`).
   */
  private val deviceOnly = listOf(
    "tohid-sync.xml", "tohid-session.xml", "tohid-session-secure.xml", "tohid-staff-pins.xml", "tohid-doc-series.xml",
    "tohid-sync-v1.xml",
    //  OPS-02: «پشتیبانِ دستی و پوشه هم رمزدار» و شکستِ پشتیبانِ پوشه (`BackupDevice`) — کلیدِ عبارت مالِ همین دستگاه است؛
    //  پرچمِ روشن روی گوشیِ تازهٔ بی‌کلید هر پشتیبانِ پوشه را بی‌صدا می‌شکست
    "tohid-backup-device.xml",
  )

  private fun excluded(xml: String): Set<String> =
    Regex("""<exclude\s+domain="sharedpref"\s+path="([^"]+)"\s*/>""").findAll(xml).map { it.groupValues[1] }.toSet()

  @Test fun `سریِ سند و هویتِ دستگاه در هیچ راهِ پشتیبانِ اندروید نیستند`() {
    val rules = file("res/xml/backup_rules.xml")
    val cloud = rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
    val transfer = rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
    val legacy = file("res/xml/backup_rules_legacy.xml")
    val agentSrc = file("java/ir/vil3ntec/tohid/data/TohidBackupAgent.kt")
    val agentList = Regex("""val EXCLUDED_PREFS = setOf\(([^)]*)\)""").find(agentSrc)?.groupValues?.get(1)
      ?: error("EXCLUDED_PREFS در TohidBackupAgent پیدا نشد")
    val agent = Regex("\"([^\"]+)\"").findAll(agentList).map { it.groupValues[1] }.toSet()
    for ((where, set) in listOf(
      "cloud-backup" to excluded(cloud), "device-transfer" to excluded(transfer),
      "backup_rules_legacy" to excluded(legacy), "TohidBackupAgent" to agent,
    )) {
      for (p in deviceOnly) assertTrue("$where: $p باید بیرون بماند", p in set)
    }
  }

  @Test fun `پرچمِ رمزِ پشتیبانِ دستی در همان پرونده‌ای است که بیرون می‌ماند`() {
    assertEquals("tohid-backup-device", BackupDevice.PREFS)
    val seal = file("java/ir/vil3ntec/tohid/data/BackupSealAndroid.kt")
    assertTrue("BackupSeal پرچم را از BackupDevice می‌خواند", "BackupDevice.prefs(context)" in seal)
  }

  @Test fun `نامِ پروندهٔ سری همان است که بیرون می‌ماند`() {
    val src = file("java/ir/vil3ntec/tohid/sync/DocSeries.kt")
    assertEquals("tohid-doc-series", Regex("""private const val PREFS = "([^"]+)"""").find(src)?.groupValues?.get(1))
    //  همان نام در حصارِ گوشیِ بازیابی‌شده (سریِ کهنه کنار) — `V1ReplicaAndroid.kt`
    val replica = file("java/ir/vil3ntec/tohid/sync/v1/V1ReplicaAndroid.kt")
    assertEquals("tohid-doc-series", Regex("""const val DOC_SERIES = "([^"]+)"""").find(replica)?.groupValues?.get(1))
  }

  /**
   *  پوشه‌های `filesDir` که مالِ همین دستگاه‌اند: سایهٔ v1 در فایلِ خودش (`sync-shadow/`) و حالِ Sync v3 (`sync-v3/`، DATA-06:
   *  cursor ِ گوشیِ پیشین روی گوشیِ دوم opهای خودِ آن را دوباره می‌نشاند — `sync/v3/V3State.kt`). هر سه راه و عاملِ رمزِ دفتر.
   */
  @Test fun `پوشهٔ حالِ Sync v3 و سایهٔ v1 در هیچ راهِ پشتیبانِ اندروید نیستند`() {
    val dirs = setOf("sync-shadow", "sync-v3")
    fun files(xml: String) = Regex("""<exclude\s+domain="file"\s+path="([^"]+)"\s*/>""").findAll(xml).map { it.groupValues[1] }.toSet()
    val rules = file("res/xml/backup_rules.xml")
    val agentSrc = file("java/ir/vil3ntec/tohid/data/TohidBackupAgent.kt")
    val agent = Regex("\"([^\"]+)\"").findAll(
      Regex("""val EXCLUDED_DIRS = setOf\(([^)]*)\)""").find(agentSrc)?.groupValues?.get(1) ?: error("EXCLUDED_DIRS در TohidBackupAgent پیدا نشد"),
    ).map { it.groupValues[1] }.toSet()
    for ((where, set) in listOf(
      "cloud-backup" to files(rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")),
      "device-transfer" to files(rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")),
      "backup_rules_legacy" to files(file("res/xml/backup_rules_legacy.xml")), "TohidBackupAgent" to agent,
    )) assertEquals(where, dirs, set)
    //  همان نام در کد و در قاعدهٔ رمزِ دفتر
    val v3 = file("java/ir/vil3ntec/tohid/sync/v3/V3StateDirAndroid.kt")
    assertEquals("sync-v3", Regex("""const val DIR = "([^"]+)"""").find(v3)?.groupValues?.get(1))
    for (n in listOf("sync-v3", "sync-v3/u1/state.json", "sync-shadow/sync-shadow-u1-1.dat")) {
      assertTrue(n, ir.vil3ntec.tohid.core.atrest.AtRestScope.skipInDeviceBackup(n))
    }
  }

  @Test fun `نامِ پروندهٔ حالِ همگام‌سازی همان است که بیرون می‌ماند — و رمزِ دفتر هم همان را کنار می‌گذارد`() {
    val src = file("java/ir/vil3ntec/tohid/sync/v1/SyncStateV1.kt")
    assertTrue(Regex("""getSharedPreferences\("tohid-sync-v1"""").containsMatchIn(src))
    assertTrue("tohid-sync-v1.xml" in ir.vil3ntec.tohid.core.atrest.AtRestScope.SYNC_PREFS)
  }
}
