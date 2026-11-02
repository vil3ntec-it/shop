package ir.vil3ntec.tohid.ios.jvm

import kotlin.concurrent.Volatile as KVolatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.floor
import kotlin.math.pow
import kotlin.native.identityHashCode
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import platform.Foundation.NSRecursiveLock

/*
 *  shimِ JVM برای iOS — همان کارِ `desktop/src/shim` برای نام‌های `android.*`، این‌بار برای
 *  چند نامِ JDK که کدِ مشترک بی import صدا می‌زند (`System`، `Math`، `Character`، `Charsets`،
 *  `synchronized`، `@Volatile`). `ios/shared/build.gradle.kts` سرِ هر فایلِ کپی‌شده
 *  `import ir.vil3ntec.tohid.ios.jvm.*` می‌گذارد.
 *
 *  ⛔ رفتار باید **همان JDK** باشد، نه «نزدیک به آن»: این توابع در فروش و پول هم صدا زده
 *  می‌شوند (گرد کردن، باقی‌مانده). هر جا JDK قاعدهٔ ویژه دارد (NaN، سرریز، گرد کردنِ نیمه)
 *  همان این‌جا نوشته شده.
 *  فقط آنچه کدِ مشترک امروز به کار می‌برد؛ نامِ تازه با نیازِ واقعی می‌آید.
 */

/** `kotlin.jvm.Volatile` در اندروید و کامپیوتر خودکار import می‌شود؛ در iOS همان `kotlin.concurrent.Volatile` */
typealias Volatile = KVolatile

private val monotonicStart = TimeSource.Monotonic.markNow()

object System {
  @OptIn(ExperimentalTime::class)
  fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

  /** مثلِ JDK: فقط برای فاصلهٔ دو لحظه معنا دارد، نه ساعتِ دیواری */
  fun nanoTime(): Long = monotonicStart.elapsedNow().inWholeNanoseconds

  @OptIn(ExperimentalNativeApi::class)
  fun identityHashCode(x: Any?): Int = x?.identityHashCode() ?: 0

  /** ویژگی‌های JVM (`os.name`، `user.home`…) در iOS نیستند — همان `null` ِ کلیدِ ناشناختهٔ JDK */
  fun getProperty(key: String): String? = null
}

object Math {
  const val PI: Double = kotlin.math.PI

  /** `Math.round(double)` ِ JDK: نزدیک‌ترین، نیمه رو به +∞؛ NaN ⇐ ۰؛ بیرون از بازه ⇐ سقف/کفِ Long */
  fun round(x: Double): Long {
    val f = floor(x)
    return (if (x - f >= 0.5) f + 1.0 else f).toLong()
  }

  /** `Math.round(float)` ِ JDK ⇐ Int */
  fun round(x: Float): Int {
    val f = floor(x)
    return (if (x - f >= 0.5f) f + 1f else f).toInt()
  }

  fun floor(x: Double): Double = kotlin.math.floor(x)
  fun ceil(x: Double): Double = kotlin.math.ceil(x)

  /** نیمه رو به زوج — همان `kotlin.math.round` */
  fun rint(x: Double): Double = kotlin.math.round(x)

  fun abs(x: Int): Int = kotlin.math.abs(x)
  fun abs(x: Long): Long = kotlin.math.abs(x)
  fun abs(x: Double): Double = kotlin.math.abs(x)
  fun abs(x: Float): Float = kotlin.math.abs(x)

  fun floorMod(x: Int, y: Int): Int = x.mod(y)
  fun floorMod(x: Long, y: Long): Long = x.mod(y)
  fun floorMod(x: Long, y: Int): Int = x.mod(y)
  fun floorDiv(x: Int, y: Int): Int = x.floorDiv(y)
  fun floorDiv(x: Long, y: Long): Long = x.floorDiv(y)
  fun floorDiv(x: Long, y: Int): Long = x.floorDiv(y.toLong())

