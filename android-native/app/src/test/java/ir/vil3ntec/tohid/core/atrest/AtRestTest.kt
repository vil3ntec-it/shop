package ir.vil3ntec.tohid.core.atrest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  SEC-01 فاز ۱ — رمزِ دفتر روی دیسک: رفت‌وبرگشت، کلیدِ اشتباه، فایلِ خراب،
 *  کلیددان، و کوچی که در هر گامش «برق می‌رود».
 *
 *  همه روی JVM، بی اندروید: KEK این‌جا یک کلیدِ AES در حافظه است که می‌شود
 *  «گمش کرد» (`gone`) یا «گذرا از کار انداخت» (`flaky`) — همان دو رفتاری که
 *  Keystore واقعاً دارد.
 */
class AtRestTest {

  @get:Rule val tmp = TemporaryFolder()

  /* ----------------------------- ابزار ----------------------------- */

  private class TestKek(initial: String = "test:kek") : AtRestKek {
    private val random = SecureRandom()
    private val keys = LinkedHashMap<String, SecretKeySpec>()
    /** KEKِ امروز — عوض کردنش همان «نامِ تازه»ِ اندروید است پس از شکستِ همیشگی */
    var current = initial
    override val id: String get() = current
    var gone = false
    /** این شناسه‌ها KEY_MISSING می‌دهند (و برداشتنشان یعنی «گذرا بود») */
    val broken = HashSet<String>()
    var flaky = false
    var wrapFails = false

    private fun key(id: String) = keys.getOrPut(id) { SecretKeySpec(ByteArray(32).also { random.nextBytes(it) }, "AES") }

    init { key(initial) }

    private fun check(kekId: String) {
      if (gone || kekId in broken) throw AtRestException(AtRestException.Kind.KEY_MISSING, "gone")
      if (flaky) throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "flaky")
    }

