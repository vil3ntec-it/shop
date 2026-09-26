package ir.vil3ntec.tohid.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  رمزِ محلیِ رازها (توکنِ نشست) — AES-256-GCM.
 *
 *  کلید در `<پوشهٔ داده>/keys/session.key` است با اجازهٔ فقط-صاحب (روی
 *  لینوکس و مک ۶۰۰؛ روی ویندوز پوشهٔ `LOCALAPPDATA` خودش مالِ همان
 *  کاربر است). جدا از فایلِ تنظیمات، تا پشتیبان یا کپیِ تنظیمات نشست را
 *  با خودش نبرد.
 *
 *  باز نشد (کلید عوض شده، فایل دستکاری شده) ⇒ `null`، یعنی «نشستی
 *  نیست» — کاربر دوباره وارد می‌شود؛ هیچ‌وقت استثنا.
 */
object SecretBox {

  private const val PREFIX = "v1:"
  private val random = SecureRandom()

  private val key: SecretKeySpec by lazy {
    val dir = File(DesktopContext.root, "keys").apply { mkdirs() }
    val f = File(dir, "session.key")
    val bytes = if (f.isFile && f.length() == 32L) f.readBytes() else {
      ByteArray(32).also { random.nextBytes(it); f.writeBytes(it) }
    }
    runCatching {
      Files.setPosixFilePermissions(f.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
      Files.setPosixFilePermissions(dir.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE))
    }
    SecretKeySpec(bytes, "AES")
  }

  fun seal(plain: String): String {
    val iv = ByteArray(12).also { random.nextBytes(it) }
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
    return PREFIX + Base64.getEncoder().encodeToString(iv + body)
  }

  fun open(sealed: String): String? = runCatching {
    if (!sealed.startsWith(PREFIX)) return null
    val raw = Base64.getDecoder().decode(sealed.removePrefix(PREFIX))
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw, 0, 12))
    }
    String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
  }.getOrNull()
}
