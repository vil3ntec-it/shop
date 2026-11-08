package ir.vil3ntec.tohid.desktop.keystore

import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.data.DiskWrite
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom

/**
 *  جای KEK ِ رمزِ دفتر روی کامپیوتر (SEC-01 فاز ۲) — فایلِ فقط-صاحبِ پیشین، و پشتِ پرچمِ `desk.keystore`
 *  (پیش‌فرض خاموش) کلیددانِ خودِ سیستم ([OsKeyStore]).
 *
 *  ── دو شکلِ **یک** کلید ──────────────────────────────────────────────
 *   • `keys/atrest-kek.key` — ۳۲ بایتِ ساده (همان فاز ۱).
 *   • `keys/atrest-kek.os` — «نشان»: نامِ کلیددان، نشانِ کلید (`check` = ۱۲۸ بیتِ اولِ SHA-256 ِ «دامنه + کلید»)،
 *     نامِ حساب و (DPAPI) بایت‌های پیچیده.
 *  بایت‌های کلید **هرگز عوض نمی‌شوند** و شناسهٔ KEK (`desktop-file:keys/atrest-kek.key`) همان می‌ماند ⇒ کلیددانِ دفتر
 *  (`AtRestKeyring`) دست نمی‌خورد و هیچ چیزی دوباره پیچیده نمی‌شود. با نشان، هر نسخه‌ای خودش را می‌سنجد: فایلِ ساده‌ای
 *  که نشانش با `check` نخواند کلید نیست.
 *
 *  ── قاعده‌های بی‌استثنا (گمِ KEK = گمِ داده) ─────────────────────────
 *   ۱) کوچ: کلید به کلیددان سپرده و **از خودِ کلیددان بازخوانی و برابر** دیده می‌شود ⇐ نشان با نوشتنِ اتمی می‌نشیند ⇐
 *      نشان از دیسک دوباره خوانده و از کلیددان دوباره باز و برابر دیده می‌شود. هر گامی نشد ⇒ فایلِ ساده همان کلید است.
 *   ۲) فایلِ ساده تا **اجرای بعدیِ موفق** می‌ماند: فقط در اجرایی جز همانی که نشان را نوشت، پس از اینکه همین کلید
 *      کلیددانِ دفتر را واقعاً باز کرد ([opened]) و نسخهٔ کلیددان دوباره برابر دیده شد، پاک می‌شود — و فقط اگر
 *      بایت‌به‌بایت همان کلید باشد.
 *   ۳) کلیددانِ سیستم نیست، قفل است، جواب نداد یا بازخوانی نشد ⇒ همان فایل (اگر هست)؛ نبودِ فایل ⇒ `KEY_UNAVAILABLE`
 *      (همین حالا نه)، **هرگز** «کلید نیست» و **هرگز** کلیدِ تازه وقتی نشان هست.
 *   ۴) هیچ نسخه‌ای رونویسی نمی‌شود: نشانِ پیشین کنار می‌رود (`.old-<زمان>`/`.bad-<زمان>`) و نسخهٔ سیستمی هرگز پاک
 *      نمی‌شود.
 *   ۵) پرچمِ خاموش ⇒ رفتارِ فاز ۱ بی‌کم‌وکاست و هیچ تماسی با کلیددانِ سیستم — جز وقتی فایلِ ساده نیست و نشان هست
 *      (برگشت پس از کوچ): کلید فقط **خوانده** و فایلِ ساده با همان نوشتن و بازخوانی برمی‌گردد.
 *
 *  آزمون: `KekVaultTest` (کلیددانِ ساختگی با شکست‌های تزریقی و «برق» در هر گام) و `OsKeystoreRoundTripTest`
 *  (کلیددانِ واقعیِ ویندوز و مک در CI).
 */
