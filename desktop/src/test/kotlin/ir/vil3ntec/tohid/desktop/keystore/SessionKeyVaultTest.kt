package ir.vil3ntec.tohid.desktop.keystore

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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  SEC-01 فاز ۲ — کلیدِ نشستِ ورود (`SecretBox` ⇐ [SessionBox] ⇐ همان [KekVault] با [KekVault.Slot.SESSION]) با
 *  کلیددانِ ساختگی، همان شکست‌ها و همان «برق» ِ `KekVaultTest`. معیار: توکن‌ها و رازِ دستگاهِ شاگرد (#1002،
 *  `TokenStorage.staffDeviceKey` ⇐ همان `SecretBox.seal`) پس از کوچ **بایت‌به‌بایت همان** باز می‌شوند، یا کلید «همین
 *  حالا نه» است و برنامه «دوباره وارد شوید» می‌گوید — هرگز کلیدِ تازه‌ای که آن‌ها را بی‌صاحب کند، هرگز استثنا.
 */
class SessionKeyVaultTest {

  private lateinit var dir: File
  private val random = SecureRandom()
  private var flag = true
  private val slot = KekVault.Slot.SESSION

  /** رازهای یک نشست: توکنِ دسترسی، توکنِ تازه‌سازی و رازِ دستگاهِ شاگرد (همه از همان `SecretBox` می‌گذرند) */
  private val secrets = listOf("access-test-value-دسترسی", "refresh-test-value-تازه‌سازی", "staff-device-test-value-شاگرد")

  @Before fun before() {
    dir = Files.createTempDirectory("session-vault").toFile()
  }

  @After fun after() {
    dir.deleteRecursively()
  }

  private val plain get() = File(dir, slot.plain)
  private val os get() = File(dir, slot.os)

  private fun vault(store: OsKeyStore?, disk: KekVault.Disk = KekVault.Disk.Real) =
    KekVault(dir, flag = { flag }, store = { store }, disk = disk, random = random, slot = slot)

  private fun box(store: OsKeyStore?, disk: KekVault.Disk = KekVault.Disk.Real) = SessionBox(vault(store, disk), random)

  /** فاز ۱ (پرچمِ خاموش): همان فایلِ سادهٔ `keys/session.key` و همان شکلِ `v1:` ِ پیشین */
  private fun phase1(): Pair<ByteArray, List<String>> {
    flag = false
    val b = box(null)
    val sealed = secrets.map { b.seal(it) }
    val k = plain.readBytes()
    assertEquals(32, k.size)
    flag = true
    return k to sealed
  }

  /** یک «اجرای برنامه»: هر رازِ رمزشده باز می‌شود و بایت‌به‌بایت همان است */
  private fun launch(b: SessionBox, sealed: List<String>): SessionBox {
    for ((i, s) in sealed.withIndex()) {
      assertArrayEquals("همان راز", secrets[i].toByteArray(Charsets.UTF_8), b.open(s)?.toByteArray(Charsets.UTF_8))
    }
    return b
  }

  private fun unavailable(b: SessionBox) = b.state() is SessionBox.State.Unavailable

  @Test fun `پرچمِ خاموش — همان فایلِ ساده، همان شکلِ پیشین و هیچ تماسی با کلیددانِ سیستم`() {
    flag = false
    val store = FakeStore()
    val b = box(store)
    assertEquals("نصبِ تازه ⇒ هنوز کلیدی نیست", SessionBox.State.Empty, b.state())
    assertNull(b.open("v1:" + Base64.getEncoder().encodeToString(ByteArray(40))))
    val sealed = secrets.map { b.seal(it) }
    val k = plain.readBytes()
    //  همان رمزِ پیشینِ `SecretBox` (پیش از این PR): v1: + base64(iv ‖ متن ‖ برچسب) با همان فایل
    val legacy = legacySeal(k, "پیشین-test-value")
    launch(b, sealed)
    assertEquals("پیشین-test-value", b.open(legacy))
    b.relaunch()
    launch(b, sealed)
    assertEquals(KekVault.Status.Off, b.vaultStatus)
    assertFalse(os.exists())
    assertEquals(0, store.protects + store.unprotects)
    assertTrue(b.persistent())
    assertFalse(b.lost)
  }

