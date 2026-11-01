package ir.vil3ntec.tohid.core.storefront

/*
 *  صفِ آفلاینِ صفحهٔ «ویترین و بازار» — M1 ِ docs/MARKETPLACE-CONTRACT-fa.md، §۳۱.۲ بندِ ۲.
 *
 *  ── چه در صف می‌رود ─────────────────────────────────────────────────
 *    • SETTINGS  ⇒ `PUT /api/shop/storefront`          (روشن/خاموش، عنوان، ساعت، تعطیلی…)
 *    • DELIVERY  ⇒ `PUT /api/shop/storefront/delivery` (ناحیه‌ها و بازه‌ها)
 *    • MARKET    ⇒ `PUT /api/shop/market`              (دسته، محله، listed)
 *    • PRESENCE  ⇒ `POST /api/shop/storefront/presence` («آنلاین تا…»)
 *  ترتیبِ فرستادن همین است: ویترین باید پیش از اعلامِ حضور ساخته شده باشد.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *    • هر (دکان، نوع) فقط یک ردیف دارد؛ ویرایشِ تازه جای کهنه را می‌گیرد (هر سه PUT
 *      کلِ فرم را می‌فرستند و اعلامِ تازه اعلامِ کهنه را بی‌معنا می‌کند).
 *    • «ثبت شد» فقط با CONFIRMED (پاسخِ ۲xx ِ سرور) — هرگز هنگامِ گذاشتن در صف.
 *    • HOLD (بی شبکه، ۵xx، ۴۰۸، ۴۲۹، نشستِ تمام‌شده) ⇒ می‌ماند با پس‌کشیِ نمایی؛ هیچ‌چیز گم نمی‌شود.
 *    • REJECTED (دیگر ۴xxها: نشانیِ تکراری، اشتراک، پرچمِ خاموش…) ⇒ بیرون و پیامِ سرور
 *      به کاربر؛ دوباره فرستادنش همان رد را می‌گیرد.
 *    • ردیف به دکانِ خودش بسته است (`shopId`): پس از عوض شدنِ دکان/حساب به دکانِ دیگر
 *      نمی‌رود (سرور دکان را از توکن می‌خواند، پس فرستادنش یعنی نوشتن روی دکانِ اشتباه).
 *    • ناسازگاری: هر ردیفِ PUT «پایه» دارد = نشانِ وضعِ سرور وقتی کاربر ویرایش را آغاز کرد
 *      (`updatedAt` یا اثرِ انگشتِ پاسخ). پیش از فرستادن، اگر سرور در این میان از دستگاهِ
 *      دیگری عوض شده باشد، بی‌پرسش رونویسی نمی‌شود: ردیف CONFLICT می‌شود تا کاربر
 *      «باز هم بفرست» یا «کنار بگذار» را بزند. اعلامِ حضور پایه ندارد؛ سرور خودش با
 *      `clientEventAt` کهنه را بی‌اثر می‌کند (PRES-08).
 *    • پاسخِ دیررسِ یک ردیف، ویرایشِ تازه‌تری را که در همین میان جایش نشسته پاک نمی‌کند
 *      (تطبیق با `id`).
 *
 *  منطقِ خالص، بی JSON، بی ساعت و بی تصادف ِ درونی: همه از بیرون داده می‌شوند تا آزمون
 *  (`core/src/commonTest`) بی دستگاه و در هر پلتفرم یکسان اجرا شود.
 */

/** نوعِ تغییر؛ ترتیبِ تعریف = ترتیبِ فرستادن */
enum class StorefrontChange(val wire: String, val label: String) {
  SETTINGS("settings", "تنظیماتِ ویترین"),
  DELIVERY("delivery", "ارسال و زمان"),
  MARKET("market", "نمایش در بازار"),
  PRESENCE("presence", "اعلامِ حضور");

  companion object {
    fun of(wire: String): StorefrontChange? = entries.firstOrNull { it.wire == wire }
  }
}

