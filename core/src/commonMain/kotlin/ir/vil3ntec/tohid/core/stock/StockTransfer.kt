package ir.vil3ntec.tohid.core.stock

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 *  INV-11 — انتقالِ موجودی بین قفسه و انبار با «در مسیر»، دریافتِ ناقص و دریافت با اسکن (§۶.۵، S21).
 *
 *  چرخه:  پیش‌نویس (فقط همین دستگاه) ⇒ ارسال (مبدأ کم، «در مسیر» زیاد) ⇒ دریافتِ ناقص (هر چند بار) ⇒ دریافتِ پایانی
 *         (کسری فقط با دلیل) یا رد (ماندهٔ در مسیر به مبدأ). داور سرور است (`server/src/lib/stock-transfers.js`).
 *
 *  بی‌اینترنت: هر گام یک ردیفِ `auditLog` با `type = stock_transfer` و شیءِ `xfer` است ([Step.xfer]) که همان صفِ
 *  همگام‌سازی می‌برد؛ کلیدِ گام = شناسهٔ همان ردیف، پس فرستادنِ دوباره هرگز دو بار نمی‌شمارد. تا سرور نگفته، گام
 *  «در صف» است و [project] اثرش را روی آخرین نمای سرور نشان می‌دهد (همان قاعده‌های سرور) تا کار بی اینترنت ادامه یابد.
 *  ⛔ این‌جا هیچ موجودیِ دفترِ دستگاه عوض نمی‌شود: موجودیِ «در مسیر» و جابه‌جایی فقط در سرور است، پس هیچ واحدی دو بار
 *     شمرده نمی‌شود (دفترِ دستگاه ⇐ سرور یک بار، گامِ انتقال ⇐ سرور یک بار).
 *
 *  دریافت با اسکن: هر اسکن یک واحدِ پایه از همان کالا (`CountScan.UNIT_MILLI`)؛ کالای وزنی هرگز با اسکن (وزنش نوشته
 *  می‌شود)؛ کالای بیرون از انتقال و اسکنِ بیش از ماندهٔ در مسیر **پذیرفته نمی‌شود** و دلیلش برمی‌گردد.
 *  مقدار همه‌جا به هزارم (Long). منطقِ خالص (iOS هم)؛ آزمون: `StockTransferTest` (commonTest).
 */
object StockTransfer {

  const val SHOP_FLOOR = "shop_floor"
  const val WAREHOUSE = "warehouse"
  val ENDPOINTS: List<String> = listOf(SHOP_FLOOR, WAREHOUSE)
  /** همان سقف‌های سرور */
  const val MAX_LINES = 100
  const val MAX_QTY_MILLI = 1_000_000_000L
  const val MAX_NOTE = 300
  const val MAX_REASON = 120
  /** سقفِ کلیدهای یک پرسشِ نتیجه (`GET /transfers?keys=`) */
  const val MAX_KEYS = 50
  /** `auditLog.type` ِ گام */
  const val AUDIT_TYPE = "stock_transfer"

  const val SEND = "send"
  const val RECEIVE = "receive"
  const val REJECT = "reject"

  enum class Status(val wire: String) {
    IN_TRANSIT("in_transit"), PARTIAL("partial"), RECEIVED("received"), REJECTED("rejected"),
    /** ارسالِ این دستگاه که سرور نپذیرفت (کمبود، کالای ناشناس…) */
    REFUSED("refused");

    val open: Boolean get() = this == IN_TRANSIT || this == PARTIAL

    companion object {
      fun of(wire: String?): Status? = entries.firstOrNull { it.wire == wire }
    }
  }

  data class Line(val productId: String, val qtyMilli: Long)

  /** یک ردیفِ انتقال از نگاهِ برنامه: فرستاده، رسیدهٔ تا اینجا، ماندهٔ در مسیر، کسریِ پس از بستن */
  data class TLine(val productId: String, val sentMilli: Long, val receivedMilli: Long, val pendingMilli: Long, val shortMilli: Long)

  data class Transfer(
    /** شناسهٔ سرور؛ `null` تا ارسالِ در صف به سرور نرسیده */
    val id: String?,
    val sendKey: String,
    val from: String,
    val to: String,
    val status: Status,
    val lines: List<TLine>,
    val note: String = "",
    val reason: String = "",
    val createdAt: Long = 0,
    /** گامی از این انتقال هنوز در صف است (اثرش فقط پیش‌بینی است) */
    val queued: Boolean = false,
  ) {
    val pendingMilli: Long get() = lines.sumOf { it.pendingMilli }
    val shortMilli: Long get() = lines.sumOf { it.shortMilli }
  }