  fun addExact(x: Int, y: Int): Int {
    val r = x + y
    if (((x xor r) and (y xor r)) < 0) throw ArithmeticException("integer overflow")
    return r
  }

  fun addExact(x: Long, y: Long): Long {
    val r = x + y
    if (((x xor r) and (y xor r)) < 0) throw ArithmeticException("long overflow")
    return r
  }

  fun multiplyExact(x: Int, y: Int): Int {
    val r = x.toLong() * y.toLong()
    if (r.toInt().toLong() != r) throw ArithmeticException("integer overflow")
    return r.toInt()
  }

  /** همان الگوریتمِ JDK ِ `Math.multiplyExact(long, long)` */
  fun multiplyExact(x: Long, y: Long): Long {
    val r = x * y
    val ax = kotlin.math.abs(x)
    val ay = kotlin.math.abs(y)
    if ((ax or ay) ushr 31 != 0L) {
      if ((y != 0L && r / y != x) || (x == Long.MIN_VALUE && y == -1L)) throw ArithmeticException("long overflow")
    }
    return r
  }

  fun multiplyExact(x: Long, y: Int): Long = multiplyExact(x, y.toLong())

  fun random(): Double = kotlin.random.Random.nextDouble()

  fun toRadians(deg: Double): Double = deg / 180.0 * kotlin.math.PI

  fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
  fun max(a: Long, b: Long): Long = kotlin.math.max(a, b)
  fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
  fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
  fun min(a: Long, b: Long): Long = kotlin.math.min(a, b)
  fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
  fun pow(a: Double, b: Double): Double = a.pow(b)
  fun sqrt(a: Double): Double = kotlin.math.sqrt(a)
}

object Character {
  /** `Character.digit` ِ JDK رقم‌های یونیکد (فارسی، عربی) را هم می‌شناسد؛ `digitToIntOrNull` هم */
  fun digit(ch: Char, radix: Int): Int = ch.digitToIntOrNull(radix) ?: -1
  fun isDigit(ch: Char): Boolean = ch.isDigit()
  fun isLetter(ch: Char): Boolean = ch.isLetter()
  fun isWhitespace(ch: Char): Boolean = ch.isWhitespace()
  fun isHighSurrogate(ch: Char): Boolean = ch.isHighSurrogate()
  fun isLowSurrogate(ch: Char): Boolean = ch.isLowSurrogate()
}

/** UTF-8، US-ASCII و ISO-8859-1 (همان JDK: نویسهٔ بیرون از بازه ⇐ «?»)؛ UTF-16 در iOS نیست و فایلش بیرون می‌ماند */
class Charset internal constructor(private val name: String) {
  fun name(): String = name
  override fun toString(): String = name
  fun newDecoder(): java.nio.charset.CharsetDecoder = java.nio.charset.CharsetDecoder()
}

object Charsets {
  val UTF_8: Charset = Charset("UTF-8")
  val US_ASCII: Charset = Charset("US-ASCII")
  val ISO_8859_1: Charset = Charset("ISO-8859-1")
  val UTF_16LE: Charset = Charset("UTF-16LE")
  val UTF_16BE: Charset = Charset("UTF-16BE")
}

private fun utf16(b: ByteArray, off: Int, len: Int, little: Boolean): String = buildString(len / 2) {
  var i = off
  while (i + 1 < off + len) {
    val lo = b[if (little) i else i + 1].toInt() and 0xFF
    val hi = b[if (little) i + 1 else i].toInt() and 0xFF
    append(((hi shl 8) or lo).toChar())
    i += 2
  }
  if ((len and 1) == 1) append('\uFFFD')
}

fun charset(name: String): Charset = when (name.uppercase()) {
  "UTF-8", "UTF8" -> Charsets.UTF_8
  "US-ASCII", "ASCII" -> Charsets.US_ASCII
  "ISO-8859-1", "LATIN1" -> Charsets.ISO_8859_1
  else -> throw IllegalArgumentException("iOS: نویسه‌نگاشتِ $name")
}