data class OutboxEntry(
  /** شناسهٔ پایدارِ همین ویرایش (برای اعلام همان `eventId`) */
  val id: String,
  val change: StorefrontChange,
  val shopId: String,
  /** بدنهٔ آمادهٔ درخواست (JSON) یا برای اعلام `PresenceDeclaration.encode` */
  val body: String,
  /** نشانِ وضعِ سرور هنگامِ آغازِ ویرایش؛ خالی = بی سنجشِ ناسازگاری */
  val base: String,
  val queuedAt: Long,
  val attempts: Int = 0,
  /** پیش از این زمان خودکار فرستاده نمی‌شود (پس‌کشی) */
  val nextAt: Long = 0,
  val lastError: String = "",
  val conflict: Boolean = false,
)

enum class SendOutcome { CONFIRMED, HOLD, REJECTED }

/** اعلامی که هنوز به سرور نرسیده (PRES-02/08/09) — همان شناسه و زمانِ دستگاه دوباره می‌رود */
data class PresenceDeclaration(val eventId: String, val mode: String, val declaredUntil: Long?, val clientEventAt: Long) {
  companion object {
    private val EVENT_RE = Regex("^[A-Za-z0-9_.:-]{8,80}$")
    val MODES: Set<String> = setOf("auto", "online", "online_until", "offline", "closed")

    fun newEventId(now: Long, random: Long): String =
      "pres-${now.toString(36)}-${(random and 0x7fffffffffffL).toString(16)}"

    fun create(mode: String, declaredUntil: Long?, now: Long, random: Long): PresenceDeclaration {
      require(mode in MODES) { "bad mode" }
      require(mode != "online_until" || declaredUntil != null) { "declaredUntil required" }
      return PresenceDeclaration(newEventId(now, random), mode, if (mode == "online_until") declaredUntil else null, now)
    }

    fun encode(p: PresenceDeclaration): String =
      listOf(p.eventId, p.mode, p.declaredUntil?.toString() ?: "", p.clientEventAt.toString()).joinToString("|")

    fun decode(raw: String?): PresenceDeclaration? {
      val parts = raw?.split('|') ?: return null
      if (parts.size != 4) return null
      val (id, mode, until, at) = parts
      if (!EVENT_RE.matches(id) || mode !in MODES) return null
      val clientAt = at.toLongOrNull() ?: return null
      val du = until.takeIf { it.isNotEmpty() }?.toLongOrNull()
      if (mode == "online_until" && du == null) return null
      return PresenceDeclaration(id, mode, du, clientAt)
    }
  }
}

object StorefrontOutbox {

  /** سقفِ ردیف‌های خوانده‌شده از دیسک (۴ نوع × چند دکان؛ بیش از این یعنی دادهٔ خراب) */
  const val MAX_ENTRIES = 64
  const val BACKOFF_BASE_MS = 5_000L
  const val BACKOFF_MAX_MS = 5 * 60_000L

  /** پیامِ هشدار تا وقتی سرور نگرفته (§۳۱.۲ بندِ ۲) */
  const val NOT_CONFIRMED = "این تغییر هنوز به سرور نرسیده؛ برای مشتری عوض نشده است."
  const val CONFLICT_NOTE = "پس از آغازِ ویرایشِ شما، این بخش از دستگاهِ دیگری روی سرور عوض شده. بفرستید تا روی آن بنشیند، یا کنار بگذارید."

  fun newId(change: StorefrontChange, now: Long, random: Long): String =
    "${change.wire}-${now.toString(36)}-${(random and 0x7fffffffffffL).toString(16)}"

  /* ------------------------------ صف ------------------------------ */

  /**
   *  گذاشتن یا جایگزینی. پایهٔ ردیفِ پیشین می‌ماند: فرمِ دوم از روی همان ویرایشِ
   *  نرسیده پر شده، پس هنوز روی همان وضعِ سرور بنا شده است.
   */
  fun put(queue: List<OutboxEntry>, entry: OutboxEntry): List<OutboxEntry> {
    val old = queue.firstOrNull { it.change == entry.change && it.shopId == entry.shopId }
    val merged = if (old != null && entry.change != StorefrontChange.PRESENCE) entry.copy(base = old.base) else entry
    return sorted(queue.filterNot { it.change == entry.change && it.shopId == entry.shopId } + merged)
  }

