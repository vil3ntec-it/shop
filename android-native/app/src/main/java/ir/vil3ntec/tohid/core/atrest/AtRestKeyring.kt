package ir.vil3ntec.tohid.core.atrest

import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 *  کلیدِ رمزِ کلید (KEK) — جایش بیرون از فایل‌هاست.
 *
 *  اندروید: کلیدِ AES در Android Keystore که هرگز از سخت‌افزار/سرویس بیرون
 *  نمی‌آید (`data/AtRestKeys.kt`). کامپیوتر: فعلاً فایلِ کلیدِ فقط-صاحب
 *  (`AtRestKeysDesk.kt`) — PARTIAL؛ DPAPI/Keychain در فاز ۲.
 */
interface AtRestKek {
  /** شناسهٔ پایدار، در کلیددان نوشته می‌شود تا عوض شدنِ KEK دیده شود */
  val id: String

  /**
   *  @throws AtRestException `KEY_MISSING` اگر KEK برای همیشه رفته؛
   *  `KEY_UNAVAILABLE` اگر خطا گذراست
   */
  fun wrap(dek: ByteArray): ByteArray

  /** همان دو نوعِ شکستِ `wrap`؛ برچسبِ نخورده ⇒ `KEY_MISSING` */
  fun unwrap(blob: ByteArray): ByteArray
}

/**
 *  کارهای دیسکی که کوچ و کلیددان لازم دارند — جدا، تا آزمون بتواند وسطِ هر
 *  گام «برق را قطع کند». پیاده‌سازیِ واقعی روی `DiskWrite` است (`data/AtRest.kt`).
 */
interface AtRestFiles {
  fun read(f: File): ByteArray

  /** نوشتن و `fsync` — بی جابه‌جایی */
  fun writeSynced(f: File, bytes: ByteArray)

  /** جایگزینیِ اتمیِ `to` با `from` (همان جابه‌جاییِ `DiskWrite`) */
  fun replace(from: File, to: File)

  fun syncDir(dir: File)

  fun delete(f: File): Boolean
}

/**
 *  کلیددانِ رمزِ پاکتی (envelope).
 *
 *  یک کلیدِ دادهٔ ۲۵۶ بیتی (DEK) برای همهٔ فایل‌های این دستگاه، که فقط
 *  **پیچیده در KEK** روی دیسک می‌نشیند (`keyring`). باز شدنش یک بار در هر
 *  اجرای برنامه است؛ بعد رمز و بازکردن درونِ همین فرایند و سریع است — رمز
 *  کردنِ مستقیمِ چند مگابایت دفتر با Keystore در هر فروش کُند است.
 *
 *  ⛔ قاعده‌های بی‌استثنا:
 *   • کلیدِ تازه فقط **پس از** نشستنِ کلیددان روی دیسک (fsync + جابه‌جاییِ
 *     اتمی + بازخوانی و بازکردن) بکار می‌رود؛ وگرنه فایلی رمز می‌شد که
 *     کلیدش با قطعِ برق رفته بود.
 *   • کلیددانِ خوانده‌نشده هرگز پاک یا رونویسی نمی‌شود؛ کنار گذاشته
 *     می‌شود (`keyring.lost-<زمان>`).
 *   • خطای گذرا (`KEY_UNAVAILABLE`) هرگز کلیدِ تازه نمی‌سازد.
 */
