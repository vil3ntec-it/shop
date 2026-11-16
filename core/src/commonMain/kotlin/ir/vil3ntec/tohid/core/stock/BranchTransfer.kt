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
import kotlin.random.Random

/*
 *  INV-11/S21 — انتقالِ موجودی **میانِ شعبه‌های یک دکان** (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰): همتای کلاینتِ
 *  `server/src/lib/branch-transfers.js` (`/api/shop/catalog/branch-transfers`، پشتِ پرچم‌های خاموشِ `shop.branches` و
 *  `shop.catalog`). core شبکه ندارد: این‌جا فقط شکلِ درخواست ([Call]: روش، مسیر از `/api`، سرآیندها، بدنه)، قاعده‌های پیش‌سنجی
 *  (همان سقف‌ها و کدهای سرور) و خواندنِ پاسخ است؛ فرستنده و صفحه با برنامه (فازِ ۲ ِ برنامه‌ها، `BRANCHES-fa.md` §۱۰).
 *
 *  چرخه:  ارسال از شعبهٔ «الف» ⇒ `in_transit` (کالا از مبدأ ِ الف کم و «در مسیر» ِ الف) ⇒ در شعبهٔ «ب»: دریافتِ ناقص (هر چند
 *         بار) و/یا دریافتِ پایانی (کسری فقط با دلیل) ⇒ `received`، یا رد (ماندهٔ در مسیر به مبدأ ِ الف) ⇒ `rejected`.
 *         ۹۴۶: پیش‌نویس (`draft`، فقط الف می‌بیند، بی اثرِ موجودی) ⇒ ویرایش/ارسال/لغو؛ لغوِ «در مسیر» ِ هیچ‌نرسیده (الف، با دلیل) ⇒
 *         `cancelled` (همه به مبدأ). دفترِ دستگاهِ هر سو آینه‌اش را از [BranchTransferLedger] می‌گیرد.
 *
 *  ⛔ شعبهٔ کننده فقط سرآیندِ `X-Branch-Id` ِ همین درخواست است ([headers])؛ شعبهٔ مقصد در بدنهٔ ارسال «هدف» است و سرور با
 *     شعبه‌های فعالِ همان دکان می‌سنجد. دریافت/رد فقط وقتی پیشنهاد می‌شود که همین شعبه مقصد است ([canAct]) — سرور باز هم
 *     می‌سنجد (`transfer_wrong_branch`).
 *  ⛔ هر گام یک کلیدِ یکتایی ([newKey]) که برای فرستادنِ دوبارهٔ **همان** فرم ثابت می‌ماند (تکرار ⇒ همان پاسخ، بی اثرِ دوم).
 *  ⛔ این‌جا هیچ موجودیِ دفترِ دستگاه عوض نمی‌شود: انتقالِ میانِ شعبه برخط و داورش سرور است؛ آینهٔ دفتر فقط پس از پاسخِ سرور
 *     ([BranchTransferLedger]، `data/BranchTransferBookAndroid.kt`).
 *  مقدار همه‌جا به هزارم (Long). منطقِ خالص (iOS هم)؛ آزمون: `BranchTransferTest` (commonTest).
 */
object BranchTransfer {

  const val PATH = "/shop/catalog/branch-transfers"
  const val HEADER_BRANCH = "X-Branch-Id"
  const val HEADER_KEY = "Idempotency-Key"
  /** شناسهٔ شعبهٔ اصلی در پاسخ‌های سرور؛ در سرآیند `main` */
  const val MAIN = ""
  const val MAIN_HEADER = "main"
  const val SHOP_FLOOR = "shop_floor"
  const val WAREHOUSE = "warehouse"
  val ENDPOINTS: List<String> = listOf(SHOP_FLOOR, WAREHOUSE)
  /** همان سقف‌های سرور (`branch-transfer-plan.js`) */
  const val MAX_LINES = 100
  const val MAX_QTY_MILLI = 1_000_000_000L
  const val MAX_NOTE = 300
  const val MAX_REASON = 120
  private val BRANCH_RE = Regex("^brn_[0-9a-f]{24}$")
  private val ID_RE = Regex("^bxf_[0-9a-f]{24}$")
  private val KEY_RE = Regex("^[A-Za-z0-9_.:-]{8,128}$")
  private const val KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

  enum class Status(val wire: String) {
    DRAFT("draft"), IN_TRANSIT("in_transit"), PARTIAL("partial"), RECEIVED("received"), REJECTED("rejected"), CANCELLED("cancelled");

