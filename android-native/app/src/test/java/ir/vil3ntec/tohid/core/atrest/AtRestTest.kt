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

  private class TestKek(override val id: String = "test:kek") : AtRestKek {
    private val random = SecureRandom()
    private val key = SecretKeySpec(ByteArray(32).also { random.nextBytes(it) }, "AES")
    var gone = false
    var flaky = false
    var wrapFails = false

    private fun check() {
      if (gone) throw AtRestException(AtRestException.Kind.KEY_MISSING, "gone")
      if (flaky) throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "flaky")
    }

    override fun wrap(dek: ByteArray): ByteArray {
      check()
      if (wrapFails) throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "wrap")
      val iv = ByteArray(12).also { random.nextBytes(it) }
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
      return iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray): ByteArray {
      check()
      return try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
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

    override fun replace(from: File, to: File) {
      Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun syncDir(dir: File) = Unit

    override fun delete(f: File): Boolean = f.delete()
  }

  private val io = JvmFiles()

  private fun ring(dir: File = tmp.root, kek: AtRestKek = TestKek()) = AtRestKeyring(File(dir, "keyring"), kek, io)

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
    val r = ring(kek = kek)
    assertTrue(r.state() is AtRestKeyring.State.Lost)
    assertEquals(AtRestException.Kind.KEY_MISSING, kinds { AtRestCodec(r).open(sealed) })
    assertArrayEquals("خواندن کلیددان را عوض نمی‌کند", before, keyring.readBytes())

    kek.gone = false // KEKِ تازه (مثلِ نصبِ دوباره)؛ کلیددانِ قبلی با آن باز نمی‌شود
    val fresh = AtRestKeyring(keyring, TestKek(), io)
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
    val lost = AtRestCodec(ring(kek = kek))
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
}
