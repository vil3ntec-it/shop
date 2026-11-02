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

/** فقط UTF-8 — بقیهٔ نویسه‌نگاشت‌ها (ISO-8859-1، UTF-16) در iOS نیستند و فایل‌شان بیرون می‌ماند */
class Charset internal constructor(private val name: String) {
  fun name(): String = name
  override fun toString(): String = name
}

object Charsets {
  val UTF_8: Charset = Charset("UTF-8")
}

fun String.toByteArray(charset: Charset): ByteArray = encodeToByteArray()

fun ByteArray.toString(charset: Charset): String = decodeToString()

fun String(bytes: ByteArray, charset: Charset): String = bytes.decodeToString()

fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String =
  bytes.decodeToString(offset, offset + length)

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
 *  `Thread` فقط برای کارخانهٔ رشتهٔ `Executors` (که در iOS صدا زده نمی‌شود، ‎java/util/concurrent‎)؛
 *  ساختنِ رشتهٔ خام در iOS نیست ⇐ `start()` خطا می‌دهد تا بی‌صدا کاری نکند.
 */
class Thread(private val target: Runnable? = null, val name: String = "") {
  var isDaemon: Boolean = false
  fun start(): Unit = throw UnsupportedOperationException("iOS: Thread ِ خام نیست؛ کوروتین یا Executors")
}