class AtRestKeyring(
  private val file: File,
  private val kek: AtRestKek,
  private val io: AtRestFiles,
  private val random: SecureRandom = SecureRandom(),
  private val clock: () -> Long = System::currentTimeMillis,
) {

  sealed class State {
    object None : State()
    data class Ready(val kid: String) : State()

    /** برای همیشه: KEK رفته یا کلیددان خراب است. فایل‌های رمزدارِ قبلی باز نمی‌شوند. */
    data class Lost(val reason: String) : State()

    /** همین حالا نه؛ دوباره تلاش شود */
    data class Unavailable(val reason: String) : State()
  }

  private class Loaded(val kid: String, val key: SecretKey)

  private val lock = Any()
  @Volatile private var cached: Loaded? = null

  fun state(): State = synchronized(lock) {
    cached?.let { return State.Ready(it.kid) }
    load()
  }

  /**
   *  کلیدِ یک شناسه، برای باز کردن.
   *  @return `null` یعنی این کلید روی این دستگاه نیست (`KEY_MISSING`)
   *  @throws AtRestException `KEY_UNAVAILABLE` اگر Keystore همین حالا جواب نمی‌دهد
   */
  fun keyFor(kid: String): SecretKey? = synchronized(lock) {
    when (val s = state()) {
      is State.Ready -> if (s.kid == kid) cached?.key else null
      is State.Unavailable -> throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, s.reason)
      else -> null
    }
  }

  /**
   *  کلید برای رمز کردن: اگر نیست ساخته می‌شود؛ اگر برای همیشه رفته، کلیددانِ
   *  قبلی کنار می‌رود و کلیدِ تازه ساخته می‌شود.
   *  @return `null` اگر کلید ساخته/باز نشد — صدازننده **ساده** می‌نویسد، نه اینکه نوشتن را بشکند
   */
  fun forWrite(): Pair<String, SecretKey>? = synchronized(lock) {
    when (state()) {
      is State.Ready -> cached?.let { it.kid to it.key }
      is State.None -> create()
      is State.Lost -> { setAside(); create() }
      is State.Unavailable -> null
    }
  }

  /** نامِ نسخه‌ای که کلیددانِ ازدست‌رفته به آن کنار رفت، اگر رفت */
  @Volatile var setAsideAs: File? = null
    private set

  private fun load(): State {
    if (!file.exists()) return State.None
    val text = runCatching { String(io.read(file), Charsets.UTF_8) }.getOrElse {
      return State.Unavailable("کلیددان خوانده نشد: ${it.message}")
    }
    val fields = text.lineSequence().mapNotNull { line ->
      val i = line.indexOf('=')
      if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.toMap()
    if (!text.startsWith(HEADER)) return State.Lost("کلیددان خراب است")
    val kid = fields["kid"].orEmpty()
    val kekId = fields["kek"].orEmpty()
    val wrapped = fields["wrapped"]?.let { runCatching { unhex(it) }.getOrNull() }
    if (kid.isEmpty() || wrapped == null) return State.Lost("کلیددان ناقص است")
    if (kekId != kek.id) return State.Lost("کلیدِ اصلیِ دستگاه عوض شده ($kekId ⇐ ${kek.id})")
    val dek = try {
      kek.unwrap(wrapped)
    } catch (e: AtRestException) {
      return if (e.kind == AtRestException.Kind.KEY_UNAVAILABLE) State.Unavailable(e.message ?: "")
      else State.Lost(e.message ?: "کلیدِ اصلیِ دستگاه نیست")
    } catch (e: Exception) {
      return State.Unavailable(e.message ?: e.javaClass.simpleName)
    }
    if (dek.size != DEK_LEN) return State.Lost("کلیدِ داده اندازهٔ درستی ندارد")
    cached = Loaded(kid, SecretKeySpec(dek, "AES"))
    return State.Ready(kid)
  }

  private fun create(): Pair<String, SecretKey>? {
    val dek = ByteArray(DEK_LEN).also { random.nextBytes(it) }
    val kid = "k" + hex(ByteArray(8).also { random.nextBytes(it) })
    val wrapped = runCatching { kek.wrap(dek) }.getOrElse { return null }
    val body = "$HEADER\nkid=$kid\nkek=${kek.id}\nwrapped=${hex(wrapped)}\n".toByteArray(Charsets.UTF_8)
    val tmp = File(file.absoluteFile.parentFile, file.name + ".new")
    return try {
      file.absoluteFile.parentFile?.mkdirs()
      io.writeSynced(tmp, body)
      //  ⛔ کلیددانِ روی دیسک باید خودش باز شود، پیش از اینکه کلیدی بکار برود
      val back = io.read(tmp)
      if (!back.contentEquals(body)) error("کلیددان درست روی دیسک ننشست")
      if (!kek.unwrap(wrapped).contentEquals(dek)) error("کلیدِ پیچیده باز نشد")
      io.replace(tmp, file)
      file.absoluteFile.parentFile?.let { io.syncDir(it) }
      cached = Loaded(kid, SecretKeySpec(dek, "AES"))
      kid to cached!!.key
    } catch (e: Exception) {
      runCatching { io.delete(tmp) }
      null
    }
  }

  /** کلیددانِ ازدست‌رفته پاک نمی‌شود؛ کنار می‌رود */
  private fun setAside() {
    if (!file.exists()) return
    val aside = File(file.absoluteFile.parentFile, "${file.name}.lost-${clock()}")
    runCatching { io.replace(file, aside) }.onSuccess { setAsideAs = aside }
    cached = null
  }

  /** فقط برای آزمون و «بستن و باز کردنِ» برنامه: کلیدِ درونِ حافظه فراموش شود */
  fun forget() = synchronized(lock) { cached = null }

  companion object {
    const val HEADER = "tohid-atrest-keyring 1"
    const val DEK_LEN = 32

    fun hex(b: ByteArray): String {
      val out = StringBuilder(b.size * 2)
      for (x in b) {
        val v = x.toInt() and 0xFF
        out.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0F])
      }
      return out.toString()
    }

    fun unhex(s: String): ByteArray {
      require(s.length % 2 == 0) { "hex" }
      return ByteArray(s.length / 2) { i ->
        ((Character.digit(s[2 * i], 16) shl 4) or Character.digit(s[2 * i + 1], 16)).also { require(it >= 0) }.toByte()
      }
    }

    private const val DIGITS = "0123456789abcdef"
  }
}
