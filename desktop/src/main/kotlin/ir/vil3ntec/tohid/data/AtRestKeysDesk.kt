package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKek
import ir.vil3ntec.tohid.desktop.DesktopContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  کلیدِ اصلیِ رمزِ دفتر روی کامپیوتر — جایگزینِ `AtRestKeys.kt`ِ اندروید.
 *
 *  ⚠️ **PARTIAL (فاز ۱)**: کلید در فایلِ فقط-صاحبِ `<پوشهٔ داده>/keys/atrest-kek.key`
 *  است — همان روشِ `SecretBox` برای توکن. این از بیرون رفتنِ تنهای فایلِ دفتر
 *  (کپی، همگام‌سازِ ابریِ پوشه، فرستادنِ اشتباهی) محافظت می‌کند، ولی کسی که
 *  کلِ پوشهٔ داده یا دیسک را دارد کلید را هم دارد. فاز ۲: پیچیدنِ همین کلید
 *  با DPAPI ویندوز / Keychain مک / libsecret لینوکس.
 *
 *  ⛔ فایلِ کلید فقط هنگامِ **ساختنِ** کلیددان ساخته می‌شود؛ نبودنش هنگامِ
 *  باز کردن یعنی `KEY_MISSING` — هرگز کلیدِ تازه‌ای که دفترِ قبلی را باز نمی‌کند.
 */
object AtRestKeys {

  const val WHERE: String = "فایلِ کلید در پوشهٔ دادهٔ همین کامپیوتر"
  const val HARDWARE_BACKED: Boolean = false

  fun kek(@Suppress("UNUSED_PARAMETER") context: Context): AtRestKek = FileKek

  private object FileKek : AtRestKek {
    override val id: String = "desktop-file:keys/atrest-kek.key"
    private val random = SecureRandom()

    private val file: File get() = File(File(DesktopContext.root, "keys"), "atrest-kek.key")

    private fun existing(): SecretKeySpec? {
      val f = file
      if (!f.isFile) return null
      val bytes = f.readBytes()
      if (bytes.size != 32) throw AtRestException(AtRestException.Kind.KEY_MISSING, "فایلِ کلید خراب است")
      return SecretKeySpec(bytes, "AES")
    }

    private fun create(): SecretKeySpec {
      val f = file
      val dir = f.parentFile.apply { mkdirs() }
      val bytes = ByteArray(32).also { random.nextBytes(it) }
      DiskWrite.bytes(f, bytes)
      runCatching {
        Files.setPosixFilePermissions(f.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        Files.setPosixFilePermissions(
          dir.toPath(),
          setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
        )
      }
      return SecretKeySpec(bytes, "AES")
    }

    override fun wrap(dek: ByteArray): ByteArray = guard {
      val key = existing() ?: create()
      val iv = ByteArray(IV).also { random.nextBytes(it) }
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
      iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray): ByteArray = guard {
      if (blob.size <= IV) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ پیچیده بریده است")
      val key = existing()
        ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, "فایلِ کلیدِ رمز روی این کامپیوتر نیست")
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV))
      c.doFinal(blob, IV, blob.size - IV)
    }

    private inline fun <T> guard(block: () -> T): T = try {
      block()
    } catch (e: AtRestException) {
      throw e
    } catch (e: AEADBadTagException) {
      throw AtRestException(AtRestException.Kind.KEY_MISSING, "فایلِ کلید با کلیددان نمی‌خواند", e)
    } catch (e: Exception) {
      throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "فایلِ کلید خوانده نشد: ${e.javaClass.simpleName}", e)
    }

    private const val IV = 12
  }
}