class KekVault(
  private val dir: File,
  private val flag: () -> Boolean,
  private val store: () -> OsKeyStore?,
  private val disk: Disk = Disk.Real,
  private val restrict: (File) -> Unit = {},
  private val random: SecureRandom = SecureRandom(),
  private val clock: () -> Long = System::currentTimeMillis,
) {

  /** کارهای دیسکیِ کوچ — جدا، تا آزمون وسطِ هر گام «برق را قطع کند» */
  interface Disk {
    fun read(f: File): ByteArray

    /** نوشتنِ اتمی با `fsync` (همان `DiskWrite`) */
    fun write(f: File, bytes: ByteArray)

    fun delete(f: File): Boolean

    /** کنار گذاشتن به نامِ یکتای تازه — هرگز رونویسی */
    fun moveAside(from: File, to: File)

    fun syncDir(dir: File)

    object Real : Disk {
      override fun read(f: File): ByteArray = f.readBytes()
      override fun write(f: File, bytes: ByteArray) = DiskWrite.bytes(f, bytes)
      override fun delete(f: File): Boolean = f.delete()
      override fun moveAside(from: File, to: File) {
        try {
          Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
          Files.move(from.toPath(), to.toPath())
        }
      }
      override fun syncDir(dir: File) = DiskWrite.syncDir(dir)
    }
  }

  /** فایلِ ساده در حالِ «نگه‌داری در سیستم» */
  enum class Plain {
    /** پاک شد — فقط نسخهٔ سیستم */
    NONE,

    /** همان کلید؛ تا اجرای بعدیِ موفق می‌ماند */
    KEPT,

    /** فایلی هست که با نشان نمی‌خواند — دست نمی‌خورد */
    STRAY,
  }

  sealed class Status {
    /** پرچم خاموش: همان فایلِ فقط-صاحب */
    object Off : Status()

    /** @param store شناسهٔ کلیددان ([OsKeyStore.id]) — نامِ نمایشی هنگامِ نشان دادن، به زبانِ همان دم (`OsKeyStores.label`) */
    data class Protected(val store: String, val plain: Plain) : Status()

    /** پرچم روشن ولی کلیددانِ سیستم نشد ⇒ همان فایل */
    data class Fallback(val store: String?, val reason: String) : Status()
  }

  val plainFile: File get() = File(dir, PLAIN)
  val osFile: File get() = File(dir, OS)

  private val lock = Any()

  /** حالِ این اجرا برای صفحهٔ امنیت؛ `null` تا نخستین بارِ خواندنِ کلید */
  @Volatile var status: Status? = null
    private set

  private var launch = newLaunch()
  private var flagOnce: Boolean? = null
  private var storeOnce: Lazy<OsKeyStore?> = lazyStore()
  private var maintained = false
  private var removeAfterOpen = false
  private var restricted = false

  private fun newLaunch(): String = hex(ByteArray(8).also { random.nextBytes(it) })

  private fun lazyStore(): Lazy<OsKeyStore?> = lazy { runCatching { store() }.getOrNull() }

  private fun flagOn(): Boolean = flagOnce ?: runCatching { flag() }.getOrDefault(false).also { flagOnce = it }

  /** «اجرای تازهٔ برنامه» — برای آزمون؛ در برنامه یک نمونه در هر اجراست */
  fun relaunch() = synchronized(lock) {
    launch = newLaunch()
    flagOnce = null
    storeOnce = lazyStore()
    maintained = false
    removeAfterOpen = false
    restricted = false
    status = null
  }

  private class Loaded(
    val key: ByteArray,
    val fromStore: Boolean,
    val os: Descriptor?,
    /** بایت‌های فایلِ ساده، هر اندازه؛ `null` یعنی نیست */
    val plain: ByteArray?,
  )

  /**
   *  کلید(ها) برای باز کردن — هرگز نمی‌سازد. اول کلیدِ درست؛ فایلِ سادهٔ ناهمخوان (اگر هست) فقط به‌عنوانِ نامزدِ دوم.
   *  @throws AtRestException `KEY_MISSING` اگر هیچ نسخه‌ای نیست یا خراب است؛ `KEY_UNAVAILABLE` اگر نسخهٔ سیستم همین حالا خوانده نشد
   */
  fun forRead(): List<ByteArray> = synchronized(lock) {
    val l = load() ?: throw AtRestException(AtRestException.Kind.KEY_MISSING, StrAppData.deskKeyMissing)
    maintain(l)
    val stray = l.plain?.takeIf { it.size == KEY_LEN && !MessageDigest.isEqual(it, l.key) }
    listOfNotNull(l.key, stray)
  }

  /** کلید برای پیچیدن؛ فقط وقتی هیچ نسخه‌ای نیست ساخته می‌شود — اول همان فایلِ ساده، بعد (پرچم روشن) همان کوچ */
  fun forWrite(): ByteArray = synchronized(lock) {
    val l = load() ?: create()
    maintain(l)
    l.key
  }

  /**
   *  همین کلید کلیددانِ دفتر را باز کرد (برچسبِ GCM درست بود) — گامِ «اجرای بعدیِ موفق»: پاک کردنِ فایلِ ساده، فقط
   *  اگر نشان در اجرای دیگری نوشته شده، نسخهٔ سیستم همین حالا دوباره برابر است و فایل بایت‌به‌بایت همین کلید است.
   *  هر شکستی ⇒ فایل می‌ماند (بی صدا؛ اجرای بعد دوباره).
   */
  fun opened(key: ByteArray) {
    synchronized(lock) { removeIfDone(key) }
  }

  private fun removeIfDone(key: ByteArray) {
    if (!removeAfterOpen) return
    removeAfterOpen = false
    runCatching {
      if (!flagOn()) return
      val d = Descriptor.parse(disk.read(osFile)) ?: return
      if (d.launch == launch || d.check != check(key)) return
      val s = storeFor(d.store) ?: return
      if (!MessageDigest.isEqual(s.unprotect(d.ref, d.blob), key)) return
      if (!MessageDigest.isEqual(disk.read(plainFile), key)) return
      if (disk.delete(plainFile)) {
        runCatching { disk.syncDir(dir) }
        status = Status.Protected(s.id, Plain.NONE)
      }
    }
  }

  private fun storeFor(id: String): OsKeyStore? = storeOnce.value?.takeIf { it.id == id }

  private fun unavailable(why: String): AtRestException =
    AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, why)

  private fun load(): Loaded? {
    val osBytes = if (osFile.isFile) disk.read(osFile) else null
    val os = osBytes?.let { Descriptor.parse(it) }
    val plain = if (plainFile.isFile) {
      //  کلیدِ ساختِ پیشین (پیش از ACL) هم یک بار در هر اجرا محدود می‌شود
      if (!restricted) { restricted = true; runCatching { restrict(plainFile) } }
      disk.read(plainFile)
    } else {
      null
    }
    val good = plain?.takeIf { it.size == KEY_LEN }
    if (os != null) {
      //  همان کلید هنوز ساده هم هست ⇒ بی تماس با سیستم (پرچمِ خاموش، یا کوچی که هنوز پاک نکرده)
      if (good != null && check(good) == os.check) return Loaded(good, false, os, plain)
      val name = storeLabel(os.store)
      val s = storeFor(os.store) ?: throw unavailable(StrAppData.deskOsUnread(name, StrAppData.deskOsMissing))
      val k = try {
        s.unprotect(os.ref, os.blob)
      } catch (e: KeyStoreFailure) {
        throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, StrAppData.deskOsUnread(name, e.reason), e)
      }
      if (k.size != KEY_LEN || check(k) != os.check) throw unavailable(StrAppData.deskOsUnread(name, StrAppData.deskOsWrongValue))
      return Loaded(k, true, os, plain)
    }
    if (osBytes != null) {
      //  نشانِ خراب: فایلِ ساده (اگر هست) کلید است و کوچ نشان را کنار می‌گذارد؛ نبود ⇒ همان «فایلِ کلید خراب» ِ فاز ۱
      if (good != null) return Loaded(good, false, null, plain)
      throw AtRestException(AtRestException.Kind.KEY_MISSING, StrAppData.deskOsBroken)
    }
    if (good != null) return Loaded(good, false, null, plain)
    if (plain != null) throw AtRestException(AtRestException.Kind.KEY_MISSING, StrAppData.deskKeyBroken)
    return null
  }

  private fun storeLabel(id: String): String = storeOnce.value?.takeIf { it.id == id }?.label ?: id

  private fun create(): Loaded {
    dir.mkdirs()
    val k = ByteArray(KEY_LEN).also { random.nextBytes(it) }
    disk.write(plainFile, k)
    runCatching { restrict(plainFile) }
    restricted = true
    return Loaded(k, false, null, k)
  }

  /** یک بار در هر اجرا: کوچ (پرچم روشن)، برگشت (پرچم خاموش)، و آماده کردنِ پاک‌کردنِ فایلِ ساده */
  private fun maintain(l: Loaded) {
    if (maintained) return
    maintained = true
    if (!flagOn()) {
      status = Status.Off
      if (l.fromStore && l.plain == null) restorePlain(l.key)
      return
    }
    val s = storeOnce.value
    val os = l.os
    status = when {
      s == null -> Status.Fallback(null, StrAppData.deskOsMissing)
      //  بی نشان، نشانِ خراب، یا نشانِ سیستمِ دیگری (پوشهٔ داده از ویندوز به مک آمده)
      os == null || os.store != s.id -> migrate(s, l.key)
      l.fromStore -> Status.Protected(s.id, if (l.plain == null) Plain.NONE else Plain.STRAY)
      else -> kept(s, os, l.key)
    }
  }

  /**
   *  نشان هست و فایلِ ساده همان کلید است: نسخهٔ سیستم همین حالا سنجیده می‌شود تا صفحهٔ امنیت راست بگوید و فایل فقط
   *  پس از سنجشِ درست پاک شود. «آن‌جا نیست» (کسی از Keychain پاکش کرد) ⇒ همان کوچ از نو؛ قفل/بی جواب ⇒ همان فایل.
   */
  private fun kept(s: OsKeyStore, os: Descriptor, key: ByteArray): Status = try {
    if (!MessageDigest.isEqual(s.unprotect(os.ref, os.blob), key)) throw KeyStoreFailure(StrAppData.deskOsWrongValue)
    removeAfterOpen = os.launch != launch
    Status.Protected(s.id, Plain.KEPT)
  } catch (e: KeyStoreFailure) {
    if (e.notFound) migrate(s, key) else Status.Fallback(s.id, e.reason)
  } catch (e: Exception) {
    Status.Fallback(s.id, StrAppData.deskOsError(e.javaClass.simpleName))
  }

  private fun migrate(s: OsKeyStore, key: ByteArray): Status = try {
    val check = check(key)
    val ref = REF_PREFIX + check.take(REF_HEX)
    val blob = s.protect(ref, key)
    if (!MessageDigest.isEqual(s.unprotect(ref, blob), key)) throw KeyStoreFailure(StrAppData.deskOsReadBack)
    if (osFile.exists()) disk.moveAside(osFile, File(dir, "$OS.old-${clock()}"))
    disk.write(osFile, Descriptor(s.id, check, ref, blob, launch).encode())
    runCatching { restrict(osFile) }
    val d = Descriptor.parse(disk.read(osFile))
    if (d == null || d.check != check || !MessageDigest.isEqual(s.unprotect(d.ref, d.blob), key)) {
      runCatching { disk.moveAside(osFile, File(dir, "$OS.bad-${clock()}")) }
      throw KeyStoreFailure(StrAppData.deskOsReadBack)
    }
    Status.Protected(s.id, Plain.KEPT)
  } catch (e: KeyStoreFailure) {
    Status.Fallback(s.id, e.reason)
  } catch (e: Exception) {
    Status.Fallback(s.id, StrAppData.deskOsError(e.javaClass.simpleName))
  }

  /** پرچمِ خاموش پس از کوچ: فایلِ ساده با همان نوشتن و بازخوانی برمی‌گردد؛ نسخهٔ سیستم دست نمی‌خورد */
  private fun restorePlain(key: ByteArray) {
    runCatching {
      dir.mkdirs()
      disk.write(plainFile, key)
      runCatching { restrict(plainFile) }
      restricted = true
      if (!MessageDigest.isEqual(disk.read(plainFile), key)) disk.delete(plainFile)
    }
  }

  /** فایلِ نشان — متنِ ساده، یک کلید=مقدار در هر سطر */
  internal class Descriptor(val store: String, val check: String, val ref: String, val blob: String, val launch: String) {
    fun encode(): ByteArray = buildString {
      append(HEADER).append('\n')
      append("store=").append(store).append('\n')
      append("check=").append(check).append('\n')
      append("ref=").append(ref).append('\n')
      append("blob=").append(blob).append('\n')
      append("launch=").append(launch).append('\n')
    }.toByteArray(Charsets.UTF_8)

    companion object {
      private val ID = Regex("[a-z][a-z0-9-]{0,31}")
      private val HEX32 = Regex("[0-9a-f]{32}")
      private val REF = Regex("[A-Za-z0-9._-]{1,64}")
      private val B64 = Regex("[A-Za-z0-9+/=]*")
      private val LAUNCH = Regex("[0-9a-f]{1,32}")

      fun parse(bytes: ByteArray): Descriptor? {
        if (bytes.size > MAX_OS_BYTES) return null
        val lines = String(bytes, Charsets.UTF_8).lines()
        if (lines.firstOrNull() != HEADER) return null
        val f = lines.drop(1).mapNotNull { line ->
          val i = line.indexOf('=')
          if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
        val store = f["store"]?.takeIf { ID.matches(it) } ?: return null
        val check = f["check"]?.takeIf { HEX32.matches(it) } ?: return null
        val ref = f["ref"]?.takeIf { REF.matches(it) } ?: return null
        val blob = f["blob"]?.takeIf { B64.matches(it) } ?: return null
        val launch = f["launch"]?.takeIf { LAUNCH.matches(it) } ?: return null
        return Descriptor(store, check, ref, blob, launch)
      }
    }
  }

  companion object {
    const val PLAIN = "atrest-kek.key"
    const val OS = "atrest-kek.os"
    const val REF_PREFIX = "atrest-kek-"
    private const val REF_HEX = 16
    private const val KEY_LEN = 32
    private const val HEADER = "tohid-kek-os v1"
    private const val MAX_OS_BYTES = 16 * 1024
    private val CHECK_DOMAIN = "tohid-atrest-kek-check-v1\u0000".toByteArray(Charsets.US_ASCII)

    /** نشانِ کلید — ۱۲۸ بیتِ اولِ SHA-256 ِ «دامنه + کلید»؛ از کلیدِ تصادفیِ ۲۵۶ بیتی چیزی نمی‌گوید */
    fun check(key: ByteArray): String {
      val md = MessageDigest.getInstance("SHA-256")
      md.update(CHECK_DOMAIN)
      md.update(key)
      return hex(md.digest()).take(32)
    }

    internal fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }
}