  fun pendingFor(queue: List<OutboxEntry>, shopId: String): List<OutboxEntry> =
    if (shopId.isBlank()) emptyList() else sorted(queue.filter { it.shopId == shopId })

  fun pendingOf(queue: List<OutboxEntry>, shopId: String, change: StorefrontChange): OutboxEntry? =
    if (shopId.isBlank()) null else queue.firstOrNull { it.shopId == shopId && it.change == change }

  /** ردیفِ بعدی برای فرستادن؛ `force` = کاربر خودش «بفرست» زده ⇒ پس‌کشی نادیده */
  fun next(queue: List<OutboxEntry>, shopId: String, now: Long, force: Boolean = false): OutboxEntry? =
    pendingFor(queue, shopId).firstOrNull { !it.conflict && (force || it.nextAt <= now) }

  /** آیا سرور پس از آغازِ ویرایش عوض شده؟ `serverBase` ِ null = خوانده نشد ⇒ تصمیم با خودِ فرستادن */
  fun isConflict(entry: OutboxEntry, serverBase: String?): Boolean =
    entry.change != StorefrontChange.PRESENCE && entry.base.isNotEmpty() && serverBase != null && serverBase != entry.base

  fun markConflict(queue: List<OutboxEntry>, entry: OutboxEntry): List<OutboxEntry> =
    queue.map { if (it.id == entry.id) it.copy(conflict = true) else it }

  /** «باز هم بفرست»: پایهٔ تازهٔ سرور را می‌پذیرد و ردیف دوباره فرستادنی می‌شود */
  fun keepMine(queue: List<OutboxEntry>, id: String, serverBase: String): List<OutboxEntry> =
    queue.map { if (it.id == id) it.copy(base = serverBase, conflict = false, nextAt = 0) else it }

  fun discard(queue: List<OutboxEntry>, id: String): List<OutboxEntry> = queue.filterNot { it.id == id }

  /** پاسخِ سرور ⇒ صفِ تازه. فقط همان ردیفِ فرستاده‌شده (با `id`) دست می‌خورد. */
  fun afterSend(queue: List<OutboxEntry>, sent: OutboxEntry, outcome: SendOutcome, message: String, now: Long): List<OutboxEntry> =
    when (outcome) {
      SendOutcome.CONFIRMED, SendOutcome.REJECTED -> queue.filterNot { it.id == sent.id }
      SendOutcome.HOLD -> queue.map {
        if (it.id != sent.id) it
        else it.copy(attempts = it.attempts + 1, nextAt = now + backoffMs(it.attempts + 1), lastError = message)
      }
    }

  /** ۵ث، ۱۰ث، ۲۰ث … تا ۵ دقیقه */
  fun backoffMs(attempts: Int): Long {
    if (attempts <= 0) return 0
    var d = BACKOFF_BASE_MS
    repeat(attempts - 1) { d = (d * 2).coerceAtMost(BACKOFF_MAX_MS) }
    return d.coerceAtMost(BACKOFF_MAX_MS)
  }

  /**
   *  پاسخِ شبکه ⇒ سرنوشتِ ردیف.
   *  @param status کدِ HTTP؛ ۰ = پاسخی نیامد
   *  @param retryable لایهٔ شبکه گفته تکرار ممکن است جواب بدهد (بی‌نت، دسترس‌ناپذیر، دیرکرد، ۵xx)
   */
  fun classify(status: Int, code: String, retryable: Boolean): SendOutcome = when {
    status in 200..299 -> SendOutcome.CONFIRMED
    retryable -> SendOutcome.HOLD
    status == 0 -> SendOutcome.HOLD                       // نشانیِ سرور هنوز نیست، …
    status == 401 || code == "session_expired" -> SendOutcome.HOLD  // پس از ورودِ دوباره می‌رود
    status == 408 || status == 425 || status == 429 -> SendOutcome.HOLD
    status >= 500 -> SendOutcome.HOLD
    else -> SendOutcome.REJECTED
  }