private fun narrow(s: String, max: Int): ByteArray = ByteArray(s.length) { i -> s[i].code.let { if (it <= max) it.toByte() else '?'.code.toByte() } }

private fun widen(b: ByteArray, off: Int, len: Int): String = buildString(len) { for (i in off until off + len) append((b[i].toInt() and 0xFF).toChar()) }

fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray = when (charset) {
  Charsets.US_ASCII -> narrow(this, 0x7F)
  Charsets.ISO_8859_1 -> narrow(this, 0xFF)
  else -> encodeToByteArray()
}

fun ByteArray.toString(charset: Charset): String = String(this, 0, size, charset)

fun String(bytes: ByteArray, charset: Charset): String = String(bytes, 0, bytes.size, charset)

fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String = when (charset) {
  Charsets.UTF_8 -> bytes.decodeToString(offset, offset + length)
  Charsets.UTF_16LE -> utf16(bytes, offset, length, little = true)
  Charsets.UTF_16BE -> utf16(bytes, offset, length, little = false)
  else -> widen(bytes, offset, length).let { s -> if (charset == Charsets.US_ASCII) s.map { if (it.code < 0x80) it else '\uFFFD' }.joinToString("") else s }
}

/*
 *  `synchronized(lock) { … }` ِ JVM. iOS قفلِ روی هر شیء ندارد؛ یک قفلِ بازگشتیِ سراسری همان
 *  تضمینِ «یک رشته در یک زمان» را می‌دهد (درشت‌تر، ولی درست؛ بازگشتی، پس قفلِ تودرتو بن‌بست نمی‌شود).
 *  `@Synchronized` ِ روی تابع را ساختِ iOS به همین `synchronized(Unit) { … }` برمی‌گرداند
 *  (`unsynchronize` در ios/shared/build.gradle.kts).
 */
@PublishedApi
internal val globalLock = NSRecursiveLock()

@OptIn(ExperimentalContracts::class)
inline fun <R> synchronized(@Suppress("UNUSED_PARAMETER") lock: Any, block: () -> R): R {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  globalLock.lock()
  try {
    return block()
  } finally {
    globalLock.unlock()
  }
}

/*
 *  `X::class.java` و `x.javaClass` — کدِ مشترک فقط نامِ رده را می‌خواهد (پیامِ خطا، کلیدِ گزارش).
 *  `Context.getSystemService(X::class.java)` در iOS همیشه null است (‎android/content/Context.kt‎).
 */
class Class<T : Any>(private val k: kotlin.reflect.KClass<T>) {
  val simpleName: String get() = k.simpleName ?: ""
  val name: String get() = k.qualifiedName ?: simpleName
  override fun toString(): String = "class $name"
}

val <T : Any> kotlin.reflect.KClass<T>.java: Class<T> get() = Class(this)

val <T : Any> T.javaClass: Class<T> get() = Class(this::class) as Class<T>

/** `Map.putIfAbsent` ِ JDK: نبود یا null ⇐ می‌گذارد و null برمی‌گرداند؛ وگرنه مقدارِ موجود */
fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
  val old = get(key)
  if (old == null) put(key, value)
  return old
}

/** `java.lang.Runnable` (در JVM خودکار import می‌شود) */
fun interface Runnable {
  fun run()
}

/*
 *  `Thread` ِ JVM: کارخانهٔ رشتهٔ `Executors` (در iOS صدا زده نمی‌شود، ‎java/util/concurrent‎) و `Thread { … }.start()`.
 */
class Thread(private val target: Runnable? = null, val name: String = "") {
  var isDaemon: Boolean = false
  /** کار روی صفِ پس‌زمینهٔ GCD (iOS رشتهٔ خامِ JVM ندارد؛ همان «کار جدا از رشتهٔ اصلی») */
  fun start() {
    val r = target ?: return
    platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { r.run() }
  }

