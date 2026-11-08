package ir.vil3ntec.tohid.desktop.keystore

import ir.vil3ntec.tohid.core.atrest.AtRestException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Base64

/**
 *  SEC-01 فاز ۲ — جای KEK روی کامپیوتر با کلیددانِ ساختگی: هر شکستِ کلیددان (نیست، قفل، نوشتن شد ولی خواندن نه،
 *  مقدارِ نادرست) و «برق» در هر گامِ کوچ و پاک کردن تزریق می‌شود. معیار همیشه یکی است: **همان ۳۲ بایت** باز
 *  به دست می‌آید، یا «همین حالا نه» (`KEY_UNAVAILABLE`) — هرگز «کلید نیست» و هرگز کلیدِ تازه. کلیددانِ واقعیِ سیستم:
 *  `OsKeystoreRoundTripTest`.
 */
class KekVaultTest {

  private lateinit var dir: File
  private val random = SecureRandom()

  @Before fun before() {
    dir = Files.createTempDirectory("kek-vault").toFile()
  }

  @After fun after() {
    dir.deleteRecursively()
  }

  /** برق — `Error`، تا هیچ `catch (Exception)` ِ کوچ آن را «شکستِ عادی» نگیرد */
  private class PowerCut : Error("power cut")

  private class FakeStore(override val id: String = "fake", private val inBlob: Boolean = false) : OsKeyStore {
    override val label: String = "Fake"
    val items = HashMap<String, ByteArray>()
    var missing = false
    var locked = false
    var readFails = false
    var wrongRead = false
    var cutAfterStore = false
    var protects = 0
    var unprotects = 0

    override fun protect(ref: String, key: ByteArray): String {
      if (missing) throw KeyStoreFailure("missing")
      if (locked) throw KeyStoreFailure("locked")
      protects++
      val blob = if (inBlob) Base64.getEncoder().encodeToString(key.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()) else ""
      if (!inBlob) items[ref] = key.copyOf()
      if (cutAfterStore) { cutAfterStore = false; throw PowerCut() }
      return blob
    }

    override fun unprotect(ref: String, blob: String): ByteArray {
      if (missing) throw KeyStoreFailure("missing")
      if (locked) throw KeyStoreFailure("locked")
      if (readFails) throw KeyStoreFailure("read failed")
      unprotects++
      val k = if (inBlob) {
        Base64.getDecoder().decode(blob).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
      } else {
        items[ref]?.copyOf() ?: throw KeyStoreFailure("not found", notFound = true)
      }
      if (wrongRead) k[0] = (k[0] + 1).toByte()
      return k
    }

    override fun remove(ref: String) {
      items.remove(ref)
    }
  }

  /** دیسکِ واقعی که در گامِ nام ِ نوشتن/پاک کردن/کنار گذاشتن برق را می‌بُرد — پیش یا پس از خودِ گام */
  private class CutDisk(private val cutAt: Int, private val after: Boolean) : KekVault.Disk {
    var ops = 0
    var dead = false
    private fun step(op: () -> Unit) {
      if (dead) throw PowerCut()
      ops++
      if (ops == cutAt && !after) { dead = true; throw PowerCut() }
      op()
      if (ops == cutAt) { dead = true; throw PowerCut() }
    }
    override fun read(f: File): ByteArray {
      if (dead) throw PowerCut()
      return KekVault.Disk.Real.read(f)
    }
    override fun write(f: File, bytes: ByteArray) = step { KekVault.Disk.Real.write(f, bytes) }
    override fun delete(f: File): Boolean {
      var r = false
      step { r = KekVault.Disk.Real.delete(f) }
      return r
    }
    override fun moveAside(from: File, to: File) = step { KekVault.Disk.Real.moveAside(from, to) }
    override fun syncDir(dir: File) = step { }
  }

  private var flag = true

  private fun vault(store: OsKeyStore?, disk: KekVault.Disk = KekVault.Disk.Real) =
    KekVault(dir, flag = { flag }, store = { store }, disk = disk, random = random)

