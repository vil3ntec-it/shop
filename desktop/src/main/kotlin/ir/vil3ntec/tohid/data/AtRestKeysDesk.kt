package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.AtRestKek
import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.keystore.DeskKeystoreFlag
import ir.vil3ntec.tohid.desktop.keystore.KekVault
import ir.vil3ntec.tohid.desktop.keystore.OsKeyStore
import ir.vil3ntec.tohid.desktop.keystore.OsKeyStores
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
 *  ⚠️ **PARTIAL**: فاز ۱ (پیش‌فرض) — کلید در فایلِ فقط-صاحبِ `<پوشهٔ داده>/keys/atrest-kek.key`
 *  است — همان روشِ `SecretBox` برای توکن. این از بیرون رفتنِ تنهای فایلِ دفتر
 *  (کپی، همگام‌سازِ ابریِ پوشه، فرستادنِ اشتباهی) محافظت می‌کند، ولی کسی که
 *  کلِ پوشهٔ داده یا دیسک را دارد کلید را هم دارد.
 *  فاز ۲ پشتِ پرچمِ `desk.keystore` (پیش‌فرض **خاموش**، `DeskKeystoreFlag`): همان کلید با DPAPI ویندوز /
 *  Keychain مک / Secret Service ِ لینوکس — کوچ، برگشت و همهٔ شکست‌ها در `desktop/keystore/KekVault.kt`.
 *  بایت‌ها و شناسهٔ KEK عوض نمی‌شوند، پس کلیددانِ دفتر هرگز دوباره پیچیده نمی‌شود.
 *
 *  ⛔ فایلِ کلید فقط هنگامِ **ساختنِ** کلیددان ساخته می‌شود؛ نبودنش (و نبودِ نشانِ کلیددانِ سیستم) هنگامِ
 *  باز کردن یعنی `KEY_MISSING` — هرگز کلیدِ تازه‌ای که دفترِ قبلی را باز نمی‌کند.
 *
 *  اجازه‌ها (بازبینیِ #331): مک/لینوکس `600` (پوشه `700`)؛ ویندوز ACLِ «صاحب + SYSTEM +
 *  Administrators» بی ارث از پوشهٔ بالا (`AclFileAttributeView`). نشد ⇒ صفحه راستش را می‌گوید.
 */
object AtRestKeys {

  val WHERE: String get() = (vault.status as? KekVault.Status.Protected)?.let { StrAppData.deskOsWhere(OsKeyStores.label(it.store)) } ?: StrAppData.deskWhere
  const val HARDWARE_BACKED: Boolean = false

  /** `true`/`false` پس از نخستین تلاش روی ویندوز؛ `null` یعنی ACL در کار نیست (مک/لینوکس) یا هنوز تلاشی نشده */
  @Volatile var windowsAcl: Boolean? = null
    private set

  /** جای کلید و آنچه از آن برمی‌آید — بی تعارف، برای صفحهٔ امنیت؛ سپس حالِ کلیدِ نشستِ ورود (`SecretBox`، همان پرچم) */
  fun note(): String = (ledgerNote().trimEnd() + " " + ir.vil3ntec.tohid.desktop.SecretBox.note()).trimEnd()

  private fun ledgerNote(): String = buildString {
    when (val s = vault.status) {
      is KekVault.Status.Protected -> {
        append(StrAppData.deskOsNote(OsKeyStores.label(s.store)))
        when (s.plain) {
          KekVault.Plain.KEPT -> append(StrAppData.deskOsPlainPending)
          KekVault.Plain.STRAY -> append(StrAppData.deskOsPlainStray)
          KekVault.Plain.NONE -> Unit
        }
        return@buildString
      }
      is KekVault.Status.Fallback -> append(StrAppData.deskOsFallback(s.reason))
      else -> Unit
    }
    append(StrAppData.deskNote1)
    append(StrAppData.deskNote2)
    append(StrAppData.deskNote3)
    append(
      when (windowsAcl) {
        true -> StrAppData.deskAclOn
        false -> StrAppData.deskAclOff
        null -> if (isWindows) StrAppData.deskAclUnknown else StrAppData.deskAclUnix
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

  /** آزمون: کلیددانِ ساختگی (یا «هیچ») به‌جای کلیددانِ سیستم؛ خودِ `null` ⇒ همان سیستم */
  @Volatile internal var storeOverride: (() -> OsKeyStore?)? = null

  /** جای کلید — یک نمونه در هر اجرای برنامه (کوچِ `desk.keystore` و «اجرای بعدیِ موفق» به همین بسته‌اند) */
  internal val vault: KekVault by lazy {
    KekVault(
      dir = File(DesktopContext.root, "keys"),
      flag = { DeskKeystoreFlag.on(DesktopContext.root) },
      store = { storeOverride.let { if (it != null) it() else OsKeyStores.forThisOs() } },
      restrict = { restrict(it) },
    )
  }

  /** آزمون: «برنامه بسته و دوباره باز شد» برای جای کلید */
  internal fun relaunchForTest() = vault.relaunch()

  fun kek(@Suppress("UNUSED_PARAMETER") context: Context): AtRestKek = FileKek

  private object FileKek : AtRestKek {
    override val id: String = "desktop-file:keys/atrest-kek.key"
    private val random = SecureRandom()

    override fun wrap(dek: ByteArray): ByteArray = guard {
      val key = SecretKeySpec(vault.forWrite(), "AES")
      val iv = ByteArray(IV).also { random.nextBytes(it) }
      val c = Cipher.getInstance("AES/GCM/NoPadding")
      c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
      iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray, kekId: String): ByteArray = guard {
      if (kekId != id) throw AtRestException(AtRestException.Kind.KEY_MISSING, StrAppData.deskKeyChanged(kekId))
      if (blob.size <= IV) throw AtRestException(AtRestException.Kind.KEY_MISSING, StrAppData.keyCut)
      //  کلیدِ درست اول؛ فایلِ سادهٔ ناهمخوان (اگر هست) فقط نامزدِ دوم — برچسبِ GCM داور است
      var bad: AEADBadTagException? = null
      for (raw in vault.forRead()) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, blob, 0, IV))
        val dek = try {
          c.doFinal(blob, IV, blob.size - IV)
        } catch (e: AEADBadTagException) {
          bad = e
          continue
        }
        vault.opened(raw)
        return@guard dek
      }
      throw bad ?: AEADBadTagException()
    }

    private inline fun <T> guard(block: () -> T): T = try {
      block()
    } catch (e: AtRestException) {
      throw e
    } catch (e: AEADBadTagException) {
      throw AtRestException(AtRestException.Kind.KEY_MISSING, StrAppData.deskKeyMismatch, e)
    } catch (e: Exception) {
      throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, StrAppData.deskKeyUnread(e.javaClass.simpleName), e)
    }

    private const val IV = 12
  }
}
