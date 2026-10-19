package ir.vil3ntec.tohid.core.sync.v3

/**
 *  ساعتِ منطقیِ ترکیبی (HLC) سمتِ برنامه — Sync v3، SYN-05 و بخشِ ۳.۸.۱ بندِ ۸.
 *
 *  ⛔ **قرینهٔ دقیقِ `server/src/lib/hlc.js`.** هر سه (سرور، این فایل و
 *  `license/sync-v3-engine.js`) با یک فایلِ بردارِ مرجع سنجیده می‌شوند:
 *  `core/test-vectors/hlc-v1.json` که `server/scripts/sync-v3-vectors.js` از
 *  **خودِ پیاده‌سازیِ سرور** می‌سازد. یکی را عوض کنی، هر سه را.
 *
 *  ── شکل ───────────────────────────────────────────────────────────
 *    "<wall:15 رقم>:<counter:6 رقم>:<node>"   000001760000000000:000003:dev-a
 *  پهنای ثابت ⇒ مقایسهٔ رشته همان مقایسهٔ زمان است.
 *
 *  ── ساعتِ دستگاه ───────────────────────────────────────────────────
 *  `now` «زمانِ تصحیح‌شده» است (ساعتِ دستگاه + اختلافی که از `server_time`ِ
 *  پاسخ‌ها نگه داشته شده)؛ HLC، ULID و تاریخِ سند از همان ساخته می‌شوند.
 *
 *  Kotlinِ خالص (commonMain) — بی java.*، تا بی تغییر در هر هدفِ KMP بماند.
 */
data class HlcTime(val wall: Long, val counter: Long, val node: String)

object Hlc {
  const val SKEW_MS: Long = 5L * 60 * 1000
  const val WALL_DIGITS: Int = 15
  const val COUNTER_DIGITS: Int = 6
  const val MAX_COUNTER: Long = 999_999L
  private const val MAX_WALL: Long = 999_999_999_999_999L

  //  `matchEntire` نه `$`: در جاوا `$` پیش از «\n»ِ پایانی هم جور می‌شود و JS نه
  private val RE = Regex("(\\d{1,15}):(\\d{1,6}):([A-Za-z0-9_.-]{1,64})")

  fun parse(s: String?): HlcTime? {
    if (s == null) return null
    val m = RE.matchEntire(s) ?: return null
    val wall = m.groupValues[1].toLongOrNull() ?: return null
    val counter = m.groupValues[2].toLongOrNull() ?: return null
    return HlcTime(wall, counter, m.groupValues[3])
  }

  fun format(t: HlcTime): String = format(t.wall, t.counter, t.node)

  fun format(wall: Long, counter: Long, node: String): String =
    maxOf(0L, wall).toString().padStart(WALL_DIGITS, '0') + ":" +
      maxOf(0L, counter).toString().padStart(COUNTER_DIGITS, '0') + ":" + node

  /** شکلِ قانونی (با صفرهای پیشرو)، یا `""` اگر نادرست باشد. */
  fun canonical(s: String?): String = parse(s)?.let { format(it) } ?: ""

  /** −۱ / ۰ / ۱ */
  fun compare(a: HlcTime, b: HlcTime): Int {
    if (a.wall != b.wall) return if (a.wall < b.wall) -1 else 1
    if (a.counter != b.counter) return if (a.counter < b.counter) -1 else 1
    if (a.node == b.node) return 0
    return if (a.node < b.node) -1 else 1
  }

  /** دو رشته؛ رشتهٔ نادرست مثلِ سرور پرتاب می‌کند. */
  fun compare(a: String, b: String): Int =
    compare(requireNotNull(parse(a)) { "HLC نادرست: $a" }, requireNotNull(parse(b)) { "HLC نادرست: $b" })

  /** زمانِ سادهٔ op v2 (`ts`) ⇒ HLC. منفی یا صفر ⇒ صفر (همیشه بازنده). */
  fun fromTs(ts: Long, counter: Int, node: String): String {
    val wall = if (ts > 0) minOf(ts, MAX_WALL) else 0L
    return format(wall, minOf(maxOf(0, counter).toLong(), MAX_COUNTER), node)
  }
}

/**
 *  ساعتِ یک گره — همان `createClock`ِ سرور.
 *
 *  @param now زمانِ تصحیح‌شده به میلی‌ثانیه
 */
class HlcClock(
  val node: String,
  private val now: () -> Long,
  val skewMs: Long = Hlc.SKEW_MS,
) {
  data class Recv(val skewed: Boolean, val hlc: String)

  private var lastWall = 0L
  private var lastCounter = 0L

  private fun bump(wall: Long, counter: Long): String {
    var w = wall
    var c = counter
    if (c > Hlc.MAX_COUNTER) { w += 1; c = 0 }
    lastWall = w
    lastCounter = c
    return Hlc.format(w, c, node)
  }

  /** رویدادِ تازهٔ همین دستگاه. */
  fun tick(): String {
    val pt = now()
    return if (pt > lastWall) bump(pt, 0) else bump(lastWall, lastCounter + 1)
  }

  /** HLCِ گرهٔ دیگر رسید. کج (بیش از `skewMs` جلوتر) ⇒ ساعت دست نمی‌خورد. */
  fun recv(remote: String?): Recv {
    val r = Hlc.parse(remote)
    val pt = now()
    if (r == null) return Recv(false, tick())
    if (r.wall > pt + skewMs) return Recv(true, Hlc.format(lastWall, lastCounter, node))
    val wall = maxOf(lastWall, r.wall, pt)
    val counter = when {
      wall == lastWall && wall == r.wall -> maxOf(lastCounter, r.counter) + 1
      wall == lastWall -> lastCounter + 1
      wall == r.wall -> r.counter + 1
      else -> 0L
    }
    return Recv(false, bump(wall, counter))
  }

  /** بی شرطِ کجی — فقط برای راه‌اندازی از روی حالِ ذخیره‌شده. */
  fun observe(seen: String?) {
    val s = Hlc.parse(seen) ?: return
    if (s.wall > lastWall || (s.wall == lastWall && s.counter > lastCounter)) {
      lastWall = s.wall
      lastCounter = s.counter
    }
  }

  fun isSkewed(remote: String?): Boolean {
    val r = Hlc.parse(remote) ?: return false
    return r.wall > now() + skewMs
  }

  fun peek(): String = Hlc.format(lastWall, lastCounter, node)
}
