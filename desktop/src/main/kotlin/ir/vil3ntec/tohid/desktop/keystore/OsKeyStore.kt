package ir.vil3ntec.tohid.desktop.keystore

import ir.vil3ntec.tohid.core.i18n.StrAppData
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 *  کلیددانِ خودِ سیستم برای KEK ِ رمزِ دفتر (SEC-01 فاز ۲، پرچمِ `desk.keystore`) — فقط آنچه برنامه از پیش
 *  صدا می‌زند، بی وابستگیِ بومیِ تازه:
 *   • ویندوز: DPAPI (`CryptProtectData`، دامنهٔ کاربرِ جاری) با JNA ِ موجود ⇒ [WindowsDpapi]؛ بایت‌های پیچیده در
 *     همان پوشهٔ `keys` می‌نشینند و فقط همان حسابِ ویندوز بازشان می‌کند.
 *   • مک: Keychain (گذرواژهٔ عمومی) با JNA ِ موجود ⇒ [MacKeychain].
 *   • لینوکس: Secret Service با `secret-tool` (کلید فقط از stdin، هرگز روی خطِ فرمان) ⇒ [LinuxSecretTool]؛
 *     نبود ⇒ همان فایلِ فقط-صاحبِ پیشین.
 *  منطقِ کوچ و برگشت در [KekVault] است؛ این‌جا فقط «بنویس / بخوان».
 */
interface OsKeyStore {
  /** شناسهٔ پایدار که در فایلِ نشان (`atrest-kek.os`) می‌نشیند — عوض نشود */
  val id: String

  /** نامِ نمایشی برای صفحهٔ امنیت */
  val label: String

  /**
   *  کلید را می‌سپارد.
   *  @param ref نامِ یکتای این کلید (حساب در Keychain / Secret Service)
   *  @return آنچه باید در فایلِ نشان بماند (DPAPI: بایت‌های پیچیده به base64؛ بقیه: تهی)
   *  @throws KeyStoreFailure
   */
  fun protect(ref: String, key: ByteArray): String

  /** @throws KeyStoreFailure */
  fun unprotect(ref: String, blob: String): ByteArray

  /** فقط برای آزمون و پاک‌سازیِ دستی — برنامه هرگز نسخهٔ سیستمی را پاک نمی‌کند */
  fun remove(ref: String)
}

/** شکستِ کلیددانِ سیستم؛ [reason] برای صفحهٔ امنیت (از `StrAppData`) */
class KeyStoreFailure(val reason: String, val notFound: Boolean = false, cause: Throwable? = null) : Exception(reason, cause)

object OsKeyStores {

  /** نامِ سرویس در Keychain / Secret Service — نامِ درونی، عوض نشود */
  const val SERVICE = "ir.vil3ntec.tohid.desktop.atrest"

  /** سرویسِ جدای کلیدِ نشستِ ورود (`SecretBox`، [KekVault.Slot.SESSION]) — نامِ درونی، عوض نشود */
  const val SESSION_SERVICE = "ir.vil3ntec.tohid.desktop.session"

  /** نامِ نمایشیِ یک شناسه به زبانِ همین دم — شناسهٔ ناشناخته همان شناسه */
  fun label(id: String): String = when (id) {
    WindowsDpapi.id -> StrAppData.deskStoreDpapi
    "keychain" -> StrAppData.deskStoreKeychain
    "secret-service" -> StrAppData.deskStoreSecret
    else -> id
  }

  /**
   *  کلیددانِ همین سیستم، یا `null` اگر این سیستم چیزی ندارد (لینوکسِ بی `secret-tool`).
   *  @param interactive مک: `false` یعنی Keychain هیچ پنجره‌ای نشان ندهد (آزمونِ CI) — قفل ⇒ شکستِ فوری
   *  @param slot کدام کلید: سرویس و برچسبِ همان کلید در Keychain / Secret Service (DPAPI: بایت‌ها در نشانِ همان کلیدند)
   */
  fun forThisOs(
    interactive: Boolean = true,
    os: String = System.getProperty("os.name").orEmpty(),
    slot: KekVault.Slot = KekVault.Slot.LEDGER,
  ): OsKeyStore? {
    val o = os.lowercase()
    return when {
      o.startsWith("windows") -> WindowsDpapi
      o.startsWith("mac") || o.startsWith("darwin") -> MacKeychain(interactive, slot.service)
      else -> LinuxSecretTool.find(System.getenv("PATH"))?.let { LinuxSecretTool(it, slot.service, slot.secretLabel) }
    }
  }
}

/**
 *  پرچمِ `desk.keystore` (پیش‌فرض **خاموش**) — مالِ همین کامپیوتر، نه حساب یا دکان (کلید هم مالِ همین کامپیوتر است).
 *  ۱) ویژگیِ JVM ِ `-Ddesk.keystore=true|false` (آزمون و `Tohid.cfg`) بر همه جلو می‌افتد؛
 *  ۲) وگرنه فایلِ `<پوشهٔ داده>/desk.keystore` با متنِ `true` (چک‌لیست: `docs/FLAGS-ON-fa.md`).
 *  فقط `true` روشن است؛ هر چیزِ دیگر، فایلِ بزرگ یا خطای خواندن ⇒ خاموش.
 */
object DeskKeystoreFlag {
  const val KEY = "desk.keystore"

  fun on(root: File): Boolean {
    System.getProperty(KEY)?.let { return it.trim().equals("true", ignoreCase = true) }
    return fileSays(File(root, KEY))
  }

  /** نوت‌پد و PowerShell گاه BOM یا UTF-16 می‌نویسند — «true» همان «true» است */
  internal fun fileSays(f: File): Boolean = runCatching {
    if (!f.isFile || f.length() > 64) return false
    val b = f.readBytes()
    val text = if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) {
      String(b, 2, b.size - 2, Charsets.UTF_16LE)
    } else {
      String(b, Charsets.UTF_8)
    }
    text.replace("﻿", "").replace("\u0000", "").trim().equals("true", ignoreCase = true)
  }.getOrDefault(false)
}

/**
 *  هر صدا به کلیددانِ سیستم با سقفِ زمان: پنجرهٔ Keychain که کسی جوابش را نمی‌دهد یا D-Bus ِ گیرکرده جلوی
 *  باز شدنِ دفتر را برای همیشه نمی‌گیرد ⇒ `KeyStoreFailure` (همان «همین حالا نه»، هرگز «کلید نیست»).
 */
internal object Bounded {
  private val pool = Executors.newCachedThreadPool { r -> Thread(r, "tohid-keystore").apply { isDaemon = true } }

  fun <T> run(seconds: Long, what: () -> T): T {
    val f = pool.submit(Callable { what() })
    try {
      return f.get(seconds, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
      f.cancel(true)
      throw KeyStoreFailure(StrAppData.deskOsTimeout, cause = e)
    } catch (e: ExecutionException) {
      when (val c = e.cause) {
        is KeyStoreFailure -> throw c
        //  JNA یا کتابخانهٔ سیستم نیست (NoClassDefFoundError، UnsatisfiedLinkError)
        is LinkageError -> throw KeyStoreFailure("${StrAppData.deskOsMissing} (${c.javaClass.simpleName})", cause = c)
        else -> throw KeyStoreFailure(StrAppData.deskOsError(c?.javaClass?.simpleName ?: "?"), cause = c)
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw KeyStoreFailure(StrAppData.deskOsTimeout, cause = e)
    }
  }
}
