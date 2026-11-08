package ir.vil3ntec.tohid.desktop.keystore

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom

/**
 *  راهِ لینوکس (`secret-tool`) با یک `secret-tool` ِ ساختگی که فرمانِ واقعی را می‌گیرد: کلید **فقط** از stdin می‌رود
 *  (هیچ‌جا در خطِ فرمان نیست)، از stdout برمی‌گردد، «نیست» از «قفل/خطا» جداست، و همان کوچِ کامل. پوسته (`/bin/sh`) لازم
 *  است؛ ویندوز آن را ندارد و DPAPI را `OsKeystoreRoundTripTest` می‌سنجد.
 */
class LinuxSecretToolTest {

  private lateinit var dir: File
  private val random = SecureRandom()

  @Before fun before() {
    Assume.assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))
    Assume.assumeTrue(File("/bin/sh").canExecute())
    dir = Files.createTempDirectory("secret-tool").toFile()
  }

  @After fun after() {
    if (::dir.isInitialized) dir.deleteRecursively()
  }

  /** همان سه فرمانِ `secret-tool`؛ هر خطِ فرمان در `argv.log` */
  private fun tool(locked: Boolean = false): File {
    val bin = File(dir, "bin").apply { mkdirs() }
    val f = File(bin, "secret-tool")
    f.writeText(
      """
      |#!/bin/sh
      |here=${'$'}(dirname "${'$'}0")
      |echo "${'$'}@" >> "${'$'}here/argv.log"
      |${if (locked) "echo 'Cannot unlock collection' >&2; exit 1" else ""}
      |cmd="${'$'}1"; shift
      |acc=""
      |while [ ${'$'}# -gt 0 ]; do if [ "${'$'}1" = account ]; then acc="${'$'}2"; fi; shift; done
      |mkdir -p "${'$'}here/store"
      |case "${'$'}cmd" in
      |  store) cat > "${'$'}here/store/${'$'}acc" ;;
      |  lookup) if [ -f "${'$'}here/store/${'$'}acc" ]; then cat "${'$'}here/store/${'$'}acc"; else exit 1; fi ;;
      |  clear) rm -f "${'$'}here/store/${'$'}acc" ;;
      |esac
      |""".trimMargin(),
    )
    assertTrue(f.setExecutable(true))
    return f
  }

  private fun argv(): String = File(File(dir, "bin"), "argv.log").takeIf { it.exists() }?.readText().orEmpty()

  @Test fun `کلید فقط از stdin می‌رود و همان برمی‌گردد`() {
    val t = LinuxSecretTool(tool())
    val key = ByteArray(32).also { random.nextBytes(it) }
    val hex = LinuxSecretTool.hex(key)
    assertEquals("", t.protect("atrest-kek-0011", key))
    assertEquals(hex, File(File(File(dir, "bin"), "store"), "atrest-kek-0011").readText())
    assertArrayEquals(key, t.unprotect("atrest-kek-0011", ""))
    assertFalse("کلید در خطِ فرمان نیست", argv().contains(hex))
    assertTrue(argv().contains("store --label=My Shop data key service ${OsKeyStores.SERVICE} account atrest-kek-0011"))

    t.remove("atrest-kek-0011")
    try {
      t.unprotect("atrest-kek-0011", "")
      fail("پاک شده بود")
    } catch (e: KeyStoreFailure) {
      assertTrue("بی پیامِ خطا یعنی «نیست»", e.notFound)
    }
  }

  @Test fun `قفل یا خطا — شکست، نه «نیست»`() {
    val t = LinuxSecretTool(tool(locked = true))
    val key = ByteArray(32).also { random.nextBytes(it) }
    for (call in listOf({ t.protect("atrest-kek-0022", key) }, { t.unprotect("atrest-kek-0022", "") })) {
      try {
        call()
        fail("قفل بود")
      } catch (e: KeyStoreFailure) {
        assertFalse(e.notFound)
        assertTrue(e.reason, e.reason.contains("Cannot unlock collection"))
      }
    }
  }

  @Test fun `پیدا کردن در PATH و کوچِ کامل با همان ابزار`() {
    val f = tool()
    assertEquals(f.path, LinuxSecretTool.find("/nonexistent${File.pathSeparator}${f.parent}")?.path)
    assertNull(LinuxSecretTool.find("/nonexistent"))
    assertNull(LinuxSecretTool.find(null))
    assertNull(LinuxSecretTool.unhex("abc"))
    assertNull(LinuxSecretTool.unhex("zz"))

    val keys = File(dir, "keys").apply { mkdirs() }
    val key = ByteArray(32).also { random.nextBytes(it) }
    File(keys, KekVault.PLAIN).writeBytes(key)
    val v = KekVault(keys, flag = { true }, store = { LinuxSecretTool(f) })
    assertArrayEquals(key, v.forRead().first()); v.opened(key)
    assertTrue(File(keys, KekVault.OS).readText().contains("store=secret-service"))
    v.relaunch()
    assertArrayEquals(key, v.forRead().first()); v.opened(key)
    assertFalse(File(keys, KekVault.PLAIN).exists())
    v.relaunch()
    assertArrayEquals(key, v.forRead().single())
    assertFalse(argv().contains(LinuxSecretTool.hex(key)))
  }
}
