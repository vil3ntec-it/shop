package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AtRestKeys
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.data.LedgerRecovery
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.SignOutVault
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

/**
 *  کامپیوترِ ویندوز و مک — همان رفع‌های ایمنیِ داده‌ای که گوشی گرفت، با خطاهای خودِ این سیستم‌ها.
 *
 *  خطای ویندوز **ساختگی** است (`DiskWrite.fs`)، نه ویندوزِ واقعی: جابه‌جایی که ضدویروس یک بار قفلش کرد،
 *  درایوِ شبکه‌ای که جابه‌جاییِ اتمی نمی‌دهد، فلشِ FAT/exFAT که پیوندِ سخت ندارد، و پوشه‌ای که `fsync` را
 *  نمی‌پذیرد. پس روی هر سیستمی (و در CI روی ویندوز و مکِ واقعی هم — کارِ `disk-windows`) همان می‌دود.
 */
class WindowsDiskFaultsTest {

  private val ctx = DesktopContext
  private val real = DiskWrite.fs
  private val n = System.nanoTime().toString(36)
  private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
  private lateinit var base: File

  /**
   *  ویندوزِ ساختگی روی دیسکِ واقعی: فقط فایل‌های زیرِ [scope] خطا می‌گیرند (نوشتن‌های پس‌زمینهٔ آزمون‌های دیگر
   *  دست نمی‌خورند) و درنگ فقط شمرده می‌شود، هرگز خوابیده نمی‌شود.
   */
  private inner class FakeWindows(
    private val scope: File,
    override val windows: Boolean = true,
    var atomicFails: Int = 0,
    var replaceFails: Int = 0,
    private val atomicSupported: Boolean = true,
    private val links: Boolean = true,
    private val dirSyncThrows: Boolean = false,
  ) : DiskWrite.Fs {
    private val owner = Thread.currentThread()
    val pauses = mutableListOf<Long>()
    var dirSyncs = 0

    private fun mine(f: File) = f.absolutePath.startsWith(scope.absolutePath)

    override fun moveAtomic(from: File, to: File) {
      if (mine(to) && !atomicSupported) throw AtomicMoveNotSupportedException(from.path, to.path, "network share")
      if (mine(to) && atomicFails > 0) {
        atomicFails--
        throw FileSystemException(from.path, to.path, "The process cannot access the file because it is being used by another process")
      }
      real.moveAtomic(from, to)
    }

    override fun moveReplace(from: File, to: File) {
      if (mine(to) && replaceFails > 0) {
        replaceFails--
        throw AccessDeniedException(to.path)
      }
      real.moveReplace(from, to)
    }

    override fun hardLink(link: File, existing: File) {
      if (mine(link) && !links) {
        //  همان رفتارِ FAT: نسخهٔ پیشین پاک شد، پیوند نشد
        Files.deleteIfExists(link.toPath())
        throw FileSystemException(link.path, existing.path, "Incorrect function")
      }
      real.hardLink(link, existing)
    }

    override fun syncDir(dir: File) {
      if (!mine(dir)) return real.syncDir(dir)
      dirSyncs++
      if (dirSyncThrows) throw AccessDeniedException(dir.path)
      real.syncDir(dir)
    }

    override fun pause(millis: Long) {
      if (Thread.currentThread() == owner) pauses += millis
    }
  }

  private fun use(fake: FakeWindows): FakeWindows = fake.also { DiskWrite.fs = it }

  @Before fun before() {
    base = Files.createTempDirectory("tohid-win").toFile()
    cleanLedger()
    neverSynced()
  }

  @After fun after() {
    DiskWrite.fs = real
    base.deleteRecursively()
    cleanLedger()
  }

  /* ------------------------- جابه‌جاییِ اتمی ------------------------- */

  @Test fun `جابه‌جاییِ قفل‌شده به دستِ ضدویروس یک بار شکست می‌خورد و دورِ دوم با درنگ می‌نشیند`() {
    val f = File(base, "shop-data.json")
    DiskWrite.text(f, "کهنه")
    val fake = use(FakeWindows(base, atomicFails = 1, replaceFails = 1))
    DiskWrite.text(f, "{\"name\":\"برنج\"}")
    assertEquals("{\"name\":\"برنج\"}", f.readText(Charsets.UTF_8))
    assertEquals("یک درنگ پیش از دورِ دوم", listOf(50L), fake.pauses)
    assertFalse("فایلِ کناری نمی‌ماند", File(base, "shop-data.json.tmp").exists())
  }

