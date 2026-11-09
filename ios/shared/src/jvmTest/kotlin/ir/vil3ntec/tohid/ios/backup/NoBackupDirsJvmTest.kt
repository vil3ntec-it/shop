package ir.vil3ntec.tohid.ios.backup

import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  پوشه‌های بیرون از پشتیبانِ آیفون (‎commonMain/…/backup/NoBackupDirs.kt‎): فهرست همان است که اندروید کنار می‌گذارد (هر دو
 *  بخشِ `backup_rules.xml`، `backup_rules_legacy.xml`، `TohidBackupAgent.EXCLUDED_DIRS` و نامِ پوشه در کدِ مشترک)، و کمکِ
 *  `NSURLIsExcludedFromBackupKey` برای هر کدام صدا زده می‌شود. همتای شبیه‌سازش (ویژگی روی پوشهٔ واقعی): ‎iosTest/…/NoBackupDirsIosTest‎.
 */
class NoBackupDirsJvmTest {

  @Test
  fun listIsTheSameAsAndroidExcludedDirectories() {
    val res = android("app/src/main/res/xml")
    val rules = File(res, "backup_rules.xml").readText()
    val dirRule = Regex("""<exclude\s+domain="file"\s+path="([^"]+?)/?"\s*/>""")
    fun dirs(xml: String) = dirRule.findAll(xml).map { it.groupValues[1] }.toSet()
    val src = android("app/src/main/java/ir/vil3ntec/tohid")
    val agent = File(src, "data/TohidBackupAgent.kt").readText()
    val agentDirs = Regex("""val EXCLUDED_DIRS = setOf\(([^)]*)\)""").find(agent)?.groupValues?.get(1)
      ?: error("EXCLUDED_DIRS در TohidBackupAgent پیدا نشد")
    //  همان نامی که کدِ مشترک مسیر را با آن می‌سازد (`ShadowStore.DIR`، `V3StateDir.DIR`) — روی آیفون هم همین کد می‌دود
    val dirConst = Regex("""const val DIR = "([^"]+)"""")
    val code = listOf("sync/v1/ShadowStoreAndroid.kt", "sync/v3/V3StateDirAndroid.kt")
      .map { dirConst.find(File(src, it).readText())?.groupValues?.get(1) ?: error("DIR در $it نیست") }.toSet()
    val expected = NoBackupDirs.NAMES.toSet()
    assertEquals(NoBackupDirs.NAMES.size, expected.size, "نامِ تکراری")
    for ((where, got) in listOf(
      "cloud-backup" to dirs(rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")),
      "device-transfer" to dirs(rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")),
      "backup_rules_legacy" to dirs(File(res, "backup_rules_legacy.xml").readText()),
      "TohidBackupAgent.EXCLUDED_DIRS" to Regex("\"([^\"]+)\"").findAll(agentDirs).map { it.groupValues[1] }.toSet(),
      "ShadowStore.DIR + V3StateDir.DIR" to code,
    )) assertEquals(expected, got, where)
  }

  @Test
  fun ledgerAndQueuesStayInBackup() {
    //  ویژگی مالِ همان قلم است: فقط یک بخشِ مسیر، و نه صفِ v3 (`sync-v3-<کلید>/`)، نه `oplog-*` ِ v1، نه خودِ `filesDir`
    val v3 = File(android("app/src/main/java/ir/vil3ntec/tohid"), "sync/v3/V3StateDirAndroid.kt").readText()
    val queuePrefix = Regex("""const val QUEUE_PREFIX = "([^"]+)"""").find(v3)?.groupValues?.get(1) ?: error("QUEUE_PREFIX نیست")
    assertEquals("sync-v3-", queuePrefix)
    for (n in NoBackupDirs.NAMES) {
      assertTrue(n.isNotBlank() && '/' !in n && n != "." && n != "..", n)
      assertFalse(n.startsWith(queuePrefix) || n.startsWith("oplog-"), n)
    }
    val rules = File(android("app/src/main/res/xml"), "backup_rules.xml").readText()
    assertEquals(2, Regex("""<include\s+domain="file"\s+path="\."\s*/>""").findAll(rules).count(), "دفتر و صف با پشتیبان")
  }

  @Test
  fun createsAndExcludesEachDirectory() {
    val io = FakeIo()
    assertEquals(emptyList<String>(), NoBackupDirs.apply(io))
    assertEquals(NoBackupDirs.NAMES.flatMap { listOf("mkdir $it", "excluded $it", "exclude $it") }, io.calls)
    assertEquals(NoBackupDirs.NAMES.toSet(), io.marked)
  }

  @Test
  fun existingInstallAlreadyExcludedWritesNothing() {
    val io = FakeIo().apply { marked += NoBackupDirs.NAMES }
    assertEquals(emptyList<String>(), NoBackupDirs.apply(io))
    assertEquals(NoBackupDirs.NAMES.flatMap { listOf("mkdir $it", "excluded $it") }, io.calls)
  }

  @Test
  fun existingInstallWithOneDirectoryNotYetExcluded() {
    //  نصبِ پیشین: `sync-shadow/` (پرچمِ فایلِ سایه) شاید هنوز نباشد، `sync-v3/` هست و بی ویژگی
    val io = FakeIo().apply { marked += NoBackupDirs.NAMES.first() }
    assertEquals(emptyList<String>(), NoBackupDirs.apply(io))
    assertEquals(listOf("exclude ${NoBackupDirs.NAMES.last()}"), io.calls.filter { it.startsWith("exclude ") })
    assertEquals(NoBackupDirs.NAMES.toSet(), io.marked)
  }

  @Test
  fun oneFailureDoesNotStopTheOthers() {
    val (a, b) = NoBackupDirs.NAMES
    for (fail in listOf("mkdir", "exclude", "throw")) {
      val io = FakeIo(failOn = a, how = fail)
      assertEquals(listOf(a), NoBackupDirs.apply(io), fail)
      assertTrue("exclude $b" in io.calls, fail)
      assertEquals(setOf(b), io.marked, fail)
    }
    //  هیچ‌کدام نشد ⇒ همه برمی‌گردند (لایهٔ iOS بارِ بعد دوباره می‌کوشد)
    val none = object : NoBackupDirs.Io {
      override fun mkdir(name: String) = true
      override fun excluded(name: String) = false
      override fun exclude(name: String) = false
    }
    assertEquals(NoBackupDirs.NAMES, NoBackupDirs.apply(none))
  }

  @Test
  fun runTwiceIsQuiet() {
    val io = FakeIo()
    NoBackupDirs.apply(io)
    io.calls.clear()
    assertEquals(emptyList<String>(), NoBackupDirs.apply(io))
    assertTrue(io.calls.none { it.startsWith("exclude ") })
  }

  // ── کمک ─────────────────────────────────────────────────────────────

  private class FakeIo(private val failOn: String? = null, private val how: String = "") : NoBackupDirs.Io {
    val calls = mutableListOf<String>()
    val marked = mutableSetOf<String>()

    override fun mkdir(name: String): Boolean {
      calls += "mkdir $name"
      if (name == failOn && how == "throw") throw IOException("دیسک")
      return !(name == failOn && how == "mkdir")
    }

    override fun excluded(name: String): Boolean {
      calls += "excluded $name"
      return name in marked
    }

    override fun exclude(name: String): Boolean {
      calls += "exclude $name"
      if (name == failOn && how == "exclude") return false
      marked += name
      return true
    }
  }

  private fun android(rel: String): File =
    listOf("../../android-native", "android-native", "../android-native").map { File(it, rel) }.firstOrNull { it.exists() }
      ?: error("android-native/$rel پیدا نشد (cwd=${File(".").absoluteFile})")
}