  /** گامِ دفتر — همان ردیفِ `auditLog` (`key` = شناسهٔ ردیف) */
  data class Step(
    val key: String,
    val step: String,
    val at: Long,
    val from: String = "",
    val to: String = "",
    val lines: List<Line> = emptyList(),
    val note: String = "",
    val transferId: String = "",
    val sendKey: String = "",
    val final: Boolean = true,
    val reason: String = "",
  ) {
    /** شیءِ `xfer` ِ ردیف — همان شکلی که `stock-transfers.ingestDevice` می‌خواند */
    fun xfer(): JsonObject = buildJsonObject {
      put("v", 1)
      put("step", step)
      if (step == SEND) {
        put("from", from)
        put("to", to)
        if (note.isNotEmpty()) put("note", note)
      } else {
        if (transferId.isNotEmpty()) put("transferId", transferId) else put("sendKey", sendKey)
        if (reason.isNotEmpty()) put("reason", reason)
      }
      if (step == RECEIVE) put("final", final)
      if (step != REJECT) put("lines", lines(lines))
    }
  }

  /** نتیجهٔ گام از سرور */
  data class Outcome(val key: String, val applied: Boolean, val code: String, val message: String, val transferId: String)

  /** گامی که سرور نپذیرفت — برای پیام در صفحه */
  data class Refusal(val step: Step, val outcome: Outcome)

  data class View(val transfers: List<Transfer>, val refusals: List<Refusal>)

  /* ------------------------------------------------------------ پیش‌نویس */

  enum class Problem {
    SAME_PLACE, BAD_PLACE, EMPTY, TOO_MANY, BAD_QTY, NO_KEY,
    /** دریافت: بیش از ماندهٔ در مسیر */
    OVER,
    /** دریافتِ ناقص بی هیچ رسیده */
    NOTHING,
    /** کسری یا رد بی دلیل */
    REASON,
    /** انتقال دیگر باز نیست (یا گامِ پایانی‌اش در صف است) */
    CLOSED,
  }

  data class Draft(val key: String, val from: String = SHOP_FLOOR, val to: String = WAREHOUSE, val lines: List<Line> = emptyList(), val note: String = "") {
    fun qty(productId: String): Long = lines.firstOrNull { it.productId == productId }?.qtyMilli ?: 0L

    /** مقدارِ یک کالا (صفر ⇒ بیرون)؛ ترتیبِ افزودن می‌ماند */
    fun with(productId: String, qtyMilli: Long): Draft {
      val q = qtyMilli.coerceIn(0L, MAX_QTY_MILLI)
      val rest = lines.filter { it.productId != productId }
      if (q == 0L) return copy(lines = rest)
      val i = lines.indexOfFirst { it.productId == productId }
      return copy(lines = if (i < 0) lines + Line(productId, q) else lines.toMutableList().also { it[i] = Line(productId, q) })
    }

    fun plus(productId: String, deltaMilli: Long): Draft = with(productId, qty(productId) + deltaMilli)

    /** جای مبدأ و مقصد عوض */
    fun swapped(): Draft = copy(from = to, to = from)
  }

  fun validate(d: Draft): Problem? = when {
    d.key.isBlank() -> Problem.NO_KEY
    d.from !in ENDPOINTS || d.to !in ENDPOINTS -> Problem.BAD_PLACE
    d.from == d.to -> Problem.SAME_PLACE
    d.lines.isEmpty() -> Problem.EMPTY
    d.lines.size > MAX_LINES -> Problem.TOO_MANY
    d.lines.any { it.qtyMilli <= 0 || it.qtyMilli > MAX_QTY_MILLI } -> Problem.BAD_QTY
    else -> null
  }

  /** پیش‌نویس ⇒ گامِ ارسال (کلید = کلیدِ همان پیش‌نویس، پس دو ضربه یک ارسال) */
  fun send(d: Draft, at: Long): Result = validate(d)?.let { Result.Bad(it) }
    ?: Result.Ok(Step(d.key, SEND, at, from = d.from, to = d.to, lines = d.lines, note = d.note.trim().take(MAX_NOTE)))

  sealed interface Result {
    data class Ok(val step: Step) : Result
    data class Bad(val problem: Problem) : Result
  }