  @Test fun `قفلی که باز نشد — خطای روشن، فایلِ پیشین دست‌نخورده، درنگ‌ها رو به افزایش و زیرِ یک ثانیه`() {
    val f = File(base, "shop-data.json")
    DiskWrite.text(f, "کهنه")
    val fake = use(FakeWindows(base, atomicFails = 99, replaceFails = 99))
    try {
      DiskWrite.text(f, "تازه")
      fail("باید IOException می‌داد")
    } catch (e: IOException) {
      assertTrue(e.message.orEmpty().isNotBlank())
    }
    assertEquals("کهنه", f.readText(Charsets.UTF_8))
    assertFalse(File(base, "shop-data.json.tmp").exists())
    assertEquals(DiskWrite.ATTEMPTS - 1, fake.pauses.size)
    assertEquals(listOf(50L, 100L, 200L, 400L), fake.pauses)
    assertTrue(fake.pauses.sum() < 1000L)
  }

  @Test fun `درایوِ شبکه بی جابه‌جاییِ اتمی — جایگزینیِ ساده بی هیچ درنگی می‌نشیند`() {
    val f = File(base, "shop-data.json")
    DiskWrite.text(f, "کهنه")
    val fake = use(FakeWindows(base, atomicSupported = false))
    DiskWrite.text(f, "تازه")
    assertEquals("تازه", f.readText(Charsets.UTF_8))
    assertTrue(fake.pauses.isEmpty())
  }

  /* ------------------------- نسخهٔ پیشین (.prev) ------------------------- */

  @Test fun `فلشِ FAT بی پیوندِ سخت — نسخهٔ پیشین رونوشت است با همان بایت‌ها و همان زمانِ فایل`() {
    val f = File(base, "shop-data.json")
    DiskWrite.bytes(f, "یک".toByteArray(Charsets.UTF_8), keepPrevious = true)
    //  زمانِ ثانیهٔ درست — هر سیستمِ فایلی همان را نگه می‌دارد
    val at = (System.currentTimeMillis() / 1000L - 120L) * 1000L
    assertTrue(f.setLastModified(at))
    use(FakeWindows(base, links = false))
    DiskWrite.bytes(f, "دو".toByteArray(Charsets.UTF_8), keepPrevious = true)

    val prev = DiskWrite.previousOf(f)
    assertEquals("دو", f.readText(Charsets.UTF_8))
    assertTrue("بی پیوندِ سخت هم نسخهٔ پیشین هست", prev.isFile)
    assertEquals("یک", prev.readText(Charsets.UTF_8))
    //  `LedgerRecovery.best` با زمانِ فایل می‌چیند: رونوشتِ «حالا» از فایلِ کناریِ تازه‌تر جلو می‌افتاد
    assertEquals(at, prev.lastModified())
    assertFalse(File(base, "shop-data.json.prev.tmp").exists())
    assertFalse(File(base, "shop-data.json.tmp").exists())
  }

  @Test fun `دفتر روی دیسکِ بی پیوندِ سخت — دفترِ بریده از رونوشتِ نسخهٔ پیشین برمی‌گردد`() {
    use(FakeWindows(ctx.filesDir, links = false))
    val store = open()
    assertTrue(runBlocking { store.save(rice) })
    assertTrue(runBlocking { store.save(sugar) })
    val prev = DiskWrite.previousOf(ledger)
    assertTrue(prev.isFile)
    assertEquals(rice, (LedgerRecovery.decode(prev.readText(), json) as LedgerRecovery.Read.Ok).data)

    ledger.writeBytes(ledger.readBytes().let { it.copyOf(it.size / 2) })
    val back = open()
    assertEquals(rice, back.data.value)
    assertEquals(LedgerRecovery.Source.PREVIOUS, back.guard.recovered.value?.source)
  }

