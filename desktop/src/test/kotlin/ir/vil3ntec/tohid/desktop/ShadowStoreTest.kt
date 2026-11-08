package ir.vil3ntec.tohid.desktop

import android.content.Context
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.ShadowFile
import ir.vil3ntec.tohid.sync.v1.ShadowFileFlag
import ir.vil3ntec.tohid.sync.v1.ShadowStore
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.desktop.perf.BigLedger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 *  PERF-LEDGER — سایه و cursor ِ Sync v1 در فایلِ خودشان (`ShadowStore`، پرچمِ `shop.sync_shadow_file`)، روی دیسکِ
 *  واقعیِ کامپیوتر و همان کدِ گوشی و آیفون (`docs/PERF-LEDGER-fa.md` §۱۰، `docs/DATA-SAFETY-fa.md`).
 *
 *   • قالب: یک فایل، cursor و سایه با دو اثرِ انگشت؛ فایلِ نیمه یا خراب ⇒ `.prev` ⇒ نسخهٔ prefs؛ هیچ‌کدام ⇒ پرتاب، هرگز «خالی».
 *   • جفت: «بسته شدن» در هر گامِ نوشتن (کناریِ نیمه، پس از جابه‌جایی، پس از prefs) یا جفتِ پیشین است یا جفتِ تازه.
 *   • کوچ از prefs: یک‌باره، با بسته شدن در هر گام، و نسخهٔ prefs تا نخستین نوشتنِ موفقِ فایل.
 *   • روشن ⇐ خاموش ⇐ روشن بی گم شدن؛ برگشت به ساختِ کهنه‌تر (نوشتنِ prefs پس از ما) دیده می‌شود.
 *   • رشتهٔ صفحه: نوار و «ردِ حال» فایل را نمی‌خوانند؛ خواندنِ فایل روی آن شمرده می‌شود.
 *  هم‌ارزیِ opها و متنِ سایه با راهِ prefs: `perf/LedgerPerfEquivalenceTest`.
 */
class ShadowStoreTest {

  private val ctx = DesktopContext
  private val n = System.nanoTime().toString(36)
  private val keys = ArrayList<String>()
  private val prefs get() = ctx.getSharedPreferences("tohid-sync-v1", Context.MODE_PRIVATE)