    /** «در مسیر» (دریافت/رد در مقصد) */
    val open: Boolean get() = this == IN_TRANSIT || this == PARTIAL
    /** هنوز بسته نشده (پیش‌نویس هم) */
    val live: Boolean get() = this == DRAFT || open

    companion object {
      fun of(wire: String?): Status? = entries.firstOrNull { it.wire == wire }
    }
  }

  enum class Direction(val wire: String) {
    IN("in"), OUT("out");

    companion object {
      fun of(wire: String?): Direction? = entries.firstOrNull { it.wire == wire }
    }
  }

  /** یک سرِ انتقال (یا یک مقصدِ ممکن) */
  data class End(val id: String, val name: String, val main: Boolean)

  data class Line(val productId: String, val qtyMilli: Long)

  /** ردیفِ انتقال: فرستاده، رسیدهٔ تا اینجا (`null` = هنوز هیچ گامی)، ماندهٔ در مسیر، کسریِ پس از بستن، برگشته به مبدأ (رد/لغو) */
  data class TLine(
    val productId: String, val sentMilli: Long, val receivedMilli: Long?, val pendingMilli: Long, val shortMilli: Long,
    val returnedMilli: Long = 0,
  )

  data class Transfer(
    val id: String,
    val direction: Direction,
    val fromBranch: End,
    val toBranch: End,
    val from: String,
    val to: String,
    val status: Status,
    val lines: List<TLine>,
    val note: String = "",
    val reason: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0,
    val closedBy: String = "",
    val closedAt: Long? = null,
    /** لحظهٔ ارسال (`null` ⇒ هرگز فرستاده نشد: پیش‌نویس یا لغوِ پیش‌نویس) */
    val sentAt: Long? = null,
    val sentBy: String = "",
  ) {
    val pendingMilli: Long get() = lines.sumOf { it.pendingMilli }
    val shortMilli: Long get() = lines.sumOf { it.shortMilli }
    val returnedMilli: Long get() = lines.sumOf { it.returnedMilli }
    val sentMilli: Long get() = lines.sumOf { it.sentMilli }
    val receivedMilli: Long get() = lines.sumOf { it.receivedMilli ?: 0L }
  }

  data class Step(
    val kind: String, val side: String, val statusFrom: String, val statusTo: String, val lines: List<Line>,
    val note: String, val final: Boolean, val by: String, val at: Long,
  )

  /** پیش‌نویسِ ارسالِ همین دستگاه؛ [key] یک بار ساخته و تا بستنِ فرم ثابت */
  data class Draft(
    val key: String,
    val toBranch: String? = null,
    val from: String = SHOP_FLOOR,
    val to: String = SHOP_FLOOR,
    val lines: List<Line> = emptyList(),
    val note: String = "",
  ) {
    /** مقدارِ یک کالا (صفر یا کمتر ⇒ برداشتن)؛ ترتیبِ ردیف‌ها می‌ماند */
    fun with(productId: String, qtyMilli: Long): Draft {
      if (qtyMilli <= 0) return copy(lines = lines.filter { it.productId != productId })
      val i = lines.indexOfFirst { it.productId == productId }
      return copy(lines = if (i < 0) lines + Line(productId, qtyMilli) else lines.mapIndexed { j, l -> if (j == i) Line(productId, qtyMilli) else l })
    }
  }

  /** یک درخواستِ آماده برای فرستندهٔ برنامه: روش، مسیر از ریشهٔ `/api`، سرآیندها و بدنهٔ JSON */
  data class Call(val method: String, val path: String, val headers: Map<String, String>, val body: JsonObject? = null)

  /** کدهای پیش‌سنجی همان کدهای خطای سرور‌اند (پیامِ صفحه با `Str*` ِ برنامه) */
  enum class Problem(val code: String) {
    NO_DESTINATION("bad_destination"), SAME_BRANCH("same_branch"), BAD_LOCATION("bad_location"), EMPTY("lines_required"),
    TOO_MANY("too_many_lines"), BAD_QTY("bad_qty"), NO_PRODUCT("product_required"), DUPLICATE("duplicate_product"),
    BAD_KEY("bad_idempotency_key"), REASON_REQUIRED("reason_required"),
    OVER_RECEIVED("over_received"), NOT_IN_TRANSFER("product_not_in_transfer"), NOTHING_RECEIVED("nothing_received"),
    NOT_DESTINATION("transfer_wrong_branch"), CLOSED("transfer_closed"),
    NOT_SENDER("transfer_not_sender"), PARTIAL_RECEIVED("transfer_partial"), BAD_STATE("transfer_bad_state"),
  }