  @Test fun `تنظیماتِ کامپیوتر روی دیسکِ بی پیوندِ سخت و با قفلِ لحظه‌ای — ذخیره می‌شود و نسخهٔ پیشین می‌ماند`() {
    val f = File(base, "tohid.json")
    val fake = use(FakeWindows(base, links = false, atomicFails = 1, replaceFails = 1))
    val p = DesktopPrefs(f)
    assertTrue(p.edit().putString("shadow", "سایهٔ دکان").commit())
    assertTrue(p.edit().putLong("cursor", 42L).commit())
    assertTrue("نسخهٔ پیشین (`.bak`) رونوشت شد", File(base, "tohid.json.bak").isFile)
    assertEquals(0, fake.atomicFails)

    f.writeBytes(ByteArray(0))
    val back = DesktopPrefs(f)
    assertEquals("سایهٔ دکان", back.getString("shadow", null))
  }

  /* ------------------------- fsync ِ پوشه ------------------------- */

  @Test fun `ویندوز — fsync ِ پوشه اصلاً تلاش نمی‌شود و نوشتن می‌نشیند`() {
    val f = File(base, "shop-data.json")
    val fake = use(FakeWindows(base, windows = true, dirSyncThrows = true))
    DiskWrite.text(f, "x")
    assertEquals("x", f.readText())
    assertEquals(0, fake.dirSyncs)
  }

  @Test fun `پوشه‌ای که fsync نمی‌پذیرد — خطایش نوشتنِ نشسته را ناموفق نمی‌کند`() {
    val f = File(base, "shop-data.json")
    val fake = use(FakeWindows(base, windows = false, dirSyncThrows = true))
    DiskWrite.text(f, "x")
    DiskWrite.bytes(f, "y".toByteArray(), keepPrevious = true)
    assertEquals("y", f.readText())
    assertTrue(fake.dirSyncs >= 2)
  }

  /* ------------------------- مسیرِ فارسی و فاصله‌دار ------------------------- */

  @Test fun `نامِ کاربرِ فارسی و فاصله در مسیر — دفتر، نسخهٔ پیشین، رونوشت، بازیابی و تنظیمات`() {
    for (dir in listOf(
      File(base, "Users/محمد رضا/AppData/Local/Tohid/files"),
      File(base, "Users/زهرا احمدی/Library/Application Support/Tohid/files"),
    )) {
      val f = File(dir, "shop-data.json")
      DiskWrite.text(f, json.encodeToString(ShopData.serializer(), rice))
      DiskWrite.bytes(f, json.encodeToString(ShopData.serializer(), sugar).toByteArray(Charsets.UTF_8), keepPrevious = true)
      assertEquals(sugar, json.decodeFromString(ShopData.serializer(), f.readText(Charsets.UTF_8)))
      val copy = File(dir, "پشتیبان ۱۴۰۵.json")
      DiskWrite.copy(f, copy)
      assertArrayEquals(f.readBytes(), copy.readBytes())

      f.writeText("{\"products\":[")
      val found = LedgerRecovery.best(LedgerRecovery.candidates(f, listOf(copy)), json) { it.readText(Charsets.UTF_8) }
      assertNotNull("نسخهٔ سالم در مسیرِ فارسی پیدا شد", found)

      val p = DesktopPrefs(File(File(dir.parentFile, "shared_prefs"), "tohid.json"))
      assertTrue(p.edit().putString("shop", "دکانِ محمد").commit())
      assertEquals("دکانِ محمد", DesktopPrefs(File(File(dir.parentFile, "shared_prefs"), "tohid.json")).getString("shop", null))
    }
  }

  /* ------------------------- پوشهٔ داده و اجازه‌ها ------------------------- */

  @Test fun `پوشهٔ داده — ویندوز LOCALAPPDATA با نامِ فارسی، مک Application Support، لینوکس XDG، متغیرِ تهی نه`() {
    val win = "C:\\Users\\محمد رضا\\AppData\\Local"
    assertEquals(File(win, "Tohid"), ctx.rootFor("Windows 11", "C:\\Users\\محمد رضا", env("LOCALAPPDATA" to win)))
    //  `LOCALAPPDATA=` ِ تهی پیش‌تر پوشهٔ نسبیِ `Tohid` را کنارِ پوشهٔ جاری می‌ساخت
    assertEquals(
      File(File(File("C:\\Users\\محمد رضا", "AppData"), "Local"), "Tohid"),
      ctx.rootFor("Windows 10", "C:\\Users\\محمد رضا", env("LOCALAPPDATA" to "  ")),
    )
    val mac = File("/Users/زهرا احمدی/Library/Application Support/Tohid")
    assertEquals(mac, ctx.rootFor("Mac OS X", "/Users/زهرا احمدی", env()))
    assertEquals(mac, ctx.rootFor("Darwin", "/Users/زهرا احمدی", env()))
    assertEquals(File("/home/علی/.local/share/tohid"), ctx.rootFor("Linux", "/home/علی", env("XDG_DATA_HOME" to "")))
    assertEquals(File("/data/x/tohid"), ctx.rootFor("Linux", "/home/علی", env("XDG_DATA_HOME" to "/data/x")))
    assertEquals(File("/t/d"), ctx.rootFor("Windows 11", "C:\\Users\\a", env("TOHID_DATA_DIR" to "/t/d")))
  }

