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
  /**
   *  شناسهٔ KEKی که `wrap` همین حالا با آن می‌پیچد؛ کنارِ کلیدِ پیچیده در کلیددان نوشته
   *  می‌شود. ممکن است عوض شود (اندروید: نامِ تازه به‌جای کلیدِ همیشه‌ازکارافتاده)؛ کلیدِ
   *  قبلی هرگز پاک نمی‌شود و `unwrap` با شناسهٔ خودِ کلیددان سراغش می‌رود.
   */
  val id: String

  /**
   *  @throws AtRestException `KEY_MISSING` اگر KEK برای همیشه رفته؛
   *  `KEY_UNAVAILABLE` اگر خطا گذراست
   */
  fun wrap(dek: ByteArray): ByteArray

  /**
   *  همان دو نوعِ شکستِ `wrap`؛ برچسبِ نخورده یا KEKِ ناشناختهٔ `kekId` ⇒ `KEY_MISSING`
   *  @param kekId شناسه‌ای که کنارِ همین کلیدِ پیچیده نوشته شده بود
   */
  fun unwrap(blob: ByteArray, kekId: String = id): ByteArray
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
 *     می‌شود (`keyring.lost-<زمان>`). کنار نرفت ⇒ کلیدِ تازه هم ساخته نمی‌شود.
 *   • دو نسخه: `keyring` و `keyring.bak` (همان بایت‌ها). خرابیِ یکی از روی
 *     دیگری درمان می‌شود؛ نسخهٔ خراب کنار می‌رود (`keyring.corrupt-<زمان>`).
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

  /** کلیدهای کلیددان‌های قدیمی‌تر (`.lost-*`، `.corrupt-*`) که هنوز باز می‌شوند — شرح سرِ `keyFor` */
  private val older = HashMap<String, SecretKey>()

  /**
   *  نشانِ «این اجرای برنامه» (بازبینیِ #331): `Lost` فقط وقتی اعلام می‌شود که همان شکست در
   *  **اجرای دوم** هم تکرار شود (`keyring.suspect`). خطای گذرای Keystore که خودش را
   *  همیشگی نشان داد، کلیددان را کنار نمی‌گذارد و کلیدِ تازه نمی‌سازد.
   */
  @Volatile private var launch: String = newLaunch()

  private fun newLaunch(): String = hex(ByteArray(8).also { random.nextBytes(it) })

  private val suspect: File get() = File(dir, file.name + SUSPECT_SUFFIX)

  fun state(): State = synchronized(lock) {
    cached?.let { return State.Ready(it.kid) }
    when (val s = load()) {
      is State.Lost -> confirmLost(s)
      is State.Ready -> { runCatching { if (suspect.exists()) io.delete(suspect) }; s }
      else -> s
    }
  }

  /** نخستین بار در این اجرا ⇒ نشان و `Unavailable`؛ نشانِ اجرای پیشین ⇒ `Lost` */
  private fun confirmLost(s: State.Lost): State {
    val seen = runCatching { if (suspect.exists()) String(io.read(suspect), Charsets.UTF_8).trim() else "" }.getOrDefault("")
    if (seen.isNotEmpty() && seen != launch) return s
    if (seen.isEmpty()) runCatching { dir.mkdirs(); io.writeSynced(suspect, launch.toByteArray(Charsets.UTF_8)) }
    return State.Unavailable("${s.reason} — در اجرای بعدیِ برنامه دوباره سنجیده می‌شود")
  }

  /**
   *  کلیدِ یک شناسه، برای باز کردن.
   *  @return `null` یعنی این کلید روی این دستگاه نیست (`KEY_MISSING`)
   *  @throws AtRestException `KEY_UNAVAILABLE` اگر Keystore همین حالا جواب نمی‌دهد
   */
  fun keyFor(kid: String): SecretKey? = synchronized(lock) {
    when (val s = state()) {
      is State.Ready -> if (s.kid == kid) return cached?.key
      is State.Unavailable -> throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, s.reason)
      else -> Unit
    }
    olderKey(kid)
  }

  /**
   *  فایلی که با کلیددانِ قدیمی‌تری رمز شده (پس از `Lost`ی که بعد معلوم شد گذرا بود، یا
   *  پیش از درمانِ نسخهٔ دوم): کلیددان‌های کنارگذاشته هم گشته می‌شوند. هیچ‌کدام پاک نمی‌شوند،
   *  پس تا KEKشان زنده است فایل‌هایشان هم باز می‌شوند.
   */
  private fun olderKey(kid: String): SecretKey? {
    older[kid]?.let { return it }
    val prefix = file.name + "."
    val copies = dir.listFiles().orEmpty().filter {
      it.isFile && it.name.startsWith(prefix) &&
        (it.name == second.name || it.name.contains(".lost-") || it.name.contains(".corrupt-"))
    }
    for (f in copies.sortedByDescending { it.name }) {
      val c = readCopy(f) as? Copy.Good ?: continue
      older[c.kid] = SecretKeySpec(c.dek, "AES")
      if (c.kid == kid) return older[kid]
    }
    return null
  }

  /**
   *  کلید برای رمز کردن: اگر نیست ساخته می‌شود؛ اگر برای همیشه رفته، کلیددانِ
   *  قبلی کنار می‌رود و کلیدِ تازه ساخته می‌شود.
   *
   *  ⛔ اگر کنار گذاشتن نشد، کلیدِ تازه هم ساخته **نمی‌شود** (بازبینیِ #331): وگرنه
   *  `create` کلیددانِ قدیمی را رونویسی می‌کرد و فایل‌های رمزدارِ قبلی — که شاید
   *  KEKشان برگردد — برای همیشه بی‌کلید می‌ماندند.
   *
   *  @return `null` اگر کلید ساخته/باز نشد — صدازننده **ساده** می‌نویسد، نه اینکه نوشتن را بشکند
   */
  fun forWrite(): Pair<String, SecretKey>? = synchronized(lock) {
    when (state()) {
      is State.Ready -> cached?.let { it.kid to it.key }
      is State.None -> create()
      is State.Lost -> if (setAside()) create() else null
      is State.Unavailable -> null
    }
  }

  /** نامِ نسخه‌ای که کلیددانِ ازدست‌رفته به آن کنار رفت، اگر رفت */
  @Volatile var setAsideAs: File? = null
    private set

  private val dir: File get() = file.absoluteFile.parentFile ?: File(".")

  /**
   *  نسخهٔ دومِ کلیددان (بازبینیِ #331) — همان بایت‌ها، نوشته با همان کناری + fsync +
   *  جابه‌جاییِ اتمی. یک بایتِ خراب در یکی دیگر یعنی «همه‌چیز Lost» نیست.
   */
  private val second: File get() = File(dir, file.name + BACKUP_SUFFIX)

  /** خواندنِ یک نسخهٔ کلیددان، بی هیچ نوشتنی */
  private sealed class Copy {
    object Missing : Copy()
    class Bad(val reason: String) : Copy()
    class Busy(val reason: String) : Copy()
    class Good(val kid: String, val dek: ByteArray, val bytes: ByteArray) : Copy()
  }

  private fun readCopy(f: File): Copy {
    if (!f.exists()) return Copy.Missing
    val bytes = runCatching { io.read(f) }.getOrElse {
      return Copy.Busy("کلیددان خوانده نشد: ${it.message}")
    }
    val text = String(bytes, Charsets.UTF_8)
    if (!text.startsWith(HEADER)) return Copy.Bad("کلیددان خراب است")
    val fields = text.lineSequence().mapNotNull { line ->
      val i = line.indexOf('=')
      if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.toMap()
    val kid = fields["kid"].orEmpty()
    val kekId = fields["kek"].orEmpty()
    val wrapped = fields["wrapped"]?.let { runCatching { unhex(it) }.getOrNull() }
    if (kid.isEmpty() || kekId.isEmpty() || wrapped == null) return Copy.Bad("کلیددان ناقص است")
    val dek = try {
      kek.unwrap(wrapped, kekId)
    } catch (e: AtRestException) {
      return if (e.kind == AtRestException.Kind.KEY_UNAVAILABLE) Copy.Busy(e.message ?: "")
      else Copy.Bad(e.message ?: "کلیدِ اصلیِ دستگاه نیست")
    } catch (e: Exception) {
      return Copy.Busy(e.message ?: e.javaClass.simpleName)
    }
    if (dek.size != DEK_LEN) return Copy.Bad("کلیدِ داده اندازهٔ درستی ندارد")
    return Copy.Good(kid, dek, bytes)
  }

  private fun load(): State {
    val main = readCopy(file)
    when (main) {
      is Copy.Good -> {
        cached = Loaded(main.kid, SecretKeySpec(main.dek, "AES"))
        //  نسخهٔ دوم نیست یا کهنه است ⇒ از روی نسخهٔ سالم (بی اثر روی نسخهٔ اصلی)
        runCatching { if (!second.exists() || !io.read(second).contentEquals(main.bytes)) writeCopy(second, main.bytes) }
        return State.Ready(main.kid)
      }
      is Copy.Busy -> return State.Unavailable(main.reason)
      else -> Unit
    }
    return when (val other = readCopy(second)) {
      is Copy.Good -> {
        cached = Loaded(other.kid, SecretKeySpec(other.dek, "AES"))
        //  نسخهٔ اصلی خراب یا نبود ⇒ خرابش کنار می‌رود (هرگز رونویسی نمی‌شود) و از نسخهٔ دوم برمی‌گردد
        runCatching {
          val asideOk = !file.exists() || run {
            io.replace(file, File(dir, "${file.name}.corrupt-${clock()}"))
            !file.exists()
          }
          if (asideOk) writeCopy(file, other.bytes)
        }
        State.Ready(other.kid)
      }
      is Copy.Busy -> State.Unavailable(other.reason)
      is Copy.Missing -> if (main is Copy.Bad) State.Lost(main.reason) else State.None
      is Copy.Bad -> State.Lost((main as? Copy.Bad)?.reason ?: other.reason)
    }
  }

  /** کناری + fsync + بازخوانی ⇐ جابه‌جاییِ اتمی ⇐ fsyncِ پوشه */
  private fun writeCopy(target: File, body: ByteArray) {
    val tmp = File(dir, target.name + ".new")
    try {
      dir.mkdirs()
      io.writeSynced(tmp, body)
      if (!io.read(tmp).contentEquals(body)) error("کلیددان درست روی دیسک ننشست")
      io.replace(tmp, target)
      io.syncDir(dir)
    } catch (e: Exception) {
      runCatching { io.delete(tmp) }
      throw e
    }
  }

  private fun create(): Pair<String, SecretKey>? {
    //  ⛔ هیچ کلیددانی (اصلی یا دوم) رونویسی نمی‌شود — فقط پس از کنار رفتنِ هر دو
    if (file.exists() || second.exists()) return null
    val dek = ByteArray(DEK_LEN).also { random.nextBytes(it) }
    val kid = "k" + hex(ByteArray(8).also { random.nextBytes(it) })
    val wrapped = runCatching { kek.wrap(dek) }.getOrElse { return null }
    //  پس از `wrap`: اگر KEK نامِ تازه گرفت، همان نوشته می‌شود
    val kekId = kek.id
    val body = "$HEADER\nkid=$kid\nkek=$kekId\nwrapped=${hex(wrapped)}\n".toByteArray(Charsets.UTF_8)
    return try {
      //  ⛔ کلیددانِ روی دیسک باید خودش باز شود، پیش از اینکه کلیدی بکار برود
      if (!kek.unwrap(wrapped, kekId).contentEquals(dek)) error("کلیدِ پیچیده باز نشد")
      writeCopy(file, body)
      //  نسخهٔ دوم: شکستش ساختن را نمی‌شکند؛ `load` بارِ بعد از روی نسخهٔ اصلی می‌سازدش
      runCatching { writeCopy(second, body) }
      cached = Loaded(kid, SecretKeySpec(dek, "AES"))
      kid to cached!!.key
    } catch (e: Exception) {
      null
    }
  }

  /**
   *  کلیددانِ ازدست‌رفته پاک نمی‌شود؛ هر دو نسخه کنار می‌روند.
   *  @return `false` اگر یکی کنار نرفت — آن‌وقت هیچ کلیدِ تازه‌ای ساخته نمی‌شود
   */
  private fun setAside(): Boolean {
    val at = clock()
    for (f in listOf(file, second)) {
      if (!f.exists()) continue
      val aside = File(dir, "${f.name}.lost-$at")
      val moved = runCatching { io.replace(f, aside) }.isSuccess && !f.exists() && aside.exists()
      if (!moved) return false
      if (f == file) setAsideAs = aside
    }
    cached = null
    runCatching { if (suspect.exists()) io.delete(suspect) }
    return true
  }

  /** فقط برای آزمون و «بستن و باز کردنِ» برنامه: کلیدِ درونِ حافظه فراموش شود */
  fun forget() = synchronized(lock) {
    cached = null
    older.clear()
    launch = newLaunch()
  }

  companion object {
    const val HEADER = "tohid-atrest-keyring 1"
    const val BACKUP_SUFFIX = ".bak"
    const val SUSPECT_SUFFIX = ".suspect"
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
