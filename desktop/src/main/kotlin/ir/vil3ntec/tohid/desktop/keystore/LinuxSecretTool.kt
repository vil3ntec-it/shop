package ir.vil3ntec.tohid.desktop.keystore

import ir.vil3ntec.tohid.core.i18n.StrAppData
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 *  لینوکس: Secret Service (GNOME Keyring، KWallet با پل، …) با ابزارِ خودِ آن، `secret-tool` (بستهٔ `libsecret-tools`).
 *
 *  ⛔ کلید **فقط از stdin** می‌رود (`secret-tool store` بی ترمینال همهٔ stdin را گذرواژه می‌گیرد) و از stdout برمی‌گردد؛
 *  خطِ فرمان فقط نامِ سرویس و حساب را دارد که راز نیستند (حساب = پیشوندِ نشانِ SHA-256 ِ کلید).
 *  ابزار نیست، D-Bus نیست، مجموعه قفل است ⇒ `KeyStoreFailure` ⇒ همان فایلِ فقط-صاحبِ پیشین.
 */
internal class LinuxSecretTool(private val tool: File) : OsKeyStore {
  override val id: String = "secret-service"
  override val label: String get() = StrAppData.deskStoreSecret

  private class Result(val code: Int, val out: String, val err: String)

  private fun run(args: List<String>, stdin: String? = null): Result {
    val p = try {
      ProcessBuilder(listOf(tool.path) + args).start()
    } catch (e: IOException) {
      throw KeyStoreFailure("${StrAppData.deskOsMissing} (${e.javaClass.simpleName})", cause = e)
    }
    try {
      try {
        p.outputStream.use { o -> if (stdin != null) o.write(stdin.toByteArray(Charsets.UTF_8)) }
      } catch (_: IOException) {
        //  ابزار پیش از خواندن بیرون رفت (قفل، بی D-Bus): «لولهٔ شکسته» — کدِ خروج و stderr می‌گویند چرا
      }
      //  خروجی چند بایت است؛ هر دو جریان پس از پایان خوانده می‌شوند (لوله پر نمی‌شود)
      if (!p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
        p.destroyForcibly()
        throw KeyStoreFailure(StrAppData.deskOsTimeout)
      }
      val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
      val err = p.errorStream.readBytes().toString(Charsets.UTF_8)
      return Result(p.exitValue(), out, err)
    } catch (e: InterruptedException) {
      p.destroyForcibly()
      Thread.currentThread().interrupt()
      throw KeyStoreFailure(StrAppData.deskOsTimeout, cause = e)
    } catch (e: IOException) {
      p.destroyForcibly()
      throw KeyStoreFailure(StrAppData.deskOsError(e.javaClass.simpleName), cause = e)
    }
  }

  private fun attrs(ref: String) = listOf("service", OsKeyStores.SERVICE, "account", ref)

  private fun why(r: Result): String {
    //  پیامِ ابزار فقط برای صفحهٔ امنیت؛ یک سطر، کوتاه
    val first = r.err.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(120)
    return StrAppData.deskOsRefused(listOfNotNull("secret-tool ${r.code}", first).joinToString(": "))
  }

  override fun protect(ref: String, key: ByteArray): String {
    val r = run(listOf("store", "--label=My Shop data key") + attrs(ref), stdin = hex(key))
    if (r.code != 0) throw KeyStoreFailure(why(r))
    return ""
  }

  override fun unprotect(ref: String, blob: String): ByteArray {
    val r = run(listOf("lookup") + attrs(ref))
    val text = r.out.trim()
    if (r.code != 0 || text.isEmpty()) {
      //  بی پیامِ خطا یعنی «چنین چیزی نیست»؛ با پیام یعنی قفل، بی D-Bus، …
      if (r.err.isBlank()) throw KeyStoreFailure(StrAppData.deskOsNotFound, notFound = true)
      throw KeyStoreFailure(why(r))
    }
    return unhex(text) ?: throw KeyStoreFailure(StrAppData.deskOsWrongValue)
  }

  override fun remove(ref: String) {
    run(listOf("clear") + attrs(ref))
  }

  companion object {
    private const val TIMEOUT_S = 20L

    /** `secret-tool` ِ اجراپذیر در `PATH` — نبود ⇒ `null` (همان فایلِ پیشین) */
    fun find(path: String?): File? =
      path.orEmpty().split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .map { File(it, "secret-tool") }
        .firstOrNull { it.isFile && it.canExecute() }

    internal fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    internal fun unhex(s: String): ByteArray? {
      if (s.length % 2 != 0 || s.any { Character.digit(it, 16) < 0 }) return null
      return ByteArray(s.length / 2) { i -> ((Character.digit(s[2 * i], 16) shl 4) or Character.digit(s[2 * i + 1], 16)).toByte() }
    }
  }
}