  /** یک خطِ وضعیت برای کاربر — هرگز «ثبت شد» */
  fun statusLine(e: OutboxEntry): String = when {
    e.conflict -> "${e.change.label}: ناسازگار با تغییرِ دستگاهِ دیگر — تصمیم با شما"
    e.attempts == 0 -> "${e.change.label}: در صف — هنوز به سرور نرسیده"
    else -> "${e.change.label}: هنوز به سرور نرسیده (${StorefrontRules.toFa(e.attempts)} بار کوشش" +
      (if (e.lastError.isNotBlank()) " — ${e.lastError}" else "") + ")"
  }

  private fun sorted(q: List<OutboxEntry>): List<OutboxEntry> =
    q.sortedWith(compareBy<OutboxEntry>({ it.shopId }, { it.change.ordinal }))

  /* ------------------------------ ذخیره روی دیسک ------------------------------ */

  private const val HEADER = "sfq1"
  private const val FIELDS = 10

  /** هر ردیف یک خط، کادرها با tab؛ `%`، tab و پایانِ خط گریز داده می‌شوند */
  fun encode(queue: List<OutboxEntry>): String = buildString {
    append(HEADER)
    for (e in queue) {
      append('\n')
      append(listOf(
        e.id, e.change.wire, e.shopId, e.body, e.base, e.queuedAt.toString(), e.attempts.toString(),
        e.nextAt.toString(), e.lastError, if (e.conflict) "1" else "0",
      ).joinToString("\t") { esc(it) })
    }
  }

  /** خواندنِ امن: سرخطِ ناآشنا ⇒ صفِ خالی؛ خطِ خراب نادیده (بقیه می‌مانند) */
  fun decode(raw: String?): List<OutboxEntry> {
    if (raw.isNullOrEmpty()) return emptyList()
    val lines = raw.split('\n')
    if (lines.first() != HEADER) return emptyList()
    val out = ArrayList<OutboxEntry>()
    for (line in lines.drop(1)) {
      if (out.size >= MAX_ENTRIES) break
      val f = line.split('\t').map { unesc(it) }
      if (f.size != FIELDS) continue
      val change = StorefrontChange.of(f[1]) ?: continue
      if (f[0].isBlank() || f[2].isBlank()) continue
      val queuedAt = f[5].toLongOrNull() ?: continue
      val attempts = f[6].toIntOrNull()?.coerceAtLeast(0) ?: continue
      val nextAt = f[7].toLongOrNull() ?: continue
      if (change == StorefrontChange.PRESENCE && PresenceDeclaration.decode(f[3]) == null) continue
      out += OutboxEntry(f[0], change, f[2], f[3], f[4], queuedAt, attempts, nextAt, f[8], f[9] == "1")
    }
    //  یک ردیف برای هر (دکان، نوع): اگر دیسک دوتایی داشت، تازه‌تر می‌ماند
    return sorted(out.groupBy { it.shopId to it.change }.values.map { g -> g.maxBy { it.queuedAt } })
  }

  private fun esc(s: String): String = buildString {
    for (c in s) when (c) {
      '%' -> append("%25")
      '\t' -> append("%09")
      '\n' -> append("%0A")
      '\r' -> append("%0D")
      else -> append(c)
    }
  }

  private fun unesc(s: String): String {
    if ('%' !in s) return s
    val b = StringBuilder()
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c == '%' && i + 2 < s.length) {
        when (s.substring(i + 1, i + 3)) {
          "25" -> { b.append('%'); i += 3; continue }
          "09" -> { b.append('\t'); i += 3; continue }
          "0A" -> { b.append('\n'); i += 3; continue }
          "0D" -> { b.append('\r'); i += 3; continue }
        }
      }
      b.append(c); i++
    }
    return b.toString()
  }

  /* ------------------------------ اثرِ انگشت ------------------------------ */

  /** FNV-1a ِ ۶۴ بیتی روی UTF-16 — پایهٔ بخشی که `updatedAt` ندارد (ارسال و زمان) */
  fun fingerprint(s: String): String {
    var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    for (c in s) {
      h = h xor c.code.toLong()
      h *= 0x100000001b3L
    }
    return h.toULong().toString(16)
  }
}