  /* ------------------------------------------------------------ دریافت با اسکن */

  sealed interface Scan {
    data class Counted(val productId: String, val countedMilli: Long) : Scan
    data class Unknown(val code: String) : Scan
    data class Weighed(val productId: String) : Scan
    /** کالای این بارکد در این انتقال نیست */
    data class NotInTransfer(val productId: String) : Scan
    /** اسکنِ بیش از ماندهٔ در مسیر — شمرده نشد */
    data class Over(val productId: String, val pendingMilli: Long) : Scan
  }

  /**
   *  یک اسکن روی شمرده‌های همین دریافت: ⇒ (شمرده‌های تازه، نتیجه). `ownerOf(code)` ⇒ کالای آن بارکد.
   *  کد مثلِ انبارگردانی تمیز می‌شود (فاصلهٔ دو سر و رقمِ فارسی/عربی).
   */
  fun scan(
    t: Transfer,
    counted: Map<String, Long>,
    code: String,
    ownerOf: (String) -> String?,
    weighed: (String) -> Boolean,
  ): Pair<Map<String, Long>, Scan> {
    val clean = CountScan.latin(code.trim())
    val id = ownerOf(clean) ?: return counted to Scan.Unknown(clean)
    val line = t.lines.firstOrNull { it.productId == id } ?: return counted to Scan.NotInTransfer(id)
    if (weighed(id)) return counted to Scan.Weighed(id)
    val next = (counted[id] ?: 0L).coerceAtLeast(0L) + CountScan.UNIT_MILLI
    if (next > line.pendingMilli) return counted to Scan.Over(id, line.pendingMilli)
    return (counted + (id to next)) to Scan.Counted(id, next)
  }

  /** «همه رسید»: شمردهٔ هر کالا = ماندهٔ در مسیرش */
  fun allArrived(t: Transfer): Map<String, Long> = t.lines.filter { it.pendingMilli > 0 }.associate { it.productId to it.pendingMilli }

  /** کسریِ هر کالا اگر همین شمرده‌ها پایانی ثبت شوند */
  fun shortages(t: Transfer, counted: Map<String, Long>): List<Line> =
    t.lines.mapNotNull { l -> (l.pendingMilli - (counted[l.productId] ?: 0L)).takeIf { it > 0 }?.let { Line(l.productId, it) } }

  /**
   *  شمرده‌ها ⇒ گامِ دریافت. پایانی ⇒ **هر** کالا با عددِ صریح (نشمرده = هیچ نرسید، نه «همه رسید» ِ پیش‌فرضِ REST) و
   *  کسری فقط با دلیل؛ ناقص ⇒ فقط رسیده‌ها، دست‌کم یکی.
   */
  fun receive(t: Transfer, key: String, counted: Map<String, Long>, final: Boolean, reason: String, at: Long, queuedFinal: Boolean = false): Result {
    if (key.isBlank()) return Result.Bad(Problem.NO_KEY)
    if (!t.status.open || queuedFinal) return Result.Bad(Problem.CLOSED)
    val unknown = counted.keys.any { id -> t.lines.none { it.productId == id } }
    if (unknown || counted.values.any { it < 0 || it > MAX_QTY_MILLI }) return Result.Bad(Problem.BAD_QTY)
    if (t.lines.any { (counted[it.productId] ?: 0L) > it.pendingMilli }) return Result.Bad(Problem.OVER)
    val why = reason.trim().take(MAX_REASON)
    val lines = if (final) t.lines.filter { it.pendingMilli > 0 }.map { Line(it.productId, counted[it.productId] ?: 0L) }
    else t.lines.mapNotNull { l -> (counted[l.productId] ?: 0L).takeIf { it > 0 }?.let { Line(l.productId, it) } }
    if (!final && lines.isEmpty()) return Result.Bad(Problem.NOTHING)
    if (final && shortages(t, counted).isNotEmpty() && why.isEmpty()) return Result.Bad(Problem.REASON)
    return Result.Ok(Step(key, RECEIVE, at, lines = lines, transferId = t.id.orEmpty(), sendKey = t.sendKey, final = final, reason = why))
  }

