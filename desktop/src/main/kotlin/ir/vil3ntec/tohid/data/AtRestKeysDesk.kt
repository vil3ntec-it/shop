package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKek
import ir.vil3ntec.tohid.desktop.DesktopContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclFileAttributeView
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
 *
 *  اجازه‌ها (بازبینیِ #331): مک/لینوکس `600` (پوشه `700`)؛ ویندوز ACLِ «صاحب + SYSTEM +
 *  Administrators» بی ارث از پوشهٔ بالا (`AclFileAttributeView`). نشد ⇒ صفحه راستش را می‌گوید.
 */
object AtRestKeys {

  const val WHERE: String = "فایلِ کلید در پوشهٔ دادهٔ همین کامپیوتر"
  const val HARDWARE_BACKED: Boolean = false

  /** `true`/`false` پس از نخستین تلاش روی ویندوز؛ `null` یعنی ACL در کار نیست (مک/لینوکس) یا هنوز تلاشی نشده */
  @Volatile var windowsAcl: Boolean? = null
    private set

  /** جای کلید و آنچه از آن برمی‌آید — بی تعارف، برای صفحهٔ امنیت */
  fun note(): String = buildString {
    append("کلید در فایلی کنارِ داده‌هاست (پوشهٔ keys کنارِ پوشهٔ files در پوشهٔ دادهٔ برنامه)، نه در خودِ ویندوز یا مک. ")
    append("پس این رمز فقط وقتی کمک می‌کند که پوشهٔ files (دفتر و نسخه‌ها) جدا کپی یا فرستاده شود؛ ")
    append("هر کس کلِ پوشهٔ داده یا دیسک را داشته باشد، کلید را هم دارد. ")
    append(
      when (windowsAcl) {
        true -> "روی ویندوز فایلِ کلید فقط برای حسابِ ویندوزِ خودتان، SYSTEM و مدیرانِ دستگاه خواندنی است (ACL)."
        false -> "روی ویندوز اجازهٔ فایلِ کلید محدود **نشد**؛ هر حسابی که پوشه را می‌خواند، کلید را هم می‌خواند."
        null -> if (isWindows) "اجازهٔ فایلِ کلید روی ویندوز هنوز سنجیده نشده." else "فایلِ کلید فقط برای حسابِ خودتان خواندنی است (۶۰۰)."
      },
    )
  }

  private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

  /** SIDهای شناخته‌شده — به هر زبانی که ویندوز باشد همین‌اند (نامِ «SYSTEM»/«Administrators» ترجمه می‌شود) */
  internal const val SID_SYSTEM = "S-1-5-18"
  internal const val SID_ADMINS = "S-1-5-32-544"

  /**
   *  فرمانِ `icacls`: ارثِ پوشهٔ بالا بریده (`/inheritance:r`)، و فقط صاحب + SYSTEM +
   *  Administrators با **SID** (`*S-…`، نه نامِ انگلیسی) — پیگیریِ #331.
   */
  internal fun aclCommand(path: String, owner: String, directory: Boolean): List<String> {
    val perm = if (directory) "(OI)(CI)F" else "F"
    return listOf("icacls", path, "/inheritance:r", "/grant:r", "$owner:$perm", "*$SID_SYSTEM:$perm", "*$SID_ADMINS:$perm")
  }

  /**
   *  ویندوز: DACLِ تازه — صاحبِ فایل، SYSTEM و Administrators، همهٔ اجازه‌ها — جای ورودی‌های
   *  ارث‌برده (حساب‌های عادیِ دیگر و Users بیرون می‌مانند). SYSTEM و Administrators می‌مانند تا
   *  پشتیبانِ فایلیِ ویندوز و مدیرِ دستگاه کلید را هم ببرند.
   *  @return `null` اگر این سیستم ACL ندارد
   */
  internal fun ownerOnlyAcl(p: Path): Boolean? {
    val view = Files.getFileAttributeView(p, AclFileAttributeView::class.java) ?: return null
    return runCatching {
      val proc = ProcessBuilder(aclCommand(p.toAbsolutePath().toString(), view.owner.name, Files.isDirectory(p)))
        .redirectErrorStream(true)
        .start()
      proc.inputStream.readBytes()
      if (!proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
        proc.destroyForcibly()
        false
      } else {
        proc.exitValue() == 0 && view.acl.any { it.principal() == view.owner }
      }
    }.getOrDefault(false)
  }

  private fun restrict(f: File) {
    val dir = f.absoluteFile.parentFile
    runCatching {
      Files.setPosixFilePermissions(f.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
      Files.setPosixFilePermissions(
        dir.toPath(),
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
      )
    }
    if (isWindows) {
      val fileOk = ownerOnlyAcl(f.toPath())
      val dirOk = ownerOnlyAcl(dir.toPath())
      windowsAcl = if (fileOk == null && dirOk == null) null else fileOk == true && dirOk == true
    }
  }

  fun kek(@Suppress("UNUSED_PARAMETER") context: Context): AtRestKek = FileKek

  private object FileKek : AtRestKek {
    override val id: String = "desktop-file:keys/atrest-kek.key"
    private val random = SecureRandom()

    private val file: File get() = File(File(DesktopContext.root, "keys"), "atrest-kek.key")

    /** کلیدِ ساختِ پیشین (پیش از ACL) هم یک بار در هر اجرا محدود می‌شود */
    @Volatile private var restricted = false

    private fun existing(): SecretKeySpec? {
      val f = file
      if (!f.isFile) return null
      if (!restricted) { restricted = true; runCatching { restrict(f) } }
      val bytes = f.readBytes()
      if (bytes.size != 32) throw AtRestException(AtRestException.Kind.KEY_MISSING, "فایلِ کلید خراب است")
      return SecretKeySpec(bytes, "AES")
    }

    private fun create(): SecretKeySpec {
      val f = file
      f.parentFile.mkdirs()
      val bytes = ByteArray(32).also { random.nextBytes(it) }
      DiskWrite.bytes(f, bytes)
      runCatching { restrict(f) }
      restricted = true
      return SecretKeySpec(bytes, "AES")
    }

    override fun wrap(dek: ByteArray): ByteArray = guard {
      val key = existing() ?: create()
      val iv = ByteArray(IV).also { random.nextBytes(it) }
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
      iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray, kekId: String): ByteArray = guard {
      if (kekId != id) throw AtRestException(AtRestException.Kind.KEY_MISSING, "کلیدِ اصلیِ کامپیوتر عوض شده ($kekId)")
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