  private fun key(tag: String) = "shadowt$tag$n".also { keys += it }
  private fun state(k: String) = SyncStateV1(ctx, k)
  private fun marker(k: String) = prefs.getString("$k.${ShadowStore.MARKER}", null)
  private fun mainOf(k: String) = File(ctx.filesDir, marker(k)!!.split(';')[2])
  private fun files(k: String) = ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("${ShadowStore.FILE_PREFIX}${OpQueue.fileKey(k)}-") }
  private fun seedPrefs(k: String, shadow: String, cursor: Long) =
    assertTrue(prefs.edit().putString("$k.shadow", shadow).putLong("$k.cursor", cursor).commit())

  /** «شروعِ دوبارهٔ برنامه» با پرچمِ [on]: همهٔ حافظه‌های سایه از نو از دیسک */
  private fun restart(on: Boolean) {
    ShadowFileFlag.override = on
    ShadowStore.restartForTest()
  }

  private fun crashAt(step: ShadowStore.Step) {
    ShadowStore.crash = { if (it == step) throw ShadowStore.CrashForTest(it) }
  }

  @After fun after() {
    ShadowStore.crash = null
    ShadowStore.mainThread = null
    ShadowFileFlag.override = null
    val e = prefs.edit()
    for (k in keys) prefs.all.keys.filter { it.startsWith("$k.") }.forEach { e.remove(it) }
    e.commit()
    keys.forEach { k -> files(k).forEach { it.delete() } }
    ShadowStore.restartForTest()
  }

  private fun shadowOf(i: Int) = """{"sales":[{"id":"s$i","note":"فروش \"$i\"\\n"}],"stamp":$i}"""

  /* ------------------------------ قالب ------------------------------ */

  @Test fun `قالب — یک نوشتن، همان بایت‌ها و همان اثرِ انگشتِ ژورنال؛ جفتِ UTF-16 میانِ دو تکه دو نیم نمی‌شود`() {
    val dir = Files.createTempDirectory("shadow-fmt").toFile()
    try {
      val f = ShadowFile(File(dir, "s.dat"))
      //  ۶۴K−۱ نویسه و بعد یک جفتِ UTF-16 ⇒ مرزِ تکه درست وسطِ جفت می‌افتاد
      val text = "x".repeat(64 * 1024 - 1) + "😀" + "سایهٔ «دکان» \"\\\n" + "y".repeat(70_000) + "😀"
      val sha = f.write(text, 42, 7)
      assertEquals(SyncCommit.sha(text), sha)
      val p = f.readMain()!!
      assertEquals(text, p.shadow)
      assertEquals(42L, p.cursor)
      assertEquals(7L, p.gen)
      val bytes = f.file.readBytes()
      val head = "TOHID-SYNC-SHADOW 1\n".toByteArray()
      val body = text.toByteArray(Charsets.UTF_8)
      assertTrue(bytes.copyOfRange(0, head.size).contentEquals(head))
      assertTrue(bytes.copyOfRange(head.size, head.size + body.size).contentEquals(body))
      //  جفتِ بی‌جفت همان `?` ِ `toByteArray` می‌شود — همان اثرِ انگشتِ `SyncCommit.sha` (و همان رفتارِ prefs ِ دیسک)
      val odd = "a\uD800b 😀 \uDC00"
      assertEquals(SyncCommit.sha(odd), f.write(odd, 1, 8))
      assertEquals(String(odd.toByteArray(Charsets.UTF_8), Charsets.UTF_8), f.readMain()!!.shadow)
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun `فایلِ نیمه یا خراب — نسخهٔ پیشین؛ هر دو خراب ⇒ پرتاب، هرگز سایهٔ خالی`() {
    val dir = Files.createTempDirectory("shadow-torn").toFile()
    try {
      val f = ShadowFile(File(dir, "s.dat"))
      assertNull(f.load())
      f.write(shadowOf(1), 1, 1)
      f.write(shadowOf(2), 2, 2)
      //  نیمه (سیستمی که `fsync` را جدی نمی‌گیرد): نصفِ فایل
      val full = f.file.readBytes()
      f.file.writeBytes(full.copyOf(full.size / 2))
      f.load()!!.let { assertTrue(it.fromPrevious); assertEquals(shadowOf(1), it.pair.shadow); assertEquals(1L, it.pair.cursor) }
      //  یک بایتِ سایه عوض شد ⇒ اثرِ انگشتِ سایه
      f.file.writeBytes(full)
      val flipped = full.copyOf().also { it[25] = (it[25] + 1).toByte() }
      f.file.writeBytes(flipped)
      assertThrows(ShadowFile.Corrupt::class.java) { f.readMain() }
      assertTrue(f.load()!!.fromPrevious)
      //  cursor ِ سطرِ پایان عوض شد (اثرِ انگشتِ سایه هنوز درست) ⇒ اثرِ انگشتِ دوم
      f.file.writeBytes(String(full, Charsets.UTF_8).replace("\"cursor\":2,", "\"cursor\":3,").toByteArray(Charsets.UTF_8))
      assertThrows(ShadowFile.Corrupt::class.java) { f.readMain() }
      //  کناریِ نیمه (بسته شدن پیش از جابه‌جایی) هیچ اثری ندارد
      f.file.writeBytes(full)
      File(dir, "s.dat.tmp").writeBytes(full.copyOf(10))
      assertEquals(shadowOf(2), f.readMain()!!.shadow)
      //  هر دو خراب
      f.file.writeBytes(ByteArray(0))
      f.previous.writeText("{}")
      assertThrows(IOException::class.java) { f.load() }
    } finally {
      dir.deleteRecursively()
    }
  }

  /* ------------------------------ جفتِ سایه و cursor ------------------------------ */

  @Test fun `جفت — بسته شدن در هر گامِ نوشتن یا جفتِ پیشین است یا جفتِ تازه، هرگز درهم`() {
    val k = key("pair")
    restart(on = true)
    assertTrue(state(k).commitShadow(shadowOf(1), 1))
    assertTrue(state(k).commitShadow(shadowOf(2), 2))
    assertTrue(marker(k)!!.startsWith("v1;file;"))

    //  ۱) پیش از جابه‌جایی: کناریِ نیمه کنارِ اصل
    File(mainOf(k).path + ".tmp").writeText("TOHID-SYNC-SHADOW 1\n{\"sales\":[")
    restart(on = true)
    assertEquals(shadowOf(2) to 2L, state(k).shadow to state(k).cursor)

    //  ۲) پس از جابه‌جایی، پیش از prefs (آینهٔ cursor عقب می‌ماند): جفتِ تازه، کامل
    crashAt(ShadowStore.Step.COMMIT_WRITTEN)
    assertThrows(ShadowStore.CrashForTest::class.java) { state(k).commitShadow(shadowOf(3), 3) }
    ShadowStore.crash = null
    restart(on = true)
    assertEquals(2L, prefs.getLong("$k.cursor", 0))
    assertEquals(shadowOf(3) to 3L, state(k).shadow to state(k).cursor)

    //  ۳) فقط cursor (pull ِ بی op): همان سایه، cursor ِ تازه — باز یک نوشتن
    state(k).cursor = 4
    restart(on = true)
    assertEquals(shadowOf(3) to 4L, state(k).shadow to state(k).cursor)
    assertEquals(4L, prefs.getLong("$k.cursor", 0))
    //  cursor ِ همان ⇒ هیچ نوشتنی
    val at = mainOf(k).lastModified()
    state(k).cursor = 4
    assertEquals(at, mainOf(k).lastModified())
  }

  @Test fun `ژورنال — ثبت با بسته شدن پس از نوشتنِ فایل، پس از بازیابی هیچ opِ تازه‌ای برای همان تغییر نمی‌سازد`() {
    val k = key("journal")
    restart(on = true)
    val dir = Files.createTempDirectory("shadow-journal").toFile()
    val queue = ArrayList<SyncOp>()
    fun commit() = SyncCommit(SyncCommit.Journal(File(dir, "j.json")), object : SyncCommit.Parts {
      override fun shadow(): String = state(k).shadow
      override fun commit(shadow: String, cursor: Long?) { if (!state(k).commitShadow(shadow, cursor)) throw IOException("x") }
      override fun queued(): Set<String> = queue.mapTo(HashSet()) { it.opId }
      override fun push(ops: List<SyncOp>) { queue += ops }
    })
    try {
      val base = BigLedger.make(BigLedger.SMOKE)
      assertEquals(0, commit().record(base))
      val sold = BigLedger.sell(base, 1)
      crashAt(ShadowStore.Step.COMMIT_WRITTEN)
      assertThrows(ShadowStore.CrashForTest::class.java) { commit().record(sold) }
      ShadowStore.crash = null
      val made = queue.size
      assertTrue(made > 0)
      restart(on = true)
      val c = commit()
      c.recover(sold)
      assertFalse(c.pending())
      assertEquals("همان تغییر دوباره op نمی‌شود", 0, c.record(sold))
      assertEquals(made, queue.size)
    } finally {
      dir.deleteRecursively()
    }
  }

  /* ------------------------------ خراب ⇐ .prev ⇐ prefs ------------------------------ */

  @Test fun `اصلِ خراب ⇒ نسخهٔ پیشین با نشانِ هم‌ترازی؛ اصل کنار می‌رود و نسخهٔ پیشین با نوشتنِ بعد رونویسی نمی‌شود`() {
    val k = key("prev")
    restart(on = true)
    assertTrue(state(k).commitShadow(shadowOf(1), 5))
    assertTrue(state(k).commitShadow(shadowOf(2), 6))
    val main = mainOf(k)
    main.writeText("خراب")
    restart(on = true)
    assertFalse(prefs.getBoolean("$k.recoverArchive", false))
    //  نخستین سنجشِ `recoverArchive` (پیش از هر تفاضل) خودش بار می‌کند و نشان می‌گذارد
    assertTrue(state(k).recoverArchive)
    assertEquals(shadowOf(1) to 5L, state(k).shadow to state(k).cursor)
    assertTrue(ctx.filesDir.listFiles().orEmpty().any { it.name.startsWith(main.name + ".corrupt-") })
    //  هم‌ترازی (`recoverArchived` ⇐ `commit.rebuild`) جفتِ تازه را می‌نشاند؛ `.prev` همان نسخهٔ سالمِ پیشین می‌ماند
    assertTrue(state(k).commitShadow(shadowOf(3), 9))
    assertEquals(shadowOf(1), ShadowFile(File(main.path + ".prev")).readMain()!!.shadow)
    restart(on = true)
    assertEquals(shadowOf(3) to 9L, state(k).shadow to state(k).cursor)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith(main.name + ".corrupt-") }.forEach { it.delete() }
  }

  @Test fun `هیچ نسخهٔ سالمی نیست ⇒ پرتاب (نه سایهٔ خالی)؛ نوار و ردِ حال نمی‌افتند`() {
    val k = key("none")
    restart(on = true)
    assertTrue(state(k).commitShadow(shadowOf(1), 5))
    assertTrue(state(k).commitShadow(shadowOf(2), 6))
    val main = mainOf(k)
    main.writeText("")
    File(main.path + ".prev").writeText("TOHID")
    restart(on = true)
    assertThrows(IOException::class.java) { state(k).shadow }
    assertThrows(IOException::class.java) { state(k).cursor }
    assertThrows(IOException::class.java) { state(k).commitShadow(shadowOf(9), 9) }
    assertEquals(6L, state(k).cursorSeen)
    assertTrue(state(k).hasSyncState())
    //  هیچ فایلی پاک نشد
    assertTrue(main.isFile && File(main.path + ".prev").isFile)
  }

  @Test fun `فایلِ کوچ خراب و نسخهٔ پیشین نیست ⇒ نسخهٔ prefs ِ کوچ، با نشانِ هم‌ترازی و کوچِ دوباره`() {
    val k = key("copy")
    seedPrefs(k, shadowOf(1), 11)
    restart(on = true)
    assertEquals(shadowOf(1), state(k).shadow)
    val first = mainOf(k)
    assertTrue(marker(k)!!.startsWith("v1;copy;"))
    first.writeBytes(first.readBytes().copyOf(30))
    restart(on = true)
    assertTrue(state(k).recoverArchive)
    assertEquals(shadowOf(1) to 11L, state(k).shadow to state(k).cursor)
    assertTrue(marker(k)!!.startsWith("v1;copy;"))
    assertNotEquals(first.name, mainOf(k).name)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith(first.name) }.forEach { it.delete() }
  }

  /* ------------------------------ کوچ ------------------------------ */

  @Test fun `کوچ از prefs — یک‌باره؛ نسخهٔ prefs تا نخستین نوشتنِ موفق، بعد برداشته و XML کوچک`() {
    val k = key("mig")
    seedPrefs(k, shadowOf(1), 9)
    restart(on = true)
    assertEquals(shadowOf(1) to 9L, state(k).shadow to state(k).cursor)
    val m = marker(k)!!
    assertTrue(m, m.startsWith("v1;copy;"))
    assertEquals("نسخهٔ prefs هنوز هست", shadowOf(1), prefs.getString("$k.shadow", null))
    restart(on = true)
    assertEquals("بارِ دوم کوچی نیست", m, marker(k))
    assertEquals(shadowOf(1) to 9L, state(k).shadow to state(k).cursor)
    assertEquals(1, files(k).count { it.name.endsWith(".dat") })

    assertTrue(state(k).commitShadow(shadowOf(2), null))
    assertTrue(marker(k)!!.startsWith("v1;file;"))
    assertNull("نسخهٔ prefs پس از نخستین نوشتنِ موفق رفت", prefs.getString("$k.shadow", null))
    assertEquals(9L, prefs.getLong("$k.cursor", 0))
    restart(on = true)
    assertEquals(shadowOf(2) to 9L, state(k).shadow to state(k).cursor)
  }

  @Test fun `کوچ — بسته شدن در هر گام (پس از نوشتن، پس از سنجش، پس از نشان) حالِ خوانا و همان جفت`() {
    for (step in listOf(ShadowStore.Step.MIGRATE_WRITTEN, ShadowStore.Step.MIGRATE_VERIFIED, ShadowStore.Step.MIGRATED)) {
      val k = key("c${step.ordinal}")
      seedPrefs(k, shadowOf(1), 9)
      restart(on = true)
      crashAt(step)
      assertThrows(step.name, ShadowStore.CrashForTest::class.java) { state(k).shadow }
      ShadowStore.crash = null
      assertEquals(step.name, step == ShadowStore.Step.MIGRATED, marker(k) != null)
      restart(on = true)
      assertEquals(step.name, shadowOf(1) to 9L, state(k).shadow to state(k).cursor)
      assertNotNull(step.name, marker(k))
      assertTrue(step.name, mainOf(k).isFile)
      assertTrue(step.name, state(k).commitShadow(shadowOf(2), 10))
      restart(on = true)
      assertEquals(step.name, shadowOf(2) to 10L, state(k).shadow to state(k).cursor)
    }
  }

  @Test fun `حسابِ تازه — بی فایل تا نخستین نوشتن؛ نشان پس از آن`() {
    val k = key("fresh")
    restart(on = true)
    assertEquals("" to 0L, state(k).shadow to state(k).cursor)
    assertFalse(state(k).hasSyncState())
    assertTrue(files(k).isEmpty())
    assertTrue(state(k).commitShadow(shadowOf(1), null))
    assertTrue(marker(k)!!.startsWith("v1;file;"))
    assertTrue(state(k).hasSyncState())
  }

  /* ------------------------------ پرچم ------------------------------ */

  @Test fun `روشن ⇐ خاموش ⇐ روشن — هیچ جفتی گم نمی‌شود؛ خاموش همان prefs و فایل پاک`() {
    val k = key("flip")
    seedPrefs(k, shadowOf(0), 3)
    restart(on = true)
    assertTrue(state(k).commitShadow(shadowOf(1), 4))
    val file = mainOf(k)

    restart(on = false)
    assertEquals(shadowOf(1) to 4L, state(k).shadow to state(k).cursor)
    assertNull(marker(k))
    assertEquals(shadowOf(1), prefs.getString("$k.shadow", null))
    assertFalse(file.exists())
    assertTrue(state(k).commitShadow(shadowOf(2), 5))
    assertEquals(shadowOf(2), prefs.getString("$k.shadow", null))

    restart(on = true)
    assertEquals(shadowOf(2) to 5L, state(k).shadow to state(k).cursor)
    assertNotNull(marker(k))
    restart(on = false)
    assertEquals(shadowOf(2) to 5L, state(k).shadow to state(k).cursor)

    //  بسته شدن پس از نشستن در prefs و پیش از پاک کردنِ فایل
    restart(on = true)
    assertTrue(state(k).commitShadow(shadowOf(3), 6))
    val left = mainOf(k)
    restart(on = false)
    crashAt(ShadowStore.Step.BACK_WRITTEN)
    assertThrows(ShadowStore.CrashForTest::class.java) { state(k).shadow }
    ShadowStore.crash = null
    restart(on = false)
    assertEquals(shadowOf(3) to 6L, state(k).shadow to state(k).cursor)
    restart(on = true)
    assertEquals(shadowOf(3) to 6L, state(k).shadow to state(k).cursor)
    left.delete(); File(left.path + ".prev").delete()
  }

  @Test fun `برگشت به ساختِ کهنه‌تر — آنچه می‌بیند و نوشته‌اش پس از به‌روزرسانیِ دوباره منبع است`() {
    val k = key("down")
    seedPrefs(k, shadowOf(0), 3)
    restart(on = true)
    state(k).shadow
    //  ساختِ کهنه پیش از نخستین نوشتن (نسخهٔ prefs هنوز هست) رویش نوشت
    seedPrefs(k, shadowOf(7), 8)
    restart(on = true)
    assertEquals(shadowOf(7) to 8L, state(k).shadow to state(k).cursor)

    assertTrue(state(k).commitShadow(shadowOf(1), 4))
    //  آنچه ساختِ کهنه می‌بیند: سایه نیست، cursor همان (آینه) ⇒ سایهٔ پایه از دفتر، pull از همان cursor
    assertNull(prefs.getString("$k.shadow", null))
    assertEquals(4L, prefs.getLong("$k.cursor", 0))
    //  ساختِ کهنه نوشت
    seedPrefs(k, shadowOf(5), 12)
    restart(on = true)
    assertEquals(shadowOf(5) to 12L, state(k).shadow to state(k).cursor)
    assertTrue(marker(k)!!.startsWith("v1;copy;"))
  }

  /* ------------------------------ رشتهٔ صفحه ------------------------------ */

  @Test fun `رشتهٔ صفحه — نوار، ردِ حال و recoverArchive فایل را نمی‌خوانند؛ خواندنِ فایل شمرده می‌شود`() {
    val k = key("main")
    restart(on = true)
    assertTrue(state(k).commitShadow(shadowOf(1), 4))
    restart(on = true)
    ShadowStore.mainThreadReads = 0
    ShadowStore.mainThread = { true }
    val st = state(k)
    assertEquals(4L, st.cursorSeen)
    assertTrue(st.hasSyncState())
    assertFalse(st.recoverArchive)
    assertEquals(0, ShadowStore.mainThreadReads)
    //  نگهبان: اگر کسی روی رشتهٔ صفحه بخواند دیده می‌شود
    st.shadow
    assertEquals(1, ShadowStore.mainThreadReads)
  }

  @Test fun `رشتهٔ صفحه — حالِ موتور (snapshotStatus) فایلِ سایه را نمی‌خواند`() {
    val engine = SyncV1Engine.of(ctx)
    SyncStateV1.setCurrentAccount(ctx, "shadowmain$n")
    engine.rebind()
    val k = engine.currentKey().also { keys += it }
    try {
      restart(on = true)
      assertTrue(state(k).commitShadow(shadowOf(1), 7))
      restart(on = true)
      ShadowStore.mainThreadReads = 0
      ShadowStore.mainThread = { true }
      assertEquals(7L, engine.snapshotStatus().cursor)
      assertEquals(0, ShadowStore.mainThreadReads)
    } finally {
      ShadowStore.mainThread = null
      SyncStateV1.setCurrentAccount(ctx, "")
      engine.rebind()
    }
  }

  @Test fun `خانه و نوارِ کامپیوتر موتور را روی رشتهٔ صفحه نمی‌سازند`() {
    for (path in listOf("ui/v3/shell/HomeView.kt", "ui/screens/DeskStatusBar.kt")) {
      val text = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/$path").readText()
      assertFalse(path, text.contains("SyncV1Engine.of("))
      assertTrue(path, text.contains("rememberSyncV1Status("))
    }
  }
}