    override fun wrap(dek: ByteArray): ByteArray {
      check(current)
      if (wrapFails) throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "wrap")
      val iv = ByteArray(12).also { random.nextBytes(it) }
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key(current), GCMParameterSpec(128, iv))
      return iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray, kekId: String): ByteArray {
      check(kekId)
      val k = keys[kekId] ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "unknown kek $kekId")
      return try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, blob, 0, 12))
        c.doFinal(blob, 12, blob.size - 12)
      } catch (e: AEADBadTagException) {
        throw AtRestException(AtRestException.Kind.KEY_MISSING, "bad tag", e)
      }
    }
  }

  /** «برق رفت» — نه یک خطای عادی؛ هیچ پاک‌کاری‌ای پس از آن نمی‌دود */
  private class PowerLoss : RuntimeException("power loss")

  /** همان کارهای `DiskWrite` روی JVM، با جایی برای قطعِ برق وسطِ نوشتن */
  private class JvmFiles : AtRestFiles {
    /** اگر پر است: نیمی از بایت‌ها نوشته می‌شود و برق می‌رود */
    var cutWriteOf: String? = null
    /** اگر پر است: بایتی از فایلِ تازه پس از نوشتن خراب می‌شود (دیسکِ بد) */
    var rotWriteOf: String? = null

    override fun read(f: File): ByteArray = f.readBytes()

    override fun writeSynced(f: File, bytes: ByteArray) {
      FileOutputStream(f).use { out ->
        if (cutWriteOf != null && f.name.startsWith(cutWriteOf!!)) {
          out.write(bytes, 0, bytes.size / 2)
          out.fd.sync()
          throw PowerLoss()
        }
        val data = if (rotWriteOf != null && f.name.startsWith(rotWriteOf!!)) bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() } else bytes
        out.write(data)
        out.flush()
        out.fd.sync()
      }
    }

    /** اگر پر است: جابه‌جایی به نامی که این را دارد شکست می‌خورد (دیسکِ پر، قفلِ ویندوز) */
    var failReplaceTo: String? = null

    override fun replace(from: File, to: File) {
      if (failReplaceTo != null && to.name.contains(failReplaceTo!!)) throw java.io.IOException("replace refused")
      Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun syncDir(dir: File) = Unit

    override fun delete(f: File): Boolean = f.delete()
  }

  private val io = JvmFiles()

  private fun ring(dir: File = tmp.root, kek: AtRestKek = TestKek()) = AtRestKeyring(File(dir, "keyring"), kek, io)

  /** «اجرای دوم»: `Lost` فقط وقتی اعلام می‌شود که اجرای پیشین هم همان شکست را دیده باشد */
  private fun secondLaunch(make: () -> AtRestKeyring): AtRestKeyring {
    make().state()
    return make()
  }

  private fun kinds(block: () -> Unit): AtRestException.Kind {
    try {
      block()
    } catch (e: AtRestException) {
      return e.kind
    }
    fail("باید AtRestException می‌داد")
    error("unreachable")
  }

  private val ledgerJson = """{"products":[{"id":"p1","name":"برنج ممتاز","salePrice":120.0}],"sales":[{"id":"s1","total":3500}],"debtors":[{"id":"d1","name":"آقای احمدی","phone":"0700123456"}]}"""

  private fun file(name: String, text: String = ledgerJson): File =
    File(tmp.root, name).apply { writeText(text) }

  /* ------------------------- پاکت: رفت‌وبرگشت ------------------------- */

  @Test fun `رفت‌وبرگشت — خالی، یک بایت، متنِ فارسی، یک مگابایت`() {
    val codec = AtRestCodec(ring())
    val big = ByteArray(1 shl 20).also { SecureRandom().nextBytes(it) }
    for (plain in listOf(ByteArray(0), byteArrayOf(42), ledgerJson.toByteArray(), big)) {
      val sealed = codec.seal(plain)
      assertTrue(AtRestEnvelope.isSealed(sealed))
      assertArrayEquals(plain, codec.open(sealed))
    }
  }

  @Test fun `متنِ ساده در فایلِ رمزدار پیدا نیست و دو رمزِ یک متن یکی نیستند`() {
    val codec = AtRestCodec(ring())
    val a = codec.seal(ledgerJson.toByteArray())
    val b = codec.seal(ledgerJson.toByteArray())
    val text = String(a, Charsets.ISO_8859_1)
    assertFalse(text.contains("products"))
    assertFalse(String(a, Charsets.UTF_8).contains("احمدی"))
    assertFalse(a.contentEquals(b))
    assertArrayEquals(codec.open(a), codec.open(b))
  }

  @Test fun `فایلِ ساده (JSON) هرگز پاکت شمرده نمی‌شود و همان‌طور باز می‌شود`() {
    val codec = AtRestCodec(ring())
    for (s in listOf(ledgerJson, "﻿{}", "  {\"a\":1}", "[]", "")) {
      val b = s.toByteArray()
      assertFalse(AtRestEnvelope.isSealed(b))
      assertArrayEquals(b, codec.open(b))
    }
    val f = file("shop-data.json")
    assertFalse(AtRestEnvelope.isSealed(f))
  }

  /* --------------------------- کلیدِ اشتباه --------------------------- */

  @Test fun `فایلِ دستگاهِ دیگر — کلیدش این‌جا نیست، KEY_MISSING و هیچ داده‌ای`() {
    val sealed = AtRestCodec(ring(tmp.newFolder("a"))).seal(ledgerJson.toByteArray())
    val other = AtRestCodec(ring(tmp.newFolder("b")))
    other.seal(byteArrayOf(1)) // کلیدِ خودش را دارد
    assertEquals(AtRestException.Kind.KEY_MISSING, kinds { other.open(sealed) })
  }

  @Test fun `همان شناسهٔ کلید با بایت‌های دیگر — AUTH_FAILED`() {
    val r = ring()
    val (kid, _) = r.forWrite()!!
    val forged = AtRestEnvelope.seal(ledgerJson.toByteArray(), kid, SecretKeySpec(ByteArray(32), "AES"), SecureRandom())
    assertEquals(AtRestException.Kind.AUTH_FAILED, kinds { AtRestCodec(r).open(forged) })
  }

  /* ---------------------------- فایلِ خراب ---------------------------- */

  @Test fun `فایلِ دست‌خورده یا بریده هرگز «باز» نمی‌شود`() {
    val codec = AtRestCodec(ring())
    val good = codec.seal(ledgerJson.toByteArray())
    val kidAt = AtRestEnvelope.MAGIC.size + 2
    fun flip(i: Int) = good.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }

    assertEquals(AtRestException.Kind.AUTH_FAILED, kinds { codec.open(flip(good.size - 1)) }) // برچسب
    assertEquals(AtRestException.Kind.AUTH_FAILED, kinds { codec.open(flip(good.size - 40)) }) // متن
    assertEquals(AtRestException.Kind.AUTH_FAILED, kinds { codec.open(flip(kidAt + 18)) }) // nonce
    assertEquals(AtRestException.Kind.KEY_MISSING, kinds { codec.open(flip(kidAt + 1)) }) // شناسهٔ کلید
    assertEquals(AtRestException.Kind.UNSUPPORTED, kinds { codec.open(good.copyOf().also { it[AtRestEnvelope.MAGIC.size] = 2 }) })
    assertEquals(AtRestException.Kind.MALFORMED, kinds { codec.open(good.copyOf(AtRestEnvelope.MAGIC.size + 1)) })
    assertEquals(AtRestException.Kind.MALFORMED, kinds { codec.open(good.copyOf(kidAt + 20)) })
    assertEquals(AtRestException.Kind.AUTH_FAILED, kinds { codec.open(good.copyOf(good.size - 1)) })
  }

  /* ----------------------------- کلیددان ----------------------------- */

  @Test fun `کلیددان روی دیسک می‌ماند — «بستن و باز کردن» همان کلید را دارد`() {
    val kek = TestKek()
    val first = ring(kek = kek)
    val sealed = AtRestCodec(first).seal(ledgerJson.toByteArray())
    val again = ring(kek = kek) // نمونهٔ تازه، فقط از روی دیسک
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(again).open(sealed))
    assertEquals(first.state(), again.state())
    assertFalse(File(tmp.root, "keyring.new").exists())
  }

  @Test fun `KEK برای همیشه رفت — Lost، کلیددان دست نمی‌خورد تا کسی بخواهد بنویسد، و آن‌وقت کنار می‌رود نه پاک`() {
    val kek = TestKek()
    val sealed = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    val keyring = File(tmp.root, "keyring")
    val before = keyring.readBytes()

    kek.gone = true
    val r = secondLaunch { ring(kek = kek) }
    assertTrue(r.state() is AtRestKeyring.State.Lost)
    assertEquals(AtRestException.Kind.KEY_MISSING, kinds { AtRestCodec(r).open(sealed) })
    assertArrayEquals("خواندن کلیددان را عوض نمی‌کند", before, keyring.readBytes())

    kek.gone = false // KEKِ تازه (مثلِ نصبِ دوباره)؛ کلیددانِ قبلی با آن باز نمی‌شود
    val newKek = TestKek()
    val fresh = secondLaunch { AtRestKeyring(keyring, newKek, io) }
    assertTrue(fresh.state() is AtRestKeyring.State.Lost)
    val (newKid, _) = fresh.forWrite()!!
    val aside = fresh.setAsideAs!!
    assertArrayEquals("کلیددانِ ازدست‌رفته کنار رفت، بایت‌به‌بایت", before, aside.readBytes())
    assertNotEquals(AtRestEnvelope.keyIdOf(sealed), newKid)
    assertEquals(AtRestException.Kind.KEY_MISSING, kinds { AtRestCodec(fresh).open(sealed) })
  }

  @Test fun `Keystore گذرا جواب نداد — KEY_UNAVAILABLE، کلیدِ تازه ساخته نمی‌شود، بعد همان کلید برمی‌گردد`() {
    val kek = TestKek()
    val sealed = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    val before = File(tmp.root, "keyring").readBytes()

    kek.flaky = true
    val r = ring(kek = kek)
    assertTrue(r.state() is AtRestKeyring.State.Unavailable)
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kinds { AtRestCodec(r).open(sealed) })
    assertNull("خطای گذرا هرگز کلیدِ تازه نمی‌سازد", r.forWrite())
    assertArrayEquals(before, File(tmp.root, "keyring").readBytes())

    kek.flaky = false
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(r).open(sealed))
  }

  @Test fun `ساختنِ کلید نشد — هیچ کلیددانی نمی‌ماند و رمز KEY_UNAVAILABLE می‌دهد (صدازننده ساده می‌نویسد)`() {
    val kek = TestKek().apply { wrapFails = true }
    val r = ring(kek = kek)
    assertNull(r.forWrite())
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kinds { AtRestCodec(r).seal(byteArrayOf(1)) })
    assertFalse(File(tmp.root, "keyring").exists())
    assertFalse(File(tmp.root, "keyring.new").exists())
  }

  @Test fun `برق وسطِ ساختنِ کلیددان رفت — هیچ فایلی با آن کلید رمز نشده و بارِ بعد از نو ساخته می‌شود`() {
    val kek = TestKek()
    io.cutWriteOf = "keyring"
    //  برق یا می‌رود (پرتاب) یا ساختن شکست می‌خورد (null) — در هر دو، هیچ کلیدی بکار نرفته
    val first = runCatching { ring(kek = kek).forWrite() }
    assertTrue(first.isFailure || first.getOrNull() == null)
    io.cutWriteOf = null
    assertFalse(File(tmp.root, "keyring").exists())
    val r = ring(kek = kek)
    assertNotNull(r.forWrite())
    assertArrayEquals(byteArrayOf(7), AtRestCodec(r).open(AtRestCodec(r).seal(byteArrayOf(7))))
  }

  /* ------------------------------- کوچ ------------------------------- */

  @Test fun `کوچ به رمزدار — همان داده، همان زمانِ فایل، بی فایلِ کناری؛ بارِ دوم کاری نیست`() {
    val codec = AtRestCodec(ring())
    val f = file("shop-data.json")
    val mtime = 1_700_000_000_000L
    f.setLastModified(mtime)

    assertEquals(AtRestMigration.Outcome.CONVERTED, AtRestMigration.convert(f, true, codec, io))
    assertTrue(AtRestEnvelope.isSealed(f))
    assertEquals(ledgerJson, String(codec.open(f.readBytes())))
    assertEquals("نگه‌داشتِ نسخه‌ها از زمانِ فایل است", mtime, f.lastModified())
    assertFalse(AtRestMigration.sidecarOf(f).exists())
    assertEquals(AtRestMigration.Outcome.ALREADY, AtRestMigration.convert(f, true, codec, io))

    //  و برگشت
    assertEquals(AtRestMigration.Outcome.CONVERTED, AtRestMigration.convert(f, false, codec, io))
    assertEquals(ledgerJson, f.readText())
    assertEquals(mtime, f.lastModified())
    assertEquals(AtRestMigration.Outcome.ABSENT, AtRestMigration.convert(File(tmp.root, "nope.json"), true, codec, io))
  }

  @Test fun `برق در هر گامِ کوچ می‌رود — فایل همیشه خواندنی و درست است و اجرای دوباره تمامش می‌کند`() {
    for (toSealed in listOf(true, false)) {
      for (step in AtRestMigration.Step.values()) {
        val dir = tmp.newFolder("crash-$toSealed-$step")
        val codec = AtRestCodec(ring(dir))
        val f = File(dir, "shop-data.json")
        if (toSealed) f.writeText(ledgerJson) else f.writeBytes(codec.seal(ledgerJson.toByteArray()))

        try {
          AtRestMigration.convert(f, toSealed, codec, io) { if (it == step) throw PowerLoss() }
          fail("برق باید در $step می‌رفت")
        } catch (_: PowerLoss) {
        }

        //  پس از قطعِ برق: فایلِ اصلی کامل است — یا قالبِ قبلی یا قالبِ تازه
        assertTrue("$toSealed/$step", f.isFile)
        assertEquals("$toSealed/$step", ledgerJson, String(codec.open(f.readBytes())))
        if (step == AtRestMigration.Step.REPLACED) assertEquals(toSealed, AtRestEnvelope.isSealed(f))
        else assertEquals(!toSealed, AtRestEnvelope.isSealed(f))

        //  بازشدنِ بعدی برنامه
        val again = AtRestMigration.convert(f, toSealed, codec, io)
        assertTrue("$toSealed/$step: $again", again != AtRestMigration.Outcome.ABSENT)
        assertEquals(toSealed, AtRestEnvelope.isSealed(f))
        assertEquals(ledgerJson, String(codec.open(f.readBytes())))
        assertFalse("$toSealed/$step: کناری نماند", AtRestMigration.sidecarOf(f).exists())
      }
    }
  }

  @Test fun `برق وسطِ نوشتنِ فایلِ کناری رفت — اصلی دست‌نخورده، کناریِ نصفه بارِ بعد پاک می‌شود`() {
    val codec = AtRestCodec(ring())
    val f = file("shop-data.json")
    io.cutWriteOf = "shop-data.json"
    try {
      AtRestMigration.convert(f, true, codec, io)
      fail()
    } catch (_: PowerLoss) {
    }
    io.cutWriteOf = null
    assertEquals(ledgerJson, f.readText())
    assertTrue(AtRestMigration.sidecarOf(f).exists())

    assertEquals(AtRestMigration.Outcome.CONVERTED, AtRestMigration.convert(f, true, codec, io))
    assertEquals(ledgerJson, String(codec.open(f.readBytes())))
    assertFalse(AtRestMigration.sidecarOf(f).exists())
  }

  @Test fun `بازبینی پس از نوشتن نخورد (دیسکِ بد) — اصلی دست نمی‌خورد و کناری پاک می‌شود`() {
    val codec = AtRestCodec(ring())
    val f = file("shop-data.json")
    io.rotWriteOf = "shop-data.json"
    assertEquals(AtRestException.Kind.AUTH_FAILED, kinds { AtRestMigration.convert(f, true, codec, io) })
    io.rotWriteOf = null
    assertEquals(ledgerJson, f.readText())
    assertFalse(AtRestMigration.sidecarOf(f).exists())
  }

  @Test fun `برگشت به ساده بی کلید — شکست با نوعِ روشن و فایلِ رمزدار بایت‌به‌بایت سرِ جایش`() {
    val kek = TestKek()
    val codec = AtRestCodec(ring(kek = kek))
    val f = File(tmp.root, "shop-data.json").apply { writeBytes(codec.seal(ledgerJson.toByteArray())) }
    val before = f.readBytes()
    kek.gone = true
    val lost = AtRestCodec(secondLaunch { ring(kek = kek) })
    assertEquals(AtRestException.Kind.KEY_MISSING, kinds { AtRestMigration.convert(f, false, lost, io) })
    assertArrayEquals(before, f.readBytes())
    assertFalse(AtRestMigration.sidecarOf(f).exists())
  }

  @Test fun `کوچِ چند فایل — شکستِ یکی بقیه را نمی‌گیرد و هیچ فایلی را خراب نمی‌کند`() {
    val codec = AtRestCodec(ring(tmp.newFolder("mine")))
    val foreign = File(tmp.root, "ledger-other.json").apply {
      writeBytes(AtRestCodec(ring(tmp.newFolder("theirs"))).seal(ledgerJson.toByteArray()))
    }
    val plain = file("shop-data.json")
    val already = File(tmp.root, "ledger-archive.json").apply { writeBytes(codec.seal("{}".toByteArray())) }
    val foreignBefore = foreign.readBytes()

    val toPlain = AtRestMigration.convertAll(listOf(plain, already, foreign, File(tmp.root, "absent.json")), false, codec, io)
    assertEquals(1, toPlain.converted) // ledger-archive.json
    assertEquals(1, toPlain.already) // shop-data.json
    assertEquals(listOf("ledger-other.json" to AtRestException.Kind.KEY_MISSING), toPlain.failed)
    assertFalse(toPlain.ok)
    assertArrayEquals(foreignBefore, foreign.readBytes())

    val toSealed = AtRestMigration.convertAll(listOf(plain, already, foreign), true, codec, io)
    assertEquals(2, toSealed.converted)
    assertEquals(1, toSealed.already) // فایلِ بیگانه رمزدار است؛ دست نمی‌خورد
    assertTrue(toSealed.ok)
    assertArrayEquals(foreignBefore, foreign.readBytes())
  }

  /* ------------------------- بازبینیِ #331 ------------------------- */

  @Test fun `کنار گذاشتنِ کلیددانِ ازدست‌رفته نشد — کلیدِ تازه ساخته نمی‌شود و کلیددانِ قبلی بایت‌به‌بایت می‌ماند`() {
    val kek = TestKek()
    AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    val keyring = File(tmp.root, "keyring")
    val before = keyring.readBytes()
    val other = TestKek()
    val fresh = secondLaunch { AtRestKeyring(keyring, other, io) } // KEKِ دیگر ⇒ Lost در اجرای دوم
    assertTrue(fresh.state() is AtRestKeyring.State.Lost)

    io.failReplaceTo = ".lost-"
    assertNull("بی کنار رفتن هیچ کلیدی ساخته نمی‌شود", fresh.forWrite())
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kinds { AtRestCodec(fresh).seal(byteArrayOf(1)) })
    assertArrayEquals("کلیددانِ قبلی رونویسی نشد", before, keyring.readBytes())
    assertFalse(File(tmp.root, "keyring.new").exists())

    io.failReplaceTo = null
    assertNotNull(fresh.forWrite())
    assertArrayEquals(before, fresh.setAsideAs!!.readBytes())
  }

  @Test fun `یک بایتِ خراب در کلیددان — از نسخهٔ دوم باز می‌شود، خراب کنار می‌رود و اصلی درمان می‌شود`() {
    val kek = TestKek()
    val sealed = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    val keyring = File(tmp.root, "keyring")
    val good = keyring.readBytes()
    assertArrayEquals("نسخهٔ دوم همان بایت‌ها", good, File(tmp.root, "keyring" + AtRestKeyring.BACKUP_SUFFIX).readBytes())

    val text = keyring.readText()
    val i = text.indexOf("wrapped=") + 10
    keyring.writeText(text.substring(0, i) + (if (text[i] == '0') '1' else '0') + text.substring(i + 1))

    val r = ring(kek = kek)
    assertTrue(r.state() is AtRestKeyring.State.Ready)
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(r).open(sealed))
    assertArrayEquals("اصلی از نسخهٔ دوم برگشت", good, keyring.readBytes())
    assertTrue("خراب کنار رفت، پاک نشد", tmp.root.listFiles().orEmpty().any { it.name.startsWith("keyring.corrupt-") })
  }

  @Test fun `نسخهٔ دوم خراب یا نبود — از اصلی باز می‌شود و نسخهٔ دوم از نو نوشته می‌شود`() {
    val kek = TestKek()
    val sealed = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    val good = File(tmp.root, "keyring").readBytes()
    val second = File(tmp.root, "keyring" + AtRestKeyring.BACKUP_SUFFIX)
    second.writeText("garbage")
    val r = ring(kek = kek)
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(r).open(sealed))
    assertArrayEquals(good, second.readBytes())

    assertTrue(second.delete())
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(ring(kek = kek)).open(sealed))
    assertArrayEquals(good, second.readBytes())
  }

  @Test fun `اصلی نیست ولی نسخهٔ دوم هست — همان کلید، نه کلیدِ تازه`() {
    val kek = TestKek()
    val sealed = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    assertTrue(File(tmp.root, "keyring").delete())
    val r = ring(kek = kek)
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(r).open(sealed))
    assertTrue(File(tmp.root, "keyring").exists())
  }

  @Test fun `هر دو نسخه خراب — Lost؛ با نوشتن هر دو کنار می‌روند`() {
    val kek = TestKek()
    AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    File(tmp.root, "keyring").writeText("x")
    File(tmp.root, "keyring" + AtRestKeyring.BACKUP_SUFFIX).writeText("y")
    val r = secondLaunch { ring(kek = kek) }
    assertTrue(r.state() is AtRestKeyring.State.Lost)
    assertNotNull(r.forWrite())
    val names = tmp.root.listFiles().orEmpty().map { it.name }
    assertTrue(names.any { it.startsWith("keyring.lost-") })
    assertTrue(names.any { it.startsWith("keyring.bak.lost-") })
  }

  @Test fun `شکستِ همیشگیِ Keystore از گذرا جداست — UnrecoverableKey و «keyblob» ِ کلی گذرایند`() {
    assertTrue(AtRestKeyFailure.isPermanent(java.security.KeyStoreException("Invalid key blob (internal Keystore code: -33)")))
    assertTrue(AtRestKeyFailure.isPermanent(java.security.InvalidKeyException("x", java.security.KeyStoreException("Invalid key blob"))))
    assertTrue(AtRestKeyFailure.isPermanent(java.security.InvalidKeyException("Key permanently invalidated")))
    //  AndroidKeyStore همین را برای SYSTEM_ERROR ِ گذرا هم می‌دهد
    assertFalse(AtRestKeyFailure.isPermanent(java.security.UnrecoverableKeyException("Failed to obtain information about key")))
    assertFalse(AtRestKeyFailure.isPermanent(java.security.InvalidKeyException("wrapped", java.security.UnrecoverableKeyException())))
    assertFalse(AtRestKeyFailure.isPermanent(java.security.KeyStoreException("Failed to load keyblob")))
    assertFalse(AtRestKeyFailure.isPermanent(java.security.KeyStoreException("System error")))
    assertFalse(AtRestKeyFailure.isPermanent(java.security.ProviderException("Keystore operation failed")))
    assertFalse(AtRestKeyFailure.isPermanent(IllegalStateException("busy")))
  }

  @Test fun `شکستِ همیشگی‌نما در اجرای نخست — هیچ چیزی کنار نمی‌رود و کلیدِ تازه ساخته نمی‌شود؛ گذرا بود ⇒ همان کلید`() {
    val kek = TestKek()
    val sealed = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    val before = File(tmp.root, "keyring").readBytes()
    kek.gone = true
    val first = ring(kek = kek)
    assertTrue(first.state() is AtRestKeyring.State.Unavailable)
    assertNull("اجرای نخست کلیدِ تازه نمی‌سازد", first.forWrite())
    assertEquals(AtRestException.Kind.KEY_UNAVAILABLE, kinds { AtRestCodec(first).open(sealed) })
    assertArrayEquals(before, File(tmp.root, "keyring").readBytes())
    assertTrue(tmp.root.listFiles().orEmpty().none { it.name.contains(".lost-") })

    kek.gone = false // اجرای دوم: گذرا بود
    val second = ring(kek = kek)
    assertArrayEquals(ledgerJson.toByteArray(), AtRestCodec(second).open(sealed))
    assertFalse("نشانِ «مشکوک» پاک شد", File(tmp.root, "keyring" + AtRestKeyring.SUSPECT_SUFFIX).exists())
  }

  @Test fun `Lost اعلام شد ولی KEKِ قدیمی برگشت — فایلِ رمزشده با کلیددانِ کنارگذاشته هنوز باز می‌شود`() {
    val kek = TestKek("kek-a")
    val old = AtRestCodec(ring(kek = kek)).seal(ledgerJson.toByteArray())
    kek.broken += "kek-a"
    kek.current = "kek-b" // اندروید: نامِ تازه، کلیدِ قدیمی پاک نمی‌شود
    val lost = secondLaunch { ring(kek = kek) }
    assertTrue(lost.state() is AtRestKeyring.State.Lost)
    val fresh = AtRestCodec(lost).seal("{}".toByteArray())
    assertNotEquals(AtRestEnvelope.keyIdOf(old), AtRestEnvelope.keyIdOf(fresh))

    kek.broken -= "kek-a" // گذرا بود
    val later = ring(kek = kek)
    assertArrayEquals("از keyring.lost-*", ledgerJson.toByteArray(), AtRestCodec(later).open(old))
    assertArrayEquals("{}".toByteArray(), AtRestCodec(later).open(fresh))
  }

  @Test fun `صفِ opها و ژورنال‌ها رمز نمی‌شوند؛ با رمزِ روشن، دفتر و حالِ همگام‌سازی با هم از پشتیبانِ اندروید بیرون`() {
    for (n in listOf("oplog-u1.json", "oplog-u1-dropped.json", "sync-journal-u1.json", "sync-v3-u1/queue.json", "ledger-summary.json", "sale-draft.json")) {
      assertFalse(n, AtRestScope.sealable(n))
    }
    for (n in listOf("shop-data.json", "before-restore.json", "ledger-u1.json", "ledger-archive.json", "auto-backups/auto-14050801-1200.json")) {
      assertTrue(n, AtRestScope.sealable(n))
    }
    //  «هر دو یا هیچ‌کدام»: دفتر، کلیددان، صف، ژورنال و خلاصه با هم بیرون
    for (n in listOf(
      "shop-data.json", "ledger-u1.json", "auto-backups/auto-1.json", "atrest/keyring", "atrest/keyring.bak",
      "oplog-u1.json", "sync-journal-u1.json", "sync-v3-u1/state.json", "ledger-summary.json", "shop-data.json.unreadable-1",
    )) assertTrue(n, AtRestScope.skipInDeviceBackup(n))
    for (n in listOf("sale-draft.json", "product-photos/p1.jpg", "chat-outbox/1.json")) assertFalse(n, AtRestScope.skipInDeviceBackup(n))
    assertTrue("tohid-sync-v1.xml" in AtRestScope.SYNC_PREFS)
    //  خاموش و بی فایلِ رمزدار ⇒ همان قاعدهٔ XML
    assertFalse(AtRestScope.activeForBackup(wanted = false, anySealed = false))
    assertTrue(AtRestScope.activeForBackup(wanted = false, anySealed = true))
    assertTrue(AtRestScope.activeForBackup(wanted = true, anySealed = false))
    assertTrue(AtRestScope.leftover("shop-data.json.rebuild-pending"))
  }

  @Test fun `پاکت در یک بافر — اندازهٔ دقیق و همان قالب`() {
    val r = ring()
    val (kid, _) = r.forWrite()!!
    for (n in listOf(0, 1, 4096, 1 shl 20)) {
      val plain = ByteArray(n) { (it % 251).toByte() }
      val sealed = AtRestCodec(r).seal(plain)
      assertEquals(AtRestEnvelope.MAGIC.size + 2 + kid.length + 12 + n + 16, sealed.size)
      assertEquals(kid, AtRestEnvelope.keyIdOf(sealed))
      assertArrayEquals(plain, AtRestCodec(r).open(sealed))
    }
  }
}