  sealed interface Result {
    data class Ok(val call: Call) : Result
    data class Bad(val problem: Problem) : Result
  }

  /** کلیدِ بی‌خطرِ تکرار (۲۴ نویسه) — یکی برای هر فرم/گام */
  fun newKey(rnd: Random = Random.Default): String = "bx" + (1..22).map { KEY_CHARS[rnd.nextInt(KEY_CHARS.length)] }.joinToString("")

  /** شعبه برای سرآیند: اصلی ⇒ `main`، شعبهٔ دیگر همان شناسه */
  fun branchHeader(branch: String): String = if (branch == MAIN) MAIN_HEADER else branch

  fun headers(branch: String, key: String? = null): Map<String, String> = buildMap {
    put(HEADER_BRANCH, branchHeader(branch))
    if (key != null) put(HEADER_KEY, key)
  }

  private fun enc(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
      val c = b.toInt() and 0xFF
      val ok = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code || c == '_'.code || c == '-'.code
      if (ok) append(c.toChar()) else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0x0F])
    }
  }

  /** فهرستِ همین شعبه: [direction] `null` = هر دو؛ [openOnly] ⇒ در مسیر و نیمه‌رسیده */
  fun list(branch: String, direction: Direction? = null, openOnly: Boolean = false): Call {
    val q = listOfNotNull(direction?.let { "direction=${it.wire}" }, if (openOnly) "status=open" else null)
    return Call("GET", PATH + if (q.isEmpty()) "" else q.joinToString("&", prefix = "?"), headers(branch))
  }

  fun destinations(branch: String): Call = Call("GET", "$PATH/destinations", headers(branch))

  fun detail(branch: String, id: String): Call = Call("GET", "$PATH/${enc(id)}", headers(branch))

  private fun isBranchId(id: String): Boolean = id == MAIN || BRANCH_RE.matches(id)

  fun validate(here: String, d: Draft): Problem? = when {
    !KEY_RE.matches(d.key) -> Problem.BAD_KEY
    d.toBranch == null || !isBranchId(d.toBranch) -> Problem.NO_DESTINATION
    d.toBranch == here -> Problem.SAME_BRANCH
    d.from !in ENDPOINTS || d.to !in ENDPOINTS -> Problem.BAD_LOCATION
    d.lines.isEmpty() -> Problem.EMPTY
    d.lines.size > MAX_LINES -> Problem.TOO_MANY
    d.lines.any { it.productId.isBlank() } -> Problem.NO_PRODUCT
    d.lines.map { it.productId }.toSet().size != d.lines.size -> Problem.DUPLICATE
    d.lines.any { it.qtyMilli < 1 || it.qtyMilli > MAX_QTY_MILLI } -> Problem.BAD_QTY
    else -> null
  }

  private fun draftBody(d: Draft, draft: Boolean): JsonObject = buildJsonObject {
    put("toBranch", d.toBranch!!)
    put("from", d.from); put("to", d.to)
    put("lines", lines(d.lines))
    put("note", d.note.trim().take(MAX_NOTE))
    if (draft) put("draft", true)
  }

  /** ارسال از شعبهٔ [here] (همان سرآیند)؛ [draft] ⇒ پیش‌نویسِ سرور (بی اثرِ موجودی، فقط همین شعبه می‌بیند) */
  fun send(here: String, d: Draft, draft: Boolean = false): Result {
    validate(here, d)?.let { return Result.Bad(it) }
    return Result.Ok(Call("POST", PATH, headers(here, d.key), draftBody(d, draft)))
  }

  /** پیش‌نویس، ویرایش، ارسالِ پیش‌نویس و لغو فقط در شعبهٔ فرستنده */
  private fun mine(t: Transfer, here: String): Boolean = t.fromBranch.id == here && t.direction == Direction.OUT

  fun canEdit(t: Transfer, here: String): Boolean = t.status == Status.DRAFT && mine(t, here)
  fun canSend(t: Transfer, here: String): Boolean = canEdit(t, here)
  /** لغو: پیش‌نویس، یا «در مسیر» ای که هنوز هیچ‌اش نرسیده */
  fun canCancel(t: Transfer, here: String): Boolean =
    mine(t, here) && (t.status == Status.DRAFT || (t.status == Status.IN_TRANSIT && t.receivedMilli == 0L))
  /** لغوِ فرستاده دلیل می‌خواهد (کالا به مبدأ برمی‌گردد) */
  fun cancelNeedsReason(t: Transfer): Boolean = t.status != Status.DRAFT

  /** پیش‌نویسِ سرور ⇒ فرمِ ویرایش با کلیدِ تازه */
  fun draftOf(t: Transfer, key: String): Draft = Draft(
    key = key, toBranch = t.toBranch.id, from = t.from, to = t.to,
    lines = t.lines.map { Line(it.productId, it.sentMilli) }, note = t.note,
  )

  private fun mineCheck(here: String, t: Transfer, key: String, ok: Boolean): Problem? = when {
    !KEY_RE.matches(key) -> Problem.BAD_KEY
    !mine(t, here) -> Problem.NOT_SENDER
    !t.status.live -> Problem.CLOSED
    t.status == Status.PARTIAL -> Problem.PARTIAL_RECEIVED
    !ok -> Problem.BAD_STATE
    else -> null
  }

  /** ویرایشِ پیش‌نویسِ [t] با فرمِ [d] (کلیدِ همین گام [Draft.key]) */
  fun edit(here: String, t: Transfer, d: Draft): Result {
    mineCheck(here, t, d.key, canEdit(t, here))?.let { return Result.Bad(it) }
    validate(here, d)?.let { return Result.Bad(it) }
    return Result.Ok(Call("PUT", "$PATH/${enc(t.id)}", headers(here, d.key), draftBody(d, draft = false)))
  }

  /** ارسالِ پیش‌نویسِ [t] */
  fun sendDraft(here: String, t: Transfer, key: String): Result {
    mineCheck(here, t, key, canSend(t, here))?.let { return Result.Bad(it) }
    return Result.Ok(Call("POST", "$PATH/${enc(t.id)}/send", headers(here, key), buildJsonObject { }))
  }

  /** لغوِ فرستنده؛ «در مسیر» فقط با [reason] */
  fun cancel(here: String, t: Transfer, key: String, reason: String = ""): Result {
    mineCheck(here, t, key, canCancel(t, here))?.let { return Result.Bad(it) }
    val r = reason.trim().take(MAX_REASON)
    if (cancelNeedsReason(t) && r.isEmpty()) return Result.Bad(Problem.REASON_REQUIRED)
    return Result.Ok(Call("POST", "$PATH/${enc(t.id)}/cancel", headers(here, key), buildJsonObject { if (r.isNotEmpty()) put("reason", r) }))
  }

  /** دریافت و رد فقط در شعبهٔ مقصد و فقط تا باز است */
  fun canAct(t: Transfer, here: String): Boolean = t.status.open && t.toBranch.id == here && t.direction == Direction.IN

  /**
   *  کسری اگر [counted] (رسیده در **همین** گام؛ نیامده = «بقیه رسید» فقط در گامِ پایانی) گامِ پایانی باشد — همان قاعدهٔ سرور.
   */
  fun shortages(t: Transfer, counted: Map<String, Long>): List<Line> = t.lines.mapNotNull { l ->
    val got = counted[l.productId] ?: l.pendingMilli
    val gap = l.pendingMilli - got
    if (gap > 0) Line(l.productId, gap) else null
  }

  /** دریافت در شعبهٔ [here]: [counted] = رسیده در همین گام؛ [final] = بستن (کسری فقط با [reason]) */
  fun receive(here: String, t: Transfer, key: String, counted: Map<String, Long>, final: Boolean, reason: String = ""): Result {
    check(here, t, key)?.let { return Result.Bad(it) }
    if (counted.keys.any { pid -> t.lines.none { it.productId == pid } }) return Result.Bad(Problem.NOT_IN_TRANSFER)
    for (l in t.lines) {
      val got = counted[l.productId] ?: continue
      if (got < 0 || got > l.pendingMilli) return Result.Bad(Problem.OVER_RECEIVED)
    }
    if (!final && counted.values.all { it == 0L }) return Result.Bad(Problem.NOTHING_RECEIVED)
    val r = reason.trim().take(MAX_REASON)
    //  گامِ ناقص کسری ندارد (بقیه در مسیر می‌ماند)؛ گامِ پایانیِ کسری‌دار فقط با دلیل
    if (final && shortages(t, counted).isNotEmpty() && r.isEmpty()) return Result.Bad(Problem.REASON_REQUIRED)
    val body = buildJsonObject {
      put("final", final)
      put("lines", buildJsonArray {
        for ((pid, q) in counted) add(buildJsonObject { put("productId", pid); put("receivedMilli", q) })
      })
      if (r.isNotEmpty()) put("reason", r)
    }
    return Result.Ok(Call("POST", "$PATH/${enc(t.id)}/receive", headers(here, key), body))
  }

  /** رد در شعبهٔ [here]: ماندهٔ در مسیر به مبدأ؛ دلیل اجباری */
  fun reject(here: String, t: Transfer, key: String, reason: String): Result {
    check(here, t, key)?.let { return Result.Bad(it) }
    val r = reason.trim().take(MAX_REASON)
    if (r.isEmpty()) return Result.Bad(Problem.REASON_REQUIRED)
    return Result.Ok(Call("POST", "$PATH/${enc(t.id)}/reject", headers(here, key), buildJsonObject { put("reason", r) }))
  }

  private fun check(here: String, t: Transfer, key: String): Problem? = when {
    !KEY_RE.matches(key) -> Problem.BAD_KEY
    !t.status.open -> Problem.CLOSED
    t.toBranch.id != here || t.direction != Direction.IN -> Problem.NOT_DESTINATION
    else -> null
  }

  /* ------------------------------------------------------------ JSON */

  private fun lines(ls: List<Line>): JsonArray = buildJsonArray {
    for (l in ls) add(buildJsonObject { put("productId", l.productId); put("qtyMilli", l.qtyMilli) })
  }

  private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
  private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
  private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
  private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
  private fun JsonElement?.arr(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

  /** یک سر: شناسهٔ بدشکل ⇒ `null` (اصلی = '') */
  fun endOf(o: JsonObject?): End? {
    val x = o ?: return null
    val id = (x["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
    if (!isBranchId(id)) return null
    return End(id, x.s("name"), x.b("main") ?: (id == MAIN))
  }

  /** یک انتقال از پاسخِ سرور؛ شکلِ نادرست ⇒ `null` */
  fun transferOf(o: JsonObject?): Transfer? {
    val x = o ?: return null
    val id = x.s("id").takeIf { ID_RE.matches(it) } ?: return null
    val status = Status.of(x.s("status")) ?: return null
    val direction = Direction.of(x.s("direction")) ?: return null
    val fromB = endOf(x["fromBranch"].obj()) ?: return null
    val toB = endOf(x["toBranch"].obj()) ?: return null
    return Transfer(
      id = id, direction = direction, fromBranch = fromB, toBranch = toB, from = x.s("from"), to = x.s("to"), status = status,
      lines = x["lines"].arr().mapNotNull { l ->
        val pid = l.s("productId").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        TLine(pid, l.l("sentMilli") ?: 0, l.l("receivedMilli"), l.l("pendingMilli") ?: 0, l.l("shortMilli") ?: 0, l.l("returnedMilli") ?: 0)
      },
      note = x.s("note"), reason = x.s("reason"), createdBy = x.s("createdBy"), createdAt = x.l("createdAt") ?: 0,
      closedBy = x.s("closedBy"), closedAt = x.l("closedAt"),
      //  پاسخِ پیش از ۹۴۶ `sentAt` ندارد: هر انتقالِ آن روز در دمِ ساختن فرستاده شد
      sentAt = if (x.containsKey("sentAt")) x.l("sentAt") else x.l("createdAt"),
      sentBy = if (x.containsKey("sentBy")) x.s("sentBy") else x.s("createdBy"),
    )
  }

  /** پاسخِ فهرست (`{branch, transfers}`) */
  fun listOf(body: JsonObject): List<Transfer> = body["transfers"].arr().mapNotNull(::transferOf)

  /** پاسخِ مقصدها (`{branch, destinations}`) — فقط شعبه‌های فعالِ دیگر */
  fun destinationsOf(body: JsonObject): List<End> = body["destinations"].arr().mapNotNull(::endOf)

  /** پاسخِ گام (`{transfer, replayed}`) یا جزئیات (`{transfer, steps}`) ⇒ انتقال و گام‌ها */
  fun detailOf(body: JsonObject): Pair<Transfer, List<Step>>? {
    val t = transferOf(body["transfer"].obj()) ?: return null
    val steps = body["steps"].arr().map { s ->
      Step(
        kind = s.s("kind"), side = s.s("side"), statusFrom = s.s("statusFrom"), statusTo = s.s("statusTo"),
        lines = s["lines"].arr().mapNotNull { l -> l.s("productId").takeIf { it.isNotEmpty() }?.let { Line(it, l.l("qtyMilli") ?: 0L) } },
        note = s.s("note"), final = s.b("final") ?: true, by = s.s("by"), at = s.l("at") ?: 0,
      )
    }
    return t to steps
  }

  /** کدِ خطای پاسخ (`{error: {code}}`) یا `null` */
  fun errorCode(body: JsonObject?): String? = body?.get("error").obj()?.s("code")?.takeIf { it.isNotEmpty() }
}