  private val plain get() = File(dir, KekVault.PLAIN)
  private val os get() = File(dir, KekVault.OS)

  /** فاز ۱: کلید با پرچمِ خاموش ساخته می‌شود — همان فایلِ ساده */
  private fun phase1Key(): ByteArray {
    flag = false
    val k = vault(null).forWrite()
    assertArrayEquals(k, plain.readBytes())
    flag = true
    return k
  }

  /** یک «اجرای برنامه»: کلید برای باز کردن، و اگر همان کلید بود «دفتر باز شد» */
  private fun launch(v: KekVault, expect: ByteArray): KekVault {
    val keys = v.forRead()
    assertArrayEquals("همان کلید", expect, keys.first())
    v.opened(keys.first())
    return v
  }

  private fun kind(block: () -> Unit): AtRestException.Kind? = try {
    block()
    null
  } catch (e: AtRestException) {
    e.kind
  }

  @Test fun `پرچمِ خاموش — همان فایلِ ساده و هیچ تماسی با کلیددانِ سیستم`() {
    flag = false
    val store = FakeStore()
    val v = vault(store)
    val k = v.forWrite()
    assertArrayEquals(k, plain.readBytes())
    launch(v, k)
    v.relaunch()
    launch(v, k)
    assertEquals(KekVault.Status.Off, v.status)
    assertFalse(os.exists())
    assertEquals(0, store.protects + store.unprotects)
    plain.delete()
    v.relaunch()
    assertEquals("بی فایل و بی نشان همان «کلید نیست» ِ فاز ۱", AtRestException.Kind.KEY_MISSING, kind { v.forRead() })
  }