  @Test fun `کوچ — رازها بایت‌به‌بایت همان و فایلِ ساده تا اجرایی که واقعاً رازی را باز کرد می‌ماند`() {
    val (k, sealed) = phase1()
    val store = FakeStore()
    val b = launch(box(store), sealed)
    assertTrue(os.exists())
    assertTrue("همان اجرایی که نشان را نوشت فایل را پاک نمی‌کند", plain.exists())
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.KEPT), b.vaultStatus)
    assertArrayEquals("بایت‌های کلید هرگز عوض نمی‌شوند", k, store.items.values.single())

    //  اجرای بعد بی هیچ رازی برای باز کردن (بیرون از حساب) یا فقط با رمز کردن: فایل می‌ماند
    b.relaunch()
    b.state()
    b.seal("only-sealed-test-value")
    assertTrue("بی بازشدنِ واقعیِ رازی، فایلِ ساده می‌ماند", plain.exists())

    //  اجرای بعد: نخستین رازی که با همین کلید باز شد ⇒ فایلِ ساده پاک
    b.relaunch()
    assertEquals(secrets[0], b.open(sealed[0]))
    assertFalse(plain.exists())
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.NONE), b.vaultStatus)
    launch(b, sealed)

    //  از این پس فقط کلیددانِ سیستم؛ رازِ تازه هم با همان کلید
    b.relaunch()
    launch(b, sealed)
    val fresh = b.seal("after-migration-test-value")
    assertTrue(fresh.startsWith("v1:"))
    b.relaunch()
    assertEquals("after-migration-test-value", b.open(fresh))
    assertArrayEquals("همان کلیدِ فاز ۱", k, store.items.values.single())
  }

  @Test fun `کلیدِ تازه با پرچمِ روشن — اول همان فایل، بعد همان کوچ`() {
    val store = FakeStore()
    val b = box(store)
    assertEquals(SessionBox.State.Empty, b.state())
    val sealed = secrets.map { b.seal(it) }
    assertTrue(plain.exists())
    assertTrue(os.exists())
    launch(b, sealed)
    assertTrue(plain.exists())
    b.relaunch()
    launch(b, sealed)
    assertFalse(plain.exists())
  }

  @Test fun `کلیددانِ سیستم نیست — همان فایل و همان نشست، هرگز «دوباره وارد شوید»`() {
    val (_, sealed) = phase1()
    for (store in listOf(null, FakeStore().apply { missing = true })) {
      val b = box(store)
      repeat(3) {
        launch(b, sealed)
        assertTrue(b.vaultStatus is KekVault.Status.Fallback)
        assertTrue(plain.exists())
        assertFalse(os.exists())
        assertTrue(b.persistent())
        assertFalse(b.lost)
        b.relaunch()
      }
    }
  }

  @Test fun `کلیددانِ قفل پیش از کوچ — همان فایل و همان نشست`() {
    val (_, sealed) = phase1()
    val store = FakeStore().apply { locked = true }
    val b = box(store)
    repeat(2) {
      launch(b, sealed)
      assertTrue(b.vaultStatus is KekVault.Status.Fallback)
      assertFalse(os.exists())
      assertTrue(plain.exists())
      assertTrue(b.persistent())
      b.relaunch()
    }
    store.locked = false
    launch(b, sealed)
    assertTrue(os.exists())
  }

  @Test fun `کلیددانِ قفل پس از کوچ — «دوباره وارد شوید»، ورودِ تازه فقط در حافظه و هرگز کلیدِ تازه؛ باز شد ⇒ همان نشست`() {
    val (k, sealed) = phase1()
    val store = FakeStore()
    val b = launch(box(store), sealed)
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())
    val marker = os.readBytes()

    store.locked = true
    b.relaunch()
    for (s in sealed) assertNull("باز نشد ⇒ «نشستی نیست»، نه استثنا", b.open(s))
    assertTrue("برنامه «دوباره وارد شوید» می‌گوید", b.lost)
    assertTrue(unavailable(b))
    assertFalse("هاب روشن نمی‌شود", b.persistent())
    //  کاربر دوباره وارد شد: نشستِ همین اجرا کار می‌کند، ولی هیچ کلیدی روی دیسک یا در کلیددان ساخته نمی‌شود
    val fresh = b.seal("signed-in-again-test-value")
    assertTrue(fresh.startsWith("m1:"))
    assertEquals("signed-in-again-test-value", b.open(fresh))
    assertFalse("هیچ فایلِ کلیدِ تازه‌ای", plain.exists())
    assertArrayEquals("نشان دست نخورد", marker, os.readBytes())
    assertEquals(1, store.items.size)
    assertArrayEquals(k, store.items.values.single())
    b.relaunch()
    assertNull("رازِ حافظه پس از بستن نمی‌ماند", b.open(fresh))
    assertTrue(unavailable(b))
    assertFalse(plain.exists())

    //  کلیددان باز شد ⇒ همان نشستِ پیشین، بایت‌به‌بایت
    store.locked = false
    b.relaunch()
    launch(b, sealed)
    assertFalse(b.lost)
    assertTrue(b.persistent())
    assertEquals(KekVault.Status.Protected("fake", KekVault.Plain.NONE), b.vaultStatus)
  }

  @Test fun `نوشتن شد ولی خواندن نشد — نشان نمی‌نشیند و فایل می‌ماند`() {
    val (_, sealed) = phase1()
    val store = FakeStore().apply { readFails = true }
    val b = box(store)
    repeat(3) {
      launch(b, sealed)
      assertTrue(b.vaultStatus is KekVault.Status.Fallback)
      assertFalse(os.exists())
      assertTrue(plain.exists())
      b.relaunch()
    }
    store.wrongRead = true
    store.readFails = false
    launch(b, sealed)
    assertTrue("بازخوانیِ نادرست هم نشان را نمی‌نشاند", b.vaultStatus is KekVault.Status.Fallback)
    assertFalse(os.exists())

    store.wrongRead = false
    b.relaunch(); launch(b, sealed)
    assertTrue(os.exists())
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())
  }

  @Test fun `پس از کوچ خواندن نشد — فایل پاک نمی‌شود تا کلیددان دوباره درست بخواند`() {
    val (_, sealed) = phase1()
    val store = FakeStore()
    val b = launch(box(store), sealed)
    store.readFails = true
    repeat(3) {
      b.relaunch()
      launch(b, sealed)
      assertTrue(plain.exists())
      assertTrue(b.vaultStatus is KekVault.Status.Fallback)
    }
    store.readFails = false
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())
  }

  @Test fun `مقدارِ نادرست از کلیددان پس از کوچ — «همین حالا نه»، نه کلیدِ تازه`() {
    val (k, sealed) = phase1()
    val store = FakeStore()
    val b = launch(box(store), sealed)
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())

    store.wrongRead = true
    b.relaunch()
    assertNull(b.open(sealed[1]))
    assertTrue(unavailable(b))
    assertTrue(b.seal("x-test-value").startsWith("m1:"))
    assertFalse(plain.exists())
    store.wrongRead = false
    b.relaunch()
    launch(b, sealed)
    assertArrayEquals(k, store.items.values.single())
  }

  @Test fun `کلیددان آن را ندارد و فایلی نیست — «همین حالا نه»؛ با فایل کوچ از نو`() {
    val (_, sealed) = phase1()
    val store = FakeStore()
    val b = launch(box(store), sealed)
    store.items.clear()
    b.relaunch(); launch(b, sealed)
    assertTrue("فایل هنوز بود ⇒ کوچ از نو", plain.exists())
    assertEquals(1, store.items.size)
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())

    store.items.clear()
    b.relaunch()
    assertNull(b.open(sealed[0]))
    assertTrue(unavailable(b))
    b.seal("y-test-value")
    assertFalse("هرگز کلیدِ تازه کنارِ نشان", plain.exists())
    assertTrue(os.exists())
  }

  @Test fun `برق در هر گامِ کوچ و پاک کردن — کلید و رازها هرگز گم نمی‌شوند`() {
    var scenarios = 0
    for (junk in listOf(false, true)) for (after in listOf(false, true)) for (cutAt in 1..6) {
      dir.listFiles().orEmpty().forEach { it.delete() }
      val (k, sealed) = phase1()
      if (junk) os.writeText("junk")
      val store = FakeStore()
      val disk = CutDisk(cutAt, after)
      val b = box(store, disk)
      for (i in 0 until 3) {
        try {
          launch(b, sealed)
        } catch (_: PowerCut) {
          break
        }
        if (disk.dead) break
        b.relaunch()
      }
      if (disk.dead) scenarios++
      val where = "گامِ $cutAt ${if (after) "پس" else "پیش"} از کار${if (junk) "، با نشانِ خراب" else ""}"
      //  پس از برق: هر نسخه‌ای که مانده همان کلید است، رازها همان‌اند و کوچ تمام می‌شود
      val again = box(store)
      repeat(3) {
        launch(again, sealed)
        assertFalse(where, again.lost)
        again.relaunch()
      }
      launch(again, sealed)
      assertFalse("کوچ پس از برق تمام شد — $where", plain.exists())
      assertTrue(where, os.exists())
      assertArrayEquals(where, k, store.items.values.single())
    }
    //  همان گام‌های `KekVaultTest`: بی نشان ۳ گامِ نوشتنی، با نشانِ خراب ۴؛ هر کدام پیش و پس از کار
    assertEquals(2 * 3 + 2 * 4, scenarios)
  }

  @Test fun `برق درست پس از سپردن به کلیددان — اجرای بعد همان حساب را دوباره می‌نویسد`() {
    val (_, sealed) = phase1()
    val store = FakeStore().apply { cutAfterStore = true }
    try {
      box(store).open(sealed[0])
      fail("برق نرفت")
    } catch (_: PowerCut) {
    }
    assertFalse(os.exists())
    assertTrue(plain.exists())
    val b = box(store)
    launch(b, sealed)
    assertTrue(os.exists())
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())
    assertEquals(1, store.items.size)
  }

  @Test fun `پرچم خاموش پس از کوچ — فایلِ ساده با همان کلید برمی‌گردد و رازها همان‌اند`() {
    val (k, sealed) = phase1()
    val store = FakeStore()
    val b = launch(box(store), sealed)
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())

    flag = false
    b.relaunch()
    launch(b, sealed)
    assertArrayEquals("فایلِ ساده برگشت", k, plain.readBytes())
    assertEquals(KekVault.Status.Off, b.vaultStatus)
    val reads = store.unprotects
    b.relaunch()
    launch(b, sealed)
    assertEquals("خاموش و فایل هست ⇒ هیچ تماسی با کلیددان", reads, store.unprotects)
    assertTrue("نسخهٔ سیستم پاک نمی‌شود", os.exists() && store.items.isNotEmpty())

    //  دوباره روشن: همان نشان، بی کوچِ تازه
    flag = true
    val protects = store.protects
    b.relaunch(); launch(b, sealed)
    b.relaunch(); launch(b, sealed)
    assertEquals(protects, store.protects)
    assertFalse(plain.exists())
  }

  @Test fun `نشانِ خراب — با فایل کنار می‌رود نه رونویسی؛ بی فایل «همین حالا نه» و هرگز کلیدِ تازه`() {
    val (_, sealed) = phase1()
    os.writeText("junk")
    val store = FakeStore()
    val b = launch(box(store), sealed)
    assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("${slot.os}.old-") && it.readText() == "junk" })
    assertTrue(os.exists())
    assertTrue(plain.exists())

    dir.listFiles().orEmpty().forEach { it.delete() }
    os.writeText("junk")
    b.relaunch()
    assertNull(b.open(sealed[0]))
    assertTrue("نشانِ خراب بی فایل ⇒ «دوباره وارد شوید»", b.lost)
    assertTrue(unavailable(b))
    assertTrue(b.seal("z-test-value").startsWith("m1:"))
    assertFalse("کلیدِ تازه کنارِ نشانِ خراب ساخته نشد", plain.exists())
    assertEquals("junk", os.readText())
    assertFalse(vault(store).retireBrokenPlain())
  }

  @Test fun `نشانِ بیگانه — نشانِ کلیدِ دفتر یا سیستمِ دیگر هرگز کلیدِ نشست خوانده نمی‌شود`() {
    //  نشانِ KEK ِ دفتر در همان پوشه (همان کلیددان) کپی شد جای نشانِ نشست
    val (_, sealed) = phase1()
    val store = FakeStore()
    val ledger = KekVault(dir, flag = { true }, store = { store }, random = random)
    ledger.forWrite()
    val ledgerMarker = File(dir, KekVault.OS).readBytes()
    assertNull("سرآیندِ دیگر", KekVault.Descriptor.parse(ledgerMarker, slot.header))
    os.writeBytes(ledgerMarker)
    //  با فایلِ ساده: همان فایل کلید است، نشانِ بیگانه کنار می‌رود و کوچ درست انجام می‌شود
    val b = launch(box(store), sealed)
    assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("${slot.os}.old-") })
    assertTrue(os.readText().startsWith("tohid-session-os v1"))
    b.relaunch(); launch(b, sealed)
    assertFalse(plain.exists())

    //  بی فایلِ ساده: نشانِ بیگانه ⇒ «همین حالا نه»، هرگز کلیدِ دفتر و هرگز کلیدِ تازه
    os.writeBytes(ledgerMarker)
    b.relaunch()
    assertNull(b.open(sealed[0]))
    assertTrue(unavailable(b))
    assertFalse(plain.exists())

    //  نشانِ سیستمِ دیگر (پوشهٔ داده از ویندوز به مک آمد) و فایلی نیست ⇒ همان «همین حالا نه»
    dir.listFiles().orEmpty().forEach { it.delete() }
    val (_, sealed2) = phase1()
    val dpapi = FakeStore(id = "dpapi", inBlob = true)
    val w = launch(box(dpapi), sealed2)
    w.relaunch(); launch(w, sealed2)
    assertFalse(plain.exists())
    val mac = box(FakeStore(id = "keychain"))
    assertNull(mac.open(sealed2[0]))
    assertTrue(unavailable(mac))
    assertFalse(plain.exists())
    assertTrue(os.readText().contains("store=dpapi"))
  }

  @Test fun `مدخلِ جدا — کلیدِ نشست و کلیدِ دفتر در یک پوشه و یک کلیددان هیچ‌کدام جای دیگری را نمی‌گیرند`() {
    val store = FakeStore()
    val ledger = KekVault(dir, flag = { true }, store = { store }, random = random)
    val kek = ledger.forWrite()
    val b = box(store)
    val sealed = secrets.map { b.seal(it) }
    val session = plain.readBytes()
    assertFalse(kek.contentEquals(session))
    assertEquals(setOf("atrest-kek.key", "atrest-kek.os", "session.key", "session.os"), dir.list()!!.toSet())
    assertEquals(2, store.items.size)
    assertTrue(store.items.keys.any { it.startsWith(KekVault.REF_PREFIX) })
    assertTrue(store.items.keys.any { it.startsWith(slot.refPrefix) })
    assertArrayEquals(session, store.items.entries.single { it.key.startsWith(slot.refPrefix) }.value)
    assertNotEquals("دامنهٔ نشانِ جدا", KekVault.Slot.LEDGER.check(kek), slot.check(kek))
    assertNotEquals(OsKeyStores.SERVICE, slot.service)
    launch(b, sealed)
    ledger.opened(kek)
    b.relaunch(); ledger.relaunch()
    launch(b, sealed)
    assertArrayEquals(kek, ledger.forRead().first())
    ledger.opened(kek)
    assertFalse(plain.exists())
    assertFalse(File(dir, KekVault.PLAIN).exists())
    ledger.relaunch()
    assertArrayEquals("کلیدِ دفتر همان ماند", kek, ledger.forRead().single())
  }

  @Test fun `فایلِ کلیدِ نشستِ خراب بی هیچ نشانی — کنار می‌رود و کلیدِ تازه؛ KEK ِ دفتر هرگز`() {
    plain.writeBytes(ByteArray(5) { 7 })
    val store = FakeStore()
    val b = box(store)
    assertEquals(SessionBox.State.Empty, b.state())
    assertTrue("کنار رفت، نه پاک", dir.listFiles().orEmpty().any { it.name.startsWith("${slot.plain}.bad-") && it.length() == 5L })
    val sealed = secrets.map { b.seal(it) }
    assertEquals(32, plain.readBytes().size)
    b.relaunch(); launch(b, sealed)

    //  همان فایلِ خراب برای KEK ِ دفتر: دست نمی‌خورد
    val ledgerPlain = File(dir, KekVault.PLAIN).apply { writeBytes(ByteArray(5) { 7 }) }
    assertFalse(KekVault(dir, flag = { true }, store = { store }, random = random).retireBrokenPlain())
    assertEquals(5L, ledgerPlain.length())
  }

  @Test fun `نشان — قالبِ نشست جدا و قالبِ دیگر یا بیش‌ازاندازه پذیرفته نمی‌شود`() {
    val k = ByteArray(32).also { random.nextBytes(it) }
    val good = KekVault.Descriptor("keychain", slot.check(k), "session-key-0011", "", "ab12", slot.header).encode()
    assertTrue(String(good).startsWith("tohid-session-os v1\n"))
    val d = KekVault.Descriptor.parse(good, slot.header)!!
    assertEquals("session-key-0011", d.ref)
    assertNull("نشانِ نشست نشانِ دفتر نیست", KekVault.Descriptor.parse(good))
    assertNull(KekVault.Descriptor.parse("junk".toByteArray(), slot.header))
    assertNull(KekVault.Descriptor.parse(good + ByteArray(20_000) { 'a'.code.toByte() }, slot.header))
    assertEquals(32, slot.check(k).length)
    assertEquals(slot.check(k), slot.check(k.copyOf()))
  }

  /** همان رمزِ `SecretBox` ِ پیش از این PR، جدا نوشته‌شده تا شکلِ روی دیسک سنجیده شود */
  private fun legacySeal(key: ByteArray, text: String): String {
    val iv = ByteArray(12).also { random.nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }
    return "v1:" + Base64.getEncoder().encodeToString(iv + c.doFinal(text.toByteArray(Charsets.UTF_8)))
  }
}