  fun reject(t: Transfer, key: String, reason: String, at: Long, queuedFinal: Boolean = false): Result {
    if (key.isBlank()) return Result.Bad(Problem.NO_KEY)
    if (!t.status.open || queuedFinal) return Result.Bad(Problem.CLOSED)
    val why = reason.trim().take(MAX_REASON)
    if (why.isEmpty()) return Result.Bad(Problem.REASON)
    return Result.Ok(Step(key, REJECT, at, transferId = t.id.orEmpty(), sendKey = t.sendKey, reason = why))
  }

  /* ------------------------------------------------------------ سرور + صف */

  /** کلیدهایی که نتیجه‌شان هنوز معلوم نیست — تازه‌ترین‌ها، حداکثر [MAX_KEYS] */
  fun unresolved(steps: List<Step>, known: Map<String, Outcome>): List<String> =
    steps.filter { it.key !in known }.sortedByDescending { it.at }.take(MAX_KEYS).map { it.key }

  /** گامِ پایانیِ (دریافتِ پایانی یا رد) ِ در صفِ این انتقال؟ */
  fun queuedFinal(t: Transfer, steps: List<Step>, known: Map<String, Outcome>): Boolean = steps.any { s ->
    s.key !in known && s.step != SEND && (s.step == REJECT || s.final) && targets(s, t)
  }

  private fun targets(s: Step, t: Transfer): Boolean =
    (s.transferId.isNotEmpty() && s.transferId == t.id) || (s.transferId.isEmpty() && s.sendKey.isNotEmpty() && s.sendKey == t.sendKey)

  /**
   *  نمای صفحه: آخرین نمای سرور + اثرِ پیش‌بینیِ گام‌های در صف (همان قاعده‌های سرور) + ارسال‌های رد شده.
   *  گامی که نتیجه‌اش «ثبت شد» است در نمای سرور هست و دوباره اعمال نمی‌شود؛ «رد شد» فقط پیام است.
   */
  fun project(server: List<Transfer>, steps: List<Step>, known: Map<String, Outcome>): View {
    val list = server.toMutableList()
    val refusals = ArrayList<Refusal>()
    for (s in steps.sortedWith(compareBy<Step> { it.at }.thenBy { it.key })) {
      val o = known[s.key]
      if (o != null) {
        if (!o.applied) {
          refusals += Refusal(s, o)
          if (s.step == SEND && list.none { it.sendKey == s.key }) {
            list += Transfer(null, s.key, s.from, s.to, Status.REFUSED, s.lines.map { TLine(it.productId, it.qtyMilli, 0, 0, 0) }, s.note, o.code, s.at)
          }
        }
        continue
      }
      when (s.step) {
        SEND -> if (list.none { it.sendKey == s.key }) {
          list += Transfer(null, s.key, s.from, s.to, Status.IN_TRANSIT, s.lines.map { TLine(it.productId, it.qtyMilli, 0, it.qtyMilli, 0) }, s.note, "", s.at, queued = true)
        }
        RECEIVE, REJECT -> {
          val i = list.indexOfFirst { targets(s, it) }
          if (i >= 0 && list[i].status.open) list[i] = applied(list[i], s)
        }
      }
    }
    return View(list.sortedWith(compareByDescending<Transfer> { it.createdAt }.thenByDescending { it.sendKey }), refusals)
  }

  /** همان قاعدهٔ `receivePlan`/`rejectPlan` ِ سرور، برای پیش‌بینی */
  internal fun applied(t: Transfer, s: Step): Transfer {
    if (s.step == REJECT) {
      return t.copy(status = Status.REJECTED, reason = s.reason, queued = true, lines = t.lines.map { it.copy(pendingMilli = 0, shortMilli = it.sentMilli - it.receivedMilli) })
    }
    val got = s.lines.associate { it.productId to it.qtyMilli }
    val step = t.lines.map { l ->
      val add = (got[l.productId] ?: if (s.final) l.pendingMilli else 0L).coerceIn(0L, l.pendingMilli)
      l.copy(receivedMilli = l.receivedMilli + add, pendingMilli = l.pendingMilli - add)
    }
    val closing = s.final || step.all { it.pendingMilli == 0L }
    return t.copy(
      status = if (closing) Status.RECEIVED else Status.PARTIAL,
      queued = true,
      reason = if (closing) s.reason else t.reason,
      lines = if (closing) step.map { it.copy(shortMilli = it.sentMilli - it.receivedMilli, pendingMilli = 0) } else step,
    )
  }

  /* ------------------------------------------------------------ JSON */

  private fun lines(ls: List<Line>): JsonArray = buildJsonArray {
    for (l in ls) add(buildJsonObject { put("productId", l.productId); put("qtyMilli", l.qtyMilli) })
  }