  @Test fun `مک و لینوکس — پوشهٔ داده فقط برای صاحبش`() {
    val dir = File(base, "Application Support/Tohid").apply { mkdirs() }
    ctx.ownerOnly(dir)
    val want = if (posix(dir) == null) null else "rwx------"
    assertEquals(want, posix(dir))
    assertEquals(want, posix(ctx.root))
  }

  @Test fun `گاوصندوقِ خروجِ اجباری روی کامپیوتر — همان کلیدِ دفتر، رمزدار، کلید فقط مالِ صاحب`() {
    cleanKeys()
    val user = "usr_vault$n"
    val op = SyncOp(
      opId = "op-vault-$n", ts = 1L, table = "products", rowId = "row-vault-$n", type = SyncOp.INSERT,
      fields = buildJsonObject { put("name", JsonPrimitive("برنج")) },
    )
    try {
      assertTrue("روی کامپیوتر کلید همیشه هست ⇒ گاوصندوق بسته می‌شود", SignOutVault.seal(ctx, user, "shp_$n", listOf(op)))
      val raw = String(SignOutVault.file(ctx, user).readBytes(), Charsets.ISO_8859_1)
      assertFalse("رمزدار، نه JSON ِ ساده", raw.contains(op.opId) || raw.contains(op.rowId))
      assertEquals(listOf(op.opId), SignOutVault.open(ctx, user)?.map { it.opId })
      //  همان کلیدِ `AtRestKeysDesk` که دفتر را می‌پیچد — کلیدِ جدایی برای گاوصندوق ساخته نمی‌شود
      assertEquals("desktop-file:keys/atrest-kek.key", AtRestKeys.kek(ctx).id)
      assertTrue(kekFile.isFile)
      if (onWindows) {
        assertEquals(true, AtRestKeys.windowsAcl)
      } else if (posix(kekFile) != null) {
        assertEquals("rw-------", posix(kekFile))
        assertEquals("rwx------", posix(kekFile.parentFile))
      }
    } finally {
      SignOutVault.drop(ctx, user)
      cleanKeys()
    }
  }

  /* ------------------------- کمک‌ها ------------------------- */

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val kekFile get() = File(File(ctx.root, "keys"), "atrest-kek.key")

  private val rice = ShopData(products = listOf(Product(id = "win-p1", name = "برنج", salePrice = 120.0)))
  private val sugar = rice.copy(products = rice.products + Product(id = "win-p2", name = "شکر", salePrice = 90.0))

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private fun env(vararg pairs: Pair<String, String>): (String) -> String? = { k -> pairs.firstOrNull { it.first == k }?.second }

  private fun posix(f: File): String? =
    Files.getFileAttributeView(f.toPath(), PosixFileAttributeView::class.java)
      ?.readAttributes()?.permissions()?.let { PosixFilePermissions.toString(it) }

  private fun cleanLedger() {
    AtRest.setWanted(ctx, false)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    AutoBackup.dir(ctx).listFiles().orEmpty().forEach { it.delete() }
  }

  private fun cleanKeys() {
    File(ctx.filesDir, "atrest").deleteRecursively()
    kekFile.delete()
    AtRest.keyring(ctx).forget()
  }

  /** برگشتِ خودکار فقط روی دستگاهِ هرگز همگام‌نشده — همان `DataSafetyDiskTest.neverSynced` */
  private fun neverSynced() {
    val sync = ir.vil3ntec.tohid.sync.SyncStore(ctx)
    val key = SyncStateV1.currentAccount(ctx).ifBlank { sync.accountName }.ifBlank { sync.deviceUid }
    SyncStateV1(ctx, key).apply { cursor = 0L; reconciledAt = 0L; snapshotAt = 0L }
  }
}