  fun interface UncaughtExceptionHandler {
    fun uncaughtException(t: Thread, e: Throwable)
  }

  companion object {
    private var handler: UncaughtExceptionHandler? = null

    fun currentThread(): Thread = Thread(name = if (platform.Foundation.NSThread.isMainThread) "main" else "worker")

    fun getDefaultUncaughtExceptionHandler(): UncaughtExceptionHandler? = handler

    /** همان «خطای گرفته‌نشده» ِ JVM روی قلابِ Kotlin/Native (پس از آن برنامه بسته می‌شود، مثلِ اندروید) */
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    fun setDefaultUncaughtExceptionHandler(h: UncaughtExceptionHandler?) {
      handler = h
      if (h != null) kotlin.native.setUnhandledExceptionHook { e -> h.uncaughtException(currentThread(), e) }
    }

    /** همان `Thread.sleep` (مسدود؛ فقط درونِ `Dispatchers.IO`، مثلِ اندروید) */
    fun sleep(millis: Long) {
      if (millis > 0) platform.posix.usleep((millis * 1000).coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt())
    }
  }
}

/** `java.lang.SecurityException` (در JVM خودکار import می‌شود) */
open class SecurityException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

/** `toSortedSet()` ِ JVM (`java.util.TreeSet`) */
fun <T : Comparable<T>> Iterable<T>.toSortedSet(): java.util.TreeSet<T> = java.util.TreeSet<T>().also { s -> forEach { s.add(it) } }

fun <T> Iterable<T>.toSortedSet(comparator: Comparator<in T>): java.util.TreeSet<T> = java.util.TreeSet(comparator).also { s -> forEach { s.add(it) } }

/** `sortedMapOf`/`toSortedMap` ِ JVM (`java.util.TreeMap`) */
fun <K : Comparable<K>, V> sortedMapOf(vararg pairs: Pair<K, V>): java.util.TreeMap<K, V> = java.util.TreeMap<K, V>().also { m -> pairs.forEach { m[it.first] = it.second } }

fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): java.util.TreeMap<K, V> = java.util.TreeMap<K, V>().also { m -> forEach { (k, v) -> m[k] = v } }

fun <K, V> Map<out K, V>.toSortedMap(comparator: Comparator<in K>): java.util.TreeMap<K, V> = java.util.TreeMap<K, V>(comparator).also { m -> forEach { (k, v) -> m[k] = v } }

/** `kotlin.io.DEFAULT_BUFFER_SIZE` ِ JVM */
const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

/** `toBigDecimal()` ِ Kotlin/JVM — همان رقم‌های `toString()` (‎java/math/BigDecimal.kt‎) */
fun Double.toBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(this.toString())
fun Float.toBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(this.toString())
fun Long.toBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(this.toString())
fun Int.toBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(this.toString())
fun String.toBigDecimal(): java.math.BigDecimal = java.math.BigDecimal(this)
fun String.toBigDecimalOrNull(): java.math.BigDecimal? = runCatching { java.math.BigDecimal(this) }.getOrNull()

/** `kotlin.concurrent.withLock` ِ JVM (ساختِ iOS import ِ آن را به این‌جا برمی‌گرداند؛ ‎build.gradle.kts‎) */
@OptIn(ExperimentalContracts::class)
inline fun <T> java.util.concurrent.locks.Lock.withLock(action: () -> T): T {
  contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
  lock()
  try {
    return action()
  } finally {
    unlock()
  }
}

/** `kotlin.io` ِ JVM: جریانِ خواندن از آرایه */
fun ByteArray.inputStream(): java.io.ByteArrayInputStream = java.io.ByteArrayInputStream(this)
fun ByteArray.inputStream(offset: Int, length: Int): java.io.ByteArrayInputStream = java.io.ByteArrayInputStream(this, offset, length)