  private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
  private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
  private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
  private fun JsonElement?.arr(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

  /** ردیفِ دفتر (`id`، `createdAt`، `xfer`) ⇒ گام؛ شکلِ نادرست ⇒ `null` */
  fun stepOf(key: String, at: Long, xfer: JsonObject?): Step? {
    val x = xfer ?: return null
    val step = x.s("step").takeIf { it == SEND || it == RECEIVE || it == REJECT } ?: return null
    val lines = x["lines"].arr().mapNotNull { o -> o.s("productId").takeIf { it.isNotEmpty() }?.let { Line(it, o.l("qtyMilli") ?: 0L) } }
    return Step(
      key, step, at, from = x.s("from"), to = x.s("to"), lines = lines, note = x.s("note"),
      transferId = x.s("transferId"), sendKey = x.s("sendKey"),
      final = (x["final"] as? JsonPrimitive)?.booleanOrNull ?: true, reason = x.s("reason"),
    )
  }

  /** یک انتقال از `GET /api/shop/catalog/transfers` */
  fun transferOf(o: JsonObject): Transfer? {
    val status = Status.of(o.s("status")) ?: return null
    val id = o.s("id").takeIf { it.isNotEmpty() } ?: return null
    return Transfer(
      id = id, sendKey = o.s("sendKey"), from = o.s("from"), to = o.s("to"), status = status,
      lines = o["lines"].arr().map { l ->
        TLine(l.s("productId"), l.l("sentMilli") ?: 0, l.l("receivedMilli") ?: 0, l.l("pendingMilli") ?: 0, l.l("shortMilli") ?: 0)
      },
      note = o.s("note"), reason = o.s("reason"), createdAt = o.l("createdAt") ?: 0,
    )
  }

  /** پاسخِ فهرست ⇒ (انتقال‌ها، نتیجهٔ گام‌ها) */
  fun listOf(body: JsonObject): Pair<List<Transfer>, Map<String, Outcome>> {
    val ts = body["transfers"].arr().mapNotNull(::transferOf)
    val os = body["steps"].arr().mapNotNull { o ->
      val key = o.s("key").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      Outcome(key, o.s("outcome") == "applied", o.s("code"), o.s("message"), o.s("transferId"))
    }
    return ts to os.associateBy { it.key }
  }

  /** پیش‌نویسِ همین دستگاه (پرونده) — شکلِ نادرست ⇒ `null` */
  fun draftJson(d: Draft): JsonObject = buildJsonObject {
    put("key", d.key); put("from", d.from); put("to", d.to); put("note", d.note); put("lines", lines(d.lines))
  }

  fun draftOf(o: JsonObject): Draft? {
    val key = o.s("key").takeIf { it.isNotBlank() } ?: return null
    val ls = o["lines"].arr().mapNotNull { l -> l.s("productId").takeIf { it.isNotEmpty() }?.let { Line(it, l.l("qtyMilli") ?: 0L) } }
    return Draft(key, o.s("from").ifEmpty { SHOP_FLOOR }, o.s("to").ifEmpty { WAREHOUSE }, ls.filter { it.qtyMilli > 0 }, o.s("note"))
  }

  /** نتیجه‌های نگه‌داشته (پرونده‌ی همین دستگاه) — همان شکلِ پاسخ */
  fun outcomesJson(known: Map<String, Outcome>): JsonArray = buildJsonArray {
    for (o in known.values) add(buildJsonObject {
      put("key", o.key); put("outcome", if (o.applied) "applied" else "refused"); put("code", o.code); put("message", o.message)
      put("transferId", o.transferId)
    })
  }

  fun transfersJson(ts: List<Transfer>): JsonArray = buildJsonArray {
    for (t in ts) {
      if (t.id == null || t.queued) continue
      add(buildJsonObject {
        put("id", t.id); put("sendKey", t.sendKey); put("from", t.from); put("to", t.to); put("status", t.status.wire)
        put("note", t.note); put("reason", t.reason); put("createdAt", t.createdAt)
        put("lines", buildJsonArray {
          for (l in t.lines) add(buildJsonObject {
            put("productId", l.productId); put("sentMilli", l.sentMilli); put("receivedMilli", l.receivedMilli)
            put("pendingMilli", l.pendingMilli); put("shortMilli", l.shortMilli)
          })
        })
      })
    }
  }
}