  @Test fun `کوچ — فایلِ ساده تا اجرای بعدیِ موفق می‌ماند و بعد پاک می‌شود`() {
    val k = phase1Key()
    val store = FakeStore()
    val v = launch(vault(store), k)
    assertTrue(os.exists())
    assertTrue("همان اجرایی که نشان را نوشت فایل را پاک نمی‌کند", plain.exists())
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.KEPT), v.status)
    assertArrayEquals(k, store.items.values.single())

    //  اجرای بعد: فایل فقط پس از «دفتر باز شد» پاک می‌شود
    v.relaunch()
    assertArrayEquals(k, v.forRead().first())
    assertTrue(plain.exists())
    v.opened(k)
    assertFalse(plain.exists())
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.NONE), v.status)

    //  از این پس فقط کلیددانِ سیستم
    v.relaunch()
    launch(v, k)
    v.relaunch()
    assertArrayEquals(k, v.forWrite())
    assertFalse(plain.exists())
  }

  @Test fun `کلیدِ تازه با پرچمِ روشن — اول همان فایل، بعد همان کوچ`() {
    val store = FakeStore()
    val v = vault(store)
    val k = v.forWrite()
    assertArrayEquals(k, plain.readBytes())
    assertTrue(os.exists())
    v.opened(k)
    assertTrue(plain.exists())
    v.relaunch()
    launch(v, k)
    assertFalse(plain.exists())
  }

  @Test fun `کلیددانِ سیستم نیست — همان فایل، هرگز «کلید نیست»`() {
    val k = phase1Key()
    for (store in listOf(null, FakeStore().apply { missing = true })) {
      val v = vault(store)
      repeat(3) {
        launch(v, k)
        assertTrue(v.status is KekVault.Status.Fallback)
        assertTrue(plain.exists())
        assertFalse(os.exists())
        v.relaunch()
      }
      assertArrayEquals(k, v.forWrite())
    }
  }

  @Test fun `کلیددانِ قفل — پیش از کوچ همان فایل، پس از کوچ «همین حالا نه» و هرگز کلیدِ تازه`() {
    val k = phase1Key()
    val store = FakeStore().apply { locked = true }
    val v = launch(vault(store), k)
    assertTrue(v.status is KekVault.Status.Fallback)
    assertFalse(os.exists())

    store.locked = false
    v.relaunch(); launch(v, k)
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())

    store.locked = true
    v.relaunch()
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kind { v.forRead() })
    assertEquals("قفل هرگز کلیدِ تازه نمی‌سازد", AtRestException.Kind.KEY_UNAVAILABLE, kind { v.forWrite() })
    assertFalse(plain.exists())
    assertEquals(1, store.items.size)

    store.locked = false
    v.relaunch()
    launch(v, k)
  }

  @Test fun `نوشتن شد ولی خواندن نشد — نشان نمی‌نشیند و فایل می‌ماند`() {
    val k = phase1Key()
    val store = FakeStore().apply { readFails = true }
    val v = vault(store)
    repeat(3) {
      launch(v, k)
      assertTrue(v.status is KekVault.Status.Fallback)
      assertFalse(os.exists())
      assertTrue(plain.exists())
      v.relaunch()
    }
    store.wrongRead = true
    store.readFails = false
    launch(v, k)
    assertTrue("بازخوانیِ نادرست هم نشان را نمی‌نشاند", v.status is KekVault.Status.Fallback)
    assertFalse(os.exists())

    store.wrongRead = false
    v.relaunch(); launch(v, k)
    assertTrue(os.exists())
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())
  }

  @Test fun `پس از کوچ خواندن نشد — فایل پاک نمی‌شود تا کلیددان دوباره درست بخواند`() {
    val k = phase1Key()
    val store = FakeStore()
    val v = launch(vault(store), k)
    store.readFails = true
    repeat(3) {
      v.relaunch()
      launch(v, k)
      assertTrue(plain.exists())
      assertTrue(v.status is KekVault.Status.Fallback)
    }
    store.readFails = false
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())
  }

  @Test fun `برق در هر گامِ کوچ و پاک کردن — کلید هرگز گم نمی‌شود`() {
    var scenarios = 0
    //  دو آغاز: بی نشان (نوشتنِ نشان ⇐ پاک کردنِ فایل ⇐ fsync ِ پوشه) و با نشانِ خراب (کنار گذاشتن هم)
    for (junk in listOf(false, true)) for (after in listOf(false, true)) for (cutAt in 1..6) {
      dir.listFiles().orEmpty().forEach { it.delete() }
      val k = phase1Key()
      if (junk) os.writeText("junk")
      val store = FakeStore()
      val disk = CutDisk(cutAt, after)
      val v = vault(store, disk)
      //  تا سه اجرا با همان دیسک؛ برق که رفت، «برنامه» مرده است
      for (i in 0 until 3) {
        try {
          launch(v, k)
        } catch (_: PowerCut) {
          break
        }
        if (disk.dead) break
        v.relaunch()
      }
      if (disk.dead) scenarios++
      val where = "گامِ $cutAt ${if (after) "پس" else "پیش"} از کار${if (junk) "، با نشانِ خراب" else ""}"
      //  پس از برق: هر نسخه‌ای که مانده همان کلید را می‌دهد، و کوچ تمام می‌شود
      val again = vault(store)
      repeat(3) {
        launch(again, k)
        again.relaunch()
      }
      assertArrayEquals(where, k, again.forRead().single())
      assertFalse("کوچ پس از برق تمام شد — $where", plain.exists())
      assertTrue(os.exists())
    }
    //  بی نشان ۳ گامِ نوشتنی، با نشانِ خراب ۴؛ هر کدام پیش و پس از کار
    assertEquals(2 * 3 + 2 * 4, scenarios)
  }

  @Test fun `برق درست پس از سپردن به کلیددان — اجرای بعد همان حساب را دوباره می‌نویسد`() {
    val k = phase1Key()
    val store = FakeStore().apply { cutAfterStore = true }
    try {
      vault(store).forRead()
      fail("برق نرفت")
    } catch (_: PowerCut) {
    }
    assertFalse(os.exists())
    assertTrue(plain.exists())
    val v = vault(store)
    launch(v, k)
    assertTrue(os.exists())
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())
    assertEquals(1, store.items.size)
  }

  @Test fun `پرچم خاموش پس از کوچ — فایلِ ساده با همان کلید برمی‌گردد`() {
    val k = phase1Key()
    val store = FakeStore()
    val v = launch(vault(store), k)
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())

    flag = false
    v.relaunch()
    launch(v, k)
    assertArrayEquals("فایلِ ساده برگشت", k, plain.readBytes())
    assertEquals(KekVault.Status.Off, v.status)
    val reads = store.unprotects
    v.relaunch()
    launch(v, k)
    assertEquals("خاموش و فایل هست ⇒ هیچ تماسی با کلیددان", reads, store.unprotects)
    assertTrue("نسخهٔ سیستم پاک نمی‌شود", os.exists() && store.items.isNotEmpty())

    //  دوباره روشن: همان نشان، بی کوچِ تازه
    flag = true
    val protects = store.protects
    v.relaunch(); launch(v, k)
    v.relaunch(); launch(v, k)
    assertEquals(protects, store.protects)
    assertFalse(plain.exists())
  }

  @Test fun `نشانِ خراب — کنار می‌رود نه رونویسی؛ بی فایلِ ساده «کلید نیست» و کلیدِ تازه ساخته نمی‌شود`() {
    val k = phase1Key()
    os.writeText("junk")
    val store = FakeStore()
    val v = launch(vault(store), k)
    assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("${KekVault.OS}.old-") && it.readText() == "junk" })
    assertTrue(os.exists())
    assertTrue(plain.exists())

    dir.listFiles().orEmpty().forEach { it.delete() }
    os.writeText("junk")
    v.relaunch()
    assertEquals(AtRestException.Kind.KEY_MISSING, kind { v.forRead() })
    assertEquals(AtRestException.Kind.KEY_MISSING, kind { v.forWrite() })
    assertFalse("کلیدِ تازه کنارِ نشانِ خراب ساخته نشد", plain.exists())
    assertEquals("junk", os.readText())
  }

  @Test fun `نشان هست و کلیددان آن را ندارد — «همین حالا نه»، نه کلیدِ تازه`() {
    val k = phase1Key()
    val store = FakeStore()
    val v = launch(vault(store), k)
    v.relaunch(); launch(v, k)
    store.items.clear()
    v.relaunch()
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kind { v.forRead() })
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kind { v.forWrite() })
    assertFalse(plain.exists())
    assertTrue(os.exists())
  }

  @Test fun `کلیددان پاکش کرد ولی فایلِ ساده هنوز هست — کوچ از نو`() {
    val k = phase1Key()
    val store = FakeStore()
    val v = launch(vault(store), k)
    store.items.clear()
    v.relaunch(); launch(v, k)
    assertTrue(plain.exists())
    assertArrayEquals(k, store.items.values.single())
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())
  }

  @Test fun `فایلِ سادهٔ ناهمخوان — نامزدِ دوم، و دست نخورده`() {
    val k = phase1Key()
    val store = FakeStore()
    val v = launch(vault(store), k)
    v.relaunch(); launch(v, k)
    val stray = ByteArray(32).also { random.nextBytes(it) }
    plain.writeBytes(stray)
    v.relaunch()
    val keys = v.forRead()
    assertEquals(2, keys.size)
    assertArrayEquals(k, keys[0])
    assertArrayEquals(stray, keys[1])
    v.opened(k)
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.STRAY), v.status)
    v.relaunch(); launch(v, k)
    assertArrayEquals("فایلِ ناهمخوان پاک نشد", stray, plain.readBytes())
  }

  @Test fun `نشانِ سیستمِ دیگر — کنار می‌رود و کلید به کلیددانِ همین سیستم می‌رود`() {
    val k = phase1Key()
    val other = FakeStore(id = "dpapi", inBlob = true)
    launch(vault(other), k)
    assertTrue(os.readText().contains("store=dpapi"))
    val here = FakeStore(id = "keychain")
    val v = launch(vault(here), k)
    assertTrue(os.readText().contains("store=keychain"))
    assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("${KekVault.OS}.old-") })
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())
  }

  @Test fun `شکلِ DPAPI — بایت‌های پیچیده در نشان، کوچ و پاک کردن همان`() {
    val k = phase1Key()
    val store = FakeStore(id = "dpapi", inBlob = true)
    val v = launch(vault(store), k)
    val blob = os.readLines().first { it.startsWith("blob=") }.removePrefix("blob=")
    assertTrue(blob.isNotEmpty())
    assertFalse("کلیدِ خام در نشان نیست", os.readText().contains(KekVault.hex(k)))
    v.relaunch(); launch(v, k)
    assertFalse(plain.exists())
    v.relaunch(); launch(v, k)
  }

  @Test fun `نشان — قالبِ دیگر یا بیش‌ازاندازه پذیرفته نمی‌شود و نشانِ کلید یکتاست`() {
    val k1 = ByteArray(32).also { random.nextBytes(it) }
    val k2 = ByteArray(32).also { random.nextBytes(it) }
    assertEquals(KekVault.check(k1), KekVault.check(k1.copyOf()))
    assertNotEquals(KekVault.check(k1), KekVault.check(k2))
    assertEquals(32, KekVault.check(k1).length)
    val good = KekVault.Descriptor("dpapi", KekVault.check(k1), "atrest-kek-0011", "QUJD", "ab12").encode()
    val d = KekVault.Descriptor.parse(good)!!
    assertEquals("dpapi", d.store)
    assertEquals("QUJD", d.blob)
    assertNull(KekVault.Descriptor.parse("junk".toByteArray()))
    assertNull(KekVault.Descriptor.parse(String(good).replace("check=", "check=zz").toByteArray()))
    assertNull(KekVault.Descriptor.parse(String(good).replace("ref=atrest-kek-0011", "ref=a b").toByteArray()))
    assertNull(KekVault.Descriptor.parse(good + ByteArray(20_000) { 'a'.code.toByte() }))
  }

  @Test fun `پرچم — فقط true روشن است، با BOM و UTF-16 ِ نوت‌پد هم؛ ویژگیِ JVM جلو می‌افتد`() {
    val f = File(dir, DeskKeystoreFlag.KEY)
    assertFalse(DeskKeystoreFlag.fileSays(f))
    f.writeText("true\n"); assertTrue(DeskKeystoreFlag.fileSays(f))
    f.writeText(" TRUE "); assertTrue(DeskKeystoreFlag.fileSays(f))
    f.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "true".toByteArray()); assertTrue(DeskKeystoreFlag.fileSays(f))
    f.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "true\r\n".toByteArray(Charsets.UTF_16LE)); assertTrue(DeskKeystoreFlag.fileSays(f))
    for (no in listOf("1", "on", "yes", "false", "", "true true")) {
      f.writeText(no); assertFalse("«$no»", DeskKeystoreFlag.fileSays(f))
    }
    f.writeText("true" + " ".repeat(100)); assertFalse("فایلِ بزرگ", DeskKeystoreFlag.fileSays(f))

    f.writeText("true")
    val before = System.getProperty(DeskKeystoreFlag.KEY)
    try {
      System.clearProperty(DeskKeystoreFlag.KEY)
      assertTrue(DeskKeystoreFlag.on(dir))
      System.setProperty(DeskKeystoreFlag.KEY, "false")
      assertFalse(DeskKeystoreFlag.on(dir))
      f.delete()
      System.setProperty(DeskKeystoreFlag.KEY, "true")
      assertTrue(DeskKeystoreFlag.on(dir))
      System.clearProperty(DeskKeystoreFlag.KEY)
      assertFalse("پیش‌فرض خاموش", DeskKeystoreFlag.on(dir))
    } finally {
      if (before == null) System.clearProperty(DeskKeystoreFlag.KEY) else System.setProperty(DeskKeystoreFlag.KEY, before)
    }
  }
}
